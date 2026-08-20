package com.quickdaily.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBubblePolicyTest {
    @Test
    fun bubbleAboveAvatarUsesBottomTail() {
        assertFalse(
            SponsorBubbleTailPolicy.tailOnTop(
                avatarTop = 920,
                avatarBottom = 976,
                bubbleTop = 760,
                bubbleBottom = 900,
            ),
        )
    }

    @Test
    fun bubbleBelowAvatarUsesTopTail() {
        assertTrue(
            SponsorBubbleTailPolicy.tailOnTop(
                avatarTop = 920,
                avatarBottom = 976,
                bubbleTop = 984,
                bubbleBottom = 1124,
            ),
        )
    }
}
