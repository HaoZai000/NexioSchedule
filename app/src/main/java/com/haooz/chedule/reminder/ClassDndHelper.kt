// 上课勿扰状态机：
// - 进入课堂时写入目标状态一次，写入前快照系统原状态
// - 课堂期间只观察：用户在课中手动改过就不再强行写回，也不销毁快照
// - 下课/离开课堂时校验：系统状态仍等于本应用写入的值才还原快照；否则视为用户已接管，保留现状
// - 用户在应用内关开关 / 切档位属于明确指令，无条件还原快照后再按新状态处理
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

    // 本应用是否正在接管系统状态
    private const val KEY_APPLIED = "dnd_applied_by_app"
    private const val KEY_APPLIED_MODE = "dnd_applied_mode"
    // 接管前系统的原始值；只在接管那一刻记录一次，用户课中改动不会污染快照
    private const val KEY_ORIGINAL_RINGER = "dnd_original_ringer_mode"
    private const val KEY_ORIGINAL_FILTER = "dnd_original_interruption_filter"
    // 接管时系统实际呈现的值，用于下课判断「有没有被用户动过」
    private const val KEY_SET_STATE = "dnd_set_state"

    // 与课程提醒闹钟的 10000 段错开
    private const val RC_DND_START_BASE = 30000
    private const val RC_DND_END_BASE = 40000

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

    // DND/PRIORITY 需要「免打扰访问权限」
    fun isDndPermissionGranted(context: Context): Boolean {
        return try {
            notificationManager(context).isNotificationPolicyAccessGranted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query notification policy access", e)
            false
        }
    }

    // 只看本应用是否登记接管，不受用户手动改系统的影响
    fun isDndAppliedByApp(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_APPLIED, false)
    }

    /** 目标档位对应的系统值 */
    private fun targetState(mode: Int): Int = when (mode) {
        MODE_SILENT -> AudioManager.RINGER_MODE_SILENT
        MODE_DND -> NotificationManager.INTERRUPTION_FILTER_NONE
        MODE_PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
        else -> AudioManager.RINGER_MODE_SILENT
    }

    /** 读取当前系统状态；读不到返回 null，调用方据此放弃干预而非误伤用户设置 */
    private fun readCurrentState(context: Context, mode: Int): Int? = try {
        if (mode == MODE_SILENT) {
            audioManager(context).ringerMode
        } else {
            notificationManager(context).currentInterruptionFilter
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read current system state for mode=$mode", e)
        null
    }

    private fun writeState(context: Context, mode: Int): Boolean {
        return try {
            when (mode) {
                MODE_SILENT -> {
                    audioManager(context).ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                MODE_DND -> {
                    notificationManager(context)
                        .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
                MODE_PRIORITY -> {
                    notificationManager(context)
                        .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write system state for mode=$mode", e)
            false
        }
    }

    /** 接管前快照：铃声模式与勿扰过滤值都记，切档位后也能还原到最原始的值 */
    private fun snapshotOriginal(context: Context) {
        prefs(context).edit {
            try {
                putInt(KEY_ORIGINAL_RINGER, audioManager(context).ringerMode)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to snapshot ringer mode", e)
                remove(KEY_ORIGINAL_RINGER)
            }
            try {
                putInt(KEY_ORIGINAL_FILTER, notificationManager(context).currentInterruptionFilter)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to snapshot interruption filter", e)
                remove(KEY_ORIGINAL_FILTER)
            }
        }
    }

    /**
     * 把系统状态还原到接管前的快照值。
     * 缺少对应快照时不动系统；DND/PRIORITY 没有权限时无法写回，同样跳过。
     */
    private fun applyOriginalSnapshot(context: Context, mode: Int) {
        val p = prefs(context)
        when (mode) {
            MODE_SILENT -> {
                if (!p.contains(KEY_ORIGINAL_RINGER)) return
                val original = p.getInt(KEY_ORIGINAL_RINGER, AudioManager.RINGER_MODE_NORMAL)
                try {
                    audioManager(context).ringerMode = original
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore ringerMode to $original", e)
                }
            }
            MODE_DND, MODE_PRIORITY -> {
                if (!p.contains(KEY_ORIGINAL_FILTER)) return
                if (!isDndPermissionGranted(context)) {
                    Log.w(TAG, "Skip restoring filter: policy access revoked")
                    return
                }
                // 还原快照而非粗暴 ALL，用户可能原本就开着 PRIORITY/ALARMS
                val original = p.getInt(
                    KEY_ORIGINAL_FILTER,
                    NotificationManager.INTERRUPTION_FILTER_ALL
                )
                try {
                    notificationManager(context).setInterruptionFilter(original)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore filter to $original", e)
                }
            }
        }
    }

    private fun clearSession(context: Context) {
        prefs(context).edit {
            putBoolean(KEY_APPLIED, false)
            remove(KEY_APPLIED_MODE)
            remove(KEY_ORIGINAL_RINGER)
            remove(KEY_ORIGINAL_FILTER)
            remove(KEY_SET_STATE)
        }
    }

    /** 接管时系统实际呈现的值；没记录则退回目标档位的理论值 */
    private fun appliedState(context: Context, mode: Int): Int {
        val p = prefs(context)
        return if (p.contains(KEY_SET_STATE)) {
            p.getInt(KEY_SET_STATE, targetState(mode))
        } else {
            targetState(mode)
        }
    }

    /** 进入课堂：快照原状态后写入一次目标状态，返回是否接管成功 */
    private fun takeOver(context: Context): Boolean {
        val mode = currentMode(context)
        if ((mode == MODE_DND || mode == MODE_PRIORITY) && !isDndPermissionGranted(context)) {
            Log.w(TAG, "takeOver skipped: mode=$mode requires policy access")
            return false
        }
        snapshotOriginal(context)
        if (!writeState(context, mode)) return false

        // 记录写入后系统实际呈现的值，用来在下课判断用户有没有动过
        val accepted = readCurrentState(context, mode) ?: targetState(mode)
        prefs(context).edit {
            putBoolean(KEY_APPLIED, true)
            putInt(KEY_APPLIED_MODE, mode)
            putInt(KEY_SET_STATE, accepted)
        }
        Log.d(TAG, "takeOver mode=$mode state=$accepted")
        return true
    }

    /**
     * 交还系统状态。
     * @param requireConsistency true 用于「下课自动恢复」：只有当系统状态仍等于本应用写入的值
     *                           时才还原快照；用户在课中改过说明已自行接管，保持现状。
     *                           false 用于用户在应用内的明确指令（关开关 / 切档位），无条件还原。
     */
    private fun handBack(context: Context, requireConsistency: Boolean) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_APPLIED, false)) {
            clearSession(context)
            return
        }
        val mode = p.getInt(KEY_APPLIED_MODE, MODE_DND)

        if (requireConsistency) {
            val expected = appliedState(context, mode)
            val current = readCurrentState(context, mode)
            if (current == null) {
                // 读不到就不还原，避免覆盖用户手动设置
                Log.w(TAG, "handBack skipped: cannot read system state")
                clearSession(context)
                return
            }
            if (current != expected) {
                Log.d(TAG, "handBack skipped: user owns state now ($current != $expected)")
                clearSession(context)
                return
            }
        }

        applyOriginalSnapshot(context, mode)
        Log.d(TAG, "handBack restored original state mode=$mode")
        clearSession(context)
    }

    // 受课程提醒总开关约束
    private fun isFeatureAvailable(context: Context): Boolean {
        val repository = CourseRepository(context)
        val masterEnabled = repository.getPreClassReminder() || repository.getNextDayReminder()
        return masterEnabled && repository.getClassDndEnabled()
    }

    // 判断是否在 [start, end) 课堂时间
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

    // 闹钟/每分钟刷新/通知按钮共同调用，幂等
    fun applyCurrentState(context: Context) {
        if (!isFeatureAvailable(context)) {
            // 用户在应用内关掉开关，明确要求停止管理
            handBack(context, requireConsistency = false)
            return
        }
        if (!isInClass(context)) {
            // 下课：系统状态还和本应用设置的一致才还原
            handBack(context, requireConsistency = true)
            return
        }
        val mode = currentMode(context)
        if ((mode == MODE_DND || mode == MODE_PRIORITY) && !isDndPermissionGranted(context)) {
            // 没权限时不强行开启，也不静默切到 SILENT
            handBack(context, requireConsistency = false)
            return
        }

        val p = prefs(context)
        if (!p.getBoolean(KEY_APPLIED, false)) {
            takeOver(context)
            return
        }

        val appliedMode = p.getInt(KEY_APPLIED_MODE, MODE_DND)
        if (appliedMode != mode) {
            // 用户在应用内换了档位：先还原旧档位，再按新档位重新接管
            applyOriginalSnapshot(context, appliedMode)
            clearSession(context)
            takeOver(context)
            return
        }

        // 已接管且仍在上课：只观察，不强行恢复。用户课中改过就尊重用户的决定；
        // 若又改回本应用写入的那个值，下课的一致性校验会通过，仍会还原到课前状态。
        val expected = appliedState(context, mode)
        val current = readCurrentState(context, mode)
        if (current != null && current != expected) {
            Log.d(TAG, "In class, user changed state ($current != $expected), leave it alone")
        }
    }

    // 切换后立刻对账：已上课则立即生效，否则等上课闹钟
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

    // 测试岛课程不在真实课表，isInClass 恒 false，走正常链路无法验证按钮；故立即开关
    fun toggleDndNowForTest(context: Context) {
        val mode = currentMode(context)
        if ((mode == MODE_DND || mode == MODE_PRIORITY) && !isDndPermissionGranted(context)) {
            Toast.makeText(context, "勿扰/优先模式需要先授予勿扰权限", Toast.LENGTH_LONG).show()
            return
        }
        val p = prefs(context)
        val applied = p.getBoolean(KEY_APPLIED, false)
        if (applied && p.getInt(KEY_APPLIED_MODE, MODE_DND) == mode) {
            handBack(context, requireConsistency = false)
            Toast.makeText(context, "测试：已关闭", Toast.LENGTH_SHORT).show()
            return
        }
        if (applied) {
            // 档位换了：先卸掉旧档位再按新档位接管
            applyOriginalSnapshot(context, p.getInt(KEY_APPLIED_MODE, MODE_DND))
            clearSession(context)
        }
        if (takeOver(context)) {
            val label = when (mode) {
                MODE_SILENT -> "测试：已开启静音（通知照弹，关铃声+振动）"
                MODE_PRIORITY -> "测试：已开启优先（屏蔽普通通知，闹钟仍响）"
                else -> "测试：已开启勿扰（完全屏蔽通知）"
            }
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "测试：开启失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 与课前提醒闹钟独立：只要总开关开着就按课表生效
    fun scheduleClassDndAlarms(context: Context, alarmManager: AlarmManager) {
        cancelClassDndAlarms(context, alarmManager)
        val repository = CourseRepository(context)
        if (!repository.getClassDndEnabled()) return

        for (course in CourseReminderHelper.getTodayCourses(context)) {
            // 与岛/课前提醒共用时间戳，避免两边上课时刻错开
            val startMillis = CourseReminderHelper.parseTimeToTodayMillis(
                CourseReminderHelper.getCourseStartTime(course, repository)
            )
            val endMillis = CourseReminderHelper.parseTimeToTodayMillis(
                CourseReminderHelper.getCourseEndTime(course, repository)
            )
            if (startMillis <= 0L || endMillis <= startMillis) continue

            scheduleOne(
                context, alarmManager,
                requestCode = RC_DND_START_BASE + course.id.hashCode(),
                action = ClassDndReceiver.ACTION_CLASS_START,
                triggerAt = startMillis
            )
            scheduleOne(
                context, alarmManager,
                requestCode = RC_DND_END_BASE + course.id.hashCode(),
                action = ClassDndReceiver.ACTION_CLASS_END,
                triggerAt = endMillis
            )
        }
    }

    // 已过去的时间点不注册，避免 AlarmManager 立即触发一堆历史闹钟
    private fun scheduleOne(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        action: String,
        triggerAt: Long
    ) {
        if (triggerAt <= System.currentTimeMillis()) return

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

    fun cancelClassDndAlarms(context: Context, alarmManager: AlarmManager) {
        val allCourses = CourseRepository(context).getAllCourses()
        for (course in allCourses) {
            val id = course.id.hashCode()
            for ((requestCode, action) in listOf(
                RC_DND_START_BASE + id to ClassDndReceiver.ACTION_CLASS_START,
                RC_DND_END_BASE + id to ClassDndReceiver.ACTION_CLASS_END
            )) {
                // action 必须与 scheduleOne 一致，否则 PendingIntent 身份不符、cancel 静默失效
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

    private fun String.toMinutes(): Int? {
        val parts = this.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
}
