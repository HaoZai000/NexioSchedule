/** 添加课程底部弹窗 */
package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.UUID
import com.haooz.chedule.ui.effects.blur.BlurBottomSheet
import com.haooz.chedule.ui.effects.blur.BlurBottomSheetTablet

/**
 * 添加课程底部弹窗
 *
 * @param show 是否显示
 * @param courses 当前课表的所有课程，用于获取默认地点和教师（取最晚周次的课程）
 * @param backdrop 模糊背景
 * @param onDismissRequest 关闭回调
 * @param onConfirm 确认回调，返回新创建的课程
 * @param getOccupiedWeeks 获取已占用周次的回调
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun AddEditCourseBottomSheet(
    show: Boolean,
    courses: List<Course>,
    backdrop: LayerBackdrop?,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (Course) -> Unit,
    editCourse: Course? = null,
    getOccupiedWeeks: (dayOfWeek: Int, startSection: Int, endSection: Int, excludeIds: List<String>) -> Set<Int> = { _, _, _, _ -> emptySet() },
) {
    val hapticFeedback = LocalHapticFeedback.current
    val totalWeeks = 20
    val totalSections = 12
    var sheetContentBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
    val isEditMode = editCourse != null

    // 取最晚周次的课程作为默认地点和教师
    val latestCourse = remember(courses) {
        courses.maxByOrNull { it.endWeek }
    }
    val defaultClassroom = latestCourse?.classroom ?: ""
    val defaultTeacher = latestCourse?.teacher ?: ""

    // 编辑状态（每次弹窗打开时根据 editCourse 初始化）
    var classroom by remember(show) { mutableStateOf(editCourse?.classroom ?: defaultClassroom) }
    var teacher by remember(show) { mutableStateOf(editCourse?.teacher ?: defaultTeacher) }
    var dayOfWeek by remember(show) { mutableIntStateOf(editCourse?.dayOfWeek ?: 0) }
    var startSection by remember(show) { mutableIntStateOf(editCourse?.startSection ?: latestCourse?.startSection ?: 0) }
    var endSection by remember(show) { mutableIntStateOf(editCourse?.endSection ?: latestCourse?.endSection ?: 0) }

    // 节次选择弹窗状态
    var showSectionDialog by remember(show) { mutableStateOf(false) }
    var tempStartSection by remember(show) { mutableIntStateOf(if (startSection > 0) startSection else 1) }
    var tempEndSection by remember(show) { mutableIntStateOf(if (endSection > 0) endSection else 1) }

    // 根据当前选择的星期和节次动态计算已占用的周次（排除自身）
    val currentOccupiedWeeks = remember(dayOfWeek, startSection, endSection) {
        getOccupiedWeeks(dayOfWeek, startSection, endSection, editCourse?.let { listOf(it.id) } ?: emptyList())
    }

    // 周次选择状态（编辑模式预填已选周次，每次弹窗打开时重置）
    val selectedWeeks = remember(show) {
        mutableStateSetOf<Int>().apply {
            if (editCourse != null) {
                if (editCourse.selectedWeeks.isNotEmpty()) {
                    addAll(editCourse.selectedWeeks)
                } else {
                    for (w in editCourse.startWeek..editCourse.endWeek) {
                        when (editCourse.weekType) {
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
    val isDark = isAppDarkTheme()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    val hasOccupiedOddWeeks = remember(selectableOddWeeks, oddWeeks) { selectableOddWeeks.size != oddWeeks.size }
    val hasOccupiedEvenWeeks = remember(selectableEvenWeeks, evenWeeks) { selectableEvenWeeks.size != evenWeeks.size }

    val onConfirmClick: () -> Unit = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        if (selectedWeeks.isNotEmpty()) {
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

            val course = Course(
                id = editCourse?.id ?: UUID.randomUUID().toString(),
                name = editCourse?.name ?: courses.firstOrNull()?.name ?: "",
                classroom = classroom.trim(),
                teacher = teacher.trim(),
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                endSection = endSection,
                startWeek = minWeek,
                endWeek = maxWeek,
                weekType = weekType,
                colorRes = editCourse?.colorRes ?: courses.firstOrNull()?.colorRes ?: Course.courseColors.first(),
                selectedWeeks = weeksToSave,
                lastModified = System.currentTimeMillis()
            )
            onConfirm(course)
            onDismissRequest()
        }
    }

    val startAction: @Composable () -> Unit = {
        if (liquidGlassBackdrop != null) {
            com.haooz.chedule.ui.effects.liquidglass.LiquidTopBarButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onDismissRequest()
                },
                backdrop = sheetContentBackdrop ?: liquidGlassBackdrop,
                icon = MiuixIcons.Normal.Close,
                contentDescription = "关闭",
                modifier = Modifier.padding(start = 20.dp),
                iconSize = 22.dp,
                useBackdropShadow = true
            )
        } else {
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onDismissRequest()
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
    }

    val endAction: @Composable () -> Unit = {
        if (liquidGlassBackdrop != null) {
            com.haooz.chedule.ui.effects.liquidglass.LiquidTopBarButton(
                onClick = onConfirmClick,
                backdrop = sheetContentBackdrop ?: liquidGlassBackdrop,
                icon = MiuixIcons.Ok,
                contentDescription = "确定",
                modifier = Modifier.padding(end = 20.dp),
                iconSize = 23.dp,
                iconTint = Color.White,
                useBackdropShadow = true,
                containerColor = if (isAppDarkTheme()) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else MiuixTheme.colorScheme.primary.copy(alpha = 0.9f)
            )
        } else {
            IconButton(
                onClick = onConfirmClick,
                modifier = Modifier.padding(end = 20.dp)
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = "确定",
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }

    val sheetContent: @Composable () -> Unit = {
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
            // 地点教师卡片
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = if (isAppDarkTheme()) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                    contentColor = MiuixTheme.colorScheme.onSurface
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 14.dp),
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
                        onValueChange = { classroom = it },
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
                        onValueChange = { teacher = it },
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

            // 上课星期卡片
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = if (isAppDarkTheme()) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                    contentColor = MiuixTheme.colorScheme.onSurface
                ),
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
                    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
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
                                    else if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7),
                                    contentColor = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                ),
                                onClick = {
                                    dayOfWeek = day
                                    selectedWeeks.clear()
                                }
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
            // 上课节次卡片
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = if (isAppDarkTheme()) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                    contentColor = MiuixTheme.colorScheme.onSurface
                ),
            ) {
                ArrowPreference(
                    title = "上课节次",
                    endActions = {
                        Text(
                            text = if (startSection > 0) "第${startSection} - ${endSection}节" else "未设置",
                            fontSize = 14.5.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = {
                        tempStartSection = if (startSection > 0) startSection else 1
                        tempEndSection = if (endSection > 0) endSection else 1
                        showSectionDialog = true
                    },
                    holdDownState = showSectionDialog
                )
            }

            // 上课周次卡片
            val noDaySelected = dayOfWeek == 0
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth().alpha(if (noDaySelected) 0.5f else 1f),
                colors = CardDefaults.defaultColors(
                    color = if (isAppDarkTheme()) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                    contentColor = MiuixTheme.colorScheme.onSurface
                ),
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
                                verticalAlignment = Alignment.CenterVertically
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
                                verticalAlignment = Alignment.CenterVertically
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
                                            } else if (allSelectableOddSelected) {
                                                selectedWeeks.clear()
                                            } else {
                                                selectedWeeks.clear()
                                                selectedWeeks.addAll(selectableOddWeeks)
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
                                verticalAlignment = Alignment.CenterVertically
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
                                            } else if (allSelectableEvenSelected) {
                                                selectedWeeks.clear()
                                            } else {
                                                selectedWeeks.clear()
                                                selectedWeeks.addAll(selectableEvenWeeks)
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
                                        val cardColor = when {
                                            noDaySelected -> defaultCardColor
                                            isSelected -> primaryColor
                                            isOccupied -> occupiedColor
                                            else -> defaultCardColor
                                        }
                                        val textColor = when {
                                            noDaySelected -> if (isDark) Color(0xFF606060) else outlineColor
                                            isSelected -> Color.White
                                            isOccupied -> if (isDark) Color(0xFF606060) else outlineColor
                                            else -> onSurfaceSummaryColor
                                        }
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(32.dp),
                                            cornerRadius = 10.dp,
                                            insideMargin = PaddingValues(0.dp),
                                            pressFeedbackType = PressFeedbackType.Sink,
                                            showIndication = !noDaySelected && !isOccupied,
                                            colors = CardDefaults.defaultColors(
                                                color = cardColor,
                                                contentColor = if (isSelected) Color.White else outlineColor
                                            ),
                                            onClick = if (noDaySelected || isOccupied) null else {
                                                {
                                                    if (isSelected) {
                                                        selectedWeeks.remove(weekNum)
                                                    } else {
                                                        selectedWeeks.add(weekNum)
                                                    }
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
                                                    color = textColor
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
            Spacer(modifier = Modifier.height(if (isTablet) 4.dp else 160.dp))
        }
    }

    if (isTablet) {
        BlurBottomSheetTablet(
            show = show,
            title = if (isEditMode) "编辑课程" else "添加课程",
            dimBackground = true,
            onDismissRequest = onDismissRequest,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onSheetContentBackdropCreated = { sheetContentBackdrop = it },
            startAction = startAction,
            endAction = endAction,
        ) {
            sheetContent()
        }
    } else {
        BlurBottomSheet(
            show = show,
            title = if (isEditMode) "编辑课程" else "添加课程",
            backdrop = backdrop,
            dimBackground = true,
            onDismissRequest = onDismissRequest,
            sheetOffsetDp = 100.dp,
            onSheetContentBackdropCreated = { sheetContentBackdrop = it },
            startAction = startAction,
            endAction = endAction,
        ) {
            sheetContent()
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
            androidx.compose.runtime.LaunchedEffect(tempStartSection) {
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
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
