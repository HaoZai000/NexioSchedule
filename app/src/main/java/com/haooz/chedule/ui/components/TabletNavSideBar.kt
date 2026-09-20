package com.haooz.chedule.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Months

/** 平板左侧导航全局状态：跨 Activity 共享；内容让位用静态 padding，避免进页入场动画 */
object TabletNavSideState {
    var expanded by mutableStateOf(false)
    /** 二级页点选主 tab 后 MainActivity 待处理下标；-1 无 */
    var pendingMainTab by mutableStateOf(-1)
}

const val TabletNavSideWidthFraction = 0.22f

/** 打开 Activity 且不播放转场（侧栏展开时避免「进页又入场」） */
fun Context.startActivityNoNavAnim(intent: Intent) {
    startActivity(intent)
    if (this is Activity) {
        overridePendingTransition(0, 0)
    }
}

/** 侧栏展开时打开页面：无转场，避免进页入场动画 */
fun Context.startActivityTabletNavAware(intent: Intent) {
    if (TabletNavSideState.expanded) {
        startActivityNoNavAnim(intent)
    } else {
        startActivity(intent)
    }
}

/** 二级页点选主 tab：回 MainActivity，关闭当前页，无转场动画 */
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

/** 侧栏展开时内容左边距（静态，不 animate） */
@Composable
fun tabletNavSideStartPadding(): androidx.compose.ui.unit.Dp {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    if (!isTablet || !TabletNavSideState.expanded) return 0.dp
    return LocalConfiguration.current.screenWidthDp.dp * TabletNavSideWidthFraction
}

/** 平板左侧导航栏（展开态）。无入场动画，直接按全局状态绘制。 */
@Composable
fun TabletNavSideBar(
    backdrop: Backdrop?,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarPadding > 0.dp) statusBarPadding else 36.dp
    val isLightTheme = !isAppDarkTheme()
    val textColor = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
    val mutedColor = textColor.copy(alpha = 0.55f)
    val containerColor =
        if (isLightTheme) Color(0xFFFFFFFF).copy(0.6f) else Color(0xFF121212).copy(0.54f)
    val solidContainer = if (isLightTheme) Color(0xFFF7F7F7) else Color(0xFF1C1C1E)
    val selectedBg =
        if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.14f)
    val defaultEdgeLight = rememberDefaultEdgeLight()
    val sideInset = 12.dp
    val sideCorner = 16.dp
    val tabs = listOf(
        "今日" to MiuixIcons.Album,
        "课程表" to MiuixIcons.Months,
        "我的" to MiuixIcons.ContactsCircle,
    )

    val panelModifier = Modifier
        .fillMaxWidth(TabletNavSideWidthFraction)
        .padding(top = topPadding + 2.dp, start = sideInset, bottom = sideInset)
        .fillMaxHeight()
        .then(
            if (backdrop != null) {
                Modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousRoundedRectangle(sideCorner) },
                        effects = {
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                        },
                        shadow = { Shadow(alpha = 0.28f) },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .edgeLight(
                        shape = ContinuousRoundedRectangle(sideCorner),
                        edgeLight = defaultEdgeLight,
                    )
            } else {
                Modifier.background(solidContainer, ContinuousRoundedRectangle(sideCorner))
            }
        )
        .padding(horizontal = 14.dp, vertical = 12.dp)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = panelModifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(ContinuousCapsule())
                        .background(selectedBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCollapse,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TabletNavSideIcon,
                        contentDescription = "收起侧栏",
                        tint = textColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("导航", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = mutedColor)
            }

            tabs.forEachIndexed { index, (label, icon) ->
                val selected = index == selectedTab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(ContinuousRoundedRectangle(16.dp))
                        .background(if (selected) selectedBg else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(index) },
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) textColor else mutedColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) textColor else mutedColor,
                    )
                }
            }
        }
    }
}

/**
 * 任意页内容外包：侧栏展开时 **静态** 左边距 + 侧栏置顶。
 * 不做 animateDpAsState，避免每次进页都播入场动画。
 */
@Composable
fun TabletNavAwareContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    content: @Composable () -> Unit,
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val expanded = TabletNavSideState.expanded
    val sidePad = tabletNavSideStartPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = sidePad)
        ) {
            content()
        }
        if (isTablet && expanded) {
            TabletNavSideBar(
                backdrop = backdrop,
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                onCollapse = { TabletNavSideState.expanded = false },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(30f),
            )
        }
    }
}

internal val TabletNavSideIcon: ImageVector = ImageVector.Builder(
    name = "TabletNavSide",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3.2f, 5f)
        lineTo(9.4f, 5f)
        lineTo(9.4f, 19f)
        lineTo(3.2f, 19f)
        close()
    }
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f, strokeLineCap = StrokeCap.Round) {
        moveTo(12.4f, 7.2f)
        lineTo(20.6f, 7.2f)
        moveTo(12.4f, 12f)
        lineTo(20.6f, 12f)
        moveTo(12.4f, 16.8f)
        lineTo(20.6f, 16.8f)
    }
}.build()
