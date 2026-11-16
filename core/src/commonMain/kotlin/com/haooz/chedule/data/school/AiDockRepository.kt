package com.haooz.chedule.data.school

import com.haooz.chedule.data.platform.AppFile
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * edu-ai-dock：AI 教务导入脚本仓库。
 *
 * 仓库与拾光同构：`index/root_index.yaml` + `resources/{schoolId}/adapters.yaml` + 同级脚本，
 * CI 把它们编成 `index/school_index.json`。客户端只读这份编好的索引，不再逐校请求：
 *   索引: {base}/index/school_index.json
 *   脚本: {base}/resources/{schoolId}/{file}.js（索引里的 asset_js_path 已带全路径）
 *
 * GitHub 是主仓，Gitee 是同构镜像；优先走哪个跟随「教务数据仓库源」设置（与拾光仓库同平台），
 * 另一个只作兜底。第一个成功的远端会记住，后续脚本下载优先复用（Gitee raw 同样有防盗链签名页）。
 */
class AiDockRepository(
    /** null = 跟随「教务数据仓库源」设置；测试可显式传一组远端 */
    private val remotes: List<String>? = null,
    private val http: HttpService = createHttpService(
        HttpTimeouts(connectSeconds = TIMEOUT_SECONDS, readSeconds = TIMEOUT_SECONDS)
    ),
) {

    companion object {
        private const val TAG = "AiDockRepository"

        const val GITHUB_BASE = "https://raw.githubusercontent.com/HaoZai000/edu-ai-dock/main"
        const val GITEE_BASE = "https://gitee.com/com_haooz_account/edu-ai-dock/raw/main"

        private const val INDEX_PATH = "index/school_index.json"
        private const val INDEX_VERSION = 1

        private const val MAX_INDEX_BYTES = 4L * 1024 * 1024
        private const val MAX_SCRIPT_BYTES = 1L * 1024 * 1024
        private const val TIMEOUT_SECONDS = 20L

        /** 索引缓存路径：与拾光索引分开存，避免互相覆盖 */
        fun indexFile(): AppFile = AppFiles.root.resolve("repo/aidock/school_index.json")

        /** Release 打包进 assets 的索引副本；首次启动本地无缓存时用它引导 */
        const val BUNDLED_INDEX_ASSET = "eduloader/school_index_ai.json"

        /** 脚本缓存路径：索引里的 `resources/...` 相对路径原样落盘 */
        fun scriptFile(assetJsPath: String): AppFile =
            AppFiles.root.resolve("repo/aidock/scripts/$assetJsPath")

        /**
         * 进程内共享实例。
         *
         * 只为保住 [lastGoodBase]：GitHub raw 在部分网络下不可达，每次新建实例会让
         * 脚本下载反复先等一次超时再回退镜像。索引读取（[SchoolRepository]）与
         * 脚本下载（[ScriptRepository]）都用这一个。
         */
        val shared: AiDockRepository by lazy { AiDockRepository() }
    }

    /** 最近一次下载成功的远端，脚本优先走它 */
    private var lastGoodBase: String? = null

    /**
     * 远端顺序跟随「教务数据仓库源」：选 GitHub 就优先 GitHub，选 Gitee 就优先 Gitee，
     * 与拾光仓库走同一个平台；另一个平台只作兜底。
     */
    private fun orderedRemotes(): List<String> {
        val preferred = remotes ?: if (ScriptRepository.isGithubSource()) {
            listOf(GITHUB_BASE, GITEE_BASE)
        } else {
            listOf(GITEE_BASE, GITHUB_BASE)
        }
        val good = lastGoodBase ?: return preferred
        return listOf(good) + preferred.filter { it != good }
    }

    /** 读本地缓存的索引（不联网）；不存在或损坏返回 null。 */
    fun loadIndex(): SchoolIndexData? {
        val file = indexFile()
        if (!file.exists()) return null
        return try {
            parseIndex(file.readBytes())
        } catch (e: Exception) {
            NexioLog.e(TAG, "AI 仓库索引解析失败: ${e.message}")
            null
        }
    }

    /**
     * 拉取 CI 编好的索引并落到本地缓存。
     *
     * 不依赖 `version_id`：内容没变就不写盘，CI 那边也不需要人工维护版本号。
     *
     * @return 1 = 有变化并已写入，0 = 已是最新，-1 = 全部远端失败
     */
    suspend fun updateIndex(onLog: (String) -> Unit = {}): Int = withContext(ioDispatcher) {
        val cached = loadIndex()
        for (base in orderedRemotes()) {
            val bytes = download("$base/$INDEX_PATH", MAX_INDEX_BYTES) ?: continue
            val remote = runCatching { parseIndex(bytes) }.getOrNull() ?: continue
            // 协议版本高于客户端：不写缓存，避免把可用的旧索引覆盖成空表
            if (remote.protocolVersion > INDEX_VERSION) continue
            // 空索引且没有 version_id：多半是 CI 还没跑或镜像没同步，换下一个远端；
            // 有 version_id 的空索引是「学校全被删掉」的正常结果，要写盘以清掉旧学校
            if (remote.schools.isEmpty() && remote.versionId.isBlank()) continue
            lastGoodBase = base
            if (cached?.schools == remote.schools) {
                onLog("AI 脚本索引已是最新")
                return@withContext 0
            }
            indexFile().writeBytes(bytes)
            val adapterCount = remote.schools.sumOf { it.adapters.size }
            onLog("AI 脚本索引已更新：${remote.schools.size} 所学校 / $adapterCount 个适配器")
            return@withContext 1
        }
        onLog("AI 脚本索引下载失败")
        -1
    }

    /**
     * 按需下载脚本；网络失败时回退本地缓存（缓存被 Gitee 防盗链 HTML 污染则视为无效）。
     * 返回 null 表示既无缓存、下载也失败。
     */
    suspend fun ensureScript(assetJsPath: String): AppFile? = withContext(ioDispatcher) {
        if (assetJsPath.isBlank()) return@withContext null
        val target = scriptFile(assetJsPath)
        for (base in orderedRemotes()) {
            val bytes = download("$base/$assetJsPath", MAX_SCRIPT_BYTES)
            if (bytes != null) {
                lastGoodBase = base
                target.writeBytes(bytes)
                return@withContext target
            }
        }
        if (target.exists() && !looksLikeAntiHotlinkHtml(target)) target else null
    }

    /** 下载字节；非 2xx、超限或全失败返回 null。Gitee 签名页会跟随一次。 */
    private suspend fun download(url: String, maxBytes: Long): ByteArray? {
        var currentUrl = url
        repeat(2) { attempt ->
            val response = try {
                http.get(currentUrl, maxBytes = maxBytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
            if (!response.isSuccessful || response.truncated) return null
            val bytes = response.bytes
            if (bytes.isEmpty()) return null
            if (attempt == 0 && isGiteeAntiHotlinkPage(bytes)) {
                currentUrl = extractGiteeSignedUrl(bytes) ?: return null
                return@repeat
            }
            return bytes
        }
        return null
    }

    private fun parseIndex(bytes: ByteArray): SchoolIndexData {
        val root = parseJsonObject(bytes.decodeToString())
        val protocolVersion = root.optInt("protocol_version", INDEX_VERSION)
        // 协议版本高于客户端：整份索引视为不可用，避免把未知字段按旧语义解释
        if (protocolVersion > INDEX_VERSION) {
            return SchoolIndexData(protocolVersion = protocolVersion, versionId = root.optString("version_id"))
        }
        val schools = root.optJsonArray("schools").orEmptyArray().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            SchoolData(
                id = id,
                name = name,
                initial = obj.optString("initial").ifBlank { name.take(1).uppercase() },
                adapters = obj.optJsonArray("adapters").orEmptyArray().mapNotNull { it.asAdapter() },
            )
        }
        return SchoolIndexData(
            protocolVersion = protocolVersion,
            versionId = root.optString("version_id"),
            schools = schools,
        )
    }

    private fun JsonElement.asAdapter(): AdapterData? {
        val obj = this as? JsonObject ?: return null
        val adapterId = obj.optString("adapter_id")
        val scriptPath = obj.optString("asset_js_path")
        // 路径来自远端：只接受仓库内的 resources/**/*.js，拦掉 ".." 以免落到缓存目录之外
        if (adapterId.isBlank() || !isSafeScriptPath(scriptPath)) return null
        return AdapterData(
            adapterId = adapterId,
            adapterName = obj.optString("adapter_name").ifBlank { adapterId },
            category = categoryOf(obj),
            assetJsPath = scriptPath,
            importUrl = obj.optString("import_url").takeIf { it.isNotBlank() },
            description = obj.optString("description"),
            maintainer = obj.optString("maintainer"),
            source = AdapterData.SOURCE_AI_DOCK,
        )
    }

    /** 分类沿用拾光的字符串枚举；数字写法也接受。 */
    private fun categoryOf(obj: JsonObject): Int {
        val raw = obj["category"]
        if (raw is JsonPrimitive && raw.isString) {
            return when (raw.content.trim().uppercase()) {
                "GENERAL_TOOL" -> AdapterData.CATEGORY_GENERAL_TOOL
                "BACHELOR_AND_ASSOCIATE" -> AdapterData.CATEGORY_BACHELOR
                "POSTGRADUATE" -> AdapterData.CATEGORY_POSTGRADUATE
                else -> AdapterData.CATEGORY_UNKNOWN
            }
        }
        return obj.optInt("category", AdapterData.CATEGORY_UNKNOWN)
    }
}

private fun JsonArray?.orEmptyArray(): List<JsonElement> = this?.toList().orEmpty()

/** 只接受 `resources/{school-id}/{file}.js` 这类仓库内路径 */
private fun isSafeScriptPath(path: String): Boolean =
    path.startsWith("resources/") &&
        path.endsWith(".js") &&
        !path.contains("..") &&
        !path.contains('\\')
