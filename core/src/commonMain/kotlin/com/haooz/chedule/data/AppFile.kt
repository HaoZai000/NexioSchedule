package com.haooz.chedule.data

/**
 * 应用私有目录里的一个文件 / 目录句柄。
 *
 * 只覆盖项目**实际用到**的能力（拼接 / 存在性 / 读 / 写 / 建目录），不做通用文件系统。
 * `java.io.File` 在 Kotlin/Native 与 JS 上都不存在，所以必须抽象。
 */
interface AppFile {
    /** 完整路径，仅用于日志与调试；不保证跨平台格式一致。 */
    val path: String

    fun exists(): Boolean

    fun readBytes(): ByteArray

    /** 按 UTF-8 读取文本。等价迁移前 `java.io.File.readText()`。 */
    fun readText(): String = readBytes().decodeToString()

    /** 覆盖写入。**会自动创建缺失的父目录**（调用方不必再手动 mkdirs）。 */
    fun writeBytes(bytes: ByteArray)

    /** 把本路径当作目录创建（含父级）。 */
    fun mkdirs()

    /** 解析相对子路径（以 `/` 分隔）。 */
    fun resolve(relative: String): AppFile
}

/**
 * 应用文件系统入口。
 *
 * ## 为什么用注入而不是 expect/actual
 *
 * 与 [AppStorage] 同一模式：`:core` 只声明接口，平台实现由应用启动时注入。
 * 这样 `:core` 的 commonMain 保持平台中立（`linuxX64` 编译门禁才能通过），
 * Android 侧继续用 `java.io.File`（行为零变化），
 * iOS 侧将来注入 `NSFileManager` 实现即可 —— 与 [KeyValueStore] 的处理方式一致。
 *
 * ## 初始化时机
 *
 * 必须在任何 [root] / [readAsset] 使用之前调用，否则抛异常而不是静默失败 ——
 * 静默返回空目录会让「学校索引读不出来」这类问题极难定位。
 */
object AppFiles {
    private var rootFile: AppFile? = null
    private var assetReader: ((String) -> ByteArray)? = null

    /** 是否已初始化。 */
    val isReady: Boolean get() = rootFile != null

    /**
     * @param root 应用私有目录（Android 传 `context.filesDir`）
     * @param readAsset 读取打包进安装包的资源（Android 传 `context.assets`）。
     *   读取失败时**抛异常**，由调用方决定如何降级 —— 保留原实现「失败打日志并跳过引导」的语义。
     */
    fun init(root: AppFile, readAsset: ((String) -> ByteArray)? = null) {
        rootFile = root
        assetReader = readAsset
    }

    /** 应用私有目录根。 */
    val root: AppFile
        get() = rootFile ?: error(
            "AppFiles 未初始化：请在 Application.onCreate() 中调用 AppFiles.init(filesDir, assets)"
        )

    /** 读取内置资源；未注入读取器时抛异常（调用方通常已有 try/catch 降级）。 */
    fun readAsset(path: String): ByteArray =
        (assetReader ?: error("AppFiles 未注入 readAsset，无法读取内置资源: $path")).invoke(path)
}
