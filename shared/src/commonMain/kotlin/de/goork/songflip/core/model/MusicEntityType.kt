package de.goork.songflip.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MusicEntityType {
    TRACK,
    ALBUM,
    PLAYLIST,
    ARTIST,
    SEARCH,
    UNKNOWN
}
