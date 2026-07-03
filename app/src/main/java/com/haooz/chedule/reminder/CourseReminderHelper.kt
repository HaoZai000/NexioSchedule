/** 课程提醒助手 - 管理课程提醒的创建、取消和调度 */
package com.haooz.chedule.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
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
    const val EXTRA_COURSE_START_MILLIS = "course_start_millis"
    const val EXTRA_COURSE_END_MILLIS = "course_end_millis"

    const val TYPE_PRE_CLASS = 1
    const val TYPE_NEXT_DAY = 2

    const val COUNTDOWN_NOTIFICATION_ID = 2
    const val WIDGET_REFRESH_REQUEST_CODE = 88888

    const val CHANNEL_REMINDER_ID = "course_reminder_alert"
    const val CHANNEL_REMINDER_NAME = "课程提醒通知"
    const val CHANNEL_LIVE_ID = "course_reminder_live"
    const val CHANNEL_LIVE_NAME = "课程提醒实况"

    fun startReminderService(context: Context) {
        startReminderService(context, CourseRepository(context))
    }

    fun startReminderService(context: Context, repository: CourseRepository) {
        if (!repository.getPreClassReminder() && !repository.getNextDayReminder()) {
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleAllAlarms(context, repository, alarmManager)
        scheduleWidgetRefresh(context, alarmManager)
    }

    fun stopReminderService(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllAlarms(context, alarmManager)
        cancelWidgetRefresh(context, alarmManager)
    }

    fun isServiceRunning(): Boolean {
        return true
    }

    private fun scheduleAllAlarms(context: Context, repository: CourseRepository, alarmManager: AlarmManager) {
        cancelAllAlarms(context, alarmManager)
        if (repository.getPreClassReminder()) {
            schedulePreClassAlarms(context, repository, alarmManager)
        }
        if (repository.getNextDayReminder()) {
            scheduleNextDayAlarm(context, repository, alarmManager)
        }
    }

    private fun cancelAllAlarms(context: Context, alarmManager: AlarmManager) {
        val allCourses = CourseRepository(context).getAllCourses()
        for (course in allCourses) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                course.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
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
        repository: CourseRepository,
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

        // 清除旧的倒计时状态，重新调度所有闹钟
        context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
            .edit().putBoolean("active", false).apply()

        val todayCourses = allCourses.filter { course ->
            course.dayOfWeek == today && course.isActiveInWeek(currentWeek)
        }.sortedBy { it.startSection }

        var immediateSent = false
        val useIsland = repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)

        for ((index, course) in todayCourses.withIndex()) {
            val startTime = getCourseStartTime(course, repository) ?: continue
            val startParts = startTime.split(":")
            if (startParts.size != 2) continue
            val startHour = startParts[0].toIntOrNull() ?: continue
            val startMinute = startParts[1].toIntOrNull() ?: continue
            val startTotalMinutes = startHour * 60 + startMinute

            val isConsecutive = index > 0 && isConsecutiveCourse(todayCourses[index - 1], course, morningSections, afternoonSections)

            val triggerMinutes = if (isConsecutive) {
                val prevCourse = todayCourses[index - 1]
                val prevEndTime = getCourseEndTime(prevCourse, repository) ?: continue
                val prevEndParts = prevEndTime.split(":")
                if (prevEndParts.size != 2) continue
                val prevEndHour = prevEndParts[0].toIntOrNull() ?: continue
                val prevEndMinute = prevEndParts[1].toIntOrNull() ?: continue
                val prevEndTotalMinutes = prevEndHour * 60 + prevEndMinute

                val breakMinutes = startTotalMinutes - prevEndTotalMinutes
                if (breakMinutes < minutesBefore) {
                    prevEndTotalMinutes
                } else {
                    startTotalMinutes - minutesBefore
                }
            } else {
                startTotalMinutes - minutesBefore
            }

            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val alarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, triggerMinutes / 60)
                set(Calendar.MINUTE, triggerMinutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (currentMinutes >= triggerMinutes) {
                // 已过触发时间，只对下一个未开始的课程发送立即通知
                if (currentMinutes < startTotalMinutes && !immediateSent) {
                    immediateSent = true
                    if (useIsland) {
                        val minutesUntil = startTotalMinutes - currentMinutes
                        IslandNotificationHelper.sendPreClassIslandNotification(
                            context = context,
                            courseName = course.name,
                            classroom = course.classroom,
                            section = course.getSectionText(),
                            startTime = startTime,
                            teacher = course.teacher,
                            minutesUntil = minutesUntil,
                            notificationId = 1003
                        )

                        // 安排课程开始时切换为"已上课"状态
                        val courseStartMillis = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, startHour)
                            set(Calendar.MINUTE, startMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val expandIntent = Intent(context, IslandExpandReceiver::class.java).apply {
                            putExtra(IslandExpandReceiver.EXTRA_COURSE_NAME, course.name)
                            putExtra(IslandExpandReceiver.EXTRA_CLASSROOM, course.classroom)
                            putExtra(IslandExpandReceiver.EXTRA_SECTION, course.getSectionText())
                            putExtra(IslandExpandReceiver.EXTRA_START_TIME, startTime)
                            putExtra(IslandExpandReceiver.EXTRA_NOTIFICATION_ID, 1003)
                        }
                        val expandPending = PendingIntent.getBroadcast(
                            context, 1003, expandIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        try {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                courseStartMillis + 1000L,
                                expandPending
                            )
                        } catch (_: SecurityException) { }
                    } else {
                        val startMillis = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, startHour)
                            set(Calendar.MINUTE, startMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val endMillisStr = getCourseEndTime(course, repository)
                        val endMillis = if (endMillisStr != null) {
                            val parts = endMillisStr.split(":")
                            if (parts.size == 2) {
                                Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                                    set(Calendar.MINUTE, parts[1].toInt())
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            } else 0L
                        } else 0L
                        showPreClassCountdownNotification(
                            context, course.name, course.classroom, course.getSectionText(),
                            startTime, startMillis, endMillis
                        )
                    }
                }
                continue
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_TYPE, TYPE_PRE_CLASS)
                putExtra(EXTRA_COURSE_NAME, course.name)
                putExtra(EXTRA_COURSE_CLASSROOM, course.classroom)
                putExtra(EXTRA_COURSE_SECTION, course.getSectionText())
                putExtra(EXTRA_COURSE_START_TIME, startTime)
                putExtra(EXTRA_COURSE_TEACHER, course.teacher)
                val courseStartMillis = alarmTime.timeInMillis + minutesBefore * 60_000L
                putExtra(EXTRA_COURSE_START_MILLIS, courseStartMillis)
                val endTimeStr = getCourseEndTime(course, repository)
                if (endTimeStr != null) {
                    val parts = endTimeStr.split(":")
                    if (parts.size == 2) {
                        val endCalendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                            set(Calendar.MINUTE, parts[1].toInt())
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        putExtra(EXTRA_COURSE_END_MILLIS, endCalendar.timeInMillis)
                    }
                }
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
            } catch (e: SecurityException) { }
        }
    }

    private fun scheduleNextDayAlarm(
        context: Context,
        repository: CourseRepository,
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
            putExtra(EXTRA_REMINDER_TYPE, TYPE_NEXT_DAY)
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

    private fun scheduleWidgetRefresh(context: Context, alarmManager: AlarmManager) {
        val intent = Intent(context, WidgetRefreshReceiver::class.java).apply {
            action = WidgetRefreshReceiver.ACTION_REFRESH_WIDGET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WIDGET_REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + 60_000L
        try {
            alarmManager.setRepeating(
                AlarmManager.RTC,
                triggerAt,
                60_000L,
                pendingIntent
            )
        } catch (_: SecurityException) { }
    }

    private fun cancelWidgetRefresh(context: Context, alarmManager: AlarmManager) {
        val intent = Intent(context, WidgetRefreshReceiver::class.java).apply {
            action = WidgetRefreshReceiver.ACTION_REFRESH_WIDGET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WIDGET_REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun isConsecutiveCourse(prev: Course, current: Course, morningSections: Int, afternoonSections: Int): Boolean {
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
        val upcoming = courses.firstOrNull { course ->
            val startTime = getCourseStartTime(course, repository) ?: return@firstOrNull false
            val parts = startTime.split(":")
            if (parts.size == 2) {
                val courseMinutes = (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                courseMinutes > currentMinutes
            } else false
        }
        if (upcoming != null) return upcoming
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

    fun getMinutesUntilNextCourse(context: Context): Int? {
        val repository = CourseRepository(context)
        val nextCourse = findNextCourseToday(context) ?: return null
        val startTime = getCourseStartTime(nextCourse, repository) ?: return null
        val parts = startTime.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return h * 60 + m - currentMinutes
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

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER_ID)
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

    fun showPreClassCountdownNotification(
        context: Context,
        courseName: String,
        classroom: String,
        section: String,
        startTime: String,
        startMillis: Long,
        endMillis: Long
    ) {
        ensureNotificationChannels(context)

        val contentIntent = PendingIntent.getActivity(
            context, courseName.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = 10000 + courseName.hashCode()
        val minutesUntilStart = ((startMillis - System.currentTimeMillis()) / 60_000).toInt()

        val notification = NotificationCompat.Builder(context, CHANNEL_LIVE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (classroom.isNotEmpty()) "$courseName｜$classroom" else courseName)
            .setContentText("即将上课｜$startTime")
            .setShortCriticalText(courseName)
            .setWhen(startMillis)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setRequestPromotedOngoing(true)
            .apply {
                val timeout = endMillis - System.currentTimeMillis()
                if (timeout > 0) setTimeoutAfter(timeout)
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)

        // 保存倒计时状态，供 WidgetRefreshReceiver 每分钟更新
        val prefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("active", true)
            .putString("courseName", courseName)
            .putString("classroom", classroom)
            .putString("section", section)
            .putString("startTime", startTime)
            .putLong("startMillis", startMillis)
            .putLong("endMillis", endMillis)
            .putInt("notificationId", notificationId)
            .apply()

        // 倒计时到达后立即触发更新，使用精确闹钟确保可靠触发
        val delay = startMillis - System.currentTimeMillis()
        if (delay > 0) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = startMillis + 1000L
            val alarmIntent = Intent(context, CourseStartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } catch (_: SecurityException) { }
        }
    }

    /**
     * 每分钟由 WidgetRefreshReceiver 调用，更新实况倒计时通知内容
     */
    fun updateActiveCountdown(context: Context) {
        val prefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return

        // 超级岛模式下不显示实况通知
        val repository = CourseRepository(context)
        if (repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)) {
            prefs.edit().putBoolean("active", false).apply()
            return
        }

        val startMillis = prefs.getLong("startMillis", 0L)
        val endMillis = prefs.getLong("endMillis", 0L)
        val notificationId = prefs.getInt("notificationId", 0)
        val courseName = prefs.getString("courseName", "") ?: ""
        val classroom = prefs.getString("classroom", "") ?: ""
        val section = prefs.getString("section", "") ?: ""
        val startTime = prefs.getString("startTime", "") ?: ""

        val now = System.currentTimeMillis()

        // 通知过期（课程结束），取消
        if (endMillis > 0 && now >= endMillis) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
            manager.cancel(1003)
            prefs.edit().putBoolean("active", false).apply()
            return
        }

        // 上课时间到了，取消倒计时通知，另发一条"已上课"实况通知
        if (now >= startMillis) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
            manager.cancel(1003)
            prefs.edit().putBoolean("active", false).apply()

            val startedIntent = PendingIntent.getActivity(
                context, courseName.hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val startedNotification = NotificationCompat.Builder(context, CHANNEL_LIVE_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(if (classroom.isNotEmpty()) "$courseName｜$classroom" else courseName)
                .setContentText("已上课｜$startTime")
                .setShortCriticalText("已上课")
                .setOngoing(true)
                .setContentIntent(startedIntent)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setRequestPromotedOngoing(true)
                .setTimeoutAfter(15_000L)
                .build()
            manager.notify(notificationId, startedNotification)
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context, courseName.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LIVE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (classroom.isNotEmpty()) "$courseName｜$classroom" else courseName)
            .setContentText("即将上课｜$startTime")
            .setShortCriticalText(courseName)
            .setWhen(startMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setRequestPromotedOngoing(true)
            .apply {
                val timeout = endMillis - now
                if (timeout > 0) setTimeoutAfter(timeout)
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}