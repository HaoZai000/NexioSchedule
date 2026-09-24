/** 平板课程管理：左右分栏。左栏复用课程管理列表，右栏复用课程编辑页（自带标题与顶栏模糊） */
package com.haooz.chedule.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.activities.CourseManageScreen
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 平板课程管理：左右分栏，两侧内容均复用原有页面 */
@Composable
fun TabletCourseManagePane(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    liquidGlassBackdrop: Backdrop?,
) {
    val courses by viewModel.courses.collectAsState()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()

    // 选中课程名：默认第一门；被删掉后回落到第一门
    val names = remember(courses) { courses.map { it.name }.distinct().sorted() }
    var selectedName by remember { mutableStateOf<String?>(null) }
    if (selectedName == null || (names.isNotEmpty() && selectedName !in names)) {
        selectedName = names.firstOrNull()
    }
    val selectedCourses = remember(courses, selectedName) {
        courses.filter { it.name == selectedName }
    }

    var leftScroll by remember { mutableFloatStateOf(0f) }

    // 长按菜单：编辑 / 删除（对齐手机课程管理）
    var menuVisible by remember { mutableStateOf(false) }
    var menuCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var menuPosition by remember { mutableStateOf(Offset.Zero) } // 相对分栏根节点
    var menuHeight by remember { mutableFloatStateOf(0f) }
    var paneOrigin by remember { mutableStateOf(Offset.Zero) }
    // 编辑：交给列表页自带的编辑弹窗
    var pendingEditCourse by remember { mutableStateOf<Course?>(null) }
    // 删除：交给列表页自带的确认弹窗
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteCourses by remember { mutableStateOf<List<Course>>(emptyList()) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val listWidth = maxWidth * 0.42f
        val rightWidth = maxWidth - listWidth
        val topInset = WindowInsets.systemBars.only(WindowInsetsSides.Top)
            .asPaddingValues().calculateTopPadding()
        val collapsedH = CollapsibleTopAppBarDefaults.CollapsedHeight
        // 内容自「状态栏 + 折叠标题」下方开始，与设置页 chromeTop 一致
        val chromeTop = topInset + collapsedH + 12.dp
        val maskHeight = topInset + 80.dp
        val surfaceColor = MiuixTheme.colorScheme.surface
        val dividerColor =
            if (isAppDarkTheme()) Color.White.copy(alpha = 0.1f)
            else Color.Black.copy(alpha = 0.06f)
        val paneWidthPx = with(density) { rightWidth.toPx() }
        val paneHeightPx = with(density) { maxHeight.toPx() }

        // 左栏内容层：供顶栏渐变模糊采样（不含模糊层自身，避免递归）
        val leftBackdrop = rememberLayerBackdrop { drawContent() }
        // 右栏编辑页自带的顶栏模糊采样层，与手机课程管理 Activity 用法一致
        val editorBackdrop = rememberLayerBackdrop()

        Row(modifier = Modifier.fillMaxSize()) {
            // —— 左栏：原课程管理列表 ——
            Box(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight()
                    .onGloballyPositioned { paneOrigin = it.localToRoot(Offset.Zero) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(leftBackdrop)
                ) {
                    CourseManageScreen(
                        scrollBehavior = null,
                        viewModel = viewModel,
                        onCourseClick = { clicked, _, _, _, _, _, _, _ ->
                            menuVisible = false
                            selectedName = clicked.firstOrNull()?.name
                        },
                        onCourseLongPress = { list, left, top, _, _ ->
                            menuCourses = list
                            menuPosition = Offset(left - paneOrigin.x, top - paneOrigin.y)
                            menuVisible = true
                        },
                        onNewCourseCreated = { viewModel.addCourse(it) },
                        onCourseUpdated = { oldName, updated ->
                            viewModel.updateCoursesByName(oldName, updated)
                        },
                        onDeleteCourses = { list -> list.forEach { viewModel.deleteCourse(it.id) } },
                        pendingEditCourse = pendingEditCourse,
                        onEditDismiss = { pendingEditCourse = null },
                        deleteConfirmShow = showDeleteConfirm,
                        deleteConfirmCourses = pendingDeleteCourses,
                        onDeleteConfirmDismiss = { showDeleteConfirm = false },
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        contentTopPadding = chromeTop,
                        columnsOverride = 2,
                        onScrollYChanged = { leftScroll = it.toFloat() },
                        // 选中卡加课程色描边
                        selectedCourseName = selectedName,
                    )
                }
                TabletPaneTopChrome(
                    scrolledPx = leftScroll,
                    backdrop = leftBackdrop,
                    maskHeight = maskHeight,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                // 标题只画在左栏：折叠标题区位于状态栏之下，与设置页固定标题同式
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .offset(y = topInset)
                        .height(collapsedH),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "课程管理",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }

            // 分栏线：与设置页一致（1dp，白 0.1 / 黑 0.06）
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(dividerColor)
            )

            // —— 右栏：原课程编辑页（自带课程标题、添加按钮与顶栏模糊） ——
            Box(
                modifier = Modifier
                    .width(rightWidth)
                    .fillMaxHeight()
                    .background(surfaceColor)
            ) {
                if (selectedCourses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (names.isEmpty()) "暂无课程" else "请选择左侧课程",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    // 切课程时重建编辑页状态，避免沿用上一门课程的选中色/弹窗状态
                    key(selectedName) {
                        CourseEditScreen(
                            courses = selectedCourses,
                            cardLeft = 0f,
                            cardTop = 0f,
                            cardWidth = paneWidthPx,
                            cardHeight = paneHeightPx,
                            screenWidth = paneWidthPx,
                            screenHeight = paneHeightPx,
                            screenCornerRadius = 0f,
                            cardSnapshot = null,
                            cardColor = Color(selectedCourses.first().colorRes),
                            onBackStart = {},
                            onBack = {},
                            onCourseUpdated = { viewModel.updateCourse(it) },
                            onCourseAdded = { viewModel.addCourse(it) },
                            onDeleteCourse = { viewModel.deleteCourse(it) },
                            getOccupiedWeeks = { day, start, end, exclude, startTime, endTime ->
                                viewModel.getOccupiedWeeks(
                                    dayOfWeek = day,
                                    startSection = start,
                                    endSection = end,
                                    excludeIds = exclude.toSet(),
                                    startTime = startTime,
                                    endTime = endTime,
                                )
                            },
                            liquidGlassBackdrop = editorBackdrop,
                            sectionTimes = sectionTimes,
                            embedded = true,
                            // 弹窗采样全屏层，才能把左栏内容一起虚化
                            dialogBackdrop = liquidGlassBackdrop,
                        )
                    }
                }
            }
        }

        // 长按菜单：编辑 / 删除。菜单与左栏同为分栏根节点的子节点，
        // 位置由卡片 root 坐标减去分栏根坐标得到，与手机端一致；
        // 玻璃采样左栏内容层（兄弟节点，不含菜单自身）。
        // 注意不能包在 if 里：否则关闭时组件被移除，退场动画不会播放。
        if (menuVisible) {
            // 点击空白关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { menuVisible = false }
            )
        }
        ShortcutMenu(
            show = menuVisible,
            items = listOf(
                ShortcutMenuItem(
                    icon = MiuixIcons.Edit,
                    label = "编辑",
                    onClick = {
                        menuVisible = false
                        pendingEditCourse = menuCourses.firstOrNull()
                    },
                ),
                ShortcutMenuItem(
                    icon = MiuixIcons.Delete,
                    label = "删除",
                    onClick = {
                        menuVisible = false
                        pendingDeleteCourses = menuCourses
                        showDeleteConfirm = true
                    },
                ),
            ),
            modifier = Modifier.offset(
                x = with(density) { menuPosition.x.toDp() } - 14.dp,
                y = with(density) { menuPosition.y.toDp() } -
                    with(density) { menuHeight.toDp() } + 4.dp,
            ),
            backdrop = leftBackdrop,
            onDismiss = { menuVisible = false },
            onMeasuredSize = { _, height -> menuHeight = height.toFloat() },
        )
    }
}

/** 顶栏表面色渐变遮罩 + 常驻渐变模糊：与设置页 TabletPaneTopChrome 一致 */
@Composable
private fun TabletPaneTopChrome(
    scrolledPx: Float,
    backdrop: Backdrop?,
    maskHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 10.dp.toPx() }
    val showMask = scrolledPx > thresholdPx
    val maskAnim = remember { Animatable(0f) }
    LaunchedEffect(showMask) {
        maskAnim.animateTo(
            targetValue = if (showMask) 1f else 0f,
            animationSpec = tween(if (showMask) 200 else 150),
        )
    }
    val gradientColor =
        if (rememberAppSettingDark()) Color.Black else Color.White

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(maskHeight)
    ) {
        if (backdrop != null) {
            ProgressiveBlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                height = maskHeight,
                blurAlpha = 1f,
                content = {},
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .graphicsLayer { alpha = maskAnim.value }
                .background(
                    Brush.verticalGradient(
                        0f to gradientColor.copy(alpha = 0.85f),
                        0.45f to gradientColor.copy(alpha = 0.55f),
                        0.7f to gradientColor.copy(alpha = 0.32f),
                        0.85f to gradientColor.copy(alpha = 0.14f),
                        0.93f to gradientColor.copy(alpha = 0.05f),
                        1f to Color.Transparent,
                    )
                )
        )
    }
}