package com.haooz.nexio.ohos

import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.ClassroomLabel
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.CourseTimeResolver
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.i18n.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.coroutines.resume

/**
 * 鸿蒙端的课程提醒排程（对应安卓的 `CourseReminderHelper` / iOS 的 `IosCourseNotifier`）。
 *
 * 职责切分与 iOS 一致：**这里只做平台无关的计算**（读开关、按日解析课表、限流排序），
 * 真正发通知的动作交给 ArkTS（见 `ReminderHost.ets`）。
 *
 * | 能力 | 安卓 | 鸿蒙 |
 * |---|---|---|
 * | 课前 / 次日提醒 | AlarmManager 精确闹钟 | 普通通知（ArkTS 进程内定时，到点 publish） |
 * | 课中实况 | 常驻通知 + 超级岛 | 实况窗未接（等 AGC 权益） |
 * | 免打扰 / 电池优化 / Shizuku | 有系统 API | 无对应概念，页面已隐藏这些行 |
 *
 * 注意：普通通知只能在本进程存活时触发；应用被系统回收后定时器消失，
 * 下次打开应用会重新对账。要在进程被杀死后仍准时提醒，需要「代理提醒」
 * (`reminderAgentManager`)，它依赖 AGC 申请的 `PUBLISH_AGENT_REMINDER` 受限权限，
 * 协议不变，届时只改 `ReminderHost.ets` 的发布实现。
 *
 * 每次对账都是「先全撤，再全发」（与安卓/iOS 同序），删课/改名不会留下孤儿提醒。
 */
internal object OhosCourseReminder {

    /**
     * 滚动窗口天数。
     *
     * 鸿蒙没有「每天凌晨重新排一遍」的等价闹钟，所以一次要铺满未来若干天；
     * 7 天 ≈ 40 条课前 + 7 条次日。应用每次启动/课表变更都会对账。
     */
    private const val WINDOW_DAYS = 7

    /** 单次最多排的提醒条数：只是防止极端课表一次挂太多定时器。 */
    private const val MAX_PENDING = 56

    private const val PREF_IN_CLASS = "course_reminder_prefs"
    private const val TAG = "OhosCourseReminder"

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()

    /** 重新对账（等价安卓 `startReminderService`）。 */
    fun reconcile() {
        // 存储还没装配好时直接跳过：启动早期由 OhosRoot 的 LaunchedEffect 再对账一次
        if (!OhosEnv.isReady || !AppFiles.isReady || !AppStorage.isReady) return
        scope.launch {
            mutex.withLock {
                runCatching { publishWindow() }
                    .onFailure { NexioLog.e(TAG, "reconcile failed: ${it.message}") }
            }
        }
    }

    /** 关闭全部课程提醒（等价安卓 `stopReminderService`）。 */
    fun stop() {
        scope.launch {
            mutex.withLock {
                ReminderBridge.request("cancelAll", "{}")
            }
        }
    }

    // ————————————————————————— 计算 —————————————————————————

    private data class Item(
        val id: String,
        val fireEpochMillis: Long,
        val fireDate: LocalDate,
        val fireTime: LocalTime,
        val title: String,
        val content: String,
    )

    private suspend fun publishWindow() {
        val repo = CourseRepository()
        val preClass = repo.getPreClassReminder()
        val nextDay = repo.getNextDayReminder()
        if (!preClass && !nextDay) {
            ReminderBridge.request("cancelAll", "{}")
            NexioLog.i(TAG, "reconcile: all switches off, cancelled")
            return
        }

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val entriesByYear = HolidayManager.loadAllByYear()
        val pending = mutableListOf<Item>()

        if (preClass) {
            for (offset in 0 until WINDOW_DAYS) {
                val date = today.plus(DatePeriod(days = offset))
                // 年份缺失只表示「当天没有节假日豁免」，不能跳过整天
                val resolution = DayScheduleResolver.resolveDaySchedule(
                    date = date,
                    repository = repo,
                    holidayEntriesByYear = entriesByYear,
                )
                pending += preClassItems(date, resolution.courses, repo)
            }
        }

        if (nextDay) {
            for (offset in 0 until WINDOW_DAYS) {
                val date = today.plus(DatePeriod(days = offset))
                nextDayItem(date, repo, entriesByYear)?.let { pending += it }
            }
        }

        // 超出上限时保留最近的：第 7 天可以等下一次对账，下一节课不能等
        val limited = pending.sortedBy { it.fireEpochMillis }.take(MAX_PENDING)
        NexioLog.i(TAG, "reconcile: ${limited.size} reminders (of ${pending.size})")

        val payload = buildJsonObject {
            putJsonArray("items") {
                for (item in limited) {
                    addJsonObject {
                        put("id", item.id)
                        // 代理提醒对标题/正文长度有上限，超长会让 publish 直接失败
                        put("title", item.title.take(120))
                        put("content", item.content.take(240))
                        put("year", item.fireDate.year)
                        put("month", item.fireDate.monthNumber)
                        put("day", item.fireDate.dayOfMonth)
                        put("hour", item.fireTime.hour)
                        put("minute", item.fireTime.minute)
                    }
                }
            }
        }.toString()

        val (ok, result) = suspendCancellableCoroutine { cont ->
            ReminderBridge.request("reschedule", payload) { ok, result ->
                if (cont.isActive) cont.resume(ok to result)
            }
        }
        if (!ok) NexioLog.w(TAG, "publish rejected: $result")
    }

    /** 一节课一条提醒：上课时间减去配置的提前量。 */
    private fun preClassItems(
        date: LocalDate,
        courses: List<Course>,
        repo: CourseRepository,
    ): List<Item> {
        val leadMinutes = repo.getPreClassReminderMinutes()
        val now = Clock.System.now().toEpochMilliseconds()
        val tz = TimeZone.currentSystemDefault()
        val rule = ClassroomLabel.read(AppStorage.store(PREF_IN_CLASS))

        return courses.mapIndexedNotNull { index, course ->
            val start = CourseTimeResolver.getStartTime(course, repo) ?: return@mapIndexedNotNull null
            val startTime = parseClock(start) ?: return@mapIndexedNotNull null
            val endTime = CourseTimeResolver.getEndTime(course, repo)?.let { parseClock(it) }

            // 提前量为负时提醒会落到前一天：日偏移就是这里产生的
            val startMin = startTime.hour * 60 + startTime.minute
            val fireMin = startMin - leadMinutes
            val fireDate = if (fireMin < 0) date.plus(DatePeriod(days = -1)) else date
            val normalized = ((fireMin % 1440) + 1440) % 1440
            val fireTime = LocalTime(normalized / 60, normalized % 60)
            val fireEpoch = fireDate.atTime(fireTime).toInstant(tz).toEpochMilliseconds()
            // 已经过去的提醒发不出去，留给下一次对账
            if (fireEpoch <= now) return@mapIndexedNotNull null

            // 跨零点的课：下课时间算到次日（只影响正文里的时间段）
            val endText = endTime?.let(::formatClock) ?: ""
            val courseName = ClassroomLabel.applyCourse(course.name, rule)
            val classroom = ClassroomLabel.apply(course.classroom, rule)
            val title = if (leadMinutes > 0) {
                L("%s · %d分钟后上课", "%s · Class in %d min")(courseName, leadMinutes)
            } else {
                L("%s · 即将上课", "%s · Starting soon")(courseName)
            }
            val section = sectionText(course)
            val body = buildString {
                val separator = L("｜", " · ")()
                if (section.isNotEmpty()) append(section)
                if (classroom.isNotEmpty()) {
                    if (isNotEmpty()) append(separator)
                    append(classroom)
                }
                if (course.teacher.isNotEmpty()) {
                    if (isNotEmpty()) append(separator)
                    append(course.teacher)
                }
                if (endText.isNotEmpty()) {
                    if (isNotEmpty()) append(separator)
                    append("$start-$endText")
                }
            }
            Item(
                id = "pre.$date.${course.id}.$index",
                fireEpochMillis = fireEpoch,
                fireDate = fireDate,
                fireTime = fireTime,
                title = title,
                content = body,
            )
        }
    }

    /** 次日课程汇总的一条提醒。 */
    private fun nextDayItem(
        today: LocalDate,
        repo: CourseRepository,
        entriesByYear: Map<Int, List<HolidayEntry>>,
    ): Item? {
        val tomorrow = today.plus(DatePeriod(days = 1))
        val resolution = DayScheduleResolver.resolveDaySchedule(
            date = tomorrow,
            repository = repo,
            holidayEntriesByYear = entriesByYear,
        )
        if (resolution.courses.isEmpty()) return null

        val tz = TimeZone.currentSystemDefault()
        val fireTime = LocalTime(repo.getNextDayReminderHour(), repo.getNextDayReminderMinute())
        val fireEpoch = today.atTime(fireTime).toInstant(tz).toEpochMilliseconds()
        if (fireEpoch <= Clock.System.now().toEpochMilliseconds()) return null

        val lines = resolution.courses.mapNotNull { course ->
            val start = CourseTimeResolver.getStartTime(course, repo) ?: return@mapNotNull null
            if (course.classroom.isBlank()) {
                "${course.name} $start"
            } else {
                "${course.name} $start · ${course.classroom}"
            }
        }
        return Item(
            id = "next.$tomorrow",
            fireEpochMillis = fireEpoch,
            fireDate = today,
            fireTime = fireTime,
            title = L("明日课程（%d 节）", "Tomorrow's Classes (%d)")(resolution.courses.size),
            content = lines.joinToString("\n"),
        )
    }

    // ————————————————————————— 工具 —————————————————————————

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
}
