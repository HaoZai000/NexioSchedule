// 上课勿扰状态机：
// - 进课堂快照系统原状态（铃声+勿扰两条通道），写入一次目标档位
// - 课堂期间只观察：用户改过就不再强行写回，也不销毁快照
// - 下课/离课堂时校验：状态仍是本应用写入值才还原快照，否则视为用户已接管
// - 两条通道互联动（静音⇄勿扰），快照与还原都要覆盖两条，只还原一条会把另一条留在勿扰态
// - 关开关/切档位属应用内明确指令，无条件还原；切档位沿用原快照不重取
// - 通知/超级岛按钮手动接管：课堂外保持到用户再点一次，进课堂转交课堂生命周期
package com.haooz.chedule.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.Toast
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.KeyValueStore
import com.haooz.chedule.data.util.DndSessionPolicy
import com.haooz.chedule.i18n.AndroidNotifStr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ClassDndHelper {

    private const val TAG = "ClassDndHelper"
    private const val PREFS_NAME = "course_reminder_prefs"

    // —— 会话状态 ——
    private const val KEY_APPLIED = "dnd_applied_by_app"
    private const val KEY_APPLIED_MODE = "dnd_applied_mode"
    // 接管前系统的原始值；只在接管那一刻记一次，用户课中改动不会污染快照
    private const val KEY_ORIGINAL_RINGER = "dnd_original_ringer_mode"
    private const val KEY_ORIGINAL_FILTER = "dnd_original_interruption_filter"
    // 接管后两条通道各自的实际呈现值，用来判断「有没有被用户动过」。
    // 缺这两个键说明是旧会话，按「次通道未观测」处理 —— 一律还原，否则泄漏没人收。
    private const val KEY_SET_RINGER = "dnd_set_ringer"
    private const val KEY_SET_FILTER = "dnd_set_filter"
    // 旧会话只记了主通道，仅用于兼容读取，新会话不再写
    private const val KEY_SET_STATE = "dnd_set_state"
    private const val KEY_APPLIED_AT = "dnd_applied_at"
    private const val KEY_CLASS_END_AT = "dnd_class_end_at"
    private const val KEY_TEMP = "dnd_temp_session"
    private const val KEY_SUPPRESS_UNTIL = "dnd_suppress_until"
    private const val KEY_MANUAL = "dnd_manual_by_user"
    private const val KEY_MANUAL_AT = "dnd_manual_at"
    private const val KEY_DEFER_AT = "dnd_defer_at"
    private const val KEY_WARN_SENT = "dnd_warn_sent"

    private const val MANUAL_HOLD_MAX_MS = 8 * 60 * 60 * 1000L
    private const val DEFER_ABANDON_MS = 12 * 60 * 60 * 1000L
    private const val OBSERVE_DELAY_MS = 1500L

    private const val RC_DND_START_BASE = 30000
    private const val RC_DND_END_BASE = 40000
    private const val RC_SLOT = 10000
    private const val RC_DND_TEST_START = 50001
    private const val RC_DND_TEST_END = 50002
    private const val RC_DND_SESSION_END = 50003
    // 闹钟登记表：课程删除/换课表后 id 变化，只按当前课程取消会留下孤儿闹钟
    private const val PREF_ALARM_REGISTRY = "reminder_alarm_registry"
    private const val KEY_DND_RCS = "class_dnd_rcs"

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    const val MODE_DND = 0
    const val MODE_SILENT = 1
    const val MODE_PRIORITY = 2

    /** 提醒设置的存储入口。 */
    private fun prefs(context: Context): KeyValueStore = AppStorage.store(PREFS_NAME)

    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun audioManager(context: Context) =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun currentMode(context: Context): Int = CourseRepository().getClassDndMode()

    fun isDndPermissionGranted(context: Context): Boolean {
        return try {
            notificationManager(context).isNotificationPolicyAccessGranted
        } catch (e: Exception) {
            NexioLog.e(TAG, "Failed to query notification policy access", e)
            false
        }
    }

    /**
     * 三个档位都要「免打扰访问权限」：DND/PRIORITY 走 setInterruptionFilter（M 起受限），
     * SILENT 走 setRingerMode，N 起同样被判定为「切换勿扰」而未授权直接抛 SecurityException。
     * 所需 API 均低于 minSdk 26，无需版本分支。
     */
    private fun canWriteState(context: Context): Boolean = isDndPermissionGranted(context)

    // 只看本应用是否登记接管，不受用户手动改系统的影响
    fun isDndAppliedByApp(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_APPLIED, false)
    }

    private fun isTempSession(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_TEMP, false) && p.getBoolean(KEY_APPLIED, false)
    }

    /** 目标档位对应的系统值 */
    private fun targetState(mode: Int): Int = when (mode) {
        MODE_SILENT -> AudioManager.RINGER_MODE_SILENT
        MODE_DND -> NotificationManager.INTERRUPTION_FILTER_NONE
        MODE_PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
        else -> AudioManager.RINGER_MODE_SILENT
    }

    // ————————————————————————— 读写两条系统通道 —————————————————————————

    /** 读取主通道（本档位直接写入的那一条）；读不到返回 null，调用方据此放弃干预而非误伤用户设置 */
    private fun readCurrentState(context: Context, mode: Int): Int? =
        if (mode == MODE_SILENT) readRingerMode(context) else readFilter(context)

    private fun readRingerMode(context: Context): Int? = try {
        audioManager(context).ringerMode
    } catch (e: Exception) {
        NexioLog.w(TAG, "Failed to read ringer mode", e)
        null
    }

    private fun readFilter(context: Context): Int? = try {
        notificationManager(context).currentInterruptionFilter
    } catch (e: Exception) {
        NexioLog.w(TAG, "Failed to read interruption filter", e)
        null
    }

    /** 接管后某条通道的实际呈现值；没观测过返回 null，该通道就不参与「用户有没有动过」的判定 */
    private fun appliedRinger(context: Context): Int? =
        prefs(context).takeIf { it.contains(KEY_SET_RINGER) }?.getInt(KEY_SET_RINGER, 0)

    private fun appliedFilter(context: Context): Int? =
        prefs(context).takeIf { it.contains(KEY_SET_FILTER) }?.getInt(KEY_SET_FILTER, 0)

    /** 主通道的期望值：优先用接管时的实测值，旧会话退回 KEY_SET_STATE，都没有则退回理论值 */
    private fun appliedState(context: Context, mode: Int): Int {
        val observed = if (mode == MODE_SILENT) appliedRinger(context) else appliedFilter(context)
        if (observed != null) return observed
        val p = prefs(context)
        return if (p.contains(KEY_SET_STATE)) p.getInt(KEY_SET_STATE, targetState(mode)) else targetState(mode)
    }

    /**
     * 次通道（被联动带起来的那一条）是否被用户动过。
     * SILENT 档写的是铃声、勿扰是次通道；DND/PRIORITY 反之。没观测过就不算动过。
     */
    private fun secondaryChannelTouched(context: Context, mode: Int): Boolean {
        val set = if (mode == MODE_SILENT) appliedFilter(context) else appliedRinger(context)
        val current = if (mode == MODE_SILENT) readFilter(context) else readRingerMode(context)
        return DndSessionPolicy.secondaryTouched(set, current)
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
            NexioLog.e(TAG, "Failed to write system state for mode=$mode", e)
            false
        }
    }

    // ————————————————————————— 快照与还原 —————————————————————————

    private fun commitSession(
        context: Context,
        mode: Int,
        windowEnd: Long,
        original: Pair<Int?, Int?>? = null
    ) {
        prefs(context).edit {
            putBoolean(KEY_APPLIED, true)
            putInt(KEY_APPLIED_MODE, mode)
            putLong(KEY_APPLIED_AT, System.currentTimeMillis())
            putLong(KEY_CLASS_END_AT, windowEnd)
            remove(KEY_SET_STATE)
            remove(KEY_SET_RINGER)
            remove(KEY_SET_FILTER)
            remove(KEY_TEMP)
            remove(KEY_MANUAL)
            remove(KEY_MANUAL_AT)
            remove(KEY_DEFER_AT)
            remove(KEY_SUPPRESS_UNTIL)
            if (original != null) {
                remove(KEY_ORIGINAL_RINGER)
                remove(KEY_ORIGINAL_FILTER)
                original.first?.let { putInt(KEY_ORIGINAL_RINGER, it) }
                original.second?.let { putInt(KEY_ORIGINAL_FILTER, it) }
            }
        }
    }

    /** 还原铃声模式到快照值；没有快照视为无需还原。从静音切回正常同样属于「切换勿扰」 */
    private fun restoreRinger(context: Context): Boolean {
        val p = prefs(context)
        if (!p.contains(KEY_ORIGINAL_RINGER)) return true
        if (!canWriteState(context)) {
            warnOnce(context, "restore_ringer", "Cannot restore ringer: notification policy access revoked")
            return false
        }
        val original = p.getInt(KEY_ORIGINAL_RINGER, AudioManager.RINGER_MODE_NORMAL)
        return try {
            audioManager(context).ringerMode = original
            true
        } catch (e: Exception) {
            NexioLog.e(TAG, "Failed to restore ringerMode to $original", e)
            false
        }
    }

    /** 还原勿扰过滤值到快照值；没有快照视为无需还原。还原快照而非粗暴 ALL，用户可能原本就开着 PRIORITY/ALARMS */
    private fun restoreFilter(context: Context): Boolean {
        val p = prefs(context)
        if (!p.contains(KEY_ORIGINAL_FILTER)) return true
        if (!canWriteState(context)) {
            warnOnce(context, "restore_filter", "Cannot restore filter: notification policy access revoked")
            return false
        }
        val original = p.getInt(KEY_ORIGINAL_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        return try {
            notificationManager(context).setInterruptionFilter(original)
            true
        } catch (e: Exception) {
            NexioLog.e(TAG, "Failed to restore filter to $original", e)
            false
        }
    }

    private fun observeChannels(context: Context) {
        val p = prefs(context)
        if (p.contains(KEY_SET_RINGER) && p.contains(KEY_SET_FILTER)) return
        if (System.currentTimeMillis() - p.getLong(KEY_APPLIED_AT, 0L) < OBSERVE_DELAY_MS) return
        val ringer = readRingerMode(context) ?: return
        val filter = readFilter(context) ?: return
        p.edit {
            putInt(KEY_SET_RINGER, ringer)
            putInt(KEY_SET_FILTER, filter)
        }
    }

    private fun clearSession(context: Context) {
        prefs(context).edit {
            putBoolean(KEY_APPLIED, false)
            remove(KEY_APPLIED_MODE)
            remove(KEY_APPLIED_AT)
            remove(KEY_CLASS_END_AT)
            remove(KEY_ORIGINAL_RINGER)
            remove(KEY_ORIGINAL_FILTER)
            remove(KEY_SET_STATE)
            remove(KEY_SET_RINGER)
            remove(KEY_SET_FILTER)
            remove(KEY_TEMP)
            remove(KEY_MANUAL)
            remove(KEY_MANUAL_AT)
            remove(KEY_DEFER_AT)
        }
    }

    private fun hasSessionData(p: KeyValueStore): Boolean =
        p.contains(KEY_APPLIED_MODE) || p.contains(KEY_APPLIED_AT) ||
            p.contains(KEY_CLASS_END_AT) || p.contains(KEY_ORIGINAL_RINGER) ||
            p.contains(KEY_ORIGINAL_FILTER) || p.contains(KEY_SET_STATE) ||
            p.contains(KEY_SET_RINGER) || p.contains(KEY_SET_FILTER) ||
            p.contains(KEY_TEMP) || p.contains(KEY_MANUAL) ||
            p.contains(KEY_MANUAL_AT) || p.contains(KEY_DEFER_AT)

    // ————————————————————————— 接管与交还 —————————————————————————

    private fun takeOver(context: Context): Boolean {
        val mode = currentMode(context)
        if (!canWriteState(context)) {
            NexioLog.d(TAG, "takeOver skipped: mode=$mode requires notification policy access")
            return false
        }
        val before = readCurrentState(context, mode)
        val original = readRingerMode(context) to readFilter(context)
        if (!writeState(context, mode)) return false

        val accepted = readCurrentState(context, mode) ?: targetState(mode)
        if (before != null && accepted == before && accepted != targetState(mode)) {
            NexioLog.d(TAG, "takeOver rejected by system: mode=$mode state=$accepted target=${targetState(mode)}")
            return false
        }

        commitSession(context, mode, classEndMillis(context), original)
        NexioLog.d(TAG, "takeOver mode=$mode state=$accepted")
        return true
    }

    /**
     * 交还系统状态。
     * @param requireConsistency true 用于「下课自动恢复」：系统状态仍等于本应用写入的值才还原快照，
     *                           用户在课中改过说明已自行接管，保持现状。
     *                           false 用于用户在应用内的明确指令（关开关 / 切档位），无条件还原。
     */
    private fun handBack(context: Context, requireConsistency: Boolean) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_APPLIED, false)) {
            // 没有会话就别每分钟提交一遍 SP
            if (hasSessionData(p)) clearSession(context)
            return
        }
        val mode = p.getInt(KEY_APPLIED_MODE, MODE_DND)

        // 两条通道都要还原：只还原本档位写的那一条，会把被联动带起来的另一条留在勿扰态。
        // 次通道若被用户在课中动过则不碰它，主通道是本应用的目标状态，必须还原。
        var restoreRinger = true
        var restoreFilter = true

        if (requireConsistency) {
            val current = readCurrentState(context, mode)
            if (current == null) {
                NexioLog.d(TAG, "handBack deferred: cannot read system state")
                if (shouldAbandonDeferred(context)) clearSession(context)
                return
            }
            // 基准放宽：读回值可能因 ROM 同步延迟失真，目标值同样算「没被用户动过」
            val expected = appliedState(context, mode)
            if (!DndSessionPolicy.ownsPrimaryChannel(current, expected, targetState(mode))) {
                NexioLog.d(TAG, "handBack skipped: user owns state now ($current != $expected)")
                clearSession(context)
                return
            }
            if (secondaryChannelTouched(context, mode)) {
                NexioLog.d(TAG, "handBack: secondary channel changed by user, leave it alone")
                if (mode == MODE_SILENT) restoreFilter = false else restoreRinger = false
            }
        }

        val hasSnapshot = p.contains(KEY_ORIGINAL_RINGER) || p.contains(KEY_ORIGINAL_FILTER)
        // 顺序固定「先勿扰后铃声」：关勿扰时系统会自己恢复铃声成它记的值，先写铃声会被随后的系统联动覆盖。
        val filterOk = !restoreFilter || restoreFilter(context)
        val ringerOk = !restoreRinger || restoreRinger(context)

        // 有快照却写不回去（多半权限被回收）：保留会话等权限恢复，别丢快照，否则手机再无自动退出勿扰机会；无快照则无可还原，正常收尾。
        if (hasSnapshot && !(filterOk && ringerOk)) {
            if (shouldAbandonDeferred(context)) {
                NexioLog.w(TAG, "handBack abandoned: giving up on restoring state after ${DEFER_ABANDON_MS / 3600_000L}h")
                clearSession(context)
                return
            }
            // 同上：权限被回收时每节课都命中，会把真正的异常刷没。
            warnOnce(context, "handback_deferred", "handBack deferred: keep session until state can be restored")
            return
        }
        NexioLog.d(TAG, "handBack restored original state mode=$mode")
        clearSession(context)
    }

    /** 还原推迟是否已超过上限；首次推迟时顺带记下起始时间 */
    private fun shouldAbandonDeferred(context: Context): Boolean {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val first = p.getLong(KEY_DEFER_AT, 0L)
        if (first <= 0L) {
            p.edit { putLong(KEY_DEFER_AT, now) }
            return false
        }
        return now - first > DEFER_ABANDON_MS || !isSameDay(first, now)
    }

    private fun handleManualHold(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_MANUAL, false)) return false
        val at = p.getLong(KEY_MANUAL_AT, 0L)
        val expired = at <= 0L ||
            System.currentTimeMillis() - at > MANUAL_HOLD_MAX_MS ||
            !isSameDay(at, System.currentTimeMillis())
        if (!expired) return true
        NexioLog.w(TAG, "Manual hold expired, restoring original state")
        p.edit {
            remove(KEY_MANUAL)
            remove(KEY_MANUAL_AT)
        }
        handBack(context, requireConsistency = false)
        return true
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

    /**
     * 同一原因的告警每天只发一条。
     * 权限被回收这类永久性条件每节课都会命中，全提到 w 会把真正的异常刷没；但整条链路全是 d 的话，
     * 报「勿扰关不掉」时日志里什么都查不到。每天一条 w 保证可自查，当天其余命中降回 d。
     */
    private fun warnOnce(context: Context, reason: String, message: String) {
        val p = prefs(context)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        val token = "$reason:$today"
        val sent = p.getStringSet(KEY_WARN_SENT, emptySet()).toMutableSet()
        if (token in sent) {
            NexioLog.d(TAG, message)
            return
        }
        sent.removeAll { !it.endsWith(":$today") }
        sent.add(token)
        p.edit { putStringSet(KEY_WARN_SENT, sent) }
        NexioLog.w(TAG, message)
    }

    // ————————————————————————— 对账入口 —————————————————————————

    // 受课程提醒总开关约束
    private fun isFeatureAvailable(context: Context): Boolean {
        val repository = CourseRepository()
        val masterEnabled = repository.getPreClassReminder() || repository.getNextDayReminder()
        return masterEnabled && repository.getClassDndEnabled()
    }

    /**
     * 测试课（「测试小米超级岛」/「测试实时活动」）的课堂时间窗。
     * 测试课不在真实课表里，并进来才能让勿扰链路在测试中跑通。
     * 两条通道二选一：实时活动写 countdown_state，超级岛写 IslandState。
     */
    private fun testClassWindow(context: Context): Pair<Long, Long>? {
        val countdown = AppStorage.store("countdown_state")
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

    private fun classEndMillis(context: Context): Long {
        val now = System.currentTimeMillis()
        val currentMinutes = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
        val repository = CourseRepository()
        val base = CourseReminderHelper.parseTimeToTodayMillis("00:00")
        var end = 0L
        for (course in CourseReminderHelper.getTodayCourses(context)) {
            val start = CourseReminderHelper.getCourseStartTime(course, repository)?.toMinutes() ?: continue
            val stop = CourseReminderHelper.getCourseEndTime(course, repository)?.toMinutes() ?: continue
            val offset = DndSessionPolicy.windowEndOffset(start, stop, currentMinutes) ?: continue
            val candidate = base + offset * 60_000L
            if (candidate > end) end = candidate
        }
        testClassWindow(context)?.let { (start, stop) ->
            if (now >= start && now < stop && stop > end) end = stop
        }
        return end
    }

    fun isInClass(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (classEndMillis(context) > now) return true
        return prefs(context).getLong(KEY_CLASS_END_AT, 0L) > now
    }

    fun applyCurrentState(context: Context) {
        val p = prefs(context)
        if (p.getLong(KEY_SUPPRESS_UNTIL, 0L) > System.currentTimeMillis()) return
        if (!isTempSession(context) && !isFeatureAvailable(context)) {
            handBack(context, requireConsistency = false)
            return
        }
        if (!isInClass(context)) {
            if (handleManualHold(context)) return
            handBack(context, requireConsistency = true)
            return
        }
        val mode = currentMode(context)
        if (!canWriteState(context)) {
            handBack(context, requireConsistency = false)
            return
        }

        if (p.getBoolean(KEY_MANUAL, false)) p.edit { remove(KEY_MANUAL) }
        if (!p.getBoolean(KEY_APPLIED, false)) {
            takeOver(context)
            return
        }

        val appliedMode = p.getInt(KEY_APPLIED_MODE, MODE_DND)
        if (appliedMode != mode) {
            val wasTemp = p.getBoolean(KEY_TEMP, false)
            if (appliedMode == MODE_SILENT) restoreRinger(context) else restoreFilter(context)
            if (writeState(context, mode)) {
                commitSession(context, mode, classEndMillis(context))
                if (wasTemp) prefs(context).edit { putBoolean(KEY_TEMP, true) }
                NexioLog.d(TAG, "mode switched $appliedMode -> $mode")
            }
            return
        }

        observeChannels(context)
        val expected = appliedState(context, mode)
        val current = readCurrentState(context, mode)
        if (current != null && current != expected) {
            NexioLog.d(TAG, "In class, user changed state ($current != $expected), leave it alone")
        }
    }

    fun toggleFromNotification(context: Context) {
        if (isDndAppliedByApp(context)) {
            handBack(context, requireConsistency = false)
            if (isDndAppliedByApp(context)) {
                Toast.makeText(context, AndroidNotifStr.dndNeedPermission(), Toast.LENGTH_LONG).show()
                return
            }
            prefs(context).edit { putLong(KEY_SUPPRESS_UNTIL, classEndMillis(context)) }
            Toast.makeText(context, AndroidNotifStr.dndDisabled(), Toast.LENGTH_SHORT).show()
            return
        }

        val repository = CourseRepository()
        if (!repository.getPreClassReminder() && !repository.getNextDayReminder()) {
            Toast.makeText(context, AndroidNotifStr.dndNeedReminderOn(), Toast.LENGTH_LONG).show()
            return
        }
        if (!canWriteState(context)) {
            Toast.makeText(context, AndroidNotifStr.dndNeedPermission(), Toast.LENGTH_LONG).show()
            return
        }
        val mode = repository.getClassDndMode()
        val inClass = isInClass(context)
        if (!takeOver(context)) {
            Toast.makeText(context, AndroidNotifStr.dndEnableFailed(), Toast.LENGTH_SHORT).show()
            return
        }
        prefs(context).edit { putBoolean(KEY_TEMP, true) }
        markManual(context, !inClass)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleClassDndAlarms(context, alarmManager)

        val what = when (mode) {
            MODE_SILENT -> AndroidNotifStr.dndModeSilent()
            MODE_PRIORITY -> AndroidNotifStr.dndModePriority()
            else -> AndroidNotifStr.dndModeNormal()
        }
        val tail = if (inClass) AndroidNotifStr.dndTailInClass() else AndroidNotifStr.dndTailManual()
        Toast.makeText(context, AndroidNotifStr.dndEnabledN(what, tail), Toast.LENGTH_SHORT).show()
    }

    // ————————————————————————— 闹钟排期 —————————————————————————

    // 与课前提醒闹钟独立：只要总开关开着就按课表生效
    fun scheduleClassDndAlarms(context: Context, alarmManager: AlarmManager) {
        cancelClassDndAlarms(context, alarmManager)
        val repository = CourseRepository()
        if (!repository.getClassDndEnabled() && !isTempSession(context)) {
            writeRcSet(context, emptySet())
            return
        }

        val rcs = mutableSetOf<Int>()
        for (course in CourseReminderHelper.getTodayCourses(context)) {
            // 与岛/课前提醒共用时间戳，避免两边上课时刻错开
            val startMillis = CourseReminderHelper.parseTimeToTodayMillis(
                CourseReminderHelper.getCourseStartTime(course, repository)
            )
            val parsedEnd = CourseReminderHelper.parseTimeToTodayMillis(
                CourseReminderHelper.getCourseEndTime(course, repository)
            )
            if (startMillis <= 0L || parsedEnd <= 0L) continue
            // 跨零点的课（如 23:00-01:00）下课点落在次日
            val endMillis = if (parsedEnd <= startMillis) parsedEnd + DAY_MS else parsedEnd

            val startRc = courseRc(RC_DND_START_BASE, course.id)
            val endRc = courseRc(RC_DND_END_BASE, course.id)
            scheduleOne(context, alarmManager, startRc, ClassDndReceiver.ACTION_CLASS_START, startMillis)
            scheduleOne(context, alarmManager, endRc, ClassDndReceiver.ACTION_CLASS_END, endMillis)
            rcs += startRc
            rcs += endRc
        }

        testClassWindow(context)?.let { (start, end) ->
            scheduleOne(context, alarmManager, RC_DND_TEST_START, ClassDndReceiver.ACTION_CLASS_START, start)
            scheduleOne(context, alarmManager, RC_DND_TEST_END, ClassDndReceiver.ACTION_CLASS_END, end)
            rcs += RC_DND_TEST_START
            rcs += RC_DND_TEST_END
        }

        val heldEnd = prefs(context).getLong(KEY_CLASS_END_AT, 0L)
        if (heldEnd > System.currentTimeMillis()) {
            scheduleOne(context, alarmManager, RC_DND_SESSION_END, ClassDndReceiver.ACTION_CLASS_END, heldEnd)
            rcs += RC_DND_SESSION_END
        }
        writeRcSet(context, rcs)
    }

    /** 测试课启动后同步勿扰闹钟，让「上课自动开启 / 下课自动关闭」在测试里也能验证 */
    fun syncTestClassDndAlarms(context: Context) {
        if (!CourseRepository().getClassDndEnabled()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleClassDndAlarms(context, alarmManager)
        applyCurrentState(context)
    }

    fun reconcileAfterSystemEvent(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_APPLIED, false)) return
        val mode = p.getInt(KEY_APPLIED_MODE, MODE_DND)
        val current = readCurrentState(context, mode) ?: return
        if (current == appliedState(context, mode) || current == targetState(mode)) return
        NexioLog.w(TAG, "System state changed outside class session, restoring snapshot")
        handBack(context, requireConsistency = false)
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

        // 上课/下课属于课表边界，走 setAlarmClock 保证 Doze 下也准点
        CourseReminderHelper.setCourseBoundaryAlarm(
            alarmManager, triggerAt, dndPendingIntent(context, requestCode, action)
        )
        NexioLog.d(TAG, "scheduleOne OK action=$action at=" +
            SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(triggerAt)) +
            " rc=$requestCode")
    }

    /**
     * 取消全部上课勿扰闹钟。
     * 先按登记表取消（覆盖已删除 / 已换 id 的课程），再按当前课程兜底，最后清登记表；
     * 只按当前课程取消会留下孤儿闹钟。
     */
    fun cancelClassDndAlarms(context: Context, alarmManager: AlarmManager) {
        for (rc in readRcSet(context)) {
            cancelOne(context, alarmManager, rc, ClassDndReceiver.ACTION_CLASS_START)
            cancelOne(context, alarmManager, rc, ClassDndReceiver.ACTION_CLASS_END)
        }
        writeRcSet(context, emptySet())

        for (course in CourseRepository().getAllCourses()) {
            cancelOne(context, alarmManager, courseRc(RC_DND_START_BASE, course.id), ClassDndReceiver.ACTION_CLASS_START)
            cancelOne(context, alarmManager, courseRc(RC_DND_END_BASE, course.id), ClassDndReceiver.ACTION_CLASS_END)
        }
        cancelOne(context, alarmManager, RC_DND_TEST_START, ClassDndReceiver.ACTION_CLASS_START)
        cancelOne(context, alarmManager, RC_DND_TEST_END, ClassDndReceiver.ACTION_CLASS_END)
        cancelOne(context, alarmManager, RC_DND_SESSION_END, ClassDndReceiver.ACTION_CLASS_END)
    }

    private fun courseRc(base: Int, id: String): Int =
        base + ((id.hashCode() and 0x7fffffff) % RC_SLOT)

    private fun cancelOne(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        action: String
    ) {
        alarmManager.cancel(dndPendingIntent(context, requestCode, action))
    }

    private fun dndPendingIntent(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ClassDndReceiver::class.java).apply { setAction(action) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun readRcSet(context: Context): Set<Int> =
        AppStorage.store(PREF_ALARM_REGISTRY)
            .getStringSet(KEY_DND_RCS, emptySet())
            .mapNotNull { it.toIntOrNull() }.toSet()

    private fun writeRcSet(context: Context, rcs: Set<Int>) {
        AppStorage.store(PREF_ALARM_REGISTRY).edit {
            putStringSet(KEY_DND_RCS, rcs.map { it.toString() }.toSet())
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
