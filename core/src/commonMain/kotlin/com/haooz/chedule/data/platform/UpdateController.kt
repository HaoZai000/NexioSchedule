package com.haooz.chedule.data.platform

/**
 * 应用更新控制器；本地安装包以绝对路径表示。
 *
 * ⚠ [checkForUpdate] / [hasValidApk] / [resolveApkUrl] 是阻塞 IO，调用方需用 `withContext(ioDispatcher)`
 * 包住；[downloadApk] 是 suspend，实现内部自行切调度器。
 */
interface UpdateController {
    /** 当前安装的版本号；取不到时为 `"未知"`。 */
    val currentVersion: String

    /** 检查更新。失败或无新版本时返回 `(false, null)`，不抛异常。 */
    fun checkForUpdate(source: String, channel: String): Pair<Boolean, UpdateRelease?>

    /** 把一次检查到的 release 落到 `update_settings` 缓存（弹窗与设置页共用同一份）。 */
    fun persistRelease(release: UpdateRelease)

    /** 记录该 tag 安装包的期望校验值（sha256 / 字节数），供后续复核使用 */
    fun rememberApkDigest(tag: String, sha256: String?, sizeBytes: Long?)

    /** 回读 [rememberApkDigest] 记下的 SHA-256；无则 null */
    fun rememberedApkSha256(tag: String): String?

    /** 回读 [rememberApkDigest] 记下的字节数；无则 null */
    fun rememberedApkSize(tag: String): Long?

    /** 本地是否已有一份**可安装**的安装包（校验不过时 Android 侧会顺手删掉损坏包）。 */
    fun hasValidApk(tag: String): Boolean

    /** 该 tag 对应本地安装包的**绝对路径**。不判断存在性，调用方先用 [hasValidApk] 判定，无效时自行置 null。 */
    fun localApkPath(tag: String): String

    /** 解析下载地址（缓存优先，缓存不可用回源重查），解析不到返回 null。 */
    fun resolveApkUrl(tag: String, source: String, channel: String): String?

    /**
     * 下载 APK 到本地并做完整性校验。成功返回落盘文件的**绝对路径**；失败抛 `Exception`。
     *
     * @param onProgress 0f..1f 进度回调，实现负责切回主线程
     */
    suspend fun downloadApk(
        apkUrl: String,
        tag: String,
        onProgress: suspend (Float) -> Unit,
    ): String

    /**
     * 清掉本地缓存的旧安装包（保留下载中/已校验完成的当前包）。
     *
     * @param keepTag 要保留的版本 tag；null/空白 = 全清
     */
    fun cleanOldApks(keepTag: String?)

    /**
     * 安装本地 APK（Android：优先 Shizuku 静默安装，失败回退系统安装器）。
     *
     * @param onInstallingChanged 安装中状态回调（主线程）
     * @param onFinished 结束回调（静默成功 / 系统安装器已拉起都会触发）
     */
    fun installApk(
        apkPath: String,
        onInstallingChanged: (Boolean) -> Unit,
        onFinished: (() -> Unit)? = null,
    )
}

/** 一次检查到的 Release 元数据。 */
data class UpdateRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String,
    val createdAt: String,
    /** Release 资产下发的 SHA-256（GitHub 为 digest="sha256:<hex>"）；拿不到时为 null */
    val apkSha256: String? = null,
    /** Release 资产声明的字节数 */
    val apkSize: Long? = null,
)

object NoopUpdateController : UpdateController {
    override val currentVersion: String get() = "未知"
    override fun checkForUpdate(source: String, channel: String): Pair<Boolean, UpdateRelease?> =
        Pair(false, null)
    override fun persistRelease(release: UpdateRelease) {}
    override fun rememberApkDigest(tag: String, sha256: String?, sizeBytes: Long?) {}
    override fun rememberedApkSha256(tag: String): String? = null
    override fun rememberedApkSize(tag: String): Long? = null
    override fun hasValidApk(tag: String): Boolean = false
    // hasValidApk 恒 false，正常流程走不到这里；返回空串只为满足非空签名
    override fun localApkPath(tag: String): String = ""
    override fun cleanOldApks(keepTag: String?) {}
    override fun resolveApkUrl(tag: String, source: String, channel: String): String? = null
    // 同上，下载按钮拿到 null 后会提前 return
    override suspend fun downloadApk(
        apkUrl: String,
        tag: String,
        onProgress: suspend (Float) -> Unit,
    ): String = ""
    override fun installApk(
        apkPath: String,
        onInstallingChanged: (Boolean) -> Unit,
        onFinished: (() -> Unit)?,
    ) {}
}

/**
 * 解析版本号为数字序列。
 *
 * 格式：`[v]MAJOR.MINOR.PATCH[-DATE]` 或 `[v]MAJOR.MINOR.PATCH.BETA[-DATE]`
 * 例：`1.5.0-0905` → `[1,5,0]`；`1.5.0.2-0905` → `[1,5,0,2]`
 * 日期后缀（`-` 之后）与构建元数据（`+` 之后）不参与比较；解析不出数字的段按 0 处理。
 */
fun parseVersion(raw: String): List<Int> {
    val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
    return cleaned.split('.').map { it.toIntOrNull() ?: 0 }
}

/** 是否 beta 版（第 4 段版本号存在） */
fun isBetaVersion(raw: String): Boolean = parseVersion(raw).size >= 4

/** 比较版本：[remote] 是否比 [local] 更新。忽略日期后缀；段数不同时短的一方补 0。 */
fun isNewerVersion(remote: String, local: String): Boolean {
    val r = parseVersion(remote)
    val l = parseVersion(local)
    val max = maxOf(r.size, l.size)
    for (i in 0 until max) {
        val rv = r.getOrElse(i) { 0 }
        val lv = l.getOrElse(i) { 0 }
        if (rv > lv) return true
        if (rv < lv) return false
    }
    return false
}
