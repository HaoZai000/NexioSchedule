package com.haooz.chedule.ui.effects.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.toShape
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.rememberNavigationBack

// 「卡片 ⇄ 全屏」连贯形变，课程详情、课程编辑、作息编辑共用。
//
// 三条进度：animProgress 驱动裁切高度与快照/内容透明度，animTransY 驱动位移曲线，
// scaleProgress 驱动缩放。预测性返回手势单独驱动缩放与锚点。

/** 展开：前快后慢。 */
private val OpenEase = OobeQuartOutEasing

/** 收起：起步快、末段缓。 */
private val ExitEase = OobeCubicOutEasing

/** 位移展开比缩放更陡，让卡片先到位。 */
private val TransOpenEase = OobeFifthpowerOutEasing

/** 位移收起更贴身。 */
private val TransExitEase = OobeQuadraticOutEasing

/** 预测返回滑到底时页面缩到全屏的倍数。 */
private const val PREDICTIVE_BACK_SCALE = 0.4f

/** 预测返回缩放锚点的横向偏移比例，朝滑动来向的反面偏。 */
private const val PREDICTIVE_ANCHOR_SHIFT = 1.0f

/** 与 navigationevent 的 EDGE_* 取值一致。 */
private const val EDGE_LEFT = 0
private const val EDGE_RIGHT = 1
private const val EDGE_NONE = 2

private const val OPEN_MILLIS = 580
private const val OPEN_TRANS_MILLIS = 520
private const val EXIT_MILLIS = 340
private const val EXIT_TRANS_MILLIS = 340

/** 预测返回松手后的回弹 / 放开锚点时长。 */
private const val GESTURE_SETTLE_MILLIS = 180
private const val GESTURE_RELEASE_MILLIS = 140

/** 认为页面已回到全屏的缩放阈值。 */
private const val FULL_SCALE_EPSILON = 0.999f

/** 形变背景快照的收缩比例（1 = 原尺寸），编辑页与课程详情共用。 */
const val MORPH_BACKGROUND_SHRINK = 0.9f

/** 卡片 → 全屏形变的一帧状态。 */
data class MorphAnimState(
    val bgAlpha: Float,
    val snapshotAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float,
    val gesture: Float,
    /** 缩放锚点横向比例，0.5 为屏幕正中；直接喂给 `transformOrigin`。 */
    val anchorX: Float,
)

/**
 * 页面裁切形状。
 *
 * 宽度取每次回调的 `size`：clipShape 是 remember 单例，分屏、折叠屏、横竖切换后
 * 捕获旧宽度会让裁切停在旧值。
 */
private class MorphClipShape(
    private val screenCornerRadiusPx: ScreenCornerRadiiPx,
    private val startCornerRadiusPx: Float,
    private val animState: State<MorphAnimState>,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val s = animState.value
        // 预测返回松手时手势进度可能先归零、页面还没回到全屏：此时保持屏幕圆角，
        // 等 scale 真正到 1 再收成直角，否则左下/右下会瞬间变方。
        val radiusPx = when {
            s.gesture > 0f -> screenCornerRadiusPx
            s.progress >= 1f -> if (s.scale >= FULL_SCALE_EPSILON) {
                ScreenCornerRadiiPx.Zero
            } else {
                screenCornerRadiusPx
            }
            s.progress <= 0.7f ->
                screenCornerRadiusPx.interpolateFrom(
                    startCornerRadiusPx,
                    (s.progress / 0.7f).coerceIn(0f, 1f),
                )
            else -> screenCornerRadiusPx
        }
        // 展开到底按实测高度裁：clipBottom 由宿主传入的 screenHeight 推导，
        // 窗口尺寸变化后可能小于实际布局高度。
        val bottom = if (s.progress >= 1f && s.gesture <= 0f) size.height else s.clipBottom
        val target = Size(size.width, bottom)
        // 全屏静止态不裁圆角，省掉每帧构建 squircle path。
        if (radiusPx == ScreenCornerRadiiPx.Zero) {
            return Outline.Rectangle(Rect(Offset.Zero, target))
        }
        // 预测返回不补偿，正常形变除以 scale，按手势平滑过渡，避免松手时圆角跳变。
        val compensate = (1f - s.gesture) / s.scale + s.gesture
        return radiusPx.scaled(compensate).toShape(density).createOutline(target, layoutDirection, density)
    }
}

/** 形变控制器：宿主读 [state] / [clipShape] 绘制页面，用 [close] 播放退出形变。 */
class MorphTransition internal constructor(
    /** 当前帧的形变状态。 */
    val state: State<MorphAnimState>,
    /** 页面裁切形状，交给 `Modifier.morphClip`。 */
    val clipShape: Shape,
    /** 是否正在播放进出形变，宿主据此隐藏原生 chrome。 */
    val animating: State<Boolean>,
    private val onClose: (onBeforeFinish: (() -> Unit)?) -> Unit,
) {
    /** 播放退出形变，动画结束后先回调 [onBeforeFinish] 再走 `onBack`。 */
    fun close(onBeforeFinish: (() -> Unit)? = null) = onClose(onBeforeFinish)
}

/**
 * 记住一套「卡片 ⇄ 全屏」形变。
 *
 * @param cardLeft 源卡片左边缘（px）
 * @param cardTop 源卡片上边缘（px）
 * @param cardWidth 源卡片宽（px），为 0 表示没有可交叉的卡片，不播形变
 * @param cardHeight 源卡片高（px）
 * @param screenWidth 屏幕宽（px）
 * @param screenHeight 屏幕高（px）
 * @param screenCornerRadius 屏幕物理圆角（px）
 * @param startCornerRadiusDp 源卡片圆角（dp）
 * @param onBackStart 开始收起时立即回调
 * @param onBack 收起动画结束后回调
 * @param interceptBack 返回被拦截时调用，返回 true 表示已处理
 * @param embedded 内嵌模式（平板右栏）：直接到全屏静态态，不播形变、不拦返回
 * @param autoStart false 时先停在起点（卡片态）不播展开：调用方借此先把「起点卡图」
 * 上屏，再藏掉源卡、拍整页快照，最后置 true 放行动画，动画首帧就不会有空洞。
 */
@Composable
fun rememberMorphTransition(
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    screenCornerRadius: ScreenCornerRadiiPx,
    startCornerRadiusDp: Float,
    onBackStart: () -> Unit,
    onBack: () -> Unit,
    interceptBack: () -> Boolean = { false },
    embedded: Boolean = false,
    autoStart: Boolean = true,
): MorphTransition {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hasCardBounds = cardWidth > 0f && cardHeight > 0f && screenWidth > 0f

    val initial = if (embedded) 1f else 0f
    val animProgress = remember { Animatable(initial) }
    val animTransY = remember { Animatable(initial) }
    val scaleProgress = remember { Animatable(initial) }
    /** 手势进度 0..1，大于 0 表示预测返回进行中。 */
    val gestureBackProgress = remember { Animatable(0f) }
    /**
     * 锚点锁中心权重：1 = 位移强制 0（围绕屏幕中心缩放），0 = 正常形变位移。
     * 用 Animatable 而非布尔，松手时不会因翻转而跳变。
     */
    val centerLock = remember { Animatable(0f) }

    /** 手势起点的缩放位置，打开动画未播完就滑返回时从当前位置接着缩。 */
    var gestureScaleBase by remember { mutableFloatStateOf(1f) }
    var isGestureActive by remember { mutableStateOf(false) }
    /**
     * 本次关闭是否由预测返回手势触发：提交时位移走「屏幕中心 → 卡片中心」的直线，
     * 避免混入顶部锚点补偿。
     */
    var closeFromGesture by remember { mutableStateOf(false) }
    var gestureEdge by remember { mutableIntStateOf(EDGE_NONE) }
    val animatingState = remember { mutableStateOf(false) }

    val startCornerRadiusPx = startCornerRadiusDp * density.density
    val navigationBackState = rememberNavigationBack()

    // 宿主的 lambda 与几何值每帧都可能换实例，统一用 UpdatedState 取最新值
    val currentOnBackStart by rememberUpdatedState(onBackStart)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentInterceptBack by rememberUpdatedState(interceptBack)
    val cardLeftState = rememberUpdatedState(cardLeft)
    val cardTopState = rememberUpdatedState(cardTop)
    val cardWidthState = rememberUpdatedState(cardWidth)
    val cardHeightState = rememberUpdatedState(cardHeight)
    val screenWidthState = rememberUpdatedState(screenWidth)
    val screenHeightState = rememberUpdatedState(screenHeight)
    val embeddedState = rememberUpdatedState(embedded)

    /** 手势取消或返回被拦截：回弹全屏并平滑放开锚点。 */
    fun settleBack() {
        isGestureActive = false
        scope.launch {
            if (gestureBackProgress.value > 0f) {
                coroutineScope {
                    launch { scaleProgress.animateTo(1f, tween(GESTURE_SETTLE_MILLIS)) }
                    launch { gestureBackProgress.animateTo(0f, tween(GESTURE_SETTLE_MILLIS)) }
                    launch { centerLock.animateTo(0f, tween(GESTURE_SETTLE_MILLIS)) }
                }
            } else {
                centerLock.animateTo(0f, tween(GESTURE_SETTLE_MILLIS))
            }
        }
    }

    /** 播放退出形变；[onBeforeFinish] 在动画播完、`onBack` 之前调用。 */
    fun playClose(onBeforeFinish: (() -> Unit)?) {
        if (!hasCardBounds) {
            onBeforeFinish?.invoke()
            currentOnBackStart()
            currentOnBack()
            return
        }
        if (animatingState.value) return
        animatingState.value = true
        isGestureActive = false
        closeFromGesture = gestureBackProgress.value > 0f
        currentOnBackStart()
        scope.launch {
            coroutineScope {
                // 缩放中心从屏幕中心平滑过渡回卡片锚点，形变位移随之接管
                launch { gestureBackProgress.animateTo(0f, tween(GESTURE_RELEASE_MILLIS)) }
                launch { centerLock.animateTo(0f, tween(GESTURE_RELEASE_MILLIS)) }
                launch { scaleProgress.animateTo(0f, tween(EXIT_MILLIS, easing = ExitEase)) }
                launch { animProgress.animateTo(0f, tween(EXIT_MILLIS, easing = ExitEase)) }
                launch { animTransY.animateTo(0f, tween(EXIT_TRANS_MILLIS, easing = TransExitEase)) }
            }
            onBeforeFinish?.invoke()
            currentOnBack()
        }
    }

    navigationBackState.handler(
        enabled = !embedded,
        onBackCancelled = { settleBack() },
        onBackCompleted = {
            when {
                animatingState.value -> Unit
                currentInterceptBack() -> settleBack()
                !hasCardBounds -> {
                    currentOnBackStart()
                    currentOnBack()
                }
                else -> playClose(null)
            }
        },
    )

    // 逐帧收集返回手势进度，单独协程避免手势期间反复重启 LaunchedEffect
    LaunchedEffect(Unit) {
        // backProgress() 已内含「转场进行中且方向为后退」判定，非手势期间为 null
        snapshotFlow { navigationBackState.backProgress() }
            .collect { raw ->
                val progress = raw ?: return@collect
                // 0 是松手后的收尾推送，不能 snap 掉进行中的回弹动画
                if (progress <= 0f || embeddedState.value) return@collect
                if (animatingState.value || !hasCardBounds) return@collect
                if (!PredictiveBackSettings.enabled) return@collect

                if (!isGestureActive) {
                    // 手势第一帧记下当前缩放位置，打开未播完时从当前位置接着缩
                    gestureScaleBase = scaleProgress.value
                    centerLock.snapTo(1f)
                    gestureEdge = navigationBackState.backSwipeEdge()
                }
                isGestureActive = true
                gestureBackProgress.snapTo(progress)

                val width = screenWidthState.value
                val cardRatio = if (width > 0f) {
                    (cardWidthState.value / width).coerceIn(0f, 0.9f)
                } else {
                    0f
                }
                val targetSp = (PREDICTIVE_BACK_SCALE - cardRatio) / (1f - cardRatio)
                // 缩放由手指水平位移占屏宽的比例驱动；左右边缘符号相反，取绝对值
                val swipePx = navigationBackState.backSwipeOffsetX()
                val swipeRatio = if (swipePx != null && width > 0f) {
                    (abs(swipePx) / width).coerceIn(0f, 1f)
                } else {
                    progress
                }
                scaleProgress.snapTo(gestureScaleBase + (targetSp - gestureScaleBase) * swipeRatio)
            }
    }

    LaunchedEffect(autoStart) {
        closeFromGesture = false
        if (embedded) {
            scaleProgress.snapTo(1f)
            animProgress.snapTo(1f)
            animTransY.snapTo(1f)
            return@LaunchedEffect
        }
        if (!hasCardBounds) return@LaunchedEffect
        // 原生取消/保存在展开开始前就到达时，别再把它拉回全屏
        if (animatingState.value) return@LaunchedEffect
        // 调用方还没放行：停在起点卡态，等它把源卡藏掉、快照拍好
        if (!autoStart) return@LaunchedEffect
        // 等一帧让静态首帧完整上屏，再从下一帧开始播放
        withFrameNanos { }
        launch { scaleProgress.animateTo(1f, tween(OPEN_MILLIS, easing = OpenEase)) }
        launch { animProgress.animateTo(1f, tween(OPEN_MILLIS, easing = OpenEase)) }
        launch { animTransY.animateTo(1f, tween(OPEN_TRANS_MILLIS, easing = TransOpenEase)) }
    }

    val animState = remember {
        // 跟手位移的冻结值：松手后 backSwipeOffsetY 变 null，直接归零会瞬移。
        // 普通 var 而非 state：在 derivedStateOf 里写 state 会自触发重算。
        var lastFollowYRaw = 0f
        derivedStateOf {
            val cardLeft = cardLeftState.value
            val cardTop = cardTopState.value
            val cardWidth = cardWidthState.value
            val cardHeight = cardHeightState.value
            val screenWidth = screenWidthState.value
            val screenHeight = screenHeightState.value
            val embedded = embeddedState.value
            val centerLockWeight = 1f - centerLock.value

            if (!(cardWidth > 0f && cardHeight > 0f && screenWidth > 0f)) {
                MorphAnimState(0f, 0f, 1f, 0f, 0f, 1f, screenHeight, 1f, 0f, 0.5f)
            } else {
                val p = animProgress.value
                val ty = animTransY.value
                val bgAlpha = if (embedded) 0f else (p * 0.5f).coerceIn(0f, 0.5f)
                val snapAlpha = (1f - p * 3f).coerceIn(0f, 1f)
                val contAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
                // coerceAtLeast 防止窄卡片时 scale 变负翻转
                val cardRatio = cardWidth / screenWidth
                val scale = (cardRatio + (1f - cardRatio) * scaleProgress.value).coerceAtLeast(0.05f)

                val gesture = gestureBackProgress.value
                val normalX = (cardLeft + cardWidth / 2f - screenWidth / 2f) * (1f - p)
                // 手势期间不做平移，横向位移全由「固定锚点 + 缩放」产生；
                // 提交时走「屏幕中心 → 卡片中心」的直线
                val translationX = if (closeFromGesture) {
                    (cardLeft + cardWidth / 2f - screenWidth / 2f) * (1f - p)
                } else {
                    normalX * (1f - gesture)
                }

                // 可见区域中心 Y 沿曲线插值，不依赖 p 与 ty 的时间差
                val cardCenterY = cardTop + cardHeight / 2f
                val screenCenterY = screenHeight / 2f
                val targetCenterY = cardCenterY + (screenCenterY - cardCenterY) * ty
                val normalY = targetCenterY - screenHeight / 2f * (1f - scale) -
                    (cardHeight + (screenHeight - cardHeight) * p) / 2f
                // 手势起点位移：由 gestureScaleBase 反推，打开未播完时不会被拽到 0
                val startScale = (cardRatio + (1f - cardRatio) * gestureScaleBase).coerceAtLeast(0.05f)
                val normalYStart = targetCenterY - screenHeight / 2f * (1f - startScale) -
                    (cardHeight + (screenHeight - cardHeight) * p) / 2f
                val lockedY = normalYStart * (1f - gesture)

                val rawY = navigationBackState.backSwipeOffsetY()
                if (rawY != null) lastFollowYRaw = rawY
                val followRange = screenHeight * 0.25f
                val damping = 0.65f * gesture
                val followY = lastFollowYRaw * damping / (1f + abs(lastFollowYRaw) / (followRange * 6.5f))
                val translationY = when {
                    isGestureActive -> followY
                    closeFromGesture -> (cardTop - screenHeight / 2f * (1f - cardRatio)) * (1f - p) + followY * p
                    else -> normalY * (1f - gesture) * centerLockWeight + lockedY * centerLock.value +
                        followY * gesture
                }

                // 缩放锚点在手势首帧按 edge 定好，期间不变；松手后按时间轴回中心
                val anchorShift = when (gestureEdge) {
                    EDGE_LEFT -> PREDICTIVE_ANCHOR_SHIFT
                    EDGE_RIGHT -> -PREDICTIVE_ANCHOR_SHIFT
                    else -> 0f
                }
                val anchorX = when {
                    isGestureActive -> 0.5f + anchorShift
                    closeFromGesture -> 0.5f + anchorShift * p
                    else -> 0.5f + anchorShift * gesture
                }

                // 手势期间裁切跟随「当前展开高度 × 缩放」；松手过渡期按 gesture 插值衔接
                val morphClipHeight = cardHeight + (screenHeight - cardHeight) * p
                val predictiveClip = morphClipHeight * scale
                val rawClipBottom = if (isGestureActive) {
                    predictiveClip
                } else {
                    predictiveClip * gesture + morphClipHeight * (1f - gesture)
                }

                MorphAnimState(
                    bgAlpha = bgAlpha,
                    snapshotAlpha = snapAlpha,
                    contentAlpha = contAlpha,
                    translationX = translationX,
                    translationY = translationY,
                    scale = scale,
                    clipBottom = rawClipBottom / scale,
                    progress = p,
                    gesture = gesture,
                    anchorX = anchorX,
                )
            }
        }
    }

    val clipShape = remember(screenCornerRadius, startCornerRadiusPx) {
        MorphClipShape(screenCornerRadius, startCornerRadiusPx, animState)
    }

    return MorphTransition(
        state = animState,
        clipShape = clipShape,
        animating = animatingState,
        onClose = { playClose(it) },
    )
}

/** 按当前形变状态裁剪页面内容，必须在绘制阶段调用。 */
fun Modifier.morphClip(morph: MorphTransition): Modifier = drawWithContent {
    // MorphClipShape 只读 size / density，不读布局方向
    val outline = morph.clipShape.createOutline(size, LayoutDirection.Ltr, this)
    when (outline) {
        is Outline.Rectangle -> {
            val r = outline.rect
            if (r.left <= 0f && r.top <= 0f && r.right >= size.width && r.bottom >= size.height) {
                drawContent()
            } else {
                clipRect(r.left, r.top, r.right, r.bottom) { this@drawWithContent.drawContent() }
            }
        }
        is Outline.Rounded -> clipPath(Path().apply { addRoundRect(outline.roundRect) }) {
            this@drawWithContent.drawContent()
        }
        is Outline.Generic -> clipPath(outline.path) { this@drawWithContent.drawContent() }
    }
}
