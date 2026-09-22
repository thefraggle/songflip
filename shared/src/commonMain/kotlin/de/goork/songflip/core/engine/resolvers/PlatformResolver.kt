package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform

interface PlatformResolver {
    val platform: MusicPlatform
    suspend fun resolveTrack(query: String): String?
    suspend fun resolveAlbum(query: String): String?
    suspend fun resolveArtist(artistName: String): String? = null
}
