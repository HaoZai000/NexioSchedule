package com.haooz.chedule.i18n

/**
 * 天气详情页文案。
 *
 * 天气现象本身（"多云"/"小雨"）是**数据**不是文案，留在数据层（`weather/WeatherModels.kt`），
 * 这里只管界面骨架词与单位。
 */
object WeatherStr {

    // ── 数据源 ──
    val title = L("天气", "Weather")
    val updatedAt = L("更新于 %s", "Updated %s")
    val refreshing = L("正在刷新", "Refreshing")
    val tapRefresh = L("点一下重新拉取", "Tap to refresh")
    val loading = L("正在获取天气…", "Loading weather…")
    val failed = L("天气没拿到", "No weather data")
    val failedHint = L("网络或数据源抽风了，再试一次", "Network or the source hiccuped. Try again")
    val retry = L("重试", "Retry")
    val emptyHint = L("还没有天气数据，回今日页下拉一次", "No data yet. Refresh on the Today page")
    val noDataField = L("—", "—")

    // ── 实况 ──
    val feelsLike = L("体感 %s°", "Feels %s°")
    val feelsLikeLabel = L("体感", "Feels like")
    val highLow = L("最高 %s° · 最低 %s°", "H %s° · L %s°")
    val aqiShort = L("空气 %s", "AQI %s")

    // ── 分区标题 ──
    val hourlyTitle = L("24 小时预报", "Next 24 hours")
    val dailyTitle = L("未来 7 天预报", "7-day forecast")
    val nowcastTitle = L("短时降水", "Next 2 hours")
    val rainBig = L("大", "Heavy")
    val rainMedium = L("中", "Moderate")
    val rainSmall = L("小", "Light")
    val rainIn1Hour = L("1小时后", "In 1 h")
    val rainIn2Hours = L("2小时后", "In 2 h")
    val airTitle = L("空气质量", "Air quality")
    val airHourlyTitle = L("24 小时空气", "Air over 24 h")
    val airUnits = L("单位 μg/m³（CO 为 mg/m³）", "μg/m³ (CO: mg/m³)")
    val indexTitle = L("生活指数", "Life indices")
    val detailTitle = L("详细数据", "Details")
    val moreInfo = L("查看更多信息", "See more")
    val alertTitle = L("气象预警", "Alerts")
    val alertIssued = L("%s 发布", "Issued at %s")
    val alertSource = L("来源 · %s", "Source · %s")
    val alertExpand = L("展开全文", "Show all")
    val alertCollapse = L("收起", "Show less")
    val sourceTitle = L("数据来源", "Sources")

    // ── 时间词 ──
    val now = L("现在", "Now")

    /**
     * 逐小时标签：`9:00` / `10:00`（不带前导零）。用钟点而不是「几点」——
     * 一排数字在同一列里对齐，扫视比读汉字快。
     */
    fun hourLabel(hour: Int): String = "$hour:00"

    /** 时间轴上的「现在」游标。 */
    val current = L("当前", "Now")
    val today = L("今天", "Today")
    val tomorrow = L("明天", "Tomorrow")
    val dayAfterTomorrow = L("后天", "In 2 days")
    val weekdaySunday = L("周日", "Sun")
    val weekdayMonday = L("周一", "Mon")
    val weekdayTuesday = L("周二", "Tue")
    val weekdayWednesday = L("周三", "Wed")
    val weekdayThursday = L("周四", "Thu")
    val weekdayFriday = L("周五", "Fri")
    val weekdaySaturday = L("周六", "Sat")

    fun weekday(index: Int): String = when (index) {
        0 -> weekdaySunday()
        1 -> weekdayMonday()
        2 -> weekdayTuesday()
        3 -> weekdayWednesday()
        4 -> weekdayThursday()
        5 -> weekdayFriday()
        else -> weekdaySaturday()
    }

    // ── 指标名 ──
    val humidity = L("湿度", "Humidity")
    val pressure = L("气压", "Pressure")
    val visibility = L("能见度", "Visibility")
    val uvIndex = L("紫外线", "UV index")
    val wind = L("风", "Wind")
    val dewPoint = L("露点", "Dew point")
    val cloudCover = L("云量", "Cloud cover")
    val precipitation = L("降水", "Precipitation")
    val sunrise = L("日出", "Sunrise")
    val sunriseSunset = L("日出 / 日落", "Sunrise / sunset")
    val sunset = L("日落", "Sunset")
    val rainChance = L("降水概率", "Chance of rain")
    val gust = L("阵风", "Gust")

    // ── 单位 ──
    val unitCelsius = L("°C", "°C")
    val unitKm = L("km", "km")
    val unitHpa = L("hPa", "hPa")
    val unitKmh = L("km/h", "km/h")
    val unitMm = L("mm", "mm")
    val unitPercent = L("%", "%")

    // ── 详细数据瓦片：一句话解读 ──

    /** 紫外线一句话（MIUI 口径：档位 + 该怎么做）。 */
    fun uvCaption(uv: Int?): String = when {
        uv == null -> ""
        uv <= 2 -> L("较弱，无需防晒", "Low, no sunscreen")()
        uv <= 5 -> L("中等，建议防晒", "Moderate, use sunscreen")()
        uv <= 7 -> L("较强，注意防晒", "High, protect your skin")()
        uv <= 10 -> L("很强，尽量避开正午", "Very high, avoid midday")()
        else -> L("极强，避免长时间户外", "Extreme, stay indoors")()
    }

    fun humidityCaption(humidity: Int?): String = when {
        humidity == null -> ""
        humidity < 30 -> L("干燥，注意补水", "Dry, drink water")()
        humidity <= 60 -> L("舒适，体感宜人", "Comfortable")()
        humidity <= 80 -> L("偏潮，略显闷", "Humid and muggy")()
        else -> L("潮湿，注意通风", "Very humid, ventilate")()
    }

    /** 露点一句话：露点 16℃ 起开始发闷、24℃ 起汗不容易干。 */
    fun dewCaption(dewC: Double?): String = when {
        dewC == null -> ""
        dewC < 0 -> L("极干，注意补水", "Very dry")()
        dewC < 10 -> L("干爽，体感舒服", "Dry and crisp")()
        dewC < 16 -> L("舒适，体感宜人", "Comfortable")()
        dewC < 20 -> L("开始发闷", "Slightly muggy")()
        dewC < 24 -> L("闷，汗不易干", "Muggy")()
        else -> L("非常闷热", "Very muggy")()
    }

    fun feelsCaption(feelsC: Double?): String = when {
        feelsC == null -> ""
        feelsC < 0 -> L("寒冷，注意保暖", "Freezing, bundle up")()
        feelsC < 10 -> L("偏冷，注意添衣", "Cold, dress warmer")()
        feelsC < 18 -> L("凉爽，体感舒适", "Cool and pleasant")()
        feelsC < 26 -> L("舒适，体感宜人", "Comfortable")()
        feelsC < 30 -> L("偏热，注意防暑", "Warm, stay hydrated")()
        else -> L("炎热，减少外出", "Hot, limit time outside")()
    }

    fun visibilityCaption(km: Double?): String = when {
        km == null -> ""
        km >= 20.0 -> L("通透，视野极佳", "Crystal clear")()
        km >= 10.0 -> L("良好，视野清晰", "Good visibility")()
        km >= 5.0 -> L("一般，略有薄雾", "Fair, slightly hazy")()
        else -> L("较差，注意行车", "Poor visibility")()
    }

    /** 日落瓦片上那句倒计时（"7小时9分钟后"）。 */
    fun inHoursMinutes(hours: Int, minutes: Int): String = when {
        hours <= 0 -> L("%d分钟后", "in %dm")(minutes)
        minutes <= 0 -> L("%d小时后", "in %dh")(hours)
        else -> L("%d小时%d分钟后", "in %dh %dm")(hours, minutes)
    }

    val sunHasSet = L("已日落", "After sunset")
    val pressureLow = L("低", "Low")
    val pressureHigh = L("高", "High")

    // ── 趋势面板 ──
    val trendHourlyTitle = L("未来 24 小时", "Next 24 hours")
    val trendDailyTitle = L("未来 7 天预报", "7-day forecast")
    val trendEmpty = L("数据源只给了当前读数，没有逐时 / 逐日预报", "Only a current reading is available")
    val trendTapHint = L("点开看走势", "Tap for the trend")
    val daylight = L("日照", "Daylight")

    fun trendRange(high: String, low: String): String = L("最高 %s · 最低 %s", "High %s · Low %s")(high, low)


    fun trendTitle(name: String): String = L("%s走势", "%s trend")(name)

    /** 7 天日照行："06:16 → 17:25"。 */
    fun sunSpan(rise: String, set: String): String = L("%s → %s", "%s → %s")(rise, set)

    /** 日照时长："11小时9分"。 */
    fun hoursMinutes(hours: Int, minutes: Int): String = L("%d小时%d分", "%dh %dm")(hours, minutes)

    // ── 空气质量 ──
    val primaryPollutant = L("首要污染物", "Main pollutant")
    val primaryPollutantFormat = L("首要污染物 %s", "Main pollutant %s")

    fun primaryPollutantValue(name: String): String = primaryPollutantFormat(name)
    val aqiMissing = L("数据源没有给 AQI，只列浓度", "No AQI from sources; concentrations only")

    // ── 底部详情面板 ──
    val daytime = L("白天", "Day")
    val nighttime = L("夜间", "Night")
    val allDaysTitle = L("每日详情", "Daily details")

    fun monthDay(month: Int, day: Int): String = L("%d月%d日", "%d/%d")(month, day)

    // ── 其它 ──
    val emptyIndices = L("这个位置的生活指数暂时没有", "No life indices for this location")
    val multiSourceNote = L("多家源加权融合，温度取中位数", "Weighted blend; temperature is a weighted median")
    val singleSourceNote = L("单一数据源，未融合", "Single source, not blended")

    // ── 设置页：数据源选项 ──
    val sourceFusion = L("多源融合（推荐）", "Multi-source blend (recommended)")
    val sourceXiaomi = L("小米天气", "Xiaomi")
    val sourceWeatherCn = L("中国天气网", "Weather.com.cn")
    val sourceOpenMeteo = L("Open-Meteo", "Open-Meteo")
}