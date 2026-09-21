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

    fun setIsDebug(isDebug: Boolean) {
        systemProps = systemProps?.copy(isDebug = isDebug)
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

    fun trackLinkFlipped(
        target: String,
        isAlbum: Boolean,
        isSearch: Boolean,
        source: String? = null
    ) {
        val params = mutableMapOf(
            "target" to target,
            "is_album" to isAlbum.toString(),
            "is_search" to isSearch.toString()
        )
        if (!source.isNullOrBlank()) {
            params["source"] = source
            params["migration"] = "$source -> $target"
        }
        trackEvent("link_flipped", params)
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

    fun trackPromoRedeemFailed(code: String, error: String) {
        trackEvent("promo_redeem_failed", mapOf("code" to code, "error" to error))
    }

    fun trackLinkFlipFailed(target: String, reason: String) {
        trackEvent("link_flip_failed", mapOf("target" to target, "reason" to reason))
    }

    fun trackPodcastIntercepted(target: String) {
        trackEvent("podcast_intercepted", mapOf("target" to target))
    }

    fun trackAudiobookIntercepted(target: String) {
        trackEvent("audiobook_intercepted", mapOf("target" to target))
    }

    fun trackPlaylistRouted(target: String) {
        trackEvent("playlist_routed", mapOf("target" to target))
    }

    fun trackPlaylistConversionStarted(sourcePlatform: String, targetPlatform: String, trackCount: Int) {
        trackEvent(
            "playlist_conversion_started",
            mapOf(
                "source" to sourcePlatform,
                "target" to targetPlatform,
                "track_count" to trackCount.toString()
            )
        )
    }

    fun trackPlaylistConversionCompleted(
        sourcePlatform: String,
        targetPlatform: String,
        totalTracks: Int,
        resolvedTracks: Int,
        failedTracks: Int,
        isBatch: Boolean = false
    ) {
        trackEvent(
            "playlist_conversion_completed",
            mapOf(
                "source" to sourcePlatform,
                "target" to targetPlatform,
                "total_tracks" to totalTracks.toString(),
                "resolved_tracks" to resolvedTracks.toString(),
                "failed_tracks" to failedTracks.toString(),
                "is_batch" to isBatch.toString()
            )
        )
    }

    fun trackPlaylistConversionFailed(sourcePlatform: String, targetPlatform: String, reason: String) {
        trackEvent(
            "playlist_conversion_failed",
            mapOf(
                "source" to sourcePlatform,
                "target" to targetPlatform,
                "reason" to reason
            )
        )
    }

    fun trackUnsupportedEntityIntercepted(target: String, entityType: String) {
        trackEvent("unsupported_entity_intercepted", mapOf("target" to target, "entity_type" to entityType))
    }

    fun trackTargetPlatformChanged(target: String) {
        trackEvent("target_platform_changed", mapOf("target" to target))
    }

    fun trackDomainSetupClicked() {
        trackEvent("domain_setup_clicked")
    }

    fun trackPauseStateChanged(action: String) {
        trackEvent("pause_state_changed", mapOf("action" to action))
    }

    fun trackPaywallViewed(source: String = "unknown") {
        trackEvent("paywall_viewed", mapOf("source" to source))
    }

    fun trackReviewPromptTriggered(trigger: String = "auto_criteria_met") {
        trackEvent("review_prompt_triggered", mapOf("trigger" to trigger))
    }

    fun trackTestStudioResolved(sourceDomain: String, success: Boolean) {
        trackEvent("test_studio_resolved", mapOf("source_domain" to sourceDomain, "success" to success.toString()))
    }

    fun trackHistoryItemClicked(target: String) {
        trackEvent("history_item_clicked", mapOf("target" to target))
    }

    fun trackHistoryCleared() {
        trackEvent("history_cleared")
    }

    fun trackProPurchased(packageId: String) {
        trackEvent("pro_purchased", mapOf("package_id" to packageId))
    }

    fun trackProPurchaseFailed(error: String) {
        trackEvent("pro_purchase_failed", mapOf("error" to error))
    }

    fun trackRateAppClicked() {
        trackEvent("rate_app_clicked")
    }

    fun trackShareAppClicked() {
        trackEvent("share_app_clicked")
    }
}
