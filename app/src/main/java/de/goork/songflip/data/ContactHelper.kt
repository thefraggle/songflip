package de.goork.songflip.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import de.goork.songflip.core.analytics.AptabaseClient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Utility to coordinate user feedback & support via direct API or email.
 */
object ContactHelper {
    const val SUPPORT_EMAIL = "songflip@goork.de"
    private const val FEEDBACK_API_URL = "https://songflip-web.web.app/api/feedback"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun submitFeedback(
        context: Context,
        category: String,
        message: String,
        email: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val appVersion = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "v" + (pInfo.versionName ?: "1.6.9")
        } catch (e: Exception) {
            "v1.6.9"
        }
        val androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        val json = JSONObject().apply {
            put("category", category.lowercase())
            put("message", message.trim())
            if (!email.isNullOrBlank()) {
                put("email", email.trim())
            }
            put("app_version", appVersion)
            put("android_version", androidVersion)
            put("device", device)
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(FEEDBACK_API_URL)
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    AptabaseClient.shared.trackEvent("feedback_submitted", mapOf("category" to category))
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Server error: ${it.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendSupportEmail(context: Context) {
        AptabaseClient.shared.trackEvent("feedback_clicked")

        val appVersion = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "v" + (pInfo.versionName ?: "1.0.0")
        } catch (e: Exception) {
            "v1.0.0"
        }

        val subject = "SongFlip Feedback ($appVersion)"
        val body = """
            
            
            ---
            App Version: $appVersion
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
        """.trimIndent()

        val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            context.startActivity(Intent.createChooser(sendIntent, "Email"))
        } catch (e: Exception) {
            // Fallback: direct ACTION_VIEW mailto URI
            try {
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("mailto:$SUPPORT_EMAIL?subject=${Uri.encode(subject)}")
                )
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, SUPPORT_EMAIL, Toast.LENGTH_LONG).show()
            }
        }
    }
}
