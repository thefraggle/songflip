package de.goork.songflip.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import de.goork.songflip.R
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.OdesliResult
import de.goork.songflip.data.PackageUtils
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Transparent activity that silently intercepts incoming music links (or shared URLs) in the background,
 * resolves them via the 5-tier fallback engine to the user's preferred player, and launches the target link.
 */
class RedirectActivity : ComponentActivity() {

    private val odesliRepository = OdesliRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepository = SettingsRepository(this)

        val rawInput = if (Intent.ACTION_SEND == intent?.action && intent?.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        } else {
            intent?.dataString ?: ""
        }

        val incomingUrl = odesliRepository.extractCleanUrl(rawInput) ?: rawInput
        if (incomingUrl.isBlank() || (!incomingUrl.startsWith("http://") && !incomingUrl.startsWith("https://"))) {
            finish()
            suppressTransitionAnimation()
            return
        }

        val incomingUri = Uri.parse(incomingUrl)
        val host = incomingUri.host ?: ""
        val sourcePlatformKey = detectSourcePlatformKey(host, incomingUrl)

        // 1. Check if SongFlip is currently paused
        if (PauseHelper.isCurrentlyPaused(this)) {
            forwardOriginalUrl(incomingUri)
            finish()
            suppressTransitionAnimation()
            return
        }

        // 2. Check if the user has disabled link interception for this platform
        if (sourcePlatformKey != null && !settingsRepository.isInputPlatformEnabled(sourcePlatformKey)) {
            forwardOriginalUrl(incomingUri)
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

        lifecycleScope.launch {
            // Generous 5.0-second timeout to handle cold mobile network requests
            val result = withTimeoutOrNull(5000L) {
                odesliRepository.resolveTargetUrl(
                    inputUrl = incomingUrl,
                    targetPlatformKey = targetPlatform,
                    customApiUrl = customApiUrl,
                    customApiToken = customApiToken
                )
            }

            if (result is OdesliResult.Success) {
                openTargetUrl(result.targetUrl, targetPlatform)
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

    /**
     * Opens target music link directly in target player app if installed (explicit package launch),
     * completely eliminating intent disambiguation dialogs and infinite intercept loops.
     */
    private fun openTargetUrl(url: String, targetPlatformKey: String) {
        val targetPackage = PackageUtils.packageMap[targetPlatformKey]
        val isTargetInstalled = PackageUtils.isAppInstalled(this, targetPlatformKey)

        if (isTargetInstalled && targetPackage != null) {
            try {
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(targetPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(appIntent)
                return
            } catch (e: Exception) {
                // Fallback to browser if explicit package launch fails
            }
        }

        // App not installed: open in web browser without re-triggering SongFlip
        forwardOriginalUrl(Uri.parse(url))
    }

    /**
     * Forwards original music URL to a web browser when redirect is disabled, paused, or resolution fails.
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
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(fallbackIntent)
            } catch (ignored: Exception) {}
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
