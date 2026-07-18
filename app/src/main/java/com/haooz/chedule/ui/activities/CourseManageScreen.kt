/** 课程管理页面 - Screen */
package com.haooz.chedule.ui.activities

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.haooz.chedule.viewmodel.CourseViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor

@Composable
fun CourseManageScreen(
    onBack: () -> Unit,
    viewModel: CourseViewModel = viewModel(),
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    onCourseClick: (
        courses: List<com.haooz.chedule.data.Course>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        snapshot: Bitmap?
    ) -> Unit = { _, _, _, _, _, _ -> }
) {
    val courses by viewModel.courses.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }
    val hapticFeedback = LocalHapticFeedback.current

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass" && liquidGlassBackdrop != null
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (!isLiquidGlass) {
                    TopAppBar(
                        modifier = if (blurAlpha > 0f) {
                            Modifier.textureBlur(backdrop = backdrop, shape = RectangleShape, colors = topAppBarColors!!)
                        } else Modifier,
                        color = topBarColor,
                        title = "课程管理", largeTitle = "课程管理",
                        scrollBehavior = scrollBehavior,
                        navigationIconPadding = 20.dp,
                        navigationIcon = {
                            IconButton(onClick = { onBack() }) {
                                Icon(
                                    MiuixIcons.Back,
                                    contentDescription = "返回",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    )
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
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
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
                        start = 16.dp,
                        top = if (isLiquidGlass) paddingValues.calculateTopPadding() + 64.dp else paddingValues.calculateTopPadding() + 8.dp,
                        end = 16.dp,
                        bottom = 60.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    val groupedCourses = courses
                        .groupBy { it.name }
                        .toSortedMap(compareBy { it })

                    items(groupedCourses.entries.toList()) { (courseName, courseList) ->
                        val representative = courseList.first()
                        val daySectionInfo = courseList
                            .groupBy { "${it.dayOfWeek}_${it.startSection}_${it.endSection}" }
                            .values
                            .map { it.first() }
                            .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
                            .map {
                                val day = dayNames.getOrElse(it.dayOfWeek) { "?" }
                                "${day}${it.startSection}-${it.endSection}节"
                            }
                            .joinToString("、")

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

                        CourseManageCard(
                            courseName = courseName,
                            teacher = teachers,
                            classroom = classrooms,
                            color = Color(representative.colorRes),
                            daySectionInfo = daySectionInfo,
                            onClick = { left, top, width, height, snapshot ->
                                onCourseClick(courseList, left, top, width, height, snapshot)
                            }
                        )
                    }
                }
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
    cardAlpha: Float = 0.15f,
    daySectionInfo: String,
    onClick: (left: Float, top: Float, width: Float, height: Float, snapshot: Bitmap?) -> Unit
) {
    var cardLeft by remember { mutableStateOf(0f) }
    var cardTop by remember { mutableStateOf(0f) }
    var cardWidth by remember { mutableStateOf(0f) }
    var cardHeight by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = cardAlpha))
            .onGloballyPositioned { coordinates ->
                val position = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                val size = coordinates.size
                cardLeft = position.x
                cardTop = position.y
                cardWidth = size.width.toFloat()
                cardHeight = size.height.toFloat()
            }
            .clickable {
                onClick(
                    cardLeft,
                    cardTop,
                    cardWidth,
                    cardHeight,
                    null
                )
            }
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
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
        Text(
            text = daySectionInfo,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}
