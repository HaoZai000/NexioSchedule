package com.haooz.chedule.data

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `HolidayManager` 的**存量数据兼容回归**。
 *
 * ## 为什么必须有这份测试
 *
 * `HolidayManager` 从 `:app` 下沉 `:core` 时换掉了三样东西，每一样都能静默毁掉用户数据：
 *
 * | 换掉 | 风险 |
 * |---|---|
 * | `SharedPreferences` → `KeyValueStore` | 偏好文件名 / 键名写错 → 用户数据读不出来 |
 * | `org.json` → `JsonSupport` | `opt(key) as? X` 的类型判断语义变了 → 合法数据被判为非法，**整行被丢弃** |
 * | Gson → `JsonSupport` | 备份里的条目行解析语义变了 → 导入备份丢数据 |
 *
 * 这里用**真实的存量串**（迁移前 org.json 写出来的那种）做 round-trip：
 * 读得进来 + 原样写得回去。
 *
 * ⚠ 本文件不入库（用户长期要求）。
 */
class HolidayManagerStorageTest {

    private lateinit var store: InMemoryKeyValueStore

    private fun freshStore(initial: Map<String, Any?> = emptyMap()) {
        store = InMemoryKeyValueStore(initial)
        AppStorage.init { store }
    }

    @BeforeTest
    fun setUp() {
        freshStore()
    }

    // ── 落盘格式 ────────────────────────────────

    /**
     * 基准字面量 = 迁移前 org.json 的产出形状：
     * 紧凑无空格、字段按插入顺序、数字不带小数点、空串就是 `""`。
     */
    private val holidayRow2026 =
        """{"date":"2026-10-01","endDate":"2026-10-08","name":"国庆节","type":0,""" +
            """"followDate":"","followWeek":-1,"followWeekday":-1,"custom":false}"""

    private val workSwapRow2026 =
        """{"date":"2026-09-20","endDate":"","name":"国庆节调休","type":1,""" +
            """"followDate":"2026-09-30","followWeek":-1,"followWeekday":-1,"custom":true}"""

    @Test
    fun `toJson 输出与 org_json 逐字一致`() {
        assertEquals(
            holidayRow2026,
            HolidayEntry(
                date = "2026-10-01",
                endDate = "2026-10-08",
                name = "国庆节",
                type = HolidayEntry.TYPE_HOLIDAY,
            ).toJson().toString(),
        )
        assertEquals(
            workSwapRow2026,
            HolidayEntry(
                date = "2026-09-20",
                name = "国庆节调休",
                type = HolidayEntry.TYPE_WORKSWAP,
                followDate = "2026-09-30",
                custom = true,
            ).toJson().toString(),
        )
    }

    @Test
    fun `save 写出的串与存量格式逐字一致`() {
        val entry = HolidayEntry(
            date = "2026-10-01",
            endDate = "2026-10-08",
            name = "国庆节",
            type = HolidayEntry.TYPE_HOLIDAY,
        )
        assertTrue(HolidayManager.save(2026, listOf(entry)))
        assertEquals("[$holidayRow2026]", store.getString("entries_2026", ""))

        // 空列表也要能存（对应 JSONArray 的 "[]"）
        assertTrue(HolidayManager.save(2026, emptyList()))
        assertEquals("[]", store.getString("entries_2026", ""))
    }

    @Test
    fun `load 能读迁移前 org_json 写出的存量串`() {
        freshStore(mapOf("entries_2026" to "[$holidayRow2026,$workSwapRow2026]"))
        val loaded = HolidayManager.load(2026)
        assertEquals(2, loaded.size, "存量行必须一行都不能丢")
        assertEquals("国庆节", loaded[0].name)
        assertEquals("2026-10-08", loaded[0].endDate)
        assertEquals("国庆节调休", loaded[1].name)
        assertEquals(HolidayEntry.TYPE_WORKSWAP, loaded[1].type)
        assertEquals("2026-09-30", loaded[1].followDate)
        assertTrue(loaded[1].custom)
    }

    @Test
    fun `load 与 save 往返后内容不变（行按日期重排，与原实现一致）`() {
        freshStore(mapOf("entries_2026" to "[$holidayRow2026,$workSwapRow2026]"))
        val loaded = HolidayManager.load(2026)
        assertTrue(HolidayManager.save(2026, loaded))
        // 原实现写盘前 `entries.sortedBy { it.date }`，所以 09-20 会排到 10-01 前面 —— 这是
        // 迁移前就有的行为，不是本次改动引入的。行内容本身逐字未变。
        assertEquals("[$workSwapRow2026,$holidayRow2026]", store.getString("entries_2026", ""))
        assertEquals(loaded.toSet(), HolidayManager.load(2026).toSet())
    }

    // ── 解析的容错语义（org.json 的 opt 判定）──────

    @Test
    fun `缺字段或类型不符的行被丢弃而不是整批失败`() {
        val rows = listOf(
            // 合法
            """{"date":"2026-01-01","endDate":"","name":"元旦","type":0}""",
            // 缺 name
            """{"date":"2026-01-02","endDate":"","type":0}""",
            // name 不是字符串（org.json 的 opt(key) as? String 会失败）
            """{"date":"2026-01-03","endDate":"","name":123,"type":0}""",
            // type 不是整数
            """{"date":"2026-01-04","endDate":"","name":"x","type":"0"}""",
            // 日期非法
            """{"date":"2026-13-45","endDate":"","name":"x","type":0}""",
            // 元素不是对象
            """42""",
        ).joinToString(",", "[", "]")
        freshStore(mapOf("entries_2026" to rows))
        val loaded = HolidayManager.load(2026)
        assertEquals(1, loaded.size, "只应留下那一条合法行")
        assertEquals("元旦", loaded[0].name)
    }

    @Test
    fun `类型不符的 followWeek 让整行作废`() {
        // isStoredOptionalIntValid：present 且不是整数 → 整行丢
        freshStore(
            mapOf(
                "entries_2026" to
                    """[{"date":"2026-01-01","endDate":"","name":"元旦","type":0,"followWeek":"1"}]""",
            ),
        )
        assertTrue(HolidayManager.load(2026).isEmpty())

        // 缺该字段是合法的（老数据）
        freshStore(
            mapOf(
                "entries_2026" to
                    """[{"date":"2026-01-01","endDate":"","name":"元旦","type":0}]""",
            ),
        )
        assertEquals(1, HolidayManager.load(2026).size)
    }

    @Test
    fun `存储串整体非法时返回空列表而不是崩`() {
        freshStore(mapOf("entries_2026" to "not json"))
        assertTrue(HolidayManager.load(2026).isEmpty())
        freshStore(mapOf("entries_2026" to """{"a":1}"""))
        assertTrue(HolidayManager.load(2026).isEmpty())
    }

    // ── 年份键与版本号 ───────────────────────────

    @Test
    fun `loadAllByYear 只认 entries_ 加纯数字的键`() {
        freshStore(
            mapOf(
                "entries_2025" to "[$holidayRow2026]",
                "entries_2026" to "[$holidayRow2026]",
                "entries_20x6" to "[$holidayRow2026]",
                "entries_20261" to "[$holidayRow2026]",
                "entries_" to "[$holidayRow2026]",
                "version" to 123L,
            ),
        )
        // ⚠ `entries_20261` **是**合法键：原实现的判据是「去掉前缀后能 toInt 且 toString 回来一模一样」，
        // 20261 满足。这里如实锁住，避免有人"顺手修正"成 4 位数而改变存量行为。
        assertEquals(setOf(2025, 2026, 20261), HolidayManager.loadAllByYear().keys)
    }

    @Test
    fun `版本号单调递增且读得出`() {
        freshStore()
        assertEquals(0L, HolidayManager.getVersion())
        HolidayManager.save(2026, listOf(HolidayEntry("2026-01-01", "", "元旦", 0)))
        val v1 = HolidayManager.getVersion()
        assertTrue(v1 > 0L)
        HolidayManager.save(2026, listOf(HolidayEntry("2026-01-01", "", "元旦", 0)))
        assertTrue(HolidayManager.getVersion() > v1, "同一毫秒内两次 save 也必须变号")
    }

    // ── 备份通道 ─────────────────────────────────

    @Test
    fun `备份里的条目行按 Gson 时代的语义解析`() {
        val backup = mapOf<String, Any?>(
            HolidayManager.BACKUP_KEY to mapOf(
                "entries_2026" to "[$holidayRow2026]",
            ),
        )
        val data = HolidayManager.decodeBackupData(backup)
        assertEquals(setOf("entries_2026"), data.entries.keys)
        assertEquals("[$holidayRow2026]", data.entries["entries_2026"])
    }

    @Test
    fun `备份条目行非法时抛错`() {
        // 行里 name 缺失 → parseBackupEntry 返回 null → hasOnlyValidStoredRows false
        val badRow = """{"date":"2026-01-01","endDate":"","type":0}"""
        assertFailsWith<IllegalArgumentException> {
            HolidayManager.decodeBackupData(
                mapOf(HolidayManager.BACKUP_KEY to mapOf("entries_2026" to "[$badRow]")),
            )
        }
        // 键不是 entries_{年}
        assertFailsWith<IllegalArgumentException> {
            HolidayManager.decodeBackupData(
                mapOf(HolidayManager.BACKUP_KEY to mapOf("nope" to "[$holidayRow2026]")),
            )
        }
        // 整体不是数组
        assertFailsWith<IllegalArgumentException> {
            HolidayManager.decodeBackupData(
                mapOf(HolidayManager.BACKUP_KEY to mapOf("entries_2026" to """{"a":1}""")),
            )
        }
    }

    @Test
    fun `备份里没有条目键时视为空配置`() {
        val data = HolidayManager.decodeBackupData(emptyMap())
        assertTrue(data.entries.isEmpty())
        assertEquals(HolidayEndCourseExclusion(), data.exclusion)
        assertEquals(HolidayBeforeCourseExclusion(), data.beforeExclusion)
    }

    @Test
    fun `剔除设置的备份往返一致`() {
        val exclusion = HolidayEndCourseExclusion(enabled = true, startSection = 3, endSection = 5)
        val before = HolidayBeforeCourseExclusion(enabled = true, startSection = 1, endSection = 2)
        assertTrue(HolidayManager.saveEndCourseExclusion(exclusion))
        assertTrue(HolidayManager.saveBeforeCourseExclusion(before))

        val exported = HolidayManager.exportBackupData()
        val decoded = HolidayManager.decodeBackupData(exported)
        assertEquals(exclusion, decoded.exclusion)
        assertEquals(before, decoded.beforeExclusion)

        freshStore()
        HolidayManager.restoreBackupData(decoded)
        assertEquals(exclusion, HolidayManager.loadEndCourseExclusion())
        assertEquals(before, HolidayManager.loadBeforeCourseExclusion())
    }

    @Test
    fun `非法区间的剔除设置不落盘`() {
        freshStore()
        assertFalse(HolidayManager.saveEndCourseExclusion(HolidayEndCourseExclusion(true, 5, 3)))
        assertEquals(0L, HolidayManager.getVersion())
    }

    // ── 写入通道 ─────────────────────────────────

    @Test
    fun `updateEntries 拒绝会写坏的变换`() {
        freshStore()
        // 变换改变了年份集合 → 拒绝
        assertFalse(
            HolidayManager.updateEntries(setOf(2026)) { current -> current + (2027 to emptyList()) },
        )
        // 变换产出了非法条目 → 拒绝
        assertFalse(
            HolidayManager.updateEntries(setOf(2026)) { current ->
                current + (2026 to listOf(HolidayEntry("bad", "", "x", 0)))
            },
        )
        assertEquals(0L, HolidayManager.getVersion())
    }

    @Test
    fun `存量串损坏时 updateEntries 拒绝覆盖`() {
        freshStore(mapOf("entries_2026" to "not json"))
        assertFalse(
            HolidayManager.updateEntries(setOf(2026)) { current ->
                current + (2026 to listOf(HolidayEntry("2026-01-01", "", "元旦", 0)))
            },
            "存量数据坏了就不该覆盖，否则用户那一年的假期就没了",
        )
    }

    @Test
    fun `mergeApiEntries 保留用户自定义的调休映射`() {
        freshStore()
        val custom = HolidayEntry(
            date = "2026-09-20",
            name = "国庆节调休",
            type = HolidayEntry.TYPE_WORKSWAP,
            followDate = "2026-09-30",
            custom = true,
        )
        assertTrue(HolidayManager.save(2026, listOf(custom)))
        val incoming = listOf(
            HolidayEntry("2026-09-20", "", "国庆节调休", HolidayEntry.TYPE_WORKSWAP),
            HolidayEntry("2026-10-01", "2026-10-08", "国庆节", HolidayEntry.TYPE_HOLIDAY),
        )
        assertTrue(HolidayManager.mergeApiEntries(2026, incoming))
        val loaded = HolidayManager.load(2026)
        assertEquals(custom, loaded.first { it.type == HolidayEntry.TYPE_WORKSWAP }, "custom 条目不能被覆盖")
        assertTrue(loaded.any { it.name == "国庆节" })
    }

    // ── 数据源解析 ───────────────────────────────

    @Test
    fun `holiday-calendar 响应解析`() {
        val json = """
            {"year":2026,"dates":[
              {"date":"2026-10-01","name_cn":"国庆节","type":"public_holiday"},
              {"date":"2026-10-02","name_cn":"国庆节","type":"public_holiday"},
              {"date":"2026-09-20","name_cn":"国庆节调休","type":"transfer_workday"},
              {"date":"2026-12-25","name_cn":"圣诞节","type":"not_a_holiday"}
            ]}
        """.trimIndent()
        val entries = HolidayManager.parseApiResponse(json)
        // mergeConsecutive 会先按 date 排序，再把「同名且相邻」的假期合并成一条区间，
        // 所以 09-20 的调休排在最前，10-01/10-02 合并成一条。
        assertEquals(2, entries.size)
        assertEquals(HolidayEntry.TYPE_WORKSWAP, entries[0].type)
        assertEquals("2026-09-20", entries[0].date)
        assertEquals(HolidayEntry.TYPE_HOLIDAY, entries[1].type)
        assertEquals("2026-10-01", entries[1].date)
        assertEquals("2026-10-02", entries[1].endDate)
    }

    @Test
    fun `holiday-calendar 响应缺 dates 时返回空列表`() {
        assertTrue(HolidayManager.parseApiResponse("""{"year":2026}""").isEmpty())
        assertTrue(HolidayManager.parseApiResponse("not json").isEmpty())
        assertTrue(HolidayManager.parseApiResponse("[]").isEmpty())
    }

    @Test
    fun `apihubs 响应只认 recess 与 overtime 两个字段`() {
        val json = """
            {"code":0,"data":{"list":[
              {"date":"20261001","holiday_recess":1,"holiday_overtime":0,"holiday_cn":"国庆节"},
              {"date":"20260920","holiday_recess":2,"holiday_overtime":1,"holiday_cn":"","holiday_overtime_cn":"国庆节调休"},
              {"date":"20261225","holiday_recess":2,"holiday_overtime":10,"holiday_cn":"圣诞节"}
            ]}}
        """.trimIndent()
        val entries = HolidayManager.parseApiHubsResponse(json)
        assertEquals(2, entries.size, "圣诞节（recess!=1 且 overtime==10）不能算假期")
        assertEquals(HolidayEntry.TYPE_WORKSWAP, entries[0].type)
        assertEquals("2026-09-20", entries[0].date)
        assertEquals("国庆节调休", entries[0].name)
        assertEquals(HolidayEntry.TYPE_HOLIDAY, entries[1].type)
        assertEquals("2026-10-01", entries[1].date)
        assertEquals("国庆节", entries[1].name)
    }

    @Test
    fun `数据源选择与地址成对`() {
        freshStore()
        assertEquals(HolidayManager.SOURCE_HOLIDAY_CALENDAR, HolidayManager.holidaySource())
        assertTrue(HolidayManager.sourceUrlFor(2026).contains("holiday-calendar@1/data/CN/2026.json"))
        HolidayManager.setHolidaySource(HolidayManager.SOURCE_APIHUBS)
        assertEquals(HolidayManager.SOURCE_APIHUBS, HolidayManager.holidaySource())
        assertTrue(HolidayManager.sourceUrlFor(2026).contains("apihubs.cn"))
    }

    // ── 旧调休映射迁移（回调注入）─────────────────

    private fun legacyWorkSwapRow() =
        """{"date":"2026-09-20","endDate":"","name":"国庆节调休","type":1,""" +
            """"followDate":"","followWeek":4,"followWeekday":3,"custom":false}"""

    @Test
    fun `迁移信任「换算出的日期确实在假期里」的旧映射`() {
        // 把假期区间扩到包含 09-30，这样换算结果落在假期内 → 走「原样换算」分支
        val coveringHoliday =
            """{"date":"2026-09-28","endDate":"2026-10-08","name":"国庆节","type":0,""" +
                """"followDate":"","followWeek":-1,"followWeekday":-1,"custom":false}"""
        freshStore(mapOf("entries_2026" to "[${legacyWorkSwapRow()},$coveringHoliday]"))
        HolidayManager.migrateLegacyFollowDates { week, weekday ->
            if (week == 4 && weekday == 3) kotlinx.datetime.LocalDate(2026, 9, 30) else null
        }
        val migrated = HolidayManager.load(2026).first { it.type == HolidayEntry.TYPE_WORKSWAP }
        assertEquals("2026-09-30", migrated.followDate)
        // 标记写上了 → 第二次调用直接返回（不会重复迁移）
        assertEquals(true, store.getBoolean("follow_date_migrated", false))
    }

    @Test
    fun `迁移对失效的旧映射改用建议值`() {
        // 假期是 10-01~10-08，换算出的 09-30 不在假期里 → 判定失效 → 用建议值（假期最后一个工作日 10-08）
        freshStore(mapOf("entries_2026" to "[${legacyWorkSwapRow()},$holidayRow2026]"))
        HolidayManager.migrateLegacyFollowDates { week, weekday ->
            if (week == 4 && weekday == 3) kotlinx.datetime.LocalDate(2026, 9, 30) else null
        }
        val migrated = HolidayManager.load(2026).first { it.type == HolidayEntry.TYPE_WORKSWAP }
        assertEquals("2026-10-08", migrated.followDate, "失效映射应回退到 suggestWorkSwapFollowTargets 的建议值")
    }

    @Test
    fun `没有旧映射时只写迁移标记`() {
        freshStore(mapOf("entries_2026" to "[$holidayRow2026]"))
        HolidayManager.migrateLegacyFollowDates { _, _ -> error("不该被调用") }
        assertEquals(true, store.getBoolean("follow_date_migrated", false))
    }
}
