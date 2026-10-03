package de.goork.songflip.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Single source for legal URLs. They were duplicated in several composables before;
 * the paywall needs them too, and a third copy would drift sooner or later.
 */
object LegalLinks {
    const val PRIVACY = "https://songflip.link/privacy-policy.html"
    const val IMPRINT = "https://songflip.link/imprint.html"
    const val TERMS = "https://songflip.link/terms.html"

    private const val TAG = "LegalLinks"

    /**
     * Opens [url] in the user's browser. Devices without any browser (kiosk / stripped ROMs)
     * throw ActivityNotFoundException – we log instead of crashing the paywall.
     */
    fun open(context: Context, url: String): Boolean {
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity available to open $url", e)
            false
        }
    }
}
