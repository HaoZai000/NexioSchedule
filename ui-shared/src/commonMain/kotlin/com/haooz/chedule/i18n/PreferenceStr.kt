package com.haooz.chedule.i18n

/** 偏好设置页面文案 */
object PreferenceStr {

    // 定位摘要（天气数据源 summary）
    val currentLocationS = L("当前定位：%s", "Current location: %s")
    val currentLocationNone = L("当前定位：未获取", "Current location: not acquired")
    val lastLocationS = L("上次定位：%s", "Last location: %s")
    val weatherNeedsLocation =
        L("未获取定位，开启定位权限后显示天气", "Location unavailable. Enable location permission to show weather")

    // 外观
    val lightMode = L("浅色模式", "Light Mode")
    val darkMode = L("深色模式", "Dark Mode")
    val autoDarkMode = L("自动切换深色模式", "Auto Dark Mode")
    val monetColor = L("莫奈取色", "Monet Colors")
    val monetColorSummary = L("跟随系统壁纸对应用进行配色", "Color the app based on the system wallpaper")
    val monetColorUnavailable = L("需要 Android 12 及以上版本", "Requires Android 12 or later")
    val appMaterialEffect = L("应用材质效果", "App Material Effects")
    val materialLockedSummary =
        L("当前系统版本不支持模糊效果，已锁定为无材质", "Blur effects are not supported on this system version; locked to None")
    val trueProgressiveBlur = L("真·渐进模糊", "True Progressive Blur")
    val trueProgressiveBlurDisabled = L("无材质档下不可用", "Unavailable with No Material")
    val superIslandGlow = L("小米超级岛光效", "Xiaomi Super Island Glow")
    val superIslandGlowSummary =
        L("在小米超级岛展开态显示流动光效", "Show a flowing glow when Xiaomi Super Island is expanded")

    // 应用设置
    val appSettings = L("应用设置", "App Settings")
    val schedule = L("课程表", "Schedule")
    val defaultHomepage = L("默认首页", "Default Homepage")
    val defaultHomepageSummary =
        L("首次启动时默认显示的页面", "The page shown by default on first launch")
    val predictiveBack = L("预测性返回动画", "Predictive Back Animation")
    val predictiveBackSummary =
        L("返回手势时页面跟随手指的动画效果", "The page follows your finger during the back gesture")
    val hapticFeedback = L("应用触感反馈", "Haptic Feedback")
    val hapticFeedbackSummary =
        L("点击、滑动、底栏切换等操作的震动反馈", "Vibration feedback for taps, swipes, and bottom bar switching")
    val hideFromRecents = L("隐藏后台", "Hide from Recents")
    val hideFromRecentsSummary =
        L("返回桌面时，隐藏应用的最近任务卡片", "Hide the app's recent tasks card when returning to the home screen")

    // 数据源
    val dataSources = L("数据源", "Data Sources")
    val forkSource = L("Fork源", "Fork Source")
    val eduRepoSource = L("教务数据仓库源", "Academic Data Repo Source")
    val eduRepoSourceSummary =
        L("更新教务系统数据源的仓库", "Repository for updating the academic system data source")
    val aggregateApi = L("聚合接口", "Aggregated API")
    val caiyunWeather = L("彩云天气", "Caiyun Weather")
    val weatherDataSource = L("天气数据源", "Weather Data Source")
    val stateCouncil = L("国务院", "State Council")
    val holidayDataSource = L("节假日数据源", "Holiday Data Source")
    val holidayDataSourceSummary =
        L("假期与调休安排的获取来源", "Source for holiday and adjusted workday arrangements")
}
