package com.haooz.chedule.i18n

/** 课程提醒设置页文案 */
object ReminderStr {

    // 总开关
    val enableReminders = L("开启提醒", "Enable Reminders")
    val notificationPermissionRequired =
        L("需要通知权限才能使用课程提醒功能", "Notification permission is required for course reminders")

    // 提醒详情
    val preClassReminder = L("下节课提醒", "Pre-class Reminder")
    val preClassReminderSummary =
        L("提供下节上课时间、地点等信息，和上课勿扰等操作建议", "Shows next class time, location, and suggestions like in-class Do Not Disturb")
    val reminderLeadTime = L("提前提醒时间", "Reminder Lead Time")
    val minutesN = L("%d分钟", "%d min")
    val reminderSections = L("提醒节次", "Reminder periods")
    val allSections = L("全部节次", "All periods")
    val sectionRangeN = L("第%d-%d节", "Period %d-%d")
    val sectionRangeFromN = L("第%d节起", "From period %d")
    val sectionRangeUntilN = L("第1-%d节", "Period 1-%d")
    val sectionStart = L("起始节", "From")
    val sectionEnd = L("结束节", "To")
    val sectionN = L("第%d节", "P%d")
    val sectionRangeHint =
        L("只提醒落在该节次范围内的课程", "Only classes starting in this period range trigger reminders")
    val nextDayReminder = L("次日课程提醒", "Next-Day Class Reminder")
    val nextDayReminderSummary =
        L("提供明天课程、首节时间、地点等信息，和定闹钟等操作建议", "Shows tomorrow's classes, first period time and location, with suggestions like setting an alarm")
    val reminderTime = L("提醒时间", "Reminder Time")
    val inClassReminder = L("课中提醒", "In-Class Reminder")
    val inClassReminderSummary =
        L("上课中展示距离下课时间与课程状态", "Shows time until class ends and class status during class")
    val reminderTiming = L("提醒时机", "Reminder Timing")
    val inClassTimingFull = L("全程", "Entire class")
    val inClassTimingBeforeEnd = L("距下课", "Before end")
    val beforeEndN = L("距下课 %d 分钟", "%d min before end")
    val inClassFullDesc = L("全程显示课中进度", "Show in-class progress throughout the class")
    val inClassBeforeEndDesc = L("距下课 %d 分钟开始显示", "Starts %d min before class ends")

    // 上课勿扰
    val autoDnd = L("自动开启勿扰", "Auto Do Not Disturb")
    val autoDndNeedsPermission =
        L("需要先授予勿扰权限才能自动开启勿扰", "Grant Do Not Disturb access first to turn this on")
    val autoDndSummary =
        L("上课时自动开启勿扰，下课后自动恢复", "Turns on Do Not Disturb during class and restores it after class")
    val dndModeNeedsPermission =
        L("该档位需要勿扰权限，已为你打开授权页", "This level needs Do Not Disturb access; the permission page has been opened")
    val dndModeTitle = L("勿扰模式档位", "Do Not Disturb Level")
    val dndModeTotalSilence = L("完全勿扰 (DND)", "Total Silence (DND)")
    val dndModeTotalSilenceSummary = L("屏蔽全部通知与铃声", "Blocks all notifications and sounds")
    val dndModeSilent = L("静音模式 (SILENT)", "Silent Mode (SILENT)")
    val dndModeSilentSummary = L("关闭铃声与振动", "Mutes ringtones and vibration")
    val dndModePriority = L("勿扰模式 (PRIORITY)", "Priority Mode (PRIORITY)")
    val dndModePrioritySummary = L("只放行优先通知", "Allows priority notifications only")

    // 超级岛 / 灵动岛 / 实时活动
    val xiaomiSuperIsland = L("小米超级岛", "Xiaomi Super Island")
    val dynamicIsland = L("灵动岛", "Dynamic Island")
    val superIslandOnSummary =
        L("已开启，课程提醒将以超级岛样式显示", "On. Course reminders will appear as the Super Island")
    val liveActivityOnSummary =
        L("已开启，点击上课提醒将启动实时活动", "On. Tapping a class reminder starts a Live Activity")
    val islandOffHyperSummary = L("关闭后使用实时动态通知", "Off. Falls back to a live notification")
    val islandOffIosSummary = L("关闭后点击提醒仅打开应用", "Off. Tapping a reminder only opens the app")
    val liveRightSide = L("实时动态右侧", "Live Notification Right Side")
    val superIslandLeft = L("超级岛左侧", "Super Island Left")
    val dynamicIslandLeft = L("灵动岛左侧", "Dynamic Island Left")
    val superIslandRight = L("超级岛右侧", "Super Island Right")
    val dynamicIslandRight = L("灵动岛右侧", "Dynamic Island Right")
    val inClassIslandRight = L("课中岛右侧", "In-Class Island Right")
    val inClassDynamicIslandRight = L("课中灵动岛右侧", "In-Class Dynamic Island Right")
    val islandAod = L("息屏显示", "Always-On Display")
    val islandAodSummary = L("全天候显示时无效", "No effect with the system Always-On Display")

    // 岛上显示内容选项
    val courseName = L("课程名称", "Course Name")
    val classLocation = L("上课地点", "Class Location")
    val countdown = L("倒计时", "Countdown")
    val inClassNow = L("正在上课", "In class")

    // Shizuku
    val shizukuStatus = L("Shizuku 状态", "Shizuku Status")
    val notRunning = L("未运行", "Not running")
    val notAuthorized = L("未授权", "Not granted")
    val ready = L("已就绪", "Ready")
    val installShizuku = L("请安装并启动 Shizuku 应用", "Install and start the Shizuku app")
    val authorizeShizuku = L("授权 Shizuku", "Authorize Shizuku")
    val shizukuAuthFailed = L("Shizuku 授权失败", "Shizuku authorization failed")

    // 缩略态精简
    val thumbAbbreviation = L("缩略态精简", "Thumbnail Abbreviation")
    val scopeOriginal = L("原始文本", "Original text")
    val scopeClassroom = L("精简地点", "Abbreviate classroom")
    val scopeCourse = L("精简课程", "Abbreviate course")
    val scopeBoth = L("精简地点与课程", "Abbreviate classroom and course")
    val abbreviationMethod = L("精简方式", "Method")
    val methodRegex = L("正则精简", "Regex")
    val methodRegexSummary = L("按预设规则对地点进行精简", "Abbreviate classrooms with preset rules")
    val methodAi = L("AI 精简", "AI")
    val methodAiSummary = L("一键按课表生成精简结果", "Generate abbreviations from the schedule in one tap")
    val viewAbbreviationPreview = L("查看精简预览", "Preview Abbreviations")
    val viewAbbreviationPreviewSummary =
        L("预览精简后的课程地点", "Preview the abbreviated courses and classrooms")
    val unknownError = L("未知错误", "Unknown error")
    val aiShrinkFailedS = L("AI 精简失败：%s", "AI abbreviation failed: %s")
    val alreadyShort =
        L("这些地点已经很短了，没有可精简的", "These classroom names are already short, nothing to abbreviate")
    val aiShrinkDoneRemaining = L("已精简 %d 条，今天还能用 %d 次", "Abbreviated %d entries, %d uses left today")
    val aiShrinkDone = L("已精简 %d 条", "Abbreviated %d entries")
    val oneTapAbbreviate = L("一键精简", "One-Tap Abbreviation")

    // 精简预览弹窗
    val abbreviationPreview = L("精简预览", "Abbreviation Preview")
    val previewEditHint = L("点击右侧文字可手动修改", "Tap the text on the right to edit it")
    val currentMethod = L("当前方式", "Current method")
    val scheduleEmpty = L("课表没有内容", "Schedule is empty")
    val willBeRewritten = L("个会被改写", "will be rewritten")
    val scheduleNoCourseOrClassroom =
        L("课表里还没有课程名和地点", "No course names or classrooms in the schedule yet")
    val restoreAutoN = L("恢复自动（%d）", "Restore Auto (%d)")
    val allRestoredAuto = L("已全部恢复自动精简", "All restored to automatic abbreviation")

    // 权限设置
    val notificationPermission = L("通知权限", "Notification Permission")
    val notificationPermissionSummary =
        L("用于接收课程提醒通知", "For receiving course reminder notifications")
    val authorized = L("已授权", "Granted")
    val liveNotificationPermission = L("实况通知权限", "Live Notification Permission")
    val liveNotificationPermissionSummary =
        L("在状态栏和锁屏实时显示课程倒计时", "Shows a live class countdown in the status bar and on the lock screen")
    val exactAlarmPermission = L("精确闹钟权限", "Exact Alarm Permission")
    val exactAlarmPermissionSummary =
        L("确保提醒准时触发，不受省电策略影响", "Ensures reminders fire on time, unaffected by battery saver policies")
    val batteryOptimization = L("电池优化", "Battery Optimization")
    val batteryOptimizationSummary =
        L("关闭电池优化以确保提醒准时送达", "Turn off battery optimization so reminders arrive on time")
    val optimizationDisabled = L("已关闭", "Disabled")
    val optimizationNotDisabled = L("未关闭", "Not disabled")
    val dndPermission = L("勿扰权限", "Do Not Disturb Permission")
    val dndPermissionSummary =
        L("用于上课时开启勿扰或静音", "For enabling Do Not Disturb or silent mode during class")
    val dndPermissionManual =
        L("请手动在系统设置中授予勿扰权限", "Please grant Do Not Disturb access manually in system settings")
    val autostartPermission = L("自启动权限", "Auto-Start Permission")
    val autostartPermissionSummary =
        L("在应用被系统清理后及时重启", "Lets the app restart promptly after being cleared by the system")
    val goToCheck = L("前往检查", "Check")
}
