package com.haooz.chedule.data.backup

import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseJsonObject

/**
 * 解析「导入课表」粘贴的 JSON 文本。
 *
 * 返回 `Triple(是否成功, 提示消息, 课表数据 Map)`；数据供后续 `applyScheduleData` 写入。
 *  - 含 `semester_settings` / `teaching_week_reorganizations` → 本地单课表备份（走错入口，拒绝）
 *  - 含 `settings` / `courses` → 课表数据（其中「拾光格式」再经 [parseShiguangScheduleJson] 归一化）
 *  - 其他 → 无效格式
 */
fun parseFullScheduleJson(text: String): Triple<Boolean, String, Map<String, Any>?> {
    try {
        @Suppress("UNCHECKED_CAST")
        val data = jsonToPlainValue(parseJsonObject(text)) as Map<String, Any>

        if (data.containsKey("semester_settings") || data.containsKey("teaching_week_reorganizations")) {
            return Triple(false, "这是本地单课表备份，请使用本地备份恢复入口", null)
        }

        if (data.containsKey("settings") || data.containsKey("courses")) {
            // 拾光格式：有 courses + config（无 settings）
            if (data.containsKey("config") && data.containsKey("courses") && !data.containsKey("settings")) {
                return parseShiguangScheduleJson(data)
            }
            return Triple(true, "解析成功", data)
        }

        return Triple(false, "无效的课表数据格式", null)
    } catch (e: Exception) {
        return Triple(false, "解析失败: ${e.message}", null)
    }
}

// 将拾光的 courses、timeSlots 和 config 转换成内部课表格式。
private fun parseShiguangScheduleJson(data: Map<String, Any>): Triple<Boolean, String, Map<String, Any>?> {
    try {
        @Suppress("UNCHECKED_CAST")
        val shiguangCourses = data["courses"] as? List<Map<String, Any>>
        if (shiguangCourses.isNullOrEmpty()) {
            return Triple(false, "未找到课程数据", null)
        }

        val courses = mutableListOf<Map<String, Any>>()
        for (sg in shiguangCourses) {
            val name = sg["name"] as? String ?: continue
            val teacher = sg["teacher"] as? String ?: ""
            val position = sg["position"] as? String ?: ""
            val day = (sg["day"] as? Number)?.toInt() ?: continue
            val startSection = (sg["startSection"] as? Number)?.toInt() ?: continue
            val endSection = (sg["endSection"] as? Number)?.toInt() ?: startSection
            @Suppress("UNCHECKED_CAST")
            val weeks = (sg["weeks"] as? List<Number>)?.map { it.toInt() } ?: emptyList()
            val color = (sg["color"] as? Number)?.toInt()

            if (weeks.isEmpty()) continue

            courses.add(mapOf(
                "name" to name,
                "teacher" to teacher,
                "classroom" to position,
                "dayOfWeek" to day,
                "startSection" to startSection,
                "endSection" to endSection,
                "selectedWeeks" to weeks,
                "shiguangColor" to (color ?: -1)
            ))
        }

        @Suppress("UNCHECKED_CAST")
        val config = data["config"] as? Map<String, Any>
        val settings = mutableMapOf<String, Any>()
        if (config != null) {
            (config["semesterStartDate"] as? String)?.let { settings["class_start_time"] = it }
            (config["semesterTotalWeeks"] as? Number)?.toInt()?.let { settings["total_weeks"] = it }
            (config["firstDayOfWeek"] as? Number)?.toInt()?.let { settings["first_day_of_week"] = it }
            (config["defaultClassDuration"] as? Number)?.toInt()?.let { settings["class_duration"] = it }
            (config["defaultBreakDuration"] as? Number)?.toInt()?.let { settings["break_duration"] = it }
        }

        @Suppress("UNCHECKED_CAST")
        val timeSlots = data["timeSlots"] as? List<Map<String, Any>>
        val times = mutableMapOf<String, Any>()
        if (!timeSlots.isNullOrEmpty()) {
            val morningTimes = mutableMapOf<String, String>()
            val afternoonTimes = mutableMapOf<String, String>()
            val eveningTimes = mutableMapOf<String, String>()

            for (slot in timeSlots) {
                val number = (slot["number"] as? Number)?.toInt() ?: continue
                val startTime = slot["startTime"] as? String ?: continue
                val endTime = slot["endTime"] as? String ?: continue
                val timeStr = "$startTime-$endTime"

                when {
                    number <= 6 -> morningTimes[number.toString()] = timeStr
                    number <= 12 -> afternoonTimes[(number - 6).toString()] = timeStr
                    else -> eveningTimes[(number - 12).toString()] = timeStr
                }
            }

            if (morningTimes.isNotEmpty()) times["morning"] = morningTimes
            if (afternoonTimes.isNotEmpty()) times["afternoon"] = afternoonTimes
            if (eveningTimes.isNotEmpty()) times["evening"] = eveningTimes
        }

        val result = mapOf<String, Any>(
            "schedule_name" to "拾光课程表导入",
            "courses" to courses,
            "settings" to settings,
            "times" to times
        )

        return Triple(true, "解析成功", result)
    } catch (e: Exception) {
        return Triple(false, "拾光课程表解析失败: ${e.message}", null)
    }
}
