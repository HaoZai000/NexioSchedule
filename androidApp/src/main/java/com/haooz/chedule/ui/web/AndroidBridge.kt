/** Android 桥接层 - WebView 与原生代码通信接口（JS 侧入口 window.AndroidBridge） */
package com.haooz.chedule.ui.web

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.ImportStr

private const val TAG = "AndroidBridge"

/**
 * AndroidBridge：处理 WebView 与 Native 代码的通信。
 * JS 脚本通过 window.AndroidBridge 调用这些方法；带 promiseId 的接口是异步的，
 * 结果经 window._resolveAndroidPromise / _rejectAndroidPromise 回传（桥定义在
 * ui-shared 的 JsPromiseBridge.kt，两端共用）。
 */
class AndroidBridge(
    private val context: Context,
    private val webView: WebView,
    private val onCourseImported: (List<Course>) -> Unit,
    private val onTaskCompleted: () -> Unit,
    private val onShowAlert: ((title: String, content: String, confirmText: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
    private val onShowPrompt: ((title: String, tip: String, defaultText: String, validatorJs: String, onResult: (String?) -> Unit) -> Unit)? = null,
    private val onShowSingleSelection: ((title: String, items: List<String>, defaultIndex: Int, onResult: (Int?) -> Unit) -> Unit)? = null
) {
    private val gson: Gson = GsonBuilder()
        .create()
    private val handler = Handler(Looper.getMainLooper())
    private var currentToast: Toast? = null

    /** 是否启用 POST 加头 + OkHttp 转发（默认 false：POST 走原生，避免 WAF 拒带 X-WebView-Post-Id 的请求） */
    @Volatile
    private var postForwardEnabled: Boolean = false

    // ── JS 读取 / App 侧设置 ──────────────────────────────────────────

    /** JS 读取：POST 是否走转发通道 */
    @JavascriptInterface
    fun isPostForwardEnabled(): Boolean = postForwardEnabled

    /**
     * AI 教务导入的网络记录器入口。
     *
     * 记录器在 document-start 就挂上，此时 UI 可能还没就绪；这里只做无锁入队，
     * 解析与截断都在 [EduNetLogStore] 里完成，异常一律吞掉（不能影响页面本身）。
     */
    @JavascriptInterface
    fun netLog(json: String) {
        runCatching { recordEduNetLog(json) }
    }

    /** App 侧按需开启（个别必须转发的学校/适配器） */
    fun setPostForwardEnabled(enabled: Boolean) {
        postForwardEnabled = enabled
    }

    /**
     * 负载解析 / 落盘 / 作息调用顺序全部在 :ui-shared 的 [EduImportBridgeCore] 里 ——
     * iOS 的 WKWebView 走同一份，两端各写一遍必然漂移。本类只留平台活：
     * 对话框三件套、toast、POST 转发开关、Promise 兑现。
     */
    private val core = EduImportBridgeCore(
        onCoursesImported = onCourseImported,
        onTaskCompleted = onTaskCompleted,
    )

    /** 设置导入的目标课表ID（每次执行导入脚本前都会调一次，顺带重置作息调用顺序状态） */
    fun setImportTableId(tableId: String?) {
        core.setImportTableId(tableId)
    }

    // ── 对话框三件套（结果经 promiseId 回传 JS）──────────────────────

    /** JS 调用：显示短暂的 Toast 消息 */
    @JavascriptInterface
    fun showToast(message: String) {
        handler.post {
            currentToast?.cancel()
            val newToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            newToast.show()
            currentToast = newToast
        }
    }

    /** JS 调用：显示 Alert 弹窗 */
    @JavascriptInterface
    fun showAlert(titleText: String, contentText: String, confirmText: String, promiseId: String) {
        handler.post {
            val callback = onShowAlert
            if (callback != null) {
                callback(titleText, contentText, confirmText) { confirmed ->
                    resolveJsPromise(promiseId, if (confirmed) "true" else "false")
                }
            } else {
                resolveJsPromise(promiseId, "true")
            }
        }
    }

    /** JS 调用：显示 Prompt 弹窗 */
    @JavascriptInterface
    fun showPrompt(
        titleText: String,
        tipText: String,
        defaultText: String,
        validatorJsFunction: String,
        promiseId: String
    ) {
        handler.post {
            val callback = onShowPrompt
            if (callback != null) {
                callback(titleText, tipText, defaultText, validatorJsFunction) { input ->
                    if (input != null) {
                        if (validatorJsFunction.isNullOrEmpty()) {
                            val escaped = input.replace("'", "\\'")
                            resolveJsPromise(promiseId, "'$escaped'")
                        } else {
                            // ⚠ 不要加 "javascript:" 前缀：`evaluateJavascript` 收的是
                            // JS 源码不是 URL，前缀只是被当成 label 侥幸能跑，别依赖它。
                            val jsScript = "${validatorJsFunction}('${input.replace("'", "\\'")}')"
                            webView.evaluateJavascript(jsScript) { result ->
                                val error = result?.trim('"')
                                if (error.isNullOrEmpty() || error.equals("false", ignoreCase = true)) {
                                    val escaped = input.replace("'", "\\'")
                                    resolveJsPromise(promiseId, "'$escaped'")
                                } else {
                                    handler.post {
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                    rejectJsPromise(promiseId, error)
                                }
                            }
                        }
                    } else {
                        resolveJsPromise(promiseId, "null")
                    }
                }
            } else {
                val escapedInput = defaultText.replace("'", "\\'")
                resolveJsPromise(promiseId, "'$escapedInput'")
            }
        }
    }

    /** JS 调用：显示单选列表弹窗 */
    @JavascriptInterface
    fun showSingleSelection(
        titleText: String,
        itemsJsonString: String,
        defaultSelectedIndex: Int,
        promiseId: String
    ) {
        handler.post {
            try {
                val items = gson.fromJson<List<String>>(
                    itemsJsonString,
                    object : TypeToken<List<String>>() {}.type
                )
                val callback = onShowSingleSelection
                if (callback != null) {
                    callback(titleText, items, defaultSelectedIndex) { selectedIndex ->
                        if (selectedIndex != null) {
                            resolveJsPromise(promiseId, selectedIndex.toString())
                        } else {
                            resolveJsPromise(promiseId, "null")
                        }
                    }
                } else {
                    resolveJsPromise(promiseId, defaultSelectedIndex.toString())
                }
            } catch (e: Exception) {
                NexioLog.e(TAG, AndroidMiscStr.logParseSelectionFailedN(e.message), e)
                Toast.makeText(context, AndroidMiscStr.selectionDataError(), Toast.LENGTH_LONG).show()
                rejectJsPromise(promiseId, AndroidMiscStr.selectionJsonInvalidN(e.message))
            }
        }
    }

    /** JS 调用：将课程数据传回原生保存 */
    @JavascriptInterface
    fun saveImportedCourses(coursesJsonString: String, promiseId: String) {
        NexioLog.d(TAG, AndroidMiscStr.logReceivedCoursesN(coursesJsonString.length / 1024))
        handler.post { dispatch("saveImportedCourses", coursesJsonString, promiseId, AndroidMiscStr.courseImportSuccess()) }
    }

    /** JS 调用：将课表配置数据传回原生保存 */
    @JavascriptInterface
    fun saveCourseConfig(configJsonString: String, promiseId: String) {
        handler.post { dispatch("saveCourseConfig", configJsonString, promiseId, AndroidMiscStr.configImportSuccess()) }
    }

    /** JS 调用：将预设时间段数据传回原生保存 */
    @JavascriptInterface
    fun savePresetTimeSlots(timeSlotsJsonString: String, promiseId: String) {
        handler.post { dispatch("savePresetTimeSlots", timeSlotsJsonString, promiseId, AndroidMiscStr.timeSlotsImportSuccess()) }
    }

    /** JS 调用：将组合作息方案（官方 1.4 comboSchedule）数据传回原生保存 */
    @JavascriptInterface
    fun saveComboSchedule(comboJsonString: String, promiseId: String) {
        handler.post { dispatch("saveComboSchedule", comboJsonString, promiseId, AndroidMiscStr.comboImportSuccess()) }
    }

    /** JS 调用：通知 Native 端 JS 任务已逻辑完成 */
    @JavascriptInterface
    fun notifyTaskCompletion() {
        handler.post { dispatch("notifyTaskCompletion", "", null, null) }
    }

    // ── 私有工具 ────────────────────────────────────────────────────

    /**
     * 统一走 :ui-shared 的 [EduImportBridgeCore]：契约拒绝与解析失败都转成 Promise reject，
     * 成功才 toast。平台侧不再自己解析脚本负载（两端各写一遍必然漂移）。
     */
    private fun dispatch(method: String, payload: String, promiseId: String?, okToast: String?) {
        val result = try {
            core.handle(method, listOf(payload))
        } catch (e: EduBridgeReject) {
            NexioLog.w(TAG, AndroidMiscStr.logMethodRejectedN(method, e.message))
            Toast.makeText(context, e.message ?: AndroidMiscStr.importFailed(), Toast.LENGTH_LONG).show()
            promiseId?.let { rejectJsPromise(it, e.message ?: AndroidMiscStr.importFailed()) }
            return
        } catch (e: Exception) {
            NexioLog.e(TAG, AndroidMiscStr.logMethodExceptionN(method), e)
            Toast.makeText(context, ImportStr.importFailedReason(e.message), Toast.LENGTH_LONG).show()
            promiseId?.let { rejectJsPromise(it, ImportStr.importFailedReason(e.message)) }
            return
        }
        if (okToast != null) Toast.makeText(context, okToast, Toast.LENGTH_SHORT).show()
        promiseId?.let { resolveJsPromise(it, result ?: "true") }
    }

    private fun resolveJsPromise(promiseId: String, result: String) {
        webView.evaluateJavascript("window._resolveAndroidPromise('$promiseId', $result);", null)
    }

    private fun rejectJsPromise(promiseId: String, error: String) {
        val escapedError = error.replace("'", "\\'")
        webView.evaluateJavascript("window._rejectAndroidPromise('$promiseId', '$escapedError');", null)
    }

    // 脚本负载的解析与 JSON 模型已随逻辑一起上提到 :ui-shared 的 EduImportBridgeCore
    // （kotlinx-serialization 逐字段读 —— Kotlin/Native 上没有 Gson 反射）。
}
