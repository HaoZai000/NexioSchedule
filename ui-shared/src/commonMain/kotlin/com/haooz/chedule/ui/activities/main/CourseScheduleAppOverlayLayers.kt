package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.SwitchCardSnapshotStore
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.data.appearance.AppearanceStore
import com.haooz.chedule.data.appearance.scheduleSurfaceAlpha
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.activities.SwitchScheduleScreen
import com.haooz.chedule.ui.activities.classschedule.CourseCard
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.basic.ShortcutMenuShadowPadding
import com.haooz.chedule.ui.components.CrossWeekDropHint
import com.haooz.chedule.ui.components.EdgeAutoPageIndicator
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.effects.motion.CustomizePageEasing
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.CombinationThumbnail
import com.haooz.chedule.ui.screens.CourseDetailScreen
import com.haooz.chedule.ui.screens.CustomizeScheduleScreen
import com.haooz.chedule.ui.theme.rememberAppThemeController
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.HostedOverlay
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.LocalHostOverlays
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.addScreenCornerRect
import com.haooz.chedule.ui.utils.bottomFloatingPadding
import com.haooz.chedule.ui.utils.buildShareScheduleMap
import com.haooz.chedule.ui.utils.courseCardSolidBacking
import com.haooz.chedule.ui.utils.loadScheduleThemeMode
import com.haooz.chedule.ui.utils.saveScheduleThemeMode
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课表主界面「浮层组」：浮动卡片 / 交换飞行、快捷菜单、课表外观页、详情页、切换课表、排班 loading。
 *
 * 层级顺序固定：详情页必须盖在弹窗层之上。
 */
@Composable
internal fun BoxScope.CourseScheduleAppOverlayLayers(
    state: CourseScheduleAppState,
    displayAppearance: AppearanceConfig,
    isEntryAnimating: Boolean,
) {
    with(state) {
        val density = densityProvider
        HostedOverlay(
            active = floatingCardVisible || swapFlightVisible ||
                    shortcutMenuVisible || shortcutMenuCourse != null || emptyCellMenuTarget != null ||
                    showCustomizePage || isCustomizeExiting || isWindowCutoutActive || customizeCoverActive ||
                    snapshotCoverBitmap != null || showDetail || detailSnapshot != null ||
                    showShiftLoading || shiftLoadingActive,
        ) {
            // 浮层主题跟壁纸/应用设置
            val overlayEffectiveForcedDark =
                if (captureThemeActive) captureThemeIsDark else forcedDark
            val overlayEffectiveDark = overlayEffectiveForcedDark ?: appSettingDark
            val overlayPageController = rememberAppThemeController(isDark = overlayEffectiveDark)
            // 无壁纸时浮层垫课程表页底色（与网格课程卡同一份，取值见 courseCardSolidBacking）
            val floatSolidBacking = courseCardSolidBacking(
                isDark = overlayEffectiveDark,
                hasWallpaper = wallpaperBitmap != null
            )
            // 拖浮层跟手高光的中心（px，非 state：只在 draw 里写，不触发额外失效）
            val glowCenterPx = remember { FloatArray(2) }
            val dragCardHighlight = remember {
                InteractiveHighlight(
                    animationScope = coroutineScope,
                    position = { _, _ ->
                        if (isFloatingCardDragging && floatingCardVisible) {
                            glowCenterPx[0] = draggedCardPosition.x + floatingOffsetX.floatValue
                            glowCenterPx[1] = draggedCardPosition.y + floatingOffsetY.floatValue
                        }
                        Offset(glowCenterPx[0], glowCenterPx[1])
                    },
                    radiusScale = { 1f / 3f },
                    radiusBaseDp = 250.dp,
                    // 按压进度由 glowAlpha 驱动（150ms 淡变），不走组件自带手势
                    pressProgressOverride = { glowAlpha },
                    // 只要圆形光晕，不要整幅提亮
                    drawFlatOverlay = false,
                )
            }
            MiuixTheme(controller = overlayPageController) {
                CompositionLocalProvider(LocalForcedDarkTheme provides overlayEffectiveForcedDark) {
                    // 跟手高光：画在浮层之前（同一父级里先声明先画），压在整页之上、浮层之下
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(dragCardHighlight.modifier)
                    )
                    // 边缘自动翻周示意：放在浮层组（root），铺满整屏才能延伸到
                    // 标题/状态栏区域；退出动画里方向不能归零，否则内容直接返回空、变成硬切。
                    var lastEdgeDirection by remember { mutableIntStateOf(0) }
                    val shownEdgeDirection =
                        if (edgeAutoPageDirection != 0) {
                            edgeAutoPageDirection
                        } else {
                            lastEdgeDirection
                        }
                    SideEffect {
                        if (edgeAutoPageDirection != 0) {
                            lastEdgeDirection = edgeAutoPageDirection
                        }
                    }
                    AnimatedVisibility(
                        visible = floatingCardVisible && isFloatingCardDragging &&
                            edgeAutoPageDirection != 0,
                        enter = fadeIn(tween(durationMillis = 180)),
                        exit = fadeOut(tween(durationMillis = 180)),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            EdgeAutoPageIndicator(
                                direction = shownEdgeDirection,
                                modifier = Modifier.align(
                                    if (shownEdgeDirection < 0) {
                                        Alignment.CenterStart
                                    } else {
                                        Alignment.CenterEnd
                                    }
                                ),
                            )
                        }
                    }
                    // 跨周调课提示：放在浮层组里（主内容录制层之外），和底栏一样
                    // 从外部采样内容层。留在页面里会自己采自己的录制层，拖拽提饱和
                    // 每帧重录时胶囊会闪没。
                    val hintBottomPadding = if (nativeBottomBar) {
                        (hostNavigation?.bottomBarInset?.toFloat() ?: 0f).dp + 8.dp
                    } else {
                        bottomFloatingPadding() + 64.dp
                    }
                    AnimatedVisibility(
                        visible = floatingCardVisible && isFloatingCardDragging,
                        enter = fadeIn(tween(durationMillis = 200)),
                        exit = fadeOut(tween(durationMillis = 160)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = hintBottomPadding),
                    ) {
                        CrossWeekDropHint(
                            fromWeek = draggedWeek,
                            toWeek = currentViewingWeek,
                            backdrop = liquidGlassBackdrop,
                        )
                    }
                    if (floatingCardVisible) {
                        val course = draggedCardCourse
                        if (course != null) {
                            // 锚点只用长按瞬间的中心；拖拽/吸附位移走 graphicsLayer，不进组合
                            val widthPx = draggedCardSize.x
                            val geom = gridGeometry
                            val sectionH = geom?.sectionHeightPx
                                ?: with(density) { displayAppearance.cardHeight.dp.toPx() }
                            val heightPx = if (isPasteFlight) {
                                // 粘贴飞行用几何算高（源课可能不在当前周，draggedCardSize 不适用）
                                geom?.let {
                                    courseVisualHeightPx(it, course.startSection, course.endSection, dividerPxFor(it))
                                } ?: (course.endSection - course.startSection + 1) * sectionH
                            } else {
                                draggedCardSize.y
                            }
                            val baseOffsetX = with(density) {
                                (draggedCardPosition.x - widthPx / 2f).toDp()
                            }
                            val baseOffsetY = with(density) {
                                (draggedCardPosition.y - heightPx / 2f).toDp()
                            }
                            val width = with(density) { widthPx.toDp() }
                            val height = with(density) { heightPx.toDp() }
                            LaunchedEffect(floatingCardVisible, isPasteFlight) {
                                // 粘贴飞行自行控制缩放，跳过长按入场
                                if (floatingCardVisible && !isPasteFlight) {
                                    floatingScale.snapTo(0.94f)
                                    floatingScale.animateTo(1.08f, tween(durationMillis = 120))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .offset(x = baseOffsetX, y = baseOffsetY)
                                    .size(width = width, height = height)
                                    // 自定义时间课与时间轴卡的高度都是实测可见高，这里不能再缝一次
                                    .then(
                                        if (course.hasValidCustomTime() ||
                                            scheduleViewMode == ScheduleViewMode.TIMELINE
                                        ) {
                                            Modifier
                                        } else {
                                            Modifier.padding(vertical = 2.dp)
                                        }
                                    )
                                    .graphicsLayer {
                                        translationX = floatingOffsetX.floatValue
                                        translationY = floatingOffsetY.floatValue
                                        scaleX = floatingScale.value
                                        scaleY = floatingScale.value
                                    }
                            ) {
                                CourseCard(
                                    course = course,
                                    // 粘贴飞行强制本周样式，避免源课不在当前周飞出灰卡
                                    isCurrentWeek = if (isPasteFlight) true else course.isActiveInWeek(
                                        draggedWeek
                                    ),
                                    cardBlurRadius = displayAppearance.cardBlurRadius,
                                    cardRefraction = displayAppearance.cardRefraction,
                                    cardAlpha = displayAppearance.cardAlpha,
                                    cardSurfaceAlpha = displayAppearance.scheduleSurfaceAlpha(isAppDarkTheme()),
                                    // 无壁纸时垫页底色，取值见 floatSolidBacking
                                    solidBackingColor = floatSolidBacking,
                                    cardHeightPerSection = displayAppearance.cardHeight,
                                    // ⚠ 必须把浮层 Box 的高度原样传给卡片，否则长按浮层会偏上：
                                    // 卡片高（customCardHeightDp ?: 节次数×每节高）与 Box 高度 heightPx
                                    // 不一致时按顶部对齐，视觉中心偏上 (heightPx − 卡片高)/2。
                                    customCardHeightDp = with(density) { heightPx.toDp().value },
                                    cardCornerRadius = displayAppearance.cardCornerRadius,
                                    isTablet = isTablet,
                                    cardContentAlignment = displayAppearance.cardContentAlignment,
                                    cardTextColor = displayAppearance.cardTextColor,
                                    cardTextScale = displayAppearance.cardTextScale,
                                    showClassroom = displayAppearance.showClassroom,
                                    showTeacher = displayAppearance.showTeacher,
                                    disablePadding = true,
                                    onClick = {}
                                )
                            }
                        }
                    }
                    if (swapFlightVisible) {
                        val swapCourse = swapFlightCourse
                        if (swapCourse != null) {
                            val centerX = swapFlightOriginCenter.x + swapFlightOffsetX.value
                            val centerY = swapFlightOriginCenter.y + swapFlightOffsetY.value
                            val widthPx = swapFlightWidth
                            val sectionCount = swapCourse.endSection - swapCourse.startSection + 1
                            val sectionH = gridGeometry?.sectionHeightPx
                                ?: with(density) { displayAppearance.cardHeight.dp.toPx() }
                            val heightPx = sectionCount * sectionH
                            val offsetX = with(density) { (centerX - widthPx / 2f).toDp() }
                            val offsetY = with(density) { (centerY - heightPx / 2f).toDp() }
                            val width = with(density) { widthPx.toDp() }
                            val height = with(density) { heightPx.toDp() }
                            Box(
                                modifier = Modifier
                                    .offset(x = offsetX, y = offsetY)
                                    .size(width = width, height = height)
                                    .padding(vertical = 2.dp)
                                    .graphicsLayer {
                                        scaleX = swapFlightScale.value
                                        scaleY = swapFlightScale.value
                                    }
                            ) {
                                CourseCard(
                                    course = swapCourse,
                                    isCurrentWeek = true,
                                    cardBlurRadius = displayAppearance.cardBlurRadius,
                                    cardRefraction = displayAppearance.cardRefraction,
                                    cardAlpha = displayAppearance.cardAlpha,
                                    cardSurfaceAlpha = displayAppearance.scheduleSurfaceAlpha(isAppDarkTheme()),
                                    // 无壁纸时垫页底色，取值见 floatSolidBacking
                                    solidBackingColor = floatSolidBacking,
                                    cardHeightPerSection = displayAppearance.cardHeight,
                                    cardCornerRadius = displayAppearance.cardCornerRadius,
                                    isTablet = isTablet,
                                    cardContentAlignment = displayAppearance.cardContentAlignment,
                                    cardTextColor = displayAppearance.cardTextColor,
                                    cardTextScale = displayAppearance.cardTextScale,
                                    showClassroom = displayAppearance.showClassroom,
                                    showTeacher = displayAppearance.showTeacher,
                                    disablePadding = true,
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }
            if (shortcutMenuCourse != null || emptyCellMenuTarget != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // 退场动画并行，结束后再清空
                            shortcutMenuVisible = false
                            dismissFloatingCard()
                            coroutineScope.launch {
                                delay(220.milliseconds)
                                shortcutMenuCourse = null
                                emptyCellMenuTarget = null
                            }
                        }
                )
            }
            val activeShortcutCourse = shortcutMenuCourse
            val activeEmptyTarget = emptyCellMenuTarget
            if (activeShortcutCourse != null || activeEmptyTarget != null) {
                val menuItems = if (activeShortcutCourse != null) {
                    buildList {
                        add(
                            ShortcutMenuItem(
                                icon = MiuixIcons.Edit,
                                label = CommonStr.edit(),
                                onClick = {
                                    FeatureLog.course("edit_shortcut")
                                    shortcutMenuVisible = false
                                    dismissFloatingCard()
                                    coroutineScope.launch {
                                        delay(240.milliseconds)
                                        shortcutMenuCourse = null
                                    }
                                    viewModel.showEditDialog(activeShortcutCourse)
                                }
                            )
                        )
                        // 自定义时间课与时间轴视图都不给「复制」：粘贴按节次跨度落位，
                        // 复制它既粘不出正确的时间段、又会让用户以为能复制出一份相同时间段的课。
                        if (shortcutMenuAllowCopy && !activeShortcutCourse.hasValidCustomTime()) {
                            add(
                                ShortcutMenuItem(
                                    icon = MiuixIcons.Copy,
                                    label = CommonStr.copy(),
                                    onClick = {
                                        FeatureLog.course("copy_shortcut")
                                        copiedCourseForPaste = activeShortcutCourse
                                        shortcutMenuVisible = false
                                        dismissFloatingCard()
                                        coroutineScope.launch {
                                            delay(240.milliseconds)
                                            shortcutMenuCourse = null
                                        }
                                        showToast(CommonStr.copied())
                                    }
                                )
                            )
                        }
                        add(
                            ShortcutMenuItem(
                                icon = MiuixIcons.Delete,
                                label = CommonStr.delete(),
                                onClick = {
                                    FeatureLog.course("delete_shortcut")
                                    deleteConfirmCourse = activeShortcutCourse
                                    shortcutMenuVisible = false
                                    dismissFloatingCard()
                                    coroutineScope.launch {
                                        delay(240.milliseconds)
                                        shortcutMenuCourse = null
                                    }
                                    showDeleteConfirmDialog = true
                                }
                            )
                        )
                    }
                } else {
                    val emptyDay = activeEmptyTarget?.columnDay ?: -1
                    val emptySection = activeEmptyTarget?.section ?: -1
                    val emptyAddDay = activeEmptyTarget?.addDay ?: emptyDay
                    val emptyAddWeek = activeEmptyTarget?.addWeek ?: -1
                    buildList {
                        if (copiedCourseForPaste != null && activeEmptyTarget != null) {
                            add(
                                ShortcutMenuItem(
                                    icon = MiuixIcons.Paste,
                                    label = ChromeStr.paste(),
                                    onClick = {
                                        pasteRangeTarget = PasteTarget(
                                            columnDay = emptyDay,
                                            dataDay = emptyAddDay,
                                            section = emptySection,
                                            dataWeek = if (emptyAddWeek > 0) emptyAddWeek else currentViewingWeek,
                                        )
                                        showPasteRangeDialog = true
                                        shortcutMenuVisible = false
                                        coroutineScope.launch {
                                            delay(240.milliseconds)
                                            emptyCellMenuTarget = null
                                        }
                                    }
                                )
                            )
                        }
                        if (activeEmptyTarget != null) {
                            add(
                                ShortcutMenuItem(
                                    icon = MiuixIcons.Add,
                                    label = CommonStr.add(),
                                    iconSize = 24.dp,
                                    onClick = {
                                        val defaultWeeks =
                                            if (emptyAddWeek > 0) setOf(emptyAddWeek) else emptySet()
                                        viewModel.showAddDialog(
                                            emptyAddDay,
                                            emptySection,
                                            null,
                                            defaultWeeks
                                        )
                                        shortcutMenuVisible = false
                                        coroutineScope.launch {
                                            delay(240.milliseconds)
                                            emptyCellMenuTarget = null
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
                ShortcutMenu(
                    show = shortcutMenuVisible,
                    items = menuItems,
                    modifier = Modifier.offset(
                        // 菜单布局四周有 ShortcutMenuShadowPadding 的阴影留白，可见面板
                        // 是布局四边各内收这么多之后的区域：间距与右缘对齐都要按它换算
                        x = with(density) {
                            shortcutMenuPosition.x.toDp() - ShortcutMenuShadowPadding
                        },
                        y = with(density) {
                            // 普通课卡的锚点还含 2dp 格缝，可见间距 8dp 就够；自定义时间/时间轴
                            // 的锚点是实测边界，8dp 看着像贴上去，要 16dp。
                            // 卡片很长时会顶出屏幕，至少留在容器内
                            val measuredAnchor = draggedCardCourse?.let {
                                it.hasValidCustomTime() || scheduleViewMode == ScheduleViewMode.TIMELINE
                            } == true
                            val gap = if (measuredAnchor) 16.dp.toPx() else 8.dp.toPx()
                            val visibleTop = shortcutMenuPosition.y - shortcutMenuSize.height +
                                ShortcutMenuShadowPadding.toPx() - gap
                            maxOf(visibleTop, 12.dp.toPx()).toDp()
                        }
                    ),
                    backdrop = liquidGlassBackdrop,
                    anchorRightPx = shortcutMenuPosition.x + shortcutMenuAnchorWidth,
                    onMeasuredSize = { width, height ->
                        shortcutMenuSize = IntSize(width, height)
                    },
                    onDismiss = {
                        shortcutMenuVisible = false
                        dismissFloatingCard()
                        coroutineScope.launch {
                            delay(220.milliseconds)
                            shortcutMenuCourse = null
                            emptyCellMenuTarget = null
                        }
                    }
                )
            }
            if (showCustomizePage && customizeSnapshot != null) {
                LaunchedEffect(true) {
                    if (showCustomizePage) {
                        onSystemBarsChanged(true, true)
                    } else {
                        onSystemBarsChanged(false, false)
                    }
                }
                val dismissCustomize: () -> Unit = {
                    isApplyingCustomize = false
                    // 取消时丢弃未落盘主题预览
                    pendingScheduleThemeMode = null
                    coroutineScope.launch {
                        blurSnapshotJob?.cancel()
                        // 动画期间保持编辑后实时状态；结束时统一恢复。仅锁定原搭配主题防跟随编辑壁纸
                        captureThemeActive = true
                        captureThemeIsDark = originalWallpaperIsLight?.let { !it }
                        // 复用进入时快照，从开洞放大回全屏盖住回退
                        customizeCoverActive = true
                        customizeCoverScale.stop()
                        customizeCoverScale.snapTo(cutoutMainScale.value)
                        customizeCoverAlpha.stop()
                        customizeCoverAlpha.snapTo(0f)
                        customizeExitScale.stop()
                        customizeExitScale.snapTo(cutoutMainScale.value)
                        customizeExitAlpha.stop()
                        customizeExitAlpha.snapTo(1f)
                        isCustomizeExiting = true
                        onSystemBarsChanged(false, false)
                    }
                }
                val applyCustomize: () -> Unit = {
                    coroutineScope.launch {
                        val bitmap = wallpaperBitmap
                        val isLight =
                            combinations.getOrNull(currentCombinationIndex)?.wallpaperIsLight
                        // 仅内存预览用
                        val capturedSnapshot = captureMainContentBitmap()
                        val saveJob = launch(ioDispatcher) {
                            // 壁纸文件独立落盘（WallpaperStore.save 是 suspend，进不了 batchSave 块）；
                            // 参数快照仍合并为一次磁盘提交
                            if (bitmap != null) {
                                WallpaperStore.save(bitmap)
                            } else {
                                // 删文件，否则旧壁纸会在重启后恢复
                                WallpaperStore.clear()
                            }
                            AppearanceStore.batchSave {
                                AppearanceStore.saveState(
                                    wallpaperOffset.x,
                                    wallpaperOffset.y,
                                    wallpaperScale,
                                    // 与当前显示尺寸绑定，旋转后可按参考屏重映射
                                    latestScreenWPx,
                                    latestScreenHPx
                                )
                                val appearanceToSave = currentAppearance()
                                AppearanceStore.saveCardBlur(
                                    appearanceToSave.cardBlurRadius
                                )
                                AppearanceStore.saveCardAlpha(
                                    appearanceToSave.cardAlpha
                                )
                                AppearanceStore.saveCardSurfaceAlpha(
                                    appearanceToSave.cardSurfaceAlpha
                                )
                                AppearanceStore.saveCardHeight(
                                    appearanceToSave.cardHeight
                                )
                                AppearanceStore.saveCardCornerRadius(
                                    appearanceToSave.cardCornerRadius
                                )
                                AppearanceStore.saveWallpaperBrightness(
                                    appearanceToSave.wallpaperBrightness
                                )
                                AppearanceStore.saveWallpaperIsLight(isLight)
                                AppearanceStore.saveShowBreakDividers(
                                    appearanceToSave.showBreakDividers
                                )
                                AppearanceStore.saveCardContentAlignment(
                                    appearanceToSave.cardContentAlignment
                                )
                                AppearanceStore.saveCardTextColor(
                                    appearanceToSave.cardTextColor
                                )
                                AppearanceStore.saveCardTextScale(
                                    appearanceToSave.cardTextScale
                                )
                                AppearanceStore.saveShowClassroom(
                                    appearanceToSave.showClassroom
                                )
                                AppearanceStore.saveShowTeacher(
                                    appearanceToSave.showTeacher
                                )
                                AppearanceStore.saveCardRefraction(
                                    appearanceToSave.cardRefraction
                                )
                                AppearanceStore.saveCardEdgeThickness(
                                    appearanceToSave.cardEdgeThickness
                                )
                                AppearanceStore.saveCardRim(
                                    appearanceToSave.cardRim
                                )
                                AppearanceStore.saveWallpaperBlur(
                                    appearanceToSave.wallpaperBlur
                                )
                            }
                            // 「应用」才写入偏好
                            pendingScheduleThemeMode?.let { mode ->
                                saveScheduleThemeMode(mode)
                                pendingScheduleThemeMode = null
                            }
                        }
                        // 快照仅存内存；替换前记下旧 snapshot，延迟回收
                        val idx = currentCombinationIndex
                        val replacedSnapshot = combinations.getOrNull(idx)?.snapshot
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(
                                    bitmap = bitmap,
                                    offset = wallpaperOffset,
                                    scale = wallpaperScale,
                                    snapshot = capturedSnapshot,
                                    wallpaperIsLight = isLight
                                )
                            }
                        }
                        if (replacedSnapshot !== capturedSnapshot) {
                            launch {
                                delay(32.milliseconds)
                                recycleSnapshot(
                                    replacedSnapshot,
                                    capturedSnapshot,
                                    customizeSnapshot,
                                    WallpaperStore.currentImageBitmap(),
                                    wallpaperBitmap,
                                )
                            }
                        }
                        savedWallpaperBitmap = bitmap
                        savedWallpaperOffset = wallpaperOffset
                        savedWallpaperScale = wallpaperScale
                        savedAppearance = currentAppearance()
                        saveJob.join()
                        isApplyingCustomize = true
                        // 从开洞大小（0.75）放大回全屏
                        customizeExitScale.snapTo(cutoutMainScale.value)
                        customizeExitAlpha.snapTo(1f)
                        isCustomizeExiting = true
                    }
                }
                // ⚠ 必须 remember：`asImageBitmap()` 每次调用都新建包装对象，不 remember 会让
                // 页面里 `remember(wallpaperBitmap, …)` 之类的 key 每帧都变（白跑一遍计算）。
                val customizeSnapshotImage = customizeSnapshot
                val customizeWallpaperImage = wallpaperBitmap
                // 搭配缩略图：页面只认「两张图」，宿主的 Combination（含 Bitmap，要做
                // remap/recycle/测光）留在这一侧，按需映射。
                val customizeThumbnails = remember(combinations) {
                    combinations.map {
                        CombinationThumbnail(
                            snapshot = it.snapshot,
                            wallpaper = it.bitmap,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = customizeExitAlpha.value
                        }
                ) {
                    CustomizeScheduleScreen(
                        snapshot = customizeSnapshotImage,
                        screenCornerRadius = screenCornerRadius,
                        onDismiss = dismissCustomize,
                        onApply = applyCustomize,
                        onThemeModePreview = { mode -> pendingScheduleThemeMode = mode },
                        onPickWallpaper = { onPickWallpaperImage?.invoke(applyPickedWallpaper) },
                        onClearWallpaper = {
                            wallpaperBitmap = null
                            wallpaperOffset = Offset.Zero
                            wallpaperScale = 1f
                            val idx = currentCombinationIndex
                            if (idx in combinations.indices) {
                                combinations = combinations.toMutableList().also { list ->
                                    list[idx] = list[idx].copy(
                                        bitmap = null,
                                        offset = Offset.Zero,
                                        scale = 1f,
                                        wallpaperIsLight = null
                                    )
                                }
                            }
                        },
                        pendingEnterCutout = pendingEnterCutout,
                        onCutoutEntered = { pendingEnterCutout = false },
                        // 视图模式：外观弹窗里切，切完即落盘（与主题档位一致）
                        scheduleViewMode = scheduleViewMode,
                        onScheduleViewModeChange = { applyScheduleViewMode(it) },
                        combinations = customizeThumbnails,
                        currentCombinationIndex = currentCombinationIndex,
                        isExiting = isCustomizeExiting,
                        isApplying = isApplyingCustomize,
                        isApplyingCustomize = isApplyingCustomize,
                        wallpaperBitmap = customizeWallpaperImage,
                        // 「默认主题」档位读写（Android = ScheduleAppearance）
                        themeMode = { loadScheduleThemeMode() },
                        onThemeModePersist = { saveScheduleThemeMode(it) },
                        wallpaperOffset = wallpaperOffset,
                        wallpaperScale = wallpaperScale,
                        onWallpaperOffsetChange = {
                            wallpaperOffset = it
                            val idx = currentCombinationIndex
                            if (idx in combinations.indices) {
                                combinations = combinations.toMutableList().also { list ->
                                    list[idx] = list[idx].copy(offset = it)
                                }
                            }
                        },
                        onWallpaperScaleChange = {
                            wallpaperScale = it
                            val idx = currentCombinationIndex
                            if (idx in combinations.indices) {
                                combinations = combinations.toMutableList().also { list ->
                                    list[idx] = list[idx].copy(scale = it)
                                }
                            }
                        },
                        onCutoutCenterChange = { xRatio, yRatio ->
                            cutoutCenterXRatio = xRatio
                            cutoutCenterYRatio = yRatio
                        },
                        // 折叠屏竖排底栏会在右侧留一条（~84pt），开洞要整体往左让开
                        rightChromeReserved = (hostNavigation?.rightSafeAreaInset ?: 0.0) > 0.0,
                        sheetOffsetShared = sheetOffsetY,
                        appearance = currentAppearance(),
                        onAppearanceChange = { newAppearance ->
                            applyAppearance(newAppearance)
                        },
                        hasWallpaper = wallpaperBitmap != null,
                        previewPage = if (selectedTab == 1) 1 else 0,
                        onPreviewPageChange = { page ->
                            val target = if (page == 1) 1 else 0
                            if (selectedTab != target) {
                                mainTabProgrammatic = true
                                selectedTab = target
                                coroutineScope.launch {
                                    try {
                                        if (navBarStyle == "rail") {
                                            mainPagerState.scrollToPage(target)
                                        } else {
                                            mainPagerState.animateMainTabTo(target)
                                        }
                                    } finally {
                                        mainTabProgrammatic = false
                                    }
                                }
                            }
                        },
                    )
                }
            }

            if (snapshotCoverBitmap != null) {
                val coverImage = remember(snapshotCoverBitmap) { snapshotCoverBitmap!! }
                Image(
                    bitmap = coverImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // 只作用于快照这一层，避免叠加闪烁
            if (customizeCoverActive && customizeSnapshot != null) {
                val customizeImage = remember(customizeSnapshot) { customizeSnapshot!! }
                Image(
                    bitmap = customizeImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = customizeCoverAlpha.value
                            scaleX = customizeCoverScale.value
                            scaleY = customizeCoverScale.value
                            transformOrigin = TransformOrigin(cutoutCenterXRatio, cutoutCenterYRatio)
                        }
                        .drawWithContent {
                            val path = Path().apply {
                                addScreenCornerRect(
                                    width = size.width,
                                    height = size.height,
                                    corners = screenCornerRadius
                                )
                            }
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    contentScale = ContentScale.Crop
                )
            }

            LaunchedEffect(isCustomizeExiting) {
                if (isCustomizeExiting && customizeSnapshot != null) {
                    coroutineScope {
                        launch {
                            customizeExitScale.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    500,
                                    easing = CustomizePageEasing
                                )
                            )
                        }
                        if (!isApplyingCustomize) {
                            launch {
                                customizeCoverScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        500,
                                        easing = CustomizePageEasing
                                    )
                                )
                            }
                            launch {
                                customizeCoverAlpha.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        400,
                                        easing = CustomizePageEasing
                                    )
                                )
                            }
                        }
                        if (!isApplyingCustomize) {
                            launch {
                                customizeExitAlpha.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        500,
                                        easing = CustomizePageEasing
                                    )
                                )
                            }
                        }
                        launch {
                            cutoutMainScale.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    500,
                                    easing = CustomizePageEasing
                                )
                            )
                        }
                    }
                    isCustomizeExiting = false
                    showCustomizePage = false
                    val exitingSnapshot = customizeSnapshot
                    val exitingCover = snapshotCoverBitmap
                    customizeSnapshot = null
                    snapshotCoverBitmap = null
                    // combinations 可能仍持有同一 snapshot 引用，共用时不 recycle
                    val keptCombSnapshots = combinations.mapNotNull { it.snapshot }.toTypedArray()
                    val keptCombBitmaps = combinations.mapNotNull { it.bitmap }.toTypedArray()
                    recycleSnapshot(
                        exitingSnapshot,
                        exitingCover,
                        WallpaperStore.currentImageBitmap(),
                        wallpaperBitmap,
                        originalWallpaperBitmap,
                        savedWallpaperBitmap,
                        *keptCombSnapshots,
                        *keptCombBitmaps,
                    )
                    recycleSnapshot(
                        exitingCover,
                        exitingSnapshot,
                        WallpaperStore.currentImageBitmap(),
                        wallpaperBitmap,
                        originalWallpaperBitmap,
                        savedWallpaperBitmap,
                        *keptCombSnapshots,
                        *keptCombBitmaps,
                    )
                    customizeCoverActive = false
                    isWindowCutoutActive = false
                    // 原搭配已恢复，forcedDark 自然接管
                    captureThemeActive = false
                    captureThemeIsDark = null
                    if (!isApplyingCustomize) {
                        // 非应用：整体还原，覆盖编辑回调改过的字段
                        wallpaperBitmap = originalWallpaperBitmap
                        wallpaperOffset = originalWallpaperOffset
                        wallpaperScale = originalWallpaperScale
                        currentCombinationIndex = originalCombinationIndex
                        val restoreIdx = originalCombinationIndex
                        val restored = originalCombination
                        if (restoreIdx in combinations.indices && restored != null) {
                            combinations = combinations.toMutableList().also {
                                it[restoreIdx] = restored
                            }
                        }
                    }
                    isApplyingCustomize = false
                    onSystemBarsChanged(false, false)
                }
            }
        }
        // 课程详情页必须盖在弹窗层之上（见 HostOverlayEntry.abovePopups）：它是在课程表弹出的
        // 「课程详情」底部弹窗里点卡片长出来的，弹窗得留在它下面（对齐 Android 的层级）。
        HostedOverlay(
            active = showDetail || detailSnapshot != null,
            abovePopups = true,
        ) {
            if (showDetail) {
                val windowInfo = LocalWindowInfo.current
                val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
                val classStartTime by viewModel.classStartTime.collectAsState()
                val detailSnapshotImage = detailSnapshot
                CourseDetailScreen(
                    courses = detailCourses,
                    cardLeft = detailCardLeft,
                    cardTop = detailCardTop,
                    cardWidth = detailCardWidth,
                    cardHeight = detailCardHeight,
                    screenWidth = windowInfo.containerSize.width.toFloat(),
                    screenHeight = windowInfo.containerSize.height.toFloat(),
                    screenCornerRadius = screenCornerRadius,
                    cardSnapshot = detailSnapshotImage,
                    sectionTimes = sectionTimes,
                    classStartTime = classStartTime,
                    teachingWeekReorganizations = teachingWeekRules,
                    targetWeek = detailTargetWeek,
                    onBackStart = {
                        detailExiting = true
                        coroutineScope.launch {
                            managePageBlurRadius.animateTo(
                                0f,
                                animationSpec = tween(320, easing = OobeCubicOutEasing)
                            )
                        }
                    },
                    onBack = {
                        hiddenCourseIds = emptySet()
                        // ⚠ 详情页延后一帧再消失：morph 结束时卡片已缩回原位，此刻 sheet 里
                        // 的原卡片还没画出来，同帧摘掉详情页就闪一帧空白。
                        coroutineScope.launch {
                            withFrameNanos { }
                            showDetail = false
                            detailExiting = false
                            detailSnapshot = null
                        }
                    }
                )
            }
        }
        // 卡片形变层要压在原生导航栏之下，不能放进弹窗层
        CompositionLocalProvider(
            LocalHostOverlays provides
                    (hostNavigation?.scheduleLibraryOverlays
                        ?: LocalHostOverlays.current),
        ) {
            HostedOverlay(active = showSwitchSchedule || switchAnimRunning || switchOverlayActive) {
                // 进入遮罩归原生控件之下管，永不进弹窗层
                if (isEntryAnimating) {
                    Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer {
                        alpha = switchReturnBgScrim.value * if (isDark) 1f else 0.6f
                    })
                }
                // 切换课表：p 在卡片↔全屏之间形变；进入 p:1→0，退出 p:0→1
                if (showSwitchSchedule) {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // iOS 上 alpha=0 的图层会跳过目标快照录制，所以留 0.001
                                alpha = if (switchPreparingLayer) 0.001f else 1f
                            }
                    ) {
                        val switchShareRepository = remember { CourseRepository() }
                        CompositionLocalProvider(
                            LocalNativeDocumentChrome provides
                                    if (nativeBottomBar && !isTablet) hostNavigation?.scheduleLibraryChrome else null,
                        ) {
                            SwitchScheduleScreen(
                                onBackStart = {
                                    switchExiting = true
                                    switchPendingReverse = false
                                    switchPreparingLayer = false
                                },
                                onBack = { cardLayer, bounds ->
                                    if (!switchExiting || (switchAnimRunning && !switchAnimForward)) {
                                        return@SwitchScheduleScreen
                                    }
                                    val wasForward = switchAnimForward
                                    switchAnimForward = false
                                    switchAnimRunning = true
                                    switchAnimJob?.cancel()
                                    switchAnimJob = coroutineScope.launch {
                                        switchMainLayerJob?.join()
                                        if (scheduleChanged) {
                                            scheduleChanged = false
                                            // 主界面内容变了（换过课表），进入前截的那张整屏图作废：
                                            // 继续用会显示旧课表。清掉后回落到 Compose 图层（底栏会缺，内容是对的）。
                                            val staleShot = mainScreenShot
                                            mainScreenShot = null
                                            recycleSnapshot(staleShot)
                                            switchReloadJob?.join()
                                            switchReloadJob = null
                                            withFrameNanos { }
                                            prepareMainContentLayer()
                                        }
                                        switchOverlayActive = true
                                        switchCardBounds = bounds?.takeIf {
                                            cardLayer != null && it.width > 0f && it.height > 0f
                                        }
                                        switchCardSnapshot = cardLayer
                                        switchCardBitmap = null
                                        // 固化给**下次进入**复用（进入方向不录制任何东西）
                                        if (cardLayer != null) {
                                            launch {
                                                val layerSize = cardLayer.layer.size
                                                val src = cardLayer.bounds ?: Rect(
                                                    0f,
                                                    0f,
                                                    layerSize.width.toFloat(),
                                                    layerSize.height.toFloat(),
                                                )
                                                val cardW = src.width.toInt()
                                                val cardH = src.height.toInt()
                                                val bmp = if (cardW <= 0 || cardH <= 0) {
                                                    null
                                                } else {
                                                    runCatching {
                                                        // 只把**卡片那一块**录进一张卡片大小的小图层，再转位图
                                                        retainedSwitchCardScratchLayer.record(
                                                            density,
                                                            switchLayerLayoutDirection,
                                                            IntSize(cardW, cardH),
                                                        ) {
                                                            translate(-src.left, -src.top) {
                                                                drawLayer(cardLayer.layer)
                                                            }
                                                        }
                                                        retainedSwitchCardScratchLayer.toImageBitmap()
                                                    }.getOrNull()
                                                }
                                                if (bmp != null) {
                                                    retainedSwitchCardBitmap = bmp
                                                    // 落盘：保持到下次切换课表退出为止
                                                    SwitchCardSnapshotStore.save(bmp)
                                                }
                                            }
                                        }
                                        val remainingDuration =
                                            ((1f - switchAnimProgress.value) * 540).toInt()
                                                .coerceAtLeast(1)
                                        switchAnimProgress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(
                                                remainingDuration,
                                                easing = OobeQuartOutEasing
                                            )
                                        )
                                        showSwitchSchedule = false
                                        FeatureLog.t("切换课表", "close", "forward=$wasForward")
                                        switchOverlayActive = false
                                        switchCardSnapshot = null
                                        switchCardBitmap = null
                                        switchCardBounds = null
                                        switchAnimRunning = false
                                        // 退出动画结束：课程表页整屏图（~12MB）用完即弃。
                                        // 先断引用再回收，免得 Compose 还持有它时被释放。
                                        val spentShot = mainScreenShot
                                        mainScreenShot = null
                                        recycleSnapshot(spentShot)
                                    }
                                },
                                onScheduleChanged = {
                                    FeatureLog.t("切换课表", "schedule_changed")
                                    switchReloadJob = viewModel.reloadCourses()
                                    settingsViewModel.refreshSettings()
                                    scheduleChanged = true
                                    // 切换页直接改 repository，须把 ScheduleViewModel 拉回一致；摘要放 IO
                                    coroutineScope.launch(ioDispatcher) {
                                        scheduleViewModel.refreshScheduleList()
                                    }
                                },
                                onScreenReady = { cardBounds ->
                                    if (!switchPendingReverse || switchExiting) {
                                        FeatureLog.t(
                                            "切换课表",
                                            "screen_ready_stale",
                                            "pending=false"
                                        )
                                    } else {
                                        switchPendingReverse = false
                                        switchAnimForward = true
                                        switchAnimRunning = true
                                        FeatureLog.t(
                                            "切换课表",
                                            "anim_enter_start",
                                            "bounds=$cardBounds"
                                        )
                                        switchAnimJob?.cancel()
                                        val cardBoundsInScreen = Rect(
                                            left = switchContentRootX + cardBounds.left,
                                            top = switchContentRootY + cardBounds.top,
                                            right = switchContentRootX + cardBounds.right,
                                            bottom = switchContentRootY + cardBounds.bottom
                                        )
                                        switchAnimJob = coroutineScope.launch {
                                            switchMainLayerJob?.join()
                                            switchAnimProgress.snapTo(1f)
                                            switchReturnBgScrim.snapTo(0.4f)
                                            switchPreparingLayer = false
                                            switchOverlayActive = true
                                            switchCardBounds = cardBoundsInScreen.takeIf {
                                                it.width > 0f && it.height > 0f
                                            }
                                            switchCardSnapshot = null
                                            // 复用上次退出固化的卡片位图 —— 进入零录制。
                                            // 卡片区域就是课表卡片本身（不含底栏），所以这张图不用管原生 chrome；
                                            // 需要底栏的是下面第三层铺满全屏的「主内容图」。
                                            switchCardBitmap = retainedSwitchCardBitmap
                                            val remainingDuration = 320
                                            val morphExitEase =
                                                CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)
                                            launch {
                                                switchReturnBgScrim.animateTo(
                                                    0f,
                                                    animationSpec = tween(
                                                        remainingDuration,
                                                        easing = morphExitEase
                                                    )
                                                )
                                            }
                                            switchAnimProgress.animateTo(
                                                0f,
                                                animationSpec = tween(
                                                    remainingDuration,
                                                    easing = OobeCubicOutEasing
                                                )
                                            )
                                            FeatureLog.t(
                                                "切换课表",
                                                "anim_enter_end",
                                                "p=${switchAnimProgress.value}"
                                            )
                                            switchOverlayActive = false
                                            switchCardSnapshot = null
                                            switchCardBitmap = null
                                            switchCardBounds = null
                                            switchAnimRunning = false
                                            // ⚠ mainScreenShot 这里**不能释放**：退出方向
                                            // （切换页 → 主界面）的「主内容层」也要用它，
                                            // 否则返回时的快照又没有顶栏/底栏。退出动画结束时才回收。
                                        }
                                    }
                                },
                                onContentOffset = { x, y ->
                                    switchContentRootX = x
                                    switchContentRootY = y
                                },
                                pageScale = 1f,
                                onShareSchedule = { name, changed ->
                                    onShareSchedule?.invoke(name, changed)
                                },
                                isScheduleShareable = { name ->
                                    buildShareScheduleMap(switchShareRepository, name) != null
                                },
                                initialScheduleNames = scheduleViewModel.scheduleNames.collectAsState().value,
                                initialCurrentScheduleId = scheduleViewModel.currentScheduleName.collectAsState().value,
                                initialScheduleSummaries = scheduleViewModel.scheduleSummaries.collectAsState().value
                            )
                        }
                    }
                    // 在「保留的主内容层」与目标卡片之间做形变
                    SwitchMorphOverlay(
                        active = switchOverlayActive,
                        progress = switchAnimProgress,
                        entering = switchAnimForward,
                        cardBounds = switchCardBounds,
                        cardSnapshot = switchCardSnapshot,
                        cardBitmap = switchCardBitmap,
                        // 课程表页整屏原生图（含顶栏/底栏）。**进出两个方向都用**：
                        // 上半屏仍由 Compose 图层画（顶栏要 Compose 那版），下半屏换成这张图 ——
                        // 原生底栏不管横跨底部还是右下角竖排，都落在下半这块里。
                        nativeScreenShot = mainScreenShot,
                        mainLayer = screenGraphicsLayer,
                        screenCornerRadius = screenCornerRadius,
                        isDark = isDark,
                    )
                }

            }
        }
        HostedOverlay(active = showShiftLoading || shiftLoadingActive) {
            ShiftLoadingOverlay(
                show = showShiftLoading,
                onShiftReady = {
                    if (isExitingShift) {
                        FeatureLog.shift("exit_confirmed")
                        shiftViewModel.exitShiftMode()
                        selectedTab = 0
                        selectedTab = 0
                        coroutineScope.launch { mainPagerState.scrollToPage(0) }
                    } else {
                        FeatureLog.shift("enter_confirmed")
                        shiftViewModel.enterShiftMode()
                    }
                },
                onHide = { showShiftLoading = false },
                onActivityChanged = { shiftLoadingActive = it },
            )
        }
    }
}
