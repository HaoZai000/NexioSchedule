package com.haooz.chedule.wearable

import android.content.Context
import com.haooz.chedule.data.NexioLog
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
        return try {
            val repo = CourseRepository.getInstance(context.applicationContext)
            val json = WatchPayload.buildFullJson(repo, context.applicationContext, scheduleName)
            val dir = java.io.File(context.applicationContext.filesDir, "wearable")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "nexio-watch-schedule.json")
            file.writeText(json, Charsets.UTF_8)
            NexioLog.i(TAG, "export json -> ${file.absolutePath}")
            ensurePermissionThenPush("manual-export", scheduleName)
            onDone?.invoke(true, file.absolutePath)
            file.absolutePath
        } catch (e: Exception) {
            NexioLog.w(TAG, "export fail: ${e.message}")
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
                NexioLog.w(TAG, "push skip ($reason): no node, queued")
                pendingPush.set(true)
                pendingScheduleName = scheduleName
                return@execute
            }
            if (permissionGranted.get()) {
                doPush(reason, scheduleName, id)
                return@execute
            }
            val auth = authApi ?: run {
                NexioLog.w(TAG, "authApi null")
                return@execute
            }
            auth.checkPermissions(id, arrayOf(Permission.DEVICE_MANAGER))
                .addOnSuccessListener { flags ->
                    val granted = flags.isNotEmpty() && flags[0]
                    NexioLog.i(TAG, "checkPermission($id)=$granted")
                    if (granted) {
                        permissionGranted.set(true)
                        doPush(reason, scheduleName, id)
                    } else {
                        auth.requestPermission(id, Permission.DEVICE_MANAGER)
                            .addOnSuccessListener { perms ->
                                val ok = perms.any { it == Permission.DEVICE_MANAGER }
                                NexioLog.i(TAG, "requestPermission result ok=$ok")
                                permissionGranted.set(ok)
                                if (ok) {
                                    doPush(reason, scheduleName, id)
                                } else {
                                    NexioLog.w(TAG, "push fail ($reason): permission not granted")
                                }
                            }
                            .addOnFailureListener { e ->
                                NexioLog.w(TAG, "requestPermission fail: ${e.message}")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    NexioLog.w(TAG, "checkPermission fail: ${e.message}")
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
        try {
            val repo = CourseRepository.getInstance(appContext)
            // v4 整表推送：一次下发完整学期（课程+周次规则+设置+节次时间+假期），
            // 手表自行推算任意日期。
            val payload = WatchPayload.buildFullJson(repo, appContext, scheduleName)
            val api = messageApi ?: return
            api.sendMessage(id, payload.toByteArray(Charsets.UTF_8))
                .addOnSuccessListener {
                    NexioLog.i(TAG, "push ok ($reason), node=$id, bytes=${payload.length}")
                }
                .addOnFailureListener { e ->
                    NexioLog.w(TAG, "push fail ($reason): ${e.message}")
                }
        } catch (e: Exception) {
            NexioLog.w(TAG, "push error ($reason): ${e.message}")
        } finally {
            pushing.set(false)
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
