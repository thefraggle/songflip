package de.goork.songflip.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SongMetadata(
    val title: String? = null,
    val artist: String? = null,
    val isAlbum: Boolean = false
)

@Serializable
sealed class ResolutionResult {
    @Serializable
    data class Success(
        val targetUrl: String,
        val platform: String,
        val title: String? = null,
        val artist: String? = null,
        val isAlbum: Boolean = false,
        val nativeAppUri: String? = null
    ) : ResolutionResult()

    @Serializable
    data class Error(
        val message: String,
        val isUnsupported: Boolean = false
    ) : ResolutionResult()
}
