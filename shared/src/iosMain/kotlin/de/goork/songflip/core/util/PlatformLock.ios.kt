package de.goork.songflip.core.util

import platform.Foundation.NSLock

actual class PlatformLock actual constructor() {
    private val lock = NSLock()
    actual fun <T> withLock(action: () -> T): T {
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
