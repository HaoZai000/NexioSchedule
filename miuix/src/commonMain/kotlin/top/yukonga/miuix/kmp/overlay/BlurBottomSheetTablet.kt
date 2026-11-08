/** 自定义模糊底部弹窗 - 平板版本：居中悬浮矩形，从底部滑入 */
package top.yukonga.miuix.kmp.overlay

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberCanvasBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.layout.rememberCollapsibleTopAppBarState
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.material.LocalMaterialNoneEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalForcedDarkTheme
import top.yukonga.miuix.kmp.theme.LocalMonetColorEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.rememberAppSettingDark
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.LocalPredictiveBackEnabled
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import top.yukonga.miuix.kmp.utils.OverScrollState
import top.yukonga.miuix.kmp.utils.rememberNavigationBack

/**
 * 平板版模糊底部弹窗组件：居中悬浮矩形，从底部滑入动画。
 *
 * @param show 是否显示
 * @param title 标题文字
 * @param dimBackground 是否压暗背景
 * @param sheetMaxWidth 弹窗最大宽度
 * @param sheetBackgroundColor 弹窗背景颜色，null 则使用默认颜色
 * @param onDismissRequest 关闭回调
 * @param startAction 标题栏左侧操作按钮
 * @param endAction 标题栏右侧操作按钮
 * @param content 内容区域
 */
@Composable
fun BlurBottomSheetTablet(
    show: Boolean,
    title: String,
    dimBackground: Boolean = false,
    sheetMaxWidth: Dp = 560.dp,
    sheetMaxHeight: Dp = Dp.Unspecified,
    sheetBackgroundColor: Color? = null,
    isBottomAligned: Boolean = false,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onSheetContentBackdropCreated: ((GlassBackdrop?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { mutableStateOf(show) }

    LaunchedEffect(show) {
        if (show) {
            visibleState.value = true
        }
    }
    // 回调直接由内容层触发，不再经 State 中转：State 在组合期被读取，挂载后写入会让弹窗内容在打开动画头几帧重组一次。

    DialogLayout(
        visible = visibleState,
        enableWindowDim = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        enableAutoLargeScreen = false,
        renderInRootScaffold = true,
    ) {
        BlurBottomSheetTabletContent(
            show = show,
            visibleState = visibleState,
            title = title,
            dimBackground = dimBackground,
            sheetMaxWidth = sheetMaxWidth,
            sheetMaxHeight = sheetMaxHeight,
            sheetBackgroundColor = sheetBackgroundColor,
            isBottomAligned = isBottomAligned,
            onDismissRequest = onDismissRequest,
            startAction = startAction,
            endAction = endAction,
            onSheetContentBackdropCreated = onSheetContentBackdropCreated,
            content = content,
        )
    }
}

@Composable
private fun BlurBottomSheetTabletContent(
    show: Boolean,
    visibleState: MutableState<Boolean>,
    title: String,
    dimBackground: Boolean = false,
    sheetMaxWidth: Dp = 560.dp,
    sheetMaxHeight: Dp = Dp.Unspecified,
    sheetBackgroundColor: Color? = null,
    isBottomAligned: Boolean = false,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onSheetContentBackdropCreated: ((GlassBackdrop?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // 弹窗跟随应用主题，不受壁纸强制主题影响。
    // ⚠ 取色开关必须一并带上（与手机版同一套）：只给 Light/Dark 会让弹窗内 isDynamicColor 恒为 false，莫奈取色分支全部失效。
    val sheetAppDark = rememberAppSettingDark()
    val sheetMonetColor = LocalMonetColorEnabled.current
    val sheetAppController = remember(sheetAppDark, sheetMonetColor) {
        ThemeController(
            when {
                sheetMonetColor && sheetAppDark -> ColorSchemeMode.MonetDark
                sheetMonetColor -> ColorSchemeMode.MonetLight
                sheetAppDark -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.Light
            }
        )
    }
    CompositionLocalProvider(LocalForcedDarkTheme provides null) {
        MiuixTheme(controller = sheetAppController) {
            val animationProgress = remember { Animatable(0f) }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    // 滑入动画用的窗口高度在组合期读取一次，避免进入动画期间逐帧做密度换算
    val windowHeightPx = with(density) { windowInfo.containerSize.height.toFloat() }
    // 预测性返回（API 33+）进度实时驱动 sheet 下滑，取消回弹、完成关闭；低版本退化为立即关闭。
    // 走本模块 expect 封装（utils/NavigationBack.kt），commonMain 不直接依赖 AndroidX navigationevent。
    // backState 必须同时喂 handler 与进度读取（同一份状态），拆开跟手动画不动。
    val backState = rememberNavigationBack()
    val backProgress = remember { Animatable(0f) }
    val detentState = remember { BlurBottomSheetDetentState() }
    val isDetentExpanded by remember(detentState) {
        derivedStateOf { detentState.expansionFraction > 0.001f }
    }

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    // sheet 底色：莫奈取色跟随 surfaceContainer（与手机版同一套）；非莫奈保持硬编码（暗 #1E1E1E / 亮 #F2F2F2）。
    val sheetBgColor = sheetBackgroundColor ?: if (MiuixTheme.isDynamicColor) {
        MiuixTheme.colorScheme.surfaceContainer
    } else if (isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2)

    // 显示/隐藏动画同时驱动位移与遮罩透明度，确保二者完全同步
    LaunchedEffect(show) {
        if (show) {
            detentState.reset()
            backProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = CubicBezierEasing(0.34f, 1.12f, 0.3f, 1f)
                )
            )
        } else {
            animationProgress.animateTo(0f, animationSpec = tween(380, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f)))
            visibleState.value = false
        }
    }

    // ⚠ 本组件存在性完全由 DialogEntry（visibleState）控制，禁止在此提前 return
    val dimModifier = if (dimBackground) {
        Modifier.drawBehind {
            drawRect(Color.Black.copy(alpha = 0.2f * animationProgress.value * (1f - backProgress.value)))
        }
    } else Modifier

    // onDismiss 身份不稳定，不能作为 pointerInput key
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val coroutineScope = rememberCoroutineScope()

    // 返回手势：进行中驱动 sheet 下滑，取消回弹、完成关闭
    backState.handler(
        enabled = show,
        onBackCancelled = {
            coroutineScope.launch {
                backProgress.animateTo(0f, animationSpec = tween(150))
            }
        },
        onBackCompleted = {
            currentOnDismissRequest()
        },
    )

    // 逐帧收集返回手势进度（单独协程，避免手势期间每帧取消/重启 LaunchedEffect）；
    // LocalPredictiveBackEnabled 须在组合期读取（协程内 .current 编译不过）。
    val predictiveBackEnabled = LocalPredictiveBackEnabled.current

    LaunchedEffect(Unit) {
        // 预测性返回关闭时连进度都不订阅（返回仍被拦截，直接关闭）；
        // backProgress() 非手势期间返回 null（保持静止），手势进行中为连续进度。
        if (!predictiveBackEnabled) return@LaunchedEffect
        snapshotFlow { backState.backProgress() }
            .collect { progress ->
                if (progress != null) {
                    backProgress.snapTo(progress)
                }
            }
    }

    // 弹窗形状与 drawBackdrop 的 shape lambda 必须固定引用（否则模糊重编译）。
    // 圆角固定 38dp：平板弹窗是居中窄条不贴屏幕边缘，不跟屏幕物理圆角。
    val sheetShape = remember { ContinuousRoundedRectangle(38.dp) }
    val windowWidth = with(density) { windowInfo.containerSize.width.toDp() }
    val horizontalGap = ((windowWidth - minOf(sheetMaxWidth, windowWidth)) / 2).coerceAtLeast(0.dp)

            Box(
        modifier = Modifier
            .fillMaxSize()
            .then(dimModifier)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnDismissRequest() })
            },
        contentAlignment = if (isBottomAligned) Alignment.BottomCenter else Alignment.Center,
    ) {
        val sheetModifier = Modifier
            .graphicsLayer {
                val progress = animationProgress.value
                // 从屏幕底部滑入；backProgress 为返回手势把 sheet 向下推出屏幕
                translationY = windowHeightPx * (1f - progress) + backProgress.value * windowHeightPx +
                    detentState.dismissOffsetPx
            }

        Box(
            modifier = sheetModifier
                .width(sheetMaxWidth)
                .fillMaxWidth()
                .heightIn(max = if (sheetMaxHeight != Dp.Unspecified) sheetMaxHeight else with(density) { windowInfo.containerSize.height.toDp() } * 0.8f)
                .blurBottomSheetDetentHeight(detentState)
                .then(if (isBottomAligned) Modifier.padding(bottom = 20.dp) else Modifier)
                .clip(sheetShape)
                // 弹窗本体不做壁纸玻璃模糊，纯实色
                .background(sheetBgColor)
                // A centered sheet already clears the window edges by this distance.
                .consumeWindowInsets(PaddingValues(horizontal = horizontalGap))
                .onGloballyPositioned { coordinates ->
                    detentState.updateCollapsedHeight(coordinates.size.height)
                }
                .pointerInput(Unit) {
                    // 消费弹窗空白处的点击，防止事件穿透到背景层触发关闭
                    detectTapGestures(onTap = {})
                }
                .semantics {
                    onClick(label = "Dismiss") {
                        onDismissRequest()
                        true
                    }
                },
            content = {
                // === 顶栏机制（迁移自手机版 BlurBottomSheet）===
                val topBarState = rememberCollapsibleTopAppBarState()
                val scrollBehavior = rememberSharedScrollBehavior(topBarState)
                val overScrollState = remember { OverScrollState() }
                LaunchedEffect(Unit) { topBarState.heightOffsetLimit = -1f }

                val showButtonShadow by remember(scrollBehavior) {
                    derivedStateOf {
                        val contentOffset = scrollBehavior.state.contentOffset
                        val os = overScrollState.offset
                        contentOffset < 0f || os < 0f
                    }
                }
                val proxyConnection = remember(scrollBehavior, overScrollState) {
                    val delegate = scrollBehavior.nestedScrollConnection
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                            delegate.onPreScroll(available, source)

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            if (overScrollState.offset != 0f) {
                                return delegate.onPostScroll(Offset.Zero, available, source)
                            }
                            return delegate.onPostScroll(consumed, available, source)
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity =
                            delegate.onPreFling(available)

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                            delegate.onPostFling(consumed, available)
                    }
                }
                val shadowAlpha = remember { Animatable(0f) }
                val backdropAlpha = remember { Animatable(0f) }
                // 稳定实例：provider 不再逐帧下发新对象
                val topBarMaterial = remember { SheetTopBarMaterial(backdropAlpha, shadowAlpha) }
                LaunchedEffect(showButtonShadow) {
                    val target = if (showButtonShadow) 1f else 0f
                    val spec = if (showButtonShadow) {
                        folmeSpring(damping = 1.0f, response = 0.6f)
                    } else {
                        folmeSpring<Float>(damping = 1.0f, response = 0.4f)
                    }
                    launch { shadowAlpha.animateTo(target, spec) }
                    launch { backdropAlpha.animateTo(target, spec) }
                }

                // 录制时先铺 sheetBgColor，空隙处糊层采到的是实底
                val sheetContentBackdrop = rememberLayerBackdrop(
                    onDraw = remember(sheetBgColor) {
                        {
                            drawRect(sheetBgColor)
                            drawContent()
                        }
                    }
                )
                LaunchedEffect(sheetContentBackdrop) {
                    onSheetContentBackdropCreated?.invoke(sheetContentBackdrop)
                }

                // 进入动画早期挂载，避免 AGSL 编译卡顿落在动画刚结束、用户准备点遮罩的窗口。
                // 无材质档：内容录屏层不挂（采样侧退回纯色 placeholder）
                val materialNone = LocalMaterialNoneEnabled.current
                var sheetBackdropMounted by remember { mutableStateOf(false) }
                LaunchedEffect(show, materialNone) {
                    if (show && !materialNone) {
                        delay(80.milliseconds)
                        sheetBackdropMounted = true
                    }
                }

                val placeholderOnDraw: DrawScope.() -> Unit = remember(sheetBgColor) {
                    {
                        drawRect(
                            color = sheetBgColor,
                            topLeft = Offset(-size.width, -size.height),
                            size = Size(size.width * 3f, size.height * 3f)
                        )
                    }
                }
                val placeholderBackdrop = rememberCanvasBackdrop(placeholderOnDraw)

                CompositionLocalProvider(
                    LocalOverScrollState provides overScrollState,
                    LocalSheetTopBarMaterial provides topBarMaterial,
                    LocalSheetContentBackdrop provides
                            if (sheetBackdropMounted) sheetContentBackdrop else placeholderBackdrop,
                    LocalBlurBottomSheetContentExpanded provides isDetentExpanded,
                ) {
                    BlurBottomSheetDragHandle(
                        onDrag = detentState::dragBy,
                        onDragStarted = { detentState.beginDrag() },
                        onDragStopped = { velocity ->
                            detentState.settle(
                                velocityY = velocity,
                                velocityThresholdPx = with(density) { 800.dp.toPx() },
                                dismissThresholdPx = with(density) { 150.dp.toPx() },
                                onDismiss = currentOnDismissRequest,
                            )
                        },
                    )
                    // 内容区域（layerBackdrop 捕获「底色+内容」）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isDetentExpanded) {
                                    Modifier.fillMaxHeight()
                                } else {
                                    Modifier.wrapContentHeight()
                                },
                            )
                            .nestedScroll(proxyConnection)
                            .sheetTopOpacityMask(82.dp) { shadowAlpha.value }
                            .then(
                                if (sheetBackdropMounted) {
                                    Modifier.layerBackdrop(sheetContentBackdrop)
                                } else Modifier
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    ) {
                        content()
                    }

                    // 顶部渐变遮罩：顶部铺满弹窗底色、向下淡出到透明（与手机版同一套）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .zIndex(1f)
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to sheetBgColor,
                                        0.2f to sheetBgColor.copy(alpha = 0.95f),
                                        0.5f to sheetBgColor.copy(alpha = 0.8f),
                                        0.8f to sheetBgColor.copy(alpha = 0.45f),
                                        1.0f to sheetBgColor.copy(alpha = 0f),
                                    ),
                                )
                            )
                    )

                    // 标题栏固定高度，保证有无操作按钮时标题都垂直居中于同一位置
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .zIndex(2f),
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.align(Alignment.Center),
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        if (startAction != null) {
                            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                                startAction()
                            }
                        }
                        if (endAction != null) {
                            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                                endAction()
                            }
                        }
                    }
                }
            },
        )
        }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
