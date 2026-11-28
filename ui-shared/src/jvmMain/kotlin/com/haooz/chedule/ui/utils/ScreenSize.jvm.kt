package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

// 窗口像素按当前密度换算为 dp；密度无效时回退到像素值。
@Composable
actual fun screenWidthDp(): Int {
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val density = LocalDensity.current.density
    val widthDp = if (density > 0f) widthPx / density else widthPx
    return widthDp.toInt()
}

@Composable
actual fun screenHeightDp(): Int {
    val heightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val density = LocalDensity.current.density
    val heightDp = if (density > 0f) heightPx / density else heightPx
    return heightDp.toInt()
}
