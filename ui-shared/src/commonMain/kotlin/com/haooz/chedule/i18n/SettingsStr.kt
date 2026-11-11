package com.haooz.chedule.i18n

/** 设置页（手机页 + 平板双栏页）文案 */
object SettingsStr {

    // ---- 分区标题 ----
    val basicSettings = L("基本设置", "General")
    val featuresSection = L("特色功能", "Features")

    // ---- 基本设置总览卡片 ----
    val scheduleSettings = L("课表管理", "Schedule Management")
    /** 折叠态摘要：开始上课日期 · 当前周 / 总周数 */
    val scheduleSettingsSummary = L("%s · 第%d周 / 共%d周", "%s · Week %d / %d weeks")
    /** 未开学 / 放假中：当前周换成状态文案 */
    val scheduleSettingsIdleSummary = L("%s · %s / 共%d周", "%s · %s / %d weeks")
    val selectCompareSchedules = L("选择对比课表", "Schedules to Compare")
    /** 导入 / 导出合并页入口。 */
    val importExport: L10n get() = ChromeStr.importExport
    val otherActions = L("其他操作", "Other Actions")

    // ---- 学期与周次 ----
    val startDate = L("开始上课日期", "Semester Start Date")
    val currentWeek = L("当前周数", "Current Week")
    val notStarted = L("未开始", "Not started")
    val onBreak = L("放假中", "On break")
    val totalWeeksTitle = L("本学期总周数", "Total Weeks")
    val totalWeeksValue = L("共%d周", "%d weeks in total")
    val smartWeekend = L("智能显示周末", "Smart Weekend Display")
    val smartWeekendSummary = L("开启后隐藏无课的周六日", "Hide weekends with no classes when enabled")
    val showNonCurrentWeek = L("显示非本周课程", "Show Courses from Other Weeks")
    val monthN = L("%d月", "%d")
    val selectCurrentWeek = L("选择当前周次", "Select Current Week")
    val selectTotalWeeks = L("选择学期总周数", "Select Total Weeks")

    // ---- 课程时间 ----
    val courseTimeManagement = L("上课时间", "Class Time")
    val classTime = L("上课时间", "Class Time")
    val courseTimeSummary = L("管理课表的节数与课程时间", "Manage periods and course times in your schedule")

    // ---- 特色功能 ----
    val courseReminder = L("课程提醒", "Course Reminders")
    val courseReminderSummary = L("课前提醒、次日课程提醒", "Before-class and next-day reminders")
    val holidayAndWorkswap = L("节假日与调休", "Holidays & Adjusted Workdays")
    val homeScreenWidget = L("桌面小部件", "Widgets")
    val shiftMode = L("排班模式", "Shift Mode")
    val shiftModeSummary = L("同时对比多个课表的排班情况", "Compare shifts across multiple schedules at once")
    val enterShiftMode = L("进入排班模式", "Enter Shift Mode")
    val enterShiftModeSummary = L(
        "将切换到排班课表模式，可同时对比多个课表的排班情况。确定进入？",
        "Switch to shift schedule mode to compare shifts across multiple schedules at once. Continue?",
    )
    val exitShiftMode = L("退出排班模式", "Exit Shift Mode")

    // ---- 导入导出 / 备份 ----
    val scheduleImport = L("课表导入", "Import Schedule")
    val scheduleExport = L("课表导出", "Export Schedule")
    val scheduleBackup = L("备份与同步", "Backup & Sync")

    // ---- 开启新学期 ----
    val newSemester = L("开启新学期", "Start New Semester")
    val newSemesterSummary = L("复用当前课表设置，创建空课程的新课表", "Create an empty schedule reusing current settings")
    val newSemesterDialogSummary = L(
        "将复用当前课表的所有设置数据，创建一个清空课程的新课表",
        "Reuses all settings from the current schedule to create a new one with no courses",
    )
    val newScheduleNameLabel = L("新课表名称", "New Schedule Name")
    val create = L("创建", "Create")
    val emptyNameToast = L("请输入课表名称", "Please enter a schedule name")
    val duplicateNameToast = L("该课表名称已存在", "This schedule name already exists")
    val createdToast = L("「%s」创建成功", "\"%s\" created successfully")

    // ---- 其他入口 ----
    val appPreferences = L("应用个性化", "Personalization")
    val updateSettings = L("更新", "Update")
    val aboutApp = L("关于应用", "About")

    /** 「关于应用」右侧提示：该页内含捐赠入口。 */
    val supportAuthor = L("支持作者", "Support the Author")

    // ---- 平板左栏目的地 ----
    val destSemester = L("学期与周次", "Semester & Weeks")
    val destFileCodeImport = L("文件口令导入", "File & Code Import")
    val destAiImport = L("AI 文本导入", "AI Text Import")
    val destAcademicImport = L("教务系统导入", "Academic System Import")
    val destLocalBackup = L("本地备份", "Local Backup")
    val destWebDav = L("WebDAV 云同步", "WebDAV Cloud Sync")
    val destAppreciate = L("捐赠支持", "Donate")
    val destCommunication = L("问题反馈", "Report a Problem")
    /** 平板左栏分组。 */
    val groupImportExport: L10n get() = ChromeStr.importExport
    val groupBackup = L("备份", "Backup")

    // ---- 平板外壳 ----

    // ---- 小部件引导 ----
    val addToHomeScreen = L("添加到桌面", "Add to Home Screen")
    val addWidgetDialogTitle = L("添加桌面小部件", "Add Home Screen Widget")
    val widgetGuideSteps = L(
        "1. 长按桌面空白处\n2. 选择「全部应用」内的「安卓小部件」\n3. 找到「Nexio课程表」并添加",
        "1. Touch and hold an empty area of the Home Screen\n2. Choose “Widgets” under “All apps”\n3. Find “Nexio Schedule” and add it",
    )

    // ---- WebDAV ----
    val backingUp = L("备份中…", "Backing up…")
    val backupToCloud = L("同步到云端", "Sync to Cloud")
    val restoring = L("恢复中…", "Restoring…")
    val restoreFromCloud = L("从云端恢复", "Restore from Cloud")
    val connected = L("已连接", "Connected")
    val testConnection = L("测试连接", "Test Connection")
    val saveFailedRetry = L("保存失败，请重试", "Save failed, please try again")

    // ---- 通知测试 ----
    val testXiaomiSuperIsland = L("测试小米超级岛", "Test Xiaomi Super Island")
    val testLiveActivity = L("测试实时活动", "Test Live Activity")

    // ---- 节假日面板 ----
    val fetchFailedNoData = L("获取失败或暂无数据", "Fetch failed or no data available")
    val localDataError = L("本地数据异常，更新未保存", "Local data error, update not saved")
    val updatedNRecords = L("已更新 %d 条记录", "Updated %d records")
}
