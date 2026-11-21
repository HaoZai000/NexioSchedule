package com.haooz.chedule.mcp

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.haooz.chedule.data.mcp.CloudMcpConfig as PublicConfig
import com.haooz.chedule.data.mcp.CloudMcpController
import com.haooz.chedule.data.mcp.CloudMcpDownloadPreview
import com.haooz.chedule.data.mcp.CloudMcpStatus
import com.haooz.chedule.data.mcp.CloudMcpUploadPreview

class AndroidCloudMcpController(context: Context) : CloudMcpController {
    private val prefs = CloudMcpPrefs(context)
    private val remote = CloudMcpRemoteOperations()
    override val supported: Boolean get() = true

    override suspend fun load(): PublicConfig {
        val saved = prefs.load() ?: return PublicConfig()
        return PublicConfig(saved.url, saved.token, saved.enabled)
    }

    override suspend fun save(config: PublicConfig) {
        val url = if (config.url.isBlank() && !config.enabled) "" else CloudMcpClient.normalizedEndpoint(config.url)
        require(!config.enabled || config.token.isNotBlank()) { "启用云端连接前请填写 Token" }
        require(config.token.length <= 4096 && config.token.all { it.code in 33..126 }) { "Token 格式不正确" }
        prefs.store(CloudMcpConfig(url, config.token, config.enabled))
    }

    override suspend fun inspect(config: PublicConfig): CloudMcpStatus = remote.inspect(config)
    override suspend fun previewUpload(config: PublicConfig, content: String): CloudMcpUploadPreview =
        remote.previewUpload(config, content)
    override suspend fun confirmUpload(config: PublicConfig, preview: CloudMcpUploadPreview): CloudMcpStatus =
        remote.confirmUpload(config, preview)
    override suspend fun previewDownload(config: PublicConfig): CloudMcpDownloadPreview =
        remote.previewDownload(config)
}

/** 网络操作独立于 Android 存储，便于在宿主机检验请求与确认流程。 */
internal class CloudMcpRemoteOperations(
    private val newClient: (PublicConfig) -> CloudMcpClient = { CloudMcpClient(it) },
) {
    suspend fun inspect(config: PublicConfig): CloudMcpStatus {
        require(config.enabled) { "云端连接已关闭" }
        val client = newClient(config)
        val server = client.initialize()
        val tools = client.tools()
        check("get_sync_status" in tools) { "该服务器不提供个人课表云状态工具" }
        return status(client.tool("get_sync_status"), server, tools.size)
    }

    suspend fun previewUpload(config: PublicConfig, content: String): CloudMcpUploadPreview {
        require(config.enabled) { "请先启用并保存云端连接" }
        require(content.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024) { "课表超过 2 MiB" }
        val document = JsonParser.parseString(content).asJsonObject
        val count = document.getAsJsonArray("courses")?.size() ?: error("课表缺少课程数组")
        val client = newClient(config)
        client.initialize()
        val current = client.tool("get_sync_status")
        val replaces = current.get("hasSchedule")?.asBoolean ?: error("云端课表状态缺失")
        val revision = current.get("revision")?.asLong?.takeIf { it >= 0 } ?: error("云端版本缺失")
        val tool = if (replaces) "update_schedule_file" else "import_schedule_file"
        val result = client.tool(tool, JsonObject().apply {
            addProperty("filename", "手机课表.json")
            addProperty("content", content)
            addProperty("expected_revision", revision)
        })
        check(result.get("requiresConfirmation")?.asBoolean == true) { "服务器未提供安全预览，未自动执行上传" }
        check(result.get("baseRevision")?.asLong == revision) { "云端预览版本不一致，请重新生成预览" }
        val key = result.get("pendingId")?.asString?.takeIf { it.length in 8..256 } ?: error("确认标识无效")
        return CloudMcpUploadPreview(key, tool, revision, count, replaces)
    }

    suspend fun confirmUpload(config: PublicConfig, preview: CloudMcpUploadPreview): CloudMcpStatus {
        require(config.enabled) { "云端连接已关闭" }
        require(preview.toolName in setOf("import_schedule_file", "update_schedule_file")) { "预览工具不正确" }
        val client = newClient(config)
        val server = client.initialize()
        val result = client.tool(preview.toolName, JsonObject().apply {
            addProperty("confirm", true)
            addProperty("pending_id", preview.pendingId)
        })
        check(result.get("ok")?.asBoolean == true) { "云端没有确认保存结果，请刷新状态后再试" }
        return status(result, server, 0)
    }

    /** 拉取云端 nexio 格式快照；只读，不产生 revision 变化。 */
    suspend fun previewDownload(config: PublicConfig): CloudMcpDownloadPreview {
        require(config.enabled) { "云端连接已关闭" }
        val client = newClient(config)
        val server = client.initialize()
        val tools = client.tools()
        check("export_schedule_file" in tools) { "该服务器不提供课表导出工具" }
        val export = client.tool("export_schedule_file")
        check(export.get("hasSchedule")?.asBoolean == true) { "云端还没有课表，先在别处上传一份" }
        val revision = export.get("revision")?.asLong?.takeIf { it >= 0 } ?: error("云端版本缺失")
        val content = export.get("content")?.asString?.takeIf { it.isNotBlank() } ?: error("云端课表正文缺失")
        check(content.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024) { "云端课表超过 2 MiB" }
        val document = JsonParser.parseString(content).asJsonObject
        val courses = document.getAsJsonArray("courses") ?: error("云端课表缺少课程数组")
        val scheduleName = document.get("schedule_name")?.asString
            ?.takeIf { it.isNotBlank() } ?: "云端课表"
        return CloudMcpDownloadPreview(
            revision = revision,
            scheduleName = scheduleName,
            recordCount = courses.size(),
            content = content,
        )
    }

    private fun status(json: JsonObject, server: String, count: Int): CloudMcpStatus = CloudMcpStatus(
        serverName = server.ifBlank { "Nexio Cloud" }, toolCount = count,
        hasSchedule = json.get("hasSchedule")?.asBoolean ?: error("云端课表状态缺失"),
        revision = json.get("revision")?.asLong?.takeIf { it >= 0 } ?: error("云端版本缺失"),
    )
}
