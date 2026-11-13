package com.haooz.chedule.data

import com.haooz.chedule.data.backup.BackupInfo
import com.haooz.chedule.data.backup.BackupResult
import com.haooz.chedule.data.backup.RestoreResult
import com.haooz.chedule.data.backup.WebDavManager
import com.haooz.chedule.data.backup.basicAuthHeader
import com.haooz.chedule.data.backup.encodePathSegment
import com.haooz.chedule.data.backup.normalizeRemotePath
import com.haooz.chedule.data.backup.resolveWebDavDirUrl
import com.haooz.chedule.data.platform.HttpResult
import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import com.haooz.chedule.data.util.formatCompactStamp
import com.haooz.chedule.data.util.formatDisplayDateTime
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseCompactStamp
import com.haooz.chedule.data.util.parseJsonObject
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * `WebDavManager` 的**请求契约**测试。
 *
 * ## 为什么是「契约」而不是「功能」
 *
 * 本地起不了 WebDAV 服务器，没法端到端测；而 WebDAV 的失败方式恰恰是
 * 「编译全绿、真机才发现」那类：方法名写错（PROPFIND vs POST）、少一个 `Depth` 头、
 * 认证头拼错 —— 全部只在连真服务器时才暴露。
 *
 * 所以塞一个**记录请求的假 [com.haooz.chedule.data.platform.HttpService]**，把「发出去的请求长什么样」逐条钉死：
 * 方法 / URL / `Authorization` / `Depth` / body / contentType。
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
        // webdav_config 这个 prefs 名与原 4 个键名是数据兼容契约
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

    // ── 自定义远端路径 ───────────────────────────

    @Test
    fun `未配置过路径时回落默认目录，显式留空则放根目录`() {
        // 老用户没有 remote_path 这个键 → 位置与迁移前一致，云端老备份还能读到
        assertFalse(store.contains("remote_path"))
        assertEquals("NexioSchedule/backups", manager.remotePath)
        assertEquals(
            "https://dav.example.com/remote.php/dav/NexioSchedule/backups",
            manager.backupDirUrl(),
        )

        // 显式留空 = 直接放服务器根目录（有些网盘必须指定已存在的子目录，但根目录仍是合法选项）
        manager.remotePath = ""
        assertTrue(store.contains("remote_path"))
        assertEquals("", manager.remotePath)
        assertEquals("https://dav.example.com/remote.php/dav", manager.backupDirUrl())
    }

    @Test
    fun `路径归一化：斜杠、空格与误粘贴的完整 URL`() {
        assertEquals("NexioSchedule/backups", normalizeRemotePath("/NexioSchedule/backups/"))
        assertEquals("a/b", normalizeRemotePath("//a///b//"))
        assertEquals("我的网盘/课表", normalizeRemotePath(" 我的网盘 / 课表 "))
        // 用户把整个 WebDAV 地址粘进「备份路径」里 → 只保留路径部分（去掉 scheme + 主机）
        assertEquals("dav/foo/bar", normalizeRemotePath("https://dav.example.com/dav/foo/bar"))
        assertEquals("dav", normalizeRemotePath("https://dav.example.com/dav"))
    }

    @Test
    fun `中文路径逐段百分号编码，已是转义的不二次编码`() {
        assertEquals(
            "https://dav.example.com/dav/%E6%88%91%E7%9A%84%E7%BD%91%E7%9B%98/%E8%AF%BE%E8%A1%A8%20%E5%A4%87%E4%BB%BD",
            resolveWebDavDirUrl("https://dav.example.com/dav/", "我的网盘/课表 备份"),
        )
        // 保留 RFC 3986 unreserved 字符；已有的 %XX 原样放过
        assertEquals("a-b_c.d~e", encodePathSegment("a-b_c.d~e"))
        assertEquals("a%20b", encodePathSegment("a%20b"))
        assertEquals("%2F", encodePathSegment("%2F"))
    }

    @Test
    fun `自定义路径：任意层级逐级建目录，文件直接落在该目录`() = runTest {
        manager.remotePath = "我的网盘/课表备份"
        http.on("PROPFIND", code = 207)
        http.on("PUT", code = 201)

        val result = manager.backupAllData(com.haooz.chedule.data.repo.CourseRepository.getInstance())
        assertTrue(result is BackupResult.Success, "实际: $result")

        // 根目录 + 每一级子目录各探一次（已在 → 不 MKCOL），最后 PUT
        assertEquals(listOf("PROPFIND", "PROPFIND", "PROPFIND", "PUT"), http.requests.map { it.method })
        // 集合地址统一带尾斜杠：避免服务端 301 后把 PROPFIND 降级成 GET
        assertEquals("https://dav.example.com/remote.php/dav/", http.requests[0].url)
        assertEquals(
            "https://dav.example.com/remote.php/dav/%E6%88%91%E7%9A%84%E7%BD%91%E7%9B%98/",
            http.requests[1].url,
        )
        assertEquals(
            "https://dav.example.com/remote.php/dav/%E6%88%91%E7%9A%84%E7%BD%91%E7%9B%98/%E8%AF%BE%E8%A1%A8%E5%A4%87%E4%BB%BD/",
            http.requests[2].url,
        )
        val put = http.requests[3]
        assertTrue(put.url.startsWith(http.requests[2].url + "backup_"), "实际: ${put.url}")
        assertTrue(put.url.endsWith(".json"))
    }

    @Test
    fun `目录建不出来仍尝试写入，失败文案指向路径`() = runTest {
        manager.remotePath = "不允许建目录的地方"
        // PROPFIND 全部 404，MKCOL 全部 409（父目录缺失 / 服务端禁建）
        http.on("PROPFIND", code = 404)
        http.on("MKCOL", code = 409)
        http.on("PUT", code = 409)

        val result = manager.backupAllData(com.haooz.chedule.data.repo.CourseRepository.getInstance())
        assertTrue(result is BackupResult.Error, "实际: $result")
        val message = (result as BackupResult.Error).message
        assertTrue(message.contains("409"), "实际: $message")
        assertTrue(message.contains("备份路径"), "建目录失败时应提示路径问题，实际: $message")
        // 关键：MKCOL 失败**没有**提前返回，PUT 照样发出去了
        assertTrue(http.requests.any { it.method == "PUT" })
    }

    // ── 连接测试 ─────────────────────────────────

    @Test
    fun `testConnection 发 PROPFIND 带 Depth 0`() = runTest {
        http.on("PROPFIND", code = 207)
        val result = manager.testConnection()
        assertEquals("连接成功", result.getOrNull())
        val r = http.requests.single()
        assertEquals("PROPFIND", r.method)
        // 探的是**备份目录**，不是服务器根 —— 自定义路径填错要在这里就暴露
        assertEquals("https://dav.example.com/remote.php/dav/NexioSchedule/backups/", r.url)
        assertEquals("Basic dXNlcjpwYXNz", r.headers["Authorization"])
        assertEquals("0", r.headers["Depth"])
        // 标准 PROPFIND 请求体：部分服务端不带 body 会 400
        assertEquals("application/xml; charset=utf-8", r.contentType)
        assertTrue(r.body!!.contains("propfind"), "实际: ${r.body}")
    }

    @Test
    fun `testConnection 的 401 与 404 分支`() = runTest {
        http.on("PROPFIND", code = 401)
        assertTrue(manager.testConnection().exceptionOrNull()?.message!!.contains("认证失败"))

        // 目录 404 但根目录可达 → 目录会在备份时自动创建
        http = FakeHttp().also { manager.http = it }
        http.on("PROPFIND", urlSuffix = "/backups/", code = 404)
        http.on("PROPFIND", code = 207)
        assertEquals("连接成功（目录将自动创建）", manager.testConnection().getOrNull())
        assertEquals(2, http.requests.size, "404 时要再探一次根目录以区分「地址错了」")

        // 目录与根目录都 404 之外的错 → 报服务器错误，不能误报「连接成功」
        http = FakeHttp().also { manager.http = it }
        http.on("PROPFIND", urlSuffix = "/backups/", code = 404)
        http.on("PROPFIND", code = 500)
        assertTrue(manager.testConnection().exceptionOrNull()?.message!!.contains("服务器返回: 500"))
    }

    // ── 备份 ─────────────────────────────────────

    @Test
    fun `backupAllData 的请求序列与 body 形状`() = runTest {
        // 目录已存在 → PROPFIND 都返回 207，不会走 MKCOL 重试
        http.on("PROPFIND", code = 207)
        http.on("PUT", code = 201)

        val repository = com.haooz.chedule.data.repo.CourseRepository.getInstance()
        val result = manager.backupAllData(repository)
        assertTrue(result is BackupResult.Success, "实际: $result")

        // 三级 PROPFIND（根 / NexioSchedule / backups）+ 一次 PUT，顺序与 URL 都要对
        assertEquals(listOf("PROPFIND", "PROPFIND", "PROPFIND", "PUT"), http.requests.map { it.method })
        assertEquals(
            "https://dav.example.com/remote.php/dav/",
            http.requests[0].url,
        )
        assertEquals(
            "https://dav.example.com/remote.php/dav/NexioSchedule/",
            http.requests[1].url,
        )
        assertEquals(
            "https://dav.example.com/remote.php/dav/NexioSchedule/backups/",
            http.requests[2].url,
        )

        val put = http.requests[3]
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

        val result = manager.backupAllData(com.haooz.chedule.data.repo.CourseRepository.getInstance())
        assertTrue(result is BackupResult.Success, "实际: $result")

        // 逐级：根 → NexioSchedule → backups，每级「探不到就 MKCOL」
        assertEquals(
            listOf("PROPFIND", "MKCOL", "PROPFIND", "MKCOL", "PROPFIND", "MKCOL", "PUT"),
            http.requests.map { it.method },
        )
        assertEquals(
            listOf(
                "https://dav.example.com/remote.php/dav/",
                "https://dav.example.com/remote.php/dav/NexioSchedule/",
                "https://dav.example.com/remote.php/dav/NexioSchedule/backups/",
            ),
            http.requests.filter { it.method == "MKCOL" }.map { it.url },
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
        http.on("PROPFIND", urlSuffix = "/backups/", code = 207, body = propfindBody)
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

    /** `<href>` 的命名空间前缀不是协议约定的一部分：默认命名空间 / lp1 / ns0 都必须能解析。 */
    @Test
    fun `列表解析不依赖命名空间前缀`() = runTest {
        val body = """
            <?xml version="1.0"?>
            <multistatus xmlns="DAV:">
              <response><href>/dav/backups/</href></response>
              <response><lp1:href xmlns:lp1="DAV:">/dav/backups/backup_20261001_120000.json</lp1:href></response>
              <response><ns0:href xmlns:ns0="DAV:">https://dav.example.com/dav/backups/backup_20260901_090000.json</ns0:href></response>
            </multistatus>
        """.trimIndent()
        http.on("PROPFIND", urlSuffix = "/backups/", code = 207, body = body)
        assertEquals(
            listOf("backup_20261001_120000", "backup_20260901_090000"),
            manager.listBackups().map { it.backupId },
        )
    }

    /**
     * PROPFIND 被重定向成 GET 时，拿到的是 HTML 目录页。
     * 旧实现会把它当「空目录」，用户只看到「暂无备份记录」；现在必须报错。
     */
    @Test
    fun `云端返回 HTML 目录页时报错而不是空列表`() = runTest {
        http.on(
            "PROPFIND",
            urlSuffix = "/backups/",
            code = 200,
            body = """<html><body><a href="backup_20261001_120000.json">x</a></body></html>""",
        )
        val listed = manager.listBackupsResult()
        assertTrue(listed.isFailure, "实际: $listed")
        assertTrue(listed.exceptionOrNull()!!.message!!.contains("WebDAV"), "实际: ${listed.exceptionOrNull()?.message}")

        http = FakeHttp().also { manager.http = it }
        http.on("PROPFIND", code = 200, body = "<html></html>")
        val connected = manager.testConnection()
        assertTrue(connected.isFailure, "实际: $connected")
    }

    @Test
    fun `云端列表失败保留状态码`() = runTest {
        http.on("PROPFIND", urlSuffix = "/backups/", code = 401)
        val result = manager.listBackupsResult()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
    }

    /**
     * 「只以文件确定」的核心：目录名换个毫不相干的也不会影响识别 ——
     * 用户可能把备份放在网盘里任何一层，目录叫什么、有几层都不该成为约束。
     */
    @Test
    fun `换任意目录名都能列出备份：只按文件名识别`() = runTest {
        manager.remotePath = "随便什么名字/深层目录"
        val body = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/remote.php/dav/x/随便什么名字/深层目录/</d:href></d:response>
              <d:response><d:href>/remote.php/dav/x/随便什么名字/深层目录/backup_20261001_120000.json</d:href></d:response>
              <d:response><d:href>/remote.php/dav/x/随便什么名字/深层目录/notes.txt</d:href></d:response>
              <d:response><d:href>/remote.php/dav/x/随便什么名字/深层目录/backup_20261001_120000.json.bak</d:href></d:response>
            </d:multistatus>
        """.trimIndent()
        http.on("PROPFIND", code = 207, body = body)
        val backups = manager.listBackups()
        assertEquals(listOf("backup_20261001_120000"), backups.map { it.backupId })
        // PROPFIND 打在自定义目录上，而不是写死的 NexioSchedule/backups
        assertTrue(http.requests.single().url.endsWith("%E9%9A%8F%E4%BE%BF%E4%BB%80%E4%B9%88%E5%90%8D%E5%AD%97/%E6%B7%B1%E5%B1%82%E7%9B%AE%E5%BD%95/"), "实际: ${http.requests.single().url}")
    }

    @Test
    fun `restoreLatestBackup 取最新那个并触发回调`() = runTest {
        http.on("PROPFIND", urlSuffix = "/backups/", code = 207, body = propfindBody)
        val payload = """
            {"version":1,"backupTime":"2026-10-01 12:00:00","backupId":"backup_20261001_120000",
             "data":{"schedule_names":"[\"甲\"]","current_schedule_id":"甲","schedule_甲_courses":"[]",
                     "holiday_entries":{"entries_2026":"[]"},
                     "holiday_end_course_exclusion":{"schema_version":1,"enabled":false,"startSection":1,"endSection":1},
                     "holiday_before_course_exclusion":{"schema_version":1,"enabled":false,"startSection":1,"endSection":1}}}
        """.trimIndent()
        http.on("GET", code = 200, body = payload)

        val repository = com.haooz.chedule.data.repo.CourseRepository.getInstance()
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

    /**
     * 云端备份含「孤儿时间配置绑定」时恢复必须成功 —— 用户真实踩坑（2026-10-10 报障）。
     *
     * 云端备份（如 2026-10-01 11:36 那份）由当时的旧版 App 导出，
     * `data` 里带 `schedule_time_config_默认课表`（课表已删、绑定键残留）。
     * 旧校验要求绑定名 ∈ schedule_names → `restoreLatestBackup` 的 catch 把
     * 异常包成用户看到的 **「恢复异常: Invalid full schedule backup:
     * time configuration belongs to an unknown schedule」**。
     * 修复 `9d13813` 后孤儿绑定被豁免（与本地恢复共用同一个
     * `importAllPreferences` → `validateFullScheduleBackupStructure`）——
     * 本用例钉住云端链路同样被覆盖。
     *
     * 断言全部走 repository 自身（getScheduleNames / exportAllPreferences），
     * 不依赖本地 store —— CourseRepository 是 JVM 级单例，本地 store 可能不是同一份。
     */
    @Test
    fun `云端备份含孤儿时间配置绑定也能恢复成功`() = runTest {
        http.on("PROPFIND", urlSuffix = "/backups/", code = 207, body = propfindBody)
        // 与真实 41 键备份同构的云端信封（data 里带孤儿绑定）
        val payload = """
            {"version":1,"backupTime":"2026-10-01 11:36:00","backupId":"backup_20261001_113600",
             "data":{"schedule_names":"[\"大二上\"]",
                     "schedule_folders":"[{\"id\":\"folder_default\",\"name\":\"默认文件夹\",\"schedules\":[\"大二上\"]}]",
                     "current_schedule_id":"大二上",
                     "schedule_大二上_courses":"[{\"name\":\"高等数学\",\"dayOfWeek\":1,\"startSection\":1,\"endSection\":2,\"selectedWeeks\":[1,2]}]",
                     "schedule_time_config_大二上":0,
                     "schedule_time_config_默认课表":0,
                     "holiday_entries":{"entries_2026":"[]"},
                     "holiday_end_course_exclusion":{"schema_version":1,"enabled":false,"startSection":1,"endSection":1},
                     "holiday_before_course_exclusion":{"schema_version":1,"enabled":false,"startSection":1,"endSection":1}}}
        """.trimIndent()
        http.on("GET", code = 200, body = payload)

        val repository = com.haooz.chedule.data.repo.CourseRepository.getInstance()
        val result = manager.restoreLatestBackup(repository)
        assertTrue(result is RestoreResult.Success, "实际: $result")

        assertEquals(listOf("大二上"), repository.getScheduleNames())
        assertEquals("大二上", repository.getCurrentScheduleId())
        assertEquals(1, holidayCallbackCount)
        // 孤儿绑定作为正常残留原样落库（修复注释：残留写回去与现状一致，无害）
        val exported = repository.exportAllPreferences()
        assertTrue(
            "schedule_time_config_默认课表" in exported.keys,
            "云端恢复后孤儿绑定应原样保留，实际键：${exported.keys.filter { it.startsWith("schedule_time_config_") }}",
        )
    }

    @Test
    fun `restoreLatestBackup 的 404 与空列表分支`() = runTest {
        http.on("PROPFIND", urlSuffix = "/backups/", code = 207, body = propfindBody)
        http.on("GET", code = 404)
        val r404 = manager.restoreLatestBackup(com.haooz.chedule.data.repo.CourseRepository.getInstance())
        assertTrue(r404 is RestoreResult.Error && r404.message.contains("不存在"))

        http = FakeHttp().also { manager.http = it }
        http.on("PROPFIND", urlSuffix = "/backups/", code = 207, body = "<d:multistatus/>")
        val empty = manager.restoreLatestBackup(com.haooz.chedule.data.repo.CourseRepository.getInstance())
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
