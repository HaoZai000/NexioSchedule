@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.school.ScriptRepository
import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.optDouble
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.i18n.L
import com.haooz.chedule.ui.web.EduBridgeReject
import com.haooz.chedule.ui.web.EduImportBridgeCore
import com.haooz.chedule.ui.web.JS_PROMISE_BRIDGE
import com.haooz.chedule.ui.web.WebHost
import com.haooz.chedule.ui.web.WebHostConfig
import com.haooz.chedule.ui.web.WebHostEvents
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 网页导入宿主 —— 鸿蒙端用 ArkUI 的 `Web` 组件（Kotlin 侧没有 WebView）。
 *
 * 与 Android / iOS 的关键差异（架构事实，不是偷懒）：
 * - 网页是 ArkUI 的兄弟节点，画在覆盖层 Compose 之上、原生顶栏之下。所以
 *   [Content] 是空实现 —— 页面把「壳」照常组合出来，网页本体由宿主叠上去。
 * - 因为网页压在 Compose 之上，页面里的底部胶囊与三个对话框会被盖住：底部胶囊与
 *   `alert/prompt/单选` 都由 ArkTS 原生画；这里只保留课程落库与流程收尾（共享内核）。
 */
private object WebHostBridge {

    private var hostCommand: ((String) -> Unit)? = null

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun onReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>> = fn.reinterpret()
        hostCommand = { json -> memScoped { cFunction.invoke(json.cstr.ptr) } }
    }

    fun send(json: String) {
        val host = hostCommand ?: return
        mainScope.launch { host(json) }
    }
}

/** C++ 在模块注册时把发起入口交过来（见 `napi_init.cpp`）。 */
@CName("NexioOnWebHostReady")
fun NexioOnWebHostReady(fn: COpaquePointer?) {
    WebHostBridge.onReady(fn)
}

/** ArkTS 回传：`webHostEvent(json)`。 */
@CName("NexioOnWebHostEvent")
fun NexioOnWebHostEvent(json: CPointer<ByteVar>?) {
    activeController?.onEvent(json?.toKString() ?: "")
}

/** 当前页面唯一的宿主控制器（同时只可能有一个网页导入页）。 */
private var activeController: OhosWebHostController? = null

/**
 * 网页页要关闭时的真正动作（路由注册的 `popBack`）。
 *
 * 顶栏按钮由 ArkTS 画，点「返回」时如果网页没有历史，就把关闭请求转回来。
 */
actual var ohosWebRequestClose: (() -> Unit)? = null

/**
 * 退出前先让 ArkUI 播放退场，再让 Compose 出栈。
 *
 * 反过来做（先出栈、dispose 后才通知）会出现「按钮先消失、页面后滑走」。
 */
actual fun ohosWebPrepareExit() {
    WebHostBridge.send("{\"op\":\"exit\"}")
}

@Composable
actual fun rememberOhosWebHost(config: WebHostConfig, events: WebHostEvents): WebHost {
    val currentConfig by rememberUpdatedState(config)
    val currentEvents by rememberUpdatedState(events)
    val controller = remember { OhosWebHostController() }

    controller.events = currentEvents
    controller.resourceFolder = config.resourceFolder
    controller.assetJsPath = config.assetJsPath

    LaunchedEffect(Unit) {
        activeController = controller
        controller.open(currentConfig)
    }

    LaunchedEffect(config.isDesktopMode) {
        if (controller.opened) controller.sendDesktopMode(currentConfig.isDesktopMode)
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.close()
            if (activeController === controller) activeController = null
        }
    }

    return remember(controller) {
        object : WebHost {
            // 网页本体是 ArkUI 节点，Compose 侧没有可画的内容
            @Composable
            override fun Content(modifier: Modifier) = Unit

            override fun reload() = controller.reload()

            override fun canGoBack(): Boolean = controller.canGoBack

            override fun goBack() = controller.goBack()

            override fun currentHostOrNull(): String? = controller.currentHost

            override fun evaluateJavascript(js: String) = controller.evaluateJavascript(js)

            override fun setImportTableId(tableId: String) = controller.core.setImportTableId(tableId)
        }
    }
}

private class OhosWebHostController {

    var events: WebHostEvents = WebHostEvents()
    var resourceFolder: String = ""
    var assetJsPath: String? = null

    var canGoBack: Boolean = false
        private set
    var currentHost: String? = null
        private set

    var opened: Boolean = false
        private set

    private var desktopMode = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 负载解析 / 落盘 / 作息顺序约束全在共享内核里（Android、iOS 同一份） */
    val core = EduImportBridgeCore(
        onCoursesImported = { events.onCoursesImported(it) },
        onTaskCompleted = { events.onTaskCompleted() },
    )

    fun open(config: WebHostConfig) {
        opened = true
        desktopMode = config.isDesktopMode
        val payload = buildJsonObject {
            put("op", "open")
            put("url", config.importUrl.orEmpty())
            put("desktop", config.isDesktopMode)
            put("schoolName", config.schoolName)
            put("currentSchedule", config.currentScheduleName)
            // ⚠ 必须是 JSON 数组：写成字符串会让 ArkTS 侧 ForEach 拿到 string 直接崩
            putJsonArray("schedules") {
                config.scheduleNames.forEach { add(it) }
            }
            // 脚本可能在页面早期就调桥：Promise 基础设施必须随 open 一起注入
            put("promiseBridgeJs", JS_PROMISE_BRIDGE)
        }
        WebHostBridge.send(payload.toString())
    }

    fun sendDesktopMode(desktop: Boolean) {
        desktopMode = desktop
        WebHostBridge.send(
            buildJsonObject {
                put("op", "desktop")
                put("desktop", desktop)
            }.toString()
        )
    }

    fun close() {
        opened = false
        WebHostBridge.send("{\"op\":\"close\"}")
    }

    fun reload() = WebHostBridge.send("{\"op\":\"reload\"}")

    fun goBack() = WebHostBridge.send("{\"op\":\"goBack\"}")

    fun evaluateJavascript(js: String) {
        WebHostBridge.send(
            buildJsonObject {
                put("op", "evalJs")
                put("js", js)
            }.toString()
        )
    }

    // ── ArkTS → Kotlin ──────────────────────────────────────────────

    fun onEvent(json: String) {
        val obj = runCatching { parseJsonObject(json) }.getOrNull() ?: return
        when (obj.optString("event")) {
            "title" -> events.onTitle(obj.optString("title"))
            "progress" -> events.onProgress(obj.optDouble("progress", 0.0).toFloat())
            "state" -> {
                canGoBack = obj.optBoolean("canGoBack")
                currentHost = obj.optString("host").takeIf { it.isNotEmpty() }
            }
            "import" -> executeImport(obj.optString("tableId"))
            // 顶栏返回：网页没有历史时交给路由出栈
            "back" -> if (canGoBack) goBack() else ohosWebRequestClose?.invoke()
            "bridge" -> handleBridge(
                method = obj.optString("method"),
                payload = obj.optString("payload"),
                promiseId = obj.optString("promiseId").takeIf { it.isNotEmpty() },
            )
        }
    }

    /**
     * 脚本 → 原生：课程 / 配置 / 时间段 / 组合作息 / notifyTaskCompletion。
     * 对话框三件套在 ArkTS 侧就兑现了，不会走到这里。
     */
    private fun handleBridge(method: String, payload: String, promiseId: String?) {
        try {
            val result = core.handle(method, listOf(payload))
            if (result == null) {
                reject(promiseId, "unsupported: $method")
            } else {
                resolve(promiseId, result)
            }
        } catch (e: EduBridgeReject) {
            val message = e.message ?: L("导入失败", "Import failed")()
            showToast(message)
            reject(promiseId, message)
        } catch (e: Exception) {
            reject(promiseId, ImportStr.importFailedReason(e.message ?: ""))
        }
    }

    /**
     * 原生底栏点了「执行导入」：选中的目标课表由 ArkTS 的弹窗给出。
     *
     * 与 `WebViewScreen.executeImportWithTable` 同一套流程（脚本落盘 → 注入执行），
     * 只是触发点在原生层。
     */
    private fun executeImport(tableId: String) {
        val path = assetJsPath
        if (path == null) {
            showToast(com.haooz.chedule.i18n.SchedStr.noImportScript(), longDuration = true)
            return
        }
        core.setImportTableId(tableId)
        scope.launch {
            val scriptFile = ScriptRepository(ScriptRepository.getRepoUrl())
                .ensureScript(resourceFolder, path)
            if (scriptFile == null) {
                showToast(com.haooz.chedule.i18n.SchedStr.scriptDownloadFailed(path), longDuration = true)
                return@launch
            }
            val js = "window.currentTableId = ${JsonPrimitive(tableId)};\n${scriptFile.readText()}"
            evaluateJavascript(js)
            showToast(com.haooz.chedule.i18n.SchedStr.runningImportScript())
        }
    }

    private fun resolve(promiseId: String?, result: String) {
        val id = promiseId ?: return
        WebHostBridge.send(
            buildJsonObject {
                put("op", "resolve")
                put("promiseId", id)
                put("result", result)
            }.toString()
        )
    }

    private fun reject(promiseId: String?, error: String) {
        val id = promiseId ?: return
        WebHostBridge.send(
            buildJsonObject {
                put("op", "reject")
                put("promiseId", id)
                put("error", error)
            }.toString()
        )
    }
}
