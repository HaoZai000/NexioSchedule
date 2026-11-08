package com.haooz.chedule.ui.activities.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.WeatherStr
import com.haooz.chedule.ui.activities.today.weatherIconRes
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.weather.LifeIndex
import com.haooz.chedule.weather.RainNowcast
import com.haooz.chedule.weather.WeatherAlert
import com.haooz.chedule.weather.WeatherDay
import com.haooz.chedule.weather.WeatherSnapshot
import com.haooz.chedule.weather.WeatherStore
import com.haooz.chedule.weather.alertLevelWord
import com.haooz.chedule.weather.floorDiv
import com.haooz.chedule.weather.splitAlertSource
import com.haooz.chedule.weather.localHourMinute
import com.haooz.chedule.weather.weekdayIndex
import com.haooz.chedule.weather.windPowerFromKmh
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.math.sqrt
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 天气详情页。
 *
 * ## 数据从哪来
 *
 * 不接路由参数（`AppRoute` 只有稳定 id，没有参数通道），也不各自抓一份 ——
 * 直接读 [WeatherStore]：今日卡先落地，本页只消费。所以从卡片点进来是**零等待**，
 * 进程被杀后恢复本页则走加载态、由「重试」重新拉。
 *
 * ## 为什么所有可空字段都按「有才显示」
 *
 * 三家源覆盖的字段不一样（小米没有能见度、Open-Meteo 没有生活指数、中国天气网只有当天）。
 * 把缺的项显示成「—」会让整页全是横杠，看起来像坏了；隐藏掉反而像「本来就不需要」。
 */
@Composable
fun WeatherScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    contentTopPadding: Dp = 0.dp,
) {
    val snapshot by WeatherStore.snapshot.collectAsState()
    val loading by WeatherStore.loading.collectAsState()
    val failed by WeatherStore.failed.collectAsState()
    val scope = rememberCoroutineScope()
    // 哪张详情面板开着。页面只留"一眼能看完"的一层，污染物限值、某一天的风、
    // 某一小时的全部读数都在面板里 —— 主页面越长，越不像一个"看天气"的页面。
    var sheetTarget by remember { mutableStateOf<WeatherSheetTarget?>(null) }

    val isTablet = screenWidthDp() >= 600
    val horizontalPadding = if (isTablet) {
        val widthDp = screenWidthDp()
        ((widthDp - 600).coerceIn(0, 600) / 600f * 108 + 20).dp
    } else 16.dp

    Scaffold(topBar = {}) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                    .collapsibleTopInset(scrollBehavior)
                    .then(scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = paddingValues.calculateTopPadding() + contentTopPadding +
                        CollapsibleTopAppBarDefaults.CollapsedHeight +
                        (if (isTablet) 24.dp else 12.dp),
                    bottom = 60.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val data = snapshot) {
                    null if loading -> item { StatusCard(WeatherStr.loading(), null) }
                    null -> item {
                        StatusCard(
                            title = if (failed) WeatherStr.failed() else WeatherStr.emptyHint(),
                            hint = if (failed) WeatherStr.failedHint() else WeatherStr.tapRefresh(),
                            onTap = { scope.launch { WeatherStore.requestRefresh() } },
                        )
                    }
                    else -> {
                        item {
                            HeroCard(
                                snapshot = data,
                                loading = loading,
                                onRefresh = { scope.launch { WeatherStore.requestRefresh() } },
                                onOpenAir = { sheetTarget = WeatherSheetTarget.Air },
                            )
                        }
                        if (data.alerts.isNotEmpty()) item { AlertCard(data.alerts) }
                        // 两小时逐分钟降水：只有当窗口内确实出现降雨时才展示卡片。
                        // 逐分钟强度全为 0（两小时内没有雨）时隐藏整张卡片，
                        // 避免一排空柱图或「无降水」文字占位。
                        data.nowcast?.let { cast ->
                            if (cast.values.any { it > 0.1f }) {
                                item {
                                    when {
                                        cast.values.isNotEmpty() -> NowcastChartCard(cast)
                                        cast.headline.isNotBlank() || cast.detail.isNotBlank() ->
                                            SectionCard(title = WeatherStr.nowcastTitle()) {
                                                Text(
                                                    text = cast.headline.ifBlank { cast.detail },
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurface,
                                                )
                                                if (cast.detail.isNotBlank() && cast.detail != cast.headline) {
                                                    Spacer(Modifier.height(5.dp))
                                                    Text(
                                                        text = cast.detail,
                                                        fontSize = 13.sp,
                                                        lineHeight = 19.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        }
                        if (data.hourly.isNotEmpty()) {
                            item { HourlyCard(data) }
                        }
                        if (data.daily.isNotEmpty()) {
                            item {
                                DailyCard(data) { sheetTarget = WeatherSheetTarget.Days }
                            }
                        }
                        // 空气质量不再单独占一张卡：头部那颗胶囊点开就是它（整卡信息量比
                        // 主页面其它卡多一倍，摆在这里会把"看天气"变成"看报表"）
                        item {
                            DetailGrid(data) { sheetTarget = WeatherSheetTarget.Metric(it) }
                        }
                        if (data.indices.isNotEmpty()) item { IndexCard(data) }
                        item { SourceFooter(data) }
                    }
                }
            }

            // 面板压在最上层（含遮罩），必须在 LazyColumn 之后组合
            snapshot?.let { snap ->
                WeatherSheetHost(target = sheetTarget, snapshot = snap) { sheetTarget = null }
            }
        }
    }
}

// ── 通用外壳 ──────────────────────────────────────────────────────────

private val CARD_H_PADDING = 18.dp

/**
 * 分区卡：标题 + 内容。
 *
 * @param bleed 内容是否**通栏**。横向滚动区（逐小时）必须通栏 —— 左右内边距由
 *   `LazyRow` 自己的 `contentPadding` 给，否则条目滚到一半就被卡片内边距切掉，
 *   看起来像「滑动区没铺满卡」。标题不受影响，仍对齐正文。
 */
@Composable
internal fun SectionCard(
    title: String,
    bleed: Boolean = false,
    bottomPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = bottomPadding)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = CARD_H_PADDING),
            )
            Spacer(Modifier.height(12.dp))
            if (bleed) {
                content()
            } else {
                // ⚠ 必须是 Column：Box 会把标题下的兄弟节点全部叠在同一个原点（短时降水两行字重叠、
                // 空气质量整块糊在一起，都是这个坑）。通栏那条只能是单个节点（LazyRow）。
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = CARD_H_PADDING)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, hint: String?, onTap: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
                .padding(horizontal = CARD_H_PADDING, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── 头部：地点 + 大温度 ────────────────────────────────────────────────

@Composable
private fun HeroCard(
    snapshot: WeatherSnapshot,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenAir: () -> Unit,
) {
    val now = snapshot.now
    val today = snapshot.daily.firstOrNull()
    val tempText = now.tempC?.let { "${it.toInt()}°" } ?: "—"
    // 天光跟着"什么天气 + 什么时段"走：晴天暖、雨天冷、雷天发紫、夜里压成深蓝
    val darkTheme = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val (skyTop, skyBottom) = heroSky(snapshot, darkTheme)

    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Box {
            // 一层天空色天光（左上 → 右下）：纯色卡片在大屏上太"塑料"，
            // 又不能用壁纸采样（页面独立）。透明度压得很低，只当"光"，不抢文字。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            // 深色底本来就暗，低 alpha 就够"发光"；浅色底要和白色拉开，
                            // 必须把颜色本身抬亮（见 heroSky）再加大 alpha，否则是一块灰渍
                            0f to skyTop.copy(alpha = if (darkTheme) 0.34f else 0.62f),
                            0.55f to skyBottom.copy(alpha = if (darkTheme) 0.14f else 0.30f),
                            1f to Color.Transparent,
                        )
                    )
            )
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = snapshot.placeName.ifBlank { WeatherStr.title() },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (loading) WeatherStr.refreshing() else WeatherStr.updatedAt(
                            now.observedAt.ifBlank { clock(snapshot) }
                        ),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .clip(ContinuousRoundedRectangle(8.dp))
                            .clickable { onRefresh() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = tempText,
                        fontSize = 72.sp,
                        lineHeight = 78.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.padding(bottom = 14.dp)) {
                        Text(
                            text = now.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        val feels = now.feelsLikeC
                        if (feels != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = WeatherStr.feelsLike(feels.toInt().toString()),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val high = today?.high
                    val low = today?.low
                    if (high != null && low != null) {
                        Text(
                            text = WeatherStr.highLow(high.toInt().toString(), low.toInt().toString()),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                    val air = snapshot.air
                    // 有 AQI 或任一颗粒物浓度就算"有空气质量可看" —— 只有浓度没有 AQI 的源
                    // （少见）也得保留入口，否则那几项数据在页面上没有任何去处
                    if (air != null && (air.aqi != null || air.pm25 != null || air.pm10 != null)) {
                        if (high != null && low != null) Spacer(Modifier.width(10.dp))
                        AirCapsule(air.aqi, air.level, onOpenAir)
                    }
                }
            }
        }
    }
}

// ── 头部天光：天气 × 时段 ────────────────────────────────────────────

/** 一天四个时段：日出前/后各留 30 分钟过渡，晨、暮各 90 分钟。 */
private enum class SkyPhase { Dawn, Day, Dusk, Night }

private fun skyPhase(snapshot: WeatherSnapshot): SkyPhase {
    val today = snapshot.daily.firstOrNull()
    val rise = today?.sunrise?.let(::minuteOfClock)
    val set = today?.sunset?.let(::minuteOfClock)
    if (rise == null || set == null || set <= rise) {
        // 缺日出日落才退回源的 isDay（再缺就当白天，宁可亮不要一片黑）
        return if (snapshot.now.isDay == false) SkyPhase.Night else SkyPhase.Day
    }
    val m = nowMinutes(snapshot)
    return when {
        m < rise - 30 || m >= set + 30 -> SkyPhase.Night
        m < rise + 90 -> SkyPhase.Dawn
        m >= set - 90 -> SkyPhase.Dusk
        else -> SkyPhase.Day
    }
}

/**
 * 现象 → 天空色（上, 下）。
 *
 * 用关键词而不是天气码：`now.text` 是融合层给的**中文现象**（"阵雨"/"小雪"/"霾"），
 * 天气码在三家源里编号体系不同，归一步骤已经做过一次，这里再判一次反而多一个失真点。
 * 顺序必须**从重到轻**："雷阵雨"要判成雷而不是雨。
 */
private fun weatherSky(text: String): Pair<Color, Color> = when {
    text.contains("雷") -> Color(0xFF6E6FA4) to Color(0xFF9A93C4)
    text.contains("雪") || text.contains("冰") -> Color(0xFFA9CBE6) to Color(0xFFD6E8F5)
    text.contains("雨") -> Color(0xFF5C86A8) to Color(0xFF87A6BE)
    text.contains("雾") || text.contains("霾") -> Color(0xFFA39A8B) to Color(0xFFC8C0B3)
    text.contains("沙") || text.contains("尘") -> Color(0xFFC99A5C) to Color(0xFFE3C08A)
    text.contains("阴") -> Color(0xFF87929E) to Color(0xFFACB5BE)
    text.contains("云") -> Color(0xFF8FB4CF) to Color(0xFFBACDDA)
    else -> Color(0xFFF0B45F) to Color(0xFF82C3EA) // 晴：上暖下蓝
}

/**
 * 最终天光色。
 *
 * ⚠ **深浅两套**：同一套饱和色在深色卡片上正好"发光"，在白色卡片上却会变成一块灰渍
 * （明度不够，跟背景糊在一起）。浅色主题把色相往白里抬 40%~46% 提高明度，再靠更大的
 * alpha 保住"看得见"；深色主题保持原色、用小 alpha。
 */
private fun heroSky(snapshot: WeatherSnapshot, dark: Boolean): Pair<Color, Color> {
    val (top, bottom) = weatherSky(snapshot.now.text)
    val (t, b) = when (skyPhase(snapshot)) {
        // 夜里不看现象：一切压成深蓝，否则"晴天"的暖黄会在午夜变成一块脏橘
        SkyPhase.Night -> Color(0xFF2B3963) to Color(0xFF48527F)
        SkyPhase.Dawn -> lerp(top, Color(0xFFF09A6C), 0.55f) to lerp(bottom, Color(0xFFEFC79E), 0.35f)
        SkyPhase.Dusk -> lerp(top, Color(0xFFE97A55), 0.60f) to lerp(bottom, Color(0xFF9C6FA8), 0.35f)
        SkyPhase.Day -> top to bottom
    }
    return if (dark) {
        t to b
    } else {
        lerp(t, Color.White, 0.40f) to lerp(b, Color.White, 0.46f)
    }
}

/**
 * 空气质量胶囊，也是空气面板的唯一入口。
 *
 * 头部只放这一颗：数据来源（小米 / Open-Meteo / 中国天气网）挪到页面底部 ——
 * 那是"这数从哪来"的注脚，不是用户打开天气要看的东西，三颗彩色标签摆在温度下面
 * 只会把视线从大温度上拽走。
 */
@Composable
private fun AirCapsule(aqi: Int?, level: String, onClick: () -> Unit) {
    val color = aqiColor(aqi)
    val label = when {
        aqi != null && level.isNotBlank() -> "$aqi $level"
        aqi != null -> "$aqi"
        // 只有浓度没有 AQI 的源（少见）：显示等级或直接显示"空气"
        level.isNotBlank() -> level
        else -> WeatherStr.airTitle()
    }
    Box(
        modifier = Modifier
            .clip(ContinuousCapsule())
            .clickable { onClick() }
            .background(color.copy(alpha = 0.14f), ContinuousCapsule())
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = WeatherStr.aqiShort(label),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

// ── 短时降水：分钟级柱状图 ────────────────────────────────────────────

private val NOWCAST_CHART_H = 64.dp

/** 量程 */
private const val NOWCAST_MAX = 1.2f

@Composable
private fun NowcastChartCard(cast: RainNowcast) {
    val darkTheme = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = CARD_H_PADDING, end = CARD_H_PADDING, top = 16.dp, bottom = 14.dp
            )
        ) {
            Text(
                text = cast.detail.ifBlank { cast.headline },
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                NowcastChart(
                    values = cast.values,
                    darkTheme = darkTheme,
                    modifier = Modifier.weight(1f).height(NOWCAST_CHART_H),
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.width(12.dp).height(NOWCAST_CHART_H)) {
                    listOf(
                        WeatherStr.rainBig() to 0.dp,
                        WeatherStr.rainMedium() to NOWCAST_CHART_H * (1f / 3f),
                        WeatherStr.rainSmall() to NOWCAST_CHART_H * (2f / 3f),
                    ).forEach { (label, y) ->
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.offset(y = y + 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(end = 20.dp)) {
                Text(
                    text = WeatherStr.now(),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = WeatherStr.rainIn1Hour(),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = WeatherStr.rainIn2Hours(),
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NowcastChart(values: List<Float>, darkTheme: Boolean, modifier: Modifier) {
    val grid = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.34f)
    val bar = if (darkTheme) Color(0xFF9AB9E6) else Color(0xFF4A7FC1)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val line = 1.dp.toPx()
        for (i in 0..2) {
            val y = h * i / 3f
            dashedLine(grid, Offset(0f, y), Offset(w, y), line)
        }
        drawLine(color = grid, start = Offset(0f, h), end = Offset(w, h), strokeWidth = line)
        drawLine(color = grid, start = Offset(0f, 0f), end = Offset(0f, h), strokeWidth = line)
        dashedLine(grid, Offset(w, 0f), Offset(w, h), line)

        val bars = values.chunked(2) { part ->
            if (part.size == 1) part[0] else (part[0] + part[1]) / 2f
        }
        val cell = if (bars.isEmpty()) w else w / bars.size
        val barW = (cell * 0.62f).coerceAtLeast(1.5.dp.toPx())
        bars.forEachIndexed { i, v ->
            if (v <= 0f) return@forEachIndexed
            val barH = ((v / NOWCAST_MAX) * h).coerceIn(2.dp.toPx(), h)
            drawRoundRect(
                color = bar,
                topLeft = Offset(i * cell + (cell - barW) / 2f, h - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

private fun DrawScope.dashedLine(color: Color, start: Offset, end: Offset, strokeWidth: Float) {
    val dash = 4.dp.toPx()
    val gap = 4.dp.toPx()
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = sqrt(dx * dx + dy * dy)
    if (len <= 0f) return
    val ux = dx / len
    val uy = dy / len
    var d = 0f
    while (d < len) {
        val e = (d + dash).coerceAtMost(len)
        drawLine(
            color = color,
            start = Offset(start.x + ux * d, start.y + uy * d),
            end = Offset(start.x + ux * e, start.y + uy * e),
            strokeWidth = strokeWidth,
        )
        d += dash + gap
    }
}

// ── 预警 ──────────────────────────────────────────────────────────────

@Composable
private fun AlertCard(alerts: List<WeatherAlert>) {
    val darkTheme = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val tone = alertColor(alerts.first())
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Box {
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        0f to tone.copy(alpha = if (darkTheme) 0.16f else 0.10f),
                        0.55f to tone.copy(alpha = if (darkTheme) 0.05f else 0.03f),
                        1f to Color.Transparent,
                    )
                )
            )
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = CARD_H_PADDING, vertical = 16.dp)
            ) {
                alerts.forEachIndexed { index, alert ->
                    AlertRow(alert)
                    if (index != alerts.lastIndex) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .height(0.5.dp)
                                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f))
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: WeatherAlert) {
    val tone = alertColor(alert)
    val (body, source) = splitAlertSource(alert.detail)
    var expanded by remember(alert) { mutableStateOf(false) }
    var overflowing by remember(alert) { mutableStateOf(false) }
    val issued = alertClock(alert.pubTime)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = alert.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            AlertLevelChip(alert, tone)
        }
        if (issued.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = WeatherStr.alertIssued(issued),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(0.5.dp)
                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (overflowing != it.didOverflowHeight) overflowing = it.didOverflowHeight },
            )
            if (overflowing || expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (expanded) WeatherStr.alertCollapse() else WeatherStr.alertExpand(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = tone,
                    modifier = Modifier
                        .clip(ContinuousCapsule())
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                )
            }
        }
        if (source.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = WeatherStr.alertSource(source),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun AlertLevelChip(alert: WeatherAlert, tone: Color) {
    val label = alertLevelWord(alert)
    if (label.isBlank()) return
    Box(
        modifier = Modifier
            .clip(ContinuousCapsule())
            .background(tone.copy(alpha = 0.16f), ContinuousCapsule())
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tone, maxLines = 1)
    }
}

/** "19:39" / "2026-11-05 19:39" 都归到 "19:39"。 */
private fun alertClock(raw: String): String {
    val colon = raw.indexOf(':')
    if (colon >= 2 && colon + 2 < raw.length) {
        val hh = raw.substring(colon - 2, colon)
        val mm = raw.substring(colon + 1, colon + 3)
        if (hh.all { it.isDigit() } && mm.all { it.isDigit() }) return "$hh:$mm"
    }
    return raw.trim()
}

// ── 逐小时：温度曲线 ──────────────────────────────────────────────────

/**
 * 一格逐时数据。
 *
 * 「现在」是**独立一列**（不是改写某个钟点、也不是一根细线）：MIUI 天气就是这样 ——
 * 前一小时 / 现在 / 下一个整点…，用户既能看到"这一小时几点"，也能看到"此刻在哪"。
 */
private data class HourPoint(
    val epochSec: Long,
    val label: String,
    val isNow: Boolean,
    val tempC: Double?,
    val text: String,
    val precipProb: Int?,
    val windPower: String,
    /** 该列是否按夜间图标渲染（日落后到日出前）。 */
    val isNight: Boolean,
)

private val HOUR_CELL_W = 60.dp
private val HOUR_LABEL_H = 22.dp
private val HOUR_CHART_H = 104.dp
private val HOUR_ICON_H = 34.dp
private val HOUR_FOOT_H = 20.dp

/**
 * 曲线在上半段走，下半段留给温度数字。
 *
 * [CURVE_BAND] 决定「幅度」：整个温度跨度铺满这一段。太小（34dp）时曲线几乎是一条直线，
 * 看不出起伏；这次从 44 → 62dp（图表区 84 → 104dp，卡片整体高 20dp），
 * 起伏明显一档，代价是每列上下留白更少。
 */
private val CURVE_TOP = 6.dp
private val CURVE_BAND = 62.dp

/** 数字离曲线多远。贴太近会跟着曲线一起「读成一团」。 */
private val TEMP_GAP = 10.dp

@Composable
private fun HourlyCard(snapshot: WeatherSnapshot) {
    // 通栏：横向滚动区自己吃内边距，条目能一直滑到卡片边缘
    SectionCard(title = WeatherStr.hourlyTitle(), bleed = true, bottomPadding = 8.dp) {
        val points = remember(snapshot) { hourlyPoints(snapshot) }
        if (points.isNotEmpty()) {
            val scroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .horizontalScroll(scroll)
            ) {
                // 左右内边距为 0：曲线从卡片最左端进、最右端出（用户要的"贴边"）。
                // 首列的钟点靠列宽自己居中，不需要额外缩进。
                HourlyStrip(points)
            }
        }
    }
}

/**
 * 从「前一小时 → 现在 → 之后每个整点」排 24 列。
 *
 * 为什么从当前小时倒推一格：源给的逐时通常从**下一个整点**开始（7:54 时首条是 8:00），
 * 直接渲染的话 9 点半打开会从 10:00 起，用户找不到"现在这一小时"。
 * 「现在」那格用实况（温度/现象/风级）—— 本来就是此刻的读数。
 */
private fun hourlyPoints(snapshot: WeatherSnapshot): List<HourPoint> {
    val nowSec = Clock.System.now().epochSeconds
    val bucket = floorDiv(nowSec, 3600L) * 3600L
    val offset = snapshot.utcOffsetSec
    val byBucket = snapshot.hourly.associateBy { floorDiv(it.epochSec, 3600L) * 3600L }

    // 昼夜按**当天实际日出日落**判，不是写死 6:00–19:00：
    // 冬天 17:00 就天黑了，写死会让 17~19 点还挂着太阳图标。拿不到日出日落才退回 6/19。
    val isNightAt = nightResolver(snapshot)

    fun pointAt(b: Long): HourPoint {
        val h = byBucket[b]
        val localHour = localHourMinute(b, offset).substringBefore(':').toIntOrNull() ?: 0
        return HourPoint(
            epochSec = b,
            label = WeatherStr.hourLabel(localHour),
            isNow = false,
            tempC = h?.tempC,
            text = h?.text.orEmpty(),
            precipProb = h?.precipProb,
            windPower = windPowerFromKmh(h?.windSpeedKmh),
            isNight = isNightAt(localHour),
        )
    }

    // 顺序是「当前整点 → 现在 → 下一个整点」。8:58 打开时左边是 8:00、右边是 9:00，
    // 不会冒出 7:00 那种"上一小时"（那是把当前小时也当成过去式了）。
    val list = ArrayList<HourPoint>(24)
    list += pointAt(bucket)
    list += HourPoint(
        epochSec = bucket,
        label = WeatherStr.now(),
        isNow = true,
        tempC = snapshot.now.tempC,
        text = snapshot.now.text,
        precipProb = null,
        windPower = snapshot.now.windPower,
        isNight = snapshot.now.isDay?.let { !it } ?: run {
            val h = localHourMinute(bucket, offset).substringBefore(':').toIntOrNull() ?: 12
            isNightAt(h)
        },
    )
    var b = bucket + 3600
    while (list.size < 24) {
        list += pointAt(b)
        b += 3600
    }
    return list
}

@Composable
private fun HourlyStrip(points: List<HourPoint>) {
    val accent = MiuixTheme.colorScheme.primary
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val temps = points.map { it.tempC }
    val valid = temps.filterNotNull()
    // 上下只各留 0.5℃ 余量：留太多会让整条曲线压成一条直线（幅度全被吃掉）
    val minT = (valid.minOrNull() ?: 0.0) - 0.5
    val maxT = (valid.maxOrNull() ?: 1.0) + 0.5
    val span = (maxT - minT).coerceAtLeast(1.0)

    fun yOf(index: Int): Dp {
        val t = temps.getOrNull(index) ?: return CURVE_TOP + CURVE_BAND / 2
        val f = ((t - minT) / span).coerceIn(0.0, 1.0).toFloat()
        return CURVE_TOP + CURVE_BAND * (1f - f)
    }

    val bodyH = HOUR_CHART_H + HOUR_ICON_H + HOUR_FOOT_H

    Box(
        modifier = Modifier
            .width(HOUR_CELL_W * points.size)
            .height(HOUR_LABEL_H + bodyH)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = HOUR_CELL_W.toPx()
            val labelH = HOUR_LABEL_H.toPx()
            val totalH = size.height

            // 列分隔线：从最顶端（连时间标签那一行一起）贯到最底，两端淡出、中间最实 ——
            // 一条等粗等浓的竖线会把"标签 / 曲线 / 图标"三行切得很硬，淡出之后它只是一道分隔提示。
            // 竖直渐变按画布整高铺（drawLine 传 brush 时 shader 用的是 scope 尺寸）。
            val sep = secondary
            val sepBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to sep.copy(alpha = 0f),
                    0.20f to sep.copy(alpha = 0.085f),
                    0.50f to sep.copy(alpha = 0.16f),
                    0.80f to sep.copy(alpha = 0.085f),
                    1f to sep.copy(alpha = 0f),
                ),
            )
            for (k in 1 until points.size) {
                drawLine(
                    brush = sepBrush,
                    start = Offset(cellW * k, 0f),
                    end = Offset(cellW * k, totalH),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }

            // 两端各补一个"虚拟点"（落在卡片外半格），曲线才是平滑地延伸到边，
            // 而不是一根直线怼上去（直线接曲线会在第一个点看到折角）
            val real = points.indices.map { i ->
                Offset(cellW * (i + 0.5f), labelH + yOf(i).toPx())
            }
            // 注意：buildList 的接收者是 MutableList，里面的 `size` 是列表长度 ——
            // 画布宽度必须先取出来，不然 `size.width` 会被解析成 Int.width 直接编不过
            val canvasW = size.width
            // 虚拟点的 y 用「镜像外推」(2*y0 - y1)，不是照抄端点 y ——
            // 照抄会让切线在边上回钩，画出来是"边缘突然下坠"。
            val leftY = real.first().y * 2f - real.getOrElse(1) { real.first() }.y
            val rightY = real.last().y * 2f - real.getOrElse(real.lastIndex - 1) { real.last() }.y
            val pts = buildList {
                add(Offset(-cellW / 2f, leftY))
                addAll(real)
                add(Offset(canvasW + cellW / 2f, rightY))
            }
            if (pts.size >= 2) {
                val path = Path()
                path.moveTo(pts.first().x, pts.first().y)
                for (i in 0 until pts.lastIndex) {
                    val p0 = pts.getOrElse(i - 1) { pts[i] }
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
                    val p3 = pts.getOrElse(i + 2) { pts[i + 1] }
                    // Catmull-Rom → 三次贝塞尔：曲线穿过每个整点且切线连续
                    path.cubicTo(
                        p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                        p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                        p2.x, p2.y,
                    )
                }
                // 曲线颜色跟着温度走：同一套色阶，和七日条不会打架
                val stops = Array(points.size) { i ->
                    (i / (points.size - 1f)) to tempColor(temps[i] ?: ((minT + maxT) / 2))
                }
                // 渐变按实数点宽铺（虚拟点各占半格，端点色值沿用最近的真实点）
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(colorStops = stops),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                )

                real.forEachIndexed { i, p ->
                    if (points[i].isNow) {
                        // 白心 + 紧贴的细环：环半径 = 白心半径 + 半个线宽，中间不留缝
                        val core = 4.2.dp.toPx()
                        val stroke = 1.2.dp.toPx()
                        drawCircle(Color.White, radius = core, center = p)
                        drawCircle(accent, radius = core + stroke / 2f, center = p, style = Stroke(stroke))
                    } else {
                        drawCircle(tempColor(temps[i] ?: 15.0), radius = 2.dp.toPx(), center = p)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            points.forEachIndexed { i, point ->
                Column(
                    modifier = Modifier
                        .width(HOUR_CELL_W)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = point.label,
                        fontSize = 12.sp,
                        fontWeight = if (point.isNow) FontWeight.Medium else null,
                        color = if (point.isNow) accent else secondary,
                        maxLines = 1,
                        modifier = Modifier.height(HOUR_LABEL_H),
                    )
                    Box(modifier = Modifier.height(HOUR_CHART_H).fillMaxWidth()) {
                        // 温度数字跟着曲线走：挂在曲线下方 2dp 处，像 MIUI 那样"贴着线"
                        Text(
                            text = point.tempC?.let { "${it.toInt()}°" } ?: "—",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (point.isNow) MiuixTheme.colorScheme.onSurface
                            else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = yOf(i) + TEMP_GAP),
                        )
                    }
                    Box(modifier = Modifier.height(HOUR_ICON_H), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(weatherIconRes(point.text, point.isNight)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Box(modifier = Modifier.height(HOUR_FOOT_H), contentAlignment = Alignment.Center) {
                        val prob = point.precipProb
                        val hasRain = prob != null && prob >= 20
                        Text(
                            text = if (hasRain) "$prob%" else point.windPower,
                            fontSize = 11.sp,
                            color = if (hasRain) accent else secondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「这个整点算不算夜里」的判定器。
 *
 * 用当天真实的日出/日落分钟数比；缺数据才退回 6:00–19:00 的经验值。
 */
internal fun nightResolver(snapshot: WeatherSnapshot): (Int) -> Boolean {
    val today = snapshot.daily.firstOrNull()
    val rise = today?.sunrise?.let(::minuteOfClock)
    val set = today?.sunset?.let(::minuteOfClock)
    if (rise == null || set == null || set <= rise) return { h -> h !in 6..<19 }
    return { h ->
        val minutes = h * 60
        minutes !in rise..<set
    }
}

/** "06:46" → 406；解析不出来给 null。 */
internal fun minuteOfClock(raw: String): Int? {
    val h = raw.substringBefore(':').toIntOrNull() ?: return null
    val m = raw.substringAfter(':', "").take(2).toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

// ── 逐日 ──────────────────────────────────────────────────────────────

/* 温度条宽度*/
private val BAR_MAX_W = 52.dp

@Composable
private fun DailyCard(snapshot: WeatherSnapshot, onMore: () -> Unit) {
    val days = snapshot.daily.take(7)
    val minTemp = days.mapNotNull { it.low }.minOrNull() ?: 0.0
    val maxTemp = days.mapNotNull { it.high }.maxOrNull() ?: 1.0
    // bleed + bottomPadding = 0：行自己吃左右内边距，分隔线和按钮才能通到卡片最左/最右，
    // 按钮的点击区也才能一直贴到卡片底边（原来它被 18dp 内边距包在卡片里，缩成一小条）。
    SectionCard(title = WeatherStr.dailyTitle(), bleed = true, bottomPadding = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CARD_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                days.forEachIndexed { index, day ->
                    DailyRow(day, index, minTemp, maxTemp)
                }
            }
            Spacer(Modifier.height(16.dp))
            // 行不再可点（点某一天只能看那一天，想横向比还得开关多次），
            // 统一收到底部这个入口：一次列出源给的**全部**天。
            // 分隔线左右跟正文对齐（内缩），下面的按钮不用 —— 它的点击区要通到卡片边缘
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CARD_H_PADDING)
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMore)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = WeatherStr.moreInfo(),
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 七日行的标签：今天 / 明天 / 后天 / 周X。逐日走势面板也用同一份，避免两处各写一套。 */
internal fun dayLabel(index: Int, date: String): String = when (index) {
    0 -> WeatherStr.today()
    1 -> WeatherStr.tomorrow()
    2 -> WeatherStr.dayAfterTomorrow()
    else -> if (date.length == 10) WeatherStr.weekday(weekdayIndex(date)) else date
}

/* 一行一天 */
@Composable
private fun DailyRow(
    day: WeatherDay,
    index: Int,
    minTemp: Double,
    maxTemp: Double,
) {
    val label = dayLabel(index, day.date)
    val isToday = index == 0
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isToday) FontWeight.Medium else null,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(24.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = day.dayText.ifBlank { day.nightText },
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(weatherIconRes(day.dayText, false)),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        // 右侧整体：低温 + 条 + 高温，作为一个 Row 收在行尾
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = day.low?.let { "${it.toInt()}°" } ?: "—",
                fontSize = 15.sp,
                color = secondary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(12.dp))
            TempRangeBar(
                low = day.low,
                high = day.high,
                minTemp = minTemp,
                maxTemp = maxTemp,
                modifier = Modifier
                    .width(BAR_MAX_W)
                    .height(6.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = day.high?.let { "${it.toInt()}°" } ?: "—",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

// ── 温度 → 颜色 ───────────────────────────────────────────────────────

/**
 * 绝对温度色阶：冷 → 天蓝，回暖 → 青，温和 → 黄，热 → 橙。
 *
 * 两个关键决定：
 * 1. **按绝对温度取色，不是按"这周最低到最高"排位**。后者看着五彩，但 30℃ 的一周里
 *    30℃ 会被涂成天蓝 —— 颜色说的是排名不是温度，那就只是装饰。
 * 2. **中间过青、黄两档，而不是蓝直接混橙**。sRGB 里蓝橙中点是灰粉色，一列灰条像坏了；
 *    走青→黄能保住饱和度，也符合温度色阶的惯例。
 */
private val TEMP_STOPS = listOf(
    -10.0 to Color(0xFF4E9BE0),
    5.0 to Color(0xFF57B7E2),
    14.0 to Color(0xFF5FC6D8),
    22.0 to Color(0xFFE4C86B),
    30.0 to Color(0xFFF4845F),
)

internal fun tempColor(temp: Double): Color {
    val first = TEMP_STOPS.first()
    val last = TEMP_STOPS.last()
    if (temp <= first.first) return first.second
    if (temp >= last.first) return last.second
    for (i in 0 until TEMP_STOPS.lastIndex) {
        val (t0, c0) = TEMP_STOPS[i]
        val (t1, c1) = TEMP_STOPS[i + 1]
        if (temp <= t1) return lerp(c0, c1, ((temp - t0) / (t1 - t0)).toFloat())
    }
    return last.second
}

/**
 * 一天的温度区间条：底轨 + 低→高**渐变**段。
 *
 * 两端的颜色各自按当天实际的低温/高温取，所以冷天是蓝条、热天是橙条、
 * 温差大的天自己就带一段渐变 —— 颜色是"这条温度"的，不是行号的颜色。
 */
@Composable
internal fun TempRangeBar(
    low: Double?,
    high: Double?,
    minTemp: Double,
    maxTemp: Double,
    modifier: Modifier = Modifier,
) {
    val track = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)
    val lowC = tempColor(low ?: high ?: 15.0)
    val highC = tempColor(high ?: low ?: 15.0)
    // 按**本周**温标铺满：最冷那天从左端起、最热那天到右端止，留白只表示"这天不是最冷/最热"
    val span = (maxTemp - minTemp).coerceAtLeast(1.0)
    val startF = (((low ?: minTemp) - minTemp) / span).coerceIn(0.0, 1.0).toFloat()
    val endF = (((high ?: maxTemp) - minTemp) / span).coerceIn(0.0, 1.0).toFloat()
    Canvas(modifier = modifier) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        val left = size.width * startF
        // 至少留 8%：1℃ 温差的日子也得看得见，不能画成 0 像素
        val width = (size.width * (endF - startF)).coerceAtLeast(size.width * 0.08f)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(lowC, highC),
                startX = left,
                endX = left + width,
            ),
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = radius,
        )
    }
}

/** 指标小格：浅底 + 名称 + 数值（单位贴在同一行基线）。 */
@Composable
internal fun MetricTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier = modifier
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f), ContinuousRoundedRectangle(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = label, fontSize = 12.sp, color = secondary, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    color = secondary,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

/** 一格指标：值已经是给用户看的字符串，单位单独存，方便按项换刻度。 */
internal data class MetricCell(val label: String, val value: String, val unit: String = "")

/** 小格网格：`chunked` 成行，空位补 `Spacer(weight)` 保持列宽一致。 */
@Composable
internal fun MetricGrid(
    cells: List<MetricCell>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    MetricTile(cell.label, cell.value, cell.unit, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

internal fun aqiColor(aqi: Int?): Color = when {
    aqi == null -> Color(0xFF8A8A8A)
    aqi <= 50 -> Color(0xFF47B881)
    aqi <= 100 -> Color(0xFFE8B23A)
    aqi <= 150 -> Color(0xFFE8833A)
    aqi <= 200 -> Color(0xFFD9534F)
    else -> Color(0xFF9B59B6)
}

private fun alertColor(alert: WeatherAlert): Color = when (alertLevelWord(alert)) {
    "红色" -> Color(0xFFD9534F)
    "橙色" -> Color(0xFFE8833A)
    "黄色" -> Color(0xFFE8B23A)
    else -> Color(0xFF4A90D9)
}

// ── 生活指数 ──────────────────────────────────────────────────────────

/**
 * 生活指数：两列瓦片。
 *
 * 原来每项是「11sp 名称 / 15sp 等级 / 11sp 建议」三行裸文字，一屏 8 项连成一片，
 * 眼睛分不清哪三行属于同一项。加一层浅底就把「一组」框出来了，字号也整体抬一档。
 */
@Composable
private fun IndexCard(snapshot: WeatherSnapshot) {
    val indices = snapshot.indices.take(8)
    val chunks = indices.chunked(2)
    // bleed：内容左右缩到 12dp（瓦片自带宽内边距，再按 18 会显得两边空），
    // 但标题不受影响 —— `SectionCard` 的标题自带 18dp，跟其它卡的标题仍然对齐。
    SectionCard(title = WeatherStr.indexTitle(), bleed = true, bottomPadding = 12.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            // 行间距、列间距都是 8dp：横竖一样的"缝"，格子才像一张网而不是两条带状
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chunks.forEach { row ->
                // IntrinsicSize.Max：同一行的两块等高，右边长建议不会把左边撑成孤零零一条
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { index ->
                        IndexTile(index, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun IndexTile(index: LifeIndex, modifier: Modifier = Modifier) {
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f), ContinuousRoundedRectangle(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(text = index.name, fontSize = 12.sp, color = secondary, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(
            text = index.level.ifBlank { WeatherStr.noDataField() },
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (index.tip.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = index.tip,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── 详细数据 ──────────────────────────────────────────────────────────
//
// 已经从「两列小格」整体挪到 `WeatherDetail.kt`：每格改成带图形的瓦片
// （刻度条 / 指北针 / 日落弧 / 气压表），点一下还能看逐时与逐日走势。

@Composable
private fun SourceFooter(snapshot: WeatherSnapshot) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = snapshot.sources.joinToString(" · "),
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (snapshot.sources.size > 1) WeatherStr.multiSourceNote() else WeatherStr.singleSourceNote(),
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
        )
    }
}

// ── 小工具 ────────────────────────────────────────────────────────────

/** 观测时刻的兜底：源没给时间就用抓取时刻按当地时区换算。 */
private fun clock(snapshot: WeatherSnapshot): String {
    val seconds = snapshot.fetchedAtEpochMs / 1000
    return localHourMinute(seconds, snapshot.utcOffsetSec)
}

/** 28.0 → "28"，28.5 → "28.5"；页面上一堆 "28.0" 很蠢。 */
internal fun trimNum(value: Double): String {
    val rounded = (value * 10).toInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
