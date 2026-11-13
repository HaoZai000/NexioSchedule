package com.haooz.chedule.data.backup

import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseJsonObject

/**
 * 本地备份文件的读写能力。Android 实现是 `AndroidBackupFileController`，桌面/iOS 用 [NoopBackupFileController]。
 * 备份 JSON 的序列化见 `BackupCodec.kt`，本接口只管文件在哪、怎么读写。
 *
 * ⚠ [listBackups] / [saveBackupFile] / [readBackupFile] / [deleteBackupFile] 是阻塞 IO，调用方需用
 * `withContext(ioDispatcher)` 包住；[pickAndReadBackup] 是异步发起系统选择器。
 */
interface BackupFileController {

    /** 扫描备份目录，按最后修改时间倒序。会逐个读 JSON 统计课表数，需在 IO 线程调。 */
    fun listBackups(): List<BackupFileInfo>

    /**
     * 拉起系统文件选择器选一份备份并读出内容。
     *
     * @param onResult `(json, displayName)`；用户取消或读取失败时均为 `(null, null)`，`displayName` 尽力而为。
     */
    fun pickAndReadBackup(onResult: (json: String?, displayName: String?) -> Unit)

    /** 把 [json] 写入备份目录下的 [fileName] 文件（UTF-8）。失败时抛异常，返回 true 表示写入成功。 */
    fun saveBackupFile(fileName: String, json: String): Boolean

    /**
     * 读取 [BackupFileInfo.id] 对应备份文件的内容（含 8 MB 上限校验）。文件不存在返回 null；
     * 超过上限或读取异常时抛异常。
     */
    fun readBackupFile(id: String): String?

    /** 删除 [BackupFileInfo.id] 对应的备份文件。文件不存在或不可写返回 false。 */
    fun deleteBackupFile(id: String): Boolean

    /**
     * 当前本机备份目录的展示文本。
     *
     * 分区存储平台（Android 10+）默认没有可长期读写的公共目录，页面需要把
     * 「已授权目录 / 私有临时目录」如实显示出来。
     */
    fun backupDirectoryLabel(): String = ""

    /** 是否支持让用户选择 / 授权备份目录（Android 用 SAF 目录树）。 */
    fun canChooseBackupDirectory(): Boolean = false

    /** 当前备份目录是否已获得长期读写授权。 */
    fun isBackupDirectoryAuthorized(): Boolean = true

    /**
     * 拉起系统目录选择器，让用户授权备份目录。
     *
     * @param onResult true 表示已获得可写授权；取消或失败返回 false
     */
    fun chooseBackupDirectory(onResult: (Boolean) -> Unit) {
        onResult(false)
    }
}

/**
 * 一条本地备份的展示元数据（扫描目录时解析文件名 / 时间 / 大小 / 是否全量得出）。
 */
data class BackupFileInfo(
    /** 平台不透明句柄（Android 旧目录 = 绝对路径，SAF 目录 = content URI）；传回控制器做读/删 */
    val id: String,
    /** 文件名（含 .json 后缀，作为列表 key 与删除弹窗文案） */
    val fileName: String,
    /** 「全部备份」/「单独课表」 */
    val type: String,
    /** 单独课表备份的课表名（全量备份为 null） */
    val scheduleName: String?,
    /** 备份内包含的课表数（解析 JSON 统计；解析失败为 0） */
    val scheduleCount: Int,
    /** 由文件名时间戳解析出的展示时间（解析不出为「未知时间」） */
    val timestamp: String,
    /** 人类可读的文件大小（B / KB / MB） */
    val size: String,
    /** 是否全量备份（文件名以「全部备份」开头） */
    val isFullBackup: Boolean,
)

/** 解析单个备份文件前允许的最大体积，避免把超大/无关文件整个读进内存。 */
const val MAX_BACKUP_PARSE_BYTES: Long = 8L * 1024 * 1024

/** 备份目录名（旧版 Android 公共 / 私有 Download 下的子目录；SAF 目录由用户自选） */
const val BACKUP_DIR_NAME: String = "Neixo_Schedule"

/** 文件名形如「<课表名>_yyyyMMdd_HHmmss」 */
val BACKUP_NAME_REGEX: Regex = Regex("^(.+)_(\\d{8}_\\d{6})$")

/**
 * 统计备份 JSON 里的课表数：单课表备份（含 `schedule_name` + `courses`）为 1；全量备份按
 * `schedule_<名>_courses` 键的前缀去重计数；解析失败为 0。键名是备份落盘格式，逐字不能改。
 */
fun countSchedulesInBackupJson(json: String): Int = try {
    @Suppress("UNCHECKED_CAST")
    val data = jsonToPlainValue(parseJsonObject(json)) as Map<String, Any>
    if (data.containsKey("schedule_name") && data.containsKey("courses")) {
        1
    } else {
        val prefixes = mutableSetOf<String>()
        for (key in data.keys) {
            if (key.startsWith("schedule_") && key.contains("_courses")) {
                val name = key.removePrefix("schedule_").substringBeforeLast("_courses")
                prefixes.add(name)
            }
        }
        prefixes.size
    }
} catch (_: Exception) {
    0
}

/** 人类可读的文件大小（B / KB / MB）。 */
fun formatBackupSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

/**
 * 解析备份文件名（不含扩展名）→（课表名 or null, 是否全量）。以「全部备份」开头为全量；
 * 否则用 [BACKUP_NAME_REGEX] 取第 1 组为课表名，匹配不上就整个名字当课表名。
 */
fun parseBackupFileBaseName(baseName: String): Pair<String?, Boolean> {
    val isFullBackup = baseName.startsWith("全部备份")
    if (isFullBackup) return null to true
    val scheduleName = BACKUP_NAME_REGEX.matchEntire(baseName)?.groupValues?.get(1) ?: baseName
    return scheduleName to false
}

/** 桌面/iOS 的空实现：恒为「无备份文件」。 */
object NoopBackupFileController : BackupFileController {
    override fun listBackups(): List<BackupFileInfo> = emptyList()

    // 没有系统选择器可拉起，直接回调「用户取消」
    override fun pickAndReadBackup(onResult: (String?, String?) -> Unit) = onResult(null, null)
    override fun saveBackupFile(fileName: String, json: String): Boolean = false
    override fun readBackupFile(id: String): String? = null
    override fun deleteBackupFile(id: String): Boolean = false
}

