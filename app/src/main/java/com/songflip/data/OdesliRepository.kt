package com.songflip.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class OdesliResult {
    data class Success(val targetUrl: String, val platform: String) : OdesliResult()
    data class Error(val message: String) : OdesliResult()
}

class OdesliRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String = "youtubeMusic"
    ): OdesliResult = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(inputUrl, "UTF-8")
            val apiUrl = "https://api.song.link/v1-alpha.1/links?url=$encodedUrl"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "SongFlip-Android-App")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext OdesliResult.Error("API returned status ${response.code}")
            }

            val bodyString = response.body?.string()
                ?: return@withContext OdesliResult.Error("Empty response body")

            val json = JSONObject(bodyString)
            val linksByPlatform = json.optJSONObject("linksByPlatform")
                ?: return@withContext OdesliResult.Error("No platform links found")

            // Try specified target platform first
            val platformObj = linksByPlatform.optJSONObject(targetPlatformKey)
                ?: if (targetPlatformKey == "youtubeMusic") {
                    // Fallback to standard YouTube if YouTube Music isn't direct
                    linksByPlatform.optJSONObject("youtube")
                } else null

            if (platformObj != null) {
                val url = platformObj.optString("url")
                if (url.isNotEmpty()) {
                    return@withContext OdesliResult.Success(url, targetPlatformKey)
                }
            }

            // Fallback to song.link pageUrl if exact target platform is missing
            val pageUrl = json.optString("pageUrl")
            if (pageUrl.isNotEmpty()) {
                return@withContext OdesliResult.Success(pageUrl, "songlink")
            }

            OdesliResult.Error("No compatible target link found")
        } catch (e: Exception) {
            OdesliResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}
