package com.haooz.chedule.data.platform

import java.io.File

/** 基于 java.io.File 的文件访问；写入字节时自动创建父目录。 */
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

    override fun delete(): Boolean = file.delete()

    override fun resolve(relative: String): AppFile = FileAppFile(File(file, relative))

    override fun toString(): String = "FileAppFile($path)"
}
