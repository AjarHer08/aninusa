package id.aninusa.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Violet = Color(0xFF8B5CF6)
val VioletDeep = Color(0xFF6D28D9)
val Cyan = Color(0xFF22D3EE)
val Amber = Color(0xFFFFC857)
val Ink = Color(0xFF0A0A12)
val InkSurface = Color(0xFF121220)
val InkCard = Color(0xFF1B1B2B)
val InkLine = Color(0xFF2E2C42)
val TextHi = Color(0xFFF4F2FB)
val TextLo = Color(0xFFA8A4C0)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B1D4F),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Cyan,
    onSecondary = Color(0xFF00201F),
    background = Ink,
    onBackground = TextHi,
    surface = InkSurface,
    onSurface = TextHi,
    surfaceVariant = InkCard,
    onSurfaceVariant = TextLo,
    outline = InkLine,
    error = Color(0xFFFF6B81)
)

private val LightColors = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF23124A),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    background = Color(0xFFF6F5FC),
    onBackground = Color(0xFF15131F),
    surface = Color.White,
    onSurface = Color(0xFF15131F),
    surfaceVariant = Color(0xFFECE9F6),
    onSurfaceVariant = Color(0xFF5B5773),
    outline = Color(0xFFD8D4E6),
    error = Color(0xFFB3261E)
)

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/** mode: "dark", "light", atau "system". */
@Composable
fun AniNusaTheme(mode: String = "dark", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
