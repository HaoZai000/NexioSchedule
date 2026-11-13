package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenu
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenuItem
import com.haooz.chedule.ui.basic.LiquidGlassDropdownShadowPadding
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.LocalChromeStartInset
import com.haooz.chedule.ui.components.tabletNavRailStartPadding
import com.haooz.chedule.ui.theme.rememberAppThemeController
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.LocalNativeTopBarHost
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.haooz.softglass.backdrop.GlassBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.FastForward
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberTopBarMaterialState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 页面内「更多」菜单的宿主状态：菜单随页面一起绘制，不再由外层 popup 槽按 pager 位移追顶栏。 */
class PageMoreMenuState(
    val show: Boolean,
    val onDismiss: () -> Unit,
    val onExpand: () -> Unit,
    val fraction: Animatable<Float, *>,
    val enabled: Boolean,
    val native: Boolean,
)

/**
 * 顶栏「更多」菜单的宿主：负责定位、遮罩与菜单项。
 *
 * 课程表页与今日页各有一颗，收起态就是 [LiquidGlassDropdownMenu] 的触发按钮；
 * 由宿主原生绘制的那一份通过 [scheduleMoreNative] / [todayMoreNative] 关掉。
 *
 * @param moreSlotTopPx 顶栏「更多」占位槽的顶部（页面坐标 px）。传 state 而非 Float，
 *   让定位在绘制期读取，首帧不会因测量未完成而跳动。
 */
@Composable
internal fun MorePopupMenus(
    showMorePopup: Boolean,
    onMorePopupDismiss: () -> Unit,
    onMorePopupExpand: () -> Unit = {},
    showTodayMorePopup: Boolean,
    onTodayMorePopupDismiss: () -> Unit,
    onTodayMorePopupExpand: () -> Unit = {},
    morePopupFraction: Animatable<Float, *>,
    todayMorePopupFraction: Animatable<Float, *>,
    scheduleMenuOffset: () -> Offset = { Offset.Zero },
    todayMenuOffset: () -> Offset = { Offset.Zero },
    moreSlotTopPx: MutableFloatState = remember { mutableFloatStateOf(0f) },
    scheduleMaterialAlpha: Float = 0f,
    todayMaterialAlpha: Float = 0f,
    liquidGlassBackdrop: GlassBackdrop,
    isShiftMode: Boolean = false,
    onJumpWeek: () -> Unit,
    onCourseManage: () -> Unit,
    onEnterCustomize: () -> Unit,
    onJumpToDate: () -> Unit,
    onMoreBackProgress: (Float) -> Unit = {},
    onMoreBackCancelled: () -> Unit = {},
    onTodayMoreBackProgress: (Float) -> Unit = {},
    onTodayMoreBackCancelled: () -> Unit = {},
    /** 平板右上角不显示「更多」：跳转、管理、外观都已在侧栏里。 */
    enabled: Boolean = true,
    /** 课程表页的「更多」由原生宿主绘制，关掉这里的触发按钮避免从原生按钮下方露出来。 */
    scheduleMoreNative: Boolean = false,
    /** 今日页的「更多」由原生宿主绘制。 */
    todayMoreNative: Boolean = false,
) {
    if (!enabled) {
        // 菜单展开时进入禁用场景（平板侧栏、课表外观、课表切换）：先收起，否则会盖在新页面上仍可点击
        LaunchedEffect(enabled) {
            if (showMorePopup) onMorePopupDismiss()
            if (showTodayMorePopup) onTodayMorePopupDismiss()
        }
        return
    }

    // 任一菜单展开时铺一层遮罩，点空白处一起收起
    if (showMorePopup || showTodayMorePopup) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (showMorePopup) onMorePopupDismiss()
                    if (showTodayMorePopup) onTodayMorePopupDismiss()
                }
        )
    }

    val density = LocalDensity.current
    // 占位槽还没测量到时，用顶栏高度兜底（状态栏 + AppBar）
    val fallbackAnchorTopPx = with(density) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        (if (statusBarTop > 0.dp) statusBarTop - 20.dp else 17.dp).coerceAtLeast(0.dp).toPx()
    }
    val shadowPaddingPx = with(density) { LiquidGlassDropdownShadowPadding.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = false
                // 占位槽顶边减去阴影外扩，才是菜单内容顶边
                val measured = moreSlotTopPx.floatValue - shadowPaddingPx
                translationY = if (measured > 0f) measured else fallbackAnchorTopPx
            }
            .offset(x = 9.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        if (!scheduleMoreNative) {
            LiquidGlassDropdownMenu(
                show = showMorePopup,
                backdrop = liquidGlassBackdrop,
                fraction = morePopupFraction,
                onDismiss = onMorePopupDismiss,
                onBackProgress = onMoreBackProgress,
                onBackCancelled = onMoreBackCancelled,
                triggerIcon = MiuixIcons.More,
                triggerContentDescription = CommonStr.more(),
                onExpand = onMorePopupExpand,
                offsetPx = scheduleMenuOffset,
                materialAlpha = scheduleMaterialAlpha,
            ) {
                MoreMenuItems(
                    jumpLabel = ChromeStr.jumpToWeek(),
                    onJump = onJumpWeek,
                    showCourseManage = !isShiftMode,
                    onCourseManage = onCourseManage,
                    onEnterCustomize = onEnterCustomize,
                    onDismiss = onMorePopupDismiss,
                )
            }
        }
        if (!todayMoreNative) {
            LiquidGlassDropdownMenu(
                show = showTodayMorePopup,
                backdrop = liquidGlassBackdrop,
                fraction = todayMorePopupFraction,
                onDismiss = onTodayMorePopupDismiss,
                onBackProgress = onTodayMoreBackProgress,
                onBackCancelled = onTodayMoreBackCancelled,
                triggerIcon = MiuixIcons.More,
                triggerContentDescription = CommonStr.more(),
                onExpand = onTodayMorePopupExpand,
                offsetPx = todayMenuOffset,
                materialAlpha = todayMaterialAlpha,
            ) {
                MoreMenuItems(
                    jumpLabel = ChromeStr.jumpToDate(),
                    onJump = onJumpToDate,
                    showCourseManage = true,
                    onCourseManage = onCourseManage,
                    onEnterCustomize = onEnterCustomize,
                    onDismiss = onTodayMorePopupDismiss,
                )
            }
        }
    }
}

/** 两个页面共用的「更多」菜单项：跳转、课程管理（班次模式隐藏）、课表外观。 */
@Composable
private fun MoreMenuItems(
    jumpLabel: String,
    onJump: () -> Unit,
    showCourseManage: Boolean,
    onCourseManage: () -> Unit,
    onEnterCustomize: () -> Unit,
    onDismiss: () -> Unit,
) {
    LiquidGlassDropdownMenuItem(
        text = jumpLabel,
        onClick = {
            onDismiss()
            onJump()
        },
        icon = { MenuIcon(MiuixIcons.Basic.FastForward) },
    )
    if (showCourseManage) {
        LiquidGlassDropdownMenuItem(
            text = ChromeStr.courseManage(),
            onClick = {
                onDismiss()
                onCourseManage()
            },
            icon = { MenuIcon(MiuixIcons.Backup) },
        )
    }
    LiquidGlassDropdownMenuItem(
        text = ChromeStr.scheduleAppearance(),
        onClick = {
            onDismiss()
            onEnterCustomize()
        },
        icon = { MenuIcon(MiuixIcons.Background) },
    )
}

@Composable
private fun MenuIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.size(23.dp),
    )
}

/**
 * 单页顶栏的主题锁。
 *
 * 共享顶栏槽的外层主题跟随当前 tab，切页瞬间会把尚未移出屏幕的顶栏染成新主题；
 * 这里再按本页自己的主题锁一层。
 */
@Composable
internal fun PageLockedTopBarTheme(
    pageForcedDark: Boolean?,
    content: @Composable () -> Unit,
) {
    val appDark = rememberAppSettingDark()
    val pageDark = pageForcedDark ?: appDark
    val controller = rememberAppThemeController(isDark = pageDark)
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalForcedDarkTheme provides pageForcedDark) {
            content()
        }
    }
}

/**
 * 今日页顶栏。
 *
 * @param visible 隐藏时 alpha 归零但仍参与测量，保证「更多」占位槽首帧就有位置。
 * @param blurResampleKey 采样层重录的指纹；[blurSampleTrack] 为当前采样位移。
 * @param onMoreSlotTop 上报「更多」占位槽坐标，由页面换算成相对页面根的局部坐标。
 * @param titleEndPadding 标题右侧额外内边距，避开折叠屏 iPhone 的竖排原生 tab bar。
 */
@Composable
internal fun TodayTopBar(
    liquidGlassBackdrop: GlassBackdrop?,
    navBarStyle: String,
    currentDayOfWeek: Int,
    isToday: Boolean = true,
    onJumpToDate: () -> Unit = {},
    onEnterCustomize: () -> Unit = {},
    scrollBehavior: SharedScrollBehavior? = null,
    visible: Boolean = true,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
    onMoreSlotTop: (LayoutCoordinates) -> Unit = {},
    onMoreMaterial: (Float) -> Unit = {},
    onMoreExpand: () -> Unit = {},
    titleEndPadding: Dp = 0.dp,
) {
    val backdrop = liquidGlassBackdrop ?: return
    val isRail = navBarStyle == "rail"
    // iOS 今日页右上角由原生 UIMenu 接管，Compose 侧不渲染触发按钮
    val hostOwnsTopRight = LocalHostNavigation.current != null ||
        LocalNativeTopBarHost.current?.ownsTopRight == true
    val nativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp
    val dayOfWeekName = when (currentDayOfWeek) {
        1 -> CommonStr.weekdayMon()
        2 -> CommonStr.weekdayTue()
        3 -> CommonStr.weekdayWed()
        4 -> CommonStr.weekdayThu()
        5 -> CommonStr.weekdayFri()
        6 -> CommonStr.weekdaySat()
        7 -> CommonStr.weekdaySun()
        else -> ""
    }
    val titleText = if (isToday) ChromeStr.todayIsDow(dayOfWeekName) else dayOfWeekName
    // 平板：标题避让左侧栏后左对齐，手机仍居中
    val titlePadding = Modifier
        .padding(start = LocalChromeStartInset.current)
        .then(
            if (isRail) tabletNavRailStartPadding().padding(start = 12.dp)
            else Modifier
        )
    // 渐变遮罩与按钮玻璃读同一个材质实例
    val material = rememberTopBarMaterialState(scrollBehavior)

    ProgressiveBlurTopBar(
        backdrop = backdrop,
        topInsetAdjustment = nativeBarInset,
        noTopStatusBar = hostVerticalBarEdge(),
        modifier = Modifier.graphicsLayer { alpha = if (visible) 1f else 0f },
        resampleKey = blurResampleKey,
        sampleTrack = blurSampleTrack,
        // 无材质档没有模糊层，遮罩是顶栏唯一的材质，跟按钮背景一起出现
        maskAlpha = material.alpha,
    ) {
        CollapsibleTopAppBar(
            title = titleText,
            largeTitle = titleText,
            modifier = Modifier.zIndex(1f),
            scrollBehavior = scrollBehavior,
            materialState = material,
            titleStartAligned = isRail,
            titleModifier = titlePadding.then(
                if (titleEndPadding > 0.dp) Modifier.padding(end = titleEndPadding) else Modifier
            ),
            // 平板左上角不放返回按钮
            startAction = null,
            onAlphaChanged = { backdropAlpha, _ -> onMoreMaterial(backdropAlpha) },
            endAction = { backdropAlpha, shadowAlpha ->
                if (visible) {
                    if (isRail) {
                        RailTopBarActions(
                            backdrop = backdrop,
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                            onJumpToDate = onJumpToDate,
                            onEnterCustomize = onEnterCustomize,
                        )
                    } else {
                        MoreSlotPlaceholder(
                            hostOwnsTopRight = hostOwnsTopRight,
                            onExpand = onMoreExpand,
                            onSlotTop = onMoreSlotTop,
                        )
                    }
                }
            },
        )
    }
}

/** 平板右上角的两个玻璃按钮：课程管理已在侧栏，这里只留跳转日期与课表外观。 */
@Composable
private fun RailTopBarActions(
    backdrop: GlassBackdrop,
    backdropAlpha: Float,
    shadowAlpha: Float,
    onJumpToDate: () -> Unit,
    onEnterCustomize: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidTopBarButton(
            onClick = onJumpToDate,
            backdrop = backdrop,
            icon = MiuixIcons.Basic.FastForward,
            contentDescription = ChromeStr.jumpToDate(),
            iconSize = 23.dp,
            backdropAlpha = backdropAlpha,
            shadowAlpha = shadowAlpha,
        )
        LiquidTopBarButton(
            onClick = onEnterCustomize,
            backdrop = backdrop,
            icon = MiuixIcons.Background,
            contentDescription = ChromeStr.scheduleAppearance(),
            iconSize = 23.dp,
            backdropAlpha = backdropAlpha,
            shadowAlpha = shadowAlpha,
        )
    }
}

/**
 * 「更多」占位槽：按钮由 [LiquidGlassDropdownMenu] 自己绘制（收起态即按钮），
 * 这里只提供同尺寸命中区并上报位置，供菜单对齐。
 */
@Composable
private fun MoreSlotPlaceholder(
    hostOwnsTopRight: Boolean,
    onExpand: () -> Unit,
    onSlotTop: (LayoutCoordinates) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Spacer(
        modifier = Modifier
            .size(42.dp)
            .then(
                if (hostOwnsTopRight) {
                    Modifier
                } else {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onExpand,
                        )
                        .semantics { contentDescription = CommonStr.more() }
                }
            )
            .onGloballyPositioned { onSlotTop(it) }
    )
}
