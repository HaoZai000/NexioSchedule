package com.haooz.chedule.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.util.Locale

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
    private var scheduleFoldersCache: List<ScheduleFolder>? = null
    private var globalSectionTimesCache: Map<Int, String>? = null

    init {
        // 启动迁移（1.6.4 数据层重构时整个 init 块被连带删掉，这里补回）：
        // - 文件夹是 1.6.1 才有的，1.5.6 及更早上来的课表散在根目录 → 收进默认文件夹；
        // - 「一课表一配置」之后，多配置时代攒下的闲置 time_config 只在这里清。
        // 两者都只跑一次，由标记/幂等条件守门。
        migrateSchedulesIntoDefaultFolder()
        pruneOrphanTimeConfigsIfNeeded()
    }


    /**
     * 首次装这个版本（或第一次使用）时，把现有课表全部收进「默认文件夹」。
     * 只跑一次：升上来的老用户不会看到课表散在根目录，新用户也从一开始就有分组。
     *
     * 备份恢复会清掉迁移标记再跑一遍：此时可能已有同 id 的默认文件夹，
     * 必须并入而不是再 add 一个，否则 LazyColumn 的 folder key 冲突会闪退。
     */
    private fun migrateSchedulesIntoDefaultFolder() {
        if (prefs.getBoolean(KEY_DEFAULT_FOLDER_MIGRATED, false)) return
        val names = getScheduleNames()
        val folders = getScheduleFolders().toMutableList()
        val ungrouped = if (folders.isEmpty()) names
        else names.filter { name -> folders.none { name in it.schedules } }
        if (ungrouped.isNotEmpty()) {
            val existingIndex = folders.indexOfFirst { it.id == DEFAULT_FOLDER_ID }
            if (existingIndex >= 0) {
                val existing = folders[existingIndex]
                val merged = (existing.schedules + ungrouped).distinct()
                folders[existingIndex] = existing.copy(
                    schedules = names.filter { it in merged }
                )
            } else {
                folders.add(
                    ScheduleFolder(
                        id = DEFAULT_FOLDER_ID,
                        name = DEFAULT_FOLDER_NAME,
                        schedules = ungrouped
                    )
                )
            }
            saveScheduleFolders(folders)
        }
        prefs.edit(commit = true) { putBoolean(KEY_DEFAULT_FOLDER_MIGRATED, true) }
    }

    // 变更回调：多播列表，避免后构造的 ViewModel 覆盖先注册的监听
    private val courseChangedListeners =
        java.util.concurrent.CopyOnWriteArrayList<(action: String, courseId: String) -> Unit>()

    fun addCourseChangedListener(listener: (action: String, courseId: String) -> Unit) {
        if (!courseChangedListeners.contains(listener)) {
            courseChangedListeners.add(listener)
        }
    }

    fun removeCourseChangedListener(listener: (action: String, courseId: String) -> Unit) {
        courseChangedListeners.remove(listener)
    }

    private fun dispatchCourseChanged(action: String, courseId: String) {
        for (listener in courseChangedListeners) {
            try {
                listener(action, courseId)
            } catch (_: Exception) {
            }
        }
    }

    /** 绕过本类 setter 直接改写 prefs 后必须调用，否则读到陈旧缓存 */
    private fun invalidateAllCaches() {
        courseCache.clear()
        occupiedWeeksCache.clear()
        timeConfigCache.clear()
        timeConfigIdsCache = null
        currentScheduleIdCache = null
        scheduleNamesCache = null
        scheduleFoldersCache = null
        globalSectionTimesCache = null
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
            commitSettingsChanged(courseId)
            return
        }
        dispatchCourseChanged(action, courseId)
    }

    /**
     * 课程数据（增删改/调课/交换）落库后，通知所有监听的 ViewModel 重新加载。
     */
    fun notifyCoursesBulkChanged() {
        dispatchCourseChanged("bulk", "")
    }

    /** 更新时间戳（本地修改不被远程覆盖）、失效时间缓存、通知 UI */
    private fun commitSettingsChanged(changeId: String = "") {
        val prefix = getScheduleKeyPrefix()
        prefs.edit { putLong("${prefix}_settings_last_modified", System.currentTimeMillis()) }
        invalidateTimeCaches()
        dispatchCourseChanged("settings", changeId)
    }

    /**
     * 某课表设置变更落库后的时间戳/UI 通知。
     * - 始终更新**该课表**的 `settings_last_modified`（按课表分键保存，供备份还原与后续同步预留）
     * - 仅当写入的是当前课表时才失效缓存并通知 UI / 触发重排
     * - 写非当前课表时不碰当前课表时间戳，也不通知当前 UI
     */
    private fun markScheduleSettingsChanged(scheduleId: String, changeId: String = "") {
        val prefix = getScheduleKeyPrefix(scheduleId)
        prefs.edit { putLong("${prefix}_settings_last_modified", System.currentTimeMillis()) }
        if (scheduleId != getCurrentScheduleId()) return
        if (batchingSettings) return
        invalidateTimeCaches()
        dispatchCourseChanged("settings", changeId)
    }

    companion object {
        private const val TAG = "CourseRepository"

        @Volatile
        private var INSTANCE: CourseRepository? = null

        fun getInstance(context: Context): CourseRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CourseRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        // 兼容旧代码的构造方式
        operator fun invoke(context: Context): CourseRepository = getInstance(context)

        /** 开学日规范格式 yyyy/MM/dd */
        fun formatClassStartDate(date: LocalDate): String =
            String.format(Locale.ROOT, "%04d/%02d/%02d", date.year, date.monthValue, date.dayOfMonth)

        /**
         * 解析教务/设置/备份里各种开学日写法。
         * 支持：yyyy/MM/dd、yyyy-MM-dd、yyyy/M/d、yyyyMMdd、带时间的 ISO 前缀。
         * 年份限 1970..2100，避免「26/9/1」被当成公元 26 年。
         */
        fun parseFlexibleDate(raw: String?): LocalDate? {
            if (raw.isNullOrBlank()) return null
            fun validYear(y: Int) = y in 1970..2100
            val trimmed = raw.trim().substringBefore(' ').substringBefore('T')
            val sep = when {
                trimmed.contains('-') -> '-'
                trimmed.contains('/') -> '/'
                else -> null
            }
            if (sep != null) {
                val parts = trimmed.split(sep)
                if (parts.size == 3) {
                    val y = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    val d = parts[2].toIntOrNull()
                    if (y != null && m != null && d != null && validYear(y)) {
                        return runCatching { LocalDate.of(y, m, d) }.getOrNull()
                    }
                }
            }
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length == 8) {
                val y = digits.substring(0, 4).toInt()
                if (!validYear(y)) return null
                return runCatching {
                    LocalDate.of(
                        y,
                        digits.substring(4, 6).toInt(),
                        digits.substring(6, 8).toInt()
                    )
                }.getOrNull()
            }
            return null
        }

        /** 规范为 yyyy/MM/dd；无法解析返回 null */
        fun normalizeClassStartDate(raw: String?): String? =
            parseFlexibleDate(raw)?.let { formatClassStartDate(it) }

        const val MAX_TOTAL_WEEKS = 30

        private const val PREFS_NAME = "course_schedule_prefs"
        private const val KEY_COURSES = "courses"
        private const val KEY_CURRENT_WEEK = "current_week"
        private const val KEY_TOTAL_WEEKS = "total_weeks"
        private const val KEY_CLASS_START_TIME = "class_start_time"
        private const val KEY_TEACHING_WEEK_REORGANIZATIONS = "teaching_week_reorganizations"
        private const val KEY_SMART_WEEKEND = "smart_weekend"
        private const val KEY_SHOW_NON_CURRENT_WEEK = "show_non_current_week"
        private const val KEY_CURRENT_SCHEDULE_ID = "current_schedule_id"
        private const val KEY_SCHEDULE_NAMES = "schedule_names"
        private const val KEY_SCHEDULE_FOLDERS = "schedule_folders"
        /** 首次引入文件夹时的归档标记；只跑一次 */
        private const val KEY_DEFAULT_FOLDER_MIGRATED = "default_folder_migrated"
        private const val DEFAULT_FOLDER_NAME = "默认文件夹"
        private const val DEFAULT_FOLDER_ID = "folder_default"
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
        private const val SCHEDULE_KEY_PREFIX = "schedule_"
        private const val KEY_TIME_CONFIG_IDS = "time_config_ids"
        private const val KEY_CURRENT_TIME_CONFIG_ID = "current_time_config_id"
        private const val TIME_CONFIG_PREFIX = "time_config_"
        // 不匹配 schedule_{name}_ 前缀，删/迁课表时需单独处理
        private const val SCHEDULE_TIME_CONFIG_PREFIX = "schedule_time_config_"

        /**
         * 小组件 padding 档位的出厂默认档，仅在用户从未手动选过时生效：
         * 小米系（HyperOS 桌面按 4×6 规格绘制小部件）默认 4×6，其余机型默认标准档。
         * 用户一旦选择就落盘，之后不再随设备判断变化。
         */
        private val defaultWidgetPaddingMode: Int = run {
            val xiaomiBrands = setOf("xiaomi", "redmi", "poco")
            val isXiaomi = Build.BRAND.lowercase(Locale.ROOT) in xiaomiBrands ||
                Build.MANUFACTURER.lowercase(Locale.ROOT) in xiaomiBrands
            if (isXiaomi) 1 else 0
        }
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
    // USELESS_ELVIS / ELVIS_ALWAYS_NULL：字段声明为非空 String，但 UnsafeAllocator 真的会给 null，
    // 这里的 ?: 是自愈数据的关键，不能删。与 TimeConfig.sanitize / TimeRoutine.fromRaw 同一处理。
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
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
        if (notify) dispatchCourseChanged("bulk", "")
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
     * @param targetWeek 目标位周次；调休列的 followWeek 与源周不同时为跨周移动，默认同周
     */
    fun moveCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int,
        targetWeek: Int = week
    ): List<Course> {
        val courses = getAllCourses()
        val result = moveWeekInPlace(
            courses, sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection,
            targetWeek
        ) ?: return courses
        saveCourses(result, notify = false)
        return result
    }

    /**
     * 调课-覆盖：按周删除目标位冲突课后移动。同源课不删，交给 move 合并。
     * @param week 源课所在周；@param targetWeek 目标位周次（冲突课按它删）
     */
    fun overwriteCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int,
        targetWeek: Int = week
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
            .filter { existing -> targetWeek in resolveSelectedWeeks(existing) }
            .map { it.id }
            .toList()

        val result = courses.toMutableList()
        for (id in conflictIds) {
            val idx = result.indexOfFirst { it.id == id }
            if (idx == -1) continue
            val updated = removeWeekFrom(result[idx], targetWeek, resetWeekType = true)
            if (updated == null) {
                result.removeAt(idx)
            } else {
                result[idx] = updated
            }
        }
        // 先落盘中间结果，避免 moveCourseForWeek 重读旧数据
        saveCourses(result, notify = false)
        return moveCourseForWeek(
            sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection, targetWeek
        )
    }

    /** 调课-交换：双方各拆出该周实例互换；与对方原位同源时同样走合并 */
    fun swapCoursesForWeek(
        sourceCourseId: String,
        targetCourseId: String,
        week: Int,
        targetWeek: Int = week
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
        if (week !in srcWeeks || targetWeek !in tgtWeeks) return courses

        // 同源课（同名/同教室/同教师）在双方都在的该周互换：位置换了、内容一样，视觉不变 → 跳过。
        // 注意必须再比一次节次跨度：占 2 节的课和占 1 节的同名课互换是有实际效果的，不能跳过
        val srcAtTarget = src.copy(
            dayOfWeek = tgtPos.first,
            startSection = tgtPos.second,
            endSection = tgtPos.third
        )
        val sameSpan =
            (src.endSection - src.startSection) == (tgt.endSection - tgt.startSection)
        if (sameSpan && isSameCourseIdentity(srcAtTarget, tgt)) return courses

        var result: MutableList<Course> = courses
        moveWeekInPlace(
            result, src.id, week, tgtPos.first, tgtPos.second, tgtPos.third, targetWeek
        )?.let { result = it }
        // 第一步后源可能已拆分，src.id 仍在原课程（已移除该周）
        moveWeekInPlace(
            result, tgt.id, targetWeek, srcPos.first, srcPos.second, srcPos.third, week
        )?.let { result = it }

        saveCourses(result, notify = false)
        return result
    }

    /**
     * 按周移动的拆分+合并（原地、不落盘）。
     * @param targetWeek 目标位周次；默认与 week 相同 = 同周内移动。调休列的 followWeek
     * 与源周不同时走跨周：源课剔除 week、目标位写入 targetWeek
     * @return 新列表；无需改动时返回 null，调用方跳过落盘
     */
    private fun moveWeekInPlace(
        courses: List<Course>,
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int,
        targetWeek: Int = week
    ): MutableList<Course>? {
        val result = courses.toMutableList()
        val sourceIdx = result.indexOfFirst { it.id == sourceCourseId }
        if (sourceIdx == -1) return null
        val source = result[sourceIdx]

        val samePosition =
            source.dayOfWeek == targetDayOfWeek &&
                source.startSection == targetStartSection &&
                source.endSection == targetEndSection
        // 同位置同周 = 无事可做
        if (samePosition && targetWeek == week) return null

        val currentSelectedWeeks = resolveSelectedWeeks(source)
        if (week !in currentSelectedWeeks) return null

        if (samePosition) {
            // 目标周本来就有这节课：挪过去是同一格同一内容，净效果只是让源周凭空少一节
            // （用户视角 = 拖了一下课没了）→ 判为无事可做，不落盘
            if (targetWeek in currentSelectedWeeks) return null
            // 同位置跨周（调休列：同一天、不同周次）→ 只挪周次，
            // 不拆成两条同槽记录（拆了也只是并回来，平白多一条）
            val newWeeks = (currentSelectedWeeks.filter { it != week } + targetWeek)
                .distinct().sorted()
            result[sourceIdx] = source.copy(
                selectedWeeks = newWeeks,
                startWeek = newWeeks.min(),
                endWeek = newWeeks.max(),
                weekType = Course.WEEK_TYPE_ALL,
                lastModified = System.currentTimeMillis()
            )
            return result
        }

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
            mergeWeeks.add(targetWeek)
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
            // 源课程只在该周有效，直接改位置；跨周时周次一并换到目标周
            result[sourceIdx] = source.copy(
                dayOfWeek = targetDayOfWeek,
                startSection = targetStartSection,
                endSection = targetEndSection,
                selectedWeeks = if (targetWeek != week) listOf(targetWeek) else source.selectedWeeks,
                startWeek = if (targetWeek != week) targetWeek else source.startWeek,
                endWeek = if (targetWeek != week) targetWeek else source.endWeek,
                weekType = if (targetWeek != week) Course.WEEK_TYPE_ALL else source.weekType,
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
                selectedWeeks = listOf(targetWeek),
                startWeek = targetWeek,
                endWeek = targetWeek,
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
        return getCurrentWeek(getCurrentScheduleId())
    }

    fun getCurrentWeek(scheduleId: String): Int {
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_CURRENT_WEEK"
        return safeGetInt(key, 1)
    }

    /** Background readers cannot assume the persisted UI week was refreshed after midnight. */
    fun getLiveTeachingWeek(date: LocalDate = LocalDate.now(), scheduleId: String = getCurrentScheduleId()): Int =
        teachingWeekPositionForDate(date, scheduleId).week
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    fun setCurrentWeek(week: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_CURRENT_WEEK"
        prefs.edit { putInt(key, week) }
        notifyCourseChanged("settings")
    }

    fun getTotalWeeks(): Int {
        return getTotalWeeks(getCurrentScheduleId())
    }

    fun getTotalWeeks(scheduleId: String): Int {
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_TOTAL_WEEKS"
        return safeGetInt(key, 20).coerceIn(1, MAX_TOTAL_WEEKS)
    }

    fun setTotalWeeks(weeks: Int) {
        setTotalWeeks(getCurrentScheduleId(), weeks)
    }

    /** 写目标课表总周数；更新该课表同步时间戳，仅当前课表才通知 UI */
    fun setTotalWeeks(scheduleId: String, weeks: Int) {
        if (weeks !in 1..MAX_TOTAL_WEEKS) return
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_TOTAL_WEEKS"
        prefs.edit { putInt(key, weeks) }
        markScheduleSettingsChanged(scheduleId)
    }

    fun getTeachingWeekReorganizations(
        scheduleId: String = getCurrentScheduleId(),
    ): List<TeachingWeekReorganizationRule> {
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_TEACHING_WEEK_REORGANIZATIONS"
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            // Keep a syntactically valid saved rule visible if total_weeks was later reduced;
            // the editor can then explain/fix it instead of silently hiding the user's data.
            TeachingWeekReorganization.decode(raw, Int.MAX_VALUE)
        }.getOrDefault(emptyList())
    }

    /** Validate the entire ruleset before one atomic preference write. */
    fun setTeachingWeekReorganizations(
        rules: List<TeachingWeekReorganizationRule>,
        scheduleId: String = getCurrentScheduleId(),
        allowExistingOutOfRangeRules: Boolean = false,
        changedRule: TeachingWeekReorganizationRule? = null,
        preserveOutOfRangeRules: Boolean = false,
    ): Boolean {
        val totalWeeks = getTotalWeeks(scheduleId)
        val validationError = if (preserveOutOfRangeRules) {
            TeachingWeekReorganization.validationError(rules, Int.MAX_VALUE)
        } else if (allowExistingOutOfRangeRules) {
            TeachingWeekReorganization.validationErrorForRuleChange(
                updatedRules = rules,
                existingRules = getTeachingWeekReorganizations(scheduleId),
                changedRule = changedRule ?: return false,
                totalWeeks = totalWeeks,
            )
        } else {
            TeachingWeekReorganization.validationError(rules, totalWeeks)
        }
        if (validationError != null) {
            return false
        }
        val normalized = rules.sortedBy { it.firstOriginalWeek }
        val raw = runCatching {
            TeachingWeekReorganization.encode(
                normalized,
                if (allowExistingOutOfRangeRules || preserveOutOfRangeRules) Int.MAX_VALUE
                else totalWeeks,
            )
        }.getOrNull() ?: return false
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_TEACHING_WEEK_REORGANIZATIONS"
        if (prefs.getString(key, null) == raw) return true
        prefs.edit { putString(key, raw) }
        markScheduleSettingsChanged(scheduleId, "teaching_week_reorganizations")
        return true
    }

    /** Deleting a rule remains possible after the semester week limit was reduced. */
    fun removeTeachingWeekReorganization(
        rule: TeachingWeekReorganizationRule,
        scheduleId: String = getCurrentScheduleId(),
    ): Boolean {
        val rules = getTeachingWeekReorganizations(scheduleId).toMutableList()
        val index = rules.indexOf(rule)
        if (index < 0) return false
        rules.removeAt(index)
        if (TeachingWeekReorganization.validationError(rules, Int.MAX_VALUE) != null) return false
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_TEACHING_WEEK_REORGANIZATIONS"
        val raw = runCatching { TeachingWeekReorganization.encode(rules, Int.MAX_VALUE) }
            .getOrNull() ?: return false
        prefs.edit { putString(key, raw) }
        markScheduleSettingsChanged(scheduleId, "teaching_week_reorganizations")
        return true
    }

    fun teachingWeekPositionForDate(
        date: LocalDate,
        scheduleId: String = getCurrentScheduleId(),
    ): TeachingWeekPosition {
        val startDate = LocalDate.parse(getClassStartTime(scheduleId).replace("/", "-"))
        return TeachingWeekReorganization.mapDate(
            semesterStartDate = startDate,
            date = date,
            rules = getTeachingWeekReorganizations(scheduleId),
        )
    }

    fun dateForTeachingWeekDay(
        teachingWeek: Int,
        weekday: Int,
        scheduleId: String = getCurrentScheduleId(),
    ): LocalDate? {
        val startDate = LocalDate.parse(getClassStartTime(scheduleId).replace("/", "-"))
        return TeachingWeekReorganization.dateForPosition(
            semesterStartDate = startDate,
            teachingWeek = teachingWeek,
            weekday = weekday,
            rules = getTeachingWeekReorganizations(scheduleId),
        )
    }

    /** 旧值不是可识别日期时回退当天并写回；兼容 yyyy-MM-dd / yyyy/M/d / yyyyMMdd */
    fun getClassStartTime(): String {
        return getClassStartTime(getCurrentScheduleId())
    }

    fun getClassStartTime(scheduleId: String): String {
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_CLASS_START_TIME"
        val cal = java.util.Calendar.getInstance()
        val default = String.format(
            Locale.ROOT, "%04d/%02d/%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        val stored = prefs.getString(key, null)
        val normalized = normalizeClassStartDate(stored)
        if (normalized != null) {
            // 教务脚本等常写入 yyyy-MM-dd：识别后就地规范化，绝不能重置成今天
            if (normalized != stored) {
                prefs.edit { putString(key, normalized) }
            }
            return normalized
        }
        prefs.edit { putString(key, default) }
        return default
    }

    fun setClassStartTime(time: String) {
        setClassStartTime(getCurrentScheduleId(), time)
    }

    /** 指定课表写入开学日；非法日期直接忽略，避免导入路径把开学日写成今天 */
    fun setClassStartTime(scheduleId: String, time: String) {
        val normalized = normalizeClassStartDate(time) ?: return
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_CLASS_START_TIME"
        prefs.edit { putString(key, normalized) }
        markScheduleSettingsChanged(scheduleId)
    }

    fun getSmartWeekend(): Boolean {
        return getSmartWeekend(getCurrentScheduleId())
    }

    fun getSmartWeekend(scheduleId: String): Boolean {
        // 兼容基线 1.5.6：旧 show_weekend 键的一次性迁移在 1.5.6 内就跑完了，这里只读现键
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_SMART_WEEKEND"
        return prefs.getBoolean(key, false)
    }

    fun setSmartWeekend(smart: Boolean) {
        val key = "${getScheduleKeyPrefix()}$KEY_SMART_WEEKEND"
        prefs.edit { putBoolean(key, smart) }
        notifyCourseChanged("settings")
    }

    /** 调休补班日也视为有课，智能周末据此决定是否显示该天 */
    fun hasCoursesOnDayInWeek(dayOfWeek: Int, week: Int): Boolean {
        if (getAllCourses().any { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week) }) return true
        return hasWorkSwapOnDay(dayOfWeek, week)
    }

    /** 调休跟随解析结果：绝对日期 + 该日期在**当前课表**下对应的 (周次, 星期) */
    data class WorkSwapFollow(val date: java.time.LocalDate, val week: Int, val weekday: Int)

    /**
     * 把调休条目的「跟随绝对日期」换算成当前课表的 (周次, 星期)。
     *
     * 关键点：存的是日期，周次每次按**当前课表**的学期开始时间现算。
     * 切换课表 = 换学期开始时间 → 同一天算出不同周次 → 调休列自动改跟随那一周的课。
     * （改之前存的是周次本身，换课表就整体错位，跟随到别的日期的课上去。）
     *
     * @return null = 这条调休还没配跟随日期
     */
    fun resolveWorkSwapFollow(
        swap: HolidayManager.Entry,
        scheduleId: String = getCurrentScheduleId(),
    ): WorkSwapFollow? {
        val date = swap.followLocalDate()
            // 老数据/老备份没有 followDate：按当前课表把 (周次, 星期) 还原成日期再走同一条路
            ?: runCatching { dateForTeachingWeekDay(swap.followWeek, swap.followWeekday, scheduleId) }
                .getOrNull()
                ?.takeIf { swap.followWeek > 0 && swap.followWeekday in 1..7 }
            ?: return null
        val position = teachingWeekPositionForDate(date, scheduleId)
        // 教学周重组把这天标成休课日时拿不到 weekday，退回日历星期
        val weekday = position.weekday ?: date.dayOfWeek.value
        return WorkSwapFollow(date, position.week.toInt(), weekday)
    }

    /** 待配置补班（未配跟随日期）不视为有课，避免智能周末误显示 */
    fun hasWorkSwapOnDay(dayOfWeek: Int, week: Int): Boolean {
        val swap = workSwapEntryOnDay(dayOfWeek, week) ?: return false
        return resolveWorkSwapFollow(swap) != null
    }

    /**
     * 该课表日「有没有课可上」：当天有课，或已配置调休且映射日/映射周有课。
     * 智能周末跳周用：无课可上（含未配置 followWeekday）→ 应跳下周。
     */
    fun hasDisplayableCoursesOnDay(dayOfWeek: Int, week: Int): Boolean {
        if (dayOfWeek !in 1..7) return false
        if (getAllCourses().any { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week) }) return true
        val swap = workSwapEntryOnDay(dayOfWeek, week) ?: return false
        val follow = resolveWorkSwapFollow(swap) ?: return false
        return getAllCourses().any {
            it.dayOfWeek == follow.weekday && it.isActiveInWeek(follow.week)
        }
    }

    private fun workSwapEntryOnDay(dayOfWeek: Int, week: Int): HolidayManager.Entry? {
        if (dayOfWeek !in 1..7) return null
        val date = runCatching { dateForTeachingWeekDay(week, dayOfWeek) }.getOrNull() ?: return null
        return HolidayManager.workSwap(appContext, date)
    }

    fun getShowNonCurrentWeek(): Boolean {
        return getShowNonCurrentWeek(getCurrentScheduleId())
    }

    fun getShowNonCurrentWeek(scheduleId: String): Boolean {
        val key = "${getScheduleKeyPrefix(scheduleId)}$KEY_SHOW_NON_CURRENT_WEEK"
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
        // 走生效作息：同一套节次骨架下，按当天日期自动取夏令时/冬令时那套时间
        return getTimeConfig(configId).effective().getPeriodTimes(period)
    }

    /** All configured section times, with afternoon and evening keys using global section numbers. */
    fun getCurrentSectionTimes(): Map<Int, String> {
        val sectionCount = getMorningSections() + getAfternoonSections() + getEveningSections()
        return getGlobalSectionTimes().filterKeys { it in 1..sectionCount }
    }

    fun getSectionTimes(scheduleId: String): Map<Int, String> {
        val morningCount = getMorningSections(scheduleId)
        val afternoonCount = getAfternoonSections(scheduleId)
        return buildMap {
            getPeriodTimes("morning", scheduleId).forEach { (index, time) -> put(index, time) }
            getPeriodTimes("afternoon", scheduleId).forEach { (index, time) -> put(morningCount + index, time) }
            getPeriodTimes("evening", scheduleId).forEach { (index, time) -> put(morningCount + afternoonCount + index, time) }
        }
    }

    fun savePeriodTimes(period: String, times: Map<Int, String>) {
        savePeriodTimes(period, times, getCurrentScheduleId())
    }

    /** 仅目标是当前课表时才通知，避免导入目标课表时无谓刷新当前页 */
    fun savePeriodTimes(period: String, times: Map<Int, String>, scheduleId: String) {
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        // 编辑的是「当天生效的那套作息」，改完只写回它，不影响其它作息方案
        val activeRoutineId = config.effectiveRoutineId()
        val base = config.effective()
        val existing = base.sectionTimes.toMutableMap()
        existing.keys.filter { it.startsWith("${period}_") }.forEach { existing.remove(it) }
        for ((idx, v) in times) {
            existing["${period}_$idx"] = v
        }
        val updated = base.copy(sectionTimes = existing, quickTimeEnabled = false)
        saveTimeConfig(
            if (activeRoutineId == null) updated
            else config.withRoutineTimesApplied(activeRoutineId, updated)
        )
        if (scheduleId == getCurrentScheduleId()) notifyCourseChanged("settings")
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

    /** 获取今日课程/课程提醒标准版小组件的 padding 档位（0=标准, 1=4×6, 2=4×7, 3=4×6无字, 4=4×7无字） */
    fun getWidgetPaddingMode(): Int {
        return safeGetInt(KEY_WIDGET_PADDING_MODE, defaultWidgetPaddingMode)
    }

    /** 设置今日课程/课程提醒标准版小组件的 padding 档位（0=标准, 1=4×6, 2=4×7, 3=4×6无字, 4=4×7无字） */
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
        notifyCourseChanged("settings", "current_schedule")
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
            // 追加到末尾，保持「添加时间」顺序，不因新建/选中而重排
            names.add(name)
            saveScheduleNames(names)
        }
        val currentPrefix = "$SCHEDULE_KEY_PREFIX${currentId}_"
        val newPrefix = "$SCHEDULE_KEY_PREFIX${name}_"
        // 必须先复制再进 edit 块：addTimeConfig 自身会提交 prefs，嵌套 edit 会打乱写入顺序
        val duplicatedTimeConfigId = addTimeConfig(
            getTimeConfig(getScheduleTimeConfigId(currentId)).copy(id = 0L, name = name)
        )
        prefs.edit(commit = true) {
            for ((key, value) in prefs.all) {
                if (key.startsWith(currentPrefix)) {
                    val settingName = key.removePrefix(currentPrefix)
                    if (settingName == KEY_COURSES ||
                        settingName == KEY_TEACHING_WEEK_REORGANIZATIONS
                    ) continue
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
            // 新学期要的是独立副本（含全部作息方案），沿用旧绑定会让两个课表互相影响
            putLong("$SCHEDULE_TIME_CONFIG_PREFIX$name", duplicatedTimeConfigId)
            val today = LocalDate.now()
            val todayStr =
                String.format(Locale.ROOT, "%04d/%02d/%02d", today.year, today.monthValue, today.dayOfMonth)
            putString("$newPrefix$KEY_CLASS_START_TIME", todayStr)
            putInt("$newPrefix$KEY_CURRENT_WEEK", 1)
            remove("$newPrefix$KEY_TEACHING_WEEK_REORGANIZATIONS")
        }
        return names
    }

    /**
     * 从文件夹原始数据里去掉（或改名）某个课表引用，并落盘。
     * 不能用 getScheduleFolders() 做判断：它读时已按当前 names 剔除刚删的名字，
     * 缓存冷时条件恒为 false，幽灵引用会一直留在 prefs 并被备份导出。
     */
    private fun rewriteScheduleNameInFolders(oldName: String, newName: String?) {
        val json = prefs.getString(KEY_SCHEDULE_FOLDERS, null) ?: return
        val folders = try {
            val type = object : TypeToken<List<ScheduleFolder>>() {}.type
            gson.fromJson<List<ScheduleFolder>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            return
        }
        if (folders.none { oldName in it.schedules }) return
        saveScheduleFolders(
            folders.map { folder ->
                if (oldName !in folder.schedules) folder
                else folder.copy(
                    schedules = folder.schedules.map { if (it == oldName) newName else it }
                        .filterNotNull()
                )
            }
        )
    }

    private fun removeScheduleNameFromFolders(name: String) =
        rewriteScheduleNameInFolders(name, newName = null)

    fun deleteSchedule(name: String): List<String> {
        val names = getScheduleNames().toMutableList()
        names.remove(name)
        // 删光后自动补默认课表，避免应用无法启动
        val replacedLastSchedule = names.isEmpty()
        if (replacedLastSchedule) {
            names.add("默认课表")
        }
        saveScheduleNames(names)
        // 课表没了，文件夹里的引用必须同步清掉，否则会留下幽灵条目
        removeScheduleNameFromFolders(name)
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
            if (replacedLastSchedule) {
                putString("${SCHEDULE_KEY_PREFIX}默认课表_$KEY_COURSES", "[]")
            }
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
            // 文件夹里的旧名跟着改，否则会留下幽灵条目且新名跑到根目录
            rewriteScheduleNameInFolders(oldName, newName)
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

    // ---------- 课表文件夹 ----------

    /**
     * 读取课表文件夹。
     * 顺带清洗：剔除已被删除的课表名，保证一个课表只出现在一个文件夹里（保留靠前的那个），
     * 并合并重复的文件夹 id（坏备份会让 LazyColumn key 冲突闪退）。
     */
    fun getScheduleFolders(): List<ScheduleFolder> {
        scheduleFoldersCache?.let { return it }
        val json = prefs.getString(KEY_SCHEDULE_FOLDERS, null)
        val allNames = getScheduleNames()
        val parsed = try {
            if (json.isNullOrBlank()) emptyList()
            else {
                val type = object : TypeToken<List<ScheduleFolder>>() {}.type
                gson.fromJson<List<ScheduleFolder>>(json, type) ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        val seen = mutableSetOf<String>()
        val byId = linkedMapOf<String, ScheduleFolder>()
        parsed
            .filter { it.id.isNotBlank() }
            .forEach { folder ->
                val kept = folder.schedules
                    .filter { name -> name in allNames && seen.add(name) }
                // 文件夹内部按全局课表顺序展示
                val ordered = allNames.filter { it in kept }
                val existing = byId[folder.id]
                if (existing == null) {
                    byId[folder.id] = folder.copy(schedules = ordered)
                } else {
                    // 同 id 并入第一个：坏备份/重复迁移会写进重复 id，UI Lazy key 会闪退
                    val merged = (existing.schedules + ordered).distinct()
                    byId[folder.id] = existing.copy(
                        schedules = allNames.filter { it in merged }
                    )
                }
            }
        val result = byId.values.toList()
        scheduleFoldersCache = result
        return result
    }

    private fun saveScheduleFolders(folders: List<ScheduleFolder>) {
        prefs.edit(commit = true) { putString(KEY_SCHEDULE_FOLDERS, gson.toJson(folders)) }
        scheduleFoldersCache = folders
    }

    /** 新建空文件夹，追加到末尾 */
    fun addScheduleFolder(name: String): List<ScheduleFolder> {
        val folders = getScheduleFolders().toMutableList()
        folders.add(
            ScheduleFolder(
                id = "folder_${System.currentTimeMillis()}_${folders.size}",
                name = name
            )
        )
        saveScheduleFolders(folders)
        return folders
    }

    fun renameScheduleFolder(id: String, newName: String): List<ScheduleFolder> {
        val folders = getScheduleFolders().toMutableList()
        val index = folders.indexOfFirst { it.id == id }
        if (index != -1) {
            folders[index] = folders[index].copy(name = newName)
            saveScheduleFolders(folders)
        }
        return folders
    }

    /**
     * 解散文件夹：文件夹内的课表回到根目录，课表本身不删除。
     * 只删文件夹壳，避免误删用户数据（真要删课表请走删除课表）。
     */
    fun disbandScheduleFolder(id: String): List<ScheduleFolder> {
        val folders = getScheduleFolders()
        if (folders.none { it.id == id }) return folders
        saveScheduleFolders(folders.filter { it.id != id })
        return getScheduleFolders()
    }

    /** 把课表移动到文件夹；folderId 为 null 表示移回根目录 */
    fun moveSchedulesToFolder(scheduleNames: List<String>, folderId: String?): List<ScheduleFolder> {
        if (scheduleNames.isEmpty()) return getScheduleFolders()
        val moving = scheduleNames.distinct()
        // 先从所有文件夹摘出来，避免同一课表同时挂在两个文件夹下
        val folders = getScheduleFolders()
            .map { it.copy(schedules = it.schedules.filter { name -> name !in moving }) }
            .toMutableList()
        if (folderId != null) {
            val index = folders.indexOfFirst { it.id == folderId }
            if (index != -1) {
                val merged = (folders[index].schedules + moving).distinct()
                // 文件夹内部仍按全局课表顺序
                val ordered = getScheduleNames().filter { it in merged }
                folders[index] = folders[index].copy(schedules = ordered)
            }
        }
        saveScheduleFolders(folders)
        return folders
    }

    /** 课表所属文件夹 id，不在任何文件夹时返回 null */
    fun getFolderIdOfSchedule(scheduleName: String): String? {
        for (folder in getScheduleFolders()) {
            if (scheduleName in folder.schedules) return folder.id
        }
        return null
    }

    /** 未归入任何文件夹的课表（切换页根目录） */
    fun getRootScheduleNames(): List<String> {
        val folders = getScheduleFolders()
        if (folders.isEmpty()) return getScheduleNames()
        return getScheduleNames().filter { name -> folders.none { name in it.schedules } }
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

    // --- 多时间配置支持 ---

    /** 获取所有时间配置 ID 列表（按创建顺序） */
    fun getTimeConfigIds(): List<Long> {
        timeConfigIdsCache?.let { return it }
        val idsStr = prefs.getString(KEY_TIME_CONFIG_IDS, null)
        val parsed = if (idsStr == null) {
            null
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
        // 列表缺失/被还原成空，但配置本身还在 → 扫现存键重建，别让读取方掉到 id=0 的默认配置
        val ids = when {
            parsed == null -> rebuildTimeConfigIdsFromKeys() ?: listOf(0L)
            parsed.isEmpty() -> rebuildTimeConfigIdsFromKeys() ?: emptyList()
            else -> parsed
        }
        timeConfigIdsCache = ids
        return ids
    }

    /**
     * `time_config_ids` 丢失时，从现存的 `time_config_{id}` 键反推重建并落盘。
     *
     * 无版本依赖的通用自愈：不管是备份还原把键冲掉、还是老数据残留，只要配置还在就自己爬起来。
     * 否则 [getScheduleTimeConfigId] 会因为「绑定 id 不在列表里」而回退到第一个配置，
     * 读到 `time_config_0` 不存在 → 节数与时间被**静默**重置成 4/4/4 默认值。
     *
     * @return 扫到了配置则返回（已落盘），一个都没有则返回 null 交回上层走原逻辑
     */
    private fun rebuildTimeConfigIdsFromKeys(): List<Long>? {
        val scanned = prefs.all.keys
            .filter { it.startsWith(TIME_CONFIG_PREFIX) && it != KEY_TIME_CONFIG_IDS }
            .mapNotNull { it.removePrefix(TIME_CONFIG_PREFIX).toLongOrNull() }
            .distinct()
            .sorted()
            .takeIf { it.isNotEmpty() } ?: return null
        saveTimeConfigIds(scanned)
        android.util.Log.w(TAG, "time_config_ids 缺失/为空，已从现存配置键重建: $scanned")
        return scanned
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
        val newConfig = config.ensureRoutine().copy(id = newId)
        ids.add(newId)
        saveTimeConfigIds(ids)
        saveTimeConfig(newConfig)
        return newId
    }

    /**
     * 直接改写 TimeConfig 后广播一次「设置变更」。
     *
     * [saveTimeConfig] 只落盘、不发通知（它被很多内部批量写调用，发了会重复重排），
     * 所以「在 UI 层拿到 config 改一改再存回去」的路径必须自己补这一下，
     * 否则课程提醒 / 小部件 / 手表推送仍按改动前的时间工作。
     */
    fun notifyTimeConfigChanged() {
        notifyCourseChanged("settings")
    }

    /**
     * 删除当前课表的一个作息方案；至少保留一个，删不动时返回 false。
     *
     * 删除后「生效的那套」可能换人（也可能还是它，时间却变了），所以必须广播 ——
     * 否则课程提醒 / 小部件仍按被删掉的作息排时间。
     */
    fun deleteRoutine(routineId: Long): Boolean {
        val configId = getScheduleTimeConfigId(getCurrentScheduleId())
        val original = getTimeConfig(configId)
        val remaining = original.withRoutineRemoved(routineId)
        if (remaining.safeRoutines.size == original.safeRoutines.size) return false
        saveTimeConfig(remaining)
        notifyCourseChanged("settings")
        return true
    }

    /**
     * 保存某个作息方案的时间数据，并广播变更让课表页重算。
     * 节次骨架不在这里改——它属于课表，由时间配置页统一管理。
     *
     * @return false 表示没找到目标作息、什么都没写（withRoutineReplaced 静默返回原值）。
     * 曾经有「保存成功但课表纹丝不动」的反馈无法复现，命中不到时必须留下日志。
     */
    fun saveRoutine(routineId: Long, edited: TimeConfig, nameOverride: String? = null): Boolean {
        val scheduleId = getCurrentScheduleId()
        val base = getTimeConfig(getScheduleTimeConfigId(scheduleId))
        val routine = edited.routineOf(routineId, nameOverride)
        if (base.routineById(routineId) == null) {
            android.util.Log.w(
                TAG,
                "saveRoutine: 目标作息不存在 config=${base.id} routineId=$routineId " +
                    "现有=${base.safeRoutines.map { it.id }}，本次保存被丢弃"
            )
            return false
        }
        saveTimeConfig(base.withRoutineReplaced(routineId, routine))
        notifyCourseChanged("settings")
        return true
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

    /**
     * 清理没有任何课表绑定的孤儿时间配置。
     *
     * 「多配置可切换」时代会攒下一堆配置；改成「一课表一配置」后它们再也不会被用到，
     * 却仍留在 time_config_ids 里 —— 不仅占空间，还会让同名检查误判。
     * 必须在确定至少还有一个配置存活时才删，避免把最后一个也清掉导致课表无配置可用。
     *
     * 删除不可逆且没有第二份副本，所以把删了哪些留下来记一条 log：一旦出现
     * 「课表名与绑定键不同步导致误删」，这是唯一能查的线索。
     */
    fun pruneOrphanTimeConfigsIfNeeded() {
        val ids = getTimeConfigIds()
        if (ids.size <= 1) return
        // 同时并入「原始绑定值」和「回退解析值」：未绑定的课表会回退到第一个配置，
        // 那个配置虽没被显式绑定却正在被使用，只看原始值会把它误删。
        val bound = getScheduleNames()
            .flatMap { name ->
                listOf(
                    prefs.getLong("$SCHEDULE_TIME_CONFIG_PREFIX$name", 0L),
                    getScheduleTimeConfigId(name)
                )
            }
            .toSet()
        val orphans = ids.filter { it !in bound }
        if (orphans.isEmpty() || orphans.size >= ids.size) return
        val kept = ids.filter { it in bound }
        saveTimeConfigIds(kept)
        orphans.forEach { orphan ->
            prefs.edit { remove("${TIME_CONFIG_PREFIX}$orphan") }
            timeConfigCache.remove(orphan)
        }
        android.util.Log.w(
            TAG,
            "pruneOrphanTimeConfigs: 课表=${getScheduleNames()} 保留=$kept 删除=$orphans"
        )
    }

    fun getCurrentTimeConfig(): TimeConfig {
        return getTimeConfig(getCurrentTimeConfigId())
    }

    /**
     * 跨过作息生效日期后，补一次「设置变更」广播，让课程提醒 / 小部件按新作息重排。
     *
     * 作息是到日期自动切换的，切换那一刻没有任何写操作；而提醒闹钟只在设置变更时重排，
     * 不补这一下的话，App 没被打开过时提醒会一直停在上一个作息的时间上。
     *
     * 只在「生效的作息真的换了」时才广播；同一天反复调用只是两次 prefs 读，无副作用。
     * prefs 里没值时只登记不重排——首次（新装 / 首次升级）的排程由既有冷启动链路负责。
     * 按课表分键存放，切换课表不会互相干扰。
     */
    fun syncRoutineAfterDateChange(): Boolean {
        val scheduleId = getCurrentScheduleId()
        val config = getTimeConfig(getScheduleTimeConfigId(scheduleId))
        val key = "${config.id}:${config.effectiveRoutineId() ?: -1L}"
        val prefKey = "${SCHEDULE_KEY_PREFIX}${scheduleId}_active_routine"
        val last = prefs.getString(prefKey, null)

        // 顺带把顶层镜像刷成当天生效的那套。跨日期切换本身没有任何写操作，
        // 镜像不刷新就会一直停在上一个作息上 —— 只读顶层的旧版本 App、
        // 以及直接导出 prefs 原始 JSON 的全量备份都会拿到过期时间。
        // （App 内部所有读取都已走 effective()，这里只是把镜像 invariant 补回来）
        val effective = config.effective()
        if (effective != config) saveTimeConfig(effective)

        if (last == key) return false
        prefs.edit(commit = true) { putString(prefKey, key) }
        // 首次登记不重排，避免新装 / 首次升级时白跑一次全量闹钟排程
        if (last == null) return false
        notifyCourseChanged("settings")
        return true
    }

    /** 相对 key "morning_1" 转全局绝对节次号 -> 名称；取当天生效作息的名称 */
    fun getSectionNames(): Map<Int, String> {
        val config = getCurrentTimeConfig().effective()
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

    /** 当前课表 + 当天生效作息合成后的配置；读时间统一走它 */
    fun getEffectiveTimeConfig(): TimeConfig = getCurrentTimeConfig().effective()

    fun getEffectiveTimeConfig(scheduleId: String): TimeConfig =
        getTimeConfig(getScheduleTimeConfigId(scheduleId)).effective()

    /**
     * 把另一个课表的时间配置（含全部作息方案）完整复制过来，直接作为本课表的时间配置使用。
     * 一个课表只绑定一个时间配置，所以这里是「覆盖」而不是「新增/切换」。
     */
    fun copyTimeConfigFromSchedule(
        sourceScheduleId: String,
        targetScheduleId: String = getCurrentScheduleId()
    ): Boolean {
        if (sourceScheduleId == targetScheduleId) return false
        val targetId = getScheduleTimeConfigId(targetScheduleId)
        val source = getTimeConfig(getScheduleTimeConfigId(sourceScheduleId))
        val target = getTimeConfig(targetId)
        // 节次骨架变了就按相对位置平移课程，避免课被挤到网格外
        remapCoursesForNewSectionCounts(
            source.morningSections, source.afternoonSections, source.eveningSections, targetScheduleId
        )
        saveTimeConfig(source.copy(id = targetId, name = target.name))
        setScheduleTimeConfigId(targetScheduleId, targetId)
        notifyCourseChanged("settings")
        return true
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
            val updated = base.copy(
                name = base.name.ifBlank { scheduleId },
                morningSections = morningSections,
                afternoonSections = afternoonSections,
                eveningSections = eveningSections,
                quickTimeEnabled = false,
                sectionTimes = sectionTimes
            )
            val routineId = base.effectiveRoutineId()
            saveTimeConfig(
                if (routineId == null) updated
                else base.withRoutineTimesApplied(routineId, updated)
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
        newMorning: Int, newAfternoon: Int, newEvening: Int,
        scheduleId: String = getCurrentScheduleId()
    ) {
        val oldMorning = getMorningSections(scheduleId)
        val oldAfternoon = getAfternoonSections(scheduleId)
        val oldEvening = getEveningSections(scheduleId)
        if (oldMorning == newMorning && oldAfternoon == newAfternoon && oldEvening == newEvening) return

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
            val updated = base.copy(
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
            val routineId = base.effectiveRoutineId()
            saveTimeConfig(
                if (routineId == null) updated
                else base.withRoutineTimesApplied(routineId, updated)
            )
        } finally {
            batchingSettings = false
        }
        commitSettingsChanged()
    }

    /**
 * 外观键不进全量备份。
 *
 * 外观已整体迁到 [ScheduleAppearance] 的独立 prefs 文件，本文件里理论上不该再有外观键。
 * 这里保留判定是为了兜住两类残留：迁移未完成的旧版本数据、以及历史备份里混入的键
 * （旧版外观键叫 `combination_*` / `comb_*`，迁移到新文件后键名也变了，但老备份仍带旧名）。
 */
    private fun isCombinationBackupKey(key: String): Boolean {
        return key == ScheduleAppearance.FILE_STYLE_KEY ||
            key.startsWith("combination_") ||
            key.startsWith("comb_") ||
            key.startsWith("wallpaper_")
    }

    /** 提醒、勿扰、小组件等应用功能设置不进全量备份 */
    private fun isAppFeatureBackupKey(key: String): Boolean {
        return key == KEY_PRE_CLASS_REMINDER ||
            key == KEY_PRE_CLASS_REMINDER_MINUTES ||
            key == KEY_NEXT_DAY_REMINDER ||
            key == KEY_NEXT_DAY_REMINDER_HOUR ||
            key == KEY_NEXT_DAY_REMINDER_MINUTE ||
            key == KEY_ISLAND_NOTIFICATION ||
            key == KEY_CLASS_DND ||
            key == KEY_CLASS_DND_MODE ||
            key == KEY_WIDGET_PADDING_MODE
    }

    /**
     * 全量备份：课表数据 + 节假日/调休。
     * 不含搭配、提醒等应用功能设置，以及主题等应用偏好。
     */
    fun exportAllPreferences(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for ((key, value) in prefs.all) {
            if (isCombinationBackupKey(key) || isAppFeatureBackupKey(key)) continue
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
        if (KEY_SCHEDULE_NAMES !in result) {
            result[KEY_SCHEDULE_NAMES] = gson.toJson(getScheduleNames())
        }
        if (KEY_CURRENT_SCHEDULE_ID !in result) {
            result[KEY_CURRENT_SCHEDULE_ID] = getCurrentScheduleId()
        }
        ensureEmptyScheduleCourseEntries(result, getScheduleNames())
        result.putAll(HolidayManager.exportBackupData(appContext))
        // 文件夹用清洗后的结果导出：历史坏数据里的幽灵课表名不应进备份
        result[KEY_SCHEDULE_FOLDERS] = gson.toJson(getScheduleFolders())
        return result
    }

    fun importAllPreferences(data: Map<String, Any>) {
        val normalizedData = normalizeFullScheduleBackup(data)
        withValidatedFullScheduleBackup(normalizedData) { holidayBackup ->
            restoreAllPreferences(
                normalizedData,
                holidayBackup,
                shouldPreserveRestoredFolderMembership(normalizedData),
            )
        }
    }

    private fun restoreAllPreferences(
        data: Map<String, Any>,
        holidayBackup: HolidayManager.BackupData,
        preserveFolderMembership: Boolean,
    ) {
        prefs.edit {
            for ((key) in prefs.all) {
                if (key.startsWith(SCHEDULE_KEY_PREFIX) || key.startsWith(TIME_CONFIG_PREFIX) ||
                    key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX)) {
                    remove(key)
                }
            }
            remove(KEY_SCHEDULE_NAMES)
            remove(KEY_SCHEDULE_FOLDERS)
            // Preserve root-level schedules in new backups; migrate only legacy backups.
            if (preserveFolderMembership) putBoolean(KEY_DEFAULT_FOLDER_MIGRATED, true)
            else remove(KEY_DEFAULT_FOLDER_MIGRATED)
            remove(KEY_CURRENT_SCHEDULE_ID)
            remove(KEY_SHIFT_MODE)
            remove(KEY_SHIFT_SELECTED_SCHEDULES)
            remove(KEY_TIME_CONFIG_IDS)
            remove(KEY_CURRENT_TIME_CONFIG_ID)
            remove(KEY_DEFAULT_HOMEPAGE)

            for ((key, value) in data) {
                if (key == HolidayManager.BACKUP_KEY ||
                    key == HolidayManager.BACKUP_EXCLUSION_KEY ||
                    key == HolidayManager.BACKUP_BEFORE_EXCLUSION_KEY
                ) continue
                // 搭配、提醒等应用功能设置不进备份，恢复时也不覆盖设备上的对应配置
                if (isCombinationBackupKey(key) || isAppFeatureBackupKey(key)) continue
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Number -> {
                        val numVal = value.toDouble()
                        val longVal = numVal.toLong()
                        val intVal = numVal.toInt()
                        // 时间配置/绑定 ID 与时间戳必须按 Long 恢复
                        if (key == KEY_CURRENT_TIME_CONFIG_ID || key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX) ||
                            key.endsWith("_last_modified")) {
                            putLong(key, longVal)
                        } else if (numVal == intVal.toDouble()) {
                            putInt(key, intVal)
                        } else {
                            putFloat(key, numVal.toFloat())
                        }
                    }

                    is List<*> -> {
                        // Set<String> 导出为 List，还原为 StringSet（不能再写成 JSON 字符串）
                        putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }
        }
        HolidayManager.restoreBackupData(appContext, holidayBackup)
        invalidateAllCaches()
        if (!preserveFolderMembership) migrateSchedulesIntoDefaultFolder()
        dispatchCourseChanged("restore", "")
    }

    fun getSectionsForSchedule(scheduleId: String): Triple<Int, Int, Int> {
        val configId = getScheduleTimeConfigId(scheduleId)
        val config = getTimeConfig(configId)
        return Triple(config.morningSections, config.afternoonSections, config.eveningSections)
    }

    /**
     * 从单课表备份还原时间配置；认不出格式时返回 null（调用方退回复制当前课表）。
     *
     * 两种形态都收：整份 JSON 字符串，或扁平 Map（旧版本 App 写的、也认识的那种）。
     * 二者都转回 JSON 交给 gson + sanitize —— 字段清单不用手写，也不用跟着 TimeConfig
     * 加字段改两处；sanitize 顺带完成钳制与默认作息播种。
     */
    private fun buildImportedTimeConfig(scheduleName: String, raw: Any): TimeConfig? = when (raw) {
        is String, is Map<*, *> -> parseTimeConfigSnapshot(raw)?.copy(name = scheduleName, id = 0L)

        else -> null
    }

    private fun parseTimeConfigSnapshot(raw: Any): TimeConfig? {
        val json = runCatching { gson.toJson(raw) }.getOrNull() ?: return null
        val parsed = TimeConfig.parseSnapshotOrNull(gson, json) ?: return null
        return TimeConfig.sanitize(0L, parsed)
    }

    fun importSingleSchedule(
        scheduleName: String,
        coursesData: List<Map<String, Any>>,
        timeConfigData: Any? = null,
        classStartTime: String? = null,
        currentWeek: Int? = null,
        totalWeeks: Int? = null,
        teachingWeekReorganizations: List<TeachingWeekReorganizationRule>? = null,
        smartWeekend: Boolean? = null,
        showNonCurrentWeek: Boolean? = null,
    ) {
        val normalizedClassStart = classStartTime?.let {
            normalizeClassStartDate(it)
                ?: throw IllegalArgumentException("Invalid single-schedule semester start date")
        }
        val importedTotalWeeks = totalWeeks ?: 20
        require(importedTotalWeeks in 1..MAX_TOTAL_WEEKS) {
            "Invalid single-schedule semester week count"
        }
        teachingWeekReorganizations?.let { rules ->
            val error = TeachingWeekReorganization.validationError(rules, Int.MAX_VALUE)
            require(error == null) { error ?: "Invalid single-schedule teaching-week rules" }
            require(rules.isEmpty() || normalizedClassStart != null) {
                "A single-schedule reorganization requires a valid semester start date"
            }
        }
        // Lowering a schedule's horizon does not delete its stored course selections; backups preserve them.
        validateSingleScheduleCourseData(coursesData, MAX_TOTAL_WEEKS)

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
            // 解析不出来（备份损坏 / 格式不认识）时退回「复制当前课表」，课表不至于没有时间配置
            val newConfig = timeConfigData?.let { buildImportedTimeConfig(scheduleName, it) }
                ?: getCurrentTimeConfig().copy(name = scheduleName, id = 0L)
            val newConfigId = addTimeConfig(newConfig)
            setScheduleTimeConfigId(scheduleName, newConfigId)
        }

        if (normalizedClassStart != null || currentWeek != null || totalWeeks != null ||
            teachingWeekReorganizations != null || smartWeekend != null || showNonCurrentWeek != null
        ) {
            val schedulePrefix = getScheduleKeyPrefix(scheduleName)
            prefs.edit {
                normalizedClassStart?.let { putString("$schedulePrefix$KEY_CLASS_START_TIME", it) }
                currentWeek?.let { putInt("$schedulePrefix$KEY_CURRENT_WEEK", it) }
                totalWeeks?.let { putInt("$schedulePrefix$KEY_TOTAL_WEEKS", it) }
                teachingWeekReorganizations?.let { rules ->
                    putString(
                        "$schedulePrefix$KEY_TEACHING_WEEK_REORGANIZATIONS",
                        TeachingWeekReorganization.encode(rules, Int.MAX_VALUE),
                    )
                }
                smartWeekend?.let { putBoolean("$schedulePrefix$KEY_SMART_WEEKEND", it) }
                showNonCurrentWeek?.let { putBoolean("$schedulePrefix$KEY_SHOW_NON_CURRENT_WEEK", it) }
            }
            markScheduleSettingsChanged(scheduleName)
        }

        dispatchCourseChanged("restore", "")
    }
}

internal fun <T> withValidatedFullScheduleBackup(
    data: Map<String, Any>,
    restore: (HolidayManager.BackupData) -> T,
): T {
    validateFullScheduleBackupStructure(data)
    val holidayBackup = HolidayManager.decodeBackupData(data)
    return restore(holidayBackup)
}

/** Export an empty slot for schedules that have never had a course persisted. */
internal fun ensureEmptyScheduleCourseEntries(data: MutableMap<String, Any>, scheduleNames: List<String>) {
    scheduleNames.forEach { name -> data.putIfAbsent("schedule_${name}_courses", "[]") }
}

internal fun shouldPreserveRestoredFolderMembership(data: Map<String, Any>): Boolean =
    data.containsKey("schedule_folders")

internal fun normalizeFullScheduleBackup(data: Map<String, Any>): Map<String, Any> {
    val names = validateFullScheduleBackupStructure(data)
    return if (data.containsKey("schedule_names")) {
        data
    } else {
        data + ("schedule_names" to Gson().toJson(names))
    }
}

internal fun validateFullScheduleBackupStructure(data: Map<String, Any>): List<String> {
    val names = if (data.containsKey("schedule_names")) {
        val rawNames = data["schedule_names"]
        when (rawNames) {
            is String -> runCatching {
                Gson().fromJson<List<*>>(rawNames, object : TypeToken<List<*>>() {}.type)
            }.getOrNull()
            is List<*> -> rawNames
            else -> null
        }
    } else {
        val courseScheduleNames = data.keys.asSequence()
            .filter { it.startsWith("schedule_") && !it.startsWith("schedule_time_config_") && it.endsWith("_courses") }
            .map { it.removePrefix("schedule_").removeSuffix("_courses") }
        val boundScheduleNames = data.keys.asSequence()
            .filter { it.startsWith("schedule_time_config_") }
            .map { it.removePrefix("schedule_time_config_") }
        (courseScheduleNames + boundScheduleNames).distinct().toList()
    } ?: throw IllegalArgumentException("Invalid full schedule backup: malformed schedule names")
    require(names.isNotEmpty() && names.all { it is String && it.isNotBlank() }) {
        "Invalid full schedule backup: schedule names are empty or malformed"
    }
    val scheduleNames = names.filterIsInstance<String>()
    require(scheduleNames.distinct().size == scheduleNames.size) {
        "Invalid full schedule backup: duplicate schedule names"
    }
    require(scheduleNames.all { name ->
        data.containsKey("schedule_${name}_courses") ||
            data.containsKey("schedule_time_config_$name")
    }) { "Invalid full schedule backup: no matching schedule data" }

    if (data.containsKey("schedule_folders")) {
        val rawFolders = data["schedule_folders"] as? String
            ?: throw IllegalArgumentException("Invalid schedule folders in backup")
        val folders = runCatching { JsonParser.parseString(rawFolders) }.getOrNull()
        require(folders != null && folders.isJsonArray) { "Invalid schedule folders in backup" }
        folders.asJsonArray.forEach { folder ->
            require(folder.isJsonObject) { "Invalid schedule folder in backup" }
            val fields = folder.asJsonObject
            val id = fields.get("id")
            val name = fields.get("name")
            val members = fields.get("schedules")
            require(id?.isJsonPrimitive == true && id.asJsonPrimitive.isString && id.asString.isNotBlank() &&
                name?.isJsonPrimitive == true && name.asJsonPrimitive.isString && name.asString.isNotBlank() &&
                members?.isJsonArray == true && members.asJsonArray.all { member ->
                    member.isJsonPrimitive && member.asJsonPrimitive.isString && member.asString in scheduleNames
                }
            ) { "Invalid schedule folder in backup" }
        }
    }

    scheduleNames.forEach { name ->
        val totalWeeksKey = "schedule_${name}_total_weeks"
        val totalWeeks = if (!data.containsKey(totalWeeksKey)) {
            20
        } else {
            val number = (data[totalWeeksKey] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid schedule total weeks in backup")
            require(number.isFinite() && number % 1.0 == 0.0 &&
                number in 1.0..CourseRepository.MAX_TOTAL_WEEKS.toDouble()
            ) { "Invalid schedule total weeks in backup" }
            number.toInt()
        }
        val currentWeekKey = "schedule_${name}_current_week"
        if (data.containsKey(currentWeekKey)) {
            val week = (data[currentWeekKey] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid schedule current week in backup")
            require(
                week.isFinite() && week % 1.0 == 0.0 &&
                    week >= Int.MIN_VALUE.toDouble() && week <= Int.MAX_VALUE.toDouble(),
            ) { "Invalid schedule current week in backup" }
        }
        val classStartKey = "schedule_${name}_class_start_time"
        val classStartDate = if (data.containsKey(classStartKey)) {
            val raw = data[classStartKey] as? String
            CourseRepository.normalizeClassStartDate(raw)
                ?: throw IllegalArgumentException("Invalid schedule semester start date in backup")
        } else null

        val coursesKey = "schedule_${name}_courses"
        if (data.containsKey(coursesKey)) {
            val rawCourses = data[coursesKey] as? String
                ?: throw IllegalArgumentException("Invalid full schedule backup: malformed course data")
            val courses = runCatching {
                Gson().fromJson<List<*>>(rawCourses, object : TypeToken<List<*>>() {}.type)
            }.getOrNull()
            require(courses != null && courses.all { it is Map<*, *> }) {
                "Invalid full schedule backup: malformed course data"
            }
            @Suppress("UNCHECKED_CAST")
            validateSingleScheduleCourseData(
                courses.filterIsInstance<Map<String, Any>>(),
                CourseRepository.MAX_TOTAL_WEEKS,
            )
        }

        val timeConfigBindingKey = "schedule_time_config_$name"
        if (data.containsKey(timeConfigBindingKey)) {
            val binding = (data[timeConfigBindingKey] as? Number)?.toDouble()
            require(
                binding != null && binding.isFinite() && binding % 1.0 == 0.0 &&
                    binding >= 0.0 && binding < Long.MAX_VALUE.toDouble(),
            ) { "Invalid full schedule backup: malformed time configuration binding" }
        }

        val rulesKey = "schedule_${name}_teaching_week_reorganizations"
        if (data.containsKey(rulesKey)) {
            val rawRules = data[rulesKey] as? String
                ?: throw IllegalArgumentException("Invalid teaching-week reorganization backup entry")
            require(classStartDate != null) {
                "Teaching-week reorganization backup requires a valid semester start date"
            }
            TeachingWeekReorganization.decode(rawRules, Int.MAX_VALUE)
        }
    }

    if (data.containsKey("current_schedule_id")) {
        val currentScheduleId = data["current_schedule_id"] as? String
        require(currentScheduleId != null && currentScheduleId in scheduleNames) {
            "Invalid full schedule backup: current schedule id is malformed"
        }
    }

    val knownRuleSuffix = "_teaching_week_reorganizations"
    data.keys.asSequence()
        .filter { it.startsWith("schedule_") && !it.startsWith("schedule_time_config_") && it.endsWith(knownRuleSuffix) }
        .forEach { key ->
            val scheduleName = key.removePrefix("schedule_").removeSuffix(knownRuleSuffix)
            require(scheduleName in scheduleNames) {
                "Invalid full schedule backup: reorganization belongs to an unknown schedule"
            }
        }
    data.keys.asSequence()
        .filter { it.startsWith("schedule_") && !it.startsWith("schedule_time_config_") && it.endsWith("_courses") }
        .forEach { key ->
            val scheduleName = key.removePrefix("schedule_").removeSuffix("_courses")
            require(scheduleName in scheduleNames) {
                "Invalid full schedule backup: course data belongs to an unknown schedule"
            }
        }
    data.keys.asSequence()
        .filter {
            it.startsWith("schedule_") && it != "schedule_names" && it != "schedule_folders" &&
                !it.startsWith("schedule_time_config_")
        }
        .forEach { key ->
            require(scheduleNames.any { key.startsWith("schedule_${it}_") }) {
                "Invalid full schedule backup: setting belongs to an unknown schedule"
            }
        }
    data.keys.asSequence()
        .filter { it.startsWith("schedule_time_config_") }
        .map { it.removePrefix("schedule_time_config_") }
        .forEach { scheduleName ->
            require(scheduleName in scheduleNames) {
                "Invalid full schedule backup: time configuration belongs to an unknown schedule"
            }
        }
    return scheduleNames
}

internal fun validateSingleScheduleCourseData(
    courses: List<Map<String, Any>>,
    maxWeeks: Int? = null,
) {
    courses.forEach { course ->
        require((course["name"] as? String)?.isNotBlank() == true) {
            "Invalid single-schedule course name"
        }
        fun requiredInteger(key: String): Int {
            val number = (course[key] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid single-schedule course $key")
            require(number.isFinite() && number % 1.0 == 0.0 &&
                number >= Int.MIN_VALUE.toDouble() && number <= Int.MAX_VALUE.toDouble()
            ) { "Invalid single-schedule course $key" }
            return number.toInt()
        }
        require(requiredInteger("dayOfWeek") in 1..7) {
            "Invalid single-schedule course dayOfWeek"
        }
        val startSection = requiredInteger("startSection")
        val endSection = requiredInteger("endSection")
        require(startSection > 0 && endSection >= startSection) {
            "Invalid single-schedule course section range"
        }
    }
    validateSingleScheduleCourseWeekData(courses, maxWeeks)
}

internal fun validateSingleScheduleCourseWeekData(
    courses: List<Map<String, Any>>,
    maxWeeks: Int? = null,
) {
    require(maxWeeks == null || maxWeeks in 1..CourseRepository.MAX_TOTAL_WEEKS) {
        "Invalid course-week validation horizon"
    }
    courses.forEach { course ->
        if (course.containsKey("selectedWeeks")) {
            val rawWeeks = course["selectedWeeks"] as? List<*>
                ?: throw IllegalArgumentException("Invalid single-schedule selected weeks")
            val weeks = rawWeeks.map { value ->
                val number = (value as? Number)?.toDouble()
                    ?: throw IllegalArgumentException("Invalid single-schedule selected week")
                require(number.isFinite() && number % 1.0 == 0.0 &&
                    number in 1.0..Int.MAX_VALUE.toDouble()
                ) { "Invalid single-schedule selected week" }
                number.toInt()
            }
            require(weeks.distinct().size == weeks.size) {
                "Duplicate single-schedule selected week"
            }
            require(maxWeeks == null || weeks.all { it <= maxWeeks }) {
                "Selected course week exceeds the supported schedule horizon"
            }
        }

        fun exactWeekField(key: String): Int? {
            if (!course.containsKey(key)) return null
            val number = (course[key] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid single-schedule $key")
            require(number.isFinite() && number % 1.0 == 0.0 &&
                number >= Int.MIN_VALUE.toDouble() && number <= Int.MAX_VALUE.toDouble()
            ) { "Invalid single-schedule $key" }
            return number.toInt()
        }

        val startWeek = exactWeekField("startWeek")
        val endWeek = exactWeekField("endWeek")
        val weekType = exactWeekField("weekType")
        require(startWeek == null || startWeek > 0) { "Invalid single-schedule startWeek" }
        require(endWeek == null || endWeek > 0) { "Invalid single-schedule endWeek" }
        require(maxWeeks == null || startWeek == null || startWeek <= maxWeeks) {
            "Course startWeek exceeds the supported schedule horizon"
        }
        require(maxWeeks == null || endWeek == null || endWeek <= maxWeeks) {
            "Course endWeek exceeds the supported schedule horizon"
        }
        require(startWeek == null || endWeek == null || endWeek >= startWeek) {
            "Invalid single-schedule week range"
        }
        require(weekType == null || weekType in 0..2) { "Invalid single-schedule weekType" }
    }
}
