package com.haooz.chedule.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper
import java.util.Calendar

/** Read-only API for today's, tomorrow's and display-resolved courses plus the widget state. */
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
                    courses.forEach { course -> newRow().also { row -> fillCourseRow(row, columns, course, repository) } }
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

    /**
     * 解析小组件要展示的状态与课程列表，与标准安卓小组件（TodayCourseWidgetProviderStandard）保持一致的
     * “今日已上完 → 切到明日” 逻辑：
     * 当开启了明日提醒、当前时间已过提醒时间、且今日课程已全部结束时，自动展示明日的课程。
     */
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

        // 与标准安卓小组件一致：显示"今天"时只保留 在课/未开始 的课程（隐藏已下课的）；
        // 显示"明天"时展示明日全部课程。
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

        val title = if (showTomorrow) "明天" else DAY_NAMES[targetDay - 1]
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
        val subText = if (isNow == 1) {
            "$remaining 分钟结束"
        } else {
            if (course.classroom.isNullOrEmpty()) sectionText else "$sectionText · ${course.classroom}"
        }
        val values = mapOf<String, Any?>(
            COLUMN_ID to course.id,
            COLUMN_NAME to course.name,
            COLUMN_CLASSROOM to course.classroom,
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
            COLUMN_SUB_TEXT to subText,
            COLUMN_SUB_COLOR to if (isNow == 1) "#A7D0FF" else "#B3FFFFFF",
        )
        columns.forEach { column -> row.add(values[column]) }
    }

    private fun getTodayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }

    /** "HH:mm" -> 分钟数，用于排序/比较；null/非法返回 Int.MAX_VALUE */
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