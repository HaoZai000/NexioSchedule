@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import com.haooz.chedule.ui.utils.NativeTopBarHost
import com.haooz.chedule.ui.utils.NativeTopBarState
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 顶栏右上角两颗按钮的语义 → ArkUI。
 *
 * 标题、星期行、渐进模糊都在 Compose 里画；只有这两颗按钮和它们的菜单交给 ArkUI
 * （要的是系统沉浸光感材质 + 原生菜单）。这里只负责把 [NativeTopBarState] 编成 JSON
 * 推过去，把宿主点到的按钮 id 转回 [NativeTopBarHost.onAction]。
 */
object TopBarBridge {

    private var publishFn: ((String) -> Unit)? = null
    private var host: NativeTopBarHost? = null
    private var scope: CoroutineScope? = null
    private var lastJson: String? = null

    /** C++ 在模块注册时把推送入口交过来（见 `napi_init.cpp`）。 */
    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>> = fn.reinterpret()
        publishFn = { json -> memScoped { cFunction.invoke(json.cstr.ptr) } }
    }

    fun bind(host: NativeTopBarHost, scope: CoroutineScope) {
        this.host = host
        this.scope = scope
    }

    /** 由 Compose 的 `snapshotFlow` 在 UI 线程调用。 */
    fun publish(state: NativeTopBarState?) {
        val fn = publishFn ?: return
        val json = encode(state)
        if (json == lastJson) return
        lastJson = json
        fn(json)
    }

    /** ArkTS 的动作回调（JS 线程）→ 回 Compose 状态写入，必须走 Main。 */
    fun dispatch(action: String) {
        val scope = scope ?: return
        val host = host ?: return
        scope.launch(Dispatchers.Main) { host.onAction?.invoke(action) }
    }

    private fun encode(state: NativeTopBarState?): String {
        if (state == null) return "null"
        return buildJsonObject {
            put("showsSwitch", state.showsSwitch)
            put("showsMore", state.showsMore)
            put("onSurface", state.onSurfaceArgb)
            put("topDp", state.topDp.toDouble())
            put("sizeDp", state.sizeDp.toDouble())
            put("spacingDp", state.spacingDp.toDouble())
            put("rightPadDp", state.rightPadDp.toDouble())
            putJsonArray("menu") {
                state.menu.forEach { item ->
                    addJsonObject {
                        put("id", item.id)
                        put("label", item.label)
                    }
                }
            }
        }.toString()
    }
}

/** C++ 注册时调用。 */
@CName("NexioOnTopBarHostReady")
fun NexioOnTopBarHostReady(fn: COpaquePointer?) {
    TopBarBridge.onHostReady(fn)
}

/** ArkTS 点了顶栏按钮。 */
@CName("NexioOnTopBarAction")
fun NexioOnTopBarAction(action: CPointer<ByteVar>?) {
    val name = action?.toKString() ?: return
    TopBarBridge.dispatch(name)
}
