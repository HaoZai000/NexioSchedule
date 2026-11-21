package com.haooz.chedule.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.haooz.chedule.data.mcp.CloudMcpConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** 使用 OkHttp 应用拦截器模拟 TLS 服务器；不发送网络请求，不加载个人课表。 */
class CloudMcpClientTest {
    private val config = CloudMcpConfig("https://schedule.example/mcp", "test-token-not-a-real-secret", true)

    private class FakeServer {
        val requests = mutableListOf<JsonObject>()
        val headers = mutableListOf<Request>()
        var hasSchedule = false
        var revision = 0L
        var responseCode = 200
        var responseText: String? = null
        var wrongId = false
        var writeRejected = false
        var pendingBase = -1L
        var previewBase: Long? = null
        var commitCount = 0
        var exportContent: String? = null

        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val bodyBuffer = okio.Buffer()
            request.body!!.writeTo(bodyBuffer)
            val message = JsonParser.parseString(bodyBuffer.readUtf8()).asJsonObject
            requests += message
            headers += request
            val method = message["method"].asString
            val params = message.getAsJsonObject("params")
            val result = when (method) {
                "initialize" -> JsonObject().apply {
                    addProperty("protocolVersion", "2025-11-25")
                    add("serverInfo", JsonObject().apply { addProperty("name", "test-cloud") })
                }
                "notifications/initialized" -> JsonObject()
                "tools/list" -> JsonParser.parseString("""{"tools":[{"name":"get_sync_status"},{"name":"import_schedule_file"},{"name":"update_schedule_file"},{"name":"export_schedule_file"}]}""").asJsonObject
                "tools/call" -> {
                    val args = params.getAsJsonObject("arguments")
                    val toolResult = when (params["name"].asString) {
                        "get_sync_status" -> JsonObject().apply {
                            addProperty("hasSchedule", hasSchedule); addProperty("revision", revision)
                        }
                        "export_schedule_file" -> JsonObject().apply {
                            addProperty("hasSchedule", hasSchedule)
                            addProperty("revision", revision)
                            addProperty("format", "nexio")
                            if (exportContent != null) addProperty("content", exportContent)
                        }
                        "import_schedule_file", "update_schedule_file" -> {
                            if (args.has("confirm")) {
                                if (pendingBase != revision) writeRejected = true
                                if (!writeRejected) { revision++; hasSchedule = true; commitCount++ }
                                JsonObject().apply {
                                    addProperty("ok", !writeRejected)
                                    addProperty("hasSchedule", hasSchedule)
                                    addProperty("revision", revision)
                                }
                            } else {
                                pendingBase = revision
                                JsonObject().apply {
                                    addProperty("requiresConfirmation", true)
                                    addProperty("pendingId", "pending-test-id-123")
                                    addProperty("baseRevision", previewBase ?: revision)
                                }
                            }
                        }
                        else -> JsonObject()
                    }
                    JsonObject().apply {
                        addProperty("isError", writeRejected)
                        add("content", JsonParser.parseString("""[{"type":"text","text":${com.google.gson.Gson().toJson(toolResult.toString())}}]"""))
                    }
                }
                else -> JsonObject()
            }
            val envelope = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                if (message.has("id")) add("id", if (wrongId) com.google.gson.JsonPrimitive(-999) else message["id"])
                add("result", result)
            }
            val code = if (method == "notifications/initialized" && responseCode == 200) 202 else responseCode
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("test")
                .body((responseText ?: if (code == 202) "" else envelope.toString()).toResponseBody("application/json".toMediaType()))
                .build()
        }).followRedirects(false).followSslRedirects(false).build()

        fun newClient(config: CloudMcpConfig) = CloudMcpClient(config, client)
        fun remote() = CloudMcpRemoteOperations(::newClient)
    }

    @Test fun disabledConnectionNeverCreatesRequests() = runBlocking {
        val server = FakeServer()
        val disabled = CloudMcpConfig(config.url, config.token, false)
        assertFails { server.remote().inspect(disabled) }
        assertFails { server.remote().previewUpload(disabled, "{}") }
        assertTrue(server.requests.isEmpty())
    }

    @Test fun normalizeOnlyAcceptsExplicitHttpsMcpEndpoint() {
        assertEquals("https://example.com/mcp", CloudMcpClient.normalizedEndpoint(" https://example.com "))
        listOf("http://example.com/mcp", "https://user:pass@example.com/mcp", "https://example.com/mcp?token=x", "https://example.com/mcp#secret", "https://example.com/other").forEach {
            try { CloudMcpClient.normalizedEndpoint(it); fail("Accepted invalid endpoint") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun inspectSendsOnlyReadCallsAndUsesBearerHeader() = runBlocking {
        val server = FakeServer()
        val status = server.remote().inspect(config)
        assertFalse(status.hasSchedule)
        assertEquals(0L, status.revision)
        assertEquals(4, status.toolCount)
        assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/call"), server.requests.map { it["method"].asString })
        assertEquals("get_sync_status", server.requests.last().getAsJsonObject("params")["name"].asString)
        assertTrue(server.headers.all { it.header("Authorization") == "Bearer ${config.token}" })
        assertTrue(server.headers.all { it.url.query == null && !it.url.toString().contains(config.token) })
        assertTrue(server.headers.all { it.header("MCP-Protocol-Version") == "2025-11-25" })
        assertEquals(0, server.commitCount)
    }

    @Test fun uploadRequiresSeparateConfirmationWithoutRepeatingContent() = runBlocking {
        val server = FakeServer()
        val remote = server.remote()
        val preview = remote.previewUpload(config, """{"courses":[{"name":"synthetic"}]}""")
        assertEquals(0, server.commitCount)
        assertEquals("import_schedule_file", preview.toolName)
        assertFalse(preview.replacesExisting)
        assertEquals(1, preview.recordCount)
        val status = remote.confirmUpload(config, preview)
        assertTrue(status.hasSchedule)
        assertEquals(1, server.commitCount)
        val confirm = server.requests.filter { it["method"].asString == "tools/call" }
            .map { it.getAsJsonObject("params") }.single { it.getAsJsonObject("arguments").has("confirm") }
        assertEquals(setOf("confirm", "pending_id"), confirm.getAsJsonObject("arguments").keySet())
        assertTrue(server.requests.last().getAsJsonObject("params").getAsJsonObject("arguments").has("confirm"))
        assertEquals(1L, status.revision)
    }

    @Test fun existingCloudScheduleUsesRevisionCheckedReplace() = runBlocking {
        val server = FakeServer().apply { hasSchedule = true; revision = 8 }
        val preview = server.remote().previewUpload(config, """{"courses":[]}""")
        assertEquals("update_schedule_file", preview.toolName)
        assertEquals(8L, preview.baseRevision)
        val args = server.requests.last().getAsJsonObject("params").getAsJsonObject("arguments")
        assertEquals(8L, args["expected_revision"].asLong)
        assertTrue(preview.replacesExisting)
        assertEquals(0, server.commitCount)
    }

    @Test fun stalePreviewCannotCommitAndServerErrorBodyStaysPrivate() = runBlocking {
        val server = FakeServer()
        val preview = server.remote().previewUpload(config, """{"courses":[]}""")
        server.revision++
        assertFails { server.remote().confirmUpload(config, preview) }
        assertEquals(0, server.commitCount)
        val failure = FakeServer().apply { responseCode = 401; responseText = config.token }
        val message = assertFails { failure.remote().inspect(config) }.message.orEmpty()
        assertFalse(message.contains(config.token))
    }

    @Test fun redirectIsRejectedWithoutSecondRequest() = runBlocking {
        val server = FakeServer().apply { responseCode = 302 }
        assertFails { server.remote().inspect(config) }
        assertEquals(1, server.requests.size)
    }

    @Test fun mismatchingResponseIdAndPreviewRevisionAreRejected() = runBlocking {
        assertFails { FakeServer().apply { wrongId = true }.remote().inspect(config) }
        val server = FakeServer().apply { previewBase = 99 }
        assertFails { server.remote().previewUpload(config, """{"courses":[]}""") }
        assertEquals(0, server.commitCount)
    }

    @Test fun oversizedResponseIsRejected(): Unit = runBlocking {
        val server = FakeServer().apply { responseText = "x".repeat(1024 * 1024 + 1) }
        assertFails { server.remote().inspect(config) }
    }

    @Test fun tokenIsRedactedFromConfigurationToString() {
        assertFalse(config.toString().contains(config.token))
        assertFalse(config.toString().contains(config.url))
    }

    @Test fun downloadReadsSnapshotWithoutWrites() = runBlocking {
        val server = FakeServer().apply {
            hasSchedule = true
            revision = 5
            exportContent = """{"schedule_name":"云端课表","settings":{},"courses":[{"name":"t","dayOfWeek":2,"startSection":1,"endSection":2,"selectedWeeks":[1]}]}"""
        }
        val remote = server.remote()
        val preview = remote.previewDownload(config)
        assertEquals(5L, preview.revision)
        assertEquals("云端课表", preview.scheduleName)
        assertEquals(1, preview.recordCount)
        assertTrue(preview.content.contains("云端课表"))
        val writes = server.requests.filter { it["method"].asString == "tools/call" }
            .map { it.getAsJsonObject("params")["name"].asString }
        assertEquals(listOf("export_schedule_file"), writes)
        // 导出只读：revision 不递增，没有 pending/confirm 帧
        assertEquals(5L, server.revision)
        assertEquals(0, server.commitCount)
    }

    @Test fun downloadRejectsEmptyCloudSchedule() = runBlocking {
        val server = FakeServer().apply { hasSchedule = false; revision = 3 }
        assertFails { server.remote().previewDownload(config) }
        assertEquals(0, server.commitCount)
    }

    private suspend fun assertFails(action: suspend () -> Unit): Exception {
        try { action() } catch (e: Exception) { return e }
        throw AssertionError("Expected failure")
    }
}
