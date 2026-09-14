package com.haooz.chedule.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import com.haooz.chedule.R
import com.haooz.chedule.shizuku.ShizukuManager
import com.haooz.chedule.ui.activities.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

object IslandNotificationHelper {
    private const val TAG = "IslandNotificationHelper"
    private const val CHANNEL_ID = "course_reminder_island"
    private const val CHANNEL_NAME = "课程提醒超级岛"
    private const val KEY_ISLAND_EXPAND_GLOW_ENABLED = "island_expand_glow_enabled"
    // 官方必选：运营场景标识
    private const val BUSINESS_TAG = "course_reminder"
    // 官方 sequence：保证多次更新不乱序
    private val sequenceCounter = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() / 1000)

    // 课前倒计时与"已上课"必须共用，否则两态会同时停留在岛上
    const val ISLAND_NOTIFICATION_ID = 1003
    const val ISLAND_TEST_NOTIFICATION_ID = 5000
    // 到点后由精确闹钟自动收起，不依赖每分钟对账
    const val ISLAND_STARTED_VISIBLE_MS = 15_000L
    private const val ISLAND_DISMISS_RC_BASE = 72000
    // 历史版本遗留 ID：发送前清理，避免与当前岛重叠
    private val LEGACY_ISLAND_NOTIFICATION_IDS = intArrayOf(1001, 1002)

    private val scope = CoroutineScope(Dispatchers.IO)
    // 串行化 Shizuku bypass，避免并发导致 XMSF 网络状态错乱
    private val shizukuBypassMutex = Mutex()

    // 岛倒计时是系统原生 Chronometer，切换依赖 AlarmManager 精确闹钟（可能丢/滞后）。
    // 持久化应处状态，由每分钟刷新链对账兜底，避免卡在倒计时或迟迟不切换。
    object IslandState {
        // 测试/真实两套 prefs：否则测试写真实 state 会让 expand 闹钟被 stale 校验丢弃
        private const val PREF = "island_countdown_state"
        private const val PREF_TEST = "island_countdown_state_test"
        private const val K_ACTIVE = "active"
        private const val K_SWITCHED = "switched"
        private const val K_SWITCHED_AT = "switched_at"
        private const val K_NOTIFICATION_ID = "notification_id"
        private const val K_COURSE_NAME = "course_name"
        private const val K_CLASSROOM = "classroom"
        private const val K_SECTION = "section"
        private const val K_START_TIME = "start_time"
        private const val K_END_TIME = "end_time"
        private const val K_START_MILLIS = "start_millis"
        private const val K_END_MILLIS = "end_millis"

        data class Snapshot(
            val notificationId: Int,
            val courseName: String,
            val classroom: String,
            val section: String,
            val startTime: String,
            val endTime: String,
            val startMillis: Long,
            val endMillis: Long,
            val switched: Boolean,
            val switchedAt: Long
        )

        private fun prefName(testMode: Boolean) = if (testMode) PREF_TEST else PREF

        fun save(
            context: Context,
            notificationId: Int,
            courseName: String,
            classroom: String,
            section: String,
            startTime: String,
            endTime: String,
            startMillis: Long,
            endMillis: Long,
            testMode: Boolean = false
        ) {
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE).edit {
                putBoolean(K_ACTIVE, true)
                putBoolean(K_SWITCHED, false)
                remove(K_SWITCHED_AT)
                putInt(K_NOTIFICATION_ID, notificationId)
                putString(K_COURSE_NAME, courseName)
                putString(K_CLASSROOM, classroom)
                putString(K_SECTION, section)
                putString(K_START_TIME, startTime)
                putString(K_END_TIME, endTime)
                putLong(K_START_MILLIS, startMillis)
                putLong(K_END_MILLIS, endMillis)
            }
        }

        fun snapshot(context: Context, testMode: Boolean = false): Snapshot? {
            val p = context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE)
            if (!p.getBoolean(K_ACTIVE, false)) return null
            val startMillis = p.getLong(K_START_MILLIS, 0L)
            if (startMillis <= 0L) return null
            return Snapshot(
                notificationId = p.getInt(K_NOTIFICATION_ID, ISLAND_NOTIFICATION_ID),
                courseName = p.getString(K_COURSE_NAME, "") ?: "",
                classroom = p.getString(K_CLASSROOM, "") ?: "",
                section = p.getString(K_SECTION, "") ?: "",
                startTime = p.getString(K_START_TIME, "") ?: "",
                endTime = p.getString(K_END_TIME, "") ?: "",
                startMillis = startMillis,
                endMillis = p.getLong(K_END_MILLIS, 0L),
                switched = p.getBoolean(K_SWITCHED, false),
                switchedAt = p.getLong(K_SWITCHED_AT, 0L)
            )
        }

        // 按通知 ID 选 state，避免 receiver 用测试 state 校验真实闹钟
        fun snapshotFor(context: Context, notificationId: Int): Snapshot? =
            snapshot(context, testMode = notificationId == ISLAND_TEST_NOTIFICATION_ID)

        fun isActive(context: Context, testMode: Boolean = false): Boolean =
            snapshot(context, testMode) != null

        fun isSwitched(context: Context, testMode: Boolean = false): Boolean =
            snapshot(context, testMode)?.switched == true

        fun markSwitched(context: Context, testMode: Boolean = false) {
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE).edit {
                putBoolean(K_SWITCHED, true)
                putLong(K_SWITCHED_AT, System.currentTimeMillis())
            }
        }

        fun clear(context: Context, testMode: Boolean = false) {
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE).edit {
                clear()
            }
        }
    }

    // Mutex 串行化 disable→notify→enable；finally 保证 XMSF 网络一定恢复
    private suspend fun withShizukuBypass(
        context: Context,
        notificationId: Int,
        notification: Notification,
        useShizukuBypass: Boolean
    ) {
        if (!useShizukuBypass || !isShizukuAvailable()) {
            sendNotificationDirect(context, notificationId, notification)
            return
        }
        shizukuBypassMutex.withLock {
            val disabled = try {
                ShizukuManager.setXmsfNetworkingEnabled(context, false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disable XMSF networking", e)
                sendNotificationDirect(context, notificationId, notification)
                return@withLock
            }
            if (!disabled) {
                Log.w(TAG, "Failed to disable XMSF networking, sending notification anyway")
                sendNotificationDirect(context, notificationId, notification)
                return@withLock
            }
            try {
                Log.d(TAG, "XMSF networking disabled, sending notification")
                sendNotificationDirect(context, notificationId, notification)
                delay(100.milliseconds)
            } finally {
                try {
                    ShizukuManager.setXmsfNetworkingEnabled(context, true)
                    Log.d(TAG, "XMSF networking restored")
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL: Failed to restore XMSF networking!", e)
                }
            }
        }
    }

    private fun sendNotificationDirect(context: Context, notificationId: Int, notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    fun init(context: Context) {
        ShizukuManager.init(context)
    }

    fun isIslandSupported(context: Context): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod(
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(null, "persist.sys.feature.island", false) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return ShizukuManager.isShizukuRunning() && ShizukuManager.checkSelfPermission()
    }

    fun requestShizukuPermission(callback: (Boolean) -> Unit) {
        ShizukuManager.requestPermission(callback)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "课程提醒超级岛通知"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun cancelIslandNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ISLAND_NOTIFICATION_ID)
        for (id in LEGACY_ISLAND_NOTIFICATION_IDS) manager.cancel(id)
        manager.cancel(ISLAND_TEST_NOTIFICATION_ID)
    }

    // 只清理历史遗留 ID，避免与本次倒计时岛并存
    private fun cancelLegacyIslandNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (id in LEGACY_ISLAND_NOTIFICATION_IDS) manager.cancel(id)
    }

    // courseStartMillis 是课程开始时间的唯一真源，倒计时/文案/已上课态都由它派生
    private fun buildIslandParamsJson(
        context: Context,
        title: String,
        content: String,
        courseName: String? = null,
        section: String? = null,
        startTime: String? = null,
        endTime: String? = null,
        classroom: String? = null,
        courseStartMillis: Long? = null,
        testMode: Boolean = false
    ): String {
        val json = JSONObject()

        val prefs = context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
        val expandGlowEnabled = prefs.getBoolean(KEY_ISLAND_EXPAND_GLOW_ENABLED, true)
        val leftMode = prefs.getInt("island_left_mode", 0)
        val rightMode = prefs.getInt("island_right_mode", 1)

        val now = System.currentTimeMillis()
        // 仅开始时间在未来才算倒计时中；null 或已到点按"已上课"静态态
        val remainMs = courseStartMillis?.let { it - now }
        val counting = remainMs != null && remainMs > 0
        // 向上取整，保证"N分钟"与倒计时剩余秒数一致
        val minutesUntil = if (counting) ((remainMs!! + 59_999L) / 60_000L).toInt() else 0

        val islandLeftText = when (leftMode) {
            0 -> courseName ?: ""
            1 -> classroom ?: ""
            2 -> if (counting) "${minutesUntil}分钟" else "已上课"
            else -> courseName ?: ""
        }
        val islandRightText = when (rightMode) {
            0 -> courseName ?: ""
            1 -> classroom ?: ""
            2 -> if (counting) "${minutesUntil}分钟" else "已上课"
            else -> classroom ?: ""
        }

        val paramV2 = JSONObject().apply {
            put("business", BUSINESS_TAG)
            put("protocol", 1)
            put("enableFloat", true)
            put("updatable", true)
            put("outEffectSrc", if (expandGlowEnabled) "outer_glow" else "")
            // 同 id 通知重发时需允许再次显示（课前→已上课切换）
            put("reopen", "reopen")
            put("sequence", sequenceCounter.incrementAndGet())

            // 模板9：文本2 + 识别1 + 按钮2
            val baseInfo = JSONObject().apply {
                put("type", 2)
                put("title", courseName ?: title)
                val contentText = buildString {
                    if (!startTime.isNullOrEmpty()) append(startTime)
                    if (!endTime.isNullOrEmpty() && endTime != startTime) {
                        if (isNotEmpty()) append(" - ")
                        append(endTime)
                    }
                    if (!section.isNullOrEmpty()) {
                        if (isNotEmpty()) append("｜")
                        append(section)
                    }
                }
                put("content", contentText)
                put("subTitle", "")
                put("extraTitle", "")
                put("specialTitle", "")
                put("subContent", "")
                put("picFunction", "")
                put("showDivider", true)
                put("showContentDivider", false)
                put("colorTitle", "#111111")
                put("colorTitleDark", "#ffffff")
                put("colorContent", "#333333")
                put("colorContentDark", "#cccccc")
            }
            put("baseInfo", baseInfo)

            val picInfo = JSONObject().apply {
                put("type", 1)
                put("pic", "")
            }
            put("picInfo", picInfo)

            val hintInfo = JSONObject().apply {
                put("type", 2)
                put("content", if (counting) "即将上课" else "现在")
                put("title", if (counting) "" else "已上课")
                val timerInfo = JSONObject().apply {
                    if (counting) {
                        put("timerType", -1)
                        put("timerWhen", courseStartMillis!!)
                        put("timerTotal", 0L)
                        put("timerSystemCurrent", now)
                    } else {
                        put("timerType", 0)
                        put("timerWhen", 0)
                        put("timerTotal", 0)
                        put("timerSystemCurrent", 0)
                    }
                }
                put("timerInfo", timerInfo)
                put("subContent", "地点")
                put("subTitle", classroom ?: "")
                put("colorContent", "#666666")
                put("colorContentDark", "#aaaaaa")
                put("colorTitle", "#222222")
                put("colorTitleDark", "#eeeeee")
                put("colorSubContent", "#666666")
                put("colorSubContentDark", "#aaaaaa")
                put("colorSubTitle", "#222222")
                put("colorSubTitleDark", "#eeeeee")
                // 测试模式用立即开关验证按钮链路；正式则切换「上课自动开启勿扰」
                val actionInfo = JSONObject().apply {
                    put("actionTitle", "上课勿扰")
                    put("actionIntentType", 2)
                    val action = if (testMode) {
                        ClassDndReceiver.ACTION_TEST_TOGGLE
                    } else {
                        ClassDndReceiver.ACTION_TOGGLE
                    }
                    put(
                        "actionIntent",
                        "intent:#Intent;action=$action;" +
                            "component=${context.packageName}/.reminder.ClassDndReceiver;end"
                    )
                }
                put("actionInfo", actionInfo)
            }
            put("hintInfo", hintInfo)

            val paramIsland = JSONObject().apply {
                put("islandProperty", 1)
                put("islandTimeout", 3600)

                val bigIsland = JSONObject().apply {
                    put("templateNo", 2)

                    val imageTextInfoLeft = JSONObject().apply {
                        put("type", 1)
                        val textInfo = JSONObject().apply {
                            put("title", islandLeftText)
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        }
                        put("textInfo", textInfo)
                    }
                    put("imageTextInfoLeft", imageTextInfoLeft)

                    if (rightMode == 2 && counting) {
                        val sameWidthDigitInfo = JSONObject().apply {
                            put("content", "上课")
                            put("showHighlightColor", false)
                            val timerInfo = JSONObject().apply {
                                put("timerType", -1)
                                put("timerWhen", courseStartMillis!!)
                                put("timerTotal", 0L)
                                put("timerSystemCurrent", now)
                            }
                            put("timerInfo", timerInfo)
                        }
                        put("sameWidthDigitInfo", sameWidthDigitInfo)
                        put("textInfo", JSONObject().apply {
                            put("frontTitle", "")
                            put("title", "")
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    } else {
                        val textInfo = JSONObject().apply {
                            put("frontTitle", "")
                            put("title", if (counting) islandRightText else "已上课")
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        }
                        put("textInfo", textInfo)
                    }
                }
                put("bigIslandArea", bigIsland)

                val smallIslandArea = JSONObject().apply {
                    val picInfo = JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_small")
                        put("picDark", "miui.focus.pic_small_dark")
                    }
                    put("picInfo", picInfo)
                }
                put("smallIslandArea", smallIslandArea)
            }
            put("param_island", paramIsland)
        }

        json.put("param_v2", paramV2)
        return json.toString()
    }

    fun sendIslandNotification(
        context: Context,
        notificationId: Int,
        title: String,
        content: String,
        courseName: String? = null,
        section: String? = null,
        startTime: String? = null,
        endTime: String? = null,
        classroom: String? = null,
        courseStartMillis: Long? = null,
        testMode: Boolean = false,
        useShizukuBypass: Boolean = true
    ) {
        if (!isIslandSupported(context)) return

        ensureChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val islandParams = buildIslandParamsJson(
            context = context,
            title = title,
            content = content,
            courseName = courseName,
            section = section,
            startTime = startTime,
            endTime = endTime,
            classroom = classroom,
            courseStartMillis = courseStartMillis,
            testMode = testMode
        )

        val picsBundle = Bundle().apply {
            putParcelable("miui.focus.pic_app_icon", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_app_icon_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
        }
        builder.addExtras(Bundle().apply {
            putBundle("miui.focus.pics", picsBundle)
        })

        val notification = builder.build()
        notification.extras.putString("miui.focus.param", islandParams)

        scope.launch {
            withShizukuBypass(context, notificationId, notification, useShizukuBypass)
        }
    }

    // courseStartMillis 是倒计时/文案/"已上课"切换的统一时间戳；courseEndMillis=0 时按 15 秒兜底
    fun sendPreClassIslandNotification(
        context: Context,
        courseName: String,
        classroom: String,
        section: String,
        startTime: String,
        teacher: String,
        endTime: String? = null,
        courseStartMillis: Long,
        courseEndMillis: Long = 0L,
        notificationId: Int = ISLAND_NOTIFICATION_ID
    ) {
        val title = if (startTime.isNotEmpty()) "$courseName $startTime" else courseName
        val content = buildString {
            if (section.isNotEmpty()) append(section)
            if (classroom.isNotEmpty()) append("｜").append(classroom)
            if (teacher.isNotEmpty()) append("｜").append(teacher)
        }

        // 开始时间已过：不要画永远走不完的倒计时，直接落到"已上课"
        if (courseStartMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "sendPreClassIslandNotification: start time already passed ($startTime), fallback to started state")
            IslandState.save(
                context, notificationId, courseName, classroom, section,
                startTime, endTime ?: "", courseStartMillis, courseEndMillis
            )
            sendClassStartedNotification(
                context = context,
                courseName = courseName,
                classroom = classroom,
                section = section,
                startTime = startTime,
                endTime = endTime,
                notificationId = notificationId
            )
            return
        }

        cancelLegacyIslandNotifications(context)

        // 供每分钟对账使用（闹钟丢失时也能正确切换/收起）
        IslandState.save(
            context = context,
            notificationId = notificationId,
            courseName = courseName,
            classroom = classroom,
            section = section,
            startTime = startTime,
            endTime = endTime ?: "",
            startMillis = courseStartMillis,
            endMillis = courseEndMillis
        )
        // 立即切到每分钟刷新，否则对账形同虚设
        kickWidgetRefresh(context)

        sendIslandNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            content = content,
            courseName = courseName,
            section = section,
            startTime = startTime,
            endTime = endTime,
            classroom = classroom,
            courseStartMillis = courseStartMillis
        )
    }

    fun sendTestIslandNotification(context: Context) {
        if (!isIslandSupported(context)) {
            Log.w(TAG, "Island not supported on this device")
            return
        }

        ensureChannel(context)

        val courseName = "大学英语Ⅱ"
        val classroom = "A201"
        val section = "第3~4节"
        val testNotificationId = ISLAND_TEST_NOTIFICATION_ID

        // 时间串也由这两个时间戳派生，否则岛上显示与倒计时对不上，无法判断是否正确
        val courseStartTimestamp = System.currentTimeMillis() + 120_000L
        val courseEndTimestamp = courseStartTimestamp + 45 * 60_000L
        val startTime = formatClock(courseStartTimestamp)
        val endTime = formatClock(courseEndTimestamp)

        cancelLegacyIslandNotifications(context)
        // 测试岛写独立 PREF，不污染真实课前提醒 state
        IslandState.save(
            context = context,
            notificationId = testNotificationId,
            courseName = courseName,
            classroom = classroom,
            section = section,
            startTime = startTime,
            endTime = endTime,
            startMillis = courseStartTimestamp,
            endMillis = courseEndTimestamp,
            testMode = true
        )
        kickWidgetRefresh(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            testNotificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "$courseName $startTime"
        val content = "第3~4节｜A201"

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val islandParams = buildIslandParamsJson(
            context = context,
            title = title,
            content = content,
            courseName = courseName,
            section = section,
            startTime = startTime,
            endTime = endTime,
            classroom = classroom,
            courseStartMillis = courseStartTimestamp,
            testMode = true
        )

        val picsBundle = Bundle().apply {
            putParcelable("miui.focus.pic_app_icon", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_app_icon_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
        }
        builder.addExtras(Bundle().apply {
            putBundle("miui.focus.pics", picsBundle)
        })

        val notification = builder.build()
        notification.extras.putString("miui.focus.param", islandParams)

        // Shizuku 不可用时提示后直接发送（不走 bypass）
        if (!isShizukuAvailable()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Shizuku 未授权，超级岛通知可能无法正常显示", android.widget.Toast.LENGTH_LONG).show()
            }
            sendNotificationDirect(context, testNotificationId, notification)
        } else {
            scope.launch {
                withShizukuBypass(context, testNotificationId, notification, useShizukuBypass = true)
            }
        }

        // 精确闹钟在倒计时结束时切"已上课"：Handler 进程被杀即失效，只靠对账又会滞后近一分钟
        CourseReminderHelper.scheduleIslandExpandAlarm(
            context = context,
            alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager,
            courseName = courseName,
            classroom = classroom,
            section = section,
            startTime = startTime,
            endTime = endTime,
            courseStartMillis = courseStartTimestamp,
            notificationId = testNotificationId
        )
    }

    // 幂等：同一门课只切换一次，避免展开闹钟与每分钟对账重复弹出
    fun sendClassStartedNotification(
        context: Context,
        courseName: String,
        classroom: String,
        section: String,
        startTime: String,
        endTime: String? = null,
        notificationId: Int = ISLAND_NOTIFICATION_ID,
        testMode: Boolean = false
    ) {
        if (!isIslandSupported(context)) return

        // 以 notificationId 再校准一次 testMode
        val effectiveTestMode = testMode || notificationId == ISLAND_TEST_NOTIFICATION_ID

        if (IslandState.isSwitched(context, testMode = effectiveTestMode)) {
            Log.d(TAG, "Already switched to started state, skip duplicate update")
            return
        }
        val state = IslandState.snapshot(context, testMode = effectiveTestMode)

        // 兜底：同种 state 下用了另一个通知 ID 的倒计时岛，先收起否则会永远停在 00:00
        val staleId = state?.notificationId
        if (staleId != null && staleId != notificationId) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(staleId)
            Log.w(TAG, "Cancelled stale countdown island id=$staleId (current=$notificationId)")
        }

        val title = if (startTime.isNotEmpty()) "$courseName $startTime" else courseName
        val content = buildString {
            if (section.isNotEmpty()) append(section)
            if (classroom.isNotEmpty()) append("｜").append(classroom)
        }

        sendIslandNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            content = content,
            courseName = courseName,
            section = section,
            startTime = startTime,
            endTime = endTime,
            classroom = classroom,
            courseStartMillis = null, // null = 已上课静态态，同 ID 原地替换
            testMode = testMode,
            useShizukuBypass = true
        )

        IslandState.markSwitched(context, testMode = effectiveTestMode)
        scheduleIslandDismiss(context, notificationId, state?.startMillis ?: -1L)
    }

    private fun formatClock(millis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            java.util.Locale.ROOT,
            "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }

    // 把刷新链立刻切到每分钟，保证岛状态对账能及时跑起来
    private fun kickWidgetRefresh(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            CourseReminderHelper.scheduleNextWidgetRefresh(context, alarmManager)
        } catch (_: Exception) {
            Log.w(TAG, "Failed to kick widget refresh")
        }
    }

    // 精确闹钟立即收起；每分钟对账仅作兜底
    private fun scheduleIslandDismiss(context: Context, notificationId: Int, courseStartMillis: Long) {
        val intent = Intent(context, IslandDismissReceiver::class.java).apply {
            putExtra(IslandDismissReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(IslandDismissReceiver.EXTRA_COURSE_START_MILLIS, courseStartMillis)
        }
        // 自纪元起的分钟数 mod 100000：同分钟开始的课不会撞号
        val rc = ISLAND_DISMISS_RC_BASE +
            kotlin.math.abs((courseStartMillis / 60_000L % 100_000L).toInt())
        val pendingIntent = PendingIntent.getBroadcast(
            context, rc, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + ISLAND_STARTED_VISIBLE_MS,
                pendingIntent
            )
        } catch (_: SecurityException) {
            Log.w(TAG, "Cannot schedule island dismiss alarm")
        }
    }
}
