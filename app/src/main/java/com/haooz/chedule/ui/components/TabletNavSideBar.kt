package com.haooz.chedule.ui.components
import com.haooz.chedule.ui.utils.ApiCompat

import android.app.Activity
import android.os.Build
import android.content.Context
import android.content.Intent
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
 import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.basic.DropdownPanelDragSelectState
import com.haooz.chedule.ui.basic.dropdownPanelDragSelect
import com.haooz.chedule.ui.basic.dropdownPanelEntry
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 平板左侧导航全局状态。
 * 只保留侧边态：true = 展开（图标+文字），false = 折叠（仅图标）。
 */
object TabletNavSideState {
    /** 展开=完整侧栏；折叠=仅图标轨 */
    var expanded by mutableStateOf(true)

    /**
     * 展开进度 0=折叠图标轨，1=完整侧栏。
     * 只在 layout/draw/graphicsLayer 读，禁止在组合期读 ——
     * 否则 CourseScheduleApp 整树会随动画每帧重组，平板展开/缩回直接掉帧。
     */
    val expandProgress = mutableFloatStateOf(1f)

    /** 二级页点选主 tab 后 MainActivity 待处理下标；-1 = 无 */
    var pendingMainTab by mutableStateOf(-1)
}

/** 顶栏糊层 draw 阶段跟踪侧栏伸缩用的稳定引用（勿在组合期调用） */
val tabletNavExpandSampleTrack: () -> Float = { TabletNavSideState.expandProgress.floatValue }

/** 展开侧栏占位宽度占屏宽比例 */
const val TabletNavSideWidthFraction = 0.22f

/** 折叠态遮罩（玻璃）宽度 */
val TabletNavIconRailWidth = 84.dp

/** 图标中心相对遮罩左缘的固定位置（折叠态即 84/2，面板内居中） */
private val TabletNavIconCenterX = 42.dp

/** 图标尺寸，展开/折叠不变 */
private val TabletNavIconSize = 28.dp

/** 遮罩内边距，展开/折叠始终保持 8 */
private val TabletNavMaskPadding = 8.dp

/** 遮罩相对屏幕左缘的间距，展开/折叠保持不变（不贴边、也不左跳） */
val TabletNavSideInset = 12.dp

/** 遮罩底缘间距，保持不变 */
private val TabletNavBottomInset = 12.dp

/**
 * 条目内图标额外左偏：遮罩内边距 8 + 此值 20 + 图标半宽 14 = 图标中心 42（相对遮罩左缘）。
 * 折叠时图标在 84 宽轨内居中；展开时中线相对遮罩不动。
 */
private val TabletNavIconAlignStart =
    TabletNavIconCenterX - TabletNavMaskPadding - TabletNavIconSize / 2f

/**
 * 条目内选中遮罩的四周内缩。
 * 滑选高光必须用同一套值（见 [TabletNavSideItem] 的 dropdownPanelEntry），
 * 否则一个满幅、一个内缩，两个遮罩叠一起对不齐。
 */
private val TabletNavItemMaskHPadding = 4.dp
private val TabletNavItemMaskVPadding = 2.dp

private val TabletNavExpandSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** 驱动展开进度动画。只写 [TabletNavSideState.expandProgress]，不在组合期读。 */
@Composable
fun TabletNavExpandAnimator() {
    LaunchedEffect(TabletNavSideState.expanded) {
        val target = if (TabletNavSideState.expanded) 1f else 0f
        if (TabletNavSideState.expandProgress.floatValue == target) return@LaunchedEffect
        animate(
            initialValue = TabletNavSideState.expandProgress.floatValue,
            targetValue = target,
            animationSpec = TabletNavExpandSpec,
        ) { value, _ -> TabletNavSideState.expandProgress.floatValue = value }
    }
}

/** 侧栏避让宽度 px。竖屏展开为叠层时固定按折叠轨，不随展开变宽。 */
fun Density.tabletNavSideInsetPx(screenWidthDp: Int, screenHeightDp: Int = screenWidthDp): Float {
    if (screenWidthDp < 600) return 0f
    val expandedWidth = screenWidthDp.dp * TabletNavSideWidthFraction
    val collapsedTotal = TabletNavSideInset + TabletNavIconRailWidth
    if (screenHeightDp > screenWidthDp) {
        return collapsedTotal.toPx()
    }
    return androidx.compose.ui.unit.lerp(
        collapsedTotal,
        expandedWidth,
        TabletNavSideState.expandProgress.floatValue,
    ).toPx()
}

/**
 * 内容区左侧的避让宽度。
 * 竖屏：展开是纯叠层（侧栏浮在内容上 + 压暗），内容恒按折叠轨避让，展开不改变布局。
 * 横屏：展开仍让位给侧栏。
 *
 * 目标宽度在展开/折叠那一刻一次性测量出来（只重排一次），中间所有帧只做
 * graphicsLayer 平移，动画期间内容树零重绘。
 */
@Composable
fun tabletNavRailStartPadding(): Modifier {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    if (screenWidthDp < 600) return Modifier
    val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp
    val density = LocalDensity.current
    val collapsedTotalPx = with(density) { (TabletNavSideInset + TabletNavIconRailWidth).toPx() }
    val expandedWidthPx = with(density) { (screenWidthDp.dp * TabletNavSideWidthFraction).toPx() }
    // 竖屏叠层：内容 start padding 恒为折叠轨，展开不改变布局
    if (isPortrait) {
        return TabletNavRailTargetPaddingElement(collapsedTotalPx, collapsedTotalPx)
    }
    return TabletNavRailTargetPaddingElement(collapsedTotalPx, expandedWidthPx)
        .graphicsLayer {
            val p = TabletNavSideState.expandProgress.floatValue
            val targetPad =
                if (TabletNavSideState.expanded) expandedWidthPx else collapsedTotalPx
            translationX =
                androidx.compose.ui.util.lerp(collapsedTotalPx, expandedWidthPx, p) - targetPad
        }
}

private class TabletNavRailTargetPaddingElement(
    private val collapsedTotalPx: Float,
    private val expandedWidthPx: Float,
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        /** 只跟展开布尔、不跟进度：一次伸缩只在这里重排一次 */
        val padPx = if (TabletNavSideState.expanded) expandedWidthPx else collapsedTotalPx
        val padInt = padPx.roundToInt().coerceAtLeast(0)
        // 与 Modifier.padding(start=) 同语义：只收窄子约束横向，高度跟内容，不撑满
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = (constraints.minWidth - padInt).coerceAtLeast(0),
                maxWidth = (constraints.maxWidth - padInt).coerceAtLeast(0),
            )
        )
        val width = (placeable.width + padInt).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            placeable.placeRelative(padInt, 0)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabletNavRailTargetPaddingElement) return false
        return collapsedTotalPx == other.collapsedTotalPx && expandedWidthPx == other.expandedWidthPx
    }

    override fun hashCode(): Int {
        var result = collapsedTotalPx.hashCode()
        result = 31 * result + expandedWidthPx.hashCode()
        return result
    }
}

/** 侧栏玻璃面板宽度随展开进度在 layout 期插值，不进组合 */
internal fun Modifier.tabletNavPanelWidth(
    collapsedWidthPx: Float,
    expandedWidthPx: Float,
): Modifier = this then TabletNavPanelWidthElement(collapsedWidthPx, expandedWidthPx)

private class TabletNavPanelWidthElement(
    private val collapsedWidthPx: Float,
    private val expandedWidthPx: Float,
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val target = androidx.compose.ui.util.lerp(
            collapsedWidthPx,
            expandedWidthPx,
            TabletNavSideState.expandProgress.floatValue,
        ).roundToInt()
        val w = target.coerceIn(constraints.minWidth, constraints.maxWidth)
        val placeable = measurable.measure(
            constraints.copy(minWidth = w, maxWidth = w)
        )
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabletNavPanelWidthElement) return false
        return collapsedWidthPx == other.collapsedWidthPx && expandedWidthPx == other.expandedWidthPx
    }

    override fun hashCode(): Int {
        var result = collapsedWidthPx.hashCode()
        result = 31 * result + expandedWidthPx.hashCode()
        return result
    }
}

/**
 * 设置页叠层标题槽：左/右栏顶栏标题随侧栏避让平移与分宽。
 * 竖屏侧栏为叠层，标题不跟随展开；横屏仍随避让。进度只在 measure 读。
 */
@Composable
internal fun Modifier.tabletNavChromeTitleSlot(
    maxWPx: Float,
    collapsedTotalPx: Float,
    expandedWidthPx: Float,
    statusBarPx: Int,
    heightPx: Int,
    isLeftColumn: Boolean,
): Modifier {
    val configuration = LocalConfiguration.current
    val lockCollapsed = configuration.screenHeightDp > configuration.screenWidthDp
    return this then TabletNavChromeTitleSlotElement(
        maxWPx = maxWPx,
        collapsedTotalPx = collapsedTotalPx,
        expandedWidthPx = expandedWidthPx,
        statusBarPx = statusBarPx,
        heightPx = heightPx,
        isLeftColumn = isLeftColumn,
        lockCollapsed = lockCollapsed,
    )
}

private class TabletNavChromeTitleSlotElement(
    private val maxWPx: Float,
    private val collapsedTotalPx: Float,
    private val expandedWidthPx: Float,
    private val statusBarPx: Int,
    private val heightPx: Int,
    private val isLeftColumn: Boolean,
    private val lockCollapsed: Boolean = false,
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val sidePad = if (lockCollapsed) {
            collapsedTotalPx
        } else {
            androidx.compose.ui.util.lerp(
                collapsedTotalPx,
                expandedWidthPx,
                TabletNavSideState.expandProgress.floatValue,
            )
        }
        val contentW = (maxWPx - sidePad).coerceAtLeast(0f)
        // 左栏标题槽与分栏同步：固定屏宽 0.39（maxWPx 即全屏宽）
        val leftW = maxWPx * 0.39f
        val x: Int
        val w: Int
        if (isLeftColumn) {
            x = sidePad.roundToInt()
            w = leftW.roundToInt().coerceAtLeast(0)
        } else {
            x = (sidePad + leftW).roundToInt()
            w = (contentW - leftW).roundToInt().coerceAtLeast(0)
        }
        val placeable = measurable.measure(
            Constraints.fixed(
                width = w.coerceIn(0, maxWPx.roundToInt().coerceAtLeast(0)),
                height = heightPx,
            )
        )
        return layout(maxWPx.roundToInt().coerceAtLeast(placeable.width + x), statusBarPx + heightPx) {
            placeable.place(x, statusBarPx)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabletNavChromeTitleSlotElement) return false
        return maxWPx == other.maxWPx &&
            collapsedTotalPx == other.collapsedTotalPx &&
            expandedWidthPx == other.expandedWidthPx &&
            statusBarPx == other.statusBarPx &&
            heightPx == other.heightPx &&
            isLeftColumn == other.isLeftColumn &&
            lockCollapsed == other.lockCollapsed
    }

    override fun hashCode(): Int {
        var result = maxWPx.hashCode()
        result = 31 * result + collapsedTotalPx.hashCode()
        result = 31 * result + expandedWidthPx.hashCode()
        result = 31 * result + statusBarPx
        result = 31 * result + heightPx
        result = 31 * result + isLeftColumn.hashCode()
        return result
    }
}

/**
 * 整块玻璃遮罩圆角：屏幕圆角 − 左缘间距；取不到系统圆角时退回 28.dp。
 */
@Composable
private fun rememberTabletNavMaskCorner(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenRadius = remember(context, density) {
        val activity = context as? android.app.Activity
        val px = activity?.let { ApiCompat.windowCornerRadius(it.window) } ?: 0f
        with(density) { px.toDp() }
    }
    val resolved = if (screenRadius > 0.dp) screenRadius else 28.dp
    return (resolved - 10.dp).coerceAtLeast(0.dp)
}

fun Context.startActivityNoNavAnim(intent: Intent) {
    startActivity(intent)
    if (this is Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

fun Context.startActivityTabletNavAware(intent: Intent) {
    if (TabletNavSideState.expanded) {
        startActivityNoNavAnim(intent)
    } else {
        startActivity(intent)
    }
}

fun navigateMainTabFromSecondary(context: Context, tab: Int) {
    TabletNavSideState.pendingMainTab = tab
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_MAIN_TAB, tab)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    context.startActivityNoNavAnim(intent)
    if (context is Activity && context !is MainActivity) {
        context.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            context.overridePendingTransition(0, 0)
        }
    }
}

/**
 * pad 侧边导航：展开/折叠是同一套布局的连续变形。
 * 遮罩左缘间距与内边距恒定，图标因此始终落在同一条竖直线上；
 * 折叠只收窄遮罩宽度并淡出文字，不整体平移。
 * 宽度/文字透明度只在 layout/draw 读进度，不进组合。
 */
@Composable
fun TabletNavSideBar(
    backdrop: Backdrop?,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isShiftMode: Boolean = false,
    showBackToNow: Boolean = false,
    onBackToNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TabletNavExpandAnimator()
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp
    // 仅竖屏默认折叠（初始化一次）；横屏保持原默认展开
    LaunchedEffect(Unit) {
        if (isPortrait) {
            TabletNavSideState.expanded = false
            TabletNavSideState.expandProgress.floatValue = 0f
        }
    }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarPadding > 0.dp) statusBarPadding else 36.dp
    val isLightTheme = !isAppDarkTheme()
    val textColor = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
    val containerColor =
        if (isLightTheme) Color(0xFFFFFFFF).copy(0.8f) else Color(0xFF242424).copy(0.8f)
    val solidContainer = if (isLightTheme) Color(0xFFFBFBFB) else Color(0xFF1C1C1E)
    val defaultEdgeLight = rememberDefaultEdgeLight(baseColor = containerColor)
    // 按压高光：跟手光晕，按哪亮哪。
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            // 倍率随展开进度反向补偿：展开 ×0.4 / 收起 ×0.7 —— 两者乘出的绝对半径
            // 接近（minDimension 变小、倍率变大），光晕视觉大小不随伸缩跳变。
            // 读的是 draw 阶段的进度，不会因此触发重组。
            radiusScale = {
                0.7f - 0.3f * TabletNavSideState.expandProgress.floatValue.coerceIn(0f, 1f)
            },
            radiusBaseDp = 150.dp,
            // 只要光晕：整幅 8% 加白叠在 84~270dp 宽的整条侧栏上就是整块泛白
            drawFlatOverlay = false,
        )
    }
    // 滑动点选：复用弹窗菜单那套跟手滑选，状态自持。
    // 侧栏无滚动 → fitsOnScreen 默认 true，走完整滑选分支。
    val dragSelect = remember { DropdownPanelDragSelectState() }
    val hapticFeedback = LocalHapticFeedback.current
    // 玻璃遮罩圆角：屏幕圆角 − 左缘间距，不写死
    val sideCorner = rememberTabletNavMaskCorner()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenWidth = screenWidthDp.dp
    val collapsedPanelPx = with(density) { TabletNavIconRailWidth.toPx() }
    val expandedPanelPx = with(density) {
        (screenWidth * TabletNavSideWidthFraction - TabletNavSideInset).toPx()
    }
    val tabs = if (isShiftMode) {
        listOf(
            "排班课表" to MiuixIcons.Months,
            "设置" to MiuixIcons.Demibold.Settings,
        )
    } else {
        listOf(
            "今日" to MiuixIcons.Album,
            "课程表" to MiuixIcons.Months,
            "我的" to MiuixIcons.ContactsCircle,
            "课程管理" to MiuixIcons.Backup,
            "切换课表" to MiuixIcons.ConvertFile,
        )
    }

    // 左缘间距恒定：不贴边，折叠只改遮罩宽度，栏体不平移
    val panelStartInset = TabletNavSideInset

    // 「数据管理」区块实测高度：折叠后选中项靠反向平移抵消它，贴到上方选项
    var dataGroupHeightPx by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        // 竖屏展开压暗：淡入淡出；折叠时不占命中。离屏合成，不进玻璃模糊采样
        if (isPortrait) {
            AnimatedVisibility(
                visible = TabletNavSideState.expanded,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(180)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.12f
                            // 离屏合成，避免被 drawBackdrop/模糊采样进玻璃
                            compositingStrategy =
                                androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            TabletNavSideState.expanded = false
                        }
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(
                    start = panelStartInset,
                    top = topPadding + 2.dp,
                    bottom = TabletNavBottomInset,
                )
                .tabletNavPanelWidth(collapsedPanelPx, expandedPanelPx)
                .fillMaxHeight()
                .then(
                    if (backdrop != null) {
                        Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { ContinuousRoundedRectangle(sideCorner) },
                                effects = {
                                    vibrancy()
                                    blur(12f.dp.toPx())
                                },
                                highlight = null,
                                onDrawSurface = { drawRect(containerColor) },
                            )
                            .edgeLight(shape = ContinuousRoundedRectangle(sideCorner), edgeLight = defaultEdgeLight)
                    } else {
                        // 无玻璃分支没有 drawBackdrop 的 clipPath 兜底：
                        // 高光是矩形 drawRect，不自己裁会溢出圆角画成方光块
                        Modifier
                            .background(solidContainer, ContinuousRoundedRectangle(sideCorner))
                            .clip(ContinuousRoundedRectangle(sideCorner))
                    }
                )
                // 高光画在材质之上、条目内容之下（内容在 drawContent 里更靠内，后画）
                .then(interactiveHighlight.modifier)
                // 触发挂整条面板：手指落在任意条目上都有光晕。
                // observeConsumed=true，条目的 clickable 消费事件不影响它跟手
                .then(interactiveHighlight.gestureModifier)
                .padding(TabletNavMaskPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            TabletNavSideItem(
                icon = MiuixIcons.Regular.Sidebar,
                label = "导航",
                selected = false,
                textColor = textColor,
                showLabel = false,
                onClick = { TabletNavSideState.expanded = !TabletNavSideState.expanded },
            )

            // 页签之间更紧；与上方「导航」的间距仍由外层 spacedBy 控制。
            // 滑动点选挂这一层：正好只覆盖页签，「导航」折叠按钮与底部「今」
            // 都在手势节点之外，各自 clickable 不会被 down.consume() 波及。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 手势与条目坐标必须同源：条目用 boundsInRoot 减这个值，
                    // 手指用的是本节点的局部坐标，原点必须是同一个
                    .onGloballyPositioned {
                        dragSelect.panelTopInRoot = it.boundsInRoot().top
                    }
                    .dropdownPanelDragSelect(
                        state = dragSelect,
                        // 现读、不能捕获（pointerInput(Unit) 的 lambda 只在首次组合跑一次）。
                        // 折叠态 fraction<0.98 时手势层直接放行且不消费，条目点击照常 ——
                        // 折叠时上移到「我的」下方的那颗数据项才点得到。
                        fraction = { TabletNavSideState.expandProgress.floatValue },
                        hapticFeedback = hapticFeedback,
                    ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    // 「数据管理」分组（分界线 + 小标题 + 课程管理 + 切换课表）：
                    // 折叠态只保留今日/课程表/我的；若分组内某项正被选中则保留该项，
                    // 并随分界线/标题收拢上移贴近上方选项。
                    val dataGroupStart = if (isShiftMode) -1 else tabs.size - 2
                    val inDataGroup = !isShiftMode && index >= dataGroupStart
                    if (inDataGroup) {
                        val isFirstDataItem = index == dataGroupStart
                        if (isFirstDataItem) {
                            // 分界线 + 小标题：折叠全程只做淡入淡出，并始终占着自己的高度
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = TabletNavSideState.expandProgress.floatValue
                                    }
                                    .onGloballyPositioned {
                                        dataGroupHeightPx = it.size.height.toFloat()
                                    },
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top = 14.dp,
                                            bottom = 12.dp,
                                            start = 10.dp,
                                            end = 10.dp
                                        )
                                        .height(0.8.dp)
                                        .background(
                                            if (isLightTheme) Color.Black.copy(alpha = 0.08f)
                                            else Color.White.copy(alpha = 0.12f)
                                        )
                                )
                                Text(
                                    text = "数据管理",
                                    fontSize = 13.4.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        .copy(alpha = 0.6f),
                                    modifier = Modifier
                                        // 与条目图标同一条左缘
                                        .padding(start = 10.dp, top = 12.dp, bottom = 6.dp)
                                )
                            }
                        }
                        // 分组条目：选中时用反向平移补掉上方留白（展开时在原位）。
                        // 第二项还要再让开第一条数据项的行高，才能贴到「我的」下方。
                        val itemHeightPx = with(density) { 52.dp.toPx() }
                        val extraOffsetPx = (index - dataGroupStart) * itemHeightPx
                        Box(
                            modifier = Modifier.graphicsLayer {
                                val p = TabletNavSideState.expandProgress.floatValue
                                // 仅选中项在折叠时上移到「我的」下方；未选中不位移，只原地淡出
                                translationY =
                                    if (index == selectedTab) {
                                        -(1f - p) * (dataGroupHeightPx + extraOffsetPx)
                                    } else {
                                        0f
                                    }
                                alpha = if (index == selectedTab) 1f else p
                            }
                        ) {
                            TabletNavSideItem(
                                icon = icon,
                                label = label,
                                selected = index == selectedTab,
                                textColor = textColor,
                                showLabel = true,
                                labelFontWeight = FontWeight.Normal,
                                selectState = dragSelect,
                                // 折叠且未选中时不可见：既不进滑选命中（否则会高亮
                                // 一个看不见的项），也不响应点击
                                selectEnabled = TabletNavSideState.expanded || index == selectedTab,
                                onClick = {
                                    if (TabletNavSideState.expanded || index == selectedTab) {
                                        onTabSelected(index)
                                    }
                                },
                            )
                        }
                    } else {
                        TabletNavSideItem(
                            icon = icon,
                            label = label,
                            selected = index == selectedTab,
                            textColor = textColor,
                            showLabel = true,
                            selectState = dragSelect,
                            onClick = { onTabSelected(index) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部居中「今」
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (backdrop != null) {
                    // ColumnScope 内需显式走非扩展版
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showBackToNow,
                        enter = fadeIn(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(120)),
                    ) {
                        BackToNowFloatingButton(
                            onClick = onBackToNow,
                            backdrop = backdrop,
                            label = "今",
                            // 折叠紧凑防裁切，展开恢复饱满内边距
                            adaptiveToSidebarExpand = true,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * 侧栏条目：展开/折叠共用。
 * 图标与文字始终同黑白主色；[showLabel]=false 时不显示右侧文字。
 *
 * 选中遮罩只有一份：由 dropdownPanelEntry 画，跟手态与静止态共用它。
 * 早期版本另有一个直接绘制的静态遮罩，两层同形同位叠着纯属浪费，
 * 而且一个淡入淡出、一个即时切换，视觉上会打架。
 */
@Composable
private fun TabletNavSideItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    textColor: Color,
    showLabel: Boolean,
    labelFontWeight: FontWeight = FontWeight.Medium,
    /** 滑动点选状态；null = 不进选择区（「导航」折叠按钮在手势节点之外） */
    selectState: DropdownPanelDragSelectState? = null,
    /** 参与选择时是否可命中；折叠态的隐藏项传 false，避免高亮看不见的项 */
    selectEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    // 滑动点选：登记纵向区间 + 命中遮罩，与弹窗菜单共用 DropdownPanelDragSelect。
    // 条目自己的 clickable 保留：折叠态手势层不消费（fraction<0.98 直接放行），
    // 上移到「我的」下方的那颗数据项仍要点得到；展开态 down 被手势层消费，
    // clickable 的 awaitFirstDown(requireUnconsumed=true) 起不来，不会双重触发。
    val selectModifier = if (selectState != null) {
        Modifier.dropdownPanelEntry(
            enabled = selectEnabled,
            // 唯一一层遮罩：内缩 + 胶囊裁剪
            highlightPadding = PaddingValues(
                horizontal = TabletNavItemMaskHPadding,
                vertical = TabletNavItemMaskVPadding,
            ),
            highlightShape = ContinuousCapsule(),
            // 静止态亮在当前选中项；手指一动就改听命于命中项
            selected = selected,
            action = onClick,
            state = selectState,
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // 登记与遮罩挂在 clickable 之后：节点 bounds 不变，drawBehind 叠加顺序也对
            .then(selectModifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = TabletNavIconAlignStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier.size(TabletNavIconSize),
            )
            if (showLabel) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = labelFontWeight,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.graphicsLayer {
                        alpha = TabletNavSideState.expandProgress.floatValue
                    },
                )
            }
        }
    }
}
