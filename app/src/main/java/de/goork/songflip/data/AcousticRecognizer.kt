package de.goork.songflip.data

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import com.shazam.shazamkit.AudioSampleRateInHz
import com.shazam.shazamkit.DeveloperToken
import com.shazam.shazamkit.DeveloperTokenProvider
import com.shazam.shazamkit.MatchResult
import com.shazam.shazamkit.ShazamKit
import com.shazam.shazamkit.ShazamKitResult
import com.shazam.shazamkit.Signature
import de.goork.songflip.core.cache.AndroidSharedPreferencesCacheStorage
import de.goork.songflip.core.engine.AcousticFlipResult
import de.goork.songflip.core.engine.AcousticQuotaManager
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.core.model.MusicPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

sealed class AcousticRecognitionState {
    object Idle : AcousticRecognitionState()
    data class Listening(val amplitude: Float = 0f, val elapsedSeconds: Int = 0) : AcousticRecognitionState()
    data class Resolving(val title: String, val artist: String, val artworkUrl: String?) : AcousticRecognitionState()
    data class Matched(val result: AcousticFlipResult) : AcousticRecognitionState()
    data class OfflineQueued(val pendingCount: Int) : AcousticRecognitionState()
    object NoMatch : AcousticRecognitionState()
    data class Error(val messageKey: String) : AcousticRecognitionState()
}

/**
 * Handles Android microphone capture, ShazamKit signature generation, Developer Token caching,
 * and Pro Offline Club Mode signature queueing (#78).
 */
object AcousticRecognizer {

    private const val PREFS_NAME = "songflip_shazam_token_prefs"
    private const val KEY_TOKEN = "developer_token"
    private const val KEY_EXPIRES_AT = "developer_token_expires_at"
    private const val TOKEN_ENDPOINT = "https://cache.songflip.link/getShazamToken"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null
    private var activeAudioRecord: AudioRecord? = null

    private val _state = MutableStateFlow<AcousticRecognitionState>(AcousticRecognitionState.Idle)
    val state: StateFlow<AcousticRecognitionState> = _state.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun resetState() {
        stopListening()
        _state.value = AcousticRecognitionState.Idle
    }

    fun stopListening() {
        activeJob?.cancel()
        activeJob = null
        try {
            activeAudioRecord?.stop()
        } catch (_: Throwable) {}
        try {
            activeAudioRecord?.release()
        } catch (_: Throwable) {}
        activeAudioRecord = null
    }

    private fun getOrFetchDeveloperToken(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()

        // Reuse cached token if valid for at least 1 more hour
        if (!cached.isNullOrBlank() && expiresAt - now > 3_600_000L) {
            return cached
        }

        return try {
            val req = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .header("x-web-client", "songflip-app")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w("AcousticRecognizer", "Token endpoint HTTP ${resp.code}")
                    return cached.orEmpty()
                }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val token = json.optString("token", "")
                val exp = json.optLong("expiresAt", now + 86_400_000L)
                if (token.isNotBlank()) {
                    prefs.edit()
                        .putString(KEY_TOKEN, token)
                        .putLong(KEY_EXPIRES_AT, exp)
                        .apply()
                }
                token.ifBlank { cached.orEmpty() }
            }
        } catch (t: Throwable) {
            android.util.Log.w("AcousticRecognizer", "Failed to fetch developer token: ${t.message}")
            cached.orEmpty()
        }
    }

    private fun invalidateCachedToken(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    @SuppressLint("MissingPermission")
    fun startListening(
        context: Context,
        targetPlatformKey: String,
        isPro: Boolean
    ) {
        stopListening()
        val appCtx = context.applicationContext

        if (!NetworkUtils.isNetworkAvailable(appCtx) && !isPro) {
            _state.value = AcousticRecognitionState.Error("listen_error_offline")
            return
        }

        activeJob = scope.launch(Dispatchers.IO) {
            var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null
            var echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
            var streamingCollectJob: Job? = null
            try {
                ShazamKit.allowLogs = de.goork.songflip.BuildConfig.DEBUG
                _state.value = AcousticRecognitionState.Listening(amplitude = 0.1f, elapsedSeconds = 0)

                val isOnlineAtStart = NetworkUtils.isNetworkAvailable(appCtx)
                // Pre-fetch developer token BEFORE starting AudioRecord so network latency never stalls audio capture
                val initialJwt = if (isOnlineAtStart) getOrFetchDeveloperToken(appCtx) else ""
                if (isOnlineAtStart && initialJwt.isBlank() && !isPro) {
                    android.util.Log.e("AcousticRecognizer", "Developer token is blank; cannot start online recognition")
                    _state.value = AcousticRecognitionState.Error("listen_error_generic")
                    return@launch
                }

                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT

                // Prefer 44.1 kHz (Android CDD universal baseline & ShazamKit recommended rate), fallback to 48 kHz
                var sampleRateHz = 44100
                var shazamSampleRate = AudioSampleRateInHz.SAMPLE_RATE_44100
                var minBufSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat)
                if (minBufSize <= 0) {
                    sampleRateHz = 48000
                    shazamSampleRate = AudioSampleRateInHz.SAMPLE_RATE_48000
                    minBufSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat)
                }
                // Allocate at least 4 seconds of ring buffer so AudioFlinger never overflows
                val recordBufSize = minBufSize.coerceAtLeast(sampleRateHz * 2 * 4)

                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRateHz,
                    channelConfig,
                    audioFormat,
                    recordBufSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release()
                    _state.value = AcousticRecognitionState.Error("listen_error_mic")
                    return@launch
                }

                // Disable OEM speech NoiseSuppressor / EchoCanceler so ambient room music is not filtered out
                try {
                    if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = android.media.audiofx.NoiseSuppressor.create(audioRecord.audioSessionId)?.apply {
                            enabled = false
                        }
                    }
                    if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = android.media.audiofx.AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply {
                            enabled = false
                        }
                    }
                } catch (_: Throwable) {}

                activeAudioRecord = audioRecord

                val sigGenResult = ShazamKit.createSignatureGenerator(shazamSampleRate)
                val sigGenerator = (sigGenResult as? ShazamKitResult.Success)?.data
                if (sigGenerator == null) {
                    audioRecord.release()
                    activeAudioRecord = null
                    _state.value = AcousticRecognitionState.Error("listen_error_generic")
                    return@launch
                }

                val readBufferSize = 4096
                val readBuffer = ByteArray(readBufferSize)

                val tokenProvider = DeveloperTokenProvider {
                    val jwt = getOrFetchDeveloperToken(appCtx)
                    DeveloperToken(jwt)
                }
                val catalog = ShazamKit.createShazamCatalog(tokenProvider, Locale.getDefault())

                val matchedItemRef = java.util.concurrent.atomic.AtomicReference<com.shazam.shazamkit.MatchedMediaItem?>(null)
                val sigLock = Any()

                val onlineSession = if (isOnlineAtStart) {
                    (ShazamKit.createSession(catalog) as? ShazamKitResult.Success)?.data
                } else {
                    null
                }

                val streamingSession = if (isOnlineAtStart) {
                    (ShazamKit.createStreamingSession(catalog, shazamSampleRate, readBufferSize) as? ShazamKitResult.Success)?.data
                } else {
                    null
                }

                if (streamingSession != null) {
                    streamingCollectJob = launch(Dispatchers.IO) {
                        streamingSession.recognitionResults().collect { matchRes ->
                            when (matchRes) {
                                is MatchResult.Match -> {
                                    val first = matchRes.matchedMediaItems.firstOrNull()
                                    if (first != null) {
                                        matchedItemRef.compareAndSet(null, first)
                                    }
                                }
                                is MatchResult.NoMatch -> {
                                    android.util.Log.d("AcousticRecognizer", "Streaming progressive attempt: NoMatch")
                                }
                                is MatchResult.Error -> {
                                    val err = matchRes.exception.matchError
                                    android.util.Log.w(
                                        "AcousticRecognizer",
                                        "Streaming match error: $err (${matchRes.exception.message})",
                                        matchRes.exception.cause
                                    )
                                    if (err == com.shazam.shazamkit.MatchError.MATCH_ATTEMPT_FAILED) {
                                        invalidateCachedToken(appCtx)
                                    }
                                }
                            }
                        }
                    }
                }

                audioRecord.startRecording()

                val startTimeMs = System.currentTimeMillis()
                var totalBytesRead = 0L
                val bytesPerSecond = sampleRateHz * 2L
                var nextSnapshotTargetMs = 3_200L
                var activeSnapshotJob: Job? = null

                // Record continuously for up to 12.0 seconds without EVER blocking AudioRecord.read()
                while (isActive && matchedItemRef.get() == null && (System.currentTimeMillis() - startTimeMs) < 12_000L) {
                    val bytesRead = audioRecord.read(readBuffer, 0, readBuffer.size)
                    if (bytesRead > 0) {
                        val nowMs = System.currentTimeMillis()
                        val elapsedMs = nowMs - startTimeMs
                        totalBytesRead += bytesRead
                        val recordedAudioDurationMs = (totalBytesRead * 1000L) / bytesPerSecond

                        // Pass actual epoch timestamp in ms (required by ShazamKit TimeProviderBasedOffsetTimestamp)
                        synchronized(sigLock) {
                            sigGenerator.append(readBuffer, bytesRead, nowMs)
                        }
                        streamingSession?.matchStream(readBuffer, bytesRead, nowMs)

                        // Compute RMS amplitude for smooth UI waveform pulse
                        val rms = computeRmsAmplitude(readBuffer, bytesRead)
                        _state.value = AcousticRecognitionState.Listening(
                            amplitude = rms,
                            elapsedSeconds = (elapsedMs / 1000L).toInt()
                        )

                        // If offline & Pro Club Mode: once we have 6 seconds of actual audio, save signature and finish
                        if (!NetworkUtils.isNetworkAvailable(appCtx) && isPro && recordedAudioDurationMs >= 6_000L) {
                            val signature = synchronized(sigLock) { sigGenerator.generateSignature() }
                            streamingCollectJob?.cancel()
                            activeSnapshotJob?.cancel()
                            stopAudioRecordInternal(audioRecord, noiseSuppressor, echoCanceler)
                            saveOfflineSignature(signature)
                            return@launch
                        }

                        // Trigger non-blocking background snapshot match at 3.2s, 6.2s, 9.2s of actual recorded audio
                        if (onlineSession != null &&
                            recordedAudioDurationMs >= nextSnapshotTargetMs &&
                            (activeSnapshotJob == null || activeSnapshotJob?.isActive == false)
                        ) {
                            nextSnapshotTargetMs = recordedAudioDurationMs + 3_000L
                            val snapshotSig = synchronized(sigLock) { sigGenerator.generateSignature() }
                            activeSnapshotJob = launch(Dispatchers.IO) {
                                when (val snapRes = onlineSession.match(snapshotSig)) {
                                    is MatchResult.Match -> {
                                        val first = snapRes.matchedMediaItems.firstOrNull()
                                        if (first != null) {
                                            matchedItemRef.compareAndSet(null, first)
                                        }
                                    }
                                    is MatchResult.Error -> {
                                        android.util.Log.w(
                                            "AcousticRecognizer",
                                            "Snapshot match error: ${snapRes.exception.matchError} (${snapRes.exception.message})",
                                            snapRes.exception.cause
                                        )
                                    }
                                    is MatchResult.NoMatch -> {
                                        android.util.Log.d("AcousticRecognizer", "Snapshot attempt (${snapshotSig.durationInMs}ms): NoMatch")
                                    }
                                }
                            }
                        }
                    } else {
                        delay(20)
                    }
                }

                stopAudioRecordInternal(audioRecord, noiseSuppressor, echoCanceler)

                // Wait briefly if a background match request is still in flight
                if (matchedItemRef.get() == null && (streamingCollectJob?.isActive == true || activeSnapshotJob?.isActive == true)) {
                    var waitMs = 0L
                    while (matchedItemRef.get() == null && waitMs < 1_200L) {
                        delay(100)
                        waitMs += 100L
                    }
                }
                streamingCollectJob?.cancel()
                activeSnapshotJob?.cancel()

                // Final match attempt with full continuous 12s signature if no progressive match succeeded yet
                if (matchedItemRef.get() == null) {
                    val finalSig = synchronized(sigLock) { sigGenerator.generateSignature() }
                    if (!NetworkUtils.isNetworkAvailable(appCtx)) {
                        if (isPro) {
                            saveOfflineSignature(finalSig)
                        } else {
                            _state.value = AcousticRecognitionState.Error("listen_error_offline")
                        }
                        return@launch
                    }

                    val fallbackSession = onlineSession
                        ?: (ShazamKit.createSession(catalog) as? ShazamKitResult.Success)?.data
                    if (fallbackSession != null) {
                        when (val finalMatch = fallbackSession.match(finalSig)) {
                            is MatchResult.Match -> {
                                val first = finalMatch.matchedMediaItems.firstOrNull()
                                if (first != null) {
                                    matchedItemRef.compareAndSet(null, first)
                                }
                            }
                            is MatchResult.Error -> {
                                android.util.Log.w(
                                    "AcousticRecognizer",
                                    "Final match error: ${finalMatch.exception.matchError} (${finalMatch.exception.message})",
                                    finalMatch.exception.cause
                                )
                            }
                            is MatchResult.NoMatch -> {
                                android.util.Log.d("AcousticRecognizer", "Final fallback attempt (${finalSig.durationInMs}ms): NoMatch")
                            }
                        }
                    }
                }

                val winner = matchedItemRef.get()
                if (winner == null) {
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackAcousticScanCompleted(
                        target = targetPlatformKey,
                        matched = false,
                        isPro = isPro,
                        isOfflineQueue = false
                    )
                    _state.value = AcousticRecognitionState.NoMatch
                    return@launch
                }

                val title = winner.title?.takeIf { it.isNotBlank() } ?: "Unknown Track"
                val artist = winner.artist?.takeIf { it.isNotBlank() } ?: winner.subtitle.orEmpty()
                val artworkUrl = winner.artworkURL?.toString()

                _state.value = AcousticRecognitionState.Resolving(
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl
                )

                val targetPlatform = MusicPlatform.fromKey(targetPlatformKey)
                val flipResult = AcousticQuotaManager.shared.resolveAndStoreAcousticMatch(
                    engine = SongLinkEngine.shared,
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    appleMusicUrl = winner.appleMusicURL?.toString(),
                    appleMusicId = winner.appleMusicID,
                    webUrl = winner.webURL?.toString(),
                    targetPlatform = targetPlatform,
                    isPro = isPro
                )

                de.goork.songflip.core.analytics.AptabaseClient.shared.trackAcousticScanCompleted(
                    target = targetPlatformKey,
                    matched = true,
                    isPro = isPro,
                    isOfflineQueue = false
                )

                _state.value = AcousticRecognitionState.Matched(flipResult)
            } catch (ce: CancellationException) {
                streamingCollectJob?.cancel()
                try { noiseSuppressor?.release() } catch (_: Throwable) {}
                try { echoCanceler?.release() } catch (_: Throwable) {}
                throw ce
            } catch (t: Throwable) {
                streamingCollectJob?.cancel()
                try { noiseSuppressor?.release() } catch (_: Throwable) {}
                try { echoCanceler?.release() } catch (_: Throwable) {}
                android.util.Log.e("AcousticRecognizer", "Recognition failed: ${t.message}", t)
                _state.value = AcousticRecognitionState.Error("listen_error_generic")
            }
        }
    }

    private fun stopAudioRecordInternal(
        audioRecord: AudioRecord,
        noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null,
        echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
    ) {
        try {
            noiseSuppressor?.release()
        } catch (_: Throwable) {}
        try {
            echoCanceler?.release()
        } catch (_: Throwable) {}
        try {
            audioRecord.stop()
        } catch (t: Throwable) {
            android.util.Log.d("AcousticRecognizer", "AudioRecord stop ignored: ${t.message}")
        }
        try {
            audioRecord.release()
        } catch (t: Throwable) {
            android.util.Log.d("AcousticRecognizer", "AudioRecord release ignored: ${t.message}")
        }
        if (activeAudioRecord === audioRecord) {
            activeAudioRecord = null
        }
    }

    private fun saveOfflineSignature(signature: Signature) {
        val storage = AndroidSharedPreferencesCacheStorage.getOrCreateSqlDelightStorage()
        val encoded = Base64.encodeToString(signature.dataRepresentation, Base64.NO_WRAP)
        val id = UUID.randomUUID().toString()
        storage.enqueuePendingAcousticScan(
            id = id,
            signatureBase64 = encoded,
            durationMs = signature.durationInMs
        )
        val totalPending = storage.countPendingAcousticScans()
        de.goork.songflip.core.analytics.AptabaseClient.shared.trackAcousticOfflineQueued()
        _state.value = AcousticRecognitionState.OfflineQueued(totalPending)
    }

    /**
     * Resolves any queued offline Club-Mode signatures when network connectivity returns (#78).
     */
    suspend fun processPendingOfflineScansIfNeeded(
        context: Context,
        targetPlatformKey: String,
        isPro: Boolean
    ): Int = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isNetworkAvailable(context)) return@withContext 0
        val storage = AndroidSharedPreferencesCacheStorage.getOrCreateSqlDelightStorage()
        val pending = storage.getPendingAcousticScans()
        if (pending.isEmpty()) return@withContext 0

        val tokenProvider = DeveloperTokenProvider {
            DeveloperToken(getOrFetchDeveloperToken(context))
        }
        val catalog = ShazamKit.createShazamCatalog(tokenProvider, Locale.getDefault())
        val session = (ShazamKit.createSession(catalog) as? ShazamKitResult.Success)?.data
            ?: return@withContext 0

        val targetPlatform = MusicPlatform.fromKey(targetPlatformKey)
        var resolvedCount = 0

        for (scan in pending) {
            try {
                val bytes = Base64.decode(scan.signatureBase64, Base64.DEFAULT)
                val ctor = Signature::class.java.declaredConstructors.first().apply { isAccessible = true }
                val sig = ctor.newInstance(bytes, scan.durationMs, null) as Signature
                when (val matchRes = session.match(sig)) {
                    is MatchResult.Match -> {
                        val winner = matchRes.matchedMediaItems.firstOrNull()
                        if (winner != null) {
                            val title = winner.title?.takeIf { it.isNotBlank() } ?: "Unknown Track"
                            val artist = winner.artist?.takeIf { it.isNotBlank() } ?: winner.subtitle.orEmpty()
                            AcousticQuotaManager.shared.resolveAndStoreAcousticMatch(
                                engine = SongLinkEngine.shared,
                                title = title,
                                artist = artist,
                                artworkUrl = winner.artworkURL?.toString(),
                                appleMusicUrl = winner.appleMusicURL?.toString(),
                                appleMusicId = winner.appleMusicID,
                                webUrl = winner.webURL?.toString(),
                                targetPlatform = targetPlatform,
                                isPro = isPro
                            )
                            resolvedCount++
                            de.goork.songflip.core.analytics.AptabaseClient.shared.trackAcousticScanCompleted(
                                target = targetPlatformKey,
                                matched = true,
                                isPro = isPro,
                                isOfflineQueue = true
                            )
                        }
                        storage.deletePendingAcousticScan(scan.id)
                    }
                    is MatchResult.NoMatch -> {
                        storage.deletePendingAcousticScan(scan.id)
                    }
                    is MatchResult.Error -> {
                        // Keep in queue if transient network error; delete if invalid signature
                        val err = matchRes.exception.matchError
                        if (err == com.shazam.shazamkit.MatchError.INVALID_SIGNATURE ||
                            err == com.shazam.shazamkit.MatchError.INVALID_SIGNATURE_DURATION
                        ) {
                            storage.deletePendingAcousticScan(scan.id)
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                android.util.Log.w("AcousticRecognizer", "Failed to process offline scan ${scan.id}: ${t.message}")
            }
        }
        resolvedCount
    }

    private fun computeRmsAmplitude(buffer: ByteArray, bytesRead: Int): Float {
        var sumSquares = 0.0
        val sampleCount = bytesRead / 2
        if (sampleCount <= 0) return 0.1f
        var i = 0
        while (i + 1 < bytesRead) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
            i += 2
        }
        val rms = sqrt(sumSquares / sampleCount).toFloat()
        return (rms * 3.5f).coerceIn(0.08f, 1.0f)
    }
}
