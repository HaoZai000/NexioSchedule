@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import com.haooz.chedule.ui.utils.NativeBottomBarHost
import com.haooz.chedule.ui.utils.NativeBottomBarState
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 底部导航语义 → ArkUI。
 *
 * 只负责把 [NativeBottomBarState] 编成 JSON 推过去，把宿主点到的下标转回
 * [NativeBottomBarHost.onTabSelected]（`index >= tabsCount` = 末尾的设置页签）。
 */
object BottomBarBridge {

    private var publishFn: ((String) -> Unit)? = null
    private var host: NativeBottomBarHost? = null
    private var scope: CoroutineScope? = null
    private var lastJson: String? = null
    private var lastState: NativeBottomBarState? = null
    private var hasPublished = false

    /** C++ 在模块注册时把推送入口交过来（见 `napi_init.cpp`）。 */
    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>> = fn.reinterpret()
        publishFn = { json -> memScoped { cFunction.invoke(json.cstr.ptr) } }
        // 注册可能晚于首次发布：补发一次，否则要等下一次状态变化宿主才有东西可画
        if (hasPublished) {
            lastJson = null
            publish(lastState)
        }
    }

    fun bind(host: NativeBottomBarHost, scope: CoroutineScope) {
        this.host = host
        this.scope = scope
    }

    /** 由 Compose 的 apply 阶段同步调用。 */
    fun publish(state: NativeBottomBarState?) {
        hasPublished = true
        lastState = state
        val fn = publishFn ?: return
        val json = encode(state)
        if (json == lastJson) return
        lastJson = json
        fn(json)
    }

    /** ArkTS 的点击回调（JS 线程）→ 回 Compose 切页，必须走 Main。 */
    fun dispatch(index: Int) {
        val scope = scope ?: return
        val host = host ?: return
        scope.launch(Dispatchers.Main) { host.onTabSelected?.invoke(index) }
    }

    private fun encode(state: NativeBottomBarState?): String {
        if (state == null) return "null"
        return buildJsonObject {
            put("selected", state.selectedTab)
            put("tabsCount", state.tabsCount)
            putJsonArray("labels") { state.labels.forEach { add(it) } }
            put("onSurface", state.onSurfaceArgb)
            put("accent", state.accentArgb)
            put("heightDp", state.heightDp.toDouble())
        }.toString()
    }
}

/** C++ 注册时调用。 */
@CName("NexioOnBottomBarHostReady")
fun NexioOnBottomBarHostReady(fn: COpaquePointer?) {
    BottomBarBridge.onHostReady(fn)
}

/** ArkTS 点了某一格（`index >= tabsCount` = 设置）。 */
@CName("NexioOnBottomBarSelected")
fun NexioOnBottomBarSelected(index: Int) {
    BottomBarBridge.dispatch(index)
}
