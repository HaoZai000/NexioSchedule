package com.haooz.chedule.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * `org.json` 的跨平台替代。
 *
 * ## 为什么要换
 *
 * `org.json`（`JSONObject` / `JSONArray`）**由 Android 平台提供**，JDK 与 Kotlin/Native
 * 都没有。任何用它的文件都进不了 `:core`。
 *
 * ## 为什么不需要 kotlinx-serialization 编译器插件
 *
 * 这里只用 **JsonElement 动态解析**（`JsonObject` / `JsonArray` / `JsonPrimitive`），
 * 不声明 `@Serializable` 数据类，所以**不需要给模块加 serialization 插件**，
 * 也就不必给 `:app` 动构建配置。将来要是需要强类型映射再加插件也不迟。
 *
 * ## 与 org.json 的语义差异（迁移时注意）
 *
 * 1. **取值失败返回默认值，不抛异常**。org.json 的 `getString` / `getJSONArray` 缺键时
 *    抛 `JSONException`；这里一律返回默认值 / null。迁移前大量调用点用
 *    `runCatching { … }.getOrDefault(…)` 兜底，现在那些兜底仍有效（只是不再需要）。
 * 2. **`JsonNull` 一律视为「无值」**。org.json 的 `optString` 遇到 `JSONObject.NULL`
 *    会返回字符串 `"null"`（公认的设计缺陷）。这里返回默认值 —— 更符合直觉，
 *    且已知调用点（NoticeFetcher）都会先用 [isJsonNull] 判空，不受影响。
 * 3. **[JsonObject] 是有序的 `Map`**，遍历顺序为插入顺序，与 org.json 的
 *    `LinkedHashMap` 行为一致；但 org.json 的 `keys()` 顺序在旧版本不保证，迁移后反而更稳定。
 */

private val json = Json {
    // 后端可能新增字段而客户端尚未跟进，忽略未知键避免解析失败
    ignoreUnknownKeys = true
    isLenient = true
}

/** 解析 JSON 文本为对象。格式非法时抛 [kotlinx.serialization.SerializationException]（对应 org.json 的 JSONException）。 */
fun parseJsonObject(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

/** 解析 JSON 文本为数组。 */
fun parseJsonArray(text: String): JsonArray = json.parseToJsonElement(text).jsonArray

/** 由键值对构造对象（对应 `JSONObject().apply { put(k, v) }`）。 */
fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.associate { (k, v) -> k to toJsonElement(v) })

/** 由元素构造数组。 */
fun jsonArrayOf(vararg values: Any?): JsonArray =
    JsonArray(values.map { toJsonElement(it) })

/** 把常见 Kotlin 值转成 [JsonElement]。支持嵌套的 Map / Iterable。 */
fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value.toDouble())
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) })
    is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
    else -> JsonPrimitive(value.toString())
}

private fun JsonObject.raw(key: String): JsonElement? =
    this[key]?.takeIf { it !is JsonNull }

private fun JsonArray.raw(index: Int): JsonElement? =
    getOrNull(index)?.takeIf { it !is JsonNull }

/**
 * 按 org.json 的 `JSON.toString(Object)` 语义把任意 [JsonElement] 转成文本。
 *
 * org.json 对**嵌套的 object / array** 也会给出其 JSON 文本（而不是空串或报错），
 * 这是 `optString` 在真实数据上可能被踩的行为 —— 早先版本这里返回 `""`。
 */
private fun JsonElement.toOrgJsonText(): String =
    if (this is JsonPrimitive) content else toString()

/**
 * 把字符串按 org.json 的 `(int) Double.parseDouble(s)` 语义转成整数。
 *
 * 关键是 org.json **先转 double 再截断**，所以 `"34.9"` → `34`、`"1e3"` → `1000`、
 * `"2.5"` → `2`。早先版本用严格的 `intOrNull()`，这些输入会**静默回落成默认值**。
 */
private fun String.toOrgJsonInt(): Int? = toDoubleOrNull()?.takeIf { !it.isNaN() }?.toInt()
private fun String.toOrgJsonLong(): Long? = toDoubleOrNull()?.takeIf { !it.isNaN() }?.toLong()

// ---- JsonObject 读取（对应 org.json 的 optXxx / isNull）----

/** 对应 `optString(key)`：缺键或值为 null 时返回 ""。 */
fun JsonObject.optString(key: String): String = optString(key, "")

/**
 * 对应 `optString(key, fallback)`。
 *
 * 与 org.json 一致：数字/布尔会强制转文本（`123` → `"123"`），
 * **嵌套 object/array 返回其 JSON 文本**。
 *
 * ⚠ 唯一有意的差异：值为显式 null 时返回 [fallback]，而 org.json 会返回字符串 `"null"`
 * （公认缺陷）。已知调用点都会先判空，不受影响。
 */
fun JsonObject.optString(key: String, fallback: String): String =
    raw(key)?.toOrgJsonText() ?: fallback

fun JsonObject.optInt(key: String, fallback: Int): Int {
    val v = raw(key) ?: return fallback
    if (v !is JsonPrimitive) return fallback
    return if (v.isString) {
        v.content.toOrgJsonInt() ?: fallback
    } else {
        v.content.toDoubleOrNull()?.toInt() ?: fallback
    }
}

fun JsonObject.optLong(key: String, fallback: Long): Long {
    val v = raw(key) ?: return fallback
    if (v !is JsonPrimitive) return fallback
    return if (v.isString) {
        v.content.toOrgJsonLong() ?: fallback
    } else {
        v.content.toDoubleOrNull()?.toLong() ?: fallback
    }
}

fun JsonObject.optDouble(key: String, fallback: Double): Double {
    val v = raw(key) ?: return fallback
    if (v !is JsonPrimitive) return fallback
    return v.content.toDoubleOrNull() ?: fallback
}

/**
 * 对应 `optBoolean(key, fallback)`。
 *
 * 与 org.json 一致：只认 `"true"` / `"false"`（**忽略大小写**），其余（含数字）返回 fallback。
 */
fun JsonObject.optBoolean(key: String, fallback: Boolean = false): Boolean {
    val v = raw(key) ?: return fallback
    if (v !is JsonPrimitive) return fallback
    return when {
        !v.isString -> v.booleanOrNull ?: fallback
        v.content.equals("true", ignoreCase = true) -> true
        v.content.equals("false", ignoreCase = true) -> false
        else -> fallback
    }
}

/** 对应 `optJSONObject(key)`：不是对象或不存在时返回 null。 */
fun JsonObject.optJsonObject(key: String): JsonObject? =
    raw(key)?.let { runCatching { it.jsonObject }.getOrNull() }

/** 对应 `optJSONArray(key)`：不是数组或不存在时返回 null。 */
fun JsonObject.optJsonArray(key: String): JsonArray? =
    raw(key)?.let { runCatching { it.jsonArray }.getOrNull() }

/** 对应 `has(key)`。 */
fun JsonObject.hasKey(key: String): Boolean = containsKey(key)

/** 对应 `isNull(key)`：键不存在、或值显式为 null，都返回 true。 */
fun JsonObject.isJsonNull(key: String): Boolean = raw(key) == null

// ---- JsonArray 读取 ----

/** 对应 `optJSONObject(index)`。 */
fun JsonArray.optJsonObject(index: Int): JsonObject? =
    raw(index)?.let { runCatching { it.jsonObject }.getOrNull() }

/** 对应 `optJSONArray(index)`。 */
fun JsonArray.optJsonArray(index: Int): JsonArray? =
    raw(index)?.let { runCatching { it.jsonArray }.getOrNull() }

/** 对应 `optString(index)`。与对象侧一致：数字/布尔转文本，嵌套结构返回 JSON 文本。 */
fun JsonArray.optString(index: Int, fallback: String = ""): String =
    raw(index)?.toOrgJsonText() ?: fallback

/** 对应 `length()`。用 [JsonArray.size] 亦可，这里提供同名方法便于机械替换。 */
fun JsonArray.length(): Int = size

/**
 * [JsonElement] → 纯 Kotlin 值（`Map` / `List` / `String` / `Double` / `Boolean` / null）。
 *
 * 用来替代 **Gson 的 `fromJson<Map<String, Any>>(raw, TypeToken…)`** —— 那条路径把 JSON
 * 解析成「嵌套的 `LinkedHashMap` + `Double` 数字」，本项目好几处解析器（如
 * `TeachingWeekReorganization.fromBackupValue`、`HolidayManager.decodeBackup*`）
 * 正是按这个形状写的（`(value as? Number)?.toDouble()`）。
 *
 * ## 与 Gson 的对齐点（别改）
 *
 * 1. **数字一律转 `Double`**：Gson 的 `ObjectTypeAdapter` 读 NUMBER 就是 `in.nextDouble()`，
 *    所以 `1` 到这里也是 `1.0`。调用方 `(value as? Number).toDouble()` 两种都吃。
 * 2. **只有 `isString` 的 primitive 才是 `String`**：JSON 里的 `"1"` 是字符串、`1` 是数字，
 *    这个区分不能丢（Gson 也是分开的）。
 * 3. **对象保持插入顺序**：返回 `LinkedHashMap`，与 Gson 的 `LinkedTreeMap` 一致。
 * 4. `JsonNull` → `null`（Gson 的 `Map<String,Any>` 里 null 就是 null）。
 */
fun jsonToPlainValue(element: JsonElement?): Any? = when (element) {
    null, is JsonNull -> null
    is JsonObject -> LinkedHashMap<String, Any?>(element.size).apply {
        element.forEach { (key, value) -> put(key, jsonToPlainValue(value)) }
    }
    is JsonArray -> element.map { jsonToPlainValue(it) }
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.content == "true" -> true
        element.content == "false" -> false
        else -> element.content.toDoubleOrNull() ?: element.content
    }
    else -> element.toString()
}
