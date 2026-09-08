/** 课程提醒闹钟接收器 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.haooz.chedule.data.CourseRepository

class AlarmReceiver : BroadcastReceiver() {

    /** 把 1..23 的整数转成中文数字（如 8 -> "八"，23 -> "二十三"），用于"早八"式文案 */
    private fun chineseNumberHour(n: Int): String {
        if (n <= 0 || n > 23) return n.toString()
        val digit = listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
        return when {
            n < 10 -> digit[n]
            n < 20 -> "十" + digit[n - 10]
            else -> digit[n / 10] + "十" + digit[n % 10]
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getIntExtra(CourseReminderHelper.EXTRA_REMINDER_TYPE, 0)
        Log.d("CourseReminder", "AlarmReceiver: type=$type ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        val repository = CourseRepository(context)
        val useIsland = repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)

        when (type) {
            CourseReminderHelper.TYPE_PRE_CLASS -> {
                val courseName = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_NAME) ?: "课程"
                val section = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_SECTION) ?: ""
                val startTime = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_START_TIME) ?: ""
                val courseId = intent.getStringExtra(CourseReminderHelper.EXTRA_COURSE_ID) ?: ""

                // 去重 ID 先以"当前课表"为准：先回查匹配课程并重新取节次/时间，
                // 避免用户改时间后旧闹钟带着旧 startTime 算 dedupId，与 checkPending
                // 用新 startTime 算的 dedupId 双发（均落入不同 dedupId，互相不拦截）。
                // 如果回查失败再退化为闹钟里快照的 name+section+time。
                val matchedEarly = CourseReminderHelper.getTodayCourses(context).firstOrNull { course ->
                    if (courseId.isNotEmpty()) course.id == courseId
                    else course.name == courseName && course.getTimeDisplayText() == section
                }
                val dedupId = if (matchedEarly != null) {
                    val freshStart = CourseReminderHelper.getCourseStartTime(
                        matchedEarly,
                        CourseRepository(context)
                    ) ?: startTime
                    "${matchedEarly.name}|${matchedEarly.getTimeDisplayText()}|$freshStart"
                } else {
                    "$courseName|$section|$startTime"
                }

                // 去重检查：如果该课程最近已发送过，跳过本次（避免闹钟触发后重新调度导致双发）
                if (CourseReminderHelper.isPreClassSentRecently(context, dedupId)) {
                    Log.d("AlarmReceiver", "Pre-class notification already sent recently for $courseName, skipping")
                    CourseReminderHelper.startReminderService(context)
                    return
                }

                // 学期未开始（未到开学日期所在周的周一）：不发送，并重新调度清理残留闹钟
                if (!CourseReminderHelper.isSemesterStarted(repository)) {
                    Log.d("AlarmReceiver", "Semester not started yet, skipping pre-class notification for $courseName")
                    CourseReminderHelper.startReminderService(context)
                    return
                }

                // 关键：闹钟里携带的是"注册那一刻"的课程快照。
                // 课程可能已被删除、改了时间/教室、或因换课表/云同步换了 ID，
                // 若直接照快照发送就会弹出旧数据提醒。这里一律以当前课表为准重新解析。
                val matched = CourseReminderHelper.getTodayCourses(context).firstOrNull { course ->
                    if (courseId.isNotEmpty()) {
                        course.id == courseId
                    } else {
                        // 旧版闹钟没有 courseId，退化为按课程名+节次匹配
                        course.name == courseName && course.getTimeDisplayText() == section
                    }
                }
                if (matched == null) {
                    Log.d("AlarmReceiver", "Stale alarm: $courseName($startTime) no longer in today's schedule, dropped")
                    CourseReminderHelper.startReminderService(context)
                    return
                }

                val freshStartTime = CourseReminderHelper.getCourseStartTime(matched, repository) ?: startTime
                val freshEndTime = CourseReminderHelper.getCourseEndTime(matched, repository) ?: ""
                val startMillis = CourseReminderHelper.parseTimeToTodayMillis(freshStartTime)
                val endMillis = CourseReminderHelper.parseTimeToTodayMillis(freshEndTime)

                if (startMillis <= 0L) {
                    Log.d("AlarmReceiver", "Invalid start time for ${matched.name}, skipped")
                    CourseReminderHelper.startReminderService(context)
                    return
                }

                // 统一走 sendPreClassNotification：
                // 未开课 → 课前倒计时；已开课（连堂课间为 0、或闹钟被 Doze 延迟）→ 直接落到"已上课"态。
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                CourseReminderHelper.sendPreClassNotification(
                    context = context,
                    alarmManager = alarmManager,
                    repository = repository,
                    course = matched,
                    startTime = freshStartTime,
                    useIsland = useIsland,
                    courseStartMillis = startMillis,
                    courseEndMillis = endMillis
                )

                // 记录已发送，防止后续 startReminderService 重调度时重复发送
                CourseReminderHelper.recordPreClassSent(context, dedupId)

                CourseReminderHelper.startReminderService(context)
            }

            CourseReminderHelper.TYPE_NEXT_DAY -> {
                // 学期未开始（未到开学日期所在周的周一）：不发送次日提醒
                if (!CourseReminderHelper.isSemesterStarted(repository)) {
                    CourseReminderHelper.startReminderService(context)
                    return
                }

                val tomorrowCourses = CourseReminderHelper.getTomorrowCourses(context)

                if (tomorrowCourses.isEmpty()) {
                    CourseReminderHelper.showReminderNotification(context, type, "明日无课", "明天没有课程安排")
                } else {
                    val title = "明天共${tomorrowCourses.size}节课"
                    val firstCourse = tomorrowCourses.first()
                    val firstStart = CourseReminderHelper.getCourseStartTime(firstCourse, repository)
                    val firstHour = firstStart?.split(":")?.firstOrNull()?.toIntOrNull() ?: 9
                    val details = when {
                        firstHour < 9 -> "明早有早${chineseNumberHour(firstHour)}，${firstCourse.name}"
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
