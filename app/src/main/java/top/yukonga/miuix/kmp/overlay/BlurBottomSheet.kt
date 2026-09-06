/** 自定义模糊底部弹窗 - 支持全区域模糊背景 */
package top.yukonga.miuix.kmp.overlay

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import kotlin.math.abs

/**
 * 顶栏材质动画值载体：由 BlurBottomSheet 顶栏机制下发，供 startAction/endAction
 * 内部的 LiquidTopBarButton 读取，驱动液态玻璃材质/阴影随滚动渐变。
 */
/**
 * 顶栏材质动画值载体：由 BlurBottomSheet 顶栏机制下发，供 startAction/endAction
 * 内部的 LiquidTopBarButton 读取，驱动液态玻璃材质/阴影随滚动渐变。
 *
 * 内部持有的是 Animatable 本身而不是快照值：provider 下发的对象因此是稳定实例，
 * 材质动画期间不会让整个弹窗内容作用域跟着逐帧重组；消费者通过 backdropAlpha /
 * shadowAlpha 读到的仍然是同一个浮点值，行为不变。
 */
@Stable
class SheetTopBarMaterial internal constructor(
    internal val backdropAlphaAnimatable: Animatable<Float, AnimationVector1D>,
    internal val shadowAlphaAnimatable: Animatable<Float, AnimationVector1D>,
) {
    val backdropAlpha: Float get() = backdropAlphaAnimatable.value
    val shadowAlpha: Float get() = shadowAlphaAnimatable.value
}

val LocalSheetTopBarMaterial = compositionLocalOf {
    SheetTopBarMaterial(Animatable(1f), Animatable(1f))
}

/**
 * 自定义模糊底部弹窗组件，支持全区域（包括标题栏）的模糊背景效果。
 *
 * @param show 是否显示
 * @param title 标题文字
 * @param liquidGlassBackdrop 液态玻璃 backdrop
 * @param blurRadius 模糊半径
 * @param dimBackground 是否压暗背景
 * @param sheetBackgroundColor 弹窗背景颜色，null 则使用默认颜色
 * @param sheetBackgroundAlpha 弹窗背景透明度，null 则使用默认值
 * @param onDismissRequest 关闭回调
 * @param startAction 标题栏左侧操作按钮
 * @param endAction 标题栏右侧操作按钮
 * @param onSheetContentBackdropCreated 弹窗内容 backdrop 创建回调
 * @param skipEnterAnimation 是否跳过进入动画
 * @param content 内容区域
 */
@Composable
fun BlurBottomSheet(
    show: Boolean,
    title: String,
    fillMaxHeight: Boolean = false,
    liquidGlassBackdrop: Backdrop? = null,
    blurRadius: Float = 18f,
    dimBackground: Boolean = false,
    sheetBackgroundColor: Color? = null,
    sheetBackgroundAlpha: Float? = null,
    sheetOffsetDp: Dp = Dp.Unspecified,
    sheetMaxWidth: Dp = Dp.Unspecified,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onSheetContentBackdropCreated: ((Backdrop?) -> Unit)? = null,
    skipEnterAnimation: Boolean = false,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { mutableStateOf(show) }
    val sheetContentBackdropHolder = remember { mutableStateOf<Backdrop?>(null) }
    // 显示时立即可见，隐藏时等动画播完再隐藏
    LaunchedEffect(show) {
        if (show) {
            visibleState.value = true
        }
    }

    LaunchedEffect(sheetContentBackdropHolder.value) {
        onSheetContentBackdropCreated?.invoke(sheetContentBackdropHolder.value)
    }

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
        BlurBottomSheetContent(
            show = show,
            visibleState = visibleState,
            title = title,
            liquidGlassBackdrop = liquidGlassBackdrop,
            blurRadius = blurRadius,
            dimBackground = dimBackground,
            sheetBackgroundColor = sheetBackgroundColor,
            sheetBackgroundAlpha = sheetBackgroundAlpha,
            onDismissRequest = onDismissRequest,
            startAction = startAction,
            endAction = endAction,
            sheetContentBackdropHolder = sheetContentBackdropHolder,
            sheetOffsetDp = sheetOffsetDp,
            sheetMaxWidth = sheetMaxWidth,
            fillMaxHeight = fillMaxHeight,
            skipEnterAnimation = skipEnterAnimation,
            content = content,
        )
    }
}

@Composable
private fun BlurBottomSheetContent(
    show: Boolean,
    visibleState: MutableState<Boolean>,
    fillMaxHeight: Boolean = false,
    title: String,
    liquidGlassBackdrop: Backdrop?,
    blurRadius: Float,
    dimBackground: Boolean = false,
    sheetBackgroundColor: Color? = null,
    sheetBackgroundAlpha: Float? = null,
    sheetOffsetDp: Dp = Dp.Unspecified,
    sheetMaxWidth: Dp = Dp.Unspecified,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    sheetContentBackdropHolder: MutableState<Backdrop?>? = null,
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
    // 拖拽位移用 floatState（graphicsLayer 在绘制期读取，拖拽期间零重组、零协程分配）；
    // 松手后的 spring 回弹单独用 Animatable，与拖拽位移相加得到总位移。
    val dragOffsetY = remember { mutableFloatStateOf(0f) }
    val settleOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val sheetHeightPx = remember { mutableIntStateOf(0) }
    val imeInsets = WindowInsets.ime

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val sheetBgColor = sheetBackgroundColor ?: if (isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2)
    val dismissThresholdPx = with(density) { 150.dp.toPx() }
    val velocityThresholdPx = with(density) { 800.dp.toPx() }

    // drawBackdrop / drawPlainBackdrop 的 ModifierNodeElement 用「引用」比较 shape 与 effects 这两个
    // lambda，且 ShapeProvider 没有实现 equals。组合期每次执行 `shape = { ... }` 都会产生新 lambda，
    // 于是 element 判不等 → 节点 update → 自动 invalidateDraw → 重新录制壁纸层 + 重新跑一次 GPU 模糊。
    // 弹窗进入动画期间只要发生重组，这套模糊就会逐帧重跑。把形状对象与 lambda 固定下来即可彻底避免。
    val sheetShape = remember { ContinuousRoundedRectangle(36.dp) }
    val sheetShapeBlock: () -> androidx.compose.ui.graphics.Shape = remember(sheetShape) { { sheetShape } }

    // 显示/隐藏动画（同时驱动弹窗位移与遮罩透明度，确保二者完全同步）
    LaunchedEffect(show) {
        if (show) {
            dragOffsetY.floatValue = 0f
            settleOffsetY.stop()
            settleOffsetY.snapTo(0f)
            if (skipEnterAnimation) {
                animationProgress.snapTo(1f)
            } else {
                // 进入动画：使用 CubicBezier 带回弹效果
                animationProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 480,
                        easing = CubicBezierEasing(0.34f, 1.12f, 0.3f, 1f)
                    )
                )
            }
        } else {
            // 退出动画
            animationProgress.animateTo(0f, animationSpec = tween(320, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f)))
            visibleState.value = false
        }
    }

    // 本组件存在性完全由 DialogEntry（visibleState）控制，禁止在此提前 return，
    // 否则退出动画结束瞬间遮罩被移除而 DialogEntry content 仍空挂在屏上，造成触摸穿透。

    // 底部弹窗主体 - 允许内容溢出屏幕底部
    // 遮罩透明度在「绘制期」读取（drawBehind），不在组合期读（Modifier.background）。
    // 若在组合期读，进入动画的 480ms 内整个弹窗作用域会逐帧重组，进而让下方 drawBackdrop 的
    // ModifierNodeElement 每帧 update → invalidateDraw → 每帧重新录制壁纸层并重新做一次 GPU 模糊。
    // 这是弹窗进入掉帧的主要来源：绘制期读取只会让「遮罩这一个节点」重绘，不触发重组。
    val dimModifier = if (dimBackground) {
        Modifier.drawBehind { drawRect(Color.Black.copy(alpha = 0.2f * animationProgress.value)) }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(dimModifier)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onDismissRequest,
            ),
    ) {
        // 弹窗最底部兜底偏移用的窗口高度：在组合期读取一次，避免进入/拖拽动画期间逐帧做密度换算
        val windowHeightPx = with(density) { windowInfo.containerDpSize.height.toPx() }
        val sheetModifier = Modifier
            .graphicsLayer {
                val progress = animationProgress.value
                val currentHeight = sheetHeightPx.intValue.toFloat()
                val baseOffset = if (currentHeight > 0) currentHeight else windowHeightPx
                translationY = baseOffset * (1f - progress) + dragOffsetY.floatValue + settleOffsetY.value
            }

        val sheetOffsetDpValue = if (sheetOffsetDp != Dp.Unspecified) sheetOffsetDp else 200.dp

        Box(
            modifier = sheetModifier
                .offset(y = sheetOffsetDpValue)
                .align(Alignment.BottomCenter)
                .then(
                    if (sheetMaxWidth != Dp.Unspecified) Modifier.width(sheetMaxWidth).fillMaxWidth()
                    else Modifier.fillMaxWidth()
                )
                .heightIn(max = windowInfo.containerDpSize.height)
                .then(if (fillMaxHeight) Modifier.fillMaxHeight() else Modifier)
                .onGloballyPositioned { coordinates ->
                    if (imeInsets.getBottom(density) == 0) {
                        val newHeight = coordinates.size.height
                        if (sheetHeightPx.intValue != newHeight) {
                            sheetHeightPx.intValue = newHeight
                        }
                    }
                }
                .imePadding()
                .clip(sheetShape)
                .then(
                    if (liquidGlassBackdrop != null && Build.VERSION.SDK_INT >= 33) {
                        val blurPx = with(density) { blurRadius.dp.toPx() }
                        val backdropEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(liquidGlassBackdrop, blurPx) {
                            {
                                vibrancy()
                                blur(blurPx)
                            }
                        }
                        Modifier.drawBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = sheetShapeBlock,
                            effects = backdropEffects,
                            highlight = null
                        )
                    } else {
                        Modifier
                    }
                )
                .edgeLight(shape = sheetShape, edgeLight = rememberDefaultEdgeLight())
                .background(sheetBgColor.copy(alpha = sheetBackgroundAlpha ?: if (liquidGlassBackdrop != null)
                    if (Build.VERSION.SDK_INT >= 33) 0.9f else 1f
                    else 1f))
                .pointerInput(Unit) {
                    // 消费弹窗空白处的点击，防止事件穿透到背景层触发关闭
                    detectTapGestures(onTap = {})
                }
                .semantics {
                    onClick(label = "Dismiss") {
                        onDismissRequest()
                        true
                    }
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dragAmount ->
                        // 直接写 floatState，不再为每个指针事件起一个协程（拖拽时一秒上百次分配）
                        val current = dragOffsetY.floatValue + settleOffsetY.value
                        val newOffset = current + dragAmount
                        // 往上拖时加阻尼，越往上越难拖
                        val dampedOffset = if (newOffset < 0f) {
                            val resistance = 1f / (1f + abs(newOffset) / 30f)
                            current + dragAmount * resistance
                        } else {
                            newOffset
                        }
                        dragOffsetY.floatValue = dampedOffset
                    },
                    onDragStarted = {
                        // 打断正在进行的回弹，把剩余位移并入拖拽位移，保证总位移连续不跳变
                        val pending = dragOffsetY.floatValue + settleOffsetY.value
                        settleOffsetY.stop()
                        settleOffsetY.snapTo(0f)
                        dragOffsetY.floatValue = pending
                    },
                    onDragStopped = { velocity ->
                        val shouldDismiss = velocity > velocityThresholdPx || dragOffsetY.floatValue > dismissThresholdPx
                        if (shouldDismiss) {
                            onDismissRequest()
                        } else {
                            // 使用 spring 动画回弹，传入初始速度让回弹更自然。
                            // onDragStopped 本身是挂起作用域，无需再 launch 协程。
                            settleOffsetY.snapTo(dragOffsetY.floatValue)
                            dragOffsetY.floatValue = 0f
                            settleOffsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.72f,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialVelocity = velocity * 0.12f
                            )
                        }
                    },
                ),
            content = {
                // === 顶栏机制（迁移自 CollapsibleTopAppBar，小标题模式）===
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
                // OverScrollNode.onPostScroll 在 overscroll 时把 available.y 全部返回给父级，
                // 会被 scrollBehavior 累加进 contentOffset 造成污染（松手后不归零，阴影错保持）。
                // 用代理连接包裹：overscroll 激活时，把 onPostScroll 的 consumed.y 置零，contentOffset 不再被污染。
                // 这样零F页面 co 始终为 0；可滚动页面 co 仅累积真实滚动量。
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
                            // 用 offset != 0f 而非 isOverScrollActive 判断，避免 os 在 0~offsetThreshold(1f) 之间时
                            // isOverScrollActive 仍为 false 导致的几帧污染窗口
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

                CompositionLocalProvider(
                    LocalOverScrollState provides overScrollState,
                    LocalSheetTopBarMaterial provides topBarMaterial,
                ) {
                    // 拖拽手柄（仅按下放大动画）
                    DragHandleArea()

                    // 捕获弹窗内容的 backdrop（先画不透明背景，再画内容，确保采样到不透明像素）
                    val sheetBackdropColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF4F4F4)
                    val sheetContentBackdrop = rememberLayerBackdrop {
                        drawRect(sheetBackdropColor)
                        drawContent()
                    }

                    // 将 backdrop 暴露给调用方
                    LaunchedEffect(sheetContentBackdrop) {
                        sheetContentBackdropHolder?.value = sheetContentBackdrop
                    }

                    // 内容区域（底层，用 layerBackdrop 捕获内容；nestedScroll 接入顶栏滚动行为）
                    Box(
                        modifier = Modifier
                            .nestedScroll(proxyConnection)
                            .wrapContentHeight()
                            .layerBackdrop(sheetContentBackdrop)
                    ) {
                        content()
                    }

                    // 渐变模糊遮罩（采样弹窗内容）：进入动画期间强制关闭，动画到位后再启用，
                    // 避免滑入那几百毫秒里逐帧重算渐变模糊占用帧。derivedStateOf 只在该布尔翻转一次时重组，不会逐帧重组。
                    val enterDone by remember(animationProgress) {
                        derivedStateOf { animationProgress.value >= 1f }
                    }
                    ProgressiveBlurTopBar(
                        backdrop = sheetContentBackdrop,
                        height = 84.dp,
                        tintColor = sheetBgColor,
                        tintIntensity = 0f,
                        blurAlpha = if (enterDone) backdropAlpha.value else 0f,
                        modifier = Modifier.zIndex(1f)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(60.dp))
                    }

                    // 标题栏（zIndex 提升到顶层，消费触摸事件）
                    // 标题栏固定高度，保证有无操作按钮时标题都垂直居中于同一位置
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
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

/**
 * Miuix 风格的拖拽手柄：按下时放大。
 */
@Composable
private fun DragHandleArea() {
    val pressScale = remember { Animatable(1f) }
    val pressWidth = remember { Animatable(45f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .zIndex(2f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        coroutineScope.launch {
                            launch { pressScale.animateTo(1.15f, tween(100)) }
                            launch { pressWidth.animateTo(55f, tween(100)) }
                        }
                        // 等待松手
                        waitForUpOrCancellation()
                        coroutineScope.launch {
                            launch { pressScale.animateTo(1f, tween(150)) }
                            launch { pressWidth.animateTo(45f, tween(150)) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(pressWidth.value.dp)
                .height(4.dp)
                .graphicsLayer {
                    scaleY = pressScale.value
                }
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f)),
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUpOrCancellation() {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.none { it.pressed }) break
    }
}
