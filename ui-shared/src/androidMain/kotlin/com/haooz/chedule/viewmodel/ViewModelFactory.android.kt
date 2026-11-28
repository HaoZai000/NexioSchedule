package com.haooz.chedule.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
actual fun courseViewModel(): CourseViewModel = viewModel()

@Composable
actual fun settingsViewModel(): SettingsViewModel = viewModel()

@Composable
actual fun scheduleViewModel(): ScheduleViewModel = viewModel()

@Composable
actual fun shiftViewModel(): ShiftViewModel = viewModel()
