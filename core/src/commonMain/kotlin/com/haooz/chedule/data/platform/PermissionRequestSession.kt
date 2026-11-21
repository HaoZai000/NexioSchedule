package com.haooz.chedule.data.platform

/**
 * 同一调度线程上的单次授权会话。平台层负责监听和计时，结束时先清理再回调，
 * 因而超时、服务死亡、迟到的授权结果只能完成一次，也允许回调里发起新请求。
 */
class PermissionRequestSession(private val onFinished: () -> Unit) {
    private var callback: ((Boolean) -> Unit)? = null
    private var requestId = 0

    val isPending: Boolean get() = callback != null

    /** 重复请求不覆盖正在等待的调用方。 */
    fun begin(onResult: (Boolean) -> Unit): Int? {
        if (isPending) return null
        requestId++
        callback = onResult
        return requestId
    }

    fun complete(id: Int, granted: Boolean): Boolean {
        if (id != requestId) return false
        val result = callback ?: return false
        callback = null
        onFinished()
        result(granted)
        return true
    }

    /** 页面离开时释放回调而不回调已销毁的页面。 */
    fun cancel(id: Int) {
        if (id != requestId || !isPending) return
        callback = null
        onFinished()
    }
}
