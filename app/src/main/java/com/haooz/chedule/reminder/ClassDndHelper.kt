/**
 * 上课勿扰助手
 *
 * 基于系统勿扰模式（NotificationManager.setInterruptionFilter）实现：
 * - 上课时间到：自动进入勿扰（INTERRUPTION_FILTER_NONE）
 * - 下课时间到：自动退出勿扰（INTERRUPTION_FILTER_ALL）
 * - 未授予「勿扰权限」时不做任何降级操作，仅引导用户授权
 *
 * 触发链路有三条，互为兜底：
 * 1. 每节课的开始/结束精确闹钟（[scheduleClassDndAlarms]）
 * 2. WidgetRefreshReceiver 每分钟的状态对账（[applyCurrentState]）
 * 3. 实况通知 / 超级岛按钮的手动开关（[toggleFromNotification]）
 */
package com.haooz.chedule.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import com.haooz.chedule.data.CourseRepository
import java.util.Calendar

object ClassDndHelper {

    private const val TAG = "ClassDndHelper"
    private const val PREFS_NAME = "course_reminder_prefs"
    /** 记录当前勿扰是否由本应用开启，避免误关用户手动开启的勿扰 */
    private const val KEY_DND_APPLIED_BY_APP = "dnd_applied_by_app"

    // 上课/下课勿扰闹钟的 requestCode 基址（与课程提醒闹钟的 10000 段错开）
    private const val RC_DND_START_BASE = 30000
    private const val RC_DND_END_BASE = 40000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** 是否已授予「勿扰权限」（免打扰访问权限） */
    fun isDndPermissionGranted(context: Context): Boolean {
        return try {
            notificationManager(context).isNotificationPolicyAccessGranted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query notification policy access", e)
            false
        }
    }

    /** 系统当前是否处于勿扰状态 */
    @Suppress("DEPRECATION")
    fun isDndOn(context: Context): Boolean {
        return try {
            notificationManager(context).currentInterruptionFilter !=
                NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query interruption filter", e)
            false
        }
    }

    /** 当前勿扰是否由本应用开启（决定下课时要不要回收） */
    fun isDndAppliedByApp(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DND_APPLIED_BY_APP, false)
    }

    /**
     * 开关系统勿扰模式。
     * [byApp] = true 时记录"由本应用开启"，下课/关闭开关时可安全回收；
     * 用户自己手动开的勿扰不会被本应用关闭。
     */
    private fun setDnd(context: Context, enable: Boolean, byApp: Boolean) {
        if (!isDndPermissionGranted(context)) {
            Log.w(TAG, "setDnd($enable) skipped: notification policy access not granted")
            return
        }
        try {
            notificationManager(context).setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            prefs(context).edit { putBoolean(KEY_DND_APPLIED_BY_APP, enable && byApp) }
            Log.d(TAG, "setDnd enable=$enable byApp=$byApp")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set interruption filter", e)
        }
    }

    /** 立即由本应用开启勿扰 */
    private fun enableDndByApp(context: Context) = setDnd(context, true, byApp = true)

    /** 若勿扰是此前由本应用开启的，则回收（恢复为全部通知） */
    private fun restoreDndIfApplied(context: Context) {
        if (!isDndAppliedByApp(context)) return
        setDnd(context, false, byApp = false)
    }

    /** 「上课自动开启勿扰」总开关是否可用（受课程提醒总开关约束 + 需要勿扰权限） */
    private fun isFeatureAvailable(context: Context): Boolean {
        val repository = CourseRepository(context)
        val masterEnabled = repository.getPreClassReminder() || repository.getNextDayReminder()
        return masterEnabled && repository.getClassDndEnabled()
    }

    /** 当前是否正处于某节课的上课时间 [start, end) */
    fun isInClass(context: Context): Boolean {
        val repository = CourseRepository(context)
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        for (course in CourseReminderHelper.getTodayCourses(context)) {
            val start = CourseReminderHelper.getCourseStartTime(course, repository)?.toMinutes() ?: continue
            val end = CourseReminderHelper.getCourseEndTime(course, repository)?.toMinutes() ?: continue
            if (currentMinutes >= start && currentMinutes < end) return true
        }
        return false
    }

    /**
     * 状态对账：按当前时间与开关状态，把系统勿扰调整到应有状态。
     * 由上课/下课闹钟、每分钟的 widget 刷新、通知按钮回调共同调用，幂等。
     */
    fun applyCurrentState(context: Context) {
        if (!isFeatureAvailable(context) || !isDndPermissionGranted(context)) {
            restoreDndIfApplied(context)
            return
        }
        if (isInClass(context)) {
            enableDndByApp(context)
        } else {
            restoreDndIfApplied(context)
        }
    }

    /**
     * 实况通知 / 超级岛「上课勿扰」按钮点击：切换「上课自动开启勿扰」开关。
     * 切换后立刻对账——若此刻已上课则立即生效，否则等上课时间点的闹钟触发。
     */
    fun toggleFromNotification(context: Context) {
        val repository = CourseRepository(context)
        val next = !repository.getClassDndEnabled()
        repository.setClassDndEnabled(next)

        if (next && !isDndPermissionGranted(context)) {
            Toast.makeText(context, "请先在「课程提醒」中授予勿扰权限", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(
            context,
            if (next) "已开启上课勿扰，上课时自动免打扰" else "已关闭上课勿扰",
            Toast.LENGTH_SHORT
        ).show()

        if (next) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            scheduleClassDndAlarms(context, alarmManager)
        }
        applyCurrentState(context)
    }

    /**
     * 测试通知专用：立即开关系统勿扰，不看课表、不看开关状态。
     *
     * 测试超级岛用的是硬编码课程（不在真实课表里），走正常链路时 [isInClass] 恒为 false，
     * 按钮点了没有任何可见效果，无法验证「岛按钮 → 广播 → 勿扰」这条链路是否可用。
     * 因此测试通知单独使用本方法，点击即开关勿扰。
     */
    fun toggleDndNowForTest(context: Context) {
        if (!isDndPermissionGranted(context)) {
            Toast.makeText(context, "未授予勿扰权限，请先在课程提醒页授权", Toast.LENGTH_LONG).show()
            return
        }
        val enable = !isDndOn(context)
        setDnd(context, enable, byApp = true)
        Toast.makeText(
            context,
            if (enable) "测试：已开启勿扰" else "测试：已关闭勿扰",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * 为今天的每节课注册「上课开勿扰 / 下课关勿扰」精确闹钟。
     * 与课前提醒闹钟相互独立：即便提醒未开启（但只要总开关开着）也能按课表生效。
     */
    fun scheduleClassDndAlarms(context: Context, alarmManager: AlarmManager) {
        cancelClassDndAlarms(context, alarmManager)
        val repository = CourseRepository(context)
        if (!repository.getClassDndEnabled()) return

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (course in CourseReminderHelper.getTodayCourses(context)) {
            val startMinutes = CourseReminderHelper.getCourseStartTime(course, repository)?.toMinutes()
            val endMinutes = CourseReminderHelper.getCourseEndTime(course, repository)?.toMinutes()
            if (startMinutes == null || endMinutes == null || endMinutes <= startMinutes) continue

            scheduleOne(
                context, alarmManager,
                requestCode = RC_DND_START_BASE + course.id.hashCode(),
                action = ClassDndReceiver.ACTION_CLASS_START,
                minutes = startMinutes,
                currentMinutes = currentMinutes
            )
            scheduleOne(
                context, alarmManager,
                requestCode = RC_DND_END_BASE + course.id.hashCode(),
                action = ClassDndReceiver.ACTION_CLASS_END,
                minutes = endMinutes,
                currentMinutes = currentMinutes
            )
        }
    }

    /**
     * 注册单个精确闹钟。已过去的时间点不再注册（交给下一次调度/每分钟对账补齐），
     * 避免 AlarmManager 立即触发一堆历史闹钟。
     */
    private fun scheduleOne(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        action: String,
        minutes: Int,
        currentMinutes: Int
    ) {
        if (minutes <= currentMinutes) return
        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val intent = Intent(context, ClassDndReceiver::class.java).apply { setAction(action) }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (_: SecurityException) { }
    }

    /** 取消所有上课/下课勿扰闹钟 */
    fun cancelClassDndAlarms(context: Context, alarmManager: AlarmManager) {
        val allCourses = CourseRepository(context).getAllCourses()
        for (course in allCourses) {
            for (requestCode in listOf(
                RC_DND_START_BASE + course.id.hashCode(),
                RC_DND_END_BASE + course.id.hashCode()
            )) {
                val intent = Intent(context, ClassDndReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    /** "HH:mm" -> 当天分钟数；非法返回 null */
    private fun String.toMinutes(): Int? {
        val parts = this.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
}
