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

class DeezerResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.DEEZER

    override suspend fun resolveTrack(query: String): String? {
        return searchDeezer(query, isAlbum = false)
    }

    override suspend fun resolveAlbum(query: String): String? {
        return searchDeezer(query, isAlbum = true)
    }

    private suspend fun searchDeezer(query: String, isAlbum: Boolean = false): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val endpoint = if (isAlbum) "search/album" else "search"
            val resp = client.get("https://api.deezer.com/$endpoint?q=$encoded&limit=1")
            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val rootObj = json.parseToJsonElement(body).jsonObject
                val data = rootObj["data"]?.jsonArray
                if (data != null && data.isNotEmpty()) {
                    val item = data[0].jsonObject
                    val link = item["link"]?.jsonPrimitive?.content
                    if (!link.isNullOrEmpty()) {
                        return link
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
            val resp = client.get("https://api.deezer.com/search/artist?q=$encoded&limit=10")
            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val rootObj = json.parseToJsonElement(body).jsonObject
                val data = rootObj["data"]?.jsonArray
                if (data != null && data.isNotEmpty()) {
                    var bestLink: String? = null
                    var maxFans = -1
                    for (el in data) {
                        val item = el.jsonObject
                        val name = item["name"]?.jsonPrimitive?.content ?: ""
                        if (isArtistNameMatch(name, artistName)) {
                            val fans = item["nb_fan"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            val link = item["link"]?.jsonPrimitive?.content
                            if (fans > maxFans && !link.isNullOrEmpty()) {
                                maxFans = fans
                                bestLink = link
                            }
                        }
                    }
                    if (bestLink != null) return bestLink
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
