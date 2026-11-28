package com.haooz.chedule.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel

/** 实例由宿主确定作用域：Android 和桌面使用当前 ViewModelStoreOwner，iOS 在页面间共享实例。 */
@Composable
expect fun courseViewModel(): CourseViewModel

@Composable
expect fun settingsViewModel(): SettingsViewModel

@Composable
expect fun scheduleViewModel(): ScheduleViewModel

@Composable
expect fun shiftViewModel(): ShiftViewModel

/** 为使用进程作用域的宿主保存共享实例。 */
internal object ViewModelSingletonHolder {
    val course: CourseViewModel by lazy { CourseViewModel() }
    val settings: SettingsViewModel by lazy { SettingsViewModel() }
    val schedule: ScheduleViewModel by lazy { ScheduleViewModel() }
    val shift: ShiftViewModel by lazy { ShiftViewModel() }
}
