package com.haooz.chedule.ui.utils

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
 * [BackupFileController] 的 Android 实现。
 *
 * 本机备份默认落在公共 `Download/Neixo_Schedule`：
 * - Android 10+ 通过 MediaStore.Downloads 写入，不需要存储权限，卸载重装后文件仍在；
 * - 用户授权 SAF 目录后改用该目录，可跨重装自动枚举（重新授权一次即可）。
 *
 * 旧版本的公共 / 私有目录仍做只读扫描，保证升级前写下的备份还能列出来恢复。
 */
class AndroidBackupFileController(private val context: Context) : BackupFileController {

    /** 宿主 Activity（SAF 结果回调依赖它；LocalContext 在正常宿主里就是 MainActivity） */
    private val activity: Activity? by lazy { context.findActivity() }

    /** 主线程 Handler：SAF 读文件放后台线程，回调（页面要改 Compose 状态）必须回主线程 */
    private val mainHandler = Handler(Looper.getMainLooper())

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** SAF 选文件结果回调槽（发起请求前更新；同 key 重复注册 = 替换回调槽，不冲突） */
    private var pickResult: ((String?, String?) -> Unit)? = null

    /** SAF 选文件结果通道（固定 key：Activity 重建后注册同 key 可接上恢复的挂起结果） */
    private val pickLauncher: ActivityResultLauncher<Array<String>>? by lazy {
        (activity as? ActivityResultRegistryOwner)?.activityResultRegistry?.register(
            KEY_PICK_BACKUP,
            ActivityResultContracts.OpenDocument()
        ) { uri -> dispatchPickResult(uri) }
    }

    /** SAF 选目录结果回调槽 */
    private var treePickResult: ((Boolean) -> Unit)? = null

    /** SAF 选目录结果通道 */
    private val treeLauncher: ActivityResultLauncher<Uri?>? by lazy {
        (activity as? ActivityResultRegistryOwner)?.activityResultRegistry?.register(
            KEY_PICK_TREE,
            ActivityResultContracts.OpenDocumentTree()
        ) { uri -> dispatchTreePickResult(uri) }
    }

    // ————————————————————————— 备份目录授权 —————————————————————————

    override fun canChooseBackupDirectory(): Boolean = treeLauncher != null

    override fun isBackupDirectoryAuthorized(): Boolean = authorizedTreeUri() != null

    override fun backupDirectoryLabel(): String {
        val tree = authorizedTreeUri()
        if (tree != null) {
            return queryTreeDisplayName(tree) ?: tree.lastPathSegment ?: ""
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publicBackupPath()
        } else {
            legacyWriteDir().absolutePath
        }
    }

    override fun chooseBackupDirectory(onResult: (Boolean) -> Unit) {
        val launcher = treeLauncher
        treePickResult = onResult
        if (launcher == null) {
            onResult(false)
            return
        }
        launcher.launch(initialTreeUri())
    }

    /**
     * 目录选择器的初始位置：优先上次授权的目录；首次使用（含卸载重装后）
     * 默认打开公共 Download，方便用户直接选回原来的备份文件夹。
     */
    private fun initialTreeUri(): Uri? {
        prefs.getString(KEY_TREE_URI, null)?.let { raw ->
            runCatching { Uri.parse(raw) }.getOrNull()?.let { return it }
        }
        return runCatching {
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download",
            )
        }.getOrNull()
    }

    private fun dispatchTreePickResult(uri: Uri?) {
        val callback = treePickResult ?: return
        treePickResult = null
        if (uri == null) {
            callback(false)
            return
        }
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            isTreeWritable(uri)
        }.getOrDefault(false)
        if (granted) prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
        callback(granted)
    }

    private fun authorizedTreeUri(): Uri? {
        val raw = prefs.getString(KEY_TREE_URI, null) ?: return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return uri.takeIf { granted }
    }

    // ————————————————————————— 备份列表 —————————————————————————

    override fun listBackups(): List<BackupFileInfo> {
        val tree = authorizedTreeUri()
        val fromTree = tree?.let { runCatching { scanTreeBackups(it) }.getOrDefault(emptyList()) }
            ?: emptyList()
        val fromMediaStore = runCatching { scanMediaStoreBackups() }.getOrDefault(emptyList())
        val legacy = runCatching { scanFileSystemBackups() }.getOrDefault(emptyList())
        // 同名文件优先取已授权目录，其次 MediaStore，最后旧目录
        return (fromTree + fromMediaStore + legacy)
            .distinctBy { it.first.fileName }
            .sortedByDescending { it.second }
            .map { it.first }
    }

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
        val tree = authorizedTreeUri()
        if (tree != null) {
            return saveToTree(tree, fileName, json)
        }
        // 未授权也写公共 Download/Neixo_Schedule：高版本走 MediaStore，文件卸载后仍在
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveToMediaStore(fileName, json)
        }
        val dir = legacyWriteDir()
        if (!isUsableDir(dir)) {
            // 逐字保留原页面的失败信息：页面 catch 后 toast「备份失败: <原因>」
            throw IllegalStateException(AndroidMiscStr.backupDirNotWritableN(dir.absolutePath))
        }
        File(dir, fileName).writeText(json, Charsets.UTF_8)
        return true
    }

    override fun readBackupFile(id: String): String? {
        if (id.startsWith("content://")) {
            val uri = Uri.parse(id)
            val input = context.contentResolver.openInputStream(uri) ?: return null
            return readBoundedUtf8(input)
        }
        val file = File(id)
        if (!file.exists()) return null
        return readBoundedUtf8(FileInputStream(file))
    }

    override fun deleteBackupFile(id: String): Boolean {
        if (id.startsWith("content://")) {
            val uri = Uri.parse(id)
            return runCatching {
                // MediaStore 条目不能用 DocumentsContract 删；按 authority 分流
                if (uri.authority == MediaStore.AUTHORITY) {
                    context.contentResolver.delete(uri, null, null) > 0
                } else {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                }
            }.getOrDefault(false)
        }
        return File(id).delete()
    }

    // ─────────────────────────── SAF 读写实现 ───────────────────────────

    private fun saveToTree(tree: Uri, fileName: String, json: String): Boolean {
        val existing = runCatching { queryTreeChildren(tree) }.getOrNull()
            ?.firstOrNull { it.displayName == fileName }
        val docUri = existing?.uri ?: runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                treeDocumentUri(tree),
                "application/json",
                fileName,
            )
        }.getOrNull() ?: throw IllegalStateException(
            AndroidMiscStr.backupDirNotWritableN(backupDirectoryLabel())
        )
        val output = context.contentResolver.openOutputStream(docUri, "wt")
            ?: throw IllegalStateException(AndroidMiscStr.backupDirNotWritableN(backupDirectoryLabel()))
        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return true
    }

    private fun scanTreeBackups(tree: Uri): List<Pair<BackupFileInfo, Long>> =
        queryTreeChildren(tree).mapNotNull { doc ->
            buildBackupInfo(
                id = doc.uri.toString(),
                fileName = doc.displayName,
                lastModified = doc.lastModified,
                size = doc.size,
            ) {
                context.contentResolver.openInputStream(doc.uri)?.let { readBoundedUtf8(it) }
            }
        }

    private class TreeDocument(
        val uri: Uri,
        val displayName: String,
        val lastModified: Long,
        val size: Long,
    )

    private fun treeDocumentUri(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(
        tree,
        DocumentsContract.getTreeDocumentId(tree),
    )

    private fun queryTreeChildren(tree: Uri): List<TreeDocument> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )
        val result = mutableListOf<TreeDocument>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                if (mime == Document.MIME_TYPE_DIR) continue
                result += TreeDocument(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId),
                    displayName = name,
                    lastModified = cursor.getLong(3),
                    size = cursor.getLong(4),
                )
            }
        }
        return result
    }

    private fun queryTreeDisplayName(tree: Uri): String? = runCatching {
        context.contentResolver.query(
            treeDocumentUri(tree),
            arrayOf(Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun isTreeWritable(tree: Uri): Boolean = runCatching {
        val flags = context.contentResolver.query(
            treeDocumentUri(tree),
            arrayOf(Document.COLUMN_FLAGS),
            null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 } ?: 0
        flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0
    }.getOrDefault(false)

    // ─────────────────────────── MediaStore Download ───────────────────────────

    /** 公共备份目录：`Download/Neixo_Schedule`。 */
    private fun publicBackupPath(): String = "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR_NAME"

    /**
     * 未授权 SAF 时写入系统下载目录。
     *
     * 走 MediaStore 而不是 `File`：Android 10+ 分区存储下直接路径写入会被拒绝，
     * MediaStore 写入不需要任何存储权限，且文件不会随应用卸载被删除。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(fileName: String, json: String): Boolean {
        val resolver = context.contentResolver
        val existing = findMediaStoreFile(fileName)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            if (existing == null) {
                put(MediaStore.Downloads.RELATIVE_PATH, publicBackupPath())
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = existing ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException(AndroidMiscStr.backupDirNotWritableN(publicBackupPath()))
        val output = resolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException(AndroidMiscStr.backupDirNotWritableN(publicBackupPath()))
        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        if (existing == null) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findMediaStoreFile(fileName: String): Uri? {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(fileName, "${publicBackupPath()}%")
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0),
                )
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun scanMediaStoreBackups(): List<Pair<BackupFileInfo, Long>> {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND " +
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("${publicBackupPath()}%", "%.json")
        val result = mutableListOf<Pair<BackupFileInfo, Long>>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val uri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idColumn),
                )
                val info = buildBackupInfo(
                    id = uri.toString(),
                    fileName = name,
                    lastModified = cursor.getLong(modifiedColumn) * 1000L,
                    size = cursor.getLong(sizeColumn),
                ) {
                    context.contentResolver.openInputStream(uri)?.let { readBoundedUtf8(it) }
                } ?: continue
                result += info
            }
        }
        return result
    }

    // ─────────────────────────── 备份元数据解析 ───────────────────────────

    /** 从文件名 / 修改时间 / 大小统一生成列表项；三种来源（SAF / MediaStore / 旧目录）共用。 */
    private fun buildBackupInfo(
        id: String,
        fileName: String,
        lastModified: Long,
        size: Long,
        readJson: () -> String?,
    ): Pair<BackupFileInfo, Long>? {
        if (!fileName.endsWith(".json", ignoreCase = true)) return null
        val displaySdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val parseSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val baseName = fileName.substringBeforeLast('.')
        val (scheduleName, isFullBackup) = parseBackupFileBaseName(baseName)
        val type = if (isFullBackup) BackupStr.fullBackup() else BackupStr.singleSchedule()
        val match = BACKUP_NAME_REGEX.matchEntire(baseName)
        val timestamp = try {
            match?.let { parseSdf.parse(it.groupValues[2]) }?.let { displaySdf.format(it) }
                ?: AndroidMiscStr.unknownTime()
        } catch (_: Exception) {
            AndroidMiscStr.unknownTime()
        }
        val scheduleCount = runCatching {
            readJson()?.let { countSchedulesInBackupJson(it) } ?: 0
        }.getOrDefault(0)
        return BackupFileInfo(
            id = id,
            fileName = fileName,
            type = type,
            scheduleName = scheduleName,
            scheduleCount = scheduleCount,
            timestamp = timestamp,
            size = formatBackupSize(size),
            isFullBackup = isFullBackup,
        ) to lastModified
    }

    // ─────────────────────────── 旧目录（只读兼容） ───────────────────────────

    /** Android 9 及以下的写入目录：优先公共 Download，失败再退回私有目录。 */
    private fun legacyWriteDir(): File {
        val publicDir = publicBackupDir()
        return if (isUsableDir(publicDir)) publicDir else privateBackupDir()
    }

    private fun publicBackupDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        BACKUP_DIR_NAME,
    )

    private fun privateBackupDir(): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
        BACKUP_DIR_NAME
    ).apply { mkdirs() }

    private fun isUsableDir(dir: File): Boolean = try {
        (dir.exists() || dir.mkdirs()) && dir.canWrite()
    } catch (_: Exception) {
        false
    }

    /**
     * 扫描用的旧目录（公共 + 私有）。两个都扫，这样升级前写下的备份不会凭空"消失"。
     */
    private fun candidateBackupDirs(): List<File> =
        listOf(publicBackupDir(), privateBackupDir())
            .filter { it.isDirectory }
            .distinctBy { it.absolutePath }

    private fun scanFileSystemBackups(): List<Pair<BackupFileInfo, Long>> =
        candidateBackupDirs()
            .flatMap { dir -> dir.listFiles()?.asList() ?: emptyList() }
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .mapNotNull { file ->
                buildBackupInfo(
                    id = file.absolutePath,
                    fileName = file.name,
                    lastModified = file.lastModified(),
                    size = file.length(),
                ) {
                    if (file.length() > MAX_BACKUP_PARSE_BYTES) null
                    else file.readText(Charsets.UTF_8)
                }
            }

    private fun readExternalBackupJson(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException(AndroidMiscStr.cannotReadSelectedFile())
        return readBoundedUtf8(inputStream)
    }

    private companion object {
        const val PREFS_NAME = "backup_center_android"
        const val KEY_TREE_URI = "backup_tree_uri"

        /** SAF 选文件的固定注册 key（同 key 重复注册 = 替换回调槽，不抛错） */
        const val KEY_PICK_BACKUP = "com.haooz.chedule.backup.pick_document"

        /** SAF 选目录的固定注册 key */
        const val KEY_PICK_TREE = "com.haooz.chedule.backup.pick_tree"
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
