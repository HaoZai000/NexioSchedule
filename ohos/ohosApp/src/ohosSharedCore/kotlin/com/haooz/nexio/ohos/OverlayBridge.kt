@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret

/**
 * 「当前有没有二级页」→ ArkUI。
 *
 * 覆盖层是一个独立的 Compose 实例，ArkUI 只有知道有没有子页，才能决定把那一层
 * 显出来（有子页 = 排在最上层，真的压住顶栏与底栏）还是藏起来（无子页 = 完全不参与
 * 命中测试，主界面照常收手势）。语义只有这一个布尔，页面内容仍全在 Compose 里。
 */
object OverlayBridge {

    private var publishFn: ((Boolean) -> Unit)? = null
    private var lastValue: Boolean? = null

    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(Boolean) -> Unit>> = fn.reinterpret()
        publishFn = { active -> cFunction.invoke(active) }
        // 注册可能晚于首次发布：补发一次，否则宿主不知道该不该显示覆盖层
        lastValue?.let { lastValue = null; publish(it) }
    }

    /** 由 Compose 的 apply 阶段同步调用。 */
    fun publish(active: Boolean) {
        if (active == lastValue) return
        lastValue = active
        publishFn?.invoke(active)
    }
}

/** C++ 注册时调用。 */
@CName("NexioOnOverlayHostReady")
fun NexioOnOverlayHostReady(fn: COpaquePointer?) {
    OverlayBridge.onHostReady(fn)
}
