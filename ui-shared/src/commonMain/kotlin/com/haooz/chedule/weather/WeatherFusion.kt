package com.haooz.chedule.weather

import kotlin.math.roundToInt

/**
 * 融合器：把 2~3 个源拼成一份完整快照。
 *
 * 规则只有四条，别加第五条 —— 每多一条都要问「为什么这个字段归它管」：
 *
 * 1. **温度/体感取加权中位数**，不是加权平均。三个源里有一个抽风（换季时常见差 5℃），
 *    平均值会把它摊进每个人的体感，中位数直接把它扔掉。
 * 2. **其余标量「谁有谁填」**：按权重从高到低覆盖空值，主源已有的一律不动。
 * 3. **现象文本跟主源**（小米的中文比模型翻译准），主源空才降级。
 * 4. **逐时/逐日按时间对齐**后补洞 —— 只补 null，不覆盖已有的。
 *
 * 输出里 [WeatherSnapshot.sources] 记录真正出力的源，页面照实展示。
 */
internal object WeatherFusion {

    fun merge(
        place: WeatherPlace,
        sources: List<SourceData>,
        nowEpochSec: Long,
        fetchedAt: Long,
    ): WeatherSnapshot? {
        if (sources.isEmpty()) return null
        val ordered = sources.sortedByDescending { it.weight }
        val offset = ordered.firstNotNullOfOrNull { it.utcOffsetSec } ?: 28_800

        val now = mergeNow(ordered)
        val hourly = mergeHourly(ordered)
        val daily = mergeDaily(ordered)
        val air = mergeAir(ordered)
        val alerts = mergeAlerts(ordered.flatMap { it.alerts })
        val indices = ordered.firstNotNullOfOrNull { src -> src.indices.takeIf { it.isNotEmpty() } }.orEmpty()
        val nowcast = ordered.firstNotNullOfOrNull { it.nowcast }
        val placeName = place.name?.takeIf(String::isNotBlank)
            ?: ordered.firstNotNullOfOrNull { it.placeName?.takeIf(String::isNotBlank) }
            ?: ""

        return WeatherSnapshot(
            placeName = placeName,
            now = now,
            hourly = hourly,
            daily = daily,
            air = air,
            alerts = alerts,
            indices = indices,
            nowcast = nowcast,
            sources = ordered.filter { it.now != null || it.hourly.isNotEmpty() || it.daily.isNotEmpty() }
                .map { it.label },
            fetchedAtEpochMs = fetchedAt,
            utcOffsetSec = offset,
        ).let { snapshot ->
            // 实况的露点三家都不给，但逐时有 —— 拿当前这小时的补上，
            // 否则「露点」那格永远不出现（页面按"有才显示"处理，缺了就静默消失）
            val dew = snapshot.now.dewPointC ?: snapshot.hourly.firstOrNull()?.dewPointC
            snapshot.copy(
                now = snapshot.now.copy(
                    isDay = snapshot.now.isDay ?: resolveIsDay(snapshot, nowEpochSec),
                    dewPointC = dew,
                )
            )
        }
    }

    // ── 实况 ──────────────────────────────────────────────────────────

    private fun mergeNow(ordered: List<SourceData>): WeatherNow {
        val nows = ordered.mapNotNull { it.now }
        if (nows.isEmpty()) return WeatherNow()
        val base = nows.first()
        // 权重跟源绑定：nows 与 ordered 的顺序一致（mapNotNull 保序）
        val weighted = ordered.filter { it.now != null }.map { it.now!! to it.weight }

        return base.copy(
            tempC = weightedMedian(weighted.mapNotNull { (n, w) -> n.tempC?.let { it to w } }) ?: base.tempC,
            feelsLikeC = weightedMedian(weighted.mapNotNull { (n, w) -> n.feelsLikeC?.let { it to w } })
                ?: base.feelsLikeC,
            text = base.text.ifBlank { nows.firstNotNullOfOrNull { it.text.takeIf(String::isNotBlank) }.orEmpty() },
            humidity = base.humidity ?: fill(nows) { it.humidity },
            pressureHpa = base.pressureHpa ?: fill(nows) { it.pressureHpa },
            windDir = base.windDir.ifBlank { fill(nows) { it.windDir.takeIf(String::isNotBlank) }.orEmpty() },
            windDirDeg = base.windDirDeg ?: fill(nows) { it.windDirDeg }
                // 只有中国天气网给字不给角度时，从字面反解一个（指北针不能空转）
                ?: windDegFromName(base.windDir),
            windPower = base.windPower.ifBlank { fill(nows) { it.windPower.takeIf(String::isNotBlank) }.orEmpty() },
            windSpeedKmh = base.windSpeedKmh ?: fill(nows) { it.windSpeedKmh },
            precipMm = base.precipMm ?: fill(nows) { it.precipMm },
            visibilityKm = base.visibilityKm ?: fill(nows) { it.visibilityKm },
            cloudCover = base.cloudCover ?: fill(nows) { it.cloudCover },
            uvIndex = base.uvIndex ?: fill(nows) { it.uvIndex },
            dewPointC = base.dewPointC ?: fill(nows) { it.dewPointC },
            observedAt = base.observedAt.ifBlank { fill(nows) { it.observedAt.takeIf(String::isNotBlank) }.orEmpty() },
        )
    }

    /** 逐个源找第一个非空值（[nows] 已按权重降序）。 */
    private fun <T : Any> fill(nows: List<WeatherNow>, pick: (WeatherNow) -> T?): T? =
        nows.firstNotNullOfOrNull(pick)

    // ── 逐时 ──────────────────────────────────────────────────────────

    /**
     * 以「条目最多」的源为骨架（小米给到逐时，Open-Meteo 给 24 条），
     * 再按**整点**对齐补其他源的缺字段。
     */
    private fun mergeHourly(ordered: List<SourceData>): List<WeatherHour> {
        val withHourly = ordered.filter { it.hourly.isNotEmpty() }
        if (withHourly.isEmpty()) return emptyList()
        val base = withHourly.maxByOrNull { it.hourly.size }!!
        val byHour = withHourly
            .filter { it !== base }
            .flatMap { it.hourly }
            .groupBy { floorDiv(it.epochSec, 3600L) }

        return base.hourly.map { hour ->
            val others = byHour[floorDiv(hour.epochSec, 3600L)].orEmpty()
            hour.copy(
                tempC = hour.tempC ?: others.firstNotNullOfOrNull { it.tempC },
                text = hour.text.ifBlank { others.firstNotNullOfOrNull { it.text.takeIf(String::isNotBlank) }.orEmpty() },
                precipProb = hour.precipProb ?: others.firstNotNullOfOrNull { it.precipProb },
                precipMm = hour.precipMm ?: others.firstNotNullOfOrNull { it.precipMm },
                windSpeedKmh = hour.windSpeedKmh ?: others.firstNotNullOfOrNull { it.windSpeedKmh },
                windDirDeg = hour.windDirDeg ?: others.firstNotNullOfOrNull { it.windDirDeg },
                humidity = hour.humidity ?: others.firstNotNullOfOrNull { it.humidity },
                aqi = hour.aqi ?: others.firstNotNullOfOrNull { it.aqi },
                pressureHpa = hour.pressureHpa ?: others.firstNotNullOfOrNull { it.pressureHpa },
                dewPointC = hour.dewPointC ?: others.firstNotNullOfOrNull { it.dewPointC },
                feelsLikeC = hour.feelsLikeC ?: others.firstNotNullOfOrNull { it.feelsLikeC },
                uvIndex = hour.uvIndex ?: others.firstNotNullOfOrNull { it.uvIndex },
                visibilityKm = hour.visibilityKm ?: others.firstNotNullOfOrNull { it.visibilityKm },
            )
        }.sortedBy { it.epochSec }
    }

    // ── 逐日 ──────────────────────────────────────────────────────────

    /** 按 `YYYY-MM-DD` 对齐：条目多的当骨架，缺的字段从其他源同一天补。 */
    private fun mergeDaily(ordered: List<SourceData>): List<WeatherDay> {
        val withDaily = ordered.filter { it.daily.any { d -> d.date.length == 10 } }
        if (withDaily.isEmpty()) return emptyList()
        val base = withDaily.maxByOrNull { it.daily.count { d -> d.date.length == 10 } }!!
        val othersByDate = withDaily
            .filter { it !== base }
            .flatMap { it.daily }
            .filter { it.date.length == 10 }
            .groupBy { it.date }

        return base.daily.filter { it.date.length == 10 }.map { day ->
            val others = othersByDate[day.date].orEmpty()
            val high = day.high ?: others.firstNotNullOfOrNull { it.high }
            val low = day.low ?: others.firstNotNullOfOrNull { it.low }
            day.copy(
                high = high,
                low = low?.takeIf { l -> high == null || l <= high },
                dayText = day.dayText.ifBlank { others.firstNotNullOfOrNull { it.dayText.takeIf(String::isNotBlank) }.orEmpty() },
                nightText = day.nightText.ifBlank { others.firstNotNullOfOrNull { it.nightText.takeIf(String::isNotBlank) }.orEmpty() },
                precipProb = day.precipProb ?: others.firstNotNullOfOrNull { it.precipProb },
                precipMm = day.precipMm ?: others.firstNotNullOfOrNull { it.precipMm },
                sunrise = day.sunrise.ifBlank { others.firstNotNullOfOrNull { it.sunrise.takeIf(String::isNotBlank) }.orEmpty() },
                sunset = day.sunset.ifBlank { others.firstNotNullOfOrNull { it.sunset.takeIf(String::isNotBlank) }.orEmpty() },
                uvIndexMax = day.uvIndexMax ?: others.firstNotNullOfOrNull { it.uvIndexMax },
                aqi = day.aqi ?: others.firstNotNullOfOrNull { it.aqi },
                windDir = day.windDir.ifBlank { others.firstNotNullOfOrNull { it.windDir.takeIf(String::isNotBlank) }.orEmpty() },
                windSpeedKmh = day.windSpeedKmh ?: others.firstNotNullOfOrNull { it.windSpeedKmh },
                humidity = day.humidity ?: others.firstNotNullOfOrNull { it.humidity },
                pressureHpa = day.pressureHpa ?: others.firstNotNullOfOrNull { it.pressureHpa },
                visibilityKm = day.visibilityKm ?: others.firstNotNullOfOrNull { it.visibilityKm },
            )
        }.sortedBy { it.date }
    }

    // ── 空气质量 ──────────────────────────────────────────────────────

    /**
     * AQI 数字只认**官方口径**的源（小米拿 CNEMC、中国天气网是气象局），
     * 污染物浓度谁有谁填。Open-Meteo 的浓度是国内没有的补充，但它没有 AQI。
     */
    private fun mergeAir(ordered: List<SourceData>): AirQuality? {
        val airs = ordered.mapNotNull { it.air }
        if (airs.isEmpty()) return null
        val official = airs.firstOrNull { it.aqi != null }
        return AirQuality(
            aqi = official?.aqi,
            level = official?.level?.ifBlank { aqiLevel(official.aqi) }.orEmpty(),
            primary = official?.primary.orEmpty(),
            pm25 = airs.firstNotNullOfOrNull { it.pm25 },
            pm10 = airs.firstNotNullOfOrNull { it.pm10 },
            o3 = airs.firstNotNullOfOrNull { it.o3 },
            no2 = airs.firstNotNullOfOrNull { it.no2 },
            so2 = airs.firstNotNullOfOrNull { it.so2 },
            co = airs.firstNotNullOfOrNull { it.co },
            tip = official?.tip?.ifBlank { aqiTip(official.aqi) }.orEmpty(),
        )
    }

    // ── 昼夜判定 ──────────────────────────────────────────────────────

    /** 没有源直接给 isDay 时，用今天的日出日落和当前时刻比。 */
    private fun resolveIsDay(snapshot: WeatherSnapshot, nowEpochSec: Long): Boolean? {
        val today = snapshot.daily.firstOrNull() ?: return null
        if (today.sunrise.isBlank() || today.sunset.isBlank()) return null
        val nowClock = localHourMinute(nowEpochSec, snapshot.utcOffsetSec)
        fun minutes(c: String): Int =
            (c.substringBefore(':').toIntOrNull() ?: return -1) * 60 + (c.substringAfter(':').toIntOrNull() ?: 0)
        val nowM = minutes(nowClock)
        val riseM = minutes(today.sunrise)
        val setM = minutes(today.sunset)
        if (nowM < 0 || riseM < 0 || setM < 0) return null
        return nowM in riseM..setM
    }

    // ── 加权中位数 ────────────────────────────────────────────────────

    /**
     * @param pairs (数值, 权重)，权重必须 > 0
     * 空输入返回 null；单值直接返回。偶数个时取中间两点的均值（与普通中位数一致）。
     */
    internal fun weightedMedian(pairs: List<Pair<Double, Double>>): Double? {
        if (pairs.isEmpty()) return null
        if (pairs.size == 1) return pairs[0].first
        val sorted = pairs.sortedBy { it.first }
        val total = sorted.sumOf { it.second }
        if (total <= 0.0) return sorted[sorted.size / 2].first
        val half = total / 2.0
        var acc = 0.0
        for ((value, weight) in sorted) {
            acc += weight
            if (acc >= half) return value
        }
        return sorted.last().first
    }

    /** 给页面用：把温度四舍五入成整数（页面上一律整度，小数没意义）。 */
    internal fun roundedC(value: Double?): Int? = value?.roundToInt()
}
