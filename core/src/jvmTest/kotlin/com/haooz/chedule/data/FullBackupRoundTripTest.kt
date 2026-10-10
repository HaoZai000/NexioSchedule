package com.haooz.chedule.data

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **全量备份的「导出 → 导入 → 导出」逐字回归** —— 文档「阶段 2.3 数据兼容红线」要求的那一条。
 *
 * ## 为什么以前做不了、现在能做了
 *
 * 这条测试需要 `CourseRepository.exportAllPreferences()` / `importAllPreferences()`，
 * 而它们原先在 `:app`（没有测试源集）。把 `CourseRepository` 下沉到 `:core` 之后，
 * 它就能用 `InMemoryKeyValueStore` 直接测了 —— **这是搬它的最大回报**。
 *
 * ## 断言
 *
 * 1. 把真实备份的内容灌进 prefs → `exportAllPreferences()` 必须**还原出同一份备份**
 * 2. `importAllPreferences(备份)` → 再 `exportAllPreferences()` 必须与第一次导出**完全一致**
 *    （即「二次导出 == 首次导出」）
 *
 * ## 比较方式：语义相等，不是字符串相等
 *
 * 按「⑪-a」的实测结论，JSON 字段顺序不是契约（release 构建的 R8 会重排字段），
 * 所以这里把两侧都归一成「数字统一 Double、Set 转 List」的 Map 再比。
 * 备份里的**原始串**（`schedule_{名}_courses` 等）是逐字透传的，不会被重新序列化 ——
 * 这一点由断言 1 直接覆盖。
 *
 * ⚠ 本文件不入库（用户长期要求）。依赖本机存在那份真实备份，不在时跳过并打印警告。
 */
class FullBackupRoundTripTest {

    private lateinit var store: InMemoryKeyValueStore

    /** 节假日数据在**另一个** prefs 文件里，测试必须照实分开，否则 exportAllPreferences 会多出 holiday 键。 */
    private val holidayStore = InMemoryKeyValueStore()

    @BeforeTest
    fun setUp() {
        store = InMemoryKeyValueStore()
        AppStorage.init { name ->
            if (name == "holiday_settings") holidayStore else store
        }
    }

    private fun loadBackup(): Map<String, Any?>? {
        val f = File(BACKUP_PATH)
        if (!f.exists()) return null
        return jsonToPlainValue(parseJsonObject(f.readText())) as? Map<String, Any?>
    }

    /** 把备份还原成「prefs 里的原始形态」灌进 store（holiday 三件套要拆回扁平键）。 */
    private fun seedStoreFrom(root: Map<String, Any?>) {
        val flat = LinkedHashMap<String, Any?>()
        root.forEach { (key, value) ->
            if (!key.startsWith("holiday_")) flat[key] = value
        }
        val holidayFlat = LinkedHashMap<String, Any?>()
        (root["holiday_entries"] as? Map<*, *>)?.forEach { (k, v) -> holidayFlat[k.toString()] = v }
        val endEx = root["holiday_end_course_exclusion"] as? Map<*, *>
        if (endEx != null) {
            holidayFlat["end_course_exclusion_enabled"] = endEx["enabled"]
            holidayFlat["end_course_exclusion_start_section"] = (endEx["startSection"] as? Number)?.toInt()
            holidayFlat["end_course_exclusion_end_section"] = (endEx["endSection"] as? Number)?.toInt()
        }
        val beforeEx = root["holiday_before_course_exclusion"] as? Map<*, *>
        if (beforeEx != null) {
            holidayFlat["before_course_exclusion_enabled"] = beforeEx["enabled"]
            holidayFlat["before_course_exclusion_start_section"] = (beforeEx["startSection"] as? Number)?.toInt()
            holidayFlat["before_course_exclusion_end_section"] = (beforeEx["endSection"] as? Number)?.toInt()
        }
        fun fill(target: InMemoryKeyValueStore, items: Map<String, Any?>) = target.edit {
            items.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Boolean -> putBoolean(k, v)
                    // 时间戳是毫秒级 Long，硬塞 Int 会截断（2147483647）——按范围选类型
                    is Number -> {
                        val d = v.toDouble()
                        if (d == d.toInt().toDouble()) putInt(k, d.toInt()) else putLong(k, d.toLong())
                    }
                    else -> Unit
                }
            }
        }
        fill(store, flat)
        fill(holidayStore, holidayFlat)
    }

    /** 数字统一成 Double、Set 转 List、嵌套 Map/List 递归 —— 消除类型与顺序噪音。 */
    private fun normalize(value: Any?): Any? = when (value) {
        null -> null
        is Map<*, *> -> value.entries.associate { it.key.toString() to normalize(it.value) }
        is Set<*> -> value.map { normalize(it) }.sortedBy { it.toString() }
        is List<*> -> value.map { normalize(it) }
        is Number -> value.toDouble()
        else -> value
    }

    private fun assertSameBackup(expected: Map<String, Any?>, actual: Map<String, Any?>, tag: String) {
        val e = normalize(expected) as Map<*, *>
        val a = normalize(actual) as Map<*, *>
        val onlyExpected = e.keys - a.keys
        val onlyActual = a.keys - e.keys
        assertEquals(
            e.keys,
            a.keys,
            "$tag：键集合必须一致；只在备份里=$onlyExpected；只在导出里=$onlyActual",
        )
        e.keys.forEach { k ->
            assertEquals(e[k], a[k], "$tag：键 $k 的值不一致")
        }
    }

    @Test
    fun `导出能还原真实备份`() {
        val root = loadBackup() ?: run { println("⚠ 跳过：本机没有 $BACKUP_PATH"); return }
        seedStoreFrom(root)

        val exported = CourseRepository.getInstance().exportAllPreferences()
        assertSameBackup(root, exported, "首次导出")
        // 课程串必须是**逐字透传**（没有被重新序列化）
        assertEquals(root["schedule_数字经济_courses"], exported["schedule_数字经济_courses"])
        assertEquals(root["schedule_数字经济3_courses"], exported["schedule_数字经济3_courses"])
    }

    @Test
    fun `导出导入导出 二次导出与首次完全一致`() {
        val root = loadBackup() ?: run { println("⚠ 跳过：本机没有 $BACKUP_PATH"); return }
        seedStoreFrom(root)
        val repository = CourseRepository.getInstance()

        val first = repository.exportAllPreferences()
        repository.importAllPreferences(first)
        val second = repository.exportAllPreferences()

        assertSameBackup(first, second, "二次导出")
        // 再走一轮，确认是稳定的（不是「恰好第二轮碰对」）
        repository.importAllPreferences(second)
        assertSameBackup(first, repository.exportAllPreferences(), "三次导出")
    }

    @Test
    fun `导入真实备份后课程与时间配置都读得出来`() {
        val root = loadBackup() ?: run { println("⚠ 跳过：本机没有 $BACKUP_PATH"); return }
        seedStoreFrom(root)
        val repository = CourseRepository.getInstance()
        repository.importAllPreferences(repository.exportAllPreferences())

        val names = repository.getScheduleNames()
        assertTrue(names.isNotEmpty(), "课表名不能丢")
        assertEquals(listOf("数字经济3", "测试", "噩梦测试", "数字经济", "WakeUp导入课表"), names)

        val courses = repository.getCoursesForSchedule("数字经济3")
        assertEquals(23, courses.size, "23 门课一门都不能少")
        assertTrue(courses.all { it.name.isNotBlank() && it.id.isNotBlank() })

        val ids = repository.getTimeConfigIds()
        assertEquals(listOf(1L, 4L, 8L), ids, "time_config_ids 必须还原（逗号分隔格式）")
        val config = repository.getTimeConfig(1L)
        assertTrue(config.routines.isNotEmpty(), "作息方案不能丢")
        assertEquals(45, config.classDuration)
    }

    private companion object {
        const val BACKUP_PATH = "C:/Users/43908/Downloads/全部备份_20261009_155825.json"
    }
}
