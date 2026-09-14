package com.haooz.chedule.reminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.haooz.chedule.R
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.ui.activities.MainActivity
import java.time.LocalDate
import java.util.Calendar

object CourseReminderHelper {

    private const val TAG = "CourseReminder"

    const val EXTRA_REMINDER_TYPE = "reminder_type"
    const val EXTRA_COURSE_NAME = "course_name"
    const val EXTRA_COURSE_SECTION = "course_section"
    const val EXTRA_COURSE_START_TIME = "course_start_time"
    const val EXTRA_OPEN_REMINDER_SETTINGS = "open_reminder_settings"
    // 闹钟触发时用它回查课表，校验课程是否仍存在/时间是否已变更
    const val EXTRA_COURSE_ID = "course_id"

    const val TYPE_PRE_CLASS = 1
    const val TYPE_NEXT_DAY = 2

    const val WIDGET_REFRESH_REQUEST_CODE = 88888

    // 课前倒计时与"已上课"必须共用同一 ID，否则两态会同时停留在岛上
    const val ISLAND_NOTIFICATION_ID = IslandNotificationHelper.ISLAND_NOTIFICATION_ID

    // 每门课互不覆盖：RC = BASE + course.id.hashCode()
    private const val ISLAND_EXPAND_RC_BASE = 70000

    // 超过该滞后视为隔夜/重启残留，直接收起而不是补一个过期的上课态
    private const val ISLAND_LATE_TOLERANCE_MS = 5 * 60_000L

    // 开课后仍允许补发"已上课"的宽限期，超出则不再打扰
    private const val ISLAND_START_GRACE_MS = 2 * 60_000L

    const val CHANNEL_REMINDER_ID = "course_reminder_alert"
    const val CHANNEL_REMINDER_NAME = "课程提醒通知"
    const val CHANNEL_LIVE_ID = "course_reminder_live"
    const val CHANNEL_LIVE_NAME = "课程提醒实况"

    // 发送去重：JSON map(courseId->时间戳)+日期，跨日失效。
    // 常规提醒 60 分钟窗口不重发；超级岛当天只发一次（原生倒计时自行跳秒，重发会弹出岛）。
    private const val PREF_SENT_HISTORY = "reminder_sent_history"
    private const val KEY_SENT_DAY = "reminder_sent_day"
    private const val KEY_SENT_MAP = "reminder_sent_map"
    private const val SENT_DEDUP_WINDOW_MS = 60 * 60 * 1000L

    private const val PREF_DAY_CHANGE = "day_change_state"
    private const val KEY_LAST_SCHEDULE_DATE = "last_schedule_date"

    // requestCode 登记表：课程删除/换课表/UUID 变化后旧闹钟不在当前列表里，
    // 只按课程取消会残留孤儿闹钟（到点弹旧数据）
    private const val PREF_ALARM_REGISTRY = "reminder_alarm_registry"
    private const val KEY_PRE_CLASS_RCS = "pre_class_rcs"
    private const val KEY_EXPAND_RCS = "expand_rcs"
    private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun readRcSet(context: Context, key: String): Set<Int> {
        val prefs = context.getSharedPreferences(PREF_ALARM_REGISTRY, Context.MODE_PRIVATE)
        return (prefs.getStringSet(key, emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
    }

    private fun writeRcSet(context: Context, key: String, rcs: Set<Int>) {
        context.getSharedPreferences(PREF_ALARM_REGISTRY, Context.MODE_PRIVATE).edit {
            putStringSet(key, rcs.map { it.toString() }.toSet())
        }
    }

    // 跨日后整份记录失效，保证次日可重新提醒
    fun recordPreClassSent(context: Context, courseId: String) {
        val prefs = context.getSharedPreferences(PREF_SENT_HISTORY, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val day = prefs.getString(KEY_SENT_DAY, null)
        val mapStr = prefs.getString(KEY_SENT_MAP, null)
        val json = if (day == today && !mapStr.isNullOrEmpty()) {
            try {
                org.json.JSONObject(mapStr)
            } catch (_: Exception) {
                org.json.JSONObject()
            }
        } else {
            org.json.JSONObject()
        }
        json.put(courseId, System.currentTimeMillis())
        prefs.edit {
            putString(KEY_SENT_DAY, today)
            putString(KEY_SENT_MAP, json.toString())
        }
    }

    // 按 courseId 精确匹配 60 分钟窗口；跨日整份重置以便次日重发
    fun isPreClassSentRecently(context: Context, courseId: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_SENT_HISTORY, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SENT_DAY, null) != getTodayDateString()) return false
        val mapStr = prefs.getString(KEY_SENT_MAP, null) ?: return false
        val lastTime = try {
            org.json.JSONObject(mapStr).optLong(courseId, 0L)
        } catch (_: Exception) {
            0L
        }
        if (lastTime <= 0L) return false
        return System.currentTimeMillis() - lastTime < SENT_DEDUP_WINDOW_MS
    }

    // 岛倒计时是原生 ChronometerCountDown，当天发一次即可；重发会触发岛重新弹出
    fun hasIslandPreClassSentToday(context: Context, courseId: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_SENT_HISTORY, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SENT_DAY, null) != getTodayDateString()) return false
        val mapStr = prefs.getString(KEY_SENT_MAP, null) ?: return false
        return try {
            org.json.JSONObject(mapStr).has(courseId)
        } catch (_: Exception) {
            false
        }
    }

    // 日期变化时重调度：原调度仅覆盖今天，跨日后需为新一天重新注册闹钟
    fun checkAndRescheduleOnDayChange(context: Context) {
        val today = getTodayDateString()
        val prefs = context.getSharedPreferences(PREF_DAY_CHANGE, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_SCHEDULE_DATE, null)

        if (lastDate == today) return

        prefs.edit { putString(KEY_LAST_SCHEDULE_DATE, today) }
        startReminderService(context)
    }

    private fun getTodayDateString(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    // 课程开始/结束时间戳：通知、闹钟、岛倒计时一律用下面两个函数，禁止另算一套

    fun parseTimeToTodayMillis(time: String?): Long {
        if (time.isNullOrBlank()) return -1L
        val parts = time.split(":")
        if (parts.size < 2) return -1L
        val hour = parts[0].trim().toIntOrNull() ?: return -1L
        val minute = parts[1].trim().toIntOrNull() ?: return -1L
        if (hour !in 0..23 || minute !in 0..59) return -1L
        return todayMillis(hour, minute)
    }

    // 向上取整，保证文案与系统倒计时剩余秒数一致
    private fun ceilMinutesUntil(startMillis: Long, now: Long = System.currentTimeMillis()): Int {
        val remain = startMillis - now
        if (remain <= 0) return 0
        return ((remain + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    fun todayMillis(hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun startReminderService(context: Context) {
        startReminderService(context, CourseRepository(context))
    }

    fun startReminderService(context: Context, repository: CourseRepository) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!repository.getPreClassReminder() && !repository.getNextDayReminder()) {
            // 提醒关闭：取消本应用闹钟/通知，但 widget 刷新闹钟必须保留（只用小组件也要刷新）
            cancelAllAlarms(context, alarmManager)
            cancelIslandExpandAlarms(context, alarmManager)
            cancelCourseStartAlarms(context, alarmManager)
            cancelAllReminderNotifications(context)
            ClassDndHelper.cancelClassDndAlarms(context, alarmManager)
            ClassDndHelper.applyCurrentState(context)
            scheduleNextWidgetRefresh(context, alarmManager)
            return
        }
        scheduleAllAlarms(context, repository, alarmManager)
        scheduleWidgetRefresh(context, alarmManager)
        // 开关切换后立即对账，不必等闹钟
        ClassDndHelper.applyCurrentState(context)
    }

    // 开学日期所在周的周一之后才算已开始；解析失败时保守放行，避免误屏蔽
    fun isSemesterStarted(repository: CourseRepository): Boolean {
        return try {
            val start = LocalDate.parse(repository.getClassStartTime().replace("/", "-"))
            val startMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
            !LocalDate.now().isBefore(startMonday)
        } catch (_: Exception) {
            true
        }
    }

    fun stopReminderService(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllAlarms(context, alarmManager)
        cancelIslandExpandAlarms(context, alarmManager)
        cancelCourseStartAlarms(context, alarmManager)
        ClassDndHelper.cancelClassDndAlarms(context, alarmManager)
        ClassDndHelper.applyCurrentState(context)
        cancelAllReminderNotifications(context)
        // 不取消 widget 刷新闹钟，避免桌面小部件停止刷新
    }

    private fun scheduleAllAlarms(context: Context, repository: CourseRepository, alarmManager: AlarmManager) {
        cancelAllAlarms(context, alarmManager)
        if (repository.getPreClassReminder()) {
            schedulePreClassAlarms(context, repository, alarmManager)
        }
        if (repository.getNextDayReminder()) {
            scheduleNextDayAlarm(context, repository, alarmManager)
        }
        ClassDndHelper.scheduleClassDndAlarms(context, alarmManager)
    }

    private fun cancelAllAlarms(context: Context, alarmManager: AlarmManager) {
        // 先按登记表取消，覆盖已删除/已换 ID 的孤儿闹钟
        for (rc in readRcSet(context, KEY_PRE_CLASS_RCS)) {
            val pendingIntent = PendingIntent.getBroadcast(
                context, rc, Intent(context, AlarmReceiver::class.java), PI_FLAGS
            )
            alarmManager.cancel(pendingIntent)
        }
        writeRcSet(context, KEY_PRE_CLASS_RCS, emptySet())

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

    private fun cancelIslandExpandAlarms(context: Context, alarmManager: AlarmManager) {
        for (rc in readRcSet(context, KEY_EXPAND_RCS)) {
            val pendingIntent = PendingIntent.getBroadcast(
                context, rc, Intent(context, IslandExpandReceiver::class.java), PI_FLAGS
            )
            alarmManager.cancel(pendingIntent)
        }
        writeRcSet(context, KEY_EXPAND_RCS, emptySet())

        // 历史版本固定 ID 兜底清理
        val knownIds = listOf(1001, 1002, 1003, 5000)
        for (id in knownIds) {
            val intent = Intent(context, IslandExpandReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun cancelCourseStartAlarms(context: Context, alarmManager: AlarmManager) {
        val allCourses = CourseRepository(context).getAllCourses()
        for (course in allCourses) {
            val intent = Intent(context, CourseStartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                10000 + course.name.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun schedulePreClassAlarms(
        context: Context,
        repository: CourseRepository,
        alarmManager: AlarmManager
    ) {
        if (!isSemesterStarted(repository)) {
            writeRcSet(context, KEY_PRE_CLASS_RCS, emptySet())
            return
        }

        val minutesBefore = repository.getPreClassReminderMinutes()

        android.util.Log.d(TAG, "schedulePre: entered semesterStarted=${isSemesterStarted(repository)} preReminder=${repository.getPreClassReminder()} minuteBefore=$minutesBefore")

        // 复用 getTodayCourses，勿再内联 workSwap/周次/节假日过滤
        val todayCourses = getTodayCourses(context)
        if (todayCourses.isEmpty()) {
            android.util.Log.d(TAG, "schedulePre: RETURN noCoursesForToday")
            writeRcSet(context, KEY_PRE_CLASS_RCS, emptySet())
            return
        }
        android.util.Log.d(TAG, "schedulePre: todayCourses=${todayCourses.size}")
        todayCourses.forEach { android.util.Log.d(TAG, "schedulePre:   course=${it.name} day=${it.dayOfWeek} start=${getCourseStartTime(it, repository)} end=${getCourseEndTime(it, repository)}") }

        // 登记本次注册的 RC，供 cancelAllAlarms 清理孤儿闹钟
        val scheduledRcs = mutableSetOf<Int>()

        val useIsland = repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)

        for ((index, course) in todayCourses.withIndex()) {
            val startTime = getCourseStartTime(course, repository) ?: continue
            val startParts = startTime.split(":")
            if (startParts.size != 2) continue
            val startHour = startParts[0].toIntOrNull() ?: continue
            val startMinute = startParts[1].toIntOrNull() ?: continue
            val startTotalMinutes = startHour * 60 + startMinute

            // 找紧邻前一节课：同一时段可能多门课，不能直接用 index-1
            val prevCourse = todayCourses.take(index).lastOrNull { prev ->
                isConsecutiveCourse(prev, course)
            }

            val triggerMinutes = if (prevCourse != null) {
                val prevEndTime = getCourseEndTime(prevCourse, repository) ?: continue
                val prevEndParts = prevEndTime.split(":")
                if (prevEndParts.size != 2) continue
                val prevEndHour = prevEndParts[0].toIntOrNull() ?: continue
                val prevEndMinute = prevEndParts[1].toIntOrNull() ?: continue
                val prevEndTotalMinutes = prevEndHour * 60 + prevEndMinute

                val breakMinutes = startTotalMinutes - prevEndTotalMinutes
                if (breakMinutes < minutesBefore) {
                    // 课间短于提前提醒：改为上一节下课时立即提醒，否则会落进上一节课堂时间
                    maxOf(0, prevEndTotalMinutes)
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
                // 已过触发时间且课未开始：立即补发，用统一 dedupId 避免重复/漏发
                android.util.Log.d(TAG, "schedulePre: immediate-branch ${course.name} cur=$currentMinutes trigger=$triggerMinutes start=$startTotalMinutes inWindow=${currentMinutes < startTotalMinutes}")
                if (currentMinutes < startTotalMinutes) {
                    val dedupId = "${course.name}|${course.getTimeDisplayText()}|$startTime"
                    val alreadySent = isPreClassSentRecently(context, dedupId) ||
                        (useIsland && hasIslandPreClassSentToday(context, dedupId))
                    if (!alreadySent) {
                        val startMillis = todayMillis(startHour, startMinute)
                        val endMillis = parseTimeToTodayMillis(getCourseEndTime(course, repository))
                        sendPreClassNotification(
                            context, alarmManager, repository, course, startTime,
                            useIsland, startMillis, endMillis
                        )
                        android.util.Log.d(TAG, "schedulePre: immediate-SENT ${course.name}")
                    }
                }
                continue
            }
            android.util.Log.d(TAG, "schedulePre: scheduling-alarm ${course.name} trigger=$triggerMinutes start=$startTotalMinutes")

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_TYPE, TYPE_PRE_CLASS)
                putExtra(EXTRA_COURSE_NAME, course.name)
                putExtra(EXTRA_COURSE_SECTION, course.getTimeDisplayText())
                // START_TIME 作 fallback（旧闹钟无 EXTRA_COURSE_ID 时按 name+section+time 匹配）
                putExtra(EXTRA_COURSE_START_TIME, startTime)
                putExtra(EXTRA_COURSE_ID, course.id)
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
                scheduledRcs.add(course.id.hashCode())
            } catch (_: SecurityException) { }
        }

        writeRcSet(context, KEY_PRE_CLASS_RCS, scheduledRcs)
    }

    private fun scheduleNextDayAlarm(
        context: Context,
        repository: CourseRepository,
        alarmManager: AlarmManager
    ) {
        if (!isSemesterStarted(repository)) return

        val currentWeek = repository.getCurrentWeek()
        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        if (currentWeek < 1 || currentWeek > totalWeeks || currentWeek > lastWeekWithCourses) return

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
        // 链式调度：每次触发后由 WidgetRefreshReceiver 重新注册下一次
        scheduleNextWidgetRefresh(context, alarmManager)
    }

    // 有课进行中/临近课：下一分钟整点（并对齐上课瞬间）；否则下次课程前或 30 分钟
    fun computeNextWidgetRefreshTime(context: Context): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val repository = CourseRepository(context)
        val todayCourses = getTodayCourses(context)

        // 倒计时/岛激活时需每分钟刷新，便于更新文案与对账补切
        val countdownPrefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        val hasActiveCountdown = countdownPrefs.getBoolean("active", false) ||
            IslandNotificationHelper.IslandState.isActive(context)

        // 课前提醒窗口内也保持每分钟刷新，保证补发与倒计时都被驱动
        val minutesBefore = repository.getPreClassReminderMinutes()
        android.util.Log.d(TAG, "computeNext: entered cur=$currentMinutes b=$minutesBefore todayCourses=${todayCourses.size} countdown=$hasActiveCountdown")
        todayCourses.forEach { android.util.Log.d(TAG, "computeNext:   c=${it.name} s=${getCourseStartTime(it, repository)} e=${getCourseEndTime(it, repository)}") }
        var hasActiveCourse = hasActiveCountdown
        var nextEventTime: Long? = null
        // 用于把刷新对齐到上课瞬间，避免倒计时归零后等一整分钟才切"已上课"
        var imminentStartMillis: Long? = null

        for (course in todayCourses) {
            val startTime = getCourseStartTime(course, repository)
            val endTime = getCourseEndTime(course, repository)
            if (startTime == null || endTime == null) continue

            val startParts = startTime.split(":")
            val endParts = endTime.split(":")
            if (startParts.size != 2 || endParts.size != 2) continue

            val startMin = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
            val endMin = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)

            if (currentMinutes in startMin until endMin) {
                hasActiveCourse = true
                break
            }

            val minutesToStart = startMin - currentMinutes
            if (minutesToStart >= 0 && minutesToStart <= minutesBefore) {
                hasActiveCourse = true
                imminentStartMillis = parseTimeToTodayMillis(startTime).takeIf { it > 0 }
                break
            }

            if (minutesToStart > 0) {
                val startCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, startParts[0].toInt())
                    set(Calendar.MINUTE, startParts[1].toInt())
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (startCal.timeInMillis > now) {
                    if (nextEventTime == null || startCal.timeInMillis < nextEventTime) {
                        nextEventTime = startCal.timeInMillis
                    }
                }
            }
        }

        val result = if (hasActiveCourse) {
            val nextMinute = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val start = imminentStartMillis
            if (start != null && start > now) minOf(nextMinute, start + 1_000L) else nextMinute
        } else if (nextEventTime != null) {
            // 提前 5 分钟进入倒计时，最迟 30 分钟避免跨日检测延迟
            val earlyRefresh = nextEventTime - 5 * 60 * 1000L
            val maxRefresh = now + 30 * 60 * 1000L
            minOf(earlyRefresh, maxRefresh)
        } else {
            now + 30 * 60 * 1000L
        }
        // clamp 到 now+1s，否则过去时间会让 setExactAndAllowWhileIdle 立即触发连刷
        val safe = maxOf(result, now + 1_000L)
        android.util.Log.d(TAG, "computeNext: RESULT active=$hasActiveCourse nextEvent=$nextEventTime ret=${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(safe))}")
        return safe
    }

    // Doze 下用 setExactAndAllowWhileIdle 保证精确触发
    fun scheduleNextWidgetRefresh(context: Context, alarmManager: AlarmManager) {
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

        val triggerAt = computeNextWidgetRefreshTime(context)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (_: SecurityException) { }
    }

    // 只按节次相邻判断；不限上午/下午分段，否则跨段连堂识别不出、提醒会落进上一节课堂
    private fun isConsecutiveCourse(prev: Course, current: Course): Boolean {
        return prev.endSection + 1 == current.startSection
    }

    fun getTodayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }

    // 新代码直接用 CourseTimeResolver
    fun getCourseStartTime(course: Course, repository: CourseRepository): String? =
        com.haooz.chedule.data.CourseTimeResolver.getStartTime(course, repository)

    fun getCourseEndTime(course: Course, repository: CourseRepository): String? =
        com.haooz.chedule.data.CourseTimeResolver.getEndTime(course, repository)

    fun getTomorrowCourses(context: Context): List<Course> {
        val repository = CourseRepository(context)
        val todayDate = LocalDate.now()
        val tomorrowDate = todayDate.plusDays(1)
        if (HolidayManager.isHoliday(context, tomorrowDate)) return emptyList()
        val todayEntry = HolidayManager.workSwap(context, todayDate)
        val tomorrowEntry = HolidayManager.workSwap(context, tomorrowDate)
        val currentWeek = todayEntry?.followWeek?.takeIf { it > 0 } ?: repository.getCurrentWeek()
        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        val courses = repository.getAllCourses()
        val tomorrowDayOfWeek = tomorrowEntry?.followWeekday?.takeIf { it in 1..7 }
            ?: tomorrowDate.dayOfWeek.value
        val tomorrowWeek = tomorrowEntry?.followWeek?.takeIf { it > 0 }
            ?: if (tomorrowDayOfWeek == 1) currentWeek + 1 else currentWeek

        if (tomorrowWeek < 1 || tomorrowWeek > totalWeeks || tomorrowWeek > lastWeekWithCourses) return emptyList()

        return courses.filter { course ->
            course.dayOfWeek == tomorrowDayOfWeek && course.isActiveInWeek(tomorrowWeek)
        }.sortedBy { getCourseStartTime(it, repository).toMinutes() }
    }

    fun getTodayCourses(context: Context): List<Course> {
        val repository = CourseRepository(context)
        val date = LocalDate.now()
        if (HolidayManager.isHoliday(context, date)) {
            android.util.Log.d(TAG, "getToday: RETURN holiday")
            return emptyList()
        }
        val workSwap = HolidayManager.workSwap(context, date)
        val currentWeek = workSwap?.followWeek?.takeIf { it > 0 } ?: repository.getCurrentWeek()
        val today = workSwap?.followWeekday?.takeIf { it in 1..7 } ?: getTodayOfWeek()
        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        android.util.Log.d(TAG, "getToday: date=$date week=$currentWeek today=$today total=$totalWeeks lastWeek=$lastWeekWithCourses all=${repository.getAllCourses().size}")
        if (currentWeek > totalWeeks || currentWeek > lastWeekWithCourses) {
            android.util.Log.d(TAG, "getToday: RETURN weekOutOfRange cur=$currentWeek total=$totalWeeks last=$lastWeekWithCourses")
            return emptyList()
        }
        return repository.getAllCourses()
            .filter { it.dayOfWeek == today && it.isActiveInWeek(currentWeek) }
            .sortedBy { getCourseStartTime(it, repository).toMinutes() }
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

    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.canPostPromotedNotifications()
    }

    private fun ensureNotificationChannels(context: Context) {
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

    fun showReminderNotification(context: Context, id: Int, title: String, message: String) {
        ensureNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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

        val dndIntent = Intent(context, ClassDndReceiver::class.java).apply {
            action = ClassDndReceiver.ACTION_TOGGLE
        }
        val dndPendingIntent = PendingIntent.getBroadcast(
            context,
            courseName.hashCode() + 100,
            dndIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = 10000 + courseName.hashCode()
        // 向上取整，与系统倒计时剩余秒数一致
        val minutesUntilStart = ceilMinutesUntil(startMillis)

        // 右侧显示模式：0=课程名称，1=上课地点，2=倒计时
        val reminderPrefs = context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
        val collapsedMode = reminderPrefs.getInt("live_right_mode", 0)
        val shortCriticalText = when (collapsedMode) {
            0 -> courseName
            1 -> classroom.ifEmpty { courseName }
            2 -> "${minutesUntilStart}分钟"
            else -> courseName
        }

        val bigText = buildString {
            if (startTime.isNotEmpty()) append(startTime)
            if (classroom.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(classroom)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_LIVE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$courseName | 即将上课")
            .setShortCriticalText(shortCriticalText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
            )
            .setWhen(startMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setRequestPromotedOngoing(true)
            .addAction(R.drawable.ic_notification_calendar, "查看课表", contentIntent)
            .addAction(R.drawable.ic_notification_mute, "上课勿扰", dndPendingIntent)
            .apply {
                val timeout = endMillis - System.currentTimeMillis()
                if (timeout > 0) setTimeoutAfter(timeout)
            }
            .build()
            .apply {
                flags = flags or Notification.FLAG_ONLY_ALERT_ONCE
            }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)

        // 倒计时状态供 WidgetRefreshReceiver 每分钟更新
        val prefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("active", true)
                .putString("courseName", courseName)
                .putString("classroom", classroom)
                .putString("section", section)
                .putString("startTime", startTime)
                .putLong("startMillis", startMillis)
                .putLong("endMillis", endMillis)
                .putInt("notificationId", notificationId)
                .remove("last_displayed_minutes")
        }

        // 精确闹钟保证到点可靠更新
            val delay = startMillis - System.currentTimeMillis()
        if (delay > 0) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = startMillis + 100L
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

        // 启动 widget 刷新链，确保倒计时每分钟更新
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleNextWidgetRefresh(context, alarmManager)
    }

    // 每分钟由 WidgetRefreshReceiver 驱动；同 notifyId 重复 notify 无痕更新
    fun updateActiveCountdown(context: Context) {
        val prefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return

        // 岛模式下不显示实况通知：收起残留实况，改由岛状态对账接管
        val repository = CourseRepository(context)
        if (repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)) {
            if (prefs.getBoolean("active", false)) {
                val liveNotificationId = prefs.getInt("notificationId", 0)
                if (liveNotificationId != 0) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(liveNotificationId)
                }
                prefs.edit { putBoolean("active", false) }
            }
            reconcileIslandCountdown(context)
            return
        }

        val startMillis = prefs.getLong("startMillis", 0L)
        val endMillis = prefs.getLong("endMillis", 0L)
        val notificationId = prefs.getInt("notificationId", 0)
        val courseName = prefs.getString("courseName", "") ?: ""
        val classroom = prefs.getString("classroom", "") ?: ""
        val startTime = prefs.getString("startTime", "") ?: ""

        val now = System.currentTimeMillis()

        // 课程结束：取消通知并清状态
        if (endMillis in 1..now) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
            manager.cancel(ISLAND_NOTIFICATION_ID)
            prefs.edit { putBoolean("active", false) }
            return
        }

        // 到点：取消倒计时，另发"已上课"实况
        if (now >= startMillis) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
            manager.cancel(ISLAND_NOTIFICATION_ID)
            prefs.edit { putBoolean("active", false) }

            val startedIntent = PendingIntent.getActivity(
                context, courseName.hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dndIntent = Intent(context, ClassDndReceiver::class.java).apply {
                action = ClassDndReceiver.ACTION_TOGGLE
            }
            val dndPendingIntent = PendingIntent.getBroadcast(
                context,
                courseName.hashCode() + 100,
                dndIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val bigText = buildString {
                if (startTime.isNotEmpty()) append(startTime)
                if (classroom.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(classroom)
                }
            }

            val startedNotification = NotificationCompat.Builder(context, CHANNEL_LIVE_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("$courseName | 已上课")
                .setShortCriticalText("已上课")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(bigText)
                )
                .setOngoing(true)
                .setContentIntent(startedIntent)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setRequestPromotedOngoing(true)
                .addAction(R.drawable.ic_notification_calendar, "查看课表", startedIntent)
                .addAction(R.drawable.ic_notification_mute, "上课勿扰", dndPendingIntent)
                .setTimeoutAfter(15_000L)
                .build()
                .apply {
                    flags = flags or Notification.FLAG_ONLY_ALERT_ONCE
                }
            manager.notify(notificationId, startedNotification)
            return
        }

        val collapsedPrefs = context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
        val collapsedMode = collapsedPrefs.getInt("live_right_mode", 0)

        // 非倒计时模式内容不变，无需每分钟重建
        if (collapsedMode != 2) return

        val minutesUntilStart = ceilMinutesUntil(startMillis, now)

        // 只在分钟数变化时重建
        val lastDisplayedMinutes = prefs.getInt("last_displayed_minutes", -1)
        if (minutesUntilStart == lastDisplayedMinutes) return
        val shortCriticalText = "${minutesUntilStart}分钟"

        val bigText = buildString {
            if (startTime.isNotEmpty()) append(startTime)
            if (classroom.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(classroom)
            }
        }

        val contentIntent = PendingIntent.getActivity(
            context, courseName.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dndIntent = Intent(context, ClassDndReceiver::class.java).apply {
            action = ClassDndReceiver.ACTION_TOGGLE
        }
        val dndPendingIntent = PendingIntent.getBroadcast(
            context,
            courseName.hashCode() + 100,
            dndIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LIVE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$courseName | 即将上课")
            .setShortCriticalText(shortCriticalText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
            )
            .setWhen(startMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setRequestPromotedOngoing(true)
            .addAction(R.drawable.ic_notification_calendar, "查看课表", contentIntent)
            .addAction(R.drawable.ic_notification_mute, "上课勿扰", dndPendingIntent)
            .apply {
                val timeout = endMillis - now
                if (timeout > 0) setTimeoutAfter(timeout)
            }
            .build()
            .apply {
                flags = flags or Notification.FLAG_ONLY_ALERT_ONCE
            }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)

        prefs.edit { putInt("last_displayed_minutes", minutesUntilStart) }
    }

    // 立即补发课前提醒（含去重）。调用方负责判断是否处于提醒窗口内。
    // courseStartMillis 是岛倒计时/文案/切换闹钟的唯一真源
    fun sendPreClassNotification(
        context: Context,
        alarmManager: AlarmManager,
        repository: CourseRepository,
        course: Course,
        startTime: String,
        useIsland: Boolean,
        courseStartMillis: Long,
        courseEndMillis: Long = 0L
    ) {
        // 校验通过后再记录去重，避免无效时间污染 60 分钟窗口拦截后续合法发送
        if (courseStartMillis <= 0L) return
        val dedupId = "${course.name}|${course.getTimeDisplayText()}|$startTime"
        recordPreClassSent(context, dedupId)

        val safeEndMillis = courseEndMillis.coerceAtLeast(0L)
        val nowMs = System.currentTimeMillis()

        if (courseStartMillis <= nowMs) {
            // 连堂课间为 0 时触发点可能已过上课时刻：落"已上课"；超宽限期则不再打扰
            val elapsed = nowMs - courseStartMillis
            if (elapsed > ISLAND_START_GRACE_MS) {
                android.util.Log.d(TAG, "sendPreClass: ${course.name} started ${elapsed}ms ago, too late, skip")
                return
            }
            android.util.Log.d(TAG, "sendPreClass: ${course.name} just started, show started state")
            if (useIsland) {
                IslandNotificationHelper.sendPreClassIslandNotification(
                    context = context,
                    courseName = course.name,
                    classroom = course.classroom,
                    section = course.getTimeDisplayText(),
                    startTime = startTime,
                    endTime = getCourseEndTime(course, repository),
                    teacher = course.teacher,
                    courseStartMillis = courseStartMillis,
                    courseEndMillis = safeEndMillis,
                    notificationId = ISLAND_NOTIFICATION_ID
                )
            } else {
                showPreClassCountdownNotification(
                    context, course.name, course.classroom, course.getTimeDisplayText(),
                    startTime, courseStartMillis, safeEndMillis
                )
                updateActiveCountdown(context)
            }
            return
        }

        if (useIsland) {
            val endTime = getCourseEndTime(course, repository)
            IslandNotificationHelper.sendPreClassIslandNotification(
                context = context,
                courseName = course.name,
                classroom = course.classroom,
                section = course.getTimeDisplayText(),
                startTime = startTime,
                endTime = endTime,
                teacher = course.teacher,
                courseStartMillis = courseStartMillis,
                courseEndMillis = safeEndMillis,
                notificationId = ISLAND_NOTIFICATION_ID
            )
            scheduleIslandExpandAlarm(
                context, alarmManager, course.name, course.classroom,
                course.getTimeDisplayText(), startTime, endTime, courseStartMillis
            )
        } else {
            showPreClassCountdownNotification(
                context, course.name, course.classroom, course.getTimeDisplayText(),
                startTime, courseStartMillis, safeEndMillis
            )
        }
    }

    // RC 按自纪元起的分钟数 mod 100000 派生：同一天不会撞号
    private fun expandRequestCode(courseStartMillis: Long): Int =
        ISLAND_EXPAND_RC_BASE +
            kotlin.math.abs((courseStartMillis / 60_000L % 100_000L).toInt())

    fun scheduleIslandExpandAlarm(
        context: Context,
        alarmManager: AlarmManager,
        courseName: String,
        classroom: String,
        section: String,
        startTime: String,
        endTime: String?,
        courseStartMillis: Long,
        notificationId: Int = ISLAND_NOTIFICATION_ID
    ) {
        val expandIntent = Intent(context, IslandExpandReceiver::class.java).apply {
            putExtra(IslandExpandReceiver.EXTRA_COURSE_NAME, courseName)
            putExtra(IslandExpandReceiver.EXTRA_CLASSROOM, classroom)
            putExtra(IslandExpandReceiver.EXTRA_SECTION, section)
            putExtra(IslandExpandReceiver.EXTRA_START_TIME, startTime)
            putExtra(IslandExpandReceiver.EXTRA_END_TIME, endTime ?: "")
            // 必须与倒计时岛同 ID，"已上课"才能原地替换而非另起一个岛
            putExtra(IslandExpandReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(IslandExpandReceiver.EXTRA_COURSE_START_MILLIS, courseStartMillis)
        }
        val rc = expandRequestCode(courseStartMillis)
        val expandPending = PendingIntent.getBroadcast(
            context, rc, expandIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                courseStartMillis,
                expandPending
            )
            writeRcSet(context, KEY_EXPAND_RCS, readRcSet(context, KEY_EXPAND_RCS) + rc)
        } catch (_: SecurityException) { }
    }

    // 每分钟对账：兜底切换闹钟丢失/Doze 延迟、岛残留、开关关闭后的清理
    fun reconcileIslandCountdown(context: Context) {
        val repository = CourseRepository(context)
        val islandEnabled = repository.getIslandNotification() &&
            IslandNotificationHelper.isIslandSupported(context)

        if (!islandEnabled) {
            if (IslandNotificationHelper.IslandState.isActive(context)) {
                IslandNotificationHelper.cancelIslandNotifications(context)
                IslandNotificationHelper.IslandState.clear(context)
                android.util.Log.d(TAG, "reconcileIsland: island disabled, dismissed")
            }
            return
        }

        val state = IslandNotificationHelper.IslandState.snapshot(context) ?: return
        val now = System.currentTimeMillis()

        val expiredByEnd = state.endMillis > 0 && now >= state.endMillis
        val expiredByShow = state.switched &&
            now - state.switchedAt >= IslandNotificationHelper.ISLAND_STARTED_VISIBLE_MS
        if (expiredByEnd || expiredByShow) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(state.notificationId)
            IslandNotificationHelper.IslandState.clear(context)
            android.util.Log.d(TAG, "reconcileIsland: dismissed end=$expiredByEnd show=$expiredByShow")
            return
        }

        // 到点但还没切换（闹钟丢失/延迟）→ 立即补切，最多滞后一个刷新周期
        if (!state.switched && now >= state.startMillis) {
            if (now - state.startMillis > ISLAND_LATE_TOLERANCE_MS) {
                // 隔夜或重启后残留的状态：不要再补一个过期的"已上课"，直接收起
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(state.notificationId)
                IslandNotificationHelper.IslandState.clear(context)
                android.util.Log.d(TAG, "reconcileIsland: stale state dismissed for ${state.courseName}")
                return
            }
            android.util.Log.d(TAG, "reconcileIsland: late switch for ${state.courseName}")
            IslandNotificationHelper.sendClassStartedNotification(
                context = context,
                courseName = state.courseName,
                classroom = state.classroom,
                section = state.section,
                startTime = state.startTime,
                endTime = state.endTime.ifEmpty { null },
                notificationId = state.notificationId
            )
        }
    }

    // 关闭提醒时清理实况/岛残留
    fun cancelAllReminderNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val countdownPrefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        val liveId = countdownPrefs.getInt("notificationId", 0)
        if (liveId != 0) manager.cancel(liveId)
        countdownPrefs.edit { putBoolean("active", false) }
        IslandNotificationHelper.cancelIslandNotifications(context)
        IslandNotificationHelper.IslandState.clear(context)
    }

    // 每分钟兜底补发：窗口内且未发送的课立即补发
    fun checkPendingPreClassReminders(context: Context) {
        val repository = CourseRepository(context)
        if (!repository.getPreClassReminder()) {
            android.util.Log.d(TAG, "checkPending: preClassReminder OFF")
            return
        }
        if (!isSemesterStarted(repository)) {
            android.util.Log.d(TAG, "checkPending: semesterNotStarted")
            return
        }

        // 复用 getTodayCourses，勿再内联过滤逻辑
        val courses = getTodayCourses(context)
        if (courses.isEmpty()) {
            android.util.Log.d(TAG, "checkPending: noCoursesForToday")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val minutesBefore = repository.getPreClassReminderMinutes()
        val useIsland = repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        android.util.Log.d(TAG, "checkPending: RUN minutesBefore=$minutesBefore current=$currentMinutes courses=${courses.size}")

        for ((index, course) in courses.withIndex()) {
            val startTime = getCourseStartTime(course, repository) ?: continue
            val startTotal = startTime.toMinutes()
            if (startTotal == Int.MAX_VALUE) continue

            // 连堂课窗口起点后移到上一节下课，与 schedulePreClassAlarms 一致
            var windowStart = startTotal - minutesBefore
            val prevCourse = courses.take(index).lastOrNull { isConsecutiveCourse(it, course) }
            if (prevCourse != null) {
                val prevEnd = getCourseEndTime(prevCourse, repository)?.toMinutes()
                if (prevEnd != null && prevEnd != Int.MAX_VALUE && startTotal - prevEnd < minutesBefore) {
                    windowStart = prevEnd
                }
            }
            val inWindow = currentMinutes in windowStart until startTotal
            val dedupId11 = "${course.name}|${course.getTimeDisplayText()}|$startTime"
            val dedupHit = isPreClassSentRecently(context, dedupId11)
            // 岛当天已发过则不重发（重发会触发岛重新弹出）
            val islandHit = useIsland && hasIslandPreClassSentToday(context, dedupId11)
            android.util.Log.d(TAG, "checkPending: ${course.name} start=$startTime cur=$currentMinutes win=[$windowStart,$startTotal) inWindow=$inWindow dedupHit=$dedupHit islandHit=$islandHit")
            if (!inWindow || dedupHit || islandHit) continue
            val startMillis = parseTimeToTodayMillis(startTime)
            val endMillis = parseTimeToTodayMillis(getCourseEndTime(course, repository))
            sendPreClassNotification(
                context, alarmManager, repository, course, startTime,
                useIsland, startMillis, endMillis
            )
            android.util.Log.d(TAG, "checkPending: SENT ${course.name}")
        }
    }

    // 非法返回 Int.MAX_VALUE 排到末尾
    private fun String?.toMinutes(): Int {
        if (this.isNullOrBlank()) return Int.MAX_VALUE
        val parts = this.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return h * 60 + m
    }
}
