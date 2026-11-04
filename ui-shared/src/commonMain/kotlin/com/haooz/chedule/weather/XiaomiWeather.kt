package com.haooz.chedule.weather

import com.haooz.chedule.data.util.parseJsonArray
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString

/**
 * 小米天气（weatherapi.market.xiaomi.com/wtr-v3）免密钥通道。
 *
 * 免配置腿里**字段最全**的一家：实况（含体感/紫外线）+ 逐时 + 7 天逐日 + AQI
 * + 预警 + 分钟级降水文案。缺点是国内才准、接口随时可能变 —— 所以它只是融合的
 * 一路，不是唯一真相。
 *
 * 城市定位走 `location/city/geo`：给定经纬度反查区县名与 `locationKey`
 * （形如 `weathercn:101010100`，后半段正好是中国天气网的区县码，白拿）。
 */
internal object XiaomiWeather {

    private const val BASE = "https://weatherapi.market.xiaomi.com/wtr-v3/"
    private const val APP_KEY = "weather20151024"
    private const val SIGN = "zUFJoAR2ZVrDy1vF3D07"

    /** 反查到的落点：显示名 + 中国天气网区县码。 */
    internal data class Located(val name: String, val areaId: String?)

    internal suspend fun locate(place: WeatherPlace): Located? {
        val url = "${BASE}location/city/geo?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&locale=zh_CN&appKey=$APP_KEY&sign=$SIGN"
        val text = WeatherHttp.getJsonArrayText(url) ?: return null
        val first = try {
            parseJsonArray(text).optJsonObject(0)
        } catch (_: Exception) {
            null
        } ?: return null
        val name = first.optString("name").trim()
        val key = first.optString("locationKey")
        val areaId = key.substringAfter(':', "").takeIf { it.isNotBlank() && it.all(Char::isDigit) }
        if (name.isBlank() && areaId == null) return null
        return Located(name, areaId)
    }

    internal suspend fun fetch(place: WeatherPlace, located: Located): SourceData? {
        val locationKey = located.areaId?.let { "weathercn:$it" } ?: return null
        val url = "${BASE}weather/all?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&isLocated=false&locationKey=${urlEncode(locationKey)}&days=7" +
            "&appKey=$APP_KEY&sign=$SIGN&isGlobal=false&locale=zh_CN"
        val root = WeatherHttp.getJson(url) ?: return null
        val offset = 28_800 // 小米固定返回 +08:00 的时间串，这里只用来解析

        val current = root.optJsonObject("current")
        val now = current?.let {
            val code = it.optString("weather")
            val windDeg = it.optJsonObject("wind")?.optJsonObject("direction").let(::unitValue)
            WeatherNow(
                tempC = unitValue(it.optJsonObject("temperature")),
                feelsLikeC = unitValue(it.optJsonObject("feelsLike")),
                text = chinaWeatherText(code).ifBlank { if (code.isBlank()) "" else "未知" },
                humidity = unitValue(it.optJsonObject("humidity"))?.toInt(),
                pressureHpa = unitValue(it.optJsonObject("pressure"))?.toInt(),
                windDir = windDirFromDeg(windDeg),
                windDirDeg = windDeg?.toInt(),
                windPower = windPowerFromKmh(it.optJsonObject("wind")?.optJsonObject("speed").let(::unitValue)),
                windSpeedKmh = it.optJsonObject("wind")?.optJsonObject("speed").let(::unitValue),
                visibilityKm = unitValue(it.optJsonObject("visibility")),
                uvIndex = it.optString("uvIndex").toDoubleOrNull()?.toInt(),
                isDay = null,
                observedAt = clockOf(it.optString("pubTime")),
            )
        }

        val hourly = parseHourly(root, offset)
        val daily = parseDaily(root)

        val air = root.optJsonObject("aqi")?.let { aqi ->
            val value = aqi.optString("aqi").toDoubleOrNull()?.toInt()
            AirQuality(
                aqi = value,
                level = aqiLevel(value),
                primary = aqi.optString("primary"),
                pm25 = aqi.optString("pm25").toDoubleOrNull(),
                pm10 = aqi.optString("pm10").toDoubleOrNull(),
                o3 = aqi.optString("o3").toDoubleOrNull(),
                no2 = aqi.optString("no2").toDoubleOrNull(),
                so2 = aqi.optString("so2").toDoubleOrNull(),
                co = aqi.optString("co").toDoubleOrNull(),
                tip = aqiTip(value),
            )
        }

        val alerts = root.optJsonArray("alerts")?.let { arr ->
            buildList {
                for (i in 0 until arr.size) {
                    val a = arr.optJsonObject(i) ?: continue
                    val title = a.optString("title").trim()
                    if (title.isBlank()) continue
                    add(
                        WeatherAlert(
                            title = title,
                            type = a.optString("type").trim(),
                            level = a.optString("level").trim(),
                            detail = a.optString("detail").trim(),
                            pubTime = clockOf(a.optString("pubTime")),
                        )
                    )
                }
            }
        }.orEmpty()

        val nowcast = root.optJsonObject("minutely")?.optJsonObject("precipitation")?.let { p ->
            val headline = p.optString("shortDescription").ifBlank { p.optString("description") }
            val detail = p.optString("description")
            if (headline.isBlank() && detail.isBlank()) null
            else RainNowcast(headline = headline, detail = detail)
        }

        if (now == null && hourly.isEmpty() && daily.isEmpty()) return null
        return SourceData(
            id = "XIAOMI",
            label = "小米",
            weight = 1.0,
            placeName = located.name.takeIf { it.isNotBlank() },
            now = now,
            hourly = hourly,
            daily = daily,
            air = air,
            alerts = alerts,
            nowcast = nowcast,
        )
    }

    /** `{unit:"℃", value:"7"}` → 7.0；value 可能是空串（小米自己也没数据）。 */
    private fun unitValue(obj: kotlinx.serialization.json.JsonObject?): Double? =
        obj?.optString("value")?.trim()?.toDoubleOrNull()

    private fun parseHourly(
        root: kotlinx.serialization.json.JsonObject,
        offset: Int,
    ): List<WeatherHour> {
        val h = root.optJsonObject("forecastHourly") ?: return emptyList()
        val temps = h.optJsonObject("temperature")?.optJsonArray("value")
        val codes = h.optJsonObject("weather")?.optJsonArray("value")
        val winds = h.optJsonObject("wind")?.optJsonArray("value")
        val aqis = h.optJsonObject("aqi")?.optJsonArray("value")
        val startSec = parseIsoEpochSec(h.optJsonObject("temperature")?.optString("pubTime"), offset)
            ?: return emptyList()
        val count = temps?.size ?: codes?.size ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(count, 48)) {
                val wind = winds?.optJsonObject(i)
                val codeText = codes?.optString(i).orEmpty()
                add(
                    WeatherHour(
                        epochSec = startSec + i * 3600L,
                        tempC = temps?.optString(i)?.toDoubleOrNull(),
                        text = chinaWeatherText(codeText),
                        windSpeedKmh = wind?.optString("speed")?.toDoubleOrNull(),
                        windDirDeg = wind?.optString("direction")?.toDoubleOrNull()?.toInt(),
                        aqi = aqis?.optString(i)?.toDoubleOrNull()?.toInt(),
                    )
                )
            }
        }
    }

    private fun parseDaily(root: kotlinx.serialization.json.JsonObject): List<WeatherDay> {
        val d = root.optJsonObject("forecastDaily") ?: return emptyList()
        val temps = d.optJsonObject("temperature")?.optJsonArray("value") ?: return emptyList()
        val codes = d.optJsonObject("weather")?.optJsonArray("value")
        val suns = d.optJsonObject("sunRiseSet")?.optJsonArray("value")
        val props = d.optJsonObject("precipitationProbability")?.optJsonArray("value")
        val aqis = d.optJsonObject("aqi")?.optJsonArray("value")
        val windDirs = d.optJsonObject("wind")?.optJsonObject("direction")?.optJsonArray("value")
        val windSpeeds = d.optJsonObject("wind")?.optJsonObject("speed")?.optJsonArray("value")
        // 小米的逐日从「今天」开始，日期靠起始日 + index 推 —— 它不返回 date 字段。
        val baseDate = root.optString("updateTime").toLongOrNull()
            ?.let { localDateKey(it / 1000, 28_800) }
            ?: localDateKey(parseIsoEpochSec(suns?.optJsonObject(0)?.optString("from")) ?: 0L, 28_800)
        val baseDays = daysFromCivil(
            baseDate.substring(0, 4).toIntOrNull() ?: 1970,
            baseDate.substring(5, 7).toIntOrNull() ?: 1,
            baseDate.substring(8, 10).toIntOrNull() ?: 1,
        )
        return buildList {
            for (i in 0 until temps.size) {
                val t = temps.optJsonObject(i) ?: continue
                val code = codes?.optJsonObject(i)
                val sun = suns?.optJsonObject(i)
                add(
                    WeatherDay(
                        date = civilFromDays(baseDays + i).let { (y, m, dd) ->
                            "$y-${m.toString().padStart(2, '0')}-${dd.toString().padStart(2, '0')}"
                        },
                        high = t.optString("from").toDoubleOrNull(),
                        low = t.optString("to").toDoubleOrNull(),
                        dayText = chinaWeatherText(code?.optString("from")),
                        nightText = chinaWeatherText(code?.optString("to")),
                        precipProb = props?.optString(i)?.toDoubleOrNull()?.toInt(),
                        sunrise = clockOf(sun?.optString("from")),
                        sunset = clockOf(sun?.optString("to")),
                        aqi = aqis?.optString(i)?.toDoubleOrNull()?.toInt(),
                        windDir = windDirFromDeg(windDirs?.optJsonObject(i)?.optString("from")?.toDoubleOrNull()),
                        windSpeedKmh = windSpeeds?.optJsonObject(i)?.optString("from")?.toDoubleOrNull(),
                    )
                )
            }
        }
    }
}
