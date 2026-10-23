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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
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

data class DirectShazamMatch(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val appleMusicId: String?,
    val appleMusicUrl: String?,
    val webUrl: String?
)

/**
 * Handles Android microphone capture, software AGC normalization for far-field speakers,
 * ShazamKit signature generation, direct Shazam catalog REST matching, Developer Token caching,
 * and Pro Offline Club Mode signature queueing (#78).
 */
object AcousticRecognizer {

    private const val PREFS_NAME = "songflip_shazam_token_prefs"
    private const val KEY_TOKEN = "developer_token"
    private const val KEY_EXPIRES_AT = "developer_token_expires_at"
    private const val TOKEN_ENDPOINT = "https://cache.songflip.link/getShazamToken"
    private const val SHAZAM_MATCH_URL_TEMPLATE =
        "https://api.shazam.apple.com/v1/catalog/%s/match?types=shazam-songs&include[shazam-songs]=songs&include[songs]=music-videos&meta=matchOffset,frequencySkew&format[resources]=map"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null
    private var activeAudioRecord: AudioRecord? = null

    private val _state = MutableStateFlow<AcousticRecognitionState>(AcousticRecognitionState.Idle)
    val state: StateFlow<AcousticRecognitionState> = _state.asStateFlow()

    // Guards the offline queue: two overlapping runs would match the same scans twice and could
    // double-insert History entries before either run deletes the scan.
    private val offlineQueueMutex = Mutex()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
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

    private fun resolveStoreFront(): String {
        val country = Locale.getDefault().country.lowercase(Locale.ROOT)
        return if (country.length == 2 && country.all { it in 'a'..'z' }) country else "de"
    }

    /**
     * Calls Shazam's catalog match REST endpoint directly via OkHttpClient + JSONObject,
     * bypassing Retrofit/Gson reflection and R8 obfuscation edge cases.
     *
     * Live scanning only needs "match or not", so this keeps the original nullable contract.
     */
    private fun matchSignatureBase64Direct(
        context: Context,
        signatureBase64: String
    ): DirectShazamMatch? =
        (matchSignatureBase64Outcome(context, signatureBase64) as? OfflineMatchOutcome.Matched)?.match

    /**
     * Same request as [matchSignatureBase64Direct] but reports WHY no match was returned (#143), so the
     * Offline Club Mode queue only drops scans on a definitive no-match, never on transient failures.
     */
    private fun matchSignatureBase64Outcome(
        context: Context,
        signatureBase64: String,
        allowTokenRetry: Boolean = true
    ): OfflineMatchOutcome {
        if (signatureBase64.isBlank()) return OfflineMatchOutcome.NoMatch
        val jwt = getOrFetchDeveloperToken(context)
        if (jwt.isBlank()) return OfflineMatchOutcome.TransientError("developer token unavailable")

        val primaryStoreFront = resolveStoreFront()
        val storeFronts = if (primaryStoreFront == "de") listOf("de", "us") else listOf(primaryStoreFront, "de")
        val payload = JSONObject()
            .put("signature", JSONObject().put("uri", "data:audio/vnd.shazam.sig;base64,$signatureBase64"))
            .toString()

        val failures = mutableListOf<OfflineMatchOutcome>()
        for (sf in storeFronts) {
            try {
                val url = String.format(Locale.US, SHAZAM_MATCH_URL_TEMPLATE, sf)
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $jwt")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "ShazamKit/2.1.1 Android")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.code == 401 || resp.code == 403) {
                        android.util.Log.w("AcousticRecognizer", "Shazam API HTTP ${resp.code}; refreshing token")
                        invalidateCachedToken(context)
                        if (allowTokenRetry) {
                            return matchSignatureBase64Outcome(context, signatureBase64, allowTokenRetry = false)
                        }
                        return OfflineMatchOutcome.TransientError("HTTP ${resp.code} after token refresh")
                    }
                    val bodyStr = if (resp.isSuccessful) resp.body?.string() else null
                    val outcome = OfflineScanQueueProcessor.classifyMatchResponse(
                        httpCode = resp.code,
                        body = bodyStr,
                        parse = { json -> parseShazamMatchResponse(json) }
                    )
                    when (outcome) {
                        is OfflineMatchOutcome.Matched -> return outcome
                        OfflineMatchOutcome.NoMatch -> {
                            // Definitive no-match on this storefront; continue loop to try fallback storefront (e.g. US)
                            failures += outcome
                        }
                        else -> {
                            android.util.Log.w("AcousticRecognizer", "Shazam API HTTP ${resp.code} on storefront $sf: $outcome")
                            failures += outcome
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.w("AcousticRecognizer", "Direct match network error ($sf): ${t.message}")
                failures += OfflineMatchOutcome.TransientError("network error on $sf: ${t::class.java.simpleName}: ${t.message}")
            }
        }
        return OfflineScanQueueProcessor.combineFailedAttempts(failures)
    }

    internal fun parseShazamMatchResponse(jsonString: String): DirectShazamMatch? {
        if (jsonString.isBlank()) return null
        return try {
            val root = com.google.gson.JsonParser.parseString(jsonString).asJsonObject
            val resources = root.getAsJsonObject("resources") ?: return null
            val shazamSongsMap = resources.getAsJsonObject("shazam-songs")
            if (shazamSongsMap == null || shazamSongsMap.size() == 0) return null
            val songsMap = resources.getAsJsonObject("songs")

            val matchesArray = root.getAsJsonObject("results")?.getAsJsonArray("matches")
                ?: root.getAsJsonArray("data")

            val firstMatchId = if (matchesArray != null && matchesArray.size() > 0) {
                matchesArray.get(0).asJsonObject.get("id")?.asString.orEmpty()
            } else {
                ""
            }

            val shazamSongObj = if (firstMatchId.isNotBlank() && shazamSongsMap.has(firstMatchId)) {
                shazamSongsMap.getAsJsonObject(firstMatchId)
            } else {
                val firstKey = shazamSongsMap.keySet().firstOrNull() ?: return null
                shazamSongsMap.getAsJsonObject(firstKey)
            }

            val shazamAttrs = shazamSongObj?.getAsJsonObject("attributes")

            val linkedSongId = shazamSongObj
                ?.getAsJsonObject("relationships")
                ?.getAsJsonObject("songs")
                ?.getAsJsonArray("data")
                ?.takeIf { it.size() > 0 }
                ?.get(0)
                ?.asJsonObject
                ?.get("id")
                ?.asString
                .orEmpty()

            val songObj = when {
                linkedSongId.isNotBlank() && songsMap != null && songsMap.has(linkedSongId) ->
                    songsMap.getAsJsonObject(linkedSongId)
                songsMap != null && songsMap.size() > 0 -> {
                    val firstSongKey = songsMap.keySet().firstOrNull()
                    if (firstSongKey != null) songsMap.getAsJsonObject(firstSongKey) else null
                }
                else -> null
            }
            val songAttrs = songObj?.getAsJsonObject("attributes")

            fun com.google.gson.JsonObject?.str(key: String): String? =
                this?.get(key)?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }

            val title = songAttrs.str("name")
                ?: shazamAttrs.str("title")
                ?: shazamAttrs.str("name")
                ?: return null

            val artist = songAttrs.str("artistName")
                ?: shazamAttrs.str("artist")
                ?: shazamAttrs.str("artistName")
                ?: shazamAttrs.str("primaryArtist")
                ?: return null

            val rawArtwork = songAttrs?.getAsJsonObject("artwork").str("url")
                ?: shazamAttrs?.getAsJsonObject("artwork").str("url")
            val artworkUrl = rawArtwork
                ?.replace("{w}", "800")
                ?.replace("{h}", "800")

            val appleMusicId = songObj.str("id") ?: linkedSongId.takeIf { it.isNotBlank() }
            val appleMusicUrl = songAttrs.str("url")
            val webUrl = shazamAttrs.str("webUrl")
                ?: shazamAttrs.str("shazamUrl")
                ?: appleMusicUrl

            DirectShazamMatch(
                title = title,
                artist = artist,
                artworkUrl = artworkUrl,
                appleMusicId = appleMusicId,
                appleMusicUrl = appleMusicUrl,
                webUrl = webUrl
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Applies smooth software Automatic Gain Control (AGC) to 16-bit little-endian mono PCM audio
     * so quiet or un-amplified Android microphone streams reach SigX's optimal spectral peak threshold
     * (~-5 dBFS / 18000 peak) without clipping.
     *
     * Returns (normalizedRmsForUi, updatedSmoothedGain, rawPeakSample).
     */
    internal fun applySoftwareAgcAndRms(
        buffer: ByteArray,
        bytesRead: Int,
        currentGain: Float
    ): Triple<Float, Float, Int> {
        val sampleCount = bytesRead / 2
        if (sampleCount <= 0) return Triple(0.08f, currentGain, 0)

        var maxAbs = 1
        var i = 0
        while (i + 1 < bytesRead) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            val a = abs(sample)
            if (a > maxAbs) maxAbs = a
            i += 2
        }

        // Only boost if signal is above ambient noise-gate floor (>= 45 out of 32767, ~-57 dBFS)
        // so background hiss or faint non-music noise is not artificially amplified into false peaks.
        val desiredGain = if (maxAbs >= 45) {
            (18000f / maxAbs.toFloat()).coerceIn(1.0f, 28.0f)
        } else {
            1.0f
        }

        // Fast attack to prevent clipping; smooth release across chunks
        val nextGain = if (desiredGain < currentGain) {
            desiredGain
        } else {
            currentGain * 0.70f + desiredGain * 0.30f
        }

        var sumSquares = 0.0
        i = 0
        while (i + 1 < bytesRead) {
            val raw = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            val boosted = (raw * nextGain).toInt().coerceIn(-32767, 32767)
            buffer[i] = (boosted and 0xFF).toByte()
            buffer[i + 1] = ((boosted shr 8) and 0xFF).toByte()

            val norm = boosted / 32768.0
            sumSquares += norm * norm
            i += 2
        }

        val rms = sqrt(sumSquares / sampleCount).toFloat()
        val uiAmplitude = if (maxAbs < 20) {
            0.08f
        } else {
            (rms * 2.4f).coerceIn(0.12f, 1.0f)
        }
        return Triple(uiAmplitude, nextGain, maxAbs)
    }

    @SuppressLint("MissingPermission")
    private fun createInitializedAudioRecord(
        sampleRateHz: Int,
        channelConfig: Int,
        audioFormat: Int,
        recordBufSize: Int,
        preferredSources: List<Int>
    ): Pair<AudioRecord, Int>? {
        for (source in preferredSources) {
            try {
                val ar = AudioRecord(source, sampleRateHz, channelConfig, audioFormat, recordBufSize)
                if (ar.state == AudioRecord.STATE_INITIALIZED) {
                    return ar to source
                }
                ar.release()
            } catch (_: Throwable) {}
        }
        return null
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
            var audioRecord: AudioRecord? = null
            var activeSnapshotJob: Job? = null
            try {
                ShazamKit.allowLogs = de.goork.songflip.BuildConfig.DEBUG
                _state.value = AcousticRecognitionState.Listening(amplitude = 0.12f, elapsedSeconds = 0)

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

                // Prefer 44.1 kHz (Android CDD baseline & ShazamKit recommended rate), fallback to 48 kHz
                var sampleRateHz = 44100
                var shazamSampleRate = AudioSampleRateInHz.SAMPLE_RATE_44100
                var minBufSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat)
                if (minBufSize <= 0) {
                    sampleRateHz = 48000
                    shazamSampleRate = AudioSampleRateInHz.SAMPLE_RATE_48000
                    minBufSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat)
                }
                val recordBufSize = minBufSize.coerceAtLeast(sampleRateHz * 2 * 4)

                // Android CDD 5.4.2 mandates VOICE_RECOGNITION to disable OEM speech-isolation/noise-suppression DSP
                // and provide a flat frequency response for acoustic fingerprinting. Fallback to CAMCORDER then MIC.
                val candidateSources = listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.CAMCORDER,
                    MediaRecorder.AudioSource.MIC
                )

                val created = createInitializedAudioRecord(
                    sampleRateHz = sampleRateHz,
                    channelConfig = channelConfig,
                    audioFormat = audioFormat,
                    recordBufSize = recordBufSize,
                    preferredSources = candidateSources
                )
                if (created == null) {
                    _state.value = AcousticRecognitionState.Error("listen_error_mic")
                    return@launch
                }

                audioRecord = created.first
                var currentSource = created.second
                activeAudioRecord = audioRecord

                val sigGenResult = ShazamKit.createSignatureGenerator(shazamSampleRate)
                val sigGenerator = (sigGenResult as? ShazamKitResult.Success)?.data
                if (sigGenerator == null) {
                    stopAudioRecordInternal(audioRecord)
                    _state.value = AcousticRecognitionState.Error("listen_error_generic")
                    return@launch
                }

                val tokenProvider = DeveloperTokenProvider {
                    DeveloperToken(getOrFetchDeveloperToken(appCtx))
                }
                val catalog = ShazamKit.createShazamCatalog(tokenProvider, Locale.getDefault())
                val fallbackSession = if (isOnlineAtStart) {
                    (ShazamKit.createSession(catalog) as? ShazamKitResult.Success)?.data
                } else {
                    null
                }

                val readBufferSize = 4096
                val readBuffer = ByteArray(readBufferSize)
                val matchedRef = AtomicReference<DirectShazamMatch?>(null)
                val sigLock = Any()

                audioRecord.startRecording()

                val startTimeMs = System.currentTimeMillis()
                var totalBytesRead = 0L
                val bytesPerSecond = sampleRateHz * 2L
                var nextSnapshotTargetMs = 3_600L
                var smoothedGain = 4.0f
                var maxRawPeakSeen = 0
                var switchedSourceOnce = false

                // Record continuously for up to 12.0 seconds without blocking AudioRecord.read()
                while (isActive && matchedRef.get() == null && (System.currentTimeMillis() - startTimeMs) < 12_000L) {
                    val activeRec = audioRecord ?: break
                    val bytesRead = activeRec.read(readBuffer, 0, readBuffer.size)
                    if (bytesRead > 0) {
                        val nowMs = System.currentTimeMillis()
                        val elapsedMs = nowMs - startTimeMs
                        totalBytesRead += bytesRead
                        val recordedAudioDurationMs = (totalBytesRead * 1000L) / bytesPerSecond

                        val (uiAmp, nextGain, rawPeak) = applySoftwareAgcAndRms(readBuffer, bytesRead, smoothedGain)
                        smoothedGain = nextGain
                        if (rawPeak > maxRawPeakSeen) maxRawPeakSeen = rawPeak

                        // If the initial audio source produces dead silence (< 20 peak) after 700ms, switch to fallback source
                        if (!switchedSourceOnce && elapsedMs >= 700L && maxRawPeakSeen < 20) {
                            switchedSourceOnce = true
                            val remainingSources = candidateSources.filter { it != currentSource }
                            val fallbackCreated = createInitializedAudioRecord(
                                sampleRateHz = sampleRateHz,
                                channelConfig = channelConfig,
                                audioFormat = audioFormat,
                                recordBufSize = recordBufSize,
                                preferredSources = remainingSources
                            )
                            if (fallbackCreated != null) {
                                android.util.Log.i("AcousticRecognizer", "Switching silent AudioSource $currentSource -> ${fallbackCreated.second}")
                                stopAudioRecordInternal(activeRec)
                                audioRecord = fallbackCreated.first
                                currentSource = fallbackCreated.second
                                activeAudioRecord = audioRecord
                                audioRecord?.startRecording()
                                continue
                            }
                        }

                        synchronized(sigLock) {
                            sigGenerator.append(readBuffer, bytesRead, nowMs)
                        }

                        _state.value = AcousticRecognitionState.Listening(
                            amplitude = uiAmp,
                            elapsedSeconds = (elapsedMs / 1000L).toInt()
                        )

                        // If offline & Pro Club Mode: once we have 6 seconds of actual audio, save signature and finish
                        if (!NetworkUtils.isNetworkAvailable(appCtx) && isPro && recordedAudioDurationMs >= 6_000L) {
                            val signature = synchronized(sigLock) { sigGenerator.generateSignature() }
                            activeSnapshotJob?.cancel()
                            stopAudioRecordInternal(activeRec)
                            saveOfflineSignature(signature)
                            return@launch
                        }

                        // Trigger non-blocking background snapshot match at 3.6s, 6.2s, 8.8s, 11.4s of recorded audio
                        if (isOnlineAtStart &&
                            recordedAudioDurationMs >= nextSnapshotTargetMs &&
                            (activeSnapshotJob == null || activeSnapshotJob?.isActive == false)
                        ) {
                            nextSnapshotTargetMs = recordedAudioDurationMs + 2_600L
                            val snapshotSig = synchronized(sigLock) { sigGenerator.generateSignature() }
                            val sigBytes = snapshotSig.dataRepresentation
                            if (sigBytes.isNotEmpty()) {
                                val sigBase64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP)
                                activeSnapshotJob = launch(Dispatchers.IO) {
                                    val directMatch = matchSignatureBase64Direct(appCtx, sigBase64)
                                    if (directMatch != null) {
                                        matchedRef.compareAndSet(null, directMatch)
                                    } else if (fallbackSession != null && snapshotSig.durationInMs >= 3_000L) {
                                        when (val snapRes = fallbackSession.match(snapshotSig)) {
                                            is MatchResult.Match -> {
                                                val item = snapRes.matchedMediaItems.firstOrNull()
                                                if (item != null) {
                                                    matchedRef.compareAndSet(
                                                        null,
                                                        DirectShazamMatch(
                                                            title = item.title?.takeIf { it.isNotBlank() } ?: "Unknown Track",
                                                            artist = item.artist?.takeIf { it.isNotBlank() } ?: item.subtitle.orEmpty(),
                                                            artworkUrl = item.artworkURL?.toString(),
                                                            appleMusicId = item.appleMusicID,
                                                            appleMusicUrl = item.appleMusicURL?.toString(),
                                                            webUrl = item.webURL?.toString()
                                                        )
                                                    )
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        delay(15)
                    }
                }

                audioRecord?.let { stopAudioRecordInternal(it) }

                // Wait briefly if a background snapshot match request is still in flight
                if (matchedRef.get() == null && activeSnapshotJob?.isActive == true) {
                    var waitMs = 0L
                    while (matchedRef.get() == null && activeSnapshotJob?.isActive == true && waitMs < 1_500L) {
                        delay(80)
                        waitMs += 80L
                    }
                }
                activeSnapshotJob?.cancel()

                // Final match attempt with full continuous 12s signature if no progressive snapshot matched yet
                if (matchedRef.get() == null) {
                    val finalSig = synchronized(sigLock) { sigGenerator.generateSignature() }
                    if (!NetworkUtils.isNetworkAvailable(appCtx)) {
                        if (isPro) {
                            saveOfflineSignature(finalSig)
                        } else {
                            _state.value = AcousticRecognitionState.Error("listen_error_offline")
                        }
                        return@launch
                    }

                    val finalBytes = finalSig.dataRepresentation
                    if (finalBytes.isNotEmpty()) {
                        val finalBase64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)
                        val directMatch = matchSignatureBase64Direct(appCtx, finalBase64)
                        if (directMatch != null) {
                            matchedRef.compareAndSet(null, directMatch)
                        } else if (fallbackSession != null && finalSig.durationInMs >= 3_000L) {
                            when (val finalRes = fallbackSession.match(finalSig)) {
                                is MatchResult.Match -> {
                                    val item = finalRes.matchedMediaItems.firstOrNull()
                                    if (item != null) {
                                        matchedRef.compareAndSet(
                                            null,
                                            DirectShazamMatch(
                                                title = item.title?.takeIf { it.isNotBlank() } ?: "Unknown Track",
                                                artist = item.artist?.takeIf { it.isNotBlank() } ?: item.subtitle.orEmpty(),
                                                artworkUrl = item.artworkURL?.toString(),
                                                appleMusicId = item.appleMusicID,
                                                appleMusicUrl = item.appleMusicURL?.toString(),
                                                webUrl = item.webURL?.toString()
                                            )
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }

                val winner = matchedRef.get()
                if (winner == null) {
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackAcousticScanNoMatch(
                        target = targetPlatformKey,
                        isPro = isPro
                    )
                    _state.value = AcousticRecognitionState.NoMatch
                    return@launch
                }

                _state.value = AcousticRecognitionState.Resolving(
                    title = winner.title,
                    artist = winner.artist,
                    artworkUrl = winner.artworkUrl
                )

                val targetPlatform = MusicPlatform.fromKey(targetPlatformKey)
                val flipResult = AcousticQuotaManager.shared.resolveAndStoreAcousticMatch(
                    engine = SongLinkEngine.shared,
                    title = winner.title,
                    artist = winner.artist,
                    artworkUrl = winner.artworkUrl,
                    appleMusicUrl = winner.appleMusicUrl,
                    appleMusicId = winner.appleMusicId,
                    webUrl = winner.webUrl,
                    targetPlatform = targetPlatform,
                    isPro = isPro,
                    authToken = ProManager.getAuthToken(),
                    audiophileMode = isPro && SettingsRepository(appCtx).audiophileMode
                )

                de.goork.songflip.core.analytics.AptabaseClient.shared.trackAcousticScanMatched(
                    target = targetPlatformKey,
                    isPro = isPro,
                    isOfflineQueue = false
                )

                _state.value = AcousticRecognitionState.Matched(flipResult)
            } catch (ce: CancellationException) {
                activeSnapshotJob?.cancel()
                audioRecord?.let { stopAudioRecordInternal(it) }
                throw ce
            } catch (t: Throwable) {
                activeSnapshotJob?.cancel()
                audioRecord?.let { stopAudioRecordInternal(it) }
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
     *
     * Scans are only removed after a match was stored or Shazam definitively reported no match (#143).
     * Transient failures keep the scan and end the run; a concurrent second invocation (e.g. rapid
     * ON_RESUME events) returns 0 immediately instead of matching the same scans twice.
     */
    suspend fun processPendingOfflineScansIfNeeded(
        context: Context,
        targetPlatformKey: String,
        isPro: Boolean
    ): Int = withContext(Dispatchers.IO) {
        if (!offlineQueueMutex.tryLock()) {
            android.util.Log.d("AcousticRecognizer", "Offline queue processing already running; skipping")
            return@withContext 0
        }
        try {
            if (!NetworkUtils.isNetworkAvailable(context)) return@withContext 0
            val appCtx = context.applicationContext
            val storage = AndroidSharedPreferencesCacheStorage.getOrCreateSqlDelightStorage()
            val pending = storage.getPendingAcousticScans()
            if (pending.isEmpty()) return@withContext 0

            val targetPlatform = MusicPlatform.fromKey(targetPlatformKey)
            val authToken = ProManager.getAuthToken()
            val audiophileMode = isPro && SettingsRepository(appCtx).audiophileMode

            OfflineScanQueueProcessor.process(
                pending = pending,
                match = { signature -> matchSignatureBase64Outcome(appCtx, signature) },
                onMatched = { winner ->
                    AcousticQuotaManager.shared.resolveAndStoreAcousticMatch(
                        engine = SongLinkEngine.shared,
                        title = winner.title,
                        artist = winner.artist,
                        artworkUrl = winner.artworkUrl,
                        appleMusicUrl = winner.appleMusicUrl,
                        appleMusicId = winner.appleMusicId,
                        webUrl = winner.webUrl,
                        targetPlatform = targetPlatform,
                        isPro = isPro,
                        authToken = authToken,
                        audiophileMode = audiophileMode
                    )
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackAcousticScanMatched(
                        target = targetPlatformKey,
                        isPro = isPro,
                        isOfflineQueue = true
                    )
                },
                delete = { id -> storage.deletePendingAcousticScan(id) },
                log = { message -> android.util.Log.w("AcousticRecognizer", message) }
            )
        } finally {
            offlineQueueMutex.unlock()
        }
    }
}
