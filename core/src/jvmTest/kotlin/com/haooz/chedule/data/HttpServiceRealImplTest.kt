package com.haooz.chedule.data

import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **真实平台实现**的 `maxBytes` 行为验证（不是假实现）。
 *
 * ## 为什么单独写这一个
 *
 * `HttpServiceContractTest` 与 `ScriptRepositoryTest` 用的都是 `FakeHttpService`，
 * 也就是只验证了**调用方**怎么处理 `truncated`，完全没碰到 `HttpService.jvm.kt` /
 * `HttpService.android.kt` 里那段"边读边停"的真实代码。而这段代码守着
 * 「异常大响应不要 OOM」的闸门 —— 是本次迁移里最容易悄悄失效的地方。
 *
 * 这里直接起一个本地 socket 服务器，用 JDK 的原生实现（JVM 目标走 `HttpURLConnection`）
 * 跑端到端：
 * - **声明长度已知**（带 Content-Length）→ 应走"预检即拒绝"，不读 body
 * - **声明长度未知**（Transfer-Encoding: chunked）→ 应走流式读取并在超限处停手
 * - 正好等于上限 → **不算截断**（off-by-one 的经典坑）
 *
 * Android 侧无法在本机跑宿主测试（缺 Robolectric / 真机），那块仍需真机回归，
 * 但两端走的是同一份 [readAtMost] 语义，这里的边界结论可直接参考。
 */
class HttpServiceRealImplTest {

    /** 极简 HTTP/1.1 服务器：一个连接一次响应，写完即关。 */
    private class TinyHttpServer(
        private val status: Int,
        private val headers: Map<String, String>,
        private val body: ByteArray,
        private val chunked: Boolean,
    ) : AutoCloseable {
        private val socket = ServerSocket(0)
        val port: Int get() = socket.localPort

        @Volatile
        private var closed = false

        private val worker = Thread {
            while (!closed) {
                val conn = try {
                    socket.accept()
                } catch (e: Exception) {
                    break
                }
                try {
                    conn.use { c ->
                        // 读掉请求头（GET，无请求体）
                        val reader = c.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val out = c.getOutputStream()
                        val sb = StringBuilder("HTTP/1.1 $status X\r\n")
                        headers.forEach { (k, v) -> sb.append("$k: $v\r\n") }
                        sb.append("Connection: close\r\n\r\n")
                        out.write(sb.toString().toByteArray())
                        if (chunked) {
                            // 分块传输：不带 Content-Length，客户端只能流式读。
                            // ⚠ 块长度是**十六进制**，写成十进制会让客户端解析出错。
                            var pos = 0
                            while (pos < body.size) {
                                val n = minOf(4096, body.size - pos)
                                out.write("${Integer.toHexString(n)}\r\n".toByteArray())
                                out.write(body, pos, n)
                                out.write("\r\n".toByteArray())
                                pos += n
                            }
                            out.write("0\r\n\r\n".toByteArray())
                        } else {
                            out.write(body)
                        }
                        out.flush()
                    }
                } catch (_: Exception) {
                    // 客户端提前断开是正常情况（超限时我们会主动停手）
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        override fun close() {
            closed = true
            runCatching { socket.close() }
        }
    }

    private fun server(
        body: ByteArray,
        status: Int = 200,
        chunked: Boolean = false,
    ): TinyHttpServer = TinyHttpServer(
        status = status,
        headers = if (chunked) {
            // ⚠ 必须显式声明 chunked，否则客户端会把分块帧当成正文
            mapOf(
                "Content-Type" to "application/octet-stream",
                "Transfer-Encoding" to "chunked",
            )
        } else {
            mapOf(
                "Content-Type" to "application/octet-stream",
                "Content-Length" to body.size.toString(),
            )
        },
        body = body,
        chunked = chunked,
    )

    private fun service() = createHttpService(
        HttpTimeouts(connectSeconds = 5, readSeconds = 5),
    )

    private fun bodyOf(size: Int) = ByteArray(size) { (it % 251).toByte() }

    // ─────────────── 声明长度已知 ───────────────

    @Test
    fun `声明长度超限时返回空字节并标记截断`() = runBlocking {
        server(bodyOf(100_000)).use { srv ->
            val r = service().get("http://127.0.0.1:${srv.port}/big", maxBytes = 1024)
            assertTrue(r.isSuccessful)
            assertTrue(r.truncated, "声明长度 100KB > 1KB，应标记截断")
            assertEquals(0, r.bytes.size, "已声明超限就不该去读 body（与 Android 实现一致）")
        }
    }

    @Test
    fun `声明长度正好等于上限时不算截断`() = runBlocking {
        val body = bodyOf(1024)
        server(body).use { srv ->
            val r = service().get("http://127.0.0.1:${srv.port}/exact", maxBytes = 1024)
            assertFalse(r.truncated, "正好等于上限不是截断（off-by-one）")
            assertTrue(r.bytes.contentEquals(body))
        }
    }

    @Test
    fun `声明长度小于上限时原样返回`() = runBlocking {
        val body = bodyOf(500)
        server(body).use { srv ->
            val r = service().get("http://127.0.0.1:${srv.port}/small", maxBytes = 1024)
            assertFalse(r.truncated)
            assertTrue(r.bytes.contentEquals(body))
        }
    }

    // ─────────────── 声明长度未知（chunked）───────────────

    @Test
    fun `chunked 超限时流式停手并只保留到上限`() = runBlocking {
        server(bodyOf(100_000), chunked = true).use { srv ->
            val r = service().get("http://127.0.0.1:${srv.port}/chunked", maxBytes = 1024)
            assertTrue(r.isSuccessful)
            assertTrue(r.truncated, "长度未知且实际 100KB > 1KB，应标记截断")
            assertEquals(1024, r.bytes.size, "应只保留到上限为止")
        }
    }

    @Test
    fun `chunked 未超限时完整返回`() = runBlocking {
        val body = bodyOf(3000)
        server(body, chunked = true).use { srv ->
            val r = service().get("http://127.0.0.1:${srv.port}/chunked-ok", maxBytes = 8192)
            assertFalse(r.truncated)
            assertEquals(body.size, r.bytes.size, "chunked 未超限时长度应完整")
            assertTrue(r.bytes.contentEquals(body), "chunked 内容应逐字节一致")
        }
    }

    // ─────────────── 不设上限 ───────────────

    @Test
    fun `不设上限时完整返回且不标记截断`() = runBlocking {
        val body = bodyOf(50_000)
        server(body).use { srv ->
            val r = service().get("http://127.0.0.1:${srv.port}/all")
            assertFalse(r.truncated)
            assertEquals(50_000, r.bytes.size)
        }
    }

    // ─────────────── 错误响应 ───────────────

    @Test
    fun `错误响应也能拿到状态码`() = runBlocking {
        server(status = 500, body = "boom".encodeToByteArray()).use { srv ->
            val r = service().get("http://127.0.0.1:${srv.port}/err", maxBytes = 1024)
            assertEquals(500, r.code)
            assertFalse(r.isSuccessful)
        }
    }

    @Test
    fun `错误响应体超限时同样标记截断`() = runBlocking {
        server(status = 500, body = bodyOf(10_000)).use { srv ->
            val r = service().get("http://127.0.0.1:${srv.port}/err-big", maxBytes = 512)
            assertEquals(500, r.code)
            assertTrue(r.truncated)
        }
    }

    // ─────────────── POST / 任意方法 ───────────────

    @Test
    fun `POST 能正常收发`() = runBlocking {
        server(bodyOf(16)).use { srv ->
            val r = service().post("http://127.0.0.1:${srv.port}/p", body = "{\"a\":1}")
            assertTrue(r.isSuccessful)
            assertEquals(16, r.bytes.size)
        }
    }
}
