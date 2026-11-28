package com.haooz.chedule.data.backup

import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.displayText
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseJsonObject
import kotlin.collections.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** 备份体：要么是单课表备份，要么是覆盖式全量备份 */
sealed interface BackupPayload {
    data class Single(
        val scheduleName: String,
        val courses: List<Map<String, Any>>,
        val timeConfig: Any?,
        val semesterSettings: SemesterSettings?,
        val teachingWeekReorganizations: List<TeachingWeekReorganizationRule>?,
        val holiday: Map<String, Any?>? = null,
    ) : BackupPayload

    data class Full(val data: Map<String, Any>) : BackupPayload
}

data class SemesterSettings(
    val classStartTime: String?,
    val currentWeek: Int?,
    val totalWeeks: Int?,
    val smartWeekend: Boolean? = null,
    val showNonCurrentWeek: Boolean? = null,
)

/**
 * 全量恢复会覆盖现有课表，因此必须在写入前识别并校验备份结构。
 *
 * @throws IllegalArgumentException 不是本应用导出的备份 / 结构非法时抛出。
 */
fun parseBackupPayload(json: String): BackupPayload {
    @Suppress("UNCHECKED_CAST")
    val data = (jsonToPlainValue(parseJsonObject(json)) as? Map<String, Any>)
        ?: throw IllegalArgumentException(
            displayText(
                "文件内容不是有效的 JSON 对象",
                "The file is not a valid JSON object"
            )
        )

    val scheduleName = data["schedule_name"]
    val courses = data["courses"]
    if (scheduleName is String && courses is List<*>) {
        require(!data.containsKey("settings")) {
            displayText(
                "这是普通课表 JSON，请使用课表 JSON 导入入口，不能按本地备份恢复",
                "This is a plain schedule JSON. Use the schedule JSON import entry instead of restoring it as a local backup."
            )
        }
        val courseList = courses.map { rawCourse ->
            val fields = rawCourse as? Map<*, *>
                ?: throw IllegalArgumentException(
                    displayText(
                        "单课表备份包含无效课程数据",
                        "The single-schedule backup contains invalid course data"
                    )
                )
            require(fields.keys.all { it is String }) {
                displayText(
                    "单课表备份包含无效课程字段",
                    "The single-schedule backup contains invalid course fields"
                )
            }
            @Suppress("UNCHECKED_CAST")
            fields as Map<String, Any>
        }
        // 整份 JSON 字符串（现行格式）或早期备份的扁平 Map，两种都要能读
        val timeConfig = data["time_config"]
        val semesterSettings = if (!data.containsKey("semester_settings")) {
            null
        } else {
            val rawSettings = data["semester_settings"] as? Map<*, *>
                ?: throw IllegalArgumentException(
                    displayText(
                        "单课表备份的学期设置无效",
                        "Invalid semester settings in the single-schedule backup"
                    )
                )
            fun exactInteger(key: String, positive: Boolean = false): Int? {
                if (!rawSettings.containsKey(key)) return null
                val number = (rawSettings[key] as? Number)?.toDouble()
                    ?: throw IllegalArgumentException(
                        displayText(
                            "单课表备份的${key}无效",
                            "Invalid $key in the single-schedule backup"
                        )
                    )
                require(number.isFinite() && number % 1.0 == 0.0 &&
                    number >= Int.MIN_VALUE.toDouble() && number <= Int.MAX_VALUE.toDouble()
                ) {
                    displayText(
                        "单课表备份的${key}无效",
                        "Invalid $key in the single-schedule backup"
                    )
                }
                val value = number.toInt()
                require(!positive || value > 0) {
                    displayText(
                        "单课表备份的${key}无效",
                        "Invalid $key in the single-schedule backup"
                    )
                }
                require(key != "total_weeks" || value <= CourseRepository.MAX_TOTAL_WEEKS) {
                    displayText(
                        "单课表备份的总周数超出支持范围",
                        "Total weeks in the single-schedule backup exceed the supported range"
                    )
                }
                return value
            }
            val classStartTime = if (rawSettings.containsKey("class_start_time")) {
                val raw = rawSettings["class_start_time"] as? String
                    ?: throw IllegalArgumentException(
                        displayText(
                            "单课表备份的开学日期无效",
                            "Invalid semester start date in the single-schedule backup"
                        )
                    )
                CourseRepository.normalizeClassStartDate(raw)
                    ?: throw IllegalArgumentException(
                        displayText(
                            "单课表备份的开学日期无效",
                            "Invalid semester start date in the single-schedule backup"
                        )
                    )
            } else null
            SemesterSettings(
                classStartTime = classStartTime,
                currentWeek = exactInteger("current_week"),
                totalWeeks = exactInteger("total_weeks", positive = true),
                smartWeekend = rawSettings["smart_weekend"] as? Boolean,
                showNonCurrentWeek = rawSettings["show_non_current_week"] as? Boolean,
            )
        }
        val reorganizationFieldPresent = data.containsKey("teaching_week_reorganizations")
        validateSingleScheduleCourseData(courseList, CourseRepository.MAX_TOTAL_WEEKS)
        val rules = TeachingWeekReorganization.fromBackupValue(
            value = data["teaching_week_reorganizations"],
            present = reorganizationFieldPresent,
            totalWeeks = semesterSettings?.totalWeeks ?: 20,
            preserveOutOfRangeRules = true,
        ).takeIf { reorganizationFieldPresent }
        if (reorganizationFieldPresent) {
            require(semesterSettings?.classStartTime != null) {
                displayText(
                    "包含教学周重组的单课表备份必须有有效开学日期",
                    "A single-schedule backup with teaching-week reorganization must have a valid semester start date"
                )
            }
        }
        return BackupPayload.Single(
            scheduleName = scheduleName,
            courses = courseList,
            timeConfig = timeConfig,
            semesterSettings = semesterSettings,
            teachingWeekReorganizations = rules,
            holiday = (data["holiday"] as? Map<*, *>)
                ?.entries?.associate { (k, v) -> k.toString() to v },
        )
    }

    val looksLikeFullBackup =
        data.containsKey("schedule_names") || data.keys.any { it.startsWith("schedule_") }
    if (!looksLikeFullBackup) {
        throw IllegalArgumentException(
            displayText(
                "不是本应用导出的备份文件",
                "This backup was not exported by Nexio Schedule"
            )
        )
    }
    validateFullScheduleBackupStructure(data)
    HolidayManager.decodeAllSchedulesBackupData(data)
    return BackupPayload.Full(data)
}

private val prettyBackupJson = Json { prettyPrint = true }

/** 输出带缩进的备份 JSON，省略值为 null 的字段。 */
fun encodeBackupJson(data: Map<String, Any?>): String =
    prettyBackupJson.encodeToString(
        JsonElement.serializer(),
        toJsonElementWithoutNulls(data),
    )
