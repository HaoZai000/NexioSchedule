@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import com.haooz.chedule.data.backup.BACKUP_DIR_NAME
import com.haooz.chedule.data.backup.BACKUP_NAME_REGEX
import com.haooz.chedule.data.backup.BackupFileController
import com.haooz.chedule.data.backup.BackupFileInfo
import com.haooz.chedule.data.backup.MAX_BACKUP_PARSE_BYTES
import com.haooz.chedule.data.backup.countSchedulesInBackupJson
import com.haooz.chedule.data.backup.formatBackupSize
import com.haooz.chedule.data.backup.parseBackupFileBaseName
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.i18n.L
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir

private val UNKNOWN_TIME = L("未知时间", "Unknown time")

/**
 * [BackupFileController] 的鸿蒙实现。
 *
 * 备份目录固定在应用私有目录 `filesDir/Neixo_Schedule`（与 iOS 的 Documents 同口径）：
 * 鸿蒙普通应用没有可长期直写的公共目录，系统文件选择器只能逐次授权单个 URI，
 * 无法作为「固定目录」被列表扫描。文件格式、命名规则、8 MB 上限与 Android / iOS 完全一致，
 * 因此 Android 写出的备份可以拷进来恢复，反之亦然。
 */
class OhosBackupFileController : BackupFileController {

    private fun directory(): OhosAppFile? =
        if (AppFiles.isReady) OhosAppFile(backupDirPath()) else null

    private fun backupDirPath(): String = AppFiles.root.resolve(BACKUP_DIR_NAME).path

    override fun listBackups(): List<BackupFileInfo> {
        val dir = directory() ?: return emptyList()
        if (!dir.exists()) return emptyList()
        return listNames(dir.path)
            .filter { it.endsWith(".json", ignoreCase = true) }
            .mapNotNull { name -> buildInfo(dir, name) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** 文件名的 `yyyyMMdd_HHmmss` 是唯一可靠时间源（不依赖各平台 stat 的字段差异）。 */
    private fun buildInfo(dir: OhosAppFile, fileName: String): Pair<BackupFileInfo, String>? {
        val baseName = fileName.substringBeforeLast('.')
        val (scheduleName, isFullBackup) = parseBackupFileBaseName(baseName)
        val bytes = runCatching { dir.resolve(fileName).readBytes() }.getOrDefault(ByteArray(0))
        val scheduleCount = if (bytes.isEmpty()) {
            0
        } else {
            runCatching { countSchedulesInBackupJson(bytes.decodeToString()) }.getOrDefault(0)
        }
        val stamp = BACKUP_NAME_REGEX.matchEntire(baseName)?.groupValues?.get(2)
        return BackupFileInfo(
            id = dir.resolve(fileName).path,
            fileName = fileName,
            type = if (isFullBackup) BackupStr.fullBackup() else BackupStr.singleSchedule(),
            scheduleName = scheduleName,
            scheduleCount = scheduleCount,
            timestamp = stamp?.let(::formatStamp) ?: UNKNOWN_TIME(),
            size = formatBackupSize(bytes.size.toLong()),
            isFullBackup = isFullBackup,
        ) to (stamp ?: baseName)
    }

    override fun pickAndReadBackup(onResult: (String?, String?) -> Unit) {
        pickTextFileForImport { text -> onResult(text, null) }
    }

    override fun saveBackupFile(fileName: String, json: String): Boolean {
        val dir = directory() ?: return false
        return runCatching {
            dir.mkdirs()
            val file = dir.resolve(fileName)
            file.writeBytes(json.encodeToByteArray())
            file.exists()
        }.getOrDefault(false)
    }

    override fun readBackupFile(id: String): String? {
        val file = OhosAppFile(id)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.size.toLong() > MAX_BACKUP_PARSE_BYTES) {
            throw IllegalStateException("备份文件超过 ${MAX_BACKUP_PARSE_BYTES / (1024 * 1024)} MB")
        }
        return bytes.decodeToString()
    }

    override fun deleteBackupFile(id: String): Boolean = OhosAppFile(id).delete()

    override fun backupDirectoryLabel(): String = backupDirPath()

    override fun canChooseBackupDirectory(): Boolean = false

    override fun isBackupDirectoryAuthorized(): Boolean = true
}

actual fun createBackupFileController(): BackupFileController = OhosBackupFileController()

/** `yyyyMMdd_HHmmss` → `yyyy-MM-dd HH:mm:ss`（与 Android 列表展示同格式）。 */
private fun formatStamp(stamp: String): String {
    if (stamp.length != 15) return UNKNOWN_TIME()
    return buildString(19) {
        append(stamp, 0, 4).append('-')
        append(stamp, 4, 6).append('-')
        append(stamp, 6, 8).append(' ')
        append(stamp, 9, 11).append(':')
        append(stamp, 11, 13).append(':')
        append(stamp, 13, 15)
    }
}

/** 目录项遍历（`AppFile` 只有单文件 API，没有 list）。 */
private fun listNames(path: String): List<String> = memScoped {
    val handle = opendir(path) ?: return@memScoped emptyList()
    try {
        val names = mutableListOf<String>()
        while (true) {
            val entry = readdir(handle) ?: break
            val name = entry.pointed.d_name.toKString()
            if (name != "." && name != "..") names.add(name)
        }
        names
    } finally {
        closedir(handle)
    }
}
