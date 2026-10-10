package com.haooz.chedule.data

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 三个数据对象的**网络逻辑**验证。
 *
 * 迁移前这些 object 直接自建 OkHttpClient，没有任何注入点，导致「公告解析与已读去重」
 * 「分页参数拼装」「上报重试与 install-once」这些有实际分支的逻辑零覆盖。
 * 现在 `http` 是 `internal var`，可以注入 [RecordingHttp] 离线覆盖。
 */
class DataFetcherLogicTest {

    /** 记录请求并按脚本依次返回响应的假实现。 */
    private class RecordingHttp(
        private val responses: List<() -> HttpResult>,
    ) : HttpService {
        val urls = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        private var index = 0

        /** 按顺序取下一个响应；用完后重复最后一个（便于「一直失败」的用例）。 */
        private fun next(): HttpResult {
            val fn = responses.getOrNull(index) ?: responses.lastOrNull()
            index++
            return fn?.invoke() ?: HttpResult(500, ByteArray(0))
        }

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
            maxBytes: Long,
        ): HttpResult {
            urls += url
            return next()
        }

        override suspend fun post(
            url: String,
            body: String,
            contentType: String,
            headers: Map<String, String>,
        ): HttpResult {
            urls += url
            bodies += body
            return next()
        }

        override suspend fun request(
            method: String,
            url: String,
            body: String?,
            contentType: String?,
            headers: Map<String, String>,
        ): HttpResult = get(url, headers)
    }

    private fun ok(body: String): () -> HttpResult = { HttpResult(200, body.encodeToByteArray()) }
    private fun fail(code: Int = 500): () -> HttpResult = { HttpResult(code, ByteArray(0)) }
    private fun boom(): () -> HttpResult = { throw IllegalStateException("connection reset") }

    private lateinit var store: InMemoryKeyValueStore
    private val originals = HashMap<String, HttpService>()

    @BeforeTest
    fun setUp() {
        store = InMemoryKeyValueStore()
        AppStorage.init { store }
        AppInfo.init("1.6.5-test")
        StatsReporter.resetForTest()
        originals["notice"] = NoticeFetcher.http
        originals["appreciation"] = AppreciationFetcher.http
        originals["stats"] = StatsReporter.http
    }

    @AfterTest
    fun tearDown() {
        // 还原全局单例，避免污染其它测试类
        originals["notice"]?.let { NoticeFetcher.http = it }
        originals["appreciation"]?.let { AppreciationFetcher.http = it }
        originals["stats"]?.let { StatsReporter.http = it }
        StatsReporter.resetForTest()
    }

    // ─────────────── NoticeFetcher ───────────────

    @Test
    fun `公告正常解析出 id 标题与内容`() = runTest {
        NoticeFetcher.http = RecordingHttp(
            listOf(ok("""{"id":"n1","title":"注意","content":"正文"}""")),
        )
        val notice = NoticeFetcher.fetch()
        assertEquals(Notice("n1", "注意", "正文"), notice)
    }

    /** 后端返回的公告可能没有 id（`isNull` 分支），此时应视为无公告。 */
    @Test
    fun `公告 id 为空或缺失时返回 null`() = runTest {
        NoticeFetcher.http = RecordingHttp(listOf(ok("""{"id":null,"title":"x"}""")))
        assertNull(NoticeFetcher.fetch())

        NoticeFetcher.http = RecordingHttp(listOf(ok("""{"title":"x"}""")))
        assertNull(NoticeFetcher.fetch())

        NoticeFetcher.http = RecordingHttp(listOf(ok("""{"id":"   ","title":"x"}""")))
        assertNull(NoticeFetcher.fetch())
    }

    @Test
    fun `公告非 2xx 与异常都返回 null`() = runTest {
        NoticeFetcher.http = RecordingHttp(listOf(fail(500)))
        assertNull(NoticeFetcher.fetch())

        NoticeFetcher.http = RecordingHttp(listOf(fail(404)))
        assertNull(NoticeFetcher.fetch())

        NoticeFetcher.http = RecordingHttp(listOf(boom()))
        assertNull(NoticeFetcher.fetch())
    }

    /** 已读去重：markSeen 后 shouldShow 必须为 false，且落盘到 notice_prefs。 */
    @Test
    fun `公告已读去重与落盘`() = runTest {
        val notice = Notice("n1", "t", "c")
        assertTrue(NoticeFetcher.shouldShow(notice), "首次应展示")

        NoticeFetcher.markSeen(notice)
        assertFalse(NoticeFetcher.shouldShow(notice), "标记已读后不应再展示")

        assertEquals("n1", store.getString("seen_id", ""))
        // 另一条公告仍应展示
        assertTrue(NoticeFetcher.shouldShow(Notice("n2", "t2", "c2")))
    }

    // ─────────────── AppreciationFetcher ───────────────

    /** 分页参数必须拼进 URL（迁移前用 HttpUrl.newBuilder()）。 */
    @Test
    fun `捐赠分页参数正确拼入 URL`() = runTest {
        val http = RecordingHttp(listOf(ok("""{"records":[],"has_more":false}""")))
        AppreciationFetcher.http = http

        AppreciationFetcher.fetch(offset = 20, limit = 10)
        assertEquals(1, http.urls.size)
        assertTrue(http.urls[0].contains("limit=10"), "缺 limit: ${http.urls[0]}")
        assertTrue(http.urls[0].contains("offset=20"), "缺 offset: ${http.urls[0]}")
        assertTrue(http.urls[0].startsWith("http://"), "URL 协议异常: ${http.urls[0]}")
    }

    @Test
    fun `捐赠默认分页大小为 10`() = runTest {
        val http = RecordingHttp(listOf(ok("""{"records":[]}""")))
        AppreciationFetcher.http = http
        AppreciationFetcher.fetch()
        assertTrue(http.urls[0].contains("limit=10"))
        assertTrue(http.urls[0].contains("offset=0"))
    }

    @Test
    fun `捐赠列表解析与 has_more`() = runTest {
        AppreciationFetcher.http = RecordingHttp(
            listOf(
                ok(
                    """{"records":[
                       {"nickname":"甲","amount":"￥1.00","time":"2026-10-08","remark":"加油"},
                       {"nickname":"乙","amount":"￥2.00","time":"2026-10-07"}
                     ],"total":2,"has_more":true}""",
                ),
            ),
        )
        val page = AppreciationFetcher.fetch()
        assertEquals(2, page.items.size)
        assertEquals("甲", page.items[0].nickname)
        assertEquals("￥1.00", page.items[0].amount)
        assertEquals("加油", page.items[0].remark)
        // remark 缺失时应为空串（optString 语义）
        assertEquals("", page.items[1].remark)
        assertTrue(page.hasMore)
    }

    /** 缺 records 字段时按空列表处理，不抛异常。 */
    @Test
    fun `缺 records 时返回空页`() = runTest {
        AppreciationFetcher.http = RecordingHttp(listOf(ok("""{"total":0}""")))
        val page = AppreciationFetcher.fetch()
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore, "缺 has_more 时应为 false")
    }

    @Test
    fun `捐赠非 2xx 与异常返回空页或空列表`() = runTest {
        AppreciationFetcher.http = RecordingHttp(listOf(fail(503)))
        assertTrue(AppreciationFetcher.fetch().items.isEmpty())

        AppreciationFetcher.http = RecordingHttp(listOf(boom()))
        assertTrue(AppreciationFetcher.fetch().items.isEmpty())
        assertTrue(AppreciationFetcher.fetchTop().isEmpty())
    }

    @Test
    fun `前三名接口解析`() = runTest {
        val http = RecordingHttp(
            listOf(ok("""{"records":[{"nickname":"甲","amount":"¥19.99","time":"2026-09-06","remark":"r"}]}""")),
        )
        AppreciationFetcher.http = http
        val top = AppreciationFetcher.fetchTop(limit = 3)
        assertEquals(1, top.size)
        assertEquals("甲", top[0].nickname)
        assertTrue(http.urls[0].contains("limit=3"))
    }

    // ─────────────── StatsReporter ───────────────

    @Test
    fun `上报首次成功即返回 true`() = runTest {
        val http = RecordingHttp(listOf(ok("""{"ok":true}""")))
        StatsReporter.http = http
        assertTrue(StatsReporter.postWithRetry("{}"))
        assertEquals(1, http.bodies.size, "成功时不应重试")
    }

    /** 失败后应重试，最多 3 次。runTest 让 delay 走虚拟时间，不会真的等 1s/2s。 */
    @Test
    fun `上报失败会重试最多三次`() = runTest {
        val http = RecordingHttp(listOf(fail(500), fail(500), fail(500)))
        StatsReporter.http = http
        assertFalse(StatsReporter.postWithRetry("{}"))
        assertEquals(3, http.bodies.size, "应恰好尝试 3 次")
    }

    @Test
    fun `上报前两次失败第三次成功`() = runTest {
        val http = RecordingHttp(listOf(fail(500), boom(), ok("""{"ok":true}""")))
        StatsReporter.http = http
        assertTrue(StatsReporter.postWithRetry("{}"))
        assertEquals(3, http.bodies.size)
    }

    /** 网络异常也应触发重试（不是只对非 2xx 重试）。 */
    @Test
    fun `网络异常也计入重试`() = runTest {
        val http = RecordingHttp(listOf(boom(), boom(), boom()))
        StatsReporter.http = http
        assertFalse(StatsReporter.postWithRetry("{}"))
        assertEquals(3, http.bodies.size)
    }

    /**
     * install-once 的核心契约：**成功后才落盘标记**。
     * 若反过来（先标记后上报），失败时会永久丢失安装量。
     */
    @Test
    fun `install 失败时不落盘标记`() = runTest {
        StatsReporter.http = RecordingHttp(listOf(fail(500), fail(500), fail(500)))
        assertFalse(StatsReporter.reportInstallOnceSuspending())
        assertFalse(
            store.getBoolean("install_reported", false),
            "上报失败却写了标记，会导致安装量永久丢失",
        )
    }

    @Test
    fun `install 成功后落盘标记且不再重复上报`() = runTest {
        val http = RecordingHttp(listOf(ok("""{"ok":true}""")))
        StatsReporter.http = http
        assertTrue(StatsReporter.reportInstallOnceSuspending())
        assertTrue(store.getBoolean("install_reported", false))

        // 第二次调用应直接返回，不再发请求
        val before = http.bodies.size
        assertFalse(StatsReporter.reportInstallOnceSuspending())
        assertEquals(before, http.bodies.size, "已上报过就不该再发请求")
    }

    @Test
    fun `active 上报发送 event_type=active`() = runTest {
        val http = RecordingHttp(listOf(ok("""{"ok":true}""")))
        StatsReporter.http = http
        assertTrue(StatsReporter.reportActiveSuspending())
        assertEquals(1, http.bodies.size)
        assertTrue(
            http.bodies[0].contains("\"event_type\":\"active\""),
            "负载里 event_type 不对: ${http.bodies[0]}",
        )
    }
}
