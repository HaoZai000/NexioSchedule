package com.haooz.chedule.reminder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.haooz.chedule.MainActivity
import com.haooz.chedule.R
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import java.util.Calendar

object CourseReminderHelper {

    const val EXTRA_REMINDER_TYPE = "reminder_type"
    const val EXTRA_COURSE_NAME = "course_name"
    const val EXTRA_COURSE_CLASSROOM = "course_classroom"
    const val EXTRA_COURSE_SECTION = "course_section"
    const val EXTRA_COURSE_START_TIME = "course_start_time"
    const val EXTRA_COURSE_TEACHER = "course_teacher"
    const val EXTRA_OPEN_REMINDER_SETTINGS = "open_reminder_settings"

    const val TYPE_PRE_CLASS = 1
    const val TYPE_NEXT_DAY = 2

    const val COUNTDOWN_NOTIFICATION_ID = 2

    fun startReminderService(context: Context) {
        startReminderService(context, CourseRepository(context))
    }

    fun startReminderService(context: Context, repository: CourseRepository) {
        if (!repository.getPreClassReminder() && !repository.getNextDayReminder()) {
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            android.widget.Toast.makeText(context, "请授予精确闹钟权限以使用课程提醒", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(context, ReminderForegroundService::class.java).apply {
            action = ReminderForegroundService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stopReminderService(context: Context) {
        val intent = Intent(context, ReminderForegroundService::class.java).apply {
            action = ReminderForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun isServiceRunning(): Boolean {
        return ReminderForegroundService.isRunning
    }

    fun getCourseStartTime(course: Course, repository: CourseRepository): String? {
        val morningTimes = repository.getPeriodTimes("morning")
        val afternoonTimes = repository.getPeriodTimes("afternoon")
        val eveningTimes = repository.getPeriodTimes("evening")
        val morningSections = repository.getMorningSections()
        val afternoonSections = repository.getAfternoonSections()

        val section = course.startSection
        val timeMap = when {
            section <= morningSections -> morningTimes
            section <= morningSections + afternoonSections -> afternoonTimes
            else -> eveningTimes
        }

        val relativeSection = when {
            section <= morningSections -> section
            section <= morningSections + afternoonSections -> section - morningSections
            else -> section - morningSections - afternoonSections
        }

        val timeRange = timeMap[relativeSection] ?: return null
        return timeRange.split("-").firstOrNull()?.trim()
    }

    fun getCourseEndTime(course: Course, repository: CourseRepository): String? {
        val morningTimes = repository.getPeriodTimes("morning")
        val afternoonTimes = repository.getPeriodTimes("afternoon")
        val eveningTimes = repository.getPeriodTimes("evening")
        val morningSections = repository.getMorningSections()
        val afternoonSections = repository.getAfternoonSections()

        val section = course.endSection
        val timeMap = when {
            section <= morningSections -> morningTimes
            section <= morningSections + afternoonSections -> afternoonTimes
            else -> eveningTimes
        }

        val relativeSection = when {
            section <= morningSections -> section
            section <= morningSections + afternoonSections -> section - morningSections
            else -> section - morningSections - afternoonSections
        }

        val timeRange = timeMap[relativeSection] ?: return null
        return timeRange.split("-").lastOrNull()?.trim()
    }

    fun getTomorrowCourses(context: Context): List<Course> {
        val repository = CourseRepository(context)
        val currentWeek = repository.getCurrentWeek()
        val totalWeeks = repository.getTotalWeeks()
        val courses = repository.getAllCourses()
        val today = getTodayOfWeek()
        val tomorrowDayOfWeek = if (today == 7) 1 else today + 1
        val tomorrowWeek = if (tomorrowDayOfWeek == 1) currentWeek + 1 else currentWeek

        if (tomorrowWeek > totalWeeks) return emptyList()

        return courses.filter { course ->
            course.dayOfWeek == tomorrowDayOfWeek && course.isActiveInWeek(tomorrowWeek)
        }.sortedBy { it.startSection }
    }

    fun getTodayCourses(context: Context): List<Course> {
        val repository = CourseRepository(context)
        val currentWeek = repository.getCurrentWeek()
        val today = getTodayOfWeek()
        if (currentWeek > repository.getTotalWeeks()) return emptyList()
        return repository.getAllCourses()
            .filter { it.dayOfWeek == today && it.isActiveInWeek(currentWeek) }
            .sortedBy { it.startSection }
    }

    fun findNextCourseToday(context: Context): Course? {
        val repository = CourseRepository(context)
        val courses = getTodayCourses(context)
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        // 先找未开始的课程
        val upcoming = courses.firstOrNull { course ->
            val startTime = getCourseStartTime(course, repository) ?: return@firstOrNull false
            val parts = startTime.split(":")
            if (parts.size == 2) {
                val courseMinutes = (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                courseMinutes > currentMinutes
            } else false
        }
        if (upcoming != null) return upcoming
        // 没有未开始的，找当前正在进行的（已开始但未结束）
        for (course in courses.reversed()) {
            val endTime = getCourseEndTime(course, repository) ?: continue
            val parts = endTime.split(":")
            if (parts.size == 2) {
                val endMinutes = (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                if (currentMinutes < endMinutes) return course
            }
        }
        return null
    }

    @SuppressLint("NewApi")
    fun buildCountdownNotification(context: Context): android.app.Notification? {
        val repository = CourseRepository(context)
        if (!repository.getPreClassReminder()) return null

        val nextCourse = findNextCourseToday(context)
        val startTime = nextCourse?.let { getCourseStartTime(it, repository) }
        val startParts = startTime?.split(":")

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val title: String
        val contentText: String
        val chipText: String
        val expandedText: String

        if (nextCourse != null && startParts != null && startParts.size == 2) {
            val startHour = startParts[0].toIntOrNull() ?: return null
            val startMinute = startParts[1].toIntOrNull() ?: return null
            val startTotalMinutes = startHour * 60 + startMinute
            val minutesUntil = startTotalMinutes - currentMinutes
            val minutesBefore = repository.getPreClassReminderMinutes()

            if (minutesUntil > minutesBefore) return null

            if (minutesUntil <= 0) {
                title = nextCourse.name
                contentText = "已上课"
                chipText = "已上课"
                expandedText = if (nextCourse.classroom.isNotEmpty()) {
                    "${nextCourse.name}｜${nextCourse.classroom}"
                } else {
                    nextCourse.name
                }
            } else {
                title = nextCourse.name
                contentText = "$startTime｜${formatCountdown(minutesUntil)}后上课"
                chipText = nextCourse.name
                expandedText = if (nextCourse.classroom.isNotEmpty()) {
                    "${nextCourse.name}｜${nextCourse.classroom}"
                } else {
                    nextCourse.name
                }
            }
        } else {
            val todayCourses = getTodayCourses(context)
            if (todayCourses.isEmpty()) return null
            val lastEnd = getCourseEndTime(todayCourses.last(), repository)
            val lastEndParts = lastEnd?.split(":")
            if (lastEndParts != null && lastEndParts.size == 2) {
                val endMinutes = (lastEndParts[0].toIntOrNull() ?: 0) * 60 + (lastEndParts[1].toIntOrNull() ?: 0)
                if (currentMinutes >= endMinutes) return null
            }
            title = "今日课程进行中"
            contentText = "${todayCourses.size}节课"
            chipText = "${todayCourses.size}节"
            expandedText = title
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, COUNTDOWN_NOTIFICATION_ID, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, ReminderForegroundService.CHANNEL_LIVE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(expandedText)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setStyle(
                Notification.BigTextStyle()
                    .setBigContentTitle(expandedText)
                    .bigText(contentText)
            )
            .setExtras(android.os.Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
                putString("android.shortCriticalText", chipText)
            })
            .build()
    }

    private var lastDisplayedMinutesUntil = -1
    private var classStartedNotified = false
    private val dismissHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingDismiss: Runnable? = null

    fun getMinutesUntilNextCourse(context: Context): Int? {
        val repository = CourseRepository(context)
        val nextCourse = findNextCourseToday(context) ?: return null
        val startTime = getCourseStartTime(nextCourse, repository) ?: return null
        val parts = startTime.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val now = java.util.Calendar.getInstance()
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        return h * 60 + m - currentMinutes
    }

    fun updateCountdownNotification(context: Context) {
        val notification = buildCountdownNotification(context) ?: run {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(COUNTDOWN_NOTIFICATION_ID)
            lastDisplayedMinutesUntil = -1
            classStartedNotified = false
            return
        }

        val repository = CourseRepository(context)
        val nextCourse = findNextCourseToday(context)
        val startTime = nextCourse?.let { getCourseStartTime(it, repository) }
        val startParts = startTime?.split(":")
        val now = java.util.Calendar.getInstance()
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val minutesUntil = if (nextCourse != null && startParts != null && startParts.size == 2) {
            val h = startParts[0].toIntOrNull() ?: 0
            val m = startParts[1].toIntOrNull() ?: 0
            h * 60 + m - currentMinutes
        } else -1

        if (minutesUntil == lastDisplayedMinutesUntil) return
        lastDisplayedMinutesUntil = minutesUntil

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 已上课时只触发一次10秒消失
        if (minutesUntil <= 0 && !classStartedNotified) {
            manager.notify(COUNTDOWN_NOTIFICATION_ID, notification)
            classStartedNotified = true
            pendingDismiss = Runnable { dismissCountdownNotification(context) }
            dismissHandler.postDelayed(pendingDismiss!!, 10_000L)
        } else if (minutesUntil <= 0 && classStartedNotified) {
            manager.cancel(COUNTDOWN_NOTIFICATION_ID)
        } else if (minutesUntil > 0) {
            pendingDismiss?.let { dismissHandler.removeCallbacks(it) }
            classStartedNotified = false
            manager.notify(COUNTDOWN_NOTIFICATION_ID, notification)
        }
    }

    fun dismissCountdownNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(COUNTDOWN_NOTIFICATION_ID)
    }

    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.canPostPromotedNotifications()
    }

    fun openPromotedNotificationsSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    private fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val alertChannel = NotificationChannel(
                ReminderForegroundService.CHANNEL_REMINDER_ID,
                ReminderForegroundService.CHANNEL_REMINDER_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "课程提醒通知"
                setShowBadge(true)
            }
            val liveChannel = NotificationChannel(
                ReminderForegroundService.CHANNEL_LIVE_ID,
                ReminderForegroundService.CHANNEL_LIVE_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "课程提醒实时进度"
                setShowBadge(true)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(liveChannel)
        }
    }

    private fun formatCountdown(minutes: Int): String {
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60
                val mins = minutes % 60
                if (mins > 0) "${hours}小时${mins}分钟" else "${hours}小时"
            }
            else -> "${minutes}分钟"
        }
    }

    fun showReminderNotification(context: Context, id: Int, title: String, message: String) {
        ensureNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_REMINDER_SETTINGS, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderForegroundService.CHANNEL_REMINDER_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message.replace("\n", " "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

    private fun getTodayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }
}
