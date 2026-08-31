package de.goork.songflip.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSDate
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Foundation.timeIntervalSince1970

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    followRedirects = true
    engine {
        handleChallenge { _, _, challenge, completionHandler ->
            val protectionSpace = challenge.protectionSpace
            val host = protectionSpace.host
            if (host.contains("goork.de") || host.contains("songflip.link")) {
                val serverTrust = protectionSpace.serverTrust
                if (serverTrust != null) {
                    val credential = NSURLCredential.credentialForTrust(serverTrust)
                    completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                    return@handleChallenge
                }
            }
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
        }
    }
}

actual fun getCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
