/** 添加/编辑课程对话框 */
package com.haooz.chedule.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.components.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.UUID

@Composable
fun AddCourseDialog(
    course: Course?,
    selectedDay: Int,
    totalWeeks: Int = 20,
    totalSections: Int = 12,
    defaultStartSection: Int = 1,
    defaultEndSection: Int = 2,
    getOccupiedWeeks: (dayOfWeek: Int, startSection: Int, endSection: Int) -> Set<Int> = { _, _, _ -> emptySet() },
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit,
    onDelete: (String) -> Unit,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null
) {
    val isEdit = course != null
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass" && liquidGlassBackdrop != null
    val isDark = isAppDarkTheme()
    val closeContainerColor = if (isDark) Color(0xFF121212).copy(1f) else Color(0xFFFAFAFA).copy(1f)
    val closeIconColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)

    var name by remember { mutableStateOf(course?.name ?: "") }
    var classroom by remember { mutableStateOf(course?.classroom ?: "") }
    var teacher by remember { mutableStateOf(course?.teacher ?: "") }
    var dayOfWeek by remember { mutableIntStateOf(course?.dayOfWeek ?: selectedDay) }
    var startSection by remember { mutableIntStateOf(course?.startSection ?: defaultStartSection) }
    var endSection by remember { mutableIntStateOf(course?.endSection ?: defaultEndSection) }
    var isSingleWeek by remember { mutableStateOf(course?.weekType == Course.WEEK_TYPE_ODD) }
    var isDoubleWeek by remember { mutableStateOf(course?.weekType == Course.WEEK_TYPE_EVEN) }
    var selectedColor by remember { mutableLongStateOf(course?.colorRes ?: Course.courseColors.first()) }

    // 根据当前选择的星期和节次动态计算已占用的周次
    val currentOccupiedWeeks by remember(dayOfWeek, startSection, endSection) {
        derivedStateOf {
            getOccupiedWeeks(dayOfWeek, startSection, endSection)
        }
    }

    val selectedWeeks = remember {
        mutableStateSetOf<Int>().apply {
            if (course != null) {
                if (course.selectedWeeks.isNotEmpty()) {
                    addAll(course.selectedWeeks)
                } else {
                    for (w in course.startWeek..course.endWeek) {
                        when (course.weekType) {
                            Course.WEEK_TYPE_ODD -> if (w % 2 == 1) add(w)
                            Course.WEEK_TYPE_EVEN -> if (w % 2 == 0) add(w)
                            else -> add(w)
                        }
                    }
                }
            }
        }
    }

    val allWeeks = (1..totalWeeks).toList()
    val oddWeeks = allWeeks.filter { it % 2 == 1 }
    val evenWeeks = allWeeks.filter { it % 2 == 0 }
    val someOddSelected = oddWeeks.any { it in selectedWeeks }
    val someEvenSelected = evenWeeks.any { it in selectedWeeks }

    val selectableWeeks = allWeeks.filter { it !in currentOccupiedWeeks }
    val selectableOddWeeks = selectableWeeks.filter { it % 2 == 1 }
    val selectableEvenWeeks = selectableWeeks.filter { it % 2 == 0 }
    val allSelectableSelected = selectableWeeks.isNotEmpty() && selectableWeeks.all { it in selectedWeeks }
    val allSelectableOddSelected = selectableOddWeeks.all { it in selectedWeeks }
    val allSelectableEvenSelected = selectableEvenWeeks.all { it in selectedWeeks }
    val someSelectableOddSelected = selectableOddWeeks.any { it in selectedWeeks }
    val someSelectableEvenSelected = selectableEvenWeeks.any { it in selectedWeeks }
    val hasOccupiedOddWeeks = selectableOddWeeks.size != oddWeeks.size
    val hasOccupiedEvenWeeks = selectableEvenWeeks.size != evenWeeks.size

    var showBottomSheet by remember { mutableStateOf(true) }
    var showSectionDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var tempStartSection by remember { mutableIntStateOf(course?.startSection ?: 1) }
    var tempEndSection by remember { mutableIntStateOf(course?.endSection ?: 2) }
    var customColor by remember { mutableStateOf(Color(selectedColor)) }

    OverlayBottomSheet(
        show = showBottomSheet,
        title = if (isEdit) "编辑课程" else "添加课程",
        startAction = {
            if (isLiquidGlass && liquidGlassBackdrop != null) {
                val animationScope = rememberCoroutineScope()
                val closeHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .size(40.dp)
                        .drawBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(2f.dp.toPx())
                                lens(12f.dp.toPx(), 12f.dp.toPx())
                            },
                            shadow = { com.kyant.backdrop.shadow.Shadow(alpha = 1f) },
                            layerBlock = {
                                val progress = closeHighlight.pressProgress
                                val scale = 1f + 2f.dp.toPx() / 40.dp.toPx() * progress
                                scaleX = scale
                                scaleY = scale
                                val offset = closeHighlight.offset
                                translationX = size.minDimension * 0.05f * offset.x / size.maxDimension
                                translationY = size.minDimension * 0.05f * offset.y / size.maxDimension
                            },
                            onDrawSurface = {
                                drawRect(closeContainerColor)
                                drawRect(Color.Black.copy(alpha = 0.03f * closeHighlight.pressProgress))
                            }
                        )
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                showBottomSheet = false
                            }
                        )
                        .then(closeHighlight.modifier)
                        .then(closeHighlight.gestureModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "取消",
                        modifier = Modifier.size(22.dp),
                        tint = closeIconColor
                    )
                }
            } else {
                IconButton(onClick = { showBottomSheet = false }, modifier = Modifier.padding(horizontal = 20.dp)) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "取消",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        endAction = {
            if (isLiquidGlass && liquidGlassBackdrop != null) {
                val animationScope = rememberCoroutineScope()
                val okHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
                val primaryColor = MiuixTheme.colorScheme.primary.copy(alpha = 1f)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .size(40.dp)
                        .drawBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(2f.dp.toPx())
                                lens(12f.dp.toPx(), 12f.dp.toPx())
                            },
                            shadow = { com.kyant.backdrop.shadow.Shadow(alpha = 1f) },
                            layerBlock = {
                                val progress = okHighlight.pressProgress
                                val scale = 1f + 2f.dp.toPx() / 40.dp.toPx() * progress
                                scaleX = scale
                                scaleY = scale
                                val offset = okHighlight.offset
                                translationX = size.minDimension * 0.05f * offset.x / size.maxDimension
                                translationY = size.minDimension * 0.05f * offset.y / size.maxDimension
                            },
                            onDrawSurface = {
                                drawRect(primaryColor.copy(0.8f))
                                drawRect(Color.Black.copy(alpha = 0.03f * okHighlight.pressProgress))
                            }
                        )
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                if (name.isNotBlank() && startSection <= endSection && selectedWeeks.isNotEmpty()) {
                                    val sortedWeeks = selectedWeeks.sorted()
                                    val minWeek = sortedWeeks.first()
                                    val maxWeek = sortedWeeks.last()
                                    val allWeeksInRange = (minWeek..maxWeek).toSet()
                                    val oddWeeksInRange = allWeeksInRange.filter { it % 2 == 1 }.toSet()
                                    val evenWeeksInRange = allWeeksInRange.filter { it % 2 == 0 }.toSet()

                                    val weekType = when (selectedWeeks) {
                                        allWeeksInRange -> Course.WEEK_TYPE_ALL
                                        oddWeeksInRange -> Course.WEEK_TYPE_ODD
                                        evenWeeksInRange -> Course.WEEK_TYPE_EVEN
                                        else -> Course.WEEK_TYPE_ALL
                                    }

                                    val isContiguous = selectedWeeks.size == (maxWeek - minWeek + 1)
                                    val weeksToSave = if (isContiguous) emptyList() else sortedWeeks

                                    val newCourse = Course(
                                        id = course?.id ?: UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        classroom = classroom.trim(),
                                        teacher = teacher.trim(),
                                        dayOfWeek = dayOfWeek,
                                        startSection = startSection,
                                        endSection = endSection,
                                        startWeek = minWeek,
                                        endWeek = maxWeek,
                                        weekType = weekType,
                                        colorRes = selectedColor,
                                        selectedWeeks = weeksToSave
                                    )

                                    onConfirm(newCourse)
                                    showBottomSheet = false
                                }
                            }
                        )
                        .then(okHighlight.modifier)
                        .then(okHighlight.gestureModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "确认",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (name.isNotBlank() && startSection <= endSection && selectedWeeks.isNotEmpty()) {
                            val sortedWeeks = selectedWeeks.sorted()
                            val minWeek = sortedWeeks.first()
                            val maxWeek = sortedWeeks.last()
                            val allWeeksInRange = (minWeek..maxWeek).toSet()
                            val oddWeeksInRange = allWeeksInRange.filter { it % 2 == 1 }.toSet()
                            val evenWeeksInRange = allWeeksInRange.filter { it % 2 == 0 }.toSet()

                            val weekType = when (selectedWeeks) {
                                allWeeksInRange -> Course.WEEK_TYPE_ALL
                                oddWeeksInRange -> Course.WEEK_TYPE_ODD
                                evenWeeksInRange -> Course.WEEK_TYPE_EVEN
                                else -> Course.WEEK_TYPE_ALL
                            }

                            val isContiguous = selectedWeeks.size == (maxWeek - minWeek + 1)
                            val weeksToSave = if (isContiguous) emptyList() else sortedWeeks

                            val newCourse = Course(
                                id = course?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                classroom = classroom.trim(),
                                teacher = teacher.trim(),
                                dayOfWeek = dayOfWeek,
                                startSection = startSection,
                                endSection = endSection,
                                startWeek = minWeek,
                                endWeek = maxWeek,
                                weekType = weekType,
                                colorRes = selectedColor,
                                selectedWeeks = weeksToSave
                            )

                            onConfirm(newCourse)
                            showBottomSheet = false
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "确认",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        onDismissRequest = { showBottomSheet = false },
        onDismissFinished = { onDismiss() },
        backgroundColor = if (isAppDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF7F7F7),
        cornerRadius = 36.dp,
        insideMargin = DpSize(0.dp, 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 基本信息卡片
            val isDark = isAppDarkTheme()
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
                    // 课程名称
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
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true,
                            textStyle = TextStyle(
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

                    // 教室
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
                        BasicTextField(
                            value = classroom,
                            onValueChange = { classroom = it },
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true,
                            textStyle = TextStyle(
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

                    // 教师
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
                        BasicTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true,
                            textStyle = TextStyle(
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

            Column(modifier = Modifier.fillMaxWidth()) {
                SmallTitle(text = "详细设置")
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 上课星期
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "上课星期",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                        pressFeedbackType = PressFeedbackType.Sink,
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
                }
            }

            // 节次范围
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "上课节次",
                            endActions = {
                                Text(
                                    text = "第${startSection} - ${endSection}节",
                                    fontSize = 14.5.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            },
                            onClick = {
                                tempStartSection = startSection
                                tempEndSection = endSection
                                showSectionDialog = true
                            },
                            holdDownState = showSectionDialog
                        )
                    }
                }
            }

            // 周次设置
            Column(modifier = Modifier.fillMaxWidth()) {
                val hasMixedSelection = someOddSelected && someEvenSelected

                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "上课周次",
                                modifier = Modifier.weight(1f),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        if (allSelectableSelected) {
                                            selectedWeeks.clear()
                                        } else {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableWeeks)
                                        }
                                        isSingleWeek = false
                                        isDoubleWeek = false
                                    }
                                ) {
                                    Checkbox(
                                        state = if (allSelectableSelected) ToggleableState.On else ToggleableState.Off,
                                        onClick = {
                                            if (allSelectableSelected) {
                                                selectedWeeks.clear()
                                            } else {
                                                selectedWeeks.clear()
                                                selectedWeeks.addAll(selectableWeeks)
                                            }
                                            isSingleWeek = false
                                            isDoubleWeek = false
                                        },
                                        colors = CheckboxDefaults.checkboxColors(
                                            uncheckedBackgroundColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "全部", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        if (hasMixedSelection) {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableOddWeeks)
                                            isSingleWeek = true
                                            isDoubleWeek = false
                                        } else if (allSelectableOddSelected) {
                                            selectedWeeks.clear()
                                            isSingleWeek = false
                                            isDoubleWeek = false
                                        } else {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableOddWeeks)
                                            isSingleWeek = true
                                            isDoubleWeek = false
                                        }
                                    }
                                ) {
                                    Checkbox(
                                        state = when {
                                            hasMixedSelection -> ToggleableState.Off
                                            allSelectableOddSelected && !hasOccupiedOddWeeks -> ToggleableState.On
                                            someSelectableOddSelected -> ToggleableState.Indeterminate
                                            else -> ToggleableState.Off
                                        },
                                        onClick = {
                                            if (hasMixedSelection) {
                                                selectedWeeks.clear()
                                                selectedWeeks.addAll(selectableOddWeeks)
                                                isSingleWeek = true
                                                isDoubleWeek = false
                                            } else if (allSelectableOddSelected) {
                                                selectedWeeks.clear()
                                                isSingleWeek = false
                                                isDoubleWeek = false
                                            } else {
                                                selectedWeeks.clear()
                                                selectedWeeks.addAll(selectableOddWeeks)
                                                isSingleWeek = true
                                                isDoubleWeek = false
                                            }
                                        },
                                        colors = CheckboxDefaults.checkboxColors(
                                            uncheckedBackgroundColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "单周", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        if (hasMixedSelection) {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableEvenWeeks)
                                            isSingleWeek = false
                                            isDoubleWeek = true
                                        } else if (allSelectableEvenSelected) {
                                            selectedWeeks.clear()
                                            isSingleWeek = false
                                            isDoubleWeek = false
                                        } else {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableEvenWeeks)
                                            isSingleWeek = false
                                            isDoubleWeek = true
                                        }
                                    }
                                ) {
                                    Checkbox(
                                        state = when {
                                            hasMixedSelection -> ToggleableState.Off
                                            allSelectableEvenSelected && !hasOccupiedEvenWeeks -> ToggleableState.On
                                            someSelectableEvenSelected -> ToggleableState.Indeterminate
                                            else -> ToggleableState.Off
                                        },
                                        onClick = {
                                            if (hasMixedSelection) {
                                                selectedWeeks.clear()
                                                selectedWeeks.addAll(selectableEvenWeeks)
                                                isSingleWeek = false
                                                isDoubleWeek = true
                                            } else if (allSelectableEvenSelected) {
                                                selectedWeeks.clear()
                                                isSingleWeek = false
                                                isDoubleWeek = false
                                            } else {
                                                selectedWeeks.clear()
                                                selectedWeeks.addAll(selectableEvenWeeks)
                                                isSingleWeek = false
                                                isDoubleWeek = true
                                            }
                                        },
                                        colors = CheckboxDefaults.checkboxColors(
                                            uncheckedBackgroundColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "双周", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        }

                        // 周次网格
                        val columns = 6
                        val rows = (totalWeeks + columns - 1) / columns
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (row in 0 until rows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (col in 0 until columns) {
                                        val weekNum = row * columns + col + 1
                                        if (weekNum <= totalWeeks) {
                                            val isSelected = weekNum in selectedWeeks
                                            val isOccupied = weekNum in currentOccupiedWeeks
                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(32.dp),
                                                cornerRadius = 10.dp,
                                                insideMargin = PaddingValues(0.dp),
                                                pressFeedbackType = PressFeedbackType.Sink,
                                                showIndication = !isOccupied,
                                                colors = CardDefaults.defaultColors(
                                                    color = when {
                                                        isSelected -> MiuixTheme.colorScheme.primary
                                                        isOccupied -> if (isDark) Color(0xFF4A4A4A) else Color(0xFFF0F0F0)
                                                        isDark -> Color(0xFF505050)
                                                        else -> Color(0xFFF7F7F7)
                                                    },
                                                    contentColor = when {
                                                        isSelected -> Color.White
                                                        isOccupied -> MiuixTheme.colorScheme.outline
                                                        else -> MiuixTheme.colorScheme.onSurface
                                                    }
                                                ),
                                                onClick = {
                                                    if (!isOccupied) {
                                                        if (isSelected) {
                                                            selectedWeeks.remove(weekNum)
                                                        } else {
                                                            selectedWeeks.add(weekNum)
                                                        }
                                                        isSingleWeek = false
                                                        isDoubleWeek = false
                                                    }
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "$weekNum",
                                                        fontSize = 13.sp,
                                                        color = when {
                                                            isSelected -> Color.White
                                                            isOccupied -> MiuixTheme.colorScheme.outline
                                                            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                        }
                                                    )
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
            }

            // 课程颜色选择
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "课程颜色",
                                modifier = Modifier.weight(1f),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val colorColumns = 6
                            val allColors = Course.courseColors
                            val totalItems = allColors.size + 1 // +1 for custom color button
                            val colorRows = (totalItems + colorColumns - 1) / colorColumns
                            for (row in 0 until colorRows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    for (col in 0 until colorColumns) {
                                        val colorIndex = row * colorColumns + col
                                        if (colorIndex < allColors.size) {
                                            val color = allColors[colorIndex]
                                            val isSelected = color == selectedColor
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
                                                                    selectedColor = color
                                                                }
                                                            }
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                // 底层卡片（squircle描边）
                                                // 底层描边 + 白色背景
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
                                                        .background(if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF))
                                                )
                                                // 上层颜色卡片（固定内边距）
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxSize(),
                                                        cornerRadius = 8.dp,
                                                        insideMargin = PaddingValues(0.dp),
                                                        colors = CardDefaults.defaultColors(
                                                            color = Color(color).copy(alpha = if (isDark) 0.22f else 0.16f),
                                                            contentColor = Color.White
                                                        ),
                                                        onClick = { selectedColor = color }
                                                    ) {}
                                                }
                                            }
                                        } else if (colorIndex == allColors.size) {
                                            // 自定义颜色按钮
                                            val isCustomColor = selectedColor !in allColors
                                            val bgColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
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
                                                                    customColor = Color(selectedColor)
                                                                    showColorDialog = true
                                                                }
                                                            }
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                // 底层卡片（squircle描边）
                                                // 底层描边 + 白色背景
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
                                                        .background(if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF))
                                                )
                                                // 上层卡片（固定内边距）
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxSize(),
                                                        cornerRadius = 8.dp,
                                                        insideMargin = PaddingValues(0.dp),
                                                        colors = CardDefaults.defaultColors(
                                                            color = bgColor,
                                                            contentColor = hintColor
                                                        ),
                                                        onClick = {
                                                            customColor = Color(selectedColor)
                                                            showColorDialog = true
                                                        }
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isCustomColor) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize(0.7f)
                                                                        .clip(RoundedRectangle(4.dp))
                                                                        .background(Color(selectedColor).copy(alpha = if (isDark) 0.22f else 0.16f))
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
                    }
                }
            }

            // 按钮区域
            if (isEdit) {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, end= 32.dp, start = 32.dp).height(50.dp),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showDeleteDialog = true
                    },
                    colors = if (isDark) ButtonDefaults.buttonColors(
                        color = Color(0xFF2A2A2A)
                    ) else ButtonDefaults.buttonColors(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除",fontSize = 17.sp, fontWeight = FontWeight.Medium,color = Color(0xFFF44336))
                }
            }
        }
    }

    OverlayDialog(
        title = "删除课程",
        summary = "确定要删除课程「${course?.name}」吗？\n此操作不可撤销。",
        show = showDeleteDialog,
        onDismissRequest = { showDeleteDialog = false }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { showDeleteDialog = false },
            ) {
                Text("取消",fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    course?.id?.let { onDelete(it) }
                    showDeleteDialog = false
                    showBottomSheet = false
                },
            ) {
                Text("删除",fontSize = 17.sp, fontWeight = FontWeight.Medium,color = Color(0xFFF44336))
            }
        }
    }

    // 节次选择弹窗
    OverlayDialog(
        title = "选择上课节次",
        show = showSectionDialog,
        onDismissRequest = { showSectionDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LaunchedEffect(tempStartSection) {
                if (tempEndSection < tempStartSection) {
                    tempEndSection = tempStartSection
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "开始",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    NumberPicker(
                        value = tempStartSection,
                        onValueChange = { tempStartSection = it },
                        range = 1..totalSections,
                        visibleItemCount = 3,
                        itemHeight = 50.dp
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "结束",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    NumberPicker(
                        value = tempEndSection,
                        onValueChange = { tempEndSection = it },
                        range = tempStartSection..totalSections,
                        visibleItemCount = 3,
                        itemHeight = 50.dp
                    )
                }
            }

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
                        showSectionDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (tempStartSection <= tempEndSection) {
                                startSection = tempStartSection
                                endSection = tempEndSection
                        }
                        showSectionDialog = false
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
        show = showColorDialog,
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
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showColorDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        selectedColor = (customColor.alpha * 255).toInt().toLong() shl 24 or
                                ((customColor.red * 255).toInt().toLong() shl 16) or
                                ((customColor.green * 255).toInt().toLong() shl 8) or
                                (customColor.blue * 255).toInt().toLong()
                        showColorDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
