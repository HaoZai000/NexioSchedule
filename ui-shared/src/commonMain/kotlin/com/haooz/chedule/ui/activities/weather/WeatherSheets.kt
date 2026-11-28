package com.haooz.chedule.ui.activities.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.WeatherStr
import com.haooz.chedule.ui.activities.today.weatherIconRes
import com.haooz.nexioui.iconbutton.IconButton
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.weather.AirQuality
import com.haooz.chedule.weather.WeatherDay
import com.haooz.chedule.weather.WeatherSnapshot
import com.haooz.chedule.weather.localHourMinute
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.chedule.ui.components.sheetContentBottomInset
import org.jetbrains.compose.resources.painterResource
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.card.CardDefaults
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Close
import com.haooz.nexioui.blurbottomsheet.BlurBottomSheet
import com.haooz.nexioui.blurbottomsheet.BlurBottomSheetTablet
import com.haooz.nexioui.blurbottomsheet.LocalBlurBottomSheetContentExpanded
import com.haooz.nexioui.blurbottomsheet.LocalSheetContentBackdrop
import com.haooz.nexioui.blurbottomsheet.LocalSheetTopBarMaterial
import com.haooz.nexioui.theme.NexioTheme

internal sealed interface WeatherSheetTarget {
    /** 空气质量：头部那颗胶囊点开。 */
    data object Air : WeatherSheetTarget

    /** 详细数据：点哪一格看哪一项的走势（逐时折线 + 逐日条形）。 */
    data class Metric(val kind: DetailMetric) : WeatherSheetTarget

    /** 显示数据源返回的所有天数，不受首页预览行数限制。 */
    data object Days : WeatherSheetTarget
}

/**
 * 面板宿主。
 *
 * ⚠ 关闭动画期间 `target` 已经是 null，但面板还要画完 —— 所以用一个普通数组记住
 * 最后一次的目标（进快照会引发额外重组，这个项目里同类做法见卡片的 tintLast）。
 */
@Composable
internal fun WeatherSheetHost(
    target: WeatherSheetTarget?,
    snapshot: WeatherSnapshot,
    onDismiss: () -> Unit,
) {
    val hold = remember { arrayOfNulls<WeatherSheetTarget>(1) }
    if (target != null) hold[0] = target
    val current = target ?: hold[0]
    val isTablet = screenWidthDp() >= 600
    val title = current?.let { sheetTitle(it) }.orEmpty()
    val content: @Composable () -> Unit = {
        WeatherSheetContent(current, snapshot, isTablet)
    }
    // 兜底采样层：标题栏按钮读到的 LocalSheetContentBackdrop 在弹窗内恒非空（组件总会下发
    // 真实层或占位层），这里只是满足类型可空 —— 别为它加分支。
    val fallbackBackdrop = rememberLayerBackdrop()
    val closeAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        IconButton(
            onClick = onDismiss,
            backdrop = LocalSheetContentBackdrop.current ?: fallbackBackdrop,
            icon = NexioIcons.Normal.Close,
            contentDescription = CommonStr.close(),
            modifier = Modifier.padding(start = 18.dp),
            // 比「添加课程」那颗 24dp 略小：这里是纯关闭，不该抢读数
            iconSize = 22.dp,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }

    if (isTablet) {
        BlurBottomSheetTablet(
            show = target != null,
            title = title,
            dimBackground = true,
            isBottomAligned = true,
            onDismissRequest = onDismiss,
            startAction = closeAction,
            content = content,
        )
    } else {
        BlurBottomSheet(
            show = target != null,
            title = title,
            dimBackground = true,
            onDismissRequest = onDismiss,
            startAction = closeAction,
            content = content,
        )
    }
}

// 首项是没有卡片内边距的读数，顶部留白须覆盖整个面板标题栏。
private val SHEET_PHONE_TOP_BAR = 76.dp
private val SHEET_TABLET_TOP_BAR = 74.dp

// sheet 内卡片底色与其它面板一致：莫奈取 surfaceContainerHigh，非莫奈沿用硬编码。
@Composable
internal fun weatherSheetCardColors() = CardDefaults.defaultColors(
    color = if (NexioTheme.isDynamicColor) NexioTheme.colorScheme.surfaceContainerHigh
    else if (isAppDarkTheme()) Color(0xFF303030) else Color(0xFFFFFFFF),
    contentColor = NexioTheme.colorScheme.onSurface,
)

@Composable
private fun WeatherSheetContent(
    target: WeatherSheetTarget?,
    snapshot: WeatherSnapshot,
    isTablet: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 拉到全屏时铺满（内容自己滚），半屏时按内容高度收着
            .then(
                if (LocalBlurBottomSheetContentExpanded.current) Modifier.fillMaxHeight() else Modifier
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .sheetContentBottomInset(isTablet),
    ) {
        // ⚠ 顶层 Column 不能挂 spacedBy：那样这个让位 Spacer 下面还会再叠一次间距，
        // 让位高度就等于"顶栏 + 12dp"，每次调都差一截。间距交给内层 Column。
        Spacer(Modifier.height(if (isTablet) SHEET_TABLET_TOP_BAR else SHEET_PHONE_TOP_BAR))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (target != null) {
                when (target) {
                    WeatherSheetTarget.Air -> AirSheetBody(snapshot)
                    is WeatherSheetTarget.Metric -> MetricTrendBody(snapshot, target.kind)
                    WeatherSheetTarget.Days -> AllDaysBody(snapshot)
                }
            }
        }
    }
}

private fun sheetTitle(target: WeatherSheetTarget): String = when (target) {
    WeatherSheetTarget.Air -> WeatherStr.airTitle()
    is WeatherSheetTarget.Metric -> WeatherStr.trendTitle(target.kind.name())
    WeatherSheetTarget.Days -> WeatherStr.allDaysTitle()
}

// ── 空气质量 ──────────────────────────────────────────────────────────

/** 各污染物按自身的浓度限值着色，而非沿用总体 AQI 的颜色。 */
@Composable
private fun AirSheetBody(snapshot: WeatherSnapshot) {
    val air = snapshot.air
    val secondary = NexioTheme.colorScheme.onSurfaceVariantSummary
    if (air == null) {
        Text(
            text = WeatherStr.aqiMissing(),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = secondary,
            modifier = Modifier.padding(start = 8.dp),
        )
        return
    }
    val color = aqiColor(air.aqi)

    // ① 读数 + 等级 + 一句话
    // 弹窗里不在卡片内的裸文字左对齐 → 左边让 8dp（卡片正文是 14dp 内边距，贴边会错位）
    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = air.aqi?.toString() ?: WeatherStr.noDataField(),
                fontSize = 44.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
            )
            if (air.level.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .background(color.copy(alpha = 0.14f), ContinuousCapsule())
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = air.level,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
        if (air.tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = air.tip,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = secondary,
            )
        }
    }

    // ② 六项浓度
    val cells = pollutantCells(air)
    if (cells.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
            colors = weatherSheetCardColors(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 15.dp),
                verticalAlignment = Alignment.Top,
            ) {
                cells.forEach { cell ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = cell.value,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = cell.color,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = cell.name,
                            fontSize = 11.sp,
                            color = secondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    // ③ 24 小时空气（源里没有逐时 AQI 就整段不出现）
    val hourly = airTrend(snapshot)
    if (hourly.size >= 4) {
        val high = hourly.maxOf { it.second }
        val low = hourly.minOf { it.second }
        TrendSection(
            title = WeatherStr.airHourlyTitle(),
            trailing = WeatherStr.trendRange("$high", "$low"),
        ) {
            AqiBars(hourly)
        }
    }

    // ④ 脚注
    val meta = buildList {
        if (air.primary.isNotBlank()) add(WeatherStr.primaryPollutantValue(air.primary))
        add(WeatherStr.airUnits())
    }.joinToString(" · ")
    Text(
        text = meta,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = secondary.copy(alpha = 0.85f),
        modifier = Modifier.padding(start = 8.dp),
    )
}

/** 一格浓度：名字 + 值 + 按**自己**的国标等级取的颜色。 */
private data class PollutantCell(val name: String, val value: String, val color: Color)

/**
 * 六项浓度的国标两级限值（24h 平均）：≤一级算优、≤二级算良、超了算差。
 * CO 单位是 mg/m³，其余 μg/m³ —— 阈值不一样，不能共用一套数字。
 */
private fun pollutantCells(air: AirQuality): List<PollutantCell> {
    val items = listOf(
        Triple("PM2.5", air.pm25, 35.0 to 75.0),
        Triple("PM10", air.pm10, 50.0 to 150.0),
        Triple("SO₂", air.so2, 50.0 to 150.0),
        Triple("NO₂", air.no2, 40.0 to 80.0),
        Triple("O₃", air.o3, 100.0 to 160.0),
        Triple("CO", air.co, 4.0 to 10.0),
    )
    return items.mapNotNull { (name, v, limits) ->
        if (v == null) return@mapNotNull null
        val color = when {
            v <= limits.first -> Color(0xFF47B881)
            v <= limits.second -> Color(0xFFE8B23A)
            else -> Color(0xFFE8833A)
        }
        PollutantCell(name, trimNum(v), color)
    }
}

/** 逐时 AQI（标签, 值）。源里没有逐时 AQI 时返回空表。 */
private fun airTrend(snapshot: WeatherSnapshot): List<Pair<String, Int>> =
    snapshot.hourly.take(24).mapNotNull { hour ->
        val v = hour.aqi ?: return@mapNotNull null
        localHourMinute(hour.epochSec, snapshot.utcOffsetSec) to v
    }

/**
 * 24 小时 AQI 柱状图：每根柱子按自己的等级上色。
 *
 * 用柱不用线：AQI 读的是"每个时段落在哪一档"，柱子自带"高度 + 颜色"两重编码；
 * 折线会把相邻两小时连成缓坡，反而看不出跨档。
 */
@Composable
private fun AqiBars(points: List<Pair<String, Int>>) {
    val secondary = NexioTheme.colorScheme.onSurfaceVariantSummary
    // 纵轴上界向上取到 50 的倍数：AQI 的分档线都是 50/100/150，柱子对齐分档才有意义
    val top = (((points.maxOf { it.second }) / 50) + 1) * 50
    Canvas(modifier = Modifier.fillMaxWidth().height(104.dp)) {
        val n = points.size
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val radius = CornerRadius(minOf(bw, 8.dp.toPx()) / 2f)
        points.forEachIndexed { i, (_, v) ->
            val h = (size.height * (v.toFloat() / top)).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = aqiColor(v),
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = radius,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    if (points.size >= 2) {
        val marks = listOf(0, points.size / 4, points.size / 2, points.size * 3 / 4, points.lastIndex).distinct()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            marks.forEachIndexed { order, i ->
                Text(
                    text = if (order == 0) WeatherStr.now() else points[i].first,
                    fontSize = 11.sp,
                    color = secondary,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── 全部天的详情 ──────────────────────────────────────────────────────

@Composable
private fun AllDaysBody(snapshot: WeatherSnapshot) {
    val days = snapshot.daily
    if (days.isEmpty()) {
        Text(
            text = WeatherStr.trendEmpty(),
            fontSize = 13.sp,
            color = NexioTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 8.dp),
        )
        return
    }
    val minTemp = days.mapNotNull { it.low }.minOrNull() ?: 0.0
    val maxTemp = days.mapNotNull { it.high }.maxOrNull() ?: 1.0
    val divider = NexioTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        colors = weatherSheetCardColors(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            days.forEachIndexed { index, day ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(divider)
                    )
                }
                DayBlock(day, index, minTemp, maxTemp)
            }
        }
    }
}

@Composable
private fun DayBlock(day: WeatherDay, index: Int, minTemp: Double, maxTemp: Double) {
    val secondary = NexioTheme.colorScheme.onSurfaceVariantSummary
    val isToday = index == 0
    val accent = NexioTheme.colorScheme.primary
    val dateText = monthDayOf(day.date)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        // ① 日期 + 相对日名 … 低温 + 条 + 高温
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 日期在前、今天/明天在后：一行七天的列表里"哪天"比"第几天"更好对
            dateText?.let { md ->
                Text(
                    text = md,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = NexioTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = dayLabel(index, day.date),
                fontSize = 15.sp,
                fontWeight = if (isToday) FontWeight.Medium else null,
                color = if (dateText != null) secondary else NexioTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = day.low?.let { "${it.toInt()}°" } ?: "—",
                fontSize = 15.sp,
                color = secondary,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            TempRangeBar(
                low = day.low,
                high = day.high,
                minTemp = minTemp,
                maxTemp = maxTemp,
                modifier = Modifier.width(60.dp).height(6.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = day.high?.let { "${it.toInt()}°" } ?: "—",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = NexioTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(7.dp))
        // ② 图标 + 天气 + 降水概率
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(weatherIconRes(day.dayText, false)),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = listOf(day.dayText, day.nightText)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" 转 ")
                    .ifBlank { WeatherStr.noDataField() },
                fontSize = 15.sp,
                color = NexioTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            day.precipProb?.let { prob ->
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${WeatherStr.rainChance()} $prob%",
                    fontSize = 15.sp,
                    fontWeight = if (prob >= 20) FontWeight.Medium else null,
                    color = if (prob >= 20) accent else secondary,
                    maxLines = 1,
                )
            }
        }
        val facts = dayFacts(day)
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = facts.joinToString(" · "),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 一天里其余的读数，只留三项：紫外线 / AQI / 风速。
 * 降水概率在第二行已经单独给了，降水量、日出日落这些主页面/走势面板里都有，
 * 全塞进来这行会挤成两行小字，反而没人读。
 */
private fun dayFacts(day: WeatherDay): List<String> = buildList {
    day.uvIndexMax?.let { add("${WeatherStr.uvIndex()} $it") }
    day.aqi?.let { add("AQI $it") }
    day.windSpeedKmh?.let { add("${WeatherStr.wind()} ${trimNum(it)} ${WeatherStr.unitKmh()}") }
}

/** "2026-11-04" → "11月4日"；不是这个格式就不显示。 */
private fun monthDayOf(date: String): String? {
    if (date.length != 10) return null
    val m = date.substring(5, 7).toIntOrNull() ?: return null
    val d = date.substring(8, 10).toIntOrNull() ?: return null
    return WeatherStr.monthDay(m, d)
}
