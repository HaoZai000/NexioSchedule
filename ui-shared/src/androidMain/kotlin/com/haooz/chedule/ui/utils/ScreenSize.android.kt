package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

// Configuration 报告当前窗口尺寸，适用于分屏与自由窗口。
@Composable
actual fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

@Composable
actual fun screenHeightDp(): Int = LocalConfiguration.current.screenHeightDp
