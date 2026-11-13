package com.haooz.nexio.ohos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.activities.applyComboSchedule
import com.haooz.chedule.ui.activities.applyImportedScheduleConfig
import com.haooz.chedule.ui.activities.applyPresetTimeSlots
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.web.EduImportSelection
import com.haooz.chedule.ui.web.WebHostConfig
import com.haooz.chedule.ui.web.WebHostEvents
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor

/**
 * 鸿蒙的网页导入页（纯 ArkUI 界面）。
 *
 * 网页、顶栏（返回 / 刷新 / 标题）、底部条、弹窗全部由 ArkTS 画 —— 这一层只做：
 * 路由生命周期、导入结果的落库与收尾、系统返回键转发。
 *
 * ⚠ 刻意不发布 Compose 的 doc chrome：那套是「一层一个 Navigation 壳」，
 * 转场期间会与新页顶栏同时出现两套按钮，退出时还会比网页先走。
 */
@Composable
fun OhosWebImportRoute(router: AppRouter) {
    val school = EduImportSelection.school
    val adapter = EduImportSelection.adapter

    val courseViewModel = courseViewModel()
    val scheduleViewModel = scheduleViewModel()
    val settingsViewModel = settingsViewModel()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    val currentScheduleName by scheduleViewModel.currentScheduleName.collectAsState()

    // 选中项丢失（进程重建 / 直接深链进来）：没有可导入的目标，退回上一层
    if (school == null || adapter == null) {
        LaunchedEffect(Unit) { router.popBack() }
        return
    }

    val currentCoursesImported by rememberUpdatedState<(List<Course>) -> Unit> { courses ->
        // 桥接层已为导入课程设置 scheduleId（目标课表，空为当前课表）
        val targetScheduleId = courses.firstOrNull()?.scheduleId?.takeIf { it.isNotEmpty() }
        if (targetScheduleId != null && targetScheduleId != currentScheduleName) {
            scheduleViewModel.saveCoursesToSchedule(targetScheduleId, courses)
        } else {
            courseViewModel.replaceCourses(courses)
        }
        scheduleViewModel.refreshScheduleList()
        showToast(ImportStr.coursesSavedN(courses.size))
    }
    val currentTaskCompleted by rememberUpdatedState {
        val prefs = AppStorage.store("edu_import_prefs")
        val targetScheduleId = prefs.getStringOrNull("target_schedule_id")
        if (prefs.getStringOrNull("preset_time_slots") != null) {
            applyPresetTimeSlots(settingsViewModel, scheduleViewModel, targetScheduleId)
        }
        val hasStartDate = prefs.getStringOrNull("semester_start_date") != null
        val hasTotalWeeks = prefs.getInt("semester_total_weeks", -1) > 0
        val hasClassDuration = prefs.getInt("default_class_duration", -1) > 0
        val hasBreakDuration = prefs.getInt("default_break_duration", -1) > 0
        val hasFirstDayOfWeek = prefs.getInt("first_day_of_week", -1) > 0
        if (hasStartDate || hasTotalWeeks || hasClassDuration || hasBreakDuration || hasFirstDayOfWeek) {
            applyImportedScheduleConfig(courseViewModel, scheduleViewModel)
        }
        if (prefs.getStringOrNull("combo_schedule") != null) {
            applyComboSchedule(settingsViewModel, courseViewModel, scheduleViewModel, targetScheduleId)
        }
        ohosWebPrepareExit()
        router.popBack()
    }

    val events = remember(school) {
        WebHostEvents(
            onCoursesImported = { courses -> currentCoursesImported(courses) },
            onTaskCompleted = { currentTaskCompleted() },
        )
    }

    val host = rememberOhosWebHost(
        WebHostConfig(
            importUrl = adapter.importUrl,
            assetJsPath = adapter.assetJsPath,
            resourceFolder = school.resourceFolder,
            isDesktopMode = isTabletWidth(),
            // 网页顶部让位由 ArkUI 按自绘顶栏高度算
            contentTopPadding = 0.dp,
            schoolName = school.name,
            scheduleNames = scheduleNames,
            currentScheduleName = currentScheduleName,
        ),
        events,
    )

    NavigationBackHandlerFor(
        enabled = true,
        onBackCompleted = {
            if (host.canGoBack()) {
                host.goBack()
            } else {
                ohosWebPrepareExit()
                router.popBack()
            }
        },
        onBackCancelled = {},
    )

    // 空占位：内容全在 ArkUI 的 Web 层（画在覆盖层 Compose 之上）
    Box(modifier = Modifier.fillMaxSize())

    // 退出本页即清空选中项：否则下次从别处进来会拿到上一轮的学校
    DisposableEffect(Unit) {
        // ArkTS 顶栏的「返回」在网页没有历史时转回来
        ohosWebRequestClose = {
            ohosWebPrepareExit()
            router.popBack()
        }
        onDispose {
            ohosWebRequestClose = null
            EduImportSelection.clear()
        }
    }
}
