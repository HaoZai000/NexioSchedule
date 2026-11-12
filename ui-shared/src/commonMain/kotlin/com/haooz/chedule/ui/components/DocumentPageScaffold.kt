package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.screens.LocalTabletSettingsPaneChrome
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.platform.isRenderEffectSupported
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.LocalTopBarMaterial
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberTopBarMaterialState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.OverScrollState

/** 顶部糊带基准高度（不含状态栏）。 */
internal val PageBlurBaseHeight: Dp = 72.dp

/** 存在头部组件（搜索框、分段控件）时，顶部糊带的额外高度。 */
internal val HeaderComponentBlurExtra: Dp = 72.dp

/**
 * 标题下方的固定头部内容（分段控件、搜索框等）。
 *
 * 统一把内容顶到折叠标题之下，并跟随标题一起让出高度；调用方不要再自行计算这段偏移。
 */
@Composable
fun PageHeaderContent(
    paddingValues: PaddingValues,
    scrollBehavior: SharedScrollBehavior?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(
                top = paddingValues.calculateTopPadding() +
                    CollapsibleTopAppBarDefaults.CollapsedHeight,
            )
            .collapsibleTopInset(scrollBehavior)
            .fillMaxWidth(),
        content = content,
    )
}

/**
 * 全屏文档页脚手架：折叠大标题顶栏、玻璃返回按钮与内容采样层。
 *
 * 页面内容会被录制进采样层 [GlassBackdrop]，顶栏按钮和页面浮层都以它作为玻璃折射的来源。
 *
 * @param recordKey 采样层的结构指纹。null 表示每帧重录；传入稳定的键（如数据版本）可在内容
 *   静止时跳过重录。键必须覆盖所有会改变被采样像素的因素，否则玻璃会采到旧帧。
 * @param pageBlurDp 页面级模糊半径（dp，绘制期取值），0 表示不模糊。内容层、顶栏层与浮层
 *   各自成层，因此不会把页内浮层（如翻面卡片）一起糊掉。
 * @param chromeBlocked 页面浮层是否接管顶栏。为 true 时顶栏区域拦下点击并转给 [onChromeBlockedTap]。
 * @param endAction 顶栏右侧动作；第三个参数是采样层，供玻璃按钮折射页面内容。
 * @param overlay 覆盖整页的浮层（下拉菜单等），绘制在顶栏之上，同样跟随页面级模糊。
 * @param headerOverlay 标题下方的固定头部内容，与顶栏按钮共用同一份材质状态。
 */
@Composable
fun DocumentPageScaffold(
    title: String,
    onBack: () -> Unit,
    recordKey: Any? = null,
    pageBlurDp: (() -> Float)? = null,
    chromeBlocked: Boolean = false,
    onChromeBlockedTap: () -> Unit = {},
    endAction: (@Composable (backdropAlpha: Float, shadowAlpha: Float, backdrop: GlassBackdrop) -> Unit)? = null,
    overlay: (@Composable BoxScope.(liquidGlassBackdrop: GlassBackdrop) -> Unit)? = null,
    headerOverlay: (@Composable (backdrop: GlassBackdrop, scrollBehavior: SharedScrollBehavior) -> Unit)? = null,
    content: @Composable (scrollBehavior: SharedScrollBehavior, liquidGlassBackdrop: GlassBackdrop) -> Unit,
) {
    val nativeChrome = LocalNativeDocumentChrome.current
    val nativeBarInset = (nativeChrome?.barInset ?: 0.0).dp
    val showNavigationActions = nativeChrome == null
    val paneChrome = LocalTabletSettingsPaneChrome.current
    // 平板右栏不在屏幕左缘，宿主已按窗口避让过：页内再套一层 safeDrawing 会在左边多出
    // 一个状态栏高度的留白（右边没有），左右不对称。
    val horizontalSafeInsets = if (paneChrome.hostHandlesHorizontalInsets) {
        Modifier
    } else {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    }
    val backgroundColor = MiuixTheme.colorScheme.surface
    val primaryColor = MiuixTheme.colorScheme.primary
    val tintArgb = remember(primaryColor) { primaryColor.toArgb().toLong() }

    SideEffect {
        nativeChrome?.title = title
        nativeChrome?.tintArgb = tintArgb
    }

    val liquidGlassBackdrop = rememberLayerBackdrop()
    val scrollBehavior = rememberSharedScrollBehavior()
    // 每页独立的 overscroll 作用域：内容与顶栏必须读同一个实例，否则越界回弹无法驱动顶栏材质。
    val pageOverScrollState = remember { OverScrollState() }
    // 折叠动画期间采样像素逐帧变化：按高度偏移的帧差判断，只在绘制期求值，不触发重组。
    val docMustRecord: () -> Boolean = remember(scrollBehavior) {
        var lastOffset = Float.NaN
        {
            val offset = scrollBehavior.state.heightOffset
            (offset != lastOffset).also { lastOffset = offset }
        }
    }
    // 两个模糊图层各自持有一份 RenderEffect 缓存，半径不变时复用同一实例。
    val topBarBlur = rememberPageBlurModifier(pageBlurDp)
    val overlayBlur = rememberPageBlurModifier(pageBlurDp)

    CompositionLocalProvider(LocalOverScrollState provides pageOverScrollState) {
        // 全站统一的材质判据：按钮玻璃与阴影都从这里取值。
        val material = rememberTopBarMaterialState(scrollBehavior)
        Scaffold(
            modifier = Modifier
                .background(backgroundColor)
                .then(horizontalSafeInsets)
                .consumeWindowInsets(PaddingValues(top = nativeBarInset)),
            topBar = {},
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                val pageScope = this

                // 采样层只包内容：顶栏按钮与浮层在层外，避免玻璃采样到自身。
                Box(
                    modifier = if (AppMaterialSettings.isNoneTier()) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxSize()
                            .layerBackdrop(
                                liquidGlassBackdrop,
                                recordKey = recordKey,
                                mustRecord = docMustRecord,
                            )
                    }
                ) {
                    content(scrollBehavior, liquidGlassBackdrop)
                }

                // 顶栏独立成层：页面级模糊糊不到标题，而整页共用一个模糊层又会把页内浮层一起糊掉。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(topBarBlur)
                ) {
                    ProgressiveBlurTopBar(
                        backdrop = liquidGlassBackdrop,
                        // 有头部组件时加高糊带，先盖住组件再淡出。
                        height = if (headerOverlay == null) {
                            Dp.Unspecified
                        } else {
                            PageBlurBaseHeight +
                                (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() -
                                    nativeBarInset).coerceAtLeast(0.dp) +
                                HeaderComponentBlurExtra
                        },
                        topInsetAdjustment = nativeBarInset,
                        noTopStatusBar = hostVerticalBarEdge(),
                    ) {
                        CollapsibleTopAppBar(
                            title = title,
                            largeTitle = title,
                            // 平板右栏的标题锁定为折叠态。
                            showLargeTitle = if (paneChrome.forceCollapsedTitle) false else null,
                            modifier = Modifier,
                            scrollBehavior = scrollBehavior,
                            materialState = material,
                            contentPadding = {},
                            startAction = if (showNavigationActions && paneChrome.showBackButton) {
                                { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = onBack,
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.ChevronBackward,
                                        contentDescription = CommonStr.back(),
                                        performHapticFeedback = false,
                                        iconSize = 25.dp,
                                        iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                }
                            } else null,
                            endAction = if (showNavigationActions) {
                                endAction?.let { action ->
                                    { backdropAlpha, shadowAlpha ->
                                        action(backdropAlpha, shadowAlpha, liquidGlassBackdrop)
                                    }
                                }
                            } else null,
                        )
                    }

                    // 页面浮层接管顶栏时，拦下糊带内的返回 / 更多，点击交给浮层退出。
                    if (chromeBlocked) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onChromeBlockedTap,
                                )
                        )
                    }
                }

                if (headerOverlay != null) {
                    PageHeaderContent(
                        paddingValues = paddingValues,
                        scrollBehavior = scrollBehavior,
                    ) {
                        // 头部组件与顶栏按钮共用材质状态，保证同一帧出现。
                        CompositionLocalProvider(LocalTopBarMaterial provides material) {
                            headerOverlay(liquidGlassBackdrop, scrollBehavior)
                        }
                    }
                }

                if (overlay != null) {
                    // 浮层叠在整页之上，同样吃页面级模糊，避免糊掉的页面上突兀地留一块清晰区域。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(overlayBlur)
                    ) {
                        overlay.invoke(pageScope, liquidGlassBackdrop)
                    }
                }
            }
        }
    }
}

/**
 * 页面级模糊修饰符：把整块内容渲染进独立图层后整体模糊。
 *
 * 半径在绘制期读取，动画期间逐帧生效但不触发重组；半径不变时复用同一个 [BlurEffect]，
 * 避免每次绘制都重建 RenderEffect 连带触发图层重绘。
 */
@Composable
private fun rememberPageBlurModifier(blurDp: (() -> Float)?): Modifier {
    if (blurDp == null) return Modifier
    val supported = remember { isRenderEffectSupported() }
    val cache = remember { BlurEffectCache() }
    val currentBlurDp = rememberUpdatedState(blurDp)
    return remember(cache) {
        Modifier.graphicsLayer {
            val blurDpValue = currentBlurDp.value?.invoke() ?: 0f
            val radiusPx = if (supported && blurDpValue > 0.01f) blurDpValue.dp.toPx() else 0f
            val effect = cache.get(radiusPx)
            if (renderEffect !== effect) renderEffect = effect
        }
    }
}

/** 按半径缓存 [BlurEffect]：半径未变化时返回同一实例，避免重复设置图层效果。 */
private class BlurEffectCache {
    private var radiusPx = Float.NaN
    private var effect: BlurEffect? = null

    fun get(radiusPx: Float): BlurEffect? {
        if (radiusPx != this.radiusPx) {
            this.radiusPx = radiusPx
            effect = if (radiusPx > 0f) {
                BlurEffect(null, radiusPx, radiusPx, TileMode.Clamp)
            } else {
                null
            }
        }
        return effect
    }
}
