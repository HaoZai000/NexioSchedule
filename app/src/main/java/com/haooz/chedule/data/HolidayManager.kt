package com.haooz.chedule.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 节假日与调休数据。假期跳过提醒，调休按配置的课表周次和星期调度。 */
object HolidayManager {
    private const val PREFS = "holiday_settings"
    private const val KEY_PREFIX = "entries_"
    private const val KEY_VERSION = "version"
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
        fun matches(target: String): Boolean =
            if (endDate.isBlank()) target == date
            else target >= date && target <= endDate
        fun toJson() = JSONObject().apply {
            put("date", date); put("endDate", endDate); put("name", name); put("type", type)
            put("followWeek", followWeek); put("followWeekday", followWeekday); put("custom", custom)
        }
    }

    fun load(context: Context, year: Int): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("$KEY_PREFIX$year", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                Entry(
                    item.optString("date"), item.optString("endDate"), item.optString("name"),
                    item.optInt("type"), item.optInt("followWeek", -1),
                    item.optInt("followWeekday", -1), item.optBoolean("custom")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, year: Int, entries: List<Entry>) {
        val array = JSONArray().apply { entries.sortedBy { it.date }.forEach { put(it.toJson()) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("$KEY_PREFIX$year", array.toString())
            putLong(KEY_VERSION, System.currentTimeMillis())
        }
    }

    /** 假期/调休数据的版本号，保存时更新，供 UI 判断是否需要刷新 */
    fun getVersion(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_VERSION, 0L)
    }

    fun isHoliday(context: Context, date: LocalDate): Boolean {
        val y2026 = load(context, date.year)
        val y2025 = load(context, date.year - 1)
        val hit = (y2026 + y2025)
            .filter { it.type == TYPE_HOLIDAY }
            .firstOrNull { it.matches(date.toString()) }
        android.util.Log.d("CourseReminder", "isHoliday: date=$date y${date.year}=${y2026.size} y${date.year - 1}=${y2025.size} hit=${hit?.name ?: "none"} date=${hit?.date ?: "-"} end=${hit?.endDate ?: "-"}")
        return hit != null
    }

    fun workSwap(context: Context, date: LocalDate): Entry? =
        (load(context, date.year) + load(context, date.year - 1))
            .firstOrNull { it.type == TYPE_WORKSWAP && it.matches(date.toString()) }

    fun mergeApiEntries(context: Context, year: Int, apiEntries: List<Entry>) {
        val existing = load(context, year)
        val apiKeys = apiEntries.map { "${it.date}|${it.type}" }.toSet()
        val preserved = existing.filter { it.custom || "${it.date}|${it.type}" !in apiKeys }
        save(context, year, preserved + apiEntries)
    }

    fun clear(context: Context, year: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove("$KEY_PREFIX$year") }
    }

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
            if (date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                result += Entry(date = date, name = item.optString("name_cn", item.optString("name", date)), type = type)
            }
        }
        mergeConsecutive(result)
    }.getOrDefault(emptyList())

    private fun mergeConsecutive(entries: List<Entry>): List<Entry> {
        val sorted = entries.sortedBy { it.date }
        val result = mutableListOf<Entry>()
        for (entry in sorted) {
            val previous = result.lastOrNull()
            if (previous != null && previous.type == entry.type && previous.name == entry.name &&
                LocalDate.parse(previous.endDate.ifBlank { previous.date }).plusDays(1) == LocalDate.parse(entry.date)) {
                result[result.lastIndex] = previous.copy(endDate = entry.date)
            } else result += entry
        }
        return result
    }
}
