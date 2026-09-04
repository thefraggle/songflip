package de.goork.songflip

import android.app.ActivityManager
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import de.goork.songflip.core.analytics.AptabaseClient
import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.ProManager
import java.util.Locale

class SongFlipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LinkCacheManager.init(this)
        ProManager.init(this)
        de.goork.songflip.data.ShortcutHelper.updateShortcuts(this)

        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isTestLab = try {
            Settings.System.getString(contentResolver, "firebase.test.lab") == "true"
        } catch (_: Exception) {
            false
        }
        val isTestHarness = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && try {
            ActivityManager.isRunningInUserTestHarness()
        } catch (_: Exception) {
            false
        }
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk_google") ||
                Build.PRODUCT.contains("google_sdk") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("sdk_x86") ||
                Build.PRODUCT.contains("vbox86p") ||
                Build.PRODUCT.contains("emulator") ||
                Build.PRODUCT.contains("simulator")

        val isDebug = isDebuggable || isTestLab || isTestHarness || isEmulator

        val pInfo = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Exception) { null }
        val versionName = pInfo?.versionName ?: "1.2.11"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo?.longVersionCode?.toString() ?: "10211"
        } else {
            @Suppress("DEPRECATION")
            pInfo?.versionCode?.toString() ?: "10211"
        }

        if (BuildConfig.APTABASE_APP_KEY.isNotBlank() && BuildConfig.APTABASE_HOST.isNotBlank()) {
            AptabaseClient.shared.init(
                appKey = BuildConfig.APTABASE_APP_KEY,
                host = BuildConfig.APTABASE_HOST,
                osName = "Android",
                osVersion = Build.VERSION.RELEASE ?: "",
                locale = Locale.getDefault().toLanguageTag(),
                appVersion = versionName,
                appBuildNumber = versionCode,
                isDebug = isDebug
            )
        }
    }
}
