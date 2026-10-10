package com.haooz.chedule.data

import kotlinx.coroutines.test.runTest
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `WebDavManager` 的**请求契约**测试。
 *
 * ## 为什么是「契约」而不是「功能」
 *
 * 本地起不了 WebDAV 服务器，所以没法端到端测。而 WebDAV 的失败方式恰恰是
 * 「编译全绿、真机才发现」那类：方法名写错（PROPFIND vs POST）、少一个 `Depth` 头、
 * 认证头拼错 —— 全部只在连真服务器时才暴露。
 *
 * 所以这里塞一个**记录请求的假 [HttpService]**，把「发出去的请求长什么样」逐条钉死：
 * 方法 / URL / `Authorization` / `Depth` / body / contentType。
 * 换实现时只要这些不变，线上行为就不会变。
 *
 * ⚠ 本文件不入库（用户长期要求）。
 */
class WebDavManagerContractTest {

    private class Recorded(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val body: String?,
        val contentType: String?,
    )

    private class FakeHttp(
        /** (method, url) → 响应；未命中时用 defaultResponse */
        private val responses: MutableList<Pair<(Recorded) -> Boolean, HttpResult>> = mutableListOf(),
        private var defaultResponse: HttpResult = HttpResult(200, ByteArray(0)),
    ) : HttpService {
        val requests = mutableListOf<Recorded>()

        fun on(method: String, urlSuffix: String = "", code: Int, body: String = "") {
            responses.add(
                ({ r: Recorded -> r.method == method && (urlSuffix.isEmpty() || r.url.endsWith(urlSuffix)) }) to
                    HttpResult(code, body.encodeToByteArray()),
            )
        }

        override suspend fun get(url: String, headers: Map<String, String>, maxBytes: Long): HttpResult =
            record("GET", url, null, null, headers)

        override suspend fun post(
            url: String,
            body: String,
            contentType: String,
            headers: Map<String, String>,
        ): HttpResult = record("POST", url, body, contentType, headers)

        override suspend fun request(
            method: String,
            url: String,
            body: String?,
            contentType: String?,
            headers: Map<String, String>,
        ): HttpResult = record(method, url, body, contentType, headers)

        private fun record(
            method: String,
            url: String,
            body: String?,
            contentType: String?,
            headers: Map<String, String>,
        ): HttpResult {
            val r = Recorded(method, url, headers, body, contentType)
            requests.add(r)
            return responses.firstOrNull { it.first(r) }?.second ?: defaultResponse
        }
    }

    private lateinit var store: InMemoryKeyValueStore
    private lateinit var http: FakeHttp
    private lateinit var manager: WebDavManager
    private var holidayCallbackCount = 0

    @BeforeTest
    fun setUp() {
        store = InMemoryKeyValueStore()
        AppStorage.init { store }
        http = FakeHttp()
        holidayCallbackCount = 0
        manager = WebDavManager(onHolidayDataChanged = { holidayCallbackCount++ })
        manager.http = http
        manager.serverUrl = "https://dav.example.com/remote.php/dav"
        manager.username = "user"
        manager.password = "pass"
    }

    // ── 认证头与配置 ─────────────────────────────

    @Test
    fun `Basic 认证头与 OkHttp Credentials basic 一致`() {
        // Credentials.basic("user","pass") 的内部实现就是 base64("user:pass")
        assertEquals("Basic dXNlcjpwYXNz", basicAuthHeader("user", "pass"))
        assertEquals("Basic dXNlcjpwYXNz", manager.let { basicAuthHeader(it.username, it.password) })
    }

    @Test
    fun `配置读写与文件名逐字未变`() {
        // webdav_config 这个 prefs 名与 4 个键名是数据兼容契约
        manager.serverUrl = "https://dav.example.com/remote.php/dav/"
        assertEquals("https://dav.example.com/remote.php/dav", manager.serverUrl, "尾部斜杠应被裁掉")
        manager.lastSyncTime = 123L
        assertEquals(123L, manager.lastSyncTime)
        assertEquals("user", store.getString("username", ""))
        assertEquals("pass", store.getString("password", ""))
        assertEquals(123L, store.getLong("last_sync_time", 0L))
        assertTrue(manager.isConfigured())
        manager.password = ""
        assertFalse(manager.isConfigured())
    }

    // ── 连接测试 ─────────────────────────────────

    @Test
    fun `testConnection 发 PROPFIND 带 Depth 0`() = runTest {
        http.on("PROPFIND", code = 207)
        val result = manager.testConnection()
        assertEquals("连接成功", result.getOrNull())
        val r = http.requests.single()
        assertEquals("PROPFIND", r.method)
        assertEquals("https://dav.example.com/remote.php/dav", r.url)
        assertEquals("Basic dXNlcjpwYXNz", r.headers["Authorization"])
        assertEquals("0", r.headers["Depth"])
    }

    @Test
    fun `testConnection 的 401 与 404 分支`() = runTest {
        http.on("PROPFIND", code = 401)
        assertTrue(manager.testConnection().exceptionOrNull()?.message!!.contains("认证失败"))

        http = FakeHttp().also { manager.http = it }
        http.on("PROPFIND", code = 404)
        assertEquals("连接成功（目录将自动创建）", manager.testConnection().getOrNull())
    }

    // ── 备份 ─────────────────────────────────────

    @Test
    fun `backupAllData 的请求序列与 body 形状`() = runTest {
        // 目录已存在 → PROPFIND 都返回 207，不会走 MKCOL 重试
        http.on("PROPFIND", code = 207)
        http.on("PUT", code = 201)

        val repository = CourseRepository.getInstance()
        val result = manager.backupAllData(repository)
        assertTrue(result is BackupResult.Success, "实际: $result")

        // 两次 PROPFIND（base / backups）+ 一次 PUT，顺序与 URL 都要对
        assertEquals(listOf("PROPFIND", "PROPFIND", "PUT"), http.requests.map { it.method })
        assertEquals(
            "https://dav.example.com/remote.php/dav/NexioSchedule",
            http.requests[0].url,
        )
        assertEquals(
            "https://dav.example.com/remote.php/dav/NexioSchedule/backups",
            http.requests[1].url,
        )

        val put = http.requests[2]
        assertTrue(put.url.startsWith("https://dav.example.com/remote.php/dav/NexioSchedule/backups/backup_"))
        assertTrue(put.url.endsWith(".json"))
        assertEquals("application/json", put.contentType)
        assertEquals("Basic dXNlcjpwYXNz", put.headers["Authorization"])

        // body 是备份信封：version / backupTime / backupId / data
        val body = put.body!!
        val parsed = jsonToPlainValue(parseJsonObject(body)) as Map<*, *>
        assertEquals(1.0, parsed["version"])
        assertTrue(parsed["backupId"] is String)
        assertTrue(parsed["backupTime"] is String)
        assertTrue(parsed["data"] is Map<*, *>)
        // backupId 形如 backup_yyyyMMdd_HHmmss
        val backupId = parsed["backupId"] as String
        assertTrue(Regex("^backup_\\d{8}_\\d{6}$").matches(backupId), "实际: $backupId")
    }

    @Test
    fun `目录不存在时用 MKCOL 创建，405 视为已存在`() = runTest {
        http.on("PROPFIND", code = 404)
        http.on("MKCOL", code = 405)
        http.on("PUT", code = 201)

        val result = manager.backupAllData(CourseRepository.getInstance())
        assertTrue(result is BackupResult.Success, "实际: $result")

        assertEquals(
            listOf("PROPFIND", "MKCOL", "PROPFIND", "MKCOL", "PUT"),
            http.requests.map { it.method },
        )
        assertTrue(http.requests.filter { it.method == "MKCOL" }.all { it.headers["Authorization"] != null })
    }

    // ── 列表 / 恢复 / 删除 ────────────────────────

    private val propfindBody = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response><d:href>/remote.php/dav/NexioSchedule/backups/</d:href></d:response>
          <d:response><d:href>/remote.php/dav/NexioSchedule/backups/backup_20261001_120000.json</d:href></d:response>
          <d:response><d:href>/remote.php/dav/NexioSchedule/backups/backup_20260901_090000.json</d:href></d:response>
          <d:response><d:href>/remote.php/dav/NexioSchedule/backups/notes.txt</d:href></d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `listBackups 用 Depth 1 并只认 backup_ 前缀的 json`() = runTest {
        http.on("PROPFIND", urlSuffix = "/backups", code = 207, body = propfindBody)
        val backups = manager.listBackups()
        assertEquals(
            listOf("backup_20261001_120000", "backup_20260901_090000"),
            backups.map { it.backupId },
        )
        assertEquals(
            listOf("backup_20261001_120000.json", "backup_20260901_090000.json"),
            backups.map { it.fileName },
        )
        assertEquals("1", http.requests.single().headers["Depth"])
        // 展示时间由 backupId 推出
        assertEquals("2026-10-01 12:00:00", backups[0].backupTime)
    }

    @Test
    fun `restoreLatestBackup 取最新那个并触发回调`() = runTest {
        http.on("PROPFIND", urlSuffix = "/backups", code = 207, body = propfindBody)
        val payload = """
            {"version":1,"backupTime":"2026-10-01 12:00:00","backupId":"backup_20261001_120000",
             "data":{"schedule_names":"[\"甲\"]","current_schedule_id":"甲","schedule_甲_courses":"[]",
                     "holiday_entries":{"entries_2026":"[]"},
                     "holiday_end_course_exclusion":{"schema_version":1,"enabled":false,"startSection":1,"endSection":1},
                     "holiday_before_course_exclusion":{"schema_version":1,"enabled":false,"startSection":1,"endSection":1}}}
        """.trimIndent()
        http.on("GET", code = 200, body = payload)

        val repository = CourseRepository.getInstance()
        val result = manager.restoreLatestBackup(repository)
        assertTrue(result is RestoreResult.Success, "实际: $result")
        assertEquals("2026-10-01 12:00:00", (result as RestoreResult.Success).backupTime)

        // GET 的是**最新**那个（20261001 > 20260901）
        val get = http.requests.single { it.method == "GET" }
        assertEquals(
            "https://dav.example.com/remote.php/dav/NexioSchedule/backups/backup_20261001_120000.json",
            get.url,
        )
        // 恢复后必须回调（:app 侧用它刷新提醒）—— 原实现直接调 CourseReminderHelper
        assertEquals(1, holidayCallbackCount)
        // 数据确实写进去了
        assertEquals(listOf("甲"), repository.getScheduleNames())
    }

    @Test
    fun `restoreLatestBackup 的 404 与空列表分支`() = runTest {
        http.on("PROPFIND", urlSuffix = "/backups", code = 207, body = propfindBody)
        http.on("GET", code = 404)
        val r404 = manager.restoreLatestBackup(CourseRepository.getInstance())
        assertTrue(r404 is RestoreResult.Error && r404.message.contains("不存在"))

        http = FakeHttp().also { manager.http = it }
        http.on("PROPFIND", urlSuffix = "/backups", code = 207, body = "<d:multistatus/>")
        val empty = manager.restoreLatestBackup(CourseRepository.getInstance())
        assertTrue(empty is RestoreResult.Error && empty.message.contains("没有备份"))
    }

    @Test
    fun `deleteBackup 发 DELETE，404 也算成功`() = runTest {
        http.on("DELETE", code = 204)
        assertTrue(manager.deleteBackup("backup_20261001_120000").isSuccess)
        assertEquals("DELETE", http.requests.single().method)

        http = FakeHttp().also { manager.http = it }
        http.on("DELETE", code = 404)
        assertTrue(manager.deleteBackup("backup_x").isSuccess, "404 视为已删除")
    }

    // ── 时间戳格式 ───────────────────────────────

    @Test
    fun `备份时间戳格式与 SimpleDateFormat 一致`() {
        val input = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val output = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val samples = listOf(
            "20260101_000000", "20261231_235959", "20240229_120000",
            "20261001_120000", "19990704_083000", "20261101_000000",
        )
        samples.forEach { raw ->
            val java = input.parse(raw)!!
            val mine = parseCompactStamp(raw)!!
            assertEquals(output.format(java), mine.formatDisplayDateTime(), "显示格式: $raw")
            assertEquals(raw, mine.formatCompactStamp(), "紧凑格式: $raw")
        }
        // 非法输入
        assertEquals(null, parseCompactStamp("2026100_120000"))
        assertEquals(null, parseCompactStamp("20261001-120000"))
        assertEquals(null, parseCompactStamp("abcdefgh_120000"))
        assertEquals(null, parseCompactStamp("20261301_120000"))

        // ⚠ 一处**有意的**差异：`SimpleDateFormat` 默认 lenient，会把不存在的日期滚过去
        // （2026 非闰年，20260229 → 3 月 1 日）；本实现是严格的 → null。
        // 备份 id 只会由 formatCompactStamp 从真实日期生成，格式坏只可能是文件被改名，
        // 此时 BackupInfo 回退显示原始 id —— 比显示一个「滚出来的假日期」更诚实。
        assertEquals("2026-03-01 12:00:00", output.format(input.parse("20260229_120000")!!))
        assertEquals(null, parseCompactStamp("20260229_120000"))
        assertEquals("20260229_120000", BackupInfo("backup_20260229_120000", "x.json").backupTime)
    }
}
