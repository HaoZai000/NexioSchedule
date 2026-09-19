package com.haooz.chedule.ui.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import com.haooz.chedule.ui.effects.motion.OobeQuartOutSoftStartEasing
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * 导航栈 push 视差。
 *
 * 下层 Activity 已 onPause、Compose 不再刷新，必须直接平移其 decorView。
 * 打开第 N 层时推动第 N-1 层（首页二级层推动主页，三级层推动二级页）。
 */
object SecondaryPushParallax {
    private class Layer(val token: Any, activity: Activity) {
        val decor: WeakReference<View> = WeakReference(activity.window.decorView)
    }

    private var mainDecor: WeakReference<View>? = null
    /** 已打开的二级页，按打开顺序；末尾是栈顶 */
    private val openPages = mutableListOf<Layer>()
    private var mainFraction = 0f

    fun attachMainRoot(activity: Activity) {
        val decor = activity.window.decorView
        // 视差只改 translationX：硬件层让系统合成器做纯变换，避免整窗每帧重绘主页
        if (decor.layerType != View.LAYER_TYPE_HARDWARE) {
            decor.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        mainDecor = WeakReference(decor)
        if (openPages.isEmpty()) {
            applyToView(mainDecor, 0f)
            mainFraction = 0f
        } else if (openPages.size == 1) {
            applyToView(mainDecor, mainFraction)
        }
        // 嵌套打开时主页保持既有偏移
    }

    fun noteOpen(token: Any, activity: Activity) {
        if (openPages.any { it.token === token }) {
            // 组合重建/重复 noteOpen：刷新 decor 弱引用，不要重复入栈
            val idx = openPages.indexOfFirst { it.token === token }
            if (idx >= 0) {
                openPages[idx] = Layer(token, activity)
            }
            return
        }
        openPages.add(Layer(token, activity))
    }

    /**
     * 登记二级页关闭。
     * [hardClose]=true 时才把视差归零（Activity 真正销毁 / 出场动画结束）。
     * 组合重建导致的临时 dispose 不能传 true，否则会把下层页 translationX 打回 0，
     * 再叠加入场动画，表现为旧页左右抖动。
     */
    fun noteClose(token: Any, hardClose: Boolean = true) {
        val idx = openPages.indexOfFirst { it.token === token }
        if (idx < 0) return
        if (!hardClose) return
        openPages.removeAt(idx)
        if (openPages.isEmpty()) {
            applyToView(mainDecor, 0f)
            mainFraction = 0f
        } else if (idx >= 1) {
            // 关掉栈上层：它曾推动的下层二级页归位
            val below = openPages[idx - 1]
            applyToView(below.decor, 0f)
        }
        // 关掉最底层但仍有上层（少见）：主页保持当前偏移，由上层动画再驱动
    }

    /**
     * 当前转场进度应推动的下层：
     * - 栈上只有一层二级页（或正在开第一层）→ 主页
     * - 栈上有多层 → 栈顶下面那层二级页
     */
    fun applyTransitionProgress(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        if (openPages.size <= 1) {
            mainFraction = f
            applyToView(mainDecor, f)
        } else {
            val below = openPages[openPages.size - 2]
            applyToView(below.decor, f)
        }
    }

    private fun applyToView(decorRef: WeakReference<View>?, fraction: Float) {
        val decor = decorRef?.get() ?: return
        val f = fraction.coerceIn(0f, 1f)
        fun apply() {
            val w = decor.width.toFloat()
            if (w <= 0f) return
            val target = -SECONDARY_PUSH_PARALLAX * f * w
            // 亚像素不变则跳过，避免多余 invalidate（视觉无差异）
            if (abs(decor.translationX - target) < 0.5f) return
            decor.translationX = target
        }
        // 主线程且已测量：直接改 translationX，避免 post 晚一帧导致与二级页不同步
        if (decor.width > 0 && Looper.myLooper() == Looper.getMainLooper()) {
            apply()
        } else {
            decor.post { apply() }
        }
    }
}

/** 主页左移距离相对屏宽比例（iOS push 常见约 0.25–0.30） */
const val SECONDARY_PUSH_PARALLAX = 0.24f

/**
 * 二级页转场：打开时压掉系统动画（主题空动画 + FLAG_NO_ANIMATION + options(0,0)），
 * 半透明窗让下层主页继续绘制，Compose 整页从右推入；顶栏返回由组合内播出场后 finish；
 * 手势返回走系统预测性返回。
 */

fun Activity.suppressOpenTransition() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

fun Activity.suppressCloseTransition() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

fun Context.openSecondaryPage(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    val activity = this as? Activity
    val options = activity?.let {
        ActivityOptions.makeCustomAnimation(it, 0, 0).toBundle()
    }
    startActivity(intent, options)
    activity?.suppressOpenTransition()
}

fun secondaryOpenOptionsCompat(activity: Activity): ActivityOptionsCompat {
    return ActivityOptionsCompat.makeCustomAnimation(activity, 0, 0)
}

/**
 * 0=整页在右侧屏外，1=完全覆盖。
 * 出场必须在组合作用域内驱动（LaunchedEffect / rememberCoroutineScope），
 * 否则 Animatable 缺 MonotonicFrameClock，立刻结束或抛错，表现为"闪现消失"。
 */
class SecondaryPageTransitionController {
    val progress = Animatable(0f)

    /** 入场是否已播过；主题切换等导致组合重建时避免从 0 重播 */
    private var enterPlayed = false

    /** 入场是否已完整播完：组合重建时不要再等首帧、不要重播 */
    val isEnterSettled: Boolean
        get() = enterPlayed && progress.value >= 0.999f

    /** 顶栏返回置 true，由组合内动画消费 */
    var exitRequested by mutableStateOf(false)

    /**
     * 预测性返回取消：每次 +1 作为 LaunchedEffect key。
     * 不能用 Boolean：新手势 snapTo 会取消进行中的 restore，Boolean 仍为 true
     * 时再次 requestRestore() 不会重启 Effect，页面会卡在半路。
     */
    var restoreToken by mutableIntStateOf(0)
        private set

    /** 出场动画播完后回调（finish） */
    var onExitComplete: (() -> Unit)? = null

    suspend fun animateEnter(durationMillis: Int = ENTER_DURATION) {
        // 已完整入场：组合重建时不要再从 0 重播
        if (enterPlayed && progress.value >= 0.999f) {
            SecondaryPushParallax.applyTransitionProgress(1f)
            return
        }
        // 半路被打断：从当前进度续播，不要 snapTo(0)
        if (!enterPlayed) {
            enterPlayed = true
            progress.snapTo(0f)
            SecondaryPushParallax.applyTransitionProgress(0f)
        }
        progress.animateTo(1f, tween(durationMillis, easing = SecondaryEnterEasing)) {
            SecondaryPushParallax.applyTransitionProgress(this.value)
        }
        settleFullyOpen()
    }

    /**
     * 预测性返回取消回弹：从当前进度弹回完全显示。
     * 在入场曲线上反解当前进度对应的时间点，用剩余时间播出，且 easing 取曲线尾段。
     */
    suspend fun restoreFromGesture() {
        val from = progress.value.coerceIn(0f, 1f)
        if (from >= 0.999f) {
            settleFullyOpen()
            return
        }
        val (durationMs, easing) = settleOnGlobalCurve(
            fromProgress = from,
            toProgress = 1f,
            fullEasing = SecondaryEnterEasing,
            totalDuration = ENTER_DURATION,
            invertGlobal = false,
            minDuration = MIN_GESTURE_RESTORE_MS,
        )
        progress.animateTo(1f, tween(durationMs, easing = easing)) {
            SecondaryPushParallax.applyTransitionProgress(this.value)
        }
        settleFullyOpen()
    }

    /** 进度与主页视差一并锁到完全显示，避免浮点残差导致「差一点盖满」 */
    private suspend fun settleFullyOpen() {
        progress.snapTo(1f)
        // 归位后视为已入场，防止后续 animateEnter 从 0 重播
        enterPlayed = true
        SecondaryPushParallax.applyTransitionProgress(1f)
    }

    /** 松手完成吸附关闭：在出场曲线上反解剩余时间，与关闭动画一致 */
    suspend fun animateGestureDismiss() {
        val from = progress.value.coerceIn(0f, 1f)
        val (durationMs, easing) = settleOnGlobalCurve(
            fromProgress = from,
            toProgress = 0f,
            fullEasing = SecondaryExitEasing,
            totalDuration = EXIT_DURATION,
            invertGlobal = true,
            minDuration = MIN_GESTURE_SETTLE_MS,
        )
        progress.animateTo(0f, tween(durationMs, easing = easing)) {
            SecondaryPushParallax.applyTransitionProgress(this.value)
        }
        SecondaryPushParallax.applyTransitionProgress(0f)
    }

    suspend fun animateExit(durationMillis: Int = EXIT_DURATION) {
        progress.animateTo(0f, tween(durationMillis, easing = SecondaryExitEasing)) {
            SecondaryPushParallax.applyTransitionProgress(this.value)
        }
        SecondaryPushParallax.applyTransitionProgress(0f)
    }

    suspend fun snapGestureProgress(p: Float) {
        val value = p.coerceIn(0f, 1f)
        // 手势已接管进度：标记入场已开始，避免 animateEnter 再 snapTo(0)
        enterPlayed = true
        progress.snapTo(value)
        SecondaryPushParallax.applyTransitionProgress(value)
    }

    fun requestExit() {
        exitRequested = true
    }

    fun requestRestore() {
        restoreToken++
    }
}

/**
 * 在全局开/关曲线上，从 [fromProgress] 播到 [toProgress] 的剩余段
 * - [invertGlobal] = false：全局进度 = easing(u)（入场 0→1）
 * - [invertGlobal] = true：全局进度 = 1 - easing(u)（出场 1→0）
 * 返回 (剩余时长, 把该尾段映射到 0..1 的局部 easing)
 */
private fun settleOnGlobalCurve(
    fromProgress: Float,
    toProgress: Float,
    fullEasing: Easing,
    totalDuration: Int,
    invertGlobal: Boolean,
    minDuration: Int,
): Pair<Int, Easing> {
    val from = fromProgress.coerceIn(0f, 1f)
    val to = toProgress.coerceIn(0f, 1f)
    val span = to - from
    if (abs(span) < 1e-4f) {
        return minDuration.coerceAtMost(totalDuration) to fullEasing
    }

    // 全局进度 = from 时，fullEasing 的参数 u0
    val eAtFrom = if (invertGlobal) 1f - from else from
    val u0 = inverseEasingTime(fullEasing, eAtFrom)
    val durationMs = (((1f - u0) * totalDuration).toInt()).coerceIn(minDuration, totalDuration)

    val local = Easing { fraction ->
        val u = u0 + (1f - u0) * fraction.coerceIn(0f, 1f)
        val e = fullEasing.transform(u)
        val global = if (invertGlobal) 1f - e else e
        ((global - from) / span).coerceIn(0f, 1f)
    }
    return durationMs to local
}

/** 二分求 easing(u) = progress 的 u ∈ [0,1] */
private fun inverseEasingTime(easing: Easing, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return 0f
    if (p >= 1f) return 1f
    var lo = 0f
    var hi = 1f
    repeat(18) {
        val mid = (lo + hi) * 0.5f
        if (easing.transform(mid) < p) lo = mid else hi = mid
    }
    return (lo + hi) * 0.5f
}

val LocalSecondaryPageTransition = staticCompositionLocalOf {
    SecondaryPageTransitionController()
}

@Composable
fun SecondaryPageEnterTransition(
    content: @Composable () -> Unit,
) {
    val controller = LocalSecondaryPageTransition.current
    val hostActivity = LocalContext.current as? Activity
    // 只持有 Animatable 引用。progress.value 必须在 graphicsLayer 内读：
    // 组合期读取会让动画每帧重组整棵二级页（偏好设置等重 UI），是掉帧主因。
    // 曲线/时长/视差比例/压暗/圆角裁切均不变。
    val progress = controller.progress
    /** 二级页内容壳是否已真实绘制过一帧（drawWithContent）；冷启动关于页首构较慢，必须等它再推下层 */
    var contentDrawn by remember { mutableStateOf(false) }

    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val isEmbedded = windowWidthPx > 0 &&
        with(density) { windowWidthPx.toDp() } < screenWidthDp.dp * 0.85f

    val screenCornerRadiusPx = rememberScreenCornerRadiusPx()
    val screenClipShape = remember(screenCornerRadiusPx, density) {
        if (screenCornerRadiusPx > 0f) {
            with(density) { ContinuousRoundedRectangle(screenCornerRadiusPx.toDp()) }
        } else {
            null
        }
    }

    LaunchedEffect(Unit) {
        hostActivity?.let { SecondaryPushParallax.noteOpen(controller, it) }
        if (controller.isEnterSettled) {
            SecondaryPushParallax.applyTransitionProgress(1f)
            return@LaunchedEffect
        }
        // 等首帧期间可能已有手势取消并发起 restore：交给 restoreToken 效果，避免双动画抢同一 Animatable
        if (controller.restoreToken > 0) {
            return@LaunchedEffect
        }
        // 只认内容壳 drawWithContent 置起的 contentDrawn。
        // 不能用 ViewTreeObserver.OnDrawListener：它在本帧真正绘制子节点之前就会回调，
        // 压暗层/空树一画就 resume → 入场提前开跑。此时新页尚未渲染，只能看到下层被视差推走
        // + 透明窗黑底；等 About 等重页首构完成时 progress 已到 1，内容闪现。
        // 冷启动关于页首构很重，这里带超时：超时后仍入场，但页壳已铺 surface，不会整段黑。
        if (!contentDrawn) {
            withTimeoutOrNull(FIRST_FRAME_WAIT_MS.milliseconds) {
                while (!contentDrawn) {
                    withFrameNanos { }
                }
            }
        }
        // 首帧等待结束后手势可能已介入
        if (controller.restoreToken > 0 || controller.isEnterSettled) {
            return@LaunchedEffect
        }
        // 内容壳已至少绘制过一帧（或超时），再驱动：新页滑入 + 下层视差，两者同相位
        controller.animateEnter()
    }

    // 注意：不要在这里 DisposableEffect→noteClose。
    // 主题/重页首构导致组合树短暂重建时 dispose 会把下层视差打回 0，
    // 与进行中的入场动画打架，表现为旧页左右闪。
    // 真正的关闭登记在 SecondaryActivity.onDestroy / 出场动画完成时 hardClose。

    // 出场：必须在这里跑，才有 MonotonicFrameClock
    LaunchedEffect(controller.exitRequested) {
        if (controller.exitRequested) {
            controller.animateExit()
            controller.exitRequested = false
            SecondaryPushParallax.noteClose(controller, hardClose = true)
            controller.onExitComplete?.invoke()
        }
    }

    // 预测性返回取消：从当前跟手位置弹回完全显示。
    // key 用递增 token：上一次 restore 被新手势 snapTo 打断后，Effect 被取消，
    // Boolean 标志会卡在 true，导致后续取消再也无法启动回弹（页面停在半路）。
    LaunchedEffect(controller.restoreToken) {
        if (controller.restoreToken <= 0) return@LaunchedEffect
        controller.restoreFromGesture()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 下层主页压暗（progress 只在 layer 内读，避免每帧重组）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value * SECONDARY_BG_DIM }
                .background(Color.Black),
        )

        // 首帧标志：只挂在内容壳上；壳带 surface，首绘即代表「新页这一层已经能被看见」
        val contentDrawnModifier = if (contentDrawn) {
            Modifier
        } else {
            Modifier.drawWithContent {
                drawContent()
                contentDrawn = true
            }
        }
        val pageSurface = MiuixTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(contentDrawnModifier)
                .graphicsLayer {
                    val p = progress.value
                    if (isEmbedded) {
                        alpha = p
                    } else {
                        translationX = (1f - p) * size.width
                    }
                    applyScreenClipDuringTransition(p, screenClipShape)
                }
                // 滑入页壳铺 surface：关于页等首构慢时，页体一进场就有底色，
                // 不会整页透明被当成「新页没加载出来」
                .background(pageSurface),
        ) {
            content()
        }
    }
}

/** 动画/手势过程中（0 < p < 1）用 Kyant 连续圆角裁切推入页，推满后恢复直角 */
private fun GraphicsLayerScope.applyScreenClipDuringTransition(
    progress: Float,
    shape: Shape?,
) {
    if (shape != null && progress > 0f && progress < 1f) {
        this.shape = shape
        clip = true
    } else {
        clip = false
    }
}

@Composable
private fun rememberScreenCornerRadiusPx(): Float {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        try {
            val multiWindow = (context as? Activity)?.isInMultiWindowMode == true
            if (multiWindow) {
                20f * density.density
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                @SuppressLint("WrongConstant")
                wm.currentWindowMetrics.windowInsets.getRoundedCorner(0)?.radius?.toFloat() ?: 0f
            }
        } catch (_: Exception) {
            0f
        }
    }
}

/** 入场：更晚进入减速，尾段拖得更长更慢 */
private val SecondaryEnterEasing = OobeQuartOutSoftStartEasing

/** 退出：起步更缓，后段正常滑出 */
private val SecondaryExitEasing = CubicBezierEasing(0.36f, 0.18f, 0.3f, 0.85f)



/** 下层页压暗强度（0=不压，1=全黑）*/
private const val SECONDARY_BG_DIM = 0.42f

/** 冷启动重页（关于等）内容壳首绘等待上限；超时仍要带 surface 入场，避免透明空窗黑底 */
private const val FIRST_FRAME_WAIT_MS = 500L

private const val ENTER_DURATION = 600
private const val EXIT_DURATION = 320

/** 手势松手吸附最短时长，避免剩余行程极短时闪一下 */
private const val MIN_GESTURE_SETTLE_MS = 100

/** 取消回弹最短时长：保证强减速段能被看出来，否则像硬吸 */
private const val MIN_GESTURE_RESTORE_MS = 200
