package com.haooz.chedule.ui.utils

/**
 * 预测性返回动画全局开关（应用偏好设置中可切换，默认开启）。
 *
 * 关闭后：各组件仍拦截返回（关闭行为不变），但手势进度不再驱动动画，
 * 返回时直接关闭、无手势跟随动画。
 */
object PredictiveBackSettings {

    const val KEY_PREDICTIVE_BACK_ANIMATION = "predictive_back_animation_enabled"

    @Volatile
    var enabled: Boolean = true
}
