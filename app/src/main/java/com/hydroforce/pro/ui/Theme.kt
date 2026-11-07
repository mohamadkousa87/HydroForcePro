package com.hydroforce.pro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

val HydroBlue = Color(0xFF0B6FC2)
val HydroDeep = Color(0xFF0B4F8A)
val HydroSky = Color(0xFF7FD3FF)
val HydroMint = Color(0xFF14B88A)
val HydroWarn = Color(0xFFE8833A)
val HydroDanger = Color(0xFFD64545)
val HydroBg = Color(0xFFF4F9FF)

private val colors = lightColorScheme(
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

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
)

/** Material 3 theme with the whole UI forced to Arabic right-to-left. */
@Composable
fun HydroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
