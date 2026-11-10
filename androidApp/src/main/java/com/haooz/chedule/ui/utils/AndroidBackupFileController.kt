package com.haooz.chedule.ui.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import com.haooz.chedule.data.backup.BACKUP_DIR_NAME
import com.haooz.chedule.data.backup.BACKUP_NAME_REGEX
import com.haooz.chedule.data.backup.BackupFileController
import com.haooz.chedule.data.backup.BackupFileInfo
import com.haooz.chedule.data.backup.MAX_BACKUP_PARSE_BYTES
import com.haooz.chedule.data.backup.countSchedulesInBackupJson
import com.haooz.chedule.data.backup.formatBackupSize
import com.haooz.chedule.data.backup.parseBackupFileBaseName
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.BackupStr
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * [BackupFileController] 的 Android 实现 —— 包装备份目录扫描（`java.io.File` + 公共
 * Download / 应用私有目录）、SAF 系统文件选择器（`ActivityResultContracts.OpenDocument`
 * + `contentResolver`）、备份文件的写 / 读 / 删。
 *
 * 同 [AndroidDiagnosticRecorder] / [AndroidUpdateController] / [AndroidReminderController]
 * 的套路：方法都带 `context`，本层在构造时捕获宿主注入的 context，接口层保持无参
 * （`:ui-shared` 的「本地备份」页只依赖跨平台接口，commonMain 里没有 android / java import）。
 * 宿主（AppRouteContent 的 LocalBackup 路由 / 设置页的 `backupFileController` 注入点）在调用点
 * 创建本实例并注入：`backupFileController = remember { AndroidBackupFileController(context) }`。
 *
 * 本文件集中了原 `LocalBackupScreen.kt`（:androidApp 版）里全部 Android 专有段落，
 * 逻辑逐字搬移：目录选择与降级（公共 Download → 私有 Download）、双目录扫描、
 * 文件名/时间/大小解析、8 MB 有界读取、SAF mime 放开为全匹配通配（任意类型）。
 */
class AndroidBackupFileController(private val context: Context) : BackupFileController {

    /** 宿主 Activity（SAF 结果回调依赖它；LocalContext 在正常宿主里就是 MainActivity） */
    private val activity: Activity? by lazy { context.findActivity() }

    /** 主线程 Handler：SAF 读文件放后台线程，回调（页面要改 Compose 状态）必须回主线程 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** SAF 选文件结果回调槽（发起请求前更新；同 key 重复注册 = 替换回调槽，不冲突） */
    private var pickResult: ((String?, String?) -> Unit)? = null

    /** SAF 选文件结果通道（固定 key：Activity 重建后注册同 key 可接上恢复的挂起结果） */
    private val pickLauncher: ActivityResultLauncher<Array<String>>? by lazy {
        (activity as? ActivityResultRegistryOwner)?.activityResultRegistry?.register(
            KEY_PICK_BACKUP,
            ActivityResultContracts.OpenDocument()
        ) { uri -> dispatchPickResult(uri) }
    }

    // ————————————————————————— 备份列表（目录扫描） —————————————————————————

    override fun listBackups(): List<BackupFileInfo> = scanBackupFiles(context)

    // ————————————————————————— SAF 选文件导入 —————————————————————————

    override fun pickAndReadBackup(onResult: (String?, String?) -> Unit) {
        // 先取 launcher（触发注册：恢复态下的陈旧挂起结果在此吞掉，不串到本次回调），再更新槽
        val launcher = pickLauncher
        pickResult = onResult
        if (launcher == null) {
            // 无 Activity 宿主：拿不到系统选择器，按「用户取消」回调，页面静默
            onResult(null, null)
            return
        }
        // 不限定 mime：各厂商文件管理器对 .json 上报的 mime 不一致
        // （text/plain / application/octet-stream 都有），
        // 限定 application/json 会让用户根本选不到文件。
        // 合法性由 parseBackupPayload 把关，放开 mime 是安全的。
        launcher.launch(arrayOf("*/*"))
    }

    private fun dispatchPickResult(uri: Uri?) {
        val callback = pickResult ?: return
        if (uri == null) {
            // 用户取消选择
            callback(null, null)
            return
        }
        // 与原页面一致：SAF 读文件 + 8 MB 上限校验放 IO 线程，结果回主线程回调
        Thread {
            val readResult = runCatching { readExternalBackupJson(context, uri) }
            val displayName = queryDisplayName(uri)
            mainHandler.post {
                readResult.fold(
                    onSuccess = { json -> callback(json, displayName) },
                    onFailure = { e ->
                        // 逐字保留原页面 beginRestore 失败分支的 toast 文案
                        showToast(BackupStr.unrecognizedBackupN(e.message ?: BackupStr.formatError()))
                        callback(null, null)
                    }
                )
            }
        }.start()
    }

    /** SAF Uri → 展示名（尽力而为；查询失败返回 null） */
    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    } catch (_: Exception) {
        null
    }

    // ————————————————————————— 写 / 读 / 删备份文件 —————————————————————————

    override fun saveBackupFile(fileName: String, json: String): Boolean {
        val dir = getBackupDir(context)
        if (!isUsableDir(dir)) {
            // 逐字保留原页面的失败信息：页面 catch 后 toast「备份失败: <原因>」
            throw IllegalStateException(AndroidMiscStr.backupDirNotWritableN(dir.absolutePath))
        }
        File(dir, fileName).writeText(json, Charsets.UTF_8)
        return true
    }

    override fun readBackupFile(id: String): String? {
        val file = File(id)
        if (!file.exists()) return null
        return readBoundedUtf8(FileInputStream(file))
    }

    override fun deleteBackupFile(id: String): Boolean = File(id).delete()

    // ─────────────────────────── 原页面 Android 私有段落（逐字搬移） ───────────────────────────

    /**
     * 备份目录。优先公共 Download（用户能在文件管理器里看到、方便拷走）；
     * 分区存储下该目录可能不可写（取决于系统策略与授权状态），此时降级到应用私有
     * Download 目录，避免整个备份功能失效。
     */
    private fun getBackupDir(context: Context): File {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            BACKUP_DIR_NAME
        )
        if (isUsableDir(publicDir)) return publicDir
        val privateDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            BACKUP_DIR_NAME
        )
        privateDir.mkdirs()
        return privateDir
    }

    private fun isUsableDir(dir: File): Boolean = try {
        (dir.exists() || dir.mkdirs()) && dir.canWrite()
    } catch (_: Exception) {
        false
    }

    /**
     * 扫描用的候选目录（公共 + 私有）。两个都扫，这样在两种存储环境之间切换时
     * 历史备份不会凭空"消失"。
     */
    private fun candidateBackupDirs(context: Context): List<File> {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            BACKUP_DIR_NAME
        )
        val privateDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            BACKUP_DIR_NAME
        )
        return listOf(publicDir, privateDir).filter { it.isDirectory }.distinctBy { it.absolutePath }
    }

    private fun countSchedulesInBackup(file: File): Int {
        return try {
            if (file.length() > MAX_BACKUP_PARSE_BYTES) return 0
            val json = file.readText(Charsets.UTF_8)
            countSchedulesInBackupJson(json)
        } catch (_: Exception) {
            0
        }
    }

    private fun scanBackupFiles(context: Context): List<BackupFileInfo> {
        val displaySdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val parseSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

        return candidateBackupDirs(context)
            .flatMap { dir -> dir.listFiles()?.asList() ?: emptyList() }
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .map { file ->
                val name = file.nameWithoutExtension
                // 非全量备份：正则第 1 组为课表名，匹配不上则整个名字当课表名（与原实现一致）
                val (scheduleName, isFullBackup) = parseBackupFileBaseName(name)
                val type = if (isFullBackup) BackupStr.fullBackup() else BackupStr.singleSchedule()

                val match = BACKUP_NAME_REGEX.matchEntire(name)

                val scheduleCount = countSchedulesInBackup(file)

                val timestamp = try {
                    match?.let { parseSdf.parse(it.groupValues[2]) }?.let { displaySdf.format(it) }
                        ?: AndroidMiscStr.unknownTime()
                } catch (_: Exception) {
                    AndroidMiscStr.unknownTime()
                }

                val sizeStr = formatBackupSize(file.length())

                BackupFileInfo(
                    id = file.absolutePath,
                    fileName = file.name,
                    type = type,
                    scheduleName = scheduleName,
                    scheduleCount = scheduleCount,
                    timestamp = timestamp,
                    size = sizeStr,
                    isFullBackup = isFullBackup,
                ) to file.lastModified()
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun readExternalBackupJson(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException(AndroidMiscStr.cannotReadSelectedFile())
        return readBoundedUtf8(inputStream)
    }

    private companion object {
        /** SAF 选文件的固定注册 key（同 key 重复注册 = 替换回调槽，不抛错） */
        const val KEY_PICK_BACKUP = "com.haooz.chedule.backup.pick_document"
    }
}

/**
 * 有界读 UTF-8 文本（8 MB 上限）。
 *
 * 原来是 [AndroidBackupFileController] 的成员方法，2026-10-11 提到文件级：
 * 导入通道（`AndroidScheduleFileController`）也要读用户选的文件，
 * 同一个「超过 8 MB 直接失败」的约束不能一人写一份。
 */
internal fun readBoundedUtf8(
    inputStream: InputStream,
    maxBytes: Long = MAX_BACKUP_PARSE_BYTES,
): String {
    require(maxBytes >= 0L) { "Invalid backup byte limit" }
    return inputStream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var totalBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            totalBytes += count
            require(totalBytes <= maxBytes) { AndroidMiscStr.backupOverSizeLimit() }
            output.write(buffer, 0, count)
        }
        String(output.toByteArray(), Charsets.UTF_8)
    }
}

/**
 * 从任意 Context 向外层找到宿主 Activity（同 AndroidReminderController 的 findActivity 套路）。
 *
 * ⚠ 保持 `private`（文件级）：`AndroidReminderController.kt` 里也有一份同名扩展，
 * 改成 `internal` 会直接撞成 `Conflicting overloads` —— 这个包里的每个控制器
 * 各带一份自己的私有副本，是既定做法，别去「统一」它们。
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
