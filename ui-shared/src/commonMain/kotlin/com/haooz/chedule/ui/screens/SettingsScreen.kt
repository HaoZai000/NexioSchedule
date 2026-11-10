package com.haooz.chedule.ui.screens

// ⚠ 本文件不得出现 android.* / androidx.activity / androidx.core；平台能力做成注入参数。

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.haooz.chedule.data.util.lengthOfMonth
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.settings.SettingsCategory
import com.haooz.chedule.ui.settings.SettingsCategoryPage
import com.haooz.chedule.ui.settings.SettingsContentHost
import com.haooz.chedule.ui.settings.SettingsHome
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import kotlinx.datetime.LocalDate
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 解析失败时回退到今天，避免弹窗初值出现非法日期
internal fun parseDate(dateStr: String): Triple<Int, Int, Int> {
    return try {
        val parts = dateStr.split("/")
        Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } catch (_: Exception) {
        // kotlinx-datetime 替代 JVM 的 java.util.Calendar
        val d = todayLocalDate()
        Triple(d.year, d.monthNumber, d.dayOfMonth)
    }
}

internal fun getDaysInMonth(year: Int, month: Int): Int {
    return try {
        LocalDate(year, month, 1).lengthOfMonth()
    } catch (_: Exception) {
        31
    }
}

/**
 * 设置页入口。
 *
 * - 手机：主页是分类卡片（[SettingsHome]，9 个分类），详情在 `AppRoute.SettingsCategoryDetail` 的二级页；
 *   排班模式下主页直接内联「课表」分类内容（含对比课表与退出按钮）。
 * - 平板（`navBarStyle == "rail"`）：交给 [TabletSettingsScreen] 的两栏布局。
 */
@Composable
fun SettingsScreen(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    shiftViewModel: ShiftViewModel,
    isShiftMode: Boolean = false,
    onExitShiftMode: () -> Unit = {},
    onEnterShiftMode: () -> Unit = {},
    navBarStyle: String = "standard",
    settingsScrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /**
     * 是否显示「更新设置」入口（iOS / 鸿蒙走应用商店更新 → false，整个「更新」分类隐藏）。
     */
    showUpdateSettings: Boolean = true,
    /** 是否已授予定位权限（天气数据源摘要用；Android 宿主注入） */
    hasLocationPermission: () -> Boolean = { false },
    /** 清空天气缓存（切换天气源后立即失效） */
    onInvalidateWeatherCache: () -> Unit = {},
    /** 隐藏最近任务卡片（Android 专有能力） */
    onSetTaskExcludedFromRecents: (Boolean) -> Unit = {},
    /** 更新能力控制器（Android 注入；平板「更新」二级页内嵌用） */
    updateController: com.haooz.chedule.data.platform.UpdateController =
        com.haooz.chedule.data.platform.NoopUpdateController,
    /** 备份文件能力（Android=SAF）——平板「备份与同步」二级页内嵌本地备份用 */
    backupFileController: com.haooz.chedule.data.backup.BackupFileController =
        com.haooz.chedule.data.backup.NoopBackupFileController,
    /** 导入/导出平台能力包（Android 注入 SAF/口令/日历/手环）——「数据管理」二级页内嵌用 */
    dataManage: com.haooz.chedule.ui.settings.DataManagePlatform =
        com.haooz.chedule.ui.settings.DataManagePlatform(),
    /** 直达型分类（提醒/小组件/关于）内嵌所需的平台能力 */
    directPlatform: com.haooz.chedule.ui.settings.SettingsDirectPlatform =
        com.haooz.chedule.ui.settings.SettingsDirectPlatform(),
    /** 全量恢复成功后的平台副作用（重排提醒） */
    onHolidayDataChanged: () -> Unit = {},
) {
    val backgroundColor = MiuixTheme.colorScheme.surface
    // 平板：左栏分类卡片，右栏渲染该分类的二级页
    if (navBarStyle == "rail") {
        TabletSettingsScreen(
            viewModel = viewModel,
            scheduleViewModel = scheduleViewModel,
            settingsViewModel = settingsViewModel,
            shiftViewModel = shiftViewModel,
            isShiftMode = isShiftMode,
            onExitShiftMode = onExitShiftMode,
            onEnterShiftMode = onEnterShiftMode,
            liquidGlassBackdrop = liquidGlassBackdrop,
            showUpdateSettings = showUpdateSettings,
            hasLocationPermission = hasLocationPermission,
            onInvalidateWeatherCache = onInvalidateWeatherCache,
            onSetTaskExcludedFromRecents = onSetTaskExcludedFromRecents,
            updateController = updateController,
            backupFileController = backupFileController,
            dataManage = dataManage,
            directPlatform = directPlatform,
            onHolidayDataChanged = onHolidayDataChanged,
        )
        return
    }

    val settingsRouter = LocalAppRouter.current
    val isDark = isAppDarkTheme()

    val host = remember(
        viewModel,
        scheduleViewModel,
        settingsViewModel,
        shiftViewModel,
        isShiftMode,
        liquidGlassBackdrop,
        settingsRouter,
        isDark,
        // 平台能力包也要进 key：宿主会用新实例替换（如超级岛开关变化时重建 directPlatform），
        // 漏掉就会一直用第一份，表现为「开关变了但测试按钮文案/行为没变」。
        updateController,
        backupFileController,
        dataManage,
        directPlatform,
    ) {
        SettingsContentHost(
            courseViewModel = viewModel,
            scheduleViewModel = scheduleViewModel,
            settingsViewModel = settingsViewModel,
            shiftViewModel = shiftViewModel,
            isShiftMode = isShiftMode,
            liquidGlassBackdrop = liquidGlassBackdrop,
            hasLocationPermission = hasLocationPermission,
            onInvalidateWeatherCache = onInvalidateWeatherCache,
            onSetTaskExcludedFromRecents = onSetTaskExcludedFromRecents,
            updateController = updateController,
            // 四个能力必须与平板/路由两处同名传递：本分支目前只渲染「课表」用不到它们，
            // 但漏传不会报错、只在将来放开分类时表现为「点了没反应」。
            backupFileController = backupFileController,
            dataManage = dataManage,
            directPlatform = directPlatform,
            onHolidayDataChanged = onHolidayDataChanged,
            onExitShiftMode = onExitShiftMode,
            onEnterShiftMode = onEnterShiftMode,
            onNavigate = { route -> settingsRouter.navigate(route) },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (isShiftMode) {
            // 排班模式：直接进「课表」二级内容（含选择对比课表 + 退出按钮），少一层点击
            SettingsCategoryPage(
                category = SettingsCategory.Course,
                host = host,
                scrollBehavior = settingsScrollBehavior,
            )
        } else {
            SettingsHome(
                host = host,
                showUpdateSettings = showUpdateSettings,
                scrollBehavior = settingsScrollBehavior,
            )
        }
    }
}
