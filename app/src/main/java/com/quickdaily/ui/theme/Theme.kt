package com.quickdaily.ui.theme

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.quickdaily.BetaLogger

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

private fun ColorScheme.withAccent(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
): ColorScheme = copy(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
)

/** Stable accent presets used when Monet is disabled or unavailable. */
internal enum class QuickDailyAccentPreset(
    val key: String,
    val label: String,
    val previewColor: Color,
    private val lightPrimary: Color,
    private val lightOnPrimary: Color,
    private val lightPrimaryContainer: Color,
    private val lightOnPrimaryContainer: Color,
    private val darkPrimary: Color,
    private val darkOnPrimary: Color,
    private val darkPrimaryContainer: Color,
    private val darkOnPrimaryContainer: Color,
) {
    BLUE(
        key = "blue",
        label = "蓝色",
        previewColor = Color(0xFF1B6EF3),
        lightPrimary = Color(0xFF1B6EF3),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFD6E3FF),
        lightOnPrimaryContainer = Color(0xFF001A41),
        darkPrimary = Color(0xFFA9C7FF),
        darkOnPrimary = Color(0xFF002F6C),
        darkPrimaryContainer = Color(0xFF004493),
        darkOnPrimaryContainer = Color(0xFFD6E3FF),
    ),
    PURPLE(
        key = "purple",
        label = "紫色",
        previewColor = Color(0xFF6750A4),
        lightPrimary = Color(0xFF6750A4),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFEADDFF),
        lightOnPrimaryContainer = Color(0xFF21005D),
        darkPrimary = Color(0xFFD0BCFF),
        darkOnPrimary = Color(0xFF381E72),
        darkPrimaryContainer = Color(0xFF4F378B),
        darkOnPrimaryContainer = Color(0xFFEADDFF),
    ),
    GREEN(
        key = "green",
        label = "绿色",
        previewColor = Color(0xFF006C4C),
        lightPrimary = Color(0xFF006C4C),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFF89F8C7),
        lightOnPrimaryContainer = Color(0xFF002114),
        darkPrimary = Color(0xFF6CDBA9),
        darkOnPrimary = Color(0xFF003824),
        darkPrimaryContainer = Color(0xFF005236),
        darkOnPrimaryContainer = Color(0xFF89F8C7),
    ),
    ORANGE(
        key = "orange",
        label = "橙色",
        previewColor = Color(0xFF8C5000),
        lightPrimary = Color(0xFF8C5000),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFDDAF),
        lightOnPrimaryContainer = Color(0xFF2D1600),
        darkPrimary = Color(0xFFFFB95D),
        darkOnPrimary = Color(0xFF4B2800),
        darkPrimaryContainer = Color(0xFF6A3B00),
        darkOnPrimaryContainer = Color(0xFFFFDDAF),
    ),
    PINK(
        key = "pink",
        label = "粉色",
        previewColor = Color(0xFF9C2D6D),
        lightPrimary = Color(0xFF9C2D6D),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFD9E7),
        lightOnPrimaryContainer = Color(0xFF3E001F),
        darkPrimary = Color(0xFFFFB0CA),
        darkOnPrimary = Color(0xFF61003C),
        darkPrimaryContainer = Color(0xFF7D1A52),
        darkOnPrimaryContainer = Color(0xFFFFD9E7),
    ),
    TEAL(
        key = "teal",
        label = "青色",
        previewColor = Color(0xFF006874),
        lightPrimary = Color(0xFF006874),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFF97F0FF),
        lightOnPrimaryContainer = Color(0xFF001F24),
        darkPrimary = Color(0xFF4FD8EB),
        darkOnPrimary = Color(0xFF00363D),
        darkPrimaryContainer = Color(0xFF004F58),
        darkOnPrimaryContainer = Color(0xFF97F0FF),
    );

    fun colorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) {
        DarkColorScheme.withAccent(
            primary = darkPrimary,
            onPrimary = darkOnPrimary,
            primaryContainer = darkPrimaryContainer,
            onPrimaryContainer = darkOnPrimaryContainer,
        )
    } else {
        LightColorScheme.withAccent(
            primary = lightPrimary,
            onPrimary = lightOnPrimary,
            primaryContainer = lightPrimaryContainer,
            onPrimaryContainer = lightOnPrimaryContainer,
        )
    }

    companion object {
        fun fromKey(key: String?): QuickDailyAccentPreset =
            entries.firstOrNull { it.key == key } ?: BLUE
    }
}

internal object QuickDailyThemePreferences {
    private const val PREFS_NAME = "QuickDaily"
    const val KEY_USE_MONET = "theme_use_monet"
    const val KEY_ACCENT_PRESET = "theme_accent_preset"
    const val KEY_NIGHT_MODE = "theme_night_mode"
    const val KEY_DARK_BACKGROUND_BRIGHTNESS = "theme_dark_background_brightness"
    const val DEFAULT_DARK_BACKGROUND_BRIGHTNESS = 35

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMonetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_MONET, true)

    fun selectedPreset(context: Context): QuickDailyAccentPreset =
        QuickDailyAccentPreset.fromKey(prefs(context).getString(KEY_ACCENT_PRESET, null))

    fun setMonetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_MONET, enabled).apply()
        BetaLogger.log("Theme/Preference", "monet_enabled=$enabled")
    }

    fun selectAccentPreset(context: Context, preset: QuickDailyAccentPreset) {
        prefs(context).edit()
            .putString(KEY_ACCENT_PRESET, preset.key)
            .putBoolean(KEY_USE_MONET, false)
            .apply()
        BetaLogger.log("Theme/Preference", "accent_preset=${preset.key} monet_enabled=false")
    }

    fun nightMode(context: Context): QuickDailyNightMode =
        QuickDailyNightMode.fromKey(prefs(context).getString(KEY_NIGHT_MODE, null))

    fun setNightMode(context: Context, mode: QuickDailyNightMode) {
        prefs(context).edit().putString(KEY_NIGHT_MODE, mode.key).apply()
        BetaLogger.log("Theme/Preference", "night_mode=${mode.key}")
    }

    fun darkBackgroundBrightness(context: Context): Int = prefs(context)
        .getInt(KEY_DARK_BACKGROUND_BRIGHTNESS, DEFAULT_DARK_BACKGROUND_BRIGHTNESS)
        .coerceIn(0, 100)

    fun setDarkBackgroundBrightness(context: Context, brightness: Int) {
        val normalized = brightness.coerceIn(0, 100)
        prefs(context).edit().putInt(KEY_DARK_BACKGROUND_BRIGHTNESS, normalized).apply()
        BetaLogger.log("Theme/Preference", "dark_background_brightness=$normalized")
    }
}

internal enum class QuickDailyNightMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "关闭"),
    DARK("dark", "开启");

    companion object {
        fun fromKey(key: String?): QuickDailyNightMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

private data class QuickDailyThemeSnapshot(
    val useMonet: Boolean,
    val accentPreset: QuickDailyAccentPreset,
    val nightMode: QuickDailyNightMode,
    val darkBackgroundBrightness: Int,
)

@Composable
private fun rememberQuickDailyThemeSnapshot(
    context: Context,
    explicitDynamicColor: Boolean?,
): QuickDailyThemeSnapshot {
    val preferences = remember(context) {
        context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
    }
    var preferencesRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == QuickDailyThemePreferences.KEY_USE_MONET ||
                key == QuickDailyThemePreferences.KEY_ACCENT_PRESET ||
                key == QuickDailyThemePreferences.KEY_NIGHT_MODE ||
                key == QuickDailyThemePreferences.KEY_DARK_BACKGROUND_BRIGHTNESS
            ) {
                preferencesRevision++
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return remember(preferencesRevision, explicitDynamicColor) {
        QuickDailyThemeSnapshot(
            useMonet = explicitDynamicColor ?: preferences.getBoolean(
                QuickDailyThemePreferences.KEY_USE_MONET,
                true,
            ),
            accentPreset = QuickDailyAccentPreset.fromKey(
                preferences.getString(QuickDailyThemePreferences.KEY_ACCENT_PRESET, null)
            ),
            nightMode = QuickDailyNightMode.fromKey(
                preferences.getString(QuickDailyThemePreferences.KEY_NIGHT_MODE, null)
            ),
            darkBackgroundBrightness = preferences.getInt(
                QuickDailyThemePreferences.KEY_DARK_BACKGROUND_BRIGHTNESS,
                QuickDailyThemePreferences.DEFAULT_DARK_BACKGROUND_BRIGHTNESS,
            ).coerceIn(0, 100),
        )
    }
}

private fun Color.adjustDarkBackgroundBrightness(level: Int): Color {
    val normalized = (level.coerceIn(0, 100) - QuickDailyThemePreferences.DEFAULT_DARK_BACKGROUND_BRIGHTNESS) / 65f
    val amount = if (normalized >= 0f) normalized * 0.32f else normalized * 0.65f
    fun adjust(channel: Float): Float = if (amount >= 0f) {
        channel + (1f - channel) * amount
    } else {
        channel * (1f + amount)
    }
    return copy(red = adjust(red), green = adjust(green), blue = adjust(blue))
}

private fun ColorScheme.withDarkBackgroundBrightness(level: Int): ColorScheme = copy(
    background = background.adjustDarkBackgroundBrightness(level),
    surface = surface.adjustDarkBackgroundBrightness(level),
    surfaceVariant = surfaceVariant.adjustDarkBackgroundBrightness(level),
    surfaceContainerLowest = surfaceContainerLowest.adjustDarkBackgroundBrightness(level),
    surfaceContainerLow = surfaceContainerLow.adjustDarkBackgroundBrightness(level),
    surfaceContainer = surfaceContainer.adjustDarkBackgroundBrightness(level),
    surfaceContainerHigh = surfaceContainerHigh.adjustDarkBackgroundBrightness(level),
    surfaceContainerHighest = surfaceContainerHighest.adjustDarkBackgroundBrightness(level),
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

/** Keeps overlay actions aligned with the active app emphasis color. */
@Composable
fun quickDailyFloaterColors(): FloaterColors {
    val colors = MaterialTheme.colorScheme
    return FloaterColors(
        background = colors.surfaceContainerHigh.copy(alpha = 0.96f),
        onBackground = colors.onSurface,
        onBackgroundVariant = colors.onSurfaceVariant,
        onBackgroundDim = colors.onSurfaceVariant.copy(alpha = 0.72f),
        primary = colors.primary,
        onSurfaceVariant = colors.onSurfaceVariant,
    )
}

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
    darkTheme: Boolean? = null,
    dynamicColor: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val colorContext = remember(context) { context.applicationContext }
    val motionPolicy = rememberQuickDailyMotionPolicy()
    val themeSnapshot = rememberQuickDailyThemeSnapshot(context, dynamicColor)
    val resolvedDarkTheme = darkTheme ?: when (themeSnapshot.nightMode) {
        QuickDailyNightMode.SYSTEM -> isSystemInDarkTheme()
        QuickDailyNightMode.LIGHT -> false
        QuickDailyNightMode.DARK -> true
    }
    val baseColorScheme = when {
        themeSnapshot.useMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            runCatching {
                if (resolvedDarkTheme) dynamicDarkColorScheme(colorContext) else dynamicLightColorScheme(colorContext)
            }.getOrElse { error ->
                BetaLogger.logException("Theme/Monet", "dynamic_color_failed context=${colorContext.javaClass.simpleName}", error)
                themeSnapshot.accentPreset.colorScheme(resolvedDarkTheme)
            }
        }
        else -> themeSnapshot.accentPreset.colorScheme(resolvedDarkTheme)
    }
    val colorScheme = if (resolvedDarkTheme) {
        baseColorScheme.withDarkBackgroundBrightness(themeSnapshot.darkBackgroundBrightness)
    } else {
        baseColorScheme
    }
    if (!view.isInEditMode) {
        DisposableEffect(view, resolvedDarkTheme) {
            val activity = view.context as? Activity
            if (activity != null) {
                val window = activity.window
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !resolvedDarkTheme
                    isAppearanceLightNavigationBars = !resolvedDarkTheme
                }
            }
            onDispose { }
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
