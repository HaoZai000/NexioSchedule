package com.haooz.chedule.data.repo

import com.haooz.chedule.data.appearance.AppearancePrefs
import com.haooz.chedule.data.backup.encodeCourses
import com.haooz.chedule.data.backup.encodeScheduleFolders
import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.backup.encodeTimeConfig
import com.haooz.chedule.data.backup.ensureEmptyScheduleCourseEntries
import com.haooz.chedule.data.backup.normalizeFullScheduleBackup
import com.haooz.chedule.data.backup.parseTimeConfigSnapshotOrNull
import com.haooz.chedule.data.backup.shouldPreserveRestoredFolderMembership
import com.haooz.chedule.data.backup.validateSingleScheduleCourseData
import com.haooz.chedule.data.backup.weekNotes
import com.haooz.chedule.data.backup.withValidatedFullScheduleBackup
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.model.truncateNote
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_CLASS_START_TIME
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_CLASS_DND
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_CLASS_DND_MODE
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_COURSES
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_CURRENT_SCHEDULE_ID
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_CURRENT_TIME_CONFIG_ID
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_CURRENT_WEEK
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_DEFAULT_FOLDER_MIGRATED
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_DEFAULT_HOMEPAGE
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_ISLAND_NOTIFICATION
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_NEXT_DAY_REMINDER
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_NEXT_DAY_REMINDER_HOUR
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_NEXT_DAY_REMINDER_MINUTE
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_PRE_CLASS_REMINDER
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_PRE_CLASS_REMINDER_MINUTES
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_SCHEDULE_FOLDERS
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_SCHEDULE_NAMES
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_SHIFT_MODE
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_SHIFT_SELECTED_SCHEDULES
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_SHOW_NON_CURRENT_WEEK
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_SMART_WEEKEND
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_TEACHING_WEEK_REORGANIZATIONS
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_TIME_CONFIG_IDS
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_TOTAL_WEEKS
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_WIDGET_PADDING_MODE
import com.haooz.chedule.data.repo.CourseRepository.Companion.MAX_TOTAL_WEEKS
import com.haooz.chedule.data.repo.CourseRepository.Companion.SCHEDULE_KEY_PREFIX
import com.haooz.chedule.data.repo.CourseRepository.Companion.SCHEDULE_TIME_CONFIG_PREFIX
import com.haooz.chedule.data.repo.CourseRepository.Companion.TIME_CONFIG_PREFIX
import com.haooz.chedule.data.repo.CourseRepository.Companion.normalizeClassStartDate
import com.haooz.chedule.data.storage.MigrationOutcome
import com.haooz.chedule.data.util.toJsonElement

/**
 * 全量备份的导出与恢复、单课表导入。
 *
 * 只负责「数据在键值快照与实体表之间搬一个来回」；格式定义与结构校验在 `ScheduleBackup`。
 *
 * 两条硬约定：
 * - **键值里的键先全部带走，表管的那些再由表覆盖**。遍历键值不是为了给表数据兜底，
 *   而是为了不必维护一份「还有哪些键要备份」的清单 —— 漏一个就是静默丢数据。
 *   反过来，表既然管了这些键，就只有表说了算：表答不出就把键从备份里删掉，
 *   不拿键值里的旧副本顶上（那是会过期的第二真相，恢复时按旧值写回去）。
 * - **恢复后立刻重建实体表**。留一个「等下次启动再迁」的窗口，窗口里读表读到的是刚被清空的行。
 */
internal class BackupTransfer(private val host: RepoHost) {

    /** 导出一份全量备份：课表数据 + 节假日/调休。外观与提醒等设备本地设置不进备份。 */
    fun exportAllPreferences(): Map<String, Any> {
        val names = host.getScheduleNames()
        val result = mutableMapOf<String, Any>()
        for ((key, value) in host.prefs.all()) {
            if (isCombinationBackupKey(key) || isAppFeatureBackupKey(key)) continue
            when (value) {
                is String -> result[key] = value
                is Int -> result[key] = value
                is Boolean -> result[key] = value
                is Float -> result[key] = value
                is Long -> result[key] = value
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    result[key] = (value as Set<String>).toList()
                }
            }
        }
        // 键不在镜像里也要有：恢复方靠这两个键判断「有哪些课表、当前看的是哪张」
        if (KEY_SCHEDULE_NAMES !in result) result[KEY_SCHEDULE_NAMES] = encodeStringList(names)
        if (KEY_CURRENT_SCHEDULE_ID !in result) {
            result[KEY_CURRENT_SCHEDULE_ID] = host.getCurrentScheduleId()
        }
        ensureEmptyScheduleCourseEntries(result, names)
        materializeRowBackedKeys(result, names)
        result.putAll(HolidayManager.exportBackupData())
        // 文件夹用清洗后的结果：历史坏数据里的幽灵课表名不该跟着备份走
        result[KEY_SCHEDULE_FOLDERS] = encodeScheduleFolders(host.getScheduleFolders())
        return result
    }

    fun importAllPreferences(data: Map<String, Any>) {
        val normalized = normalizeFullScheduleBackup(data)
        withValidatedFullScheduleBackup(normalized) { holidayBackup ->
            restoreAllPreferences(
                data = normalized,
                holidayBackup = holidayBackup,
                preserveFolderMembership = shouldPreserveRestoredFolderMembership(normalized),
            )
        }
    }

    /**
     * 单课表导入。
     *
     * 顺序不能换：**先定作息绑定，再落课程**。`schedule.time_config_id` 是 NOT NULL 外键，
     * 锚点行必须先有合法作息可指；绑定晚于课程的话，锚点只能回退到共用配置 ——
     * 两张课表从此改一套另一套跟着变。
     */
    fun importSingleSchedule(
        scheduleName: String,
        coursesData: List<Map<String, Any>>,
        timeConfigData: Any? = null,
        classStartTime: String? = null,
        currentWeek: Int? = null,
        totalWeeks: Int? = null,
        teachingWeekReorganizations: List<TeachingWeekReorganizationRule>? = null,
        smartWeekend: Boolean? = null,
        showNonCurrentWeek: Boolean? = null,
    ) {
        val normalizedClassStart = classStartTime?.let {
            normalizeClassStartDate(it)
                ?: throw IllegalArgumentException("Invalid single-schedule semester start date")
        }
        val importedTotalWeeks = totalWeeks ?: 20
        require(importedTotalWeeks in 1..MAX_TOTAL_WEEKS) {
            "Invalid single-schedule semester week count"
        }
        teachingWeekReorganizations?.let { rules ->
            val error = TeachingWeekReorganization.validationError(rules, Int.MAX_VALUE)
            require(error == null) { error ?: "Invalid single-schedule teaching-week rules" }
            require(rules.isEmpty() || normalizedClassStart != null) {
                "A single-schedule reorganization requires a valid semester start date"
            }
        }
        // 调低总周数不会删已存的周次选择，备份里保留它们更安全
        validateSingleScheduleCourseData(coursesData, MAX_TOTAL_WEEKS)

        val names = host.getScheduleNames().toMutableList()
        if (scheduleName !in names) {
            names.add(scheduleName)
            host.saveScheduleNames(names)
        }

        var colorIndex = 0
        // 备份不导出课程 id，这里现造。合成键只有「课名+星期+起始节」，不含教室/教师/
        // 结束节/周次 —— 同一门课分两个实验室上（真实备份里就有）会撞 id。撞了的话
        // INSERT OR REPLACE 会把后一条覆盖掉前一条，末尾的行表自校验因尺寸不符失败，
        // 整单恢复回滚。所以撞 id 就补序号后缀，保证唯一。
        val usedCourseIds = mutableSetOf<String>()
        val courses = coursesData.mapNotNull { courseMap ->
            val name = courseMap["name"] as? String ?: return@mapNotNull null
            val classroom = courseMap["classroom"] as? String ?: ""
            val teacher = courseMap["teacher"] as? String ?: ""
            val dayOfWeek = (courseMap["dayOfWeek"] as? Number)?.toInt() ?: return@mapNotNull null
            val startSection = (courseMap["startSection"] as? Number)?.toInt() ?: return@mapNotNull null
            val endSection = (courseMap["endSection"] as? Number)?.toInt() ?: return@mapNotNull null
            // JSON 数组元素可能是 Double/Integer，一律走 Number
            val selectedWeeks = (courseMap["selectedWeeks"] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }
                ?: emptyList()
            // 旧备份只有 selectedWeeks：用 min/max 推断，weekType 无从还原保持 0
            val explicitStartWeek = (courseMap["startWeek"] as? Number)?.toInt()
            val explicitEndWeek = (courseMap["endWeek"] as? Number)?.toInt()
            val weekType = (courseMap["weekType"] as? Number)?.toInt() ?: 0
            val startWeek = explicitStartWeek ?: selectedWeeks.minOrNull() ?: 1
            val endWeek = maxOf(startWeek, explicitEndWeek ?: selectedWeeks.maxOrNull() ?: 20)

            val exportedColor = (courseMap["colorRes"] as? Number)?.toLong()
            val color = exportedColor ?: run {
                val c = Course.courseColors[colorIndex % Course.courseColors.size]
                colorIndex++
                c
            }
            val baseCourseId = "${scheduleName}_${name}_${dayOfWeek}_$startSection"
            var courseId = baseCourseId
            var idSuffix = 2
            while (!usedCourseIds.add(courseId)) {
                courseId = "${baseCourseId}_$idSuffix"
                idSuffix++
            }
            Course(
                id = courseId,
                scheduleId = scheduleName,
                name = name,
                classroom = classroom,
                teacher = teacher,
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                endSection = endSection,
                isCustomTime = (courseMap["isCustomTime"] as? Boolean) ?: false,
                customStartTime = courseMap["customStartTime"] as? String,
                customEndTime = courseMap["customEndTime"] as? String,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = weekType,
                colorRes = color,
                selectedWeeks = selectedWeeks,
                // 备注是后加字段：旧备份没有就按「无备注」处理，长度上限与入口一致
                note = ((courseMap["note"] as? String) ?: "").trim().truncateNote(),
                weekNotes = courseMap.weekNotes("weekNotes"),
            )
        }

        val alreadyBound = host.boundTimeConfigIdOrNull(scheduleName)
        val importedConfig = timeConfigData?.let { buildImportedTimeConfig(scheduleName, it) }
        if (importedConfig != null || alreadyBound == null) {
            // 备份带了作息就用它建一份新配置；没带就复制当前生效的那套
            // （解析不出来的退化路径：课表不至于没有时间配置）
            val newConfig = importedConfig
                ?: host.getTimeConfig(host.resolveLegacyCurrentTimeConfigId())
                    .copy(name = scheduleName, id = 0L)
            host.setScheduleTimeConfigId(scheduleName, host.addTimeConfig(newConfig))
        }

        val rows = host.rowStore()
            ?: error("单课表导入失败：课程行表没有接线 schedule=$scheduleName")
        val anchor = host.courseRowAnchor(scheduleName)
            ?: error("单课表导入拿不到锚点行：schedule=$scheduleName")
        // importAll 自带校验与导入标记，写完就是唯一真相，没有 blob 残留
        rows.importAll(anchor, courses)

        // 设置只写行，不写 prefs 镜像：读路径只认行
        if (normalizedClassStart != null || currentWeek != null || totalWeeks != null) {
            host.writeRowSettings(
                scheduleId = scheduleName,
                currentWeek = currentWeek,
                totalWeeks = totalWeeks,
                classStart = normalizedClassStart,
            )
        }
        teachingWeekReorganizations?.let { rules ->
            host.writeTeachingWeekReorganizationsRaw(
                scheduleName,
                TeachingWeekReorganization.encode(rules, Int.MAX_VALUE),
            )
        }
        smartWeekend?.let { host.setSmartWeekendFor(scheduleName, it) }
        showNonCurrentWeek?.let { host.setShowNonCurrentWeekFor(scheduleName, it) }

        // 全部写完之后再统一失效：中途失效会让后面的写读到刚清空的缓存再回填一次
        host.invalidateAllCaches()
        host.dispatchCourseChanged("restore", "")
    }

    // ── 导出：从表物化 ────────────────────────────────────────────────────────

    /**
     * 表管的键，一律以表为准。
     *
     * 覆盖面：课程、作息 ids 与 payload、绑定、三个设置字段、两个显示开关、重组规则。
     * **表答不出就从备份里删掉这个键**，不保留键值里的旧副本。
     */
    private fun materializeRowBackedKeys(result: MutableMap<String, Any>, names: List<String>) {
        val rows = host.rowStore()

        // 已删课表的残留键必须清掉：恢复校验会把它判成「属于未知课表」，整份备份都被拒
        if (names.isNotEmpty()) {
            val kept = setOf(KEY_SCHEDULE_NAMES, KEY_SCHEDULE_FOLDERS, "schedule_folder_map")
            result.keys
                .filter { key ->
                    key.startsWith("schedule_") && key !in kept &&
                        !key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX) &&
                        names.none { key.startsWith("schedule_${it}_") }
                }
                .forEach { result.remove(it) }
        }

        names.forEach { name ->
            // 读失败会抛（见 [readCoursesForBackup]）：导出一份「课表是空的」备份，
            // 恢复时等于把这张表清空，比备份失败更糟。
            val key = host.getScheduleKeyPrefix(name) + KEY_COURSES
            result[key] = encodeCourses(readCoursesForBackup(name).orEmpty())
        }

        // 作息：ids 与 payload 一起物化。表里没有配置就是没有，键值里的旧条目一并删掉。
        val configs = rows?.let {
            runCatching { it.timeConfigs() }.getOrElse { e ->
                throw IllegalStateException("备份导出：作息表读不出来", e)
            }
        }
        if (configs != null) {
            result.keys
                .filter { it.startsWith(TIME_CONFIG_PREFIX) && it != KEY_TIME_CONFIG_IDS }
                .forEach { result.remove(it) }
            val liveIds = configs.map { it.id }.filter { it > 0 }
            if (liveIds.isEmpty()) {
                result.remove(KEY_TIME_CONFIG_IDS)
            } else {
                result[KEY_TIME_CONFIG_IDS] = liveIds.joinToString(",")
                configs.mapNotNull { row ->
                    val parsed = runCatching { parseTimeConfigSnapshotOrNull(row.payload) }
                        .getOrNull() ?: return@mapNotNull null
                    "$TIME_CONFIG_PREFIX${row.id}" to
                        encodeTimeConfig(TimeConfig.sanitize(row.id, parsed))
                }.toMap().let { result.putAll(it) }
            }
        }

        // 绑定：`schedule.time_config_id` 是真相，物化成备份要的扁平键
        if (rows != null) {
            val bindings = runCatching { rows.bindings() }.getOrElse { e ->
                throw IllegalStateException("备份导出：作息绑定读不出来", e)
            }
            // 先清再整体写：已删课表的残留绑定会被恢复校验当成未知课表
            result.keys
                .filter { it.startsWith(SCHEDULE_TIME_CONFIG_PREFIX) }
                .forEach { result.remove(it) }
            bindings.forEach { (name, id) -> result["$SCHEDULE_TIME_CONFIG_PREFIX$name"] = id }
        }

        // 三个设置字段只取行里的值，**不走 getClassStartTime**：它缺值时会写今天并建行，
        // 导出过程中建行，下面物化到的就是默认列 —— 等于把用户设置改成默认值。
        names.forEach { name ->
            val prefix = host.getScheduleKeyPrefix(name)
            val row = host.rowSettingsOf(name)
            if (row == null) {
                // 没有行 = 这张课表没有任何设置，键值里那份是别处留下的旧值
                result.remove("${prefix}$KEY_CURRENT_WEEK")
                result.remove("${prefix}$KEY_TOTAL_WEEKS")
                result.remove("${prefix}$KEY_CLASS_START_TIME")
                return@forEach
            }
            result["${prefix}$KEY_CURRENT_WEEK"] = row.currentWeek
            result["${prefix}$KEY_TOTAL_WEEKS"] = row.totalWeeks
            if (row.classStart != null) {
                result["${prefix}$KEY_CLASS_START_TIME"] = row.classStart
            } else {
                result.remove("${prefix}$KEY_CLASS_START_TIME")
            }
        }

        // 显示开关：行不存在就不写。写默认值等于替用户伪造一次设置，
        // 而镜像里如果还留着旧值，上面的遍历已经带进来了。
        names.forEach { name ->
            val prefix = host.getScheduleKeyPrefix(name)
            val display = runCatching { rows?.displaySettingsOf(name) }.getOrNull()
            if (display == null) {
                result.remove("${prefix}$KEY_SMART_WEEKEND")
                result.remove("${prefix}$KEY_SHOW_NON_CURRENT_WEEK")
                return@forEach
            }
            result["${prefix}$KEY_SMART_WEEKEND"] = display.smartWeekend
            result["${prefix}$KEY_SHOW_NON_CURRENT_WEEK"] = display.showNonCurrentWeek
        }

        // 重组规则必须走读路径：迁移会把 prefs 副本删掉，这里自己拼键等于「备份里永远没有这一块」
        names.forEach { name ->
            val prefix = host.getScheduleKeyPrefix(name)
            val raw = host.teachingWeekReorganizationsRawOf(name)
            if (raw == null) result.remove("${prefix}$KEY_TEACHING_WEEK_REORGANIZATIONS")
            else result["${prefix}$KEY_TEACHING_WEEK_REORGANIZATIONS"] = raw
        }
    }

    /**
     * 备份专用的课程读法。
     *
     * 与 `loadCourses` 的差别：后者把读失败当空表（界面宁可空也不能崩），备份不能 ——
     * 那会把一张有课的课表导成 `[]`，恢复时直接清空，所以读失败在这里抛。
     * 未导入的课表返回空表而**不顺手导入**：导出过程中建行，会把刚建出来的默认列
     * 当成用户设置写进备份。没有行表的宿主退回 blob。
     */
    private fun readCoursesForBackup(scheduleId: String): List<Course>? {
        val rows = host.rowStore() ?: return host.readCoursesFromBlob(scheduleId)
        return runCatching {
            if (rows.isImported(scheduleId)) rows.load(scheduleId) else emptyList()
        }.getOrElse { e ->
            throw IllegalStateException("备份导出：$scheduleId 的课程读不出来", e)
        }
    }

    // ── 恢复 ──────────────────────────────────────────────────────────────────

    private fun restoreAllPreferences(
        data: Map<String, Any>,
        holidayBackup: HolidayManager.BackupData,
        preserveFolderMembership: Boolean,
    ) {
        host.prefs.edit {
            // 先把「一套课表」会占用的键整体清掉再写回：留着旧值就是新旧混住
            for ((key) in host.prefs.all()) {
                if (key.startsWith(SCHEDULE_KEY_PREFIX) || key.startsWith(TIME_CONFIG_PREFIX) ||
                    key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX)
                ) {
                    remove(key)
                }
            }
            remove(KEY_SCHEDULE_NAMES)
            remove(KEY_SCHEDULE_FOLDERS)
            // 新备份自带文件夹归属；只有旧备份才需要再跑一次归档迁移
            if (preserveFolderMembership) putBoolean(KEY_DEFAULT_FOLDER_MIGRATED, true)
            else remove(KEY_DEFAULT_FOLDER_MIGRATED)
            remove(KEY_CURRENT_SCHEDULE_ID)
            remove(KEY_SHIFT_MODE)
            remove(KEY_SHIFT_SELECTED_SCHEDULES)
            remove(KEY_TIME_CONFIG_IDS)
            remove(KEY_CURRENT_TIME_CONFIG_ID)
            remove(KEY_DEFAULT_HOMEPAGE)

            for ((key, value) in data) {
                if (key == HolidayManager.BACKUP_KEY ||
                    key == HolidayManager.BACKUP_EXCLUSION_KEY ||
                    key == HolidayManager.BACKUP_BEFORE_EXCLUSION_KEY
                ) {
                    continue
                }
                // 外观与提醒等设备本地设置不进备份，恢复时也不覆盖本机的对应配置
                if (isCombinationBackupKey(key) || isAppFeatureBackupKey(key)) continue
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Number -> {
                        val numVal = value.toDouble()
                        val longVal = numVal.toLong()
                        val intVal = numVal.toInt()
                        // 配置 id、绑定 id 与时间戳必须按 Long 恢复，按 Int 存会丢精度
                        if (key == KEY_CURRENT_TIME_CONFIG_ID ||
                            key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX) ||
                            key.endsWith("_last_modified")
                        ) {
                            putLong(key, longVal)
                        } else if (numVal == intVal.toDouble()) {
                            putInt(key, intVal)
                        } else {
                            putFloat(key, numVal.toFloat())
                        }
                    }

                    is List<*> -> {
                        // Set<String> 导出为 List，这里还原成 StringSet（不能写成 JSON 字符串）
                        putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }
        }
        HolidayManager.restoreBackupData(holidayBackup)
        // 行表是真相，会整体作废：不删的话恢复完读到的还是恢复前的课表
        host.rowStore()?.let { rows ->
            runCatching { rows.invalidateAll() }
                .onFailure { NexioLog.e(TAG, "恢复备份后作废课程行表失败，可能读到恢复前的课表", it) }
        }
        host.invalidateAllCaches()
        if (!preserveFolderMembership) host.migrateIntoDefaultFolder()
        // 恢复出的是另一套数据：当场重建成实体表，不留「等下次启动」的窗口
        if (host.rowStore() != null) {
            when (val outcome = host.migrateLegacyEntitiesOnce()) {
                is MigrationOutcome.Incomplete ->
                    NexioLog.e(TAG, "恢复备份后重建实体表未完成，下次启动重试：${outcome.failed}")
                MigrationOutcome.NoRowStore ->
                    NexioLog.e(TAG, "恢复备份后重建实体表失败：课程行表没有接线")
                else -> Unit
            }
        }
        host.dispatchCourseChanged("restore", "")
    }

    // ── 单课表备份里的作息 ────────────────────────────────────────────────────

    /**
     * 从单课表备份还原作息；认不出格式返回 null（调用方退回复制当前课表）。
     *
     * 两种形态都收：整份 JSON **字符串**，或扁平 **Map**（当前导出格式）。
     */
    private fun buildImportedTimeConfig(scheduleName: String, raw: Any): TimeConfig? =
        parseTimeConfigSnapshot(raw)?.copy(name = scheduleName, id = 0L)

    private fun parseTimeConfigSnapshot(raw: Any): TimeConfig? {
        // 字符串要按「它本身就是 JSON 文本」解析。走 toJsonElement 会被包成字符串字面量，
        // 解析出来是 primitive 而不是对象，于是所有字符串形态的作息都被判成损坏。
        val json = if (raw is String) {
            raw
        } else {
            runCatching { toJsonElement(raw).toString() }.getOrNull() ?: return null
        }
        val parsed = parseTimeConfigSnapshotOrNull(json) ?: return null
        return TimeConfig.sanitize(0L, parsed)
    }

    // ── 不进备份的键 ──────────────────────────────────────────────────────────

    /**
     * 外观键不进全量备份。外观已迁到 [AppearancePrefs] 的独立存储，
     * 这里保留判定只为兜住历史备份里混入的旧键（`combination_*` / `comb_*` / `wallpaper_*`）。
     */
    private fun isCombinationBackupKey(key: String): Boolean =
        key == AppearancePrefs.FILE_STYLE_KEY ||
            key.startsWith("combination_") ||
            key.startsWith("comb_") ||
            key.startsWith("wallpaper_")

    /** 提醒、勿扰、小组件等跟设备强相关的设置不进全量备份。 */
    private fun isAppFeatureBackupKey(key: String): Boolean =
        key == KEY_PRE_CLASS_REMINDER ||
            key == KEY_PRE_CLASS_REMINDER_MINUTES ||
            key == KEY_NEXT_DAY_REMINDER ||
            key == KEY_NEXT_DAY_REMINDER_HOUR ||
            key == KEY_NEXT_DAY_REMINDER_MINUTE ||
            key == KEY_ISLAND_NOTIFICATION ||
            key == KEY_CLASS_DND ||
            key == KEY_CLASS_DND_MODE ||
            key == KEY_WIDGET_PADDING_MODE

    private companion object {
        const val TAG = "BackupTransfer"
    }
}
