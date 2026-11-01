package com.haooz.nexio.ohos

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.storage.CourseRowStore
import com.haooz.chedule.data.storage.KeyValueStore
import com.haooz.chedule.data.storage.SqlDriverBackend
import com.haooz.chedule.data.storage.SqlNamespaceStorage
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.chdir

/**
 * 鸿蒙的存储装配：与 Android / iOS **同一个 `nexio.db` schema、同一份 [SqlDriverBackend]**。
 *
 * 差别只有两处：
 * 1. driver 用 `NativeSqliteDriver`（Android 是 `AndroidSqliteDriver`）；
 * 2. 数据库文件放在**沙箱绝对路径**下 —— `NativeSqliteDriver` 在 Kotlin/Native 上
 *    用相对路径会落到进程工作目录，而鸿蒙应用的 cwd 不可写。
 *
 * sqlite3 本体由 `third_party/sqlite` 自带（鸿蒙 NDK 的 sysroot 里没有）。
 *
 * ⚠ 与 Android 的 `SqliteStorage` / iOS 的 `IosSqlStorage` 一样：进程内只许有一份 backend，
 * 两个 `NexioDatabase` 实例会让「删课表 + 清课程」退回成两次提交。
 */
object OhosSqlStorage {

    /** 数据库文件绝对路径。`OhosEnv` 没就绪时返回 null（此时不建库）。 */
    private fun dbPath(): String? {
        val dir = OhosEnv.filesDir.takeIf { it.isNotEmpty() } ?: return null
        return "$dir/${SqlDriverBackend.DB_NAME}"
    }

    private val storage by lazy {
        val path = dbPath() ?: error("OhosSqlStorage: 沙箱目录未注入（ArkTS 未调 setAppEnv）")
        // `NativeSqliteDriver` 的 name 只接受**纯文件名**：它会拒绝含路径分隔符的值
        // （"File … contains a path separator"），`file:` 前缀也不行（校验在 URI 分支之前）。
        // 它把库文件建在**进程工作目录**下，而鸿蒙应用的 cwd 不可写 —— 所以先把 cwd
        // 切到沙箱目录，再交一个纯文件名。
        // 其余 IO 都用绝对路径（`AppFiles.root`），切 cwd 不影响它们。
        runCatching { chdir(path.substringBeforeLast('/', "")) }
        SqlNamespaceStorage(
            SqlDriverBackend(
                NativeSqliteDriver(
                    schema = NexioDatabase.Schema,
                    name = SqlDriverBackend.DB_NAME,
                ),
            ),
        )
    }

    /** 取某个命名空间的键值存储。异常外抛，没有第二套存储可退。 */
    fun store(namespace: String): KeyValueStore = storage.store(namespace)

    /** 课程行表门面。 */
    fun courseRows(): CourseRowStore = storage.courseRows()
}
