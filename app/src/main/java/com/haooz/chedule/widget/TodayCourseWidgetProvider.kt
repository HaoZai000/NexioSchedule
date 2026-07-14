/** 今日课程桌面小组件提供者 (2×2) */
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
import com.haooz.chedule.R
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import java.util.Calendar

class TodayCourseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.haooz.chedule.UPDATE_TODAY_WIDGET"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, TodayCourseWidgetProvider::class.java).apply {
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
                ComponentName(context, TodayCourseWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_today_course)

        val repository = CourseRepository(context)
        val currentWeek = repository.getCurrentWeek()
        val today = getTodayOfWeek()
        val courses = repository.getAllCourses()
        val todayCourses = courses.filter { it.dayOfWeek == today && it.isActiveInWeek(currentWeek) }
            .sortedBy { it.startSection }

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
            .sortedBy { it.startSection }

        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        val isHoliday = currentWeek > totalWeeks || (currentWeek >= 1 && currentWeek > lastWeekWithCourses)

        val titleText = if (showTomorrow) "明天" else dayNames[dayOfWeek - 1]
        views.setTextViewText(R.id.widget_title, titleText)
        val weekText = when {
            isHoliday -> "放假中"
            currentWeek < 1 -> "未开始"
            else -> "第${currentWeek}周"
        }
        views.setTextViewText(R.id.widget_week, weekText)

        val displayCourse: Course?
        val remainingCourses: List<Course>
        val allCourses: List<Course>

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
            views.setViewVisibility(R.id.widget_course_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)

            views.setTextViewText(R.id.widget_course_name, displayCourse.name)
            val startTime = getCourseStartTime(displayCourse, repository) ?: ""
            val endTime = getCourseEndTime(displayCourse, repository) ?: ""
            views.setTextViewText(R.id.widget_course_time, "$startTime-$endTime")
            views.setTextViewText(R.id.widget_course_location, displayCourse.classroom)

            val remainingCount = remainingCourses.size
            val remainingText = if (remainingCount > 0) "还有${remainingCount}节课" else "没有其他课程"
            views.setTextViewText(R.id.widget_remaining_text, remainingText)

            val dotIds = listOf(
                R.id.widget_dot1, R.id.widget_dot2, R.id.widget_dot3, R.id.widget_dot4,
                R.id.widget_dot5, R.id.widget_dot6, R.id.widget_dot7, R.id.widget_dot8
            )

            val dotCourses = if (!showTomorrow && displayCourse != null) {
                val start = getCourseStartTime(displayCourse, repository)
                val end = getCourseEndTime(displayCourse, repository)
                if (start != null && end != null) {
                    val startParts = start.split(":")
                    val endParts = end.split(":")
                    if (startParts.size == 2 && endParts.size == 2) {
                        val startMin = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
                        val endMin = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
                        if (currentMinutes in startMin until endMin) {
                            allCourses.filter { it.id != displayCourse.id }
                        } else allCourses
                    } else allCourses
                } else allCourses
            } else allCourses

            for (i in dotIds.indices) {
                if (i < dotCourses.size) {
                    views.setViewVisibility(dotIds[i], View.VISIBLE)
                    views.setImageViewBitmap(dotIds[i], createCircleBitmap(dotCourses[i].colorRes.toInt()))
                } else {
                    views.setViewVisibility(dotIds[i], View.GONE)
                }
            }
        } else {
            views.setViewVisibility(R.id.widget_course_content, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            val emptyText = when {
                isHoliday -> "假期中，暂无课程"
                currentWeek < 1 -> "学期暂未开始"
                showTomorrow -> "明日无课"
                todayCourses.isEmpty() -> "今日无课"
                else -> "今日课程已上完"
            }
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

    private fun createCircleBitmap(color: Int): Bitmap {
        val size = 24
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }

    private fun getCourseStartTime(course: Course, repository: CourseRepository): String? {
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

    private fun getTodayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }
}
