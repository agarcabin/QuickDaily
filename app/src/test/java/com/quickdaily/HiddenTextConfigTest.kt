package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenTextConfigTest {
    @Test
    fun encodingNormalizesLineEndingsDropsBlankRulesAndKeepsOrderAndDuplicates() {
        assertEquals(
            "[\"a\\nb\",\"a\\nb\"]",
            HiddenTextConfig.encode(listOf("a\r\nb", " \t", "a\rb")),
        )
    }
}
