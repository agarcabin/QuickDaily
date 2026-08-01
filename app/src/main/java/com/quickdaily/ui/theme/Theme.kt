package com.quickdaily.ui.theme

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF705575),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBD8FA),
    onTertiaryContainer = Color(0xFF28132D),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191B20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191B20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color.Black,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF002F6C),
    primaryContainer = Color(0xFF004493),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253141),
    secondaryContainer = Color(0xFF3B485A),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFDEBCDD),
    onTertiary = Color(0xFF3F2842),
    tertiaryContainer = Color(0xFF573E59),
    onTertiaryContainer = Color(0xFFFBD8FA),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    scrim = Color.Black,
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

val AppTypography = Typography()

@Immutable
data class QuickDailyMotionPolicy(val reducedMotion: Boolean) {
    fun <T> spatialSpec(): FiniteAnimationSpec<T> = if (reducedMotion) {
        snap()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }

    fun <T> effectSpec(): FiniteAnimationSpec<T> = if (reducedMotion) {
        snap()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }
}

val LocalQuickDailyMotion = staticCompositionLocalOf { QuickDailyMotionPolicy(reducedMotion = false) }

@Composable
fun rememberQuickDailyMotionPolicy(): QuickDailyMotionPolicy {
    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService<android.view.accessibility.AccessibilityManager>()
    }
    val reducedMotion = !ValueAnimator.areAnimatorsEnabled() ||
        (accessibilityManager?.isEnabled == true && accessibilityManager.isTouchExplorationEnabled)
    return remember(reducedMotion) { QuickDailyMotionPolicy(reducedMotion) }
}

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
fun QuickDailyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val motionPolicy = rememberQuickDailyMotionPolicy()
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            if (activity != null) {
                val window = activity.window
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = {
            CompositionLocalProvider(
                LocalAppDimensions provides AppDimensions(),
                LocalQuickDailyMotion provides motionPolicy,
                content = content
            )
        }
    )
}
