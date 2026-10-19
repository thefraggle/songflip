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
                if (!resp.isSuccessful) return cached.orEmpty()
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

        activeJob = scope.launch {
            try {
                _state.value = AcousticRecognitionState.Listening(amplitude = 0.1f, elapsedSeconds = 0)

                val sampleRateHz = 48000
                val shazamSampleRate = AudioSampleRateInHz.SAMPLE_RATE_48000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat)
                    .coerceAtLeast(sampleRateHz * 2)

                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRateHz,
                    channelConfig,
                    audioFormat,
                    minBufSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release()
                    _state.value = AcousticRecognitionState.Error("listen_error_mic")
                    return@launch
                }

                activeAudioRecord = audioRecord

                val sigGenResult = ShazamKit.createSignatureGenerator(shazamSampleRate)
                val sigGenerator = (sigGenResult as? ShazamKitResult.Success)?.data
                if (sigGenerator == null) {
                    audioRecord.release()
                    activeAudioRecord = null
                    _state.value = AcousticRecognitionState.Error("listen_error_generic")
                    return@launch
                }

                val isOnline = NetworkUtils.isNetworkAvailable(appCtx)

                val session = if (isOnline) {
                    val tokenProvider = DeveloperTokenProvider {
                        val jwt = getOrFetchDeveloperToken(appCtx)
                        DeveloperToken(jwt)
                    }
                    val catalog = ShazamKit.createShazamCatalog(tokenProvider, Locale.getDefault())
                    (ShazamKit.createSession(catalog) as? ShazamKitResult.Success)?.data
                } else {
                    null
                }

                audioRecord.startRecording()

                val readBuffer = ByteArray(4096)
                val startTimeMs = System.currentTimeMillis()
                var lastMatchAttemptMs = startTimeMs
                var matchedItem: com.shazam.shazamkit.MatchedMediaItem? = null

                // Record up to 11 seconds; attempt matches at ~3.5s, ~6.5s, ~10.0s
                while (isActive && (System.currentTimeMillis() - startTimeMs) < 10_500L) {
                    val bytesRead = audioRecord.read(readBuffer, 0, readBuffer.size)
                    if (bytesRead > 0) {
                        val elapsedMs = System.currentTimeMillis() - startTimeMs
                        sigGenerator.append(readBuffer, bytesRead, elapsedMs)

                        // Compute RMS amplitude for smooth UI waveform pulse
                        val rms = computeRmsAmplitude(readBuffer, bytesRead)
                        _state.value = AcousticRecognitionState.Listening(
                            amplitude = rms,
                            elapsedSeconds = (elapsedMs / 1000L).toInt()
                        )

                        // If offline & Pro Club Mode: once we have 6 seconds of audio, save signature and finish
                        if (!NetworkUtils.isNetworkAvailable(appCtx) && isPro && elapsedMs >= 6_000L) {
                            val signature = sigGenerator.generateSignature()
                            stopAudioRecordInternal(audioRecord)
                            saveOfflineSignature(signature)
                            return@launch
                        }

                        // Online periodic match check every ~3.2 seconds after initial 3.2s buffer
                        if (session != null && elapsedMs >= 3_200L && (System.currentTimeMillis() - lastMatchAttemptMs) >= 3_200L) {
                            lastMatchAttemptMs = System.currentTimeMillis()
                            val currentSig = sigGenerator.generateSignature()
                            when (val matchRes = session.match(currentSig)) {
                                is MatchResult.Match -> {
                                    matchedItem = matchRes.matchedMediaItems.firstOrNull()
                                    if (matchedItem != null) break
                                }
                                else -> {
                                    // Continue listening for a longer signature
                                }
                            }
                        }
                    } else {
                        delay(40)
                    }
                }

                stopAudioRecordInternal(audioRecord)

                // Final match attempt with full recorded signature
                if (matchedItem == null) {
                    val finalSig = sigGenerator.generateSignature()
                    if (!NetworkUtils.isNetworkAvailable(appCtx)) {
                        if (isPro) {
                            saveOfflineSignature(finalSig)
                        } else {
                            _state.value = AcousticRecognitionState.Error("listen_error_offline")
                        }
                        return@launch
                    }

                    if (session != null) {
                        val finalMatch = session.match(finalSig)
                        if (finalMatch is MatchResult.Match) {
                            matchedItem = finalMatch.matchedMediaItems.firstOrNull()
                        }
                    }
                }

                val winner = matchedItem
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
                throw ce
            } catch (t: Throwable) {
                android.util.Log.e("AcousticRecognizer", "Recognition failed: ${t.message}", t)
                _state.value = AcousticRecognitionState.Error("listen_error_generic")
            }
        }
    }

    private fun stopAudioRecordInternal(audioRecord: AudioRecord) {
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
