package com.haooz.nexio.ohos

import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.CourseTimeResolver
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.util.CourseTimeSpan
import com.haooz.chedule.i18n.CommonStr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 桌面小组件的数据快照。
 *
 * 卡片的 UI 跑在**桌面进程**里（ArkTS 卡片，不能跑 Compose，也读不到 Kotlin 对象），
 * 所以数据必须先落成沙箱里的 JSON：Kotlin 负责算（周次、节假日、调休、课程时间），
 * `CourseWidgetAbility` / 入口 Ability 负责把它塞进 `formBindingData`。
 *
 * 为什么放「绝对时间戳」而不是「N 分钟之后」：`updateDuration` 最快 30 分钟一次，
 * 卡片拿到的是上一次的快照；卡片自己按 `Date.now()` 过滤已结束的课，
 * 跨零点后也不会继续显示昨天的课。
 */
internal object OhosWidgetSnapshot {

    private const val TAG = "OhosWidgetSnapshot"
    private const val FILE_NAME = "widget_snapshot.json"

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()

    /** 重算并落盘，然后让 ArkTS 刷新已添加的卡片（没有卡片时是无害的空操作）。 */
    fun refresh() {
        if (!OhosEnv.isReady || !AppFiles.isReady || !AppStorage.isReady) return
        scope.launch {
            mutex.withLock {
                val json = runCatching { buildSnapshot() }
                    .onFailure { NexioLog.e(TAG, "build snapshot failed: ${it.message}") }
                    .getOrNull() ?: return@withLock
                runCatching {
                    AppFiles.root.resolve(FILE_NAME).writeBytes(json.encodeToByteArray())
                }.onFailure { NexioLog.e(TAG, "write snapshot failed: ${it.message}") }
                ReminderBridge.request("refreshWidgets", "{}")
            }
        }
    }

    private fun buildSnapshot(): String {
        val repository = CourseRepository()
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(zone).date
        val holidays = HolidayManager.loadAllByYear()
        val totalWeeks = repository.getTotalWeeks()
        val lastCourseWeek = repository.getLastWeekWithCourses()

        val todayBlock = resolveDay(today, repository, holidays, zone, totalWeeks, lastCourseWeek)
        val tomorrowBlock = resolveDay(
            today.plus(DatePeriod(days = 1)),
            repository,
            holidays,
            zone,
            totalWeeks,
            lastCourseWeek,
        )

        // 与安卓同一条「次日提醒」规则：开关开着、到了提醒时间、今天的课上完了 → 卡片改显示明天
        val nowMinutes = now.toLocalDateTime(zone).hour * 60 + now.toLocalDateTime(zone).minute
        val nowMillis = now.toEpochMilliseconds()
        val showTomorrow = repository.getNextDayReminder() &&
            nowMinutes >= repository.getNextDayReminderHour() * 60 + repository.getNextDayReminderMinute() &&
            todayBlock.courses.none { it.end > nowMillis }

        return buildJsonObject {
            put("generatedAt", nowMillis)
            put("showTomorrow", showTomorrow)
            // 顶层仍是今天的字段：旧卡片（今日课程）直接用，不用改
            putDay(todayBlock)
            // 今日与明日卡片多一段
            putJsonObject("tomorrow") { putDay(tomorrowBlock) }
        }.toString()
    }

    private data class DayBlock(
        val week: Int,
        val weekday: String,
        val status: String,
        val month: Int,
        val day: Int,
        val courses: List<CourseEntry>,
    )

    private fun resolveDay(
        date: LocalDate,
        repository: CourseRepository,
        holidays: Map<Int, List<HolidayEntry>>,
        zone: TimeZone,
        totalWeeks: Int,
        lastCourseWeek: Int,
    ): DayBlock {
        val resolution = DayScheduleResolver.resolveDaySchedule(date, repository, holidays)
        val status = when {
            resolution.displayWeek < 1 -> "notStarted"
            resolution.isHolidayDate ||
                resolution.displayWeek > totalWeeks ||
                resolution.displayWeek > lastCourseWeek -> "holiday"
            else -> "normal"
        }
        val courses = if (status == "normal") {
            resolution.courses.mapNotNull { courseSnapshot(it, date, repository, zone) }
                .sortedBy { it.start }
        } else {
            emptyList()
        }
        return DayBlock(
            week = resolution.displayWeek,
            weekday = weekdayText(date.dayOfWeek.isoDayNumber),
            status = status,
            month = date.monthNumber,
            day = date.dayOfMonth,
            courses = courses,
        )
    }

    private fun JsonObjectBuilder.putDay(block: DayBlock) {
        put("week", block.week)
        put("weekday", block.weekday)
        put("status", block.status)
        put("month", block.month)
        put("day", block.day)
        putJsonArray("courses") {
            for (course in block.courses) {
                addJsonObject {
                    put("name", course.name)
                    put("room", course.room)
                    put("info", course.info)
                    put("infoShort", course.infoShort)
                    put("start", course.start)
                    put("end", course.end)
                    put("color", course.color)
                }
            }
        }
    }

    private data class CourseEntry(
        val start: Long,
        val end: Long,
        val name: String,
        val room: String,
        /** 与安卓 `buildCourseInfo` 同口径：节次 / 教室 / 教师，用「｜」连接 */
        val info: String,
        /** 今明卡片专用：只有 教室｜教师（节次信息由第二行时间承担） */
        val infoShort: String,
        /** `#AARRGGBB`，与安卓小部件的课程色条同源（`Course.colorRes`） */
        val color: String,
    )

    private fun courseSnapshot(
        course: Course,
        date: LocalDate,
        repository: CourseRepository,
        zone: TimeZone,
    ): CourseEntry? {
        val startTime = parseClock(CourseTimeResolver.getStartTime(course, repository)) ?: return null
        val endTime = parseClock(CourseTimeResolver.getEndTime(course, repository)) ?: return null
        val endDate = if (CourseTimeSpan.crossesMidnight(
                startTime.hour * 60 + startTime.minute,
                endTime.hour * 60 + endTime.minute,
            )
        ) {
            date.plus(DatePeriod(days = 1))
        } else {
            date
        }
        val start = date.atTime(startTime).toInstant(zone).toEpochMilliseconds()
        val end = endDate.atTime(endTime).toInstant(zone).toEpochMilliseconds()
        return CourseEntry(
            start = start,
            end = end,
            name = course.name,
            room = course.classroom,
            info = courseInfo(course),
            infoShort = courseInfoShort(course),
            color = colorHex(course.colorRes),
        )
    }

    /** 与安卓 `CourseWidgetProviderStandard.buildCourseInfo` 保持一致。 */
    private fun courseInfo(course: Course): String = buildList {
        if (!course.hasValidCustomTime()) add(course.getSectionText())
        if (course.classroom.isNotEmpty()) add(course.classroom)
        if (course.teacher.isNotEmpty()) add(course.teacher)
    }.filter { it.isNotEmpty() }.joinToString("｜")

    /** 今明卡片的第三行：只有教室与教师，不含节次。 */
    private fun courseInfoShort(course: Course): String = buildList {
        if (course.classroom.isNotEmpty()) add(course.classroom)
        if (course.teacher.isNotEmpty()) add(course.teacher)
    }.joinToString("｜")

    /** `Course.colorRes` 是 ARGB；alpha 为 0 的旧数据按不透明处理，否则卡片上会看不见。 */
    private fun colorHex(colorRes: Long): String {
        val argb = (colorRes and 0xFFFFFFFFL).let {
            if ((it ushr 24) == 0L) it or 0xFF000000L else it
        }
        return "#" + argb.toString(16).padStart(8, '0').uppercase()
    }

    private fun parseClock(value: String?): LocalTime? {
        val parts = value?.split(":") ?: return null
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime(hour, minute)
    }

    private fun weekdayText(isoDay: Int): String = when (isoDay) {
        1 -> CommonStr.weekdayMon()
        2 -> CommonStr.weekdayTue()
        3 -> CommonStr.weekdayWed()
        4 -> CommonStr.weekdayThu()
        5 -> CommonStr.weekdayFri()
        6 -> CommonStr.weekdaySat()
        else -> CommonStr.weekdaySun()
    }
}
