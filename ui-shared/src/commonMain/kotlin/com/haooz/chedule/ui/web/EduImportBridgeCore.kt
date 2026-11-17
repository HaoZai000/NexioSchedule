package com.haooz.chedule.ui.web

import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.KeyValueEditor
import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonArray
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.i18n.ImportStr
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** 契约性拒绝：脚本会在 `await` 处拿到 Error，不是崩溃。 */
class EduBridgeReject(message: String) : Exception(message)

/**
 * 教务导入桥的跨平台内核：四类负载（课程 / 配置 / 时间段 / 组合作息）的解析、落盘与调用顺序约束都在此，Android 与 iOS 共用一份（两端各写一遍必然漂移）。
 *
 * ⚠ 与云端脚本仓库是契约：JSON 字段名、[PREFS_NAME] 键名、默认值（20 / 45 / 10 / 1）逐字不能改，改了存量用户的作息配置会静默错位。
 *
 * 平台侧只留三件事：调用 [handle]、兑现 Promise、对话框三件套与 toast。
 */
class EduImportBridgeCore(
    private val onCoursesImported: (List<Course>) -> Unit,
    private val onTaskCompleted: () -> Unit,
) {

    /** 本次导入的目标课表；null = 当前课表 */
    private var importTableId: String? = null

    // 组合作息的顺序状态（契约 2.1.5 / 2.1.7）：saveComboSchedule 需以已提交的基础时间段为骨架；combo 提交后本次不再接受 savePresetTimeSlots（否则方案绑定被覆盖）。
    // ⚠ 只在 [setImportTableId]（每次执行前必调）重置，不靠页面生命周期（会跨轮次残留）。
    private var baseTimeSlotsSaved = false
    private var comboSaved = false

    /** 同名课程复用同色（与 Android 迁移前逐字一致，别改成随机） */
    private val courseJson = EduCourseJson()

    fun setImportTableId(tableId: String?) {
        importTableId = tableId
        baseTimeSlotsSaved = false
        comboSaved = false
    }

    /**
     * @return 回给 JS 的字面量（`"true"`）；null = 内核不负责，交给平台。
     * @throws EduBridgeReject 契约拒绝或解析失败，平台侧应转成 Promise reject。
     */
    fun handle(method: String, args: List<Any?>): String? = when (method) {
        "saveImportedCourses" -> saveCourses(args.firstOrNull()?.toString().orEmpty())
        "saveCourseConfig" -> saveConfig(args.firstOrNull()?.toString().orEmpty())
        "savePresetTimeSlots" -> saveTimeSlots(args.firstOrNull()?.toString().orEmpty())
        "saveComboSchedule" -> saveCombo(args.firstOrNull()?.toString().orEmpty())
        "notifyTaskCompletion" -> {
            importTableId = null
            onTaskCompleted()
            "true"
        }
        else -> null
    }

    // ── 1.1 课程 ────────────────────────────────────────────────────

    private fun saveCourses(json: String): String {
        val courses = courseJson.parse(json, importTableId)
        if (courses.isEmpty()) throw EduBridgeReject(ImportStr.bridgeCoursesEmpty())

        storeTarget()
        onCoursesImported(courses)

        val badDay = courses.count { it.dayOfWeek !in 1..7 }
        if (badDay > 0) {
            // 这类课落库后日期边界计算会跳过 → 表上看不见；不 reject 是怕偶发脏值毁掉整批
            NexioLog.w(TAG, ImportStr.bridgeInvalidDayWarningN(badDay))
        }
        return "true"
    }

    private fun randomId(): String =
        Random.Default.nextLong().toString(36) + Random.Default.nextLong().toString(36)

    // ── 1.3 课表配置 ────────────────────────────────────────────────

    private fun saveConfig(json: String): String {
        val config = try {
            parseJsonObject(json)
        } catch (e: Exception) {
            throw EduBridgeReject(ImportStr.bridgeParseConfigFailed(e.message))
        }
        val totalWeeks = config.optInt("semesterTotalWeeks", 20)
        val classDuration = config.optInt("defaultClassDuration", 45)
        val breakDuration = config.optInt("defaultBreakDuration", 10)
        val firstDay = config.optInt("firstDayOfWeek", 1)
        val startDate = CourseRepository.normalizeClassStartDate(
            config.optString("semesterStartDate").takeIf { it.isNotBlank() }
        )
        AppStorage.store(PREFS_NAME).edit {
            // 显式写/清：本次没传的字段不能残留上一次导入的值
            if (startDate != null) putString("semester_start_date", startDate)
            else remove("semester_start_date")
            if (totalWeeks > 0) putInt("semester_total_weeks", totalWeeks)
            else remove("semester_total_weeks")
            putInt("default_class_duration", classDuration)
            putInt("default_break_duration", breakDuration)
            putInt("first_day_of_week", firstDay)
            writeTarget(this)
        }
        NexioLog.d(TAG, ImportStr.bridgeConfigLog(startDate, totalWeeks, firstDay))
        return "true"
    }

    // ── 1.2 预设时间段 ──────────────────────────────────────────────

    private fun saveTimeSlots(json: String): String {
        if (comboSaved) throw EduBridgeReject(ImportStr.bridgeComboAlreadySaved())
        val slots = try {
            parseJsonArray(json)
        } catch (e: Exception) {
            throw EduBridgeReject(ImportStr.bridgeParseTimeSlotsFailed(e.message))
        }
        if (slots.isEmpty()) throw EduBridgeReject(ImportStr.bridgeTimeSlotsEmpty())
        AppStorage.store(PREFS_NAME).edit {
            putString("preset_time_slots", json)
            writeTarget(this)
        }
        baseTimeSlotsSaved = true
        return "true"
    }

    // ── 1.4 组合作息方案 ────────────────────────────────────────────

    private fun saveCombo(json: String): String {
        // 官方契约 2.1.7：没有骨架就无从做差量合并
        if (!baseTimeSlotsSaved) {
            throw EduBridgeReject(ImportStr.bridgeCallSaveTimeSlotsFirst())
        }
        val combo = try {
            parseJsonObject(json)
        } catch (e: Exception) {
            throw EduBridgeReject(ImportStr.bridgeParseComboFailed(e.message))
        }
        val count = (combo["publicSchedules"] as? JsonArray)?.size ?: 0
        if (count == 0) throw EduBridgeReject(ImportStr.bridgeComboNoPublicSchedules())
        // 原样存，结构校验与差量合并交给消费端（与 preset_time_slots 同套路）
        AppStorage.store(PREFS_NAME).edit {
            putString("combo_schedule", json)
            writeTarget(this)
        }
        comboSaved = true
        return "true"
    }

    // ── 目标课表登记 ────────────────────────────────────────────────

    private fun storeTarget() {
        AppStorage.store(PREFS_NAME).edit { writeTarget(this) }
    }

    /**
     * ⚠ null 必须显式 remove —— `KeyValueEditor.putString` 只收非空值，不像
     * SharedPreferences 的 `putString(k, null)` 会隐式移除该键。
     */
    private fun writeTarget(editor: KeyValueEditor) {
        val target = importTableId
        if (target != null) editor.putString(TARGET_KEY, target)
        else editor.remove(TARGET_KEY)
    }

    private companion object {
        const val TAG = "EduImportBridge"
        const val PREFS_NAME = "edu_import_prefs"
        const val TARGET_KEY = "target_schedule_id"
    }
}

/**
 * AI 草稿的解析结果。
 *
 * [merged] 是合并掉的条数（同课同周次的连续节次合成一段、同课同节次的周次取并集）；
 * [skipped] 是因缺少上课星期/节次/周次、且没有可用真实时间而跳过的课程名
 * —— 两者都要在预览里让用户看得见，不能悄悄消失。
 */
internal data class EduDraftCourses(
    val courses: List<Course>,
    val merged: Int = 0,
    val skipped: List<String> = emptyList(),
)

/**
 * 教务脚本的课程 JSON → [Course]。
 *
 * ⚠ 这份字段映射只有一处（以前是桥里的私有 `toCourse`，AI 导入的预览也要用同一套，
 * 复制一份必然漂移）：`position`→classroom、`day`→dayOfWeek、`weeks` 是周次数组。
 *
 * 同名课程复用同色，所以这个对象要在一次导入内保持同一个实例。
 */
internal class EduCourseJson {

    private val nameColors = mutableMapOf<String, Long>()
    private var colorIndex = 0

    /** @throws EduBridgeReject JSON 坏掉时（而不是静默给空列表 —— 那会变成「导入成功但没有课」） */
    fun parse(json: String, importTableId: String?): List<Course> {
        val elements = try {
            parseJsonArray(json).mapNotNull { it as? JsonObject }
        } catch (e: Exception) {
            throw EduBridgeReject(ImportStr.bridgeParseCoursesFailed(e.message))
        }
        return elements.map { toCourse(it, importTableId) }
    }

    /**
     * AI 草稿专用：严格解析 + 合并重复，**不猜任何字段**。
     *
     * 与 [parse] 的差别（[parse] 服务网页脚本桥，历史行为不能动）：
     * - `day` 不在 1..7、或 `weeks` 为空 → 这门课跳过，而不是默认成「周一第 1 节 1-20 周」；
     * - 没有节次、但给了合法真实起止时间（`isCustomTime`）→ 收下，按时间轴定位；
     * - 同课同周次且节次连续（1-2 + 3-4）合成一段；
     * - 同课（名称/教师/地点/星期/节次相同）合并成一条，`weeks` 取并集 —— 脚本按「一周一行」
     *   输出时不该变成一堆重复卡片。
     *
     * 跳过的课与合并掉的条数都要在预览里让用户看见，不能悄悄消失（见 [EduDraftCourses]）。
     */
    fun parseDraft(json: String, importTableId: String?): EduDraftCourses {
        val elements = try {
            parseJsonArray(json).mapNotNull { it as? JsonObject }
        } catch (e: Exception) {
            throw EduBridgeReject(ImportStr.bridgeParseCoursesFailed(e.message))
        }
        val valid = mutableListOf<Course>()
        val skipped = mutableListOf<String>()
        var mergedCount = 0
        for (o in elements) {
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val teacher = o.optString("teacher").trim()
            val position = o.optString("position").trim()
            val day = o.optInt("day", -1)
            val weeks = (o["weeks"] as? JsonArray)
                ?.mapNotNull { it.toString().trim('"').toIntOrNull() }
                ?.filter { it in 1..CourseRepository.MAX_TOTAL_WEEKS }
                ?.distinct()
                ?.sorted()
                .orEmpty()
            val customStart = o.optString("customStartTime").trim()
            val customEnd = o.optString("customEndTime").trim()
            val custom = o.optBoolean("isCustomTime") && validClock(customStart) && validClock(customEnd)
            val startSection = o.optInt("startSection", 0)
            val endSection = o.optInt("endSection", startSection)
            // 星期 / 周次 / 上课时刻缺一项就不猜：宁可少一门，也不能落一门「看着正常」的假课
            val usable = day in 1..7 && weeks.isNotEmpty() &&
                (custom || (startSection >= 1 && endSection >= startSection))
            if (!usable) {
                skipped += name
                continue
            }
            val sectionStart = startSection.coerceAtLeast(1)
            valid += Course(
                id = randomId(),
                name = name,
                classroom = position,
                teacher = teacher,
                dayOfWeek = day,
                startSection = sectionStart,
                endSection = endSection.coerceAtLeast(sectionStart),
                startWeek = weeks.first(),
                endWeek = weeks.last(),
                weekType = Course.WEEK_TYPE_ALL,
                selectedWeeks = weeks,
                colorRes = o.optInt("color", 0).takeIf { it != 0 }?.toLong()
                    ?: colorForName(name),
                isCustomTime = custom,
                customStartTime = customStart.takeIf { custom },
                customEndTime = customEnd.takeIf { custom },
                scheduleId = importTableId ?: "",
            )
        }

        // 第一层：同课同周次且节次连续（1-2 + 3-4）先合成一段 —— 标准适配器就是这么输出的，
        // 不合并的话同一门课会在课表与今日页里拆成两张卡。
        val continuous = mutableListOf<Course>()
        valid.groupBy {
            listOf(
                it.name, it.teacher, it.classroom, it.dayOfWeek,
                it.selectedWeeks.joinToString(","),
                it.isCustomTime, it.customStartTime, it.customEndTime,
            ).joinToString("|")
        }.values.forEach { group ->
            val sorted = group.sortedBy { it.startSection }
            var current = sorted.first()
            for (i in 1 until sorted.size) {
                val next = sorted[i]
                val mergeable = !current.isCustomTime && !next.isCustomTime &&
                    next.startSection <= current.endSection + 1
                if (!mergeable) {
                    continuous += current
                    current = next
                    continue
                }
                if (next.endSection > current.endSection) {
                    current = current.copy(endSection = next.endSection)
                }
                mergedCount++
            }
            continuous += current
        }

        // 第二层：同课同节次，周次取并集（脚本按「一段一行」输出时不该变成一堆重复卡片）
        val merged = LinkedHashMap<String, Course>()
        for (course in continuous) {
            val key = listOf(
                course.name, course.teacher, course.classroom, course.dayOfWeek,
                course.startSection, course.endSection, course.isCustomTime,
                course.customStartTime, course.customEndTime,
            ).joinToString("|")
            val previous = merged[key]
            if (previous == null) {
                merged[key] = course
                continue
            }
            mergedCount++
            val union = (previous.selectedWeeks + course.selectedWeeks).distinct().sorted()
            merged[key] = previous.copy(
                startWeek = union.first(),
                endWeek = union.last(),
                selectedWeeks = union,
            )
        }
        return EduDraftCourses(merged.values.toList(), mergedCount, skipped)
    }

    /** HH:mm（0-23:0-59）。自定义时间写坏了不能当成有效时间用。 */
    private fun validClock(value: String): Boolean {
        val parts = value.split(':')
        if (parts.size != 2) return false
        val hour = parts[0].toIntOrNull() ?: return false
        val minute = parts[1].toIntOrNull() ?: return false
        return hour in 0..23 && minute in 0..59
    }

    private fun toCourse(o: JsonObject, importTableId: String?): Course {
        val name = o.optString("name")
        val weeks = (o["weeks"] as? JsonArray)
            ?.mapNotNull { it.toString().trim('"').toIntOrNull() }
            .orEmpty()
            .sorted()
        val startSection = o.optInt("startSection", 1)
        return Course(
            id = o.optString("id").takeIf { it.isNotBlank() } ?: randomId(),
            name = name,
            classroom = o.optString("position"),
            teacher = o.optString("teacher"),
            dayOfWeek = o.optInt("day", 1),
            startSection = startSection,
            endSection = o.optInt("endSection", startSection),
            startWeek = weeks.minOrNull() ?: 1,
            endWeek = weeks.maxOrNull() ?: 20,
            weekType = Course.WEEK_TYPE_ALL,
            selectedWeeks = weeks,
            colorRes = o.optInt("color", 0).takeIf { it != 0 }?.toLong()
                ?: colorForName(name),
            isCustomTime = o.optBoolean("isCustomTime"),
            customStartTime = o.optString("customStartTime").takeIf { it.isNotBlank() },
            customEndTime = o.optString("customEndTime").takeIf { it.isNotBlank() },
            scheduleId = importTableId ?: "",
        )
    }

    private fun colorForName(name: String): Long {
        val key = name.trim()
        nameColors[key]?.let { return it }
        val color = Course.courseColors[colorIndex % Course.courseColors.size]
        colorIndex++
        nameColors[key] = color
        return color
    }

    private fun randomId(): String =
        Random.Default.nextLong().toString(36) + Random.Default.nextLong().toString(36)
}
