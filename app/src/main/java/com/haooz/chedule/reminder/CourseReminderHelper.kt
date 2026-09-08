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
    // 课程唯一 ID：闹钟触发时用它回查课表，校验课程是否仍存在/时间是否已变更
    const val EXTRA_COURSE_ID = "course_id"
    // 注：原本还有 EXTRA_COURSE_CLASSROOM / TEACHER / END_TIME / START_MILLIS / END_MILLIS，
    // AlarmReceiver 重构后回查课表一律从 CourseRepository 取，这几个变成纯载荷噪声，已删除。

    const val TYPE_PRE_CLASS = 1
    const val TYPE_NEXT_DAY = 2

    const val WIDGET_REFRESH_REQUEST_CODE = 88888

    /**
     * 超级岛通知 ID：课前倒计时与"已上课"必须共用（与 IslandNotificationHelper 保持一致）。
     * 之前不同入口分别用了 1001/1003，导致两态各占一个通知同时停留在岛上。
     */
    const val ISLAND_NOTIFICATION_ID = IslandNotificationHelper.ISLAND_NOTIFICATION_ID

    /** "已上课"切换闹钟的 requestCode 基值，叠加 course.id.hashCode() 保证每门课互不覆盖 */
    private const val ISLAND_EXPAND_RC_BASE = 70000

    /** 对账补切"已上课"的最大容忍滞后：超过视为隔夜/重启残留，直接收起而不是补一个过期的上课态 */
    private const val ISLAND_LATE_TOLERANCE_MS = 5 * 60_000L

    /** 开课后仍允许补发"已上课"的宽限期，超出则视为过期闹钟不再打扰 */
    private const val ISLAND_START_GRACE_MS = 2 * 60_000L

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

    // 已注册闹钟的 requestCode 登记表。
    // 课程被删除、切换课表、云同步/导入重建课程（UUID 变化）后，旧闹钟不会出现在当前课程列表里，
    // 只按课程列表取消就会残留孤儿闹钟，到点后带着旧课程名/旧上课时间弹出（"提醒时间为旧数据"）。
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

    // ------------------------------------------------------------------------
    // 课程开始/结束时间戳：全链路唯一真源
    //
    // 之前存在四套互不相干的算法（字符串解析 + 跨天兜底、分钟级整数差、Calendar 快照、
    // 岛参数里的 now+minutesUntil 估算），这是"提醒时间为旧数据 / 倒计时提前或延迟切换"
    // 的总根因。所有通知、闹钟、岛倒计时一律改用下面两个函数。
    // ------------------------------------------------------------------------

    /** "HH:mm" -> 今天对应的时间戳（秒/毫秒归零）；非法返回 -1 */
    fun parseTimeToTodayMillis(time: String?): Long {
        if (time.isNullOrBlank()) return -1L
        val parts = time.split(":")
        if (parts.size < 2) return -1L
        val hour = parts[0].trim().toIntOrNull() ?: return -1L
        val minute = parts[1].trim().toIntOrNull() ?: return -1L
        if (hour !in 0..23 || minute !in 0..59) return -1L
        return todayMillis(hour, minute)
    }

    /** 距离上课还有多少分钟（向上取整），保证文案与系统倒计时剩余秒数一致 */
    private fun ceilMinutesUntil(startMillis: Long, now: Long = System.currentTimeMillis()): Int {
        val remain = startMillis - now
        if (remain <= 0) return 0
        return ((remain + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    /** 今天 hour:minute:00.000 的时间戳 */
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
            // 两个开关都关闭时，取消所有提醒相关的闹钟
            // 注意：widget 刷新闹钟独立于通知开关，不取消，避免桌面小部件停止刷新
            cancelAllAlarms(context, alarmManager)
            cancelIslandExpandAlarms(context, alarmManager)
            cancelCourseStartAlarms(context, alarmManager)
            // 屏幕上残留的倒计时/超级岛通知一并收起，否则关掉开关后岛还挂着
            cancelAllReminderNotifications(context)
            // 提醒总开关关闭 → 上课勿扰整体停用，并回收此前由本应用开启的勿扰
            ClassDndHelper.cancelClassDndAlarms(context, alarmManager)
            ClassDndHelper.applyCurrentState(context)
            // 仍要保持 widget 刷新链：只用小组件、不开提醒的用户必须靠它
            // 驱动跨日检测与每日刷新；项目约束要求"提醒关闭时刷新闹钟保持激活"。
            // 这里也覆盖了"开机后未打开 app 但开了小组件"的场景（BootCompletedReceiver
            // → startReminderService → 走到本分支也得把链子挂上）。
            scheduleNextWidgetRefresh(context, alarmManager)
            return
        }
        scheduleAllAlarms(context, repository, alarmManager)
        scheduleWidgetRefresh(context, alarmManager)
        // 立即对账勿扰状态：打开/关闭开关、切换课表后无需等闹钟
        ClassDndHelper.applyCurrentState(context)
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
        ClassDndHelper.cancelClassDndAlarms(context, alarmManager)
        ClassDndHelper.applyCurrentState(context)
        cancelAllReminderNotifications(context)
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
        // 上课/下课勿扰闹钟：只要提醒总开关开着就按当天课表注册
        ClassDndHelper.scheduleClassDndAlarms(context, alarmManager)
    }

    private fun cancelAllAlarms(context: Context, alarmManager: AlarmManager) {
        // 先按登记表取消历史注册过的所有课前闹钟，覆盖已删除/已换 ID 的孤儿闹钟
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

    /**
     * 取消所有 IslandExpandReceiver 闹钟（"已上课"切换触发器）
     * 覆盖正式通知（1001-1003）和测试通知（5000）的固定 ID
     */
    private fun cancelIslandExpandAlarms(context: Context, alarmManager: AlarmManager) {
        // 按登记表取消（按课程区分的 requestCode）
        for (rc in readRcSet(context, KEY_EXPAND_RCS)) {
            val pendingIntent = PendingIntent.getBroadcast(
                context, rc, Intent(context, IslandExpandReceiver::class.java), PI_FLAGS
            )
            alarmManager.cancel(pendingIntent)
        }
        writeRcSet(context, KEY_EXPAND_RCS, emptySet())

        // 历史版本用过的固定 ID，兜底清理
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
        if (!isSemesterStarted(repository)) {
            writeRcSet(context, KEY_PRE_CLASS_RCS, emptySet())
            return
        }

        val minutesBefore = repository.getPreClassReminderMinutes()

        android.util.Log.d(TAG, "schedulePre: entered semesterStarted=${isSemesterStarted(repository)} preReminder=${repository.getPreClassReminder()} minuteBefore=$minutesBefore")

        // getTodayCourses 已经处理好 workSwap / 周次范围 / 节假日 / dayOfWeek 过滤 / 按开始时间排序，
        // 之前这里内联了完全一样的逻辑（变量名 currentWeek/today/totalWeeks/lastWeekWithCourses 各算一遍），
        // 内联一多就容易在 workSwap 规则演化时漏改某一处。
        val todayCourses = getTodayCourses(context)
        if (todayCourses.isEmpty()) {
            android.util.Log.d(TAG, "schedulePre: RETURN noCoursesForToday")
            writeRcSet(context, KEY_PRE_CLASS_RCS, emptySet())
            return
        }
        android.util.Log.d(TAG, "schedulePre: todayCourses=${todayCourses.size}")
        todayCourses.forEach { android.util.Log.d(TAG, "schedulePre:   course=${it.name} day=${it.dayOfWeek} start=${getCourseStartTime(it, repository)} end=${getCourseEndTime(it, repository)}") }

        // 本次实际注册的 requestCode，登记后供下次 cancelAllAlarms 精确清理孤儿闹钟
        val scheduledRcs = mutableSetOf<Int>()

        val useIsland = repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)

        for ((index, course) in todayCourses.withIndex()) {
            val startTime = getCourseStartTime(course, repository) ?: continue
            val startParts = startTime.split(":")
            if (startParts.size != 2) continue
            val startHour = startParts[0].toIntOrNull() ?: continue
            val startMinute = startParts[1].toIntOrNull() ?: continue
            val startTotalMinutes = startHour * 60 + startMinute

            // 找出紧邻在前的那门课。不直接用 index - 1：同一时段可能有多门课，
            // 排序不稳定时会漏判连堂。
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
                    // 课间休息短于提前提醒时间：提前提醒会落进上一节课的课堂时间里，
                    // 因此改为上一节下课（prevEnd）的那一刻立即提醒。
                    // 注意不做 -1 分钟之类的偏移，用户要求就是"下课后立即发"。
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
                // 旧闹钟里写过的 EXTRA_COURSE_START_TIME 只是为了 AlarmReceiver 派发时识别身份，
                // 实际去重 ID 已经在下面 AlarmReceiver 改用"当前课表"新数据生成。
                // START_TIME 仍保留以做 fallback（旧闹钟没 EXTRA_COURSE_ID 时按 name+section+time 匹配）
                putExtra(EXTRA_COURSE_START_TIME, startTime)
                // 课程 ID：AlarmReceiver 触发时用它回查课表，校验课程是否仍存在/时间是否已变更
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
        // 超级岛倒计时同样需要每分钟驱动，以便闹钟丢失时能对账补切"已上课"
        val countdownPrefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        val hasActiveCountdown = countdownPrefs.getBoolean("active", false) ||
            IslandNotificationHelper.IslandState.isActive(context)

        // 检查是否有课程正在进行或即将开始（课前提醒窗口内）
        // 使用课前提醒提前量作为窗口，保证补发与倒计时在整个提醒窗口内每分钟被驱动
        val minutesBefore = repository.getPreClassReminderMinutes()
        android.util.Log.d(TAG, "computeNext: entered cur=$currentMinutes b=$minutesBefore todayCourses=${todayCourses.size} countdown=$hasActiveCountdown")
        todayCourses.forEach { android.util.Log.d(TAG, "computeNext:   c=${it.name} s=${getCourseStartTime(it, repository)} e=${getCourseEndTime(it, repository)}") }
        var hasActiveCourse = hasActiveCountdown
        var nextEventTime: Long? = null  // 下一个课程开始/结束时间
        // 提醒窗口内课程的精确上课时刻：用于把下一次刷新对齐到上课瞬间，
        // 避免倒计时归零后最多还要等一整分钟才切到"已上课"
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
                imminentStartMillis = parseTimeToTodayMillis(startTime).takeIf { it > 0 }
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
            }.timeInMillis
            // 额外对齐到上课时刻：否则倒计时归零后最多还要等一整分钟才切到"已上课"
            val start = imminentStartMillis
            if (start != null && start > now) minOf(nextMinute, start + 1_000L) else nextMinute
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
        // 防御性：若计算结果已落在过去（nextEventTime 在 5 分钟内、或上界 30min 已过），
        // clamp 到 now+1s，否则 setExactAndAllowWhileIdle 会立即触发造成短时间内多次刷新。
        val safe = maxOf(result, now + 1_000L)
        android.util.Log.d(TAG, "computeNext: RESULT active=$hasActiveCourse nextEvent=$nextEventTime ret=${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(safe))}")
        return safe
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

    /**
     * 上一门课与当前课是否紧邻（中间没有空节次）。
     *
     * 只按节次相邻判断，**不再限制"上午/下午/晚上"分段**：节次是全局递增的，
     * 第 4 节结束与第 5 节开始在时间上同样相邻。之前加了分段判断，导致
     * "3~4 节有课 + 5~6 节有课"这类跨段场景识别不出连堂，提醒会落进第 4 节的课堂时间里。
     */
    private fun isConsecutiveCourse(prev: Course, current: Course): Boolean {
        return prev.endSection + 1 == current.startSection
    }

    fun getTodayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }

    /**
     * @deprecated 解析逻辑已迁移到 [com.haooz.chedule.data.CourseTimeResolver]，
     * 保留此方法仅为兼容旧调用点。新代码请直接调用 `CourseTimeResolver.getStartTime/getEndTime`。
     */
    fun getCourseStartTime(course: Course, repository: CourseRepository): String? =
        com.haooz.chedule.data.CourseTimeResolver.getStartTime(course, repository)

    /** @deprecated 详见 [getCourseStartTime] */
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

        // 上课勿扰 PendingIntent：切换「上课自动开启勿扰」
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
        // 向上取整：与系统倒计时剩余秒数保持一致（剩余 55s 显示 1 分钟，而不是 2 分钟）
        val minutesUntilStart = ceilMinutesUntil(startMillis)

        // 读取实况通知右侧显示模式：0=课程名称，1=上课地点，2=倒计时
        val reminderPrefs = context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
        val collapsedMode = reminderPrefs.getInt("live_right_mode", 0)
        val shortCriticalText = when (collapsedMode) {
            0 -> courseName
            1 -> classroom.ifEmpty { courseName }
            2 -> "${minutesUntilStart}分钟"
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

        // 超级岛模式下不显示实况通知：收起可能残留的实况通知，并转由岛状态对账接管
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

        // 通知过期（课程结束），取消
        if (endMillis in 1..now) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
            // 顺带收起可能并存的岛通知（用常量而非硬编码 1003，避免改 ID 时遗漏）
            manager.cancel(ISLAND_NOTIFICATION_ID)
            prefs.edit { putBoolean("active", false) }
            return
        }

        // 上课时间到了，取消倒计时通知，另发一条"已上课"实况通知
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

            // 上课勿扰 PendingIntent：切换「上课自动开启勿扰」
            val dndIntent = Intent(context, ClassDndReceiver::class.java).apply {
                action = ClassDndReceiver.ACTION_TOGGLE
            }
            val dndPendingIntent = PendingIntent.getBroadcast(
                context,
                courseName.hashCode() + 100,
                dndIntent,
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
                .addAction(R.drawable.ic_notification_mute, "上课勿扰", dndPendingIntent)
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
        val minutesUntilStart = ceilMinutesUntil(startMillis, now)

        // 只在分钟数变化时才重建通知
        val lastDisplayedMinutes = prefs.getInt("last_displayed_minutes", -1)
        if (minutesUntilStart == lastDisplayedMinutes) return
        val shortCriticalText = "${minutesUntilStart}分钟"

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

        // 记录本次显示的分钟数
        prefs.edit { putInt("last_displayed_minutes", minutesUntilStart) }
    }

    /**
     * 立即补发课前提醒（含去重记录）。调用方负责判断是否处于提醒窗口内。
     *
     * @param courseStartMillis 课程开始的精确时间戳（"今天 HH:mm:00.000"），
     *                          岛倒计时、分钟文案、"已上课"切换闹钟全部以它为准，
     *                          不再各自用分钟差另算一套。
     * @param courseEndMillis   课程结束时间戳，用于到点自动收起；<=0 表示未知。
     */
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
            // 连堂课课间为 0（或课程时间重叠）时，"上一节下课后立即提醒"的触发点
            // 就是本节上课时刻甚至更晚，课前倒计时已无意义，直接落到"已上课"态。
            // 但只有刚开课不久才补发：被 Doze 严重延迟的旧闹钟不该在课后很久还弹一条过期提醒。
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

    /**
     * "已上课"切换闹钟的 requestCode：用课程开始时刻自纪元起的分钟数取模派生。
     *
     * 模 100000 远大于一天 1440 分钟，**同一天内不会撞**；跨日理论可撞但旧闹钟当日
     * 已触发/被取消，无实际影响。原先按当日分钟数（mod 1440）仍会被两门"同分钟
     * 开始"的课撞号；而按 hashCode % 20000 派生则存在任意小概率撞号。
     */
    private fun expandRequestCode(courseStartMillis: Long): Int =
        ISLAND_EXPAND_RC_BASE +
            kotlin.math.abs((courseStartMillis / 60_000L % 100_000L).toInt())

    /**
     * 注册"已上课"切换闹钟。
     *
     * 修复点：
     * - requestCode 之前固定为 1003，所有课程共用一个 PendingIntent，
     *   后一门课注册时会把前一门课的切换闹钟整体顶掉，导致倒计时结束后永远不切换。
     * - 触发时间之前是 startMillis + 1000ms，岛倒计时归零后还要多等一秒才切换。
     */
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
            // 必须与倒计时岛用同一个 ID，"已上课"才能原地替换掉倒计时，
            // 而不是另起一个岛、把倒计时岛永远留在 00:00
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

    /**
     * 超级岛状态对账：每分钟由 WidgetRefreshReceiver 驱动。
     *
     * 修复三类问题：
     * 1. "已上课"切换闹钟丢失 / 被 Doze 延迟 → 岛倒计时归零后卡住不切换（延迟切换上课态）
     * 2. 课程已结束但岛仍挂着（原先靠进程内 Handler 15 秒后收起，进程被杀即失效）
     * 3. 关闭超级岛开关后旧岛残留，与后续的实况倒计时重叠出现
     */
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

    /** 收起所有提醒相关通知（实况倒计时 + 超级岛），用于关闭提醒开关时清理残留 */
    fun cancelAllReminderNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val countdownPrefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        val liveId = countdownPrefs.getInt("notificationId", 0)
        if (liveId != 0) manager.cancel(liveId)
        countdownPrefs.edit { putBoolean("active", false) }
        IslandNotificationHelper.cancelIslandNotifications(context)
        IslandNotificationHelper.IslandState.clear(context)
    }

    /**
     * 每分钟由 WidgetRefreshReceiver 调用（兜底补发）。
     * 当某门课因闹钟未触发/丢失而漏发时，只要仍在"课前提醒窗口内"[startTime - 提前量, startTime)并未发送，
     * 立即补发（含超级岛通道）。开销极低：优先按 60 分钟去重窗口短路。
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

        // 复用 getTodayCourses：它已经处理 workSwap / 周次范围 / 节假日 / dayOfWeek 过滤 / 按开始时间排序，
        // 之前这里内联了一份完全相同的逻辑，变量名 todayOfWeek/totalWeeks3 等还易混淆。
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

            // 与 schedulePreClassAlarms 保持一致：连堂课课间短于提前提醒时间时，
            // 提醒窗口的起点要后移到上一节下课。否则兜底补发会在第 4 节课"上课途中"
            // 就弹出第 5 节课的提醒（闹钟那条链路已经推迟到下课，这里不跟着改就会绕过）。
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
            // 超级岛倒计时已发送过则当天不再重发（原生倒计时自行跳秒，重发会触发岛重新弹出）
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
