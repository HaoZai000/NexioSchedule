package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.model.TimeRoutine
import com.haooz.chedule.data.model.truncateNote
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.MiscStr

// 分享/导出课表的**数据契约**：组装（写出）与解析（读入）两半。
// ⚠ 「导入 / 导出」两条路必须**成对改**：字段名动一处，两边都要动。

// ── 1. 组装侧：CourseRepository → 分享/导出 JSON Map ──────────────

/**
 * 由 [CourseRepository] 组装分享/导出用的完整课表 JSON 对象。
 * 全部字段按 [scheduleName] 对应的课表/时间配置读取，避免与当前课表错配。
 */
fun buildShareScheduleMap(
    repository: CourseRepository,
    scheduleName: String,
): Map<String, Any>? {
    val courses = repository.getCoursesForSchedule(scheduleName)
    if (courses.isEmpty()) return null

    // 必须绑定被分享课表的 TimeConfig：getCurrentTimeConfig 可能指向另一张表
    // effective()：导出当天生效的那套作息时间（夏令时/冬令时）
    val timeConfig = repository.getTimeConfig(
        repository.getScheduleTimeConfigId(scheduleName)
    ).effective()
    val morning = repository.getPeriodTimes("morning", scheduleName)
        .mapKeys { it.key.toString() }
    val afternoon = repository.getPeriodTimes("afternoon", scheduleName)
        .mapKeys { it.key.toString() }
    val evening = repository.getPeriodTimes("evening", scheduleName)
        .mapKeys { it.key.toString() }

    val settings = shareScheduleSettings(
        baseSettings = mapOf(
            "class_start_time" to repository.getClassStartTime(scheduleName),
            "current_week" to repository.getCurrentWeek(scheduleName),
            "total_weeks" to repository.getTotalWeeks(scheduleName),
            "smart_weekend" to repository.getSmartWeekend(scheduleName),
            "show_non_current_week" to repository.getShowNonCurrentWeek(scheduleName),
            "morning_sections" to timeConfig.morningSections,
            "afternoon_sections" to timeConfig.afternoonSections,
            "evening_sections" to timeConfig.eveningSections,
        ),
        reorganizationRules = repository.getTeachingWeekReorganizations(scheduleName),
    )
    val times = mapOf(
        "morning" to morning,
        "afternoon" to afternoon,
        "evening" to evening,
        "section_names" to timeConfig.sectionNames,
    )
    // times 只带当天生效那套（旧版本靠它渲染）；routines 带全部作息，导入后按日期自动切换
    return buildShareSchedulePayload(
        scheduleName = scheduleName,
        settings = settings,
        times = times,
        courses = courses,
        routines = timeConfig.safeRoutines,
        holiday = HolidayManager.exportBackupDataFor(scheduleName),
    )
}

/** 单课表/分享共用的课程字段序列化；导出与单课表备份必须走同一份字段清单 */
fun courseToShareMap(course: Course): Map<String, Any?> = buildMap {
    put("name", course.name)
    put("classroom", course.classroom)
    put("teacher", course.teacher)
    put("dayOfWeek", course.dayOfWeek)
    put("startSection", course.startSection)
    put("endSection", course.endSection)
    put("isCustomTime", course.isCustomTime)
    put("customStartTime", course.customStartTime)
    put("customEndTime", course.customEndTime)
    put("colorRes", course.colorRes)
    putAll(shareCourseWeekFields(course))
    // 备注是后加的字段：没有备注就不写，旧版本导入方看到的载荷与以前逐字一致
    if (course.note.isNotEmpty()) put("note", course.note)
    if (course.weekNotes.isNotEmpty()) {
        put("weekNotes", course.weekNotes.mapKeys { it.key.toString() })
    }
}

fun buildShareSchedulePayload(
    scheduleName: String,
    settings: Map<String, Any>,
    times: Map<String, Any>,
    courses: List<Course>,
    routines: List<TimeRoutine> = emptyList(),
    holiday: Map<String, Any>? = null,
): Map<String, Any> = buildMap {
    put("schedule_name", scheduleName)
    put("settings", settings)
    put("times", times)
    put("courses", courses.map(::courseToShareMap))
    // 全套作息（夏令时/冬令时）。旧版本忽略这个字段，仍按 times 建单作息
    put("routines", routines)
    if (holiday != null) put("holiday", holiday)
}

fun shareScheduleSettings(
    baseSettings: Map<String, Any>,
    reorganizationRules: List<TeachingWeekReorganizationRule>,
): Map<String, Any> = baseSettings + (
    "teaching_week_reorganizations" to TeachingWeekReorganization.toBackupValue(reorganizationRules)
)

data class ShareCourseWeekModel(
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int,
)

/** Keep parity and sparse selections distinguishable in shared schedules. */
fun shareCourseWeekFields(course: Course): Map<String, Any> = mapOf(
    "startWeek" to course.startWeek,
    "endWeek" to course.endWeek,
    "weekType" to course.weekType,
    "selectedWeeks" to course.selectedWeeks.sorted(),
)

// ── 课程备注（导出 / 导入成对）──
//
// 长度上限只有一个来源：`Course.NOTE_MAX_LENGTH`（截断走 `String.truncateNote()`）。
// 服务端 `SHARE_MAX_NOTE_LEN` 必须与它相同，改一边就要改另一边。

/** 分享包里的 `note`：缺字段 → 空串；类型不对也按空串处理，坏数据不该拦住导入。 */
fun parseShareCourseNote(data: Map<String, Any?>): String =
    (data["note"] as? String)?.trim()?.truncateNote() ?: ""

/** 分享包里的 `weekNotes`：键是周次，越界与非正数条目跳过。 */
fun parseShareCourseWeekNotes(
    data: Map<String, Any?>,
    maxWeeks: Int? = null,
): Map<Int, String> {
    val raw = data["weekNotes"] as? Map<*, *> ?: return emptyMap()
    return buildMap {
        for ((rawKey, rawValue) in raw) {
            val week = when (rawKey) {
                is String -> rawKey.trim().toIntOrNull()
                is Number -> rawKey.toInt()
                else -> null
            } ?: continue
            if (week <= 0 || (maxWeeks != null && week > maxWeeks)) continue
            val text = (rawValue as? String)?.trim()?.truncateNote() ?: continue
            if (text.isNotEmpty()) put(week, text)
        }
    }
}

// ── 2. 解析侧：分享 JSON Map → 校验过的课表结构 ──────────────────

/** Returns null for legacy share payloads that had no explicit week model. */
fun parseShareCourseWeekModel(
    data: Map<String, Any?>,
    maxWeeks: Int? = null,
): ShareCourseWeekModel? {
    val fields = listOf("startWeek", "endWeek", "weekType")
    if (fields.none(data::containsKey)) return null

    fun exactInteger(key: String): Int? {
        val number = (data[key] as? Number)?.toDouble() ?: return null
        if (!number.isFinite() || number % 1.0 != 0.0 ||
            number < Int.MIN_VALUE.toDouble() || number > Int.MAX_VALUE.toDouble()
        ) return null
        return number.toInt()
    }

    val model = ShareCourseWeekModel(
        startWeek = exactInteger("startWeek") ?: throw IllegalArgumentException(MiscStr.shareStartWeekInvalid()),
        endWeek = exactInteger("endWeek") ?: throw IllegalArgumentException(MiscStr.shareEndWeekInvalid()),
        weekType = exactInteger("weekType") ?: throw IllegalArgumentException(MiscStr.shareWeekTypeInvalid()),
    )
    require(model.startWeek > 0 && model.endWeek >= model.startWeek) {
        MiscStr.shareWeekRangeInvalid()
    }
    require(model.weekType in setOf(Course.WEEK_TYPE_ALL, Course.WEEK_TYPE_ODD, Course.WEEK_TYPE_EVEN)) {
        MiscStr.shareOddEvenTypeInvalid()
    }
    require(maxWeeks == null || maxWeeks in 1..CourseRepository.MAX_TOTAL_WEEKS) {
        MiscStr.shareSemesterWeeksInvalid()
    }
    require(maxWeeks == null || model.endWeek <= maxWeeks) {
        MiscStr.shareWeeksOutOfRange()
    }
    return model
}

/** Validate sparse week selections before callers persist any part of an imported schedule. */
fun parseShareSelectedWeeks(
    data: Map<String, Any?>,
    maxWeeks: Int? = null,
): List<Int> {
    if (!data.containsKey("selectedWeeks")) return emptyList()
    val rawWeeks = data["selectedWeeks"] as? List<*>
        ?: throw IllegalArgumentException(MiscStr.shareSelectedWeeksInvalid())
    val weeks = rawWeeks.map { value ->
        val number = (value as? Number)?.toDouble()
            ?: throw IllegalArgumentException(MiscStr.shareSelectedWeeksInvalid())
        require(number.isFinite() && number % 1.0 == 0.0 &&
            number in 1.0..Int.MAX_VALUE.toDouble()
        ) { MiscStr.shareSelectedWeeksInvalid() }
        number.toInt()
    }
    require(weeks.distinct().size == weeks.size) { MiscStr.shareSelectedWeeksDuplicate() }
    require(maxWeeks == null || maxWeeks in 1..CourseRepository.MAX_TOTAL_WEEKS) {
        MiscStr.shareSemesterWeeksInvalid()
    }
    require(maxWeeks == null || weeks.all { it <= maxWeeks }) {
        MiscStr.shareSelectedWeeksOutOfRange()
    }
    return weeks
}

/** Distinguish a legacy missing settings object from an explicit malformed/null value. */
fun parseShareSettings(data: Map<String, Any>): Map<String, Any?>? {
    if (!data.containsKey("settings")) return null
    val rawSettings = data["settings"] as? Map<*, *>
        ?: throw IllegalArgumentException(MiscStr.shareSettingsInvalid())
    require(rawSettings.keys.all { it is String }) { MiscStr.shareSettingsFieldsInvalid() }
    return rawSettings.entries.associate { (key, value) -> (key as String) to value }
}

fun parseShareTotalWeeks(settings: Map<String, Any?>?): Int {
    if (settings == null || !settings.containsKey("total_weeks")) return 20
    val number = (settings["total_weeks"] as? Number)?.toDouble()
        ?: throw IllegalArgumentException(MiscStr.shareTotalWeeksInvalid())
    require(
        number.isFinite() && number % 1.0 == 0.0 &&
            number in 1.0..CourseRepository.MAX_TOTAL_WEEKS.toDouble(),
    ) { MiscStr.shareTotalWeeksInvalid() }
    return number.toInt()
}

/** Fully parse course records before the caller creates or persists an imported timetable. */
fun parseShareCourses(data: Map<String, Any>): List<Map<String, Any?>> {
    val rawCourses = data["courses"] as? List<*>
        ?: throw IllegalArgumentException(MiscStr.shareMissingCourses())
    require(rawCourses.isNotEmpty()) { MiscStr.shareNoCourses() }
    return rawCourses.map { rawCourse ->
        val fields = rawCourse as? Map<*, *>
            ?: throw IllegalArgumentException(MiscStr.shareCourseInvalid())
        require(fields.keys.all { it is String }) { MiscStr.shareCourseFieldsInvalid() }
        val course = fields.entries.associate { (key, value) -> (key as String) to value }
        require((course["name"] as? String)?.isNotBlank() == true) {
            MiscStr.shareCourseNameMissing()
        }
        val day = (course["dayOfWeek"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException(MiscStr.shareDayInvalid())
        require(day.isFinite() && day % 1.0 == 0.0 && day in 1.0..7.0) {
            MiscStr.shareDayInvalid()
        }
        val hasDirectSections = course.containsKey("startSection") || course.containsKey("endSection")
        if (hasDirectSections) {
            val startSection = (course["startSection"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException(MiscStr.shareStartSectionInvalid())
            val endSection = (course["endSection"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException(MiscStr.shareEndSectionInvalid())
            require(startSection.isFinite() && startSection % 1.0 == 0.0 && startSection >= 1.0 &&
                endSection.isFinite() && endSection % 1.0 == 0.0 && endSection >= startSection
            ) { MiscStr.shareSectionRangeInvalid() }
        } else {
            val startMinutes = (course["startTotalMinutes"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException(MiscStr.shareStartTimeInvalid())
            val endMinutes = (course["endTotalMinutes"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException(MiscStr.shareEndTimeInvalid())
            require(startMinutes.isFinite() && startMinutes % 1.0 == 0.0 && startMinutes >= 0.0 &&
                endMinutes.isFinite() && endMinutes % 1.0 == 0.0 && endMinutes > startMinutes
            ) { MiscStr.shareTimeRangeInvalid() }
        }
        course
    }
}
