/** WebDAV 备份/恢复管理器 */
package com.haooz.chedule.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class WebDavManager(private val context: Context) {

    private val configPrefs = context.getSharedPreferences("webdav_config", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "WebDavManager"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val BACKUP_DIR = "HyperSchedule"
        private const val BACKUPS_DIR = "backups"
        private const val BACKUP_FILE_PREFIX = "backup_"
        private const val BACKUP_FILE_SUFFIX = ".json"
    }

    var serverUrl: String
        get() = configPrefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = configPrefs.edit { putString(KEY_SERVER_URL, value.trimEnd('/')) }

    var username: String
        get() = configPrefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = configPrefs.edit { putString(KEY_USERNAME, value) }

    var password: String
        get() = configPrefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = configPrefs.edit { putString(KEY_PASSWORD, value) }

    var lastSyncTime: Long
        get() = configPrefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = configPrefs.edit { putLong(KEY_LAST_SYNC_TIME, value) }

    fun isConfigured(): Boolean = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    private fun authHeader(): String = Credentials.basic(username, password)
    private fun baseUrl() = "$serverUrl/$BACKUP_DIR"
    private fun backupsDirUrl() = "${baseUrl()}/$BACKUPS_DIR"
    private fun backupFileUrl(backupId: String) = "${backupsDirUrl()}/$backupId$BACKUP_FILE_SUFFIX"

    // ============ WebDAV 基础操作 ============

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(serverUrl)
                .header("Authorization", authHeader())
                .method("PROPFIND", "".toRequestBody(null))
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.success("连接成功")
                    response.code == 401 -> Result.failure(Exception("认证失败，请检查用户名和密码"))
                    response.code == 404 -> Result.success("连接成功（目录将自动创建）")
                    else -> Result.failure(Exception("服务器返回: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接失败: ${e.message}"))
        }
    }

    private fun ensureDir(dirUrl: String, retries: Int = 2): Result<Boolean> {
        val checkRequest = Request.Builder()
            .url(dirUrl)
            .header("Authorization", authHeader())
            .method("PROPFIND", "".toRequestBody(null))
            .header("Depth", "0")
            .build()
        client.newCall(checkRequest).execute().use { checkResponse ->
            if (checkResponse.isSuccessful) return Result.success(true)
        }

        var lastError: String? = null
        for (attempt in 0..retries) {
            if (attempt > 0) {
                Thread.sleep(1000L * attempt)
            }
            val mkcolRequest = Request.Builder()
                .url(dirUrl)
                .header("Authorization", authHeader())
                .method("MKCOL", "".toRequestBody(null))
                .build()
            client.newCall(mkcolRequest).execute().use { mkcolResponse ->
                if (mkcolResponse.isSuccessful || mkcolResponse.code == 405) {
                    return Result.success(true)
                }
                lastError = "${mkcolResponse.code}"
            }
        }
        return Result.failure(Exception("创建目录失败: $lastError"))
    }

    private fun ensureBackupDirs(): Result<Boolean> {
        val baseResult = ensureDir(baseUrl())
        if (baseResult.isFailure) return baseResult

        val backupsResult = ensureDir(backupsDirUrl())
        if (backupsResult.isFailure) return backupsResult

        return Result.success(true)
    }

    // ============ 备份操作 ============

    suspend fun backupAllData(repository: CourseRepository): BackupResult = withContext(Dispatchers.IO) {
        try {
            val dirResult = ensureBackupDirs()
            if (dirResult.isFailure) {
                return@withContext BackupResult.Error("创建目录失败: ${dirResult.exceptionOrNull()?.message}")
            }

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val now = Date()
            val backupId = "${BACKUP_FILE_PREFIX}${dateFormat.format(now)}"
            val backupTime = displayFormat.format(now)

            val allData = repository.exportAllPreferences()
            val backupData = mapOf(
                "version" to 1,
                "backupTime" to backupTime,
                "backupId" to backupId,
                "data" to allData
            )

            val json = gson.toJson(backupData)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(backupFileUrl(backupId))
                .header("Authorization", authHeader())
                .put(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    lastSyncTime = System.currentTimeMillis()
                    Log.d(TAG, "Backup succeeded: $backupId")
                    BackupResult.Success(backupId)
                } else {
                    BackupResult.Error("上传备份失败: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backup exception", e)
            BackupResult.Error("备份异常: ${e.message}")
        }
    }

    // ============ 恢复操作 ============

    suspend fun restoreLatestBackup(repository: CourseRepository): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val backups = listBackups()
            if (backups.isEmpty()) {
                return@withContext RestoreResult.Error("云端没有备份文件")
            }

            val latest = backups.maxByOrNull { it.backupId }
                ?: return@withContext RestoreResult.Error("无法获取最新备份")

            val request = Request.Builder()
                .url(backupFileUrl(latest.backupId))
                .header("Authorization", authHeader())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body?.string() ?: "{}"
                        val type = object : TypeToken<Map<String, Any>>() {}.type
                        val backupData: Map<String, Any> = gson.fromJson(body, type) ?: emptyMap()

                        @Suppress("UNCHECKED_CAST")
                        val data = backupData["data"] as? Map<String, Any> ?: emptyMap()

                        if (data.isEmpty()) {
                            return@withContext RestoreResult.Error("备份数据为空")
                        }

                        repository.importAllPreferences(data)
                        lastSyncTime = System.currentTimeMillis()
                        Log.d(TAG, "Restore succeeded from ${latest.backupTime}")
                        RestoreResult.Success(latest.backupTime)
                    }
                    response.code == 404 -> RestoreResult.Error("备份文件不存在")
                    else -> RestoreResult.Error("下载备份失败: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restore exception", e)
            RestoreResult.Error("恢复异常: ${e.message}")
        }
    }

    // ============ 备份列表 ============

    suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(backupsDirUrl())
                .header("Authorization", authHeader())
                .method("PROPFIND", "".toRequestBody(null))
                .header("Depth", "1")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string() ?: return@withContext emptyList()
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "List backups exception", e)
            emptyList()
        }
    }

    suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(backupFileUrl(backupId))
                .header("Authorization", authHeader())
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 404) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("删除备份失败: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("删除备份异常: ${e.message}"))
        }
    }
}

data class BackupInfo(
    val backupId: String,
    val fileName: String
) {
    val backupTime: String
        get() {
            return try {
                val id = backupId.removePrefix("backup_")
                val inputFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = inputFormat.parse(id)
                if (date != null) outputFormat.format(date) else id
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
