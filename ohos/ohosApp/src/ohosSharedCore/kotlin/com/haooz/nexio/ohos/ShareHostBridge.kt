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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 系统分享 / 剪贴板桥：ShareKit 与 pasteboard 都只能由 ArkTS 调起。
 * 与 Toast 一样，发起必须落到 JS 线程（`Dispatchers.Main`）。
 */
internal object ShareHostBridge {

    private var host: ((String, String) -> Unit)? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun onReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(CPointer<ByteVar>?, CPointer<ByteVar>?) -> Unit>> =
            fn.reinterpret()
        host = { op, payload -> memScoped { cFunction.invoke(op.cstr.ptr, payload.cstr.ptr) } }
    }

    fun request(op: String, payload: String): Boolean {
        val target = host ?: return false
        mainScope.launch { target(op, payload) }
        return true
    }
}

@CName("NexioOnShareHostReady")
fun NexioOnShareHostReady(fn: COpaquePointer?) {
    ShareHostBridge.onReady(fn)
}
