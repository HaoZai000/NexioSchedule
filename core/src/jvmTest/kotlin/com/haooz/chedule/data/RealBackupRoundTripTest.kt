package com.haooz.chedule.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **真实用户备份**的逐字 round-trip 回归。
 *
 * ## 为什么这是最强的一条安全网
 *
 * 前面几批（⑨ 的 Gson→JsonSupport、⑩ 的 org.json→JsonSupport）换的都是**序列化实现**，
 * 而它们的输出会落盘、会进备份文件。单测里的字面量是我按语义推的，
 * 这份是**真机跑出来的存量数据** —— 只要它逐字过，就说明格式没漂。
 *
 * 数据来源：`全部备份_20261009_155825.json`（166 个键的全量备份，prefs 级透传）。
 *
 * ## 覆盖的两条通道
 *
 * 1. **节假日条目**（`holiday_entries.entries_2026`，530 字节，4 条）
 *    —— 走 `load()` 读 → `toJson()` 写，要求与原串**逐字相同**。
 * 2. **调休改周规则**（`schedule_{名}_teaching_week_reorganizations`）
 *    —— 真实值是 `{"schema_version":1,"rules":[]}`，走 `decode` → `encode` 要求逐字相同。
 *
 * ⚠ 本文件不入库（用户长期要求）。下面第 3 个用例依赖本机存在那份备份文件，
 * 文件不在时**打印警告并跳过**（不静默通过）。
 */
class RealBackupRoundTripTest {

    private lateinit var store: InMemoryKeyValueStore

    @BeforeTest
    fun setUp() {
        store = InMemoryKeyValueStore()
        AppStorage.init { store }
    }

    /** 真机 `entries_2026` 的原始落盘串，逐字抄自 2026-10-09 的全量备份。 */
    private val realEntries2026 =
        """[{"date":"2026-09-20","endDate":"","name":"国庆节补班","type":1,"followDate":"2026-10-06","followWeek":6,"followWeekday":2,"custom":true},""" +
            """{"date":"2026-09-25","endDate":"2026-09-27","name":"中秋节","type":0,"followDate":"","followWeek":-1,"followWeekday":-1,"custom":false},""" +
            """{"date":"2026-10-01","endDate":"2026-10-07","name":"国庆节","type":0,"followDate":"","followWeek":-1,"followWeekday":-1,"custom":true},""" +
            """{"date":"2026-10-10","endDate":"","name":"国庆节补班","type":1,"followDate":"2026-10-07","followWeek":6,"followWeekday":3,"custom":true}]"""

    @Test
    fun `真实 entries_2026 读出来再写回去逐字不变`() {
        store = InMemoryKeyValueStore(mapOf("entries_2026" to realEntries2026))
        AppStorage.init { store }

        val loaded = HolidayManager.load(2026)
        assertEquals(4, loaded.size, "4 条真实条目一条都不能丢")

        // 逐条核对字段（含 custom / followDate / 旧的 followWeek 组合）
        assertEquals(
            HolidayEntry(
                date = "2026-09-20", endDate = "", name = "国庆节补班",
                type = HolidayEntry.TYPE_WORKSWAP, followDate = "2026-10-06",
                followWeek = 6, followWeekday = 2, custom = true,
            ),
            loaded[0],
        )
        assertEquals("2026-09-27", loaded[1].endDate)
        assertTrue(loaded[2].custom)
        assertTrue(!loaded[1].custom)

        // ★ 关键断言：写回去必须与真机串**逐字相同**
        assertTrue(HolidayManager.save(2026, loaded))
        assertEquals(realEntries2026, store.getString("entries_2026", ""))
    }

    @Test
    fun `真实调休改周串 decode 再 encode 逐字不变`() {
        val real = """{"schema_version":1,"rules":[]}"""
        val rules = TeachingWeekReorganization.decode(real, totalWeeks = 20)
        assertTrue(rules.isEmpty())
        assertEquals(real, TeachingWeekReorganization.encode(rules, totalWeeks = 20))
    }

    @Test
    fun `整份全量备份的节假日通道能解码并原样还原`() {
        val backupFile = File(BACKUP_PATH)
        if (!backupFile.exists()) {
            println("⚠ 跳过：本机没有 $BACKUP_PATH（这份用例依赖真实备份文件）")
            return
        }
        val root = jsonToPlainValue(parseJsonObject(backupFile.readText())) as Map<*, *>

        // 1) 解码整份备份里的节假日三件套
        val data = HolidayManager.decodeBackupData(root.mapKeys { it.key.toString() })
        assertEquals(setOf("entries_2026"), data.entries.keys)
        assertEquals(realEntries2026, data.entries["entries_2026"], "备份里的条目串应与我抄录的一致")
        assertEquals(HolidayEndCourseExclusion(false, 1, 1), data.exclusion)
        assertEquals(HolidayBeforeCourseExclusion(false, 1, 1), data.beforeExclusion)

        // 2) 还原进空 store，再导出，三件套必须逐字一致
        HolidayManager.restoreBackupData(data)
        val reExported = HolidayManager.exportBackupData()
        assertEquals(data.entries, reExported[HolidayManager.BACKUP_KEY])
        assertEquals(realEntries2026, store.getString("entries_2026", ""))
        // 再解一遍必须得到同一份数据（不比较 Map.toString：备份里 schema_version 是 Int，
        // 从 JSON 读回来是 Double，字符串形状不同但语义相同 —— backupInteger 两种都吃）
        assertEquals(data, HolidayManager.decodeBackupData(reExported))
        // 字段名逐字核对：这几个键名是备份兼容契约，改一个存量备份就读不进来
        assertEquals(
            setOf("schema_version", "enabled", "startSection", "endSection"),
            (reExported[HolidayManager.BACKUP_EXCLUSION_KEY] as Map<*, *>).keys,
        )

        // 3) 顺带确认：整份备份能被 jsonToPlainValue 完整还原成 Gson 那种 Map<String,Any>
        //    （R2 的 4 条通道都要经过这一步）
        assertTrue(root.size > 100, "全量备份应有一百多个键，实际 ${root.size}")
        assertTrue(root["current_schedule_id"] is String)
        assertTrue(root["current_week"] == null || root["current_week"] is Double)
    }

    /**
     * R2 的关键实验：**JsonSupport 能不能逐字重放 Gson 导出的整份备份**。
     *
     * 真实备份是 `GsonBuilder().setPrettyPrinting()` 的产物（2 空格缩进、`": "` 分隔）。
     * 如果这一步能过，说明「全量备份」这条通道换掉 Gson 之后**文件字节完全不变**；
     * 过不去，就说明得先解决格式差异再动 R2。
     */
    @Test
    fun `整份备份能被 JsonSupport 逐字重放（Gson pretty 格式）`() {
        val backupFile = File(BACKUP_PATH)
        if (!backupFile.exists()) {
            println("⚠ 跳过：本机没有 $BACKUP_PATH")
            return
        }
        val original = backupFile.readText()
        val element = parseJsonElement(original)
        val replay = prettyJson.encodeToString(JsonElement.serializer(), element)

        // 先报差异规模，失败时能一眼看出是「差一个换行」还是「整体格式不同」
        if (original != replay) {
            val a = original.lines()
            val b = replay.lines()
            val firstDiff = a.indices.firstOrNull { it >= b.size || a[it] != b[it] }
            println("原文 ${original.length} 字符 / ${a.size} 行；重放 ${replay.length} 字符 / ${b.size} 行")
            println("首个差异行 = $firstDiff")
            if (firstDiff != null) {
                println("原文: ${a.getOrNull(firstDiff)}")
                println("重放: ${b.getOrNull(firstDiff)}")
            }
        }
        assertEquals(original, replay, "JsonSupport 的 pretty 输出必须与 Gson 逐字一致")
    }

    private companion object {
        const val BACKUP_PATH = "C:/Users/43908/Downloads/全部备份_20261009_155825.json"

        val prettyJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }
}
