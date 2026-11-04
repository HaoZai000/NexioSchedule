package com.haooz.chedule.data.backup

import kotlin.collections.iterator
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

// ════════════════════════════════════════════════════════════════════════
//  ICS（`text/calendar`）导入 —— 纯文本解析，无 Android / java 依赖
//  （「课表导入」页要进 `:ui-shared` commonMain，留在 Android 模块里那页就迁不动）。
//
//  与 [parseFullScheduleJson]（JSON 导入通道，见 `ScheduleImportCodec.kt`）并列：
//  两者都返回 `Triple(是否成功, 提示消息, 课表数据 Map)`，数据交由
//  `:ui-shared` 的 `applyScheduleData` 写入。
// ════════════════════════════════════════════════════════════════════════

/**
 * 解析 ICS 文本 → 可导入的课表数据 Map。
 *
 * 同名课（同星期 + 同起始分钟 + 同教室 + 同教师）合并周次，日期对放在 `datePairs` 里，
 * 节次映射（分钟 → 第几节）由下游 `applyScheduleData` 用用户的时间配置完成。
 */
fun parseIcsFile(text: String): Triple<Boolean, String, Map<String, Any>?> {
    return try {
        // 同名课在同星期同时段时合并周次
        val courseGroups = mutableMapOf<String, MutableList<Map<String, Any>>>()
        val lines = text.lines()

        var currentEvent = mutableMapOf<String, String>()
        var inEvent = false

        for (line in lines) {
            val trimmed = line.trim().trimEnd('\r', '\n')
            when {
                trimmed == "BEGIN:VEVENT" -> {
                    inEvent = true
                    currentEvent = mutableMapOf()
                }
                trimmed == "END:VEVENT" -> {
                    inEvent = false
                    if (currentEvent.isNotEmpty()) {
                        val parsed = parseIcsEvent(currentEvent)
                        if (parsed != null) {
                            // 键含教室/教师，避免不同地点的同名课被误合并
                            val mergeKey = "${parsed["name"]}_${parsed["dayOfWeek"]}_${parsed["startTotalMinutes"]}_${parsed["classroom"]}_${parsed["teacher"]}"
                            courseGroups.getOrPut(mergeKey) { mutableListOf() }.add(parsed)
                        }
                    }
                }
                inEvent -> {
                    val colonIndex = trimmed.indexOf(':')
                    if (colonIndex > 0) {
                        var key = trimmed.substring(0, colonIndex)
                        val value = trimmed.substring(colonIndex + 1)
                        key = key.substringBefore(';')
                        currentEvent[key] = value
                    }
                }
            }
        }

        if (courseGroups.isEmpty()) {
            return Triple(false, "未找到课程事件", null)
        }

        // List<List<String>> 承载日期对，避免 Pair 序列化问题
        val mergedCourses = mutableListOf<Map<String, Any>>()
        for ((_, courseEvents) in courseGroups) {
            val firstEvent = courseEvents.first()
            val datePairs = mutableListOf<List<String>>()
            for (event in courseEvents) {
                val sd = event["startDate"] as? String
                val ud = event["untilDate"] as? String
                if (!sd.isNullOrEmpty()) {
                    datePairs.add(listOf(sd, ud ?: ""))
                }
            }
            mergedCourses.add(firstEvent + mapOf("datePairs" to datePairs))
        }

        val data = mapOf(
            "schedule_name" to "ICS导入课表",
            "courses" to mergedCourses,
            "settings" to emptyMap<String, Any>()
        )
        Triple(true, "成功", data)
    } catch (e: Exception) {
        Triple(false, "ICS解析失败: ${e.message}", null)
    }
}

private fun parseIcsEvent(event: Map<String, String>): Map<String, Any>? {
    val summary = event["SUMMARY"] ?: return null
    val location = event["LOCATION"] ?: ""

    val dtstart = event["DTSTART"] ?: return null
    val dtend = event["DTEND"] ?: return null

    // ICS: YYYYMMDDTHHMMSS[.Z]，全天事件仅 YYYYMMDD
    val startRaw = dtstart.substringAfter(":")
    val endRaw = dtend.substringAfter(":")

    val isAllDay = startRaw.length == 8 && !startRaw.contains('T')

    val startDateStr = startRaw.take(8)
    val startTimeStr = if (isAllDay) "080000" else startRaw.drop(9).take(6)
    val endTimeStr = if (isAllDay) "090000" else endRaw.drop(9).take(6)

    val startYear = startDateStr.substring(0, 4).toIntOrNull() ?: return null
    val startMonth = startDateStr.substring(4, 6).toIntOrNull() ?: return null
    val startDay = startDateStr.substring(6, 8).toIntOrNull() ?: return null

    val startHour = startTimeStr.substring(0, 2).toIntOrNull() ?: 8
    val startMinute = startTimeStr.substring(2, 4).toIntOrNull() ?: 0
    val endHour = endTimeStr.substring(0, 2).toIntOrNull() ?: (startHour + 1)
    val endMinute = endTimeStr.substring(2, 4).toIntOrNull() ?: 0

    // 只返回分钟数，节次映射由 applyScheduleData 用用户配置完成
    val startTotalMinutes = startHour * 60 + startMinute
    // 跨午夜（结束不晚于开始）按次日结束算：与 CourseTimeSpan.crossesMidnight 同语义。
    // 不补这一天的话，下游 require(endMinutes > startMinutes) 会直接抛错，整份 ICS 导入失败。
    val endTotalMinutes = (endHour * 60 + endMinute).let {
        if (it <= startTotalMinutes) it + 24 * 60 else it
    }

    val startDate = LocalDate(startYear, startMonth, startDay)
    val dayOfWeek = startDate.dayOfWeek.isoDayNumber

    val rrule = event["RRULE"] ?: ""
    var untilStr = ""
    var countStr = ""
    var byDayStr = ""

    for (part in rrule.split(";")) {
        when {
            part.startsWith("UNTIL=") -> untilStr = part.substringAfter("UNTIL=").take(8)
            part.startsWith("COUNT=") -> countStr = part.substringAfter("COUNT=")
            part.startsWith("BYDAY=") -> byDayStr = part.substringAfter("BYDAY=")
        }
    }

    if (byDayStr.isNotEmpty()) {
        val dayMap = mapOf("MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7)
        val byDays = byDayStr.split(",").mapNotNull { dayMap[it.trim()] }
        if (byDays.isNotEmpty() && dayOfWeek !in byDays) {
            return null
        }
    }

    // LOCATION 约定：前导空格=仅教师；尾随/无空格=仅教室；中间空格分隔两者。
    // 优先读导出侧写的扩展属性 —— LOCATION 一个字段表达不了「教室本身含空格」，
    // 别的日历 App 会忽略 X- 前缀，不影响互通。
    val explicitClassroom = event["X-NEXIO-CLASSROOM"]
    val explicitTeacher = event["X-NEXIO-TEACHER"]
    val classroom: String
    val teacher: String
    if (explicitClassroom != null || explicitTeacher != null) {
        classroom = explicitClassroom.orEmpty().trim()
        teacher = explicitTeacher.orEmpty().trim()
    } else {
        when {
            location.startsWith(" ") -> {
                classroom = ""
                teacher = location.trim()
            }
            location.trimEnd().endsWith(" ") || !location.contains(" ") -> {
                classroom = location.trim()
                teacher = ""
            }
            else -> {
                val spaceIndex = location.indexOf(' ')
                classroom = location.substring(0, spaceIndex).trim()
                teacher = location.substring(spaceIndex + 1).trim()
            }
        }
    }

    // 自定义时间的课：ICS 本身不带节次，导出侧用 X-NEXIO-CUSTOM-* 带出来。
    // 只有显式带了扩展属性才算「自定义时间课」—— 外部 ICS 的普通事件不该被我们
    // 标成自定义时间，那会把它们的节次映射改成硬编码钟点。
    val customStartAttr = event["X-NEXIO-CUSTOM-START"]?.trim()
    val customEndAttr = event["X-NEXIO-CUSTOM-END"]?.trim()
    val hasExplicitCustomTime = !customStartAttr.isNullOrBlank() && !customEndAttr.isNullOrBlank()

    // 用 buildMap 而不是 mapOf：自定义时间的两个键只在命中时才加，避免 map 的值类型
    // 被推断成 Any?（下游契约是 Map<String, Any>，null 值会一路传进 Course）。
    return buildMap<String, Any> {
        put("name", summary)
        put("classroom", classroom)
        put("teacher", teacher)
        put("dayOfWeek", dayOfWeek)
        put("startTotalMinutes", startTotalMinutes)
        put("endTotalMinutes", endTotalMinutes)
        put("selectedWeeks", emptyList<Int>())
        put("startDate", startDateStr)
        put("untilDate", untilStr)
        put("count", countStr)
        put("isAllDay", isAllDay)
        put("isCustomTime", hasExplicitCustomTime)
        if (hasExplicitCustomTime) {
            put("customStartTime", customStartAttr.orEmpty())
            put("customEndTime", customEndAttr.orEmpty())
        }
    }
}
