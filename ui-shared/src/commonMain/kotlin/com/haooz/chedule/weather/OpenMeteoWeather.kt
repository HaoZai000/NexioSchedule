package com.haooz.chedule.weather

import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import kotlinx.serialization.json.JsonObject

/**
 * Open-Meteo（免密钥、全球、无广告）—— 融合里唯一「哪都能用」的一路。
 *
 * 它给的是**数值模式预报**（ECMWF/GFS 那一批），不是观测：所以实况精度不如小米，
 * 但逐小时连续、全球覆盖、字段口径统一，天生适合给国内源补洞。
 *
 * 两请求：`/v1/forecast`（实况+逐时+逐日）与 `/v1/air-quality`（污染物浓度）。
 * 空气质量只取**浓度**，不取 AQI：它的 `us_aqi` 是美标，和中国 AQI 不是一个刻度，
 * 混进同一行就是造一个没人能解释的数字。AQI 交给小米/中国天气网，拿不到就不显示。
 */
internal object OpenMeteoWeather {

    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val AIR = "https://air-quality-api.open-meteo.com/v1/air-quality"

    private const val CURRENT_FIELDS =
        "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code," +
            "cloud_cover,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
            // 下面三项小米/中国天气网也有，但都挂掉时（或者单飞 Open-Meteo 时）
            // 页面的紫外线、能见度、露点会整格消失 —— 这里补上就永远有数
            "uv_index,visibility,dew_point_2m"

    private const val HOURLY_FIELDS =
        "temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code," +
            "wind_speed_10m,wind_direction_10m,relative_humidity_2m,visibility,uv_index,dew_point_2m,cloud_cover," +
            // 逐时气压**必须用 surface_pressure**：小米/中国天气网给的是本站气压（约 1008），
            // pressure_msl 是海平面气压（约 1019）。混着用会出现"表盘 1008、曲线 1019"的图，
            // 差的那 11 hPa 全是海拔，谁也不信。两个都取，是为了算出这个固定差去换算逐日。
            "surface_pressure,pressure_msl"

    private const val DAILY_FIELDS =
        "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum," +
            "precipitation_probability_max,uv_index_max,wind_speed_10m_max,wind_direction_10m_dominant," +
            // 逐日的湿度/气压/能见度只有它有（小米和中国天气网的逐日都没有这三项），
            // 少这三项，详细数据里的「未来 7 天」就只剩温度和紫外线
            "relative_humidity_2m_mean,pressure_msl_mean,visibility_mean"

    internal suspend fun fetch(place: WeatherPlace, nowEpochSec: Long): SourceData? {
        val url = "$FORECAST?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=$CURRENT_FIELDS&hourly=$HOURLY_FIELDS&daily=$DAILY_FIELDS" +
            "&timezone=auto&forecast_days=7&wind_speed_unit=kmh"
        val root = WeatherHttp.getJson(url) ?: return null
        val offset = root.optString("utc_offset_seconds").toIntOrNull() ?: 28_800

        val current = root.optJsonObject("current")
        val now = current?.let {
            // 本站气压（surface_pressure），不是海平面气压 —— 见 HOURLY_FIELDS 的说明
            val pressure = it.optString("surface_pressure").toDoubleOrNull()
            val windDeg = it.optString("wind_direction_10m").toDoubleOrNull()?.toInt()
            WeatherNow(
                tempC = it.optString("temperature_2m").toDoubleOrNull(),
                feelsLikeC = it.optString("apparent_temperature").toDoubleOrNull(),
                text = wmoWeatherText(it.optString("weather_code").toIntOrNull()),
                humidity = it.optString("relative_humidity_2m").toDoubleOrNull()?.toInt(),
                pressureHpa = pressure?.toInt(),
                windDir = windDirFromDeg(windDeg?.toDouble()),
                windDirDeg = windDeg,
                windPower = windPowerFromKmh(it.optString("wind_speed_10m").toDoubleOrNull()),
                windSpeedKmh = it.optString("wind_speed_10m").toDoubleOrNull(),
                precipMm = it.optString("precipitation").toDoubleOrNull(),
                cloudCover = it.optString("cloud_cover").toDoubleOrNull()?.toInt(),
                // OM 的能见度是米
                visibilityKm = it.optString("visibility").toDoubleOrNull()?.div(1000.0),
                uvIndex = it.optString("uv_index").toDoubleOrNull()?.toInt(),
                dewPointC = it.optString("dew_point_2m").toDoubleOrNull(),
                isDay = it.optString("is_day").toIntOrNull()?.let { d -> d == 1 },
                observedAt = clockOf(it.optString("time")),
            )
        }

        val air = fetchAir(place)

        val hourly = parseHourly(root, offset, nowEpochSec)
        val daily = parseDaily(root, surfaceDelta(root))
        if (now == null && hourly.isEmpty() && daily.isEmpty()) return null
        return SourceData(
            id = "OPEN-METEO",
            label = "Open-Meteo",
            weight = 0.85,
            now = now,
            hourly = hourly,
            daily = daily,
            air = air,
        )
    }

    private suspend fun fetchAir(place: WeatherPlace): AirQuality? {
        val url = "$AIR?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone&timezone=auto"
        val current = WeatherHttp.getJson(url)?.optJsonObject("current") ?: return null
        val pm25 = current.optString("pm2_5").toDoubleOrNull()
        val pm10 = current.optString("pm10").toDoubleOrNull()
        if (pm25 == null && pm10 == null) return null
        return AirQuality(
            aqi = null, // 刻度不同，不混用；AQI 由小米/中国天气网提供
            level = "",
            pm25 = pm25,
            pm10 = pm10,
            o3 = current.optString("ozone").toDoubleOrNull(),
            no2 = current.optString("nitrogen_dioxide").toDoubleOrNull(),
            so2 = current.optString("sulphur_dioxide").toDoubleOrNull(),
            // OM 的 CO 是 μg/m³，小米/国标口径是 mg/m³：这里就换算掉。
            // 不换算的话，小米挂了之后「CO 3583 mg/m³」会直接显示出来 —— 差 1000 倍，
            // 而且不会报错，是最难发现的那种脏数据。
            co = current.optString("carbon_monoxide").toDoubleOrNull()?.div(1000.0),
        )
    }

    /** 逐时取「当前小时起 24 条」——模型给的是从今天 0 点开始的整段，前面要裁掉。 */
    private fun parseHourly(root: JsonObject, offset: Int, nowEpochSec: Long): List<WeatherHour> {
        val h = root.optJsonObject("hourly") ?: return emptyList()
        val times = h.optJsonArray("time") ?: return emptyList()
        val temps = h.optJsonArray("temperature_2m")
        val codes = h.optJsonArray("weather_code")
        val probs = h.optJsonArray("precipitation_probability")
        val precips = h.optJsonArray("precipitation")
        val winds = h.optJsonArray("wind_speed_10m")
        val dirs = h.optJsonArray("wind_direction_10m")
        val hums = h.optJsonArray("relative_humidity_2m")
        val dews = h.optJsonArray("dew_point_2m")
        val feels = h.optJsonArray("apparent_temperature")
        val uvs = h.optJsonArray("uv_index")
        val vis = h.optJsonArray("visibility")
        val pressures = h.optJsonArray("surface_pressure")
        val currentHour = floorDiv(nowEpochSec, 3600L)
        return buildList {
            for (i in 0 until times.size) {
                val epoch = parseIsoEpochSec(times.optString(i), offset) ?: continue
                if (floorDiv(epoch, 3600L) < currentHour) continue
                add(
                    WeatherHour(
                        epochSec = epoch,
                        tempC = temps?.optString(i)?.toDoubleOrNull(),
                        text = wmoWeatherText(codes?.optString(i)?.toIntOrNull()),
                        precipProb = probs?.optString(i)?.toDoubleOrNull()?.toInt(),
                        precipMm = precips?.optString(i)?.toDoubleOrNull(),
                        windSpeedKmh = winds?.optString(i)?.toDoubleOrNull(),
                        windDirDeg = dirs?.optString(i)?.toDoubleOrNull()?.toInt(),
                        humidity = hums?.optString(i)?.toDoubleOrNull()?.toInt(),
                        dewPointC = dews?.optString(i)?.toDoubleOrNull(),
                        feelsLikeC = feels?.optString(i)?.toDoubleOrNull(),
                        uvIndex = uvs?.optString(i)?.toDoubleOrNull()?.toInt(),
                        // OM 的能见度是米
                        visibilityKm = vis?.optString(i)?.toDoubleOrNull()?.div(1000.0),
                        pressureHpa = pressures?.optString(i)?.toDoubleOrNull()?.toInt(),
                    )
                )
                if (size >= 24) break
            }
        }
    }

    /**
     * 本站气压 − 海平面气压 = 站点海拔造成的固定差（约 −11 hPa / 100m）。
     *
     * 逐日接口**没有** `surface_pressure_mean`，只有海平面气压。拿这一个差值把逐日挪到
     * 本站口径上，页面上「气压 1008」和「未来 7 天 1007/1009」才是同一把尺子。
     * 差值取第一条两条曲线都在的时刻 —— 它是海拔常数，不随时间变。
     */
    private fun surfaceDelta(root: JsonObject): Double? {
        val h = root.optJsonObject("hourly") ?: return null
        val surface = h.optJsonArray("surface_pressure") ?: return null
        val msl = h.optJsonArray("pressure_msl") ?: return null
        for (i in 0 until minOf(surface.size, msl.size)) {
            val a = surface.optString(i).toDoubleOrNull() ?: continue
            val b = msl.optString(i).toDoubleOrNull() ?: continue
            return a - b
        }
        return null
    }

    private fun parseDaily(root: JsonObject, pressureDelta: Double?): List<WeatherDay> {
        val d = root.optJsonObject("daily") ?: return emptyList()
        val dates = d.optJsonArray("time") ?: return emptyList()
        val codes = d.optJsonArray("weather_code")
        val highs = d.optJsonArray("temperature_2m_max")
        val lows = d.optJsonArray("temperature_2m_min")
        val sunrises = d.optJsonArray("sunrise")
        val sunsets = d.optJsonArray("sunset")
        val precSums = d.optJsonArray("precipitation_sum")
        val precProbs = d.optJsonArray("precipitation_probability_max")
        val uvMax = d.optJsonArray("uv_index_max")
        val windMax = d.optJsonArray("wind_speed_10m_max")
        val windDirs = d.optJsonArray("wind_direction_10m_dominant")
        val hums = d.optJsonArray("relative_humidity_2m_mean")
        val pressures = d.optJsonArray("pressure_msl_mean")
        val vis = d.optJsonArray("visibility_mean")
        return buildList {
            for (i in 0 until dates.size) {
                val date = dates.optString(i)
                if (date.length < 10) continue
                add(
                    WeatherDay(
                        date = date.substring(0, 10),
                        high = highs?.optString(i)?.toDoubleOrNull(),
                        low = lows?.optString(i)?.toDoubleOrNull(),
                        dayText = wmoWeatherText(codes?.optString(i)?.toIntOrNull()),
                        precipProb = precProbs?.optString(i)?.toDoubleOrNull()?.toInt(),
                        precipMm = precSums?.optString(i)?.toDoubleOrNull(),
                        sunrise = clockOf(sunrises?.optString(i)),
                        sunset = clockOf(sunsets?.optString(i)),
                        uvIndexMax = uvMax?.optString(i)?.toDoubleOrNull()?.toInt(),
                        windDir = windDirFromDeg(windDirs?.optString(i)?.toDoubleOrNull()),
                        windSpeedKmh = windMax?.optString(i)?.toDoubleOrNull(),
                        humidity = hums?.optString(i)?.toDoubleOrNull()?.toInt(),
                        // 逐日只有海平面气压，换算成本站口径（见 surfaceDelta）
                        pressureHpa = pressures?.optString(i)?.toDoubleOrNull()
                            ?.let { msl -> (msl + (pressureDelta ?: 0.0)).toInt() },
                        // OM 的能见度是米
                        visibilityKm = vis?.optString(i)?.toDoubleOrNull()?.div(1000.0),
                    )
                )
            }
        }
    }
}
