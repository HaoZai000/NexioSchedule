/** 自定义模糊底部弹窗 - 平板版本：居中悬浮矩形，从底部滑入 */
package top.yukonga.miuix.kmp.overlay

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberCollapsibleTopAppBarState
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.LocalOverScrollState
import com.haooz.chedule.ui.utils.OverScrollState
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import androidx.compose.foundation.layout.fillMaxHeight as fillMaxHeightModifier

/**
 * 平板版模糊底部弹窗组件：居中悬浮矩形，从底部滑入动画。
 *
 * @param show 是否显示
 * @param title 标题文字
 * @param blurRadius 模糊半径
 * @param dimBackground 是否压暗背景
 * @param sheetMaxWidth 弹窗最大宽度
 * @param sheetBackgroundColor 弹窗背景颜色，null 则使用默认颜色
 * @param sheetBackgroundAlpha 弹窗背景透明度，null 则使用默认值
 * @param onDismissRequest 关闭回调
 * @param startAction 标题栏左侧操作按钮
 * @param endAction 标题栏右侧操作按钮
 * @param liquidGlassBackdrop 液态玻璃 backdrop
 * @param content 内容区域
 */
@Composable
fun BlurBottomSheetTablet(
    show: Boolean,
    title: String,
    fillMaxHeight: Boolean = false,
    blurRadius: Float = 18f,
    dimBackground: Boolean = false,
    sheetMaxWidth: Dp = 560.dp,
    sheetMaxHeight: Dp = Dp.Unspecified,
    sheetBackgroundColor: Color? = null,
    sheetBackgroundAlpha: Float? = null,
    isBottomAligned: Boolean = false,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    liquidGlassBackdrop: Backdrop? = null,
    onSheetContentBackdropCreated: ((Backdrop?) -> Unit)? = null,
    skipEnterAnimation: Boolean = false,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { mutableStateOf(show) }

    LaunchedEffect(show) {
        if (show) {
            visibleState.value = true
        }
    }
    // 回调不再经 State 中转（state 的 value 在组合期被 LaunchedEffect key 读取 →
    // 弹窗挂载后的写入会让弹窗内容在打开动画头几帧重组一次），改由内容层直接回调。

    // 返回手势放在 DialogLayout 外面，确保组合时立即生效
    BackHandler(enabled = show) {
        onDismissRequest()
    }

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
            blurRadius = blurRadius,
            dimBackground = dimBackground,
            sheetMaxWidth = sheetMaxWidth,
            sheetMaxHeight = sheetMaxHeight,
            sheetBackgroundColor = sheetBackgroundColor,
            sheetBackgroundAlpha = sheetBackgroundAlpha,
            isBottomAligned = isBottomAligned,
            onDismissRequest = onDismissRequest,
            startAction = startAction,
            endAction = endAction,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onSheetContentBackdropCreated = onSheetContentBackdropCreated,
            skipEnterAnimation = skipEnterAnimation,
            content = content,
        )
    }
}

@Composable
private fun BlurBottomSheetTabletContent(
    show: Boolean,
    visibleState: MutableState<Boolean>,
    fillMaxHeight: Boolean = false,
    title: String,
    blurRadius: Float = 18f,
    dimBackground: Boolean = false,
    sheetMaxWidth: Dp = 560.dp,
    sheetMaxHeight: Dp = Dp.Unspecified,
    sheetBackgroundColor: Color? = null,
    sheetBackgroundAlpha: Float? = null,
    isBottomAligned: Boolean = false,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    liquidGlassBackdrop: Backdrop? = null,
    onSheetContentBackdropCreated: ((Backdrop?) -> Unit)? = null,
    skipEnterAnimation: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 弹窗始终跟随应用主题，不受壁纸强制主题影响
    val sheetAppDark = rememberAppSettingDark()
    val sheetAppController = remember(sheetAppDark) {
        ThemeController(if (sheetAppDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
    }
    CompositionLocalProvider(LocalForcedDarkTheme provides null) {
        MiuixTheme(controller = sheetAppController) {
            val animationProgress = remember { Animatable(if (show && skipEnterAnimation) 1f else 0f) }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    // 滑入动画用的窗口高度：在组合期读取一次，避免进入动画期间逐帧做密度换算
    val windowHeightPx = with(density) { windowInfo.containerDpSize.height.toPx() }

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val sheetBgColor = sheetBackgroundColor ?: if (isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2)

    // 显示/隐藏动画（同时驱动弹窗位移与遮罩透明度，确保二者完全同步）
    LaunchedEffect(show) {
        if (show) {
            if (skipEnterAnimation) {
                animationProgress.snapTo(1f)
            } else {
                animationProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = CubicBezierEasing(0.34f, 1.12f, 0.3f, 1f)
                    )
                )
            }
        } else {
            animationProgress.animateTo(0f, animationSpec = tween(380, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f)))
            visibleState.value = false
        }
    }

    // 本组件存在性完全由 DialogEntry（visibleState）控制，禁止在此提前 return，
    // 否则退出动画结束瞬间遮罩被移除而 DialogEntry content 仍空挂在屏上，造成触摸穿透。

    // 平板弹窗主体 - 居中悬浮矩形，从底部滑入
    // 遮罩透明度在「绘制期」读取（drawBehind），不在组合期读（Modifier.background）。
    // 组合期读会让进入动画的 500ms 内整个弹窗作用域逐帧重组，牵连 backdrop 录制与模糊节点。
    val dimModifier = if (dimBackground) {
        Modifier.drawBehind { drawRect(Color.Black.copy(alpha = 0.2f * animationProgress.value)) }
    } else Modifier

    // 外层 lambda 身份不稳定时不要拿它当 pointerInput key
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    // 弹窗形状与 drawBackdrop 的 shape lambda 必须固定引用：
    // drawBackdrop 的 ModifierNodeElement 用「引用」比较 shape 与 effects，ShapeProvider 没有实现 equals，
    // 组合期每次 `ContinuousRoundedRectangle(38.dp)` 都是新对象、每次 `{ ... }` 都是新 lambda，
    // 于是节点判不等 → invalidateDraw → 重新录制壁纸层 + 重跑一次 GPU 模糊（进入动画期间就是逐帧重跑）。
    val sheetShape = remember { ContinuousRoundedRectangle(38.dp) }
    val sheetShapeBlock: () -> androidx.compose.ui.graphics.Shape = remember(sheetShape) { { sheetShape } }

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
                // 从屏幕底部滑入
                translationY = windowHeightPx * (1f - progress)
            }

        Box(
            modifier = sheetModifier
                .width(sheetMaxWidth)
                .fillMaxWidth()
                .heightIn(max = if (sheetMaxHeight != Dp.Unspecified) sheetMaxHeight else windowInfo.containerDpSize.height * 0.8f)
                .then(if (fillMaxHeight) Modifier.fillMaxHeightModifier() else Modifier)
                .then(if (isBottomAligned) Modifier.padding(bottom = 20.dp) else Modifier)
                .clip(sheetShape)
                // 弹窗本体不做壁纸玻璃模糊，纯实色
                .edgeLight(shape = sheetShape, edgeLight = rememberDefaultEdgeLight())
                .background(sheetBgColor)
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

                // 进入动画早期挂载，避免 AGSL 编译卡顿落在动画刚结束、用户准备点遮罩的窗口
                var sheetBackdropMounted by remember { mutableStateOf(skipEnterAnimation) }
                LaunchedEffect(show) {
                    if (show && !skipEnterAnimation) {
                        delay(80)
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
                ) {
                    // 内容区域（layerBackdrop 捕获「底色+内容」）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .nestedScroll(proxyConnection)
                            .then(
                                if (sheetBackdropMounted) {
                                    Modifier.layerBackdrop(sheetContentBackdrop)
                                } else Modifier
                            )
                    ) {
                        content()
                    }

                    // 顶部渐进模糊：只盖标题下方一条，常显
                    if (sheetBackdropMounted) {
                        ProgressiveBlurTopBar(
                            backdrop = sheetContentBackdrop,
                            height = 82.dp,
                            tintColor = sheetBgColor,
                            tintIntensity = 0f,
                            blurAlpha = 1f,
                            edgeFadeStart = 0.55f,
                            modifier = Modifier.fillMaxWidth().zIndex(1f)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(60.dp))
                        }
                    }

                    // 标题栏固定高度，保证有无操作按钮时标题都垂直居中于同一位置
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
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
