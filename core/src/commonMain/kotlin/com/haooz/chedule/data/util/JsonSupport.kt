package com.haooz.chedule.data.util

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
 * `org.json` 的跨平台替代（kotlinx-serialization 动态解析，无需编译器插件）。
 *
 * 与 org.json 的差异：取值失败返回默认值而不抛异常；`JsonNull` 一律视为「无值」
 * （org.json 的 `optString` 会返回字符串 `"null"`）；[JsonObject] 保持插入顺序。
 */

private val json = Json {
    // 后端可能新增字段而客户端尚未跟进，忽略未知键避免解析失败
    ignoreUnknownKeys = true
    isLenient = true
}

/** 解析 JSON 文本为任意元素。格式非法时抛 SerializationException。 */
fun parseJsonElement(text: String): JsonElement = json.parseToJsonElement(text)

/** 解析 JSON 文本为对象。格式非法或顶层不是对象时抛异常。 */
fun parseJsonObject(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

/** 解析 JSON 文本为数组。格式非法或顶层不是数组时抛异常。 */
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

/** 按 org.json 的 `JSON.toString(Object)` 语义转文本；嵌套 object/array 给其 JSON 文本，而非空串或报错。 */
private fun JsonElement.toOrgJsonText(): String =
    if (this is JsonPrimitive) content else toString()

/**
 * 按 org.json 的 `(int) Double.parseDouble(s)` 语义转整数：先转 double 再截断，
 * 所以 `"34.9"` → `34`、`"1e3"` → `1000`。
 */
private fun String.toOrgJsonInt(): Int? = toDoubleOrNull()?.takeIf { !it.isNaN() }?.toInt()
private fun String.toOrgJsonLong(): Long? = toDoubleOrNull()?.takeIf { !it.isNaN() }?.toLong()

// ---- 「原始值」读取：给 `opt(key) as? X` 风格的代码用 ----

/**
 * 对应 org.json 的 `opt(key)`：返回原始值而非强制转换（字符串 → `String`、数字 → `Double`、
 * 对象 → `Map`、数组 → `List`）。
 *
 * 保留它是为了让 `item.opt("date") as? String` 这类判断原样可用 —— 改写成 `optString`
 * 会做强制转换，语义不同（数字 `1` 会变成 `"1"`）。
 */
fun JsonObject.optRaw(key: String): Any? = jsonToPlainValue(this[key])

/** 对应 org.json 的 `optJSONObject(index)` 的「原始值」版本。 */
fun JsonArray.optRaw(index: Int): Any? = jsonToPlainValue(getOrNull(index))

// ---- JsonObject 读取（对应 org.json 的 optXxx / isNull）----

/** 对应 `optString(key)`：缺键或值为 null 时返回 ""。 */
fun JsonObject.optString(key: String): String = optString(key, "")

/**
 * 对应 `optString(key, fallback)`：数字/布尔会强制转文本，嵌套 object/array 返回其 JSON 文本。
 * 唯一有意的差异：显式 null 返回 [fallback]，org.json 会返回字符串 `"null"`。
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
 * 对应 `optBoolean(key, fallback)`：只认 `"true"` / `"false"`（忽略大小写），其余（含数字）返回 fallback。
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
    raw(key) as? JsonObject

/** 对应 `optJSONArray(key)`：不是数组或不存在时返回 null。 */
fun JsonObject.optJsonArray(key: String): JsonArray? =
    raw(key) as? JsonArray

/** 对应 `has(key)`。 */
fun JsonObject.hasKey(key: String): Boolean = containsKey(key)

/** 对应 `isNull(key)`：键不存在、或值显式为 null，都返回 true。 */
fun JsonObject.isJsonNull(key: String): Boolean = raw(key) == null

// ---- JsonArray 读取 ----

/** 对应 `optJSONObject(index)`。 */
fun JsonArray.optJsonObject(index: Int): JsonObject? =
    raw(index) as? JsonObject

/** 对应 `optJSONArray(index)`。 */
fun JsonArray.optJsonArray(index: Int): JsonArray? =
    raw(index) as? JsonArray

/** 对应 `optString(index)`。与对象侧一致：数字/布尔转文本，嵌套结构返回 JSON 文本。 */
fun JsonArray.optString(index: Int, fallback: String = ""): String =
    raw(index)?.toOrgJsonText() ?: fallback

/** 对应 `length()`。用 [size] 亦可，这里提供同名方法便于机械替换。 */
fun JsonArray.length(): Int = size

/**
 * [JsonElement] → 纯 Kotlin 值（`Map` / `List` / `String` / `Double` / `Boolean` / null），
 * 替代 Gson 的 `fromJson<Map<String, Any>>(raw, TypeToken…)` 路径 —— 多个解析器
 * （`TeachingWeekReorganization.fromBackupValue`、`HolidayManager.decodeBackup*`）按此形状编写。
 *
 * ⚠ 别改：数字一律转 `Double`（`1` → `1.0`）；仅 `isString` 的 primitive 为 `String`；
 * 对象保持插入顺序（`LinkedHashMap`，同 Gson 的 `LinkedTreeMap`）；`JsonNull` → null。
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
}
