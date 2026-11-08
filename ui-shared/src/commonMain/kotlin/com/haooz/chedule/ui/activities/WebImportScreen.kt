/**
 * 教务导入第二步：网页导入页（独立路由 `AppRoute.WebImport`，右侧推入，返回先回选择页），
 * 学校 / 适配器经 [com.haooz.chedule.ui.web.EduImportSelection] 带入。
 * iOS 原生文档页上本页整页交给系统：导航栏与底部悬浮条由 Swift 画，安全区由系统 inset 处理。
 */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentActionEffect
import com.haooz.chedule.ui.utils.NativeDocumentLeadingActionEffect
import com.haooz.chedule.ui.utils.NativeDocumentSystemHeaderEffect
import com.haooz.chedule.ui.web.EduImportSelection
import com.haooz.chedule.ui.web.WebHost
import com.haooz.chedule.ui.web.WebHostConfig
import com.haooz.chedule.ui.web.WebHostEvents
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WebImportScreen(
    /** 网页宿主工厂：Android 注入 `AndroidWebHost`，iOS 注入 `IosWebHost`。 */
    webHostFactory: @Composable (WebHostConfig, WebHostEvents) -> WebHost,
    /** 退出本页（回选择页） */
    onFinish: () -> Unit,
) {
    val school = EduImportSelection.school
    val adapter = EduImportSelection.adapter

    val courseViewModel = courseViewModel()
    val scheduleViewModel = scheduleViewModel()
    val settingsViewModel = settingsViewModel()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    val currentScheduleName by scheduleViewModel.currentScheduleName.collectAsState()

    // 选中项丢失（进程重建 / 直接深链进来）：没有可导入的目标，退回上一层
    if (school == null || adapter == null) {
        LaunchedEffect(Unit) { onFinish() }
        return
    }

    val nativeChrome = LocalNativeDocumentChrome.current
    val nativeBarInset = (nativeChrome?.barInset ?: 0.0).dp
    // ⚠ 必须在组合期取好：SideEffect 的 lambda 不是 @Composable，读不了 MiuixTheme
    val chromeTint = MiuixTheme.colorScheme.primary.toArgb().toLong()
    // iOS 原生文档页：顶栏整个交给系统（原生导航栏标题 + 材质 + 关闭/刷新按钮）
    NativeDocumentSystemHeaderEffect(nativeChrome != null)

    val liquidGlassBackdrop: LayerBackdrop = com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop()
    val hapticFeedback = LocalHapticFeedback.current
    val webViewScrollBehavior = rememberSharedScrollBehavior()

    var webPageTitle by remember(AppLanguage.resolved) { mutableStateOf(CommonStr.loading()) }
    // 页面顶部取色：按亮度定顶栏标题/图标的黑白，不跟应用深浅色主题（仅非原生文档时用得上）
    var webTopColor by remember { mutableStateOf(Color.White) }
    val webTopIsLight = remember(webTopColor) {
        val lum = 0.299f * webTopColor.red + 0.587f * webTopColor.green + 0.114f * webTopColor.blue
        lum >= 0.55f
    }
    val webBarContentColor = if (webTopIsLight) Color.Black else Color.White
    var reloadWebViewAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // ⚠ 必须先读出来：SideEffect 在提交阶段跑，在那里读 state 不建立订阅（标题变化不触发重组）
    val chromeTitle = webPageTitle
    SideEffect {
        nativeChrome?.title = chromeTitle
        nativeChrome?.tintArgb = chromeTint
    }
    NativeDocumentLeadingActionEffect(
        if (nativeChrome != null) {
            NativeDocumentAction(symbol = "xmark", label = CommonStr.close(), onClick = onFinish)
        } else null
    )
    NativeDocumentActionEffect(
        NativeDocumentAction(
            symbol = "arrow.clockwise",
            label = ImportStr.refresh(),
            onClick = { reloadWebViewAction?.invoke() },
        )
    )

    Scaffold(
        modifier = Modifier.consumeWindowInsets(PaddingValues(top = nativeBarInset))
    ) { paddingValues ->
        // 顶栏交给系统时没有 Compose 顶栏，折叠高度按 0 算
        val topBarHeightDp = if (nativeChrome != null) {
            0.dp
        } else {
            with(LocalDensity.current) { (webViewScrollBehavior.currentHeightPx).toDp() }
        }
        val statusBarHeight = WindowInsets.statusBars
            .asPaddingValues().calculateTopPadding()
        val blurHeight = statusBarHeight +
            top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight + 40.dp
        val contentTopInset = paddingValues.calculateTopPadding() + nativeBarInset
        // 顶栏与底部胶囊共用：录的是 WebView Offscreen 层
        val webContentBackdrop = com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop()

        Box(modifier = Modifier.fillMaxSize()) {
            com.haooz.chedule.ui.screens.WebViewScreen(
                school = school,
                adapterId = adapter.adapterId,
                importUrl = adapter.importUrl,
                assetJsPath = adapter.assetJsPath,
                webContentBackdrop = webContentBackdrop,
                // 与选择页同构：Scaffold padding + 顶栏高度，内容贴在顶栏下
                contentTopPadding = (contentTopInset + topBarHeightDp + 12.dp)
                    .coerceAtLeast(0.dp),
                scheduleNames = scheduleNames,
                currentScheduleName = currentScheduleName,
                onBack = onFinish,
                onImportComplete = { courses ->
                    // 桥接层已为导入课程设置 scheduleId（目标课表，空为当前课表）
                    val targetScheduleId =
                        courses.firstOrNull()?.scheduleId?.takeIf { it.isNotEmpty() }
                    if (targetScheduleId != null && targetScheduleId != currentScheduleName) {
                        scheduleViewModel.saveCoursesToSchedule(targetScheduleId, courses)
                    } else {
                        courseViewModel.replaceCourses(courses)
                    }
                    scheduleViewModel.refreshScheduleList()
                    showToast(ImportStr.coursesSavedN(courses.size))
                },
                onTaskCompleted = {
                    val prefs = AppStorage.store("edu_import_prefs")
                    val hasPresetTimeSlots = prefs.getStringOrNull("preset_time_slots") != null
                    val targetScheduleId = prefs.getStringOrNull("target_schedule_id")

                    if (hasPresetTimeSlots) {
                        applyPresetTimeSlots(settingsViewModel, scheduleViewModel, targetScheduleId)
                    }
                    val hasStartDate = prefs.getStringOrNull("semester_start_date") != null
                    val hasTotalWeeks = prefs.getInt("semester_total_weeks", -1) > 0
                    val hasClassDuration = prefs.getInt("default_class_duration", -1) > 0
                    val hasBreakDuration = prefs.getInt("default_break_duration", -1) > 0
                    val hasFirstDayOfWeek = prefs.getInt("first_day_of_week", -1) > 0
                    if (hasStartDate || hasTotalWeeks || hasClassDuration ||
                        hasBreakDuration || hasFirstDayOfWeek
                    ) {
                        applyImportedScheduleConfig(courseViewModel, scheduleViewModel)
                    }
                    val hasComboSchedule = prefs.getStringOrNull("combo_schedule") != null
                    if (hasComboSchedule) {
                        applyComboSchedule(
                            settingsViewModel, courseViewModel, scheduleViewModel, targetScheduleId
                        )
                    }
                },
                onPageTitleChanged = { webPageTitle = it },
                onTopColorChanged = { webTopColor = it },
                onReloadRef = { action -> reloadWebViewAction = action },
                // 网页本体（WebView / JS 桥 / PixelCopy 采色）在平台侧，这里只把宿主造出来注入。
                webHostFactory = webHostFactory,
            )

            // iOS 顶栏归系统，Compose 渐变模糊顶栏整条不画
            if (nativeChrome == null) ProgressiveBlurTopBar(
                backdrop = webContentBackdrop,
                height = blurHeight,
                blurAlpha = 0f,
            ) {
                CollapsibleTopAppBar(
                    title = webPageTitle,
                    showLargeTitle = false,
                    // 教务页自带顶栏空白区色条，AppBar 不再叠任何遮罩
                    showShadow = false,
                    titleColor = webBarContentColor,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    scrollBehavior = webViewScrollBehavior,
                    contentPadding = {},
                    startAction = { backdropAlpha, shadowAlpha ->
                        LiquidTopBarButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onFinish()
                            },
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.Close,
                            iconTint = webBarContentColor.copy(alpha = 0.85f),
                            contentDescription = CommonStr.close(),
                            performHapticFeedback = false,
                            iconSize = 22.dp,
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                        )
                    },
                    endAction = { backdropAlpha, shadowAlpha ->
                        LiquidTopBarButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                reloadWebViewAction?.invoke()
                            },
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.Refresh,
                            iconTint = webBarContentColor.copy(alpha = 0.85f),
                            contentDescription = ImportStr.refresh(),
                            performHapticFeedback = false,
                            iconSize = 24.dp,
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                        )
                    },
                )
            }
        }
    }

    // 退出本页即清空选中项：否则下次从别处进来会拿到上一轮的学校
    DisposableEffect(Unit) {
        onDispose { EduImportSelection.clear() }
    }
}
