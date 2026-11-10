package com.haooz.chedule.data.storage

import android.content.Context
import com.haooz.chedule.data.platform.NexioLog

/**
 * 建库、按命名空间发 [com.haooz.chedule.data.storage.KeyValueStore]、首次访问时从同名 SharedPreferences 导入一次。
 *
 * - 导入、回读比对、写标记同一事务，任一步失败整体回滚且不写标记；
 * - 失败直接抛给调用方，没有第二套存储可退；
 * - SharedPreferences 只是迁移源：`shared_prefs` 目录被删之后，这里读到的永远是空；
 * - [LegacyStorageMigration] 扫完旧存储会写 [META_LEGACY_SCAN_DONE]，之后新建的命名空间
 *   （按课表分区的节假日等）不再探测 SharedPreferences —— 动态名字不受 prefs 文件名规则约束。
 */
object SqliteStorage {

    private const val TAG = "SqliteStorage"

    /** 导入标记的前缀。带命名空间，缺一个就少导一个，互不影响。 */
    private const val META_IMPORTED_PREFIX = "sp_imported:"

    /** 旧 SharedPreferences 已全部扫描并导入完成的全局标记。 */
    private const val META_LEGACY_SCAN_DONE = "sp_legacy_scan_done"

    /** 整个应用的库连接。[backendOf] 懒建，[backendLock] 守。 */
    @Volatile
    private var backend: AndroidSqlBackend? = null

    /** 建库 / 取库。只在首次创建 backend 时短暂持有，不覆盖导入过程。 */
    private val backendLock = Any()

    /**
     * 「这个命名空间首次导入」的锁，按命名空间分。
     * 一把全局锁的后果是：主线程要读 `app_preferences`，得排在 IO 线程导入几百 KB 的课表后面。
     */
    private val namespaceLocksLock = Any()
    private val namespaceLocks = HashMap<String, Any>()

    /** 课程行表门面，与键值表共用同一个 [AndroidSqlBackend]。 */
    @Volatile
    private var courseStore: CourseRowStore? = null

    /** 每个命名空间唯一的实例。两个实例 = 两份互相看不见的快照。 */
    private val stores = java.util.concurrent.ConcurrentHashMap<String, SqliteKeyValueStore>()

    private fun lockOf(namespace: String): Any = synchronized(namespaceLocksLock) {
        namespaceLocks.getOrPut(namespace) { Any() }
    }

    private fun backendOf(context: Context): AndroidSqlBackend =
        backend ?: synchronized(backendLock) {
            backend ?: AndroidSqlBackend(context.applicationContext).also { backend = it }
        }

    /** 取某个命名空间的存储。异常外抛，调用方没有别的存储可用。 */
    fun store(context: Context, namespace: String): KeyValueStore =
        synchronized(lockOf(namespace)) {
            val b = backendOf(context)
            importFromSharedPreferencesIfNeeded(context, b, namespace)
            stores.getOrPut(namespace) { SqliteKeyValueStore(b, namespace) }
        }

    /** 该命名空间是否已迁进库（含「文件本来就是空」与「扫描完成后才新建」）。 */
    fun isImported(context: Context, namespace: String): Boolean {
        val b = backendOf(context)
        return b.readMeta(META_IMPORTED_PREFIX + namespace) != null ||
            b.readMeta(META_LEGACY_SCAN_DONE) != null
    }

    /** 旧 SharedPreferences 已全部扫完。此后新建的命名空间不再探测 SharedPreferences。 */
    fun markLegacyScanComplete(context: Context) {
        val b = backendOf(context)
        if (b.readMeta(META_LEGACY_SCAN_DONE) != null) return
        b.transaction { putMeta(META_LEGACY_SCAN_DONE, "1") }
    }

    /** 课程行表门面。异常外抛。 */
    fun courseRows(context: Context): CourseRowStore = courseStore ?: synchronized(backendLock) {
        courseStore ?: SqliteCourseRowStore(backendOf(context).database).also { courseStore = it }
    }

    /** 从同名 SharedPreferences 文件导入。空文件也写标记，表示「这个命名空间确实没有数据」。 */
    private fun importFromSharedPreferencesIfNeeded(
        context: Context,
        backend: SqlBackend,
        namespace: String,
    ) {
        if (backend.readMeta(META_IMPORTED_PREFIX + namespace) != null) return
        // 旧存储已扫完：这时才出现的命名空间不可能是历史数据，也不再拿它当 prefs 文件名
        if (backend.readMeta(META_LEGACY_SCAN_DONE) != null) return

        val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
        val snapshot = try {
            prefs.all
        } catch (e: Exception) {
            error("读取 $namespace 的 SharedPreferences 失败：$e")
        }

        val rows = snapshot.mapNotNull { (key, value) -> value?.let { toRow(key, it) } }
        val unconvertible = snapshot.size - rows.size
        if (unconvertible > 0) {
            // 部分值转不了不算失败，但要留痕
            NexioLog.w(TAG, "$namespace 有 $unconvertible 个键类型无法识别，已跳过（其余正常导入）")
        }
        if (snapshot.isNotEmpty() && rows.isEmpty()) {
            error("$namespace 的 ${snapshot.size} 个键一个都没转成功，放弃导入")
        }

        try {
            backend.transaction {
                rows.forEach { upsert(namespace, it) }

                // 自校验：读回刚写的，逐键比对类型和值
                val written = load(namespace).associateBy { it.key }
                for (row in rows) {
                    val back = written[row.key]
                        ?: error("$namespace/${row.key} 写入后读不回来")
                    if (back.type != row.type) {
                        error("$namespace/${row.key} 类型漂移：写入 ${row.type}，读回 ${back.type}")
                    }
                    if (back.value != row.value) {
                        error("$namespace/${row.key} 值不一致：写入 ${row.value}，读回 ${back.value}")
                    }
                }

                putMeta(META_IMPORTED_PREFIX + namespace, rows.size.toString())
            }
        } catch (t: Throwable) {
            error("导入 $namespace 失败，已整体回滚：${t.message}")
        }

        NexioLog.w(TAG, "已从 SharedPreferences 导入 $namespace：${rows.size} 个键")
    }

    /** SharedPreferences 运行时类型 → 带标签的行。认不出返回 null。 */
    private fun toRow(key: String, value: Any): KvRow? = when (value) {
        is String -> KvRow(key, SqliteKeyValueStore.TYPE_STRING, value)
        is Int -> KvRow(key, SqliteKeyValueStore.TYPE_INT, value)
        is Long -> KvRow(key, SqliteKeyValueStore.TYPE_LONG, value)
        is Float -> KvRow(key, SqliteKeyValueStore.TYPE_FLOAT, value)
        is Boolean -> KvRow(key, SqliteKeyValueStore.TYPE_BOOL, value)
        is Set<*> -> KvRow(
            key,
            SqliteKeyValueStore.TYPE_STRING_SET,
            value.mapNotNull { it as? String }.toCollection(LinkedHashSet()),
        )
        else -> null
    }
}
