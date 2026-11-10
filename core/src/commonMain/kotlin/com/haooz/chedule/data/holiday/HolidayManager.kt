package com.haooz.chedule.data.holiday

import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.platform.ScheduleEffects
import com.haooz.chedule.data.platform.synchronizedOn
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.KeyValueStore
import com.haooz.chedule.data.util.jsonObjectOf
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optRaw
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonArray
import com.haooz.chedule.data.util.parseJsonElement
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.data.util.plusDays
import kotlin.collections.get
import kotlin.collections.iterator
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ════════════════════════════════════════════════════════════════════════
//  节假日与调休 · 存储层（加载 / 备份 / 数据源）
//
//  数据按课表分区：每张课表一个 `holiday_settings_<课表名十六进制>` 命名空间
//  （课表名可含 `/`，存储层拿命名空间当 prefs 文件名，故整体转十六进制）；
//  `holiday_settings` 只留全局数据源设置。全量备份按课表导出。
//
//  [HolidayEntry.toJson] 产出的串以 `entries_{年}` 为键直接落盘，字段名、字段顺序、
//  紧凑格式与存量数据逐字一致，由 `HolidayManagerStorageTest` 做 round-trip 回归。
// ════════════════════════════════════════════════════════════════════════

/** 节假日与调休数据。假期跳过提醒，调休按配置的课表周次和星期调度。 */
object HolidayManager {
    private const val PREFS = "holiday_settings"
    private const val KEY_PREFIX = "entries_"
    private const val KEY_VERSION = "version"
    private const val PARTITION_NAMESPACE_PREFIX = "holiday_settings_"
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val KEY_PARTITION_MIGRATED = "schedule_partition_migrated_v1"

    // 这 4 个键必须 public：`:app` 的 CourseRepository 要跨模块过滤全量备份里的节假日键
    const val BACKUP_KEY = "holiday_entries"
    const val BACKUP_EXCLUSION_KEY = "holiday_end_course_exclusion"
    const val BACKUP_BEFORE_EXCLUSION_KEY = "holiday_before_course_exclusion"
    /** 全量备份里按课表分区的节假日配置。 */
    const val BACKUP_BY_SCHEDULE_KEY = "holiday_by_schedule"

    private const val KEY_EXCLUSION_ENABLED = "end_course_exclusion_enabled"
    private const val KEY_EXCLUSION_START_SECTION = "end_course_exclusion_start_section"
    private const val KEY_EXCLUSION_END_SECTION = "end_course_exclusion_end_section"
    private const val KEY_BEFORE_EXCLUSION_ENABLED = "before_course_exclusion_enabled"
    private const val KEY_BEFORE_EXCLUSION_START_SECTION = "before_course_exclusion_start_section"
    private const val KEY_BEFORE_EXCLUSION_END_SECTION = "before_course_exclusion_end_section"
    private const val BACKUP_SCHEMA_VERSION = 1
    private const val BACKUP_SCHEMA_VERSION_KEY = "schema_version"
    private const val KEY_FOLLOW_DATE_MIGRATED = "follow_date_migrated"

    private val EXCLUSION_KEYS = setOf(
        KEY_EXCLUSION_ENABLED, KEY_EXCLUSION_START_SECTION, KEY_EXCLUSION_END_SECTION,
        KEY_BEFORE_EXCLUSION_ENABLED, KEY_BEFORE_EXCLUSION_START_SECTION,
        KEY_BEFORE_EXCLUSION_END_SECTION,
    )

    /** 绑定 store 实例：AppStorage 换实例（测试 / 重置）后要重跑迁移。 */
    @Volatile
    private var migratedStore: KeyValueStore? = null
    private val _dataRevision = MutableStateFlow(0L)
    val dataRevision = _dataRevision.asStateFlow()

    /**
     * [loadAllByYear] 的快照缓存。它每次都是「prefs 全量快照 + 逐年反序列化」，
     * 而主线程的智能周末判定、小组件 / 提醒的逐日课表解析都会反复调它。
     * 键 = 存储实例 + 版本号：任何写入都会换版本号，[AppStorage] 换实例（测试 / 重置）也不再命中。
     */
    private class EntriesSnapshot(
        val store: KeyValueStore,
        val version: Long,
        val entries: Map<Int, List<HolidayEntry>>,
    )

    @Volatile
    private var allByYearSnapshot: EntriesSnapshot? = null

    // 转发到 [HolidayEntry]，调用点 `HolidayManager.TYPE_HOLIDAY` 保持可用
    const val TYPE_HOLIDAY = HolidayEntry.TYPE_HOLIDAY
    const val TYPE_WORKSWAP = HolidayEntry.TYPE_WORKSWAP

    /** 保护「读 prefs → 算 → 写 prefs」的复合操作（后台迁移线程与主线程保存会交错）。 */
    private val lock = Any()

    private fun globalStore(): KeyValueStore = AppStorage.store(PREFS)

    private fun currentScheduleId(): String =
        CourseRepository.getInstance().getCurrentScheduleId()

    /**
     * 课表名可含任意字符，而存储层会拿命名空间当 SharedPreferences 文件名做一次性导入，
     * 文件名不允许路径分隔符 —— 统一转十六进制，保证只含 `[0-9a-f]`。
     */
    private fun namespaceFor(scheduleId: String): String =
        PARTITION_NAMESPACE_PREFIX + hexOf(scheduleId)

    private fun hexOf(value: String): String = buildString(value.length * 2) {
        for (byte in value.encodeToByteArray()) {
            val v = byte.toInt() and 0xFF
            append(HEX_DIGITS[v ushr 4])
            append(HEX_DIGITS[v and 0x0F])
        }
    }

    private fun storeFor(scheduleId: String): KeyValueStore {
        migrateToSchedulePartitionsOnce()
        return AppStorage.store(namespaceFor(scheduleId))
    }

    private fun store(): KeyValueStore = storeFor(currentScheduleId())

    /** 老全局数据归给当前课表，每张课表一份。只跑一次，可重入。 */
    fun migrateToSchedulePartitionsOnce() {
        val global = globalStore()
        if (migratedStore === global && global.getBoolean(KEY_PARTITION_MIGRATED, false)) return
        synchronizedOn(lock) {
            if (migratedStore === global && global.getBoolean(KEY_PARTITION_MIGRATED, false)) {
                return@synchronizedOn
            }
            if (!global.getBoolean(KEY_PARTITION_MIGRATED, false)) {
                val legacy = global.all()
                val dataKeys = legacy.keys.filter {
                    it.startsWith(KEY_PREFIX) || it in EXCLUSION_KEYS ||
                        it == KEY_VERSION || it == KEY_FOLLOW_DATE_MIGRATED
                }
                if (dataKeys.isNotEmpty()) {
                    val target = AppStorage.store(namespaceFor(currentScheduleId()))
                    // 同一个 store 实例时「复制 → 删源」会把刚复制的键删掉，数据留在原地即可
                    if (target !== global) {
                        target.edit {
                            for (key in dataKeys) {
                                when (val value = legacy[key]) {
                                    is String -> putString(key, value)
                                    is Int -> putInt(key, value)
                                    is Boolean -> putBoolean(key, value)
                                    is Long -> putLong(key, value)
                                    is Float -> putFloat(key, value)
                                }
                            }
                        }
                        global.edit { dataKeys.forEach(::remove) }
                    }
                }
                global.edit { putBoolean(KEY_PARTITION_MIGRATED, true) }
            }
            allByYearSnapshot = null
            migratedStore = global
        }
    }

    /** 课表改名：整份分区跟着搬。 */
    fun onScheduleRenamed(oldId: String, newId: String) = synchronizedOn(lock) {
        if (oldId == newId) return@synchronizedOn
        val from = AppStorage.store(namespaceFor(oldId))
        val to = AppStorage.store(namespaceFor(newId))
        if (from === to) return@synchronizedOn
        val snapshot = from.all()
        if (snapshot.isEmpty()) return@synchronizedOn
        to.edit {
            for ((key, value) in snapshot) {
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                }
            }
        }
        from.edit { snapshot.keys.forEach(::remove) }
        clearSnapshot()
    }

    /** 课表删除：分区一起清掉。 */
    fun onScheduleDeleted(id: String) = synchronizedOn(lock) {
        val target = AppStorage.store(namespaceFor(id))
        if (target === globalStore()) return@synchronizedOn
        val keys = target.all().keys
        if (keys.isNotEmpty()) target.edit { keys.forEach(::remove) }
        clearSnapshot()
    }

    /** 开启新学期：复制当前课表的节假日配置作为新表初值。 */
    fun onScheduleCopied(fromId: String, toId: String) = synchronizedOn(lock) {
        if (fromId == toId) return@synchronizedOn
        val from = AppStorage.store(namespaceFor(fromId))
        val to = AppStorage.store(namespaceFor(toId))
        if (from === to) return@synchronizedOn
        val snapshot = from.all()
        if (snapshot.isEmpty()) return@synchronizedOn
        to.edit {
            for ((key, value) in snapshot) {
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                }
            }
        }
        clearSnapshot()
    }

    /** 切换当前课表：让按 getVersion 记忆的 UI 重新取数。 */
    fun notifyScheduleSwitched() {
        migrateToSchedulePartitionsOnce()
        val target = AppStorage.store(namespaceFor(currentScheduleId()))
        val prev = runCatching { target.getLong(KEY_VERSION, 0L) }.getOrDefault(0L)
        val next = maxOf(Clock.System.now().toEpochMilliseconds(), prev + 1L)
        target.edit { putLong(KEY_VERSION, next) }
        _dataRevision.value = next
        allByYearSnapshot = null
    }

    private fun clearSnapshot() {
        allByYearSnapshot = null
    }

    data class BackupData(
        val entries: Map<String, String>,
        val exclusion: HolidayEndCourseExclusion,
        val beforeExclusion: HolidayBeforeCourseExclusion = HolidayBeforeCourseExclusion(),
    )

    /** 纯查询转发到 [HolidayEntries]，行为不变。 */
    fun entriesForDate(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
    ): List<HolidayEntry> = HolidayEntries.entriesForDate(entriesByYear, date)

    // ── 加载 ────────────────────────────────────

    fun load(year: Int): List<HolidayEntry> = synchronizedOn(lock) { loadLocked(year) }

    private fun loadLocked(year: Int): List<HolidayEntry> = loadLocked(store(), year)

    private fun loadLocked(store: KeyValueStore, year: Int): List<HolidayEntry> {
        val key = "$KEY_PREFIX$year"
        // KeyValueStore 无可空默认值，先判存在；缺键 → 空列表
        if (!store.contains(key)) return emptyList()
        val raw = store.getString(key, "")
        val array = runCatching { parseJsonArray(raw) }.getOrNull() ?: return emptyList()
        return readEntriesSafely(array.size) { index ->
            // 元素不是对象时抛，被 readEntriesSafely 的 runCatching 吃掉 → 跳过该行
            parseStoredEntry(array[index] as? JsonObject ?: error("Invalid holiday entry"))
                ?: error("Invalid holiday entry")
        }
    }

    internal fun readEntriesSafely(size: Int, readEntry: (Int) -> HolidayEntry): List<HolidayEntry> =
        buildList {
            repeat(size) { index ->
                runCatching { readEntry(index) }
                    .getOrNull()
                    ?.takeIf(::isValidEntry)
                    ?.let(::add)
            }
        }

    /** 加载所有有存量条目的年份，保留存储年份用于课表查找。按「存储实例 + 版本号」缓存。 */
    fun loadAllByYear(): Map<Int, List<HolidayEntry>> = synchronizedOn(lock) {
        val store = store()
        val version = runCatching { store.getLong(KEY_VERSION, 0L) }.getOrDefault(0L)
        allByYearSnapshot
            ?.takeIf { it.store === store && it.version == version }
            ?.let { return@synchronizedOn it.entries }
        val years = storedEntryYears(store.all().keys)
        val entries = years.associateWith { loadLocked(store, it) }
        allByYearSnapshot = EntriesSnapshot(store, version, entries)
        entries
    }

    /** 指定课表的全年条目；手环按目标课表推送时用。 */
    fun loadAllByYearFor(scheduleId: String): Map<Int, List<HolidayEntry>> {
        migrateToSchedulePartitionsOnce()
        return synchronizedOn(lock) {
            val target = AppStorage.store(namespaceFor(scheduleId))
            storedEntryYears(target.all().keys).associateWith { loadLocked(target, it) }
        }
    }

    /** 指定课表某一年的条目。 */
    fun loadFor(year: Int, scheduleId: String): List<HolidayEntry> {
        migrateToSchedulePartitionsOnce()
        return synchronizedOn(lock) {
            loadLocked(AppStorage.store(namespaceFor(scheduleId)), year)
        }
    }

    /** 按年原样导出存量 JSON，含自定义映射与跨年范围。 */
    fun exportBackupEntries(): Map<String, String> =
        synchronizedOn(lock) { exportBackupEntries(store()) }

    internal fun exportBackupEntries(store: KeyValueStore): Map<String, String> =
        backupEntries(store.all())

    internal fun backupEntries(stored: Map<String, *>): Map<String, String> =
        stored.mapNotNull { (key, value) ->
            if (storedYearFromKey(key) != null && value is String) key to value else null
        }.toMap()

    // ── 假期课程剔除设置 ─────────────────────────

    fun loadEndCourseExclusion(): HolidayEndCourseExclusion =
        synchronizedOn(lock) { loadEndCourseExclusion(store()) }

    internal fun loadEndCourseExclusion(store: KeyValueStore): HolidayEndCourseExclusion {
        val enabled = runCatching { store.getBoolean(KEY_EXCLUSION_ENABLED, false) }
            .getOrDefault(false)
        val startSection = runCatching {
            store.getInt(KEY_EXCLUSION_START_SECTION, 1)
        }.getOrDefault(1)
        val endSection = runCatching {
            store.getInt(KEY_EXCLUSION_END_SECTION, 1)
        }.getOrDefault(1)
        return HolidayEndCourseExclusion(enabled, startSection, endSection)
            .takeIf(HolidayEndCourseExclusion::isValid)
            ?: HolidayEndCourseExclusion()
    }

    fun saveEndCourseExclusion(value: HolidayEndCourseExclusion): Boolean =
        synchronizedOn(lock) { saveEndCourseExclusion(store(), value) }

    internal fun saveEndCourseExclusion(store: KeyValueStore, value: HolidayEndCourseExclusion): Boolean {
        if (!value.isValid()) return false
        val previousVersion = runCatching { store.getLong(KEY_VERSION, 0L) }.getOrDefault(0L)
        val newVersion = maxOf(Clock.System.now().toEpochMilliseconds(), previousVersion + 1L)
        store.edit {
            putBoolean(KEY_EXCLUSION_ENABLED, value.enabled)
            putInt(KEY_EXCLUSION_START_SECTION, value.startSection)
            putInt(KEY_EXCLUSION_END_SECTION, value.endSection)
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = newVersion
        return true
    }

    fun loadBeforeCourseExclusion(): HolidayBeforeCourseExclusion =
        synchronizedOn(lock) { loadBeforeCourseExclusion(store()) }

    internal fun loadBeforeCourseExclusion(store: KeyValueStore): HolidayBeforeCourseExclusion {
        val enabled = runCatching { store.getBoolean(KEY_BEFORE_EXCLUSION_ENABLED, false) }
            .getOrDefault(false)
        val startSection = runCatching {
            store.getInt(KEY_BEFORE_EXCLUSION_START_SECTION, 1)
        }.getOrDefault(1)
        val endSection = runCatching {
            store.getInt(KEY_BEFORE_EXCLUSION_END_SECTION, 1)
        }.getOrDefault(1)
        return HolidayBeforeCourseExclusion(enabled, startSection, endSection)
            .takeIf(HolidayBeforeCourseExclusion::isValid)
            ?: HolidayBeforeCourseExclusion()
    }

    fun saveBeforeCourseExclusion(value: HolidayBeforeCourseExclusion): Boolean =
        synchronizedOn(lock) { saveBeforeCourseExclusion(store(), value) }

    internal fun saveBeforeCourseExclusion(
        store: KeyValueStore,
        value: HolidayBeforeCourseExclusion,
    ): Boolean {
        if (!value.isValid()) return false
        val previousVersion = runCatching { store.getLong(KEY_VERSION, 0L) }.getOrDefault(0L)
        val newVersion = maxOf(Clock.System.now().toEpochMilliseconds(), previousVersion + 1L)
        store.edit {
            putBoolean(KEY_BEFORE_EXCLUSION_ENABLED, value.enabled)
            putInt(KEY_BEFORE_EXCLUSION_START_SECTION, value.startSection)
            putInt(KEY_BEFORE_EXCLUSION_END_SECTION, value.endSection)
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = newVersion
        return true
    }

    // ── 备份导出 / 还原 ──────────────────────────

    fun exportBackupData(): Map<String, Any> = synchronizedOn(lock) { exportBackupData(store()) }

    internal fun exportBackupData(store: KeyValueStore): Map<String, Any> {
        val exclusion = loadEndCourseExclusion(store)
        val beforeExclusion = loadBeforeCourseExclusion(store)
        return mapOf(
            BACKUP_KEY to exportBackupEntries(store),
            BACKUP_EXCLUSION_KEY to mapOf(
                BACKUP_SCHEMA_VERSION_KEY to BACKUP_SCHEMA_VERSION,
                "enabled" to exclusion.enabled,
                "startSection" to exclusion.startSection,
                "endSection" to exclusion.endSection,
            ),
            BACKUP_BEFORE_EXCLUSION_KEY to mapOf(
                BACKUP_SCHEMA_VERSION_KEY to BACKUP_SCHEMA_VERSION,
                "enabled" to beforeExclusion.enabled,
                "startSection" to beforeExclusion.startSection,
                "endSection" to beforeExclusion.endSection,
            ),
        )
    }

    fun decodeBackupData(backup: Map<String, Any?>): BackupData = BackupData(
        entries = decodeBackupEntries(backup),
        exclusion = decodeBackupEndCourseExclusion(backup),
        beforeExclusion = decodeBackupBeforeCourseExclusion(backup),
    )

    internal fun decodeBackupBeforeCourseExclusion(
        backup: Map<String, Any?>,
    ): HolidayBeforeCourseExclusion {
        if (BACKUP_BEFORE_EXCLUSION_KEY !in backup) return HolidayBeforeCourseExclusion()
        val value = backup[BACKUP_BEFORE_EXCLUSION_KEY]
        require(value is Map<*, *>) { "Invalid holiday before-course exclusion data" }
        val schemaVersion = backupInteger(value[BACKUP_SCHEMA_VERSION_KEY])
        require(schemaVersion == BACKUP_SCHEMA_VERSION) { "Unsupported holiday backup schema" }
        val enabled = value["enabled"] as? Boolean
            ?: throw IllegalArgumentException("Invalid holiday before-course exclusion enabled state")
        val startSection = backupInteger(value["startSection"])
        val endSection = backupInteger(value["endSection"])
        val exclusion = HolidayBeforeCourseExclusion(enabled, startSection, endSection)
        require(exclusion.isValid()) { "Invalid holiday before-course exclusion section range" }
        return exclusion
    }

    internal fun decodeBackupEndCourseExclusion(
        backup: Map<String, Any?>,
    ): HolidayEndCourseExclusion {
        if (BACKUP_EXCLUSION_KEY !in backup) return HolidayEndCourseExclusion()
        val value = backup[BACKUP_EXCLUSION_KEY]
        require(value is Map<*, *>) { "Invalid holiday end-course exclusion data" }
        val schemaVersion = backupInteger(value[BACKUP_SCHEMA_VERSION_KEY])
        require(schemaVersion == BACKUP_SCHEMA_VERSION) { "Unsupported holiday backup schema" }
        val enabled = value["enabled"] as? Boolean
            ?: throw IllegalArgumentException("Invalid holiday end-course exclusion enabled state")
        val startSection = backupInteger(value["startSection"])
        val endSection = backupInteger(value["endSection"])
        val exclusion = HolidayEndCourseExclusion(enabled, startSection, endSection)
        require(exclusion.isValid()) { "Invalid holiday end-course exclusion section range" }
        return exclusion
    }

    private fun backupInteger(value: Any?): Int {
        require(isStoredInteger(value)) { "Invalid integer in holiday backup" }
        return (value as Number).toInt()
    }

    /** 老全量备份缺该字段 = 空的节假日配置。 */
    internal fun decodeBackupEntries(backup: Map<String, Any?>): Map<String, String> {
        if (BACKUP_KEY !in backup) return emptyMap()
        val value = backup[BACKUP_KEY]
        require(value is Map<*, *>) { "Invalid holiday backup data" }
        return value.entries.associate { (key, raw) ->
            require(key is String && storedYearFromKey(key) != null && raw is String &&
                hasOnlyValidStoredRows(raw)) {
                "Invalid holiday backup entry"
            }
            key to raw
        }
    }

    fun restoreBackupData(data: BackupData) =
        synchronizedOn(lock) { restoreBackupData(store(), data) }

    internal fun restoreBackupData(store: KeyValueStore, data: BackupData) {
        require(data.exclusion.isValid()) { "Invalid holiday end-course exclusion section range" }
        require(data.beforeExclusion.isValid()) {
            "Invalid holiday before-course exclusion section range"
        }
        val previousVersion = runCatching { store.getLong(KEY_VERSION, 0L) }.getOrDefault(0L)
        val newVersion = maxOf(Clock.System.now().toEpochMilliseconds(), previousVersion + 1L)
        store.edit {
            store.all().keys.filter { it.startsWith(KEY_PREFIX) }.forEach(::remove)
            data.entries.forEach { (key, raw) -> putString(key, raw) }
            putBoolean(KEY_EXCLUSION_ENABLED, data.exclusion.enabled)
            putInt(KEY_EXCLUSION_START_SECTION, data.exclusion.startSection)
            putInt(KEY_EXCLUSION_END_SECTION, data.exclusion.endSection)
            putBoolean(KEY_BEFORE_EXCLUSION_ENABLED, data.beforeExclusion.enabled)
            putInt(KEY_BEFORE_EXCLUSION_START_SECTION, data.beforeExclusion.startSection)
            putInt(KEY_BEFORE_EXCLUSION_END_SECTION, data.beforeExclusion.endSection)
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = newVersion
    }

    /** 单课表导出：只取该课表的节假日配置。 */
    fun exportBackupDataFor(scheduleId: String): Map<String, Any> {
        migrateToSchedulePartitionsOnce()
        return synchronizedOn(lock) {
            exportBackupData(AppStorage.store(namespaceFor(scheduleId)))
        }
    }

    /** 单课表导入：写回目标课表分区。 */
    fun restoreBackupDataFor(scheduleId: String, data: BackupData) {
        migrateToSchedulePartitionsOnce()
        synchronizedOn(lock) {
            restoreBackupData(AppStorage.store(namespaceFor(scheduleId)), data)
            allByYearSnapshot = null
        }
    }

    /** 全量备份导出：每张课表一份配置。 */
    fun exportAllSchedulesBackupData(scheduleIds: List<String>): Map<String, Any> {
        migrateToSchedulePartitionsOnce()
        return synchronizedOn(lock) {
            mapOf(BACKUP_BY_SCHEDULE_KEY to scheduleIds.associateWith { id ->
                exportBackupData(AppStorage.store(namespaceFor(id)))
            })
        }
    }

    /** 全量备份解码：老备份的单份数据归给当前课表。 */
    fun decodeAllSchedulesBackupData(
        backup: Map<String, Any?>,
        legacyScheduleId: String? = null,
    ): Map<String, BackupData> {
        val raw = backup[BACKUP_BY_SCHEDULE_KEY]
            ?: return mapOf(
                (legacyScheduleId ?: currentScheduleId()) to decodeBackupData(backup)
            )
        require(raw is Map<*, *>) { "Invalid holiday schedule backup" }
        return raw.entries.associate { (key, value) ->
            require(key is String && value is Map<*, *>) { "Invalid holiday schedule backup" }
            key to decodeBackupData(value.entries.associate { (k, v) -> k.toString() to v })
        }
    }

    /** 全量恢复：先清掉所有已知课表的分区，再按备份写回。 */
    fun restoreAllSchedulesBackupData(
        data: Map<String, BackupData>,
        scheduleIds: List<String>,
    ) {
        migrateToSchedulePartitionsOnce()
        synchronizedOn(lock) {
            for (id in scheduleIds.distinct()) {
                val target = AppStorage.store(namespaceFor(id))
                val keys = target.all().keys
                if (keys.isNotEmpty()) target.edit { keys.forEach(::remove) }
            }
            for ((id, backupData) in data) {
                restoreBackupData(AppStorage.store(namespaceFor(id)), backupData)
            }
            allByYearSnapshot = null
        }
    }

    /** 只替换节假日条目；恢复时不回退运行时版本号。 */
    fun restoreBackupEntries(entries: Map<String, String>) =
        synchronizedOn(lock) { restoreBackupEntries(store(), entries) }

    internal fun restoreBackupEntries(store: KeyValueStore, entries: Map<String, String>) {
        val previousVersion = store.getLong(KEY_VERSION, 0L)
        val newVersion = maxOf(Clock.System.now().toEpochMilliseconds(), previousVersion + 1L)
        store.edit {
            store.all().keys.filter { it.startsWith(KEY_PREFIX) }.forEach(::remove)
            entries.forEach { (key, raw) -> putString(key, raw) }
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = newVersion
    }

    internal fun storedEntryYears(preferenceKeys: Set<String>): List<Int> =
        preferenceKeys.asSequence()
            .mapNotNull(::storedYearFromKey)
            .distinct()
            .sorted()
            .toList()

    private fun storedYearFromKey(key: String): Int? {
        if (!key.startsWith(KEY_PREFIX)) return null
        val value = key.removePrefix(KEY_PREFIX)
        val year = value.toIntOrNull() ?: return null
        return year.takeIf { it.toString() == value }
    }

    // ── 纯查询（不碰存储）─────────────────────────

    fun entriesOverlapping(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        firstDate: LocalDate,
        lastDate: LocalDate,
    ): List<HolidayEntry> {
        if (lastDate < firstDate) return emptyList()
        // 按 key 排序后展开，顺序与原来的 toSortedMap 一致
        return entriesByYear.entries.sortedBy { it.key }.flatMap { (year, entries) ->
            entries.filter { entry ->
                val startDate = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                    ?: return@filter false
                val endDate = if (entry.endDate.isBlank()) {
                    startDate
                } else {
                    runCatching { LocalDate.parse(entry.endDate) }.getOrNull()
                        ?: return@filter false
                }
                endDate >= firstDate && startDate <= lastDate
            }.map { year to it }
        }.sortedWith(
            compareBy<Pair<Int, HolidayEntry>> { it.second.custom }
                .thenBy { it.first }
        ).map { it.second }
    }

    fun entriesByDateRange(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        firstDate: LocalDate,
        lastDate: LocalDate,
    ): Map<String, List<HolidayEntry>> {
        if (lastDate < firstDate) return emptyMap()
        return buildMap {
            var date = firstDate
            while (true) {
                entriesForDate(entriesByYear, date).takeIf { it.isNotEmpty() }?.let {
                    put(date.toString(), it)
                }
                if (date == lastDate) break
                date = date.plusDays(1)
            }
        }
    }

    fun withoutCustomWorkSwapsOnDate(entries: List<HolidayEntry>, date: String): List<HolidayEntry> =
        entries.filterNot {
            it.type == TYPE_WORKSWAP && it.custom && it.date == date
        }

    fun withoutEntry(entries: List<HolidayEntry>, entry: HolidayEntry): List<HolidayEntry> =
        buildList {
            var removed = false
            entries.forEach { candidate ->
                if (!removed && candidate == entry) {
                    removed = true
                } else {
                    add(candidate)
                }
            }
        }

    internal fun canOverwriteStoredEntries(raw: String?, existingDataIsValid: Boolean): Boolean =
        raw == null || existingDataIsValid

    internal fun isStoredOptionalIntValid(value: Any?, present: Boolean): Boolean =
        !present || isStoredInteger(value)

    internal fun isStoredOptionalBooleanValid(value: Any?, present: Boolean): Boolean =
        !present || value is Boolean

    internal fun allStoredRowsValid(size: Int, readEntry: (Int) -> HolidayEntry): Boolean =
        (0 until size).all { index ->
            runCatching { readEntry(index) }
                .getOrNull()
                ?.let(::isValidEntry) == true
        }

    // ── 存量数据解析（org.json 语义）──────────────

    /**
     * 解析 `entries_{年}` 里的一行。
     *
     * ⚠ 用 [optRaw]（返回原始值）而非 `optString` / `optInt`：后者会做强制转换，
     * 语义不同（`optString` 会把数字 `1` 变成 `"1"`）。
     */
    private fun parseStoredEntry(item: JsonObject): HolidayEntry? {
        val date = item.optRaw("date") as? String ?: return null
        val endDate = if (item.containsKey("endDate")) {
            item.optRaw("endDate") as? String ?: return null
        } else ""
        val name = item.optRaw("name") as? String ?: return null
        val typeValue = item.optRaw("type")
        if (!isStoredInteger(typeValue)) return null
        if (!isStoredOptionalIntValid(item.optRaw("followWeek"), item.containsKey("followWeek"))) {
            return null
        }
        if (!isStoredOptionalIntValid(item.optRaw("followWeekday"), item.containsKey("followWeekday"))) {
            return null
        }
        if (!isStoredOptionalBooleanValid(item.optRaw("custom"), item.containsKey("custom"))) {
            return null
        }
        val followDate = if (item.containsKey("followDate")) {
            item.optRaw("followDate") as? String ?: return null
        } else ""

        val entry = HolidayEntry(
            date = date,
            endDate = endDate,
            name = name,
            type = (typeValue as Number).toInt(),
            followDate = followDate,
            followWeek = (item.optRaw("followWeek") as? Number)?.toInt() ?: -1,
            followWeekday = (item.optRaw("followWeekday") as? Number)?.toInt() ?: -1,
            custom = item.optRaw("custom") as? Boolean ?: false,
        )
        return entry.takeIf(::isValidEntry)
    }

    private fun isStoredInteger(value: Any?): Boolean =
        (value as? Number)?.toDouble()?.let { number ->
            number.isFinite() && number % 1.0 == 0.0 &&
                number >= Int.MIN_VALUE && number <= Int.MAX_VALUE
        } == true

    private fun isValidEntry(entry: HolidayEntry): Boolean {
        if (entry.type !in TYPE_HOLIDAY..TYPE_WORKSWAP || entry.name.isBlank()) return false
        val startDate = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return false
        val endDate = if (entry.endDate.isBlank()) {
            startDate
        } else {
            runCatching { LocalDate.parse(entry.endDate) }.getOrNull() ?: return false
        }
        if (entry.type == TYPE_WORKSWAP) {
            if (endDate != startDate) return false
            if (entry.followDate.isNotBlank() &&
                runCatching { LocalDate.parse(entry.followDate) }.isFailure
            ) return false
            if (entry.followWeek != -1 && entry.followWeek !in 1..52) return false
            if (entry.followWeekday != -1 && entry.followWeekday !in 1..7) return false
        }
        return endDate >= startDate
    }

    // ── 备份里的条目行（Gson 语义）────────────────

    private fun hasOnlyValidStoredRows(raw: String): Boolean = runCatching {
        val json = parseJsonElement(raw)
        json is JsonArray && json.all { parseBackupEntry(it) != null }
    }.getOrDefault(false)

    /**
     * 解析备份里的条目行。
     *
     * 用「`JsonPrimitive` + `isString`」区分字符串与字面量：`isString == false` 时
     * `content` 只可能是数字 / `true` / `false` / `null`。
     */
    private fun parseBackupEntry(element: JsonElement): HolidayEntry? {
        val item = element as? JsonObject ?: return null
        val date = backupString(item["date"]) ?: return null
        val endDate = if (item.containsKey("endDate")) {
            backupString(item["endDate"]) ?: return null
        } else ""
        val name = backupString(item["name"]) ?: return null
        val type = backupJsonInteger(item["type"]) ?: return null
        val followWeek = if (item.containsKey("followWeek")) {
            backupJsonInteger(item["followWeek"]) ?: return null
        } else -1
        val followWeekday = if (item.containsKey("followWeekday")) {
            backupJsonInteger(item["followWeekday"]) ?: return null
        } else -1
        val custom = if (item.containsKey("custom")) {
            backupJsonBoolean(item["custom"]) ?: return null
        } else false
        // 老备份没有 followDate：留空，由 followWeek/followWeekday 在迁移时补
        val followDate = if (item.containsKey("followDate")) {
            backupString(item["followDate"]) ?: return null
        } else ""
        return HolidayEntry(
            date = date,
            endDate = endDate,
            name = name,
            type = type,
            followDate = followDate,
            followWeek = followWeek,
            followWeekday = followWeekday,
            custom = custom,
        ).takeIf(::isValidEntry)
    }

    private fun backupString(value: JsonElement?): String? {
        val primitive = value as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }

    private fun backupJsonInteger(value: JsonElement?): Int? {
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        val number = primitive.content.toDoubleOrNull() ?: return null
        return number.takeIf(::isStoredInteger)?.toInt()
    }

    private fun backupJsonBoolean(value: JsonElement?): Boolean? {
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return when (primitive.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    // ── 写入 ────────────────────────────────────

    fun updateEntries(
        years: Set<Int>,
        transform: (Map<Int, List<HolidayEntry>>) -> Map<Int, List<HolidayEntry>>,
    ): Boolean = synchronizedOn(lock) {
        if (years.isEmpty()) return@synchronizedOn true
        val store = store()
        val currentRaw = years.associateWith { year ->
            val key = "$KEY_PREFIX$year"
            if (store.contains(key)) store.getString(key, "") else null
        }
        val canWrite = currentRaw.values.all { raw ->
            canOverwriteStoredEntries(
                raw,
                raw == null || hasOnlyValidStoredRows(raw),
            )
        }
        if (!canWrite) return@synchronizedOn false

        val currentEntries = years.associateWith { loadLocked(it) }
        val updatedEntries = transform(currentEntries)
        if (updatedEntries.keys != years || updatedEntries.values.flatten()
                .any { !isValidEntry(it) }
        ) {
            return@synchronizedOn false
        }

        val serializedEntries = updatedEntries.mapValues { (_, entries) ->
            JsonArray(entries.sortedBy { it.date }.map { it.toJson() }).toString()
        }
        // 单调递增：同一毫秒内两次 save 也要变号，避免 UI 版本对比失效
        val prev = store.getLong(KEY_VERSION, 0L)
        val newVersion = maxOf(Clock.System.now().toEpochMilliseconds(), prev + 1L)
        store.edit {
            serializedEntries.forEach { (year, raw) -> putString("$KEY_PREFIX$year", raw) }
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = newVersion
        ScheduleEffects.syncWearable("holiday-mutate")
        true
    }

    fun save(year: Int, entries: List<HolidayEntry>): Boolean =
        updateEntries(setOf(year)) { current -> current + (year to entries) }

    /** 假期/调休数据的版本号，保存时更新，供 UI 判断是否需要刷新 */
    fun getVersion(): Long = store().getLong(KEY_VERSION, 0L)

    fun isHoliday(date: LocalDate): Boolean {
        val hit = entriesForDate(loadAllByYear(), date)
            .firstOrNull { it.type == TYPE_HOLIDAY }
        NexioLog.d(
            "CourseReminder",
            "isHoliday: date=$date hit=${hit?.name ?: "none"} date=${hit?.date ?: "-"} end=${hit?.endDate ?: "-"}"
        )
        return hit != null
    }

    fun workSwap(date: LocalDate): HolidayEntry? = workSwap(date, loadAllByYear())

    /**
     * 同 [workSwap]，但假期数据由调用方供给：连查多次（如智能周末周六/周日各查一次）
     * 共用一份 [loadAllByYear] 快照，避免重复全量解析（每次都是 SharedPreferences 全量快照 + 逐年反序列化）。
     */
    fun workSwap(date: LocalDate, entriesByYear: Map<Int, List<HolidayEntry>>): HolidayEntry? =
        entriesForDate(entriesByYear, date)
            .firstOrNull { it.type == TYPE_WORKSWAP }

    fun mergeApiEntries(year: Int, apiEntries: List<HolidayEntry>): Boolean {
        if (apiEntries.isEmpty()) return true
        // 调休日「上哪天的课」是推算值非权威数据，只在编辑弹窗预填
        val incoming = apiEntries
        return updateEntries(setOf(year)) { current ->
            val existing = current[year].orEmpty()
            val apiKeys = incoming.map { "${it.date}|${it.type}" }.toSet()
            val preserved = existing.filter { it.custom || "${it.date}|${it.type}" !in apiKeys }
            current + (year to (preserved + incoming))
        }
    }

    fun clear(year: Int) = synchronizedOn(lock) {
        val store = store()
        val previousVersion = store.getLong(KEY_VERSION, 0L)
        store.edit {
            remove("$KEY_PREFIX$year")
            val newVersion = maxOf(Clock.System.now().toEpochMilliseconds(), previousVersion + 1L)
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = store.getLong(KEY_VERSION, 0L)
    }

    // ── 数据源 ──────────────────────────────────

    internal fun parseApiDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }
            .getOrNull()
            ?.takeIf { it.toString() == value }

    fun parseApiResponse(json: String): List<HolidayEntry> = runCatching {
        // 缺键/类型不对时抛 → 整体返回空列表
        val dates = parseJsonObject(json).optJsonArray("dates")
            ?: throw IllegalArgumentException("Invalid holiday response")
        val result = mutableListOf<HolidayEntry>()
        for (i in 0 until dates.size) {
            val item = dates[i] as? JsonObject
                ?: throw IllegalArgumentException("Invalid holiday row")
            val type = when (item.optString("type")) {
                "public_holiday" -> TYPE_HOLIDAY
                "transfer_workday" -> TYPE_WORKSWAP
                else -> continue
            }
            val date = item.optString("date")
            if (parseApiDate(date) != null) {
                result += HolidayEntry(
                    date = date,
                    name = item.optString("name_cn", item.optString("name", date)),
                    type = type,
                )
            }
        }
        mergeConsecutive(result)
    }.getOrDefault(emptyList())

    internal fun mergeConsecutive(entries: List<HolidayEntry>): List<HolidayEntry> {
        val sorted = entries.sortedBy { it.date }
        val result = mutableListOf<HolidayEntry>()
        for (entry in sorted) {
            val previous = result.lastOrNull()
            if (previous != null && entry.type == TYPE_HOLIDAY && previous.type == TYPE_HOLIDAY &&
                previous.name == entry.name &&
                LocalDate.parse(previous.endDate.ifBlank { previous.date }).plusDays(1) == LocalDate.parse(entry.date)) {
                result[result.lastIndex] = previous.copy(endDate = entry.date)
            } else result += entry
        }
        return result
    }

    // holiday-calendar：unpkg 静态 JSON，无限流、最稳，字段为「放假 / 补班」两态 + 节日名。
    // APIHubs：字段更全，能给出「补班日归属哪个节日」（如「国庆节调休」）。

    const val SOURCE_APIHUBS = "apihubs"
    const val SOURCE_HOLIDAY_CALENDAR = "holiday_calendar"
    /** 默认数据源：稳定优先 */
    const val DEFAULT_SOURCE = SOURCE_HOLIDAY_CALENDAR
    private const val KEY_HOLIDAY_SOURCE = "holiday_data_source"
    /** APIHubs 里 holiday_overtime 为 10 表示「非节假日调休」 */
    private const val NO_OVERTIME = 10

    fun holidaySource(): String =
        globalStore().getString(KEY_HOLIDAY_SOURCE, DEFAULT_SOURCE)

    fun setHolidaySource(source: String) {
        globalStore().edit {
            putString(KEY_HOLIDAY_SOURCE, source)
        }
    }

    /** 当前数据源下取某一年数据的地址 */
    fun sourceUrlFor(year: Int): String = when (holidaySource()) {
        // 用 semver 范围 @1（最新 1.x）而非写死版本：作者补新年份数据 App 无需改代码；
        // 不用 @latest —— 2.0 若改 JSON 结构会打挂所有用户的导入。
        SOURCE_HOLIDAY_CALENDAR -> "https://unpkg.com/holiday-calendar@1/data/CN/$year.json"
        else -> "https://api.apihubs.cn/holiday/get" +
            "?field=date,holiday_recess,holiday_overtime,holiday_cn,holiday_overtime_cn" +
            "&year=$year&size=366"
    }

    /** 按当前数据源解析响应；与 [sourceUrlFor] 同源，避免 URL 与解析器错配 */
    fun parseSourceResponse(json: String): List<HolidayEntry> =
        if (holidaySource() == SOURCE_HOLIDAY_CALENDAR) parseApiResponse(json)
        else parseApiHubsResponse(json)

    /**
     * APIHubs 全年响应 → 条目列表（一次请求即全年）。
     *
     * ⚠ 只能用 holiday_recess / holiday_overtime 判定：该源会把圣诞、感恩节等一堆
     * **不放假**的日子也标成「节日当天」，按名字或 holiday_today 导入会多出假假期。
     */
    fun parseApiHubsResponse(json: String): List<HolidayEntry> = runCatching {
        val rows = parseJsonObject(json).optJsonObject("data")?.optJsonArray("list")
            ?: throw IllegalArgumentException("Invalid holiday response")
        val result = mutableListOf<HolidayEntry>()
        for (i in 0 until rows.size) {
            val item = rows[i] as? JsonObject
                ?: throw IllegalArgumentException("Invalid holiday row")
            val date = formatCompactDate(item.optString("date")) ?: continue
            when {
                item.optInt("holiday_recess", 2) == 1 -> result += HolidayEntry(
                    date = date,
                    name = item.optString("holiday_cn").ifBlank { "节假日" },
                    type = TYPE_HOLIDAY,
                )
                item.optInt("holiday_overtime", NO_OVERTIME) != NO_OVERTIME -> result += HolidayEntry(
                    date = date,
                    name = item.optString("holiday_overtime_cn").ifBlank { "调休工作日" },
                    type = TYPE_WORKSWAP,
                )
            }
        }
        mergeConsecutive(result)
    }.getOrDefault(emptyList())

    /** 20261010 → 2026-10-10；只认 8 位纯数字 */
    internal fun formatCompactDate(value: String): String? {
        if (value.length != 8 || value.any { !it.isDigit() }) return null
        return "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}"
            .takeIf { parseApiDate(it) != null }
    }

    /**
     * 给还没配映射的调休日算一套「上第 X 周星期 Y 的课」的建议值，只用于 UI 预填、不落库。
     * 只动未配映射的条目；用户手工保存过的（custom=true）由 [mergeApiEntries] 原样保留。
     */
    fun suggestWorkSwapFollowTargets(entries: List<HolidayEntry>): List<HolidayEntry> {
        val swaps = entries.filter { it.type == TYPE_WORKSWAP && it.followLocalDate() == null }
        if (swaps.isEmpty()) return entries

        // 节日名 → 该段假期的全部日期（展开 date ~ endDate）
        val blocks = LinkedHashMap<String, MutableList<LocalDate>>()
        for (entry in entries) {
            if (entry.type != TYPE_HOLIDAY) continue
            val start = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: continue
            val end = runCatching { LocalDate.parse(entry.endDate.ifBlank { entry.date }) }
                .getOrNull() ?: start
            val days = blocks.getOrPut(entry.name) { mutableListOf() }
            var cursor = start
            while (cursor <= end) {
                days += cursor
                cursor = cursor.plusDays(1)
            }
        }
        if (blocks.isEmpty()) return entries

        val swapDates = swaps.mapNotNull { swap ->
            runCatching { LocalDate.parse(swap.date) }.getOrNull()?.let { swap to it }
        }
        fun distanceTo(date: LocalDate, days: List<LocalDate>): Long {
            val first = days.first()
            val last = days.last()
            return when {
                date < first -> date.daysUntil(first).toLong()
                date > last -> last.daysUntil(date).toLong()
                else -> 0L
            }
        }
        // 补班日归属哪段假期：先按名字（去掉「补班/调休」后缀）匹配，匹配不到退化为最近的一段，
        // 这样 2026 的 09-20 才不会被误挂到更近的中秋（官方把它归在国庆）。
        val swapsByBlock = LinkedHashMap<String, MutableList<Pair<HolidayEntry, LocalDate>>>()
        for ((swap, date) in swapDates) {
            val baseName = swap.name.removeSuffix("补班").removeSuffix("调休")
            val blockName = blocks.keys.firstOrNull {
                it == baseName || it.startsWith(baseName) || baseName.startsWith(it)
            } ?: blocks.entries.minByOrNull { distanceTo(date, it.value) }?.key ?: continue
            swapsByBlock.getOrPut(blockName) { mutableListOf() } += swap to date
        }

        val assigned = HashMap<HolidayEntry, LocalDate>()
        for ((blockName, items) in swapsByBlock) {
            val days = blocks.getValue(blockName).sorted()
            // 假期吃掉的工作日（周一~周五），按日期先后
            val lost = days.filter { it.dayOfWeek.isoDayNumber <= 5 }
            if (lost.isEmpty()) continue
            // 假期两侧都算：节前、节后的补班日都从「最后一个工作日」往前拿，
            // 且按补班日**倒序**分配（越靠近假期结束的补班，补的课越靠后）。
            val ordered = items
                .filter { it.second < days.first() || it.second > days.last() }
                .sortedByDescending { it.second }
            ordered.forEachIndexed { index, pair ->
                lost.getOrNull(lost.lastIndex - index)?.let { assigned[pair.first] = it }
            }
        }
        if (assigned.isEmpty()) return entries

        return entries.map { entry ->
            val target = assigned[entry] ?: return@map entry
            // 存绝对日期：周次留空，读取时按当前课表实时换算，换课表不再错位
            entry.copy(followDate = target.toString())
        }
    }

    /**
     * 一次性迁移：把旧数据的「第几周 + 星期几」换算成绝对日期写进 followDate，之后按当前课表实时推算。
     *
     * 自愈：跟随日必须落在假期内，否则映射已失效（换课表/改学期开始时间后常见）→ 改用建议值重算。
     *
     * ⚠ 调用方拿不到课表仓储时不要调用本函数，以保持「构造失败即整体跳过、不写迁移标记」的语义。
     *
     * @param resolveDateForTeachingWeekDay 由（周次, 星期）换算绝对日期；失败返回 null
     */
    fun migrateLegacyFollowDates(
        resolveDateForTeachingWeekDay: (week: Int, weekday: Int) -> LocalDate?,
    ) = synchronizedOn(lock) { migrateLegacyFollowDatesLocked(resolveDateForTeachingWeekDay) }

    private fun migrateLegacyFollowDatesLocked(
        resolveDateForTeachingWeekDay: (week: Int, weekday: Int) -> LocalDate?,
    ) {
        val store = store()
        if (store.getBoolean(KEY_FOLLOW_DATE_MIGRATED, false)) return
        val byYear = loadAllByYear()
        val allEntries = byYear.values.flatten()
        val legacy = allEntries.filter {
            it.type == TYPE_WORKSWAP && it.followDate.isBlank() &&
                it.followWeek > 0 && it.followWeekday in 1..7
        }
        if (legacy.isEmpty()) {
            store.edit { putBoolean(KEY_FOLLOW_DATE_MIGRATED, true) }
            return
        }
        // 建议值按日期算、不随课表漂移；算法只处理未配映射的条目，故先清掉待迁移条目的旧周次
        val suggestionInput = allEntries.map {
            if (it in legacy) it.copy(followWeek = -1, followWeekday = -1) else it
        }
        val suggested = runCatching { suggestWorkSwapFollowTargets(suggestionInput) }
            .getOrDefault(suggestionInput)
            .associateBy { it.date }
        val years = byYear.filterValues { entries ->
            entries.any { it in legacy }
        }.keys
        updateEntries(years) { current ->
            current.mapValues { (_, entries) ->
                entries.map { entry ->
                    if (entry !in legacy) return@map entry
                    val derived = runCatching {
                        resolveDateForTeachingWeekDay(entry.followWeek, entry.followWeekday)
                    }.getOrNull()
                    val derivedIsHoliday = derived != null && allEntries.any {
                        it.type == TYPE_HOLIDAY && it.matches(derived.toString())
                    }
                    when {
                        // 跟随日确实在假期里 → 旧映射可信，原样换算成日期
                        derivedIsHoliday -> entry.copy(followDate = derived.toString())
                        // 失效 → 用建议值（没有建议就还是换算，至少让用户能看见并手改）
                        else -> entry.copy(
                            followDate = suggested[entry.date]?.followDate
                                ?: derived?.toString().orEmpty()
                        )
                    }
                }
            }
        }
        store.edit { putBoolean(KEY_FOLLOW_DATE_MIGRATED, true) }
    }
}

/**
 * 条目 → JSON。
 *
 * ⚠ 这是落盘格式：产出以 `entries_{年}` 为键直接写进 `holiday_settings`
 * （见 [HolidayManager.updateEntries]），字段名、字段顺序、紧凑格式必须与存量数据逐字一致。
 */
fun HolidayEntry.toJson(): JsonObject = jsonObjectOf(
    "date" to date,
    "endDate" to endDate,
    "name" to name,
    "type" to type,
    "followDate" to followDate,
    "followWeek" to followWeek,
    "followWeekday" to followWeekday,
    "custom" to custom,
)
