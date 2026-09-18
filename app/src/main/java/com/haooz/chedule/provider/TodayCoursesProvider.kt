package com.haooz.chedule.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.graphics.Paint
import android.graphics.Typeface
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper
import java.util.Calendar

class TodayCoursesProvider : ContentProvider() {

    override fun onCreate(): Boolean = context != null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val match = uriMatcher.match(uri)
        require(match == TODAY_COURSES || match == TOMORROW_COURSES || match == DISPLAY_COURSES || match == DISPLAY_STATE) {
            "Unsupported URI: $uri"
        }
        require(selection == null && selectionArgs == null) { "Selection is not supported" }

        val appContext = requireNotNull(context)
        val repository = CourseRepository(appContext)
        // uri query 参数 size=2x2 区分详情文案；size=single 为单日程小组件专属截断；缺省 4x2
        val widgetSize = uri.getQueryParameter("size")
        // loc_only=1|true 或 size=single：location 仅回地点（单日程）
        val locOnly = widgetSize == "single" || when (uri.getQueryParameter("loc_only")?.lowercase()) {
            "1", "true", "yes" -> true
            else -> false
        }

        return when (match) {
            TODAY_COURSES, TOMORROW_COURSES, DISPLAY_COURSES -> {
                val columns = projection?.toList() ?: COURSE_COLUMNS
                require(columns.all { it in COURSE_COLUMNS }) { "Unsupported column requested: $columns" }
                val courses = when (match) {
                    TODAY_COURSES -> CourseReminderHelper.getTodayCourses(appContext)
                    TOMORROW_COURSES -> CourseReminderHelper.getTomorrowCourses(appContext)
                    else -> resolveState(appContext, repository).courses
                }
                MatrixCursor(columns.toTypedArray()).apply {
                    courses.forEach { course ->
                        newRow().also { row ->
                            fillCourseRow(row, columns, course, repository, widgetSize, locOnly)
                        }
                    }
                }
            }
            else -> { // DISPLAY_STATE
                val columns = projection?.toList() ?: STATE_COLUMNS
                require(columns.all { it in STATE_COLUMNS }) { "Unsupported column requested: $columns" }
                val state = resolveState(appContext, repository)
                MatrixCursor(columns.toTypedArray()).apply {
                    newRow().also { row ->
                        val values = mapOf(
                            COLUMN_TITLE to state.title,
                            COLUMN_WEEK_TEXT to state.weekText,
                            COLUMN_EMPTY_TEXT to state.emptyText,
                            COLUMN_SHOW_TOMORROW to state.showTomorrow,
                        )
                        columns.forEach { column -> row.add(values.getValue(column)) }
                    }
                }
            }
        }
    }

    override fun getType(uri: Uri): String {
        val match = uriMatcher.match(uri)
        require(match == TODAY_COURSES || match == TOMORROW_COURSES || match == DISPLAY_COURSES || match == DISPLAY_STATE) {
            "Unsupported URI: $uri"
        }
        return if (match == DISPLAY_STATE) {
            "vnd.android.cursor.dir/vnd.com.haooz.chedule.course.state"
        } else {
            "vnd.android.cursor.dir/vnd.com.haooz.chedule.course"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = unsupportedWrite(uri)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        unsupportedWrite(uri)

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = unsupportedWrite(uri)

    private fun <T> unsupportedWrite(uri: Uri): T =
        throw UnsupportedOperationException("$uri is read-only")

    // 与标准小组件一致：开了明日提醒且已过提醒时间、今日课全上完时自动切到明日
    private fun resolveState(context: android.content.Context, repository: CourseRepository): DisplayState {
        val all = repository.getAllCourses()
        val currentWeek = repository.getCurrentWeek()
        val today = getTodayOfWeek()
        val todayCourses = all
            .filter { it.dayOfWeek == today && it.isActiveInWeek(currentWeek) }
            .sortedBy { CourseReminderHelper.getCourseStartTime(it, repository).toMinutes() }

        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val nextDayEnabled = repository.getNextDayReminder()
        val reminderMinutes = repository.getNextDayReminderHour() * 60 + repository.getNextDayReminderMinute()

        val todayFinished = if (todayCourses.isNotEmpty()) {
            val lastEnd = CourseReminderHelper.getCourseEndTime(todayCourses.maxByOrNull { it.endSection }!!, repository)
            lastEnd?.let { it.toMinutes() <= currentMinutes } ?: true
        } else true
        val showTomorrow = nextDayEnabled && currentMinutes >= reminderMinutes && todayFinished

        val (targetDay, targetWeek) = if (showTomorrow) {
            if (today == 7) 1 to (currentWeek + 1) else (today + 1) to currentWeek
        } else {
            today to currentWeek
        }
        val targetCourses = all
            .filter { it.dayOfWeek == targetDay && it.isActiveInWeek(targetWeek) }
            .sortedBy { CourseReminderHelper.getCourseStartTime(it, repository).toMinutes() }

        // 今天只保留在课/未开始；明天展示全部
        val displayCourses = if (showTomorrow) {
            targetCourses
        } else {
            targetCourses.filter { course ->
                val endMinutes = CourseReminderHelper.getCourseEndTime(course, repository)?.toMinutes() ?: Int.MAX_VALUE
                endMinutes > currentMinutes
            }
        }

        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        val isHoliday = currentWeek > totalWeeks || (currentWeek >= 1 && currentWeek > lastWeekWithCourses)

        val title = DAY_NAMES[targetDay - 1]
        val weekText = when {
            isHoliday -> "放假中"
            currentWeek < 1 -> "未开始"
            else -> "第${currentWeek}周"
        }
        val emptyText = when {
            isHoliday -> "假期中，暂无课程"
            currentWeek < 1 -> "学期暂未开始"
            showTomorrow -> "明日无课"
            todayCourses.isEmpty() -> "今日无课"
            else -> "今日课程已上完"
        }
        return DisplayState(title, weekText, emptyText, if (showTomorrow) 1 else 0, displayCourses)
    }

    private fun fillCourseRow(
        row: MatrixCursor.RowBuilder,
        columns: List<String>,
        course: Course,
        repository: CourseRepository,
        size: String?,
        locOnly: Boolean = false,
    ) {
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val start = CourseReminderHelper.getCourseStartTime(course, repository)
        val end = CourseReminderHelper.getCourseEndTime(course, repository)
        val startMinutes = start?.toMinutes() ?: -1
        val endMinutes = end?.toMinutes() ?: -1
        val isNow = if (startMinutes < endMinutes && currentMinutes in startMinutes until endMinutes) 1 else 0
        val remaining = if (isNow == 1) endMinutes - currentMinutes else 0
        val sectionText = course.getSectionText()
        val startText = start.orEmpty()
        // 详情按规格区分：2x2=开始时间｜地点；4x2=节次｜地点｜教师；自定义时间课不显示节次
val showSection = sectionText.isNotEmpty() && !course.hasValidCustomTime()
val subText = when {
    size == "2x2" -> listOf(startText, course.classroom).filter { it.isNotEmpty() }.joinToString("｜")
    showSection -> listOf(sectionText, course.classroom, course.teacher).filter { it.isNotEmpty() }.joinToString("｜")
    else -> listOf(course.classroom, course.teacher).filter { it.isNotEmpty() }.joinToString("｜")
}
        // 2x2：行2时间范围，行3地点｜教师；loc_only/single 时第三行只回地点
        val timeRange = listOf(startText, end.orEmpty()).filter { it.isNotEmpty() }.joinToString(" - ")
        val locationRaw = if (locOnly) {
            course.classroom
        } else {
            listOf(course.classroom, course.teacher).filter { it.isNotEmpty() }.joinToString("｜")
        }
        // App 端按容器宽度测量截断
        // size=single：单日程 440 设计稿，名称 x=42 size=48，地点 x=92 size=38
        // size=2x2：日程表卡片内 名称/地点｜教师
        // 默认 4x2：上课时为右侧倒计时让位
        val isSingle = size == "single"
        val isTwoByTwo = size == "2x2"
        val detailWidthPx = if (isNow == 1) 170f else 255f
        val displayName = when {
            isSingle -> truncateByPx(course.name, SINGLE_NAME_WIDTH, SINGLE_NAME_TEXT_SIZE)
            isTwoByTwo -> truncateByPx(course.name, TWO_X_TWO_WIDTH, TWO_X_TWO_NAME_TEXT_SIZE)
            else -> truncateByPx(course.name, detailWidthPx, NAME_TEXT_SIZE)
        }
        val displaySubText = if (isTwoByTwo) subText else truncateByPx(subText, detailWidthPx, SUB_TEXT_SIZE)
        val displayLocationTeacher = when {
            isSingle -> truncateByPx(locationRaw, SINGLE_LOCATION_WIDTH, SINGLE_LOCATION_TEXT_SIZE)
            isTwoByTwo -> truncateByPx(locationRaw, TWO_X_TWO_WIDTH, TWO_X_TWO_SUB_TEXT_SIZE)
            else -> locationRaw
        }
        // classroom 列：单日程也按地点字号截断，保证 @course_classroom 不超长
        val displayClassroom = if (isSingle) {
            truncateByPx(course.classroom, SINGLE_LOCATION_WIDTH, SINGLE_LOCATION_TEXT_SIZE)
        } else {
            course.classroom
        }
        val values = mapOf<String, Any?>(
            COLUMN_ID to course.id,
            COLUMN_NAME to displayName,
            COLUMN_CLASSROOM to displayClassroom,
            COLUMN_TEACHER to course.teacher,
            COLUMN_START_SECTION to course.startSection,
            COLUMN_END_SECTION to course.endSection,
            COLUMN_SECTION_TEXT to sectionText,
            COLUMN_START_TIME to start.orEmpty(),
            COLUMN_END_TIME to end.orEmpty(),
            COLUMN_COLOR to String.format("#%08X", course.colorRes),
            COLUMN_COLOR_INDEX to run {
                val colorIndex = Course.courseColors.indexOf(course.colorRes)
                android.util.Log.d("TodayCoursesProvider", "Course: ${course.name}, colorRes: ${course.colorRes}, colorIndex: $colorIndex")
                colorIndex
            },
            COLUMN_IS_NOW to isNow,
            COLUMN_REMAINING to remaining,
            COLUMN_ROW_BG to if (isNow == 1) "#1A2196F3" else "#14FFFFFF",
            COLUMN_SUB_TEXT to displaySubText,
            COLUMN_SUB_COLOR to if (isNow == 1) "#A7D0FF" else "#B3FFFFFF",
            COLUMN_REMAINING_TEXT to if (isNow == 1) "${remaining}分钟结束" else "",
            COLUMN_TIME_RANGE to timeRange,
            COLUMN_LOCATION_TEACHER to displayLocationTeacher,
        )
        columns.forEach { column -> row.add(values[column]) }
    }

    // 用与 widget 同款字体/字号测量；宽度为 sx=1 设计基准，渲染时等比缩放
    private fun truncateByPx(text: String, maxWidthPx: Float, textSizePx: Float): String {
        if (text.isEmpty() || maxWidthPx <= 0f) return text
        val paint = Paint().apply {
            this.textSize = textSizePx
            typeface = Typeface.create("mipro-medium", Typeface.NORMAL)
        }
        if (paint.measureText(text) <= maxWidthPx) return text

        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (paint.measureText(text.substring(0, mid)) + ellipsisWidth <= maxWidthPx) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }
        return text.substring(0, lo) + ellipsis
    }

    private fun getTodayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }

    // null/非法返回 Int.MAX_VALUE 排到末尾
    private fun String?.toMinutes(): Int {
        if (this.isNullOrBlank()) return Int.MAX_VALUE
        val parts = this.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return h * 60 + m
    }

    private data class DisplayState(
        val title: String,
        val weekText: String,
        val emptyText: String,
        val showTomorrow: Int,
        val courses: List<Course>,
    )

    private companion object {
        const val AUTHORITY = "com.haooz.chedule.courses"
        // sx=1 设计基准字号，用于 App 端测量截断
        const val NAME_TEXT_SIZE = 14f
        const val SUB_TEXT_SIZE = 12f
        // 2x2 绝对坐标 440×440 下的容器宽与字号
        const val TWO_X_TWO_WIDTH = 320f
        const val TWO_X_TWO_NAME_TEXT_SIZE = 38f
        const val TWO_X_TWO_SUB_TEXT_SIZE = 32f
        // 单日程 440 设计稿：名称 x=42 size=48（可用约 360）；地点 x=92 size=38（可用约 320）
        const val SINGLE_NAME_WIDTH = 360f
        const val SINGLE_NAME_TEXT_SIZE = 48f
        const val SINGLE_LOCATION_WIDTH = 320f
        const val SINGLE_LOCATION_TEXT_SIZE = 38f
        const val PATH_TODAY = "today"
        const val PATH_TOMORROW = "tomorrow"
        const val PATH_DISPLAY = "display"
        const val PATH_STATE = "state"
        const val TODAY_COURSES = 1
        const val TOMORROW_COURSES = 2
        const val DISPLAY_COURSES = 3
        const val DISPLAY_STATE = 4

        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_CLASSROOM = "classroom"
        const val COLUMN_TEACHER = "teacher"
        const val COLUMN_START_SECTION = "start_section"
        const val COLUMN_END_SECTION = "end_section"
        const val COLUMN_SECTION_TEXT = "section_text"
        const val COLUMN_START_TIME = "start_time"
        const val COLUMN_END_TIME = "end_time"
        const val COLUMN_COLOR = "color"
        const val COLUMN_COLOR_INDEX = "color_index"
        const val COLUMN_IS_NOW = "is_now"
        const val COLUMN_REMAINING = "remaining"
        const val COLUMN_ROW_BG = "row_bg"
        const val COLUMN_SUB_TEXT = "sub_text"
        const val COLUMN_SUB_COLOR = "sub_color"
        const val COLUMN_REMAINING_TEXT = "remaining_text"
        const val COLUMN_TIME_RANGE = "time_range"
        const val COLUMN_LOCATION_TEACHER = "location_teacher"

        const val COLUMN_TITLE = "title"
        const val COLUMN_WEEK_TEXT = "week_text"
        const val COLUMN_EMPTY_TEXT = "empty_text"
        const val COLUMN_SHOW_TOMORROW = "show_tomorrow"

        val COURSE_COLUMNS = listOf(
            COLUMN_ID,
            COLUMN_NAME,
            COLUMN_CLASSROOM,
            COLUMN_TEACHER,
            COLUMN_START_SECTION,
            COLUMN_END_SECTION,
            COLUMN_SECTION_TEXT,
            COLUMN_START_TIME,
            COLUMN_END_TIME,
            COLUMN_COLOR,
            COLUMN_COLOR_INDEX,
            COLUMN_IS_NOW,
            COLUMN_REMAINING,
            COLUMN_ROW_BG,
            COLUMN_SUB_TEXT,
            COLUMN_SUB_COLOR,
            COLUMN_REMAINING_TEXT,
            COLUMN_TIME_RANGE,
            COLUMN_LOCATION_TEACHER,
        )

        val STATE_COLUMNS = listOf(
            COLUMN_TITLE,
            COLUMN_WEEK_TEXT,
            COLUMN_EMPTY_TEXT,
            COLUMN_SHOW_TOMORROW,
        )

        val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_TODAY, TODAY_COURSES)
            addURI(AUTHORITY, PATH_TOMORROW, TOMORROW_COURSES)
            addURI(AUTHORITY, PATH_DISPLAY, DISPLAY_COURSES)
            addURI(AUTHORITY, PATH_STATE, DISPLAY_STATE)
        }
    }
}