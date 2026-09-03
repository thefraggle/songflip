package de.goork.songflip.core.engine

import de.goork.songflip.core.cache.LinkCache
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.UrlUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SongLinkEngine(
    private val client: HttpClient = createPlatformHttpClient(),
    private val cache: LinkCache = LinkCache()
) {
    constructor() : this(createPlatformHttpClient(), LinkCache())

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val ytVideoIdJsonRegex = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
    private val ytWatchRegex = Regex("/watch\\?v=([a-zA-Z0-9_-]{11})")
    private val ytAlbumPlaylistRegex = Regex("\"playlistId\":\"(OLAK5uy_[a-zA-Z0-9_-]+)\"")
    private val ytGenericPlaylistRegex = Regex("\"playlistId\":\"([a-zA-Z0-9_-]{18,})\"")
    private val ytChannelIdRegex = Regex("\"channelId\":\"(UC[a-zA-Z0-9_-]{22})\"")

    suspend fun resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String = "youtubeMusic",
        customApiUrl: String = "",
        customApiToken: String = ""
    ): ResolutionResult {
        try {
            // 1. Extract clean URL
            val cleanUrl = UrlUtils.extractCleanUrl(inputUrl)
                ?: return ResolutionResult.Error("No valid URL found in input")

            // 2. Custom API / Webhook (if configured)
            if (customApiUrl.isNotBlank()) {
                val customResult = queryCustomApi(customApiUrl, customApiToken, cleanUrl, targetPlatformKey)
                if (customResult != null) {
                    val formatted = UrlUtils.formatTargetUrl(customResult, targetPlatformKey)
                    val nativeUri = UrlUtils.toNativeAppUri(formatted, targetPlatformKey)
                    return ResolutionResult.Success(
                        targetUrl = formatted,
                        platform = "custom_api",
                        nativeAppUri = nativeUri
                    )
                }
            }

            // 3. Resolve short links
            val resolvedUrl = if (UrlUtils.isShortLinkDomain(cleanUrl)) {
                resolveCanonicalUrl(cleanUrl)
            } else {
                cleanUrl
            }
            val canonicalUrl = UrlUtils.normalizeUrl(resolvedUrl)

            // Playlist links cannot be converted 1:1 in background
            if (UrlUtils.isPlaylistUrl(canonicalUrl)) {
                return ResolutionResult.Error("PLAYLIST_NOT_SUPPORTED", isUnsupported = true)
            }

            val isExplicitAlbumUrl = UrlUtils.isAlbumUrl(canonicalUrl)
            val now = getCurrentTimeMillis()

            // 4. L1 Cache Lookup (< 5ms)
            val cached = cache.get(canonicalUrl, targetPlatformKey, now)
            if (cached != null) {
                return cached
            }

            // 4.5. Search URL Resolution (Spotify, Apple Music, YouTube, Deezer, Tidal search links)
            val searchQuery = UrlUtils.extractSearchQuery(canonicalUrl)
            if (searchQuery != null) {
                val directUrl = resolveDirectPlatformUrl(searchQuery, targetPlatformKey, isAlbum = false)
                val finalTargetUrl = directUrl ?: UrlUtils.buildSearchUrl(searchQuery, targetPlatformKey)
                val nativeUri = UrlUtils.toNativeAppUri(finalTargetUrl, targetPlatformKey)
                val result = ResolutionResult.Success(
                    targetUrl = finalTargetUrl,
                    platform = if (directUrl != null) targetPlatformKey else "${targetPlatformKey}_search",
                    title = searchQuery,
                    artist = null,
                    isAlbum = false,
                    nativeAppUri = nativeUri
                )
                cache.put(canonicalUrl, targetPlatformKey, result, now)
                return result
            }

            // 5. Parallel Multi-Source Resolution
            val (songLinkData, trackInfo) = supervisorScope {
                val songLinkDeferred = async { fetchSongLinkData(canonicalUrl) }
                val fallbackTrackDeferred = async { extractTrackInfo(canonicalUrl) }
                val sld = try { songLinkDeferred.await() } catch (e: Exception) { null }
                val ti = try { fallbackTrackDeferred.await() } catch (e: Exception) { null }
                Pair(sld, ti)
            }

            if (songLinkData != null) {
                val directUrl = songLinkData.links[targetPlatformKey]
                    ?: if (targetPlatformKey == "youtubeMusic") songLinkData.links["youtube"] else null

                if (!directUrl.isNullOrEmpty()) {
                    val formatted = UrlUtils.formatTargetUrl(directUrl, targetPlatformKey)
                    val nativeUri = UrlUtils.toNativeAppUri(formatted, targetPlatformKey)
                    val result = ResolutionResult.Success(
                        targetUrl = formatted,
                        platform = targetPlatformKey,
                        title = songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = songLinkData.isAlbum || isExplicitAlbumUrl,
                        nativeAppUri = nativeUri
                    )
                    cache.put(canonicalUrl, targetPlatformKey, result, now)
                    return result
                }

                if (songLinkData.title.isNotEmpty()) {
                    val query = if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                        "${songLinkData.artist} ${songLinkData.title}"
                    } else {
                        songLinkData.title
                    }

                    val isAlbum = songLinkData.isAlbum || isExplicitAlbumUrl
                    val resolvedDirectUrl = resolveDirectPlatformUrl(
                        query = query,
                        targetPlatformKey = targetPlatformKey,
                        isAlbum = isAlbum
                    )
                    val finalTargetUrl = resolvedDirectUrl ?: UrlUtils.buildSearchUrl(query, targetPlatformKey)
                    val nativeUri = UrlUtils.toNativeAppUri(finalTargetUrl, targetPlatformKey)
                    val result = ResolutionResult.Success(
                        targetUrl = finalTargetUrl,
                        platform = if (resolvedDirectUrl != null) targetPlatformKey else "${targetPlatformKey}_search",
                        title = songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = isAlbum,
                        nativeAppUri = nativeUri
                    )
                    cache.put(canonicalUrl, targetPlatformKey, result, now)
                    return result
                }
            }

            // 6. Fallback Metadata Extraction via OEmbed / Public APIs
            if (trackInfo != null && trackInfo.isNotBlank()) {
                val resolvedDirectUrl = resolveDirectPlatformUrl(
                    query = trackInfo,
                    targetPlatformKey = targetPlatformKey,
                    isAlbum = isExplicitAlbumUrl
                )
                val targetUrl = resolvedDirectUrl ?: UrlUtils.buildSearchUrl(trackInfo, targetPlatformKey)
                val platform = if (resolvedDirectUrl != null) targetPlatformKey else "${targetPlatformKey}_search"
                val nativeUri = UrlUtils.toNativeAppUri(targetUrl, targetPlatformKey)
                val result = ResolutionResult.Success(
                    targetUrl = targetUrl,
                    platform = platform,
                    title = trackInfo,
                    artist = null,
                    isAlbum = isExplicitAlbumUrl,
                    nativeAppUri = nativeUri
                )
                cache.put(canonicalUrl, targetPlatformKey, result, now)
                return result
            }

            // 7. Fallback: Artist Page Detection
            val artistInfo = extractArtistInfo(canonicalUrl)
            if (artistInfo != null && artistInfo.isNotBlank()) {
                val directArtistUrl = resolveDirectArtistUrl(artistInfo, targetPlatformKey)
                val finalTargetUrl = directArtistUrl ?: UrlUtils.buildSearchUrl(artistInfo, targetPlatformKey)
                val nativeUri = UrlUtils.toNativeAppUri(finalTargetUrl, targetPlatformKey)
                val result = ResolutionResult.Success(
                    targetUrl = finalTargetUrl,
                    platform = if (directArtistUrl != null) targetPlatformKey else "${targetPlatformKey}_artist",
                    title = null,
                    artist = artistInfo,
                    isAlbum = false,
                    nativeAppUri = nativeUri
                )
                cache.put(canonicalUrl, targetPlatformKey, result, now)
                return result
            }

            // 8. Fallback: Playlist Search Routing
            val playlistInfo = extractPlaylistInfo(canonicalUrl)
            if (playlistInfo != null && playlistInfo.isNotBlank()) {
                val searchUrl = UrlUtils.buildSearchUrl(playlistInfo, targetPlatformKey)
                val nativeUri = UrlUtils.toNativeAppUri(searchUrl, targetPlatformKey)
                val result = ResolutionResult.Success(
                    targetUrl = searchUrl,
                    platform = "${targetPlatformKey}_playlist",
                    title = playlistInfo,
                    artist = null,
                    isAlbum = false,
                    nativeAppUri = nativeUri
                )
                return result
            }

            return ResolutionResult.Error("Could not resolve music link")
        } catch (e: Exception) {
            val cleanUrl = UrlUtils.extractCleanUrl(inputUrl) ?: inputUrl
            val isExplicitAlbumUrl = UrlUtils.isAlbumUrl(cleanUrl)

            val trackInfo = extractTrackInfo(cleanUrl)
            if (trackInfo != null) {
                val resolved = resolveDirectPlatformUrl(trackInfo, targetPlatformKey, isExplicitAlbumUrl)
                    ?: UrlUtils.buildSearchUrl(trackInfo, targetPlatformKey)
                val nativeUri = UrlUtils.toNativeAppUri(resolved, targetPlatformKey)
                return ResolutionResult.Success(
                    targetUrl = resolved,
                    platform = "${targetPlatformKey}_fallback",
                    title = trackInfo,
                    artist = null,
                    isAlbum = isExplicitAlbumUrl,
                    nativeAppUri = nativeUri
                )
            }

            return ResolutionResult.Error(e.message ?: "Unknown network error")
        }
    }

    private data class SongLinkData(
        val title: String,
        val artist: String,
        val type: String,
        val links: Map<String, String>
    ) {
        val isAlbum: Boolean
            get() = type.equals("album", ignoreCase = true) || type.equals("ep", ignoreCase = true)
    }

    private suspend fun fetchSongLinkData(url: String): SongLinkData? {
        return try {
            val targetSongLink = UrlUtils.normalizeToSongLinkDirectUrl(url)
            val resp = client.get(targetSongLink) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            if (!resp.status.isSuccess()) return null

            val html = resp.bodyAsText()
            val scriptTag = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            if (!html.contains(scriptTag)) return null

            val jsonString = html.substringAfter(scriptTag).substringBefore("</script>")
            val rootObj = json.parseToJsonElement(jsonString).jsonObject
            val pageProps = rootObj["props"]?.jsonObject?.get("pageProps")?.jsonObject ?: return null
            val pageData = pageProps["pageData"]?.jsonObject ?: return null

            val pageId = pageData["pageId"]?.jsonPrimitive?.content ?: ""
            val entityUniqueId = pageData["entityUniqueId"]?.jsonPrimitive?.content ?: ""
            val isAlbumEntity = pageId.contains("|album|") || entityUniqueId.contains("|album|")

            val entityData = pageData["entityData"]?.jsonObject
            var title = entityData?.get("title")?.jsonPrimitive?.content ?: ""
            var artist = entityData?.get("artistName")?.jsonPrimitive?.content ?: ""
            var entityType = entityData?.get("type")?.jsonPrimitive?.content ?: (if (isAlbumEntity) "album" else "")

            val sections = pageData["sections"]?.jsonArray
            if (sections != null && sections.isNotEmpty()) {
                val firstSection = sections[0].jsonObject
                if (title.isEmpty()) {
                    title = firstSection["title"]?.jsonPrimitive?.content ?: ""
                }
                if (artist.isEmpty()) {
                    artist = firstSection["artistName"]?.jsonPrimitive?.content ?: ""
                }
            }

            val linksMap = mutableMapOf<String, String>()
            if (sections != null) {
                for (sectionElem in sections) {
                    val section = sectionElem.jsonObject
                    val links = section["links"]?.jsonArray ?: continue
                    for (linkElem in links) {
                        val linkObj = linkElem.jsonObject
                        val platform = linkObj["platform"]?.jsonPrimitive?.content ?: ""
                        val linkUrl = linkObj["url"]?.jsonPrimitive?.content ?: ""
                        if (platform.isNotEmpty() && linkUrl.isNotEmpty()) {
                            linksMap[platform] = linkUrl
                        }
                    }
                }
            }

            SongLinkData(title = title, artist = artist, type = entityType, links = linksMap)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveDirectPlatformUrl(query: String, targetPlatformKey: String, isAlbum: Boolean = false): String? {
        return when (targetPlatformKey) {
            "youtubeMusic" -> {
                if (isAlbum) {
                    resolveYouTubeMusicAlbumUrl(query)
                        ?: resolveYouTubeMusicDirectPlayUrl(query)
                        ?: UrlUtils.buildSearchUrl(query, "youtubeMusic")
                } else {
                    resolveYouTubeMusicDirectPlayUrl(query)
                        ?: UrlUtils.buildSearchUrl(query, "youtubeMusic")
                }
            }
            "appleMusic" -> resolveAppleMusicDirectUrl(query, isAlbum) ?: UrlUtils.buildSearchUrl(query, "appleMusic")
            "deezer" -> resolveDeezerDirectUrl(query, isAlbum) ?: UrlUtils.buildSearchUrl(query, "deezer")
            "spotify" -> UrlUtils.buildSearchUrl(query, "spotify")
            "tidal" -> UrlUtils.buildSearchUrl(query, "tidal")
            "amazonMusic" -> UrlUtils.buildSearchUrl(query, "amazonMusic")
            else -> UrlUtils.buildSearchUrl(query, targetPlatformKey)
        }
    }

    private suspend fun resolveYouTubeMusicAlbumUrl(query: String): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val resp = client.get("https://www.youtube.com/results?search_query=$encoded&sp=EgIQAw%253D%253D") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            if (!resp.status.isSuccess()) return null
            val html = resp.bodyAsText()

            val olakMatch = ytAlbumPlaylistRegex.find(html)
            if (olakMatch != null) {
                val playlistId = olakMatch.groupValues[1]
                if (playlistId.isNotEmpty()) {
                    return "https://music.youtube.com/playlist?list=$playlistId"
                }
            }

            val plMatch = ytGenericPlaylistRegex.find(html)
            if (plMatch != null) {
                val playlistId = plMatch.groupValues[1]
                if (playlistId.isNotEmpty()) {
                    return "https://music.youtube.com/playlist?list=$playlistId"
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveYouTubeMusicDirectPlayUrl(query: String): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val resp = client.get("https://www.youtube.com/results?search_query=$encoded") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            if (!resp.status.isSuccess()) return null
            val html = resp.bodyAsText()

            val jsonMatch = ytVideoIdJsonRegex.find(html)
            if (jsonMatch != null) {
                val videoId = jsonMatch.groupValues[1]
                if (videoId.isNotEmpty()) {
                    return "https://music.youtube.com/watch?v=$videoId"
                }
            }

            val watchMatch = ytWatchRegex.find(html)
            if (watchMatch != null) {
                val videoId = watchMatch.groupValues[1]
                if (videoId.isNotEmpty()) {
                    return "https://music.youtube.com/watch?v=$videoId"
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveAppleMusicDirectUrl(query: String, isAlbum: Boolean = false): String? {
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
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveDeezerDirectUrl(query: String, isAlbum: Boolean = false): String? {
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
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveDirectArtistUrl(artistName: String, targetPlatformKey: String): String? {
        return when (targetPlatformKey) {
            "deezer" -> resolveDeezerArtistUrl(artistName)
            "appleMusic" -> resolveAppleMusicArtistUrl(artistName)
            "youtubeMusic" -> resolveYouTubeMusicArtistUrl(artistName)
            else -> null
        }
    }

    private suspend fun resolveAppleMusicArtistUrl(artistName: String): String? {
        return try {
            val encoded = artistName.encodeURLParameter()
            val resp = client.get("https://itunes.apple.com/search?term=$encoded&entity=musicArtist&limit=1")
            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val rootObj = json.parseToJsonElement(body).jsonObject
                val results = rootObj["results"]?.jsonArray
                if (results != null && results.isNotEmpty()) {
                    val item = results[0].jsonObject
                    val link = item["artistLinkUrl"]?.jsonPrimitive?.content
                        ?: item["artistViewUrl"]?.jsonPrimitive?.content
                    if (!link.isNullOrEmpty()) {
                        return link
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveDeezerArtistUrl(artistName: String): String? {
        return try {
            val encoded = artistName.encodeURLParameter()
            val resp = client.get("https://api.deezer.com/search/artist?q=$encoded&limit=1")
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
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveYouTubeMusicArtistUrl(artistName: String): String? {
        return try {
            val encoded = "$artistName artist".encodeURLParameter()
            val resp = client.get("https://www.youtube.com/results?search_query=$encoded&sp=EgIQAg%253D%253D") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept-Language", "en-US,en;q=0.9")
            }
            if (!resp.status.isSuccess()) return null
            val html = resp.bodyAsText()
            val match = ytChannelIdRegex.find(html)
            if (match != null) {
                val channelId = match.groupValues[1]
                if (channelId.isNotEmpty()) {
                    return "https://music.youtube.com/channel/$channelId"
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun extractTrackInfo(url: String): String? {
        return try {
            if (url.contains("spotify.com")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://open.spotify.com/oembed?url=$encoded")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content
                    if (!title.isNullOrEmpty()) return title
                }
            } else if (url.contains("apple.com") && url.contains("i=")) {
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
            } else if (url.contains("apple.com") && url.contains("/song/")) {
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
            } else if (url.contains("apple.com") && url.contains("/album/")) {
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
            } else if (url.contains("youtube.com") || url.contains("youtu.be")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://www.youtube.com/oembed?url=$encoded&format=json")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content ?: ""
                    val author = root["author_name"]?.jsonPrimitive?.content ?: ""
                    if (title.isNotEmpty()) {
                        return if (author.isNotEmpty() && !title.contains(author, ignoreCase = true)) "$author $title" else title
                    }
                }
            } else if (url.contains("deezer.com")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://api.deezer.com/oembed?url=$encoded")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content
                    if (!title.isNullOrEmpty()) return title
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun extractArtistInfo(url: String): String? {
        return try {
            if (url.contains("deezer.com") && url.contains("/artist/")) {
                val artistId = url.substringAfter("/artist/").substringBefore("?").substringBefore("/").trim()
                if (artistId.isNotEmpty()) {
                    val resp = client.get("https://api.deezer.com/artist/$artistId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val name = root["name"]?.jsonPrimitive?.content
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            } else if (url.contains("spotify.com") && url.contains("/artist/")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://open.spotify.com/oembed?url=$encoded")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content
                    if (!title.isNullOrEmpty()) return title
                }
            } else if (url.contains("apple.com") && url.contains("/artist/")) {
                val artistId = url.substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (artistId.isNotEmpty() && artistId.all { it.isDigit() }) {
                    val resp = client.get("https://itunes.apple.com/lookup?id=$artistId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val results = root["results"]?.jsonArray
                        if (results != null && results.isNotEmpty()) {
                            val artistObj = results[0].jsonObject
                            val artistName = artistObj["artistName"]?.jsonPrimitive?.content
                            if (!artistName.isNullOrEmpty()) return artistName
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun extractPlaylistInfo(url: String): String? {
        return try {
            if (url.contains("deezer.com") && url.contains("/playlist/")) {
                val playlistId = url.substringAfter("/playlist/").substringBefore("?").substringBefore("/").trim()
                if (playlistId.isNotEmpty()) {
                    val resp = client.get("https://api.deezer.com/playlist/$playlistId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val title = root["title"]?.jsonPrimitive?.content
                        if (!title.isNullOrEmpty()) return title
                    }
                }
            } else if (url.contains("spotify.com") && url.contains("/playlist/")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://open.spotify.com/oembed?url=$encoded")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content
                    if (!title.isNullOrEmpty()) return title
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveCanonicalUrl(url: String): String {
        return try {
            val resp = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            resp.request.url.toString()
        } catch (e: Exception) {
            url
        }
    }

    private suspend fun queryCustomApi(apiUrl: String, token: String, url: String, targetPlatform: String): String? {
        return try {
            val payload = "{\"url\":\"$url\",\"targetPlatform\":\"$targetPlatform\"}"
            val resp = client.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
                if (token.isNotBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            if (resp.status.isSuccess()) {
                val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                val targetUrl = root["targetUrl"]?.jsonPrimitive?.content
                    ?: root["url"]?.jsonPrimitive?.content
                if (!targetUrl.isNullOrEmpty()) {
                    return targetUrl
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
