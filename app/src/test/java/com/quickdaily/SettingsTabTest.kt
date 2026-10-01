package com.quickdaily

import com.quickdaily.ui.SettingsTab
import com.quickdaily.ui.theme.QuickDailyNightMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTabTest {
    @Test
    fun restoredNavigatorScreenKeepsTheCurrentMainSurface() {
        assertEquals(
            Screen.SETTINGS,
            Navigator(showOnboarding = false, firstLaunch = false, restoredScreen = Screen.SETTINGS).screen,
        )
    }

    @Test
    fun stableSettingsOrderAndTitles() {
        assertEquals(
            listOf(
                R.string.qd_tab_quick_capture,
                R.string.qd_tab_widgets,
                R.string.qd_tab_appearance,
                R.string.qd_tab_other,
            ),
            SettingsTab.entries.map(SettingsTab::titleRes),
        )
    }

    @Test
    fun nightModeDropdownKeepsTheThreeUserFacingChoices() {
        assertEquals(
            listOf("\u5f00\u542f", "\u5173\u95ed", "\u8ddf\u968f\u7cfb\u7edf"),
            listOf(
                QuickDailyNightMode.DARK,
                QuickDailyNightMode.LIGHT,
                QuickDailyNightMode.SYSTEM,
            ).map(QuickDailyNightMode::label),
        )
    }
}
