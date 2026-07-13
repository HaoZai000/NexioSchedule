package com.haooz.chedule.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 课程数据仓库 - 使用 SharedPreferences 存储（单例）
 */
class CourseRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()

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

    // 变更回调
    var onCourseChanged: ((action: String, courseId: String) -> Unit)? = null

    private fun notifyCourseChanged(action: String, courseId: String = "") {
        if (action == "settings") {
            // 设置变更时更新时间戳，确保本地修改不会被远程覆盖
            val prefix = getScheduleKeyPrefix()
            prefs.edit().putLong("${prefix}_settings_last_modified", System.currentTimeMillis()).apply()
        }
        onCourseChanged?.invoke(action, courseId)
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
        private const val KEY_SHOW_NON_CURRENT_WEEK = "show_non_current_week"
        private const val KEY_MORNING_SECTIONS = "morning_sections"
        private const val KEY_AFTERNOON_SECTIONS = "afternoon_sections"
        private const val KEY_EVENING_SECTIONS = "evening_sections"
        private const val KEY_SECTION_TIMES = "section_times"
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
        private const val KEY_SHIFT_MODE = "shift_mode_enabled"
        private const val KEY_SHIFT_SELECTED_SCHEDULES = "shift_selected_schedules"
        private const val KEY_DEFAULT_HOMEPAGE = "default_homepage"
        private const val KEY_NAV_BAR_STYLE = "nav_bar_style"
        private const val KEY_WALLPAPER_OFFSET_X = "wallpaper_offset_x"
        private const val KEY_WALLPAPER_OFFSET_Y = "wallpaper_offset_y"
        private const val KEY_WALLPAPER_SCALE = "wallpaper_scale"
        private const val WALLPAPER_FILE_NAME = "schedule_wallpaper.png"
        private const val SCHEDULE_KEY_PREFIX = "schedule_"
        // 多搭配支持
        private const val KEY_COMBINATION_IDS = "combination_ids"
        private const val KEY_CURRENT_COMBINATION_ID = "current_combination_id"
        private const val COMBINATION_WALLPAPER_PREFIX = "combination_wallpaper_"
        private const val COMBINATION_SNAPSHOT_PREFIX = "combination_snapshot_"
        private const val KEY_COMBINATION_OFFSET_X_PREFIX = "comb_offset_x_"
        private const val KEY_COMBINATION_OFFSET_Y_PREFIX = "comb_offset_y_"
        private const val KEY_COMBINATION_SCALE_PREFIX = "comb_scale_"
        private const val KEY_COMBINATION_CARD_BLUR_PREFIX = "comb_card_blur_"
        private const val KEY_COMBINATION_CARD_ALPHA_PREFIX = "comb_card_alpha_"
        private const val KEY_COMBINATION_CARD_HEIGHT_PREFIX = "comb_card_height_"
        private const val KEY_COMBINATION_CARD_CORNER_PREFIX = "comb_card_corner_"
        private const val KEY_COMBINATION_WALLPAPER_BRIGHTNESS_PREFIX = "comb_wp_brightness_"
        private const val KEY_COMBINATION_SHOW_BREAK_DIVIDERS_PREFIX = "comb_break_div_"
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
            prefs.edit().putInt(key, intVal).apply()
            return intVal
        }
    }

    /**
     * 兼容性读取 Boolean 值（云备份恢复时可能存为其他类型）
     */
    private fun safeGetBoolean(key: String, defValue: Boolean): Boolean {
        try {
            return prefs.getBoolean(key, defValue)
        } catch (_: ClassCastException) {
            val intVal = prefs.getInt(key, if (defValue) 1 else 0)
            val boolVal = intVal != 0
            prefs.edit().putBoolean(key, boolVal).apply()
            return boolVal
        }
    }

    /**
     * 获取指定课表的课程列表
     */
    fun getCoursesForSchedule(scheduleId: String): List<Course> {
        val key = "$SCHEDULE_KEY_PREFIX${scheduleId}_$KEY_COURSES"
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<Course>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存指定课表的课程列表
     */
    fun saveCoursesForSchedule(scheduleId: String, courses: List<Course>, notify: Boolean = true) {
        val key = "$SCHEDULE_KEY_PREFIX${scheduleId}_$KEY_COURSES"
        val json = gson.toJson(courses)
        prefs.edit().putString(key, json).apply()
        if (notify) onCourseChanged?.invoke("bulk", "")
    }

    /**
     * 获取课表摘要（课程数和周数范围）
     */
    fun getScheduleSummary(scheduleId: String): String {
        val courses = getCoursesForSchedule(scheduleId)
        if (courses.isEmpty()) return "空课表"
        val courseCount = courses.size
        val weeks = courses.flatMap { course ->
            if (course.selectedWeeks.isNotEmpty()) {
                course.selectedWeeks
            } else {
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
        return "$SCHEDULE_KEY_PREFIX${getCurrentScheduleId()}_"
    }

    /**
     * 获取所有课程
     */
    fun getAllCourses(): List<Course> {
        val key = "${getScheduleKeyPrefix()}$KEY_COURSES"
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<Course>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存课程列表
     */
    fun saveCourses(courses: List<Course>, notify: Boolean = true) {
        val key = "${getScheduleKeyPrefix()}$KEY_COURSES"
        val json = gson.toJson(courses)
        prefs.edit().putString(key, json).apply()
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
        onCourseChanged?.invoke("add", courseWithSchedule.id)
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
            onCourseChanged?.invoke("update", course.id)
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
        onCourseChanged?.invoke("delete", courseId)
        return courses
    }

    /**
     * 获取指定周次、星期的课程
     */
    fun getCoursesForDay(week: Int, dayOfWeek: Int): List<Course> {
        return getAllCourses().filter {
            it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week)
        }.sortedBy { it.startSection }
    }

    /**
     * 获取指定周次的所有课程
     */
    fun getCoursesForWeek(week: Int): List<Course> {
        return getAllCourses().filter { it.isActiveInWeek(week) }
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
        prefs.edit().putInt(key, week).apply()
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
        prefs.edit().putInt(key, weeks).apply()
        notifyCourseChanged("settings")
    }

    /**
     * 获取开始上课日期（格式 YYYY/MM/DD）
     * 如果存储的值不是日期格式（如旧版本的时间格式），自动回退为当天日期
     */
    fun getClassStartTime(): String {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_START_TIME"
        val cal = java.util.Calendar.getInstance()
        val default = String.format("%04d/%02d/%02d",
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
        prefs.edit().putString(key, default).apply()
        return default
    }

    /**
     * 设置开始上课日期
     */
    fun setClassStartTime(time: String) {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_START_TIME"
        prefs.edit().putString(key, time).apply()
        notifyCourseChanged("settings")
    }

    /**
     * 获取显示周末的天数集合
     */
    fun getShowWeekendDays(): Set<Int> {
        val key = "${getScheduleKeyPrefix()}$KEY_SHOW_WEEKEND"
        val json = prefs.getString(key, "")
        return try {
            if (json.isNullOrBlank()) emptySet()
            else json.split(",").map { it.trim().toInt() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * 设置显示周末的天数集合
     */
    fun setShowWeekendDays(days: Set<Int>) {
        val key = "${getScheduleKeyPrefix()}$KEY_SHOW_WEEKEND"
        prefs.edit().putString(key, days.joinToString(",")).apply()
        notifyCourseChanged("settings")
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
        prefs.edit().putBoolean(key, show).apply()
        notifyCourseChanged("settings")
    }

    /**
     * 获取上午节数
     */
    fun getMorningSections(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_MORNING_SECTIONS"
        return safeGetInt(key, 4)
    }

    /**
     * 设置上午节数
     */
    fun setMorningSections(count: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_MORNING_SECTIONS"
        prefs.edit().putInt(key, count).apply()
        notifyCourseChanged("settings")
    }

    /**
     * 获取下午节数
     */
    fun getAfternoonSections(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_AFTERNOON_SECTIONS"
        return safeGetInt(key, 4)
    }

    /**
     * 设置下午节数
     */
    fun setAfternoonSections(count: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_AFTERNOON_SECTIONS"
        prefs.edit().putInt(key, count).apply()
        notifyCourseChanged("settings")
    }

    /**
     * 获取晚上节数
     */
    fun getEveningSections(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_EVENING_SECTIONS"
        return safeGetInt(key, 4)
    }

    /**
     * 设置晚上节数
     */
    fun setEveningSections(count: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_EVENING_SECTIONS"
        prefs.edit().putInt(key, count).apply()
        notifyCourseChanged("settings")
    }

    /**
     * 获取指定时段的节次时间映射
     * period: "morning" / "afternoon" / "evening"
     * key: 时段内相对节次号 (1-6), value: "HH:mm-HH:mm"
     */
    fun getPeriodTimes(period: String): Map<Int, String> {
        val key = "${getScheduleKeyPrefix()}$KEY_SECTION_TIMES"
        val json = prefs.getString(key, null) ?: return getDefaultTimesForPeriod(period)
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val raw: Map<*, *> = gson.fromJson(json, type) ?: return getDefaultTimesForPeriod(period)
            val result = mutableMapOf<Int, String>()
            for ((k, v) in raw) {
                val strKey = k as String
                if (strKey.startsWith("${period}_")) {
                    val idx = strKey.removePrefix("${period}_").toIntOrNull()
                    if (idx != null) result[idx] = v as String
                }
            }
            if (result.isEmpty()) {
                migrateOldSectionTimes(raw, period)
            } else result
        } catch (e: Exception) {
            getDefaultTimesForPeriod(period)
        }
    }

    /**
     * 保存指定时段的节次时间映射
     */
    fun savePeriodTimes(period: String, times: Map<Int, String>) {
        val sharedKey = "${getScheduleKeyPrefix()}$KEY_SECTION_TIMES"
        val type = object : TypeToken<Map<String, String>>() {}.type
        val existing: MutableMap<String, String> = try {
            val json = prefs.getString(sharedKey, null)
            if (json != null) gson.fromJson(json, type) ?: mutableMapOf()
            else mutableMapOf()
        } catch (_: Exception) { mutableMapOf() }
        val toRemove = existing.keys.filter { it.startsWith("${period}_") }
        toRemove.forEach { existing.remove(it) }
        for ((idx, v) in times) {
            existing["${period}_$idx"] = v
        }
        prefs.edit().putString(sharedKey, gson.toJson(existing)).apply()
        notifyCourseChanged("settings")
    }

    private fun getDefaultTimesForPeriod(period: String): Map<Int, String> = when (period) {
        "morning" -> Course.defaultMorningTimes
        "afternoon" -> Course.defaultAfternoonTimes
        "evening" -> Course.defaultEveningTimes
        else -> emptyMap()
    }

    /**
     * 迁移旧版节次时间格式到新的时段分离格式
     * 旧格式：全局绝对节次号作为key（如 "3" -> "10:00-10:45"）
     * 新格式：时段前缀+相对节次号（如 "morning_3" -> "10:00-10:45"）
     * 迁移逻辑：根据上午/下午/晚上节数，将绝对节次映射到对应时段的相对节次
     */
    private fun migrateOldSectionTimes(raw: Map<*, *>, period: String): Map<Int, String> {
        val oldMap = mutableMapOf<Int, String>()
        for ((k, v) in raw) {
            val intKey = (k as? String)?.toIntOrNull() ?: continue
            oldMap[intKey] = v as String
        }
        if (oldMap.isEmpty()) return getDefaultTimesForPeriod(period)
        val m = getMorningSections()
        val a = getAfternoonSections()
        val defaults = getDefaultTimesForPeriod(period)
        val result = mutableMapOf<Int, String>()
        when (period) {
            "morning" -> for (i in 1..6) result[i] = oldMap[i] ?: defaults[i] ?: ""
            "afternoon" -> for (i in 1..6) result[i] = oldMap[m + i] ?: defaults[i] ?: ""
            "evening" -> for (i in 1..6) result[i] = oldMap[m + a + i] ?: defaults[i] ?: ""
        }
        savePeriodTimes(period, result)
        return result
    }

    fun getQuickTimeEnabled(): Boolean {
        val key = "${getScheduleKeyPrefix()}$KEY_QUICK_TIME_ENABLED"
        return prefs.getBoolean(key, false)
    }

    fun setQuickTimeEnabled(enabled: Boolean) {
        val key = "${getScheduleKeyPrefix()}$KEY_QUICK_TIME_ENABLED"
        prefs.edit().putBoolean(key, enabled).apply()
        notifyCourseChanged("settings")
    }

    fun getClassDuration(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_DURATION"
        return safeGetInt(key, 45)
    }

    fun setClassDuration(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_DURATION"
        prefs.edit().putInt(key, minutes).apply()
        notifyCourseChanged("settings")
    }

    fun getShortBreak(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_SHORT_BREAK"
        return safeGetInt(key, 10)
    }

    fun setShortBreak(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_SHORT_BREAK"
        prefs.edit().putInt(key, minutes).apply()
        notifyCourseChanged("settings")
    }

    fun getLongBreakEnabled(): Boolean {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_enabled"
        return prefs.getBoolean(key, false)
    }

    fun setLongBreakEnabled(enabled: Boolean) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_enabled"
        prefs.edit().putBoolean(key, enabled).apply()
        notifyCourseChanged("settings")
    }

    fun getLongBreakMorning(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_morning"
        return safeGetInt(key, 20)
    }

    fun setLongBreakMorning(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_morning"
        prefs.edit().putInt(key, minutes).apply()
        notifyCourseChanged("settings")
    }

    fun getLongBreakAfternoon(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_afternoon"
        return safeGetInt(key, 20)
    }

    fun setLongBreakAfternoon(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_afternoon"
        prefs.edit().putInt(key, minutes).apply()
        notifyCourseChanged("settings")
    }

    fun getLongBreakEvening(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_evening"
        return safeGetInt(key, 20)
    }

    fun setLongBreakEvening(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_evening"
        prefs.edit().putInt(key, minutes).apply()
        notifyCourseChanged("settings")
    }

    fun getLongBreakMorningSection(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_morning_section"
        return safeGetInt(key, 2)
    }

    fun setLongBreakMorningSection(section: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_morning_section"
        prefs.edit().putInt(key, section).apply()
        notifyCourseChanged("settings")
    }

    fun getLongBreakAfternoonSection(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_afternoon_section"
        return safeGetInt(key, 2)
    }

    fun setLongBreakAfternoonSection(section: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_afternoon_section"
        prefs.edit().putInt(key, section).apply()
        notifyCourseChanged("settings")
    }

    fun getLongBreakEveningSection(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_evening_section"
        return safeGetInt(key, 2)
    }

    fun setLongBreakEveningSection(section: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_evening_section"
        prefs.edit().putInt(key, section).apply()
        notifyCourseChanged("settings")
    }

    fun getMorningStartHour(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_MORNING_START"
        return safeGetInt(key, 8)
    }

    fun setMorningStartHour(hour: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_MORNING_START"
        prefs.edit().putInt(key, hour).apply()
        notifyCourseChanged("settings")
    }

    fun getMorningStartMinute(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_MORNING_START}_min"
        return safeGetInt(key, 0)
    }

    fun setMorningStartMinute(minute: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_MORNING_START}_min"
        prefs.edit().putInt(key, minute).apply()
        notifyCourseChanged("settings")
    }

    fun getAfternoonStartHour(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_AFTERNOON_START"
        return safeGetInt(key, 14)
    }

    fun setAfternoonStartHour(hour: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_AFTERNOON_START"
        prefs.edit().putInt(key, hour).apply()
        notifyCourseChanged("settings")
    }

    fun getAfternoonStartMinute(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_AFTERNOON_START}_min"
        return safeGetInt(key, 0)
    }

    fun setAfternoonStartMinute(minute: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_AFTERNOON_START}_min"
        prefs.edit().putInt(key, minute).apply()
        notifyCourseChanged("settings")
    }

    fun getEveningStartHour(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_EVENING_START"
        return safeGetInt(key, 18)
    }

    fun setEveningStartHour(hour: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_EVENING_START"
        prefs.edit().putInt(key, hour).apply()
        notifyCourseChanged("settings")
    }

    fun getEveningStartMinute(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_EVENING_START}_min"
        return safeGetInt(key, 30)
    }

    fun setEveningStartMinute(minute: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_EVENING_START}_min"
        prefs.edit().putInt(key, minute).apply()
        notifyCourseChanged("settings")
    }

    fun getPreClassReminder(): Boolean {
        return prefs.getBoolean(KEY_PRE_CLASS_REMINDER, false)
    }

    fun setPreClassReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRE_CLASS_REMINDER, enabled).apply()
    }

    fun getPreClassReminderMinutes(): Int {
        return safeGetInt(KEY_PRE_CLASS_REMINDER_MINUTES, 20)
    }

    fun setPreClassReminderMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_PRE_CLASS_REMINDER_MINUTES, minutes).apply()
    }

    fun getNextDayReminder(): Boolean {
        return prefs.getBoolean(KEY_NEXT_DAY_REMINDER, false)
    }

    fun setNextDayReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NEXT_DAY_REMINDER, enabled).apply()
    }

    fun getNextDayReminderHour(): Int {
        return safeGetInt(KEY_NEXT_DAY_REMINDER_HOUR, 21)
    }

    fun setNextDayReminderHour(hour: Int) {
        prefs.edit().putInt(KEY_NEXT_DAY_REMINDER_HOUR, hour).apply()
    }

    fun getNextDayReminderMinute(): Int {
        return safeGetInt(KEY_NEXT_DAY_REMINDER_MINUTE, 0)
    }

    fun setNextDayReminderMinute(minute: Int) {
        prefs.edit().putInt(KEY_NEXT_DAY_REMINDER_MINUTE, minute).apply()
    }

    fun getIslandNotification(): Boolean {
        return prefs.getBoolean(KEY_ISLAND_NOTIFICATION, false)
    }

    fun setIslandNotification(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ISLAND_NOTIFICATION, enabled).apply()
    }

    /**
     * 获取指定星期和节次范围已占用的周次列表
     */
    fun getOccupiedWeeks(
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        excludeId: String? = null
    ): Set<Int> {
        val occupied = mutableSetOf<Int>()
        getAllCourses().forEach { course ->
            if (excludeId != null && course.id == excludeId) return@forEach
            if (course.dayOfWeek == dayOfWeek &&
                course.startSection <= endSection &&
                course.endSection >= startSection
            ) {
                // 如果有具体的周次列表，直接添加
                if (course.selectedWeeks.isNotEmpty()) {
                    occupied.addAll(course.selectedWeeks)
                } else {
                    // 否则使用范围判断
                    for (week in course.startWeek..course.endWeek) {
                        when (course.weekType) {
                            Course.WEEK_TYPE_ODD -> if (week % 2 == 1) occupied.add(week)
                            Course.WEEK_TYPE_EVEN -> if (week % 2 == 0) occupied.add(week)
                            else -> occupied.add(week)
                        }
                    }
                }
            }
        }
        return occupied
    }

    /**
     * 获取指定时间段的所有课程（用于显示多课程详情）
     */
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
        val json = prefs.getString(KEY_SCHEDULE_NAMES, null)
        return try {
            if (json.isNullOrBlank()) listOf("默认课表")
            else gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: listOf("默认课表")
        } catch (e: Exception) {
            listOf("默认课表")
        }
    }

    /**
     * 保存课表名称列表
     */
   internal fun saveScheduleNames(names: List<String>) {
        val json = gson.toJson(names)
        prefs.edit().putString(KEY_SCHEDULE_NAMES, json).commit()
    }

    /**
     * 获取当前选中的课表ID
     */
    fun getCurrentScheduleId(): String {
        return prefs.getString(KEY_CURRENT_SCHEDULE_ID, "默认课表") ?: "默认课表"
    }

    /**
     * 设置当前选中的课表ID
     */
    fun setCurrentScheduleId(scheduleId: String) {
        prefs.edit().putString(KEY_CURRENT_SCHEDULE_ID, scheduleId).apply()
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
        val editor = prefs.edit()
        for ((key, value) in prefs.all) {
            if (key.startsWith(currentPrefix)) {
                val settingName = key.removePrefix(currentPrefix)
                // 跳过课程数据，只复制设置
                if (settingName == KEY_COURSES) continue
                val newKey = "$newPrefix$settingName"
                when (value) {
                    is Int -> editor.putInt(newKey, value)
                    is Boolean -> editor.putBoolean(newKey, value)
                    is String -> editor.putString(newKey, value)
                    is Float -> editor.putFloat(newKey, value)
                    is Long -> editor.putLong(newKey, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(newKey, value as Set<String>)
                    }
                }
            }
        }
        // 将开始上课日期设为今天，当前周数设为第1周
        val today = java.time.LocalDate.now()
        val todayStr = String.format("%04d/%02d/%02d", today.year, today.monthValue, today.dayOfMonth)
        editor.putString("$newPrefix$KEY_CLASS_START_TIME", todayStr)
        editor.putInt("$newPrefix$KEY_CURRENT_WEEK", 1)
        editor.commit()
        return names
    }

    /**
     * 删除课表
     */
    fun deleteSchedule(name: String): List<String> {
        val names = getScheduleNames().toMutableList()
        names.remove(name)
        saveScheduleNames(names)
        // 删除该课表的所有数据
        val prefix = "$SCHEDULE_KEY_PREFIX${name}_"
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(prefix)) {
                editor.remove(key)
            }
        }
        editor.apply()
        // 如果删除的是当前课表，切换到第一个课表
        if (getCurrentScheduleId() == name && names.isNotEmpty()) {
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
            names[index] = newName
            saveScheduleNames(names)
            // 如果重命名的是当前课表，更新当前课表ID
            if (getCurrentScheduleId() == oldName) {
                setCurrentScheduleId(newName)
            }
        }
        notifyCourseChanged("settings")
        return names
    }

    /**
     * 切换到指定课表
     */
    fun switchToSchedule(scheduleId: String) {
        setCurrentScheduleId(scheduleId)
    }

    fun isShiftModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHIFT_MODE, false)
    }

    fun setShiftModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHIFT_MODE, enabled).apply()
    }

    fun getShiftSelectedSchedules(): List<String> {
        val json = prefs.getString(KEY_SHIFT_SELECTED_SCHEDULES, null)
        return try {
            if (json.isNullOrBlank()) emptyList()
            else gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setShiftSelectedSchedules(names: List<String>) {
        val json = gson.toJson(names)
        prefs.edit().putString(KEY_SHIFT_SELECTED_SCHEDULES, json).apply()
    }

    fun getDefaultHomepage(): String {
        return prefs.getString(KEY_DEFAULT_HOMEPAGE, "课程表") ?: "课程表"
    }

    fun setDefaultHomepage(homepage: String) {
        prefs.edit().putString(KEY_DEFAULT_HOMEPAGE, homepage).apply()
    }

    // --- 壁纸设置 ---

    fun saveWallpaperState(offsetX: Float, offsetY: Float, scale: Float) {
        prefs.edit()
            .putFloat(KEY_WALLPAPER_OFFSET_X, offsetX)
            .putFloat(KEY_WALLPAPER_OFFSET_Y, offsetY)
            .putFloat(KEY_WALLPAPER_SCALE, scale)
            .apply()
    }

    fun getWallpaperOffsetX(): Float = prefs.getFloat(KEY_WALLPAPER_OFFSET_X, 0f)

    fun getWallpaperOffsetY(): Float = prefs.getFloat(KEY_WALLPAPER_OFFSET_Y, 0f)

    fun getWallpaperScale(): Float = prefs.getFloat(KEY_WALLPAPER_SCALE, 1f)

    fun saveWallpaperBitmap(bitmap: android.graphics.Bitmap): Boolean {
        return try {
            val file = java.io.File(appContext.filesDir, WALLPAPER_FILE_NAME)
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadWallpaperBitmap(): android.graphics.Bitmap? {
        val file = java.io.File(appContext.filesDir, WALLPAPER_FILE_NAME)
        if (!file.exists()) return null
        return try {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    fun clearWallpaper() {
        val file = java.io.File(appContext.filesDir, WALLPAPER_FILE_NAME)
        if (file.exists()) file.delete()
        prefs.edit()
            .remove(KEY_WALLPAPER_OFFSET_X)
            .remove(KEY_WALLPAPER_OFFSET_Y)
            .remove(KEY_WALLPAPER_SCALE)
            .apply()
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
        prefs.edit().putLong(KEY_CURRENT_COMBINATION_ID, id).apply()
    }

    /** 添加新搭配，返回新 ID */
    fun addCombination(): Long {
        val ids = getCombinationIds().toMutableList()
        val newId = (ids.maxOrNull() ?: -1L) + 1L
        ids.add(newId)
        prefs.edit()
            .putString(KEY_COMBINATION_IDS, ids.joinToString(","))
            .putFloat("${KEY_COMBINATION_OFFSET_X_PREFIX}$newId", 0f)
            .putFloat("${KEY_COMBINATION_OFFSET_Y_PREFIX}$newId", 0f)
            .putFloat("${KEY_COMBINATION_SCALE_PREFIX}$newId", 1f)
            .apply()
        return newId
    }

    /** 删除指定搭配，包括其壁纸、快照和 SharedPreferences 中的状态 */
    fun deleteCombination(id: Long) {
        val ids = getCombinationIds().toMutableList()
        if (!ids.remove(id)) return
        prefs.edit()
            .putString(KEY_COMBINATION_IDS, ids.joinToString(","))
            .remove("${KEY_COMBINATION_OFFSET_X_PREFIX}$id")
            .remove("${KEY_COMBINATION_OFFSET_Y_PREFIX}$id")
            .remove("${KEY_COMBINATION_SCALE_PREFIX}$id")
            .remove("${KEY_COMBINATION_CARD_BLUR_PREFIX}$id")
            .remove("${KEY_COMBINATION_CARD_ALPHA_PREFIX}$id")
            .remove("${KEY_COMBINATION_WALLPAPER_BRIGHTNESS_PREFIX}$id")
            .remove("${KEY_COMBINATION_SHOW_BREAK_DIVIDERS_PREFIX}$id")
            .apply()
        // 删除壁纸与快照文件
        java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.png").delete()
        java.io.File(appContext.filesDir, "${COMBINATION_SNAPSHOT_PREFIX}$id.png").delete()
        // 同步清理内存缓存，防止读到已删除搭配的旧 bitmap
        wallpaperCache.remove(id)
        // 若删除的是当前搭配，且仍有其他搭配，则切换到第一个
        if (ids.isNotEmpty() && getCurrentCombinationId() == id) {
            setCurrentCombinationId(ids.first())
        } else if (ids.isEmpty()) {
            // 删光后重新创建一个默认搭配 id=0
            prefs.edit().putString(KEY_COMBINATION_IDS, "0").apply()
            setCurrentCombinationId(0L)
        }
    }

    /** 保存指定搭配的壁纸图片 */
    fun saveCombinationWallpaper(id: Long, bitmap: android.graphics.Bitmap): Boolean {
        return try {
            val file = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.png")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            // 同步更新内存缓存，避免下次读取再用旧图重新解码
            wallpaperCache.put(id, bitmap)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 加载指定搭配的壁纸图片（带内存缓存，命中时零延迟） */
    fun loadCombinationWallpaper(id: Long): android.graphics.Bitmap? {
        // 1. 缓存命中：直接返回，避免重复解码
        wallpaperCache.get(id)?.let { return it }
        // 2. 缓存未命中：从磁盘解码
        val file = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}$id.png")
        if (!file.exists()) return null
        return try {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.also { bmp ->
                wallpaperCache.put(id, bmp)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 保存指定搭配的偏移和缩放 */
    fun saveCombinationState(id: Long, offsetX: Float, offsetY: Float, scale: Float) {
        prefs.edit()
            .putFloat("${KEY_COMBINATION_OFFSET_X_PREFIX}$id", offsetX)
            .putFloat("${KEY_COMBINATION_OFFSET_Y_PREFIX}$id", offsetY)
            .putFloat("${KEY_COMBINATION_SCALE_PREFIX}$id", scale)
            .apply()
    }

    fun getCombinationOffsetX(id: Long): Float = prefs.getFloat("${KEY_COMBINATION_OFFSET_X_PREFIX}$id", 0f)
    fun getCombinationOffsetY(id: Long): Float = prefs.getFloat("${KEY_COMBINATION_OFFSET_Y_PREFIX}$id", 0f)
    fun getCombinationScale(id: Long): Float = prefs.getFloat("${KEY_COMBINATION_SCALE_PREFIX}$id", 1f)

    fun saveCombinationCardBlur(id: Long, blurRadius: Float) {
        prefs.edit()
            .putFloat("${KEY_COMBINATION_CARD_BLUR_PREFIX}$id", blurRadius)
            .apply()
    }

    fun getCombinationCardBlur(id: Long): Float = prefs.getFloat("${KEY_COMBINATION_CARD_BLUR_PREFIX}$id", 0f)

    fun saveCombinationCardAlpha(id: Long, alpha: Float) {
        prefs.edit()
            .putFloat("${KEY_COMBINATION_CARD_ALPHA_PREFIX}$id", alpha)
            .apply()
    }

    fun getCombinationCardAlpha(id: Long): Float = prefs.getFloat("${KEY_COMBINATION_CARD_ALPHA_PREFIX}$id", 0.15f)

    fun saveCombinationCardHeight(id: Long, height: Float) {
        prefs.edit()
            .putFloat("${KEY_COMBINATION_CARD_HEIGHT_PREFIX}$id", height)
            .apply()
    }

    fun getCombinationCardHeight(id: Long): Float = prefs.getFloat("${KEY_COMBINATION_CARD_HEIGHT_PREFIX}$id", 54f)

    fun saveCombinationCardCornerRadius(id: Long, cornerRadius: Float) {
        prefs.edit()
            .putFloat("${KEY_COMBINATION_CARD_CORNER_PREFIX}$id", cornerRadius)
            .apply()
    }

    fun getCombinationCardCornerRadius(id: Long): Float = prefs.getFloat("${KEY_COMBINATION_CARD_CORNER_PREFIX}$id", 8f)

    fun saveCombinationWallpaperBrightness(id: Long, brightness: Float) {
        prefs.edit()
            .putFloat("${KEY_COMBINATION_WALLPAPER_BRIGHTNESS_PREFIX}$id", brightness)
            .apply()
    }

    fun getCombinationWallpaperBrightness(id: Long): Float = prefs.getFloat("${KEY_COMBINATION_WALLPAPER_BRIGHTNESS_PREFIX}$id", 0f)

    fun saveCombinationShowBreakDividers(id: Long, show: Boolean) {
        prefs.edit()
            .putBoolean("${KEY_COMBINATION_SHOW_BREAK_DIVIDERS_PREFIX}$id", show)
            .apply()
    }

    fun getCombinationShowBreakDividers(id: Long): Boolean = prefs.getBoolean("${KEY_COMBINATION_SHOW_BREAK_DIVIDERS_PREFIX}$id", true)

    /** 保存指定搭配的完整快照（课表+壁纸预览） */
    fun saveCombinationSnapshot(id: Long, bitmap: android.graphics.Bitmap): Boolean {
        return try {
            val file = java.io.File(appContext.filesDir, "${COMBINATION_SNAPSHOT_PREFIX}$id.png")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 加载指定搭配的完整快照 */
    fun loadCombinationSnapshot(id: Long): android.graphics.Bitmap? {
        val file = java.io.File(appContext.filesDir, "${COMBINATION_SNAPSHOT_PREFIX}$id.png")
        if (!file.exists()) return null
        return try {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    /** 迁移：如果只有旧的单搭配数据（无 combination_ids），将其作为 id=0 的搭配 */
    fun migrateToCombinationsIfNeeded() {
        if (prefs.contains(KEY_COMBINATION_IDS)) return
        // 首次迁移：将现有单搭配数据作为 id=0
        prefs.edit()
            .putString(KEY_COMBINATION_IDS, "0")
            .putLong(KEY_CURRENT_COMBINATION_ID, 0L)
            .putFloat("${KEY_COMBINATION_OFFSET_X_PREFIX}0", getWallpaperOffsetX())
            .putFloat("${KEY_COMBINATION_OFFSET_Y_PREFIX}0", getWallpaperOffsetY())
            .putFloat("${KEY_COMBINATION_SCALE_PREFIX}0", getWallpaperScale())
            .apply()
        // 复制壁纸文件
        val oldFile = java.io.File(appContext.filesDir, WALLPAPER_FILE_NAME)
        if (oldFile.exists()) {
            val newFile = java.io.File(appContext.filesDir, "${COMBINATION_WALLPAPER_PREFIX}0.png")
            try { oldFile.copyTo(newFile, overwrite = true) } catch (_: Exception) {}
        }
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
            KEY_NAV_BAR_STYLE
        )
        for ((key, value) in prefs.all) {
            if (key.startsWith(SCHEDULE_KEY_PREFIX) || key in relevantKeys) {
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
        val editor = prefs.edit()
        // 先清除所有旧的课表数据
        for ((key) in prefs.all) {
            if (key.startsWith(SCHEDULE_KEY_PREFIX)) {
                editor.remove(key)
            }
        }
        // 清除全局课表配置
        editor.remove(KEY_SCHEDULE_NAMES)
        editor.remove(KEY_CURRENT_SCHEDULE_ID)
        editor.remove(KEY_SHIFT_MODE)
        editor.remove(KEY_SHIFT_SELECTED_SCHEDULES)

        // 写入备份数据
        for ((key, value) in data) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Number -> {
                    val numVal = value.toDouble()
                    val intVal = numVal.toInt()
                    if (numVal == intVal.toDouble()) {
                        editor.putInt(key, intVal)
                    } else {
                        editor.putFloat(key, numVal.toFloat())
                    }
                }
                is List<*> -> {
                    // Set<String> 被导出为 List，需要还原
                    @Suppress("UNCHECKED_CAST")
                    val list = value.filterIsInstance<String>()
                    editor.putString(key, gson.toJson(list))
                }
            }
        }
        editor.apply()
        onCourseChanged?.invoke("restore", "")
    }

    fun getSectionsForSchedule(scheduleId: String): Triple<Int, Int, Int> {
        val prefix = "$SCHEDULE_KEY_PREFIX${scheduleId}_"
        val morning = safeGetInt("${prefix}$KEY_MORNING_SECTIONS", 4)
        val afternoon = safeGetInt("${prefix}$KEY_AFTERNOON_SECTIONS", 4)
        val evening = safeGetInt("${prefix}$KEY_EVENING_SECTIONS", 4)
        return Triple(morning, afternoon, evening)
    }

    // ============ 课表设置导出/导入（用于云同步） ============

    private fun getSettingsLastModified(scheduleId: String): Long {
        val prefix = "$SCHEDULE_KEY_PREFIX${scheduleId}_"
        return prefs.getLong("${prefix}_settings_last_modified", 0L)
    }

    private fun setSettingsLastModified(scheduleId: String, time: Long) {
        val prefix = "$SCHEDULE_KEY_PREFIX${scheduleId}_"
        prefs.edit().putLong("${prefix}_settings_last_modified", time).apply()
    }

    /**
     * 导出单个课表的设置（不含课程数据）
     * 上传时使用当前时间作为时间戳
     */
    fun exportScheduleSettings(scheduleId: String, useCurrentTime: Boolean = false): Map<String, Any> {
        val prefix = "$SCHEDULE_KEY_PREFIX${scheduleId}_"
        val settings = mutableMapOf<String, Any>()

        // 用显式 getter 读取，确保默认值也能导出
        settings[KEY_TOTAL_WEEKS] = safeGetInt("${prefix}$KEY_TOTAL_WEEKS", 20)
        settings[KEY_CLASS_START_TIME] = prefs.getString("${prefix}$KEY_CLASS_START_TIME", "") ?: ""
        settings[KEY_CURRENT_WEEK] = safeGetInt("${prefix}$KEY_CURRENT_WEEK", 1)
        settings[KEY_SHOW_WEEKEND] = prefs.getString("${prefix}$KEY_SHOW_WEEKEND", "") ?: ""
        settings[KEY_SHOW_NON_CURRENT_WEEK] = prefs.getBoolean("${prefix}$KEY_SHOW_NON_CURRENT_WEEK", true)
        settings[KEY_MORNING_SECTIONS] = safeGetInt("${prefix}$KEY_MORNING_SECTIONS", 4)
        settings[KEY_AFTERNOON_SECTIONS] = safeGetInt("${prefix}$KEY_AFTERNOON_SECTIONS", 4)
        settings[KEY_EVENING_SECTIONS] = safeGetInt("${prefix}$KEY_EVENING_SECTIONS", 4)
        settings[KEY_SECTION_TIMES] = prefs.getString("${prefix}$KEY_SECTION_TIMES", "") ?: ""
        settings[KEY_QUICK_TIME_ENABLED] = prefs.getBoolean("${prefix}$KEY_QUICK_TIME_ENABLED", false)
        settings[KEY_CLASS_DURATION] = safeGetInt("${prefix}$KEY_CLASS_DURATION", 45)
        settings[KEY_SHORT_BREAK] = safeGetInt("${prefix}$KEY_SHORT_BREAK", 10)

        settings["lastModified"] = if (useCurrentTime) System.currentTimeMillis() else getSettingsLastModified(scheduleId)
        return settings
    }

    /**
     * 获取本地设置的最后修改时间
     */
    fun getLocalSettingsLastModified(scheduleId: String): Long {
        return getSettingsLastModified(scheduleId)
    }

    /**
     * 导入单个课表的设置
     */
    fun importScheduleSettings(scheduleId: String, settings: Map<String, Any>) {
        val prefix = "$SCHEDULE_KEY_PREFIX${scheduleId}_"
        val editor = prefs.edit()
        for ((key, value) in settings) {
            if (key == "lastModified") continue // 跳过时间戳，单独处理
            val fullKey = "$prefix$key"
            when (value) {
                is String -> editor.putString(fullKey, value)
                is Double -> editor.putInt(fullKey, value.toInt())
                is Number -> {
                    val numVal = value.toDouble()
                    val intVal = numVal.toInt()
                    if (numVal == intVal.toDouble()) {
                        editor.putInt(fullKey, intVal)
                    } else {
                        editor.putFloat(fullKey, numVal.toFloat())
                    }
                }
                is Boolean -> editor.putBoolean(fullKey, value)
            }
        }
        // 保存导入的时间戳
        val remoteTime = (settings["lastModified"] as? Number)?.toLong() ?: System.currentTimeMillis()
        editor.putLong("${prefix}_settings_last_modified", remoteTime)
        editor.apply()
        onCourseChanged?.invoke("settings", "")
    }
}
