package com.haooz.chedule.data.sync.kingosoft

import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KingosoftSyncTest {
    @Test
    fun codecMatchesWireVectors() = with(KingosoftCodec) {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Hex("abc"))
        assertEquals("ec849349b656b768918a8154d745431c", md5Hex("青果".repeat(21)))
        val plain = "userid=demo&usertype=STU&week=2"
        val param = "3w1jje3u7tsh2prvg33icsak2prw7g3x8r3k3sgylg36f8lk2l0du23mh8fu000049"
        val param2 = "b1ed72579c1aeb42f564550e655fe84d"
        val encoded = encodeParam(plain, "test-key")
        assertEquals(param, encoded)
        assertEquals(0, encoded.length % 6)
        assertTrue(encoded.matches(Regex("[0-9a-z]+")))
        assertEquals(param2, encodeParam2(plain))
        assertEquals("param=$param&param2=$param2", buildFormBody(plain, "test-key"))
    }

    private fun week(n: Int, entries: String, start: String = "") = KingosoftScheduleParser.parseWeek(
        """{"xn":"2025","xq":"2","zc":"$n","maxzc":"8","maxjc":"12","qssj":"$start",
            "week1":[$entries],"week2":[],"week3":[],"week4":[],"week5":[],"week6":[],"week7":[]}""",
    )

    @Test
    fun reimportCommitsNewBindingAndSummaryOnlyAfterCoursesAreSaved() = runTest {
        AppStorage.init { InMemoryKeyValueStore() }
        val id = "schedule"
        val store = KingosoftSyncStore
        val previous = store.saveConfig(id, KingosoftSyncConfig("https://old.example", "old-key", "old-user"))
        store.recordSuccess(id, 42L, "previous summary")
        val draft = KingosoftSyncConfig("https://new.example", "new-key", "new-user", autoSyncEnabled = true)
        val expected = draft.copy(syncScopeId = previous.syncScopeId)
        fun assertOriginalBinding() {
            assertEquals(previous, store.getConfig(id))
            assertEquals(42L, store.lastSyncAtMillis(id))
            assertEquals("previous summary", store.lastSyncSummary(id))
        }
        var courses = emptyList<Course>()
        val target = KingosoftSyncTarget(id, isCurrent = { true }, readCourses = { courses }, saveCourses = {
            assertOriginalBinding()
            courses = it
        })
        val failure = assertFailsWith<IllegalStateException> {
            KingosoftScheduleSyncer.importAndSync(target, draft) {
                assertEquals(expected, it)
                assertOriginalBinding()
                error("HTTP 503")
            }
        }
        assertEquals("HTTP 503", failure.message)
        assertOriginalBinding()
        assertTrue(courses.isEmpty())

        val outcome = KingosoftScheduleSyncer.importAndSync(target, draft) {
            listOf(week(1, """{"kcmc":"Math","kcdm":"m","jcdm":"1"}"""))
        }
        assertEquals("Math", courses.single().name)
        assertEquals(expected, store.getConfig(id))
        assertTrue(store.lastSyncAtMillis(id) > 42L)
        assertTrue(outcome.hasChanges)
        assertEquals(outcome.summary, store.lastSyncSummary(id))
    }

    @Test
    fun aggregateUsesObservedWeeksAndSkipsCancellations() {
        val entry = """{"kcmc":"课A","kcdm":"a","skbj":"b","rkjs":"T","xq":"R","jcdm":"4,2,3","jcxx":"1-2","skzs":"1,3,5-6","txxx":"note"}"""
        val w1 = week(1, "$entry,$entry")
        val w3 = week(3, entry.replace("\"jcdm\":\"4,2,3\"", "\"sftk\":\"1\"" ).replace("1-2", "2-4"))
        val w5 = week(5, entry.replace("\"4,2,3\"", "\"\"").replace("1-2", "2-4"), "2025-03-31")
        val weeks = listOf(w5, w1, w3)
        fun parse(id: String, items: List<KingosoftWeekPayload>) = KingosoftScheduleParser.aggregateWeeks(id, items, listOf(1L, 2L))
        val result = parse("one", weeks)
        val course = result.courses.single()
        assertEquals(listOf(1, 5), course.selectedWeeks)
        assertEquals(1 to 5, course.startWeek to course.endWeek)
        assertEquals(2 to 4, course.startSection to course.endSection)
        assertEquals("R", course.classroom)
        assertEquals("note", course.note)
        assertEquals(LocalDate(2025, 3, 3), result.semesterStartDate)
        assertEquals(12, result.maxSection)
        val single = parse("one", listOf(w1)).courses.single()
        assertEquals(listOf(1, 3, 5, 6), single.selectedWeeks)
        assertEquals(course.id, single.id)
        assertNotEquals(course.id, parse("two", weeks).courses.single().id)
        val otherTeacher = entry.replace("\"T\"", "\"U\"")
        assertEquals(2, parse("one", listOf(week(1, "$entry,$otherTeacher"))).courses.distinctBy { it.id }.size)
    }

    @Test
    fun mergeUpdatesOnlyRemoteFieldsAndIsIdempotent() {
        val local = Course("mine", "Art", "", "", 1, 1, 2, 1, 2, Course.WEEK_TYPE_ALL, 7)
        val remote = local.copy(id = "kg_1", colorRes = 1, note = "note", weekNotes = mapOf(1 to "quiz"),
            isCustomTime = true, customStartTime = "09:00", customEndTime = "10:00")
        val changed = local.copy(id = remote.id, name = "Biology", classroom = "R", teacher = "T", dayOfWeek = 4,
            startSection = 3, endSection = 4, startWeek = 2, endWeek = 6, selectedWeeks = listOf(2, 4, 6))
        val added = local.copy(id = "kg_new")
        val same = local.copy(id = "kg_same")
        val incoming = listOf(changed, same.copy(lastModified = 0), added)
        val result = KingosoftSyncMerger.mergeCourses(listOf(local, remote, remote.copy(id = "kg_gone"), same), incoming)
        assertTrue(result.hasChanges)
        assertEquals(listOf(1, 1, 1, 1), listOf(result.added, result.updated, result.removed, result.unchanged))
        val updated = result.merged[1]
        assertEquals(changed.copy(colorRes = remote.colorRes, note = remote.note, weekNotes = remote.weekNotes,
            isCustomTime = true, customStartTime = "09:00", customEndTime = "10:00", lastModified = updated.lastModified), updated)
        assertEquals(listOf(local, updated, same, added), result.merged)
        val second = KingosoftSyncMerger.mergeCourses(result.merged, incoming)
        assertFalse(second.hasChanges)
        assertEquals(3, second.unchanged)
        assertEquals(result.merged, second.merged)
    }
}
