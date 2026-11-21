package com.haooz.chedule.data.mcp

import com.haooz.chedule.data.storage.AppStorage

/**
 * MCP 服务设置。
 *
 * Token 可留空：留空时不校验 `Authorization`，仅靠「只监听回环地址」隔离；
 * 填写后请求头必须带上同一个值（`Bearer` 前缀可有可无）。
 */
object McpServicePrefs {

    private const val STORE = "mcp_service"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TOKEN = "token"

    fun isEnabled(): Boolean = AppStorage.store(STORE).getBoolean(KEY_ENABLED, false)

    fun token(): String = AppStorage.store(STORE).getString(KEY_TOKEN, "")

    fun setEnabled(enabled: Boolean) {
        AppStorage.store(STORE).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun setToken(token: String) {
        AppStorage.store(STORE).edit { putString(KEY_TOKEN, token.trim()) }
    }
}
