package de.goork.songflip.core.engine

import de.goork.songflip.core.model.PromoBannerConfig
import de.goork.songflip.core.util.SongFlipLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object PromoBannerManager {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var client: HttpClient = createPlatformHttpClient()

    private val _config = MutableStateFlow(PromoBannerConfig())
    val config: StateFlow<PromoBannerConfig> = _config.asStateFlow()

    private val _isDismissed = MutableStateFlow(false)
    val isDismissed: StateFlow<Boolean> = _isDismissed.asStateFlow()

    private var lastFetchTimeMs: Long = 0L
    private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes

    fun setHttpClientForTesting(testClient: HttpClient) {
        client = testClient
    }

    fun setConfigForTesting(testConfig: PromoBannerConfig) {
        _config.value = testConfig
        lastFetchTimeMs = 0L
    }

    fun resetDismissed() {
        _isDismissed.value = false
    }

    fun dismiss() {
        _isDismissed.value = true
    }

    fun isBannerVisible(isPro: Boolean, currentTimeMs: Long = getCurrentTimeMillis()): Boolean {
        if (isPro) return false
        if (_isDismissed.value) return false
        return _config.value.isValid(currentTimeMs)
    }

    fun isBannerVisible(isPro: Boolean): Boolean = isBannerVisible(isPro, getCurrentTimeMillis())

    fun initFromCachedJson(cachedJson: String, currentTimeMs: Long = getCurrentTimeMillis()) {
        val parsed = parsePromoBannerJson(cachedJson)
        if (parsed != null && parsed.isValid(currentTimeMs)) {
            _config.value = parsed
        }
    }

    fun initFromCachedJson(cachedJson: String) = initFromCachedJson(cachedJson, getCurrentTimeMillis())

    fun fetchPromoBanner(
        endpointUrl: String = "https://songflip-web.web.app/api/promo-banner",
        currentTimeMs: Long = getCurrentTimeMillis(),
        forceRefresh: Boolean = false,
        onSuccess: ((String) -> Unit)? = null
    ) {
        scope.launch {
            val raw = fetchPromoBannerRaw(endpointUrl, currentTimeMs, forceRefresh)
            if (raw != null) {
                onSuccess?.invoke(raw)
            }
        }
    }

    fun fetchPromoBanner(
        endpointUrl: String,
        forceRefresh: Boolean,
        onSuccess: ((String) -> Unit)? = null
    ) = fetchPromoBanner(endpointUrl, getCurrentTimeMillis(), forceRefresh, onSuccess)

    suspend fun fetchPromoBannerRaw(
        endpointUrl: String = "https://songflip-web.web.app/api/promo-banner",
        currentTimeMs: Long = getCurrentTimeMillis(),
        forceRefresh: Boolean = false
    ): String? {
        val now = if (currentTimeMs > 0L) currentTimeMs else 0L
        if (!forceRefresh && lastFetchTimeMs > 0L && now > 0L && (now - lastFetchTimeMs) >= 0L && (now - lastFetchTimeMs) < CACHE_TTL_MS) {
            return null
        }

        return try {
            val response = client.get(endpointUrl)
            val body = response.bodyAsText()
            val parsed = parsePromoBannerJson(body)
            if (parsed != null) {
                _config.value = parsed
                lastFetchTimeMs = now
                body
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SongFlipLogger.w("PromoBannerManager", "Failed to fetch promo banner", e)
            null
        }
    }

    suspend fun fetchPromoBannerRaw(
        endpointUrl: String,
        forceRefresh: Boolean
    ): String? = fetchPromoBannerRaw(endpointUrl, getCurrentTimeMillis(), forceRefresh)

    suspend fun fetchPromoBannerDirect(
        endpointUrl: String = "https://songflip-web.web.app/api/promo-banner",
        currentTimeMs: Long = getCurrentTimeMillis(),
        forceRefresh: Boolean = false
    ): PromoBannerConfig? {
        fetchPromoBannerRaw(endpointUrl, currentTimeMs, forceRefresh)
        return _config.value
    }

    suspend fun fetchPromoBannerDirect(
        endpointUrl: String,
        forceRefresh: Boolean
    ): PromoBannerConfig? = fetchPromoBannerDirect(endpointUrl, getCurrentTimeMillis(), forceRefresh)

    fun parsePromoBannerJson(jsonString: String): PromoBannerConfig? {
        if (jsonString.isBlank()) return null
        return try {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val enabled = root["enabled"]?.jsonPrimitive?.booleanOrNull ?: false
            val campaignId = root["campaign_id"]?.jsonPrimitive?.content
                ?: root["campaignId"]?.jsonPrimitive?.content
                ?: ""
            val endTimestampMs = root["end_timestamp"]?.jsonPrimitive?.longOrNull
                ?: root["endTimestampMs"]?.jsonPrimitive?.longOrNull
                ?: 0L

            val badge = parseStringMap(root["badge"]?.jsonObject)
            val title = parseStringMap(root["title"]?.jsonObject)
            val subtitle = parseStringMap(root["subtitle"]?.jsonObject)
            val buttonText = parseStringMap(root["button_text"]?.jsonObject ?: root["buttonText"]?.jsonObject)

            PromoBannerConfig(
                enabled = enabled,
                campaignId = campaignId,
                endTimestampMs = endTimestampMs,
                badge = badge,
                title = title,
                subtitle = subtitle,
                buttonText = buttonText
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseStringMap(obj: JsonObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        for ((key, value) in obj) {
            val str = value.jsonPrimitive.content
            if (str.isNotBlank()) {
                result[key.lowercase().trim()] = str.trim()
            }
        }
        return result
    }
}
