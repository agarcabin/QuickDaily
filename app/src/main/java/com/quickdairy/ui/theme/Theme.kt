package com.quickdairy.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF1B6EF3),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD6E3FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF535F70),
    surface = androidx.compose.ui.graphics.Color(0xFFFDFBFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1A1C1E),
    background = androidx.compose.ui.graphics.Color(0xFFFDFBFF),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFDFE2EB),
)

@Composable
fun QuickDairyTheme(content: @Composable () -> Unit) {
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
        content = content
    )
}
