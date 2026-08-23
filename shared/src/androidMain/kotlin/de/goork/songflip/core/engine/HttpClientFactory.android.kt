package de.goork.songflip.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = true
}

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
