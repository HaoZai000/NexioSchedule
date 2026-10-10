package com.haooz.chedule.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ScheduleCodec] 与**真实 Gson** 的差分对拍 —— R2 数据层迁移的安全网。
 *
 * ## 判据：语义相等，不是逐字相等
 *
 * 实测（`java -cp gson-2.11.0.jar Order.java`）：**Gson 按声明顺序输出字段、保留 Map 插入顺序**。
 * 而真实用户备份里 `_courses` 的字段是**字母序** —— 那是 release 构建被 **R8 重排字段**的产物。
 * JSON 对象按规范无序，两端都按名字取值，所以**字段顺序不是兼容契约**。
 *
 * 因此这里用「**解析回 Map 后比较**」：Map 相等与键顺序无关，数字统一成 Double 后也比较得动。
 * 这既能抓住真正的兼容性问题（字段名/字段集合/值/类型），又不会被无意义的顺序差异绊倒。
 *
 * ## 数据来源
 *
 * `全部备份_20261009_155825.json`（真实全量备份）。文件不在时打印警告并跳过。
 *
 * ⚠ 本文件不入库（用户长期要求）。
 */
class ScheduleCodecGsonParityTest {

    private val gson = Gson()

    private fun backupValue(key: String): String? {
        val f = File(BACKUP_PATH)
        if (!f.exists()) return null
        val root = jsonToPlainValue(parseJsonObject(f.readText())) as? Map<*, *> ?: return null
        return root[key] as? String
    }

    private fun requireBackupOrSkip(): Boolean {
        if (!File(BACKUP_PATH).exists()) {
            println("⚠ 跳过：本机没有 $BACKUP_PATH")
            return false
        }
        return true
    }

    /** JSON 文本 → 与键顺序无关的语义结构（数字统一 Double）。 */
    private fun semantic(json: String): Any? = jsonToPlainValue(parseJsonElement(json))

    // ── Course ──────────────────────────────────

    @Test
    fun `真实 courses 解码结果与 Gson 完全一致`() {
        if (!requireBackupOrSkip()) return
        val real = backupValue("schedule_数字经济3_courses")!!
        val theirs: List<Course> = gson.fromJson(real, object : TypeToken<List<Course>>() {}.type)
        val mine = decodeCourses(real)

        assertTrue(theirs.isNotEmpty())
        assertEquals(theirs.size, mine.size, "课程条数必须一致")
        theirs.zip(mine).forEachIndexed { i, (t, m) ->
            assertEquals(t, m, "第 $i 门课字段不一致")
        }
    }

    @Test
    fun `真实 courses 编码结果与 Gson 语义一致`() {
        if (!requireBackupOrSkip()) return
        val real = backupValue("schedule_数字经济3_courses")!!
        val courses = decodeCourses(real)

        assertEquals(semantic(gson.toJson(courses)), semantic(encodeCourses(courses)))
    }

    @Test
    fun `Course 编解码往返稳定`() {
        if (!requireBackupOrSkip()) return
        val real = backupValue("schedule_数字经济3_courses")!!
        val once = decodeCourses(real)
        val twice = decodeCourses(encodeCourses(once))
        assertEquals(once, twice)
        // 二次编码也必须与一次编码语义相同（往返稳定）
        assertEquals(semantic(encodeCourses(once)), semantic(encodeCourses(twice)))
    }

    @Test
    fun `null 字段被省略（Gson 的 serializeNulls=false）`() {
        if (!requireBackupOrSkip()) return
        val real = backupValue("schedule_数字经济3_courses")!!
        // 真实数据里没有自定义时间课 → 一个 customStartTime 都不该出现
        assertTrue(!real.contains("customStartTime"))
        val encoded = encodeCourses(decodeCourses(real))
        assertTrue(!encoded.contains("customStartTime"), "null 字段必须被省略，不能写出 null")
        assertTrue(!encoded.contains("null"))

        // 反过来：有自定义时间的课必须把两个字段都写出来
        val custom = Course(
            id = "c", name = "n", classroom = "", teacher = "", dayOfWeek = 1,
            startSection = 1, endSection = 1, startWeek = 1, endWeek = 1, weekType = 0,
            colorRes = 1L, scheduleId = "s", lastModified = 2L,
            isCustomTime = true, customStartTime = "09:00", customEndTime = "10:30",
        )
        val json = encodeCourses(listOf(custom))
        assertTrue(json.contains("\"customStartTime\":\"09:00\""))
        assertTrue(json.contains("\"customEndTime\":\"10:30\""))
        assertEquals(semantic(gson.toJson(listOf(custom))), semantic(json))
    }

    @Test
    fun `Courses 的边界输入与 Gson 行为对齐`() {
        // 空数组
        assertTrue(decodeCourses("[]").isEmpty())
        assertEquals(semantic("[]"), semantic(encodeCourses(emptyList())))
        // 字面量 null：Gson 返回 null，调用点写的是 `?: emptyList()`
        assertTrue(decodeCourses("null").isEmpty())
        // 格式非法：Gson 抛 JsonSyntaxException；这里抛异常，由调用点的 try/catch 兜
        assertTrue(runCatching { decodeCourses("not json") }.isFailure)
        assertTrue(runCatching { decodeCourses("""{"a":1}""") }.isFailure)
        assertTrue(
            runCatching {
                gson.fromJson<List<Course>>("not json", object : TypeToken<List<Course>>() {}.type)
            }.isFailure,
        )
    }

    // ── ScheduleFolder ──────────────────────────

    @Test
    fun `真实 schedule_folders 编解码与 Gson 语义一致`() {
        if (!requireBackupOrSkip()) return
        val real = backupValue("schedule_folders")!!
        val theirs: List<ScheduleFolder> =
            gson.fromJson(real, object : TypeToken<List<ScheduleFolder>>() {}.type)
        val mine = decodeScheduleFolders(real)

        assertEquals(theirs, mine, "文件夹解码结果必须一致")
        assertEquals(semantic(gson.toJson(theirs)), semantic(encodeScheduleFolders(mine)))
        assertEquals(semantic(gson.toJson(mine)), semantic(encodeScheduleFolders(mine)))
    }

    @Test
    fun `空文件夹数组与字面量 null`() {
        assertTrue(decodeScheduleFolders("[]").isEmpty())
        assertTrue(decodeScheduleFolders("null").isEmpty())
        assertTrue(runCatching { decodeScheduleFolders("nope") }.isFailure)
    }

    // ── 标量列表 ────────────────────────────────

    @Test
    fun `真实 schedule_names 与 Gson 一致`() {
        if (!requireBackupOrSkip()) return
        val real = backupValue("schedule_names")!!
        val theirs: List<String> = gson.fromJson(real, object : TypeToken<List<String>>() {}.type)
        assertEquals(theirs, decodeStringList(real))
        assertEquals(semantic(gson.toJson(theirs)), semantic(encodeStringList(theirs)))
    }

    @Test
    fun `time_config_ids 不是 JSON（逗号分隔）不能按 JSON 解`() {
        if (!requireBackupOrSkip()) return
        val raw = backupValue("time_config_ids")!!
        // 真实值形如 "1,4,8" —— 按 JSON 解会失败，这正是它必须走 split 的证据
        assertTrue(!raw.trimStart().startsWith("["), "真实值: $raw")
        assertTrue(runCatching { decodeLongList(raw) }.isFailure)
        assertEquals(listOf(1L, 4L, 8L), raw.split(",").mapNotNull { it.trim().toLongOrNull() })
    }

    @Test
    fun `Long 列表与 Gson 一致`() {
        val ids = listOf(1L, 4L, 8L, 1234567890123L)
        assertEquals(semantic(gson.toJson(ids)), semantic(encodeLongList(ids)))
        assertEquals(ids, decodeLongList(encodeLongList(ids)))
        assertEquals(semantic(gson.toJson(ids)), semantic(encodeLongList(decodeLongList(gson.toJson(ids)))))
    }

    // ── TimeConfig（R2 里最微妙的一族）─────────────

    @Test
    fun `真实 time_config 解码加 sanitize 与 Gson 完全一致`() {
        if (!requireBackupOrSkip()) return
        for (key in listOf("time_config_1", "time_config_4", "time_config_8")) {
            val real = backupValue(key) ?: continue
            val theirs = TimeConfig.sanitize(7L, gson.fromJson(real, TimeConfig::class.java))
            val mine = TimeConfig.sanitize(7L, decodeTimeConfig(real))
            assertEquals(theirs, mine, "$key 经 sanitize 后必须完全一致")
            assertTrue(mine.routines.isNotEmpty(), "$key 的作息方案不能丢")
        }
    }

    @Test
    fun `真实 time_config 编码与 Gson 语义一致`() {
        if (!requireBackupOrSkip()) return
        val real = backupValue("time_config_1")!!
        val config = TimeConfig.sanitize(1L, decodeTimeConfig(real))
        assertEquals(semantic(gson.toJson(config)), semantic(encodeTimeConfig(config)))
    }

    @Test
    fun `time_config 编解码往返稳定`() {
        if (!requireBackupOrSkip()) return
        val config = TimeConfig.sanitize(1L, decodeTimeConfig(backupValue("time_config_1")!!))
        val once = encodeTimeConfig(config)
        val back = TimeConfig.sanitize(1L, decodeTimeConfig(once))
        assertEquals(config, back)
        assertEquals(semantic(once), semantic(encodeTimeConfig(back)))
    }

    /**
     * ⚠ 这批最容易出错的地方：Gson 走 `UnsafeAllocator` 绕开构造器，
     * **缺字段会让非空字段变 null**，`sanitize()` 正是靠这个兜底。
     * 手写解码若图省事取 Kotlin 构造默认值（`morningSections = 4`），
     * 就会跳过 `total <= 0 → 4/4/4` 的恢复分支。这个用例逐个字段钉死。
     */
    @Test
    fun `缺字段时的恢复路径与 Gson 逐字对齐`() {
        val cases = listOf(
            """{}""",
            """{"id":1}""",
            """{"id":1,"name":null}""",
            """{"id":1,"morningSections":3}""",
            """{"id":1,"afternoonSections":0}""",
            """{"id":1,"classDuration":0}""",
            """{"id":1,"classDuration":-5}""",
            """{"id":1,"shortBreak":-1}""",
            """{"id":1,"sectionTimes":null}""",
            """{"id":1,"sectionNames":null}""",
            """{"id":1,"specialBlocks":null}""",
            """{"id":1,"specialBlocks":[]}""",
            """{"id":1,"routines":[]}""",
            """{"id":1,"routines":[{"id":9}]}""",
            """{"id":1,"routines":[{"id":9,"name":"夏","effectiveMonth":5,"effectiveDay":1}]}""",
            """{"id":1,"specialBlocks":[{"id":2,"name":"早读"}]}""",
            """{"id":1,"specialBlocks":[{"id":2,"name":"早读","items":[{"id":3,"name":"周一"}]}]}""",
            """{"id":1,"routines":[{"id":9,"specialBlocks":null}]}""",
            """{"id":1,"routines":[{"id":9,"specialBlocks":[]}]}""",
        )
        cases.forEach { json ->
            val theirs = TimeConfig.sanitize(7L, gson.fromJson(json, TimeConfig::class.java))
            val mine = TimeConfig.sanitize(7L, decodeTimeConfig(json))
            assertEquals(theirs, mine, "输入: $json")
        }
    }

    /**
     * **一处有意的行为改进**（不是回归，单独钉住以免被误判成 bug）。
     *
     * `{"routines":null}` 这种 JSON：Gson 会把 null 塞进 `TimeConfig.routines`（非空类型），
     * 随后 `TimeConfig.sanitize()` 内部的 `raw.copy(...)` 因为要复制 `routines` 而**直接 NPE**。
     * 也就是说**现有实现遇到这种数据会崩**。
     *
     * 手写解码把「显式 null」与「字段缺失」都归一成 `emptyList()`，
     * 于是能正常走完 sanitize —— 这是**更健壮**，且不会改变任何正常数据的语义。
     */
    @Test
    fun `routines 显式为 null 时本实现比 Gson 更健壮（有意为之）`() {
        val json = """{"id":1,"routines":null}"""
        val theirs = runCatching { TimeConfig.sanitize(7L, gson.fromJson(json, TimeConfig::class.java)) }
        val mine = runCatching { TimeConfig.sanitize(7L, decodeTimeConfig(json)) }

        assertTrue(theirs.isFailure, "Gson 路径在这种数据上会 NPE（既有行为）")
        assertTrue(mine.isSuccess, "本实现应当容忍它")
        // 归一结果与「字段整个缺失」完全一致（sanitize 会补种一个默认作息，所以 routines 不为空）
        assertEquals(
            TimeConfig.sanitize(7L, gson.fromJson("""{"id":1}""", TimeConfig::class.java)),
            mine.getOrThrow(),
        )
        assertEquals(1, mine.getOrThrow().routines.size, "应补种默认作息")
    }

    @Test
    fun `SpecialBlock 的 items 为 null 时字段被省略`() {
        // 旧数据里 items 缺失 = null（Gson 置 null，Kotlin 默认值不生效）
        val block = SpecialBlock.fromRaw(mapOf("id" to 2.0, "name" to "早读"))!!
        assertEquals(null, block.items)
        val json = encodeTimeConfig(
            TimeConfig(id = 1L, specialBlocks = listOf(block)),
        )
        assertTrue(!json.contains("\"items\""), "items 为 null 时不能写出该字段: $json")

        val withItems = SpecialBlock.fromRaw(
            mapOf("id" to 2.0, "name" to "早读", "items" to listOf(mapOf("id" to 3.0))),
        )!!
        val json2 = encodeTimeConfig(TimeConfig(id = 1L, specialBlocks = listOf(withItems)))
        assertTrue(json2.contains("\"items\""), "items 非 null 时必须写出: $json2")
        assertTrue(json2.contains("\"startTime\":\"08:00\""), "缺 startTime 时走 fromRaw 的兜底值")
    }

    @Test
    fun `time_config 的非法输入与 Gson 行为对齐`() {
        assertTrue(runCatching { decodeTimeConfig("not json") }.isFailure)
        assertTrue(runCatching { decodeTimeConfig("[]") }.isFailure)
        assertTrue(runCatching { decodeTimeConfig("null") }.isFailure)
        assertTrue(runCatching { decodeTimeConfig("1") }.isFailure)
        assertTrue(
            runCatching { gson.fromJson("not json", TimeConfig::class.java) }.isFailure,
        )
    }

    private companion object {
        const val BACKUP_PATH = "C:/Users/43908/Downloads/全部备份_20261009_155825.json"
    }
}
