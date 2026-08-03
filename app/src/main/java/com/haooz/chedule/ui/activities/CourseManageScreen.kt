/** 课程管理页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.ui.components.NativeMiuixTextField
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.haooz.chedule.viewmodel.CourseViewModel
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun CourseManageScreen(
    onBack: () -> Unit,
    viewModel: CourseViewModel = viewModel(),
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    hiddenCourseIds: Set<String> = emptySet(),
    shrinkingCourseIds: Set<String> = emptySet(),
    onCourseClick: (
        courses: List<com.haooz.chedule.data.Course>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        snapshot: Bitmap?,
        cardColor: Color,
        cardAlpha: Float
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onNewCourseCreated: (com.haooz.chedule.data.Course) -> Unit = {}
) {
    val hapticFeedback = LocalHapticFeedback.current
    val courses by viewModel.courses.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass" && liquidGlassBackdrop != null
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp
    val blurAlpha = if (!isLiquidGlass) {
        if (listScrollY < 50) 0f else ((listScrollY - 50) / 30f).coerceIn(0f, 0.7f)
    } else 0f
    val topBarColorProgress = if (!isLiquidGlass) ((listScrollY - 50) / 30f).coerceIn(0f, 1f) else 0f
    val topBarColor = if (!isLiquidGlass) {
        if (listScrollY < 50) MiuixTheme.colorScheme.surface
        else {
            val surface = MiuixTheme.colorScheme.surface
            val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
            lerp(surface, target, topBarColorProgress)
        }
    } else MiuixTheme.colorScheme.surface
    val topAppBarColors = if (!isLiquidGlass) {
        BlurDefaults.blurColors(
            blendColors = listOf(
                if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
                else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            ),
            brightness = 0f, contrast = 1f, saturation = 1.2f
        )
    } else null

    val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // 新建课程弹窗状态
    var showNewCourseDialog by remember { mutableStateOf(false) }
    var newCourseName by remember { mutableStateOf("") }
    var newCourseColor by remember { mutableLongStateOf(com.haooz.chedule.data.Course.courseColors.first()) }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(ComposeColor(com.haooz.chedule.data.Course.courseColors.first())) }

    // 新课程入场动画跟踪
    var newlyAddedCourseNames by remember { mutableStateOf(setOf<String>()) }
    var pendingNewCourse by remember { mutableStateOf<com.haooz.chedule.data.Course?>(null) }

    // dialog 关闭后延迟写入数据库并触发动画
    LaunchedEffect(showNewCourseDialog) {
        if (!showNewCourseDialog && pendingNewCourse != null) {
            delay(200.milliseconds)
            val course = pendingNewCourse!!
            onNewCourseCreated(course)
            newlyAddedCourseNames = newlyAddedCourseNames + course.name
            pendingNewCourse = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (!isLiquidGlass) {
                    val topBarModifier = if (blurAlpha > 0f) {
                        Modifier.textureBlur(backdrop = backdrop, shape = RectangleShape, colors = topAppBarColors!!)
                    } else Modifier
                    val navIcon: @Composable () -> Unit = {
                        IconButton(onClick = { onBack() }) {
                            Icon(
                                MiuixIcons.Back,
                                contentDescription = "返回",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    if (isTablet) {
                        SmallTopAppBar(
                            modifier = topBarModifier,
                            color = topBarColor,
                            title = "课程管理",
                            scrollBehavior = scrollBehavior,
                            navigationIconPadding = 20.dp,
                            navigationIcon = navIcon
                        )
                    } else {
                        TopAppBar(
                            modifier = topBarModifier,
                            color = topBarColor,
                            title = "课程管理", largeTitle = "课程管理",
                            scrollBehavior = scrollBehavior,
                            navigationIconPadding = 20.dp,
                            navigationIcon = navIcon
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                val gridState = rememberLazyStaggeredGridState()
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.firstVisibleItemScrollOffset }
                        .collect { offset ->
                            listScrollY = offset
                        }
                }

                val groupedCourses = courses
                    .groupBy { it.name }
                    .toSortedMap(compareBy { it })

                if (groupedCourses.isEmpty()) {
                    // 空状态
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = paddingValues.calculateTopPadding()),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "暂无课程",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "还没有添加任何课程",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(if (isTablet) 4 else 2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize()
                            .overScrollVertical()
                            .scrollEndHaptic(
                                hapticFeedbackType = HapticFeedbackType.TextHandleMove
                            )
                            .then(
                                if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
                            ),
                        contentPadding = PaddingValues(
                            start = tabletHorizontalPadding,
                            top = if (isLiquidGlass) paddingValues.calculateTopPadding() + 64.dp else paddingValues.calculateTopPadding() + 8.dp,
                            end = tabletHorizontalPadding,
                            bottom = 60.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp
                    ) {
                        items(groupedCourses.entries.toList(), key = { it.key }) { (courseName, courseList) ->
                            val isNew = courseName in newlyAddedCourseNames
                            val isShrinking = courseList.any { it.id in shrinkingCourseIds }
                            val scale = remember { Animatable(0.8f) }
                            val alpha = remember { Animatable(0f) }
                            LaunchedEffect(courseName, isShrinking) {
                                if (isNew) {
                                    launch { scale.animateTo(1f, tween(350)) }
                                    launch { alpha.animateTo(1f, tween(300)) }
                                    newlyAddedCourseNames = newlyAddedCourseNames - courseName
                                } else if (isShrinking) {
                                    launch { scale.animateTo(0.8f, tween(300)) }
                                    launch { alpha.animateTo(0f, tween(250)) }
                                } else {
                                    scale.snapTo(1f)
                                    alpha.snapTo(1f)
                                }
                            }

                            val representative = courseList.first()
                            val daySectionInfo = courseList
                                .groupBy { "${it.dayOfWeek}_${it.startSection}_${it.endSection}" }
                                .values
                                .map { it.first() }
                                .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
                                .filter { it.dayOfWeek > 0 && it.startSection > 0 }
                                .joinToString("、") {
                                    val day = dayNames.getOrElse(it.dayOfWeek) { "?" }
                                    "${day}${it.startSection}-${it.endSection}节"
                                }

                            val teachers = courseList
                                .map { it.teacher }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString("/")

                            val classrooms = courseList
                                .map { it.classroom }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString("/")

                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .graphicsLayer {
                                        scaleX = scale.value
                                        scaleY = scale.value
                                        this.alpha = alpha.value
                                    }
                            ) {
                                CourseManageCard(
                                    courseName = courseName,
                                    teacher = teachers,
                                    classroom = classrooms,
                                    color = Color(representative.colorRes),
                                    daySectionInfo = daySectionInfo,
                                    isHidden = courseList.any { it.id in hiddenCourseIds },
                                    onClick = { left, top, width, height, snapshot ->
                                        onCourseClick(courseList, left, top, width, height, snapshot, Color(representative.colorRes), 0.20f)
                                    }
                                )
                            }
                        }

                        items(1) {
                            Box(
                                modifier = Modifier.animateItem()
                            ) {
                                NewCourseCard(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        newCourseName = ""
                                        newCourseColor = com.haooz.chedule.data.Course.courseColors.first()
                                        showNewCourseDialog = true
                                    }
                                )
                            }
                        }
                }
            }
        }
    }
}

    // 新建课程弹窗
    OverlayDialog(
        title = "新建课程",
        show = showNewCourseDialog,
        onDismissRequest = { showNewCourseDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            // 课程名称输入
            NativeMiuixTextField(
                value = newCourseName,
                onValueChange = { newCourseName = it },
                label = "课程名称",

                requestFocus = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 课程颜色选择
            Column(modifier = Modifier.fillMaxWidth()) {
                val allColors = remember { com.haooz.chedule.data.Course.courseColors }
                val colorColumns = 6
                val totalItems = remember(allColors) { allColors.size + 1 }
                val colorRows = remember(totalItems, colorColumns) { (totalItems + colorColumns - 1) / colorColumns }
                Text(
                    text = "课程颜色",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
                for (row in 0 until colorRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (col in 0 until colorColumns) {
                            val colorIndex = row * colorColumns + col
                            if (colorIndex < allColors.size) {
                                val color = allColors[colorIndex]
                                val isSelected = color == newCourseColor
                                var isPressed by remember { mutableStateOf(false) }
                                val primaryColor = MiuixTheme.colorScheme.primary
                                val scale = remember { Animatable(1f) }
                                val borderAlpha by animateFloatAsState(
                                    targetValue = if (isSelected) 1f else 0f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "borderAlpha"
                                )
                                LaunchedEffect(isPressed) {
                                    if (isPressed) {
                                        scale.animateTo(
                                            targetValue = 0.94f,
                                            animationSpec = tween(durationMillis = 100)
                                        )
                                    } else {
                                        scale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(durationMillis = 180)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .graphicsLayer {
                                            scaleX = scale.value
                                            scaleY = scale.value
                                        }
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val anyPressed = event.changes.any { it.pressed }
                                                    isPressed = anyPressed
                                                    if (!anyPressed) {
                                                        newCourseColor = color
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = borderAlpha }
                                            .clip(RoundedRectangle(12.dp))
                                            .background(primaryColor)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(if (isSelected) 2.dp else 0.dp)
                                            .clip(RoundedRectangle(10.dp))
                                            .background(if (isDark) ComposeColor(0xFF242424) else ComposeColor(0xFFFFFFFF))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        Card(
                                            modifier = Modifier.fillMaxSize(),
                                            cornerRadius = 8.dp,
                                            insideMargin = PaddingValues(0.dp),
                                            colors = CardDefaults.defaultColors(
                                                color = Color(color).copy(alpha = if (isDark) 0.22f else 0.16f),
                                                contentColor = ComposeColor.White
                                            ),
                                            onClick = { newCourseColor = color }
                                        ) {}
                                    }
                                }
                            } else if (colorIndex == allColors.size) {
                                // 自定义颜色按钮
                                val isCustomColor = newCourseColor !in allColors
                                val bgColor = if (isDark) ComposeColor(0xFF505050) else ComposeColor(0xFFF7F7F7)
                                val hintColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                val primaryColor = MiuixTheme.colorScheme.primary
                                var isCustomPressed by remember { mutableStateOf(false) }
                                val customScale = remember { Animatable(1f) }
                                val customBorderAlpha by animateFloatAsState(
                                    targetValue = if (isCustomColor) 1f else 0f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "customBorderAlpha"
                                )
                                LaunchedEffect(isCustomPressed) {
                                    if (isCustomPressed) {
                                        customScale.animateTo(
                                            targetValue = 0.94f,
                                            animationSpec = tween(durationMillis = 100)
                                        )
                                    } else {
                                        customScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(durationMillis = 180)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .graphicsLayer {
                                            scaleX = customScale.value
                                            scaleY = customScale.value
                                        }
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val anyPressed = event.changes.any { it.pressed }
                                                    isCustomPressed = anyPressed
                                                    if (!anyPressed) {
                                                        customColor = Color(newCourseColor)
                                                        showCustomColorDialog = true
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = customBorderAlpha }
                                            .clip(RoundedRectangle(12.dp))
                                            .background(primaryColor)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(if (isCustomColor) 2.dp else 0.dp)
                                            .clip(RoundedRectangle(10.dp))
                                            .background(if (isDark) ComposeColor(0xFF242424) else ComposeColor(0xFFFFFFFF))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        Card(
                                            modifier = Modifier.fillMaxSize(),
                                            cornerRadius = 8.dp,
                                            insideMargin = PaddingValues(0.dp),
                                            colors = CardDefaults.defaultColors(
                                                color = bgColor,
                                                contentColor = hintColor
                                            ),
                                            onClick = {
                                                customColor = Color(newCourseColor)
                                                showCustomColorDialog = true
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = androidx.compose.ui.Alignment.Center
                                            ) {
                                                if (isCustomColor) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(RoundedRectangle(6.dp))
                                                            .background(Color(newCourseColor))
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = MiuixIcons.Add,
                                                        contentDescription = "自定义颜色",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = hintColor
                                                    )
                                                }
                                            }
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

            Spacer(modifier = Modifier.height(8.dp))

            // 确认和取消按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showNewCourseDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (newCourseName.isNotBlank()) {
                            val course = com.haooz.chedule.data.Course(
                                id = java.util.UUID.randomUUID().toString(),
                                name = newCourseName.trim(),
                                classroom = "",
                                teacher = "",
                                dayOfWeek = 0,
                                startSection = 0,
                                endSection = 0,
                                startWeek = 0,
                                endWeek = 0,
                                weekType = com.haooz.chedule.data.Course.WEEK_TYPE_ALL,
                                colorRes = newCourseColor
                            )
                            pendingNewCourse = course
                            showNewCourseDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 自定义颜色选择弹窗
    OverlayDialog(
        title = "选择颜色",
        show = showCustomColorDialog,
        onDismissRequest = { showCustomColorDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
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
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showCustomColorDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        newCourseColor = (customColor.alpha * 255).toInt().toLong() shl 24 or
                                ((customColor.red * 255).toInt().toLong() shl 16) or
                                ((customColor.green * 255).toInt().toLong() shl 8) or
                                (customColor.blue * 255).toInt().toLong()
                        showCustomColorDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CourseManageCard(
    courseName: String,
    teacher: String,
    classroom: String,
    color: Color,
    cardAlpha: Float = 0.20f,
    daySectionInfo: String,
    isHidden: Boolean = false,
    onClick: (left: Float, top: Float, width: Float, height: Float, snapshot: Bitmap?) -> Unit
) {
    var cardLeft by remember { mutableFloatStateOf(0f) }
    var cardTop by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableFloatStateOf(0f) }

    Card(
        cornerRadius = 16.dp,
        showIndication = true,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(
            color = if (isHidden) ComposeColor.Transparent else color.copy(alpha = cardAlpha)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val position = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                val size = coordinates.size
                cardLeft = position.x
                cardTop = position.y
                cardWidth = size.width.toFloat()
                cardHeight = size.height.toFloat()
            },
        onClick = {
            onClick(
                cardLeft,
                cardTop,
                cardWidth,
                cardHeight,
                null
            )
        }
    ) {
        Column(modifier = Modifier.graphicsLayer { alpha = if (isHidden) 0f else 1f }) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedRectangle(4.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = courseName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (teacher.isNotBlank()) {
                Text(
                    text = teacher,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            if (classroom.isNotBlank()) {
                Text(
                    text = classroom,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (daySectionInfo.isNotBlank()) {
                Text(
                    text = daySectionInfo,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            } else {
                Text(
                    text = "点击设置时间",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NewCourseCard(
    onClick: () -> Unit
) {
    Card(
        cornerRadius = 16.dp,
        showIndication = true,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(
            color = if (isAppDarkTheme()) ComposeColor(0xFF121212) else ComposeColor(0xFFEEEEEE)
        ),
        modifier = Modifier.fillMaxWidth().height(120.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxSize(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                MiuixIcons.Add,
                contentDescription = "新建课程",
                modifier = Modifier.size(28.dp),
                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "新建课程",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
