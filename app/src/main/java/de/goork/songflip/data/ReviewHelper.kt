package de.goork.songflip.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Helper to coordinate Google Play In-App Reviews politely and unobtrusively.
 */
object ReviewHelper {

    private const val MIN_FLIPS_FOR_REVIEW = 5
    private const val MIN_DAYS_AFTER_INSTALL_MS = 3 * 24 * 60 * 60 * 1000L // 3 days
    private const val PROMPT_COOLDOWN_MS = 60 * 24 * 60 * 60 * 1000L // 60 days

    /**
     * Checks activity and timing criteria before requesting Google Play In-App Review.
     */
    fun maybeRequestReview(activity: Activity, settingsRepository: SettingsRepository) {
        val now = System.currentTimeMillis()
        val flips = settingsRepository.successfulFlipCount
        val installTime = settingsRepository.firstInstallTimestamp
        val lastPrompt = settingsRepository.lastReviewPromptTimestamp

        val isInstalledLongEnough = (now - installTime) >= MIN_DAYS_AFTER_INSTALL_MS
        val isCooldownPassed = (now - lastPrompt) >= PROMPT_COOLDOWN_MS
        val hasEnoughFlips = flips >= MIN_FLIPS_FOR_REVIEW

        if (hasEnoughFlips && isInstalledLongEnough && isCooldownPassed) {
            settingsRepository.lastReviewPromptTimestamp = now
            launchReviewFlow(activity)
        }
    }

    /**
     * Directly triggers the In-App Review Flow.
     */
    fun launchReviewFlow(activity: Activity, onComplete: (() -> Unit)? = null) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener {
                        onComplete?.invoke()
                    }
                } else {
                    onComplete?.invoke()
                }
            }
        } catch (_: Throwable) {
            onComplete?.invoke()
        }
    }

    /**
     * Opens Play Store app listing as a direct fallback or manual feedback option.
     */
    fun openPlayStoreListing(context: Context) {
        val packageName = context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
