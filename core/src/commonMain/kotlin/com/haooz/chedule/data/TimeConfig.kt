package com.haooz.chedule.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

// 注：TimeRoutine / SpecialItem / SpecialBlock 的 fromRaw 原先是 internal。
// 搬到 :core 后 internal 的可见范围变成了 :core 模块本身，:app 的解析与导入逻辑访问不到，
// 因此改为 public。等阶段 2 换成 kotlinx.serialization、由编译器生成反序列化代码后，
// 这些手工 fromRaw 应当整体删除。

/**
 * 作息方案：同一套节次骨架（上午/下午/晚上各几节）下的**一套时间**。
 *
 * 用于夏令时/冬令时这类"节次不变、时间变"的场景。每个方案带一个「几月几号起生效」，
 * 到日期自动切换，无需手动操作。
 *
 * 生效规则（按年内第几天比较，跨年回绕）：
 *   取生效日期 <= 今天 的最后一个方案；若今天早于所有方案的生效日期，则回绕取日期最大的那个。
 *   例：A 生效 5/1、B 生效 10/1 → 5/1~9/30 用 A，10/1~次年 4/30 用 B。
 */
data class TimeRoutine(
    val id: Long = 0L,
    val name: String = "作息方案",
    val effectiveMonth: Int = 1,          // 1..12
    val effectiveDay: Int = 1,            // 1..31

    /** 以下为「时间数据」，与 TimeConfig 顶层同名字段一一对应 */
    val quickTimeEnabled: Boolean = false,
    val classDuration: Int = 45,
    val shortBreak: Int = 10,

    val longBreakEnabled: Boolean = false,
    val longBreakMorning: Int = 20,
    val longBreakAfternoon: Int = 20,
    val longBreakEvening: Int = 20,
    val longBreakMorningSection: Int = 2,
    val longBreakAfternoonSection: Int = 2,
    val longBreakEveningSection: Int = 2,

    val morningStartHour: Int = 8,
    val morningStartMinute: Int = 0,
    val afternoonStartHour: Int = 14,
    val afternoonStartMinute: Int = 0,
    val eveningStartHour: Int = 18,
    val eveningStartMinute: Int = 30,

    val sectionTimes: Map<String, String> = emptyMap(),
    val sectionNames: Map<String, String> = emptyMap(),
    val specialBlocks: List<SpecialBlock>? = null
) {
    /** 年内序数值，用于比较生效先后：month*100 + day */
    val effectiveOrdinal: Int
        get() = effectiveMonth.coerceIn(1, 12) * 100 + effectiveDay.coerceIn(1, 31)

    val safeSpecialBlocks: List<SpecialBlock>
        get() = (specialBlocks as List<*>?).orEmpty().mapNotNull { SpecialBlock.fromRaw(it) }

    /** 生效日期的可读文案，如 "5月1日起" */
    val effectiveLabel: String
        get() = "${effectiveMonth.coerceIn(1, 12)}月${effectiveDay.coerceIn(1, 31)}日起"

    companion object {
        /**
         * 某年闰年月的天数（month 越界按 12 月算）。
         *
         * 生效日期是「几月几号」，不是「一年中的第几天」，所以 2 月 29 日在平年等于
         * 永远不触发。日期选择器与导入都要按这个夹一次，否则会静默存下一个永不生效的作息。
         */
        fun daysInMonth(month: Int, leapYear: Boolean = true): Int = when (month.coerceIn(1, 12)) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            else -> if (leapYear) 29 else 28
        }

        /** 把「几月几日」夹进该月真实存在的范围，如 2 月 31 日 → 2 月 28 日 */
        fun clampDayOfMonth(month: Int, day: Int, leapYear: Boolean = true): Int =
            day.coerceIn(1, daysInMonth(month, leapYear))

        /** Gson 泛型丢失后 sectionTimes/sectionNames 可能是 Map<*,*>，逐项强转后重建 */
        private fun stringMap(raw: Any?): Map<String, String> {
            val map = raw as? Map<*, *> ?: return emptyMap()
            val out = linkedMapOf<String, String>()
            for ((k, v) in map) {
                val key = k?.toString() ?: continue
                val value = v?.toString() ?: continue
                out[key] = value
            }
            return out
        }

        // USELESS_ELVIS：Gson 反序列化后非空字段仍可能是 null
        @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
        fun fromRaw(raw: Any?, fallbackId: Long = 0L): TimeRoutine? = when (raw) {
            is TimeRoutine -> TimeRoutine(
                id = raw.id,
                name = raw.name ?: "作息方案",
                effectiveMonth = raw.effectiveMonth.takeIf { it in 1..12 } ?: 1,
                effectiveDay = clampDayOfMonth(
                    raw.effectiveMonth.takeIf { it in 1..12 } ?: 1,
                    raw.effectiveDay.takeIf { it in 1..31 } ?: 1
                ),
                quickTimeEnabled = raw.quickTimeEnabled,
                classDuration = if (raw.classDuration > 0) raw.classDuration else 45,
                shortBreak = if (raw.shortBreak >= 0) raw.shortBreak else 10,
                longBreakEnabled = raw.longBreakEnabled,
                longBreakMorning = raw.longBreakMorning,
                longBreakAfternoon = raw.longBreakAfternoon,
                longBreakEvening = raw.longBreakEvening,
                longBreakMorningSection = raw.longBreakMorningSection,
                longBreakAfternoonSection = raw.longBreakAfternoonSection,
                longBreakEveningSection = raw.longBreakEveningSection,
                morningStartHour = raw.morningStartHour,
                morningStartMinute = raw.morningStartMinute,
                afternoonStartHour = raw.afternoonStartHour,
                afternoonStartMinute = raw.afternoonStartMinute,
                eveningStartHour = raw.eveningStartHour,
                eveningStartMinute = raw.eveningStartMinute,
                sectionTimes = raw.sectionTimes ?: emptyMap(),
                sectionNames = raw.sectionNames ?: emptyMap(),
                // 保留 null 与空列表的区别：null = 该作息从没写过特殊课程（老数据），
                // 空列表 = 用户清空过。抹平会让「清空」在切作息后被顶层镜像复活。
                specialBlocks = raw.specialBlocks?.let { list ->
                    (list as List<*>).mapNotNull { SpecialBlock.fromRaw(it) }
                }
            )

            is Map<*, *> -> {
                val sectionTimes = stringMap(raw["sectionTimes"])
                val sectionNames = stringMap(raw["sectionNames"])
                val blocks = (raw["specialBlocks"] as? List<*>)?.mapNotNull { SpecialBlock.fromRaw(it) }
                TimeRoutine(
                    id = (raw["id"] as? Number)?.toLong() ?: fallbackId,
                    name = raw["name"] as? String ?: "作息方案",
                    effectiveMonth = (raw["effectiveMonth"] as? Number)?.toInt()?.takeIf { it in 1..12 } ?: 1,
                    effectiveDay = clampDayOfMonth(
                        (raw["effectiveMonth"] as? Number)?.toInt()?.takeIf { it in 1..12 } ?: 1,
                        (raw["effectiveDay"] as? Number)?.toInt()?.takeIf { it in 1..31 } ?: 1
                    ),
                    quickTimeEnabled = raw["quickTimeEnabled"] as? Boolean ?: false,
                    classDuration = (raw["classDuration"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 45,
                    shortBreak = (raw["shortBreak"] as? Number)?.toInt()?.takeIf { it >= 0 } ?: 10,
                    longBreakEnabled = raw["longBreakEnabled"] as? Boolean ?: false,
                    longBreakMorning = (raw["longBreakMorning"] as? Number)?.toInt() ?: 20,
                    longBreakAfternoon = (raw["longBreakAfternoon"] as? Number)?.toInt() ?: 20,
                    longBreakEvening = (raw["longBreakEvening"] as? Number)?.toInt() ?: 20,
                    longBreakMorningSection = (raw["longBreakMorningSection"] as? Number)?.toInt() ?: 2,
                    longBreakAfternoonSection = (raw["longBreakAfternoonSection"] as? Number)?.toInt() ?: 2,
                    longBreakEveningSection = (raw["longBreakEveningSection"] as? Number)?.toInt() ?: 2,
                    morningStartHour = (raw["morningStartHour"] as? Number)?.toInt() ?: 8,
                    morningStartMinute = (raw["morningStartMinute"] as? Number)?.toInt() ?: 0,
                    afternoonStartHour = (raw["afternoonStartHour"] as? Number)?.toInt() ?: 14,
                    afternoonStartMinute = (raw["afternoonStartMinute"] as? Number)?.toInt() ?: 0,
                    eveningStartHour = (raw["eveningStartHour"] as? Number)?.toInt() ?: 18,
                    eveningStartMinute = (raw["eveningStartMinute"] as? Number)?.toInt() ?: 30,
                    sectionTimes = sectionTimes,
                    sectionNames = sectionNames,
                    specialBlocks = blocks
                )
            }

            else -> null
        }
    }
}

/** 特殊课程内部按星期划分的子块；同一 SpecialBlock 内各子块星期区间互不重叠 */
data class SpecialItem(
    val id: Long = 0L,
    val name: String = "",
    val startDay: Int = 1,       // 1..7
    val endDay: Int = 1          // >= startDay
) {
    companion object {
        /**
         * 兜底还原：Gson 泛型丢失会把元素解析成 Map；UnsafeAllocator 使默认值不生效，
         * 即使是 SpecialItem 实例也可能字段为 null，故统一重建。
         */
        // USELESS_ELVIS：以下 ?: 编译期看似走左值，但 Gson 反序列化后字段可能是 null
        @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
        fun fromRaw(raw: Any?): SpecialItem? = when (raw) {
            is SpecialItem -> SpecialItem(
                id = raw.id,
                name = raw.name ?: "",
                startDay = if (raw.startDay in 1..7) raw.startDay else 1,
                endDay = if (raw.endDay in 1..7) raw.endDay else 1
            )

            is Map<*, *> -> SpecialItem(
                id = (raw["id"] as? Number)?.toLong() ?: 0L,
                name = raw["name"] as? String ?: "",
                startDay = (raw["startDay"] as? Number)?.toInt()?.takeIf { it in 1..7 } ?: 1,
                endDay = (raw["endDay"] as? Number)?.toInt()?.takeIf { it in 1..7 } ?: 1
            )

            else -> null
        }
    }
}

/** 无编号特殊时段（早读/大课间等），按起止时间定位并挤出让出空间，不计入课程提醒 */
data class SpecialBlock(
    val id: Long = 0L,
    val name: String = "",
    val startTime: String = "08:00",
    val endTime: String = "08:40",
    // 旧 JSON 缺失该字段时 Gson 置 null（默认值不生效），故可空并走 safeItems
    val items: List<SpecialItem>? = null
) {
    /**
     * 先擦成 List<*> 再逐元素还原。**不要**写 filterIsInstance<SpecialItem>：
     * 接收者已是 List<SpecialItem>，R8 可能把过滤当恒等变换消除，坏元素会漏进 UI。
     */
    val safeItems: List<SpecialItem>
        get() = (items as List<*>?).orEmpty().mapNotNull { SpecialItem.fromRaw(it) }

    companion object {
        /**
         * 兜底还原：UnsafeAllocator 使缺失字段为 null，原样返回会在非空 String 参数处 NPE。
         */
        // USELESS_ELVIS：以下 ?: 编译期看似走左值，但 Gson 反序列化后字段可能是 null
        @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
        fun fromRaw(raw: Any?): SpecialBlock? = when (raw) {
            is SpecialBlock -> SpecialBlock(
                id = raw.id,
                name = raw.name ?: "",
                startTime = raw.startTime ?: "08:00",
                endTime = raw.endTime ?: "08:40",
                items = (raw.items as List<*>?).orEmpty().mapNotNull { SpecialItem.fromRaw(it) }
            )

            is Map<*, *> -> SpecialBlock(
                id = (raw["id"] as? Number)?.toLong() ?: 0L,
                name = raw["name"] as? String ?: "",
                startTime = raw["startTime"] as? String ?: "08:00",
                endTime = raw["endTime"] as? String ?: "08:40",
                items = (raw["items"] as? List<*>)?.mapNotNull { SpecialItem.fromRaw(it) }
            )

            else -> null
        }
    }
}

data class TimeConfig(
    val id: Long = 0L,
    val name: String = "默认配置",

    val morningSections: Int = 4,
    val afternoonSections: Int = 4,
    val eveningSections: Int = 4,

    val quickTimeEnabled: Boolean = false,
    val classDuration: Int = 45,
    val shortBreak: Int = 10,

    val longBreakEnabled: Boolean = false,
    val longBreakMorning: Int = 20,
    val longBreakAfternoon: Int = 20,
    val longBreakEvening: Int = 20,
    val longBreakMorningSection: Int = 2,
    val longBreakAfternoonSection: Int = 2,
    val longBreakEveningSection: Int = 2,

    val morningStartHour: Int = 8,
    val morningStartMinute: Int = 0,
    val afternoonStartHour: Int = 14,
    val afternoonStartMinute: Int = 0,
    val eveningStartHour: Int = 18,
    val eveningStartMinute: Int = 30,

    // key 必须是 String：Gson 会把 Int key 序列化成 String，Map<Int,_> 反序列化会丢
    val sectionTimes: Map<String, String> = emptyMap(), // "morning_1" -> "HH:mm-HH:mm"

    val sectionNames: Map<String, String> = emptyMap(), // key 同 sectionTimes

    val specialBlocks: List<SpecialBlock> = emptyList(),

    /**
     * 作息方案列表。为空表示「单作息」旧数据，时间直接取顶层字段。
     * 节次骨架（morningSections 等）始终在顶层，作息方案只承载时间相关字段。
     */
    val routines: List<TimeRoutine> = emptyList()
) {

    /** 同 SpecialBlock.safeItems：先擦除类型再逐元素还原，兜住泛型丢失成 Map 的情况 */
    val safeSpecialBlocks: List<SpecialBlock>
        get() = (specialBlocks as List<*>?).orEmpty().mapNotNull { SpecialBlock.fromRaw(it) }

    val safeRoutines: List<TimeRoutine>
        get() = (routines as List<*>?).orEmpty().mapNotNull { TimeRoutine.fromRaw(it) }

    /** 当前日期下生效的作息方案；无方案时返回 null（此时取顶层时间字段） */
    fun routineFor(date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): TimeRoutine? {
        val list = safeRoutines
        if (list.isEmpty()) return null
        val today = date.monthNumber * 100 + date.dayOfMonth
        val sorted = list.sortedBy { it.effectiveOrdinal }
        // 今天早于全部生效日期时回绕到日期最大的方案（跨年循环）
        var chosen = sorted.last()
        for (r in sorted) {
            if (r.effectiveOrdinal <= today) chosen = r
        }
        return chosen
    }

    /** 按 id 取作息方案 */
    fun routineById(routineId: Long): TimeRoutine? =
        safeRoutines.firstOrNull { it.id == routineId }

    /**
     * 从「已叠加某作息」的这份配置里反推出该作息的 TimeRoutine。
     * 二级页面拿到的是 effectiveFor(routineId) 的结果（顶层字段已被该作息覆盖）。
     *
     * 名称/生效日期以 [nameOverride] / 已有作息为准 —— 二级页把用户输入的作息名
     * 写在了顶层 `name` 上，这里必须显式采用，否则改名不落库。
     */
    fun routineOf(routineId: Long, nameOverride: String? = null): TimeRoutine {
        val existing = safeRoutines.firstOrNull { it.id == routineId }
        return TimeRoutine(
            id = routineId,
            name = nameOverride?.takeIf { it.isNotBlank() }
                ?: existing?.name
                ?: DEFAULT_ROUTINE_NAME,
            effectiveMonth = existing?.effectiveMonth ?: 1,
            effectiveDay = existing?.effectiveDay ?: 1,
            quickTimeEnabled = quickTimeEnabled,
            classDuration = classDuration,
            shortBreak = shortBreak,
            longBreakEnabled = longBreakEnabled,
            longBreakMorning = longBreakMorning,
            longBreakAfternoon = longBreakAfternoon,
            longBreakEvening = longBreakEvening,
            longBreakMorningSection = longBreakMorningSection,
            longBreakAfternoonSection = longBreakAfternoonSection,
            longBreakEveningSection = longBreakEveningSection,
            morningStartHour = morningStartHour,
            morningStartMinute = morningStartMinute,
            afternoonStartHour = afternoonStartHour,
            afternoonStartMinute = afternoonStartMinute,
            eveningStartHour = eveningStartHour,
            eveningStartMinute = eveningStartMinute,
            sectionTimes = sectionTimes,
            sectionNames = sectionNames,
            specialBlocks = safeSpecialBlocks
        )
    }

    /**
     * 叠加当前生效作息的时间数据后返回新配置。
     * 无作息方案时原样返回，旧数据完全兼容。
     */
    fun effective(date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): TimeConfig {
        val r = routineFor(date) ?: return this
        return applyRoutine(r)
    }

    /** 指定作息方案（而非按日期自动选）；routineId 为 null 时等同 [effective] */
    fun effectiveFor(routineId: Long?): TimeConfig {
        val r = routineId?.let { routineById(it) } ?: return effective()
        return applyRoutine(r)
    }

    private fun applyRoutine(r: TimeRoutine): TimeConfig {
        return copy(
            quickTimeEnabled = r.quickTimeEnabled,
            classDuration = r.classDuration,
            shortBreak = r.shortBreak,
            longBreakEnabled = r.longBreakEnabled,
            longBreakMorning = r.longBreakMorning,
            longBreakAfternoon = r.longBreakAfternoon,
            longBreakEvening = r.longBreakEvening,
            longBreakMorningSection = r.longBreakMorningSection,
            longBreakAfternoonSection = r.longBreakAfternoonSection,
            longBreakEveningSection = r.longBreakEveningSection,
            morningStartHour = r.morningStartHour,
            morningStartMinute = r.morningStartMinute,
            afternoonStartHour = r.afternoonStartHour,
            afternoonStartMinute = r.afternoonStartMinute,
            eveningStartHour = r.eveningStartHour,
            eveningStartMinute = r.eveningStartMinute,
            sectionTimes = r.sectionTimes.takeIf { it.isNotEmpty() } ?: sectionTimes,
            sectionNames = r.sectionNames.takeIf { it.isNotEmpty() } ?: sectionNames,
            // 特殊课程不能用「空集合=未配置」的回退口径：**空列表就是「一条都没有」的合法状态**。
            // 早先写成 blocks.takeIf{isNotEmpty} ?: safeSpecialBlocks，于是「删光特殊课程」
            // 只改到该作息、切过去又被顶层镜像（旧值）填回来，删不掉。
            // 只有该作息压根没有这个字段（老数据）才回退顶层。
            specialBlocks = if (r.specialBlocks != null) r.safeSpecialBlocks else safeSpecialBlocks
        )
    }

    /** 指定日期生效的作息方案 id；无方案返回 null */
    fun effectiveRoutineId(date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): Long? = routineFor(date)?.id

    /**
     * 把 source 的**作息时间数据**写回指定作息方案。
     *
     * 刻意不写节次骨架（morningSections 等）：骨架由时间配置页统一管理，
     * 属于所有作息共享的数据。若这里跟着 source 写回顶层，
     * 编辑作息 B 会用「B 的视角」覆盖骨架，改一个作息就污染另一个。
     *
     * 顶层镜像仍然更新（备份/分享/旧版链路读顶层字段），镜像值取该作息自己的数据。
     */
    fun withRoutineTimesApplied(routineId: Long, source: TimeConfig): TimeConfig {
        val list = safeRoutines.toMutableList()
        val idx = list.indexOfFirst { it.id == routineId }
        if (idx < 0) return copy(
            quickTimeEnabled = source.quickTimeEnabled,
            classDuration = source.classDuration,
            shortBreak = source.shortBreak,
            longBreakEnabled = source.longBreakEnabled,
            longBreakMorning = source.longBreakMorning,
            longBreakAfternoon = source.longBreakAfternoon,
            longBreakEvening = source.longBreakEvening,
            longBreakMorningSection = source.longBreakMorningSection,
            longBreakAfternoonSection = source.longBreakAfternoonSection,
            longBreakEveningSection = source.longBreakEveningSection,
            morningStartHour = source.morningStartHour,
            morningStartMinute = source.morningStartMinute,
            afternoonStartHour = source.afternoonStartHour,
            afternoonStartMinute = source.afternoonStartMinute,
            eveningStartHour = source.eveningStartHour,
            eveningStartMinute = source.eveningStartMinute,
            sectionTimes = source.sectionTimes,
            sectionNames = source.sectionNames,
            specialBlocks = source.safeSpecialBlocks
        )

        val updated = list[idx].copy(
            quickTimeEnabled = source.quickTimeEnabled,
            classDuration = source.classDuration,
            shortBreak = source.shortBreak,
            longBreakEnabled = source.longBreakEnabled,
            longBreakMorning = source.longBreakMorning,
            longBreakAfternoon = source.longBreakAfternoon,
            longBreakEvening = source.longBreakEvening,
            longBreakMorningSection = source.longBreakMorningSection,
            longBreakAfternoonSection = source.longBreakAfternoonSection,
            longBreakEveningSection = source.longBreakEveningSection,
            morningStartHour = source.morningStartHour,
            morningStartMinute = source.morningStartMinute,
            afternoonStartHour = source.afternoonStartHour,
            afternoonStartMinute = source.afternoonStartMinute,
            eveningStartHour = source.eveningStartHour,
            eveningStartMinute = source.eveningStartMinute,
            sectionTimes = source.sectionTimes,
            sectionNames = source.sectionNames,
            specialBlocks = source.safeSpecialBlocks
        )
        list[idx] = updated
        return copy(
            quickTimeEnabled = updated.quickTimeEnabled,
            classDuration = updated.classDuration,
            shortBreak = updated.shortBreak,
            longBreakEnabled = updated.longBreakEnabled,
            longBreakMorning = updated.longBreakMorning,
            longBreakAfternoon = updated.longBreakAfternoon,
            longBreakEvening = updated.longBreakEvening,
            longBreakMorningSection = updated.longBreakMorningSection,
            longBreakAfternoonSection = updated.longBreakAfternoonSection,
            longBreakEveningSection = updated.longBreakEveningSection,
            morningStartHour = updated.morningStartHour,
            morningStartMinute = updated.morningStartMinute,
            afternoonStartHour = updated.afternoonStartHour,
            afternoonStartMinute = updated.afternoonStartMinute,
            eveningStartHour = updated.eveningStartHour,
            eveningStartMinute = updated.eveningStartMinute,
            sectionTimes = updated.sectionTimes,
            sectionNames = updated.sectionNames,
            specialBlocks = updated.safeSpecialBlocks,
            routines = list
        )
    }

    /**
     * 无作息方案时，用当前顶层时间播种一个「默认作息」（1月1日起生效）。
     * 旧数据升级与新建配置都走这里，保证多作息编辑始终有落点。
     */
    fun ensureRoutine(): TimeConfig {
        if (safeRoutines.isNotEmpty()) return this
        return copy(
            routines = listOf(
                TimeRoutine(
                    id = 1L,
                    name = DEFAULT_ROUTINE_NAME,
                    effectiveMonth = 1,
                    effectiveDay = 1,
                    quickTimeEnabled = quickTimeEnabled,
                    classDuration = classDuration,
                    shortBreak = shortBreak,
                    longBreakEnabled = longBreakEnabled,
                    longBreakMorning = longBreakMorning,
                    longBreakAfternoon = longBreakAfternoon,
                    longBreakEvening = longBreakEvening,
                    longBreakMorningSection = longBreakMorningSection,
                    longBreakAfternoonSection = longBreakAfternoonSection,
                    longBreakEveningSection = longBreakEveningSection,
                    morningStartHour = morningStartHour,
                    morningStartMinute = morningStartMinute,
                    afternoonStartHour = afternoonStartHour,
                    afternoonStartMinute = afternoonStartMinute,
                    eveningStartHour = eveningStartHour,
                    eveningStartMinute = eveningStartMinute,
                    sectionTimes = sectionTimes,
                    sectionNames = sectionNames,
                    specialBlocks = safeSpecialBlocks
                )
            )
        )
    }

    fun nextRoutineId(): Long = (safeRoutines.maxOfOrNull { it.id } ?: 0L) + 1L

    /** 生效日期是否已被占用（同一年的同一天只能有一套作息，否则切换结果取决于列表顺序） */
    fun isRoutineDateTaken(month: Int, day: Int, excludeId: Long = 0L): Boolean {
        val m = month.coerceIn(1, 12)
        val ordinal = m * 100 + TimeRoutine.clampDayOfMonth(m, day)
        return safeRoutines.any { it.id != excludeId && it.effectiveOrdinal == ordinal }
    }

    /** 以当前生效作息的时间为蓝本新增一个作息方案，返回 (新配置, 新方案 id) */
    fun withRoutineAdded(name: String, month: Int, day: Int): Pair<TimeConfig, Long> {
        val newId = nextRoutineId()
        val seed = effective()
        val safeMonth = month.coerceIn(1, 12)
        val routine = TimeRoutine(
            id = newId,
            name = name,
            effectiveMonth = safeMonth,
            effectiveDay = TimeRoutine.clampDayOfMonth(safeMonth, day),
            quickTimeEnabled = seed.quickTimeEnabled,
            classDuration = seed.classDuration,
            shortBreak = seed.shortBreak,
            longBreakEnabled = seed.longBreakEnabled,
            longBreakMorning = seed.longBreakMorning,
            longBreakAfternoon = seed.longBreakAfternoon,
            longBreakEvening = seed.longBreakEvening,
            longBreakMorningSection = seed.longBreakMorningSection,
            longBreakAfternoonSection = seed.longBreakAfternoonSection,
            longBreakEveningSection = seed.longBreakEveningSection,
            morningStartHour = seed.morningStartHour,
            morningStartMinute = seed.morningStartMinute,
            afternoonStartHour = seed.afternoonStartHour,
            afternoonStartMinute = seed.afternoonStartMinute,
            eveningStartHour = seed.eveningStartHour,
            eveningStartMinute = seed.eveningStartMinute,
            sectionTimes = seed.sectionTimes,
            sectionNames = seed.sectionNames,
            specialBlocks = seed.safeSpecialBlocks
        )
        return copy(routines = safeRoutines + routine) to newId
    }

    /**
     * 删除作息方案；至少保留一个，删到最后一个时原样返回。
     *
     * 删完要把顶层镜像换成「删除后生效的那套」（[effective] 顺带把 routines 保留下来）。
     * 否则顶层仍是被删掉的那一套的时间 —— 备份 / 分享 / 旧链路只读顶层，会导出已经不存在的时间。
     */
    fun withRoutineRemoved(id: Long): TimeConfig {
        val list = safeRoutines.toMutableList()
        if (list.size <= 1) return this
        if (list.none { it.id == id }) return this
        return copy(routines = list.filter { it.id != id }).effective()
    }

    /** 改作息方案的名称 / 生效日期，不传的字段保持原值 */
    fun withRoutineMeta(
        id: Long,
        name: String? = null,
        month: Int? = null,
        day: Int? = null
    ): TimeConfig {
        val list = safeRoutines.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return this
        val r = list[idx]
        val newMonth = month?.coerceIn(1, 12) ?: r.effectiveMonth
        list[idx] = r.copy(
            name = name ?: r.name,
            effectiveMonth = newMonth,
            // 月份也可能同时被改（如 1/31 → 2/31），所以按新月份夹一次
            effectiveDay = if (day != null) TimeRoutine.clampDayOfMonth(newMonth, day) else r.effectiveDay
        )
        return copy(routines = list)
    }

    /**
     * 用 [edited] 整体替换指定作息，并同步镜像到顶层时间字段。
     * 二级页面保存「一个作息」时走这里：只动作息，不碰节次骨架。
     * 若 [edited] 恰好是当前生效作息，顶层镜像才有意义——
     * 否则顶层会变成"未来作息"的时间，课表立刻显示错。
     */
    fun withRoutineReplaced(id: Long, edited: TimeRoutine): TimeConfig {
        val list = safeRoutines.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return this
        val stored = edited.copy(id = id)
        list[idx] = stored
        val base = copy(routines = list)
        // 用 base（已含本次保存的新生效日期）判断，不能用 this：
        // 二级页把生效日期往前改时，"这一个"此刻才刚变成生效作息，看旧值会漏掉镜像
        val isActive = base.effectiveRoutineId() == id
        return if (isActive) base.copy(
            quickTimeEnabled = stored.quickTimeEnabled,
            classDuration = stored.classDuration,
            shortBreak = stored.shortBreak,
            longBreakEnabled = stored.longBreakEnabled,
            longBreakMorning = stored.longBreakMorning,
            longBreakAfternoon = stored.longBreakAfternoon,
            longBreakEvening = stored.longBreakEvening,
            longBreakMorningSection = stored.longBreakMorningSection,
            longBreakAfternoonSection = stored.longBreakAfternoonSection,
            longBreakEveningSection = stored.longBreakEveningSection,
            morningStartHour = stored.morningStartHour,
            morningStartMinute = stored.morningStartMinute,
            afternoonStartHour = stored.afternoonStartHour,
            afternoonStartMinute = stored.afternoonStartMinute,
            eveningStartHour = stored.eveningStartHour,
            eveningStartMinute = stored.eveningStartMinute,
            sectionTimes = stored.sectionTimes,
            sectionNames = stored.sectionNames,
            specialBlocks = stored.safeSpecialBlocks
        ) else base
    }

    /**
     * period: "morning" / "afternoon" / "evening"
     * 返回时段内相对节次号 (1-6) -> "HH:mm-HH:mm"
     */
    fun getPeriodTimes(period: String): Map<Int, String> {
        if (quickTimeEnabled) {
            return calculatePeriodTimes(period)
        }

        val result = mutableMapOf<Int, String>()
        for ((k, v) in sectionTimes) {
            if (k.startsWith("${period}_")) {
                val idx = k.removePrefix("${period}_").toIntOrNull()
                if (idx != null) result[idx] = v
            }
        }
        return result.ifEmpty { getDefaultTimesForPeriod(period) }
    }

    fun calculateSectionTimes(): Map<Int, String> {
        val morningTimes = calculatePeriodTimes("morning")
        val afternoonTimes = calculatePeriodTimes("afternoon")
        val eveningTimes = calculatePeriodTimes("evening")

        val result = mutableMapOf<Int, String>()
        morningTimes.forEach { (k, v) -> result[k] = v }
        afternoonTimes.forEach { (k, v) -> result[morningSections + k] = v }
        eveningTimes.forEach { (k, v) -> result[morningSections + afternoonSections + k] = v }
        return result
    }

    private fun calculatePeriodTimes(period: String): Map<Int, String> {
        val sectionCount = when (period) {
            "morning" -> morningSections
            "afternoon" -> afternoonSections
            "evening" -> eveningSections
            else -> return emptyMap()
        }
        if (sectionCount <= 0) return emptyMap()

        val (startHour, startMinute) = when (period) {
            "morning" -> morningStartHour to morningStartMinute
            "afternoon" -> afternoonStartHour to afternoonStartMinute
            "evening" -> eveningStartHour to eveningStartMinute
            else -> return emptyMap()
        }

        val longBreak = when (period) {
            "morning" -> longBreakMorning
            "afternoon" -> longBreakAfternoon
            "evening" -> longBreakEvening
            else -> 0
        }
        val longBreakSection = when (period) {
            "morning" -> longBreakMorningSection
            "afternoon" -> longBreakAfternoonSection
            "evening" -> longBreakEveningSection
            else -> 2
        }

        return Course.calculatePeriodTimes(
            sectionCount = sectionCount,
            startHour = startHour,
            startMinute = startMinute,
            classDuration = classDuration,
            shortBreak = shortBreak,
            longBreak = if (longBreakEnabled) longBreak else shortBreak,
            longBreakSection = longBreakSection
        )
    }

    companion object {
        // 注：parseSnapshotOrNull 依赖 Gson，已留在 Android 侧的 TimeConfigSnapshotParser.kt，
        // 等阶段 2 换成 kotlinx.serialization 后再移回 commonMain。

        /**
         * 长期数据兜底：UnsafeAllocator 使非空字段可能为 null；三段节数全 0 视为损坏恢复 4/4/4；
         * 单段节数夹到 0..6（0 表示该时段无课）。
         */
        // USELESS_ELVIS：Gson 反序列化后非空字段仍可能是 null
        @Suppress("SENSELESS_COMPARISON", "ELVIS_ALWAYS_NULL", "USELESS_ELVIS")
        fun sanitize(id: Long, raw: TimeConfig): TimeConfig {
            val total = raw.morningSections + raw.afternoonSections + raw.eveningSections
            val (morning, afternoon, evening) = if (total <= 0) {
                Triple(DEFAULT_MORNING_SECTIONS, DEFAULT_AFTERNOON_SECTIONS, DEFAULT_EVENING_SECTIONS)
            } else {
                Triple(
                    raw.morningSections.coerceIn(0, 6),
                    raw.afternoonSections.coerceIn(0, 6),
                    raw.eveningSections.coerceIn(0, 6)
                )
            }
            val base = raw.copy(
                id = id,
                name = raw.name ?: "默认配置",
                morningSections = morning,
                afternoonSections = afternoon,
                eveningSections = evening,
                classDuration = if (raw.classDuration > 0) raw.classDuration else 45,
                shortBreak = if (raw.shortBreak >= 0) raw.shortBreak else 10,
                sectionTimes = raw.sectionTimes ?: emptyMap(),
                sectionNames = raw.sectionNames ?: emptyMap(),
                specialBlocks = raw.safeSpecialBlocks
            )
            // 旧数据（无作息方案）就地播种一个「默认作息」，承载现有时间，之后多作息编辑才有落点
            return base.ensureRoutine().let { seeded -> seeded.copy(routines = seeded.safeRoutines) }
        }

        const val DEFAULT_MORNING_SECTIONS = 4
        const val DEFAULT_AFTERNOON_SECTIONS = 4
        const val DEFAULT_EVENING_SECTIONS = 4
        const val DEFAULT_ROUTINE_NAME = "默认作息"

        private fun getDefaultTimesForPeriod(period: String): Map<Int, String> = when (period) {
            "morning" -> Course.defaultMorningTimes
            "afternoon" -> Course.defaultAfternoonTimes
            "evening" -> Course.defaultEveningTimes
            else -> emptyMap()
        }

    }
}
