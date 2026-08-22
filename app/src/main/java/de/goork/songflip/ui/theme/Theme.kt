package de.goork.songflip.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.ui.graphics.toArgb

private val DarkColorScheme = darkColorScheme(
    primary = StateActiveGreen,
    onPrimary = Color(0xFF022C22),
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
    primary = Color(0xFF059669), // Kräftiges Smaragdgrün
    onPrimary = Color.White,     // Strahlend weißer Text auf Buttons
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = Color(0xFFF1F5F9), // Sanfter Slate-Hintergrund
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    error = StateErrorRedLight,
    onError = Color.White
)

@Composable
fun SongFlipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.decorView.setBackgroundColor(colorScheme.background.toArgb())

                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
