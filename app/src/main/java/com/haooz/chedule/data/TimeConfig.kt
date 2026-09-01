package com.haooz.chedule.data

/**
 * 特殊时段块（无编号，如早读/大课间/眼保健操）。
 * 按自定义起止时间沿时间轴定位，挤出让出纵向空间，不计入课程提醒。
 */
data class SpecialBlock(
    val id: Long = 0L,
    val name: String = "",          // 如"早读""眼保健操"
    val startTime: String = "08:00",
    val endTime: String = "08:40"
)

/**
 * 时间配置数据类 - 存储多套时间设置
 */
data class TimeConfig(
    val id: Long = 0L,
    val name: String = "默认配置",

    // 节数配置
    val morningSections: Int = 4,
    val afternoonSections: Int = 4,
    val eveningSections: Int = 4,

    // 快速时间配置
    val quickTimeEnabled: Boolean = false,
    val classDuration: Int = 45,
    val shortBreak: Int = 10,

    // 长课间休息配置
    val longBreakEnabled: Boolean = false,
    val longBreakMorning: Int = 20,
    val longBreakAfternoon: Int = 20,
    val longBreakEvening: Int = 20,
    val longBreakMorningSection: Int = 2,
    val longBreakAfternoonSection: Int = 2,
    val longBreakEveningSection: Int = 2,

    // 各时段起始时间
    val morningStartHour: Int = 8,
    val morningStartMinute: Int = 0,
    val afternoonStartHour: Int = 14,
    val afternoonStartMinute: Int = 0,
    val eveningStartHour: Int = 18,
    val eveningStartMinute: Int = 30,

    // 各节次时间（全局绝对节次号 -> "HH:mm-HH:mm"）
    // 注意：Gson 会将 Int key 转换为 String，所以存储为 Map<String, String>
    val sectionTimes: Map<String, String> = emptyMap(),

    // 自定义节次名称（key 同 sectionTimes，如 "morning_1" -> "早自习"）
    val sectionNames: Map<String, String> = emptyMap(),

    // 特殊时段块（无编号，如早读/大课间/眼保健操），不计入课程提醒
    val specialBlocks: List<SpecialBlock> = emptyList()
) {

    /**
     * 获取指定时段的节次时间映射
     * period: "morning" / "afternoon" / "evening"
     * 返回：时段内相对节次号 (1-6) -> "HH:mm-HH:mm"
     */
    fun getPeriodTimes(period: String): Map<Int, String> {
        // 如果有预设的快速时间，使用快速时间计算
        if (quickTimeEnabled) {
            return calculatePeriodTimes(period)
        }

        // 否则从 sectionTimes 中提取
        val result = mutableMapOf<Int, String>()
        for ((k, v) in sectionTimes) {
            val strKey = k.toString()
            if (strKey.startsWith("${period}_")) {
                val idx = strKey.removePrefix("${period}_").toIntOrNull()
                if (idx != null) result[idx] = v
            }
        }
        return result.ifEmpty { getDefaultTimesForPeriod(period) }
    }

    /**
     * 根据快速时间配置计算指定时段的节次时间
     */
    fun calculateSectionTimes(): Map<Int, String> {
        val morningTimes = calculatePeriodTimes("morning")
        val afternoonTimes = calculatePeriodTimes("afternoon")
        val eveningTimes = calculatePeriodTimes("evening")

        val result = mutableMapOf<Int, String>()
        // 转换为全局绝对节次号
        morningTimes.forEach { (k, v) -> result[k] = v }
        afternoonTimes.forEach { (k, v) -> result[morningSections + k] = v }
        eveningTimes.forEach { (k, v) -> result[morningSections + afternoonSections + k] = v }
        return result
    }

    /**
     * 内部方法：计算指定时段的节次时间
     */
    private fun calculatePeriodTimes(period: String): Map<Int, String> {
        val sectionCount = when (period) {
            "morning" -> morningSections
            "afternoon" -> afternoonSections
            "evening" -> eveningSections
            else -> return emptyMap()
        }
        if (sectionCount <= 0) return emptyMap()

        val (startHour, startMinute) = when (period) {
            "morning" -> morningStartHour to morningStartMinute
            "afternoon" -> afternoonStartHour to afternoonStartMinute
            "evening" -> eveningStartHour to eveningStartMinute
            else -> return emptyMap()
        }

        val longBreak = when (period) {
            "morning" -> longBreakMorning
            "afternoon" -> longBreakAfternoon
            "evening" -> longBreakEvening
            else -> 0
        }
        val longBreakSection = when (period) {
            "morning" -> longBreakMorningSection
            "afternoon" -> longBreakAfternoonSection
            "evening" -> longBreakEveningSection
            else -> 2
        }

        return Course.calculatePeriodTimes(
            sectionCount = sectionCount,
            startHour = startHour,
            startMinute = startMinute,
            classDuration = classDuration,
            shortBreak = shortBreak,
            longBreak = if (longBreakEnabled) longBreak else 0,
            longBreakSection = longBreakSection
        )
    }

    companion object {
        /**
         * 默认时间段的节次时间
         */
        private fun getDefaultTimesForPeriod(period: String): Map<Int, String> = when (period) {
            "morning" -> Course.defaultMorningTimes
            "afternoon" -> Course.defaultAfternoonTimes
            "evening" -> Course.defaultEveningTimes
            else -> emptyMap()
        }

        /**
         * 从 CourseRepository 创建默认 TimeConfig
         */
        fun fromRepository(repository: CourseRepository): TimeConfig {
            val sectionTimes = mutableMapOf<String, String>()
            for (period in listOf("morning", "afternoon", "evening")) {
                val times = repository.getPeriodTimes(period)
                for ((idx, time) in times) {
                    sectionTimes["${period}_$idx"] = time
                }
            }
            return TimeConfig(
                morningSections = repository.getMorningSections(),
                afternoonSections = repository.getAfternoonSections(),
                eveningSections = repository.getEveningSections(),
                quickTimeEnabled = repository.getQuickTimeEnabled(),
                classDuration = repository.getClassDuration(),
                shortBreak = repository.getShortBreak(),
                longBreakEnabled = repository.getLongBreakEnabled(),
                longBreakMorning = repository.getLongBreakMorning(),
                longBreakAfternoon = repository.getLongBreakAfternoon(),
                longBreakEvening = repository.getLongBreakEvening(),
                longBreakMorningSection = repository.getLongBreakMorningSection(),
                longBreakAfternoonSection = repository.getLongBreakAfternoonSection(),
                longBreakEveningSection = repository.getLongBreakEveningSection(),
                morningStartHour = repository.getMorningStartHour(),
                morningStartMinute = repository.getMorningStartMinute(),
                afternoonStartHour = repository.getAfternoonStartHour(),
                afternoonStartMinute = repository.getAfternoonStartMinute(),
                eveningStartHour = repository.getEveningStartHour(),
                eveningStartMinute = repository.getEveningStartMinute(),
                sectionTimes = sectionTimes
            )
        }
    }
}
