package com.haooz.chedule.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 落盘缓存编解码的回归测试。
 *
 * 这份格式是手写的，字段顺序一旦和解析端错位，症状是「天气数据安静地串行」—— 温度跑到湿度里、
 * 风力显示成城市名，不抛任何异常。所以必须逐字段往返校验一遍。
 */
class WeatherCacheTest {

    private fun sample(): WeatherSnapshot = WeatherSnapshot(
        placeName = "杭州",
        now = WeatherNow(
            tempC = 18.5,
            feelsLikeC = 17.0,
            text = "多云",
            humidity = 62,
            pressureHpa = 1008,
            windDir = "东北风",
            windDirDeg = 45,
            windPower = "3级",
            windSpeedKmh = 14.2,
            precipMm = 0.0,
            visibilityKm = 12.0,
            cloudCover = 40,
            uvIndex = 3,
            dewPointC = 11.1,
            isDay = true,
            observedAt = "07:35",
        ),
        hourly = listOf(
            WeatherHour(
                epochSec = 1_760_000_000L,
                tempC = 19.0,
                text = "晴",
                precipProb = 10,
                precipMm = null,
                windSpeedKmh = 9.5,
                windDirDeg = 90,
                humidity = 55,
                aqi = 42,
                pressureHpa = 1007,
                dewPointC = 9.9,
                feelsLikeC = 18.0,
                uvIndex = 4,
                visibilityKm = 15.0,
            ),
        ),
        daily = listOf(
            WeatherDay(
                date = "2026-11-11",
                high = 22.0,
                low = 13.0,
                dayText = "多云",
                nightText = "晴",
                precipProb = 20,
                precipMm = 0.0,
                sunrise = "06:26",
                sunset = "17:05",
                uvIndexMax = 5,
                aqi = 48,
                windDir = "东风",
                windSpeedKmh = 11.0,
                humidity = 58,
                pressureHpa = 1009,
                visibilityKm = 14.0,
            ),
        ),
        air = AirQuality(
            aqi = 48,
            level = "优",
            primary = "—",
            pm25 = 22.0,
            pm10 = 40.0,
            o3 = 60.0,
            no2 = 18.0,
            so2 = 4.0,
            co = 0.6,
            tip = "空气很好，随便呼吸",
        ),
        alerts = listOf(
            WeatherAlert(
                title = "大风蓝色预警",
                type = "大风",
                level = "蓝色",
                // 详情里带换行与竖线，专门用来验转义
                detail = "预计 24 小时内 | 阵风 7 级\n请注意防范",
                pubTime = "2026-11-11 06:00",
            ),
        ),
        indices = listOf(
            LifeIndex(name = "穿衣", level = "较冷", tip = "建议穿外套"),
            LifeIndex(name = "洗车", level = "不宜", tip = ""),
        ),
        nowcast = RainNowcast(
            headline = "未来两小时无降水",
            detail = "安心出门",
            values = listOf(0f, 0.1f, 0.3f),
            pubTime = "07:30",
        ),
        sources = listOf("小米", "中国天气网"),
        fetchedAtEpochMs = 1_760_000_000_000L,
        utcOffsetSec = 28_800,
    )

    @Test
    fun roundTripKeepsEveryField() {
        val original = sample()
        val restored = decodeWeatherSnapshot(encodeWeatherSnapshot(original))
        assertEquals(original, restored)
    }

    @Test
    fun emptySnapshotSurvives() {
        val original = WeatherSnapshot()
        assertEquals(original, decodeWeatherSnapshot(encodeWeatherSnapshot(original)))
    }

    @Test
    fun unknownVersionIsDiscarded() {
        assertNull(decodeWeatherSnapshot("0\nPLACE|杭州|28800|1"))
        assertNull(decodeWeatherSnapshot(""))
        assertNull(decodeWeatherSnapshot("1\nGARBAGE"))
    }
}
