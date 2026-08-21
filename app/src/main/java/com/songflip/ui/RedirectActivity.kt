package com.songflip.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.songflip.R
import com.songflip.data.OdesliRepository
import com.songflip.data.OdesliResult
import com.songflip.data.PauseHelper
import com.songflip.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Transparent activity that silently intercepts incoming music links (or shared URLs) in the background,
 * resolves them via the 5-tier fallback engine to the user's preferred player, and launches the target link.
 */
class RedirectActivity : Activity() {

    private val odesliRepository = OdesliRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepository = SettingsRepository(this)
        val incomingUrl: String?
        val incomingUri: Uri?

        if (Intent.ACTION_SEND == intent?.action && intent?.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            incomingUrl = sharedText
            incomingUri = Uri.parse(sharedText)
        } else {
            incomingUri = intent?.data
            incomingUrl = incomingUri?.toString()
        }

        if (incomingUrl.isNullOrBlank()) {
            finish()
            suppressTransitionAnimation()
            return
        }

        val host = incomingUri?.host ?: ""
        val sourcePlatformKey = detectSourcePlatformKey(host, incomingUrl)

        // 1. Check if SongFlip is currently paused
        if (PauseHelper.isCurrentlyPaused(this)) {
            if (incomingUri != null) {
                forwardOriginalUrl(incomingUri)
            } else {
                openUrl(incomingUrl)
            }
            finish()
            suppressTransitionAnimation()
            return
        }

        // 2. Check if the user has disabled link interception for this platform
        if (sourcePlatformKey != null && !settingsRepository.isInputPlatformEnabled(sourcePlatformKey)) {
            if (incomingUri != null) {
                forwardOriginalUrl(incomingUri)
            } else {
                openUrl(incomingUrl)
            }
            finish()
            suppressTransitionAnimation()
            return
        }

        // Show immediate visual feedback
        Toast.makeText(
            applicationContext,
            getString(R.string.redirecting_toast),
            Toast.LENGTH_SHORT
        ).show()

        val targetPlatform = settingsRepository.targetPlatform
        val customApiUrl = settingsRepository.customApiUrl
        val customApiToken = settingsRepository.customApiToken

        CoroutineScope(Dispatchers.Main + Job()).launch {
            // Strict 3.5-second timeout
            val result = withTimeoutOrNull(3500L) {
                odesliRepository.resolveTargetUrl(
                    inputUrl = incomingUrl,
                    targetPlatformKey = targetPlatform,
                    customApiUrl = customApiUrl,
                    customApiToken = customApiToken
                )
            }

            if (result is OdesliResult.Success) {
                openUrl(result.targetUrl)
            } else {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.redirect_error_toast),
                    Toast.LENGTH_SHORT
                ).show()
                if (incomingUri != null) {
                    forwardOriginalUrl(incomingUri)
                } else {
                    openUrl(incomingUrl)
                }
            }
            finish()
            suppressTransitionAnimation()
        }
    }

    private fun detectSourcePlatformKey(host: String, rawText: String): String? {
        val lower = (host + " " + rawText).lowercase()
        return when {
            lower.contains("spotify") -> "spotify"
            lower.contains("apple.com") || lower.contains("music.apple") -> "appleMusic"
            lower.contains("youtube") || lower.contains("youtu.be") -> "youtubeMusic"
            lower.contains("tidal") -> "tidal"
            lower.contains("deezer") -> "deezer"
            lower.contains("amazon.") || lower.contains("amzn.to") || lower.contains("a.co") -> "amazonMusic"
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
