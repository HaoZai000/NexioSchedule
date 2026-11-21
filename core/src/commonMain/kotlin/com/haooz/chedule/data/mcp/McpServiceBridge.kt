package com.haooz.chedule.data.mcp

import kotlin.concurrent.Volatile

/**
 * 共享 UI 与服务进程之间的桥。
 *
 * Android 在 `NexioApplication` 里装配；其它平台保持默认的「不支持」，页面只做说明展示。
 */
object McpServiceBridge {

    /** 测试构建使用不同端口，可与正式版本地服务并存。 */
    @Volatile
    var port: Int = McpToolCatalog.PORT

    val serverUrl: String get() = "http://127.0.0.1:$port/mcp"

    /** 当前平台是否提供本地 MCP 服务（目前仅 Android）。 */
    @Volatile
    var supported: Boolean = false

    /** 启动保活服务；参数是当前 Token（可为空）。 */
    @Volatile
    var start: (String) -> Unit = {}

    /** 停止保活服务。 */
    @Volatile
    var stop: () -> Unit = {}

    /** 服务是否正在运行（由平台侧维护）。 */
    @Volatile
    var running: Boolean = false
}
