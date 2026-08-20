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
