package com.haooz.chedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import top.yukonga.miuix.kmp.interactive.DropdownPanelDragSelectState
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragSelect
import top.yukonga.miuix.kmp.interactive.dropdownPanelEntry
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.glass.BarFill
import com.haooz.chedule.ui.glass.BrightLargeTone
import com.haooz.chedule.ui.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.glass.applyLiveGlass
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
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

/** 顶栏糊层 draw 阶段跟踪侧栏伸缩用的稳定引用（勿在组合期调用） */
val tabletNavExpandSampleTrack: () -> Float = { TabletNavSideState.expandProgress.floatValue }

/** 图标中心相对遮罩左缘的固定位置（折叠态即 84/2，面板内居中） */
private val TabletNavIconCenterX = 42.dp

/** 图标尺寸，展开/折叠不变 */
private val TabletNavIconSize = 28.dp

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
    val expandedWidth = widthDp.dp * TabletNavSideWidthFraction
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
    backdrop: Backdrop?,
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
            // 倍率随展开进度反向补偿（展开 ×0.4 / 收起 ×0.7），使绝对半径接近，
            // 光晕大小不随伸缩跳变；读的是 draw 阶段进度，不触发重组。
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
    val sideShapeBlock: () -> Shape = remember(sideCorner) {
        { ContinuousRoundedRectangle(sideCorner) }
    }
    val sideGlassEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(sideGlassBlurDp, sideGlassOn, sideGlassStyle, sideCornerPx) {
            {
                if (sideGlassOn) {
                    blur(sideGlassBlurDp.dp.toPx())
                    runtimeShaderEffect(
                        key = LIVE_TABLET_NAV_GLASS_KEY,
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applyLiveGlass(
                            style = sideGlassStyle,
                            width = size.width,
                            height = size.height,
                            radiusPx = sideCornerPx,
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
    val screenWidth = widthDp.dp
    val collapsedPanelPx = with(density) { TabletNavIconRailWidth.toPx() }
    val expandedPanelPx = with(density) {
        (screenWidth * TabletNavSideWidthFraction - TabletNavSideInset).toPx()
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
                                shape = sideShapeBlock,
                                effects = sideGlassEffects,
                                highlight = null,
                                // 柔光自己画玻璃色与方向光；无材质档不跑 shader，必须留实色底。
                                onDrawSurface = if (sideGlassOn) null else sideSurface,
                            )
                    } else {
                        // 无玻璃分支没有 drawBackdrop 的 clipPath 兜底：
                        // 高光是矩形 drawRect，不自己裁会溢出圆角画成方光块。
                        // 底色走玻璃路径同一档半透明，不降级实心。
                        Modifier
                            .background(containerColor, ContinuousRoundedRectangle(sideCorner))
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
                label = ChromeStr.navigationLabel(),
                selected = false,
                textColor = textColor,
                showLabel = false,
                onClick = { TabletNavSideState.expanded = !TabletNavSideState.expanded },
            )

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
}

/**
 * 侧栏条目：展开/折叠共用。图标与文字同黑白主色，[showLabel]=false 时不显示文字。
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

/**
 * pad 主导航：只保留侧边态（展开=图标+文字，折叠=仅图标），直接转调 [TabletNavSideBar]。
 * 折叠按钮为 Miuix Sidebar 图标。
 */
@Composable
fun LiquidNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
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