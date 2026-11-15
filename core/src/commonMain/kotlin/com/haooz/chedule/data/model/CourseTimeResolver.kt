package com.haooz.chedule.data.model

/**
 * 课程节次→时间字符串的唯一解析器。
 *
 * 之前这段逻辑（自定义时间优先 / 上午下午晚间归属 / 时段映射）在 `CourseReminderHelper`
 * 与 4 个 widget provider 里各有一份，改一处极易漏改，正是"查课时间不对"类 bug 的温床。
 * 现在统一调用这里。
 *
 * 返回值：节次对应的 "HH:mm"，非法节次返回 null。
 */
object CourseTimeResolver {

    /** 课程开始时间（如 "08:00"）；自定义时间优先 */
    fun getStartTime(course: Course, repository: PeriodTimeSource): String? {
        if (course.hasValidCustomTime()) return course.customStartTime
        return resolveSectionTime(course.startSection, repository)
    }

    /** 课程结束时间（如 "08:45"）；自定义时间优先 */
    fun getEndTime(course: Course, repository: PeriodTimeSource): String? {
        if (course.hasValidCustomTime()) return course.customEndTime
        return resolveSectionTime(course.endSection, repository, isEnd = true)
    }

    private fun resolveSectionTime(
        section: Int,
        repository: PeriodTimeSource,
        isEnd: Boolean = false
    ): String? {
        if (section <= 0) return null
        // 一次取到全局节次表（实现方有缓存），不要再按三个时段各取一遍——
        // 解一门课的起止时间原本要走 6 次 getPeriodTimes，每一步都要重算生效作息
        val range = repository.getSectionTimes()[section] ?: return null
        // 同一节次对应 "HH:mm-HH:mm"，起始/结束按 isEnd 取左/右
        return if (isEnd) {
            range.substringAfter("-", range).trim().ifEmpty { null }
        } else {
            range.substringBefore("-").trim().ifEmpty { null }
        }
    }

    /**
     * 「上课提醒节次范围」判定。
     *
     * 范围先换算成一个**时间窗**：`startSection` 的上课时间 ~ `endSection` 的下课时间
     * （`endSection <= 0` 表示不设上限），再拿**课程自己的上课时间**落点判断。
     * 这样自定义时间的课也能被范围屏蔽，不再有「按节次过滤跳过自定义时间课」的例外。
     *
     * 节次时间表缺失或异常（旧数据、未配置的节次）时退回按 [Course.startSection] 比较；
     * 此时自定义时间的课定位不到节次，保持提醒 —— 宁可多提醒，不漏提醒。
     */
    fun isInReminderSections(
        course: Course,
        repository: PeriodTimeSource,
        startSection: Int,
        endSection: Int
    ): Boolean {
        if (startSection <= 1 && endSection <= 0) return true

        val sectionTimes = repository.getSectionTimes()
        val windowStart = sectionStartMinutes(sectionTimes, startSection)
        val windowEnd = if (endSection <= 0) Int.MAX_VALUE
        else sectionEndMinutes(sectionTimes, endSection)
        val courseStart = getStartTime(course, repository)?.let(::parseMinutes)

        if (windowStart != null && windowEnd != null &&
            courseStart != null && windowStart <= windowEnd
        ) {
            return courseStart in windowStart..windowEnd
        }

        // 时间表缺失/非法：退回按节次比较
        if (course.hasValidCustomTime()) return true
        if (course.startSection < startSection) return false
        if (endSection > 0 && course.startSection > endSection) return false
        return true
    }

    private fun sectionStartMinutes(sectionTimes: Map<Int, String>, section: Int): Int? =
        sectionTimes[section]?.substringBefore("-")?.trim()?.let(::parseMinutes)

    private fun sectionEndMinutes(sectionTimes: Map<Int, String>, section: Int): Int? =
        sectionTimes[section]?.substringAfter("-", "")?.trim()?.let(::parseMinutes)

    /** "HH:mm" → 当日分钟数；非法返回 null */
    private fun parseMinutes(value: String): Int? {
        val parts = value.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}

/**
 * 节次→时间解析所需的最小能力集。
 *
 * `CourseTimeResolver` 原本直接把 `CourseRepository` 当参数类型，而它是强 Android 依赖
 * （构造要 `Context`、内部全是 SharedPreferences），跟着进 commonMain 会直接阻断 iOS 目标。
 * 这里只把它真正用到的能力抽成接口：Android 侧由 `CourseRepository` 实现（签名本来就
 * 完全一致，不需要改实现），iOS / 桌面侧将来各自提供一个 actual 实现即可。
 *
 * 调用方（CourseReminderHelper 与 4 个 widget）传的还是 CourseRepository 实例，
 * 向上转型自动完成，调用点无需改动。
 */
interface PeriodTimeSource {
    /** period 取 "morning" / "afternoon" / "evening"；返回 节次 → "HH:mm-HH:mm" */
    fun getPeriodTimes(period: String): Map<Int, String>

    fun getMorningSections(): Int

    fun getAfternoonSections(): Int

    /**
     * 全局节次（1..总节数）→ "HH:mm-HH:mm"。
     *
     * 默认实现把三个时段拼起来；[CourseTimeResolver] 解一门课的时间只查一次这张表，
     * 有现成缓存的实现（[com.haooz.chedule.data.repo.CourseRepository]）可以覆盖掉这三次读取。
     */
    fun getSectionTimes(): Map<Int, String> {
        val morning = getPeriodTimes("morning")
        val afternoon = getPeriodTimes("afternoon")
        val evening = getPeriodTimes("evening")
        val morningSections = getMorningSections()
        val afternoonSections = getAfternoonSections()
        return buildMap {
            morning.forEach { (index, time) -> put(index, time) }
            afternoon.forEach { (index, time) -> put(morningSections + index, time) }
            evening.forEach { (index, time) -> put(morningSections + afternoonSections + index, time) }
        }
    }
}
