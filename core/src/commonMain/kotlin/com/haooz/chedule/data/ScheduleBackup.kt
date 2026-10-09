package com.haooz.chedule.data

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ════════════════════════════════════════════════════════════════════════
//  课表备份 —— 单文件全包
//
//  备份与恢复的完整能力集中于此：
//  1. [SyncManager]    手动备份/恢复：驱动 WebDAV 上传/下载，并暴露可观察状态
//  2. [WebDavManager]  WebDAV：账号配置、上传、下载、远端文件列表、删除
//
//  两者共用 [CourseRepository.exportAllPreferences] / `importAllPreferences`
//  这一对全量序列化入口，备份格式由它们决定；**格式改动只需看那两处**。
//
//  与 `ScheduleExport.kt`（分享口令）的区别：备份走 WebDAV，保留完整
//  课表 + 节假日数据，不含外观等本机观感；分享走口令，只含课表本体。
//
//  账号密码存 `webdav_config` prefs。
//
//  ## 下沉 `:core` 时替换掉的东西（逐条对齐，未做「顺手优化」）
//
//  | 原来 | 现在 |
//  |---|---|
//  | `Context` + `getSharedPreferences("webdav_config", …)` | `AppStorage.store("webdav_config")`（**文件名与 4 个键名逐字未变**） |
//  | OkHttp（`OkHttpClient` / `Request` / `Credentials.basic`） | [HttpService]（`request()` 支持 PROPFIND / MKCOL / PUT / GET / DELETE） |
//  | `SimpleDateFormat` ×4 | `DateExt` 的 `formatCompactStamp` / `formatDisplayDateTime` / `parseCompactStamp` |
//  | Gson（备份外层信封） | [JsonSupport] |
//  | `Thread.sleep` | `delay`（本来就在协程里） |
//  | `Dispatchers.IO` | [ioDispatcher]（Kotlin/Native 上没有 `Dispatchers.IO`） |
//  | `CourseReminderHelper.onHolidayDataChanged(context)` | 构造时注入的回调（`:core` 不能反向依赖 `:app` 的提醒模块） |
//
//  ## ⚠ 行为对齐说明
//
//  请求方法、`Authorization`、`Depth` 头、状态码分支（2xx / 401 / 404 / 405）、
//  `ensureDir` 的重试次数（2 次）与退避（1s、2s）全部照抄。
//  **唯一可观测差异**：OkHttp 的 `response.message`（reason phrase）在 [HttpResult] 里没有对应物，
//  所以 `testConnection` 的失败文案由「服务器返回: <code> <message>」变成「服务器返回: <code>」。
// ════════════════════════════════════════════════════════════════════════

// ── 1. 同步管理器 ───────────────────────────────────────────

/** 同步管理器 - 备份/恢复（单例） */
class SyncManager private constructor() {

    companion object {
        private const val TAG = "SyncManager"

        @Volatile
        private var INSTANCE: SyncManager? = null

        private val lock = Any()

        fun getInstance(): SyncManager {
            return INSTANCE ?: synchronizedOn(lock) {
                INSTANCE ?: SyncManager().also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private var repository: CourseRepository? = null
    private var webDavManager: WebDavManager? = null

    var onSyncCompleted: (() -> Unit)? = null

    private val _syncState = MutableStateFlow<SyncOperationState>(SyncOperationState.Idle)
    val syncState = _syncState.asStateFlow()

    sealed class SyncOperationState {
        data object Idle : SyncOperationState()
        data object Running : SyncOperationState()
        data class BackupSuccess(val backupId: String) : SyncOperationState()
        data class RestoreSuccess(val backupTime: String) : SyncOperationState()
        data class Error(val message: String) : SyncOperationState()
    }

    fun start(repository: CourseRepository, webDavManager: WebDavManager) {
        this.repository = repository
        this.webDavManager = webDavManager
        NexioLog.d(TAG, "SyncManager started")
    }

    suspend fun backupNow(): SyncOperationState {
        val mgr = webDavManager ?: return SyncOperationState.Error("未初始化")
        val repo = repository ?: return SyncOperationState.Error("未初始化")
        if (!mgr.isConfigured()) return SyncOperationState.Error("请先配置 WebDAV 服务器")
        if (_syncState.value is SyncOperationState.Running) return SyncOperationState.Error("正在执行操作，请稍候")

        _syncState.value = SyncOperationState.Running
        NexioLog.d(TAG, "Starting backup...")

        try {
            val result = mgr.backupAllData(repo)

            when (result) {
                is BackupResult.Success -> {
                    _syncState.value = SyncOperationState.BackupSuccess(result.backupId)
                    onSyncCompleted?.invoke()
                    NexioLog.d(TAG, "Backup completed: ${result.backupId}")
                }
                is BackupResult.Error -> {
                    _syncState.value = SyncOperationState.Error(result.message)
                    NexioLog.e(TAG, "Backup failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            _syncState.value = SyncOperationState.Error("备份异常: ${e.message}")
            NexioLog.e(TAG, "Backup exception", e)
        }

        return _syncState.value
    }

    suspend fun restoreNow(): SyncOperationState {
        val mgr = webDavManager ?: return SyncOperationState.Error("未初始化")
        val repo = repository ?: return SyncOperationState.Error("未初始化")
        if (!mgr.isConfigured()) return SyncOperationState.Error("请先配置 WebDAV 服务器")
        if (_syncState.value is SyncOperationState.Running) return SyncOperationState.Error("正在执行操作，请稍候")

        _syncState.value = SyncOperationState.Running
        NexioLog.d(TAG, "Starting restore...")

        try {
            val result = mgr.restoreLatestBackup(repo)

            when (result) {
                is RestoreResult.Success -> {
                    _syncState.value = SyncOperationState.RestoreSuccess(result.backupTime)
                    onSyncCompleted?.invoke()
                    NexioLog.d(TAG, "Restore completed from ${result.backupTime}")
                }
                is RestoreResult.Error -> {
                    _syncState.value = SyncOperationState.Error(result.message)
                    NexioLog.e(TAG, "Restore failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            _syncState.value = SyncOperationState.Error("恢复异常: ${e.message}")
            NexioLog.e(TAG, "Restore exception", e)
        }

        return _syncState.value
    }

    fun resetState() {
        _syncState.value = SyncOperationState.Idle
    }
}

// ── 2. WebDAV 备份 ───────────────────────────────────────────

/** WebDAV 备份/恢复管理器 */
class WebDavManager(
    /**
     * 恢复成功后通知平台侧刷新提醒。
     *
     * 原实现直接调 `:app` 的 `CourseReminderHelper.onHolidayDataChanged(context)` ——
     * 那是反向依赖。改为构造时注入；`:app` 的调用点传
     * `{ CourseReminderHelper.onHolidayDataChanged(context) }`。
     */
    private val onHolidayDataChanged: () -> Unit = {},
) {

    private val configPrefs = AppStorage.store("webdav_config")

    /**
     * 超时与迁移前逐字一致：connect 10s / read 30s；原来没调 callTimeout，所以不设整体超时。
     *
     * `internal var` 而不是 `private val`：与 [StatsReporter.http] 同一个套路 ——
     * 让测试能塞一个假的实现进来断言「发出去的请求逐条不变」。
     * WebDAV 没法在本地起真服务器，这是唯一能锁住请求形状（方法 / Authorization / Depth / body）的办法。
     */
    internal var http: HttpService = createHttpService(
        HttpTimeouts(connectSeconds = 10, readSeconds = 30),
    )

    companion object {
        private const val TAG = "WebDavManager"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val BACKUP_DIR = "NexioSchedule"
        private const val BACKUPS_DIR = "backups"
        private const val BACKUP_FILE_PREFIX = "backup_"
        private const val BACKUP_FILE_SUFFIX = ".json"
    }

    var serverUrl: String
        get() = configPrefs.getString(KEY_SERVER_URL, "")
        set(value) = configPrefs.edit { putString(KEY_SERVER_URL, value.trimEnd('/')) }

    var username: String
        get() = configPrefs.getString(KEY_USERNAME, "")
        set(value) = configPrefs.edit { putString(KEY_USERNAME, value) }

    var password: String
        get() = configPrefs.getString(KEY_PASSWORD, "")
        set(value) = configPrefs.edit { putString(KEY_PASSWORD, value) }

    var lastSyncTime: Long
        get() = configPrefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = configPrefs.edit { putLong(KEY_LAST_SYNC_TIME, value) }

    fun isConfigured(): Boolean = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** 对应 OkHttp 的 `Credentials.basic(user, pass)`：`Basic ` + base64("user:pass")。 */
    private fun authHeader(): String = basicAuthHeader(username, password)

    private fun baseUrl() = "$serverUrl/$BACKUP_DIR"
    private fun backupsDirUrl() = "${baseUrl()}/$BACKUPS_DIR"
    private fun backupFileUrl(backupId: String) = "${backupsDirUrl()}/$backupId$BACKUP_FILE_SUFFIX"

    private fun authHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf("Authorization" to authHeader()) + extra

    // ============ WebDAV 基础操作 ============

    suspend fun testConnection(): Result<String> = withContext(ioDispatcher) {
        try {
            val response = http.request(
                method = "PROPFIND",
                url = serverUrl,
                headers = authHeaders(mapOf("Depth" to "0")),
            )
            when {
                response.isSuccessful -> Result.success("连接成功")
                response.code == 401 -> Result.failure(Exception("认证失败，请检查用户名和密码"))
                response.code == 404 -> Result.success("连接成功（目录将自动创建）")
                // ⚠ OkHttp 的 response.message（reason phrase）在 HttpResult 里没有对应物，故省略
                else -> Result.failure(Exception("服务器返回: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接失败: ${e.message}"))
        }
    }

    private suspend fun ensureDir(dirUrl: String, retries: Int = 2): Result<Boolean> {
        val checkResponse = http.request(
            method = "PROPFIND",
            url = dirUrl,
            headers = authHeaders(mapOf("Depth" to "0")),
        )
        if (checkResponse.isSuccessful) return Result.success(true)

        var lastError: String? = null
        for (attempt in 0..retries) {
            if (attempt > 0) {
                // 原实现是 Thread.sleep（JVM 专有）；这里本来就在协程里，用 delay
                delay(1000L * attempt)
            }
            val mkcolResponse = http.request(
                method = "MKCOL",
                url = dirUrl,
                headers = authHeaders(),
            )
            if (mkcolResponse.isSuccessful || mkcolResponse.code == 405) {
                return Result.success(true)
            }
            lastError = "${mkcolResponse.code}"
        }
        return Result.failure(Exception("创建目录失败: $lastError"))
    }

    private suspend fun ensureBackupDirs(): Result<Boolean> {
        val baseResult = ensureDir(baseUrl())
        if (baseResult.isFailure) return baseResult

        val backupsResult = ensureDir(backupsDirUrl())
        if (backupsResult.isFailure) return backupsResult

        return Result.success(true)
    }

    // ============ 备份操作 ============

    suspend fun backupAllData(repository: CourseRepository): BackupResult = withContext(ioDispatcher) {
        try {
            val dirResult = ensureBackupDirs()
            if (dirResult.isFailure) {
                return@withContext BackupResult.Error("创建目录失败: ${dirResult.exceptionOrNull()?.message}")
            }

            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val backupId = "$BACKUP_FILE_PREFIX${now.formatCompactStamp()}"
            val backupTime = now.formatDisplayDateTime()

            val allData = repository.exportAllPreferences()
            val backupData = mapOf(
                "version" to 1,
                "backupTime" to backupTime,
                "backupId" to backupId,
                "data" to allData
            )

            val json = toJsonElement(backupData).toString()
            val response = http.request(
                method = "PUT",
                url = backupFileUrl(backupId),
                body = json,
                contentType = "application/json",
                headers = authHeaders(),
            )

            if (response.isSuccessful) {
                lastSyncTime = Clock.System.now().toEpochMilliseconds()
                NexioLog.d(TAG, "Backup succeeded: $backupId")
                BackupResult.Success(backupId)
            } else {
                BackupResult.Error("上传备份失败: ${response.code}")
            }
        } catch (e: Exception) {
            NexioLog.e(TAG, "Backup exception", e)
            BackupResult.Error("备份异常: ${e.message}")
        }
    }

    // ============ 恢复操作 ============

    suspend fun restoreLatestBackup(repository: CourseRepository): RestoreResult = withContext(ioDispatcher) {
        try {
            val backups = listBackups()
            if (backups.isEmpty()) {
                return@withContext RestoreResult.Error("云端没有备份文件")
            }

            val latest = backups.maxByOrNull { it.backupId }
                ?: return@withContext RestoreResult.Error("无法获取最新备份")

            val response = http.request(
                method = "GET",
                url = backupFileUrl(latest.backupId),
                headers = authHeaders(),
            )

            when {
                response.isSuccessful -> {
                    val body = response.text.ifEmpty { "{}" }
                    @Suppress("UNCHECKED_CAST")
                    val backupData = runCatching {
                        jsonToPlainValue(parseJsonObject(body)) as? Map<String, Any>
                    }.getOrNull() ?: emptyMap()

                    @Suppress("UNCHECKED_CAST")
                    val data = backupData["data"] as? Map<String, Any> ?: emptyMap()

                    if (data.isEmpty()) {
                        return@withContext RestoreResult.Error("备份数据为空")
                    }

                    repository.importAllPreferences(data)
                    onHolidayDataChanged()
                    lastSyncTime = Clock.System.now().toEpochMilliseconds()
                    NexioLog.d(TAG, "Restore succeeded from ${latest.backupTime}")
                    RestoreResult.Success(latest.backupTime)
                }
                response.code == 404 -> RestoreResult.Error("备份文件不存在")
                else -> RestoreResult.Error("下载备份失败: ${response.code}")
            }
        } catch (e: Exception) {
            NexioLog.e(TAG, "Restore exception", e)
            RestoreResult.Error("恢复异常: ${e.message}")
        }
    }

    // ============ 备份列表 ============

    suspend fun listBackups(): List<BackupInfo> = withContext(ioDispatcher) {
        try {
            val response = http.request(
                method = "PROPFIND",
                url = backupsDirUrl(),
                headers = authHeaders(mapOf("Depth" to "1")),
            )
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.text
            val backups = mutableListOf<BackupInfo>()

            val hrefRegex = Regex("<D:href>([^<]+)</D:href>", RegexOption.IGNORE_CASE)
            val matches = hrefRegex.findAll(body)

            for (match in matches) {
                val href = match.groupValues[1]
                val fileName = href.substringAfterLast("/")

                if (fileName.startsWith(BACKUP_FILE_PREFIX) && fileName.endsWith(BACKUP_FILE_SUFFIX)) {
                    val backupId = fileName.removeSuffix(BACKUP_FILE_SUFFIX)
                    backups.add(
                        BackupInfo(
                            backupId = backupId,
                            fileName = fileName
                        )
                    )
                }
            }

            backups
        } catch (e: Exception) {
            NexioLog.e(TAG, "List backups exception", e)
            emptyList()
        }
    }

    suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val response = http.request(
                method = "DELETE",
                url = backupFileUrl(backupId),
                headers = authHeaders(),
            )
            if (response.isSuccessful || response.code == 404) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("删除备份失败: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("删除备份异常: ${e.message}"))
        }
    }
}

/**
 * Basic 认证头：`Basic ` + base64("user:pass")。
 *
 * 对应 OkHttp 的 `Credentials.basic(username, password)` —— 它内部就是
 * `base64(username + ":" + password, UTF-8)`。base64 用 stdlib 的
 * [kotlin.io.encoding.Base64]（跨平台，不需要额外依赖）。
 */
internal fun basicAuthHeader(username: String, password: String): String =
    "Basic " + kotlin.io.encoding.Base64.Default.encode("$username:$password".encodeToByteArray())

data class BackupInfo(
    val backupId: String,
    val fileName: String
) {
    val backupTime: String
        get() {
            return try {
                val id = backupId.removePrefix("backup_")
                // 原实现：SimpleDateFormat 解析 yyyyMMdd_HHmmss，再格式化成 yyyy-MM-dd HH:mm:ss
                parseCompactStamp(id)?.formatDisplayDateTime() ?: id
            } catch (_: Exception) {
                backupId
            }
        }
}

sealed class BackupResult {
    data class Success(val backupId: String) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

sealed class RestoreResult {
    data class Success(val backupTime: String) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}
