package com.quickdaily.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryAppendUtilTest {
    @Test
    fun insertsAfterPreviousEntryImagesBeforeNextHeading() {
        val body = """## 今日速记
- 12:00 first
![[first-a.jpg]]
![[first-b.jpg]]

## 明天
- other"""

        val result = DiaryAppendUtil.appendAtAnchorSectionEnd(
            body,
            "## 今日速记",
            listOf("- 12:01 second", "![[second.jpg]]")
        )

        assertEquals(
            """## 今日速记
- 12:00 first
![[first-a.jpg]]
![[first-b.jpg]]
- 12:01 second
![[second.jpg]]

## 明天
- other""",
            result
        )
    }

    @Test
    fun appendsNonListTimestampAndMultipleImagesAtFileEnd() {
        val body = "## 今日速记\n12:00 first\n![[first.jpg]]\n"
        val result = DiaryAppendUtil.appendAtAnchorSectionEnd(
            body,
            "## 今日速记",
            listOf("12:01 second", "![[second-a.jpg]]", "![[second-b.jpg]]")
        )
        assertEquals(
            "## 今日速记\n12:00 first\n![[first.jpg]]\n12:01 second\n![[second-a.jpg]]\n![[second-b.jpg]]\n",
            result
        )
    }

    @Test
    fun appendsWithoutAnchorAndPreservesFrontmatterCallerBoundary() {
        val body = "body line\n![[old.jpg]]\n"
        val result = DiaryAppendUtil.appendAtAnchorSectionEnd(
            body,
            "## missing",
            listOf("new line", "![[new.jpg]]")
        )
        assertEquals("body line\n![[old.jpg]]\nnew line\n![[new.jpg]]\n", result)
    }

    @Test
    fun keepsFrontmatterBeforeTheAnchoredSection() {
        val body = "---\ntags: diary\n---\n## 今日速记\nold line\n\n"
        val result = DiaryAppendUtil.appendAtAnchorSectionEnd(
            body,
            "## 今日速记",
            listOf("new line", "![[new.jpg]]")
        )

        assertEquals(
            "---\ntags: diary\n---\n## 今日速记\nold line\nnew line\n![[new.jpg]]\n\n",
            result
        )
    }

    @Test
    fun handlesEmptyBody() {
        assertEquals(
            "new line\n![[new.jpg]]",
            DiaryAppendUtil.appendAtAnchorSectionEnd("", "## 今日速记", listOf("new line", "![[new.jpg]]"))
        )
    }
}
