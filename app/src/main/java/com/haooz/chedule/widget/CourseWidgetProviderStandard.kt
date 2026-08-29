/** 课程表桌面小组件提供者 (2×2 标准版) */
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
import java.util.Calendar

class CourseWidgetProviderStandard : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.haooz.chedule.UPDATE_WIDGET_STANDARD"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, CourseWidgetProviderStandard::class.java).apply {
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
                ComponentName(context, CourseWidgetProviderStandard::class.java)
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
        val views = RemoteViews(context.packageName, R.layout.widget_course_reminder_standard)
        applyWidgetMode(views, repository, context)
        WidgetTextSizes.applyCourseReminder(context, views)

        val currentWeek = repository.getCurrentWeek()
        val today = getTodayOfWeek()
        val courses = repository.getAllCourses()
        val todayCourses = courses.filter { it.dayOfWeek == today && it.isActiveInWeek(currentWeek) }
            .sortedBy { getCourseStartTime(it, repository).toMinutes() }

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
        val dayOfWeek: Int
        val targetWeek: Int
        if (showTomorrow) {
            val tomorrow = today + 1
            dayOfWeek = if (tomorrow > 7) 1 else tomorrow
            targetWeek = if (tomorrow > 7) currentWeek + 1 else currentWeek
        } else {
            dayOfWeek = today
            targetWeek = currentWeek
        }

        val targetCourses = courses.filter { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(targetWeek) }
            .sortedBy { getCourseStartTime(it, repository).toMinutes() }

        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        val isHoliday = currentWeek > totalWeeks || (currentWeek >= 1 && currentWeek > lastWeekWithCourses)
        val prefix = if (showTomorrow) "明日课程" else "今天"
        views.setTextViewText(R.id.widget_title, "$prefix / ${dayNames[dayOfWeek - 1]}")
        val weekText = when {
            isHoliday -> "放假中"
            currentWeek < 1 -> "未开始"
            else -> "第${currentWeek}周"
        }
        views.setTextViewText(R.id.widget_week, weekText)

        val displayCourses = if (showTomorrow) {
            targetCourses.take(2)
        } else {
            todayCourses.filter { course ->
                val end = getCourseEndTime(course, repository) ?: return@filter false
                val endParts = end.split(":")
                if (endParts.size == 2) {
                    val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
                    endMinutes > currentMinutes
                } else false
            }.take(2)
        }

        if (displayCourses.isEmpty()) {
            views.setViewVisibility(R.id.widget_course1, View.GONE)
            views.setViewVisibility(R.id.widget_course2, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            val emptyText = when {
                isHoliday -> "假期中，暂无课程"
                currentWeek < 1 -> "学期暂未开始"
                showTomorrow -> "明日无课"
                todayCourses.isEmpty() -> "今日无课"
                else -> "今日课程已上完"
            }
            views.setTextViewText(R.id.widget_empty_text, emptyText)
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            views.setViewVisibility(R.id.widget_course1, View.VISIBLE)
            views.setViewVisibility(R.id.widget_course2, View.VISIBLE)

            val c1 = displayCourses[0]
            views.setViewVisibility(R.id.widget_course1, View.VISIBLE)
            views.setTextViewText(R.id.widget_name1, c1.name)
            views.setBitmap(R.id.widget_color1, "setImageBitmap", createColorBarBitmap(context, c1.colorRes.toInt()))
            val start1 = getCourseStartTime(c1, repository) ?: ""
            val end1 = getCourseEndTime(c1, repository) ?: ""
            views.setTextViewText(R.id.widget_time_start1, start1)
            views.setTextViewText(R.id.widget_time_end1, end1)
            val remaining1 = if (showTomorrow) null else getRemainingMinutes(start1, end1, currentMinutes)
            views.setViewVisibility(R.id.widget_now1, if (remaining1 != null) View.VISIBLE else View.GONE)
            if (remaining1 != null) views.setTextViewText(R.id.widget_now1, "${remaining1}分钟结束")
            views.setInt(R.id.widget_course1, "setBackgroundResource",
                if (remaining1 != null) R.drawable.widget_card_active_background else R.drawable.widget_card_background)
            views.setTextViewText(R.id.widget_info1, buildCourseInfo(c1))

            if (displayCourses.size >= 2) {
                val c2 = displayCourses[1]
                views.setTextViewText(R.id.widget_name2, c2.name)
                views.setBitmap(R.id.widget_color2, "setImageBitmap", createColorBarBitmap(context, c2.colorRes.toInt()))
                val start2 = getCourseStartTime(c2, repository) ?: ""
                val end2 = getCourseEndTime(c2, repository) ?: ""
                views.setTextViewText(R.id.widget_time_start2, start2)
                views.setTextViewText(R.id.widget_time_end2, end2)
                val remaining2 = if (showTomorrow) null else getRemainingMinutes(start2, end2, currentMinutes)
                views.setViewVisibility(R.id.widget_now2, if (remaining2 != null) View.VISIBLE else View.GONE)
                if (remaining2 != null) views.setTextViewText(R.id.widget_now2, "${remaining2}分钟结束")
                views.setInt(R.id.widget_course2, "setBackgroundResource",
                    if (remaining2 != null) R.drawable.widget_card_active_background else R.drawable.widget_card_background)
                views.setTextViewText(R.id.widget_info2, buildCourseInfo(c2))
            } else {
                views.setTextViewText(R.id.widget_name2, "")
                views.setBitmap(R.id.widget_color2, "setImageBitmap", createBitmap(1, 1))
                views.setTextViewText(R.id.widget_time_start2, "")
                views.setTextViewText(R.id.widget_time_end2, "")
                views.setTextViewText(R.id.widget_info2, "")
                views.setViewVisibility(R.id.widget_now2, View.GONE)
            }
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val launchPending = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_header, launchPending)

        val refreshIntent = Intent(context, CourseWidgetProviderStandard::class.java).apply {
            action = ACTION_UPDATE_WIDGET
        }
        val refreshPending = PendingIntent.getBroadcast(
            context, 1, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_course1, refreshPending)
        views.setOnClickPendingIntent(R.id.widget_course2, refreshPending)
        views.setOnClickPendingIntent(R.id.widget_empty, refreshPending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun applyWidgetMode(
        views: RemoteViews,
        repository: CourseRepository,
        context: Context
    ) {
        // 0=标准(0/0), 1=4×6(12/14), 2=4×7(8/10)
        val (top, bottom) = when (repository.getWidgetPaddingMode()) {
            1 -> 12f to 14f
            2 -> 8f to 10f
            else -> 0f to 0f
        }
        val density = context.resources.displayMetrics.density
        views.setViewPadding(
            R.id.widget_standard_root,
            0,
            (top * density).toInt(),
            0,
            (bottom * density).toInt()
        )
    }

    private fun isCourseActive(startTime: String, endTime: String, currentMinutes: Int): Boolean {
        val startParts = startTime.split(":")
        val endParts = endTime.split(":")
        if (startParts.size != 2 || endParts.size != 2) return false
        val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
        val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
        return currentMinutes in startMinutes until endMinutes
    }

    private fun getRemainingMinutes(startTime: String, endTime: String, currentMinutes: Int): Int? {
        val startParts = startTime.split(":")
        val endParts = endTime.split(":")
        if (startParts.size != 2 || endParts.size != 2) return null
        val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
        val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
        return if (currentMinutes in startMinutes until endMinutes) {
            endMinutes - currentMinutes
        } else null
    }

    private fun getCourseStartTime(course: Course, repository: CourseRepository): String? {
        if (course.hasValidCustomTime()) return course.customStartTime
        val morningTimes = repository.getPeriodTimes("morning")
        val afternoonTimes = repository.getPeriodTimes("afternoon")
        val eveningTimes = repository.getPeriodTimes("evening")
        val morningSections = repository.getMorningSections()
        val afternoonSections = repository.getAfternoonSections()
        val section = course.startSection
        val timeMap = when {
            section <= morningSections -> morningTimes
            section <= morningSections + afternoonSections -> afternoonTimes
            else -> eveningTimes
        }
        val relativeSection = when {
            section <= morningSections -> section
            section <= morningSections + afternoonSections -> section - morningSections
            else -> section - morningSections - afternoonSections
        }
        return timeMap[relativeSection]?.split("-")?.firstOrNull()?.trim()
    }

    private fun getCourseEndTime(course: Course, repository: CourseRepository): String? {
        if (course.hasValidCustomTime()) return course.customEndTime
        val morningTimes = repository.getPeriodTimes("morning")
        val afternoonTimes = repository.getPeriodTimes("afternoon")
        val eveningTimes = repository.getPeriodTimes("evening")
        val morningSections = repository.getMorningSections()
        val afternoonSections = repository.getAfternoonSections()
        val section = course.endSection
        val timeMap = when {
            section <= morningSections -> morningTimes
            section <= morningSections + afternoonSections -> afternoonTimes
            else -> eveningTimes
        }
        val relativeSection = when {
            section <= morningSections -> section
            section <= morningSections + afternoonSections -> section - morningSections
            else -> section - morningSections - afternoonSections
        }
        return timeMap[relativeSection]?.split("-")?.lastOrNull()?.trim()
    }

    private fun createColorBarBitmap(context: Context, color: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (4 * density).toInt()
        val height = (28 * density).toInt()
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        val radius = width.toFloat()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        return bitmap
    }

    /** 拼接课程信息：节次（自定义时间课程跳过）+ 教室 + 教师，非空项间用「｜」分隔 */
    private fun buildCourseInfo(course: Course): String = buildList {
        if (!course.hasValidCustomTime()) add(course.getTimeDisplayText())
        if (course.classroom.isNotEmpty()) add(course.classroom)
        if (course.teacher.isNotEmpty()) add(course.teacher)
    }.joinToString("｜")

    private fun getTodayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }

    private fun String?.toMinutes(): Int {
        if (this.isNullOrBlank()) return Int.MAX_VALUE
        val parts = this.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return h * 60 + m
    }
}
