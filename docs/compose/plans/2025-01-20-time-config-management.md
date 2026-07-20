# 课程时间配置管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现多时间配置管理功能，支持创建、编辑、切换多个时间配置，不同课表可以绑定不同时间配置

**Architecture:** 
- 数据层：TimeConfig 数据类 + CourseRepository 扩展
- UI层：TimeConfigSelectScreen（选择页面）+ TimeConfigEditScreen（编辑页面）
- 迁移：自动将现有时间设置迁移为默认时间配置

**Tech Stack:** Kotlin, Jetpack Compose, Miuix UI, SharedPreferences, Gson

---

## 文件结构

### 新建文件
- `app/src/main/java/com/haooz/chedule/data/TimeConfig.kt` - 时间配置数据类
- `app/src/main/java/com/haooz/chedule/ui/activities/TimeConfigEditScreen.kt` - 编辑时间配置页面

### 修改文件
- `app/src/main/java/com/haooz/chedule/data/CourseRepository.kt` - 添加时间配置 CRUD 操作
- `app/src/main/java/com/haooz/chedule/ui/activities/CourseTimeSettingsScreen.kt` - 改为选择页面
- `app/src/main/java/com/haooz/chedule/ui/activities/CourseTimeSettingsActivity.kt` - 更新页面导航
- `app/src/main/java/com/haooz/chedule/viewmodel/SettingsViewModel.kt` - 添加时间配置相关状态

---

## Task 1: 创建 TimeConfig 数据类

**Covers:** 数据模型定义

**Files:**
- Create: `app/src/main/java/com/haooz/chedule/data/TimeConfig.kt`

**Interfaces:**
- Produces: `TimeConfig` data class，供后续任务使用

- [ ] **Step 1: 创建 TimeConfig 数据类**

```kotlin
package com.haooz.chedule.data

/**
 * 时间配置数据类
 * 存储一套完整的时间设置（节数、时长、开始时间等）
 */
data class TimeConfig(
    val id: Long = System.currentTimeMillis(),
    val name: String = "默认时间",
    val morningSections: Int = 4,
    val afternoonSections: Int = 4,
    val eveningSections: Int = 4,
    // 快捷设置参数
    val quickTimeEnabled: Boolean = false,
    val classDuration: Int = 45,
    val shortBreak: Int = 10,
    val longBreakEnabled: Boolean = false,
    val longBreakMorning: Int = 20,
    val longBreakAfternoon: Int = 20,
    val longBreakEvening: Int = 20,
    val longBreakMorningSection: Int = 2,
    val longBreakAfternoonSection: Int = 2,
    val longBreakEveningSection: Int = 2,
    // 开始时间
    val morningStartHour: Int = 8,
    val morningStartMinute: Int = 0,
    val afternoonStartHour: Int = 14,
    val afternoonStartMinute: Int = 0,
    val eveningStartHour: Int = 18,
    val eveningStartMinute: Int = 30,
    // 各节次时间（key: 时段内相对节次号 1-6, value: "HH:mm-HH:mm"）
    val sectionTimes: Map<Int, String> = emptyMap()
) {
    companion object {
        // 默认时间配置
        val default = TimeConfig(name = "默认时间")
    }
    
    /**
     * 获取指定时段的节次时间
     * @param period "morning" / "afternoon" / "evening"
     */
    fun getPeriodTimes(period: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for ((k, v) in sectionTimes) {
            val strKey = k.toString()
            if (strKey.startsWith("${period}_")) {
                val idx = strKey.removePrefix("${period}_").toIntOrNull()
                if (idx != null) result[idx] = v
            }
        }
        return result.ifEmpty { getDefaultTimesForPeriod(period) }
    }
    
    private fun getDefaultTimesForPeriod(period: String): Map<Int, String> = when (period) {
        "morning" -> Course.defaultMorningTimes
        "afternoon" -> Course.defaultAfternoonTimes
        "evening" -> Course.defaultEveningTimes
        else -> emptyMap()
    }
    
    /**
     * 计算并返回更新后的节次时间
     * 根据快捷设置参数计算各节次的开始和结束时间
     */
    fun calculateSectionTimes(): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        
        // 上午
        val morningTimes = Course.calculatePeriodTimes(
            morningSections, morningStartHour, morningStartMinute,
            classDuration, shortBreak,
            if (longBreakEnabled) longBreakMorning else shortBreak,
            if (longBreakEnabled) longBreakMorningSection else 2
        )
        for ((k, v) in morningTimes) {
            result["morning_$k".hashCode()] = v
        }
        
        // 下午
        val afternoonTimes = Course.calculatePeriodTimes(
            afternoonSections, afternoonStartHour, afternoonStartMinute,
            classDuration, shortBreak,
            if (longBreakEnabled) longBreakAfternoon else shortBreak,
            if (longBreakEnabled) longBreakAfternoonSection else 2
        )
        for ((k, v) in afternoonTimes) {
            result["afternoon_$k".hashCode()] = v
        }
        
        // 晚上
        val eveningTimes = Course.calculatePeriodTimes(
            eveningSections, eveningStartHour, eveningStartMinute,
            classDuration, shortBreak,
            if (longBreakEnabled) longBreakEvening else shortBreak,
            if (longBreakEnabled) longBreakEveningSection else 2
        )
        for ((k, v) in eveningTimes) {
            result["evening_$k".hashCode()] = v
        }
        
        return result
    }
}
```

- [ ] **Step 2: 验证编译通过**

Run: 在 Android Studio 中编译项目
Expected: 编译成功，无错误

---

## Task 2: 扩展 CourseRepository 支持时间配置

**Covers:** 数据层 CRUD 操作

**Files:**
- Modify: `app/src/main/java/com/haooz/chedule/data/CourseRepository.kt`

**Interfaces:**
- Consumes: `TimeConfig` data class
- Produces: 时间配置的增删改查方法

- [ ] **Step 1: 添加时间配置相关的常量和方法**

在 CourseRepository.kt 的 companion object 中添加：
```kotlin
// 时间配置相关
private const val KEY_TIME_CONFIG_IDS = "time_config_ids"
private const val KEY_CURRENT_TIME_CONFIG_ID = "current_time_config_id"
private const val TIME_CONFIG_PREFIX = "time_config_"
```

在 CourseRepository 类中添加以下方法：
```kotlin
// --- 时间配置支持 ---

/** 获取所有时间配置 ID 列表 */
fun getTimeConfigIds(): List<Long> {
    val idsStr = prefs.getString(KEY_TIME_CONFIG_IDS, null) ?: return listOf(0L)
    return idsStr.split(",").mapNotNull { it.toLongOrNull() }
}

/** 获取当前选中的时间配置 ID */
fun getCurrentTimeConfigId(): Long {
    return prefs.getLong(KEY_CURRENT_TIME_CONFIG_ID, 0L)
}

/** 设置当前选中的时间配置 ID */
fun setCurrentTimeConfigId(id: Long) {
    prefs.edit { putLong(KEY_CURRENT_TIME_CONFIG_ID, id) }
    notifyCourseChanged("settings")
}

/** 获取时间配置 */
fun getTimeConfig(id: Long): TimeConfig {
    val json = prefs.getString("$TIME_CONFIG_PREFIX$id", null)
    if (json == null) {
        // 返回默认配置
        return TimeConfig.default.copy(id = id)
    }
    return try {
        gson.fromJson(json, TimeConfig::class.java) ?: TimeConfig.default.copy(id = id)
    } catch (_: Exception) {
        TimeConfig.default.copy(id = id)
    }
}

/** 保存时间配置 */
fun saveTimeConfig(config: TimeConfig) {
    val json = gson.toJson(config)
    prefs.edit { putString("$TIME_CONFIG_PREFIX${config.id}", json) }
    // 确保 ID 在列表中
    val ids = getTimeConfigIds().toMutableList()
    if (config.id !in ids) {
        ids.add(config.id)
        prefs.edit { putString(KEY_TIME_CONFIG_IDS, ids.joinToString(",")) }
    }
    notifyCourseChanged("settings")
}

/** 添加新时间配置，返回新 ID */
fun addTimeConfig(config: TimeConfig): Long {
    val newId = if (config.id == 0L) System.currentTimeMillis() else config.id
    val newConfig = config.copy(id = newId)
    saveTimeConfig(newConfig)
    // 如果是第一个配置，设为当前
    if (getTimeConfigIds().size == 1) {
        setCurrentTimeConfigId(newId)
    }
    return newId
}

/** 删除时间配置 */
fun deleteTimeConfig(id: Long) {
    val ids = getTimeConfigIds().toMutableList()
    if (!ids.remove(id)) return
    prefs.edit {
        putString(KEY_TIME_CONFIG_IDS, ids.joinToString(","))
        remove("$TIME_CONFIG_PREFIX$id")
    }
    // 如果删除的是当前配置，切换到第一个
    if (getCurrentTimeConfigId() == id && ids.isNotEmpty()) {
        setCurrentTimeConfigId(ids.first())
    } else if (ids.isEmpty()) {
        // 删光后创建默认配置
        val defaultId = addTimeConfig(TimeConfig.default)
        setCurrentTimeConfigId(defaultId)
    }
    notifyCourseChanged("settings")
}

/** 重命名时间配置 */
fun renameTimeConfig(id: Long, newName: String) {
    val config = getTimeConfig(id).copy(name = newName)
    saveTimeConfig(config)
}

/** 获取当前时间配置 */
fun getCurrentTimeConfig(): TimeConfig {
    return getTimeConfig(getCurrentTimeConfigId())
}

/** 切换到指定时间配置 */
fun switchToTimeConfig(id: Long) {
    setCurrentTimeConfigId(id)
    // 应用该配置的时间设置到当前课表
    val config = getTimeConfig(id)
    applyTimeConfigToCurrentSchedule(config)
}

/** 将时间配置应用到当前课表 */
private fun applyTimeConfigToCurrentSchedule(config: TimeConfig) {
    setMorningSections(config.morningSections)
    setAfternoonSections(config.afternoonSections)
    setEveningSections(config.eveningSections)
    setQuickTimeEnabled(config.quickTimeEnabled)
    setClassDuration(config.classDuration)
    setShortBreak(config.shortBreak)
    setLongBreakEnabled(config.longBreakEnabled)
    setLongBreakMorning(config.longBreakMorning)
    setLongBreakAfternoon(config.longBreakAfternoon)
    setLongBreakEvening(config.longBreakEvening)
    setLongBreakMorningSection(config.longBreakMorningSection)
    setLongBreakAfternoonSection(config.longBreakAfternoonSection)
    setLongBreakEveningSection(config.longBreakEveningSection)
    setMorningStartHour(config.morningStartHour)
    setMorningStartMinute(config.morningStartMinute)
    setAfternoonStartHour(config.afternoonStartHour)
    setAfternoonStartMinute(config.afternoonStartMinute)
    setEveningStartHour(config.eveningStartHour)
    setEveningStartMinute(config.eveningStartMinute)
    // 保存节次时间
    savePeriodTimes("morning", config.getPeriodTimes("morning"))
    savePeriodTimes("afternoon", config.getPeriodTimes("afternoon"))
    savePeriodTimes("evening", config.getPeriodTimes("evening"))
}

/** 从当前课表设置创建时间配置 */
fun createTimeConfigFromCurrent(name: String): Long {
    val config = TimeConfig(
        name = name,
        morningSections = getMorningSections(),
        afternoonSections = getAfternoonSections(),
        eveningSections = getEveningSections(),
        quickTimeEnabled = getQuickTimeEnabled(),
        classDuration = getClassDuration(),
        shortBreak = getShortBreak(),
        longBreakEnabled = getLongBreakEnabled(),
        longBreakMorning = getLongBreakMorning(),
        longBreakAfternoon = getLongBreakAfternoon(),
        longBreakEvening = getLongBreakEvening(),
        longBreakMorningSection = getLongBreakMorningSection(),
        longBreakAfternoonSection = getLongBreakAfternoonSection(),
        longBreakEveningSection = getLongBreakEveningSection(),
        morningStartHour = getMorningStartHour(),
        morningStartMinute = getMorningStartMinute(),
        afternoonStartHour = getAfternoonStartHour(),
        afternoonStartMinute = getAfternoonStartMinute(),
        eveningStartHour = getEveningStartHour(),
        eveningStartMinute = getEveningStartMinute(),
        sectionTimes = buildSectionTimesMap()
    )
    return addTimeConfig(config)
}

/** 构建节次时间 Map */
private fun buildSectionTimesMap(): Map<Int, String> {
    val result = mutableMapOf<Int, String>()
    val morning = getPeriodTimes("morning")
    val afternoon = getPeriodTimes("afternoon")
    val evening = getPeriodTimes("evening")
    for ((k, v) in morning) result["morning_$k".hashCode()] = v
    for ((k, v) in afternoon) result["afternoon_$k".hashCode()] = v
    for ((k, v) in evening) result["evening_$k".hashCode()] = v
    return result
}

/** 迁移：如果没有时间配置，从当前设置创建默认配置 */
fun migrateToTimeConfigsIfNeeded() {
    if (prefs.contains(KEY_TIME_CONFIG_IDS)) return
    // 从当前设置创建默认配置
    val defaultId = createTimeConfigFromCurrent("默认时间")
    setCurrentTimeConfigId(defaultId)
}
```

- [ ] **Step 2: 验证编译通过**

Run: 在 Android Studio 中编译项目
Expected: 编译成功，无错误

---

## Task 3: 创建 TimeConfigEditScreen

**Covers:** 编辑时间配置页面 UI

**Files:**
- Create: `app/src/main/java/com/haooz/chedule/ui/activities/TimeConfigEditScreen.kt`

**Interfaces:**
- Consumes: `TimeConfig` 数据
- Produces: 编辑后的 `TimeConfig`

- [ ] **Step 1: 创建 TimeConfigEditScreen 基本结构**

参考 `CourseTimeSettingsScreen.kt` 的结构，创建编辑页面：

```kotlin
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor

private fun parseTimeRange(timeStr: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    return try {
        val parts = timeStr.split("-")
        val startParts = parts[0].split(":")
        val endParts = parts[1].split(":")
        Pair(
            Pair(startParts[0].toInt(), startParts[1].toInt()),
            Pair(endParts[0].toInt(), endParts[1].toInt())
        )
    } catch (_: Exception) {
        Pair(Pair(8, 0), Pair(8, 45))
    }
}

@SuppressLint("DefaultLocale", "AutoboxingStateValueProperty")
@Composable
fun TimeConfigEditScreen(
    timeConfig: TimeConfig,
    onBack: () -> Unit,
    onSave: (TimeConfig) -> Unit,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    // 编辑状态
    var configName by remember { mutableStateOf(timeConfig.name) }
    var morningSections by remember { mutableIntStateOf(timeConfig.morningSections) }
    var afternoonSections by remember { mutableIntStateOf(timeConfig.afternoonSections) }
    var eveningSections by remember { mutableIntStateOf(timeConfig.eveningSections) }
    
    // 快捷设置
    var quickTimeEnabled by remember { mutableStateOf(timeConfig.quickTimeEnabled) }
    var classDuration by remember { mutableIntStateOf(timeConfig.classDuration) }
    var shortBreak by remember { mutableIntStateOf(timeConfig.shortBreak) }
    var longBreakEnabled by remember { mutableStateOf(timeConfig.longBreakEnabled) }
    var longBreakMorning by remember { mutableIntStateOf(timeConfig.longBreakMorning) }
    var longBreakAfternoon by remember { mutableIntStateOf(timeConfig.longBreakAfternoon) }
    var longBreakEvening by remember { mutableIntStateOf(timeConfig.longBreakEvening) }
    var longBreakMorningSection by remember { mutableIntStateOf(timeConfig.longBreakMorningSection) }
    var longBreakAfternoonSection by remember { mutableIntStateOf(timeConfig.longBreakAfternoonSection) }
    var longBreakEveningSection by remember { mutableIntStateOf(timeConfig.longBreakEveningSection) }
    var morningStartHour by remember { mutableIntStateOf(timeConfig.morningStartHour) }
    var morningStartMinute by remember { mutableIntStateOf(timeConfig.morningStartMinute) }
    var afternoonStartHour by remember { mutableIntStateOf(timeConfig.afternoonStartHour) }
    var afternoonStartMinute by remember { mutableIntStateOf(timeConfig.afternoonStartMinute) }
    var eveningStartHour by remember { mutableIntStateOf(timeConfig.eveningStartHour) }
    var eveningStartMinute by remember { mutableIntStateOf(timeConfig.eveningStartMinute) }
    
    // 节次时间
    var morningTimes by remember { mutableStateOf(timeConfig.getPeriodTimes("morning")) }
    var afternoonTimes by remember { mutableStateOf(timeConfig.getPeriodTimes("afternoon")) }
    var eveningTimes by remember { mutableStateOf(timeConfig.getPeriodTimes("evening")) }
    
    // 弹窗状态
    var showQuickItemDialog by remember { mutableStateOf(false) }
    var quickEditType by remember { mutableStateOf("") }
    var quickTempValue by remember { mutableIntStateOf(0) }
    var quickTempSection by remember { mutableIntStateOf(2) }
    var quickTempHour by remember { mutableIntStateOf(0) }
    var quickTempMinute by remember { mutableIntStateOf(0) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var editingSection by remember { mutableIntStateOf(1) }
    var editingPeriod by remember { mutableStateOf("morning") }
    var tempStartHour by remember { mutableIntStateOf(8) }
    var tempStartMinute by remember { mutableIntStateOf(0) }
    var tempEndHour by remember { mutableIntStateOf(8) }
    var tempEndMinute by remember { mutableIntStateOf(45) }
    
    val minuteValues = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55)
    
    // 构建 TimeConfig 并保存
    fun buildAndSaveConfig() {
        val updatedConfig = timeConfig.copy(
            name = configName,
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            quickTimeEnabled = quickTimeEnabled,
            classDuration = classDuration,
            shortBreak = shortBreak,
            longBreakEnabled = longBreakEnabled,
            longBreakMorning = longBreakMorning,
            longBreakAfternoon = longBreakAfternoon,
            longBreakEvening = longBreakEvening,
            longBreakMorningSection = longBreakMorningSection,
            longBreakAfternoonSection = longBreakAfternoonSection,
            longBreakEveningSection = longBreakEveningSection,
            morningStartHour = morningStartHour,
            morningStartMinute = morningStartMinute,
            afternoonStartHour = afternoonStartHour,
            afternoonStartMinute = afternoonStartMinute,
            eveningStartHour = eveningStartHour,
            eveningStartMinute = eveningStartMinute,
            sectionTimes = buildSectionTimesMap()
        )
        onSave(updatedConfig)
    }
    
    fun buildSectionTimesMap(): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for ((k, v) in morningTimes) result["morning_$k".hashCode()] = v
        for ((k, v) in afternoonTimes) result["afternoon_$k".hashCode()] = v
        for ((k, v) in eveningTimes) result["evening_$k".hashCode()] = v
        return result
    }
    
    // UI 渲染（参考 CourseTimeSettingsScreen 的结构）
    // ... (完整实现见实际文件)
}
```

- [ ] **Step 2: 完成 TimeConfigEditScreen 的完整实现**

完整的 UI 实现，包含：
1. 顶部导航栏（左上角返回，右上角保存）
2. 配置名称输入
3. 快捷设置区域（可展开/收起）
4. 上午/下午/晚上各节课的时间设置
5. 各种弹窗（时间选择、节数设置等）

- [ ] **Step 3: 验证编译通过**

Run: 在 Android Studio 中编译项目
Expected: 编译成功，无错误

---

## Task 4: 修改 CourseTimeSettingsScreen 为选择页面

**Covers:** 选择时间配置页面 UI

**Files:**
- Modify: `app/src/main/java/com/haooz/chedule/ui/activities/CourseTimeSettingsScreen.kt`

**Interfaces:**
- Consumes: `TimeConfig` 列表
- Produces: 选择/创建/编辑时间配置的交互

- [ ] **Step 1: 重写 CourseTimeSettingsScreen**

将原来的编辑页面改为选择页面：

```kotlin
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor

@SuppressLint("DefaultLocale")
@Composable
fun CourseTimeSettingsScreen(
    onBack: () -> Unit,
    onEditConfig: (TimeConfig) -> Unit,
    onCreateConfig: () -> Unit,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val repository = remember { CourseRepository(context) }
    
    // 时间配置列表
    var timeConfigs by remember { mutableStateOf<List<TimeConfig>>(emptyList()) }
    var currentConfigId by remember { mutableLongStateOf(repository.getCurrentTimeConfigId()) }
    
    // 加载时间配置列表
    LaunchedEffect(Unit) {
        val ids = repository.getTimeConfigIds()
        timeConfigs = ids.map { repository.getTimeConfig(it) }
    }
    
    // 删除确认弹窗
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableLongStateOf(0L) }
    
    // 新建名称输入
    var showNewConfigDialog by remember { mutableStateOf(false) }
    var newConfigName by remember { mutableStateOf("") }
    
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }
    
    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass" && liquidGlassBackdrop != null
    val blurAlpha = if (!isLiquidGlass) {
        if (listScrollY < 50) 0f else ((listScrollY - 50) / 30f).coerceIn(0f, 0.7f)
    } else 0f
    val topBarColorProgress = if (!isLiquidGlass) ((listScrollY - 50) / 30f).coerceIn(0f, 1f) else 0f
    val topBarColor = if (!isLiquidGlass) {
        if (listScrollY < 50) MiuixTheme.colorScheme.surface
        else {
            val surface = MiuixTheme.colorScheme.surface
            val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
            lerp(surface, target, topBarColorProgress)
        }
    } else MiuixTheme.colorScheme.surface
    val topAppBarColors = if (!isLiquidGlass) {
        BlurDefaults.blurColors(
            blendColors = listOf(
                if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
                else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            ),
            brightness = 0f, contrast = 1f, saturation = 1.2f
        )
    } else null

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (!isLiquidGlass) {
                    TopAppBar(
                        modifier = if (blurAlpha > 0f) {
                            Modifier.textureBlur(backdrop = backdrop, shape = RectangleShape, colors = topAppBarColors!!)
                        } else Modifier,
                        color = topBarColor,
                        title = "课程时间", largeTitle = "课程时间",
                        scrollBehavior = scrollBehavior,
                        navigationIconPadding = 20.dp,
                        navigationIcon = {
                            IconButton(onClick = { onBack() }) {
                                Icon(MiuixIcons.Back,
                                    contentDescription = "返回",
                                    modifier = Modifier.size(28.dp))
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemScrollOffset }
                        .collect { offset ->
                            listScrollY = offset
                        }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(
                            hapticFeedbackType = HapticFeedbackType.TextHandleMove
                        )
                        .then(
                            if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
                        ),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = if (isLiquidGlass) paddingValues.calculateTopPadding() + 64.dp else paddingValues.calculateTopPadding() + 8.dp,
                        end = 16.dp,
                        bottom = 60.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 时间配置列表
                    items(timeConfigs, key = { it.id }) { config ->
                        val isCurrent = config.id == currentConfigId
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 切换到该时间配置
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        repository.switchToTimeConfig(config.id)
                                        currentConfigId = config.id
                                        Toast.makeText(context, "已切换到「${config.name}」", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = config.name,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "上午${config.morningSections}节 · 下午${config.afternoonSections}节 · 晚上${config.eveningSections}节",
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    if (isCurrent) {
                                        Text(
                                            text = "当前使用",
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                                // 编辑按钮
                                IconButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onEditConfig(config)
                                    }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Edit,
                                        contentDescription = "编辑",
                                        modifier = Modifier.size(22.dp),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                }
                                // 删除按钮（仅非当前配置可删除）
                                if (!isCurrent) {
                                    IconButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            deleteTargetId = config.id
                                            showDeleteDialog = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(22.dp),
                                            tint = MiuixTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // 新建按钮
                    item {
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            TextButton(
                                text = "+ 新建时间配置",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    newConfigName = ""
                                    showNewConfigDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    }
                }
            }
        }
        
        // 删除确认弹窗
        OverlayDialog(
            title = "删除时间配置",
            summary = "确定要删除该时间配置吗？",
            show = showDeleteDialog,
            onDismissRequest = { showDeleteDialog = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        repository.deleteTimeConfig(deleteTargetId)
                        // 刷新列表
                        val ids = repository.getTimeConfigIds()
                        timeConfigs = ids.map { repository.getTimeConfig(it) }
                        currentConfigId = repository.getCurrentTimeConfigId()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsError(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 新建配置弹窗
        OverlayDialog(
            title = "新建时间配置",
            show = showNewConfigDialog,
            onDismissRequest = { showNewConfigDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                top.yukonga.miuix.kmp.basic.TextField(
                    value = newConfigName,
                    onValueChange = { newConfigName = it },
                    label = "配置名称",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showNewConfigDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "创建",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (newConfigName.isNotBlank()) {
                                val newId = repository.createTimeConfigFromCurrent(newConfigName)
                                // 刷新列表
                                val ids = repository.getTimeConfigIds()
                                timeConfigs = ids.map { repository.getTimeConfig(it) }
                                // 进入编辑页面
                                val newConfig = repository.getTimeConfig(newId)
                                showNewConfigDialog = false
                                onEditConfig(newConfig)
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译通过**

Run: 在 Android Studio 中编译项目
Expected: 编译成功，无错误

---

## Task 5: 更新 CourseTimeSettingsActivity

**Covers:** 页面导航逻辑

**Files:**
- Modify: `app/src/main/java/com/haooz/chedule/ui/activities/CourseTimeSettingsActivity.kt`

**Interfaces:**
- Consumes: `TimeConfig` 数据
- Produces: 页面导航逻辑

- [ ] **Step 1: 更新 CourseTimeSettingsActivity**

```kotlin
package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.rememberAppStyle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.unit.DpOffset
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class CourseTimeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        setContent {
            CourseScheduleTheme {
                val backgroundColor = MiuixTheme.colorScheme.surface
                val backdrop = rememberLayerBackdrop {
                    drawRect(backgroundColor)
                    drawContent()
                }
                val appStyle = rememberAppStyle()
                val liquidGlassBackdrop = if (appStyle == "liquidglass") {
                    com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                } else null
                val isLiquidGlass = liquidGlassBackdrop != null
                
                // 页面状态：select = 选择页面, edit = 编辑页面
                var screenState by remember { mutableStateOf("select") }
                var editingConfig by remember { mutableStateOf<TimeConfig?>(null) }
                
                Scaffold(
                    topBar = {
                        if (isLiquidGlass) {
                            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                            ProgressiveBlurTopBar(
                                backdrop = liquidGlassBackdrop!!,
                            ) {
                                SmallTopAppBar(
                                    color = Color.Transparent,
                                    title = if (screenState == "select") "课程时间" else "编辑时间配置",
                                    modifier = Modifier.zIndex(1f),
                                    navigationIcon = {}
                                )
                                LiquidTopBarButton(
                                    onClick = { 
                                        if (screenState == "edit") {
                                            screenState = "select"
                                        } else {
                                            finish() 
                                        }
                                    },
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Medium.ChevronBackward,
                                    contentDescription = "返回",
                                    modifier = Modifier
                                        .zIndex(2f)
                                        .offset(x = 20.dp, y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                                    iconSize = 22.dp,
                                    iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                    useBackdropShadow = true
                                )
                            }
                        }
                    }
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().then(
                                if (liquidGlassBackdrop != null) Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                                else Modifier
                            )
                        ) {
                            when (screenState) {
                                "select" -> {
                                    CourseTimeSettingsScreen(
                                        onBack = { finish() },
                                        onEditConfig = { config ->
                                            editingConfig = config
                                            screenState = "edit"
                                        },
                                        onCreateConfig = {
                                            // 新建后自动进入编辑页面，在 CourseTimeSettingsScreen 中处理
                                        },
                                        liquidGlassBackdrop = liquidGlassBackdrop
                                    )
                                }
                                "edit" -> {
                                    editingConfig?.let { config ->
                                        TimeConfigEditScreen(
                                            timeConfig = config,
                                            onBack = { screenState = "select" },
                                            onSave = { updatedConfig ->
                                                val repository = CourseRepository(this@CourseTimeSettingsActivity)
                                                repository.saveTimeConfig(updatedConfig)
                                                // 如果是当前配置，应用到当前课表
                                                if (updatedConfig.id == repository.getCurrentTimeConfigId()) {
                                                    repository.switchToTimeConfig(updatedConfig.id)
                                                }
                                                screenState = "select"
                                            },
                                            liquidGlassBackdrop = liquidGlassBackdrop
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译通过**

Run: 在 Android Studio 中编译项目
Expected: 编译成功，无错误

---

## Task 6: 添加数据迁移逻辑

**Covers:** 现有数据迁移

**Files:**
- Modify: `app/src/main/java/com/haooz/chedule/data/CourseRepository.kt`

**Interfaces:**
- Consumes: 现有 SharedPreferences 数据
- Produces: 迁移后的时间配置

- [ ] **Step 1: 在合适的位置调用迁移方法**

在 `CourseRepository` 的初始化或其他合适位置添加：
```kotlin
init {
    // 迁移：如果没有时间配置，从当前设置创建默认配置
    migrateToTimeConfigsIfNeeded()
}
```

- [ ] **Step 2: 验证编译通过**

Run: 在 Android Studio 中编译项目
Expected: 编译成功，无错误

---

## Task 7: 测试和调试

**Covers:** 功能验证

**Files:**
- 无新文件

**Interfaces:**
- 无

- [ ] **Step 1: 运行应用并测试基本功能**

1. 进入"我的" → "课程时间设置"
2. 验证显示时间配置列表（应该有一个默认配置）
3. 点击配置项，验证切换功能
4. 点击编辑按钮，验证进入编辑页面
5. 在编辑页面修改设置，验证保存功能
6. 点击新建按钮，验证创建新配置功能
7. 测试删除功能

- [ ] **Step 2: 修复发现的问题**

根据测试结果修复任何问题

- [ ] **Step 3: 验证数据持久化**

1. 创建新配置
2. 退出应用
3. 重新进入，验证配置仍然存在

---

## 完成

所有任务完成后，课程时间配置管理功能应该可以正常工作：
- 可以创建多个时间配置
- 可以编辑每个配置的详细设置
- 可以在不同配置间切换
- 不同课表可以绑定不同时间配置
- 数据持久化存储