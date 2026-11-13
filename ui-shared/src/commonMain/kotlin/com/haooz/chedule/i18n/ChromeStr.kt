package com.haooz.chedule.i18n

/**
 * 课表主界面外壳（顶栏/底栏/侧栏/主界面弹窗/浮层）与路由壳页面的文案。
 * 与 [CommonStr] 语义一致的条目（确定/取消/删除等）直接引用 [CommonStr]，不在此重复。
 */
object ChromeStr {

    // ── 主界面确认/选择类弹窗 ──

    val deleteCourse = L("删除课程", "Delete Course")
    val deleteThisWeekCourse = L("删除本周课程", "Delete This Week's Course")
    val deleteAllSummary = L(
        "确定要删除「%s」的全部课程吗？\n此操作不可撤销。",
        "This will delete all sessions of “%s”.\nThis action cannot be undone."
    )
    val deleteWeekSummary = L(
        "确定要删除「%s」在第%d周的课程吗？\n此操作不可撤销。",
        "This will delete “%s” in Week %d.\nThis action cannot be undone."
    )
    val deleteAllWeeksLabel = L("删除全部周", "All Weeks")
    val deleteAllWeeksDesc = L("删除该课程的所有周次", "Delete the course from every week")
    val deleteOnlyThisWeekDesc = L("关闭则仅删除第%d周", "Turn off to delete only Week %d")

    val pasteCourse = L("粘贴课程", "Paste Course")
    val pasteRangeSummary = L("选择「%s」的粘贴范围", "Choose the paste range for “%s”")
    val allWeeks = L("全部周", "All Weeks")
    val currentWeekOption = L("当前周", "Current Week")

    val slotOccupiedTitle = L("该位置已有课程", "Slot Already Occupied")
    val chooseResolutionSummary = L("请选择课程处理方式", "Choose how to handle the conflict")
    val overwrite = L("覆盖", "Overwrite")
    val swap = L("交换", "Swap")

    val confirmOperationSummary = L("确定要执行此操作吗？", "Are you sure you want to proceed?")
    val overwriteCrossWeekSummary = L(
        "将删除「%s」第%d周的课程，\n并把「%s」从第%d周调到第%d周的此位置。",
        "This will delete “%s” in Week %d\nand move “%s” from Week %d into this slot in Week %d."
    )
    val overwriteThisWeekSummary = L(
        "将删除「%s」本周的课程，\n并把「%s」调到此位置。",
        "This will delete “%s” this week\nand move “%s” into this slot."
    )
    val swapCrossWeekSummary = L(
        "将把「%s」第%d周与「%s」第%d周的两节课互换位置。",
        "This will swap “%s” in Week %d with “%s” in Week %d."
    )
    val swapThisWeekSummary = L(
        "将把「%s」与「%s」本周的两节课互换位置。",
        "This will swap “%s” and “%s” this week."
    )
    val confirmOverwrite = L("确认覆盖", "Confirm Overwrite")
    val confirmSwap = L("确认交换", "Confirm Swap")

    // ── 顶栏与「更多」菜单 ──

    val jumpToWeek = L("跳转周数", "Jump to Week")
    val jumpToDate = L("跳转日期", "Jump to Date")
    val courseManage = L("课程管理", "Course Management")
    val scheduleAppearance = L("课表外观", "Appearance")
    val todayIsDow = L("今天是%s", "Today is %s")

    // ── 主内容层 ──

    /** 「回到今天/本周」浮动按钮上的单字徽标 */
    val todayBadge = L("今", "Now")

    val pasteNoSpace = L("空间不足，无法粘贴", "Not enough space to paste")
    val pasteTargetOccupied = L("目标位置有课，无法粘贴", "Target slot is occupied, cannot paste")

    // ── 浮层与切换课表 loading ──

    val paste = L("粘贴", "Paste")
    val switching = L("切换中", "Switching")

    // ── 底栏/侧栏导航 ──

    val schedule = L("课程表", "Schedule")
    val shiftSchedule = L("排班课表", "Shift Schedule")
    val switchSchedule = L("切换课表", "Switch Schedule")
    val navigationLabel = L("导航", "Navigation")
    /** 平板侧栏「数据管理」分组。 */
    val dataManagement = L("数据管理", "Data Management")

    // ── 路由壳页面 ──

    /** 导入 / 导出合并页与设置页入口文案。 */
    val importExport = L("导入导出", "Import & Export")

    val unknownVersion = L("未知版本", "Unknown version")
    val appName = L("Nexio课程表", "Nexio Schedule")

    val changelog = L("更新日志", "Changelog")
    val openSourceLicenses = L("开源协议", "Open Source License")
    val privacyPolicy = L("隐私政策", "Privacy Policy")
    val communityFeedback = L("问题反馈", "Report a Problem")
    val donate = L("捐赠支持", "Donate")

    val appPreferences = L("应用个性化", "Personalization")
    val updateSettingsTitle = L("更新", "Update")

    /** 更新设置页（更新通道 / 下载源）。 */
    val updatePreferencesTitle = L("更新设置", "Update Settings")
    val aiImport = L("AI 文本导入", "AI Text Import")

    val updating = L("更新中", "Updating")
    val holidaysTitle = L("节假日与调休", "Holidays & Adjusted Workdays")

    val widgetsTitle = L("桌面小部件", "Widgets")
    val addToHomeScreen = L("添加到桌面", "Add to Home Screen")
    val addWidgetTitle = L("添加桌面小部件", "Add Home Screen Widget")
    val widgetGuideIos = L(
        "1. 长按主屏幕空白处\n2. 点击「编辑」→「添加小组件」\n3. 搜索「Nexio课程表」\n4. 添加「课程表」，调整大小切换课程视图",
        "1. Touch and hold an empty area of the Home Screen\n2. Tap “Edit” → “Add Widget”\n3. Search for “Nexio Schedule”\n4. Add “Schedule”, resize it to switch course views"
    )
    val widgetGuideAndroid = L(
        "1. 长按桌面空白处\n2. 选择「全部应用」内的「安卓小部件」\n3. 找到「Nexio课程表」并添加",
        "1. Touch and hold an empty area of the Home Screen\n2. Choose “Widgets” under “All apps”\n3. Find “Nexio Schedule” and add it"
    )

    val testNotification = L("测试通知", "Test Notification")
    val courseReminders = L("课程提醒", "Course Reminders")

    val connected = L("已连接", "Connected")
    val testConnection = L("测试连接", "Test Connection")
    val webdavBackup = L("WebDAV 云同步", "WebDAV Cloud Sync")

    /** %s = 能力名（口令导入 / 第三方来源显示名） */
    val platformUnsupported = L("当前平台不支持%s", "%s is not supported on this platform")
    val shareCodeImport = L("口令导入", "Share Code Import")

    val scheduleBackup = L("备份与同步", "Backup & Sync")
    /** 导入导出页胶囊短标签。 */
    val importTab = L("导入", "Import")
    val exportTab = L("导出", "Export")

    val classTimeSettings = L("上课时间", "Class Time")
    val saveFailedRetry = L("保存失败，请重试", "Save failed, please try again")

    // ── 路由器 ──

    val routerNotProvided = L(
        "LocalAppRouter 未提供：请在 MainActivity.setContent 最外层 CompositionLocalProvider",
        "LocalAppRouter is not provided: provide it via CompositionLocalProvider at the outermost level of MainActivity.setContent"
    )
}
