package top.yukonga.miuix.kmp.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Skia（iOS / Desktop / wasm）侧：按窗口像素宽度 + 密度换算 dp，与 Android 的
 * `screenWidthDp >= 600` 口径等价。
 *
 * 为什么用窗口尺寸而不是布局约束：本函数在 `CollapsibleTopAppBar` 的组合入口被调用，
 * 那里的 `maxWidth` 受父容器（可能是平板分栏里的侧栏）影响，宽度会偏小；
 * 而原 Android 行为判的是**整个屏幕**，用窗口尺寸才与迁移前一致。
 *
 * `LocalWindowInfo.containerSize` 是 CMP 的公共 API，desktop/iOS 均可用。
 * 读不到有效密度时（某些宿主）保守返回 false（按手机布局），
 * 与调用方显式传 `showLargeTitle` 时的行为一致。
 */
@Composable
actual fun isTabletWidth(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val density = LocalDensity.current.density
    val widthDp = if (density > 0f) widthPx / density else widthPx
    return widthDp >= 600f
}