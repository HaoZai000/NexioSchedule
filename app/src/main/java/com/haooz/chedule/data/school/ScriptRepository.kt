package com.haooz.chedule.data.school

import android.content.Context
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File

/**
 * 脚本仓库管理 - 使用 JGit 增量更新教务适配脚本
 * 复用 shiguang_warehouse 仓库结构
 */
class ScriptRepository(private val context: Context, private val repoUrl: String? = null) {

    companion object {
        private const val DEFAULT_REPO_URL = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
        private const val RESOURCES_BRANCH = "main"
        private const val INDEX_BRANCH = "index-pb-release"
        private const val INDEX_FILE_NAME = "school_index.pb"

        // 客户端支持的协议版本
        private const val CLIENT_PROTOCOL_VERSION = 1

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

    private val schoolsDir: File
        get() = File(baseDir, "schools")

    private val tempResourcesDir: File
        get() = File(context.cacheDir, "temp_schools_repo")

    private val tempIndexDir: File
        get() = File(context.cacheDir, "temp_index_repo")

    // 更新结果
    private data class UpdateResult(
        var indexFileContent: ByteArray? = null,
        var indexRemoteVersionId: String? = null,
        var resourceFiles: List<Pair<File, File>> = emptyList(),
        var isFatalIndexError: Boolean = false
    )

    /**
     * 比较版本ID（TIME_YYYYMMDDHHMMSS_XXX 格式）
     * 返回 true 如果 newVersion 比 localVersion 新
     */
    private fun isNewerVersion(newVersion: String?, localVersion: String?): Boolean {
        if (newVersion.isNullOrBlank()) return false
        if (localVersion.isNullOrBlank()) return true
        return newVersion > localVersion
    }

    /**
     * 读取并解析索引文件
     */
    private fun readIndex(file: File): SchoolIndexData? {
        if (!file.exists()) return null
        return try {
            SchoolIndexParser.parse(file.readBytes())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 【步骤一】更新资源文件：克隆或拉取，暂存文件列表
     */
    private fun updateResourceFiles(onLog: (String) -> Unit, result: UpdateResult, onProgress: (Float) -> Unit = {}): Boolean {
        onLog("\n--- 资源文件更新（第一阶段：拉取） ---")

        try {
            val gitDir = File(tempResourcesDir, ".git")
            val isLocalRepoExist = tempResourcesDir.exists() && gitDir.exists()

            val git: Git = if (isLocalRepoExist) {
                onLog("临时仓库已存在，执行增量更新...")
                val openedGit = Git.open(tempResourcesDir)

                onLog("正在拉取远程变更...")
                openedGit.fetch()
                    .setProgressMonitor(SimpleProgressMonitor(onLog) { onProgress(it * 0.5f) })
                    .setTimeout(60)
                    .call()
                onProgress(0.5f)

                val remoteRef = "refs/remotes/origin/$RESOURCES_BRANCH"
                if (openedGit.repository.findRef(remoteRef) == null) {
                    onLog("错误：不存在分支 '$RESOURCES_BRANCH'")
                    return false
                }

                onLog("正在重置到远程最新...")
                openedGit.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(remoteRef)
                    .call()

                openedGit
            } else {
                if (tempResourcesDir.exists()) tempResourcesDir.deleteRecursively()
                onLog("正在克隆资源仓库...")
                Git.cloneRepository()
                    .setURI(repoUrl ?: DEFAULT_REPO_URL)
                    .setDirectory(tempResourcesDir)
                    .setBranch(RESOURCES_BRANCH)
                    .setProgressMonitor(SimpleProgressMonitor(onLog) { onProgress(it * 0.5f) })
                    .setTimeout(120)
                    .call()
            }

            git.use {
                val sourceResourcesDir = File(tempResourcesDir, "resources")
                if (!sourceResourcesDir.exists() || !sourceResourcesDir.isDirectory) {
                    onLog("错误：仓库中未找到 resources 文件夹")
                    return false
                }

                val filesToCopy = mutableListOf<Pair<File, File>>()
                sourceResourcesDir.walkTopDown().forEach { sourceFile ->
                    if (sourceFile.isFile && !sourceFile.name.equals("adapters.yaml", true)) {
                        val relativePath = sourceFile.relativeTo(sourceResourcesDir)
                        val targetFile = File(schoolsDir, "resources/$relativePath")
                        filesToCopy.add(Pair(sourceFile, targetFile))
                    }
                }

                result.resourceFiles = filesToCopy
                onProgress(1f)
                onLog("已暂存 ${filesToCopy.size} 个脚本文件")
                return true
            }
        } catch (e: Exception) {
            onLog("错误：资源更新失败 - ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * 【步骤二】下载索引文件，校验协议版本和数据版本
     */
    private fun downloadIndexFile(onLog: (String) -> Unit, result: UpdateResult) {
        onLog("\n--- 索引文件下载（第二阶段：拉取与校验） ---")

        try {
            if (tempIndexDir.exists()) tempIndexDir.deleteRecursively()

            onLog("正在克隆索引分支...")
            Git.cloneRepository()
                .setURI(repoUrl ?: DEFAULT_REPO_URL)
                .setDirectory(tempIndexDir)
                .setBranch(INDEX_BRANCH)
                .setTimeout(30)
                .call()
                .close()

            val sourceFile = File(tempIndexDir, INDEX_FILE_NAME)
            if (!sourceFile.exists()) {
                onLog("警告：索引文件不存在，使用本地索引（若有）")
                return
            }

            val remoteIndex = readIndex(sourceFile)
            if (remoteIndex == null) {
                onLog("错误：无法解析远程索引，文件可能损坏")
                return
            }

            // A. 校验协议版本
            val remoteProtocol = remoteIndex.protocolVersion
            if (remoteProtocol > CLIENT_PROTOCOL_VERSION) {
                onLog("致命错误：远程协议版本 ($remoteProtocol) 高于客户端支持版本 ($CLIENT_PROTOCOL_VERSION)")
                onLog("操作：更新中止，请更新应用版本")
                result.isFatalIndexError = true
                return
            }
            onLog("协议版本校验通过：$remoteProtocol <= $CLIENT_PROTOCOL_VERSION")

            // B. 校验数据版本
            val localIndex = readIndex(indexFile)
            val localVersionId = localIndex?.versionId

            onLog("远程版本: ${remoteIndex.versionId}")
            onLog("本地版本: ${localVersionId ?: "N/A"}")

            if (isNewerVersion(remoteIndex.versionId, localVersionId)) {
                onLog("远程版本更新，将写入新索引")
                result.indexFileContent = sourceFile.readBytes()
                result.indexRemoteVersionId = remoteIndex.versionId
            } else if (remoteIndex.versionId == localVersionId) {
                onLog("索引已是最新版本，跳过写入")
            } else {
                onLog("致命错误：远程索引更旧，数据一致性异常")
                result.isFatalIndexError = true
                return
            }

        } catch (e: Exception) {
            val msg = e.message ?: ""
            val isBranchNotFound = msg.contains(INDEX_BRANCH) ||
                    e::class.java.simpleName.contains("RefNotAdvertisedException")

            if (isBranchNotFound) {
                onLog("警告：索引分支不存在，使用本地索引（若有）")
            } else {
                onLog("错误：索引下载失败 - ${e.message}")
            }
        }
    }

    /**
     * 【步骤三】统一写入本地存储
     */
    private fun commitUpdates(result: UpdateResult, onLog: (String) -> Unit): Boolean {
        onLog("\n--- 统一写入本地存储 ---")

        // 备份旧索引
        var localIndexBackup: ByteArray? = null
        if (indexFile.exists()) {
            try {
                localIndexBackup = indexFile.readBytes()
                onLog("本地索引已备份")
            } catch (e: Exception) {
                onLog("警告：备份本地索引失败")
            }
        }

        // 清理整个 repo 目录
        onLog("清理本地仓库目录...")
        if (baseDir.exists()) baseDir.deleteRecursively()
        if (!baseDir.mkdirs()) {
            onLog("致命错误：无法创建目录")
            return false
        }

        // 写入资源文件
        if (result.resourceFiles.isNotEmpty()) {
            onLog("写入 ${result.resourceFiles.size} 个资源文件...")
            try {
                schoolsDir.mkdirs()
                result.resourceFiles.forEach { (source, target) ->
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
                onLog("资源文件写入完成")
            } catch (e: Exception) {
                onLog("错误：写入资源文件失败")
                return false
            }
        }

        // 写入索引文件
        if (result.indexFileContent != null) {
            try {
                indexDir.mkdirs()
                indexFile.writeBytes(result.indexFileContent!!)
                onLog("索引文件已写入 (版本: ${result.indexRemoteVersionId})")
            } catch (e: Exception) {
                onLog("错误：写入索引文件失败")
            }
        } else if (localIndexBackup != null) {
            // 版本未更新，恢复旧索引
            try {
                indexDir.mkdirs()
                indexFile.writeBytes(localIndexBackup)
                onLog("索引版本未更新，已恢复旧索引")
            } catch (e: Exception) {
                onLog("警告：恢复旧索引失败")
            }
        }

        return true
    }

    /**
     * 一键更新：先检查索引版本，再决定是否下载资源
     * onProgress: 0.0~0.3=检查索引, 0.3~1.0=下载资源
     * 返回 true 表示有更新并完成，false 表示已是最新或失败
     */
    fun updateAll(onLog: (String) -> Unit, onProgress: (Float) -> Unit = {}): Boolean {
        onLog("=== 开始检查更新 ===")
        onProgress(0f)

        // 阶段一：先下载索引，检查版本
        val indexResult = UpdateResult()
        downloadIndexFile(onLog, indexResult)
        onProgress(0.3f)

        if (indexResult.isFatalIndexError) {
            onLog("\n!!! 索引校验失败，终止")
            cleanupTempDirs()
            return false
        }

        // 索引版本未变 → 已是最新
        if (indexResult.indexFileContent == null) {
            onLog("\n=== 数据已是最新，无需更新 ===")
            cleanupTempDirs()
            return true
        }

        // 阶段二：索引有更新，下载资源
        onLog("\n索引有更新，开始下载脚本...")
        val resourceResult = UpdateResult(
            indexFileContent = indexResult.indexFileContent,
            indexRemoteVersionId = indexResult.indexRemoteVersionId
        )
        val resourceSuccess = updateResourceFiles(onLog, resourceResult) { progress ->
            onProgress(0.3f + progress * 0.7f)
        }
        if (!resourceSuccess) {
            onLog("\n!!! 资源更新失败，终止")
            cleanupTempDirs()
            return false
        }

        resourceResult.indexFileContent = indexResult.indexFileContent
        resourceResult.indexRemoteVersionId = indexResult.indexRemoteVersionId

        // 阶段三：统一写入
        val commitSuccess = commitUpdates(resourceResult, onLog)
        cleanupTempDirs()

        return if (commitSuccess) {
            onLog("\n=== 更新完成 ===")
            true
        } else {
            onLog("\n!!! 写入失败 ===")
            false
        }
    }

    private fun cleanupTempDirs() {
        listOf(tempResourcesDir, tempIndexDir).forEach { dir ->
            if (dir.exists()) dir.deleteRecursively()
        }
    }

    /**
     * 简单的进度监控器（进度单调递增，不会回退）
     */
    private class SimpleProgressMonitor(
        private val onLog: (String) -> Unit,
        private val onProgress: (Float) -> Unit = {}
    ) : ProgressMonitor {
        private var totalWork = 0
        private var completed = 0
        private var taskCount = 0
        private var finishedTasks = 0
        private var lastProgress = 0f

        override fun start(totalTasks: Int) {
            taskCount = totalTasks.coerceAtLeast(1)
        }
        override fun beginTask(title: String?, totalWork: Int) {
            this.totalWork = totalWork
            this.completed = 0
            title?.let { onLog("  $it") }
        }
        override fun update(completed: Int) {
            this.completed += completed
            if (totalWork > 0) {
                val taskProgress = (this.completed.toFloat() / totalWork).coerceIn(0f, 1f)
                val overall = ((finishedTasks + taskProgress) / taskCount).coerceIn(0f, 1f)
                if (overall > lastProgress) {
                    lastProgress = overall
                    onProgress(overall)
                }
            }
        }
        override fun endTask() {
            finishedTasks++
            val overall = (finishedTasks.toFloat() / taskCount).coerceIn(0f, 1f)
            if (overall > lastProgress) {
                lastProgress = overall
                onProgress(overall)
            }
        }
        override fun isCancelled(): Boolean = false
    }
}
