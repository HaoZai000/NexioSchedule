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

/** 实况通知管理页 action（原页面 `Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")`） */
private const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS =
    "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

/**
 * [ReminderController] 的 Android 实现 —— 包装 `CourseReminderHelper` / `ClassDndHelper` /
 * `IslandNotificationHelper` / `ShizukuManager` 等全局单例，`AlarmManager` / `PowerManager`
 * 系统服务，以及权限弹窗与系统设置页跳转。
 *
 * 同 [AndroidDiagnosticRecorder] / [AndroidUpdateController] 的套路：单例方法都带 `context`，
 * 本层在构造时捕获宿主注入的 context，接口层保持无参 —— `:ui-shared` 的「课程提醒」页只依赖
 * 跨平台接口（commonMain 无 android / java import）。宿主（AndroidSettingsScreen 的
 * `directPlatform` / AppRouteContent 的 CourseReminder 路由）在调用点创建本实例并注入：
 * `reminderController = remember { AndroidReminderController(context) }`。
 *
 * ## 设置页 / 权限的结果回调
 * 原页面用 `rememberLauncherForActivityResult`（每类请求一个 launcher + `rememberUpdatedState`
 * 回调）。这里改用 `ActivityResultRegistry.register(固定 key, 合同) { 走回调槽 }` ——
 * 非组合环境也能注册；**同 key 重复注册只会替换回调槽，不会抛「重复 key」**（见
 * ActivityResultRegistry.register 字节码：`keyToCallback.put`）；Activity 重建后用同 key 再注册
 * 还能接上恢复的挂起结果。回调槽在每次发起请求前更新，等价于原实现的最新回调语义。
 *
 * 拿不到 `ActivityResultRegistryOwner`（异常注入了 Application context 等）时降级：
 * 直接 `startActivity` 并立即回调，权限申请按「被拒绝」回调 —— 正常宿主（MainActivity）不会走到。
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

    /** 原页面 `android.os.Build.VERSION.SDK_INT >= 36`（实况通知设置页 API 36+ 才存在） */
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
        // 原实现逐字保留：首选「实况通知管理页」，打不开（ActivityNotFoundException 等）时
        // 退回「应用通知设置」
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
        // 原实现逐字保留：首选「请求忽略电池优化」授权页，打不开时退回「电池优化白名单列表」
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
         * 可接上恢复的挂起结果。两处注入点（AppRouteContent 的 CourseReminder 路由 /
         * AndroidSettingsScreen 的 directPlatform）不会同时存活，即便先后注册也是后者接管，行为等价。
         */
        const val KEY_SETTINGS_RESULT = "com.haooz.chedule.reminder.settings_result"
        const val KEY_NOTIFICATION_PERMISSION = "com.haooz.chedule.reminder.notification_permission"
    }
}

/** 从任意 Context 向外层找到宿主 Activity（同 WebViewScreen 的 findActivity 套路） */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
