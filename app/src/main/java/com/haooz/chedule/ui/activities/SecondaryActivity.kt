package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.LocalSecondaryPageTransition
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.SecondaryPageEnterTransition
import com.haooz.chedule.ui.utils.SecondaryPageTransitionController
import com.haooz.chedule.ui.utils.SecondaryPushParallax
import com.haooz.chedule.ui.utils.shouldLockMainPushParallax
import com.haooz.chedule.ui.utils.shouldPaintOpaquePaneSurface
import com.haooz.chedule.ui.utils.shouldSkipSecondaryEnterAnimation
import com.haooz.chedule.ui.utils.suppressCloseTransition
import com.haooz.chedule.ui.utils.suppressOpenTransition
import kotlin.coroutines.cancellation.CancellationException

/**
 * 二级页基类。
 *
 * - 打开：压掉系统转场，由 [SecondaryPageEnterTransition] 自绘入场
 * - 关闭：顶栏返回经 [finishSecondary] 播出场后再 finish；侧滑/返回键走预测性返回
 * - 分屏/嵌入：onCreate 时给窗口铺应用 surface，避免同级切换首帧闪背景
 * - 小窗 freeform：窗口保持半透明（主题默认），不铺不透明底，否则会盖住下层主页
 */
open class SecondaryActivity : ComponentActivity() {

    val pageTransition = SecondaryPageTransitionController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressOpenTransition()
        // 仅「确认是分栏窗格」才铺不透明 windowBackground。
        // 小窗/无法判定的 multi-window 保持主题半透明，否则盖住下层主页、入场变纯色。
        if (shouldPaintOpaquePaneSurface()) {
            SecondaryPushParallax.ensureWindowSurfaceBackground(this)
        }
        pageTransition.onExitComplete = { finishWithNoWindowAnim() }
    }

    override fun onDestroy() {
        // 仅在真正销毁时出栈；组合重建不可走这里
        SecondaryPushParallax.noteClose(pageTransition, hardClose = true)
        super.onDestroy()
    }

    internal fun finishWithNoWindowAnim() {
        if (isFinishing || isDestroyed) return
        suppressCloseTransition()
        super.finish()
        suppressCloseTransition()
    }

    /** 顶栏返回：请求出场动画，播完后由 onExitComplete finish */
    fun finishSecondary() {
        if (isFinishing || isDestroyed) return
        if (pageTransition.exitRequested) return
        pageTransition.requestExit()
    }

    protected fun setSecondaryContent(content: @Composable () -> Unit) {
        setContent {
            CompositionLocalProvider(LocalSecondaryPageTransition provides pageTransition) {
                CourseScheduleTheme {
                    SecondaryPagePredictiveBack(activity = this@SecondaryActivity)
                    SecondaryPageEnterTransition {
                        content()
                    }
                }
            }
        }
    }
}

/** 预测性返回：跟手时冻结/驱动 progress；松手吸附关闭或取消回弹 */
@Composable
private fun SecondaryPagePredictiveBack(activity: SecondaryActivity) {
    val controller = activity.pageTransition
    PredictiveBackHandler { progress ->
        var sawGesture = false
        // 手势起点进度；入场未播完时从当前值继续，而不是从 1 重开
        var gestureBase = 1f
        try {
            progress.collect { backEvent ->
                if (!sawGesture) {
                    sawGesture = true
                    if (PredictiveBackSettings.enabled) {
                        gestureBase = controller.progress.value.coerceIn(0f, 1f)
                        controller.snapGestureProgress(gestureBase)
                    }
                }
                if (PredictiveBackSettings.enabled) {
                    val p = (gestureBase * (1f - backEvent.progress)).coerceIn(0f, 1f)
                    controller.snapGestureProgress(p)
                }
            }
            if (sawGesture && PredictiveBackSettings.enabled) {
                if (activity.shouldSkipSecondaryEnterAnimation() ||
                    activity.shouldLockMainPushParallax()
                ) {
                    // 分屏：不播跟手归位动画，避免主页从左移位置弹回
                    controller.progress.snapTo(0f)
                    SecondaryPushParallax.applyTransitionProgress(0f)
                } else {
                    controller.animateGestureDismiss()
                }
                SecondaryPushParallax.noteClose(controller, hardClose = true)
                activity.finishWithNoWindowAnim()
            } else {
                // 未跟手（返回键 / 跟手开关关闭）
                activity.finishSecondary()
            }
        } catch (c: CancellationException) {
            if (PredictiveBackSettings.enabled) {
                controller.requestRestore()
            }
            throw c
        }
    }
}
