package com.haooz.chedule.i18n

/** 设置页（手机页 + 平板双栏页）文案 */
object SettingsStr {

    // ---- 分类主页（9 个分类：5 个容器型 + 4 个直达型）----
    val catCourse = L("课表", "Schedule")
    val catCourseSummary = L("学期、周次与课表显示", "Term, weeks and display")
    val catReminder = L("提醒", "Reminders")
    val catReminderSummary = L("课程提醒与节假日", "Class reminders and holidays")
    val catWidget = L("小组件", "Widgets")
    val catWidgetSummary = L("桌面小部件与样式", "Home screen widgets and styles")
    val catPersonalization = L("个性化", "Personalization")
    val catPersonalizationSummary = L("主题、材质与交互", "Theme, materials and interactions")
    val catData = L("数据管理", "Data")
    val catDataSummary = L("导入、导出与数据源", "Import, export and data sources")
    val catSync = L("备份与同步", "Backup & Sync")
    val catSyncSummary = L("课表备份、本地备份与 WebDAV", "Schedule backup, local backup and WebDAV")
    val catUpdate = L("更新", "Updates")
    val catUpdateSummary = L("版本与更新通道", "Version and update channel")
    val catAbout = L("关于", "About")
    val catAboutSummary = L("关于本应用与支持作者", "About this app and supporting the author")
    val catDonate = L("捐赠", "Donate")
    val catDonateSummary = L("支持开发者", "Support the developer")

    // ---- 二级页分组标题 ----
    val secSemester = L("学期", "Semester")
    val secCourseDisplay = L("课表显示", "Schedule Display")
    val secCourseExtra = L("其它", "More")
    val appLanguage = L("应用语言", "App Language")
    val secLanguageHome = L("显示", "Display")
    val secInteraction = L("交互", "Interaction")
    val secDataSources = L("数据源", "Data Sources")
    val secSync = L("同步", "Sync")

    // ---- 分区标题 ----
    val selectCompareSchedules = L("选择对比课表", "Schedules to Compare")

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
    val courseTimeManagement = L("课程时间管理", "Class Time Settings")
    val courseTimeSummary = L("管理课表的节数与课程时间", "Manage periods and course times in your schedule")

    val holidayAndWorkswap = L("节假日与调休", "Holidays & Adjusted Workdays")
    val shiftMode = L("排班模式", "Shift Mode")
    val shiftModeSummary = L("同时对比多个课表的排班情况", "Compare shifts across multiple schedules at once")
    val enterShiftMode = L("进入排班模式", "Enter Shift Mode")
    val enterShiftModeSummary = L(
        "将切换到排班课表模式，可同时对比多个课表的排班情况。确定进入？",
        "Switch to shift schedule mode to compare shifts across multiple schedules at once. Continue?",
    )
    val exitShiftMode = L("退出排班模式", "Exit Shift Mode")

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

    val destLocalBackup = L("本地备份", "Local Backup")
    val destWebDav = L("WebDAV 云同步", "WebDAV Cloud Sync")


    // ---- 通知测试 ----
    val testXiaomiSuperIsland = L("测试小米超级岛", "Test Xiaomi Super Island")
    val testLiveActivity = L("测试实时活动", "Test Live Activity")
    // iOS 的「超级岛」= Live Activity，文案沿用原生课程提醒页的说法
    val testDynamicIsland = L("测试灵动岛", "Test Dynamic Island")
    val islandTestStarted = L("已启动灵动岛测试", "Dynamic Island test started")
    val testNotificationSent = L("已发送测试通知", "Test notification sent")
}
