package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.Course
import java.text.Collator
import java.util.Locale

/**
 * 课程排序规则。
 *
 * 之前课程名用 `compareBy { it }` 排，等于按 Unicode 码点排 —— 中文没有任何顺序感
 * （"英"U+82F1 排在 "高"U+9AD8 后面），看起来就是乱的；分组顺序则直接沿用数据库添加顺序。
 * 这里统一成：
 * - **课程名**：中文按拼音，同名内的数字段按数值比（"体育2" 排在 "体育10" 前）
 * - **时段**：未排时间的沉底 → 周一→周日 → 实际上课时刻 → 节次号 → 周次
 */
object CourseSorting {

    /**
     * Collator 会在compare 间缓存上次结果，非线程安全；用 ThreadLocal 隔离，
     * 且只在真正排序时创建，不拖慢首屏。
     */
    private val collators = ThreadLocal.withInitial {
        Collator.getInstance(Locale.CHINA).apply { strength = Collator.SECONDARY }
    }

    /**
     * 课程名比较：逐段切分「连续数字段/ 非数字段」，
     * 数字段按数值大小比（去前导零后先比长度再比字典序），非数字段交给中文Collator（拼音）。
     */
    fun compareNames(a: String, b: String): Int {
        val ta = tokenize(a)
        val tb = tokenize(b)
        val collator = collators.get()
        for (i in 0 until minOf(ta.size, tb.size)) {
            val (aIsNum, aText) = ta[i]
            val (bIsNum, bText) = tb[i]
            val cmp = when {
                aIsNum && bIsNum -> compareNumeric(aText, bText)
                // 数字段与文本段相遇时仍交给 Collator，规则由locale 决定，不自己发明顺序
                else -> collator.compare(aText, bText)
            }
            if (cmp != 0) return cmp
        }
        return ta.size - tb.size
    }

    /** 已排出时间（有星期 + 有节次或有效自定义时间）才算「已排课」，否则沉到列表末尾。 */
    fun hasScheduledTime(
        dayOfWeek: Int,
        startSection: Int,
        isCustomTime: Boolean,
        customStartTime: String?,
        customEndTime: String?
    ): Boolean {
        if (dayOfWeek !in 1..7) return false
        return startSection > 0 || (isCustomTime && !customStartTime.isNullOrBlank() && !customEndTime.isNullOrBlank())
    }

    fun hasScheduledTime(course: Course): Boolean =
        hasScheduledTime(
            course.dayOfWeek,
            course.startSection,
            course.isCustomTime,
            course.customStartTime,
            course.customEndTime
        )

    /**
     * 实际开始时刻（当天 00:00 起的分钟数），用于同一星期内的先后排序。
     * 自定义时间优先；否则取该节次在当前作息里的开始时刻；
     * 作息表缺该节次时退化为节次序号（保序，不返回 MAX_VALUE 把自定义时间的课挤到后面）。
     * 完全无法判断时返回 [Int.MAX_VALUE]（沉底）。
     */
    fun startMinutesOf(
        startSection: Int,
        isCustomTime: Boolean,
        customStartTime: String?,
        sectionTimes: Map<Int, String>
    ): Int {
        if (isCustomTime) {
            parseHhmm(customStartTime)?.let { return it }
        }
        if (startSection <= 0) return Int.MAX_VALUE
        parseHhmm(sectionTimes[startSection]?.substringBefore("-"))?.let { return it }
        return startSection * 1000
    }

    fun startMinutesOf(course: Course, sectionTimes: Map<Int, String>): Int =
        startMinutesOf(course.startSection, course.isCustomTime, course.customStartTime, sectionTimes)

    /** "HH:mm" → 分钟；无法解析返回 null。 */
    fun parseHhmm(hm: String?): Int? {
        val parts = hm?.trim()?.split(":") ?: return null
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..47 || m !in 0..59) return null
        return h * 60 + m
    }

    /** 切分为 (是否数字段, 原文) 的交替序列。 */
    private fun tokenize(s: String): List<Pair<Boolean, String>> {
        if (s.isEmpty()) return emptyList()
        val result = ArrayList<Pair<Boolean, String>>()
        var i = 0
        while (i < s.length) {
            val isDigit = s[i].isDigit()
            var j = i
            while (j < s.length && s[j].isDigit() == isDigit) j++
            result += isDigit to s.substring(i, j)
            i = j
        }
        return result
    }

    private fun compareNumeric(a: String, b: String): Int {
        val na = a.trimStart('0')
        val nb = b.trimStart('0')
        if (na.length != nb.length) return na.length - nb.length
        return na.compareTo(nb)
    }
}