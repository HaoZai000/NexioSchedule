package com.haooz.chedule.weather

/**
 * 时间工具：只做三件事 —— ISO 串转纪元秒、纪元秒转当地「时:分」、当地「年月日」。
 *
 * 为什么不用 kotlinx-datetime：这一步只需要**固定偏移**换算，三家源都给回
 * `utc_offset_seconds` 或自带 `+08:00`。走 `TimeZone.of("Asia/Shanghai")` 反而
 * 要依赖各平台内置 tzdb（iOS 目标默认关闭、鸿蒙是另一套构建），且海外城市会错。
 * 所以这里用「纪元日号 ↔ 民用日期」的整数算法（Howard Hinnant 的 civil_from_days），
 * 纯算术、零依赖、可单测。
 */

/** 公历 (y,m,d) → 自 1970-01-01 起的天数。月按 1..12。 */
internal fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400                                   // [0, 399]
    val mp = (month + 9) % 12                                 // 3..14 → 0..11（三月为年首）
    val doy = (153 * mp + 2) / 5 + day - 1                    // [0, 365]
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy           // [0, 146096]
    return era * 146097 + doe - 719468
}

/** 自 1970-01-01 起的天数 → 公历 (y,m,d)。 */
internal fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
    var z = z0 + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
}

/**
 * 解析 ISO 风格时间串为纪元秒。
 *
 * 支持："2026-11-04T08:00:00+08:00"、"2026-11-04T08:00"（无偏移时用 [fallbackOffsetSec]）、
 * "2026-11-04"（当天 0 点）。解析失败返回 null —— 调用方一律当作"没这个字段"，
 * 不要拿 0 当兜底（那是 1970 年）。
 */
internal fun parseIsoEpochSec(raw: String?, fallbackOffsetSec: Int = 28_800): Long? {
    val s = raw?.trim().orEmpty()
    if (s.length < 10) return null
    val y = s.substring(0, 4).toIntOrNull() ?: return null
    val mo = s.substring(5, 7).toIntOrNull() ?: return null
    val d = s.substring(8, 10).toIntOrNull() ?: return null
    var hour = 0
    var minute = 0
    var second = 0
    var offsetSec = fallbackOffsetSec
    if (s.length >= 16 && (s[10] == 'T' || s[10] == ' ')) {
        hour = s.substring(11, 13).toIntOrNull() ?: return null
        minute = s.substring(14, 16).toIntOrNull() ?: return null
        if (s.length >= 19) second = s.substring(17, 19).toIntOrNull() ?: 0
        // 尾部偏移：+08:00 / -05:00 / Z。
        // ⚠ 必须先判长度再 substring：Open-Meteo 的 sunrise 是 "2026-11-04T06:46"（只有 16 位），
        // 直接 substring(19) 会整条源被你抛出去 —— 表现是「Open-Meteo 一路永远缺数据」。
        val tail = if (s.length > 19) s.substring(19) else ""
        offsetSec = when {
            tail.startsWith("Z") -> 0
            tail.length >= 6 && (tail[0] == '+' || tail[0] == '-') -> {
                val oh = tail.substring(1, 3).toIntOrNull() ?: 0
                val om = tail.substring(4, 6).toIntOrNull() ?: 0
                val sign = if (tail[0] == '-') -1 else 1
                sign * (oh * 3600 + om * 60)
            }
            else -> fallbackOffsetSec
        }
    }
    return daysFromCivil(y, mo, d) * 86_400L + hour * 3600L + minute * 60L + second - offsetSec
}

/** 纪元秒 + 偏移 → 当地「时:分」，补零。 */
internal fun localHourMinute(epochSec: Long, offsetSec: Int): String {
    val local = epochSec + offsetSec
    val h = floorMod(local, 86_400L) / 3600
    val m = floorMod(local, 3_600L) / 60
    return "${pad2(h.toInt())}:${pad2(m.toInt())}"
}

/** 纪元秒 + 偏移 → 当地日期 "2026-11-04"。 */
internal fun localDateKey(epochSec: Long, offsetSec: Int): String {
    val (y, m, d) = civilFromDays(floorDiv(epochSec + offsetSec, 86_400L))
    return "${y}-${pad2(m)}-${pad2(d)}"
}

/** "2026-11-04" → 周序号，0 = 周日。 */
internal fun weekdayIndex(dateKey: String): Int {
    val y = dateKey.substring(0, 4).toIntOrNull() ?: return 0
    val m = dateKey.substring(5, 7).toIntOrNull() ?: return 0
    val d = dateKey.substring(8, 10).toIntOrNull() ?: return 0
    // 1970-01-01 是周四（index 4）
    return floorMod(daysFromCivil(y, m, d) + 4, 7L).toInt()
}

/** 从 ISO 串里取 "HH:mm"；失败返回空串。 */
internal fun clockOf(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.length >= 16 && (s[10] == 'T' || s[10] == ' ')) return s.substring(11, 16)
    return ""
}

/** 向负无穷取整的除法（Kotlin 的 `/` 对负数是向零截断，算日期会差一天）。 */
internal fun floorDiv(a: Long, b: Long): Long {
    val q = a / b
    return if (a % b != 0L && ((a xor b) < 0)) q - 1 else q
}

/** 结果符号始终跟随除数（非负），用于取模。 */
internal fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

private fun pad2(v: Int): String = if (v < 10) "0$v" else "$v"

/** UTF-8 百分号编码（KMP 无 URLEncoder；只放行 unreserved 字符）。 */
internal fun urlEncode(value: String): String {
    val sb = StringBuilder(value.length + 16)
    for (byte in value.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        val c = b.toChar()
        if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
            sb.append(c)
        } else {
            sb.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
        }
    }
    return sb.toString()
}

private val HEX = "0123456789ABCDEF".toCharArray()
