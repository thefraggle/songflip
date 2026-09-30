package de.goork.songflip.core.engine

import de.goork.songflip.core.model.PromoBannerConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
    }

    fun resetDismissed() {
        _isDismissed.value = false
    }

    fun dismiss() {
        _isDismissed.value = true
    }

    fun isBannerVisible(isPro: Boolean, currentTimeMs: Long): Boolean {
        if (isPro) return false
        if (_isDismissed.value) return false
        return _config.value.isValid(currentTimeMs)
    }

    fun fetchPromoBanner(
        endpointUrl: String = "https://songflip-web.web.app/api/promo-banner",
        currentTimeMs: Long = 0L,
        forceRefresh: Boolean = false
    ) {
        scope.launch {
            fetchPromoBannerDirect(endpointUrl, currentTimeMs, forceRefresh)
        }
    }

    suspend fun fetchPromoBannerDirect(
        endpointUrl: String = "https://songflip-web.web.app/api/promo-banner",
        currentTimeMs: Long = 0L,
        forceRefresh: Boolean = false
    ): PromoBannerConfig? {
        val now = if (currentTimeMs > 0L) currentTimeMs else 0L
        if (!forceRefresh && now > 0L && (now - lastFetchTimeMs) < CACHE_TTL_MS) {
            return _config.value
        }

        return try {
            val response = client.get(endpointUrl)
            val body = response.bodyAsText()
            val parsed = parsePromoBannerJson(body)
            if (parsed != null) {
                _config.value = parsed
                lastFetchTimeMs = if (currentTimeMs > 0L) currentTimeMs else 0L
            }
            parsed
        } catch (_: Exception) {
            null
        }
    }

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
