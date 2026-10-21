package de.goork.songflip.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.cinterop.convert
import platform.Foundation.NSDate
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.timeIntervalSince1970

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    followRedirects = true
    install(HttpTimeout) {
        requestTimeoutMillis = 12000L
        connectTimeoutMillis = 8000L
        socketTimeoutMillis = 12000L
    }
    engine {
        handleChallenge { _, _, _, completionHandler ->
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling.convert(), null)
        }
    }
}

actual fun getCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
