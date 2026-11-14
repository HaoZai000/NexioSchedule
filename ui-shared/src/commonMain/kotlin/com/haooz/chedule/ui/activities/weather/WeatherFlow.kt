/** 天气页的流光参数：由「现象 × 时段」共同决定配色、光斑形态与流速。 */
package com.haooz.chedule.ui.activities.weather

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.haooz.chedule.ui.effects.background.BgEffectConfig
import com.haooz.chedule.weather.WeatherSnapshot
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 一天四个时段：日出前/后各留 30 分钟过渡，晨、暮各 90 分钟。 */
internal enum class SkyPhase { Dawn, Day, Dusk, Night }

internal enum class WeatherFlowKind { Clear, Cloudy, Rain, Snow, Frost, Thunder, Fog, Dust }

/**
 * 现象 → 流光种类。
 *
 * 顺序从重到轻：「雷阵雨」必须判成雷，「雨夹雪」判成雪，否则会被更常见的雨吃掉。
 * 与 [weatherSky] 用同一套关键词，保证顶部天光和整页流光同源。
 */
internal fun weatherFlowKind(text: String): WeatherFlowKind = when {
    text.contains("雷") -> WeatherFlowKind.Thunder
    // 冰粒/冻雨走结霜效果；「雨夹雪」没有冰字，仍归到雪
    text.contains("冻") || text.contains("冰") -> WeatherFlowKind.Frost
    text.contains("雪") -> WeatherFlowKind.Snow
    text.contains("雨") -> WeatherFlowKind.Rain
    text.contains("雾") || text.contains("霾") -> WeatherFlowKind.Fog
    text.contains("沙") || text.contains("尘") -> WeatherFlowKind.Dust
    text.contains("云") || text.contains("阴") -> WeatherFlowKind.Cloudy
    else -> WeatherFlowKind.Clear
}

/**
 * 取当前时段；没有数据时按本地时钟兜底（进页首帧 / 拉取失败也要有对的底色）。
 */
internal fun skyPhaseOrNow(snapshot: WeatherSnapshot?): SkyPhase {
    if (snapshot != null) return skyPhase(snapshot)
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val minute = now.hour * 60 + now.minute
    return when {
        minute < 6 * 60 || minute >= 19 * 60 -> SkyPhase.Night
        minute < 8 * 60 -> SkyPhase.Dawn
        minute >= 17 * 60 -> SkyPhase.Dusk
        else -> SkyPhase.Day
    }
}

/**
 * 一套天气流光。
 *
 * - [config] 直接喂给 [BgEffectConfig.Config]：配色是 6 色轮换，三次取色错位形成循环。
 * - [timeScale] 越小越慢，天气页整体压到关于页的 1/4 ~ 1/3。
 */
internal class WeatherFlowSpec(
    val kind: WeatherFlowKind,
    val phase: SkyPhase,
) {
    private val motion = motionOf(kind)

    val timeScale: Float = motion.timeScale * phaseTimeScale(phase)

    val config: BgEffectConfig.Config = run {
        val wheel = wheelOf(kind).map { phaseColor(it, phase) }
        val alpha = phaseAlpha(phase)
        BgEffectConfig.Config(
            points = motion.points,
            colors1 = cycle(wheel, start = 0, alpha = alpha),
            colors2 = cycle(wheel, start = 2, alpha = alpha),
            colors3 = cycle(wheel, start = 4, alpha = alpha),
            colorInterpPeriod = motion.colorInterpPeriod,
            lightOffset = motion.lightOffset,
            saturateOffset = motion.saturateOffset,
            pointOffset = motion.pointOffset,
        )
    }
}

private class FlowMotion(
    val points: FloatArray,
    val colorInterpPeriod: Float,
    val lightOffset: Float,
    val saturateOffset: Float,
    val pointOffset: Float,
    val timeScale: Float,
)

/**
 * 形态差异：晴天光斑大而疏、雨天上下拉长、雪天小而散、雾天一块巨大的弥散光，
 * 雷暴对比最强、摆幅最大，沙尘横向铺开。
 */
private fun motionOf(kind: WeatherFlowKind): FlowMotion = when (kind) {
    WeatherFlowKind.Clear -> FlowMotion(
        points = floatArrayOf(0.15f, 0.10f, 0.95f, 0.85f, 0.25f, 0.80f, 0.28f, 0.90f, 1.00f, 0.95f, 0.85f, 0.85f),
        colorInterpPeriod = 15f, lightOffset = 0.10f, saturateOffset = 0.18f,
        pointOffset = 0.22f, timeScale = 0.28f,
    )
    WeatherFlowKind.Cloudy -> FlowMotion(
        points = floatArrayOf(0.10f, 0.20f, 1.10f, 0.90f, 0.32f, 1.00f, 0.30f, 0.88f, 1.05f, 0.82f, 0.92f, 1.00f),
        colorInterpPeriod = 18f, lightOffset = 0.07f, saturateOffset = 0.22f,
        pointOffset = 0.16f, timeScale = 0.24f,
    )
    WeatherFlowKind.Rain -> FlowMotion(
        points = floatArrayOf(0.50f, -0.10f, 0.90f, 0.18f, 0.52f, 0.62f, 0.84f, 0.58f, 0.62f, 0.52f, 1.10f, 0.92f),
        colorInterpPeriod = 13f, lightOffset = 0.02f, saturateOffset = 0.26f,
        pointOffset = 0.12f, timeScale = 0.34f,
    )
    WeatherFlowKind.Snow -> FlowMotion(
        points = floatArrayOf(0.18f, 0.18f, 0.55f, 0.74f, 0.30f, 0.50f, 0.36f, 0.76f, 0.55f, 0.86f, 0.82f, 0.50f),
        colorInterpPeriod = 20f, lightOffset = 0.08f, saturateOffset = 0.16f,
        pointOffset = 0.30f, timeScale = 0.20f,
    )
    WeatherFlowKind.Frost -> FlowMotion(
        points = floatArrayOf(0.22f, 0.24f, 0.62f, 0.70f, 0.34f, 0.46f, 0.40f, 0.72f, 0.60f, 0.84f, 0.88f, 0.56f),
        colorInterpPeriod = 24f, lightOffset = 0.04f, saturateOffset = 0.10f,
        pointOffset = 0.26f, timeScale = 0.16f,
    )
    WeatherFlowKind.Thunder -> FlowMotion(
        points = floatArrayOf(0.20f, 0.12f, 1.00f, 0.76f, 0.34f, 0.70f, 0.60f, 0.90f, 0.85f, 0.94f, 0.68f, 0.60f),
        colorInterpPeriod = 11f, lightOffset = 0.00f, saturateOffset = 0.30f,
        pointOffset = 0.36f, timeScale = 0.32f,
    )
    WeatherFlowKind.Fog -> FlowMotion(
        points = floatArrayOf(0.50f, 0.42f, 1.60f, 0.18f, 0.78f, 1.20f, 0.86f, 0.18f, 1.20f, 0.52f, 0.62f, 1.40f),
        colorInterpPeriod = 22f, lightOffset = 0.05f, saturateOffset = 0.30f,
        pointOffset = 0.08f, timeScale = 0.14f,
    )
    WeatherFlowKind.Dust -> FlowMotion(
        points = floatArrayOf(0.08f, 0.48f, 1.00f, 0.92f, 0.44f, 0.90f, 0.34f, 0.76f, 1.10f, 0.70f, 0.24f, 0.80f),
        colorInterpPeriod = 16f, lightOffset = 0.06f, saturateOffset = 0.28f,
        pointOffset = 0.20f, timeScale = 0.26f,
    )
}

/** 白天的基础色轮：取 6 色是为了让三组预设错位取色后仍能首尾相接。 */
private fun wheelOf(kind: WeatherFlowKind): List<Color> = when (kind) {
    WeatherFlowKind.Clear -> listOf(
        Color(0xFF8FC8E8), Color(0xFFC6E6F7), Color(0xFFA5D6F0),
        Color(0xFFF7DCB0), Color(0xFF7FB6DE), Color(0xFFD9EEFA),
    )
    WeatherFlowKind.Cloudy -> listOf(
        Color(0xFF8FB4CF), Color(0xFFBACDDA), Color(0xFFA9BECC),
        Color(0xFFD9E4EC), Color(0xFF9AAFC0), Color(0xFFC9D8E4),
    )
    WeatherFlowKind.Rain -> listOf(
        Color(0xFF4F7FA6), Color(0xFF6D93B4), Color(0xFF3E6D8E),
        Color(0xFF8FB0C6), Color(0xFF5E87A8), Color(0xFF7FA3BD),
    )
    WeatherFlowKind.Snow -> listOf(
        Color(0xFFA9CBE6), Color(0xFFD6E8F5), Color(0xFFC4D9E8),
        Color(0xFFEAF4FA), Color(0xFFB7D3E8), Color(0xFFDDEBF6),
    )
    WeatherFlowKind.Frost -> listOf(
        Color(0xFFBFD9EC), Color(0xFFE4F1FA), Color(0xFF9FC4DE),
        Color(0xFFF2FAFF), Color(0xFFB2D2E8), Color(0xFFD8EAF6),
    )
    WeatherFlowKind.Thunder -> listOf(
        Color(0xFF6E6FA4), Color(0xFF9A93C4), Color(0xFF4B4E86),
        Color(0xFFB9A9D6), Color(0xFF7D74B0), Color(0xFFA79BCB),
    )
    WeatherFlowKind.Fog -> listOf(
        Color(0xFFA39A8B), Color(0xFFC8C0B3), Color(0xFF8E8778),
        Color(0xFFDDD7CB), Color(0xFFB2A99B), Color(0xFFD2CABE),
    )
    WeatherFlowKind.Dust -> listOf(
        Color(0xFFC99A5C), Color(0xFFE3C08A), Color(0xFFB07F44),
        Color(0xFFF0D5A8), Color(0xFFD3A868), Color(0xFFEAC79B),
    )
}

/** 时段只改色相倾向，不改形态：夜里一律压成深蓝，晨暮往暖色偏。 */
private fun phaseColor(color: Color, phase: SkyPhase): Color = when (phase) {
    SkyPhase.Day -> color
    SkyPhase.Dawn -> lerp(color, Color(0xFFF5A06E), 0.42f)
    SkyPhase.Dusk -> lerp(color, Color(0xFFD96E5E), 0.40f)
    // 夜间压成深蓝：保留约两成原色相，既让晴/雨/雪之间有差别，又不会在暗底上泛出脏橘
    SkyPhase.Night -> lerp(lerp(color, Color(0xFF22305A), 0.78f), Color(0xFF46579A), 0.15f)
}

private fun phaseAlpha(phase: SkyPhase): Float = when (phase) {
    SkyPhase.Dawn -> 0.60f
    SkyPhase.Day -> 0.54f
    SkyPhase.Dusk -> 0.60f
    SkyPhase.Night -> 0.74f
}

/** 夜间整体再慢一点，避免暗底上快速移动的亮斑显得躁。 */
private fun phaseTimeScale(phase: SkyPhase): Float = when (phase) {
    SkyPhase.Night -> 0.82f
    else -> 1f
}

private fun cycle(wheel: List<Color>, start: Int, alpha: Float): FloatArray {
    val colors = FloatArray(16)
    for (i in 0 until 4) {
        val color = wheel[(start + i) % wheel.size]
        colors[i * 4] = color.red
        colors[i * 4 + 1] = color.green
        colors[i * 4 + 2] = color.blue
        colors[i * 4 + 3] = alpha
    }
    return colors
}


/**
 * 雨势 0（毛毛雨）..1（暴雨）：降水量优先，没有就按文本里的强度词兜底。
 * 只用于雨的 shader 参数，不影响配色。
 */
internal fun rainIntensity(snapshot: WeatherSnapshot?): Float {
    val text = snapshot?.now?.text.orEmpty()
    val byText = when {
        text.contains("暴") -> 1f
        text.contains("大") -> 0.85f
        text.contains("中") -> 0.62f
        text.contains("阵") -> 0.45f
        text.contains("小") -> 0.22f
        text.contains("雷") -> 0.75f
        text.contains("雨") -> 0.45f
        else -> 0f
    }
    val mm = (snapshot?.now?.precipMm ?: 0.0) / 12.0
    return maxOf(byText, mm.toFloat().coerceIn(0f, 1f))
}
