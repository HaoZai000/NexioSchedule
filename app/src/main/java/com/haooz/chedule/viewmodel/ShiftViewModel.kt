package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShiftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    private val _isShiftMode = MutableStateFlow(repository.isShiftModeEnabled())
    val isShiftMode: StateFlow<Boolean> = _isShiftMode.asStateFlow()

    private val _shiftSelectedSchedules = MutableStateFlow(repository.getShiftSelectedSchedules())
    val shiftSelectedSchedules: StateFlow<List<String>> = _shiftSelectedSchedules.asStateFlow()

    private val _shiftScheduleCourses = MutableStateFlow<Map<String, List<Course>>>(emptyMap())
    val shiftScheduleCourses: StateFlow<Map<String, List<Course>>> = _shiftScheduleCourses.asStateFlow()

    private val _shiftScheduleSections = MutableStateFlow<Map<String, Triple<Int, Int, Int>>>(emptyMap())
    val shiftScheduleSections: StateFlow<Map<String, Triple<Int, Int, Int>>> = _shiftScheduleSections.asStateFlow()

    init {
        if (_isShiftMode.value) {
            viewModelScope.launch(Dispatchers.IO) { reloadShiftData() }
        }
    }

    fun enterShiftMode() {
        com.haooz.chedule.ui.utils.FeatureLog.shift("enter_mode")
        _isShiftMode.value = true
        repository.setShiftModeEnabled(true)
        if (_shiftSelectedSchedules.value.isEmpty()) {
            // 默认只对比当前课表，避免把所有课表的内容都塞进排班视图；
            // 需要对比其他课表时再到「设置 → 选择对比课表」中勾选
            val current = repository.getCurrentScheduleId()
            _shiftSelectedSchedules.value = listOf(current)
            repository.setShiftSelectedSchedules(_shiftSelectedSchedules.value)
        }
        reloadShiftData()
    }

    fun exitShiftMode() {
        com.haooz.chedule.ui.utils.FeatureLog.shift("exit_mode")
        _isShiftMode.value = false
        repository.setShiftModeEnabled(false)
    }

    fun setShiftSelectedSchedules(names: List<String>) {
        _shiftSelectedSchedules.value = names
        repository.setShiftSelectedSchedules(names)
        reloadShiftData()
    }

    private fun reloadShiftData() {
        val coursesMap = mutableMapOf<String, List<Course>>()
        val sectionsMap = mutableMapOf<String, Triple<Int, Int, Int>>()
        val validNames = mutableListOf<String>()

        for (name in _shiftSelectedSchedules.value) {
            if (name !in repository.getScheduleNames()) continue
            validNames.add(name)
            coursesMap[name] = repository.getCoursesForSchedule(name)
            sectionsMap[name] = repository.getSectionsForSchedule(name)
        }

        // 清理已删除的课表
        if (validNames.size != _shiftSelectedSchedules.value.size) {
            _shiftSelectedSchedules.value = validNames
            repository.setShiftSelectedSchedules(validNames)
        }

        _shiftScheduleCourses.value = coursesMap
        _shiftScheduleSections.value = sectionsMap
    }
}
