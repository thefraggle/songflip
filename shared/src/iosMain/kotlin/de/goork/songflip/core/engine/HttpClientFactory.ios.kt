package de.goork.songflip.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    followRedirects = true
}

actual fun getCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
