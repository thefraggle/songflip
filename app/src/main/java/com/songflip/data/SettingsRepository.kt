package com.songflip.data

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
        get() = prefs.getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) {
            prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "songflip_settings"
        private const val KEY_TARGET_PLATFORM = "target_platform"
        private const val KEY_APP_LANGUAGE = "app_language"
        const val DEFAULT_TARGET = "youtubeMusic"
        const val DEFAULT_LANGUAGE = "de"
    }
}
