package com.haooz.chedule.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar

/**
 * 平板左侧导航全局状态。
 * pad 只保留侧边态：true=展开（图标+文字），false=折叠（仅图标）。
 */
object TabletNavSideState {
    /** 展开=完整侧栏；折叠=仅图标轨 */
    var expanded by mutableStateOf(true)
    /** 二级页点选主 tab 后 MainActivity 待处理下标；-1 无 */
    var pendingMainTab by mutableStateOf(-1)
}

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
private val TabletNavSideInset = 12.dp
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

/** 展开进度 0=折叠图标轨，1=完整侧栏；同一结构连续插值 */
@Composable
fun rememberTabletNavExpandProgress(): Float {
    return animateFloatAsState(
        targetValue = if (TabletNavSideState.expanded) 1f else 0f,
        animationSpec = TabletNavExpandSpec,
        label = "tabletNavExpand",
    ).value
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

/** pad 侧栏占位宽度：左缘间距 + 遮罩宽度（折叠 88 ↔ 展开屏宽*比例）；手机=0 */
@Composable
fun tabletNavSideStartPadding(): Dp {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    if (!isTablet) return 0.dp
    val progress = rememberTabletNavExpandProgress()
    val expandedWidth = LocalConfiguration.current.screenWidthDp.dp * TabletNavSideWidthFraction
    val collapsedTotal = TabletNavSideInset + TabletNavIconRailWidth
    return lerp(collapsedTotal, expandedWidth, progress)
}

/**
 * pad 侧边导航：展开/折叠是同一套布局的连续变形。
 * 遮罩左缘间距不变、遮罩内边距恒为 16.dp，图标因此始终落在同一条竖直线上；
 * 折叠只收窄遮罩宽度并淡出文字，不整体平移。
 */
@Composable
fun TabletNavSideBar(
    backdrop: Backdrop?,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isShiftMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val expandProgress = rememberTabletNavExpandProgress()
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
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
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
        )
    }

    // 左缘间距恒定：不贴边，折叠只改遮罩宽度，栏体不平移
    val panelStartInset = TabletNavSideInset
    val expandedWidth = screenWidth * TabletNavSideWidthFraction
    val collapsedTotal = TabletNavSideInset + TabletNavIconRailWidth
    val reservedWidth = lerp(collapsedTotal, expandedWidth, expandProgress)
    val panelWidth = reservedWidth - panelStartInset

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(
                    start = panelStartInset,
                    top = topPadding + 2.dp,
                    bottom = TabletNavBottomInset,
                )
                .width(panelWidth)
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
                expandProgress = expandProgress,
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
                        expandProgress = expandProgress,
                        textColor = textColor,
                        selectedBg = selectedBg,
                        showSelectedBg = true,
                        showLabel = true,
                        onClick = { onTabSelected(index) },
                    )
                }
            }
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
    expandProgress: Float,
    textColor: Color,
    selectedBg: Color,
    showSelectedBg: Boolean,
    showLabel: Boolean,
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
                    fontWeight = if (selected && expandProgress > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.graphicsLayer { alpha = expandProgress },
                )
            }
        }
    }
}
