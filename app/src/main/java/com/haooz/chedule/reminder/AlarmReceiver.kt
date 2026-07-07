/** 课程提醒闹钟接收器 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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

                // 根据课程名+节次生成去重ID（与 schedulePreClassAlarms 中 course.id 不同来源时也能匹配）
                val courseId = "$courseName|$section|$startTime"

                // 去重检查：如果该课程最近已发送过，跳过本次（避免闹钟触发后重新调度导致双发）
                if (CourseReminderHelper.isPreClassSentRecently(context, courseId)) {
                    Log.d("AlarmReceiver", "Pre-class notification already sent recently for $courseName, skipping")
                    CourseReminderHelper.startReminderService(context)
                    return
                }

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
                    // 与 schedulePreClassAlarms 立即发送分支保持一致：使用 1003 作为 notificationId
                    val islandNotificationId = 1003
                    IslandNotificationHelper.sendPreClassIslandNotification(
                        context = context,
                        courseName = courseName,
                        classroom = classroom,
                        section = section,
                        startTime = startTime,
                        teacher = teacher,
                        minutesUntil = minutesUntil,
                        notificationId = islandNotificationId
                    )

                    // 注册 IslandExpandReceiver：课程开始时切换为"已上课"，避免倒计时卡在 00:00
                    if (minutesUntil != null && minutesUntil > 0 && startTime.isNotEmpty()) {
                        val parts = startTime.split(":")
                        if (parts.size == 2) {
                            val startHour = parts[0].toIntOrNull()
                            val startMinute = parts[1].toIntOrNull()
                            if (startHour != null && startMinute != null) {
                                val courseStartMillis = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.HOUR_OF_DAY, startHour)
                                    set(java.util.Calendar.MINUTE, startMinute)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                val expandIntent = Intent(context, IslandExpandReceiver::class.java).apply {
                                    putExtra(IslandExpandReceiver.EXTRA_COURSE_NAME, courseName)
                                    putExtra(IslandExpandReceiver.EXTRA_CLASSROOM, classroom)
                                    putExtra(IslandExpandReceiver.EXTRA_SECTION, section)
                                    putExtra(IslandExpandReceiver.EXTRA_START_TIME, startTime)
                                    putExtra(IslandExpandReceiver.EXTRA_NOTIFICATION_ID, islandNotificationId)
                                }
                                val expandPending = android.app.PendingIntent.getBroadcast(
                                    context, islandNotificationId, expandIntent,
                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                )
                                try {
                                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                                    alarmManager.setExactAndAllowWhileIdle(
                                        android.app.AlarmManager.RTC_WAKEUP,
                                        courseStartMillis + 1000L,
                                        expandPending
                                    )
                                } catch (_: SecurityException) { }
                            }
                        }
                    }
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

                // 记录已发送，防止后续 startReminderService 重调度时重复发送
                CourseReminderHelper.recordPreClassSent(context, courseId)

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