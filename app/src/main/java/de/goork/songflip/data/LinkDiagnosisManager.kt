package de.goork.songflip.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

enum class PlatformLinkState {
    /** SongFlip captures this domain directly */
    CAPTURED_BY_SONGFLIP,

    /** Captured by the official app, which IS the user's selected target player (optimal, no redirect needed) */
    CAPTURED_BY_TARGET_APP,

    /** Captured exclusively by third-party official app, blocking SongFlip from intercepting clicks */
    BLOCKED_BY_THIRD_PARTY,

    /** Official third-party app is not installed on this device */
    NOT_INSTALLED,

    /** Link opens in browser or system resolver, neither SongFlip nor third-party app is default */
    UNCONFIGURED
}

data class PlatformDiagnosis(
    val platformKey: String,
    val displayName: String,
    val packageName: String,
    val sampleUrl: String,
    val isAppInstalled: Boolean,
    val state: PlatformLinkState,
    val handlerPackage: String?
)

data class DiagnosisSummary(
    val platforms: List<PlatformDiagnosis>,
    val blockedCount: Int,
    val capturedBySongFlipCount: Int,
    val targetAppKey: String
)

object LinkDiagnosisManager {

    data class PlatformConfig(
        val key: String,
        val displayName: String,
        val packageName: String,
        val sampleUrl: String
    )

    val supportedPlatforms = listOf(
        PlatformConfig(
            key = "spotify",
            displayName = "Spotify",
            packageName = "com.spotify.music",
            sampleUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"
        ),
        PlatformConfig(
            key = "youtubeMusic",
            displayName = "YouTube Music",
            packageName = "com.google.android.apps.youtube.music",
            sampleUrl = "https://music.youtube.com/watch?v=dQw4w9WgXcQ"
        ),
        PlatformConfig(
            key = "appleMusic",
            displayName = "Apple Music",
            packageName = "com.apple.android.music",
            sampleUrl = "https://music.apple.com/us/album/song/12345"
        ),
        PlatformConfig(
            key = "deezer",
            displayName = "Deezer",
            packageName = "deezer.android.app",
            sampleUrl = "https://www.deezer.com/track/3506388961"
        ),
        PlatformConfig(
            key = "tidal",
            displayName = "Tidal",
            packageName = "com.aspiro.tidal",
            sampleUrl = "https://tidal.com/browse/track/1234567"
        ),
        PlatformConfig(
            key = "amazonMusic",
            displayName = "Amazon Music",
            packageName = "com.amazon.mp3",
            sampleUrl = "https://music.amazon.com/albums/B07XYZ"
        )
    )

    fun runDiagnosis(context: Context, targetPlatformKey: String): DiagnosisSummary {
        val pm = context.packageManager
        val myPackage = context.packageName

        val results = supportedPlatforms.map { config ->
            val isInstalled = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(config.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(config.packageName, 0)
                }
                true
            } catch (e: Exception) {
                false
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.sampleUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }

            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            val handlerPackage = resolveInfo?.activityInfo?.packageName
            val isTargetApp = config.key == targetPlatformKey

            val state = when {
                handlerPackage == myPackage -> PlatformLinkState.CAPTURED_BY_SONGFLIP
                isTargetApp && (handlerPackage == config.packageName || (config.key == "youtubeMusic" && handlerPackage == "com.google.android.youtube")) -> PlatformLinkState.CAPTURED_BY_TARGET_APP
                handlerPackage == config.packageName || (config.key == "youtubeMusic" && handlerPackage == "com.google.android.youtube") -> PlatformLinkState.BLOCKED_BY_THIRD_PARTY
                !isInstalled -> PlatformLinkState.NOT_INSTALLED
                else -> PlatformLinkState.UNCONFIGURED
            }

            PlatformDiagnosis(
                platformKey = config.key,
                displayName = config.displayName,
                packageName = config.packageName,
                sampleUrl = config.sampleUrl,
                isAppInstalled = isInstalled,
                state = state,
                handlerPackage = handlerPackage
            )
        }

        val blocked = results.count { it.state == PlatformLinkState.BLOCKED_BY_THIRD_PARTY }
        val captured = results.count { it.state == PlatformLinkState.CAPTURED_BY_SONGFLIP }

        return DiagnosisSummary(
            platforms = results,
            blockedCount = blocked,
            capturedBySongFlipCount = captured,
            targetAppKey = targetPlatformKey
        )
    }
}
