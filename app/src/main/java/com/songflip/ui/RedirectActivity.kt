package com.songflip.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.songflip.R
import com.songflip.data.OdesliRepository
import com.songflip.data.OdesliResult
import com.songflip.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Transparent activity that silently intercepts incoming music links in the background,
 * resolves them via Odesli API to the user's preferred target player, and launches the target link.
 *
 * Uses MapFlip's proven browser-targeting fallback to prevent infinite redirect loops.
 */
class RedirectActivity : Activity() {

    private val odesliRepository = OdesliRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepository = SettingsRepository(this)
        val incomingUri = intent?.data
        if (incomingUri == null) {
            finish()
            suppressTransitionAnimation()
            return
        }

        val incomingUrl = incomingUri.toString()
        val host = incomingUri.host ?: ""
        val sourcePlatformKey = detectSourcePlatformKey(host)

        // If the user has disabled link interception for this platform, bypass SongFlip to prevent infinite loops
        if (sourcePlatformKey != null && !settingsRepository.isInputPlatformEnabled(sourcePlatformKey)) {
            forwardOriginalUrl(incomingUri)
            finish()
            suppressTransitionAnimation()
            return
        }

        // Show immediate toast feedback on link tap
        Toast.makeText(
            applicationContext,
            getString(R.string.redirecting_toast),
            Toast.LENGTH_SHORT
        ).show()

        val targetPlatform = settingsRepository.targetPlatform

        CoroutineScope(Dispatchers.Main + Job()).launch {
            // Strict 3.5-second timeout to ensure the app never hangs indefinitely
            val result = withTimeoutOrNull(3500L) {
                odesliRepository.resolveTargetUrl(incomingUrl, targetPlatformKey = targetPlatform)
            }

            if (result is OdesliResult.Success) {
                openUrl(result.targetUrl)
            } else {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.redirect_error_toast),
                    Toast.LENGTH_SHORT
                ).show()
                forwardOriginalUrl(incomingUri)
            }
            finish()
            suppressTransitionAnimation()
        }
    }

    private fun detectSourcePlatformKey(host: String): String? {
        return when {
            host.contains("spotify") -> "spotify"
            host.contains("apple") -> "appleMusic"
            host.contains("youtube") || host.contains("youtu.be") -> "youtubeMusic"
            host.contains("tidal") -> "tidal"
            host.contains("deezer") -> "deezer"
            else -> null
        }
    }

    private fun openUrl(url: String) {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(viewIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Forwards original music URL to a web browser when redirect is disabled or resolution fails.
     * Queries generic HTTPS browsers and sets explicit package name to guarantee bypassing SongFlip.
     */
    private fun forwardOriginalUrl(uri: Uri) {
        try {
            val genericWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }

            val browserPackage = packageManager.queryIntentActivities(genericWebIntent, 0)
                .map { it.activityInfo.packageName }
                .firstOrNull { it != packageName }

            if (browserPackage != null) {
                val targetIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(browserPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(targetIntent)
                return
            }

            // Fallback: browser selector intent
            val selectorIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_BROWSER)
            }
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                selector = selectorIntent
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
        } catch (e: Exception) {
            openUrl(uri.toString())
        }
    }

    /** Suppress enter/exit animation so the redirect is 100% invisible. */
    private fun suppressTransitionAnimation() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
