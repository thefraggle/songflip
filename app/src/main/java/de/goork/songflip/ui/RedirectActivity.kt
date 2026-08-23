package de.goork.songflip.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Browser
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import de.goork.songflip.R
import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.NetworkUtils
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

    companion object {
        const val EXTRA_FORWARDED_FROM_SONGFLIP = "de.goork.songflip.FORWARDED_FROM_SONGFLIP"
    }

    private val odesliRepository = OdesliRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0. Loop Break: If this intent was already forwarded by SongFlip, never re-intercept
        if (intent?.getBooleanExtra(EXTRA_FORWARDED_FROM_SONGFLIP, false) == true) {
            finish()
            suppressTransitionAnimation()
            return
        }

        try {
            LinkCacheManager.init(this)
            val settingsRepository = SettingsRepository(this)

            // Extract raw input text from EXTRA_TEXT, ClipData, or Data URI
            val rawInput = when {
                Intent.ACTION_SEND == intent?.action -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                        ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        ?: intent.dataString
                        ?: intent.data?.toString()
                        ?: ""
                }
                else -> intent?.dataString ?: intent?.data?.toString() ?: ""
            }

            val incomingUrl = odesliRepository.extractCleanUrl(rawInput) ?: rawInput
            if (incomingUrl.isBlank() || (!incomingUrl.startsWith("http://") && !incomingUrl.startsWith("https://"))) {
                finish()
                suppressTransitionAnimation()
                return
            }

            val incomingUri = Uri.parse(incomingUrl)

            // 1. Check if SongFlip is currently paused
            if (PauseHelper.isCurrentlyPaused(this)) {
                forwardOriginalUrl(incomingUri)
                finish()
                suppressTransitionAnimation()
                return
            }

            val targetPlatform = settingsRepository.targetPlatform
            val customApiUrl = settingsRepository.customApiUrl
            val customApiToken = settingsRepository.customApiToken

            lifecycleScope.launch {
                try {
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
                        val targetDisplayName = PackageUtils.getPlatformDisplayName(targetPlatform)
                        val feedbackText = when {
                            !result.artist.isNullOrBlank() && !result.title.isNullOrBlank() -> {
                                "🎵 ${result.artist} – ${result.title} ➔ $targetDisplayName"
                            }
                            !result.title.isNullOrBlank() -> {
                                "🎵 ${result.title} ➔ $targetDisplayName"
                            }
                            else -> {
                                "🎵 ➔ $targetDisplayName"
                            }
                        }

                        Toast.makeText(
                            applicationContext,
                            feedbackText,
                            Toast.LENGTH_SHORT
                        ).show()

                        openTargetUrl(result.targetUrl, targetPlatform)
                    } else {
                        val errorMsg = if (result is OdesliResult.Error && result.message == "PLAYLIST_NOT_SUPPORTED") {
                            getString(R.string.playlist_not_supported_toast)
                        } else {
                            getString(R.string.redirect_error_toast)
                        }
                        Toast.makeText(
                            applicationContext,
                            errorMsg,
                            Toast.LENGTH_SHORT
                        ).show()
                        forwardOriginalUrl(incomingUri)
                    }
                } catch (t: Throwable) {
                    forwardOriginalUrl(incomingUri)
                } finally {
                    finish()
                    suppressTransitionAnimation()
                }
            }
        } catch (t: Throwable) {
            finish()
            suppressTransitionAnimation()
        }
    }

    /**
     * Opens target music link directly in target player app if installed (explicit package launch),
     * completely eliminating intent disambiguation dialogs and infinite intercept loops.
     * Uses native URI schemas for Spotify, Deezer, Tidal for zero latency.
     */
    private fun openTargetUrl(url: String, targetPlatformKey: String) {
        val targetPackage = PackageUtils.packageMap[targetPlatformKey]
        val isTargetInstalled = PackageUtils.isAppInstalled(this, targetPlatformKey)

        // Stage 1: Explicit target app launch (fastest, zero intent disambiguation)
        if (isTargetInstalled && targetPackage != null) {
            try {
                val targetUri = Uri.parse(PackageUtils.toNativeAppUri(url, targetPlatformKey))

                val appIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    setPackage(targetPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(appIntent)
                return
            } catch (e: Exception) {
                // Proceed to Stage 2
            }
        }

        // Stage 2: Implicit player intent (dispatch to installed player handler excluding SongFlip)
        try {
            val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val handlers = packageManager.queryIntentActivities(genericIntent, 0)
                .map { it.activityInfo.packageName }
                .filter { it != packageName }

            if (handlers.isNotEmpty()) {
                val bestHandler = if (targetPackage != null && handlers.contains(targetPackage)) {
                    targetPackage
                } else {
                    handlers.first()
                }
                val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(bestHandler)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(launchIntent)
                return
            }
        } catch (ignored: Exception) {}

        // Stage 3: Safe web browser fallback
        openInBrowser(Uri.parse(url))
    }

    /**
     * Forwards original music URL to web browser when redirect is disabled, paused, or resolution fails.
     */
    private fun forwardOriginalUrl(uri: Uri) {
        openInBrowser(uri)
    }

    private fun openInBrowser(uri: Uri) {
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
                    putExtra(EXTRA_FORWARDED_FROM_SONGFLIP, true)
                    putExtra(Browser.EXTRA_APPLICATION_ID, browserPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(targetIntent)
                return
            }

            val selectorIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_BROWSER)
            }
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                selector = selectorIntent
                putExtra(EXTRA_FORWARDED_FROM_SONGFLIP, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
        } catch (ignored: Exception) {}
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
