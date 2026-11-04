package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.backup.BackupPayload
import com.haooz.chedule.data.backup.parseBackupPayload
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import com.haooz.chedule.data.storage.SqliteCourseRowStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 单课表恢复：课程 id 必须唯一。
 *
 * 单课表备份**不导出课程 id**，恢复时用
 * `"${scheduleName}_${name}_${dayOfWeek}_$startSection"` 现造。这个键不含教室、教师、
 * 结束节与周次 —— 同一门课在同一时段分两个实验室上（真实课表里很常见）就会撞 id。
 * 撞了之后 `INSERT OR REPLACE` 把后一条覆盖掉前一条，末尾的行表自校验因尺寸不符失败，
 * **整单恢复回滚**，用户看到「恢复失败」，还会因为课表名先写进去了而多一张空课表。
 *
 * 夹具 [FIXTURE] 是用户真实备份（「数字经济3」，23 门课），其中「三茶统筹专题」
 * 在 day=3 / startSection=6 与 day=3 / startSection=8 各有一对只差实验室的课 ——
 * 正是撞 id 的形态，所以这份真实数据本身就是最好的回归用例。
 */
class SingleScheduleImportIdCollisionTest {

    private lateinit var repository: com.haooz.chedule.data.repo.CourseRepository
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqliteCourseRowStore

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        repository = com.haooz.chedule.data.repo.CourseRepository.getInstance()
        repository.invalidateAllCaches()
        driver = openInMemoryDriver()
        NexioDatabase.Schema.create(driver)
        store = SqliteCourseRowStore(NexioDatabase(driver))
        CourseStorage.install { store }
    }

    @AfterTest
    fun tearDown() {
        CourseStorage.install { null }
        repository.invalidateAllCaches()
        driver.close()
    }

    private fun loadFixture(): BackupPayload.Single {
        val stream = javaClass.getResourceAsStream(FIXTURE) ?: error("找不到测试夹具 $FIXTURE")
        val json = stream.use { it.readBytes() }.toString(Charsets.UTF_8)
        val payload = parseBackupPayload(json)
        assertTrue(payload is BackupPayload.Single, "夹具应识别为单课表备份，实际：$payload")
        return payload
    }

    @Test
    fun `夹具里确实存在会撞 id 的同名同时段课程`() {
        // 先钉住前提：如果哪天夹具换了、撞 id 的形态没了，这个测试就该提醒我们
        val courses = loadFixture().courses
        val syntheticIds = courses.map {
            "${it["name"]}_${it["dayOfWeek"]}_${it["startSection"]}"
        }
        assertTrue(
            syntheticIds.size > syntheticIds.toSet().size,
            "夹具应含同名同时段的课（否则这个回归测试失去意义）",
        )
        assertEquals(23, courses.size, "夹具应有 23 门课")
        assertEquals(21, syntheticIds.toSet().size, "旧合成键只能生成 21 个唯一 id")
    }

    @Test
    fun `真实备份恢复后 23 门课一门不少`() {
        val payload = loadFixture()
        val name = "数字经济3-${System.nanoTime()}"

        repository.importSingleSchedule(
            scheduleName = name,
            coursesData = payload.courses,
            timeConfigData = payload.timeConfig,
            classStartTime = payload.semesterSettings?.classStartTime,
            currentWeek = payload.semesterSettings?.currentWeek,
            totalWeeks = payload.semesterSettings?.totalWeeks,
            teachingWeekReorganizations = payload.teachingWeekReorganizations,
            smartWeekend = payload.semesterSettings?.smartWeekend,
            showNonCurrentWeek = payload.semesterSettings?.showNonCurrentWeek,
        )

        val restored = store.load(name)
        assertEquals(
            payload.courses.size,
            restored.size,
            "恢复的课程数必须与备份一致 —— 撞 id 时这里会少 2 门并整单回滚",
        )
        assertEquals(
            restored.size,
            restored.map { it.id }.toSet().size,
            "课程 id 必须唯一",
        )
    }

    @Test
    fun `分实验室的同名课两条都能落库`() {
        val payload = loadFixture()
        val name = "撞名-${System.nanoTime()}"

        repository.importSingleSchedule(
            scheduleName = name,
            coursesData = payload.courses,
            timeConfigData = payload.timeConfig,
            classStartTime = payload.semesterSettings?.classStartTime,
            currentWeek = payload.semesterSettings?.currentWeek,
            totalWeeks = payload.semesterSettings?.totalWeeks,
        )

        val teaCourses = store.load(name).filter { it.name == "三茶统筹专题" }
        assertTrue(
            teaCourses.size >= 4,
            "「三茶统筹专题」应完整保留（day3 第6节 + 第8节，各两个实验室），实际 ${teaCourses.size} 条",
        )
        assertTrue(
            teaCourses.map { it.classroom }.toSet().size >= 2,
            "两个实验室的课都要在，不能被互相覆盖",
        )
    }

    private companion object {
        const val FIXTURE = "/backup_single_schedule.json"
    }
}
