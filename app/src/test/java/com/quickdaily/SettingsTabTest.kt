package com.quickdaily

import com.quickdaily.ui.SettingsTab
import com.quickdaily.ui.theme.QuickDailyNightMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTabTest {
    @Test
    fun stableSettingsOrderAndTitles() {
        assertEquals(
            listOf("速录设置", "小部件", "外观设置", "其他"),
            SettingsTab.entries.map(SettingsTab::title),
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
