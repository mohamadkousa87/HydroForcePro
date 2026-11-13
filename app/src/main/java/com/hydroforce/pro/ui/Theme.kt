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

// ===== VeroSip — Bio-Tech Aqua palette =====
// Brand accent constants (used directly across the UI for headers/buttons/accents).
val HydroBlue = Color(0xFF0097B2)   // deep electric cyan (accent / buttons, readable on white & dark)
val HydroDeep = Color(0xFF0A1118)   // Deep Carbon Navy (dark surfaces / header base)
val HydroSky = Color(0xFF00E5FF)    // Electric Bio-Cyan (bright accents on dark)
val HydroMint = Color(0xFF00E676)   // Verified Bio-Green (success / verification)
val HydroWarn = Color(0xFFE8833A)
val HydroDanger = Color(0xFFD64545)
val HydroBg = Color(0xFFF4F9FB)

// Dark palette base
val HydroDarkBg = Color(0xFF0A1118)       // Deep Carbon Navy
val HydroDarkSurface = Color(0xFF131E29)

private val lightColors = lightColorScheme(
    primary = Color(0xFF007C91),              // VeroSip light primary
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCEAF2),
    onPrimaryContainer = Color(0xFF00363F),
    secondary = Color(0xFF00A152),           // VeroSip light success/secondary
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6F3D8),
    onSecondaryContainer = Color(0xFF00391C),
    tertiary = HydroWarn,
    error = HydroDanger,
    background = HydroBg,                     // #F4F9FB
    onBackground = Color(0xFF0F2236),
    surface = Color.White,
    onSurface = Color(0xFF0F2236),
    surfaceVariant = Color(0xFFE2EEF2),
    onSurfaceVariant = Color(0xFF45606A),
    outline = Color(0xFFAEC6CE)
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF00E5FF),             // Electric Bio-Cyan
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0E3A45),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = Color(0xFF00E676),           // Verified Bio-Green
    onSecondary = Color(0xFF00391C),
    secondaryContainer = Color(0xFF0E3B2A),
    onSecondaryContainer = Color(0xFFBFF8D6),
    tertiary = Color(0xFF00E676),
    error = Color(0xFFFF8A80),
    background = HydroDarkBg,                 // #0A1118
    onBackground = Color(0xFFE6EEF6),
    surface = HydroDarkBg,                    // #0A1118 (spec: background/surface same)
    onSurface = Color(0xFFE6EEF6),
    surfaceVariant = HydroDarkSurface,       // #131E29
    onSurfaceVariant = Color(0xFFAFC2D4),
    outline = Color(0xFF32424F)
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
