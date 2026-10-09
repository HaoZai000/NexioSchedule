package com.haooz.chedule.data

import kotlinx.datetime.LocalDate

// ════════════════════════════════════════════════════════════════════════
//  节假日条目 —— 纯数据类型
//
//  原先它是 `HolidayManager` 里的**嵌套类** `HolidayManager.Entry`。提到顶层并下沉 `:core`
//  的原因是：`CourseScheduleDateBounds` / `TeachingWeekReorganization` /
//  `HolidayCourseExclusion` / `HolidayCountdown` 全都依赖它，而 `HolidayManager` 本体
//  （存储层）还差 `Context` / `org.json` / Gson / `@Synchronized`，短期搬不动。
//  把类型单独提出来，那四个文件就能先走。
//
//  ## ⚠ 刻意留在这里没有的东西
//
//  `Entry.toJson()`（org.json 的 `JSONObject`）**没有搬过来**，仍留在 `:app` 的
//  `HolidayManager.kt` 里，作为 `HolidayEntry` 的扩展函数。
//  原因：它产出的 JSON 串是**直接落盘的持久化格式**（`holiday_settings` 的 `entries_{年}`），
//  换 JSON 库必须逐字对齐，属于数据兼容红线。等 `HolidayManager` 整体下沉时
//  再一并换 `JsonSupport`，并配合真实用户数据的 round-trip 回归。
// ════════════════════════════════════════════════════════════════════════

/** 节假日与调休数据。假期跳过提醒，调休按配置的课表周次和星期调度。 */
data class HolidayEntry(
    val date: String,
    val endDate: String = "",
    val name: String,
    val type: Int,
    /**
     * 调休「上哪一天的课」的**绝对日期**（yyyy-MM-dd，空 = 未配置）。
     *
     * ⚠ 这是唯一可信来源。周次是相对「课表学期开始时间」算出来的，同一对 (周次,星期)
     * 在不同课表下指向完全不同的日期 —— 早先存 followWeek/followWeekday 导致一切换课表
     * 调休列就跟错课。现在存绝对日期，读取时按**当前课表**实时换算，换课表自动跟随。
     */
    val followDate: String = "",
    /** 旧数据兼容：仅用于迁移/老备份还原，读取一律走 [followDate] */
    val followWeek: Int = -1,
    /** 旧数据兼容：仅用于迁移/老备份还原，读取一律走 [followDate] */
    val followWeekday: Int = -1,
    val custom: Boolean = false,
) {
    companion object {
        /** 假期 */
        const val TYPE_HOLIDAY = 0

        /** 调休工作日 */
        const val TYPE_WORKSWAP = 1
    }

    fun matches(target: String): Boolean {
        val targetDate = runCatching { LocalDate.parse(target) }.getOrNull() ?: return false
        val startDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        val lastDate = if (endDate.isBlank()) {
            startDate
        } else {
            runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return false
        }
        return targetDate >= startDate && targetDate <= lastDate
    }

    /** 调休跟随的绝对日期；未配置/格式坏 → null */
    fun followLocalDate(): LocalDate? =
        followDate.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** 是否已配好跟随日期（老数据用 followWeek/followWeekday 也暂时算已配，等迁移） */
    fun hasFollowMapping(): Boolean =
        followLocalDate() != null || (followWeek > 0 && followWeekday in 1..7)
}

/**
 * 假期条目的**纯查询**逻辑。
 *
 * 与 `HolidayManager`（存储层，留在 `:app`）分开：这里只做「给定条目集合 → 查某天的条目」，
 * 不碰任何存储或平台 API。
 */
object HolidayEntries {
    fun entriesForDate(entriesByYear: Map<Int, List<HolidayEntry>>, date: LocalDate): List<HolidayEntry> {
        val storageYearPriority = buildList {
            add(date.year)
            add(date.year - 1)
            addAll(entriesByYear.keys.filter { it < date.year - 1 }.sortedDescending())
        }.distinct()

        val yearRank = storageYearPriority.withIndex().associate { it.value to it.index }
        return storageYearPriority.flatMap { year ->
            entriesByYear[year].orEmpty()
                .filter { it.matches(date.toString()) }
                .map { year to it }
        }.sortedWith(
            compareByDescending<Pair<Int, HolidayEntry>> { it.second.custom }
                .thenBy { yearRank.getValue(it.first) }
        ).map { it.second }
    }
}
