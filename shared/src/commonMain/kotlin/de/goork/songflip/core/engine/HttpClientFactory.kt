package de.goork.songflip.core.engine

import io.ktor.client.HttpClient

expect fun createPlatformHttpClient(): HttpClient

expect fun getCurrentTimeMillis(): Long
