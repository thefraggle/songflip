package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
            val resp = client.get("https://itunes.apple.com/search?term=$encoded&entity=$entity&limit=1")
            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val rootObj = json.parseToJsonElement(body).jsonObject
                val results = rootObj["results"]?.jsonArray
                if (results != null && results.isNotEmpty()) {
                    val item = results[0].jsonObject
                    val viewUrl = if (isAlbum) {
                        item["collectionViewUrl"]?.jsonPrimitive?.content
                    } else {
                        item["trackViewUrl"]?.jsonPrimitive?.content
                    }
                    if (!viewUrl.isNullOrEmpty()) {
                        return viewUrl
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun resolveArtist(artistName: String): String? {
        return try {
            val encoded = artistName.encodeURLParameter()
            val resp = client.get("https://itunes.apple.com/search?term=$encoded&entity=musicArtist&limit=25")
            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val rootObj = json.parseToJsonElement(body).jsonObject
                val results = rootObj["results"]?.jsonArray
                if (results != null && results.isNotEmpty()) {
                    var fallbackLink: String? = null
                    for (el in results) {
                        val item = el.jsonObject
                        val name = item["artistName"]?.jsonPrimitive?.content ?: ""
                        if (isArtistNameMatch(name, artistName)) {
                            val link = item["artistLinkUrl"]?.jsonPrimitive?.content
                                ?: item["artistViewUrl"]?.jsonPrimitive?.content
                            val genre = item["primaryGenreName"]?.jsonPrimitive?.content ?: ""
                            if (genre.contains("rock", ignoreCase = true) || genre.contains("punk", ignoreCase = true) || genre.contains("alternative", ignoreCase = true)) {
                                return link
                            }
                            if (fallbackLink == null && !link.isNullOrEmpty()) {
                                fallbackLink = link
                            }
                        }
                    }
                    if (fallbackLink != null) return fallbackLink
                }
            }
            null
        } catch (_: Throwable) {
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
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val results = root["results"]?.jsonArray
                        if (results != null && results.isNotEmpty()) {
                            val track = results[0].jsonObject
                            val trackName = track["trackName"]?.jsonPrimitive?.content ?: ""
                            val artistName = track["artistName"]?.jsonPrimitive?.content ?: ""
                            if (trackName.isNotEmpty()) {
                                return if (artistName.isNotEmpty()) "$artistName $trackName" else trackName
                            }
                        }
                    }
                }
            } else if (url.contains("/song/")) {
                val trackId = url.substringAfter("/song/").substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (trackId.isNotEmpty() && trackId.all { it.isDigit() }) {
                    val resp = client.get("https://itunes.apple.com/lookup?id=$trackId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val results = root["results"]?.jsonArray
                        if (results != null && results.isNotEmpty()) {
                            val track = results[0].jsonObject
                            val trackName = track["trackName"]?.jsonPrimitive?.content ?: ""
                            val artistName = track["artistName"]?.jsonPrimitive?.content ?: ""
                            if (trackName.isNotEmpty()) {
                                return if (artistName.isNotEmpty()) "$artistName $trackName" else trackName
                            }
                        }
                    }
                }
            } else if (url.contains("/album/")) {
                val albumId = url.substringAfter("/album/").substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (albumId.isNotEmpty() && albumId.all { it.isDigit() }) {
                    val resp = client.get("https://itunes.apple.com/lookup?id=$albumId&entity=album")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val results = root["results"]?.jsonArray
                        if (results != null && results.isNotEmpty()) {
                            val album = results[0].jsonObject
                            val collectionName = album["collectionName"]?.jsonPrimitive?.content ?: ""
                            val artistName = album["artistName"]?.jsonPrimitive?.content ?: ""
                            if (collectionName.isNotEmpty()) {
                                return if (artistName.isNotEmpty()) "$artistName $collectionName" else collectionName
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Throwable) {
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
