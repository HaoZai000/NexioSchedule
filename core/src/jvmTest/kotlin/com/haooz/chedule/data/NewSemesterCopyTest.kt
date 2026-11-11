package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import com.haooz.chedule.data.storage.SqliteCourseRowStore
import com.haooz.chedule.data.util.formatSlashDate
import com.haooz.chedule.data.util.todayLocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 「开启新学期」应复用源课表的设置：总周数 / 智能周末 / 显示非本周要跟着搬，
 * 当前周回到 1、开课日重置为今天、课程为空、作息独立复制。
 *
 * 用行表断言是故意的 —— 设置的真相在 `schedule` 行，prefs 副本迁移后已删。
 * 早先只按键前缀复制 prefs，这三项搬空了才落库默认值（20 / false / true）。
 */
class NewSemesterCopyTest {

    private lateinit var repository: com.haooz.chedule.data.repo.CourseRepository
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqliteCourseRowStore

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        repository = com.haooz.chedule.data.repo.CourseRepository.getInstance()
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
    fun `开启新学期复制源课表设置并重置周次与开课日`() {
        val src = "源课表-${System.nanoTime()}"
        repository.addSchedule(src)
        repository.setCurrentScheduleId(src)
        repository.setTotalWeeks(18)
        repository.setCurrentWeek(5)
        repository.setClassStartTime("2026/9/1")
        repository.addCourse(course(src, "probe-course-1"))
        repository.setSmartWeekend(true)
        repository.setShowNonCurrentWeek(false)

        val dst = "新课表-${System.nanoTime()}"
        repository.createNewSemesterSchedule(dst)

        probe("总周数从源搬过来", repository.getTotalWeeks(dst), 18)
        probe("智能周末从源搬过来", repository.getSmartWeekend(dst), true)
        probe("显示非本周从源搬过来", repository.getShowNonCurrentWeek(dst), false)
        probe("当前周回到第 1 周", repository.getCurrentWeek(dst), 1)
        probe("开课日重置为今天", repository.getClassStartTime(dst), todayLocalDate().formatSlashDate())
        probe("新课表是空表", repository.getCoursesForSchedule(dst).size, 0)
        probe("作息独立复制", repository.getScheduleTimeConfigId(dst) != repository.getScheduleTimeConfigId(src), true)
        probe("作息已绑定", repository.getScheduleTimeConfigId(dst) != 0L, true)
        probe("名字列表含新课表", repository.getScheduleNames().contains(dst), true)
        probe("源课表总周数未被改动", repository.getTotalWeeks(src), 18)
        probe("源课表智能周末未被改动", repository.getSmartWeekend(src), true)
        probe("源课表课程未被带走", repository.getCoursesForSchedule(src).size, 1)

        assertEquals(emptyList<String>(), mismatches, "未按预期复制的设置项")
    }

    private val mismatches = mutableListOf<String>()

    private fun probe(label: String, actual: Any?, expected: Any?) {
        if (actual != expected) mismatches += "$label: 实际=$actual 期望=$expected"
    }

    private fun course(scheduleId: String, id: String) = Course(
        id = id,
        name = "测试课",
        classroom = "A101",
        teacher = "张老师",
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        startWeek = 1,
        endWeek = 16,
        weekType = Course.WEEK_TYPE_ALL,
        colorRes = 0xFF4CAF50L,
        selectedWeeks = emptyList(),
        scheduleId = scheduleId,
        lastModified = 1_000L,
        note = "",
    )
}
