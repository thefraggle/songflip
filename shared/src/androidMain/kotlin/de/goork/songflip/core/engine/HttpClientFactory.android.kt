package de.goork.songflip.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = true
    install(HttpTimeout) {
        requestTimeoutMillis = 12000L
        connectTimeoutMillis = 8000L
        socketTimeoutMillis = 12000L
    }
}

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
