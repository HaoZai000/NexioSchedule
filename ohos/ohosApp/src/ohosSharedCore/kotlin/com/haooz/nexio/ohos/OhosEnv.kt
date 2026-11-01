package com.haooz.nexio.ohos

import com.haooz.chedule.data.platform.AppFile
import com.haooz.chedule.data.platform.AppFiles
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.F_OK
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.rewind
import platform.posix.SEEK_END
import platform.posix.SEEK_SET

/**
 * 鸿蒙端的运行环境：沙箱目录 + 屏幕像素尺寸。
 *
 * 这两样都必须由 ArkTS 传进来（`Index.ets` → `libentry.so` → 这里），因为
 * Kotlin/Native 侧拿不到 `UIAbilityContext`，而 `context.filesDir` 只有它给得出。
 */
object OhosEnv {
    /** 应用私有目录（`context.filesDir`）。 */
    var filesDir: String = ""
        private set

    var screenWidthPx: Int = 0
        private set

    var screenHeightPx: Int = 0
        private set

    /**
     * 屏幕圆角半径（px），来自 ArkTS 的 `Display.getRoundedCorner()`（API 17+）。
     * 无圆角的设备（外接显示器、部分模拟器）为 0，消费方按「不裁切」处理。
     */
    var screenCornerRadiusPx: Int = 0
        private set

    val isReady: Boolean get() = filesDir.isNotEmpty()

    /** 由 [NexioSetAppEnv] 调用；重复调用幂等。 */
    internal fun init(dir: String, w: Int, h: Int, cornerRadiusPx: Int) {
        filesDir = dir
        screenWidthPx = w
        screenHeightPx = h
        screenCornerRadiusPx = cornerRadiusPx
        if (!AppFiles.isReady) {
            AppFiles.init(OhosAppFile(dir))
        }
    }
}

/**
 * ArkTS 侧的 `setAppEnv(filesDir, screenW, screenH)` 落到这里。
 *
 * 首次调用时顺手把 [AppFiles] 初始化掉 —— 壁纸、课表缓存、设置都靠它落盘。
 * 重复调用是幂等的（Android 侧同理，`AppFiles.init` 只覆盖引用）。
 */
@OptIn(ExperimentalForeignApi::class)
@CName("NexioSetAppEnv")
fun NexioSetAppEnv(filesDir: CPointer<ByteVar>?, screenW: Int, screenH: Int, cornerRadiusPx: Int) {
    val dir = filesDir?.toKString() ?: return
    OhosEnv.init(dir, screenW, screenH, cornerRadiusPx)
}

/**
 * [AppFile] 的鸿蒙实现：直接用 POSIX 文件 API。
 *
 * 为什么不用 ArkTS 的 `@ohos.file.fs`：那需要跨 NAPI 调回 JS 线程，而 [AppFile] 的
 * 读写在首帧路径上是**同步**的（壁纸要在第一帧前读出来）。POSIX 走沙箱内的普通文件，
 * 同步、无线程切换。
 */
internal class OhosAppFile(override val path: String) : AppFile {

    override fun exists(): Boolean = access(path, F_OK) == 0

    @OptIn(ExperimentalForeignApi::class)
    override fun readBytes(): ByteArray = memScoped {
        val file = fopen(path, "rb") ?: return@memScoped ByteArray(0)
        try {
            fseek(file, 0L, SEEK_END)
            val size = ftell(file) ?: 0L
            rewind(file)
            if (size <= 0L) return@memScoped ByteArray(0)
            val bytes = ByteArray(size.toInt())
            val read = bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, size.toULong(), file)
            }
            if (read.toLong() != size) bytes.copyOf(read.toInt()) else bytes
        } finally {
            fclose(file)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun writeBytes(bytes: ByteArray) {
        // AppFile 的契约：写之前自动补父目录
        path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { OhosAppFile(it).mkdirs() }
        val file = fopen(path, "wb") ?: return
        try {
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned -> fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file) }
            }
        } finally {
            fclose(file)
        }
    }

    override fun mkdirs() {
        // 逐级创建，忽略“已存在”
        var acc = if (path.startsWith("/")) "" else "."
        for (segment in path.split('/')) {
            if (segment.isEmpty()) continue
            acc = "$acc/$segment"
            mkdir(acc, 0x1EDu) // 0755
        }
    }

    override fun delete(): Boolean = remove(path) == 0

    override fun resolve(relative: String): OhosAppFile =
        OhosAppFile(if (path.endsWith("/")) path + relative else "$path/$relative")
}
