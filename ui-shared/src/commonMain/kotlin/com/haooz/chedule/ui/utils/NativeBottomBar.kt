package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** 宿主接管主界面底部导航（当前只有鸿蒙 ArkUI）。 */
class NativeBottomBarHost {

    var ownsBottomBar by mutableStateOf(false)

    /** true = 暂时整条收起（课表外观页 / 切换课表页 / 详情页这些全屏层盖不住它）。 */
    var suppressed by mutableStateOf(false)

    /**
     * 最近一次发布的底栏语义。普通字段 + 同步回调，不走 snapshot，
     * 免得切 tab 时指示器晚一帧。
     */
    var state: NativeBottomBarState? = null
        set(value) {
            if (field == value) return
            field = value
            onState?.invoke(value)
        }

    var onState: ((NativeBottomBarState?) -> Unit)? = null

    /** 宿主点了某一格；`index == tabsCount` 是末尾的设置页签。 */
    var onTabSelected: ((Int) -> Unit)? = null
}

val LocalNativeBottomBarHost = staticCompositionLocalOf<NativeBottomBarHost?> { null }

/** 宿主画底栏要的语义与配色。 */
data class NativeBottomBarState(
    /** 当前选中格（排班模式恒 0）。 */
    val selectedTab: Int,
    /** 页签格数：普通 2（今日 / 课程表），排班 1（只有课程表）。设置格不算在内。 */
    val tabsCount: Int,
    /** 每一格的文案，长度与 [tabsCount] 一致。 */
    val labels: List<String>,
    val onSurfaceArgb: Long,
    /** 主题色，选中态的图标与文字用它。 */
    val accentArgb: Long,
    /** 胶囊高度。 */
    val heightDp: Float,
)

/** Compose 的 Color → 0xAARRGGBB（宿主按整数解析）。 */
internal fun Color.argbValue(): Long = toArgb().toLong() and 0xFFFFFFFFL
