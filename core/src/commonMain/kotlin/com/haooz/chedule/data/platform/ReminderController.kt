package com.haooz.chedule.data.platform

/** 由宿主提供提醒调度、权限状态和设置页入口。偏好键与档位值也是平台实现的持久化契约。 */
interface ReminderController {

    // —— 提醒服务（幂等对账）——

    /** 启动/刷新课程提醒服务（内部会按当前开关状态对账，多余提醒会被清掉） */
    fun startReminderService()

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

    fun hasNotificationPermission(): Boolean

    /** 是否已授予「免打扰访问权限」（自动开勿扰的前提） */
    fun isDndPermissionGranted(): Boolean

    /** 该平台能否改写系统勿扰状态 */
    fun supportsDnd(): Boolean = false

    // —— 超级岛 / Shizuku ——

    /** 设备是否支持超级岛（非 HyperOS 返回 false） */
    fun isIslandSupported(): Boolean

    /** 是否提供实时活动容器。与 [isIslandSupported] 分开探测，以免显示 HyperOS 专有设置。 */
    fun supportsLiveActivity(): Boolean = false

    fun isShizukuRunning(): Boolean

    fun isShizukuAuthorized(): Boolean

    fun requestShizukuPermission(onResult: (Boolean) -> Unit)

    // —— 系统能力探测（不发起跳转）——

    /** 是否允许设置精确闹钟（API 31+ canScheduleExactAlarms；更低版本恒 true） */
    fun canScheduleExactAlarms(): Boolean

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
        const val KEY_IN_CLASS = "in_class_reminder_enabled"
        const val KEY_IN_CLASS_TIMING_MODE = "in_class_timing_mode"
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

/** 未提供提醒能力时不执行调度或跳转；权限申请回调 false。 */
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
