package com.haooz.chedule.weather

/**
 * 多源天气的领域模型（纯 Kotlin，无平台 API）。
 *
 * 字段一律**可空**：三个源各有各的缺口（小米没有能见度、Open-Meteo 没有生活指数、
 * 中国天气网只有当天），融合器靠「谁有谁填」拼出完整快照。宁可留空让 UI 隐藏，
 * 也不要给一个 0 或 "—" 的假值 —— 那是最难查的一类 bug。
 */

/** 一次查询的落点：只要经纬度，城市名由数据源自己反查（三家都会回名称，不必依赖平台 Geocoder）。 */
data class WeatherPlace(
    val latitude: Double,
    val longitude: Double,
    /** 平台侧已知的显示名（可空）；为空时用数据源返回的城市名。 */
    val name: String? = null,
)

/** 实况。 */
data class WeatherNow(
    val tempC: Double? = null,
    val feelsLikeC: Double? = null,
    /** 中文现象文本（"多云"），卡片的图标映射按中文关键词匹配。 */
    val text: String = "",
    val humidity: Int? = null,
    val pressureHpa: Int? = null,
    val windDir: String = "",
    /** 风向角度（0=北，顺时针）。指北针要它 —— 光有"南风"两个字画不出箭头。 */
    val windDirDeg: Int? = null,
    val windPower: String = "",
    val windSpeedKmh: Double? = null,
    val precipMm: Double? = null,
    val visibilityKm: Double? = null,
    val cloudCover: Int? = null,
    val uvIndex: Int? = null,
    val dewPointC: Double? = null,
    val isDay: Boolean? = null,
    /** 观测/发布时间文本（"07:35"），原样展示。 */
    val observedAt: String = "",
)

/** 逐小时。 */
data class WeatherHour(
    /** 自 UTC 纪元起的秒；融合时按它对齐不同源。 */
    val epochSec: Long,
    val tempC: Double? = null,
    val text: String = "",
    val precipProb: Int? = null,
    val precipMm: Double? = null,
    val windSpeedKmh: Double? = null,
    val windDirDeg: Int? = null,
    val humidity: Int? = null,
    val aqi: Int? = null,
    /** 海平面气压。只有 Open-Meteo 逐时给，用来画气压的走势。 */
    val pressureHpa: Int? = null,
    /** 露点。只有 Open-Meteo 逐时给，用来补实况（三家都不在 current 里提供）。 */
    val dewPointC: Double? = null,
    val feelsLikeC: Double? = null,
    val uvIndex: Int? = null,
    val visibilityKm: Double? = null,
)

/** 逐日。 */
data class WeatherDay(
    /** "2026-11-04"（当地日期）。 */
    val date: String,
    val high: Double? = null,
    val low: Double? = null,
    val dayText: String = "",
    val nightText: String = "",
    val precipProb: Int? = null,
    val precipMm: Double? = null,
    /** "06:46"。 */
    val sunrise: String = "",
    val sunset: String = "",
    val uvIndexMax: Int? = null,
    val aqi: Int? = null,
    val windDir: String = "",
    val windSpeedKmh: Double? = null,
    /** 日平均相对湿度。只有 Open-Meteo 逐日给（小米/中国天气网的逐日没有湿度）。 */
    val humidity: Int? = null,
    /** 日均海平面气压，同上。 */
    val pressureHpa: Int? = null,
    /** 日均能见度（km），同上。 */
    val visibilityKm: Double? = null,
)

/** 气象预警。 */
data class WeatherAlert(
    val title: String = "",
    val type: String = "",
    val level: String = "",
    val detail: String = "",
    val pubTime: String = "",
)

/** 生活指数（一条 = 一类）。 */
data class LifeIndex(
    val name: String,
    val level: String = "",
    val tip: String = "",
)

/** 空气质量。 */
data class AirQuality(
    val aqi: Int? = null,
    /** "良" / "轻度污染"。 */
    val level: String = "",
    val primary: String = "",
    val pm25: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null,
    val no2: Double? = null,
    val so2: Double? = null,
    val co: Double? = null,
    val tip: String = "",
)

/** 分钟级降水：[values] 从当前分钟起，每分钟一个强度值。 */
data class RainNowcast(
    val headline: String = "",
    val detail: String = "",
    val values: List<Float> = emptyList(),
    val pubTime: String = "",
)

/**
 * 融合后的完整快照。UI 只读它，不再关心背后有几个源。
 *
 * [sources] 是**实际参与**的源标签（"小米" / "中国天气网" / "Open-Meteo"），
 * 页面底部照实展示 —— 用户有权知道这个温度是谁算的。
 */
data class WeatherSnapshot(
    val placeName: String = "",
    val now: WeatherNow = WeatherNow(),
    val hourly: List<WeatherHour> = emptyList(),
    val daily: List<WeatherDay> = emptyList(),
    val air: AirQuality? = null,
    val alerts: List<WeatherAlert> = emptyList(),
    val indices: List<LifeIndex> = emptyList(),
    val nowcast: RainNowcast? = null,
    val sources: List<String> = emptyList(),
    val fetchedAtEpochMs: Long = 0L,
    /** 当地时区偏移（秒），用于把逐时时间戳转成"18:00"。 */
    val utcOffsetSec: Int = 28_800,
) {
    /** 今日（数组第一项即当地今天，三个源都按当地日期排列）。 */
    val today: WeatherDay? get() = daily.firstOrNull()
}

/** 单个源的抓取结果。融合器的输入，字段与公开模型同构但多一个权重。 */
internal data class SourceData(
    val id: String,
    val label: String,
    val weight: Double,
    val placeName: String? = null,
    val now: WeatherNow? = null,
    val hourly: List<WeatherHour> = emptyList(),
    val daily: List<WeatherDay> = emptyList(),
    val air: AirQuality? = null,
    val alerts: List<WeatherAlert> = emptyList(),
    val indices: List<LifeIndex> = emptyList(),
    val nowcast: RainNowcast? = null,
    /** 当地时区偏移（秒）；逐时对齐与"现在几点"都靠它。 */
    val utcOffsetSec: Int? = null,
)

// ── 天气码 → 中文 ───────────────────────────────────────────────────────

/**
 * 小米/中国天气网共用的国标天气码（前导零已去掉）。
 *
 * 只做**显示文本**映射：卡片图标按中文关键词匹配，所以这里必须给出人话，
 * 而不是把码原样透出去。强度词（"小/中/大"）保留，页面上的差异靠它体现。
 */
internal fun chinaWeatherText(code: String?): String = when (normalizeCode(code)) {
    "0" -> "晴"
    "1" -> "多云"
    "2" -> "阴"
    "3" -> "阵雨"
    "4" -> "雷阵雨"
    "5" -> "冰雹"
    "6" -> "雨夹雪"
    "7" -> "小雨"
    "8" -> "中雨"
    "9" -> "大雨"
    "10" -> "暴雨"
    "11" -> "大暴雨"
    "12" -> "特大暴雨"
    "13" -> "阵雪"
    "14" -> "小雪"
    "15" -> "中雪"
    "16" -> "大雪"
    "17" -> "暴雪"
    "18" -> "雾"
    "19" -> "冻雨"
    "20" -> "沙尘暴"
    "21" -> "小到中雨"
    "22" -> "中到大雨"
    "23" -> "大到暴雨"
    "24" -> "暴雨到大暴雨"
    "25" -> "大暴雨到特大暴雨"
    "26" -> "小到中雪"
    "27" -> "中到大雪"
    "28" -> "大到暴雪"
    "29" -> "浮尘"
    "30" -> "扬沙"
    "31" -> "强沙尘暴"
    "32" -> "浓雾"
    "33" -> "大风"
    "34" -> "小雪"
    "35" -> "雾"
    "49" -> "强浓雾"
    "53" -> "霾"
    "54" -> "中度霾"
    "55" -> "重度霾"
    "56" -> "严重霾"
    "57" -> "大雾"
    "58" -> "特强浓雾"
    "301" -> "雨"
    "302" -> "雪"
    else -> ""
}

/**
 * 天气码归一：只留数字，去掉前导零，但**不能把 "0" 吃成空串**。
 *
 * "0" 是「晴」，是国标码里最常用的一个；`trimStart('0')` 会把它变成 ""，
 * 于是晴天全部显示成空白、图标退到兜底 —— 这类 bug 不会抛异常，只会让页面安静地错。
 * 顺带兼容中国天气网的 "d18"/"n1"（前缀表示昼夜）。
 */
private fun normalizeCode(code: String?): String {
    val digits = code?.filter { it.isDigit() }.orEmpty()
    if (digits.isEmpty()) return ""
    return digits.trimStart('0').ifEmpty { "0" }
}

/** Open-Meteo 的 WMO 天气码 → 中文。分级比国标粗，够用。 */
internal fun wmoWeatherText(code: Int?): String = when (code) {
    0 -> "晴"
    1 -> "晴间多云"
    2 -> "多云"
    3 -> "阴"
    45, 48 -> "雾"
    51, 53, 55 -> "毛毛雨"
    56, 57 -> "冻毛毛雨"
    61 -> "小雨"
    63 -> "中雨"
    65 -> "大雨"
    66, 67 -> "冻雨"
    71 -> "小雪"
    73 -> "中雪"
    75 -> "大雪"
    77 -> "米雪"
    80 -> "阵雨"
    81 -> "强阵雨"
    82 -> "暴雨"
    85, 86 -> "阵雪"
    95 -> "雷阵雨"
    96, 99 -> "雷阵雨伴冰雹"
    else -> ""
}

/** 风向角度 → 中文方位（8 方位，够看）。 */
internal fun windDirFromDeg(deg: Double?): String {
    if (deg == null || deg.isNaN()) return ""
    val dirs = listOf("北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风")
    val idx = (((deg % 360 + 360) % 360) / 45.0).toInt().coerceIn(0, 7)
    return dirs[idx]
}

/**
 * 中文风向 → 角度，[windDirFromDeg] 的反向。
 *
 * 只有中国天气网**不给角度**（它只回 "南风"/"偏北风" 这种字），而指北针没有角度就只能
 * 停在一个装饰性的圆圈上。这里只做字面反解，解不出来返回 null（UI 退回不画箭头）。
 * 匹配顺序必须「长的在前」：`东北风` 里也含 `东风`，先匹配短的会画成 90°。
 */
internal fun windDegFromName(name: String): Int? {
    val n = name.trim()
    if (n.isBlank()) return null
    val table = listOf(
        "西北风" to 315, "东北风" to 45, "西南风" to 225, "东南风" to 135,
        "北风" to 0, "东风" to 90, "南风" to 180, "西风" to 270,
    )
    return table.firstOrNull { n.contains(it.first) }?.second
}

/** 蒲福风级 → "3级"。仅在没有原始风级字段时兜底。 */
internal fun windPowerFromKmh(kmh: Double?): String {
    if (kmh == null || kmh.isNaN()) return ""
    val level = when {
        kmh < 1 -> 0
        kmh < 6 -> 1
        kmh < 12 -> 2
        kmh < 20 -> 3
        kmh < 29 -> 4
        kmh < 39 -> 5
        kmh < 50 -> 6
        kmh < 62 -> 7
        kmh < 75 -> 8
        kmh < 89 -> 9
        kmh < 103 -> 10
        kmh < 118 -> 11
        else -> 12
    }
    return if (level == 0) "静风" else "${level}级"
}

/** AQI 数值 → 等级文本（GB 3095 / HJ 633 口径）。 */
fun aqiLevel(aqi: Int?): String = when {
    aqi == null -> ""
    aqi <= 50 -> "优"
    aqi <= 100 -> "良"
    aqi <= 150 -> "轻度污染"
    aqi <= 200 -> "中度污染"
    aqi <= 300 -> "重度污染"
    else -> "严重污染"
}

/** AQI 建议语（页面底部一句劝）。 */
fun aqiTip(aqi: Int?): String = when {
    aqi == null -> ""
    aqi <= 50 -> "空气很好，随便呼吸"
    aqi <= 100 -> "可以正常户外活动"
    aqi <= 150 -> "敏感人群少在外面久待"
    aqi <= 200 -> "戴口罩，别去跑步"
    else -> "尽量待在室内，关窗"
}
