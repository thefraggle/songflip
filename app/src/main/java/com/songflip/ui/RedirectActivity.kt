package com.songflip.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.songflip.R
import com.songflip.data.OdesliRepository
import com.songflip.data.OdesliResult
import com.songflip.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Invisible Activity that intercepts incoming music links in the background,
 * resolves them via Odesli API to the user's preferred target player, and launches the target link.
 */
class RedirectActivity : ComponentActivity() {

    private val odesliRepository = OdesliRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepository = SettingsRepository(this)
        val incomingUri = intent?.data
        if (incomingUri == null) {
            finish()
            return
        }

        val incomingUrl = incomingUri.toString()
        val host = incomingUri.host ?: ""
        val sourcePlatformKey = detectSourcePlatformKey(host)

        // If the user has disabled link interception for this platform, bypass SongFlip to prevent infinite loops
        if (sourcePlatformKey != null && !settingsRepository.isInputPlatformEnabled(sourcePlatformKey)) {
            openOriginalUrlBypassingSelf(incomingUrl)
            finish()
            return
        }

        val targetPlatform = settingsRepository.targetPlatform

        lifecycleScope.launch {
            val result = odesliRepository.resolveTargetUrl(incomingUrl, targetPlatformKey = targetPlatform)
            when (result) {
                is OdesliResult.Success -> {
                    openUrl(result.targetUrl)
                }
                is OdesliResult.Error -> {
                    Toast.makeText(
                        this@RedirectActivity,
                        getString(R.string.redirect_error_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    openOriginalUrlBypassingSelf(incomingUrl) // Fallback to original URL
                }
            }
            finish()
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
     * Resolves intent handlers for the original URL while excluding SongFlip itself,
     * avoiding infinite redirect loops when a source link type is disabled.
     */
    private fun openOriginalUrlBypassingSelf(url: String) {
        try {
            val uri = Uri.parse(url)
            val viewIntent = Intent(Intent.ACTION_VIEW, uri)
            val resolveInfos = packageManager.queryIntentActivities(
                viewIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            // Find an intent handler that is NOT SongFlip itself
            val otherHandler = resolveInfos.firstOrNull { 
                it.activityInfo.packageName != packageName 
            }

            if (otherHandler != null) {
                val forwardIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setClassName(
                        otherHandler.activityInfo.packageName,
                        otherHandler.activityInfo.name
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(forwardIntent)
            } else {
                // Fallback: Open in default web browser directly
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            openUrl(url)
        }
    }
}
