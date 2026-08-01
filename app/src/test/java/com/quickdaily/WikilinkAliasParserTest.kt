package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class WikilinkAliasParserTest {
    @Test
    fun parsesInlineBlockAndSingleValueAliases() {
        val result = WikilinkAliasParser.parse(
            """
            title: Demo
            aliases: ["First name", Second]
            alias: Third
            labels:
              - ignored
            more:
            aliases:
              - Fourth
              - "Fifth"
            """.trimIndent(),
        )

        assertEquals(listOf("First name", "Second", "Third", "Fourth", "Fifth"), result)
    }
}
