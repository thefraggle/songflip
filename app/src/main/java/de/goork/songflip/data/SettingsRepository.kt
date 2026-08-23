package de.goork.songflip.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var targetPlatform: String
        get() = prefs.getString(KEY_TARGET_PLATFORM, DEFAULT_TARGET) ?: DEFAULT_TARGET
        set(value) {
            prefs.edit().putString(KEY_TARGET_PLATFORM, value).apply()
        }

    var appLanguage: String
        get() {
            val saved = prefs.getString(KEY_APP_LANGUAGE, null)
            if (!saved.isNullOrBlank()) return saved
            val currentAppLocale = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
            if (!currentAppLocale.isEmpty) {
                val appLang = currentAppLocale[0]?.language
                if (!appLang.isNullOrBlank() && SUPPORTED_LANGUAGE_CODES.contains(appLang)) return appLang
            }
            val sysLang = java.util.Locale.getDefault().language
            return if (SUPPORTED_LANGUAGE_CODES.contains(sysLang)) sysLang else DEFAULT_LANGUAGE
        }
        set(value) {
            prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()
        }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value).apply()
        }

    var customApiUrl: String
        get() = prefs.getString(KEY_CUSTOM_API_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_API_URL, value).apply()
        }

    var customApiToken: String
        get() = prefs.getString(KEY_CUSTOM_API_TOKEN, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_API_TOKEN, value).apply()
        }

    companion object {
        const val PREFS_NAME = "songflip_settings"
        private const val KEY_TARGET_PLATFORM = "target_platform"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_CUSTOM_API_URL = "custom_api_url"
        private const val KEY_CUSTOM_API_TOKEN = "custom_api_token"

        const val DEFAULT_TARGET = "youtubeMusic"
        const val DEFAULT_LANGUAGE = "en"
        const val DEFAULT_THEME_MODE = "system"

        val SUPPORTED_LANGUAGE_CODES = setOf(
            "de", "en", "da", "nb", "sv", "nl", "fr", "es", "it", "pt", "pl",
            "ru", "tr", "uk", "ja", "ko", "zh", "in", "id", "vi", "bn", "hi", "mr"
        )
    }
}
