package com.haooz.chedule.ui.utils

import android.content.Context
import android.widget.Toast
import com.google.gson.GsonBuilder
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.ShareCodeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 由 [CourseRepository] 组装分享/导出用的完整课表 JSON 对象。
 * 与 BackupAndMigration 的 buildExportJson 字段对齐，供口令分享与导入复用。
 */
fun buildShareScheduleMap(
    repository: CourseRepository,
    scheduleName: String,
): Map<String, Any>? {
    val courses = repository.getCoursesForSchedule(scheduleName)
    if (courses.isEmpty()) return null

    val timeConfig = repository.getCurrentTimeConfig()
    val morning = repository.getPeriodTimes("morning", scheduleName)
        .mapKeys { it.key.toString() }
    val afternoon = repository.getPeriodTimes("afternoon", scheduleName)
        .mapKeys { it.key.toString() }
    val evening = repository.getPeriodTimes("evening", scheduleName)
        .mapKeys { it.key.toString() }

    return mapOf(
        "schedule_name" to scheduleName,
        "settings" to mapOf(
            "class_start_time" to repository.getClassStartTime(),
            "current_week" to repository.getCurrentWeek(),
            "total_weeks" to repository.getTotalWeeks(),
            "smart_weekend" to repository.getSmartWeekend(),
            "show_non_current_week" to repository.getShowNonCurrentWeek(),
            "morning_sections" to timeConfig.morningSections,
            "afternoon_sections" to timeConfig.afternoonSections,
            "evening_sections" to timeConfig.eveningSections,
        ),
        "times" to mapOf(
            "morning" to morning,
            "afternoon" to afternoon,
            "evening" to evening,
            "section_names" to timeConfig.sectionNames,
        ),
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
                "selectedWeeks" to (course.selectedWeeks.ifEmpty {
                    (course.startWeek..course.endWeek).toList()
                }).sorted(),
            )
        },
    )
}

/** 上传课表生成趣味口令并唤起系统分享（确认后调用） */
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
                    val ok = ShareImageGenerator.shareCard(
                        context,
                        created.code,
                        created.scheduleName
                    )
                    if (ok) {
                        Toast.makeText(
                            context,
                            "口令「${created.code}」30 分钟内有效",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "生成分享图片失败", Toast.LENGTH_SHORT).show()
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
