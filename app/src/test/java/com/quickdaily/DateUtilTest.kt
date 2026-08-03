package com.quickdaily

import com.quickdaily.util.DateUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilTest {

    @Test
    fun dayOfMonthTokenRemainsTwoDigitAndWeekdayTokensRemainMapped() {
        assertEquals("yyyy-MM-dd(EEEEE)", DateUtil.convertObsidianFormat("YYYY-MM-DD(dd)"))
        assertEquals("EEEEE", DateUtil.convertObsidianFormat("dd"))
        assertEquals("EEE", DateUtil.convertObsidianFormat("ddd"))
        assertEquals("EEEE", DateUtil.convertObsidianFormat("dddd"))
    }
}
