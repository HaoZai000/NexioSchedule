package com.haooz.chedule.weather

/**
 * 天气快照的离线缓存编解码（纯 Kotlin，不依赖序列化插件）。
 *
 * ## 为什么要手写
 *
 * 本项目没有 kotlinx-serialization 的编译器插件（见 `libs.versions.toml` 里 kotlinxSerialization
 * 那一段的注释）。这些模型全是扁平的可空字段，手写一份「一行一条记录、`|` 分隔」的文本，
 * 比为了缓存去引一个编译器插件便宜得多。
 *
 * ## 格式
 *
 * 第一行是版本号，后面每行一条记录：`TAG|字段1|字段2|…`。空串代表 null。
 * 改字段时**版本号 +1**：旧缓存直接当「读不出来」丢掉。缓存丢了最多多一次联网，
 * 不值得为它写迁移 —— 这与项目里存储层「表是唯一真相」的口径一致。
 */

private const val CACHE_VERSION = "1"

internal fun encodeWeatherSnapshot(s: WeatherSnapshot): String = buildString {
    append(CACHE_VERSION).append('\n')

    row(
        "PLACE",
        s.placeName,
        s.utcOffsetSec,
        s.fetchedAtEpochMs,
    )

    with(s.now) {
        row(
            "NOW",
            tempC, feelsLikeC, text, humidity, pressureHpa,
            windDir, windDirDeg, windPower, windSpeedKmh, precipMm,
            visibilityKm, cloudCover, uvIndex, dewPointC, isDay, observedAt,
        )
    }

    s.hourly.forEach { h ->
        with(h) {
            row(
                "H",
                epochSec, tempC, text, precipProb, precipMm,
                windSpeedKmh, windDirDeg, humidity, aqi, pressureHpa,
                dewPointC, feelsLikeC, uvIndex, visibilityKm,
            )
        }
    }

    s.daily.forEach { d ->
        with(d) {
            row(
                "D",
                date, high, low, dayText, nightText,
                precipProb, precipMm, sunrise, sunset, uvIndexMax,
                aqi, windDir, windSpeedKmh, humidity, pressureHpa, visibilityKm,
            )
        }
    }

    s.air?.let { a ->
        with(a) {
            row("AIR", aqi, level, primary, pm25, pm10, o3, no2, so2, co, tip)
        }
    }

    s.alerts.forEach { a ->
        row("ALERT", a.title, a.type, a.level, a.detail, a.pubTime)
    }

    s.indices.forEach { i ->
        row("IDX", i.name, i.level, i.tip)
    }

    s.nowcast?.let { n ->
        // 分钟级降水是一串浮点，量小（≈120 个），逗号拼接比一行一个 token 省事
        row("RAIN", n.headline, n.detail, n.pubTime, n.values.joinToString(","))
    }

    row("SRC", s.sources.joinToString("\u0001"))
}

/** 解析失败返回 null（调用方当「没有缓存」处理）。 */
internal fun decodeWeatherSnapshot(raw: String): WeatherSnapshot? = runCatching {
    val lines = raw.split('\n')
    if (lines.firstOrNull() != CACHE_VERSION) return null

    var placeName = ""
    var utcOffsetSec = 28_800
    var fetchedAtMs = 0L
    var now = WeatherNow()
    val hourly = ArrayList<WeatherHour>(32)
    val daily = ArrayList<WeatherDay>(7)
    var air: AirQuality? = null
    val alerts = ArrayList<WeatherAlert>(2)
    val indices = ArrayList<LifeIndex>(8)
    var nowcast: RainNowcast? = null
    var sources: List<String> = emptyList()
    var sawRecord = false

    for (line in lines) {
        if (line.isEmpty()) continue
        val f = splitRow(line)
        when (f[0]) {
            "PLACE" -> {
                placeName = f.str(1)
                utcOffsetSec = f.int(2) ?: 28_800
                fetchedAtMs = f.long(3) ?: 0L
            }

            "NOW" -> now = WeatherNow(
                tempC = f.dbl(1),
                feelsLikeC = f.dbl(2),
                text = f.str(3),
                humidity = f.int(4),
                pressureHpa = f.int(5),
                windDir = f.str(6),
                windDirDeg = f.int(7),
                windPower = f.str(8),
                windSpeedKmh = f.dbl(9),
                precipMm = f.dbl(10),
                visibilityKm = f.dbl(11),
                cloudCover = f.int(12),
                uvIndex = f.int(13),
                dewPointC = f.dbl(14),
                isDay = f.bool(15),
                observedAt = f.str(16),
            )

            "H" -> hourly += WeatherHour(
                epochSec = f.long(1) ?: 0L,
                tempC = f.dbl(2),
                text = f.str(3),
                precipProb = f.int(4),
                precipMm = f.dbl(5),
                windSpeedKmh = f.dbl(6),
                windDirDeg = f.int(7),
                humidity = f.int(8),
                aqi = f.int(9),
                pressureHpa = f.int(10),
                dewPointC = f.dbl(11),
                feelsLikeC = f.dbl(12),
                uvIndex = f.int(13),
                visibilityKm = f.dbl(14),
            )

            "D" -> daily += WeatherDay(
                date = f.str(1),
                high = f.dbl(2),
                low = f.dbl(3),
                dayText = f.str(4),
                nightText = f.str(5),
                precipProb = f.int(6),
                precipMm = f.dbl(7),
                sunrise = f.str(8),
                sunset = f.str(9),
                uvIndexMax = f.int(10),
                aqi = f.int(11),
                windDir = f.str(12),
                windSpeedKmh = f.dbl(13),
                humidity = f.int(14),
                pressureHpa = f.int(15),
                visibilityKm = f.dbl(16),
            )

            "AIR" -> air = AirQuality(
                aqi = f.int(1),
                level = f.str(2),
                primary = f.str(3),
                pm25 = f.dbl(4),
                pm10 = f.dbl(5),
                o3 = f.dbl(6),
                no2 = f.dbl(7),
                so2 = f.dbl(8),
                co = f.dbl(9),
                tip = f.str(10),
            )

            "ALERT" -> alerts += WeatherAlert(
                title = f.str(1),
                type = f.str(2),
                level = f.str(3),
                detail = f.str(4),
                pubTime = f.str(5),
            )

            "IDX" -> indices += LifeIndex(
                name = f.str(1),
                level = f.str(2),
                tip = f.str(3),
            )

            "RAIN" -> nowcast = RainNowcast(
                headline = f.str(1),
                detail = f.str(2),
                pubTime = f.str(3),
                values = f.str(4).split(',')
                    .mapNotNull { it.trim().toFloatOrNull() },
            )

            "SRC" -> sources = f.str(1).split('\u0001').filter { it.isNotEmpty() }
            else -> continue
        }
        sawRecord = true
    }

    // 版本对得上但一条记录都认不出来：别返回一坨默认值（那会画出空卡片，比没有缓存更糟）
    if (!sawRecord) return null

    WeatherSnapshot(
        placeName = placeName,
        now = now,
        hourly = hourly,
        daily = daily,
        air = air,
        alerts = alerts,
        indices = indices,
        nowcast = nowcast,
        sources = sources,
        fetchedAtEpochMs = fetchedAtMs,
        utcOffsetSec = utcOffsetSec,
    )
}.getOrNull()

// ── 写：一行一条记录 ────────────────────────────────────────────────────

private fun StringBuilder.row(tag: String, vararg values: Any?): StringBuilder {
    append(tag)
    for (v in values) {
        append('|')
        when (v) {
            null -> Unit
            is String -> append(escapeField(v))
            is Boolean -> append(if (v) '1' else '0')
            else -> append(v.toString())
        }
    }
    return append('\n')
}

/**
 * 字段内的 `|` 与换行会破坏行/列结构，必须转义。
 * 生活指数的 tip、预警 detail 都是长句，出现换行是完全正常的输入。
 */
private fun escapeField(s: String): String {
    if (s.none { it == '\\' || it == '|' || it == '\n' || it == '\r' }) return s
    return buildString(s.length + 8) {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '|' -> append("\\|")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }
}

/**
 * 按 `|` 切列，但**跳过被转义的 `|`**。
 *
 * 不能用 `line.split('|')`：预警详情里出现竖线是正常的（"阵风 7 级 | 请防范"），
 * 切分前不认转义的话，一条记录会被切碎，后面所有列整体错位 —— 这类 bug 不抛异常，
 * 只让温度显示成湿度。
 */
private fun splitRow(line: String): List<String> {
    val out = ArrayList<String>(8)
    val cur = StringBuilder(line.length)
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '\\' && i + 1 < line.length) {
            when (val n = line[i + 1]) {
                'n' -> cur.append('\n')
                'r' -> cur.append('\r')
                '|' -> cur.append('|')
                '\\' -> cur.append('\\')
                else -> cur.append(c).append(n)
            }
            i += 2
        } else if (c == '|') {
            out.add(cur.toString())
            cur.clear()
            i += 1
        } else {
            cur.append(c)
            i += 1
        }
    }
    out.add(cur.toString())
    return out
}

// ── 读：按列取，取不到就 null（不抛） ──────────────────────────────────

private fun List<String>.str(i: Int): String = getOrNull(i).orEmpty()

private fun List<String>.dbl(i: Int): Double? =
    getOrNull(i)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

private fun List<String>.int(i: Int): Int? =
    getOrNull(i)?.takeIf { it.isNotEmpty() }?.toIntOrNull()

private fun List<String>.long(i: Int): Long? =
    getOrNull(i)?.takeIf { it.isNotEmpty() }?.toLongOrNull()

private fun List<String>.bool(i: Int): Boolean? = when (getOrNull(i)) {
    "1" -> true
    "0" -> false
    else -> null
}
