package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.haooz.chedule.data.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 课表管理 ViewModel
 * 负责课表的增删改查、切换、创建新学期等操作
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    // 当前课表名称
    private val _currentScheduleName = MutableStateFlow(repository.getCurrentScheduleId())
    val currentScheduleName: StateFlow<String> = _currentScheduleName.asStateFlow()

    // 课表列表
    private val _scheduleNames = MutableStateFlow(repository.getScheduleNames())
    val scheduleNames: StateFlow<List<String>> = _scheduleNames.asStateFlow()

    // 课表摘要（课程数和周数范围）
    private val _scheduleSummaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val scheduleSummaries: StateFlow<Map<String, String>> = _scheduleSummaries.asStateFlow()

    init {
        refreshScheduleList()
    }

    /**
     * 刷新课表列表和摘要
     */
    fun refreshScheduleList() {
        _scheduleNames.value = repository.getScheduleNames()
        val summaries = mutableMapOf<String, String>()
        _scheduleNames.value.forEach { name ->
            summaries[name] = repository.getScheduleSummary(name)
        }
        _scheduleSummaries.value = summaries
    }

    /**
     * 添加新课表
     */
    fun addSchedule(name: String): List<String> {
        val names = repository.addSchedule(name)
        refreshScheduleList()
        return names
    }

    /**
     * 创建新学期课表：复制当前课表设置（不含课程），创建后自动切换
     */
    fun createNewSemesterSchedule(name: String) {
        repository.createNewSemesterSchedule(name)
        repository.switchToSchedule(name)
        _currentScheduleName.value = name
        refreshScheduleList()
    }

    /**
     * 切换到指定课表
     */
    fun switchToSchedule(scheduleId: String) {
        repository.switchToSchedule(scheduleId)
        _currentScheduleName.value = scheduleId
    }

    /**
     * 删除课表
     */
    fun deleteSchedule(name: String): List<String> {
        val names = repository.deleteSchedule(name)
        if (_currentScheduleName.value == name) {
            _currentScheduleName.value = names.first()
        }
        refreshScheduleList()
        return names
    }

    /**
     * 重命名课表
     */
    fun renameSchedule(oldName: String, newName: String): List<String> {
        val names = repository.renameSchedule(oldName, newName)
        if (_currentScheduleName.value == oldName) {
            _currentScheduleName.value = newName
        }
        refreshScheduleList()
        return names
    }

    /**
     * 保存课程到指定课表（不切换当前课表）
     */
    fun saveCoursesToSchedule(scheduleId: String, courses: List<com.haooz.chedule.data.Course>) {
        val oldScheduleId = repository.getCurrentScheduleId()
        repository.setCurrentScheduleId(scheduleId)
        repository.saveCourses(courses)
        repository.setCurrentScheduleId(oldScheduleId)
    }
}
