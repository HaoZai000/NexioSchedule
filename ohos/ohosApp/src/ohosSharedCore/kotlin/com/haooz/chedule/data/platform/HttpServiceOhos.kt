@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.chedule.data.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 鸿蒙端 HTTP：走 ArkTS 的 `@ohos.net.http`。
 *
 * NDK sysroot 里没有 curl/openssl，网络只能借系统的。桥的两个方向：
 * - 发起：C++ 把 `HostHttpSend` 函数指针交过来，这里调用它 → ArkTS `sendHttp`；
 * - 回传：ArkTS 调 `setHttpResult` → C++ → [NexioOnHttpResult]。
 *
 * ⚠ 调 ArkTS 必须在 **JS 线程**，否则 ArkTS VM 直接 `ecma_vm cannot run in multi-thread` 崩进程。
 * 所以发送包在 `withContext(Dispatchers.Main)` 里；`await` 挂起时会让出 JS 线程，不阻塞渲染。
 *
 * `maxBytes` 只能事后截断（ArkTS 侧一次性拿到整个响应，没有流式接口），
 * 与 iOS 的「边读边计数」不同 —— 超大响应仍会先在 ArkTS 侧占满内存。
 */
private object HttpBridge {

    private var hostSend: ((Int, String, Double) -> Unit)? = null

    /** 请求号 → 等待者。发起与回传都在 JS 线程，不需要额外同步。 */
    private val pending = mutableMapOf<Int, CompletableDeferred<HttpResult>>()
    private var nextId = 1

    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(Int, CPointer<ByteVar>?, Double) -> Unit>> = fn.reinterpret()
        hostSend = { id, json, maxBytes ->
            // cstr 只在 memScoped 内有效，用完即释放
            memScoped { cFunction.invoke(id, json.cstr.ptr, maxBytes) }
        }
    }

    suspend fun send(payloadJson: String, maxBytes: Long): HttpResult {
        val send = hostSend ?: throw IllegalStateException("HTTP 桥未就绪（宿主未注册 registerHttpSender）")
        return withContext(Dispatchers.Main) {
            val id = nextId++
            val deferred = CompletableDeferred<HttpResult>()
            pending[id] = deferred
            try {
                send(id, payloadJson, maxBytes.toDouble())
            } catch (t: Throwable) {
                pending.remove(id)
                throw t
            }
            // 取消时也要摘掉，别等结果回来才清
            try {
                deferred.await()
            } finally {
                pending.remove(id)
            }
        }
    }

    fun onResult(requestId: Int, code: Int, bytes: ByteArray, truncated: Boolean) {
        pending.remove(requestId)?.complete(HttpResult(code, bytes, truncated))
    }
}

/** C++ 在模块注册时把发送入口交过来（见 `napi_init.cpp`）。 */
@CName("NexioOnHttpHostReady")
fun NexioOnHttpHostReady(fn: COpaquePointer?) {
    HttpBridge.onHostReady(fn)
}

/** ArkTS 回传结果；`code <= 0` 表示失败，此时 [data] 是错误信息。 */
@CName("NexioOnHttpResult")
fun NexioOnHttpResult(
    requestId: Int,
    code: Int,
    data: CPointer<ByteVar>?,
    length: Int,
    truncated: Boolean,
) {
    val bytes = if (data == null || length <= 0) ByteArray(0) else data.readBytes(length)
    HttpBridge.onResult(requestId, code, bytes, truncated)
}

actual fun createHttpService(timeouts: HttpTimeouts): HttpService = ArkTsHttpService(timeouts)

private class ArkTsHttpService(private val timeouts: HttpTimeouts) : HttpService {

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBytes: Long,
    ): HttpResult = execute("GET", url, null, null, headers, maxBytes)

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
    ): HttpResult = execute("POST", url, body, contentType, headers, -1)

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
    ): HttpResult = execute(method, url, body, contentType, headers, -1)

    private suspend fun execute(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
        maxBytes: Long,
    ): HttpResult {
        val payload = buildJsonObject {
            put("method", method)
            put("url", url)
            if (body != null) put("body", body)
            if (contentType != null) put("contentType", contentType)
            put("connectTimeout", timeouts.connectSeconds * 1000)
            put("readTimeout", timeouts.readSeconds * 1000)
            putJsonObject("headers") {
                headers.forEach { (name, value) -> put(name, value) }
                if (contentType != null) put("Content-Type", contentType)
            }
        }.toString()
        val result = HttpBridge.send(payload, maxBytes)
        if (result.code <= 0) throw IllegalStateException("网络请求失败：${result.text}")
        return result
    }
}
