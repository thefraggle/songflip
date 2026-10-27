package de.goork.songflip.core.util

actual class PlatformLock actual constructor() {
    private val lock = Any()
    actual fun <T> withLock(action: () -> T): T = synchronized(lock, action)
}
