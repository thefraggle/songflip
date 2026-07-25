package com.songflip.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.songflip.R
import com.songflip.data.OdesliRepository
import com.songflip.data.OdesliResult
import kotlinx.coroutines.launch

class RedirectActivity : ComponentActivity() {

    private val odesliRepository = OdesliRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incomingUri = intent?.data
        if (incomingUri == null) {
            finish()
            return
        }

        val incomingUrl = incomingUri.toString()
        Toast.makeText(this, getString(R.string.redirecting_toast), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = odesliRepository.resolveTargetUrl(incomingUrl, targetPlatformKey = "youtubeMusic")
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
                    openUrl(incomingUrl) // Fallback to original URL
                }
            }
            finish()
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
}
