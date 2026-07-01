package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CourseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getIntExtra(CourseReminderHelper.EXTRA_REMINDER_TYPE, 0)

        when (type) {
            CourseReminderHelper.TYPE_PRE_CLASS -> {
                val courseName = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_NAME) ?: "课程"
                val classroom = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_CLASSROOM) ?: ""
                val section = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_SECTION) ?: ""
                val startTime = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_START_TIME) ?: ""
                val teacher = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_TEACHER) ?: ""

                val title = if (startTime.isNotEmpty()) "$courseName $startTime" else courseName
                val message = buildString {
                    if (section.isNotEmpty()) append(section)
                    if (classroom.isNotEmpty()) append("｜").append(classroom)
                    if (teacher.isNotEmpty()) append("｜").append(teacher)
                }

                CourseReminderHelper.showReminderNotification(context, type, title, message)
            }

            CourseReminderHelper.TYPE_NEXT_DAY -> {
                val tomorrowCourses = CourseReminderHelper.getTomorrowCourses(context)

                if (tomorrowCourses.isEmpty()) {
                    CourseReminderHelper.showReminderNotification(context, type, "明日无课", "明天没有课程安排")
                } else {
                    val title = "明日课程（${tomorrowCourses.size}节）"
                    val details = tomorrowCourses.joinToString("\n") { course ->
                        val repo = com.haooz.chedule.data.CourseRepository(context)
                        val startTime = CourseReminderHelper.getCourseStartTime(course, repo)
                        val endTime = CourseReminderHelper.getCourseEndTime(course, repo)
                        val timeRange = if (startTime != null && endTime != null) {
                            "$startTime-$endTime"
                        } else ""
                        buildString {
                            if (timeRange.isNotEmpty()) append(timeRange).append(" ")
                            append(course.name)
                            if (course.classroom.isNotEmpty()) append(" @").append(course.classroom)
                        }
                    }
                    CourseReminderHelper.showReminderNotification(context, type, title, details)
                }
            }
        }
    }
}
