package com.haooz.chedule.data.school

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 脚本仓库管理 - 使用 HTTP 按需获取教务适配脚本
 * 复用 shiguang_warehouse 仓库结构，直链拉取静态文件：
 *   索引: {repoUrl}/raw/{INDEX_BRANCH}/school_index.pb
 *   脚本: {repoUrl}/raw/main/resources/{resourceFolder}/{assetJsPath}
 */
class ScriptRepository(private val context: Context, private val repoUrl: String? = null) {

    companion object {
        private const val DEFAULT_REPO_URL = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
        private const val RESOURCES_BRANCH = "main"
        private const val INDEX_BRANCH = "index-pb-release"
        private const val INDEX_FILE_NAME = "school_index.pb"

        // 客户端支持的协议版本
        private const val CLIENT_PROTOCOL_VERSION = 2

        private const val TIMEOUT_SECONDS = 30L

        fun getRepoUrl(context: Context): String {
            val prefs = context.getSharedPreferences("edu_import_prefs", Context.MODE_PRIVATE)
            return prefs.getString("repo_url", DEFAULT_REPO_URL) ?: DEFAULT_REPO_URL
        }

        fun setRepoUrl(context: Context, url: String) {
            val prefs = context.getSharedPreferences("edu_import_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("repo_url", url).apply()
        }
    }

    private val baseDir: File
        get() = File(context.filesDir, "repo")

    private val indexDir: File
        get() = File(baseDir, "index")

    private val indexFile: File
        get() = File(indexDir, INDEX_FILE_NAME)

    private val resourcesDir: File
        get() = File(baseDir, "schools/resources")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val remoteBase: String
        get() = repoUrl ?: DEFAULT_REPO_URL

    /**
     * 比较版本ID（TIME_YYYYMMDDHHMMSS_XXX 格式）
     * 返回 true 如果 newVersion 比 localVersion 新
     */
    private fun isNewerVersion(newVersion: String?, localVersion: String?): Boolean {
        if (newVersion.isNullOrBlank()) return false
        if (localVersion.isNullOrBlank()) return true
        return newVersion > localVersion
    }

    private fun readIndex(file: File): SchoolIndexData? {
        if (!file.exists()) return null
        return try {
            SchoolIndexParser.parse(file.readBytes())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 一键更新：HTTP 拉取远端索引，校验并写入本地
     * onProgress: 0.0~0.5=下载索引, 0.5~1.0=校验写入
     * 返回 0=已是最新, 1=更新完成, -1=失败
     */
    fun updateAll(onLog: (String) -> Unit, onProgress: (Float) -> Unit = {}): Int {
        onLog("=== 开始检查更新 ===")
        onProgress(0f)

        val url = "$remoteBase/raw/$INDEX_BRANCH/$INDEX_FILE_NAME"
        onLog("下载索引...")
        val downloaded = try {
            downloadBytes(url, onLog)
        } catch (e: IOException) {
            onLog("错误：索引下载失败 - ${e.message}")
            onProgress(1f)
            return -1
        }
        if (downloaded == null) {
            onLog("警告：远程索引文件不存在")
            onProgress(1f)
            return -1
        }
        onProgress(0.5f)

        val remoteIndex = try {
            SchoolIndexParser.parse(downloaded)
        } catch (e: Exception) {
            onLog("错误：无法解析远程索引，文件可能损坏")
            onProgress(1f)
            return -1
        }

        // A. 校验协议版本
        if (remoteIndex.protocolVersion > CLIENT_PROTOCOL_VERSION) {
            onLog("致命错误：远程协议版本 (${remoteIndex.protocolVersion}) 高于客户端支持版本 ($CLIENT_PROTOCOL_VERSION)")
            onLog("操作：更新中止，请更新应用版本")
            onProgress(1f)
            return -1
        }
        onLog("协议版本校验通过：${remoteIndex.protocolVersion} <= $CLIENT_PROTOCOL_VERSION")

        // B. 校验数据版本
        val localIndex = readIndex(indexFile)
        val localVersionId = localIndex?.versionId
        onLog("远程版本: ${remoteIndex.versionId}")
        onLog("本地版本: ${localVersionId ?: "N/A"}")

        return when {
            isNewerVersion(remoteIndex.versionId, localVersionId) -> {
                onLog("远程版本更新，将写入新索引")
                indexDir.mkdirs()
                indexFile.writeBytes(downloaded)
                onProgress(1f)
                onLog("\n=== 更新完成 ===")
                1
            }
            remoteIndex.versionId == localVersionId -> {
                onLog("\n=== 数据已是最新，无需更新 ===")
                onProgress(1f)
                0
            }
            else -> {
                onLog("致命错误：远程索引更旧，数据一致性异常")
                onProgress(1f)
                -1
            }
        }
    }

    /**
     * 按需获取适配脚本，随索引版本刷新
     * 已缓存且对应的索引版本未变则直接返回；否则重新下载后落盘缓存
     * 返回 null 表示获取失败
     */
    suspend fun ensureScript(resourceFolder: String, assetJsPath: String): File? {
        val target = File(resourcesDir, "$resourceFolder/$assetJsPath")
        val currentVersion = readIndex(indexFile)?.versionId
        val marker = File(target.path + ".v")
        val isCached = target.exists() && marker.exists() && marker.readText() == currentVersion
        if (isCached) return target
        return withContext(Dispatchers.IO) {
            try {
                val url = "$remoteBase/raw/$RESOURCES_BRANCH/resources/$resourceFolder/$assetJsPath"
                val bytes = downloadBytes(url) ?: return@withContext null
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
                marker.writeText(currentVersion ?: "")
                target
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun downloadBytes(url: String, onLog: ((String) -> Unit)? = null): ByteArray? {
        onLog?.invoke("正在下载: $url")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            return if (bytes.isEmpty()) null else bytes
        }
    }
}