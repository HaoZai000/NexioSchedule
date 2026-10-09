package com.haooz.chedule.data

import android.content.SharedPreferences

/**
 * Android 实现：**原样包装 SharedPreferences**，不换存储引擎。
 *
 * ⚠ **红线**：绝不能改成 DataStore / 自建文件 / Room。存量用户的课程表、时间配置、
 * 节假日设置、备份配置全部存在 SharedPreferences 里，换引擎等于全部读不出来。
 *
 * 语义对齐说明：
 * - 读取类型不符时，SharedPreferences 会抛 `ClassCastException`；这里原样抛出，
 *   不吞异常 —— 现有调用点普遍用 `runCatching { … }.getOrDefault(…)` 兜底（历史数据
 *   格式变更多次），保持这个行为才能让那些兜底继续有效。
 * - `edit` 用 KTX 的 `edit {}`（提交即 apply），与迁移前调用点的写法完全一致。
 * - `getStringSet` 返回**副本**：SharedPreferences 返回的是内部实例引用，
 *   就地修改会抛 UnsupportedOperationException。返回副本后调用方的 `add` 行为
 *   与迁移前不同（迁移前会崩），属修复而非回归。
 */
class SharedPreferencesStore(
    private val prefs: SharedPreferences,
) : KeyValueStore {

    override fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int =
        prefs.getInt(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long =
        prefs.getLong(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        prefs.getFloat(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        prefs.getStringSet(key, defaultValue)?.let { LinkedHashSet(it) } ?: defaultValue

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun all(): Map<String, Any?> = LinkedHashMap(prefs.all)

    override fun edit(block: KeyValueEditor.() -> Unit) {
        val editor = prefs.edit()
        EditorAdapter(editor).block()
        editor.apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun registerListener(listener: (String) -> Unit): Any? =
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            // 平台回调的 key 声明为 String?（KeyValueStore 契约是 String），
            // 空值在 SharedPreferences 里不会出现，直接跳过即可。
            if (key != null) listener(key)
        }

    override fun unregisterListener(token: Any?) {
        if (token is SharedPreferences.OnSharedPreferenceChangeListener) {
            prefs.unregisterOnSharedPreferenceChangeListener(token)
        }
    }

    private class EditorAdapter(
        private val editor: SharedPreferences.Editor,
    ) : KeyValueEditor {
        override fun putString(key: String, value: String) { editor.putString(key, value) }
        override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
        override fun putLong(key: String, value: Long) { editor.putLong(key, value) }
        override fun putFloat(key: String, value: Float) { editor.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
        override fun putStringSet(key: String, value: Set<String>) {
            editor.putStringSet(key, LinkedHashSet(value))
        }
        override fun remove(key: String) { editor.remove(key) }
        override fun clear() { editor.clear() }
    }
}

/**
 * 便捷构造：把既有的 `getSharedPreferences(name, MODE_PRIVATE)` 包一层。
 *
 * 迁移期刻意**保留底层 SharedPreferences 的可见入口**，让还没迁移的调用点
 * 继续按原样工作 —— 两套写法在同一个 App 里共存，不会互相影响。
 */
fun SharedPreferences.asKeyValueStore(): KeyValueStore = SharedPreferencesStore(this)