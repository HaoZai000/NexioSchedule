package com.haooz.chedule.data

import java.io.File

/**
 * [AppFile] 的 Android / JVM 实现，直接包装 `java.io.File`。
 *
 * 行为与迁移前逐字一致（原代码就是 `File(context.filesDir, "…")` + `readBytes/writeBytes`），
 * 唯一新增的是 [writeBytes] 自动建父目录 —— 原来是在每个调用点手写
 * `parentFile?.mkdirs()`，收进来少一处遗漏机会。
 */
class FileAppFile(private val file: File) : AppFile {

    override val path: String get() = file.path

    override fun exists(): Boolean = file.exists()

    override fun readBytes(): ByteArray = file.readBytes()

    override fun writeBytes(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override fun mkdirs() {
        file.mkdirs()
    }

    override fun resolve(relative: String): AppFile = FileAppFile(File(file, relative))

    override fun toString(): String = "FileAppFile($path)"
}
