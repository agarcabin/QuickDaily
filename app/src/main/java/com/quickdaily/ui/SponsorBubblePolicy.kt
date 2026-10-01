package com.quickdaily.ui

/** Decides which side of a speech bubble should carry its tail. */
internal object SponsorBubbleTailPolicy {
    /**
     * The bubble is normally either fully above or fully below the avatar.
     * The center fallback keeps the direction deterministic during a transient overlap.
     */
    fun tailOnTop(
        avatarTop: Int,
        avatarBottom: Int,
        bubbleTop: Int,
        bubbleBottom: Int,
    ): Boolean {
        if (bubbleBottom <= avatarTop) return false
        if (bubbleTop >= avatarBottom) return true
        val avatarCenter = (avatarTop + avatarBottom) / 2f
        val bubbleCenter = (bubbleTop + bubbleBottom) / 2f
        return bubbleCenter >= avatarCenter
    }
}

/**
 * Converts the selected avatar's window-space center into the popup-local
 * coordinate used by the speech-bubble shape.
 *
 * Keeping this calculation in one coordinate space is important because a
 * Compose Popup may render in a separate platform window.
 */
internal fun sponsorBubbleTailOffset(
    avatarLeft: Int,
    avatarRight: Int,
    bubbleLeft: Int,
    bubbleWidthPx: Int,
    tailWidthPx: Float,
): Float {
    if (bubbleWidthPx <= 0) return 0f
    val halfTail = tailWidthPx / 2f
    val maxTail = (bubbleWidthPx.toFloat() - halfTail).coerceAtLeast(halfTail)
    return ((avatarLeft + avatarRight) / 2f - bubbleLeft)
        .coerceIn(halfTail, maxTail)
}
