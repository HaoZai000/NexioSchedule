package com.haooz.nexio.ohos

import androidx.compose.ui.graphics.ImageBitmap
import com.haooz.chedule.data.backup.BackupFileController
import com.haooz.chedule.data.backup.ScheduleFileController

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

/**
 * 系统文件选择器（JSON / ICS 导入与导出）。
 *
 * 实现放在 `ohosMain`（走 ArkTS 的 `DocumentViewPicker`）；共享路由表只认这个工厂，
 * 不依赖具体实现类型。
 */
expect fun createScheduleFileController(): ScheduleFileController

/**
 * 本地备份目录（备份中心列表、导出、WebDAV 覆盖恢复共用）。
 *
 * 鸿蒙实现落在应用私有目录 `filesDir/Neixo_Schedule`，不依赖 SAF 一类的授权。
 */
expect fun createBackupFileController(): BackupFileController

/** 关于页展示的应用版本号（空串 = 尚未取到）。 */
expect fun ohosAppVersion(): String

/**
 * 课程提醒控制器（代理提醒 + ArkTS 桥）。
 *
 * 实现在 `ohosSharedCore`（只有 `ohosMain` 看得到），路由表在 `commonMain`，
 * 所以通过 expect/actual 暴露工厂，避免把整个平台实现挪进公共源集。
 */
expect fun createReminderController(): com.haooz.chedule.data.platform.ReminderController

/** 关于页展示的应用名称（空串 = 尚未取到）。 */
expect fun ohosAppName(): String

/** 退出应用（关于页「撤回并退出」等需要真正关掉进程的场景）。 */
expect fun ohosExitApp()

/**
 * 「口令分享导出」的入口：生成口令、复制到剪贴板、出图并拉起系统分享。
 * 与 iOS 的 `rememberIosShareAction` 同签名，页面只拿这个 lambda。
 */
@androidx.compose.runtime.Composable
expect fun rememberOhosShareAction(): (String, (Boolean) -> Unit) -> Unit

/**
 * 「同步到日历」的动作：系统日历只有 ArkTS 的 `calendarManager` 能写。
 * 实现放在 `ohosMain`（见 `CalendarSyncBridge.kt`），页面只拿这个 lambda。
 */
@androidx.compose.runtime.Composable
expect fun rememberOhosCalendarSyncAction(): suspend (String) -> com.haooz.chedule.ui.activities.CalendarSyncResult

/**
 * 网页导入宿主（ArkUI `Web` 组件）工厂。
 *
 * 网页本体是 ArkUI 节点，画在覆盖层 Compose 之上；页面只通过 [WebHost] 契约驱动它。
 */
@androidx.compose.runtime.Composable
expect fun rememberOhosWebHost(
    config: com.haooz.chedule.ui.web.WebHostConfig,
    events: com.haooz.chedule.ui.web.WebHostEvents,
): com.haooz.chedule.ui.web.WebHost

/** 网页页「返回 / 关闭」的真正出栈动作，由路由注册（顶栏按钮在 ArkUI 层）。 */
expect var ohosWebRequestClose: (() -> Unit)?

/** 出栈前先让 ArkUI 播放退场，避免「按钮先走、页面后走」。 */
expect fun ohosWebPrepareExit()
