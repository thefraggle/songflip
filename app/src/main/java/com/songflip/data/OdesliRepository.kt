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
        customApiUrl: String = ""
    ): OdesliResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Extract clean URL from raw input string (removes share text)
            val cleanUrl = extractCleanUrl(inputUrl)
                ?: return@withContext OdesliResult.Error("No valid URL found in input")

            // Step 2: Try Custom API / n8n Webhook Endpoint if configured
            if (customApiUrl.isNotBlank()) {
                val customResult = queryCustomApi(customApiUrl, cleanUrl, targetPlatformKey)
                if (customResult != null) {
                    return@withContext OdesliResult.Success(customResult, "custom_api")
                }
            }

            // Step 3: Resolve short links (spotify.link, deezer.page.link) if needed
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
                        val platformObj = linksByPlatform.optJSONObject(targetPlatformKey)
                            ?: if (targetPlatformKey == "youtubeMusic") {
                                linksByPlatform.optJSONObject("youtube")
                            } else null

                        if (platformObj != null) {
                            val targetUrl = platformObj.optString("url")
                            if (targetUrl.isNotEmpty()) {
                                return@withContext OdesliResult.Success(targetUrl, targetPlatformKey)
                            }
                        }
                    }

                    // Fallback to Odesli song.link page if target app link isn't directly listed
                    val pageUrl = json.optString("pageUrl")
                    if (pageUrl.isNotEmpty()) {
                        return@withContext OdesliResult.Success(pageUrl, "songlink")
                    }
                }
            }

            // Step 5: High-Reliability Fallback Search via Spotify OEmbed / Meta tags
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

    private fun queryCustomApi(apiUrl: String, url: String, targetPlatform: String): String? {
        return try {
            val jsonPayload = JSONObject().apply {
                put("url", url)
                put("targetPlatform", targetPlatform)
            }.toString()

            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(apiUrl)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
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
               url.contains("bit.ly")
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

            // Try Spotify OEmbed API first if it's a Spotify link
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

            if (title.isNullOrEmpty()) {
                return null
            }

            val query = URLEncoder.encode(title, "UTF-8")
            when (targetPlatformKey) {
                "youtubeMusic" -> "https://music.youtube.com/search?q=$query"
                "appleMusic" -> "https://music.apple.com/search?term=$query"
                "spotify" -> "https://open.spotify.com/search/$query"
                "tidal" -> "https://listen.tidal.com/search?q=$query"
                "deezer" -> "https://www.deezer.com/search/$query"
                else -> "https://music.youtube.com/search?q=$query"
            }
        } catch (e: Exception) {
            null
        }
    }
}
