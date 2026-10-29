package com.shazam.shazamkit

import java.util.Locale

object ShazamKit {
    var allowLogs: Boolean = false

    fun createSignatureGenerator(sampleRate: AudioSampleRateInHz): ShazamKitResult<SignatureGenerator> {
        return ShazamKitResult.Failure(UnsupportedOperationException("ShazamKit stub implementation"))
    }

    fun createShazamCatalog(tokenProvider: DeveloperTokenProvider, locale: Locale): Any {
        return Any()
    }

    fun createSession(catalog: Any): ShazamKitResult<Session> {
        return ShazamKitResult.Failure(UnsupportedOperationException("ShazamKit stub implementation"))
    }
}

interface SignatureGenerator {
    fun append(data: ByteArray, length: Int, timestampMs: Long)
    fun generateSignature(): Signature
}

interface Session {
    suspend fun match(signature: Signature): MatchResult
}
