package de.goork.songflip.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MusicPlatform(
    val key: String,
    val displayName: String,
    val androidPackage: String,
    val iosScheme: String
) {
    YOUTUBE_MUSIC(
        key = "youtubeMusic",
        displayName = "YouTube Music",
        androidPackage = "com.google.android.apps.youtube.music",
        iosScheme = "youtubemusic://"
    ),
    APPLE_MUSIC(
        key = "appleMusic",
        displayName = "Apple Music",
        androidPackage = "com.apple.android.music",
        iosScheme = "music://"
    ),
    SPOTIFY(
        key = "spotify",
        displayName = "Spotify",
        androidPackage = "com.spotify.music",
        iosScheme = "spotify://"
    ),
    TIDAL(
        key = "tidal",
        displayName = "Tidal",
        androidPackage = "com.aspiro.tidal",
        iosScheme = "tidal://"
    ),
    DEEZER(
        key = "deezer",
        displayName = "Deezer",
        androidPackage = "deezer.android.app",
        iosScheme = "deezer://"
    ),
    AMAZON_MUSIC(
        key = "amazonMusic",
        displayName = "Amazon Music",
        androidPackage = "com.amazon.mp3",
        iosScheme = "amznmp3://"
    );

    companion object {
        fun fromKey(key: String): MusicPlatform {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: YOUTUBE_MUSIC
        }
    }
}
