/** 时间配置编辑页面 - Screen */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.SpecialBlock
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.components.SpecialItemsEditorSection
import com.haooz.chedule.ui.components.describeSpecialDays
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeFifthpowerOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuadraticOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

// ===================== Morph Animation Foundation =====================

private data class ConfigAnimState(
    val bgAlpha: Float,
    val snapshotAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float,
    val gesture: Float
)

/**
 * 裁切形状。宽度取当次回调的 size，不在构造时捕获外部宽度：
 * 折叠屏展开 / 分屏 / 横竖屏切换时容器宽度会变，而 clipShape 是 remember 单例，
 * 捕获旧宽度会让裁切停在旧值（平板上表现为右侧一段被裁掉）。
 */
private class ConfigAnimClipShape(
    private val screenCornerRadiusPx: Float,
    private val startCornerRadiusPx: Float,
    private val animState: State<ConfigAnimState>
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val s = animState.value
        // 预测性返回：裁切圆角固定为屏幕圆角（不除以 scale，随页面缩放一起缩放）；
        // 其余按 morph 进度插值
        val radiusPx = when {
            s.gesture > 0f -> screenCornerRadiusPx
            s.progress >= 1f -> 0f
            s.progress <= 0.7f -> startCornerRadiusPx + (screenCornerRadiusPx - startCornerRadiusPx) * (s.progress / 0.7f)
            else -> screenCornerRadiusPx
        }
        // 补偿在"预测返回不补偿（×1）"与"正常 morph 除以 scale"之间按 gesture 平滑插值，
        // 避免松手瞬间圆角跳变大
        val compensate = (1f - s.gesture) / s.scale + s.gesture
        val radiusDp = (radiusPx * compensate / density.density).dp
        return ContinuousRoundedRectangle(radiusDp).createOutline(
            Size(size.width, s.clipBottom),
            layoutDirection,
            density
        )
    }
}

private fun parseTimeRange(timeStr: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    return try {
        val parts = timeStr.split("-")
        val startParts = parts[0].split(":")
        val endParts = parts[1].split(":")
        Pair(
            Pair(startParts[0].toInt(), startParts[1].toInt()),
            Pair(endParts[0].toInt(), endParts[1].toInt())
        )
    } catch (_: Exception) {
        Pair(Pair(8, 0), Pair(8, 45))
    }
}

/** 解析 "HH:mm" 为 (小时, 分钟)，解析失败返回 (8, 0) */
private fun parseTimeHm(time: String): Pair<Int, Int> {
    return try {
        val parts = time.split(":")
        Pair(parts[0].toInt(), parts[1].toInt())
    } catch (_: Exception) {
        Pair(8, 0)
    }
}

/**
 * 保存前的时间冲突检查结果。
 *
 * [blocking] 节次互相重叠 → 不允许保存（沿用旧口径）。
 * [warnings] 只要有一边是特殊课程 → 只提示，允许用户「仍然保存」：
 * 块压在节次上现在能正确排开，且老数据可能本来就重叠，硬拦会让用户改别处也存不下去。
 */
private data class OverlapCheck(
    val blocking: String? = null,
    val warnings: List<String> = emptyList()
) {
    val hasBlocking: Boolean get() = blocking != null
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}

private fun formatTimeRangeForDisplay(timeStr: String): String = timeStr.replace('-', '–')

/** 特殊时段块列表项的摘要文本：时段 + 已排的星期 */
private fun specialBlockSummary(block: SpecialBlock): String {
    // 纯展示文案，时间区间也用 en dash，与项目其它区间（如「第1–3节」）一致。
    // ⚠️ 别改 block.startTime/endTime 本身：那是 "HH:mm" 单值，拼接与解析都另有约定。
    val base = "${block.startTime}–${block.endTime}"
    val items = block.safeItems
    if (items.isEmpty()) return base
    val days = items.flatMap { it.startDay..it.endDay }.distinct().sorted()
    val dayLabel = if (days.size >= 7) "每天" else describeSpecialDays(days.toSet())
    return "$base · $dayLabel"
}

/** 解析 "HH:mm" → 分钟数；解析失败返回 null（不猜默认值，猜错会静默改时间） */
private fun parseHhMmToMinutes(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

// 解析失败返回 Int.MAX_VALUE，使无法解析的条目排序时落在末尾
private fun parseTimeToMinutesForSort(time: String): Int {
    return try {
        val parts = time.split(":")
        if (parts.size == 2) parts[0].toInt() * 60 + parts[1].toInt() else Int.MAX_VALUE
    } catch (_: Exception) {
        Int.MAX_VALUE
    }
}

// 按开始时间（其次结束时间、再次名称）排序，避免插入顺序导致"午休排在午餐之前"等错乱
private fun sortSpecialBlocksByTime(blocks: List<SpecialBlock>): List<SpecialBlock> {
    return blocks.sortedWith(
        compareBy<SpecialBlock> { parseTimeToMinutesForSort(it.startTime) }
            .thenBy { parseTimeToMinutesForSort(it.endTime) }
            .thenBy { it.name }
    )
}

@SuppressLint("DefaultLocale", "AutoboxingStateValueProperty", "ConfigurationScreenWidthHeight")
@Composable
fun TimeConfigEditScreen(
    timeConfig: TimeConfig,
    routineId: Long,
    onBackStart: () -> Unit = {},
    onBack: () -> Unit,
    onSave: (TimeConfig) -> Unit,
    cardLeft: Float = 0f,
    cardTop: Float = 0f,
    cardWidth: Float = 0f,
    cardHeight: Float = 0f,
    screenWidth: Float = 0f,
    screenHeight: Float = 0f,
    screenCornerRadius: Float = 0f,
    cardStartCornerRadius: Float = 0f,
    cardSnapshot: Bitmap? = null,
    isFabCreation: Boolean = false,
    /** 删除当前作息并回到一级列表（宿主负责落库与刷新列表） */
    onDeleteRoutine: (Long) -> Unit = {},
    liquidGlassBackdrop: LayerBackdrop? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollBehavior = rememberSharedScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    val screenTitle = "编辑作息"
    var routineName by remember(timeConfig) { mutableStateOf(timeConfig.safeRoutines.firstOrNull { it.id == routineId }?.name ?: "") }

    var morningSections by remember(timeConfig) { mutableIntStateOf(timeConfig.morningSections) }
    var afternoonSections by remember(timeConfig) { mutableIntStateOf(timeConfig.afternoonSections) }
    var eveningSections by remember(timeConfig) { mutableIntStateOf(timeConfig.eveningSections) }

    // 快捷设置开关只控制参数卡片展开，不落数据层。
    // 数据层的 quickTimeEnabled 是"课表按参数实时计算"的总开关：为 true 时课表
    // 完全无视 sectionTimes，为 false 且 sectionTimes 为空时会掉回内置默认时间。
    // 曾经就因为开关把 false 落库、而用户时间只存在参数里，导致"更新后校时变回默认"。
    // 所以这里与数据层字段解耦；保存时见下方 quickTimeEnabled = false 的迁移注释。
    var quickExpanded by remember(timeConfig) { mutableStateOf(timeConfig.quickTimeEnabled) }
    var classDuration by remember(timeConfig) { mutableIntStateOf(timeConfig.classDuration) }
    var shortBreak by remember(timeConfig) { mutableIntStateOf(timeConfig.shortBreak) }
    var longBreakEnabled by remember(timeConfig) { mutableStateOf(timeConfig.longBreakEnabled) }
    var longBreakMorning by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakMorning) }
    var longBreakAfternoon by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakAfternoon) }
    var longBreakEvening by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakEvening) }
    var longBreakMorningSection by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakMorningSection) }
    var longBreakAfternoonSection by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakAfternoonSection) }
    var longBreakEveningSection by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakEveningSection) }
    var morningStartHour by remember(timeConfig) { mutableIntStateOf(timeConfig.morningStartHour) }
    var morningStartMinute by remember(timeConfig) { mutableIntStateOf(timeConfig.morningStartMinute) }
    var afternoonStartHour by remember(timeConfig) { mutableIntStateOf(timeConfig.afternoonStartHour) }
    var afternoonStartMinute by remember(timeConfig) { mutableIntStateOf(timeConfig.afternoonStartMinute) }
    var eveningStartHour by remember(timeConfig) { mutableIntStateOf(timeConfig.eveningStartHour) }
    var eveningStartMinute by remember(timeConfig) { mutableIntStateOf(timeConfig.eveningStartMinute) }

    // 节次时间

    var showQuickItemDialog by remember { mutableStateOf(false) }
    var quickEditType by remember { mutableStateOf("") }
    var quickTempValue by remember { mutableIntStateOf(0) }
    var quickTempSection by remember { mutableIntStateOf(2) }
    var quickTempHour by remember { mutableIntStateOf(0) }
    var quickTempMinute by remember { mutableIntStateOf(0) }

    var showTimeDialog by remember { mutableStateOf(false) }
    var editingSection by remember { mutableIntStateOf(1) }
    var editingPeriod by remember { mutableStateOf("morning") }
    var tempStartHour by remember { mutableIntStateOf(8) }
    var tempStartMinute by remember { mutableIntStateOf(0) }
    var tempEndHour by remember { mutableIntStateOf(8) }
    var tempEndMinute by remember { mutableIntStateOf(45) }

    var specialBlocks by remember(timeConfig) { mutableStateOf(sortSpecialBlocksByTime(timeConfig.specialBlocks)) }
    var showSpecialDialog by remember { mutableStateOf(false) }
    var editingSpecialIndex by remember { mutableIntStateOf(-1) }
    var tempSpecialName by remember { mutableStateOf("") }
    var tempSpecialStartHour by remember { mutableIntStateOf(8) }
    var tempSpecialStartMinute by remember { mutableIntStateOf(0) }
    var tempSpecialEndHour by remember { mutableIntStateOf(8) }
    var tempSpecialEndMinute by remember { mutableIntStateOf(40) }

    // 添加入口必须从空白初值开始，不能沿用上一次编辑的内容（resetSpecialTemp 见下方）

    // ---- 作息自身的元信息：生效日期（一级列表 summary 显示的就是它）与删除 ----
    val editingRoutine = timeConfig.safeRoutines.firstOrNull { it.id == routineId }
    val canDeleteRoutine = timeConfig.safeRoutines.size > 1
    var routineEffectiveMonth by remember(timeConfig) {
        mutableIntStateOf(editingRoutine?.effectiveMonth ?: 1)
    }
    var routineEffectiveDay by remember(timeConfig) {
        mutableIntStateOf(editingRoutine?.effectiveDay ?: 1)
    }
    var showRoutineDateDialog by remember { mutableStateOf(false) }
    var tempRoutineMonth by remember { mutableIntStateOf(1) }
    var tempRoutineDay by remember { mutableIntStateOf(1) }
    var showDeleteRoutineDialog by remember { mutableStateOf(false) }
    /** 危险操作红，与 AddCourseDialog 保持一致 */
    val destructiveColor = Color(0xFFF44336)

    var showSpecialDeleteConfirm by remember { mutableStateOf(false) }

    var showOverlapDialog by remember { mutableStateOf(false) }
    var showOverlapWarningDialog by remember { mutableStateOf(false) }
    var overlapMessage by remember { mutableStateOf("") }

    // ===================== Morph Animation =====================
    val density = LocalDensity.current
    val animProgress = remember { Animatable(0f) }
    val animTransY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val startCornerRadiusPx = cardStartCornerRadius * density.density
    val morphOpenEase = OobeQuartOutEasing
    val morphExitEase = OobeCubicOutEasing
    val isUpperHalf = cardTop < screenHeight / 2f
    val transOpenEase = OobeFifthpowerOutEasing
    val transExitEase = OobeQuadraticOutEasing
    val transOpenMillis = if (isUpperHalf) 500 else 500
    val transExitMillis = if (isUpperHalf) 320 else 320
    val hasCardBounds = cardWidth > 0f && cardHeight > 0f && screenWidth > 0f

    var animating by remember { mutableStateOf(false) }

    // 预测性返回：手势只驱动缩放位置 scaleProgress（1=全屏，0=卡片，可退到 -1 即 200% 行程），
    // 预测返回期间围绕屏幕中心缩放（translation=0），位移/裁切保持全屏不动；
    // 取消回弹全屏、完成随关闭动画一起缩回卡片；
    // 低版本 NavigationBackHandler 自动退化为立即播放完整退出动画
    val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // 手势进度（0..1）：>0 表示预测性返回进行中，用于切换"中心缩放"
    val gestureBackProgress = remember { Animatable(0f) }
    val scaleProgress = remember { Animatable(0f) }
    // 手势是否正在推进：进行中裁切完全跟随页面缩放，松手/取消后进入平滑过渡
    var isGestureActive by remember { mutableStateOf(false) }

    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = true,
        onBackCancelled = {
            isGestureActive = false
            scope.launch {
                if (gestureBackProgress.value > 0f) {
                    // 手势取消：缩放回弹恢复全屏
                    gestureBackProgress.animateTo(0f, animationSpec = tween(180))
                    scaleProgress.animateTo(1f, animationSpec = tween(180))
                }
            }
        },
        onBackCompleted = {
            when {
                animating -> Unit
                showTimeDialog || showQuickItemDialog -> {
                    // 对话框打开时：手势回弹恢复页面，不关闭
                    isGestureActive = false
                    scope.launch {
                        coroutineScope {
                            launch { gestureBackProgress.animateTo(0f, animationSpec = tween(180)) }
                            launch { scaleProgress.animateTo(1f, animationSpec = tween(180)) }
                        }
                    }
                }
                !hasCardBounds -> {
                    onBackStart()
                    onBack()
                }
                else -> {
                    isGestureActive = false
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    animating = true
                    onBackStart()
                    scope.launch {
                        coroutineScope {
                            // 缩放中心从屏幕中心平滑过渡回左上角锚点（150ms），morph 位移随之接管
                            launch { gestureBackProgress.animateTo(0f, animationSpec = tween(150)) }
                            launch {
                                scaleProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 350, easing = morphExitEase)
                                )
                            }
                            launch {
                                animProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 350, easing = morphExitEase)
                                )
                            }
                            launch {
                                animTransY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = transExitMillis,
                                        easing = transExitEase
                                    )
                                )
                            }
                        }
                        onBack()
                    }
                }
            }
        },
    )

    // 逐帧收集返回手势进度（单独协程，避免手势期间每帧取消/重启 LaunchedEffect）；
    // 缩放跟随行程为"卡片→全屏"全程的 200%（滑到底 scaleProgress=-1），
    // 松手后由 onBackCompleted 按正常关闭动画回到卡片（1 倍）
    LaunchedEffect(Unit) {
        snapshotFlow { navigationEventState.transitionState }
            .collect { transitionState ->
                if (
                    transitionState is NavigationEventTransitionState.InProgress &&
                    transitionState.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                ) {
                    // 关闭动画进行中或无卡片边界时不驱动页面变形
                    if (!animating && hasCardBounds) {
                        // 预测性返回动画开关：关闭时不驱动跟随动画（返回仍被拦截，直接关闭）
                        if (PredictiveBackSettings.enabled) {
                            isGestureActive = true
                            val progress = transitionState.latestEvent.progress
                            gestureBackProgress.snapTo(progress)
                            // 添加时间配置（右下角加号进入）退出时行程 20%，编辑模式为 200%
                            scaleProgress.snapTo(1f - progress * if (isFabCreation) 0.2f else 2f)
                        }
                    }
                }
            }
    }

    LaunchedEffect(Unit) {
        if (hasCardBounds) {
            delay(12.milliseconds)
            launch {
                scaleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 560, easing = morphOpenEase)
                )
            }
            launch {
                animProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 560, easing = morphOpenEase)
                )
            }
            launch {
                animTransY.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = transOpenMillis, easing = transOpenEase)
                )
            }
        }
    }

    val animState = remember {
        derivedStateOf {
            if (!hasCardBounds) {
                ConfigAnimState(0f, 0f, 1f, 0f, 0f, 1f, screenHeight, 1f, 0f)
            } else {
                val p = animProgress.value
                val ty = animTransY.value
                val bgAlpha = (p * 0.5f).coerceIn(0f, 0.5f)
                val snapAlpha = (1f - p * 3f).coerceIn(0f, 1f)
                val contAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
                // 预测性返回手势只驱动缩放位置 scaleProgress（1=全屏，0=卡片，手势可退到 -1 即 200% 行程），
                // 位移与裁切保持全屏（p 不变）不随手势变化；coerceAtLeast 防止窄卡片时 scale 变负翻转
                val scale = (cardWidth / screenWidth + (1f - cardWidth / screenWidth) * scaleProgress.value).coerceAtLeast(0.05f)

                // 预测返回期间围绕屏幕中心缩放（gesture=1 时 translation=0），
                // 松手后 gesture 平滑归 0，translation 平滑过渡回正常 morph（卡片中心位移）
                val gesture = gestureBackProgress.value
                val normalX = (cardLeft + cardWidth / 2f - screenWidth / 2f) * (1f - p)
                val translationX = normalX * (1f - gesture)

                // ★ 可见区域中心 Y 直接沿抛物线插值，不再依赖 p 和 ty 的时间差
                val cardCenterY = cardTop + cardHeight / 2f
                val screenCenterY = screenHeight / 2f
                val targetCenterY = cardCenterY + (screenCenterY - cardCenterY) * ty
                val normalY = targetCenterY - screenHeight / 2f * (1f - scale) - (cardHeight + (screenHeight - cardHeight) * p) / 2f
                val translationY = normalY * (1f - gesture)

                // 手势推进期间裁切跟随"当前展开高度×缩放"（打开未完成时也连续，底部不瞬间归位）；
                // 松手/取消过渡期按 gesture 平滑插值衔接 morph 的底部收缩动画
                val morphClip = cardHeight + (screenHeight - cardHeight) * p
                val predictiveClip = morphClip * scale
                val rawClipBottom = if (isGestureActive) predictiveClip else predictiveClip * gesture + morphClip * (1f - gesture)
                val clipBottom = rawClipBottom / scale
                ConfigAnimState(bgAlpha, snapAlpha, contAlpha, translationX, translationY, scale, clipBottom, p, gesture)
            }
        }
    }

    fun triggerExitAndBack(onSavePending: (() -> Unit)? = null) {
        keyboardController?.hide()
        focusManager.clearFocus()
        if (!hasCardBounds) {
            onSavePending?.invoke()
            onBackStart()
            onBack()
            return
        }
        if (animating) return
        animating = true
        onBackStart()
        scope.launch {
            coroutineScope {
                launch {
                    scaleProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 350, easing = morphExitEase)
                    )
                }
                launch {
                    animProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 350, easing = morphExitEase)
                    )
                }
                launch {
                    animTransY.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = transExitMillis,
                            easing = transExitEase
                        )
                    )
                }
            }
            onSavePending?.invoke()
            onBack()
        }
    }

    val minuteValues = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55)

    // 节次时间（从已保存配置读取，只有点"应用"时才重新计算）
    var morningTimes by remember(timeConfig) { mutableStateOf(timeConfig.getPeriodTimes("morning")) }
    var afternoonTimes by remember(timeConfig) { mutableStateOf(timeConfig.getPeriodTimes("afternoon")) }
    var eveningTimes by remember(timeConfig) { mutableStateOf(timeConfig.getPeriodTimes("evening")) }

    // 特殊课程弹窗的「重新开始填」逻辑放在这儿：要读下面的 morningTimes，
    // Kotlin 不允许局部函数前向捕获后面才声明的变量。
    // 默认时段跟着第一节课走（第1节开始前 40 分钟），别再写死 08:00-08:40 ——
    // 那几乎必然和第1节 08:00-08:45 撞车，用户一添加就踩重叠。
    fun resetSpecialTemp() {
        tempSpecialName = ""
        val firstSectionStart = parseHhMmToMinutes(morningTimes[1]?.substringBefore("-")) ?: (8 * 60)
        val startMin = (firstSectionStart - 40).coerceAtLeast(0)
        tempSpecialStartHour = startMin / 60
        tempSpecialStartMinute = startMin % 60
        val endMin = (startMin + 40).coerceAtMost(23 * 60 + 59)
        tempSpecialEndHour = endMin / 60
        tempSpecialEndMinute = endMin % 60
    }

    // key 同 sectionTimes，如 "morning_1" -> "早自习"
    var sectionNames by remember(timeConfig) { mutableStateOf(timeConfig.sectionNames) }
    var tempSectionName by remember { mutableStateOf("") }

    fun getSectionTitle(period: String, relSection: Int): String {
        val key = "${period}_$relSection"
        val abs = when (period) {
            "morning" -> relSection
            "afternoon" -> morningSections + relSection
            "evening" -> morningSections + afternoonSections + relSection
            else -> relSection
        }
        val name = sectionNames[key]?.takeIf { it.isNotBlank() }
        return if (name != null) "第${abs}节 $name" else "第${abs}节"
    }

    // 仅检查当前节数范围内的节次 + 当前特殊课程块
    fun checkTimeOverlap(): OverlapCheck {
        data class TimeRange(val start: Int, val end: Int, val label: String, val isSection: Boolean)

        // 解析 "HH:MM-HH:MM" → 分钟区间；空串或格式非法返回 null（显式跳过）
        fun parseRange(text: String): IntRange? {
            if (text.isBlank()) return null
            val parts = text.split("-")
            if (parts.size != 2) return null
            val startParts = parts[0].split(":")
            val endParts = parts[1].split(":")
            if (startParts.size != 2 || endParts.size != 2) return null
            val start = startParts[0].toIntOrNull()?.times(60)
                ?.plus(startParts[1].toIntOrNull() ?: 0) ?: return null
            val end = endParts[0].toIntOrNull()?.times(60)?.plus(endParts[1].toIntOrNull() ?: 0)
                ?: return null
            return start..end
        }

        val allTimes = mutableListOf<TimeRange>()
        for ((section, timeStr) in morningTimes) {
            if (section > morningSections) continue
            val range = parseRange(timeStr) ?: continue
            allTimes.add(TimeRange(range.first, range.last, "上午第${section}节", true))
        }
        for ((section, timeStr) in afternoonTimes) {
            if (section > afternoonSections) continue
            val range = parseRange(timeStr) ?: continue
            allTimes.add(TimeRange(range.first, range.last, "下午第${section}节", true))
        }
        for ((section, timeStr) in eveningTimes) {
            if (section > eveningSections) continue
            val range = parseRange(timeStr) ?: continue
            allTimes.add(TimeRange(range.first, range.last, "晚上第${section}节", true))
        }
        // 特殊课程块也上时间轴：早读/大课间与节次同处一条时间线，重叠会让横带骑到课程卡上。
        for (block in specialBlocks) {
            val s = parseRange("${block.startTime}-${block.endTime}") ?: continue
            if (s.last <= s.first) continue
            val label = block.name.ifBlank { "特殊课程" }
            allTimes.add(TimeRange(s.first, s.last, label, false))
        }

        var blocking: String? = null
        val warnings = mutableListOf<String>()
        for (i in allTimes.indices) {
            for (j in i + 1 until allTimes.size) {
                val a = allTimes[i]
                val b = allTimes[j]
                if (a.start < b.end && b.start < a.end) {
                    // 节次互相重叠沿用旧口径：直接拦保存。
                    if (a.isSection && b.isSection) {
                        if (blocking == null) blocking = "${a.label} 与 ${b.label}"
                    } else {
                        warnings.add("${a.label} 与 ${b.label}")
                    }
                }
            }
        }
        return OverlapCheck(blocking, warnings)
    }

    /** 真正落库并退出。顶栏保存与「仍然保存」共用，避免两处各写一份字段清单。 */
    fun commitSave() {
        val finalSectionTimes = mutableMapOf<String, String>()
        for ((k, v) in morningTimes) if (k <= morningSections) finalSectionTimes["morning_$k"] = v
        for ((k, v) in afternoonTimes) if (k <= afternoonSections) finalSectionTimes["afternoon_$k"] = v
        for ((k, v) in eveningTimes) if (k <= eveningSections) finalSectionTimes["evening_$k"] = v
        // 过滤掉超出当前节数范围的自定义名称
        val finalSectionNames = mutableMapOf<String, String>()
        for ((k, v) in sectionNames) {
            val parts = k.split("_")
            if (parts.size != 2) continue
            val period = parts[0]
            val idx = parts[1].toIntOrNull() ?: continue
            val count = when (period) {
                "morning" -> morningSections
                "afternoon" -> afternoonSections
                "evening" -> eveningSections
                else -> 0
            }
            if (idx in 1..count) finalSectionNames[k] = v
        }

        val newConfig = timeConfig.copy(
            name = routineName,
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            // 保存统一落 sectionTimes（所见即所存），quickTimeEnabled
            // 恒为 false：老数据若为 true，本次保存即完成迁移——
            // 编辑页显示值（morningTimes 等）本来就来自参数实时计算，
            // 落成 sectionTimes 后课表不再依赖 quickTimeEnabled，
            // 从此开关状态、节次手改都不会再互相打架。
            quickTimeEnabled = false,
            classDuration = classDuration,
            shortBreak = shortBreak,
            longBreakEnabled = longBreakEnabled,
            longBreakMorning = longBreakMorning,
            longBreakAfternoon = longBreakAfternoon,
            longBreakEvening = longBreakEvening,
            longBreakMorningSection = longBreakMorningSection,
            longBreakAfternoonSection = longBreakAfternoonSection,
            longBreakEveningSection = longBreakEveningSection,
            morningStartHour = morningStartHour,
            morningStartMinute = morningStartMinute,
            afternoonStartHour = afternoonStartHour,
            afternoonStartMinute = afternoonStartMinute,
            eveningStartHour = eveningStartHour,
            eveningStartMinute = eveningStartMinute,
            sectionTimes = finalSectionTimes,
            sectionNames = finalSectionNames,
            specialBlocks = specialBlocks
        ).withRoutineMeta(
            routineId,
            month = routineEffectiveMonth,
            day = routineEffectiveDay
        )
        triggerExitAndBack(onSavePending = { onSave(newConfig) })
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val isLiquidGlass = liquidGlassBackdrop != null
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    val s = animState.value
    val clipShape = remember(screenCornerRadius, startCornerRadiusPx) {
        ConfigAnimClipShape(
            screenCornerRadius,
            startCornerRadiusPx,
            animState
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) ComposeColor(0xFF2C2C2C).copy(alpha = s.bgAlpha)
                else ComposeColor.Black.copy(alpha = s.bgAlpha)
            )
            .pointerInput(Unit) { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    scaleX = s.scale
                    scaleY = s.scale
                    translationX = s.translationX
                    translationY = s.translationY
                }
                .clip(clipShape)
                .background(
                    if (isFabCreation) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.background
                )
        ) {
            if (cardSnapshot != null && s.snapshotAlpha > 0f) {
                val imageBitmap = remember(cardSnapshot) { cardSnapshot.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle((cardStartCornerRadius / s.scale).dp))
                        .graphicsLayer { alpha = s.snapshotAlpha },
                    contentScale = ContentScale.FillWidth
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = s.contentAlpha }
            ) {
                Scaffold(
                    topBar = {
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop!!,
                        ) {
                            CollapsibleTopAppBar(
                                title = screenTitle,
                                largeTitle = screenTitle,
                                modifier = Modifier,
                                scrollBehavior = scrollBehavior,
                                contentPadding = {},
                                startAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            triggerExitAndBack()
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Normal.Close,
                                        contentDescription = "取消",
                                        iconSize = 24.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                                endAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            val check = checkTimeOverlap()
                                            when {
                                                check.hasBlocking -> {
                                                    overlapMessage = check.blocking!!
                                                    showOverlapDialog = true
                                                }
                                                check.hasWarnings -> {
                                                    overlapMessage = check.warnings.joinToString("\n")
                                                    showOverlapWarningDialog = true
                                                }
                                                else -> commitSave()
                                            }
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Ok,
                                        contentDescription = "保存并关闭",
                                        iconSize = 25.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                            )
                        }
                    },
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                            .then(
                                if (isLiquidGlass) Modifier.liquidGlassLayerBackdrop(
                                    liquidGlassBackdrop
                                )
                                else Modifier
                            )
                    ) {
                        val listState = rememberLazyListState()
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.firstVisibleItemScrollOffset }
                                .collect { offset ->
                                    listScrollY = offset
                                }
                        }

                        Card(
                            modifier = Modifier.fillMaxSize()
                                .background(MiuixTheme.colorScheme.surface),
                            insideMargin = PaddingValues(0.dp),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surface,
                                contentColor = MiuixTheme.colorScheme.onSurface
                            )
                        ) {
                            val topBarHeightDp = with(density) {
                                scrollBehavior.currentHeightPx.toDp()
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                                    .overScrollVertical()
                                    .scrollEndHaptic(
                                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                    )
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    start = tabletHorizontalPadding,
                                    top = paddingValues.calculateTopPadding() + topBarHeightDp - 82.dp,
                                    end = tabletHorizontalPadding,
                                    bottom = 120.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item(key = "config_name") {
                                    SmallTitle(text = "作息名称")
                                    NativeMiuixTextField(
                                        value = routineName,
                                        onValueChange = { routineName = it },
                                        label = "请输入作息名称",
                                        useLabelAsPlaceholder = true,
                                        requestFocus = isFabCreation
                                    )

                                }

                                // 生效安排：属于作息本身，跟名字一样在这一层改
                                item(key = "routine_meta") {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            ArrowPreference(
                                                title = "生效日期",
                                                // 只有一套作息时没有可切换的对象，日期无意义
                                                summary = if (canDeleteRoutine) {
                                                    "每年 ${routineEffectiveMonth}月${routineEffectiveDay}日起自动切换"
                                                } else "始终生效",
                                                enabled = canDeleteRoutine,
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.Confirm
                                                    )
                                                    tempRoutineMonth = routineEffectiveMonth
                                                    tempRoutineDay = routineEffectiveDay
                                                    showRoutineDateDialog = true
                                                },
                                                holdDownState = showRoutineDateDialog
                                            )
                                        }
                                    }
                                }

                                item(key = "quick_settings") {
                                    val bottomEndRadius by animateDpAsState(
                                        if (quickExpanded) 32.dp else 20.dp,
                                        label = "bottomEnd"
                                    )
                                    val bottomStartRadius by animateDpAsState(
                                        if (quickExpanded) 32.dp else 20.dp,
                                        label = "bottomStart"
                                    )
                                    val cardModifier = Modifier.fillMaxWidth().squircleSurface(
                                        color = MiuixTheme.colorScheme.secondaryVariant,
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomEnd = bottomEndRadius,
                                        bottomStart = bottomStartRadius
                                    )
                                    Card(
                                        cornerRadius = 0.dp,
                                        modifier = cardModifier,
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                                    .clickable {
                                                        quickExpanded = !quickExpanded
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "快捷设置时间",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurface
                                                )
                                                Switch(
                                                    checked = quickExpanded,
                                                    onCheckedChange = { quickExpanded = it })
                                            }
                                            AnimatedVisibility(
                                                visible = quickExpanded,
                                                enter = expandVertically(),
                                                exit = shrinkVertically()
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    ArrowPreference(
                                                        title = "每节课时长",
                                                        endActions = {
                                                            Text(
                                                                "${classDuration}分钟",
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "duration"; quickTempValue =
                                                            classDuration; showQuickItemDialog =
                                                            true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "duration"
                                                    )
                                                    ArrowPreference(
                                                        title = "课间休息",
                                                        endActions = {
                                                            Text(
                                                                "${shortBreak}分钟",
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "short_break"; quickTempValue =
                                                            shortBreak; showQuickItemDialog = true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "short_break"
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth()
                                                            .clickable {
                                                                longBreakEnabled = !longBreakEnabled
                                                            }.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 14.dp
                                                            ),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            "大课间休息",
                                                            fontSize = 17.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MiuixTheme.colorScheme.onSurface
                                                        )
                                                        Switch(
                                                            checked = longBreakEnabled,
                                                            onCheckedChange = {
                                                                longBreakEnabled = it
                                                            })
                                                    }
                                                    AnimatedVisibility(
                                                        visible = longBreakEnabled,
                                                        enter = expandVertically(),
                                                        exit = shrinkVertically()
                                                    ) {
                                                        Column {
                                                            ArrowPreference(
                                                                title = "上午大课间休息",
                                                                endActions = {
                                                                    Text(
                                                                        "第${longBreakMorningSection}节后 ${longBreakMorning}分钟",
                                                                        fontSize = 14.5.sp,
                                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                                    )
                                                                },
                                                                onClick = {
                                                                    quickEditType =
                                                                        "long_morning"; quickTempSection =
                                                                    longBreakMorningSection; quickTempValue =
                                                                    longBreakMorning; showQuickItemDialog =
                                                                    true
                                                                },
                                                                holdDownState = showQuickItemDialog && quickEditType == "long_morning"
                                                            )
                                                            ArrowPreference(
                                                                title = "下午大课间休息",
                                                                endActions = {
                                                                    Text(
                                                                        "第${longBreakAfternoonSection}节后 ${longBreakAfternoon}分钟",
                                                                        fontSize = 14.5.sp,
                                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                                    )
                                                                },
                                                                onClick = {
                                                                    quickEditType =
                                                                        "long_afternoon"; quickTempSection =
                                                                    longBreakAfternoonSection; quickTempValue =
                                                                    longBreakAfternoon; showQuickItemDialog =
                                                                    true
                                                                },
                                                                holdDownState = showQuickItemDialog && quickEditType == "long_afternoon"
                                                            )
                                                            ArrowPreference(
                                                                title = "晚上大课间休息",
                                                                endActions = {
                                                                    Text(
                                                                        "第${longBreakEveningSection}节后 ${longBreakEvening}分钟",
                                                                        fontSize = 14.5.sp,
                                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                                    )
                                                                },
                                                                onClick = {
                                                                    quickEditType =
                                                                        "long_evening"; quickTempSection =
                                                                    longBreakEveningSection; quickTempValue =
                                                                    longBreakEvening; showQuickItemDialog =
                                                                    true
                                                                },
                                                                holdDownState = showQuickItemDialog && quickEditType == "long_evening"
                                                            )
                                                        }
                                                    }
                                                    ArrowPreference(
                                                        title = "上午开始时间",
                                                        endActions = {
                                                            Text(
                                                                String.format(
                                                                    "%02d:%02d",
                                                                    morningStartHour,
                                                                    morningStartMinute
                                                                ),
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "start_morning"; quickTempHour =
                                                            morningStartHour; quickTempMinute =
                                                            morningStartMinute; showQuickItemDialog =
                                                            true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "start_morning"
                                                    )
                                                    ArrowPreference(
                                                        title = "下午开始时间",
                                                        endActions = {
                                                            Text(
                                                                String.format(
                                                                    "%02d:%02d",
                                                                    afternoonStartHour,
                                                                    afternoonStartMinute
                                                                ),
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "start_afternoon"; quickTempHour =
                                                            afternoonStartHour; quickTempMinute =
                                                            afternoonStartMinute; showQuickItemDialog =
                                                            true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "start_afternoon"
                                                    )
                                                    ArrowPreference(
                                                        title = "晚上开始时间",
                                                        endActions = {
                                                            Text(
                                                                String.format(
                                                                    "%02d:%02d",
                                                                    eveningStartHour,
                                                                    eveningStartMinute
                                                                ),
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "start_evening"; quickTempHour =
                                                            eveningStartHour; quickTempMinute =
                                                            eveningStartMinute; showQuickItemDialog =
                                                            true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "start_evening"
                                                    )
                                                    TextButton(
                                                        text = "应用",
                                                        onClick = {
                                                            hapticFeedback.performHapticFeedback(
                                                                HapticFeedbackType.Confirm
                                                            )
                                                            val lbM =
                                                                if (longBreakEnabled) longBreakMorning else shortBreak
                                                            val lbA =
                                                                if (longBreakEnabled) longBreakAfternoon else shortBreak
                                                            val lbE =
                                                                if (longBreakEnabled) longBreakEvening else shortBreak
                                                            val lbsM =
                                                                if (longBreakEnabled) longBreakMorningSection else 2
                                                            val lbsA =
                                                                if (longBreakEnabled) longBreakAfternoonSection else 2
                                                            val lbsE =
                                                                if (longBreakEnabled) longBreakEveningSection else 2
                                                            morningTimes =
                                                                Course.calculatePeriodTimes(
                                                                    morningSections,
                                                                    morningStartHour,
                                                                    morningStartMinute,
                                                                    classDuration,
                                                                    shortBreak,
                                                                    lbM,
                                                                    lbsM
                                                                )
                                                            afternoonTimes =
                                                                Course.calculatePeriodTimes(
                                                                    afternoonSections,
                                                                    afternoonStartHour,
                                                                    afternoonStartMinute,
                                                                    classDuration,
                                                                    shortBreak,
                                                                    lbA,
                                                                    lbsA
                                                                )
                                                            eveningTimes =
                                                                Course.calculatePeriodTimes(
                                                                    eveningSections,
                                                                    eveningStartHour,
                                                                    eveningStartMinute,
                                                                    classDuration,
                                                                    shortBreak,
                                                                    lbE,
                                                                    lbsE
                                                                )
                                                            Toast.makeText(
                                                                context,
                                                                "课程时间已应用",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        },
                                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                                        modifier = Modifier.fillMaxWidth().padding(
                                                            start = 20.dp,
                                                            end = 20.dp,
                                                            top = 8.dp,
                                                            bottom = 20.dp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 特殊课程（无编号时段块）
                                item(key = "morning") {
                                    SmallTitle(
                                        text = "上午",
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            (1..morningSections).forEach { relSection ->
                                                val timeStr = morningTimes[relSection] ?: ""
                                                ArrowPreference(
                                                    title = getSectionTitle("morning", relSection),
                                                    endActions = {
                                                        Text(
                                                            formatTimeRangeForDisplay(timeStr),
                                                            fontSize = 14.5.sp,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                        )
                                                    },
                                                    onClick = {
                                                        editingSection = relSection; editingPeriod =
                                                        "morning"
                                                        tempSectionName = sectionNames["morning_$relSection"] ?: ""
                                                        val (start, end) = parseTimeRange(timeStr)
                                                        tempStartHour =
                                                            start.first; tempStartMinute =
                                                        start.second
                                                        tempEndHour = end.first; tempEndMinute =
                                                        end.second
                                                        showTimeDialog = true
                                                    },
                                                    holdDownState = showTimeDialog && editingPeriod == "morning" && editingSection == relSection
                                                )
                                            }
                                        }
                                    }
                                }

                                item(key = "afternoon") {
                                    SmallTitle(
                                        text = "下午",
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            (1..afternoonSections).forEach { relSection ->
                                                val timeStr = afternoonTimes[relSection] ?: ""
                                                ArrowPreference(
                                                    title = getSectionTitle("afternoon", relSection),
                                                    endActions = {
                                                        Text(
                                                            formatTimeRangeForDisplay(timeStr),
                                                            fontSize = 14.5.sp,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                        )
                                                    },
                                                    onClick = {
                                                        editingSection = relSection; editingPeriod =
                                                        "afternoon"
                                                        tempSectionName = sectionNames["afternoon_$relSection"] ?: ""
                                                        val (start, end) = parseTimeRange(timeStr)
                                                        tempStartHour =
                                                            start.first; tempStartMinute =
                                                        start.second
                                                        tempEndHour = end.first; tempEndMinute =
                                                        end.second
                                                        showTimeDialog = true
                                                    },
                                                    holdDownState = showTimeDialog && editingPeriod == "afternoon" && editingSection == relSection
                                                )
                                            }
                                        }
                                    }
                                }

                                item(key = "evening") {
                                    SmallTitle(
                                        text = "晚上",
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            (1..eveningSections).forEach { relSection ->
                                                val timeStr = eveningTimes[relSection] ?: ""
                                                ArrowPreference(
                                                    title = getSectionTitle("evening", relSection),
                                                    endActions = {
                                                        Text(
                                                            formatTimeRangeForDisplay(timeStr),
                                                            fontSize = 14.5.sp,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                        )
                                                    },
                                                    onClick = {
                                                        editingSection = relSection; editingPeriod =
                                                        "evening"
                                                        tempSectionName = sectionNames["evening_$relSection"] ?: ""
                                                        val (start, end) = parseTimeRange(timeStr)
                                                        tempStartHour =
                                                            start.first; tempStartMinute =
                                                        start.second
                                                        tempEndHour = end.first; tempEndMinute =
                                                        end.second
                                                        showTimeDialog = true
                                                    },
                                                    holdDownState = showTimeDialog && editingPeriod == "evening" && editingSection == relSection
                                                )
                                            }
                                        }
                                    }
                                }

                                item(key = "special_courses") {
                                    SmallTitle(
                                        text = "特殊课程"
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            if (specialBlocks.isEmpty()) {
                                                ArrowPreference(
                                                    title = "暂无特殊课程",
                                                    summary = "点击添加早读、眼保健操等无编号时段",
                                                    holdDownState = showSpecialDialog,
                                                    onClick = {
                                                        resetSpecialTemp()
                                                        editingSpecialIndex = -1
                                                        showSpecialDialog = true
                                                    }
                                                )
                                            } else {
                                                // 始终按开始时间排序展示
                                                sortSpecialBlocksByTime(specialBlocks).forEachIndexed { index, block ->
                                                    ArrowPreference(
                                                        title = if (block.name.isNotBlank()) block.name else "特殊课程",
                                                        summary = specialBlockSummary(block),
                                                        // 只压暗被点开弹窗的那一项
                                                        holdDownState = showSpecialDialog && index == editingSpecialIndex,
                                                        onClick = {
                                                            editingSpecialIndex = index
                                                            tempSpecialName = block.name
                                                            val (sh, sm) = parseTimeHm(block.startTime)
                                                            tempSpecialStartHour = sh; tempSpecialStartMinute = sm
                                                            val (eh, em) = parseTimeHm(block.endTime)
                                                            tempSpecialEndHour = eh; tempSpecialEndMinute = em
                                                            showSpecialDialog = true
                                                        }
                                                    )
                                                }
                                                ArrowPreference(
                                                    title = "添加特殊课程",
                                                    holdDownState = showSpecialDialog && editingSpecialIndex == -1,
                                                    onClick = {
                                                        // 添加不能带出上一次编辑的名称/时间
                                                        resetSpecialTemp()
                                                        editingSpecialIndex = -1
                                                        showSpecialDialog = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                // 页面最后的危险操作：整宽红字按钮，样式同 AddCourseDialog 的删除按钮
                                if (canDeleteRoutine) {
                                    item(key = "routine_delete") {
                                        Button(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp),
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.Confirm
                                                )
                                                showDeleteRoutineDialog = true
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                color = if (isDark) Color.White.copy(alpha = 0.04f)
                                                else Color.Black.copy(alpha = 0.04f)
                                            ),
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = destructiveColor
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "删除作息",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = destructiveColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OverlayDialog(
                            title = when (quickEditType) {
                                "duration" -> "每节课时长"
                                "short_break" -> "小课间休息时长"
                                "long_morning" -> "上午大课间设置"
                                "long_afternoon" -> "下午大课间设置"
                                "long_evening" -> "晚上大课间设置"
                                "start_morning" -> "上午开始时间"
                                "start_afternoon" -> "下午开始时间"
                                "start_evening" -> "晚上开始时间"
                                else -> ""
                            },
                            show = showQuickItemDialog,
                            onDismissRequest = { showQuickItemDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (quickEditType.startsWith("start_")) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NumberPicker(
                                            value = quickTempHour,
                                            onValueChange = { quickTempHour = it },
                                            range = when (quickEditType) {
                                                "start_morning" -> 6..12; "start_afternoon" -> 12..18; else -> 17..22
                                            },
                                            visibleItemCount = 3,
                                            itemHeight = 60.dp,
                                            label = { String.format("%02d", it) },
                                            wrapAround = true,
                                            textStyle = MiuixTheme.textStyles.title1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            ":",
                                            style = MiuixTheme.textStyles.title2,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding().offset(y = (-2).dp)
                                        )
                                        val qMinIdx =
                                            minuteValues.indexOf(quickTempMinute).coerceAtLeast(0)
                                        NumberPicker(
                                            value = qMinIdx,
                                            onValueChange = { quickTempMinute = minuteValues[it] },
                                            range = minuteValues.indices,
                                            visibleItemCount = 3,
                                            itemHeight = 60.dp,
                                            label = { String.format("%02d", minuteValues[it]) },
                                            wrapAround = true,
                                            textStyle = MiuixTheme.textStyles.title1,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else if (quickEditType.startsWith("long_")) {
                                    val sectionCount = when (quickEditType) {
                                        "long_morning" -> morningSections
                                        "long_afternoon" -> afternoonSections
                                        "long_evening" -> eveningSections
                                        else -> 4
                                    }
                                    val sectionRange = 1 until sectionCount
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NumberPicker(
                                            value = quickTempSection,
                                            onValueChange = { quickTempSection = it },
                                            range = sectionRange,
                                            visibleItemCount = 3,
                                            itemHeight = 60.dp,
                                            label = { "第${it}节后" },
                                            wrapAround = false,
                                            textStyle = MiuixTheme.textStyles.title2,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val breakOptions = listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60)
                                        val breakIndex =
                                            breakOptions.indexOf(quickTempValue).coerceAtLeast(0)
                                        NumberPicker(
                                            value = breakIndex,
                                            onValueChange = { quickTempValue = breakOptions[it] },
                                            range = breakOptions.indices,
                                            visibleItemCount = 3,
                                            itemHeight = 60.dp,
                                            label = { "${breakOptions[it]}分钟" },
                                            wrapAround = false,
                                            textStyle = MiuixTheme.textStyles.title2,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    val options = when (quickEditType) {
                                        "duration" -> listOf(40, 45, 50, 55, 60)
                                        "short_break" -> listOf(0, 5, 10, 15, 20, 25)
                                        else -> listOf(5, 10, 15, 20, 25, 30)
                                    }
                                    val currentIndex =
                                        options.indexOf(quickTempValue).coerceAtLeast(0)
                                    NumberPicker(
                                        value = currentIndex,
                                        onValueChange = { quickTempValue = options[it] },
                                        range = options.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { "${options[it]}分钟" },
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = "取消",
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showQuickItemDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = "确定",
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            when (quickEditType) {
                                                "duration" -> {
                                                    classDuration = quickTempValue
                                                }

                                                "short_break" -> {
                                                    shortBreak = quickTempValue
                                                }

                                                "long_morning" -> {
                                                    longBreakMorning =
                                                        quickTempValue; longBreakMorningSection =
                                                        quickTempSection
                                                }

                                                "long_afternoon" -> {
                                                    longBreakAfternoon =
                                                        quickTempValue; longBreakAfternoonSection =
                                                        quickTempSection
                                                }

                                                "long_evening" -> {
                                                    longBreakEvening =
                                                        quickTempValue; longBreakEveningSection =
                                                        quickTempSection
                                                }

                                                "start_morning" -> {
                                                    morningStartHour =
                                                        quickTempHour; morningStartMinute =
                                                        quickTempMinute
                                                }

                                                "start_afternoon" -> {
                                                    afternoonStartHour =
                                                        quickTempHour; afternoonStartMinute =
                                                        quickTempMinute
                                                }

                                                "start_evening" -> {
                                                    eveningStartHour =
                                                        quickTempHour; eveningStartMinute =
                                                        quickTempMinute
                                                }
                                            }
                                            showQuickItemDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }


                        OverlayDialog(
                            title = if (editingSpecialIndex == -1) "添加特殊课程" else "编辑特殊课程",
                            summary = null,
                            show = showSpecialDialog,
                            onDismissRequest = { showSpecialDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                NativeMiuixTextField(
                                    value = tempSpecialName,
                                    onValueChange = { tempSpecialName = it },
                                    label = "特殊课程",
                                    useLabelAsPlaceholder = true,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    NumberPicker(
                                        value = tempSpecialStartHour,
                                        onValueChange = { tempSpecialStartHour = it },
                                        range = 0..23,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { String.format("%02d", it) },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        ":",
                                        style = MiuixTheme.textStyles.paragraph,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding().offset(y = (-2).dp)
                                    )
                                    val sMinIdx = minuteValues.indexOf(tempSpecialStartMinute).coerceAtLeast(0)
                                    NumberPicker(
                                        value = sMinIdx,
                                        onValueChange = { tempSpecialStartMinute = minuteValues[it] },
                                        range = minuteValues.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { String.format("%02d", minuteValues[it]) },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "-",
                                        style = MiuixTheme.textStyles.title2,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding()
                                    )
                                    NumberPicker(
                                        value = tempSpecialEndHour,
                                        onValueChange = { tempSpecialEndHour = it },
                                        range = 0..23,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { String.format("%02d", it) },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        ":",
                                        style = MiuixTheme.textStyles.paragraph,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding().offset(y = (-2).dp)
                                    )
                                    val eMinIdx = minuteValues.indexOf(tempSpecialEndMinute).coerceAtLeast(0)
                                    NumberPicker(
                                        value = eMinIdx,
                                        onValueChange = { tempSpecialEndMinute = minuteValues[it] },
                                        range = minuteValues.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { String.format("%02d", minuteValues[it]) },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // 按星期的安排：原来只能回课表页点横带逐格填，这里一并能改
                                val editingSpecialBlock = specialBlocks.getOrNull(editingSpecialIndex)
                                if (editingSpecialIndex != -1) {
                                    Text(
                                        text = "按星期的安排",
                                        fontSize = 15.sp,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                    SpecialItemsEditorSection(
                                        items = editingSpecialBlock?.safeItems ?: emptyList(),
                                        onItemsChange = { newItems ->
                                            val target = editingSpecialIndex
                                            if (target in specialBlocks.indices) {
                                                specialBlocks = specialBlocks.mapIndexed { idx, b ->
                                                    if (idx == target) b.copy(items = newItems) else b
                                                }
                                            }
                                        },
                                        liquidGlassBackdrop = liquidGlassBackdrop
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = "取消",
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showSpecialDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = "保存",
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            val startMinutes = tempSpecialStartHour * 60 + tempSpecialStartMinute
                                            val endMinutes = tempSpecialEndHour * 60 + tempSpecialEndMinute
                                            if (endMinutes <= startMinutes) {
                                                Toast.makeText(context, "结束时间需晚于开始时间", Toast.LENGTH_SHORT).show()
                                                return@TextButton
                                            }
                                            val startStr = String.format("%02d:%02d", tempSpecialStartHour, tempSpecialStartMinute)
                                            val endStr = String.format("%02d:%02d", tempSpecialEndHour, tempSpecialEndMinute)
                                            val block = SpecialBlock(
                                                id = if (editingSpecialIndex == -1) System.currentTimeMillis() else specialBlocks[editingSpecialIndex].id,
                                                name = tempSpecialName,
                                                startTime = startStr,
                                                endTime = endStr,
                                                items = if (editingSpecialIndex == -1) null else specialBlocks[editingSpecialIndex].items
                                            )
                                            val updated = specialBlocks.toMutableList()
                                            if (editingSpecialIndex == -1) {
                                                updated.add(block)
                                            } else {
                                                updated[editingSpecialIndex] = block
                                            }
                                            // 保持列表按时间排序
                                            specialBlocks = sortSpecialBlocksByTime(updated)
                                            showSpecialDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            // 右上角删除按钮
                            if (editingSpecialIndex != -1) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(y = (-42).dp)
                                        .size(36.dp)
                                        .clip(ContinuousRoundedRectangle(20))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showSpecialDeleteConfirm = true
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = "删除",
                                        colorFilter = ColorFilter.tint(Color(0xFFF44336)),
                                        modifier = Modifier.size(23.dp),
                                    )
                                }
                            }
                            }
                        }

                        OverlayDialog(
                            title = "删除特殊课程",
                            summary = "确定要删除这条特殊课程吗？\n此操作不可撤销。",
                            show = showSpecialDeleteConfirm,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            onDismissRequest = { showSpecialDeleteConfirm = false }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    "取消",
                                    {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showSpecialDeleteConfirm = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    "删除",
                                    {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        if (editingSpecialIndex in specialBlocks.indices) {
                                            specialBlocks = sortSpecialBlocksByTime(
                                                specialBlocks.toMutableList().apply { removeAt(editingSpecialIndex) }
                                            )
                                        }
                                        showSpecialDeleteConfirm = false
                                        showSpecialDialog = false
                                    },
                                    textColor = Color(0xFFF44336),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        OverlayDialog(
                            title = when (editingPeriod) {
                                "morning" -> "第${editingSection}节时间设置"
                                "afternoon" -> "第${morningSections + editingSection}节时间设置"
                                "evening" -> "第${morningSections + afternoonSections + editingSection}节时间设置"
                                else -> "时间设置"
                            },
                            show = showTimeDialog,
                            onDismissRequest = { showTimeDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                NativeMiuixTextField(
                                    value = tempSectionName,
                                    onValueChange = { tempSectionName = it },
                                    label = "自定义节次名称",
                                    useLabelAsPlaceholder = true,
                                    singleLine = true,
                                    trailingIcon = {
                                        val nameCount = tempSectionName.length
                                        Text(
                                            "$nameCount/2",
                                            fontSize = 14.2.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (nameCount > 2) ComposeColor(0xFFF44336)
                                            else MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    NumberPicker(
                                        value = tempStartHour,
                                        onValueChange = { tempStartHour = it },
                                        range = 0..23,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { String.format("%02d", it) },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        ":",
                                        style = MiuixTheme.textStyles.paragraph,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding().offset(y = (-2).dp)
                                    )
                                    val sMinIdx =
                                        minuteValues.indexOf(tempStartMinute).coerceAtLeast(0)
                                    NumberPicker(
                                        value = sMinIdx,
                                        onValueChange = { tempStartMinute = minuteValues[it] },
                                        range = minuteValues.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { String.format("%02d", minuteValues[it]) },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "-",
                                        style = MiuixTheme.textStyles.title2,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding()
                                    )
                                    NumberPicker(
                                        value = tempEndHour,
                                        onValueChange = { tempEndHour = it },
                                        range = 0..23,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { String.format("%02d", it) },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        ":",
                                        style = MiuixTheme.textStyles.paragraph,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding().offset(y = (-2).dp)
                                    )
                                    val eMinIdx =
                                        minuteValues.indexOf(tempEndMinute).coerceAtLeast(0)
                                    NumberPicker(
                                        value = eMinIdx,
                                        onValueChange = { tempEndMinute = minuteValues[it] },
                                        range = minuteValues.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { String.format("%02d", minuteValues[it]) },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = "取消",
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showTimeDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = "确定",
                                        enabled = tempSectionName.length <= 2,
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            val startMinutes = tempStartHour * 60 + tempStartMinute
                                            val endMinutes = tempEndHour * 60 + tempEndMinute
                                            if (endMinutes <= startMinutes) {
                                                Toast.makeText(
                                                    context,
                                                    "结束时间需晚于开始时间",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@TextButton
                                            }
                                            val newTimeStr = "${
                                                String.format(
                                                    "%02d:%02d",
                                                    tempStartHour,
                                                    tempStartMinute
                                                )
                                            }-${
                                                String.format(
                                                    "%02d:%02d",
                                                    tempEndHour,
                                                    tempEndMinute
                                                )
                                            }"
                                            when (editingPeriod) {
                                                "morning" -> {
                                                    morningTimes = morningTimes.toMutableMap()
                                                        .apply { put(editingSection, newTimeStr) }
                                                }

                                                "afternoon" -> {
                                                    afternoonTimes = afternoonTimes.toMutableMap()
                                                        .apply { put(editingSection, newTimeStr) }
                                                }

                                                "evening" -> {
                                                    eveningTimes = eveningTimes.toMutableMap()
                                                        .apply { put(editingSection, newTimeStr) }
                                                }
                                            }
                                            // 空名称则恢复默认"第N节"
                                            val nameKey = "${editingPeriod}_${editingSection}"
                                            val trimmedName = tempSectionName.trim()
                                            sectionNames = sectionNames.toMutableMap().apply {
                                                if (trimmedName.isEmpty()) remove(nameKey)
                                                else put(nameKey, trimmedName)
                                            }
                                            showTimeDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        OverlayDialog(
                            title = "时间重叠",
                            show = showOverlapDialog,
                            onDismissRequest = { showOverlapDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = overlapMessage,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "存在时间重叠，请重新设置",
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    text = "知道了",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showOverlapDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 特殊课程与其它时间重叠：只警告。块会插到被压节次之前排开，
                        // 渲染不会坏；老数据也可能本来就重叠，给「仍然保存」出口。
                        OverlayDialog(
                            title = "时间重叠（可继续保存）",
                            show = showOverlapWarningDialog,
                            onDismissRequest = { showOverlapWarningDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = overlapMessage,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "特殊课程会排在该时段之前，确认无误可继续保存",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = "返回修改",
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showOverlapWarningDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = "仍然保存",
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showOverlapWarningDialog = false
                                            commitSave()
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // ---- 修改生效日期 ----
                        OverlayDialog(
                            title = "生效日期",
                            summary = "到这一天自动切换到该作息",
                            show = showRoutineDateDialog,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            onDismissRequest = { showRoutineDateDialog = false }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NumberPicker(
                                        value = tempRoutineMonth,
                                        // 切月时把日一起夹住：1/31 切到 2 月会变成 2/28
                                        onValueChange = {
                                            tempRoutineMonth = it
                                            tempRoutineDay = com.haooz.chedule.data.TimeRoutine
                                                .clampDayOfMonth(it, tempRoutineDay)
                                        },
                                        range = 1..12,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { "${it}月" },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberPicker(
                                        value = tempRoutineDay,
                                        onValueChange = { tempRoutineDay = it },
                                        // 上限跟随月份，否则能选出「2 月 31 日」—— 这样的作息永远不会生效
                                        range = 1..com.haooz.chedule.data.TimeRoutine
                                            .daysInMonth(tempRoutineMonth),
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { "${it}日" },
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = "取消",
                                        onClick = { showRoutineDateDialog = false },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = "确定",
                                        onClick = {
                                            // 同一天只能有一套作息，否则到点切换的结果取决于列表顺序
                                            if (timeConfig.isRoutineDateTaken(
                                                    tempRoutineMonth, tempRoutineDay,
                                                    excludeId = routineId
                                                )
                                            ) {
                                                Toast.makeText(
                                                    context,
                                                    "该日期已有作息，请换一个生效日期",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@TextButton
                                            }
                                            routineEffectiveMonth = tempRoutineMonth
                                            routineEffectiveDay = tempRoutineDay
                                            showRoutineDateDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // ---- 删除作息 ----
                        OverlayDialog(
                            title = "删除作息",
                            summary = "确定要删除作息「${editingRoutine?.name ?: routineName}」吗？\n此操作不可撤销。",
                            show = showDeleteRoutineDialog,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            onDismissRequest = { showDeleteRoutineDialog = false }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    text = "取消",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.Confirm
                                        )
                                        showDeleteRoutineDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    text = "删除",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.Confirm
                                        )
                                        showDeleteRoutineDialog = false
                                        onDeleteRoutine(routineId)
                                        triggerExitAndBack()
                                    },
                                    textColor = destructiveColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
