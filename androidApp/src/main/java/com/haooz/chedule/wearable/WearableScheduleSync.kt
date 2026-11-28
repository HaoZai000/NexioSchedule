package com.haooz.chedule.wearable

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.NodeApi
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * 小米穿戴（手表 rpk）课表推送。
 *
 * 通道：xms-wearable MessageApi ↔ 手表 @system.interconnect
 * 权限：必须先 requestPermission(DEVICE_MANAGER)，否则 bind/send 会 permission denied
 *
 * 注意：手表 rpk 与 APK 必须同包名（com.haooz.chedule）且**同签名**。
 * 签名不一致时 sendMessage 会失败且报错含糊（手环侧表现为一直连不上），
 * 所以失败时要把可执行的原因说清楚，而不是只给一句"推送失败"。
 */
object WearableScheduleSync {

    private const val TAG = "WearableScheduleSync"
    private const val PROTOCOL = "nexio.schedule"

    /** 手环端的首页路由（拉起手环 App 用，与 rpk manifest 的 router.entry 一致） */
    private const val WATCH_ROUTE = "pages/index"

    /** 上一包推送刚落地、期间又攒下变更，补推时用的归因名。 */
    private const val REASON_PUSH_DRAINED = "push-drained"

    /**
     * 即使没有 pending 也要推的原因。
     *
     * - `watch-request`：手表主动要数据
     * - `node-ready`：节点刚连上
     * - `push-drained`：上一包还没落地时的合并请求，必须补
     */
    private val FORCED_PUSH_REASONS = setOf("watch-request", "node-ready", REASON_PUSH_DRAINED)

    private val initialized = AtomicBoolean(false)
    private val pushing = AtomicBoolean(false)
    private val pendingPush = AtomicBoolean(false)
    private val permissionGranted = AtomicBoolean(false)
    /** 无节点时暂存的课表名，连上后补推 */
    @Volatile
    private var pendingScheduleName: String = ""

    private lateinit var appContext: Context
    private var nodeApi: NodeApi? = null
    private var messageApi: MessageApi? = null
    private var authApi: AuthApi? = null
    private var nodeId: String? = null

    private var scheduler: ScheduledExecutorService? = null

    /** 回调统一回主线程：推送链路大部分跑在 scheduler/回调线程上，见 [deliver] */
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * 把结果交回主线程再执行。
     *
     * ⚠ 这里踩过坑：SDK 的 Task 监听器在主线程回调，但"没有节点 / 通道不可用"这些
     * 早退分支是在 scheduler 线程上直接回调的，而回调里会 showToast ——
     * Android 在无 Looper 的线程上 Toast 会抛 "Can't toast on a thread that has not
     * called Looper.prepare()"，异常顺着 lambda 抛出把整个推送任务带走。
     * 用户侧看到的就是「点了导出，只有一句'正在推送'，然后再无下文」。
     */
    private fun deliver(
        onResult: ((ok: Boolean, message: String) -> Unit)?,
        ok: Boolean,
        message: String,
    ) {
        if (onResult == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onResult(ok, message)
        } else {
            mainHandler.post { onResult(ok, message) }
        }
    }

    private val messageListener = OnMessageReceivedListener { _, message ->
        try {
            val text = String(message, Charsets.UTF_8)
            val json = JSONObject(text)
            val protocol = json.optString("protocol")
            if (protocol.isNotEmpty() && protocol != PROTOCOL) return@OnMessageReceivedListener
            val action = json.optString("action")
            if (action == "request" || action.isEmpty()) {
                pushSchedule("watch-request")
            }
        } catch (e: Exception) {
            NexioLog.w(TAG, "handle message fail: ${e.message}")
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
            NexioLog.w(TAG, "wearable api init fail: ${e.message}")
            initialized.set(false)
            return
        }
        tryResolveNode()
        startNodeWatcher()
        NexioLog.i(TAG, "init ok")
    }

    /** 课程/课表变更后调用（可多次，内部合帧） */
    fun onScheduleChanged(reason: String = "course-change") {
        if (!initialized.get()) return
        pendingPush.set(true)
        schedulePush(300L, reason)
    }

    /**
     * 界面「导出到手环」：打包 JSON 落盘 + 推送到手表。
     *
     * @return JSON 文件路径（打包失败返回 null）。⚠ 这只表示**文件写好了**，
     *   不代表手环收到了 —— 推送是异步的，真实结果走 [onDone]，并同时 Toast 出来。
     *   以前这里写文件就回调成功，四个推送失败出口全无反馈，用户看到的就是「点了没反应」。
     */
    fun exportToWearable(
        context: Context,
        scheduleName: String = "",
        onDone: ((ok: Boolean, message: String) -> Unit)? = null
    ): String? {
        if (!initialized.get()) {
            init(context)
        }
        return try {
            val repo = CourseRepository.getInstance()
            val json = WatchPayload.buildFullJson(repo, context.applicationContext, scheduleName)
            val dir = java.io.File(context.applicationContext.filesDir, "wearable")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "nexio-watch-schedule.json")
            file.writeText(json, Charsets.UTF_8)
            val bytes = json.toByteArray(Charsets.UTF_8).size
            NexioLog.i(TAG, "export json -> ${file.absolutePath} (${bytes}B)")
            val report: (Boolean, String) -> Unit = { ok, message ->
                onDone?.invoke(ok, message)
                showToast(message, longDuration = !ok)
            }
            report(false, "已生成课表，正在推送到手环…")
            ensurePermissionThenPush("manual-export", scheduleName, report)
            file.absolutePath
        } catch (e: Exception) {
            NexioLog.w(TAG, "export build fail: ${e.message}")
            val message = "导出失败：${e.message ?: "未知错误"}"
            onDone?.invoke(false, message)
            showToast(message, longDuration = true)
            null
        }
    }

    /** 立即推送整周课表 */
    fun pushSchedule(reason: String = "manual", scheduleName: String = "") {
        if (!initialized.get()) return
        ensurePermissionThenPush(reason, scheduleName)
    }

    /** 先确认 DEVICE_MANAGER 授权，再发消息（permission denied 的根因） */
    private fun ensurePermissionThenPush(
        reason: String,
        scheduleName: String = "",
        onResult: ((ok: Boolean, message: String) -> Unit)? = null,
    ) {
        val exec = scheduler ?: Executors.newSingleThreadScheduledExecutor().also { scheduler = it }
        exec.execute {
            val id = resolveNodeId()
            if (id == null) {
                // 节点没就绪就只排队，不在这里 sleep 等：这条线程是单线程执行器，
                // 睡一下会把「手环主动来要数据」的推送一起卡住；节点连上后
                // tryResolveNode → "node-ready" 会立刻补推。
                NexioLog.w(TAG, "push skip ($reason): no node, queued")
                pendingPush.set(true)
                pendingScheduleName = scheduleName
                deliver(onResult, false, "手环未连接，重连后将自动同步")
                return@execute
            }
            if (permissionGranted.get()) {
                doPush(reason, scheduleName, id, onResult)
                return@execute
            }
            val auth = authApi ?: run {
                NexioLog.w(TAG, "authApi null")
                deliver(onResult, false, "手环服务不可用，请确认已安装小米运动健康")
                return@execute
            }
            auth.checkPermissions(id, arrayOf(Permission.DEVICE_MANAGER))
                .addOnSuccessListener { flags ->
                    val granted = flags.isNotEmpty() && flags[0]
                    NexioLog.i(TAG, "checkPermission($id)=$granted")
                    if (granted) {
                        permissionGranted.set(true)
                        doPush(reason, scheduleName, id, onResult)
                    } else {
                        auth.requestPermission(id, Permission.DEVICE_MANAGER)
                            .addOnSuccessListener { perms ->
                                // 必须按权限名比较：SDK 的 Permission 没有重写 equals()，
                                // requestPermission 回传的是 AIDL 反序列化出的新实例
                                // （Permission(Parcel)），`it == Permission.DEVICE_MANAGER`
                                // 是引用比较、恒为 false —— 用户明明点了允许，这次推送也发不出去。
                                val ok = perms.any {
                                    it.name.equals(Permission.DEVICE_MANAGER.name, ignoreCase = true)
                                }
                                NexioLog.i(TAG, "requestPermission result ok=$ok names=${perms.map { it.name }}")
                                permissionGranted.set(ok)
                                if (ok) {
                                    doPush(reason, scheduleName, id, onResult)
                                } else {
                                    NexioLog.w(TAG, "push fail ($reason): permission not granted")
                                    deliver(onResult, false, "未获得手环授权，无法推送")
                                }
                            }
                            .addOnFailureListener { e ->
                                NexioLog.w(TAG, "requestPermission fail: ${e.message}")
                                deliver(onResult, false, "申请手环授权失败：${e.message}")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    NexioLog.w(TAG, "checkPermission fail: ${e.message}")
                    deliver(onResult, false, "查询手环授权失败：${e.message}")
                }
        }
    }

    private fun schedulePush(delayMs: Long, reason: String) {
        val exec = scheduler ?: Executors.newSingleThreadScheduledExecutor().also { scheduler = it }
        exec.schedule({
            // 无 pending 的强制原因（手表请求 / 节点就绪 / 上一次推送刚落地）也要推；
            // 其余情况下 pending 已被前面的 compareAndSet 吃掉，就说明没什么要补的。
            if (pendingPush.compareAndSet(true, false) || reason in FORCED_PUSH_REASONS) {
                ensurePermissionThenPush(reason)
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun doPush(
        reason: String,
        scheduleName: String,
        id: String,
        onResult: ((ok: Boolean, message: String) -> Unit)? = null,
    ) {
        if (!pushing.compareAndSet(false, true)) {
            // 上一次还没发完就记账重排，等它落地再补一次，避免连点「推送到手环」连发多包
            pendingPush.set(true)
            pendingScheduleName = scheduleName
            deliver(onResult, false, "正在同步中，稍后将自动重试")
            return
        }
        try {
            val repo = CourseRepository.getInstance()
            // v4 整表推送：一次下发完整学期（课程+周次规则+设置+节次时间+假期），
            // 手表自行推算任意日期。
            val payload = WatchPayload.buildFullJson(repo, appContext, scheduleName)
            // 真实字节数：中文载荷按字符数算会低估 1.5–2 倍，排查「到底超没超限」时这个数字很关键
            val payloadBytes = payload.toByteArray(Charsets.UTF_8).size
            val api = messageApi ?: run {
                releasePushSlot()
                deliver(onResult, false, "无法连接手环同步服务")
                return
            }
            api.sendMessage(id, payload.toByteArray(Charsets.UTF_8))
                .addOnSuccessListener {
                    NexioLog.i(TAG, "push ok ($reason), node=$id, bytes=$payloadBytes")
                    // ⚠ 推送槽必须在这里、也就是 sendMessage 真正落地后才释放。
                    //   放进 finally 的话会在 sendMessage 还没回调时就把标记清掉，
                    //   合帧形同虚设，连点「推送到手环」会连发多包。
                    releasePushSlot()
                    deliver(onResult, true, "已推送到手环（${payloadBytes / 1024} KB）")
                }
                .addOnFailureListener { e ->
                    NexioLog.w(TAG, "push fail ($reason), node=$id, bytes=$payloadBytes: ${e.message}")
                    releasePushSlot()
                    // 不自动重试：失败多半是「手环 App 没在前台 / 没装」，
                    // 反复重试只会反复弹授权、反复拉起手环。交给 explainFailure 给原因。
                    explainFailure(payloadBytes, e.message ?: "", onResult)
                }
        } catch (e: Exception) {
            NexioLog.w(TAG, "push error ($reason): ${e.message}")
            releasePushSlot()
            deliver(onResult, false, "推送异常：${e.message}")
        }
    }

    /**
     * 推送失败时给出**可执行**的原因，而不是一句"推送失败"。
     *
     * 手环侧收不到推送只有三类原因，用户能做的动作完全不同：
     *  1. 手环上没装/装的是别的包 —— isWearAppInstalled 能直接判出来；
     *  2. 装了但没在前台（interconnect 连接随页面建立）—— 拉起手环 App 即可自愈：
     *     手环打开后会自己发 action=request，手机随即整包推送；
     *  3. 签名/包名不一致 —— 这种 sendMessage 会直接失败且没有可读信息，
     *     只能提示用户装配套版本（手环端同步诊断也会给出「连接异常」）。
     */
    private fun explainFailure(
        bytes: Int,
        detail: String,
        onResult: ((ok: Boolean, message: String) -> Unit)? = null,
    ) {
        val pkg = appContext.packageName
        val node = nodeApi
        val suffix = if (detail.isBlank()) "" else "（$detail）"
        // 先看是不是体积问题：这一条最确定（自己算出来的字节数），不用问系统
        if (bytes > WatchPayload.MAX_PAYLOAD_BYTES) {
            deliver(
                onResult,
                false,
                "课表数据过大（${bytes / 1024} KB，上限 ${WatchPayload.MAX_PAYLOAD_BYTES / 1024} KB），请精简课程后再同步",
            )
            return
        }
        if (node == null) {
            deliver(onResult, false, "推送失败$suffix，请在手环上打开 Nexio 后重试")
            return
        }
        node.isWearAppInstalled(pkg)
            .addOnSuccessListener { installed ->
                if (!installed) {
                    NexioLog.w(TAG, "wear app not installed: $pkg")
                    deliver(onResult, false, "手环上未安装 Nexio 手环版，请先用小米运动健康安装")
                    return@addOnSuccessListener
                }
                val launched = runCatching { node.launchWearApp(pkg, WATCH_ROUTE) }.isSuccess
                NexioLog.i(TAG, "wear app installed, launch=$launched")
                deliver(
                    onResult,
                    false,
                    if (launched) "同步失败，已尝试打开手环端 Nexio，请稍候查看"
                    else "推送失败$suffix，请在手环上打开 Nexio 后重试",
                )
            }
            .addOnFailureListener {
                // 老版本小米运动健康没有这项查询：不阻断，退回通用文案
                deliver(onResult, false, "推送失败$suffix，请在手环上打开 Nexio 后重试")
            }
    }

    /** 释放推送槽；期间又攒下了变更就立刻补推一次。 */
    private fun releasePushSlot() {
        pushing.set(false)
        if (pendingPush.get()) {
            schedulePush(0L, REASON_PUSH_DRAINED)
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
                        NexioLog.i(TAG, "node ready: ${first.id} changed=$changed pending=${pendingPush.get()}")
                        bindMessageListener(first.id)
                        // 连上后补推排队的课表
                        if (changed || pendingPush.get()) {
                            ensurePermissionThenPush("node-ready", pendingScheduleName)
                        }
                    } else {
                        NexioLog.w(TAG, "connectedNodes empty")
                        nodeId = null
                    }
                }
                .addOnFailureListener { e ->
                    NexioLog.w(TAG, "getConnectedNodes fail: ${e.message}")
                }
        } catch (e: Exception) {
            NexioLog.w(TAG, "tryResolveNode fail: ${e.message}")
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
                    NexioLog.i(TAG, "message listener bound: $id")
                }
                .addOnFailureListener { e ->
                    // 已注册属正常（重连/重复 bind），不刷警告
                    val msg = e.message ?: ""
                    if (msg.contains("registered")) {
                        NexioLog.i(TAG, "message listener already bound")
                    } else {
                        NexioLog.w(TAG, "bind listener fail: $msg")
                    }
                }
        } catch (e: Exception) {
            NexioLog.w(TAG, "bind listener error: ${e.message}")
        }
    }

    private fun startNodeWatcher() {
        val exec = scheduler ?: Executors.newSingleThreadScheduledExecutor().also { scheduler = it }
        exec.scheduleWithFixedDelay({
            tryResolveNode()
        }, 5, 30, TimeUnit.SECONDS)
    }
}
