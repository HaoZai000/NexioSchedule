package com.haooz.chedule.ui.web

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 在页面里跑一段 JS 并等结果（超时回 null）。
 *
 * AI 教务导入的每个工具都是「注入脚本 → 等值 → 交给模型」，所以统一从这里走：
 * 平台差异只在 [WebHost.evaluateJavascriptWithResult]，超时与协程语义留在这里一份。
 *
 * @param timeoutMs 页面脚本自己也要有预算 —— 工具侧一般给 15s，读大页面够用；
 *   接口查询由 JS 内部的 AbortController 先兜（见脚本侧 8s），这里只是最终保险。
 */
suspend fun WebHost.evaluateJavascriptAwait(
    js: String,
    timeoutMs: Long = 15_000L,
): String? = withTimeoutOrNull(timeoutMs) {
    suspendCancellableCoroutine { continuation ->
        evaluateJavascriptWithResult(js) { result ->
            // 平台可能回调两次（例如 iOS 的 completion 与兜底分支），只认第一次
            if (continuation.isActive) continuation.resume(result)
        }
    }
}
