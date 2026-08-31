package de.goork.songflip.core.analytics

import de.goork.songflip.core.engine.createPlatformHttpClient
import de.goork.songflip.core.engine.getCurrentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class SystemProps(
    val isDebug: Boolean,
    val osName: String,
    val osVersion: String,
    val locale: String,
    val appVersion: String,
    val appBuildNumber: String,
    val sdkVersion: String = "aptabase-kmp@1.0.0"
)

@Serializable
data class AptabaseEvent(
    val timestamp: String,
    val sessionId: String,
    val eventName: String,
    val systemProps: SystemProps,
    val props: Map<String, JsonElement> = emptyMap()
)

object AptabaseClient {
    val shared: AptabaseClient get() = this

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client: HttpClient by lazy { createPlatformHttpClient() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var appKey: String = ""
    private var host: String = ""
    private var systemProps: SystemProps? = null

    private var currentSessionId: String = ""
    private var lastEventTimestamp: Long = 0
    private val sessionTimeoutMs = 60 * 60 * 1000L // 1 hour

    fun init(
        appKey: String,
        host: String,
        osName: String,
        osVersion: String,
        locale: String,
        appVersion: String,
        appBuildNumber: String,
        isDebug: Boolean
    ) {
        this.appKey = appKey
        this.host = host.trimEnd('/')
        this.systemProps = SystemProps(
            isDebug = isDebug,
            osName = osName,
            osVersion = osVersion,
            locale = locale,
            appVersion = appVersion,
            appBuildNumber = appBuildNumber
        )
        refreshSessionId()
    }

    private fun refreshSessionId() {
        currentSessionId = generateRandomSessionId()
        lastEventTimestamp = getCurrentTimeMillis()
    }

    private fun getValidSessionId(): String {
        val now = getCurrentTimeMillis()
        if (currentSessionId.isEmpty() || (now - lastEventTimestamp) > sessionTimeoutMs) {
            refreshSessionId()
        }
        lastEventTimestamp = now
        return currentSessionId
    }

    fun trackEvent(eventName: String, props: Map<String, String> = emptyMap()) {
        val key = appKey
        val h = host
        val sys = systemProps ?: return
        if (key.isEmpty() || h.isEmpty()) return

        val jsonProps = buildJsonObject {
            props.forEach { (k, v) ->
                put(k, JsonPrimitive(v))
            }
        }

        val event = AptabaseEvent(
            timestamp = getIso8601Timestamp(),
            sessionId = getValidSessionId(),
            eventName = eventName,
            systemProps = sys,
            props = jsonProps
        )

        scope.launch {
            try {
                val url = "$h/api/v0/event"
                val bodyText = json.encodeToString(AptabaseEvent.serializer(), event)
                client.post(url) {
                    header("App-Key", key)
                    contentType(ContentType.Application.Json)
                    setBody(bodyText)
                }
            } catch (_: Throwable) {
                // Silently swallow analytics dispatch errors to never disrupt app UX
            }
        }
    }

    fun trackAppLaunched(platform: String, language: String) {
        trackEvent("app_launched", mapOf("platform" to platform, "language" to language))
    }

    fun trackLinkFlipped(target: String, isAlbum: Boolean, isSearch: Boolean) {
        trackEvent(
            "link_flipped",
            mapOf(
                "target" to target,
                "is_album" to isAlbum.toString(),
                "is_search" to isSearch.toString()
            )
        )
    }

    fun trackHistoryOpened() {
        trackEvent("history_opened")
    }

    fun trackSettingsOpened() {
        trackEvent("settings_opened")
    }

    fun trackSharePageGenerated(target: String) {
        trackEvent("share_page_generated", mapOf("target" to target))
    }

    fun trackPromoRedeemedSuccess(code: String) {
        trackEvent("promo_redeemed_success", mapOf("code" to code))
    }
}
