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
import com.haooz.chedule.ui.utils.suppressCloseTransition
import com.haooz.chedule.ui.utils.suppressOpenTransition
import kotlin.coroutines.cancellation.CancellationException

/**
 * 二级页基类：打开压掉系统动画、Compose 右推入场（半透明窗保留下层主页）；
 * 顶栏返回经 [finishSecondary] 组合内播出场；侧滑/系统返回由预测性返回进度驱动同一 progress。
 */
open class SecondaryActivity : ComponentActivity() {

    val pageTransition = SecondaryPageTransitionController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressOpenTransition()
        pageTransition.onExitComplete = { finishWithNoWindowAnim() }
    }

    override fun onDestroy() {
        // 异常销毁（配置变更/系统回收）时补偿，避免主页视差登记泄漏
        SecondaryPushParallax.noteClose(pageTransition)
        super.onDestroy()
    }

    internal fun finishWithNoWindowAnim() {
        if (isFinishing || isDestroyed) return
        suppressCloseTransition()
        super.finish()
        suppressCloseTransition()
    }

    /** 顶栏返回：组合内播出场再 finish */
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

@Composable
private fun SecondaryPagePredictiveBack(activity: SecondaryActivity) {
    val controller = activity.pageTransition
    PredictiveBackHandler { progress ->
        var sawGesture = false
        // 手势起点处的页面进度；入场未播完时应从当前值继续，而不是从 1 重开
        var gestureBase = 1f
        try {
            progress.collect { backEvent ->
                if (!sawGesture) {
                    sawGesture = true
                    // 仅开启跟手时才冻结进度并打断入场；关闭时让入场继续播完
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
                // 已跟手到接近关闭：归零后直接 finish
                controller.snapGestureProgress(0f)
                SecondaryPushParallax.noteClose(controller)
                activity.finishWithNoWindowAnim()
            } else {
                // 未跟手（返回键 / 开关关闭）：播正常出场，避免整页闪退
                activity.finishSecondary()
            }
        } catch (c: CancellationException) {
            // 开关关闭时未冻结进度，入场会自己跑完，无需回弹
            if (PredictiveBackSettings.enabled) {
                controller.requestRestore()
            }
            throw c
        }
    }
}
