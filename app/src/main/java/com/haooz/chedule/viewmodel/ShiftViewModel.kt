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

/**
 * 排班模式 ViewModel
 * 负责排班模式的启用/禁用、多课表选择、排班数据加载
 */
class ShiftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    // 排班模式状态
    private val _isShiftMode = MutableStateFlow(repository.isShiftModeEnabled())
    val isShiftMode: StateFlow<Boolean> = _isShiftMode.asStateFlow()

    // 排班模式选中的课表
    private val _shiftSelectedSchedules = MutableStateFlow(repository.getShiftSelectedSchedules())
    val shiftSelectedSchedules: StateFlow<List<String>> = _shiftSelectedSchedules.asStateFlow()

    // 排班模式各课表课程缓存
    private val _shiftScheduleCourses = MutableStateFlow<Map<String, List<Course>>>(emptyMap())
    val shiftScheduleCourses: StateFlow<Map<String, List<Course>>> = _shiftScheduleCourses.asStateFlow()

    // 排班模式各课表节数缓存
    private val _shiftScheduleSections = MutableStateFlow<Map<String, Triple<Int, Int, Int>>>(emptyMap())
    val shiftScheduleSections: StateFlow<Map<String, Triple<Int, Int, Int>>> = _shiftScheduleSections.asStateFlow()

    init {
        if (_isShiftMode.value) {
            viewModelScope.launch(Dispatchers.IO) { reloadShiftData() }
        }
    }

    /**
     * 进入排班模式
     */
    fun enterShiftMode() {
        _isShiftMode.value = true
        repository.setShiftModeEnabled(true)
        if (_shiftSelectedSchedules.value.isEmpty()) {
            _shiftSelectedSchedules.value = repository.getScheduleNames()
            repository.setShiftSelectedSchedules(_shiftSelectedSchedules.value)
        }
        reloadShiftData()
    }

    /**
     * 退出排班模式
     */
    fun exitShiftMode() {
        _isShiftMode.value = false
        repository.setShiftModeEnabled(false)
    }

    /**
     * 设置排班模式选中的课表
     */
    fun setShiftSelectedSchedules(names: List<String>) {
        _shiftSelectedSchedules.value = names
        repository.setShiftSelectedSchedules(names)
        reloadShiftData()
    }

    /**
     * 重新加载排班数据
     */
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
