package com.haooz.chedule.data

import android.content.Context
import androidx.core.content.edit
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
    private val _dataRevision = MutableStateFlow(0L)
    val dataRevision = _dataRevision.asStateFlow()
    const val TYPE_HOLIDAY = 0
    const val TYPE_WORKSWAP = 1

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

    internal fun storedEntryYears(preferenceKeys: Set<String>): List<Int> =
        preferenceKeys.asSequence()
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { it.removePrefix(KEY_PREFIX).toIntOrNull() }
            .distinct()
            .sorted()
            .toList()

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

    private fun hasOnlyValidStoredRows(raw: String): Boolean {
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return false
        return allStoredRowsValid(array.length()) { index ->
            parseStoredEntry(array.getJSONObject(index)) ?: error("Invalid holiday entry")
        }
    }

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
