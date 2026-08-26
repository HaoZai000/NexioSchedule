package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.widget.CourseWidgetProvider
import com.haooz.chedule.widget.CourseWidgetProvider4x7
import com.haooz.chedule.widget.CourseWidgetProviderStandard
import com.haooz.chedule.widget.TodayCourseWidgetProvider
import com.haooz.chedule.widget.TodayCourseWidgetProvider4x7
import com.haooz.chedule.widget.TodayCourseWidgetProviderStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    // 当前是否处于假期（无课程的周）
    private val _isHoliday = MutableStateFlow(false)

    init {
        repository.onCourseChanged = { _, _ ->
            viewModelScope.launch(Dispatchers.IO) {
                loadCourses()
                // 课程增删改后重新调度提醒闹钟：新增/修改的课程也能及时注册精确闹钟，
                // 并让 widget 刷新链按最新课程重新计算（否则新课程在提醒窗口内无任何驱动源）
                rescheduleReminders()
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
        _isHoliday.value = isWeekHoliday(calculatedWeek)
        _dataVersion.value++
    }

    private fun loadCourses() {
        _courses.value = repository.getAllCourses()
        _isHoliday.value = isWeekHoliday(_currentWeek.value)
        updateWidgets()
    }

    /** 同步更新 _courses 并异步刷新小组件（供课程变更操作使用，保证 UI 即时响应） */
    private fun applyCoursesAndRefreshWidgets(courses: List<Course>) {
        _courses.value = courses
        _isHoliday.value = isWeekHoliday(_currentWeek.value)
        updateWidgets()
    }

    private fun updateWidgets() {
        viewModelScope.launch {
            CourseWidgetProvider.updateAllWidgets(getApplication())
            CourseWidgetProvider4x7.updateAllWidgets(getApplication())
            CourseWidgetProviderStandard.updateAllWidgets(getApplication())
            TodayCourseWidgetProvider.updateAllWidgets(getApplication())
            TodayCourseWidgetProvider4x7.updateAllWidgets(getApplication())
            TodayCourseWidgetProviderStandard.updateAllWidgets(getApplication())
        }
    }

    private fun loadData() {
        loadEssentialData()
        loadCourses()
    }

    /**
     * 重新加载所有数据（切换课表后调用）
     * 返回 Job 供调用方等待加载完成后再截取新课表快照
     */
    fun reloadCourses(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
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
        _isHoliday.value = isWeekHoliday(calculatedWeek)
        // 日期/周次可能变化，立即重调度提醒（含开学前取消残留闹钟）
        rescheduleReminders()
    }

    /**
     * 设置当前周次（今天是第几周）
     * 保留原日期的星期几，通过周次差值调整：新日期 = 原日期 + (旧周次 - 新周次) * 7
     */
    fun setCurrentWeek(week: Int) {
        val oldWeek = _currentWeek.value
        _currentWeek.value = week
        repository.setCurrentWeek(week)
        _isHoliday.value = isWeekHoliday(week)

        if (week != oldWeek) {
            val oldStartDate = LocalDate.parse(_classStartTime.value.replace("/", "-"))
            val newStartDate = oldStartDate.plusDays((oldWeek - week).toLong() * 7)
            val newStartDateStr = newStartDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            _classStartTime.value = newStartDateStr
            repository.setClassStartTime(newStartDateStr)
        }
        // 周次/开学日期已变化，立即重调度提醒
        rescheduleReminders()
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
        _isHoliday.value = isWeekHoliday(newWeek)
        // 开学日期已变化，立即重调度提醒（开学前取消已注册的课前/次日闹钟）
        rescheduleReminders()
    }

    /**
     * 设置总周数
     */
    fun setTotalWeeks(weeks: Int) {
        _totalWeeks.value = weeks
        repository.setTotalWeeks(weeks)
        _isHoliday.value = isWeekHoliday(_currentWeek.value)
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
            val week = daysBetween.floorDiv(7).toInt() + 1
            // 不做任何 clamp，允许返回 0 或负数（学期未开始）或超过 totalWeeks（学期已结束）
            week
        } catch (_: Exception) {
            _isSemesterStarted.value = true
            1
        }
    }

    /**
     * 判断指定周次是否处于假期（该周及之后均无课程）
     * 假期条件：week > totalWeeks，或当前周无课程且之后所有周也无课程
     */
    fun isWeekHoliday(week: Int): Boolean {
        val total = _totalWeeks.value
        if (week > total) return true
        if (week < 1) return false
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        return week > lastWeekWithCourses
    }

    /**
     * 添加课程
     */
    fun addCourse(course: Course) {
        applyCoursesAndRefreshWidgets(repository.addCourse(course))
    }

    /**
     * 更新课程
     */
    fun updateCourse(course: Course) {
        applyCoursesAndRefreshWidgets(repository.updateCourse(course))
    }

    /**
     * 按旧名称更新所有同名课程
     */
    fun updateCoursesByName(oldName: String, updated: Course) {
        applyCoursesAndRefreshWidgets(repository.updateCoursesByName(oldName, updated))
    }

    /**
     * 删除课程
     */
    fun deleteCourse(courseId: String) {
        applyCoursesAndRefreshWidgets(repository.deleteCourse(courseId))
    }

    /**
     * 仅删除指定周次的课程实例
     */
    fun deleteCourseForWeek(courseId: String, week: Int) {
        applyCoursesAndRefreshWidgets(repository.deleteCourseForWeek(courseId, week))
    }

    /**
     * 调课-移动：将指定周次的课程实例移动到新位置（仅影响该周）
     */
    fun moveCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int
    ) {
        applyCoursesAndRefreshWidgets(repository.moveCourseForWeek(
            sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection
        ))
    }

    /**
     * 调课-覆盖：将指定周次的源课程移动到目标位置，并删除该周在目标位置上的所有冲突课程
     */
    fun overwriteCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int
    ) {
        applyCoursesAndRefreshWidgets(repository.overwriteCourseForWeek(
            sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection
        ))
    }

    /**
     * 调课-交换：将指定周次的源课程与目标课程互换位置（仅影响该周）
     */
    fun swapCoursesForWeek(sourceCourseId: String, targetCourseId: String, week: Int) {
        applyCoursesAndRefreshWidgets(repository.swapCoursesForWeek(sourceCourseId, targetCourseId, week))
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
        return repository.getOccupiedWeeks(
            dayOfWeek,
            startSection,
            endSection,
            excludeIds,
            startTime,
            endTime
        )
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
