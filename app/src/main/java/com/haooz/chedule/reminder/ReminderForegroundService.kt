package com.haooz.chedule.reminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.haooz.chedule.MainActivity
import com.haooz.chedule.R
import java.util.Calendar

class ReminderForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "course_reminder_channel"
        const val CHANNEL_NAME = "课程提醒"
        const val CHANNEL_REMINDER_ID = "course_reminder_alert"
        const val CHANNEL_REMINDER_NAME = "课程提醒通知"
        const val CHANNEL_LIVE_ID = "course_reminder_live"
        const val CHANNEL_LIVE_NAME = "课程提醒实况"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.haooz.chedule.ACTION_START_REMINDER"
        const val ACTION_STOP = "com.haooz.chedule.ACTION_STOP_REMINDER"
        const val COUNTDOWN_REFRESH_INTERVAL = 1_000L
        var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var classStartedDismissed = false
    private var lastWidgetMinute = -1
    private val countdownRunnable = object : Runnable {
        override fun run() {
            val minutesUntil = CourseReminderHelper.getMinutesUntilNextCourse(this@ReminderForegroundService)
            CourseReminderHelper.updateCountdownNotification(this@ReminderForegroundService)

            // 小部件分钟变化时刷新
            val currentMinute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
            if (currentMinute != lastWidgetMinute) {
                lastWidgetMinute = currentMinute
                com.haooz.chedule.widget.CourseWidgetProvider.updateAllWidgets(this@ReminderForegroundService)
            }

            if (minutesUntil != null && minutesUntil <= 0 && !classStartedDismissed) {
                classStartedDismissed = true
                handler.postDelayed({
                    CourseReminderHelper.dismissCountdownNotification(this@ReminderForegroundService)
                }, 10_000L)
            } else if (minutesUntil != null && minutesUntil > 0) {
                classStartedDismissed = false
            }

            handler.postDelayed(this, COUNTDOWN_REFRESH_INTERVAL)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isRunning = false
                stopCountdownRefresh()
                CourseReminderHelper.dismissCountdownNotification(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val repository = com.haooz.chedule.data.CourseRepository(this)
                if (!repository.getPreClassReminder() && !repository.getNextDayReminder()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                isRunning = true
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, createNotification())
                if (repository.getPreClassReminder()) {
                    CourseReminderHelper.updateCountdownNotification(this)
                    startCountdownRefresh()
                }
                scheduleAllAlarms()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdownRefresh()
        CourseReminderHelper.dismissCountdownNotification(this)
    }

    private fun startCountdownRefresh() {
        handler.removeCallbacks(countdownRunnable)
        handler.postDelayed(countdownRunnable, COUNTDOWN_REFRESH_INTERVAL)
    }

    private fun stopCountdownRefresh() {
        handler.removeCallbacks(countdownRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "课程提醒服务"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val alertChannel = NotificationChannel(
                CHANNEL_REMINDER_ID,
                CHANNEL_REMINDER_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "课程提醒通知"
                setShowBadge(true)
            }
            val liveChannel = NotificationChannel(
                CHANNEL_LIVE_ID,
                CHANNEL_LIVE_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "课程提醒实时进度（状态栏显示图标+文字）"
                setShowBadge(true)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(liveChannel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("课程表")
            .setContentText("课程提醒服务运行中")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun scheduleAllAlarms() {
        val repository = com.haooz.chedule.data.CourseRepository(this)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            }
        }

        // Cancel all existing alarms before rescheduling
        cancelAllAlarms(this, repository, alarmManager)

        if (repository.getPreClassReminder()) {
            schedulePreClassAlarms(this, repository, alarmManager)
        }

        if (repository.getNextDayReminder()) {
            scheduleNextDayAlarm(this, repository, alarmManager)
        }
    }

    private fun cancelAllAlarms(
        context: Context,
        repository: com.haooz.chedule.data.CourseRepository,
        alarmManager: AlarmManager
    ) {
        // Cancel pre-class alarms for all courses in current schedule
        for (course in repository.getAllCourses()) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                course.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        // Cancel next-day alarm
        val nextDayIntent = Intent(context, AlarmReceiver::class.java)
        val nextDayPendingIntent = PendingIntent.getBroadcast(
            context,
            99999,
            nextDayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(nextDayPendingIntent)
    }

    private fun schedulePreClassAlarms(
        context: Context,
        repository: com.haooz.chedule.data.CourseRepository,
        alarmManager: AlarmManager
    ) {
        val minutesBefore = repository.getPreClassReminderMinutes()
        val currentWeek = repository.getCurrentWeek()
        val totalWeeks = repository.getTotalWeeks()
        val today = getTodayOfWeek()
        val allCourses = repository.getAllCourses()
        val morningSections = repository.getMorningSections()
        val afternoonSections = repository.getAfternoonSections()

        if (currentWeek > totalWeeks) return

        val todayCourses = allCourses.filter { course ->
            course.dayOfWeek == today && course.isActiveInWeek(currentWeek)
        }.sortedBy { it.startSection }

        for ((index, course) in todayCourses.withIndex()) {
            val startTime = CourseReminderHelper.getCourseStartTime(course, repository) ?: continue
            val startParts = startTime.split(":")
            if (startParts.size != 2) continue
            val startHour = startParts[0].toIntOrNull() ?: continue
            val startMinute = startParts[1].toIntOrNull() ?: continue
            val startTotalMinutes = startHour * 60 + startMinute

            val isConsecutive = index > 0 && isConsecutiveCourse(todayCourses[index - 1], course, morningSections, afternoonSections)

            val triggerMinutes = if (isConsecutive) {
                val prevCourse = todayCourses[index - 1]
                val prevEndTime = CourseReminderHelper.getCourseEndTime(prevCourse, repository) ?: continue
                val prevEndParts = prevEndTime.split(":")
                if (prevEndParts.size != 2) continue
                val prevEndHour = prevEndParts[0].toIntOrNull() ?: continue
                val prevEndMinute = prevEndParts[1].toIntOrNull() ?: continue
                prevEndHour * 60 + prevEndMinute
            } else {
                startTotalMinutes - minutesBefore
            }

            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            if (currentMinutes >= triggerMinutes) continue

            val alarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, triggerMinutes / 60)
                set(Calendar.MINUTE, triggerMinutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(CourseReminderHelper.EXTRA_REMINDER_TYPE, CourseReminderHelper.TYPE_PRE_CLASS)
                putExtra(CourseReminderHelper.EXTRA_COURSE_NAME, course.name)
                putExtra(CourseReminderHelper.EXTRA_COURSE_CLASSROOM, course.classroom)
                putExtra(CourseReminderHelper.EXTRA_COURSE_SECTION, course.getSectionText())
                putExtra(CourseReminderHelper.EXTRA_COURSE_START_TIME, startTime)
                putExtra(CourseReminderHelper.EXTRA_COURSE_TEACHER, course.teacher)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                course.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime.timeInMillis,
                    pendingIntent
                )
            } catch (_: SecurityException) { }
        }
    }

    private fun scheduleNextDayAlarm(
        context: Context,
        repository: com.haooz.chedule.data.CourseRepository,
        alarmManager: AlarmManager
    ) {
        val hour = repository.getNextDayReminderHour()
        val minute = repository.getNextDayReminderMinute()

        val now = Calendar.getInstance()
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DATE, 1)
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(CourseReminderHelper.EXTRA_REMINDER_TYPE, CourseReminderHelper.TYPE_NEXT_DAY)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            99999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime.timeInMillis,
                pendingIntent
            )
        } catch (_: SecurityException) { }
    }

    private fun isConsecutiveCourse(prev: com.haooz.chedule.data.Course, current: com.haooz.chedule.data.Course, morningSections: Int, afternoonSections: Int): Boolean {
        val prevPeriod = getCoursePeriod(prev.startSection, morningSections, afternoonSections)
        val currentPeriod = getCoursePeriod(current.startSection, morningSections, afternoonSections)
        if (prevPeriod != currentPeriod) return false
        return prev.endSection + 1 == current.startSection
    }

    private fun getCoursePeriod(section: Int, morningSections: Int, afternoonSections: Int): Int {
        return when {
            section <= morningSections -> 0
            section <= morningSections + afternoonSections -> 1
            else -> 2
        }
    }

    private fun getTodayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }
}
