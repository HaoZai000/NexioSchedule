package com.haooz.chedule.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.google.gson.GsonBuilder
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.ShareCodeApi
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

    return mapOf(
        "schedule_name" to scheduleName,
        "settings" to mapOf(
            "class_start_time" to repository.getClassStartTime(scheduleName),
            "current_week" to repository.getCurrentWeek(scheduleName),
            "total_weeks" to repository.getTotalWeeks(scheduleName),
            "smart_weekend" to repository.getSmartWeekend(scheduleName),
            "show_non_current_week" to repository.getShowNonCurrentWeek(scheduleName),
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
