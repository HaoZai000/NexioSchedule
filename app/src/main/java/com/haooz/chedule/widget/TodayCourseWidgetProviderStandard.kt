/** 今日课程桌面小组件提供者 (2×2 标准版) */
package com.haooz.chedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import com.haooz.chedule.R
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper
import java.util.Calendar

class TodayCourseWidgetProviderStandard : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.haooz.chedule.UPDATE_TODAY_WIDGET_STANDARD"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, TodayCourseWidgetProviderStandard::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TodayCourseWidgetProviderStandard::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val repository = CourseRepository(context)
        val dark = WidgetTextSizes.isDark(context)

        val currentWeek = repository.getCurrentWeek()
        // getTodayOfWeek/getTodayCourses 统一在 CourseReminderHelper（含 workSwap / 节假日 / 周次范围 / 排序），
        // 这里不再保留私有副本。
        val today = CourseReminderHelper.getTodayOfWeek()
        val courses = repository.getAllCourses()
        val todayCourses = CourseReminderHelper.getTodayCourses(context)

        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val isNextDayReminderEnabled = repository.getNextDayReminder()
        val reminderMinutes = repository.getNextDayReminderHour() * 60 + repository.getNextDayReminderMinute()
        val todayCoursesFinished = if (todayCourses.isNotEmpty()) {
            val lastCourse = todayCourses.maxByOrNull { it.endSection }
            val lastEndTime = lastCourse?.let { getCourseEndTime(it, repository) }
            if (lastEndTime != null) {
                val parts = lastEndTime.split(":")
                if (parts.size == 2) {
                    val endMinutes = (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                    currentMinutes >= endMinutes
                } else true
            } else true
        } else true
        val showTomorrow = isNextDayReminderEnabled && currentMinutes >= reminderMinutes && todayCoursesFinished

        val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        // 明日预告与次日提醒同口径：节假日/调休走 resolveDaySchedule
        val resolution = CourseReminderHelper.resolveDaySchedule(context, forTomorrow = showTomorrow)
        val dayOfWeek = if (showTomorrow) resolution.displayDayOfWeek else today
        val targetWeek = resolution.displayWeek
        val targetCourses = if (showTomorrow) {
            resolution.courses
        } else {
            courses.filter { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(targetWeek) }
                .sortedBy { getCourseStartTime(it, repository).toMinutes() }
        }

        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        val isHoliday = currentWeek > totalWeeks || (currentWeek >= 1 && currentWeek > lastWeekWithCourses)

        val titleText = if (showTomorrow) {
            "明天"
        } else {
            // 今日也用日历日，避免调休日标题写成映射星期
            dayNames[(resolution.calendarDayOfWeek - 1).coerceIn(0, 6)]
        }
        val weekText = when {
            isHoliday -> "放假中"
            currentWeek < 1 -> "未开始"
            showTomorrow -> "第${targetWeek}周"
            else -> "第${currentWeek}周"
        }

        val displayCourse: Course?
        val remainingCourses: List<Course>
        val allCourses: List<Course>
        val emptyText: String
        val startTime: String
        val endTime: String
        val remainingCount: Int
        val dotCourses: List<Course>

        if (showTomorrow) {
            displayCourse = targetCourses.firstOrNull()
            remainingCourses = targetCourses.drop(1)
            allCourses = targetCourses
        } else {
            val unfinishedCourses = todayCourses.filter { course ->
                val end = getCourseEndTime(course, repository) ?: return@filter false
                val endParts = end.split(":")
                if (endParts.size == 2) {
                    val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
                    endMinutes > currentMinutes
                } else false
            }
            displayCourse = unfinishedCourses.firstOrNull()
            remainingCourses = unfinishedCourses.drop(1)
            allCourses = unfinishedCourses
        }

        if (displayCourse != null) {
            emptyText = ""
            startTime = getCourseStartTime(displayCourse, repository) ?: ""
            endTime = getCourseEndTime(displayCourse, repository) ?: ""
            remainingCount = remainingCourses.size
            dotCourses = if (!showTomorrow) {
                val startParts = startTime.split(":")
                val endParts = endTime.split(":")
                if (startParts.size == 2 && endParts.size == 2) {
                    val startMin = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
                    val endMin = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
                    if (currentMinutes in startMin until endMin) {
                        allCourses.filter { it.id != displayCourse.id }
                    } else allCourses
                } else allCourses
            } else allCourses
        } else {
            emptyText = when {
                isHoliday -> "假期中，暂无课程"
                currentWeek < 1 -> "学期暂未开始"
                showTomorrow && resolution.isHolidayDate -> "假期中，暂无课程"
                showTomorrow -> "明日无课"
                todayCourses.isEmpty() -> "今日无课"
                else -> "今日课程已上完"
            }
            startTime = ""
            endTime = ""
            remainingCount = -1
            dotCourses = emptyList()
        }

        val paddingMode = repository.getWidgetPaddingMode()
        val inCourse = displayCourse != null && run {
            val sp = startTime.split(":")
            val ep = endTime.split(":")
            sp.size == 2 && ep.size == 2 &&
                currentMinutes in ((sp[0].toIntOrNull() ?: 0) * 60 + (sp[1].toIntOrNull() ?: 0)) until
                ((ep[0].toIntOrNull() ?: 0) * 60 + (ep[1].toIntOrNull() ?: 0))
        }
        val signature = buildString {
            append(dark).append('|').append(paddingMode)
            append('|').append(titleText).append('|').append(weekText)
            if (displayCourse != null) {
                append('|').append(displayCourse.id).append('|').append(displayCourse.name)
                append('|').append(startTime).append('|').append(endTime)
                append('|').append(displayCourse.classroom)
                append('|').append(remainingCount).append('|').append(if (inCourse) "in" else "out")
                append('|').append(dotCourses.joinToString(",") { "${it.id}:${it.colorRes}" })
            } else {
                append('|').append(emptyText)
            }
        }
        if (WidgetUpdateCache.shouldSkip("today_std_$appWidgetId", signature)) return

        val views = RemoteViews(context.packageName, R.layout.widget_today_course_standard)
        applyWidgetMode(views, context, repository)
        WidgetTextSizes.applyTodayCourse(views)
        views.setTextViewText(R.id.widget_title, titleText)
        views.setTextViewText(R.id.widget_week, weekText)

        if (displayCourse != null) {
            views.setViewVisibility(R.id.widget_course_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)

            views.setTextViewText(R.id.widget_course_name, displayCourse.name)
            views.setTextViewText(R.id.widget_course_time, "$startTime-$endTime")
            views.setTextViewText(R.id.widget_course_location, displayCourse.classroom)

            val remainingText = if (remainingCount > 0) "还有${remainingCount}节课" else "没有其他课程"
            views.setTextViewText(R.id.widget_remaining_text, remainingText)

            val dotIds = listOf(
                R.id.widget_dot1, R.id.widget_dot2, R.id.widget_dot3, R.id.widget_dot4,
                R.id.widget_dot5, R.id.widget_dot6, R.id.widget_dot7, R.id.widget_dot8
            )
            for (i in dotIds.indices) {
                if (i < dotCourses.size) {
                    views.setViewVisibility(dotIds[i], View.VISIBLE)
                    views.setImageViewBitmap(dotIds[i], createCircleBitmap(context, dotCourses[i].colorRes.toInt(),
                        if (dark) WidgetTextSizes.TODAY_BG_DARK else WidgetTextSizes.TODAY_BG_LIGHT))
                } else {
                    views.setViewVisibility(dotIds[i], View.GONE)
                }
            }
        } else {
            views.setViewVisibility(R.id.widget_course_content, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty_text, emptyText)
            val dotIds = listOf(
                R.id.widget_dot1, R.id.widget_dot2, R.id.widget_dot3, R.id.widget_dot4,
                R.id.widget_dot5, R.id.widget_dot6, R.id.widget_dot7, R.id.widget_dot8
            )
            for (dotId in dotIds) {
                views.setViewVisibility(dotId, View.GONE)
            }
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val launchPending = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_header, launchPending)
        views.setOnClickPendingIntent(R.id.widget_course_content, launchPending)
        views.setOnClickPendingIntent(R.id.widget_empty, launchPending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetUpdateCache.invalidateWidget("today_std_$it") }
    }

    private fun applyWidgetMode(
        views: RemoteViews,
        context: Context,
        repository: CourseRepository
    ) {
        // 0=标准(0/0), 1=4×6(12/14), 2=4×7(8/10)
        val (top, bottom) = when (repository.getWidgetPaddingMode()) {
            1 -> 12f to 14f
            2 -> 8f to 10f
            else -> 0f to 0f
        }
        views.setViewPadding(
            R.id.widget_standard_root,
            0,
            WidgetTextSizes.dpToPx(context, top).toInt(),
            0,
            WidgetTextSizes.dpToPx(context, bottom).toInt()
        )
    }

    private fun createCircleBitmap(context: Context, color: Int, background: Int): Bitmap {
        val size = (7 * WidgetTextSizes.deviceDensity(context)).toInt()
        // 用不透明的卡片底色填充，避免透明像素被桌面渲染成灰色方块
        val bitmap = createBitmap(size, size).apply { eraseColor(background) }
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }

    private fun getCourseStartTime(course: Course, repository: CourseRepository): String? =
        com.haooz.chedule.data.CourseTimeResolver.getStartTime(course, repository)

    private fun getCourseEndTime(course: Course, repository: CourseRepository): String? =
        com.haooz.chedule.data.CourseTimeResolver.getEndTime(course, repository)

    /** "HH:mm" -> 分钟数，用于排序；null/非法返回 Int.MAX_VALUE 排到末尾 */
    private fun String?.toMinutes(): Int {
        if (this.isNullOrBlank()) return Int.MAX_VALUE
        val parts = this.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return h * 60 + m
    }
}
