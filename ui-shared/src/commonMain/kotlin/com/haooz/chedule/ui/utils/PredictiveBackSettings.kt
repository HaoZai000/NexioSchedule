package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 预测性返回动画全局开关（应用个性化中可切换，默认开启）。
 *
 * 关闭后：各组件仍拦截返回（关闭行为不变），但手势进度不再驱动动画，
 * 返回时直接关闭、无手势跟随动画。
 */
object PredictiveBackSettings {

    const val KEY_PREDICTIVE_BACK_ANIMATION = "predictive_back_animation_enabled"

    // 使用 Compose 状态，让主题与弹窗宿主在切换时重新读取手势动画设置。
    var enabled: Boolean by mutableStateOf(true)
}
