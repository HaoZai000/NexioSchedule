package com.haooz.chedule.ui.activities.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.WeatherStr
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.weather.WeatherSnapshot
import com.haooz.chedule.weather.localHourMinute
import com.haooz.chedule.weather.windDegFromName
import com.haooz.chedule.weather.windPowerFromKmh
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.datetime.Clock
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 「详细数据」：一张一张独立的正方形卡，一行两张
 *
 * 红线：
 * 1. **只画源里真有的东西**。三家源覆盖不同，缺的项整张卡不出现（不是显示 "—"）。
 *    `detailMetrics` 是唯一的准入清单。
 * 2. **降水 / 云量不在这里**：这两项对"今天穿什么、带不带伞"没有增量信息，
 *    逐时/逐日里已经能看到，摆进正方形卡只是占位置。
 *    露点例外：它是"闷不闷"的直接判据（比湿度准），所以占第 8 格，让 2×4 不留空位。
 * 3. 卡是**正方形**且跟页面其它卡同级（不再包一层大卡）：两列宽度由 weight 均分，
 *    高度靠 `aspectRatio(1f)` 跟着宽度走 —— 写死高度的话，平板/折叠屏一宽就变形。
 * 4. 字号只有四档：标题 13 / 读数 30 / 单位 13 / 说明 13。
 */

/** 详细数据里的每一项。顺序即渲染顺序。 */
internal enum class DetailMetric { Uv, Humidity, Dew, FeelsLike, Wind, Pressure, Visibility, Sun }

/** 一格读数：已经渲染成给用户看的字符串，单位单独存（大字号值 + 小字号单位）。 */
internal data class MetricReading(
    val label: String,
    val value: String,
    val unit: String = "",
    val caption: String = "",
)

private val CARD_PAD = 16.dp
private val CARD_GAP = 10.dp

/** 四档字号（红线 4 的唯一来源，别在下面另写裸数字）。 */
private val TITLE_SP = 13.sp
private val VALUE_SP = 30.sp
private val UNIT_SP = 13.sp
private val CAPTION_SP = 13.sp

/** 趋势面板标题用的名字（风不能叫"南风走势"）。 */
internal fun DetailMetric.name(): String = when (this) {
    DetailMetric.Uv -> WeatherStr.uvIndex()
    DetailMetric.Humidity -> WeatherStr.humidity()
    DetailMetric.Dew -> WeatherStr.dewPoint()
    DetailMetric.FeelsLike -> WeatherStr.feelsLikeLabel()
    DetailMetric.Wind -> WeatherStr.wind()
    DetailMetric.Pressure -> WeatherStr.pressure()
    DetailMetric.Visibility -> WeatherStr.visibility()
    DetailMetric.Sun -> WeatherStr.sunriseSunset()
}

/** 有哪些项值得占一格。缺数据就不占 —— 这是页面「看不到横杠」的唯一保证。 */
internal fun detailMetrics(snapshot: WeatherSnapshot): List<DetailMetric> {
    val now = snapshot.now
    val today = snapshot.daily.firstOrNull()
    return buildList {
        if (now.uvIndex != null || today?.uvIndexMax != null) add(DetailMetric.Uv)
        if (now.humidity != null) add(DetailMetric.Humidity)
        // 露点紧跟湿度：它才是"闷不闷"的真正判据（湿度 60% 在 5℃ 和 28℃ 里是两回事）
        if (now.dewPointC != null) add(DetailMetric.Dew)
        if (now.feelsLikeC != null) add(DetailMetric.FeelsLike)
        if (now.windDir.isNotBlank() || now.windPower.isNotBlank() || now.windSpeedKmh != null) add(DetailMetric.Wind)
        if (now.pressureHpa != null) add(DetailMetric.Pressure)
        if (now.visibilityKm != null) add(DetailMetric.Visibility)
        if (today != null && today.sunrise.isNotBlank() && today.sunset.isNotBlank()) add(DetailMetric.Sun)
    }
}

internal fun readingOf(snapshot: WeatherSnapshot, kind: DetailMetric): MetricReading {
    val now = snapshot.now
    val today = snapshot.daily.firstOrNull()
    return when (kind) {
        DetailMetric.Uv -> {
            val uv = now.uvIndex ?: today?.uvIndexMax
            MetricReading(WeatherStr.uvIndex(), uv?.toString() ?: "—", caption = WeatherStr.uvCaption(uv))
        }
        DetailMetric.Humidity -> MetricReading(
            WeatherStr.humidity(),
            now.humidity?.toString() ?: "—",
            WeatherStr.unitPercent(),
            WeatherStr.humidityCaption(now.humidity),
        )
        DetailMetric.Dew -> MetricReading(
            WeatherStr.dewPoint(),
            now.dewPointC?.toInt()?.toString() ?: "—",
            "°",
            WeatherStr.dewCaption(now.dewPointC),
        )
        DetailMetric.FeelsLike -> MetricReading(
            WeatherStr.feelsLikeLabel(),
            now.feelsLikeC?.toInt()?.toString() ?: "—",
            "°",
            WeatherStr.feelsCaption(now.feelsLikeC),
        )
        DetailMetric.Wind -> MetricReading(
            now.windDir.ifBlank { WeatherStr.wind() },
            now.windPower.ifBlank { windPowerFromKmh(now.windSpeedKmh) }.ifBlank { "—" },
            // 瓦片只放方位与风级；面板里要看走势（km/h），所以把风速写进这句解读
            caption = listOf(
                now.windDir,
                now.windSpeedKmh?.let { "${trimNum(it)} ${WeatherStr.unitKmh()}" }.orEmpty(),
            ).filter { it.isNotBlank() }.joinToString(" · "),
        )
        DetailMetric.Pressure -> MetricReading(
            WeatherStr.pressure(),
            now.pressureHpa?.toString() ?: "—",
            WeatherStr.unitHpa(),
        )
        DetailMetric.Visibility -> MetricReading(
            WeatherStr.visibility(),
            now.visibilityKm?.let(::trimNum) ?: "—",
            WeatherStr.unitKm(),
            WeatherStr.visibilityCaption(now.visibilityKm),
        )
        DetailMetric.Sun -> {
            val sun = sunState(snapshot)
            MetricReading(WeatherStr.sunset(), sun.text, caption = sun.caption)
        }
    }
}

@Composable
internal fun DetailGrid(snapshot: WeatherSnapshot, onOpen: (DetailMetric) -> Unit) {
    val metrics = detailMetrics(snapshot)
    if (metrics.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        metrics.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
            ) {
                row.forEach { metric ->
                    MetricTileView(snapshot, metric, onOpen, Modifier.weight(1f))
                }
                // 奇数张：末位补个空占位，否则最后那张会被拉成整行宽（正方形就破了）
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTileView(
    snapshot: WeatherSnapshot,
    kind: DetailMetric,
    onOpen: (DetailMetric) -> Unit,
    modifier: Modifier,
) {
    val reading = readingOf(snapshot, kind)
    // 没有走势的项不给点击反馈 —— 点开一张"没有数据"的面板比点不动更糟
    val onClick: (() -> Unit)? = if (kind.hasTrend(snapshot)) {
        { onOpen(kind) }
    } else {
        null
    }
    when (kind) {
        DetailMetric.Wind -> WindTile(snapshot, reading, onClick, modifier)
        DetailMetric.Pressure -> PressureTile(reading, snapshot, onClick, modifier)
        DetailMetric.Sun -> SunTile(reading, snapshot, onClick, modifier)
        DetailMetric.Uv -> ValueTile(reading, onClick, modifier) {
            GradientBar(uvFraction(snapshot), UV_LEVEL_COLORS)
        }
        DetailMetric.Humidity -> ValueTile(reading, onClick, modifier) {
            FillBar((snapshot.now.humidity ?: 0) / 100f, Color(0xFF58C0EA))
        }
        DetailMetric.Dew -> ValueTile(reading, onClick, modifier) {
            FillBar(dewFraction(snapshot.now.dewPointC), Color(0xFF58C0EA))
        }
        DetailMetric.FeelsLike -> ValueTile(reading, onClick, modifier) {
            GradientBar(feelsFraction(snapshot.now.feelsLikeC), feelsColors())
        }
        DetailMetric.Visibility -> ValueTile(reading, onClick, modifier) {
            FillBar((snapshot.now.visibilityKm ?: 0.0).toFloat() / 30f, Color(0xFF7FB2D9))
        }
    }
}

// ── 卡片外壳 ──────────────────────────────────────────────────────────

/**
 * 一张正方形卡：标题 + 内容。
 *
 * 用 Miuix 的 `Card(onClick=)` 而不是自己 `background + clickable` —— 它与页面其它卡
 * 共用同一套材质、圆角和按下反馈（Sink），拼在一起才像一套。
 * 高度交给 [aspectRatio]，不要写死 dp。
 */
@Composable
private fun Tile(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.aspectRatio(1f),
        insideMargin = PaddingValues(CARD_PAD),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = onClick != null,
        onClick = onClick,
    ) {
        Text(
            text = title,
            fontSize = TITLE_SP,
            lineHeight = 17.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 标题与下面的读数之间留 6dp（原来 2dp，字贴得太紧）
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** 数值瓦片：大数字 + 单位 + 一句话解读 + 底部刻度条。 */
@Composable
private fun ValueTile(
    reading: MetricReading,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    bar: @Composable () -> Unit,
) {
    Tile(reading.label, onClick, modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // 度数符号要"上标"到数字右上，落在基线上会被误读成句号（29。）
                val degree = reading.unit == "°"
                Row(verticalAlignment = if (degree) Alignment.Top else Alignment.Bottom) {
                    Text(
                        text = reading.value,
                        fontSize = VALUE_SP,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (reading.unit.isNotBlank()) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = reading.unit,
                            fontSize = if (degree) 17.sp else UNIT_SP,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            modifier = Modifier.padding(top = if (degree) 5.dp else 0.dp, bottom = if (degree) 0.dp else 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                if (reading.caption.isNotBlank()) {
                    Text(
                        text = reading.caption,
                        fontSize = CAPTION_SP,
                        lineHeight = 18.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            bar()
        }
    }
}

// ── 刻度条 ────────────────────────────────────────────────────────────

/**
 * 紫外线色阶：绿 → 黄 → 橙 → 红 → 紫（国际 UV index 的通用配色）。
 * 逐日走势面板的五格信号条也用这一份（同包 internal），两处颜色必须是同一套。
 */
internal val UV_LEVEL_COLORS = listOf(
    Color(0xFF55BE6A),
    Color(0xFFD9D24E),
    Color(0xFFE8933F),
    Color(0xFFD9534F),
    Color(0xFF9B59B6),
)

/** UV 的 11 是「极强」上限；超过就顶格，别让圆点跑出轨道。 */
private fun uvFraction(snapshot: WeatherSnapshot): Float {
    val uv = snapshot.now.uvIndex ?: snapshot.daily.firstOrNull()?.uvIndexMax ?: 0
    return uv / 11f
}

/**
 * 露点条的窗口：-10℃ ~ 30℃。露点 16℃ 是"开始发闷"的分水岭、24℃ 是"闷到难受"，
 * 所以窗口不能按温度的那套画（30℃ 的露点已经很极端了）。
 */
private const val DEW_MIN = -10.0
private const val DEW_MAX = 30.0

private fun dewFraction(c: Double?): Float {
    if (c == null) return 0f
    return ((c - DEW_MIN) / (DEW_MAX - DEW_MIN)).toFloat()
}

/** 体感条的温度窗口：-10℃ 到 38℃，覆盖国内几乎所有日子。 */
private const val FEELS_MIN = -10.0
private const val FEELS_MAX = 38.0

private fun feelsFraction(c: Double?): Float {
    if (c == null) return 0f
    return ((c - FEELS_MIN) / (FEELS_MAX - FEELS_MIN)).toFloat()
}

/** 体感色阶直接取样温度色阶 —— 页面上所有"温度的意思"必须是同一套颜色。 */
private fun feelsColors(): List<Color> =
    List(12) { i -> tempColor(FEELS_MIN + (FEELS_MAX - FEELS_MIN) * i / 11.0) }

@Composable
private fun FillBar(frac: Float, color: Color) {
    val track = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.16f)
    // 画布比轨道高：圆角端点 + 高光才有地方画，也不会被父布局裁掉
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val barH = 6.dp.toPx()
        val top = (size.height - barH) / 2f
        val radius = CornerRadius(barH / 2f)
        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, barH), cornerRadius = radius)
        val f = frac.coerceIn(0f, 1f)
        if (f <= 0f) return@Canvas
        // 至少留一个圆头宽：2% 的湿度也得看得见，不能画成 0 像素
        val w = (size.width * f).coerceIn(barH, size.width)
        drawRoundRect(color, topLeft = Offset(0f, top), size = Size(w, barH), cornerRadius = radius)
    }
}

@Composable
private fun GradientBar(frac: Float, colors: List<Color>) {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val barH = 6.dp.toPx()
        val top = (size.height - barH) / 2f
        val radius = CornerRadius(barH / 2f)
        drawRoundRect(
            brush = Brush.horizontalGradient(colors),
            topLeft = Offset(0f, top),
            size = Size(size.width, barH),
            cornerRadius = radius,
        )
        // 圆点：白心 + 一圈更亮的描边（深色底上白点本身就够跳，描边只是防止落在浅黄段看不清）
        val r = barH * 0.62f
        val x = (size.width * frac.coerceIn(0f, 1f)).coerceIn(r, size.width - r)
        val center = Offset(x, size.height / 2f)
        drawCircle(Color.White, radius = r, center = center)
        drawCircle(Color.Black.copy(alpha = 0.22f), radius = r, center = center, style = Stroke(1.2.dp.toPx()))
    }
}

// ── 风：指北针 ────────────────────────────────────────────────────────

@Composable
private fun WindTile(
    snapshot: WeatherSnapshot,
    reading: MetricReading,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    // 角度优先用源给的原值；只有中国天气网那种"只给字不给角度"的源才反解
    val deg = snapshot.now.windDirDeg ?: windDegFromName(snapshot.now.windDir)
    Tile(reading.label, onClick, modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            // 表盘吃满卡片内容区（短边定直径）：写死 100dp 在 174dp 的方卡里四边全是空
            WindDial(deg)
            Text(
                text = reading.value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * 风向罗盘。
 *
 * ⚠ **指针必须是一根从头画到尾的线**：分成「来向一小段 + 去向箭头」两笔，
 * 中间又被中心圆盖掉，看起来就是断成两截的两个零件（上一版的 bug）。
 * 现在一笔从「来自」侧画到尖端，中心圆最后才盖 —— 中间那段被盘面挡住，
 * 露出来的两截共线，读起来才是同一根针。
 */
@Composable
private fun WindDial(deg: Int?) {
    val track = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.30f)
    val ink = MiuixTheme.colorScheme.onSurface
    // 圆心那块底必须**等于瓦片底色**，否则就是一颗"洞"。固定口径（不跟主题色跑）：
    // 浅色 = 白 + 6% 黑；深色 = 黑 + 10% 白。
    // ⚠ 别用 `lerp(surface, onSurface, 5%)`：深色下 surface 本身就接近纯黑，
    // 叠完还是纯黑 —— 用户看到的"调成全黑"就是这个。
    val darkScheme = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val core = if (darkScheme) {
        lerp(Color.Black, Color.White, 0.10f)
    } else {
        lerp(Color.White, Color.Black, 0.06f)
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val c = center
        // 短边定半径，圆盘在容器里居中：容器不是正方形时也不会被拉成椭圆
        val r = size.minDimension / 2f - 2.dp.toPx()
        // 72 根刻度、每 5°：45° 的整数倍正好落在长刻度上（方位角要对得上）
        for (i in 0 until 72) {
            val a = (i * 5.0 - 90.0) * PI / 180.0
            val major = i % 9 == 0
            val inner = r - (if (major) 7.dp else 3.5.dp).toPx()
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color = if (major) track.copy(alpha = 0.55f) else track,
                start = Offset(c.x + dx * inner, c.y + dy * inner),
                end = Offset(c.x + dx * r, c.y + dy * r),
                strokeWidth = if (major) 1.6.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        if (deg != null) {
            // 风向是「来自」：箭头画**去程**（南风 → 箭头朝北），来向那头一颗实心点。
            // 这跟 MIUI 的画法一致，也符合"风往哪吹"的直觉。
            val to = (deg + 90.0) * PI / 180.0
            val ux = cos(to).toFloat()
            val uy = sin(to).toFloat()
            // 针尖顶到盘沿（r），来向端收到 r-4dp：来向那颗点半径 3.2dp，
            // 中心放在 r-1 时整个点会溢出圆盘（就是"尾部超出"），收到 r-4 才刚好压在环内。
            val tipR = r
            val tailR = r - 4.dp.toPx()
            val tip = Offset(c.x + ux * tipR, c.y + uy * tipR)
            val tail = Offset(c.x - ux * tailR, c.y - uy * tailR)

            val head = 11.dp.toPx()
            val wing = 5.5.dp.toPx()
            // ⚠ 杆只画到箭头根部往里 55% 处。画到尖端的话，2dp 粗的**圆头帽**会顶出
            // 三角形顶点 1dp，远看就是"尖头和那根杠糊在一起"（上一版就这样）。
            val shaftEnd = Offset(tip.x - ux * head * 0.55f, tip.y - uy * head * 0.55f)
            drawLine(ink, tail, shaftEnd, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

            // 箭头：实心三角，顶点就是针尖（53° 顶角，比小三角更像箭头）
            val px = -uy
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - ux * head + px * wing, tip.y - uy * head + ux * wing)
                lineTo(tip.x - ux * head - px * wing, tip.y - uy * head - ux * wing)
                close()
            }
            drawPath(path, ink)

            // 来向端：实心点。它必须落在针尾上（上一版只有这颗点、没有针尾，
            // 点孤零零挂在刻度环上，就是"箭头断了"的来源）
            drawCircle(ink, radius = 3.2.dp.toPx(), center = tail)
        }
        // 中心留白盖住刻度线与针身中段，数字才立得住
        drawCircle(core, radius = r * 0.46f, center = c)
    }
}

// ── 气压：表盘 ────────────────────────────────────────────────────────

/** 国内地面气压的实际范围，980~1040 够用；超出就顶格。逐日偏差条用同一窗口。 */
internal const val PRESSURE_MIN = 980.0
internal const val PRESSURE_MAX = 1040.0

/** 标准大气压：逐日偏差条的中轴（低了偏阴雨、高了偏晴好）。 */
internal const val PRESSURE_NORMAL = 1013.0

/**
 * 偏差条只画 ±20hPa：表盘要看全量程（980~1040），但逐日那七行读的是"比标准高还是低"，
 * 用全量程的话一周的 1005~1020 全挤在中轴附近，什么也看不出来。
 */
internal const val PRESSURE_SPAN = 20.0

/**
 * 表盘几何：从「左下」顺时针扫 270°，缺口留在正下方。
 * 起点 135°、终点 405°(=45°)，两端极角都在下两角 —— 所以「低 / 高」两个字必须
 * 贴在弧线两端（±45° 方位、半径 r），而不是钉在卡片左右下角：
 * 卡片比表盘宽，钉在角上就会离弧线老远，两个词跟表盘完全脱节。
 */
private const val GAUGE_START_DEG = 135.0
private const val GAUGE_SWEEP_DEG = 270.0

/** 45° 的余弦：弧线两端点在水平方向离圆心的距离 = r · COS45。 */
private const val COS45 = 0.70710677f

@Composable
private fun PressureTile(
    reading: MetricReading,
    snapshot: WeatherSnapshot,
    onClick: (()-> Unit)?,
    modifier: Modifier,
) {
    val pressure = snapshot.now.pressureHpa?.toDouble()
    val accent = MiuixTheme.colorScheme.primary
    // 主题色必须在组合期取好：Canvas 的 draw lambda 里不能读 @Composable 属性
    val base = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.22f)
    val hint = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Tile(reading.label, onClick, modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // 表盘直径 = 内容区的短边（标题吃掉 19dp 之后剩下的整块地）。
            // 上一版写死 100dp，174dp 的方卡里四边各空 20dp —— 环又细又小、数字挤在中间。
            val r = minOf(maxWidth, maxHeight) / 2 - 2.dp
            // 弧线端点（±45°）在盒内的水平位置离圆心 r·cos45°，两个词就从那里再往里缩一点；
            // 竖直方向直接贴底 —— 端点本来就在盒子下部，贴底就是"紧挨着端点下方"。
            val endInset = maxOf(maxWidth / 2 - r * COS45 + 6.dp, 0.dp)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = center
                // 与上面同一套几何：短边定直径，圆在容器里居中（容器不是正方形时也不变形）
                val pr = size.minDimension / 2f - 2.dp.toPx()
                val tickLen = size.minDimension * 0.052f
                val frac = pressure?.let {
                    ((it - PRESSURE_MIN) / (PRESSURE_MAX - PRESSURE_MIN)).toFloat().coerceIn(0f, 1f)
                }
                val ticks = 60
                for (i in 0..ticks) {
                    val lit = frac != null && i <= (frac * ticks)
                    val a = (GAUGE_START_DEG + GAUGE_SWEEP_DEG * i / ticks) * PI / 180.0
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    val inner = pr - tickLen
                    drawLine(
                        color = if (lit) accent else base,
                        start = Offset(c.x + dx * inner, c.y + dy * inner),
                        end = Offset(c.x + dx * pr, c.y + dy * pr),
                        strokeWidth = if (lit) 2.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = reading.value,
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = reading.unit,
                    fontSize = 11.sp,
                    color = hint,
                    maxLines = 1,
                )
            }
            Text(
                text = WeatherStr.pressureLow(),
                fontSize = 11.sp,
                color = hint,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = endInset),
            )
            Text(
                text = WeatherStr.pressureHigh(),
                fontSize = 11.sp,
                color = hint,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endInset),
            )
        }
    }
}

// ── 日落：弧线 + 倒计时 ───────────────────────────────────────────────

private data class SunState(
    val text: String,
    /** 倒计时是"到日落"还是"到日出"—— 不写清楚，晚上那句"3小时后"会被当成日落。 */
    val caption: String,
    /** 当天日照的完成度；夜里没有圆点。 */
    val progress: Float?,
    val rise: String,
    val set: String,
)

private fun sunState(snapshot: WeatherSnapshot): SunState {
    val today = snapshot.daily.firstOrNull()
    val riseClock = today?.sunrise.orEmpty()
    val setClock = today?.sunset.orEmpty()
    val rise = minuteOfClock(riseClock)
    val set = minuteOfClock(setClock)
    val nowMin = nowMinutes(snapshot)
    if (rise == null || set == null || set <= rise) {
        return SunState("—", "", null, riseClock, setClock)
    }
    return when {
        // 只有"距日出"需要说明：瓦片标题写的是日落，单看"3小时后"会被读成日落倒计时
        nowMin < rise -> {
            val left = rise - nowMin
            SunState(WeatherStr.inHoursMinutes(left / 60, left % 60), WeatherStr.sunrise(), null, riseClock, setClock)
        }
        nowMin >= set -> SunState(WeatherStr.sunHasSet(), "", null, riseClock, setClock)
        else -> {
            val left = set - nowMin
            SunState(
                WeatherStr.inHoursMinutes(left / 60, left % 60),
                "",
                (nowMin - rise).toFloat() / (set - rise),
                riseClock,
                setClock,
            )
        }
    }
}

/** 当地「现在」的分钟数（0..1439）。 */
internal fun nowMinutes(snapshot: WeatherSnapshot): Int {
    val clock = localHourMinute(Clock.System.now().epochSeconds, snapshot.utcOffsetSec)
    val h = clock.substringBefore(':').toIntOrNull() ?: 12
    val m = clock.substringAfter(':').toIntOrNull() ?: 0
    return h * 60 + m
}

@Composable
private fun SunTile(
    reading: MetricReading,
    snapshot: WeatherSnapshot,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val sun = sunState(snapshot)
    Tile(reading.label, onClick, modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = reading.value,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (reading.caption.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = reading.caption,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            }
            SunArc(sun.progress, Modifier.fillMaxWidth().height(44.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SunClock(WeatherStr.sunrise(), sun.rise)
                SunClock(WeatherStr.sunset(), sun.set)
            }
        }
    }
}

@Composable
private fun SunClock(label: String, clock: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = clock.ifBlank { "—" },
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.9f),
            maxLines = 1,
        )
    }
}

/**
 * 日照弧：一条抛物线代表日出→日落，走过的那段亮起来，当前时刻一颗白点。
 *
 * 用抛物线而不是真太阳轨迹：视觉上分不出差别，而且抛物线在参数 t 上就是 x 线性、
 * y = 4·amp·t(1-t)，打点不用解方程。
 */
@Composable
private fun SunArc(progress: Float?, modifier: Modifier) {
    val track = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.22f)
    val warm = Color(0xFFF6BE5C)
    val surface = MiuixTheme.colorScheme.surface
    Canvas(modifier) {
        val baseY = size.height - 1.dp.toPx()
        val amp = size.height * 0.72f
        fun yAt(t: Float): Float = baseY - 4f * amp * t * (1f - t)
        val w = size.width

        val full = Path().apply {
            moveTo(0f, baseY)
            quadraticTo(w / 2f, baseY - 2f * amp, w, baseY)
        }
        drawPath(full, track, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        val p = progress?.coerceIn(0f, 1f)
        if (p != null && p > 0f) {
            val steps = 40
            val walked = Path()
            for (i in 0..steps) {
                val t = p * i / steps
                val x = w * t
                val y = yAt(t)
                if (i == 0) walked.moveTo(x, y) else walked.lineTo(x, y)
            }
            drawPath(walked, warm, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))

            val dot = Offset(w * p, yAt(p))
            drawCircle(warm.copy(alpha = 0.22f), radius = 7.dp.toPx(), center = dot)
            drawCircle(surface, radius = 4.dp.toPx(), center = dot)
            drawCircle(warm, radius = 3.2.dp.toPx(), center = dot)
        }
    }
}
