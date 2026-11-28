package com.haooz.chedule.data.sync.kingosoft

import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.platform.displayText
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.repo.CourseRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

data class KingosoftSyncOutcome(val hasChanges: Boolean, val summary: String)

internal class KingosoftSyncTarget(
    val scheduleId: String, val isCurrent: () -> Boolean,
    val readCourses: () -> List<Course>, val saveCourses: (List<Course>) -> Unit,
) {
    constructor(scheduleId: String, repository: CourseRepository) : this(
        scheduleId, { repository.getCurrentScheduleId() == scheduleId },
        { repository.getCoursesForSchedule(scheduleId) }, { repository.saveCourses(it, expectedScheduleId = scheduleId) },
    )
}

object KingosoftScheduleSyncer {
    private val lock = Mutex()
    private val _activeSchedule = MutableStateFlow<String?>(null)
    val activeSchedule = _activeSchedule.asStateFlow()

    suspend fun sync(scheduleId: String, repository: CourseRepository): KingosoftSyncOutcome = lock.withLock {
        syncLocked(KingosoftSyncTarget(scheduleId, repository), KingosoftSyncStore.getConfig(scheduleId))
    }

    suspend fun importAndSync(
        scheduleId: String, config: KingosoftSyncConfig, repository: CourseRepository = CourseRepository(),
    ): KingosoftSyncOutcome = importAndSync(KingosoftSyncTarget(scheduleId, repository), config, ::fetchWeeks)

    internal suspend fun importAndSync(
        target: KingosoftSyncTarget, config: KingosoftSyncConfig,
        fetchWeeks: suspend (KingosoftSyncConfig) -> List<KingosoftWeekPayload>,
    ): KingosoftSyncOutcome = lock.withLock {
        config.validate()
        val previous = KingosoftSyncStore.getConfig(target.scheduleId)
        val effectiveConfig = config.copy(syncScopeId = previous.syncScopeId.ifEmpty { randomUuidV4() })
        syncLocked(target, previous, effectiveConfig, fetchWeeks)
    }

    suspend fun syncIfDue(
        scheduleId: String, repository: CourseRepository, minIntervalMs: Long = 6L * 3600_000L,
    ): KingosoftSyncOutcome? = lock.withLock {
        val config = KingosoftSyncStore.getConfig(scheduleId)
        val last = KingosoftSyncStore.lastSyncAtMillis(scheduleId)
        val elapsed = Clock.System.now().toEpochMilliseconds() - last
        if (!config.autoSyncEnabled || !config.isComplete || (last != 0L && elapsed in 0 until minIntervalMs)) null
        else syncLocked(KingosoftSyncTarget(scheduleId, repository), config)
    }

    private suspend fun syncLocked(
        target: KingosoftSyncTarget, previous: KingosoftSyncConfig, importedConfig: KingosoftSyncConfig? = null,
        fetchWeeks: suspend (KingosoftSyncConfig) -> List<KingosoftWeekPayload> = ::fetchWeeks,
    ): KingosoftSyncOutcome {
        val config = importedConfig ?: previous.also { it.validate() }
        require(config.syncScopeId.isNotBlank()) { displayText("请重新保存同步配置", "Save the sync settings again") }
        val scheduleId = target.scheduleId
        _activeSchedule.value = scheduleId
        try {
            val weeks = fetchWeeks(config)
            val parsed = KingosoftScheduleParser.aggregateWeeks(scheduleId, weeks, Course.courseColors, config.syncScopeId)
            check(parsed.courses.isNotEmpty()) {
                displayText("教务系统没有返回有效课程，原课表未改动", "No valid courses returned. Your schedule was not changed.")
            }
            val merge = KingosoftSyncMerger.mergeCourses(target.readCourses(), parsed.courses)
            currentCoroutineContext().ensureActive()
            check(target.isCurrent() && KingosoftSyncStore.getConfig(scheduleId) == previous) {
                displayText("课表或同步配置已更改，请重新同步", "Schedule or sync settings changed. Sync again.")
            }
            if (importedConfig != null || merge.hasChanges) target.saveCourses(merge.merged)
            val summary = if (merge.hasChanges) displayText(
                "新增 ${merge.added} 门，更新 ${merge.updated} 门，移除 ${merge.removed} 门",
                "${merge.added} added, ${merge.updated} updated, ${merge.removed} removed",
            ) else displayText("课表已是最新", "Schedule is up to date")
            currentCoroutineContext().ensureActive()
            KingosoftSyncStore.recordSuccess(scheduleId, Clock.System.now().toEpochMilliseconds(), summary, importedConfig)
            return KingosoftSyncOutcome(merge.hasChanges, summary)
        } finally {
            _activeSchedule.value = null
        }
    }

    private suspend fun fetchWeeks(config: KingosoftSyncConfig): List<KingosoftWeekPayload> {
        val http = createHttpService(HttpTimeouts(connectSeconds = 15, readSeconds = 20))
        val url = config.jwBaseUrl.trimEnd('/') + "/wap/mycourseschedule.action"
        val user = "userid=${config.userId}&usertype=${config.userType}"
        suspend fun fetch(week: Int?): KingosoftWeekPayload {
            val plain = if (week == null) user else "$user&week=$week"
            val response = http.post(url, KingosoftCodec.buildFormBody(plain, config.encrptKey), "application/x-www-form-urlencoded")
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return KingosoftScheduleParser.parseWeek(response.text)
        }

        val term = fetch(null)
        return (1..term.maxzc).chunked(4).flatMap { batch ->
            coroutineScope {
                batch.map { w -> async {
                    fetch(w).also {
                        check(it.zc == w && it.maxzc == term.maxzc && it.xn == term.xn && it.xq == term.xq) {
                            displayText("第 ${w} 周课表的学期或周次不匹配", "Term or week mismatch for week $w")
                        }
                    }
                } }.awaitAll()
            }
        }
    }
}
