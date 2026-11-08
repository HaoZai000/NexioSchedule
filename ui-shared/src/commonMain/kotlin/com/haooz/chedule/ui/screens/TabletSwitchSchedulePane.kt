/** 平板切换课表：左右分栏 */
package com.haooz.chedule.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SwitchStr
import com.haooz.chedule.ui.activities.SwitchScheduleScreen
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.components.LocalPageStartPadding
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 平板切换课表：左右分栏。
 * 左栏 = 切换课表列表（embedded，无卡片形变）；右栏暂留空。
 * 顶栏遮罩与标题栏按钮玻璃随滚动显隐，曲线对齐设置页。
 */
@Composable
fun TabletSwitchSchedulePane(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    liquidGlassBackdrop: GlassBackdrop?,
    /** 分享执行：由 Android 宿主注入（ScheduleExport.performScheduleShare 是 Android 专有）；
     *  内嵌编辑模式的底栏「分享」按钮会渲染，须转发给左栏 SwitchScheduleScreen */
    onShareSchedule: (scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit = { _, _ -> },
    /** 分享前置检查（课表是否为空）：宿主注入（buildShareScheduleMap 依赖 Gson=R2，留在宿主） */
    isScheduleShareable: (scheduleName: String) -> Boolean = { true },
) {
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    // 滚动量：与设置页左栏同式（index*8000+offset）
    var leftScrollPx by remember { mutableFloatStateOf(0f) }
    // 标题栏按钮驱动的弹窗开关
    val showAddDialog = remember { mutableStateOf(false) }
    val showAddFolderDialog = remember { mutableStateOf(false) }
    // 编辑模式：左上角 Close 退出
    val isEditMode = remember { mutableStateOf(false) }
    // 右栏预览：跟随当前选中课表；节数/上中晚分段与主课表同一套配置
    // （原 `val context = LocalContext.current` 取了没用 —— 死变量，迁 :ui-shared 时删掉）
    val repository = remember { CourseRepository() }
    val previewName by scheduleViewModel.currentScheduleName.collectAsState()
    val currentCourses by viewModel.courses.collectAsState()
    val previewCourses = remember(previewName, currentCourses) {
        repository.getCoursesForSchedule(previewName)
    }
    // 预览当前课表时用 SettingsViewModel 实时值（与课程表 tab 一致）；其它课表读该课表绑定的时间配置
    val currentName by scheduleViewModel.currentScheduleName.collectAsState()
    val liveMorning by settingsViewModel.morningSections.collectAsState()
    val liveAfternoon by settingsViewModel.afternoonSections.collectAsState()
    val liveEvening by settingsViewModel.eveningSections.collectAsState()
    val previewSections = remember(previewName, currentName, liveMorning, liveAfternoon, liveEvening) {
        if (previewName == currentName) {
            Triple(liveMorning, liveAfternoon, liveEvening)
        } else {
            Triple(
                repository.getMorningSections(previewName),
                repository.getAfternoonSections(previewName),
                repository.getEveningSections(previewName),
            )
        }
    }

    // 页面底色铺到屏幕边缘（含侧栏下），内容整体避让 —— background 在 padding 之前。
    val paneSurface = MiuixTheme.colorScheme.surface
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(paneSurface)
            .then(LocalPageStartPadding.current)
    ) {
        // 左栏固定为屏宽 0.39，与课程管理/设置分栏一致
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

        // 左栏内容层：供顶栏渐变模糊采样
        val leftBackdrop = rememberLayerBackdrop()
        // 顶栏遮罩与标题栏按钮共用同一 alpha（设置页同款）
        val maskAlpha = rememberPaneMaskAlpha(-leftScrollPx)

        Row(modifier = Modifier.fillMaxSize()) {
            // —— 左栏：切换课表列表 ——
            Box(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight()
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
                    SwitchScheduleScreen(
                        embedded = true,
                        onScheduleChanged = {
                            viewModel.reloadCourses()
                            settingsViewModel.refreshSettings()
                            scope.launch(ioDispatcher) {
                                scheduleViewModel.refreshScheduleList()
                            }
                        },
                        onShareSchedule = onShareSchedule,
                        isScheduleShareable = isScheduleShareable,
                        contentTopPadding = chromeTop,
                        onScrollYChanged = { leftScrollPx = it.toFloat().coerceAtLeast(0f) },
                        externalShowAddDialog = showAddDialog,
                        externalShowAddFolderDialog = showAddFolderDialog,
                        externalIsEditMode = isEditMode,
                    )
                }
                TabletPaneTopChrome(
                    backdrop = leftBackdrop,
                    maskHeight = maskHeight,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                // 标题栏：编辑模式左上 Close，居中标题，右侧添加/文件夹
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .offset(y = topInset)
                        .height(collapsedH),
                ) {
                    if (isEditMode.value) {
                        LiquidTopBarButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                isEditMode.value = false
                            },
                            backdrop = leftBackdrop,
                            icon = MiuixIcons.Normal.Close,
                            contentDescription = CommonStr.close(),
                            iconSize = 24.dp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp),
                            backdropAlpha = maskAlpha,
                            shadowAlpha = maskAlpha,
                        )
                    }
                    Text(
                        text = if (isEditMode.value) SwitchStr.editSchedule() else SwitchStr.switchSchedule(),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Center),
                        // 标题由 Box 的 align(Center) 居中摆位，落位也按中心收放
                        textAlign = TextAlign.Center,
                    )
                    if (!isEditMode.value) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiquidTopBarButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showAddDialog.value = true
                            },
                            backdrop = leftBackdrop,
                            icon = MiuixIcons.Add,
                            contentDescription = CommonStr.add(),
                            iconSize = 24.dp,
                            backdropAlpha = maskAlpha,
                            shadowAlpha = maskAlpha,
                        )
                        LiquidTopBarButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showAddFolderDialog.value = true
                            },
                            backdrop = leftBackdrop,
                            icon = MiuixIcons.AddFolder,
                            contentDescription = SwitchStr.newFolder(),
                            iconSize = 26.dp,
                            backdropAlpha = maskAlpha,
                            shadowAlpha = maskAlpha,
                        )
                    }
                    }
                }
            }

            // —— 右栏：选中课表的学期课表预览 ——
            Box(
                modifier = Modifier
                    .width(rightWidth)
                    .fillMaxHeight()
                    .background(surfaceColor)
            ) {
                // 右栏内容层：先铺表面底色再采样，避免糊层透底
                val rightSurface = surfaceColor
                val rightBackdrop = rememberLayerBackdrop {
                    drawRect(rightSurface)
                    drawContent()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 无材质档：玻璃全关，采样录屏层不挂
                        .then(
                            if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                            else Modifier.layerBackdrop(rightBackdrop)
                        )
                ) {
                    SemesterSchedulePreview(
                        courses = previewCourses,
                        scheduleName = previewName,
                        morningSections = previewSections.first,
                        afternoonSections = previewSections.second,
                        eveningSections = previewSections.third,
                        contentTopPadding = chromeTop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                TabletPaneTopChrome(
                    backdrop = rightBackdrop,
                    maskHeight = maskHeight,
                    modifier = Modifier.align(Alignment.TopStart),
                )
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
    }
}

/**
 * 顶栏遮罩 / 标题栏按钮共用的 alpha，判定与设置页 [TabletSettingsScreen] 完全一致：
 * - contentOffset（向下滚为负）超过 10dp
 * - 或未滚动时的底部越界
 */
@Composable
private fun rememberPaneMaskAlpha(contentOffset: Float): Float {
    val density = LocalDensity.current
    val overScroll = LocalOverScrollState.current
    val scrollThresholdPx = with(density) { 10.dp.toPx() }
    val showMask =
        contentOffset < -scrollThresholdPx ||
            (contentOffset >= 0f && overScroll.offset < 0f)
    val maskAnim = remember { Animatable(0f) }
    LaunchedEffect(showMask) {
        val spec =
            if (showMask) folmeSpring(damping = 1.0f, response = 0.6f)
            else folmeSpring<Float>(damping = 1.0f, response = 0.4f)
        maskAnim.animateTo(
            targetValue = if (showMask) 1f else 0f,
            animationSpec = spec,
        )
    }
    return maskAnim.value
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

private val DayLabels = listOf(
    SwitchStr.dayHeaderMon,
    SwitchStr.dayHeaderTue,
    SwitchStr.dayHeaderWed,
    SwitchStr.dayHeaderThu,
    SwitchStr.dayHeaderFri,
    SwitchStr.dayHeaderSat,
    SwitchStr.dayHeaderSun,
)

/** 学期课表预览：紧凑周网格 */
@Composable
private fun SemesterSchedulePreview(
    courses: List<Course>,
    scheduleName: String,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    contentTopPadding: Dp,
    modifier: Modifier = Modifier,
) {
    // 实际节数：配置为基准，并覆盖课程用到的最大节次（避免课在配置之外被吃掉）
    val maxCourseSection = remember(courses) {
        courses.maxOfOrNull { it.endSection.coerceAtLeast(it.startSection) } ?: 0
    }
    val configTotal = morningSections + afternoonSections + eveningSections
    val totalSections = maxOf(configTotal, maxCourseSection).coerceAtLeast(1)

    val dayLabelHeight = 28.dp
    val breakHeight = 24.dp
    val sideWidth = 20.dp
    val corner = 10.dp
    val emptyCellMinHeight = 48.dp
    val cellHPadding = 2.dp
    val cellVPadding = 2.dp

    val isDark = isAppDarkTheme()
    val gridLine = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val headerText = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val emptyHint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val scrollState = rememberScrollState()

    // (day, startSection) -> 该格课程列表
    val slotMap = remember(courses) {
        val map = HashMap<Pair<Int, Int>, MutableList<Course>>()
        courses.forEach { c ->
            if (c.dayOfWeek in 1..7 && c.startSection >= 1) {
                map.getOrPut(c.dayOfWeek to c.startSection) { mutableListOf() }.add(c)
            }
        }
        map.values.forEach { list -> list.sortBy { it.name } }
        map
    }

    val hasCourses = courses.isNotEmpty()
    // 严格按上午/下午/晚上三段渲染；配置外多出的节次追加在末尾
    val extraSections = (totalSections - configTotal).coerceAtLeast(0)
    val periodBands = buildList {
        if (morningSections > 0) add(morningSections)
        if (afternoonSections > 0) add(afternoonSections)
        if (eveningSections > 0) add(eveningSections)
        if (extraSections > 0) add(extraSections)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .overScrollVertical()
            .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
            .verticalScroll(scrollState)
            .padding(start = 20.dp, end = 20.dp, top = contentTopPadding, bottom = 60.dp),
    ) {
        Text(
            text = scheduleName,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (hasCourses) SwitchStr.semesterPreview() else SwitchStr.noCourses(),
            fontSize = 14.sp,
            color = if (hasCourses) headerText else emptyHint,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (!hasCourses) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = SwitchStr.scheduleEmptyHint(),
                fontSize = 15.sp,
                color = emptyHint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        // 表头
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(sideWidth)
                    .height(dayLabelHeight),
            )
            DayLabels.forEach { label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dayLabelHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = headerText,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // 每段独立绘制，段间留空；节次号全局连续
        var sectionNo = 1
        periodBands.forEachIndexed { bandIndex, bandCount ->
            if (bandIndex > 0) {
                Spacer(Modifier.height(breakHeight))
            }
            repeat(bandCount) {
                val section = sectionNo++
                // 行高由内容决定；空格 defaultMinSize 撑下限、fillMaxHeight 随行拉高
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .width(sideWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = section.toString(),
                            fontSize = 12.sp,
                            color = headerText,
                            textAlign = TextAlign.Center,
                        )
                    }

                    DayLabels.forEachIndexed { dayIndex, _ ->
                        val day = dayIndex + 1
                        val stack = slotMap[day to section]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = cellHPadding, vertical = cellVPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (stack != null) {
                                // 单课撑满当行；多课按文字高度堆叠；整格最低 emptyCellMinHeight
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .defaultMinSize(minHeight = emptyCellMinHeight),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (stack.size == 1) {
                                        PreviewCourseChip(
                                            course = stack[0],
                                            corner = corner,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(),
                                        )
                                    } else {
                                        stack.forEach { course ->
                                            PreviewCourseChip(
                                                course = course,
                                                corner = corner,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            } else {
                                // 空格：最低 emptyCellMinHeight，随行拉高
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = emptyCellMinHeight)
                                        .fillMaxHeight()
                                        .clip(ContinuousRoundedRectangle(corner))
                                        .background(gridLine.copy(alpha = gridLine.alpha * 0.35f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 课卡：高度随文字变化，同一格多门课上下堆叠 */
@Composable
private fun PreviewCourseChip(
    course: Course,
    corner: Dp,
    modifier: Modifier = Modifier,
) {
    val base = Color(course.colorRes)
    Column(
        modifier = modifier
            .clip(ContinuousRoundedRectangle(corner))
            .background(base.copy(alpha = 0.88f))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = course.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val weekText = course.weekTextLocalized()
        if (weekText.isNotEmpty()) {
            Text(
                text = weekText,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
