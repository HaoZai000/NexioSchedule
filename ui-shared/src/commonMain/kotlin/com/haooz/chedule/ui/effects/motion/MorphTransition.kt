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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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

/**
 * 「卡片 ⇄ 全屏」连贯形变（课程详情 / 编辑作息 / 课程编辑共用一套）。
 *
 * 三个进度驱动：animProgress（0=卡片，1=全屏）管位移、裁切高度、快照/内容透明度；
 * animTransY（0..1）是位移曲线参数，前快后慢，让卡片中心沿抛物线落到屏幕中心；
 * scaleProgress（1=全屏，0=卡片）管缩放，预测返回手势单独驱动它、走完整行程。
 * 预测返回期间位移锁 0（锚点固定屏幕正中心），松手后由内部平滑放开，详见 centerLock 注释。
 */

/** 展开：前快后慢，收尾绵。 */
private val MorphOpenEase = OobeQuartOutEasing

/** 收起：起步快、末段缓。 */
private val MorphExitEase = OobeCubicOutEasing

/** 位移展开：比缩放更陡（五阶），让卡片先「冲」到位。 */
private val MorphTransOpenEase = OobeFifthpowerOutEasing

/** 位移收起：二次曲线，回卡片时更贴身。 */
private val MorphTransExitEase = OobeQuadraticOutEasing

/** 预测返回滑到底时，页面缩到全屏的多少倍。 */
private const val PREDICTIVE_BACK_SCALE = 0.4f

/**
 * 预测返回时缩放锚点的横向偏移量（占屏宽比例）。
 * 锚点朝「滑动来向的反面」偏，缩小时页面朝手指来的那侧收。
 */
private const val PREDICTIVE_ANCHOR_SHIFT = 1.0f

/** [NavigationBackState.backSwipeEdge] 的取值，与 navigationevent 的 EDGE_* 一致。 */
private const val EDGE_LEFT = 0
private const val EDGE_RIGHT = 1
private const val EDGE_NONE = 2

/** 展开时长：缩放与裁切同步，位移稍快。 */
private const val MORPH_OPEN_MILLIS = 540
private const val TRANS_OPEN_MILLIS = 500

/** 收起时长：缩放与裁切同步，位移稍快。 */
private const val MORPH_EXIT_MILLIS = 320
private const val TRANS_EXIT_MILLIS = 320

/** 预测返回松手后的回弹 / 放开锚点时长。 */
private const val GESTURE_SETTLE_MILLIS = 180
private const val GESTURE_RELEASE_MILLIS = 140

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
    /**
     * 缩放锚点的横向比例（0.5 = 屏幕正中）。
     * 手势进行中直接取偏移锚点（不从中心渐进）；松手后的收尾按时间轴回中心
     * （提交走 p、取消走 gesture），落地时必须回到 0.5。直接喂给 `transformOrigin`。
     */
    val anchorX: Float,
)

/**
 * 裁切形状。
 *
 * 宽度取当次回调的 `size`，别在构造时捕获外部宽度：clipShape 是 remember 单例，
 * 折叠屏展开 / 分屏 / 横竖切换时宽度会变，捕获旧宽度会让裁切停在旧值（平板右侧被裁掉）。
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
        // 预测返回：裁切圆角固定为屏幕圆角（不除以 scale，随页面缩放一起缩放）；
        // 其余按 morph 进度插值
        val radiusPx = when {
            s.gesture > 0f -> screenCornerRadiusPx
            s.progress >= 1f -> ScreenCornerRadiiPx.Zero
            s.progress <= 0.7f -> screenCornerRadiusPx.interpolateFrom(startCornerRadiusPx, s.progress / 0.7f)
            else -> screenCornerRadiusPx
        }
        // 补偿在"预测返回不补偿（×1）"与"正常 morph 除以 scale"之间按 gesture 平滑插值，
        // 避免松手瞬间圆角跳变大
        val compensate = (1f - s.gesture) / s.scale + s.gesture
        // 展开到底（无手势）时按实测高度裁：clipBottom 由宿主传入的 screenHeight 推导，
        // 窗口尺寸变化后它可能小于实际布局高度，那样会把页面底部整片切掉。
        val bottom = if (s.progress >= 1f && s.gesture <= 0f) size.height else s.clipBottom
        return radiusPx.scaled(compensate).toShape(density).createOutline(
            Size(size.width, bottom),
            layoutDirection,
            density,
        )
    }
}

/**
 * 形变控制器。宿主读 [state] / [clipShape] 画页面，用 [close] 播退出形变。
 */
class MorphTransition internal constructor(
    /** 当前帧的形变状态。 */
    val state: State<MorphAnimState>,
    /** 页面裁切形状，直接交给 `Modifier.clip`。 */
    val clipShape: Shape,
    /** 是否正在播进出形变。宿主据此隐藏原生 chrome（避免它盖在形变上）。 */
    val animating: State<Boolean>,
    private val onClose: (onBeforeFinish: (() -> Unit)?) -> Unit,
) {
    /**
     * 播放退出形变，结束后回调 [onBeforeFinish] 再走 [onBack]。
     * 没有卡片边界时直接返回（不播形变）。
     */
    fun close(onBeforeFinish: (() -> Unit)? = null) = onClose(onBeforeFinish)
}

/**
 * 记住一套「卡片 ⇄ 全屏」形变。
 *
 * @param cardLeft 源卡片左边缘（px）
 * @param cardTop 源卡片上边缘（px）
 * @param cardWidth 源卡片宽（px）；为 0 表示没有可交叉的卡片，不播形变
 * @param cardHeight 源卡片高（px）
 * @param screenWidth 屏幕宽（px）
 * @param screenHeight 屏幕高（px）
 * @param screenCornerRadius 屏幕物理圆角（px），形变到位时按它裁切
 * @param startCornerRadiusDp 源卡片的圆角（dp），形变起点用它
 * @param onBackStart 开始收起时立刻回调（宿主据此同步撤掉下层模糊等）
 * @param onBack 收起动画播完后回调（宿主据此真正出栈）
 * @param interceptBack 返回被拦截时调用（例如对话框开着）；返回 true 表示已处理、不关闭页面
 * @param embedded 内嵌模式（平板右栏）：直接到全尺寸静态态，不播形变、不拦返回手势
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
): MorphTransition {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hasCardBounds = cardWidth > 0f && cardHeight > 0f && screenWidth > 0f

    val initial = if (embedded) 1f else 0f
    val animProgress = remember { Animatable(initial) }
    val animTransY = remember { Animatable(initial) }
    val scaleProgress = remember { Animatable(initial) }
    // 手势进度（0..1）：>0 表示预测性返回进行中
    val gestureBackProgress = remember { Animatable(0f) }
    // 手势起点的缩放位置。打开动画还没播完就滑返回时，要从当前位置接着缩；
    // 固定从 1f 起会先把页面放大到全屏再缩小，视觉上跳一下。
    var gestureScaleBase by remember { mutableFloatStateOf(1f) }
    // 「锚点锁中心」权重：1 = 位移强制 0（缩放围绕屏幕中心），0 = 走正常形变位移。
    // 用 Animatable 而不是布尔：松手时 gesture 停在你滑到的位置（不一定是 1），
    // 布尔一翻转，translation 会从 0 直接跳到 normalY*(1-gesture) —— 视觉上闪到最上面。
    val centerLock = remember { Animatable(0f) }
    // 手势是否正在推进：进行中裁切完全跟随页面缩放，松手/取消后进入平滑过渡
    var isGestureActive by remember { mutableStateOf(false) }
    /**
     * 本次关闭是否由预测返回手势触发。
     *
     * 手势提交时页面正停在屏幕中心（全屏的 PREDICTIVE_BACK_SCALE 倍），位移要走
     * 「屏幕中心 → 卡片中心」的直线插值；套 normalY 的话会掺进顶部锚点补偿
     * （`- screenHeight/2*(1-scale)`），中途先向上窜一截再落到卡片。
     */
    var closeFromGesture by remember { mutableStateOf(false) }
    // 手势来向：决定缩放锚点往哪边偏
    var gestureEdge by remember { mutableIntStateOf(EDGE_NONE) }
    val animatingState = remember { mutableStateOf(false) }

    val startCornerRadiusPx = startCornerRadiusDp * density.density
    val navigationBackState = rememberNavigationBack()

    // 宿主传进来的 lambda 每帧都可能换实例，用 UpdatedState 取最新的
    val currentOnBackStart by rememberUpdatedState(onBackStart)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentInterceptBack by rememberUpdatedState(interceptBack)

    /** 手势取消 / 被拦截：回弹到全屏，并把锚点锁平滑放开。 */
    fun settleBack() {
        isGestureActive = false
        scope.launch {
            if (gestureBackProgress.value > 0f) {
                // 回弹三条一起走：缩放回全屏 / 手势进度归零 / 锚点锁放开。
                // 放开由 centerLock 平滑完成，所以不需要靠「先 scale 后 gesture」来避跳。
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

    /** 播放退出形变；[onBeforeFinish] 在动画播完、[onBack] 之前调用。 */
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
                launch { scaleProgress.animateTo(0f, tween(MORPH_EXIT_MILLIS, easing = MorphExitEase)) }
                launch { animProgress.animateTo(0f, tween(MORPH_EXIT_MILLIS, easing = MorphExitEase)) }
                launch { animTransY.animateTo(0f, tween(TRANS_EXIT_MILLIS, easing = MorphTransExitEase)) }
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

    // 逐帧收集返回手势进度（单独协程，避免手势期间每帧取消/重启 LaunchedEffect）
    LaunchedEffect(Unit) {
        // backProgress() 已内含「转场进行中且方向为后退」判定，非手势期间为 null —— 保持静止。
        snapshotFlow { navigationBackState.backProgress() }
            .collect { progress ->
                if (progress == null || embedded) return@collect
                // 收起动画进行中、或没有卡片边界时不驱动页面形变
                if (animatingState.value || !hasCardBounds) return@collect
                // 预测性返回动画开关：关闭时不驱动跟随动画（返回仍被拦截，直接关闭）
                if (!PredictiveBackSettings.enabled) return@collect

                if (!isGestureActive) {
                    // 手势第一帧记下当前缩放位置：打开动画没播完就滑返回时从这儿接着缩。
                    // 直接 snapTo(1f) 会先把页面放大到全屏再缩，跳一下。
                    gestureScaleBase = scaleProgress.value
                    // 锁中心在第一帧就压满，手势全程锚点固定在屏幕正中
                    centerLock.snapTo(1f)
                    // 来向决定锚点往哪边偏
                    gestureEdge = navigationBackState.backSwipeEdge()
                }
                isGestureActive = true
                gestureBackProgress.snapTo(progress)
                // 行程按「滑到底缩到全屏的 PREDICTIVE_BACK_SCALE 倍」反解：
                val cardRatio = if (screenWidth > 0f) {
                    (cardWidth / screenWidth).coerceIn(0f, 0.9f)
                } else {
                    0f
                }
                val targetSp = (PREDICTIVE_BACK_SCALE - cardRatio) / (1f - cardRatio)
                // 缩放进度由手指水平位移占屏宽的比例驱动：划一半缩一半、划满一屏缩到底。
                // 左右边缘的 offsetX 符号相反，取绝对值；无触摸数据时退回 progress。
                val swipePx = navigationBackState.backSwipeOffsetX()
                val swipeRatio = if (swipePx != null && screenWidth > 0f) {
                    (abs(swipePx) / screenWidth).coerceIn(0f, 1f)
                } else {
                    progress
                }
                scaleProgress.snapTo(gestureScaleBase + (targetSp - gestureScaleBase) * swipeRatio)
            }
    }

    LaunchedEffect(Unit) {
        closeFromGesture = false
        if (embedded) {
            // 内嵌：直接到全尺寸静态态，不播展开形变
            scaleProgress.snapTo(1f)
            animProgress.snapTo(1f)
            animTransY.snapTo(1f)
            return@LaunchedEffect
        }
        if (!hasCardBounds) return@LaunchedEffect
        // 原生取消/保存在展开开始前就到达时，别再把它拉回全屏
        if (animatingState.value) return@LaunchedEffect
        launch { scaleProgress.animateTo(1f, tween(MORPH_OPEN_MILLIS, easing = MorphOpenEase)) }
        launch { animProgress.animateTo(1f, tween(MORPH_OPEN_MILLIS, easing = MorphOpenEase)) }
        launch { animTransY.animateTo(1f, tween(TRANS_OPEN_MILLIS, easing = MorphTransOpenEase)) }
    }

    // ⚠ 宿主传进来的几何值都是普通 Float，直接捕获进 derivedStateOf 会被冻结在首次组合的值上
    val cardLeftState = rememberUpdatedState(cardLeft)
    val cardTopState = rememberUpdatedState(cardTop)
    val cardWidthState = rememberUpdatedState(cardWidth)
    val cardHeightState = rememberUpdatedState(cardHeight)
    val screenWidthState = rememberUpdatedState(screenWidth)
    val screenHeightState = rememberUpdatedState(screenHeight)

    val animState = remember {
        // 跟手位移的冻结值：松手后 backSwipeOffsetY 变 null，若直接归零页面会瞬移回原位。
        // 记住最后一帧的手指数值，收尾期间按权重衰减。普通 var（draw 阶段读写，主线程），
        // 不用 state —— 在 derivedStateOf 计算块里写 state 会自触发重算。
        var lastFollowYRaw = 0f
        derivedStateOf {
            val cardLeft = cardLeftState.value
            val cardTop = cardTopState.value
            val cardWidth = cardWidthState.value
            val cardHeight = cardHeightState.value
            val screenWidth = screenWidthState.value
            val screenHeight = screenHeightState.value
            // 1 = 位移完全放开，0 = 锁死在屏幕中心（预测返回期间）
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
                // 预测返回期间不做平移：横向位移全部来自「固定锚点 + 缩放」（见 anchorX），
                // 手指水平位移只驱动缩放进度。提交时走「屏幕中心 → 卡片中心」的直线。
                val translationX = if (closeFromGesture) {
                    (cardLeft + cardWidth / 2f - screenWidth / 2f) * (1f - p)
                } else {
                    normalX * (1f - gesture)
                }

                // 可见区域中心 Y 直接沿曲线插值，不依赖 p 和 ty 的时间差
                val cardCenterY = cardTop + cardHeight / 2f
                val screenCenterY = screenHeight / 2f
                val targetCenterY = cardCenterY + (screenCenterY - cardCenterY) * ty
                val normalY = targetCenterY - screenHeight / 2f * (1f - scale) -
                    (cardHeight + (screenHeight - cardHeight) * p) / 2f
                // 预测返回的收敛起点：用 gestureScaleBase 反推手势第一帧的 scale，再算当时的位移。
                // 打开动画没播完就滑返回时，起点就是当前位移，不会被 centerLock 一把拽到 0
                //（那样会向上闪一下）；已展开时 startScale=1、起点本来就是 0。
                val startScale = (cardRatio + (1f - cardRatio) * gestureScaleBase).coerceAtLeast(0.05f)
                val normalYStart = targetCenterY - screenHeight / 2f * (1f - startScale) -
                    (cardHeight + (screenHeight - cardHeight) * p) / 2f
                val lockedY = normalYStart * (1f - gesture)
                // 提交位移：从屏幕中心直线收到「内容顶部对齐卡片顶部」，终点须与
                // p=0/scale=cardRatio 时的 normalY 相等（裁切框从内容顶部算，按中心
                // 对齐会整体偏上）。垂直跟随 = touchY − 首帧 touchY（progress 只归一化
                // 水平方向），阻尼 0.65×手势进度、按 1/(1+|o|/6.5·range) 衰减防露白。
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

                // 缩放锚点：手势首帧按 edge 定好（右边缘返回 → 偏左），期间不变；变的
                // 是缩放进度（手指水平位移比例驱动）。围绕固定锚点缩放，横向位移天然有限。
                // 松手后按时间轴收回中心（提交 p、取消 gesture），落地必须回 0.5。
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

                // 手势推进期间裁切跟随"当前展开高度×缩放"（打开未完成时也连续，底部不瞬间归位）；
                // 松手/取消过渡期按 gesture 平滑插值衔接形变的底部收缩
                val morphClip = cardHeight + (screenHeight - cardHeight) * p
                val predictiveClip = morphClip * scale
                val rawClipBottom = if (isGestureActive) {
                    predictiveClip
                } else {
                    predictiveClip * gesture + morphClip * (1f - gesture)
                }
                val clipBottom = rawClipBottom / scale
                MorphAnimState(
                    bgAlpha = bgAlpha,
                    snapshotAlpha = snapAlpha,
                    contentAlpha = contAlpha,
                    translationX = translationX,
                    translationY = translationY,
                    scale = scale,
                    clipBottom = clipBottom,
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

/**
 * 按当前形变状态裁剪页面内容 —— 必须在**绘制阶段**做。
 *
 * ⚠ 别改回 `Modifier.clip(morph.clipShape)` / `graphicsLayer { shape = ... }`：
 * 裁切高度逐帧变大（卡片 → 全屏），而 RenderNode 会按第一次拿到的 outline 把绘制区域定死，
 * 之后 outline 变大也不重建 —— 表现为展开后下半截整片空白，切后台（窗口重建）才恢复。
 * 矩形走 `clipRect`、圆角走 `clipPath`（只有动画前段有非零圆角，那时页面在缩放，锯齿看不出来）。
 */
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
