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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
 * 弹窗内容自身的 backdrop（由弹窗内部 rememberLayerBackdrop 捕获）。
 *
 * 弹窗内的玻璃组件（关闭按钮、下拉菜单等）应当从这里取 backdrop，
 * 而不是让调用方用 `onSheetContentBackdropCreated` 把它提升成屏幕级 State：
 * 后者会在弹窗挂载后的头一两帧里回写外层 State，导致整个页面（含壁纸模糊层）
 * 在弹窗进入动画最吃紧的时刻重跑一次组合，这是打开弹窗掉帧的主要来源。
 * 用 CompositionLocal 下发则完全不触碰外层作用域，零重组成本。
 */
val LocalSheetContentBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * 非快照的 backdrop 持有者，配合 `onSheetContentBackdropCreated` 使用。
 *
 * 适用对象：**弹窗外部**的组件（典型是从弹窗里点开的二级 OverlayDialog，如节次/时间选择器）
 * 需要采样弹窗内容做模糊时——它们在弹窗作用域之外，读不到 LocalSheetContentBackdrop。
 *
 * 为什么不用 State：写入 State 会让整个宿主页面在弹窗挂载后重跑一次组合，而那正好压在
 * 弹窗进入动画的头几帧（打开弹窗掉帧的主因）。这里是普通对象，写入零成本、零重组。
 * 二级弹窗只可能在用户交互之后才组合，那时 backdrop 早已就绪，不需要 State 来驱动更新。
 */
class BackdropHolder {
    var value: Backdrop? = null
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
    // 显示时立即可见，隐藏时等动画播完再隐藏
    LaunchedEffect(show) {
        if (show) {
            visibleState.value = true
        }
    }
    // 注意：回调不再经由 State 中转。早先这里用一个 mutableStateOf 承接 backdrop，
    // 但它的 value 在组合期被 LaunchedEffect 的 key 读取 → 弹窗挂载后的写入会让本组合作用域
    // （进而整个弹窗内容）在打开动画头几帧重组一次。现在直接由内容层回调。

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
            onSheetContentBackdropCreated = onSheetContentBackdropCreated,
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

    // 外层 lambda 身份不稳定时不要拿它当 pointerInput key，否则每次重组都会重启手势协程
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(dimModifier)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnDismissRequest() })
            },
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

                // 录制时先铺 sheetBgColor：只进 backdrop，不改屏幕上的合成。
                // 空隙/上边距才有实底，渐进模糊底边不会和外壳颜色对不齐。
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

                // 原先等 enterDone 再挂 backdrop/AGSL，编译卡顿正好落在「动画刚结束、用户准备点遮罩」的窗口，
                // 表现为遮罩暂时点不动、返回键却可以。改为进入动画早期挂载，让卡顿被滑入过程盖住。
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
                    // 拖拽手柄（仅按下放大动画）
                    DragHandleArea()

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

                    // 顶部渐进模糊：只盖标题下方一条，常显；采样已不透明的内容层
                    if (sheetBackdropMounted) {
                        ProgressiveBlurTopBar(
                            backdrop = sheetContentBackdrop,
                            height = 84.dp,
                            tintColor = sheetBgColor,
                            tintIntensity = 0f,
                            blurAlpha = 1f,
                            edgeFadeStart = 0.55f,
                            modifier = Modifier.fillMaxWidth().zIndex(1f)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(60.dp))
                        }
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
