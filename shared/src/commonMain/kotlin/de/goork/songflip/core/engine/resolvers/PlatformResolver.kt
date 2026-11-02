package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.engine.resilience.ResolutionOutcome
import de.goork.songflip.core.model.MusicPlatform

interface PlatformResolver {
    val platform: MusicPlatform
    suspend fun resolveTrack(query: String, audiophileMode: Boolean = false): String?
    suspend fun resolveAlbum(query: String, audiophileMode: Boolean = false): String?
    suspend fun resolveArtist(artistName: String): String? = null

    suspend fun resolveTrackDetailed(query: String, audiophileMode: Boolean = false): ResolutionOutcome<String> =
        resolveTrack(query, audiophileMode)?.let { ResolutionOutcome.Success(it) } ?: ResolutionOutcome.NotFound

    suspend fun resolveAlbumDetailed(query: String, audiophileMode: Boolean = false): ResolutionOutcome<String> =
        resolveAlbum(query, audiophileMode)?.let { ResolutionOutcome.Success(it) } ?: ResolutionOutcome.NotFound
}
