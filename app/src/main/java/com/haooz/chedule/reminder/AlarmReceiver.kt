package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haooz.chedule.data.CourseRepository

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getIntExtra(CourseReminderHelper.EXTRA_REMINDER_TYPE, 0)
        val repository = CourseRepository(context)
        val useIsland = repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)

        when (type) {
            CourseReminderHelper.TYPE_PRE_CLASS -> {
                val courseName = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_NAME) ?: "课程"
                val classroom = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_CLASSROOM) ?: ""
                val section = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_SECTION) ?: ""
                val startTime = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_START_TIME) ?: ""
                val teacher = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_TEACHER) ?: ""

                if (useIsland) {
                    val minutesUntil = if (startTime.isNotEmpty()) {
                        val parts = startTime.split(":")
                        if (parts.size == 2) {
                            val h = parts[0].toIntOrNull() ?: 0
                            val m = parts[1].toIntOrNull() ?: 0
                            val now = java.util.Calendar.getInstance()
                            val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                            val courseMinutes = h * 60 + m
                            courseMinutes - currentMinutes
                        } else null
                    } else null
                    IslandNotificationHelper.sendPreClassIslandNotification(
                        context = context,
                        courseName = courseName,
                        classroom = classroom,
                        section = section,
                        startTime = startTime,
                        teacher = teacher,
                        minutesUntil = minutesUntil
                    )
                } else {
                    val startMillis = intent.getLongExtra(CourseReminderHelper.EXTRA_COURSE_START_MILLIS, 0L)
                    val endMillis = intent.getLongExtra(CourseReminderHelper.EXTRA_COURSE_END_MILLIS, 0L)
                    CourseReminderHelper.showPreClassCountdownNotification(
                        context = context,
                        courseName = courseName,
                        classroom = classroom,
                        section = section,
                        startTime = startTime,
                        startMillis = startMillis,
                        endMillis = endMillis
                    )
                }

                CourseReminderHelper.startReminderService(context)
            }

            CourseReminderHelper.TYPE_NEXT_DAY -> {
                val tomorrowCourses = CourseReminderHelper.getTomorrowCourses(context)

                if (tomorrowCourses.isEmpty()) {
                    CourseReminderHelper.showReminderNotification(context, type, "明日无课", "明天没有课程安排")
                } else {
                    val title = "明天共${tomorrowCourses.size}节课"
                    val firstCourse = tomorrowCourses.first()
                    val firstStart = CourseReminderHelper.getCourseStartTime(firstCourse, repository)
                    val firstHour = firstStart?.split(":")?.firstOrNull()?.toIntOrNull() ?: 9
                    val details = when {
                        firstHour < 9 -> "明早有早$firstHour，${firstCourse.name}"
                        firstHour < 12 -> "明早有课，${firstCourse.name} $firstStart"
                        firstHour < 18 -> "下午有课，${firstCourse.name} $firstStart"
                        else -> "晚上有课，${firstCourse.name} $firstStart"
                    }
                    CourseReminderHelper.showReminderNotification(context, type, title, details)
                }

                CourseReminderHelper.startReminderService(context)
            }
        }
    }
}