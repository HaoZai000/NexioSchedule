package com.haooz.chedule.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [HttpService] 的契约测试。
 *
 * 用 [FakeHttpService] 而非真实网络：迁移期间要验证的是**调用方与门面之间的约定**
 * （方法、URL、头、体、状态码判定、异常传播），这些与后端是否可用无关。
 * 真实连通性由各业务文件自己负责。
 */
class HttpServiceContractTest {

    private class Recorded(
        val method: String,
        val url: String,
        val body: String?,
        val contentType: String?,
        val headers: Map<String, String>,
    )

    private class FakeHttpService(
        var respond: (String) -> HttpResult = { HttpResult(200, "{}".encodeToByteArray()) },
    ) : HttpService {
        val calls = mutableListOf<Recorded>()

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
            maxBytes: Long,
        ): HttpResult {
            calls += Recorded("GET", url, null, null, headers)
            return respond(url)
        }

        override suspend fun post(
            url: String,
            body: String,
            contentType: String,
            headers: Map<String, String>,
        ): HttpResult {
            calls += Recorded("POST", url, body, contentType, headers)
            return respond(url)
        }

        override suspend fun request(
            method: String,
            url: String,
            body: String?,
            contentType: String?,
            headers: Map<String, String>,
        ): HttpResult {
            calls += Recorded(method, url, body, contentType, headers)
            return respond(url)
        }
    }

    @Test
    fun `get 透传 URL 与请求头`() = runTest {
        val fake = FakeHttpService()
        fake.get("http://example.com/api/x", mapOf("Authorization" to "Basic abc"))

        val c = fake.calls.single()
        assertEquals("GET", c.method)
        assertEquals("http://example.com/api/x", c.url)
        assertEquals("Basic abc", c.headers["Authorization"])
        assertEquals(null, c.body)
    }

    @Test
    fun `post 默认按 application-json 发送`() = runTest {
        val fake = FakeHttpService()
        fake.post("http://example.com/api/stats", """{"a":1}""")

        val c = fake.calls.single()
        assertEquals("POST", c.method)
        assertEquals("""{"a":1}""", c.body)
        assertEquals("application/json", c.contentType)
    }

    /** WebDAV 备份依赖自定义方法，这条不能被「只支持 GET/POST」的实现破坏。 */
    @Test
    fun `request 支持自定义方法`() = runTest {
        val fake = FakeHttpService()
        fake.request("PROPFIND", "http://dav.example.com/", body = null, contentType = "application/xml")

        val c = fake.calls.single()
        assertEquals("PROPFIND", c.method)
        assertEquals(null, c.body)
        assertEquals("application/xml", c.contentType)
    }

    @Test
    fun `2xx 判定为成功`() {
        for (code in listOf(200, 201, 204, 299)) {
            assertTrue(HttpResult(code, ByteArray(0)).isSuccessful, "code=$code 应为成功")
        }
    }

    @Test
    fun `非 2xx 判定为失败`() {
        for (code in listOf(199, 300, 301, 400, 404, 500, 0)) {
            assertFalse(HttpResult(code, ByteArray(0)).isSuccessful, "code=$code 应为失败")
        }
    }

    @Test
    fun `响应体按 UTF-8 解码`() {
        val body = "课程表：数据结构".encodeToByteArray()
        assertEquals("课程表：数据结构", HttpResult(200, body).text)
    }

    @Test
    fun `空响应体不抛异常`() {
        assertEquals("", HttpResult(204, ByteArray(0)).text)
    }

    /** 门面**不能吞**网络异常 —— 迁移前的调用点自带 try/catch，吞掉会让兜底失效。 */
    @Test
    fun `网络异常照常抛出`() = runTest {
        // 必须用**平台中立**的异常类型：commonTest 也会参与 Kotlin/Native 编译，
        // 用 java.io.IOException 会让 linuxX64/ios 的测试编译直接失败。
        val fake = FakeHttpService { throw IllegalStateException("connection reset") }
        assertFailsWith<IllegalStateException> {
            fake.get("http://example.com/api/x")
        }
    }

    /**
     * 超时配置的默认值必须与「不额外设置」等价，
     * 否则会给原本无整体超时的请求强加超时（行为变更）。
     */
    @Test
    fun `HttpTimeouts 默认不启用 callTimeout`() {
        assertEquals(0L, HttpTimeouts().callSeconds, "默认必须为 0，表示不设置整体超时")
        assertTrue(HttpTimeouts().connectSeconds > 0)
        assertTrue(HttpTimeouts().readSeconds > 0)
    }

    /** 迁移前实测存在的 4 种超时组合，都要能表达出来。 */
    @Test
    fun `能表达迁移前的四种超时组合`() {
        val combos = listOf(
            HttpTimeouts(5, 5),
            HttpTimeouts(10, 30),
            HttpTimeouts(10, 10, 15),
            HttpTimeouts(12, 20, 30),
        )
        assertEquals(4, combos.toSet().size, "四种组合不能互相等价，否则无法保留原有语义")
    }
}
