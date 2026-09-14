package com.haooz.chedule.data

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
    val lastModified: Long = System.currentTimeMillis(),
    // 开启后用 customStartTime/EndTime，否则回退节次时间表
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null, // "HH:mm"
    val customEndTime: String? = null    // "HH:mm"
) {
    companion object {
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
                result[i] = String.format("%02d:%02d-%02d:%02d", sH, sM, eH, eM)
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

    /** 自定义开关开启且起止时间均非空才生效 */
    fun hasValidCustomTime(): Boolean {
        return isCustomTime &&
            !customStartTime.isNullOrBlank() &&
            !customEndTime.isNullOrBlank()
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

    /** 自定义时间显示 "HH:mm - HH:mm"，否则回退节次文本 */
    fun getTimeDisplayText(): String {
        if (hasValidCustomTime()) {
            return "$customStartTime - $customEndTime"
        }
        return getSectionText()
    }

    /**
     * 课程所属时段：0=上午, 1=下午, 2=晚上。
     * 自定义时间按实际开始时刻归类（周末异构课常见：墙钟是上午但节次号落在下午段）；
     * 普通课仍按绝对节次号归类。
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
}
