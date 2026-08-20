package com.quickdaily

/** Defaults shared by the settings sliders and their reset actions. */
internal object SettingsSliderDefaults {
    const val DEFAULT_WIDGET_BACKGROUND_COLOR: Long = 0xFF202124L
    const val DEFAULT_RED: Int = 32
    const val DEFAULT_GREEN: Int = 33
    const val DEFAULT_BLUE: Int = 36

    fun withRed(color: Long, value: Int): Long =
        (color and 0xFF00FFFFL) or (value.coerceIn(0, 255).toLong() shl 16)

    fun withGreen(color: Long, value: Int): Long =
        (color and 0xFFFF00FFL) or (value.coerceIn(0, 255).toLong() shl 8)

    fun withBlue(color: Long, value: Int): Long =
        (color and 0xFFFFFF00L) or value.coerceIn(0, 255).toLong()

    fun resetRed(color: Long): Long = withRed(color, DEFAULT_RED)

    fun resetGreen(color: Long): Long = withGreen(color, DEFAULT_GREEN)

    fun resetBlue(color: Long): Long = withBlue(color, DEFAULT_BLUE)
}
