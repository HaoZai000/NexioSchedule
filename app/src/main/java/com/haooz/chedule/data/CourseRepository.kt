package com.haooz.chedule.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.graphics.scale
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

/** 课程数据仓库（SharedPreferences，单例） */
class CourseRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()

    private val courseCache = mutableMapOf<String, List<Course>>()
    private val occupiedWeeksCache = mutableMapOf<String, Set<Int>>()
    // getPeriodTimes 等高频路径的配置缓存
    private val timeConfigCache = mutableMapOf<Long, TimeConfig>()
    private var timeConfigIdsCache: List<Long>? = null
    // 几乎所有 key 拼接都经过它，全类最热路径
    private var currentScheduleIdCache: String? = null
    private var scheduleNamesCache: List<String>? = null
    private var globalSectionTimesCache: Map<Int, String>? = null
    private val combinationStyleCache = mutableMapOf<Long, CombinationStyle>()

    // 壁纸解码是主要瓶颈，按可用内存 1/8 做 Lru 缓存
    private val wallpaperCache: android.util.LruCache<Long, android.graphics.Bitmap> =
        run {
            val maxBytes = (Runtime.getRuntime().maxMemory() / 8).coerceAtLeast(4L * 1024 * 1024)
            object : android.util.LruCache<Long, android.graphics.Bitmap>(maxBytes.toInt()) {
                override fun sizeOf(key: Long, value: android.graphics.Bitmap): Int {
                    return value.byteCount
                }
            }
        }

    init {
        migrateToTimeConfigsIfNeeded()
        migrateScheduleTimeConfigBindingsIfNeeded()
    }

    // 变更回调
    var onCourseChanged: ((action: String, courseId: String) -> Unit)? = null

    /** 绕过本类 setter 直接改写 prefs 后必须调用，否则读到陈旧缓存 */
    private fun invalidateAllCaches() {
        courseCache.clear()
        occupiedWeeksCache.clear()
        timeConfigCache.clear()
        timeConfigIdsCache = null
        currentScheduleIdCache = null
        scheduleNamesCache = null
        globalSectionTimesCache = null
        combinationStyleCache.clear()
    }

    /** 节数/节次时间变化会同时影响全局节次映射与分钟级占用判断 */
    private fun invalidateTimeCaches() {
        occupiedWeeksCache.clear()
        globalSectionTimesCache = null
    }

    /** 为 true 时 [notifyCourseChanged] 延迟到批次结束统一提交，避免连续磁盘写与 UI 抖动 */
    private var batchingSettings = false

    private fun notifyCourseChanged(action: String, courseId: String = "") {
        if (action == "settings") {
            if (batchingSettings) return
            commitSettingsChanged()
            return
        }
        onCourseChanged?.invoke(action, courseId)
    }

    /** 更新时间戳（本地修改不被远程覆盖）、失效时间缓存、通知 UI */
    private fun commitSettingsChanged() {
        val prefix = getScheduleKeyPrefix()
        prefs.edit { putLong("${prefix}_settings_last_modified", System.currentTimeMillis()) }
        invalidateTimeCaches()
        onCourseChanged?.invoke("settings", "")
    }

    companion object {
        @Volatile
        private var INSTANCE: CourseRepository? = null

        fun getInstance(context: Context): CourseRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CourseRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        // 兼容旧代码的构造方式
        operator fun invoke(context: Context): CourseRepository = getInstance(context)
        private const val PREFS_NAME = "course_schedule_prefs"
        private const val KEY_COURSES = "courses"
        private const val KEY_CURRENT_WEEK = "current_week"
        private const val KEY_TOTAL_WEEKS = "total_weeks"
        private const val KEY_CLASS_START_TIME = "class_start_time"
        private const val KEY_SHOW_WEEKEND = "show_weekend"
        private const val KEY_SMART_WEEKEND = "smart_weekend"
        private const val KEY_SHOW_NON_CURRENT_WEEK = "show_non_current_week"
        private const val KEY_QUICK_TIME_ENABLED = "quick_time_enabled"
        private const val KEY_CLASS_DURATION = "class_duration"
        private const val KEY_SHORT_BREAK = "short_break"
        private const val KEY_LONG_BREAK = "long_break"
        private const val KEY_MORNING_START = "morning_start"
        private const val KEY_AFTERNOON_START = "afternoon_start"
        private const val KEY_EVENING_START = "evening_start"
        private const val KEY_CURRENT_SCHEDULE_ID = "current_schedule_id"
        private const val KEY_SCHEDULE_NAMES = "schedule_names"
        private const val KEY_PRE_CLASS_REMINDER = "pre_class_reminder"
        private const val KEY_PRE_CLASS_REMINDER_MINUTES = "pre_class_reminder_minutes"
        private const val KEY_NEXT_DAY_REMINDER = "next_day_reminder"
        private const val KEY_NEXT_DAY_REMINDER_HOUR = "next_day_reminder_hour"
        private const val KEY_NEXT_DAY_REMINDER_MINUTE = "next_day_reminder_minute"
        private const val KEY_ISLAND_NOTIFICATION = "island_notification"
        private const val KEY_CLASS_DND = "class_dnd_enabled"
        /** 上课时启用的系统勿扰档位：0=勿扰模式 (DND, NONE)，1=静音模式 (SILENT, PRIORITY，闹钟仍响) */
        private const val KEY_CLASS_DND_MODE = "class_dnd_mode"
        private const val KEY_SHIFT_MODE = "shift_mode_enabled"
        private const val KEY_SHIFT_SELECTED_SCHEDULES = "shift_selected_schedules"
        private const val KEY_DEFAULT_HOMEPAGE = "default_homepage"
        private const val KEY_WIDGET_PADDING_MODE = "widget_padding_mode"
        private const val KEY_TODAY_SHOW_WALLPAPER = "today_show_wallpaper"
        @Suppress("UNUSED") private const val KEY_WALLPAPER_OFFSET_X = "wallpaper_offset_x"
        @Suppress("UNUSED") private const val KEY_WALLPAPER_OFFSET_Y = "wallpaper_offset_y"
        @Suppress("UNUSED") private const val KEY_WALLPAPER_SCALE = "wallpaper_scale"
        @Suppress("UNUSED") private const val WALLPAPER_FILE_NAME = "schedule_wallpaper.png"
        private const val SCHEDULE_KEY_PREFIX = "schedule_"
        // 当前只有单搭配（id 恒为 0），保留 id 维度以免将来恢复多搭配再改存储
        private const val KEY_COMBINATION_IDS = "combination_ids"
        private const val KEY_CURRENT_COMBINATION_ID = "current_combination_id"
        private const val COMBINATION_WALLPAPER_PREFIX = "combination_wallpaper_"
        // 合并存储键（取代下面 17 个 comb_xxx_{id} 分散键）
        private const val COMBINATION_STYLE_PREFIX = "combination_style_"
        private const val KEY_COMBINATION_OFFSET_X_PREFIX = "comb_offset_x_"
        private const val KEY_COMBINATION_OFFSET_Y_PREFIX = "comb_offset_y_"
        private const val KEY_COMBINATION_SCALE_PREFIX = "comb_scale_"
        private const val KEY_COMBINATION_CARD_BLUR_PREFIX = "comb_card_blur_"
        private const val KEY_COMBINATION_CARD_ALPHA_PREFIX = "comb_card_alpha_"
        private const val KEY_COMBINATION_CARD_HEIGHT_PREFIX = "comb_card_height_"
        private const val KEY_COMBINATION_CARD_CORNER_PREFIX = "comb_card_corner_"
        private const val KEY_COMBINATION_WALLPAPER_BRIGHTNESS_PREFIX = "comb_wp_brightness_"
        private const val KEY_COMBINATION_WALLPAPER_IS_LIGHT_PREFIX = "comb_wp_is_light_"
        private const val KEY_COMBINATION_SHOW_BREAK_DIVIDERS_PREFIX = "comb_break_div_"
        private const val KEY_COMBINATION_CARD_CONTENT_ALIGNMENT_PREFIX = "comb_card_align_"
        private const val KEY_COMBINATION_CARD_TEXT_COLOR_PREFIX = "comb_card_text_color_"
        private const val KEY_COMBINATION_CARD_TEXT_SCALE_PREFIX = "comb_card_text_scale_"
        private const val KEY_COMBINATION_SHOW_CLASSROOM_PREFIX = "comb_show_classroom_"
        private const val KEY_COMBINATION_SHOW_TEACHER_PREFIX = "comb_show_teacher_"
        private const val KEY_COMBINATION_CARD_REFRACTION_PREFIX = "comb_card_refraction_"
        private const val KEY_COMBINATION_WALLPAPER_BLUR_PREFIX = "comb_wp_blur_"
        private const val KEY_TIME_CONFIG_IDS = "time_config_ids"
        private const val KEY_CURRENT_TIME_CONFIG_ID = "current_time_config_id"
        private const val TIME_CONFIG_PREFIX = "time_config_"
        // 不匹配 schedule_{name}_ 前缀，删/迁课表时需单独处理
        private const val SCHEDULE_TIME_CONFIG_PREFIX = "schedule_time_config_"
    }

    /** 云备份恢复可能把 Int 存成 Float，读失败时按 Float 再存回 Int */
    private fun safeGetInt(key: String, defValue: Int): Int {
        try {
            return prefs.getInt(key, defValue)
        } catch (_: ClassCastException) {
            val floatVal = prefs.getFloat(key, defValue.toFloat())
            val intVal = floatVal.toInt()
            prefs.edit { putInt(key, intVal) }
            return intVal
        }
    }

    fun getCoursesForSchedule(scheduleId: String): List<Course> {
        courseCache[scheduleId]?.let { return it }
        val key = "$SCHEDULE_KEY_PREFIX${scheduleId}_$KEY_COURSES"
        val json = prefs.getString(key, null) ?: return emptyList()
        // 坏 JSON 按真名读会得到空壳课，不当成有效课表，保留原数据供备份恢复
        if (!coursesJsonLooksValid(json)) return emptyList()
        val type = object : TypeToken<List<Course>>() {}.type
        return try {
            val courses = sanitizeCourses(gson.fromJson(json, type) ?: emptyList())
            courseCache[scheduleId] = courses
            courses
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getScheduleSummary(scheduleId: String): String {
        val courses = getCoursesForSchedule(scheduleId).ifEmpty { return "空课表" }
        val courseCount = courses.size
        val weeks = courses.flatMap { course ->
            course.selectedWeeks.ifEmpty {
                course.startWeek..course.endWeek
            }
        }.toSortedSet()
        val weekCount = weeks.size
        return "共${weekCount}周，${courseCount}节课"
    }

    private fun getScheduleKeyPrefix(): String {
        return getScheduleKeyPrefix(getCurrentScheduleId())
    }

    /** 指定课表前缀；导入到目标课表时用它把设置写到目标而非当前 */
    private fun getScheduleKeyPrefix(scheduleId: String): String {
        return "$SCHEDULE_KEY_PREFIX${scheduleId}_"
    }

    // USELESS_ELVIS：Gson 反序列化后非空字段仍可能是 null
    @Suppress(
        "SENSELESS_COMPARISON",
        "ELVIS_ALWAYS_NULL",
        "USELESS_ELVIS",
        "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS"
    )
    /** 任一稳定字段名出现即可；坏 JSON 判为无课，避免被当成空课表静默覆盖 */
    private fun coursesJsonLooksValid(json: String): Boolean {
        return json.contains("\"name\"") ||
            json.contains("\"dayOfWeek\"") ||
            json.contains("\"startSection\"") ||
            json.contains("\"id\"")
    }

    /** UnsafeAllocator 使旧 JSON 缺失字段为 null，无条件重建以拿到默认值 */
    private fun sanitizeCourses(courses: List<Course>): List<Course> {
        return courses.map { course ->
            Course(
                id = course.id ?: "",
                name = course.name ?: "",
                classroom = course.classroom ?: "",
                teacher = course.teacher ?: "",
                dayOfWeek = course.dayOfWeek,
                startSection = course.startSection,
                endSection = course.endSection,
                startWeek = course.startWeek,
                endWeek = course.endWeek,
                weekType = course.weekType,
                colorRes = course.colorRes,
                selectedWeeks = course.selectedWeeks ?: emptyList(),
                scheduleId = course.scheduleId ?: "",
                lastModified = course.lastModified,
                isCustomTime = course.isCustomTime,
                customStartTime = course.customStartTime,
                customEndTime = course.customEndTime
            )
        }
    }

    fun getAllCourses(): List<Course> {
        val scheduleId = getCurrentScheduleId()
        courseCache[scheduleId]?.let { return it }
        val key = "${getScheduleKeyPrefix()}$KEY_COURSES"
        val json = prefs.getString(key, null) ?: return emptyList()
        if (!coursesJsonLooksValid(json)) return emptyList()
        val type = object : TypeToken<List<Course>>() {}.type
        return try {
            val courses = sanitizeCourses(gson.fromJson(json, type) ?: emptyList())
            courseCache[scheduleId] = courses
            // 不预热 occupiedWeeksCache：仅编辑选周时用到，按需算即可，冷路径对首屏是白烧
            courses
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCourses(courses: List<Course>, notify: Boolean = true) {
        val scheduleId = getCurrentScheduleId()
        val key = "${getScheduleKeyPrefix()}$KEY_COURSES"
        val json = gson.toJson(courses)
        prefs.edit { putString(key, json) }
        courseCache[scheduleId] = courses
        occupiedWeeksCache.clear()
        if (notify) onCourseChanged?.invoke("bulk", "")
    }

    fun addCourse(course: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        val courseWithSchedule = if (course.scheduleId.isEmpty()) {
            course.copy(scheduleId = getCurrentScheduleId())
        } else {
            course
        }
        courses.add(courseWithSchedule)
        saveCourses(courses, notify = false)
        return courses
    }

    fun updateCourse(course: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        val index = courses.indexOfFirst { it.id == course.id }
        if (index != -1) {
            courses[index] = course.copy(lastModified = System.currentTimeMillis())
            saveCourses(courses, notify = false)
        }
        return courses
    }

    fun updateCoursesByName(oldName: String, updated: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        var changed = false
        for (i in courses.indices) {
            if (courses[i].name == oldName) {
                courses[i] = courses[i].copy(
                    name = updated.name,
                    colorRes = updated.colorRes,
                    lastModified = System.currentTimeMillis()
                )
                changed = true
            }
        }
        if (changed) {
            saveCourses(courses, notify = false)
        }
        return courses
    }

    fun deleteCourse(courseId: String): List<Course> {
        val courses = getAllCourses().toMutableList()
        courses.removeAll { it.id == courseId }
        saveCourses(courses, notify = false)
        return courses
    }

    /** 仅删该周实例；删光则整条删除。通知由 ViewModel 统一处理，避免竞态 */
    fun deleteCourseForWeek(courseId: String, week: Int): List<Course> {
        val courses = getAllCourses().toMutableList()
        val index = courses.indexOfFirst { it.id == courseId }
        if (index != -1) {
            val updated = removeWeekFrom(courses[index], week)
            if (updated == null) {
                courses.removeAt(index)
            } else {
                courses[index] = updated
            }
            saveCourses(courses, notify = false)
        }
        return courses
    }

    /** @return 移除后的新课程；最后一周被移除时返回 null 表示应删除整条 */
    private fun removeWeekFrom(course: Course, week: Int, resetWeekType: Boolean = false): Course? {
        val remaining = resolveSelectedWeeks(course).filter { it != week }
        if (remaining.isEmpty()) return null
        return course.copy(
            selectedWeeks = remaining,
            startWeek = remaining.min(),
            endWeek = remaining.max(),
            // 冲突路径调用方置 ALL；单独删某周保持原 weekType
            weekType = if (resetWeekType) Course.WEEK_TYPE_ALL else course.weekType,
            lastModified = System.currentTimeMillis()
        )
    }

    /** selectedWeeks 为空时按 start/end/weekType 推导 */
    private fun resolveSelectedWeeks(course: Course): List<Int> {
        if (course.selectedWeeks.isNotEmpty()) return course.selectedWeeks
        val weeks = mutableListOf<Int>()
        for (w in course.startWeek..course.endWeek) {
            when (course.weekType) {
                Course.WEEK_TYPE_ODD -> if (w % 2 == 1) weeks.add(w)
                Course.WEEK_TYPE_EVEN -> if (w % 2 == 0) weeks.add(w)
                else -> weeks.add(w)
            }
        }
        return weeks
    }

    /** 同源（名/教室/教师/位置相同）用于调课时合并周次而非新建重复课程 */
    private fun isSameCourseIdentity(a: Course, b: Course): Boolean {
        return a.name == b.name &&
            a.classroom == b.classroom &&
            a.teacher == b.teacher &&
            a.dayOfWeek == b.dayOfWeek &&
            a.startSection == b.startSection &&
            a.endSection == b.endSection
    }

    /**
     * 调课-移动：仅影响该周。单周直接改位置；多周拆分；目标已有同源则合并。
     * 通知由 ViewModel 统一处理，避免竞态。
     */
    fun moveCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int
    ): List<Course> {
        val courses = getAllCourses()
        val result = moveWeekInPlace(
            courses, sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection
        ) ?: return courses
        saveCourses(result, notify = false)
        return result
    }

    /**
     * 调课-覆盖：按周删除目标位冲突课后移动。同源课不删，交给 move 合并。
     */
    fun overwriteCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int
    ): List<Course> {
        val courses = getAllCourses().toMutableList()
        val source = courses.find { it.id == sourceCourseId } ?: return courses

        val targetTemp = source.copy(
            dayOfWeek = targetDayOfWeek,
            startSection = targetStartSection,
            endSection = targetEndSection
        )

        val conflictIds = courses.asSequence()
            .filter { existing ->
                existing.id != sourceCourseId &&
                !isSameCourseIdentity(existing, targetTemp) &&
                existing.dayOfWeek == targetDayOfWeek &&
                existing.startSection <= targetEndSection &&
                existing.endSection >= targetStartSection
            }
            .filter { existing -> week in resolveSelectedWeeks(existing) }
            .map { it.id }
            .toList()

        val result = courses.toMutableList()
        for (id in conflictIds) {
            val idx = result.indexOfFirst { it.id == id }
            if (idx == -1) continue
            val updated = removeWeekFrom(result[idx], week, resetWeekType = true)
            if (updated == null) {
                result.removeAt(idx)
            } else {
                result[idx] = updated
            }
        }
        // 先落盘中间结果，避免 moveCourseForWeek 重读旧数据
        saveCourses(result, notify = false)
        return moveCourseForWeek(sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection)
    }

    /** 调课-交换：双方各拆出该周实例互换；与对方原位同源时同样走合并 */
    fun swapCoursesForWeek(
        sourceCourseId: String,
        targetCourseId: String,
        week: Int
    ): List<Course> {
        if (sourceCourseId == targetCourseId) return getAllCourses()
        val courses = getAllCourses().toMutableList()
        val srcIdx = courses.indexOfFirst { it.id == sourceCourseId }
        val tgtIdx = courses.indexOfFirst { it.id == targetCourseId }
        if (srcIdx == -1 || tgtIdx == -1) return courses
        val src = courses[srcIdx]
        val tgt = courses[tgtIdx]

        val srcPos = Triple(src.dayOfWeek, src.startSection, src.endSection)
        val tgtPos = Triple(tgt.dayOfWeek, tgt.startSection, tgt.endSection)

        val srcWeeks = resolveSelectedWeeks(src)
        val tgtWeeks = resolveSelectedWeeks(tgt)
        if (week !in srcWeeks || week !in tgtWeeks) return courses

        var result: MutableList<Course> = courses
        moveWeekInPlace(result, src.id, week, tgtPos.first, tgtPos.second, tgtPos.third)?.let { result = it }
        // 第一步后源可能已拆分，src.id 仍在原课程（已移除该周）
        moveWeekInPlace(result, tgt.id, week, srcPos.first, srcPos.second, srcPos.third)?.let { result = it }

        saveCourses(result, notify = false)
        return result
    }

    /**
     * 按周移动的拆分+合并（原地、不落盘）。
     * @return 新列表；无需改动时返回 null，调用方跳过落盘
     */
    private fun moveWeekInPlace(
        courses: List<Course>,
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int
    ): MutableList<Course>? {
        val result = courses.toMutableList()
        val sourceIdx = result.indexOfFirst { it.id == sourceCourseId }
        if (sourceIdx == -1) return null
        val source = result[sourceIdx]

        if (source.dayOfWeek == targetDayOfWeek &&
            source.startSection == targetStartSection &&
            source.endSection == targetEndSection
        ) return null

        val currentSelectedWeeks = resolveSelectedWeeks(source)
        if (week !in currentSelectedWeeks) return null

        val targetTemp = source.copy(
            dayOfWeek = targetDayOfWeek,
            startSection = targetStartSection,
            endSection = targetEndSection
        )

        val mergeTargetIdx = result.indexOfFirst { existing ->
            existing.id != source.id && isSameCourseIdentity(existing, targetTemp)
        }

        if (mergeTargetIdx != -1) {
            // 合并周次进同源课程
            val mergeTarget = result[mergeTargetIdx]
            val mergeWeeks = resolveSelectedWeeks(mergeTarget).toMutableSet()
            mergeWeeks.add(week)
            val sortedWeeks = mergeWeeks.sorted()
            result[mergeTargetIdx] = mergeTarget.copy(
                selectedWeeks = sortedWeeks,
                startWeek = sortedWeeks.min(),
                endWeek = sortedWeeks.max(),
                weekType = Course.WEEK_TYPE_ALL,
                lastModified = System.currentTimeMillis()
            )
            val sourceWeeks = currentSelectedWeeks.filter { it != week }
            if (sourceWeeks.isEmpty()) {
                // 源课程所有周次已合并到同源课程，删除源课程
                result.removeAt(sourceIdx)
            } else {
                // 源课程还有其他周次，更新剩余周次
                result[sourceIdx] = source.copy(
                    selectedWeeks = sourceWeeks,
                    startWeek = sourceWeeks.min(),
                    endWeek = sourceWeeks.max(),
                    weekType = Course.WEEK_TYPE_ALL,
                    lastModified = System.currentTimeMillis()
                )
            }
        } else if (currentSelectedWeeks.size == 1 && currentSelectedWeeks.first() == week) {
            // 源课程只在该周有效，直接改位置
            result[sourceIdx] = source.copy(
                dayOfWeek = targetDayOfWeek,
                startSection = targetStartSection,
                endSection = targetEndSection,
                lastModified = System.currentTimeMillis()
            )
        } else {
            // 拆分
            val sourceWeeks = currentSelectedWeeks.filter { it != week }
            result[sourceIdx] = source.copy(
                selectedWeeks = sourceWeeks,
                startWeek = sourceWeeks.min(),
                endWeek = sourceWeeks.max(),
                weekType = Course.WEEK_TYPE_ALL,
                lastModified = System.currentTimeMillis()
            )
            val newCourse = source.copy(
                id = java.util.UUID.randomUUID().toString(),
                dayOfWeek = targetDayOfWeek,
                startSection = targetStartSection,
                endSection = targetEndSection,
                selectedWeeks = listOf(week),
                startWeek = week,
                endWeek = week,
                weekType = Course.WEEK_TYPE_ALL,
                lastModified = System.currentTimeMillis()
            )
            result.add(newCourse)
        }
        return result
    }

    fun getLastWeekWithCourses(): Int {
        val courses = getAllCourses()
        if (courses.isEmpty()) return 0
        var maxWeek = 0
        for (c in courses) {
            val end = if (c.selectedWeeks.isNotEmpty()) c.selectedWeeks.max() else c.endWeek
            if (end > maxWeek) maxWeek = end
        }
        return maxWeek
    }

    fun getCurrentWeek(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_CURRENT_WEEK"
        return safeGetInt(key, 1)
    }

    fun setCurrentWeek(week: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_CURRENT_WEEK"
        prefs.edit { putInt(key, week) }
        notifyCourseChanged("settings")
    }

    fun getTotalWeeks(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_TOTAL_WEEKS"
        return safeGetInt(key, 20)
    }

    fun setTotalWeeks(weeks: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_TOTAL_WEEKS"
        prefs.edit { putInt(key, weeks) }
        notifyCourseChanged("settings")
    }

    /** 旧值不是 YYYY/MM/DD 时自动回退当天并写回 */
    fun getClassStartTime(): String {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_START_TIME"
        val cal = java.util.Calendar.getInstance()
        val default = String.format(java.util.Locale.ROOT, "%04d/%02d/%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        val stored = prefs.getString(key, null)
        if (stored != null && Regex("^\\d{4}/\\d{2}/\\d{2}$").matches(stored)) {
            return stored
        }
        prefs.edit { putString(key, default) }
        return default
    }

    fun setClassStartTime(time: String) {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_START_TIME"
        prefs.edit { putString(key, time) }
        notifyCourseChanged("settings")
    }

    fun getSmartWeekend(): Boolean {
        val key = "${getScheduleKeyPrefix()}$KEY_SMART_WEEKEND"
        // 兼容旧 key：首次读取时迁移
        if (!prefs.contains(key)) {
            val oldKey = "${getScheduleKeyPrefix()}$KEY_SHOW_WEEKEND"
            val oldVal = prefs.getString(oldKey, "")
            val smart = !oldVal.isNullOrBlank()
            prefs.edit { putBoolean(key, smart); remove(oldKey) }
            return smart
        }
        return prefs.getBoolean(key, false)
    }

    fun setSmartWeekend(smart: Boolean) {
        val key = "${getScheduleKeyPrefix()}$KEY_SMART_WEEKEND"
        prefs.edit { putBoolean(key, smart) }
        notifyCourseChanged("settings")
    }

    fun getTodayShowWallpaper(): Boolean {
        val key = "${getScheduleKeyPrefix()}$KEY_TODAY_SHOW_WALLPAPER"
        return prefs.getBoolean(key, true)
    }

    fun setTodayShowWallpaper(show: Boolean) {
        val key = "${getScheduleKeyPrefix()}$KEY_TODAY_SHOW_WALLPAPER"
        prefs.edit { putBoolean(key, show) }
        notifyCourseChanged("settings")
    }

    /** 调休补班日也视为有课，智能周末据此决定是否显示该天 */
    fun hasCoursesOnDayInWeek(dayOfWeek: Int, week: Int): Boolean {
        if (getAllCourses().any { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week) }) return true
        return hasWorkSwapOnDay(dayOfWeek, week)
    }

    /** 待配置补班（followWeekday 未设置）不视为有课，避免智能周末误显示 */
    fun hasWorkSwapOnDay(dayOfWeek: Int, week: Int): Boolean {
        val start = runCatching {
            LocalDate.parse(getClassStartTime().replace("/", "-"))
        }.getOrNull() ?: return false
        val monday = start.minusDays((start.dayOfWeek.value - 1).toLong())
        val date = monday.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek - 1).toLong())
        val swap = HolidayManager.workSwap(appContext, date) ?: return false
        return swap.followWeekday in 1..7
    }

    fun getShowNonCurrentWeek(): Boolean {
        val key = "${getScheduleKeyPrefix()}$KEY_SHOW_NON_CURRENT_WEEK"
        return prefs.getBoolean(key, true)
    }

    fun setShowNonCurrentWeek(show: Boolean) {
        val key = "${getScheduleKeyPrefix()}$KEY_SHOW_NON_CURRENT_WEEK"
        prefs.edit { putBoolean(key, show) }
        notifyCourseChanged("settings")
    }

    fun getMorningSections(): Int = getMorningSections(getCurrentScheduleId())

    fun getMorningSections(scheduleId: String): Int {
        val configId = getScheduleTimeConfigId(scheduleId)
        return getTimeConfig(configId).morningSections
    }

    fun setMorningSections(count: Int) {
        val scheduleId = getCurrentScheduleId()
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        saveTimeConfig(config.copy(morningSections = count))
        notifyCourseChanged("settings")
    }

    fun getAfternoonSections(): Int = getAfternoonSections(getCurrentScheduleId())

    fun getAfternoonSections(scheduleId: String): Int {
        val configId = getScheduleTimeConfigId(scheduleId)
        return getTimeConfig(configId).afternoonSections
    }

    fun setAfternoonSections(count: Int) {
        val scheduleId = getCurrentScheduleId()
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        saveTimeConfig(config.copy(afternoonSections = count))
        notifyCourseChanged("settings")
    }

    fun getEveningSections(): Int = getEveningSections(getCurrentScheduleId())

    fun getEveningSections(scheduleId: String): Int {
        val configId = getScheduleTimeConfigId(scheduleId)
        return getTimeConfig(configId).eveningSections
    }

    fun setEveningSections(count: Int) {
        val scheduleId = getCurrentScheduleId()
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        saveTimeConfig(config.copy(eveningSections = count))
        notifyCourseChanged("settings")
    }

    /**
     * period: "morning" / "afternoon" / "evening"
     * 返回相对节次号 (1-6) -> "HH:mm-HH:mm"
     */
    fun getPeriodTimes(period: String): Map<Int, String> {
        return getPeriodTimes(period, getCurrentScheduleId())
    }

    fun getPeriodTimes(period: String, scheduleId: String): Map<Int, String> {
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        return config.getPeriodTimes(period)
    }

    fun savePeriodTimes(period: String, times: Map<Int, String>) {
        savePeriodTimes(period, times, getCurrentScheduleId())
    }

    /** 仅目标是当前课表时才通知，避免导入目标课表时无谓刷新当前页 */
    fun savePeriodTimes(period: String, times: Map<Int, String>, scheduleId: String) {
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        val existing = config.sectionTimes.toMutableMap()
        existing.keys.filter { it.startsWith("${period}_") }.forEach { existing.remove(it) }
        for ((idx, v) in times) {
            existing["${period}_$idx"] = v
        }
        saveTimeConfig(config.copy(sectionTimes = existing, quickTimeEnabled = false))
        if (scheduleId == getCurrentScheduleId()) notifyCourseChanged("settings")
    }

    // 旧版影子 prefs：唯一数据源已是 TimeConfig；这些 getter 仅供版本迁移与 export 兼容。
    // 日常读写走 TimeConfig，不要在这里新增逻辑。
    fun getQuickTimeEnabled(): Boolean {
        val key = "${getScheduleKeyPrefix()}$KEY_QUICK_TIME_ENABLED"
        return prefs.getBoolean(key, false)
    }

    fun getClassDuration(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_DURATION"
        return safeGetInt(key, 45)
    }

    fun getShortBreak(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_SHORT_BREAK"
        return safeGetInt(key, 10)
    }

    fun getLongBreakEnabled(): Boolean {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_enabled"
        return prefs.getBoolean(key, false)
    }

    fun getLongBreakMorning(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_morning"
        return safeGetInt(key, 20)
    }

    fun getLongBreakAfternoon(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_afternoon"
        return safeGetInt(key, 20)
    }

    fun getLongBreakEvening(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_evening"
        return safeGetInt(key, 20)
    }

    fun getLongBreakMorningSection(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_morning_section"
        return safeGetInt(key, 2)
    }

    fun getLongBreakAfternoonSection(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_afternoon_section"
        return safeGetInt(key, 2)
    }

    fun getLongBreakEveningSection(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_evening_section"
        return safeGetInt(key, 2)
    }

    fun getMorningStartHour(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_MORNING_START"
        return safeGetInt(key, 8)
    }

    fun getMorningStartMinute(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_MORNING_START}_min"
        return safeGetInt(key, 0)
    }

    fun getAfternoonStartHour(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_AFTERNOON_START"
        return safeGetInt(key, 14)
    }

    fun getAfternoonStartMinute(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_AFTERNOON_START}_min"
        return safeGetInt(key, 0)
    }

    fun getEveningStartHour(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_EVENING_START"
        return safeGetInt(key, 18)
    }

    fun getEveningStartMinute(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_EVENING_START}_min"
        return safeGetInt(key, 30)
    }

    fun getPreClassReminder(): Boolean {
        return prefs.getBoolean(KEY_PRE_CLASS_REMINDER, false)
    }

    fun setPreClassReminder(enabled: Boolean) {
        prefs.edit {putBoolean(KEY_PRE_CLASS_REMINDER, enabled) }
    }

    fun getPreClassReminderMinutes(): Int {
        return safeGetInt(KEY_PRE_CLASS_REMINDER_MINUTES, 20)
    }

    fun setPreClassReminderMinutes(minutes: Int) {
        prefs.edit {putInt(KEY_PRE_CLASS_REMINDER_MINUTES, minutes) }
    }

    fun getNextDayReminder(): Boolean {
        return prefs.getBoolean(KEY_NEXT_DAY_REMINDER, false)
    }

    fun setNextDayReminder(enabled: Boolean) {
        prefs.edit {putBoolean(KEY_NEXT_DAY_REMINDER, enabled) }
    }

    fun getNextDayReminderHour(): Int {
        return safeGetInt(KEY_NEXT_DAY_REMINDER_HOUR, 21)
    }

    fun setNextDayReminderHour(hour: Int) {
        prefs.edit { putInt(KEY_NEXT_DAY_REMINDER_HOUR, hour) }
    }

    fun getNextDayReminderMinute(): Int {
        return safeGetInt(KEY_NEXT_DAY_REMINDER_MINUTE, 0)
    }

    fun setNextDayReminderMinute(minute: Int) {
        prefs.edit { putInt(KEY_NEXT_DAY_REMINDER_MINUTE, minute) }
    }

    /** 获取今日课程/课程提醒标准版小组件的 padding 档位（0=标准, 1=4×6, 2=4×7） */
    fun getWidgetPaddingMode(): Int {
        return safeGetInt(KEY_WIDGET_PADDING_MODE, 1)
    }

    /** 设置今日课程/课程提醒标准版小组件的 padding 档位（0=标准, 1=4×6, 2=4×7） */
    fun setWidgetPaddingMode(mode: Int) {
        prefs.edit { putInt(KEY_WIDGET_PADDING_MODE, mode) }
    }

    fun getIslandNotification(): Boolean {
        return prefs.getBoolean(KEY_ISLAND_NOTIFICATION, false)
    }

    fun setIslandNotification(enabled: Boolean) {
        prefs.edit {putBoolean(KEY_ISLAND_NOTIFICATION, enabled) }
    }

    /** 「上课自动开启勿扰」总开关；需已授予勿扰权限 */
    fun getClassDndEnabled(): Boolean {
        return prefs.getBoolean(KEY_CLASS_DND, false)
    }

    fun setClassDndEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CLASS_DND, enabled) }
    }

    /** 0=勿扰 1=静音(闹钟仍响) 2=优先；默认 1 不影响查看通知 */
    fun getClassDndMode(): Int {
        return safeGetInt(KEY_CLASS_DND_MODE, 1)
    }

    fun setClassDndMode(mode: Int) {
        prefs.edit { putInt(KEY_CLASS_DND_MODE, mode) }
    }

    /**
     * 占用判断为分钟级时间重叠；自定义时间课按实际起止参与，时间无法确定时回退节次重叠。
     *
     * @param excludeIds 编辑时排除自身
     * @param startTime/endTime 自定义时间课程传入 "HH:mm"
     */
    fun getOccupiedWeeks(
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        excludeIds: Set<String> = emptySet(),
        startTime: String? = null,
        endTime: String? = null
    ): Set<Int> {
        // 无排除条件且非自定义时间时使用缓存
        if (excludeIds.isEmpty() && startTime == null && endTime == null) {
            occupiedWeeksCache[occupiedWeeksKey(dayOfWeek, startSection, endSection)]?.let { return it }
        }

        val sectionTimes = getGlobalSectionTimes()
        val newStartMin = if (startTime != null) {
            timeToMinutes(startTime)
        } else {
            timeToMinutes(sectionTimes[startSection]?.substringBefore("-")?.trim())
        }
        val newEndMin = if (endTime != null) {
            timeToMinutes(endTime)
        } else {
            timeToMinutes(sectionTimes[endSection]?.substringAfter("-")?.trim())
        }

        val occupied = mutableSetOf<Int>()
        getAllCourses().forEach { course ->
            if (course.id in excludeIds) return@forEach
            if (course.dayOfWeek == dayOfWeek &&
                isTimeConflict(newStartMin, newEndMin, startSection, endSection, course, sectionTimes)
            ) {
                addCourseWeeks(occupied, course)
            }
        }

        if (excludeIds.isEmpty() && startTime == null && endTime == null) {
            occupiedWeeksCache[occupiedWeeksKey(dayOfWeek, startSection, endSection)] = occupied
        }

        return occupied
    }

    /** 必须带课表 ID，否则切换课表后会命中另一课表的缓存 */
    private fun occupiedWeeksKey(dayOfWeek: Int, startSection: Int, endSection: Int): String =
        "${getCurrentScheduleId()}_${dayOfWeek}_${startSection}_${endSection}"

    private fun timeToMinutes(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    /** 上午原编号，下午/晚上按节数偏移后的全局绝对节次映射 */
    private fun getGlobalSectionTimes(): Map<Int, String> {
        globalSectionTimesCache?.let { return it }
        val morning = getPeriodTimes("morning")
        val afternoon = getPeriodTimes("afternoon")
        val evening = getPeriodTimes("evening")
        val morningSections = getMorningSections()
        val afternoonSections = getAfternoonSections()
        val times = buildMap {
            morning.forEach { (k, v) -> put(k, v) }
            afternoon.forEach { (k, v) -> put(morningSections + k, v) }
            evening.forEach { (k, v) -> put(morningSections + afternoonSections + k, v) }
        }
        globalSectionTimesCache = times
        return times
    }

    private fun addCourseWeeks(occupied: MutableSet<Int>, course: Course) {
        if (course.selectedWeeks.isNotEmpty()) {
            occupied.addAll(course.selectedWeeks)
        } else {
            for (week in course.startWeek..course.endWeek) {
                when (course.weekType) {
                    Course.WEEK_TYPE_ODD -> if (week % 2 == 1) occupied.add(week)
                    Course.WEEK_TYPE_EVEN -> if (week % 2 == 0) occupied.add(week)
                    else -> occupied.add(week)
                }
            }
        }
    }

    /**
     * 分钟级重叠判断；相邻（end==start）不算冲突。时间无法确定时回退节次重叠。
     *
     * @param sectionTimes 全局绝对节次号 -> "HH:mm-HH:mm"
     */
    private fun isTimeConflict(
        newStartMin: Int?,
        newEndMin: Int?,
        newStartSection: Int,
        newEndSection: Int,
        existing: Course,
        sectionTimes: Map<Int, String>
    ): Boolean {
        val existingStart = timeToMinutes(existing.getEffectiveStartTime(sectionTimes))
        val existingEnd = timeToMinutes(existing.getEffectiveEndTime(sectionTimes))
        if (newStartMin != null && newEndMin != null && existingStart != null && existingEnd != null) {
            return newStartMin < existingEnd && existingStart < newEndMin
        }
        return existing.startSection <= newEndSection && existing.endSection >= newStartSection
    }

    @Suppress("UNUSED_PARAMETER") // week: 槽位共享所有周次，同槽冲突课程无论周次均需展示
    fun getCoursesAtSlot(
        week: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): List<Course> {
        return getAllCourses().filter { course ->
            course.dayOfWeek == dayOfWeek &&
            course.startSection <= endSection &&
            course.endSection >= startSection
        }.sortedBy { it.startSection }
    }

    fun getScheduleNames(): List<String> {
        scheduleNamesCache?.let { return it }
        val json = prefs.getString(KEY_SCHEDULE_NAMES, null)
        val names = try {
            if (json.isNullOrBlank()) listOf("默认课表")
            else {
                val parsed: List<String>? = gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
                parsed?.takeIf { it.isNotEmpty() } ?: listOf("默认课表")
            }
        } catch (_: Exception) {
            listOf("默认课表")
        }
        scheduleNamesCache = names
        return names
    }

    internal fun saveScheduleNames(names: List<String>) {
        val json = gson.toJson(names)
        prefs.edit(commit = true) { putString(KEY_SCHEDULE_NAMES, json) }
        // 课表列表变化会让"当前课表 ID 是否仍有效"的结论失效
        scheduleNamesCache = names
        currentScheduleIdCache = null
    }

    fun getCurrentScheduleId(): String {
        currentScheduleIdCache?.let { return it }
        val saved = prefs.getString(KEY_CURRENT_SCHEDULE_ID, "默认课表") ?: "默认课表"
        // 不在列表中则回退第一个
        val names = getScheduleNames()
        val resolved = if (saved in names) saved else names.first()
        currentScheduleIdCache = resolved
        return resolved
    }

    fun setCurrentScheduleId(scheduleId: String) {
        prefs.edit { putString(KEY_CURRENT_SCHEDULE_ID, scheduleId) }
        currentScheduleIdCache = scheduleId
        // 占用周次与全局节次时间都基于当前课表
        invalidateTimeCaches()
        notifyCourseChanged("settings")
    }

    fun addSchedule(name: String): List<String> {
        val names = getScheduleNames().toMutableList()
        if (name !in names) {
            names.add(name)
            saveScheduleNames(names)
            // 必须绑独立时间配置，否则会回退共享第一个，改一个课表会波及其他课表
            createDefaultTimeConfigForSchedule(name)
        }
        notifyCourseChanged("settings")
        return names
    }

    /** 为课表新建默认 4/4/4 专属时间配置并绑定，避免多课表共享 */
    fun createDefaultTimeConfigForSchedule(name: String) {
        // 不能用 getScheduleTimeConfigId 判断已绑定：它对未绑定会回退到第一个配置 id
        val boundKey = "$SCHEDULE_TIME_CONFIG_PREFIX$name"
        if (prefs.contains(boundKey)) return
        val newId = addTimeConfig(
            TimeConfig(
                name = name,
                morningSections = 4,
                afternoonSections = 4,
                eveningSections = 4
            )
        )
        setScheduleTimeConfigId(name, newId)
    }

    /** 复制当前课表设置（不含课程）；开课日重置为今天、当前周为第 1 周 */
    fun createNewSemesterSchedule(name: String): List<String> {
        val currentId = getCurrentScheduleId()
        val names = getScheduleNames().toMutableList()
        if (name !in names) {
            names.add(0, name)
            saveScheduleNames(names)
        }
        val currentPrefix = "$SCHEDULE_KEY_PREFIX${currentId}_"
        val newPrefix = "$SCHEDULE_KEY_PREFIX${name}_"
        prefs.edit(commit = true) {
            for ((key, value) in prefs.all) {
                if (key.startsWith(currentPrefix)) {
                    val settingName = key.removePrefix(currentPrefix)
                    if (settingName == KEY_COURSES) continue
                    val newKey = "$newPrefix$settingName"
                    when (value) {
                        is Int -> putInt(newKey, value)
                        is Boolean -> putBoolean(newKey, value)
                        is String -> putString(newKey, value)
                        is Float -> putFloat(newKey, value)
                        is Long -> putLong(newKey, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            putStringSet(newKey, value as Set<String>)
                        }
                    }
                }
            }
            val currentTimeConfigId = prefs.getLong("$SCHEDULE_TIME_CONFIG_PREFIX$currentId", 0L)
            if (currentTimeConfigId != 0L) {
                putLong("$SCHEDULE_TIME_CONFIG_PREFIX$name", currentTimeConfigId)
            }
            val today = LocalDate.now()
            val todayStr =
                String.format(java.util.Locale.ROOT, "%04d/%02d/%02d", today.year, today.monthValue, today.dayOfMonth)
            putString("$newPrefix$KEY_CLASS_START_TIME", todayStr)
            putInt("$newPrefix$KEY_CURRENT_WEEK", 1)
        }
        return names
    }

    fun deleteSchedule(name: String): List<String> {
        val names = getScheduleNames().toMutableList()
        names.remove(name)
        // 删光后自动补默认课表，避免应用无法启动
        if (names.isEmpty()) {
            names.add("默认课表")
        }
        saveScheduleNames(names)
        val prefix = "$SCHEDULE_KEY_PREFIX${name}_"
        prefs.edit {
            for (key in prefs.all.keys) {
                if (key.startsWith(prefix)) {
                    remove(key)
                }
            }
            // 绑定键不匹配 schedule_{name}_ 前缀，必须单独删；
            // 否则同名课表再导入会撞上残留绑定
            remove("$SCHEDULE_TIME_CONFIG_PREFIX$name")
        }
        // 直接改写了 prefs，必须失效否则同名重建会读到旧数据
        invalidateAllCaches()
        if (getCurrentScheduleId() == name) {
            setCurrentScheduleId(names.first())
        }
        notifyCourseChanged("settings")
        return names
    }

    fun renameSchedule(oldName: String, newName: String): List<String> {
        val names = getScheduleNames().toMutableList()
        val index = names.indexOf(oldName)
        if (index != -1) {
            // 先更新当前课表 ID，再改名称列表
            val savedCurrentId = prefs.getString(KEY_CURRENT_SCHEDULE_ID, "默认课表") ?: "默认课表"
            if (savedCurrentId == oldName) {
                setCurrentScheduleId(newName)
            }
            names[index] = newName
            saveScheduleNames(names)
            // 迁移 schedule_{old}_* → schedule_{new}_*
            val oldPrefix = "$SCHEDULE_KEY_PREFIX${oldName}_"
            val newPrefix = "$SCHEDULE_KEY_PREFIX${newName}_"
            prefs.edit(commit = true) {
                for ((key, value) in prefs.all) {
                    if (key.startsWith(oldPrefix)) {
                        val suffix = key.removePrefix(oldPrefix)
                        val newKey = "$newPrefix$suffix"
                        when (value) {
                            is Int -> putInt(newKey, value)
                            is Boolean -> putBoolean(newKey, value)
                            is String -> {
                                if (suffix == KEY_COURSES) {
                                    // 坏 JSON 不能 sanitize 后写回，否则变成空壳课并永久盖掉原数据
                                    if (!coursesJsonLooksValid(value)) {
                                        putString(newKey, value)
                                    } else {
                                        val type = object : TypeToken<List<Course>>() {}.type
                                        try {
                                            // 旧 JSON 字段可能为 null，直接 copy() 会 NPE
                                            val courses =
                                                sanitizeCourses(gson.fromJson(value, type) ?: emptyList())
                                            val updated = courses.map { it.copy(scheduleId = newName) }
                                            putString(newKey, gson.toJson(updated))
                                        } catch (_: Exception) {
                                            putString(newKey, value)
                                        }
                                    }
                                } else {
                                    putString(newKey, value)
                                }
                            }
                            is Float -> putFloat(newKey, value)
                            is Long -> putLong(newKey, value)
                            is Set<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                putStringSet(newKey, value as Set<String>)
                            }
                        }
                        remove(key)
                    }
                }
            }
            // 绑定键不匹配上面前缀，循环迁不到，必须单独搬
            val oldBoundKey = "$SCHEDULE_TIME_CONFIG_PREFIX$oldName"
            if (prefs.contains(oldBoundKey)) {
                val boundId = prefs.getLong(oldBoundKey, 0L)
                prefs.edit(commit = true) {
                    putLong("$SCHEDULE_TIME_CONFIG_PREFIX$newName", boundId)
                    remove(oldBoundKey)
                }
            }
            invalidateAllCaches()
        }
        notifyCourseChanged("settings")
        return names
    }

    /** 绑定无效时回退第一个可用配置 */
    fun getScheduleTimeConfigId(scheduleId: String): Long {
        val id = prefs.getLong("$SCHEDULE_TIME_CONFIG_PREFIX$scheduleId", 0L)
        if (id != 0L && id in getTimeConfigIds()) {
            return id
        }
        val firstId = getTimeConfigIds().firstOrNull()
        return firstId ?: 0L
    }

    fun setScheduleTimeConfigId(scheduleId: String, timeConfigId: Long) {
        prefs.edit { putLong("$SCHEDULE_TIME_CONFIG_PREFIX$scheduleId", timeConfigId) }
        invalidateTimeCaches()
    }

    fun switchToSchedule(scheduleId: String) {
        setCurrentScheduleId(scheduleId)
        val timeConfigId = getScheduleTimeConfigId(scheduleId)
        if (timeConfigId != 0L) {
            val config = getTimeConfig(timeConfigId)
            setCurrentTimeConfigId(timeConfigId)
            applyTimeConfigToSchedule(config)
        } else if (getTimeConfigIds().isNotEmpty()) {
            // 未绑定则用第一个配置并补绑
            val firstConfigId = getTimeConfigIds().first()
            val config = getTimeConfig(firstConfigId)
            setCurrentTimeConfigId(firstConfigId)
            setScheduleTimeConfigId(scheduleId, firstConfigId)
            applyTimeConfigToSchedule(config)
        }
        notifyCourseChanged("settings")
    }

    fun isShiftModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHIFT_MODE, false)
    }

    fun setShiftModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHIFT_MODE, enabled) }
    }

    fun getShiftSelectedSchedules(): List<String> {
        val json = prefs.getString(KEY_SHIFT_SELECTED_SCHEDULES, null)
        return try {
            if (json.isNullOrBlank()) emptyList()
            else gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setShiftSelectedSchedules(names: List<String>) {
        val json = gson.toJson(names)
        prefs.edit {putString(KEY_SHIFT_SELECTED_SCHEDULES, json) }
    }

    fun getDefaultHomepage(): String {
        return prefs.getString(KEY_DEFAULT_HOMEPAGE, "课程表") ?: "课程表"
    }

    fun setDefaultHomepage(homepage: String) {
        prefs.edit {putString(KEY_DEFAULT_HOMEPAGE, homepage) }
    }

    fun getCombinationIds(): List<Long> {
        val idsStr = prefs.getString(KEY_COMBINATION_IDS, null) ?: return listOf(0L)
        return idsStr.split(",").mapNotNull { it.toLongOrNull() }
    }

    fun getCurrentCombinationId(): Long {
        return prefs.getLong(KEY_CURRENT_COMBINATION_ID, 0L)
    }

    fun setCurrentCombinationId(id: Long) {
        prefs.edit { putLong(KEY_CURRENT_COMBINATION_ID, id) }
    }

    /** 批量模式复用的 Editor；保存一次搭配约 16 个 save*，合并为一次磁盘提交 */
    private var pendingEditor: SharedPreferences.Editor? = null

    /** 所有写入统一入口：批量复用 Editor，否则独立提交 */
    private fun edit(commit: Boolean = false, block: SharedPreferences.Editor.() -> Unit) {
        val pending = pendingEditor
        if (pending != null) {
            block(pending)
        } else {
            prefs.edit(commit = commit) { block(this) }
        }
    }

    /** 块内 prefs 写入合并为一次提交；可嵌套 */
    fun batchEdit(block: () -> Unit) {
        val outer = pendingEditor
        if (outer != null) {
            block()
            return
        }
        val editor = prefs.edit()
        pendingEditor = editor
        try {
            block()
        } finally {
            pendingEditor = null
        }
        editor.apply()
    }

    /**
     * 无 JSON 时从旧分散键迁移；有 JSON 但不可识别则丢弃并恢复默认（自愈一次），
     * 否则 cardHeight=0 会课表页静默空白。
     */
    private fun getCombinationStyle(id: Long): CombinationStyle {
        combinationStyleCache[id]?.let { return it }
        val json = prefs.getString("$COMBINATION_STYLE_PREFIX$id", null)
        val style = if (json == null) {
            readLegacyCombinationStyle(id).also { saveCombinationStyle(id, it) }
        } else {
            CombinationStyle.parseSnapshotOrNull(gson, json)
                ?: restoreStyleAfterBadSnapshot(id)
        }
        combinationStyleCache[id] = style
        return style
    }

    /** 坏快照重置后用现有壁纸重测光，否则主题开关因 isLight=null 整条失效 */
    private fun restoreStyleAfterBadSnapshot(id: Long): CombinationStyle {
        val isLight = computeWallpaperIsLight(loadCombinationWallpaper(id))
        val style = CombinationStyle(wallpaperIsLight = isLight)
        saveCombinationStyle(id, style)
        return style
    }

    /** 16×16 网格感知加权平均亮度；无壁纸返回 null */
    private fun computeWallpaperIsLight(bitmap: android.graphics.Bitmap?): Boolean? {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
        val gridW = 16
        val gridH = 16
        val small = bitmap.scale(gridW, gridH)
        var sum = 0L
        for (x in 0 until gridW) {
            for (y in 0 until gridH) {
                val c = small.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                sum += (299 * r + 587 * g + 114 * b) / 1000
            }
        }
        val avg = sum / (gridW * gridH)
        small.recycle()
        return avg >= 128
    }

    private fun saveCombinationStyle(id: Long, style: CombinationStyle) {
        edit { putString("$COMBINATION_STYLE_PREFIX$id", gson.toJson(style)) }
        combinationStyleCache[id] = style
    }

    /** 在现有快照基础上做一次变更并落盘 */
    private fun updateCombinationStyle(id: Long, transform: (CombinationStyle) -> CombinationStyle) {
        saveCombinationStyle(id, transform(getCombinationStyle(id)))
    }

    /** 旧分散键迁移专用；默认值须与迁移前 getter 一致 */
    private fun readLegacyCombinationStyle(id: Long): CombinationStyle {
        val isLightKey = "${KEY_COMBINATION_WALLPAPER_IS_LIGHT_PREFIX}$id"
        return CombinationStyle(
            offsetX = prefs.getFloat("${KEY_COMBINATION_OFFSET_X_PREFIX}$id", 0f),
            offsetY = prefs.getFloat("${KEY_COMBINATION_OFFSET_Y_PREFIX}$id", 0f),
            scale = prefs.getFloat("${KEY_COMBINATION_SCALE_PREFIX}$id", 1f),
            cardBlur = prefs.getFloat("${KEY_COMBINATION_CARD_BLUR_PREFIX}$id", 0f),
            cardAlpha = prefs.getFloat("${KEY_COMBINATION_CARD_ALPHA_PREFIX}$id", 0.15f),
            cardHeight = prefs.getFloat("${KEY_COMBINATION_CARD_HEIGHT_PREFIX}$id", 54f),
            cardCornerRadius = prefs.getFloat("${KEY_COMBINATION_CARD_CORNER_PREFIX}$id", 8f),
            wallpaperBrightness = prefs.getFloat("${KEY_COMBINATION_WALLPAPER_BRIGHTNESS_PREFIX}$id", 0f),
            wallpaperIsLight = if (prefs.contains(isLightKey)) prefs.getBoolean(isLightKey, false) else null,
            showBreakDividers = prefs.getBoolean("${KEY_COMBINATION_SHOW_BREAK_DIVIDERS_PREFIX}$id", true),
            cardContentAlignment = CardContentAlignment.fromOrdinal(
                prefs.getInt(
                    "${KEY_COMBINATION_CARD_CONTENT_ALIGNMENT_PREFIX}$id",
                    CardContentAlignment.CENTER_CENTER.ordinal
                )
            ),
            cardTextColor = CardTextColor.fromOrdinal(
                prefs.getInt("${KEY_COMBINATION_CARD_TEXT_COLOR_PREFIX}$id", CardTextColor.COLORFUL.ordinal)
            ),
            cardTextScale = prefs.getFloat("${KEY_COMBINATION_CARD_TEXT_SCALE_PREFIX}$id", 1f),
            showClassroom = prefs.getBoolean("${KEY_COMBINATION_SHOW_CLASSROOM_PREFIX}$id", true),
            showTeacher = prefs.getBoolean("${KEY_COMBINATION_SHOW_TEACHER_PREFIX}$id", true),
            cardRefraction = CardRefractionLevel.fromOrdinal(
                prefs.getInt(
                    "${KEY_COMBINATION_CARD_REFRACTION_PREFIX}$id",
                    CardRefractionLevel.DEFAULT.ordinal
                )
            ),
            wallpaperBlur = prefs.getBoolean("${KEY_COMBINATION_WALLPAPER_BLUR_PREFIX}$id", false)
        )
    }

    /** 壁纸的目标存储/解码分辨率（屏幕分辨率），用于平衡显示质量与内存占用 */
    private fun wallpaperTargetBounds(): Pair<Int, Int> {
        val metrics = appContext.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /** 计算满足目标尺寸的 2 的幂次降采样倍数（inSampleSize） */
    private fun calculateInSampleSize(bounds: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (bounds.outHeight > reqHeight || bounds.outWidth > reqWidth) {
            val halfHeight = bounds.outHeight / 2
            val halfWidth = bounds.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /** 缩放到屏幕分辨率后以 WebP 有损 80 存储 */
    fun saveCombinationWallpaper(id: Long, bitmap: android.graphics.Bitmap): Boolean {
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            val scaled = if (bitmap.width > targetW || bitmap.height > targetH) {
                val scale = minOf(targetW / bitmap.width.toFloat(), targetH / bitmap.height.toFloat())
                bitmap.scale(
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap
            val file = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.webp")
            java.io.FileOutputStream(file).use { out ->
                scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, out)
            }
            wallpaperCache.put(id, scaled)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 删磁盘文件并清缓存，配合内存 bitmap=null 实现持久化清除 */
    fun clearCombinationWallpaper(id: Long) {
        wallpaperCache.remove(id)
        val webpFile = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.webp")
        if (webpFile.exists()) webpFile.delete()
        val pngFile = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.png")
        if (pngFile.exists()) pngFile.delete()
    }

    /** 带 Lru 缓存；解码按屏幕分辨率降采样，优先 webp 兼容旧 png */
    fun loadCombinationWallpaper(id: Long): android.graphics.Bitmap? {
        wallpaperCache.get(id)?.let { return it }
        val webpFile = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.webp")
        val file = if (webpFile.exists()) webpFile
        else java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.png")
        if (!file.exists()) return null
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            // 先读尺寸再降采样，避免超大图一次性解码耗尽内存
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            opts.inSampleSize = calculateInSampleSize(opts, targetW, targetH)
            opts.inJustDecodeBounds = false
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)?.also { bmp ->
                wallpaperCache.put(id, bmp)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveCombinationState(id: Long, offsetX: Float, offsetY: Float, scale: Float) =
        updateCombinationStyle(id) { it.copy(offsetX = offsetX, offsetY = offsetY, scale = scale) }

    fun getCombinationOffsetX(id: Long): Float = getCombinationStyle(id).offsetX
    fun getCombinationOffsetY(id: Long): Float = getCombinationStyle(id).offsetY
    fun getCombinationScale(id: Long): Float = getCombinationStyle(id).scale

    fun saveCombinationCardBlur(id: Long, blurRadius: Float) =
        updateCombinationStyle(id) { it.copy(cardBlur = blurRadius) }

    fun getCombinationCardBlur(id: Long): Float = getCombinationStyle(id).cardBlur

    fun saveCombinationCardAlpha(id: Long, alpha: Float) =
        updateCombinationStyle(id) { it.copy(cardAlpha = alpha) }

    fun getCombinationCardAlpha(id: Long): Float = getCombinationStyle(id).cardAlpha

    fun saveCombinationCardHeight(id: Long, height: Float) =
        updateCombinationStyle(id) { it.copy(cardHeight = height) }

    fun getCombinationCardHeight(id: Long): Float = getCombinationStyle(id).safeCardHeight

    fun saveCombinationCardCornerRadius(id: Long, cornerRadius: Float) =
        updateCombinationStyle(id) { it.copy(cardCornerRadius = cornerRadius) }

    fun getCombinationCardCornerRadius(id: Long): Float = getCombinationStyle(id).cardCornerRadius

    fun saveCombinationWallpaperBrightness(id: Long, brightness: Float) =
        updateCombinationStyle(id) { it.copy(wallpaperBrightness = brightness) }

    fun getCombinationWallpaperBrightness(id: Long): Float = getCombinationStyle(id).wallpaperBrightness

    fun saveCombinationWallpaperIsLight(id: Long, isLight: Boolean?) =
        updateCombinationStyle(id) { it.copy(wallpaperIsLight = isLight) }

    fun getCombinationWallpaperIsLight(id: Long): Boolean? {
        getCombinationStyle(id).wallpaperIsLight?.let { return it }
        // 有壁纸但测光结果丢失时兜底重算（坏快照重置/升级后主题开关依赖此值）
        val isLight = computeWallpaperIsLight(loadCombinationWallpaper(id)) ?: return null
        updateCombinationStyle(id) { it.copy(wallpaperIsLight = isLight) }
        return isLight
    }

    fun saveCombinationShowBreakDividers(id: Long, show: Boolean) =
        updateCombinationStyle(id) { it.copy(showBreakDividers = show) }

    fun getCombinationShowBreakDividers(id: Long): Boolean = getCombinationStyle(id).showBreakDividers

    fun saveCombinationCardContentAlignment(id: Long, alignment: CardContentAlignment) =
        updateCombinationStyle(id) { it.copy(cardContentAlignment = alignment) }

    fun getCombinationCardContentAlignment(id: Long): CardContentAlignment = getCombinationStyle(id).safeAlignment

    fun saveCombinationCardTextColor(id: Long, color: CardTextColor) =
        updateCombinationStyle(id) { it.copy(cardTextColor = color) }

    fun getCombinationCardTextColor(id: Long): CardTextColor = getCombinationStyle(id).safeTextColor

    fun saveCombinationCardTextScale(id: Long, scale: Float) =
        updateCombinationStyle(id) { it.copy(cardTextScale = scale) }

    // 必须走 safeCardTextScale：旧快照缺该字段时 Gson 会留 0，文字会整体消失
    fun getCombinationCardTextScale(id: Long): Float = getCombinationStyle(id).safeCardTextScale

    fun saveCombinationShowClassroom(id: Long, show: Boolean) =
        updateCombinationStyle(id) { it.copy(showClassroom = show) }

    fun getCombinationShowClassroom(id: Long): Boolean = getCombinationStyle(id).showClassroom

    fun saveCombinationShowTeacher(id: Long, show: Boolean) =
        updateCombinationStyle(id) { it.copy(showTeacher = show) }

    fun getCombinationShowTeacher(id: Long): Boolean = getCombinationStyle(id).showTeacher

    fun saveCombinationCardRefraction(id: Long, level: CardRefractionLevel) =
        updateCombinationStyle(id) { it.copy(cardRefraction = level) }

    fun getCombinationCardRefraction(id: Long): CardRefractionLevel = getCombinationStyle(id).safeRefraction

    fun saveCombinationWallpaperBlur(id: Long, blur: Boolean) =
        updateCombinationStyle(id) { it.copy(wallpaperBlur = blur) }

    fun getCombinationWallpaperBlur(id: Long): Boolean = getCombinationStyle(id).wallpaperBlur

    /** 迁移：如果只有旧的单搭配数据（无 combination_ids），将其作为 id=0 的搭配 */
    fun migrateToCombinationsIfNeeded() {
        if (prefs.contains(KEY_COMBINATION_IDS)) return
        // 首次迁移：将现有单搭配数据作为 id=0
        prefs.edit {
                putString(KEY_COMBINATION_IDS, "0")
                    .putLong(KEY_CURRENT_COMBINATION_ID, 0L)
                    .putFloat("${KEY_COMBINATION_OFFSET_X_PREFIX}0", prefs.getFloat(KEY_WALLPAPER_OFFSET_X, 0f))
                    .putFloat("${KEY_COMBINATION_OFFSET_Y_PREFIX}0", prefs.getFloat(KEY_WALLPAPER_OFFSET_Y, 0f))
                    .putFloat("${KEY_COMBINATION_SCALE_PREFIX}0", prefs.getFloat(KEY_WALLPAPER_SCALE, 1f))
        }
        // 复制壁纸文件
        val oldFile = java.io.File(appContext.filesDir, WALLPAPER_FILE_NAME)
        if (oldFile.exists()) {
            val newFile = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}0.png")
            try { oldFile.copyTo(newFile, overwrite = true) } catch (_: Exception) {}
        }
    }

    // --- 多时间配置支持 ---

    /** 获取所有时间配置 ID 列表（按创建顺序） */
    fun getTimeConfigIds(): List<Long> {
        timeConfigIdsCache?.let { return it }
        val idsStr = prefs.getString(KEY_TIME_CONFIG_IDS, null)
        val ids = if (idsStr == null) {
            listOf(0L)
        } else {
            // 兼容两种格式：逗号分隔 "1,2,3" 和 JSON 数组 "[1,2,3]"
            val cleaned = idsStr.trim()
            if (cleaned.startsWith("[")) {
                try {
                    val type = object : TypeToken<List<Long>>() {}.type
                    gson.fromJson<List<Long>>(cleaned, type) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                cleaned.split(",").mapNotNull { it.toLongOrNull() }
            }
        }
        timeConfigIdsCache = ids
        return ids
    }

    /** 持久化时间配置 ID 列表并同步缓存 */
    private fun saveTimeConfigIds(ids: List<Long>) {
        prefs.edit { putString(KEY_TIME_CONFIG_IDS, ids.joinToString(",")) }
        timeConfigIdsCache = ids
    }

    fun getCurrentTimeConfigId(): Long {
        val scheduleId = getCurrentScheduleId()
        val boundKey = "$SCHEDULE_TIME_CONFIG_PREFIX$scheduleId"
        val bound = prefs.getLong(boundKey, 0L)
        if (prefs.contains(boundKey) && bound in getTimeConfigIds()) {
            return bound
        }
        // 升级自旧版（仅有全局指针）：回退旧指针并补绑，避免节数被重置为默认
        val resolved = resolveLegacyCurrentTimeConfigId()
        setScheduleTimeConfigId(scheduleId, resolved)
        return resolved
    }

    private fun resolveLegacyCurrentTimeConfigId(): Long {
        val legacy = prefs.getLong(KEY_CURRENT_TIME_CONFIG_ID, 0L)
        if (legacy in getTimeConfigIds()) return legacy
        return getTimeConfigIds().firstOrNull() ?: 0L
    }

    fun setCurrentTimeConfigId(id: Long) {
        prefs.edit { putLong(KEY_CURRENT_TIME_CONFIG_ID, id) }
    }

    fun getTimeConfig(id: Long): TimeConfig {
        timeConfigCache[id]?.let { return it }
        val key = "$TIME_CONFIG_PREFIX$id"
        val json = prefs.getString(key, null)
        val fallback = TimeConfig(id = id, name = "默认配置")
        if (json.isNullOrEmpty()) {
            return fallback
        }
        val config = try {
            val parsed = TimeConfig.parseSnapshotOrNull(gson, json)
            if (parsed == null) {
                // 键名不可辨认：丢弃并覆写默认，避免每次启动读到 0 节
                saveTimeConfig(fallback)
                fallback
            } else {
                val sanitized = TimeConfig.sanitize(id, parsed)
                // 自愈：清洗后覆写回 prefs
                if (sanitized != parsed || !json.contains("morningSections")) {
                    saveTimeConfig(sanitized)
                }
                sanitized
            }
        } catch (_: Exception) {
            fallback
        }
        timeConfigCache[id] = config
        return config
    }

    /** 节数与节次时间都来自这里，保存时同步失效占用/全局节次缓存 */
    fun saveTimeConfig(config: TimeConfig) {
        val key = "${TIME_CONFIG_PREFIX}${config.id}"
        val json = gson.toJson(config)
        prefs.edit { putString(key, json) }
        timeConfigCache[config.id] = config
        invalidateTimeCaches()
    }

    fun addTimeConfig(config: TimeConfig): Long {
        val ids = getTimeConfigIds().toMutableList()
        val newId = (ids.maxOrNull() ?: -1L) + 1L
        val newConfig = config.copy(id = newId)
        ids.add(newId)
        saveTimeConfigIds(ids)
        saveTimeConfig(newConfig)
        return newId
    }

    fun deleteTimeConfig(id: Long) {
        val ids = getTimeConfigIds().toMutableList()
        if (!ids.remove(id)) return
        saveTimeConfigIds(ids)
        prefs.edit { remove("${TIME_CONFIG_PREFIX}$id") }
        timeConfigCache.remove(id)
        if (ids.isNotEmpty() && getCurrentTimeConfigId() == id) {
            setCurrentTimeConfigId(ids.first())
        } else if (ids.isEmpty()) {
            saveTimeConfigIds(listOf(0L))
            setCurrentTimeConfigId(0L)
        }
    }

    fun getCurrentTimeConfig(): TimeConfig {
        return getTimeConfig(getCurrentTimeConfigId())
    }

    /** 相对 key "morning_1" 转全局绝对节次号 -> 名称 */
    fun getSectionNames(): Map<Int, String> {
        val config = getCurrentTimeConfig()
        val names = mutableMapOf<Int, String>()
        for ((k, v) in config.sectionNames) {
            val parts = k.split("_")
            if (parts.size != 2) continue
            val period = parts[0]
            val idx = parts[1].toIntOrNull() ?: continue
            val abs = when (period) {
                "morning" -> idx
                "afternoon" -> config.morningSections + idx
                "evening" -> config.morningSections + config.afternoonSections + idx
                else -> continue
            }
            if (v.isNotBlank()) names[abs] = v
        }
        return names
    }

    fun switchToTimeConfig(id: Long) {
        val config = getTimeConfig(id)
        // 保持课程时段相对位置，避免随绝对节次平移
        remapCoursesForNewSectionCounts(
            config.morningSections, config.afternoonSections, config.eveningSections
        )
        setCurrentTimeConfigId(id)
        setScheduleTimeConfigId(getCurrentScheduleId(), id)
        applyTimeConfigToSchedule(config)
        notifyCourseChanged("settings")
    }

    /** 软导入：覆盖绑定配置以免之后被旧配置盖回；共享 id0 时新建专属配置 */
    fun applyTimeImportToCurrentSchedule(
        morningSections: Int, afternoonSections: Int, eveningSections: Int,
        morningTimes: Map<Int, String>, afternoonTimes: Map<Int, String>, eveningTimes: Map<Int, String>
    ) = applyTimeImportToSchedule(
        getCurrentScheduleId(), morningSections, afternoonSections, eveningSections,
        morningTimes, afternoonTimes, eveningTimes
    )

    /** 同 applyTimeImportToCurrentSchedule，作用于指定课表 */
    fun applyTimeImportToSchedule(
        scheduleId: String,
        morningSections: Int, afternoonSections: Int, eveningSections: Int,
        morningTimes: Map<Int, String>, afternoonTimes: Map<Int, String>, eveningTimes: Map<Int, String>
    ) {
        val configId = getScheduleTimeConfigId(scheduleId)

        val sectionTimes = buildMap {
            morningTimes.forEach { (k, v) -> put("morning_$k", v) }
            afternoonTimes.forEach { (k, v) -> put("afternoon_$k", v) }
            eveningTimes.forEach { (k, v) -> put("evening_$k", v) }
        }

        if (configId != 0L) {
            val base = getTimeConfig(configId)
            saveTimeConfig(
                base.copy(
                    name = base.name.ifBlank { scheduleId },
                    morningSections = morningSections,
                    afternoonSections = afternoonSections,
                    eveningSections = eveningSections,
                    quickTimeEnabled = false,
                    sectionTimes = sectionTimes
                )
            )
        } else {
            val newId = addTimeConfig(
                TimeConfig(
                    name = scheduleId,
                    morningSections = morningSections,
                    afternoonSections = afternoonSections,
                    eveningSections = eveningSections,
                    quickTimeEnabled = false,
                    sectionTimes = sectionTimes
                )
            )
            setScheduleTimeConfigId(scheduleId, newId)
        }
    }

    /**
     * 节数变化时保持课程时段相对位置；不向上钳制，缩节数后课暂落网格外。
     */
    private fun remapCoursesForNewSectionCounts(
        newMorning: Int, newAfternoon: Int, newEvening: Int
    ) {
        val oldMorning = getMorningSections()
        val oldAfternoon = getAfternoonSections()
        val oldEvening = getEveningSections()
        if (oldMorning == newMorning && oldAfternoon == newAfternoon && oldEvening == newEvening) return

        val scheduleId = getCurrentScheduleId()
        var changed = false
        val remapped = getCoursesForSchedule(scheduleId).map { course ->
            val newStart = remapSection(
                course.startSection, oldMorning, oldAfternoon, newMorning, newAfternoon
            )
            val newEnd = remapSection(
                course.endSection, oldMorning, oldAfternoon, newMorning, newAfternoon
            )
            if (newStart != course.startSection || newEnd != course.endSection) {
                changed = true
                course.copy(startSection = newStart, endSection = newEnd)
            } else {
                course
            }
        }
        if (changed) saveCourses(remapped)
    }

    /**
     * 保持时段内相对位置映射节次号；不向上钳制，缩节数后课暂落网格外，恢复后自动归位。
     */
    private fun remapSection(
        section: Int,
        oldMorning: Int, oldAfternoon: Int,
        newMorning: Int, newAfternoon: Int
    ): Int {
        val oldStart: Int
        val period: Int
        when {
            section <= oldMorning -> { oldStart = 1; period = 0 }
            section <= oldMorning + oldAfternoon -> {
                oldStart = oldMorning + 1; period = 1
            }
            else -> {
                oldStart = oldMorning + oldAfternoon + 1; period = 2
            }
        }
        val relative = (section - oldStart).coerceAtLeast(0)
        val newStart = when (period) {
            0 -> 1
            1 -> newMorning + 1
            else -> newMorning + newAfternoon + 1
        }
        return newStart + relative
    }

    private fun extractPeriodTimes(
        sectionTimes: Map<String, String>,
        period: String
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for ((k, v) in sectionTimes) {
            if (!k.startsWith("${period}_")) continue
            val idx = k.removePrefix("${period}_").toIntOrNull() ?: continue
            result[idx] = v
        }
        return result
    }

    /**
     * 一次写入完整配置（旧实现 20+ setter 造成多次磁盘提交；且 savePeriodTimes 会
     * 静默关掉 quickTimeEnabled，这里显式保持关闭以对齐历史行为）。
     */
    private fun applyTimeConfigToSchedule(config: TimeConfig) {
        val scheduleId = getCurrentScheduleId()
        val configId = getScheduleTimeConfigId(scheduleId)
        val base = getTimeConfig(configId)

        // 配置空则回退默认时段时间；非空但某时段缺失时保留原值
        val sectionTimes: Map<String, String> = if (config.sectionTimes.isNotEmpty()) {
            val merged = base.sectionTimes.toMutableMap()
            for (period in listOf("morning", "afternoon", "evening")) {
                val times = extractPeriodTimes(config.sectionTimes, period)
                if (times.isEmpty()) continue
                merged.keys.filter { it.startsWith("${period}_") }.forEach { merged.remove(it) }
                times.forEach { (idx, v) -> merged["${period}_$idx"] = v }
            }
            merged
        } else {
            buildMap {
                Course.defaultMorningTimes.forEach { (k, v) -> put("morning_$k", v) }
                Course.defaultAfternoonTimes.forEach { (k, v) -> put("afternoon_$k", v) }
                Course.defaultEveningTimes.forEach { (k, v) -> put("evening_$k", v) }
            }
        }

        batchingSettings = true
        try {
            saveTimeConfig(
                base.copy(
                    morningSections = config.morningSections,
                    afternoonSections = config.afternoonSections,
                    eveningSections = config.eveningSections,
                    // 与旧 savePeriodTimes 一致：应用配置时快速时间保持关闭
                    quickTimeEnabled = false,
                    classDuration = config.classDuration,
                    shortBreak = config.shortBreak,
                    longBreakEnabled = config.longBreakEnabled,
                    longBreakMorning = config.longBreakMorning,
                    longBreakAfternoon = config.longBreakAfternoon,
                    longBreakEvening = config.longBreakEvening,
                    longBreakMorningSection = config.longBreakMorningSection,
                    longBreakAfternoonSection = config.longBreakAfternoonSection,
                    longBreakEveningSection = config.longBreakEveningSection,
                    morningStartHour = config.morningStartHour,
                    morningStartMinute = config.morningStartMinute,
                    afternoonStartHour = config.afternoonStartHour,
                    afternoonStartMinute = config.afternoonStartMinute,
                    eveningStartHour = config.eveningStartHour,
                    eveningStartMinute = config.eveningStartMinute,
                    sectionTimes = sectionTimes
                )
            )
            writeLegacyTimeShadowPrefs(scheduleId, config)
        } finally {
            batchingSettings = false
        }
        commitSettingsChanged()
    }

    /** 旧影子键无读取方，仅 export 兼容旧版回滚；合并为一次提交 */
    private fun writeLegacyTimeShadowPrefs(scheduleId: String, config: TimeConfig) {
        val prefix = getScheduleKeyPrefix(scheduleId)
        prefs.edit {
            putBoolean("${prefix}$KEY_QUICK_TIME_ENABLED", config.quickTimeEnabled)
            putInt("${prefix}$KEY_CLASS_DURATION", config.classDuration)
            putInt("${prefix}$KEY_SHORT_BREAK", config.shortBreak)
            putBoolean("${prefix}${KEY_LONG_BREAK}_enabled", config.longBreakEnabled)
            putInt("${prefix}${KEY_LONG_BREAK}_morning", config.longBreakMorning)
            putInt("${prefix}${KEY_LONG_BREAK}_afternoon", config.longBreakAfternoon)
            putInt("${prefix}${KEY_LONG_BREAK}_evening", config.longBreakEvening)
            putInt("${prefix}${KEY_LONG_BREAK}_morning_section", config.longBreakMorningSection)
            putInt("${prefix}${KEY_LONG_BREAK}_afternoon_section", config.longBreakAfternoonSection)
            putInt("${prefix}${KEY_LONG_BREAK}_evening_section", config.longBreakEveningSection)
            putInt("${prefix}$KEY_MORNING_START", config.morningStartHour)
            putInt("${prefix}${KEY_MORNING_START}_min", config.morningStartMinute)
            putInt("${prefix}$KEY_AFTERNOON_START", config.afternoonStartHour)
            putInt("${prefix}${KEY_AFTERNOON_START}_min", config.afternoonStartMinute)
            putInt("${prefix}$KEY_EVENING_START", config.eveningStartHour)
            putInt("${prefix}${KEY_EVENING_START}_min", config.eveningStartMinute)
        }
    }

    fun migrateToTimeConfigsIfNeeded() {
        if (prefs.contains(KEY_TIME_CONFIG_IDS)) return
        val currentConfig = TimeConfig.fromRepository(this).copy(id = 0L, name = "默认配置")
        prefs.edit {
            putString(KEY_TIME_CONFIG_IDS, "0")
            putLong(KEY_CURRENT_TIME_CONFIG_ID, 0L)
        }
        saveTimeConfig(currentConfig)
    }

    /**
     * 为未绑定课表各建独立配置：修复历史上 getScheduleTimeConfigId 回退共享
     * 第一个配置导致「改 A 波及 B」；内容 copy 自回退目标，用户所见时间不变。
     */
    private fun migrateScheduleTimeConfigBindingsIfNeeded() {
        for (name in getScheduleNames()) {
            val boundKey = "$SCHEDULE_TIME_CONFIG_PREFIX$name"
            if (prefs.contains(boundKey)) continue
            val fallbackId = getTimeConfigIds().firstOrNull() ?: continue
            val fallback = getTimeConfig(fallbackId)
            val newId = addTimeConfig(fallback.copy(id = 0L, name = name))
            setScheduleTimeConfigId(name, newId)
        }
    }

    /** 导出 schedule_ / time_config_ 等前缀与全局课表配置，供云备份 */
    fun exportAllPreferences(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val relevantKeys = listOf(
            KEY_SCHEDULE_NAMES,
            KEY_CURRENT_SCHEDULE_ID,
            KEY_SHIFT_MODE,
            KEY_SHIFT_SELECTED_SCHEDULES,
            KEY_DEFAULT_HOMEPAGE,
            KEY_TIME_CONFIG_IDS,
            KEY_CURRENT_TIME_CONFIG_ID
        )
        for ((key, value) in prefs.all) {
            if (key.startsWith(SCHEDULE_KEY_PREFIX) || key.startsWith(TIME_CONFIG_PREFIX) ||
                key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX) || key in relevantKeys) {
                when (value) {
                    is String -> result[key] = value
                    is Int -> result[key] = value
                    is Boolean -> result[key] = value
                    is Float -> result[key] = value
                    is Long -> result[key] = value
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        result[key] = (value as Set<String>).toList()
                    }
                }
            }
        }
        return result
    }

    fun importAllPreferences(data: Map<String, Any>) {
        prefs.edit {
            for ((key) in prefs.all) {
                if (key.startsWith(SCHEDULE_KEY_PREFIX) || key.startsWith(TIME_CONFIG_PREFIX) ||
                    key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX)) {
                    remove(key)
                }
            }
            remove(KEY_SCHEDULE_NAMES)
            remove(KEY_CURRENT_SCHEDULE_ID)
            remove(KEY_SHIFT_MODE)
            remove(KEY_SHIFT_SELECTED_SCHEDULES)
            remove(KEY_TIME_CONFIG_IDS)
            remove(KEY_CURRENT_TIME_CONFIG_ID)
            remove(KEY_DEFAULT_HOMEPAGE)

            for ((key, value) in data) {
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Number -> {
                        val numVal = value.toDouble()
                        val longVal = numVal.toLong()
                        val intVal = numVal.toInt()
                        // 时间配置/绑定/组合 ID 与时间戳必须按 Long 恢复
                        if (key == KEY_CURRENT_TIME_CONFIG_ID || key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX) ||
                            key == "current_combination_id" || key.endsWith("_last_modified")) {
                            putLong(key, longVal)
                        } else if (numVal == intVal.toDouble()) {
                            putInt(key, intVal)
                        } else {
                            putFloat(key, numVal.toFloat())
                        }
                    }

                    is List<*> -> {
                        // Set<String> 被导出为 List，需要还原
                        @Suppress("UNCHECKED_CAST")
                        val list = value.filterIsInstance<String>()
                        putString(key, gson.toJson(list))
                    }
                }
            }
        }
        invalidateAllCaches()
        onCourseChanged?.invoke("restore", "")
    }

    fun getSectionsForSchedule(scheduleId: String): Triple<Int, Int, Int> {
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        return Triple(config.morningSections, config.afternoonSections, config.eveningSections)
    }

    /** @param timeConfigData 可选；有则强制新建绑定，否则无绑定时复制当前配置 */
    fun importSingleSchedule(scheduleName: String, coursesData: List<Map<String, Any>>, timeConfigData: Map<String, Any>? = null) {
        val names = getScheduleNames().toMutableList()
        if (scheduleName !in names) {
            names.add(scheduleName)
            saveScheduleNames(names)
        }

        val prefix = "$SCHEDULE_KEY_PREFIX${scheduleName}_"
        var colorIndex = 0
        val courses = coursesData.mapNotNull { courseMap ->
            val name = courseMap["name"] as? String ?: return@mapNotNull null
            val classroom = courseMap["classroom"] as? String ?: ""
            val teacher = courseMap["teacher"] as? String ?: ""
            val dayOfWeek = (courseMap["dayOfWeek"] as? Number)?.toInt() ?: return@mapNotNull null
            val startSection = (courseMap["startSection"] as? Number)?.toInt() ?: return@mapNotNull null
            val endSection = (courseMap["endSection"] as? Number)?.toInt() ?: return@mapNotNull null
            // JSON 数组元素可能是 Double/Integer，as? Number 更稳
            val selectedWeeks = (courseMap["selectedWeeks"] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }
                ?: emptyList()
            // 旧备份只有 selectedWeeks：用 min/max 推断，weekType 无从还原保持 0
            val explicitStartWeek = (courseMap["startWeek"] as? Number)?.toInt()
            val explicitEndWeek = (courseMap["endWeek"] as? Number)?.toInt()
            val weekType = (courseMap["weekType"] as? Number)?.toInt() ?: 0
            val startWeek = explicitStartWeek ?: selectedWeeks.minOrNull() ?: 1
            val endWeek = maxOf(startWeek, explicitEndWeek ?: selectedWeeks.maxOrNull() ?: 20)

            val exportedColor = (courseMap["colorRes"] as? Number)?.toLong()
            val color = exportedColor ?: run {
                val c = Course.courseColors[colorIndex % Course.courseColors.size]
                colorIndex++
                c
            }
            Course(
                id = "${scheduleName}_${name}_${dayOfWeek}_$startSection",
                scheduleId = scheduleName,
                name = name,
                classroom = classroom,
                teacher = teacher,
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                endSection = endSection,
                isCustomTime = (courseMap["isCustomTime"] as? Boolean) ?: false,
                customStartTime = courseMap["customStartTime"] as? String,
                customEndTime = courseMap["customEndTime"] as? String,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = weekType,
                colorRes = color,
                selectedWeeks = selectedWeeks
            )
        }

        val key = "${prefix}$KEY_COURSES"
        val json = gson.toJson(courses)
        prefs.edit { putString(key, json) }
        invalidateAllCaches()

        // 不能靠 getScheduleTimeConfigId==0 判断未绑定（会 fallback 到第一个配置），
        // 也不能只看 contains（deleteSchedule 历史上不清理绑定）。
        // 有 time_config 则强制新建覆盖；没有且确实无绑定时才复制当前配置。
        val boundKey = "$SCHEDULE_TIME_CONFIG_PREFIX$scheduleName"
        if (timeConfigData != null || !prefs.contains(boundKey)) {
            val newConfig = if (timeConfigData != null) {
                // 旧备份可能只含部分字段，其余走默认值
                val sectionTimesMap = mutableMapOf<String, String>()
                (timeConfigData["sectionTimes"] as? Map<*, *>)?.forEach { (k, v) ->
                    if (k is String && v is String) sectionTimesMap[k] = v
                }
                val importedSectionNames = mutableMapOf<String, String>()
                (timeConfigData["sectionNames"] as? Map<*, *>)?.forEach { (k, v) ->
                    if (k is String && v is String) importedSectionNames[k] = v
                }
                val importedSpecialBlocks = (timeConfigData["specialBlocks"] as? List<*>)
                    ?.mapNotNull { SpecialBlock.fromRaw(it) }
                    ?: emptyList()
                TimeConfig(
                    name = scheduleName,
                    morningSections = (timeConfigData["morningSections"] as? Number)?.toInt() ?: 4,
                    afternoonSections = (timeConfigData["afternoonSections"] as? Number)?.toInt() ?: 4,
                    eveningSections = (timeConfigData["eveningSections"] as? Number)?.toInt() ?: 4,
                    quickTimeEnabled = (timeConfigData["quickTimeEnabled"] as? Boolean) ?: false,
                    classDuration = (timeConfigData["classDuration"] as? Number)?.toInt() ?: 45,
                    shortBreak = (timeConfigData["shortBreak"] as? Number)?.toInt() ?: 10,
                    longBreakEnabled = (timeConfigData["longBreakEnabled"] as? Boolean) ?: false,
                    longBreakMorning = (timeConfigData["longBreakMorning"] as? Number)?.toInt() ?: 20,
                    longBreakAfternoon = (timeConfigData["longBreakAfternoon"] as? Number)?.toInt() ?: 20,
                    longBreakEvening = (timeConfigData["longBreakEvening"] as? Number)?.toInt() ?: 20,
                    longBreakMorningSection = (timeConfigData["longBreakMorningSection"] as? Number)?.toInt() ?: 2,
                    longBreakAfternoonSection = (timeConfigData["longBreakAfternoonSection"] as? Number)?.toInt() ?: 2,
                    longBreakEveningSection = (timeConfigData["longBreakEveningSection"] as? Number)?.toInt() ?: 2,
                    morningStartHour = (timeConfigData["morningStartHour"] as? Number)?.toInt() ?: 8,
                    morningStartMinute = (timeConfigData["morningStartMinute"] as? Number)?.toInt() ?: 0,
                    afternoonStartHour = (timeConfigData["afternoonStartHour"] as? Number)?.toInt() ?: 14,
                    afternoonStartMinute = (timeConfigData["afternoonStartMinute"] as? Number)?.toInt() ?: 0,
                    eveningStartHour = (timeConfigData["eveningStartHour"] as? Number)?.toInt() ?: 18,
                    eveningStartMinute = (timeConfigData["eveningStartMinute"] as? Number)?.toInt() ?: 30,
                    sectionTimes = sectionTimesMap,
                    sectionNames = importedSectionNames,
                    specialBlocks = importedSpecialBlocks
                )
            } else {
                // 没有导入时间配置，复制当前课表的
                val defaultConfig = getCurrentTimeConfig()
                defaultConfig.copy(name = scheduleName, id = 0L)
            }
            val newConfigId = addTimeConfig(newConfig)
            setScheduleTimeConfigId(scheduleName, newConfigId)
        }

        onCourseChanged?.invoke("restore", "")
    }
}
