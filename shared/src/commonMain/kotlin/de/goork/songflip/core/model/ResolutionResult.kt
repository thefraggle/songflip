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
    data class Playlist(
        val originalUrl: String,
        val platform: String,
        val message: String? = null
    ) : ResolutionResult()

    @Serializable
    data class PodcastOrAudiobook(
        val originalUrl: String,
        val platform: String,
        val isAudiobook: Boolean = false,
        val message: String? = null
    ) : ResolutionResult()

    @Serializable
    data class UnsupportedEntity(
        val originalUrl: String,
        val platform: String,
        val entityType: String = "unsupported",
        val message: String? = null
    ) : ResolutionResult()

    @Serializable
    data class Error(
        val message: String,
        val isUnsupported: Boolean = false
    ) : ResolutionResult()
}
