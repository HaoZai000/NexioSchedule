package com.haooz.chedule.data

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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import com.haooz.chedule.data.NexioLog

// ════════════════════════════════════════════════════════════════════════
//  节假日与调休 —— 单文件全包
//
//  数据全部存`holiday_settings` 一个 prefs 文件（见 [HolidayManager]），
//  进全量备份。**改动节假日/调休相关逻辑只需要动这一个文件。**
//
//  注意：块之间的依赖方向是单向的 ——
//  [HolidayCourseExclusion] 只依赖纯日期类型，不反向依赖 Manager；
//  需要读取假期数据时由调用方先查 Manager 再传入，避免互相持有。
// ════════════════════════════════════════════════════════════════════════
// ── 1. 假期数据本体：加载 / 缓存 / 导入导出 ──────────────

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
    const val TYPE_HOLIDAY = 0
    const val TYPE_WORKSWAP = 1

    data class BackupData(
        val entries: Map<String, String>,
        val exclusion: HolidayEndCourseExclusion,
        val beforeExclusion: HolidayBeforeCourseExclusion = HolidayBeforeCourseExclusion(),
    )

    data class Entry(
        val date: String,
        val endDate: String = "",
        val name: String,
        val type: Int,
        /**
         * 调休「上哪一天的课」的**绝对日期**（yyyy-MM-dd，空 = 未配置）。
         *
         * ⚠ 这是唯一可信来源。周次是相对「课表学期开始时间」算出来的，同一对 (周次,星期)
         * 在不同课表下指向完全不同的日期 —— 早先存 followWeek/followWeekday 导致一切换课表
         * 调休列就跟错课。现在存绝对日期，读取时按**当前课表**实时换算，换课表自动跟随。
         */
        val followDate: String = "",
        /** 旧数据兼容：仅用于迁移/老备份还原，读取一律走 [followDate] */
        val followWeek: Int = -1,
        /** 旧数据兼容：仅用于迁移/老备份还原，读取一律走 [followDate] */
        val followWeekday: Int = -1,
        val custom: Boolean = false,
    ) {
        fun matches(target: String): Boolean {
            val targetDate = runCatching { LocalDate.parse(target) }.getOrNull() ?: return false
            val startDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
            val lastDate = if (endDate.isBlank()) {
                startDate
            } else {
                runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return false
            }
            return !targetDate.isBefore(startDate) && !targetDate.isAfter(lastDate)
        }

        /** 调休跟随的绝对日期；未配置/格式坏 → null */
        fun followLocalDate(): LocalDate? =
            followDate.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        /** 是否已配好跟随日期（老数据用 followWeek/followWeekday 也暂时算已配，等迁移） */
        fun hasFollowMapping(): Boolean =
            followLocalDate() != null || (followWeek > 0 && followWeekday in 1..7)

        fun toJson() = JSONObject().apply {
            put("date", date); put("endDate", endDate); put("name", name); put("type", type)
            put("followDate", followDate)
            put("followWeek", followWeek); put("followWeekday", followWeekday); put("custom", custom)
        }
    }

    @Synchronized
    fun load(context: Context, year: Int): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("$KEY_PREFIX$year", null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return readEntriesSafely(array.length()) { index ->
            parseStoredEntry(array.getJSONObject(index)) ?: error("Invalid holiday entry")
        }
    }

    internal fun readEntriesSafely(size: Int, readEntry: (Int) -> Entry): List<Entry> =
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
    fun loadAllByYear(context: Context): Map<Int, List<Entry>> {
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

    fun entriesForDate(entriesByYear: Map<Int, List<Entry>>, date: LocalDate): List<Entry> {
        val storageYearPriority = buildList {
            add(date.year)
            add(date.year - 1)
            addAll(entriesByYear.keys.filter { it < date.year - 1 }.sortedDescending())
        }.distinct()

        val yearRank = storageYearPriority.withIndex().associate { it.value to it.index }
        return storageYearPriority.flatMap { year ->
            entriesByYear[year].orEmpty()
                .filter { it.matches(date.toString()) }
                .map { year to it }
        }.sortedWith(
            compareByDescending<Pair<Int, Entry>> { it.second.custom }
                .thenBy { yearRank.getValue(it.first) }
        ).map { it.second }
    }

    fun entriesOverlapping(
        entriesByYear: Map<Int, List<Entry>>,
        firstDate: LocalDate,
        lastDate: LocalDate,
    ): List<Entry> {
        if (lastDate.isBefore(firstDate)) return emptyList()
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
                !endDate.isBefore(firstDate) && !startDate.isAfter(lastDate)
            }.map { year to it }
        }.sortedWith(
            compareBy<Pair<Int, Entry>> { it.second.custom }
                .thenBy { it.first }
        ).map { it.second }
    }

    fun entriesByDateRange(
        entriesByYear: Map<Int, List<Entry>>,
        firstDate: LocalDate,
        lastDate: LocalDate,
    ): Map<String, List<Entry>> {
        if (lastDate.isBefore(firstDate)) return emptyMap()
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

    fun withoutCustomWorkSwapsOnDate(entries: List<Entry>, date: String): List<Entry> =
        entries.filterNot {
            it.type == TYPE_WORKSWAP && it.custom && it.date == date
        }

    fun withoutEntry(entries: List<Entry>, entry: Entry): List<Entry> =
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

    internal fun allStoredRowsValid(size: Int, readEntry: (Int) -> Entry): Boolean =
        (0 until size).all { index ->
            runCatching { readEntry(index) }
                .getOrNull()
                ?.let(::isValidEntry) == true
        }

    private fun parseStoredEntry(item: JSONObject): Entry? {
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

        val entry = Entry(
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

    private fun isValidEntry(entry: Entry): Boolean {
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
        return !endDate.isBefore(startDate)
    }

    private fun hasOnlyValidStoredRows(raw: String): Boolean = runCatching {
        val json = JsonParser.parseString(raw)
        json.isJsonArray && json.asJsonArray.all { parseBackupEntry(it) != null }
    }.getOrDefault(false)

    private fun parseBackupEntry(element: JsonElement): Entry? {
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
        return Entry(
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
        transform: (Map<Int, List<Entry>>) -> Map<Int, List<Entry>>,
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

    fun save(context: Context, year: Int, entries: List<Entry>): Boolean =
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

    fun workSwap(context: Context, date: LocalDate): Entry? =
        entriesForDate(loadAllByYear(context), date)
            .firstOrNull { it.type == TYPE_WORKSWAP }

    fun mergeApiEntries(context: Context, year: Int, apiEntries: List<Entry>): Boolean {
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

    fun parseApiResponse(json: String): List<Entry> = runCatching {
        val dates = JSONObject(json).getJSONArray("dates")
        val result = mutableListOf<Entry>()
        for (i in 0 until dates.length()) {
            val item = dates.getJSONObject(i)
            val type = when (item.optString("type")) {
                "public_holiday" -> TYPE_HOLIDAY
                "transfer_workday" -> TYPE_WORKSWAP
                else -> continue
            }
            val date = item.optString("date")
            if (parseApiDate(date) != null) {
                result += Entry(
                    date = date,
                    name = item.optString("name_cn", item.optString("name", date)),
                    type = type,
                )
            }
        }
        mergeConsecutive(result)
    }.getOrDefault(emptyList())

    internal fun mergeConsecutive(entries: List<Entry>): List<Entry> {
        val sorted = entries.sortedBy { it.date }
        val result = mutableListOf<Entry>()
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
    fun parseSourceResponse(context: Context, json: String): List<Entry> =
        if (holidaySource(context) == SOURCE_HOLIDAY_CALENDAR) parseApiResponse(json)
        else parseApiHubsResponse(json)

    /**
     * APIHubs 全年响应 → 条目列表（一次请求即全年，无需分页）。
     *
     * 判定只能用 holiday_recess / holiday_overtime 两个字段：
     * 这个源会把圣诞、感恩节、记者节、下元节、七夕……一堆**不放假**的日子也标成
     * 「节日当天」，按名字或 holiday_today 导入会多出一堆假假期。
     */
    fun parseApiHubsResponse(json: String): List<Entry> = runCatching {
        val rows = JSONObject(json).getJSONObject("data").getJSONArray("list")
        val result = mutableListOf<Entry>()
        for (i in 0 until rows.length()) {
            val item = rows.getJSONObject(i)
            val date = formatCompactDate(item.optString("date")) ?: continue
            when {
                item.optInt("holiday_recess", 2) == 1 -> result += Entry(
                    date = date,
                    name = item.optString("holiday_cn").ifBlank { "节假日" },
                    type = TYPE_HOLIDAY,
                )
                item.optInt("holiday_overtime", NO_OVERTIME) != NO_OVERTIME -> result += Entry(
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
    fun suggestWorkSwapFollowTargets(context: Context, entries: List<Entry>): List<Entry> {
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
            while (!cursor.isAfter(end)) {
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
                date.isBefore(first) -> ChronoUnit.DAYS.between(date, first)
                date.isAfter(last) -> ChronoUnit.DAYS.between(last, date)
                else -> 0L
            }
        }
        // 补班日归属哪段假期：先按名字（去掉「补班/调休」后缀）匹配，匹配不到退化为最近的一段，
        // 这样 2026 的 09-20 才不会被误挂到更近的中秋（官方把它归在国庆）。
        val swapsByBlock = LinkedHashMap<String, MutableList<Pair<Entry, LocalDate>>>()
        for ((swap, date) in swapDates) {
            val baseName = swap.name.removeSuffix("补班").removeSuffix("调休")
            val blockName = blocks.keys.firstOrNull {
                it == baseName || it.startsWith(baseName) || baseName.startsWith(it)
            } ?: blocks.entries.minByOrNull { distanceTo(date, it.value) }?.key ?: continue
            swapsByBlock.getOrPut(blockName) { mutableListOf() } += swap to date
        }

        val assigned = HashMap<Entry, LocalDate>()
        for ((blockName, items) in swapsByBlock) {
            val days = blocks.getValue(blockName).sorted()
            // 假期吃掉的工作日（周一~周五），按日期先后
            val lost = days.filter { it.dayOfWeek.value <= 5 }
            if (lost.isEmpty()) continue
            // 假期两侧都算：节前、节后的补班日都从「最后一个工作日」往前拿，
            // 且按补班日**倒序**分配（越靠近假期结束的补班，补的课越靠后）。
            val ordered = items
                .filter { it.second.isBefore(days.first()) || it.second.isAfter(days.last()) }
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


// ── 2. 调休改周规则 ─────────────────────────────

/** Two consecutive weekday ranges from stable, original calendar weeks form one teaching week. */
data class TeachingWeekReorganizationRule(
    val firstOriginalWeek: Int,
    val firstStartWeekday: Int,
    val firstEndWeekday: Int,
    val secondOriginalWeek: Int,
    val secondStartWeekday: Int,
    val secondEndWeekday: Int,
)

/** A date in a reorganization gap keeps its surrounding teaching-week context but has no weekday. */
data class TeachingWeekPosition(
    val week: Long,
    val weekday: Int?,
    val isReorganizationPause: Boolean = false,
)

/** Pure date mapping and strict persistence codec for teaching-week reorganizations. */
object TeachingWeekReorganization {
    const val SCHEMA_VERSION = 1
    private const val SCHEMA_VERSION_KEY = "schema_version"
    private const val RULES_KEY = "rules"
    private val gson = Gson()

    /**
     * Returns a user-facing validation error, or null for a complete, non-overlapping ruleset.
     * Every rule must compose exactly Monday–Sunday; the uncovered calendar dates are a pause.
     */
    fun validationError(
        rules: List<TeachingWeekReorganizationRule>,
        totalWeeks: Int,
    ): String? {
        if (totalWeeks <= 0) return "当前课表总周数无效"
        var compressedWeeks = 0L
        var previousSpanEnd = Long.MIN_VALUE
        val ordered = rules.sortedBy { it.firstOriginalWeek }
        for (rule in ordered) {
            if (rule.firstOriginalWeek < 1 || rule.secondOriginalWeek < 1 ||
                rule.firstEndWeekday !in 1..6 || rule.firstStartWeekday != 1 ||
                rule.secondEndWeekday != 7 || rule.secondStartWeekday !in 2..7
            ) {
                return "第一段须从星期一开始，第二段须延续到星期日"
            }
            if (rule.secondOriginalWeek <= rule.firstOriginalWeek) {
                return "第二段原始周必须晚于第一段"
            }
            if (rule.secondStartWeekday != rule.firstEndWeekday + 1) {
                return "两段星期必须连续组成完整教学周"
            }

            val targetWeek = rule.firstOriginalWeek.toLong() - compressedWeeks
            if (targetWeek !in 1L..totalWeeks.toLong()) {
                return "重组后的教学周超出当前课表总周数"
            }

            val spanStart = (rule.firstOriginalWeek.toLong() - 1L) * 7L
            val spanEnd = (rule.secondOriginalWeek.toLong() - 1L) * 7L + 6L
            if (spanStart <= previousSpanEnd) return "教学周重组规则的日期范围不能重叠"
            previousSpanEnd = spanEnd

            compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
        }
        return null
    }

    /** Allow editing one existing rule while preserving other rules invalidated by a shorter term. */
    fun validationErrorForRuleChange(
        updatedRules: List<TeachingWeekReorganizationRule>,
        existingRules: List<TeachingWeekReorganizationRule>,
        changedRule: TeachingWeekReorganizationRule,
        totalWeeks: Int,
    ): String? {
        if (totalWeeks <= 0) return "当前课表总周数无效"
        validationError(updatedRules, Int.MAX_VALUE)?.let { return it }
        val changedTeachingWeek = teachingWeekForRule(changedRule, updatedRules)
            ?: return "找不到当前编辑的教学周规则"
        if (changedTeachingWeek !in 1L..totalWeeks.toLong()) {
            return "当前编辑的重组教学周超出课表总周数"
        }
        val newlyOutOfRange = updatedRules.firstOrNull { rule ->
            val teachingWeek = teachingWeekForRule(rule, updatedRules) ?: return@firstOrNull true
            val previousTeachingWeek = teachingWeekForRule(rule, existingRules)
            teachingWeek !in 1L..totalWeeks.toLong() &&
                (previousTeachingWeek == null || previousTeachingWeek in 1L..totalWeeks.toLong())
        }
        if (newlyOutOfRange != null) return "新规则超出当前课表总周数"
        return null
    }

    fun teachingWeekForRule(
        rule: TeachingWeekReorganizationRule,
        rules: List<TeachingWeekReorganizationRule>,
    ): Long? {
        var compressedWeeks = 0L
        for (candidate in rules.sortedBy { it.firstOriginalWeek }) {
            val teachingWeek = candidate.firstOriginalWeek.toLong() - compressedWeeks
            if (candidate == rule) return teachingWeek
            compressedWeeks += candidate.secondOriginalWeek.toLong() - candidate.firstOriginalWeek.toLong()
        }
        return null
    }

    /** Date -> teaching week/day. Pause dates have a week context but no schedulable weekday. */
    fun mapDate(
        semesterStartDate: LocalDate,
        date: LocalDate,
        rules: List<TeachingWeekReorganizationRule>,
    ): TeachingWeekPosition {
        val monday = semesterMonday(semesterStartDate)
        val daysFromMonday = ChronoUnit.DAYS.between(monday, date)
        var compressedWeeks = 0L
        for (rule in rules.sortedBy { it.firstOriginalWeek }) {
            val firstStart = (rule.firstOriginalWeek.toLong() - 1L) * 7L + rule.firstStartWeekday - 1L
            val firstEnd = (rule.firstOriginalWeek.toLong() - 1L) * 7L + rule.firstEndWeekday - 1L
            val secondStart = (rule.secondOriginalWeek.toLong() - 1L) * 7L + rule.secondStartWeekday - 1L
            val secondEnd = (rule.secondOriginalWeek.toLong() - 1L) * 7L + rule.secondEndWeekday - 1L
            if (daysFromMonday < firstStart) break

            val targetWeek = rule.firstOriginalWeek.toLong() - compressedWeeks
            when {
                daysFromMonday <= firstEnd -> return TeachingWeekPosition(targetWeek, date.dayOfWeek.value)
                daysFromMonday < secondStart -> return TeachingWeekPosition(
                    week = targetWeek,
                    weekday = null,
                    isReorganizationPause = true,
                )
                daysFromMonday <= secondEnd -> return TeachingWeekPosition(targetWeek, date.dayOfWeek.value)
                else -> compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
            }
        }

        val rawWeek = daysFromMonday.floorDiv(7L) + 1L
        return TeachingWeekPosition(rawWeek - compressedWeeks, date.dayOfWeek.value)
    }

    /** Teaching week/day -> actual calendar date; returns null for invalid positions or overflow. */
    fun dateForPosition(
        semesterStartDate: LocalDate,
        teachingWeek: Int,
        weekday: Int,
        rules: List<TeachingWeekReorganizationRule>,
    ): LocalDate? = dateForPosition(semesterStartDate, teachingWeek.toLong(), weekday, rules)

    fun datesForTeachingWeek(
        semesterStartDate: LocalDate,
        teachingWeek: Int,
        rules: List<TeachingWeekReorganizationRule>,
    ): List<LocalDate> = (1..7).mapNotNull { weekday ->
        dateForPosition(semesterStartDate, teachingWeek, weekday, rules)
    }.takeIf { it.size == 7 }.orEmpty()

    /** A teaching week cannot advance while any of its mapped calendar dates are still ahead. */
    fun hasFutureTeachingWeekDates(
        semesterStartDate: LocalDate,
        date: LocalDate,
        rules: List<TeachingWeekReorganizationRule>,
    ): Boolean {
        if (rules.isEmpty()) return false
        val position = mapDate(semesterStartDate, date, rules)
        return (1..7).any { weekday ->
            dateForPosition(semesterStartDate, position.week, weekday, rules)?.isAfter(date) == true
        }
    }

    fun dateForPosition(
        semesterStartDate: LocalDate,
        teachingWeek: Long,
        weekday: Int,
        rules: List<TeachingWeekReorganizationRule>,
    ): LocalDate? {
        if (weekday !in 1..7) return null
        val monday = semesterMonday(semesterStartDate)
        // Current-week alignment can legitimately shift a candidate before original week one.
        // Reorganization rules begin at week one, so such positions retain the linear baseline.
        if (teachingWeek < 1L) {
            return runCatching {
                monday.plusWeeks(Math.subtractExact(teachingWeek, 1L)).plusDays((weekday - 1).toLong())
            }.getOrNull()
        }
        var compressedWeeks = 0L
        for (rule in rules.sortedBy { it.firstOriginalWeek }) {
            val targetWeek = rule.firstOriginalWeek.toLong() - compressedWeeks
            if (teachingWeek == targetWeek) {
                val originalWeek = when (weekday) {
                    in rule.firstStartWeekday..rule.firstEndWeekday -> rule.firstOriginalWeek
                    in rule.secondStartWeekday..rule.secondEndWeekday -> rule.secondOriginalWeek
                    else -> return null
                }
                return originalWeekDate(monday, originalWeek, weekday)
            }
            if (teachingWeek > targetWeek) {
                compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
            } else {
                break
            }
        }
        val originalWeek = runCatching { Math.addExact(teachingWeek, compressedWeeks) }
            .getOrNull() ?: return null
        return runCatching { monday.plusWeeks(originalWeek - 1L).plusDays((weekday - 1).toLong()) }
            .getOrNull()
    }

    /** Rebase the semester start so [date] maps to the requested teaching week and weekday. */
    fun semesterStartDateForTeachingWeekOnDate(
        semesterStartDate: LocalDate,
        date: LocalDate,
        teachingWeek: Long,
        rules: List<TeachingWeekReorganizationRule>,
    ): LocalDate? {
        val targetPositionDate = dateForPosition(
            semesterStartDate = semesterStartDate,
            teachingWeek = teachingWeek,
            weekday = date.dayOfWeek.value,
            rules = rules,
        ) ?: return null
        val targetOffsetDays = ChronoUnit.DAYS.between(semesterMonday(semesterStartDate), targetPositionDate)
        return runCatching {
            date.minusDays(targetOffsetDays)
                .plusDays((semesterStartDate.dayOfWeek.value - 1).toLong())
        }.getOrNull()
    }

    /** Teaching-week labels occupied by merge rules, in chronological order. */
    fun teachingWeeksForRules(rules: List<TeachingWeekReorganizationRule>): List<Long> {
        var compressedWeeks = 0L
        return rules.sortedBy { it.firstOriginalWeek }.map { rule ->
            (rule.firstOriginalWeek.toLong() - compressedWeeks).also {
                compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
            }
        }
    }

    /** Raw calendar-week index for a non-reorganized teaching week. */
    fun originalWeekForTeachingWeek(
        teachingWeek: Long,
        rules: List<TeachingWeekReorganizationRule>,
    ): Long? {
        if (teachingWeek < 1L) return teachingWeek
        var compressedWeeks = 0L
        for (rule in rules.sortedBy { it.firstOriginalWeek }) {
            val targetWeek = rule.firstOriginalWeek.toLong() - compressedWeeks
            if (teachingWeek == targetWeek) return null
            if (teachingWeek > targetWeek) {
                compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
            } else {
                break
            }
        }
        return runCatching { Math.addExact(teachingWeek, compressedWeeks) }.getOrNull()
    }

    /** Week picker ceiling follows the active instructional horizon and existing raw-week entries. */
    fun maxOriginalWeek(
        totalWeeks: Int,
        rules: List<TeachingWeekReorganizationRule>,
    ): Int {
        val compressed = rules.sumOf {
            it.secondOriginalWeek.toLong() - it.firstOriginalWeek.toLong()
        }
        val horizon = totalWeeks.toLong().coerceAtLeast(1L) + compressed + 1L
        val existing = rules.maxOfOrNull { maxOf(it.firstOriginalWeek, it.secondOriginalWeek) } ?: 1
        return maxOf(horizon.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), existing, 1)
    }

    /** Pick a valid adjacent-week merge draft, preferring week four when available. */
    fun firstAvailableStartingWeek(
        totalWeeks: Int,
        rules: List<TeachingWeekReorganizationRule>,
        preferredWeek: Int = 4,
    ): Int? {
        val maxWeek = maxOriginalWeek(totalWeeks, rules)
        val spans = rules.sortedBy { it.firstOriginalWeek }
        fun validCandidate(week: Int): Boolean {
            val candidate = TeachingWeekReorganizationRule(
                firstOriginalWeek = week,
                firstStartWeekday = 1,
                firstEndWeekday = 3,
                secondOriginalWeek = week + 1,
                secondStartWeekday = 4,
                secondEndWeekday = 7,
            )
            return validationErrorForRuleChange(
                updatedRules = rules + candidate,
                existingRules = rules,
                changedRule = candidate,
                totalWeeks = totalWeeks,
            ) == null
        }
        if (preferredWeek in 1 until maxWeek && validCandidate(preferredWeek)) return preferredWeek
        var week = 1L
        while (week < maxWeek.toLong()) {
            val conflicting = spans.firstOrNull { rule ->
                week <= rule.secondOriginalWeek.toLong() && week + 1L >= rule.firstOriginalWeek.toLong()
            }
            if (conflicting != null) {
                week = conflicting.secondOriginalWeek.toLong() + 1L
                continue
            }
            if (validCandidate(week.toInt())) return week.toInt()
            week++
        }
        return null
    }

    fun encode(rules: List<TeachingWeekReorganizationRule>, totalWeeks: Int): String {
        val error = validationError(rules, totalWeeks)
        require(error == null) { error!! }
        return gson.toJson(toBackupValue(rules))
    }

    fun decode(raw: String, totalWeeks: Int): List<TeachingWeekReorganizationRule> {
        val data = runCatching {
            gson.fromJson<Map<String, Any>>(raw, object : TypeToken<Map<String, Any>>() {}.type)
        }.getOrNull() ?: throw IllegalArgumentException("Invalid teaching-week reorganization data")
        return fromBackupValue(data, present = true, totalWeeks = totalWeeks)
    }

    fun toBackupValue(rules: List<TeachingWeekReorganizationRule>): Map<String, Any> = mapOf(
        SCHEMA_VERSION_KEY to SCHEMA_VERSION,
        RULES_KEY to rules.map { rule ->
            mapOf(
                "firstOriginalWeek" to rule.firstOriginalWeek,
                "firstStartWeekday" to rule.firstStartWeekday,
                "firstEndWeekday" to rule.firstEndWeekday,
                "secondOriginalWeek" to rule.secondOriginalWeek,
                "secondStartWeekday" to rule.secondStartWeekday,
                "secondEndWeekday" to rule.secondEndWeekday,
            )
        },
    )

    /** A missing field is a legacy configuration; a present null or invalid value is rejected. */
    fun fromBackupValue(
        value: Any?,
        present: Boolean,
        totalWeeks: Int,
        preserveOutOfRangeRules: Boolean = false,
    ): List<TeachingWeekReorganizationRule> {
        if (!present) return emptyList()
        require(value is Map<*, *>) { "Invalid teaching-week reorganization backup" }
        require(readInteger(value[SCHEMA_VERSION_KEY]) == SCHEMA_VERSION) {
            "Unsupported teaching-week reorganization schema"
        }
        val rawRules = value[RULES_KEY]
        require(rawRules is List<*>) { "Invalid teaching-week reorganization rules" }
        val rules = rawRules.map { rawRule ->
            require(rawRule is Map<*, *>) { "Invalid teaching-week reorganization rule" }
            TeachingWeekReorganizationRule(
                firstOriginalWeek = requiredInteger(rawRule, "firstOriginalWeek"),
                firstStartWeekday = requiredInteger(rawRule, "firstStartWeekday"),
                firstEndWeekday = requiredInteger(rawRule, "firstEndWeekday"),
                secondOriginalWeek = requiredInteger(rawRule, "secondOriginalWeek"),
                secondStartWeekday = requiredInteger(rawRule, "secondStartWeekday"),
                secondEndWeekday = requiredInteger(rawRule, "secondEndWeekday"),
            )
        }.sortedBy { it.firstOriginalWeek }
        val validationHorizon = if (preserveOutOfRangeRules) Int.MAX_VALUE else totalWeeks
        val error = validationError(rules, validationHorizon)
        require(error == null) { error ?: "Invalid teaching-week reorganization rules" }
        return rules
    }

    private fun requiredInteger(map: Map<*, *>, key: String): Int =
        readInteger(map[key]) ?: throw IllegalArgumentException("Invalid $key in teaching-week reorganization")

    private fun readInteger(value: Any?): Int? {
        val number = (value as? Number)?.toDouble() ?: return null
        if (!number.isFinite() || number % 1.0 != 0.0 ||
            number < Int.MIN_VALUE.toDouble() || number > Int.MAX_VALUE.toDouble()
        ) return null
        return number.toInt()
    }

    private fun semesterMonday(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    private fun originalWeekDate(monday: LocalDate, week: Int, weekday: Int): LocalDate? =
        runCatching { monday.plusWeeks((week.toLong() - 1L)).plusDays((weekday - 1).toLong()) }
            .getOrNull()
}


// ── 3. 假期课程剔除与逐日裁决 ─────────────────────

data class HolidayEndCourseExclusion(
    val enabled: Boolean = false,
    val startSection: Int = 1,
    val endSection: Int = 1,
) {
    fun isValid(): Boolean = startSection > 0 && endSection >= startSection
}

data class HolidayBeforeCourseExclusion(
    val enabled: Boolean = false,
    val startSection: Int = 1,
    val endSection: Int = 1,
) {
    fun isValid(): Boolean = startSection > 0 && endSection >= startSection
}

data class HolidayDayCourseResolution(
    val courses: List<Course>,
    val isHolidayDate: Boolean,
    val isHolidayEndCourseExclusionActive: Boolean,
    val isHolidayBeforeCourseExclusionActive: Boolean = false,
)

data class HolidayCourseDisplaySelection(
    val representative: Course?,
    val hidden: List<Course>,
)

/** Pure rules for allowing selected courses on the final date of a holiday interval. */
object HolidayCourseExclusion {
    fun resolveDayCourses(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
        exclusion: HolidayEndCourseExclusion,
        candidates: () -> List<Course>,
        sectionTimes: () -> Map<Int, String>,
        sectionCount: () -> Int,
        beforeExclusion: HolidayBeforeCourseExclusion = HolidayBeforeCourseExclusion(),
    ): HolidayDayCourseResolution {
        val isHolidayDate = HolidayManager.entriesForDate(entriesByYear, date)
            .any { it.type == HolidayManager.TYPE_HOLIDAY }
        val isExclusionActive = isHolidayDate && isEnabledOnDate(entriesByYear, date, exclusion)
        val isBeforeExclusionActive = !isHolidayDate &&
            isEnabledBeforeHolidayDate(entriesByYear, date, beforeExclusion)
        if (isHolidayDate && !isExclusionActive) {
            return HolidayDayCourseResolution(
                courses = emptyList(),
                isHolidayDate = true,
                isHolidayEndCourseExclusionActive = false,
                isHolidayBeforeCourseExclusionActive = false,
            )
        }

        val dayCandidates = candidates()
        val courses = when {
            isExclusionActive -> filterMatchingCourses(
                dayCandidates,
                exclusion,
                sectionTimes(),
                sectionCount(),
            )
            isBeforeExclusionActive -> filterExcludedCourses(
                dayCandidates,
                beforeExclusion,
                sectionTimes(),
                sectionCount(),
            )
            else -> dayCandidates
        }
        return HolidayDayCourseResolution(
            courses = courses,
            isHolidayDate = isHolidayDate,
            isHolidayEndCourseExclusionActive = isExclusionActive,
            isHolidayBeforeCourseExclusionActive = isBeforeExclusionActive,
        )
    }

    /**
 * 该日期是否为「假期的前一天」。
 *
 * 判定只看「后一天是否假期」，**刻意不看当天是否为调休上班日**：用户开启「假期前日课程
 * 排除」后，规则在假期前一天硬性生效，当天即使是调休上班日（TYPE_WORKSWAP + 配了
 * followWeekday）也一样停课。课表/今日/提醒/桌面组件/ICS 导出全部按这一条口径。
 * 不要因为「调休上班日本来就要上课」就擅自加排除 —— 那会与调休配置互相打架，
 * 是有意为之的取舍。
 */
fun isBeforeHolidayDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
    ): Boolean {
        if (HolidayManager.entriesForDate(entriesByYear, date)
                .any { it.type == HolidayManager.TYPE_HOLIDAY } || date == LocalDate.MAX
        ) return false

        return HolidayManager.entriesForDate(entriesByYear, date.plusDays(1))
            .any { it.type == HolidayManager.TYPE_HOLIDAY }
    }

    fun isEnabledBeforeHolidayDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
        exclusion: HolidayBeforeCourseExclusion,
    ): Boolean = exclusion.enabled && exclusion.isValid() && isBeforeHolidayDate(entriesByYear, date)

    fun isEnabledOnDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
        exclusion: HolidayEndCourseExclusion,
    ): Boolean = exclusion.enabled && exclusion.isValid() && isLastHolidayDate(entriesByYear, date)

    fun isLastHolidayDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
    ): Boolean {
        val isHoliday = HolidayManager.entriesForDate(entriesByYear, date)
            .any { it.type == HolidayManager.TYPE_HOLIDAY }
        if (!isHoliday) return false
        if (date == LocalDate.MAX) return true

        return HolidayManager.entriesForDate(entriesByYear, date.plusDays(1))
            .none { it.type == HolidayManager.TYPE_HOLIDAY }
    }

    fun matchesCourse(
        course: Course,
        exclusion: HolidayEndCourseExclusion,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): Boolean {
        return matchesCourseInRange(
            course,
            exclusion.enabled,
            exclusion.startSection,
            exclusion.endSection,
            sectionTimes,
            sectionCount,
        )
    }

    fun matchesCourse(
        course: Course,
        exclusion: HolidayBeforeCourseExclusion,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): Boolean = matchesCourseInRange(
        course,
        exclusion.enabled,
        exclusion.startSection,
        exclusion.endSection,
        sectionTimes,
        sectionCount,
    )

    private fun matchesCourseInRange(
        course: Course,
        enabled: Boolean,
        startSection: Int,
        endSection: Int,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): Boolean {
        if (!enabled || startSection <= 0 || endSection < startSection || sectionCount <= 0) return false
        val selectedRange = startSection..minOf(endSection, sectionCount)
        if (selectedRange.isEmpty()) return false

        if (course.isCustomTime) {
            if (!course.hasValidCustomTime()) return false
            val courseStart = parseTime(course.customStartTime) ?: return false
            val courseEnd = parseTime(course.customEndTime) ?: return false
            if (!courseStart.isBefore(courseEnd)) return false

            return selectedRange.any { section ->
                val (sectionStart, sectionEnd) = parseSectionTime(sectionTimes[section]) ?: return@any false
                courseStart.isBefore(sectionEnd) && courseEnd.isAfter(sectionStart)
            }
        }

        if (course.startSection <= 0 || course.endSection < course.startSection) return false
        return course.startSection <= selectedRange.last && course.endSection >= selectedRange.first
    }

    fun filterMatchingCourses(
        candidates: List<Course>,
        exclusion: HolidayEndCourseExclusion,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): List<Course> = candidates.filter {
        matchesCourse(it, exclusion, sectionTimes, sectionCount)
    }

    fun filterExcludedCourses(
        candidates: List<Course>,
        exclusion: HolidayBeforeCourseExclusion,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): List<Course> = candidates.filterNot {
        matchesCourse(it, exclusion, sectionTimes, sectionCount)
    }

    fun cancelledCourseIdsOnDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
        exclusion: HolidayBeforeCourseExclusion,
        candidates: List<Course>,
        displayWeek: Int,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): Set<String> {
        if (!isEnabledBeforeHolidayDate(entriesByYear, date, exclusion)) return emptySet()
        return candidates.asSequence()
            .filter { it.isActiveInWeek(displayWeek) }
            .filter { matchesCourse(it, exclusion, sectionTimes, sectionCount) }
            .map { it.id }
            .toSet()
    }

    fun selectDisplayCourses(
        currentWeekCourses: List<Course>,
        cancelledCourseIds: Set<String>,
    ): HolidayCourseDisplaySelection {
        val representativeIndex = currentWeekCourses.indexOfFirst {
            it.id !in cancelledCourseIds
        }.takeIf { it >= 0 } ?: currentWeekCourses.indices.firstOrNull()
            ?: return HolidayCourseDisplaySelection(null, emptyList())
        return HolidayCourseDisplaySelection(
            representative = currentWeekCourses[representativeIndex],
            hidden = currentWeekCourses.filterIndexed { index, _ -> index != representativeIndex },
        )
    }

    private fun parseSectionTime(value: String?): Pair<LocalTime, LocalTime>? {
        val parts = value?.split('-') ?: return null
        if (parts.size != 2) return null
        val start = parseTime(parts[0]) ?: return null
        val end = parseTime(parts[1]) ?: return null
        return (start to end).takeIf { start.isBefore(end) }
    }

    private fun parseTime(value: String?): LocalTime? {
        val parts = value?.trim()?.split(':') ?: return null
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
}


// ── 4. 假期倒计时（今日页）─────────────────────

/** Pure date/time rules for the holiday countdown shown on the Today page. */
object HolidayCountdown {
    fun millisUntilNextMinute(epochMillis: Long): Long =
        60_000L - Math.floorMod(epochMillis, 60_000L)

    data class HolidayPeriod(
        val startDate: LocalDate,
        val endDate: LocalDate,
    )

    fun holidayPeriodsFromStoredEntries(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
    ): List<HolidayPeriod> = entriesByYear.flatMap { (storageYear, entries) ->
        entries.asSequence()
            .filter { it.type == HolidayManager.TYPE_HOLIDAY }
            .mapNotNull { entry ->
                val start = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                    ?: return@mapNotNull null
                val end = runCatching { LocalDate.parse(entry.endDate.ifBlank { entry.date }) }
                    .getOrNull() ?: return@mapNotNull null
                if (end.isBefore(start) || storageYear > end.year) return@mapNotNull null
                val firstEligibleDate = if (storageYear > start.year) {
                    LocalDate.of(storageYear, 1, 1)
                } else {
                    start
                }
                if (firstEligibleDate.isAfter(end)) null
                else HolidayPeriod(firstEligibleDate, end)
            }
            .toList()
    }

    sealed interface Snapshot {
        data class BeforeHoliday(val startsAt: LocalDateTime) : Snapshot
        data class DuringHoliday(val returnDate: LocalDate) : Snapshot
    }

    /**
     * Builds a date-stable snapshot. The callback supplies the end time of the latest effective
     * class on a date, or null when that date has no effective class.
     */
    fun createSnapshot(
        today: LocalDate,
        holidays: List<HolidayPeriod>,
        lastClassEndAt: (LocalDate) -> LocalTime?,
        earliestPossibleCourseDate: LocalDate? = null,
        latestPossibleCourseDate: LocalDate? = null,
        additionalCourseDates: Collection<LocalDate> = emptyList(),
        regularCoursePatterns: List<CourseScheduleDateBounds.CourseDatePattern>? = null,
    ): Snapshot? {
        val validHolidays = mergeHolidayPeriods(holidays)

        validHolidays.firstOrNull { holiday ->
            !today.isBefore(holiday.startDate) && !today.isAfter(holiday.endDate)
        }?.let { return Snapshot.DuringHoliday(it.endDate) }

        val nextHoliday = validHolidays.firstOrNull { it.startDate.isAfter(today) } ?: return null
        var additionalCandidate: Snapshot.BeforeHoliday? = null
        for (date in additionalCourseDates.asSequence()
                .filter { it.isBefore(nextHoliday.startDate) }
                .distinct()
                .sortedDescending()) {
            if (validHolidays.any {
                !date.isBefore(it.startDate) && !date.isAfter(it.endDate) && date != it.endDate
            }) {
                continue
            }
            val endTime = lastClassEndAt(date) ?: continue
            additionalCandidate = Snapshot.BeforeHoliday(date.atTime(endTime))
            if (latestPossibleCourseDate != null && date.isAfter(latestPossibleCourseDate)) {
                return additionalCandidate
            }
            break
        }

        if (regularCoursePatterns != null && regularCoursePatterns.isNotEmpty()) {
            var searchLimit = minOf(
                nextHoliday.startDate.minusDays(1),
                latestPossibleCourseDate ?: nextHoliday.startDate.minusDays(1),
            )
            val earliestPatternDate = regularCoursePatterns.minOf { it.firstDate }
            val searchStart = earliestPossibleCourseDate?.let { minOf(it, earliestPatternDate) }
                ?: earliestPatternDate
            while (!searchLimit.isBefore(searchStart)) {
                val candidateDate = regularCoursePatterns.asSequence()
                    .mapNotNull { latestPatternDateOnOrBefore(it, searchLimit) }
                    .maxOrNull() ?: break
                if (additionalCandidate != null &&
                    !candidateDate.isAfter(additionalCandidate.startsAt.toLocalDate())
                ) break

                val holiday = validHolidays.firstOrNull {
                    !candidateDate.isBefore(it.startDate) && !candidateDate.isAfter(it.endDate)
                }
                if (holiday != null) {
                    if (candidateDate == holiday.endDate) {
                        lastClassEndAt(candidateDate)?.let { endTime ->
                            return Snapshot.BeforeHoliday(candidateDate.atTime(endTime))
                        }
                    }
                    if (holiday.startDate == LocalDate.MIN) break
                    searchLimit = holiday.startDate.minusDays(1)
                    continue
                }
                lastClassEndAt(candidateDate)?.let { endTime ->
                    return Snapshot.BeforeHoliday(candidateDate.atTime(endTime))
                }
                if (candidateDate == searchStart || candidateDate == LocalDate.MIN) break
                searchLimit = candidateDate.minusDays(1)
            }
        } else if (regularCoursePatterns == null &&
            earliestPossibleCourseDate != null && latestPossibleCourseDate != null
        ) {
            var searchDate = minOf(nextHoliday.startDate.minusDays(1), latestPossibleCourseDate)
            while (!searchDate.isBefore(earliestPossibleCourseDate) &&
                (additionalCandidate == null || searchDate.isAfter(additionalCandidate.startsAt.toLocalDate()))
            ) {
                val holiday = validHolidays.firstOrNull {
                    !searchDate.isBefore(it.startDate) && !searchDate.isAfter(it.endDate)
                }
                if (holiday != null) {
                    if (searchDate == holiday.endDate) {
                        lastClassEndAt(searchDate)?.let { endTime ->
                            return Snapshot.BeforeHoliday(searchDate.atTime(endTime))
                        }
                    }
                    if (holiday.startDate == LocalDate.MIN ||
                        holiday.startDate.isBefore(earliestPossibleCourseDate)
                    ) break
                    searchDate = holiday.startDate.minusDays(1)
                    continue
                }
                lastClassEndAt(searchDate)?.let { endTime ->
                    return Snapshot.BeforeHoliday(searchDate.atTime(endTime))
                }
                if (searchDate == earliestPossibleCourseDate) break
                searchDate = searchDate.minusDays(1)
            }
        }

        return additionalCandidate ?: Snapshot.BeforeHoliday(nextHoliday.startDate.atStartOfDay())
    }

    fun createSnapshotWithCourseBoundsResult(
        today: LocalDate,
        holidays: List<HolidayPeriod>,
        courseDateBounds: Result<CourseScheduleDateBounds.Bounds?>,
        lastClassEndAt: (LocalDate) -> LocalTime?,
    ): Snapshot? = courseDateBounds.fold(
        onSuccess = { bounds ->
            createSnapshot(
                today = today,
                holidays = holidays,
                lastClassEndAt = lastClassEndAt,
                earliestPossibleCourseDate = bounds?.firstDate,
                latestPossibleCourseDate = bounds?.lastDate,
                additionalCourseDates = bounds?.additionalCourseDates.orEmpty(),
                regularCoursePatterns = bounds?.regularCoursePatterns.orEmpty(),
            )
        },
        onFailure = { null },
    )

    /** 一段连续假期的概览：[startDate, endDate] 闭区间，[name] 取该段首日的假期名 */
    data class HolidayBlock(
        val name: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
    )

    /**
     * 目标日所在的那一段连续假期；目标日不是假期时返回 null。
     *
     * 区间口径与今日页假期倒计时共用同一份 [mergeHolidayPeriods]：相邻两段记录
     * （如中秋 + 国庆）算同一段假期，中间只隔一个非假日也算同一段。
     * 提醒文案必须跟随这里，否则会出现「今日页显示放假 8 天、明日提醒却说 1 天」的分裂。
     */
    fun holidayBlockAt(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
    ): HolidayBlock? {
        val period = mergeHolidayPeriods(holidayPeriodsFromStoredEntries(entriesByYear))
            .firstOrNull { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }
            ?: return null
        val name = HolidayManager.entriesForDate(entriesByYear, period.startDate)
            .firstOrNull { it.type == HolidayManager.TYPE_HOLIDAY }?.name.orEmpty()
        return HolidayBlock(name = name, startDate = period.startDate, endDate = period.endDate)
    }

    private fun mergeHolidayPeriods(holidays: List<HolidayPeriod>): List<HolidayPeriod> {
        val merged = mutableListOf<HolidayPeriod>()
        holidays.filter { !it.endDate.isBefore(it.startDate) }
            .sortedBy { it.startDate }
            .forEach { holiday ->
                val previous = merged.lastOrNull()
                if (previous == null ||
                    ChronoUnit.DAYS.between(previous.endDate, holiday.startDate) > 1L
                ) {
                    merged += holiday
                } else {
                    merged[merged.lastIndex] = previous.copy(
                        endDate = maxOf(previous.endDate, holiday.endDate)
                    )
                }
            }
        return merged
    }

    private fun latestPatternDateOnOrBefore(
        pattern: CourseScheduleDateBounds.CourseDatePattern,
        limit: LocalDate,
    ): LocalDate? {
        if (pattern.stepDays <= 0L || limit.isBefore(pattern.firstDate)) return null
        val boundedLimit = minOf(limit, pattern.lastDate)
        if (boundedLimit.isBefore(pattern.firstDate)) return null
        val intervals = ChronoUnit.DAYS.between(pattern.firstDate, boundedLimit) / pattern.stepDays
        return runCatching { pattern.firstDate.plusDays(intervals * pattern.stepDays) }.getOrNull()
    }

    fun message(snapshot: Snapshot, now: LocalDateTime): String = when (snapshot) {
        is Snapshot.DuringHoliday -> {
            val daysUntilReturn = ChronoUnit.DAYS.between(now.toLocalDate(), snapshot.returnDate)
            if (daysUntilReturn <= 0) "怎么今天就返校了……"
            else "还有 $daysUntilReturn 天返校"
        }

        is Snapshot.BeforeHoliday -> {
            val remaining = Duration.between(now, snapshot.startsAt)
            if (remaining.isNegative || remaining.isZero) {
                "恭喜你放假啦！"
            } else {
                formatRemaining(remaining)
            }
        }
    }

    private fun formatRemaining(remaining: Duration): String = when {
        remaining.toDays() >= 1 -> "还有 ${remaining.toDays()} 天放假"
        remaining.toHours() >= 1 -> "还有 ${remaining.toHours()} 小时放假"
        else -> "还有 ${remaining.toMinutes().coerceAtLeast(1)} 分钟放假"
    }
}
