package com.quickdaily.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/** Compact/medium/expanded breakpoints shared by all in-app Compose surfaces. */
enum class QuickDailyWindowSize {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Composable
fun rememberQuickDailyWindowSize(): QuickDailyWindowSize {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        when {
            configuration.screenWidthDp >= 840 -> QuickDailyWindowSize.EXPANDED
            configuration.screenWidthDp >= 600 -> QuickDailyWindowSize.MEDIUM
            else -> QuickDailyWindowSize.COMPACT
        }
    }
}

val QuickDailyWindowSize.isLarge: Boolean
    get() = this != QuickDailyWindowSize.COMPACT
