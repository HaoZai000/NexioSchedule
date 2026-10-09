package com.haooz.chedule.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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
 * 把「值可能是 null」的 Map 转成 JSON，**跳过 null 值**。
 *
 * 对应 Gson 的默认行为 `serializeNulls = false` —— 真实备份里
 * `Course.customStartTime` / `customEndTime`（非自定义时间时为 null）**根本没出现**，
 * 就是这个行为。直接 `toJsonElement(map)` 会写出 `"customStartTime":null`，格式就变了。
 */
fun toJsonElementWithoutNulls(map: Map<String, Any?>): JsonElement =
    kotlinx.serialization.json.JsonObject(
        map.entries.mapNotNull { (key, value) ->
            if (value == null) null else key to toJsonElement(value)
        }.toMap(LinkedHashMap())
    )

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
