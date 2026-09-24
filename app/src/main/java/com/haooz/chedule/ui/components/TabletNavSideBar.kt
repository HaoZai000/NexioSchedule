package com.haooz.chedule.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.RoundedCorner
import android.view.WindowManager
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
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
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
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import kotlin.math.roundToInt

/**
 * 平板左侧导航全局状态。
 * pad 只保留侧边态：true=展开（图标+文字），false=折叠（仅图标）。
 */
object TabletNavSideState {
    /** 展开=完整侧栏；折叠=仅图标轨 */
    var expanded by mutableStateOf(true)
    /**
     * 展开进度 0=折叠图标轨，1=完整侧栏。
     * 只在 layout/draw/graphicsLayer 读，禁止在组合期读——
     * 否则 CourseScheduleApp 整树会随动画每帧重组，平板展开/缩回直接掉帧。
     */
    val expandProgress = mutableFloatStateOf(1f)
    /** 二级页点选主 tab 后 MainActivity 待处理下标；-1 无 */
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
 * 条目内图标额外左偏：遮罩内边距 + 此值 + 图标半宽 = 44（相对遮罩左缘）。
 * 折叠时在 88 宽玻璃内居中；展开时中线相对遮罩不动。
 */
private val TabletNavIconAlignStart =
    TabletNavIconCenterX - TabletNavMaskPadding - TabletNavIconSize / 2f

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

/** 侧栏避让宽度 px。只在 layout/draw 阶段调用。 */
fun Density.tabletNavSideInsetPx(screenWidthDp: Int): Float {
    if (screenWidthDp < 600) return 0f
    val expandedWidth = screenWidthDp.dp * TabletNavSideWidthFraction
    val collapsedTotal = TabletNavSideInset + TabletNavIconRailWidth
    return androidx.compose.ui.unit.lerp(
        collapsedTotal,
        expandedWidth,
        TabletNavSideState.expandProgress.floatValue,
    ).toPx()
}

/**
 * 内容左避让侧栏。展开进度只在 measure 读，只失效 layout，不进组合。
 */
@Composable
fun tabletNavRailStartPadding(): Modifier {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    if (screenWidthDp < 600) return Modifier
    val density = LocalDensity.current
    val collapsedTotalPx = with(density) { (TabletNavSideInset + TabletNavIconRailWidth).toPx() }
    val expandedWidthPx = with(density) { (screenWidthDp.dp * TabletNavSideWidthFraction).toPx() }
    return TabletNavRailStartPaddingElement(collapsedTotalPx, expandedWidthPx)
}

private class TabletNavRailStartPaddingElement(
    private val collapsedTotalPx: Float,
    private val expandedWidthPx: Float,
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val padPx = androidx.compose.ui.util.lerp(
            collapsedTotalPx,
            expandedWidthPx,
            TabletNavSideState.expandProgress.floatValue,
        )
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
        if (other !is TabletNavRailStartPaddingElement) return false
        return collapsedTotalPx == other.collapsedTotalPx && expandedWidthPx == other.expandedWidthPx
    }

    override fun hashCode(): Int {
        var result = collapsedTotalPx.hashCode()
        result = 31 * result + expandedWidthPx.hashCode()
        return result
    }
}

/**
 * 侧栏玻璃面板宽度随展开进度在 layout 期插值，不进组合。
 */
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
 * 进度只在 measure 读。
 */
internal fun Modifier.tabletNavChromeTitleSlot(
    maxWPx: Float,
    collapsedTotalPx: Float,
    expandedWidthPx: Float,
    statusBarPx: Int,
    heightPx: Int,
    isLeftColumn: Boolean,
): Modifier = this then TabletNavChromeTitleSlotElement(
    maxWPx = maxWPx,
    collapsedTotalPx = collapsedTotalPx,
    expandedWidthPx = expandedWidthPx,
    statusBarPx = statusBarPx,
    heightPx = heightPx,
    isLeftColumn = isLeftColumn,
)

private class TabletNavChromeTitleSlotElement(
    private val maxWPx: Float,
    private val collapsedTotalPx: Float,
    private val expandedWidthPx: Float,
    private val statusBarPx: Int,
    private val heightPx: Int,
    private val isLeftColumn: Boolean,
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val sidePad = androidx.compose.ui.util.lerp(
            collapsedTotalPx,
            expandedWidthPx,
            TabletNavSideState.expandProgress.floatValue,
        )
        val contentW = (maxWPx - sidePad).coerceAtLeast(0f)
        val leftW = contentW * 0.42f
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
            isLeftColumn == other.isLeftColumn
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
 * 整块玻璃遮罩圆角：屏幕圆角 − 遮罩相对屏幕的间距。
 * 取不到系统圆角时退回 28.dp。
 */
@Composable
private fun rememberTabletNavMaskCorner(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenRadius = remember(context, density) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val px = wm.currentWindowMetrics.windowInsets
                .getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
            with(density) { px.toDp() }
        } catch (_: Exception) {
            0.dp
        }
    }
    val resolved = if (screenRadius > 0.dp) screenRadius else 28.dp
    return (resolved - 10.dp).coerceAtLeast(0.dp)
}

fun Context.startActivityNoNavAnim(intent: Intent) {
    startActivity(intent)
    if (this is Activity) {
        overridePendingTransition(0, 0)
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
        context.overridePendingTransition(0, 0)
    }
}

/**
 * pad 侧边导航：展开/折叠是同一套布局的连续变形。
 * 遮罩左缘间距不变、遮罩内边距恒为 8.dp，图标因此始终落在同一条竖直线上；
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
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarPadding > 0.dp) statusBarPadding else 36.dp
    val isLightTheme = !isAppDarkTheme()
    val textColor = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
    val containerColor =
        if (isLightTheme) Color(0xFFFFFFFF).copy(0.8f) else Color(0xFF242424).copy(0.8f)
    val solidContainer = if (isLightTheme) Color(0xFFF7F7F7) else Color(0xFF1C1C1E)
    val selectedBg =
        if (isLightTheme) Color.Black.copy(0.06f) else Color.White.copy(0.1f)
    val defaultEdgeLight = rememberDefaultEdgeLight()
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
        )
    }

    // 左缘间距恒定：不贴边，折叠只改遮罩宽度，栏体不平移
    val panelStartInset = TabletNavSideInset

    Box(modifier = modifier.fillMaxSize()) {
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
                        Modifier.background(solidContainer, ContinuousRoundedRectangle(sideCorner))
                    }
                )
                .padding(TabletNavMaskPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            TabletNavSideItem(
                icon = MiuixIcons.Regular.Sidebar,
                label = "导航",
                selected = false,
                textColor = textColor,
                selectedBg = selectedBg,
                showSelectedBg = false,
                showLabel = false,
                onClick = { TabletNavSideState.expanded = !TabletNavSideState.expanded },
            )

            // 页签之间更紧；与上方「导航」的间距仍由外层 spacedBy 控制
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    TabletNavSideItem(
                        icon = icon,
                        label = label,
                        selected = index == selectedTab,
                        textColor = textColor,
                        selectedBg = selectedBg,
                        showSelectedBg = true,
                        showLabel = true,
                        // 字重只跟布尔展开态，动画中途不触发文本重组
                        emphasized = index == selectedTab && TabletNavSideState.expanded,
                        onClick = { onTabSelected(index) },
                    )
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
 * 图标与文字始终同黑白主色；选中只靠底色。[showLabel]=false 时不显示右侧文字。
 */
@Composable
private fun TabletNavSideItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    textColor: Color,
    selectedBg: Color,
    showSelectedBg: Boolean,
    showLabel: Boolean,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        if (showSelectedBg && selected) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(ContinuousCapsule())
                    .background(selectedBg),
            )
        }

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
                    fontSize = 15.sp,
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
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
