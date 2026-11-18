package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/** 屏幕物理圆角类型由 nexioui 提供，应用侧沿用旧包名引用。 */
typealias ScreenCornerRadiiPx = com.haooz.nexioui.window.ScreenCornerRadiiPx

/** Screen corners for page clipping. Unavailable corners are zero (no clipping). */
@Composable
expect fun rememberScreenCornerRadiiPx(): ScreenCornerRadiiPx

/** Uniform-radius consumers such as dialogs use the largest physical corner. */
@Composable
fun rememberScreenCornerRadiusPx(): Float = rememberScreenCornerRadiiPx().maxRadius
