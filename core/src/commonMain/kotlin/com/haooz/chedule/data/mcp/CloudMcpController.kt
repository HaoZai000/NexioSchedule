package com.haooz.chedule.data.mcp

/** 云端连接与本地监听独立；配置不属于课表数据，不得放进导出包。 */
class CloudMcpConfig(val url: String = "", val token: String = "", val enabled: Boolean = false) {
    override fun toString(): String = "CloudMcpConfig(enabled=$enabled)"
}

data class CloudMcpStatus(
    val serverName: String,
    val toolCount: Int,
    val hasSchedule: Boolean,
    val revision: Long,
)

/** 只持有短期确认标识及说明，不包含 Token 或完整课程。 */
data class CloudMcpUploadPreview(
    val pendingId: String,
    val toolName: String,
    val baseRevision: Long,
    val recordCount: Int,
    val replacesExisting: Boolean,
)

/** 下载的是固定版本快照；课程正文只保留在当前页面内，不进入日志。 */
class CloudMcpDownloadPreview(
    val revision: Long,
    val scheduleName: String,
    val recordCount: Int,
    val content: String,
) {
    override fun toString(): String = "CloudMcpDownloadPreview(revision=$revision, recordCount=$recordCount)"
}

interface CloudMcpController {
    val supported: Boolean get() = false
    suspend fun load(): CloudMcpConfig = CloudMcpConfig()
    suspend fun save(config: CloudMcpConfig) { error("Cloud MCP is unavailable") }
    suspend fun inspect(config: CloudMcpConfig): CloudMcpStatus { error("Cloud MCP is unavailable") }
    suspend fun previewUpload(config: CloudMcpConfig, content: String): CloudMcpUploadPreview {
        error("Cloud MCP is unavailable")
    }
    suspend fun confirmUpload(config: CloudMcpConfig, preview: CloudMcpUploadPreview): CloudMcpStatus {
        error("Cloud MCP is unavailable")
    }
    /** 拉取云端课表快照；返回的 content 只进内存，调用方确认后才落库。 */
    suspend fun previewDownload(config: CloudMcpConfig): CloudMcpDownloadPreview {
        error("Cloud MCP is unavailable")
    }
}

/** Android 注入加密存储和专用 HTTP 客户端，其它平台不显示不可用的按钮。 */
object CloudMcpBridge {
    var controller: CloudMcpController = object : CloudMcpController {}
}
