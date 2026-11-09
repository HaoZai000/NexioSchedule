/** 今日课程桌面小组件提供者 (Pad 1×1 版) */
package com.haooz.chedule.widget

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
import com.haooz.chedule.data.WidgetWallpaperStore
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.CourseTimeResolver
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.CourseTimeSpan
import com.haooz.chedule.i18n.AndroidWidgetStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.reminder.CourseReminderHelper
import java.util.Calendar

class TodayCourseWidgetProviderPad : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.haooz.chedule.UPDATE_TODAY_WIDGET_PAD"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, TodayCourseWidgetProviderPad::class.java).apply {
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
                ComponentName(context, TodayCourseWidgetProviderPad::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val repository = CourseRepository()
        val appearance = WidgetWallpaperStore.loadAppearance(WidgetWallpaperStore.KEY_SMALL)
        val dark = WidgetWallpaperRender.resolveDark(context, WidgetWallpaperStore.KEY_SMALL)
        val monet = WidgetMonetColors.palette(context, dark)

        val currentWeek = repository.getLiveTeachingWeek()
        val todayCourses = CourseReminderHelper.getTodayCourses(context)

        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val isNextDayReminderEnabled = repository.getNextDayReminder()
        val reminderMinutes = repository.getNextDayReminderHour() * 60 + repository.getNextDayReminderMinute()
        val todayCoursesFinished = if (todayCourses.isNotEmpty()) {
            val lastEndTime = CourseReminderHelper.getLatestCourseEndTime(todayCourses, repository)
            if (lastEndTime != null) {
                val parts = lastEndTime.split(":")
                if (parts.size == 2) {
                    val endMinutes = (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                    currentMinutes >= endMinutes
                } else true
            } else true
        } else true
        val showTomorrow = isNextDayReminderEnabled && currentMinutes >= reminderMinutes && todayCoursesFinished

        val dayNames = listOf(
            CommonStr.weekdayMon(), CommonStr.weekdayTue(), CommonStr.weekdayWed(),
            CommonStr.weekdayThu(), CommonStr.weekdayFri(), CommonStr.weekdaySat(),
            CommonStr.weekdaySun()
        )
        // 今日/明日统一走 resolveDaySchedule：节假日末日例外按节次保留课程、调休按映射查课
        val resolution = CourseReminderHelper.resolveDaySchedule(context, forTomorrow = showTomorrow)
        val targetWeek = resolution.displayWeek
        val targetCourses = resolution.courses

        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        val isHoliday = currentWeek > totalWeeks || (currentWeek >= 1 && currentWeek > lastWeekWithCourses)

        val titleText = if (showTomorrow) {
            AndroidWidgetStr.tomorrow()
        } else {
            dayNames[(resolution.calendarDayOfWeek - 1).coerceIn(0, 6)]
        }
        val weekText = when {
            isHoliday -> AndroidWidgetStr.onBreak()
            currentWeek < 1 -> AndroidWidgetStr.notStarted()
            else -> CommonStr.weekN(targetWeek)
        }

        val displayCourse: Course?
        val remainingCourses: List<Course>
        val allCourses: List<Course>
        val emptyText: String
        val startTime: String
        val endTime: String

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
        } else {
            emptyText = when {
                isHoliday -> AndroidWidgetStr.vacationNoClasses()
                currentWeek < 1 -> AndroidWidgetStr.semesterNotStarted()
                resolution.isHolidayDate -> AndroidWidgetStr.vacationNoClasses()
                showTomorrow -> AndroidWidgetStr.noClassesTomorrow()
                todayCourses.isEmpty() -> AndroidWidgetStr.noClassesToday()
                else -> AndroidWidgetStr.allClassesFinished()
            }
            startTime = ""
            endTime = ""
        }

        // 上课瞬间圆点列表会从 allCourses 变为去掉当前课，签名必须含 is_now 与圆点摘要
        val inCourse = displayCourse != null && run {
            val sp = startTime.split(":")
            val ep = endTime.split(":")
            sp.size == 2 && ep.size == 2 && CourseTimeSpan.isActiveAt(
                (sp[0].toIntOrNull() ?: 0) * 60 + (sp[1].toIntOrNull() ?: 0),
                (ep[0].toIntOrNull() ?: 0) * 60 + (ep[1].toIntOrNull() ?: 0),
                currentMinutes,
            )
        }
        val dotCourses = if (displayCourse != null && !showTomorrow && inCourse) {
            allCourses.filter { it.id != displayCourse.id }
        } else if (displayCourse != null) {
            allCourses
        } else {
            emptyList()
        }

        val signature = buildString {
            append(dark).append('|').append(repository.getWidgetPaddingMode())
            append('|').append(WidgetWallpaperRender.signaturePart(WidgetWallpaperStore.KEY_SMALL))
            append('|').append(WidgetMonetColors.signature(monet))
            append('|').append(titleText).append('|').append(weekText)
            if (displayCourse != null) {
                append('|').append(displayCourse.id).append('|').append(displayCourse.name)
                append('|').append(startTime).append('-').append(endTime)
                append('|').append(displayCourse.classroom)
                append('|').append(remainingCourses.size)
                append('|').append(if (inCourse) "in" else "out")
                append('|').append(dotCourses.joinToString(",") { "${it.id}:${it.colorRes}" })
            } else {
                append('|').append(emptyText)
            }
        }
        if (WidgetUpdateCache.shouldSkip("today_pad_$appWidgetId", signature)) return

        val views = RemoteViews(context.packageName, R.layout.widget_today_course_standard)
        WidgetWallpaperRender.apply(
            views = views,
            context = context,
            key = WidgetWallpaperStore.KEY_SMALL,
            appWidgetId = appWidgetId,
            imageId = R.id.widget_wallpaper,
            containerId = R.id.widget_today_container,
            normalBackgroundRes = R.drawable.widget_today_course_background,
            monet = monet,
        )
        WidgetTextSizes.applyTodayCourse(views, appearance.textScale)
        if (WidgetWallpaperRender.hasWallpaper(WidgetWallpaperStore.KEY_SMALL) || monet != null) {
            WidgetTextSizes.applyTextColors(views, context, dark, courseReminder = false, monet = monet)
        }
        views.setTextViewText(R.id.widget_title, titleText)
        views.setTextViewText(R.id.widget_week, weekText)

        if (displayCourse != null) {
            views.setViewVisibility(R.id.widget_course_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)

            views.setTextViewText(R.id.widget_course_name, displayCourse.name)
            views.setTextViewText(R.id.widget_course_time, "$startTime-$endTime")
            views.setTextViewText(R.id.widget_course_location, displayCourse.classroom)

            val remainingCount = remainingCourses.size
            val remainingText = if (remainingCount > 0) AndroidWidgetStr.moreClassesN(remainingCount) else AndroidWidgetStr.noOtherClasses()
            views.setTextViewText(R.id.widget_remaining_text, remainingText)

            val dotIds = listOf(
                R.id.widget_dot1, R.id.widget_dot2, R.id.widget_dot3, R.id.widget_dot4,
                R.id.widget_dot5, R.id.widget_dot6, R.id.widget_dot7, R.id.widget_dot8
            )

            for (i in dotIds.indices) {
                if (i < dotCourses.size) {
                    views.setViewVisibility(dotIds[i], View.VISIBLE)
                    views.setImageViewBitmap(
                        dotIds[i],
                        createCircleBitmap(context, dotCourses[i].colorRes.toInt())
                    )
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

        // 任意位置点击 = 打开应用 + 刷新全部小部件
        views.setOnClickPendingIntent(
            R.id.widget_standard_root,
            WidgetClickHelper.launchAndRefreshPending(context)
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetUpdateCache.invalidateWidget("today_pad_$it") }
    }

    /** 色球位图：整张图就是一个圆，四角留透明（填底色会在半透明卡片上露出方角）。 */
    private fun createCircleBitmap(context: Context, color: Int): Bitmap {
        val size = (7 * WidgetTextSizes.deviceDensity(context)).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }

    private fun getCourseStartTime(course: Course, repository: CourseRepository): String? =
        CourseTimeResolver.getStartTime(course, repository)

    private fun getCourseEndTime(course: Course, repository: CourseRepository): String? =
        CourseTimeResolver.getEndTime(course, repository)

    private fun String?.toMinutes(): Int {
        if (this.isNullOrBlank()) return Int.MAX_VALUE
        val parts = this.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return h * 60 + m
    }
}
