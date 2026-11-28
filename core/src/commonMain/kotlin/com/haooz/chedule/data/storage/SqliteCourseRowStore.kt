package com.haooz.chedule.data.storage

import com.haooz.chedule.data.backup.decodeStringList
import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.db.Course as CourseRow
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.NexioLog

/**
 * [CourseRowStore] 的实现。只用 [NexioDatabase] 与 `transactionWithResult`，无平台 API，
 * 所以放在 commonMain：三端一份逻辑，宿主机也能跑真 SQL。
 *
 * 一次 [writeDiff] 一个事务，中途失败整体回滚。
 * ⚠ `upsertCourse` 是 `INSERT OR REPLACE`，会级联删掉子表，必须先 upsert 课程再写周次。
 */
class SqliteCourseRowStore(private val database: NexioDatabase) : CourseRowStore {

    private val q get() = database.scheduleQueries

    /**
     * ⚠ 不许吞异常：读标记失败若被当成「未导入」，调用方会从（已被删掉的）blob 重新导入，
     * 而 blob 不存在时就是「导入一张空表」—— 直接把这张课表清空。读不了就让它抛。
     */
    override fun isImported(scheduleId: String): Boolean =
        q.readMeta(importKey(scheduleId)).executeAsOneOrNull() != null

    override fun load(scheduleId: String): List<Course> = loadInTx(scheduleId)

    override fun importAll(anchor: CourseRowAnchor, courses: List<Course>) {
        database.transactionWithResult {
            ensureAnchors(anchor)
            // 先清空再灌，避免上次失败留下的半截行
            q.deleteCoursesOfSchedule(anchor.scheduleId)
            courses.forEach { writeCourse(anchor.scheduleId, it) }

            // 自校验：对拍不过就抛异常，事务回滚且不写导入标记，读取继续走 JSON
            val readBack = loadInTx(anchor.scheduleId)
            if (!sameCourseContent(courses, readBack)) {
                val lost = courses.size - readBack.size
                error(
                    "课程行表自校验失败（schedule=${anchor.scheduleId}）：写入 ${courses.size} 门，" +
                        "读回 ${readBack.size} 门（差 $lost），已整体回滚"
                )
            }
            q.writeMeta(importKey(anchor.scheduleId), courses.size.toString())
        }
        NexioLog.w(TAG, "课程已导入行表：schedule=${anchor.scheduleId}，${courses.size} 门")
    }

    override fun writeDiff(anchor: CourseRowAnchor, previous: List<Course>, next: List<Course>) {
        val diff = diffCourses(previous, next)
        // 无变化不开事务
        if (diff.deleted.isEmpty() && diff.updated.isEmpty()) return

        database.transactionWithResult {
            ensureAnchors(anchor)
            diff.deleted.forEach { q.deleteCourse(it) }
            diff.updated.forEach { writeCourse(anchor.scheduleId, it) }
        }
    }

    override fun renameSchedule(oldId: String, newId: String) {
        // 同名等于「REPLACE 自己」：先级联删光课程，再把空行插回来、搬 0 门课、最后连锚点一起删掉，
        // 而导入标记会被原样写回 —— 课表变空且再也不会从 JSON 重建。UI 挡了同名，存储层也得挡。
        if (oldId == newId) return
        database.transactionWithResult {
            val flag = q.readMeta(importKey(oldId)).executeAsOneOrNull()
            // 顺序：先建新锚点（课程的 schedule_id 外键要指向它），再搬课程，最后删旧锚点
            q.copyScheduleRow(newId, newId, oldId)
            q.moveCoursesToSchedule(newId, oldId)
            q.deleteSchedule(oldId)
            q.deleteMeta(importKey(oldId))
            // 标记一起搬，否则新名字被判成未导入，会从冻结的 JSON 重建
            if (flag != null) q.writeMeta(importKey(newId), flag)
        }
    }

    override fun deleteSchedule(scheduleId: String) {
        database.transactionWithResult {
            // course 由 CASCADE 跟着删；标记一起清
            q.deleteSchedule(scheduleId)
            q.deleteMeta(importKey(scheduleId))
        }
    }

    override fun bindingOf(scheduleId: String): Long? =
        q.selectScheduleBinding(scheduleId).executeAsOneOrNull()

    override fun setBinding(anchor: CourseRowAnchor) {
        database.transactionWithResult {
            // 外键目标先就位
            ensureAnchors(anchor)
            q.updateScheduleBinding(anchor.timeConfigId, anchor.scheduleId)
        }
    }

    override fun bindings(): Map<String, Long> =
        q.selectAllScheduleBindings().executeAsList().associate { it.id to it.time_config_id }

    override fun settingsOf(scheduleId: String): ScheduleRowSettings? =
        q.selectScheduleSettings(scheduleId).executeAsOneOrNull()?.let {
            ScheduleRowSettings(
                currentWeek = it.current_week.toInt(),
                totalWeeks = it.total_weeks.toInt(),
                classStart = it.class_start,
            )
        }

    override fun writeSettings(
        scheduleId: String,
        currentWeek: Int?,
        totalWeeks: Int?,
        classStart: String?,
    ) {
        q.updateScheduleSettings(
            currentWeek = currentWeek?.toLong(),
            totalWeeks = totalWeeks?.toLong(),
            classStart = classStart,
            id = scheduleId,
        )
    }

    override fun timeConfigs(): List<StoredTimeConfig> =
        q.selectAllTimeConfigs().executeAsList().map {
            StoredTimeConfig(id = it.id, name = it.name, payload = it.payload)
        }

    override fun upsertTimeConfig(row: StoredTimeConfig) {
        database.transactionWithResult {
            q.updateTimeConfigRow(name = row.name, payload = row.payload, id = row.id)
            q.insertTimeConfigRow(id = row.id, name = row.name, payload = row.payload)
        }
    }

    override fun deleteTimeConfigRow(id: Long) {
        q.deleteTimeConfig(id)
    }

    /**
     * 标记里存的是**块版本号**，不是布尔。读到旧值（或读不到）就重跑一遍迁移 ——
     * 迁移本身是幂等的，重跑只多一次遍历，漏跑却是永久丢数据。
     */
    override fun isFullyMigrated(): Boolean =
        q.readMeta(META_ENTITY_VERSION).executeAsOneOrNull()?.toIntOrNull()
            ?.let { it >= ENTITY_MIGRATION_VERSION } ?: false

    override fun markFullyMigrated() {
        q.writeMeta(META_ENTITY_VERSION, ENTITY_MIGRATION_VERSION.toString())
    }

    // ── 显示开关 ──────────────────────────────────────────────────────────────

    override fun displaySettingsOf(scheduleId: String): ScheduleDisplaySettings? =
        q.selectScheduleDisplaySettings(scheduleId).executeAsOneOrNull()?.let {
            ScheduleDisplaySettings(
                smartWeekend = it.smart_weekend != 0L,
                showNonCurrentWeek = it.show_non_current_week != 0L,
            )
        }

    override fun writeSmartWeekend(scheduleId: String, smart: Boolean) {
        q.updateSmartWeekend(if (smart) 1L else 0L, scheduleId)
    }

    override fun writeShowNonCurrentWeek(scheduleId: String, show: Boolean) {
        q.updateShowNonCurrentWeek(if (show) 1L else 0L, scheduleId)
    }

    // ── 教学周重组规则 ────────────────────────────────────────────────────────

    override fun teachingWeekReorganizationsOf(scheduleId: String): String? =
        q.selectTeachingWeekReorganizations(scheduleId).executeAsOneOrNull()

    override fun writeTeachingWeekReorganizations(scheduleId: String, payload: String) {
        q.upsertTeachingWeekReorganizations(scheduleId, payload)
    }

    // ── 文件夹 ────────────────────────────────────────────────────────────────

    override fun folders(): List<StoredFolder> =
        q.selectAllFolders().executeAsList().map { row ->
            StoredFolder(
                id = row.id,
                name = row.name,
                schedules = runCatching { decodeStringList(row.payload) }
                    .onFailure { NexioLog.e(TAG, "文件夹内容解析失败：${row.id}", it) }
                    .getOrThrow(),
                sortOrder = row.sort_order.toInt(),
            )
        }

    override fun upsertFolder(row: StoredFolder) {
        val payload = encodeStringList(row.schedules)
        database.transactionWithResult {
            q.updateFolderRow(
                name = row.name,
                payload = payload,
                sortOrder = row.sortOrder.toLong(),
                id = row.id,
            )
            q.insertFolderRow(
                id = row.id,
                name = row.name,
                payload = payload,
                sortOrder = row.sortOrder.toLong(),
            )
        }
    }

    override fun deleteFolder(id: String) {
        q.deleteFolder(id)
    }

    override fun invalidateAll() {
        database.transactionWithResult {
            q.deleteAllCourses()
            q.deleteAllSchedules()
            // 课表删完之后才删文件夹：schedule.folder_id 是 SET NULL，顺序反了会白跑一遍
            q.deleteAllTeachingWeekReorganizations()
            q.deleteAllFolders()
            q.deleteAllTimeConfigs()
            q.deleteCourseImportFlags()
            // 恢复出的是另一套数据：整体迁移标记也要清，否则新数据会被当成「已经迁完」
            q.deleteMeta(META_ENTITY_VERSION)
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    /**
     * 读一个课表的全部课程（含周次 / 周备注）。
     *
     * 事务内外都可用：读的是当前连接快照，事务内调用看到的就是该事务已写入的内容 ——
     * [importAll] 的自校验依赖这一点。周次 / 周备注各一条查询取整表再分组，不按课循环查。
     */
    private fun loadInTx(scheduleId: String): List<Course> {
        val courses = q.selectCoursesOf(scheduleId).executeAsList()
        // 空表省掉两次查询：大多数课表没有课程时这是首屏路径
        if (courses.isEmpty()) return emptyList()
        val weeks = q.selectWeeksOfSchedule(scheduleId).executeAsList()
            .groupBy({ it.course_id }, { it.week.toInt() })
        val notes = q.selectWeekNotesOfSchedule(scheduleId).executeAsList()
            .groupBy({ it.course_id }, { it.week.toInt() to it.note })
        return courses.map { row ->
            row.toCourse(
                weeks = weeks[row.id].orEmpty(),
                weekNotes = notes[row.id].orEmpty().toMap(),
            )
        }
    }

    /** 补齐外键目标行。两者都是 INSERT OR IGNORE，可重复调用。 */
    private fun ensureAnchors(anchor: CourseRowAnchor) {
        q.ensureTimeConfigRow(
            id = anchor.timeConfigId,
            name = anchor.timeConfigName,
            payload = anchor.timeConfigPayload,
        )
        q.ensureScheduleRow(
            id = anchor.scheduleId,
            name = anchor.scheduleId,
            time_config_id = anchor.timeConfigId,
            sort_order = anchor.sortOrder.toLong(),
            current_week = anchor.currentWeek.toLong(),
            total_weeks = anchor.totalWeeks.toLong(),
            class_start = anchor.classStart,
        )
    }

    /** 写一门课 + 它的周次 / 周备注。顺序见类注释。 */
    private fun writeCourse(scheduleId: String, course: Course) {
        q.upsertCourse(
            id = course.id,
            // 归属只认调用方给的课表：course 自带的 scheduleId 可能是别的课表（复制 / 迁移残留），
            // 跟着它写会把行挂到别的课表名下，这边读不到、那边多一门，还会 REPLACE 掉对面同 id 的行
            schedule_id = scheduleId,
            name = course.name,
            classroom = course.classroom,
            teacher = course.teacher,
            day_of_week = course.dayOfWeek.toLong(),
            start_section = course.startSection.toLong(),
            end_section = course.endSection.toLong(),
            start_week = course.startWeek.toLong(),
            end_week = course.endWeek.toLong(),
            week_type = course.weekType.toLong(),
            color_res = course.colorRes,
            note = course.note,
            last_modified = course.lastModified,
            is_custom_time = if (course.isCustomTime) 1L else 0L,
            custom_start_time = course.customStartTime,
            custom_end_time = course.customEndTime,
        )
        // REPLACE 已级联删掉子表，这里重新写
        course.selectedWeeks.distinct().forEach { q.insertWeek(course.id, it.toLong()) }
        course.weekNotes.forEach { (week, note) -> q.insertWeekNote(course.id, week.toLong(), note) }
    }

    private fun CourseRow.toCourse(weeks: List<Int>, weekNotes: Map<Int, String>): Course = Course(
        id = id,
        name = name,
        classroom = classroom,
        teacher = teacher,
        dayOfWeek = day_of_week.toInt(),
        startSection = start_section.toInt(),
        endSection = end_section.toInt(),
        startWeek = start_week.toInt(),
        endWeek = end_week.toInt(),
        weekType = week_type.toInt(),
        colorRes = color_res,
        selectedWeeks = weeks,
        scheduleId = schedule_id,
        lastModified = last_modified,
        isCustomTime = is_custom_time != 0L,
        customStartTime = custom_start_time,
        customEndTime = custom_end_time,
        note = note,
        weekNotes = weekNotes,
    )

    private fun importKey(scheduleId: String) = "$META_IMPORTED_PREFIX$scheduleId"

    private companion object {
        const val TAG = "SqliteCourseRowStore"
        /** 与 SqliteStorage 的 `sp_imported:` 前缀区分：一个是偏好文件、一个是课程行。 */
        const val META_IMPORTED_PREFIX = "courses_imported:"
        /** 作息配置是否已从 prefs 同步进表。 */
        const val META_TIME_CONFIGS_SYNCED = "time_configs_synced"
        /**
         * 「全部历史数据已搬进实体表」的标记。值是 [ENTITY_MIGRATION_VERSION]。
         *
         * 1.6.7 用的是 `entity_tables_v1 = "1"`，不带版本语义，1.6.8 新增三块数据后
         * 老用户会被判成「已迁完」而跳过 —— 所以这里换了键名，旧标记自然作废。
         * 以后再加块：只把 [ENTITY_MIGRATION_VERSION] +1，不要改键名。
         */
        const val META_ENTITY_VERSION = "entity_tables_version"
        /** 迁移块版本。新增一块要搬的数据就 +1。 */
        const val ENTITY_MIGRATION_VERSION = 3
    }
}
