package de.goork.songflip

import android.app.Application
import android.content.pm.ApplicationInfo
import de.goork.songflip.core.analytics.AptabaseClient
import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.ProManager
import java.util.Locale

class SongFlipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LinkCacheManager.init(this)
        ProManager.init(this)

        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val pInfo = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Exception) { null }
        val versionName = pInfo?.versionName ?: "1.2.6"
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pInfo?.longVersionCode?.toString() ?: "10206"
        } else {
            @Suppress("DEPRECATION")
            pInfo?.versionCode?.toString() ?: "10206"
        }

        AptabaseClient.shared.init(
            appKey = "A-SH-4092372492",
            host = "https://telemetry-apps.goork.de",
            osName = "Android",
            osVersion = android.os.Build.VERSION.RELEASE ?: "",
            locale = Locale.getDefault().toLanguageTag(),
            appVersion = versionName,
            appBuildNumber = versionCode,
            isDebug = isDebug
        )
    }
}
