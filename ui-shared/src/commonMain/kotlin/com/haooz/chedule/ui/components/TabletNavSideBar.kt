package com.haooz.chedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import top.yukonga.miuix.kmp.interactive.DropdownPanelDragSelectState
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragSelect
import top.yukonga.miuix.kmp.interactive.dropdownPanelEntry
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.liquidglass.DampedDragAnimation
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.softglass.glass.BarFill
import com.haooz.softglass.glass.BrightLargeTone
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.shape.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 平板侧栏的柔光 shader 缓存键：面板宽度逐帧变化，uniform 每帧按当前尺寸重灌。 */
private const val LIVE_TABLET_NAV_GLASS_KEY = "NexioSoftGlassLiveTabletNavSideBar"

/**
 * 顶栏糊层（`ProgressiveBlurTopBar` 的 sampleTrack）的采样跟踪信号，
 * 返回一个可比大小的单调量，供它做 draw 期"有没有动过"的判断。勿在组合期调用。
 *
 * 语义与 [tabletNavSamplingSignal] 同源，改那边记得同步这边。
 */
val tabletNavExpandSampleTrack: () -> Float = {
    TabletNavSideState.expandProgress.floatValue + TabletNavSideState.morphProgress.floatValue
}


/** 图标中心相对遮罩左缘的固定位置（折叠态即 84/2，面板内居中） */
private val TabletNavIconCenterX = 42.dp

/** 图标尺寸，展开/折叠不变（左上折叠钮、侧栏条目、右上收顶栏钮统一这一档） */
private val TabletNavIconSize = 26.dp

/** 遮罩内边距，展开/折叠始终保持 8 */
private val TabletNavMaskPadding = 8.dp

/** 遮罩底缘间距，保持不变 */
private val TabletNavBottomInset = 12.dp

/** 条目内图标额外左偏：与遮罩内边距、图标半宽共同把图标中心钉在 [TabletNavIconCenterX] */
private val TabletNavIconAlignStart =
    TabletNavIconCenterX - TabletNavMaskPadding - TabletNavIconSize / 2f

/**
 * 条目内选中遮罩的四周内缩；滑选高光必须用同一套值（见 [TabletNavSideItem] 的
 * dropdownPanelEntry），否则两个遮罩一满幅一内缩，叠一起对不齐。
 */
private val TabletNavItemMaskHPadding = 4.dp
private val TabletNavItemMaskVPadding = 2.dp

/** 侧栏条目行高：折叠态选中项的位移量也按它算 */
private val TabletNavSideItemHeight = 52.dp

/** 侧栏条目之间的行距（Column spacedBy） */
private val TabletNavItemRowGap = 4.dp

/** 顶栏 → 回侧栏：等面板长到底再弹，否则会撞在半路上 */
private const val LANDING_DELAY_MS = 240L

/** 侧栏条目里图标与文字的间距 */
private val TabletNavLabelGap = 12.dp

/** 顶栏胶囊 */
private val TabletNavTopBarHeight = 48.dp
private val TabletNavTopBarPad = 4.dp
private val TabletNavTopBarButtonWidth = 48.dp
private val TabletNavTopBarTabWidth = 78.dp
private val TabletNavTopBarIconSize = 26.dp
/** 胶囊格里只有文字，与侧栏同字号（所以不用缩放，直接是清晰的原字号） */
private val TabletNavTopBarLabelSize = 16.sp

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
fun Density.tabletNavSideInsetPx(widthDp: Int, screenHeightDp: Int = widthDp): Float {
    if (widthDp < 600) return 0f
    val expandedWidth = tabletNavExpandedWidthDp(widthDp.toFloat())
    val collapsedTotal = TabletNavSideInset + TabletNavIconRailWidth
    if (screenHeightDp > widthDp) {
        return collapsedTotal.toPx()
    }
    return androidx.compose.ui.unit.lerp(
        collapsedTotal,
        expandedWidth,
        TabletNavSideState.expandProgress.floatValue,
    ).toPx()
}

/**
 * 整块玻璃遮罩圆角：屏幕圆角 − 左缘间距；取不到系统圆角时退回 28.dp。
 *
 * @param screenCornerRadiusPx 屏幕圆角（px）；0 = 取不到 → 按 28.dp 基准。
 */
@Composable
private fun rememberTabletNavMaskCorner(screenCornerRadiusPx: Float): Dp {
    val density = LocalDensity.current
    val screenRadius = with(density) { screenCornerRadiusPx.toDp() }
    val resolved = if (screenRadius > 0.dp) screenRadius else 28.dp
    return (resolved - 10.dp).coerceAtLeast(0.dp)
}

/**
 * pad 侧边导航：展开/折叠是同一套布局的连续变形 —— 遮罩左缘间距与内边距恒定，
 * 折叠只收窄宽度并淡出文字，不整体平移。
 * 宽度/文字透明度只在 layout/draw 读进度，不进组合。
 */
@Composable
fun TabletNavSideBar(
    backdrop: GlassBackdrop?,
    /** 屏幕圆角（px）；宿主注入，0 = 取不到（退回 28.dp 基准） */
    screenCornerRadiusPx: Float = 0f,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isShiftMode: Boolean = false,
    showBackToNow: Boolean = false,
    onBackToNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TabletNavExpandAnimator()
    val isPortrait = screenHeightDp() > screenWidthDp()
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
    // 无材质档实色
    val containerColor =
        if (isLightTheme) Color(0xFFFFFFFF).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.8f))
        else Color(0xFF242424).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.8f))
    // 按压高光：跟手光晕，按哪亮哪。
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            // 基准取 min(节点尺寸, 150dp)：胶囊只有 50dp 高，不补倍率光晕只剩三分之一。
            // 侧栏按展开进度反向补偿，顶栏再乘 2.4 拉到 ≈126dp。
            radiusScale = {
                val expand = TabletNavSideState.expandProgress.floatValue.coerceIn(0f, 1f)
                val morph = TabletNavSideState.morphProgress.floatValue.coerceIn(0f, 1f)
                (0.7f - 0.3f * expand) * (1f + 1.4f * morph)
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

    // 底部落地回弹：展开落位那一刻让面板底缘往下顶一下再弹回。
    // 用显式动画而不是 spring 过冲 —— 展开方向 spring 只有 2~4% 过冲，
    // 被面板底部 12dp 留白吃掉以后肉眼就是 0（实测确认）。所以这里自己造一条。
    var landingJob by remember { mutableStateOf<Job?>(null) }
    // 只有「顶栏 → 回侧栏」这条会从 50dp 长到全高，才需要落地回弹；
    // 折叠轨 ↔ 展开侧栏高度不变，不能触发。
    val playLanding: () -> Unit = remember(animationScope) {
        {
            landingJob?.cancel()
            landingJob = animationScope.launch {
                delay(LANDING_DELAY_MS)
                TabletNavSideState.landBounce.floatValue = 0f
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(210, easing = LinearOutSlowInEasing),
                ) { v, _ -> TabletNavSideState.landBounce.floatValue = v }
                // 回程别再弹一次：阻尼 0.85 只是平稳收回，不产生第二下
                animate(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = spring(0.85f, Spring.StiffnessMediumLow),
                ) { v, _ -> TabletNavSideState.landBounce.floatValue = v }
            }
        }
    }
    // 玻璃遮罩圆角：屏幕圆角 − 左缘间距，不写死
    val sideCorner = rememberTabletNavMaskCorner(screenCornerRadiusPx)
    val density = LocalDensity.current

    // 侧栏是「大面板」：与对话框同走 BarFill + BrightLargeTone。面板宽度在 layout 期逐帧变，
    // uniform 必须在 draw 期读当前 size，不能把尺寸记进组合期状态。
    val sideGlassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = !isLightTheme,
        fill = BarFill,
        tone = BrightLargeTone,
    )
    val sideCornerPx = with(density) { sideCorner.toPx() }
    val sideGlassBlurDp = AppMaterialSettings.glassPanelBlurDp()
    val sideGlassOn = AppMaterialSettings.glassEffectsEnabled()
    val morphShape = remember(sideCornerPx) { TabletNavMorphShape(sideCornerPx) }
    val sideShapeBlock: () -> Shape = remember(morphShape) {
        { morphShape }
    }
    val sideGlassEffects: com.haooz.softglass.backdrop.GlassEffectScope.() -> Unit =
        remember(sideGlassBlurDp, sideGlassOn, sideGlassStyle, sideCornerPx) {
            {
                if (sideGlassOn) {
                    blur(sideGlassBlurDp.dp.toPx())
                    runtimeShaderEffect(
                        key = LIVE_TABLET_NAV_GLASS_KEY,
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applySoftGlass(
                            style = sideGlassStyle,
                            width = size.width,
                            height = size.height,
                            radiusPx = sideCornerPx +
                                (kotlin.math.min(size.width, size.height) / 2f - sideCornerPx) *
                                tabletNavSettleProgress(),
                        )
                    }
                }
            }
        }
    // 无材质档（以及无 backdrop 分支）用的实色底：按引用比较，必须稳定。
    val sideSurface: DrawScope.() -> Unit = remember(containerColor) {
        { drawRect(containerColor) }
    }
    val widthDp = screenWidthDp()
    val collapsedPanelPx = with(density) { TabletNavIconRailWidth.toPx() }
    val expandedPanelPx = with(density) {
        (tabletNavExpandedWidthDp(widthDp.toFloat()) - TabletNavSideInset).toPx()
    }
    val tabs = if (isShiftMode) {
        listOf(
            ChromeStr.shiftSchedule() to MiuixIcons.Months,
            CommonStr.settings() to MiuixIcons.Demibold.Settings,
        )
    } else {
        listOf(
            CommonStr.today() to MiuixIcons.Album,
            ChromeStr.schedule() to MiuixIcons.Months,
            CommonStr.settings() to MiuixIcons.Demibold.Settings,
            ChromeStr.courseManage() to MiuixIcons.Backup,
            ChromeStr.switchSchedule() to MiuixIcons.ConvertFile,
        )
    }

    // 顶栏形态只放主 tab：数据管理分组留在侧栏里，不收进胶囊。
    // 下标与 tabs 前缀一一对齐，所以 onTabSelected(index) 可以直接复用。
    val topBarTabs = if (isShiftMode) {
        listOf(
            ChromeStr.shiftSchedule() to MiuixIcons.Months,
            CommonStr.settings() to MiuixIcons.Demibold.Settings,
        )
    } else {
        listOf(
            CommonStr.today() to MiuixIcons.Album,
            ChromeStr.schedule() to MiuixIcons.Months,
            CommonStr.settings() to MiuixIcons.Demibold.Settings,
        )
    }
    // 胶囊宽度 = 内边距 + 最左圆钮 + 每格等宽 × tab 数，与手机底栏同一套算法
    val capsuleWidthPx = with(density) {
        (TabletNavTopBarPad * 2 + TabletNavTopBarButtonWidth +
            TabletNavTopBarTabWidth * topBarTabs.size).toPx()
    }
    val capsuleHeightPx = with(density) { TabletNavTopBarHeight.toPx() }

    // 同一个面板容器在「侧栏 ↔ 顶部胶囊」之间连续变形
    val topBarMode = TabletNavSideState.topBarMode
    val morphMotion = remember(animationScope, tabs.size) {
        TabletNavMorphMotion(
            animationScope,
            if (topBarMode) 1f else 0f,
            tabs.size,
            onFrame = { TabletNavSideState.morphProgress.floatValue = it },
        )
    }
    LaunchedEffect(topBarMode) {
        val target = if (topBarMode) 1f else 0f
        if (morphMotion.lastTarget != target) morphMotion.to(target)
    }

    // 左缘间距恒定：不贴边，折叠只改遮罩宽度，栏体不平移
    val panelStartInset = TabletNavSideInset

    // 「数据管理」区块实测高度：折叠后选中项靠反向平移抵消它，贴到上方选项
    var dataGroupHeightPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val hostHeightPx = with(density) { maxHeight.toPx() }
        val hostWidthPx = with(density) { maxWidth.toPx() }
        val panelStartInsetPx = with(density) { panelStartInset.toPx() }
        val topPaddingPx = with(density) { topPadding.toPx() }
        val sideTopPx = with(density) { (topPadding + 2.dp).toPx() }
        val sideBottomInsetPx = with(density) { TabletNavBottomInset.toPx() }
        val maskPadPx = with(density) { TabletNavMaskPadding.toPx() }
        // 侧栏内容层自己那套高度：恒定，不跟 morph 变 —— 变形期间不重排
        val sideContentHeightPx = hostHeightPx - sideTopPx - sideBottomInsetPx
        val sideWidthProvider: () -> Float = {
            lerp(
                collapsedPanelPx,
                expandedPanelPx,
                TabletNavSideState.expandProgress.floatValue,
            )
        }
        val topBarPadPx = with(density) { TabletNavTopBarPad.toPx() }
        val topBarButtonWidthPx = with(density) { TabletNavTopBarButtonWidth.toPx() }
        val topBarTabWidthPx = with(density) { TabletNavTopBarTabWidth.toPx() }
        val topBarIconPx = with(density) { TabletNavTopBarIconSize.toPx() }
        val sideItemHeightPx = with(density) { TabletNavSideItemHeight.toPx() }
        val sideItemGapPx = with(density) { TabletNavItemRowGap.toPx() }
        // 侧栏态右上那颗「收成顶栏」：52dp 圆钮、与左上同档图标
        val buttonFromPx = with(density) { TabletNavSideItemHeight.toPx() }
        val buttonIconFromPx = with(density) { TabletNavIconSize.toPx() }
        val sideItemWidthProvider: () -> Float = { sideWidthProvider() - maskPadPx * 2f }
        // 第 index 个 tab 在胶囊里的左缘（相对面板左缘）
        fun topBarSlotLeftPx(index: Int): Float =
            topBarPadPx + topBarButtonWidthPx + index * topBarTabWidthPx

        // 竖屏展开压暗：淡入淡出；折叠时不占命中。离屏合成，不进玻璃模糊采样
        if (isPortrait) {
            AnimatedVisibility(
                visible = TabletNavSideState.expanded && !topBarMode,
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
        // 面板容器：一块玻璃、一个控件，四边界在 layout 期连续变形。
        // 条目自己按进度滑进胶囊格，材质与裁剪仍然只有这一层。
        Box(
            modifier = Modifier
                .tabletNavMorphPanel(
                    sideInsetPx = panelStartInsetPx,
                    sideTopPx = sideTopPx,
                    capsuleTopPx = topPaddingPx,
                    sideBottomInsetPx = sideBottomInsetPx,
                    sideWidthPx = sideWidthProvider,
                    capsuleWidthPx = capsuleWidthPx,
                    capsuleHeightPx = capsuleHeightPx,
                    morphX = { TabletNavSideState.morphProgress.floatValue },
                    morphY = { morphMotion.y },
                )
                .then(
                    if (backdrop != null) {
                        Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = sideShapeBlock,
                                effects = sideGlassEffects,
                                // 柔光自己画玻璃色与方向光；无材质档不跑 shader，必须留实色底。
                                onDrawSurface = if (sideGlassOn) null else sideSurface,
                            )
                    } else {
                        // 无玻璃分支没有 drawBackdrop 的 clipPath 兜底：
                        // 高光是矩形 drawRect，不自己裁会溢出圆角画成方光块。
                        // 底色走玻璃路径同一档半透明，不降级实心。
                        Modifier
                            .background(containerColor, morphShape)
                            .clip(morphShape)
                    }
                )
                // 高光画在材质之上、条目内容之下（内容在 drawContent 里更靠内，后画）
                .then(interactiveHighlight.modifier)
                // 触发挂整条面板：手指落在任意条目上都有光晕。
                // observeConsumed=true，条目的 clickable 消费事件不影响它跟手
                .then(interactiveHighlight.gestureModifier)
        ) {
            // 顶栏的滑块与手势：整段从手机底栏搬过来（弹簧双边界 + 拖尾 + 跟手吸附），
            // 坐标换成胶囊自己的，前面多让出一颗圆钮的宽度。
            if (topBarMode) {
                TabletNavTopBarInteraction(
                    padPx = topBarPadPx,
                    leadingPx = topBarButtonWidthPx,
                    tabWidthPx = topBarTabWidthPx,
                    tabsCount = topBarTabs.size,
                    pressProgress = { interactiveHighlight.pressProgress },
                    selectedTabIndex = { selectedTab },
                    onTabSelected = onTabSelected,
                )
            }

            // 侧栏排版：固定按「展开/折叠」这一套尺寸布局，不跟 morph 变 ——
            // 变形期间这棵树零重排；条目自己按进度连到胶囊槽位，其余东西原地淡出。
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .tabletNavFixedSize(sideWidthProvider) { sideContentHeightPx }
                    .padding(TabletNavMaskPadding),
                verticalArrangement = Arrangement.spacedBy(TabletNavItemRowGap),
                horizontalAlignment = Alignment.Start,
            ) {
                // 左上「侧栏展开/折叠」：只管侧栏自己。进了顶栏形态就交给右上那颗
                // （它一路滑到胶囊最左边成为「回侧栏」），这颗原地淡出。
                // ⚠ 这里**不能**挂 blockTouchInput：它按 Initial pass 消费，而这一行正好压在
                // 胶囊第一格上，会把顶栏手势整条吃掉（表现为胶囊点不动）。
                // 条目自己的 clickable 已经在顶栏态禁用，事件会正常落到胶囊手势层。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TabletNavSideItemHeight)
                        .graphicsLayer {
                            alpha = 1f - TabletNavSideState.morphProgress.floatValue
                        }
                ) {
                    TabletNavSideItem(
                        icon = MiuixIcons.Regular.Sidebar,
                        label = ChromeStr.navigationLabel(),
                        selected = false,
                        textColor = textColor,
                        showLabel = false,
                        onClick = {
                            // 只改折叠/展开：高度没变，不该有落地回弹
                            TabletNavSideState.expanded = !TabletNavSideState.expanded
                        },
                    )
                }

            // 滑动点选挂这一层：正好只覆盖页签，「导航」折叠按钮与底部「今」在
            // 手势节点之外，各自 clickable 不会被 down.consume() 波及。
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
                        // 顶栏形态返回 0：条目已经排成胶囊槽位，再让手势层吃掉 down 就点不动了。
                        fraction = {
                            if (TabletNavSideState.topBarMode) 0f
                            else TabletNavSideState.expandProgress.floatValue
                        },
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
                    // 本条目在侧栏里的行原点（相对 Column 内容区）：决定它滑向胶囊槽位的路程
                    val itemOriginY =
                        (if (inDataGroup) dataGroupHeightPx else 0f) + index * sideItemHeightPx
                    // 顶栏只露前几个；后面那些照样滑到自己的槽位（动画一致性），只是淡出
                    val morphItem = Modifier.tabletNavMorphItem(
                        sideWidthPx = sideItemWidthProvider,
                        sideHeightPx = sideItemHeightPx,
                        progress = { tabletNavItemProgress(morphMotion, index) },
                        targetLeftPx = topBarSlotLeftPx(index) - maskPadPx,
                        // 胶囊贴面板顶，条目撑满 56 高 ⇒ 目标顶 = -内边距 -(上面那颗 + 行距 + 分组标题)
                        targetTopPx = -(maskPadPx + sideItemHeightPx + sideItemGapPx + itemOriginY),
                        targetWidthPx = topBarTabWidthPx,
                        targetHeightPx = capsuleHeightPx,
                    )
                    if (inDataGroup) {
                        val isFirstDataItem = index == dataGroupStart
                        if (isFirstDataItem) {
                            // 分界线 + 小标题：折叠全程只做淡入淡出，并始终占着自己的高度
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val expand = TabletNavSideState.expandProgress.floatValue
                                        val morph = TabletNavSideState.morphProgress.floatValue
                                        alpha = (expand * (1f - morph)).coerceIn(0f, 1f)
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
                                    text = ChromeStr.dataManagement(),
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
                        val extraOffsetPx = (index - dataGroupStart) * sideItemHeightPx
                        Box(
                            modifier = morphItem.then(
                                if (topBarMode && index >= topBarTabs.size) {
                                    // 顶栏放不下的项：已经滑出胶囊，别再留命中
                                    Modifier.blockTouchInput()
                                } else {
                                    Modifier
                                }
                            )
                        ) {
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    val p = TabletNavSideState.expandProgress.floatValue
                                    val morph = TabletNavSideState.morphProgress.floatValue
                                    // 仅选中项在折叠时上移到「我的」下方；未选中不位移，只原地淡出
                                    translationY =
                                        if (index == selectedTab) {
                                            -(1f - p) * (dataGroupHeightPx + extraOffsetPx)
                                        } else {
                                            0f
                                        }
                                    // 顶栏只留主 tab：这两项照样滑到自己的槽位，但一路淡出
                                    alpha = (if (index == selectedTab) 1f else p) * (1f - morph)
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
                                    progress = { tabletNavItemProgress(morphMotion, index) },
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
                        }
                    } else {
                        Box(modifier = morphItem) {
                            TabletNavSideItem(
                                icon = icon,
                                label = label,
                                selected = index == selectedTab,
                                textColor = textColor,
                                showLabel = true,
                                selectState = dragSelect,
                                progress = { tabletNavItemProgress(morphMotion, index) },
                                onClick = { onTabSelected(index) },
                            )
                        }
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
                            label = ChromeStr.todayBadge(),
                            // 折叠紧凑防裁切，展开恢复饱满内边距
                            adaptiveToSidebarExpand = true,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // 「收成顶栏 / 回侧栏」：侧栏里钉在**当前**面板右缘（展开是整宽、折叠是图标轨），
        // 所以收窄时它跟着右缘一起左移、同时淡出，而不是原地消失；
        // 进顶栏形态则一路滑到胶囊最左边那颗。
        Box(
            modifier = Modifier
                // 明确压在面板之上：胶囊态里 TabletNavTopBarInteraction 的手势层带
                // zIndex(2f)（只作用于面板内部），这颗是外层兄弟，给 1 保证
                // 最左「回侧栏」圆钮的命中优先级，绘制顺序与原来一致（本就最后画）。
                .zIndex(1f)
                .offset {
                    // 横移吃整条弹簧（跟胶囊一起冲出去回弹），纵向提前收尾
                    val slide = tabletNavSlideProgress()
                    val settle = tabletNavSettleProgress()
                    // 起点跟面板右缘走：sideWidthProvider 里已经叠了展开弹簧，
                    // 收窄时按钮随右缘内收 —— 尺寸不变，只改这一条的 X
                    val fromX = panelStartInsetPx + sideWidthProvider() - maskPadPx - buttonFromPx
                    val fromY = sideTopPx + maskPadPx + (sideItemHeightPx - buttonFromPx) / 2f
                    val toX = (hostWidthPx - capsuleWidthPx) / 2f + topBarPadPx
                    val toY = topPaddingPx
                    IntOffset(
                        (fromX + (toX - fromX) * slide).roundToInt(),
                        (fromY + (toY - fromY) * settle).roundToInt(),
                    )
                }
                .tabletNavMorphBoxSize(buttonFromPx, buttonFromPx, topBarButtonWidthPx, capsuleHeightPx)
                .clip(ContinuousCapsule())
                .graphicsLayer {
                    // 折叠轨里整颗隐掉（面板右缘缩走了）；顶栏形态下它就是最左那颗，常亮
                    val expand = TabletNavSideState.expandProgress.floatValue.coerceIn(0f, 1f)
                    val morph = TabletNavSideState.morphProgress.floatValue.coerceIn(0f, 1f)
                    alpha = expand + (1f - expand) * morph
                }
                .then(
                    if (topBarMode) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            // 回侧栏：落完整侧栏，不是折叠轨
                            TabletNavSideState.expanded = true
                            TabletNavSideState.topBarMode = false
                            // 面板长到底之后再顶一下（高度本身还在飞，等它落位再弹）
                            playLanding()
                        }
                    } else if (TabletNavSideState.expanded) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { TabletNavSideState.topBarMode = true }
                    } else {
                        // 折叠态这颗按钮随面板右缘滑进 84dp 图标轨，几何上正好压在左上
                        // 「展开/折叠」按钮的图标上（fromX = inset + 84 - 8 - 52 = 36dp）。
                        // alpha 虽为 0，但只要挂着 pointerInput 它就在命中链里，左上角
                        // 就点不动 —— 症状即「收起以后展不开」。折叠态它没有任何功能，
                        // 干脆一个指针节点都不挂：Compose 的 hit path 只收带
                        // PointerInputModifier 的节点，事件会直接落到下面左上那颗按钮。
                        // ⚠ 别改回 blockTouchInput + active 谓词：「不消费」拦不住这层
                        // Initial-pass 节点，实测仍会把展开点击吃掉。
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.tabletNavMorphSize(buttonIconFromPx, topBarIconPx)) {
                // 两个图标叠在同一个坑里交叉淡入：侧栏态露「折叠为顶栏」，顶栏态露「展开为侧栏」
                Icon(
                    imageVector = TabletNavIconCollapseTopBar,
                    contentDescription = if (topBarMode) null else ChromeStr.navigationLabel(),
                    tint = textColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - TabletNavSideState.morphProgress.floatValue
                                .coerceIn(0f, 1f)
                        },
                )
                Icon(
                    imageVector = TabletNavIconExpandSidebar,
                    contentDescription = if (topBarMode) ChromeStr.navigationLabel() else null,
                    tint = textColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = TabletNavSideState.morphProgress.floatValue
                                .coerceIn(0f, 1f)
                        },
                )
            }
        }
    }
}

/**
 * 顶栏胶囊的滑块与手势：**整段照抄手机底栏**（[LiquidBottomTabs]）——
 * 左右两条边界各一条弹簧、位移途中宽度先撑开再收回的橡皮筋感、拖尾一阶低通、
 * 跟手拖动、松手吸附最近格。差别只有两点：坐标换成胶囊自己的，前面让出一颗圆钮。
 *
 * 视觉条目仍然走 Column 里那批按 morph 滑过来的节点，这里只管「滑块 + 命中」，
 * 所以条目不用为了手势去凑 Row 的流式布局。
 */
@Composable
private fun TabletNavTopBarInteraction(
    padPx: Float,
    leadingPx: Float,
    tabWidthPx: Float,
    tabsCount: Int,
    /** 面板那份按压进度：滑块要在按下时四周各缩 2dp（手机底栏同款） */
    pressProgress: () -> Float,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val isLightTheme = !isAppDarkTheme()
    val selectorInsetPx = with(density) { CapsuleSlider.Inset.toPx() }
    val selectorHeightPx = with(density) { (TabletNavTopBarHeight - CapsuleSlider.VerticalInset).roundToPx() }
    val stripLeftPx = padPx + leadingPx
    val stripRightPx = stripLeftPx + tabsCount * tabWidthPx
    val selectorWidthPx = tabWidthPx - selectorInsetPx * 2f
    val maxIndex = (tabsCount - 1).toFloat()
    val trailMaxPx = with(density) { CapsuleSlider.TrailMax.toPx() }
    // 快甩阈值 dp/秒 → px/秒
    val flingVelocityThresholdPxPerSec =
        with(density) { CapsuleSlider.FlingVelocityDpPerSecond.toPx() }

    fun slotLeft(index: Float): Float = stripLeftPx + index * tabWidthPx + selectorInsetPx

    var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
    val latestSelectedTabIndex by rememberUpdatedState(selectedTabIndex)

    val dampedDragAnimation = remember(animationScope, tabsCount) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedTabIndex().toFloat(),
            valueRange = 0f..maxIndex,
            visibilityThreshold = CapsuleSlider.DampedVisibilityThreshold,
            initialScale = 1f,
            pressedScale = CapsuleSlider.PressScale,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )
    }
    val capsule = remember(
        animationScope, tabsCount, tabWidthPx, stripLeftPx, stripRightPx, selectorWidthPx
    ) {
        CapsuleSelector(
            scope = animationScope,
            widthPx = selectorWidthPx,
            startLeftPx = slotLeft(currentIndex.toFloat()),
            // 不许滑出胶囊内边
            leftLimit = { stripLeftPx..(stripRightPx - selectorWidthPx) },
        )
    }
    val settleToSlot: (Int) -> Unit = { index ->
        val target = slotLeft(index.toFloat())
        capsule.settleTo(target, forward = target > capsule.leftPx)
    }
    // 胶囊视觉中心落在哪一格：比逻辑 index 准，半格拖拽也对
    val indexAtCapsule: () -> Int = {
        ((capsule.centerPx - stripLeftPx) / tabWidthPx - 0.5f)
            .roundToInt().coerceIn(0, tabsCount - 1)
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { latestSelectedTabIndex() }
            .collectLatest { index -> if (index != currentIndex) currentIndex = index }
    }
    LaunchedEffect(dampedDragAnimation, capsule) {
        snapshotFlow { currentIndex }
            .drop(1)
            .collectLatest { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                settleToSlot(index)
                onTabSelected(index)
            }
    }

    // 选中滑块：位置与宽度都在 layout 期读，组合期不碰任何高频值。
    // 按下时四周各缩 2dp —— 与手机底栏滑块完全一致（照抄 LiquidBottomTabs）。
    Box(
        modifier = Modifier.capsuleSliderPill(
            selector = capsule,
            heightPx = selectorHeightPx,
            pressProgress = { pressProgress() },
            // 胶囊快成形了才显形，别在侧栏形态里闪一下
            visibility = {
                val p = TabletNavSideState.morphProgress.floatValue.coerceIn(0f, 1f)
                ((p - 0.5f) * 2f).coerceIn(0f, 1f)
            },
        ),
    )

    // 统一手势层：按下某格 / 拖胶囊 / 松手吸附。最左那颗圆钮自己处理点击。
    // zIndex 抬到条目之上：条目虽然禁了 clickable，但不该让命中顺序靠猜。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2f)
            .pointerInput(
                capsule, tabsCount, tabWidthPx, stripLeftPx, stripRightPx,
                flingVelocityThresholdPxPerSec,
            ) {
                val touchSlop = viewConfiguration.touchSlop
                val flingVelocityThreshold = flingVelocityThresholdPxPerSec
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downX = down.position.x
                    if (downX < stripLeftPx) return@awaitEachGesture
                    val startIndex = currentIndex.coerceIn(0, tabsCount - 1)
                    val fling = CapsuleFlingTracker(down.uptimeMillis, downX)

                    // 按在滑块上才是拖；按在别的格上直接选中（胶囊就是拖动把手）
                    val capsuleHalf = tabWidthPx * CapsuleSlider.GrabHalfWidthRatio
                    var dragging = abs(downX - capsule.centerPx) <= capsuleHalf
                    var pressedTab = -1
                    var dragAnchorLeft = capsule.leftPx
                    var dragAnchorX = downX
                    var trailVelocity = 0f

                    if (dragging) {
                        dampedDragAnimation.press()
                    } else {
                        pressedTab = floor((downX - stripLeftPx) / tabWidthPx)
                            .toInt()
                            .coerceIn(0, tabsCount - 1)
                        dampedDragAnimation.animateToValueKeepingPress(pressedTab.toFloat())
                        settleToSlot(pressedTab)
                    }

                    var lastX = downX
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            val positionalTarget =
                                if (dragging) indexAtCapsule()
                                else pressedTab.coerceIn(0, tabsCount - 1)
                            // 顶栏胶囊为 LTR 布局
                            val flingDir = if (dragging) {
                                fling.flingDirection(flingVelocityThreshold, touchSlop)
                            } else 0
                            val target = CapsuleSlider.flingTarget(
                                startIndex = startIndex,
                                positionalTarget = positionalTarget,
                                indexDirection = flingDir,
                                lastIndex = tabsCount - 1,
                            )
                            currentIndex = target
                            dampedDragAnimation.animateToValue(target.toFloat())
                            settleToSlot(target)
                            dampedDragAnimation.release()
                            onTabSelected(target)
                            break
                        }

                        val dx = change.position.x - lastX
                        val totalDx = change.position.x - downX
                        fling.onMove(change.uptimeMillis, change.position, downX)

                        // 点完继续滑就接管成拖动；锚点取当前手指位置，吃掉过 slop 那一段
                        if (!dragging && abs(totalDx) > touchSlop) {
                            dragging = true
                            dampedDragAnimation.press()
                            dragAnchorLeft = capsule.leftPx
                            dragAnchorX = change.position.x
                            trailVelocity = 0f
                            lastX = change.position.x
                        }

                        if (dragging) {
                            trailVelocity = trailVelocity * CapsuleSlider.TrailSmoothing +
                                dx * (1f - CapsuleSlider.TrailSmoothing)
                            val trail = (abs(trailVelocity) * CapsuleSlider.TrailGain).coerceAtMost(trailMaxPx)
                            val dir = when {
                                trailVelocity > 0.5f -> 1
                                trailVelocity < -0.5f -> -1
                                else -> 0
                            }
                            capsule.onDrag(
                                leftPx = dragAnchorLeft + (change.position.x - dragAnchorX),
                                trailPx = trail,
                                dir = dir,
                            )
                            if (abs(dx) > 0.01f) {
                                dampedDragAnimation.updateValue(
                                    (dampedDragAnimation.targetValue + dx / tabWidthPx)
                                        .coerceIn(0f, maxIndex)
                                )
                            }
                        }

                        lastX = change.position.x
                        change.consume()
                    }
                }
            },
    )
}

/**
 * 淡出后的层不再接命中：避免隔空点到看不见的按钮。Initial pass 先于内部 clickable。
 *
 * 只给「已经滑出胶囊的条目」这类纯死区用；**不要**拿它挡左上折叠按钮 —— 见
 * 右上那颗按钮的 else 分支注释。
 */
private fun Modifier.blockTouchInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/**
 * 侧栏条目 = 顶栏胶囊格：同一个节点按 morph 进度连续换形状，不是淡出一个淡入另一个。
 * 宽高由外层 [tabletNavMorphItem] 给；内部坐标与文字缩放在 layout 期插值，不进组合。
 * 选中遮罩只有一份（由 dropdownPanelEntry 画，跟手态与静止态共用）。
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
    /** 本条目自己的 morph 进度；默认不参与错落（左上那颗折叠按钮） */
    progress: () -> Float = { tabletNavSettleProgress() },
    onClick: () -> Unit,
) {
    // 登记纵向区间 + 命中遮罩，与弹窗菜单共用 DropdownPanelDragSelect。
    // clickable 保留：折叠态手势层放行故仍可点；展开态 down 已被消费，不会双重触发。
    val selectModifier = if (selectState != null) {
        Modifier.dropdownPanelEntry(
            enabled = selectEnabled,
            // 唯一一层遮罩：内缩 + 胶囊裁剪
            highlightPadding = PaddingValues(
                horizontal = TabletNavItemMaskHPadding,
                vertical = TabletNavItemMaskVPadding,
            ),
            highlightShape = ContinuousCapsule(),
            // 静止态亮在当前选中项；手指一动就改听命于命中项。
            // 顶栏形态下选中由胶囊滑块整体承担，条目自己不再画，否则两层高亮叠一起。
            selected = selected && !TabletNavSideState.topBarMode,
            action = onClick,
            state = selectState,
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 尺寸由外层 tabletNavMorphItem 的 Constraints 决定，这里只填满。
            // 顶栏形态下点击交给胶囊那一层统一手势（滑块/拖动/吸附），条目自己不抢。
            .clickable(
                enabled = !TabletNavSideState.topBarMode,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // 登记与遮罩挂在 clickable 之后：节点 bounds 不变，drawBehind 叠加顺序也对
            .then(selectModifier),
    ) {
        TabletNavItemContent(
            icon = icon,
            label = label,
            textColor = textColor,
            showLabel = showLabel,
            labelFontWeight = labelFontWeight,
            progress = progress,
        )
    }
}

/**
 * 条目内部：图标原地淡出、占位收到 0，文字从图标右侧走到格子正中。
 * 一切都在 layout 期按进度插值。
 */
@Composable
private fun TabletNavItemContent(
    icon: ImageVector,
    label: String,
    textColor: Color,
    showLabel: Boolean,
    labelFontWeight: FontWeight,
    progress: () -> Float,
) {
    val density = LocalDensity.current
    val startPadPx = with(density) { TabletNavIconAlignStart.toPx() }
    val iconFromPx = with(density) { TabletNavIconSize.toPx() }
    val gapFromPx = with(density) { TabletNavLabelGap.toPx() }
    val labelFromSp = 16f
    // 顶栏格子里只有文字（用户口径：不要图标），字号略收
    val labelScale = TabletNavTopBarLabelSize.value / labelFromSp

    Layout(
        content = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 顶栏格子里不要图标：**不缩小**，只淡出 + 让出占位
                        alpha = 1f - progress()
                    },
            )
            if (showLabel) {
                Text(
                    text = label,
                    fontSize = labelFromSp.sp,
                    fontWeight = labelFontWeight,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.graphicsLayer {
                        // 与 layout 期同一个公式，保证缩放后左上角对得上摆放点
                        val p = progress()
                        val s = 1f + (labelScale - 1f) * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                        // 折叠轨里文字跟着收起；顶栏形态必须可见（那时展开进度可能是 0）
                        val expand = TabletNavSideState.expandProgress.floatValue
                        alpha = if (expand > p) expand else p
                    },
                )
            }
        },
    ) { measurables, constraints ->
        // 横排（文字归位）提前到 0.8 完成；每条再按自己的延迟错开
        val p = progress()
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // 图标不参与缩放：尺寸恒为侧栏那一档，只靠 alpha 淡出、位置原地不动
        val iconSize = iconFromPx.roundToInt().coerceAtLeast(1)
        val iconPlaceable = measurables[0].measure(Constraints.fixed(iconSize, iconSize))
        val textPlaceable = measurables.getOrNull(1)?.measure(Constraints())
        val scale = 1f + (labelScale - 1f) * p
        val textW = (textPlaceable?.width ?: 0) * scale
        val textH = (textPlaceable?.height ?: 0) * scale

        // 侧栏：图标在左、文字在右，整体垂直居中。
        // 图标本身不缩，但它**占的宽度**随进度收到 0，文字跟着往左吃掉这段空间。
        val iconSlot = iconSize * (1f - p)
        val gapSlot = gapFromPx * (1f - p)
        val sideIconX = startPadPx
        val sideIconY = (h - iconSize) / 2f
        val textXFree = startPadPx + iconSlot + gapSlot
        val sideTextY = (h - textH) / 2f
        // 顶栏：文字落在格子正中（与手机底栏同一套格子几何）
        val topTextX = (w - textW) / 2f
        val topTextY = (h - textH) / 2f
        // 先按「占位收窄」往左贴，再被拉向格子中心
        val textX = textXFree + (topTextX - textXFree) * p

        layout(w.roundToInt(), h.roundToInt()) {
            // 图标原地不动，只淡出（尺寸与位置都不随进度变）
            iconPlaceable.place(sideIconX.roundToInt(), sideIconY.roundToInt())
            textPlaceable?.place(
                textX.roundToInt(),
                (sideTextY + (topTextY - sideTextY) * p).roundToInt(),
            )
        }
    }
}

/**
 * pad 主导航：只保留侧边态（展开=图标+文字，折叠=仅图标），直接转调 [TabletNavSideBar]。
 * 折叠按钮为 Miuix Sidebar 图标。
 */
@Composable
fun LiquidNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: GlassBackdrop,
    isShiftMode: Boolean,
    /** 屏幕圆角（px）；宿主注入，0 = 取不到（退回 28.dp 基准） */
    screenCornerRadiusPx: Float = 0f,
    modifier: Modifier = Modifier,
    onSidebarExpandedChange: ((Boolean) -> Unit)? = null,
    showBackToNow: Boolean = false,
    onBackToNow: () -> Unit = {},
) {
    var liquidSelectedTab by remember { mutableIntStateOf(selectedTab) }
    LaunchedEffect(selectedTab) { liquidSelectedTab = selectedTab }

    val sidebarExpanded = TabletNavSideState.expanded
    LaunchedEffect(sidebarExpanded) { onSidebarExpandedChange?.invoke(sidebarExpanded) }

    TabletNavSideBar(
        backdrop = backdrop,
        screenCornerRadiusPx = screenCornerRadiusPx,
        selectedTab = liquidSelectedTab,
        onTabSelected = onTabSelected,
        isShiftMode = isShiftMode,
        showBackToNow = showBackToNow,
        onBackToNow = onBackToNow,
        modifier = modifier,
    )
}
