package com.haooz.chedule.data.platform

/** 只读能力检查的结果；Ready 仅表示前置条件满足，不保证系统会展示超级岛。 */
enum class IslandAuthorizationState {
    Unsupported,
    NotificationPermissionRequired,
    ChannelBlocked,
    ShizukuNotRunning,
    ShizukuPermissionRequired,
    Ready;

    /** 缺少 Shizuku 时仍可发布普通通知；系统或通知权限不满足时不能发送。 */
    val canPostNotification: Boolean
        get() = this == ShizukuNotRunning || this == ShizukuPermissionRequired || this == Ready

    companion object {
        fun evaluate(
            supported: Boolean,
            notificationsAllowed: Boolean,
            channelEnabled: Boolean,
            shizukuRunning: Boolean,
            shizukuAuthorized: Boolean,
        ): IslandAuthorizationState = when {
            !supported -> Unsupported
            !notificationsAllowed -> NotificationPermissionRequired
            !channelEnabled -> ChannelBlocked
            !shizukuRunning -> ShizukuNotRunning
            !shizukuAuthorized -> ShizukuPermissionRequired
            else -> Ready
        }

        /** Android 13 前无 POST_NOTIFICATIONS 运行时权限，但应用通知总开关仍必须开启。 */
        fun notificationsAllowed(
            apiLevel: Int,
            runtimePermissionGranted: Boolean,
            appNotificationsEnabled: Boolean,
        ): Boolean = (apiLevel < 33 || runtimePermissionGranted) && appNotificationsEnabled
    }
}

/** notify 返回不代表上岛成功；网络恢复结果也必须独立于通知提交结果报告。 */
data class IslandNotificationResult(
    val posted: Boolean,
    val authorization: IslandAuthorizationState,
    val bypassUsed: Boolean = false,
    val networkRestored: Boolean = true,
)
