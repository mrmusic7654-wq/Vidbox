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
    primary = Color(0xFF006B63), onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F4EB), onPrimaryContainer = Color(0xFF003D37),
    secondary = Color(0xFF46626C), secondaryContainer = Color(0xFFE0EBEF),
    background = Color(0xFFF6F8FA), onBackground = Color(0xFF15282C),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF15282C),
    surfaceVariant = Color(0xFFECF1F3), onSurfaceVariant = Color(0xFF56656A),
    outline = Color(0xFF829197), outlineVariant = Color(0xFFDDE5E8),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF6ADBC5), onPrimary = Color(0xFF003D35),
    primaryContainer = Color(0xFF154D46), onPrimaryContainer = Color(0xFFB2F5E3),
    secondary = Color(0xFFA8CAD5), secondaryContainer = Color(0xFF263E47),
    background = Color(0xFF0F191E), onBackground = Color(0xFFE4EDF0),
    surface = Color(0xFF17252B), onSurface = Color(0xFFE4EDF0),
    surfaceVariant = Color(0xFF213239), onSurfaceVariant = Color(0xFFB2C3CA),
    outline = Color(0xFF82989F), outlineVariant = Color(0xFF30434B),
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
fun VidboxTheme(theme: AppTheme, dynamicColors: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (theme) { AppTheme.SYSTEM -> isSystemInDarkTheme(); AppTheme.DARK -> true; AppTheme.LIGHT -> false }
    val context = LocalContext.current
    // Material You palettes on Android 12+ unless the user prefers Vidbox's own identity.
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
