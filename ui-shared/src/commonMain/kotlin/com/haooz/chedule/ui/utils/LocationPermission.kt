package com.haooz.chedule.ui.utils

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 请求定位权限。
 *
 * 引导页的「使用位置信息」开关打开后，点「继续」会调用它。宿主在能弹系统权限框的位置提供
 * 实现（Android 在 MainActivity）；没提供时为空实现，页面流程照常走完。
 */
val LocalLocationPermissionRequester = staticCompositionLocalOf<() -> Unit> { {} }
