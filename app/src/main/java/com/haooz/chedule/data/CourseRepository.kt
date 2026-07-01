package com.haooz.chedule.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 课程数据仓库 - 使用 SharedPreferences 存储
 */
class CourseRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
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
        private const val KEY_SHIFT_MODE = "shift_mode_enabled"
        private const val KEY_SHIFT_SELECTED_SCHEDULES = "shift_selected_schedules"
        private const val KEY_DEFAULT_HOMEPAGE = "default_homepage"
        private const val SCHEDULE_KEY_PREFIX = "schedule_"
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
    fun saveCourses(courses: List<Course>) {
        val key = "${getScheduleKeyPrefix()}$KEY_COURSES"
        val json = gson.toJson(courses)
        prefs.edit().putString(key, json).apply()
    }

    /**
     * 添加课程
     */
    fun addCourse(course: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        courses.add(course)
        saveCourses(courses)
        return courses
    }

    /**
     * 更新课程
     */
    fun updateCourse(course: Course): List<Course> {
        val courses = getAllCourses().toMutableList()
        val index = courses.indexOfFirst { it.id == course.id }
        if (index != -1) {
            courses[index] = course
            saveCourses(courses)
        }
        return courses
    }

    /**
     * 删除课程
     */
    fun deleteCourse(courseId: String): List<Course> {
        val courses = getAllCourses().toMutableList()
        courses.removeAll { it.id == courseId }
        saveCourses(courses)
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
        return prefs.getInt(key, 1)
    }

    /**
     * 设置当前周次
     */
    fun setCurrentWeek(week: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_CURRENT_WEEK"
        prefs.edit().putInt(key, week).apply()
    }

    /**
     * 获取总周数
     */
    fun getTotalWeeks(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_TOTAL_WEEKS"
        return prefs.getInt(key, 20)
    }

    /**
     * 设置总周数
     */
    fun setTotalWeeks(weeks: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_TOTAL_WEEKS"
        prefs.edit().putInt(key, weeks).apply()
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
    }

    /**
     * 获取上午节数
     */
    fun getMorningSections(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_MORNING_SECTIONS"
        return prefs.getInt(key, 4)
    }

    /**
     * 设置上午节数
     */
    fun setMorningSections(count: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_MORNING_SECTIONS"
        prefs.edit().putInt(key, count).apply()
    }

    /**
     * 获取下午节数
     */
    fun getAfternoonSections(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_AFTERNOON_SECTIONS"
        return prefs.getInt(key, 4)
    }

    /**
     * 设置下午节数
     */
    fun setAfternoonSections(count: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_AFTERNOON_SECTIONS"
        prefs.edit().putInt(key, count).apply()
    }

    /**
     * 获取晚上节数
     */
    fun getEveningSections(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_EVENING_SECTIONS"
        return prefs.getInt(key, 4)
    }

    /**
     * 设置晚上节数
     */
    fun setEveningSections(count: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_EVENING_SECTIONS"
        prefs.edit().putInt(key, count).apply()
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
    }

    private fun getDefaultTimesForPeriod(period: String): Map<Int, String> = when (period) {
        "morning" -> Course.defaultMorningTimes
        "afternoon" -> Course.defaultAfternoonTimes
        "evening" -> Course.defaultEveningTimes
        else -> emptyMap()
    }

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
    }

    fun getClassDuration(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_DURATION"
        return prefs.getInt(key, 45)
    }

    fun setClassDuration(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_CLASS_DURATION"
        prefs.edit().putInt(key, minutes).apply()
    }

    fun getShortBreak(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_SHORT_BREAK"
        return prefs.getInt(key, 10)
    }

    fun setShortBreak(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_SHORT_BREAK"
        prefs.edit().putInt(key, minutes).apply()
    }

    fun getLongBreakEnabled(): Boolean {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_enabled"
        return prefs.getBoolean(key, false)
    }

    fun setLongBreakEnabled(enabled: Boolean) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_enabled"
        prefs.edit().putBoolean(key, enabled).apply()
    }

    fun getLongBreakMorning(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_morning"
        return prefs.getInt(key, 20)
    }

    fun setLongBreakMorning(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_morning"
        prefs.edit().putInt(key, minutes).apply()
    }

    fun getLongBreakAfternoon(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_afternoon"
        return prefs.getInt(key, 20)
    }

    fun setLongBreakAfternoon(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_afternoon"
        prefs.edit().putInt(key, minutes).apply()
    }

    fun getLongBreakEvening(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_evening"
        return prefs.getInt(key, 20)
    }

    fun setLongBreakEvening(minutes: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_LONG_BREAK}_evening"
        prefs.edit().putInt(key, minutes).apply()
    }

    fun getMorningStartHour(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_MORNING_START"
        return prefs.getInt(key, 8)
    }

    fun setMorningStartHour(hour: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_MORNING_START"
        prefs.edit().putInt(key, hour).apply()
    }

    fun getMorningStartMinute(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_MORNING_START}_min"
        return prefs.getInt(key, 0)
    }

    fun setMorningStartMinute(minute: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_MORNING_START}_min"
        prefs.edit().putInt(key, minute).apply()
    }

    fun getAfternoonStartHour(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_AFTERNOON_START"
        return prefs.getInt(key, 14)
    }

    fun setAfternoonStartHour(hour: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_AFTERNOON_START"
        prefs.edit().putInt(key, hour).apply()
    }

    fun getAfternoonStartMinute(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_AFTERNOON_START}_min"
        return prefs.getInt(key, 0)
    }

    fun setAfternoonStartMinute(minute: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_AFTERNOON_START}_min"
        prefs.edit().putInt(key, minute).apply()
    }

    fun getEveningStartHour(): Int {
        val key = "${getScheduleKeyPrefix()}$KEY_EVENING_START"
        return prefs.getInt(key, 18)
    }

    fun setEveningStartHour(hour: Int) {
        val key = "${getScheduleKeyPrefix()}$KEY_EVENING_START"
        prefs.edit().putInt(key, hour).apply()
    }

    fun getEveningStartMinute(): Int {
        val key = "${getScheduleKeyPrefix()}${KEY_EVENING_START}_min"
        return prefs.getInt(key, 30)
    }

    fun setEveningStartMinute(minute: Int) {
        val key = "${getScheduleKeyPrefix()}${KEY_EVENING_START}_min"
        prefs.edit().putInt(key, minute).apply()
    }

    fun getPreClassReminder(): Boolean {
        return prefs.getBoolean(KEY_PRE_CLASS_REMINDER, false)
    }

    fun setPreClassReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRE_CLASS_REMINDER, enabled).apply()
    }

    fun getPreClassReminderMinutes(): Int {
        return prefs.getInt(KEY_PRE_CLASS_REMINDER_MINUTES, 20)
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
        return prefs.getInt(KEY_NEXT_DAY_REMINDER_HOUR, 21)
    }

    fun setNextDayReminderHour(hour: Int) {
        prefs.edit().putInt(KEY_NEXT_DAY_REMINDER_HOUR, hour).apply()
    }

    fun getNextDayReminderMinute(): Int {
        return prefs.getInt(KEY_NEXT_DAY_REMINDER_MINUTE, 0)
    }

    fun setNextDayReminderMinute(minute: Int) {
        prefs.edit().putInt(KEY_NEXT_DAY_REMINDER_MINUTE, minute).apply()
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
     * 获取指定时间段是否有多个课程
     */
    fun hasMultipleCoursesAtSlot(
        week: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): Boolean {
        return getCoursesAtSlot(week, dayOfWeek, startSection, endSection).size > 1
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

    fun getSectionsForSchedule(scheduleId: String): Triple<Int, Int, Int> {
        val prefix = "$SCHEDULE_KEY_PREFIX${scheduleId}_"
        val morning = prefs.getInt("${prefix}$KEY_MORNING_SECTIONS", 4)
        val afternoon = prefs.getInt("${prefix}$KEY_AFTERNOON_SECTIONS", 4)
        val evening = prefs.getInt("${prefix}$KEY_EVENING_SECTIONS", 4)
        return Triple(morning, afternoon, evening)
    }
}
