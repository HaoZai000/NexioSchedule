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
import kotlinx.coroutines.Job
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
    // 课中提醒：到点后「已上课」替换为模板6进度卡片
    const val KEY_IN_CLASS_REMINDER = "island_in_class_enabled"
    // 官方必选：运营场景标识
    private const val BUSINESS_TAG = "course_reminder"
    // 官方 sequence：保证多次更新不乱序
    private val sequenceCounter = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() / 1000)

    // 课前倒计时岛
    const val ISLAND_NOTIFICATION_ID = 1003
    const val ISLAND_TEST_NOTIFICATION_ID = 5000
    // 「已上课」独立 ID：同 ID 更新被岛框架视为静默替换，Chronometer 会卡 00:00
    const val ISLAND_STARTED_NOTIFICATION_ID = 1004
    const val ISLAND_STARTED_TEST_NOTIFICATION_ID = 5001
    // 课中提醒模板6 独立 ID：与「已上课」两条路径完全分开，不在同一条通知里 if/else
    const val ISLAND_IN_CLASS_NOTIFICATION_ID = 1005
    const val ISLAND_IN_CLASS_TEST_NOTIFICATION_ID = 5002
    // 到点后由精确闹钟自动收起，不依赖每分钟对账
    const val ISLAND_STARTED_VISIBLE_MS = 15_000L
    private const val ISLAND_DISMISS_RC_BASE = 72000
    // 历史版本遗留 ID：发送前清理，避免与当前岛重叠
    private val LEGACY_ISLAND_NOTIFICATION_IDS = intArrayOf(1001, 1002)

    private val scope = CoroutineScope(Dispatchers.IO)
    // 串行化 Shizuku bypass，避免并发导致 XMSF 网络状态错乱
    private val shizukuBypassMutex = Mutex()
    // 课中倒计时由系统 Chronometer 自刷；ticker 已退役，仅保留 stop 以兼容清理路径
    private var inClassTickJob: Job? = null
    private val inClassTickLock = Any()

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
            snapshot(context, testMode = isIslandTestId(notificationId))

        // 倒计时态 -> 已上课态后需改写 ID，否则后续对账/dismiss 会去 cancel 一个已不存在的通知
        fun updateNotificationId(context: Context, notificationId: Int, testMode: Boolean = false) {
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE).edit {
                putInt(K_NOTIFICATION_ID, notificationId)
            }
        }

        fun isActive(context: Context, testMode: Boolean = false): Boolean =
            snapshot(context, testMode) != null

        // 刷新链/对账需同时看到测试岛与真实岛，否则测试课中进度永远不更新
        fun isActiveAny(context: Context): Boolean =
            isActive(context, testMode = false) || isActive(context, testMode = true)

        fun isSwitched(context: Context, testMode: Boolean = false): Boolean =
            snapshot(context, testMode)?.switched == true

        fun markSwitched(context: Context, testMode: Boolean = false) {
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE).edit {
                putBoolean(K_SWITCHED, true)
                putLong(K_SWITCHED_AT, System.currentTimeMillis())
            }
        }

        // 课中提醒：记录上次下发的剩余分钟/进度，避免每分钟无意义重发
        private const val K_LAST_REMAINING = "last_remaining_minutes"
        private const val K_LAST_PROGRESS = "last_progress"

        fun lastRemainingMinutes(context: Context, testMode: Boolean = false): Int =
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE)
                .getInt(K_LAST_REMAINING, Int.MIN_VALUE)

        fun saveLastRemainingMinutes(context: Context, minutes: Int, testMode: Boolean = false) {
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE).edit {
                putInt(K_LAST_REMAINING, minutes)
            }
        }

        fun lastProgress(context: Context, testMode: Boolean = false): Int =
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE)
                .getInt(K_LAST_PROGRESS, Int.MIN_VALUE)

        fun saveLastProgress(context: Context, progress: Int, testMode: Boolean = false) {
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE).edit {
                putInt(K_LAST_PROGRESS, progress)
            }
        }

        fun clearLastRemainingMinutes(context: Context, testMode: Boolean = false) {
            context.getSharedPreferences(prefName(testMode), Context.MODE_PRIVATE).edit {
                remove(K_LAST_REMAINING)
                remove(K_LAST_PROGRESS)
            }
        }

        fun clear(context: Context, testMode: Boolean = false) {
            stopInClassTicker()
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

    fun isInClassReminderEnabled(context: Context): Boolean {
        // 与原生实况共用 CourseReminderHelper 的统一课中开关
        return CourseReminderHelper.isInClassEnabled(context)
    }

    fun isInClassNotificationId(notificationId: Int): Boolean =
        notificationId == ISLAND_IN_CLASS_NOTIFICATION_ID ||
            notificationId == ISLAND_IN_CLASS_TEST_NOTIFICATION_ID

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
            // 与实况频道同理：不放行的话开「上课勿扰」后超级岛会被一并屏蔽
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    // 测试岛含倒计时/已上课/课中三个 ID
    fun isIslandTestId(notificationId: Int): Boolean =
        notificationId == ISLAND_TEST_NOTIFICATION_ID ||
            notificationId == ISLAND_STARTED_TEST_NOTIFICATION_ID ||
            notificationId == ISLAND_IN_CLASS_TEST_NOTIFICATION_ID

    // 到点后：未开课中提醒 → 已上课 ID；开了 → 课中 ID
    fun activeIdFor(notificationId: Int, inClass: Boolean): Int = when {
        isIslandTestId(notificationId) ->
            if (inClass) ISLAND_IN_CLASS_TEST_NOTIFICATION_ID else ISLAND_STARTED_TEST_NOTIFICATION_ID
        inClass -> ISLAND_IN_CLASS_NOTIFICATION_ID
        else -> ISLAND_STARTED_NOTIFICATION_ID
    }

    // 倒计时态使用的 ID（切换前用它收起可能卡在 00:00 的倒计时岛）
    fun countdownIdFor(notificationId: Int): Int = when (notificationId) {
        ISLAND_STARTED_TEST_NOTIFICATION_ID, ISLAND_IN_CLASS_TEST_NOTIFICATION_ID -> ISLAND_TEST_NOTIFICATION_ID
        ISLAND_STARTED_NOTIFICATION_ID, ISLAND_IN_CLASS_NOTIFICATION_ID -> ISLAND_NOTIFICATION_ID
        else -> notificationId
    }

    // 同一门课可能并存的岛态 ID
    fun siblingIdsFor(notificationId: Int): IntArray = when (notificationId) {
        ISLAND_NOTIFICATION_ID -> intArrayOf(ISLAND_STARTED_NOTIFICATION_ID, ISLAND_IN_CLASS_NOTIFICATION_ID)
        ISLAND_STARTED_NOTIFICATION_ID -> intArrayOf(ISLAND_NOTIFICATION_ID, ISLAND_IN_CLASS_NOTIFICATION_ID)
        ISLAND_IN_CLASS_NOTIFICATION_ID -> intArrayOf(ISLAND_NOTIFICATION_ID, ISLAND_STARTED_NOTIFICATION_ID)
        ISLAND_TEST_NOTIFICATION_ID -> intArrayOf(ISLAND_STARTED_TEST_NOTIFICATION_ID, ISLAND_IN_CLASS_TEST_NOTIFICATION_ID)
        ISLAND_STARTED_TEST_NOTIFICATION_ID -> intArrayOf(ISLAND_TEST_NOTIFICATION_ID, ISLAND_IN_CLASS_TEST_NOTIFICATION_ID)
        ISLAND_IN_CLASS_TEST_NOTIFICATION_ID -> intArrayOf(ISLAND_TEST_NOTIFICATION_ID, ISLAND_STARTED_TEST_NOTIFICATION_ID)
        else -> intArrayOf()
    }

    // 收起指定岛态，并连带收起同课其它态，避免残留
    fun cancelIslandState(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
        for (id in siblingIdsFor(notificationId)) manager.cancel(id)
    }

    fun cancelIslandNotifications(context: Context) {
        stopInClassTicker()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ISLAND_NOTIFICATION_ID)
        manager.cancel(ISLAND_STARTED_NOTIFICATION_ID)
        manager.cancel(ISLAND_IN_CLASS_NOTIFICATION_ID)
        for (id in LEGACY_ISLAND_NOTIFICATION_IDS) manager.cancel(id)
        manager.cancel(ISLAND_TEST_NOTIFICATION_ID)
        manager.cancel(ISLAND_STARTED_TEST_NOTIFICATION_ID)
        manager.cancel(ISLAND_IN_CLASS_TEST_NOTIFICATION_ID)
    }

    // 只清理历史遗留 ID，避免与本次倒计时岛并存
    private fun cancelLegacyIslandNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (id in LEGACY_ISLAND_NOTIFICATION_IDS) manager.cancel(id)
    }

    // courseStartMillis 是课程开始时间的唯一真源，倒计时/文案/已上课态都由它派生
    // courseEndMillis 用于课中提醒：给出剩余时间与进度；null 或已过下课点则回退静态「已上课」
    // 仅用于：课前倒计时 / 静态「已上课」。课中模板6 走 buildInClassIslandParamsJson
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
        val aodMode = prefs.getInt("island_aod_mode", 0)

        val now = System.currentTimeMillis()
        val remainMs = courseStartMillis?.let { it - now }
        val counting = remainMs != null && remainMs > 0
        val minutesUntil = if (counting) ((remainMs + 59_999L) / 60_000L).toInt() else 0

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
        val aodTitle = if (aodMode == 1) classroom ?: courseName ?: title else courseName ?: title

        val paramV2 = JSONObject().apply {
            put("business", BUSINESS_TAG)
            put("protocol", 1)
            if (counting) {
                put("enableFloat", true)
            } else {
                // 已上课：不弹悬浮窗，只走岛
                put("islandFirstFloat", true)
                put("enableFloat", false)
            }
            put("updatable", true)
            put("outEffectSrc", if (expandGlowEnabled) "outer_glow" else "")
            put("reopen", "reopen")
            put("sequence", sequenceCounter.incrementAndGet())
            put("aodTitle", aodTitle)

            val baseInfo = JSONObject().apply {
                put("type", 2)
                put("title", courseName ?: title)
                put("content", content)
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

            put("picInfo", JSONObject().apply {
                put("type", 1)
                put("pic", "")
            })

            val hintInfo = JSONObject().apply {
                put("type", 2)
                if (counting) {
                    put("content", "即将上课")
                    put("title", "")
                } else {
                    put("content", "现在")
                    put("title", "已上课")
                }
                val timerInfo = JSONObject().apply {
                    if (counting) {
                        put("timerType", -1)
                        put("timerWhen", courseStartMillis)
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
                        put("textInfo", JSONObject().apply {
                            put("title", islandLeftText)
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    }
                    put("imageTextInfoLeft", imageTextInfoLeft)

                    if (rightMode == 2 && counting) {
                        val sameWidthDigitInfo = JSONObject().apply {
                            put("content", "上课")
                            put("showHighlightColor", false)
                            put("timerInfo", JSONObject().apply {
                                put("timerType", -1)
                                put("timerWhen", courseStartMillis)
                                put("timerTotal", 0L)
                                put("timerSystemCurrent", now)
                            })
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
                        put("textInfo", JSONObject().apply {
                            put("frontTitle", "")
                            put("title", if (counting) islandRightText else "已上课")
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    }
                }
                put("bigIslandArea", bigIsland)

                put("smallIslandArea", JSONObject().apply {
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_small")
                        put("picDark", "miui.focus.pic_small_dark")
                    })
                })
            }
            put("param_island", paramIsland)
        }

        json.put("param_v2", paramV2)
        return json.toString()
    }

    // 课中提醒：模板9（文本组件2 + 识别图形组件1 + 按钮组件2）
    private fun buildInClassIslandParamsJson(
        context: Context,
        courseName: String,
        classroom: String,
        section: String,
        startTime: String,
        endTime: String,
        courseEndMillis: Long
    ): String {
        val json = JSONObject()
        val prefs = context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
        val expandGlowEnabled = prefs.getBoolean(KEY_ISLAND_EXPAND_GLOW_ENABLED, true)
        val aodMode = prefs.getInt("island_aod_mode", 0)
        // 课中岛缩略态 B 区：0=正在上课（静态文案），1=距下课倒计时
        val inClassRightMode = prefs.getInt("island_in_class_right_mode", 0)

        val now = System.currentTimeMillis()
        val counting = courseEndMillis > now
        val aodTitle = if (aodMode == 1) classroom.ifEmpty { courseName } else courseName
        val baseContent = buildString {
            if (startTime.isNotEmpty()) append(startTime)
            if (endTime.isNotEmpty() && endTime != startTime) {
                if (isNotEmpty()) append(" - ")
                append(endTime)
            }
            if (section.isNotEmpty()) {
                if (isNotEmpty()) append("｜")
                append(section)
            }
        }

        val paramV2 = JSONObject().apply {
            put("business", BUSINESS_TAG)
            put("protocol", 1)
            // 课中岛挂到下课，不反复弹悬浮窗（与课前「已上课」同策略）
            put("islandFirstFloat", true)
            put("enableFloat", false)
            put("updatable", true)
            put("outEffectSrc", if (expandGlowEnabled) "outer_glow" else "")
            put("reopen", "reopen")
            put("sequence", sequenceCounter.incrementAndGet())
            put("aodTitle", aodTitle)

            // 文本组件2
            put("baseInfo", JSONObject().apply {
                put("type", 2)
                put("title", courseName)
                put("content", baseContent)
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
            })

            // 识别图形组件1
            put("picInfo", JSONObject().apply {
                put("type", 1)
                put("pic", "")
            })

            // 按钮组件2：距下课倒计时
            put("hintInfo", JSONObject().apply {
                put("type", 2)
                put("content", if (counting) "距离下课" else "已下课")
                put("title", "")
                put("timerInfo", JSONObject().apply {
                    if (counting) {
                        put("timerType", -1)
                        put("timerWhen", courseEndMillis)
                        put("timerTotal", 0L)
                        put("timerSystemCurrent", now)
                    } else {
                        put("timerType", 0)
                        put("timerWhen", 0)
                        put("timerTotal", 0)
                        put("timerSystemCurrent", 0)
                    }
                })
                put("subContent", "地点")
                put("subTitle", classroom)
                put("colorContent", "#666666")
                put("colorContentDark", "#aaaaaa")
                put("colorTitle", "#222222")
                put("colorTitleDark", "#eeeeee")
                put("colorSubContent", "#666666")
                put("colorSubContentDark", "#aaaaaa")
                put("colorSubTitle", "#222222")
                put("colorSubTitleDark", "#eeeeee")
                put("actionInfo", JSONObject().apply {
                    put("actionTitle", "查看课表")
                    // 1=启动 Activity；与通知体点击一致，打开课表主页
                    put("actionIntentType", 1)
                    put(
                        "actionIntent",
                        "intent:#Intent;component=${context.packageName}/.ui.activities.MainActivity;" +
                            "launchFlags=0x14000000;end"
                    )
                })
            })

            // 缩略态：模板2，与课前同构；A区课程名，B区可在「正在上课」与距下课倒计时间自选
            put("param_island", JSONObject().apply {
                put("islandProperty", 1)
                put("islandTimeout", 3600)
                put("bigIslandArea", JSONObject().apply {
                    put("templateNo", 2)
                    // A区：课程名称
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("textInfo", JSONObject().apply {
                            put("title", courseName)
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    })
                    if (inClassRightMode == 1 && counting) {
                        // B区：距下课倒计时。与课前「N分钟上课」同一套 sameWidthDigitInfo 机制，
                        // 由系统 timer 自刷到下课，App 无需按分钟重推，也不会让岛反复弹出
                        put("sameWidthDigitInfo", JSONObject().apply {
                            put("content", "下课")
                            put("showHighlightColor", false)
                            put("timerInfo", JSONObject().apply {
                                put("timerType", -1)
                                put("timerWhen", courseEndMillis)
                                put("timerTotal", 0L)
                                put("timerSystemCurrent", now)
                            })
                        })
                        put("textInfo", JSONObject().apply {
                            put("frontTitle", "")
                            put("title", "")
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    } else {
                        // B区：静态文案
                        put("textInfo", JSONObject().apply {
                            put("frontTitle", "")
                            put("title", if (counting) "正在上课" else "已下课")
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    }
                })
                put("smallIslandArea", JSONObject().apply {
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_small")
                        put("picDark", "miui.focus.pic_small_dark")
                    })
                })
            })
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
        useShizukuBypass: Boolean = true,
        islandParamsOverride: String? = null
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

        val islandParams = islandParamsOverride ?: buildIslandParamsJson(
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

        // 开始时间已过：不要画永远走不完的倒计时，直接走分流入口
        if (courseStartMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "sendPreClassIslandNotification: start time already passed ($startTime), fallback to class start")
            IslandState.save(
                context, notificationId, courseName, classroom, section,
                startTime, endTime ?: "", courseStartMillis, courseEndMillis
            )
            onClassStart(
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
        // 上一节残留的「已上课 / 课中」岛先收起，避免与本次倒计时岛并存
        cancelIslandState(context, ISLAND_STARTED_NOTIFICATION_ID)
        cancelIslandState(context, ISLAND_IN_CLASS_NOTIFICATION_ID)

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
        val classroom = "博A201"
        val section = "第3~4节"
        val testNotificationId = ISLAND_TEST_NOTIFICATION_ID

        // 时间串也由这两个时间戳派生，否则岛上显示与倒计时对不上，无法判断是否正确
        // 测试课：课前 1 分 10 秒倒计时 + 课中 2 分钟
        val courseStartTimestamp = System.currentTimeMillis() + 70_000L
        val courseEndTimestamp = courseStartTimestamp + 120_000L
        val startTime = formatClock(courseStartTimestamp)
        val endTime = formatClock(courseEndTimestamp)

        cancelLegacyIslandNotifications(context)
        // 清掉上一轮测试的「已上课 / 课中」岛，避免与本次倒计时岛并存
        cancelIslandState(context, ISLAND_STARTED_TEST_NOTIFICATION_ID)
        cancelIslandState(context, ISLAND_IN_CLASS_TEST_NOTIFICATION_ID)
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
        val content = "第3~4节｜博A201"

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
        // 测试岛也要进刷新链，否则课中 ticker/对账在进程被杀后无人驱动
        kickWidgetRefresh(context)
    }

    // 到点分流：开了课中提醒 → 只进课中卡，无「已上课」
    // 未开课中提醒 → 静态「已上课」15 秒
    fun onClassStart(
        context: Context,
        courseName: String,
        classroom: String,
        section: String,
        startTime: String,
        endTime: String? = null,
        notificationId: Int = ISLAND_NOTIFICATION_ID,
        testMode: Boolean = false
    ) {
        val effectiveTestMode = testMode || isIslandTestId(notificationId)
        val state = IslandState.snapshot(context, effectiveTestMode)
        val startMillis = state?.startMillis ?: System.currentTimeMillis()
        val endMillis = state?.endMillis ?: 0L
        val inClassOn = CourseReminderHelper.isInClassEnabled(context)

        if (inClassOn) {
            val showNow = endMillis > startMillis &&
                CourseReminderHelper.shouldShowInClassNow(context, startMillis, endMillis)
            if (showNow || endMillis > startMillis) {
                // 课中开启：一律走课中卡（含距下课未进窗时也先挂上，倒计时由系统自刷）
                sendInClassIslandNotification(
                    context = context,
                    courseName = courseName,
                    classroom = classroom,
                    section = section,
                    startTime = startTime,
                    endTime = endTime ?: "",
                    notificationId = notificationId,
                    testMode = effectiveTestMode
                )
            } else {
                // 无有效下课时间：只收倒计时，不发已上课
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(countdownIdFor(notificationId))
                IslandState.markSwitched(context, testMode = effectiveTestMode)
            }
            return
        }

        sendClassStartedNotification(
            context = context,
            courseName = courseName,
            classroom = classroom,
            section = section,
            startTime = startTime,
            endTime = endTime,
            notificationId = notificationId,
            testMode = effectiveTestMode
        )
    }

    // 静态「已上课」：独立 ID，15 秒后收起。不含任何课中逻辑
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

        val effectiveTestMode = testMode || isIslandTestId(notificationId)
        val startedId = activeIdFor(notificationId, inClass = false)
        val countdownId = countdownIdFor(notificationId)

        if (IslandState.isSwitched(context, testMode = effectiveTestMode) &&
            IslandState.snapshot(context, effectiveTestMode)?.notificationId == startedId
        ) {
            Log.d(TAG, "Already switched to started state, skip duplicate update")
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(countdownId)
        // 收起可能残留的课中岛
        manager.cancel(activeIdFor(notificationId, inClass = true))

        val title = if (startTime.isNotEmpty()) "$courseName $startTime" else courseName
        val content = buildString {
            if (section.isNotEmpty()) append(section)
            if (classroom.isNotEmpty()) append("｜").append(classroom)
        }

        sendIslandNotification(
            context = context,
            notificationId = startedId,
            title = title,
            content = content,
            courseName = courseName,
            section = section,
            startTime = startTime,
            endTime = endTime,
            classroom = classroom,
            courseStartMillis = null,
            testMode = effectiveTestMode
        )

        val startMillis = IslandState.snapshot(context, effectiveTestMode)?.startMillis ?: -1L
        IslandState.updateNotificationId(context, startedId, testMode = effectiveTestMode)
        IslandState.markSwitched(context, testMode = effectiveTestMode)
        IslandState.clearLastRemainingMinutes(context, testMode = effectiveTestMode)
        scheduleIslandDismiss(context, startedId, startMillis)
    }

    // 课中：模板9 独立 ID，挂到下课；距下课倒计时由系统 Chronometer 自刷
    fun sendInClassIslandNotification(
        context: Context,
        courseName: String,
        classroom: String,
        section: String,
        startTime: String,
        endTime: String,
        notificationId: Int = ISLAND_NOTIFICATION_ID,
        testMode: Boolean = false
    ) {
        if (!isIslandSupported(context)) return

        val effectiveTestMode = testMode || isIslandTestId(notificationId)
        val inClassId = activeIdFor(notificationId, inClass = true)
        val countdownId = countdownIdFor(notificationId)
        val state = IslandState.snapshot(context, effectiveTestMode)
        val startMillis = state?.startMillis ?: System.currentTimeMillis()
        val endMillis = state?.endMillis ?: 0L
        val now = System.currentTimeMillis()
        if (endMillis <= now) {
            Log.w(TAG, "sendInClass: already past end, skip")
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(countdownId)
        manager.cancel(activeIdFor(notificationId, inClass = false))

        val params = buildInClassIslandParamsJson(
            context = context,
            courseName = courseName,
            classroom = classroom,
            section = section.ifEmpty { state?.section ?: "" },
            startTime = startTime,
            endTime = endTime,
            courseEndMillis = endMillis
        )

        sendIslandNotification(
            context = context,
            notificationId = inClassId,
            title = if (startTime.isNotEmpty()) "$courseName $startTime" else courseName,
            content = buildString {
                if (classroom.isNotEmpty()) append(classroom)
                if (isNotEmpty()) append(" ")
                if (endTime.isNotEmpty()) {
                    append(endTime)
                    append("下课")
                }
            },
            courseName = courseName,
            section = section,
            startTime = startTime,
            endTime = endTime,
            classroom = classroom,
            testMode = effectiveTestMode,
            useShizukuBypass = true,
            islandParamsOverride = params
        )

        IslandState.updateNotificationId(context, inClassId, testMode = effectiveTestMode)
        IslandState.markSwitched(context, testMode = effectiveTestMode)
        IslandState.clearLastRemainingMinutes(context, testMode = effectiveTestMode)
        scheduleIslandDismissAt(context, inClassId, startMillis, endMillis)
        stopInClassTicker()
        kickWidgetRefresh(context)
    }

    // 距下一次「剩余分钟」跳变的毫秒数；小组件刷新链仍按分钟对账
    fun msUntilNextMinuteBoundary(remainMs: Long): Long {
        val toBoundary = remainMs % 60_000L
        return (if (toBoundary <= 0L) 60_000L else toBoundary) + 200L
    }

    // 课中倒计时由系统 Chronometer 自刷，App 不再按分钟重推内容
    fun stopInClassTicker() {
        synchronized(inClassTickLock) {
            inClassTickJob?.cancel()
            inClassTickJob = null
        }
    }

    // 课中岛内容无需按分钟重推：距下课数字由系统 timer 自刷；对账只负责进窗补发与下课收起
    fun updateInClassIslandIfNeeded(
        context: Context,
        state: IslandState.Snapshot,
        testMode: Boolean = false
    ) {
        if (!isInClassReminderEnabled(context)) return
        if (state.endMillis <= System.currentTimeMillis()) return
        if (!isInClassNotificationId(state.notificationId)) return
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
        scheduleIslandDismissAt(
            context,
            notificationId,
            courseStartMillis,
            System.currentTimeMillis() + ISLAND_STARTED_VISIBLE_MS
        )
    }

    // 课中提醒：精确闹钟挂在下课时刻收起
    private fun scheduleIslandDismissAt(
        context: Context,
        notificationId: Int,
        courseStartMillis: Long,
        triggerAtMillis: Long
    ) {
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
                triggerAtMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            Log.w(TAG, "Cannot schedule island dismiss alarm")
        }
    }
}
