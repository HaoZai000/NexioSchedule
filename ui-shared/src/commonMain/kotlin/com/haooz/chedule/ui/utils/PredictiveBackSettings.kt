package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 预测性返回动画全局开关（设置 →「个性化」→「交互」中可切换，默认开启）。
 *
 * 关闭后：各组件仍拦截返回（关闭行为不变），但手势进度不再驱动动画，
 * 返回时直接关闭、无手势跟随动画。
 */
object PredictiveBackSettings {

    const val KEY_PREDICTIVE_BACK_ANIMATION = "predictive_back_animation_enabled"

    // ⚠ 必须是 Compose 状态，不能是裸 @Volatile 变量（曾经是，代价是一次线上回归）：
    // 根主题在组合期把本值读进 `LocalPredictiveBackEnabled`，而弹窗 / 底部弹窗是在
    // root host 里渲染的，主题走 `MiuixOverlayTheme` 快照 + `SideEffect` 回灌 ——
    // 裸变量改值不会让任何组合失效，快照就永远停在旧值，表现为「开关改了要重进应用才生效」。
    // 换成 mutableStateOf 后，provides 点会随开关失效 → 快照刷新 → 弹窗当帧跟着改。
    var enabled: Boolean by mutableStateOf(true)
}
