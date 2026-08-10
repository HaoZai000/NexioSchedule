/** 添加/编辑课程对话框 - Blur版本 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.effects.blur.BlurBottomSheet
import com.haooz.chedule.ui.effects.blur.BlurBottomSheetTablet
import com.haooz.chedule.ui.components.NativeTextField
import com.haooz.chedule.ui.effects.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import com.haooz.chedule.ui.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.UUID

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun AddCourseDialog(
    show: Boolean,
    course: Course?,
    selectedDay: Int,
    backdrop: LayerBackdrop?,
    liquidGlassBackdrop: Backdrop? = null,
    totalWeeks: Int = 20,
    totalSections: Int = 12,
    defaultStartSection: Int = 1,
    defaultEndSection: Int = 2,
    getOccupiedWeeks: (dayOfWeek: Int, startSection: Int, endSection: Int, excludeIds: List<String>) -> Set<Int> = { _, _, _, _ -> emptySet() },
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit,
    onDelete: (String) -> Unit,
) {
    val isEdit = course != null
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val isDark = isAppDarkTheme()
    var sheetContentBackdrop by remember { mutableStateOf<Backdrop?>(null) }

    var name by remember(show) { mutableStateOf(course?.name ?: "") }
    var classroom by remember(show) { mutableStateOf(course?.classroom ?: "") }
    var teacher by remember(show) { mutableStateOf(course?.teacher ?: "") }
    var dayOfWeek by remember(show) { mutableIntStateOf(course?.dayOfWeek ?: selectedDay) }
    var startSection by remember(show) { mutableIntStateOf(course?.startSection ?: defaultStartSection) }
    var endSection by remember(show) { mutableIntStateOf(course?.endSection ?: defaultEndSection) }
    var isSingleWeek by remember(show) { mutableStateOf(course?.weekType == Course.WEEK_TYPE_ODD) }
    var isDoubleWeek by remember(show) { mutableStateOf(course?.weekType == Course.WEEK_TYPE_EVEN) }
    var selectedColor by remember(show) { mutableLongStateOf(course?.colorRes ?: Course.courseColors.first()) }

    val currentOccupiedWeeks by remember(dayOfWeek, startSection, endSection) {
        derivedStateOf {
            getOccupiedWeeks(dayOfWeek, startSection, endSection, listOfNotNull(course?.id))
        }
    }

    val selectedWeeks = remember(show) {
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

    LaunchedEffect(currentOccupiedWeeks) {
        selectedWeeks.removeAll(currentOccupiedWeeks)
    }

    val allWeeks = remember(totalWeeks) { (1..totalWeeks).toList() }
    val oddWeeks = remember(allWeeks) { allWeeks.filter { it % 2 == 1 } }
    val evenWeeks = remember(allWeeks) { allWeeks.filter { it % 2 == 0 } }

    val selectableWeeks = remember(allWeeks, currentOccupiedWeeks) { allWeeks.filter { it !in currentOccupiedWeeks } }
    val selectableOddWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 1 } }
    val selectableEvenWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 0 } }
    val allSelectableSelected = selectableWeeks.isNotEmpty() && selectableWeeks.all { it in selectedWeeks }
    val allSelectableOddSelected = selectableOddWeeks.all { it in selectedWeeks }
    val allSelectableEvenSelected = selectableEvenWeeks.all { it in selectedWeeks }
    val someSelectableOddSelected = selectableOddWeeks.any { it in selectedWeeks }
    val someSelectableEvenSelected = selectableEvenWeeks.any { it in selectedWeeks }
    val hasOccupiedOddWeeks = remember(selectableOddWeeks, oddWeeks) { selectableOddWeeks.size != oddWeeks.size }
    val hasOccupiedEvenWeeks = remember(selectableEvenWeeks, evenWeeks) { selectableEvenWeeks.size != evenWeeks.size }

    var showSectionDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var tempStartSection by remember(show) { mutableIntStateOf(course?.startSection ?: 1) }
    var tempEndSection by remember(show) { mutableIntStateOf(course?.endSection ?: 2) }
    var customColor by remember { mutableStateOf(Color(selectedColor)) }

    val onConfirmClick: () -> Unit = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        if (name.isBlank()) {
            android.widget.Toast.makeText(context, "请输入课程名称", android.widget.Toast.LENGTH_SHORT).show()
        } else if (selectedWeeks.isEmpty()) {
            android.widget.Toast.makeText(context, "请选择上课周次", android.widget.Toast.LENGTH_SHORT).show()
        } else if (name.isNotBlank() && startSection <= endSection && selectedWeeks.isNotEmpty()) {
            val sortedWeeks = selectedWeeks.sorted()
            val minWeek = sortedWeeks.first()
            val maxWeek = sortedWeeks.last()
            val allWeeksInRange = (minWeek..maxWeek).toSet()
            val oddWeeksInRange = allWeeksInRange.filter { it % 2 == 1 }.toSet()
            val evenWeeksInRange = allWeeksInRange.filter { it % 2 == 0 }.toSet()

            val weekType = when {
                selectedWeeks.toSet() == allWeeksInRange -> Course.WEEK_TYPE_ALL
                selectedWeeks.toSet() == oddWeeksInRange -> Course.WEEK_TYPE_ODD
                selectedWeeks.toSet() == evenWeeksInRange -> Course.WEEK_TYPE_EVEN
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
            onDismiss()
        }
    }
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    if (isTablet) {
        BlurBottomSheetTablet(
            show = show,
            title = if (isEdit) "编辑课程" else "添加课程",
            dimBackground = true,
            onDismissRequest = onDismiss,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onSheetContentBackdropCreated = { sheetContentBackdrop = it },
            startAction = {
                if (liquidGlassBackdrop != null) {
                    LiquidTopBarButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDismiss()
                        },
                        backdrop = sheetContentBackdrop ?: liquidGlassBackdrop,
                        icon = MiuixIcons.Normal.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.padding(start = 20.dp),
                        iconSize = 22.dp,
                    )
                } else {
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDismiss()
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Normal.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            endAction = {
                if (liquidGlassBackdrop != null) {
                    LiquidTopBarButton(
                        onClick = onConfirmClick,
                        backdrop = sheetContentBackdrop ?: liquidGlassBackdrop,
                        icon = MiuixIcons.Ok,
                        contentDescription = "确定",
                        modifier = Modifier.padding(end = 20.dp),
                        iconSize = 23.dp,
                        iconTint = Color.White,
                        containerColor = if (isDark) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else MiuixTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                } else {
                    IconButton(
                        onClick = onConfirmClick,
                        modifier = Modifier.padding(end = 20.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = "确定",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
        ) {
            AddCourseDialogContent(
                isEdit = isEdit,
                isDark = isDark,
                name = name,
                onNameChange = { name = it },
                classroom = classroom,
                onClassroomChange = { classroom = it },
                teacher = teacher,
                onTeacherChange = { teacher = it },
                dayOfWeek = dayOfWeek,
                onDayOfWeekChange = { dayOfWeek = it; selectedWeeks.clear() },
                startSection = startSection,
                endSection = endSection,
                totalWeeks = totalWeeks,
                selectedWeeks = selectedWeeks,
                selectedColor = selectedColor,
                onSelectedColorChange = { selectedColor = it },
                currentOccupiedWeeks = currentOccupiedWeeks,
                selectableWeeks = selectableWeeks,
                selectableOddWeeks = selectableOddWeeks,
                selectableEvenWeeks = selectableEvenWeeks,
                allSelectableSelected = allSelectableSelected,
                allSelectableOddSelected = allSelectableOddSelected,
                allSelectableEvenSelected = allSelectableEvenSelected,
                someSelectableOddSelected = someSelectableOddSelected,
                someSelectableEvenSelected = someSelectableEvenSelected,
                hasOccupiedOddWeeks = hasOccupiedOddWeeks,
                hasOccupiedEvenWeeks = hasOccupiedEvenWeeks,
                onIsSingleWeekChange = { isSingleWeek = it; isDoubleWeek = false },
                onIsDoubleWeekChange = { isDoubleWeek = it; isSingleWeek = false },
                onShowSectionDialog = {
                    tempStartSection = startSection
                    tempEndSection = endSection
                    showSectionDialog = true
                },
                onShowColorDialog = {
                    customColor = Color(selectedColor)
                    showColorDialog = true
                },
                onDeleteClick = { showDeleteDialog = true },
            )
        }
    } else {
    BlurBottomSheet(
        show = show,
        title = if (isEdit) "编辑课程" else "添加课程",
        backdrop = backdrop,
        dimBackground = true,
        sheetOffsetDp = statusBarsPadding + 5.dp,
        onDismissRequest = onDismiss,
        onSheetContentBackdropCreated = { sheetContentBackdrop = it },
        startAction = {
            if (liquidGlassBackdrop != null) {
                LiquidTopBarButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDismiss()
                    },
                    backdrop = sheetContentBackdrop ?: liquidGlassBackdrop,
                    icon = MiuixIcons.Normal.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.padding(start = 20.dp),
                    iconSize = 22.dp,
                )
            } else {
                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDismiss()
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Normal.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        endAction = {
            if (liquidGlassBackdrop != null) {
                LiquidTopBarButton(
                    onClick = onConfirmClick,
                    backdrop = sheetContentBackdrop ?: liquidGlassBackdrop,
                    icon = MiuixIcons.Ok,
                    contentDescription = "确定",
                    modifier = Modifier.padding(end = 20.dp),
                    iconSize = 23.dp,
                    iconTint = Color.White,
                    containerColor = if (isDark) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else MiuixTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
            } else {
                IconButton(
                    onClick = onConfirmClick,
                    modifier = Modifier.padding(end = 20.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "确定",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
    ) {
        AddCourseDialogContent(
            isEdit = isEdit,
            isDark = isDark,
            name = name,
            onNameChange = { name = it },
            classroom = classroom,
            onClassroomChange = { classroom = it },
            teacher = teacher,
            onTeacherChange = { teacher = it },
            dayOfWeek = dayOfWeek,
            onDayOfWeekChange = { dayOfWeek = it; selectedWeeks.clear() },
            startSection = startSection,
            endSection = endSection,
            totalWeeks = totalWeeks,
            selectedWeeks = selectedWeeks,
            selectedColor = selectedColor,
            onSelectedColorChange = { selectedColor = it },
            currentOccupiedWeeks = currentOccupiedWeeks,
            selectableWeeks = selectableWeeks,
            selectableOddWeeks = selectableOddWeeks,
            selectableEvenWeeks = selectableEvenWeeks,
            allSelectableSelected = allSelectableSelected,
            allSelectableOddSelected = allSelectableOddSelected,
            allSelectableEvenSelected = allSelectableEvenSelected,
            someSelectableOddSelected = someSelectableOddSelected,
            someSelectableEvenSelected = someSelectableEvenSelected,
            hasOccupiedOddWeeks = hasOccupiedOddWeeks,
            hasOccupiedEvenWeeks = hasOccupiedEvenWeeks,
            onIsSingleWeekChange = { isSingleWeek = it; isDoubleWeek = false },
            onIsDoubleWeekChange = { isDoubleWeek = it; isSingleWeek = false },
            onShowSectionDialog = {
                tempStartSection = startSection
                tempEndSection = endSection
                showSectionDialog = true
            },
            onShowColorDialog = {
                customColor = Color(selectedColor)
                showColorDialog = true
            },
            onDeleteClick = { showDeleteDialog = true },
        )
    }
    } // end of if (isTablet) else

    // 删除确认弹窗
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
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    showDeleteDialog = false
                },
            ) {
                Text("取消", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    course?.id?.let { onDelete(it) }
                    showDeleteDialog = false
                    onDismiss()
                },
            ) {
                Text("删除", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF44336))
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

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun AddCourseDialogContent(
    isEdit: Boolean,
    isDark: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    classroom: String,
    onClassroomChange: (String) -> Unit,
    teacher: String,
    onTeacherChange: (String) -> Unit,
    dayOfWeek: Int,
    onDayOfWeekChange: (Int) -> Unit,
    startSection: Int,
    endSection: Int,
    totalWeeks: Int,
    selectedWeeks: MutableSet<Int>,
    selectedColor: Long,
    onSelectedColorChange: (Long) -> Unit,
    currentOccupiedWeeks: Set<Int>,
    selectableWeeks: List<Int>,
    selectableOddWeeks: List<Int>,
    selectableEvenWeeks: List<Int>,
    allSelectableSelected: Boolean,
    allSelectableOddSelected: Boolean,
    allSelectableEvenSelected: Boolean,
    someSelectableOddSelected: Boolean,
    someSelectableEvenSelected: Boolean,
    hasOccupiedOddWeeks: Boolean,
    hasOccupiedEvenWeeks: Boolean,
    onIsSingleWeekChange: (Boolean) -> Unit,
    onIsDoubleWeekChange: (Boolean) -> Unit,
    onShowSectionDialog: () -> Unit,
    onShowColorDialog: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .overScrollVertical()
            .scrollEndHaptic(
                hapticFeedbackType = HapticFeedbackType.TextHandleMove
            )
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(68.dp))

        // 基本信息卡片
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = if (isDark) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                contentColor = MiuixTheme.colorScheme.onSurface
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 课程名称
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "课程名称",
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    NativeTextField(
                        value = name,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(0.65f),
                        hint = "必填",
                        singleLine = true,
                        textAlign = TextAlign.End,
                        textStyle = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // 教室
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "地点",
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    NativeTextField(
                        value = classroom,
                        onValueChange = onClassroomChange,
                        modifier = Modifier.fillMaxWidth(0.65f),
                        hint = "非必填",
                        singleLine = true,
                        textAlign = TextAlign.End,
                        textStyle = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // 教师
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "教师",
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    NativeTextField(
                        value = teacher,
                        onValueChange = onTeacherChange,
                        modifier = Modifier.fillMaxWidth(0.65f),
                        hint = "非必填",
                        singleLine = true,
                        textAlign = TextAlign.End,
                        textStyle = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // 上课星期卡片
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = if (isDark) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                contentColor = MiuixTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 17.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "上课星期",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                val dayIsDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val dayLabels = remember { listOf("一", "二", "三", "四", "五", "六", "日") }
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
                                else if (dayIsDark) Color(0xFF505050) else Color(0xFFF7F7F7),
                                contentColor = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary
                            ),
                            onClick = { onDayOfWeekChange(day) }
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

        // 节次范围
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = if (isDark) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                contentColor = MiuixTheme.colorScheme.onSurface
            )
        ) {
            ArrowPreference(
                title = "上课节次",
                endActions = {
                    Text(
                        text = "第${startSection} - ${endSection}节",
                        fontSize = 14.5.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                },
                onClick = onShowSectionDialog,
            )
        }

        // 周次设置
        val noDaySelected = dayOfWeek == 0
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth().alpha(if (noDaySelected) 0.5f else 1f),
            colors = CardDefaults.defaultColors(
                color = if (isDark) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                contentColor = MiuixTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val hasMixedSelection = someSelectableOddSelected && someSelectableEvenSelected

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        // 全部
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                state = if (allSelectableSelected) ToggleableState.On else ToggleableState.Off,
                                onClick = if (noDaySelected) null else {
                                    {
                                        if (allSelectableSelected) {
                                            selectedWeeks.clear()
                                        } else {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableWeeks)
                                        }
                                        onIsSingleWeekChange(false)
                                    }
                                },
                                colors = CheckboxDefaults.checkboxColors(
                                    uncheckedBackgroundColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "全部", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }

                        // 单周
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                state = when {
                                    hasMixedSelection -> ToggleableState.Off
                                    allSelectableOddSelected && !hasOccupiedOddWeeks -> ToggleableState.On
                                    someSelectableOddSelected -> ToggleableState.Indeterminate
                                    else -> ToggleableState.Off
                                },
                                onClick = if (noDaySelected) null else {
                                    {
                                        if (hasMixedSelection) {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableOddWeeks)
                                            onIsSingleWeekChange(true)
                                        } else if (allSelectableOddSelected) {
                                            selectedWeeks.clear()
                                            onIsSingleWeekChange(false)
                                        } else {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableOddWeeks)
                                            onIsSingleWeekChange(true)
                                        }
                                    }
                                },
                                colors = CheckboxDefaults.checkboxColors(
                                    uncheckedBackgroundColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "单周", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }

                        // 双周
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                state = when {
                                    hasMixedSelection -> ToggleableState.Off
                                    allSelectableEvenSelected && !hasOccupiedEvenWeeks -> ToggleableState.On
                                    someSelectableEvenSelected -> ToggleableState.Indeterminate
                                    else -> ToggleableState.Off
                                },
                                onClick = if (noDaySelected) null else {
                                    {
                                        if (hasMixedSelection) {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableEvenWeeks)
                                            onIsDoubleWeekChange(true)
                                        } else if (allSelectableEvenSelected) {
                                            selectedWeeks.clear()
                                            onIsDoubleWeekChange(false)
                                        } else {
                                            selectedWeeks.clear()
                                            selectedWeeks.addAll(selectableEvenWeeks)
                                            onIsDoubleWeekChange(true)
                                        }
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

                Spacer(modifier = Modifier.height(10.dp))

                // 周次网格
                val columns = 6
                val rows = remember(totalWeeks, columns) { (totalWeeks + columns - 1) / columns }
                val primaryColor = MiuixTheme.colorScheme.primary
                val outlineColor = MiuixTheme.colorScheme.outline
                val onSurfaceColor = MiuixTheme.colorScheme.onSurface
                val onSurfaceSummaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                val occupiedColor = if (isDark) Color(0xFF4A4A4A) else Color(0xFFF0F0F0)
                val defaultCardColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)

                val weekStates = (1..totalWeeks).map { weekNum ->
                    val isSelected = weekNum in selectedWeeks
                    val isOccupied = weekNum in currentOccupiedWeeks
                    Triple(weekNum, isSelected, isOccupied)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0 until columns) {
                                val idx = row * columns + col
                                if (idx < weekStates.size) {
                                    val (weekNum, isSelected, isOccupied) = weekStates[idx]
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        cornerRadius = 10.dp,
                                        insideMargin = PaddingValues(0.dp),
                                        pressFeedbackType = PressFeedbackType.Sink,
                                        showIndication = !noDaySelected && !isOccupied,
                                        colors = CardDefaults.defaultColors(
                                            color = when {
                                                noDaySelected -> defaultCardColor
                                                isSelected -> primaryColor
                                                isOccupied -> occupiedColor
                                                else -> defaultCardColor
                                            },
                                            contentColor = when {
                                                noDaySelected -> outlineColor
                                                isSelected -> Color.White
                                                isOccupied -> outlineColor
                                                else -> onSurfaceColor
                                            }
                                        ),
                                        onClick = if (noDaySelected || isOccupied) null else {
                                            {
                                                if (isSelected) {
                                                    selectedWeeks.remove(weekNum)
                                                } else {
                                                    selectedWeeks.add(weekNum)
                                                }
                                                onIsSingleWeekChange(false)
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
                                                    noDaySelected -> if (isDark) Color(0xFF606060) else outlineColor
                                                    isSelected -> Color.White
                                                    isOccupied -> if (isDark) Color(0xFF606060) else outlineColor
                                                    else -> onSurfaceSummaryColor
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

        // 课程颜色选择
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = if (isDark) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                contentColor = MiuixTheme.colorScheme.onSurface
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 16.dp),
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
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val colorColumns = 6
                    val allColors = remember { Course.courseColors }
                    val totalItems = remember(allColors) { allColors.size + 1 }
                    val colorRows = remember(totalItems, colorColumns) { (totalItems + colorColumns - 1) / colorColumns }
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
                                                            onSelectedColorChange(color)
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
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
                                                .background(if (isDark) Color(0xFF303030) else Color(0xF8F8F8F8))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Card(
                                                modifier = Modifier.fillMaxSize(),
                                                cornerRadius = 8.dp,
                                                insideMargin = PaddingValues(0.dp),
                                                colors = CardDefaults.defaultColors(
                                                    color = Color(color).copy(alpha = if (isDark) 0.22f else 0.16f),
                                                    contentColor = Color.White
                                                ),
                                                onClick = { onSelectedColorChange(color) }
                                            ) {}
                                        }
                                    }
                                } else if (colorIndex == allColors.size) {
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
                                                            onShowColorDialog()
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
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
                                                .background(if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Card(
                                                modifier = Modifier.fillMaxSize(),
                                                cornerRadius = 8.dp,
                                                insideMargin = PaddingValues(0.dp),
                                                colors = CardDefaults.defaultColors(
                                                    color = bgColor,
                                                    contentColor = hintColor
                                                ),
                                                onClick = onShowColorDialog
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

        // 删除按钮（仅编辑模式）
        if (isEdit) {
            Button(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onDeleteClick()
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
                Text("删除", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF44336))
            }
        }
        val configuration = LocalConfiguration.current
        val isTablet = configuration.screenWidthDp >= 600
        Spacer(modifier = Modifier.height(if (isTablet) 4.dp else statusBarsPadding + 65.dp))
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
