/** 小组件使用引导页面 - Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.WIDGET_CARD_ALPHA_WITH_WALLPAPER
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.WidgetAppearance
import com.haooz.chedule.data.WidgetWallpaperCrop
import com.haooz.chedule.data.WidgetWallpaperStore
import com.haooz.chedule.data.appearance.ThemeMode
import com.haooz.chedule.data.calculateWidgetCropRect
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.HorizontalPageSwitch
import com.haooz.chedule.ui.components.PageSwitchState
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.rememberPageSwitchState
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.isIosPlatform
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_widget_clock
import com.haooz.chedule.uishared.resources.ic_widget_location
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 预览小部件的**固定物理宽度**。
 *
 * ⚠ 绝不允许 `fillMaxWidth()`：卡内字号是固定 sp、内边距是固定 dp，宽度一跟着容器走，
 * 平板上卡片面积按平方放大而文字不变 ⇒ 「面积巨大、文字占比完全不对」。
 * 所有设备都取同一个 dp 宽度，只有可用宽度不足时才收敛（防止小屏溢出）。
 * 340dp 与真机 4×2 组件同量级，也约等于手机端原先的观感。
 */
private val WidgetPreviewMaxWidth = 340.dp

/** 4×2 组件预览统一宽高比，锁定后不随设备屏宽变化 */
private const val WidgetPreviewRatio4x2 = 2.15f

/** 说明文字 / 边距选择器与预览卡共用一条列宽，平板不会被单独拉宽 */
private val WidgetIntroFeatureInset = 18.dp

/** 预览卡上下的内边距（dp） */
private val WidgetPreviewVerticalPadding = 12.dp

/** 翻转卡最大模糊半径（dp），翻转进度越过起始阈值后 0 → 此值线性渐入 */
private val WidgetPreviewFlipBlurRadius = 8.dp

/** 模糊起始阈值：翻转进度（0–1）小于此值完全清晰，越过后才开始加模糊 */
private const val WidgetPreviewBlurStartProgress = 0.12f

/** 模糊出血（dp）：RenderEffect 会把图层强制裁到自身边界 */
private val WidgetPreviewBlurBleed = 8.dp

/**
 * @param onRequestWidgetRefresh 小组件边距改完后通知宿主刷新已添加的小组件。
 *   Android uses AppWidgetManager; iOS uses a shared WidgetKit snapshot.
 *   Platform-specific refresh behavior is supplied by the host.
 *   与 `SchoolSelectionScreen(onAdapterSelected)` / `CourseTimeSettingsScreen(onEditRoutine)`
 *   同一套路：**平台专有能力留在宿主，页面本体保持平台中立**。
 */
@Suppress("RememberReturnType")
@Composable
fun WidgetIntroScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    onRequestWidgetRefresh: () -> Unit = {},
    /** 点「自定义」：把当前页小部件的屏幕矩形交给宿主，浮层从原位起步 */
    onCustomizeRequest: (WidgetCustomizeRequest) -> Unit = {},
    /**
     * 浮层正在编辑的页：那一页的原预览要让位（alpha 0）。
     * 浮层自己画了同一张小部件，两份同时在场就是「双重」。
     * 用 alpha 而不是不挂载，矩形（onGloballyPositioned）才不会失效。
     */
    hiddenPage: Int? = null,
    /**
     * 壁纸变更计数：宿主在「应用」后自增，逼这一页重读壁纸。
     * 不直接读 [WidgetWallpaperStore.revision] 是因为这条链路必须确定地触发重组 ——
     * 参数变了 Compose 一定会重跑，靠全局 state 的失效时机则会拖到下次进页面。
     */
    wallpaperRevision: Int = 0,
    /** 「自定义」入口（换壁纸浮层）。鸿蒙卡片是 ArkTS 渲染，改不了这份预览，直接隐藏 */
    showCustomizeEntry: Boolean = true,
    /** 底部「边距样式」选择器：只对安卓 RemoteViews 生效 */
    showPaddingSelector: Boolean = true,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    // 今明课程是 Android 桌面专属组件，iOS 端不展示该预览页
    val pageCount = if (isIosPlatform) 2 else 3
    // 每页小部件本体的屏幕矩形，供浮层从原位起步。
    // 刻意不用 snapshot state：滚动时矩形每帧都变，进 state 会让入口按钮逐帧重组，
    // 而按钮只在点击那一刻需要最新值。
    val widgetBounds = remember { mutableMapOf<Int, Rect>() }

    // 各页壁纸：浮层里「应用」后靠 wallpaperRevision 触发重读
    val widgetWallpapers = remember { mutableStateMapOf<Int, WidgetWallpaper?>() }
    val widgetThemeModes = remember { mutableStateMapOf<Int, ThemeMode>() }
    val widgetAppearances = remember { mutableStateMapOf<Int, WidgetAppearance>() }
    // 组合期同步刷一遍：应用后 save 已经把新位图放进缓存，这一帧就能拿到。
    // 交给下面的 LaunchedEffect 会晚一帧 —— 表现是「退出浮层时先闪一下旧壁纸」。
    remember(wallpaperRevision, pageCount) {
        for (page in 0 until pageCount) {
            val key = widgetWallpaperKey(page)
            widgetWallpapers[page] = WidgetWallpaperStore.cached(key)?.let { cached ->
                WidgetWallpaper(bitmap = cached, crop = WidgetWallpaperStore.loadCrop(key))
            }
            widgetThemeModes[page] = WidgetWallpaperStore.loadThemeMode(key)
            widgetAppearances[page] = WidgetWallpaperStore.loadAppearance(key)
        }
    }
    LaunchedEffect(wallpaperRevision, pageCount) {
        // 冷启动才需要读盘：缓存命中的那几页上面已经填好了
        for (page in 0 until pageCount) {
            val key = widgetWallpaperKey(page)
            if (WidgetWallpaperStore.cached(key) != null) continue
            if (!WidgetWallpaperStore.exists(key)) {
                widgetWallpapers[page] = null
                continue
            }
            val bitmap = WidgetWallpaperStore.load(key, forceReload = true)
            widgetWallpapers[page] = bitmap?.let { bmp ->
                WidgetWallpaper(bitmap = bmp, crop = WidgetWallpaperStore.loadCrop(key))
            }
            widgetThemeModes[page] = WidgetWallpaperStore.loadThemeMode(key)
        }
    }
    val appIsDark = isAppDarkTheme()

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 一列内容 = 小部件预览宽度。用父容器实测宽度而不是屏幕宽度：
            // 平板设置页右栏 / 分屏窗口都比整屏窄，按屏宽算会溢出。
            val availableWidth = maxWidth - tabletHorizontalPadding * 2f
            val previewWidth = if (availableWidth < WidgetPreviewMaxWidth) {
                availableWidth.coerceAtLeast(160.dp)
            } else {
                WidgetPreviewMaxWidth
            }
            val previewHeight = previewWidth / WidgetPreviewRatio4x2
            // 2×2 正方形边长 = 4×2 槽位高度，三页 pager 高度恒定
            val smallWidgetSize = previewHeight
            val smallPage = if (isIosPlatform) 1 else 2

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                    .collapsibleTopInset(scrollBehavior)
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                    )
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight + pageTopExtra(isTablet, hasSmallTitle = false)))

                val pageState = rememberPageSwitchState(0)

                HorizontalPageSwitch(
                    state = pageState,
                    pageCount = pageCount,
                    modifier = Modifier.fillMaxWidth(),
                    beyondPageCount = 1,
                ) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = tabletHorizontalPadding - WidgetPreviewBlurBleed,
                                vertical = WidgetPreviewVerticalPadding - WidgetPreviewBlurBleed
                            ),
                        // 限宽槽居中：手机、平板、桌面窗口里小部件都是同一块 340dp
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                // 槽尺寸固定（含模糊出血），翻页旋转轴落在小部件自身中心，
                                // 而不是整页宽的中心 —— 平板上不会绕着一条看不见的远轴翻
                                .width(previewWidth + WidgetPreviewBlurBleed * 2f)
                                .height(previewHeight + WidgetPreviewBlurBleed * 2f)
                                .graphicsLayer {
                                    val off = (pageState.currentPage - page) + pageState.offsetFraction
                                    val absOff = min(abs(off), 1f)
                                    cameraDistance = 48f
                                    rotationY = -off * 56f
                                    val scale = 1f - 0.2f * absOff
                                    scaleX = scale
                                    scaleY = scale
                                    val blurProgress = ((absOff - WidgetPreviewBlurStartProgress) /
                                        (1f - WidgetPreviewBlurStartProgress)).coerceIn(0f, 1f)
                                    renderEffect = if (blurProgress > 0.01f) {
                                        val blur = blurProgress * WidgetPreviewFlipBlurRadius.toPx()
                                        BlurEffect(blur, blur)
                                    } else null
                                }
                                // 出血必须在 graphicsLayer 内侧：扩大离屏缓冲的采样范围，
                                // 外层槽内边距已等量减回，静止布局不变
                                .padding(WidgetPreviewBlurBleed),
                            contentAlignment = Alignment.Center
                        ) {
                            val pageWallpaper = widgetWallpapers[page]
                            val pageMode = widgetThemeModes[page] ?: ThemeMode.FOLLOW_WALLPAPER
                            val bodySize = if (page == smallPage) {
                                Modifier.size(smallWidgetSize)
                            } else {
                                Modifier.size(previewWidth, previewHeight)
                            }
                            WidgetPreviewContent(
                                page = page,
                                // modifier 就是「小部件本体」：2×2 页拿到的是正方形，
                                // 浮层从这块矩形起步才不会被压成 2.15:1
                                modifier = bodySize
                                    .onGloballyPositioned { coords ->
                                        widgetBounds[page] = coords.boundsInRoot()
                                    }
                                    .graphicsLayer { alpha = if (page == hiddenPage) 0f else 1f },
                                wallpaper = pageWallpaper,
                                appearance = widgetAppearances[page] ?: WidgetAppearance(),
                                isDark = remember(pageWallpaper, pageMode, appIsDark) {
                                    resolveWidgetDark(pageWallpaper?.bitmap, pageMode, appIsDark)
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                // 页面指示器（会跟手跑的胶囊，点一下也能跳页）
                WidgetPagerIndicator(pageCount = pageCount, pageState = pageState)

                Spacer(modifier = Modifier.height(16.dp))

                // 自定义入口：跟着「当前这一页」的小部件走，所以矩形也取当前页的
                if (showCustomizeEntry) {
                    WidgetCustomizeEntry(
                        onClick = {
                            val page = pageState.currentPage
                            val bounds = widgetBounds[page]
                            if (bounds != null) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onCustomizeRequest(WidgetCustomizeRequest(page = page, bounds = bounds))
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .width(previewWidth)
                        .padding(horizontal = WidgetIntroFeatureInset),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    FeatureItem(AboutStr.featureTodayTitle(), AboutStr.featureTodayDesc())
                    FeatureItem(AboutStr.featureTomorrowTitle(), AboutStr.featureTomorrowDesc())
                    FeatureItem(
                        AboutStr.featureOngoingTitle(),
                        if (isIosPlatform) AboutStr.featureOngoingDescIos() else AboutStr.featureOngoingDescAndroid()
                    )
                    FeatureItem(AboutStr.featureStatusTitle(), AboutStr.featureStatusDesc())
                    if (isIosPlatform) {
                        FeatureItem(AboutStr.featureAppearanceTitle(), AboutStr.featureAppearanceDesc())
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!isIosPlatform && showPaddingSelector) {
                    val repository = remember { CourseRepository() }
                    var paddingMode by remember { mutableIntStateOf(repository.getWidgetPaddingMode()) }
                    Box(modifier = Modifier.width(previewWidth)) {
                        WidgetPaddingSelector(
                            selectedMode = paddingMode,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            onSelect = { mode ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                paddingMode = mode
                                repository.setWidgetPaddingMode(mode)
                                onRequestWidgetRefresh()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(120.dp))
            }

            // 底部「添加到桌面」已上提到 WidgetIntroActivity
        }
    }
}

/**
 * 翻页指示器：三颗里有一颗会「跑」。
 *
 * 位置直接取 `currentPage + currentPageOffsetFraction`，手指拖到哪它就跟到哪；
 * 松手后由 pager 自己的吸附动画把它带整 —— 视觉上的「吸住」就是这两段接起来的，
 * 不需要另外写 spring。宽度、颜色按与它重合的程度逐帧插值，所以是「拉长过去」而不是跳格。
 *
 * ⚠ 全程在 **draw 阶段** 读 pager 状态：拖动时只脏这一条 Canvas，不重组整页
 * （这页的节点数不便宜，见 topics/pager-perf.md 的口径）。
 */
@Composable
private fun WidgetPagerIndicator(
    pageCount: Int,
    pageState: PageSwitchState,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val activeColor = MiuixTheme.colorScheme.primary
    val inactiveColor = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.12f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(pageCount) {
                detectTapGestures { offset ->
                    val dotPx = IndicatorDot.toPx()
                    val gapPx = IndicatorGap.toPx()
                    val stretchPx = IndicatorStretch.toPx()
                    val position = pagerPosition(pageState, pageCount)
                    var left = (size.width - indicatorWidth(pageCount, position, dotPx, gapPx, stretchPx)) / 2f
                    for (index in 0 until pageCount) {
                        val width = indicatorSlotWidth(index, position, dotPx, stretchPx)
                        if (offset.x >= left && offset.x <= left + width) {
                            if (index != pageState.currentPage) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                scope.launch { pageState.animateTo(index) }
                            }
                            break
                        }
                        left += width + gapPx
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotPx = IndicatorDot.toPx()
            val gapPx = IndicatorGap.toPx()
            val stretchPx = IndicatorStretch.toPx()
            val position = pagerPosition(pageState, pageCount)
            var left = (size.width - indicatorWidth(pageCount, position, dotPx, gapPx, stretchPx)) / 2f
            val centerY = size.height / 2f
            for (index in 0 until pageCount) {
                val weight = indicatorSlotWeight(index, position)
                val width = dotPx + stretchPx * weight
                drawRoundRect(
                    // 只跟「离它多近」有关：路过时颜色一路染过去，停下时正好整颗点亮
                    color = lerp(inactiveColor, activeColor, weight),
                    topLeft = Offset(left, centerY - dotPx / 2f),
                    size = Size(width, dotPx),
                    cornerRadius = CornerRadius(dotPx / 2f),
                )
                left += width + gapPx
            }
        }
    }
}

/** 未选中时的圆点直径 */
private val IndicatorDot = 6.dp

/** 相邻两颗的间隙 */
private val IndicatorGap = 6.dp

/** 选中时额外抻出的长度（加上 [IndicatorDot] 就是胶囊长度） */
private val IndicatorStretch = 12.dp

/** 跟手位置：整页为整数，拖动过程中是小数 */
private fun pagerPosition(pageState: PageSwitchState, pageCount: Int): Float =
    (pageState.currentPage + pageState.offsetFraction)
        .coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())

/** 第 index 颗与当前位置的「重合度」：0 = 完全没挨着，1 = 正好停在它上面 */
private fun indicatorSlotWeight(index: Int, position: Float): Float =
    (1f - abs(position - index)).coerceIn(0f, 1f)

private fun indicatorSlotWidth(
    index: Int,
    position: Float,
    dotPx: Float,
    stretchPx: Float,
): Float = dotPx + stretchPx * indicatorSlotWeight(index, position)

/** 所有圆点 + 间隙的总宽（用来把整排居中） */
private fun indicatorWidth(
    pageCount: Int,
    position: Float,
    dotPx: Float,
    gapPx: Float,
    stretchPx: Float,
): Float {
    if (pageCount <= 0) return 0f
    var total = gapPx * (pageCount - 1)
    for (index in 0 until pageCount) {
        total += indicatorSlotWidth(index, position, dotPx, stretchPx)
    }
    return total
}

@Composable
private fun WidgetCustomizeEntry(onClick: () -> Unit) {
    val contentColor = MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(ContinuousCapsule())
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = MiuixIcons.GridView,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor
        )
        Text(
            text = SchedStr.customize(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

/**
 * 三种预览页的统一入口：分页器与自定义浮层共用同一份内容，浮层里的小部件才和页面里一模一样。
 *
 * [modifier] 必须是**小部件本体**的尺寸（页面给固定 dp，浮层给 `fillMaxSize()`）。
 * 尺寸只由调用方决定，这里不再自己 `fillMaxWidth` / `aspectRatio` —— 那是平板把卡片
 * 拉成整屏宽的根源。槽位、居中、翻页出血都属于页面布局，不进这份内容。
 */
@Composable
internal fun WidgetPreviewContent(
    page: Int,
    modifier: Modifier = Modifier,
    wallpaper: WidgetWallpaper? = null,
    appearance: WidgetAppearance = WidgetAppearance(),
    isDark: Boolean = isAppDarkTheme(),
) {
    // 字体缩放靠改 fontScale：一处生效，不用逐个 sp 去乘
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * appearance.textScale)
    ) {
        when (page) {
            0 -> WidgetPreviewToday(modifier, wallpaper, appearance, isDark)
            1 -> if (isIosPlatform) {
                WidgetPreviewSmall2x2(modifier, wallpaper, appearance, isDark)
            } else {
                WidgetPreviewTodayTomorrow(modifier, wallpaper, appearance, isDark)
            }

            else -> WidgetPreviewSmall2x2(modifier, wallpaper, appearance, isDark)
        }
    }
}

/** 小部件背景图 + 它当前的取景 */
internal data class WidgetWallpaper(
    val bitmap: ImageBitmap,
    val crop: WidgetWallpaperCrop,
)

/**
 * 解析小部件该用深色还是浅色。
 *
 * 「跟随壁纸」时按**照片本身的明暗**锁死，不跟系统 —— 组件贴在桌面上，
 * 周围是壁纸而不是 App 界面，跟着系统走会在深色系统 + 浅色照片时出现白字压在亮部上。
 * 没壁纸可跟随时退回应用主题。
 */
internal fun resolveWidgetDark(
    wallpaper: ImageBitmap?,
    mode: ThemeMode,
    appDark: Boolean,
): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.FOLLOW_WALLPAPER -> WallpaperStore.measureLightness(wallpaper)?.let { !it } ?: appDark
    ThemeMode.FOLLOW_APP -> appDark
}



/** 卡片磨砂要用到的现场：采样层 + 外观参数 */
private class WidgetGlassSpec(
    val hasWallpaper: Boolean,
)

private val LocalWidgetGlass = compositionLocalOf<WidgetGlassSpec?> { null }

/**
 * 小部件根节点：壁纸单独录进采样层，卡片从层外采「自己背后那块」。
 *
 * ⚠ 采样节点必须在被录的子树**外面**（与课表页玻璃按钮同一套路）：
 * 录进去的话卡片会采到自己，模糊出来是一团糊。
 */
@Composable
private fun WidgetGlassHost(
    wallpaper: WidgetWallpaper?,
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    CompositionLocalProvider(
        LocalWidgetGlass provides WidgetGlassSpec(
            hasWallpaper = wallpaper != null,
        ),
    ) {
        Box(
            modifier = modifier.onGloballyPositioned { rootCoordinates = it }
        ) {
            Box(modifier = Modifier.matchParentSize()) { background() }
            content()
        }
    }
}

/**
 * 课程卡片的背景。
 *
 * 卡片只负责画自己的底色 + 叠层；「卡片背后那块壁纸」的磨砂由 [WidgetPreviewBackground]
 * 统一处理 —— 这里只把自己的圆角矩形登记过去。
 *
 * @param tint 额外叠加层（「正在上课」的淡蓝），**不参与**不透明度调节
 */
@Composable
private fun WidgetCardGlass(
    cardColor: ComposeColor,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    tint: ComposeColor? = null,
) {
    val spec = LocalWidgetGlass.current
    // 有壁纸就固定半透明，没壁纸纯色（真机侧也只能是固定值，这里保持一致）
    val alpha = if (spec?.hasWallpaper == true) WIDGET_CARD_ALPHA_WITH_WALLPAPER else 1f
    val shape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius) }
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(cardColor.copy(alpha = alpha))
                if (tint != null) drawRect(tint)
            }
    )
}

/**
 * 小部件背景：有壁纸就画壁纸，没壁纸就是原来那块纯色。
 *
 * 壁纸上一律不叠遮罩 —— 加一层主题底色就会变成「照片上蒙了块玻璃」，
 * 可读性交给 [resolveWidgetDark] 决定深浅来解决。
 *
 */
@Composable
private fun WidgetPreviewBackground(
    bgColor: ComposeColor,
    wallpaper: WidgetWallpaper?,
    wallpaperBlur: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (wallpaper == null) {
            Box(modifier = Modifier.fillMaxSize().background(bgColor))
        } else {
            WidgetWallpaperLayer(bgColor, wallpaper, wallpaperBlur)
        }
    }
}

/**
 * 一层壁纸。
 *
 * 模糊时**先垫一层主题底色再模糊**：模糊会采样边界外的像素，边界外是透明的话，
 * 边缘就会淡掉、露出后面的白（表现是「壁纸外的白色被糊进壁纸」）。
 */
@Composable
private fun WidgetWallpaperLayer(
    bgColor: ComposeColor,
    wallpaper: WidgetWallpaper,
    blurRadius: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (blurRadius > 0.01f) Modifier.blur(blurRadius.dp) else Modifier)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor))
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 与参考实现同一套：按容器尺寸现算裁剪区，不拉伸
            val rect = calculateWidgetCropRect(
                bitmapW = wallpaper.bitmap.width,
                bitmapH = wallpaper.bitmap.height,
                containerW = size.width,
                containerH = size.height,
                crop = wallpaper.crop,
            )
            if (rect.width > 0f && rect.height > 0f) {
                drawImage(
                    image = wallpaper.bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(wallpaper.bitmap.width, wallpaper.bitmap.height),
                    dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                    dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}

@Composable
private fun WidgetPreviewToday(
    modifier: Modifier = Modifier,
    wallpaper: WidgetWallpaper? = null,
    appearance: WidgetAppearance = WidgetAppearance(),
    isDark: Boolean = isAppDarkTheme(),
) {
    val bgColor = if (isDark) ComposeColor(0xFF1A1A1A) else ComposeColor(0xFFF7F7F7)
    val cardBg = if (isDark) ComposeColor(0xFF262626) else ComposeColor.White
    val borderColor = if (isDark) ComposeColor(0xFF2A2A2A) else ComposeColor(0x0D000000)
    val titleColor = if (isDark) ComposeColor(0x77FFFFFF) else ComposeColor(0x77000000)
    val nameColor = if (isDark) ComposeColor(0xFFEEEEEE) else ComposeColor(0xFF1A1A1A)
    val infoColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)
    val timeStartColor = if (isDark) ComposeColor(0x88FFFFFF) else ComposeColor(0x88000000)
    val timeEndColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)
    val nowColor = if (isDark) ComposeColor(0xFF42A5F5) else ComposeColor(0xFF2196F3)
    // 「正在上课」= 卡片底色 + 10% 蓝叠加层。叠加层单独画，所以调卡片不透明度时它不会跟着变淡
    val activeTint = ComposeColor(0x1A2196F3)

    Card(
        cornerRadius = 22.dp,
        modifier = modifier.border(1.dp, borderColor, com.haooz.softglass.shape.ContinuousRoundedRectangle(22.dp)),
        insideMargin = PaddingValues(0.dp)
    ) {
        WidgetGlassHost(
            wallpaper = wallpaper,
            modifier = Modifier.fillMaxSize(),
            background = {
                WidgetPreviewBackground(
                    bgColor = bgColor,
                    wallpaper = wallpaper,
                    // 卡片磨砂并进背景模糊：卡片背后就是这张壁纸，一起糊即可，
                    // 逐卡片取背后在预览里做不稳（采样层在 pager 里会拿到错帧）
                    wallpaperBlur = appearance.wallpaperBlur,
                    modifier = Modifier.fillMaxSize()
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(AboutStr.previewHeaderToday(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif, modifier = Modifier.weight(1f))
                    Text(CommonStr.weekN(10), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif)
                }

                CourseCardPreview("08:00", "09:35", AboutStr.previewCourseMath(), AboutStr.previewInfoMath(), ComposeColor(0xFF4CAF50), null, cardBg, nameColor, infoColor, timeStartColor, timeEndColor, nowColor, Modifier.fillMaxWidth().weight(1f))
                Spacer(modifier = Modifier.height(8.dp))
                CourseCardPreview("14:00", "15:35", AboutStr.previewCourseEnglish(), AboutStr.previewInfoEnglish(), ComposeColor(0xFF2196F3), AboutStr.previewEndingSoon(), cardBg, nameColor, infoColor, timeStartColor, timeEndColor, nowColor, Modifier.fillMaxWidth().weight(1f), tint = activeTint)
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun WidgetPreviewSmall2x2(
    modifier: Modifier = Modifier,
    wallpaper: WidgetWallpaper? = null,
    appearance: WidgetAppearance = WidgetAppearance(),
    isDark: Boolean = isAppDarkTheme(),
) {
    val bgColor = if (isDark) ComposeColor(0xFF1A1A1A) else ComposeColor.White
    val borderColor = if (isDark) ComposeColor(0xFF2A2A2A) else ComposeColor(0x0D000000)
    val titleColor = if (isDark) ComposeColor(0x99FFFFFF) else ComposeColor(0x99000000)
    val nameColor = if (isDark) ComposeColor(0xFFEEEEEE) else ComposeColor(0xFF1A1A1A)
    val infoColor = if (isDark) ComposeColor(0x88FFFFFF) else ComposeColor(0x88000000)
    val remainingColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)

    // 根节点就是小部件本体：尺寸全由调用方给（页面给正方形、浮层给同一块矩形）。
    // 不再套一层 2.15:1 的槽位 —— 那会让 2×2 的浮层被压成横条。
    WidgetGlassHost(
        wallpaper = wallpaper,
        modifier = modifier
            .border(1.dp, borderColor, com.haooz.softglass.shape.ContinuousRoundedRectangle(22.dp))
            .clip(com.haooz.softglass.shape.ContinuousRoundedRectangle(22.dp)),
        background = {
            WidgetPreviewBackground(
                bgColor = bgColor,
                wallpaper = wallpaper,
                // 卡片磨砂并进背景模糊：卡片背后就是这张壁纸，一起糊即可，
                // 逐卡片取背后在预览里做不稳（采样层在 pager 里会拿到错帧）
                wallpaperBlur = appearance.wallpaperBlur,
                modifier = Modifier.fillMaxSize()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title: 14sp bold
                Text(CommonStr.weekdayFri(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif)
                Spacer(modifier = Modifier.weight(1f))
                // Week: 14sp bold
                Text(CommonStr.weekN(10), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif)
            }

            // Course content: gravity=bottom, weight=1
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Course name: 17sp, paddingStart=4dp, paddingTop=8dp, paddingBottom=4dp, marginBottom=4dp
                Text(
                    AboutStr.previewCourseMath(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = nameColor,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )

                // Time row: marginStart=4dp, marginBottom=2dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon: 12dp, marginEnd=6dp
                    androidx.compose.foundation.Image(
                        painter = painterResource(Res.drawable.ic_widget_clock),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        alpha = 0.5f
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Text: 14sp
                    Text("08:00 - 09:35", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif)
                }

                // Location row: marginStart=4dp, marginBottom=16dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon: 12dp, marginEnd=6dp
                    androidx.compose.foundation.Image(
                        painter = painterResource(Res.drawable.ic_widget_location),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        alpha = 0.5f
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Text: 14sp
                    Text("A301", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif, maxLines = 1)
                }
            }

            // Remaining info: marginStart=4dp, text 12sp
            Row(
                modifier = Modifier.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    AboutStr.previewRemainingClasses(),
                    fontSize = 12.sp,
                    color = remainingColor,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(ComposeColor(0xFF4CAF50), ContinuousCapsule())
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(ComposeColor(0xFF2196F3), ContinuousCapsule())
                )
            }
        }
    }
}

@Composable
private fun WidgetPreviewTodayTomorrow(
    modifier: Modifier = Modifier,
    wallpaper: WidgetWallpaper? = null,
    appearance: WidgetAppearance = WidgetAppearance(),
    isDark: Boolean = isAppDarkTheme(),
) {
    val bgColor = if (isDark) ComposeColor(0xFF1A1A1A) else ComposeColor(0xFFF7F7F7)
    val cardBg = if (isDark) ComposeColor(0xFF262626) else ComposeColor.White
    val borderColor = if (isDark) ComposeColor(0xFF2A2A2A) else ComposeColor(0x0D000000)
    val titleColor = if (isDark) ComposeColor(0x77FFFFFF) else ComposeColor(0x77000000)
    val nameColor = if (isDark) ComposeColor(0xFFEEEEEE) else ComposeColor(0xFF1A1A1A)
    val timeColor = if (isDark) ComposeColor(0x88FFFFFF) else ComposeColor(0x88000000)
    val infoColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)

    Card(
        cornerRadius = 22.dp,
        modifier = modifier.border(1.dp, borderColor, com.haooz.softglass.shape.ContinuousRoundedRectangle(22.dp)),
        insideMargin = PaddingValues(0.dp)
    ) {
        WidgetGlassHost(
            wallpaper = wallpaper,
            modifier = Modifier.fillMaxSize(),
            background = {
                WidgetPreviewBackground(
                    bgColor = bgColor,
                    wallpaper = wallpaper,
                    // 卡片磨砂并进背景模糊：卡片背后就是这张壁纸，一起糊即可，
                    // 逐卡片取背后在预览里做不稳（采样层在 pager 里会拿到错帧）
                    wallpaperBlur = appearance.wallpaperBlur,
                    modifier = Modifier.fillMaxSize()
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 9.dp, end = 9.dp, top = 9.dp, bottom = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 与真实组件一致：两个各占一半的表头槽位，明天槽位左移 4dp 起点
                    Text(AboutStr.previewHeaderToday(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(AboutStr.previewHeaderTomorrow(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 4.dp))
                }

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        TodayTomorrowCardPreview(AboutStr.previewCourseMath(), "08:00-09:35", AboutStr.previewInfoMathTeacher(), ComposeColor(0xFF4CAF50), cardBg, nameColor, timeColor, infoColor, Modifier.fillMaxWidth().weight(1f))
                        TodayTomorrowCardPreview(AboutStr.previewCourseEnglish(), "14:00-15:35", AboutStr.previewInfoEnglishTeacher(), ComposeColor(0xFF2196F3), cardBg, nameColor, timeColor, infoColor, Modifier.fillMaxWidth().weight(1f), tint = ComposeColor(0x1A2196F3))
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        TodayTomorrowCardPreview(AboutStr.previewCourseLinearAlgebra(), "08:00-09:35", AboutStr.previewInfoLinearTeacher(), ComposeColor(0xFFFF9800), cardBg, nameColor, timeColor, infoColor, Modifier.fillMaxWidth().weight(1f))
                        TodayTomorrowCardPreview(AboutStr.previewCoursePhysics(), "10:00-11:35", AboutStr.previewInfoPhysicsTeacher(), ComposeColor(0xFF9C27B0), cardBg, nameColor, timeColor, infoColor, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTomorrowCardPreview(
    name: String, time: String, info: String,
    barColor: ComposeColor, cardBg: ComposeColor,
    nameColor: ComposeColor, timeColor: ComposeColor, infoColor: ComposeColor,
    modifier: Modifier = Modifier,
    /** 额外叠加层（正在上课的淡蓝），不受卡片不透明度影响 */
    tint: ComposeColor? = null,
) {
    Card(
        cornerRadius = 14.dp,
        modifier = modifier,
        // 底色交给 WidgetCardGlass：卡片自己再铺一层，圆角差一点点就会在四个角露白
        colors = CardDefaults.defaultColors(
            color = ComposeColor.Transparent,
            contentColor = ComposeColor.Transparent
        ),
        insideMargin = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            WidgetCardGlass(
                cardColor = cardBg,
                cornerRadius = 14.dp,
                tint = tint,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .width(4.dp)
                    .background(barColor, com.haooz.softglass.shape.ContinuousRoundedRectangle(2.dp))
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = nameColor, maxLines = 1)
                Text(time, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = timeColor, fontFamily = FontFamily.SansSerif, maxLines = 1)
                Text(info, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif, maxLines = 1)
            }
            }
        }
    }
}

@Composable
private fun CourseCardPreview(
    startTime: String, endTime: String, name: String, info: String,
    barColor: ComposeColor, remaining: String?,
    cardBg: ComposeColor, nameColor: ComposeColor, infoColor: ComposeColor,
    timeStartColor: ComposeColor, timeEndColor: ComposeColor, nowColor: ComposeColor,
    modifier: Modifier = Modifier,
    /** 额外叠加层（正在上课的淡蓝），不受卡片不透明度影响 */
    tint: ComposeColor? = null,
) {
    Card(
        cornerRadius = 12.dp,
        modifier = modifier,
        colors = CardDefaults.defaultColors(
            color = ComposeColor.Transparent,
            contentColor = ComposeColor.Transparent
        ),
        insideMargin = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            WidgetCardGlass(
                cardColor = cardBg,
                cornerRadius = 12.dp,
                tint = tint,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Column(modifier = Modifier.width(35.dp), horizontalAlignment = Alignment.Start) {
                Text(startTime, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = timeStartColor, fontFamily = FontFamily.SansSerif)
                Text(endTime, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = timeEndColor, fontFamily = FontFamily.SansSerif)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.width(4.dp).height(32.dp).background(barColor, com.haooz.softglass.shape.ContinuousRoundedRectangle(2.dp)))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = nameColor, maxLines = 1)
                Text(info, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif)
            }
            if (remaining != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(remaining, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = nowColor, textAlign = TextAlign.End)
            }
            }
        }
    }
}

@Composable
private fun FeatureItem(title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(8.dp).padding(top = 7.dp).background(MiuixTheme.colorScheme.primary, com.haooz.softglass.shape.ContinuousRoundedRectangle(4.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
        }
    }
}

@Composable
private fun WidgetPaddingSelector(
    selectedMode: Int,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop?,
    onSelect: (Int) -> Unit
) {
    val entry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = AboutStr.paddingStandard(),
                selected = selectedMode == 0,
                onClick = { onSelect(0) }
            ),
            DropdownItem(
                text = "4×6",
                selected = selectedMode == 1,
                onClick = { onSelect(1) }
            ),
            DropdownItem(
                text = "4×7",
                selected = selectedMode == 2,
                onClick = { onSelect(2) }
            ),
            DropdownItem(
                text = AboutStr.padding4x6NoText(),
                selected = selectedMode == 3,
                onClick = { onSelect(3) }
            ),
            DropdownItem(
                text = AboutStr.padding4x7NoText(),
                selected = selectedMode == 4,
                onClick = { onSelect(4) }
            ),
        )
    )
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = ComposeColor.Transparent,
        selectedContainerColor = ComposeColor.Transparent,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        insideMargin = PaddingValues(0.dp)
    ) {
        OverlayDropdownMenu(
            title = AboutStr.widgetLayoutTitle(),
            entry = entry,
            collapseOnSelection = true,
            liquidGlassBackdrop = liquidGlassBackdrop,
            dropdownColors = liquidGlassDropdownColors,
        )
    }
}
