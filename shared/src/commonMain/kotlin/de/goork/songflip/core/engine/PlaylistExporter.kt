package de.goork.songflip.core.engine

import de.goork.songflip.core.model.PlaylistChunk
import de.goork.songflip.core.model.PlaylistTrackItem

/**
 * Universal export and chunking engine for playlists.
 * Supports standards-compliant M3U8 (Extended M3U, UTF-8) and RFC 4180 CSV exports,
 * as well as 50-track chunking to stay within streaming service URL/intent limits.
 */
object PlaylistExporter {

    const val DEFAULT_CHUNK_SIZE = 50

    /**
     * Generates an Extended M3U8 playlist string encoded in UTF-8.
     * Compatible with VLC, Apple Music Desktop, Navidrome, Foobar, and community players.
     */
    fun exportToM3u8(title: String, tracks: List<PlaylistTrackItem>): String {
        val safeTitle = title.ifBlank { "SongFlip Playlist" }.replace("\n", " ").trim()
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#EXTENC:UTF-8\n")
        sb.append("#PLAYLIST:").append(safeTitle).append("\n\n")

        for (track in tracks) {
            val artist = track.artist.ifBlank { "Unknown Artist" }.replace("\n", " ").trim()
            val trackTitle = track.title.ifBlank { "Unknown Title" }.replace("\n", " ").trim()
            val playbackUrl = track.targetUrl ?: track.sourceUrl ?: ""

            sb.append("#EXTINF:-1,").append(artist).append(" - ").append(trackTitle).append("\n")
            if (playbackUrl.isNotBlank()) {
                sb.append(playbackUrl).append("\n")
            } else {
                sb.append("# No URL available\n")
            }
        }

        return sb.toString()
    }

    /**
     * Generates an RFC 4180 compliant CSV string.
     * Columns: Index, Title, Artist, Matched, TargetUrl, SourceUrl
     */
    fun exportToCsv(title: String, tracks: List<PlaylistTrackItem>): String {
        val sb = StringBuilder()
        sb.append("\"Index\",\"Title\",\"Artist\",\"Matched\",\"TargetUrl\",\"SourceUrl\"\n")

        tracks.forEachIndexed { index, track ->
            val trackIndex = (index + 1).toString()
            val cleanTitle = escapeCsv(track.title)
            val cleanArtist = escapeCsv(track.artist)
            val matched = track.matched.toString()
            val targetUrl = escapeCsv(track.targetUrl ?: "")
            val sourceUrl = escapeCsv(track.sourceUrl ?: "")

            sb.append("\"").append(trackIndex).append("\",")
                .append("\"").append(cleanTitle).append("\",")
                .append("\"").append(cleanArtist).append("\",")
                .append("\"").append(matched).append("\",")
                .append("\"").append(targetUrl).append("\",")
                .append("\"").append(sourceUrl).append("\"\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\n", " ").replace("\r", "")
    }

    /**
     * Splits a list of tracks into 50-track chunks for Zero-OAuth deeplink compatibility.
     * Computes the respective range and matched counts for each chunk.
     */
    fun calculateChunks(
        tracks: List<PlaylistTrackItem>,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        targetPlatform: String = "",
        playlistTitle: String = ""
    ): List<PlaylistChunk> {
        if (tracks.isEmpty()) return emptyList()

        val chunks = tracks.chunked(chunkSize)
        return chunks.mapIndexed { index, chunkTracks ->
            val start = (index * chunkSize) + 1
            val end = start + chunkTracks.size - 1
            val matchedCount = chunkTracks.count { it.matched }

            val zeroOAuthUrl = buildChunkZeroOAuthUrl(
                title = playlistTitle,
                partIndex = index + 1,
                targetPlatform = targetPlatform,
                chunkTracks = chunkTracks
            )

            PlaylistChunk(
                partIndex = index + 1,
                rangeStart = start,
                rangeEnd = end,
                zeroOAuthUrl = zeroOAuthUrl,
                matchedCount = matchedCount,
                totalTracks = chunkTracks.size
            )
        }
    }

    /**
     * Builds platform-specific Zero-OAuth URL for a specific chunk.
     */
    fun buildChunkZeroOAuthUrl(
        title: String,
        partIndex: Int,
        targetPlatform: String,
        chunkTracks: List<PlaylistTrackItem>
    ): String? {
        val platform = targetPlatform.lowercase()
        val matchedWithIds = chunkTracks.filter { it.matched }

        return when {
            platform == "spotify" -> {
                val ids = matchedWithIds.mapNotNull { it.targetId ?: extractSpotifyTrackId(it.targetUrl) }
                if (ids.isNotEmpty()) {
                    val safeTitle = title.ifBlank { "SongFlip Playlist" }
                        .replace(":", " ")
                        .replace("/", " ")
                        .trim()
                    val partTitle = "$safeTitle (Part $partIndex)"
                    "spotify:trackset:$partTitle:${ids.joinToString(",")}"
                } else null
            }
            platform == "youtubemusic" || platform == "youtube" -> {
                val videoIds = matchedWithIds.mapNotNull { it.targetId ?: extractYouTubeVideoId(it.targetUrl) }
                if (videoIds.isNotEmpty()) {
                    // watch_videos allows batch queuing on YouTube/YouTube Music
                    "https://music.youtube.com/watch_videos?video_ids=${videoIds.joinToString(",")}"
                } else null
            }
            else -> {
                // For services without batch trackset deeplinks (Apple Music, Deezer, etc.),
                // link to first matched song
                matchedWithIds.firstOrNull()?.targetUrl
            }
        }
    }

    private fun extractSpotifyTrackId(url: String?): String? {
        if (url == null) return null
        val match = Regex("""(?:spotify\.com/track/|spotify:track:)([a-zA-Z0-9]{22})""").find(url)
        return match?.groupValues?.get(1)
    }

    private fun extractYouTubeVideoId(url: String?): String? {
        if (url == null) return null
        val match = Regex("""(?:v=|youtu\.be/|watch\?v=)([a-zA-Z0-9_-]{11})""").find(url)
        return match?.groupValues?.get(1)
    }
}
