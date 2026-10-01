package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorDataTest {
    @Test
    fun defaultSponsorsAreRankedAndKeepFixedMessages() {
        val ranked = rankSponsorEntries(defaultSponsorEntries)
        assertEquals(
            listOf("sponsor-pokewins", "sponsor-o", "sponsor-wei"),
            ranked.map { it.id },
        )
        assertEquals(100, ranked.first().amount)
        assertEquals(0, ranked[1].amount)
        assertEquals("暂无留言", ranked[1].message)
        assertTrue(ranked[2].message.contains("QuickDaily和讯飞输入法很好用"))
    }

    @Test
    fun readStateKeysAreScopedBySponsorId() {
        assertEquals(
            "sponsor_message_read_sponsor-o",
            SponsorReadState.readKey("sponsor-o"),
        )
        assertEquals(
            "sponsor_message_read_sponsor-wei",
            SponsorReadState.readKey("sponsor-wei"),
        )
    }
}
