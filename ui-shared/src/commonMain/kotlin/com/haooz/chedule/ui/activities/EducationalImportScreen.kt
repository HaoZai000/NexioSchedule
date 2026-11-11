/**
 * 教务导入第一步：学校 / 适配器选择，由路由 `AppRoute.EducationalImport` 打开。
 * 选完跳独立路由 `AppRoute.WebImport`（见 WebImportScreen）。
 */
package com.haooz.chedule.ui.activities


import com.haooz.chedule.ui.components.PageHeaderContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.TimeRoutine
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.school.ScriptRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.data.util.hasKey
import com.haooz.chedule.data.util.isJsonNull
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonArray
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentActionEffect
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.ui.web.EduImportSelection
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberTopBarMaterialState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 学校 / 适配器选择页主体。
 * @param dataVersion 数据版本号，+1 触发列表刷新
 */
@Composable
fun EducationalImportScreen(
    isInFreeformWindow: Boolean,
    isUpdating: Boolean,
    dataVersion: Int,
    onForceUpdate: () -> Unit,
    onFinish: () -> Unit,
    /**
     * 选完学校/适配器后打开网页导入页（独立路由）。
     * ⚠ 由路由层注入而非页内读 `LocalAppRouter`：iOS 原生文档页容器不 provide 它，默认值会抛 error。
     */
    onOpenWebImport: () -> Unit,
) {
    val courseViewModel = courseViewModel()
    val scheduleViewModel = scheduleViewModel()
    val settingsViewModel = settingsViewModel()

    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    val currentScheduleName by scheduleViewModel.currentScheduleName.collectAsState()

    // 本页只做学校 / 适配器选择：顶栏标题与渐变模糊由 Compose 画，
    // 左右两个按钮在 iOS 原生文档下由 Swift 画（避免双层）。整页系统化只用于 WebImportScreen。
    val nativeChrome = LocalNativeDocumentChrome.current
    val nativeBarInset = (nativeChrome?.barInset ?: 0.0).dp
    val showNavigationActions = nativeChrome == null
    // ⚠ 必须在组合期取好：SideEffect 的 lambda 不是 @Composable，读不了 MiuixTheme
    val chromeTint = MiuixTheme.colorScheme.primary.toArgb().toLong()

    val backgroundColor = MiuixTheme.colorScheme.surface
    // 本页 backdrop 是纯采样层（onDraw 画背景+内容，contentOnlyCapture=false，
    // 上屏本来就双遍历），降采样录制不增加遍历成本，采样 blit 面积降 4 倍。
    // 顶栏 ProgressiveBlurTopBar 采它；模糊带内容对本就不敏感。
    val backdrop = rememberLayerBackdrop(recordScale = 0.5f) {
        drawRect(backgroundColor)
        drawContent()
    }
    val liquidGlassBackdrop: LayerBackdrop = com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop()
    val scrollBehavior = rememberSharedScrollBehavior()

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // 点击搜索框时收起标题栏
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            scrollBehavior.collapse()
        }
    }

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) {
        val widthDp = screenWidthDp()
        ((widthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 0.dp
    val hapticFeedback = LocalHapticFeedback.current
    val displayTabs = listOf(ImportStr.tabSchoolImport(), ImportStr.generalTools())

    // 标题发布放在 Scaffold 外：content lambda 会被缓存，放里面不保证重新执行
    SideEffect {
        nativeChrome?.title = ImportStr.selectSchool()
        nativeChrome?.tintArgb = chromeTint
    }
    NativeDocumentActionEffect(
        NativeDocumentAction(
            symbol = "arrow.clockwise",
            label = CommonStr.update(),
            enabled = !isUpdating,
            busy = isUpdating,
            onClick = onForceUpdate,
        )
    )

    Scaffold(
        modifier = Modifier.consumeWindowInsets(PaddingValues(top = nativeBarInset))
    ) { paddingValues ->
        val topBarHeightDp = with(LocalDensity.current) {
            (scrollBehavior.currentHeightPx).toDp()
        }
        val statusBarHeight = WindowInsets.statusBars
            .asPaddingValues().calculateTopPadding()
        // iOS 上 statusBars 含原生导航栏，模糊带按纯状态栏算，否则多出一条导航栏高度
        val blurHeight = 80.dp +
            (statusBarHeight - nativeBarInset).coerceAtLeast(0.dp) + 120.dp
        // ⚠ 不再加 nativeBarInset：consumeWindowInsets 已把原生导航栏那截扣掉，再加会多出 54
        val contentTopInset = paddingValues.calculateTopPadding()

        var searchBackdropAlpha by remember { mutableFloatStateOf(0f) }
        var searchShadowAlpha by remember { mutableFloatStateOf(0f) }

        // 本页两层采样源（backdrop / liquidGlassBackdrop）录的是同一棵子树，
        // 变化因素一致，共用一份指纹与谓词。
        // 指纹：结构/内容变更（搜索词、tab、数据版本、更新中、主题底色）。
        // 谓词：顶栏折叠 offset 与搜索框淡入淡出在 draw 阶段读，不进组合；
        // 帧差检测，只有真在动的那几帧才重录。
        val eduRecordKey = remember(
            searchQuery, searchExpanded, selectedTab, dataVersion, isUpdating, backgroundColor
        ) {
            listOf(
                searchQuery, searchExpanded, selectedTab, dataVersion, isUpdating, backgroundColor
            )
        }
        val eduMustRecord = remember(scrollBehavior) {
            var lastOffset = Float.NaN
            var lastSearchAlpha = Float.NaN
            var lastShadowAlpha = Float.NaN
            {
                val offset = scrollBehavior.state.heightOffset
                val searchAlpha = searchBackdropAlpha
                val shadowAlpha = searchShadowAlpha
                val moving = offset != lastOffset ||
                    searchAlpha != lastSearchAlpha ||
                    shadowAlpha != lastShadowAlpha
                lastOffset = offset
                lastSearchAlpha = searchAlpha
                lastShadowAlpha = shadowAlpha
                moving
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // 无材质档：顶栏走渐变遮罩、玻璃全关，录屏层不挂
                        if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                        else Modifier.layerBackdrop(
                            backdrop,
                            recordKey = eduRecordKey,
                            mustRecord = eduMustRecord
                        )
                    )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().then(
                        if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                        else Modifier.layerBackdrop(
                            liquidGlassBackdrop,
                            recordKey = eduRecordKey,
                            mustRecord = eduMustRecord
                        )
                    )
                ) {
                    com.haooz.chedule.ui.screens.SchoolSelectionScreen(
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        isUpdating = isUpdating,
                        dataVersion = dataVersion,
                        isInFreeformWindow = isInFreeformWindow,
                        scrollBehavior = scrollBehavior,
                        searchQuery = searchQuery,
                        selectedTab = selectedTab,
                        topContentPadding = contentTopInset + topBarHeightDp + 100.dp,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        onAdapterSelected = { school, adapter ->
                            // 选中项经 EduImportSelection 带给独立路由（右侧推入），返回先回本页
                            EduImportSelection.set(school, adapter)
                            onOpenWebImport()
                        }
                    )
                }
            }

            // 本页唯一材质状态：按钮玻璃走全站同一判据（本页遮罩常驻，不接 maskAlpha）
            val material = rememberTopBarMaterialState(scrollBehavior)
            ProgressiveBlurTopBar(
                backdrop = liquidGlassBackdrop,
                height = blurHeight,
                topInsetAdjustment = nativeBarInset,
            ) {
                CollapsibleTopAppBar(
                    title = ImportStr.selectSchool(),
                    largeTitle = ImportStr.selectSchool(),
                    showLargeTitle = true,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    scrollBehavior = scrollBehavior,
                    materialState = material,
                    contentPadding = {},
                    onAlphaChanged = { bd, sh ->
                        searchBackdropAlpha = bd
                        searchShadowAlpha = sh
                    },
                    // 原生文档页（iOS）这两个按钮由 Swift 画（见上面的 effect）
                    startAction = if (showNavigationActions) {
                        { backdropAlpha, shadowAlpha ->
                            LiquidTopBarButton(
                                onClick = onFinish,
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.ChevronBackward,
                                contentDescription = CommonStr.back(),
                                performHapticFeedback = false,
                                iconSize = 25.dp,
                                iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        }
                    } else null,
                    endAction = if (showNavigationActions) {
                        { backdropAlpha, shadowAlpha ->
                            if (isUpdating) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = (-6).dp, y = (-4).dp)
                                        .size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            } else {
                                LiquidTopBarButton(
                                    onClick = onForceUpdate,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Normal.Update,
                                    contentDescription = CommonStr.update(),
                                    iconSize = 28.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            }
                        }
                    } else null,
                )
            }

            val searchIsLightTheme = !isAppDarkTheme()
            // 与所有文档页共用同一段「标题下方内容」定位（见 PageHeaderContent）
            PageHeaderContent(
                paddingValues = paddingValues,
                scrollBehavior = scrollBehavior,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                ),
            ) {
                SearchBar(
                    modifier = Modifier.padding(
                        top = 8.dp,
                        bottom = 6.dp,
                        start = 6.dp + tabletHorizontalPadding,
                        end = 6.dp + tabletHorizontalPadding
                    ),
                    inputField = {
                        InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { searchExpanded = false },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = ImportStr.searchSchool(),
                            backdrop = liquidGlassBackdrop,
                            backdropAlpha = searchBackdropAlpha,
                            shadowAlpha = searchShadowAlpha,
                            isLightTheme = { searchIsLightTheme },
                        )
                    },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    actionIcon = MiuixIcons.Normal.Close,
                    onActionClick = {
                        searchExpanded = false
                        searchQuery = ""
                    },
                    backdrop = liquidGlassBackdrop,
                    backdropAlpha = searchBackdropAlpha,
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp + tabletHorizontalPadding, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayTabs.forEachIndexed { index, tabName ->
                        val isSelected = selectedTab == index
                        Surface(
                            modifier = Modifier
                                .clip(ContinuousRoundedRectangle(20.dp))
                                .clickable {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    selectedTab = index
                                },
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp).height(35.dp).clip(ContinuousCapsule()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabName,
                                    fontSize = 14.sp,
                                    color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 应用教务脚本下发的预设时间段（JSON 存在 `edu_import_prefs`，导入完成后消费一次）。
 * 目标课表可能是非当前课表 —— 那时直接走 CourseRepository 写入，不动当前 UI 状态。
 */
internal fun applyPresetTimeSlots(
    settingsViewModel: SettingsViewModel,
    scheduleViewModel: ScheduleViewModel,
    targetScheduleId: String?
) {
    val prefs = AppStorage.store("edu_import_prefs")
    val timeSlotsJson = prefs.getStringOrNull("preset_time_slots") ?: return
    try {
        // Gson 运行时反射在 Kotlin/Native 不存在，改用 JsonElement 逐字段读（缺键/类型不对走默认值）。
        val timeSlots = parseJsonArray(timeSlotsJson).mapNotNull { element ->
            (element as? JsonObject)?.let { slot ->
                PresetTimeSlot(
                    number = slot.optInt("number", 0),
                    startTime = slot.optString("startTime"),
                    endTime = slot.optString("endTime"),
                )
            }
        }
        if (timeSlots.isEmpty()) return

        val isTargetCurrent = targetScheduleId.isNullOrEmpty() ||
            targetScheduleId == scheduleViewModel.currentScheduleName.value
        val repository = if (isTargetCurrent) null else CourseRepository()

        val morningSections: Int
        val afternoonSections: Int
        val eveningSections: Int
        if (isTargetCurrent) {
            morningSections = settingsViewModel.morningSections.value
            afternoonSections = settingsViewModel.afternoonSections.value
            eveningSections = settingsViewModel.eveningSections.value
        } else {
            val sid = targetScheduleId
            morningSections = repository!!.getMorningSections(sid)
            afternoonSections = repository.getAfternoonSections(sid)
            eveningSections = repository.getEveningSections(sid)
        }

        val morningTimes = mutableMapOf<Int, String>()
        val afternoonTimes = mutableMapOf<Int, String>()
        val eveningTimes = mutableMapOf<Int, String>()

        for (slot in timeSlots) {
            val number = slot.number
            val startTime = slot.startTime
            val endTime = slot.endTime
            if (number <= 0 || startTime.isEmpty() || endTime.isEmpty()) continue
            val timeStr = "$startTime-$endTime"

            when {
                number <= morningSections -> morningTimes[number] = timeStr
                number <= morningSections + afternoonSections -> afternoonTimes[number - morningSections] = timeStr
                else -> eveningTimes[number - morningSections - afternoonSections] = timeStr
            }
        }

        if (isTargetCurrent) {
            if (morningTimes.isNotEmpty()) settingsViewModel.saveMorningTimes(morningTimes)
            if (afternoonTimes.isNotEmpty()) settingsViewModel.saveAfternoonTimes(afternoonTimes)
            if (eveningTimes.isNotEmpty()) settingsViewModel.saveEveningTimes(eveningTimes)
            settingsViewModel.applyTimeImportToCurrentSchedule(
                morningSections, afternoonSections, eveningSections,
                morningTimes, afternoonTimes, eveningTimes
            )
        } else {
            val sid = targetScheduleId
            repository!!.applyTimeImportToSchedule(
                sid, morningSections, afternoonSections, eveningSections,
                morningTimes, afternoonTimes, eveningTimes
            )
        }

        prefs.edit { remove("preset_time_slots") }
    } catch (e: Exception) {
        NexioLog.e("EduImport", ImportStr.logApplyPresetFailed(e.message))
    }
}

/**
 * 应用教务脚本通过 saveCourseConfig 传回的课表配置（开学日 / 总周数 / 单节时长 / 周起始日）。
 * 一次性消费标记，读完整组清掉，防止残留旧值在下次导入时被误应用。
 */
internal fun applyImportedScheduleConfig(
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
) {
    val prefs = AppStorage.store("edu_import_prefs")
    val rawStart = prefs.getStringOrNull("semester_start_date")
    val startDate = CourseRepository.normalizeClassStartDate(rawStart)
    val totalWeeks = prefs.getInt("semester_total_weeks", -1)
    val targetScheduleId = prefs.getStringOrNull("target_schedule_id")
    val isTargetCurrent = targetScheduleId.isNullOrEmpty() ||
        targetScheduleId == scheduleViewModel.currentScheduleName.value
    val repository = CourseRepository.getInstance()

    var applied = false
    if (startDate != null) {
        if (isTargetCurrent) {
            courseViewModel.setClassStartTime(startDate)
        } else if (!targetScheduleId.isNullOrEmpty()) {
            // 只写目标课表存储，不 refreshEssentialData：当前 UI/提醒不应被非当前课表改动触发
            repository.setClassStartTime(targetScheduleId, startDate)
        }
        applied = true
        NexioLog.d("EduImport", ImportStr.logApplyStartDate(rawStart, startDate, targetScheduleId))
    } else if (!rawStart.isNullOrBlank()) {
        NexioLog.w("EduImport", ImportStr.logStartDateIgnored(rawStart))
    }
    if (totalWeeks > 0) {
        if (isTargetCurrent) {
            courseViewModel.setTotalWeeks(totalWeeks)
        } else if (!targetScheduleId.isNullOrEmpty()) {
            repository.setTotalWeeks(targetScheduleId, totalWeeks)
        }
        applied = true
    }

    // 桥接层已把 config 写入 prefs，这里落到目标课表 TimeConfig
    // （节数、节次时间、作息原样保留；无绑定配置时新建并绑定）。
    val classDuration = prefs.getInt("default_class_duration", -1)
    val breakDuration = prefs.getInt("default_break_duration", -1)
    if (classDuration > 0 || breakDuration > 0) {
        val targetId = if (isTargetCurrent) repository.getCurrentScheduleId() else targetScheduleId
        if (!targetId.isNullOrEmpty()) {
            repository.applyImportedDefaultDurationsToSchedule(
                scheduleId = targetId,
                classDuration = classDuration.takeIf { it > 0 } ?: 45,
                shortBreak = breakDuration.takeIf { it > 0 } ?: 10,
            )
            applied = true
            NexioLog.d("EduImport", ImportStr.logApplyDurations(classDuration, breakDuration, targetId))
        }
    }

    // 学校周起始日非周一时，其课程 day 编号相对该周起始，需换算回绝对星期，
    // 否则整表错位一天（App 表格固定周一起始）。
    val firstDayOfWeek = prefs.getInt("first_day_of_week", 1)
    if (firstDayOfWeek in 2..7) {
        val targetId = if (isTargetCurrent) repository.getCurrentScheduleId() else targetScheduleId
        if (!targetId.isNullOrEmpty()) {
            val courses = repository.getCoursesForSchedule(targetId)
            if (courses.isNotEmpty()) {
                val remapped = courses.map { course ->
                    val day = course.dayOfWeek
                    if (day in 1..7) {
                        val absolute = (day - 1 + (firstDayOfWeek - 1)) % 7 + 1
                        if (absolute != day) course.copy(dayOfWeek = absolute) else course
                    } else {
                        course
                    }
                }
                if (remapped.indices.any { remapped[it].dayOfWeek != courses[it].dayOfWeek }) {
                    if (isTargetCurrent) {
                        courseViewModel.replaceCourses(remapped)
                    } else {
                        scheduleViewModel.saveCoursesToSchedule(targetId, remapped)
                    }
                    NexioLog.d("EduImport", ImportStr.logNormalizeWeekdays(firstDayOfWeek, targetId))
                }
            }
            applied = true
        }
    }

    // config 是一次性消费标记：导入完成后整组清掉，避免残留旧值在下次导入时被误应用。
    prefs.edit {
        remove("semester_start_date")
        remove("semester_total_weeks")
        remove("default_class_duration")
        remove("default_break_duration")
        remove("first_day_of_week")
        remove("target_schedule_id")
    }
    if (applied) {
        NexioLog.d("EduImport", ImportStr.logApplyConfigSuccess(startDate, totalWeeks, targetScheduleId))
    }
}

/**
 * 应用教务脚本通过 saveComboSchedule 传回的组合作息方案（同一课表按日期区间切换作息）。
 * 时间槽是差量：只覆盖脚本提交的节次，其余沿用基础时间段，避免切换后未覆盖节次丢时间。
 */
internal fun applyComboSchedule(
    settingsViewModel: SettingsViewModel,
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    targetScheduleId: String?,
) {
    val prefs = AppStorage.store("edu_import_prefs")
    val comboJson = prefs.getStringOrNull("combo_schedule") ?: return
    try {
        // Gson 运行时反射换成 common JsonElement 的逐字段读取。
        val combo = parseJsonObject(comboJson)
        val comboName = combo.optString("name")
        val items = combo.optJsonArray("publicSchedules")?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val slots = item.optJsonArray("timeSlots")?.mapNotNull { slotElement ->
                val slot = slotElement as? JsonObject ?: return@mapNotNull null
                ComboTimeSlotImportModel(
                    number = slot.optInt("number", 0),
                    startTime = slot.optionalString("startTime"),
                    endTime = slot.optionalString("endTime"),
                )
            }.orEmpty()
            ComboScheduleImportItem(
                name = item.optString("name"),
                startDate = item.optionalString("startDate"),
                endDate = item.optionalString("endDate"),
                timeSlots = slots,
            )
        }.orEmpty()
        if (items.isEmpty()) {
            prefs.edit { remove("combo_schedule") }
            return
        }

        val isTargetCurrent = targetScheduleId.isNullOrEmpty() ||
            targetScheduleId == scheduleViewModel.currentScheduleName.value
        val repository = CourseRepository.getInstance()
        val targetId = if (isTargetCurrent) repository.getCurrentScheduleId() else targetScheduleId
        if (targetId.isNullOrEmpty()) {
            prefs.edit { remove("combo_schedule") }
            return
        }

        // 目标课表可能未绑定（脚本指定的表 / 历史脏数据）：用「可写」入口先补绑，
        // 否则下面的 saveTimeConfig 会把组合作息写进它回退到的那套配置
        val base = repository.getWritableTimeConfig(targetId)
        val morning = base.morningSections
        val afternoon = base.afternoonSections
        val evening = base.eveningSections

        var nextId = base.nextRoutineId()
        val routines = items.mapNotNull { item ->
            val start = item.startDate ?: return@mapNotNull null
            val month = start.substringBefore("-").toIntOrNull() ?: return@mapNotNull null
            val day = start.substringAfter("-").substringBefore("-").toIntOrNull() ?: return@mapNotNull null
            if (month !in 1..12 || day !in 1..31) return@mapNotNull null

            // 差量合并：以基础时间段为底，仅覆盖脚本提交的节次
            val merged = base.sectionTimes.toMutableMap()
            for (slot in item.timeSlots) {
                val number = slot.number
                if (number <= 0) continue
                val startTime = slot.startTime
                val endTime = slot.endTime
                if (startTime.isNullOrEmpty() || endTime.isNullOrEmpty()) continue
                val key = when {
                    number <= morning -> "morning_$number"
                    number <= morning + afternoon -> "afternoon_${number - morning}"
                    number <= morning + afternoon + evening -> "evening_${number - morning - afternoon}"
                    else -> continue
                }
                merged[key] = "$startTime-$endTime"
            }

            TimeRoutine(
                id = nextId++,
                name = item.name.ifBlank { comboName.ifBlank { ImportStr.defaultRoutineName() } },
                effectiveMonth = month,
                effectiveDay = TimeRoutine.clampDayOfMonth(month, day),
                // 时间字段以基础配置为准：方案只携带节次时间差量，其余沿用课表当前值
                quickTimeEnabled = base.quickTimeEnabled,
                classDuration = base.classDuration,
                shortBreak = base.shortBreak,
                longBreakEnabled = base.longBreakEnabled,
                longBreakMorning = base.longBreakMorning,
                longBreakAfternoon = base.longBreakAfternoon,
                longBreakEvening = base.longBreakEvening,
                longBreakMorningSection = base.longBreakMorningSection,
                longBreakAfternoonSection = base.longBreakAfternoonSection,
                longBreakEveningSection = base.longBreakEveningSection,
                morningStartHour = base.morningStartHour,
                morningStartMinute = base.morningStartMinute,
                afternoonStartHour = base.afternoonStartHour,
                afternoonStartMinute = base.afternoonStartMinute,
                eveningStartHour = base.eveningStartHour,
                eveningStartMinute = base.eveningStartMinute,
                sectionTimes = merged,
            )
        }
        if (routines.isEmpty()) {
            prefs.edit { remove("combo_schedule") }
            return
        }

        repository.saveTimeConfig(base.copy(routines = routines))
        if (isTargetCurrent) {
            settingsViewModel.refreshSettings()
            courseViewModel.refreshEssentialData()
        }
        prefs.edit { remove("combo_schedule") }
        NexioLog.d("EduImport", ImportStr.logApplyComboSuccess(routines.size, targetId))
    } catch (e: Exception) {
        NexioLog.e("EduImport", ImportStr.logApplyComboFailed(e.message))
    }
}

/** 预设时间段：消费 `preset_time_slots` 存的 JSON */
private data class PresetTimeSlot(
    val number: Int,
    val startTime: String,
    val endTime: String,
)

/** Preserve Gson's nullable String fields: absent / explicit JSON null → null; otherwise optString text. */
private fun JsonObject.optionalString(key: String): String? =
    if (hasKey(key) && !isJsonNull(key)) optString(key) else null

private data class ComboScheduleImportModel(
    val name: String = "",
    val publicSchedules: List<ComboScheduleImportItem> = emptyList()
)

private data class ComboScheduleImportItem(
    val name: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val timeSlots: List<ComboTimeSlotImportModel> = emptyList()
)

private data class ComboTimeSlotImportModel(
    val number: Int,
    val startTime: String? = null,
    val endTime: String? = null
)

/**
 * 教务脚本索引的后台更新器。⚠ 逻辑与迁移前逐字保留，勿改。
 * - [startUpdateIfNeeded]：进入页面时调用，距上次成功更新 ≥7 天才动
 * - [forceUpdate]：顶栏「更新」按钮，结果 Toast 给用户
 */
object EduImportUpdater {

    private const val TAG = "EduImport"
    private const val PREFS_NAME = "edu_import_prefs"
    private const val KEY_LAST_UPDATE_TIME = "last_update_time"
    private const val AUTO_UPDATE_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L

    private val updateScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    val isUpdating = MutableStateFlow(false)
    val dataVersion = MutableStateFlow(0)

    fun startUpdateIfNeeded() {
        if (isUpdating.value) return

        val prefs = AppStorage.store(PREFS_NAME)
        val lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIME, 0)
        val now = Clock.System.now().toEpochMilliseconds()
        val needsUpdate = lastUpdateTime == 0L || (now - lastUpdateTime > AUTO_UPDATE_INTERVAL_MS)

        if (!needsUpdate) {
            return
        }

        isUpdating.value = true
        updateScope.launch {
            try {
                ScriptRepository(ScriptRepository.getRepoUrl()).updateAll(
                    onLog = { }
                )
                prefs.edit { putLong(KEY_LAST_UPDATE_TIME, Clock.System.now().toEpochMilliseconds()) }
            } catch (e: Exception) {
                NexioLog.e(TAG, ImportStr.logUpdateFailed(e.message))
            } finally {
                isUpdating.value = false
                dataVersion.value++
            }
        }
    }

    fun forceUpdate() {
        if (isUpdating.value) return
        isUpdating.value = true
        updateScope.launch {
            try {
                val result = ScriptRepository(ScriptRepository.getRepoUrl()).updateAll(
                    onLog = { }
                )
                val msg = when (result) {
                    0 -> ImportStr.alreadyLatest()
                    1 -> ImportStr.updateComplete()
                    -1 -> ImportStr.updateFailedNoNetwork()
                    else -> ImportStr.updateFailed()
                }
                // Toast 必须回主线程（Main 是 kotlinx-coroutines 的跨平台 dispatcher）。⚠ 与迁移前逐字一致，勿改。
                withContext(Dispatchers.Main) { showToast(msg) }
                if (result >= 0) {
                    val prefs = AppStorage.store(PREFS_NAME)
                    prefs.edit { putLong(KEY_LAST_UPDATE_TIME, Clock.System.now().toEpochMilliseconds()) }
                }
            } catch (e: Exception) {
                NexioLog.e(TAG, ImportStr.logManualUpdateFailed(e.message))
                withContext(Dispatchers.Main) { showToast(ImportStr.updateFailed()) }
            } finally {
                isUpdating.value = false
                dataVersion.value++
            }
        }
    }
}
