package com.haooz.chedule.ui.utils

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import com.haooz.chedule.data.platform.ReminderController
import com.haooz.chedule.reminder.ClassDndHelper
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.shizuku.ShizukuManager

private const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS =
    "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

/**
 * 通过宿主 Activity 的结果注册表接收权限与设置页结果。设置页和权限请求各用一个固定 key，
 * 回调在发起请求时更新。无注册表时直接启动设置页并立即回调，权限请求则按拒绝处理。
 */
class AndroidReminderController(private val context: Context) : ReminderController {

    /** 宿主 Activity（结果回调依赖它；LocalContext 在正常宿主里就是 MainActivity） */
    private val activity: Activity? by lazy { context.findActivity() }

    // —— 结果回调槽：每类请求一个固定 key 的 launcher，发起请求前更新槽内回调 ——
    private var settingsResult: (() -> Unit)? = null
    private var notificationPermissionResult: ((Boolean) -> Unit)? = null

    /** 设置页结果通道（五个 openXxxSettings 共用；同一时刻只会有一个设置页在前台） */
    private val settingsLauncher: ActivityResultLauncher<Intent>? by lazy {
        (activity as? ActivityResultRegistryOwner)?.activityResultRegistry?.register(
            KEY_SETTINGS_RESULT,
            ActivityResultContracts.StartActivityForResult()
        ) { settingsResult?.invoke() }
    }

    /** POST_NOTIFICATIONS 授权弹窗结果通道 */
    private val notificationPermissionLauncher: ActivityResultLauncher<String>? by lazy {
        (activity as? ActivityResultRegistryOwner)?.activityResultRegistry?.register(
            KEY_NOTIFICATION_PERMISSION,
            ActivityResultContracts.RequestPermission()
        ) { granted -> notificationPermissionResult?.invoke(granted) }
    }

    // ————————————————————————— 提醒服务（CourseReminderHelper） —————————————————————————

    override fun startReminderService() = CourseReminderHelper.startReminderService(context)

    override fun stopReminderService() = CourseReminderHelper.stopReminderService(context)

    // ————————————————————————— 课中提醒（读 course_reminder_prefs） —————————————————————————

    override fun isInClassEnabled(): Boolean = CourseReminderHelper.isInClassEnabled(context)

    override fun getInClassTimingMode(): Int = CourseReminderHelper.getInClassTimingMode(context)

    override fun getInClassLeadMinutes(): Int = CourseReminderHelper.getInClassLeadMinutes(context)

    // ————————————————————————— 通知权限能力 —————————————————————————

    override fun canPostPromotedNotifications(): Boolean =
        CourseReminderHelper.canPostPromotedNotifications(context)

    override fun hasNotificationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    override fun isDndPermissionGranted(): Boolean = ClassDndHelper.isDndPermissionGranted(context)

    override fun supportsDnd(): Boolean = true

    // ————————————————————————— 超级岛 / Shizuku —————————————————————————

    override fun isIslandSupported(): Boolean = IslandNotificationHelper.isIslandSupported(context)

    override fun isShizukuRunning(): Boolean = ShizukuManager.isShizukuRunning()

    override fun isShizukuAuthorized(): Boolean = ShizukuManager.checkSelfPermission()

    override fun requestShizukuPermission(onResult: (Boolean) -> Unit) =
        IslandNotificationHelper.requestShizukuPermission(onResult)

    // ————————————————————————— 系统能力探测 —————————————————————————

    override fun canScheduleExactAlarms(): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return ApiCompat.canScheduleExactAlarms(alarmManager)
    }

    override fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    override val supportsPromotedNotifications: Boolean
        get() = Build.VERSION.SDK_INT >= 36

    // ————————————————————————— 权限 / 系统设置页 —————————————————————————

    override fun requestNotificationPermission(onResult: (granted: Boolean) -> Unit) {
        // 先取 launcher（触发注册：恢复态下的陈旧挂起结果在此吞掉，不串到本次回调），再更新槽
        val launcher = notificationPermissionLauncher
        notificationPermissionResult = onResult
        if (launcher == null) {
            // 无 Activity 宿主：拿不到授权弹窗，按「被拒绝」回调，页面走原有引导分支
            onResult(false)
            return
        }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun openNotificationAccessSettings(onResult: () -> Unit) =
        launchSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), onResult)

    override fun openPromotedNotificationSettings(onResult: () -> Unit) {
        // 部分设备没有实况通知管理页，改为打开应用通知设置。
        try {
            launchSettings(
                Intent(ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS).apply { data = packageUri() },
                onResult
            )
        } catch (_: Exception) {
            launchSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                },
                onResult
            )
        }
    }

    override fun openExactAlarmSettings(onResult: () -> Unit) =
        launchSettings(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = packageUri() },
            onResult
        )

    override fun openBatteryOptimizationSettings(onResult: () -> Unit) {
        // 设备没有单应用授权页时，打开电池优化白名单列表。
        try {
            launchSettings(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = packageUri()
                },
                onResult
            )
        } catch (_: Exception) {
            launchSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), onResult)
        }
    }

    override fun openAppDetailsSettings(onResult: () -> Unit) =
        launchSettings(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = packageUri() },
            onResult
        )

    // ————————————————————————— 内部工具 —————————————————————————

    /**
     * 发起「设置页 + 等结果」：先取 launcher（触发注册并吞掉恢复态的陈旧结果），
     * 再更新回调槽，最后 launch。无 Activity 宿主时降级为直接跳转并立即回调。
     */
    private fun launchSettings(intent: Intent, onResult: () -> Unit) {
        val launcher = settingsLauncher
        settingsResult = onResult
        if (launcher == null) {
            startOutside(intent)
            onResult()
            return
        }
        launcher.launch(intent)
    }

    /** 无 Activity 宿主的兜底跳转（非 Activity context 必须带 NEW_TASK，否则抛 AndroidRuntimeException） */
    private fun startOutside(intent: Intent) {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun packageUri(): Uri = Uri.parse("package:${context.packageName}")

    private companion object {
        /**
         * 固定 key：同 key 重复注册 = 替换回调槽（不抛错）；Activity 重建后注册同 key
         * 可接上恢复的挂起结果。两处注入点（AppRouteContent / SettingsScreen androidPane）
         * 不会同时存活，即便先后注册也是后者接管，行为等价。
         */
        const val KEY_SETTINGS_RESULT = "com.haooz.chedule.reminder.settings_result"
        const val KEY_NOTIFICATION_PERMISSION = "com.haooz.chedule.reminder.notification_permission"
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
