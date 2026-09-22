package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TidalResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val tidalAppToken: String = "CzET4vdadNUFQ5JU"
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.TIDAL

    override suspend fun resolveTrack(query: String): String? {
        return searchTidal(query, isAlbum = false)
    }

    override suspend fun resolveAlbum(query: String): String? {
        return searchTidal(query, isAlbum = true)
    }

    private suspend fun searchTidal(query: String, isAlbum: Boolean = false): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val resp = client.get("https://listen.tidal.com/v1/search?query=$encoded&limit=5&countryCode=DE") {
                header("x-tidal-token", tidalAppToken)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val rootObj = json.parseToJsonElement(body).jsonObject
                val key = if (isAlbum) "albums" else "tracks"
                val items = rootObj[key]?.jsonObject?.get("items")?.jsonArray
                if (items != null && items.isNotEmpty()) {
                    val firstItem = items[0].jsonObject
                    val id = firstItem["id"]?.jsonPrimitive?.content
                    if (!id.isNullOrEmpty()) {
                        val path = if (isAlbum) "album" else "track"
                        return "https://tidal.com/browse/$path/$id"
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
            val resp = client.get("https://listen.tidal.com/v1/search?query=$encoded&limit=10&countryCode=DE") {
                header("x-tidal-token", tidalAppToken)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val rootObj = json.parseToJsonElement(body).jsonObject
                val items = rootObj["artists"]?.jsonObject?.get("items")?.jsonArray
                if (items != null && items.isNotEmpty()) {
                    var bestId: String? = null
                    var maxPop = -1
                    for (el in items) {
                        val item = el.jsonObject
                        val name = item["name"]?.jsonPrimitive?.content ?: ""
                        if (isArtistNameMatch(name, artistName)) {
                            val pop = item["popularity"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            if (pop > maxPop) {
                                maxPop = pop
                                bestId = item["id"]?.jsonPrimitive?.content
                            }
                        }
                    }
                    if (!bestId.isNullOrEmpty()) {
                        return "https://tidal.com/artist/$bestId"
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
