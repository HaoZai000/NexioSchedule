package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.widget.CourseWidgetProviderStandard
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

class CourseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    // 每次重新加载数据时递增，用于强制 UI 重组
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    /** 上次向小组件广播刷新的日期，用于同一天内内容未变时跳过广播 */
    private var lastWidgetRefreshDate: LocalDate? = null

    /** 节假日/调休等外部数据变更时 bump，让今日页 remember 失效（不重载课程列表） */
    fun bumpDataVersion() {
        _dataVersion.value++
    }

    private val _currentWeek = MutableStateFlow(1)
    val currentWeek: StateFlow<Int> = _currentWeek.asStateFlow()

    private val _isSemesterStarted = MutableStateFlow(true)
    val isSemesterStarted: StateFlow<Boolean> = _isSemesterStarted.asStateFlow()

    private val _totalWeeks = MutableStateFlow(20)
    val totalWeeks: StateFlow<Int> = _totalWeeks.asStateFlow()

    private val _classStartTime = MutableStateFlow("2025-09-01")
    val classStartTime: StateFlow<String> = _classStartTime.asStateFlow()

    // 1-7 对应周一~周日
    private val _selectedDay = MutableStateFlow(1)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    private val _selectedStartSection = MutableStateFlow(1)
    val selectedStartSection: StateFlow<Int> = _selectedStartSection.asStateFlow()

    private val _selectedEndSection = MutableStateFlow(2)
    val selectedEndSection: StateFlow<Int> = _selectedEndSection.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    /** 空白格添加时的默认周次；调课日应传 followWeek（被调来源周） */
    private val _addDialogDefaultWeeks = MutableStateFlow<Set<Int>>(emptySet())
    val addDialogDefaultWeeks: StateFlow<Set<Int>> = _addDialogDefaultWeeks.asStateFlow()

    private val _showJumpWeekDialog = MutableStateFlow(false)
    val showJumpWeekDialog: StateFlow<Boolean> = _showJumpWeekDialog.asStateFlow()

    private val _editingCourse = MutableStateFlow<Course?>(null)
    val editingCourse: StateFlow<Course?> = _editingCourse.asStateFlow()

    private val _isHoliday = MutableStateFlow(false)

    private val courseChangedListener: (String, String) -> Unit = { _, _ ->
        viewModelScope.launch(Dispatchers.IO) {
            loadCourses()
            // 课程变更后重排闹钟并驱动 widget 刷新链，否则新课程在提醒窗口内无驱动源
            rescheduleReminders()
        }
    }

    init {
        repository.addCourseChangedListener(courseChangedListener)
        loadEssentialData()
        viewModelScope.launch(Dispatchers.IO) {
            loadCourses()
        }
    }

    private fun rescheduleReminders() {
        val context = getApplication<Application>()
        CourseReminderHelper.startReminderService(context, repository)
    }

    /**
     * 只在「会影响课表渲染的数据」真的变了才返回 true。
     *
     * 不要在这里无条件 `_dataVersion.value++`：
     * MainScheduleScreen 的 weekendDaysByWeek / weekFilteredCourses 都以 dataVersion 为 key，
     * 预计算「全周次 × 全课程」两张表。无脑 bump 会让每次从二级页返回时在主线程整表重算，
     * 而这恰好落在 MainActivity 刚 resume、主界面刚可见的那几帧上 → 返回时卡一下。
     */
    private fun loadEssentialData(): Boolean {
        val newTotalWeeks = repository.getTotalWeeks()
        val newClassStartTime = repository.getClassStartTime()
        var changed = _totalWeeks.value != newTotalWeeks || _classStartTime.value != newClassStartTime
        _totalWeeks.value = newTotalWeeks
        _classStartTime.value = newClassStartTime

        val calculatedWeek = calculateCurrentWeekFromDate(newClassStartTime)
        if (_currentWeek.value != calculatedWeek) changed = true
        _currentWeek.value = calculatedWeek
        repository.setCurrentWeek(calculatedWeek)

        val holiday = isWeekHoliday(calculatedWeek)
        if (_isHoliday.value != holiday) changed = true
        _isHoliday.value = holiday
        return changed
    }

    private fun loadCourses(): Boolean {
        val newCourses = repository.getAllCourses()
        var changed = _courses.value != newCourses
        _courses.value = newCourses
        val holiday = isWeekHoliday(_currentWeek.value)
        if (_isHoliday.value != holiday) changed = true
        _isHoliday.value = holiday
        updateWidgetsIfNeeded(contentChanged = changed)
        return changed
    }

    private fun applyCoursesAndRefreshWidgets(courses: List<Course>) {
        _courses.value = courses
        // 调课/交换可能不改变 size，必须 bump 才能让 dayRange 等按 dataVersion 记忆的 UI 重算
        _dataVersion.value++
        _isHoliday.value = isWeekHoliday(_currentWeek.value)
        updateWidgets()
    }

    private fun updateWidgets() {
        viewModelScope.launch {
            com.haooz.chedule.widget.WidgetUpdateCache.updateInstalledWidgets(getApplication())
        }
    }

    /**
     * 小组件广播：内容变了、或跨天（今日/明日预告与周次都随时间变）才发。
     * 每次 resume 都无条件广播，会让 provider 在本进程主线程重建所有 RemoteViews，
     * 正好和主界面 resume 后的重组撞在一起。跨天仍会刷，不会漏掉日期滚动。
     */
    private fun updateWidgetsIfNeeded(contentChanged: Boolean) {
        val today = LocalDate.now()
        if (!contentChanged && lastWidgetRefreshDate == today) return
        lastWidgetRefreshDate = today
        updateWidgets()
    }

    private fun loadData() {
        val essentialChanged = loadEssentialData()
        val coursesChanged = loadCourses()
        // 显式加载：只有数据真的变了才驱动 UI 重算（见 loadEssentialData 注释）
        if (essentialChanged || coursesChanged) _dataVersion.value++
    }

    // 返回 Job：调用方需等待加载完成后再截取新课表快照
    fun reloadCourses(): Job {
        com.haooz.chedule.ui.utils.FeatureLog.t("课程数据", "reload")
        return viewModelScope.launch(Dispatchers.IO) {
            loadData()
        }
    }

    // 云同步导入后刷新周次等基本数据
    fun refreshEssentialData() {
        _totalWeeks.value = repository.getTotalWeeks()
        _classStartTime.value = repository.getClassStartTime()
        val calculatedWeek = calculateCurrentWeekFromDate(_classStartTime.value)
        _currentWeek.value = calculatedWeek
        _isHoliday.value = isWeekHoliday(calculatedWeek)
        rescheduleReminders()
    }

    // 同步调整开学日期：新开学日 = 原开学日 + (旧周次-新周次)*7，保留星期几对齐
    fun setCurrentWeek(week: Int) {
        com.haooz.chedule.ui.utils.FeatureLog.t("课表", "set_week", "week=$week")
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
        rescheduleReminders()
    }

    fun setClassStartTime(time: String) {
        // 教务导入常给 yyyy-MM-dd；先规范化，避免被 getClassStartTime 重置成今天
        val normalized = CourseRepository.normalizeClassStartDate(time) ?: return
        _classStartTime.value = normalized
        repository.setClassStartTime(normalized)

        val newWeek = calculateCurrentWeekFromDate(normalized)
        _currentWeek.value = newWeek
        repository.setCurrentWeek(newWeek)
        _isHoliday.value = isWeekHoliday(newWeek)
        rescheduleReminders()
    }

    fun setTotalWeeks(weeks: Int) {
        if (weeks <= 0) return
        _totalWeeks.value = weeks
        repository.setTotalWeeks(weeks)
        _isHoliday.value = isWeekHoliday(_currentWeek.value)
    }

    // 周次 = (今天 - 开学周一) / 7 + 1；开学周一为开始上课日期所在周的周一
    private fun calculateCurrentWeekFromDate(startDate: String): Int {
        return try {
            val today = LocalDate.now()
            val start = LocalDate.parse(startDate.replace("/", "-"))
            val startMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
            _isSemesterStarted.value = !today.isBefore(startMonday)
            val daysBetween = ChronoUnit.DAYS.between(startMonday, today)
            val week = daysBetween.floorDiv(7).toInt() + 1
            // 刻意不 clamp：允许 0/负数（未开学）或 >totalWeeks（已结束）
            week
        } catch (_: Exception) {
            _isSemesterStarted.value = true
            1
        }
    }

    // 假期 = 超出总周数，或已过最后一个有课周
    fun isWeekHoliday(week: Int): Boolean {
        val total = _totalWeeks.value
        if (week > total) return true
        if (week < 1) return false
        val lastWeekWithCourses = repository.getLastWeekWithCourses()
        return week > lastWeekWithCourses
    }

    fun addCourse(course: Course) {
        applyCoursesAndRefreshWidgets(repository.addCourse(course))
    }

    fun updateCourse(course: Course) {
        applyCoursesAndRefreshWidgets(repository.updateCourse(course))
    }

    fun updateCoursesByName(oldName: String, updated: Course) {
        applyCoursesAndRefreshWidgets(repository.updateCoursesByName(oldName, updated))
    }

    fun deleteCourse(courseId: String) {
        applyCoursesAndRefreshWidgets(repository.deleteCourse(courseId))
    }

    fun deleteCourseForWeek(courseId: String, week: Int) {
        applyCoursesAndRefreshWidgets(repository.deleteCourseForWeek(courseId, week))
    }

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

    // 调课-覆盖：移动到目标位置并删除该周冲突课程
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

    fun swapCoursesForWeek(sourceCourseId: String, targetCourseId: String, week: Int) {
        applyCoursesAndRefreshWidgets(repository.swapCoursesForWeek(sourceCourseId, targetCourseId, week))
    }

    fun replaceCourses(courses: List<Course>) {
        val currentScheduleId = repository.getCurrentScheduleId()
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

    fun appendCourses(courses: List<Course>) {
        val currentScheduleId = repository.getCurrentScheduleId()
        // 补 scheduleId，id 冲突时重新生成
        val existingIds = _courses.value.map { it.id }.toSet()
        val coursesWithSchedule = courses.map { course ->
            val withSchedule = if (course.scheduleId.isEmpty()) {
                course.copy(scheduleId = currentScheduleId)
            } else {
                course
            }
            if (withSchedule.id.isEmpty() || withSchedule.id in existingIds) {
                withSchedule.copy(id = java.util.UUID.randomUUID().toString())
            } else {
                withSchedule
            }
        }
        val merged = _courses.value + coursesWithSchedule
        repository.saveCourses(merged)
        _courses.value = merged
        _dataVersion.value++
    }

    fun showAddDialog(
        dayOfWeek: Int? = null,
        startSection: Int? = null,
        endSection: Int? = null,
        defaultWeeks: Set<Int> = emptySet(),
    ) {
        _editingCourse.value = null
        _selectedDay.value = dayOfWeek ?: 0
        if (startSection != null) {
            _selectedStartSection.value = startSection
            _selectedEndSection.value = endSection ?: startSection
        }
        _addDialogDefaultWeeks.value = defaultWeeks
        _showAddDialog.value = true
    }

    fun showEditDialog(course: Course) {
        _editingCourse.value = course
        _addDialogDefaultWeeks.value = emptySet()
        _showAddDialog.value = true
    }

    fun hideDialog() {
        _showAddDialog.value = false
        _editingCourse.value = null
        _addDialogDefaultWeeks.value = emptySet()
    }

    fun showJumpWeekDialog() {
        _showJumpWeekDialog.value = true
    }

    fun hideJumpWeekDialog() {
        _showJumpWeekDialog.value = false
    }

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
        repository.removeCourseChangedListener(courseChangedListener)
    }
}
