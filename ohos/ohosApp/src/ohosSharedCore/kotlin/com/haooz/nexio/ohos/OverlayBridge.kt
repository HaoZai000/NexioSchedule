@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret

/**
 * 「覆盖层要不要显示」→ ArkUI。
 *
 * 两个来源取 **OR**，缺一不可：
 * - [publishMain]：主实例读 `router.hasOverlay` —— push 的瞬间就能变 true；
 * - [publishOverlay]：覆盖层实例经 `AppNavHost.onOverlayActivityChanged` 发布 ——
 *   它在**退出动画播完**（出栈层销毁）之后才变 false。
 *
 * 只用 hasOverlay 的话，pop 一开始覆盖层就被 ArkUI 藏掉，退出动画一帧都看不见 ——
 * 页面直接消失。OR 之后隐藏时机天然落在动画收尾，不需要 ArkTS 数延迟。
 */
object OverlayBridge {

    private var publishFn: ((Boolean) -> Unit)? = null
    private var mainActive: Boolean = false
    private var overlayActive: Boolean = false
    private var lastValue: Boolean? = null

    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(Boolean) -> Unit>> = fn.reinterpret()
        publishFn = { active -> cFunction.invoke(active) }
        // 注册可能晚于首次发布：补发一次，否则宿主不知道该不该显示覆盖层
        lastValue = null
        dispatch()
    }

    /** 主实例：`router.hasOverlay`。 */
    fun publishMain(active: Boolean) {
        mainActive = active
        dispatch()
    }

    /** 覆盖层实例：`AppNavHost.onOverlayActivityChanged`（含退出动画期间）。 */
    fun publishOverlay(active: Boolean) {
        overlayActive = active
        dispatch()
    }

    private fun dispatch() {
        val value = mainActive || overlayActive
        if (value == lastValue) return
        lastValue = value
        publishFn?.invoke(value)
    }
}

/** C++ 注册时调用。 */
@CName("NexioOnOverlayHostReady")
fun NexioOnOverlayHostReady(fn: COpaquePointer?) {
    OverlayBridge.onHostReady(fn)
}
