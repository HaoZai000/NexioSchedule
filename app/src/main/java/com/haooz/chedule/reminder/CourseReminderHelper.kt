/** 课程提醒助手 - 管理课程提醒的创建、取消和调度 */
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
import com.haooz.chedule.reminder.CourseReminderHelper.schedulePreClassAlarms
import com.haooz.chedule.ui.activities.MainActivity
import java.time.LocalDate
import java.util.Calendar

object CourseReminderHelper {

    private const val TAG = "CourseReminder"

    const val EXTRA_REMINDER_TYPE = "reminder_type"
    const val EXTRA_COURSE_NAME = "course_name"
    const val EXTRA_COURSE_CLASSROOM = "course_classroom"
    const val EXTRA_COURSE_SECTION = "course_section"
    const val EXTRA_COURSE_START_TIME = "course_start_time"
    const val EXTRA_COURSE_END_TIME = "course_end_time"
    const val EXTRA_COURSE_TEACHER = "course_teacher"
    const val EXTRA_OPEN_REMINDER_SETTINGS = "open_reminder_settings"
    const val EXTRA_COURSE_START_MILLIS = "course_start_millis"
    const val EXTRA_COURSE_END_MILLIS = "course_end_millis"

    const val TYPE_PRE_CLASS = 1
    const val TYPE_NEXT_DAY = 2

    const val WIDGET_REFRESH_REQUEST_CODE = 88888

    const val CHANNEL_REMINDER_ID = "course_reminder_alert"
    const val CHANNEL_REMINDER_NAME = "课程提醒通知"
    const val CHANNEL_LIVE_ID = "course_reminder_live"
    const val CHANNEL_LIVE_NAME = "课程提醒实况"

    // 发送去重统一存储：JSON map(courseId -> 最近发送时间戳) + 日期串，跨日自动失效。
    // 语义：
    //   - 常规倒计时 / 立即补发：同一课程在 60 分钟窗口内不重复发送（覆盖课前提醒倒计时窗口，
    //     避免倒计时过程中周期重发触发超级岛再次弹出）
    //   - 超级岛：同一课程当天只发一次（倒计时是原生 ChronometerCountDown，发一次系统即可自行跳秒，
    //     无需随每分钟刷新重发，否则重发会触发岛重新弹出）
    private const val PREF_SENT_HISTORY = "reminder_sent_history"
    private const val KEY_SENT_DAY = "reminder_sent_day"
    private const val KEY_SENT_MAP = "reminder_sent_map"
    private const val SENT_DEDUP_WINDOW_MS = 60 * 60 * 1000L

    // 跨日重调度检测
    private const val PREF_DAY_CHANGE = "day_change_state"
    private const val KEY_LAST_SCHEDULE_DATE = "last_schedule_date"

    /**
     * 记录已发送课前提醒的课程（统一的发送去重存储）。
     * 存储为 JSON map(courseId -> 最近发送时间戳)，同时记录对应日期；
     * 跨日后所有判空自动失效，从而保证"每个课程按窗口/当天粒度最多发送一次"。
     */
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

    /**
     * 判断该课程（按 courseId 精确匹配）是否在 60 分钟去重窗口内已发送过。
     * 用于常规倒计时 / 立即补发：同一课程在自身发送窗口内不重复发送。
     * courseId 按"课程名+时间段+当天开始时间"生成，不同课程互不相同，
     * 因此一门课已发送的记录不会影响其他课程的判断与补发（按各自 key 独立匹配）。
     * 同一日内的真正失效约束是上面的 60 分钟窗口；
     * "跨日"仅作兜底：存储日期非今天时整份记录视为未发送（自动重置），以便次日重新提醒。
     */
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

    /**
     * 判断超级岛倒计时当天是否已发送过：倒计时是原生 ChronometerCountDown，
     * 发一次系统即可自行跳秒，无需随每分钟刷新重发，否则重发会触发岛重新弹出。
     * 复用统一去重存储，按"当天 map 中已存在该课程"判定，跨日后自动失效。
     */
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

    /**
     * 跨日检测：若系统日期与上次调度日期不同，重新调度所有提醒。
     * 解决"次日课程无提醒"问题：原调度仅基于今天的课程，
     * 跨日后 widget refresh 触发本方法，自动为新一天重新注册课前/次日闹钟。
     *
     * 即使课前闹钟时间已过，[schedulePreClassAlarms] 的立即发送分支会兜底通知。
     * 每分钟由 WidgetRefreshReceiver 调用，开销极低（仅 SharedPreferences 读取）。
     */
    fun checkAndRescheduleOnDayChange(context: Context) {
        val today = getTodayDateString()
        val prefs = context.getSharedPreferences(PREF_DAY_CHANGE, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_SCHEDULE_DATE, null)

        if (lastDate == today) return

        // 日期变化（或首次运行），重新调度
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

    fun startReminderService(context: Context) {
        startReminderService(context, CourseRepository(context))
    }

    fun startReminderService(context: Context, repository: CourseRepository) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!repository.getPreClassReminder() && !repository.getNextDayReminder()) {
            // 两个开关都关闭时，取消所有提醒相关的闹钟
            // 注意：widget 刷新闹钟独立于通知开关，不取消，避免桌面小部件停止刷新
            cancelAllAlarms(context, alarmManager)
            cancelIslandExpandAlarms(context, alarmManager)
            cancelCourseStartAlarms(context, alarmManager)
            return
        }
        scheduleAllAlarms(context, repository, alarmManager)
        scheduleWidgetRefresh(context, alarmManager)
    }

    /**
     * 判断学期是否已开始：开学日期所在周的周一 <= 今天。
     * 即使 currentWeek 被误判为第 1 周，只要还没到开学周，课前/次日提醒都不应调度或发送。
     */
    fun isSemesterStarted(repository: CourseRepository): Boolean {
        return try {
            val start = LocalDate.parse(repository.getClassStartTime().replace("/", "-"))
            val startMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
            !LocalDate.now().isBefore(startMonday)
        } catch (_: Exception) {
            // 日期解析失败时保守放行，避免误屏蔽正常提醒
            true
        }
    }

    fun stopReminderService(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllAlarms(context, alarmManager)
        cancelIslandExpandAlarms(context, alarmManager)
        cancelCourseStartAlarms(context, alarmManager)
        // 注意：不取消 widget 刷新闹钟，避免桌面小部件停止刷新
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

    /**
     * 取消所有 IslandExpandReceiver 闹钟（"已上课"切换触发器）
     * 覆盖正式通知（1001-1003）和测试通知（5000）的固定 ID
     */
    private fun cancelIslandExpandAlarms(context: Context, alarmManager: AlarmManager) {
        // 所有用过的固定 ID（详见 IslandNotificationHelper 中的定义）
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

    /**
     * 取消所有 CourseStartReceiver 闹钟（非岛模式下的"已上课"切换）
     * request code 为 10000 + courseName.hashCode()
     */
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
        // 学期未开始（未到开学日期所在周的周一），不调度任何课前提醒
        if (!isSemesterStarted(repository)) return

        val minutesBefore = repository.getPreClassReminderMinutes()
        var currentWeek = repository.getCurrentWeek()
        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        val todayDate = LocalDate.now()
        if (HolidayManager.isHoliday(context, todayDate)) return
        val workSwap = HolidayManager.workSwap(context, todayDate)
        var today = getTodayOfWeek()
        val followWeek = workSwap?.followWeek ?: -1
        val followWeekday = workSwap?.followWeekday ?: -1
        if (followWeek > 0 && followWeekday > 0) {
            currentWeek = followWeek
            today = followWeekday
        }
        val allCourses = repository.getAllCourses()
        val morningSections = repository.getMorningSections()
        val afternoonSections = repository.getAfternoonSections()

        android.util.Log.d(TAG, "schedulePre: entered week=$currentWeek today=$today total=$totalWeeks last=$lastWeekWithCourses semesterStarted=${isSemesterStarted(repository)} holiday=${HolidayManager.isHoliday(context, todayDate)} preReminder=${repository.getPreClassReminder()} minuteBefore=$minutesBefore")

        if (currentWeek < 1 || currentWeek > totalWeeks || currentWeek > lastWeekWithCourses) {
            android.util.Log.d(TAG, "schedulePre: RETURN weekOutOfRange cur=$currentWeek")
            return
        }

        val todayCourses = allCourses.filter { course ->
            course.dayOfWeek == today && course.isActiveInWeek(currentWeek)
        }.sortedBy { getCourseStartTime(it, repository).toMinutes() }
        android.util.Log.d(TAG, "schedulePre: todayCourses=${todayCourses.size}")
        todayCourses.forEach { android.util.Log.d(TAG, "schedulePre:   course=${it.name} day=${it.dayOfWeek} start=${getCourseStartTime(it, repository)} end=${getCourseEndTime(it, repository)}") }

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
                // 已过触发时间，对未开始的课程发送立即通知（补发）。
                // 去重依赖统一的 isPreClassSentRecently / hasIslandPreClassSentToday：
                // 使用与 AlarmReceiver / checkPendingPreClassReminders 一致的 dedupId，
                // 避免自定义时间课程双发/漏发，也避免 startReminderService 重入（闹钟触发后、
                // 设置变更后）导致同一课程在短窗口内重复发送。
                android.util.Log.d(TAG, "schedulePre: immediate-branch ${course.name} cur=$currentMinutes trigger=$triggerMinutes start=$startTotalMinutes inWindow=${currentMinutes < startTotalMinutes}")
                if (currentMinutes < startTotalMinutes) {
                    val dedupId = "${course.name}|${course.getTimeDisplayText()}|$startTime"
                    val alreadySent = isPreClassSentRecently(context, dedupId) ||
                        (useIsland && hasIslandPreClassSentToday(context, dedupId))
                    if (!alreadySent) {
                        sendPreClassNotification(context, alarmManager, repository, course, startTime, useIsland)
                        android.util.Log.d(TAG, "schedulePre: immediate-SENT ${course.name}")
                    }
                }
                continue
            }
            android.util.Log.d(TAG, "schedulePre: scheduling-alarm ${course.name} trigger=$triggerMinutes start=$startTotalMinutes")

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_TYPE, TYPE_PRE_CLASS)
                putExtra(EXTRA_COURSE_NAME, course.name)
                putExtra(EXTRA_COURSE_CLASSROOM, course.classroom)
                putExtra(EXTRA_COURSE_SECTION, course.getTimeDisplayText())
                putExtra(EXTRA_COURSE_START_TIME, startTime)
                putExtra(EXTRA_COURSE_END_TIME, getCourseEndTime(course, repository) ?: "")
                putExtra(EXTRA_COURSE_TEACHER, course.teacher)
                // courseStartMillis 必须是课程实际上课时间，与 alarmTime（触发时间）解耦
                // 连堂课触发时间可能是上一节课的结束时间，不能用 alarmTime + minutesBefore
                val courseStartMillis = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, startHour)
                    set(Calendar.MINUTE, startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
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
            } catch (_: SecurityException) { }
        }
    }

    private fun scheduleNextDayAlarm(
        context: Context,
        repository: CourseRepository,
        alarmManager: AlarmManager
    ) {
        // 学期未开始（未到开学日期所在周的周一），不调度次日课程提醒
        if (!isSemesterStarted(repository)) return

        // 学期未开始或已结束，不再调度明日课程提醒
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
        // 根据是否有课进行中决定间隔，避免无课时每分钟唤醒
        scheduleNextWidgetRefresh(context, alarmManager)
    }

    /**
     * 计算下一次 widget 刷新的最佳触发时间：
     * - 有课程进行中：下一分钟整点（保证倒计时及时更新）
     * - 课程即将开始（5分钟内）：下一分钟整点（提前进入倒计时）
     * - 其他情况（无课/课程远未开始）：下一次课程开始时间或 30 分钟后取较早者
     *
     * @return 下一次刷新的时间戳（毫秒）
     */
    fun computeNextWidgetRefreshTime(context: Context): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val repository = CourseRepository(context)
        val todayCourses = getTodayCourses(context)

        // 倒计时通知激活时，需要每分钟刷新以更新倒计时文本
        val countdownPrefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        val hasActiveCountdown = countdownPrefs.getBoolean("active", false)

        // 检查是否有课程正在进行或即将开始（课前提醒窗口内）
        // 使用课前提醒提前量作为窗口，保证补发与倒计时在整个提醒窗口内每分钟被驱动
        val minutesBefore = repository.getPreClassReminderMinutes()
        android.util.Log.d(TAG, "computeNext: entered cur=$currentMinutes b=$minutesBefore todayCourses=${todayCourses.size} countdown=$hasActiveCountdown")
        todayCourses.forEach { android.util.Log.d(TAG, "computeNext:   c=${it.name} s=${getCourseStartTime(it, repository)} e=${getCourseEndTime(it, repository)}") }
        var hasActiveCourse = hasActiveCountdown
        var nextEventTime: Long? = null  // 下一个课程开始/结束时间

        for (course in todayCourses) {
            val startTime = getCourseStartTime(course, repository)
            val endTime = getCourseEndTime(course, repository)
            if (startTime == null || endTime == null) continue

            val startParts = startTime.split(":")
            val endParts = endTime.split(":")
            if (startParts.size != 2 || endParts.size != 2) continue

            val startMin = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
            val endMin = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)

            // 课程进行中：需要每分钟刷新
            if (currentMinutes in startMin until endMin) {
                hasActiveCourse = true
                break
            }

            // 课程即将开始（课前提醒窗口内）：整个窗口保持每分钟刷新，
            // 保证补发（checkPendingPreClassReminders）与倒计时都能及时驱动，不被单点闹钟错过
            val minutesToStart = startMin - currentMinutes
            if (minutesToStart >= 0 && minutesToStart <= minutesBefore) {
                hasActiveCourse = true
                break
            }

            // 记录下一个课程开始时间（仅未开始的课程）
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
            // 有课进行中：下一分钟整点刷新（对齐到分钟边界，确保倒计时及时变化）
            val nextMinute = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            nextMinute.timeInMillis
        } else if (nextEventTime != null) {
            // 无课但有未来课程：在课程开始时间前 5 分钟开始刷新（提前显示倒计时）
            // 但最迟不超过 30 分钟，避免长时间不刷新导致跨日检测延迟
            val earlyRefresh = nextEventTime - 5 * 60 * 1000L
            val maxRefresh = now + 30 * 60 * 1000L
            minOf(earlyRefresh, maxRefresh)
        } else {
            // 今日无课或课程已全部结束：30 分钟后刷新（用于跨日检测）
            now + 30 * 60 * 1000L
        }
        android.util.Log.d(TAG, "computeNext: RESULT active=$hasActiveCourse nextEvent=$nextEventTime ret=${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(result))}")
        return result
    }

    /**
     * 调度下一次 widget 刷新（链式调度，由 WidgetRefreshReceiver 每次触发后调用）
     * 使用 setExactAndAllowWhileIdle 保证在 Doze 模式下也能精确触发
     */
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
        // 先取消已有的刷新闹钟，避免重复
        alarmManager.cancel(pendingIntent)

        val triggerAt = computeNextWidgetRefreshTime(context)
        try {
            // 用 setExactAndAllowWhileIdle 保证在 Doze 下精确触发
            // 用 RTC_WAKEUP 唤醒 CPU 进行刷新
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (_: SecurityException) { }
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
        // 自定义上课时间优先
        if (course.hasValidCustomTime()) return course.customStartTime
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
        // 自定义上课时间优先
        if (course.hasValidCustomTime()) return course.customEndTime
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
            ?: if (tomorrowDate.dayOfWeek.value == 7) 7 else tomorrowDate.dayOfWeek.value
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

        // 静音模式 PendingIntent
        val muteIntent = Intent(context, MuteReceiver::class.java)
        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            courseName.hashCode() + 100,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = 10000 + courseName.hashCode()
        val minutesUntilStart = ((startMillis - System.currentTimeMillis()) / 60_000).toInt()

        // 读取实况通知右侧显示模式：0=课程名称，1=上课地点，2=倒计时
        val reminderPrefs = context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
        val collapsedMode = reminderPrefs.getInt("live_right_mode", 0)
        val shortCriticalText = when (collapsedMode) {
            0 -> courseName
            1 -> classroom.ifEmpty { courseName }
            2 -> "${minutesUntilStart +1}分钟"
            else -> courseName
        }

        // 构建大文本内容
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
            .addAction(R.drawable.ic_notification_mute, "立即静音", mutePendingIntent)
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

        // 保存倒计时状态，供 WidgetRefreshReceiver 每分钟更新
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

        // 倒计时到达后立即触发更新，使用精确闹钟确保可靠触发
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

    /**
     * 每分钟由 WidgetRefreshReceiver 调用，更新倒计时通知
     * 使用同一个 notifyId 重复调用 notify() 无痕更新UI
     */
    fun updateActiveCountdown(context: Context) {
        val prefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return

        // 超级岛模式下不显示实况通知
        val repository = CourseRepository(context)
        if (repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)) {
            prefs.edit { putBoolean("active", false) }
            return
        }

        val startMillis = prefs.getLong("startMillis", 0L)
        val endMillis = prefs.getLong("endMillis", 0L)
        val notificationId = prefs.getInt("notificationId", 0)
        val courseName = prefs.getString("courseName", "") ?: ""
        val classroom = prefs.getString("classroom", "") ?: ""
        val startTime = prefs.getString("startTime", "") ?: ""

        val now = System.currentTimeMillis()

        // 通知过期（课程结束），取消
        if (endMillis in 1..now) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
            manager.cancel(1003)
            prefs.edit { putBoolean("active", false) }
            return
        }

        // 上课时间到了，取消倒计时通知，另发一条"已上课"实况通知
        if (now >= startMillis) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
            manager.cancel(1003)
            prefs.edit { putBoolean("active", false) }

            val startedIntent = PendingIntent.getActivity(
                context, courseName.hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 静音模式 PendingIntent
            val muteIntent = Intent(context, MuteReceiver::class.java)
            val mutePendingIntent = PendingIntent.getBroadcast(
                context,
                courseName.hashCode() + 100,
                muteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 构建"已上课"大文本内容
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
                .addAction(R.drawable.ic_notification_mute, "立即静音", mutePendingIntent)
                .setTimeoutAfter(15_000L)
                .build()
                .apply {
                    flags = flags or Notification.FLAG_ONLY_ALERT_ONCE
                }
            manager.notify(notificationId, startedNotification)
            return
        }

        // 读取实况通知右侧显示模式
        val collapsedPrefs = context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
        val collapsedMode = collapsedPrefs.getInt("live_right_mode", 0)

        // 非倒计时模式（课程名/教室）内容不变，无需每分钟重建通知
        if (collapsedMode != 2) return

        // 更新倒计时通知，使用同一个 notifyId 无痕更新
        val minutesUntilStart = ((startMillis - now) / 60_000).toInt()

        // 只在分钟数变化时才重建通知
        val lastDisplayedMinutes = prefs.getInt("last_displayed_minutes", -1)
        if (minutesUntilStart == lastDisplayedMinutes) return
        val shortCriticalText = "${minutesUntilStart + 1}分钟"

        // 构建大文本内容
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

        val muteIntent = Intent(context, MuteReceiver::class.java)
        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            courseName.hashCode() + 100,
            muteIntent,
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
            .addAction(R.drawable.ic_notification_mute, "立即静音", mutePendingIntent)
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

        // 记录本次显示的分钟数
        prefs.edit { putInt("last_displayed_minutes", minutesUntilStart) }
    }

    /**
     * 立即补发课前提醒（含去重记录）。调用方负责判断是否处于提醒窗口内。
     */
    private fun sendPreClassNotification(
        context: Context,
        alarmManager: AlarmManager,
        repository: CourseRepository,
        course: Course,
        startTime: String,
        useIsland: Boolean
    ) {
        val dedupId = "${course.name}|${course.getTimeDisplayText()}|$startTime"
        recordPreClassSent(context, dedupId)

        val startParts = startTime.split(":")
        if (startParts.size != 2) return
        val startHour = startParts[0].toIntOrNull() ?: return
        val startMinute = startParts[1].toIntOrNull() ?: return

        if (useIsland) {
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val minutesUntil = startHour * 60 + startMinute - currentMinutes
            val endTime = getCourseEndTime(course, repository)
            IslandNotificationHelper.sendPreClassIslandNotification(
                context = context,
                courseName = course.name,
                classroom = course.classroom,
                section = course.getTimeDisplayText(),
                startTime = startTime,
                endTime = endTime,
                teacher = course.teacher,
                minutesUntil = minutesUntil,
                notificationId = 1003
            )

            // 课程开始时切换为"已上课"状态
            val courseStartMillis = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val expandIntent = Intent(context, IslandExpandReceiver::class.java).apply {
                putExtra(IslandExpandReceiver.EXTRA_COURSE_NAME, course.name)
                putExtra(IslandExpandReceiver.EXTRA_CLASSROOM, course.classroom)
                putExtra(IslandExpandReceiver.EXTRA_SECTION, course.getTimeDisplayText())
                putExtra(IslandExpandReceiver.EXTRA_START_TIME, startTime)
                putExtra(IslandExpandReceiver.EXTRA_END_TIME, endTime ?: "")
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
                context, course.name, course.classroom, course.getTimeDisplayText(),
                startTime, startMillis, endMillis
            )
        }
    }

    /**
     * 每分钟由 WidgetRefreshReceiver 调用（兜底补发）。
     * 当某门课因闹钟未触发/丢失而漏发时，只要仍在"课前提醒窗口内"[startTime - 提前量, startTime)并未发送，
     * 立即补发（含超级岛通道）。开销极低：优先按 10 分钟去重窗口短路。
     */
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
        val today9 = LocalDate.now()
        if (HolidayManager.isHoliday(context, today9)) {
            android.util.Log.d(TAG, "checkPending: holiday")
            return
        }
        val workSwap = HolidayManager.workSwap(context, today9)
        var currentWeek = repository.getCurrentWeek()
        var todayOfWeek = getTodayOfWeek()
        if (workSwap != null && workSwap.followWeek > 0 && workSwap.followWeekday > 0) {
            currentWeek = workSwap.followWeek
            todayOfWeek = workSwap.followWeekday
        }
        val totalWeeks3 = repository.getTotalWeeks()
        val lastWeekWithCourses3 = repository.getLastWeekWithCourses()
        if (currentWeek < 1 || currentWeek > totalWeeks3 || currentWeek > lastWeekWithCourses3) {
            android.util.Log.d(TAG, "checkPending: weekOutOfRange cur=$currentWeek total=$totalWeeks3 last=$lastWeekWithCourses3")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val minutesBefore = repository.getPreClassReminderMinutes()
        val useIsland = repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val courses = repository.getAllCourses()
            .filter { it.dayOfWeek == todayOfWeek && it.isActiveInWeek(currentWeek) }
            .sortedBy { getCourseStartTime(it, repository).toMinutes() }
        android.util.Log.d(TAG, "checkPending: RUN week=$currentWeek weekday=$todayOfWeek minutesBefore=$minutesBefore current=$currentMinutes courses=${courses.size}")

        for (course in courses) {
            val startTime = getCourseStartTime(course, repository) ?: continue
            val startTotal = startTime.toMinutes()
            if (startTotal == Int.MAX_VALUE) continue
            val windowStart = startTotal - minutesBefore
            val inWindow = currentMinutes in windowStart until startTotal
            val dedupId11 = "${course.name}|${course.getTimeDisplayText()}|$startTime"
            val dedupHit = isPreClassSentRecently(context, dedupId11)
            // 超级岛倒计时已发送过则当天不再重发（原生倒计时自行跳秒，重发会触发岛重新弹出）
            val islandHit = useIsland && hasIslandPreClassSentToday(context, dedupId11)
            android.util.Log.d(TAG, "checkPending: ${course.name} start=$startTime cur=$currentMinutes win=[$windowStart,$startTotal) inWindow=$inWindow dedupHit=$dedupHit islandHit=$islandHit")
            if (!inWindow || dedupHit || islandHit) continue
            sendPreClassNotification(context, alarmManager, repository, course, startTime, useIsland)
            android.util.Log.d(TAG, "checkPending: SENT ${course.name}")
        }
    }

    /** "HH:mm" -> 分钟数，用于排序；null/非法返回 Int.MAX_VALUE 排到末尾 */
    private fun String?.toMinutes(): Int {
        if (this.isNullOrBlank()) return Int.MAX_VALUE
        val parts = this.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return h * 60 + m
    }
}
