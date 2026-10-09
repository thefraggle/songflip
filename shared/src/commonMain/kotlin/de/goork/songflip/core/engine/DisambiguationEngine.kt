package de.goork.songflip.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DisambiguationCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val releaseYear: String? = null,
    val durationMs: Long? = null,
    val targetUrl: String,
    val nativeAppUri: String? = null,
    val coverUrl: String? = null,
    val isCurrentMatch: Boolean = false
)

class DisambiguationEngine(
    private val client: HttpClient = createPlatformHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    companion object {
        val shared: DisambiguationEngine by lazy { DisambiguationEngine() }
    }

    suspend fun getCandidates(
        title: String,
        artist: String,
        targetPlatformKey: String,
        currentMatchedUrl: String? = null,
        limit: Int = 6
    ): Result<List<DisambiguationCandidate>> {
        val cleanQuery = "$artist $title".trim()
        val encoded = cleanQuery.encodeURLParameter()

        return try {
            val candidates = mutableListOf<DisambiguationCandidate>()

            when (targetPlatformKey.lowercase()) {
                "applemusic" -> {
                    val resp = client.get("https://itunes.apple.com/search?term=$encoded&entity=song&limit=$limit")
                    if (resp.status.isSuccess()) {
                        val parsed = json.decodeFromString<ITunesSearchResponse>(resp.bodyAsText())
                        parsed.results.forEach { item ->
                            val tName = item.trackName?.trim()
                            val aName = item.artistName?.trim()
                            val url = item.trackViewUrl
                            if (!tName.isNullOrEmpty() && !aName.isNullOrEmpty() && !url.isNullOrEmpty()) {
                                val highResCover = item.artworkUrl100?.replace("100x100bb", "600x600bb")
                                candidates.add(
                                    DisambiguationCandidate(
                                        id = item.trackId?.toString() ?: url,
                                        title = tName,
                                        artist = aName,
                                        album = item.collectionName,
                                        durationMs = item.trackTimeMillis,
                                        targetUrl = url,
                                        coverUrl = highResCover ?: item.artworkUrl100,
                                        isCurrentMatch = currentMatchedUrl != null && (url.contains(currentMatchedUrl) || currentMatchedUrl.contains(url))
                                    )
                                )
                            }
                        }
                    }
                }
                "deezer" -> {
                    val resp = client.get("https://api.deezer.com/search?q=$encoded&limit=$limit")
                    if (resp.status.isSuccess()) {
                        val parsed = json.decodeFromString<DeezerSearchResponse>(resp.bodyAsText())
                        parsed.data.forEach { item ->
                            val tName = item.title?.trim()
                            val aName = item.artist?.name?.trim()
                            val url = item.link
                            if (!tName.isNullOrEmpty() && !aName.isNullOrEmpty() && !url.isNullOrEmpty()) {
                                candidates.add(
                                    DisambiguationCandidate(
                                        id = item.id?.toString() ?: url,
                                        title = tName,
                                        artist = aName,
                                        album = item.album?.title,
                                        durationMs = item.duration?.let { it * 1000L },
                                        targetUrl = url,
                                        coverUrl = item.album?.cover_medium,
                                        isCurrentMatch = currentMatchedUrl != null && (url.contains(currentMatchedUrl) || currentMatchedUrl.contains(url))
                                    )
                                )
                            }
                        }
                    }
                }
                else -> {
                    val resp = client.get("https://api.deezer.com/search?q=$encoded&limit=$limit")
                    if (resp.status.isSuccess()) {
                        val parsed = json.decodeFromString<DeezerSearchResponse>(resp.bodyAsText())
                        parsed.data.forEach { item ->
                            val tName = item.title?.trim()
                            val aName = item.artist?.name?.trim()
                            if (!tName.isNullOrEmpty() && !aName.isNullOrEmpty()) {
                                val url = when (targetPlatformKey.lowercase()) {
                                    "youtubemusic" -> "https://music.youtube.com/search?q=${(aName + " " + tName).encodeURLParameter()}"
                                    "spotify" -> "https://open.spotify.com/search/${(aName + " " + tName).encodeURLParameter()}"
                                    "tidal" -> "https://listen.tidal.com/search?q=${(aName + " " + tName).encodeURLParameter()}"
                                    else -> item.link ?: ""
                                }
                                candidates.add(
                                    DisambiguationCandidate(
                                        id = item.id?.toString() ?: "${aName}_$tName",
                                        title = tName,
                                        artist = aName,
                                        album = item.album?.title,
                                        durationMs = item.duration?.let { it * 1000L },
                                        targetUrl = url,
                                        coverUrl = item.album?.cover_medium,
                                        isCurrentMatch = currentMatchedUrl != null && (url.contains(currentMatchedUrl) || currentMatchedUrl.contains(url))
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Result.success(candidates)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
