package com.haooz.chedule.weather

/** 等级词按严重度从高到低；两家源一个放 level，一个只写在标题里。 */
private val LEVEL_WORDS = listOf("红色", "橙色", "黄色", "蓝色", "白色")
private val LEVEL_RANK = mapOf("红色" to 4, "橙色" to 3, "黄色" to 2, "蓝色" to 1, "白色" to 0)

internal fun alertLevelWord(alert: WeatherAlert): String =
    LEVEL_WORDS.firstOrNull { (alert.level + alert.title + alert.type).contains(it) }.orEmpty()

/** 标题只留「类型 + 级别 + 预警」，地区在正文里已经有了。 */
internal fun alertHeadline(alert: WeatherAlert): String {
    val pub = alert.detail.indexOf("发布")
    val signal = if (pub >= 0) alert.detail.indexOf("预警信号", pub + 2) else -1
    if (signal > pub + 2) {
        val body = alert.detail.substring(pub + 2, signal).trim()
        if (body.isNotBlank() && body.length <= 16 && body.none { it.isWhitespace() }) return body + "预警"
    }
    val level = alertLevelWord(alert)
    val kind = alert.type.trim().removeSuffix("预警").removeSuffix("信号")
        .takeIf { it.isNotBlank() && it.length <= 12 && it.any { c -> c.code in 0x4E00..0x9FFF } }
    return when {
        kind != null -> kind + level + "预警"
        level.isNotEmpty() -> level + "预警"
        else -> alert.title.trim().ifBlank { "气象预警" }
    }
}

/** 正文末尾的来源括注单独摆在卡片底部。 */
internal fun splitAlertSource(detail: String): Pair<String, String> {
    for (marker in listOf("（预警信息来源：", "(预警信息来源：", "（信息来源：", "(信息来源：")) {
        val start = detail.indexOf(marker)
        if (start < 0) continue
        val body = detail.substring(0, start).trim()
        val tail = detail.substring(start + marker.length)
        val end = tail.indexOfFirst { it == '）' || it == ')' }.let { if (it < 0) tail.length else it }
        return body to tail.substring(0, end).trim().trimEnd('！', '!', '。')
    }
    return detail.trim() to ""
}

/** 一条预警常被两家同时收录，按标题 + 发布时刻指纹合并，保留权重高的那份。 */
internal fun mergeAlerts(raw: List<WeatherAlert>): List<WeatherAlert> {
    val kept = mutableListOf<WeatherAlert>()
    val stamps = mutableListOf<String>()
    for (alert in raw) {
        val title = alertHeadline(alert)
        val stamp = alertStamp(alert)
        val dup = kept.indices.firstOrNull { i ->
            kept[i].title == title && sameStamp(stamps[i], stamp)
        } ?: -1
        if (dup >= 0) {
            if (kept[dup].detail.isBlank() && alert.detail.isNotBlank()) {
                kept[dup] = kept[dup].copy(detail = alert.detail)
            }
            continue
        }
        kept += alert.copy(title = title)
        stamps += stamp
    }
    return kept.sortedByDescending { LEVEL_RANK[alertLevelWord(it)] ?: 0 }
}

/** 两家源的正文都带「2026年11月05日19时39分发布」，抽它的数字当指纹最稳。 */
private fun alertStamp(alert: WeatherAlert): String {
    val pub = alert.detail.indexOf("发布")
    if (pub > 0) {
        val digits = alert.detail.substring(0, pub).filter { it.isDigit() }
        if (digits.length >= 8) return digits
    }
    return alert.pubTime.filter { it.isDigit() }
}

private fun sameStamp(a: String, b: String): Boolean =
    a == b || a.isEmpty() || b.isEmpty() || a.takeLast(4) == b.takeLast(4)
