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
 * 单课表导入：**直接写实体表**，不留 blob。
 *
 * 以前这条路径是「写 `schedule_{名}_courses` + 清导入标记」，靠下一次读取顺带把 JSON 导进表。
 * 那等于把一次业务写伪装成一次迁移：导入完到下次读取之间，库里有一份只存在于 prefs 的课表，
 * 而备份、diff、读路径各自对「谁是真相」判断不同。现在只有一个动作 —— 写行。
 */
class SingleScheduleImportTest {

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

    @Test
    fun `导入直接写行，课程与设置都不经过 prefs`() {
        val name = schedule()

        repository.importSingleSchedule(
            scheduleName = name,
            coursesData = listOf(courseMap("高等数学", dayOfWeek = 1, startSection = 1, endSection = 2)),
            timeConfigData = null,
            classStartTime = "2026/09/01",
            currentWeek = 3,
            totalWeeks = 18,
            teachingWeekReorganizations = emptyList(),
            smartWeekend = true,
            showNonCurrentWeek = false,
        )

        assertTrue(store.isImported(name), "导入完就该有行，不该等下次读取")
        assertEquals(listOf("高等数学"), store.load(name).map { it.name })
        assertEquals(3, store.settingsOf(name)?.currentWeek, "当前周要落在行上")
        assertEquals(18, store.settingsOf(name)?.totalWeeks)
        assertEquals("2026/09/01", store.settingsOf(name)?.classStart)
        assertEquals(true, store.displaySettingsOf(name)?.smartWeekend)
        assertEquals(false, store.displaySettingsOf(name)?.showNonCurrentWeek)

        // 卸掉行表：读路径只剩 prefs。还能读到课，说明导入确实写了两份（blob + 行）；
        // 读到空才是「只有表」。
        repository.invalidateAllCaches()
        CourseStorage.install { null }
        assertEquals(
            emptyList(),
            repository.getCoursesForSchedule(name),
            "prefs 里不该再留课程副本：那是会过期的第二真相",
        )
    }

    @Test
    fun `导入自带作息时新配置独立，不串改别的课表`() {
        val other = schedule("原有")
        repository.importSingleSchedule(
            scheduleName = other,
            coursesData = emptyList(),
            timeConfigData = null,
            classStartTime = "2026/09/01",
            currentWeek = 1,
            totalWeeks = 18,
        )
        val otherConfigBefore = repository.getScheduleTimeConfigId(other)

        val name = schedule("导入")
        repository.importSingleSchedule(
            scheduleName = name,
            coursesData = emptyList(),
            timeConfigData = """
                {"name":"导入作息","morningSections":5,"afternoonSections":3,"eveningSections":2}
            """.trimIndent(),
            classStartTime = "2026/09/01",
            currentWeek = 2,
            totalWeeks = 16,
        )

        val imported = repository.getScheduleTimeConfigId(name)
        assertTrue(imported != otherConfigBefore, "导入必须建自己的配置，不能共用")
        assertEquals(5, repository.getTimeConfig(imported).morningSections, "用的是备份里那份作息")
        assertEquals(
            repository.getTimeConfig(otherConfigBefore).morningSections,
            repository.getScheduleTimeConfigId(other).let { repository.getTimeConfig(it).morningSections },
            "另一张课表的作息不能被改",
        )
    }

    @Test
    fun `真实备份恢复后 23 门课都在且分实验室的同名课不互相覆盖`() {
        val json = requireNotNull(javaClass.getResource("/backup_single_schedule.json")).readText()
        val payload = parseBackupPayload(json)
        assertTrue(payload is BackupPayload.Single, "夹具应识别为单课表备份，实际：$payload")
        val name = schedule("数字经济3")

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
            23,
            restored.size,
            "恢复的课程数必须与备份一致 —— 撞 id 时这里会少 2 门并整单回滚",
        )
        assertEquals(
            restored.size,
            restored.map { it.id }.toSet().size,
            "课程 id 必须唯一",
        )

        val teaCourses = restored.filter { it.name == "三茶统筹专题" }
        assertTrue(
            teaCourses.size >= 4,
            "「三茶统筹专题」应完整保留（day3 第6节 + 第8节，各两个实验室），实际 ${teaCourses.size} 条",
        )
        assertTrue(
            teaCourses.map { it.classroom }.toSet().size >= 2,
            "两个实验室的课都要在，不能被互相覆盖",
        )
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun schedule(tag: String = "单表") = "$tag-${System.nanoTime()}"

    private fun courseMap(name: String, dayOfWeek: Int, startSection: Int, endSection: Int) = mapOf(
        "name" to name,
        "classroom" to "A101",
        "teacher" to "张老师",
        "dayOfWeek" to dayOfWeek,
        "startSection" to startSection,
        "endSection" to endSection,
        "selectedWeeks" to listOf(1, 2, 3),
        "weekType" to 0,
    )
}
