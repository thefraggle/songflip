package de.goork.songflip.core.analytics

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.SerializationException

/**
 * Maps exceptions to stable telemetry identifiers.
 *
 * Release builds are minified by R8, so `t::class.simpleName` arrives as "f0" and is useless
 * for analysis (issue #59). Only types known here get a name; everything else is "unknown_exception",
 * with the sanitized message carried separately in `error_detail`.
 */
object FlipErrorClassifier {

    const val UNKNOWN = "unknown_exception"
    private const val MAX_DETAIL_LENGTH = 64
    private val URL_PATTERN = Regex("""https?://\S+""")
    private val WHITESPACE = Regex("""\s+""")

    /** Order matters: the specific Ktor timeouts are IOException subclasses on JVM. */
    fun classify(t: Throwable): String = when (t) {
        is HttpRequestTimeoutException -> "timeout"
        is ConnectTimeoutException, is SocketTimeoutException -> "socket_timeout"
        is SerializationException -> "parse_error"
        is IOException -> "io_error"
        else -> UNKNOWN
    }

    /**
     * Short, privacy-safe excerpt of the exception message: URLs (which may carry user-specific
     * share links) are replaced, whitespace collapsed, length capped for Aptabase props.
     */
    fun detail(t: Throwable): String? {
        val message = t.message?.takeIf { it.isNotBlank() } ?: return null
        return message
            .replace(URL_PATTERN, "<url>")
            .replace(WHITESPACE, " ")
            .trim()
            .take(MAX_DETAIL_LENGTH)
    }
}
