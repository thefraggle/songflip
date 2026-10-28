package de.goork.songflip.core.util

import kotlin.concurrent.Volatile

/**
 * Cross-platform logging abstraction for SongFlip KMP core modules.
 * Allows platforms (Android, iOS) or test suites to inject custom logging handlers.
 */
object SongFlipLogger {

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Pluggable log handler: (level, tag, message, throwable) -> Unit.
     */
    @Volatile
    var handler: ((level: Level, tag: String, message: String, throwable: Throwable?) -> Unit)? = null

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(Level.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Level.ERROR, tag, message, throwable)

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val h = handler
        if (h != null) {
            h(level, tag, message, throwable)
        } else {
            val prefix = when (level) {
                Level.DEBUG -> "[DEBUG]"
                Level.INFO -> "[INFO]"
                Level.WARN -> "[WARN]"
                Level.ERROR -> "[ERROR]"
            }
            val errSuffix = throwable?.let { ": ${it.message ?: it::class.simpleName}" } ?: ""
            println("$prefix $tag: $message$errSuffix")
        }
    }
}
