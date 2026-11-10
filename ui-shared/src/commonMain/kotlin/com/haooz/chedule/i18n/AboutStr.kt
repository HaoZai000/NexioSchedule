package com.haooz.chedule.i18n

/** 关于页 / 应用更新 / 交流反馈 / 赞赏作者 / 小组件引导相关文案 */
object AboutStr {

    // ---- 通用（关于页头部与全局提示） ----

    /** 关于页标题（顶栏 / 桌面端原生标题）。 */
    val aboutApp = L("关于应用", "About")

    /** 应用名（桌面/iOS 未注入时的默认值）。 */
    val appName = L("Nexio课程表", "Nexio Schedule")

    /** 版本号取不到时的默认值。 */
    val unknownVersion = L("未知版本", "Unknown version")

    /** 页脚版权行前缀。 */
    val copyright = L("© 2026 Nexio课程表 · ", "© 2026 Nexio Schedule · ")

    /** 工信部备案号（官方标识，中英文一致展示）。 */
    val icpNumber = L("闽ICP备2026037367号-2A", "闽ICP备2026037367号-2A")

    /** 日志分享按钮。 */
    val share = L("分享", "Share")

    val opFailedRetry = L("操作失败，请重试", "Operation failed, please try again")

    // ---- 关于页 ----

    val communicationFeedback = L("问题反馈", "Report a Problem")
    val projectRepo = L("项目仓库", "Project Repository")
    val donate = L("捐赠支持", "Donate")

    /** 「捐赠支持」右侧摘要。 */
    val buyCoffee = L("请作者喝杯咖啡", "Buy the author a coffee")
    val changelog = L("更新日志", "Changelog")
    val specialThanks = L("特别致谢", "Special Thanks")

    /** 页脚开源协议入口。 */
    val license = L("开源协议", "Open Source License")
    val privacyPolicy = L("隐私政策", "Privacy Policy")
    val revokeConsent = L("撤回同意", "Revoke Consent")
    val revokeDialogTitle = L("撤回隐私政策同意", "Revoke Privacy Consent")
    val revokeDialogSummary = L(
        "撤回后将关闭应用，且需要重新同意隐私政策才能继续使用。确定撤回吗？",
        "Revoking will close the app, and you will need to accept the Privacy Policy again to keep using it. Revoke?"
    )
    val revokeAndExit = L("撤回并退出", "Revoke & Exit")

    // ---- 功能日志录制 ----

    val diagRecording = L("功能日志录制", "Diagnostic Log Recording")
    val recordingRemaining = L(
        "录制中… 剩余 %s \n正在记入日志，崩溃/被杀自动结束",
        "Recording… %s left \nLogging in progress, ends automatically on crash or kill"
    )
    val diagCrashSaved = L(
        "检测到崩溃，日志已自动保存，可直接分享",
        "Crash detected. The log has been saved automatically and is ready to share"
    )
    val diagKilled = L(
        "上次进程被杀，已自动结束录制，可分享",
        "The process was killed last time. Recording ended automatically and is ready to share"
    )
    val diagTimeout = L(
        "录制已达 30 分钟上限，可分享",
        "Recording reached the 30-minute limit and is ready to share"
    )
    val diagEnded = L("录制已结束，可分享给开发者", "Recording finished. You can share it with the developer")
    val diagIdleHint = L(
        "点击开始录制后请复现问题，结束后点分享。\n日志仅存本机，需你主动分享才会离开设备",
        "Tap Start Recording, reproduce the issue, then tap Share when done.\nThe log stays on this device and only leaves it when you share it"
    )
    val diagStart = L("开始录制", "Start Recording")
    val diagStop = L("结束录制", "Stop Recording")
    val diagAlreadyRecording = L("录制已在进行", "Recording is already in progress")
    val diagStarted = L("已开始录制，最长 30 分钟", "Recording started, up to 30 minutes")
    val diagEndedShort = L("录制已结束，可分享", "Recording finished, ready to share")
    val shareOpened = L("已打开分享，请把日志发给开发者", "Share sheet opened. Please send the log to the developer")
    val shareFailedRetry = L("分享失败，请重试", "Share failed, please try again")

    // ---- 应用更新（设置页 + 更新弹窗共用） ----

    val currentVersion = L("当前版本", "Current Version")
    val latestVersionN = L("最新版本：%s", "Latest version: %s")
    val checkForUpdates = L("检查更新", "Check for Updates")

    /** 「检查更新」右侧「有新版本」角标。 */
    val newVersionAvailable = L("有新版本", "New Version Available")

    /** 更新弹窗标题。 */
    val newVersionFound = L("发现新版本", "Update Available")
    val checkUpdateFailedNetwork = L(
        "检查更新失败，请检查网络连接",
        "Failed to check for updates. Check your network connection"
    )

    /** 更新检查异常时的日志文案。 */
    val checkUpdateFailed = L("检查更新失败", "Failed to check for updates")
    val upToDate = L("当前已是最新版本", "Up to date")
    val moreSettings = L("更多设置", "More Settings")
    val stableChannel = L("稳定版", "Stable")
    val updateChannel = L("更新通道", "Update Channel")
    val downloadSource = L("下载源", "Download Source")
    val betaLockedGitee = L("Beta 通道已锁定 Gitee", "Gitee is locked for the Beta channel")
    val downloadSourceSummary = L("选择应用更新的下载仓库", "Choose the repository for app updates")
    val autoCheckUpdates = L("自动检查更新", "Auto-Check for Updates")
    val autoCheckUpdatesSummary = L("启动时自动检查是否有新版本", "Check for new versions on launch")
    val updateReminder = L("更新提醒", "Update Reminder")
    val updateReminderSummary = L("发现新版本时弹出提醒", "Show a reminder when a new version is found")
    val installing = L("安装中", "Installing")
    val downloadComplete = L("下载完成", "Download Complete")

    /** 弹窗标题「正在获取下载地址」。 */
    val gettingDownloadUrl = L("正在获取下载地址", "Getting Download Link")

    /** 弹窗正文「正在获取下载地址...」。 */
    val gettingDownloadUrlDots = L("正在获取下载地址…", "Getting download link…")

    /** 弹窗标题「正在获取链接」。 */
    val gettingLink = L("正在获取链接", "Getting Link")
    val getLink = L("获取链接", "Get Link")
    val install = L("安装", "Install")
    val startDownload = L("开始下载", "Start Download")
    val backgroundDownload = L("后台下载", "Download in Background")
    val later = L("稍后", "Later")
    val installingSummary = L("正在安装应用，请稍候…", "Installing the app, please wait…")
    val versionInfoNotFound = L("未找到版本信息", "Version info not found")
    val getDownloadUrlFailedRetry = L(
        "获取下载地址失败，请稍后重试",
        "Failed to get the download link. Try again later"
    )
    val getDownloadUrlFailedSettings = L(
        "获取下载地址失败，请到「设置 - 更新」中重试",
        "Failed to get the download link. Try again in Settings - Update Settings"
    )
    val downloadFailedN = L("下载失败：%s", "Download failed: %s")

    // ---- 问题反馈 ----

    val contactUs = L("联系我们", "Contact Us")
    val joinGroup = L("加入交流群", "Join the Group Chat")
    val joinGroupSummary = L("加入QQ群与其他用户交流", "Chat with other users in the QQ group")
    val joinChannel = L("加入频道", "Join the Channel")
    val joinChannelSummary = L("加入QQ频道与其他用户交流", "Chat with other users in the QQ channel")
    val feedbackSuggestions = L("反馈与建议", "Feedback & Suggestions")
    val feedbackOnGitHub = L("前往GitHub反馈", "Feedback on GitHub")
    val githubIssuesSummary = L("在GitHub Issues提交反馈和建议", "Submit feedback and suggestions on GitHub Issues")
    val feedbackForm = L("应用内反馈", "In-App Feedback")
    val feedbackFormSummary = L("在应用内提交 Bug 报告或功能建议", "Report a bug or suggest a feature in the app")
    val featureProgress = L("开发进度", "Development Progress")
    val featureProgressTitle = L("查看开发进度", "View Progress")
    val featureProgressSummary = L("新功能开发与问题修复的进展", "What's being built and fixed")

    // ---- 赞赏作者 ----

    val totalSponsorship = L("累计赞助", "Total Donations")
    val anonymous = L("匿名", "Anonymous")
    val donateQr = L("赞赏码", "Donation QR Code")
    val donationDetails = L("捐赠明细", "Donation Details")
    val donationManual = L("正在手工填写中", "Manually curated")

    // ---- 小组件使用引导 ----

    val featureTodayTitle = L("今日课程一目了然", "Today's Courses at a Glance")
    val featureTodayDesc = L(
        "桌面显示当天课程、时间、地点和教师信息",
        "The home screen widget shows today's courses, times, locations and teachers"
    )
    val featureTomorrowTitle = L("明日课程提前预览", "Preview Tomorrow's Courses")
    val featureTomorrowDesc = L(
        "开启次日课程提醒后，智能显示明日课程",
        "Smartly shows tomorrow's courses once the next-day course reminder is enabled"
    )
    val featureOngoingTitle = L("进行中课程高亮", "Ongoing Course Highlight")
    val featureOngoingDescIos = L("突出显示正在上课的课程和结束时间", "Highlights the current course and its end time")
    val featureOngoingDescAndroid = L(
        "正在上课的课程高亮显示剩余时间",
        "Highlights the current course with the time remaining"
    )
    val featureStatusTitle = L("智能显示状态", "Smart Status Display")
    val featureStatusDesc = L("智能提醒今日课程当前状态", "Smartly indicates the current status of today's courses")
    val featureAppearanceTitle = L("跟随桌面外观", "Follow Home Screen Appearance")
    val featureAppearanceDesc = L(
        "支持浅色、深色与系统着色外观",
        "Supports light, dark and system-tinted appearances"
    )

    // 小组件预览里的示例数据（静态 mock，随语言切换展示对应文案）
    val previewHeaderToday = L("今天 / 周五", "Today / Fri")
    val previewHeaderTomorrow = L("明天 / 周六", "Tomorrow / Sat")
    val previewCourseMath = L("高等数学", "Advanced Mathematics")
    val previewCourseEnglish = L("大学英语", "College English")
    val previewCourseLinearAlgebra = L("线性代数", "Linear Algebra")
    val previewCoursePhysics = L("大学物理", "College Physics")
    val previewInfoMath = L("第1-2节｜A301｜张老师", "Periods 1-2 | A301 | Mr. Zhang")
    val previewInfoEnglish = L("第5-6节｜B205｜李老师", "Periods 5-6 | B205 | Mr. Li")
    val previewEndingSoon = L("25分钟结束", "Ends in 25 min")
    val previewRemainingClasses = L("还剩 2 节课", "2 classes left")
    val previewInfoMathTeacher = L("A301｜张老师", "A301 | Mr. Zhang")
    val previewInfoEnglishTeacher = L("B205｜李老师", "B205 | Mr. Li")
    val previewInfoLinearTeacher = L("C102｜王老师", "C102 | Mr. Wang")
    val previewInfoPhysicsTeacher = L("D203｜赵老师", "D203 | Mr. Zhao")
    val paddingStandard = L("标准", "Standard")
    val padding4x6NoText = L("4×6 无字", "4×6 No Text")
    val padding4x7NoText = L("4×7 无字", "4×7 No Text")
    val widgetLayoutTitle = L("小组件适应布局", "Widget Layout")
}
