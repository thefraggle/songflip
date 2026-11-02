package de.goork.songflip.core.engine.resilience

import de.goork.songflip.core.util.PlatformLock

object DynamicTokenRegistry {
    private val lock = PlatformLock()
    private val dynamicTokens = mutableMapOf<String, String>()

    const val KEY_TIDAL_TOKEN = "tidal_app_token"
    const val KEY_QOBUZ_APP_ID = "qobuz_app_id"

    const val DEFAULT_TIDAL_TOKEN = "CzET4vdadNUFQ5JU"
    const val DEFAULT_QOBUZ_APP_ID = "712109809"

    fun setToken(key: String, token: String?) {
        lock.withLock {
            if (token.isNullOrBlank()) {
                dynamicTokens.remove(key)
            } else {
                dynamicTokens[key] = token.trim()
            }
        }
    }

    fun getTidalToken(): String {
        return lock.withLock { dynamicTokens[KEY_TIDAL_TOKEN] ?: DEFAULT_TIDAL_TOKEN }
    }

    fun getQobuzAppId(): String {
        return lock.withLock { dynamicTokens[KEY_QOBUZ_APP_ID] ?: DEFAULT_QOBUZ_APP_ID }
    }

    fun reset() {
        lock.withLock {
            dynamicTokens.clear()
        }
    }
}
