@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.chedule.data

import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.synchronizedOn
import com.haooz.chedule.data.platform.ReminderController
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.util.CourseTimeSpan
import com.haooz.chedule.data.model.CourseTimeResolver
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.i18n.L
import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/**
 * Course reminder scheduling for iOS: pre-class notices and tomorrow's summary.
 *
 * Split of duties — Swift (`NativeCourseNotifier`) owns anything touching
 * `UserNotifications` or `ActivityKit`, because Kotlin/Native's ObjC bindings do not
 * export the `UNCalendarNotificationTrigger` factory methods, and ActivityKit is a
 * pure-Swift framework cinterop cannot reach. This side only does platform-neutral
 * work: read settings, resolve target days, cap the pending list. Same injection
 * pattern as `IosToast` / `IosNativeCapture`.
 *
 * Gaps vs Android (see docs/KMP_IOS_MIGRATION_PLAN.md §7):
 *  - no `setAlarmClock` equivalent, so pre-class notices may fire late
 *  - the in-class island can only start on a notification tap or in the foreground
 *  - DND control is forbidden to third parties
 *  - 64 pending notices is a hard cap, hence the rolling window
 *
 * Schedule resolution is NOT here: ask [DayScheduleResolver], shared with Android's
 * reminder code, so both platforms cannot drift apart.
 */
object IosCourseNotifier {

    /** Rolling window for individual course notices and daily summaries. */
    const val WINDOW_DAYS = 14

    /**
     * Pending ceiling. iOS allows 64; headroom is left for the summary and test
     * notices. Past the cap `add` fails and new notices are dropped silently, so the
     * trim has to happen here rather than in Swift.
     */
    const val MAX_PENDING = 56

    // Notice identifiers; Swift cancels by prefix, so do not change the format.
    const val ID_PREFIX_PRE_CLASS = "nexio.pre_class"
    const val ID_PREFIX_NEXT_DAY = "nexio.next_day"

    // userInfo keys, read back by the host on tap.
    const val KEY_ACTION = "nexio.action"
    const val KEY_COURSE_ID = "nexio.course_id"
    const val KEY_COURSE_NAME = "nexio.course_name"
    const val KEY_CLASSROOM = "nexio.classroom"
    const val KEY_SECTION = "nexio.section"
    const val KEY_START_TIME = "nexio.start_time"
    const val KEY_END_TIME = "nexio.end_time"
    const val KEY_START_EPOCH = "nexio.start_epoch"
    const val KEY_END_EPOCH = "nexio.end_epoch"
    const val KEY_FIRE_EPOCH = "nexio.fire_epoch"

    /** Tap a pre-class notice -> start the Live Activity, when the island switch is on. */
    const val ACTION_OPEN_COURSE_LIVE = "open_course_live"

    /** Tap tomorrow's summary -> open the app. */
    const val ACTION_OPEN_APP = "open_app"

    /** Shared with Android's `CourseReminderHelper`. */
    private const val PREF_IN_CLASS = "course_reminder_prefs"
    private const val DEFAULT_LEAD_MINUTES = 10

    @Volatile
    private var authorized = false

    /**
     * Injection points. Plain non-null fields plus [installed] rather than nullable
     * function types: Kotlin/Native mis-bridges `var f: ((Boolean) -> Unit)?`, parsing
     * `f.invoke(cb)` as passing a Boolean argument. Reproduced with nullable,
     * non-null and local-val variants alike.
     */
    @Volatile
    private var installed = false

    private var nativeWrite: (List<Notice>) -> Unit = {}
    private var nativeCancelAll: () -> Unit = {}
    private var nativeSendTestNotification: () -> Unit = {}

    /** Parameterless by design; the result comes back via [requestAuthorizationFromSwift]. */
    private var nativeRequestAuthorization: () -> Unit = {}

    /** 不弹窗的权限查询，冷启动从 [install] 打一发，给 [authorized] 播种系统真实状态。 */
    private var nativeRefreshAuthorization: () -> Unit = {}

    /**
     * 等待异步授权回包的队列。提前回 `false` 会丢掉调用方待执行动作（开关不翻转、reschedule 无通知可写）；
     * [requestAuthorizationFromSwift] 以真实结果冲刷，授权→排程无需二次触发。
     */
    private val pendingAuthorization = mutableListOf<(Boolean) -> Unit>()

    /** 双保险；真正的约束是主线程收口，见 [ensureAuthorization]。 */
    private val authLock = Any()

    private var authorizationRequestInFlight = false

    /**
     * One pending notice. Mirrors the Swift side field for field.
     *
     * @param userInfo carried on the notification, handed back to the host on tap.
     */
    data class Notice(
        val id: String,
        val fireEpochMillis: Long,
        val title: String,
        val body: String,
        val userInfo: Map<String, String>,
    )

    fun install(
        write: (List<Notice>) -> Unit,
        cancelAll: () -> Unit,
        requestAuthorization: () -> Unit,
        refreshAuthorization: () -> Unit = {},
        sendTestNotification: () -> Unit = {},
    ) {
        nativeWrite = write
        nativeCancelAll = cancelAll
        nativeRequestAuthorization = requestAuthorization
        nativeRefreshAuthorization = refreshAuthorization
        nativeSendTestNotification = sendTestNotification
        installed = true
        // Seed `authorized` from the system state without ever showing the prompt.
        nativeRefreshAuthorization()
    }

    fun sendTestNotification() {
        if (installed) nativeSendTestNotification()
    }

    /** Swift 的授权回包（冷启动播种 + 真实结果），冲刷 [pendingAuthorization]。 */
    fun requestAuthorizationFromSwift(granted: Boolean) {
        // Swift 的 reportAuthorization 已 hop 主线程；这里再兜一层底，
        // 保证回调（进 Compose state / toast）一定在主线程执行
        onMain {
            authorized = granted
            val waiting = synchronizedOn(authLock) {
                authorizationRequestInFlight = false
                val copy = pendingAuthorization.toList()
                pendingAuthorization.clear()
                copy
            }
            waiting.forEach { it(granted) }
        }
    }

    /** [block] 保证在主线程执行；已是主线程则同步跑，避免无谓的下一帧延迟。 */
    private fun onMain(block: () -> Unit) {
        if (NSThread.currentThread.isMainThread) block()
        else dispatch_async(dispatch_get_main_queue(), block)
    }

    // MARK: Rescheduling

    /** Recheck system permission and refill the rolling window on launch/resume. */
    fun reconcile() {
        onMain {
            authorized = false
            reschedule()
        }
    }

    /**
     * Reconcile every notice against current settings; equivalent to Android's
     * `startReminderService`. Cancel-then-write, same order as Android's
     * cancelAllAlarms + scheduleAllAlarms, so deleted or renamed courses cannot leave
     * orphans behind.
     */
    fun reschedule(repository: CourseRepository? = null) {
        IosWidgetSnapshot.refresh()
        val repo = repository ?: CourseRepository()
        val preClass = repo.getPreClassReminder()
        val nextDay = repo.getNextDayReminder()
        if (!preClass && !nextDay) {
            cancelAll()
            return
        }
        ensureAuthorization { granted ->
            // Read current switches after the asynchronous permission response: the
            // user may have disabled reminders while the prompt was open.
            if (granted) {
                // ⚠ writeAll 是「逐日 DayScheduleResolver + 排序」纯计算，实测占主线程 ~250ms
                //（sample 最大单项）；它挂在切课表的 reloadCourses() 上，压主线程会卡死那一帧。
                // 无需主线程：仅最后 nativeWrite 碰 UNUserNotificationCenter（增删线程安全）。
                val work = {
                    writeAll(repo, repo.getPreClassReminder(), repo.getNextDayReminder())
                }
                if (NSThread.currentThread.isMainThread) {
                    dispatch_async(dispatch_get_global_queue(0L, 0u)) { work() }
                } else {
                    work()
                }
            }
        }
    }

    fun stop() {
        IosWidgetSnapshot.refresh()
        cancelAll()
    }

    private fun writeAll(repo: CourseRepository, preClass: Boolean, nextDay: Boolean) {
        val pending = mutableListOf<Notice>()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val entriesByYear = HolidayManager.loadAllByYear()

        if (preClass) {
            for (offset in 0 until WINDOW_DAYS) {
                val date = today.plus(DatePeriod(days = offset))
                // 年份缺失只代表「当天无节假日豁免」，不能跳过整天（否则从未拉取过
                // 节假日数据的安装会丢掉全部课前提醒）
                val resolution = DayScheduleResolver.resolveDaySchedule(
                    date = date,
                    repository = repo,
                    holidayEntriesByYear = entriesByYear,
                )
                pending += preClassNotices(date, resolution, repo)
            }
        }

        if (nextDay) {
            for (offset in 0 until WINDOW_DAYS) {
                val reminderDate = today.plus(DatePeriod(days = offset))
                nextDayNotice(reminderDate, repo, entriesByYear)?.let { pending += it }
            }
        }

        // Keep the nearest ones when over the cap: day 14 can wait for the next pass,
        // unlike the next bell.
        val limited = pending.sortedBy { it.fireEpochMillis }.take(MAX_PENDING)
        NexioLog.i(TAG, "reschedule: ${limited.size} notices (of ${pending.size})")
        if (installed) nativeWrite(limited)
    }

    /** One notice per course, at start minus the configured lead. */
    private fun preClassNotices(
        date: LocalDate,
        resolution: DayScheduleResolver.DayScheduleResolution,
        repo: CourseRepository,
    ): List<Notice> {
        val leadMinutes = repo.getPreClassReminderMinutes()
        val rangeStart = repo.getPreClassReminderStartSection()
        val rangeEnd = repo.getPreClassReminderEndSection()
        val rangeLimited = rangeStart > 1 || rangeEnd > 0
        val now = Clock.System.now().toEpochMilliseconds()
        val out = mutableListOf<Notice>()
        resolution.courses.forEachIndexed { index, course ->
            // 与 Android 一致：只提醒落在用户设定节次范围内的课
            if (rangeLimited && !course.hasValidCustomTime()) {
                if (course.startSection < rangeStart) return@forEachIndexed
                if (rangeEnd > 0 && course.startSection > rangeEnd) return@forEachIndexed
            }
            val start = CourseTimeResolver.getStartTime(course, repo) ?: return@forEachIndexed
            val startTime = parseClock(start) ?: return@forEachIndexed
            val endTime = CourseTimeResolver.getEndTime(course, repo)?.let { parseClock(it) }
            val startEpoch = date.atTime(startTime).toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            val fireAt = startEpoch - leadMinutes * 60_000L
            // A local notification cannot fire in the past; it would only be swept up
            // by the next reconciliation.
            if (fireAt <= now) return@forEachIndexed
            val endText = endTime?.let { formatClock(it) } ?: ""
            val endEpoch = if (endTime != null) {
                val startMin = startTime.hour * 60 + startTime.minute
                val endMin = endTime.hour * 60 + endTime.minute
                if (CourseTimeSpan.crossesMidnight(startMin, endMin)) {
                    date.plus(DatePeriod(days = 1)).atTime(endTime)
                        .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                } else {
                    date.atTime(endTime)
                        .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                }
            } else {
                startEpoch + 45 * 60_000L
            }
            out += Notice(
                id = "$ID_PREFIX_PRE_CLASS.$date.$index",
                fireEpochMillis = fireAt,
                title = if (leadMinutes > 0) {
                    L("%s · %d分钟后上课", "%s · Class in %d min")(course.name, leadMinutes)
                } else {
                    L("%s · 即将上课", "%s · Starting soon")(course.name)
                },
                body = buildString {
                    val separator = L("｜", " · ")()
                    val section = sectionText(course)
                    if (section.isNotEmpty()) append(section)
                    if (course.classroom.isNotEmpty()) {
                        if (isNotEmpty()) append(separator)
                        append(course.classroom)
                    }
                    if (course.teacher.isNotEmpty()) {
                        if (isNotEmpty()) append(separator)
                        append(course.teacher)
                    }
                    if (endText.isNotEmpty()) {
                        if (isNotEmpty()) append(separator)
                        append("$start-$endText")
                    }
                },
                userInfo = mapOf(
                    KEY_ACTION to ACTION_OPEN_COURSE_LIVE,
                    KEY_COURSE_ID to course.id,
                    KEY_COURSE_NAME to course.name,
                    KEY_CLASSROOM to course.classroom,
                    KEY_SECTION to sectionText(course),
                    KEY_START_TIME to start,
                    KEY_END_TIME to endText,
                    KEY_START_EPOCH to startEpoch.toString(),
                    KEY_END_EPOCH to endEpoch.toString(),
                    KEY_FIRE_EPOCH to fireAt.toString(),
                ),
            )
        }
        return out
    }

    /** Tomorrow's courses in one notice at the configured time. */
    private fun nextDayNotice(
        today: LocalDate,
        repo: CourseRepository,
        entriesByYear: Map<Int, List<HolidayEntry>>,
    ): Notice? {
        val tomorrow = today.plus(DatePeriod(days = 1))
        // 同 writeAll：年份缺失只是「无豁免」，不能取消这条提醒
        val resolution = DayScheduleResolver.resolveDaySchedule(
            date = tomorrow,
            repository = repo,
            holidayEntriesByYear = entriesByYear,
        )
        if (resolution.courses.isEmpty()) return null

        val fireAt = today
            .atTime(LocalTime(repo.getNextDayReminderHour(), repo.getNextDayReminderMinute()))
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        if (fireAt <= Clock.System.now().toEpochMilliseconds()) return null

        val lines = resolution.courses.mapNotNull { course ->
            val start = CourseTimeResolver.getStartTime(course, repo) ?: return@mapNotNull null
            if (course.classroom.isBlank()) {
                "${course.name} $start"
            } else {
                "${course.name} $start · ${course.classroom}"
            }
        }
        return Notice(
            id = "$ID_PREFIX_NEXT_DAY.$tomorrow",
            fireEpochMillis = fireAt,
            title = L("明日课程（%d 节）", "Tomorrow's Classes (%d)")(resolution.courses.size),
            body = lines.joinToString("\n"),
            userInfo = mapOf(KEY_ACTION to ACTION_OPEN_APP),
        )
    }

    // MARK: Authorization

    private fun cancelAll() {
        if (installed) nativeCancelAll()
    }

    /**
     * 请求通知权限：已有授权（或宿主未接入）同步回包，否则等 Swift 的回包再冲刷。
     * 回调一律在主线程执行，可直接碰 UI 状态。
     */
    fun ensureAuthorization(onResult: (Boolean) -> Unit) {
        // 主线程收口：Swift 的回包在主线程清空 pendingAuthorization，而 reschedule()
        // 可能从 IO 线程进来（WebDAV / 本地全量恢复的 onHolidayDataChanged 就在
        // ioDispatcher 上调用），不收口就是普通 ArrayList 的并发读写
        if (!NSThread.currentThread.isMainThread) {
            dispatch_async(dispatch_get_main_queue()) { ensureAuthorization(onResult) }
            return
        }
        if (authorized) {
            onResult(true)
            return
        }
        if (!installed) {
            onResult(false)
            return
        }
        // 排队而不是立刻回 false：提前回会让开关动作被丢、reschedule() 无通知可写
        // 一次 native 往返够一批：弹窗期间的连点只入队
        val startRequest = synchronizedOn(authLock) {
            pendingAuthorization += onResult
            if (authorizationRequestInFlight) {
                false
            } else {
                authorizationRequestInFlight = true
                true
            }
        }
        if (startRequest) nativeRequestAuthorization()
    }

    fun isAuthorized(): Boolean = authorized

    // MARK: In-class switch

    /**
     * Whether the Live Activity island is enabled, i.e. iOS's "in-class reminder".
     * Shares Android's `in_class_reminder_enabled` key so the switch means the same
     * thing on both platforms, even though the mechanism differs.
     */
    fun isLiveActivityEnabled(): Boolean =
        AppStorage.store(PREF_IN_CLASS).getBoolean(ReminderController.KEY_IN_CLASS, false)

    fun setLiveActivityEnabled(enabled: Boolean) {
        AppStorage.store(PREF_IN_CLASS).edit {
            putBoolean(ReminderController.KEY_IN_CLASS, enabled)
        }
        if (!enabled) IosCourseLiveActivity.endCurrent()
    }

    fun getInClassTimingMode(): Int = AppStorage.store(PREF_IN_CLASS)
        .getInt(ReminderController.KEY_IN_CLASS_TIMING_MODE, ReminderController.IN_CLASS_TIMING_FULL)

    fun getInClassLeadMinutes(): Int = AppStorage.store(PREF_IN_CLASS)
        .getInt(ReminderController.KEY_IN_CLASS_LEAD_MINUTES, DEFAULT_LEAD_MINUTES)

    // MARK: Helpers

    private fun parseClock(value: String): LocalTime? {
        val parts = value.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime(hour, minute)
    }

    private fun formatClock(time: LocalTime): String =
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

    private fun sectionText(course: Course): String =
        if (course.startSection == course.endSection) {
            L("第%d节", "Period %d")(course.startSection)
        } else {
            L("第%d-%d节", "Periods %d-%d")(course.startSection, course.endSection)
        }

    private const val TAG = "IosCourseNotifier"
}

/**
 * Injection point for driving the Live Activity.
 *
 * The implementation has to be Swift (ActivityKit is pure-Swift; cinterop cannot reach it).
 * While null, every call is dropped rather than throwing — the host is not ready yet.
 */
object IosCourseLiveActivity {

    /**
     * Starts or updates the island.
     *
     * @param endEpochMillis the bell time; a day later than [startEpochMillis] when
     *   the course crosses midnight.
     */
    var startOrUpdate: ((
        courseId: String,
        courseName: String,
        classroom: String,
        section: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        leftMode: Int,
        rightMode: Int,
        inClassRightMode: Int,
    ) -> Unit)? = null

    var end: (() -> Unit)? = null

    fun install(
        startOrUpdate: (
            String, String, String, String, Long, Long, Int, Int, Int,
        ) -> Unit,
        end: () -> Unit,
    ) {
        this.startOrUpdate = startOrUpdate
        this.end = end
    }

    fun getLeftMode(): Int =
        AppStorage.store("course_reminder_prefs").getInt("island_left_mode", 0)

    fun getRightMode(): Int =
        AppStorage.store("course_reminder_prefs").getInt("island_right_mode", 2)

    fun getInClassRightMode(): Int =
        AppStorage.store("course_reminder_prefs").getInt("island_in_class_right_mode", 1)

    /** Entry point used on a notification tap or when the app notices class started. */
    fun startFromCourse(
        courseId: String,
        courseName: String,
        classroom: String,
        section: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
    ) {
        startFromCourse(
            courseId,
            courseName,
            classroom,
            section,
            startEpochMillis,
            endEpochMillis,
            getLeftMode(),
            getRightMode(),
            getInClassRightMode(),
        )
    }

    fun startFromCourse(
        courseId: String,
        courseName: String,
        classroom: String,
        section: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        leftMode: Int,
        rightMode: Int,
        inClassRightMode: Int,
    ) {
        startOrUpdate?.invoke(
            courseId,
            courseName,
            classroom,
            section,
            startEpochMillis,
            endEpochMillis,
            leftMode,
            rightMode,
            inClassRightMode,
        )
    }

    fun endCurrent() {
        end?.invoke()
    }

    /** Triggers a 30-second test Live Activity to verify countdown and auto-dismissal. */
    fun startTest() {
        val now = Clock.System.now().toEpochMilliseconds()
        startFromCourse(
            courseId = "test_live_course",
            courseName = L("大学英语Ⅱ", "College English II")(),
            classroom = L("博A201", "Bo A201")(),
            section = L("第3-4节", "Periods 3-4")(),
            startEpochMillis = now + 30_000L,
            endEpochMillis = now + 30_000L,
        )
    }
}
