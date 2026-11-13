package com.haooz.nexio.ohos

import com.haooz.chedule.data.platform.ReminderController
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.parseJsonObject

/**
 * [ReminderController] 的鸿蒙实现。
 *
 * | 能力 | 安卓 | 鸿蒙 |
 * |---|---|---|
 * | 课前 / 次日提醒 | AlarmManager 精确闹钟 | 普通通知（ArkTS 进程内定时） |
 * | 通知权限 | 运行时权限 | `notificationManager.requestEnableNotification()` |
 * | 课中实况 | 常驻通知 + 超级岛 | 实况窗未接（等 AGC 权益） |
 * | 精确闹钟 / 电池优化 / 免打扰 / Shizuku | 有系统 API | 无对应概念，页面不展示这些行 |
 *
 * 探测类方法的口径与 iOS 一致：**没有对应能力的返回「已满足」或「不支持」**。
 * 代理提醒由系统托管、普通通知在进程内定时 → [canScheduleExactAlarms] 恒 true；
 * 鸿蒙没有电池优化白名单概念 → [isIgnoringBatteryOptimizations] 恒 true。
 */
class OhosReminderController : ReminderController {

    private val prefs get() = AppStorage.store("course_reminder_prefs")

    // —— 提醒服务（幂等对账）——

    override fun startReminderService() = OhosCourseReminder.reconcile()

    override fun stopReminderService() = OhosCourseReminder.stop()

    // —— 课中提醒（读 course_reminder_prefs）——

    override fun isInClassEnabled(): Boolean =
        prefs.getBoolean(ReminderController.KEY_IN_CLASS, false)

    override fun getInClassTimingMode(): Int = prefs.getInt(
        ReminderController.KEY_IN_CLASS_TIMING_MODE,
        ReminderController.IN_CLASS_TIMING_FULL,
    )

    override fun getInClassLeadMinutes(): Int =
        prefs.getInt(ReminderController.KEY_IN_CLASS_LEAD_MINUTES, 10).coerceIn(0, 60)

    // —— 通知权限 ——

    /** 鸿蒙没有「实况通知」这一层，等实况窗权益批下来再谈。 */
    override fun canPostPromotedNotifications(): Boolean = false

    /** 同步接口：数据源是 ArkTS 在启动/授权后回写的缓存。 */
    override fun hasNotificationPermission(): Boolean = ReminderBridge.notificationEnabled

    override fun isDndPermissionGranted(): Boolean = false

    // —— 超级岛 / Shizuku（鸿蒙没有）——

    override fun isIslandSupported(): Boolean = false

    override fun isShizukuRunning(): Boolean = false

    override fun isShizukuAuthorized(): Boolean = false

    override fun requestShizukuPermission(onResult: (Boolean) -> Unit) = onResult(false)

    // —— 系统能力探测 ——

    override fun canScheduleExactAlarms(): Boolean = true

    override fun isIgnoringBatteryOptimizations(): Boolean = true

    override val supportsPromotedNotifications: Boolean get() = false

    /**
     * 实况窗（Live View Kit）暂未接入：需要先在 AGC 申请权益并审核通过，
     * 且要按场景模板提交。接上之后这里改 true，页面会自动出现实况窗卡片。
     */
    override fun supportsLiveActivity(): Boolean = false

    // —— 权限 / 系统设置页 ——

    override fun requestNotificationPermission(onResult: (granted: Boolean) -> Unit) {
        ReminderBridge.request("requestNotification", "{}") { _, payload ->
            val enabled = runCatching {
                parseJsonObject(payload).optBoolean("enabled", false)
            }.getOrDefault(false)
            onResult(enabled)
        }
    }

    /** 鸿蒙把「通知设置」收在应用通知页里，这里直接交给系统的授权弹窗。 */
    override fun openNotificationAccessSettings(onResult: () -> Unit) {
        ReminderBridge.request("openNotificationSettings", "{}")
        onResult()
    }

    override fun openPromotedNotificationSettings(onResult: () -> Unit) = onResult()

    override fun openExactAlarmSettings(onResult: () -> Unit) = onResult()

    override fun openBatteryOptimizationSettings(onResult: () -> Unit) = onResult()

    override fun openAppDetailsSettings(onResult: () -> Unit) = onResult()
}
