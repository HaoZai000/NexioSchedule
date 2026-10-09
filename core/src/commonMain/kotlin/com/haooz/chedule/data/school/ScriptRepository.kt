package com.haooz.chedule.data.school

import com.haooz.chedule.data.AppFile
import com.haooz.chedule.data.AppFiles
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.HttpService
import com.haooz.chedule.data.HttpTimeouts
import com.haooz.chedule.data.createHttpService
import com.haooz.chedule.data.ioDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

/**
 * 脚本仓库管理 - 使用 HTTP 按需获取教务适配脚本
 * 复用 shiguang_warehouse 仓库结构，直链拉取静态文件：
 *   索引: {repoUrl}/raw/{INDEX_BRANCH}/school_index.pb
 *   脚本: {repoUrl}/raw/main/resources/{resourceFolder}/{assetJsPath}
 *
 * ## 迁移说明（行为保持）
 *
 * 原先依赖 `Context`（`filesDir` + `getSharedPreferences`）、`java.io.File`、OkHttp。
 * 现分别改用 [AppFiles] / [AppStorage] / [HttpService]，因此不再持有 Context。
 *
 * 逐项对齐的语义：
 * - 偏好文件名 `edu_import_prefs` 与键 `repo_url` **逐字未变**（改了等于用户的仓库地址丢失）
 * - `edit { }` 提交即写盘，等价原来的 `prefs.edit().putString(...).apply()`
 * - 索引/脚本落盘路径逐字未变（`repo/index/…`、`repo/schools/resources/…`）
 * - 防盗链判定 / 签名链接提取 / 版本比较 / `remoteBase` 的 `.git` 后缀处理 **逐字移植**，
 *   未做任何"顺手优化"
 * - **保留了 8MB 上限**：原来用 `byteStream()` 边读边计数、超限即中止，
 *   现在通过 `HttpService.get(maxBytes = …)` 实现同样的「读取过程中就停手」，
 *   超出时 `truncated = true`，这里按原来的语义视为下载失败
 * - 异常捕获由 `IOException` 放宽为 `Exception`：commonMain 没有 `java.io.IOException`。
 *   ⚠ **这不完全等价**（独立核对确认），有两处可观测差异，方向都偏「修复」：
 *   1. `ensureScript`：非 `IOException`（如 URL 非法的 `IllegalArgumentException`）以前会
 *      逃逸到调用方 —— 而它的三个调用点都没有 try/catch（`WebViewScreen` 的 LaunchedEffect
 *      与 `scope.launch`、`ScheduleImport`），也就是会崩；现在变成「下载失败」+ 缓存回退。
 *   2. `updateAll`：以前异常抛给调用方，`EducationalImportActivity` 的 catch 吃掉后
 *      **不写** `last_update_time`（下次启动还会重试）；现在返回 -1，调用方照写时间戳，
 *      自动更新会被 7 天间隔抑制。注意旧版对「返回 -1」（404 / 网络异常）本来就写时间戳，
 *      这次只是把「URL 非法」也并进同一行为。
 *   3. `CancellationException` 已显式透传，避免吞掉协程取消、破坏结构化并发。
 * - 顺手修掉：原来本文件自建 `OkHttpClient`（第 11 套连接池），现在复用 [HttpService]
 */
class ScriptRepository(
    private val repoUrl: String? = null,
    /**
     * 可注入，供测试替换 —— 防盗链跟随、版本比较、协议版本校验这些分支
     * 全靠它才能离线覆盖（与 [com.haooz.chedule.data.NoticeFetcher] 等同一手法）。
     * 默认按迁移前的超时（connect / read 各 30s）创建。
     */
    private val http: HttpService = createHttpService(
        HttpTimeouts(connectSeconds = TIMEOUT_SECONDS, readSeconds = TIMEOUT_SECONDS)
    ),
) {

    companion object {
        // 默认使用上游 Gitee 拾光仓库
        private const val DEFAULT_REPO_URL = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
        private const val RESOURCES_BRANCH = "main"
        private const val INDEX_BRANCH = "index-pb-release"
        private const val INDEX_FILE_NAME = "school_index.pb"

        // 客户端支持的协议版本
        private const val CLIENT_PROTOCOL_VERSION = 2

        // ⚠ 与迁移前逐字一致，别改
        private const val PREFS_NAME = "edu_import_prefs"
        private const val KEY_REPO_URL = "repo_url"

        // 索引/脚本整包进内存前的硬上限，防止异常大响应直接 OOM
        private const val MAX_DOWNLOAD_BYTES = 8L * 1024 * 1024

        private const val TIMEOUT_SECONDS = 30L

        fun getRepoUrl(): String =
            AppStorage.store(PREFS_NAME).getString(KEY_REPO_URL, DEFAULT_REPO_URL)

        fun setRepoUrl(url: String) {
            AppStorage.store(PREFS_NAME).edit { putString(KEY_REPO_URL, url) }
        }
    }

    private val remoteBase: String
        get() = (repoUrl ?: DEFAULT_REPO_URL).removeSuffix(".git")

    private val baseDir: AppFile
        get() = AppFiles.root.resolve("repo")

    private val indexDir: AppFile
        get() = baseDir.resolve("index")

    private val indexFile: AppFile
        get() = indexDir.resolve(INDEX_FILE_NAME)

    private val resourcesDir: AppFile
        get() = baseDir.resolve("schools/resources")

    /**
     * gitee raw 防盗链检测：直链请求返回 HTML 签名页（含 raw.giteeusercontent.com 链接）
     * 而非文件内容，需解析并跟随签名链接获取真实数据
     */
    private fun isGiteeAntiHotlinkPage(bytes: ByteArray): Boolean {
        if (bytes.size > 1024 || bytes.isEmpty()) return false
        val head = bytes.decodeToString().trimStart()
        return head.startsWith("<") && head.contains("raw.giteeusercontent.com")
    }

    private fun extractGiteeSignedUrl(bytes: ByteArray): String? {
        val html = bytes.decodeToString()
        val match = Regex("href=\"([^\"]+)\"").find(html) ?: return null
        return match.groupValues[1].replace("&amp;", "&")
    }

    /** 缓存文件是否为 gitee 防盗链 HTML（旧版本下载失败时可能残留），视为无效缓存 */
    private fun looksLikeAntiHotlinkHtml(file: AppFile): Boolean {
        return try {
            val bytes = file.readBytes()
            bytes.isNotEmpty() && isGiteeAntiHotlinkPage(bytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 比较版本ID（TIME_YYYYMMDDHHMMSS_XXX 格式）
     * 返回 true 如果 newVersion 比 localVersion 新
     */
    private fun isNewerVersion(newVersion: String?, localVersion: String?): Boolean {
        if (newVersion.isNullOrBlank()) return false
        if (localVersion.isNullOrBlank()) return true
        return newVersion > localVersion
    }

    private fun readIndex(file: AppFile): SchoolIndexData? {
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
     *
     * 迁移前这里是阻塞函数（OkHttp 同步调用）。跨平台后 HTTP 走
     * `suspend` 的 [HttpService]，所以本函数也变成 `suspend`。
     * 现有两个调用点本来就在 `updateScope.launch { }` 里，**无需改动**。
     */
    suspend fun updateAll(onLog: (String) -> Unit, onProgress: (Float) -> Unit = {}): Int {
        onLog("=== 开始检查更新 ===")
        onProgress(0f)

        val url = "$remoteBase/raw/$INDEX_BRANCH/$INDEX_FILE_NAME"
        onLog("下载索引...")
        val downloaded = try {
            downloadBytes(url, onLog)
        } catch (e: CancellationException) {
            // 协程取消必须透传：吞掉它会破坏结构化并发
            throw e
        } catch (e: Exception) {
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
     * 按需获取适配脚本：每次进入都从云端全量重新下载，保证始终是远端最新内容。
     * 只在网络失败时回退本地缓存（且缓存未被防盗链 HTML 污染），不阻塞导入。
     * 返回 null 表示既无缓存、下载也失败。
     */
    suspend fun ensureScript(resourceFolder: String, assetJsPath: String): AppFile? {
        val target = resourcesDir.resolve("$resourceFolder/$assetJsPath")
        return withContext(ioDispatcher) {
            val url = "$remoteBase/raw/$RESOURCES_BRANCH/resources/$resourceFolder/$assetJsPath"
            val bytes = try {
                downloadBytes(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            when {
                bytes != null -> {
                    target.writeBytes(bytes)
                    target
                }
                // 网络失败：有可用缓存则回退，绝不让脚本缺失阻塞导入
                target.exists() && !looksLikeAntiHotlinkHtml(target) -> target
                else -> null
            }
        }
    }

    /**
     * 下载并返回字节；失败、非 2xx 或超出 [MAX_DOWNLOAD_BYTES] 均返回 null。
     *
     * 上限通过 `maxBytes` 下推到 HTTP 层，**在读取过程中就中止**，
     * 与原 `readLimitedBytes`（边读边计数、超限即返回 null）的意图一致。
     */
    private suspend fun downloadBytes(url: String, onLog: ((String) -> Unit)? = null): ByteArray? {
        onLog?.invoke("正在下载: $url")
        var currentUrl = url
        repeat(2) { attempt ->
            val response = http.get(currentUrl, maxBytes = MAX_DOWNLOAD_BYTES)
            if (!response.isSuccessful) return null
            if (response.truncated) return null
            val bytes = response.bytes
            if (bytes.isEmpty()) return null
            // gitee 防盗链：首次请求拿到签名页时，跟随签名链接重试
            if (attempt == 0 && isGiteeAntiHotlinkPage(bytes)) {
                val signedUrl = extractGiteeSignedUrl(bytes) ?: return null
                onLog?.invoke("检测到 gitee 防盗链，跟随签名链接")
                currentUrl = signedUrl
                return@repeat
            }
            return bytes
        }
        return null
    }
}
