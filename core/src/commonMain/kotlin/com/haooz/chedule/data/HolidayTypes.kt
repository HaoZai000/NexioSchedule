package com.haooz.chedule.data

// ════════════════════════════════════════════════════════════════════════
//  节假日与调休 · 平台中立类型
//
//  从 `:app` 的 `Holidays.kt`（1782 行单文件全包）拆出来的 6 个类型。
//  它们的共同点：**不碰 `android.*` / `java.time` / `org.json` / Gson**，
//  只由 Int / Long / Boolean / String / [Course] 构成，因此具备直接进 commonMain 的资格。
//
//  ## 为什么先搬这 6 个
//
//  `Holidays.kt` 里其余部分都要先解决其中一种平台依赖才能动：
//
//  | 剩余部分 | 挡路的东西 |
//  |---|---|
//  | `HolidayManager` | `Context` / `SharedPreferences` / `org.json` / Gson / `@Synchronized` |
//  | `TeachingWeekReorganization`（object） | `java.time.LocalDate` / `ChronoUnit` / Gson |
//  | `HolidayCourseExclusion`（object） | `java.time.LocalDate` / `LocalTime` |
//  | `HolidayCountdown` | `java.time.LocalDate/LocalTime/LocalDateTime/Duration` |
//
//  这 6 个是**零转换成本**的部分：搬走它们不改变任何类型，`:app` 侧 import 一行都不用改
//  （包名仍是 `com.haooz.chedule.data`，与 `:app` 同包）。
//
//  ## ⚠ 不要为了「文件归属好看」把它们塞进子包
//
//  一旦放进 `com.haooz.chedule.data.holiday` 之类子包，`:app` 里同包的调用点就要逐个加
//  import —— 那是纯噪音改动，还会掩盖真正有意义的 diff。
//  等阶段 2 把 `java.time` 换成 kotlinx-datetime、整个集群一起搬完再考虑分包。
// ════════════════════════════════════════════════════════════════════════

/** Two consecutive weekday ranges from stable, original calendar weeks form one teaching week. */
data class TeachingWeekReorganizationRule(
    val firstOriginalWeek: Int,
    val firstStartWeekday: Int,
    val firstEndWeekday: Int,
    val secondOriginalWeek: Int,
    val secondStartWeekday: Int,
    val secondEndWeekday: Int,
)

/** A date in a reorganization gap keeps its surrounding teaching-week context but has no weekday. */
data class TeachingWeekPosition(
    val week: Long,
    val weekday: Int?,
    val isReorganizationPause: Boolean = false,
)

/** 假期最后一天的课程剔除：落在 [startSection]..[endSection] 节次内的课不上。 */
data class HolidayEndCourseExclusion(
    val enabled: Boolean = false,
    val startSection: Int = 1,
    val endSection: Int = 1,
) {
    fun isValid(): Boolean = startSection > 0 && endSection >= startSection
}

/** 假期前一天的课程剔除：与 [HolidayEndCourseExclusion] 同构，判定方向相反。 */
data class HolidayBeforeCourseExclusion(
    val enabled: Boolean = false,
    val startSection: Int = 1,
    val endSection: Int = 1,
) {
    fun isValid(): Boolean = startSection > 0 && endSection >= startSection
}

/** 某一天的逐日裁决结果：[courses] 是当天实际要上的课，两个 active 标志供 UI 提示用。 */
data class HolidayDayCourseResolution(
    val courses: List<Course>,
    val isHolidayDate: Boolean,
    val isHolidayEndCourseExclusionActive: Boolean,
    val isHolidayBeforeCourseExclusionActive: Boolean = false,
)

/** 被停掉的那批课里选一个代表展示，其余记为 [hidden]（用于课表卡片折叠）。 */
data class HolidayCourseDisplaySelection(
    val representative: Course?,
    val hidden: List<Course>,
)
