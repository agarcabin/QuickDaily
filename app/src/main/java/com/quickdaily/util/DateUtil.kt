package com.quickdaily.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object DateUtil {

    /**
     * Templater 格式解析结果。
     */
    data class TemplaterResult(
        val format: String,
        val dayOffset: Long = 0
    )

    /**
     * 解析 Templater 日期格式字符串，提取格式和日期偏移。
     *
     * 支持格式：
     * - 普通格式: "YYYY-MM-DD"
     * - tp.date.now: "<% tp.date.now("格式") %>" 或 tp.date.now("格式")
     * - tp.date.tomorrow: "<% tp.date.tomorrow("格式") %>"
     * - tp.date.yesterday: "<% tp.date.yesterday("格式") %>"
     * - tp.date.weekday: "<% tp.date.weekday("格式", 1) %>"
     * - 可选带 [[...]] 包裹
     */
    fun parseTemplaterFormat(raw: String): TemplaterResult {
        var s = raw.trim()

        // 剥离 [[...]] 包裹
        if (s.startsWith("[[") && s.endsWith("]]")) {
            s = s.substring(2, s.length - 2).trim()
        }

        // 剥离 <% ... %> 包裹
        if (s.startsWith("<%") && s.endsWith("%>")) {
            s = s.substring(2, s.length - 2).trim()
        }

        // 尝试匹配 tp.date.xxx("format", ...)
        val dateFuncRegex = Regex("""tp\.date\.(\w+)\s*\(\s*"([^"]*)"\s*(?:,\s*(\d+))?\s*\)""")
        val match = dateFuncRegex.find(s)
        if (match != null) {
            val funcName = match.groupValues[1]
            val formatStr = match.groupValues[2]
            val extraArg = match.groupValues[3]

            val offset = when (funcName) {
                "now" -> 0L
                "tomorrow" -> 1L
                "yesterday" -> -1L
                "weekday" -> {
                    val targetDay = extraArg.toIntOrNull()?.let {
                        DayOfWeek.of(((it - 1) % 7) + 1)
                    } ?: DayOfWeek.MONDAY
                    val today = LocalDate.now()
                    val next = today.with(TemporalAdjusters.next(targetDay))
                    java.time.temporal.ChronoUnit.DAYS.between(today, next)
                }
                else -> 0L
            }
            return TemplaterResult(formatStr, offset)
        }

        // 非 Templater 格式，直接返回原字符串
        return TemplaterResult(raw)
    }

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
            .replace("WW", "ww")       // ISO week-of-year → Java week-of-year
            .replace("DD", "dd")       // day-of-month（修复关键差异）
            .replace("YY", "yy")
    }

    /** 用给定格式获取日期字符串（支持 Templater 格式） */
    fun todayStr(format: String): String {
        val parsed = parseTemplaterFormat(format)
        val javaFormat = convertObsidianFormat(parsed.format)
        val baseDate = if (parsed.dayOffset == 0L) {
            LocalDate.now()
        } else {
            LocalDate.now().plusDays(parsed.dayOffset)
        }
        return try {
            baseDate.format(DateTimeFormatter.ofPattern(javaFormat))
        } catch (_: IllegalArgumentException) {
            // 格式非法时回退到 ISO 标准格式
            baseDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
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
