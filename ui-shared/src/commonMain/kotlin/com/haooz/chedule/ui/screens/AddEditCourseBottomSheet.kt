/** 添加课程底部弹窗 */
package com.haooz.chedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.truncateNote
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.CourseEditStr
import com.haooz.chedule.i18n.NoteStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.CardReveal
import com.haooz.chedule.ui.components.WeekRangeSelectGrid
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberImeDismiss
import com.haooz.chedule.ui.utils.rememberOpenKey
import com.haooz.softglass.backdrop.GlassBackdrop
import kotlinx.datetime.Clock
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.NativeTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.BackdropHolder
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalBlurBottomSheetContentExpanded
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.haooz.softglass.shape.continuousClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AddEditCourseBottomSheet(
    show: Boolean,
    courses: List<Course>,
    liquidGlassBackdrop: GlassBackdrop? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (Course) -> Unit,
    editCourse: Course? = null,
    getOccupiedWeeks: (dayOfWeek: Int, startSection: Int, endSection: Int, excludeIds: List<String>, startTime: String?, endTime: String?) -> Set<Int> = { _, _, _, _, _, _ -> emptySet() },
    sectionTimes: Map<Int, String> = emptyMap(),
) {
    val hapticFeedback = LocalHapticFeedback.current
    // 弹窗要关的时候先收键盘：键盘托在关闭动画上会残留到下一个界面。
    // 输入框是 AndroidView 里的原生 EditText，Compose 侧清不掉它的焦点，交给平台实现。
    val dismissKeyboard = rememberImeDismiss()
    val totalWeeks = 20
    val totalSections = 12
    // 二级弹窗在弹窗作用域外读不到 LocalSheetContentBackdrop，用非快照 holder 接收
    val sheetContentBackdropHolder = remember { BackdropHolder() }
    val isEditMode = editCourse != null
    // 表单状态只随「打开」重建，不能随 show 重置：关闭时还有退出动画
    val openKey = rememberOpenKey(show)

    val latestCourse = remember(courses) {
        courses.maxByOrNull { it.endWeek }
    }
    val defaultClassroom = latestCourse?.classroom ?: ""
    val defaultTeacher = latestCourse?.teacher ?: ""

    // 地点 / 教师用 MutableState 直接交给输入行自己读：本组合作用域**不读**它们，
    // 于是每敲一个字只重组那一行，不会把 4 张卡 + 20 格周次整片重来（见 SheetTextFieldRow）
    val classroomState = remember(openKey) {
        mutableStateOf(editCourse?.classroom ?: defaultClassroom)
    }
    val teacherState = remember(openKey) {
        mutableStateOf(editCourse?.teacher ?: defaultTeacher)
    }
    // 备注：这张表单里只能写「所有周」那条，单周备注归详情弹窗管（不在这里给范围选择）
    val noteState = remember(openKey) {
        mutableStateOf(editCourse?.note ?: "")
    }

    var dayOfWeek by remember(openKey) { mutableIntStateOf(editCourse?.dayOfWeek ?: 0) }
    var startSection by remember(openKey) {
        mutableIntStateOf(
            editCourse?.startSection ?: latestCourse?.startSection ?: 0
        )
    }
    var endSection by remember(openKey) {
        mutableIntStateOf(
            editCourse?.endSection ?: latestCourse?.endSection ?: 0
        )
    }

    var showSectionDialog by remember(openKey) { mutableStateOf(false) }
    var tempStartSection by remember(openKey) { mutableIntStateOf(if (startSection > 0) startSection else 1) }
    var tempEndSection by remember(openKey) { mutableIntStateOf(if (endSection > 0) endSection else 1) }

    var isCustomTime by remember(openKey) { mutableStateOf(editCourse?.isCustomTime ?: false) }
    var customStartTime by remember(openKey) { mutableStateOf(editCourse?.customStartTime ?: "") }
    var customEndTime by remember(openKey) { mutableStateOf(editCourse?.customEndTime ?: "") }
    var showTimeDialog by remember(openKey) { mutableStateOf(false) }
    var timeError by remember(openKey) { mutableStateOf(false) }
    var tempStartHour by remember(openKey) { mutableIntStateOf(parseTimeHour(editCourse?.customStartTime)) }
    var tempStartMinute by remember(openKey) { mutableIntStateOf(parseTimeMinute(editCourse?.customStartTime)) }
    var tempEndHour by remember(openKey) { mutableIntStateOf(parseTimeHour(editCourse?.customEndTime)) }
    var tempEndMinute by remember(openKey) { mutableIntStateOf(parseTimeMinute(editCourse?.customEndTime)) }

    // 勾选自定义时间时自动从节次时间预填
    LaunchedEffect(isCustomTime) {
        if (isCustomTime && customStartTime.isBlank() && customEndTime.isBlank()) {
            val sectionStart = sectionTimes[startSection]?.split("-")?.firstOrNull()?.trim()
            val sectionEnd = sectionTimes[endSection]?.split("-")?.lastOrNull()?.trim()
            if (sectionStart != null && sectionEnd != null) {
                customStartTime = sectionStart
                customEndTime = sectionEnd
                tempStartHour = parseTimeHour(sectionStart)
                tempStartMinute = parseTimeMinute(sectionStart)
                tempEndHour = parseTimeHour(sectionEnd)
                tempEndMinute = parseTimeMinute(sectionEnd)
            }
        }
    }

    var currentOccupiedWeeks by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 必须带上 editCourse?.id：按 id 排除自身，编辑对象变化而星期/节次相同时缺此 key 会误判自身周次为占用
    LaunchedEffect(dayOfWeek, startSection, endSection, isCustomTime, customStartTime, customEndTime, editCourse?.id) {
        currentOccupiedWeeks = getOccupiedWeeks(
            dayOfWeek,
            startSection,
            endSection,
            editCourse?.let { listOf(it.id) } ?: emptyList(),
            if (isCustomTime) customStartTime.ifBlank { null } else null,
            if (isCustomTime) customEndTime.ifBlank { null } else null
        )
    }

    val selectedWeeks = remember(openKey) {
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

    val selectableWeeks =
        remember(allWeeks, currentOccupiedWeeks) { allWeeks.filter { it !in currentOccupiedWeeks } }
    val selectableOddWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 1 } }
    val selectableEvenWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 0 } }

    val allSelectableSelected =
        selectableWeeks.isNotEmpty() && selectableWeeks.all { it in selectedWeeks }
    val allSelectableOddSelected = selectableOddWeeks.all { it in selectedWeeks }
    val allSelectableEvenSelected = selectableEvenWeeks.all { it in selectedWeeks }
    val someSelectableOddSelected = selectableOddWeeks.any { it in selectedWeeks }
    val someSelectableEvenSelected = selectableEvenWeeks.any { it in selectedWeeks }
    val isDark = isAppDarkTheme()
    val isTablet = isTabletWidth()

    // 4 张卡共用一套配色，别各建一次。卡底色莫奈下取 surfaceContainerHigh（比 sheet 底亮一档，
    // 与 DayColumn 待放格同一套）；非莫奈保持原硬编码。
    val sheetCardColors = CardDefaults.defaultColors(
        color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh
        else if (isAppDarkTheme()) Color(0xFF303030) else Color(0xFFFFFFFF),
        contentColor = MiuixTheme.colorScheme.onSurface
    )

    val hasOccupiedOddWeeks =
        remember(selectableOddWeeks, oddWeeks) { selectableOddWeeks.size != oddWeeks.size }
    val hasOccupiedEvenWeeks =
        remember(selectableEvenWeeks, evenWeeks) { selectableEvenWeeks.size != evenWeeks.size }

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

            // 备注框写的是「所有周」那条。只要文本变了就视为「重设全部周备注」——
            // 详情弹窗里按周写过的单周备注一并清掉，否则改完这里某些周还是旧文案，
            // 看起来就是「保存没生效」。没改文本则原样保留单周备注。
            val newNote = noteState.value.trim().truncateNote()
            val noteChanged = newNote != (editCourse?.note ?: "")
            val weekNotesToSave = if (noteChanged) {
                emptyMap()
            } else {
                editCourse?.weekNotes ?: emptyMap()
            }

            val course = Course(
                id = editCourse?.id ?: randomUuidV4(),
                name = editCourse?.name ?: courses.firstOrNull()?.name ?: "",
                classroom = classroomState.value.trim(),
                teacher = teacherState.value.trim(),
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                endSection = endSection,
                startWeek = minWeek,
                endWeek = maxWeek,
                weekType = weekType,
                colorRes = editCourse?.colorRes ?: courses.firstOrNull()?.colorRes
                ?: Course.courseColors.first(),
                selectedWeeks = weeksToSave,
                isCustomTime = isCustomTime,
                customStartTime = if (isCustomTime) customStartTime else null,
                customEndTime = if (isCustomTime) customEndTime else null,
                // System.currentTimeMillis() 是 JVM 专有（Kotlin/Native 没有），
                // 改用 kotlinx-datetime —— 返回值同为 epoch millis，与 :core 的 Course.lastModified 口径一致
                lastModified = Clock.System.now().toEpochMilliseconds(),
                note = newNote,
                weekNotes = weekNotesToSave,
            )
            // 先收键盘再回调：onConfirm 会立刻触发关闭/跳转，晚一步键盘就托在动画上
            dismissKeyboard()
            onConfirm(course)
            onDismissRequest()
        } else {
            // 一节课都没选就点确定：以前是静默不动（看起来像点了没反应）
            showToast(CourseEditStr.selectWeeksFirst())
        }
    }

    val startAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        LiquidTopBarButton(
            onClick = {
                dismissKeyboard()
                onDismissRequest()
            },
            backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
            icon = MiuixIcons.Normal.Close,
            contentDescription = CommonStr.close(),
            modifier = Modifier.padding(start = if (isTablet) 16.dp else 18.dp),
            iconSize = 24.dp,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }

    val endAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        LiquidTopBarButton(
            onClick = onConfirmClick,
            backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
            icon = MiuixIcons.Ok,
            contentDescription = CommonStr.ok(),
            modifier = Modifier.padding(end = if (isTablet) 16.dp else 18.dp),
            iconSize = 25.dp,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }

    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (LocalBlurBottomSheetContentExpanded.current) Modifier.fillMaxHeight()
                    else Modifier
                )
                .overScrollVertical()
                .scrollEndHaptic(
                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                )
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(if (isTablet) 56.dp else 58.dp))
            CardReveal(index = 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = sheetCardColors,
            ) {
                SheetTextFieldRow(
                    label = CourseEditStr.location(),
                    state = classroomState,
                    hint = CommonStr.notRequired(),
                    topPadding = 17.dp,
                    bottomPadding = 14.dp,
                )
                SheetTextFieldRow(
                    label = CourseEditStr.teacher(),
                    state = teacherState,
                    hint = CommonStr.notRequired(),
                    topPadding = 14.dp,
                    bottomPadding = 14.dp,
                )
                SheetTextFieldRow(
                    label = NoteStr.noteLabel(),
                    state = noteState,
                    // 与地点/教师同一套「非必填」提示；占位示例留给详情弹窗那块便签，一行里两种口气太乱
                    hint = CommonStr.notRequired(),
                    topPadding = 14.dp,
                    bottomPadding = 17.dp,
                    limitNoteLength = true,
                )
            }
            }

            CardReveal(index = 1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = sheetCardColors,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 17.dp, horizontal = 16.dp)
                ) {
                    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = CourseEditStr.classDay(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            state = if (isCustomTime) ToggleableState.On else ToggleableState.Off,
                            onClick = { isCustomTime = !isCustomTime },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = CourseEditStr.customTime(),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dayLabels = CourseEditStr.dayLabels
                        for (day in 1..7) {
                            val isSelected = day == dayOfWeek
                            val bgColor = if (isSelected) MiuixTheme.colorScheme.primary
                                // 未选中格子底：莫奈下 surfaceContainerHighest（比分组卡再亮一档）
                                else if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHighest
                                else if (isDark) Color(0xFF363636) else Color(0xFFF2F2F2)
                            // 选中格文字：primary 底上的对色取 onPrimary（莫奈下 primary 亮度不定，
                            // 死白对比可能不足）；非莫奈时 onPrimary 即白，行为不变
                            val textColor = if (isSelected) MiuixTheme.colorScheme.onPrimary
                                else MiuixTheme.colorScheme.onSurfaceVariantSummary
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .continuousClip(10.dp)
                                    .background(bgColor)
                                    .clickable(
                                        interactionSource = null,
                                        indication = null,
                                    ) {
                                        dayOfWeek = day
                                        selectedWeeks.clear()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayLabels[day - 1](),
                                    fontSize = 14.sp,
                                    color = textColor,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
            }

            CardReveal(index = 2) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = sheetCardColors,
            ) {
                CardExpand(visible = isCustomTime) {
                    ArrowPreference(
                        title = CourseEditStr.classTime(),
                        endActions = {
                            Text(
                                text = "$customStartTime - $customEndTime",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                textAlign = TextAlign.End,
                            )
                        },
                        onClick = {
                            tempStartHour = parseTimeHour(customStartTime)
                            tempStartMinute = parseTimeMinute(customStartTime)
                            tempEndHour = parseTimeHour(customEndTime)
                            tempEndMinute = parseTimeMinute(customEndTime)
                            timeError = false
                            showTimeDialog = true
                        },
                        holdDownState = showTimeDialog
                    )
                }
                CardExpand(visible = !isCustomTime) {
                    ArrowPreference(
                        title = CourseEditStr.classPeriods(),
                        endActions = {
                            Text(
                                text = if (startSection > 0) CourseEditStr.periodsRange(startSection, endSection) else CommonStr.notSet(),
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                textAlign = TextAlign.End,
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
            }
            }

            val noDaySelected = dayOfWeek == 0
            CardReveal(index = 3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (noDaySelected) 0.5f else 1f),
                colors = sheetCardColors,
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
                            text = CourseEditStr.classWeeks(),
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
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = CourseEditStr.all(),
                                    fontSize = 15.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }

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
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = CourseEditStr.oddWeeks(),
                                    fontSize = 15.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }

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
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = CourseEditStr.evenWeeks(),
                                    fontSize = 15.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 支持按住滑动选择连续区间
                    WeekRangeSelectGrid(
                        totalWeeks = totalWeeks,
                        selectedWeeks = selectedWeeks.toSet(),
                        occupiedWeeks = currentOccupiedWeeks,
                        enabled = !noDaySelected,
                        isDark = isDark,
                        onToggleWeek = { week ->
                            if (week in selectedWeeks) selectedWeeks.remove(week)
                            else selectedWeeks.add(week)
                        },
                        onReplaceWeeks = { weeks ->
                            selectedWeeks.clear()
                            selectedWeeks.addAll(weeks)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            }
            Spacer(modifier = Modifier.height(if (isTablet) 4.dp else statusBarsPadding + 60.dp))
        }
    }

    if (isTablet) {
        BlurBottomSheetTablet(
            show = show,
            title = if (isEditMode) CourseEditStr.editCourse() else CourseEditStr.addCourse(),
            dimBackground = true,
            onDismissRequest = onDismissRequest,
            onSheetContentBackdropCreated = { sheetContentBackdropHolder.value = it },
            startAction = startAction,
            endAction = endAction,
        ) {
            sheetContent()
        }
    } else {
        BlurBottomSheet(
            show = show,
            title = if (isEditMode) CourseEditStr.editCourse() else CourseEditStr.addCourse(),
            dimBackground = true,
            onDismissRequest = onDismissRequest,
            onSheetContentBackdropCreated = { sheetContentBackdropHolder.value = it },
            startAction = startAction,
            endAction = endAction,
        ) {
            sheetContent()
        }
    }

    OverlayDialog(
        title = CourseEditStr.selectSectionTitle(),
        show = showSectionDialog,
        liquidGlassBackdrop = sheetContentBackdropHolder.value ?: liquidGlassBackdrop,
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
                        text = CourseEditStr.start(),
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
                        text = CourseEditStr.end(),
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
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showSectionDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.ok(),
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

    OverlayDialog(
        title = CourseEditStr.selectTimeTitle(),
        show = showTimeDialog,
        liquidGlassBackdrop = sheetContentBackdropHolder.value ?: liquidGlassBackdrop,
        onDismissRequest = { showTimeDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TimeRangePickerGroup(
                startHour = tempStartHour,
                startMinute = tempStartMinute,
                endHour = tempEndHour,
                endMinute = tempEndMinute,
                onStartHourChange = { tempStartHour = it; timeError = false },
                onStartMinuteChange = { tempStartMinute = it; timeError = false },
                onEndHourChange = { tempEndHour = it; timeError = false },
                onEndMinuteChange = { tempEndMinute = it; timeError = false }
            )
            if (timeError) {
                Text(
                    text = CourseEditStr.timeSameError(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = Color(0xFFF44336),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

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
                        showTimeDialog = false
                        timeError = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.ok(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        val startMinutes = tempStartHour * 60 + tempStartMinute
                        val endMinutes = tempEndHour * 60 + tempEndMinute
                        // 结束不晚于开始按跨午夜处理（次日结束），只有起止完全相同才是无效输入
                        if (startMinutes != endMinutes) {
                            customStartTime = formatTime(tempStartHour, tempStartMinute)
                            customEndTime = formatTime(tempEndHour, tempEndMinute)
                            timeError = false
                            showTimeDialog = false
                        } else {
                            timeError = true
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


/**
 * 一行「标签 + 原生输入框」。
 *
 * 文本状态由 [state] 持有、**只在这里读**：父级组合期不订阅它，敲字只重组这一行，
 * 不会带动整张弹窗（4 张卡 + 20 格周次）一起重来。
 */
@Composable
private fun SheetTextFieldRow(
    label: String,
    state: MutableState<String>,
    hint: String,
    topPadding: Dp,
    bottomPadding: Dp,
    /** true 时按 [Course.NOTE_MAX_LENGTH] 截断输入（备注用），地点/教师不截 */
    limitNoteLength: Boolean = false,
    /** 输入框下方的常驻说明（右对齐小字）。hint 只在空值时可见，规则说明得靠它 */
    caption: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = bottomPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            NativeTextField(
                value = state.value,
                onValueChange = { input ->
                    state.value = if (limitNoteLength) input.truncateNote() else input
                },
                modifier = Modifier.fillMaxWidth(0.65f),
                hint = hint,
                singleLine = true,
                textAlign = TextAlign.End,
                textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
            )
        }
        if (caption != null) {
            Text(
                text = caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** 时间段 时:分 双滚轮选择器 */
@Composable
private fun TimeRangePickerGroup(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStartHourChange: (Int) -> Unit,
    onStartMinuteChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    onEndMinuteChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        NumberPicker(
            value = startHour,
            onValueChange = onStartHourChange,
            range = 0..23,
            visibleItemCount = 3,
            itemHeight = 60.dp,
            label = { it.toString().padStart(2, '0') },
            wrapAround = true,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ":",
            style = MiuixTheme.textStyles.paragraph,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding().offset(y = (-2).dp)
        )
        val sMinIdx = minuteValues.indexOf(startMinute).coerceAtLeast(0)
        NumberPicker(
            value = sMinIdx,
            onValueChange = { onStartMinuteChange(minuteValues[it]) },
            range = minuteValues.indices,
            visibleItemCount = 3,
            itemHeight = 60.dp,
            label = { minuteValues[it].toString().padStart(2, '0') },
            wrapAround = true,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "-",
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding()
        )
        NumberPicker(
            value = endHour,
            onValueChange = onEndHourChange,
            range = 0..23,
            visibleItemCount = 3,
            itemHeight = 60.dp,
            label = { it.toString().padStart(2, '0') },
            wrapAround = true,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ":",
            style = MiuixTheme.textStyles.paragraph,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding().offset(y = (-2).dp)
        )
        val eMinIdx = minuteValues.indexOf(endMinute).coerceAtLeast(0)
        NumberPicker(
            value = eMinIdx,
            onValueChange = { onEndMinuteChange(minuteValues[it]) },
            range = minuteValues.indices,
            visibleItemCount = 3,
            itemHeight = 60.dp,
            label = { minuteValues[it].toString().padStart(2, '0') },
            wrapAround = true,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

/** 解析 "HH:mm" 中的小时，无效时返回 8 */
private fun parseTimeHour(time: String?): Int {
    if (time.isNullOrBlank()) return 8
    val parts = time.split(":")
    return parts.firstOrNull()?.toIntOrNull()?.coerceIn(0, 23) ?: 8
}

/** 解析 "HH:mm" 中的分钟，无效时返回 0 */
private fun parseTimeMinute(time: String?): Int {
    if (time.isNullOrBlank()) return 0
    val parts = time.split(":")
    return parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
}

private fun formatTime(hour: Int, minute: Int): String {
    return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

/** 自定义时间弹窗可用分钟值（每 5 分钟一档） */
private val minuteValues = (0..59 step 5).toList()
