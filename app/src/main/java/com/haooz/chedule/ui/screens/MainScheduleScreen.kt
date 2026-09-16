package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.components.DayColumn
import com.haooz.chedule.ui.components.SectionColumn
import com.haooz.chedule.ui.components.SpecialBandClickLayer
import com.haooz.chedule.ui.components.SpecialBandOverlay
import com.haooz.chedule.ui.components.computeSpecialGridLayout
import com.haooz.chedule.ui.components.scheduleContentTopPadding
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberCourseCardEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantLayerBackdrop

// 普通 holder 而非 state：避免 onGloballyPositioned 每次写入触发卡片重组
private class CardBoundsHolder {
    var rect: Rect? = null
}

// 刻意不用 snapshot state：滑动中坐标用不上，state 会带着几十张卡一起重组
class GridScrollFlag {
    var scrolling: Boolean = false
}

private fun expandEntryByDate(
    entry: HolidayManager.Entry,
    put: (String, HolidayManager.Entry) -> Unit
) {
    if (entry.endDate.isBlank()) {
        put(entry.date, entry)
    } else {
        runCatching {
            var d = LocalDate.parse(entry.date)
            val end = LocalDate.parse(entry.endDate)
            while (!d.isAfter(end)) {
                put(d.toString(), entry)
                d = d.plusDays(1)
            }
        }.onFailure {
            put(entry.date, entry)
        }
    }
}

data class ScheduleGridGeometry(
    val dayBounds: Map<Int, FloatArray>,
    val sectionHeightPx: Float,
    val morningSections: Int,
    val afternoonSections: Int,
    val eveningSections: Int,
    val showBreakDividers: Boolean
)

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun MainScheduleScreen(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    pagerState: PagerState,
    hiddenCourseIds: Set<String> = emptySet(),
    draggingCourseIds: Set<String> = emptySet(),
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: android.graphics.Bitmap?, courseIdToHide: String, targetWeek: Int) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onPopupStateChange: (Boolean) -> Unit = {},
    // 空白格长按：返回 Root 绝对坐标供上层定位快捷菜单
    onEmptyLongPress: (day: Int, section: Int, centerX: Float, cellTopY: Float, width: Float, height: Float) -> Unit = { _, _, _, _, _, _ -> },
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, backdrop: com.kyant.backdrop.Backdrop?, currentWeek: Int) -> Unit = { _, _, _, _, _, _, _ -> },
    onCourseDragStart: (courseId: String) -> Unit = { _ -> },
    onCourseDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onCourseDragEnd: (courseId: String) -> Unit = { _ -> },
    onCourseMenuDismiss: () -> Unit = {},
    wallpaperBitmap: android.graphics.Bitmap? = null,
    wallpaperOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    wallpaperScale: Float = 1f,
    // true：壁纸由主 pager 后共享层绘制，本页透明叠上，切 tab 时不随页平移
    useSharedWallpaper: Boolean = false,
    // 共享壁纸层 backdrop，供卡片玻璃采样（useSharedWallpaper 时必传 LayerBackdrop）
    sharedWallpaperBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    isWallpaperEditing: Boolean = false,
    onWallpaperOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onWallpaperScaleChange: (Float) -> Unit = {},
    appearance: com.haooz.chedule.data.AppearanceConfig = com.haooz.chedule.data.AppearanceConfig(),
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    // 拖拽落点高亮：Pair(dayOfWeek, sectionRange)，sectionRange 为落点覆盖的节次区间
    dropHighlight: Pair<Int, IntRange>? = null,
    onGridGeometryChange: (ScheduleGridGeometry) -> Unit = {},
    scheduleScrollBehavior: SharedScrollBehavior? = null,
    paddingValues: PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
    // Activity 层提升，return@Scaffold 不会销毁
    externalScrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    externalShowCourseDetail: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false),
    externalSelectedCourse: androidx.compose.runtime.MutableState<Course?> = mutableStateOf(null),
    externalSelectedCourses: androidx.compose.runtime.MutableState<List<Course>> = mutableStateOf(emptyList()),
) {
    // 解构外观配置
    val cardBlurRadius = appearance.cardBlurRadius
    val cardAlpha = appearance.cardAlpha
    val cardSurfaceAlpha = appearance.cardSurfaceAlpha
    val cardHeightPerSection = appearance.cardHeight
    val cardCornerRadius = appearance.cardCornerRadius
    val wallpaperBrightness = appearance.wallpaperBrightness
    val showBreakDividers = appearance.showBreakDividers
    val cardContentAlignment = appearance.cardContentAlignment
    val cardTextColor = appearance.cardTextColor
    val cardTextScale = appearance.cardTextScale
    val showClassroom = appearance.showClassroom
    val showTeacher = appearance.showTeacher
    val cardRefraction = appearance.cardRefraction
    val wallpaperBlur = appearance.wallpaperBlur

    val courses by viewModel.courses.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val dataVersion by viewModel.dataVersion.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val sectionNames by settingsViewModel.sectionNames.collectAsState()
    val specialBlocks by settingsViewModel.specialBlocks.collectAsState()
    // 状态提升到页面顶层：弹窗在顶层作用域渲染，点击横带只写这些状态
    var showSpecialItemDialog by remember { mutableStateOf(false) }
    var specialItemEditingBlockId by remember { mutableLongStateOf(0L) }
    var specialItemEditingId by remember { mutableLongStateOf(-1L) } // -1 表示新增
    var specialItemName by remember { mutableStateOf("") }
    // 支持不连续点选，保存时自动合并为连续区间
    var specialItemSelectedDays by remember { mutableStateOf(setOf<Int>()) }
    val hapticFeedback = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val scrollState = externalScrollState
    // 非 state 版本：卡片 onGloballyPositioned 读取不触发重组
    val gridScrollFlag = remember { GridScrollFlag() }
    // 供停滑后冲刷 dayBounds 版本号；仅滑动起停各写一次
    val scheduleScrollInProgress = remember { mutableStateOf(false) }
    LaunchedEffect(pagerState, scrollState) {
        // 必须直接读 ScrollState：捕获 composition 期 Boolean 后 snapshotFlow 不会再观测变化
        snapshotFlow {
            pagerState.isScrollInProgress || scrollState.isScrollInProgress
        }.collect {
            gridScrollFlag.scrolling = it
            scheduleScrollInProgress.value = it
        }
    }
    // 由顶栏自身几何纯计算（切页不变），避免 paddingValues 随 tab 变化导致位移
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val contentTopPaddingDp = remember(statusBarHeight) { scheduleContentTopPadding(statusBarHeight) }

    // 填满短边不露底；最小缩放 = cover/fit
    val minWallpaperScale = remember(wallpaperBitmap, screenWidthPx, screenHeightPx) {
        if (wallpaperBitmap != null && wallpaperBitmap.width > 0 && wallpaperBitmap.height > 0) {
            val fitScale = minOf(screenWidthPx / wallpaperBitmap.width, screenHeightPx / wallpaperBitmap.height)
            val coverScale = maxOf(screenWidthPx / wallpaperBitmap.width, screenHeightPx / wallpaperBitmap.height)
            if (fitScale > 0f) coverScale / fitScale else 1f
        } else 1f
    }

    var showCourseDetail by externalShowCourseDetail
    // backdrop 在弹窗作用域读 LocalSheetContentBackdrop，不提升到本页 State
    var selectedCourse by externalSelectedCourse
    var selectedCourses by externalSelectedCourses
    var pendingDay by remember { mutableIntStateOf(-1) }
    var pendingSection by remember { mutableIntStateOf(-1) }
    var viewingWeek by remember { mutableIntStateOf(currentWeek) }

    // 从详情页返回重建时弹窗已打开，跳过 reveal 重播
    var skipSheetReveal by remember { mutableStateOf(showCourseDetail) }
    LaunchedEffect(showCourseDetail) {
        if (!showCourseDetail) {
            skipSheetReveal = false
        }
    }

    LaunchedEffect(showAddDialog) {
        if (showAddDialog && pendingDay != -1) {
            kotlinx.coroutines.delay(300.milliseconds)
            pendingDay = -1
            pendingSection = -1
        }
    }

    val totalSections = morningSections + afternoonSections + eveningSections

    val specialGrid = remember(
        totalSections, morningSections, afternoonSections, eveningSections,
        specialBlocks, sectionTimes, cardHeightPerSection, showBreakDividers
    ) {
        computeSpecialGridLayout(
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            specialBlocks = specialBlocks,
            sectionTimes = sectionTimes,
            cardHeightPerSection = cardHeightPerSection,
            dividerGap = if (showBreakDividers) 24 else 0
        )
    }

    // 睡到下一次节次边界再重算，避免长时间停留后高亮过期
    var currentSection by remember { mutableIntStateOf(-1) }
    LaunchedEffect(sectionTimes, totalSections) {
        while (true) {
            val now = java.time.LocalTime.now()
            val currentMinutes = now.hour * 60 + now.minute
            var result = -1
            var nextTransition = Int.MAX_VALUE
            for (section in 1..totalSections) {
                val timeStr = sectionTimes[section] ?: ""
                if (timeStr.isEmpty()) continue
                val parts = timeStr.split("-")
                if (parts.size != 2) continue
                val startParts = parts[0].split(":")
                val endParts = parts[1].split(":")
                if (startParts.size != 2 || endParts.size != 2) continue
                val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
                val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
                if (currentMinutes in startMinutes until endMinutes) {
                    result = section
                }
                if (endMinutes > currentMinutes && endMinutes < nextTransition) {
                    nextTransition = endMinutes
                }
                if (startMinutes > currentMinutes && startMinutes < nextTransition) {
                    nextTransition = startMinutes
                }
            }
            if (currentSection != result) currentSection = result
            val sleepMinutes = if (nextTransition == Int.MAX_VALUE) {
                1
            } else {
                (nextTransition - currentMinutes).coerceIn(1, 60)
            }
            delay(sleepMinutes * 60_000L)
        }
    }

    // snapshotFlow 避免 LaunchedEffect(currentPage) 整页重组
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewingWeek = page + 1
        }
    }

    val allDays = (1..7).toList()
    val coursesByDay = remember(courses) {
        allDays.associateWith { dayOfWeek ->
            courses.filter { it.dayOfWeek == dayOfWeek }
                .sortedBy { it.startSection }
        }
    }

    val scheduleContext = LocalContext.current
    val semesterStartMonday = remember(scheduleContext, dataVersion) {
        val start = runCatching {
            LocalDate.parse(
                CourseRepository.getInstance(scheduleContext).getClassStartTime().replace("/", "-")
            )
        }.getOrNull() ?: LocalDate.now()
        start.minusDays((start.dayOfWeek.value - 1).toLong())
    }

    // 记忆化版本号：假期编辑返回 bump dataVersion 时才重读 SP
    val holidayVersion = remember(scheduleContext, dataVersion) {
        HolidayManager.getVersion(scheduleContext)
    }
    val holidayEntries = remember(
        scheduleContext, dataVersion, holidayVersion, semesterStartMonday, totalWeeks
    ) {
        val lastDate = semesterStartMonday.plusWeeks((totalWeeks - 1).toLong()).plusDays(6)
        (semesterStartMonday.year..lastDate.year).flatMap { year ->
            HolidayManager.load(scheduleContext, year)
        }
    }

    // O(1) 查表替代线性扫；跨日期条目展开；与调休索引分开避免同日互相覆盖
    val holidayIndex: Map<String, HolidayManager.Entry> = remember(holidayEntries) {
        if (holidayEntries.isEmpty()) emptyMap()
        else HashMap<String, HolidayManager.Entry>(holidayEntries.size * 3).apply {
            holidayEntries.forEach { entry ->
                if (entry.type == HolidayManager.TYPE_HOLIDAY) {
                    expandEntryByDate(entry) { date, e -> put(date, e) }
                }
            }
        }
    }
    // O(1) 查表替代线性扫
    val workswapIndex: Map<String, HolidayManager.Entry> = remember(holidayEntries) {
        if (holidayEntries.isEmpty()) emptyMap()
        else HashMap<String, HolidayManager.Entry>(holidayEntries.size * 3).apply {
            holidayEntries.forEach { entry ->
                if (entry.type == HolidayManager.TYPE_WORKSWAP) {
                    expandEntryByDate(entry) { date, e -> put(date, e) }
                }
            }
        }
    }

    // 一次算齐全部周，切页 O(1) 查表；智能周末下避免每次换周扫 courses+SP
    val weekendDaysByWeek: Map<Int, Set<Int>> = remember(
        courses, dataVersion, holidayVersion, smartWeekend, totalWeeks,
        semesterStartMonday, workswapIndex
    ) {
        if (!smartWeekend) {
            (1..totalWeeks).associateWith { setOf(6, 7) }
        } else {
            val result = HashMap<Int, Set<Int>>(totalWeeks * 2)
            for (week in 1..totalWeeks) {
                val mondayOfWeek = semesterStartMonday.plusWeeks((week - 1).toLong())
                val satDate = mondayOfWeek.plusDays(5).toString()
                val sunDate = mondayOfWeek.plusDays(6).toString()
                val satActive = courses.any { it.dayOfWeek == 6 && it.isActiveInWeek(week) } ||
                    (workswapIndex[satDate]?.followWeekday?.let { it in 1..7 } == true)
                val sunActive = courses.any { it.dayOfWeek == 7 && it.isActiveInWeek(week) } ||
                    (workswapIndex[sunDate]?.followWeekday?.let { it in 1..7 } == true)
                result[week] = buildSet {
                    if (satActive) add(6)
                    if (sunActive) add(7)
                }
            }
            result
        }
    }

    // 仅在 pager 内部访问时计算；值：dayOfWeek -> (displayWeek, 该日课程)
    @Suppress("RedundantInitializer")
    val filteredCoursesCache = remember(
        coursesByDay, showNonCurrentWeek, dataVersion, holidayVersion, workswapIndex
    ) {
        mutableMapOf<Int, Map<Int, Pair<Int, List<Course>>>>()
    }

    @Suppress("RedundantInitializer")
    val onPendingChange: (Int, Int) -> Unit = remember {
        { day, section ->
            pendingDay = day
            pendingSection = section
        }
    }

    // 顶层读一次主题，避免每列/分界带各自挂 prefs 监听
    val scheduleIsDark = isAppDarkTheme()
    val wallpaperBackdropColor = if (scheduleIsDark) Color(0xFF000000) else Color(0xFFF7F7F7)

    // onDraw 必须稳定：每次新建会换掉 LayerBackdrop 实例，SharedBlur 与全部课卡采样跟着重建
    val wallpaperBackColorState = rememberUpdatedState(wallpaperBackdropColor)
    val wallpaperOnDraw: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit =
        remember {
            {
                drawRect(wallpaperBackColorState.value)
                drawContent()
            }
        }

    // key 含 wallpaperBitmap：壁纸变化时强制重建并重录
    // 共享壁纸时直接用主层 backdrop，卡片才能采到真实壁纸像素
    val localCourseCardBackdrop = key(wallpaperBitmap) {
        rememberKyantLayerBackdrop(onDraw = wallpaperOnDraw)
    }
    val courseCardBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop =
        if (useSharedWallpaper && sharedWallpaperBackdrop != null) sharedWallpaperBackdrop
        else localCourseCardBackdrop

    // 仅壁纸路径需要共享模糊层
    val sharedBlurManager = if (wallpaperBitmap != null) {
        remember(courseCardBackdrop) { SharedBlurBackdrop(courseCardBackdrop) }
    } else null
    // blur=0 也走共享层：省掉每卡独立重录；滚动 LOD 时可直接 blit 共享采样
    val hasSharedBlur = sharedBlurManager != null && isRenderEffectSupported()
    val activeCardBackdrop: com.kyant.backdrop.Backdrop? = when {
        wallpaperBitmap == null -> null
        hasSharedBlur -> sharedBlurManager
        else -> courseCardBackdrop
    }

    DisposableEffect(sharedBlurManager) {
        onDispose { sharedBlurManager?.release() }
    }

    // 非 state：滚动不触发重组
    val scheduleViewport = remember { com.kyant.backdrop.BackdropViewport() }

    // 这些量不变时跳过壁纸录制与共享模糊；必须覆盖所有影响壁纸层内容的因素
    val wallpaperRecordKey = listOf(
        wallpaperBitmap,
        maxOf(wallpaperScale, minWallpaperScale),
        wallpaperOffset.x,
        wallpaperOffset.y,
        wallpaperBrightness,
        wallpaperBlur,
        wallpaperBackdropColor
    )

    androidx.compose.runtime.CompositionLocalProvider(
        com.kyant.backdrop.LocalBackdropViewport provides scheduleViewport
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (wallpaperBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // 共享层已录制；本页不再录，避免用透明内容覆盖共享 backdrop
                        if (useSharedWallpaper) Modifier
                        else Modifier.kyantLayerBackdrop(courseCardBackdrop, wallpaperRecordKey)
                    )
            ) {
                val brightnessFilter = remember(wallpaperBrightness) {
                    if (wallpaperBrightness != 0f) {
                        val b = (1f + wallpaperBrightness / 50f).coerceIn(0f, 2f)
                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(
                                floatArrayOf(
                                    b, 0f, 0f, 0f, 0f,
                                    0f, b, 0f, 0f, 0f,
                                    0f, 0f, b, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                        )
                    } else null
                }
                val imageBitmap = remember(wallpaperBitmap) { wallpaperBitmap.asImageBitmap() }
                val wallpaperBlurEffect = remember(wallpaperBlur) {
                    if (wallpaperBlur) {
                        val blurRadiusPx = 12f * density.density
                        android.graphics.RenderEffect.createBlurEffect(
                            blurRadiusPx,
                            blurRadiusPx,
                            android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    } else null
                }
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val effectiveScale = maxOf(wallpaperScale, minWallpaperScale)
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                            // 共享层负责显示；这里只录 backdrop，保持不可见
                            alpha = if (useSharedWallpaper) 0f else 1f
                            translationX = wallpaperOffset.x
                            translationY = wallpaperOffset.y
                            renderEffect = wallpaperBlurEffect
                        },
                    contentScale = ContentScale.Fit,
                    colorFilter = brightnessFilter
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .kyantLayerBackdrop(courseCardBackdrop, wallpaperRecordKey)
                    .background(wallpaperBackdropColor)
            )
        }

        // 不可见预渲染：壁纸录制到降采样+模糊层，供所有课卡共享采样
        if (hasSharedBlur) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0f }
                    .then(sharedBlurManager.preRenderModifier(
                        blurRadiusPx = with(density) { cardBlurRadius.dp.toPx() },
                        downsampleScale = 0.48f,
                        sourceKey = wallpaperRecordKey
                    ))
            )
        }

        // rememberUpdatedState：pointerInput(Unit) 读到最新值，避免捕获陈旧状态
        val latestWallpaperScale by rememberUpdatedState(wallpaperScale)
        val latestWallpaperOffset by rememberUpdatedState(wallpaperOffset)
        val latestOnScaleChange by rememberUpdatedState(onWallpaperScaleChange)
        val latestOnOffsetChange by rememberUpdatedState(onWallpaperOffsetChange)
        val latestMinWallpaperScale by rememberUpdatedState(minWallpaperScale)
        val latestWallpaperBitmap by rememberUpdatedState(wallpaperBitmap)
        val latestScreenWidthPx by rememberUpdatedState(screenWidthPx)
        val latestScreenHeightPx by rememberUpdatedState(screenHeightPx)

        // 指针作用域内无法直接 animate，经状态触发回弹
        var bounceBackTrigger by remember { mutableIntStateOf(0) }
        var gestureEndScale by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(bounceBackTrigger) {
            if (bounceBackTrigger > 0 && gestureEndScale < latestMinWallpaperScale) {
                animate(
                    initialValue = gestureEndScale,
                    targetValue = latestMinWallpaperScale,
                    animationSpec = tween(
                        durationMillis = 350,
                        easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f)
                    )
                ) { value, _ ->
                    onWallpaperScaleChange(value)
                }
            }
        }

// beyondViewportPageCount=1 让 next page 先进 composition+layout，缓解水平滑动掉帧
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        userScrollEnabled = !isWallpaperEditing
    ) { page ->
            val week = page + 1



            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (scheduleScrollBehavior != null) Modifier.nestedScroll(scheduleScrollBehavior.nestedScrollConnection)
                        else Modifier
                    )
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInWindow()
                        scheduleViewport.topPx = pos.y
                        scheduleViewport.bottomPx = pos.y + coordinates.size.height
                    }
                    .verticalScroll(scrollState)
                    // 布局期读顶栏高度，避免组合期读导致整页逐帧重组；内容高度无限，折叠期间子树不重测
                    .layout { measurable, constraints ->
                        val topPad = contentTopPaddingDp.roundToPx().coerceAtLeast(0)
                        val bottomPad = 140.dp.roundToPx()
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height + topPad + bottomPad) {
                            placeable.place(0, topPad)
                        }
                    }
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val dayBoundsArray = remember { arrayOfNulls<FloatArray>(8) }
                    var lastDayBoundsVersion by remember { mutableIntStateOf(0) }
                    val isScheduleScrolling by scheduleScrollInProgress
                    LaunchedEffect(isScheduleScrolling) {
                        // 停滑后冲刷：滑动中 onGloballyPositioned 只写数组不递增版本号
                        if (!isScheduleScrolling) lastDayBoundsVersion++
                    }
                    // 提升到 Row 之外：特殊横带按同一套列宽切分内部星期子块
                    val pageDayRange = remember(weekendDaysByWeek, week) {
                        (1..5).toList() + (weekendDaysByWeek[week] ?: emptySet()).filter { it in 6..7 }
                    }
                    // 特殊课程横带：作为 Row 下层背景条带，起止时间由左侧时间列标注
                    specialGrid.specialBands.forEach { band ->
                        val bandItems = remember(specialBlocks, band.blockId) {
                            specialBlocks.firstOrNull { it.id == band.blockId }?.safeItems ?: emptyList()
                        }
                        // 只覆盖周一~周日列，不盖左侧时间轴
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(band.height.dp)
                                .offset(y = band.top.dp)
                                // end 与下方 Row 的 end padding 一致，子块才能与 DayColumn 逐列对齐
                                .padding(
                                    start = (if (isTablet) 24.dp else 0.dp) + (if (isTablet) 56.dp else 36.dp),
                                    end = if (isTablet) 24.dp else 2.dp
                                )
                        ) {
                            SpecialBandOverlay(
                                name = band.name,
                                hasBlur = wallpaperBitmap != null,
                                isDark = scheduleIsDark,
                                cardCornerRadius = cardCornerRadius,
                                cardBlurRadius = cardBlurRadius,
                                cardAlpha = cardAlpha,
                                cardSurfaceAlpha = cardSurfaceAlpha,
                                cardRefraction = cardRefraction,
                                isTablet = isTablet,
                                wallpaperBackdrop = activeCardBackdrop,
                                items = bandItems,
                                dayRange = pageDayRange
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isTablet) Modifier.padding(horizontal = 24.dp) else Modifier.padding(end = 2.dp)
                            )
                    ) {
                        SectionColumn(
                            totalSections = totalSections,
                            morningSections = morningSections,
                            afternoonSections = afternoonSections,
                            eveningSections = eveningSections,
                            sectionTimes = sectionTimes,
                            sectionNames = sectionNames,
                            specialBlocks = specialBlocks,
                            grid = specialGrid,
                            cardHeightPerSection = cardHeightPerSection,
                            showBreakDividers = showBreakDividers,
                            currentSection = if (week == currentWeek) currentSection else -1,
                            isTablet = isTablet,
                            hasWallpaper = wallpaperBitmap != null,
                            isDark = scheduleIsDark
                        )

                        pageDayRange.forEach { dayOfWeek ->
                            // 按需缓存；调休日返回映射后的 displayWeek
                            val (displayWeekForDay, filteredDayCourses) = filteredCoursesCache.getOrPut(page) {
                                val weekForPage = page + 1
                                allDays.associateWith { dayOfWeek ->
                                    val dateForDay = semesterStartMonday
                                        .plusWeeks((weekForPage - 1).toLong())
                                        .plusDays((dayOfWeek - 1).toLong())
                                    val swapForDay = workswapIndex[dateForDay.toString()]
                                    val displayDay = swapForDay?.followWeekday?.takeIf { it in 1..7 } ?: dayOfWeek
                                    val displayWeek = swapForDay?.followWeek?.takeIf { it > 0 } ?: weekForPage
                                    val dayCourses = coursesByDay[displayDay] ?: emptyList()
                                    val courses = if (showNonCurrentWeek) dayCourses
                                    else dayCourses.filter { it.isActiveInWeek(displayWeek) }
                                    displayWeek to courses
                                }
                            }.getOrElse(dayOfWeek) { week to emptyList() }
                            val dateForDay = semesterStartMonday
                                .plusWeeks((week - 1).toLong())
                                .plusDays((dayOfWeek - 1).toLong())
                            val isHoliday = holidayIndex[dateForDay.toString()] != null
                            val isWorkSwap = workswapIndex[dateForDay.toString()]
                                ?.followWeekday?.takeIf { it in 1..7 } != null
                            val stableOnCourseClick: (Course) -> Unit =
                                remember(page, dayOfWeek, week, displayWeekForDay) {
                                    { course ->
                                        // 调休日用被调星期+映射周次查槽位，避免弹出原始课程
                                        val coursesAtSlot = viewModel.getCoursesAtSlot(
                                            displayWeekForDay,
                                            course.dayOfWeek,
                                            course.startSection,
                                            course.endSection
                                        )
                                        selectedCourses = coursesAtSlot
                                        selectedCourse = coursesAtSlot.find { it.id == course.id } ?: course
                                        showCourseDetail = true
                                        onPopupStateChange(true)
                                    }
                                }
                            val stableOnEmptyClick: (Int) -> Unit = remember(dayOfWeek) {
                                { section -> viewModel.showAddDialog(dayOfWeek, section) }
                            }
                            val stableOnEmptyLongPress: (Int, Float, Float, Float, Float) -> Unit =
                                remember(dayOfWeek, onEmptyLongPress) {
                                    { section, centerX, cellTopY, width, height ->
                                        // 长按进菜单时清掉 pending，避免两层交互叠加
                                        pendingDay = -1
                                        pendingSection = -1
                                        onEmptyLongPress(dayOfWeek, section, centerX, cellTopY, width, height)
                                    }
                                }
                            val stableOnCourseLongPress: (Course, Float, Float, Float, Float, com.kyant.backdrop.Backdrop?, Int) -> Unit =
                                remember(page, dayOfWeek) {
                                    { course, left, top, width, height, _, cWeek ->
                                        val backdrop = activeCardBackdrop ?: courseCardBackdrop
                                        onCourseLongPress(course, left, top, width, height, backdrop, cWeek)
                                    }
                                }
                            DayColumn(
                                dayOfWeek = dayOfWeek,
                                gridScrollFlag = gridScrollFlag,
                                courses = filteredDayCourses,
                                onCourseClick = stableOnCourseClick,
                                onEmptyClick = stableOnEmptyClick,
                                onEmptyLongPress = stableOnEmptyLongPress,
                                morningSections = morningSections,
                                afternoonSections = afternoonSections,
                                eveningSections = eveningSections,
                                sectionTimes = sectionTimes,
                                specialBlocks = specialBlocks,
                                grid = specialGrid,
                                currentWeek = displayWeekForDay,
                                isHoliday = isHoliday,
                                isWorkSwap = isWorkSwap,
                                pendingDay = pendingDay,
                                pendingSection = pendingSection,
                                onPendingChange = onPendingChange,
                                wallpaperBackdrop = activeCardBackdrop,
                                cardBlurRadius = cardBlurRadius,
                                cardAlpha = cardAlpha,
                                cardSurfaceAlpha = cardSurfaceAlpha,
                                cardHeightPerSection = cardHeightPerSection,
                                cardCornerRadius = cardCornerRadius,
                                showBreakDividers = showBreakDividers,
                                isTablet = isTablet,
                                cardContentAlignment = cardContentAlignment,
                                cardTextColor = cardTextColor,
                                cardTextScale = cardTextScale,
                                showClassroom = showClassroom,
                                showTeacher = showTeacher,
                                cardRefraction = cardRefraction,
                                draggingCourseIds = draggingCourseIds,
                                onCourseLongPress = stableOnCourseLongPress,
                                onCourseDragStart = onCourseDragStart,
                                onCourseDrag = onCourseDrag,
                                onCourseDragEnd = onCourseDragEnd,
                                onCourseMenuDismiss = onCourseMenuDismiss,
                                dropHighlightSections = if (dropHighlight?.first == dayOfWeek) dropHighlight.second else null,
                                isDark = scheduleIsDark,
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coordinates ->
                                        val pos = coordinates.positionInRoot()
                                        val w = coordinates.size.width.toFloat()
                                        val arr = dayBoundsArray[dayOfWeek]
                                        // 滑动中只写数组不递增版本号，避免逐帧重组；停后由 LaunchedEffect 冲刷
                                        if (arr == null) {
                                            dayBoundsArray[dayOfWeek] = floatArrayOf(pos.x, pos.x + w, pos.y)
                                            if (!gridScrollFlag.scrolling) lastDayBoundsVersion++
                                        } else if (arr[0] != pos.x || arr[1] != pos.x + w || arr[2] != pos.y) {
                                            arr[0] = pos.x
                                            arr[1] = pos.x + w
                                            arr[2] = pos.y
                                            if (!gridScrollFlag.scrolling) lastDayBoundsVersion++
                                        }
                                    }
                            )
                        }
                    }

                    // 点击层必须在 Row 之上：空节次层会消费整列点击
                    specialGrid.specialBands.forEach { band ->
                        val bandItems = remember(specialBlocks, band.blockId) {
                            specialBlocks.firstOrNull { it.id == band.blockId }?.safeItems ?: emptyList()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(band.height.dp)
                                .offset(y = band.top.dp)
                                .padding(
                                    start = (if (isTablet) 24.dp else 0.dp) + (if (isTablet) 56.dp else 36.dp),
                                    end = if (isTablet) 24.dp else 2.dp
                                )
                        ) {
                            SpecialBandClickLayer(
                                items = bandItems,
                                dayRange = pageDayRange,
                                onItemClick = { item ->
                                    specialItemEditingBlockId = band.blockId
                                    specialItemEditingId = item.id
                                    specialItemName = item.name
                                    specialItemSelectedDays = (item.startDay..item.endDay).toSet()
                                    showSpecialItemDialog = true
                                },
                                onEmptyClick = { day ->
                                    specialItemEditingBlockId = band.blockId
                                    specialItemEditingId = -1L
                                    specialItemName = ""
                                    specialItemSelectedDays = setOf(day)
                                    showSpecialItemDialog = true
                                }
                            )
                        }
                    }

                    // 仅当前页上报；几何实际变化时才触发回调，避免每次重组分配新对象
                    val sectionHeightPx = with(density) { cardHeightPerSection.dp.toPx() }
                    val prevBoundsVersion = remember { mutableIntStateOf(lastDayBoundsVersion) }
                    val prevSectionHeight = remember { mutableFloatStateOf(sectionHeightPx) }
                    val prevMorning = remember { mutableIntStateOf(morningSections) }
                    val prevAfternoon = remember { mutableIntStateOf(afternoonSections) }
                    val prevEvening = remember { mutableIntStateOf(eveningSections) }
                    val prevShowBreak = remember { mutableStateOf(showBreakDividers) }
                    SideEffect {
                        if (page == pagerState.currentPage) {
                            val changed = prevBoundsVersion.intValue != lastDayBoundsVersion
                                    || prevSectionHeight.floatValue != sectionHeightPx
                                    || prevMorning.intValue != morningSections
                                    || prevAfternoon.intValue != afternoonSections
                                    || prevEvening.intValue != eveningSections
                                    || prevShowBreak.value != showBreakDividers
                            if (changed) {
                                prevBoundsVersion.intValue = lastDayBoundsVersion
                                prevSectionHeight.floatValue = sectionHeightPx
                                prevMorning.intValue = morningSections
                                prevAfternoon.intValue = afternoonSections
                                prevEvening.intValue = eveningSections
                                prevShowBreak.value = showBreakDividers
                                val boundsMap = mutableMapOf<Int, FloatArray>()
                                for (i in 1..7) {
                                    val arr = dayBoundsArray[i]
                                    if (arr != null) boundsMap[i] = arr
                                }
                                onGridGeometryChange(
                                    ScheduleGridGeometry(
                                        dayBounds = boundsMap,
                                        sectionHeightPx = sectionHeightPx,
                                        morningSections = morningSections,
                                        afternoonSections = afternoonSections,
                                        eveningSections = eveningSections,
                                        showBreakDividers = showBreakDividers
                                    )
                                )
                            }
                        }
                    }

                    val morningHeight = specialGrid.dividerY.getOrNull(0)?.toInt() ?: (morningSections * cardHeightPerSection).toInt()
                    val afternoonHeight = (afternoonSections * cardHeightPerSection).toInt()
                    val dividerOffset = if (showBreakDividers) 24 else 0
                    val dinnerBreakY = specialGrid.dividerY.getOrNull(1)?.toInt()
                        ?: (morningHeight + dividerOffset + afternoonHeight)

                    if (showBreakDividers) {
                    val dividerShape = ContinuousRoundedRectangle(12.dp)
                    val dividerHorizontalPadding = if (isTablet) 24.dp else 4.dp
                    val dividerIsDark = scheduleIsDark
                    val dividerDensity = LocalDensity.current
                    val dividerBlurPx = with(dividerDensity) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
                    val dividerLensRadiusPx = with(dividerDensity) { remember(cardRefraction) { (cardRefraction.lensRadiusDp * 0.67f).dp.toPx() } }
                    val dividerLensStrengthPx = with(dividerDensity) { remember(cardRefraction) { (cardRefraction.lensStrengthDp * 1f).dp.toPx() } }
                    val hasWallpaperDivider = wallpaperBitmap != null
                    val dividerBaseColor = if (hasWallpaperDivider) Color.Transparent else if (dividerIsDark) Color(0xFF121212) else Color(0xFFF0F0F0)
                    // 15% 锚定当前视觉；百分比映射到 0..1（仅作用于有壁纸玻璃表面）
                    val dividerSurfaceAlphaAt15 = if (dividerIsDark) 0.64f else 0.50f
                    val dividerBlurShape = remember { ContinuousRoundedRectangle(12.dp) }
                    val dividerEdgeLightShape = remember { ContinuousRoundedRectangle(12.dp) }

                    @Composable
                    fun BreakDivider(offsetY: Int, text: String) {
                        // 0%→0，15%→当前，100%→1；两段线性映射（仅作用于有壁纸玻璃表面）
                        val p = cardSurfaceAlpha.coerceIn(0f, 1f)
                        val surfaceAlpha = if (p <= 0.15f) {
                            (p / 0.15f) * dividerSurfaceAlphaAt15
                        } else {
                            dividerSurfaceAlphaAt15 +
                                ((p - 0.15f) / 0.85f) * (1f - dividerSurfaceAlphaAt15)
                        }
                        // 无壁纸底色保持实色，不随「卡片不透明度」变化
                        val dividerFgBase = dividerBaseColor
                        val dividerFgSurface = if (dividerIsDark) {
                            Color(0xFF323232).copy(alpha = surfaceAlpha)
                        } else {
                            Color.White.copy(alpha = surfaceAlpha)
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().offset(y = offsetY.dp)
                                .height(24.dp)
                                .padding(vertical = 2.dp)
                                .padding(horizontal = dividerHorizontalPadding)
                                .background(dividerFgBase, dividerShape)
                                .then(
                                    if (hasWallpaperDivider) {
                                        val dividerBackdrop = activeCardBackdrop ?: courseCardBackdrop
                                        val dividerEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
                                            remember(dividerBackdrop, dividerBlurPx, dividerLensRadiusPx, dividerLensStrengthPx) {
                                                {
                                                    if (dividerBackdrop !is SharedBlurBackdrop) {
                                                        blur(dividerBlurPx)
                                                    }
                                                    lens(dividerLensRadiusPx, dividerLensStrengthPx)
                                                }
                                            }
                                        Modifier.drawBackdrop(
                                            backdrop = dividerBackdrop,
                                            shape = { dividerBlurShape },
                                            effects = dividerEffects,
                                            highlight = null,
                                            shadow = null,
                                            downsampleScale = 0.48f,
                                            viewport = com.kyant.backdrop.LocalBackdropViewport.current,
                                            onDrawSurface = remember(dividerFgSurface) {
                                                {
                                                    drawRect(dividerFgSurface)
                                                }
                                            }
                                        ).edgeLight(shape = dividerEdgeLightShape, edgeLight = rememberCourseCardEdgeLight())
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = text,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    }
                    BreakDivider(morningHeight, "午休")
                    BreakDivider(dinnerBreakY, "晚休")
                    }
                }
            }

            if (isWallpaperEditing && wallpaperBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                var gestureScale = latestWallpaperScale
                                var lastDisplayScale = gestureScale
                                do {
                                    val event = awaitPointerEvent()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    gestureScale *= zoom
                                    val newScale = if (gestureScale < latestMinWallpaperScale) {
                                        val diff = gestureScale - latestMinWallpaperScale
                                        latestMinWallpaperScale + diff * 0.3f
                                    } else {
                                        gestureScale
                                    }
                                    lastDisplayScale = newScale
                                    val bmp = latestWallpaperBitmap
                                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                                        val fitScale = minOf(latestScreenWidthPx / bmp.width, latestScreenHeightPx / bmp.height)
                                        val scaledW = bmp.width * fitScale * newScale
                                        val scaledH = bmp.height * fitScale * newScale
                                        val maxOffsetX = ((scaledW - latestScreenWidthPx) / 2f).coerceAtLeast(0f)
                                        val maxOffsetY = ((scaledH - latestScreenHeightPx) / 2f).coerceAtLeast(0f)
                                        val newOffset = latestWallpaperOffset + pan
                                        latestOnScaleChange(newScale)
                                        latestOnOffsetChange(
                                            androidx.compose.ui.geometry.Offset(
                                                newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                                newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                        )
                                    } else {
                                        latestOnScaleChange(newScale)
                                        latestOnOffsetChange(latestWallpaperOffset + pan)
                                    }
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                gestureEndScale = lastDisplayScale
                                bounceBackTrigger++
                            }
                        }
                )
            }
        }

        val detailEndAction: @Composable () -> Unit = {
            val scope = rememberCoroutineScope()
            val material = LocalSheetTopBarMaterial.current
            LiquidTopBarButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    val course = selectedCourse ?: selectedCourses.firstOrNull()
                    showCourseDetail = false
                    // 等关闭动画后再开添加弹窗
                    scope.launch {
                        delay(100)
                        if (course != null) {
                            viewModel.showAddDialog(
                                course.dayOfWeek,
                                course.startSection,
                                course.endSection
                            )
                        } else {
                            viewModel.showAddDialog()
                        }
                    }
                },
                backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
                icon = MiuixIcons.Add,
                contentDescription = "添加课程",
                modifier = Modifier.padding(end = if (isTablet) 16.dp else 18.dp),
                iconSize = 24.dp,
                containerColor = if (isAppDarkTheme()) Color(0xFF363636).copy(0.4f)
                else Color(0xFFFFFFFF).copy(0.6f),
                backdropAlpha = material.backdropAlpha,
                shadowAlpha = material.shadowAlpha,
            )
        }
        val detailContent: @Composable () -> Unit = {
            val scope = rememberCoroutineScope()
            val coursesToShow = remember(selectedCourses, selectedCourse, viewingWeek) {
                selectedCourses.ifEmpty { listOfNotNull(selectedCourse) }
                    .sortedWith(
                        compareByDescending<Course> { it.isActiveInWeek(viewingWeek) }
                            .thenBy {
                                val distance = if (it.isActiveInWeek(viewingWeek)) 0
                                else if (viewingWeek < it.startWeek) it.startWeek - viewingWeek
                                else viewingWeek - it.endWeek
                                distance
                            }
                    )
                }
            // 始终参与布局，用 graphicsLayer 做透明/位移，避免移除节点导致弹窗高度闪烁
            var revealCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(coursesToShow.size) {
                if (skipSheetReveal) {
                    revealCount = coursesToShow.size
                    return@LaunchedEffect
                }
                revealCount = 0
                delay(120)
                for (i in 1..coursesToShow.size) {
                    revealCount = i
                    delay(56)
                }
            }
            Column(
                modifier = Modifier
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isTablet) 56.dp else 58.dp))
                coursesToShow.forEachIndexed { index, course ->
                    val summaryText = buildString {
                        append(course.getWeekText())
                        append(" ｜ ")
                        append(course.getTimeDisplayText())
                        if (course.classroom.isNotEmpty()) {
                            append("\n")
                            append(course.classroom)
                        }
                        if (course.teacher.isNotEmpty()) {
                            if (course.classroom.isNotEmpty()) append(" ｜ ")
                            append(course.teacher)
                        }
                    }
                    val isCurrentWeekCourse = course.isActiveInWeek(viewingWeek)
                    val isHidden = course.id in hiddenCourseIds
                    // 普通 holder 零重组；key 用 course.id 防列表重排错位
                    val cardBoundsHolder = remember(course.id) { CardBoundsHolder() }
                    // 动画结束后去掉离屏层；skipSheetReveal 时直接全显不播淡入
                    val appear by animateFloatAsState(
                        targetValue = if (index < revealCount) 1f else 0f,
                        animationSpec = tween(220),
                        label = "reveal$index",
                    )
                    val shown = if (skipSheetReveal) 1f else appear
                    val revealAnimating = shown < 1f
                    val revealDensity = LocalDensity.current
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isHidden || revealAnimating) Modifier.graphicsLayer {
                                    alpha = (if (isHidden) 0f else 1f) * shown
                                    translationY = (1f - shown) * revealDensity.run { 8.dp.toPx() }
                                    scaleX = 0.97f + 0.03f * shown
                                    scaleY = 0.97f + 0.03f * shown
                                } else Modifier
                            )
                    ) {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { coordinates ->
                                val position =
                                    coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                                val size = coordinates.size
                                cardBoundsHolder.rect = androidx.compose.ui.geometry.Rect(
                                    left = position.x,
                                    top = position.y,
                                    right = position.x + size.width,
                                    bottom = position.y + size.height
                                )
                            },
                        insideMargin = PaddingValues(0.dp),
                        pressFeedbackType = PressFeedbackType.None,
                        showIndication = true,
                        colors = CardDefaults.defaultColors(
                            color = if (isAppDarkTheme()) Color(0xFF303030) else Color(0xFFFFFFFF),
                            contentColor = MiuixTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            val coursesForDetail = courses.filter { it.name == course.name }
                            // 本周有课用当前查看周，否则回退到最近上课周
                            val targetWeek = if (course.isActiveInWeek(viewingWeek)) viewingWeek
                            else if (viewingWeek < course.startWeek) course.startWeek
                            else course.endWeek
                            val bounds = cardBoundsHolder.rect
                            if (bounds != null) {
                                onCourseClick(
                                    coursesForDetail,
                                    bounds.left,
                                    bounds.top,
                                    bounds.width,
                                    bounds.height,
                                    null,
                                    course.id,
                                    targetWeek
                                )
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isCurrentWeekCourse) course.name else "${course.name}（非本周）",
                                    style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = summaryText,
                                    style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(ContinuousRoundedRectangle(20.dp))
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .clickable {
                                        showCourseDetail = false
                                        onPopupStateChange(false)
                                        // 等关闭动画后再开编辑弹窗
                                        scope.launch {
                                            delay(100)
                                            viewModel.showEditDialog(course)
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "编辑",
                                    style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    } // Column (isHidden)
                }
                Spacer(modifier = Modifier.height(if (isTablet) 0.dp else 260.dp))
            }
        }
        // show 状态下沉到子作用域：顶层读会在点卡片那帧重组整页，压在弹窗动画头两帧
        CourseDetailSheet(
            showState = externalShowCourseDetail,
            isTablet = isTablet,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismiss = {
                showCourseDetail = false
                onPopupStateChange(false)
            },
            endAction = detailEndAction,
            content = detailContent,
        )

        val editingBlock = specialBlocks.firstOrNull { it.id == specialItemEditingBlockId }
        // 编辑时排除自己，否则无法取消自己占用的格子
        val occupiedDays = (editingBlock?.safeItems ?: emptyList())
            .filter { it.id != specialItemEditingId }
            .flatMap { it.startDay..it.endDay }
            .toSet()
        SpecialItemEditDialog(
            show = showSpecialItemDialog,
            isEditing = specialItemEditingId != -1L,
            name = specialItemName,
            selectedDays = specialItemSelectedDays,
            occupiedDays = occupiedDays,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onNameChange = { specialItemName = it },
            onDayToggle = { day ->
                specialItemSelectedDays = if (day in specialItemSelectedDays) {
                    specialItemSelectedDays - day
                } else {
                    specialItemSelectedDays + day
                }
            },
            onDismiss = { showSpecialItemDialog = false },
            onSave = {
                val block = editingBlock
                if (block == null) {
                    showSpecialItemDialog = false
                } else {
                    val trimmedName = specialItemName.trim()
                    val selected = specialItemSelectedDays.sorted()
                    when {
                        trimmedName.isBlank() -> {
                            Toast.makeText(scheduleContext, "请输入名称", Toast.LENGTH_SHORT).show()
                        }
                        selected.isEmpty() -> {
                            Toast.makeText(scheduleContext, "请选择至少一个星期", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            // 不连续点选合并为连续区间，如 {1,3,4} → 两个子块
                            val ranges = mergeConsecutiveDays(selected)
                            val base = block.safeItems.filter { it.id != specialItemEditingId }
                            val newItems = ranges.map { (s, e) ->
                                com.haooz.chedule.data.SpecialItem(
                                    id = System.currentTimeMillis(),
                                    name = trimmedName,
                                    startDay = s,
                                    endDay = e
                                )
                            }
                            val finalItems = (base + newItems).sortedBy { it.startDay }
                            settingsViewModel.updateSpecialBlocks(
                                specialBlocks.map {
                                    if (it.id == specialItemEditingBlockId) it.copy(items = finalItems) else it
                                }
                            )
                            showSpecialItemDialog = false
                        }
                    }
                }
            },
            onDelete = {
                val block = editingBlock
                if (block != null) {
                    settingsViewModel.updateSpecialBlocks(
                        specialBlocks.map {
                            if (it.id == specialItemEditingBlockId) {
                                it.copy(items = block.safeItems.filter { item -> item.id != specialItemEditingId })
                            } else it
                        }
                    )
                }
                showSpecialItemDialog = false
            }
        )
    }
    } // CompositionLocalProvider
}

/** 弹窗里周一~周日的格子标签 */
private val SPECIAL_WEEK_LABELS = arrayOf("一", "二", "三", "四", "五", "六", "日")

// 已被同横带其他子块占用的星期置灰不可点，避免重叠
@Composable
private fun SpecialItemEditDialog(
    show: Boolean,
    isEditing: Boolean,
    name: String,
    selectedDays: Set<Int>,
    occupiedDays: Set<Int>,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    onNameChange: (String) -> Unit,
    onDayToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    OverlayDialog(
        title = if (isEditing) "编辑安排" else "添加安排",
        summary = null,
        show = show,
        onDismissRequest = onDismiss,
        liquidGlassBackdrop = liquidGlassBackdrop
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NativeMiuixTextField(
                value = name,
                onValueChange = onNameChange,
                label = "名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                requestFocus = show
            )
            // 按连续区间分组显示：{1,3,4} → "周一 / 周三~周四"
            val rangesLabel = if (selectedDays.isEmpty()) {
                "点选下方星期（不连续可分段保存）"
            } else {
                mergeConsecutiveDays(selectedDays.sorted()).joinToString(" / ") { (s, e) ->
                    if (s == e) "周${SPECIAL_WEEK_LABELS[s - 1]}"
                    else "周${SPECIAL_WEEK_LABELS[s - 1]}~周${SPECIAL_WEEK_LABELS[e - 1]}"
                }
            }
            Text(
                text = rangesLabel,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            WeekDayRangeSelector(
                selectedDays = selectedDays,
                occupiedDays = occupiedDays,
                onDayToggle = onDayToggle
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "保存",
                    onClick = onSave,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
            if (isEditing) {
                TextButton(
                    text = "删除该安排",
                    onClick = onDelete,
                    textColor = Color(0xFFF44336),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// 已占用置灰不可点；编辑自身时占用方应排除自己对应格子
@Composable
private fun WeekDayRangeSelector(
    selectedDays: Set<Int>,
    occupiedDays: Set<Int>,
    onDayToggle: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (day in 1..7) {
            val selected = day in selectedDays
            val occupied = day in occupiedDays && !selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(ContinuousRoundedRectangle(10.dp))
                    .background(
                        when {
                            selected -> MiuixTheme.colorScheme.primary
                            occupied -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        }
                    )
                    .clickable(enabled = !occupied) { onDayToggle(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = SPECIAL_WEEK_LABELS[day - 1],
                    style = MiuixTheme.textStyles.body2,
                    color = when {
                        selected -> Color.White
                        occupied -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        else -> MiuixTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

// {1,3,4} → [(1,1), (3,4)]；输入需已排序
private fun mergeConsecutiveDays(sortedDays: List<Int>): List<Pair<Int, Int>> {
    if (sortedDays.isEmpty()) return emptyList()
    val ranges = mutableListOf<Pair<Int, Int>>()
    var start = sortedDays[0]
    var end = sortedDays[0]
    for (i in 1 until sortedDays.size) {
        if (sortedDays[i] == end + 1) {
            end = sortedDays[i]
        } else {
            ranges.add(start to end)
            start = sortedDays[i]
            end = sortedDays[i]
        }
    }
    ranges.add(start to end)
    return ranges
}

// show 状态读取下沉到子作用域，避免页面顶层因 sheet 开关而重组
@Composable
private fun CourseDetailSheet(
    showState: MutableState<Boolean>,
    isTablet: Boolean,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    onDismiss: () -> Unit,
    endAction: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    var show by showState
    // 重建时若已打开则跳过进入动画；关闭后重置
    var skipSheetEnterAnimation by remember { mutableStateOf(show) }
    LaunchedEffect(show) {
        if (!show) {
            skipSheetEnterAnimation = false
        }
    }
    if (isTablet) {
        BlurBottomSheetTablet(
            show = show,
            title = "课程详情",
            dimBackground = true,
            isBottomAligned = true,
            onDismissRequest = onDismiss,
            liquidGlassBackdrop = liquidGlassBackdrop,
            endAction = endAction,
            skipEnterAnimation = skipSheetEnterAnimation,
            content = content,
        )
    } else {
        BlurBottomSheet(
            show = show,
            title = "课程详情",
            liquidGlassBackdrop = liquidGlassBackdrop,
            dimBackground = true,
            onDismissRequest = onDismiss,
            endAction = endAction,
            skipEnterAnimation = skipSheetEnterAnimation,
            content = content,
        )
    }
}
