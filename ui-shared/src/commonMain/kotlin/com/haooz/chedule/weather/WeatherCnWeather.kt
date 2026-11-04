package com.haooz.chedule.weather

import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 中国天气网（d1.weather.com.cn）免密钥通道 —— 中国气象局公共气象服务中心的网页自用数据。
 *
 * 它补的正是另外两家缺的那几块：**能见度、官方 AQI、预警、约 30 项生活指数**。
 * 但接口是 JS 变量不是 JSON（`var dataSK={...};`），所以要先做花括号配对扫描再解析。
 *
 * 只在拿到区县码（[WeatherPlace] 解析出的 `weathercn:101010100`）时工作，
 * 拿不到就整条跳过 —— 它不提供按经纬度查询，硬凑一个码只会拿到别的城市的数据。
 */
internal object WeatherCnWeather {

    private const val REFERER = "https://www.weather.com.cn/"

    internal suspend fun fetch(areaId: String): SourceData? {
        val headers = mapOf("Referer" to REFERER)
        val skText = WeatherHttp.getText("https://d1.weather.com.cn/sk_2d/$areaId.html", headers)
        val idxText = WeatherHttp.getText("https://d1.weather.com.cn/weather_index/$areaId.html", headers)
        if (skText == null && idxText == null) return null

        val sk = skText?.let { extractVar(it, "dataSK") }
        val today = idxText?.let { extractVar(it, "cityDZ")?.optJsonObject("weatherinfo") }
        val alarms = idxText?.let { parseAlarms(extractVar(it, "alarmDZ")) }.orEmpty()
        val indices = idxText?.let { parseIndices(extractVar(it, "dataZS")?.optJsonObject("zs")) }.orEmpty()

        val now = sk?.let {
            WeatherNow(
                tempC = it.optString("temp").toDoubleOrNull(),
                text = it.optString("weather").ifBlank { chinaWeatherText(it.optString("weathercode")) },
                humidity = it.optString("SD").trimEnd('%').toDoubleOrNull()?.toInt(),
                pressureHpa = it.optString("qy").toDoubleOrNull()?.toInt(),
                windDir = it.optString("WD"),
                windPower = it.optString("WS"),
                windSpeedKmh = it.optString("wse").removeSuffix("km/h").toDoubleOrNull(),
                precipMm = it.optString("rain").toDoubleOrNull(),
                visibilityKm = it.optString("njd").removeSuffix("km").toDoubleOrNull(),
                observedAt = it.optString("time"),
            )
        }

        val air = sk?.let {
            val aqi = it.optString("aqi").toDoubleOrNull()?.toInt()
            if (aqi == null) null else AirQuality(
                aqi = aqi,
                level = aqiLevel(aqi),
                primary = "",
                pm25 = it.optString("aqi_pm25").toDoubleOrNull(),
                tip = aqiTip(aqi),
            )
        }

        val daily = if (today == null) emptyList() else listOf(
            WeatherDay(
                date = "",
                dayText = today.optString("weather").trim(),
                nightText = today.optString("weathern").trim(),
                windDir = today.optString("wd").trim(),
            )
        )

        if (now == null && daily.isEmpty() && indices.isEmpty() && air == null) return null
        return SourceData(
            id = "WEATHERCN",
            label = "中国天气网",
            weight = 0.8,
            placeName = sk?.optString("cityname")?.takeIf { it.isNotBlank() },
            now = now,
            daily = daily,
            air = air,
            alerts = alarms,
            indices = indices,
        )
    }

    /**
     * `alarmDZ.w[]` 里是 w1..w9 的位置字段（没有名字，纯约定）：
     * w5=类型、w7=级别、w8=发布时间、w9=正文，w1/w2 拼标题。
     */
    private fun parseAlarms(raw: JsonObject?): List<WeatherAlert> {
        val arr = raw?.optJsonArray("w") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.size) {
                val e = arr.optJsonObject(i) ?: continue
                val w1 = e.optString("w1").trim()
                val w2 = e.optString("w2").trim()
                val type = e.optString("w5").trim()
                val level = e.optString("w7").trim()
                val title = listOf(w1, w2).filter { it.isNotBlank() }.joinToString("")
                    .ifBlank { listOfNotNull(type.ifBlank { null }, level.ifBlank { null }).joinToString(" ") }
                if (title.isBlank()) continue
                add(
                    WeatherAlert(
                        title = title,
                        type = type,
                        level = level,
                        detail = e.optString("w9").trim(),
                        pubTime = e.optString("w8").trim(),
                    )
                )
            }
        }
    }

    /** `dataZS.zs` 是扁平字典：每一项三个键 `<前缀>_name` / `_hint` / `_des_s`。 */
    private fun parseIndices(zs: JsonObject?): List<LifeIndex> {
        if (zs == null) return emptyList()
        return buildList {
            for ((key, value) in zs) {
                if (!key.endsWith("_name")) continue
                val prefix = key.removeSuffix("_name")
                val name = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (name.isBlank()) continue
                add(
                    LifeIndex(
                        name = name,
                        level = zs.optString("${prefix}_hint").trim(),
                        tip = zs.optString("${prefix}_des_s").trim(),
                    )
                )
            }
        }
    }

    /** 从 `var name = {...};` 里取出对象：花括号配对扫描（字符串内的括号不算）。 */
    internal fun extractVar(text: String, name: String): JsonObject? {
        val anchor = text.indexOf("var $name")
        if (anchor < 0) return null
        val start = text.indexOf('{', anchor)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (c == '\\') {
                    i++ // 跳过被转义的字符，别把它当引号
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return try {
                                parseJsonObject(text.substring(start, i + 1))
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }
            }
            i++
        }
        return null
    }
}
