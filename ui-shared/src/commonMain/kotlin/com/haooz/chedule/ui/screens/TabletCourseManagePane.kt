/** 平板课程管理：左右分栏。左栏复用课程管理列表，右栏复用课程编辑页（自带标题与顶栏模糊） */
package com.haooz.chedule.ui.screens

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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SwitchStr
import com.haooz.chedule.ui.activities.CourseManageScreen
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.components.LocalPageStartPadding
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.utils.CourseSorting
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 平板课程管理：左右分栏，两侧内容均复用原有页面 */
@Composable
fun TabletCourseManagePane(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    liquidGlassBackdrop: GlassBackdrop?,
) {
    val courses by viewModel.courses.collectAsState()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()

    // 选中课程名：默认第一门；被删掉后回落到第一门
    // 用中文拼音排序，与左栏卡片顺序保持一致（否则左栏看到的顺序和右栏切换顺序对不上）
    val names = remember(courses) {
        courses.map { it.name }.distinct().sortedWith { a, b -> CourseSorting.compareNames(a, b) }
    }
    var selectedName by remember { mutableStateOf<String?>(null) }
    if (selectedName == null || (names.isNotEmpty() && selectedName !in names)) {
        selectedName = names.firstOrNull()
    }
    val selectedCourses = remember(courses, selectedName) {
        courses.filter { it.name == selectedName }
    }

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

    // 页面底色铺到屏幕边缘（含侧栏下），内容整体避让 —— background 在 padding 之前。
    val paneSurface = MiuixTheme.colorScheme.surface
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(paneSurface)
            .then(LocalPageStartPadding.current)
    ) {
        val density = LocalDensity.current
        // 左栏固定为屏宽 0.39，不随侧栏展开/收起变化；剩余宽度全给右栏
        val listWidth = screenWidthDp().dp * 0.39f
        val rightWidth = maxWidth - listWidth
        val topInset = WindowInsets.systemBars.only(WindowInsetsSides.Top)
            .asPaddingValues().calculateTopPadding()
        val collapsedH = CollapsibleTopAppBarDefaults.CollapsedHeight
        // 内容自「状态栏 + 折叠标题」下方开始，与设置页 chromeTop 一致
        val chromeTop = topInset + collapsedH + 24.dp
        val maskHeight = topInset + 80.dp
        val surfaceColor = MiuixTheme.colorScheme.surface
        val dividerColor =
            if (isAppDarkTheme()) Color.White.copy(alpha = 0.1f)
            else Color.Black.copy(alpha = 0.06f)
        val paneWidthPx = with(density) { rightWidth.toPx() }
        val paneHeightPx = with(density) { maxHeight.toPx() }

        // 左栏内容层：供顶栏渐变模糊采样（不含模糊层自身，避免递归）
        val leftBackdrop = rememberLayerBackdrop()
        // 右栏编辑页自带的顶栏模糊采样层，与手机课程管理 Activity 用法一致
        val editorBackdrop = rememberLayerBackdrop()

        Row(modifier = Modifier.fillMaxSize()) {
            // —— 左栏：原课程管理列表 ——
            Box(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight()
                    .onGloballyPositioned { paneOrigin = it.localToRoot(Offset.Zero) }
                    .background(surfaceColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 无材质档：玻璃全关，采样录屏层不挂
                        .then(
                            if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                            else Modifier.layerBackdrop(leftBackdrop)
                        )
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
                        sectionTimes = sectionTimes,
                        // 选中卡加课程色描边
                        selectedCourseName = selectedName,
                    )
                }
                TabletPaneTopChrome(
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
                        text = SwitchStr.courseManage(),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }

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
                            text = if (names.isEmpty()) SwitchStr.noCourses() else SwitchStr.selectCourseHint(),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
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
                            screenCornerRadius = ScreenCornerRadiiPx.Zero,
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
                            // 右栏内容顶距与左栏 chromeTop 对齐（状态栏 + 52 + 24）
                            contentTopPadding = chromeTop,
                        )
                    }
                }
            }
        }
        // 分界线画在 Row 之上：左栏内部有顶栏/底栏模糊层，画在栏内会被糊掉
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = listWidth)
                .width(1.dp)
                .fillMaxHeight()
                .background(dividerColor)
        )

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
                    label = CommonStr.edit(),
                    onClick = {
                        menuVisible = false
                        pendingEditCourse = menuCourses.firstOrNull()
                    },
                ),
                ShortcutMenuItem(
                    icon = MiuixIcons.Delete,
                    label = CommonStr.delete(),
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

/** 顶栏常驻表面色渐变遮罩 + 常驻渐变模糊：与设置页 TabletPaneTopChrome 一致 */
@Composable
private fun TabletPaneTopChrome(
    backdrop: GlassBackdrop?,
    maskHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(maskHeight)
    ) {
        // 只保留渐变模糊，不再叠表面色遮罩（压暗层已全局移除）
        if (backdrop != null) {
            ProgressiveBlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                height = maskHeight,
                blurAlpha = 1f,
                content = {},
            )
        }
    }
}