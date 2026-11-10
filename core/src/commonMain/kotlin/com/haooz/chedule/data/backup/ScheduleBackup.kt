package com.haooz.chedule.data.backup

import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.synchronizedOn
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.util.formatCompactStamp
import com.haooz.chedule.data.util.formatDisplayDateTime
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseCompactStamp
import com.haooz.chedule.data.util.parseJsonElement
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.data.util.toJsonElement
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ════════════════════════════════════════════════════════════════════════
//  课表备份 —— 单文件全包
//
//  1. [SyncManager]    手动备份/恢复：驱动 WebDAV 上传/下载，并暴露可观察状态
//  2. [WebDavManager]  WebDAV：账号配置、上传、下载、远端文件列表、删除
//
//  两者共用 [CourseRepository.exportAllPreferences] / `importAllPreferences` 全量
//  序列化入口，备份格式由它们决定；格式改动只需看那两处。
//
//  ⚠ 数据兼容红线：prefs 文件名 `webdav_config` 与原 4 个键名、备份 JSON 字段名逐字未变；
//  请求方法 / `Authorization` / `Depth` 头 / 状态码分支（2xx / 401 / 403 / 404 / 405）/
//  `MKCOL` 重试次数（2 次）与退避（1s、2s）全部照抄，超时 connect 10s / read 30s。
//  唯一可观测差异：失败文案不含 reason phrase，为「服务器返回: <code>」。
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
    /** 恢复成功后通知平台侧刷新提醒（构造时注入，避免 `:core` 反向依赖 `:app`）。 */
    private val onHolidayDataChanged: () -> Unit = {},
) {

    private val configPrefs = AppStorage.store("webdav_config")

    /**
     * ⚠ 超时与迁移前一致：connect 10s / read 30s；不设整体 callTimeout。
     *
     * `internal var` 是为了让测试注入假实现，锁住请求形状（WebDAV 无法本地起真服务器）。
     */
    internal var http: HttpService = createHttpService(
        HttpTimeouts(connectSeconds = 10, readSeconds = 30),
    )

    companion object {
        private const val TAG = "WebDavManager"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        /** 新增键（2026-10-17 自定义路径）；老用户没这个键 → 走 [DEFAULT_REMOTE_PATH]。 */
        private const val KEY_REMOTE_PATH = "remote_path"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val BACKUP_FILE_PREFIX = "backup_"
        private const val BACKUP_FILE_SUFFIX = ".json"

        /** 从未设置过路径时的兜底值：迁移前的两级目录，保证老用户的云端备份还能读到。 */
        const val DEFAULT_REMOTE_PATH = "NexioSchedule/backups"
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

    /**
     * 远端备份目录，相对 [serverUrl]，可任意层级、任意名字，例如 `我的网盘/课表备份`。
     *
     * - 空串 = 直接放在服务器根目录（部分网盘不允许，此时填一个已存在的子目录即可）。
     * - 从未设置过 → [DEFAULT_REMOTE_PATH]，老用户的备份位置不变。
     *
     * ⚠ 目录名只是「放在哪」，**不构成任何约束**：备份一律只按文件名
     *   `backup_<时间戳>.json` 识别（见 [listBackups]），目录叫什么都能读回来。
     */
    var remotePath: String
        get() = if (configPrefs.contains(KEY_REMOTE_PATH)) {
            configPrefs.getString(KEY_REMOTE_PATH, "")
        } else {
            DEFAULT_REMOTE_PATH
        }
        set(value) = configPrefs.edit { putString(KEY_REMOTE_PATH, normalizeRemotePath(value)) }

    var lastSyncTime: Long
        get() = configPrefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = configPrefs.edit { putLong(KEY_LAST_SYNC_TIME, value) }

    fun isConfigured(): Boolean = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** Basic 认证头：`Basic ` + base64("user:pass")。 */
    private fun authHeader(): String = basicAuthHeader(username, password)

    /** 备份目录的完整 URL（逐段百分号编码，中文路径可用）。 */
    fun backupDirUrl(): String = resolveWebDavDirUrl(serverUrl, remotePath)

    private fun backupFileUrl(backupId: String) = "${backupDirUrl()}/$backupId$BACKUP_FILE_SUFFIX"

    private fun authHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf("Authorization" to authHeader()) + extra

    // ============ WebDAV 基础操作 ============

    /**
     * 探测**备份目录本身**（不只是服务器根）——自定义路径填错时，这里就要报错，
     * 而不是等到备份时才炸。
     */
    suspend fun testConnection(): Result<String> = withContext(ioDispatcher) {
        try {
            val authFail = Result.failure<String>(Exception("认证失败，请检查用户名和密码"))
            val dir = backupDirUrl()
            val response = http.request(
                method = "PROPFIND",
                url = dir,
                headers = authHeaders(mapOf("Depth" to "0")),
            )
            when {
                response.isSuccessful -> Result.success("连接成功")
                response.code == 401 -> authFail
                response.code == 403 -> Result.failure(Exception("无访问权限，请检查账号权限或备份路径"))
                // 路径留空（= 直接放根目录）时两者是同一个 URL，没必要再探一次
                response.code == 404 && dir == serverUrl -> Result.success("连接成功（目录将自动创建）")
                response.code == 404 -> {
                    // 404 可能是「路径还没建」，也可能是「服务器地址根本不对」——
                    // 再探一次根目录把两者分开，否则地址错了也会报「连接成功」。
                    val root = http.request(
                        method = "PROPFIND",
                        url = serverUrl,
                        headers = authHeaders(mapOf("Depth" to "0")),
                    )
                    when {
                        root.isSuccessful || root.code == 404 || root.code == 405 ->
                            Result.success("连接成功（目录将自动创建）")
                        root.code == 401 -> authFail
                        // ⚠ reason phrase 在 HttpResult 里没有对应物，故省略
                        else -> Result.failure(Exception("服务器返回: ${root.code}"))
                    }
                }
                // ⚠ reason phrase 在 HttpResult 里没有对应物，故省略
                else -> Result.failure(Exception("服务器返回: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接失败: ${e.message}"))
        }
    }

    private suspend fun dirExists(url: String): Boolean {
        val response = http.request(
            method = "PROPFIND",
            url = url,
            headers = authHeaders(mapOf("Depth" to "0")),
        )
        return response.isSuccessful
    }

    /** @param retries 与迁移前一致（共 3 次尝试，退避 1s、2s）。 */
    private suspend fun mkcol(url: String, retries: Int = 2): Boolean {
        var lastCode = -1
        for (attempt in 0..retries) {
            if (attempt > 0) {
                // 已在协程内，用 delay 而非阻塞 sleep
                delay(1000L * attempt)
            }
            val response = http.request(
                method = "MKCOL",
                url = url,
                headers = authHeaders(),
            )
            // 405 = 已存在（WebDAV 规范要求 MKCOL 对已存在资源返回 405）
            if (response.isSuccessful || response.code == 405) return true
            lastCode = response.code
        }
        NexioLog.w(TAG, "MKCOL 失败: $url -> $lastCode")
        return false
    }

    /**
     * 自顶向下逐级确保备份目录存在。
     *
     * ⚠ 必须逐级：WebDAV 的 MKCOL **不递归**，父目录不存在时返回 409。迁移前只建两级
     * 固定目录，自定义路径可以有任意层数，所以这里按段依次建。
     *
     * @return 目录是否确认存在。**false 不是致命错误**：部分网盘禁 MKCOL 但允许 PUT，
     *   调用方仍应尝试写入，由写入结果决定成败。
     */
    private suspend fun ensureBackupDir(): Boolean {
        for (url in remoteUrlChain()) {
            if (dirExists(url)) continue
            if (mkcol(url)) continue
            return false
        }
        return true
    }

    /** [serverUrl] 及其下每一级子目录的 URL，由浅到深。 */
    private fun remoteUrlChain(): List<String> {
        val base = serverUrl.trimEnd('/')
        val segments = normalizeRemotePath(remotePath).split('/').filter { it.isNotEmpty() }
        val chain = ArrayList<String>(segments.size + 1)
        var current = base
        chain.add(current)
        for (segment in segments) {
            current = "$current/${encodePathSegment(segment)}"
            chain.add(current)
        }
        return chain
    }

    // ============ 备份操作 ============

    suspend fun backupAllData(repository: CourseRepository): BackupResult = withContext(ioDispatcher) {
        try {
            // 目录建不出来不算致命：部分网盘禁 MKCOL 但允许直接 PUT，照样写一次再判成败。
            val dirReady = ensureBackupDir()

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
                BackupResult.Error(
                    if (dirReady) "上传备份失败: ${response.code}"
                    else "上传备份失败: ${response.code}，请确认备份路径存在且有写入权限"
                )
            }
        } catch (e: Exception) {
            NexioLog.e(TAG, "Backup exception", e)
            BackupResult.Error("备份异常: ${e.message}")
        }
    }

    // ============ 恢复操作 ============

    suspend fun restoreLatestBackup(repository: CourseRepository): RestoreResult = withContext(
        ioDispatcher
    ) {
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
                url = backupDirUrl(),
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

/** Basic 认证头：`Basic ` + base64("user:pass")，用 stdlib 的 [kotlin.io.encoding.Base64]。 */
internal fun basicAuthHeader(username: String, password: String): String =
    "Basic " + Base64.encode("$username:$password".encodeToByteArray())

/**
 * 归一化用户填写的远端路径：去首尾斜杠、合并重复斜杠、顺手剥掉误粘贴的完整 URL。
 *
 * ⚠ 空串是**合法值**（= 服务器根目录），所以这里不把空串兜底成默认路径 ——
 * 兜底放在 [WebDavManager.remotePath] 的读取侧按「键是否存在」判断，
 * 否则用户就永远没法选「直接放根目录」了。
 */
fun normalizeRemotePath(raw: String): String {
    var value = raw.trim()
    val lower = value.lowercase()
    for (scheme in arrayOf("https://", "http://")) {
        if (lower.startsWith(scheme)) {
            // 用户整段 WebDAV 地址粘进来了 → 只留路径部分（`substringAfter` 无斜杠时返回空串）
            value = value.substring(scheme.length).substringAfter('/', "")
            break
        }
    }
    return value.split('/').filter { it.isNotBlank() }.joinToString("/") { it.trim() }
}

/** 把 [serverUrl] 与 [remotePath] 拼成备份目录 URL：路径逐段百分号编码，中文目录名可用。 */
fun resolveWebDavDirUrl(serverUrl: String, remotePath: String): String {
    val base = serverUrl.trim().trimEnd('/')
    val segments = normalizeRemotePath(remotePath).split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return base
    return buildString {
        append(base)
        for (segment in segments) {
            append('/')
            append(encodePathSegment(segment))
        }
    }
}

private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * 对单个路径段做百分号编码，保留 RFC 3986 的 unreserved 字符。
 *
 * ⚠ 不能直接用平台 API：`java.net.URLEncoder` 是 JVM 专有，iOS 侧没有对应物，
 * 而 Kotlin 的 String 在 Native 上也不暴露 `stringByAddingPercentEncoding…`。
 *
 * 已是合法 `%XX` 的三字符序列**不二次编码**（用户可能自己粘了编码后的路径）。
 */
internal fun encodePathSegment(segment: String): String {
    val bytes = segment.encodeToByteArray()
    val out = StringBuilder(bytes.size)
    var i = 0
    while (i < bytes.size) {
        val b = bytes[i].toInt() and 0xFF
        val c = b.toChar()
        when {
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' ||
                c == '-' || c == '.' || c == '_' || c == '~' -> out.append(c)
            c == '%' && i + 2 < bytes.size &&
                isHexByte(bytes[i + 1]) && isHexByte(bytes[i + 2]) -> {
                out.append(c)
                    .append((bytes[i + 1].toInt() and 0xFF).toChar())
                    .append((bytes[i + 2].toInt() and 0xFF).toChar())
                i += 2
            }
            else -> out.append('%').append(HEX_DIGITS[b shr 4]).append(HEX_DIGITS[b and 0x0F])
        }
        i++
    }
    return out.toString()
}

private fun isHexByte(byte: Byte): Boolean {
    val v = byte.toInt() and 0xFF
    return v in '0'.code..'9'.code || v in 'A'.code..'F'.code || v in 'a'.code..'f'.code
}

data class BackupInfo(
    val backupId: String,
    val fileName: String
) {
    val backupTime: String
        get() {
            return try {
                val id = backupId.removePrefix("backup_")
                // 解析 yyyyMMdd_HHmmss，格式化成 yyyy-MM-dd HH:mm:ss
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

internal fun <T> withValidatedFullScheduleBackup(
    data: Map<String, Any>,
    legacyScheduleId: String? = null,
    restore: (Map<String, HolidayManager.BackupData>) -> T,
): T {
    validateFullScheduleBackupStructure(data)
    val holidayBackup = HolidayManager.decodeAllSchedulesBackupData(data, legacyScheduleId)
    return restore(holidayBackup)
}

/** 对应 Gson 的 `x?.isJsonPrimitive == true && x.asJsonPrimitive.isString && x.asString.isNotBlank()`。 */
private fun JsonElement?.isNonBlankJsonString(): Boolean =
    this is JsonPrimitive && isString && content.isNotBlank()

/** Export an empty slot for schedules that have never had a course persisted. */
internal fun ensureEmptyScheduleCourseEntries(data: MutableMap<String, Any>, scheduleNames: List<String>) {
    scheduleNames.forEach { name ->
        val key = "schedule_${name}_courses"
        // MutableMap.putIfAbsent 是 JVM 专有，等价展开
        if (!data.containsKey(key)) data[key] = "[]"
    }
}

internal fun shouldPreserveRestoredFolderMembership(data: Map<String, Any>): Boolean =
    data.containsKey("schedule_folders")

internal fun normalizeFullScheduleBackup(data: Map<String, Any>): Map<String, Any> {
    val names = validateFullScheduleBackupStructure(data)
    return if (data.containsKey("schedule_names")) {
        data
    } else {
        data + ("schedule_names" to encodeStringList(names))
    }
}

fun validateFullScheduleBackupStructure(data: Map<String, Any>): List<String> {
    val names = if (data.containsKey("schedule_names")) {
        val rawNames = data["schedule_names"]
        when (rawNames) {
            is String -> runCatching {
                jsonToPlainValue(parseJsonElement(rawNames)) as? List<*>
            }.getOrNull()
            is List<*> -> rawNames
            else -> null
        }
    } else {
        val courseScheduleNames = data.keys.asSequence()
            .filter { it.startsWith("schedule_") && !it.startsWith("schedule_time_config_") && it.endsWith("_courses") }
            .map { it.removePrefix("schedule_").removeSuffix("_courses") }
        val boundScheduleNames = data.keys.asSequence()
            .filter { it.startsWith("schedule_time_config_") }
            .map { it.removePrefix("schedule_time_config_") }
        (courseScheduleNames + boundScheduleNames).distinct().toList()
    } ?: throw IllegalArgumentException("Invalid full schedule backup: malformed schedule names")
    require(names.isNotEmpty() && names.all { it is String && it.isNotBlank() }) {
        "Invalid full schedule backup: schedule names are empty or malformed"
    }
    val scheduleNames = names.filterIsInstance<String>()
    require(scheduleNames.distinct().size == scheduleNames.size) {
        "Invalid full schedule backup: duplicate schedule names"
    }
    require(scheduleNames.all { name ->
        data.containsKey("schedule_${name}_courses") ||
            data.containsKey("schedule_time_config_$name")
    }) { "Invalid full schedule backup: no matching schedule data" }

    if (data.containsKey("schedule_folders")) {
        val rawFolders = data["schedule_folders"] as? String
            ?: throw IllegalArgumentException("Invalid schedule folders in backup")
        // kotlinx 的 JsonElement 用类型判断 + JsonPrimitive.isString 表达与原 Gson 相同的语义。
        val folders = runCatching { parseJsonElement(rawFolders) }.getOrNull()
        require(folders is JsonArray) { "Invalid schedule folder in backup" }
        folders.forEach { folder ->
            require(folder is JsonObject) { "Invalid schedule folder in backup" }
            val id = folder["id"]
            val name = folder["name"]
            val members = folder["schedules"]
            require(
                id.isNonBlankJsonString() &&
                    name.isNonBlankJsonString() &&
                    members is JsonArray && members.all { member ->
                        member is JsonPrimitive && member.isString && member.content in scheduleNames
                    }
            ) { "Invalid schedule folder in backup" }
        }
    }

    scheduleNames.forEach { name ->
        val totalWeeksKey = "schedule_${name}_total_weeks"
        val totalWeeks = if (!data.containsKey(totalWeeksKey)) {
            20
        } else {
            val number = (data[totalWeeksKey] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid schedule total weeks in backup")
            require(number.isFinite() && number % 1.0 == 0.0 &&
                number in 1.0..CourseRepository.MAX_TOTAL_WEEKS.toDouble()
            ) { "Invalid schedule total weeks in backup" }
            number.toInt()
        }
        val currentWeekKey = "schedule_${name}_current_week"
        if (data.containsKey(currentWeekKey)) {
            val week = (data[currentWeekKey] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid schedule current week in backup")
            require(
                week.isFinite() && week % 1.0 == 0.0 &&
                    week >= Int.MIN_VALUE.toDouble() && week <= Int.MAX_VALUE.toDouble(),
            ) { "Invalid schedule current week in backup" }
        }
        val classStartKey = "schedule_${name}_class_start_time"
        val classStartDate = if (data.containsKey(classStartKey)) {
            val raw = data[classStartKey] as? String
            CourseRepository.normalizeClassStartDate(raw)
                ?: throw IllegalArgumentException("Invalid schedule semester start date in backup")
        } else null

        val coursesKey = "schedule_${name}_courses"
        if (data.containsKey(coursesKey)) {
            val rawCourses = data[coursesKey] as? String
                ?: throw IllegalArgumentException("Invalid full schedule backup: malformed course data")
            val courses = runCatching {
                jsonToPlainValue(parseJsonElement(rawCourses)) as? List<*>
            }.getOrNull()
            require(courses != null && courses.all { it is Map<*, *> }) {
                "Invalid full schedule backup: malformed course data"
            }
            @Suppress("UNCHECKED_CAST")
            validateSingleScheduleCourseData(
                courses.filterIsInstance<Map<String, Any>>(),
                CourseRepository.MAX_TOTAL_WEEKS,
            )
        }

        val timeConfigBindingKey = "schedule_time_config_$name"
        if (data.containsKey(timeConfigBindingKey)) {
            val binding = (data[timeConfigBindingKey] as? Number)?.toDouble()
            require(
                binding != null && binding.isFinite() && binding % 1.0 == 0.0 &&
                    binding >= 0.0 && binding < Long.MAX_VALUE.toDouble(),
            ) { "Invalid full schedule backup: malformed time configuration binding" }
        }

        val rulesKey = "schedule_${name}_teaching_week_reorganizations"
        if (data.containsKey(rulesKey)) {
            val rawRules = data[rulesKey] as? String
                ?: throw IllegalArgumentException("Invalid teaching-week reorganization backup entry")
            require(classStartDate != null) {
                "Teaching-week reorganization backup requires a valid semester start date"
            }
            TeachingWeekReorganization.decode(rawRules, Int.MAX_VALUE)
        }
    }

    if (data.containsKey("current_schedule_id")) {
        val currentScheduleId = data["current_schedule_id"] as? String
        require(currentScheduleId != null && currentScheduleId in scheduleNames) {
            "Invalid full schedule backup: current schedule id is malformed"
        }
    }

    val knownRuleSuffix = "_teaching_week_reorganizations"
    data.keys.asSequence()
        .filter { it.startsWith("schedule_") && !it.startsWith("schedule_time_config_") && it.endsWith(knownRuleSuffix) }
        .forEach { key ->
            val scheduleName = key.removePrefix("schedule_").removeSuffix(knownRuleSuffix)
            require(scheduleName in scheduleNames) {
                "Invalid full schedule backup: reorganization belongs to an unknown schedule"
            }
        }
    data.keys.asSequence()
        .filter { it.startsWith("schedule_") && !it.startsWith("schedule_time_config_") && it.endsWith("_courses") }
        .forEach { key ->
            val scheduleName = key.removePrefix("schedule_").removeSuffix("_courses")
            require(scheduleName in scheduleNames) {
                "Invalid full schedule backup: course data belongs to an unknown schedule"
            }
        }
    data.keys.asSequence()
        .filter {
            it.startsWith("schedule_") && it != "schedule_names" && it != "schedule_folders" &&
                // ⚠ schedule_folder_map 是已无引用的老版本遗留键，但仍留在老用户 prefs 里、
                // 会被原样导出：这里必须豁免，否则用户连自己的备份都恢复不了。
                it != "schedule_folder_map" &&
                !it.startsWith("schedule_time_config_")
        }
        .forEach { key ->
            require(scheduleNames.any { key.startsWith("schedule_${it}_") }) {
                "Invalid full schedule backup: setting belongs to an unknown schedule"
            }
        }
    // ⚠ 「绑定到已删除课表的时间配置」不是损坏，是正常残留：删课表清的是 `schedule_{名}_*`，
    // 绑定键 `schedule_time_config_{名}` 没被一起清掉。旧校验会因此拒绝恢复用户的备份，
    // 故这里只做「格式合理」检查（非空、不含分隔符歧义），不要求名字已知。
    data.keys.asSequence()
        .filter { it.startsWith("schedule_time_config_") }
        .map { it.removePrefix("schedule_time_config_") }
        .forEach { scheduleName ->
            require(scheduleName.isNotBlank()) {
                "Invalid full schedule backup: malformed time configuration binding"
            }
        }
    return scheduleNames
}

fun validateSingleScheduleCourseData(
    courses: List<Map<String, Any>>,
    maxWeeks: Int? = null,
) {
    courses.forEach { course ->
        require((course["name"] as? String)?.isNotBlank() == true) {
            "Invalid single-schedule course name"
        }
        fun requiredInteger(key: String): Int {
            val number = (course[key] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid single-schedule course $key")
            require(number.isFinite() && number % 1.0 == 0.0 &&
                number >= Int.MIN_VALUE.toDouble() && number <= Int.MAX_VALUE.toDouble()
            ) { "Invalid single-schedule course $key" }
            return number.toInt()
        }
        require(requiredInteger("dayOfWeek") in 1..7) {
            "Invalid single-schedule course dayOfWeek"
        }
        val startSection = requiredInteger("startSection")
        val endSection = requiredInteger("endSection")
        require(startSection > 0 && endSection >= startSection) {
            "Invalid single-schedule course section range"
        }
    }
    validateSingleScheduleCourseWeekData(courses, maxWeeks)
}

internal fun validateSingleScheduleCourseWeekData(
    courses: List<Map<String, Any>>,
    maxWeeks: Int? = null,
) {
    require(maxWeeks == null || maxWeeks in 1..CourseRepository.MAX_TOTAL_WEEKS) {
        "Invalid course-week validation horizon"
    }
    courses.forEach { course ->
        if (course.containsKey("selectedWeeks")) {
            val rawWeeks = course["selectedWeeks"] as? List<*>
                ?: throw IllegalArgumentException("Invalid single-schedule selected weeks")
            val weeks = rawWeeks.map { value ->
                val number = (value as? Number)?.toDouble()
                    ?: throw IllegalArgumentException("Invalid single-schedule selected week")
                require(number.isFinite() && number % 1.0 == 0.0 &&
                    number in 1.0..Int.MAX_VALUE.toDouble()
                ) { "Invalid single-schedule selected week" }
                number.toInt()
            }
            require(weeks.distinct().size == weeks.size) {
                "Duplicate single-schedule selected week"
            }
            require(maxWeeks == null || weeks.all { it <= maxWeeks }) {
                "Selected course week exceeds the supported schedule horizon"
            }
        }

        fun exactWeekField(key: String): Int? {
            if (!course.containsKey(key)) return null
            val number = (course[key] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid single-schedule $key")
            require(number.isFinite() && number % 1.0 == 0.0 &&
                number >= Int.MIN_VALUE.toDouble() && number <= Int.MAX_VALUE.toDouble()
            ) { "Invalid single-schedule $key" }
            return number.toInt()
        }

        val startWeek = exactWeekField("startWeek")
        val endWeek = exactWeekField("endWeek")
        val weekType = exactWeekField("weekType")
        require(startWeek == null || startWeek > 0) { "Invalid single-schedule startWeek" }
        require(endWeek == null || endWeek > 0) { "Invalid single-schedule endWeek" }
        require(maxWeeks == null || startWeek == null || startWeek <= maxWeeks) {
            "Course startWeek exceeds the supported schedule horizon"
        }
        require(maxWeeks == null || endWeek == null || endWeek <= maxWeeks) {
            "Course endWeek exceeds the supported schedule horizon"
        }
        require(startWeek == null || endWeek == null || endWeek >= startWeek) {
            "Invalid single-schedule week range"
        }
        require(weekType == null || weekType in 0..2) { "Invalid single-schedule weekType" }
    }
}
