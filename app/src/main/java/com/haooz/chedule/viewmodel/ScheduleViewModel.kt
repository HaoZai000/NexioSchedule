package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    private val _currentScheduleName = MutableStateFlow(repository.getCurrentScheduleId())
    val currentScheduleName: StateFlow<String> = _currentScheduleName.asStateFlow()

    private val _scheduleNames = MutableStateFlow(repository.getScheduleNames())
    val scheduleNames: StateFlow<List<String>> = _scheduleNames.asStateFlow()

    private val _scheduleSummaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val scheduleSummaries: StateFlow<Map<String, String>> = _scheduleSummaries.asStateFlow()

    init {
        // 摘要需反序列化全部课程，首屏不用，放 IO 避免阻塞首帧
        viewModelScope.launch(Dispatchers.IO) {
            refreshScheduleList()
        }
    }

    // SwitchScheduleScreen 直接改磁盘不经本 VM，须从磁盘同步当前课表名，否则 UI 显示旧值
    fun refreshScheduleList() {
        _scheduleNames.value = repository.getScheduleNames()
        _currentScheduleName.value = repository.getCurrentScheduleId()
        val summaries = mutableMapOf<String, String>()
        _scheduleNames.value.forEach { name ->
            summaries[name] = repository.getScheduleSummary(name)
        }
        _scheduleSummaries.value = summaries
    }

    fun addSchedule(name: String): List<String> {
        val names = repository.addSchedule(name)
        refreshScheduleList()
        return names
    }

    // 复制当前设置（不含课程）并自动切换
    fun createNewSemesterSchedule(name: String) {
        repository.createNewSemesterSchedule(name)
        repository.switchToSchedule(name)
        _currentScheduleName.value = name
        refreshScheduleList()
    }

    fun switchToSchedule(scheduleId: String) {
        repository.switchToSchedule(scheduleId)
        _currentScheduleName.value = scheduleId
        // 重排闹钟并驱动 widget 刷新链，否则新课表 30 分钟内的课可能漏提醒
        CourseReminderHelper.startReminderService(getApplication(), repository)
    }

    fun getCurrentScheduleTimeConfigId(): Long {
        return repository.getScheduleTimeConfigId(repository.getCurrentScheduleId())
    }

    fun setScheduleTimeConfigId(scheduleId: String, timeConfigId: Long) {
        repository.setScheduleTimeConfigId(scheduleId, timeConfigId)
    }

    fun addTimeConfig(config: com.haooz.chedule.data.TimeConfig): Long {
        return repository.addTimeConfig(config)
    }

    fun deleteSchedule(name: String): List<String> {
        val names = repository.deleteSchedule(name)
        if (_currentScheduleName.value == name) {
            _currentScheduleName.value = names.first()
        }
        refreshScheduleList()
        // 取消已删除课程的闹钟
        CourseReminderHelper.startReminderService(getApplication(), repository)
        return names
    }

    fun renameSchedule(oldName: String, newName: String): List<String> {
        val names = repository.renameSchedule(oldName, newName)
        if (_currentScheduleName.value == oldName) {
            _currentScheduleName.value = newName
        }
        refreshScheduleList()
        return names
    }

    // 临时切到目标课表写入再恢复，对外表现为不切换
    fun saveCoursesToSchedule(scheduleId: String, courses: List<com.haooz.chedule.data.Course>) {
        val oldScheduleId = repository.getCurrentScheduleId()
        repository.setCurrentScheduleId(scheduleId)
        repository.saveCourses(courses)
        repository.setCurrentScheduleId(oldScheduleId)
    }

    fun getScheduleTimeConfigId(scheduleId: String): Long {
        return repository.getScheduleTimeConfigId(scheduleId)
    }

    fun getTimeConfig(configId: Long): com.haooz.chedule.data.TimeConfig {
        return repository.getTimeConfig(configId)
    }
}
