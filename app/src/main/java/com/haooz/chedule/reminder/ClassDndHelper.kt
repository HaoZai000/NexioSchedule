// 上课勿扰状态机：
// - 进入课堂时写入目标状态一次，写入前快照系统原状态
// - 课堂期间只观察：用户在课中手动改过就不再强行写回，也不销毁快照
// - 下课/离开课堂时校验：系统状态仍等于本应用写入的值才还原快照；否则视为用户已接管，保留现状
// - 用户在应用内关开关 / 切档位属于明确指令，无条件还原快照后再按新状态处理
// - 通知 / 超级岛按钮属于手动接管：课堂外点了立刻生效并保持，进入课堂后转交课堂生命周期
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
    // 用户从通知/超级岛按钮手动开启：不在课堂也保持，只有用户再点一次才还原
    private const val KEY_MANUAL = "dnd_manual_by_user"
    private const val KEY_MANUAL_AT = "dnd_manual_at"
    // 手动接管的最长保留时间：跨日或超时就无条件还原，避免手机被遗忘在勿扰里
    private const val MANUAL_HOLD_MAX_MS = 8 * 60 * 60 * 1000L

    // 与课程提醒闹钟的 10000 段错开
    private const val RC_DND_START_BASE = 30000
    private const val RC_DND_END_BASE = 40000
    // 测试课（测试超级岛 / 测试实时活动）专属，不占用课程 id 段
    private const val RC_DND_TEST_START = 39901
    private const val RC_DND_TEST_END = 49901

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
            remove(KEY_MANUAL)
            remove(KEY_MANUAL_AT)
        }
    }

    /** 用户手动接管中：不在课堂也不自动还原；跨日或超时则视为遗留状态，无条件还原 */
    private fun isManualHeld(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_MANUAL, false)) return false
        val at = p.getLong(KEY_MANUAL_AT, 0L)
        val expired = at <= 0L ||
            System.currentTimeMillis() - at > MANUAL_HOLD_MAX_MS ||
            !isSameDay(at, System.currentTimeMillis())
        if (!expired) return true
        Log.w(TAG, "Manual hold expired, restoring original state")
        handBack(context, requireConsistency = false)
        return false
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun markManual(context: Context, held: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_MANUAL, held)
            putLong(KEY_MANUAL_AT, System.currentTimeMillis())
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

    /**
     * 测试课（「测试小米超级岛」/「测试实时活动」）的课堂时间窗。
     * 测试课不在真实课表，以前 isInClass 恒 false，勿扰链路在测试里根本跑不到；
     * 现在把它并进来，测试也能验证「上课自动开启、下课自动关闭」。
     * 两条通道二选一：实时活动写 countdown_state，超级岛写 IslandState。
     */
    private fun testClassWindow(context: Context): Pair<Long, Long>? {
        val countdown = context.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
        if (countdown.getBoolean("active", false) && countdown.getBoolean("test_mode", false)) {
            val s = countdown.getLong("startMillis", 0L)
            val e = countdown.getLong("endMillis", 0L)
            if (s > 0L && e > s) return s to e
        }
        val island = IslandNotificationHelper.IslandState.snapshot(context, testMode = true)
        if (island != null && island.startMillis > 0L && island.endMillis > island.startMillis) {
            return island.startMillis to island.endMillis
        }
        return null
    }

    // 判断是否在 [start, end) 课堂时间
    fun isInClass(context: Context): Boolean {
        // 测试课按毫秒级窗口判断，真实课按分钟级节次判断
        testClassWindow(context)?.let { (start, end) ->
            val now = System.currentTimeMillis()
            if (now >= start && now < end) return true
        }
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
            // 用户从通知 / 超级岛按钮手动开启的：不在课堂也保持，等用户再点一次才还原
            if (isManualHeld(context)) return
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
        // 手动接管一旦进入课堂就转交课堂生命周期，下课才能自动还原
        if (p.getBoolean(KEY_MANUAL, false)) p.edit { remove(KEY_MANUAL) }
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

    /**
     * 通知 / 超级岛「上课勿扰」按钮：点一下立刻生效，再点一下立刻还原。
     * 旧实现只翻转「上课自动开启勿扰」开关，不在课堂时点了系统状态纹丝不动，
     * 表现就是「Toast 说已开启，实际没开启」——而按钮文案承诺的是立即勿扰。
     * 非课堂时段开启记为用户手动接管，applyCurrentState 不会自动把它还原掉。
     */
    fun toggleFromNotification(context: Context) {
        val repository = CourseRepository(context)
        if (!repository.getPreClassReminder() && !repository.getNextDayReminder()) {
            Toast.makeText(context, "请先在「课程提醒」中开启课程提醒", Toast.LENGTH_LONG).show()
            return
        }

        val next = !repository.getClassDndEnabled()
        repository.setClassDndEnabled(next)

        if (!next) {
            handBack(context, requireConsistency = false)
            Toast.makeText(context, "已关闭上课勿扰", Toast.LENGTH_SHORT).show()
            return
        }

        val mode = repository.getClassDndMode()
        if ((mode == MODE_DND || mode == MODE_PRIORITY) && !isDndPermissionGranted(context)) {
            // 回滚开关：否则它停在开启态却永远不生效
            repository.setClassDndEnabled(false)
            Toast.makeText(context, "请先在「课程提醒」中授予勿扰权限", Toast.LENGTH_LONG).show()
            return
        }

        val inClass = isInClass(context)
        // 已接管时先交还，避免上一次的快照被覆盖
        if (isDndAppliedByApp(context)) handBack(context, requireConsistency = false)
        if (!takeOver(context)) {
            repository.setClassDndEnabled(false)
            Toast.makeText(context, "开启失败，请检查勿扰权限", Toast.LENGTH_SHORT).show()
            return
        }
        // 课堂内交给课堂生命周期（下课自动恢复）；课堂外保持到用户再点一次
        markManual(context, !inClass)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleClassDndAlarms(context, alarmManager)

        val what = when (mode) {
            MODE_SILENT -> "静音"
            MODE_PRIORITY -> "勿扰模式"
            else -> "勿扰"
        }
        val tail = if (inClass) "，下课自动恢复" else "，再次点击可关闭"
        Toast.makeText(context, "已开启$what$tail", Toast.LENGTH_SHORT).show()
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

        // 测试课同样注册上课/下课闹钟：课前 70 秒、课中 120 秒的窗口
        // 靠每分钟对账粒度太粗，必须走精确闹钟才能准时开、准时关
        testClassWindow(context)?.let { (start, end) ->
            scheduleOne(context, alarmManager, RC_DND_TEST_START, ClassDndReceiver.ACTION_CLASS_START, start)
            scheduleOne(context, alarmManager, RC_DND_TEST_END, ClassDndReceiver.ACTION_CLASS_END, end)
        }
    }

    /** 测试课启动后同步勿扰闹钟，让「上课自动开启 / 下课自动关闭」在测试里也能验证 */
    fun syncTestClassDndAlarms(context: Context) {
        if (!CourseRepository(context).getClassDndEnabled()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleClassDndAlarms(context, alarmManager)
        applyCurrentState(context)
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
        // 上课/下课属于课表边界，走 setAlarmClock 保证 Doze 下也准点
        CourseReminderHelper.setCourseBoundaryAlarm(alarmManager, triggerAt, pendingIntent)
        Log.d(TAG, "scheduleOne OK action=$action at=" +
            java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerAt)) +
            " rc=$requestCode")
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
        cancelOne(context, alarmManager, RC_DND_TEST_START, ClassDndReceiver.ACTION_CLASS_START)
        cancelOne(context, alarmManager, RC_DND_TEST_END, ClassDndReceiver.ACTION_CLASS_END)
    }

    private fun cancelOne(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        action: String
    ) {
        val intent = Intent(context, ClassDndReceiver::class.java).apply { setAction(action) }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun String.toMinutes(): Int? {
        val parts = this.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
}
