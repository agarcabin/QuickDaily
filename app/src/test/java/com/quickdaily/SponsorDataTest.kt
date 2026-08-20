package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorDataTest {
    @Test
    fun defaultSponsorsKeepOrderAndFixedMessages() {
        assertEquals(
            listOf("sponsor-o", "sponsor-wei"),
            defaultSponsorEntries.map { it.id },
        )
        assertEquals("暂无留言", defaultSponsorEntries[0].message)
        assertTrue(defaultSponsorEntries[1].message.contains("QuickDaily和讯飞输入法很好用"))
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
