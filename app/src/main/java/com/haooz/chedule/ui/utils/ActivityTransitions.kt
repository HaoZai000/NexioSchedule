package com.haooz.chedule.ui.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
import java.lang.ref.WeakReference

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
        mainDecor = WeakReference(activity.window.decorView)
        if (openPages.isEmpty()) {
            applyToView(mainDecor, 0f)
            mainFraction = 0f
        } else if (openPages.size == 1) {
            applyToView(mainDecor, mainFraction)
        }
        // 嵌套打开时主页保持既有偏移
    }

    fun noteOpen(token: Any, activity: Activity) {
        if (openPages.any { it.token === token }) return
        openPages.add(Layer(token, activity))
    }

    fun noteClose(token: Any) {
        val idx = openPages.indexOfFirst { it.token === token }
        if (idx < 0) return
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
        decor.post {
            val w = decor.width.toFloat()
            if (w <= 0f) return@post
            decor.translationX = -SECONDARY_PUSH_PARALLAX * f * w
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

    /** 顶栏返回置 true，由组合内动画消费 */
    var exitRequested by mutableStateOf(false)

    /** 预测性返回取消时置 true，组合内回弹到完全显示 */
    var restoreRequested by mutableStateOf(false)

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
        SecondaryPushParallax.applyTransitionProgress(1f)
    }

    /**
     * 预测性返回取消回弹：从当前进度弹回完全显示。
     * 在入场曲线上反解当前进度对应的时间点，用剩余时间播出，且 easing 取曲线尾段。
     */
    suspend fun restoreFromGesture() {
        val from = progress.value.coerceIn(0f, 1f)
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
        progress.snapTo(value)
        SecondaryPushParallax.applyTransitionProgress(value)
    }

    fun requestExit() {
        exitRequested = true
    }

    fun requestRestore() {
        restoreRequested = true
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
    if (kotlin.math.abs(span) < 1e-4f) {
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
    val p = controller.progress.value

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
        controller.animateEnter()
    }

    // 组合销毁（finish / 主题等路径）时补偿注销，避免 open 计数泄漏
    DisposableEffect(controller) {
        onDispose {
            SecondaryPushParallax.noteClose(controller)
        }
    }

    // 出场：必须在这里跑，才有 MonotonicFrameClock
    LaunchedEffect(controller.exitRequested) {
        if (controller.exitRequested) {
            controller.animateExit()
            controller.exitRequested = false
            SecondaryPushParallax.noteClose(controller)
            controller.onExitComplete?.invoke()
        }
    }

    // 预测性返回取消：从当前跟手位置弹回。
    // 先播完再清标志——否则 key 变化取消本 Effect，页面停在半路。
    LaunchedEffect(controller.restoreRequested) {
        if (controller.restoreRequested) {
            controller.restoreFromGesture()
            controller.restoreRequested = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 下层主页压暗
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p * SECONDARY_BG_DIM }
                .background(Color.Black),
        )
        // 不要在这里 background()：会盖住下层 Activity。页面自身 Scaffold 已有不透明底。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isEmbedded) {
                        alpha = p
                    } else {
                        translationX = (1f - p) * size.width
                    }
                    applyScreenClipDuringTransition(p, screenClipShape)
                },
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



/** 下层主页压暗强度（0=不压，1=全黑）*/
private const val SECONDARY_BG_DIM = 0.42f

private const val ENTER_DURATION = 600
private const val EXIT_DURATION = 320

/** 手势松手吸附最短时长，避免剩余行程极短时闪一下 */
private const val MIN_GESTURE_SETTLE_MS = 100

/** 取消回弹最短时长：保证强减速段能被看出来，否则像硬吸 */
private const val MIN_GESTURE_RESTORE_MS = 200
