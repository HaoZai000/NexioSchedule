/** 课程编辑页面 - 修改课程时段/周次 */
package com.haooz.chedule.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.CourseEditStr
import com.haooz.chedule.i18n.NoteStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.CardExpandSpec
import com.haooz.chedule.ui.components.cardRevealLayer
import com.haooz.chedule.ui.components.rememberCardRevealState
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.motion.morphClip
import com.haooz.chedule.ui.effects.motion.rememberMorphTransition
import com.haooz.chedule.ui.utils.CourseSorting
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentEditorChrome
import com.haooz.chedule.ui.utils.NativeDocumentEditorEffect
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.TransitionLayerImage
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.blockTouchPassThrough
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberTopBarMaterialState
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import com.haooz.softglass.shape.continuousBorder
import com.haooz.softglass.shape.continuousClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// ===================== Course Grouping Helpers =====================

data class CourseGroupKey(
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weekType: Int,
    val startWeek: Int,
    val endWeek: Int,
    val selectedWeeks: List<Int> = emptyList(),
    // 自定义时间的课程按实际起止时间分组，避免同名师不同时段被误合并
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null
) {
    // 包含全部区分字段，避免 LazyStaggeredGrid key 冲突
    fun uniqueKey(): String = buildString {
        append(dayOfWeek).append('_')
        append(startSection).append('_')
        append(endSection).append('_')
        append(weekType).append('_')
        append(startWeek).append('_')
        append(endWeek).append('_')
        if (selectedWeeks.isNotEmpty()) append(selectedWeeks)
        if (isCustomTime) {
            append("_t_").append(customStartTime).append('-').append(customEndTime)
        }
    }
}

data class CourseGroup(
    val key: CourseGroupKey,
    val courses: List<Course>
)

// ===================== CourseEditScreen =====================

@Composable
fun CourseEditScreen(
    courses: List<Course>,
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    screenCornerRadius: ScreenCornerRadiiPx,
    /** Retained source card drawing, owned by the underlying management page. */
    cardSnapshot: TransitionLayerSnapshot?,
    cardColor: Color = Color(0xFF4CAF50),
    cardAlpha: Float = 0.12f,
    onBackStart: () -> Unit,
    onBack: () -> Unit,
    onCourseUpdated: (Course) -> Unit = { _ -> },
    onCourseAdded: (Course) -> Unit = { _ -> },
    onDeleteCourse: (String) -> Unit = { _ -> },
    onColorChanged: (Long) -> Unit = { _ -> },
    getOccupiedWeeks: (dayOfWeek: Int, startSection: Int, endSection: Int, excludeIds: List<String>, startTime: String?, endTime: String?) -> Set<Int> = { _, _, _, _, _, _ -> emptySet() },
    liquidGlassBackdrop: com.haooz.softglass.backdrop.backdrops.LayerBackdrop? = null,
    sectionTimes: Map<Int, String> = emptyMap(),
    /** 平板右栏内嵌：不做展开/返回形变，也不拦截返回手势，直接以全尺寸静态呈现 */
    embedded: Boolean = false,
    /** 弹窗/底部抽屉的玻璃采样层：内嵌时传全屏层，使弹窗能采样到左栏内容 */
    dialogBackdrop: GlassBackdrop? = null,
    /** 内嵌时由外部指定内容顶距（与左栏 chromeTop 对齐），替代按顶栏高度推算 */
    contentTopPadding: androidx.compose.ui.unit.Dp? = null,
) {
    // 弹窗默认跟随自身玻璃层；内嵌时由外层指定全屏层
    val dialogGlass: GlassBackdrop? = dialogBackdrop ?: liquidGlassBackdrop
    val courseName = courses.firstOrNull()?.name ?: ""
    // 所有同名课程共享颜色，仅保存时生效
    var selectedColor by remember {
        mutableLongStateOf(
            courses.firstOrNull()?.colorRes ?: Course.courseColors.first()
        )
    }
    var showColorDialog by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(Color(selectedColor)) }

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    // 嵌入分栏时宿主已处理安全区，页内只留固定白边
    val horizontalSafeInsets = if (embedded) Modifier else Modifier.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    )

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val morph = rememberMorphTransition(
        cardLeft = cardLeft,
        cardTop = cardTop,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        screenCornerRadius = screenCornerRadius,
        startCornerRadiusDp = 16f,
        onBackStart = onBackStart,
        onBack = onBack,
        embedded = embedded,
    )

    // ---- UI State ----
    val isDark = isAppDarkTheme()
    var listScrollY by remember { mutableIntStateOf(0) }
    val scrollBehavior = rememberSharedScrollBehavior()

    var deletingGroupId by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteGroup by remember { mutableStateOf<CourseGroup?>(null) }
    var pendingDeleteCourseIds by remember { mutableStateOf<List<String>>(emptyList()) }

    var showAddCourseSheet by remember { mutableStateOf(false) }
    var showEditCourseSheet by remember { mutableStateOf(false) }
    val nativeChrome = if (embedded) null else LocalNativeDocumentChrome.current
    val nativeBarInset = (nativeChrome?.barInset ?: 0.0).dp
    var exiting by remember { mutableStateOf(false) }

    fun closeEditor() {
        if (exiting) return
        exiting = true
        morph.close()
    }

    if (!embedded) {
        NativeDocumentEditorEffect(
            if (exiting) null else NativeDocumentEditorChrome(
                title = courseName,
                cancel = NativeDocumentAction("chevron.backward", CommonStr.back(), enabled = !showAddCourseSheet && !showEditCourseSheet) { closeEditor() },
                save = NativeDocumentAction("plus", CourseEditStr.addCourse(), enabled = !showAddCourseSheet && !showEditCourseSheet) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    showAddCourseSheet = true
                },
            )
        )
    }
    var editingGroup by remember { mutableStateOf<CourseGroup?>(null) }
    // 待添加课程（弹窗关闭后再添加，触发淡入动画）
    var pendingAddCourse by remember { mutableStateOf<Course?>(null) }

    // 动画结束后执行删除
    LaunchedEffect(deletingGroupId) {
        val courseIds = pendingDeleteCourseIds
        if (deletingGroupId != null && courseIds.isNotEmpty()) {
            delay(300.milliseconds)
            courseIds.forEach { onDeleteCourse(it) }
            pendingDeleteCourseIds = emptyList()
            deletingGroupId = null
        }
    }

    var hasTriggeredAutoBack by remember { mutableStateOf(false) }
    LaunchedEffect(courses.size) {
        // 内嵌（平板右栏）不自动关闭，交由外层切换选中项
        if (!embedded && courses.isEmpty() && !hasTriggeredAutoBack && morph.state.value.progress > 0.5f) {
            hasTriggeredAutoBack = true
            delay(400.milliseconds)
            morph.close()
        }
    }

    // 颜色修改即保存
    LaunchedEffect(selectedColor) {
        if (selectedColor != courses.firstOrNull()?.colorRes) {
            onColorChanged(selectedColor)
            courses.forEach { course ->
                onCourseUpdated(
                    course.copy(
                        colorRes = selectedColor,
                        lastModified = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
        }
    }

    // 弹窗关闭后再添加课程，触发卡片淡入
    LaunchedEffect(showAddCourseSheet) {
        if (!showAddCourseSheet) {
            pendingAddCourse?.let { course ->
                onCourseAdded(course)
                pendingAddCourse = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val alpha = morph.state.value.bgAlpha
                drawRect(
                    if (isDark) ComposeColor(0xFF2C2C2C).copy(alpha = alpha)
                    else ComposeColor.Black.copy(alpha = alpha)
                )
            }
            // 只挡下层穿透、不 consume，避免内部列表/控件滑不动
            .blockTouchPassThrough()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = morph.state.value
                    clip = false
                    transformOrigin = TransformOrigin(s.anchorX, 0.5f)
                    scaleX = s.scale
                    scaleY = s.scale
                    translationX = s.translationX
                    translationY = s.translationY
                }
                .morphClip(morph)
                .background(MiuixTheme.colorScheme.surface)
                .background(cardColor.copy(alpha = cardAlpha))
        ) {
            if (cardSnapshot != null) {
                TransitionLayerImage(
                    snapshot = cardSnapshot,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val s = morph.state.value
                            shape = ContinuousRoundedRectangle((18 / s.scale).dp)
                            clip = true
                            alpha = s.snapshotAlpha
                        },
                    contentScale = ContentScale.FillWidth
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = morph.state.value.contentAlpha }
            ) {
                Scaffold(
                    modifier = Modifier.consumeWindowInsets(PaddingValues(top = nativeBarInset)),
                    topBar = {

                        // 本页唯一材质状态：按钮玻璃走全站同一判据（本页遮罩常驻，不接 maskAlpha）
                        val material = rememberTopBarMaterialState(scrollBehavior)
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop!!,
                            topInsetAdjustment = nativeBarInset,
                            noTopStatusBar = hostVerticalBarEdge(),
                        ) {
                            CollapsibleTopAppBar(
                                title = courseName,
                                largeTitle = courseName,
                                modifier = horizontalSafeInsets,
                                scrollBehavior = scrollBehavior,
                                materialState = material,
                                contentPadding = {},
                                startAction = if (embedded || nativeChrome != null) null else {
                                    { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = { morph.close() },
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
                                },
                                endAction = if (nativeChrome != null) null else { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showAddCourseSheet = true
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Add,
                                        contentDescription = CourseEditStr.addCourse(),
                                        iconSize = 24.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                            )
                        }

                    },
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                // 无材质档：玻璃全关，采样录屏层不挂
                                if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) {
                                    Modifier
                                } else {
                                    Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop!!)
                                }
                            )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MiuixTheme.colorScheme.surface),
                            insideMargin = PaddingValues(0.dp),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surface,
                                contentColor = MiuixTheme.colorScheme.onSurface
                            )
                        ) {
                        // 同一门课的多个时段排序：未排时间的沉底 → 周一→周日 → 实际上课时刻 → 节次 → 周次。
                        // 不排会沿用 groupBy 插入序（= 数据库添加顺序），视觉上是乱的。
                        val courseGroups = remember(courses, sectionTimes) {
                            courses.groupBy { course ->
                                CourseGroupKey(
                                    dayOfWeek = course.dayOfWeek,
                                    startSection = course.startSection,
                                    endSection = course.endSection,
                                    weekType = course.weekType,
                                    startWeek = course.startWeek,
                                    endWeek = course.endWeek,
                                    selectedWeeks = course.selectedWeeks,
                                    isCustomTime = course.isCustomTime,
                                    customStartTime = course.customStartTime,
                                    customEndTime = course.customEndTime
                                )
                            }.map { (key, groupCourses) ->
                                CourseGroup(key = key, courses = groupCourses)
                            }.sortedWith(
                                compareBy(
                                    { if (CourseSorting.hasScheduledTime(it.courses.first())) 0 else 1 },
                                    { it.key.dayOfWeek },
                                    { CourseSorting.startMinutesOf(it.courses.first(), sectionTimes) },
                                    { it.key.startSection },
                                    { it.key.endSection },
                                    { it.key.startWeek },
                                    { it.key.endWeek }
                                )
                            )
                        }

                            val gridState = rememberLazyStaggeredGridState()
                            LaunchedEffect(gridState) {
                                snapshotFlow { gridState.firstVisibleItemScrollOffset }
                                    .collect { offset ->
                                        listScrollY = offset
                                    }
                            }
                            val topBarHeightDp = with(density) {
                                scrollBehavior.currentHeightPx.toDp()
                            }
                            LazyVerticalStaggeredGrid(
                                state = gridState,
                                columns = StaggeredGridCells.Fixed(1),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(horizontalSafeInsets)
                                    .overScrollVertical()
                                    .scrollEndHaptic(
                                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                    )
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    start = tabletHorizontalPadding,
                                    top = contentTopPadding
                                        ?: (paddingValues.calculateTopPadding() + topBarHeightDp - 74.dp),
                                    end = tabletHorizontalPadding,
                                    bottom = 120.dp
                                ),
                                verticalItemSpacing = 12.dp,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                item(key = "color_picker", span = StaggeredGridItemSpan.FullLine) {
                                    val allColors = remember { Course.courseColors }
                                    val colorColumns = 6
                                    val totalItems =
                                        remember(allColors) { allColors.size + 1 } // +1 for custom color button
                                    val colorRows = remember(
                                        totalItems,
                                        colorColumns
                                    ) { (totalItems + colorColumns - 1) / colorColumns }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(top = 14.dp),
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = CourseEditStr.courseColor(),
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(
                                                    start = 16.dp,
                                                    bottom = 10.dp
                                                )
                                            )
                                            for (row in 0 until colorRows) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            start = 12.dp,
                                                            end = 12.dp,
                                                            bottom = 12.dp
                                                        ),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    for (col in 0 until colorColumns) {
                                                        val colorIndex = row * colorColumns + col
                                                        if (colorIndex < allColors.size) {
                                                            val color = allColors[colorIndex]
                                                            val isSelected = color == selectedColor
                                                            val primaryColor =
                                                                MiuixTheme.colorScheme.primary
                                                            val borderAlpha by animateFloatAsState(
                                                                targetValue = if (isSelected) 1f else 0f,
                                                                animationSpec = tween(durationMillis = 200),
                                                                label = "borderAlpha"
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .aspectRatio(1f)
                                                                    .pointerInput(Unit) {
                                                                        detectTapGestures {
                                                                            selectedColor = color
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                // 选中态：外圈主题色描边，内部填课程色
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .continuousBorder(
                                                                            width = 2.dp,
                                                                            color = primaryColor.copy(
                                                                                alpha = borderAlpha
                                                                            ),
                                                                            cornerRadius = 12.dp
                                                                        )
                                                                        .padding(4.dp)
                                                                        .continuousClip(8.dp)
                                                                        .background(
                                                                            Color(color).copy(
                                                                                alpha = if (isDark) 0.22f else 0.16f
                                                                            )
                                                                        )
                                                                )
                                                            }
                                                        } else if (colorIndex == allColors.size) {
                                                            val isCustomColor =
                                                                selectedColor !in allColors
                                                            val hintColor =
                                                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                            val primaryColor =
                                                                MiuixTheme.colorScheme.primary
                                                            val customBorderAlpha by animateFloatAsState(
                                                                targetValue = if (isCustomColor) 1f else 0f,
                                                                animationSpec = tween(durationMillis = 200),
                                                                label = "customBorderAlpha"
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .aspectRatio(1f)
                                                                    .pointerInput(Unit) {
                                                                        detectTapGestures {
                                                                            customColor = Color(
                                                                                selectedColor
                                                                            )
                                                                            showColorDialog = true
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                // 选中态：外圈主题色描边
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .continuousBorder(
                                                                            width = 2.dp,
                                                                            color = primaryColor.copy(
                                                                                alpha = customBorderAlpha
                                                                            ),
                                                                            cornerRadius = 12.dp
                                                                        )
                                                                        .padding(4.dp)
                                                                        .continuousClip(8.dp)
                                                                        .background(
                                                                            if (isCustomColor) Color(
                                                                                selectedColor
                                                                            ).copy(
                                                                                alpha = if (isDark) 0.22f else 0.16f
                                                                            )
                                                                            else if (isDark) Color(
                                                                                0xFF363636
                                                                            ) else MiuixTheme.colorScheme.surface
                                                                        ),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    if (!isCustomColor) {
                                                                        Icon(
                                                                            imageVector = MiuixIcons.Add,
                                                                            contentDescription = CourseEditStr.customColorLabel(),
                                                                            modifier = Modifier.size(
                                                                                18.dp
                                                                            ),
                                                                            tint = hintColor
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                items(
                                    items = courseGroups,
                                    key = { it.key.uniqueKey() },
                                    contentType = { "CourseGroupCard" }
                                ) { group ->
                                    val groupKey = group.key.uniqueKey()
                                    val isDeleting = deletingGroupId == groupKey

                                    // 入场由 CardReveal 负责，这里只管删除时的收拢（同一条 spring）
                                    AnimatedVisibility(
                                        visible = !isDeleting,
                                        enter = EnterTransition.None,
                                        exit = shrinkVertically(CardExpandSpec.HeightSpring) +
                                            fadeOut(tween(CardExpandSpec.FADE_MILLIS)),
                                    ) {
                                        // 入场统一走 CardRevealSpec；收拢退场仍由外面的 AnimatedVisibility 负责
                                        val cardReveal = rememberCardRevealState()
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .cardRevealLayer(cardReveal)
                                        ) {
                                            CourseGroupCard(
                                                group = group,
                                                onEdit = { g ->
                                                    editingGroup = g
                                                    showEditCourseSheet = true
                                                }
                                            )
                                            Button(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 12.dp)
                                                    .height(50.dp),
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.Confirm
                                                    )
                                                    pendingDeleteGroup = group
                                                    showDeleteDialog = true
                                                },
                                                colors = if (isDark) ButtonDefaults.buttonColors(
                                                    color = Color(0xFF2A2A2A)
                                                )
                                                else ButtonDefaults.buttonColors(),
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Delete,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = Color(0xFFF44336)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    CommonStr.delete(),
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFFF44336)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OverlayDialog(
                        title = CourseEditStr.selectColorTitle(),
                        show = showColorDialog,
                        liquidGlassBackdrop = dialogGlass,
                        onDismissRequest = { showColorDialog = false }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ColorPalette(
                                color = customColor,
                                onColorChanged = { customColor = it },
                                cornerRadius = 20.dp,
                                indicatorRadius = 12.dp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    text = CommonStr.cancel(),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showColorDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    text = CommonStr.ok(),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        selectedColor =
                                            (customColor.alpha * 255).toInt().toLong() shl 24 or
                                                    ((customColor.red * 255).toInt()
                                                        .toLong() shl 16) or
                                                    ((customColor.green * 255).toInt()
                                                        .toLong() shl 8) or
                                                    (customColor.blue * 255).toInt().toLong()
                                        showColorDialog = false
                                    },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    OverlayDialog(
                        title = CourseEditStr.deleteCourse(),
                        summary = CourseEditStr.deleteCourseConfirm(
                            pendingDeleteGroup?.courses?.firstOrNull()?.name ?: ""
                        ),
                        show = showDeleteDialog,
                        liquidGlassBackdrop = dialogGlass,
                        onDismissRequest = { showDeleteDialog = false }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                text = CommonStr.cancel(),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showDeleteDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                text = CommonStr.delete(),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showDeleteDialog = false
                                    pendingDeleteGroup?.let { group ->
                                        pendingDeleteCourseIds = group.courses.map { it.id }
                                        deletingGroupId = group.key.uniqueKey()
                                    }
                                },
                                textColor = Color(0xFFF44336),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    AddEditCourseBottomSheet(
                        show = showAddCourseSheet,
                        courses = courses,
                        liquidGlassBackdrop = dialogGlass,
                        onDismissRequest = { showAddCourseSheet = false },
                        onConfirm = { newCourse ->
                            pendingAddCourse = newCourse
                        },
                        getOccupiedWeeks = { dow, ss, es, excludeIds, startTime, endTime ->
                            getOccupiedWeeks(dow, ss, es, excludeIds, startTime, endTime)
                        },
                        sectionTimes = sectionTimes
                    )

                    AddEditCourseBottomSheet(
                        show = showEditCourseSheet,
                        courses = editingGroup?.courses ?: emptyList(),
                        liquidGlassBackdrop = dialogGlass,
                        editCourse = editingGroup?.courses?.first(),
                        onDismissRequest = {
                            showEditCourseSheet = false
                            editingGroup = null
                        },
                        onConfirm = { updatedCourse ->
                            editingGroup?.courses?.forEach { old ->
                                onCourseUpdated(
                                    old.copy(
                                        classroom = updatedCourse.classroom,
                                        teacher = updatedCourse.teacher,
                                        dayOfWeek = updatedCourse.dayOfWeek,
                                        startSection = updatedCourse.startSection,
                                        endSection = updatedCourse.endSection,
                                        startWeek = updatedCourse.startWeek,
                                        endWeek = updatedCourse.endWeek,
                                        weekType = updatedCourse.weekType,
                                        selectedWeeks = updatedCourse.selectedWeeks,
                                        isCustomTime = updatedCourse.isCustomTime,
                                        customStartTime = updatedCourse.customStartTime,
                                        customEndTime = updatedCourse.customEndTime,
                                        // ⚠ 逐字段 copy：新加的可编辑字段都要在这里补一行，
                                        //   漏了就是「表单里能填、点确定后没保存」
                                        note = updatedCourse.note,
                                        // 备注文本变了 = 重设「全部周」备注，这条记录自己的
                                        // 单周覆盖跟着清掉；没改就原样保留
                                        weekNotes = if (updatedCourse.note != old.note) {
                                            emptyMap()
                                        } else {
                                            old.weekNotes
                                        },
                                        lastModified = Clock.System.now().toEpochMilliseconds()
                                    )
                                )
                            }
                            showEditCourseSheet = false
                            editingGroup = null
                        },
                        getOccupiedWeeks = { dow, ss, es, excludeIds, startTime, endTime ->
                            getOccupiedWeeks(dow, ss, es, excludeIds, startTime, endTime)
                        },
                        sectionTimes = sectionTimes
                    )
                }
            }
        }
    }
}

// ===================== Course Group Card =====================

@Composable
private fun CourseGroupCard(
    group: CourseGroup,
    onEdit: (CourseGroup) -> Unit
) {
    val course = group.courses.first()
    val dayLabels = listOf(
        "",
        CommonStr.weekdayMon(),
        CommonStr.weekdayTue(),
        CommonStr.weekdayWed(),
        CommonStr.weekdayThu(),
        CommonStr.weekdayFri(),
        CommonStr.weekdaySat(),
        CommonStr.weekdaySun(),
    )
    val weekText = course.weekTextLocalized().ifEmpty { CommonStr.notSet() }
    val sectionText = course.timeDisplayTextLocalized().ifEmpty { CommonStr.notSet() }
    // 这里只认「所有周」那条备注：编辑表单里改的也是它
    val noteText = course.note

    Column(modifier = Modifier.fillMaxWidth()) {
        // 分组标题用「周几 + 时间」而非周次：列表按时段排序，
        // 周次作标题会连续重名、看不出是哪天的课
        SmallTitle(
            text = if (course.dayOfWeek in 1..7) {
                "${dayLabels[course.dayOfWeek]} $sectionText"
            } else {
                CourseEditStr.notSetTime()
            }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onEdit(group) }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CourseEditStr.location(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = course.classroom.ifBlank { CommonStr.notSet() },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 14.dp,
                            // 下面接备注行时不留 17dp，否则卡片底部会多出一条缝
                            bottom = if (noteText.isNotBlank()) 14.dp else 17.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CourseEditStr.teacher(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = course.teacher.ifBlank { CommonStr.notSet() },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End
                    )
                }

                // 备注跟在教师后面，只显示「所有周」那条（单周备注归详情弹窗，
                // 这里混着列出来反而看不出哪条属于哪一周）
                if (noteText.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 17.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = NoteStr.noteLabel(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = noteText,
                            modifier = Modifier.fillMaxWidth(0.8f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(0.5.dp)
                        .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CourseEditStr.classTime(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (course.dayOfWeek > 0) "${dayLabels[course.dayOfWeek]} $sectionText" else sectionText,
                        modifier = Modifier.fillMaxWidth(0.8f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End
                    )
                }


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CourseEditStr.classWeeks(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = weekText,
                        modifier = Modifier.fillMaxWidth(0.8f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        if (group.courses.size > 1) {
            Text(
                text = CourseEditStr.coursesShareConfig(group.courses.size),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 32.dp, top = 4.dp)
            )
        }
    }
}

// ===================== Course Group Card + Delete (Tablet) =====================


