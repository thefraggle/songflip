package de.goork.songflip.core.engine

import kotlin.concurrent.Volatile

/**
 * Global configuration constants and optional proprietary backend endpoints for SongFlip KMP Core Engine.
 *
 * By default, proprietary cloud backend URLs are empty so open-source forks compile and run
 * completely locally without calling SongFlip's hosted infrastructure.
 */
object EngineConfig {
    const val APP_VERSION = "1.15.7"

    @Volatile
    var backendCacheBaseUrl: String = ""
        private set

    @Volatile
    var backendWebBaseUrl: String = ""
        private set

    fun configureBackend(cacheBaseUrl: String, webBaseUrl: String) {
        backendCacheBaseUrl = cacheBaseUrl.trim().trimEnd('/')
        backendWebBaseUrl = webBaseUrl.trim().trimEnd('/')
    }

    fun buildBackendEndpoints(path: String): List<String> {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return listOf(backendCacheBaseUrl, backendWebBaseUrl)
            .filter { it.isNotBlank() }
            .distinct()
            .map { "$it$normalizedPath" }
    }

    fun promoBannerEndpoint(): String {
        val base = backendWebBaseUrl.ifBlank { backendCacheBaseUrl }
        return if (base.isBlank()) "" else "$base/api/promo-banner"
    }
}
