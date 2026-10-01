package com.quickdaily.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun tailOffsetUsesPopupLeftAsItsCoordinateOrigin() {
        assertEquals(
            114f,
            sponsorBubbleTailOffset(
                avatarLeft = 260,
                avatarRight = 316,
                bubbleLeft = 174,
                bubbleWidthPx = 236,
                tailWidthPx = 20f,
            ),
            0.01f,
        )
    }

    @Test
    fun tailOffsetStaysInsideBubbleWhenTheAvatarIsOutsideTheClampedPopup() {
        assertEquals(
            10f,
            sponsorBubbleTailOffset(
                avatarLeft = 0,
                avatarRight = 20,
                bubbleLeft = 24,
                bubbleWidthPx = 236,
                tailWidthPx = 20f,
            ),
            0.01f,
        )
    }
}
