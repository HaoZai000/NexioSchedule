// 级联弹窗的共享动效常量
//
// 这些常量原先定义在 CascadingListPopupLayout.kt 里。该文件是本项目自研的
// 级联下拉实现、且 :app 侧零引用，此前按死代码删除了 —— 但 CascadingMorphContent.kt
// 仍在使用它们，等级联布局本身可以再考虑是否恢复，这里先把常量独立出来，
// 免得 Morph 动画跟着一起消失。

package top.yukonga.miuix.kmp.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 级联弹窗的圆角。 */
internal val CascadingPopupCornerRadius: Dp = 16.dp

/** 入场缩放的起始倍率（从 15% 长上来）。 */
internal const val ENTER_SCALE_FROM = 0.15f

/** 入场缩放的跨度，凑成 ENTER_SCALE_FROM + ENTER_SCALE_RANGE = 1f。 */
internal const val ENTER_SCALE_RANGE = 1f - ENTER_SCALE_FROM