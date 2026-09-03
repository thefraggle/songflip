package de.goork.songflip.data

import de.goork.songflip.core.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

sealed class OdesliResult {
    data class Success(
        val targetUrl: String,
        val platform: String,
        val title: String? = null,
        val artist: String? = null,
        val isAlbum: Boolean = false
    ) : OdesliResult()
    data class Error(val message: String) : OdesliResult()
}

class OdesliRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val urlPattern = Pattern.compile("(https?://[^\\s<>'\"]+)")
    private val ytVideoRendererPattern = Pattern.compile("\"videoRenderer\":\\{\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
    private val ytVideoIdJsonPattern = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
    private val ytWatchPattern = Pattern.compile("/watch\\?v=([a-zA-Z0-9_-]{11})")
    private val ytAlbumPlaylistPattern = Pattern.compile("\"playlistId\":\"(OLAK5uy_[a-zA-Z0-9_-]+)\"")
    private val ytGenericPlaylistPattern = Pattern.compile("\"playlistId\":\"([a-zA-Z0-9_-]{18,})\"")
    private val ytChannelIdPattern = Pattern.compile("\"channelId\":\"(UC[a-zA-Z0-9_-]{22})\"")

    suspend fun resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String = "youtubeMusic",
        customApiUrl: String = "",
        customApiToken: String = ""
    ): OdesliResult = withContext(Dispatchers.IO) {
        try {
            // 1. Extract clean URL from raw input (removes share text)
            val cleanUrl = extractCleanUrl(inputUrl)
                ?: return@withContext OdesliResult.Error("No valid URL found in input")

            // 2. Custom Webhook / AI API (if configured)
            if (customApiUrl.isNotBlank()) {
                val customResult = queryCustomApi(customApiUrl, customApiToken, cleanUrl, targetPlatformKey)
                if (customResult != null) {
                    val formatted = formatTargetUrl(customResult, targetPlatformKey)
                    return@withContext OdesliResult.Success(formatted, "custom_api")
                }
            }

            // 3. Resolve short links (spotify.link, link.deezer.com, deezer.page.link, amzn.to, youtu.be, etc.)
            val resolvedUrl = if (isShortLinkDomain(cleanUrl)) {
                resolveCanonicalUrl(cleanUrl)
            } else {
                cleanUrl
            }
            val canonicalUrl = UrlUtils.normalizeUrl(resolvedUrl)

            // Playlist links cannot be converted 1:1 in background; reject cleanly to avoid garbage search results
            if (isPlaylistUrl(canonicalUrl)) {
                return@withContext OdesliResult.Error("PLAYLIST_NOT_SUPPORTED")
            }

            val isExplicitTrackUrl = canonicalUrl.contains("i=") || canonicalUrl.contains("/song/") || canonicalUrl.contains("/track/")
            val isExplicitAlbumUrl = !isExplicitTrackUrl && isAlbumUrl(canonicalUrl)

            // 4. L1 Cache Lookup (In-Memory LRU & Persisted - < 5ms)
            val cached = LinkCacheManager.get(canonicalUrl, targetPlatformKey)
            if (cached != null) {
                return@withContext OdesliResult.Success(
                    targetUrl = cached.targetUrl,
                    platform = cached.platform,
                    title = cached.title,
                    artist = cached.artist,
                    isAlbum = cached.isAlbum
                )
            }

            // 4.2. Search URL Resolution (Spotify, Apple Music, YouTube, Deezer, Tidal, Amazon)
            val searchQuery = extractSearchQuery(canonicalUrl)
            if (searchQuery != null) {
                val directUrl = resolveDirectPlatformUrl(searchQuery, targetPlatformKey, isAlbum = false)
                val finalTargetUrl = directUrl ?: buildSearchUrl(searchQuery, targetPlatformKey)
                val result = OdesliResult.Success(
                    targetUrl = finalTargetUrl,
                    platform = if (directUrl != null) targetPlatformKey else "${targetPlatformKey}_search",
                    title = searchQuery,
                    artist = null,
                    isAlbum = false
                )
                LinkCacheManager.put(
                    canonicalUrl = canonicalUrl,
                    targetPlatformKey = targetPlatformKey,
                    targetUrl = result.targetUrl,
                    platform = result.platform,
                    title = result.title,
                    artist = null,
                    isAlbum = false
                )
                return@withContext result
            }

            // 4.5. L2 Server Cache (PRO Feature - < 30ms)
            if (ProManager.isPro) {
                val l2Result = queryL2ServerCache(canonicalUrl, targetPlatformKey)
                if (l2Result != null) {
                    LinkCacheManager.put(
                        canonicalUrl = canonicalUrl,
                        targetPlatformKey = targetPlatformKey,
                        targetUrl = l2Result.targetUrl,
                        platform = l2Result.platform,
                        title = l2Result.title,
                        artist = l2Result.artist,
                        isAlbum = l2Result.isAlbum
                    )
                    return@withContext l2Result
                }
            }

            // 5. Parallel Multi-Source Resolution (Async Songlink + OEmbed Fallback with supervisorScope)
            val (songLinkData, trackInfo) = supervisorScope {
                val songLinkDeferred = async { fetchSongLinkData(canonicalUrl) }
                val fallbackTrackDeferred = async { extractTrackInfo(canonicalUrl) }
                val sld = try { songLinkDeferred.await() } catch (e: Exception) { null }
                val ti = try { fallbackTrackDeferred.await() } catch (e: Exception) { null }
                Pair(sld, ti)
            }
            if (songLinkData != null) {
                // If the user requested an explicit track (e.g. Apple Music with ?i=1525933492), but song.link mapped it to the whole album:
                if (isExplicitTrackUrl && songLinkData.isAlbum) {
                    val resolvedQuery = trackInfo ?: if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                        "${songLinkData.artist} ${songLinkData.title}"
                    } else {
                        songLinkData.title
                    }
                    val directTrackUrl = resolveDirectPlatformUrl(resolvedQuery, targetPlatformKey, isAlbum = false)
                    val finalTargetUrl = directTrackUrl ?: buildSearchUrl(resolvedQuery, targetPlatformKey)
                    val result = OdesliResult.Success(
                        targetUrl = finalTargetUrl,
                        platform = if (directTrackUrl != null) targetPlatformKey else "${targetPlatformKey}_search",
                        title = trackInfo ?: songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = false
                    )
                    LinkCacheManager.put(
                        canonicalUrl = canonicalUrl,
                        targetPlatformKey = targetPlatformKey,
                        targetUrl = result.targetUrl,
                        platform = result.platform,
                        title = result.title,
                        artist = result.artist,
                        isAlbum = false
                    )
                    return@withContext result
                }

                // If direct link for the target platform exists in song.link
                val directUrl = songLinkData.links[targetPlatformKey]
                    ?: if (targetPlatformKey == "youtubeMusic") songLinkData.links["youtube"] else null

                val isAlbum = if (isExplicitTrackUrl) false else (songLinkData.isAlbum || isExplicitAlbumUrl)

                if (!directUrl.isNullOrEmpty()) {
                    val isAlbumPlaylist = directUrl.contains("playlist?list=") || directUrl.contains("/playlist/") || directUrl.contains("/album/") || directUrl.contains("/albums/")
                    if (isExplicitTrackUrl && isAlbumPlaylist) {
                        // Track was requested, but song.link returned an album playlist link -> resolve exact track direct play
                        val trackQuery = if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                            "${songLinkData.artist} ${songLinkData.title}"
                        } else {
                            songLinkData.title
                        }
                        val resolvedDirectUrl = resolveDirectPlatformUrl(trackQuery, targetPlatformKey, isAlbum = false)
                        if (resolvedDirectUrl != null) {
                            val result = OdesliResult.Success(
                                targetUrl = resolvedDirectUrl,
                                platform = targetPlatformKey,
                                title = songLinkData.title.ifEmpty { null },
                                artist = songLinkData.artist.ifEmpty { null },
                                isAlbum = false
                            )
                            LinkCacheManager.put(
                                canonicalUrl = canonicalUrl,
                                targetPlatformKey = targetPlatformKey,
                                targetUrl = result.targetUrl,
                                platform = result.platform,
                                title = result.title,
                                artist = result.artist,
                                isAlbum = false
                            )
                            return@withContext result
                        }
                    }

                    val formatted = formatTargetUrl(directUrl, targetPlatformKey)
                    val result = OdesliResult.Success(
                        targetUrl = formatted,
                        platform = targetPlatformKey,
                        title = songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = isAlbum
                    )
                    LinkCacheManager.put(
                        canonicalUrl = canonicalUrl,
                        targetPlatformKey = targetPlatformKey,
                        targetUrl = result.targetUrl,
                        platform = result.platform,
                        title = result.title,
                        artist = result.artist,
                        isAlbum = result.isAlbum
                    )
                    return@withContext result
                }

                // If target platform link is not directly available, use the extracted track/album title + artist
                if (songLinkData.title.isNotEmpty()) {
                    val rawQuery = if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                        "${songLinkData.artist} ${songLinkData.title}"
                    } else {
                        songLinkData.title
                    }
                    val cleanQuery = UrlUtils.cleanSearchQuery(rawQuery)

                    val resolvedDirectUrl = resolveDirectPlatformUrl(
                        query = cleanQuery,
                        targetPlatformKey = targetPlatformKey,
                        isAlbum = isAlbum
                    ) ?: if (cleanQuery != rawQuery) {
                        resolveDirectPlatformUrl(
                            query = rawQuery,
                            targetPlatformKey = targetPlatformKey,
                            isAlbum = isAlbum
                        )
                    } else null

                    val finalTargetUrl = resolvedDirectUrl ?: buildSearchUrl(cleanQuery, targetPlatformKey)
                    val result = OdesliResult.Success(
                        targetUrl = finalTargetUrl,
                        platform = if (resolvedDirectUrl != null) targetPlatformKey else "${targetPlatformKey}_search",
                        title = songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = isAlbum
                    )
                    LinkCacheManager.put(
                        canonicalUrl = canonicalUrl,
                        targetPlatformKey = targetPlatformKey,
                        targetUrl = result.targetUrl,
                        platform = result.platform,
                        title = result.title,
                        artist = result.artist,
                        isAlbum = result.isAlbum
                    )
                    return@withContext result
                }
            }

            // 6. Fallback Metadata Extraction via Service OEmbed / Public APIs
            if (trackInfo != null && trackInfo.isNotBlank()) {
                val cleanTrack = UrlUtils.cleanSearchQuery(trackInfo)
                val resolvedDirectUrl = resolveDirectPlatformUrl(
                    query = cleanTrack,
                    targetPlatformKey = targetPlatformKey,
                    isAlbum = isExplicitAlbumUrl
                ) ?: if (cleanTrack != trackInfo) {
                    resolveDirectPlatformUrl(
                        query = trackInfo,
                        targetPlatformKey = targetPlatformKey,
                        isAlbum = isExplicitAlbumUrl
                    )
                } else null

                val targetUrl = resolvedDirectUrl ?: buildSearchUrl(cleanTrack, targetPlatformKey)
                val platform = if (resolvedDirectUrl != null) targetPlatformKey else "${targetPlatformKey}_search"
                val result = OdesliResult.Success(
                    targetUrl = targetUrl,
                    platform = platform,
                    title = trackInfo,
                    artist = null,
                    isAlbum = isExplicitAlbumUrl
                )
                LinkCacheManager.put(
                    canonicalUrl = canonicalUrl,
                    targetPlatformKey = targetPlatformKey,
                    targetUrl = result.targetUrl,
                    platform = result.platform,
                    title = result.title,
                    artist = result.artist,
                    isAlbum = result.isAlbum
                )
                return@withContext result
            }

            // 7. Fallback: Artist Page Detection & Direct Catalog Routing
            val artistInfo = extractArtistInfo(canonicalUrl)
            if (artistInfo != null && artistInfo.isNotBlank()) {
                val directArtistUrl = resolveDirectArtistUrl(artistInfo, targetPlatformKey)
                val finalTargetUrl = directArtistUrl ?: buildSearchUrl(artistInfo, targetPlatformKey)
                val result = OdesliResult.Success(
                    targetUrl = finalTargetUrl,
                    platform = if (directArtistUrl != null) targetPlatformKey else "${targetPlatformKey}_artist",
                    title = null,
                    artist = artistInfo,
                    isAlbum = false
                )
                LinkCacheManager.put(
                    canonicalUrl = canonicalUrl,
                    targetPlatformKey = targetPlatformKey,
                    targetUrl = result.targetUrl,
                    platform = result.platform,
                    title = result.title,
                    artist = result.artist,
                    isAlbum = result.isAlbum
                )
                return@withContext result
            }

            // 8. Fallback: Playlist Detection & Search Routing
            val playlistInfo = extractPlaylistInfo(canonicalUrl)
            if (playlistInfo != null && playlistInfo.isNotBlank()) {
                val searchUrl = buildSearchUrl(playlistInfo, targetPlatformKey)
                val result = OdesliResult.Success(
                    targetUrl = searchUrl,
                    platform = "${targetPlatformKey}_playlist",
                    title = playlistInfo,
                    artist = null,
                    isAlbum = false
                )
                return@withContext result
            }

            OdesliResult.Error("Could not resolve music link")
        } catch (e: Exception) {
            val cleanUrl = extractCleanUrl(inputUrl) ?: inputUrl
            val isExplicitAlbumUrl = isAlbumUrl(cleanUrl)

            val searchQuery = extractSearchQuery(cleanUrl)
            if (searchQuery != null) {
                val resolved = resolveDirectPlatformUrl(searchQuery, targetPlatformKey, isAlbum = false)
                    ?: buildSearchUrl(searchQuery, targetPlatformKey)
                return@withContext OdesliResult.Success(
                    targetUrl = resolved,
                    platform = "${targetPlatformKey}_search_fallback",
                    title = searchQuery,
                    artist = null,
                    isAlbum = false
                )
            }

            val trackInfo = extractTrackInfo(cleanUrl)
            if (trackInfo != null) {
                val resolved = resolveDirectPlatformUrl(trackInfo, targetPlatformKey, isExplicitAlbumUrl)
                    ?: buildSearchUrl(trackInfo, targetPlatformKey)
                return@withContext OdesliResult.Success(
                    targetUrl = resolved,
                    platform = "${targetPlatformKey}_fallback",
                    title = trackInfo,
                    artist = null,
                    isAlbum = isExplicitAlbumUrl
                )
            }

            val artistInfo = extractArtistInfo(cleanUrl)
            if (artistInfo != null) {
                return@withContext OdesliResult.Success(
                    targetUrl = buildSearchUrl(artistInfo, targetPlatformKey),
                    platform = "${targetPlatformKey}_artist_fallback",
                    title = null,
                    artist = artistInfo,
                    isAlbum = false
                )
            }

            val playlistInfo = extractPlaylistInfo(cleanUrl)
            if (playlistInfo != null) {
                return@withContext OdesliResult.Success(
                    targetUrl = buildSearchUrl(playlistInfo, targetPlatformKey),
                    platform = "${targetPlatformKey}_playlist_fallback",
                    title = playlistInfo,
                    artist = null,
                    isAlbum = false
                )
            }

            OdesliResult.Error(e.localizedMessage ?: "Unknown network error")
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

    private fun isAlbumUrl(url: String): Boolean {
        if (url.contains("i=")) return false // Apple Music track ID!
        if (url.contains("trackAsin=")) return false // Amazon Music track ID!
        if (url.contains("/track/")) return false // Spotify / Deezer / Tidal track!
        if (url.contains("/song/")) return false // Apple Music song!
        return url.contains("/album/") || url.contains("/albums/") || url.contains("/album") || url.contains("album.link")
    }

    private fun isPlaylistUrl(url: String): Boolean {
        if (url.contains("i=") || url.contains("trackAsin=") || url.contains("/track/") || url.contains("/song/")) return false
        return url.contains("/playlist/") || url.contains("/playlists/") || url.contains("link.deezer.com")
    }

    fun isSearchUrl(url: String): Boolean {
        val clean = url.lowercase()
        return clean.contains("spotify.com/search/") ||
                (clean.contains("spotify.com") && clean.contains("/search")) ||
                clean.contains("music.apple.com") && clean.contains("/search") ||
                clean.contains("music.youtube.com/search") ||
                clean.contains("youtube.com/results") ||
                clean.contains("deezer.com") && clean.contains("/search") ||
                clean.contains("tidal.com") && clean.contains("/search") ||
                clean.contains("music.amazon.") && clean.contains("/search")
    }

    fun extractSearchQuery(url: String): String? {
        val clean = url.trim()
        val lower = clean.lowercase()

        val rawQuery: String? = when {
            // Spotify: open.spotify.com/search/Farin%20Urlaub%20Kein%20Pardon or /intl-de/search/...
            lower.contains("spotify.com") && lower.contains("/search") -> {
                val afterSearch = clean.substringAfter("/search/").substringAfter("/search?")
                if (afterSearch.startsWith("q=")) {
                    afterSearch.substringAfter("q=").substringBefore("&").substringBefore("?")
                } else {
                    afterSearch.substringBefore("?").substringBefore("&")
                }
            }
            // Apple Music: music.apple.com/de/search?term=Farin%20Urlaub
            lower.contains("apple.com") && lower.contains("/search") -> {
                if (clean.contains("term=")) {
                    clean.substringAfter("term=").substringBefore("&")
                } else if (clean.contains("q=")) {
                    clean.substringAfter("q=").substringBefore("&")
                } else {
                    clean.substringAfter("/search/").substringBefore("?").substringBefore("&")
                }
            }
            // YouTube Music: music.youtube.com/search?q=Farin+Urlaub
            lower.contains("music.youtube.com/search") -> {
                clean.substringAfter("q=").substringBefore("&")
            }
            // YouTube: youtube.com/results?search_query=Farin+Urlaub
            lower.contains("youtube.com/results") -> {
                clean.substringAfter("search_query=").substringBefore("&")
            }
            // Deezer: deezer.com/search/Farin%20Urlaub or deezer.com/de/search/Farin%20Urlaub
            lower.contains("deezer.com") && lower.contains("/search") -> {
                val afterSearch = clean.substringAfter("/search/").substringAfter("/search?")
                if (afterSearch.startsWith("q=")) {
                    afterSearch.substringAfter("q=").substringBefore("&")
                } else {
                    afterSearch.substringBefore("?").substringBefore("&")
                }
            }
            // Tidal: tidal.com/search?q=Farin%20Urlaub or listen.tidal.com/search?q=...
            lower.contains("tidal.com") && lower.contains("/search") -> {
                if (clean.contains("q=")) {
                    clean.substringAfter("q=").substringBefore("&")
                } else {
                    clean.substringAfter("/search/").substringBefore("?").substringBefore("&")
                }
            }
            // Amazon Music: music.amazon.com/search/Farin%20Urlaub or ?k=...
            lower.contains("music.amazon.") && lower.contains("/search") -> {
                if (clean.contains("k=")) {
                    clean.substringAfter("k=").substringBefore("&")
                } else if (clean.contains("keywords=")) {
                    clean.substringAfter("keywords=").substringBefore("&")
                } else {
                    clean.substringAfter("/search/").substringBefore("?").substringBefore("&")
                }
            }
            else -> null
        }

        if (rawQuery.isNullOrBlank()) return null

        return try {
            java.net.URLDecoder.decode(rawQuery, "UTF-8")
        } catch (_: Exception) {
            rawQuery.replace("+", " ").replace("%20", " ")
        }
    }


    /**
     * Normalizes streaming URLs into direct 0-redirect Songlink/Albumlink URLs
     */
    private fun normalizeToSongLinkDirectUrl(url: String): String {
        val clean = if (url.contains("?")) url.substringBefore("?") else url

        // Spotify (Supports standard and international /intl-*/ links)
        if (clean.contains("spotify.com") && clean.contains("/track/")) {
            val id = clean.substringAfter("/track/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/s/$id"
        }
        if (clean.contains("spotify.com") && clean.contains("/album/")) {
            val id = clean.substringAfter("/album/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://album.link/s/$id"
        }

        // Apple Music
        if (url.contains("apple.com") && url.contains("i=")) {
            val id = url.substringAfter("i=").substringBefore("&").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/i/$id"
        }
        if (clean.contains("apple.com") && clean.contains("/song/")) {
            val id = clean.substringAfter("/song/").substringAfterLast("/").substringBefore("?").trim()
            if (id.isNotEmpty() && id.all { it.isDigit() }) return "https://song.link/i/$id"
        }
        if (clean.contains("apple.com") && clean.contains("/album/")) {
            val id = clean.substringAfter("/album/").substringAfterLast("/").substringBefore("?").trim()
            if (id.isNotEmpty() && id.all { it.isDigit() }) return "https://album.link/i/$id"
        }

        // Deezer
        if (clean.contains("deezer.com") && clean.contains("/track/")) {
            val id = clean.substringAfter("/track/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/d/$id"
        }
        if (clean.contains("deezer.com") && clean.contains("/album/")) {
            val id = clean.substringAfter("/album/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://album.link/d/$id"
        }

        // Tidal
        if (clean.contains("tidal.com") && clean.contains("/track/")) {
            val id = clean.substringAfter("/track/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/t/$id"
        }

        // YouTube
        if (clean.contains("youtu.be/")) {
            val id = clean.substringAfter("youtu.be/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/y/$id"
        }
        if (url.contains("youtube.com/watch") && url.contains("v=")) {
            val id = url.substringAfter("v=").substringBefore("&").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/y/$id"
        }

        val regionalCleaned = url
            .replace("music.amazon.de", "music.amazon.com")
            .replace("music.amazon.co.uk", "music.amazon.com")
            .replace("music.amazon.fr", "music.amazon.com")
            .replace("music.amazon.it", "music.amazon.com")
            .replace("music.amazon.es", "music.amazon.com")
            .replace("music.amazon.co.jp", "music.amazon.com")
            .replace("geo.music.apple.com", "music.apple.com")

        return if (clean.contains("/album/")) "https://album.link/$regionalCleaned" else "https://song.link/$regionalCleaned"
    }

    /**
     * Queries song.link web page and extracts JSON from __NEXT_DATA__
     */
    private fun fetchSongLinkData(url: String): SongLinkData? {
        return try {
            val targetSongLink = normalizeToSongLinkDirectUrl(url)
            val req = Request.Builder()
                .url(targetSongLink)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return null
            }

            val html = resp.body?.string() ?: ""
            resp.close()

            val scriptTag = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            if (!html.contains(scriptTag)) return null

            val jsonString = html.substringAfter(scriptTag).substringBefore("</script>")
            val json = JSONObject(jsonString)
            val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps") ?: return null
            val pageData = pageProps.optJSONObject("pageData") ?: return null

            val pageId = pageData.optString("pageId", "")
            val entityUniqueId = pageData.optString("entityUniqueId", "")
            val isAlbumEntity = pageId.contains("|album|") || entityUniqueId.contains("|album|")

            val entityData = pageData.optJSONObject("entityData")
            var title = entityData?.optString("title", "") ?: ""
            var artist = entityData?.optString("artistName", "") ?: ""
            var entityType = entityData?.optString("type", "") ?: (if (isAlbumEntity) "album" else "")

            val sections = pageData.optJSONArray("sections")
            if (sections != null && sections.length() > 0) {
                val firstSection = sections.optJSONObject(0)
                if (title.isEmpty() && firstSection != null) {
                    title = firstSection.optString("title", "")
                }
                if (artist.isEmpty() && firstSection != null) {
                    artist = firstSection.optString("artistName", "")
                }
            }

            val linksMap = mutableMapOf<String, String>()

            // 1. Check pageData.linksByPlatform
            val linksByPlatform = pageData.optJSONObject("linksByPlatform")
            if (linksByPlatform != null) {
                val keys = linksByPlatform.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val linkObj = linksByPlatform.optJSONObject(key)
                    val urlVal = linkObj?.optString("url") ?: ""
                    if (urlVal.isNotEmpty()) {
                        linksMap[key] = urlVal
                    }
                }
            }

            // 2. Check pageData.sections
            if (sections != null) {
                for (i in 0 until sections.length()) {
                    val section = sections.optJSONObject(i) ?: continue
                    val links = section.optJSONArray("links") ?: section.optJSONArray("items") ?: continue
                    for (j in 0 until links.length()) {
                        val linkObj = links.optJSONObject(j) ?: continue
                        val platform = linkObj.optString("platform")
                        val linkUrl = linkObj.optString("url")
                        if (platform.isNotEmpty() && linkUrl.isNotEmpty()) {
                            linksMap[platform] = linkUrl
                        }
                    }
                }
            }

            // 3. Fallback: Extract platform URLs embedded directly in HTML
            val platformPatterns = mapOf(
                "spotify" to Pattern.compile("https://open\\.spotify\\.com/track/([a-zA-Z0-9]+)"),
                "tidal" to Pattern.compile("https://(?:listen|www)\\.tidal\\.com/track/([0-9]+)"),
                "deezer" to Pattern.compile("https://www\\.deezer\\.com/track/([0-9]+)"),
                "amazonMusic" to Pattern.compile("https://music\\.amazon\\.com/albums/[a-zA-Z0-9]+\\?trackAsin=([a-zA-Z0-9]+)"),
                "appleMusic" to Pattern.compile("https://music\\.apple\\.com/[a-z]{2}/(?:album|song)/[^\"]+")
            )
            for ((plat, pat) in platformPatterns) {
                if (!linksMap.containsKey(plat)) {
                    val m = pat.matcher(html)
                    if (m.find()) {
                        val matchedUrl = m.group(0)
                        if (!matchedUrl.isNullOrEmpty()) {
                            linksMap[plat] = matchedUrl
                        }
                    }
                }
            }

            SongLinkData(title = title, artist = artist, type = entityType, links = linksMap)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves direct playable link for specific target platforms (Instant Playback for tracks; catalog search/entity lookup for albums)
     */
    private fun resolveDirectPlatformUrl(query: String, targetPlatformKey: String, isAlbum: Boolean = false): String? {
        return when (targetPlatformKey) {
            "youtubeMusic" -> {
                if (isAlbum) {
                    resolveYouTubeMusicAlbumUrl(query)
                        ?: resolveYouTubeMusicDirectPlayUrl(query)
                        ?: buildSearchUrl(query, "youtubeMusic")
                } else {
                    resolveYouTubeMusicDirectPlayUrl(query)
                        ?: buildSearchUrl(query, "youtubeMusic")
                }
            }
            "appleMusic" -> resolveAppleMusicDirectUrl(query, isAlbum) ?: buildSearchUrl(query, "appleMusic")
            "deezer" -> resolveDeezerDirectUrl(query, isAlbum) ?: buildSearchUrl(query, "deezer")
            "spotify" -> buildSearchUrl(query, "spotify")
            "tidal" -> buildSearchUrl(query, "tidal")
            "amazonMusic" -> buildSearchUrl(query, "amazonMusic")
            else -> buildSearchUrl(query, targetPlatformKey)
        }
    }

    /**
     * Finds exact YouTube Music Playlist / Album ID for full album playback
     */
    private fun resolveYouTubeMusicAlbumUrl(query: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAw%253D%253D")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return null
            }
            val html = resp.body?.string() ?: ""
            resp.close()

            // 1. Official YouTube Music album playlist (OLAK5uy...)
            val olakMatcher = ytAlbumPlaylistPattern.matcher(html)
            if (olakMatcher.find()) {
                val playlistId = olakMatcher.group(1)
                if (!playlistId.isNullOrEmpty()) {
                    return "https://music.youtube.com/playlist?list=$playlistId"
                }
            }

            // 2. Generic playlist ID (PL...)
            val plMatcher = ytGenericPlaylistPattern.matcher(html)
            if (plMatcher.find()) {
                val playlistId = plMatcher.group(1)
                if (!playlistId.isNullOrEmpty()) {
                    return "https://music.youtube.com/playlist?list=$playlistId"
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Finds exact YouTube Video ID for instant direct playback in YouTube Music
     */
    private fun resolveYouTubeMusicDirectPlayUrl(query: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAQ%253D%253D")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return null
            }
            val html = resp.body?.string() ?: ""
            resp.close()

            // 1. Prioritize official videoRenderer (filters out Shorts, reels, fan clips)
            val vrMatcher = ytVideoRendererPattern.matcher(html)
            if (vrMatcher.find()) {
                val videoId = vrMatcher.group(1)
                if (!videoId.isNullOrEmpty()) {
                    return "https://music.youtube.com/watch?v=$videoId"
                }
            }

            // 2. Check JSON videoId (desktop YT)
            val jsonMatcher = ytVideoIdJsonPattern.matcher(html)
            if (jsonMatcher.find()) {
                val videoId = jsonMatcher.group(1)
                if (!videoId.isNullOrEmpty()) {
                    return "https://music.youtube.com/watch?v=$videoId"
                }
            }

            // 3. Check watch?v= format
            val watchMatcher = ytWatchPattern.matcher(html)
            if (watchMatcher.find()) {
                val videoId = watchMatcher.group(1)
                if (!videoId.isNullOrEmpty()) {
                    return "https://music.youtube.com/watch?v=$videoId"
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves direct track/album URL on Apple Music using iTunes Public Search API
     */
    private fun resolveAppleMusicDirectUrl(query: String, isAlbum: Boolean = false): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val entity = if (isAlbum) "album" else "song"
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&entity=$entity&limit=1")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val item = results.getJSONObject(0)
                    val viewUrl = if (isAlbum) item.optString("collectionViewUrl") else item.optString("trackViewUrl")
                    if (viewUrl.isNotEmpty()) {
                        return viewUrl
                    }
                }
            }
            resp.close()
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves direct track/album URL on Deezer using Deezer Public Search API
     */
    private fun resolveDeezerDirectUrl(query: String, isAlbum: Boolean = false): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val endpoint = if (isAlbum) "search/album" else "search"
            val req = Request.Builder()
                .url("https://api.deezer.com/$endpoint?q=$encoded&limit=1")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val item = data.getJSONObject(0)
                    val link = item.optString("link")
                    if (link.isNotEmpty()) {
                        return link
                    }
                }
            }
            resp.close()
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
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&entity=musicArtist&limit=1")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val item = results.getJSONObject(0)
                    val link = item.optString("artistLinkUrl").ifEmpty { item.optString("artistViewUrl") }
                    if (link.isNotEmpty()) {
                        resp.close()
                        return link
                    }
                }
            }
            resp.close()
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveDeezerArtistUrl(artistName: String): String? {
        return try {
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val req = Request.Builder()
                .url("https://api.deezer.com/search/artist?q=$encoded&limit=1")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val item = data.getJSONObject(0)
                    val link = item.optString("link")
                    if (link.isNotEmpty()) {
                        resp.close()
                        return link
                    }
                }
            }
            resp.close()
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveYouTubeMusicArtistUrl(artistName: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode("$artistName artist", "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAg%253D%253D")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return null
            }
            val html = resp.body?.string() ?: ""
            resp.close()

            val channelMatcher = ytChannelIdPattern.matcher(html)
            if (channelMatcher.find()) {
                val channelId = channelMatcher.group(1)
                if (!channelId.isNullOrEmpty()) {
                    return "https://music.youtube.com/channel/$channelId"
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts track and artist string from incoming URLs
     */
    private fun extractTrackInfo(url: String): String? {
        return try {
            // 1. Spotify Track & Album Info (oEmbed + HTML OpenGraph)
            if (url.contains("spotify.com")) {
                val cleanSpotifyUrl = url.substringBefore("?").trim()
                
                // A. Fast Public Spotify oEmbed
                try {
                    val encoded = URLEncoder.encode(cleanSpotifyUrl, "UTF-8")
                    val oembedReq = Request.Builder()
                        .url("https://open.spotify.com/oembed?url=$encoded")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .get()
                        .build()
                    val oembedResp = client.newCall(oembedReq).execute()
                    if (oembedResp.isSuccessful) {
                        val body = oembedResp.body?.string() ?: ""
                        oembedResp.close()
                        val json = JSONObject(body)
                        val title = json.optString("title", "")
                        if (title.isNotEmpty()) {
                            return title
                        }
                    } else {
                        oembedResp.close()
                    }
                } catch (e: Exception) { /* continue */ }

                // B. Page OpenGraph metadata (Artist + Title)
                try {
                    val req = Request.Builder()
                        .url(cleanSpotifyUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        resp.close()
                        val descMatcher = Pattern.compile("<meta\\s+(?:property|name)=[\"']og:description[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                        if (descMatcher.find()) {
                            val desc = descMatcher.group(1) ?: ""
                            if (desc.contains("·")) {
                                val parts = desc.split("·").map { it.trim() }
                                val artist = parts.getOrNull(0) ?: ""
                                val track = parts.getOrNull(1) ?: ""
                                if (artist.isNotEmpty() && track.isNotEmpty()) {
                                    return "$artist $track"
                                }
                            }
                        }
                        val titleMatcher = Pattern.compile("<meta\\s+(?:property|name)=[\"']og:title[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                        if (titleMatcher.find()) {
                            val title = titleMatcher.group(1) ?: ""
                            if (title.isNotEmpty()) return title
                        }
                    } else {
                        resp.close()
                    }
                } catch (e: Exception) { /* continue */ }
            }
            // 2. Apple Music Track & Album Lookup via iTunes
            else if (url.contains("apple.com") && url.contains("i=")) {
                val trackId = url.substringAfter("i=").substringBefore("&").substringBefore("?")
                if (trackId.isNotEmpty()) {
                    val req = Request.Builder()
                        .url("https://itunes.apple.com/lookup?id=$trackId")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val track = results.getJSONObject(0)
                            val trackName = track.optString("trackName")
                            val artistName = track.optString("artistName")
                            resp.close()
                            if (trackName.isNotEmpty()) {
                                return if (artistName.isNotEmpty()) "$artistName $trackName" else trackName
                            }
                        }
                    }
                    resp.close()
                }
            }
            else if (url.contains("apple.com") && url.contains("/song/")) {
                val trackId = url.substringAfter("/song/").substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (trackId.isNotEmpty() && trackId.all { it.isDigit() }) {
                    val req = Request.Builder()
                        .url("https://itunes.apple.com/lookup?id=$trackId")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val track = results.getJSONObject(0)
                            val trackName = track.optString("trackName")
                            val artistName = track.optString("artistName")
                            resp.close()
                            if (trackName.isNotEmpty()) {
                                return if (artistName.isNotEmpty()) "$artistName $trackName" else trackName
                            }
                        }
                    }
                    resp.close()
                }
            }
            else if (url.contains("apple.com") && url.contains("/album/")) {
                val albumId = url.substringAfter("/album/").substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (albumId.isNotEmpty() && albumId.all { it.isDigit() }) {
                    val req = Request.Builder()
                        .url("https://itunes.apple.com/lookup?id=$albumId&entity=album")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val album = results.getJSONObject(0)
                            val collectionName = album.optString("collectionName")
                            val artistName = album.optString("artistName")
                            resp.close()
                            if (collectionName.isNotEmpty()) {
                                return if (artistName.isNotEmpty()) "$artistName $collectionName" else collectionName
                            }
                        }
                    }
                    resp.close()
                }
            }
            // 3. YouTube OEmbed
            else if (url.contains("youtube.com") || url.contains("youtu.be")) {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val req = Request.Builder()
                    .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val title = json.optString("title")
                    val author = json.optString("author_name")
                    resp.close()
                    if (title.isNotEmpty()) {
                        return if (author.isNotEmpty() && !title.contains(author, ignoreCase = true)) "$author $title" else title
                    }
                }
                resp.close()
            }
            // 4. Deezer OEmbed
            else if (url.contains("deezer.com")) {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val req = Request.Builder()
                    .url("https://api.deezer.com/oembed?url=$encoded")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val title = json.optString("title")
                    resp.close()
                    if (title.isNotEmpty()) return title
                }
                resp.close()
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts artist name for artist pages across streaming services (Deezer, Spotify, Apple, YouTube, Tidal)
     */
    private fun extractArtistInfo(url: String): String? {
        return try {
            // 1. Deezer Artist (e.g. deezer.com/artist/1234 or deezer.com/en/artist/1234)
            if (url.contains("deezer.com") && url.contains("/artist/")) {
                val artistId = url.substringAfter("/artist/").substringBefore("?").substringBefore("/").trim()
                if (artistId.isNotEmpty()) {
                    val req = Request.Builder()
                        .url("https://api.deezer.com/artist/$artistId")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val name = json.optString("name")
                        resp.close()
                        if (name.isNotEmpty()) return name
                    }
                    resp.close()
                }
            }
            // 2. Spotify Artist (e.g. open.spotify.com/artist/...)
            else if (url.contains("spotify.com") && url.contains("/artist/")) {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val req = Request.Builder()
                    .url("https://open.spotify.com/oembed?url=$encoded")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val title = json.optString("title")
                    resp.close()
                    if (title.isNotEmpty()) return title
                }
                resp.close()
            }
            // 3. Apple Music Artist (e.g. music.apple.com/de/artist/queen/3296287 or /artist/eminem/111051)
            else if (url.contains("apple.com") && url.contains("/artist/")) {
                val artistId = url.substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (artistId.isNotEmpty() && artistId.all { it.isDigit() }) {
                    val req = Request.Builder()
                        .url("https://itunes.apple.com/lookup?id=$artistId")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val artistObj = results.getJSONObject(0)
                            val artistName = artistObj.optString("artistName")
                            resp.close()
                            if (artistName.isNotEmpty()) return artistName
                        }
                    }
                    resp.close()
                }
                val slug = url.substringAfter("/artist/").substringBefore("/").replace("-", " ")
                if (slug.isNotEmpty() && !slug.all { it.isDigit() }) {
                    return slug
                }
            }
            // 4. YouTube / YT Music Channel / Artist
            else if (url.contains("youtube.com/channel/") || url.contains("youtube.com/@") || url.contains("music.youtube.com/channel/")) {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val req = Request.Builder()
                    .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val authorName = json.optString("author_name")
                    val title = json.optString("title")
                    resp.close()
                    if (authorName.isNotEmpty()) return authorName
                    if (title.isNotEmpty()) return title
                }
                resp.close()
            }
            // 5. Tidal Artist
            else if (url.contains("tidal.com") && url.contains("/artist/")) {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    resp.close()
                    val titleTag = "<title>"
                    if (html.contains(titleTag)) {
                        val title = html.substringAfter(titleTag).substringBefore("</title>").substringBefore(" on TIDAL").substringBefore(" | TIDAL").trim()
                        if (title.isNotEmpty()) return title
                    }
                } else {
                    resp.close()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts playlist name from playlist URLs (Deezer, Spotify, Apple Music)
     */
    private fun extractPlaylistInfo(url: String): String? {
        return try {
            // 1. Deezer Playlist
            if (url.contains("deezer.com") && url.contains("/playlist/")) {
                val playlistId = url.substringAfter("/playlist/").substringBefore("?").substringBefore("/").trim()
                if (playlistId.isNotEmpty()) {
                    val req = Request.Builder()
                        .url("https://api.deezer.com/playlist/$playlistId")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val title = json.optString("title")
                        resp.close()
                        if (title.isNotEmpty()) return title
                    }
                    resp.close()
                }
            }
            // 2. Spotify Playlist
            else if (url.contains("spotify.com") && url.contains("/playlist/")) {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val req = Request.Builder()
                    .url("https://open.spotify.com/oembed?url=$encoded")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val title = json.optString("title")
                    resp.close()
                    if (title.isNotEmpty()) return title
                }
                resp.close()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTargetUrl(rawUrl: String, targetPlatformKey: String): String {
        if (targetPlatformKey == "youtubeMusic") {
            if (rawUrl.contains("music.youtube.com")) return rawUrl
            if (rawUrl.contains("youtube.com/playlist") || rawUrl.contains("m.youtube.com/playlist")) {
                return rawUrl.replace("www.youtube.com", "music.youtube.com")
                    .replace("m.youtube.com", "music.youtube.com")
                    .replace("youtube.com", "music.youtube.com")
            }
            if (rawUrl.contains("youtube.com/watch") || rawUrl.contains("m.youtube.com/watch")) {
                return rawUrl.replace("www.youtube.com", "music.youtube.com")
                    .replace("m.youtube.com", "music.youtube.com")
                    .replace("youtube.com", "music.youtube.com")
            }
            if (rawUrl.contains("youtu.be/")) {
                val videoId = rawUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                if (videoId.isNotEmpty()) {
                    return "https://music.youtube.com/watch?v=$videoId"
                }
            }
        }
        return rawUrl
    }

    private fun buildSearchUrl(queryText: String, targetPlatformKey: String): String {
        val query = URLEncoder.encode(queryText, "UTF-8")
        return when (targetPlatformKey) {
            "youtubeMusic" -> "https://music.youtube.com/search?q=$query"
            "appleMusic" -> "https://music.apple.com/search?term=$query"
            "spotify" -> "https://open.spotify.com/search/$query"
            "tidal" -> "https://listen.tidal.com/search?q=$query"
            "deezer" -> "https://www.deezer.com/search/$query"
            "amazonMusic" -> "https://music.amazon.com/search/$query"
            else -> "https://music.youtube.com/search?q=$query"
        }
    }

    private fun queryCustomApi(apiUrl: String, token: String, url: String, targetPlatform: String): String? {
        return try {
            val jsonPayload = JSONObject().apply {
                put("url", url)
                put("targetPlatform", targetPlatform)
            }.toString()

            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val requestBuilder = Request.Builder()
                .url(apiUrl)
                .post(body)

            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val respString = response.body?.string() ?: ""
                val respJson = JSONObject(respString)
                val targetUrl = respJson.optString("targetUrl", respJson.optString("url", ""))
                if (targetUrl.isNotEmpty()) {
                    return targetUrl
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun extractCleanUrl(rawInput: String): String? {
        val matcher = urlPattern.matcher(rawInput)
        var extracted = if (matcher.find()) {
            matcher.group(1)
        } else if (rawInput.startsWith("http://") || rawInput.startsWith("https://")) {
            rawInput.trim()
        } else {
            null
        }

        if (extracted != null) {
            extracted = extracted.trimEnd('.', ',', '!', '?', ';', ':', '>', ']', '"', '\'', '»', '”', '“')
            val openCount = extracted.count { it == '(' }
            val closeCount = extracted.count { it == ')' }
            if (closeCount > openCount && extracted.endsWith(")")) {
                extracted = extracted.substring(0, extracted.length - (closeCount - openCount))
            }
        }

        return if (extracted != null) UrlUtils.normalizeUrl(extracted) else null
    }

    private fun isShortLinkDomain(url: String): Boolean {
        return url.contains("spotify.link") ||
                url.contains("deezer.page.link") ||
                url.contains("link.deezer.com") ||
                url.contains("youtu.be") ||
                url.contains("t.co/") ||
                url.contains("://t.co") ||
                url.contains("bit.ly") ||
                url.contains("amzn.to") ||
                url.contains("a.co/") ||
                url.contains("://a.co") ||
                url.contains("apple.co/") ||
                url.contains("://apple.co")
    }

    private fun resolveCanonicalUrl(url: String): String {
        if (!isShortLinkDomain(url)) return url

        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            val response = client.newCall(req).execute()
            val finalHttpUrl = response.request.url.toString()

            // If standard HTTP redirect successfully went to the canonical domain
            if (!isShortLinkDomain(finalHttpUrl)) {
                response.close()
                return finalHttpUrl
            }

            // Otherwise inspect HTML body for og:url, canonical link, or window.location
            val body = response.body?.string() ?: ""
            response.close()

            // 1. og:url or twitter:url
            val ogMatcher = Pattern.compile("<meta\\s+(?:property|name)=[\"'](?:og:url|twitter:url)[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(body)
            if (ogMatcher.find()) {
                val ogUrl = ogMatcher.group(1)
                if (!ogUrl.isNullOrBlank() && !isShortLinkDomain(ogUrl)) {
                    return ogUrl
                }
            }

            // 2. link rel="canonical"
            val canonicalMatcher = Pattern.compile("<link\\s+rel=[\"']canonical[\"']\\s+href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(body)
            if (canonicalMatcher.find()) {
                val canUrl = canonicalMatcher.group(1)
                if (!canUrl.isNullOrBlank() && !isShortLinkDomain(canUrl)) {
                    return canUrl
                }
            }

            // 3. window.location or destination url in script
            val jsMatcher = Pattern.compile("(https?://(?:open\\.spotify\\.com|www\\.deezer\\.com|music\\.apple\\.com|music\\.youtube\\.com|music\\.amazon\\.[a-z.]+)/[^\"'\\s<]+)", Pattern.CASE_INSENSITIVE).matcher(body)
            if (jsMatcher.find()) {
                val jsUrl = jsMatcher.group(1)
                if (!jsUrl.isNullOrBlank()) {
                    return jsUrl
                }
            }

            finalHttpUrl
        } catch (e: Exception) {
            url
        }
    }

    suspend fun queryL2ServerCache(
        canonicalUrl: String,
        targetPlatformKey: String
    ): OdesliResult.Success? = withContext(Dispatchers.IO) {
        val authToken = ProManager.getAuthToken()
        val encodedUrl = URLEncoder.encode(canonicalUrl, "UTF-8")
        val endpoints = listOf(
            "https://cache.songflip.link/resolve?url=$encodedUrl",
            "https://songflip-web.web.app/resolve?url=$encodedUrl"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $authToken")
                    .addHeader("Accept", "application/json")
                    .build()

                val fastClient = client.newBuilder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()

                val result = fastClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    if (json.optString("status") != "success") return@use null
                    val item = json.optJSONObject("item") ?: return@use null
                    val title = item.optString("title").ifBlank { null }
                    val artist = item.optString("artist").ifBlank { null }
                    val isAlbum = item.optBoolean("isAlbum", false)
                    val links = item.optJSONObject("links") ?: JSONObject()

                    val rawTarget = when (targetPlatformKey) {
                        "spotify" -> links.optString("spotify")
                        "appleMusic" -> links.optString("appleMusic")
                        "youtubeMusic" -> links.optString("youtubeMusic")
                        "deezer" -> links.optString("deezer")
                        "tidal" -> links.optString("tidal")
                        "amazonMusic" -> links.optString("amazonMusic")
                        else -> ""
                    }

                    if (!rawTarget.isNullOrBlank()) {
                        val formatted = formatTargetUrl(rawTarget, targetPlatformKey)
                        OdesliResult.Success(
                            targetUrl = formatted,
                            platform = targetPlatformKey,
                            title = title,
                            artist = artist,
                            isAlbum = isAlbum
                        )
                    } else {
                        null
                    }
                }

                if (result != null) {
                    return@withContext result
                }
            } catch (e: Exception) {
                // Endpoint unreachable or timed out; continue to next fallback
            }
        }
        null
    }
}
