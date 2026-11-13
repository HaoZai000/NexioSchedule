package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 宿主接管主界面右上角那两颗按钮。
 *
 * 目前没有平台提供它（鸿蒙已改回 Compose 自绘，iOS 走 [HostNavigationState]）；
 * 保留这层协议是为了将来有原生 chrome 的宿主时不必再改页面。
 */
class NativeTopBarHost {

    var ownsTopRight by mutableStateOf(false)

    /**
     * 最近一次发布的按钮语义；null = 当前没有。
     *
     * 故意不是 Compose 状态：唯一消费者是宿主桥接，走 snapshot 观察要多等一帧调度，
     * 翻页时按钮就明显跟不上手。写入即在同一次 apply 里同步回调 [onState]。
     */
    var state: NativeTopBarState? = null
        set(value) {
            if (field == value) return
            field = value
            onState?.invoke(value)
        }

    /** `state` 的同步出口，由宿主桥接安装。 */
    var onState: ((NativeTopBarState?) -> Unit)? = null

    /** true = 暂时整条收起（push 了子页）。 */
    var suppressed by mutableStateOf(false)

    /** 按钮点击（`switchSchedule` / `jumpWeek` / `jumpToDate` / `courseManage` / `customize`）。 */
    var onAction: ((String) -> Unit)? = null
}

val LocalNativeTopBarHost = staticCompositionLocalOf<NativeTopBarHost?> { null }

/** 一页的按钮组：内容 + 相对屏幕右上角的平移量（跟随主 pager 翻页）。 */
data class NativeTopBarGroup(
    val showsSwitch: Boolean,
    val showsMore: Boolean,
    val menu: List<NativeTopBarMenuItem>,
    val offsetXDp: Float,
    val offsetYDp: Float,
)

/** 宿主画右上角按钮要的语义：位置 + 图标色 + 两页各自的菜单与位移。 */
data class NativeTopBarState(
    val today: NativeTopBarGroup,
    val schedule: NativeTopBarGroup,
    val onSurfaceArgb: Long,
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
