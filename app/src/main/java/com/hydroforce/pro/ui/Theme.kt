package com.hydroforce.pro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydroforce.pro.data.Language
import com.hydroforce.pro.data.SettingsRepository
import com.hydroforce.pro.data.ThemeMode

val HydroBlue = Color(0xFF0B6FC2)
val HydroDeep = Color(0xFF0B4F8A)
val HydroSky = Color(0xFF7FD3FF)
val HydroMint = Color(0xFF14B88A)
val HydroWarn = Color(0xFFE8833A)
val HydroDanger = Color(0xFFD64545)
val HydroBg = Color(0xFFF4F9FF)

// Dark palette
val HydroDarkBg = Color(0xFF071221)
val HydroDarkSurface = Color(0xFF0E1E30)

private val lightColors = lightColorScheme(
    primary = HydroBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6ECFF),
    onPrimaryContainer = HydroDeep,
    secondary = HydroMint,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F5EA),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = HydroWarn,
    error = HydroDanger,
    background = HydroBg,
    onBackground = Color(0xFF0F2236),
    surface = Color.White,
    onSurface = Color(0xFF0F2236),
    surfaceVariant = Color(0xFFE6F0FA),
    onSurfaceVariant = Color(0xFF475A6E),
    outline = Color(0xFFB6C8DA)
)

private val darkColors = darkColorScheme(
    primary = HydroSky,
    onPrimary = Color(0xFF042033),
    primaryContainer = Color(0xFF13344F),
    onPrimaryContainer = Color(0xFFCDE8FF),
    secondary = HydroMint,
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF0E3B2E),
    onSecondaryContainer = Color(0xFFBFF3E2),
    tertiary = HydroWarn,
    error = Color(0xFFFF8A80),
    background = HydroDarkBg,
    onBackground = Color(0xFFE6EEF6),
    surface = HydroDarkSurface,
    onSurface = Color(0xFFE6EEF6),
    surfaceVariant = Color(0xFF17293C),
    onSurfaceVariant = Color(0xFFAFC2D4),
    outline = Color(0xFF3A4D60)
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
)

/**
 * Material 3 theme forced to Arabic RTL. Honors the stored [ThemeMode] (system/light/dark)
 * so dark mode applies everywhere the theme is used.
 */
@Composable
fun HydroTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val mode by repo.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val language by repo.language.collectAsStateWithLifecycle(initialValue = Language.AR)
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val direction = if (language == Language.EN) LayoutDirection.Ltr else LayoutDirection.Rtl
    MaterialTheme(colorScheme = if (dark) darkColors else lightColors, typography = typography) {
        CompositionLocalProvider(
            LocalLayoutDirection provides direction,
            LocalLanguage provides language
        ) {
            content()
        }
    }
}

/** True when the current Material theme is dark (for custom-colored surfaces). */
val isDarkTheme: Boolean
    @Composable get() = MaterialTheme.colorScheme.background == HydroDarkBg
