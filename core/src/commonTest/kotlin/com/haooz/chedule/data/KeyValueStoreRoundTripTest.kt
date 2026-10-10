package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KeyValueStore 的 round-trip 回归。
 *
 * 迁移 `SharedPreferences` 的最大风险不是编译失败，而是**类型或默认值悄悄变了**，
 * 表现为「用户设置静默丢失」—— 而且往往要等到用户反馈才发现。
 *
 * 这里用 [InMemoryKeyValueStore] 锁定语义：任何实现（Android 的 SharedPreferences
 * 包装、iOS 的 NSUserDefaults）都必须满足同一组断言。
 */
class KeyValueStoreRoundTripTest {

    private fun store() = InMemoryKeyValueStore()

    @Test
    fun `写入后能原样读出各类型`() {
        val s = store()
        s.edit {
            putString("name", "计算机学院")
            putInt("week", 20)
            putLong("version", 1_700_000_000_000L)
            putFloat("scale", 1.5f)
            putBoolean("enabled", true)
            putStringSet("days", setOf("周一", "周三"))
        }

        assertEquals("计算机学院", s.getString("name", ""))
        assertEquals(20, s.getInt("week", 0))
        assertEquals(1_700_000_000_000L, s.getLong("version", 0L))
        assertEquals(1.5f, s.getFloat("scale", 0f))
        assertTrue(s.getBoolean("enabled", false))
        assertEquals(setOf("周一", "周三"), s.getStringSet("days", emptySet()))
    }

    /** 项目里大量用 `runCatching { … }.getOrDefault(…)` 兜底历史脏数据，
     * 约定是「类型不符时返回默认值」而非抛异常。 */
    @Test
    fun `类型不匹配时返回默认值而非抛异常`() {
        val s = InMemoryKeyValueStore(mapOf("str" to "我是字符串", "num" to 123))
        // 存的是 String，按 String 读当然要拿到 —— 不匹配指的是跨类型读取
        assertEquals("我是字符串", s.getString("str", "默认"))
        assertEquals(-1, s.getInt("str", -1))
        assertEquals(-1L, s.getLong("str", -1L))
        assertFalse(s.getBoolean("str", false))
        assertEquals("默认", s.getString("num", "默认"))
        assertFalse(s.getBoolean("num", false))
    }

    @Test
    fun `不存在的键返回调用方给的默认值`() {
        val s = store()
        assertEquals("x", s.getString("缺失", "x"))
        assertEquals(7, s.getInt("缺失", 7))
        assertEquals(7L, s.getLong("缺失", 7L))
        assertEquals(7f, s.getFloat("缺失", 7f))
        assertFalse(s.getBoolean("缺失", false))
        assertTrue(s.getStringSet("缺失", setOf("d")).contains("d"))
    }

    /** 数字窄化：历史数据里同一个键可能存成 Long，按 Int 读回来时不能崩。 */
    @Test
    fun `数字类型窄化与放大都安全`() {
        val s = store()
        s.edit { putLong("n", 42L) }
        assertEquals(42, s.getInt("n", -1))
        s.edit { putInt("n", 42) }
        assertEquals(42L, s.getLong("n", -1L))
    }

    /** getStringSet 必须返回副本 —— Android 上就地修改会抛
     * `UnsupportedOperationException`，这是经典陷阱。 */
    @Test
    fun `getStringSet 返回副本可就地修改`() {
        val s = store()
        s.edit { putStringSet("days", setOf("周一")) }

        val got = s.getStringSet("days", emptySet())
        (got as MutableSet).add("周三")

        assertEquals(setOf("周一"), s.getStringSet("days", emptySet()), "返回值必须是副本，不能反写存储")
    }

    @Test
    fun `all 返回快照改动不影响存储`() {
        val s = store()
        s.edit { putInt("a", 1) }

        val snapshot = s.all()
        (snapshot as MutableMap)["a"] = 999

        assertEquals(1, s.getInt("a", 0), "all() 必须返回拷贝")
    }

    /** 备份/恢复依赖 all() 的完整性 —— 每个类型都要在。 */
    @Test
    fun `all 覆盖全部已写入的类型`() {
        val s = store()
        s.edit {
            putString("s", "v")
            putInt("i", 1)
            putLong("l", 2L)
            putFloat("f", 3f)
            putBoolean("b", true)
            putStringSet("set", setOf("a"))
        }

        val all = s.all()
        assertEquals("v", all["s"])
        assertEquals(1, all["i"])
        assertEquals(2L, all["l"])
        assertEquals(3f, all["f"])
        assertEquals(true, all["b"])
        assertEquals(setOf("a"), all["set"])
    }

    @Test
    fun `remove 与 clear 生效`() {
        val s = store()
        s.edit { putString("a", "1"); putString("b", "2") }

        s.remove("a")
        assertFalse(s.contains("a"))
        assertTrue(s.contains("b"))

        s.clear()
        assertTrue(s.all().isEmpty())
    }

    @Test
    fun `edit 内 remove 与 clear 可与其他写入混用`() {
        val s = store()
        s.edit {
            putString("keep", "v")
            putString("drop", "v")
            remove("drop")
        }
        assertEquals("v", s.getString("keep", ""))
        assertFalse(s.contains("drop"))

        s.edit {
            putString("x", "1")
            clear()
        }
        assertTrue(s.all().isEmpty())
    }

    @Test
    fun `覆盖写不产生重复键`() {
        val s = store()
        s.edit { putInt("k", 1) }
        s.edit { putInt("k", 2) }
        assertEquals(2, s.getInt("k", -1))
        assertEquals(1, s.all().size)
    }

    /** 跨实例构造：用于模拟「备份 → 清空 → 恢复」。 */
    @Test
    fun `导出再导入结果一致`() {
        val source = store()
        source.edit {
            putString("course", "数据结构")
            putInt("sectionCount", 14)
            putBoolean("remindEnabled", true)
            putStringSet("weekdays", setOf("1", "3", "5"))
        }

        // 备份：拿到快照后清空原库
        val backup = LinkedHashMap(source.all())
        source.clear()
        assertTrue(source.all().isEmpty())

        // 恢复
        val restored = store()
        restored.edit {
            backup.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Float -> putFloat(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Set<*> -> putStringSet(k, v.map { it.toString() }.toSet())
                    else -> Unit
                }
            }
        }

        // 二次导出必须与首次完全一致
        assertEquals(backup, restored.all(), "round-trip 后二次导出必须与首次一致")
    }
}
