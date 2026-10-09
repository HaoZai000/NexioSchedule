package com.haooz.chedule.data

import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.datetime.LocalDate

// ════════════════════════════════════════════════════════════════════════
//  节假日与调休 · 数据本体（加载 / 缓存 / 导入导出 / 数据源）
//
//  原 `Holidays.kt`（1782 行单文件全包）按类型集群拆成四个文件，逐字搬运、零语义变更：
//
//    HolidayManager.kt              本文件：存储 + 备份 + 数据源
//    TeachingWeekReorganization.kt  调休改周规则（纯日期映射 + 严格编解码）
//    HolidayCourseExclusion.kt      假期课程剔除与逐日裁决
//    HolidayCountdown.kt            假期倒计时（今日页）
//
//  数据全部存 `holiday_settings` 一个 prefs 文件，进全量备份。
//
//  依赖方向是单向的：后三个文件只依赖纯日期类型，不反向依赖 Manager；
//  需要读取假期数据时由调用方先查 Manager 再传入，避免互相持有。
// ════════════════════════════════════════════════════════════════════════

/** 节假日与调休数据。假期跳过提醒，调休按配置的课表周次和星期调度。 */
object HolidayManager {
    private const val PREFS = "holiday_settings"
    private const val KEY_PREFIX = "entries_"
    private const val KEY_VERSION = "version"
    internal const val BACKUP_KEY = "holiday_entries"
    internal const val BACKUP_EXCLUSION_KEY = "holiday_end_course_exclusion"
    internal const val BACKUP_BEFORE_EXCLUSION_KEY = "holiday_before_course_exclusion"
    private const val KEY_EXCLUSION_ENABLED = "end_course_exclusion_enabled"
    private const val KEY_EXCLUSION_START_SECTION = "end_course_exclusion_start_section"
    private const val KEY_EXCLUSION_END_SECTION = "end_course_exclusion_end_section"
    private const val KEY_BEFORE_EXCLUSION_ENABLED = "before_course_exclusion_enabled"
    private const val KEY_BEFORE_EXCLUSION_START_SECTION = "before_course_exclusion_start_section"
    private const val KEY_BEFORE_EXCLUSION_END_SECTION = "before_course_exclusion_end_section"
    private const val BACKUP_SCHEMA_VERSION = 1
    private const val BACKUP_SCHEMA_VERSION_KEY = "schema_version"
    private const val KEY_FOLLOW_DATE_MIGRATED = "follow_date_migrated"
    private val _dataRevision = MutableStateFlow(0L)
    val dataRevision = _dataRevision.asStateFlow()
    // 条目类型常量转发到 :core 的 HolidayEntry，调用点 `HolidayManager.TYPE_HOLIDAY` 保持可用
    const val TYPE_HOLIDAY = HolidayEntry.TYPE_HOLIDAY
    const val TYPE_WORKSWAP = HolidayEntry.TYPE_WORKSWAP

    data class BackupData(
        val entries: Map<String, String>,
        val exclusion: HolidayEndCourseExclusion,
        val beforeExclusion: HolidayBeforeCourseExclusion = HolidayBeforeCourseExclusion(),
    )

    /** 纯查询转发到 :core 的 [HolidayEntries]（原实现整体下沉，行为逐字未变）。 */
    fun entriesForDate(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
    ): List<HolidayEntry> = HolidayEntries.entriesForDate(entriesByYear, date)

    @Synchronized
    fun load(context: Context, year: Int): List<HolidayEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("$KEY_PREFIX$year", null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return readEntriesSafely(array.length()) { index ->
            parseStoredEntry(array.getJSONObject(index)) ?: error("Invalid holiday entry")
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

    /** Loads all years with saved holiday entries, retaining the storage year for schedule lookup. */
    @Synchronized
    fun loadAllByYear(context: Context): Map<Int, List<HolidayEntry>> {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val years = storedEntryYears(preferences.all.keys)
        return years.associateWith { load(context, it) }
    }

    /** Preserve each year's stored JSON, including custom mappings and cross-year ranges. */
    @Synchronized
    fun exportBackupEntries(context: Context): Map<String, String> =
        exportBackupEntries(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    internal fun exportBackupEntries(preferences: SharedPreferences): Map<String, String> =
        backupEntries(preferences.all)

    internal fun backupEntries(stored: Map<String, *>): Map<String, String> =
        stored.mapNotNull { (key, value) ->
            if (storedYearFromKey(key) != null && value is String) key to value else null
        }.toMap()

    @Synchronized
    fun loadEndCourseExclusion(context: Context): HolidayEndCourseExclusion =
        loadEndCourseExclusion(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    internal fun loadEndCourseExclusion(preferences: SharedPreferences): HolidayEndCourseExclusion {
        val enabled = runCatching { preferences.getBoolean(KEY_EXCLUSION_ENABLED, false) }
            .getOrDefault(false)
        val startSection = runCatching {
            preferences.getInt(KEY_EXCLUSION_START_SECTION, 1)
        }.getOrDefault(1)
        val endSection = runCatching {
            preferences.getInt(KEY_EXCLUSION_END_SECTION, 1)
        }.getOrDefault(1)
        return HolidayEndCourseExclusion(enabled, startSection, endSection)
            .takeIf(HolidayEndCourseExclusion::isValid)
            ?: HolidayEndCourseExclusion()
    }

    @Synchronized
    fun saveEndCourseExclusion(
        context: Context,
        value: HolidayEndCourseExclusion,
    ): Boolean = saveEndCourseExclusion(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
        value,
    )

    internal fun saveEndCourseExclusion(
        preferences: SharedPreferences,
        value: HolidayEndCourseExclusion,
    ): Boolean {
        if (!value.isValid()) return false
        val previousVersion = runCatching { preferences.getLong(KEY_VERSION, 0L) }.getOrDefault(0L)
        val newVersion = maxOf(System.currentTimeMillis(), previousVersion + 1L)
        preferences.edit {
            putBoolean(KEY_EXCLUSION_ENABLED, value.enabled)
            putInt(KEY_EXCLUSION_START_SECTION, value.startSection)
            putInt(KEY_EXCLUSION_END_SECTION, value.endSection)
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = newVersion
        return true
    }

    @Synchronized
    fun loadBeforeCourseExclusion(context: Context): HolidayBeforeCourseExclusion =
        loadBeforeCourseExclusion(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    internal fun loadBeforeCourseExclusion(
        preferences: SharedPreferences,
    ): HolidayBeforeCourseExclusion {
        val enabled = runCatching { preferences.getBoolean(KEY_BEFORE_EXCLUSION_ENABLED, false) }
            .getOrDefault(false)
        val startSection = runCatching {
            preferences.getInt(KEY_BEFORE_EXCLUSION_START_SECTION, 1)
        }.getOrDefault(1)
        val endSection = runCatching {
            preferences.getInt(KEY_BEFORE_EXCLUSION_END_SECTION, 1)
        }.getOrDefault(1)
        return HolidayBeforeCourseExclusion(enabled, startSection, endSection)
            .takeIf(HolidayBeforeCourseExclusion::isValid)
            ?: HolidayBeforeCourseExclusion()
    }

    @Synchronized
    fun saveBeforeCourseExclusion(
        context: Context,
        value: HolidayBeforeCourseExclusion,
    ): Boolean = saveBeforeCourseExclusion(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
        value,
    )

    internal fun saveBeforeCourseExclusion(
        preferences: SharedPreferences,
        value: HolidayBeforeCourseExclusion,
    ): Boolean {
        if (!value.isValid()) return false
        val previousVersion = runCatching { preferences.getLong(KEY_VERSION, 0L) }.getOrDefault(0L)
        val newVersion = maxOf(System.currentTimeMillis(), previousVersion + 1L)
        preferences.edit {
            putBoolean(KEY_BEFORE_EXCLUSION_ENABLED, value.enabled)
            putInt(KEY_BEFORE_EXCLUSION_START_SECTION, value.startSection)
            putInt(KEY_BEFORE_EXCLUSION_END_SECTION, value.endSection)
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = newVersion
        return true
    }

    @Synchronized
    fun exportBackupData(context: Context): Map<String, Any> =
        exportBackupData(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    internal fun exportBackupData(preferences: SharedPreferences): Map<String, Any> {
        val exclusion = loadEndCourseExclusion(preferences)
        val beforeExclusion = loadBeforeCourseExclusion(preferences)
        return mapOf(
            BACKUP_KEY to exportBackupEntries(preferences),
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

    /** A missing field in a legacy full backup represents an empty holiday configuration. */
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

    @Synchronized
    fun restoreBackupData(context: Context, data: BackupData) =
        restoreBackupData(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), data)

    internal fun restoreBackupData(preferences: SharedPreferences, data: BackupData) {
        require(data.exclusion.isValid()) { "Invalid holiday end-course exclusion section range" }
        require(data.beforeExclusion.isValid()) {
            "Invalid holiday before-course exclusion section range"
        }
        val previousVersion = runCatching { preferences.getLong(KEY_VERSION, 0L) }.getOrDefault(0L)
        val newVersion = maxOf(System.currentTimeMillis(), previousVersion + 1L)
        preferences.edit {
            preferences.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach(::remove)
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

    /** Replace only holiday entries; do not roll back the runtime revision on restore. */
    @Synchronized
    fun restoreBackupEntries(context: Context, entries: Map<String, String>) {
        restoreBackupEntries(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), entries)
    }

    internal fun restoreBackupEntries(preferences: SharedPreferences, entries: Map<String, String>) {
        val previousVersion = preferences.getLong(KEY_VERSION, 0L)
        val newVersion = maxOf(System.currentTimeMillis(), previousVersion + 1L)
        preferences.edit {
            preferences.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach(::remove)
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

    fun entriesOverlapping(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        firstDate: LocalDate,
        lastDate: LocalDate,
    ): List<HolidayEntry> {
        if (lastDate < firstDate) return emptyList()
        return entriesByYear.toSortedMap().flatMap { (year, entries) ->
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

    private fun parseStoredEntry(item: JSONObject): HolidayEntry? {
        val date = item.opt("date") as? String ?: return null
        val endDate = if (item.has("endDate")) item.opt("endDate") as? String ?: return null else ""
        val name = item.opt("name") as? String ?: return null
        val typeValue = item.opt("type")
        if (!isStoredInteger(typeValue)) return null
        if (!isStoredOptionalIntValid(item.opt("followWeek"), item.has("followWeek"))) return null
        if (!isStoredOptionalIntValid(item.opt("followWeekday"), item.has("followWeekday"))) return null
        if (!isStoredOptionalBooleanValid(item.opt("custom"), item.has("custom"))) return null
        val followDate = if (item.has("followDate")) {
            item.opt("followDate") as? String ?: return null
        } else ""

        val entry = HolidayEntry(
            date = date,
            endDate = endDate,
            name = name,
            type = (typeValue as Number).toInt(),
            followDate = followDate,
            followWeek = item.optInt("followWeek", -1),
            followWeekday = item.optInt("followWeekday", -1),
            custom = item.optBoolean("custom"),
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

    private fun hasOnlyValidStoredRows(raw: String): Boolean = runCatching {
        val json = JsonParser.parseString(raw)
        json.isJsonArray && json.asJsonArray.all { parseBackupEntry(it) != null }
    }.getOrDefault(false)

    private fun parseBackupEntry(element: JsonElement): HolidayEntry? {
        if (!element.isJsonObject) return null
        val item = element.asJsonObject
        val date = backupString(item.get("date")) ?: return null
        val endDate = if (item.has("endDate")) {
            backupString(item.get("endDate")) ?: return null
        } else ""
        val name = backupString(item.get("name")) ?: return null
        val type = backupJsonInteger(item.get("type")) ?: return null
        val followWeek = if (item.has("followWeek")) {
            backupJsonInteger(item.get("followWeek")) ?: return null
        } else -1
        val followWeekday = if (item.has("followWeekday")) {
            backupJsonInteger(item.get("followWeekday")) ?: return null
        } else -1
        val custom = if (item.has("custom")) {
            backupJsonBoolean(item.get("custom")) ?: return null
        } else false
        // 老备份没有 followDate：留空，由 followWeek/followWeekday 在迁移时补
        val followDate = if (item.has("followDate")) {
            backupString(item.get("followDate")) ?: return null
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

    private fun backupString(value: JsonElement?): String? =
        value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun backupJsonInteger(value: JsonElement?): Int? {
        val number = value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.let { runCatching { it.asJsonPrimitive.asNumber }.getOrNull() }
            ?: return null
        return number.takeIf(::isStoredInteger)?.toInt()
    }

    private fun backupJsonBoolean(value: JsonElement?): Boolean? =
        value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

    fun updateEntries(
        context: Context,
        years: Set<Int>,
        transform: (Map<Int, List<HolidayEntry>>) -> Map<Int, List<HolidayEntry>>,
    ): Boolean = synchronized(this) {
        if (years.isEmpty()) return@synchronized true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentRaw = years.associateWith { prefs.getString("$KEY_PREFIX$it", null) }
        val canWrite = currentRaw.values.all { raw ->
            canOverwriteStoredEntries(
                raw,
                raw == null || hasOnlyValidStoredRows(raw),
            )
        }
        if (!canWrite) return@synchronized false

        val currentEntries = years.associateWith { load(context, it) }
        val updatedEntries = transform(currentEntries)
        if (updatedEntries.keys != years || updatedEntries.values.flatten().any { !isValidEntry(it) }) {
            return@synchronized false
        }

        val serializedEntries = updatedEntries.mapValues { (_, entries) ->
            JSONArray().apply { entries.sortedBy { it.date }.forEach { put(it.toJson()) } }.toString()
        }
        // 单调递增：同一毫秒内两次 save 也要变号，避免 UI 版本对比失效
        val prev = prefs.getLong(KEY_VERSION, 0L)
        val newVersion = maxOf(System.currentTimeMillis(), prev + 1L)
        prefs.edit {
            serializedEntries.forEach { (year, raw) -> putString("$KEY_PREFIX$year", raw) }
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = newVersion
        true
    }

    fun save(context: Context, year: Int, entries: List<HolidayEntry>): Boolean =
        updateEntries(context, setOf(year)) { current -> current + (year to entries) }

    /** 假期/调休数据的版本号，保存时更新，供 UI 判断是否需要刷新 */
    fun getVersion(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_VERSION, 0L)
    }

    fun isHoliday(context: Context, date: LocalDate): Boolean {
        val hit = entriesForDate(loadAllByYear(context), date)
            .firstOrNull { it.type == TYPE_HOLIDAY }
        NexioLog.d(
            "CourseReminder",
            "isHoliday: date=$date hit=${hit?.name ?: "none"} date=${hit?.date ?: "-"} end=${hit?.endDate ?: "-"}"
        )
        return hit != null
    }

    fun workSwap(context: Context, date: LocalDate): HolidayEntry? =
        entriesForDate(loadAllByYear(context), date)
            .firstOrNull { it.type == TYPE_WORKSWAP }

    fun mergeApiEntries(context: Context, year: Int, apiEntries: List<HolidayEntry>): Boolean {
        if (apiEntries.isEmpty()) return true
        // 调休日的「上哪天的课」 —— 那是推算值不是权威数据，
        // 只在用户打开编辑弹窗时预填
        val incoming = apiEntries
        return updateEntries(context, setOf(year)) { current ->
            val existing = current[year].orEmpty()
            val apiKeys = incoming.map { "${it.date}|${it.type}" }.toSet()
            val preserved = existing.filter { it.custom || "${it.date}|${it.type}" !in apiKeys }
            current + (year to (preserved + incoming))
        }
    }

    @Synchronized
    fun clear(context: Context, year: Int) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousVersion = preferences.getLong(KEY_VERSION, 0L)
        preferences.edit {
            remove("$KEY_PREFIX$year")
            val newVersion = maxOf(System.currentTimeMillis(), previousVersion + 1L)
            putLong(KEY_VERSION, newVersion)
        }
        _dataRevision.value = getVersion(context)
    }

    internal fun parseApiDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }
            .getOrNull()
            ?.takeIf { it.toString() == value }

    fun parseApiResponse(json: String): List<HolidayEntry> = runCatching {
        val dates = JSONObject(json).getJSONArray("dates")
        val result = mutableListOf<HolidayEntry>()
        for (i in 0 until dates.length()) {
            val item = dates.getJSONObject(i)
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

    // ── 1b. 数据源 ──────────────────────────────
    // 默认 holiday-calendar：unpkg 上的静态 JSON，无限流、最稳，字段是「放假 / 补班」两态 + 节日名。
    // 备选 APIHubs：字段更全，能给出「补班日归属哪个节日」（如「国庆节调休」），

    const val SOURCE_APIHUBS = "apihubs"
    const val SOURCE_HOLIDAY_CALENDAR = "holiday_calendar"
    /** 默认数据源：稳定优先 */
    const val DEFAULT_SOURCE = SOURCE_HOLIDAY_CALENDAR
    private const val KEY_HOLIDAY_SOURCE = "holiday_data_source"
    /** APIHubs 里 holiday_overtime 为 10 表示「非节假日调休」 */
    private const val NO_OVERTIME = 10

    fun holidaySource(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HOLIDAY_SOURCE, DEFAULT_SOURCE) ?: DEFAULT_SOURCE

    fun setHolidaySource(context: Context, source: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_HOLIDAY_SOURCE, source)
        }
    }

    /** 当前数据源下取某一年数据的地址 */
    fun sourceUrlFor(context: Context, year: Int): String = when (holidaySource(context)) {
        // 用 semver 范围 @1（= 最新 1.x）而不是写死版本：作者补了新年份数据 App 不用改代码。
        // 不写 @latest —— 2.0 若改了 JSON 结构会把所有用户的导入一次性打挂。
        // 该包 1.x 结构稳定（dates[] 里 date/name_cn/type），已实测 1.3.0 与 1.3.3 一致。
        SOURCE_HOLIDAY_CALENDAR -> "https://unpkg.com/holiday-calendar@1/data/CN/$year.json"
        else -> "https://api.apihubs.cn/holiday/get" +
            "?field=date,holiday_recess,holiday_overtime,holiday_cn,holiday_overtime_cn" +
            "&year=$year&size=366"
    }

    /** 按当前数据源解析响应；与 [sourceUrlFor] 同源，避免 URL 与解析器错配 */
    fun parseSourceResponse(context: Context, json: String): List<HolidayEntry> =
        if (holidaySource(context) == SOURCE_HOLIDAY_CALENDAR) parseApiResponse(json)
        else parseApiHubsResponse(json)

    /**
     * APIHubs 全年响应 → 条目列表（一次请求即全年，无需分页）。
     *
     * 判定只能用 holiday_recess / holiday_overtime 两个字段：
     * 这个源会把圣诞、感恩节、记者节、下元节、七夕……一堆**不放假**的日子也标成
     * 「节日当天」，按名字或 holiday_today 导入会多出一堆假假期。
     */
    fun parseApiHubsResponse(json: String): List<HolidayEntry> = runCatching {
        val rows = JSONObject(json).getJSONObject("data").getJSONArray("list")
        val result = mutableListOf<HolidayEntry>()
        for (i in 0 until rows.length()) {
            val item = rows.getJSONObject(i)
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
     * 给还没配映射的调休日算一套「上第 X 周星期 Y 的课」的**建议值
     * 只用于 UI 预填，**不落库*
     * 只动还没配过映射的条目；用户手工保存过的条目（custom=true）由
     * [mergeApiEntries] 原样保留，不会被覆盖。
     */
    fun suggestWorkSwapFollowTargets(context: Context, entries: List<HolidayEntry>): List<HolidayEntry> {
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
     * 一次性迁移：旧数据存的是「第几周 + 星期几」（相对课表的周次），一切换课表就指向别的日期。
     * 这里把它换算成绝对日期写进 followDate，之后周次一律按当前课表实时推算。
     *
     * 自愈规则：补班日跟随的那一天**必须是放假的日子**，否则这个映射一定是错的
     * （典型症状：换了课表/改过学期开始时间后，跟随日跑到假期外去了）→ 直接改用建议值重算。
     */
    @Synchronized
    fun migrateLegacyFollowDates(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FOLLOW_DATE_MIGRATED, false)) return
        val repository = runCatching { CourseRepository(context) }.getOrNull() ?: return
        val byYear = loadAllByYear(context)
        val allEntries = byYear.values.flatten()
        val legacy = allEntries.filter {
            it.type == TYPE_WORKSWAP && it.followDate.isBlank() &&
                it.followWeek > 0 && it.followWeekday in 1..7
        }
        if (legacy.isEmpty()) {
            prefs.edit { putBoolean(KEY_FOLLOW_DATE_MIGRATED, true) }
            return
        }
        // 建议值按日期算，天然不随课表漂移，用它替换掉判定为失效的旧映射。
        // 建议算法只处理「还没配映射」的条目，所以先把待迁移条目的旧周次清掉再喂进去
        val suggestionInput = allEntries.map {
            if (it in legacy) it.copy(followWeek = -1, followWeekday = -1) else it
        }
        val suggested = runCatching { suggestWorkSwapFollowTargets(context, suggestionInput) }
            .getOrDefault(suggestionInput)
            .associateBy { it.date }
        val years = byYear.filterValues { entries ->
            entries.any { it in legacy }
        }.keys
        updateEntries(context, years) { current ->
            current.mapValues { (_, entries) ->
                entries.map { entry ->
                    if (entry !in legacy) return@map entry
                    val derived = runCatching {
                        repository.dateForTeachingWeekDay(entry.followWeek, entry.followWeekday)
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
        prefs.edit { putBoolean(KEY_FOLLOW_DATE_MIGRATED, true) }
    }
}

/**
 * 条目 → JSON。
 *
 * ⚠ **刻意留在 `:app`**：它产出的串会以 `entries_{年}` 为键**直接落盘**（见 [HolidayManager.updateEntries]），
 * 是数据兼容红线 —— 换 JSON 库必须逐字对齐。`HolidayEntry` 本体已下沉 `:core`，
 * 这里用扩展函数把存储格式留在原地，等 `HolidayManager` 整体下沉时再一并换 `JsonSupport`，
 * 并配合真实用户数据的 round-trip 回归。
 */
internal fun HolidayEntry.toJson() = JSONObject().apply {
    put("date", date); put("endDate", endDate); put("name", name); put("type", type)
    put("followDate", followDate)
    put("followWeek", followWeek); put("followWeekday", followWeekday); put("custom", custom)
}
