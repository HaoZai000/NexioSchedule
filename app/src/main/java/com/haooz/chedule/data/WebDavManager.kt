/** WebDAV 数据同步管理器 */
package com.haooz.chedule.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WebDavManager(private val context: Context) {

    private val configPrefs = context.getSharedPreferences("webdav_config", Context.MODE_PRIVATE)
    private val syncPrefs = context.getSharedPreferences("webdav_sync_state", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val BACKUP_DIR = "HyperSchedule"
        private const val MANIFEST_FILE = "manifest.json"
        private const val COURSES_DIR = "courses"
        private const val INDEX_FILE = "schedules_index.json"
    }

    // ============ 配置读写 ============

    var serverUrl: String
        get() = configPrefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = configPrefs.edit().putString(KEY_SERVER_URL, value.trimEnd('/')).apply()

    var username: String
        get() = configPrefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = configPrefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = configPrefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = configPrefs.edit().putString(KEY_PASSWORD, value).apply()

    var lastSyncTime: Long
        get() = configPrefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = configPrefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    var autoSyncEnabled: Boolean
        get() = configPrefs.getBoolean(KEY_AUTO_SYNC, true)
        set(value) = configPrefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()

    fun isConfigured(): Boolean = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    // ============ 本地同步状态缓存 ============

    private fun getLastKnownIndex(): Map<String, Any> {
        val json = syncPrefs.getString("last_known_index", null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveLastKnownIndex(index: Map<String, Any>) {
        syncPrefs.edit().putString("last_known_index", gson.toJson(index)).apply()
    }

    private fun authHeader(): String = Credentials.basic(username, password)
    private fun baseUrl() = "$serverUrl/$BACKUP_DIR"
    private fun scheduleManifestUrl(scheduleId: String) = "${baseUrl()}/$scheduleId.json"
    private fun courseUrl(courseId: String) = "${baseUrl()}/$COURSES_DIR/$courseId.json"
    private fun schedulesIndexUrl() = "${baseUrl()}/$INDEX_FILE"

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

    private suspend fun ensureRemoteDir(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 检查主目录
            val checkResult = checkAndCreateDir(baseUrl())
            if (checkResult.isFailure) return@withContext checkResult

            // 检查 courses 子目录
            val coursesResult = checkAndCreateDir("${baseUrl()}/$COURSES_DIR")
            if (coursesResult.isFailure) return@withContext coursesResult

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(Exception("创建目录失败: ${e.message}"))
        }
    }

    private fun checkAndCreateDir(dirUrl: String): Result<Boolean> {
        val checkRequest = Request.Builder()
            .url(dirUrl)
            .header("Authorization", authHeader())
            .method("PROPFIND", "".toRequestBody(null))
            .header("Depth", "0")
            .build()
        client.newCall(checkRequest).execute().use { checkResponse ->
            if (checkResponse.isSuccessful) return Result.success(true)
        }
        val mkcolRequest = Request.Builder()
            .url(dirUrl)
            .header("Authorization", authHeader())
            .method("MKCOL", "".toRequestBody(null))
            .build()
        client.newCall(mkcolRequest).execute().use { mkcolResponse ->
            return if (mkcolResponse.isSuccessful || mkcolResponse.code == 405) {
                Result.success(true)
            } else {
                Result.failure(Exception("创建目录失败: ${mkcolResponse.code}"))
            }
        }
    }

    // ============ 课表 Manifest 操作 ============

    suspend fun downloadScheduleManifest(scheduleId: String): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(scheduleManifestUrl(scheduleId))
                .header("Authorization", authHeader())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body?.string() ?: "{}"
                        val type = object : TypeToken<Map<String, Any>>() {}.type
                        val manifest: Map<String, Any> = gson.fromJson(body, type) ?: emptyMap()
                        Result.success(manifest)
                    }
                    response.code == 404 -> Result.success(emptyMap())
                    else -> Result.failure(Exception("下载课表 manifest 失败: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("下载课表 manifest 失败: ${e.message}"))
        }
    }

    suspend fun uploadScheduleManifest(scheduleId: String, manifest: Map<String, Any>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureRemoteDir()
            val json = gson.toJson(manifest)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(scheduleManifestUrl(scheduleId))
                .header("Authorization", authHeader())
                .put(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("上传课表 manifest 失败: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("上传课表 manifest 失败: ${e.message}"))
        }
    }

    // ============ 课表索引操作 ============

    /**
     * 下载课表索引
     */
    suspend fun downloadSchedulesIndex(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(schedulesIndexUrl())
                .header("Authorization", authHeader())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body?.string() ?: "{}"
                        val type = object : TypeToken<Map<String, Any>>() {}.type
                        val index: Map<String, Any> = gson.fromJson(body, type) ?: emptyMap()
                        Result.success(index)
                    }
                    response.code == 404 -> Result.success(emptyMap())
                    else -> Result.failure(Exception("下载课表索引失败: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("下载课表索引失败: ${e.message}"))
        }
    }

    /**
     * 上传课表索引
     */
    suspend fun uploadSchedulesIndex(index: Map<String, Any>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureRemoteDir()
            val json = gson.toJson(index)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(schedulesIndexUrl())
                .header("Authorization", authHeader())
                .put(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("上传课表索引失败: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("上传课表索引失败: ${e.message}"))
        }
    }

    /**
     * 从本地课表构建索引数据
     */
    fun buildSchedulesIndex(repository: CourseRepository): Map<String, Any> {
        val scheduleNames = repository.getScheduleNames()
        val schedules = mutableMapOf<String, Any>()
        for (name in scheduleNames) {
            val courses = repository.getCoursesForSchedule(name)
            schedules[name] = mapOf(
                "name" to name,
                "courseCount" to courses.size,
                "lastModified" to (courses.maxOfOrNull { it.lastModified } ?: 0L)
            )
        }
        return mapOf(
            "schedules" to schedules,
            "lastSyncTime" to System.currentTimeMillis()
        )
    }

    /**
     * 检查远程是否有更新（检查索引 + manifest，不下载课程）
     */
    suspend fun hasRemoteChanges(repository: CourseRepository): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false

        try {
            // 1. 检查课表索引 — 是否有远程新增的课表
            val indexResult = downloadSchedulesIndex()
            if (indexResult.isSuccess) {
                val index = indexResult.getOrThrow()
                @Suppress("UNCHECKED_CAST")
                val remoteSchedules = index["schedules"] as? Map<String, Any> ?: emptyMap()
                val localNames = repository.getScheduleNames().toSet()
                // 远程有、本地没有的课表 → 有变化
                if (remoteSchedules.keys.any { it !in localNames }) {
                    return@withContext true
                }
            }

            // 2. 检查已有课表的 manifest — 是否有更新
            val scheduleNames = repository.getScheduleNames()
            for (scheduleId in scheduleNames) {
                val manifestResult = downloadScheduleManifest(scheduleId)
                if (manifestResult.isSuccess) {
                    val manifest = manifestResult.getOrThrow()
                    val remoteSyncTime = (manifest["lastSyncTime"] as? Number)?.toLong() ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val courses = manifest["courses"] as? Map<String, Any> ?: emptyMap()
                    if (courses.isNotEmpty() && remoteSyncTime > lastSyncTime) {
                        return@withContext true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    // ============ 单课程操作 ============

    suspend fun uploadCourse(course: Course): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureRemoteDir()
            val json = gson.toJson(course)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(courseUrl(course.id))
                .header("Authorization", authHeader())
                .put(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("上传课程失败: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("上传课程失败: ${e.message}"))
        }
    }

    suspend fun downloadCourse(courseId: String): Result<Course> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(courseUrl(courseId))
                .header("Authorization", authHeader())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body?.string() ?: ""
                        val course = gson.fromJson(body, Course::class.java)
                        if (course != null) Result.success(course)
                        else Result.failure(Exception("课程数据解析失败"))
                    }
                    response.code == 404 -> Result.failure(Exception("远程课程不存在"))
                    else -> Result.failure(Exception("下载课程失败: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("下载课程失败: ${e.message}"))
        }
    }

    suspend fun deleteRemoteCourse(courseId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(courseUrl(courseId))
                .header("Authorization", authHeader())
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                // 404 也视为成功（已经不存在了）
                if (response.isSuccessful || response.code == 404) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("删除远程课程失败: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("删除远程课程失败: ${e.message}"))
        }
    }

    /**
     * 删除远程课表的 manifest 和所有课程文件
     */
    suspend fun deleteRemoteSchedule(scheduleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 删除 manifest
            val manifestRequest = Request.Builder()
                .url(scheduleManifestUrl(scheduleId))
                .header("Authorization", authHeader())
                .delete()
                .build()
            client.newCall(manifestRequest).execute().use { it.close() }

            // 下载 manifest 获取课程列表，逐个删除课程文件
            val manifestResult = downloadScheduleManifest(scheduleId)
            if (manifestResult.isSuccess) {
                val manifest = manifestResult.getOrThrow()
                @Suppress("UNCHECKED_CAST")
                val courses = manifest["courses"] as? Map<String, Any> ?: emptyMap()
                for (courseId in courses.keys) {
                    deleteRemoteCourse(courseId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("删除远程课表失败: ${e.message}"))
        }
    }

    // ============ 核心合并逻辑 ============

    /**
     * 同步单个课表
     */
    suspend fun syncSchedule(scheduleId: String, repository: CourseRepository): SyncResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext SyncResult.Error("请先配置 WebDAV 服务器")
        }

        try {
            // 1. 确保远程目录存在
            val dirResult = ensureRemoteDir()
            if (dirResult.isFailure) {
                return@withContext SyncResult.Error("创建目录失败: ${dirResult.exceptionOrNull()?.message}")
            }

            // 2. 下载该课表的 manifest
            val remoteManifestResult = downloadScheduleManifest(scheduleId)
            if (remoteManifestResult.isFailure) {
                return@withContext SyncResult.Error("下载课表 manifest 失败: ${remoteManifestResult.exceptionOrNull()?.message}")
            }
            val remoteManifest = remoteManifestResult.getOrThrow()

            // 3. 获取远程课程信息
            @Suppress("UNCHECKED_CAST")
            val remoteCourses = remoteManifest["courses"] as? Map<String, Any> ?: emptyMap()

            // 4. 获取本地该课表的课程
            val localCourses = repository.getCoursesForSchedule(scheduleId)

            var uploaded = 0
            var downloaded = 0

            // 5a. 遍历本地课程 — 找需要上传的
            for (course in localCourses) {
                val remoteInfo = remoteCourses[course.id] as? Map<*, *>
                if (remoteInfo == null) {
                    // 本地有，远程没有 → 上传
                    val uploadResult = uploadCourse(course)
                    if (uploadResult.isSuccess) uploaded++
                } else {
                    val remoteTime = (remoteInfo["lastModified"] as? Number)?.toLong() ?: 0L
                    if (course.lastModified > remoteTime) {
                        // 本地更新 → 上传
                        val uploadResult = uploadCourse(course)
                        if (uploadResult.isSuccess) uploaded++
                    }
                }
            }

            // 5b. 遍历远程课程 — 下载本地没有的课程
            val localIds = localCourses.map { it.id }.toSet()
            val downloadedCourses = mutableListOf<Course>()
            for ((id, info) in remoteCourses) {
                if (id in localIds) continue
                // 远程有，本地没有 → 下载到本地
                val downloadResult = downloadCourse(id)
                if (downloadResult.isSuccess) {
                    downloadedCourses.add(downloadResult.getOrThrow())
                    downloaded++
                }
            }
            // 将下载的课程合并到本地
            if (downloadedCourses.isNotEmpty()) {
                val allCourses = repository.getCoursesForSchedule(scheduleId).toMutableList()
                allCourses.addAll(downloadedCourses)
                repository.saveCoursesForSchedule(scheduleId, allCourses, notify = false)
            }

            // 5c. 同步课表设置（时间、节数、周数等）
            @Suppress("UNCHECKED_CAST")
            val remoteSettings = remoteManifest["settings"] as? Map<String, Any> ?: emptyMap()
            val localSettings = repository.exportScheduleSettings(scheduleId)
            var settingsChanged = false

            if (remoteSettings.isNotEmpty() && localSettings.isEmpty()) {
                // 远程有设置、本地没有 → 导入远程设置
                repository.importScheduleSettings(scheduleId, remoteSettings)
                settingsChanged = true
            } else if (remoteSettings.isNotEmpty() && localSettings.isNotEmpty()) {
                // 双方都有设置 → 比较 lastModified 或直接用远程覆盖（远程为准）
                // 这里简单处理：如果远程设置和本地不同，用远程覆盖
                if (remoteSettings != localSettings) {
                    repository.importScheduleSettings(scheduleId, remoteSettings)
                    settingsChanged = true
                }
            }
            // 如果本地有设置但远程没有，会在步骤 6 上传时带上

            // 6. 构建合并后的 manifest 并上传
            val mergedCourses = repository.getCoursesForSchedule(scheduleId)
            val coursesMap = mutableMapOf<String, Any>()
            for (course in mergedCourses) {
                coursesMap[course.id] = mapOf(
                    "lastModified" to course.lastModified,
                    "deleted" to false
                )
            }
            // 导出当前课表设置
            val currentSettings = repository.exportScheduleSettings(scheduleId)
            val newManifest = mapOf(
                "version" to 5,
                "scheduleId" to scheduleId,
                "lastSyncTime" to System.currentTimeMillis(),
                "courses" to coursesMap,
                "settings" to currentSettings
            )

            val manifestResult = uploadScheduleManifest(scheduleId, newManifest)
            if (manifestResult.isFailure) {
                return@withContext SyncResult.Error("上传课表 manifest 失败: ${manifestResult.exceptionOrNull()?.message}")
            }
            lastSyncTime = System.currentTimeMillis()

            if (uploaded == 0 && downloaded == 0) {
                SyncResult.NoChange("数据已是最新")
            } else {
                SyncResult.Merged(uploaded, downloaded)
            }
        } catch (e: Exception) {
            SyncResult.Error("同步失败: ${e.message}")
        }
    }

    /**
     * 同步所有课表
     */
    suspend fun syncAllSchedules(repository: CourseRepository): SyncResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext SyncResult.Error("请先配置 WebDAV 服务器")
        }

        // 1. 确保远程目录存在
        val dirResult = ensureRemoteDir()
        if (dirResult.isFailure) {
            return@withContext SyncResult.Error("创建目录失败: ${dirResult.exceptionOrNull()?.message}")
        }

        // 2. 同步课表索引：对比远程和本地，决定新增或删除
        val localNames = repository.getScheduleNames().toMutableList()
        val remoteIndexResult = downloadSchedulesIndex()
        var deletedSchedules = 0

        // 读取上次同步时的索引（用于判断哪些课表是本地删除的）
        val lastKnownIndex = getLastKnownIndex()
        @Suppress("UNCHECKED_CAST")
        val lastKnownSchedules = lastKnownIndex["schedules"] as? Map<String, Any> ?: emptyMap()

        if (remoteIndexResult.isSuccess) {
            val remoteIndex = remoteIndexResult.getOrThrow()
            @Suppress("UNCHECKED_CAST")
            val remoteSchedules = remoteIndex["schedules"] as? Map<String, Any> ?: emptyMap()

            // 远程有、上次已知也有、但本地没有 → 本地删除了，删除远程
            for (remoteName in remoteSchedules.keys) {
                if (remoteName in lastKnownSchedules && remoteName !in localNames) {
                    deleteRemoteSchedule(remoteName)
                    deletedSchedules++
                }
            }

            // 远程有、本地没有、上次也没见过 → 新课表，创建本地
            for (remoteName in remoteSchedules.keys) {
                if (remoteName !in localNames && remoteName !in lastKnownSchedules) {
                    repository.addSchedule(remoteName)
                    localNames.add(remoteName)
                }
            }
        }

        // 保存本次索引为"已知"状态
        if (remoteIndexResult.isSuccess) {
            saveLastKnownIndex(remoteIndexResult.getOrThrow())
        }

        // 3. 同步所有本地课表
        val scheduleNames = repository.getScheduleNames()
        var totalUploaded = 0
        var totalDownloaded = 0
        var lastError: String? = null

        for (scheduleId in scheduleNames) {
            val result = syncSchedule(scheduleId, repository)
            when (result) {
                is SyncResult.Merged -> {
                    totalUploaded += result.uploaded
                    totalDownloaded += result.downloaded
                }
                is SyncResult.Error -> {
                    lastError = result.message
                }
                is SyncResult.NoChange -> {
                    // 继续
                }
            }
        }

        // 4. 上传更新后的课表索引
        val newIndex = buildSchedulesIndex(repository)
        uploadSchedulesIndex(newIndex)

        if (lastError != null && totalUploaded == 0 && totalDownloaded == 0 && deletedSchedules == 0) {
            SyncResult.Error(lastError)
        } else if (totalUploaded == 0 && totalDownloaded == 0 && deletedSchedules == 0) {
            SyncResult.NoChange("所有课表已是最新")
        } else {
            SyncResult.Merged(totalUploaded, totalDownloaded)
        }
    }
}

/**
 * 同步结果密封类
 */
sealed class SyncResult {
    data class Merged(val uploaded: Int, val downloaded: Int) : SyncResult()
    data class NoChange(val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
