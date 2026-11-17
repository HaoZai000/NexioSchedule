package com.haooz.chedule.data.mcp

/**
 * 共享 UI 与服务进程之间的桥。
 *
 * Android 在 `NexioApplication` 里装配；其它平台保持默认的「不支持」，页面只做说明展示。
 */
object McpServiceBridge {

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
