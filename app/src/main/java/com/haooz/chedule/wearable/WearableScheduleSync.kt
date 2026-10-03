package com.haooz.chedule.wearable

import android.content.Context
import android.util.Log
import com.haooz.chedule.data.CourseRepository
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.NodeApi
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 小米穿戴（手表 rpk）课表推送。
 *
 * 通道：xms-wearable MessageApi ↔ 手表 @system.interconnect
 * 权限：必须先 requestPermission(DEVICE_MANAGER)，否则 bind/send 会 permission denied
 *
 * 注意：手表 rpk 与 APK 必须同包名（com.haooz.chedule）且同签名。
 */
object WearableScheduleSync {

    private const val TAG = "WearableScheduleSync"
    private const val PROTOCOL = "nexio.schedule"

    /** ACK 确认超时：sendMessage 成功后等待手环回 ack 的秒数 */
    private const val ACK_TIMEOUT_SEC = 5L

    /** 最多重试次数（不含首次发送），即最多共 3 次尝试 */
    private const val MAX_RETRIES = 2

    private val initialized = AtomicBoolean(false)
    private val pushing = AtomicBoolean(false)
    private val pendingPush = AtomicBoolean(false)
    private val permissionGranted = AtomicBoolean(false)
    /** 无节点时暂存的课表名，连上后补推 */
    @Volatile
    private var pendingScheduleName: String = ""

    /** 当前在途推送的 sentAt（取自 payload JSON）；手环 ack 带同一 sentAt 才视为本次推送成功。-1=无在途推送 */
    @Volatile
    private var inFlightSentAt: Long = -1L

    /** 已重试次数（不计首次发送）；ACK 成功或彻底放弃时清零 */
    private val retryCount = AtomicInteger(0)

    /** ACK 超时定时器；收到 ack 后取消 */
    @Volatile
    private var ackTimeoutFuture: ScheduledFuture<*>? = null

    /**
     * 手动导出推送的结果回调槽（对象级）。
     * 仅由 exportToWearable 在派发「manual-export」推送前挂入；4 个结算点（无节点/权限失败/重试放弃/ACK 成功）
     * 触发后立即清空，防止重入与悬挂。自动推送（onScheduleChanged/pushSchedule/node-ready 补推）不触发。
     * 回调运行在后台 scheduler / binder 线程，不做任何 UI 操作，由调用方自行切主线程。
     */
    @Volatile
    var manualOutcome: ((ok: Boolean, reason: String) -> Unit)? = null

    /** 标记当前在途推送是否来自手动导出；ACK 成功 / 彻底放弃时据此决定是否触发 manualOutcome。 */
    @Volatile
    private var manualInFlight: Boolean = false

    /** 触发并消费手动导出结果回调（幂等：先到者胜出，后到者 manualOutcome 已为 null 直接忽略）。 */
    private fun fireManualOutcome(ok: Boolean, reason: String) {
        val cb = manualOutcome ?: return
        manualOutcome = null
        manualInFlight = false
        cb(ok, reason)
    }

    private lateinit var appContext: Context
    private var nodeApi: NodeApi? = null
    private var messageApi: MessageApi? = null
    private var authApi: AuthApi? = null
    private var nodeId: String? = null

    private var scheduler: ScheduledExecutorService? = null

    private val messageListener = OnMessageReceivedListener { _, message ->
        try {
            val text = String(message, Charsets.UTF_8)
            val json = JSONObject(text)
            val protocol = json.optString("protocol")
            if (protocol.isNotEmpty() && protocol != PROTOCOL) return@OnMessageReceivedListener
            val action = json.optString("action")
            when (action) {
                // 手环收到 payload 后回 ack（带同一 sentAt）。仅当与在途推送匹配时才确认成功。
                "ack" -> {
                    val ackSentAt = json.optLong("sentAt", -1L)
                    val expected = inFlightSentAt
                    if (expected > 0L && ackSentAt == expected) {
                        // 与 ACK 超时/发送失败路径竞争：用 CAS 抢 pushing，先到者胜出，后到者直接忽略
                        if (pushing.compareAndSet(true, false)) {
                            cancelAckTimeout()
                            retryCount.set(0)
                            inFlightSentAt = -1L
                            Log.i(TAG, "push ack confirmed, sentAt=$ackSentAt")
                            // (d) ACK 确认成功：手动导出场景回调成功
                            if (manualInFlight) fireManualOutcome(true, "")
                        }
                    } else {
                        Log.i(TAG, "ignore ack sentAt=$ackSentAt expected=$expected")
                    }
                }
                // 手环主动请求推送（首次连接/唤醒）
                "request", "" -> {
                    pushSchedule("watch-request")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "handle message fail: ${e.message}")
        }
    }

    /** 应用启动时调用一次 */
    @Synchronized
    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        try {
            nodeApi = Wearable.getNodeApi(appContext)
            messageApi = Wearable.getMessageApi(appContext)
            authApi = Wearable.getAuthApi(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "wearable api init fail: ${e.message}")
            initialized.set(false)
            return
        }
        tryResolveNode()
        startNodeWatcher()
        Log.i(TAG, "init ok")
    }

    /** 课程/课表变更后调用（可多次，内部合帧） */
    fun onScheduleChanged(reason: String = "course-change") {
        if (!initialized.get()) return
        pendingPush.set(true)
        schedulePush(300L, reason)
    }

    /**
     * 界面「导出到手环」：打包 JSON 落盘 + 推送到手表。
     * @return JSON 文件路径（失败返回 null）
     */
    fun exportToWearable(
        context: Context,
        scheduleName: String = "",
        onDone: ((ok: Boolean, message: String) -> Unit)? = null
    ): String? {
        if (!initialized.get()) {
            init(context)
        }
        // 若上一轮手动导出结果回调仍悬挂，先触发旧值，避免 UI 永久等待后再挂新回调。
        manualOutcome?.let { old ->
            manualOutcome = null
            manualInFlight = false
            old(false, "已被新的导出取代")
        }
        return try {
            val repo = CourseRepository.getInstance(context.applicationContext)
            val json = WatchPayload.buildFullJson(repo, context.applicationContext, scheduleName)
            val dir = java.io.File(context.applicationContext.filesDir, "wearable")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "nexio-watch-schedule.json")
            file.writeText(json, Charsets.UTF_8)
            Log.i(TAG, "export json -> ${file.absolutePath}")
            // 本地导出成功：把结果回调挂到对象槽，推送异步派发，结果由 4 个结算点回调（不再立即回调成功）。
            manualOutcome = onDone
            manualInFlight = false
            ensurePermissionThenPush("manual-export", scheduleName)
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "export fail: ${e.message}")
            // 本地导出失败：直接回调（path==null），不走推送结算。
            onDone?.invoke(false, e.message ?: "export fail")
            null
        }
    }

    /** 立即推送整周课表 */
    fun pushSchedule(reason: String = "manual", scheduleName: String = "") {
        if (!initialized.get()) return
        ensurePermissionThenPush(reason, scheduleName)
    }

    /** 先确认 DEVICE_MANAGER 授权，再发消息（permission denied 的根因） */
    private fun ensurePermissionThenPush(reason: String, scheduleName: String = "") {
        val exec = scheduler ?: Executors.newSingleThreadScheduledExecutor().also { scheduler = it }
        exec.execute {
            var id = resolveNodeId()
            if (id == null) {
                // 节点可能尚未就绪：再探一次，仍无则排队，等 node-ready 补推
                tryResolveNode()
                Thread.sleep(400)
                id = resolveNodeId()
            }
            if (id == null) {
                Log.w(TAG, "push skip ($reason): no node, queued")
                pendingPush.set(true)
                pendingScheduleName = scheduleName
                // (a) 无节点：手动导出场景立即回调未发现设备
                if (reason == "manual-export") fireManualOutcome(false, "no-device")
                return@execute
            }
            if (permissionGranted.get()) {
                doPush(reason, scheduleName, id)
                return@execute
            }
            val auth = authApi ?: run {
                Log.w(TAG, "authApi null")
                // (b) 权限未授予/授权失败
                if (reason == "manual-export") fireManualOutcome(false, "权限未授予或授权失败")
                return@execute
            }
            auth.checkPermissions(id, arrayOf(Permission.DEVICE_MANAGER))
                .addOnSuccessListener { flags ->
                    val granted = flags.isNotEmpty() && flags[0]
                    Log.i(TAG, "checkPermission($id)=$granted")
                    if (granted) {
                        permissionGranted.set(true)
                        doPush(reason, scheduleName, id)
                    } else {
                        auth.requestPermission(id, Permission.DEVICE_MANAGER)
                            .addOnSuccessListener { perms ->
                                val ok = perms.any { it == Permission.DEVICE_MANAGER }
                                Log.i(TAG, "requestPermission result ok=$ok")
                                permissionGranted.set(ok)
                                if (ok) {
                                    doPush(reason, scheduleName, id)
                                } else {
                                    Log.w(
                                        TAG,
                                        "push fail ($reason): 互联/消息权限未授予（DEVICE_MANAGER）。" +
                                            "排查指引：1) 系统设置→应用→NexioSchedule→权限，授予「连接与共享/设备互联/邻近设备」类权限；" +
                                            "2) 确认手机端小米运动健康/小米穿戴 App 已与手环正常连接、后台运行；" +
                                            "3) 授权后到设置页点一次「导出到手环」手动重试。"
                                    )
                                    // (b) requestPermission 成功但未授予
                                    if (reason == "manual-export") fireManualOutcome(false, "权限未授予或授权失败")
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "requestPermission fail: ${e.message}")
                                // (b) requestPermission 失败
                                if (reason == "manual-export") fireManualOutcome(false, "权限未授予或授权失败")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "checkPermission fail: ${e.message}")
                    // (b) checkPermissions 失败
                    if (reason == "manual-export") fireManualOutcome(false, "权限未授予或授权失败")
                }
        }
    }

    private fun schedulePush(delayMs: Long, reason: String) {
        val exec = scheduler ?: Executors.newSingleThreadScheduledExecutor().also { scheduler = it }
        exec.schedule({
            if (pendingPush.compareAndSet(true, false) || reason == "watch-request" || reason == "node-ready") {
                ensurePermissionThenPush(reason)
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun doPush(reason: String, scheduleName: String, id: String) {
        if (pushing.get()) {
            pendingPush.set(true)
            return
        }
        pushing.set(true)
        // 标记在途推送是否来自手动导出，供 ACK 成功/彻底放弃结算时触发 manualOutcome
        if (reason == "manual-export") manualInFlight = true
        try {
            val repo = CourseRepository.getInstance(appContext)
            // v4 整表推送：一次下发完整学期（课程+周次规则+设置+节次时间+假期），
            // 手表自行推算任意日期。
            val payload = WatchPayload.buildFullJson(repo, appContext, scheduleName)
            // 提取本次 payload 的 sentAt，作为与手环 ack 配对的关联 ID
            val sentAt = try {
                JSONObject(payload).optLong("sentAt", -1L)
            } catch (e: Exception) {
                -1L
            }
            inFlightSentAt = sentAt
            val api = messageApi
            if (api == null) {
                onPushOutcomeFailure("messageApi null", reason, scheduleName, id)
                return
            }
            // 注意：sendMessage 成功 ≠ 手环收到。成功后仅启动 ACK 超时器；
            // pushing 保持 true，直到收到匹配 ack 或重试彻底放弃才释放。
            api.sendMessage(id, payload.toByteArray(Charsets.UTF_8))
                .addOnSuccessListener {
                    Log.i(TAG, "push sent ($reason), node=$id, bytes=${payload.length}, sentAt=$sentAt")
                    armAckTimeout(reason, scheduleName, id)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "push send fail ($reason): ${e.message}")
                    onPushOutcomeFailure(e.message ?: "send fail", reason, scheduleName, id)
                }
        } catch (e: Exception) {
            Log.w(TAG, "push error ($reason): ${e.message}")
            onPushOutcomeFailure(e.message ?: "push error", reason, scheduleName, id)
        }
    }

    /** 启动 ACK 超时器：[ACK_TIMEOUT_SEC] 秒内未收到匹配 ack 则判失败并走重试。 */
    private fun armAckTimeout(reason: String, scheduleName: String, id: String) {
        val exec = scheduler ?: Executors.newSingleThreadScheduledExecutor().also { scheduler = it }
        cancelAckTimeout()
        ackTimeoutFuture = exec.schedule({
            Log.w(TAG, "push ack timeout ($reason): no ack within ${ACK_TIMEOUT_SEC}s, sentAt=$inFlightSentAt")
            onPushOutcomeFailure("ack timeout", reason, scheduleName, id)
        }, ACK_TIMEOUT_SEC, TimeUnit.SECONDS)
    }

    private fun cancelAckTimeout() {
        ackTimeoutFuture?.cancel(false)
        ackTimeoutFuture = null
    }

    /**
     * 推送失败统一出口（sendMessage 失败 / 异常 / ACK 超时）。
     * 先用 CAS 抢 pushing：与 ack 成功路径竞争时先到者胜出；抢到后再按退避重试或彻底放弃。
     * 绝不在持锁状态下调度重试（先释放 pushing 再 schedule），避免 pushing 悬挂。
     */
    private fun onPushOutcomeFailure(errMsg: String, reason: String, scheduleName: String, id: String) {
        cancelAckTimeout()
        if (!pushing.compareAndSet(true, false)) {
            // 已被 ack 成功路径处理（pushing 已释放），本次失败结果忽略
            Log.i(TAG, "push outcome already settled ($reason), ignore failure: $errMsg")
            return
        }
        val retriesDone = retryCount.getAndIncrement()
        if (retriesDone < MAX_RETRIES) {
            val backoffSec = if (retriesDone == 0) 1L else 2L
            Log.w(TAG, "push retry #${retriesDone + 1}/$MAX_RETRIES in ${backoffSec}s ($reason): $errMsg")
            val exec = scheduler ?: Executors.newSingleThreadScheduledExecutor().also { scheduler = it }
            exec.schedule({
                // 退避后重发同一推送；若期间已有新推送占住 pushing，doPush 会把本次并入 pending
                doPush(reason, scheduleName, id)
            }, backoffSec, TimeUnit.SECONDS)
        } else {
            Log.w(TAG, "push give up ($reason): $errMsg (total attempts=${retriesDone + 1})")
            retryCount.set(0)
            inFlightSentAt = -1L
            // (c) 重试彻底放弃：手动导出场景回调失败（带具体 errMsg）
            if (manualInFlight) fireManualOutcome(false, errMsg)
        }
    }

    private fun tryResolveNode() {
        val api = nodeApi ?: return
        try {
            api.connectedNodes
                .addOnSuccessListener { nodes ->
                    val first = nodes?.firstOrNull()
                    if (first != null) {
                        val changed = nodeId != first.id
                        nodeId = first.id
                        Log.i(TAG, "node ready: ${first.id} changed=$changed pending=${pendingPush.get()}")
                        bindMessageListener(first.id)
                        // 连上后补推排队的课表
                        if (changed || pendingPush.get()) {
                            ensurePermissionThenPush("node-ready", pendingScheduleName)
                        }
                    } else {
                        Log.w(TAG, "connectedNodes empty")
                        nodeId = null
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "getConnectedNodes fail: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "tryResolveNode fail: ${e.message}")
        }
    }

    private fun resolveNodeId(): String? {
        nodeId?.let { return it }
        tryResolveNode()
        return nodeId
    }

    private fun bindMessageListener(id: String) {
        val api = messageApi ?: return
        try {
            api.addListener(id, messageListener)
                .addOnSuccessListener {
                    Log.i(TAG, "message listener bound: $id")
                }
                .addOnFailureListener { e ->
                    // 已注册属正常（重连/重复 bind），不刷警告
                    val msg = e.message ?: ""
                    if (msg.contains("registered")) {
                        Log.i(TAG, "message listener already bound")
                    } else {
                        Log.w(TAG, "bind listener fail: $msg")
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "bind listener error: ${e.message}")
        }
    }

    private fun startNodeWatcher() {
        val exec = scheduler ?: Executors.newSingleThreadScheduledExecutor().also { scheduler = it }
        exec.scheduleWithFixedDelay({
            tryResolveNode()
        }, 5, 30, TimeUnit.SECONDS)
    }
}
