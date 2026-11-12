package com.haooz.chedule.data.platform

/**
 * 课程提醒控制器 —— 平台能力抽象（注入模式，同 [DiagnosticRecorder]）。真实实现包装 AlarmManager、
 * 权限与系统设置页跳转等 Android 专有能力，桌面/iOS 用 [NoopReminderController]。
 *
 * ⚠ companion 里的 KEY_ / IN_CLASS_TIMING_ / MODE_ 常量与 Android 侧实现**保持同值**，不要单方面改。
 */
interface ReminderController {

    // —— 提醒服务（幂等对账）——

    /** 启动/刷新课程提醒服务（内部会按当前开关状态对账，多余提醒会被清掉） */
    fun startReminderService()

    /** 停止课程提醒服务 */
    fun stopReminderService()

    // —— 课中提醒（读 course_reminder_prefs）——

    /** 课中提醒开关（超级岛 / 原生实况共用） */
    fun isInClassEnabled(): Boolean

    /** 提醒时机：[ReminderController.IN_CLASS_TIMING_FULL]=全程 / [ReminderController.IN_CLASS_TIMING_BEFORE_END]=距下课 */
    fun getInClassTimingMode(): Int

    /** 距下课分钟数（0..60） */
    fun getInClassLeadMinutes(): Int

    // —— 通知权限能力 ——

    /** 能否发布「实况通知」（API 36+ 的 promoted notifications） */
    fun canPostPromotedNotifications(): Boolean

    /** 是否已授予 POST_NOTIFICATIONS 通知权限 */
    fun hasNotificationPermission(): Boolean

    /** 是否已授予「免打扰访问权限」（自动开勿扰的前提） */
    fun isDndPermissionGranted(): Boolean

    /** 该平台能否改写系统勿扰状态 */
    fun supportsDnd(): Boolean = false

    // —— 超级岛 / Shizuku ——

    /** 设备是否支持超级岛（非 HyperOS 返回 false） */
    fun isIslandSupported(): Boolean

    /**
     * Whether this platform has a Live Activity container (iOS Dynamic Island /
     * lock screen).
     *
     * Kept separate from [isIslandSupported] on purpose: that one probes for HyperOS
     * super island, which enables Xiaomi-specific controls (Shizuku, left/right
     * slot, AOD). This method only reports whether a live container exists at all.
     * iOS 26+ is always true, but skips the Xiaomi-specific sub-options.
     *
     * The shared screen computes:
     * `showIslandCard = isIslandSupported() || supportsLiveActivity()`.
     *
     * Defaults to false so desktops never need to implement it.
     */
    fun supportsLiveActivity(): Boolean = false

    /** Shizuku 服务是否在运行 */
    fun isShizukuRunning(): Boolean

    /** Shizuku 是否已授权本应用 */
    fun isShizukuAuthorized(): Boolean

    /** 请求 Shizuku 授权，结果回调 true=已授权 */
    fun requestShizukuPermission(onResult: (Boolean) -> Unit)

    // —— 系统能力探测（不发起跳转）——

    /** 是否允许设置精确闹钟（API 31+ canScheduleExactAlarms；更低版本恒 true） */
    fun canScheduleExactAlarms(): Boolean

    /** 本应用是否已被电池优化白名单忽略 */
    fun isIgnoringBatteryOptimizations(): Boolean

    /** 当前系统版本是否有「实况通知」相关设置入口（Android = SDK_INT >= 36） */
    val supportsPromotedNotifications: Boolean

    // —— 权限 / 系统设置页（onResult 在返回后触发）——

    /** 申请 POST_NOTIFICATIONS 通知权限（授权弹窗），回调 granted */
    fun requestNotificationPermission(onResult: (granted: Boolean) -> Unit)

    /** 打开「免打扰访问权限」设置页（ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS） */
    fun openNotificationAccessSettings(onResult: () -> Unit)

    /** 打开「实况通知」设置页（首选 MANAGE_APP_PROMOTED_NOTIFICATIONS，失败退回应用通知设置） */
    fun openPromotedNotificationSettings(onResult: () -> Unit)

    /** 打开「精确闹钟」授权页（ACTION_REQUEST_SCHEDULE_EXACT_ALARM） */
    fun openExactAlarmSettings(onResult: () -> Unit)

    /** 打开「电池优化」授权页（首选 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS，失败退回白名单列表） */
    fun openBatteryOptimizationSettings(onResult: () -> Unit)

    /** 打开本应用详情设置页（ACTION_APPLICATION_DETAILS_SETTINGS，自启动检查入口） */
    fun openAppDetailsSettings(onResult: () -> Unit)

    companion object {
        // —— course_reminder_prefs 键（与 Android 侧 CourseReminderHelper.KEY_* 同值）——
        /** 课中提醒统一开关 */
        const val KEY_IN_CLASS = "in_class_reminder_enabled"
        /** 提醒时机档位（0=全程，1=距下课） */
        const val KEY_IN_CLASS_TIMING_MODE = "in_class_timing_mode"
        /** 距下课分钟数 */
        const val KEY_IN_CLASS_LEAD_MINUTES = "in_class_lead_minutes"

        // —— 提醒时机档位（与 CourseReminderHelper.IN_CLASS_TIMING_* 同值）——
        const val IN_CLASS_TIMING_FULL = 0
        const val IN_CLASS_TIMING_BEFORE_END = 1

        // —— 勿扰档位（与 Android 侧 ClassDndHelper.MODE_* 同值）——
        const val MODE_DND = 0
        const val MODE_SILENT = 1
        const val MODE_PRIORITY = 2
    }
}

/**
 * 桌面/iOS 的空实现：探测返回 false / 0，发起类方法不动作，权限类回调按「被拒绝」（granted=false），
 * 让页面走到原有的引导文案分支。
 */
object NoopReminderController : ReminderController {
    override fun startReminderService() {}
    override fun stopReminderService() {}
    override fun isInClassEnabled(): Boolean = false
    override fun getInClassTimingMode(): Int = ReminderController.IN_CLASS_TIMING_FULL
    /** 与 Android 侧默认值一致（10 分钟） */
    override fun getInClassLeadMinutes(): Int = 10
    override fun canPostPromotedNotifications(): Boolean = false
    override fun hasNotificationPermission(): Boolean = false
    override fun isDndPermissionGranted(): Boolean = false
    override fun isIslandSupported(): Boolean = false
    override fun isShizukuRunning(): Boolean = false
    override fun isShizukuAuthorized(): Boolean = false
    override fun requestShizukuPermission(onResult: (Boolean) -> Unit) = onResult(false)
    override fun canScheduleExactAlarms(): Boolean = false
    override fun isIgnoringBatteryOptimizations(): Boolean = false
    override val supportsPromotedNotifications: Boolean get() = false
    override fun requestNotificationPermission(onResult: (granted: Boolean) -> Unit) = onResult(false)
    override fun openNotificationAccessSettings(onResult: () -> Unit) {}
    override fun openPromotedNotificationSettings(onResult: () -> Unit) {}
    override fun openExactAlarmSettings(onResult: () -> Unit) {}
    override fun openBatteryOptimizationSettings(onResult: () -> Unit) {}
    override fun openAppDetailsSettings(onResult: () -> Unit) {}
}
