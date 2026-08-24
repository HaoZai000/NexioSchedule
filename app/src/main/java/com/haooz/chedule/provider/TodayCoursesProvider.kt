package com.haooz.chedule.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper

/** Read-only API for today's active courses. */
class TodayCoursesProvider : ContentProvider() {

    override fun onCreate(): Boolean = context != null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        require(uriMatcher.match(uri) == TODAY_COURSES) { "Unsupported URI: $uri" }
        require(selection == null && selectionArgs == null) { "Selection is not supported" }

        val appContext = requireNotNull(context)
        val repository = CourseRepository(appContext)
        val columns = projection?.toList() ?: DEFAULT_COLUMNS
        require(columns.all { it in DEFAULT_COLUMNS }) { "Unsupported column requested" }

        return MatrixCursor(columns.toTypedArray()).apply {
            CourseReminderHelper.getTodayCourses(appContext).forEach { course ->
                val values = mapOf(
                    COLUMN_ID to course.id,
                    COLUMN_NAME to course.name,
                    COLUMN_CLASSROOM to course.classroom,
                    COLUMN_TEACHER to course.teacher,
                    COLUMN_START_SECTION to course.startSection,
                    COLUMN_END_SECTION to course.endSection,
                    COLUMN_SECTION_TEXT to course.getSectionText(),
                    COLUMN_START_TIME to CourseReminderHelper.getCourseStartTime(course, repository).orEmpty(),
                    COLUMN_END_TIME to CourseReminderHelper.getCourseEndTime(course, repository).orEmpty(),
                )
                newRow().also { row -> columns.forEach { column -> row.add(values[column]) } }
            }
        }
    }

    override fun getType(uri: Uri): String {
        require(uriMatcher.match(uri) == TODAY_COURSES) { "Unsupported URI: $uri" }
        return "vnd.android.cursor.dir/vnd.com.haooz.chedule.course"
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

    private companion object {
        const val AUTHORITY = "com.haooz.chedule.courses"
        const val PATH_TODAY = "today"
        const val TODAY_COURSES = 1

        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_CLASSROOM = "classroom"
        const val COLUMN_TEACHER = "teacher"
        const val COLUMN_START_SECTION = "start_section"
        const val COLUMN_END_SECTION = "end_section"
        const val COLUMN_SECTION_TEXT = "section_text"
        const val COLUMN_START_TIME = "start_time"
        const val COLUMN_END_TIME = "end_time"

        val DEFAULT_COLUMNS = listOf(
            COLUMN_ID,
            COLUMN_NAME,
            COLUMN_CLASSROOM,
            COLUMN_TEACHER,
            COLUMN_START_SECTION,
            COLUMN_END_SECTION,
            COLUMN_SECTION_TEXT,
            COLUMN_START_TIME,
            COLUMN_END_TIME,
        )

        val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_TODAY, TODAY_COURSES)
        }
    }
}
