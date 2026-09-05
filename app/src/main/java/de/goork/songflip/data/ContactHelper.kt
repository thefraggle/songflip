package de.goork.songflip.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import de.goork.songflip.core.analytics.AptabaseClient

/**
 * Utility to coordinate user feedback & support via direct email to songflip@goork.de.
 */
object ContactHelper {
    const val SUPPORT_EMAIL = "songflip@goork.de"

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
