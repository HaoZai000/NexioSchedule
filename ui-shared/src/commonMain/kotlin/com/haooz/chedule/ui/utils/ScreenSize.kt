package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/** 当前窗口（而非物理屏幕）的宽度，单位 dp。 */
@Composable
expect fun screenWidthDp(): Int

/** 当前窗口的高度，单位 dp。 */
@Composable
expect fun screenHeightDp(): Int
