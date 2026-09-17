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
import androidx.compose.animation.core.FastOutSlowInEasing
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
 * 主页 push 视差：二级页盖住时 MainActivity 已 onPause、Compose 不再刷新，
 * 所以直接平移主页 decorView。用 token 集合而非计数，避免主题切换销毁组合导致计数泄漏。
 */
object SecondaryPushParallax {
    private var mainDecor: WeakReference<View>? = null
    private val openTokens = HashSet<Int>()
    private var lastFraction = 0f

    /** 当前是否只有（或正在打开）第一层二级页——只有这时才驱动主页左移 */
    val drivesMainParallax: Boolean get() = openTokens.size <= 1

    fun attachMainRoot(activity: Activity) {
        mainDecor = WeakReference(activity.window.decorView)
        // 无二级页时强制归位，兜底 lastFraction 异常残留
        if (openTokens.isEmpty()) {
            applyToMain(0f)
        } else {
            applyToMain(lastFraction)
        }
    }

    fun noteOpen(token: Any) {
        openTokens.add(System.identityHashCode(token))
    }

    fun noteClose(token: Any) {
        openTokens.remove(System.identityHashCode(token))
        if (openTokens.isEmpty()) {
            applyToMain(0f)
        }
    }

    /** 在 UI 线程把主页整窗平移；fraction: 0=原位，1=完全左移 */
    fun applyToMain(fraction: Float) {
        lastFraction = fraction.coerceIn(0f, 1f)
        val decor = mainDecor?.get() ?: return
        decor.post {
            val w = decor.width.toFloat()
            if (w <= 0f) return@post
            decor.translationX = -SECONDARY_PUSH_PARALLAX * lastFraction * w
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

    /** 顶栏返回置 true，由组合内动画消费 */
    var exitRequested by mutableStateOf(false)

    /** 预测性返回取消时置 true，组合内回弹到完全显示 */
    var restoreRequested by mutableStateOf(false)

    /** 出场动画播完后回调（finish） */
    var onExitComplete: (() -> Unit)? = null

    suspend fun animateEnter(durationMillis: Int = ENTER_DURATION) {
        val driveMain = SecondaryPushParallax.drivesMainParallax
        progress.snapTo(0f)
        if (driveMain) SecondaryPushParallax.applyToMain(0f)
        progress.animateTo(1f, tween(durationMillis, easing = SecondaryEnterEasing)) {
            if (driveMain) SecondaryPushParallax.applyToMain(this.value)
        }
        if (driveMain) SecondaryPushParallax.applyToMain(1f)
    }

    /**
     * 预测性返回取消回弹：从当前进度弹回完全显示。
     * 不要 snapTo(0)——那会先把整页甩到屏外，回弹被打断就只剩下层页。
     */
    suspend fun restoreFromGesture(durationMillis: Int = 220) {
        val driveMain = SecondaryPushParallax.drivesMainParallax
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing)) {
            if (driveMain) SecondaryPushParallax.applyToMain(this.value)
        }
        if (driveMain) SecondaryPushParallax.applyToMain(1f)
    }

    suspend fun animateExit(durationMillis: Int = EXIT_DURATION) {
        val driveMain = SecondaryPushParallax.drivesMainParallax
        progress.animateTo(0f, tween(durationMillis, easing = SecondaryExitEasing)) {
            if (driveMain) SecondaryPushParallax.applyToMain(this.value)
        }
        if (driveMain) SecondaryPushParallax.applyToMain(0f)
    }

    suspend fun snapGestureProgress(p: Float) {
        val value = p.coerceIn(0f, 1f)
        progress.snapTo(value)
        if (SecondaryPushParallax.drivesMainParallax) {
            SecondaryPushParallax.applyToMain(value)
        }
    }

    fun requestExit() {
        exitRequested = true
    }

    fun requestRestore() {
        restoreRequested = true
    }
}

val LocalSecondaryPageTransition = staticCompositionLocalOf {
    SecondaryPageTransitionController()
}

@Composable
fun SecondaryPageEnterTransition(
    content: @Composable () -> Unit,
) {
    val controller = LocalSecondaryPageTransition.current
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
        SecondaryPushParallax.noteOpen(controller)
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
