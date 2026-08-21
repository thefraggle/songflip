package com.songflip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = StateActiveGreen,
    onPrimary = Color.Black,
    primaryContainer = NightSlate800,
    onPrimaryContainer = NightSlate200,
    secondary = BrandSpotify,
    onSecondary = Color.Black,
    tertiary = StatePausedAmber,
    onTertiary = Color.Black,
    background = NightSlate950,
    onBackground = NightSlate200,
    surface = NightSlate900,
    onSurface = NightSlate200,
    surfaceVariant = NightSlate800,
    onSurfaceVariant = NightSlate400,
    outline = NightSlate700,
    error = StateErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = NightSlate050,
    onBackground = NightSlate950,
    surface = Color.White,
    onSurface = NightSlate950,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = StateErrorRed,
    onError = Color.White
)

@Composable
fun SongFlipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
