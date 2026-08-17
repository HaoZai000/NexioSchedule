package com.haooz.chedule.data

/**
 * 课程数据模型
 */
data class Course(
    val id: String,
    val name: String,           // 课程名称
    val classroom: String,      // 教室
    val teacher: String,        // 教师
    val dayOfWeek: Int,         // 星期几 (1=周一, 7=周日)
    val startSection: Int,      // 开始节次 (1-12)
    val endSection: Int,        // 结束节次 (1-12)
    val startWeek: Int,         // 开始周次
    val endWeek: Int,           // 结束周次
    val weekType: Int,          // 周类型: 0=全周, 1=单周, 2=双周
    val colorRes: Long,         // 课程颜色
    val selectedWeeks: List<Int> = emptyList(), // 选中的具体周次列表（为空时使用startWeek/endWeek/weekType）
    val scheduleId: String = "", // 所属课表ID（空字符串表示未指定，用于云同步区分课表）
    val lastModified: Long = System.currentTimeMillis(), // 最后修改时间戳
    // 自定义上课时间：开启后使用 customStartTime/customEndTime，否则回退到节次时间表
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null, // "HH:mm"
    val customEndTime: String? = null    // "HH:mm"
) {
    companion object {
        const val WEEK_TYPE_ALL = 0   // 全部周
        const val WEEK_TYPE_ODD = 1   // 单周
        const val WEEK_TYPE_EVEN = 2  // 双周

        // 预设课程颜色
        val courseColors = listOf(
            0xFF4CAF50L,  // 绿色
            0xFF2196F3L,  // 蓝色
            0xFFFF9800L,  // 橙色
            0xFFF44336L,  // 红色
            0xFFE6B422L,  // 黄色
            0xFFE91E63L,  // 粉色
            0xFF00BCD4L,  // 青色
            0xFF3F51B5L,  // 靛蓝色
            0xFFAB47BCL,  // 紫罗兰
            0xFF009688L,  // 蓝绿色
            0xFF673AB7L   // 深紫色
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
         * 根据参数自动计算每个节次的时间区间
         * 算法：从起始时间开始，依次累加课时长度和休息时间，在指定节次处插入长休息
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

    /**
     * 判断该课程在指定周次是否有效
     */
    fun isActiveInWeek(week: Int): Boolean {
        // 如果有具体的周次列表，直接检查是否包含
        if (selectedWeeks.isNotEmpty()) {
            return week in selectedWeeks
        }
        // 否则使用范围判断
        if (week < startWeek || week > endWeek) return false
        return when (weekType) {
            WEEK_TYPE_ODD -> week % 2 == 1   // 单周
            WEEK_TYPE_EVEN -> week % 2 == 0  // 双周
            else -> true                      // 全部周
        }
    }

    /**
     * 是否启用了自定义上课时间（自定义开关开启且起止时间均有效）
     */
    fun hasValidCustomTime(): Boolean {
        return isCustomTime &&
            !customStartTime.isNullOrBlank() &&
            !customEndTime.isNullOrBlank()
    }

    /**
     * 获取有效开始时间（"HH:mm"）。优先使用自定义时间，否则回退到节次时间表
     * @param sectionTimes 全局绝对节次号 -> "HH:mm-HH:mm"
     */
    fun getEffectiveStartTime(sectionTimes: Map<Int, String>): String? {
        if (hasValidCustomTime()) return customStartTime
        return sectionTimes[startSection]?.split("-")?.firstOrNull()?.trim()
    }

    /**
     * 获取有效结束时间（"HH:mm"）。优先使用自定义时间，否则回退到节次时间表
     * @param sectionTimes 全局绝对节次号 -> "HH:mm-HH:mm"
     */
    fun getEffectiveEndTime(sectionTimes: Map<Int, String>): String? {
        if (hasValidCustomTime()) return customEndTime
        return sectionTimes[endSection]?.split("-")?.lastOrNull()?.trim()
    }

    /**
     * 获取周类型描述
     */
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

    /**
     * 获取节次描述
     */
    fun getSectionText(): String {
        if (startSection <= 0 && endSection <= 0) return ""
        return if (startSection == endSection) {
            "第${startSection}节"
        } else {
            "第${startSection}-${endSection}节"
        }
    }

    /**
     * 获取课程时间展示文本。自定义时间课程显示 "HH:mm - HH:mm"，
     * 否则回退到节次文本（如 "第1-2节"）
     */
    fun getTimeDisplayText(): String {
        if (hasValidCustomTime()) {
            return "$customStartTime - $customEndTime"
        }
        return getSectionText()
    }

    /**
     * 获取周次描述
     */
    fun getWeekText(): String {
        if (selectedWeeks.isNotEmpty()) {
            return formatWeeks(selectedWeeks.sorted())
        }
        if (startWeek <= 0 && endWeek <= 0) return ""
        val weekTypeStr = getWeekTypeText()
        return if (weekTypeStr.isNotEmpty()) {
            "${startWeek}-${endWeek}周 ($weekTypeStr)"
        } else {
            "${startWeek}-${endWeek}周"
        }
    }

    /**
     * 将周次列表格式化为紧凑显示字符串
     * 算法：优先检测单双周序列（步长2），其次检测连续序列（步长1），
     * 只有同类型序列长度≥3时才合并，否则单独显示。
     * 例：[1,3,5,7,9,11,13,15,16,17] → "1-15 (单周)、16周、17周"
     *     [1,3,5] → "1-5 (单周)"，[1,3] → "1周、3周"
     */
    private fun formatWeeks(sorted: List<Int>): String {
        val groups = mutableListOf<String>()
        var i = 0
        while (i < sorted.size) {
            // 优先检测单双周序列（步长2）
            val step2Run = extractRun(sorted, i, step = 2)
            if (step2Run.size >= 3) {
                val parity = if (step2Run.first() % 2 == 1) "单周" else "双周"
                groups.add("${step2Run.first()}-${step2Run.last()} ($parity)")
                i += step2Run.size
                continue
            }
            // 检测连续序列（步长1）
            val step1Run = extractRun(sorted, i, step = 1)
            if (step1Run.size >= 3) {
                groups.add("${step1Run.first()}-${step1Run.last()}周")
                i += step1Run.size
                continue
            }
            // 不满足合并条件，单独显示
            groups.add("${sorted[i]}周")
            i++
        }
        return groups.joinToString("、")
    }

    /**
     * 从指定位置开始提取步长为 step 的最长连续等差子序列
     */
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
