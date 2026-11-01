package com.haooz.chedule.viewmodel

import androidx.compose.runtime.Composable

/**
 * 鸿蒙端的 ViewModel 工厂。
 *
 * 与 iOS 同一个理由：没有 Activity、也没有 `LocalViewModelStoreOwner`，`viewModel()` 会抛
 * `UnsupportedOperationException`（见 `ViewModelFactory` 的 KDoc），所以退一档用**进程级单例** ——
 * 语义等价于「整个 App 只有一个宿主页面」，与 Android 单 Activity 架构的实例边界一致。
 *
 * ⚠ 副作用：页面退出不回收 VM。四个 VM 都只是 `:core` 仓储的观察窗口，不持有页面级资源，
 * 长住进程是安全的。
 */
@Composable
actual fun courseViewModel(): CourseViewModel = ViewModelSingletonHolder.course

@Composable
actual fun settingsViewModel(): SettingsViewModel = ViewModelSingletonHolder.settings

@Composable
actual fun scheduleViewModel(): ScheduleViewModel = ViewModelSingletonHolder.schedule

@Composable
actual fun shiftViewModel(): ShiftViewModel = ViewModelSingletonHolder.shift
