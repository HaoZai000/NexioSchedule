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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.weather.AirQuality
import com.haooz.chedule.weather.WeatherDay
import com.haooz.chedule.weather.WeatherSnapshot
import com.haooz.chedule.weather.localHourMinute
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalBlurBottomSheetContentExpanded
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 天气页的底部详情面板。
 *
 * 页面本身只留「一眼能看完」的一层：大温度、逐小时曲线、七天、几个入口。
 * 污染物的国标限值、某一天的风和紫外线、某一小时的全部读数 —— 这些属于
 * 「想知道才去看」的信息，塞回主页面只会把它变成长文档。所以做成面板，点哪儿看哪儿。
 *
 * ⚠ 外壳一律用应用统一的 `BlurBottomSheet`（不是自制的遮罩 + 滑入面板）：
 * 玻璃材质、圆角、把手、下拉关闭、预测性返回全在组件里，自己做一套只会两套手感打架。
 */
internal sealed interface WeatherSheetTarget {
    /** 空气质量：头部那颗胶囊点开。 */
    data object Air : WeatherSheetTarget

    /** 详细数据：点哪一格看哪一项的走势（逐时折线 + 逐日条形）。 */
    data class Metric(val kind: DetailMetric) : WeatherSheetTarget

    /**
     * 七天卡片底部的「查看更多信息」：把**数据源给的全部天**一次列出来。
     *
     * ⚠ 不再有"点某一天看那一天"的面板：7 行各点一次要开关 7 次，
     * 想横向比较还得来回切；一次列完反而更快，也省掉一套单日模板。
     * 同理不再有逐小时面板（点某一列没有任何反应）。
     */
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
        LiquidTopBarButton(
            onClick = onDismiss,
            backdrop = LocalSheetContentBackdrop.current ?: fallbackBackdrop,
            icon = MiuixIcons.Normal.Close,
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

/**
 * 顶栏让位高度 = 组件标题栏的**写死高度**（手机 76 / 平板 74）。
 *
 * ⚠ 这里故意不照抄别处的 56 / 58dp：那套是按「第一块是卡片」算的 —— 58dp 时首屏还有
 * 18dp 压在标题栏底部渐变里（alpha≈0.5→0），卡片靠自己的顶内边距把这段吃掉，看不出来。
 * 天气面板的第一块是**裸的大读数**（没有卡片内边距兜着），18dp 进去就是数字上沿被削平、
 * 图标上半截发灰。所以整段让开：渐变尾声归零，第一行落在标题文字下方 ~25dp。
 * 组件标题栏高度改了，这两个数字必须跟着改。
 */
private val SHEET_PHONE_TOP_BAR = 76.dp
private val SHEET_TABLET_TOP_BAR = 74.dp

@Composable
private fun WeatherSheetContent(
    target: WeatherSheetTarget?,
    snapshot: WeatherSnapshot,
    isTablet: Boolean,
) {
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 拉到全屏时铺满（内容自己滚），半屏时按内容高度收着
            .then(
                if (LocalBlurBottomSheetContentExpanded.current) Modifier.fillMaxHeight() else Modifier
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
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
        // 展开态末尾要能滚过状态栏，否则最后一行贴着屏幕顶边
        Spacer(Modifier.height(if (isTablet) 4.dp else statusBarsPadding + 60.dp))
    }
}

private fun sheetTitle(target: WeatherSheetTarget): String = when (target) {
    WeatherSheetTarget.Air -> WeatherStr.airTitle()
    is WeatherSheetTarget.Metric -> WeatherStr.trendTitle(target.kind.name())
    WeatherSheetTarget.Days -> WeatherStr.allDaysTitle()
}

// ── 空气质量 ──────────────────────────────────────────────────────────

/**
 * 空气面板：**读数 → 六项浓度 → 24 小时空气**，三段一张卡（读数那段不套卡，跟走势面板一致）。
 *
 * 内容上的取舍：
 * - 六项浓度**各自按国标判色**（一级限以内绿、二级限以内黄、超了橙），不是统一涂成
 *   当前 AQI 的颜色 —— 统一涂色的话，PM2.5 已经 90 了也会显示成绿色。
 * - 原来的 0~500 六段色阶条删掉了：42 落在第一格里，剩下八分之七全是空轨道，
 *   只占高度不给信息。趋势改由下面 24 小时那组柱子说。
 * - 浓度单位不逐格重复，收到脚注一行（六列里再塞 μg/m³ 会挤成两行）。
 */
@Composable
private fun AirSheetBody(snapshot: WeatherSnapshot) {
    val air = snapshot.air
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
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
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
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

/**
 * 「查看更多信息」：把数据源给的**每一天**排成一条竖列，一天三行。
 *
 * 为什么不是"点一天看一天"：7 天要开关 7 次，中途想回头比一下就得再来一遍；
 * 一天三行（日期 + 温度跨度 / 天气 + 降水概率 / 其余读数）密而不挤，
 * 一屏能看三四天，滚动两下就到底。
 *
 * ⚠ 天数用 `snapshot.daily` 的**全长**，不截 7：卡片上只摆 7 行是版式决定，
 * 这里既然叫"全部"，源多给几天就该多列几天。
 */
@Composable
private fun AllDaysBody(snapshot: WeatherSnapshot) {
    val days = snapshot.daily
    if (days.isEmpty()) {
        Text(
            text = WeatherStr.trendEmpty(),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 8.dp),
        )
        return
    }
    val minTemp = days.mapNotNull { it.low }.minOrNull() ?: 0.0
    val maxTemp = days.mapNotNull { it.high }.maxOrNull() ?: 1.0
    val divider = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
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

/**
 * 一天两行（+ 一行小字读数）。
 *
 * ⚠ 字号只有两级：**15sp**（日期、天气、温度、降水概率）和 **12sp**（其余读数）。
 * 上一版在一天里塞了 15/14/13/12 四种字号，还让"月日"单独占一个 12sp 位，
 * 同一行里大小跳来跳去，看着就是"乱"。
 */
@Composable
private fun DayBlock(day: WeatherDay, index: Int, minTemp: Double, maxTemp: Double) {
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val isToday = index == 0
    val accent = MiuixTheme.colorScheme.primary
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
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = dayLabel(index, day.date),
                fontSize = 15.sp,
                fontWeight = if (isToday) FontWeight.Medium else null,
                color = if (dateText != null) secondary else MiuixTheme.colorScheme.onSurface,
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
                color = MiuixTheme.colorScheme.onSurface,
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
                color = MiuixTheme.colorScheme.onSurface,
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
