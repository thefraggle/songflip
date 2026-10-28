package de.goork.songflip.core.engine

import de.goork.songflip.core.engine.resolvers.ResolverUtils
import de.goork.songflip.core.util.SongFlipLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SearchTrackResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val previewUrl: String? = null,
    val durationMs: Long? = null,
    val sourcePlatform: String = "appleMusic",
    val sourceUrl: String? = null
)

@Serializable
internal data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesSearchItem> = emptyList()
)

@Serializable
internal data class ITunesSearchItem(
    val trackId: Long? = null,
    val artistName: String? = null,
    val trackName: String? = null,
    val collectionName: String? = null,
    val trackViewUrl: String? = null,
    val previewUrl: String? = null,
    val artworkUrl100: String? = null,
    val trackTimeMillis: Long? = null
)

@Serializable
internal data class DeezerSearchResponse(
    val data: List<DeezerSearchItem> = emptyList()
)

@Serializable
internal data class DeezerSearchItem(
    val id: Long? = null,
    val title: String? = null,
    val link: String? = null,
    val preview: String? = null,
    val duration: Long? = null,
    val artist: DeezerSearchArtist? = null,
    val album: DeezerSearchAlbum? = null
)

@Serializable
internal data class DeezerSearchArtist(
    val id: Long? = null,
    val name: String? = null
)

@Serializable
internal data class DeezerSearchAlbum(
    val id: Long? = null,
    val title: String? = null,
    val cover_medium: String? = null
)

class CatalogSearchEngine(
    private val client: HttpClient = createPlatformHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    companion object {
        val shared: CatalogSearchEngine by lazy { CatalogSearchEngine() }

        private val REMASTER_PAREN_REGEX = Regex(
            """\s*[\(\[]\s*(?:\d{4}\s+)?(?:remaster(?:ed)?(?:\s+\d{4})?|single\s+version|album\s+version|original\s+version|radio\s+edit|explicit|clean)\s*[\)\]]""",
            RegexOption.IGNORE_CASE
        )
        private val REMASTER_DASH_REGEX = Regex(
            """\s*[-–—]\s*(?:\d{4}\s+)?(?:remaster(?:ed)?(?:\s+\d{4})?|single\s+version|album\s+version|original\s+version)$""",
            RegexOption.IGNORE_CASE
        )
        private val FEAT_SUFFIX_REGEX = Regex(
            """\s*(?:[\(\[]\s*(?:feat\.?|ft\.?|featuring|with)\s+[^\)\]]+[\)\]]|(?:feat\.?|ft\.?|featuring)\s+.+)$""",
            RegexOption.IGNORE_CASE
        )
        private val COMPILATION_ALBUM_KEYWORDS = listOf(
            "greatest hits", "best of", "celebration", "the immaculate collection",
            "essentials", "number ones", "gold", "anthology", "the collection",
            "the singles", "complete", "remastered", "various artists", "top 100"
        )

        internal fun normalizeDedupKey(artist: String, title: String): String {
            val baseArtist = artist
                .replace(FEAT_SUFFIX_REGEX, "")
                .lowercase()
                .replace(Regex("[^a-z0-9\\p{L}]"), "")
                .trim()
            val baseTitle = ResolverUtils.stripEditionSuffix(title)
                .replace(FEAT_SUFFIX_REGEX, "")
                .replace(REMASTER_PAREN_REGEX, "")
                .replace(REMASTER_DASH_REGEX, "")
                .lowercase()
                .replace(Regex("[^a-z0-9\\p{L}]"), "")
                .trim()
            return "$baseArtist|$baseTitle"
        }

        private fun isCompilationOrRemaster(track: SearchTrackResult): Boolean {
            val lowerAlbum = track.album?.lowercase().orEmpty()
            val lowerTitle = track.title.lowercase()
            if (COMPILATION_ALBUM_KEYWORDS.any { lowerAlbum.contains(it) }) return true
            if (REMASTER_PAREN_REGEX.containsMatchIn(lowerTitle) || REMASTER_DASH_REGEX.containsMatchIn(lowerTitle)) return true
            return false
        }

        /**
         * Deduplicates identical tracks across original studio releases, Greatest Hits compilations,
         * and remastered re-issues (#137), preferring canonical studio album releases while preserving
         * the API relevance ranking and distinct tracks (e.g. different artists or distinct remixes/live versions).
         */
        internal fun deduplicateTracks(tracks: List<SearchTrackResult>, maxResults: Int): List<SearchTrackResult> {
            if (tracks.isEmpty()) return emptyList()
            val dedupedByKey = LinkedHashMap<String, SearchTrackResult>()
            for (track in tracks) {
                val key = normalizeDedupKey(track.artist, track.title)
                val existing = dedupedByKey[key]
                if (existing == null) {
                    dedupedByKey[key] = track
                } else if (isCompilationOrRemaster(existing) && !isCompilationOrRemaster(track)) {
                    // Replace compilation/remastered duplicate with the canonical studio release at the same rank slot
                    dedupedByKey[key] = track
                }
            }
            return dedupedByKey.values.take(maxResults)
        }
    }

    @Throws(Throwable::class)
    suspend fun searchOrThrow(query: String, limit: Int = 35): List<SearchTrackResult> {
        return search(query, limit).getOrThrow()
    }

    suspend fun search(query: String, limit: Int = 35): Result<List<SearchTrackResult>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        val fetchLimit = maxOf(limit * 2, 50).coerceAtMost(100)

        // 1. Primary: iTunes Search API (fast, metadata-rich, reliable)
        try {
            val encoded = trimmed.encodeURLParameter()
            val resp = client.get("https://itunes.apple.com/search?term=$encoded&entity=song&limit=$fetchLimit")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<ITunesSearchResponse>(resp.bodyAsText())
                val tracks = parsed.results.mapNotNull { item ->
                    val title = item.trackName?.trim()
                    val artist = item.artistName?.trim()
                    if (title.isNullOrEmpty() || artist.isNullOrEmpty()) null
                    else {
                        val highResCover = item.artworkUrl100?.replace("100x100bb", "600x600bb")
                        SearchTrackResult(
                            id = item.trackId?.toString() ?: "${artist}_$title",
                            title = title,
                            artist = artist,
                            album = item.collectionName,
                            coverUrl = highResCover ?: item.artworkUrl100,
                            previewUrl = item.previewUrl,
                            durationMs = item.trackTimeMillis,
                            sourcePlatform = "appleMusic",
                            sourceUrl = item.trackViewUrl
                        )
                    }
                }
                if (tracks.isNotEmpty()) {
                    return Result.success(deduplicateTracks(tracks, limit))
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            SongFlipLogger.w("CatalogSearchEngine", "iTunes search failed, falling back to Deezer", t)
        }

        // 2. Fallback: Deezer Search API
        return try {
            val encoded = trimmed.encodeURLParameter()
            val resp = client.get("https://api.deezer.com/search?q=$encoded&limit=$fetchLimit")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<DeezerSearchResponse>(resp.bodyAsText())
                val tracks = parsed.data.mapNotNull { item ->
                    val title = item.title?.trim()
                    val artist = item.artist?.name?.trim()
                    if (title.isNullOrEmpty() || artist.isNullOrEmpty()) null
                    else {
                        SearchTrackResult(
                            id = item.id?.toString() ?: "${artist}_$title",
                            title = title,
                            artist = artist,
                            album = item.album?.title,
                            coverUrl = item.album?.cover_medium,
                            previewUrl = item.preview,
                            durationMs = item.duration?.let { it * 1000L },
                            sourcePlatform = "deezer",
                            sourceUrl = item.link
                        )
                    }
                }
                Result.success(deduplicateTracks(tracks, limit))
            } else {
                Result.success(emptyList())
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
