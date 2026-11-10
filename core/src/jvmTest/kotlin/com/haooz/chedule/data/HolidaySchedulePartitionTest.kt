package com.haooz.chedule.data

import com.haooz.chedule.data.backup.BackupPayload
import com.haooz.chedule.data.backup.encodeBackupJson
import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.backup.parseBackupPayload
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.toJson
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseJsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray

/** 节假日按课表分区的存储 / 备份 / 课表联动回归。 */
class HolidaySchedulePartitionTest {

    private val namespaces = mutableMapOf<String, InMemoryKeyValueStore>()

    @BeforeTest
    fun setUp() {
        namespaces.clear()
        AppStorage.init { name -> namespaces.getOrPut(name) { InMemoryKeyValueStore() } }
    }

    /** 与生产代码同算法的十六进制编码：命名空间不能含路径分隔符。 */
    private fun encodeId(id: String): String = buildString {
        for (byte in id.encodeToByteArray()) {
            val v = byte.toInt() and 0xFF
            append("0123456789abcdef"[v ushr 4])
            append("0123456789abcdef"[v and 0x0F])
        }
    }

    private fun partition(id: String) = AppStorage.store("holiday_settings_" + encodeId(id))

    private fun seed(id: String, year: Int, entries: List<HolidayEntry>) {
        val raw = JsonArray(entries.map { it.toJson() }).toString()
        partition(id).edit { putString("entries_$year", raw) }
    }

    private fun holiday(name: String, date: String) = HolidayEntry(
        date = date,
        name = name,
        type = HolidayEntry.TYPE_HOLIDAY,
    )

    @Test
    fun `命名空间编码不含路径分隔符`() {
        val dangerous = listOf("数字经济3", "a/b", "c:d", "e f", "默认课表")
        dangerous.forEach { id ->
            val namespace = "holiday_settings_" + encodeId(id)
            assertTrue(
                '/' !in namespace && ':' !in namespace && ' ' !in namespace,
                "命名空间含非法字符：$namespace",
            )
        }
        // 编码可逆且不同课表名不撞
        val encoded = dangerous.map { encodeId(it) }
        assertEquals(encoded.size, encoded.distinct().size)
    }

    @Test
    fun `每张课表只看到自己的节假日`() {
        seed("表A", 2026, listOf(holiday("国庆", "2026-10-01")))
        seed("表B", 2026, listOf(holiday("元旦", "2026-01-01")))

        assertEquals(listOf("国庆"), HolidayManager.loadFor(2026, "表A").map { it.name })
        assertEquals(listOf("元旦"), HolidayManager.loadFor(2026, "表B").map { it.name })
        assertTrue(HolidayManager.loadFor(2026, "表C").isEmpty())
        assertEquals(setOf(2026), HolidayManager.loadAllByYearFor("表A").keys)
        assertEquals(1, HolidayManager.loadAllByYearFor("表B")[2026]?.size)
    }

    @Test
    fun `老全局数据迁移到当前课表后从全局命名空间移除`() {
        val global = AppStorage.store("holiday_settings")
        global.edit {
            putString("entries_2026", JsonArray(listOf(holiday("国庆", "2026-10-01").toJson())).toString())
        }
        val current = CourseRepository.getInstance().getCurrentScheduleId()

        HolidayManager.migrateToSchedulePartitionsOnce()

        assertEquals(listOf("国庆"), HolidayManager.loadFor(2026, current).map { it.name })
        assertTrue(!global.contains("entries_2026"))
        assertTrue(global.getBoolean("schedule_partition_migrated_v1", false))
    }

    @Test
    fun `全量备份按课表导出并能还原回各自分区`() {
        seed("表A", 2026, listOf(holiday("国庆", "2026-10-01")))
        seed("表B", 2026, listOf(holiday("元旦", "2026-01-01")))

        val exported = HolidayManager.exportAllSchedulesBackupData(listOf("表A", "表B"))
        val bySchedule = exported[HolidayManager.BACKUP_BY_SCHEDULE_KEY] as Map<*, *>
        assertEquals(setOf("表A", "表B"), bySchedule.keys)

        val decoded = HolidayManager.decodeAllSchedulesBackupData(exported)
        assertEquals(2, decoded.size)

        HolidayManager.restoreAllSchedulesBackupData(decoded, listOf("表A", "表B", "表C"))
        assertEquals(listOf("国庆"), HolidayManager.loadFor(2026, "表A").map { it.name })
        assertEquals(listOf("元旦"), HolidayManager.loadFor(2026, "表B").map { it.name })
        assertTrue(HolidayManager.loadFor(2026, "表C").isEmpty())
    }

    @Test
    fun `老全量备份的单份数据归给指定课表`() {
        val raw = JsonArray(listOf(holiday("国庆", "2026-10-01").toJson())).toString()
        val legacy = mapOf<String, Any?>(
            HolidayManager.BACKUP_KEY to mapOf("entries_2026" to raw),
        )

        val decoded = HolidayManager.decodeAllSchedulesBackupData(legacy, "表A")

        assertEquals(setOf("表A"), decoded.keys)
        assertEquals(raw, decoded.getValue("表A").entries["entries_2026"])
    }

    @Test
    fun `课表改名复制删除跟着搬分区`() {
        seed("表A", 2026, listOf(holiday("国庆", "2026-10-01")))

        HolidayManager.onScheduleCopied("表A", "表B")
        assertEquals(listOf("国庆"), HolidayManager.loadFor(2026, "表B").map { it.name })

        HolidayManager.onScheduleRenamed("表A", "表C")
        assertTrue(HolidayManager.loadFor(2026, "表A").isEmpty())
        assertEquals(listOf("国庆"), HolidayManager.loadFor(2026, "表C").map { it.name })

        HolidayManager.onScheduleDeleted("表C")
        assertTrue(HolidayManager.loadFor(2026, "表C").isEmpty())
        assertEquals(listOf("国庆"), HolidayManager.loadFor(2026, "表B").map { it.name })
    }

    @Test
    fun `全量备份经 JSON 往返仍按课表分区`() {
        seed("表A", 2026, listOf(holiday("国庆", "2026-10-01")))
        seed("表B", 2026, listOf(holiday("元旦", "2026-01-01")))
        val exported = HolidayManager.exportAllSchedulesBackupData(listOf("表A", "表B"))

        val parsed = jsonToPlainValue(parseJsonObject(encodeBackupJson(exported))) as Map<String, Any>
        val decoded = HolidayManager.decodeAllSchedulesBackupData(parsed)

        assertEquals(setOf("表A", "表B"), decoded.keys)
        assertTrue(decoded.getValue("表A").entries.getValue("entries_2026").contains("国庆"))
        assertTrue(decoded.getValue("表B").entries.getValue("entries_2026").contains("元旦"))
    }

    @Test
    fun `单课表导出只带该课表配置且能写回另一张表`() {
        seed("表A", 2026, listOf(holiday("国庆", "2026-10-01")))

        val exported = HolidayManager.exportBackupDataFor("表A")
        val decoded = HolidayManager.decodeBackupData(exported)
        HolidayManager.restoreBackupDataFor("表B", decoded)

        assertEquals(listOf("国庆"), HolidayManager.loadFor(2026, "表B").map { it.name })
    }
}
