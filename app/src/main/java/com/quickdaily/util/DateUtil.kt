package com.quickdaily.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateUtil {
    /** 将 Obsidian (Moment.js) 日期格式转为 Java DateTimeFormatter 格式 */
    fun convertObsidianFormat(format: String): String {
        return format
            .replace("YYYY", "yyyy")   // week-based year → calendar year
            .replace("YY", "yy")
            .replace("DD", "dd")        // day-of-month（修复关键差异）
            .replace("dddd", "EEEE")    // full day name
            .replace("ddd", "EEE")      // abbreviated day name
    }

    /** 用给定格式获取今天日期字符串 */
    fun todayStr(format: String): String {
        val javaFormat = convertObsidianFormat(format)
        return try {
            LocalDate.now().format(DateTimeFormatter.ofPattern(javaFormat))
        } catch (_: IllegalArgumentException) {
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
    }

    /** 获取当前时间字符串 (HH:mm) */
    fun nowTimeStr(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }
}
