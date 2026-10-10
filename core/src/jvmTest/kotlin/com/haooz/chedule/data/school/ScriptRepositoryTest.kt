package com.haooz.chedule.data.school

import com.haooz.chedule.data.AppFiles
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.HttpResult
import com.haooz.chedule.data.HttpService
import com.haooz.chedule.data.InMemoryAppFile
import com.haooz.chedule.data.InMemoryKeyValueStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [ScriptRepository] 迁移等价性验证。
 *
 * 这个类是从 `:app` 搬进 `:core` 的，过程中换了三样东西：
 * `Context.filesDir` → [AppFiles]、`SharedPreferences` → [AppStorage]、OkHttp → [HttpService]。
 * 所以必须验证的不是「能编译」，而是**分支行为没变**：
 *
 * - 防盗链跟随（`isGiteeAntiHotlinkPage` / `extractGiteeSignedUrl` / `remoteBase` 的 `.git` 处理）
 * - 版本比较的三个分支（更新 / 相同 / 更旧）
 * - 协议版本闸门
 * - 8MB 上限（原来靠 `byteStream()` 边读边计数，现在下推到 `HttpService.get(maxBytes)`）
 * - 偏好文件名与键名**逐字未变**（改了等于用户的仓库地址丢失）
 *
 * 用真实生产索引（78,911 字节）做夹具；「旧版本」「高协议版本」用**等长原地补丁**构造。
 */
class ScriptRepositoryTest {

    private val prefsName = "edu_import_prefs"
    private val repoUrlKey = "repo_url"
    private val defaultRepo = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
    private val indexUrl = "$defaultRepo/raw/index-pb-release/school_index.pb"
    private val indexPath = "repo/index/school_index.pb"
    private val versionId = "TIME_20261008145522_344"

    // ─────────────── 夹具 ───────────────

    private fun fixture(): ByteArray {
        val stream = javaClass.getResourceAsStream("/school_index.pb")
        assertNotNull(stream, "找不到测试夹具 /school_index.pb")
        return stream.use { it.readBytes() }
    }

    /** 原地替换 versionId（等长，protobuf 里字符串是长度前缀的，等长替换安全）。 */
    private fun withVersion(id: String): ByteArray {
        val bytes = fixture()
        val old = versionId.encodeToByteArray()
        val new = id.encodeToByteArray()
        require(old.size == new.size) { "版本号长度必须一致才能原地补丁" }
        val at = indexOfSub(bytes, old)
        assertTrue(at >= 0, "夹具里找不到 versionId")
        new.copyInto(bytes, at)
        return bytes
    }

    /** 夹具开头是 `08 02`（field 1 varint = protocolVersion 2），把 2 改成别的值。 */
    private fun withProtocolVersion(v: Byte): ByteArray {
        val bytes = fixture()
        assertEquals(0x08.toByte(), bytes[0], "夹具首位应为 protocolVersion 字段标签")
        assertEquals(2.toByte(), bytes[1], "夹具 protocolVersion 应为 2")
        bytes[1] = v
        return bytes
    }

    private fun indexOfSub(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** 记录请求 URL 并按顺序返回响应的假实现。 */
    private class FakeHttp(private val responses: List<HttpResult>) : HttpService {
        val urls = mutableListOf<String>()
        val maxBytesSeen = mutableListOf<Long>()
        private var i = 0

        private fun next(): HttpResult =
            responses.getOrNull(i++) ?: HttpResult(404, ByteArray(0))

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
            maxBytes: Long,
        ): HttpResult {
            urls += url
            maxBytesSeen += maxBytes
            return next()
        }

        override suspend fun post(
            url: String,
            body: String,
            contentType: String,
            headers: Map<String, String>,
        ): HttpResult = next()

        override suspend fun request(
            method: String,
            url: String,
            body: String?,
            contentType: String?,
            headers: Map<String, String>,
        ): HttpResult = next()
    }

    private var root: InMemoryAppFile? = null

    /** 已初始化的内存文件系统（AppFiles.root 是接口类型，取不到测试用的快照方法）。 */
    private fun rootFs(): InMemoryAppFile = root ?: error("freshEnv() 未调用")
    private var prefs: InMemoryKeyValueStore? = null

    private fun freshEnv(): Pair<InMemoryAppFile, InMemoryKeyValueStore> {
        val r = InMemoryAppFile.root()
        val p = InMemoryKeyValueStore()
        AppFiles.init(r, readAsset = { throw IllegalStateException("本测试不读内置资源") })
        AppStorage.init { name -> if (name == prefsName) p else InMemoryKeyValueStore() }
        root = r
        prefs = p
        return r to p
    }

    private fun seedLocalIndex(bytes: ByteArray) {
        rootFs().resolve(indexPath).writeBytes(bytes)
    }

    private fun repo(http: HttpService, url: String? = null) = ScriptRepository(url, http)

    // ─────────────── 版本比较三分支 ───────────────

    @Test
    fun `远端更新时写入并返回 1`() = runBlocking {
        val (_, _) = freshEnv()
        val remote = fixture()
        seedLocalIndex(withVersion("TIME_20251008145522_344")) // 更旧

        val fake = FakeHttp(listOf(HttpResult(200, remote)))
        assertEquals(1, repo(fake).updateAll(onLog = {}))

        val written = rootFs().resolve(indexPath).readBytes()
        assertTrue(written.contentEquals(remote), "应把远端索引原样写入")
    }

    @Test
    fun `版本相同时返回 0 且不写盘`() = runBlocking {
        freshEnv()
        val local = withVersion("TIME_20251008145522_344")
        seedLocalIndex(local)

        val fake = FakeHttp(listOf(HttpResult(200, withVersion("TIME_20251008145522_344"))))
        assertEquals(0, repo(fake).updateAll(onLog = {}))
        assertTrue(
            rootFs().resolve(indexPath).readBytes().contentEquals(local),
            "版本相同时不应改动本地文件",
        )
    }

    @Test
    fun `远端更旧时返回 -1`() = runBlocking {
        freshEnv()
        seedLocalIndex(fixture()) // 本地是最新的

        val fake = FakeHttp(listOf(HttpResult(200, withVersion("TIME_20251008145522_344"))))
        assertEquals(-1, repo(fake).updateAll(onLog = {}))
    }

    // ─────────────── 协议版本闸门 ───────────────

    @Test
    fun `远端协议版本过高时中止`() = runBlocking {
        freshEnv()
        seedLocalIndex(withVersion("TIME_20251008145522_344"))

        val fake = FakeHttp(listOf(HttpResult(200, withProtocolVersion(3))))
        assertEquals(-1, repo(fake).updateAll(onLog = {}))
        assertTrue(
            rootFs().resolve(indexPath).readBytes()
                .contentEquals(withVersion("TIME_20251008145522_344")),
            "协议版本过高时不得写盘",
        )
    }

    // ─────────────── 防盗链 ───────────────

    @Test
    fun `防盗链签名页时跟随并重试`() = runBlocking {
        freshEnv()
        seedLocalIndex(withVersion("TIME_20251008145522_344"))

        // 第一次返回签名页（<=1024 字节、以 < 开头、含 raw.giteeusercontent.com、带 href）
        val signed = "https://raw.giteeusercontent.com/a/b?token=xyz&t=1"
        val html = (
            "<html><body><a href=\"https://raw.giteeusercontent.com/a/b?token=xyz&amp;t=1\">go</a>"
                + "</body></html>"
            ).encodeToByteArray()
        val fake = FakeHttp(listOf(HttpResult(200, html), HttpResult(200, fixture())))

        assertEquals(1, repo(fake).updateAll(onLog = {}))

        assertEquals(2, fake.urls.size, "应发出两次请求")
        assertEquals(indexUrl, fake.urls[0])
        assertEquals(signed, fake.urls[1], "第二次应请求签名链接，且 &amp; 要还原成 &")
    }

    @Test
    fun `签名页里没有 href 时视为失败`() = runBlocking {
        freshEnv()
        val html = "<html><body>denied raw.giteeusercontent.com</body></html>".encodeToByteArray()
        val fake = FakeHttp(listOf(HttpResult(200, html)))
        assertEquals(-1, repo(fake).updateAll(onLog = {}))
    }

    /** 体积大于 1024 的 HTML 不算防盗链页（原判定就是这样），会被当成索引解析失败。 */
    @Test
    fun `超过 1KB 的 HTML 不按防盗链处理`() = runBlocking {
        freshEnv()
        val html = ("<html>" + "x".repeat(1200) + "raw.giteeusercontent.com</html>").encodeToByteArray()
        val fake = FakeHttp(listOf(HttpResult(200, html)))
        assertEquals(-1, repo(fake).updateAll(onLog = {}))
        assertEquals(1, fake.urls.size, "不应触发第二次请求")
    }

    // ─────────────── 下载失败与上限 ───────────────

    @Test
    fun `非 2xx 视为失败`() = runBlocking {
        freshEnv()
        val fake = FakeHttp(listOf(HttpResult(404, ByteArray(0))))
        assertEquals(-1, repo(fake).updateAll(onLog = {}))
    }

    @Test
    fun `空响应体视为失败`() = runBlocking {
        freshEnv()
        val fake = FakeHttp(listOf(HttpResult(200, ByteArray(0))))
        assertEquals(-1, repo(fake).updateAll(onLog = {}))
    }

    @Test
    fun `超出大小上限视为失败`() = runBlocking {
        freshEnv()
        // truncated = true 对应原来 readLimitedBytes 返回 null
        val fake = FakeHttp(listOf(HttpResult(200, fixture(), truncated = true)))
        assertEquals(-1, repo(fake).updateAll(onLog = {}))
    }

    @Test
    fun `损坏索引视为失败`() = runBlocking {
        freshEnv()
        val fake = FakeHttp(listOf(HttpResult(200, byteArrayOf(0x08))))
        assertEquals(-1, repo(fake).updateAll(onLog = {}))
    }

    /** 8MB 上限必须下推到 HTTP 层，否则「边读边计数」的保护就没了。 */
    @Test
    fun `大小上限被下推到 HTTP 层`() = runBlocking {
        freshEnv()
        val fake = FakeHttp(listOf(HttpResult(200, fixture())))
        repo(fake).updateAll(onLog = {})
        assertEquals(listOf(8L * 1024 * 1024), fake.maxBytesSeen)
    }

    // ─────────────── 路径与偏好（数据安全红线）───────────────

    @Test
    fun `索引落盘路径逐字未变`() = runBlocking {
        freshEnv()
        val fake = FakeHttp(listOf(HttpResult(200, fixture())))
        repo(fake).updateAll(onLog = {})
        assertEquals(
            setOf("repo/index/school_index.pb"),
            rootFs().filePaths(),
            "落盘路径变了，老用户本地已下载的索引会读不出来",
        )
    }

    @Test
    fun `偏好文件名与键名逐字未变`() {
        val (_, _) = freshEnv()
        // 通过 getRepoUrl/setRepoUrl 间接确认名字与键
        assertEquals(defaultRepo, ScriptRepository.getRepoUrl())

        ScriptRepository.setRepoUrl("https://example.com/foo.git")
        assertEquals("https://example.com/foo.git", ScriptRepository.getRepoUrl())

        // 直接检查存储层，确认名字与键没有被改名
        val store = AppStorage.store(prefsName)
        assertEquals(
            "https://example.com/foo.git",
            store.getString(repoUrlKey, ""),
            "偏好文件名或键名被改了：老用户的仓库地址会读不出来",
        )
    }

    @Test
    fun `repoUrl 末尾的 git 后缀会被去掉`() = runBlocking {
        freshEnv()
        val fake = FakeHttp(listOf(HttpResult(200, ByteArray(0))))
        repo(fake, "https://example.com/foo.git").updateAll(onLog = {})
        assertEquals("https://example.com/foo/raw/index-pb-release/school_index.pb", fake.urls[0])
    }

    // ─────────────── 脚本缓存 ───────────────

    @Test
    fun `脚本下载成功后写入资源目录`() = runBlocking {
        freshEnv()
        val js = "window.importTable = function(){}".encodeToByteArray()
        val fake = FakeHttp(listOf(HttpResult(200, js)))

        val file = repo(fake).ensureScript("SOME_SCHOOL", "adapter.js")
        assertNotNull(file, "下载成功应返回文件句柄")
        assertTrue(file.readBytes().contentEquals(js))
        assertEquals(
            "repo/schools/resources/SOME_SCHOOL/adapter.js",
            file.path,
            "脚本缓存路径变了",
        )
    }

    @Test
    fun `脚本下载失败时回退到未被污染的缓存`() = runBlocking {
        freshEnv()
        val js = "cached".encodeToByteArray()
        rootFs().resolve("repo/schools/resources/S/adapter.js").writeBytes(js)

        // 网络失败（500），应回退缓存
        val fake = FakeHttp(listOf(HttpResult(500, ByteArray(0))))
        val file = repo(fake).ensureScript("S", "adapter.js")
        assertNotNull(file)
        assertTrue(file.readBytes().contentEquals(js))
    }

    @Test
    fun `缓存是防盗链 HTML 时不回退`() = runBlocking {
        freshEnv()
        val html = "<html>raw.giteeusercontent.com</html>".encodeToByteArray()
        rootFs().resolve("repo/schools/resources/S/adapter.js").writeBytes(html)

        val fake = FakeHttp(listOf(HttpResult(500, ByteArray(0))))
        assertEquals(null, repo(fake).ensureScript("S", "adapter.js"))
    }
}
