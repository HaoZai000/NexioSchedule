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
        // 每个课表的摘要都要把该课表的全部课程 JSON 反序列化一遍，课表多时开销可观。
        // 首屏并不展示这些摘要（只在"切换课表"页用到），放到后台线程算，不阻塞首帧。
        viewModelScope.launch(Dispatchers.IO) {
            refreshScheduleList()
        }
    }

    /**
     * 刷新课表列表和摘要
     *
     * 注意：切换课表页（SwitchScheduleScreen）是直接调 repository 改磁盘的，不会走本 ViewModel
     * 的 switchToSchedule()，所以「当前课表」也必须在这里一并从磁盘同步回来，
     * 否则 _currentScheduleName 会一直是旧值（表现为切换后重新打开切换页，当前课表没变）。
     */
    fun refreshScheduleList() {
        _scheduleNames.value = repository.getScheduleNames()
        _currentScheduleName.value = repository.getCurrentScheduleId()
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
        // 切换后重新调度提醒：
        // - 取消旧课表的课前闹钟（孤儿清理）
        // - 注册新课表的课前闹钟
        // - widget 刷新链按新课表重算（否则若旧课表当天无后续课程，
        //   下次刷新会排在 30 分钟后，新课表 30 分钟内的课会漏提醒）
        CourseReminderHelper.startReminderService(getApplication(), repository)
    }

    /**
     * 获取当前课表绑定的时间配置 ID
     */
    fun getCurrentScheduleTimeConfigId(): Long {
        return repository.getScheduleTimeConfigId(repository.getCurrentScheduleId())
    }

    /**
     * 设置指定课表绑定的时间配置 ID
     */
    fun setScheduleTimeConfigId(scheduleId: String, timeConfigId: Long) {
        repository.setScheduleTimeConfigId(scheduleId, timeConfigId)
    }

    /**
     * 创建新的时间配置并返回 ID
     */
    fun addTimeConfig(config: com.haooz.chedule.data.TimeConfig): Long {
        return repository.addTimeConfig(config)
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
        // 删除课表后重新调度提醒，取消已删除课程的闹钟
        CourseReminderHelper.startReminderService(getApplication(), repository)
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

    /**
     * 获取指定课表绑定的时间配置 ID
     */
    fun getScheduleTimeConfigId(scheduleId: String): Long {
        return repository.getScheduleTimeConfigId(scheduleId)
    }

    /**
     * 获取指定 ID 的时间配置
     */
    fun getTimeConfig(configId: Long): com.haooz.chedule.data.TimeConfig {
        return repository.getTimeConfig(configId)
    }
}
