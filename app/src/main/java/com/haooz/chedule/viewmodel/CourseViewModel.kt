package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.haooz.chedule.reminder.CourseReminderHelper
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.widget.CourseWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 课程表 ViewModel
 */
class CourseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    // 所有课程
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    // 数据版本号，每次重新加载数据时递增，用于强制 UI 重组
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    // 当前周次
    private val _currentWeek = MutableStateFlow(1)
    val currentWeek: StateFlow<Int> = _currentWeek.asStateFlow()

    // 总周数
    private val _totalWeeks = MutableStateFlow(20)
    val totalWeeks: StateFlow<Int> = _totalWeeks.asStateFlow()

    // 开始上课日期
    private val _classStartTime = MutableStateFlow("2025-09-01")
    val classStartTime: StateFlow<String> = _classStartTime.asStateFlow()

    // 显示周末设置 (Set of day numbers to show: 6=周六, 7=周日)
    private val _showWeekendDays = MutableStateFlow(emptySet<Int>())
    val showWeekendDays: StateFlow<Set<Int>> = _showWeekendDays.asStateFlow()

    // 兼容旧逻辑
    val showWeekend: Boolean get() = _showWeekendDays.value.isNotEmpty()

    // 显示非本周课程
    private val _showNonCurrentWeek = MutableStateFlow(true)
    val showNonCurrentWeek: StateFlow<Boolean> = _showNonCurrentWeek.asStateFlow()

    // 上午节数
    private val _morningSections = MutableStateFlow(4)
    val morningSections: StateFlow<Int> = _morningSections.asStateFlow()

    // 下午节数
    private val _afternoonSections = MutableStateFlow(4)
    val afternoonSections: StateFlow<Int> = _afternoonSections.asStateFlow()

    // 晚上节数
    private val _eveningSections = MutableStateFlow(4)
    val eveningSections: StateFlow<Int> = _eveningSections.asStateFlow()

    // 各时段节次时间映射 (key: 时段内相对节次 1-6, value: "HH:mm-HH:mm")
    private val _morningTimes = MutableStateFlow<Map<Int, String>>(Course.defaultMorningTimes)
    val morningTimes: StateFlow<Map<Int, String>> = _morningTimes.asStateFlow()

    private val _afternoonTimes = MutableStateFlow<Map<Int, String>>(Course.defaultAfternoonTimes)
    val afternoonTimes: StateFlow<Map<Int, String>> = _afternoonTimes.asStateFlow()

    private val _eveningTimes = MutableStateFlow<Map<Int, String>>(Course.defaultEveningTimes)
    val eveningTimes: StateFlow<Map<Int, String>> = _eveningTimes.asStateFlow()

    // 兼容：合并为绝对编号的扁平映射
    val sectionTimes: StateFlow<Map<Int, String>> = run {
        val combined = combine(_morningTimes, _afternoonTimes, _eveningTimes, _morningSections, _afternoonSections) { m, a, e, ms, _ ->
            buildMap {
                m.forEach { (k, v) -> put(k, v) }
                a.forEach { (k, v) -> put(ms + k, v) }
                e.forEach { (k, v) -> put(ms + _afternoonSections.value + k, v) }
            }
        }
        MutableStateFlow(Course.defaultSectionTimes).also { flow ->
            viewModelScope.launch { combined.collect { flow.value = it } }
        }
    }

    // 课前提醒开关
    private val _preClassReminder = MutableStateFlow(false)
    val preClassReminder: StateFlow<Boolean> = _preClassReminder.asStateFlow()

    // 课前提醒提前分钟数
    private val _preClassReminderMinutes = MutableStateFlow(20)
    val preClassReminderMinutes: StateFlow<Int> = _preClassReminderMinutes.asStateFlow()

    // 次日课程提醒开关
    private val _nextDayReminder = MutableStateFlow(false)
    val nextDayReminder: StateFlow<Boolean> = _nextDayReminder.asStateFlow()

    // 次日课程提醒时间（小时）
    private val _nextDayReminderHour = MutableStateFlow(21)
    val nextDayReminderHour: StateFlow<Int> = _nextDayReminderHour.asStateFlow()

    // 次日课程提醒时间（分钟）
    private val _nextDayReminderMinute = MutableStateFlow(0)
    val nextDayReminderMinute: StateFlow<Int> = _nextDayReminderMinute.asStateFlow()

    // 当前选中的星期 (1-7)
    private val _selectedDay = MutableStateFlow(1)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    // 当前选中的开始节次
    private val _selectedStartSection = MutableStateFlow(1)
    val selectedStartSection: StateFlow<Int> = _selectedStartSection.asStateFlow()

    // 当前选中的结束节次
    private val _selectedEndSection = MutableStateFlow(2)
    val selectedEndSection: StateFlow<Int> = _selectedEndSection.asStateFlow()

    // 是否显示添加/编辑对话框
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    // 是否显示跳转周数弹窗
    private val _showJumpWeekDialog = MutableStateFlow(false)
    val showJumpWeekDialog: StateFlow<Boolean> = _showJumpWeekDialog.asStateFlow()

    // 正在编辑的课程
    private val _editingCourse = MutableStateFlow<Course?>(null)
    val editingCourse: StateFlow<Course?> = _editingCourse.asStateFlow()

    // 是否显示设置页面
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    // 当前课表名称
    private val _currentScheduleName = MutableStateFlow(repository.getCurrentScheduleId())
    val currentScheduleName: StateFlow<String> = _currentScheduleName.asStateFlow()

    // 课表列表及摘要（预加载缓存）
    private val _scheduleNames = MutableStateFlow(repository.getScheduleNames())
    val scheduleNames: StateFlow<List<String>> = _scheduleNames.asStateFlow()

    private val _scheduleSummaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val scheduleSummaries: StateFlow<Map<String, String>> = _scheduleSummaries.asStateFlow()

    // 排班模式状态
    private val _isShiftMode = MutableStateFlow(false)
    val isShiftMode: StateFlow<Boolean> = _isShiftMode.asStateFlow()

    // 排班模式选中的课表
    private val _shiftSelectedSchedules = MutableStateFlow<List<String>>(emptyList())
    val shiftSelectedSchedules: StateFlow<List<String>> = _shiftSelectedSchedules.asStateFlow()

    // 默认首页
    private val _defaultHomepage = MutableStateFlow("课程表")
    val defaultHomepage: StateFlow<String> = _defaultHomepage.asStateFlow()

    // 排班模式各课表课程缓存 scheduleName -> List<Course>
    private val _shiftScheduleCourses = MutableStateFlow<Map<String, List<Course>>>(emptyMap())
    val shiftScheduleCourses: StateFlow<Map<String, List<Course>>> = _shiftScheduleCourses.asStateFlow()

    // 排班模式各课表节数缓存 scheduleName -> Triple(morning, afternoon, evening)
    private val _shiftScheduleSections = MutableStateFlow<Map<String, Triple<Int, Int, Int>>>(emptyMap())
    val shiftScheduleSections: StateFlow<Map<String, Triple<Int, Int, Int>>> = _shiftScheduleSections.asStateFlow()

    init {
        // 轻量 SP 读取在主线程同步完成，确保 currentWeek/totalWeeks 立即就绪
        loadEssentialData()
        // 课程列表 Gson 反序列化较重，移到 IO 线程
        viewModelScope.launch(Dispatchers.IO) {
            loadCourses()
            refreshScheduleList()
        }
        rescheduleReminders()
        _isShiftMode.value = repository.isShiftModeEnabled()
        _shiftSelectedSchedules.value = repository.getShiftSelectedSchedules()
        if (_isShiftMode.value) {
            viewModelScope.launch(Dispatchers.IO) { reloadShiftData() }
        }
    }

    private fun rescheduleReminders() {
        val context = getApplication<Application>()
        CourseReminderHelper.startReminderService(context, repository)
    }

    private fun loadEssentialData() {
        _totalWeeks.value = repository.getTotalWeeks()
        _classStartTime.value = repository.getClassStartTime()
        val calculatedWeek = calculateCurrentWeekFromDate(_classStartTime.value)
        _currentWeek.value = calculatedWeek
        repository.setCurrentWeek(calculatedWeek)
        _showWeekendDays.value = repository.getShowWeekendDays()
        _showNonCurrentWeek.value = repository.getShowNonCurrentWeek()
        _morningSections.value = repository.getMorningSections()
        _afternoonSections.value = repository.getAfternoonSections()
        _eveningSections.value = repository.getEveningSections()
        _morningTimes.value = repository.getPeriodTimes("morning")
        _afternoonTimes.value = repository.getPeriodTimes("afternoon")
        _eveningTimes.value = repository.getPeriodTimes("evening")
        _preClassReminder.value = repository.getPreClassReminder()
        _preClassReminderMinutes.value = repository.getPreClassReminderMinutes()
        _nextDayReminder.value = repository.getNextDayReminder()
        _nextDayReminderHour.value = repository.getNextDayReminderHour()
        _nextDayReminderMinute.value = repository.getNextDayReminderMinute()
        _defaultHomepage.value = repository.getDefaultHomepage()
        _dataVersion.value++
    }

    private fun loadCourses() {
        _courses.value = repository.getAllCourses()
        viewModelScope.launch { CourseWidgetProvider.updateAllWidgets(getApplication()) }
    }

    private fun loadData() {
        loadEssentialData()
        loadCourses()
    }

    private fun refreshScheduleList() {
        _scheduleNames.value = repository.getScheduleNames()
        val summaries = mutableMapOf<String, String>()
        _scheduleNames.value.forEach { name ->
            summaries[name] = repository.getScheduleSummary(name)
        }
        _scheduleSummaries.value = summaries
    }

    /**
     * 重新加载所有数据（切换课表后调用）
     */
    fun reloadCourses() {
        viewModelScope.launch(Dispatchers.IO) {
            loadData()
            _currentScheduleName.value = repository.getCurrentScheduleId()
            refreshScheduleList()
        }
    }

    /**
     * 设置当前周次（今天是第几周）
     * 保留原日期的星期几，通过周次差值调整：新日期 = 原日期 + (旧周次 - 新周次) * 7
     */
    fun setCurrentWeek(week: Int) {
        val oldWeek = _currentWeek.value
        _currentWeek.value = week
        repository.setCurrentWeek(week)

        if (week != oldWeek) {
            val oldStartDate = LocalDate.parse(_classStartTime.value.replace("/", "-"))
            val newStartDate = oldStartDate.plusDays((oldWeek - week).toLong() * 7)
            val newStartDateStr = newStartDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            _classStartTime.value = newStartDateStr
            repository.setClassStartTime(newStartDateStr)
        }
    }

    /**
     * 设置开始上课日期（同时更新当前周次）
     */
    fun setClassStartTime(time: String) {
        _classStartTime.value = time
        repository.setClassStartTime(time)

        val newWeek = calculateCurrentWeekFromDate(time)
        _currentWeek.value = newWeek
        repository.setCurrentWeek(newWeek)
    }

    /**
     * 根据当前周次计算该周的周一日期
     * 公式：周一 = 开学周一 + (week - 1) * 7
     * 开学周一 = 开始上课日期所在周的周一
     */
    private fun calculateStartDateFromWeek(week: Int): String {
        return try {
            val startDate = LocalDate.parse(_classStartTime.value.replace("/", "-"))
            // 找到开始日期所在周的周一
            val startMonday = startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())
            // 计算目标周的周一
            val targetMonday = startMonday.plusDays((week - 1).toLong() * 7)
            targetMonday.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        } catch (e: Exception) {
            _classStartTime.value
        }
    }

    /**
     * 设置总周数
     */
    fun setTotalWeeks(weeks: Int) {
        _totalWeeks.value = weeks
        repository.setTotalWeeks(weeks)
    }

    /**
     * 根据上课日期计算当前周次
     * 公式：当前周次 = (今天 - 开学周一) / 7 + 1
     * 开学周一 = 开始上课日期所在周的周一
     */
    private fun calculateCurrentWeekFromDate(startDate: String): Int {
        return try {
            val today = LocalDate.now()
            val start = LocalDate.parse(startDate.replace("/", "-"))
            // 找到开始日期所在周的周一
            val startMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
            val daysBetween = ChronoUnit.DAYS.between(startMonday, today)
            val week = (daysBetween / 7 + 1).toInt()
            // 确保周次在合理范围内
            week.coerceIn(1, _totalWeeks.value)
        } catch (e: Exception) {
            1
        }
    }

    /**
     * 根据周次差异计算新的上课日期
     * 公式：新上课日期 = 原始上课日期 + (原始周次 - 目标周次) * 7
     */
    private fun calculateStartDateFromWeekDifference(originalWeek: Int, targetWeek: Int): String {
        return try {
            val startDate = LocalDate.parse(_classStartTime.value.replace("/", "-"))
            val daysDifference = (originalWeek - targetWeek) * 7L
            val newStartDate = startDate.plusDays(daysDifference)
            newStartDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        } catch (e: Exception) {
            _classStartTime.value
        }
    }

    /**
     * 设置显示周末
     */
    fun setShowWeekendDays(days: Set<Int>) {
        _showWeekendDays.value = days
        repository.setShowWeekendDays(days)
    }

    /**
     * 设置显示非本周课程
     */
    fun setShowNonCurrentWeek(show: Boolean) {
        _showNonCurrentWeek.value = show
        repository.setShowNonCurrentWeek(show)
    }

    /**
     * 设置上午节数
     */
    fun setMorningSections(count: Int) {
        _morningSections.value = count
        repository.setMorningSections(count)
    }

    /**
     * 设置下午节数
     */
    fun setAfternoonSections(count: Int) {
        _afternoonSections.value = count
        repository.setAfternoonSections(count)
    }

    /**
     * 设置晚上节数
     */
    fun setEveningSections(count: Int) {
        _eveningSections.value = count
        repository.setEveningSections(count)
    }

    fun getMorningTimes(): Map<Int, String> = _morningTimes.value
    fun getAfternoonTimes(): Map<Int, String> = _afternoonTimes.value
    fun getEveningTimes(): Map<Int, String> = _eveningTimes.value

    fun saveMorningTimes(times: Map<Int, String>) {
        _morningTimes.value = times
        repository.savePeriodTimes("morning", times)
    }

    fun saveAfternoonTimes(times: Map<Int, String>) {
        _afternoonTimes.value = times
        repository.savePeriodTimes("afternoon", times)
    }

    fun saveEveningTimes(times: Map<Int, String>) {
        _eveningTimes.value = times
        repository.savePeriodTimes("evening", times)
    }

    fun resetSectionTimes() {
        _morningTimes.value = Course.defaultMorningTimes
        _afternoonTimes.value = Course.defaultAfternoonTimes
        _eveningTimes.value = Course.defaultEveningTimes
        repository.savePeriodTimes("morning", Course.defaultMorningTimes)
        repository.savePeriodTimes("afternoon", Course.defaultAfternoonTimes)
        repository.savePeriodTimes("evening", Course.defaultEveningTimes)
    }

    fun setPreClassReminder(enabled: Boolean) {
        _preClassReminder.value = enabled
        repository.setPreClassReminder(enabled)
    }

    fun setPreClassReminderMinutes(minutes: Int) {
        _preClassReminderMinutes.value = minutes
        repository.setPreClassReminderMinutes(minutes)
    }

    fun setNextDayReminder(enabled: Boolean) {
        _nextDayReminder.value = enabled
        repository.setNextDayReminder(enabled)
    }

    fun setNextDayReminderHour(hour: Int) {
        _nextDayReminderHour.value = hour
        repository.setNextDayReminderHour(hour)
    }

    fun setNextDayReminderMinute(minute: Int) {
        _nextDayReminderMinute.value = minute
        repository.setNextDayReminderMinute(minute)
    }

    fun setDefaultHomepage(homepage: String) {
        _defaultHomepage.value = homepage
        repository.setDefaultHomepage(homepage)
    }

    /**
     * 选择星期
     */
    fun selectDay(day: Int) {
        _selectedDay.value = day
    }

    /**
     * 获取指定周次、星期的课程（包括非本周课程）
     */
    fun getCoursesForDay(week: Int, dayOfWeek: Int): List<Course> {
        val courses = _courses.value.filter {
            it.dayOfWeek == dayOfWeek
        }.sortedBy { it.startSection }

        return if (_showNonCurrentWeek.value) {
            courses
        } else {
            courses.filter { it.isActiveInWeek(week) }
        }
    }

    /**
     * 添加课程
     */
    fun addCourse(course: Course) {
        _courses.value = repository.addCourse(course)
    }

    /**
     * 更新课程
     */
    fun updateCourse(course: Course) {
        _courses.value = repository.updateCourse(course)
    }

    /**
     * 删除课程
     */
    fun deleteCourse(courseId: String) {
        _courses.value = repository.deleteCourse(courseId)
    }

    /**
     * 替换所有课程（导入时使用）
     */
    fun replaceCourses(courses: List<Course>) {
        repository.saveCourses(courses)
        _courses.value = courses
        _dataVersion.value++
    }

    /**
     * 添加新课表并切换到它
     */
    fun addSchedule(name: String): List<String> {
        return repository.addSchedule(name)
    }

    /**
     * 创建新学期课表：复制当前课表所有设置（不含课程），创建后自动切换
     */
    fun createNewSemesterSchedule(name: String) {
        repository.createNewSemesterSchedule(name)
        repository.switchToSchedule(name)
        _currentScheduleName.value = name
        loadData()
        refreshScheduleList()
    }

    /**
     * 切换到指定课表
     */
    fun switchToSchedule(scheduleId: String) {
        repository.switchToSchedule(scheduleId)
        _currentScheduleName.value = scheduleId
        viewModelScope.launch(Dispatchers.IO) { loadData() }
    }

    /**
     * 保存课程到指定课表（不切换当前课表）
     */
    fun saveCoursesToSchedule(scheduleId: String, courses: List<Course>) {
        val oldScheduleId = repository.getCurrentScheduleId()
        repository.setCurrentScheduleId(scheduleId)
        repository.saveCourses(courses)
        repository.setCurrentScheduleId(oldScheduleId)
    }

    /**
     * 显示添加对话框
     */
    fun showAddDialog(dayOfWeek: Int? = null, startSection: Int? = null, endSection: Int? = null) {
        _editingCourse.value = null
        if (dayOfWeek != null) {
            _selectedDay.value = dayOfWeek
        }
        if (startSection != null) {
            _selectedStartSection.value = startSection
            _selectedEndSection.value = endSection ?: startSection
        }
        _showAddDialog.value = true
    }

    /**
     * 显示编辑对话框
     */
    fun showEditDialog(course: Course) {
        _editingCourse.value = course
        _showAddDialog.value = true
    }

    /**
     * 隐藏对话框
     */
    fun hideDialog() {
        _showAddDialog.value = false
        _editingCourse.value = null
    }

    /**
     * 显示跳转周数弹窗
     */
    fun showJumpWeekDialog() {
        _showJumpWeekDialog.value = true
    }

    /**
     * 隐藏跳转周数弹窗
     */
    fun hideJumpWeekDialog() {
        _showJumpWeekDialog.value = false
    }

    /**
     * 显示/隐藏设置
     */
    fun toggleSettings() {
        _showSettings.value = !_showSettings.value
    }

    /**
     * 检查课程时间冲突
     */
    fun hasTimeConflict(
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        startWeek: Int,
        endWeek: Int,
        excludeId: String? = null
    ): Boolean {
        return _courses.value.any { course ->
            if (excludeId != null && course.id == excludeId) return@any false
            course.dayOfWeek == dayOfWeek &&
                    course.startSection <= endSection &&
                    course.endSection >= startSection &&
                    course.startWeek <= endWeek &&
                    course.endWeek >= startWeek
        }
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
        return repository.getOccupiedWeeks(dayOfWeek, startSection, endSection, excludeId)
    }

    /**
     * 获取指定时间段的所有课程
     */
    fun getCoursesAtSlot(
        week: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): List<Course> {
        return repository.getCoursesAtSlot(week, dayOfWeek, startSection, endSection)
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
        return repository.hasMultipleCoursesAtSlot(week, dayOfWeek, startSection, endSection)
    }

    fun enterShiftMode() {
        _isShiftMode.value = true
        repository.setShiftModeEnabled(true)
        if (_shiftSelectedSchedules.value.isEmpty()) {
            _shiftSelectedSchedules.value = repository.getScheduleNames()
            repository.setShiftSelectedSchedules(_shiftSelectedSchedules.value)
        }
        reloadShiftData()
    }

    fun exitShiftMode() {
        _isShiftMode.value = false
        repository.setShiftModeEnabled(false)
    }

    fun setShiftSelectedSchedules(names: List<String>) {
        _shiftSelectedSchedules.value = names
        repository.setShiftSelectedSchedules(names)
        reloadShiftData()
    }

    private fun reloadShiftData() {
        val coursesMap = mutableMapOf<String, List<Course>>()
        val sectionsMap = mutableMapOf<String, Triple<Int, Int, Int>>()
        val validNames = mutableListOf<String>()
        for (name in _shiftSelectedSchedules.value) {
            if (name !in repository.getScheduleNames()) continue
            validNames.add(name)
            coursesMap[name] = repository.getCoursesForSchedule(name)
            sectionsMap[name] = repository.getSectionsForSchedule(name)
        }
        if (validNames.size != _shiftSelectedSchedules.value.size) {
            _shiftSelectedSchedules.value = validNames
            repository.setShiftSelectedSchedules(validNames)
        }
        _shiftScheduleCourses.value = coursesMap
        _shiftScheduleSections.value = sectionsMap
    }
}
