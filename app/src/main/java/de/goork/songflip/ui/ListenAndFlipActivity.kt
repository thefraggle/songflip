package de.goork.songflip.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.goork.songflip.core.cache.AndroidSharedPreferencesCacheStorage
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.ui.components.ListenAndFlipBottomSheet
import de.goork.songflip.ui.components.ProPaywallBottomSheet
import de.goork.songflip.ui.theme.SongFlipTheme

/**
 * Transparent overlay activity that displays the Listen & Flip bottom sheet directly
 * on top of the current app or home screen when triggered from the Quick Settings Tile
 * or App Icon Long-Press Shortcut.
 */
class ListenAndFlipActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidSharedPreferencesCacheStorage.init(this)

        val settingsRepo = SettingsRepository(this)
        val savedLang = settingsRepo.appLanguage
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty || currentLocales[0]?.language != savedLang) {
            val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(savedLang)
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
        }

        setContent {
            val darkTheme = when (settingsRepo.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val proState by ProManager.proState.collectAsState()
            var showPaywall by remember { mutableStateOf(false) }

            SongFlipTheme(darkTheme = darkTheme) {
                if (showPaywall) {
                    ProPaywallBottomSheet(
                        onDismissRequest = {
                            showPaywall = false
                            finishQuietly()
                        }
                    )
                } else {
                    ListenAndFlipBottomSheet(
                        targetPlatformKey = settingsRepo.targetPlatform,
                        isPro = proState.isPro,
                        onDismissRequest = {
                            finishQuietly()
                        },
                        onOpenProPaywall = {
                            de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("listen_quick_tile")
                            showPaywall = true
                        }
                    )
                }
            }
        }
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
