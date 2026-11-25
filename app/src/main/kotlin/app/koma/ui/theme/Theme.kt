package app.koma.ui.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Material You reads the wallpaper's colors, which Android exposes from 12 on. */
val dynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KomaTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark =
        when (settings.mode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    val context = LocalContext.current
    val wallpaper =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null
    // enableEdgeToEdge tints the bar icons for the system's mode, not the one picked here.
    val window = LocalActivity.current?.window
    val view = LocalView.current
    if (window != null) {
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalAppTheme provides AppTheme(settings, dark)) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme(settings, dark, wallpaper),
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

private data class AppTheme(val settings: ThemeSettings, val dark: Boolean)

private val LocalAppTheme = staticCompositionLocalOf<AppTheme?> { null }

/** The app's colors drawn from [seed], an ARGB; null while [seed] is, and under Monochrome. */
@Composable
fun seededColors(seed: Int?): ColorScheme? {
    val app = LocalAppTheme.current ?: return null
    return remember(app, seed) { seed?.let { seededColorScheme(app.settings, app.dark, it) } }
}
