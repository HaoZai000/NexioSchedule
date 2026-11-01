// miuix-core 的 utils actual（鸿蒙端）。
//
// 以官方 `Utils.skiko.kt` 为蓝本，只改一处：`DialogProperties` 的参数集。
// `usePlatformInsets` / `animateTransition` / `decorFitsSystemWindows` 是 CMP 1.12 才加的，
// 1.9.2 没有 —— 这里只传两端都有的参数，语义不变（弹窗不要平台默认宽度、不要系统遮罩）。
package top.yukonga.miuix.kmp.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** 平台标识。miuix 只在少量布局里区分 Desktop/Android，鸿蒙对它是「非 Android 的 native 端」。 */
actual fun platform(): Platform = Platform.IOS

actual val hasFocusReassignBug: Boolean = false

@Composable
actual fun getRoundedCorner(): Dp = 0.dp

@Composable
actual fun RemovePlatformDialogDefaultEffects() {
    // Skia 系后端没有平台默认弹窗效果
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun platformDialogProperties(): DialogProperties = DialogProperties(
    dismissOnBackPress = false,
    usePlatformDefaultWidth = false,
    scrimColor = Color.Transparent,
)
