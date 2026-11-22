package com.haooz.chedule.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.haooz.chedule.data.mcp.CloudMcpConfig as PublicConfig
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 仅接入个人课表云的无状态 JSON MCP，不把凭据交给默认重定向客户端。 */
class CloudMcpClient internal constructor(
    config: PublicConfig,
    private val httpClient: OkHttpClient,
) {
    constructor(config: PublicConfig) : this(config, client)

    init {
        require(config.enabled) { "云端连接已关闭" }
    }

    private val endpoint = normalizedEndpoint(config.url)
    private val token = config.token.trim().also {
        require(it.length in 1..4096 && it.all { char -> char.code in 33..126 }) { "Token 格式不正确" }
    }
    private var version = "2025-11-25"

    suspend fun initialize(): String {
        val result = rpc("initialize", JsonObject().apply {
            addProperty("protocolVersion", version)
            add("capabilities", JsonObject())
            add("clientInfo", JsonObject().apply {
                addProperty("name", "nexio-android-cloud-control")
                addProperty("version", "1.0")
            })
        })
        val negotiated = result.get("protocolVersion")?.asString
        check(negotiated in VERSIONS) { "服务器返回不支持的协议版本" }
        version = negotiated!!
        rpc("notifications/initialized", notification = true)
        return result.getAsJsonObject("serverInfo")?.get("name")?.asString?.take(80).orEmpty()
    }

    suspend fun tools(): Set<String> = rpc("tools/list").getAsJsonArray("tools")
        ?.mapNotNull { it.asJsonObject.get("name")?.asString }?.toSet()
        ?: error("服务器未返回工具列表")

    suspend fun tool(name: String, arguments: JsonObject = JsonObject()): JsonObject {
        val result = rpc("tools/call", JsonObject().apply {
            addProperty("name", name)
            add("arguments", arguments)
        })
        // 服务端错误正文可能含私人课程，不能直接展示到设置页或日志。
        check(result.get("isError")?.asBoolean != true) { "云端拒绝操作：请检查课表格式或重新预览，当前数据未被自动覆盖" }
        val content = result.getAsJsonArray("content")
            ?.firstOrNull { it.asJsonObject.get("type")?.asString == "text" }
            ?.asJsonObject?.get("text")?.asString ?: error("服务器结果格式不正确")
        return try {
            JsonParser.parseString(content).asJsonObject
        } catch (_: Exception) {
            error("服务器结果不是有效 JSON 对象")
        }
    }

    private suspend fun rpc(method: String, params: JsonObject = JsonObject(), notification: Boolean = false): JsonObject = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val id = ids.incrementAndGet()
        val json = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            if (!notification) addProperty("id", id)
            addProperty("method", method)
            add("params", params)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", version).post(body).build()
        try {
            httpClient.newCall(request).execute().use { response ->
                currentCoroutineContext().ensureActive()
                check(response.code !in 300..399) { "为保护 Token，拒绝服务器重定向；请填写最终 HTTPS 地址" }
                check(response.code !in setOf(401, 403)) { "云端鉴权失败，请检查地址、Token 和服务器配置" }
                check(response.isSuccessful) { "云端请求失败（HTTP ${response.code}）" }
                if (notification && response.code in setOf(200, 202, 204)) return@withContext JsonObject()
                val stream = response.body?.byteStream() ?: error("服务器返回空响应")
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= MAX_RESPONSE_BYTES) { "服务器响应超过 1 MiB，已停止读取" }
                    output.write(buffer, 0, count)
                }
                val root = runCatching { JsonParser.parseString(output.toString("UTF-8")).asJsonObject }
                    .getOrElse { error("服务器没有返回 JSON MCP 响应") }
                check(root.get("jsonrpc")?.asString == "2.0" && root.get("id")?.asInt == id) { "服务器响应标识不匹配" }
                check(!root.has("error")) { "云端 MCP 调用失败，请检查服务版本" }
                root.getAsJsonObject("result") ?: error("服务器结果缺失")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {
            error("无法连接云端 MCP，请检查网络与 HTTPS 证书")
        }
    }

    companion object {
        private val ids = AtomicInteger()
        private val VERSIONS = setOf("2025-03-26", "2025-06-18", "2025-11-25")
        private const val MAX_RESPONSE_BYTES = 1024 * 1024
        private val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).followRedirects(false)
            .followSslRedirects(false).retryOnConnectionFailure(false).build()

        fun normalizedEndpoint(value: String): String {
            require(value.length <= 2048) { "云端地址过长" }
            val url = value.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入完整的 HTTPS MCP 地址")
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "云端地址必须是 HTTPS，不能包含账号、查询参数或片段"
            }
            require(url.encodedPath in setOf("/", "/mcp")) { "云端地址路径应为 /mcp" }
            return url.newBuilder().encodedPath("/mcp").build().toString()
        }
    }
}
