/** 课程表桌面小组件提供者 (2×2 标准版) */
package com.haooz.chedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
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

class CourseWidgetProviderStandard : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.haooz.chedule.UPDATE_WIDGET_STANDARD"

        /** ColorOS 拉伸到 3/4 格时会把小部件内容整体缩小的反补偿倍率 */
        private const val COLOR_OS_CONTENT_SCALE = 1.18f

        // 卡位相关的 view id，数组顺序即第 1~5 张卡
        private val cardIds = intArrayOf(
            R.id.widget_course1, R.id.widget_course2, R.id.widget_course3,
            R.id.widget_course4, R.id.widget_course5
        )
        private val nameIds = intArrayOf(
            R.id.widget_name1, R.id.widget_name2, R.id.widget_name3,
            R.id.widget_name4, R.id.widget_name5
        )
        private val startIds = intArrayOf(
            R.id.widget_time_start1, R.id.widget_time_start2, R.id.widget_time_start3,
            R.id.widget_time_start4, R.id.widget_time_start5
        )
        private val endIds = intArrayOf(
            R.id.widget_time_end1, R.id.widget_time_end2, R.id.widget_time_end3,
            R.id.widget_time_end4, R.id.widget_time_end5
        )
        private val infoIds = intArrayOf(
            R.id.widget_info1, R.id.widget_info2, R.id.widget_info3,
            R.id.widget_info4, R.id.widget_info5
        )
        private val colorIds = intArrayOf(
            R.id.widget_color1, R.id.widget_color2, R.id.widget_color3,
            R.id.widget_color4, R.id.widget_color5
        )
        private val nowIds = intArrayOf(
            R.id.widget_now1, R.id.widget_now2, R.id.widget_now3,
            R.id.widget_now4, R.id.widget_now5
        )

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

    /** 拉伸高度（2/3/4 格）后必须重算卡位数量，系统不会为此再发一次 onUpdate */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        // 尺寸变了必须重建：内容签名里虽然带了尺寸，这里再兜一层
        WidgetUpdateCache.invalidateWidget("course_std_$appWidgetId")
        updateWidget(context, appWidgetManager, appWidgetId)
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
        val repository = CourseRepository()
        // 有壁纸且档位是「跟随壁纸」时，深浅按照片锁，不跟系统
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
        // 标题用日历日：调休只影响「上哪套课」，预告仍应写真实的明天/今天（如周日补周二课 → 写周日）
        val titleDay = resolution.calendarDayOfWeek
        val titleText = "$prefix / ${dayNames[titleDay - 1]}"
        val weekText = when {
            isHoliday -> AndroidWidgetStr.onBreak()
            currentWeek < 1 -> AndroidWidgetStr.notStarted()
            else -> CommonStr.weekN(targetWeek)
        }

        // 卡位/课程数随小组件高度变化：2 格=2 / 3 格=3 / 4 格=5
        val rowCount = widgetRowCount(appWidgetManager.getAppWidgetOptions(appWidgetId))
        // ColorOS 拉伸到 3/4 格会把整份内容（字号与间距）一起缩小，按 1.2 倍反补偿
        val contentScale = if (rowCount >= 3 && WidgetTextSizes.isColorOs) COLOR_OS_CONTENT_SCALE else 1f
        val displayCourses = if (showTomorrow) {
            targetCourses.take(rowCount)
        } else {
            todayCourses.filter { course ->
                val end = getCourseEndTime(course, repository) ?: return@filter false
                val endParts = end.split(":")
                if (endParts.size == 2) {
                    val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
                    endMinutes > currentMinutes
                } else false
            }.take(rowCount)
        }

        data class CourseSlot(
            val id: String,
            val name: String,
            val start: String,
            val end: String,
            val info: String,
            val color: Int,
            val remaining: Int?
        )
        val slots = displayCourses.map { c ->
            val start = getCourseStartTime(c, repository) ?: ""
            val end = getCourseEndTime(c, repository) ?: ""
            val remaining = if (showTomorrow) null else getRemainingMinutes(start, end, currentMinutes)
            CourseSlot(c.id, c.name, start, end, buildCourseInfo(c), c.colorRes.toInt(), remaining)
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

        val paddingMode = repository.getWidgetPaddingMode()
        val signature = buildString {
            append(dark).append('|').append(paddingMode).append('|').append(rowCount)
            append('|').append(titleText).append('|').append(weekText)
            append('|').append(WidgetWallpaperRender.signaturePart(WidgetWallpaperStore.KEY_TODAY))
            append('|').append(WidgetMonetColors.signature(monet))
            append('|').append(
                WidgetWallpaperRender.sizeSignature(
                    appWidgetManager, appWidgetId, WidgetWallpaperRender.WidgetSize(250, 157)
                )
            )
            if (slots.isEmpty()) {
                append('|').append(emptyText)
            } else {
                slots.forEach { s ->
                    append('|').append(s.id).append(':').append(s.name)
                        .append(':').append(s.start).append('-').append(s.end)
                        .append(':').append(s.info).append(':').append(s.color)
                        .append(':').append(s.remaining ?: -1)
                }
            }
        }
        if (WidgetUpdateCache.shouldSkip("course_std_$appWidgetId", signature)) return

        fun buildViews(size: WidgetWallpaperRender.WidgetSize): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_course_reminder_standard)
        val (insetTopDp, insetBottomDp) = applyWidgetMode(views, context, repository)
        // 外层圆角：HyperOS（小米系）按系统规范 24dp，其余 20dp
        val containerBackground = if (WidgetTextSizes.isXiaomi) {
            R.drawable.widget_background_xiaomi
        } else {
            R.drawable.widget_background
        }
        views.setInt(R.id.widget_container, "setBackgroundResource", containerBackground)
        WidgetWallpaperRender.apply(
            views = views,
            context = context,
            key = WidgetWallpaperStore.KEY_TODAY,
            appWidgetId = appWidgetId,
            imageId = R.id.widget_wallpaper,
            containerId = R.id.widget_container,
            normalBackgroundRes = containerBackground,
            insetTopDp = insetTopDp,
            insetBottomDp = insetBottomDp,
            size = size,
            monet = monet,
        )
        WidgetTextSizes.applyCourseReminder(views, contentScale * appearance.textScale)
        // 有壁纸时深浅按照片锁死，文字颜色必须显式下发（否则仍跟系统的 values-night）
        if (WidgetWallpaperRender.hasWallpaper(WidgetWallpaperStore.KEY_TODAY) || monet != null) {
            WidgetTextSizes.applyTextColors(views, context, dark, courseReminder = true, monet = monet)
        }
        applyScaledMetrics(views, context, contentScale)
        views.setTextViewText(R.id.widget_title, titleText)
        views.setTextViewText(R.id.widget_week, weekText)

        if (slots.isEmpty()) {
            cardIds.forEach { views.setViewVisibility(it, View.GONE) }
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty_text, emptyText)
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            cardIds.indices.forEach { index ->
                val visible = index < rowCount
                views.setViewVisibility(cardIds[index], if (visible) View.VISIBLE else View.GONE)
                if (!visible) return@forEach
                val slot = slots.getOrNull(index)
                if (slot == null) {
                    // 空卡位：只清内容、藏色条，不再塞占位位图，避免灰色竖杆/矩形残留
                    views.setTextViewText(nameIds[index], "")
                    views.setTextViewText(startIds[index], "")
                    views.setTextViewText(endIds[index], "")
                    views.setTextViewText(infoIds[index], "")
                    views.setViewVisibility(colorIds[index], View.GONE)
                    views.setViewVisibility(nowIds[index], View.GONE)
                    WidgetWallpaperRender.applyCardBackground(
                        views, cardIds[index], WidgetWallpaperStore.KEY_TODAY, dark,
                        active = false, monet = monet,
                    )
                    return@forEach
                }
                views.setTextViewText(nameIds[index], slot.name)
                views.setTextViewText(startIds[index], slot.start)
                views.setTextViewText(endIds[index], slot.end)
                views.setTextViewText(infoIds[index], slot.info)
                // 色条位图只有胶囊本身，四角透明；填底色会在半透明卡片上露出方角
                views.setBitmap(colorIds[index], "setImageBitmap",
                    createColorBarBitmap(context, slot.color))
                views.setViewVisibility(colorIds[index], View.VISIBLE)
                views.setViewVisibility(nowIds[index], if (slot.remaining != null) View.VISIBLE else View.GONE)
                if (slot.remaining != null) views.setTextViewText(nowIds[index], AndroidWidgetStr.endsInN(slot.remaining))
                WidgetWallpaperRender.applyCardBackground(
                    views, cardIds[index], WidgetWallpaperStore.KEY_TODAY, dark,
                    active = slot.remaining != null, monet = monet,
                )
            }
        }

        // 任意位置点击 = 打开应用 + 刷新全部小部件
        views.setOnClickPendingIntent(
            R.id.widget_standard_root,
            WidgetClickHelper.launchAndRefreshPending(context)
        )

            return views
        }
        val sizes = WidgetWallpaperRender.renderSizes(
            manager = appWidgetManager,
            appWidgetId = appWidgetId,
            fallback = WidgetWallpaperRender.WidgetSize(250, 157),
        )
        appWidgetManager.updateAppWidget(
            appWidgetId,
            WidgetWallpaperRender.responsiveViews(sizes.map { it to buildViews(it) }),
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetUpdateCache.invalidateWidget("course_std_$it") }
    }

    private fun applyWidgetMode(
        views: RemoteViews,
        context: Context,
        repository: CourseRepository
    ): Pair<Float, Float> {
        // 0=标准(0/0), 1=4×6(12/14), 2=4×7(8/10), 3=4×6无字(21/5), 4=4×7无字(17/2)
        val (top, bottom) = when (repository.getWidgetPaddingMode()) {
            1 -> 12f to 14f
            2 -> 8f to 10f
            3 -> 21f to 5f
            4 -> 17f to 2f
            else -> 0f to 0f
        }
        views.setViewPadding(
            R.id.widget_standard_root,
            0,
            WidgetTextSizes.dpToPx(context, top).toInt(),
            0,
            WidgetTextSizes.dpToPx(context, bottom).toInt()
        )
        return top to bottom
    }

    /**
     * 小组件高度档位 → 卡位/课程行数：2 格=2 / 3 格=3 / 4 格=5。
     * 真机实测 OPTION_APPWIDGET_MIN_HEIGHT：2 格=157dp、3 格=295dp、4 格=412dp，
     * 阈值取相邻两档中点 226/353。
     */
    private fun widgetRowCount(options: android.os.Bundle): Int {
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 157)
        return when {
            heightDp >= 353 -> 5
            heightDp >= 226 -> 3
            else -> 2
        }
    }

    /**
     * 间距随倍率缩放（与字号一起），用于 ColorOS 拉伸后的反补偿。
     * 每次都显式下发全部数值：从缩放档回到 1× 时会覆盖回原始值，不残留上次的缩放。
     */
    private fun applyScaledMetrics(views: RemoteViews, context: Context, scale: Float) {
        val unit = TypedValue.COMPLEX_UNIT_DIP
        // setViewPadding 的带单位重载是 API 37 才有的，这里统一用 px 版（API 1 起即可用）
        fun px(value: Float): Int = WidgetTextSizes.dpToPx(context, value * scale).toInt()
        fun padding(id: Int, start: Float, top: Float, end: Float, bottom: Float) {
            views.setViewPadding(id, px(start), px(top), px(end), px(bottom))
        }
        fun margin(id: Int, type: Int, dp: Float) {
            views.setLayoutMarginCompat(id, type, dp * scale, unit)
        }

        padding(R.id.widget_container, 11f, 9f, 11f, 3f)
        margin(R.id.widget_header, RemoteViews.MARGIN_START, 4f)
        margin(R.id.widget_header, RemoteViews.MARGIN_END, 4f)
        margin(R.id.widget_header, RemoteViews.MARGIN_BOTTOM, 8f)

        cardIds.indices.forEach { index ->
            val card = cardIds[index]
            padding(card, 12f, 4f, 12f, 4f)
            margin(card, RemoteViews.MARGIN_BOTTOM, 8f)
            margin(infoIds[index], RemoteViews.MARGIN_TOP, 2f)
            views.setLayoutWidthCompat(colorIds[index], 4f * scale, unit)
            margin(colorIds[index], RemoteViews.MARGIN_TOP, 6f)
            margin(colorIds[index], RemoteViews.MARGIN_BOTTOM, 6f)
        }
    }

    private fun isCourseActive(startTime: String, endTime: String, currentMinutes: Int): Boolean {
        val startParts = startTime.split(":")
        val endParts = endTime.split(":")
        if (startParts.size != 2 || endParts.size != 2) return false
        val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
        val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
        return CourseTimeSpan.isActiveAt(startMinutes, endMinutes, currentMinutes)
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
