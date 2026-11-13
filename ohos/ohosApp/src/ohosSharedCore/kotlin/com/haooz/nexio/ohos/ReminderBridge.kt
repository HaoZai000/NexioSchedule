@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * 课程提醒的 ArkTS 桥。
 *
 * 鸿蒙的通知发布只能由 ArkTS 调，方向与文件选择桥一致：
 * Kotlin 负责算「哪天几点要提醒」，ArkTS 负责真正发布 / 撤销，并把通知权限状态回传。
 *
 * ```
 * Kotlin（OhosCourseReminder） -> request("reschedule", json)
 *   -> C++ HostReminderRequest -> ReminderHost.ets（notificationManager）
 *   -> setReminderResult -> NexioOnReminderResult -> 回到 Kotlin 回调
 * ```
 *
 * 另有一条**单向**通道：ArkTS 启动/授权后把通知开关写回 [notificationEnabled]，
 * 因为 `ReminderController.hasNotificationPermission()` 是同步签名，不能每次去问系统。
 */
internal object ReminderBridge {

    private var hostRequest: ((Int, String, String) -> Unit)? = null

    private val pending = mutableMapOf<Int, (Boolean, String) -> Unit>()
    private var nextId = 1

    /** 所有对 ArkTS 的调用都必须落在 JS 线程（ArkTS VM 不接受跨线程调用）。 */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 系统通知总开关（由 ArkTS 播种与刷新）。 */
    @Volatile
    var notificationEnabled: Boolean = false
        private set

    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(Int, CPointer<ByteVar>?, CPointer<ByteVar>?) -> Unit>> =
            fn.reinterpret()
        hostRequest = { id, op, payload ->
            memScoped {
                cFunction.invoke(id, op.cstr.ptr, payload.cstr.ptr)
            }
        }
    }

    /**
     * 发起一次宿主调用。
     *
     * @param onResult 为 null 表示「发出去就行」（发布/撤销失败只落日志）；
     *   回调一定在主线程执行。
     */
    fun request(op: String, payload: String, onResult: ((Boolean, String) -> Unit)? = null) {
        mainScope.launch {
            val host = hostRequest
            if (host == null) {
                // 宿主还没注册：按失败收尾，别让调用方（或 suspendCancellableCoroutine）永远挂着
                onResult?.invoke(false, "")
                return@launch
            }
            val id = nextId++
            if (onResult != null) pending[id] = onResult
            try {
                host(id, op, payload)
            } catch (t: Throwable) {
                pending.remove(id)?.invoke(false, "")
            }
        }
    }

    /** 冷启动问一次系统通知开关，给 [notificationEnabled] 播种（ArkTS 回写）。 */
    fun queryNotification() {
        request("queryNotification", "{}")
    }

    fun onResult(requestId: Int, ok: Boolean, payload: String) {
        pending.remove(requestId)?.invoke(ok, payload)
    }

    /** ArkTS 侧 `setNotificationEnabled(enabled)`：冷启动播种 + 授权结果回写。 */
    fun onNotificationEnabled(enabled: Boolean) {
        notificationEnabled = enabled
    }
}

/** C++ 在模块注册时把发起入口交过来（见 `napi_init.cpp`）。 */
@CName("NexioOnReminderHostReady")
fun NexioOnReminderHostReady(fn: COpaquePointer?) {
    ReminderBridge.onHostReady(fn)
}

/** ArkTS 回传：`setReminderResult(requestId, ok, payload)`。 */
@CName("NexioOnReminderResult")
fun NexioOnReminderResult(requestId: Int, ok: Boolean, payload: CPointer<ByteVar>?) {
    ReminderBridge.onResult(requestId, ok, payload?.toKString() ?: "")
}

/** ArkTS 回传：`setNotificationEnabled(enabled)`（同步接口的数据源）。 */
@CName("NexioSetNotificationEnabled")
fun NexioSetNotificationEnabled(enabled: Boolean) {
    ReminderBridge.onNotificationEnabled(enabled)
}
