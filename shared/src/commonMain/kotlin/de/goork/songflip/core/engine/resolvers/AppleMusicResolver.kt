package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ITunesResponse(
    val resultCount: Int = 0,
    val results: List<ITunesItem> = emptyList()
)

@Serializable
internal data class ITunesItem(
    val artistName: String? = null,
    val trackName: String? = null,
    val collectionName: String? = null,
    val trackViewUrl: String? = null,
    val collectionViewUrl: String? = null,
    val artistLinkUrl: String? = null,
    val artistViewUrl: String? = null
)

class AppleMusicResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.APPLE_MUSIC

    override suspend fun resolveTrack(query: String): String? {
        return searchAppleMusic(query, isAlbum = false)
    }

    override suspend fun resolveAlbum(query: String): String? {
        return searchAppleMusic(query, isAlbum = true)
    }

    private suspend fun searchAppleMusic(query: String, isAlbum: Boolean = false): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val entity = if (isAlbum) "album" else "song"
            val resp = client.get("https://itunes.apple.com/search?term=$encoded&entity=$entity&limit=5")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<ITunesResponse>(resp.bodyAsText())
                val validItem = parsed.results.firstOrNull { item ->
                    val title = if (isAlbum) item.collectionName else item.trackName
                    ResolverUtils.isMatch(query, item.artistName, title)
                }
                val viewUrl = if (isAlbum) validItem?.collectionViewUrl else validItem?.trackViewUrl
                if (!viewUrl.isNullOrEmpty()) {
                    return viewUrl
                }
            }
            null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    override suspend fun resolveArtist(artistName: String): String? {
        return try {
            val encoded = artistName.encodeURLParameter()
            val resp = client.get("https://itunes.apple.com/search?term=$encoded&entity=musicArtist&limit=25")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<ITunesResponse>(resp.bodyAsText())
                for (item in parsed.results) {
                    val name = item.artistName ?: ""
                    if (isArtistNameMatch(name, artistName)) {
                        val link = item.artistLinkUrl ?: item.artistViewUrl
                        if (!link.isNullOrEmpty()) {
                            return link
                        }
                    }
                }
            }
            null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    suspend fun extractMetadata(url: String): String? {
        return try {
            if (url.contains("i=")) {
                val trackId = url.substringAfter("i=").substringBefore("&").substringBefore("?")
                if (trackId.isNotEmpty()) {
                    val resp = client.get("https://itunes.apple.com/lookup?id=$trackId")
                    if (resp.status.isSuccess()) {
                        val parsed = json.decodeFromString<ITunesResponse>(resp.bodyAsText())
                        val track = parsed.results.firstOrNull()
                        val trackName = track?.trackName ?: ""
                        val artistName = track?.artistName ?: ""
                        if (trackName.isNotEmpty()) {
                            return if (artistName.isNotEmpty()) "$artistName $trackName" else trackName
                        }
                    }
                }
            } else if (url.contains("/song/")) {
                val trackId = url.substringAfter("/song/").substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (trackId.isNotEmpty() && trackId.all { it.isDigit() }) {
                    val resp = client.get("https://itunes.apple.com/lookup?id=$trackId")
                    if (resp.status.isSuccess()) {
                        val parsed = json.decodeFromString<ITunesResponse>(resp.bodyAsText())
                        val track = parsed.results.firstOrNull()
                        val trackName = track?.trackName ?: ""
                        val artistName = track?.artistName ?: ""
                        if (trackName.isNotEmpty()) {
                            return if (artistName.isNotEmpty()) "$artistName $trackName" else trackName
                        }
                    }
                }
            } else if (url.contains("/album/")) {
                val albumId = url.substringAfter("/album/").substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (albumId.isNotEmpty() && albumId.all { it.isDigit() }) {
                    val resp = client.get("https://itunes.apple.com/lookup?id=$albumId&entity=album")
                    if (resp.status.isSuccess()) {
                        val parsed = json.decodeFromString<ITunesResponse>(resp.bodyAsText())
                        val album = parsed.results.firstOrNull()
                        val collectionName = album?.collectionName ?: ""
                        val artistName = album?.artistName ?: ""
                        if (collectionName.isNotEmpty()) {
                            return if (artistName.isNotEmpty()) "$artistName $collectionName" else collectionName
                        }
                    }
                }
            }
            null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    private fun isArtistNameMatch(candidate: String, target: String): Boolean {
        fun normalize(str: String): String {
            return str.lowercase().trim()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replace("[^a-z0-9]".toRegex(), "")
        }
        val normCand = normalize(candidate)
        val normTarget = normalize(target)
        if (normCand.isEmpty() || normTarget.isEmpty()) return false
        if (normCand == normTarget) return true
        if (normCand == normTarget + "thema" || normCand == normTarget + "topic") return true
        return false
    }
}
