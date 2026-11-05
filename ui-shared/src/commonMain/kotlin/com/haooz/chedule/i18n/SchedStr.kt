package com.haooz.chedule.i18n

/** 课表主页 / 课表外观 / 网页导入文案 */
object SchedStr {

    // ── 课表顶栏 ──
    val onBreak = L("放假中", "On break")
    val semesterNotStarted = L("学期未开始", "Semester not started")
    val jumpToWeek = L("跳转周数", "Jump to Week")
    val scheduleAppearance = L("课表外观", "Appearance")
    val switchSchedule = L("课表切换", "Switch schedule")

    // ── 课卡徽标 ──
    val badgeHoliday = L("假", "H")
    val badgeAdjusted = L("调", "Adj")

    // ── 时间轴视图 ──
    val viewAsTimeline = L("时间轴视图", "Timeline view")
    val viewAsWeek = L("周视图", "Week view")
    val viewAs = L("课表视图", "Schedule view")
    val timelineFixedHeight = L("时间轴按真实时长", "Real duration")

    // ── 课表网格 / 课程详情 ──
    val specialCourses = L("特殊课程", "Special block")
    val unnamed = L("未命名", "Unnamed")
    val lunchBreak = L("午休", "Lunch break")
    val dinnerBreak = L("晚休", "Dinner break")
    val addCourse = L("添加课程", "Add Course")
    val nextDayTime = L("次日%s", "Next day %s")
    val courseDetails = L("课程详情", "Course details")
    val enterName = L("请输入名称", "Please enter a name")
    val selectAtLeastOneDay = L("请选择至少一个星期", "Please select at least one day")

    // ── 课表外观 ──
    val schedule = L("课程表", "Schedule")
    val applyChanges = L("应用", "Apply")
    val applying = L("正在应用", "Applying")
    val combinationN = L("搭配 %d", "Combination %d")
    val wallpaper = L("壁纸", "Wallpaper")
    val clearWallpaper = L("清除壁纸", "Remove wallpaper")
    val effects = L("效果", "Effects")
    val customize = L("自定义", "Customize")
    val followApp = L("跟随应用", "Follow app")
    val followSystem = L("跟随系统", "Follow system")
    val defaultTheme = L("默认主题", "Default theme")
    val wallpaperBrightness = L("壁纸亮度", "Wallpaper brightness")
    val wallpaperBlur = L("壁纸模糊", "Wallpaper blur")
    val cardTint = L("卡片着色程度", "Card tint")
    val cardOpacity = L("卡片不透明度", "Card opacity")
    val cardBlur = L("卡片模糊", "Card blur")
    val cardRefraction = L("卡片折射", "Card refraction")
    val cardEdgeThickness = L("边缘厚度", "Edge thickness")
    val cardRim = L("高光描边", "Rim highlight")
    val noMaterial = L("无材质", "No Material")
    val wallpaperRequired = L("需设置壁纸", "Wallpaper required")
    val courseCardHeight = L("课程卡片高度", "Course card height")
    val courseCardCornerRadius = L("课程卡片圆角", "Course card corner radius")
    val cardContentAlignment = L("卡片内容对齐方式", "Card content alignment")
    val cardTextColor = L("卡片文字颜色", "Card text color")
    val cardTextScale = L("卡片文字缩放比例", "Card text scale")
    val showClassroom = L("显示地点", "Show classroom")
    val showTeacher = L("显示教师", "Show teacher")
    val breakDividers = L("午休晚休分界线", "Lunch/dinner break dividers")

    // ── 网页导入（WebView） ──
    val importDone = L("导入完成", "Import completed")
    val noImportScript = L("无导入脚本", "No import script")
    val webImportSubtitle = L(
        "登入教务系统 → 按照提示进行导入",
        "Sign in to the school portal → follow the prompts to import"
    )
    val desktopMode = L("桌面版", "Desktop")
    val mobileMode = L("手机版", "Mobile")
    val toggleDesktopMode = L("切换手机版/桌面版", "Switch mobile/desktop")
    val runImport = L("执行导入", "Run import")
    val runningImportScript = L("正在执行导入脚本…", "Running import script…")
    val scriptDownloadFailed = L("脚本下载失败：%s", "Failed to download script: %s")
    val selectScheduleToImport = L("选择要导入的课表", "Select a schedule to import")
    val importTargetHint = L(
        "导入的课程将写入所选课表，当前课表不会受影响",
        "Imported courses will be written to the selected schedule; the current one is not affected"
    )
    val currentTag = L("当前", "Current")
}
