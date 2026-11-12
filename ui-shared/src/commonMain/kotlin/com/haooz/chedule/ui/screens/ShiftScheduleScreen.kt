/** 排班课表页面 */
package com.haooz.chedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.holiday.TeachingWeekPosition
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.SwitchStr
import com.haooz.chedule.ui.activities.classschedule.SchedulePageChrome
import com.haooz.chedule.ui.activities.classschedule.ScheduleTopBar
import com.haooz.chedule.ui.activities.classschedule.SectionColumn
import com.haooz.chedule.ui.activities.main.MorePopupMenus
import com.haooz.chedule.ui.activities.main.PageLockedTopBarTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberCombinedBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.chedule.ui.components.HorizontalPageSwitch
import com.haooz.chedule.ui.components.LocalPageStartPadding
import com.haooz.chedule.ui.components.PageSwitchState
import com.haooz.chedule.ui.components.ShiftDayColumn
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.schedulePageBackgroundColor
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import com.haooz.chedule.ui.components.sheetContentBottomInset
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalBlurBottomSheetContentExpanded
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

internal data class ShiftSchedulePageDayRangeKey(
    val week: Int,
    val smartWeekend: Boolean,
    val scheduleDataVersion: Int,
)

@Composable
fun ShiftScheduleScreen(
    shiftViewModel: ShiftViewModel,
    settingsViewModel: com.haooz.chedule.viewmodel.SettingsViewModel,
    /** 周页横向位置。 */
    pageState: PageSwitchState,
    /** 总周数。 */
    pageCount: Int,
    scheduleDataVersion: Int = 0,
    cardHeightPerSection: Float = 54f,
    scheduleScrollBehavior: SharedScrollBehavior? = null,
    /** 是否自由形态窗口（小米平板分屏/PC 模式）；Android 宿主注入，桌面/iOS 恒 false */
    isInFreeformWindow: Boolean = false,
    /** 自由形态窗口的标题栏高度（isInFreeformWindow=true 时用于顶距计算）；默认 56.dp */
    titleBarHeight: androidx.compose.ui.unit.Dp = 56.dp,
    /** 顶栏玻璃采样源；null 时本页不画顶栏 */
    liquidGlassBackdrop: GlassBackdrop? = null,
    /** 顶栏宿主数据；null 时本页不画顶栏 */
    chrome: SchedulePageChrome? = null,
) {
    val shiftScheduleCourses by shiftViewModel.shiftScheduleCourses.collectAsState()
    val shiftScheduleSections by shiftViewModel.shiftScheduleSections.collectAsState()
    val repository = remember { CourseRepository.getInstance() }
    val hasReorganizedComparison = remember(shiftScheduleCourses.keys, scheduleDataVersion) {
        (shiftScheduleCourses.keys + repository.getCurrentScheduleId()).any { name ->
            repository.getTeachingWeekReorganizations(name).isNotEmpty()
        }
    }
    val isDark = isAppDarkTheme()
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isTablet = isTabletWidth()
    val nativeChromeInset = LocalHostNavigation.current
        ?.takeIf { it.showsNativeTabBar && !isTablet }
        ?.bottomBarInset?.toFloat()?.dp
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()

    var showDetail by remember { mutableStateOf(false) }
    var detailCourses by remember { mutableStateOf<List<Pair<String, Course>>>(emptyList()) }

    val maxMorning = remember(shiftScheduleSections) {
        shiftScheduleSections.values.maxOfOrNull { it.first } ?: 4
    }
    val maxAfternoon = remember(shiftScheduleSections) {
        shiftScheduleSections.values.maxOfOrNull { it.second } ?: 4
    }
    val maxEvening = remember(shiftScheduleSections) {
        shiftScheduleSections.values.maxOfOrNull { it.third } ?: 4
    }
    val totalSections = maxMorning + maxAfternoon + maxEvening

    val wallpaperBackdropColor = schedulePageBackgroundColor(isDark)

    // 内容单独录一层（页面坐标）：顶栏/菜单在层外，采样「壁纸 + 本页内容」，
    // 不会把自己的模糊带/按钮再录进去形成循环采样。
    val contentBackdrop = chrome?.contentBackdrop ?: rememberLayerBackdrop()
    val glassLayersEnabled =
        !com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier() && chrome != null
    val wallpaperBackdrop = chrome?.wallpaperBackdrop
    val chromeBackdrop: GlassBackdrop = if (wallpaperBackdrop != null) {
        rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop)
    } else {
        contentBackdrop
    }
    val moreSlotTopPx = remember { mutableFloatStateOf(0f) }
    val moreMaterialAlpha = remember { mutableFloatStateOf(0f) }
    val pageRootY = remember { mutableFloatStateOf(0f) }
    // 顶栏单独录一层：展开的「更多」菜单采「本页内容 + 本页顶栏」，不会采到自己
    val topBarBackdrop = rememberLayerBackdrop()
    val menuBackdrop = if (wallpaperBackdrop != null) {
        rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop, topBarBackdrop)
    } else {
        rememberCombinedBackdrop(contentBackdrop, topBarBackdrop)
    }
    // 这层只为「更多」菜单服务：菜单收起且展开动画归零就摘掉，不让顶栏每帧白录一层
    val topBarLayerWanted by remember(chrome?.moreMenu) {
        derivedStateOf {
            val menu = chrome?.moreMenu
            menu != null && (menu.show || menu.fraction.value > 0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(wallpaperBackdropColor)
            .then(
                if (glassLayersEnabled) {
                    Modifier.layerBackdrop(
                        contentBackdrop,
                        chrome?.contentRecordKey,
                        chrome?.contentMustRecord,
                    )
                } else {
                    Modifier
                }
            )
            .onGloballyPositioned { pageRootY.floatValue = it.positionInRoot().y }
    ) {

    HorizontalPageSwitch(
        state = pageState,
        pageCount = pageCount,
        modifier = Modifier.fillMaxSize(),
        beyondPageCount = 1,
    ) { page ->
        val week = page + 1
        val scrollState = rememberScrollState()
        val nativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp
        val statusBarHeight = (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() - nativeBarInset).coerceAtLeast(0.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                // 平板侧栏避让：与普通课表页一致，pager 容器保持全屏，每页自己留白
                .then(LocalPageStartPadding.current)
                .then(
                    if (scheduleScrollBehavior != null) Modifier.nestedScroll(scheduleScrollBehavior.nestedScrollConnection)
                    else Modifier
                )
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .verticalScroll(scrollState)
                .padding(
                    top = statusBarHeight +
                        if (isInFreeformWindow) titleBarHeight + 40.dp else 96.dp,
                    bottom = nativeChromeInset?.plus(24.dp) ?: 140.dp
                )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isTablet) Modifier.padding(horizontal = 24.dp) else Modifier
                        )
                ) {
                    SectionColumn(
                        totalSections = totalSections,
                        morningSections = maxMorning,
                        afternoonSections = maxAfternoon,
                        eveningSections = maxEvening,
                        sectionTimes = sectionTimes,
                        cardHeightPerSection = cardHeightPerSection,
                        isTablet = isTablet
                    )

                    val pageDayRange = remember(
                        ShiftSchedulePageDayRangeKey(week, smartWeekend, scheduleDataVersion),
                    ) {
                        (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(week).filter { it in 6..7 }
                    }

                    pageDayRange.forEach { dayOfWeek ->
                        val actualDate = remember(week, dayOfWeek, scheduleDataVersion) {
                            repository.dateForTeachingWeekDay(week, dayOfWeek)
                        }
                        val schedulePositions = remember(actualDate, shiftScheduleCourses, scheduleDataVersion, hasReorganizedComparison) {
                            if (!hasReorganizedComparison) {
                                shiftScheduleCourses.keys.associateWith { TeachingWeekPosition(week.toLong(), dayOfWeek) }
                            } else actualDate?.let { date ->
                                shiftScheduleCourses.keys.associateWith { name ->
                                    repository.teachingWeekPositionForDate(date, name)
                                }
                            }.orEmpty()
                        }
                        ShiftDayColumn(
                            dayOfWeek = dayOfWeek,
                            allScheduleCourses = shiftScheduleCourses,
                            morningSections = maxMorning,
                            afternoonSections = maxAfternoon,
                            eveningSections = maxEvening,
                            schedulePositions = schedulePositions,
                            onSlotClick = { _, _, courses ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                detailCourses = courses
                                showDetail = true
                            },
                            cardHeightPerSection = cardHeightPerSection,
                            isTablet = isTablet,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val morningHeight = (maxMorning * cardHeightPerSection).toInt()
                val afternoonHeight = (maxAfternoon * cardHeightPerSection).toInt()
                val dinnerBreakY = morningHeight + 24 + afternoonHeight

                val dividerShape = ContinuousRoundedRectangle(12.dp)
                val dividerHorizontalPadding = if (isTablet) 24.dp else 4.dp
                val dividerBaseColor = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh
                    else if (isDark) Color(0xFF121212) else Color(0xFFF0F0F0)

                @Composable
                fun BreakDivider(offsetY: Int, text: String) {
                    Box(
                        modifier = Modifier.fillMaxWidth().offset(y = offsetY.dp)
                            .height(24.dp)
                            .padding(vertical = 2.dp)
                            .padding(horizontal = dividerHorizontalPadding)
                            .background(dividerBaseColor, dividerShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                BreakDivider(morningHeight, SwitchStr.lunchBreak())
                BreakDivider(dinnerBreakY, SwitchStr.dinnerBreak())
            }
        }
    }

    } // wallpaper Box


    val detailContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .then(
                    if (LocalBlurBottomSheetContentExpanded.current) Modifier.fillMaxHeight()
                    else Modifier,
                )
                .overScrollVertical()
                .scrollEndHaptic(
                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                )
                .verticalScroll(rememberScrollState())
                .sheetContentBottomInset(isTablet),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(if (isTablet) 56.dp else 58.dp))
            detailCourses.forEach { (scheduleName, course) ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    insideMargin = PaddingValues(0.dp),
                    pressFeedbackType = PressFeedbackType.None,
                    showIndication = true,
                    colors = CardDefaults.defaultColors(
                        color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh else if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = scheduleName,
                            style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${course.name}  ${course.timeDisplayTextLocalized()}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
        }
    }

    // 顶栏与页面同层：翻周 / 切 tab 时跟着页面一起位移，不再由外层按页码算偏移
    chrome?.let { c ->
        PageLockedTopBarTheme(c.forcedDarkTheme) {
            Box(Modifier.then(
                if (glassLayersEnabled && topBarLayerWanted) Modifier.layerBackdrop(topBarBackdrop) else Modifier
            )) {
            ScheduleTopBar(
                visible = true,
                pagerCurrentPage = pageState.currentPage,
                currentWeek = c.currentWeek,
                isHoliday = c.isHoliday,
                dayRange = c.dayRange,
                currentDayOfWeek = c.currentDayOfWeek,
                isCurrentWeek = pageState.currentPage + 1 == c.currentWeek &&
                    c.currentWeek in 1..c.totalWeeks,
                weekDates = c.weekDates,
                isReorganized = c.isReorganized,
                onOpenSwitchSchedule = c.onOpenSwitchSchedule,
                onJumpWeek = c.onJumpWeek,
                onEnterCustomize = c.onEnterCustomize,
                onCourseManage = c.onCourseManage,
                isTablet = isTablet,
                isShiftMode = c.isShiftMode,
                liquidGlassBackdrop = chromeBackdrop,
                scrollBehavior = scheduleScrollBehavior,
                blurResampleKey = c.blurResampleKey,
                blurSampleTrack = c.blurSampleTrack,
                titleEndPadding = c.titleEndPadding,
                onMoreSlotTop = { moreSlotTopPx.floatValue = it - pageRootY.floatValue },
                onMoreMaterial = { moreMaterialAlpha.floatValue = it },
                onMoreExpand = { c.moreMenu?.onExpand?.invoke() },
            )
            }
            c.moreMenu?.let { menu ->
                MorePopupMenus(
                    showMorePopup = menu.show,
                    onMorePopupDismiss = menu.onDismiss,
                    onMorePopupExpand = menu.onExpand,
                    showTodayMorePopup = false,
                    onTodayMorePopupDismiss = {},
                    morePopupFraction = menu.fraction,
                    todayMorePopupFraction = remember {
                        androidx.compose.animation.core.Animatable(0f)
                    },
                    scheduleMenuOffset = { Offset.Zero },
                    moreSlotTopPx = moreSlotTopPx,
                    scheduleMaterialAlpha = moreMaterialAlpha.floatValue,
                    liquidGlassBackdrop = menuBackdrop,
                    isShiftMode = c.isShiftMode,
                    enabled = menu.enabled,
                    scheduleMoreNative = menu.native,
                    todayMoreNative = true,
                    onJumpWeek = c.onJumpWeek,
                    onCourseManage = c.onCourseManage,
                    onEnterCustomize = c.onEnterCustomize,
                    onJumpToDate = {},
                )
            }
        }
    }

    if (isTablet) {
        BlurBottomSheetTablet(
            show = showDetail,
            title = SwitchStr.scheduleDetail(),
            dimBackground = true,
            isBottomAligned = true,
            onDismissRequest = { showDetail = false },
            content = detailContent
        )
    } else {
        BlurBottomSheet(
            show = showDetail,
            title = SwitchStr.scheduleDetail(),
            dimBackground = true,
            onDismissRequest = { showDetail = false },
            content = detailContent
        )
    }
}
