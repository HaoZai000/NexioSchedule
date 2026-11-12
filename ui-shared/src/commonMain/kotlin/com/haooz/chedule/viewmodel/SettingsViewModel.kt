package com.haooz.chedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.holiday.HolidayCourseExclusion
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.SpecialBlock
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.util.todayLocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

internal fun shouldAdvanceForReorganizedWeekend(
    today: LocalDate,
    semesterStartDate: LocalDate?,
    rules: List<TeachingWeekReorganizationRule>,
    hasCoursesToday: () -> Boolean,
): Boolean {
    if (today.dayOfWeek.isoDayNumber !in 6..7) return false
    if (semesterStartDate != null &&
        TeachingWeekReorganization
            .hasFutureTeachingWeekDates(semesterStartDate, today, rules)
    ) return false
    return !hasCoursesToday()
}

/**
 * ⚠ 原是 `AndroidViewModel(application)`，仅为拿 `Context` 调 `CourseReminderHelper.resolveDaySchedule(context, …)`，而那个 `context` 参数函数体根本没用（见 `DayScheduleResolver`）。提醒算法抠进 `:core` 后不再需要 `Application` → 退回普通 [ViewModel]，本文件得以进 `:ui-shared`。
 */
class SettingsViewModel : ViewModel() {

    private val repository = CourseRepository()

    private val _smartWeekend = MutableStateFlow(repository.getSmartWeekend())
    val smartWeekend: StateFlow<Boolean> = _smartWeekend.asStateFlow()

    fun getWeekendDaysForWeek(week: Int): Set<Int> {
        return if (_smartWeekend.value) {
            val rules = repository.getTeachingWeekReorganizations()
            if (rules.isNotEmpty()) {
                val start = runCatching {
                    LocalDate.parse(repository.getClassStartTime().replace('/', '-'))
                }.getOrNull() ?: return emptySet()
                return DayScheduleResolver.effectiveWeekendDays(
                    start, week, rules,
                ) { date ->
                    DayScheduleResolver
                        .resolveDaySchedule(date, repository).courses.isNotEmpty()
                }
            }
            buildSet {
                for (weekday in 6..7) {
                    val date = runCatching {
                        val start = LocalDate.parse(repository.getClassStartTime().replace('/', '-'))
                        TeachingWeekReorganization
                            .dateForPosition(start, week, weekday, emptyList())
                    }.getOrNull()
                    val hasCourses = if (date != null && isBeforeHolidayExclusionActive(date)) {
                        DayScheduleResolver
                            .resolveDaySchedule(date, repository)
                            .courses.isNotEmpty()
                    } else {
                        repository.hasCoursesOnDayInWeek(weekday, week)
                    }
                    if (hasCourses) add(weekday)
                }
            }
        } else {
            setOf(6, 7)
        }
    }

    /**
     * 智能周末：当前日是否应自动跳到下周课表。
     * 当天有课，或已配置调休且映射日/周有课 → 停留本周；
     * 周六还要看周日 —— 周日有课说明本周没走完，同样停留本周；
     * 无课可上（含 API 导入未配置 followWeekday、映射日无课）→ 跳下周。
     */
    fun shouldAdvanceToNextWeek(todayDayOfWeek: Int, week: Int): Boolean {
        if (!_smartWeekend.value) return false
        if (todayDayOfWeek !in 6..7) return false
        val rules = repository.getTeachingWeekReorganizations()
        if (rules.isNotEmpty()) {
            val today = todayLocalDate()
            val start = runCatching {
                LocalDate.parse(repository.getClassStartTime().replace('/', '-'))
            }.getOrNull()
            // Holiday filtering and an explicit work-swap are both defined on the real date.
            return shouldAdvanceForReorganizedWeekend(today, start, rules) {
                DayScheduleResolver
                    .resolveDaySchedule(today, repository).courses.isNotEmpty()
            }
        }
        val today = todayLocalDate()
        // 周六/周日两次判定共用一份假期快照：loadAllByYear 无缓存，每次都是全量
        // SharedPreferences 解析；不共享的话「两天都无课」最坏一次判定跑两遍。
        // lazy 保证当天有直接课时整段不加载（本方法在主线程 remember 链路上）
        val holidayEntriesByYear = lazy { HolidayManager.loadAllByYear() }
        val hasCourses = if (isBeforeHolidayExclusionActive(today)) {
            DayScheduleResolver
                .resolveDaySchedule(today, repository).courses.isNotEmpty()
        } else {
            repository.hasDisplayableCoursesOnDay(todayDayOfWeek, week, holidayEntriesByYear)
        }
        if (hasCourses) return false
        // 周六只是本周第六天：周日排了课就说明本周还没结束。
        // 只判「今天有没有课」的话，「周六空、周日满」会被当成一周已过完
        if (todayDayOfWeek == 6 && repository.hasDisplayableCoursesOnDay(7, week, holidayEntriesByYear)) return false
        return true
    }

    private fun isBeforeHolidayExclusionActive(date: LocalDate): Boolean {
        val exclusion = HolidayManager.loadBeforeCourseExclusion()
        // 短路：Kotlin 实参急切求值，不挡这一下会让未开启该功能的用户每次白跑 loadAllByYear（SharedPreferences 全量解析 + 逐年 Gson 反序列化）。本方法在主线程 remember 的周末判定链路上，节次时间一改就重跑。
        if (!exclusion.enabled || !exclusion.isValid()) return false
        return HolidayCourseExclusion.isEnabledBeforeHolidayDate(
            HolidayManager.loadAllByYear(),
            date,
            exclusion,
        )
    }

    private val _showNonCurrentWeek = MutableStateFlow(repository.getShowNonCurrentWeek())
    val showNonCurrentWeek: StateFlow<Boolean> = _showNonCurrentWeek.asStateFlow()

    private val _morningSections = MutableStateFlow(repository.getMorningSections())
    val morningSections: StateFlow<Int> = _morningSections.asStateFlow()

    private val _afternoonSections = MutableStateFlow(repository.getAfternoonSections())
    val afternoonSections: StateFlow<Int> = _afternoonSections.asStateFlow()

    private val _eveningSections = MutableStateFlow(repository.getEveningSections())
    val eveningSections: StateFlow<Int> = _eveningSections.asStateFlow()

    private val _morningTimes = MutableStateFlow(repository.getPeriodTimes("morning"))
    val morningTimes: StateFlow<Map<Int, String>> = _morningTimes.asStateFlow()

    private val _afternoonTimes = MutableStateFlow(repository.getPeriodTimes("afternoon"))
    val afternoonTimes: StateFlow<Map<Int, String>> = _afternoonTimes.asStateFlow()

    private val _eveningTimes = MutableStateFlow(repository.getPeriodTimes("evening"))
    val eveningTimes: StateFlow<Map<Int, String>> = _eveningTimes.asStateFlow()

    private val _sectionNames = MutableStateFlow(repository.getSectionNames())
    val sectionNames: StateFlow<Map<Int, String>> = _sectionNames.asStateFlow()

    // 无编号特殊块（早读/大课间等），来自当前时间配置
    private val _specialBlocks = MutableStateFlow(repository.getEffectiveTimeConfig().safeSpecialBlocks)
    val specialBlocks: StateFlow<List<SpecialBlock>> = _specialBlocks.asStateFlow()

    private val _preClassReminder = MutableStateFlow(repository.getPreClassReminder())
    val preClassReminder: StateFlow<Boolean> = _preClassReminder.asStateFlow()

    private val _preClassReminderMinutes = MutableStateFlow(repository.getPreClassReminderMinutes())
    val preClassReminderMinutes: StateFlow<Int> = _preClassReminderMinutes.asStateFlow()

    private val _nextDayReminder = MutableStateFlow(repository.getNextDayReminder())
    val nextDayReminder: StateFlow<Boolean> = _nextDayReminder.asStateFlow()

    private val _nextDayReminderHour = MutableStateFlow(repository.getNextDayReminderHour())
    val nextDayReminderHour: StateFlow<Int> = _nextDayReminderHour.asStateFlow()

    private val _nextDayReminderMinute = MutableStateFlow(repository.getNextDayReminderMinute())
    val nextDayReminderMinute: StateFlow<Int> = _nextDayReminderMinute.asStateFlow()

    private val _islandNotification = MutableStateFlow(repository.getIslandNotification())
    val islandNotification: StateFlow<Boolean> = _islandNotification.asStateFlow()

    private val _classDndEnabled = MutableStateFlow(repository.getClassDndEnabled())
    val classDndEnabled: StateFlow<Boolean> = _classDndEnabled.asStateFlow()

    // 0=勿扰，1=静音，2=优先
    private val _classDndMode = MutableStateFlow(repository.getClassDndMode())
    val classDndMode: StateFlow<Int> = _classDndMode.asStateFlow()

    private val _defaultHomepage = MutableStateFlow(repository.getDefaultHomepage())
    val defaultHomepage: StateFlow<String> = _defaultHomepage.asStateFlow()

    private val settingsChangedListener: (String, String) -> Unit = { action, _ ->
        if (action == "settings") {
            viewModelScope.launch {
                refreshSettings()
            }
        }
    }

    init {
        // 多播监听：与 CourseViewModel 并存，不互相覆盖
        repository.addCourseChangedListener(settingsChangedListener)
    }

    override fun onCleared() {
        super.onCleared()
        repository.removeCourseChangedListener(settingsChangedListener)
    }

    // 兼容：合并各时段相对节次为全局绝对编号（下午偏移上午节数，晚上偏移上午+下午）
    val sectionTimes: StateFlow<Map<Int, String>> = run {
        val combined = combine(_morningTimes, _afternoonTimes, _eveningTimes, _morningSections, _afternoonSections) { m, a, e, ms, as_ ->
            buildMap {
                m.forEach { (k, v) -> put(k, v) }
                a.forEach { (k, v) -> put(ms + k, v) }
                e.forEach { (k, v) -> put(ms + as_ + k, v) }
            }
        }
        MutableStateFlow(Course.defaultSectionTimes).also { flow ->
            viewModelScope.launch { combined.collect { flow.value = it } }
        }
    }

    // 仅在值变化时写 Flow，避免无谓重组
    fun refreshSettings() {
        val newSmartWeekend = repository.getSmartWeekend()
        if (_smartWeekend.value != newSmartWeekend) _smartWeekend.value = newSmartWeekend

        val newShowNonCurrentWeek = repository.getShowNonCurrentWeek()
        if (_showNonCurrentWeek.value != newShowNonCurrentWeek) _showNonCurrentWeek.value = newShowNonCurrentWeek

        val newMorningSections = repository.getMorningSections()
        if (_morningSections.value != newMorningSections) _morningSections.value = newMorningSections

        val newAfternoonSections = repository.getAfternoonSections()
        if (_afternoonSections.value != newAfternoonSections) _afternoonSections.value = newAfternoonSections

        val newEveningSections = repository.getEveningSections()
        if (_eveningSections.value != newEveningSections) _eveningSections.value = newEveningSections

        val newMorningTimes = repository.getPeriodTimes("morning")
        if (_morningTimes.value != newMorningTimes) _morningTimes.value = newMorningTimes

        val newAfternoonTimes = repository.getPeriodTimes("afternoon")
        if (_afternoonTimes.value != newAfternoonTimes) _afternoonTimes.value = newAfternoonTimes

        val newEveningTimes = repository.getPeriodTimes("evening")
        if (_eveningTimes.value != newEveningTimes) _eveningTimes.value = newEveningTimes

        val newSectionNames = repository.getSectionNames()
        if (_sectionNames.value != newSectionNames) _sectionNames.value = newSectionNames

        // 必须走 getEffectiveTimeConfig()（与 _specialBlocks 初值一致）：顶层字段只是「当前生效作息」的镜像，跨日期自动切换和新增作息都不会刷新它，直读会出现「节次时间用当天那套、特殊时段块用上一套」的错位。
        val newSpecialBlocks = repository.getEffectiveTimeConfig().safeSpecialBlocks
        if (_specialBlocks.value != newSpecialBlocks) _specialBlocks.value = newSpecialBlocks

        val newPreClassReminder = repository.getPreClassReminder()
        if (_preClassReminder.value != newPreClassReminder) _preClassReminder.value = newPreClassReminder

        val newPreClassReminderMinutes = repository.getPreClassReminderMinutes()
        if (_preClassReminderMinutes.value != newPreClassReminderMinutes) _preClassReminderMinutes.value = newPreClassReminderMinutes

        val newNextDayReminder = repository.getNextDayReminder()
        if (_nextDayReminder.value != newNextDayReminder) _nextDayReminder.value = newNextDayReminder

        val newNextDayReminderHour = repository.getNextDayReminderHour()
        if (_nextDayReminderHour.value != newNextDayReminderHour) _nextDayReminderHour.value = newNextDayReminderHour

        val newNextDayReminderMinute = repository.getNextDayReminderMinute()
        if (_nextDayReminderMinute.value != newNextDayReminderMinute) _nextDayReminderMinute.value = newNextDayReminderMinute

        val newIslandNotification = repository.getIslandNotification()
        if (_islandNotification.value != newIslandNotification) _islandNotification.value = newIslandNotification

        val newClassDndEnabled = repository.getClassDndEnabled()
        if (_classDndEnabled.value != newClassDndEnabled) _classDndEnabled.value = newClassDndEnabled

        val newClassDndMode = repository.getClassDndMode()
        if (_classDndMode.value != newClassDndMode) _classDndMode.value = newClassDndMode

        val newDefaultHomepage = repository.getDefaultHomepage()
        if (_defaultHomepage.value != newDefaultHomepage) _defaultHomepage.value = newDefaultHomepage
    }

    fun setSmartWeekend(smart: Boolean) {
        _smartWeekend.value = smart
        repository.setSmartWeekend(smart)
    }

    fun setShowNonCurrentWeek(show: Boolean) {
        _showNonCurrentWeek.value = show
        repository.setShowNonCurrentWeek(show)
    }

    fun setDefaultHomepage(homepage: String) {
        _defaultHomepage.value = homepage
        repository.setDefaultHomepage(homepage)
    }

    fun setMorningSections(count: Int) {
        _morningSections.value = count
        repository.setMorningSections(count)
    }

    fun setAfternoonSections(count: Int) {
        _afternoonSections.value = count
        repository.setAfternoonSections(count)
    }

    fun setEveningSections(count: Int) {
        _eveningSections.value = count
        repository.setEveningSections(count)
    }

    fun getMorningTimes(): Map<Int, String> = _morningTimes.value
    fun getAfternoonTimes(): Map<Int, String> = _afternoonTimes.value
    fun getEveningTimes(): Map<Int, String> = _eveningTimes.value

    fun updateSpecialBlocks(blocks: List<SpecialBlock>) {
        // 用「可写」入口：未绑定的课表先补绑，否则特殊时段块会写进别人的配置
        val base = repository.getWritableCurrentTimeConfig()
        val config = base.effective()
        val routineId = base.effectiveRoutineId()
        val updated = config.copy(specialBlocks = blocks)
        repository.saveTimeConfig(
            if (routineId == null) updated
            else base.withRoutineTimesApplied(routineId, updated)
        )
        _specialBlocks.value = blocks
    }

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

    // 教务/AI 软导入：同步更新当前课表绑定的时间配置，避免之后被旧配置盖回
    fun applyTimeImportToCurrentSchedule(
        morningSections: Int, afternoonSections: Int, eveningSections: Int,
        morningTimes: Map<Int, String>, afternoonTimes: Map<Int, String>, eveningTimes: Map<Int, String>
    ) {
        repository.applyTimeImportToCurrentSchedule(
            morningSections, afternoonSections, eveningSections,
            morningTimes, afternoonTimes, eveningTimes
        )
    }

    fun resetSectionTimes() {
        val defaults = Course
        _morningTimes.value = defaults.defaultMorningTimes
        _afternoonTimes.value = defaults.defaultAfternoonTimes
        _eveningTimes.value = defaults.defaultEveningTimes
        repository.savePeriodTimes("morning", defaults.defaultMorningTimes)
        repository.savePeriodTimes("afternoon", defaults.defaultAfternoonTimes)
        repository.savePeriodTimes("evening", defaults.defaultEveningTimes)
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

    fun setIslandNotification(enabled: Boolean) {
        _islandNotification.value = enabled
        repository.setIslandNotification(enabled)
    }

    fun setClassDndEnabled(enabled: Boolean) {
        _classDndEnabled.value = enabled
        repository.setClassDndEnabled(enabled)
    }

    fun setClassDndMode(mode: Int) {
        _classDndMode.value = mode
        repository.setClassDndMode(mode)
    }
}
