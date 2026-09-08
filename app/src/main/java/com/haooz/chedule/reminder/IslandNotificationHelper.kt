/** 超级岛通知助手 - 管理灵动岛/超级岛通知展示 */
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
    // business: 运营场景标识（官方必选字段）
    private const val BUSINESS_TAG = "course_reminder"
    // 通知更新序号计数器：保证课前→已上课等多次更新不乱序（官方 sequence 字段）
    private val sequenceCounter = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() / 1000)

    /**
     * 课程提醒超级岛统一使用的通知 ID。
     * 课前倒计时与"已上课"必须共用同一个 ID，否则两态会同时停留在岛上（倒计时与已上课重叠）。
     */
    const val ISLAND_NOTIFICATION_ID = 1003
    /** 测试通知使用的独立 ID */
    const val ISLAND_TEST_NOTIFICATION_ID = 5000
    /** "已上课"岛展示时长：到点后自动收起（由精确闹钟触发，不依赖每分钟对账） */
    const val ISLAND_STARTED_VISIBLE_MS = 15_000L
    /** 自动收起闹钟的 requestCode 基值 */
    private const val ISLAND_DISMISS_RC_BASE = 72000
    /** 历史版本遗留的岛通知 ID：发送前统一清理，避免与当前倒计时岛重叠 */
    private val LEGACY_ISLAND_NOTIFICATION_IDS = intArrayOf(1001, 1002)

    private val scope = CoroutineScope(Dispatchers.IO)
    // 串行化 Shizuku bypass 流程，避免并发导致 XMSF 网络状态错乱
    private val shizukuBypassMutex = Mutex()

    /**
     * 超级岛倒计时状态。
     *
     * 存在的意义：岛上的倒计时是系统原生 ChronometerCountDown，而"已上课"切换依赖 AlarmManager
     * 精确闹钟。闹钟可能因 Doze 延迟、进程被杀、PendingIntent 被复用覆盖而丢失或滞后，
     * 一旦丢失，岛会永久卡在倒计时或迟迟不切换。这里持久化一份"当前岛应该处于什么状态"，
     * 由每分钟的刷新链（WidgetRefreshReceiver → reconcileIslandCountdown）对账兜底。
     */
    object IslandState {
        // 真实提醒与测试岛使用两套独立 prefs，避免互相覆盖：
        // 测试岛若直接写真实 state，等下回真实课前提醒触发时 expand 闹钟会被
        // IslandExpandReceiver 的 stale 校验丢弃，造成"已上课"切换丢失。
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

        /**
         * 根据通知 ID 自动选择 state 来源：
         * 测试 ID (5000) → 测试 state；其他 → 真实 state。
         * 给 receiver 使用，避免误把测试 state 当成真实 state 校验导致 expand/dismiss 闹钟被丢弃。
         */
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

    /**
     * 统一的 Shizuku bypass 执行器：
     * - 用 Mutex 串行化，避免 disable→notify→enable 序列在并发下互相错位
     * - 用 try/finally 保证 XMSF 网络一定被恢复，即使 notify 抛异常或协程被取消
     * - 不可用或 disable 失败时仍直接发送通知（降级路径）
     */
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

    /** 收起当前所有课程提醒岛（含历史遗留 ID 与测试 ID） */
    fun cancelIslandNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ISLAND_NOTIFICATION_ID)
        for (id in LEGACY_ISLAND_NOTIFICATION_IDS) manager.cancel(id)
        manager.cancel(ISLAND_TEST_NOTIFICATION_ID)
    }

    /** 只清理历史遗留 ID，避免与本次发出的倒计时岛并存造成"两个岛" */
    private fun cancelLegacyIslandNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (id in LEGACY_ISLAND_NOTIFICATION_IDS) manager.cancel(id)
    }

    /**
     * 构建超级岛通知参数 JSON。
     *
     * 关键约定：[courseStartMillis] 是唯一的"课程开始时间"真源，
     * 倒计时剩余量、倒计时文案、已上课/即将上课态全部由它派生，
     * 不再由调用方传入的分钟数另算一套，避免岛倒计时与切换闹钟对不上。
     */
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

        // 读取超级岛左侧/右侧显示模式
        val prefs = context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE)
        val expandGlowEnabled = prefs.getBoolean(KEY_ISLAND_EXPAND_GLOW_ENABLED, true)
        val leftMode = prefs.getInt("island_left_mode", 0)
        val rightMode = prefs.getInt("island_right_mode", 1)

        val now = System.currentTimeMillis()
        // 仅当开始时间在未来的此刻才算倒计时中；null 或已到点一律按"已上课"静态态渲染
        val remainMs = courseStartMillis?.let { it - now }
        val counting = remainMs != null && remainMs > 0
        // 向上取整：保证"N分钟"文案与倒计时剩余秒数一致（剩余 55s 显示 1 分钟而不是 2 分钟）
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

        // param_v2 部分
        val paramV2 = JSONObject().apply {
            put("business", BUSINESS_TAG)
            put("protocol", 1)
            put("enableFloat", true)
            put("updatable", true)
            // HyperOS expanded-island glow effect.
            put("outEffectSrc", if (expandGlowEnabled) "outer_glow" else "")
            // reopen=reopen：课前提醒→已上课切换会重发同 id 通知，需允许再次显示
            put("reopen", "reopen")
            // sequence：每次更新递增，避免课前态/已上课态展示乱序
            put("sequence", sequenceCounter.incrementAndGet())

            // 模板9：文本2 + 识别1 + 按钮2
            // 上半部分：文本组件2 baseInfo（type=2）
            val baseInfo = JSONObject().apply {
                put("type", 2) // 文本组件类型 2
                // 第一行：课程名称
                put("title", courseName ?: title)
                // 第二行：上课时间｜课程节次
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
                // 其他字段留空
                put("subTitle", "")
                put("extraTitle", "")
                put("specialTitle", "")
                put("subContent", "")
                put("picFunction", "")
                // 分割线
                put("showDivider", true)
                put("showContentDivider", false)
                // 颜色
                put("colorTitle", "#111111")
                put("colorTitleDark", "#ffffff")
                put("colorContent", "#333333")
                put("colorContentDark", "#cccccc")
            }
            put("baseInfo", baseInfo)

            // 识别图形1：应用图标（不传pic自动隐藏）
            val picInfo = JSONObject().apply {
                put("type", 1)
                put("pic", "")
            }
            put("picInfo", picInfo)

            // 下半部分：按钮组件2 hintInfo（type=2）+ 动态倒计时
            val hintInfo = JSONObject().apply {
                put("type", 2) // 按钮组件类型 2
                // 前置文本1标签：倒计时进行中=即将上课，结束后=现在
                put("content", if (counting) "即将上课" else "现在")
                // 前置文本1：倒计时进行中为空，结束后显示"已上课"
                put("title", if (counting) "" else "已上课")
                // 动态倒计时 timerInfo
                val timerInfo = JSONObject().apply {
                    if (counting) {
                        put("timerType", -1) // -1 倒计时开始
                        put("timerWhen", courseStartMillis!!)
                        put("timerTotal", 0L)
                        put("timerSystemCurrent", now)
                    } else {
                        put("timerType", 0) // 0 静态文本：已上课
                        put("timerWhen", 0)
                        put("timerTotal", 0)
                        put("timerSystemCurrent", 0)
                    }
                }
                put("timerInfo", timerInfo)
                // 前置文本2标签：地点
                put("subContent", "地点")
                // 前置文本2数值：教室名称
                put("subTitle", classroom ?: "")
                // 颜色
                put("colorContent", "#666666")
                put("colorContentDark", "#aaaaaa")
                put("colorTitle", "#222222")
                put("colorTitleDark", "#eeeeee")
                put("colorSubContent", "#666666")
                put("colorSubContentDark", "#aaaaaa")
                put("colorSubTitle", "#222222")
                put("colorSubTitleDark", "#eeeeee")
                // 可选：圆头操作按钮 —— 上课勿扰
                // 查看课表仍可点击岛体本身进入 MainActivity
                // 测试模式下点击立即开关勿扰（用于验证按钮链路），正式通知则切换「上课自动开启勿扰」
                val actionInfo = JSONObject().apply {
                    put("actionTitle", "上课勿扰")
                    put("actionIntentType", 2) // 2=广播
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

            // 岛数据 - param_island
            val paramIsland = JSONObject().apply {
                put("islandProperty", 1) // 信息展示为主
                put("islandTimeout", 3600) // 1小时超时（大岛最大存活时长）

                // 大岛区域（模板2：A图文1 + B文本textInfo）
                val bigIsland = JSONObject().apply {
                    put("templateNo", 2) // 模板2：文本

                    // A区：左侧显示
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

                    // B区：右侧显示
                    if (rightMode == 2 && counting) {
                        // 倒计时模式：使用 sameWidthDigitInfo 等宽数字计时
                        val sameWidthDigitInfo = JSONObject().apply {
                            put("content", "上课")
                            put("showHighlightColor", false)
                            val timerInfo = JSONObject().apply {
                                put("timerType", -1) // -1 倒计时
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
                        // 文本模式
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

                // 小岛区域
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

        // 构建岛参数 JSON
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

        // 添加图片 Bundle（大岛模板2：A图文1 + B文本textInfo）
        val picsBundle = Bundle().apply {
            putParcelable("miui.focus.pic_app_icon", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_app_icon_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
        }
        builder.addExtras(Bundle().apply {
            putBundle("miui.focus.pics", picsBundle)
        })

        // 添加岛参数
        val notification = builder.build()
        notification.extras.putString("miui.focus.param", islandParams)

        scope.launch {
            withShizukuBypass(context, notificationId, notification, useShizukuBypass)
        }
    }

    /**
     * 发送课前倒计时超级岛。
     *
     * @param courseStartMillis 课程开始的精确时间戳（"今天 HH:mm:00.000"），由调用方统一计算。
     *                          岛上的倒计时、分钟文案、"已上课"切换闹钟全部以此为准。
     * @param courseEndMillis   课程结束时间戳，用于到点自动收起岛（0 表示未知，按 15 秒兜底）。
     */
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

        // 开始时间已过：不要再画一个永远走不完的倒计时岛，直接落到"已上课"
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

        // 清理历史遗留 ID，防止上一版遗留的岛与本次倒计时岛同时停留
        cancelLegacyIslandNotifications(context)

        // 记录岛状态，供每分钟对账使用（闹钟丢失时也能正确切换/收起）
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
        // 立即把刷新链切到每分钟：否则下一次刷新可能排在 30 分钟后，
        // 对账就形同虚设（测试通知尤其明显）
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

    /**
     * 发送测试超级岛通知（实际课程）
     */
    fun sendTestIslandNotification(context: Context) {
        if (!isIslandSupported(context)) {
            Log.w(TAG, "Island not supported on this device")
            return
        }

        ensureChannel(context)

        // 测试数据
        val courseName = "大学英语Ⅱ"
        val classroom = "A201"
        val section = "第3~4节"
        val testNotificationId = ISLAND_TEST_NOTIFICATION_ID

        // 当前时间 + 2 分钟。显示的时间串也由这两个时间戳派生，
        // 否则岛上显示 09:00 而倒计时走的是"现在+2分钟"，测试时对不上，很难判断是否正确。
        val courseStartTimestamp = System.currentTimeMillis() + 120_000L
        val courseEndTimestamp = courseStartTimestamp + 45 * 60_000L
        val startTime = formatClock(courseStartTimestamp)
        val endTime = formatClock(courseEndTimestamp)

        // 清理历史遗留 ID 与上一轮测试的残留
        cancelLegacyIslandNotifications(context)
        // 测试岛写到独立 PREF，不污染真实课前提醒的 state（参见 IslandState 注释）
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

        // 构建标题和内容
        val title = "$courseName $startTime"
        val content = "第3~4节｜A201"

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 构建岛参数
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

        // 添加图片
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

        // Shizuku 不可用时弹 Toast 提示，然后直接发送（不走 bypass）
        if (!isShizukuAvailable()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Shizuku 未授权，超级岛通知可能无法正常显示", android.widget.Toast.LENGTH_LONG).show()
            }
            sendNotificationDirect(context, testNotificationId, notification)
        } else {
            // Shizuku 已可用，走 bypass 发送
            scope.launch {
                withShizukuBypass(context, testNotificationId, notification, useShizukuBypass = true)
            }
        }

        // 用精确闹钟在倒计时结束的那一刻切换到"已上课"。
        // 之前这里用进程内 Handler，进程被杀即失效；改成只靠每分钟对账又会滞后近一分钟。
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

    /**
     * 发送"已上课"通知，触发展开态弹出。
     *
     * 幂等：同一门课只切换一次，避免展开闹钟与每分钟对账重复弹出。
     */
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

        // 测试岛 / 真实岛用各自的 state；调用方已传 testMode，但保险起见再以 notificationId 校准一次
        val effectiveTestMode = testMode || notificationId == ISLAND_TEST_NOTIFICATION_ID

        if (IslandState.isSwitched(context, testMode = effectiveTestMode)) {
            Log.d(TAG, "Already switched to started state, skip duplicate update")
            return
        }
        val state = IslandState.snapshot(context, testMode = effectiveTestMode)

        // 兜底：若同种 state 的倒计时岛用了另一个通知 ID（调用方传错、或历史遗留数据），
        // 先把它收起来。否则它会永远停在 00:00，与"已上课"岛并存。
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
            courseStartMillis = null, // null = 已上课静态态，与倒计时共用同一通知 ID 直接替换
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

    /** 把小组件刷新链立刻切到每分钟节奏，保证岛状态对账能及时跑起来 */
    private fun kickWidgetRefresh(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            CourseReminderHelper.scheduleNextWidgetRefresh(context, alarmManager)
        } catch (_: Exception) {
            Log.w(TAG, "Failed to kick widget refresh")
        }
    }

    /**
     * 注册"已上课"岛的自动收起闹钟。
     *
     * 之前只靠每分钟刷新链对账收起，最坏要等一整分钟才消失，且刷新链断了就永远收不起来。
     * 改为精确闹钟后，展示时长一到立即收起，对账仅作为兜底。
     */
    private fun scheduleIslandDismiss(context: Context, notificationId: Int, courseStartMillis: Long) {
        val intent = Intent(context, IslandDismissReceiver::class.java).apply {
            putExtra(IslandDismissReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(IslandDismissReceiver.EXTRA_COURSE_START_MILLIS, courseStartMillis)
        }
        // 与 expand 闹钟同理：按课程开始时刻的"自纪元起的分钟数"取模派生。
        // 用 mod 100000 远大于一天 1440 分钟，**避免两门同一分钟开始的课撞号**；
        // 之前按"当日分钟数"（mod 1440）虽然跨日安全，但同分钟并发课仍会撞，
        // 导致后一门的 dismiss 闹钟覆盖前一门的，两门课共享同一个 PendingIntent。
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
