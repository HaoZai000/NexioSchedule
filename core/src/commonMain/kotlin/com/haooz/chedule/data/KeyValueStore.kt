package com.haooz.chedule.data

/**
 * 跨平台键值存储。
 *
 * 现状：28 个文件、约 700 处 `SharedPreferences` 调用，横跨 17 个偏好文件，
 * 且承载全部用户数据（课程表 / 时间配置 / 节假日 / 备份配置 / 隐私同意）。
 *
 * ## 为什么是接口而不是 expect/actual 类
 *
 * `SharedPreferences` 在 `Holidays` 等处**作为参数类型出现**（6 个 `internal fun`
 * 形如 `loadEndCourseExclusion(preferences: SharedPreferences)`）。若走 expect class，
 * actual 就是 SharedPreferences 本身，`:app` 侧的既有签名不必改 —— 但那样 commonMain
 * 无法表达"写入"能力（KTX 的 `edit {}` 依赖 Editor）。
 *
 * 选接口的代价是 `:app` 侧需要一层包装；换来的是：
 * - commonTest 可以用 [InMemoryKeyValueStore] 做 round-trip 回归，**不需要 Android 设备**
 * - iOS 侧接 NSUserDefaults 时只需再写一个实现，调用点零改动
 *
 * ## 与 SharedPreferences 的语义差异（务必注意）
 *
 * 1. **写入分两段**：Android 是 `edit {}` DSL（提交即 apply），这里是 [KeyValueEditor]。
 *    不要在 `:app` 里混用两套 —— 一个文件内统一用一种。
 * 2. **类型不匹配时不抛异常**：SharedPreferences 对类型不符的 key 会抛
 *    `ClassCastException`，项目里 `Holidays` 大量用 `runCatching { … }.getOrDefault(…)`
 *    来兜底（历史数据格式变更导致）。本接口约定**返回默认值而非抛异常**，
 *    这样那些 runCatching 可以逐步简化；但**现有 runCatching 先保留**，不要顺手删。
 * 3. **[all] 的返回类型**：Android 是 `Map<String, *>`（只读快照）。本接口返回
 *    `Map<String, Any?>` 的拷贝，**改动它不影响存储**。
 *
 * ⚠ **红线**：Android 实现必须继续走 SharedPreferences 本身，不得换成 DataStore 或
 * 自建文件 —— 否则存量用户的全部设置会读不出来。
 */
interface KeyValueStore {
    fun getString(key: String, defaultValue: String): String
    fun getInt(key: String, defaultValue: Int): Int
    fun getLong(key: String, defaultValue: Long): Long
    fun getFloat(key: String, defaultValue: Float): Float
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    /** Android 的 `getStringSet` 返回的集合是**内部实例的引用**，就地修改会抛异常。
     *  本接口约定返回**副本**，可就地修改。 */
    fun getStringSet(key: String, defaultValue: Set<String>): Set<String>

    fun contains(key: String): Boolean

    /** 全量快照（拷贝）。备份 / 迁移功能依赖它。 */
    fun all(): Map<String, Any?>

    /** 开启一次事务写入。回调内可混合调用任意 put / remove / clear。 */
    fun edit(block: KeyValueEditor.() -> Unit)

    fun remove(key: String)

    fun clear()

    /** 变更监听。iOS 无等价能力，实现可返回 null。 */
    fun registerListener(listener: (String) -> Unit): Any?

    fun unregisterListener(token: Any?)
}

/**
 * 对应 `SharedPreferences.getString(key, null)`：**键不存在返回 null**。
 *
 * 接口的 [KeyValueStore.getString] 刻意收非空默认值（契约里没有可空重载），
 * 而迁移前有 12 处 `getString(key, null) ?: …` 的写法，所以补这个扩展。
 *
 * ⚠ 语义逐条对齐：键存在但**类型不是 String** 时，Android 实现会原样抛
 * `ClassCastException`（见 `SharedPreferencesStore`），这里不会把它吞成 null。
 */
fun KeyValueStore.getStringOrNull(key: String): String? =
    if (contains(key)) getString(key, "") else null

/** 事务内可用的写入面。用完 [KeyValueStore.edit] 的回调即提交（等价 Android 的 apply）。 */
interface KeyValueEditor {
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putFloat(key: String, value: Float)
    fun putBoolean(key: String, value: Boolean)
    fun putStringSet(key: String, value: Set<String>)
    fun remove(key: String)
    fun clear()
}

/**
 * 内存实现，供 commonTest 做 round-trip 回归，也作为未来非持久化场景的默认值。
 *
 * 刻意**不做线程安全优化**：SharedPreferences 的跨线程一致性由 Android 保证，
 * 而 `Holidays` / `CourseRepository` 等调用点普遍带 `@Synchronized`，
 * 测试实现只需行为等价即可。
 */
class InMemoryKeyValueStore(
    initial: Map<String, Any?> = emptyMap(),
) : KeyValueStore {
    private val data = LinkedHashMap<String, Any?>(initial)

    override fun getString(key: String, defaultValue: String): String =
        data[key] as? String ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int = when (val v = data[key]) {
        is Int -> v
        is Long -> v.toInt()
        else -> defaultValue
    }

    override fun getLong(key: String, defaultValue: Long): Long = when (val v = data[key]) {
        is Long -> v
        is Int -> v.toLong()
        else -> defaultValue
    }

    override fun getFloat(key: String, defaultValue: Float): Float =
        (data[key] as? Number)?.toFloat() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        data[key] as? Boolean ?: defaultValue

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        val stored = data[key] as? Set<String> ?: return defaultValue
        // 必须用 LinkedHashSet：kotlin.collections.toSet() 对 size==1 的集合会返回
        // 共享的不可变单例（Collections.singleton），调用方就地 add 会抛
        // UnsupportedOperationException —— 这正是 Android 上那个经典陷阱。
        return LinkedHashSet(stored)
    }

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun all(): Map<String, Any?> = LinkedHashMap(data)

    override fun edit(block: KeyValueEditor.() -> Unit) {
        InMemoryEditor().block()
    }

    override fun remove(key: String) {
        data.remove(key)
    }

    override fun clear() {
        data.clear()
    }

    /** 内存实现不支持跨实例监听。 */
    override fun registerListener(listener: (String) -> Unit): Any? = null

    override fun unregisterListener(token: Any?) = Unit

    private inner class InMemoryEditor : KeyValueEditor {
        override fun putString(key: String, value: String) { data[key] = value }
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun putLong(key: String, value: Long) { data[key] = value }
        override fun putFloat(key: String, value: Float) { data[key] = value }
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun putStringSet(key: String, value: Set<String>) { data[key] = LinkedHashSet(value) }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }
}
