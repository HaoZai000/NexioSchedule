# KeCB 代码整理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 KeCB 课程表 Android 项目进行全面代码整理，包括包结构重组、ViewModel 拆分、死代码清理、命名规范统一和必要中文注释添加。

**Architecture:** 将散落在根包的 Activity 文件迁移到 `ui/activities/` 子包，将单一的 `CourseViewModel`（692行）拆分为职责明确的多个 ViewModel，统一命名风格，清理未使用代码。

**Tech Stack:** Kotlin, Jetpack Compose, Android, SharedPreferences, Gson

## Global Constraints

- 最低支持 Android 13 (API 33)
- 使用 Jetpack Compose + Material3 UI 框架
- 保持所有现有功能不变，仅做结构调整
- 中文注释仅添加在必要位置（非显而易见的逻辑、复杂算法、重要常量）

---

## 文件结构变更概览

### 当前结构（需重组）
```
com/haooz/chedule/
├── MainActivity.kt              → 移动到 ui/activities/
├── AboutActivity.kt             → 移动到 ui/activities/
├── AppreciateAuthorActivity.kt  → 移动到 ui/activities/
├── CourseReminderActivity.kt    → 移动到 ui/activities/
├── CourseTimeSettingsActivity.kt→ 移动到 ui/activities/
├── EducationalImportActivity.kt → 移动到 ui/activities/
├── PreferenceSettingsActivity.kt→ 移动到 ui/activities/
├── SwitchScheduleActivity.kt    → 移动到 ui/activities/
├── ThemeUtils.kt                → 移动到 ui/theme/
├── UpdateSettingsActivity.kt    → 移动到 ui/activities/
├── WebDavSettingsActivity.kt    → 移动到 ui/activities/
├── WidgetIntroActivity.kt       → 移动到 ui/activities/
├── viewmodel/
│   └── CourseViewModel.kt       → 拆分为多个 ViewModel
└── ...
```

### 目标结构
```
com/haooz/chedule/
├── data/                        // 数据层（不变）
│   ├── Course.kt
│   ├── CourseRepository.kt
│   ├── SyncManager.kt
│   ├── WebDavManager.kt
│   └── school/
├── ui/
│   ├── activities/              // 新增：所有 Activity
│   │   ├── MainActivity.kt
│   │   ├── AboutActivity.kt
│   │   ├── AppreciateAuthorActivity.kt
│   │   ├── CourseReminderActivity.kt
│   │   ├── CourseTimeSettingsActivity.kt
│   │   ├── EducationalImportActivity.kt
│   │   ├── PreferenceSettingsActivity.kt
│   │   ├── SwitchScheduleActivity.kt
│   │   ├── UpdateSettingsActivity.kt
│   │   ├── WebDavSettingsActivity.kt
│   │   └── WidgetIntroActivity.kt
│   ├── components/              // 通用 UI 组件（不变）
│   ├── screens/                 // 页面（不变）
│   ├── theme/                   // 主题（移入 ThemeUtils.kt）
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   ├── ThemeUtils.kt       // 从根包移入
│   │   └── Type.kt
│   └── web/                     // WebView 兼容（不变）
├── viewmodel/                   // ViewModel（拆分后）
│   ├── CourseViewModel.kt       // 精简为核心课程管理
│   ├── ScheduleViewModel.kt     // 新增：课表管理
│   ├── SettingsViewModel.kt     // 新增：设置管理
│   └── ShiftViewModel.kt        // 新增：排班模式
├── reminder/                    // 提醒（不变）
├── shizuku/                     // Shizuku 服务（不变）
├── effect/                      // 背景特效（不变）
└── widget/                      // 小组件（不变）
```

---

## Task 1: 创建 Activities 子包并迁移文件

**Covers:** 包结构重组

**Files:**
- Create: `app/src/main/java/com/haooz/chedule/ui/activities/` 目录
- Modify: 以下 11 个文件的 `package` 声明
  - `MainActivity.kt`
  - `AboutActivity.kt`
  - `AppreciateAuthorActivity.kt`
  - `CourseReminderActivity.kt`
  - `CourseTimeSettingsActivity.kt`
  - `EducationalImportActivity.kt`
  - `PreferenceSettingsActivity.kt`
  - `SwitchScheduleActivity.kt`
  - `UpdateSettingsActivity.kt`
  - `WebDavSettingsActivity.kt`
  - `WidgetIntroActivity.kt`

- [ ] **Step 1: 创建 activities 目录**

```bash
mkdir -p app/src/main/java/com/haooz/chedule/ui/activities
```

- [ ] **Step 2: 移动所有 Activity 文件**

将以下文件从 `app/src/main/java/com/haooz/chedule/` 移动到 `app/src/main/java/com/haooz/chedule/ui/activities/`：
- MainActivity.kt
- AboutActivity.kt
- AppreciateAuthorActivity.kt
- CourseReminderActivity.kt
- CourseTimeSettingsActivity.kt
- EducationalImportActivity.kt
- PreferenceSettingsActivity.kt
- SwitchScheduleActivity.kt
- UpdateSettingsActivity.kt
- WebDavSettingsActivity.kt
- WidgetIntroActivity.kt

- [ ] **Step 3: 更新所有 Activity 的 package 声明**

将每个文件的第一行从：
```kotlin
package com.haooz.chedule
```
改为：
```kotlin
package com.haooz.chedule.ui.activities
```

- [ ] **Step 4: 更新所有 Activity 引用**

搜索并更新项目中所有引用这些 Activity 的地方，添加新的 import 语句。需要更新的文件包括：
- AndroidManifest.xml（Activity 注册）
- 任何使用 `Intent(this, XxxActivity::class.java)` 的地方

- [ ] **Step 5: 验证编译**

```bash
cd app && ./gradlew compileDebugKotlin
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: 将所有 Activity 迁移到 ui/activities 子包"
```

---

## Task 2: 迁移 ThemeUtils 到 ui/theme 子包

**Covers:** 包结构重组

**Files:**
- Move: `app/src/main/java/com/haooz/chedule/ThemeUtils.kt` → `app/src/main/java/com/haooz/chedule/ui/theme/ThemeUtils.kt`

- [ ] **Step 1: 移动 ThemeUtils.kt 到 ui/theme/**

```bash
mv app/src/main/java/com/haooz/chedule/ThemeUtils.kt \
   app/src/main/java/com/haooz/chedule/ui/theme/ThemeUtils.kt
```

- [ ] **Step 2: 更新 package 声明**

将第一行从：
```kotlin
package com.haooz.chedule
```
改为：
```kotlin
package com.haooz.chedule.ui.theme
```

- [ ] **Step 3: 更新所有引用 ThemeUtils 的地方**

搜索 `isAppDarkTheme` 和 `applyThemeAwareSystemBars` 的使用位置，更新 import 语句。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: 将 ThemeUtils 迁移到 ui/theme 子包"
```

---

## Task 3: 拆分 CourseViewModel - 创建 ScheduleViewModel

**Covers:** ViewModel 拆分

**Files:**
- Create: `app/src/main/java/com/haooz/chedule/viewmodel/ScheduleViewModel.kt`
- Modify: `app/src/main/java/com/haooz/chedule/viewmodel/CourseViewModel.kt`

从 CourseViewModel 中提取课表管理相关逻辑到 ScheduleViewModel。

- [ ] **Step 1: 创建 ScheduleViewModel.kt**

```kotlin
package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.haooz.chedule.data.CourseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
        if (_currentScheduleName.value == name && names.isNotEmpty()) {
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
```

- [ ] **Step 2: 从 CourseViewModel 中移除已迁移的方法**

从 `CourseViewModel.kt` 中移除以下方法和属性：
- `_currentScheduleName` / `currentScheduleName`
- `_scheduleNames` / `scheduleNames`
- `_scheduleSummaries` / `scheduleSummaries`
- `refreshScheduleList()`
- `addSchedule()`
- `createNewSemesterSchedule()`
- `switchToSchedule()`
- `saveCoursesToSchedule()`

- [ ] **Step 3: 更新 CourseViewModel 的 init 块**

移除 `refreshScheduleList()` 调用（已迁移到 ScheduleViewModel）。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: 提取 ScheduleViewModel 管理课表操作"
```

---

## Task 4: 拆分 CourseViewModel - 创建 SettingsViewModel

**Covers:** ViewModel 拆分

**Files:**
- Create: `app/src/main/java/com/haooz/chedule/viewmodel/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/haooz/chedule/viewmodel/CourseViewModel.kt`

从 CourseViewModel 中提取设置管理相关逻辑到 SettingsViewModel。

- [ ] **Step 1: 创建 SettingsViewModel.kt**

```kotlin
package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.haooz.chedule.data.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
```

- [ ] **Step 2: 从 CourseViewModel 中移除已迁移的方法**

从 `CourseViewModel.kt` 中移除以下方法和属性：
- 所有 `_showWeekendDays` / `showWeekendDays` 相关
- 所有 `_showNonCurrentWeek` / `showNonCurrentWeek` 相关
- 所有 `_morningSections` / `afternoonSections` / `eveningSections` 相关
- 所有 `_morningTimes` / `afternoonTimes` / `eveningTimes` 相关
- 所有提醒相关（`_preClassReminder` 等）
- 所有 setter 方法（`setShowWeekendDays`, `setMorningSections` 等）
- `resetSectionTimes()` 方法
- `_islandNotification` / `islandNotification`
- `_defaultHomepage` / `defaultHomepage`

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: 提取 SettingsViewModel 管理设置操作"
```

---

## Task 5: 拆分 CourseViewModel - 创建 ShiftViewModel

**Covers:** ViewModel 拆分

**Files:**
- Create: `app/src/main/java/com/haooz/chedule/viewmodel/ShiftViewModel.kt`
- Modify: `app/src/main/java/com/haooz/chedule/viewmodel/CourseViewModel.kt`

从 CourseViewModel 中提取排班模式相关逻辑到 ShiftViewModel。

- [ ] **Step 1: 创建 ShiftViewModel.kt**

```kotlin
package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
```

- [ ] **Step 2: 从 CourseViewModel 中移除已迁移的方法**

从 `CourseViewModel.kt` 中移除以下方法和属性：
- `_isShiftMode` / `isShiftMode`
- `_shiftSelectedSchedules` / `shiftSelectedSchedules`
- `_shiftScheduleCourses` / `shiftScheduleCourses`
- `_shiftScheduleSections` / `shiftScheduleSections`
- `enterShiftMode()`
- `exitShiftMode()`
- `setShiftSelectedSchedules()`
- `reloadShiftData()`

- [ ] **Step 3: 更新 CourseViewModel 的 init 块**

移除排班模式相关的初始化代码。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: 提取 ShiftViewModel 管理排班模式"
```

---

## Task 6: 精简 CourseViewModel

**Covers:** ViewModel 拆分

**Files:**
- Modify: `app/src/main/java/com/haooz/chedule/viewmodel/CourseViewModel.kt`

经过前三个任务的拆分，CourseViewModel 应该只剩核心课程管理逻辑。

- [ ] **Step 1: 验证 CourseViewModel 内容**

确保 CourseViewModel 只保留以下功能：
- 课程数据加载和管理（`_courses`, `loadCourses`, `addCourse`, `updateCourse`, `deleteCourse`）
- 当前周次管理（`_currentWeek`, `setCurrentWeek`, `totalWeeks`）
- 课程查询（`getCoursesForDay`, `hasTimeConflict`, `getOccupiedWeeks`）
- 课程添加/编辑对话框状态（`_showAddDialog`, `_editingCourse`）
- 数据版本号（`_dataVersion`）

- [ ] **Step 2: 移除不再需要的 import**

清理 CourseViewModel 中不再使用的 import 语句。

- [ ] **Step 3: 添加必要的中文注释**

```kotlin
package com.haooz.chedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.widget.CourseWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 课程管理 ViewModel
 * 负责课程数据的加载、增删改查、周次计算等核心功能
 */
class CourseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    // 所有课程列表
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    // 数据版本号，每次重新加载数据时递增，用于强制 UI 重组
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    // 当前周次
    private val _currentWeek = MutableStateFlow(1)
    val currentWeek: StateFlow<Int> = _currentWeek.asStateFlow()

    // 总周数
    private val _totalWeeks = MutableStateFlow(20)
    val totalWeeks: StateFlow<Int> = _totalWeeks.asStateFlow()

    // 开始上课日期（格式：yyyy/MM/dd）
    private val _classStartTime = MutableStateFlow("2025-09-01")
    val classStartTime: StateFlow<String> = _classStartTime.asStateFlow()

    // 当前选中的星期 (1-7)
    private val _selectedDay = MutableStateFlow(1)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    // 当前选中的开始节次
    private val _selectedStartSection = MutableStateFlow(1)
    val selectedStartSection: StateFlow<Int> = _selectedStartSection.asStateFlow()

    // 当前选中的结束节次
    private val _selectedEndSection = MutableStateFlow(2)
    val selectedEndSection: StateFlow<Int> = _selectedEndSection.asStateFlow()

    // 是否显示添加/编辑对话框
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    // 是否显示跳转周数弹窗
    private val _showJumpWeekDialog = MutableStateFlow(false)
    val showJumpWeekDialog: StateFlow<Boolean> = _showJumpWeekDialog.asStateFlow()

    // 正在编辑的课程
    private val _editingCourse = MutableStateFlow<Course?>(null)
    val editingCourse: StateFlow<Course?> = _editingCourse.asStateFlow()

    init {
        loadEssentialData()
        viewModelScope.launch(Dispatchers.IO) {
            loadCourses()
        }
        rescheduleReminders()
    }

    private fun rescheduleReminders() {
        val context = getApplication<Application>()
        CourseReminderHelper.startReminderService(context, repository)
    }

    /**
     * 加载基本数据（周次、日期等），在主线程同步完成确保立即就绪
     */
    private fun loadEssentialData() {
        _totalWeeks.value = repository.getTotalWeeks()
        _classStartTime.value = repository.getClassStartTime()
        val calculatedWeek = calculateCurrentWeekFromDate(_classStartTime.value)
        _currentWeek.value = calculatedWeek
        repository.setCurrentWeek(calculatedWeek)
        _dataVersion.value++
    }

    /**
     * 加载课程列表（IO 线程）
     */
    private fun loadCourses() {
        _courses.value = repository.getAllCourses()
        viewModelScope.launch { CourseWidgetProvider.updateAllWidgets(getApplication()) }
    }

    /**
     * 重新加载所有数据（切换课表后调用）
     */
    fun reloadCourses() {
        viewModelScope.launch(Dispatchers.IO) {
            loadEssentialData()
            loadCourses()
        }
    }

    /**
     * 设置当前周次（今天是第几周）
     * 保留原日期的星期几，通过周次差值调整：新日期 = 原日期 + (旧周次 - 新周次) * 7
     */
    fun setCurrentWeek(week: Int) {
        val oldWeek = _currentWeek.value
        _currentWeek.value = week
        repository.setCurrentWeek(week)

        if (week != oldWeek) {
            val oldStartDate = LocalDate.parse(_classStartTime.value.replace("/", "-"))
            val newStartDate = oldStartDate.plusDays((oldWeek - week).toLong() * 7)
            val newStartDateStr = newStartDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            _classStartTime.value = newStartDateStr
            repository.setClassStartTime(newStartDateStr)
        }
    }

    /**
     * 设置开始上课日期（同时更新当前周次）
     */
    fun setClassStartTime(time: String) {
        _classStartTime.value = time
        repository.setClassStartTime(time)

        val newWeek = calculateCurrentWeekFromDate(time)
        _currentWeek.value = newWeek
        repository.setCurrentWeek(newWeek)
    }

    /**
     * 设置总周数
     */
    fun setTotalWeeks(weeks: Int) {
        _totalWeeks.value = weeks
        repository.setTotalWeeks(weeks)
    }

    /**
     * 根据上课日期计算当前周次
     * 公式：当前周次 = (今天 - 开学周一) / 7 + 1
     * 开学周一 = 开始上课日期所在周的周一
     */
    private fun calculateCurrentWeekFromDate(startDate: String): Int {
        return try {
            val today = LocalDate.now()
            val start = LocalDate.parse(startDate.replace("/", "-"))
            // 找到开始日期所在周的周一
            val startMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
            val daysBetween = ChronoUnit.DAYS.between(startMonday, today)
            val week = (daysBetween / 7 + 1).toInt()
            week.coerceIn(1, _totalWeeks.value)
        } catch (e: Exception) {
            1
        }
    }

    /**
     * 选择星期
     */
    fun selectDay(day: Int) {
        _selectedDay.value = day
    }

    /**
     * 获取指定周次、星期的课程（包括非本周课程）
     */
    fun getCoursesForDay(week: Int, dayOfWeek: Int, showNonCurrentWeek: Boolean = true): List<Course> {
        val courses = _courses.value.filter {
            it.dayOfWeek == dayOfWeek
        }.sortedBy { it.startSection }

        return if (showNonCurrentWeek) {
            courses
        } else {
            courses.filter { it.isActiveInWeek(week) }
        }
    }

    /**
     * 添加课程
     */
    fun addCourse(course: Course) {
        _courses.value = repository.addCourse(course)
    }

    /**
     * 更新课程
     */
    fun updateCourse(course: Course) {
        _courses.value = repository.updateCourse(course)
    }

    /**
     * 删除课程
     */
    fun deleteCourse(courseId: String) {
        _courses.value = repository.deleteCourse(courseId)
    }

    /**
     * 替换所有课程（导入时使用）
     */
    fun replaceCourses(courses: List<Course>) {
        val currentScheduleId = repository.getCurrentScheduleId()
        // 为导入的课程设置 scheduleId
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

    /**
     * 显示添加对话框
     */
    fun showAddDialog(dayOfWeek: Int? = null, startSection: Int? = null, endSection: Int? = null) {
        _editingCourse.value = null
        if (dayOfWeek != null) {
            _selectedDay.value = dayOfWeek
        }
        if (startSection != null) {
            _selectedStartSection.value = startSection
            _selectedEndSection.value = endSection ?: startSection
        }
        _showAddDialog.value = true
    }

    /**
     * 显示编辑对话框
     */
    fun showEditDialog(course: Course) {
        _editingCourse.value = course
        _showAddDialog.value = true
    }

    /**
     * 隐藏对话框
     */
    fun hideDialog() {
        _showAddDialog.value = false
        _editingCourse.value = null
    }

    /**
     * 显示跳转周数弹窗
     */
    fun showJumpWeekDialog() {
        _showJumpWeekDialog.value = true
    }

    /**
     * 隐藏跳转周数弹窗
     */
    fun hideJumpWeekDialog() {
        _showJumpWeekDialog.value = false
    }

    /**
     * 检查课程时间冲突
     */
    fun hasTimeConflict(
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        startWeek: Int,
        endWeek: Int,
        excludeId: String? = null
    ): Boolean {
        return _courses.value.any { course ->
            if (excludeId != null && course.id == excludeId) return@any false
            course.dayOfWeek == dayOfWeek &&
                    course.startSection <= endSection &&
                    course.endSection >= startSection &&
                    course.startWeek <= endWeek &&
                    course.endWeek >= startWeek
        }
    }

    /**
     * 获取指定星期和节次范围已占用的周次列表
     */
    fun getOccupiedWeeks(
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        excludeId: String? = null
    ): Set<Int> {
        return repository.getOccupiedWeeks(dayOfWeek, startSection, endSection, excludeId)
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: 精简 CourseViewModel 为核心课程管理"
```

---

## Task 7: 更新所有引用 ViewModel 的地方

**Covers:** ViewModel 拆分

**Files:**
- Modify: `app/src/main/java/com/haooz/chedule/ui/activities/MainActivity.kt`
- Modify: `app/src/main/java/com/haooz/chedule/ui/screens/*.kt`
- Modify: `app/src/main/java/com/haooz/chedule/ui/activities/*Activity.kt`

更新所有使用 CourseViewModel 的地方，改为使用新的 ViewModel。

- [ ] **Step 1: 搜索所有使用 CourseViewModel 的文件**

```bash
grep -r "CourseViewModel" app/src/main/java/ --include="*.kt"
```

- [ ] **Step 2: 更新 MainActivity.kt**

在 `MainActivity.kt` 中添加新的 ViewModel 导入和实例化：

```kotlin
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel

// 在 CourseScheduleApp() 中
val viewModel: CourseViewModel = viewModel()
val scheduleViewModel: ScheduleViewModel = viewModel()
val settingsViewModel: SettingsViewModel = viewModel()
val shiftViewModel: ShiftViewModel = viewModel()
```

- [ ] **Step 3: 更新各个 Screen 文件**

根据每个 Screen 实际使用的功能，注入对应的 ViewModel。例如：
- `SettingsScreen` 需要 `SettingsViewModel`
- `ShiftScheduleScreen` 需要 `ShiftViewModel`
- `MainScheduleScreen` 需要 `CourseViewModel`

- [ ] **Step 4: 更新各个 Activity 文件**

检查所有 Activity 中对 ViewModel 的引用，确保使用正确的 ViewModel。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: 更新所有 ViewModel 引用"
```

---

## Task 8: 死代码清理

**Covers:** 死代码清理

**Files:**
- Modify: 多个文件（根据分析结果）

- [ ] **Step 1: 搜索未使用的函数和变量**

使用 IDE 或 grep 搜索以下模式：
- 未使用的 import
- 未使用的 private 函数
- 未使用的变量
- 已注释的代码块

- [ ] **Step 2: 清理未使用的 import**

```bash
# 在每个 .kt 文件中移除未使用的 import
```

- [ ] **Step 3: 清理已注释的代码块**

删除所有被注释掉的代码块（不是注释说明，而是被注释掉的代码）。

- [ ] **Step 4: 清理未使用的常量和变量**

检查 `CourseRepository.kt` 中定义的 KEY_* 常量是否都被使用。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: 清理未使用的代码和 import"
```

---

## Task 9: 统一命名规范

**Covers:** 包/文件命名规范

**Files:**
- Modify: 多个文件

- [ ] **Step 1: 检查命名风格一致性**

确保以下命名规范：
- 类名：PascalCase（如 `CourseRepository`）
- 函数名：camelCase（如 `loadCourses`）
- 常量：UPPER_SNAKE_CASE（如 `KEY_COURSES`）
- 包名：全小写（如 `com.haooz.chedule.data`）

- [ ] **Step 2: 修复命名不一致的地方**

搜索并修复任何不符合规范的命名。

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "style: 统一命名规范"
```

---

## Task 10: 添加必要中文注释

**Covers:** 添加必要中文注释

**Files:**
- Modify: 多个核心文件

- [ ] **Step 1: 为复杂算法添加注释**

在以下位置添加中文注释：
- 周次计算逻辑
- 时间冲突检测
- 数据迁移逻辑
- 课表同步逻辑

- [ ] **Step 2: 为重要常量添加注释**

```kotlin
// 周类型常量
const val WEEK_TYPE_ALL = 0   // 全部周
const val WEEK_TYPE_ODD = 1   // 单周
const val WEEK_TYPE_EVEN = 2  // 双周
```

- [ ] **Step 3: 为非显而易见的逻辑添加注释**

```kotlin
// 使用周次差值调整日期：新日期 = 原日期 + (旧周次 - 新周次) * 7
// 这样可以保持原始日期的星期几不变
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: 添加必要的中文注释"
```

---

## Task 11: 代码格式化

**Covers:** 代码格式化

**Files:**
- Modify: 所有 .kt 文件

- [ ] **Step 1: 配置 ktlint 或使用 IDE 格式化**

确保项目使用统一的代码格式化工具。

- [ ] **Step 2: 格式化所有 Kotlin 文件**

- 统一缩进（4空格）
- 统一空行规则
- 统一导入排序
- 统一括号位置

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "style: 统一代码格式化"
```

---

## Task 12: 更新 AndroidManifest.xml

**Covers:** 包结构重组

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 更新所有 Activity 的注册**

将所有 Activity 的注册路径更新为新的包名：

```xml
<activity
    android:name=".ui.activities.MainActivity"
    android:exported="true">
    ...
</activity>
<activity android:name=".ui.activities.AboutActivity" />
<activity android:name=".ui.activities.AppreciateAuthorActivity" />
<!-- ... 其他 Activity ... -->
```

- [ ] **Step 2: 更新 Receiver 和 Service 的注册**

检查是否有其他组件需要更新包名。

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: 更新 AndroidManifest.xml 中的组件注册"
```

---

## Task 13: 最终验证

**Covers:** 所有

**Files:**
- 无（验证任务）

- [ ] **Step 1: 完整编译**

```bash
./gradlew assembleDebug
```

- [ ] **Step 2: 检查编译警告**

确保没有编译警告或错误。

- [ ] **Step 3: 验证功能完整性**

确保所有功能正常工作：
- 课程添加/编辑/删除
- 课表切换
- 排班模式
- 设置保存
- 提醒功能

- [ ] **Step 4: 最终 Commit**

```bash
git add -A
git commit -m "chore: 完成代码整理，确保所有功能正常"
```
