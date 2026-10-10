package com.haooz.chedule.data

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/**
 * [KeyValueStore] 的 iOS 实现：按名字开一个独立的 NSUserDefaults 域。
 *
 * ## 为什么是「按 suiteName 开域」而不是 `standardUserDefaults`
 *
 * Android 侧是 `getSharedPreferences(name, MODE_PRIVATE)` —— **每个名字一个独立 XML 文件**，
 * 项目有 17 个不同名字。iOS 侧语义等价物是 `NSUserDefaults(suiteName:)`
 * （`multiplatform-settings` 在 Apple 平台也是这个做法），而不是单例的 `standardUserDefaults`。
 * 若所有名字都映射到 standardUserDefaults，17 个文件的键会挤进同一个域 ——
 * 一旦两个文件有同名键（本项目 `island_countdown_state_test` 之类就是这么来的），
 * 数据会互相覆盖。
 *
 * ## ⚠ 为什么要额外记「类型标记」（本类最不直观的一处）
 *
 * [all] 的返回值被 `CourseRepository` **按具体类型匹配**后用掉两处：
 * - 复制课表（`when (value) { is Int -> putInt … is Long -> putLong … }`）
 * - 全量备份 `exportAllPreferences()`
 *
 * 而 plist 里所有数值都是 `NSNumber`，**读出来无法区分原本是 Int 还是 Long**
 * （64 位上 NSInteger 就是 Long）。如果统一按 Long 还原，后果是：
 * 复制课表会把 Int 键写成 Long → 之后 `getInt` 拿到 Long → Android 侧抛
 * `ClassCastException` → 被既有 `runCatching` 兜底成默认值 → **用户设置静默丢失**。
 * iOS 内部因为走 NSNumber 宽松转换不会崩，但一旦备份文件跨平台往返就会炸。
 *
 * 所以写入时把类型记到一个**独立域** `suiteName.__kvtypes`。
 * 必须独立：`all()` 会被遍历，标记键若和业务键同域，会被当成业务数据复制/导出。
 *
 * ## 与 Android 实现的行为差异（有意为之）
 *
 * Android 对「键存在但类型不符」抛 `ClassCastException`（历史数据格式变更多次，
 * 调用点靠 `runCatching` 兜底）。iOS **没有存量数据**，不需要复刻这个行为，
 * 按 [KeyValueStore] 主契约返回默认值。
 *
 * ⚠ **Windows 上无法编译本文件**（iOS target 需要 Xcode SDK）。
 * 首次 `compileKotlinIosArm64` 必须在 macOS 上做，重点验证：
 *   1. Kotlin 的 Int/Long/Float/Boolean 传给 `setObject(_:forKey:)` 是否自动装箱成 NSNumber
 *   2. `persistentDomainForName` 对自定义 suite 是否返回非空
 *   3. `raw is String` 对 plist 里的 NSString 是否成立
 */
class UserDefaultsStore(
    private val suiteName: String,
) : KeyValueStore {

    private val defaults = NSUserDefaults(suiteName = suiteName)

    /** 类型标记域。见类注释：绝不和业务键同域。 */
    private val typeDomain = NSUserDefaults(suiteName = "$suiteName.__kvtypes")

    override fun getString(key: String, defaultValue: String): String =
        defaults.stringForKey(key) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int =
        number(key)?.intValue ?: defaultValue

    override fun getLong(key: String, defaultValue: Long): Long =
        number(key)?.longValue ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float =
        number(key)?.floatValue ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        number(key)?.boolValue ?: defaultValue

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        val list = defaults.arrayForKey(key) ?: return defaultValue
        return LinkedHashSet(list.mapNotNull { it as? String })
    }

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun all(): Map<String, Any?> {
        val domain = defaults.persistentDomainForName(suiteName) ?: return emptyMap()
        val result = LinkedHashMap<String, Any?>(domain.size)
        for ((rawKey, rawValue) in domain) {
            result[rawKey.toString()] = restore(rawKey.toString(), rawValue)
        }
        return result
    }

    override fun edit(block: KeyValueEditor.() -> Unit) {
        UserDefaultsEditor().block()
        // 对应 Android 的 apply()：提示系统落盘。iOS 本身会在合适时机持久化，
        // 这里显式调用一次让「提交」语义与 Android 对齐；返回 false 表示落盘失败，
        // 与 apply() 一样不向调用方抛（调用方无能为力）。
        defaults.synchronize()
        typeDomain.synchronize()
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
        typeDomain.removeObjectForKey(key)
    }

    override fun clear() {
        defaults.removePersistentDomainForName(suiteName)
        typeDomain.removePersistentDomainForName("$suiteName.__kvtypes")
    }

    /**
     * iOS 无「带 key 的变更回调」：`NSUserDefaultsDidChangeNotification` 只通知「变了」，
     * 不带是哪个键。契约允许返回 null，且目前 `:app` / `:core` 都没有调用点。
     */
    override fun registerListener(listener: (String) -> Unit): Any? = null

    override fun unregisterListener(token: Any?) = Unit

    /** 按调用方期望的类型读 NSNumber —— 这是单键读取不需要类型标记的原因。 */
    private fun number(key: String): NSNumber? = defaults.objectForKey(key) as? NSNumber

    /** 把 plist 里的原始值还原成 Kotlin 类型，供 [all] 使用。 */
    private fun restore(key: String, raw: Any?): Any? = when {
        raw == null -> null
        // NSString 在 Kotlin/Native 里直接映射为 kotlin.String
        raw is String -> raw
        // 目前唯一的集合类型是 getStringSet 写的字符串数组
        raw is List<*> -> LinkedHashSet(raw.mapNotNull { it as? String })
        raw is NSNumber -> when (typeDomain.stringForKey(key)) {
            TYPE_INT -> raw.intValue
            TYPE_LONG -> raw.longValue
            TYPE_FLOAT -> raw.floatValue
            TYPE_BOOLEAN -> raw.boolValue
            // 无标记（例如从别的途径写进来的值）：按 Long 兜底，至少不丢量级
            else -> raw.longValue
        }
        else -> raw
    }

    private inner class UserDefaultsEditor : KeyValueEditor {
        override fun putString(key: String, value: String) {
            defaults.setObject(value, forKey = key)
            typeDomain.removeObjectForKey(key)
        }

        override fun putInt(key: String, value: Int) {
            defaults.setObject(value, forKey = key)
            typeDomain.setObject(TYPE_INT, forKey = key)
        }

        override fun putLong(key: String, value: Long) {
            defaults.setObject(value, forKey = key)
            typeDomain.setObject(TYPE_LONG, forKey = key)
        }

        override fun putFloat(key: String, value: Float) {
            defaults.setObject(value, forKey = key)
            typeDomain.setObject(TYPE_FLOAT, forKey = key)
        }

        override fun putBoolean(key: String, value: Boolean) {
            defaults.setObject(value, forKey = key)
            typeDomain.setObject(TYPE_BOOLEAN, forKey = key)
        }

        override fun putStringSet(key: String, value: Set<String>) {
            defaults.setObject(value.toList(), forKey = key)
            typeDomain.removeObjectForKey(key)
        }

        override fun remove(key: String) = this@UserDefaultsStore.remove(key)

        override fun clear() = this@UserDefaultsStore.clear()
    }

    private companion object {
        const val TYPE_INT = "i"
        const val TYPE_LONG = "l"
        const val TYPE_FLOAT = "f"
        const val TYPE_BOOLEAN = "b"
    }
}
