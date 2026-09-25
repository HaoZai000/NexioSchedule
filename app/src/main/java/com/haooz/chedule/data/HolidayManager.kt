package com.haooz.chedule.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 节假日与调休数据。假期跳过提醒，调休按配置的课表周次和星期调度。 */
object HolidayManager {
    private const val PREFS = "holiday_settings"
    private const val KEY_PREFIX = "entries_"
    private const val KEY_VERSION = "version"
    internal const val BACKUP_KEY = "holiday_entries"
    internal const val BACKUP_EXCLUSION_KEY = "holiday_end_course_exclusion"
    private const val KEY_EXCLUSION_ENABLED = "end_course_exclusion_enabled"
    private const val KEY_EXCLUSION_START_SECTION = "end_course_exclusion_start_section"
    private const val KEY_EXCLUSION_END_SECTION = "end_course_exclusion_end_section"
    private const val BACKUP_SCHEMA_VERSION = 1
    private const val BACKUP_SCHEMA_VERSION_KEY = "schema_version"
    private val _dataRevision = MutableStateFlow(0L)
    val dataRevision = _dataRevision.asStateFlow()
    const val TYPE_HOLIDAY = 0
    const val TYPE_WORKSWAP = 1

    data class BackupData(
        val entries: Map<String, String>,
        val exclusion: HolidayEndCourseExclusion,
    )

    data class Entry(
        val date: String,
        val endDate: String = "",
        val name: String,
        val type: Int,
        val followWeek: Int = -1,
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

        fun toJson() = JSONObject().apply {
            put("date", date); put("endDate", endDate); put("name", name); put("type", type)
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
    fun exportBackupData(context: Context): Map<String, Any> =
        exportBackupData(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    internal fun exportBackupData(preferences: SharedPreferences): Map<String, Any> {
        val exclusion = loadEndCourseExclusion(preferences)
        return mapOf(
            BACKUP_KEY to exportBackupEntries(preferences),
            BACKUP_EXCLUSION_KEY to mapOf(
                BACKUP_SCHEMA_VERSION_KEY to BACKUP_SCHEMA_VERSION,
                "enabled" to exclusion.enabled,
                "startSection" to exclusion.startSection,
                "endSection" to exclusion.endSection,
            ),
        )
    }

    fun decodeBackupData(backup: Map<String, Any?>): BackupData = BackupData(
        entries = decodeBackupEntries(backup),
        exclusion = decodeBackupEndCourseExclusion(backup),
    )

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
        val previousVersion = runCatching { preferences.getLong(KEY_VERSION, 0L) }.getOrDefault(0L)
        val newVersion = maxOf(System.currentTimeMillis(), previousVersion + 1L)
        preferences.edit {
            preferences.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach(::remove)
            data.entries.forEach { (key, raw) -> putString(key, raw) }
            putBoolean(KEY_EXCLUSION_ENABLED, data.exclusion.enabled)
            putInt(KEY_EXCLUSION_START_SECTION, data.exclusion.startSection)
            putInt(KEY_EXCLUSION_END_SECTION, data.exclusion.endSection)
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

        val entry = Entry(
            date = date,
            endDate = endDate,
            name = name,
            type = (typeValue as Number).toInt(),
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
        return Entry(date, endDate, name, type, followWeek, followWeekday, custom)
            .takeIf(::isValidEntry)
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
        android.util.Log.d(
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
        return updateEntries(context, setOf(year)) { current ->
            val existing = current[year].orEmpty()
            val apiKeys = apiEntries.map { "${it.date}|${it.type}" }.toSet()
            val preserved = existing.filter { it.custom || "${it.date}|${it.type}" !in apiKeys }
            current + (year to (preserved + apiEntries))
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
}
