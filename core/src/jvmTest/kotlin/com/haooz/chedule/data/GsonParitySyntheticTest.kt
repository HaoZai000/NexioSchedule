package com.haooz.chedule.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haooz.chedule.data.backup.decodeCourses
import com.haooz.chedule.data.backup.decodeLongList
import com.haooz.chedule.data.backup.decodeScheduleFolders
import com.haooz.chedule.data.backup.decodeStringList
import com.haooz.chedule.data.backup.decodeTimeConfig
import com.haooz.chedule.data.backup.encodeCourses
import com.haooz.chedule.data.backup.encodeLongList
import com.haooz.chedule.data.backup.encodeScheduleFolders
import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.backup.encodeTimeConfig
import com.haooz.chedule.data.backup.parseTimeConfigSnapshotOrNull
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.SpecialBlock
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.storage.ScheduleFolder
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 用**本机现场生成**的 Gson 产物对拍 [ScheduleCodec]，不依赖任何外部备份文件。
 *
 * 判据：语义相等（按名字取值、数字归一为 Double），不是逐字相等 ——
 * release 构建会被 R8 重排字段顺序，字段顺序不是兼容契约。
 */
class GsonParitySyntheticTest {

    private val gson = Gson()

    private fun semantic(json: String): Any? = jsonToPlainValue(parseJsonElement(json))

    private fun sampleCourses(): List<Course> = listOf(
        // 常规课：全部字段有值
        Course(
            id = "c1", name = "高等数学", classroom = "A101", teacher = "张老师",
            dayOfWeek = 3, startSection = 1, endSection = 2,
            startWeek = 1, endWeek = 16, weekType = 0,
            colorRes = 0xFF4CAF50L,
            selectedWeeks = listOf(1, 3, 5, 7, 9, 11, 13, 15),
            scheduleId = "数字经济3", lastModified = 1_760_000_000_000L,
            isCustomTime = false, customStartTime = null, customEndTime = null,
        ),
        // 自定义时间的课：两个 nullable 字段非 null
        Course(
            id = "c2", name = "体育", classroom = "操场", teacher = "",
            dayOfWeek = 5, startSection = 3, endSection = 4,
            startWeek = 1, endWeek = 16, weekType = 2,
            colorRes = 0xFF2196F3L,
            selectedWeeks = emptyList(),
            scheduleId = "数字经济3", lastModified = 1_760_000_000_001L,
            isCustomTime = true, customStartTime = "09:00", customEndTime = "10:30",
        ),
        // 最小课：空串 / 零值 / 空列表
        Course(
            id = "c3", name = "", classroom = "", teacher = "",
            dayOfWeek = 7, startSection = 12, endSection = 12,
            startWeek = 0, endWeek = 0, weekType = 1,
            colorRes = 0L,
            selectedWeeks = emptyList(),
            scheduleId = "", lastModified = 0L,
        ),
        // 中文 / 特殊字符：Gson 不转义 `<>&='`，转义必须一致
        Course(
            id = "c4", name = "C++ & <数据结构>", classroom = "B'2\"03", teacher = "李老师",
            dayOfWeek = 1, startSection = 5, endSection = 6,
            startWeek = 1, endWeek = 8, weekType = 0,
            colorRes = 0xFFFF9800L,
        ),
    )

    @Test
    fun `Gson 序列化的课程 JSON 能被新解码器原样读回`() {
        val courses = sampleCourses()
        val gsonJson = gson.toJson(courses)
        val decoded = decodeCourses(gsonJson)

        assertEquals(courses.size, decoded.size, "课程条数必须一致")
        courses.zip(decoded).forEachIndexed { i, (expect, actual) ->
            assertEquals(expect, actual, "第 $i 门课与 Gson 产物不一致")
        }
    }

    @Test
    fun `新编码器的课程 JSON 与 Gson 语义一致`() {
        val courses = sampleCourses()
        // 备注是后加字段：空值不落盘，存量课程 JSON 才能逐字不变。
        // Gson 走反射会把空 note / 空 weekNotes 一起写出来 —— 这条差异是有意的，比较前剔掉。
        assertEquals(
            dropEmptyNoteFields(semantic(gson.toJson(courses))),
            semantic(encodeCourses(courses)),
        )
    }

    /** 递归删掉空备注字段：`note` 为 ""、`weekNotes` 为空对象。 */
    private fun dropEmptyNoteFields(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries
            .filterNot { (key, item) ->
                (key == "note" && item == "") ||
                    (key == "weekNotes" && (item as? Map<*, *>)?.isEmpty() == true)
            }
            .associate { it.key.toString() to dropEmptyNoteFields(it.value) }
        is List<*> -> value.map { dropEmptyNoteFields(it) }
        else -> value
    }

    @Test
    fun `课程编解码往返稳定`() {
        val courses = sampleCourses()
        val once = decodeCourses(encodeCourses(courses))
        val twice = decodeCourses(encodeCourses(once))
        assertEquals(once, twice)
        assertEquals(courses, once)
    }

    @Test
    fun `null 字段被省略，与 Gson 的 serializeNulls=false 一致`() {
        val json = encodeCourses(sampleCourses().take(1))
        assertTrue(!json.contains("customStartTime"), "null 的 nullable 字段不能写出")
        assertTrue(!json.contains("null"), "不能出现字面量 null")
        // 有值的必须写出
        val customJson = encodeCourses(listOf(sampleCourses()[1]))
        assertTrue(customJson.contains("\"customStartTime\":\"09:00\""))
        assertTrue(customJson.contains("\"customEndTime\":\"10:30\""))
    }

    @Test
    fun `字段顺序被打乱（R8 重排）仍能正确解码`() {
        val courses = sampleCourses()
        // 模拟 release 构建的字母序 + 缺字段：JSON 对象无序，按名字取值必须成立
        val reordered = """
            [{"classroom":"A101","colorRes":4283215696,"customEndTime":null,
              "customStartTime":null,"dayOfWeek":3,"endSection":2,"endWeek":16,
              "id":"c1","isCustomTime":false,"lastModified":1760000000000,
              "name":"高等数学","scheduleId":"数字经济3","selectedWeeks":[1,3,5,7,9,11,13,15],
              "startSection":1,"startWeek":1,"teacher":"张老师","weekType":0}]
        """.trimIndent()
        val decoded = decodeCourses(reordered)
        assertEquals(1, decoded.size)
        assertEquals(courses[0], decoded[0], "乱序/全量字段的 JSON 必须解出同一门课")
    }

    @Test
    fun `缺字段的老 JSON 不会静默变空课表`() {
        // 老备份里可能只写了部分字段（早期版本模型更瘦）
        val lean = """[{"id":"old1","name":"英语","dayOfWeek":2,"startSection":1,"endSection":2}]"""
        val decoded = decodeCourses(lean)
        assertEquals(1, decoded.size)
        assertEquals("old1", decoded[0].id)
        assertEquals("英语", decoded[0].name)
        assertEquals(2, decoded[0].dayOfWeek)
        // 缺失字段回落零值而非丢失整条
        assertEquals("", decoded[0].classroom)
        assertEquals(emptyList(), decoded[0].selectedWeeks)
    }

    @Test
    fun `time_config 走 Gson 对拍`() {
        val config = TimeConfig(
            id = 7L,
            name = "默认作息",
            morningSections = 4,
            afternoonSections = 4,
            eveningSections = 3,
        ).let { TimeConfig.sanitize(7L, it) }

        val gsonJson = gson.toJson(config)
        val decoded = parseTimeConfigSnapshotOrNull(gsonJson)
        assertTrue(decoded != null, "Gson 写的 time_config 必须能解析")
        assertEquals(
            semantic(gsonJson),
            semantic(encodeTimeConfig(TimeConfig.sanitize(7L, decoded))),
            "新编码结果与 Gson 语义一致"
        )
    }

    @Test
    fun `schedule_names 与 schedule_folders 走 Gson 对拍`() {
        val names = listOf("数字经济3", "默认课表", "C++ & <数据结构>")
        assertEquals(semantic(gson.toJson(names)), semantic(encodeStringList(names)))
        assertEquals(names, decodeStringList(gson.toJson(names)))

        val folders = listOf(
            ScheduleFolder(id = "f1", name = "大一上", schedules = listOf("数字经济3")),
            ScheduleFolder(id = "f2", name = "大一下", schedules = emptyList()),
        )
        assertEquals(semantic(gson.toJson(folders)), semantic(encodeScheduleFolders(folders)))
        assertEquals(folders, decodeScheduleFolders(gson.toJson(folders)))
    }

    @Test
    fun `time_config_ids 两种存量格式都要能读`() {
        // saveTimeConfigIds 写的是逗号分隔 "0,1,2"；更老的备份里可能是 JSON 数组 "[0,1,2]"。
        // getTimeConfigIds 两条路都留了口，这里只锁住「JSON 数组」这条 codec 契约。
        assertEquals(listOf(0L, 1L, 2L), decodeLongList("[0,1,2]"))
        // 逗号分隔不走 codec，由 CourseRepository.getTimeConfigIds 自行 split —— 断言它不会
        // 被当成 JSON 抛异常而整体掉空（否则会误触发 rebuildTimeConfigIdsFromKeys）。
        assertTrue(
            runCatching { decodeLongList("0,1,2") }.isFailure,
            "非 JSON 必须由调用方分支处理，codec 侧保持抛异常"
        )
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

    @Test
    fun `空文件夹数组与字面量 null`() {
        assertTrue(decodeScheduleFolders("[]").isEmpty())
        assertTrue(decodeScheduleFolders("null").isEmpty())
        assertTrue(runCatching { decodeScheduleFolders("nope") }.isFailure)
    }

    @Test
    fun `Long 列表与 Gson 一致`() {
        val ids = listOf(1L, 4L, 8L, 1234567890123L)
        val gsonJson = gson.toJson(ids)
        val encoded = encodeLongList(ids)
        assertEquals(semantic(gsonJson), semantic(encoded))
        assertEquals(ids, decodeLongList(encoded))
        assertEquals(semantic(gsonJson), semantic(encodeLongList(decodeLongList(gsonJson))))
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
}
