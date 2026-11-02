package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** 宿主接管主界面右上角那两颗按钮（当前只有鸿蒙 ArkUI）。 */
class NativeTopBarHost {

    var ownsTopRight by mutableStateOf(false)

    /** 最近一次发布的按钮语义；null = 当前没有。 */
    var state by mutableStateOf<NativeTopBarState?>(null)

    /** true = 暂时整条收起（push 了子页）。 */
    var suppressed by mutableStateOf(false)

    /** 按钮点击（`switchSchedule` / `jumpWeek` / `courseManage` / `customize`）。 */
    var onAction: ((String) -> Unit)? = null
}

val LocalNativeTopBarHost = staticCompositionLocalOf<NativeTopBarHost?> { null }

/** 宿主画右上角按钮要的语义：位置 + 图标色 + 菜单项。 */
data class NativeTopBarState(
    val showsSwitch: Boolean,
    val showsMore: Boolean,
    val onSurfaceArgb: Long,
    val menu: List<NativeTopBarMenuItem>,
    /** 按钮顶边距窗口顶（dp，含状态栏），与 Compose 侧折叠顶栏同一中线。 */
    val topDp: Float,
    val sizeDp: Float,
    val spacingDp: Float,
    val rightPadDp: Float,
)

/** 「更多」菜单里的一项：只带 id 与文案，图标是 Compose 的 ImageVector，宿主拿不到。 */
data class NativeTopBarMenuItem(
    val id: String,
    val label: String,
)
