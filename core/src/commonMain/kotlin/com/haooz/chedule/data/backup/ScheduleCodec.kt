package com.haooz.chedule.data.backup

import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.SpecialBlock
import com.haooz.chedule.data.model.SpecialItem
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.model.TimeRoutine
import com.haooz.chedule.data.model.truncateNote
import com.haooz.chedule.data.storage.ScheduleFolder
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseJsonElement
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.data.util.toJsonElement
import kotlin.collections.get
import kotlin.collections.iterator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// 课表数据模型 ↔ JSON 的显式字段清单编解码（手写清单，因 kotlinx-serialization 编译器插件本机不可用）。
//
//  兼容性判据是「字段集合 + 字段名 + 值的语义」，不是逐字相等：JSON 对象按规范无序，两端按名字取值。
//  与 Gson 的对齐点：null 字段省略、默认值照写、Long 原样输出、`<>&='` 不转义；缺字段导致的 null 由调用方 sanitize* 兜底。

/**
 * 落盘/备份用的 JSON 实例。
 *
 * ⚠ 不要拿 `JsonSupport` 里那个内部实例来序列化模型 —— 那个是给动态 JsonElement 解析用的，
 * 语义取向不同（它对 null 会写 `JsonNull`）。
 */
internal val scheduleJson: Json = Json {
    // Gson 会写默认值（真实备份里 `isCustomTime:false` / `selectedWeeks:[]` 都在）
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 把「值可能是 null」的 Map 转成 JSON，递归跳过对象里的 null 值（对应 Gson `serializeNulls = false`）。
 *
 * ⚠ 必须递归：`specialBlocks` / `routines` 是嵌套对象，只跳顶层的话内层 `"items":null` 照样写出。
 * ⚠ 数组元素里的 null 要保留：`serializeNulls` 只影响对象字段，集合元素照写 null。
 */
private fun plainValueToJsonWithoutNulls(value: Any?): JsonElement? = when (value) {
    null -> null
    is Map<*, *> -> JsonObject(
        value.entries.mapNotNull { (key, item) ->
            plainValueToJsonWithoutNulls(item)?.let { key.toString() to it }
        }.toMap(LinkedHashMap())
    )
    is Iterable<*> -> JsonArray(
        value.map { plainValueToJsonWithoutNulls(it) ?: JsonNull }
    )
    else -> toJsonElement(value)
}

/** 见 [plainValueToJsonWithoutNulls]。顶层必须是对象（模型序列化都是对象）。 */
fun toJsonElementWithoutNulls(map: Map<String, Any?>): JsonElement =
    plainValueToJsonWithoutNulls(map) ?: JsonNull

// ── Map 读取小工具（输入形状 = Gson 的 Map<String,Any>：数字是 Double）──

private fun Map<*, *>.raw(key: String): Any? = this[key]

private fun Map<*, *>.str(key: String): String? = raw(key) as? String

private fun Map<*, *>.int(key: String, fallback: Int = 0): Int =
    (raw(key) as? Number)?.toInt() ?: fallback

private fun Map<*, *>.long(key: String, fallback: Long = 0L): Long =
    (raw(key) as? Number)?.toLong() ?: fallback

private fun Map<*, *>.bool(key: String, fallback: Boolean = false): Boolean =
    raw(key) as? Boolean ?: fallback

// ════════════════════════════════════════════════════════════════════════
//  Course
// ════════════════════════════════════════════════════════════════════════

/** `Course` → JSON Map（字段顺序 = 声明顺序，不是契约；null 字段由 [toJsonElementWithoutNulls] 跳过）。 */
fun Course.toJsonMap(): Map<String, Any?> = buildMap {
    put("id", id)
    put("name", name)
    put("classroom", classroom)
    put("teacher", teacher)
    put("dayOfWeek", dayOfWeek)
    put("startSection", startSection)
    put("endSection", endSection)
    put("startWeek", startWeek)
    put("endWeek", endWeek)
    put("weekType", weekType)
    put("colorRes", colorRes)
    put("selectedWeeks", selectedWeeks)
    put("scheduleId", scheduleId)
    put("lastModified", lastModified)
    put("isCustomTime", isCustomTime)
    put("customStartTime", customStartTime)
    put("customEndTime", customEndTime)
    // 备注是后加的字段：没有备注就不写，存量课程的 JSON 保持逐字不变
    if (note.isNotEmpty()) put("note", note)
    if (weekNotes.isNotEmpty()) {
        put("weekNotes", weekNotes.mapKeys { it.key.toString() })
    }
}

/**
 * JSON Map → `Course`。
 *
 * ⚠ 缺字段取零值（0 / "" / emptyList / null），刻意不用 Kotlin 构造默认值 —— 与 Gson 的
 * `UnsafeAllocator` 路径一致（绕开构造器，非空 `String` 字段因此会变 null），由调用方 sanitize* 兜底。
 */
fun courseFromJsonMap(map: Map<*, *>): Course = Course(
    id = map.str("id") ?: "",
    name = map.str("name") ?: "",
    classroom = map.str("classroom") ?: "",
    teacher = map.str("teacher") ?: "",
    dayOfWeek = map.int("dayOfWeek"),
    startSection = map.int("startSection"),
    endSection = map.int("endSection"),
    startWeek = map.int("startWeek"),
    endWeek = map.int("endWeek"),
    weekType = map.int("weekType"),
    colorRes = map.long("colorRes"),
    selectedWeeks = (map.raw("selectedWeeks") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
        ?: emptyList(),
    scheduleId = map.str("scheduleId") ?: "",
    lastModified = map.long("lastModified"),
    isCustomTime = map.bool("isCustomTime"),
    customStartTime = map.str("customStartTime"),
    customEndTime = map.str("customEndTime"),
    note = (map.str("note") ?: "").trim().truncateNote(),
    weekNotes = map.weekNotes("weekNotes"),
)

/**
 * `weekNotes` 的 JSON 形状是 `{"3":"…"}`（键是字符串），读回时统一转成 `Map<Int, String>`。
 *
 * 周次 ≤ 0 或文本为空的条目直接丢掉：空备注不该占一个键，否则「有没有备注」的判断会失真。
 * 文本超 [Course.NOTE_MAX_LENGTH] 的按上限截断 —— 与输入框、导入解析同一口径。
 */
internal fun Map<*, *>.weekNotes(key: String): Map<Int, String> {
    val raw = raw(key) as? Map<*, *> ?: return emptyMap()
    return buildMap {
        for ((weekKey, value) in raw) {
            val week = when (weekKey) {
                is String -> weekKey.trim().toIntOrNull()
                is Number -> weekKey.toInt()
                else -> null
            } ?: continue
            val text = (value as? String)?.trim()?.truncateNote() ?: continue
            if (week > 0 && text.isNotEmpty()) put(week, text)
        }
    }
}

/** `List<Course>` → JSON 数组串。等价 `gson.toJson(courses)`。 */
fun encodeCourses(courses: List<Course>): String =
    scheduleJson.encodeToString(
        JsonElement.serializer(),
        JsonArray(courses.map { toJsonElementWithoutNulls(it.toJsonMap()) }),
    )

/**
 * JSON 数组串 → `List<Course>`。
 *
 * 空数组 / 字面量 `null` → 空列表；格式非法或顶层不是数组时抛异常（与 Gson 一致），
 * 调用点的 `try/catch` 负责兜底。
 */
fun decodeCourses(json: String): List<Course> {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val element = parseJsonElement(trimmed)
    val array = element as? JsonArray
        ?: throw IllegalArgumentException("Not a course array")
    return array.mapNotNull { item ->
        (jsonToPlainValue(item) as? Map<*, *>)?.let(::courseFromJsonMap)
    }
}

// ════════════════════════════════════════════════════════════════════════
//  ScheduleFolder
// ════════════════════════════════════════════════════════════════════════

/** `ScheduleFolder` → JSON Map。 */
fun ScheduleFolder.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "schedules" to schedules,
)

/** JSON Map → `ScheduleFolder`（缺字段取零值，与 Gson 一致）。 */
fun scheduleFolderFromJsonMap(map: Map<*, *>): ScheduleFolder = ScheduleFolder(
    id = map.str("id") ?: "",
    name = map.str("name") ?: "",
    schedules = (map.raw("schedules") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
)

/** `List<ScheduleFolder>` → JSON 数组串。等价 `gson.toJson(folders)`。 */
fun encodeScheduleFolders(folders: List<ScheduleFolder>): String =
    scheduleJson.encodeToString(
        JsonElement.serializer(),
        JsonArray(folders.map { toJsonElementWithoutNulls(it.toJsonMap()) }),
    )

/** JSON 数组串 → `List<ScheduleFolder>`。语义同 [decodeCourses]。 */
fun decodeScheduleFolders(json: String): List<ScheduleFolder> {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val array = parseJsonElement(trimmed) as? JsonArray
        ?: throw IllegalArgumentException("Not a folder array")
    return array.mapNotNull { item ->
        (jsonToPlainValue(item) as? Map<*, *>)?.let(::scheduleFolderFromJsonMap)
    }
}

// TimeConfig（含 TimeRoutine / SpecialBlock / SpecialItem）
//
//  这一族全部有默认值 → Gson 走合成无参构造器，缺字段得到 Kotlin 默认值（Course 则相反）。
//  故 [timeConfigFromJsonMap] 用 `TimeConfig()` 作底 + copy 只覆盖出现过的字段，
//  嵌套结构复用各自的 `fromRaw(Map)`（fallback 字面量恰好等于各自的 Kotlin 默认值）。

private fun Map<*, *>.stringMap(key: String): Map<String, String> {
    val raw = raw(key) as? Map<*, *> ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    for ((k, v) in raw) {
        val name = k?.toString() ?: continue
        val value = v?.toString() ?: continue
        out[name] = value
    }
    return out
}

/** `SpecialItem` → JSON Map（4 个字段）。 */
fun SpecialItem.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "startDay" to startDay,
    "endDay" to endDay,
)

/**
 * `SpecialBlock` → JSON Map。`items` 为 null 时会被 [toJsonElementWithoutNulls] 跳过，
 * 与 Gson 的 `serializeNulls=false` 一致（旧数据里 `items` 缺失就是这个状态）。
 */
fun SpecialBlock.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "startTime" to startTime,
    "endTime" to endTime,
    "items" to items?.map { it.toJsonMap() },
)

/** `TimeRoutine` → JSON Map（`specialBlocks` 为 null 时跳过）。 */
fun TimeRoutine.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "effectiveMonth" to effectiveMonth,
    "effectiveDay" to effectiveDay,
    "quickTimeEnabled" to quickTimeEnabled,
    "classDuration" to classDuration,
    "shortBreak" to shortBreak,
    "longBreakEnabled" to (longBreakMorningEnabled || longBreakAfternoonEnabled || longBreakEveningEnabled),
    "longBreakMorningEnabled" to longBreakMorningEnabled,
    "longBreakAfternoonEnabled" to longBreakAfternoonEnabled,
    "longBreakEveningEnabled" to longBreakEveningEnabled,
    "longBreakMorning" to longBreakMorning,
    "longBreakAfternoon" to longBreakAfternoon,
    "longBreakEvening" to longBreakEvening,
    "longBreakMorningSection" to longBreakMorningSection,
    "longBreakAfternoonSection" to longBreakAfternoonSection,
    "longBreakEveningSection" to longBreakEveningSection,
    "morningStartHour" to morningStartHour,
    "morningStartMinute" to morningStartMinute,
    "afternoonStartHour" to afternoonStartHour,
    "afternoonStartMinute" to afternoonStartMinute,
    "eveningStartHour" to eveningStartHour,
    "eveningStartMinute" to eveningStartMinute,
    "sectionTimes" to sectionTimes,
    "sectionNames" to sectionNames,
    "specialBlocks" to specialBlocks?.map { it.toJsonMap() },
)

/** `TimeConfig` → JSON Map。 */
fun TimeConfig.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "morningSections" to morningSections,
    "afternoonSections" to afternoonSections,
    "eveningSections" to eveningSections,
    "quickTimeEnabled" to quickTimeEnabled,
    "classDuration" to classDuration,
    "shortBreak" to shortBreak,
    "longBreakEnabled" to (longBreakMorningEnabled || longBreakAfternoonEnabled || longBreakEveningEnabled),
    "longBreakMorningEnabled" to longBreakMorningEnabled,
    "longBreakAfternoonEnabled" to longBreakAfternoonEnabled,
    "longBreakEveningEnabled" to longBreakEveningEnabled,
    "longBreakMorning" to longBreakMorning,
    "longBreakAfternoon" to longBreakAfternoon,
    "longBreakEvening" to longBreakEvening,
    "longBreakMorningSection" to longBreakMorningSection,
    "longBreakAfternoonSection" to longBreakAfternoonSection,
    "longBreakEveningSection" to longBreakEveningSection,
    "morningStartHour" to morningStartHour,
    "morningStartMinute" to morningStartMinute,
    "afternoonStartHour" to afternoonStartHour,
    "afternoonStartMinute" to afternoonStartMinute,
    "eveningStartHour" to eveningStartHour,
    "eveningStartMinute" to eveningStartMinute,
    "sectionTimes" to sectionTimes,
    "sectionNames" to sectionNames,
    "specialBlocks" to specialBlocks.map { it.toJsonMap() },
    "routines" to routines.map { it.toJsonMap() },
)

/**
 * JSON Map → `TimeConfig`。结果**必须再过一遍 [TimeConfig.sanitize]**，
 * 这里只负责「把 JSON 变成 Gson 会给的那个对象」，不做业务归一。
 */
fun timeConfigFromJsonMap(map: Map<*, *>): TimeConfig {
    // 以 TimeConfig() 作底，再只覆盖 JSON 里出现过的字段
    val base = TimeConfig()
    val legacyEnabled = map.bool("longBreakEnabled", base.longBreakEnabled)
    return base.copy(
        id = map.long("id", base.id),
        // 非空类型 + JSON 里显式 null → Gson 会塞 null；这里直接给底值，sanitize 后结果相同
        name = map.str("name") ?: base.name,
        morningSections = map.int("morningSections", base.morningSections),
        afternoonSections = map.int("afternoonSections", base.afternoonSections),
        eveningSections = map.int("eveningSections", base.eveningSections),
        quickTimeEnabled = map.bool("quickTimeEnabled", base.quickTimeEnabled),
        classDuration = map.int("classDuration", base.classDuration),
        shortBreak = map.int("shortBreak", base.shortBreak),
        longBreakEnabled = legacyEnabled,
        longBreakMorningEnabled = map.bool("longBreakMorningEnabled", legacyEnabled),
        longBreakAfternoonEnabled = map.bool("longBreakAfternoonEnabled", legacyEnabled),
        longBreakEveningEnabled = map.bool("longBreakEveningEnabled", legacyEnabled),
        longBreakMorning = map.int("longBreakMorning", base.longBreakMorning),
        longBreakAfternoon = map.int("longBreakAfternoon", base.longBreakAfternoon),
        longBreakEvening = map.int("longBreakEvening", base.longBreakEvening),
        longBreakMorningSection = map.int("longBreakMorningSection", base.longBreakMorningSection),
        longBreakAfternoonSection = map.int("longBreakAfternoonSection", base.longBreakAfternoonSection),
        longBreakEveningSection = map.int("longBreakEveningSection", base.longBreakEveningSection),
        morningStartHour = map.int("morningStartHour", base.morningStartHour),
        morningStartMinute = map.int("morningStartMinute", base.morningStartMinute),
        afternoonStartHour = map.int("afternoonStartHour", base.afternoonStartHour),
        afternoonStartMinute = map.int("afternoonStartMinute", base.afternoonStartMinute),
        eveningStartHour = map.int("eveningStartHour", base.eveningStartHour),
        eveningStartMinute = map.int("eveningStartMinute", base.eveningStartMinute),
        sectionTimes = map.stringMap("sectionTimes"),
        sectionNames = map.stringMap("sectionNames"),
        // 复用现有 fromRaw：与 safeSpecialBlocks / safeRoutines 同源，避免两套归一逻辑打架。
        // 它们的 fallback 字面量恰好等于各自的 Kotlin 默认值（已逐个核对）。
        specialBlocks = (map.raw("specialBlocks") as? List<*>)?.mapNotNull { SpecialBlock.fromRaw(it) }
            ?: emptyList(),
        routines = (map.raw("routines") as? List<*>)?.mapNotNull { TimeRoutine.fromRaw(it) }
            ?: emptyList(),
    )
}

/** `TimeConfig` → JSON 串。等价 `gson.toJson(config)`。 */
fun encodeTimeConfig(config: TimeConfig): String =
    scheduleJson.encodeToString(JsonElement.serializer(), toJsonElementWithoutNulls(config.toJsonMap()))

/**
 * JSON 串 → `TimeConfig`。顶层不是对象或格式非法时抛异常（Gson 同），调用点用 `runCatching` 兜。
 * ⚠ 调用方拿到之后必须再过 [TimeConfig.sanitize]。
 */
fun decodeTimeConfig(json: String): TimeConfig {
    val element = parseJsonElement(json)
    val map = jsonToPlainValue(element) as? Map<*, *>
        ?: throw IllegalArgumentException("Not a time config object")
    return timeConfigFromJsonMap(map)
}

/**
 * `TimeConfig` 的已知字段名。JSON 里一个都不像时判为损坏。
 */
private val TIME_CONFIG_FIELD_NAMES = setOf(
    "id", "name", "morningSections", "afternoonSections", "eveningSections",
    "quickTimeEnabled", "classDuration", "shortBreak",
    "sectionTimes", "sectionNames", "specialBlocks", "routines",
)

/**
 * `TimeConfig` 快照解析：JSON 键名一个已知字段都不像时判为损坏，返回 null。
 * 两步都吞异常返回 null（调用方按「损坏」处理）。
 */
fun parseTimeConfigSnapshotOrNull(json: String): TimeConfig? {
    val obj = runCatching { parseJsonObject(json) }.getOrNull() ?: return null
    if (obj.keys.none { it in TIME_CONFIG_FIELD_NAMES }) return null
    return runCatching { decodeTimeConfig(json) }.getOrNull()
}

// ════════════════════════════════════════════════════════════════════════
//  简单标量列表（课表名 / time_config id）
// ════════════════════════════════════════════════════════════════════════

/** `List<String>` → JSON 数组串。等价 `gson.toJson(names)`。 */
fun encodeStringList(values: List<String>): String =
    scheduleJson.encodeToString(JsonElement.serializer(), toJsonElement(values))

/** JSON 数组串 → `List<String>`；非数组或格式非法时抛异常（Gson 同样抛）。 */
fun decodeStringList(json: String): List<String> {
    val array = parseJsonElement(json) as? JsonArray
        ?: throw IllegalArgumentException("Not a string array")
    return array.mapNotNull { (jsonToPlainValue(it) as? String) }
}

/** `List<Long>` → JSON 数组串。等价 `gson.toJson(ids)`。 */
fun encodeLongList(values: List<Long>): String =
    scheduleJson.encodeToString(JsonElement.serializer(), toJsonElement(values))

/** JSON 数组串 → `List<Long>`。 */
fun decodeLongList(json: String): List<Long> {
    val array = parseJsonElement(json) as? JsonArray
        ?: throw IllegalArgumentException("Not a long array")
    return array.mapNotNull { (jsonToPlainValue(it) as? Number)?.toLong() }
}
