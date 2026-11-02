package de.goork.songflip.core.engine.resilience

import de.goork.songflip.core.engine.getCurrentTimeMillis
import de.goork.songflip.core.util.PlatformLock

class PlatformCircuitBreaker(
    private val clock: () -> Long = { getCurrentTimeMillis() },
    private val defaultCooldownMs: Long = 5 * 60 * 1000L // 5 minutes default
) {
    private val lock = PlatformLock()
    private val hostStates = mutableMapOf<String, HostState>()

    data class HostState(
        val isOpen: Boolean,
        val openedAtMs: Long,
        val cooldownMs: Long,
        val consecutiveBreaks: Int
    )

    fun canExecute(host: String): Boolean {
        val now = clock()
        return lock.withLock {
            val state = hostStates[host] ?: return@withLock true
            if (!state.isOpen) return@withLock true
            if (now - state.openedAtMs >= state.cooldownMs) {
                // Cooldown elapsed -> half-open probe attempt allowed
                return@withLock true
            }
            false
        }
    }

    fun recordSuccess(host: String) {
        lock.withLock {
            hostStates.remove(host)
        }
    }

    fun recordRateLimit(host: String, retryAfterSeconds: Long? = null) {
        val now = clock()
        val cooldown = if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            retryAfterSeconds * 1000L
        } else {
            defaultCooldownMs
        }
        lock.withLock {
            val previous = hostStates[host]
            val breaks = (previous?.consecutiveBreaks ?: 0) + 1
            hostStates[host] = HostState(
                isOpen = true,
                openedAtMs = now,
                cooldownMs = cooldown,
                consecutiveBreaks = breaks
            )
        }
    }

    fun getCooldownRemainingSeconds(host: String): Long {
        val now = clock()
        return lock.withLock {
            val state = hostStates[host] ?: return@withLock 0L
            if (!state.isOpen) return@withLock 0L
            val elapsed = now - state.openedAtMs
            val remaining = (state.cooldownMs - elapsed) / 1000L
            if (remaining > 0L) remaining else 0L
        }
    }

    fun isHostOpen(host: String): Boolean {
        val now = clock()
        return lock.withLock {
            val state = hostStates[host] ?: return@withLock false
            state.isOpen && (now - state.openedAtMs < state.cooldownMs)
        }
    }

    fun reset() {
        lock.withLock {
            hostStates.clear()
        }
    }

    companion object {
        val shared = PlatformCircuitBreaker()
    }
}
