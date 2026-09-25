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
import androidx.core.content.edit
import com.haooz.chedule.R
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.CourseScheduleDateBounds
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.widget.WidgetUpdateCache
import java.time.LocalDate
import java.util.Calendar

object CourseReminderHelper {

    private const val TAG = "CourseReminder"

    // --- startReminderService 合并窗口所需状态（见 SERVICE_START_COALESCE_MS）---
    // 不能用 Long.MIN_VALUE：now - Long.MIN_VALUE 会溢出成负数，
    // 反而小于合并窗口 → 首次调用会被误判成「窗口内」，从此再也不真正执行。
    @Volatile
    private var lastServiceStartAt = -SERVICE_START_COALESCE_MS
    private val coalesceLock = Any()
    @Volatile
    private var coalescePending = false
    private val coalesceHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        android.os.Handler(android.os.Looper.getMainLooper())
    }

    @Volatile
    private var debugLogs: Boolean? = null

    /** 仅 debug 包打热路径日志，release 避免每分钟字符串拼接 */
    private fun debugEnabled(context: Context): Boolean {
        debugLogs?.let { return it }
        val enabled = try {
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Exception) {
            false
        }
        debugLogs = enabled
        return enabled
    }

    private fun logD(context: Context, message: String) {
        if (debugEnabled(context)) android.util.Log.d(TAG, message)
    }

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

    // 原生实况三态固定 ID，与超级岛 1003/1004/1005 同思路，不随课程名变化
    const val LIVE_COUNTDOWN_ID = 2003
    const val LIVE_STARTED_ID = 2004
    const val LIVE_IN_CLASS_ID = 2005
    // 测试实时活动独立 ID，对齐超级岛 5000/5001/5002，不覆盖真实课提醒
    const val LIVE_TEST_COUNTDOWN_ID = 5100
    const val LIVE_TEST_STARTED_ID = 5101
    const val LIVE_TEST_IN_CLASS_ID = 5102

    private fun liveCountdownId(testMode: Boolean): Int =
        if (testMode) LIVE_TEST_COUNTDOWN_ID else LIVE_COUNTDOWN_ID

    private fun liveStartedId(testMode: Boolean): Int =
        if (testMode) LIVE_TEST_STARTED_ID else LIVE_STARTED_ID

    private fun liveInClassId(testMode: Boolean): Int =
        if (testMode) LIVE_TEST_IN_CLASS_ID else LIVE_IN_CLASS_ID

    // 课中提醒统一开关（超级岛 / 原生实况共用；迁移旧 island_in_class_enabled / live_in_class_enabled）
    const val KEY_IN_CLASS = "in_class_reminder_enabled"
    // 提醒时机：0=全程，1=距下课 N 分钟（N>=课程总时长时按全程，避免溢出）
    const val KEY_IN_CLASS_TIMING_MODE = "in_class_timing_mode"
    const val KEY_IN_CLASS_LEAD_MINUTES = "in_class_lead_minutes"
    const val IN_CLASS_TIMING_FULL = 0
    const val IN_CLASS_TIMING_BEFORE_END = 1

    fun isInClassEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
        if (prefs.contains(KEY_IN_CLASS)) {
            return prefs.getBoolean(KEY_IN_CLASS, false)
        }
        // 旧键任一打开过则视为开启，避免升级后开关被静默关掉
        return prefs.getBoolean(IslandNotificationHelper.KEY_IN_CLASS_REMINDER, false) ||
            prefs.getBoolean("live_in_class_enabled", false)
    }

    fun isInClassLiveEnabled(context: Context): Boolean = isInClassEnabled(context)

    fun getInClassTimingMode(context: Context): Int {
        return context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
            .getInt(KEY_IN_CLASS_TIMING_MODE, IN_CLASS_TIMING_FULL)
    }

    fun getInClassLeadMinutes(context: Context): Int {
        return context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
            .getInt(KEY_IN_CLASS_LEAD_MINUTES, 10).coerceIn(0, 60)
    }

    /**
     * 当前是否应展示课中进度。
     * 距下课模式：剩余时长 <= N 分钟才展示；N 不小于本节课总时长时按全程，避免「设 60 分钟却比课还长」的溢出。
     */
    fun shouldShowInClassNow(
        context: Context,
        startMillis: Long,
        endMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isInClassEnabled(context)) return false
        if (endMillis <= startMillis) return false
        if (nowMillis < startMillis || nowMillis >= endMillis) return false
        if (getInClassTimingMode(context) == IN_CLASS_TIMING_FULL) return true
        val leadMs = getInClassLeadMinutes(context) * 60_000L
        val totalMs = endMillis - startMillis
        // 课总时长不足 lead → 等价全程
        if (leadMs >= totalMs) return true
        return (endMillis - nowMillis) <= leadMs
    }

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

    /**
     * 课表边界闹钟专用：课前提醒点、上课瞬间、下课瞬间。
     *
     * 用 setAlarmClock 而非 setExactAndAllowWhileIdle——后者受 Doze 的
     *「每应用每 9 分钟一次」配额限制，会被高频的对账刷新抢占而迟到最多约 8 分钟；
     * 而 setAlarmClock 不受该配额限制，系统会提前退出 Doze 保证准点。
     *
     * 代价：状态栏会显示闹钟图标，下一个闹钟时间会暴露给锁屏与其他应用。
     *
     * 只用于课表边界这类一次性关键时刻。**每分钟的对账刷新链绝不能用**，
     * 否则状态栏闹钟图标会一直亮着（刷新链走 scheduleNextWidgetRefresh）。
     */
    fun setCourseBoundaryAlarm(
        alarmManager: AlarmManager,
        triggerAt: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, null),
                pendingIntent
            )
        } catch (_: SecurityException) {
            // 精确闹钟权限被撤销时退回，至少不让闹钟静默丢失
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
     * 合并窗口。一次「切换课表」会同时惊动 CourseViewModel / ScheduleViewModel /
     * SettingsViewModel，各自调一次 startReminderService，实测 1ms 内触发 4 次，
     * 而每次都是 cancelAllAlarms + scheduleAllAlarms（全量重排）+ 写 SP。
     * 这些调用读的是同一份最新状态，重复执行毫无意义，所以窗口内只真正跑一次。
     */
    private const val SERVICE_START_COALESCE_MS = 400L

    fun startReminderService(context: Context) {
        startReminderService(context, CourseRepository(context))
    }

    fun startReminderService(context: Context, repository: CourseRepository) {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(coalesceLock) {
            if (now - lastServiceStartAt < SERVICE_START_COALESCE_MS) {
                // 命中窗口：本次不立即执行，只在窗口末尾补跑一次，保证最终状态一定生效
                scheduleCoalescedStartLocked(context)
                com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("service_start_coalesced") {
                    "sinceLast=${now - lastServiceStartAt}ms from=${callSite()}"
                }
                return
            }
            // 立即占位：检查与更新必须原子，否则两条并发线程会同时通过判断、各全量重排一次
            lastServiceStartAt = now
        }
        doStartReminderService(context, repository)
    }

    /**
     * 调用来源：跳过自身栈帧，回溯到第一个外部调用点。
     * 只在录制时求值（走 lambda 惰性），正式版未录制零成本。
     */
    private fun callSite(): String {
        val st = Thread.currentThread().stackTrace
        for (i in st.indices) {
            val e = st[i]
            val cn = e.className
            // 跳过自身与抓栈本身产生的内部帧（VMStack / Thread.getStackTrace）
            if (cn.contains("CourseReminderHelper") || cn.contains("VMStack") ||
                cn == "java.lang.Thread"
            ) continue
            return "${e.fileName}:${e.lineNumber} ${e.methodName}"
        }
        return "unknown"
    }

    private fun scheduleCoalescedStartLocked(context: Context) {
        val app = context.applicationContext
        if (coalescePending) return
        // 必须置位：补发回调靠它判断要不要真的跑，漏了这行补发会被永久跳过
        coalescePending = true
        coalesceHandler.postDelayed({
            synchronized(coalesceLock) {
                if (!coalescePending) return@postDelayed
                coalescePending = false
            }
            // 全量重排 + 写 SP，放后台线程，别占主线程
            Thread {
                runCatching { doStartReminderService(app, CourseRepository(app), fromCoalesced = true) }
            }.apply {
                isDaemon = true
                name = "reminder-coalesced"
            }.start()
        }, SERVICE_START_COALESCE_MS)
    }

    /** 关闭提醒时取消待补发，否则延迟的那次会把刚关掉的闹钟又排上 */
    private fun cancelCoalescedStart() {
        synchronized(coalesceLock) {
            coalescePending = false
        }
    }

    private fun doStartReminderService(
        context: Context,
        repository: CourseRepository,
        fromCoalesced: Boolean = false
    ) {
        lastServiceStartAt = android.os.SystemClock.elapsedRealtime()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pre = repository.getPreClassReminder()
        val next = repository.getNextDayReminder()
        // detail 惰性求值：未录制时连 dnd 这次 SP 读、这次抓栈都不做
        com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("service_start") {
            "pre=$pre next=$next dnd=${repository.getClassDndEnabled()} " +
                "coalesced=$fromCoalesced from=${callSite()}"
        }
        if (!pre && !next) {
            // 提醒关闭：取消本应用闹钟/通知，但 widget 刷新闹钟必须保留（只用小组件也要刷新）
            com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("service_start_path", "all_off_cleanup")
            cancelAllAlarms(context, alarmManager)
            cancelIslandExpandAlarms(context, alarmManager)
            cancelCourseStartAlarms(context, alarmManager)
            cancelAllReminderNotifications(context)
            ClassDndHelper.cancelClassDndAlarms(context, alarmManager)
            ClassDndHelper.applyCurrentState(context)
            scheduleNextWidgetRefresh(context, alarmManager)
            return
        }
        com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("service_start_path", "schedule_all")
        scheduleAllAlarms(context, repository, alarmManager)
        scheduleWidgetRefresh(context, alarmManager)
        // 开关切换后立即对账，不必等闹钟
        ClassDndHelper.applyCurrentState(context)
    }

    /**
     * 闹钟触发后的轻量收尾：只保 widget 刷新链与勿扰对账。
     * 今日课程闹钟在调度时已一次性注册，无需每次触发都 cancel+全量重装。
     * 课表变更仍由设置页 / ViewModel / 开机 / 跨日路径调用 startReminderService。
     */
    fun onAlarmProcessed(context: Context, fullReschedule: Boolean = false) {
        if (fullReschedule) {
            startReminderService(context)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleNextWidgetRefresh(context, alarmManager)
        ClassDndHelper.applyCurrentState(context)
    }

    /** 次日提醒触发后只注册下一个次日闹钟，避免全量重调度 */
    fun scheduleNextDayOnly(context: Context) {
        val repository = CourseRepository(context)
        if (!repository.getNextDayReminder() || !isSemesterStarted(repository)) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleNextDayAlarm(context, repository, alarmManager)
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
        com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("service_stop")
        cancelCoalescedStart()
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
        val intent = Intent(context, CourseStartReceiver::class.java)
        // 新固定 ID 的到点闹钟（真实 + 测试）
        for (rc in intArrayOf(LIVE_COUNTDOWN_ID, LIVE_TEST_COUNTDOWN_ID)) {
            val pending = PendingIntent.getBroadcast(
                context,
                rc,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pending)
        }
        // 旧版按课程名 hash 派生 RC 的残留
        val allCourses = CourseRepository(context).getAllCourses()
        for (course in allCourses) {
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

        logD(context, "schedulePre: semesterStarted=true minuteBefore=$minutesBefore")

        // 复用 getTodayCourses，勿再内联 workSwap/周次/节假日过滤
        val todayCourses = getTodayCourses(context)
        if (todayCourses.isEmpty()) {
            logD(context, "schedulePre: noCoursesForToday")
            writeRcSet(context, KEY_PRE_CLASS_RCS, emptySet())
            return
        }
        logD(context, "schedulePre: todayCourses=${todayCourses.size}")

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
                logD(context, "schedulePre: immediate ${course.name} cur=$currentMinutes trigger=$triggerMinutes")
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
                        logD(context, "schedulePre: immediate-SENT ${course.name}")
                    }
                }
                continue
            }
            logD(context, "schedulePre: alarm ${course.name} trigger=$triggerMinutes")

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

            setCourseBoundaryAlarm(alarmManager, alarmTime.timeInMillis, pendingIntent)
            scheduledRcs.add(course.id.hashCode())
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
            IslandNotificationHelper.IslandState.isActiveAny(context)

        // 课中提醒：剩余分钟与进度都按整分钟量化，只有跨分钟才会变，真实课与测试课同理。
        // 所以取活跃的那份快照对齐分钟边界即可——再密的轮询也只是在重复算同一个值、白白多唤醒。
        val inClassEndMillis = if (IslandNotificationHelper.isInClassReminderEnabled(context)) {
            // 真实岛与测试岛互斥，命中任一处于课中态的快照
            IslandNotificationHelper.IslandState.snapshot(context, testMode = false)
                ?.takeIf { it.switched && it.endMillis > now }?.endMillis
                ?: IslandNotificationHelper.IslandState.snapshot(context, testMode = true)
                    ?.takeIf { it.switched && it.endMillis > now }?.endMillis
        } else null

        // 课前提醒窗口内也保持每分钟刷新，保证补发与倒计时都被驱动
        val minutesBefore = repository.getPreClassReminderMinutes()
        logD(context, "computeNext: courses=${todayCourses.size} countdown=$hasActiveCountdown inClassEnd=$inClassEndMillis")
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

        val result = if (inClassEndMillis != null) {
            // 真实课与测试课统一对齐分钟边界；跨分钟时自然会重算剩余时间与进度
            now + IslandNotificationHelper.msUntilNextMinuteBoundary(inClassEndMillis - now)
        } else if (hasActiveCourse) {
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
        logD(context, "computeNext: active=$hasActiveCourse ret=$safe")
        return safe
    }

    // 这里曾按「关键 / 非关键时段」分流，非关键时段降级为 setAndAllowWhileIdle 以省电。
    // 已移除，勿再引入：刷新链是链式的（每次触发后才注册下一次），
    // 一旦掺入非精确闹钟，触发点漂移会逐次累积，Doze 深处延迟可达数小时，
    // 跨日重调度与课前补发会整个失效——用户反馈的「提醒不准时」正源于此。
    // 刷新链现在一律走 setExactAndAllowWhileIdle，见 scheduleNextWidgetRefresh。

    /**
     * 刷新链必须始终精确唤醒，不能用 setAndAllowWhileIdle 降级。
     * 它是链式调度：每次触发后才注册下一次。非精确闹钟会让触发点漂移，
     * 且漂移逐次累积——Doze 深处可能延迟数小时，跨日重调度与课前补发会整个失效。
     * 省下的那点电远不抵「提醒不准时」的代价。
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

    /**
     * 小组件/ContentProvider/今日页/次日提醒共用的某日课表解析。
     * 节假日空课；调休按 followWeekday/followWeek 映射；无映射时按日历周次查课。
     */
    data class DayScheduleResolution(
        val courses: List<Course>,
        /** 实际用于查课的星期（调休映射后） */
        val displayDayOfWeek: Int,
        /** 实际用于查课的周次 */
        val displayWeek: Int,
        /** 日历上的星期（未映射） */
        val calendarDayOfWeek: Int,
        /** 目标日是否为节假日 */
        val isHolidayDate: Boolean,
        /** 目标日是否配置了调休映射 */
        val isWorkSwap: Boolean,
    )

    /** 由开学日推目标日期所在日历课表周；仅作相对偏移，不直接当「当前周」 */
    fun calendarWeekForDate(repository: CourseRepository, date: LocalDate): Int =
        calendarWeekLongForDate(repository, date)
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            .toInt()

    private fun calendarWeekLongForDate(repository: CourseRepository, date: LocalDate): Long {
        return try {
            val start = LocalDate.parse(repository.getClassStartTime().replace("/", "-"))
            CourseScheduleDateBounds.calendarWeekForDate(start, date)
        } catch (_: Exception) {
            repository.getCurrentWeek().toLong()
        }
    }

    /**
     * 与主课表「当前周」对齐的目标日周次。
     * 存储周（Settings 可手动调）为基准，按目标日相对今天的日历周差平移；
     * 避免 widget/今日页/提醒用纯日历周，与主课表手动调周后的单双周错位。
     * 调休 followWeek 仍由调用方优先覆盖。
     */
    fun alignedStoredWeekForDate(repository: CourseRepository, date: LocalDate): Int {
        val today = LocalDate.now()
        val delta = calendarWeekLongForDate(repository, date) -
            calendarWeekLongForDate(repository, today)
        return (repository.getCurrentWeek().toLong() + delta)
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun resolveDaySchedule(context: Context, forTomorrow: Boolean): DayScheduleResolution {
        val today = LocalDate.now()
        return resolveDaySchedule(context, if (forTomorrow) today.plusDays(1) else today)
    }

    fun resolveDaySchedule(context: Context, date: LocalDate): DayScheduleResolution =
        resolveDaySchedule(context, date, CourseRepository(context))

    /** Same day resolution with a reusable repository for multi-date calculations. */
    fun resolveDaySchedule(
        context: Context,
        date: LocalDate,
        repository: CourseRepository,
    ): DayScheduleResolution = resolveDaySchedule(context, date, repository, null)

    /** Same day resolution with cached entries indexed by year for multi-date calculations. */
    fun resolveDaySchedule(
        context: Context,
        date: LocalDate,
        repository: CourseRepository,
        holidayEntriesByYear: Map<Int, List<HolidayManager.Entry>>?,
    ): DayScheduleResolution {
        val calendarDay = date.dayOfWeek.value
        val holidayEntries = HolidayManager.entriesForDate(
            holidayEntriesByYear ?: HolidayManager.loadAllByYear(context),
            date,
        )
        val isHolidayDate = holidayEntries.any { it.type == HolidayManager.TYPE_HOLIDAY }
        val targetEntry = holidayEntries.firstOrNull { it.type == HolidayManager.TYPE_WORKSWAP }
        val isWorkSwap = targetEntry?.followWeekday?.let { it in 1..7 } == true
        val displayDay = targetEntry?.followWeekday?.takeIf { it in 1..7 } ?: calendarDay
        // 未配置映射：用「存储当前周 + 日历偏移」，与主课表手动调周一致，且不受今日调休 followWeek 污染
        val displayWeek = targetEntry?.followWeek?.takeIf { it > 0 }
            ?: alignedStoredWeekForDate(repository, date)

        if (isHolidayDate) {
            return DayScheduleResolution(
                courses = emptyList(),
                displayDayOfWeek = displayDay,
                displayWeek = displayWeek,
                calendarDayOfWeek = calendarDay,
                isHolidayDate = true,
                isWorkSwap = isWorkSwap,
            )
        }

        val totalWeeks = repository.getTotalWeeks()
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        if (displayWeek < 1 || displayWeek > totalWeeks || displayWeek > lastWeekWithCourses) {
            return DayScheduleResolution(
                courses = emptyList(),
                displayDayOfWeek = displayDay,
                displayWeek = displayWeek,
                calendarDayOfWeek = calendarDay,
                isHolidayDate = false,
                isWorkSwap = isWorkSwap,
            )
        }

        val courses = repository.getAllCourses()
            .filter { it.dayOfWeek == displayDay && it.isActiveInWeek(displayWeek) }
            .sortedBy { getCourseStartTime(it, repository).toMinutes() }
        return DayScheduleResolution(
            courses = courses,
            displayDayOfWeek = displayDay,
            displayWeek = displayWeek,
            calendarDayOfWeek = calendarDay,
            isHolidayDate = false,
            isWorkSwap = isWorkSwap,
        )
    }

    /** 节假日/调休数据变更后：重排提醒并立即刷新已放置的小部件 */
    fun onHolidayDataChanged(context: Context) {
        startReminderService(context)
        WidgetUpdateCache.updateInstalledWidgets(context)
    }

    fun getTomorrowCourses(context: Context): List<Course> =
        resolveDaySchedule(context, forTomorrow = true).courses

    fun getTodayCourses(context: Context): List<Course> =
        resolveDaySchedule(context, forTomorrow = false).courses

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

    // 与超级岛同口径：剩余分钟向上取整；进度按已上课比例（0→100）
    private fun inClassRemainMinutes(endMillis: Long, now: Long): Int {
        val remainMs = endMillis - now
        if (remainMs <= 0L) return 0
        return ((remainMs + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
    }

    private fun inClassProgressPercent(startMillis: Long, endMillis: Long, remainMinutes: Int): Int {
        val totalMs = endMillis - startMillis
        if (totalMs <= 0L) return 0
        val remainMs = remainMinutes * 60_000L
        val elapsedMs = (totalMs - remainMs).coerceIn(0L, totalMs)
        return ((elapsedMs * 100L) / totalMs).toInt().coerceIn(0, 100)
    }

    /**
     * 原生实况课中进度：API 36+ 用 ProgressStyle + 提升 ongoing，与 SleepDown 同思路。
     * 每分钟只在剩余分钟/进度变化时重推，挂到下课自动结束。
     */
    @SuppressLint("NewApi")
    fun showOrUpdateInClassLiveNotification(
        context: Context,
        courseName: String,
        classroom: String,
        endTime: String,
        startMillis: Long,
        endMillis: Long,
        countdownNotificationId: Int,
        testMode: Boolean = false
    ) {
        ensureNotificationChannels(context)
        val now = System.currentTimeMillis()
        if (endMillis <= now) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val inClassId = liveInClassId(testMode)
        val startedId = liveStartedId(testMode)
        val countdownId = liveCountdownId(testMode)

        val prefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        val wasInClass = prefs.getBoolean("in_class_active", false)
        val remainMin = inClassRemainMinutes(endMillis, now)
        val progress = inClassProgressPercent(startMillis, endMillis, remainMin)
        val lastMin = prefs.getInt("last_in_class_minutes", -1)
        val lastProgress = prefs.getInt("last_in_class_progress", -1)

        // 首次切入课中：收起倒计时/已上课，避免三态并存
        if (!wasInClass) {
            manager.cancel(countdownId)
            manager.cancel(startedId)
            // 清掉另一套（真实/测试）残留，避免双通道并存
            if (testMode) {
                manager.cancel(LIVE_COUNTDOWN_ID)
                manager.cancel(LIVE_STARTED_ID)
            } else {
                manager.cancel(LIVE_TEST_COUNTDOWN_ID)
                manager.cancel(LIVE_TEST_STARTED_ID)
            }
        } else if (remainMin == lastMin && progress == lastProgress) {
            // 同分钟无变化，不重推，避免岛/胶囊闪烁
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context, courseName.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endTimeLabel = if (endTime.isNotEmpty()) "${endTime}下课" else "下课"
        val infoLine = "$endTimeLabel · 还剩${remainMin}分钟"
        val expandedText = if (classroom.isBlank()) infoLine else "$infoLine\n$classroom"
        val shortCriticalText = "${remainMin}分钟"

        // 课中只保留进度卡片，不挂动作按钮（点整卡打开课表即可）
        val notification = if (Build.VERSION.SDK_INT >= 36) {
            val builder = Notification.Builder(context, CHANNEL_LIVE_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("$courseName | 上课中")
                .setContentText(infoLine)
                .setStyle(Notification.BigTextStyle().bigText(expandedText))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setTimeoutAfter(endMillis - now)
            builder.setStyle(
                Notification.ProgressStyle()
                    .setProgressTrackerIcon(
                        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_live_dot)
                    )
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))
                    .setProgress(progress)
            )
            runCatching { builder.setRequestPromotedOngoing(true) }
            runCatching { builder.setShortCriticalText(shortCriticalText) }
            builder.build().apply {
                flags = flags or Notification.FLAG_ONLY_ALERT_ONCE
            }
        } else {
            // 低版本无 ProgressStyle：用普通进度条 + 提升请求降级
            NotificationCompat.Builder(context, CHANNEL_LIVE_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("$courseName | 上课中")
                .setContentText(expandedText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setTimeoutAfter(endMillis - now)
                .setShortCriticalText(shortCriticalText)
                .setRequestPromotedOngoing(true)
                .build()
                .apply {
                    flags = flags or Notification.FLAG_ONLY_ALERT_ONCE
                }
        }

        manager.notify(inClassId, notification)
        prefs.edit {
            putBoolean("in_class_active", true)
                .putInt("in_class_notification_id", inClassId)
                .putInt("last_in_class_minutes", remainMin)
                .putInt("last_in_class_progress", progress)
        }
    }

    private fun cancelInClassLiveNotification(
        context: Context,
        testMode: Boolean = false
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(liveInClassId(testMode))
        val prefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("in_class_active", false)
                .remove("last_in_class_minutes")
                .remove("last_in_class_progress")
        }
    }

    /** 15 秒「已上课」实况；独立 ID，与倒计时/课中错开。 */
    private fun showStartedLiveNotification(
        context: Context,
        courseName: String,
        classroom: String,
        startTime: String,
        testMode: Boolean = false
    ) {
        ensureNotificationChannels(context)
        val startedIntent = PendingIntent.getActivity(
            context, courseName.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dndIntent = Intent(context, ClassDndReceiver::class.java).apply {
            // 测试课已并入 isInClass，与真实课共用同一条按钮链路
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
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val startedNotificationId = liveStartedId(testMode)
        context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
            .edit { putInt("started_notification_id", startedNotificationId) }
        manager.notify(startedNotificationId, startedNotification)
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
            // 绕过勿扰：本频道承载倒计时/已上课/课中进度，若不放行，
            // 开「上课勿扰」后用户特意打开的课中提醒就永远看不见了。
            // 注意：仅对 PRIORITY 档生效；完全勿扰（INTERRUPTION_FILTER_NONE）
            // 属系统级全静音，bypassDnd 无法放行。
            setBypassDnd(true)
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
        endMillis: Long,
        testMode: Boolean = false
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
            // 测试课已并入 isInClass，与真实课共用同一条按钮链路
            action = ClassDndReceiver.ACTION_TOGGLE
        }
        val dndPendingIntent = PendingIntent.getBroadcast(
            context,
            courseName.hashCode() + 100,
            dndIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = liveCountdownId(testMode)
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
        // 互斥：发真实课时清测试，发测试时清真实，避免双通道并存
        if (testMode) {
            manager.cancel(LIVE_COUNTDOWN_ID)
            manager.cancel(LIVE_STARTED_ID)
            manager.cancel(LIVE_IN_CLASS_ID)
        } else {
            manager.cancel(LIVE_TEST_COUNTDOWN_ID)
            manager.cancel(LIVE_TEST_STARTED_ID)
            manager.cancel(LIVE_TEST_IN_CLASS_ID)
        }
        manager.notify(notificationId, notification)

        // 倒计时状态供 WidgetRefreshReceiver 每分钟更新
        val prefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("active", true)
                .putBoolean("test_mode", testMode)
                .putString("courseName", courseName)
                .putString("classroom", classroom)
                .putString("section", section)
                .putString("startTime", startTime)
                .putLong("startMillis", startMillis)
                .putLong("endMillis", endMillis)
                .putInt("notificationId", notificationId)
                .putBoolean("in_class_active", false)
                .remove("last_displayed_minutes")
                .remove("last_in_class_minutes")
                .remove("last_in_class_progress")
                .remove("started_shown")
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
            setCourseBoundaryAlarm(alarmManager, triggerAt, pendingIntent)
        }

        // 启动 widget 刷新链，确保倒计时每分钟更新
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleNextWidgetRefresh(context, alarmManager)
    }

    /**
     * 测试实时活动：与超级岛测试同一套固定课程，不依赖真实课表。
     * 课前 70 秒倒计时 + 课中 120 秒，便于验证倒计时 / 已上课 / 课中进度全链路。
     */
    fun sendTestLiveNotification(context: Context) {
        val courseName = "大学英语Ⅱ"
        val classroom = "博A201"
        val section = "第3~4节"
        val startMillis = System.currentTimeMillis() + 70_000L
        val endMillis = startMillis + 120_000L
        val startTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(startMillis))
        showPreClassCountdownNotification(
            context = context,
            courseName = courseName,
            classroom = classroom,
            section = section,
            startTime = startTime,
            startMillis = startMillis,
            endMillis = endMillis,
            testMode = true
        )
        // 测试课也要走「上课自动开启、下课自动关闭」，否则勿扰链路在测试里跑不到
        ClassDndHelper.syncTestClassDndAlarms(context)
    }

    // 每分钟由 WidgetRefreshReceiver 驱动；同 notifyId 重复 notify 无痕更新
    fun updateActiveCountdown(context: Context) {
        val prefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return

        // 岛模式下不显示实况通知：收起残留实况，改由岛状态对账接管
        val repository = CourseRepository(context)
        if (repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)) {
            if (prefs.getBoolean("active", false)) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(LIVE_COUNTDOWN_ID)
                manager.cancel(LIVE_STARTED_ID)
                manager.cancel(LIVE_IN_CLASS_ID)
                manager.cancel(LIVE_TEST_COUNTDOWN_ID)
                manager.cancel(LIVE_TEST_STARTED_ID)
                manager.cancel(LIVE_TEST_IN_CLASS_ID)
                prefs.edit {
                    putBoolean("active", false)
                        .putBoolean("in_class_active", false)
                }
            }
            reconcileIslandCountdown(context)
            return
        }

        val testMode = prefs.getBoolean("test_mode", false)
        val startMillis = prefs.getLong("startMillis", 0L)
        val endMillis = prefs.getLong("endMillis", 0L)
        val notificationId = liveCountdownId(testMode)
        val courseName = prefs.getString("courseName", "") ?: ""
        val classroom = prefs.getString("classroom", "") ?: ""
        val startTime = prefs.getString("startTime", "") ?: ""

        val now = System.currentTimeMillis()

        // 课程结束：取消通知并清状态
        if (endMillis in 1..now) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(liveCountdownId(testMode))
            manager.cancel(liveStartedId(testMode))
            cancelInClassLiveNotification(context, testMode)
            IslandNotificationHelper.cancelIslandState(context, ISLAND_NOTIFICATION_ID)
            prefs.edit { putBoolean("active", false) }
            return
        }

        // 到点：优先课中进度，否则发 15 秒「已上课」
        if (now >= startMillis) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val inClassOn = isInClassLiveEnabled(context) && endMillis > now
            val inClassNow = inClassOn && shouldShowInClassNow(context, startMillis, endMillis, now)

            if (inClassNow) {
                // 课中：保持 countdown_state active，挂到下课由每分钟刷新驱动
                if (!prefs.getBoolean("in_class_active", false)) {
                    manager.cancel(liveCountdownId(testMode))
                    IslandNotificationHelper.cancelIslandState(context, ISLAND_NOTIFICATION_ID)
                }
                val endTimeText = if (endMillis > 0L) {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(endMillis))
                } else {
                    ""
                }
                showOrUpdateInClassLiveNotification(
                    context = context,
                    courseName = courseName,
                    classroom = classroom,
                    endTime = endTimeText,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    countdownNotificationId = liveCountdownId(testMode),
                    testMode = testMode
                )
                return
            }

            if (inClassOn && !inClassNow) {
                // 课中已开但「距下课」未进窗：不发「已上课」，保持 active 等进窗再切课中。
                // 但上课时刻已过，倒计时卡失去意义，必须收起，否则会一直停在 00:00。
                manager.cancel(liveCountdownId(testMode))
                IslandNotificationHelper.cancelIslandState(context, ISLAND_NOTIFICATION_ID)
                return
            }

            // 课中未开：到点闪一次「已上课」
            manager.cancel(liveCountdownId(testMode))
            IslandNotificationHelper.cancelIslandState(context, ISLAND_NOTIFICATION_ID)
            cancelInClassLiveNotification(context, testMode)
            prefs.edit { putBoolean("active", false) }
            showStartedLiveNotification(
                context = context,
                courseName = courseName,
                classroom = classroom,
                startTime = startTime,
                testMode = testMode
            )
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
            // 测试课已并入 isInClass，与真实课共用同一条按钮链路
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
        setCourseBoundaryAlarm(alarmManager, courseStartMillis, expandPending)
        writeRcSet(context, KEY_EXPAND_RCS, readRcSet(context, KEY_EXPAND_RCS) + rc)
    }

    // 每分钟对账：兜底切换闹钟丢失/Doze 延迟、岛残留、开关关闭后的清理
    // 同时扫真实岛与测试岛，否则「测试小米超级岛」课中进度永远不更新
    fun reconcileIslandCountdown(context: Context) {
        val repository = CourseRepository(context)
        val islandEnabled = repository.getIslandNotification() &&
            IslandNotificationHelper.isIslandSupported(context)

        if (!islandEnabled) {
            if (IslandNotificationHelper.IslandState.isActiveAny(context)) {
                IslandNotificationHelper.cancelIslandNotifications(context)
                IslandNotificationHelper.IslandState.clear(context)
                IslandNotificationHelper.IslandState.clear(context, testMode = true)
                android.util.Log.d(TAG, "reconcileIsland: island disabled, dismissed")
            }
            return
        }

        reconcileIslandState(context, testMode = false)
        reconcileIslandState(context, testMode = true)
    }

    private fun reconcileIslandState(context: Context, testMode: Boolean) {
        val state = IslandNotificationHelper.IslandState.snapshot(context, testMode) ?: return
        val now = System.currentTimeMillis()

        val inClassEnabled = IslandNotificationHelper.isInClassReminderEnabled(context)
        // 课中开启：不按 15 秒「已上课」收起；对账只负责进课中/刷进度/下课收起
        val keepForInClass = inClassEnabled && state.endMillis > now
        val shouldShowInClass = keepForInClass &&
            shouldShowInClassNow(context, state.startMillis, state.endMillis, now)

        val expiredByEnd = state.endMillis > 0 && now >= state.endMillis
        val expiredByShow = !inClassEnabled && state.switched &&
            now - state.switchedAt >= IslandNotificationHelper.ISLAND_STARTED_VISIBLE_MS
        if (expiredByEnd || expiredByShow) {
            // 连带收起另一半（倒计时/已上课），避免残留岛一直停在 00:00
            IslandNotificationHelper.cancelIslandState(context, state.notificationId)
            IslandNotificationHelper.IslandState.clear(context, testMode)
            android.util.Log.d(TAG, "reconcileIsland: dismissed test=$testMode end=$expiredByEnd show=$expiredByShow")
            return
        }

        // 到点但还没切换（闹钟丢失/延迟）→ 立即补切，最多滞后一个刷新周期
        if (!state.switched && now >= state.startMillis) {
            if (now - state.startMillis > ISLAND_LATE_TOLERANCE_MS) {
                // 隔夜或重启后残留的状态：不要再补一个过期的"已上课"，直接收起
                IslandNotificationHelper.cancelIslandState(context, state.notificationId)
                IslandNotificationHelper.IslandState.clear(context, testMode)
                android.util.Log.d(TAG, "reconcileIsland: stale state dismissed for ${state.courseName}")
                return
            }
            android.util.Log.d(TAG, "reconcileIsland: late switch for ${state.courseName}")
            IslandNotificationHelper.onClassStart(
                context = context,
                courseName = state.courseName,
                classroom = state.classroom,
                section = state.section,
                startTime = state.startTime,
                endTime = state.endTime.ifEmpty { null },
                notificationId = state.notificationId,
                testMode = testMode
            )
            return
        }

        // 课中提醒：仅 shouldShowInClassNow 为真（全程，或距下课已进窗）才切课中卡
        // 已在课中则无需重推（系统 timer 自刷距下课）；课中开启时对账不补「已上课」
        if (inClassEnabled && now >= state.startMillis) {
            val alreadyInClass = IslandNotificationHelper.isInClassNotificationId(state.notificationId)
            val canShow = state.endMillis > now
            if (canShow) {
                if (alreadyInClass) {
                    IslandNotificationHelper.updateInClassIslandIfNeeded(context, state, testMode)
                } else if (shouldShowInClass) {
                    // 仅进窗后切课中；不可 || end>start，否则「距下课 N 分钟」被短路、上课即挂卡
                    IslandNotificationHelper.sendInClassIslandNotification(
                        context = context,
                        courseName = state.courseName,
                        classroom = state.classroom,
                        section = state.section,
                        startTime = state.startTime,
                        endTime = state.endTime,
                        notificationId = state.notificationId,
                        testMode = testMode
                    )
                }
            }
            return
        }

        if (shouldShowInClass) {
            // 理论上 inClassEnabled 已在上面 return；此处兜底
            IslandNotificationHelper.sendInClassIslandNotification(
                context = context,
                courseName = state.courseName,
                classroom = state.classroom,
                section = state.section,
                startTime = state.startTime,
                endTime = state.endTime,
                notificationId = state.notificationId,
                testMode = testMode
            )
        }
    }

    // 关闭提醒时清理实况/岛残留
    fun cancelAllReminderNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val countdownPrefs = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        manager.cancel(LIVE_COUNTDOWN_ID)
        manager.cancel(LIVE_STARTED_ID)
        manager.cancel(LIVE_IN_CLASS_ID)
        manager.cancel(LIVE_TEST_COUNTDOWN_ID)
        manager.cancel(LIVE_TEST_STARTED_ID)
        manager.cancel(LIVE_TEST_IN_CLASS_ID)
        // 旧版按课程名 hash 的残留 ID
        val legacyId = countdownPrefs.getInt("notificationId", 0)
        if (legacyId != 0 && legacyId != LIVE_COUNTDOWN_ID && legacyId != LIVE_TEST_COUNTDOWN_ID) {
            manager.cancel(legacyId)
            manager.cancel(legacyId + 1)
            manager.cancel(legacyId + 2)
        }
        countdownPrefs.edit {
            putBoolean("active", false)
                .putBoolean("in_class_active", false)
                .remove("test_mode")
                .remove("last_in_class_minutes")
                .remove("last_in_class_progress")
        }
        IslandNotificationHelper.cancelIslandNotifications(context)
        IslandNotificationHelper.IslandState.clear(context)
    }

    // 每分钟兜底补发：窗口内且未发送的课立即补发
    fun checkPendingPreClassReminders(context: Context) {
        val repository = CourseRepository(context)
        if (!repository.getPreClassReminder()) {
            return
        }
        if (!isSemesterStarted(repository)) {
            return
        }

        // 复用 getTodayCourses，勿再内联过滤逻辑
        val courses = getTodayCourses(context)
        if (courses.isEmpty()) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val minutesBefore = repository.getPreClassReminderMinutes()
        val useIsland = repository.getIslandNotification() && IslandNotificationHelper.isIslandSupported(context)
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

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
            if (!inWindow || dedupHit || islandHit) continue
            val startMillis = parseTimeToTodayMillis(startTime)
            val endMillis = parseTimeToTodayMillis(getCourseEndTime(course, repository))
            sendPreClassNotification(
                context, alarmManager, repository, course, startTime,
                useIsland, startMillis, endMillis
            )
            logD(context, "checkPending: SENT ${course.name}")
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
