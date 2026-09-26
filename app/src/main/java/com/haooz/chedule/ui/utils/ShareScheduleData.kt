package com.haooz.chedule.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.google.gson.GsonBuilder
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.ShareCodeApi
import com.haooz.chedule.data.TeachingWeekReorganization
import com.haooz.chedule.data.TeachingWeekReorganizationRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    val timeConfig = repository.getTimeConfig(
        repository.getScheduleTimeConfigId(scheduleName)
    )
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
    return buildShareSchedulePayload(scheduleName, settings, times, courses)
}

internal fun buildShareSchedulePayload(
    scheduleName: String,
    settings: Map<String, Any>,
    times: Map<String, Any>,
    courses: List<Course>,
): Map<String, Any> = mapOf(
    "schedule_name" to scheduleName,
    "settings" to settings,
    "times" to times,
    "courses" to courses.map { course ->
        mapOf(
            "name" to course.name,
            "classroom" to course.classroom,
            "teacher" to course.teacher,
            "dayOfWeek" to course.dayOfWeek,
            "startSection" to course.startSection,
            "endSection" to course.endSection,
            "isCustomTime" to course.isCustomTime,
            "customStartTime" to course.customStartTime,
            "customEndTime" to course.customEndTime,
        ) + shareCourseWeekFields(course)
    },
)

internal fun shareScheduleSettings(
    baseSettings: Map<String, Any>,
    reorganizationRules: List<TeachingWeekReorganizationRule>,
): Map<String, Any> = baseSettings + (
    "teaching_week_reorganizations" to TeachingWeekReorganization.toBackupValue(reorganizationRules)
)

internal data class ShareCourseWeekModel(
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int,
)

/** Keep parity and sparse selections distinguishable in shared schedules. */
internal fun shareCourseWeekFields(course: Course): Map<String, Any> = mapOf(
    "startWeek" to course.startWeek,
    "endWeek" to course.endWeek,
    "weekType" to course.weekType,
    "selectedWeeks" to course.selectedWeeks.sorted(),
)

/** Returns null for legacy share payloads that had no explicit week model. */
internal fun parseShareCourseWeekModel(
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
        startWeek = exactInteger("startWeek") ?: throw IllegalArgumentException("分享课表的startWeek无效"),
        endWeek = exactInteger("endWeek") ?: throw IllegalArgumentException("分享课表的endWeek无效"),
        weekType = exactInteger("weekType") ?: throw IllegalArgumentException("分享课表的weekType无效"),
    )
    require(model.startWeek > 0 && model.endWeek >= model.startWeek) {
        "分享课表的课程周次范围无效"
    }
    require(maxWeeks == null || maxWeeks in 1..CourseRepository.MAX_TOTAL_WEEKS) {
        "分享课表的学期总周数无效"
    }
    require(maxWeeks == null || model.endWeek <= maxWeeks) {
        "分享课表的课程周次超出支持范围"
    }
    require(model.weekType in setOf(Course.WEEK_TYPE_ALL, Course.WEEK_TYPE_ODD, Course.WEEK_TYPE_EVEN)) {
        "分享课表的课程单双周类型无效"
    }
    return model
}

/** Validate sparse week selections before callers persist any part of an imported schedule. */
internal fun parseShareSelectedWeeks(
    data: Map<String, Any?>,
    maxWeeks: Int? = null,
): List<Int> {
    if (!data.containsKey("selectedWeeks")) return emptyList()
    val rawWeeks = data["selectedWeeks"] as? List<*>
        ?: throw IllegalArgumentException("分享课表的selectedWeeks无效")
    val weeks = rawWeeks.map { value ->
        val number = (value as? Number)?.toDouble()
            ?: throw IllegalArgumentException("分享课表的selectedWeeks无效")
        require(number.isFinite() && number % 1.0 == 0.0 &&
            number in 1.0..Int.MAX_VALUE.toDouble()
        ) { "分享课表的selectedWeeks无效" }
        number.toInt()
    }
    require(weeks.distinct().size == weeks.size) { "分享课表的selectedWeeks重复" }
    require(maxWeeks == null || maxWeeks in 1..CourseRepository.MAX_TOTAL_WEEKS) {
        "分享课表的学期总周数无效"
    }
    require(maxWeeks == null || weeks.all { it <= maxWeeks }) {
        "分享课表的selectedWeeks超出支持范围"
    }
    return weeks
}

/** Distinguish a legacy missing settings object from an explicit malformed/null value. */
internal fun parseShareSettings(data: Map<String, Any>): Map<String, Any?>? {
    if (!data.containsKey("settings")) return null
    val rawSettings = data["settings"] as? Map<*, *>
        ?: throw IllegalArgumentException("分享课表的设置数据无效")
    require(rawSettings.keys.all { it is String }) { "分享课表的设置字段无效" }
    return rawSettings.entries.associate { (key, value) -> (key as String) to value }
}

internal fun parseShareTotalWeeks(settings: Map<String, Any?>?): Int {
    if (settings == null || !settings.containsKey("total_weeks")) return 20
    val number = (settings["total_weeks"] as? Number)?.toDouble()
        ?: throw IllegalArgumentException("分享课表的总周数无效")
    require(
        number.isFinite() && number % 1.0 == 0.0 &&
            number in 1.0..CourseRepository.MAX_TOTAL_WEEKS.toDouble(),
    ) { "分享课表的总周数无效" }
    return number.toInt()
}

/** Fully parse course records before the caller creates or persists an imported timetable. */
internal fun parseShareCourses(data: Map<String, Any>): List<Map<String, Any?>> {
    val rawCourses = data["courses"] as? List<*>
        ?: throw IllegalArgumentException("分享课表缺少课程数据")
    require(rawCourses.isNotEmpty()) { "分享课表没有课程" }
    return rawCourses.map { rawCourse ->
        val fields = rawCourse as? Map<*, *>
            ?: throw IllegalArgumentException("分享课表中的课程数据无效")
        require(fields.keys.all { it is String }) { "分享课表中的课程字段无效" }
        val course = fields.entries.associate { (key, value) -> (key as String) to value }
        require((course["name"] as? String)?.isNotBlank() == true) {
            "分享课表中的课程缺少有效名称"
        }
        val day = (course["dayOfWeek"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("分享课表中的星期无效")
        require(day.isFinite() && day % 1.0 == 0.0 && day in 1.0..7.0) {
            "分享课表中的星期无效"
        }
        val hasDirectSections = course.containsKey("startSection") || course.containsKey("endSection")
        if (hasDirectSections) {
            val startSection = (course["startSection"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("分享课表中的开始节次无效")
            val endSection = (course["endSection"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("分享课表中的结束节次无效")
            require(startSection.isFinite() && startSection % 1.0 == 0.0 && startSection >= 1.0 &&
                endSection.isFinite() && endSection % 1.0 == 0.0 && endSection >= startSection
            ) { "分享课表中的节次范围无效" }
        } else {
            val startMinutes = (course["startTotalMinutes"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("分享课表中的开始时间无效")
            val endMinutes = (course["endTotalMinutes"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("分享课表中的结束时间无效")
            require(startMinutes.isFinite() && startMinutes % 1.0 == 0.0 && startMinutes >= 0.0 &&
                endMinutes.isFinite() && endMinutes % 1.0 == 0.0 && endMinutes > startMinutes
            ) { "分享课表中的时间范围无效" }
        }
        course
    }
}

/** 将分享口令写入系统剪贴板 */
private fun copyShareCodeToClipboard(context: Context, code: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Nexio课表口令", code))
    } catch (_: Exception) {
        // 剪贴板失败不影响分享流程
    }
}

/** 上传课表生成趣味口令并唤起系统分享（确认后调用）；成功后自动复制口令到剪贴板 */
fun performScheduleShare(
    context: Context,
    scope: CoroutineScope,
    scheduleName: String,
    onSharingChanged: (Boolean) -> Unit = {},
) {
    val repository = CourseRepository.getInstance(context.applicationContext)
    val scheduleMap = buildShareScheduleMap(repository, scheduleName)
    if (scheduleMap == null) {
        Toast.makeText(context, "「$scheduleName」课表为空，无法分享", Toast.LENGTH_SHORT).show()
        return
    }
    onSharingChanged(true)
    scope.launch {
        try {
            Toast.makeText(context, "正在生成分享口令…", Toast.LENGTH_SHORT).show()
            val json = GsonBuilder().create().toJson(scheduleMap)
            ShareCodeApi.createShare(scheduleName, json).fold(
                onSuccess = { created ->
                    copyShareCodeToClipboard(context, created.code)
                    val ok = ShareImageGenerator.shareCard(
                        context,
                        created.code,
                        created.scheduleName
                    )
                    if (ok) {
                        Toast.makeText(
                            context,
                            "口令已复制：「${created.code}」30 分钟内有效",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "口令已复制「${created.code}」，但生成图片失败",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onFailure = { e ->
                    Toast.makeText(
                        context,
                        "分享失败：${e.message ?: "网络错误"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        } finally {
            onSharingChanged(false)
        }
    }
}
