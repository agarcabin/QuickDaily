package com.quickdaily.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateUtil {
    /**
     * 将 Obsidian (Moment.js) 日期格式转为 Java DateTimeFormatter 格式。
     *
     * 修复点：原实现按 YYYY→YY→DD→dddd→ddd 顺序替换，
     * 对 `DDDD` 输入靠巧合正确（DD→dd 后变 dddd 再→EEEE），但脆弱。
     * 改为按 token 长度降序替换，保证长 token 优先匹配，避免子串冲突。
     */
    fun convertObsidianFormat(format: String): String {
        // 顺序很重要：长 token 必须先于其前缀 token 替换
        return format
            .replace("dddd", "EEEE")   // full day name
            .replace("ddd", "EEE")     // abbreviated day name
            .replace("YYYY", "yyyy")   // week-based year → calendar year
            .replace("DD", "dd")       // day-of-month（修复关键差异）
            .replace("YY", "yy")
    }

    /** 用给定格式获取今天日期字符串 */
    fun todayStr(format: String): String {
        val javaFormat = convertObsidianFormat(format)
        return try {
            LocalDate.now().format(DateTimeFormatter.ofPattern(javaFormat))
        } catch (_: IllegalArgumentException) {
            // 格式非法时回退到 ISO 标准格式
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
    }

    /** 获取当前时间字符串 (HH:mm) */
   fun nowTimeStr(): String {
       return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
   }

    /** 获取当前时间字符串 (HH:mm:ss) */
    fun nowTimeSecondsStr(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }
}
