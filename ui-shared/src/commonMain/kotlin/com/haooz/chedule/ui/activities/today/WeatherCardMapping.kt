package com.haooz.chedule.ui.activities.today

import com.haooz.chedule.weather.WeatherSnapshot
import kotlin.math.roundToInt

/**
 * 融合快照 → 卡片要的那一薄层。
 *
 * 卡片只显示一行（图标 + 温度 + 现象 + 一句补充），而快照有几十个字段。
 * 转换写在这里而不是卡片的参数里，是为了让卡片保持「笨」：它不认识多源融合，
 * 只认识五个字段，换数据源不用动 UI。
 *
 * 是 public 不是 internal：`:androidApp` / iOS 宿主各自装配 `rememberWeather`，都要用它。
 */
fun WeatherSnapshot.toCardData(): WeatherData {
    val today = daily.firstOrNull()
    val temp = now.tempC
    if (temp == null && now.text.isBlank()) {
        return WeatherData(loaded = true, apiError = true)
    }
    return WeatherData(
        temperature = temp?.toFloat() ?: Float.NaN,
        weatherType = now.text,
        sunset = today?.sunset.orEmpty(),
        sunrise = today?.sunrise.orEmpty(),
        notice = shortNotice(this),
        loaded = true,
    )
}

/**
 * 卡片那句补充：优先给「今天多少度」，有降水概率就带上，都没有才退回分钟级降水文案。
 *
 * ⚠ 别把 nowcast 全文塞进来 —— 小米那句是「未来两小时不会下雨，您可以放心出门~」，
 * 一行放不下，卡片会用省略号截断在一半的位置，比不给还难看。
 */
private fun shortNotice(snapshot: WeatherSnapshot): String {
    val today = snapshot.daily.firstOrNull()
    val high = today?.high?.roundToInt()
    val low = today?.low?.roundToInt()
    val parts = buildList {
        if (high != null && low != null) add("$high°/$low°")
        val prob = today?.precipProb
        if (prob != null && prob >= 30) add("降水 $prob%")
    }
    if (parts.isNotEmpty()) return parts.joinToString(" ")
    return snapshot.nowcast?.headline?.takeIf { it.isNotBlank() }
        ?: snapshot.now.windDir.takeIf { it.isNotBlank() }
        ?: ""
}
