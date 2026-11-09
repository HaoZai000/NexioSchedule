/** 今明课程桌面小组件提供者 (4×2：左列=今天 / 右列=明天) */
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

class TodayTomorrowCourseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.haooz.chedule.UPDATE_WIDGET_TODAY_TOMORROW"

        /** ColorOS 拉伸到 3/4 格时会把小部件内容整体缩小的反补偿倍率 */
        private const val COLOR_OS_CONTENT_SCALE = 1.18f

        // 卡位 id：列优先配对，奇数位(1,3,5,7,9)=今天列自上而下，偶数位(2,4,6,8,10)=明天列
        private val cardIds = intArrayOf(
            R.id.widget_course1, R.id.widget_course2, R.id.widget_course3,
            R.id.widget_course4, R.id.widget_course5, R.id.widget_course6,
            R.id.widget_course7, R.id.widget_course8, R.id.widget_course9,
            R.id.widget_course10
        )
        private val nameIds = intArrayOf(
            R.id.widget_name1, R.id.widget_name2, R.id.widget_name3,
            R.id.widget_name4, R.id.widget_name5, R.id.widget_name6,
            R.id.widget_name7, R.id.widget_name8, R.id.widget_name9,
            R.id.widget_name10
        )
        // 卡片第二行：课程时间（起-止，单行）
        private val timeIds = intArrayOf(
            R.id.widget_course_time1, R.id.widget_course_time2, R.id.widget_course_time3,
            R.id.widget_course_time4, R.id.widget_course_time5, R.id.widget_course_time6,
            R.id.widget_course_time7, R.id.widget_course_time8, R.id.widget_course_time9,
            R.id.widget_course_time10
        )
        private val infoIds = intArrayOf(
            R.id.widget_info1, R.id.widget_info2, R.id.widget_info3,
            R.id.widget_info4, R.id.widget_info5, R.id.widget_info6,
            R.id.widget_info7, R.id.widget_info8, R.id.widget_info9,
            R.id.widget_info10
        )
        private val colorIds = intArrayOf(
            R.id.widget_color1, R.id.widget_color2, R.id.widget_color3,
            R.id.widget_color4, R.id.widget_color5, R.id.widget_color6,
            R.id.widget_color7, R.id.widget_color8, R.id.widget_color9,
            R.id.widget_color10
        )


        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, TodayTomorrowCourseWidgetProvider::class.java).apply {
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

    /** 拉伸高度（2/3/4 格）后必须重算每列课程数，系统不会为此再发一次 onUpdate */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        // 尺寸变了必须重建：内容签名里虽然带了尺寸，这里再兜一层
        WidgetUpdateCache.invalidateWidget("course_tt_$appWidgetId")
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TodayTomorrowCourseWidgetProvider::class.java)
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
        val appearance = WidgetWallpaperStore.loadAppearance(WidgetWallpaperStore.KEY_TODAY_TOMORROW)
        val dark = WidgetWallpaperRender.resolveDark(
            context,
            WidgetWallpaperStore.KEY_TODAY_TOMORROW
        )
        val monet = WidgetMonetColors.palette(context, dark)

        val currentWeek = repository.getLiveTeachingWeek()
        // 今天/明天两套日程：getTodayCourses 即 resolveDaySchedule(false).courses；
        // 明天列走 resolveDaySchedule(forTomorrow=true)（含调休/节假日映射），
        // 且不受「次日提醒」开关拦截——本组件左右两列固定同时显示
        val todayResolution = CourseReminderHelper.resolveDaySchedule(context, forTomorrow = false)
        val tomorrowResolution = CourseReminderHelper.resolveDaySchedule(context, forTomorrow = true)
        val todayCourses = todayResolution.courses

        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        val isHoliday = currentWeek > totalWeeks || (currentWeek >= 1 && currentWeek > lastWeekWithCourses)

        val dayNames = listOf(
            CommonStr.weekdayMon(), CommonStr.weekdayTue(), CommonStr.weekdayWed(),
            CommonStr.weekdayThu(), CommonStr.weekdayFri(), CommonStr.weekdaySat(),
            CommonStr.weekdaySun()
        )
        // 表头用各自日历日：周日补周二课 → 明天列仍写周日
        val todayHeaderText = "${CommonStr.today()} / ${dayNames[todayResolution.calendarDayOfWeek - 1]}"
        val tomorrowHeaderText = "${AndroidWidgetStr.tomorrow()} / ${dayNames[tomorrowResolution.calendarDayOfWeek - 1]}"

        // 每列课程数随小组件高度变化：2 格=2 / 3 格=3 / 4 格=5
        val rowCount = widgetRowCount(appWidgetManager.getAppWidgetOptions(appWidgetId))
        // ColorOS 拉伸到 3/4 格会把整份内容（字号与间距）一起缩小，按 1.2 倍反补偿
        val contentScale = if (rowCount >= 3 && WidgetTextSizes.isColorOs) COLOR_OS_CONTENT_SCALE else 1f

        // 今天列：照抄旧组件——已结束的课不显示
        val todayDisplay = todayCourses.filter { course ->
            val end = getCourseEndTime(course, repository) ?: return@filter false
            val endParts = end.split(":")
            if (endParts.size == 2) {
                val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
                endMinutes > currentMinutes
            } else false
        }.take(rowCount)
        // 明天列：全天课程
        val tomorrowDisplay = tomorrowResolution.courses.take(rowCount)

        data class CourseSlot(
            val id: String,
            val name: String,
            val start: String,
            val end: String,
            val timeText: String,
            val info: String,
            val color: Int,
            val remaining: Int?
        )
        fun slotsOf(courses: List<Course>, allowActive: Boolean) = courses.map { c ->
            val start = getCourseStartTime(c, repository) ?: ""
            val end = getCourseEndTime(c, repository) ?: ""
            // 第二行时间：起-止 单行显示；任一端缺失只显示有的那段
            val timeText = listOf(start, end).filter { it.isNotEmpty() }.joinToString("-")
            // 明天列 allowActive=false：还没上课，永远不变蓝
            val remaining = if (allowActive) getRemainingMinutes(start, end, currentMinutes) else null
            CourseSlot(c.id, c.name, start, end, timeText, buildCourseInfo(c), c.colorRes.toInt(), remaining)
        }
        val todaySlots = slotsOf(todayDisplay, allowActive = true)
        val tomorrowSlots = slotsOf(tomorrowDisplay, allowActive = false)

        // 空态文案：照抄旧组件 when 链，按列各算一份
        fun emptyTextFor(isTomorrow: Boolean, isHolidayDate: Boolean): String = when {
            isHoliday -> AndroidWidgetStr.vacationNoClasses()
            currentWeek < 1 -> AndroidWidgetStr.semesterNotStarted()
            isHolidayDate -> AndroidWidgetStr.vacationNoClasses()
            isTomorrow -> AndroidWidgetStr.noClassesTomorrow()
            todayCourses.isEmpty() -> AndroidWidgetStr.noClassesToday()
            else -> AndroidWidgetStr.allClassesFinished()
        }
        val todayEmptyText = if (todaySlots.isEmpty()) {
            emptyTextFor(isTomorrow = false, isHolidayDate = todayResolution.isHolidayDate)
        } else ""
        val tomorrowEmptyText = if (tomorrowSlots.isEmpty()) {
            emptyTextFor(isTomorrow = true, isHolidayDate = tomorrowResolution.isHolidayDate)
        } else ""

        val paddingMode = repository.getWidgetPaddingMode()
        val signature = buildString {
            append(dark).append('|').append(paddingMode).append('|').append(rowCount)
            append('|').append(todayHeaderText).append('|').append(tomorrowHeaderText)
            append('|').append(todayEmptyText).append('|').append(tomorrowEmptyText)
            append('|').append(
                WidgetWallpaperRender.signaturePart(WidgetWallpaperStore.KEY_TODAY_TOMORROW)
            )
            append('|').append(WidgetMonetColors.signature(monet))
            append('|').append(
                WidgetWallpaperRender.sizeSignature(
                    appWidgetManager, appWidgetId, WidgetWallpaperRender.WidgetSize(250, 157)
                )
            )
            fun slotsSig(slots: List<CourseSlot>) = slots.forEach { s ->
                append('|').append(s.id).append(':').append(s.name)
                    .append(':').append(s.start).append('-').append(s.end)
                    .append(':').append(s.info).append(':').append(s.color)
                    // 无倒计时文案，remaining 只剩「是否正在上课」影响外观（蓝卡），
                    // 签到分钟级变化不再触发重绘
                    .append(':').append(if (s.remaining != null) 1 else 0)
            }
            slotsSig(todaySlots)
            slotsSig(tomorrowSlots)
        }
        if (WidgetUpdateCache.shouldSkip("course_tt_$appWidgetId", signature)) return

        fun buildViews(size: WidgetWallpaperRender.WidgetSize): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today_tomorrow_course)
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
            key = WidgetWallpaperStore.KEY_TODAY_TOMORROW,
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
        if (WidgetWallpaperRender.hasWallpaper(WidgetWallpaperStore.KEY_TODAY_TOMORROW) || monet != null) {
            WidgetTextSizes.applyTextColors(views, context, dark, courseReminder = true, monet = monet)
        }
        applyScaledMetrics(views, context, contentScale)
        views.setTextViewText(R.id.widget_header_today, todayHeaderText)
        views.setTextViewText(R.id.widget_header_tomorrow, tomorrowHeaderText)

        val todayEmpty = todaySlots.isEmpty()
        val tomorrowEmpty = tomorrowSlots.isEmpty()

        // 整列无课：卡片全部隐藏，空态文案覆盖整列、上下居中
        // （与另一列卡片区的垂直中心对齐）
        views.setViewVisibility(R.id.widget_empty_today, if (todayEmpty) View.VISIBLE else View.GONE)
        if (todayEmpty) views.setTextViewText(R.id.widget_empty_today, todayEmptyText)
        views.setViewVisibility(R.id.widget_empty_tomorrow, if (tomorrowEmpty) View.VISIBLE else View.GONE)
        if (tomorrowEmpty) {
            views.setTextViewText(R.id.widget_empty_tomorrow, tomorrowEmptyText)
        }

        cardIds.indices.forEach { index ->
            val isTodayColumn = index % 2 == 0
            val slotIndex = index / 2
            val columnEmpty = if (isTodayColumn) todayEmpty else tomorrowEmpty
            // 超出高度档位的卡位、整列无课时的全部卡片都隐藏
            // （两列各自是独立的 weight 列，隐藏不会让另一列变宽）
            val visible = slotIndex < rowCount && !columnEmpty
            views.setViewVisibility(cardIds[index], if (visible) View.VISIBLE else View.GONE)
            if (!visible) return@forEach
            val slot = (if (isTodayColumn) todaySlots else tomorrowSlots).getOrNull(slotIndex)
            if (slot == null) {
                // 空卡位：只清内容、藏色条与后两行，不再塞占位位图，避免灰色竖杆/矩形残留
                views.setTextViewText(nameIds[index], "")
                views.setTextViewText(timeIds[index], "")
                views.setViewVisibility(timeIds[index], View.GONE)
                views.setTextViewText(infoIds[index], "")
                views.setViewVisibility(infoIds[index], View.GONE)
                views.setViewVisibility(colorIds[index], View.GONE)
                WidgetWallpaperRender.applyCardBackground(
                    views, cardIds[index], WidgetWallpaperStore.KEY_TODAY_TOMORROW, dark,
                    active = false, monet = monet, jinming = true,
                )
                return@forEach
            }
            views.setTextViewText(nameIds[index], slot.name)
            // 第二行时间、第三行教室｜教师：内容为空则隐藏整行
            views.setTextViewText(timeIds[index], slot.timeText)
            views.setViewVisibility(timeIds[index], if (slot.timeText.isNotEmpty()) View.VISIBLE else View.GONE)
            views.setTextViewText(infoIds[index], slot.info)
            views.setViewVisibility(infoIds[index], if (slot.info.isNotEmpty()) View.VISIBLE else View.GONE)
            // 色条位图只有胶囊本身，四角透明；填底色会在半透明卡片上露出方角
            views.setBitmap(colorIds[index], "setImageBitmap",
                createColorBarBitmap(context, slot.color))
            views.setViewVisibility(colorIds[index], View.VISIBLE)
            // 无倒计时文案：正在上课仅体现为卡片变蓝
            WidgetWallpaperRender.applyCardBackground(
                views, cardIds[index], WidgetWallpaperStore.KEY_TODAY_TOMORROW, dark,
                active = slot.remaining != null, monet = monet, jinming = true,
            )
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
        appWidgetIds.forEach { WidgetUpdateCache.invalidateWidget("course_tt_$it") }
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
     * 小组件高度档位 → 每列课程数：2 格=2 / 3 格=3 / 4 格=5。
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

        padding(R.id.widget_container, 9f, 9f, 9f, 2f)
        // 列表头行：左右两个 slot 平分，左 slot 右侧留 8dp 缝（与卡片列间缝一致）
        margin(R.id.widget_day_header, RemoteViews.MARGIN_START, 4f)
        margin(R.id.widget_day_header, RemoteViews.MARGIN_END, 4f)
        margin(R.id.widget_day_header, RemoteViews.MARGIN_BOTTOM, 6f)
        margin(R.id.widget_header_slot_today, RemoteViews.MARGIN_END, 8f)
        // 明天表头整体右移 4dp（用 padding 而非 margin：不参与 weight 分配，偏移量精确）
        padding(R.id.widget_header_slot_tomorrow, 4f, 0f, 0f, 0f)

        // 两列间距 8dp：挂在今天列容器上（卡片本身不再带左右外边距）
        margin(R.id.widget_column_today, RemoteViews.MARGIN_END, 8f)

        cardIds.indices.forEach { index ->
            val card = cardIds[index]
            // 色条左移：左侧内边距 8dp（右侧仍 12dp），文字块随之获得 4dp 额外宽度
            padding(card, 8f, 4f, 12f, 4f)
            margin(card, RemoteViews.MARGIN_BOTTOM, 7f)
            // 三行行距靠 includeFontPadding=false 收紧，行间不再加 margin
            views.setLayoutWidthCompat(colorIds[index], 4f * scale, unit)
            margin(colorIds[index], RemoteViews.MARGIN_TOP, 6f)
            margin(colorIds[index], RemoteViews.MARGIN_BOTTOM, 6f)
        }
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

    /** 拼接课程信息（今明版第三行）：教室｜教师，非空项间用「｜」分隔；节次信息由第二行时间承担 */
    private fun buildCourseInfo(course: Course): String = buildList {
        if (course.classroom.isNotEmpty()) add(course.classroom)
        if (course.teacher.isNotEmpty()) add(course.teacher)
    }.joinToString(AndroidWidgetStr.infoSeparator())
}
