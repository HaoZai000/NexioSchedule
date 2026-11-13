@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import com.haooz.chedule.data.backup.ScheduleFileController
import com.haooz.chedule.data.platform.showToast
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 文件选择 / 另存为的双向桥。
 *
 * 系统文件选择器只有 ArkTS 能拉起，而调用点在共享 UI 的导入导出页。方向与 HTTP 桥一致：
 * - Kotlin → ArkTS：C++ 把 `HostRequestFile` 指针交过来（`registerFilePicker` 注册 JS 函数）；
 * - ArkTS → Kotlin：选完/写完调 `setFileResult`，C++ 转到 [NexioOnFileResult]。
 *
 * ⚠ 调 ArkTS 必须在 JS 线程，统一丢到 `Dispatchers.Main`（由 `NexioInitMainHandler` 绑定）。
 */
private object FilePickerBridge {

    private var hostRequest: ((Int, String, String) -> Unit)? = null

    /** 请求号 → 回调。发起与回传都在主线程，不需要额外同步。 */
    private val pending = mutableMapOf<Int, (Boolean, String) -> Unit>()
    private var nextId = 1

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(Int, CPointer<ByteVar>?, CPointer<ByteVar>?) -> Unit>> =
            fn.reinterpret()
        hostRequest = { id, op, payload ->
            memScoped { cFunction.invoke(id, op.cstr.ptr, payload.cstr.ptr) }
        }
    }

    fun request(op: String, payload: String, onResult: (Boolean, String) -> Unit) {
        val host = hostRequest
        if (host == null) {
            onResult(false, "文件选择器未就绪")
            return
        }
        mainScope.launch {
            val id = nextId++
            pending[id] = onResult
            try {
                host(id, op, payload)
            } catch (t: Throwable) {
                pending.remove(id)
                onResult(false, t.message ?: "文件操作失败")
            }
        }
    }

    fun onResult(requestId: Int, ok: Boolean, payload: String) {
        pending.remove(requestId)?.invoke(ok, payload)
    }
}

/** C++ 在模块注册时把发起入口交过来（见 `napi_init.cpp`）。 */
@CName("NexioOnFileHostReady")
fun NexioOnFileHostReady(fn: COpaquePointer?) {
    FilePickerBridge.onHostReady(fn)
}

/** ArkTS 回传：`setFileResult(requestId, ok, payload)`；取消时 ok=false 且 payload 为空。 */
@CName("NexioOnFileResult")
fun NexioOnFileResult(requestId: Int, ok: Boolean, payload: CPointer<ByteVar>?) {
    FilePickerBridge.onResult(requestId, ok, payload?.toKString() ?: "")
}

/**
 * [ScheduleFileController] 的鸿蒙实现：导入走系统文件选择器读文本，导出走「另存为」。
 * 取消不弹提示；读写失败时把 ArkTS 给的错误原因弹出来。
 */
private class OhosScheduleFileController : ScheduleFileController {

    override fun openTextFile(mimeTypes: List<String>, onResult: (String?) -> Unit) {
        FilePickerBridge.request("openText", "") { ok, payload ->
            onResult(if (ok) payload else null)
        }
    }

    override fun saveTextFile(fileName: String, mimeType: String, content: String) {
        val payload = buildJsonObject {
            put("fileName", fileName)
            put("mimeType", mimeType)
            put("content", content)
        }.toString()
        FilePickerBridge.request("saveText", payload) { ok, error ->
            if (!ok && error.isNotEmpty()) showToast(error)
        }
    }
}

actual fun createScheduleFileController(): ScheduleFileController = OhosScheduleFileController()

/** 备份恢复的「手动选择文件」复用同一条选择链路。 */
internal fun pickTextFileForImport(onResult: (String?) -> Unit) {
    FilePickerBridge.request("openText", "") { ok, payload -> onResult(if (ok) payload else null) }
}
