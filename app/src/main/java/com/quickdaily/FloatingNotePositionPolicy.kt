package com.quickdaily

import android.content.Context

data class FloatingNotePosition(val x: Int, val y: Int)

object FloatingNotePositionPolicy {
    const val PREF_X = "floating_window_x"
    const val PREF_Y = "floating_window_y"

    fun defaultPosition(screenWidth: Int, screenHeight: Int, windowWidth: Int, windowHeight: Int): FloatingNotePosition =
        FloatingNotePosition(
            x = ((screenWidth - windowWidth) / 2).coerceAtLeast(0),
            y = (screenHeight * 0.25f).toInt().coerceAtLeast(0),
        )

    fun clamp(
        position: FloatingNotePosition,
        screenWidth: Int,
        screenHeight: Int,
        windowWidth: Int,
        windowHeight: Int,
    ): FloatingNotePosition {
        val maxX = (screenWidth - windowWidth).coerceAtLeast(0)
        val maxY = (screenHeight - windowHeight).coerceAtLeast(0)
        return FloatingNotePosition(
            position.x.coerceIn(0, maxX),
            position.y.coerceIn(0, maxY),
        )
    }

    fun load(context: Context, fallback: FloatingNotePosition): FloatingNotePosition {
        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        return if (prefs.contains(PREF_X) && prefs.contains(PREF_Y)) {
            FloatingNotePosition(prefs.getInt(PREF_X, fallback.x), prefs.getInt(PREF_Y, fallback.y))
        } else fallback
    }

    fun save(context: Context, position: FloatingNotePosition) {
        context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE).edit()
            .putInt(PREF_X, position.x)
            .putInt(PREF_Y, position.y)
            .apply()
    }
}
