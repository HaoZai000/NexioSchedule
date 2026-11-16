package com.haooz.chedule.data.model

import com.haooz.chedule.data.platform.DisplayLang
import com.haooz.chedule.data.util.CourseTimeSpan
import kotlin.time.Clock

data class Course(
    val id: String,
    val name: String,
    val classroom: String,
    val teacher: String,
    val dayOfWeek: Int,         // 1=周一, 7=周日
    val startSection: Int,      // 1-12
    val endSection: Int,        // 1-12
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int,          // 0=全周, 1=单周, 2=双周
    val colorRes: Long,
    // 为空时用 startWeek/endWeek/weekType 推导有效周次
    val selectedWeeks: List<Int> = emptyList(),
    // 空串表示未指定，云同步靠它区分课表
    val scheduleId: String = "",
    // System.currentTimeMillis() 是 JVM 专有（Kotlin/Native 没有），改用 kotlinx-datetime；值语义相同。
    val lastModified: Long = Clock.System.now().toEpochMilliseconds(),
    // 开启后用 customStartTime/EndTime，否则回退节次时间表
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null, // "HH:mm"
    val customEndTime: String? = null,   // "HH:mm"
    // 课程备注（今日页展示、详情弹窗里添加）。note 对这条课的所有周生效，
    // weekNotes 按周覆盖 note：key = 周次。空串 / 空 Map 表示没有备注。
    // 两者都受 NOTE_MAX_LENGTH 约束，超长在入口（输入框 / 导入解析 / 读盘清洗）就截断，
    // 不要只在某一侧截 —— 否则「本机完整、分享后变短」没人看得出来。
    val note: String = "",
    val weekNotes: Map<Int, String> = emptyMap()
) {
    companion object {
        /** 单条备注字符上限。客户端输入框、导入解析、服务端 `SHARE_MAX_NOTE_LEN` 三处必须一致。 */
        const val NOTE_MAX_LENGTH = 50

        const val WEEK_TYPE_ALL = 0
        const val WEEK_TYPE_ODD = 1
        const val WEEK_TYPE_EVEN = 2

        const val PERIOD_MORNING = 0
        const val PERIOD_AFTERNOON = 1
        const val PERIOD_EVENING = 2

        val courseColors = listOf(
            0xFF4CAF50L,
            0xFF2196F3L,
            0xFFFF9800L,
            0xFFF44336L,
            0xFFE6B422L,
            0xFFE91E63L,
            0xFF00BCD4L,
            0xFF3F51B5L,
            0xFFAB47BCL,
            0xFF009688L,
            0xFF673AB7L
        )

        val defaultMorningTimes = mapOf(
            1 to "08:00-08:45",
            2 to "08:55-09:40",
            3 to "10:00-10:45",
            4 to "10:55-11:40",
            5 to "",
            6 to ""
        )
        val defaultAfternoonTimes = mapOf(
            1 to "14:00-14:45",
            2 to "14:55-15:40",
            3 to "16:00-16:45",
            4 to "16:55-17:40",
            5 to "",
            6 to ""
        )
        val defaultEveningTimes = mapOf(
            1 to "18:30-19:15",
            2 to "19:25-20:10",
            3 to "20:30-21:15",
            4 to "21:25-22:10",
            5 to "",
            6 to ""
        )

        val defaultSectionTimes: Map<Int, String>
            get() {
                val map = mutableMapOf<Int, String>()
                val morningCount = defaultMorningTimes.size
                val afternoonCount = defaultAfternoonTimes.size
                defaultMorningTimes.forEach { (k, v) -> map[k] = v }
                defaultAfternoonTimes.forEach { (k, v) -> map[morningCount + k] = v }
                defaultEveningTimes.forEach { (k, v) -> map[morningCount + afternoonCount + k] = v }
                return map
            }

        /**
         * 从起始时间累加课时与休息，生成各节次时间；在 [longBreakSection] 后插入长休息。
         */
        fun calculatePeriodTimes(
            sectionCount: Int,
            startHour: Int,
            startMinute: Int,
            classDuration: Int,
            shortBreak: Int,
            longBreak: Int,
            longBreakSection: Int = 2
        ): Map<Int, String> {
            if (sectionCount <= 0) return emptyMap()
            val result = mutableMapOf<Int, String>()
            var currentMinute = startHour * 60 + startMinute
            for (i in 1..sectionCount) {
                val sH = currentMinute / 60
                val sM = currentMinute % 60
                val endMinute = currentMinute + classDuration
                val eH = endMinute / 60
                val eM = endMinute % 60
                // String.format 是 JVM 专有，且用**默认 Locale 的数码字形** ——
                // 阿拉伯语等 locale 下 "%02d" 会输出非 ASCII；padStart 与 locale 无关，恒为 ASCII。
                result[i] = "${sH.pad2()}:${sM.pad2()}-${eH.pad2()}:${eM.pad2()}"
                currentMinute = endMinute
                if (i < sectionCount) {
                    currentMinute += if (i == longBreakSection) longBreak else shortBreak
                }
            }
            return result
        }

    }

    fun isActiveInWeek(week: Int): Boolean {
        if (selectedWeeks.isNotEmpty()) {
            return week in selectedWeeks
        }
        if (week < startWeek || week > endWeek) return false
        return when (weekType) {
            WEEK_TYPE_ODD -> week % 2 == 1
            WEEK_TYPE_EVEN -> week % 2 == 0
            else -> true
        }
    }

    /**
     * 该周实际生效的备注：单周备注优先，没有就回退到「所有周」的备注。
     *
     * 只查不写 —— 换周次展示时不该顺手改数据。
     */
    fun noteForWeek(week: Int): String =
        weekNotes[week]?.takeIf { it.isNotBlank() } ?: note
    /** 自定义开关开启且起止时间均非空才生效 */
    fun hasValidCustomTime(): Boolean {
        return isCustomTime &&
            !customStartTime.isNullOrBlank() &&
            !customEndTime.isNullOrBlank()
    }

    /** 自定义时间是否跨午夜（结束不晚于开始，见 [CourseTimeSpan]）。 */
    fun crossesMidnightCustomTime(): Boolean {
        if (!hasValidCustomTime()) return false
        val s = parseHmToMinutes(customStartTime!!) ?: return false
        val e = parseHmToMinutes(customEndTime!!) ?: return false
        return CourseTimeSpan.crossesMidnight(s, e)
    }

    /** 自定义时间的时长（分钟），跨午夜按次日结束算；非自定义时间返回 null。 */
    fun customDurationMinutes(): Int? {
        if (!hasValidCustomTime()) return null
        val s = parseHmToMinutes(customStartTime!!) ?: return null
        val e = parseHmToMinutes(customEndTime!!) ?: return null
        return CourseTimeSpan.minutesUntilEnd(s, e)
    }

    /**
     * @param sectionTimes 全局绝对节次号 -> "HH:mm-HH:mm"
     */
    fun getEffectiveStartTime(sectionTimes: Map<Int, String>): String? {
        if (hasValidCustomTime()) return customStartTime
        return sectionTimes[startSection]?.split("-")?.firstOrNull()?.trim()
    }

    /**
     * @param sectionTimes 全局绝对节次号 -> "HH:mm-HH:mm"
     */
    fun getEffectiveEndTime(sectionTimes: Map<Int, String>): String? {
        if (hasValidCustomTime()) return customEndTime
        return sectionTimes[endSection]?.split("-")?.lastOrNull()?.trim()
    }

    fun getWeekTypeText(): String {
        if (selectedWeeks.isNotEmpty()) {
            return "自定义"
        }
        return when (weekType) {
            WEEK_TYPE_ODD -> "单周"
            WEEK_TYPE_EVEN -> "双周"
            else -> ""
        }
    }

    fun getSectionText(): String {
        if (startSection <= 0 && endSection <= 0) return ""
        return if (startSection == endSection) {
            "第${startSection}节"
        } else {
            "第${startSection}-${endSection}节"
        }
    }

    /** 自定义时间显示 "HH:mm - HH:mm"，跨午夜标注「次日」；否则回退节次文本 */
    fun getTimeDisplayText(): String {
        if (hasValidCustomTime()) {
            val end = if (crossesMidnightCustomTime()) "次日$customEndTime" else "$customEndTime"
            return "$customStartTime - $end"
        }
        return getSectionText()
    }

    /**
     * 课程所属时段：0=上午, 1=下午, 2=晚上。
     * 自定义时间按实际开始时刻归类（墙钟是上午但节次号落在下午段的异构课常见）；
     * 普通课按绝对节次号归类。
     */
    fun periodIndex(
        sectionTimes: Map<Int, String>,
        morningSections: Int,
        afternoonSections: Int
    ): Int {
        if (hasValidCustomTime()) {
            val startMin = parseHmToMinutes(customStartTime!!)
                ?: return sectionPeriodIndex(morningSections, afternoonSections)
            val afternoonStart = sectionTimes[morningSections + 1]
                ?.substringBefore("-")
                ?.trim()
                ?.let { parseHmToMinutes(it) }
                ?: 12 * 60
            val eveningStart = sectionTimes[morningSections + afternoonSections + 1]
                ?.substringBefore("-")
                ?.trim()
                ?.let { parseHmToMinutes(it) }
                ?: 18 * 60 + 30
            return when {
                startMin < afternoonStart -> PERIOD_MORNING
                startMin < eveningStart -> PERIOD_AFTERNOON
                else -> PERIOD_EVENING
            }
        }
        return sectionPeriodIndex(morningSections, afternoonSections)
    }

    private fun sectionPeriodIndex(morningSections: Int, afternoonSections: Int): Int = when {
        startSection <= morningSections -> PERIOD_MORNING
        startSection <= morningSections + afternoonSections -> PERIOD_AFTERNOON
        else -> PERIOD_EVENING
    }

    private fun parseHmToMinutes(hm: String): Int? {
        val parts = hm.split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        return h * 60 + m
    }

    fun getWeekText(): String {
        if (selectedWeeks.isNotEmpty()) {
            return formatWeeks(selectedWeeks.sorted())
        }
        if (startWeek <= 0 && endWeek <= 0) return ""
        val weekTypeStr = getWeekTypeText()
        val weeks = if (startWeek == endWeek) "${startWeek}周" else "${startWeek}-${endWeek}周"
        return if (weekTypeStr.isNotEmpty()) {
            "$weeks ($weekTypeStr)"
        } else {
            weeks
        }
    }

    /**
     * 周次紧凑显示：先按步长2（单双周）再按步长1 合并，同类型序列≥3 才合并。
     * 例：[1,3,5,7,9,11,13,15,16,17] → "1-15 (单周)、16周、17周"
     */
    private fun formatWeeks(sorted: List<Int>): String {
        val groups = mutableListOf<String>()
        var i = 0
        while (i < sorted.size) {
            val step2Run = extractRun(sorted, i, step = 2)
            if (step2Run.size >= 3) {
                val parity = if (step2Run.first() % 2 == 1) "单周" else "双周"
                groups.add("${step2Run.first()}-${step2Run.last()} ($parity)")
                i += step2Run.size
                continue
            }
            val step1Run = extractRun(sorted, i, step = 1)
            if (step1Run.size >= 3) {
                groups.add("${step1Run.first()}-${step1Run.last()}周")
                i += step1Run.size
                continue
            }
            groups.add("${sorted[i]}周")
            i++
        }
        return groups.joinToString("、")
    }

    /** 从 start 起提取步长为 step 的最长连续等差子序列 */
    private fun extractRun(sorted: List<Int>, start: Int, step: Int): List<Int> {
        val run = mutableListOf(sorted[start])
        var j = start + 1
        while (j < sorted.size && sorted[j] - sorted[j - 1] == step) {
            run.add(sorted[j])
            j++
        }
        return run
    }

    // ── 显示用本地化变体（随应用语言取词，见 [DisplayLang]）──
    // 通知去重 ID、Intent extra、回查比较等数据身份一律用上面不带 Localized 的稳定形态（中文），
    // 否则切换语言后新旧值失配。

    /** 显示用节次文本：中文「第3-4节」，英文「P3-4」。 */
    fun sectionTextLocalized(): String {
        if (startSection <= 0 && endSection <= 0) return ""
        if (DisplayLang.isZh) return getSectionText()
        return if (startSection == endSection) "P$startSection" else "P$startSection-$endSection"
    }

    /** 显示用时间文本：自定义时间跨午夜的「次日」在英文侧以 "+1d" 标注；否则同 [sectionTextLocalized]。 */
    fun timeDisplayTextLocalized(): String {
        if (hasValidCustomTime()) {
            val end = when {
                !crossesMidnightCustomTime() -> customEndTime
                DisplayLang.isZh -> "次日$customEndTime"
                else -> "$customEndTime (+1d)"
            }
            return "$customStartTime - $end"
        }
        return sectionTextLocalized()
    }

    /** 显示用周次文本：中文「1-15 (单周)、16周、17周」，英文「1-15 (odd), W16, W17」。 */
    fun weekTextLocalized(): String {
        if (DisplayLang.isZh) return getWeekText()
        if (selectedWeeks.isNotEmpty()) return formatWeeksEn(selectedWeeks.sorted())
        if (startWeek <= 0 && endWeek <= 0) return ""
        val type = when (weekType) {
            WEEK_TYPE_ODD -> "odd"
            WEEK_TYPE_EVEN -> "even"
            else -> ""
        }
        val weeks = if (startWeek == endWeek) "Week $startWeek" else "Weeks $startWeek-$endWeek"
        return if (type.isNotEmpty()) "$weeks ($type)" else weeks
    }

    /** [formatWeeks] 的英文版，语义与合并规则逐条对应。 */
    private fun formatWeeksEn(sorted: List<Int>): String {
        val groups = mutableListOf<String>()
        var i = 0
        while (i < sorted.size) {
            val step2Run = extractRun(sorted, i, step = 2)
            if (step2Run.size >= 3) {
                val parity = if (step2Run.first() % 2 == 1) "odd" else "even"
                groups.add("${step2Run.first()}-${step2Run.last()} ($parity)")
                i += step2Run.size
                continue
            }
            val step1Run = extractRun(sorted, i, step = 1)
            if (step1Run.size >= 3) {
                groups.add("W${step1Run.first()}-W${step1Run.last()}")
                i += step1Run.size
                continue
            }
            groups.add("W${sorted[i]}")
            i++
        }
        return groups.joinToString(", ")
    }
}

/**
 * 两位补零，对应 `String.format("%02d", this)`，但**与 Locale 无关**。
 * 2 是最小宽度而非截断：`-5` → `"-5"`、`100` → `"100"`；本项目取值 0..23 / 0..59，恒两位。
 */
private fun Int.pad2(): String = if (this in 0..9) "0$this" else toString()

// ── 同一槽位里「该显示哪一条」 ───────────────────────────────────────
// 网格与时间轴共用这一份：同一格可能堆着几门不同周次的课，显示哪张决定了用户看到的是不是本周那节。

/** 该课在 [week] **之后**最近一次会上的是哪一周；已经全部上完返回 null。 */
fun Course.nextActiveWeekAfter(week: Int): Int? {
    if (selectedWeeks.isNotEmpty()) {
        return selectedWeeks.filter { it > week }.minOrNull()
    }
    if (endWeek < week) return null
    val from = maxOf(week + 1, startWeek)
    if (from > endWeek) return null
    return (from..endWeek).firstOrNull { isActiveInWeek(it) }
}

/** 该课在 [week] **及之前**最后一次会上的是哪一周；还没开始返回 null。 */
fun Course.lastActiveWeekAtOrBefore(week: Int): Int? {
    if (selectedWeeks.isNotEmpty()) {
        return selectedWeeks.filter { it <= week }.maxOrNull()
    }
    if (startWeek > week) return null
    val to = minOf(week, endWeek)
    if (to < startWeek) return null
    return (to downTo startWeek).firstOrNull { isActiveInWeek(it) }
}

/**
 * 一组同槽位、**本周都没课**的课里挑一条显示。
 *
 * 取「下一次会上」最近的那门；全都上完了取最后结束的那门。
 * 判据用 [nextActiveWeekAfter] 而不是 `startWeek`：后者会把「1 周开始、第 10 周还有一次」
 * 的课判出局，明明下周就上却显示成下个月才上的那门。
 */
fun pickNonCurrentWeekCourse(group: List<Course>, week: Int): Course? {
    if (group.isEmpty()) return null
    if (group.size == 1) return group.first()

    val upcoming = group.mapNotNull { course ->
        course.nextActiveWeekAfter(week)?.let { course to it }
    }
    if (upcoming.isNotEmpty()) {
        return upcoming.minWithOrNull(
            compareBy({ it.second }, { it.first.startSection }, { it.first.endWeek })
        )?.first
    }

    val past = group.mapNotNull { course ->
        course.lastActiveWeekAtOrBefore(week)?.let { course to it }
    }
    if (past.isNotEmpty()) {
        return past.maxWithOrNull(
            compareBy({ it.second }, { it.first.endWeek })
        )?.first
    }
    return group.first()
}

/**
 * 备注截断到 [Course.NOTE_MAX_LENGTH]，**不把代理对切一半**。
 *
 * 输入框、导入解析、读盘清洗三处都走这里：Kotlin 的 String 是 UTF-16，直接 `take(50)`
 * 在末尾是 emoji 时会留下半个代理对（渲染成豆腐块，再落盘就是坏字符）。
 */
fun String.truncateNote(): String {
    val max = Course.NOTE_MAX_LENGTH
    if (length <= max) return this
    // 第 max 个字符若正好是某个代理对的前半个，就少取一个
    val cut = if (this[max - 1] in '\uD800'..'\uDBFF') max - 1 else max
    return substring(0, cut)
}
