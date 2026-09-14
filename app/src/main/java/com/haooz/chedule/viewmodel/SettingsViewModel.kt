package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    private val _smartWeekend = MutableStateFlow(repository.getSmartWeekend())
    val smartWeekend: StateFlow<Boolean> = _smartWeekend.asStateFlow()

    fun getWeekendDaysForWeek(week: Int): Set<Int> {
        return if (_smartWeekend.value) {
            buildSet {
                if (repository.hasCoursesOnDayInWeek(6, week)) add(6)
                if (repository.hasCoursesOnDayInWeek(7, week)) add(7)
            }
        } else {
            setOf(6, 7)
        }
    }

    private val _showNonCurrentWeek = MutableStateFlow(repository.getShowNonCurrentWeek())
    val showNonCurrentWeek: StateFlow<Boolean> = _showNonCurrentWeek.asStateFlow()

    private val _todayShowWallpaper = MutableStateFlow(repository.getTodayShowWallpaper())
    val todayShowWallpaper: StateFlow<Boolean> = _todayShowWallpaper.asStateFlow()

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
    private val _specialBlocks = MutableStateFlow(repository.getCurrentTimeConfig().specialBlocks)
    val specialBlocks: StateFlow<List<com.haooz.chedule.data.SpecialBlock>> = _specialBlocks.asStateFlow()

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

    // 0=勿扰，1=静音
    private val _classDndMode = MutableStateFlow(repository.getClassDndMode())
    val classDndMode: StateFlow<Int> = _classDndMode.asStateFlow()

    private val _defaultHomepage = MutableStateFlow(repository.getDefaultHomepage())
    val defaultHomepage: StateFlow<String> = _defaultHomepage.asStateFlow()

    init {
        repository.onCourseChanged = { action, _ ->
            if (action == "settings") {
                viewModelScope.launch {
                    refreshSettings()
                }
            }
        }
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

        val newSpecialBlocks = repository.getCurrentTimeConfig().specialBlocks
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

        val newTodayShowWallpaper = repository.getTodayShowWallpaper()
        if (_todayShowWallpaper.value != newTodayShowWallpaper) _todayShowWallpaper.value = newTodayShowWallpaper

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

    fun setTodayShowWallpaper(show: Boolean) {
        _todayShowWallpaper.value = show
        repository.setTodayShowWallpaper(show)
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

    fun updateSpecialBlocks(blocks: List<com.haooz.chedule.data.SpecialBlock>) {
        val config = repository.getCurrentTimeConfig()
        repository.saveTimeConfig(config.copy(specialBlocks = blocks))
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
        val defaults = com.haooz.chedule.data.Course
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
