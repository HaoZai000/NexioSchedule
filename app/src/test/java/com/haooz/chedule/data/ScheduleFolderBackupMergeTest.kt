package com.haooz.chedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleFolderBackupMergeTest {
    @Test
    fun fullBackupWithFoldersPassesScheduleValidation() {
        val backup = mapOf<String, Any>(
            "schedule_names" to "[\"Default\"]",
            "current_schedule_id" to "Default",
            "schedule_Default_courses" to "[]",
            "schedule_folders" to """[{"id":"folder_1","name":"Group","schedules":["Default"]}]""",
        )

        assertEquals(listOf("Default"), validateFullScheduleBackupStructure(backup))
    }

    @Test
    fun unknownScheduleKeyIsStillRejected() {
        val backup = mapOf<String, Any>(
            "schedule_names" to "[\"Default\"]",
            "schedule_Default_courses" to "[]",
            "schedule_Unknown_total_weeks" to 20,
        )

        assertThrows(IllegalArgumentException::class.java) {
            validateFullScheduleBackupStructure(backup)
        }
    }

    @Test
    fun invalidFolderDataCannotStartRestoringPreferences() {
        val base = mapOf<String, Any>(
            "schedule_names" to "[\"Default\"]",
            "schedule_Default_courses" to "[]",
        )
        val invalidFolders = listOf(
            "null",
            "[null]",
            """[{"name":"Group","schedules":["Default"]}]""",
            """[{"id":"folder_1","name":"Group"}]""",
            """[{"id":"folder_1","name":"Group","schedules":["Missing"]}]""",
        )

        invalidFolders.forEach { folders ->
            var restoreStarted = false
            assertThrows(IllegalArgumentException::class.java) {
                withValidatedFullScheduleBackup(base + ("schedule_folders" to folders)) {
                    restoreStarted = true
                }
            }
            assertFalse(restoreStarted)
        }
    }

    @Test
    fun validFoldersKeepAnExplicitlyUngroupedSchedule() {
        val backup = mapOf<String, Any>(
            "schedule_names" to "[\"Grouped\",\"Root\"]",
            "schedule_Grouped_courses" to "[]",
            "schedule_Root_courses" to "[]",
            "schedule_folders" to """[{"id":"folder_1","name":"Group","schedules":["Grouped"]}]""",
        )

        assertEquals(listOf("Grouped", "Root"), validateFullScheduleBackupStructure(backup))
        assertTrue(shouldPreserveRestoredFolderMembership(backup))
    }

    @Test
    fun legacyBackupWithoutFoldersNeedsDefaultFolderMigration() {
        val backup = mapOf<String, Any>(
            "schedule_names" to "[\"Default\"]",
            "schedule_Default_courses" to "[]",
        )

        assertFalse(shouldPreserveRestoredFolderMembership(backup))
    }
}
