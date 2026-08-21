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
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
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

            // 3. Resolve short links (spotify.link, deezer.page.link, amzn.to, youtu.be, etc.)
            val canonicalUrl = if (isShortLinkDomain(cleanUrl)) {
                resolveCanonicalUrl(cleanUrl)
            } else {
                cleanUrl
            }

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

                // If target platform link is not directly available, use the extracted track title + artist
                if (songLinkData.title.isNotEmpty()) {
                    val query = if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                        "${songLinkData.artist} ${songLinkData.title}"
                    } else {
                        songLinkData.title
                    }

                    val resolvedDirectUrl = resolveDirectPlatformUrl(query, targetPlatformKey)
                    if (resolvedDirectUrl != null) {
                        return@withContext OdesliResult.Success(resolvedDirectUrl, targetPlatformKey)
                    }
                }
            }

            // 5. Fallback Metadata Extraction via Service OEmbed / Public APIs
            val trackInfo = extractTrackInfo(canonicalUrl)
            if (trackInfo != null && trackInfo.isNotBlank()) {
                val resolvedDirectUrl = resolveDirectPlatformUrl(trackInfo, targetPlatformKey)
                if (resolvedDirectUrl != null) {
                    return@withContext OdesliResult.Success(resolvedDirectUrl, targetPlatformKey)
                }

                // Final Fallback: Direct Search URL in target service
                return@withContext OdesliResult.Success(buildSearchUrl(trackInfo, targetPlatformKey), "${targetPlatformKey}_search")
            }

            OdesliResult.Error("Could not resolve music link")
        } catch (e: Exception) {
            val cleanUrl = extractCleanUrl(inputUrl) ?: inputUrl
            val trackInfo = extractTrackInfo(cleanUrl)
            if (trackInfo != null) {
                val resolved = resolveDirectPlatformUrl(trackInfo, targetPlatformKey)
                    ?: buildSearchUrl(trackInfo, targetPlatformKey)
                return@withContext OdesliResult.Success(resolved, "${targetPlatformKey}_fallback")
            }

            OdesliResult.Error(e.localizedMessage ?: "Unknown network error")
        }
    }

    private data class SongLinkData(
        val title: String,
        val artist: String,
        val links: Map<String, String>
    )

    /**
     * Queries song.link web page and extracts JSON from __NEXT_DATA__
     */
    private fun fetchSongLinkData(url: String): SongLinkData? {
        return try {
            val normalizedUrl = url
                .replace("music.amazon.de", "music.amazon.com")
                .replace("music.amazon.co.uk", "music.amazon.com")
                .replace("music.amazon.fr", "music.amazon.com")
                .replace("music.amazon.it", "music.amazon.com")
                .replace("music.amazon.es", "music.amazon.com")
                .replace("music.amazon.co.jp", "music.amazon.com")
                .replace("geo.music.apple.com", "music.apple.com")

            val targetSongLink = "https://song.link/$normalizedUrl"
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

            SongLinkData(title = title, artist = artist, links = linksMap)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves direct playable track link for specific target platforms (Instant Playback!)
     */
    private fun resolveDirectPlatformUrl(query: String, targetPlatformKey: String): String? {
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
            // 2. Apple Music Track Lookup via iTunes
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

    private fun extractCleanUrl(rawInput: String): String? {
        val matcher = urlPattern.matcher(rawInput)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return if (rawInput.startsWith("http://") || rawInput.startsWith("https://")) rawInput.trim() else null
    }

    private fun isShortLinkDomain(url: String): Boolean {
        return url.contains("spotify.link") ||
                url.contains("deezer.page.link") ||
                url.contains("youtu.be") ||
                url.contains("t.co") ||
                url.contains("bit.ly") ||
                url.contains("amzn.to") ||
                url.contains("a.co")
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
