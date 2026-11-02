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

/**
 * 二三四级页的原生顶栏语义 → ArkUI。
 *
 * 复用 iOS 的 per-route 协议（`NativeDocumentChrome`）：每个路由层一个 chrome 实例，
 * 页面把自己的标题 / 返回 / 动作写进去，这里按层编成 JSON 推给宿主。两条通道：
 * - **内容**：`[{key,title,tint,back,leading,action,secondary}]`，只在语义变化时发；
 * - **位移**：`[[key,centi],…]`，转场期间逐帧发。fraction 公式与 `AppNavHost` 的
 *   `PageLayer` 完全一致（栈顶 `1-p`、次顶 `-NAV_PARALLAX*p`），原生顶栏按钮才能
 *   跟着自己的页面同帧滑动，而不是凭空出现/消失。
 *
 * 宿主点按钮走 [dispatch]（`key` + 角色）转回 Compose。
 */
object DocChromeBridge {

    private var chromeFn: ((String) -> Unit)? = null
    private var motionFn: ((String) -> Unit)? = null
    private var lastChrome: String? = null
    private var lastMotion: String? = null

    /** ArkTS 点了某一层的按钮（JS 线程）；宿主在 OhosEntry 里接。 */
    var onAction: ((key: String, role: String) -> Unit)? = null

    /** C++ 在模块注册时把两个推送入口交过来（见 `napi_init.cpp`）。 */
    fun onChromeHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>> = fn.reinterpret()
        chromeFn = { json -> memScoped { cFunction.invoke(json.cstr.ptr) } }
        // 注册可能晚于首次发布：补发最后一次内容，否则宿主要等下一次语义变化才有东西可画
        lastChrome?.let { cFunction.invokeJson(it) }
    }

    fun onMotionHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>> = fn.reinterpret()
        motionFn = { json -> memScoped { cFunction.invoke(json.cstr.ptr) } }
        lastMotion?.let { cFunction.invokeJson(it) }
    }

    /** 内容变化时调用；相同 JSON 不重发。 */
    fun publishChrome(json: String) {
        if (json == lastChrome) return
        lastChrome = json
        chromeFn?.invoke(json)
    }

    /** 转场期间逐帧调用；静止时值不变会被去重掉。 */
    fun publishMotion(json: String) {
        if (json == lastMotion) return
        lastMotion = json
        motionFn?.invoke(json)
    }

    fun dispatch(key: String, role: String) {
        onAction?.invoke(key, role)
    }

    private fun CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>>.invokeJson(json: String) {
        memScoped { invoke(json.cstr.ptr) }
    }
}

/** C++ 注册时调用（内容通道）。 */
@CName("NexioOnDocChromeHostReady")
fun NexioOnDocChromeHostReady(fn: COpaquePointer?) {
    DocChromeBridge.onChromeHostReady(fn)
}

/** C++ 注册时调用（位移通道）。 */
@CName("NexioOnDocChromeMotionHostReady")
fun NexioOnDocChromeMotionHostReady(fn: COpaquePointer?) {
    DocChromeBridge.onMotionHostReady(fn)
}

/** ArkTS 点了某一层顶栏的按钮：`role` = back / leading / action / secondary。 */
@CName("NexioOnDocChromeAction")
fun NexioOnDocChromeAction(key: CPointer<ByteVar>?, role: CPointer<ByteVar>?) {
    val k = key?.toKString() ?: return
    val r = role?.toKString() ?: return
    DocChromeBridge.dispatch(k, r)
}
