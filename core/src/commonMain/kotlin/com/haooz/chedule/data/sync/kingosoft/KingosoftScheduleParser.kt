package com.haooz.chedule.data.sync.kingosoft

import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.truncateNote
import com.haooz.chedule.data.platform.displayText
import com.haooz.chedule.data.util.minusWeeks
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KingosoftWeekPayload internal constructor(
    val xn: String, val xq: String, val zc: Int, val maxzc: Int,
    val qssj: LocalDate?, val jssj: String, val maxjc: Int,
    internal val days: List<List<KingosoftEntry>>,
)

internal data class KingosoftEntry(
    val kcmc: String, val rkjs: String, val skdd: String, val xq: String,
    val jcdm: String, val jcxx: String, val skzs: String, val dsz: String,
    val sftk: String, val kcdm: String, val skbj: String, val bz: String, val txxx: String,
)

data class KingosoftParsedSchedule(val courses: List<Course>, val semesterStartDate: LocalDate?, val maxSection: Int)

object KingosoftScheduleParser {
    private fun JsonObject.field(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    private fun JsonObject.required(key: String) = field(key).also {
        require(it.isNotBlank()) { displayText("课表缺少 $key", "Schedule is missing $key") }
    }

    fun parseWeek(json: String): KingosoftWeekPayload {
        val obj = Json.parseToJsonElement(json).jsonObject
        val maxWeek = obj.required("maxzc").toInt()
        val week = obj.required("zc").toInt()
        require(maxWeek in 1..30 && week in 1..maxWeek) {
            displayText("教务系统返回的周次无效：$week/$maxWeek", "Invalid schedule week: $week/$maxWeek")
        }
        val days = (1..7).map { day ->
            obj.getValue("week$day").jsonArray.map { element ->
                with(element.jsonObject) {
                    KingosoftEntry(
                        field("kcmc"), field("rkjs"), field("skdd"), field("xq"),
                        field("jcdm"), field("jcxx"), field("skzs"), field("dsz"),
                        field("sftk"), field("kcdm"), field("skbj"), field("bz"), field("txxx"),
                    )
                }
            }
        }
        val start = obj.field("qssj").takeIf { it.isNotBlank() }?.let {
            val iso = if (it.length == 8 && it.all(Char::isDigit)) {
                "${it.take(4)}-${it.substring(4, 6)}-${it.takeLast(2)}"
            } else it.replace('/', '-')
            LocalDate.parse(iso)
        }
        return KingosoftWeekPayload(
            obj.required("xn"), obj.required("xq"), week, maxWeek, start, obj.field("jssj"),
            obj.required("maxjc").toInt(), days,
        )
    }

    private data class Slot(
        val code: String, val group: String, val day: Int, val start: Int, val end: Int,
        val room: String, val teacher: String,
    )

    private fun sections(entry: KingosoftEntry): IntRange? {
        if (entry.jcdm.isNotBlank()) {
            val values = entry.jcdm.split(',').map { it.trim().toIntOrNull()?.takeIf { n -> n > 0 } ?: return null }
            return values.min()..values.max()
        }
        val parts = entry.jcxx.split('-')
        if (parts.size != 2) return null
        val start = parts.first().trim().toIntOrNull() ?: return null
        val end = parts.last().trim().toIntOrNull() ?: return null
        return (start..end).takeIf { start > 0 && end >= start }
    }

    /** skzs is only a hint for a lone response; never revive a cancelled week in a full-term fetch. */
    private fun hintedWeeks(text: String, maxWeek: Int): List<Int> {
        val weeks = mutableListOf<Int>()
        for (part in text.removeSuffix("周").split(',', '，', '、')) {
            val bounds = part.trim().split('-')
            val start = bounds.first().toIntOrNull() ?: return emptyList()
            val end = bounds.last().toIntOrNull() ?: return emptyList()
            if (bounds.size > 2 || start !in 1..maxWeek || end !in start..maxWeek) return emptyList()
            weeks.addAll(start..end)
        }
        return weeks
    }

    fun aggregateWeeks(
        scheduleId: String, weeks: List<KingosoftWeekPayload>, palette: List<Long>, syncScopeId: String = scheduleId,
    ): KingosoftParsedSchedule {
        require(scheduleId.isNotBlank() && syncScopeId.isNotBlank() && weeks.isNotEmpty() && palette.isNotEmpty())
        val first = weeks.first()
        val slots = linkedMapOf<Slot, Pair<KingosoftEntry, MutableSet<Int>>>()
        for (week in weeks) {
            require(week.xn == first.xn && week.xq == first.xq && week.maxzc == first.maxzc) {
                displayText("各周课表不属于同一学期", "The weekly schedules do not belong to the same term")
            }
            week.days.forEachIndexed { index, day ->
                for (entry in day) {
                    if (entry.sftk == "1") continue
                    val sections = sections(entry) ?: continue
                    require(entry.kcmc.isNotBlank() && entry.kcdm.isNotBlank()) {
                        displayText("课程缺少名称或课程代码", "Missing course name or code")
                    }
                    val slot = Slot(entry.kcdm, entry.skbj, index + 1, sections.first, sections.last,
                        entry.skdd.ifBlank { entry.xq }, entry.rkjs)
                    val selected = slots.getOrPut(slot) { entry to mutableSetOf() }.second
                    selected.add(week.zc)
                    if (weeks.size == 1) selected.addAll(hintedWeeks(entry.skzs, week.maxzc))
                }
            }
        }
        val courses = slots.map { (slot, group) ->
            val (entry, seen) = group
            val selected = seen.sorted()
            val key = listOf(syncScopeId, first.xn, first.xq, slot.code, slot.group, slot.day, slot.start,
                slot.end, slot.room, slot.teacher).joinToString("|")
            Course(
                id = "kg_" + KingosoftCodec.md5Hex(key).take(16), scheduleId = scheduleId,
                name = entry.kcmc, teacher = slot.teacher, classroom = slot.room, dayOfWeek = slot.day,
                startSection = slot.start, endSection = slot.end, selectedWeeks = selected,
                startWeek = selected.first(), endWeek = selected.last(), weekType = Course.WEEK_TYPE_ALL,
                note = entry.bz.ifBlank { entry.txxx }.truncateNote(),
                colorRes = palette[(entry.kcmc.hashCode() and Int.MAX_VALUE) % palette.size],
            )
        }
        val start = weeks.sortedBy { it.zc }.firstNotNullOfOrNull { it.qssj?.minusWeeks(it.zc - 1L) }
        return KingosoftParsedSchedule(courses, start, weeks.maxOf { it.maxjc })
    }
}
