package de.goork.songflip.data

import kotlinx.coroutines.Dispatchers
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
    data class Success(val targetUrl: String, val platform: String) : OdesliResult()
    data class Error(val message: String) : OdesliResult()
}

class OdesliRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val urlPattern = Pattern.compile("(https?://[^\\s<>'\"()]+)")
    private val ytVideoIdJsonPattern = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
    private val ytWatchPattern = Pattern.compile("/watch\\?v=([a-zA-Z0-9_-]{11})")

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
                    return@withContext OdesliResult.Success(formatTargetUrl(customResult, targetPlatformKey), "custom_api")
                }
            }

            // 3. Resolve short links (spotify.link, link.deezer.com, deezer.page.link, amzn.to, youtu.be, etc.)
            val canonicalUrl = if (isShortLinkDomain(cleanUrl)) {
                resolveCanonicalUrl(cleanUrl)
            } else {
                cleanUrl
            }

            val isExplicitAlbumUrl = isAlbumUrl(canonicalUrl)

            // 4. Try Songlink / Odesli web page metadata parsing (__NEXT_DATA__)
            val songLinkData = fetchSongLinkData(canonicalUrl)
            if (songLinkData != null) {
                // If direct link for the target platform exists in song.link
                val directUrl = songLinkData.links[targetPlatformKey]
                    ?: if (targetPlatformKey == "youtubeMusic") songLinkData.links["youtube"] else null

                if (!directUrl.isNullOrEmpty()) {
                    val formatted = formatTargetUrl(directUrl, targetPlatformKey)
                    return@withContext OdesliResult.Success(formatted, targetPlatformKey)
                }

                // If target platform link is not directly available, use the extracted track/album title + artist
                if (songLinkData.title.isNotEmpty()) {
                    val query = if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                        "${songLinkData.artist} ${songLinkData.title}"
                    } else {
                        songLinkData.title
                    }

                    val resolvedDirectUrl = resolveDirectPlatformUrl(
                        query = query,
                        targetPlatformKey = targetPlatformKey,
                        isAlbum = songLinkData.isAlbum || isExplicitAlbumUrl
                    )
                    if (resolvedDirectUrl != null) {
                        return@withContext OdesliResult.Success(resolvedDirectUrl, targetPlatformKey)
                    }
                }
            }

            // 5. Fallback Metadata Extraction via Service OEmbed / Public APIs
            val trackInfo = extractTrackInfo(canonicalUrl)
            if (trackInfo != null && trackInfo.isNotBlank()) {
                val resolvedDirectUrl = resolveDirectPlatformUrl(
                    query = trackInfo,
                    targetPlatformKey = targetPlatformKey,
                    isAlbum = isExplicitAlbumUrl
                )
                if (resolvedDirectUrl != null) {
                    return@withContext OdesliResult.Success(resolvedDirectUrl, targetPlatformKey)
                }

                // Final Track/Album Fallback: Direct Search URL in target service
                return@withContext OdesliResult.Success(buildSearchUrl(trackInfo, targetPlatformKey), "${targetPlatformKey}_search")
            }

            // 6. Fallback: Artist Page Detection & Direct Catalog Routing
            val artistInfo = extractArtistInfo(canonicalUrl)
            if (artistInfo != null && artistInfo.isNotBlank()) {
                val searchUrl = buildSearchUrl(artistInfo, targetPlatformKey)
                return@withContext OdesliResult.Success(searchUrl, "${targetPlatformKey}_artist")
            }

            // 7. Fallback: Playlist Detection & Search Routing
            val playlistInfo = extractPlaylistInfo(canonicalUrl)
            if (playlistInfo != null && playlistInfo.isNotBlank()) {
                val searchUrl = buildSearchUrl(playlistInfo, targetPlatformKey)
                return@withContext OdesliResult.Success(searchUrl, "${targetPlatformKey}_playlist")
            }

            OdesliResult.Error("Could not resolve music link")
        } catch (e: Exception) {
            val cleanUrl = extractCleanUrl(inputUrl) ?: inputUrl
            val isExplicitAlbumUrl = isAlbumUrl(cleanUrl)

            val trackInfo = extractTrackInfo(cleanUrl)
            if (trackInfo != null) {
                val resolved = resolveDirectPlatformUrl(trackInfo, targetPlatformKey, isExplicitAlbumUrl)
                    ?: buildSearchUrl(trackInfo, targetPlatformKey)
                return@withContext OdesliResult.Success(resolved, "${targetPlatformKey}_fallback")
            }

            val artistInfo = extractArtistInfo(cleanUrl)
            if (artistInfo != null) {
                return@withContext OdesliResult.Success(buildSearchUrl(artistInfo, targetPlatformKey), "${targetPlatformKey}_artist_fallback")
            }

            val playlistInfo = extractPlaylistInfo(cleanUrl)
            if (playlistInfo != null) {
                return@withContext OdesliResult.Success(buildSearchUrl(playlistInfo, targetPlatformKey), "${targetPlatformKey}_playlist_fallback")
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
        return url.contains("/album/") || url.contains("/album") || url.contains("album.link")
    }

    /**
     * Normalizes streaming URLs into direct 0-redirect Songlink/Albumlink URLs
     */
    private fun normalizeToSongLinkDirectUrl(url: String): String {
        val clean = if (url.contains("?")) url.substringBefore("?") else url

        // Spotify
        if (clean.contains("open.spotify.com/track/")) {
            val id = clean.substringAfter("/track/").substringBefore("/").trim()
            if (id.isNotEmpty()) return "https://song.link/s/$id"
        }
        if (clean.contains("open.spotify.com/album/")) {
            val id = clean.substringAfter("/album/").substringBefore("/").trim()
            if (id.isNotEmpty()) return "https://album.link/s/$id"
        }

        // Apple Music
        if (url.contains("apple.com") && url.contains("i=")) {
            val id = url.substringAfter("i=").substringBefore("&").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/i/$id"
        }
        if (clean.contains("apple.com") && clean.contains("/album/")) {
            val id = clean.trimEnd('/').substringAfterLast("/").trim()
            if (id.isNotEmpty() && id.all { it.isDigit() }) return "https://album.link/i/$id"
        }

        // Deezer
        if (clean.contains("deezer.com") && clean.contains("/track/")) {
            val id = clean.substringAfter("/track/").substringBefore("/").trim()
            if (id.isNotEmpty()) return "https://song.link/d/$id"
        }
        if (clean.contains("deezer.com") && clean.contains("/album/")) {
            val id = clean.substringAfter("/album/").substringBefore("/").trim()
            if (id.isNotEmpty()) return "https://album.link/d/$id"
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

            val entityData = pageData.optJSONObject("entityData")
            val title = entityData?.optString("title", "") ?: ""
            val artist = entityData?.optString("artistName", "") ?: ""
            val entityType = entityData?.optString("type", "") ?: ""

            val linksMap = mutableMapOf<String, String>()
            val sections = pageData.optJSONArray("sections")
            if (sections != null) {
                for (i in 0 until sections.length()) {
                    val section = sections.optJSONObject(i) ?: continue
                    val links = section.optJSONArray("links") ?: continue
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

            SongLinkData(title = title, artist = artist, type = entityType, links = linksMap)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves direct playable link for specific target platforms (Instant Playback for tracks; catalog search for albums)
     */
    private fun resolveDirectPlatformUrl(query: String, targetPlatformKey: String, isAlbum: Boolean = false): String? {
        if (isAlbum) {
            // For albums, always open the full album in target app (never single video)
            return buildSearchUrl(query, targetPlatformKey)
        }

        return when (targetPlatformKey) {
            "youtubeMusic" -> resolveYouTubeMusicDirectPlayUrl(query) ?: buildSearchUrl(query, "youtubeMusic")
            "appleMusic" -> resolveAppleMusicDirectUrl(query) ?: buildSearchUrl(query, "appleMusic")
            "deezer" -> resolveDeezerDirectUrl(query) ?: buildSearchUrl(query, "deezer")
            "spotify" -> buildSearchUrl(query, "spotify")
            "tidal" -> buildSearchUrl(query, "tidal")
            "amazonMusic" -> buildSearchUrl(query, "amazonMusic")
            else -> buildSearchUrl(query, targetPlatformKey)
        }
    }

    /**
     * Finds exact YouTube Video ID for instant direct playback in YouTube Music
     */
    private fun resolveYouTubeMusicDirectPlayUrl(query: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$encodedQuery")
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

            // Check JSON videoId first (modern desktop YT)
            val jsonMatcher = ytVideoIdJsonPattern.matcher(html)
            if (jsonMatcher.find()) {
                val videoId = jsonMatcher.group(1)
                if (!videoId.isNullOrEmpty()) {
                    return "https://music.youtube.com/watch?v=$videoId"
                }
            }

            // Check watch?v= format
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
     * Resolves direct track URL on Apple Music using iTunes Public Search API
     */
    private fun resolveAppleMusicDirectUrl(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&entity=song&limit=1")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val track = results.getJSONObject(0)
                    val trackViewUrl = track.optString("trackViewUrl")
                    if (trackViewUrl.isNotEmpty()) {
                        return trackViewUrl
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
     * Resolves direct track URL on Deezer using Deezer Public Search API
     */
    private fun resolveDeezerDirectUrl(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://api.deezer.com/search?q=$encoded&limit=1")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val track = data.getJSONObject(0)
                    val link = track.optString("link")
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

    /**
     * Extracts track and artist string from incoming URLs
     */
    private fun extractTrackInfo(url: String): String? {
        return try {
            // 1. Spotify OEmbed
            if (url.contains("spotify.com")) {
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
            else if (url.contains("apple.com") && url.contains("/album/")) {
                val albumId = url.substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
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
        if (matcher.find()) {
            return matcher.group(1)
        }
        return if (rawInput.startsWith("http://") || rawInput.startsWith("https://")) rawInput.trim() else null
    }

    private fun isShortLinkDomain(url: String): Boolean {
        return url.contains("spotify.link") ||
                url.contains("deezer.page.link") ||
                url.contains("link.deezer.com") ||
                url.contains("youtu.be") ||
                url.contains("t.co") ||
                url.contains("bit.ly") ||
                url.contains("amzn.to") ||
                url.contains("a.co") ||
                url.contains("apple.co")
    }

    private fun resolveCanonicalUrl(url: String): String {
        return try {
            val headRequest = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(headRequest).execute()
            val finalUrl = response.request.url.toString()
            response.close()
            finalUrl
        } catch (e: Exception) {
            url
        }
    }
}
