package com.quickdaily.ui.theme

import android.app.Activity
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF535F70),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    background = Color(0xFFFDFBFF),
    surfaceVariant = Color(0xFFDFE2EB),
)

// Floater overlay colors for NoteEditActivity floating window

data class FloaterColors(
    val background: Color = Color(0xEE1B1B2B),
    val onBackground: Color = Color(0xFFEEEEEE),
    val onBackgroundVariant: Color = Color(0xFFAAAAAA),
    val onBackgroundDim: Color = Color(0x66FFFFFF),
    val primary: Color = Color(0xFF6EB8FF),
    val onSurfaceVariant: Color = Color(0xFFCCCCCC),
)

val LocalFloaterColors = staticCompositionLocalOf { FloaterColors() }

// Typography tokens matching app font sizes

val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
)

// Dimension tokens (4dp spacing grid)

data class AppDimensions(
    val spacingXxs: Dp = 2.dp,
    val spacingXs: Dp = 4.dp,
    val spacingSm: Dp = 8.dp,
    val spacingMd: Dp = 12.dp,
    val spacingLg: Dp = 16.dp,
    val spacingXl: Dp = 20.dp,
    val spacingXxl: Dp = 24.dp,
    val spacing3xl: Dp = 44.dp,
    val iconXs: Dp = 14.dp,
    val iconSm: Dp = 18.dp,
    val iconMd: Dp = 20.dp,
    val iconLg: Dp = 22.dp,
    val iconXl: Dp = 36.dp,
    val buttonHeight: Dp = 56.dp,
    val maxContentWidth: Dp = 400.dp,
    val thumbnailHeight: Dp = 70.dp,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 12.dp,
    val radiusLg: Dp = 16.dp,
    val radiusXl: Dp = 20.dp,
    val radiusXxl: Dp = 36.dp,
)

val LocalAppDimensions = staticCompositionLocalOf { AppDimensions() }

@Composable
fun QuickDailyTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        content = {
            CompositionLocalProvider(
                LocalAppDimensions provides AppDimensions(),
                content = content
            )
        }
    )
}
