package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.widget.CourseWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    // 学期是否已开始（开学日期的周一 <= 今天）
    private val _isSemesterStarted = MutableStateFlow(true)
    val isSemesterStarted: StateFlow<Boolean> = _isSemesterStarted.asStateFlow()

    // 总周数
    private val _totalWeeks = MutableStateFlow(20)
    val totalWeeks: StateFlow<Int> = _totalWeeks.asStateFlow()

    // 开始上课日期
    private val _classStartTime = MutableStateFlow("2025-09-01")
    val classStartTime: StateFlow<String> = _classStartTime.asStateFlow()

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

    init {
        repository.onCourseChanged = { action, _ ->
            viewModelScope.launch(Dispatchers.IO) {
                loadCourses()
            }
        }
        loadEssentialData()
        viewModelScope.launch(Dispatchers.IO) {
            loadCourses()
        }
        rescheduleReminders()
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

    /**
     * 重新加载所有数据（切换课表后调用）
     */
    fun reloadCourses() {
        viewModelScope.launch(Dispatchers.IO) {
            loadData()
        }
    }

    /**
     * 刷新周次和日期等基本数据（云同步导入后调用）
     */
    fun refreshEssentialData() {
        _totalWeeks.value = repository.getTotalWeeks()
        _classStartTime.value = repository.getClassStartTime()
        val calculatedWeek = calculateCurrentWeekFromDate(_classStartTime.value)
        _currentWeek.value = calculatedWeek
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
            // 判断学期是否已开始
            _isSemesterStarted.value = !today.isBefore(startMonday)
            val daysBetween = ChronoUnit.DAYS.between(startMonday, today)
            val week = (daysBetween / 7 + 1).toInt()
            // 不做任何 clamp，允许返回 0 或负数（学期未开始）或超过 totalWeeks（学期已结束）
            week
        } catch (e: Exception) {
            _isSemesterStarted.value = true
            1
        }
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
    fun getCoursesForDay(week: Int, dayOfWeek: Int, showNonCurrentWeek: Boolean = true): List<Course> {
        val courses = _courses.value.filter {
            it.dayOfWeek == dayOfWeek
        }.sortedBy { it.startSection }

        return if (showNonCurrentWeek) {
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
        val currentScheduleId = repository.getCurrentScheduleId()
        // 为导入的课程设置 scheduleId
        val coursesWithSchedule = courses.map { course ->
            if (course.scheduleId.isEmpty()) {
                course.copy(scheduleId = currentScheduleId)
            } else {
                course
            }
        }
        repository.saveCourses(coursesWithSchedule)
        _courses.value = coursesWithSchedule
        _dataVersion.value++
    }

    /**
     * 显示添加对话框
     */
    fun showAddDialog(dayOfWeek: Int? = null, startSection: Int? = null, endSection: Int? = null) {
        _editingCourse.value = null
        _selectedDay.value = dayOfWeek ?: 0
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

    override fun onCleared() {
        super.onCleared()
        repository.onCourseChanged = null
    }
}
