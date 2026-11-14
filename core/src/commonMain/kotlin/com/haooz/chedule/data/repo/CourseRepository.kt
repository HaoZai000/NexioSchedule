package com.haooz.chedule.data.repo

import com.haooz.chedule.data.backup.decodeCourses
import com.haooz.chedule.data.backup.decodeStringList
import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.backup.encodeTimeConfig
import com.haooz.chedule.data.backup.parseTimeConfigSnapshotOrNull
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.TeachingWeekPosition
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.PeriodTimeSource
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.model.truncateNote
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.currentDeviceInfo
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.platform.synchronizedOn
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseRowAnchor
import com.haooz.chedule.data.storage.CourseRowStore
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.storage.KeyValueStore
import com.haooz.chedule.data.storage.LegacyEntityMigration
import com.haooz.chedule.data.storage.MigrationOutcome
import com.haooz.chedule.data.storage.ScheduleFolder
import com.haooz.chedule.data.storage.ScheduleRowSettings
import com.haooz.chedule.data.storage.StoredTimeConfig
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.data.util.CourseTimeSpan
import com.haooz.chedule.data.util.formatSlashDate
import com.haooz.chedule.data.util.todayLocalDate
import kotlin.concurrent.Volatile
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** 课程数据仓库（单例）：课程读写走行表，设置项走键值存储。 */
class CourseRepository private constructor() : PeriodTimeSource {

    private val prefs: KeyValueStore = AppStorage.store(PREFS_NAME)

    /** 缓存一律不可变 Map + 整体替换：读不加锁，写持 [cacheLock]。备份与预热在 IO 线程并发读。 */
    private val cacheLock = Any()

    @Volatile
    private var courseCache: Map<String, List<Course>> = emptyMap()

    @Volatile
    private var occupiedWeeksCache: Map<String, Set<Int>> = emptyMap()
    // getPeriodTimes 等高频路径的配置缓存
    @Volatile
    private var timeConfigCache: Map<Long, TimeConfig> = emptyMap()
    @Volatile
    private var timeConfigIdsCache: List<Long>? = null
    @Volatile
    private var globalSectionTimesCache: Map<Int, String>? = null

    /**
     * 当天生效的作息（`TimeConfig.effective()` 的结果）。`effective()` 要把作息方案逐条转成
     * 对象、排序、再整份 copy 一次；`CourseTimeResolver` 每门课起止时间各取一遍三个时段，
     * 于是「一门课 6 次深拷贝」。按配置 id 缓存当天的结果后只剩查表。
     */
    @Volatile
    private var effectiveConfigCache: Map<Long, EffectiveConfig> = emptyMap()

    /** 「课表 → 解析后的教学周重组规则」。键里带原始 JSON，存储变了自动重解析，不用手动失效。 */
    @Volatile
    private var reorganizationCache: Map<String, CachedReorganizations> = emptyMap()

    /** 「课表 → 规范化后的开学日」。每次教学周换算都要解析一遍，值只在写入时变。 */
    @Volatile
    private var classStartCache: Map<String, String> = emptyMap()

    /** 「配置 id → 编码好的 JSON」。课程每次保存都要拿它当锚点 payload，不能回回重编码整份作息。 */
    @Volatile
    private var encodedTimeConfigCache: Map<Long, EncodedTimeConfig> = emptyMap()

    /**
     * 「课表 → 绑定配置 id」，0 表示未绑定。[getScheduleTimeConfigId] 在 draw 期被反复调用。
     *
     * 和代次绑成一个不可变快照，不能只存 Map：读线程查表期间写线程改了绑定并清空缓存，
     * 读线程回来把旧值写回，缓存就永久脏了（表现：改了绑定界面一直显示旧的，重启才好）。
     * 写入换代次，读线程只在代次没变时落值。
     */
    @Volatile
    private var bindingSnapshot = BindingSnapshot(0L, emptyMap())

    private data class BindingSnapshot(val generation: Long, val values: Map<String, Long>)

    /** 「课表 → 当前周 / 总周数 / 开课日」。只有 `schedule` 行一个来源。 */
    @Volatile
    private var scheduleSettingsCache: Map<String, ScheduleRowSettings> = emptyMap()

    /** 作息是否已从表灌进 [timeConfigCache]（进程内一次）。 */
    @Volatile
    private var timeConfigsHydrated = false

    /**
     * 本进程内「从 blob 导入行表」失败过的课表。坏库上不设它，每次读都会重试一遍导入
     * （抛一次异常 + 解析一次 JSON），首屏滚动时就是每帧一次。
     */
    @Volatile
    private var blobImportFailed: Set<String> = emptySet()

    /**
     * 迁移器的仓库侧能力。
     *
     * 只递出去搬运需要的动作：键名规则、锚点、缓存都留在仓库里。
     * 迁移是「绕过业务入口的直写」，所以写完必须显式作废对应缓存。
     */
    private val migrationHost = object : LegacyEntityMigration.Host {
        override fun scheduleNames() = getScheduleNames()

        override fun scheduleKeyPrefix(scheduleId: String) = getScheduleKeyPrefix(scheduleId)

        override fun loadCourses(scheduleId: String) = getCoursesForSchedule(scheduleId)

        override fun timeConfig(id: Long) = getTimeConfig(id)

        override fun addTimeConfig(config: TimeConfig) = this@CourseRepository.addTimeConfig(config)

        override fun legacyGlobalTimeConfigId() = resolveLegacyCurrentTimeConfigId()

        override fun anchorFor(scheduleId: String, timeConfigId: Long) =
            rowAnchorFor(scheduleId, timeConfigId)

        override fun createScheduleRowIfAbsent(scheduleId: String) {
            CourseStorage.rows?.let { this@CourseRepository.createScheduleRowIfAbsent(it, scheduleId) }
        }

        override fun onTimeConfigsWritten() {
            synchronizedOn(cacheLock) {
                timeConfigsHydrated = false
                timeConfigIdsCache = null
                timeConfigCache = emptyMap()
            }
        }

        override fun onBindingsWritten() {
            invalidateBindingCache()
        }

        override fun folders() = getScheduleFolders()

        override fun saveFolders(folders: List<ScheduleFolder>) = saveScheduleFolders(folders)
    }

    /**
     * 协作者（[BackupTransfer] 等）看到的仓库。
     *
     * 只递出去它们真正需要的动作：键名规则、缓存失效、变更通知都只有这一份实现。
     * 声明必须在 [init] 之前 —— init 里就会用到协作者（默认文件夹归档）。
     */
    private val host: RepoHost = object : RepoHost {
        override val prefs: KeyValueStore get() = this@CourseRepository.prefs

        override fun rowStore(): CourseRowStore? = CourseStorage.rows

        override fun getScheduleNames(): List<String> = this@CourseRepository.getScheduleNames()

        override fun saveScheduleNames(names: List<String>) =
            this@CourseRepository.saveScheduleNames(names)

        override fun getCurrentScheduleId(): String = this@CourseRepository.getCurrentScheduleId()

        override fun getScheduleKeyPrefix(scheduleId: String): String =
            this@CourseRepository.getScheduleKeyPrefix(scheduleId)

        override fun getScheduleFolders(): List<ScheduleFolder> =
            this@CourseRepository.getScheduleFolders()

        override fun rowSettingsOf(scheduleId: String): ScheduleRowSettings? =
            this@CourseRepository.rowSettingsOf(scheduleId)

        override fun writeRowSettings(
            scheduleId: String,
            currentWeek: Int?,
            totalWeeks: Int?,
            classStart: String?,
        ): Boolean = this@CourseRepository.writeRowSettings(
            scheduleId = scheduleId,
            currentWeek = currentWeek,
            totalWeeks = totalWeeks,
            classStart = classStart,
        )

        override fun teachingWeekReorganizationsRawOf(scheduleId: String): String? =
            this@CourseRepository.teachingWeekReorganizationsRawOf(scheduleId)

        override fun writeTeachingWeekReorganizationsRaw(scheduleId: String, raw: String): Boolean =
            this@CourseRepository.writeTeachingWeekReorganizationsRaw(scheduleId, raw)

        override fun setSmartWeekendFor(scheduleId: String, smart: Boolean) =
            this@CourseRepository.setSmartWeekendFor(scheduleId, smart)

        override fun setShowNonCurrentWeekFor(scheduleId: String, show: Boolean) =
            this@CourseRepository.setShowNonCurrentWeekFor(scheduleId, show)

        override fun readCoursesFromBlob(scheduleId: String): List<Course>? =
            this@CourseRepository.readCoursesFromBlob(scheduleId)

        override fun courseRowAnchor(scheduleId: String): CourseRowAnchor? =
            this@CourseRepository.courseRowAnchor(scheduleId)

        override fun boundTimeConfigIdOrNull(scheduleId: String): Long? =
            this@CourseRepository.boundTimeConfigIdOrNull(scheduleId)

        override fun resolveLegacyCurrentTimeConfigId(): Long =
            this@CourseRepository.resolveLegacyCurrentTimeConfigId()

        override fun getTimeConfig(id: Long): TimeConfig = this@CourseRepository.getTimeConfig(id)

        override fun addTimeConfig(config: TimeConfig): Long =
            this@CourseRepository.addTimeConfig(config)

        override fun setScheduleTimeConfigId(scheduleId: String, timeConfigId: Long) =
            this@CourseRepository.setScheduleTimeConfigId(scheduleId, timeConfigId)

        override fun invalidateAllCaches() = this@CourseRepository.invalidateAllCaches()

        override fun dispatchCourseChanged(action: String, courseId: String) =
            this@CourseRepository.dispatchCourseChanged(action, courseId)

        override fun migrateIntoDefaultFolder() = newMigration().migrateIntoDefaultFolder()

        override fun migrateLegacyEntitiesOnce(): MigrationOutcome =
            this@CourseRepository.migrateLegacyEntitiesOnce()
    }

    /**
     * 目录协作者。`by lazy` 是因为它自己的 Host 引用了本类的成员，
     * 而 [init] 里的默认文件夹归档就要用它。
     */
    private val catalog by lazy { ScheduleCatalog(catalogHost) }

    private val catalogHost = object : ScheduleCatalog.Host {
        override val prefs: KeyValueStore get() = this@CourseRepository.prefs

        override fun rowStore(): CourseRowStore? = CourseStorage.rows

        override fun invalidateAllCaches() = this@CourseRepository.invalidateAllCaches()

        override fun invalidateTimeCaches() = this@CourseRepository.invalidateTimeCaches()

        override fun notifySettingsChanged() = notifyCourseChanged("settings")

        override fun boundTimeConfigIdOrNull(scheduleId: String): Long? =
            this@CourseRepository.boundTimeConfigIdOrNull(scheduleId)

        override fun addTimeConfig(config: TimeConfig): Long =
            this@CourseRepository.addTimeConfig(config)

        override fun setScheduleTimeConfigId(scheduleId: String, timeConfigId: Long) =
            this@CourseRepository.setScheduleTimeConfigId(scheduleId, timeConfigId)

        override fun getTimeConfig(id: Long): TimeConfig = this@CourseRepository.getTimeConfig(id)

        override fun getScheduleTimeConfigId(scheduleId: String): Long =
            this@CourseRepository.getScheduleTimeConfigId(scheduleId)

        override fun coursesJsonLooksValid(json: String): Boolean =
            this@CourseRepository.coursesJsonLooksValid(json)

        override fun sanitizeCourses(courses: List<Course>): List<Course> =
            this@CourseRepository.sanitizeCourses(courses)
    }

    init {
        // 启动清理，均幂等：课表收进默认文件夹；清理闲置 time_config（判据不足时跳过）。
        // 不在这里补绑 —— 补绑会让只在旧指针里的配置被误判成孤儿。
        newMigration().migrateIntoDefaultFolder()
        pruneOrphanTimeConfigsIfNeeded()
    }


    // 变更回调：多播列表，避免后构造的 ViewModel 覆盖先注册的监听。
    // @Volatile 不可变列表 + 写时复制：读走快照、写不阻塞读。
    @Volatile
    private var courseChangedListeners: List<(action: String, courseId: String) -> Unit> = emptyList()

    fun addCourseChangedListener(listener: (action: String, courseId: String) -> Unit) {
        if (listener !in courseChangedListeners) {
            courseChangedListeners = courseChangedListeners + listener
        }
    }

    fun removeCourseChangedListener(listener: (action: String, courseId: String) -> Unit) {
        courseChangedListeners = courseChangedListeners - listener
    }

    private fun dispatchCourseChanged(action: String, courseId: String) {
        for (listener in courseChangedListeners) {
            try {
                listener(action, courseId)
            } catch (_: Exception) {
            }
        }
    }

    /** 绕过本类 setter 直接改写 prefs 后必须调用，否则读到陈旧缓存 */
    // internal：测试要「清缓存 → 重新解码」来模拟重启（见 NotePersistenceTest）
    internal fun invalidateAllCaches() {
        courseCache = emptyMap()
        occupiedWeeksCache = emptyMap()
        timeConfigCache = emptyMap()
        timeConfigIdsCache = null
        // 目录的缓存也在这里一起清：仓库是「全库失效」的唯一入口
        catalog.invalidateCaches()
        globalSectionTimesCache = null
        effectiveConfigCache = emptyMap()
        reorganizationCache = emptyMap()
        classStartCache = emptyMap()
        encodedTimeConfigCache = emptyMap()
        invalidateBindingCache()
        scheduleSettingsCache = emptyMap()
        timeConfigsHydrated = false
        blobImportFailed = emptySet()
    }

    /** 节数/节次时间变化会同时影响全局节次映射与分钟级占用判断 */
    private fun invalidateTimeCaches() {
        occupiedWeeksCache = emptyMap()
        globalSectionTimesCache = null
        effectiveConfigCache = emptyMap()
    }

    /** 为 true 时 [notifyCourseChanged] 延迟到批次结束统一提交，避免连续磁盘写与 UI 抖动 */
    private var batchingSettings = false

    private fun notifyCourseChanged(action: String, courseId: String = "") {
        if (action == "settings") {
            if (batchingSettings) return
            commitSettingsChanged(courseId)
            return
        }
        dispatchCourseChanged(action, courseId)
    }

    /**
     * 课程数据（增删改/调课/交换）落库后，通知所有监听的 ViewModel 重新加载。
     */
    fun notifyCoursesBulkChanged() {
        dispatchCourseChanged("bulk", "")
    }

    /** 更新时间戳（本地修改不被远程覆盖）、失效时间缓存、通知 UI */
    private fun commitSettingsChanged(changeId: String = "") {
        val prefix = getScheduleKeyPrefix()
        prefs.edit { putLong("${prefix}_settings_last_modified", Clock.System.now().toEpochMilliseconds()) }
        invalidateTimeCaches()
        dispatchCourseChanged("settings", changeId)
    }

    /**
     * 某课表设置变更落库后的时间戳/UI 通知：始终更新该课表的 `settings_last_modified`；
     * 仅当写入的是当前课表时才失效缓存并通知 UI / 触发重排。
     */
    private fun markScheduleSettingsChanged(scheduleId: String, changeId: String = "") {
        // 该行的设置值已经变了，两个按课表缓存的读取结果必须一起作废
        synchronizedOn(cacheLock) {
            scheduleSettingsCache = scheduleSettingsCache - scheduleId
            classStartCache = classStartCache - scheduleId
        }
        val prefix = getScheduleKeyPrefix(scheduleId)
        prefs.edit { putLong("${prefix}_settings_last_modified", Clock.System.now().toEpochMilliseconds()) }
        if (scheduleId != getCurrentScheduleId()) return
        if (batchingSettings) return
        invalidateTimeCaches()
        dispatchCourseChanged("settings", changeId)
    }

    companion object {
        private const val TAG = "CourseRepository"

        @Volatile
        private var INSTANCE: CourseRepository? = null

        private val lock = Any()

        fun getInstance(): CourseRepository {
            return INSTANCE ?: synchronizedOn(lock) {
                INSTANCE ?: CourseRepository().also { INSTANCE = it }
            }
        }

        /** 兼容旧代码的构造方式 */
        operator fun invoke(): CourseRepository = getInstance()

        /** 开学日规范格式 yyyy/MM/dd */
        fun formatClassStartDate(date: LocalDate): String =
            date.formatSlashDate()

        /**
         * 解析教务/设置/备份里各种开学日写法（yyyy/MM/dd、yyyy-MM-dd、yyyy/M/d、yyyyMMdd 等）。
         * 年份限 1970..2100，避免「26/9/1」被当成公元 26 年。
         */
        fun parseFlexibleDate(raw: String?): LocalDate? {
            if (raw.isNullOrBlank()) return null
            fun validYear(y: Int) = y in 1970..2100
            val trimmed = raw.trim().substringBefore(' ').substringBefore('T')
            val sep = when {
                trimmed.contains('-') -> '-'
                trimmed.contains('/') -> '/'
                else -> null
            }
            if (sep != null) {
                val parts = trimmed.split(sep)
                if (parts.size == 3) {
                    val y = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    val d = parts[2].toIntOrNull()
                    if (y != null && m != null && d != null && validYear(y)) {
                        return runCatching { LocalDate(y, m, d) }.getOrNull()
                    }
                }
            }
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length == 8) {
                val y = digits.substring(0, 4).toInt()
                if (!validYear(y)) return null
                return runCatching {
                    LocalDate(
                        y,
                        digits.substring(4, 6).toInt(),
                        digits.substring(6, 8).toInt()
                    )
                }.getOrNull()
            }
            return null
        }

        /** 规范为 yyyy/MM/dd；无法解析返回 null */
        fun normalizeClassStartDate(raw: String?): String? =
            parseFlexibleDate(raw)?.let { formatClassStartDate(it) }

        const val MAX_TOTAL_WEEKS = 30

        private const val PREFS_NAME = "course_schedule_prefs"
        internal const val KEY_COURSES = "courses"
        /** 坏 JSON 的留档键。原键被删掉，字节搬到这里，人工排查/恢复时还在。 */
        private const val KEY_CORRUPT_COURSES = "courses_corrupt"
        internal const val KEY_CURRENT_WEEK = "current_week"
        internal const val KEY_TOTAL_WEEKS = "total_weeks"
        internal const val KEY_CLASS_START_TIME = "class_start_time"
        internal const val KEY_TEACHING_WEEK_REORGANIZATIONS = "teaching_week_reorganizations"
        internal const val KEY_SMART_WEEKEND = "smart_weekend"
        internal const val KEY_SHOW_NON_CURRENT_WEEK = "show_non_current_week"
        internal const val KEY_CURRENT_SCHEDULE_ID = "current_schedule_id"
        internal const val KEY_SCHEDULE_NAMES = "schedule_names"
        internal const val KEY_SCHEDULE_FOLDERS = "schedule_folders"
        /** 首次引入文件夹时的归档标记；只跑一次 */
        internal const val KEY_DEFAULT_FOLDER_MIGRATED = "default_folder_migrated"
        internal const val DEFAULT_FOLDER_NAME = "默认文件夹"
        internal const val DEFAULT_FOLDER_ID = "folder_default"
        internal const val KEY_PRE_CLASS_REMINDER = "pre_class_reminder"
        internal const val KEY_PRE_CLASS_REMINDER_MINUTES = "pre_class_reminder_minutes"
        /** 上课提醒的节次范围：起始节（1 起） */
        internal const val KEY_PRE_CLASS_REMINDER_START_SECTION = "pre_class_reminder_start_section"
        /** 上课提醒的节次范围：结束节；0 = 不限（到最后） */
        internal const val KEY_PRE_CLASS_REMINDER_END_SECTION = "pre_class_reminder_end_section"
        internal const val KEY_NEXT_DAY_REMINDER = "next_day_reminder"
        internal const val KEY_NEXT_DAY_REMINDER_HOUR = "next_day_reminder_hour"
        internal const val KEY_NEXT_DAY_REMINDER_MINUTE = "next_day_reminder_minute"
        internal const val KEY_ISLAND_NOTIFICATION = "island_notification"
        internal const val KEY_CLASS_DND = "class_dnd_enabled"
        /** 0=勿扰 (FILTER_NONE)，1=静音 (RINGER_SILENT)，2=优先 (FILTER_PRIORITY) */
        internal const val KEY_CLASS_DND_MODE = "class_dnd_mode"
        internal const val KEY_SHIFT_MODE = "shift_mode_enabled"
        internal const val KEY_SHIFT_SELECTED_SCHEDULES = "shift_selected_schedules"
        internal const val KEY_DEFAULT_HOMEPAGE = "default_homepage"
        internal const val KEY_WIDGET_PADDING_MODE = "widget_padding_mode"
        internal const val SCHEDULE_KEY_PREFIX = "schedule_"
        internal const val KEY_TIME_CONFIG_IDS = "time_config_ids"
        internal const val KEY_CURRENT_TIME_CONFIG_ID = "current_time_config_id"
        internal const val TIME_CONFIG_PREFIX = "time_config_"
        // 不匹配 schedule_{name}_ 前缀，删/迁课表时需单独处理
        internal const val SCHEDULE_TIME_CONFIG_PREFIX = "schedule_time_config_"

        /**
         * 小组件 padding 档位的出厂默认档，仅在用户从未手动选过时生效：
         * 小米系（HyperOS 按 4×6 绘制）默认 4×6，其余机型标准档；用户选过就落盘不再变。
         */
        private val defaultWidgetPaddingMode: Int = run {
            val xiaomiBrands = setOf("xiaomi", "redmi", "poco")
            val device = currentDeviceInfo()
            val isXiaomi = device.brand.lowercase() in xiaomiBrands ||
                device.manufacturer.lowercase() in xiaomiBrands
            if (isXiaomi) 1 else 0
        }
    }

    /** 云备份恢复可能把 Int 存成 Float，读失败时按 Float 再存回 Int */
    private fun safeGetInt(key: String, defValue: Int): Int {
        try {
            return prefs.getInt(key, defValue)
        } catch (_: ClassCastException) {
            val floatVal = prefs.getFloat(key, defValue.toFloat())
            val intVal = floatVal.toInt()
            prefs.edit { putInt(key, intVal) }
            return intVal
        }
    }

    fun getCoursesForSchedule(scheduleId: String): List<Course> {
        courseCache[scheduleId]?.let { return it }
        val courses = loadCourses(scheduleId)
        // 并发下可能被算两次，值等价；整体替换不会破坏快照
        synchronizedOn(cacheLock) {
            courseCache = courseCache + (scheduleId to courses)
        }
        return courses
    }

    /**
     * 读一个课表的课程。行表是真相，JSON blob 只作「待导入源」。
     *
     * 1. 已导入 → 读行；
     * 2. 未导入 → 从 blob 导入一次（导入与标记同一事务，自校验不过整体回滚），成功后**删掉 blob**；
     * 3. 没有行表（桌面 / 测试 / 库打不开）或导入失败 → 读 blob。
     */
    private fun loadCourses(scheduleId: String): List<Course> {
        val rows = CourseStorage.rows ?: return readCoursesFromBlob(scheduleId) ?: emptyList()

        if (rows.isImported(scheduleId)) {
            return try {
                rows.load(scheduleId)
            } catch (e: Exception) {
                // 不返回旧 blob 冒充当前课表：冻结快照与行表可能差很多，拿去当 diff 基准会删掉真实行
                NexioLog.e(TAG, "读取课程行表失败，本次按空表处理：schedule=$scheduleId", e)
                emptyList()
            }
        }
        // 本进程已经试过并失败：不再每次读都撞一次库（坏库上会变成每帧一次异常 + 一次 JSON 解析）
        if (scheduleId in blobImportFailed) return readCoursesFromBlob(scheduleId) ?: emptyList()

        val imported = try {
            importCoursesFromBlob(scheduleId, rows)
        } catch (e: Exception) {
            NexioLog.w(TAG, "课程导入行表失败，继续用 JSON：schedule=$scheduleId", e)
            synchronizedOn(cacheLock) {
                blobImportFailed = blobImportFailed + scheduleId
            }
            null
        }
        return imported ?: readCoursesFromBlob(scheduleId) ?: emptyList()
    }

    /** 旧路径：解析 `schedule_{id}_courses`。坏 JSON 返回 null（调用方当空课表，但绝不写回）。 */
    private fun readCoursesFromBlob(scheduleId: String): List<Course>? {
        val json = prefs.getStringOrNull("${getScheduleKeyPrefix(scheduleId)}$KEY_COURSES") ?: return null
        // 坏 JSON 按真名读会得到空壳课，不当成有效课表，保留原数据供备份恢复
        if (!coursesJsonLooksValid(json)) return null
        return try {
            sanitizeCourses(decodeCourses(json)).normalizeScheduleId(scheduleId)
        } catch (e: Exception) {
            NexioLog.e(TAG, "课程 JSON 解析失败：schedule=$scheduleId", e)
            null
        }
    }

    /**
     * 从 JSON 导入行表。**每个 blob 都有确定归宿**：
     *
     * - 正常 JSON → 导入 + 删 blob；
     * - 坏 JSON（认不出 / 解析抛异常）→ 原始字节留档到 `courses_corrupt`，然后按空表迁移。
     *   一份坏数据不能卡住整次升级（1.6.7 → 1.6.9 要求一次迁完），也不能把坏数据硬塞进表，
     *   所以「留档 + 空表」是唯一既保证进度又不丢原始字节的做法；
     * - 没有 courses 键（新建 / 新学期）→ 直接记成空表。
     *
     * 导入成功后**立刻删掉 blob**：行表已是唯一真相，留着的旧 JSON 只会变成一份会过期的第二真相
     * （备份、diff 基准、读失败兜底都会读到它）。
     *
     * 返回 null 只表示「连空表都写不进去」（锚点或库异常），调用方继续用 blob 兜住界面。
     */
    private fun importCoursesFromBlob(scheduleId: String, rows: CourseRowStore): List<Course>? {
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_COURSES"
        val json = prefs.getStringOrNull(key)
        if (json == null) {
            // 没有课程键的课表（新建 / 新学期）直接记成空表
            val anchor = courseRowAnchor(scheduleId) ?: return null
            rows.importAll(anchor, emptyList())
            return emptyList()
        }

        val courses = runCatching {
            if (!coursesJsonLooksValid(json)) null
            else sanitizeCourses(decodeCourses(json)).normalizeScheduleId(scheduleId)
        }.onFailure {
            NexioLog.e(TAG, "课程 JSON 解析异常：schedule=$scheduleId", it)
        }.getOrNull()

        if (courses == null) {
            // 坏数据：留档后按空表迁移，不能让它永远卡住升级
            quarantineCorruptCourses(scheduleId, key, json)
            val anchor = courseRowAnchor(scheduleId) ?: return null
            rows.importAll(anchor, emptyList())
            return emptyList()
        }

        val anchor = courseRowAnchor(scheduleId) ?: return null
        rows.importAll(anchor, courses)
        // 顺序：先落行表（含自校验与标记）再删源。反过来的话，中途被杀会两头都没有
        prefs.remove(key)
        return courses
    }

    /**
     * 坏 JSON 留档：原键删掉、字节原样搬到 `courses_corrupt`。
     *
     * 用户看不见它，但人工排查/恢复时还在，备份也会带上。不这么做的话，要么整次升级卡死，
     * 要么原始数据被空表覆盖 —— 两种都不可接受。
     */
    private fun quarantineCorruptCourses(scheduleId: String, key: String, json: String) {
        NexioLog.e(TAG, "课程 JSON 无法解析，留档后按空表迁移：schedule=$scheduleId")
        prefs.edit {
            putString("${getScheduleKeyPrefix(scheduleId)}$KEY_CORRUPT_COURSES", json)
            remove(key)
        }
    }

    /**
     * 一次性把旧版本（1.6.7 及更早）的本地数据搬进实体表。启动时与恢复备份后各调一次。
     *
     * 搬迁细节全在 [LegacyEntityMigration]，这里只提供它需要的写入能力。
     * 跑完之后 [loadCourses] 的 blob 导入只会是空操作 —— 那时删掉那条路径才是安全的。
     */
    fun migrateLegacyEntitiesOnce(): MigrationOutcome = newMigration().run()

    private fun newMigration() =
        LegacyEntityMigration(prefs, CourseStorage.rows, migrationHost)


    /**
     * 行表的外键锚点。`schedule.time_config_id` 是 NOT NULL 外键，必须有一份真实存在的作息配置。
     *
     * 一个配置都没有时返回 null，调用方退回 JSON；不凭空造配置。
     */
    private fun courseRowAnchor(scheduleId: String): CourseRowAnchor? =
        // 只读不补绑。未绑定时用它实际会用到的那套（与 getScheduleTimeConfigId 同一回退）
        rowAnchorFor(scheduleId, boundTimeConfigIdOrNull(scheduleId) ?: resolveLegacyCurrentTimeConfigId())

    /**
     * 按指定绑定构造锚点。
     *
     * 一个作息配置都没有时**不再放弃**：`getTimeConfig` 会现场播种一份默认配置，
     * 让 `schedule.time_config_id` 有合法外键目标。否则这张课表永远迁不进实体表，
     * 升级就卡在这里 —— 而升级只发生一次，卡住就是永久卡住。
     */
    private fun rowAnchorFor(scheduleId: String, timeConfigId: Long): CourseRowAnchor? {
        val config = getTimeConfig(timeConfigId)
        val prefix = getScheduleKeyPrefix(scheduleId)
        return CourseRowAnchor(
            scheduleId = scheduleId,
            timeConfigId = timeConfigId,
            timeConfigName = config.name,
            timeConfigPayload = encodeTimeConfigCached(config),
            sortOrder = getScheduleNames().indexOf(scheduleId).coerceAtLeast(0),
            currentWeek = safeGetInt("${prefix}$KEY_CURRENT_WEEK", 1),
            totalWeeks = safeGetInt("${prefix}$KEY_TOTAL_WEEKS", 20),
            classStart = prefs.getStringOrNull("${prefix}$KEY_CLASS_START_TIME")
                ?.let { normalizeClassStartDate(it) },
        )
    }

    /**
     * 锚点里的作息 payload。`ensureAnchors` 只在行不存在时才真的写它，但每次保存课程都会把
     * 整份作息重新序列化一遍（含作息方案与节次时间），纯属白跑。
     * 按实例缓存：配置被替换成新对象时自然重编码。
     */
    private fun encodeTimeConfigCached(config: TimeConfig): String {
        encodedTimeConfigCache[config.id]?.let { cached ->
            if (cached.config === config) return cached.json
        }
        val json = encodeTimeConfig(config)
        synchronizedOn(cacheLock) {
            encodedTimeConfigCache =
                encodedTimeConfigCache + (config.id to EncodedTimeConfig(config, json))
        }
        return json
    }

    /**
     * 课表行上的设置值。**行是唯一真相**，不存在就返回 null，不回看 prefs。
     *
     * 以前这里还有一次「首次访问 prefs → 行」的同步：迁移把 prefs 副本删掉之后，
     * 它只会把行里的正确值覆盖成默认值 —— 恢复备份后设置被重置就是这么来的。
     * 现在搬数据只发生在 [migrateLegacyEntitiesOnce] 里。
     */
    private fun rowSettingsOf(scheduleId: String): ScheduleRowSettings? {
        scheduleSettingsCache[scheduleId]?.let { return it }
        val rows = CourseStorage.rows ?: return null
        val settings = runCatching { rows.settingsOf(scheduleId) }.getOrNull() ?: return null
        synchronizedOn(cacheLock) {
            scheduleSettingsCache = scheduleSettingsCache + (scheduleId to settings)
        }
        return settings
    }

    /** 行不存在时补建锚点行，返回是否真的新建了。 */
    private fun createScheduleRowIfAbsent(rows: CourseRowStore, scheduleId: String): Boolean {
        if (runCatching { rows.settingsOf(scheduleId) }.getOrNull() != null) return false
        val anchor = courseRowAnchor(scheduleId) ?: return false
        return runCatching { rows.setBinding(anchor) }
            .onFailure { NexioLog.w(TAG, "补建课表行失败：$scheduleId", it) }
            .isSuccess
    }

    /**
     * 行不存在（新表 / 从没写过课程）时补建锚点行，让「只写开关」这类操作也有行可落。
     *
     * 只建行，不从 prefs 补值：prefs 里那几块在迁移成功后就删了，能从 prefs 补到值
     * 说明迁移没跑完 —— 那是「下次启动重跑迁移」的事，不是每次写都去猜。
     */
    private fun ensureScheduleRow(rows: CourseRowStore, scheduleId: String) {
        createScheduleRowIfAbsent(rows, scheduleId)
    }

    /**
     * 写行上的三个设置值，返回是否写成功。
     *
     * 只写行。prefs 里的同名字段是迁移源，迁移成功后就删了 —— 再写一份只会变成
     * 会过期的第二真相，还要额外维护「谁是真相」的标记。
     */
    private fun writeRowSettings(
        scheduleId: String,
        currentWeek: Int? = null,
        totalWeeks: Int? = null,
        classStart: String? = null,
    ): Boolean {
        synchronizedOn(cacheLock) {
            scheduleSettingsCache = scheduleSettingsCache - scheduleId
            classStartCache = classStartCache - scheduleId
        }
        val rows = CourseStorage.rows
        if (rows == null) {
            NexioLog.e(TAG, "课表设置写失败：课程行表没有接线")
            return false
        }
        return runCatching {
            // 行还不存在（新表 / 从没写过课程）：先补建锚点行
            ensureScheduleRow(rows, scheduleId)
            rows.writeSettings(scheduleId, currentWeek, totalWeeks, classStart)
        }.onFailure { NexioLog.e(TAG, "课表设置写行表失败：$scheduleId", it) }.isSuccess
    }

    fun getScheduleSummary(scheduleId: String): String {
        val courses = getCoursesForSchedule(scheduleId).ifEmpty { return "空课表" }
        val courseCount = courses.size
        val weeks = courses.flatMap { course ->
            course.selectedWeeks.ifEmpty {
                course.startWeek..course.endWeek
            }
        }.toSet()
        val weekCount = weeks.size
        return "共${weekCount}周，${courseCount}节课"
    }

    private fun getScheduleKeyPrefix(): String {
        return getScheduleKeyPrefix(getCurrentScheduleId())
    }

    /** 指定课表前缀；导入到目标课表时用它把设置写到目标而非当前 */
    private fun getScheduleKeyPrefix(scheduleId: String): String {
        return "$SCHEDULE_KEY_PREFIX${scheduleId}_"
    }

    // USELESS_ELVIS：Gson 反序列化后非空字段仍可能是 null
    @Suppress(
        "SENSELESS_COMPARISON",
        "ELVIS_ALWAYS_NULL",
        "USELESS_ELVIS",
        "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS"
    )
    /** 任一稳定字段名出现即可；坏 JSON 判为无课，避免被当成空课表静默覆盖 */
    private fun coursesJsonLooksValid(json: String): Boolean {
        // 空数组是合法数据（新建 / 清空课表）。判成坏 JSON 会让空课表永不导入行表，
        // 也就永远没有锚点行，绑定写不进 schedule。
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed == "[]") return true
        return json.contains("\"name\"") ||
            json.contains("\"dayOfWeek\"") ||
            json.contains("\"startSection\"") ||
            json.contains("\"id\"")
    }

    /** UnsafeAllocator 使旧 JSON 缺失字段为 null，无条件重建以拿到默认值 */
    // USELESS_ELVIS / ELVIS_ALWAYS_NULL：字段声明为非空 String，但 UnsafeAllocator 真的会给 null，
    // 这里的 ?: 是自愈数据的关键，不能删。与 TimeConfig.sanitize / TimeRoutine.fromRaw 同一处理。
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
    private fun sanitizeCourses(courses: List<Course>): List<Course> {
        return courses.map { course ->
            Course(
                id = course.id ?: "",
                name = course.name ?: "",
                classroom = course.classroom ?: "",
                teacher = course.teacher ?: "",
                dayOfWeek = course.dayOfWeek,
                startSection = course.startSection,
                endSection = course.endSection,
                startWeek = course.startWeek,
                endWeek = course.endWeek,
                weekType = course.weekType,
                colorRes = course.colorRes,
                selectedWeeks = course.selectedWeeks ?: emptyList(),
                scheduleId = course.scheduleId ?: "",
                lastModified = course.lastModified,
                isCustomTime = course.isCustomTime,
                customStartTime = course.customStartTime,
                customEndTime = course.customEndTime,
                // ⚠ 这里用显式构造（UnsafeAllocator 会给出 null 字段），新增字段必须一起补 ——
                //   漏了就等于每次读盘都把该字段抹掉，表现为「重启后备注/新字段全丢」。
                note = (course.note ?: "").trim().truncateNote(),
                // 备注长度/空值在这里再收一次口：盘上的数据可能来自旧版本或手工改过的备份
                weekNotes = (course.weekNotes ?: emptyMap())
                    .mapValues { it.value.trim().truncateNote() }
                    .filterValues { it.isNotEmpty() },
            )
        }
    }

    fun getAllCourses(): List<Course> = getCoursesForSchedule(getCurrentScheduleId())

    /**
     * 保存整个课表的课程列表。调用点都是「读全量 → 改内存 → 存全量」，签名保持不变；
     * 差分在存储层做，改一门课只写一行。
     *
     * **写失败就抛**，不落 JSON 副本、不更新内存缓存：以前这里有「行写失败 → 把整表 encode 成
     * `schedule_{名}_courses` → 清导入标记」的抢救路径，等于给同一次写入造第二真相，
     * 而且它和真正的迁移源长得一模一样（读路径无法分辨「待迁移」和「抢救副本」）。
     * 现在库写不进去就说明库坏了，让调用方看见异常，比「界面显示已保存、重启全丢」诚实。
     */
    fun saveCourses(courses: List<Course>, notify: Boolean = true) {
        val scheduleId = getCurrentScheduleId()
        val normalized = courses.normalizeScheduleId(scheduleId)
        val rows = CourseStorage.rows
            ?: error("课程保存失败：课程行表没有接线 schedule=$scheduleId")
        val anchor = courseRowAnchor(scheduleId)
            ?: error("课程保存失败：拿不到课表锚点行（没有可用作息配置）schedule=$scheduleId")

        if (rows.isImported(scheduleId)) {
            // 缓存可能未热（刚切表就保存）：必须拿到真实上一版，否则删掉的课不会被删
            val previous = courseCache[scheduleId] ?: loadCourses(scheduleId)
            runCatching { rows.writeDiff(anchor, previous, normalized) }
                .onFailure { NexioLog.e(TAG, "课程行写入失败：schedule=$scheduleId", it) }
                .getOrThrow()
        } else {
            // 这张表还没落过库（新建 / 刚恢复 / 之前的导入失败）：整表导入并写下导入标记。
            // 不能只写 diff —— 没有标记的话下次读取会当成「未导入」再导一次空表，把课程抹掉。
            runCatching { rows.importAll(anchor, normalized) }
                .onFailure { NexioLog.e(TAG, "课程整表写入失败：schedule=$scheduleId", it) }
                .getOrThrow()
        }

        synchronizedOn(cacheLock) {
            courseCache = courseCache + (scheduleId to normalized)
        }
        occupiedWeeksCache = emptyMap()
        if (notify) dispatchCourseChanged("bulk", "")
    }

    /** 统一 scheduleId：blob 里可能残留别的课表 id。 */
    private fun List<Course>.normalizeScheduleId(scheduleId: String): List<Course> =
        if (all { it.scheduleId == scheduleId }) this
        else map { if (it.scheduleId == scheduleId) it else it.copy(scheduleId = scheduleId) }

    fun addCourse(course: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        val courseWithSchedule = if (course.scheduleId.isEmpty()) {
            course.copy(scheduleId = getCurrentScheduleId())
        } else {
            course
        }
        courses.add(courseWithSchedule)
        saveCourses(courses, notify = false)
        return courses
    }

    fun updateCourse(course: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        val index = courses.indexOfFirst { it.id == course.id }
        if (index != -1) {
            courses[index] = course.copy(lastModified = Clock.System.now().toEpochMilliseconds())
            saveCourses(courses, notify = false)
        }
        return courses
    }

    fun updateCoursesByName(oldName: String, updated: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        var changed = false
        for (i in courses.indices) {
            if (courses[i].name == oldName) {
                courses[i] = courses[i].copy(
                    name = updated.name,
                    colorRes = updated.colorRes,
                    lastModified = Clock.System.now().toEpochMilliseconds()
                )
                changed = true
            }
        }
        if (changed) {
            saveCourses(courses, notify = false)
        }
        return courses
    }

    fun deleteCourse(courseId: String): List<Course> {
        val courses = getAllCourses().toMutableList()
        courses.removeAll { it.id == courseId }
        saveCourses(courses, notify = false)
        return courses
    }

    /** 仅删该周实例；删光则整条删除。通知由 ViewModel 统一处理，避免竞态 */
    fun deleteCourseForWeek(courseId: String, week: Int): List<Course> {
        val courses = getAllCourses().toMutableList()
        val index = courses.indexOfFirst { it.id == courseId }
        if (index != -1) {
            val updated = removeWeekFrom(courses[index], week)
            if (updated == null) {
                courses.removeAt(index)
            } else {
                courses[index] = updated
            }
            saveCourses(courses, notify = false)
        }
        return courses
    }

    /** @return 移除后的新课程；最后一周被移除时返回 null 表示应删除整条 */
    private fun removeWeekFrom(course: Course, week: Int, resetWeekType: Boolean = false): Course? {
        val remaining = resolveSelectedWeeks(course).filter { it != week }
        if (remaining.isEmpty()) return null
        return course.copy(
            selectedWeeks = remaining,
            startWeek = remaining.min(),
            endWeek = remaining.max(),
            // 冲突路径调用方置 ALL；单独删某周保持原 weekType
            weekType = if (resetWeekType) Course.WEEK_TYPE_ALL else course.weekType,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
    }

    /** selectedWeeks 为空时按 start/end/weekType 推导 */
    private fun resolveSelectedWeeks(course: Course): List<Int> {
        if (course.selectedWeeks.isNotEmpty()) return course.selectedWeeks
        val weeks = mutableListOf<Int>()
        for (w in course.startWeek..course.endWeek) {
            when (course.weekType) {
                Course.WEEK_TYPE_ODD -> if (w % 2 == 1) weeks.add(w)
                Course.WEEK_TYPE_EVEN -> if (w % 2 == 0) weeks.add(w)
                else -> weeks.add(w)
            }
        }
        return weeks
    }

    /** 同源（名/教室/教师/位置相同）用于调课时合并周次而非新建重复课程 */
    private fun isSameCourseIdentity(a: Course, b: Course): Boolean {
        return a.name == b.name &&
            a.classroom == b.classroom &&
            a.teacher == b.teacher &&
            a.dayOfWeek == b.dayOfWeek &&
            a.startSection == b.startSection &&
            a.endSection == b.endSection
    }

    /**
     * 调课-移动：仅影响该周。单周直接改位置；多周拆分；目标已有同源则合并。通知由 ViewModel 统一处理。
     * @param targetWeek 目标位周次；与源周不同时为跨周移动，默认同周
     */
    fun moveCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int,
        targetWeek: Int = week
    ): List<Course> {
        val courses = getAllCourses()
        val result = moveWeekInPlace(
            courses, sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection,
            targetWeek
        ) ?: return courses
        saveCourses(result, notify = false)
        return result
    }

    /**
     * 调课-覆盖：按周删除目标位冲突课后移动。同源课不删，交给 move 合并。
     * @param targetWeek 目标位周次（冲突课按它删）
     */
    fun overwriteCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int,
        targetWeek: Int = week
    ): List<Course> {
        val courses = getAllCourses().toMutableList()
        val source = courses.find { it.id == sourceCourseId } ?: return courses

        val targetTemp = source.copy(
            dayOfWeek = targetDayOfWeek,
            startSection = targetStartSection,
            endSection = targetEndSection
        )

        val conflictIds = courses.asSequence()
            .filter { existing ->
                existing.id != sourceCourseId &&
                !isSameCourseIdentity(existing, targetTemp) &&
                existing.dayOfWeek == targetDayOfWeek &&
                existing.startSection <= targetEndSection &&
                existing.endSection >= targetStartSection
            }
            .filter { existing -> targetWeek in resolveSelectedWeeks(existing) }
            .map { it.id }
            .toList()

        val result = courses.toMutableList()
        for (id in conflictIds) {
            val idx = result.indexOfFirst { it.id == id }
            if (idx == -1) continue
            val updated = removeWeekFrom(result[idx], targetWeek, resetWeekType = true)
            if (updated == null) {
                result.removeAt(idx)
            } else {
                result[idx] = updated
            }
        }
        // 先落盘中间结果，避免 moveCourseForWeek 重读旧数据
        saveCourses(result, notify = false)
        return moveCourseForWeek(
            sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection, targetWeek
        )
    }

    /** 调课-交换：双方各拆出该周实例互换；与对方原位同源时同样走合并 */
    fun swapCoursesForWeek(
        sourceCourseId: String,
        targetCourseId: String,
        week: Int,
        targetWeek: Int = week
    ): List<Course> {
        if (sourceCourseId == targetCourseId) return getAllCourses()
        val courses = getAllCourses().toMutableList()
        val srcIdx = courses.indexOfFirst { it.id == sourceCourseId }
        val tgtIdx = courses.indexOfFirst { it.id == targetCourseId }
        if (srcIdx == -1 || tgtIdx == -1) return courses
        val src = courses[srcIdx]
        val tgt = courses[tgtIdx]

        val srcPos = Triple(src.dayOfWeek, src.startSection, src.endSection)
        val tgtPos = Triple(tgt.dayOfWeek, tgt.startSection, tgt.endSection)

        val srcWeeks = resolveSelectedWeeks(src)
        val tgtWeeks = resolveSelectedWeeks(tgt)
        if (week !in srcWeeks || targetWeek !in tgtWeeks) return courses

        // 同源课（同名/同教室/同教师）在双方都在的该周互换：位置换了、内容一样，视觉不变 → 跳过。
        // 注意必须再比一次节次跨度：占 2 节的课和占 1 节的同名课互换是有实际效果的，不能跳过
        val srcAtTarget = src.copy(
            dayOfWeek = tgtPos.first,
            startSection = tgtPos.second,
            endSection = tgtPos.third
        )
        val sameSpan =
            (src.endSection - src.startSection) == (tgt.endSection - tgt.startSection)
        if (sameSpan && isSameCourseIdentity(srcAtTarget, tgt)) return courses

        var result: MutableList<Course> = courses
        moveWeekInPlace(
            result, src.id, week, tgtPos.first, tgtPos.second, tgtPos.third, targetWeek
        )?.let { result = it }
        // 第一步后源可能已拆分，src.id 仍在原课程（已移除该周）
        moveWeekInPlace(
            result, tgt.id, targetWeek, srcPos.first, srcPos.second, srcPos.third, week
        )?.let { result = it }

        saveCourses(result, notify = false)
        return result
    }

    /**
     * 按周移动的拆分+合并（原地、不落盘）。
     * @param targetWeek 目标位周次；与 week 不同时走跨周：源课剔除 week、目标位写入 targetWeek
     * @return 新列表；无需改动时返回 null，调用方跳过落盘
     */
    private fun moveWeekInPlace(
        courses: List<Course>,
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int,
        targetWeek: Int = week
    ): MutableList<Course>? {
        val result = courses.toMutableList()
        val sourceIdx = result.indexOfFirst { it.id == sourceCourseId }
        if (sourceIdx == -1) return null
        val source = result[sourceIdx]

        val samePosition =
            source.dayOfWeek == targetDayOfWeek &&
                source.startSection == targetStartSection &&
                source.endSection == targetEndSection
        // 同位置同周 = 无事可做
        if (samePosition && targetWeek == week) return null

        val currentSelectedWeeks = resolveSelectedWeeks(source)
        if (week !in currentSelectedWeeks) return null

        if (samePosition) {
            // 目标周本来就有这节课：挪过去是同一格同一内容，净效果只是让源周凭空少一节
            // （用户视角 = 拖了一下课没了）→ 判为无事可做，不落盘
            if (targetWeek in currentSelectedWeeks) return null
            // 同位置跨周（调休列：同一天、不同周次）→ 只挪周次，
            // 不拆成两条同槽记录（拆了也只是并回来，平白多一条）
            val newWeeks = (currentSelectedWeeks.filter { it != week } + targetWeek)
                .distinct().sorted()
            result[sourceIdx] = source.copy(
                selectedWeeks = newWeeks,
                startWeek = newWeeks.min(),
                endWeek = newWeeks.max(),
                weekType = Course.WEEK_TYPE_ALL,
                lastModified = Clock.System.now().toEpochMilliseconds()
            )
            return result
        }

        val targetTemp = source.copy(
            dayOfWeek = targetDayOfWeek,
            startSection = targetStartSection,
            endSection = targetEndSection
        )

        val mergeTargetIdx = result.indexOfFirst { existing ->
            existing.id != source.id && isSameCourseIdentity(existing, targetTemp)
        }

        if (mergeTargetIdx != -1) {
            // 合并周次进同源课程
            val mergeTarget = result[mergeTargetIdx]
            val mergeWeeks = resolveSelectedWeeks(mergeTarget).toMutableSet()
            mergeWeeks.add(targetWeek)
            val sortedWeeks = mergeWeeks.sorted()
            result[mergeTargetIdx] = mergeTarget.copy(
                selectedWeeks = sortedWeeks,
                startWeek = sortedWeeks.min(),
                endWeek = sortedWeeks.max(),
                weekType = Course.WEEK_TYPE_ALL,
                lastModified = Clock.System.now().toEpochMilliseconds()
            )
            val sourceWeeks = currentSelectedWeeks.filter { it != week }
            if (sourceWeeks.isEmpty()) {
                // 源课程所有周次已合并到同源课程，删除源课程
                result.removeAt(sourceIdx)
            } else {
                // 源课程还有其他周次，更新剩余周次
                result[sourceIdx] = source.copy(
                    selectedWeeks = sourceWeeks,
                    startWeek = sourceWeeks.min(),
                    endWeek = sourceWeeks.max(),
                    weekType = Course.WEEK_TYPE_ALL,
                    lastModified = Clock.System.now().toEpochMilliseconds()
                )
            }
        } else if (currentSelectedWeeks.size == 1 && currentSelectedWeeks.first() == week) {
            // 源课程只在该周有效，直接改位置；跨周时周次一并换到目标周
            result[sourceIdx] = source.copy(
                dayOfWeek = targetDayOfWeek,
                startSection = targetStartSection,
                endSection = targetEndSection,
                selectedWeeks = if (targetWeek != week) listOf(targetWeek) else source.selectedWeeks,
                startWeek = if (targetWeek != week) targetWeek else source.startWeek,
                endWeek = if (targetWeek != week) targetWeek else source.endWeek,
                weekType = if (targetWeek != week) Course.WEEK_TYPE_ALL else source.weekType,
                lastModified = Clock.System.now().toEpochMilliseconds()
            )
        } else {
            // 拆分
            val sourceWeeks = currentSelectedWeeks.filter { it != week }
            result[sourceIdx] = source.copy(
                selectedWeeks = sourceWeeks,
                startWeek = sourceWeeks.min(),
                endWeek = sourceWeeks.max(),
                weekType = Course.WEEK_TYPE_ALL,
                lastModified = Clock.System.now().toEpochMilliseconds()
            )
            val newCourse = source.copy(
                id = randomUuidV4(),
                dayOfWeek = targetDayOfWeek,
                startSection = targetStartSection,
                endSection = targetEndSection,
                selectedWeeks = listOf(targetWeek),
                startWeek = targetWeek,
                endWeek = targetWeek,
                weekType = Course.WEEK_TYPE_ALL,
                lastModified = Clock.System.now().toEpochMilliseconds()
            )
            result.add(newCourse)
        }
        return result
    }

    fun getLastWeekWithCourses(): Int {
        val courses = getAllCourses()
        if (courses.isEmpty()) return 0
        var maxWeek = 0
        for (c in courses) {
            val end = if (c.selectedWeeks.isNotEmpty()) c.selectedWeeks.max() else c.endWeek
            if (end > maxWeek) maxWeek = end
        }
        return maxWeek
    }

    fun getCurrentWeek(): Int {
        return getCurrentWeek(getCurrentScheduleId())
    }

    fun getCurrentWeek(scheduleId: String): Int {
        // 只认行；行不存在就是新表，取默认值
        return rowSettingsOf(scheduleId)?.currentWeek ?: 1
    }

    /** Background readers cannot assume the persisted UI week was refreshed after midnight. */
    fun getLiveTeachingWeek(date: LocalDate = todayLocalDate(), scheduleId: String = getCurrentScheduleId()): Int =
        teachingWeekPositionForDate(date, scheduleId).week
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    fun setCurrentWeek(week: Int) {
        val scheduleId = getCurrentScheduleId()
        // 只写真相（行）；没有行表的平台由 writeRowSettings 自己写 prefs
        if (!writeRowSettings(scheduleId, currentWeek = week)) return
        notifyCourseChanged("settings")
    }

    fun getTotalWeeks(): Int {
        return getTotalWeeks(getCurrentScheduleId())
    }

    fun getTotalWeeks(scheduleId: String): Int {
        // 只认行；行不存在就是新表，取默认值
        return (rowSettingsOf(scheduleId)?.totalWeeks ?: 20).coerceIn(1, MAX_TOTAL_WEEKS)
    }

    fun setTotalWeeks(weeks: Int) {
        setTotalWeeks(getCurrentScheduleId(), weeks)
    }

    /** 写目标课表总周数；更新该课表同步时间戳，仅当前课表才通知 UI */
    fun setTotalWeeks(scheduleId: String, weeks: Int) {
        if (weeks !in 1..MAX_TOTAL_WEEKS) return
        if (!writeRowSettings(scheduleId, totalWeeks = weeks)) return
        markScheduleSettingsChanged(scheduleId)
    }

    /**
     * 重组规则的原始 JSON，只从表读。
     *
     * 读和**备份导出**共用这一个口子 —— 导出曾经自己拼 prefs 键，迁移把键删掉之后
     * 备份里就悄悄少了这一块，恢复时整份规则消失。
     */
    private fun teachingWeekReorganizationsRawOf(scheduleId: String): String? =
        CourseStorage.rows?.let { rows ->
            runCatching { rows.teachingWeekReorganizationsOf(scheduleId) }.getOrNull()
        }

    fun getTeachingWeekReorganizations(
        scheduleId: String = getCurrentScheduleId(),
    ): List<TeachingWeekReorganizationRule> {
        val raw = teachingWeekReorganizationsRawOf(scheduleId)
        // 键里带原始串：写盘一变就重解析，不需要在每个写入点手动失效
        reorganizationCache[scheduleId]?.let { cached ->
            if (cached.raw == raw) return cached.rules
        }
        val rules = if (raw == null) {
            emptyList()
        } else {
            runCatching {
                // Keep a syntactically valid saved rule visible if total_weeks was later reduced;
                // the editor can then explain/fix it instead of silently hiding the user's data.
                TeachingWeekReorganization.decode(raw, Int.MAX_VALUE)
            }.getOrDefault(emptyList())
        }
        synchronizedOn(cacheLock) {
            reorganizationCache =
                reorganizationCache + (scheduleId to CachedReorganizations(raw, rules))
        }
        return rules
    }

    /**
     * 写整份规则 JSON，返回内容是否真的变了（调用方据此决定要不要发变更通知）。
     *
     * 只写表。内容相同时短路，避免一次无谓的写盘 + 通知。
     */
    private fun writeTeachingWeekReorganizationsRaw(scheduleId: String, raw: String): Boolean {
        val rows = CourseStorage.rows
        if (rows == null) {
            NexioLog.e(TAG, "教学周重组规则写失败：课程行表没有接线")
            return false
        }
        if (teachingWeekReorganizationsRawOf(scheduleId) == raw) return true
        ensureScheduleRow(rows, scheduleId)
        return runCatching { rows.writeTeachingWeekReorganizations(scheduleId, raw) }
            .onFailure { NexioLog.e(TAG, "教学周重组规则写行失败：$scheduleId", it) }
            .isSuccess
    }

    /** Validate the entire ruleset before one atomic preference write. */
    fun setTeachingWeekReorganizations(
        rules: List<TeachingWeekReorganizationRule>,
        scheduleId: String = getCurrentScheduleId(),
        allowExistingOutOfRangeRules: Boolean = false,
        changedRule: TeachingWeekReorganizationRule? = null,
        preserveOutOfRangeRules: Boolean = false,
    ): Boolean {
        val totalWeeks = getTotalWeeks(scheduleId)
        val validationError = if (preserveOutOfRangeRules) {
            TeachingWeekReorganization.validationError(rules, Int.MAX_VALUE)
        } else if (allowExistingOutOfRangeRules) {
            TeachingWeekReorganization.validationErrorForRuleChange(
                updatedRules = rules,
                existingRules = getTeachingWeekReorganizations(scheduleId),
                changedRule = changedRule ?: return false,
                totalWeeks = totalWeeks,
            )
        } else {
            TeachingWeekReorganization.validationError(rules, totalWeeks)
        }
        if (validationError != null) {
            return false
        }
        val normalized = rules.sortedBy { it.firstOriginalWeek }
        val raw = runCatching {
            TeachingWeekReorganization.encode(
                normalized,
                if (allowExistingOutOfRangeRules || preserveOutOfRangeRules) Int.MAX_VALUE
                else totalWeeks,
            )
        }.getOrNull() ?: return false
        if (!writeTeachingWeekReorganizationsRaw(scheduleId, raw)) return false
        markScheduleSettingsChanged(scheduleId, "teaching_week_reorganizations")
        return true
    }

    /** Deleting a rule remains possible after the semester week limit was reduced. */
    fun removeTeachingWeekReorganization(
        rule: TeachingWeekReorganizationRule,
        scheduleId: String = getCurrentScheduleId(),
    ): Boolean {
        val rules = getTeachingWeekReorganizations(scheduleId).toMutableList()
        val index = rules.indexOf(rule)
        if (index < 0) return false
        rules.removeAt(index)
        if (TeachingWeekReorganization.validationError(rules, Int.MAX_VALUE) != null) return false
        val raw = runCatching { TeachingWeekReorganization.encode(rules, Int.MAX_VALUE) }
            .getOrNull() ?: return false
        writeTeachingWeekReorganizationsRaw(scheduleId, raw)
        markScheduleSettingsChanged(scheduleId, "teaching_week_reorganizations")
        return true
    }

    fun teachingWeekPositionForDate(
        date: LocalDate,
        scheduleId: String = getCurrentScheduleId(),
    ): TeachingWeekPosition {
        val startDate = LocalDate.parse(getClassStartTime(scheduleId).replace("/", "-"))
        return TeachingWeekReorganization.mapDate(
            semesterStartDate = startDate,
            date = date,
            rules = getTeachingWeekReorganizations(scheduleId),
        )
    }

    fun dateForTeachingWeekDay(
        teachingWeek: Int,
        weekday: Int,
        scheduleId: String = getCurrentScheduleId(),
    ): LocalDate? {
        val startDate = LocalDate.parse(getClassStartTime(scheduleId).replace("/", "-"))
        return TeachingWeekReorganization.dateForPosition(
            semesterStartDate = startDate,
            teachingWeek = teachingWeek,
            weekday = weekday,
            rules = getTeachingWeekReorganizations(scheduleId),
        )
    }

    /** 旧值不是可识别日期时回退当天并写回；兼容 yyyy-MM-dd / yyyy/M/d / yyyyMMdd */
    fun getClassStartTime(): String {
        return getClassStartTime(getCurrentScheduleId())
    }

    fun getClassStartTime(scheduleId: String): String {
        // 每次教学周换算都要用：规范化结果只随写入变化，算一遍就够
        classStartCache[scheduleId]?.let { return it }
        // 只认行；prefs 里那份在迁移成功后已经删了
        val stored = rowSettingsOf(scheduleId)?.classStart
        // 教务脚本等常写入 yyyy-MM-dd：识别后就地规范化，不能重置成今天
        val normalized = normalizeClassStartDate(stored)
        val resolved = if (normalized != null) {
            if (normalized != stored) writeRowSettings(scheduleId, classStart = normalized)
            normalized
        } else {
            val default = todayLocalDate().formatSlashDate()
            writeRowSettings(scheduleId, classStart = default)
            default
        }
        synchronizedOn(cacheLock) {
            classStartCache = classStartCache + (scheduleId to resolved)
        }
        return resolved
    }

    fun setClassStartTime(time: String) {
        setClassStartTime(getCurrentScheduleId(), time)
    }

    /** 指定课表写入开学日；非法日期直接忽略，避免导入路径把开学日写成今天 */
    fun setClassStartTime(scheduleId: String, time: String) {
        val normalized = normalizeClassStartDate(time) ?: return
        if (!writeRowSettings(scheduleId, classStart = normalized)) return
        markScheduleSettingsChanged(scheduleId)
    }

    fun getSmartWeekend(): Boolean {
        return getSmartWeekend(getCurrentScheduleId())
    }

    fun getSmartWeekend(scheduleId: String): Boolean {
        // 只认表。行不存在就是「这张课表没设过」，取默认值 —— 不回看 prefs：
        // 迁移成功后 prefs 副本已删，回落只会读到半套旧数据。
        val row = CourseStorage.rows?.let { rows ->
            runCatching { rows.displaySettingsOf(scheduleId) }.getOrNull()
        }
        return row?.smartWeekend ?: false
    }

    fun setSmartWeekend(smart: Boolean) {
        setSmartWeekendFor(getCurrentScheduleId(), smart)
        notifyCourseChanged("settings")
    }

    /** 指定课表的开关写入（备份导入要写「别的那张」）。不发通知，由调用方统一发。 */
    private fun setSmartWeekendFor(scheduleId: String, smart: Boolean) {
        val rows = CourseStorage.rows
        if (rows == null) {
            NexioLog.e(TAG, "智能周末写失败：课程行表没有接线")
            return
        }
        ensureScheduleRow(rows, scheduleId)
        runCatching { rows.writeSmartWeekend(scheduleId, smart) }
            .onFailure { NexioLog.e(TAG, "智能周末写行失败：$scheduleId", it) }
    }

    /** 调休补班日也视为有课，智能周末据此决定是否显示该天 */
    fun hasCoursesOnDayInWeek(dayOfWeek: Int, week: Int): Boolean {
        if (getAllCourses().any { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week) }) return true
        return hasWorkSwapOnDay(dayOfWeek, week)
    }

    /** 调休跟随解析结果：绝对日期 + 该日期在**当前课表**下对应的 (周次, 星期) */
    data class WorkSwapFollow(val date: LocalDate, val week: Int, val weekday: Int)

    /**
     * 把调休条目的「跟随绝对日期」换算成当前课表的 (周次, 星期)。存的是日期，
     * 周次按当前课表学期开始时间现算，切换课表后自动改跟随（存周次本身会整体错位）。
     * @return null = 这条调休还没配跟随日期
     */
    fun resolveWorkSwapFollow(
        swap: HolidayEntry,
        scheduleId: String = getCurrentScheduleId(),
    ): WorkSwapFollow? {
        val date = swap.followLocalDate()
            // 老数据/老备份没有 followDate：按当前课表把 (周次, 星期) 还原成日期再走同一条路
            ?: runCatching { dateForTeachingWeekDay(swap.followWeek, swap.followWeekday, scheduleId) }
                .getOrNull()
                ?.takeIf { swap.followWeek > 0 && swap.followWeekday in 1..7 }
            ?: return null
        val position = teachingWeekPositionForDate(date, scheduleId)
        // 教学周重组把这天标成休课日时拿不到 weekday，退回日历星期
        val weekday = position.weekday ?: date.dayOfWeek.isoDayNumber
        return WorkSwapFollow(date, position.week.toInt(), weekday)
    }

    /** 待配置补班（未配跟随日期）不视为有课，避免智能周末误显示 */
    fun hasWorkSwapOnDay(dayOfWeek: Int, week: Int): Boolean {
        val swap = workSwapEntryOnDay(dayOfWeek, week) ?: return false
        return resolveWorkSwapFollow(swap) != null
    }

    /**
     * 该课表日「有没有课可上」：当天有课，或已配置调休且映射日/周有课。
     * 智能周末跳周用：无课可上（含未配置 followWeekday）→ 跳下周。
     */
    fun hasDisplayableCoursesOnDay(
        dayOfWeek: Int,
        week: Int,
        // 一次判定连查几天（如周六+周日）时传入共享快照，避免每天各跑一遍 loadAllByYear；
        // 懒加载：当天有直接课时整段不碰假期数据
        holidayEntriesByYear: Lazy<Map<Int, List<HolidayEntry>>> = lazy { HolidayManager.loadAllByYear() },
    ): Boolean {
        if (dayOfWeek !in 1..7) return false
        if (getAllCourses().any { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week) }) return true
        val swap = workSwapEntryOnDay(dayOfWeek, week, holidayEntriesByYear) ?: return false
        val follow = resolveWorkSwapFollow(swap) ?: return false
        return getAllCourses().any {
            it.dayOfWeek == follow.weekday && it.isActiveInWeek(follow.week)
        }
    }

    private fun workSwapEntryOnDay(
        dayOfWeek: Int,
        week: Int,
        holidayEntriesByYear: Lazy<Map<Int, List<HolidayEntry>>> = lazy { HolidayManager.loadAllByYear() },
    ): HolidayEntry? {
        if (dayOfWeek !in 1..7) return null
        val date = runCatching { dateForTeachingWeekDay(week, dayOfWeek) }.getOrNull() ?: return null
        return HolidayManager.workSwap(date, holidayEntriesByYear.value)
    }

    fun getShowNonCurrentWeek(): Boolean {
        return getShowNonCurrentWeek(getCurrentScheduleId())
    }

    fun getShowNonCurrentWeek(scheduleId: String): Boolean {
        // 只认表；行不存在取默认 true，不回看 prefs
        val row = CourseStorage.rows?.let { rows ->
            runCatching { rows.displaySettingsOf(scheduleId) }.getOrNull()
        }
        return row?.showNonCurrentWeek ?: true
    }

    fun setShowNonCurrentWeek(show: Boolean) {
        setShowNonCurrentWeekFor(getCurrentScheduleId(), show)
        notifyCourseChanged("settings")
    }

    /** 指定课表的开关写入（备份导入要写「别的那张」）。不发通知，由调用方统一发。 */
    private fun setShowNonCurrentWeekFor(scheduleId: String, show: Boolean) {
        val rows = CourseStorage.rows
        if (rows == null) {
            NexioLog.e(TAG, "显示非本周写失败：课程行表没有接线")
            return
        }
        ensureScheduleRow(rows, scheduleId)
        runCatching { rows.writeShowNonCurrentWeek(scheduleId, show) }
            .onFailure { NexioLog.e(TAG, "显示非本周课程写行失败：$scheduleId", it) }
    }

    override fun getMorningSections(): Int = getMorningSections(getCurrentScheduleId())

    fun getMorningSections(scheduleId: String): Int {
        val configId = getScheduleTimeConfigId(scheduleId)
        return getTimeConfig(configId).morningSections
    }

    fun setMorningSections(count: Int) {
        val scheduleId = getCurrentScheduleId()
        // 写路径一律 ensure：未绑定就走回退会改到别人的配置上
        val configId = ensureScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        saveTimeConfig(config.copy(morningSections = count))
        notifyCourseChanged("settings")
    }

    override fun getAfternoonSections(): Int = getAfternoonSections(getCurrentScheduleId())

    fun getAfternoonSections(scheduleId: String): Int {
        val configId = getScheduleTimeConfigId(scheduleId)
        return getTimeConfig(configId).afternoonSections
    }

    fun setAfternoonSections(count: Int) {
        val scheduleId = getCurrentScheduleId()
        val configId = ensureScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        saveTimeConfig(config.copy(afternoonSections = count))
        notifyCourseChanged("settings")
    }

    fun getEveningSections(): Int = getEveningSections(getCurrentScheduleId())

    fun getEveningSections(scheduleId: String): Int {
        val configId = getScheduleTimeConfigId(scheduleId)
        return getTimeConfig(configId).eveningSections
    }

    fun setEveningSections(count: Int) {
        val scheduleId = getCurrentScheduleId()
        val configId = ensureScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        saveTimeConfig(config.copy(eveningSections = count))
        notifyCourseChanged("settings")
    }

    /** period: "morning"/"afternoon"/"evening"；返回相对节次号 (1-6) -> "HH:mm-HH:mm" */
    override fun getPeriodTimes(period: String): Map<Int, String> {
        return getPeriodTimes(period, getCurrentScheduleId())
    }

    fun getPeriodTimes(period: String, scheduleId: String): Map<Int, String> {
        val configId = getScheduleTimeConfigId(scheduleId)
        // 走生效作息：同一套节次骨架下，按当天日期自动取夏令时/冬令时那套时间
        return effectiveConfig(configId).getPeriodTimes(period)
    }

    /**
     * 某配置在**今天**生效的那份作息。`effective()` 每次都把作息方案转对象、排序、整份 copy，
     * `CourseTimeResolver` 解一门课的起止时间会取 6 次时段表，逐次重算等于每门课 6 次深拷贝。
     * 缓存在这里，跨天或配置被改写时整批作废（见 [invalidateTimeCaches] / [saveTimeConfig]）。
     * 条目里带日期：跨天自动重算，条目数恒等于配置数，不会随运行天数增长。
     */
    private fun effectiveConfig(configId: Long): TimeConfig =
        effectiveConfigOn(configId, todayLocalDate())

    /**
     * 同 [effectiveConfig]，但按指定日期算。生产只用今天；测试用它钉「跨天必须重算」——
     * 缓存条目只比对日期，日期一变就重算，条目数恒等于配置数。
     */
    internal fun effectiveConfigOn(configId: Long, date: LocalDate): TimeConfig {
        effectiveConfigCache[configId]?.let { cached ->
            if (cached.date == date) return cached.config
        }
        val config = getTimeConfig(configId).effective(date)
        synchronizedOn(cacheLock) {
            effectiveConfigCache =
                effectiveConfigCache + (configId to EffectiveConfig(date, config))
        }
        return config
    }

    /**
     * 当前课表的全局节次表（未按节数裁剪，与 [com.haooz.chedule.data.model.CourseTimeResolver] 原先逐时段拼出来的内容一致）。
     * 返回的是 [globalSectionTimesCache] 里的实例，调用方只读。
     */
    override fun getSectionTimes(): Map<Int, String> = getGlobalSectionTimes()

    /** All configured section times, with afternoon and evening keys using global section numbers. */
    fun getCurrentSectionTimes(): Map<Int, String> {
        val sectionCount = getMorningSections() + getAfternoonSections() + getEveningSections()
        return getGlobalSectionTimes().filterKeys { it in 1..sectionCount }
    }

    fun getSectionTimes(scheduleId: String): Map<Int, String> {
        val morningCount = getMorningSections(scheduleId)
        val afternoonCount = getAfternoonSections(scheduleId)
        return buildMap {
            getPeriodTimes("morning", scheduleId).forEach { (index, time) -> put(index, time) }
            getPeriodTimes("afternoon", scheduleId).forEach { (index, time) -> put(morningCount + index, time) }
            getPeriodTimes("evening", scheduleId).forEach { (index, time) -> put(morningCount + afternoonCount + index, time) }
        }
    }

    fun savePeriodTimes(period: String, times: Map<Int, String>) {
        savePeriodTimes(period, times, getCurrentScheduleId())
    }

    /** 仅目标是当前课表时才通知，避免导入目标课表时无谓刷新当前页 */
    fun savePeriodTimes(period: String, times: Map<Int, String>, scheduleId: String) {
        val configId = ensureScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        // 编辑的是「当天生效的那套作息」，改完只写回它，不影响其它作息方案
        val activeRoutineId = config.effectiveRoutineId()
        val base = config.effective()
        val existing = base.sectionTimes.toMutableMap()
        existing.keys.filter { it.startsWith("${period}_") }.forEach { existing.remove(it) }
        for ((idx, v) in times) {
            existing["${period}_$idx"] = v
        }
        val updated = base.copy(sectionTimes = existing, quickTimeEnabled = false)
        saveTimeConfig(
            if (activeRoutineId == null) updated
            else config.withRoutineTimesApplied(activeRoutineId, updated)
        )
        if (scheduleId == getCurrentScheduleId()) notifyCourseChanged("settings")
    }

    fun getPreClassReminder(): Boolean {
        return prefs.getBoolean(KEY_PRE_CLASS_REMINDER, false)
    }

    fun setPreClassReminder(enabled: Boolean) {
        prefs.edit {putBoolean(KEY_PRE_CLASS_REMINDER, enabled) }
    }

    fun getPreClassReminderMinutes(): Int {
        return safeGetInt(KEY_PRE_CLASS_REMINDER_MINUTES, 20)
    }

    fun setPreClassReminderMinutes(minutes: Int) {
        prefs.edit {putInt(KEY_PRE_CLASS_REMINDER_MINUTES, minutes) }
    }

    /** 上课提醒起始节（默认 1，即从第一节开始提醒） */
    fun getPreClassReminderStartSection(): Int {
        return safeGetInt(KEY_PRE_CLASS_REMINDER_START_SECTION, 1).coerceAtLeast(1)
    }

    fun setPreClassReminderStartSection(section: Int) {
        prefs.edit { putInt(KEY_PRE_CLASS_REMINDER_START_SECTION, section.coerceAtLeast(1)) }
    }

    /** 上课提醒结束节；0 = 不限（一直到最后节） */
    fun getPreClassReminderEndSection(): Int {
        return safeGetInt(KEY_PRE_CLASS_REMINDER_END_SECTION, 0).coerceAtLeast(0)
    }

    fun setPreClassReminderEndSection(section: Int) {
        prefs.edit { putInt(KEY_PRE_CLASS_REMINDER_END_SECTION, section.coerceAtLeast(0)) }
    }

    fun getNextDayReminder(): Boolean {
        return prefs.getBoolean(KEY_NEXT_DAY_REMINDER, false)
    }

    fun setNextDayReminder(enabled: Boolean) {
        prefs.edit {putBoolean(KEY_NEXT_DAY_REMINDER, enabled) }
    }

    fun getNextDayReminderHour(): Int {
        return safeGetInt(KEY_NEXT_DAY_REMINDER_HOUR, 21)
    }

    fun setNextDayReminderHour(hour: Int) {
        prefs.edit { putInt(KEY_NEXT_DAY_REMINDER_HOUR, hour) }
    }

    fun getNextDayReminderMinute(): Int {
        return safeGetInt(KEY_NEXT_DAY_REMINDER_MINUTE, 0)
    }

    fun setNextDayReminderMinute(minute: Int) {
        prefs.edit { putInt(KEY_NEXT_DAY_REMINDER_MINUTE, minute) }
    }

    /** 获取今日课程/课程提醒标准版小组件的 padding 档位（0=标准, 1=4×6, 2=4×7, 3=4×6无字, 4=4×7无字） */
    fun getWidgetPaddingMode(): Int {
        return safeGetInt(KEY_WIDGET_PADDING_MODE, defaultWidgetPaddingMode)
    }

    /** 设置今日课程/课程提醒标准版小组件的 padding 档位（0=标准, 1=4×6, 2=4×7, 3=4×6无字, 4=4×7无字） */
    fun setWidgetPaddingMode(mode: Int) {
        prefs.edit { putInt(KEY_WIDGET_PADDING_MODE, mode) }
    }

    fun getIslandNotification(): Boolean {
        return prefs.getBoolean(KEY_ISLAND_NOTIFICATION, false)
    }

    fun setIslandNotification(enabled: Boolean) {
        prefs.edit {putBoolean(KEY_ISLAND_NOTIFICATION, enabled) }
    }

    /** 「上课自动开启勿扰」总开关；需已授予勿扰权限 */
    fun getClassDndEnabled(): Boolean {
        return prefs.getBoolean(KEY_CLASS_DND, false)
    }

    fun setClassDndEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CLASS_DND, enabled) }
    }

    /** 0=勿扰 1=静音 2=优先；默认 1，通知仍可查看 */
    fun getClassDndMode(): Int {
        return safeGetInt(KEY_CLASS_DND_MODE, 1)
    }

    fun setClassDndMode(mode: Int) {
        prefs.edit { putInt(KEY_CLASS_DND_MODE, mode) }
    }

    /**
     * 占用判断为分钟级时间重叠；自定义时间课按实际起止参与，无法确定时回退节次重叠。
     * @param excludeIds 编辑时排除自身；@param startTime/endTime 自定义时间课传 "HH:mm"
     */
    fun getOccupiedWeeks(
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        excludeIds: Set<String> = emptySet(),
        startTime: String? = null,
        endTime: String? = null
    ): Set<Int> {
        // 无排除条件且非自定义时间时使用缓存
        if (excludeIds.isEmpty() && startTime == null && endTime == null) {
            occupiedWeeksCache[occupiedWeeksKey(dayOfWeek, startSection, endSection)]?.let { return it }
        }

        val sectionTimes = getGlobalSectionTimes()
        val newStartMin = if (startTime != null) {
            timeToMinutes(startTime)
        } else {
            timeToMinutes(sectionTimes[startSection]?.substringBefore("-")?.trim())
        }
        val newEndMin = if (endTime != null) {
            timeToMinutes(endTime)
        } else {
            timeToMinutes(sectionTimes[endSection]?.substringAfter("-")?.trim())
        }

        val occupied = mutableSetOf<Int>()
        getAllCourses().forEach { course ->
            if (course.id in excludeIds) return@forEach
            if (course.dayOfWeek == dayOfWeek &&
                isTimeConflict(newStartMin, newEndMin, startSection, endSection, course, sectionTimes)
            ) {
                addCourseWeeks(occupied, course)
            }
        }

        if (excludeIds.isEmpty() && startTime == null && endTime == null) {
            val key = occupiedWeeksKey(dayOfWeek, startSection, endSection)
            synchronizedOn(cacheLock) {
                occupiedWeeksCache = occupiedWeeksCache + (key to occupied)
            }
        }

        return occupied
    }

    /** 必须带课表 ID，否则切换课表后会命中另一课表的缓存 */
    private fun occupiedWeeksKey(dayOfWeek: Int, startSection: Int, endSection: Int): String =
        "${getCurrentScheduleId()}_${dayOfWeek}_${startSection}_${endSection}"

    private fun timeToMinutes(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    /** 上午原编号，下午/晚上按节数偏移后的全局绝对节次映射 */
    private fun getGlobalSectionTimes(): Map<Int, String> {
        globalSectionTimesCache?.let { return it }
        val morning = getPeriodTimes("morning")
        val afternoon = getPeriodTimes("afternoon")
        val evening = getPeriodTimes("evening")
        val morningSections = getMorningSections()
        val afternoonSections = getAfternoonSections()
        val times = buildMap {
            morning.forEach { (k, v) -> put(k, v) }
            afternoon.forEach { (k, v) -> put(morningSections + k, v) }
            evening.forEach { (k, v) -> put(morningSections + afternoonSections + k, v) }
        }
        globalSectionTimesCache = times
        return times
    }

    private fun addCourseWeeks(occupied: MutableSet<Int>, course: Course) {
        if (course.selectedWeeks.isNotEmpty()) {
            occupied.addAll(course.selectedWeeks)
        } else {
            for (week in course.startWeek..course.endWeek) {
                when (course.weekType) {
                    Course.WEEK_TYPE_ODD -> if (week % 2 == 1) occupied.add(week)
                    Course.WEEK_TYPE_EVEN -> if (week % 2 == 0) occupied.add(week)
                    else -> occupied.add(week)
                }
            }
        }
    }

    /**
     * 分钟级重叠判断；相邻（end==start）不算冲突。时间无法确定时回退节次重叠。
     * @param sectionTimes 全局绝对节次号 -> "HH:mm-HH:mm"
     */
    private fun isTimeConflict(
        newStartMin: Int?,
        newEndMin: Int?,
        newStartSection: Int,
        newEndSection: Int,
        existing: Course,
        sectionTimes: Map<Int, String>
    ): Boolean {
        val existingStart = timeToMinutes(existing.getEffectiveStartTime(sectionTimes))
        val existingEnd = timeToMinutes(existing.getEffectiveEndTime(sectionTimes))
        if (newStartMin != null && newEndMin != null && existingStart != null && existingEnd != null) {
            // 跨午夜时段要先展开再比，否则 `23:00-00:30` 与 `00:00-01:00` 永远判不出冲突
            return CourseTimeSpan.overlaps(
                newStartMin, newEndMin,
                existingStart, existingEnd,
            )
        }
        return existing.startSection <= newEndSection && existing.endSection >= newStartSection
    }

    @Suppress("UNUSED_PARAMETER") // week: 槽位共享所有周次，同槽冲突课程无论周次均需展示
    fun getCoursesAtSlot(
        week: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): List<Course> {
        return getAllCourses().filter { course ->
            course.dayOfWeek == dayOfWeek &&
            course.startSection <= endSection &&
            course.endSection >= startSection
        }.sortedBy { it.startSection }
    }

    // ── 课表目录与文件夹（实现在 [ScheduleCatalog]）──────────────────────────

    fun getScheduleNames(): List<String> = catalog.getScheduleNames()

    internal fun saveScheduleNames(names: List<String>) = catalog.saveScheduleNames(names)

    fun getCurrentScheduleId(): String = catalog.getCurrentScheduleId()

    fun setCurrentScheduleId(scheduleId: String) = catalog.setCurrentScheduleId(scheduleId)

    fun addSchedule(name: String): List<String> = catalog.addSchedule(name)

    fun createDefaultTimeConfigForSchedule(name: String) =
        catalog.createDefaultTimeConfigForSchedule(name)

    fun createNewSemesterSchedule(name: String): List<String> = catalog.createNewSemesterSchedule(name)

    fun deleteSchedule(name: String): List<String> = catalog.deleteSchedule(name)

    fun renameSchedule(oldName: String, newName: String): List<String> =
        catalog.renameSchedule(oldName, newName)

    fun getScheduleFolders(): List<ScheduleFolder> = catalog.getScheduleFolders()

    internal fun saveScheduleFolders(folders: List<ScheduleFolder>) =
        catalog.saveScheduleFolders(folders)

    fun addScheduleFolder(name: String): List<ScheduleFolder> = catalog.addScheduleFolder(name)

    fun renameScheduleFolder(id: String, newName: String): List<ScheduleFolder> =
        catalog.renameScheduleFolder(id, newName)

    fun disbandScheduleFolder(id: String): List<ScheduleFolder> = catalog.disbandScheduleFolder(id)

    fun moveSchedulesToFolder(
        scheduleNames: List<String>,
        folderId: String?,
    ): List<ScheduleFolder> = catalog.moveSchedulesToFolder(scheduleNames, folderId)

    fun getFolderIdOfSchedule(scheduleName: String): String? =
        catalog.getFolderIdOfSchedule(scheduleName)

    fun getRootScheduleNames(): List<String> = catalog.getRootScheduleNames()

    /** 绑定失效时必须回退到旧全局指针的解析值，不能 `firstOrNull()` —— 见 [pruneOrphanTimeConfigsIfNeeded] */
    fun getScheduleTimeConfigId(scheduleId: String): Long {
        boundTimeConfigIdOrNull(scheduleId)?.let { return it }
        return resolveLegacyCurrentTimeConfigId()
    }

    /**
     * 这张课表绑定的配置。未绑定返回 null。
     *
     * 只读 `schedule.time_config_id`。prefs 的 `schedule_time_config_{名}` 是迁移源，
     * 迁移成功后就删了 —— 再回看它就是读一份会过期的旧值。
     */
    private fun boundTimeConfigIdOrNull(scheduleId: String): Long? {
        val snapshot = bindingSnapshot
        // 按 id 集合校验：删配置后旧缓存自动判成未绑定，无需手动失效
        snapshot.values[scheduleId]?.let { return it.takeIf { id -> id != 0L && id in getTimeConfigIds() } }

        val resolved = resolveBoundTimeConfigId(scheduleId)
        synchronizedOn(cacheLock) {
            val current = bindingSnapshot
            // 查表期间有人写过绑定（代次变了）：这次结果可能是旧的，不落缓存
            if (current.generation == snapshot.generation) {
                bindingSnapshot = current.copy(values = current.values + (scheduleId to resolved))
            }
        }
        return resolved.takeIf { it != 0L && it in getTimeConfigIds() }
    }

    /**
     * 绑定可能变了：换一代，旧值全部作废。
     *
     * 所有会改 `schedule.time_config_id` 的路径都要走这里 —— 不只是「记得清缓存」，
     * 代次还负责让「查表期间被写入」的读线程丢弃自己那份过期结果。
     */
    private fun invalidateBindingCache() {
        synchronizedOn(cacheLock) {
            bindingSnapshot = BindingSnapshot(bindingSnapshot.generation + 1, emptyMap())
        }
    }

    /** 真正的存储查询：只认 `schedule.time_config_id`。0 表示未绑定。 */
    private fun resolveBoundTimeConfigId(scheduleId: String): Long {
        val fromRow = CourseStorage.rows?.let { rows ->
            runCatching { rows.bindingOf(scheduleId) }.getOrNull()
        } ?: return 0L
        return if (fromRow in getTimeConfigIds()) fromRow else 0L
    }

    /**
     * 当前被任何课表占用的配置 id。只认 `schedule.time_config_id`。
     *
     * @return null 表示判据不可靠（行表在但读失败），调用方按「不知道」处理。
     */
    private fun boundTimeConfigIdsInUse(): Set<Long>? {
        val rows = CourseStorage.rows ?: return null
        val fromRows = runCatching { rows.bindings() }.getOrNull() ?: return null
        return fromRows.values.filter { it != 0L }.toSet()
    }

    /**
     * 写入前取这张课表自己的配置 id，未绑定就当场绑上。读取可以回退，写入不能 ——
     * 否则会改到它回退到的那套，即另一张课表的作息。
     */
    private fun ensureScheduleTimeConfigId(scheduleId: String): Long {
        boundTimeConfigIdOrNull(scheduleId)?.let { return it }
        val fallbackId = resolveLegacyCurrentTimeConfigId()
        // 判据读不到时按「已被占用」处理：复制独立配置，不过户别人的作息
        val boundElsewhere = boundTimeConfigIdsInUse()?.let { fallbackId in it } ?: true
        val id = if (boundElsewhere) {
            addTimeConfig(getTimeConfig(fallbackId).copy(id = 0L, name = scheduleId))
        } else {
            fallbackId
        }
        // 0 是「一个配置都没有」的兜底值，写进绑定会被当成有绑定
        if (id == 0L) return 0L
        NexioLog.w(
            TAG,
            "课表 $scheduleId 未绑定时间配置，已补绑 id=$id（fallback=$fallbackId 被他人绑定=$boundElsewhere）"
        )
        setScheduleTimeConfigId(scheduleId, id)
        return id
    }

    /**
     * 改绑作息。`schedule.time_config_id` 是唯一真相。
     *
     * prefs 的 `schedule_time_config_{名}` 只在没有行表的平台写（那里 prefs 就是真相）。
     * 有行表时不再写穿：备份从表物化绑定（见 [materializeRowBackedKeys]），迁移完成后
     * 这个键会被 [purgeMigratedPrefsKeys] 清掉。
     */
    fun setScheduleTimeConfigId(scheduleId: String, timeConfigId: Long) {
        // 0 是「一个配置都没有」的兜底值，不写进绑定
        if (timeConfigId == 0L) return

        val rows = CourseStorage.rows
        if (rows == null) {
            NexioLog.e(TAG, "改绑失败：课程行表没有接线 schedule=$scheduleId id=$timeConfigId")
            return
        }

        val anchor = rowAnchorFor(scheduleId, timeConfigId)
        if (anchor == null) {
            // 锚点建不出来说明库或作息表有问题：不能假装绑成功，否则界面显示新绑定、重启又变回去
            NexioLog.e(TAG, "作息绑定拿不到锚点，本次改绑未生效：schedule=$scheduleId id=$timeConfigId")
            return
        }
        val written = runCatching { rows.setBinding(anchor) }
            .onFailure { NexioLog.w(TAG, "作息绑定写行表失败：schedule=$scheduleId id=$timeConfigId", it) }
            .isSuccess
        if (!written) return

        // 写穿：换代次并只放这一条 —— 其他条目可能是在这次写入之前查的，一起作废
        synchronizedOn(cacheLock) {
            bindingSnapshot = BindingSnapshot(
                generation = bindingSnapshot.generation + 1,
                values = mapOf(scheduleId to timeConfigId),
            )
        }
        invalidateTimeCaches()
    }

    fun switchToSchedule(scheduleId: String) {
        setCurrentScheduleId(scheduleId)
        // 补绑必须走 ensure：[getScheduleTimeConfigId] 对未绑定会回退成非 0，不能用它判是否已绑
        val timeConfigId = ensureScheduleTimeConfigId(scheduleId)
        if (timeConfigId != 0L) {
            val config = getTimeConfig(timeConfigId)
            setCurrentTimeConfigId(timeConfigId)
            applyTimeConfigToSchedule(config)
        }
        notifyCourseChanged("settings")
    }

    fun isShiftModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHIFT_MODE, false)
    }

    fun setShiftModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHIFT_MODE, enabled) }
    }

    fun getShiftSelectedSchedules(): List<String> {
        val json = prefs.getStringOrNull(KEY_SHIFT_SELECTED_SCHEDULES)
        return try {
            if (json.isNullOrBlank()) emptyList()
            else runCatching { decodeStringList(json) }.getOrNull() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setShiftSelectedSchedules(names: List<String>) {
        val json = encodeStringList(names)
        prefs.edit {putString(KEY_SHIFT_SELECTED_SCHEDULES, json) }
    }

    fun getDefaultHomepage(): String {
        return prefs.getString(KEY_DEFAULT_HOMEPAGE, "课程表")
    }

    fun setDefaultHomepage(homepage: String) {
        prefs.edit {putString(KEY_DEFAULT_HOMEPAGE, homepage) }
    }

    // --- 多时间配置支持 ---

    /** 获取所有时间配置 ID 列表（按创建顺序）。只认 `time_config` 表。 */
    fun getTimeConfigIds(): List<Long> {
        timeConfigIdsCache?.let { return it }
        hydrateTimeConfigs()
        timeConfigIdsCache?.let { return it }
        // 表里一条配置都没有（全新安装）：默认配置 id 是 0，由 getTimeConfig 播种
        val ids = listOf(0L)
        timeConfigIdsCache = ids
        return ids
    }

    /**
     * 表 → 内存缓存，进程内一次。
     *
     * 不再做「首次访问把 prefs 同步进表」：那一步在迁移里做完了，
     * 留着它只会在恢复备份后拿空 prefs 覆盖正确的行。
     */
    private fun hydrateTimeConfigs() {
        if (timeConfigsHydrated) return
        val rows = CourseStorage.rows ?: return
        val all = runCatching { rows.timeConfigs() }.getOrNull().orEmpty()
        if (all.isNotEmpty()) {
            val parsed = all.mapNotNull { row ->
                runCatching {
                    parseTimeConfigSnapshotOrNull(
                        row.payload
                    )
                }.getOrNull()
                    ?.let { TimeConfig.sanitize(row.id, it) }
                    ?.let { it.id to it }
            }.toMap()
            synchronizedOn(cacheLock) {
                timeConfigCache = timeConfigCache + parsed
            }
            // 0 是「一个配置都没有」的兜底 id，不进 id 列表（进了会被当成一个真配置）
            all.map { it.id }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.let { timeConfigIdsCache = it }
        }
        timeConfigsHydrated = true
    }

    /** 更新 id 列表缓存。id 列表的真相是 `time_config` 表本身，这里只碰内存。 */
    private fun saveTimeConfigIds(ids: List<Long>) {
        timeConfigIdsCache = ids
    }

    fun getCurrentTimeConfigId(): Long {
        val scheduleId = getCurrentScheduleId()
        boundTimeConfigIdOrNull(scheduleId)?.let { return it }
        // 未绑定：回退旧全局指针并补绑，避免节数被重置为默认
        val resolved = resolveLegacyCurrentTimeConfigId()
        setScheduleTimeConfigId(scheduleId, resolved)
        return resolved
    }

    private fun resolveLegacyCurrentTimeConfigId(): Long {
        val legacy = prefs.getLong(KEY_CURRENT_TIME_CONFIG_ID, 0L)
        if (legacy in getTimeConfigIds()) return legacy
        return getTimeConfigIds().firstOrNull() ?: 0L
    }

    fun setCurrentTimeConfigId(id: Long) {
        prefs.edit { putLong(KEY_CURRENT_TIME_CONFIG_ID, id) }
    }

    fun getTimeConfig(id: Long): TimeConfig {
        timeConfigCache[id]?.let { return it }
        // 表是唯一真相：hydrate 一次，之后全走内存缓存
        hydrateTimeConfigs()
        timeConfigCache[id]?.let { return it }
        // 兜底配置必须经 sanitize 播种（内部 ensureRoutine 补默认作息）：裸默认配置 routines
        // 为空，而 saveRoutine 按存储态校验「目标作息是否存在」，两头对不上会静默丢弃保存。
        val fallback = TimeConfig.sanitize(id, TimeConfig(id = id, name = "默认配置"))
        // id=0 没数据是全新安装的正常状态；别的 id 读不到 = 配置真丢了，会静默回落 4/4/4
        if (id != 0L) NexioLog.e(TAG, "time_config $id 不存在，回落默认配置")
        // 缺行必须落盘自愈：不写回去，routines 就一直是空的，保存链路永远起不来
        saveTimeConfig(fallback)
        return fallback
    }

    /** 节数与节次时间都来自这里，保存时同步失效占用/全局节次缓存 */
    fun saveTimeConfig(config: TimeConfig) {
        val json = encodeTimeConfig(config)
        val rows = CourseStorage.rows
        if (rows == null) {
            NexioLog.e(TAG, "作息写失败：课程行表没有接线")
        } else {
            runCatching { rows.upsertTimeConfig(StoredTimeConfig(config.id, config.name, json)) }
                .onFailure { NexioLog.e(TAG, "作息写表失败：id=${config.id}", it) }
        }
        synchronizedOn(cacheLock) {
            timeConfigCache = timeConfigCache + (config.id to config)
            // 刚编码过就顺手填上，下一次保存课程不必再把整份作息重编码
            encodedTimeConfigCache =
                encodedTimeConfigCache + (config.id to EncodedTimeConfig(config, json))
        }
        invalidateTimeCaches()
    }

    fun addTimeConfig(config: TimeConfig): Long {
        // id 必须按**表**里的最大值算：缓存可能落后于表（迁移 / 恢复备份后重建），
        // 撞号会 upsert 掉别人的作息 —— 表现是「改一张表的作息，另一张表跟着变」。
        val ids = runCatching { CourseStorage.rows?.timeConfigs()?.map { it.id } }.getOrNull()
            ?: getTimeConfigIds()
        val newId = (ids.maxOrNull() ?: 0L) + 1L
        val newConfig = config.ensureRoutine().copy(id = newId)
        saveTimeConfigIds(ids + newId)
        saveTimeConfig(newConfig)
        return newId
    }

    /**
     * 直接改写 TimeConfig 后广播一次「设置变更」。[saveTimeConfig] 只落盘、不发通知
     *（它被很多内部批量写调用），所以「改一改再存回去」的路径必须自己补这一下，
     * 否则课程提醒 / 小部件 / 手表推送仍按改动前的时间工作。
     */
    fun notifyTimeConfigChanged() {
        notifyCourseChanged("settings")
    }

    /**
     * 删除当前课表的一个作息方案；至少保留一个，删不动时返回 false。
     * 删除后「生效的那套」可能换人（也可能时间变了），必须广播，否则提醒 / 小部件仍按旧作息排时间。
     */
    fun deleteRoutine(routineId: Long): Boolean {
        val configId = ensureScheduleTimeConfigId(getCurrentScheduleId())
        val original = getTimeConfig(configId)
        val remaining = original.withRoutineRemoved(routineId)
        if (remaining.safeRoutines.size == original.safeRoutines.size) return false
        saveTimeConfig(remaining)
        notifyCourseChanged("settings")
        return true
    }

    /**
     * 保存某个作息方案的时间数据，并广播变更让课表页重算。节次骨架不在这里改。
     * @return false 表示没找到目标作息、什么都没写；命中不到时必须留下日志。
     */
    fun saveRoutine(routineId: Long, edited: TimeConfig, nameOverride: String? = null): Boolean {
        val scheduleId = getCurrentScheduleId()
        val base = getTimeConfig(ensureScheduleTimeConfigId(scheduleId))
        val routine = edited.routineOf(routineId, nameOverride)
        if (base.routineById(routineId) == null) {
            NexioLog.w(
                TAG,
                "saveRoutine: 目标作息不存在 config=${base.id} routineId=$routineId " +
                    "现有=${base.safeRoutines.map { it.id }}，本次保存被丢弃"
            )
            return false
        }
        saveTimeConfig(base.withRoutineReplaced(routineId, routine))
        notifyCourseChanged("settings")
        return true
    }

    /** 清理没有课表绑定的孤儿配置。删除不可逆，判据不足时不动手（见 [collectBoundTimeConfigIds]）。 */
    fun pruneOrphanTimeConfigsIfNeeded() {
        val ids = getTimeConfigIds()
        if (ids.size <= 1) return
        val unbound = getScheduleNames().filter { boundTimeConfigIdOrNull(it) == null }
        if (unbound.isNotEmpty()) {
            // 未绑定的课表回退到旧全局指针，指针值在下面的判据里
            NexioLog.w(TAG, "课表 $unbound 未绑定时间配置，按旧指针计入在用水位（ids=$ids）")
        }
        val bound = collectBoundTimeConfigIds() ?: run {
            NexioLog.w(TAG, "在用水位读不到（行表查询失败），跳过清理（ids=$ids）")
            return
        }
        val orphans = ids.filter { it !in bound }
        if (orphans.isEmpty()) return
        if (orphans.size >= ids.size) {
            NexioLog.w(TAG, "孤儿配置占满全部候选，判据可疑，不处理（ids=$ids）")
            return
        }
        val kept = ids.filter { it in bound }
        saveTimeConfigIds(kept)
        prefs.edit { orphans.forEach { remove("$TIME_CONFIG_PREFIX$it") } }
        CourseStorage.rows?.let { rows ->
            orphans.forEach { orphan -> runCatching { rows.deleteTimeConfigRow(orphan) } }
        }
        synchronizedOn(cacheLock) {
            timeConfigCache = timeConfigCache - orphans.toSet()
            encodedTimeConfigCache = encodedTimeConfigCache - orphans.toSet()
        }
        NexioLog.w(TAG, "pruneOrphanTimeConfigs: 保留=$kept 删除=$orphans")
    }

    /**
     * 「正在被使用」的判据：全部课表绑定（行表 ∪ prefs 镜像，含已删课表的残留）
     * + 旧全局指针及其解析值（未绑定的课表会回退到它）。
     */
    private fun collectBoundTimeConfigIds(): Set<Long>? {
        val bound = boundTimeConfigIdsInUse()?.toMutableSet() ?: return null
        bound.add(prefs.getLong(KEY_CURRENT_TIME_CONFIG_ID, 0L))
        bound.add(resolveLegacyCurrentTimeConfigId())
        return bound
    }

    fun getCurrentTimeConfig(): TimeConfig {
        return getTimeConfig(getCurrentTimeConfigId())
    }

    /**
     * 当前课表**可写**的配置：未绑定会先补绑（见 `ensureScheduleTimeConfigId`）。
     *
     * 改作息 / 改节次的页面必须用它取配置 —— 拿 [getScheduleTimeConfigId] 的返回值去
     * `saveTimeConfig` 时，未绑定的课表会把改动写进它回退到的那套（别人的）配置。
     */
    fun getWritableCurrentTimeConfig(): TimeConfig {
        return getTimeConfig(ensureScheduleTimeConfigId(getCurrentScheduleId()))
    }

    /** 同 [getWritableCurrentTimeConfig]，作用于指定课表（如教务导入的目标课表） */
    fun getWritableTimeConfig(scheduleId: String): TimeConfig {
        return getTimeConfig(ensureScheduleTimeConfigId(scheduleId))
    }

    /**
     * 跨过作息生效日期后，补一次「设置变更」广播，让课程提醒 / 小部件按新作息重排：
     * 作息到日期自动切换、切换那一刻没有写操作，而提醒闹钟只在设置变更时重排。
     * 只在生效作息真的换了时才广播；prefs 里没值时只登记不重排，首次排程交给冷启动链路。
     */
    fun syncRoutineAfterDateChange(): Boolean {
        val scheduleId = getCurrentScheduleId()
        val config = getTimeConfig(getScheduleTimeConfigId(scheduleId))
        val key = "${config.id}:${config.effectiveRoutineId() ?: -1L}"
        val prefKey = "${SCHEDULE_KEY_PREFIX}${scheduleId}_active_routine"
        val last = prefs.getStringOrNull(prefKey)

        // 顺带把顶层镜像刷成当天生效的那套：跨日期切换本身没有写操作，不刷新会停在
        // 上一个作息上，只读顶层的旧版本 App 与导出 prefs 原始 JSON 的备份会拿到过期时间。
        val effective = config.effective()
        if (effective != config) saveTimeConfig(effective)

        if (last == key) return false
        prefs.edit { putString(prefKey, key) }
        // 首次登记不重排，避免新装 / 首次升级时白跑一次全量闹钟排程
        if (last == null) return false
        notifyCourseChanged("settings")
        return true
    }

    /** 相对 key "morning_1" 转全局绝对节次号 -> 名称；取当天生效作息的名称 */
    fun getSectionNames(): Map<Int, String> {
        val config = getCurrentTimeConfig().effective()
        val names = mutableMapOf<Int, String>()
        for ((k, v) in config.sectionNames) {
            val parts = k.split("_")
            if (parts.size != 2) continue
            val period = parts[0]
            val idx = parts[1].toIntOrNull() ?: continue
            val abs = when (period) {
                "morning" -> idx
                "afternoon" -> config.morningSections + idx
                "evening" -> config.morningSections + config.afternoonSections + idx
                else -> continue
            }
            if (v.isNotBlank()) names[abs] = v
        }
        return names
    }

    /** 当前课表 + 当天生效作息合成后的配置；读时间统一走它 */
    fun getEffectiveTimeConfig(): TimeConfig = getCurrentTimeConfig().effective()

    fun getEffectiveTimeConfig(scheduleId: String): TimeConfig =
        getTimeConfig(getScheduleTimeConfigId(scheduleId)).effective()

    /**
     * 把另一个课表的时间配置（含全部作息方案）完整复制过来直接使用：
     * 一个课表只绑定一个时间配置，所以这里是「覆盖」而非「新增/切换」。
     */
    fun copyTimeConfigFromSchedule(
        sourceScheduleId: String,
        targetScheduleId: String = getCurrentScheduleId()
    ): Boolean {
        if (sourceScheduleId == targetScheduleId) return false
        val targetId = ensureScheduleTimeConfigId(targetScheduleId)
        val source = getTimeConfig(getScheduleTimeConfigId(sourceScheduleId))
        val target = getTimeConfig(targetId)
        // 节次骨架变了就按相对位置平移课程，避免课被挤到网格外
        remapCoursesForNewSectionCounts(
            source.morningSections, source.afternoonSections, source.eveningSections, targetScheduleId
        )
        saveTimeConfig(source.copy(id = targetId, name = target.name))
        setScheduleTimeConfigId(targetScheduleId, targetId)
        notifyCourseChanged("settings")
        return true
    }

    /** 软导入：覆盖绑定配置以免之后被旧配置盖回；共享 id0 时新建专属配置 */
    fun applyTimeImportToCurrentSchedule(
        morningSections: Int, afternoonSections: Int, eveningSections: Int,
        morningTimes: Map<Int, String>, afternoonTimes: Map<Int, String>, eveningTimes: Map<Int, String>
    ) = applyTimeImportToSchedule(
        getCurrentScheduleId(), morningSections, afternoonSections, eveningSections,
        morningTimes, afternoonTimes, eveningTimes
    )

    /** 同 applyTimeImportToCurrentSchedule，作用于指定课表 */
    fun applyTimeImportToSchedule(
        scheduleId: String,
        morningSections: Int, afternoonSections: Int, eveningSections: Int,
        morningTimes: Map<Int, String>, afternoonTimes: Map<Int, String>, eveningTimes: Map<Int, String>
    ) {
        // 未绑定时必须 ensure：直接回退会把时间写进别的课表那套配置
        val configId = ensureScheduleTimeConfigId(scheduleId)

        val sectionTimes = buildMap {
            morningTimes.forEach { (k, v) -> put("morning_$k", v) }
            afternoonTimes.forEach { (k, v) -> put("afternoon_$k", v) }
            eveningTimes.forEach { (k, v) -> put("evening_$k", v) }
        }

        if (configId != 0L) {
            val base = getTimeConfig(configId)
            val updated = base.copy(
                name = base.name.ifBlank { scheduleId },
                morningSections = morningSections,
                afternoonSections = afternoonSections,
                eveningSections = eveningSections,
                quickTimeEnabled = false,
                sectionTimes = sectionTimes
            )
            val routineId = base.effectiveRoutineId()
            saveTimeConfig(
                if (routineId == null) updated
                else base.withRoutineTimesApplied(routineId, updated)
            )
        } else {
            val newId = addTimeConfig(
                TimeConfig(
                    name = scheduleId,
                    morningSections = morningSections,
                    afternoonSections = afternoonSections,
                    eveningSections = eveningSections,
                    quickTimeEnabled = false,
                    sectionTimes = sectionTimes
                )
            )
            setScheduleTimeConfigId(scheduleId, newId)
        }
    }

    /**
     * 教务导入：把脚本下发的默认单节时长 / 课间休息时长落到目标课表的 TimeConfig。
     * 只改 [TimeConfig.classDuration] / [TimeConfig.shortBreak]，节数、节次时间、作息方案原样保留；
     * 未绑定时新建并绑定。与「作息时间是否为空」无关，这两个值始终作为骨架参数落库。
     */
    fun applyImportedDefaultDurationsToSchedule(
        scheduleId: String,
        classDuration: Int,
        shortBreak: Int,
    ) {
        val configId = ensureScheduleTimeConfigId(scheduleId)
        if (configId != 0L) {
            val base = getTimeConfig(configId)
            saveTimeConfig(
                base.copy(
                    name = base.name.ifBlank { scheduleId },
                    classDuration = classDuration,
                    shortBreak = shortBreak,
                )
            )
        } else {
            val newId = addTimeConfig(
                TimeConfig(
                    name = scheduleId,
                    classDuration = classDuration,
                    shortBreak = shortBreak,
                )
            )
            setScheduleTimeConfigId(scheduleId, newId)
        }
    }

    /**
     * 节数变化时保持课程时段相对位置；不向上钳制，缩节数后课暂落网格外。
     */
    private fun remapCoursesForNewSectionCounts(
        newMorning: Int, newAfternoon: Int, newEvening: Int,
        scheduleId: String = getCurrentScheduleId()
    ) {
        val oldMorning = getMorningSections(scheduleId)
        val oldAfternoon = getAfternoonSections(scheduleId)
        val oldEvening = getEveningSections(scheduleId)
        if (oldMorning == newMorning && oldAfternoon == newAfternoon && oldEvening == newEvening) return

        var changed = false
        val remapped = getCoursesForSchedule(scheduleId).map { course ->
            val newStart = remapSection(
                course.startSection, oldMorning, oldAfternoon, newMorning, newAfternoon
            )
            val newEnd = remapSection(
                course.endSection, oldMorning, oldAfternoon, newMorning, newAfternoon
            )
            if (newStart != course.startSection || newEnd != course.endSection) {
                changed = true
                course.copy(startSection = newStart, endSection = newEnd)
            } else {
                course
            }
        }
        if (changed) saveCourses(remapped)
    }

    /**
     * 保持时段内相对位置映射节次号；不向上钳制，缩节数后课暂落网格外，恢复后自动归位。
     */
    private fun remapSection(
        section: Int,
        oldMorning: Int, oldAfternoon: Int,
        newMorning: Int, newAfternoon: Int
    ): Int {
        val oldStart: Int
        val period: Int
        when {
            section <= oldMorning -> { oldStart = 1; period = 0 }
            section <= oldMorning + oldAfternoon -> {
                oldStart = oldMorning + 1; period = 1
            }
            else -> {
                oldStart = oldMorning + oldAfternoon + 1; period = 2
            }
        }
        val relative = (section - oldStart).coerceAtLeast(0)
        val newStart = when (period) {
            0 -> 1
            1 -> newMorning + 1
            else -> newMorning + newAfternoon + 1
        }
        return newStart + relative
    }

    private fun extractPeriodTimes(
        sectionTimes: Map<String, String>,
        period: String
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for ((k, v) in sectionTimes) {
            if (!k.startsWith("${period}_")) continue
            val idx = k.removePrefix("${period}_").toIntOrNull() ?: continue
            result[idx] = v
        }
        return result
    }

    /**
     * 一次写入完整配置（旧实现 20+ setter 造成多次磁盘提交）。
     * savePeriodTimes 会静默关掉 quickTimeEnabled，这里显式保持关闭以对齐历史行为。
     */
    private fun applyTimeConfigToSchedule(config: TimeConfig) {
        val scheduleId = getCurrentScheduleId()
        val configId = ensureScheduleTimeConfigId(scheduleId)
        val base = getTimeConfig(configId)

        // 配置空则回退默认时段时间；非空但某时段缺失时保留原值
        val sectionTimes: Map<String, String> = if (config.sectionTimes.isNotEmpty()) {
            val merged = base.sectionTimes.toMutableMap()
            for (period in listOf("morning", "afternoon", "evening")) {
                val times = extractPeriodTimes(config.sectionTimes, period)
                if (times.isEmpty()) continue
                merged.keys.filter { it.startsWith("${period}_") }.forEach { merged.remove(it) }
                times.forEach { (idx, v) -> merged["${period}_$idx"] = v }
            }
            merged
        } else {
            buildMap {
                Course.defaultMorningTimes.forEach { (k, v) -> put("morning_$k", v) }
                Course.defaultAfternoonTimes.forEach { (k, v) -> put("afternoon_$k", v) }
                Course.defaultEveningTimes.forEach { (k, v) -> put("evening_$k", v) }
            }
        }

        batchingSettings = true
        try {
            val updated = base.copy(
                morningSections = config.morningSections,
                    afternoonSections = config.afternoonSections,
                    eveningSections = config.eveningSections,
                    // 与旧 savePeriodTimes 一致：应用配置时快速时间保持关闭
                    quickTimeEnabled = false,
                    classDuration = config.classDuration,
                    shortBreak = config.shortBreak,
                    longBreakEnabled = config.longBreakEnabled,
                    longBreakMorning = config.longBreakMorning,
                    longBreakAfternoon = config.longBreakAfternoon,
                    longBreakEvening = config.longBreakEvening,
                    longBreakMorningSection = config.longBreakMorningSection,
                    longBreakAfternoonSection = config.longBreakAfternoonSection,
                    longBreakEveningSection = config.longBreakEveningSection,
                    morningStartHour = config.morningStartHour,
                    morningStartMinute = config.morningStartMinute,
                    afternoonStartHour = config.afternoonStartHour,
                    afternoonStartMinute = config.afternoonStartMinute,
                    eveningStartHour = config.eveningStartHour,
                    eveningStartMinute = config.eveningStartMinute,
                    sectionTimes = sectionTimes
                )
            val routineId = base.effectiveRoutineId()
            saveTimeConfig(
                if (routineId == null) updated
                else base.withRoutineTimesApplied(routineId, updated)
            )
        } finally {
            batchingSettings = false
        }
        commitSettingsChanged()
    }

    fun getSectionsForSchedule(scheduleId: String): Triple<Int, Int, Int> {
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        return Triple(config.morningSections, config.afternoonSections, config.eveningSections)
    }

    // ── 备份与恢复（实现在 [BackupTransfer]）─────────────────────────────────

    private val backup by lazy { BackupTransfer(host) }

    fun exportAllPreferences(): Map<String, Any> = backup.exportAllPreferences()

    fun importAllPreferences(data: Map<String, Any>) {
        backup.importAllPreferences(data)
    }

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
        holiday: Map<String, Any?>? = null,
    ) {
        backup.importSingleSchedule(
            scheduleName = scheduleName,
            coursesData = coursesData,
            timeConfigData = timeConfigData,
            classStartTime = classStartTime,
            currentWeek = currentWeek,
            totalWeeks = totalWeeks,
            teachingWeekReorganizations = teachingWeekReorganizations,
            smartWeekend = smartWeekend,
            showNonCurrentWeek = showNonCurrentWeek,
            holiday = holiday,
        )
    }
}

/** 教学周重组规则的解析缓存：原始串一致就直接复用，存储侧写入后自动失效。 */
private data class CachedReorganizations(
    val raw: String?,
    val rules: List<TeachingWeekReorganizationRule>,
)

/** [CourseRepository] 生效作息的缓存条目：日期 + 当天生效的作息。跨天自然失效，条目数恒等于配置数。 */
private data class EffectiveConfig(
    val date: LocalDate,
    val config: TimeConfig,
)

/** 已编码的作息配置。`config` 按引用比对，配置被替换成新实例时自然重编码。 */
private data class EncodedTimeConfig(
    val config: TimeConfig,
    val json: String,
)
