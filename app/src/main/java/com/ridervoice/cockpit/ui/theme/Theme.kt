package com.ridervoice.cockpit.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ridervoice.cockpit.model.AppThemeMode

// Tactical Carbon & High-Contrast Dark Palette
val CarbonBackground = Color(0xFF0A0C10)
val CarbonSurface = Color(0xFF14171F)
val CarbonSurfaceVariant = Color(0xFF1E222D)
val CarbonOutline = Color(0xFF2D3342)

val NeonCyan = Color(0xFF06B6D4)       // Primary Lead / Nav
val NeonGreen = Color(0xFF10B981)      // Pack / Connected / Active
val NeonAmber = Color(0xFFF59E0B)      // Sweep / Warning / Hazard
val NeonRose = Color(0xFFF43F5E)       // Emergency SOS / Danger

val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

// Daylight High-Contrast Light Palette
val DaylightBackground = Color(0xFFF8FAFC)
val DaylightSurface = Color(0xFFFFFFFF)
val DaylightSurfaceVariant = Color(0xFFE2E8F0)
val DaylightOutline = Color(0xFFCBD5E1)

val DaylightTextPrimary = Color(0xFF0F172A)
val DaylightTextSecondary = Color(0xFF475569)

val DaylightNeonCyan = Color(0xFF0284C7)
val DaylightNeonGreen = Color(0xFF059669)
val DaylightNeonAmber = Color(0xFFD97706)
val DaylightNeonRose = Color(0xFFE11D48)

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CarbonBackground,
    primaryContainer = CarbonSurfaceVariant,
    onPrimaryContainer = NeonCyan,
    secondary = NeonGreen,
    onSecondary = CarbonBackground,
    secondaryContainer = CarbonSurfaceVariant,
    onSecondaryContainer = NeonGreen,
    tertiary = NeonAmber,
    onTertiary = CarbonBackground,
    error = NeonRose,
    onError = Color.White,
    background = CarbonBackground,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CarbonOutline
)

private val LightColorScheme = lightColorScheme(
    primary = DaylightNeonCyan,
    onPrimary = Color.White,
    primaryContainer = DaylightSurfaceVariant,
    onPrimaryContainer = DaylightNeonCyan,
    secondary = DaylightNeonGreen,
    onSecondary = Color.White,
    secondaryContainer = DaylightSurfaceVariant,
    onSecondaryContainer = DaylightNeonGreen,
    tertiary = DaylightNeonAmber,
    onTertiary = Color.White,
    error = DaylightNeonRose,
    onError = Color.White,
    background = DaylightBackground,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightTextSecondary,
    outline = DaylightOutline
)

val MonospaceTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 76.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
)

@Composable
fun RiderVoiceTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = if (themeMode.isDark) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bgColor = if (themeMode.isDark) CarbonBackground else DaylightBackground
            window.statusBarColor = bgColor.toArgb()
            window.navigationBarColor = bgColor.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !themeMode.isDark
            controller.isAppearanceLightNavigationBars = !themeMode.isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MonospaceTypography,
        content = content
    )
}
