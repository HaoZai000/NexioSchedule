package com.haooz.chedule.data

/**
 * 内存版 [AppFile]，供测试与非持久化场景使用。
 *
 * 与 [InMemoryKeyValueStore] 同理：让 commonTest 能在没有真实文件系统的情况下
 * 验证「拼路径 → 建目录 → 读 → 写」的语义。
 */
class InMemoryAppFile private constructor(
    override val path: String,
    private val files: MutableMap<String, ByteArray>,
    private val dirs: MutableSet<String>,
    private val isRoot: Boolean = false,
) : AppFile {

    override fun exists(): Boolean = isRoot || files.containsKey(path) || dirs.contains(path)

    override fun readBytes(): ByteArray = files[path] ?: error("文件不存在: $path")

    override fun writeBytes(bytes: ByteArray) {
        // 自动建父目录（与 FileAppFile.writeBytes 的契约一致）
        var parent = parentOf(path)
        while (!parent.isNullOrEmpty()) {
            dirs.add(parent)
            parent = parentOf(parent)
        }
        files[path] = bytes
    }

    override fun mkdirs() {
        var p: String? = path
        while (!p.isNullOrEmpty()) {
            dirs.add(p)
            p = parentOf(p)
        }
    }

    override fun resolve(relative: String): AppFile {
        val normalized = relative.trim('/')
        val child = if (path.isEmpty()) normalized else "$path/$normalized"
        return InMemoryAppFile(child, files, dirs)
    }

    /** 已写入的文件路径快照，便于断言。 */
    fun filePaths(): Set<String> = files.keys.toSet()

    fun directoryPaths(): Set<String> = dirs.toSet()

    private fun parentOf(p: String): String? {
        val i = p.lastIndexOf('/')
        return if (i <= 0) null else p.substring(0, i)
    }

    companion object {
        fun root(): InMemoryAppFile = InMemoryAppFile("", HashMap(), HashSet(), isRoot = true)
    }
}
