package com.haooz.chedule.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.graphics.scale
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

/**
 * 课程数据仓库 - 使用 SharedPreferences 存储（单例）
 */
class CourseRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()

    // 课程内存缓存：避免每次读取都解析 JSON
    private val courseCache = mutableMapOf<String, List<Course>>()

    // 占用周次缓存：避免每次编辑都重新计算
    private val occupiedWeeksCache = mutableMapOf<String, Set<Int>>()

    // 时间配置缓存：getPeriodTimes 等为高频调用，原本每次都要走一次 prefs 读取 + Gson 反序列化
    private val timeConfigCache = mutableMapOf<Long, TimeConfig>()
    // 时间配置 ID 列表缓存：getScheduleTimeConfigId 每次都要解析一次字符串
    private var timeConfigIdsCache: List<Long>? = null
    // 当前课表 ID 缓存：几乎所有 key 拼接都经过它，是全类最热路径
    private var currentScheduleIdCache: String? = null
    // 课表名列表缓存：getCurrentScheduleId 内部的校验依赖它
    private var scheduleNamesCache: List<String>? = null
    // 全局节次时间缓存：由三个时段 map 合并而来，节数/节次时间未变时可复用
    private var globalSectionTimesCache: Map<Int, String>? = null
    // 搭配外观快照缓存：一个搭配的全部外观参数已收敛为单个 JSON，读一次即可
    private val combinationStyleCache = mutableMapOf<Long, CombinationStyle>()

    // 壁纸内存缓存：避免每次进入搭配页都重新解码 PNG（解码耗时是主要瓶颈）
    // 按可用内存的 1/8 计算，单位为字节
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
    }

    // 变更回调
    var onCourseChanged: ((action: String, courseId: String) -> Unit)? = null

    /**
     * 统一失效全部内存缓存。
     *
     * 凡是绕过本类 setter、直接改写 prefs 的地方（导入备份 / 删除课表 / 重命名课表 / 迁移）
     * 都必须调用，否则会读到与磁盘不一致的陈旧数据。
     */
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

    /**
     * 失效节次时间相关的缓存。
     * 节数或节次时间变化会同时影响全局节次时间映射与分钟级占用判断。
     */
    private fun invalidateTimeCaches() {
        occupiedWeeksCache.clear()
        globalSectionTimesCache = null
    }

    /**
     * 批量设置写入中：为 true 时 [notifyCourseChanged] 不再逐次写时间戳 / 清缓存 / 回调 UI，
     * 由批量结束时统一执行一次，避免连续写入导致的重复磁盘提交与 UI 抖动。
     */
    private var batchingSettings = false

    private fun notifyCourseChanged(action: String, courseId: String = "") {
        if (action == "settings") {
            // 批量模式下延迟到批次结束统一提交
            if (batchingSettings) return
            commitSettingsChanged()
            return
        }
        onCourseChanged?.invoke(action, courseId)
    }

    /**
     * 提交一次设置变更：更新时间戳（确保本地修改不被远程覆盖）、失效时间缓存、通知 UI。
     */
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
        // 多搭配支持
        // 注意：当前只有单搭配，KEY_COMBINATION_IDS 恒为 "0"（见 migrateToCombinationsIfNeeded）。
        // 保留 id 维度是为了不破坏数据结构，将来要恢复多搭配时无需再改存储。
        private const val KEY_COMBINATION_IDS = "combination_ids"
        private const val KEY_CURRENT_COMBINATION_ID = "current_combination_id"
        private const val COMBINATION_WALLPAPER_PREFIX = "combination_wallpaper_"
        // 外观参数的合并存储键（取代下面 17 个 comb_xxx_{id} 分散键）
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
        // 多时间配置支持
        private const val KEY_TIME_CONFIG_IDS = "time_config_ids"
        private const val KEY_CURRENT_TIME_CONFIG_ID = "current_time_config_id"
        private const val TIME_CONFIG_PREFIX = "time_config_"
        // 课表绑定时间配置
        private const val SCHEDULE_TIME_CONFIG_PREFIX = "schedule_time_config_"
    }

    /**
     * 兼容性读取 Int 值（云备份恢复时可能存为 Float）
     */
    private fun safeGetInt(key: String, defValue: Int): Int {
        try {
            return prefs.getInt(key, defValue)
        } catch (_: ClassCastException) {
            val floatVal = prefs.getFloat(key, defValue.toFloat())
            val intVal = floatVal.toInt()
            // 修正存储类型为 Int
            prefs.edit { putInt(key, intVal) }
            return intVal
        }
    }

    /**
     * 获取指定课表的课程列表（带缓存）
     */
    fun getCoursesForSchedule(scheduleId: String): List<Course> {
        courseCache[scheduleId]?.let { return it }
        val key = "$SCHEDULE_KEY_PREFIX${scheduleId}_$KEY_COURSES"
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<Course>>() {}.type
        return try {
            val courses = sanitizeCourses(gson.fromJson(json, type) ?: emptyList())
            courseCache[scheduleId] = courses
            courses
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 获取课表摘要（课程数和周数范围）
     */
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

    /**
     * 获取当前课表的数据键前缀
     */
    private fun getScheduleKeyPrefix(): String {
        return getScheduleKeyPrefix(getCurrentScheduleId())
    }

    /**
     * 指定课表的键前缀。用于“导入到指定课表”等场景，将设置写入目标课表而非当前课表。
     */
    private fun getScheduleKeyPrefix(scheduleId: String): String {
        return "$SCHEDULE_KEY_PREFIX${scheduleId}_"
    }

    /**
     * 修复旧数据中 scheduleId/selectedWeeks 可能为 null 的问题
     * Gson 反序列化绕过 Kotlin non-null 检查，后加的字段在旧 JSON 中缺失时会被设为 null
     * 直接调用 copy() 会因将 null 传给 non-null 参数而 NPE
     */
    // USELESS_ELVIS：以下 ?: 在编译器看来永远走左值，但 Gson 用 UnsafeAllocator 绕过构造器反序列化，
    // 旧 JSON 缺失字段时这些"非空"字段会是 null，运行期必须兜底，不能删。
    @Suppress(
        "SENSELESS_COMPARISON",
        "ELVIS_ALWAYS_NULL",
        "USELESS_ELVIS",
        "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS"
    )
    private fun sanitizeCourses(courses: List<Course>): List<Course> {
        return courses.map { course ->
            if (course.scheduleId == null || course.selectedWeeks == null
                || course.customStartTime == null || course.customEndTime == null) {
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
            } else {
                course
            }
        }
    }

    /**
     * 获取当前课表的所有课程（带缓存）
     */
    fun getAllCourses(): List<Course> {
        val scheduleId = getCurrentScheduleId()
        courseCache[scheduleId]?.let { return it }
        val key = "${getScheduleKeyPrefix()}$KEY_COURSES"
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<Course>>() {}.type
        return try {
            val courses = sanitizeCourses(gson.fromJson(json, type) ?: emptyList())
            courseCache[scheduleId] = courses
            preWarmOccupiedWeeksCache(courses)
            courses
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 预热占用周次缓存：预计算所有节次组合的占用周次
     * 占用判断为分钟级时间重叠（自定义时间课程同样参与占用）
     */
    private fun preWarmOccupiedWeeksCache(courses: List<Course>) {
        occupiedWeeksCache.clear()
        val totalSections = getMorningSections() + getAfternoonSections() + getEveningSections()
        val sectionTimes = getGlobalSectionTimes()
        for (day in 1..7) {
            for (start in 1..totalSections) {
                for (end in start..totalSections) {
                    val occupied = mutableSetOf<Int>()
                    val newStartMin = timeToMinutes(sectionTimes[start]?.substringBefore("-")?.trim())
                    val newEndMin = timeToMinutes(sectionTimes[end]?.substringAfter("-")?.trim())
                    courses.forEach { course ->
                        if (course.dayOfWeek == day &&
                            isTimeConflict(newStartMin, newEndMin, start, end, course, sectionTimes)
                        ) {
                            addCourseWeeks(occupied, course)
                        }
                    }
                    occupiedWeeksCache[occupiedWeeksKey(day, start, end)] = occupied
                }
            }
        }
    }

    /**
     * 保存课程列表
     */
    fun saveCourses(courses: List<Course>, notify: Boolean = true) {
        val scheduleId = getCurrentScheduleId()
        val key = "${getScheduleKeyPrefix()}$KEY_COURSES"
        val json = gson.toJson(courses)
        prefs.edit { putString(key, json) }
        courseCache[scheduleId] = courses
        occupiedWeeksCache.clear() // 课程变化时清空占用周次缓存
        if (notify) onCourseChanged?.invoke("bulk", "")
    }

    /**
     * 添加课程
     */
    fun addCourse(course: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        // 自动设置 scheduleId
        val courseWithSchedule = if (course.scheduleId.isEmpty()) {
            course.copy(scheduleId = getCurrentScheduleId())
        } else {
            course
        }
        courses.add(courseWithSchedule)
        saveCourses(courses, notify = false)
        return courses
    }

    /**
     * 更新课程
     */
    fun updateCourse(course: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        val index = courses.indexOfFirst { it.id == course.id }
        if (index != -1) {
            courses[index] = course.copy(lastModified = System.currentTimeMillis())
            saveCourses(courses, notify = false)
        }
        return courses
    }

    /**
     * 按旧名称更新所有同名课程
     */
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

    /**
     * 删除课程
     */
    fun deleteCourse(courseId: String): List<Course> {
        val courses = getAllCourses().toMutableList()
        courses.removeAll { it.id == courseId }
        saveCourses(courses, notify = false)
        return courses
    }

    /**
     * 仅删除指定周次的课程实例
     * 如果该课程只有当前周，则删除整个课程
     */
    fun deleteCourseForWeek(courseId: String, week: Int): List<Course> {
        val courses = getAllCourses().toMutableList()
        val index = courses.indexOfFirst { it.id == courseId }
        if (index != -1) {
            val updated = removeWeekFrom(courses[index], week)
            if (updated == null) {
                // 所有周次都已移除，删除整个课程
                courses.removeAt(index)
            } else {
                courses[index] = updated
            }
            saveCourses(courses, notify = false)
            // 不在此处触发 onCourseChanged，由 ViewModel 统一处理 UI 更新，避免竞态
        }
        return courses
    }

    /**
     * 从课程中移除指定周次。
     *
     * @return 移除后的新课程；若该周是课程的最后一周，返回 null 表示整条课程应被删除。
     */
    private fun removeWeekFrom(course: Course, week: Int, resetWeekType: Boolean = false): Course? {
        val remaining = resolveSelectedWeeks(course).filter { it != week }
        if (remaining.isEmpty()) return null
        return course.copy(
            selectedWeeks = remaining,
            startWeek = remaining.min(),
            endWeek = remaining.max(),
            // 冲突处理路径调用方置 ALL（与原行为一致）；单独删除某周时保持原 weekType
            weekType = if (resetWeekType) Course.WEEK_TYPE_ALL else course.weekType,
            lastModified = System.currentTimeMillis()
        )
    }

    /**
     * 解析课程有效周次列表（若 selectedWeeks 为空，根据 startWeek/endWeek/weekType 推导）
     */
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

    /**
     * 判断两课程是否同源（相同名称、教室、教师、位置）
     * 用于调课时合并周次而非新建重复课程
     */
    private fun isSameCourseIdentity(a: Course, b: Course): Boolean {
        return a.name == b.name &&
            a.classroom == b.classroom &&
            a.teacher == b.teacher &&
            a.dayOfWeek == b.dayOfWeek &&
            a.startSection == b.startSection &&
            a.endSection == b.endSection
    }

    /**
     * 调课-移动：将指定周次的课程实例移动到新位置（仅影响该周）
     *
     * 核心逻辑：
     * 1. 若源课程只在该周有效 → 直接改位置
     * 2. 若源课程多周有效 → 拆分：源移除该周，新建单周课程到目标位置
     * 3. 若目标位置已有同源课程 → 合并周次进同源课程，不新建
     *
     * 边界情况处理：
     * - 调回原位置：若与原课程同源，合并回去并删除拆分出的课程
     * - 连续调课到同位置：同源合并，避免产生多条相同课程
     * - selectedWeeks 为空：先展开为周次列表再操作
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
        // 不在此处触发 onCourseChanged，由 ViewModel 统一处理 UI 更新，避免竞态
        return result
    }

    /**
     * 调课-覆盖：将指定周次的源课程移动到目标位置，并删除该周在目标位置上的所有冲突课程
     *
     * 冲突课程处理：
     * - 非同源课程：按周删除（从 selectedWeeks 移除该周，若变空则删除整个课程）
     * - 同源课程：不删除，由 moveCourseForWeek 走合并路径
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

        // 构造目标位置的临时课程对象，用于同源判断
        val targetTemp = source.copy(
            dayOfWeek = targetDayOfWeek,
            startSection = targetStartSection,
            endSection = targetEndSection
        )

        // 找到目标位置上、该周有效的所有冲突课程（排除源课程自身和同源课程）
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

        // 对每个冲突课程执行按周删除
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
        // 保存中间结果，避免 moveCourseForWeek 重新读取旧数据
        saveCourses(result, notify = false)
        // 然后执行移动
        return moveCourseForWeek(sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection)
    }

    /**
     * 调课-交换：将指定周次的源课程与目标课程互换位置（仅影响该周）
     *
     * 双方都拆分出该周实例，互换位置。
     * 拆分后若与对方原位置已有同源课程，同样走合并逻辑。
     */
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

        // 记录双方原位置
        val srcPos = Triple(src.dayOfWeek, src.startSection, src.endSection)
        val tgtPos = Triple(tgt.dayOfWeek, tgt.startSection, tgt.endSection)

        // 双方都必须在该周有效
        val srcWeeks = resolveSelectedWeeks(src)
        val tgtWeeks = resolveSelectedWeeks(tgt)
        if (week !in srcWeeks || week !in tgtWeeks) return courses

        // 双方互换位置：源移到目标位置，目标移到源位置
        // 复用 moveCourseForWeek 的拆分+合并逻辑，分两步执行
        // 第一步：源课程移到目标位置（会自动处理与目标位置其他课程的同源合并）
        var result: MutableList<Course> = courses
        moveWeekInPlace(result, src.id, week, tgtPos.first, tgtPos.second, tgtPos.third)?.let { result = it }
        // 第二步：目标课程移到源原位置
        // 注意：第一步后源课程可能已拆分，src 的 id 仍在原课程（已移除该周）
        moveWeekInPlace(result, tgt.id, week, srcPos.first, srcPos.second, srcPos.third)?.let { result = it }

        saveCourses(result, notify = false)
        // 不在此处触发 onCourseChanged，由 ViewModel 统一处理 UI 更新，避免竞态
        return result
    }

    /**
     * 在给定列表上执行"按周移动"的拆分+合并逻辑（原地操作，不保存）。
     *
     * @return 修改后的新列表；无需改动（课程不存在 / 目标位置相同 / 该周无效）时返回 null，
     *         调用方可据此跳过一次无意义的落盘。
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

    /**
     * 获取有课程的最晚周次，若没有任何课程则返回 0
     */
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

    /**
     * 获取当前周次
     */
    fun getCurrentWeek(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_CURRENT_WEEK"
        return safeGetInt(key, 1)
    }

    /**
     * 设置当前周次
     */
    fun setCurrentWeek(week: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_CURRENT_WEEK"
        prefs.edit { putInt(key, week) }
        notifyCourseChanged("settings")
    }

    /**
     * 获取总周数
     */
    fun getTotalWeeks(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_TOTAL_WEEKS"
        return safeGetInt(key, 20)
    }

    /**
     * 设置总周数
     */
    fun setTotalWeeks(weeks: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_TOTAL_WEEKS"
        prefs.edit { putInt(key, weeks) }
        notifyCourseChanged("settings")
    }

    /**
     * 获取开始上课日期（格式 YYYY/MM/DD）
     * 如果存储的值不是日期格式（如旧版本的时间格式），自动回退为当天日期
     */
    fun getClassStartTime(): String {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_START_TIME"
        val cal = java.util.Calendar.getInstance()
        val default = String.format(java.util.Locale.ROOT, "%04d/%02d/%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        val stored = prefs.getString(key, null)
        // 校验存储值是否符合 YYYY/MM/DD 格式
        if (stored != null && Regex("^\\d{4}/\\d{2}/\\d{2}$").matches(stored)) {
            return stored
        }
        // 不符合则写入默认值并返回
        prefs.edit { putString(key, default) }
        return default
    }

    /**
     * 设置开始上课日期
     */
    fun setClassStartTime(time: String) {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_START_TIME"
        prefs.edit { putString(key, time) }
        notifyCourseChanged("settings")
    }

    /**
     * 获取是否开启智能显示周末
     */
    fun getSmartWeekend(): Boolean {
        val key = "${getScheduleKeyPrefix()}$KEY_SMART_WEEKEND"
        // 兼容旧数据：首次读取时检查旧 key
        if (!prefs.contains(key)) {
            val oldKey = "${getScheduleKeyPrefix()}$KEY_SHOW_WEEKEND"
            val oldVal = prefs.getString(oldKey, "")
            val smart = !oldVal.isNullOrBlank()
            prefs.edit { putBoolean(key, smart); remove(oldKey) }
            return smart
        }
        return prefs.getBoolean(key, false)
    }

    /**
     * 设置是否开启智能显示周末
     */
    fun setSmartWeekend(smart: Boolean) {
        val key = "${getScheduleKeyPrefix()}$KEY_SMART_WEEKEND"
        prefs.edit { putBoolean(key, smart) }
        notifyCourseChanged("settings")
    }

    /**
     * 获取今日页是否显示壁纸
     */
    fun getTodayShowWallpaper(): Boolean {
        val key = "${getScheduleKeyPrefix()}$KEY_TODAY_SHOW_WALLPAPER"
        return prefs.getBoolean(key, true)
    }

    /**
     * 设置今日页是否显示壁纸
     */
    fun setTodayShowWallpaper(show: Boolean) {
        val key = "${getScheduleKeyPrefix()}$KEY_TODAY_SHOW_WALLPAPER"
        prefs.edit { putBoolean(key, show) }
        notifyCourseChanged("settings")
    }

    /**
     * 检查当前课表中指定周次、星期是否有课程（含调休补班日）
     * 智能周末据此决定是否显示该周末天
     */
    fun hasCoursesOnDayInWeek(dayOfWeek: Int, week: Int): Boolean {
        if (getAllCourses().any { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week) }) return true
        // 调休补班日：该天有被调来的课程，也视为“有课”
        return hasWorkSwapOnDay(dayOfWeek, week)
    }

    /**
     * 指定周次、星期是否为调休补班日（该天有已配置补班课的 WORKSWAP 条目）
     * 待配置补班课程（followWeekday 未设置）不视为有课，避免智能周末误显示
     */
    fun hasWorkSwapOnDay(dayOfWeek: Int, week: Int): Boolean {
        val start = runCatching {
            LocalDate.parse(getClassStartTime().replace("/", "-"))
        }.getOrNull() ?: return false
        val monday = start.minusDays((start.dayOfWeek.value - 1).toLong())
        val date = monday.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek - 1).toLong())
        val swap = HolidayManager.workSwap(appContext, date) ?: return false
        return swap.followWeekday in 1..7
    }

    /**
     * 获取是否显示非本周课程
     */
    fun getShowNonCurrentWeek(): Boolean {
        val key = "${getScheduleKeyPrefix()}$KEY_SHOW_NON_CURRENT_WEEK"
        return prefs.getBoolean(key, true)
    }

    /**
     * 设置是否显示非本周课程
     */
    fun setShowNonCurrentWeek(show: Boolean) {
        val key = "${getScheduleKeyPrefix()}$KEY_SHOW_NON_CURRENT_WEEK"
        prefs.edit { putBoolean(key, show) }
        notifyCourseChanged("settings")
    }

    /**
     * 获取上午节数
     */
    fun getMorningSections(): Int = getMorningSections(getCurrentScheduleId())

    /**
     * 获取指定课表的上午节数，用于导入到目标课表
     */
    fun getMorningSections(scheduleId: String): Int {
        val configId = getScheduleTimeConfigId(scheduleId)
        return getTimeConfig(configId).morningSections
    }

    /**
     * 设置上午节数
     */
    fun setMorningSections(count: Int) {
        val scheduleId = getCurrentScheduleId()
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        saveTimeConfig(config.copy(morningSections = count))
        notifyCourseChanged("settings")
    }

    fun getAfternoonSections(): Int = getAfternoonSections(getCurrentScheduleId())

    /**
     * 获取指定课表的下午节数，用于导入到目标课表
     */
    fun getAfternoonSections(scheduleId: String): Int {
        val configId = getScheduleTimeConfigId(scheduleId)
        return getTimeConfig(configId).afternoonSections
    }

    /**
     * 设置下午节数
     */
    fun setAfternoonSections(count: Int) {
        val scheduleId = getCurrentScheduleId()
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        saveTimeConfig(config.copy(afternoonSections = count))
        notifyCourseChanged("settings")
    }

    fun getEveningSections(): Int = getEveningSections(getCurrentScheduleId())

    /**
     * 获取指定课表的晚上节数，用于导入到目标课表
     */
    fun getEveningSections(scheduleId: String): Int {
        val configId = getScheduleTimeConfigId(scheduleId)
        return getTimeConfig(configId).eveningSections
    }

    /**
     * 设置晚上节数
     */
    fun setEveningSections(count: Int) {
        val scheduleId = getCurrentScheduleId()
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        saveTimeConfig(config.copy(eveningSections = count))
        notifyCourseChanged("settings")
    }

    /**
     * 获取指定时段的节次时间映射（统一从当前课表绑定的 TimeConfig 读取）
     * period: "morning" / "afternoon" / "evening"
     * key: 时段内相对节次号 (1-6), value: "HH:mm-HH:mm"
     */
    fun getPeriodTimes(period: String): Map<Int, String> {
        return getPeriodTimes(period, getCurrentScheduleId())
    }

    /**
     * 获取指定课表指定时段的节次时间映射（从该课表绑定的 TimeConfig 读取）
     */
    fun getPeriodTimes(period: String, scheduleId: String): Map<Int, String> {
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        return config.getPeriodTimes(period)
    }

    /**
     * 保存指定时段的节次时间映射（写入当前课表绑定的 TimeConfig）
     */
    fun savePeriodTimes(period: String, times: Map<Int, String>) {
        savePeriodTimes(period, times, getCurrentScheduleId())
    }

    /**
     * 保存指定时段的节次时间映射到指定课表绑定的 TimeConfig。
     * 仅当目标是当前课表时触发课程变更通知，避免导入目标课表时无谓刷新当前课表。
     */
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

    // --- 旧版遗留影子 prefs 的读取口 ---
    // 引入 TimeConfig 之后，节数 / 节次时间 / 快速时间参数的唯一数据源是 TimeConfig，
    // 下面这些 getter 读的 schedule_{课表}_* 键只在 TimeConfig.fromRepository() 做版本迁移时被读过一次。
    // 保留原因：迁移路径要读旧数据，exportAllPreferences 也依赖这套键做导出兼容。
    // 日常读写一律走 TimeConfig，不要在这里新增逻辑。
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

    /**
     * 获取「上课自动开启勿扰」开关。
     * 开启后，上课时自动进入系统勿扰模式，下课后自动退出（需要用户已授予勿扰权限）。
     */
    fun getClassDndEnabled(): Boolean {
        return prefs.getBoolean(KEY_CLASS_DND, false)
    }

    /**
     * 设置「上课自动开启勿扰」开关
     */
    fun setClassDndEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CLASS_DND, enabled) }
    }

    /**
     * 获取上课时启用的勿扰档位。
     * 0 = 勿扰模式 (DND, INTERRUPTION_FILTER_NONE) —— 完全屏蔽通知、来电、振动
     * 1 = 静音模式 (SILENT, AudioManager.RINGER_MODE_SILENT) —— 关铃声与振动，通知照常弹出
     * 2 = 优先模式 (PRIORITY, INTERRUPTION_FILTER_PRIORITY) —— 屏蔽普通通知，但闹钟等优先事项仍响
     * 默认 1 = 静音模式：不影响上课时正常查看通知，符合大多数人期望。
     */
    fun getClassDndMode(): Int {
        return safeGetInt(KEY_CLASS_DND_MODE, 1)
    }

    /**
     * 设置上课时启用的勿扰档位（0=勿扰, 1=静音, 2=优先）
     */
    fun setClassDndMode(mode: Int) {
        prefs.edit { putInt(KEY_CLASS_DND_MODE, mode) }
    }

    /**
     * 获取指定星期和节次范围已占用的周次列表
     * 占用判断为分钟级时间重叠：自定义时间课程按其实际起止时间参与占用；
     * 时间无法确定时回退到节次重叠判断。
     *
     * @param dayOfWeek 星期几
     * @param startSection 开始节次
     * @param endSection 结束节次
     * @param excludeIds 需要排除的课程ID（编辑时排除自身）
     * @param startTime 自定义开始时间（"HH:mm"，仅自定义时间课程传入）
     * @param endTime 自定义结束时间（"HH:mm"，仅自定义时间课程传入）
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

        // 无排除条件且非自定义时间时存入缓存
        if (excludeIds.isEmpty() && startTime == null && endTime == null) {
            occupiedWeeksCache[occupiedWeeksKey(dayOfWeek, startSection, endSection)] = occupied
        }

        return occupied
    }

    /**
     * 占用周次的缓存键。
     * 必须带上课表 ID：课程数据与节次时间都随课表变化，
     * 仅用 "天_起节_止节" 会在切换课表后命中另一课表的缓存。
     */
    private fun occupiedWeeksKey(dayOfWeek: Int, startSection: Int, endSection: Int): String =
        "${getCurrentScheduleId()}_${dayOfWeek}_${startSection}_${endSection}"

    /**
     * 将 "HH:mm" 时间字符串转换为分钟数（自当日 00:00 起），无法解析时返回 null
     */
    private fun timeToMinutes(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    /**
     * 获取全局绝对节次号 -> "HH:mm-HH:mm" 的扁平映射
     * 上午保持原编号，下午偏移上午节数，晚上偏移上午+下午节数
     */
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

    /**
     * 将课程的周次加入集合
     */
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
     * 判断待添加课程与已存在课程是否在时间上冲突（分钟级）
     *
     * @param newStartMin 待添加课程开始分钟数（无法确定时为 null）
     * @param newEndMin 待添加课程结束分钟数（无法确定时为 null）
     * @param newStartSection 待添加课程开始节次（用于时间无法确定时的回退判断）
     * @param newEndSection 待添加课程结束节次
     * @param existing 已存在课程
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
        // 双方时间均可确定时使用分钟级重叠判断（相邻不重叠，即 end == start 不算冲突）
        if (newStartMin != null && newEndMin != null && existingStart != null && existingEnd != null) {
            return newStartMin < existingEnd && existingStart < newEndMin
        }
        // 时间无法确定时回退到节次重叠判断
        return existing.startSection <= newEndSection && existing.endSection >= newStartSection
    }

    /**
     * 获取指定时间段的所有课程（用于显示多课程详情）
     */
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

    /**
     * 获取所有课表名称
     */
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

    /**
     * 保存课表名称列表
     */
    internal fun saveScheduleNames(names: List<String>) {
        val json = gson.toJson(names)
        prefs.edit(commit = true) { putString(KEY_SCHEDULE_NAMES, json) }
        // 课表列表变化会让"当前课表 ID 是否仍有效"的结论失效
        scheduleNamesCache = names
        currentScheduleIdCache = null
    }

    /**
     * 获取当前选中的课表ID
     */
    fun getCurrentScheduleId(): String {
        currentScheduleIdCache?.let { return it }
        val saved = prefs.getString(KEY_CURRENT_SCHEDULE_ID, "默认课表") ?: "默认课表"
        // 如果当前课表不在课表列表中，回退到第一个课表
        val names = getScheduleNames()
        val resolved = if (saved in names) saved else names.first()
        currentScheduleIdCache = resolved
        return resolved
    }

    /**
     * 设置当前选中的课表ID
     */
    fun setCurrentScheduleId(scheduleId: String) {
        prefs.edit { putString(KEY_CURRENT_SCHEDULE_ID, scheduleId) }
        currentScheduleIdCache = scheduleId
        // 课表切换后，占用周次与全局节次时间都基于"当前课表"计算，必须失效
        invalidateTimeCaches()
        notifyCourseChanged("settings")
    }

    /**
     * 添加新课表
     */
    fun addSchedule(name: String): List<String> {
        val names = getScheduleNames().toMutableList()
        if (name !in names) {
            names.add(name)
            saveScheduleNames(names)
        }
        notifyCourseChanged("settings")
        return names
    }

    /**
     * 为指定课表新建一个默认(4/4/4)专属时间配置并绑定，配置名跟随课表名。
     * 用于手动新建课表时自动生成独立时间ID，避免多个课表共享同一份默认配置。
     */
    fun createDefaultTimeConfigForSchedule(name: String) {
        // 仅当课表已有“显式绑定”的时间配置记录时才跳过；不能用 getScheduleTimeConfigId(name)，
        // 因为它对未绑定课表会回退返回第一个已有配置的 id（非 0），导致 new 课表被误判为“已绑定”而漏建独立配置
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

    /**
     * 创建新学期课表：复制当前课表的所有设置（不含课程数据）到新课表
     */
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
                    // 跳过课程数据，只复制设置
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
            // 复制时间配置绑定关系
            val currentTimeConfigId = prefs.getLong("$SCHEDULE_TIME_CONFIG_PREFIX$currentId", 0L)
            if (currentTimeConfigId != 0L) {
                putLong("$SCHEDULE_TIME_CONFIG_PREFIX$name", currentTimeConfigId)
            }
            // 将开始上课日期设为今天，当前周数设为第1周
            val today = LocalDate.now()
            val todayStr =
                String.format(java.util.Locale.ROOT, "%04d/%02d/%02d", today.year, today.monthValue, today.dayOfMonth)
            putString("$newPrefix$KEY_CLASS_START_TIME", todayStr)
            putInt("$newPrefix$KEY_CURRENT_WEEK", 1)
        }
        return names
    }

    /**
     * 删除课表
     */
    fun deleteSchedule(name: String): List<String> {
        val names = getScheduleNames().toMutableList()
        names.remove(name)
        // 如果删除后没有课表了，自动创建"默认课表"避免应用无法启动
        if (names.isEmpty()) {
            names.add("默认课表")
        }
        saveScheduleNames(names)
        // 删除该课表的所有数据
        val prefix = "$SCHEDULE_KEY_PREFIX${name}_"
        prefs.edit {
            for (key in prefs.all.keys) {
                if (key.startsWith(prefix)) {
                    remove(key)
                }
            }
        }
        // 直接改写了 prefs：该课表的课程缓存与时间配置缓存全部失效，
        // 否则新建同名课表时会读到已删除的旧数据
        invalidateAllCaches()
        // 如果删除的是当前课表，切换到第一个课表
        if (getCurrentScheduleId() == name) {
            setCurrentScheduleId(names.first())
        }
        notifyCourseChanged("settings")
        return names
    }

    /**
     * 重命名课表
     */
    fun renameSchedule(oldName: String, newName: String): List<String> {
        val names = getScheduleNames().toMutableList()
        val index = names.indexOf(oldName)
        if (index != -1) {
            // 先检查并更新当前课表ID，再修改课表名称列表
            val savedCurrentId = prefs.getString(KEY_CURRENT_SCHEDULE_ID, "默认课表") ?: "默认课表"
            if (savedCurrentId == oldName) {
                setCurrentScheduleId(newName)
            }
            names[index] = newName
            saveScheduleNames(names)
            // 迁移 SharedPreferences 中所有 schedule_{oldName}_* 键到 schedule_{newName}_*
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
                                // 如果是课程数据，更新其中的 scheduleId 字段
                                if (suffix == KEY_COURSES) {
                                    val type = object : TypeToken<List<Course>>() {}.type
                                    try {
                                        val courses: List<Course> = gson.fromJson(value, type) ?: emptyList()
                                        val updated = courses.map { it.copy(scheduleId = newName) }
                                        putString(newKey, gson.toJson(updated))
                                    } catch (_: Exception) {
                                        putString(newKey, value)
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
            // 课表重命名改写了 schedule_* 键前缀并可能改写当前课表 ID，
            // 旧的课程缓存、课表 ID 缓存、时间配置绑定缓存一律失效
            invalidateAllCaches()
        }
        notifyCourseChanged("settings")
        return names
    }

    /**
     * 获取指定课表绑定的时间配置 ID
     */
    fun getScheduleTimeConfigId(scheduleId: String): Long {
        val id = prefs.getLong("$SCHEDULE_TIME_CONFIG_PREFIX$scheduleId", 0L)
        // 如果 ID 不在有效列表中，返回第一个有效配置的 ID
        if (id != 0L && id in getTimeConfigIds()) {
            return id
        }
        // 返回第一个可用配置的 ID
        val firstId = getTimeConfigIds().firstOrNull()
        return firstId ?: 0L
    }

    /**
     * 设置指定课表绑定的时间配置 ID
     */
    fun setScheduleTimeConfigId(scheduleId: String, timeConfigId: Long) {
        prefs.edit { putLong("$SCHEDULE_TIME_CONFIG_PREFIX$scheduleId", timeConfigId) }
        // 绑定关系变化后该课表读到的节数/节次时间会随之改变
        invalidateTimeCaches()
    }

    /**
     * 切换到指定课表
     */
    fun switchToSchedule(scheduleId: String) {
        setCurrentScheduleId(scheduleId)
        // 切换到该课表绑定的时间配置
        val timeConfigId = getScheduleTimeConfigId(scheduleId)
        if (timeConfigId != 0L) {
            // 有绑定的时间配置，直接应用
            val config = getTimeConfig(timeConfigId)
            setCurrentTimeConfigId(timeConfigId)
            applyTimeConfigToSchedule(config)
        } else if (getTimeConfigIds().isNotEmpty()) {
            // 没有绑定的时间配置，使用第一个时间配置并绑定
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

    // --- 多搭配支持 ---

    /** 获取所有搭配 ID 列表（按创建顺序） */
    fun getCombinationIds(): List<Long> {
        val idsStr = prefs.getString(KEY_COMBINATION_IDS, null) ?: return listOf(0L)
        return idsStr.split(",").mapNotNull { it.toLongOrNull() }
    }

    /** 获取当前选中的搭配 ID */
    fun getCurrentCombinationId(): Long {
        return prefs.getLong(KEY_CURRENT_COMBINATION_ID, 0L)
    }

    /** 设置当前选中的搭配 ID */
    fun setCurrentCombinationId(id: Long) {
        prefs.edit { putLong(KEY_CURRENT_COMBINATION_ID, id) }
    }

    // --- 批量写入支持 ---

    /**
     * 批量写入中复用的 Editor；非批量时为 null，各写入点独立提交。
     *
     * 存在意义：保存一次搭配会连续调用 16 个 save*，原本就是 16 次独立磁盘事务，
     * 用 [batchEdit] 包裹后合并为一次提交。
     */
    private var pendingEditor: SharedPreferences.Editor? = null

    /** 所有写入的统一入口：批量模式下复用 Editor，否则独立提交 */
    private fun edit(commit: Boolean = false, block: SharedPreferences.Editor.() -> Unit) {
        val pending = pendingEditor
        if (pending != null) {
            block(pending)
        } else {
            prefs.edit(commit = commit) { block(this) }
        }
    }

    /**
     * 把块内所有 prefs 写入合并为一次提交。可嵌套（内层直接复用外层 Editor）。
     */
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

    // --- 搭配外观：17 个分散键收敛为单个 JSON ---

    /**
     * 读取搭配外观快照（带缓存）。
     * 旧版本散落在 `comb_xxx_{id}` 的 17 个键在首次读取时自动迁移为单个 JSON。
     */
    private fun getCombinationStyle(id: Long): CombinationStyle {
        combinationStyleCache[id]?.let { return it }
        val json = prefs.getString("$COMBINATION_STYLE_PREFIX$id", null)
        val style = if (json != null) {
            runCatching { gson.fromJson(json, CombinationStyle::class.java) }.getOrNull()
                ?: CombinationStyle()
        } else {
            readLegacyCombinationStyle(id).also { saveCombinationStyle(id, it) }
        }
        combinationStyleCache[id] = style
        return style
    }

    private fun saveCombinationStyle(id: Long, style: CombinationStyle) {
        edit { putString("$COMBINATION_STYLE_PREFIX$id", gson.toJson(style)) }
        combinationStyleCache[id] = style
    }

    /** 在现有快照基础上做一次变更并落盘 */
    private fun updateCombinationStyle(id: Long, transform: (CombinationStyle) -> CombinationStyle) {
        saveCombinationStyle(id, transform(getCombinationStyle(id)))
    }

    /**
     * 从旧版 17 个分散键读取外观参数（迁移专用）。
     * 每个默认值都与迁移前对应 getter 保持一致，确保升级前后行为不变。
     */
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

    /** 保存指定搭配的壁纸图片（缩放到屏幕分辨率并以 WebP 有损格式存储） */
    fun saveCombinationWallpaper(id: Long, bitmap: android.graphics.Bitmap): Boolean {
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            // 超出屏幕分辨率时先等比缩小，避免存储超大图占用磁盘与内存
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
                // WebP 有损，质量 80，同画质下体积比 PNG 小 70%+
                scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, out)
            }
            // 同步更新内存缓存，避免下次读取再用旧图重新解码
            wallpaperCache.put(id, scaled)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 清除指定搭配的壁纸：删除磁盘文件并移除内存缓存（配合内存中 bitmap=null 实现"清除壁纸"持久化） */
    fun clearCombinationWallpaper(id: Long) {
        wallpaperCache.remove(id)
        val webpFile = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.webp")
        if (webpFile.exists()) webpFile.delete()
        val pngFile = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.png")
        if (pngFile.exists()) pngFile.delete()
    }

    /** 加载指定搭配的壁纸图片（带内存缓存，命中时零延迟；解码时按屏幕分辨率降采样） */
    fun loadCombinationWallpaper(id: Long): android.graphics.Bitmap? {
        // 1. 缓存命中：直接返回，避免重复解码
        wallpaperCache.get(id)?.let { return it }
        // 2. 缓存未命中：从磁盘解码（优先 webp，兼容旧版 png）
        val webpFile = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.webp")
        val file = if (webpFile.exists()) webpFile
        else java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.png")
        if (!file.exists()) return null
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            // 先读尺寸，再按屏幕分辨率计算降采样倍数，避免超大图一次性解码耗尽内存
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

    /** 保存指定搭配的偏移和缩放 */
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

    fun getCombinationCardHeight(id: Long): Float = getCombinationStyle(id).cardHeight

    fun saveCombinationCardCornerRadius(id: Long, cornerRadius: Float) =
        updateCombinationStyle(id) { it.copy(cardCornerRadius = cornerRadius) }

    fun getCombinationCardCornerRadius(id: Long): Float = getCombinationStyle(id).cardCornerRadius

    fun saveCombinationWallpaperBrightness(id: Long, brightness: Float) =
        updateCombinationStyle(id) { it.copy(wallpaperBrightness = brightness) }

    fun getCombinationWallpaperBrightness(id: Long): Float = getCombinationStyle(id).wallpaperBrightness

    fun saveCombinationWallpaperIsLight(id: Long, isLight: Boolean?) =
        updateCombinationStyle(id) { it.copy(wallpaperIsLight = isLight) }

    fun getCombinationWallpaperIsLight(id: Long): Boolean? = getCombinationStyle(id).wallpaperIsLight

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

    fun getCombinationCardTextScale(id: Long): Float = getCombinationStyle(id).cardTextScale

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

    /** 获取当前选中的时间配置 ID */
    fun getCurrentTimeConfigId(): Long {
        val scheduleId = getCurrentScheduleId()
        // 优先使用课表绑定的时间配置
        val boundKey = "$SCHEDULE_TIME_CONFIG_PREFIX$scheduleId"
        val bound = prefs.getLong(boundKey, 0L)
        if (prefs.contains(boundKey) && bound in getTimeConfigIds()) {
            return bound
        }
        // 课表从未绑定（升级自旧版本：旧版只维护单一"当前时间配置"全局指针）：
        // 回退到旧全局指针指向的有效配置，避免升级后节数/时间被重置为默认；
        // 顺手把解析结果补绑到该课表，避免后续重复回退
        val resolved = resolveLegacyCurrentTimeConfigId()
        setScheduleTimeConfigId(scheduleId, resolved)
        return resolved
    }

    /** 升级兼容：优先沿用旧版单一"当前时间配置"全局指针，无效时取第一个可用配置 */
    private fun resolveLegacyCurrentTimeConfigId(): Long {
        val legacy = prefs.getLong(KEY_CURRENT_TIME_CONFIG_ID, 0L)
        if (legacy in getTimeConfigIds()) return legacy
        return getTimeConfigIds().firstOrNull() ?: 0L
    }

    /** 设置当前选中的时间配置 ID */
    fun setCurrentTimeConfigId(id: Long) {
        prefs.edit { putLong(KEY_CURRENT_TIME_CONFIG_ID, id) }
    }

    /** 获取指定 ID 的时间配置 */
    fun getTimeConfig(id: Long): TimeConfig {
        timeConfigCache[id]?.let { return it }
        val key = "$TIME_CONFIG_PREFIX$id"
        val json = prefs.getString(key, null)
        if (json.isNullOrEmpty()) {
            // 没有找到配置，返回默认配置
            return TimeConfig(id = id, name = "默认配置")
        }
        val config = try {
            val parsed = gson.fromJson(json, TimeConfig::class.java)
            // 验证解析结果
            parsed?.copy(id = id) ?: TimeConfig(id = id, name = "默认配置")
        } catch (_: Exception) {
            TimeConfig(id = id, name = "默认配置")
        }
        timeConfigCache[id] = config
        return config
    }

    /**
     * 保存时间配置。
     * 节数与节次时间均来自这里，因此同时失效占用周次与全局节次时间缓存。
     */
    fun saveTimeConfig(config: TimeConfig) {
        val key = "${TIME_CONFIG_PREFIX}${config.id}"
        val json = gson.toJson(config)
        prefs.edit { putString(key, json) }
        timeConfigCache[config.id] = config
        invalidateTimeCaches()
    }

    /** 添加新时间配置，返回新 ID */
    fun addTimeConfig(config: TimeConfig): Long {
        val ids = getTimeConfigIds().toMutableList()
        val newId = (ids.maxOrNull() ?: -1L) + 1L
        val newConfig = config.copy(id = newId)
        ids.add(newId)
        saveTimeConfigIds(ids)
        saveTimeConfig(newConfig)
        return newId
    }

    /** 删除指定时间配置 */
    fun deleteTimeConfig(id: Long) {
        val ids = getTimeConfigIds().toMutableList()
        if (!ids.remove(id)) return
        saveTimeConfigIds(ids)
        prefs.edit { remove("${TIME_CONFIG_PREFIX}$id") }
        timeConfigCache.remove(id)
        // 若删除的是当前配置，且仍有其他配置，则切换到第一个
        if (ids.isNotEmpty() && getCurrentTimeConfigId() == id) {
            setCurrentTimeConfigId(ids.first())
        } else if (ids.isEmpty()) {
            // 删光后重新创建一个默认配置 id=0
            saveTimeConfigIds(listOf(0L))
            setCurrentTimeConfigId(0L)
        }
    }

    /** 获取当前时间配置 */
    fun getCurrentTimeConfig(): TimeConfig {
        return getTimeConfig(getCurrentTimeConfigId())
    }

    /** 获取当前时间配置的自定义节次名称（全局绝对节次号 -> 名称） */
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

    /** 切换到指定时间配置 */
    fun switchToTimeConfig(id: Long) {
        val config = getTimeConfig(id)
        // 节数变化时保持课程的时段相对位置，避免课程随绝对节次整体平移
        remapCoursesForNewSectionCounts(
            config.morningSections, config.afternoonSections, config.eveningSections
        )
        setCurrentTimeConfigId(id)
        // 更新当前课表绑定的时间配置
        setScheduleTimeConfigId(getCurrentScheduleId(), id)
        // 将配置中的值应用到当前课表的设置
        applyTimeConfigToSchedule(config)
        notifyCourseChanged("settings")
    }

    /**
     * 教务/AI 等"软导入"：把导入的节数与节次时间就地覆盖到当前课表绑定的时间配置对象，
     * 使绑定配置与课表设置保持一致，避免此后切换/重应用时被旧配置盖回。
     * 若当前课表仅回退到共享默认配置(id0)，则为其新建专属配置并绑定，以免影响其他课表。
     */
    fun applyTimeImportToCurrentSchedule(
        morningSections: Int, afternoonSections: Int, eveningSections: Int,
        morningTimes: Map<Int, String>, afternoonTimes: Map<Int, String>, eveningTimes: Map<Int, String>
    ) = applyTimeImportToSchedule(
        getCurrentScheduleId(), morningSections, afternoonSections, eveningSections,
        morningTimes, afternoonTimes, eveningTimes
    )

    /**
     * 教务/AI 等"软导入"：把导入的节数与节次时间就地覆盖到指定课表绑定的时间配置对象，
     * 使绑定配置与课表设置保持一致。若目标课表仅回退到共享默认配置(id0)，
     * 则为其新建专属配置并绑定，以免影响其他课表。
     */
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
     * 节次数量变化时，保持每门课在原时段内的相对位置不变，重映射当前课表的课程。
     * 相对位置不向上钳制，因此缩小节数后会暂时落在新网格之外、恢复节数后自动回到原位。
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
     * 将单个节次号从旧节数映射到新节数，保持所在时段（上午/下午/晚上）及时段内相对位置不变。
     * 相对位置不向上钳制：若新时段节数变少，课程会暂时落在新网格之外（不显示），恢复节数后自动回到原位。
     */
    private fun remapSection(
        section: Int,
        oldMorning: Int, oldAfternoon: Int,
        newMorning: Int, newAfternoon: Int
    ): Int {
        // 旧配置下的时段起始节与时段编号（0 上午 / 1 下午 / 2 晚上）
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
        // 新配置下该时段的起始节
        val newStart = when (period) {
            0 -> 1
            1 -> newMorning + 1
            else -> newMorning + newAfternoon + 1
        }
        // 不向上钳制，保留原相对位置；新时段节数不足时课程暂落在网格外，恢复节数后回归原位
        return newStart + relative
    }

    /**
     * 按 "period_index" 取出某一时段的节次时间（相对节次号 -> "HH:mm-HH:mm"）
     */
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
     * 将时间配置应用到当前课表的设置。
     *
     * 旧实现逐个调用 20+ 个 setter，造成 20+ 次磁盘提交、20+ 次缓存清空与 20+ 次 UI 回调；
     * 且 savePeriodTimes() 内部会把 quickTimeEnabled 重置为 false，
     * 导致前面刚写入的快速时间开关被静默覆盖（切换时间配置后快速时间总是被关掉）。
     * 这里改为一次性构造完整配置后单次写入，并修正开关被覆盖的问题。
     */
    private fun applyTimeConfigToSchedule(config: TimeConfig) {
        val scheduleId = getCurrentScheduleId()
        val configId = getScheduleTimeConfigId(scheduleId)
        val base = getTimeConfig(configId)

        // 节次时间：配置为空时整体回退到默认时段时间；
        // 配置非空但某时段缺失时，保留该时段原有值（与旧 savePeriodTimes 行为一致）
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
                    // 与原 4x2 版本行为一致：应用配置时快速时间开关保持关闭
                    // （旧实现中 savePeriodTimes 会把它重置为 false，这里显式保持该结果）
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

    /**
     * 写入旧版遗留的影子 prefs（schedule_{课表}_*）。
     *
     * 这些键在引入 TimeConfig 后已无读取方：节数/节次时间统一从 TimeConfig 读，
     * 快速时间相关 getter 在仓库内更是零调用；保留仅因 exportAllPreferences 会导出它们，
     * 用于旧版本回滚。此处把原先分散在 17 个 setter 中的写入合并为一次提交。
     */
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

    /** 迁移：如果只有旧的单时间配置数据（无 time_config_ids），将其作为 id=0 的配置 */
    fun migrateToTimeConfigsIfNeeded() {
        if (prefs.contains(KEY_TIME_CONFIG_IDS)) return
        // 首次迁移：将现有时间设置作为 id=0 的配置
        val currentConfig = TimeConfig.fromRepository(this).copy(id = 0L, name = "默认配置")
        prefs.edit {
            putString(KEY_TIME_CONFIG_IDS, "0")
            putLong(KEY_CURRENT_TIME_CONFIG_ID, 0L)
        }
        saveTimeConfig(currentConfig)
    }

    /**
     * 导出所有课表相关的 SharedPreferences 数据（用于云备份）
     * 返回所有 schedule_* 前缀和全局课表配置的键值对
     */
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

    /**
     * 从备份数据恢复所有课表配置
     */
    fun importAllPreferences(data: Map<String, Any>) {
        prefs.edit {
            // 先清除所有旧的课表数据
            for ((key) in prefs.all) {
                if (key.startsWith(SCHEDULE_KEY_PREFIX) || key.startsWith(TIME_CONFIG_PREFIX) ||
                    key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX)) {
                    remove(key)
                }
            }
            // 清除全局课表配置
            remove(KEY_SCHEDULE_NAMES)
            remove(KEY_CURRENT_SCHEDULE_ID)
            remove(KEY_SHIFT_MODE)
            remove(KEY_SHIFT_SELECTED_SCHEDULES)
            remove(KEY_TIME_CONFIG_IDS)
            remove(KEY_CURRENT_TIME_CONFIG_ID)

            // 写入备份数据
            for ((key, value) in data) {
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Number -> {
                        val numVal = value.toDouble()
                        val longVal = numVal.toLong()
                        val intVal = numVal.toInt()
                        // Long 类型的键：时间配置 ID、课表绑定的时间配置 ID、课表组合 ID、时间戳
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
        // 备份数据直接覆盖了 prefs，所有内存缓存一律失效
        invalidateAllCaches()
        onCourseChanged?.invoke("restore", "")
    }

    fun getSectionsForSchedule(scheduleId: String): Triple<Int, Int, Int> {
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        return Triple(config.morningSections, config.afternoonSections, config.eveningSections)
    }

    /**
     * 导入单个课表的备份数据
     * @param scheduleName 课表名称
     * @param coursesData 课程数据列表
     * @param timeConfigData 时间配置数据（可选）
     */
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
            @Suppress("UNCHECKED_CAST")
            val selectedWeeks = (courseMap["selectedWeeks"] as? List<Number>)?.map { it.toInt() } ?: emptyList()

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
                startWeek = selectedWeeks.minOrNull() ?: 1,
                endWeek = selectedWeeks.maxOrNull() ?: 20,
                weekType = 0,
                colorRes = color,
                selectedWeeks = selectedWeeks
            )
        }

        val key = "${prefix}$KEY_COURSES"
        val json = gson.toJson(courses)
        prefs.edit { putString(key, json) }
        // 直接写入了课程数据，缓存失效以确保 UI 读到新数据
        invalidateAllCaches()

        // 为新课表创建时间配置并绑定
        val existingConfigId = getScheduleTimeConfigId(scheduleName)
        if (existingConfigId == 0L) {
            val newConfig = if (timeConfigData != null) {
                // 从导入数据创建时间配置
                val sectionTimesMap = mutableMapOf<String, String>()
                @Suppress("UNCHECKED_CAST")
                (timeConfigData["sectionTimes"] as? Map<String, String>)?.forEach { (k, v) ->
                    sectionTimesMap[k] = v
                }
                @Suppress("UNCHECKED_CAST")
                val importedSectionNames = (timeConfigData["sectionNames"] as? Map<String, String>) ?: emptyMap()
                TimeConfig(
                    name = scheduleName,
                    morningSections = (timeConfigData["morningSections"] as? Number)?.toInt() ?: 4,
                    afternoonSections = (timeConfigData["afternoonSections"] as? Number)?.toInt() ?: 4,
                    eveningSections = (timeConfigData["eveningSections"] as? Number)?.toInt() ?: 4,
                    sectionTimes = sectionTimesMap,
                    sectionNames = importedSectionNames
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
