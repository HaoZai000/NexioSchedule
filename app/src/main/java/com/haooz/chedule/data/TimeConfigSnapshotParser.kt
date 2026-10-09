package com.haooz.chedule.data

import com.google.gson.Gson

/**
 * [TimeConfig] 的快照解析，依赖 Gson，因此留在 Android 侧。
 *
 * 原来它是 `TimeConfig.Companion.parseSnapshotOrNull`，随 TimeConfig 一起被搬到 `:core`
 * 的 commonMain。但 Gson 是运行时反射库，Kotlin/Native（iOS）上不存在，
 * 一旦留在 commonMain 会直接阻断 iOS 目标，所以拆到这里。
 *
 * 等阶段 2 把序列化换成 kotlinx.serialization 之后，这个函数应当移回
 * `TimeConfig.Companion`，与本文件一并删除。
 *
 * 逻辑与拆分前完全一致，未做任何行为改动。
 */
private val TIME_CONFIG_FIELD_NAMES = setOf(
    "id", "name", "morningSections", "afternoonSections", "eveningSections",
    "quickTimeEnabled", "classDuration", "shortBreak",
    "sectionTimes", "sectionNames", "specialBlocks", "routines"
)

/** JSON 键名一个已知字段都不像时判为损坏，返回 null */
fun parseTimeConfigSnapshotOrNull(gson: Gson, json: String): TimeConfig? {
    val obj = runCatching {
        gson.fromJson(json, com.google.gson.JsonObject::class.java)
    }.getOrNull() ?: return null
    if (obj.keySet().none { it in TIME_CONFIG_FIELD_NAMES }) return null
    return runCatching { gson.fromJson(json, TimeConfig::class.java) }.getOrNull()
}
