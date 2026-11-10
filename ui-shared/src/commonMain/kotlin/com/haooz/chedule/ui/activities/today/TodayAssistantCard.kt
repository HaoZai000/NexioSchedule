package com.haooz.chedule.ui.activities.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.util.coursePeriodCompleted
import com.haooz.chedule.data.util.coursePeriodOngoing
import com.haooz.chedule.data.util.millisBetween
import com.haooz.chedule.data.util.nowLocalTime
import com.haooz.chedule.data.util.parseHourMinute
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.AssistantStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.components.WeatherAlertBadge
import com.haooz.chedule.weather.WeatherAlert
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_widget_location
import com.haooz.chedule.uishared.resources.icon_cloudy
import com.haooz.chedule.uishared.resources.icon_cloudy_night
import com.haooz.chedule.uishared.resources.icon_float_dirt
import com.haooz.chedule.uishared.resources.icon_heavy_rain
import com.haooz.chedule.uishared.resources.icon_heavy_snow
import com.haooz.chedule.uishared.resources.icon_fog
import com.haooz.chedule.uishared.resources.icon_fog_night
import com.haooz.chedule.uishared.resources.icon_ice_rain
import com.haooz.chedule.uishared.resources.icon_light_rain
import com.haooz.chedule.uishared.resources.icon_light_snow
import com.haooz.chedule.uishared.resources.icon_moderate_rain
import com.haooz.chedule.uishared.resources.icon_moderate_snow
import com.haooz.chedule.uishared.resources.icon_overcast
import com.haooz.chedule.uishared.resources.icon_pm_dirt
import com.haooz.chedule.uishared.resources.icon_rain_snow
import com.haooz.chedule.uishared.resources.icon_sand
import com.haooz.chedule.uishared.resources.icon_sunny
import com.haooz.chedule.uishared.resources.icon_sunny_night
import com.haooz.chedule.uishared.resources.icon_t_storm
import com.haooz.chedule.ui.components.LocalSoftGlassSurface
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 中文现象文本 → 图标。
 *
 * 按**关键词**匹配而不是枚举：三家源的现象词表各写各的（"小到中雨"/"强阵雨"/"霾"），
 * 枚举永远落后一步，关键词包含关系才是稳的。internal 是因为天气详情页也要用同一套，
 * 两处图标打起来比没有图标更难看。
 *
 * 判断顺序是**强度从重到轻**，不是从常见到少见：
 * 「大到暴雪」同时含"暴雪"和"雪"，先命中哪条决定画哪个图标；暴雨/大雪排在小雨/小雪前面，
 * 否则所有雨天都会退化成毛毛雨图标。同理「雨夹雪」必须排在"雨"判定之前。
 *
 * ⚠ 资源里**没有风图标、也没有"未知"图标**：大风借中性云顶替，空现象同样走中性云 ——
 * 千万别让它落到兜底的"晴"（阴天显示大太阳是最容易被当成 bug 报上来的）。
 */
internal fun weatherIconRes(type: String, isNight: Boolean = false): DrawableResource = when {
    type.contains("冰雹") || type.contains("雷") -> Res.drawable.icon_t_storm
    // 「冻雨」「冻毛毛雨」都算冻雨；必须排在"雨"之前
    type.contains("冻") -> Res.drawable.icon_ice_rain
    type.contains("雨夹雪") -> Res.drawable.icon_rain_snow
    type.contains("霾") -> Res.drawable.icon_pm_dirt
    type.contains("沙尘") || type.contains("扬沙") -> Res.drawable.icon_sand
    type.contains("浮尘") -> Res.drawable.icon_float_dirt
    type.contains("雾") -> if (isNight) Res.drawable.icon_fog_night else Res.drawable.icon_fog
    type.contains("暴雨") || type.contains("大雨") -> Res.drawable.icon_heavy_rain
    type.contains("中雨") -> Res.drawable.icon_moderate_rain
    type.contains("小雨") || type.contains("阵雨") || type.contains("雨") -> Res.drawable.icon_light_rain
    type.contains("暴雪") || type.contains("大雪") -> Res.drawable.icon_heavy_snow
    type.contains("中雪") -> Res.drawable.icon_moderate_snow
    type.contains("小雪") || type.contains("阵雪") || type.contains("雪") -> Res.drawable.icon_light_snow
    // 国标 33（大风）手上没有风图标，用中性云顶一下，至少不像"晴"
    type.contains("风") -> Res.drawable.icon_overcast
    type.contains("阴") -> Res.drawable.icon_overcast
    type.contains("多云") -> if (isNight) Res.drawable.icon_cloudy_night else Res.drawable.icon_cloudy
    type.contains("晴") -> if (isNight) Res.drawable.icon_sunny_night else Res.drawable.icon_sunny
    else -> Res.drawable.icon_overcast
}
/** 天气图标（普通 painterResource，不依赖平台图形栈）。 */
@Composable
private fun WeatherIcon(
    resource: DrawableResource,
    size: Dp,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

/** 状态徽标：胶囊底 + 主题色文字。 */
@Composable
private fun StatusBadge(text: String) {
    val accent = MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.14f), ContinuousCapsule())
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Medium,
            color = accent,
            maxLines = 1
        )
    }
}

// 条高两态：静止 0.5dp 细线 ↔ 满血 6dp；上下留白恒为 RULE_GAP
private val BAR_HEIGHT = 6.dp
private val RULE_HEIGHT = 0.5.dp
private val RULE_GAP = 10.dp

/** 分隔线与进度条是同一根：静止 0.5dp 细线，上课中长成 6dp 进度条。 */
@Composable
private fun CourseRuleBar(percent: Int?) {
    val accent = MiuixTheme.colorScheme.primary
    val isDark = isAppDarkTheme()
    // 0 = 细线，1 = 满血条；只在测量期与绘制期读，长出/收回不重组整张卡
    val morph = animateFloatAsState(
        targetValue = if (percent != null) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "assistantRuleBar"
    )
    val rawFrac = (percent ?: 0).coerceIn(0, 100) / 100f
    // 下课瞬间 percent 变 null，留住最后一次宽度跟着高度淡出（普通字段，不进快照）
    val lastFrac = remember { floatArrayOf(0f) }
    if (percent != null) lastFrac[0] = rawFrac
    val frac = if (percent != null) rawFrac else lastFrac[0]

    val ruleColor = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.09f)
    val trackColor = remember(accent) { accent.copy(alpha = 0.08f) }
    // 左淡右浓，尾巴淡出
    val fillBrush = remember(accent) {
        Brush.horizontalGradient(
            listOf(accent.copy(alpha = 0.36f), accent.copy(alpha = 0.62f))
        )
    }
    // 叠加层往白里混一档：深色卡轻、浅色卡重
    val overlayTint = remember(accent, isDark) {
        lerp(accent, Color.White, if (isDark) 0.06f else 0.4f)
    }

    // 高度给线自己：留白恒为 RULE_GAP，长高的只有中间那根
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // ⚠ 只在测量期读 morph：只触发重排，不重组整张卡
            .layout { measurable, constraints ->
                val h = (RULE_HEIGHT + (BAR_HEIGHT - RULE_HEIGHT) * morph.value).roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = h, maxHeight = h)
                )
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
    ) {
        // 轨道 + 本体：不模糊，容器高度就是条高
        Canvas(modifier = Modifier.fillMaxSize()) {
            val m = morph.value
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            // 颜色跟着高度一起过渡，不先跳粗细再跳色
            drawRoundRect(
                color = lerp(ruleColor, trackColor, m),
                size = size,
                cornerRadius = radius
            )
            if (frac > 0f) {
                drawRoundRect(
                    brush = fillBrush,
                    size = Size(size.width * frac, size.height),
                    cornerRadius = radius,
                    alpha = m
                )
            }
        }

        // 叠加层：圆头 + 切线尾迹，一笔闭合路径
        // ⚠ 承载模糊的节点必须比条高，否则 RenderNode 只有条那么大，模糊上下被裁断
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(22.dp)
                .align(Alignment.Center)
                .blur(0.5.dp, BlurredEdgeTreatment.Unbounded)
        ) {
            val m = morph.value
            if (frac <= 0f || m <= 0f) return@Canvas
            val cy = size.height / 2f
            val rBar = (RULE_HEIGHT + (BAR_HEIGHT - RULE_HEIGHT) * m).toPx() / 2f
            val barRight = size.width * frac      // 条的右边缘
            val rHead = rBar * 1.4f
            // 光/圆头锚在圆头圆心，不是条的右边缘
            val headX = (barRight - rBar).coerceAtLeast(rHead)
            val rTail = rBar * 1.4f
            val xTail = (headX - headX.coerceAtMost(36.dp.toPx())).coerceAtLeast(0f)

            val circle = Rect(
                Offset(headX - rHead, cy - rHead),
                Size(rHead * 2f, rHead * 2f)
            )
            val dx = headX - xTail
            val dist = sqrt(dx * dx + rTail * rTail)
            val path = Path()
            if (dist <= rHead * 1.02f) {
                // 尾巴短到没有直线段可用，退化成一个圆头
                path.addOval(circle)
            } else {
                // 直线必须切于圆：连到顶点就是折角。切点兼作弧起点，接缝处方向连续
                val phi = atan2(rTail, dx) - (PI.toFloat() - acos((rHead / dist).coerceIn(-1f, 1f)))
                val phiDeg = phi * (180f / PI.toFloat())
                path.moveTo(xTail, cy - rTail)
                path.lineTo(headX + rHead * cos(phi), cy + rHead * sin(phi))
                // 从上方切点顺时针扫到镜像的下方切点，正好兜住整个圆头
                path.arcTo(circle, phiDeg, -2f * phiDeg, false)
                path.lineTo(xTail, cy + rTail)
                path.close()
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,       // 尾部淡出，融进本体
                    1f to overlayTint.copy(alpha = 0.72f),
                    startX = xTail,
                    endX = headX
                ),
                alpha = m
            )
        }
    }
}

private fun parseTime(timeStr: String): LocalTime? {
    return try {
        parseHourMinute(timeStr)
    } catch (_: Exception) {
        null
    }
}
data class WeatherData(
    val temperature: Float = Float.NaN,
    val weatherType: String = "",
    val sunset: String = "",
    val sunrise: String = "",
    val notice: String = "",
    /** 当前最高级别的气象预警；无预警为 null。 */
    val alert: WeatherAlert? = null,
    val loaded: Boolean = false,
    val needsLocation: Boolean = false,
    val apiError: Boolean = false
) {
    fun isNight(): Boolean {
        if (sunset.isBlank() || sunrise.isBlank()) return false
        val now = nowLocalTime()
        val sunsetTime = parseTime(sunset.substringAfter("T")) ?: return false
        val sunriseTime = parseTime(sunrise.substringAfter("T")) ?: return false
        return now > sunsetTime || now < sunriseTime
    }
}

private data class CourseStatus(
    val currentCourse: Course? = null,
    val nextCourse: Course? = null,
    val timeMessage: String = "",
    /** 当前课结束时刻；跨过该时刻时触发下课庆祝 */
    val currentEnd: LocalTime? = null,
    /** 当前课已过的整数百分比（0..100）；null = 不在课上 */
    val progressPercent: Int? = null
)

/** 上课进度按整数百分比量化：一节课约 27 秒才跨 1%，进度条看起来仍连续。 */
private fun progressPercentOf(range: CourseTimeRange, now: LocalTime): Int {
    // 起止相同按满 24 小时处理，与 CourseTimeSpan 的口径一致
    val total = millisBetween(range.start, range.end, allowRollover = true)
        .takeIf { it > 0L } ?: (24L * 60L * 60L * 1000L)
    val elapsed = millisBetween(range.start, now, allowRollover = true)
    return ((elapsed * 100L) / total).coerceIn(0L, 100L).toInt()
}

@Composable
private fun rememberCourseStatus(
    courses: List<Course>,
    sectionTimes: Map<Int, String>
): CourseStatus {
    // 时间区间只随 courses/sectionTimes 重算；保持原顺序以兼容 first-match
    val ranges = remember(courses, sectionTimes) {
        buildCourseTimeRanges(courses, sectionTimes, sortByStart = false)
    }
    fun statusAt(now: LocalTime): CourseStatus {
        // 开始含、结束不含，避免整点卡在「正在上课」；跨午夜课由 coursePeriodOngoing 兜住
        val current = ranges.find { coursePeriodOngoing(it.start, it.end, now) }
        val next = ranges.find { now < it.start }
        val message = when {
            current != null -> formatRemainingUntil(now, current.end, isCountdownToStart = false)
            next != null -> formatRemainingUntil(now, next.start, isCountdownToStart = true)
            courses.isEmpty() -> ""
            else -> ""
        }
        return CourseStatus(
            currentCourse = current?.course,
            nextCourse = next?.course,
            timeMessage = message,
            currentEnd = current?.end,
            progressPercent = current?.let { progressPercentOf(it, now) }
        )
    }
    // 初值当场算出来：留空会让新组合的卡先闪一帧「当前没有课程」（借位页 / 首次进页都踩得到）
    var status by remember(courses, sectionTimes) { mutableStateOf(statusAt(nowLocalTime())) }
    LaunchedEffect(ranges) {
        while (true) {
            val newStatus = statusAt(nowLocalTime())
            // 未变化不写状态，避免每秒重组
            if (newStatus != status) status = newStatus
            delay(1000L.milliseconds)
        }
    }
    return status
}

/** 最后一分钟切换为秒级，其余仍用分钟口径。 */
private fun formatRemainingUntil(
    from: LocalTime,
    to: LocalTime,
    isCountdownToStart: Boolean
): String {
    val remainMillis = millisBetween(from, to, allowRollover = true)
    if (remainMillis < 0L) return if (isCountdownToStart) AssistantStr.alreadyStarted() else AssistantStr.alreadyEnded()
    if (remainMillis < 60_000L) {
        val seconds = ((remainMillis + 999L) / 1000L).toInt().coerceIn(1, 60)
        return if (isCountdownToStart) AssistantStr.secondsToStart(seconds) else AssistantStr.secondsLeft(seconds)
    }
    // 直接复用已算出的毫秒差，不再多算一次 millisBetween
    val duration = formatCountdownMinutes(ceilMinutesOf(remainMillis))
    return if (isCountdownToStart) AssistantStr.durationToStart(duration)
    else AssistantStr.durationLeft(duration)
}
private data class CourseTimeRange(
    val course: Course,
    val start: LocalTime,
    val end: LocalTime
)
private fun buildCourseTimeRanges(
    courses: List<Course>,
    sectionTimes: Map<Int, String>,
    sortByStart: Boolean = true
): List<CourseTimeRange> {
    val ranges = courses.mapNotNull { course ->
        val startStr = course.getEffectiveStartTime(sectionTimes) ?: return@mapNotNull null
        val endStr = course.getEffectiveEndTime(sectionTimes) ?: return@mapNotNull null
        val start = parseTime(startStr) ?: return@mapNotNull null
        val end = parseTime(endStr) ?: return@mapNotNull null
        CourseTimeRange(course, start, end)
    }
    return if (sortByStart) ranges.sortedBy { it.start } else ranges
}

// 向上取整，与顶部倒计时、系统岛倒计时共用同一套「剩余分钟」口径
private fun ceilMinutesOf(remainMillis: Long): Int =
    if (remainMillis <= 0L) 0 else ((remainMillis + 59_999L) / 60_000L).toInt()

private fun ceilMinutesUntil(from: LocalTime, to: LocalTime): Int =
    ceilMinutesOf(millisBetween(from, to, allowRollover = true))

private fun formatCountdownMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> AssistantStr.durationHourMinute(hours, mins)
        hours > 0 -> AssistantStr.durationHour(hours)
        else -> AssistantStr.durationMinute(minutes)
    }
}

// 同一时段内固定一条（不随每秒 tick 乱跳），种子混了日期 + 会话盐 ⇒ 换天/重进就换句
private fun pickTip(variants: List<String>, seed: Int): String {
    if (variants.isEmpty()) return ""
    // 乘法打散再取模：相邻 seed 不会落成同一下标
    val mixed = (seed.toLong() * 2_654_435_761L + 0x9E37_79B9L) and 0x7FFF_FFFFL
    return variants[(mixed % variants.size).toInt()]
}

/** 智能提醒文案：按时段/课间/课前分档轮换；分钟数一律交给顶部倒计时，不在这里重复。 */
private fun generateSmartTip(
    courses: List<Course>,
    tomorrowCourses: List<Course>,
    sectionTimes: Map<Int, String>,
    morningSections: Int,
    afternoonSections: Int,
    /** 会话盐：每次进入今日页换一次随机数，避免每次打开都是同一句 */
    salt: Int = 0
): String? {
    val now = nowLocalTime()
    // 日期入种子：同样的钟点，昨天和今天不说同一句
    val dayKey = todayLocalDate().toEpochDays()
    fun seedOf(bucket: Int): Int = salt * 3607 + dayKey * 7919 + now.hour * 31 + bucket
    val ranges = buildCourseTimeRanges(courses, sectionTimes)
    val tomorrowRanges = buildCourseTimeRanges(tomorrowCourses, sectionTimes)

    fun roomOf(course: Course): String =
        course.classroom.ifBlank { AssistantStr.roomTbd() }

    fun tomorrowNote(): String {
        if (now.hour < 20) return ""
        val n = tomorrowCourses.size
        if (n == 0) return AssistantStr.tomorrowFree()
        val first = tomorrowRanges.firstOrNull()?.course
        val firstLine = if (first != null) AssistantStr.tomorrowStartsWith(first.name) else AssistantStr.tomorrowFirstTbd()
        return AssistantStr.tomorrowCount(n, firstLine)
    }

    if (ranges.isEmpty()) {
        // 时间残缺时避免误报「今天没课」
        if (courses.isNotEmpty()) {
            return pickTip(
                listOf(
                    AssistantStr.timesMissingFillFirst(courses.size),
                    AssistantStr.timesMissingNoCountdown(courses.size),
                    AssistantStr.timesMissingFixIt(courses.size)
                ),
                seed = seedOf(0)
            )
        }
        val hour = now.hour
        val note = tomorrowNote()
        val pool = when (hour) {
            in 5..8 -> listOf(
                AssistantStr.freeMorningAlarm(),
                AssistantStr.freeMorningBed(),
                AssistantStr.freeMorningSleepIn()
            )
            in 9..11 -> listOf(
                AssistantStr.freeAmPlanIt(),
                AssistantStr.freeAmLibrary(),
                AssistantStr.freeAmPrecious()
            )
            in 12..13 -> listOf(
                AssistantStr.freeNoonMeals(),
                AssistantStr.freeNoonStomach(),
                AssistantStr.freeNoonFreedom()
            )
            in 14..17 -> listOf(
                AssistantStr.freePmRealWork(),
                AssistantStr.freePmSunshine(),
                AssistantStr.freePmProgressBar()
            )
            in 18..21 -> listOf(
                AssistantStr.freeEveUnwind(),
                AssistantStr.freeEvePhone(),
                AssistantStr.freeEveStayUp()
            )
            in 22..23 -> listOf(
                AssistantStr.freeNightWrapUp(),
                AssistantStr.freeNightSleep(),
                AssistantStr.freeNightFaceYourself()
            )
            else -> listOf(AssistantStr.noClassToday())
        }
        return pickTip(pool, seed = seedOf(0)) + note
    }

    val ongoing = ranges.find { coursePeriodOngoing(it.start, it.end, now) }
    val next = ranges.find { now < it.start }
    val prev = ranges.lastOrNull { coursePeriodCompleted(it.start, it.end, now) }

    val totalCount = courses.size

    return when {
        // 上课中：按「课上到哪一段」分档，剩余时间交给顶部倒计时
        ongoing != null -> {
            // 连堂判断只对同日课有意义；跨午夜课结束点在次日，当天没有「下一节」
            val nextAfter = if (ongoing.end > ongoing.start) {
                ranges.find { it.start > ongoing.end }
            } else null
            val gap = nextAfter?.let { ceilMinutesUntil(ongoing.end, it.start) }
            val gapPart = when {
                gap != null && gap <= 3 -> AssistantStr.gapBackToBack()
                gap != null && gap <= 10 -> AssistantStr.gapQuickRefuel()
                gap != null && gap <= 15 -> AssistantStr.gapCatchBreath()
                gap != null && gap <= 30 -> AssistantStr.gapLeaveRoom()
                else -> ""
            }
            val remaining = ceilMinutesUntil(now, ongoing.end)
            val pool = when {
                remaining <= 1 -> listOf(
                    AssistantStr.endWrapUpNow(),
                    AssistantStr.endAtTheBell(),
                    AssistantStr.endFinalSeconds()
                )
                remaining <= 5 -> listOf(
                    AssistantStr.endWindDown(),
                    AssistantStr.endPackUp(),
                    AssistantStr.endHangIn()
                )
                remaining <= 15 -> listOf(
                    AssistantStr.ongoingSecondHalf(),
                    AssistantStr.ongoingKeyPart(),
                    AssistantStr.ongoingKeepRhythm()
                )
                remaining <= 45 -> listOf(
                    AssistantStr.ongoingMidClass(),
                    AssistantStr.ongoingOwnPace(),
                    AssistantStr.ongoingNoEarlyDone()
                )
                else -> listOf(
                    AssistantStr.ongoingJustStarted(),
                    AssistantStr.ongoingSaveFocus(),
                    AssistantStr.ongoingLongStretch()
                )
            }
            // 文案不含数字，轮换粒度粗一档：15 分钟才换一句
            pickTip(pool, seed = seedOf(remaining / 15)) + gapPart
        }

        // 课间：只说走到哪一步 + 下节去向，分钟数交给顶部倒计时
        prev != null && next != null -> {
            val remainToNext = ceilMinutesUntil(now, next.start)
            val room = roomOf(next.course)
            val name = next.course.name
            val pool = when {
                remainToNext <= 1 -> listOf(
                    AssistantStr.breakGrabSeat(),
                    AssistantStr.breakBellAboutToRing(),
                    AssistantStr.breakZeroBuffer()
                )
                remainToNext <= 5 -> listOf(
                    AssistantStr.breakHeadBack(room),
                    AssistantStr.breakDontCutClose(room),
                    AssistantStr.breakOnYourFeet(room)
                )
                remainToNext <= 10 -> listOf(
                    AssistantStr.breakWatchTheClock(room),
                    AssistantStr.breakClassWaiting(room, name),
                    AssistantStr.breakPlanRoute(room)
                )
                remainToNext <= 20 -> listOf(
                    AssistantStr.breakNextRoom(room),
                    AssistantStr.breakChill(name, room),
                    AssistantStr.breakNoNewStarts()
                )
                remainToNext <= 45 -> listOf(
                    AssistantStr.breakDontWander(room),
                    AssistantStr.breakLongBreak(name),
                    AssistantStr.breakRecover()
                )
                else -> listOf(
                    AssistantStr.breakOwnRhythm(),
                    AssistantStr.breakBigGap(name, room),
                    AssistantStr.breakTimeYours()
                )
            }
            pickTip(pool, seed = seedOf(remainToNext / 10))
        }

        // 今天课已上完
        prev != null && next == null -> {
            val completedCount = ranges.count { coursePeriodCompleted(it.start, it.end, now) }
            val pool = when {
                completedCount >= totalCount -> listOf(
                    AssistantStr.doneClaimKnowledge(totalCount),
                    AssistantStr.doneNextLevel(totalCount),
                    AssistantStr.doneOffClock(totalCount)
                )
                else -> listOf(
                    AssistantStr.doneOnPlan(completedCount, totalCount),
                    AssistantStr.doneProgress(completedCount, totalCount),
                    AssistantStr.doneSteadyFinish()
                )
            }
            pickTip(pool, seed = seedOf(0)) + tomorrowNote()
        }

        // 还没上课：按「离上课还有多远」分档
        next != null -> {
            val minutes = ceilMinutesUntil(now, next.start)
            val room = roomOf(next.course)
            val name = next.course.name
            val hour = now.hour
            val dayPlanPool = when {
                totalCount >= 5 -> listOf(
                    AssistantStr.planHeavy(totalCount),
                    AssistantStr.planHeavyRation(totalCount),
                    AssistantStr.planHeavyBattery(totalCount)
                )
                totalCount >= 3 -> listOf(
                    AssistantStr.planNormal(totalCount),
                    AssistantStr.planNormalPace(totalCount),
                    AssistantStr.planNormalFollow(totalCount)
                )
                else -> listOf(
                    AssistantStr.planFewDontFloat(totalCount),
                    AssistantStr.planFewSpendWisely(totalCount),
                    AssistantStr.planLightDay(totalCount)
                )
            }
            val dayPlan = pickTip(dayPlanPool, seed = seedOf(1))
            val pool = when {
                minutes > 180 -> listOf(
                    AssistantStr.firstFarHours(dayPlan),
                    AssistantStr.firstFarOwnRhythm(dayPlan),
                    AssistantStr.firstFarDontCrash(dayPlan)
                )
                minutes > 90 -> listOf(
                    AssistantStr.first90Room(dayPlan, room),
                    AssistantStr.first90Until(dayPlan, name),
                    AssistantStr.first90NoRush(dayPlan)
                )
                minutes > 30 -> listOf(
                    AssistantStr.first30GetReady(dayPlan, room),
                    AssistantStr.first30PackUp(name, room),
                    AssistantStr.first30WarmUp(dayPlan)
                )
                minutes > 15 -> listOf(
                    AssistantStr.first15HeadOut(room),
                    AssistantStr.first15RouteSet(name),
                    AssistantStr.first15Buffer(room)
                )
                minutes > 5 -> listOf(
                    AssistantStr.first5NoDawdle(room),
                    AssistantStr.first5SteadyWalk(room),
                    AssistantStr.first5LeaveNow(room)
                )
                minutes >= 1 -> listOf(
                    AssistantStr.first1KeepPace(room),
                    AssistantStr.first1NoSprint(room),
                    AssistantStr.first1TakeSeat(room)
                )
                else -> {
                    // 只有这一支要统计午/晚节数，别每次调用都全表扫两遍
                    val afternoonCount = courses.count {
                        it.periodIndex(sectionTimes, morningSections, afternoonSections) == Course.PERIOD_AFTERNOON
                    }
                    val eveningCount = courses.count {
                        it.periodIndex(sectionTimes, morningSections, afternoonSections) == Course.PERIOD_EVENING
                    }
                    val periodHint = when {
                        afternoonCount > 0 && hour in 12..13 ->
                            AssistantStr.hintAfternoonMore(afternoonCount)
                        eveningCount > 0 && hour in 14..17 ->
                            AssistantStr.hintEveningMore(eveningCount)
                        else -> ""
                    }
                    listOf(
                        AssistantStr.rushHurryIn(room) + periodHint,
                        AssistantStr.rushBellRinging(room) + periodHint,
                        AssistantStr.rushStraightIn(name) + periodHint
                    )
                }
            }
            pickTip(pool, seed = seedOf(minutes / 15))
        }

        else -> pickTip(
            listOf(
                AssistantStr.readyFollowSchedule(totalCount),
                AssistantStr.readyDayPlanned(totalCount),
                AssistantStr.readyScheduleSet(totalCount)
            ),
            seed = seedOf(0)
        )
    }
}

@Composable
fun TodayAssistantCard(
    /** 天气与定位状态：宿主用自己的平台能力算好后传入（Android=LocationManager，iOS=CoreLocation） */
    weather: WeatherData,
    hasLocationPermission: Boolean,
    onRequestLocation: () -> Unit,
    courses: List<Course>,
    tomorrowCourses: List<Course> = emptyList(),
    sectionTimes: Map<Int, String>,
    morningSections: Int,
    afternoonSections: Int,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    surfaceOpacity: Float,
    /** 下课烟花（Android = FireworksOverlay，依赖 LocalContext/nanoTime）；null = 该端无此效果 */
    fireworksOverlay: (@Composable (trigger: Int, modifier: Modifier) -> Unit)? = null,
    /** 点击天气行进入天气详情页；null = 该端还没接详情页（此时整行不可点） */
    onOpenWeather: (() -> Unit)? = null,
) {
    val courseStatus = rememberCourseStatus(courses, sectionTimes)
    // 徽标、倒计时、进度条、柔光共用同一支强调色，避免多色打架
    val accent = MiuixTheme.colorScheme.primary
    // TextStyle.copy 会新建对象，按主题记一次，别每次重组都重建
    val textStyles = MiuixTheme.textStyles
    val nameStyle = remember(textStyles) { textStyles.body1.copy(fontSize = 18.sp) }
    val metaStyle = remember(textStyles) { textStyles.body2.copy(fontSize = 13.sp) }
    val countdownStyle = remember(textStyles) { textStyles.footnote1.copy(fontSize = 13.sp) }
    // ⚠ 不要回落到 courses.lastOrNull()：全部上完后状态已是「当前没有课程」，
    //   颜色却还挂着最后一节课 —— 那就是一片永远散不掉的残留色斑
    val tintCourse = courseStatus.currentCourse ?: courseStatus.nextCourse
    val isDarkTheme = isAppDarkTheme()
    val tintTarget = remember(tintCourse, isDarkTheme) {
        tintCourse?.let { course ->
            lerp(Color(course.colorRes), Color.White, if (isDarkTheme) 0.4f else 0.55f)
        }
    }
    // 颜色收起同理：留住最后一次颜色跟着 420ms 淡出（普通数组，不进快照）
    val tintLast = remember { arrayOfNulls<Color>(1) }
    if (tintTarget != null) tintLast[0] = tintTarget
    val tintColor = tintTarget ?: tintLast[0]
    val tintFade = animateFloatAsState(
        targetValue = if (tintTarget != null) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "assistantTintFade"
    )
    // 有玻璃时底子是从壁纸采样来的、比较杂，渐变要浓一档才立得住
    val tintAlpha = if (LocalSoftGlassSurface.current != null) 0.24f else 0.1f
    val tintBrush = remember(tintColor, tintAlpha) {
        tintColor?.let { color ->
            Brush.verticalGradient(
                0f to Color.Transparent,
                // 上半卡保持干净，只在下半段渐入
                0.4f to Color.Transparent,
                1f to color.copy(alpha = tintAlpha)
            )
        }
    }
    var smartTip by remember { mutableStateOf("") }
    // 盐放 remember：每次进今日页换一次，同一时段重进也能换句；现算会每秒跳
    val tipSalt = remember { kotlin.random.Random.nextInt() }
    // 每秒唤醒，但文案的分档全按分钟量化 ⇒ 只在分钟变化时重算，省掉 59/60 的重复计算
    LaunchedEffect(courses, tomorrowCourses, sectionTimes, morningSections, afternoonSections) {
        var lastMinute = -1
        while (true) {
            val now = nowLocalTime()
            val minute = now.hour * 60 + now.minute
            if (minute != lastMinute) {
                lastMinute = minute
                val newTip = generateSmartTip(
                    courses, tomorrowCourses, sectionTimes, morningSections, afternoonSections, tipSalt
                ).orEmpty()
                if (newTip != smartTip) smartTip = newTip
            }
            delay(1_000L.milliseconds)
        }
    }

    var fireworksTrigger by remember { mutableIntStateOf(0) }
    var lastCurrentEnd by remember { mutableStateOf<LocalTime?>(null) }
    // 只在「今天最后一节课」下课瞬间触发；中途下课/回看历史不触发
    LaunchedEffect(courseStatus.currentEnd) {
        val prevEnd = lastCurrentEnd
        lastCurrentEnd = courseStatus.currentEnd
        if (prevEnd == null) return@LaunchedEffect
        if (courseStatus.currentEnd == prevEnd) return@LaunchedEffect
        val justEndedMillis = millisBetween(prevEnd, nowLocalTime())
        if (justEndedMillis !in 0L..2500L) return@LaunchedEffect
        // 最后一节结束后：无当前课、也无下一节
        val isLastOfDay = courseStatus.currentCourse == null && courseStatus.nextCourse == null
        if (isLastOfDay) {
            fireworksTrigger++
        }
    }

    // ⚠ 别给这个 Box 挂 animateContentSize：它会按目标尺寸裁内容，首帧就是从 0 高度展开、
    //   底部圆角先被切成直角。高度变化交给下面各行动画自己收拢
    Box(modifier = Modifier.fillMaxWidth()) {
        BlurCard(
            surfaceOpacity = surfaceOpacity,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 渐变画在内容之下、玻璃面之上；alpha 在绘制期读，淡出只失效绘制
                    .drawBehind { tintBrush?.let { drawRect(it, alpha = tintFade.value) } }
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp)
            ) {
                val currentCourse = courseStatus.currentCourse
                val nextCourse = courseStatus.nextCourse
                // 当前优先、其次下一节；两者都空才是「当前没有课程」
                val active = currentCourse ?: nextCourse

                val label = when {
                    currentCourse != null -> AssistantStr.inClass()
                    nextCourse != null -> AssistantStr.nextClass()
                    else -> ""
                }
                val name = active?.name
                    ?: if (courses.isEmpty()) AssistantStr.noCourseTodayRest() else AssistantStr.noCourseNow()

                // ⚠ AnimatedVisibility 播 exit 时 content 仍在重组，此时 label / timeMessage 已是空串；
                //   必须留住最后一次非空值，否则那行先瞬间消失、只剩空白慢慢收
                val lastLabel = remember { arrayOf("") }
                val lastTime = remember { arrayOf("") }
                if (label.isNotBlank()) lastLabel[0] = label
                if (courseStatus.timeMessage.isNotBlank()) lastTime[0] = courseStatus.timeMessage
                val shownLabel = if (label.isNotBlank()) label else lastLabel[0]
                val shownTime =
                    if (courseStatus.timeMessage.isNotBlank()) courseStatus.timeMessage else lastTime[0]

                // 第一行只放徽标与倒计时，课名独占整卡宽度；行消隐走 expand/shrink，
                // 卡片高度始终等于内容高度，不裁内容
                AnimatedVisibility(
                    visible = label.isNotBlank() || courseStatus.timeMessage.isNotBlank(),
                    enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(200)),
                    exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing)) +
                        fadeOut(tween(140))
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (shownLabel.isNotBlank()) {
                                StatusBadge(text = shownLabel)
                            }
                            if (shownTime.isNotBlank()) {
                                Text(
                                    text = shownTime,
                                    style = countdownStyle,
                                    fontWeight = FontWeight.Medium,
                                    color = accent,
                                    textAlign = TextAlign.End,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Text(
                    text = name,
                    style = nameStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = buildString {
                    val location = if (showClassroom) active?.classroom.orEmpty() else ""
                    val teacher = if (showTeacher) active?.teacher.orEmpty() else ""
                    if (location.isNotBlank()) append(location)
                    if (location.isNotBlank() && teacher.isNotBlank()) append(" · ")
                    if (teacher.isNotBlank()) append(teacher)
                }
                // 同上：exit 期间 meta 已变空，留住上一次的「教室 · 老师」淡出去
                val lastMeta = remember { arrayOf("") }
                if (meta.isNotBlank()) lastMeta[0] = meta
                val shownMeta = if (meta.isNotBlank()) meta else lastMeta[0]
                AnimatedVisibility(
                    visible = meta.isNotBlank(),
                    enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(200)),
                    exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing)) +
                        fadeOut(tween(140))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = shownMeta,
                            style = metaStyle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 分隔线同时是进度条：上下各留 RULE_GAP
                Spacer(modifier = Modifier.height(RULE_GAP))
                CourseRuleBar(percent = courseStatus.progressPercent)
                Spacer(modifier = Modifier.height(RULE_GAP))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 文案会换、行数跟着变：AnimatedContent + clip = false 让高度随字走且不裁
                    AnimatedContent(
                        targetState = smartTip,
                        transitionSpec = {
                            (fadeIn(tween(240)) togetherWith fadeOut(tween(160)))
                                .using(
                                    SizeTransform(clip = false) { _, _ ->
                                        tween(300, easing = FastOutSlowInEasing)
                                    }
                                )
                        },
                        label = "assistantTip"
                    ) { tip ->
                        if (tip.isNotBlank()) {
                            Text(
                                text = tip,
                                style = textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                    // 整行可点 = 天气详情入口。点击区把图标+文字+箭头包在一起，
                    // 不然只有文字窄条能点，手指按在图标上没反应。
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (onOpenWeather != null) {
                            Modifier
                                .clip(ContinuousRoundedRectangle(8.dp))
                                .clickable(onClick = onOpenWeather)
                                .padding(vertical = 2.dp, horizontal = 2.dp)
                        } else {
                            Modifier
                        }
                    ) {
                        if (weather.loaded && !weather.temperature.isNaN()) {
                            WeatherIcon(
                                resource = weatherIconRes(weather.weatherType, weather.isNight()),
                                size = 23.dp,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                // 兜底字段可能为空：别拼出「7°C 雾 · 」这种挂着的分隔符
                                text = buildString {
                                    append("${weather.temperature.toInt()}°C ${weather.weatherType}")
                                    if (weather.notice.isNotBlank()) append(" · ${weather.notice}")
                                },
                                style = textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                // weight 只吃剩余空间、不撑满，箭头才会紧跟在文字尾部
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            weather.alert?.let { alert ->
                                Spacer(modifier = Modifier.width(6.dp))
                                WeatherAlertBadge(alert = alert)
                            }
                            if (onOpenWeather != null) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = MiuixIcons.ChevronForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        } else if (weather.needsLocation) {
                            Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(Res.drawable.ic_widget_location),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (hasLocationPermission) {
                                            AssistantStr.locationFailedTapRetry()
                                        } else {
                                            AssistantStr.locationPermissionNeeded()
                                        },
                                        style = textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.clickable { onRequestLocation() }
                                    )
                                }
                            }
                        } else if (weather.apiError) {
                            Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(Res.drawable.ic_widget_location),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = AssistantStr.weatherServiceTapRetry(),
                                        style = textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.clickable { onRequestLocation() }
                                    )
                                }
                            }
                        } else {
                            WeatherIcon(
                                resource = Res.drawable.icon_overcast,
                                size = 23.dp,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = CommonStr.loading(),
                                style = textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    }
                }
            }
        }
        fireworksOverlay?.invoke(
            fireworksTrigger,
            Modifier
                .matchParentSize()
                .graphicsLayer { clip = false },
        )
    }
}
