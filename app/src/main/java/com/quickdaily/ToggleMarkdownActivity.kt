package com.quickdaily

import com.quickdaily.BetaLogger

import android.app.Activity
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean

class ToggleMarkdownActivity : Activity() {
    companion object {
        private val isToggling = AtomicBoolean(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isToggling.compareAndSet(false, true)) {
            finish()
            return
        }
        try {
            val prefs = getSharedPreferences("QuickDaily", 0)
            val current = prefs.getBoolean("render_markdown", true)
            BetaLogger.log("ToggleMD", "current=" + current + " new=" + (!current))
            prefs.edit().putBoolean("render_markdown", !current).commit()

            try { QuickDailyReadWidget.refreshAllWidgets(this) } catch (_: Exception) { }
            try { TaskWidget.refreshAllWidgets(this) } catch (_: Exception) { }

            finish()
        } finally {
            isToggling.set(false)
        }
    }
}
