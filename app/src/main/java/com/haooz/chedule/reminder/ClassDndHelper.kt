/**
 * 上课勿扰助手
 *
 * 提供两档「上课时自动降低打扰」模式：
 * - 0 勿扰模式 (DND)   = `NotificationManager.setInterruptionFilter(NONE)`
 *   系统屏蔽通知、来电、振动；需要用户授予「免打扰访问权限」
 * - 1 静音模式 (SILENT) = `AudioManager.ringerMode = RINGER_MODE_SILENT`
 *   仅关铃声+振动，通知照常弹出，无需任何运行时权限
 *
 * 下课 / 关闭开关时按开启前保存的原始状态恢复，不会动用户手动改过的设置。
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
import android.media.AudioManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import com.haooz.chedule.data.CourseRepository
import java.util.Calendar

object ClassDndHelper {

    private const val TAG = "ClassDndHelper"
    private const val PREFS_NAME = "course_reminder_prefs"

    /** 标记当前是否由本应用开启过任何一档（避免误关用户手动开的勿扰/静音） */
    private const val KEY_APPLIED = "dnd_applied_by_app"
    /** 当 [KEY_APPLIED]=true 时记录具体档位：0=DND, 1=SILENT */
    private const val KEY_APPLIED_MODE = "dnd_applied_mode"
    /** SILENT 档位下保存的原始 [AudioManager.ringerMode]，下课 / 关闭时还原 */
    private const val KEY_ORIGINAL_RINGER = "dnd_original_ringer_mode"

    // 上课/下课闹钟的 requestCode 基址（与课程提醒闹钟的 10000 段错开）
    private const val RC_DND_START_BASE = 30000
    private const val RC_DND_END_BASE = 40000

    /** 用户可选档位 */
    const val MODE_DND = 0
    const val MODE_SILENT = 1
    const val MODE_PRIORITY = 2

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun audioManager(context: Context) =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun currentMode(context: Context): Int =
        CourseRepository(context).getClassDndMode()

    /** 是否已授予「勿扰权限」（免打扰访问权限，仅 DND 档需要） */
    fun isDndPermissionGranted(context: Context): Boolean {
        return try {
            notificationManager(context).isNotificationPolicyAccessGranted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query notification policy access", e)
            false
        }
    }

    /** 当前所选档位是否已由本应用开启（与 [isDndOn] 不同：不受用户手动改系统的影响） */
    fun isDndAppliedByApp(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_APPLIED, false) && p.getInt(KEY_APPLIED_MODE, MODE_DND) == currentMode(context)
    }

    /**
     * 把当前所选档位应用到系统。
     * 内部先清理之前可能开启的另一档（避免切档时叠加），幂等。
     */
    private fun enableDndByApp(context: Context) {
        val mode = currentMode(context)
        val nm = notificationManager(context)
        val am = audioManager(context)

        // 先清理之前可能开的另一档（DND <-> SILENT 切换时用到）
        val p = prefs(context)
        val wasApplied = p.getBoolean(KEY_APPLIED, false)
        if (wasApplied) {
            val prevMode = p.getInt(KEY_APPLIED_MODE, MODE_DND)
            if (prevMode != mode) {
                restoreAppliedSilent(context, prevMode)
            }
        }

        // 关键：仅在「首次进入 SILENT」时快照原始 ringerMode。
        // 每分钟补发会反复调用本方法，若每次都快照，会把原始值覆盖成 SILENT(0)，
        // 导致下课恢复时读到的还是静音（表现为"下课没关闭"）。
        val wasSilentApplied = wasApplied && p.getInt(KEY_APPLIED_MODE, MODE_DND) == MODE_SILENT
        if (mode == MODE_SILENT && !wasSilentApplied) {
            p.edit { putInt(KEY_ORIGINAL_RINGER, am.ringerMode) }
        }

        var applied = true
        when (mode) {
            MODE_DND -> {
                if (!isDndPermissionGranted(context)) {
                    Log.w(TAG, "enableDndByApp: DND mode requires policy access")
                    return
                }
                try {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set interruption filter to NONE", e)
                    applied = false
                }
            }
            MODE_PRIORITY -> {
                if (!isDndPermissionGranted(context)) {
                    Log.w(TAG, "enableDndByApp: PRIORITY mode requires policy access")
                    return
                }
                try {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set interruption filter to PRIORITY", e)
                    applied = false
                }
            }
            MODE_SILENT -> {
                try {
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set ringerMode to SILENT", e)
                    applied = false
                }
            }
        }
        if (applied) {
            p.edit {
                putBoolean(KEY_APPLIED, true)
                putInt(KEY_APPLIED_MODE, mode)
            }
            Log.d(TAG, "enableDndByApp mode=$mode")
        }
    }

    /** 静默恢复之前应用的档位（不清理 prefs，便于紧接着切到另一档时复用上下文） */
    private fun restoreAppliedSilent(context: Context, prevMode: Int) {
        val nm = notificationManager(context)
        val am = audioManager(context)
        val p = prefs(context)
        when (prevMode) {
            MODE_DND, MODE_PRIORITY -> {
                // DND 与 PRIORITY 都改 interruption filter，恢复时统一回 ALL
                if (isDndPermissionGranted(context)) {
                    try {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore filter to ALL", e)
                    }
                }
            }
            MODE_SILENT -> {
                val original = p.getInt(KEY_ORIGINAL_RINGER, AudioManager.RINGER_MODE_NORMAL)
                try {
                    am.ringerMode = original
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore ringerMode to $original", e)
                }
            }
        }
    }

    /** 若当前所选档位由本应用开启，则回收（恢复全部允许 / 原始铃声） */
    private fun restoreDndIfApplied(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_APPLIED, false)) return
        val prevMode = p.getInt(KEY_APPLIED_MODE, MODE_DND)
        restoreAppliedSilent(context, prevMode)
        p.edit {
            putBoolean(KEY_APPLIED, false)
            remove(KEY_APPLIED_MODE)
            remove(KEY_ORIGINAL_RINGER)
        }
        Log.d(TAG, "restoreDndIfApplied prevMode=$prevMode")
    }

    /** 「上课自动开启勿扰」总开关是否可用（受课程提醒总开关约束） */
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
     * 状态对账：按当前时间与开关状态，把系统调整到应有状态。
     * 由上课/下课闹钟、每分钟的 widget 刷新、通知按钮回调共同调用，幂等。
     */
    fun applyCurrentState(context: Context) {
        if (!isFeatureAvailable(context)) {
            restoreDndIfApplied(context)
            return
        }
        if (!isInClass(context)) {
            restoreDndIfApplied(context)
            return
        }
        // 上课 + 总开关开
        val mode = currentMode(context)
        if ((mode == MODE_DND || mode == MODE_PRIORITY) && !isDndPermissionGranted(context)) {
            // 需要勿扰权限；没权限时不强行开启，也不静默切换到 SILENT（尊重用户选择）
            restoreDndIfApplied(context)
            return
        }
        enableDndByApp(context)
    }

    /**
     * 实况通知 / 超级岛「上课勿扰」按钮点击：切换「上课自动开启勿扰」开关。
     * 切换后立刻对账——若此刻已上课则立即生效，否则等上课时间点的闹钟触发。
     */
    fun toggleFromNotification(context: Context) {
        val repository = CourseRepository(context)
        val next = !repository.getClassDndEnabled()
        repository.setClassDndEnabled(next)

        if (next) {
            val mode = repository.getClassDndMode()
            if ((mode == MODE_DND || mode == MODE_PRIORITY) && !isDndPermissionGranted(context)) {
                Toast.makeText(context, "请先在「课程提醒」中授予勿扰权限", Toast.LENGTH_LONG).show()
                return
            }
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
     * 测试通知专用：立即按当前所选档位开关系统，不看课表、不看开关状态。
     *
     * 测试超级岛用的是硬编码课程（不在真实课表里），走正常链路时 [isInClass] 恒为 false，
     * 按钮点了没有任何可见效果，无法验证「岛按钮 → 广播 → 勿扰」这条链路是否可用。
     * 因此测试通知单独使用本方法，点击即开关。
     */
    fun toggleDndNowForTest(context: Context) {
        val mode = currentMode(context)
        if ((mode == MODE_DND || mode == MODE_PRIORITY) && !isDndPermissionGranted(context)) {
            Toast.makeText(context, "勿扰/优先模式需要先授予勿扰权限", Toast.LENGTH_LONG).show()
            return
        }
        // 用 prefs 判断「当前所选档位是否已开」——不被用户手动改系统的行为干扰
        val currentlyApplied = prefs(context).getBoolean(KEY_APPLIED, false) &&
            prefs(context).getInt(KEY_APPLIED_MODE, MODE_DND) == mode
        if (currentlyApplied) {
            restoreDndIfApplied(context)
            Toast.makeText(context, "测试：已关闭", Toast.LENGTH_SHORT).show()
        } else {
            enableDndByApp(context)
            val label = when (mode) {
                MODE_SILENT -> "测试：已开启静音（通知照弹，关铃声+振动）"
                MODE_PRIORITY -> "测试：已开启优先（屏蔽普通通知，闹钟仍响）"
                else -> "测试：已开启勿扰（完全屏蔽通知）"
            }
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 为今天的每节课注册「上课开 / 下课关」精确闹钟。
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
            Log.d(TAG, "scheduleOne OK action=$action at=" +
                java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerAt)) +
                " rc=$requestCode")
        } catch (e: SecurityException) {
            Log.e(TAG, "scheduleOne SecurityException action=$action (需精确闹钟权限)", e)
        }
    }

    /** 取消所有上课/下课闹钟 */
    fun cancelClassDndAlarms(context: Context, alarmManager: AlarmManager) {
        val allCourses = CourseRepository(context).getAllCourses()
        for (course in allCourses) {
            val id = course.id.hashCode()
            for ((requestCode, action) in listOf(
                RC_DND_START_BASE + id to ClassDndReceiver.ACTION_CLASS_START,
                RC_DND_END_BASE + id to ClassDndReceiver.ACTION_CLASS_END
            )) {
                // 必须与 scheduleOne 里相同的 action 构造 PendingIntent，
                // 否则 PendingIntent 身份不一致，cancel 会静默失效（导致闹钟残留）
                val intent = Intent(context, ClassDndReceiver::class.java).apply { setAction(action) }
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