/** 二级页导航转场：push 视差、入场/出场动画、预测性返回 */
package com.haooz.chedule.ui.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.Window
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import com.haooz.chedule.ui.effects.motion.OobeQuartOutSoftStartEasing
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

// region 常量

/** 下层页左移距离 / 窗口宽度（约 iOS push 的 0.25–0.30） */
const val SECONDARY_PUSH_PARALLAX = 0.24f

/** 下层页压暗强度：0=不压，1=全黑 */
private const val SECONDARY_BG_DIM = 0.42f

/** 入场时长 */
private const val ENTER_DURATION = 600

/** 出场时长 */
private const val EXIT_DURATION = 320

/** 手势松手吸附关闭的最短时长，避免剩余行程过短时闪一下 */
private const val MIN_GESTURE_SETTLE_MS = 100

/** 预测性返回取消回弹的最短时长，保证减速段可被看见 */
private const val MIN_GESTURE_RESTORE_MS = 200

/** 冷启动重页（如关于）内容壳首绘等待上限；超时仍入场，避免透明空窗 */
private const val FIRST_FRAME_WAIT_MS = 500L

/** 入场曲线：更晚进入减速，尾段更长更慢 */
private val SecondaryEnterEasing = OobeQuartOutSoftStartEasing

/** 出场曲线：起步更缓，后段正常滑出 */
private val SecondaryExitEasing = CubicBezierEasing(0.36f, 0.18f, 0.3f, 0.85f)

// endregion

// region 分屏判定与窗口启动工具

/**
 * 是否处于平板分窗 / Activity Embedding 窄栏。
 * 分栏内二级页在独立窗格切换：不播 Compose 右推动画，主界面也不左移。
 */
fun Activity?.isInSecondarySplitMode(): Boolean {
    val act = this ?: return false
    if (act.isInMultiWindowMode) return true
    val decorW = act.window.decorView.width
    val screenW = act.resources.displayMetrics.widthPixels
    return screenW > 0 && decorW in 1 until (screenW * 0.85f).toInt()
}

/** 压掉 Activity 打开的系统转场（二级页由 Compose 自绘入场） */
fun Activity.suppressOpenTransition() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

/** 压掉 Activity 关闭的系统转场 */
fun Activity.suppressCloseTransition() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

/** 以无系统动画的方式打开二级页 */
fun Context.openSecondaryPage(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    val activity = this as? Activity
    val options = activity?.let {
        ActivityOptions.makeCustomAnimation(it, 0, 0).toBundle()
    }
    startActivity(intent, options)
    activity?.suppressOpenTransition()
}

/** Activity Result 启动二级页时使用的无动画 Options */
fun secondaryOpenOptionsCompat(activity: Activity): ActivityOptionsCompat {
    return ActivityOptionsCompat.makeCustomAnimation(activity, 0, 0)
}

// endregion

// region SecondaryPushParallax — 导航栈 push 视差

/**
 * 导航栈 push 视差。
 *
 * 下层 Activity 已 onPause，Compose 不再刷新，只能直接改 View 的 `translationX`。
 *
 * 实现要点：
 * - 只平移 `android.R.id.content`；decor 不动，并铺应用 surface，避免右缘残影叠层
 * - 被推动的 content 入栈时即挂 `LAYER_TYPE_HARDWARE`，避免与位移同帧改层类型导致闪烁
 * - 手机全屏：一级→二级推主页，二级→三级推栈顶下方的二级页
 * - 平板分窗 / Embedding：主页不左移
 */
object SecondaryPushParallax {

    private class Layer(
        val token: Any,
        val activityRef: WeakReference<Activity>,
        val content: WeakReference<View>,
    )

    private var mainContent: WeakReference<View>? = null

    /** 已打开的二级页，按打开顺序；末尾为栈顶 */
    private val openPages = mutableListOf<Layer>()

    /** 主页当前视差进度（0–1），退出分窗后用于恢复 */
    private var mainFraction = 0f

    /** 系统分窗/自由窗口时主页不参与左移 */
    @Volatile
    private var mainInMultiWindow = false

    /** 分窗状态变化时由 MainActivity 调用 */
    fun setMainMultiWindowMode(enabled: Boolean) {
        mainInMultiWindow = enabled
        if (enabled) {
            mainFraction = 0f
            applyToView(mainContent, 0f)
        } else if (openPages.size == 1) {
            applyToView(mainContent, mainFraction.takeIf { it > 0f } ?: 1f)
        }
    }

    /** MainActivity onCreate/onResume：登记主页 content，并保证窗口底色与层类型 */
    fun attachMainRoot(activity: Activity) {
        val window = activity.window
        val decor = window.decorView
        val content = resolveContentRoot(activity)

        mainInMultiWindow = activity.isInMultiWindowMode
        ensureAppSurfaceBackground(activity, window)
        if (decor.layerType == View.LAYER_TYPE_HARDWARE) {
            decor.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        if (content !== decor && content.layerType != View.LAYER_TYPE_HARDWARE) {
            content.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        mainContent = WeakReference(content)
        pruneDeadLayers()
        when {
            openPages.isEmpty() || mainInMultiWindow -> {
                if (openPages.isEmpty()) mainFraction = 0f
                applyToView(mainContent, 0f)
            }
            openPages.size == 1 -> applyToView(mainContent, mainFraction)
        }
    }

    /** 分屏二级页 onCreate：窗口先铺应用 surface，避免同级切换首帧闪背景 */
    fun ensureWindowSurfaceBackground(activity: Activity) {
        ensureAppSurfaceBackground(activity, activity.window)
    }

    /** 二级页入栈（SecondaryPageEnterTransition 首次组合时） */
    fun noteOpen(token: Any, activity: Activity) {
        pruneDeadLayers()
        val content = resolveContentRoot(activity)
        if (content.layerType != View.LAYER_TYPE_HARDWARE) {
            content.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        val existing = openPages.indexOfFirst { it.token === token }
        if (existing >= 0) {
            openPages[existing] = layerOf(token, activity)
            return
        }
        openPages.add(layerOf(token, activity))
    }

    /**
     * 二级页关闭登记。
     * [hardClose]=true 才出栈并归零视差（Activity 销毁或出场动画结束）。
     * 组合重建等临时 dispose 不可传 true，否则会与入场动画抢同一进度。
     */
    fun noteClose(token: Any, hardClose: Boolean = true) {
        if (!hardClose) return
        pruneDeadLayers()
        val idx = openPages.indexOfFirst { it.token === token }
        if (idx < 0) return
        openPages.removeAt(idx)
        when {
            openPages.isEmpty() -> {
                mainFraction = 0f
                applyToView(mainContent, 0f)
            }
            idx >= 1 -> applyToView(openPages[idx - 1].content, 0f)
        }
    }

    /**
     * 转场进度驱动下层视差：
     * - 栈上仅一层二级（一级→二级）→ 推主页（分窗时强制 0）
     * - 栈上多层（二级→三级）→ 推栈顶下方那层二级页
     */
    fun applyTransitionProgress(fraction: Float) {
        pruneDeadLayers()
        val f = fraction.coerceIn(0f, 1f)
        if (openPages.size <= 1) {
            if (mainInMultiWindow || isMainNarrowWindow()) {
                mainFraction = 0f
                applyToView(mainContent, 0f)
            } else {
                mainFraction = f
                applyToView(mainContent, f)
            }
        } else {
            applyToView(openPages[openPages.size - 2].content, f)
            if (mainInMultiWindow || isMainNarrowWindow()) {
                applyToView(mainContent, 0f)
            }
        }
    }

    private fun layerOf(token: Any, activity: Activity): Layer {
        return Layer(
            token = token,
            activityRef = WeakReference(activity),
            content = WeakReference(resolveContentRoot(activity)),
        )
    }

    private fun resolveContentRoot(activity: Activity): View {
        return activity.findViewById(android.R.id.content) ?: activity.window.decorView
    }

    private fun View.hostActivity(): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /** 剔除已销毁页，避免 noteClose 漏调时 openPages 无限增长 */
    private fun pruneDeadLayers() {
        openPages.removeAll { layer ->
            val view = layer.content.get()
            val act = layer.activityRef.get() ?: view?.hostActivity()
            view == null || act == null || act.isDestroyed
        }
    }

    /** 窗口底色与 Miuix surface 对齐：浅色 0xFFF4F4F4，深色 0xFF000000 */
    private fun ensureAppSurfaceBackground(activity: Activity, window: Window) {
        val color = resolveAppSurfaceColor(activity)
        val current = (window.decorView.background as? ColorDrawable)?.color
        if (current != color) {
            window.setBackgroundDrawable(ColorDrawable(color))
        }
    }

    private fun resolveAppSurfaceColor(activity: Activity): Int {
        val prefs = activity.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "system") ?: "system"
        val dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> {
                val night = activity.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                night == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        return if (dark) 0xFF000000.toInt() else 0xFFF4F4F4.toInt()
    }

    /** 主页是否处于窄窗（Embedding 左栏等） */
    private fun isMainNarrowWindow(): Boolean {
        val content = mainContent?.get() ?: return false
        val w = content.width
        val screenW = content.resources.displayMetrics.widthPixels
        return screenW > 0 && w in 1 until (screenW * 0.85f).toInt()
    }

    private fun isMainView(view: View): Boolean = mainContent?.get() === view

    private fun applyToView(contentRef: WeakReference<View>?, fraction: Float) {
        val content = contentRef?.get() ?: return
        val f = fraction.coerceIn(0f, 1f)
        fun apply() {
            if (isMainView(content) && (mainInMultiWindow || isMainNarrowWindow())) {
                if (content.translationX != 0f) {
                    content.translationX = 0f
                    content.invalidate()
                    (content.parent as? View)?.invalidate()
                }
                return
            }
            if (content.layerType != View.LAYER_TYPE_HARDWARE) {
                content.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            val w = content.width.toFloat().takeIf { it > 0f }
                ?: content.resources.displayMetrics.widthPixels.toFloat()
            if (w <= 0f) return
            val target = -SECONDARY_PUSH_PARALLAX * f * w
            if (abs(content.translationX - target) < 0.5f) {
                if (target == 0f && content.translationX != 0f) {
                    content.translationX = 0f
                    (content.parent as? View)?.invalidate()
                }
                return
            }
            // 硬件层下 translationX 由合成器处理，不必再 invalidate 整窗
            content.translationX = target
        }
        if ((content.width > 0 || content.resources.displayMetrics.widthPixels > 0) &&
            Looper.myLooper() == Looper.getMainLooper()
        ) {
            apply()
        } else {
            content.post { apply() }
        }
    }
}

// endregion

// region SecondaryPageTransitionController — 入场/出场进度

/**
 * 二级页转场进度：0=整页在窗口右侧外，1=完全覆盖。
 *
 * 出场/手势动画必须在组合作用域内驱动（LaunchedEffect 等），
 * 否则 Animatable 缺少 MonotonicFrameClock，会立刻结束或表现为闪退。
 */
class SecondaryPageTransitionController {

    val progress = Animatable(0f)

    /** 入场是否已开始/播过；组合重建时避免从 0 重播 */
    private var enterPlayed = false

    val isEnterSettled: Boolean
        get() = enterPlayed && progress.value >= 0.999f

    /** 分屏同级切换：标记入场已完成，不播滑入动画 */
    fun markEnterSettledWithoutAnimation() {
        enterPlayed = true
    }

    /** 顶栏返回置 true，由 SecondaryPageEnterTransition 消费 */
    var exitRequested by mutableStateOf(false)

    /**
     * 预测性返回取消时递增，作为 LaunchedEffect key。
     * 不能用 Boolean：新手势 snapTo 会取消进行中的 restore，Boolean 会卡在 true。
     */
    var restoreToken by mutableIntStateOf(0)
        private set

    /** 出场动画播完后回调（通常用于 finish） */
    var onExitComplete: (() -> Unit)? = null

    suspend fun animateEnter(durationMillis: Int = ENTER_DURATION) {
        if (isEnterSettled) {
            SecondaryPushParallax.applyTransitionProgress(1f)
            return
        }
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

    /** 预测性返回取消：从当前进度弹回完全显示 */
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

    /** 松手吸附关闭：按出场曲线反解剩余时长 */
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
        enterPlayed = true
        val value = p.coerceIn(0f, 1f)
        progress.snapTo(value)
        SecondaryPushParallax.applyTransitionProgress(value)
    }

    fun requestExit() {
        exitRequested = true
    }

    fun requestRestore() {
        restoreToken++
    }

    private suspend fun settleFullyOpen() {
        progress.snapTo(1f)
        enterPlayed = true
        SecondaryPushParallax.applyTransitionProgress(1f)
    }
}

/**
 * 在全局开/关曲线上，从 [fromProgress] 播到 [toProgress] 的剩余段。
 * 返回 (剩余时长, 将该尾段映射到 0..1 的局部 easing)。
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

// endregion

// region SecondaryPageEnterTransition — Compose 入场壳

val LocalSecondaryPageTransition = staticCompositionLocalOf {
    SecondaryPageTransitionController()
}

/**
 * 二级页内容壳：压暗层 + 可选整窗 surface + 从右滑入的页面。
 *
 * - progress 只在 graphicsLayer 内读，避免动画每帧重组整页
 * - 分屏/嵌入：跳过入场动画，并整窗铺 surface（防同级切换闪背景）
 * - 手机全屏：不铺整窗底色，避免盖住被推开的一级页
 * - 关闭登记不在 DisposableEffect：组合重建会误伤下层视差
 */
@Composable
fun SecondaryPageEnterTransition(
    content: @Composable () -> Unit,
) {
    val controller = LocalSecondaryPageTransition.current
    val hostActivity = LocalContext.current as? Activity
    val progress = controller.progress
    var contentDrawn by remember { mutableStateOf(false) }

    val paintsFullSurface = remember(hostActivity) {
        hostActivity?.isInMultiWindowMode == true || hostActivity.isInSecondarySplitMode()
    }

    val density = LocalDensity.current
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

        // 分屏/嵌入：同级二级切换不播入场动画
        if (hostActivity.isInSecondarySplitMode()) {
            controller.markEnterSettledWithoutAnimation()
            controller.progress.snapTo(1f)
            SecondaryPushParallax.applyTransitionProgress(0f)
            return@LaunchedEffect
        }
        if (controller.isEnterSettled) {
            SecondaryPushParallax.applyTransitionProgress(1f)
            return@LaunchedEffect
        }
        if (controller.restoreToken > 0) return@LaunchedEffect

        // 等内容壳真实绘过一帧再入场；OnDrawListener 会过早回调，不可用
        if (!contentDrawn) {
            withTimeoutOrNull(FIRST_FRAME_WAIT_MS.milliseconds) {
                while (!contentDrawn) {
                    withFrameNanos { }
                }
            }
        }
        if (controller.restoreToken > 0 || controller.isEnterSettled) {
            return@LaunchedEffect
        }
        controller.animateEnter()
    }

    LaunchedEffect(controller.exitRequested) {
        if (controller.exitRequested) {
            controller.animateExit()
            controller.exitRequested = false
            SecondaryPushParallax.noteClose(controller, hardClose = true)
            controller.onExitComplete?.invoke()
        }
    }

    LaunchedEffect(controller.restoreToken) {
        if (controller.restoreToken <= 0) return@LaunchedEffect
        controller.restoreFromGesture()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 下层压暗
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value * SECONDARY_BG_DIM }
                .background(Color.Black),
        )

        val pageSurface = MiuixTheme.colorScheme.surface
        // 仅分屏铺整窗 surface（不随 content 平移）
        if (paintsFullSurface) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageSurface)
            )
        }

        val contentDrawnModifier = if (contentDrawn) {
            Modifier
        } else {
            Modifier.drawWithContent {
                drawContent()
                contentDrawn = true
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(contentDrawnModifier)
                .graphicsLayer {
                    val p = progress.value
                    translationX = (1f - p) * size.width
                    applyScreenClipDuringTransition(p, screenClipShape)
                }
                .background(pageSurface),
        ) {
            content()
        }
    }
}

/** 转场过程中（0<p<1）用连续圆角裁切推入页，推满后恢复直角 */
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

// endregion
