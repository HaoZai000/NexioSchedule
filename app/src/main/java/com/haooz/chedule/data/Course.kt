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
    val lastModified: Long = System.currentTimeMillis() // 最后修改时间戳
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
        return if (startSection == endSection) {
            "第${startSection}节"
        } else {
            "第${startSection}-${endSection}节"
        }
    }

    /**
     * 获取周次描述
     */
    fun getWeekText(): String {
        if (selectedWeeks.isNotEmpty()) {
            if (selectedWeeks.size < 3) {
                return selectedWeeks.sorted().joinToString("、") { "${it}周" }
            }
            return formatWeeks(selectedWeeks.sorted())
        }
        val weekTypeStr = getWeekTypeText()
        return if (weekTypeStr.isNotEmpty()) {
            "${startWeek}-${endWeek}周 ($weekTypeStr)"
        } else {
            "${startWeek}-${endWeek}周"
        }
    }

    /**
     * 将周次列表格式化为紧凑显示字符串
     * 算法：检测全单周/全双周模式，然后按连续性分组压缩
     * 例：[1,3,5,7,9] → "1-9周 (单周)"，[1,2,3,5,6] → "1-3、5-6周"
     */
    private fun formatWeeks(sorted: List<Int>): String {
        val allOdd = sorted.all { it % 2 == 1 }
        val allEven = sorted.all { it % 2 == 0 }

        if (allOdd) {
            return groupConsecutive(sorted, 2, " (单周)")
        } else if (allEven) {
            return groupConsecutive(sorted, 2, " (双周)")
        } else {
            return groupConsecutive(sorted, 1, "周")
        }
    }

    /**
     * 将连续的数字序列分组压缩
     * @param step 步长，全周为1，单双周为2
     * @param suffix 后缀字符串，如"周"、" (单周)"、" (双周)"
     */
    private fun groupConsecutive(sorted: List<Int>, step: Int, suffix: String): String {
        val groups = mutableListOf<Pair<Int, Int>>()
        var start = sorted[0]
        var end = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] - end == step) {
                end = sorted[i]
            } else {
                groups.add(Pair(start, end))
                start = sorted[i]
                end = sorted[i]
            }
        }
        groups.add(Pair(start, end))
        return groups.joinToString("、") { (s, e) ->
            if (s == e) "${s}${suffix}" else "${s}-${e}${suffix}"
        }
    }
}
