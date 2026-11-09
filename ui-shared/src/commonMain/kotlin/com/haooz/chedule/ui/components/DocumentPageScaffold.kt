package com.haooz.chedule.ui.components

import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.OverScrollState

/**
 * 标题下方内容（分段控件、搜索框等）的**唯一**定位实现
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
 * 「全屏文档页」的统一脚手架：折叠大标题顶栏 + 玻璃返回按钮 + 内容采样层
 *
 * @param recordKey 两层采样源共用的结构指纹。传 null（默认）= 每帧录制，
 *   保持原行为；使用方传入 remember 稳定的 List 可让顶栏玻璃在内容静止时
 *   跳过重录。指纹必须覆盖**所有**会改变被采样像素的因素（数据版本、
 *   弹窗/浮层可见性、主题等），漏项会让玻璃采到旧帧。
 */
@Composable
fun DocumentPageScaffold(
    title: String,
    onBack: () -> Unit,
    recordKey: Any? = null,
    // 第三个参数是**页面采样层本身**：顶栏玻璃按钮要拿它当 backdrop，才能真的折射内容。
    // 以前调用方各自 `rememberLayerBackdrop()` 造一个空层，渲染出来是一颗纯色圆 ——
    // 看着"没有玻璃效果"就是这个原因。
    endAction: (@Composable (backdropAlpha: Float, shadowAlpha: Float, backdrop: com.haooz.softglass.backdrop.GlassBackdrop) -> Unit)? = null,
    overlay: (@Composable BoxScope.(liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop) -> Unit)? = null,
    // 挂在页面固定层的头部内容（分段控件等）
    headerOverlay: (@Composable (
        backdrop: com.haooz.softglass.backdrop.GlassBackdrop,
        scrollBehavior: SharedScrollBehavior,
    ) -> Unit)? = null,
    content: @Composable (
        scrollBehavior: SharedScrollBehavior,
        liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop,
    ) -> Unit,
) {
    val nativeChrome = LocalNativeDocumentChrome.current
    val nativeBarInset = (nativeChrome?.barInset ?: 0.0).dp
    val showNavigationActions = nativeChrome == null
    val tintArgb = MiuixTheme.colorScheme.primary.toArgb().toLong()
    SideEffect {
        nativeChrome?.title = title
        nativeChrome?.tintArgb = tintArgb
    }
    val backgroundColor = MiuixTheme.colorScheme.surface
    // 采样层本身（recordKey 由调用方给）；顶栏玻璃采它，页面内容不再另录一份没人采的层。
    val liquidGlassBackdrop = rememberLayerBackdrop()
    val scrollBehavior = rememberSharedScrollBehavior()
    // 本页独立的 overscroll 作用域：内容的 overScrollVertical 节点把越界量写进这个实例，
    // 顶栏 CollapsibleTopAppBar 也从同一个 CompositionLocal 读它 —— 两边必须是同一套。
    // 搬迁到 :miuix 后曾出现两套 LocalOverScrollState（app 的 fork 与 miuix 自带的），
    // 顶栏读的是永远为 0 的那套，于是「回弹也显现材质」整条分支成了死代码。
    // 顺带隔离：每页一个实例，不会互相串到 compositionLocalOf 的全局默认实例上。
    val pageOverScrollState = remember { OverScrollState() }
    // 顶栏折叠动画期间采样像素逐帧变，谓词读 offset 帧差（draw 阶段，不进组合）。
    // 内容自身的变化由调用方的 recordKey 负责枚举——组件看不到 content 的内部状态。
    val docMustRecord = remember(scrollBehavior) {
        var lastOffset = Float.NaN
        {
            val offset = scrollBehavior.state.heightOffset
            val moving = offset != lastOffset
            lastOffset = offset
            moving
        }
    }

    CompositionLocalProvider(LocalOverScrollState provides pageOverScrollState) {
        Scaffold(
            modifier = Modifier
                .background(backgroundColor)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
                .consumeWindowInsets(PaddingValues(top = nativeBarInset)),
            topBar = {},
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 采样层只包内容；顶栏玻璃按钮放在层外，避免循环采样
                Box(
                    modifier = Modifier.fillMaxSize().then(
                        // 无材质档：玻璃全关，采样录屏层不挂
                        if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                        else Modifier.layerBackdrop(
                            liquidGlassBackdrop,
                            recordKey = recordKey,
                            mustRecord = docMustRecord
                        )
                    )
                ) {
                    content(scrollBehavior, liquidGlassBackdrop)
                }

                ProgressiveBlurTopBar(
                    backdrop = liquidGlassBackdrop,
                    topInsetAdjustment = nativeBarInset,
                    noTopStatusBar = hostVerticalBarEdge(),
                ) {
                    CollapsibleTopAppBar(
                        title = title,
                        largeTitle = title,
                        modifier = Modifier,
                        scrollBehavior = scrollBehavior,
                        contentPadding = {},
                        startAction = if (showNavigationActions) {
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

                // 标题下方内容：走共用实现，与教务导入页同一段结构
                if (headerOverlay != null) {
                    PageHeaderContent(
                        paddingValues = paddingValues,
                        scrollBehavior = scrollBehavior,
                    ) {
                        headerOverlay.invoke(liquidGlassBackdrop, scrollBehavior)
                    }
                }

                overlay?.invoke(this, liquidGlassBackdrop)
            }
        }
    }
}
