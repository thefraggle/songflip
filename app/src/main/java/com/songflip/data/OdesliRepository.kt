package com.songflip.data

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

    suspend fun resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String = "youtubeMusic",
        customApiUrl: String = "",
        customApiToken: String = ""
    ): OdesliResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Extract clean URL from raw input string (removes share text)
            val cleanUrl = extractCleanUrl(inputUrl)
                ?: return@withContext OdesliResult.Error("No valid URL found in input")

            // Step 2: Try Custom API / n8n Webhook Endpoint if configured
            if (customApiUrl.isNotBlank()) {
                val customResult = queryCustomApi(customApiUrl, customApiToken, cleanUrl, targetPlatformKey)
                if (customResult != null) {
                    return@withContext OdesliResult.Success(formatTargetUrl(customResult, targetPlatformKey), "custom_api")
                }
            }

            // Step 3: Resolve short links (spotify.link, deezer.page.link, amzn.to, etc.)
            val canonicalUrl = if (isShortLinkDomain(cleanUrl)) {
                resolveCanonicalUrl(cleanUrl)
            } else {
                cleanUrl
            }

            // Step 4: Query Odesli API (api.song.link)
            val encodedUrl = URLEncoder.encode(canonicalUrl, "UTF-8")
            val apiUrl = "https://api.song.link/v1-alpha.1/links?url=$encodedUrl"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "SongFlip-Android-App/1.0")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                if (!bodyString.isNullOrEmpty()) {
                    val json = JSONObject(bodyString)
                    val linksByPlatform = json.optJSONObject("linksByPlatform")

                    if (linksByPlatform != null) {
                        // Check direct platform or platform aliases
                        val platformObj = linksByPlatform.optJSONObject(targetPlatformKey)
                            ?: when (targetPlatformKey) {
                                "youtubeMusic" -> linksByPlatform.optJSONObject("youtube")
                                "amazonMusic" -> linksByPlatform.optJSONObject("amazonMusic")
                                    ?: linksByPlatform.optJSONObject("amazon")
                                else -> null
                            }

                        if (platformObj != null) {
                            val rawTargetUrl = platformObj.optString("url")
                            if (rawTargetUrl.isNotEmpty()) {
                                val formattedUrl = formatTargetUrl(rawTargetUrl, targetPlatformKey)
                                return@withContext OdesliResult.Success(formattedUrl, targetPlatformKey)
                            }
                        }
                    }

                    // Extract entity title if target platform wasn't directly in linksByPlatform
                    val entityTitle = extractEntityTitle(json)
                    if (!entityTitle.isNullOrEmpty()) {
                        val searchUrl = buildSearchUrl(entityTitle, targetPlatformKey)
                        return@withContext OdesliResult.Success(searchUrl, "${targetPlatformKey}_search")
                    }

                    // Fallback to Odesli song.link page only if no entity title exists
                    val pageUrl = json.optString("pageUrl")
                    if (pageUrl.isNotEmpty()) {
                        return@withContext OdesliResult.Success(pageUrl, "songlink")
                    }
                }
            }

            // Step 5: Multi-Source Fallback via Spotify / Deezer / YouTube OEmbed
            val fallbackSearchUrl = buildFallbackSearchUrl(canonicalUrl, targetPlatformKey)
            if (fallbackSearchUrl != null) {
                return@withContext OdesliResult.Success(fallbackSearchUrl, "${targetPlatformKey}_search")
            }

            OdesliResult.Error("Could not resolve link (HTTP ${response.code})")
        } catch (e: Exception) {
            val cleanUrl = extractCleanUrl(inputUrl) ?: inputUrl
            val fallbackSearchUrl = buildFallbackSearchUrl(cleanUrl, targetPlatformKey)
            if (fallbackSearchUrl != null) {
                return@withContext OdesliResult.Success(fallbackSearchUrl, "${targetPlatformKey}_search")
            }

            OdesliResult.Error(e.localizedMessage ?: "Unknown network error")
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

    private fun extractEntityTitle(json: JSONObject): String? {
        val entities = json.optJSONObject("entitiesByUniqueId") ?: return null
        val keys = entities.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entity = entities.optJSONObject(key) ?: continue
            val title = entity.optString("title")
            val artist = entity.optString("artistName")
            if (title.isNotEmpty()) {
                return if (artist.isNotEmpty() && !title.contains(artist, ignoreCase = true)) {
                    "$artist $title"
                } else {
                    title
                }
            }
        }
        return null
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
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = client.newCall(headRequest).execute()
            val finalUrl = response.request.url.toString()
            response.close()
            finalUrl
        } catch (e: Exception) {
            url
        }
    }

    private fun buildFallbackSearchUrl(url: String, targetPlatformKey: String): String? {
        return try {
            var title: String? = null

            // 1. Spotify OEmbed
            if (url.contains("spotify.com")) {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val req = Request.Builder()
                    .url("https://open.spotify.com/oembed?url=$encoded")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        title = json.optString("title")
                    }
                }
                resp.close()
            }
            // 2. YouTube / YouTube Music OEmbed
            else if (url.contains("youtube.com") || url.contains("youtu.be")) {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val req = Request.Builder()
                    .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        title = json.optString("title")
                    }
                }
                resp.close()
            }
            // 3. Deezer OEmbed
            else if (url.contains("deezer.com")) {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val req = Request.Builder()
                    .url("https://api.deezer.com/oembed?url=$encoded")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        title = json.optString("title")
                    }
                }
                resp.close()
            }

            if (title.isNullOrEmpty()) {
                return null
            }

            return buildSearchUrl(title, targetPlatformKey)
        } catch (e: Exception) {
            null
        }
    }
}
