package de.goork.songflip.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PromoBannerConfig(
    val enabled: Boolean = false,
    val campaignId: String = "",
    val endTimestampMs: Long = 0L,
    val badge: Map<String, String> = emptyMap(),
    val title: Map<String, String> = emptyMap(),
    val subtitle: Map<String, String> = emptyMap(),
    val buttonText: Map<String, String> = emptyMap()
) {
    fun getLocalizedBadge(locale: String): String {
        return resolveLocalization(badge, locale)
    }

    fun getLocalizedTitle(locale: String): String {
        return resolveLocalization(title, locale)
    }

    fun getLocalizedSubtitle(locale: String): String {
        return resolveLocalization(subtitle, locale)
    }

    fun getLocalizedButtonText(locale: String): String {
        return resolveLocalization(buttonText, locale)
    }

    private fun resolveLocalization(map: Map<String, String>, locale: String): String {
        if (map.isEmpty()) return ""
        val clean = locale.lowercase().replace("_", "-").trim()
        val primary = clean.substringBefore("-").trim()
        return map[clean] ?: map[primary] ?: map["en"] ?: map.values.firstOrNull() ?: ""
    }

    fun isValid(currentTimeMs: Long): Boolean {
        if (!enabled) return false
        if (endTimestampMs > 0 && currentTimeMs >= endTimestampMs) return false
        return true
    }
}
