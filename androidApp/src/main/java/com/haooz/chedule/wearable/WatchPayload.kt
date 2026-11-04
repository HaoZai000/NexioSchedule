package com.haooz.chedule.wearable

import android.content.Context
import com.haooz.chedule.data.holiday.HolidayEntries
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.toJson
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.CourseTimeResolver
import com.haooz.chedule.data.model.PeriodTimeSource
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.util.minusDays
import com.haooz.chedule.data.util.plusDays
import com.haooz.chedule.data.util.plusWeeks
import com.haooz.chedule.data.util.todayLocalDate
import kotlinx.datetime.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * 手机端 → 手表端 课表 JSON 组装，协议 version=4 整表推送。
 *
 * 一次下发完整学期，手表端自行推算任意日期，不再依赖「当前周快照 + 按日期窗口」。
 * 协议与手表 `src/common/sync.js` 的整表分支对齐，跨端字段改动必须两边同步：
 * - 顶层：protocol=nexio.schedule / version=4 / action=replace / sentAt / schedule_name
 * - settings：class_start_time 开学日、current_week 实时教学周（手表据此校准调休合并周
 *   偏移）、total_weeks、morning|afternoon|evening_sections、
 *   pre_class_reminder_minutes 课前提醒提前量（手表「快上课了」判定用，缺省手表端取 15）
 * - times：相对节次号 -> "HH:mm-HH:mm"（getPeriodTimes 原格式）
 * - courses：整学期全部课程，不过滤周次；dayOfWeek 1=周一…7=周日；
 *   startWeek/endWeek 为原始值（0=未设置），weekType 0全周/1单周/2双周，
 *   selectedWeeks 非空时优先于上面三者
 * - holidays：HolidayEntry[]，缺失则手表无法显示调休日。
 *   ⚠ 数组顺序即优先级：手表端只取第一条命中，不认 custom / 年份优先级，
 *     排序与去重见 buildHolidaysJson，改动前务必读 HolidayEntries.forFlatPush。
 *
 * 手表端周次 = floorDiv(开学日到当天天数 + (开学日星期-1), 7) + 1，再用 current_week
 * 校准偏移（±2 内吸收）；周次过滤与 Course.isActiveInWeek 严格一致。
 *
 * ── v4.1 新增：overrides ────────────────────────────────────────────────
 * - overrides：DayOverride[]，**手机逐日裁决好的结果**，覆盖假期内外的每一天（见
 *   [buildOverridesJson]）。手表翻到这些日期时直接采用，不再自行推算。
 * - 为什么必须有它：v4.0 手表拿到的只有「星期模板 + 全局节次表 + 假期条目」，它按自己的
 *   公式重算某一天，但以下四类信息 v4.0 根本没发，重算必然和手机不一致：
 *     1. 调休跟随日的**教学周**（[DayScheduleResolver.resolveDaySchedule] 用的是
 *        follow 那天的周，不是查看日的周 —— 跨周边界的调休会套错周，单双周翻转）；
 *     2. 假期末日 / 假期前一天的**节次剔除**（HolidayEndCourseExclusion /
 *        HolidayBeforeCourseExclusion，手表端完全没有这两个概念）；
 *     3. **按日期生效的作息方案**（TimeConfig.routineFor 按日历日期挑夏令时/冬令时，
 *        v4.0 的 times 只有推送时刻生效的那一套）；
 *     4. 教学周重组把某标成休课日时的退回星期（position.weekday == null → 日历星期）。
 *   「单独调整调休日的课程时间 / 节数」正是 1/2/3 的表现 —— 用户改的东西能出手机，
 *   只要那天落在 overrides 里就能同步。
 * - 兼容性：overrides 是**新增字段**，v4.0 手表读到不认识的 version=4.1 也只打一行诊断
 *   日志、照旧解析（见手表 sync.js normalizePayload 的版本宽容分支），行为与 4.0 一致；
 *   新版手表命中 overrides 才用它，未命中回落到原推算。因此**不需要与 rpk 同步发版**。
 * - 体积：只覆盖假期区间端点与调休日（寒假/暑假这类长区间不逐日展开），上限
 *   [MAX_OVERRIDE_DAYS] 条，每条一门课约 120 字节，最坏 +10 KB 量级。
 */
object WatchPayload {

    private const val PROTOCOL = "nexio.schedule"

    /**
     * 协议版本 4.1 = 4 + [buildOverridesJson] 的逐日裁决覆盖。
     * 顶层结构、courses/settings/times/holidays 的语义与 4.0 完全一致。
     */
    private const val VERSION = 4.1

    /**
     * 假期下发窗口在学期首尾 / 今天之外的额外余量（天）。
     *
     * 手表端只能线性扫数组取第一条命中，没法像手机端那样按「custom + 年份优先级」挑，
     * 所以重活只能在手机端做完（见 [HolidayEntries.forFlatPush]）；这个窗口只负责
     * 把历史年份的存量挡在包外。留 60 天足够覆盖跨年学期 + 前后翻看。
     */
    private const val HOLIDAY_WINDOW_PAD_DAYS = 60L

    /** override 条数上限：单包体积保护（手环 interconnect 有消息大小限制） */
    private const val MAX_OVERRIDE_DAYS = 90

    /**
     * 单包字节硬上限。
     *
     * 手环端 interconnect 的具体上限没有公开契约，这里取一个保守值：正常课表
     * （20 门课 + 假期）大约 20–30 KB，留够余量。超了不是「发失败」而是
     * `sendMessage` 静默失败 —— 所以必须自己裁。
     */
    private const val MAX_PAYLOAD_BYTES = 48 * 1024

    /** `,"overrides":[]` 这层信封本身的字节余量。 */
    private const val OVERRIDES_ENVELOPE_BYTES = 64

    /** 单个调休区间最多展开的天数（防脏数据把区间写成整年） */
    private const val MAX_OVERRIDE_RANGE_DAYS = 31

    /** 整表推送 JSON。课程取 Course 原始值，周次过滤与星期坐标系都交给手表端。 */
    // USELESS_ELVIS / ELVIS_ALWAYS_NULL：Course 字段声明为非空 String，但 Gson UnsafeAllocator
    // 会给旧数据塞 null，这里的 ?: 是推送兜底，不能删。与 CourseRepository.sanitizeCourses 同一处理。
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
    fun buildFullJson(
        repository: CourseRepository,
        context: Context,
        scheduleId: String = ""
    ): String {
        val sid = scheduleId.ifEmpty { repository.getCurrentScheduleId() }
        val all = if (sid == repository.getCurrentScheduleId()) {
            repository.getAllCourses()
        } else {
            repository.getCoursesForSchedule(sid)
        }

        val courseArr = JSONArray()
        for (course in all) {
            if (course.dayOfWeek !in 1..7) continue
            courseArr.put(
                JSONObject()
                    .put("id", course.id)
                    .put("name", course.name)
                    .put("dayOfWeek", course.dayOfWeek)
                    .put("startSection", course.startSection)
                    .put("endSection", course.endSection)
                    .put("startWeek", course.startWeek)
                    .put("endWeek", course.endWeek)
                    .put("weekType", course.weekType)
                    .put("selectedWeeks", JSONArray(course.selectedWeeks))
                    .put("isCustomTime", course.isCustomTime)
                    .put("customStartTime", course.customStartTime ?: "")
                    .put("customEndTime", course.customEndTime ?: "")
                    .put("location", course.classroom ?: "")
                    .put("teacher", course.teacher ?: "")
            )
        }

        val settings = JSONObject()
            .put("class_start_time", repository.getClassStartTime(sid))
            .put("current_week", repository.getLiveTeachingWeek(scheduleId = sid))
            .put("total_weeks", repository.getTotalWeeks(sid))
            .put("morning_sections", repository.getMorningSections(sid))
            .put("afternoon_sections", repository.getAfternoonSections(sid))
            .put("evening_sections", repository.getEveningSections(sid))
            // 手表据此判定「快上课了」的提前量；不下发时手表端固定用 15 分钟。
            .put("pre_class_reminder_minutes", repository.getPreClassReminderMinutes())

        val times = JSONObject()
            .put("morning", periodTimesJson(repository, "morning", sid))
            .put("afternoon", periodTimesJson(repository, "afternoon", sid))
            .put("evening", periodTimesJson(repository, "evening", sid))

        // holidays 与 overrides 共用同一份「推送专用」条目集与窗口：两边看到的假期必须一致，
        // 否则手表用 holidays 推算假、用 overrides 拿答案，两个来源打架比 v4.0 更糟。
        val today = todayLocalDate()
        val (windowFirst, windowLast) = holidayWindow(repository, sid, today)
        val entriesByYear = HolidayManager.loadAllByYear()
        val flatHolidays = HolidayEntries.forFlatPush(
            entriesByYear = entriesByYear,
            today = today,
            windowFirst = windowFirst,
            windowLast = windowLast,
        )
        val holidaysArr = buildHolidaysJson(flatHolidays)
        // overrides 依赖 resolveDaySchedule，它内部按「当前课表」取课程与周次；
        // 推非当前课表时结果会串表 —— 宁可不发（手表回退 v4.0 推算），也不发错答案。
        //
        // 整段包 runCatching：以前 getTimeConfig / effective 抛异常会直接冒到
        // buildFullJson 外面，整包构建失败 → 手环一条都收不到。overrides 只是增强，
        // 失败就退化成老载荷（手环自己推算），不该拖累主功能。
        val overridesArr = if (sid == repository.getCurrentScheduleId()) {
            runCatching {
                buildOverridesJson(repository, sid, flatHolidays, windowFirst, windowLast, entriesByYear)
            }.onFailure {
                NexioLog.w("WatchPayload", "overrides build fail, degrade to plain payload: ${it.message}")
            }.getOrDefault(JSONArray())
        } else {
            JSONArray()
        }
        NexioLog.i(
            "WatchPayload",
            "buildFull sid=$sid total=${all.size} packed=${courseArr.length()} " +
                "week=${settings.optInt("current_week")}/${settings.optInt("total_weeks")} " +
                "holidays=${holidaysArr.length()} overrides=${overridesArr.length()}"
        )

        val payload = JSONObject()
            .put("protocol", PROTOCOL)
            .put("version", VERSION)
            .put("action", "replace")
            .put("sentAt", System.currentTimeMillis())
            .put("schedule_name", sid)
            .put("settings", settings)
            .put("times", times)
            .put("courses", courseArr)
            .put("holidays", holidaysArr)
        // 字节预算：手环 interconnect 有单包上限，超了 sendMessage 会失败而 UI 不知情。
        // 按 UTF-8 实际字节裁 overrides（它是最可裁的部分），主数据一条不动。
        val fittedOverrides = fitOverridesWithinBudget(payload, overridesArr)
        payload.put("overrides", fittedOverrides)
        val json = payload.toString()
        NexioLog.i(
            "WatchPayload",
            "payload bytes=${json.toByteArray(Charsets.UTF_8).size} " +
                "overrides=${fittedOverrides.length()}/${overridesArr.length()}"
        )
        return json
    }

    /**
     * 把 overrides 裁进单包字节预算内。
     *
     * 以前只限制了「天数」（[MAX_OVERRIDE_DAYS]），但每个 override 日内联完整课程对象，
     * 最坏能顶到上百 KB。超限时 sendMessage 直接失败，而导出 UI 照样提示成功 ——
     * 用户看到的就是「点了没反应」。宁可少发几天，也不要整包发不出去。
     */
    private fun fitOverridesWithinBudget(base: JSONObject, overrides: JSONArray): JSONArray {
        if (overrides.length() == 0) return overrides
        // base 还没挂 overrides，先按它的实际字节数留出余量
        val baseBytes = base.toString().toByteArray(Charsets.UTF_8).size
        var budget = MAX_PAYLOAD_BYTES - baseBytes - OVERRIDES_ENVELOPE_BYTES
        if (budget <= 0) {
            NexioLog.w("WatchPayload", "payload base already ${baseBytes}B, overrides dropped")
            return JSONArray()
        }
        val kept = JSONArray()
        for (i in 0 until overrides.length()) {
            val item = overrides.optJSONObject(i) ?: continue
            val cost = item.toString().toByteArray(Charsets.UTF_8).size + 1
            if (cost > budget) {
                NexioLog.w(
                    "WatchPayload",
                    "overrides budget hit: keep=$i/${overrides.length()} budget=${budget}B cost=${cost}B",
                )
                break
            }
            kept.put(item)
            budget -= cost
        }
        return kept
    }

    /**
     * HolidayEntry → 手表 holidays 数组（字段与 HolidayEntry.toJson 一致）。
     *
     * ⚠ 顺序即语义：手表端只按数组顺序取第一条命中，没有 `custom` / 年份优先级概念。
     * 所以排序 + 去重必须在手机端做完（[HolidayEntries.forFlatPush]），否则自定义
     * 调休 / 当年条目会被更早年份的同名条目盖掉，手表换课跟错。
     *
     * 现在收的是 [buildFullJson] 里已经过 forFlatPush 的同一份列表，overrides 用的也是它，
     * 两端同源，不会漂。
     */
    private fun buildHolidaysJson(flatHolidays: List<HolidayEntry>): JSONArray {
        val arr = JSONArray()
        for (entry in flatHolidays) {
            // ⚠ 必须包一层 JSONObject(...)：entry.toJson() 现在返回 kotlinx 的 JsonObject
            // （不是 org.json 的类型），直接 put 进去会被 org.json 当成未知对象转成
            // 一个**带引号的字符串**，手表侧就解析不出结构了。转成 JSON 文本再解析回来
            // 得到的还是同一个对象，载荷逐字不变。
            arr.put(JSONObject(entry.toJson().toString()))
        }
        return arr
    }

    /**
     * v4.1：手机逐日裁决 → 手表 overrides 数组。
     *
     * 逐日调用 [DayScheduleResolver.resolveDaySchedule] —— 就是手机今日页/提醒/小组件用的
     * **同一个**函数，所以手表拿到的是"答案"而不是"再算一遍的材料"，四类 v4.0 发不出的
     * 信息（跟随周、节次剔除、按日期作息、重组休课日）自动全对。
     *
     * 覆盖范围：窗口内每个假期区间的**端点日**（首日/末日/末日±1）与每个**调休日**。
     * 长区间（寒暑假）不逐日展开，只有端点附近几天。上限 [MAX_OVERRIDE_DAYS]。
     *
     * ⚠ 时间表达：override 内的课程**默认原样下发**（保留 startSection/endSection 语义）。
     * 只有当该日作息解析出的钟点与推送时刻全局 `times`（即手表收到的 `times` 字段）**不同**时，
     * 才把墙钟写成 isCustomTime/customStartTime/customEndTime（[renderCourse]）—— 手表端
     * `fullCourseTimes` 的「自定义时间优先」会采用它。时间一致的课程不受改写影响，「第几节」
     * 与上午/下午分组语义完全保留。
     */
    private fun buildOverridesJson(
        repository: CourseRepository,
        scheduleId: String,
        flatHolidays: List<HolidayEntry>,
        windowFirst: LocalDate,
        windowLast: LocalDate,
        entriesByYear: Map<Int, List<HolidayEntry>>,
    ): JSONArray {
        val arr = JSONArray()
        val dates = overrideDates(flatHolidays, windowFirst, windowLast)
        if (dates.isEmpty()) return arr
        val config = repository.getTimeConfig(repository.getScheduleTimeConfigId(scheduleId))
        // 推送时刻的全局 times 就是手表收到的 `times` —— 以它为基准，只有偏差日才改写课程时间
        val pushCtx = DayContext(config.effective(todayLocalDate()))
        for (date in dates) {
            if (arr.length() >= MAX_OVERRIDE_DAYS) {
                NexioLog.w("WatchPayload", "overrides truncated at $MAX_OVERRIDE_DAYS days")
                break
            }
            val resolution = runCatching {
                DayScheduleResolver.resolveDaySchedule(date, repository, entriesByYear)
            }.getOrNull() ?: continue
            // effective(date) 只是扫一遍作息列表挑方案，逐日调用开销可忽略
            arr.put(
                renderOverride(
                    date, resolution,
                    DayContext(config.effective(date)), pushCtx, entriesByYear
                )
            )
        }
        return arr
    }

    /**
     * 某个 override 日的时间来源：该日按 [TimeConfig.routineFor] 选中的作息。
     *
     * 实现 [PeriodTimeSource] 是为了直接喂给 [CourseTimeResolver] —— 手机上今日页/提醒
     * 解析课程时间走的就是这个解析器，这里复用同一入口，杜绝第二套时间算法。
     * ⚠ 不能图省事传 repository：它的 getPeriodTimes 按「今天」选作息，而 override 可能是
     * 下周或假期另一头，按日偏差正是要修的东西。
     */
    private class DayContext(
        private val config: TimeConfig,
    ) : PeriodTimeSource {
        override fun getPeriodTimes(period: String): Map<Int, String> = config.getPeriodTimes(period)
        override fun getMorningSections(): Int = config.morningSections
        override fun getAfternoonSections(): Int = config.afternoonSections
    }

    private fun renderOverride(
        date: LocalDate,
        resolution: DayScheduleResolver.DayScheduleResolution,
        ctx: DayContext,
        pushCtx: DayContext,
        entriesByYear: Map<Int, List<HolidayEntry>>,
    ): JSONObject {
        val courses = JSONArray()
        for (course in resolution.courses) {
            courses.put(renderCourse(course, ctx, pushCtx))
        }
        return JSONObject()
            .put("date", date.toString())
            // 1=周一..7=周日（ISO，与顶层 courses.dayOfWeek 同域），非手表 JS 的 0=周日
            .put("weekday", resolution.displayDayOfWeek)
            .put("week", resolution.displayWeek)
            .put("holiday", resolution.isHolidayDate)
            .put("workswap", resolution.isWorkSwap)
            .put("holidayName", holidayNameForDate(entriesByYear, date))
            .put("courses", courses)
    }

    /**
     * 当天首个命中条目的名称（手表空态「今天是假期·XX」文案用）。
     *
     * v4.0 里手表按自己的 holidays 顺序查名字，与手机裁决用的那份可能不同年/不同条目；
     * override 既然已经带 holiday 标志，名字也必须同源，否则会出现「课程是手机的答案、
     * 假期名是手表自己猜的」。entriesByYear 直接复用调用方已加载的那份，不再读存储。
     */
    private fun holidayNameForDate(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
    ): String = runCatching { HolidayEntries.entriesForDate(entriesByYear, date) }
        .getOrDefault(emptyList())
        .firstOrNull { it.name.isNotBlank() }?.name
        ?: ""

    /**
     * 课程 → 手表字段（与顶层 courses 同构）。
     *
     * 与顶层的唯一差异：该日作息与推送时刻作息解析出的钟点不同时，把墙钟固化成
     * isCustomTime/customStartTime/customEndTime（手表端 `fullCourseTimes` 的
     * 「自定义时间优先」直接采用）。一致则原样下发，节次号语义保留。
     */
    // Course 字段 Gson 泛型丢失后可能为 null，与 buildFullJson 同一处理
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
    private fun renderCourse(course: Course, ctx: DayContext, pushCtx: DayContext): JSONObject {
        val obj = JSONObject()
            .put("id", course.id)
            .put("name", course.name)
            .put("dayOfWeek", course.dayOfWeek)
            .put("startSection", course.startSection)
            .put("endSection", course.endSection)
            .put("startWeek", course.startWeek)
            .put("endWeek", course.endWeek)
            .put("weekType", course.weekType)
            .put("selectedWeeks", JSONArray(course.selectedWeeks))
            .put("isCustomTime", course.isCustomTime)
            .put("customStartTime", course.customStartTime ?: "")
            .put("customEndTime", course.customEndTime ?: "")
            .put("location", course.classroom ?: "")
            .put("teacher", course.teacher ?: "")
        val dayStart = CourseTimeResolver.getStartTime(course, ctx)
        val dayEnd = CourseTimeResolver.getEndTime(course, ctx)
        val pushStart = CourseTimeResolver.getStartTime(course, pushCtx)
        val pushEnd = CourseTimeResolver.getEndTime(course, pushCtx)
        // 只有该日钟点与手表手上的全局 times 不一致才改写
        if (dayStart != null && dayEnd != null && (dayStart != pushStart || dayEnd != pushEnd)) {
            obj.put("isCustomTime", true)
                .put("customStartTime", dayStart)
                .put("customEndTime", dayEnd)
        }
        return obj
    }

    /**
     * 需要 override 的日期集合（升序）：
     * - 每个调休条目命中区间的全部日期（调休常是单日，几天的小长假补班也不多）
     * - 每个假期区间：首日、末日、末日+1（节次剔除作用在假期末日与假期前一天）
     * - 今天（作息切换最常在这里生效，也让手表端的 current_week 校准有个锚点）
     */
    private fun overrideDates(
        flatHolidays: List<HolidayEntry>,
        windowFirst: LocalDate,
        windowLast: LocalDate,
    ): List<LocalDate> {
        val set = sortedSetOf<LocalDate>()
        val today = todayLocalDate()
        set.add(today)
        for (entry in flatHolidays) {
            val start = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: continue
            val end = runCatching { LocalDate.parse(if (entry.endDate.isBlank()) entry.date else entry.endDate) }
                .getOrNull() ?: start
            if (entry.type == HolidayEntry.TYPE_WORKSWAP) {
                var d = start
                var guard = 0
                while (d <= end && guard < MAX_OVERRIDE_RANGE_DAYS) {
                    set.add(d)
                    d = d.plusDays(1)
                    guard++
                }
            } else {
                set.add(start)
                set.add(end)
                set.add(end.plusDays(1))
                set.add(start.minusDays(1))
            }
        }
        return set.filter { it in windowFirst..windowLast }.toList()
    }

    /** 下发窗口 = min(学期首日, 今天) .. max(学期末日, 今天)，两端各留 [HOLIDAY_WINDOW_PAD_DAYS]。
     *
     * 取并集是必要的：学期可能还没开始（新生），也可能早已结束，
     * 但手表端仍会按今天所在的周展示课表。
     */
    private fun holidayWindow(
        repository: CourseRepository,
        scheduleId: String,
        today: LocalDate,
    ): Pair<LocalDate, LocalDate> {
        // 开学日缺失/脏值时 getClassStartTime 已回退成今天，这里再兜一层纯属防御
        val semesterStart =
            CourseRepository.parseFlexibleDate(repository.getClassStartTime(scheduleId)) ?: today
        val semesterLast = semesterStart.plusWeeks(repository.getTotalWeeks(scheduleId).toLong())
        val first = minOf(semesterStart, today).minusDays(HOLIDAY_WINDOW_PAD_DAYS)
        val last = maxOf(semesterLast, today).plusDays(HOLIDAY_WINDOW_PAD_DAYS)
        return first to last
    }

    /** 节次时间 → 手表格式 {相对节次号: 'HH:mm-HH:mm'}（getPeriodTimes 本身即此格式） */
    // 同 buildFullJson：Map 声明为 Map<Int,String>，Gson 泛型丢失后 value 仍可能是 null
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
    private fun periodTimesJson(
        repository: CourseRepository,
        period: String,
        scheduleId: String
    ): JSONObject {
        val obj = JSONObject()
        for ((index, time) in repository.getPeriodTimes(period, scheduleId)) {
            obj.put(index.toString(), time ?: "")
        }
        return obj
    }
}