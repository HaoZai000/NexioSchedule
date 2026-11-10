package com.haooz.chedule.data

import com.haooz.chedule.data.backup.normalizeFullScheduleBackup
import com.haooz.chedule.data.backup.validateFullScheduleBackupStructure
import com.haooz.chedule.data.backup.withValidatedFullScheduleBackup
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseJsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 「孤儿时间配置绑定」备份的恢复回归 —— 用户真实踩坑（2026-10-10 报障）。
 *
 * ## 背景
 *
 * 真实备份（41 键）里有 `schedule_time_config_默认课表`：课表删除时 `schedule_{名}_*`
 * 前缀的键被清掉，但绑定键用的是另一个前缀 `schedule_time_config_{名}`，没被一起清
 * → 残留在用户 prefs 里、随全量备份原样导出。
 *
 * 旧校验（1.6.4-1008 及更早）要求绑定名 ∈ `schedule_names`，否则抛
 * `Invalid full schedule backup: time configuration belongs to an unknown schedule`
 * → 用户**连自己的备份都恢复不了**（Toast「无法识别备份文件：…」）。
 *
 * 修复（`9d13813`，2026-10-09）：孤儿绑定**不是损坏**，是正常残留 —— 恢复流程本来就
 * 先删全部 `schedule_*` 再按备份写回，残留写回去与现状一致、无害。校验降级为
 * 「格式合理」（非空），不再要求名字已知。
 *
 * ## 为什么只断言纯函数层
 *
 * 修复点就在 [com.haooz.chedule.data.backup.validateFullScheduleBackupStructure] —— 把孤儿校验拿掉这里立刻红，
 * 覆盖完整。刻意**不碰 CourseRepository / AppStorage**：`CourseRepository()` 实为
 * `getInstance()` 单例，其 prefs 在 JVM 内首次构造时就钉死当时的 AppStorage store；
 * 本类若在别的测试类之前构造它，会污染它们的 store（实测：FullBackupRoundTripTest
 * 因此红过）。纯函数断言顺序无关。
 */
class OrphanTimeConfigBackupRestoreTest {

    /** 与真实备份同构的最小样例：孤儿绑定 + 单课表 + 文件夹。 */
    private val fixtureJson = """
        {
          "schedule_names": "[\"大二上\"]",
          "schedule_folders": "[{\"id\":\"folder_default\",\"name\":\"默认文件夹\",\"schedules\":[\"大二上\"]}]",
          "current_schedule_id": "大二上",
          "schedule_大二上_courses": "[{\"name\":\"高等数学\",\"dayOfWeek\":1,\"startSection\":1,\"endSection\":2,\"selectedWeeks\":[1,2],\"classroom\":\"A101\",\"teacher\":\"张三\"}]",
          "schedule_大二上_total_weeks": 18,
          "schedule_time_config_大二上": 0,
          "schedule_time_config_默认课表": 0,
          "time_config_ids": 0,
          "current_time_config_id": 0
        }
    """.trimIndent()

    @Suppress("UNCHECKED_CAST")
    private fun parse(text: String): Map<String, Any> =
        jsonToPlainValue(parseJsonObject(text)) as Map<String, Any>

    @Test
    fun `孤儿时间配置绑定不被结构校验拒绝`() {
        val root = parse(fixtureJson)

        // 旧版在这里抛 "time configuration belongs to an unknown schedule" —— 本用例守住修复
        val names = validateFullScheduleBackupStructure(root)
        assertEquals(listOf("大二上"), names)

        // normalize 不丢孤儿绑定（恢复写回依赖它）
        val normalized = normalizeFullScheduleBackup(root)
        assertEquals(0, (normalized["schedule_time_config_默认课表"] as Number).toInt())

        // 节假日三件套 + 校验回调同链路走通（与 importAllPreferences 内部管线一致）
        HolidayManager.decodeBackupData(root)
        withValidatedFullScheduleBackup(normalized) { holidayBackup ->
            assertEquals(setOf<String>(), holidayBackup.values.single().entries.keys, "样例无节假日条目")
        }
    }

    /**
     * 真实报障文件的校验全链路（41 键，2026-10-10 报障的那份）。
     * ⚠ 文件不在时**打印警告并跳过**（不静默通过）—— 与 RealBackupRoundTripTest 同惯例。
     */
    @Test
    fun `真实备份_20261009_155910 结构校验与解码通过`() {
        val backupFile = File("C:/Users/43908/Downloads/全部备份_20261009_155910.json")
        if (!backupFile.exists()) {
            println("⚠ 跳过：本机没有 ${backupFile.absolutePath}（这份用例依赖真实报障备份）")
            return
        }
        val root = parse(backupFile.readText())
        assertEquals(41, root.size, "报障备份应为 41 键（键数漂了说明样本换了，用例要跟）")

        val names = validateFullScheduleBackupStructure(root)
        assertEquals(listOf("大二上"), names)

        val holiday = HolidayManager.decodeBackupData(root)
        assertEquals(setOf("entries_2026"), holiday.entries.keys)
    }
}
