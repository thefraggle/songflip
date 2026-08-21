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

    fun isInputPlatformEnabled(platformKey: String): Boolean {
        return prefs.getBoolean(KEY_INPUT_PREFIX + platformKey, true)
    }

    fun setInputPlatformEnabled(platformKey: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INPUT_PREFIX + platformKey, enabled).apply()
    }

    companion object {
        const val PREFS_NAME = "songflip_settings"
        private const val KEY_TARGET_PLATFORM = "target_platform"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_CUSTOM_API_URL = "custom_api_url"
        private const val KEY_CUSTOM_API_TOKEN = "custom_api_token"
        private const val KEY_INPUT_PREFIX = "input_enabled_"

        const val DEFAULT_TARGET = "youtubeMusic"
        const val DEFAULT_LANGUAGE = "de"
    }
}
