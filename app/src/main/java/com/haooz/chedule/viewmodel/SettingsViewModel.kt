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

/**
 * 设置管理 ViewModel
 * 负责课程时间、节数、提醒、主题等设置
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    // 显示周末设置
    private val _showWeekendDays = MutableStateFlow(repository.getShowWeekendDays())
    val showWeekendDays: StateFlow<Set<Int>> = _showWeekendDays.asStateFlow()

    // 显示非本周课程
    private val _showNonCurrentWeek = MutableStateFlow(repository.getShowNonCurrentWeek())
    val showNonCurrentWeek: StateFlow<Boolean> = _showNonCurrentWeek.asStateFlow()

    // 上午节数
    private val _morningSections = MutableStateFlow(repository.getMorningSections())
    val morningSections: StateFlow<Int> = _morningSections.asStateFlow()

    // 下午节数
    private val _afternoonSections = MutableStateFlow(repository.getAfternoonSections())
    val afternoonSections: StateFlow<Int> = _afternoonSections.asStateFlow()

    // 晚上节数
    private val _eveningSections = MutableStateFlow(repository.getEveningSections())
    val eveningSections: StateFlow<Int> = _eveningSections.asStateFlow()

    // 各时段节次时间映射
    private val _morningTimes = MutableStateFlow(repository.getPeriodTimes("morning"))
    val morningTimes: StateFlow<Map<Int, String>> = _morningTimes.asStateFlow()

    private val _afternoonTimes = MutableStateFlow(repository.getPeriodTimes("afternoon"))
    val afternoonTimes: StateFlow<Map<Int, String>> = _afternoonTimes.asStateFlow()

    private val _eveningTimes = MutableStateFlow(repository.getPeriodTimes("evening"))
    val eveningTimes: StateFlow<Map<Int, String>> = _eveningTimes.asStateFlow()

    // 课前提醒开关
    private val _preClassReminder = MutableStateFlow(repository.getPreClassReminder())
    val preClassReminder: StateFlow<Boolean> = _preClassReminder.asStateFlow()

    // 课前提醒提前分钟数
    private val _preClassReminderMinutes = MutableStateFlow(repository.getPreClassReminderMinutes())
    val preClassReminderMinutes: StateFlow<Int> = _preClassReminderMinutes.asStateFlow()

    // 次日课程提醒开关
    private val _nextDayReminder = MutableStateFlow(repository.getNextDayReminder())
    val nextDayReminder: StateFlow<Boolean> = _nextDayReminder.asStateFlow()

    // 次日课程提醒时间
    private val _nextDayReminderHour = MutableStateFlow(repository.getNextDayReminderHour())
    val nextDayReminderHour: StateFlow<Int> = _nextDayReminderHour.asStateFlow()

    private val _nextDayReminderMinute = MutableStateFlow(repository.getNextDayReminderMinute())
    val nextDayReminderMinute: StateFlow<Int> = _nextDayReminderMinute.asStateFlow()

    // 超级岛通知开关
    private val _islandNotification = MutableStateFlow(repository.getIslandNotification())
    val islandNotification: StateFlow<Boolean> = _islandNotification.asStateFlow()

    // 默认首页
    private val _defaultHomepage = MutableStateFlow(repository.getDefaultHomepage())
    val defaultHomepage: StateFlow<String> = _defaultHomepage.asStateFlow()

    // 兼容：将各时段的相对节次时间合并为全局绝对编号的扁平映射
    // 上午节次保持原编号，下午节次偏移上午节数，晚上节次偏移上午+下午节数
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

    // --- 显示设置 ---

    fun setShowWeekendDays(days: Set<Int>) {
        _showWeekendDays.value = days
        repository.setShowWeekendDays(days)
    }

    fun setShowNonCurrentWeek(show: Boolean) {
        _showNonCurrentWeek.value = show
        repository.setShowNonCurrentWeek(show)
    }

    fun setDefaultHomepage(homepage: String) {
        _defaultHomepage.value = homepage
        repository.setDefaultHomepage(homepage)
    }

    // --- 节数设置 ---

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

    // --- 时间设置 ---

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
        val defaults = com.haooz.chedule.data.Course
        _morningTimes.value = defaults.defaultMorningTimes
        _afternoonTimes.value = defaults.defaultAfternoonTimes
        _eveningTimes.value = defaults.defaultEveningTimes
        repository.savePeriodTimes("morning", defaults.defaultMorningTimes)
        repository.savePeriodTimes("afternoon", defaults.defaultAfternoonTimes)
        repository.savePeriodTimes("evening", defaults.defaultEveningTimes)
    }

    // --- 提醒设置 ---

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
}
