package com.quickdaily

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** Chooses a high-contrast insertion cursor for the floating editor surface. */
internal object FloatingCursorPolicy {
    const val DARK_BACKGROUND_THRESHOLD = 0.5f

    fun colorFor(background: Color): Color =
        if (perceivedLuminance(background) < DARK_BACKGROUND_THRESHOLD) Color.White else Color.Black

    fun perceivedLuminance(color: Color): Float {
        fun linearize(channel: Float): Float =
            if (channel <= 0.04045f) channel / 12.92f
            else ((channel + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

        return 0.2126f * linearize(color.red) +
            0.7152f * linearize(color.green) +
            0.0722f * linearize(color.blue)
    }
}
