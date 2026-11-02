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
import kotlinx.cinterop.reinterpret
import kotlin.math.roundToInt

/**
 * 主界面视差 → ArkUI：推送 `@<centi>`（1/10000 屏宽，有符号）。
 *
 * 顶栏圆钮与底栏胶囊是 ArkUI 的兄弟节点，不在主界面那棵 Compose 树里，必须单独发位移。
 * 转场期间逐帧调用，值不变会被去重。
 */
object MainMotionBridge {

    private var publishFn: ((String) -> Unit)? = null
    private var lastJson: String? = null

    /** C++ 在模块注册时把推送入口交过来（见 `napi_init.cpp`）。 */
    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>> = fn.reinterpret()
        publishFn = { json -> memScoped { cFunction.invoke(json.cstr.ptr) } }
        // 注册可能晚于首次发布：补发一次，否则主界面第一次被推开时宿主不知道位移
        lastJson?.let { json -> publishFn?.invoke(json) }
    }

    /** 转场期间逐帧调用；静止时值不变会被去重掉。 */
    fun publish(shiftFraction: Float) {
        val json = "@" + (shiftFraction * 10000f).roundToInt()
        if (json == lastJson) return
        lastJson = json
        publishFn?.invoke(json)
    }
}

/** C++ 注册时调用。 */
@CName("NexioOnMainMotionHostReady")
fun NexioOnMainMotionHostReady(fn: COpaquePointer?) {
    MainMotionBridge.onHostReady(fn)
}
