package de.goork.songflip.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistTrackItem(
    val title: String,
    val artist: String,
    val matched: Boolean = false,
    val targetUrl: String? = null,
    val targetId: String? = null,
    val sourceUrl: String? = null,
    val thumbnailUrl: String? = null
)

@Serializable
data class PlaylistChunk(
    val partIndex: Int,
    val rangeStart: Int,
    val rangeEnd: Int,
    val zeroOAuthUrl: String? = null,
    val matchedCount: Int = 0,
    val totalTracks: Int = 0
)

@Serializable
data class PlaylistConversionResult(
    val status: String = "success",
    val playlistId: String = "",
    val title: String = "",
    val sourcePlatform: String = "",
    val targetPlatform: String = "",
    val thumbnailUrl: String? = null,
    val totalTracks: Int = 0,
    val originalTotalTracks: Int = 0,
    val convertedTracks: Int = 0,
    val matchedCount: Int = 0,
    val zeroOAuthUrl: String? = null,
    val webShareUrl: String? = null,
    val isLimited: Boolean = false,
    val tracks: List<PlaylistTrackItem> = emptyList(),
    val parts: List<PlaylistChunk> = emptyList(),
    val duplicateTracksRemoved: Int = 0
)

fun PlaylistConversionResult.deduplicated(): PlaylistConversionResult {
    if (tracks.isEmpty()) return this
    val seen = mutableSetOf<String>()
    val uniqueTracks = mutableListOf<PlaylistTrackItem>()
    for (t in tracks) {
        val normTitle = t.title.lowercase().trim()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("[^a-z0-9]".toRegex(), "")
        val normArtist = t.artist.lowercase().trim()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("[^a-z0-9]".toRegex(), "")
        val key = "${normArtist}_${normTitle}"
        if (seen.add(key)) {
            uniqueTracks.add(t)
        }
    }
    val removed = tracks.size - uniqueTracks.size
    return if (removed > 0) {
        copy(
            tracks = uniqueTracks,
            convertedTracks = uniqueTracks.size,
            matchedCount = uniqueTracks.count { it.matched },
            duplicateTracksRemoved = duplicateTracksRemoved + removed
        )
    } else {
        this
    }
}

@Serializable
enum class PlaylistErrorCode {
    UNSUPPORTED_PLATFORM,
    PRIVATE_OR_RESTRICTED,
    EMPTY_PLAYLIST,
    UPSTREAM_TIMEOUT,
    RATE_LIMITED,
    EXTRACTION_FAILED,
    UNKNOWN_ERROR
}

@Serializable
data class PlaylistErrorPayload(
    val error: String? = null,
    val code: String? = null,
    val message: String? = null,
    val reason: String? = null
)

class PlaylistConversionException(
    val errorCode: PlaylistErrorCode,
    override val message: String,
    val reason: String? = null
) : Exception(message)

sealed class PlaylistConversionState {
    data object Idle : PlaylistConversionState()
    data class Loading(val message: String? = null) : PlaylistConversionState()
    data class Converting(val current: Int, val total: Int) : PlaylistConversionState()
    data class Success(val result: PlaylistConversionResult) : PlaylistConversionState()
    data class Error(
        val message: String,
        val errorCode: PlaylistErrorCode = PlaylistErrorCode.UNKNOWN_ERROR,
        val reason: String? = null
    ) : PlaylistConversionState()
    data object SamePlatform : PlaylistConversionState()
}
