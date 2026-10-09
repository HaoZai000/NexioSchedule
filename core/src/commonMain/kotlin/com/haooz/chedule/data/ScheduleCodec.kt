package com.haooz.chedule.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

// ════════════════════════════════════════════════════════════════════════
//  课表数据模型 ↔ JSON 的**显式字段清单**编解码
//
//  ## 为什么要有这个文件
//
//  `CourseRepository` 目前用 **Gson 反射**序列化这些模型。Gson 靠运行时反射，
//  Kotlin/Native 上不存在，所以要把 `CourseRepository` 搬进 `:core`，必须先把
//  这几条序列化通道换成**显式字段清单**。
//
//  ## ⚠ 为什么是手写而不是 `@Serializable`
//
//  本机（离线）**没有 `kotlin-serialization` 编译器插件**（`~/.gradle/caches` 里查无此物），
//  加不上 `org.jetbrains.kotlin.plugin.serialization`。所以走**手写字段清单**，
//  这也正好符合本文档对 R2 的要求（「需 `@Serializable` + **显式字段清单**」）——
//  手写就是最直白的显式清单。
//
//  ## ⚠⚠ 字段顺序**不是**兼容契约（实测结论，别去对齐它）
//
//  真实用户备份里 `_courses` 的字段是**字母序**（`classroom, colorRes, dayOfWeek, …`），
//  而 `Course` 的声明顺序是 `id, name, classroom, …`。用真实 Gson 2.11.0 实测：
//  **Gson 按声明顺序输出，且保留 Map 插入顺序**，绝不会排字母序。
//
//  ⇒ 那个字母序是 **release 构建被 R8 重排字段**的产物，随构建而变，**不是契约**。
//  JSON 对象按规范无序，两端都按名字取值，所以字段顺序怎么排都能互相读。
//
//  ⇒ **兼容性判据 = 字段集合 + 字段名 + 值的语义**，不是逐字相等。
//  本文件按声明顺序输出（可读性最好），并用「字段集合 + 值」的语义比较做回归。
//
//  ## 与 Gson 的行为对齐点（逐条实测/推演过）
//
//  | 行为 | Gson | 这里 |
//  |---|---|---|
//  | `null` 字段 | **省略**（默认 `serializeNulls=false`） | 省略（[toJsonElementWithoutNulls]） |
//  | 默认值 | **照写**（只跳 null） | 照写 |
//  | 数字 | `Long` 原样、无小数点 | 同 |
//  | 缺字段反序列化 | 非空字段变 **null**（UnsafeAllocator） | 由调用方的 `sanitize*` 兜底，结果一致（见各 `fromJsonMap` 的注释） |
//  | `<>&='` | 转义成 `\u003c` 等 | **不转义**（都是合法 JSON、解析回同值；记为可接受差异） |
// ════════════════════════════════════════════════════════════════════════

/**
 * 落盘/备份用的 JSON 实例。
 *
 * ⚠ **不要**拿 `JsonSupport` 里那个内部实例来序列化模型 —— 那个是给
 * 「动态 JsonElement 解析」用的，语义取向不同（比如它对 null 会写 `JsonNull`）。
 */
internal val scheduleJson: Json = Json {
    // Gson 会写默认值（真实备份里 `isCustomTime:false` / `selectedWeeks:[]` 都在），
    // 所以这里也不能关掉默认值输出。
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 把「值可能是 null」的 Map 转成 JSON，**递归地跳过对象里的 null 值**。
 *
 * 对应 Gson 的默认行为 `serializeNulls = false` —— 真实备份里
 * `Course.customStartTime` / `customEndTime`（非自定义时间时为 null）**根本没出现**，
 * `SpecialBlock.items` 同理，就是这个行为。直接 `toJsonElement(map)` 会写出 `"…":null`。
 *
 * ⚠ **必须递归**：`specialBlocks` / `routines` 是**嵌套对象**，只跳顶层的话
 * 内层 `"items":null` 照样会被写出去（这是本文件第一版踩到的坑）。
 *
 * ⚠ 数组元素里的 null **要保留**（写 `null`）：Gson 的 `serializeNulls` 只影响
 * **对象字段**，集合元素照写 null。
 */
private fun plainValueToJsonWithoutNulls(value: Any?): JsonElement? = when (value) {
    null -> null
    is Map<*, *> -> kotlinx.serialization.json.JsonObject(
        value.entries.mapNotNull { (key, item) ->
            plainValueToJsonWithoutNulls(item)?.let { key.toString() to it }
        }.toMap(LinkedHashMap())
    )
    is Iterable<*> -> kotlinx.serialization.json.JsonArray(
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

/**
 * `Course` → JSON Map（显式字段清单，17 个字段）。
 *
 * 字段顺序 = 声明顺序（见文件头：顺序不是契约）。
 * `customStartTime` / `customEndTime` 为 null 时会被 [toJsonElementWithoutNulls] 跳过。
 */
fun Course.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "classroom" to classroom,
    "teacher" to teacher,
    "dayOfWeek" to dayOfWeek,
    "startSection" to startSection,
    "endSection" to endSection,
    "startWeek" to startWeek,
    "endWeek" to endWeek,
    "weekType" to weekType,
    "colorRes" to colorRes,
    "selectedWeeks" to selectedWeeks,
    "scheduleId" to scheduleId,
    "lastModified" to lastModified,
    "isCustomTime" to isCustomTime,
    "customStartTime" to customStartTime,
    "customEndTime" to customEndTime,
)

/**
 * JSON Map → `Course`。
 *
 * ⚠ **缺字段时取零值**（0 / "" / emptyList / null），刻意**不用 Kotlin 的构造默认值** ——
 * 这才与 Gson 一致：Gson 走 `UnsafeAllocator` 绕开构造器，缺字段留下 JVM 零值
 * （非空 `String` 字段因此会变成 null）。调用方 `sanitizeCourses` 原本就是为兜这件事写的
 * （`course.name ?: ""` 等），所以**最终结果与 Gson 路径完全一致**。
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
)

/** `List<Course>` → JSON 数组串。等价 `gson.toJson(courses)`。 */
fun encodeCourses(courses: List<Course>): String =
    scheduleJson.encodeToString(
        JsonElement.serializer(),
        kotlinx.serialization.json.JsonArray(courses.map { toJsonElementWithoutNulls(it.toJsonMap()) }),
    )

/**
 * JSON 数组串 → `List<Course>`。
 *
 * 语义对齐 `gson.fromJson<List<Course>>(json, …)`：
 * - 空数组 → 空列表
 * - 字面量 `null` → 空列表（Gson 返回 null，调用点写的是 `?: emptyList()`）
 * - **格式非法 → 抛异常**（Gson 抛 JsonSyntaxException），调用点的 `try/catch` 负责兜底
 * - 顶层不是数组 → 抛异常
 */
fun decodeCourses(json: String): List<Course> {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val element = parseJsonElement(trimmed)
    val array = element as? kotlinx.serialization.json.JsonArray
        ?: throw IllegalArgumentException("Not a course array")
    return array.mapNotNull { item ->
        (jsonToPlainValue(item) as? Map<*, *>)?.let(::courseFromJsonMap)
    }
}

// ════════════════════════════════════════════════════════════════════════
//  ScheduleFolder
// ════════════════════════════════════════════════════════════════════════

/** `ScheduleFolder` → JSON Map（3 个字段）。 */
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
        kotlinx.serialization.json.JsonArray(folders.map { toJsonElementWithoutNulls(it.toJsonMap()) }),
    )

/** JSON 数组串 → `List<ScheduleFolder>`。语义同 [decodeCourses]。 */
fun decodeScheduleFolders(json: String): List<ScheduleFolder> {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val array = parseJsonElement(trimmed) as? kotlinx.serialization.json.JsonArray
        ?: throw IllegalArgumentException("Not a folder array")
    return array.mapNotNull { item ->
        (jsonToPlainValue(item) as? Map<*, *>)?.let(::scheduleFolderFromJsonMap)
    }
}

// ════════════════════════════════════════════════════════════════════════
//  TimeConfig（含 TimeRoutine / SpecialBlock / SpecialItem）
//
//  ⚠ 这一族是 R2 里**最微妙**的部分：Gson 走 `UnsafeAllocator` 绕开构造器，
//  缺字段会让**非空字段变成 null**，而 `TimeConfig.sanitize()` / `safeSpecialBlocks` /
//  `safeRoutines` / `SpecialBlock.safeItems` 正是为兜这件事写的。
//
//  ⇒ **解码一律取零值（0 / false / "" / emptyList），绝不能用 Kotlin 的构造默认值**，
//  否则会跳过 sanitize 的恢复分支。反例：只缺 `morningSections` 时
//  Gson 给 0 → `total = 0+4+4 = 8` → 保留 0/4/4；若给默认值 4 → 变成 4/4/4，**结果不同**。
//
//  ⇒ 只有「非空类型 + Gson 会给 null」的字段，才在解码时直接补成 sanitize 的兜底值
//  （`name` → "默认配置"、`sectionTimes`/`sectionNames` → emptyMap、
//  `specialBlocks`/`routines` → emptyList），这样 sanitize 之后的**最终结果与 Gson 路径一致**。
//
//  ## ⚠⚠ 但是：Kotlin 默认值对 TimeConfig **是生效的**（实测，别搞反）
//
//  「Gson 绕开构造器」只对**没有无参构造器**的类成立。判据是「有没有必填参数」：
//
//  | 类 | 有必填参数？ | 有合成无参构造器？ | Gson 缺字段时给什么 |
//  |---|---|---|---|
//  | `Course` | ✅ 有（id/name/classroom… 前 11 个无默认值） | ❌ | **零值/null**（UnsafeAllocator） |
//  | `TimeConfig` / `TimeRoutine` / `SpecialBlock` / `SpecialItem` | ❌ 全部有默认值 | ✅ | **Kotlin 默认值** |
//
//  实测证据：`{}` 反序列化后 `shortBreak = 10`、`longBreakMorning = 20`、`morningStartHour = 8`
//  —— 全是 Kotlin 默认值，不是 0。
//
//  ⇒ 所以 [timeConfigFromJsonMap] 用 **`TimeConfig()` 作底 + `copy` 只覆盖「出现过的字段」**，
//  这正是「无参构造器 + 按需覆盖」的等价写法，也就不用把 25 个默认值再抄一遍。
//  ⇒ 嵌套的 `TimeRoutine.fromRaw` / `SpecialBlock.fromRaw` / `SpecialItem.fromRaw` 里的
//  fallback 字面量**恰好等于**各自的 Kotlin 默认值（已逐个核对），所以直接复用即可。
//
//  嵌套的 TimeRoutine / SpecialBlock / SpecialItem **直接复用现有的 `fromRaw(Map)`** ——
//  它们本来就是为「Gson 泛型丢失 + UnsafeAllocator 置 null」写的，且 `safeXxx` 走的也是它们，
//  所以复用等于与 sanitize 路径同源。
// ════════════════════════════════════════════════════════════════════════

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
 * `SpecialBlock` → JSON Map（5 个字段）。
 *
 * `items` 可空：**为 null 时会被 [toJsonElementWithoutNulls] 跳过**，
 * 与 Gson 的 `serializeNulls=false` 一致（旧数据里 `items` 缺失就是这个状态）。
 */
fun SpecialBlock.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "startTime" to startTime,
    "endTime" to endTime,
    "items" to items?.map { it.toJsonMap() },
)

/** `TimeRoutine` → JSON Map（24 个字段；`specialBlocks` 为 null 时跳过）。 */
fun TimeRoutine.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "effectiveMonth" to effectiveMonth,
    "effectiveDay" to effectiveDay,
    "quickTimeEnabled" to quickTimeEnabled,
    "classDuration" to classDuration,
    "shortBreak" to shortBreak,
    "longBreakEnabled" to longBreakEnabled,
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

/** `TimeConfig` → JSON Map（25 个字段）。 */
fun TimeConfig.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "morningSections" to morningSections,
    "afternoonSections" to afternoonSections,
    "eveningSections" to eveningSections,
    "quickTimeEnabled" to quickTimeEnabled,
    "classDuration" to classDuration,
    "shortBreak" to shortBreak,
    "longBreakEnabled" to longBreakEnabled,
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
 * JSON Map → `TimeConfig`（**Gson 形状**：缺字段取零值）。
 *
 * 结果**必须再过一遍 [TimeConfig.sanitize]**，那才是完整语义 ——
 * 这里只负责「把 JSON 变成 Gson 会给的那个对象」，不做任何业务归一。
 */
fun timeConfigFromJsonMap(map: Map<*, *>): TimeConfig {
    // 以 TimeConfig() 作底：这就是 Gson 走的那个合成无参构造器，默认值完全一致。
    // 然后**只覆盖 JSON 里出现过的字段**，等价于「Gson 先 new 再按需 set」。
    val base = TimeConfig()
    return base.copy(
        id = map.long("id", base.id),
        // 非空类型 + JSON 里显式 null → Gson 会塞 null，sanitize 再兜；这里直接给底值，
        // 经 sanitize 后结果相同（`{}` / `{"name":null}` 两个用例都钉住了）
        name = map.str("name") ?: base.name,
        morningSections = map.int("morningSections", base.morningSections),
        afternoonSections = map.int("afternoonSections", base.afternoonSections),
        eveningSections = map.int("eveningSections", base.eveningSections),
        quickTimeEnabled = map.bool("quickTimeEnabled", base.quickTimeEnabled),
        classDuration = map.int("classDuration", base.classDuration),
        shortBreak = map.int("shortBreak", base.shortBreak),
        longBreakEnabled = map.bool("longBreakEnabled", base.longBreakEnabled),
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
 * JSON 串 → `TimeConfig`。
 *
 * 语义对齐 `gson.fromJson<TimeConfig>(json)`：顶层不是对象或格式非法时**抛异常**
 * （Gson 抛 JsonSyntaxException），调用点用 `runCatching` 兜。
 * ⚠ 调用方拿到之后**必须**再过 [TimeConfig.sanitize]。
 */
fun decodeTimeConfig(json: String): TimeConfig {
    val element = parseJsonElement(json)
    val map = jsonToPlainValue(element) as? Map<*, *>
        ?: throw IllegalArgumentException("Not a time config object")
    return timeConfigFromJsonMap(map)
}

/**
 * `TimeConfig` 的已知字段名。JSON 里**一个都不像**时判为损坏。
 *
 * 原先是 `TimeConfigSnapshotParser.kt`（`:app`）里的私有常量 —— 那个文件因为依赖 Gson
 * 被留在 Android 侧，注释里写明「等阶段 2 换成 kotlinx.serialization 之后应当移回」。
 * 现在就是那个时候：逻辑逐字搬过来，原文件删除。
 */
private val TIME_CONFIG_FIELD_NAMES = setOf(
    "id", "name", "morningSections", "afternoonSections", "eveningSections",
    "quickTimeEnabled", "classDuration", "shortBreak",
    "sectionTimes", "sectionNames", "specialBlocks", "routines",
)

/**
 * `TimeConfig` 快照解析：JSON 键名一个已知字段都不像时判为损坏，返回 null。
 *
 * 与迁移前 `parseTimeConfigSnapshotOrNull(gson, json)` **逻辑完全一致**：
 * 先解析成对象做「像不像 TimeConfig」的判定，再整体解析。
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
    val array = parseJsonElement(json) as? kotlinx.serialization.json.JsonArray
        ?: throw IllegalArgumentException("Not a string array")
    return array.mapNotNull { (jsonToPlainValue(it) as? String) }
}

/** `List<Long>` → JSON 数组串。等价 `gson.toJson(ids)`。 */
fun encodeLongList(values: List<Long>): String =
    scheduleJson.encodeToString(JsonElement.serializer(), toJsonElement(values))

/** JSON 数组串 → `List<Long>`。 */
fun decodeLongList(json: String): List<Long> {
    val array = parseJsonElement(json) as? kotlinx.serialization.json.JsonArray
        ?: throw IllegalArgumentException("Not a long array")
    return array.mapNotNull { (jsonToPlainValue(it) as? Number)?.toLong() }
}
