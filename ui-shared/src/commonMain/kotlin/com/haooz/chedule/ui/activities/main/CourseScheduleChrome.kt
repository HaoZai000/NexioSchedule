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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import com.haooz.chedule.ui.components.rememberTopBarMaterialAlpha
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课表主界面的顶栏与「更多」下拉菜单。从 `CourseScheduleApp.kt` 拆出。
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
    /** 「更多」占位槽实测顶部（root 坐标 px）；传 state 而非 Float，定位须在绘制期读，首帧才不跳。 */
    moreSlotTopPx: MutableFloatState = mutableFloatStateOf(0f),
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
    // 平板右上角不显示「更多」：跳转/管理/外观都已在侧栏里
    enabled: Boolean = true,
    /**
     * 课程表页的「更多」由宿主（鸿蒙 ArkUI）画。
     *
     * 这颗按钮的**收起态就是按钮本身**（`LiquidGlassDropdownMenu` 的 trigger），
     * 宿主接管后它还留在 Compose 里，会从原生按钮下面露出一截（实测是几个黑点）。
     * 今日页那颗不受影响，所以这里只关课程表这一份。
     */
    scheduleMoreNative: Boolean = false,
    /** 今日页的「更多」同样由宿主画，这颗 Compose 触发按钮必须一起关掉。 */
    todayMoreNative: Boolean = false,
) {
    if (!enabled) {
        // 菜单开着时切进/切出禁用场景（平板 rail、课表外观、课表切换）要强制收起，
        // 否则展开态会盖在新页面上还能点。用 enabled 做 key，翻转即重跑。
        LaunchedEffect(enabled) {
            if (showMorePopup) onMorePopupDismiss()
            if (showTodayMorePopup) onTodayMorePopupDismiss()
        }
        return
    }
    if (showMorePopup) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onMorePopupDismiss() }
        )
    }
    if (showTodayMorePopup) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onTodayMorePopupDismiss() }
        )
    }
    // 「更多」锚点纵坐标：**绘制期**定位
    val fallbackAnchorTopPx = with(LocalDensity.current) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        (if (statusBarTop > 0.dp) statusBarTop - 20.dp else 17.dp)
            .coerceAtLeast(0.dp)
            .toPx()
    }
    val shadowPaddingPx = with(LocalDensity.current) {
        LiquidGlassDropdownShadowPadding.toPx()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = false
                // moreSlotTopPx 是占位槽顶边，减掉阴影外扩才是内容顶边
                val measured = moreSlotTopPx.floatValue - shadowPaddingPx
                translationY = if (measured > 0f) measured else fallbackAnchorTopPx
            }
            .offset(x = 9.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        // 课程表页「更多」
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
                LiquidGlassDropdownMenuItem(
                    text = ChromeStr.jumpToWeek(),
                    onClick = {
                        onMorePopupDismiss()
                        onJumpWeek()
                    },
                    icon = {
                        Icon(
                            imageVector = MiuixIcons.Basic.FastForward,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
                if (!isShiftMode) {
                    LiquidGlassDropdownMenuItem(
                        text = ChromeStr.courseManage(),
                        onClick = {
                            onMorePopupDismiss()
                            onCourseManage()
                        },
                        icon = {
                            Icon(
                                imageVector = MiuixIcons.Backup,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    )
                }
                LiquidGlassDropdownMenuItem(
                    text = ChromeStr.scheduleAppearance(),
                    onClick = {
                        onMorePopupDismiss()
                        onEnterCustomize()
                    },
                    icon = {
                        Icon(
                            imageVector = MiuixIcons.Background,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                )
            }
        }
        // 今日页「更多」
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
            LiquidGlassDropdownMenuItem(
                text = ChromeStr.jumpToDate(),
                onClick = {
                    onTodayMorePopupDismiss()
                    onJumpToDate()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Basic.FastForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
            LiquidGlassDropdownMenuItem(
                text = ChromeStr.courseManage(),
                onClick = {
                    onTodayMorePopupDismiss()
                    onCourseManage()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Backup,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
            LiquidGlassDropdownMenuItem(
                text = ChromeStr.scheduleAppearance(),
                onClick = {
                    onTodayMorePopupDismiss()
                    onEnterCustomize()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Background,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
            }
        }
    }
}

/**
 * 共享顶栏槽里单页顶栏的主题锁：外层 chrome 主题跟随当前 tab（[forcedDark]），
 * 切页时会先变，尚未移出屏幕的顶栏遮罩瞬间变色；这里按本页自己的主题再锁一层。
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

@Composable
internal fun SettingsTopBar(
    liquidGlassBackdrop: GlassBackdrop?,
    navBarStyle: String,
    scrollBehavior: SharedScrollBehavior? = null,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
    // 标题文字的右侧额外内边距：避开右下角竖排的原生 tab bar（折叠屏 iPhone 折叠态）。
    // 渐变糊带铺满、右上角是原生按钮，都不加这个 padding。
    titleEndPadding: Dp = 0.dp,
) {
    if (liquidGlassBackdrop == null) return
    val isTablet = navBarStyle == "rail"
    // 仅设置页顶栏锁应用主题，避免切页时渐变/糊层随课程表壁纸锁色跳变
    val settingsBarDark = rememberAppSettingDark()
    val settingsBarController = rememberAppThemeController(isDark = settingsBarDark)

    // 平板设置：渐变画在 TabletSettingsScreen 内容层，这里不再叠全宽遮罩
    val settingsNativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp
    MiuixTheme(controller = settingsBarController) {
        ProgressiveBlurTopBar(
            backdrop = liquidGlassBackdrop,
            blurAlpha = if (isTablet) 0f else 1f,
            topInsetAdjustment = settingsNativeBarInset,
            noTopStatusBar = hostVerticalBarEdge(),
            resampleKey = blurResampleKey,
            sampleTrack = blurSampleTrack,
        ) {
            CollapsibleTopAppBar(
                // 平板设置：顶栏不再显示标题，标题由 MainActivity 叠层绘制
                title = if (isTablet) "" else CommonStr.settings(),
                largeTitle = if (isTablet) "" else CommonStr.settings(),
                showLargeTitle = if (isTablet) false else null,
                showSmallTitle = if (isTablet) true else null,
                modifier = Modifier.zIndex(1f),
                scrollBehavior = if (isTablet) null else scrollBehavior,
                titleModifier = if (titleEndPadding > 0.dp) {
                    Modifier.padding(end = titleEndPadding)
                } else {
                    Modifier
                },
                startAction = null,
            )
        }
    }
}

@Composable
internal fun TodayTopBar(
    liquidGlassBackdrop: GlassBackdrop?,
    navBarStyle: String,
    currentDayOfWeek: Int,
    isToday: Boolean = true,
    onBackToToday: () -> Unit = {},
    onJumpToDate: () -> Unit = {},
    onEnterCustomize: () -> Unit = {},
    scrollBehavior: SharedScrollBehavior? = null,
    visible: Boolean = true,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
    onMoreSlotTop: (Float) -> Unit = {},
    onMoreMaterial: (Float) -> Unit = {},
    onMoreExpand: () -> Unit = {},
    // 标题文字的右侧额外内边距：避开右下角竖排的原生 tab bar（折叠屏 iPhone 折叠态）。
    // 渐变糊带铺满，且 iOS 侧 Compose 根本不渲染顶栏按钮（原生 chrome 接管），都不加。
    titleEndPadding: Dp = 0.dp,
) {
    if (liquidGlassBackdrop == null) return
    val isTabletLiquidGlass = navBarStyle == "rail"
    // 同 ScheduleTopBar：iOS 今日页右上角由原生 UIMenu 接管，Compose 侧不渲染
    val hostOwnsTopRight = LocalHostNavigation.current != null ||
        LocalNativeTopBarHost.current?.ownsTopRight == true
    // 平板：标题避让侧栏后左对齐用的附加修饰符
    val titleRailPadding = Modifier
        .padding(start = LocalChromeStartInset.current)
        .then(
            if (isTabletLiquidGlass) tabletNavRailStartPadding().padding(start = 12.dp)
            else Modifier
        )
    val dayOfWeekNames = listOf(
        CommonStr.weekdayMon(), CommonStr.weekdayTue(), CommonStr.weekdayWed(),
        CommonStr.weekdayThu(), CommonStr.weekdayFri(), CommonStr.weekdaySat(),
        CommonStr.weekdaySun(),
    )
    val dayOfWeekName = if (currentDayOfWeek in 1..7) dayOfWeekNames[currentDayOfWeek - 1] else ""
    val titleText = if (isToday) ChromeStr.todayIsDow(dayOfWeekName) else dayOfWeekName
    val todayNativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp

    // 隐藏时 alpha=0 但仍测量，保证 currentHeightPx 启动即就位
    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
        topInsetAdjustment = todayNativeBarInset,
        noTopStatusBar = hostVerticalBarEdge(),
        modifier = Modifier.graphicsLayer { alpha = if (visible) 1f else 0f },
        resampleKey = blurResampleKey,
        sampleTrack = blurSampleTrack,
        // 无材质档没有模糊层，遮罩是顶栏唯一的材质：跟按钮背景一起出现
        maskAlpha = rememberTopBarMaterialAlpha(scrollBehavior),
    ) {
        CollapsibleTopAppBar(
            title = titleText,
            largeTitle = titleText,
            modifier = Modifier.zIndex(1f),
            scrollBehavior = scrollBehavior,
            // 平板：标题避让左侧侧栏后左对齐（手机仍居中）
            titleStartAligned = isTabletLiquidGlass,
            titleModifier = titleRailPadding.then(
                if (titleEndPadding > 0.dp) Modifier.padding(end = titleEndPadding) else Modifier
            ),
            // 平板左上角不放返回按钮
            startAction = null,
            onAlphaChanged = { backdrop, _ -> onMoreMaterial(backdrop) },
            endAction = { backdropAlpha, shadowAlpha ->
                if (visible) {
                    if (isTabletLiquidGlass) {
                        // pad：课程管理已在侧栏，右上角直接放跳转日期 + 课表外观
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidTopBarButton(
                                onClick = onJumpToDate,
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Basic.FastForward,
                                contentDescription = ChromeStr.jumpToDate(),
                                iconSize = 23.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                            LiquidTopBarButton(
                                onClick = onEnterCustomize,
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Background,
                                contentDescription = ChromeStr.scheduleAppearance(),
                                iconSize = 23.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        }
                    } else {
                        // 「更多」按钮由下拉菜单组件自带（收起态即那颗按钮，唯一一份），这里只占位对齐
                        Spacer(
                            modifier = Modifier
                                .size(42.dp)
                                .then(
                                    if (!hostOwnsTopRight) Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onMoreExpand,
                                        )
                                        .semantics { contentDescription = CommonStr.more() }
                                    else Modifier
                                )
                                .onGloballyPositioned { onMoreSlotTop(it.positionInRoot().y) }
                        )
                    }
                }
            },
        )
    }
}
