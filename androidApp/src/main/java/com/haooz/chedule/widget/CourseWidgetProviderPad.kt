/** 课程表桌面小组件提供者 (Pad 2×1 版) */
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

class CourseWidgetProviderPad : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.haooz.chedule.UPDATE_WIDGET_PAD"


        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, CourseWidgetProviderPad::class.java).apply {
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
                ComponentName(context, CourseWidgetProviderPad::class.java)
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
        val appearance = WidgetWallpaperStore.loadAppearance(WidgetWallpaperStore.KEY_TODAY)
        val dark = WidgetWallpaperRender.resolveDark(context, WidgetWallpaperStore.KEY_TODAY)
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
        val prefix = if (showTomorrow) AndroidWidgetStr.tomorrowCourses() else CommonStr.today()
        // 标题用日历日：调休只影响「上哪套课」，预告仍应写真实的明天/今天
        val titleDay = resolution.calendarDayOfWeek
        val titleText = "$prefix / ${dayNames[titleDay - 1]}"
        val weekText = when {
            isHoliday -> AndroidWidgetStr.onBreak()
            currentWeek < 1 -> AndroidWidgetStr.notStarted()
            else -> CommonStr.weekN(targetWeek)
        }

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

        val slotSig = displayCourses.joinToString("|") { c ->
            val start = getCourseStartTime(c, repository) ?: ""
            val end = getCourseEndTime(c, repository) ?: ""
            val remaining = if (showTomorrow) null else getRemainingMinutes(start, end, currentMinutes)
            "${c.id}:${c.name}:$start-$end:${c.colorRes}:${remaining ?: -1}:${buildCourseInfo(c)}"
        }
        val emptyText = if (displayCourses.isEmpty()) {
            when {
                isHoliday -> AndroidWidgetStr.vacationNoClasses()
                currentWeek < 1 -> AndroidWidgetStr.semesterNotStarted()
                resolution.isHolidayDate -> AndroidWidgetStr.vacationNoClasses()
                showTomorrow -> AndroidWidgetStr.noClassesTomorrow()
                todayCourses.isEmpty() -> AndroidWidgetStr.noClassesToday()
                else -> AndroidWidgetStr.allClassesFinished()
            }
        } else ""
        val signature = "${dark}|${repository.getWidgetPaddingMode()}|$titleText|$weekText|$slotSig|$emptyText|" +
            WidgetWallpaperRender.signaturePart(WidgetWallpaperStore.KEY_TODAY) + "|" + WidgetMonetColors.signature(monet)
        if (WidgetUpdateCache.shouldSkip("course_pad_$appWidgetId", signature)) return

        val views = RemoteViews(context.packageName, R.layout.widget_course_reminder_standard)
        WidgetWallpaperRender.apply(
            views = views,
            context = context,
            key = WidgetWallpaperStore.KEY_TODAY,
            appWidgetId = appWidgetId,
            imageId = R.id.widget_wallpaper,
            containerId = R.id.widget_container,
            normalBackgroundRes = R.drawable.widget_background,
            monet = monet,
        )
        WidgetTextSizes.applyCourseReminder(views, appearance.textScale)
        if (WidgetWallpaperRender.hasWallpaper(WidgetWallpaperStore.KEY_TODAY) || monet != null) {
            WidgetTextSizes.applyTextColors(views, context, dark, courseReminder = true, monet = monet)
        }
        views.setTextViewText(R.id.widget_title, titleText)
        views.setTextViewText(R.id.widget_week, weekText)

        if (displayCourses.isEmpty()) {
            views.setViewVisibility(R.id.widget_course1, View.GONE)
            views.setViewVisibility(R.id.widget_course2, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty_text, emptyText)
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            views.setViewVisibility(R.id.widget_course1, View.VISIBLE)
            views.setViewVisibility(R.id.widget_course2, View.VISIBLE)

            val c1 = displayCourses[0]
            views.setViewVisibility(R.id.widget_course1, View.VISIBLE)
            views.setTextViewText(R.id.widget_name1, c1.name)
            val start1 = getCourseStartTime(c1, repository) ?: ""
            val end1 = getCourseEndTime(c1, repository) ?: ""
            views.setTextViewText(R.id.widget_time_start1, start1)
            views.setTextViewText(R.id.widget_time_end1, end1)
            val remaining1 = if (showTomorrow) null else getRemainingMinutes(start1, end1, currentMinutes)
            // 色条位图只有胶囊本身，四角透明；填底色会在半透明卡片上露出方角
            views.setBitmap(R.id.widget_color1, "setImageBitmap",
                createColorBarBitmap(context, c1.colorRes.toInt()))
            views.setViewVisibility(R.id.widget_now1, if (remaining1 != null) View.VISIBLE else View.GONE)
            if (remaining1 != null) views.setTextViewText(R.id.widget_now1, AndroidWidgetStr.endsInN(remaining1))
            WidgetWallpaperRender.applyCardBackground(
                views, R.id.widget_course1, WidgetWallpaperStore.KEY_TODAY, dark,
                active = remaining1 != null, monet = monet,
            )
            views.setTextViewText(R.id.widget_info1, buildCourseInfo(c1))

            if (displayCourses.size >= 2) {
                val c2 = displayCourses[1]
                views.setViewVisibility(R.id.widget_color2, View.VISIBLE)
                views.setTextViewText(R.id.widget_name2, c2.name)
                val start2 = getCourseStartTime(c2, repository) ?: ""
                val end2 = getCourseEndTime(c2, repository) ?: ""
                views.setTextViewText(R.id.widget_time_start2, start2)
                views.setTextViewText(R.id.widget_time_end2, end2)
                val remaining2 = if (showTomorrow) null else getRemainingMinutes(start2, end2, currentMinutes)
                views.setBitmap(R.id.widget_color2, "setImageBitmap",
                    createColorBarBitmap(context, c2.colorRes.toInt()))
                views.setViewVisibility(R.id.widget_now2, if (remaining2 != null) View.VISIBLE else View.GONE)
                if (remaining2 != null) views.setTextViewText(R.id.widget_now2, AndroidWidgetStr.endsInN(remaining2))
                WidgetWallpaperRender.applyCardBackground(
                    views, R.id.widget_course2, WidgetWallpaperStore.KEY_TODAY, dark,
                    active = remaining2 != null, monet = monet,
                )
                views.setTextViewText(R.id.widget_info2, buildCourseInfo(c2))
            } else {
                // 无课时直接隐藏色条，不再塞占位位图，避免灰色竖杆/矩形残留
                views.setViewVisibility(R.id.widget_color2, View.GONE)
                views.setTextViewText(R.id.widget_name2, "")
                views.setTextViewText(R.id.widget_time_start2, "")
                views.setTextViewText(R.id.widget_time_end2, "")
                views.setTextViewText(R.id.widget_info2, "")
                views.setViewVisibility(R.id.widget_now2, View.GONE)
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
        appWidgetIds.forEach { WidgetUpdateCache.invalidateWidget("course_pad_$it") }
    }

    private fun getRemainingMinutes(startTime: String, endTime: String, currentMinutes: Int): Int? {
        val startParts = startTime.split(":")
        val endParts = endTime.split(":")
        if (startParts.size != 2 || endParts.size != 2) return null
        val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
        val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
        // 跨午夜课按下课 + 1440 算，否则 23:30 的「50 分钟后下课」会算成负数
        return if (CourseTimeSpan.isActiveAt(startMinutes, endMinutes, currentMinutes)) {
            CourseTimeSpan.endOffsetMinutes(startMinutes, endMinutes) - currentMinutes
        } else null
    }

    private fun getCourseStartTime(course: Course, repository: CourseRepository): String? =
        CourseTimeResolver.getStartTime(course, repository)

    private fun getCourseEndTime(course: Course, repository: CourseRepository): String? =
        CourseTimeResolver.getEndTime(course, repository)

    /**
     * 色条位图：整张图就是一条胶囊，四角留透明。
     *
     * 以前是「整张填不透明卡片底色，再在上面画胶囊」—— 卡片底色现在是半透明的，
     * 那四个角的纯色就会从胶囊边上戳出来，看着像方尖。
     */
    private fun createColorBarBitmap(context: Context, color: Int): Bitmap {
        val density = WidgetTextSizes.deviceDensity(context)
        val width = (4 * density).toInt().coerceAtLeast(1)
        val height = (28 * density).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        // 端头半径取半宽 = 正好一个胶囊
        val radius = width / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        return bitmap
    }

    /** 拼接课程信息：节次（自定义时间课程跳过）+ 教室 + 教师，非空项间用「｜」分隔 */
    private fun buildCourseInfo(course: Course): String = buildList {
        if (!course.hasValidCustomTime()) add(course.timeDisplayTextLocalized())
        if (course.classroom.isNotEmpty()) add(course.classroom)
        if (course.teacher.isNotEmpty()) add(course.teacher)
    }.joinToString(AndroidWidgetStr.infoSeparator())

    private fun String?.toMinutes(): Int {
        if (this.isNullOrBlank()) return Int.MAX_VALUE
        val parts = this.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return h * 60 + m
    }
}
