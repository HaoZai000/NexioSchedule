package com.haooz.chedule.ui.activities.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.WeatherStr
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.weather.WeatherSnapshot
import com.haooz.chedule.weather.localHourMinute
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 详细数据瓦片点开后的「走势」。
 *
 * ## 能画什么，全看源里有什么（别在这里补数据）
 *
 * | 指标 | 未来 24 小时 | 未来 7 天 |
 * |---|---|---|
 * | 紫外线 | ✅ Open-Meteo 逐时 | ✅ 逐日最大 |
 * | 湿度 | ✅ Open-Meteo 逐时 | ✅ Open-Meteo **日均** |
 * | 体感 | ✅ Open-Meteo 逐时 | ❌ 逐日没有体感 |
 * | 风 | ✅ 逐时风速 | ✅ 逐日最大风速 |
 * | 气压 | ✅ Open-Meteo 逐时 | ✅ Open-Meteo **日均** |
 * | 能见度 | ✅ Open-Meteo 逐时 | ✅ Open-Meteo **日均** |
 * | 日出日落 | ❌ | ✅ 逐日时间 |
 *
 * 小米的逐时没有湿度/气压，中国天气网干脆没有逐时 —— 融合器补洞之后，单项缺就是真的缺，
 * 这里**只做过滤，不做插值**：空档直接跳过，不画一条"看起来平滑"的假线。
 */

internal data class TrendPoint(val label: String, val value: Double)

/** 某一项在未来 24 小时里的走势（缺数据的小时直接跳过）。 */
internal fun hourlyTrend(snapshot: WeatherSnapshot, kind: DetailMetric): List<TrendPoint> =
    snapshot.hourly.take(24).mapNotNull { hour ->
        val v = when (kind) {
            DetailMetric.Uv -> hour.uvIndex?.toDouble()
            DetailMetric.Humidity -> hour.humidity?.toDouble()
            DetailMetric.Dew -> hour.dewPointC
            DetailMetric.FeelsLike -> hour.feelsLikeC
            DetailMetric.Wind -> hour.windSpeedKmh
            DetailMetric.Pressure -> hour.pressureHpa?.toDouble()
            DetailMetric.Visibility -> hour.visibilityKm
            DetailMetric.Sun -> null
        }
        v?.let { TrendPoint(localHourMinute(hour.epochSec, snapshot.utcOffsetSec), it) }
    }

/** 某一项在未来 7 天里的走势。 */
internal fun dailyTrend(snapshot: WeatherSnapshot, kind: DetailMetric): List<TrendPoint> =
    snapshot.daily.take(7).mapIndexedNotNull { index, day ->
        val v = when (kind) {
            DetailMetric.Uv -> day.uvIndexMax?.toDouble()
            DetailMetric.Humidity -> day.humidity?.toDouble()
            DetailMetric.Wind -> day.windSpeedKmh
            DetailMetric.Pressure -> day.pressureHpa?.toDouble()
            DetailMetric.Visibility -> day.visibilityKm
            // 体感/露点只有逐时；日出日落走 sunRows
            DetailMetric.FeelsLike, DetailMetric.Dew, DetailMetric.Sun -> null
        }
        v?.let { TrendPoint(dayLabel(index, day.date), it) }
    }

internal data class SunRow(
    val label: String,
    val rise: String,
    val set: String,
    /** 日出 / 日落在一日中的分钟数（0..1439），画白昼带用。 */
    val startMin: Int,
    val endMin: Int,
)

/** 逐日日出日落：时间本身没有"高低"，所以不做曲线，只排时间。 */
internal fun sunRows(snapshot: WeatherSnapshot): List<SunRow> =
    snapshot.daily.take(7).mapIndexedNotNull { index, day ->
        val rise = minuteOfClock(day.sunrise)
        val set = minuteOfClock(day.sunset)
        if (rise == null || set == null || set <= rise) return@mapIndexedNotNull null
        SunRow(dayLabel(index, day.date), day.sunrise, day.sunset, rise, set)
    }

/** 这一格有没有可点的走势。没有就不给点击反馈（点开一张空面板比点不动更糟）。 */
internal fun DetailMetric.hasTrend(snapshot: WeatherSnapshot): Boolean = when (this) {
    DetailMetric.Sun -> sunRows(snapshot).isNotEmpty()
    else -> hourlyTrend(snapshot, this).size >= 2 || dailyTrend(snapshot, this).isNotEmpty()
}

internal fun trendUnit(kind: DetailMetric): String = when (kind) {
    DetailMetric.Uv -> ""
    DetailMetric.Humidity -> WeatherStr.unitPercent()
    DetailMetric.Dew -> "°"
    DetailMetric.FeelsLike -> "°"
    DetailMetric.Wind -> WeatherStr.unitKmh()
    DetailMetric.Pressure -> WeatherStr.unitHpa()
    DetailMetric.Visibility -> WeatherStr.unitKm()
    DetailMetric.Sun -> ""
}

/** 整数指标不显示小数点；风速/能见度保留一位（2 和 2.4 在走线上是两回事）。 */
private fun fmt(kind: DetailMetric, value: Double): String = when (kind) {
    DetailMetric.Uv, DetailMetric.Humidity, DetailMetric.FeelsLike, DetailMetric.Dew,
    DetailMetric.Pressure -> value.roundToInt().toString()
    else -> trimNum(value)
}

// ── 面板内容 ──────────────────────────────────────────────────────────

/**
 * 单项走势面板。
 *
 * 三段式：**读数 → 逐时折线 → 逐日条形**，后两段各装在一张 miuix Card 里。
 *
 * 上一版是「大数字 + 几个自由浮动的小标题 + 没有容器的图」，四个毛病：
 * 1. 顶部只让开 58dp，而标题栏写死 76dp —— 读数有 18dp 压在标题栏和顶部渐变里（像被削掉一截）；
 * 2. 段间距由 18 / 10 / 20 三个 Spacer 和外壳的 `spacedBy(12)` 叠加而成，同一层意思出现
 *    22dp / 30dp 两种距离，看着就是"说不清的间距"；
 * 3. 折线与条形直接画在弹窗底色上，没有边界，跟标题栏的渐变糊在一起；
 * 4. 区间（最高/最低）单独占一行夹在标题和折线之间，把图往下推。
 *
 * 现在：段间距统一由外壳给 14dp，段内"标题 + 8dp + 卡片内边距 14dp"，区间挪到段标题右端，
 * 每个尺寸只有一个来源。
 */
@Composable
internal fun MetricTrendBody(snapshot: WeatherSnapshot, kind: DetailMetric) {
    val reading = readingOf(snapshot, kind)

    ReadingHero(reading)

    if (kind == DetailMetric.Sun) {
        val rows = sunRows(snapshot)
        if (rows.isEmpty()) {
            EmptyTrendHint()
            return
        }
        TrendSection(title = WeatherStr.trendDailyTitle()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { SunRowView(it) }
            }
        }
        return
    }

    val hourly = hourlyTrend(snapshot, kind)
    val daily = dailyTrend(snapshot, kind)
    if (hourly.size < 2 && daily.isEmpty()) {
        EmptyTrendHint()
        return
    }

    if (hourly.size >= 2) {
        TrendSection(title = WeatherStr.trendHourlyTitle()) {
            HourlyTrendChart(hourly, kind)
        }
    }
    if (daily.isNotEmpty()) {
        val axis = dayAxisOf(kind, daily)
        TrendSection(title = WeatherStr.trendDailyTitle()) {
            DailyTrendList(daily, kind, axis)
        }
    }
}

/**
 * 顶部读数：大数字 + 单位 + 一句话解读。
 * 单位贴底边（不是基线居中）：`hPa` / `km/h` 这种矮字母落在中线上会跟数字脱开。
 */
@Composable
private fun ReadingHero(reading: MetricReading) {
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    // "6小时52分钟后"这种长句子按大数字的字号会顶满整行，长值降一档
    val long = reading.value.length > 6
    // 弹窗里**不在卡片内**的裸文字统一让 8dp：卡片内的正文有 14dp 内边距，
    // 裸文字贴着弹窗 16dp 边会显得比卡片还靠外，视线上下对不齐。
    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
        // 度数符号必须**上标**到数字右上：跟数字同基线时那颗小圈落在底部，
        // 远看就是"32。"（句号）。跟方卡里的 `ValueTile` 同一个坑、同一个解法。
        val degree = reading.unit == "°"
        Row(verticalAlignment = if (degree) Alignment.Top else Alignment.Bottom) {
            Text(
                text = reading.value,
                fontSize = if (long) 24.sp else 38.sp,
                lineHeight = if (long) 30.sp else 42.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (reading.unit.isNotBlank()) {
                Spacer(Modifier.width(if (degree) 1.dp else 4.dp))
                Text(
                    text = reading.unit,
                    fontSize = if (degree) 17.sp else 14.sp,
                    color = secondary,
                    maxLines = 1,
                    modifier = if (degree) {
                        Modifier.padding(top = 5.dp)
                    } else {
                        Modifier.padding(bottom = 6.dp)
                    },
                )
            }
        }
        if (reading.caption.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = reading.caption,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = secondary,
            )
        }
    }
}

/** 段标题（左）+ 区间（右）+ 一张卡片。整段只有这一个间距阶梯。空气面板也用它。 */
@Composable
internal fun TrendSection(
    title: String,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            // 标题左对齐、区间右对齐 → 两端各让 8dp
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                color = secondary,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (!trailing.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = trailing,
                    fontSize = 12.sp,
                    color = secondary.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
            colors = weatherSheetCardColors(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun EmptyTrendHint() {
    Text(
        text = WeatherStr.trendEmpty(),
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 8.dp),
    )
}

// ── 逐时折线 ──────────────────────────────────────────────────────────

/** 逐时纵轴：部分指标从 0 起，部分围绕基准上下波动。 */
private class HourlyAxis(
    val min: Double,
    val max: Double,
    /** 非 null 时画一条虚线基准（温度=区间中值，气压=1013hPa）。 */
    val baseline: Double? = null,
) {
    val span: Double get() = (max - min).coerceAtLeast(1e-3)
    fun frac(v: Double): Float = ((v - min) / span).coerceIn(0.0, 1.0).toFloat()
}

private fun hourlyAxisOf(kind: DetailMetric, values: List<Double>): HourlyAxis {
    val lo = values.min()
    val hi = values.max()
    return when (kind) {
        DetailMetric.Humidity -> HourlyAxis(0.0, 100.0)
        DetailMetric.Uv -> HourlyAxis(0.0, maxOf(11.0, ceil(hi * 1.2)))
        DetailMetric.Wind -> HourlyAxis(0.0, maxOf(20.0, ceil(hi * 1.2 / 5.0) * 5.0))
        DetailMetric.Visibility -> HourlyAxis(0.0, maxOf(10.0, ceil(hi * 1.2)))
        DetailMetric.Pressure -> {
            val span = maxOf(5.0, abs(hi - 1013.0), abs(lo - 1013.0)) * 1.4
            HourlyAxis(1013.0 - span, 1013.0 + span, 1013.0)
        }
        // 温度 / 体感 / 露点：围绕区间中值上下，不从 0 起，否则 24 小时的 3° 波动会被压平
        DetailMetric.FeelsLike, DetailMetric.Dew -> {
            val pad = maxOf(1.0, (hi - lo) * 0.15)
            HourlyAxis(floor(lo - pad), ceil(hi + pad), (lo + hi) / 2.0)
        }
        DetailMetric.Sun -> HourlyAxis(0.0, 1.0)
    }
}

/** 各指标的主色；温度类走冷→暖渐变，不走统一色。 */
private fun hourlyColor(kind: DetailMetric): Color = when (kind) {
    DetailMetric.Humidity -> Color(0xFF58C0EA)   // 天蓝
    DetailMetric.Uv -> Color(0xFF9B7BFF)         // 紫
    DetailMetric.Wind -> Color(0xFF4FB3A8)       // 青绿
    DetailMetric.Pressure -> Color(0xFFF0A55B)   // 琥珀
    DetailMetric.Visibility -> Color(0xFF7FB2D9) // 灰蓝
    DetailMetric.FeelsLike, DetailMetric.Dew -> Color(0xFFE8A05A)
    DetailMetric.Sun -> Color(0xFFF2C14E)
}

private val TEMP_COLD = Color(0xFF5AA9E6)
private val TEMP_WARM = Color(0xFFF2C14E)
private val TEMP_HOT = Color(0xFFE85D4A)

private fun isTemperature(kind: DetailMetric): Boolean =
    kind == DetailMetric.FeelsLike || kind == DetailMetric.Dew

private fun tempColor(frac: Float): Color =
    if (frac <= 0.5f) lerp(TEMP_COLD, TEMP_WARM, frac * 2f)
    else lerp(TEMP_WARM, TEMP_HOT, (frac - 0.5f) * 2f)

private fun axisText(kind: DetailMetric, value: Double, withUnit: Boolean): String {
    val text = fmt(kind, value)
    return if (withUnit) text + trendUnit(kind) else text
}

@Composable
private fun HourlyTrendChart(points: List<TrendPoint>, kind: DetailMetric) {
    val values = points.map { it.value }
    val axis = remember(points, kind) { hourlyAxisOf(kind, values) }
    val baseColor = hourlyColor(kind)
    val temperature = isTemperature(kind)
    val grid = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.13f)
    val axisColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)

    Row(modifier = Modifier.fillMaxWidth()) {
        // 纵坐标：上 / 中 / 下三档；高度与绘图区对齐（上下各让 padY=10dp）
        Column(
            // 宽度取三行里最宽的那行，不写死固定值：短标签（0、50）不会在左侧留一大块空白
            modifier = Modifier.width(IntrinsicSize.Max).height(112.dp).padding(vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text(axisText(kind, axis.max, true), style = labelStyle, maxLines = 1)
            Text(axisText(kind, (axis.max + axis.min) / 2.0, true), style = labelStyle, maxLines = 1)
            Text(axisText(kind, axis.min, true), style = labelStyle, maxLines = 1)
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(112.dp)) {
                val padX = 4.dp.toPx()
                val padY = 10.dp.toPx()
                val w = size.width - padX * 2
                val h = size.height - padY * 2
                val n = points.size
                fun xAt(i: Int): Float = padX + w * i / (n - 1f)
                fun yAt(v: Double): Float = padY + h * (1f - axis.frac(v))

                val pts = points.indices.map { Offset(xAt(it), yAt(values[it])) }

                // 上沿 / 中线 / 下沿，与左侧三档刻度对应
                for (i in 0..2) {
                    val y = padY + h * i / 2f
                    drawLine(grid, Offset(padX, y), Offset(padX + w, y), strokeWidth = 1.dp.toPx())
                }
                axis.baseline?.let { baseline ->
                    val y = yAt(baseline)
                    drawLine(
                        color = baseColor.copy(alpha = 0.35f),
                        start = Offset(padX, y),
                        end = Offset(padX + w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    )
                }

                val line = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 0 until pts.lastIndex) {
                        val p0 = pts.getOrElse(i - 1) { pts[i] }
                        val p1 = pts[i]
                        val p2 = pts[i + 1]
                        val p3 = pts.getOrElse(i + 2) { pts[i + 1] }
                        cubicTo(
                            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                            p2.x, p2.y,
                        )
                    }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }

                if (temperature) {
                    val lineBrush = Brush.verticalGradient(
                        colors = listOf(TEMP_HOT, TEMP_WARM, TEMP_COLD),
                        startY = padY,
                        endY = padY + h,
                    )
                    val areaBrush = Brush.verticalGradient(
                        colors = listOf(
                            TEMP_HOT.copy(alpha = 0.20f),
                            TEMP_WARM.copy(alpha = 0.10f),
                            TEMP_COLD.copy(alpha = 0.02f),
                        ),
                        startY = padY,
                        endY = padY + h,
                    )
                    drawPath(area, brush = areaBrush)
                    drawPath(line, brush = lineBrush, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                } else {
                    drawPath(
                        path = area,
                        brush = Brush.verticalGradient(
                            colors = listOf(baseColor.copy(alpha = 0.20f), baseColor.copy(alpha = 0.02f)),
                            startY = padY,
                            endY = size.height,
                        ),
                    )
                    drawPath(line, baseColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }

                pts.forEachIndexed { i, p ->
                    val c = if (temperature) tempColor(axis.frac(values[i])) else baseColor
                    drawCircle(c.copy(alpha = 0.85f), radius = 1.8.dp.toPx(), center = p)
                }
                val firstColor = if (temperature) tempColor(axis.frac(values.first())) else baseColor
                drawCircle(firstColor, radius = 3.2.dp.toPx(), center = pts.first())
                drawCircle(Color.White, radius = 1.6.dp.toPx(), center = pts.first())
            }

            Spacer(Modifier.height(9.dp))
            if (points.size >= 2) {
                val count = 6
                val marks = (0 until count)
                    .map { it * points.lastIndex / (count - 1) }
                    .distinct()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    marks.forEachIndexed { order, i ->
                        Text(
                            text = if (order == 0) WeatherStr.now() else points[i].label,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ── 逐日：每一项换一种表达 ────────────────────────────────────────────
//
// ⚠ 逐日**不能用本周 min..max 的相对排位**（上一版就是这么画的）：一周 40%~70% 里，
// 59% 的湿度只剩中间一小截，读者会以为"今天很干"。每个指标都得有一条**绝对刻度**，
// 条/点在刻度上的位置才有意义。
//
// 也**不能一律用进度条**：气压没有"总量"，一根从左填到右的条读作"气压快满了"，
// 语义是错的。所以按指标分五种表达：
//   湿度   → 绝对填充条（0~100%，多少就占多少，跨天可比）
//   紫外线 → 五格信号条（分级本来就是离散的，一格 = 一级，颜色自带含义）
//   气压   → 以 1013hPa 为中轴的**偏差条**（往左偏低、往右偏高，没有"满"）
//   风     → 一段带箭头的射线（强度交给长度，不要轨道）
//   能见度 → 刻度尺上的一个点（读的是落点，不是填满多少）

/** 绝对刻度：min..max + 一条参考刻度（只有气压的偏差条用它当"中轴"）。 */
private class DayAxis(val min: Double, val max: Double, val mark: Double? = null) {
    fun frac(v: Double): Float = ((v - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
}

private fun dayAxisOf(kind: DetailMetric, points: List<TrendPoint>): DayAxis = when (kind) {
    DetailMetric.Humidity -> DayAxis(0.0, 100.0)
    // 世卫 UV 指数的极强上限就是 11，超过顶格
    DetailMetric.Uv -> DayAxis(0.0, 11.0)
    DetailMetric.Pressure -> DayAxis(
        PRESSURE_NORMAL - PRESSURE_SPAN,
        PRESSURE_NORMAL + PRESSURE_SPAN,
        PRESSURE_NORMAL,
    )
    DetailMetric.Visibility -> DayAxis(0.0, 30.0)
    // 风速上限跟着本周走（10 的整数倍，至少 20）：固定 30 时 5km/h 的微风全挤在左边
    DetailMetric.Wind -> DayAxis(0.0, maxOf(20.0, ceil(points.maxOf { it.value } / 10.0) * 10.0))
    // 体感/露点没有逐日、日出日落走时间行，都到不了这里
    DetailMetric.FeelsLike, DetailMetric.Dew, DetailMetric.Sun -> DayAxis(0.0, 1.0)
}

private val HUMIDITY_COLOR = Color(0xFF58C0EA)
private val VISIBILITY_COLOR = Color(0xFF7FB2D9)
private val PRESSURE_LOW_COLOR = Color(0xFF7FA8C9)
private val PRESSURE_HIGH_COLOR = Color(0xFFF0A55B)

@Composable
private fun DailyTrendList(points: List<TrendPoint>, kind: DetailMetric, axis: DayAxis) {
    // 数值列必须**每行一样宽**：轨道代表绝对刻度，行与行长度不同就成了"七把不一样长的尺子"。
    // 但宽度又不能写死（"8" 和 "100%" 差三倍，写死要么截断要么留一大块空白），
    // 所以按这一组里最宽的那个值实测 —— 用同一个 LocalTextStyle，量出来的就是渲染宽度。
    val baseStyle = LocalTextStyle.current.merge(
        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val valueWidth = remember(points, kind, baseStyle, density) {
        val widest = points.maxOf { point ->
            measurer.measure(dayValueText(kind, point.value), baseStyle, maxLines = 1).size.width
        }
        with(density) { widest.toDp() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        points.forEach { point ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = point.label,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(16.dp))
                Box(
                    modifier = Modifier.weight(1f).height(22.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    DayMark(point.value, axis, kind)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = dayValueText(kind, point.value),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(valueWidth),
                )
            }
        }
    }
}

/** 一行的数值文案（数值 + 单位，单位为空就不加）。刻度定宽时要按它来量。 */
private fun dayValueText(kind: DetailMetric, value: Double): String {
    val unit = trendUnit(kind)
    return if (unit.isBlank()) fmt(kind, value) else "${fmt(kind, value)}$unit"
}

@Composable
private fun DayMark(value: Double, axis: DayAxis, kind: DetailMetric) {
    when (kind) {
        DetailMetric.Uv -> UvLevelMeter(value)
        DetailMetric.Pressure -> PressureDeviationBar(value, axis)
        DetailMetric.Wind -> WindLengthBar(value, axis)
        DetailMetric.Visibility -> RulerDot(value, axis, VISIBILITY_COLOR)
        else -> ScaleFillBar(value, axis, HUMIDITY_COLOR)
    }
}

/** 绝对刻度填充条：条从 0 起，长度就是数值本身（0~100% 就该铺满一百格）。 */
@Composable
private fun ScaleFillBar(value: Double, axis: DayAxis, color: Color) {
    val track = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        val h = 6.dp.toPx()
        val top = (size.height - h) / 2f
        val radius = CornerRadius(h / 2f)
        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = radius)
        val f = axis.frac(value)
        if (f > 0f) {
            // 至少留一个圆头宽：2% 的湿度也得看得见，不能画成 0 像素
            val w = (size.width * f).coerceIn(h, size.width)
            drawRoundRect(color, topLeft = Offset(0f, top), size = Size(w, h), cornerRadius = radius)
        }
    }
}

/** 五格信号条：亮到第几格就是第几级（分级数据不该用连续进度条糊过去）。 */
@Composable
private fun UvLevelMeter(value: Double) {
    val level = when {
        value <= 2 -> 0
        value <= 5 -> 1
        value <= 7 -> 2
        value <= 10 -> 3
        else -> 4
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        val h = 7.dp.toPx()
        val top = (size.height - h) / 2f
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * 4) / 5
        val radius = CornerRadius(2.dp.toPx())
        for (i in 0..4) {
            drawRoundRect(
                color = UV_LEVEL_COLORS[i].copy(alpha = if (i <= level) 1f else 0.14f),
                topLeft = Offset(i * (bw + gap), top),
                size = Size(bw, h),
                cornerRadius = radius,
            )
        }
    }
}

/** 偏差条：从中轴（1013）向数值那一侧伸，低=冷色、高=暖色。没有"填满"这回事。 */
@Composable
private fun PressureDeviationBar(value: Double, axis: DayAxis) {
    val track = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)
    val mark = axis.mark ?: PRESSURE_NORMAL
    val tick = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        val h = 6.dp.toPx()
        val top = (size.height - h) / 2f
        val radius = CornerRadius(h / 2f)
        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = radius)

        val midX = size.width * axis.frac(mark)
        val x = size.width * axis.frac(value)
        val color = if (value >= mark) PRESSURE_HIGH_COLOR else PRESSURE_LOW_COLOR
        val left = minOf(midX, x)
        val w = maxOf(kotlin.math.abs(x - midX), 4.dp.toPx())
        drawRoundRect(color, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = radius)

        drawLine(
            color = tick,
            start = Offset(midX, top - 3.dp.toPx()),
            end = Offset(midX, top + h + 3.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** 风：一条带箭头的射线，长度 = 风速在刻度上的位置。没有轨道，就是一段"吹出去"的长度。 */
@Composable
private fun WindLengthBar(value: Double, axis: DayAxis) {
    val ink = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val rail = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2f
        drawLine(rail, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())

        val head = 5.dp.toPx()
        val start = 2.dp.toPx()
        val x = (size.width * axis.frac(value)).coerceIn(start + head, size.width - 1.dp.toPx())
        // 杆不画到尖端（圆头帽会顶出箭头），跟风向罗盘同一个教训
        drawLine(ink, Offset(start, y), Offset(x - head * 0.55f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x - head, y - head * 0.55f)
            lineTo(x - head, y + head * 0.55f)
            close()
        }
        drawPath(path, ink)
    }
}

/** 能见度：一把刻度尺 + 一个落点。每 10km 一根短刻度，读的是"落在哪"。 */
@Composable
private fun RulerDot(value: Double, axis: DayAxis, color: Color) {
    val line = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
    val surface = MiuixTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2f
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        var t = 0.0
        while (t <= axis.max + 0.001) {
            val x = size.width * axis.frac(t)
            drawLine(
                color = line.copy(alpha = 0.6f),
                start = Offset(x, y - 3.dp.toPx()),
                end = Offset(x, y + 3.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            t += 10.0
        }
        val r = 4.5.dp.toPx()
        val x = (size.width * axis.frac(value)).coerceIn(r, size.width - r)
        drawCircle(color, radius = r, center = Offset(x, y))
        drawCircle(surface, radius = r * 0.42f, center = Offset(x, y))
    }
}

// ── 逐日日出日落 ──────────────────────────────────────────────────────

/**
 * 逐日日出日落：日期 / **白昼带** / 时间。
 *
 * 带子是 24 小时尺上的白昼段（不是"本周排位"）：位置说"几点升、几点落"，
 * 长度说"白天多长"，一天和一天之间可以直接比。右边的时刻给精确值。
 */
@Composable
private fun SunRowView(row: SunRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = row.label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier.weight(1f).height(22.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            DaylightBand(row.startMin, row.endMin)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = WeatherStr.sunSpan(row.rise, row.set),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/** 24 小时尺上的白昼段：两头暗、正午亮，一眼看出"长还是短、早晚还是晚"。 */
@Composable
private fun DaylightBand(startMin: Int, endMin: Int) {
    val track = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)
    val warm = Color(0xFFF6BE5C)
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        val h = 6.dp.toPx()
        val top = (size.height - h) / 2f
        val radius = CornerRadius(h / 2f)
        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = radius)
        val x0 = size.width * (startMin / 1440f)
        val x1 = size.width * (endMin / 1440f)
        if (x1 - x0 <= 0f) return@Canvas
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(warm.copy(alpha = 0.45f), warm, warm.copy(alpha = 0.45f)),
                startX = x0,
                endX = x1,
            ),
            topLeft = Offset(x0, top),
            size = Size(maxOf(x1 - x0, h), h),
            cornerRadius = radius,
        )
    }
}
