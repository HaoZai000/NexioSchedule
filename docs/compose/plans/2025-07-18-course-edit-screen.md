# Course Edit Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a course modification page that allows editing course segments (different time slots/weeks) with morphing animation similar to CourseDetailScreen.

**Architecture:** New `CourseEditScreen` composable with morphing animation from card to full screen. Course segments are grouped by time slot (day + section + week range) and displayed as editable cards. Each card opens an edit dialog similar to AddCourseDialog.

**Tech Stack:** Kotlin, Jetpack Compose, Miuix UI Library, Material3

## Global Constraints

- Package: `com.haooz.chedule.ui.activities`
- UI Library: Miuix (top.yukonga.miuix.kmp)
- Theme: Use MiuixTheme for colors and text styles
- Animation: Reference CourseDetailScreen's AnimState and AnimClipShape pattern
- Language: Chinese UI text (课程名称, 教师, 教室, etc.)

---

## Task 1: Create CourseEditScreen.kt with Animation Foundation

**Covers:** Basic page structure with morphing animation

**Files:**
- Create: `C:\Users\43908\Desktop\KeCB\app\src\main\java\com\haooz\chedule\ui\activities\CourseEditScreen.kt`

**Interfaces:**
- Consumes: Course data, card position (left, top, width, height), screen dimensions
- Produces: CourseEditScreen composable

- [ ] **Step 1: Create the file with basic structure**

```kotlin
/** 课程修改页面 - Screen */
package com.haooz.chedule.ui.activities

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.oobe.OobeCubicOutEasing
import com.haooz.chedule.ui.oobe.OobeQuartOutEasing
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

private data class AnimState(
    val bgAlpha: Float,
    val snapshotAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float
)

private class AnimClipShape(
    private val screenWidth: Float,
    private val screenCornerRadiusPx: Float,
    private val startCornerRadiusPx: Float,
    private val animState: androidx.compose.runtime.State<AnimState>
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val s = animState.value
        val radiusPx = if (s.progress >= 1f) 0f
        else startCornerRadiusPx + (screenCornerRadiusPx - startCornerRadiusPx) * s.progress
        val radiusDp = (radiusPx / s.scale / density.density).dp
        return RoundedRectangle(radiusDp).createOutline(
            androidx.compose.ui.geometry.Size(screenWidth, s.clipBottom),
            layoutDirection,
            density
        )
    }
}

@Composable
fun CourseEditScreen(
    courseName: String,
    courses: List<Course>,
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    screenCornerRadius: Float,
    cardSnapshot: Bitmap?,
    sectionTimes: Map<Int, String>,
    onBackStart: () -> Unit,
    onBack: () -> Unit,
    onCourseUpdated: (Course) -> Unit,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null
) {
    // Animation state similar to CourseDetailScreen
    val density = LocalDensity.current
    val animProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val startCornerRadiusPx = 20f * density.density
    val morphOpenEase = OobeQuartOutEasing
    val morphExitEase = OobeCubicOutEasing

    // Group courses by time slot
    val groupedCourses = remember(courses) {
        courses.groupBy { course ->
            CourseGroupKey(
                dayOfWeek = course.dayOfWeek,
                startSection = course.startSection,
                endSection = course.endSection,
                startWeek = course.startWeek,
                endWeek = course.endWeek,
                weekType = course.weekType,
                selectedWeeks = course.selectedWeeks
            )
        }.map { (key, courseList) ->
            CourseGroup(
                key = key,
                courses = courseList,
                representative = courseList.first()
            )
        }.sortedWith(compareBy({ it.key.dayOfWeek }, { it.key.startSection }))
    }

    // Back handler
    BackHandler {
        onBackStart()
        scope.launch {
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 380,
                    easing = morphExitEase
                )
            )
            onBack()
        }
    }

    // Enter animation
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 620,
                easing = morphOpenEase
            )
        )
    }

    val animState = remember {
        derivedStateOf {
            val p = animProgress.value
            val bgAlpha = (p * 0.5f).coerceIn(0f, 0.5f)
            val snapAlpha = (1f - p * 3f).coerceIn(0f, 1f)
            val contAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
            val scale = cardWidth / screenWidth + (1f - cardWidth / screenWidth) * p
            val translationX = (cardLeft + cardWidth / 2f - screenWidth / 2f) * (1f - p)
            val translationY = cardTop * (1f - p)
            val clipBottom = cardHeight + 20 + (screenHeight - cardHeight - 20) * p
            AnimState(bgAlpha, snapAlpha, contAlpha, translationX, translationY, scale, clipBottom, p)
        }
    }

    // Main content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isAppDarkTheme()) ComposeColor(0xFF2C2C2C).copy(alpha = animState.value.bgAlpha)
                else ComposeColor.Black.copy(alpha = animState.value.bgAlpha)
            )
    ) {
        // Content will be added in next task
    }
}

// Helper data classes for course grouping
private data class CourseGroupKey(
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int,
    val selectedWeeks: List<Int>
)

private data class CourseGroup(
    val key: CourseGroupKey,
    val courses: List<Course>,
    val representative: Course
)
```

- [ ] **Step 2: Verify file compiles**

Run: Build the project to verify syntax
Expected: No compilation errors

---

## Task 2: Add Content Animation and UI Structure

**Covers:** Animated content display with morphing effect

**Files:**
- Modify: `C:\Users\43908\Desktop\KeCB\app\src\main\java\com\haooz\chedule\ui\activities\CourseEditScreen.kt`

**Interfaces:**
- Consumes: AnimState from Task 1
- Produces: Full animated UI with content

- [ ] **Step 1: Add content to the Box in CourseEditScreen**

Replace the empty Box content in CourseEditScreen:

```kotlin
// Main content
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(
            if (isAppDarkTheme()) ComposeColor(0xFF2C2C2C).copy(alpha = animState.value.bgAlpha)
            else ComposeColor.Black.copy(alpha = animState.value.bgAlpha)
        )
) {
    val s = animState.value
    val clipShape = remember { AnimClipShape(screenWidth, screenCornerRadius, startCornerRadiusPx, animState) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = false
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = s.scale
                scaleY = s.scale
                translationX = s.translationX
                translationY = s.translationY
            }
            .clip(clipShape)
            .background(if (isAppDarkTheme()) ComposeColor(0xFF363636) else ComposeColor(0xFFFFFFFF))
    ) {
        // Snapshot fade out
        if (cardSnapshot != null && s.snapshotAlpha > 0f) {
            Image(
                bitmap = cardSnapshot.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = s.snapshotAlpha },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

        // Content fade in
        if (s.contentAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = s.contentAlpha }
            ) {
                // Scaffold with top bar and lazy column content
                val isLiquidGlass = liquidGlassBackdrop != null && rememberAppStyle() == "liquidglass"
                val backgroundColor = MiuixTheme.colorScheme.surface
                val backdrop = rememberLayerBackdrop {
                    drawRect(backgroundColor)
                    drawContent()
                }

                Scaffold(
                    topBar = {
                        if (isLiquidGlass) {
                            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                            ProgressiveBlurTopBar(
                                backdrop = liquidGlassBackdrop!!,
                            ) {
                                SmallTopAppBar(
                                    color = Color.Transparent,
                                    title = courseName,
                                    modifier = Modifier.zIndex(1f),
                                    navigationIcon = {}
                                )
                                LiquidTopBarButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onBackStart()
                                        scope.launch {
                                            animProgress.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(
                                                    durationMillis = 380,
                                                    easing = morphExitEase
                                                )
                                            )
                                            onBack()
                                        }
                                    },
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Medium.ChevronBackward,
                                    contentDescription = "返回",
                                    modifier = Modifier
                                        .zIndex(2f)
                                        .offset(x = 20.dp, y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                                    iconSize = 22.dp,
                                    iconOffset = androidx.compose.ui.unit.DpOffset(x = (-2).dp, y = 0.dp),
                                    useBackdropShadow = true
                                )
                            }
                        } else {
                            // Non-liquid glass top bar
                            val scrollBehavior = MiuixScrollBehavior()
                            var listScrollY by remember { mutableIntStateOf(0) }
                            val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 50f).coerceIn(0f, 0.7f)
                            val topBarColor = if (listScrollY < 50) {
                                MiuixTheme.colorScheme.surface
                            } else {
                                val topBarColorProgress = ((listScrollY - 50) / 50f).coerceIn(0f, 1f)
                                val surface = MiuixTheme.colorScheme.surface
                                val target = if (isAppDarkTheme()) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
                                lerp(surface, target, topBarColorProgress)
                            }
                            val topAppBarColors = BlurDefaults.blurColors(
                                blendColors = listOf(
                                    if (isAppDarkTheme()) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
                                    else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
                                ),
                                brightness = 0f,
                                contrast = 1f,
                                saturation = 1.2f
                            )
                            top.yukonga.miuix.kmp.basic.TopAppBar(
                                modifier = if (blurAlpha > 0f) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RectangleShape,
                                        colors = topAppBarColors
                                    )
                                } else {
                                    Modifier
                                },
                                color = topBarColor,
                                title = courseName,
                                scrollBehavior = scrollBehavior,
                                navigationIcon = {
                                    top.yukonga.miuix.kmp.basic.IconButton(onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onBackStart()
                                        scope.launch {
                                            animProgress.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(
                                                    durationMillis = 380,
                                                    easing = morphExitEase
                                                )
                                            )
                                            onBack()
                                        }
                                    },
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        top.yukonga.miuix.kmp.basic.Icon(
                                            imageVector = MiuixIcons.Back,
                                            contentDescription = "返回",
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                ) { paddingValues ->
                    // Lazy column with course groups
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .scrollEndHaptic(
                                hapticFeedbackType = HapticFeedbackType.TextHandleMove
                            ),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = if (isLiquidGlass) paddingValues.calculateTopPadding() + 56.dp else paddingValues.calculateTopPadding(),
                            end = 16.dp,
                            bottom = 120.dp
                        ),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                    ) {
                        items(groupedCourses.size) { index ->
                            val group = groupedCourses[index]
                            // Course group card will be implemented in Task 3
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify animation works**

Run: Build and run the app
Expected: Page opens with morphing animation from card to full screen

---

## Task 3: Implement CourseGroupCard Component

**Covers:** Editable course segment cards

**Files:**
- Modify: `C:\Users\43908\Desktop\KeCB\app\src\main\java\com\haooz\chedule\ui\activities\CourseEditScreen.kt`

**Interfaces:**
- Consumes: CourseGroup data
- Produces: CourseGroupCard composable with edit capability

- [ ] **Step 1: Add CourseGroupCard composable**

Add after the CourseEditScreen composable:

```kotlin
@Composable
private fun CourseGroupCard(
    group: CourseGroup,
    sectionTimes: Map<Int, String>,
    onEdit: (Course) -> Unit
) {
    val isDark = isAppDarkTheme()
    val course = group.representative
    val dayName = when (course.dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "未知"
    }
    val sectionText = "第${course.startSection}-${course.endSection}节"
    val weekText = course.getWeekText()
    val timeStart = sectionTimes[course.startSection]?.split("-")?.firstOrNull() ?: ""
    val timeEnd = sectionTimes[course.endSection]?.split("-")?.lastOrNull() ?: ""
    val timeText = if (timeStart.isNotEmpty() && timeEnd.isNotEmpty()) "$timeStart-$timeEnd" else ""

    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Header row with day and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$dayName $sectionText",
                        style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = weekText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
                if (timeText.isNotEmpty()) {
                    Text(
                        text = timeText,
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 15.sp),
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Teacher and classroom info
            val detailParts = mutableListOf<String>()
            if (course.teacher.isNotEmpty()) detailParts.add("教师: ${course.teacher}")
            if (course.classroom.isNotEmpty()) detailParts.add("教室: ${course.classroom}")
            if (detailParts.isNotEmpty()) {
                Text(
                    text = detailParts.joinToString(" | "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Edit button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(20.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable {
                        onEdit(course)
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
}
```

- [ ] **Step 2: Update items block in LazyColumn to use CourseGroupCard**

Replace the items block:

```kotlin
items(groupedCourses.size) { index ->
    val group = groupedCourses[index]
    CourseGroupCard(
        group = group,
        sectionTimes = sectionTimes,
        onEdit = { course ->
            // TODO: Open edit dialog
        }
    )
}
```

- [ ] **Step 3: Verify cards display correctly**

Run: Build and run the app
Expected: Course groups display as cards with edit buttons

---

## Task 4: Implement Edit Dialog

**Covers:** Course editing dialog similar to AddCourseDialog

**Files:**
- Modify: `C:\Users\43908\Desktop\KeCB\app\src\main\java\com\haooz\chedule\ui\activities\CourseEditScreen.kt`

**Interfaces:**
- Consumes: Course data for editing
- Produces: EditDialog composable

- [ ] **Step 1: Add edit dialog state and composable**

Add state variables and dialog composable:

```kotlin
// Add to CourseEditScreen's content area
var showEditDialog by remember { mutableStateOf(false) }
var editingCourse by remember { mutableStateOf<Course?>(null) }

// Add dialog composable at the end of the Box
if (showEditDialog && editingCourse != null) {
    CourseEditDialog(
        course = editingCourse!!,
        onDismiss = {
            showEditDialog = false
            editingCourse = null
        },
        onConfirm = { updatedCourse ->
            onCourseUpdated(updatedCourse)
            showEditDialog = false
            editingCourse = null
        }
    )
}
```

- [ ] **Step 2: Implement CourseEditDialog composable**

Add after CourseGroupCard:

```kotlin
@Composable
private fun CourseEditDialog(
    course: Course,
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass"

    var name by remember { mutableStateOf(course.name) }
    var classroom by remember { mutableStateOf(course.classroom) }
    var teacher by remember { mutableStateOf(course.teacher) }
    var dayOfWeek by remember { mutableIntStateOf(course.dayOfWeek) }
    var startSection by remember { mutableIntStateOf(course.startSection) }
    var endSection by remember { mutableIntStateOf(course.endSection) }
    var startWeek by remember { mutableIntStateOf(course.startWeek) }
    var endWeek by remember { mutableIntStateOf(course.endWeek) }
    var weekType by remember { mutableIntStateOf(course.weekType) }

    var showBottomSheet by remember { mutableStateOf(true) }

    top.yukonga.miuix.kmp.overlay.OverlayBottomSheet(
        show = showBottomSheet,
        title = "编辑课程",
        startAction = {
            top.yukonga.miuix.kmp.basic.IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    showBottomSheet = false
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = MiuixIcons.Normal.Close,
                    contentDescription = "取消",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        endAction = {
            top.yukonga.miuix.kmp.basic.IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    if (name.isNotBlank() && startSection <= endSection && startWeek <= endWeek) {
                        val updatedCourse = course.copy(
                            name = name.trim(),
                            classroom = classroom.trim(),
                            teacher = teacher.trim(),
                            dayOfWeek = dayOfWeek,
                            startSection = startSection,
                            endSection = endSection,
                            startWeek = startWeek,
                            endWeek = endWeek,
                            weekType = weekType,
                            lastModified = System.currentTimeMillis()
                        )
                        onConfirm(updatedCourse)
                        showBottomSheet = false
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = "确认",
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        onDismissRequest = { showBottomSheet = false },
        onDismissFinished = onDismiss,
        backgroundColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF7F7F7),
        cornerRadius = 36.dp,
        insideMargin = androidx.compose.ui.unit.DpSize(0.dp, 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 60.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            // Basic info card
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                insideMargin = PaddingValues(horizontal = 16.dp),
                colors = CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Course name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "课程名称",
                            modifier = Modifier.weight(1f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        top.yukonga.miuix.kmp.basic.BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = TextAlign.End,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterEnd) {
                                    if (name.isEmpty()) {
                                        Text(
                                            text = "必填",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // Classroom
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "地点",
                            modifier = Modifier.weight(1f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        top.yukonga.miuix.kmp.basic.BasicTextField(
                            value = classroom,
                            onValueChange = { classroom = it },
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = TextAlign.End,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterEnd) {
                                    if (classroom.isEmpty()) {
                                        Text(
                                            text = "非必填",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // Teacher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "教师",
                            modifier = Modifier.weight(1f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        top.yukonga.miuix.kmp.basic.BasicTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = TextAlign.End,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterEnd) {
                                    if (teacher.isEmpty()) {
                                        Text(
                                            text = "非必填",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }

            // Day of week selection
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "上课星期",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                    ) {
                        val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                        for (day in 1..7) {
                            val isSelected = day == dayOfWeek
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                cornerRadius = 10.dp,
                                insideMargin = PaddingValues(0.dp),
                                pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
                                colors = CardDefaults.defaultColors(
                                    color = if (isSelected) MiuixTheme.colorScheme.primary
                                    else if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7),
                                    contentColor = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                ),
                                onClick = { dayOfWeek = day }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayLabels[day - 1],
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section range
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "上课节次",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "第${startSection}节",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "至",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        Text(
                            text = "第${endSection}节",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        // Simple increment/decrement buttons
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "-",
                                onClick = {
                                    if (startSection > 1) {
                                        startSection--
                                        if (endSection < startSection) endSection = startSection
                                    }
                                }
                            )
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "+",
                                onClick = {
                                    if (endSection < 12) endSection++
                                }
                            )
                        }
                    }
                }
            }

            // Week range
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "上课周次",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "第${startWeek}周",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "至",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        Text(
                            text = "第${endWeek}周",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "-",
                                onClick = {
                                    if (startWeek > 1) {
                                        startWeek--
                                        if (endWeek < startWeek) endWeek = startWeek
                                    }
                                }
                            )
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "+",
                                onClick = {
                                    if (endWeek < 30) endWeek++
                                }
                            )
                        }
                    }

                    // Week type selection
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        val weekTypes = listOf(
                            Pair(Course.WEEK_TYPE_ALL, "全周"),
                            Pair(Course.WEEK_TYPE_ODD, "单周"),
                            Pair(Course.WEEK_TYPE_EVEN, "双周")
                        )
                        weekTypes.forEach { (type, label) ->
                            val isSelected = weekType == type
                            Card(
                                modifier = Modifier.weight(1f),
                                cornerRadius = 10.dp,
                                insideMargin = PaddingValues(0.dp),
                                pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
                                colors = CardDefaults.defaultColors(
                                    color = if (isSelected) MiuixTheme.colorScheme.primary
                                    else if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7),
                                    contentColor = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                ),
                                onClick = { weekType = type }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
```

- [ ] **Step 3: Add missing imports**

Add these imports to the file:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import com.haooz.chedule.data.Course
```

- [ ] **Step 4: Verify dialog works**

Run: Build and run the app
Expected: Clicking edit button opens the edit dialog

---

## Task 5: Update CourseManageScreen to Navigate to Edit Screen

**Covers:** Navigation from course management to edit screen

**Files:**
- Modify: `C:\Users\43908\Desktop\KeCB\app\src\main\java\com\haooz\chedule\ui\activities\CourseManageScreen.kt`
- Modify: `C:\Users\43908\Desktop\KeCB\app\src\main\java\com\haooz\chedule\ui\activities\CourseManageActivity.kt`

**Interfaces:**
- Consumes: Course name, card position data
- Produces: Navigation to CourseEditScreen

- [ ] **Step 1: Update CourseManageScreen to pass course data**

Modify the CourseManageCard click handler:

```kotlin
CourseManageCard(
    courseName = courseName,
    teacher = teachers,
    classroom = classrooms,
    color = Color(representative.colorRes),
    daySectionInfo = daySectionInfo,
    onClick = { 
        // TODO: Navigate to edit screen with animation
    }
)
```

- [ ] **Step 2: Add navigation state to CourseManageActivity**

Modify CourseManageActivity.kt:

```kotlin
class CourseManageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        setContent {
            CourseScheduleTheme {
                // ... existing code ...
                
                var showEditScreen by remember { mutableStateOf(false) }
                var selectedCourseName by remember { mutableStateOf("") }
                var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
                var cardPosition by remember { mutableStateOf(CardPosition(0f, 0f, 0f, 0f)) }
                
                if (showEditScreen) {
                    CourseEditScreen(
                        courseName = selectedCourseName,
                        courses = selectedCourses,
                        cardLeft = cardPosition.left,
                        cardTop = cardPosition.top,
                        cardWidth = cardPosition.width,
                        cardHeight = cardPosition.height,
                        screenWidth = resources.displayMetrics.widthPixels.toFloat(),
                        screenHeight = resources.displayMetrics.heightPixels.toFloat(),
                        screenCornerRadius = 40f,
                        cardSnapshot = null,
                        sectionTimes = com.haooz.chedule.data.Course.defaultSectionTimes,
                        onBackStart = { },
                        onBack = { showEditScreen = false },
                        onCourseUpdated = { updatedCourse ->
                            // TODO: Update course in database
                        },
                        liquidGlassBackdrop = liquidGlassBackdrop
                    )
                } else {
                    // ... existing scaffold code ...
                }
            }
        }
    }
}

private data class CardPosition(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)
```

- [ ] **Step 3: Pass navigation callback to CourseManageScreen**

```kotlin
CourseManageScreen(
    onBack = { finish() },
    liquidGlassBackdrop = liquidGlassBackdrop,
    onCourseClick = { courseName, courses, position ->
        selectedCourseName = courseName
        selectedCourses = courses
        cardPosition = position
        showEditScreen = true
    }
)
```

- [ ] **Step 4: Verify navigation works**

Run: Build and run the app
Expected: Clicking course card navigates to edit screen with animation

---

## Task 6: Implement Course Update Logic

**Covers:** Save edited course data

**Files:**
- Modify: `C:\Users\43908\Desktop\KeCB\app\src\main\java\com\haooz\chedule\ui\activities\CourseManageActivity.kt`

**Interfaces:**
- Consumes: Updated course from edit dialog
- Produces: Updated course in database

- [ ] **Step 1: Add course update callback**

```kotlin
onCourseUpdated = { updatedCourse ->
    // Update course in database
    val repository = com.haooz.chedule.data.CourseRepository(this@CourseManageActivity)
    lifecycleScope.launch {
        repository.updateCourse(updatedCourse)
        // Refresh the courses list
        // ...
    }
}
```

- [ ] **Step 2: Verify update works**

Run: Build and run the app
Expected: Editing a course saves the changes

---

## Task 7: Add Delete Course Functionality

**Covers:** Delete course from edit screen

**Files:**
- Modify: `C:\Users\43908\Desktop\KeCB\app\src\main\java\com\haooz\chedule\ui\activities\CourseEditScreen.kt`

**Interfaces:**
- Consumes: Course to delete
- Produces: Deleted course from database

- [ ] **Step 1: Add delete button to CourseGroupCard**

```kotlin
// Add after edit button
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedRectangle(20.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
            .clickable {
                onEdit(course)
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
    Box(
        modifier = Modifier
            .clip(RoundedRectangle(20.dp))
            .background(Color(0xFFF44336).copy(alpha = 0.1f))
            .clickable {
                onDelete(course)
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "删除",
            style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
            fontWeight = FontWeight.Medium,
            color = Color(0xFFF44336)
        )
    }
}
```

- [ ] **Step 2: Add delete callback to CourseGroupCard**

```kotlin
@Composable
private fun CourseGroupCard(
    group: CourseGroup,
    sectionTimes: Map<Int, String>,
    onEdit: (Course) -> Unit,
    onDelete: (Course) -> Unit
) {
    // ... existing code ...
}
```

- [ ] **Step 3: Add delete confirmation dialog**

```kotlin
var showDeleteDialog by remember { mutableStateOf(false) }
var deletingCourse by remember { mutableStateOf<Course?>(null) }

if (showDeleteDialog && deletingCourse != null) {
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        title = "删除课程",
        summary = "确定要删除这个课程吗？",
        show = showDeleteDialog,
        onDismissRequest = {
            showDeleteDialog = false
            deletingCourse = null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = "取消",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    showDeleteDialog = false
                    deletingCourse = null
                },
                modifier = Modifier.weight(1f)
            )
            top.yukonga.miuix.kmp.basic.TextButton(
                text = "删除",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (deletingCourse != null) {
                        onCourseDeleted(deletingCourse!!)
                    }
                    showDeleteDialog = false
                    deletingCourse = null
                },
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

- [ ] **Step 4: Verify delete works**

Run: Build and run the app
Expected: Deleting a course removes it from the list

---

## Task 8: Final Integration and Testing

**Covers:** Complete integration and testing

**Files:**
- All modified files

**Interfaces:**
- Full feature working end-to-end

- [ ] **Step 1: Build and verify no compilation errors**

Run: Build the entire project
Expected: No errors

- [ ] **Step 2: Test complete flow**

1. Open app
2. Navigate to course management
3. Click on a course card
4. Verify edit screen opens with animation
5. Edit course details
6. Save changes
7. Verify changes are reflected
8. Delete a course
9. Verify course is removed

- [ ] **Step 3: Test edge cases**

1. Edit a course with multiple time slots
2. Edit a course with custom weeks
3. Edit course during animation
4. Press back during animation

- [ ] **Step 4: Commit final changes**

```bash
git add .
git commit -m "feat: implement course edit screen with morphing animation"
```
