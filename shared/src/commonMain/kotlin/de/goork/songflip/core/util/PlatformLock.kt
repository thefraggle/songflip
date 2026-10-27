package de.goork.songflip.core.util

expect class PlatformLock() {
    fun <T> withLock(action: () -> T): T
}
