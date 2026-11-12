package com.haooz.chedule.data

import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupScopeTest {

    private lateinit var repository: CourseRepository

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        CourseStorage.install { null }
        repository = CourseRepository.getInstance()
        repository.invalidateAllCaches()
        freshState("备份范围")
        repository.setPreClassReminder(true)
        repository.setPreClassReminderMinutes(15)
        repository.setIslandNotification(true)
        repository.setClassDndEnabled(true)
        repository.setClassDndMode(2)
    }

    private fun freshState(name: String) {
        val cleaned = repository.exportAllPreferences()
            .filterKeys { !it.startsWith("schedule_") && !it.startsWith("time_config_") }
            .toMutableMap()
        cleaned["schedule_names"] = encodeStringList(listOf(name))
        cleaned["schedule_folders"] = "[]"
        cleaned["current_schedule_id"] = name
        cleaned["schedule_${name}_courses"] = "[]"
        repository.importAllPreferences(cleaned)
    }

    @Test
    fun `导出只含课表内容`() {
        val backup = repository.exportAllPreferences()
        assertTrue("schedule_备份范围_courses" in backup, "课程必须进备份")
        assertTrue("schedule_names" in backup, "课表名必须进备份")
        deviceKeys.forEach { key ->
            assertFalse(key in backup, "$key 是设备本地设置，不该进备份")
        }
    }

    @Test
    fun `导入备份不丢本机设备设置`() {
        val backup = repository.exportAllPreferences()
        repository.importAllPreferences(backup)
        assertTrue(repository.getPreClassReminder(), "课前提醒应原样留在本机")
        assertEquals(15, repository.getPreClassReminderMinutes())
        assertTrue(repository.getIslandNotification(), "超级岛开关应原样留在本机")
        assertTrue(repository.getClassDndEnabled(), "勿扰开关应原样留在本机")
        assertEquals(2, repository.getClassDndMode())
    }

    private companion object {
        val deviceKeys = listOf(
            "pre_class_reminder", "pre_class_reminder_minutes",
            "next_day_reminder", "next_day_reminder_hour", "next_day_reminder_minute",
            "island_notification", "class_dnd_enabled", "class_dnd_mode",
            "widget_padding_mode", "default_homepage", "shift_mode_enabled",
            "shift_selected_schedules", "appearance_style", "wallpaper_uri",
        )
    }
}
