package de.goork.songflip.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates an App-specific Baseline Profile covering cold starts of MainActivity,
 * Share/Deep-Link RedirectActivity hotpath, and primary Compose sheet surfaces.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val targetPackage = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "de.goork.songflip"

        rule.collect(
            packageName = targetPackage,
            includeInStartupProfile = true
        ) {
            // 1. Cold start MainActivity
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // Exercise scrollable main layout if present
            device.findObject(By.scrollable(true))?.let { scrollable ->
                scrollable.fling(androidx.test.uiautomator.Direction.DOWN)
                device.waitForIdle()
                scrollable.fling(androidx.test.uiautomator.Direction.UP)
                device.waitForIdle()
            }

            // 2. Cold start RedirectActivity via Deep-Link Intent (Song-Flip hotpath)
            pressHome()
            val redirectIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")).apply {
                setPackage(targetPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndWait(redirectIntent)
            device.wait(Until.hasObject(By.pkg(targetPackage)), 3_000)
            device.waitForIdle()
        }
    }
}
