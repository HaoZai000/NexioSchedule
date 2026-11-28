package com.haooz.chedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.ScheduleEffects
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.platform.synchronizedOn
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.formatSlashDate
import com.haooz.chedule.data.util.minusDays
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.ui.utils.FeatureLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

class CourseViewModel : ViewModel() {

    private val repository = CourseRepository()
    private var loadedScheduleId = repository.getCurrentScheduleId()
    private var loadedTeachingWeekReorganizations =
        repository.getTeachingWeekReorganizations(loadedScheduleId)

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    // 每次重新加载数据时递增，用于强制 UI 重组
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    /** 上次向小组件广播刷新的日期，用于同一天内内容未变时跳过广播 */
    private var lastWidgetRefreshDate: LocalDate? = null
    private val courseRefreshLock = Any()

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

    /** 添加时的默认自定义时间（时间轴空白点开）：非空即弹窗以自定义时间模式打开 */
    private val _addDialogCustomTime = MutableStateFlow<Pair<String, String>?>(null)
    val addDialogCustomTime: StateFlow<Pair<String, String>?> = _addDialogCustomTime.asStateFlow()

    private val _showJumpWeekDialog = MutableStateFlow(false)
    val showJumpWeekDialog: StateFlow<Boolean> = _showJumpWeekDialog.asStateFlow()

    private val _editingCourse = MutableStateFlow<Course?>(null)
    val editingCourse: StateFlow<Course?> = _editingCourse.asStateFlow()

    private val _isHoliday = MutableStateFlow(false)

    private val courseChangedListener: (String, String) -> Unit = { action, changeId ->
        val reorganizationChanged = action == "settings" &&
            changeId == "teaching_week_reorganizations"
        val currentScheduleChanged = action == "settings" && changeId == "current_schedule"
        val restoreChanged = action == "restore"
        if (reorganizationChanged || currentScheduleChanged || restoreChanged) {
            _dataVersion.value++
        }
        viewModelScope.launch(ioDispatcher) {
            synchronizedOn(courseRefreshLock) {
                if (reorganizationChanged || currentScheduleChanged || restoreChanged) {
                    loadEssentialData()
                }
                loadCourses()
                // 课程变更后重排闹钟并驱动 widget 刷新链，否则新课程在提醒窗口内无驱动源
                rescheduleReminders()
            }
        }
    }

    init {
        repository.addCourseChangedListener(courseChangedListener)
        synchronizedOn(courseRefreshLock) { loadEssentialData() }
        viewModelScope.launch(ioDispatcher) {
            synchronizedOn(courseRefreshLock) { loadCourses() }
        }
    }

    private fun rescheduleReminders() {
        // 提醒服务是 Android 专有（AlarmManager），走 :core 的出口回调，
        // 由 NexioApplication 注入的 AndroidScheduleEffects 接过去。
        ScheduleEffects.rescheduleReminders()
    }

    /** 仅渲染数据变化时返回 true，避免让课表按所有周次重新计算。 */
    private fun loadEssentialData(): Boolean {
        val currentScheduleId = repository.getCurrentScheduleId()
        val currentRules = repository.getTeachingWeekReorganizations(currentScheduleId)
        var changed = currentScheduleId != loadedScheduleId ||
            currentRules != loadedTeachingWeekReorganizations
        loadedScheduleId = currentScheduleId
        loadedTeachingWeekReorganizations = currentRules
        val newTotalWeeks = repository.getTotalWeeks()
        val newClassStartTime = repository.getClassStartTime()
        if (_totalWeeks.value != newTotalWeeks || _classStartTime.value != newClassStartTime) {
            changed = true
        }
        _totalWeeks.value = newTotalWeeks
        _classStartTime.value = newClassStartTime

        val calculatedWeek = calculateCurrentWeekFromDate(newClassStartTime)
        if (_currentWeek.value != calculatedWeek) changed = true
        _currentWeek.value = calculatedWeek
        if (repository.getCurrentWeek() != calculatedWeek) {
            repository.setCurrentWeek(calculatedWeek)
        }

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
        synchronizedOn(courseRefreshLock) {
            _courses.value = courses
            // 调课/交换可能不改变 size，必须 bump 才能让 dayRange 等按 dataVersion 记忆的 UI 重算
            _dataVersion.value++
            _isHoliday.value = isWeekHoliday(_currentWeek.value)
            updateWidgets()
        }
    }

    private fun mutateCourses(mutation: () -> List<Course>) {
        synchronizedOn(courseRefreshLock) {
            applyCoursesAndRefreshWidgets(mutation())
        }
        ScheduleEffects.syncWearable("course-mutate")
        // 课程变了要重排闹钟：增删改/调课/交换都走 saveCourses(notify=false)
        viewModelScope.launch(ioDispatcher) { rescheduleReminders() }
        // 跨 ViewModel 实例广播：主页与管理页各自持有独立 CourseViewModel，
        // 不广播的话管理页改色/改时段后主页要重启 App 才刷新
        repository.notifyCoursesBulkChanged()
    }

    private fun updateWidgets() {
        viewModelScope.launch {
            ScheduleEffects.refreshWidgets()
        }
    }

    /**
     * 小组件广播：内容变了、或跨天（今日/明日预告与周次都随时间变）才发。每次 resume 都无条件广播会让 provider 在本进程主线程重建所有 RemoteViews，正好和主界面 resume 后的重组撞在一起。跨天仍会刷，不会漏掉日期滚动。
     */
    private fun updateWidgetsIfNeeded(contentChanged: Boolean) {
        val today = todayLocalDate()
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
        return viewModelScope.launch(ioDispatcher) {
            synchronizedOn(courseRefreshLock) { loadData() }
        }
    }

    // 云同步导入后刷新周次等基本数据
    fun refreshEssentialData() {
        synchronizedOn(courseRefreshLock) {
            if (loadEssentialData()) _dataVersion.value++
            rescheduleReminders()
        }
    }

    /** Refresh rule-dependent screen caches after an in-place tablet settings edit. */
    fun refreshTeachingWeekReorganizations() {
        synchronizedOn(courseRefreshLock) {
            if (loadEssentialData()) _dataVersion.value++
        }
    }

    // Rebase the semester start so the selected teaching week remains stable under merge rules.
    fun setCurrentWeek(week: Int) {
        synchronizedOn(courseRefreshLock) {
            FeatureLog.t("课表", "set_week", "week=$week")
            val oldWeek = _currentWeek.value
            val newStartDate = if (week != oldWeek) {
                val semesterStartDate = LocalDate.parse(_classStartTime.value.replace("/", "-"))
                TeachingWeekReorganization.semesterStartDateForTeachingWeekOnDate(
                    semesterStartDate = semesterStartDate,
                    date = todayLocalDate(),
                    teachingWeek = week.toLong(),
                    rules = repository.getTeachingWeekReorganizations(),
                    // ⚠ 必须写具名 return@synchronizedOn：`synchronizedOn` 刻意非 inline（见 :core 的 `Lock.kt`），非局部 return 会被 Kotlin 拒绝；此处 lambda 即函数体、块后无代码，具名返回行为一致。
                ) ?: return@synchronizedOn
            } else null
            _currentWeek.value = week
            repository.setCurrentWeek(week)
            _isHoliday.value = isWeekHoliday(week)

            if (newStartDate != null) {
                val newStartDateStr = newStartDate.formatSlashDate()
                _classStartTime.value = newStartDateStr
                repository.setClassStartTime(newStartDateStr)
                updateWidgets()
            }
            rescheduleReminders()
        }
    }

    fun setClassStartTime(time: String) {
        // 教务导入常给 yyyy-MM-dd；先规范化，避免被 getClassStartTime 重置成今天
        val normalized = CourseRepository.normalizeClassStartDate(time) ?: return
        synchronizedOn(courseRefreshLock) {
            _classStartTime.value = normalized
            repository.setClassStartTime(normalized)

            val newWeek = calculateCurrentWeekFromDate(normalized)
            _currentWeek.value = newWeek
            repository.setCurrentWeek(newWeek)
            _isHoliday.value = isWeekHoliday(newWeek)
            rescheduleReminders()
        }
    }

    fun setTotalWeeks(weeks: Int) {
        if (weeks !in 1..CourseRepository.MAX_TOTAL_WEEKS) return
        synchronizedOn(courseRefreshLock) {
            _totalWeeks.value = weeks
            repository.setTotalWeeks(weeks)
            _isHoliday.value = isWeekHoliday(_currentWeek.value)
        }
    }

    // 周次 = (今天 - 开学周一) / 7 + 1；开学周一为开始上课日期所在周的周一
    private fun calculateCurrentWeekFromDate(startDate: String): Int {
        val start = CourseRepository.parseFlexibleDate(startDate)
        if (start == null) {
            _isSemesterStarted.value = true
            return 1
        }
        val today = todayLocalDate()
        val startMonday = start.minusDays((start.dayOfWeek.isoDayNumber - 1).toLong())
        _isSemesterStarted.value = today >= startMonday
        // 刻意不 clamp 到学期范围：允许 0/负数（未开学）或 >totalWeeks（已结束）
        return TeachingWeekReorganization.mapDate(
            semesterStartDate = start,
            date = today,
            rules = repository.getTeachingWeekReorganizations(),
        ).week.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
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
        mutateCourses { repository.addCourse(course) }
    }

    fun updateCourse(course: Course) {
        mutateCourses { repository.updateCourse(course) }
    }

    fun updateCoursesByName(oldName: String, updated: Course) {
        mutateCourses { repository.updateCoursesByName(oldName, updated) }
    }

    fun deleteCourse(courseId: String) {
        mutateCourses { repository.deleteCourse(courseId) }
    }

    fun deleteCourseForWeek(courseId: String, week: Int) {
        mutateCourses { repository.deleteCourseForWeek(courseId, week) }
    }

    fun moveCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int,
        /** 目标位周次；调休列的目标周（followWeek）与源周不同时为跨周移动，默认同周 */
        targetWeek: Int = week
    ) {
        mutateCourses {
            repository.moveCourseForWeek(
                sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection, targetWeek
            )
        }
    }

    // 调课-覆盖：移动到目标位置并删除该周冲突课程
    fun overwriteCourseForWeek(
        sourceCourseId: String,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetEndSection: Int,
        targetWeek: Int = week
    ) {
        mutateCourses {
            repository.overwriteCourseForWeek(
                sourceCourseId, week, targetDayOfWeek, targetStartSection, targetEndSection, targetWeek
            )
        }
    }

    fun swapCoursesForWeek(
        sourceCourseId: String,
        targetCourseId: String,
        week: Int,
        targetWeek: Int = week
    ) {
        mutateCourses { repository.swapCoursesForWeek(sourceCourseId, targetCourseId, week, targetWeek) }
    }

    fun replaceCourses(courses: List<Course>) {
        synchronizedOn(courseRefreshLock) {
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
            updateWidgets()
        }
    }

    fun appendCourses(courses: List<Course>) {
        synchronizedOn(courseRefreshLock) {
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
                    withSchedule.copy(id = randomUuidV4())
                } else {
                    withSchedule
                }
            }
            val merged = _courses.value + coursesWithSchedule
            repository.saveCourses(merged)
            _courses.value = merged
            _dataVersion.value++
            updateWidgets()
        }
    }

    fun showAddDialog(
        dayOfWeek: Int? = null,
        startSection: Int? = null,
        endSection: Int? = null,
        defaultWeeks: Set<Int> = emptySet(),
        customStartTime: String? = null,
        customEndTime: String? = null,
    ) {
        _editingCourse.value = null
        _selectedDay.value = dayOfWeek ?: 0
        if (startSection != null) {
            _selectedStartSection.value = startSection
            _selectedEndSection.value = endSection ?: startSection
        }
        _addDialogDefaultWeeks.value = defaultWeeks
        _addDialogCustomTime.value =
            if (customStartTime != null && customEndTime != null) {
                customStartTime to customEndTime
            } else {
                null
            }
        _showAddDialog.value = true
    }

    fun showEditDialog(course: Course) {
        _editingCourse.value = course
        _addDialogDefaultWeeks.value = emptySet()
        _addDialogCustomTime.value = null
        _showAddDialog.value = true
    }

    fun hideDialog() {
        _showAddDialog.value = false
        _editingCourse.value = null
        _addDialogDefaultWeeks.value = emptySet()
        _addDialogCustomTime.value = null
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
