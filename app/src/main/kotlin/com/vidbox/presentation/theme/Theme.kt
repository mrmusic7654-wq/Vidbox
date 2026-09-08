package com.vidbox.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.vidbox.domain.model.AppTheme

private val Light = lightColorScheme(
    primary = Color(0xFFF59E0B), onPrimary = Color(0xFF271B00),
    primaryContainer = Color(0xFFFFE9C2), onPrimaryContainer = Color(0xFF2E2200),
    secondary = Color(0xFF6F5B3F), secondaryContainer = Color(0xFFF6E7C8),
    background = Color(0xFFFAF9F7), onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFF1ECE2), onSurfaceVariant = Color(0xFF5D5647),
    outline = Color(0xFF908876), outlineVariant = Color(0xFFE4DDD0),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB94E), onPrimary = Color(0xFF271B00),
    primaryContainer = Color(0xFF51390B), onPrimaryContainer = Color(0xFFFFE0AC),
    secondary = Color(0xFFD9C5A0), secondaryContainer = Color(0xFF3E3320),
    background = Color(0xFF15130E), onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF1D1A14), onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF292418), onSurfaceVariant = Color(0xFFC9C1B1),
    outline = Color(0xFF948D7D), outlineVariant = Color(0xFF3B3527),
)
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 37.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun VidboxTheme(theme: AppTheme, dynamicColors: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (theme) { AppTheme.SYSTEM -> isSystemInDarkTheme(); AppTheme.DARK -> true; AppTheme.LIGHT -> false }
    val context = LocalContext.current
    // Material You palettes on Android 12+ only when the user opts in; Vidbox keeps its amber identity otherwise.
    val colorScheme = if (dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Type, content = content)
}
