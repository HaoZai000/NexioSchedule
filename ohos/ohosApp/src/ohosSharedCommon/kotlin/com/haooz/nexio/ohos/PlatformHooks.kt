package com.haooz.nexio.ohos

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 宿主提供的「选壁纸」钩子。
 *
 * UI 里的选图按钮在 `:ui-shared`（`CourseScheduleApp.onPickWallpaperImage`），
 * 但系统选图器只有 ArkTS 能起，所以实现放在 `ohosMain`。
 * 没有选图能力时返回 null（桌面端同口径：外观页不显示选壁纸入口）。
 */
expect val PlatformWallpaperPicker: (((ImageBitmap?) -> Unit) -> Unit)?

/**
 * 装配平台存储（键值 + 课程行表）。
 *
 * 必须在任何 `AppStorage.store(...)` 之前调用。鸿蒙端用自带的 sqlite3
 * （`third_party/sqlite`）落盘，与 Android / iOS 同一个 `nexio.db` schema。
 */
expect fun installPlatformStorage()
