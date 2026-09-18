/** 添加/编辑课程对话框 - Blur版本 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.WeekRangeSelectGrid
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NativeTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.BackdropHolder
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/** 揭示分组数（基本信息/星期/节次/周次/颜色/删除） */
private const val REVEAL_GROUP_COUNT = 6

/** 每组揭示间隔 */
private const val REVEAL_STEP_MS = 60L

// 字段只在消费它的那张卡片内读取；已在 compose-stability.conf 声明 stable
private class AddCourseFormState(
    course: Course?,
    selectedDay: Int,
    defaultStartSection: Int,
    defaultEndSection: Int,
    defaultWeeks: Set<Int> = emptySet(),
) {
    var name by mutableStateOf(course?.name ?: "")
    var classroom by mutableStateOf(course?.classroom ?: "")
    var teacher by mutableStateOf(course?.teacher ?: "")
    var dayOfWeek by mutableIntStateOf(course?.dayOfWeek ?: selectedDay)
    var startSection by mutableIntStateOf(course?.startSection ?: defaultStartSection)
    var endSection by mutableIntStateOf(course?.endSection ?: defaultEndSection)
    var selectedColor by mutableLongStateOf(course?.colorRes ?: Course.courseColors.first())
    var isCustomTime by mutableStateOf(course?.isCustomTime ?: false)
    var customStartTime by mutableStateOf(course?.customStartTime ?: "")
    var customEndTime by mutableStateOf(course?.customEndTime ?: "")

    val selectedWeeks: MutableSet<Int> = mutableStateSetOf<Int>().apply {
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
        } else if (defaultWeeks.isNotEmpty()) {
            // 空白格添加：预选调课来源周 / 当前浏览周
            addAll(defaultWeeks)
        }
    }

    /** 切换星期：周次占用随星期变化，已选周次一并清空 */
    fun selectDay(day: Int) {
        dayOfWeek = day
        selectedWeeks.clear()
    }

}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun AddCourseDialog(
    show: Boolean,
    course: Course?,
    selectedDay: Int,
    liquidGlassBackdrop: Backdrop? = null,
    totalWeeks: Int = 20,
    totalSections: Int = 12,
    defaultStartSection: Int = 1,
    defaultEndSection: Int = 2,
    defaultWeeks: Set<Int> = emptySet(),
    getOccupiedWeeks: (dayOfWeek: Int, startSection: Int, endSection: Int, excludeIds: List<String>, startTime: String?, endTime: String?) -> Set<Int> = { _, _, _, _, _, _ -> emptySet() },
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit,
    onDelete: (String) -> Unit,
    sectionTimes: Map<Int, String> = emptyMap(),
) {
    val isEdit = course != null
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val isDark = isAppDarkTheme()
    // 嵌套弹窗在 root popup host，强制跟随应用主题而非壁纸主题
    val appDialogDark = rememberAppSettingDark()
    val appDialogController = remember(appDialogDark) {
        ThemeController(if (appDialogDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
    }
    // 二级弹窗在弹窗作用域外读不到 LocalSheetContentBackdrop，用非快照 holder 接收
    val sheetContentBackdropHolder = remember { BackdropHolder() }

    // 卡片始终占位参与布局，仅 graphicsLayer 做透明/位移/缩放，避免外高闪烁
    var revealStep by remember(show) { mutableIntStateOf(-1) }
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        delay(120.milliseconds)
        for (step in 0 until REVEAL_GROUP_COUNT) {
            revealStep = step
            delay(REVEAL_STEP_MS.milliseconds)
        }
    }

    // 父级组合期不解引用 form 字段，读取下沉到各卡片内部
    val form = remember(show) {
        AddCourseFormState(course, selectedDay, defaultStartSection, defaultEndSection, defaultWeeks)
    }

    // 自定义上课时间弹窗暂存值（只在时间弹窗内被读取）
    var showTimeDialog by remember(show) { mutableStateOf(false) }
    var timeError by remember(show) { mutableStateOf(false) }
    var tempStartHour by remember(show) { mutableIntStateOf(parseTimeHour(course?.customStartTime)) }
    var tempStartMinute by remember(show) { mutableIntStateOf(parseTimeMinute(course?.customStartTime)) }
    var tempEndHour by remember(show) { mutableIntStateOf(parseTimeHour(course?.customEndTime)) }
    var tempEndMinute by remember(show) { mutableIntStateOf(parseTimeMinute(course?.customEndTime)) }

    // 勾选自定义时间时自动从节次时间预填
    LaunchedEffect(form.isCustomTime) {
        if (form.isCustomTime) {
            val sectionStart = sectionTimes[form.startSection]?.split("-")?.firstOrNull()?.trim()
            val sectionEnd = sectionTimes[form.endSection]?.split("-")?.lastOrNull()?.trim()
            if (sectionStart != null && sectionEnd != null) {
                form.customStartTime = sectionStart
                form.customEndTime = sectionEnd
                tempStartHour = parseTimeHour(sectionStart)
                tempStartMinute = parseTimeMinute(sectionStart)
                tempEndHour = parseTimeHour(sectionEnd)
                tempEndMinute = parseTimeMinute(sectionEnd)
            }
        }
    }

    var currentOccupiedWeeks by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 占用计算与剔除合并进同一协程；内容相等时不写状态，避免空 Set 反复重启协程
    LaunchedEffect(
        form.dayOfWeek,
        form.startSection,
        form.endSection,
        form.isCustomTime,
        form.customStartTime,
        form.customEndTime
    ) {
        val occupied = getOccupiedWeeks(
            form.dayOfWeek,
            form.startSection,
            form.endSection,
            listOfNotNull(course?.id),
            if (form.isCustomTime) form.customStartTime.ifBlank { null } else null,
            if (form.isCustomTime) form.customEndTime.ifBlank { null } else null
        )
        if (occupied != currentOccupiedWeeks) {
            form.selectedWeeks.removeAll(occupied)
            currentOccupiedWeeks = occupied
        }
    }

    val allWeeks = remember(totalWeeks) { (1..totalWeeks).toList() }
    val oddWeeks = remember(allWeeks) { allWeeks.filter { it % 2 == 1 } }
    val evenWeeks = remember(allWeeks) { allWeeks.filter { it % 2 == 0 } }

    val selectableWeeks =
        remember(allWeeks, currentOccupiedWeeks) { allWeeks.filter { it !in currentOccupiedWeeks } }
    val selectableOddWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 1 } }
    val selectableEvenWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 0 } }
    // 注意：不要再在父级作用域读 form.selectedWeeks，否则点周次格子会整弹窗重组
    val hasOccupiedOddWeeks =
        remember(selectableOddWeeks, oddWeeks) { selectableOddWeeks.size != oddWeeks.size }
    val hasOccupiedEvenWeeks =
        remember(selectableEvenWeeks, evenWeeks) { selectableEvenWeeks.size != evenWeeks.size }

    var showSectionDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var tempStartSection by remember(show) { mutableIntStateOf(defaultStartSection) }
    var tempEndSection by remember(show) { mutableIntStateOf(defaultEndSection) }
    // 初值无关紧要：每次打开调色板前都会用当前课程色重新赋值
    var customColor by remember { mutableStateOf(Color.Transparent) }

    val onConfirmClick: () -> Unit = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        if (form.name.isBlank()) {
            android.widget.Toast.makeText(
                context,
                "请输入课程名称",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else if (form.selectedWeeks.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                "请选择上课周次",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else if (form.startSection <= form.endSection) {
            val sortedWeeks = form.selectedWeeks.sorted()
            val minWeek = sortedWeeks.first()
            val maxWeek = sortedWeeks.last()
            val allWeeksInRange = (minWeek..maxWeek).toSet()
            val oddWeeksInRange = allWeeksInRange.filter { it % 2 == 1 }.toSet()
            val evenWeeksInRange = allWeeksInRange.filter { it % 2 == 0 }.toSet()

            val selectedWeekSet = form.selectedWeeks.toSet()
            val weekType = when {
                selectedWeekSet == allWeeksInRange -> Course.WEEK_TYPE_ALL
                selectedWeekSet == oddWeeksInRange -> Course.WEEK_TYPE_ODD
                selectedWeekSet == evenWeeksInRange -> Course.WEEK_TYPE_EVEN
                else -> Course.WEEK_TYPE_ALL
            }

            val isContiguous = sortedWeeks.size == (maxWeek - minWeek + 1)
            val weeksToSave = if (isContiguous) emptyList() else sortedWeeks

            val newCourse = Course(
                id = course?.id ?: UUID.randomUUID().toString(),
                name = form.name.trim(),
                classroom = form.classroom.trim(),
                teacher = form.teacher.trim(),
                dayOfWeek = form.dayOfWeek,
                startSection = form.startSection,
                endSection = form.endSection,
                startWeek = minWeek,
                endWeek = maxWeek,
                weekType = weekType,
                colorRes = form.selectedColor,
                selectedWeeks = weeksToSave,
                isCustomTime = form.isCustomTime,
                customStartTime = if (form.isCustomTime) form.customStartTime else null,
                customEndTime = if (form.isCustomTime) form.customEndTime else null
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
            fillMaxHeight = true,
            onDismissRequest = onDismiss,
            liquidGlassBackdrop = null,
            onSheetContentBackdropCreated = { sheetContentBackdropHolder.value = it },
            startAction = {
                val material = LocalSheetTopBarMaterial.current
                LiquidTopBarButton(
                    onClick = {
                        onDismiss()
                    },
                    backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
                    icon = MiuixIcons.Normal.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.padding(start = 16.dp),
                    iconSize = 24.dp,
                    backdropAlpha = material.backdropAlpha,
                    shadowAlpha = material.shadowAlpha,
                )
            },
            endAction = {
                val material = LocalSheetTopBarMaterial.current
                LiquidTopBarButton(
                    onClick = onConfirmClick,
                    backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
                    icon = MiuixIcons.Ok,
                    contentDescription = "确定",
                    modifier = Modifier.padding(end = 16.dp),
                    iconSize = 25.dp,
                    backdropAlpha = material.backdropAlpha,
                    shadowAlpha = material.shadowAlpha,
                )
            },
        ) {
            AddCourseDialogContent(
                isEdit = isEdit,
                isDark = isDark,
                revealStep = revealStep,
                form = form,
                totalWeeks = totalWeeks,
                currentOccupiedWeeks = currentOccupiedWeeks,
                selectableWeeks = selectableWeeks,
                selectableOddWeeks = selectableOddWeeks,
                selectableEvenWeeks = selectableEvenWeeks,
                hasOccupiedOddWeeks = hasOccupiedOddWeeks,
                hasOccupiedEvenWeeks = hasOccupiedEvenWeeks,
                onShowSectionDialog = {
                    tempStartSection = form.startSection
                    tempEndSection = form.endSection
                    showSectionDialog = true
                },
                onShowColorDialog = {
                    customColor = Color(form.selectedColor)
                    showColorDialog = true
                },
                onShowTimeDialog = {
                    tempStartHour = parseTimeHour(form.customStartTime)
                    tempStartMinute = parseTimeMinute(form.customStartTime)
                    tempEndHour = parseTimeHour(form.customEndTime)
                    tempEndMinute = parseTimeMinute(form.customEndTime)
                    timeError = false
                    showTimeDialog = true
                },
                onDeleteClick = { showDeleteDialog = true },
            )
        }
    } else {
        BlurBottomSheet(
            show = show,
            title = if (isEdit) "编辑课程" else "添加课程",
            liquidGlassBackdrop = null,
            dimBackground = true,
            fillMaxHeight = true,
            sheetOffsetDp = statusBarsPadding + 5.dp,
            onDismissRequest = onDismiss,
            onSheetContentBackdropCreated = { sheetContentBackdropHolder.value = it },
            startAction = {
                val material = LocalSheetTopBarMaterial.current
                LiquidTopBarButton(
                    onClick = {
                        onDismiss()
                    },
                    backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
                    icon = MiuixIcons.Normal.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.padding(start = 18.dp),
                    iconSize = 24.dp,
                    backdropAlpha = material.backdropAlpha,
                    shadowAlpha = material.shadowAlpha,
                )
            },
            endAction = {
                val material = LocalSheetTopBarMaterial.current
                LiquidTopBarButton(
                    onClick = onConfirmClick,
                    backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
                    icon = MiuixIcons.Ok,
                    contentDescription = "确定",
                    modifier = Modifier.padding(end = 18.dp),
                    iconSize = 25.dp,
                    backdropAlpha = material.backdropAlpha,
                    shadowAlpha = material.shadowAlpha,
                )
            },
        ) {
            AddCourseDialogContent(
                isEdit = isEdit,
                isDark = isDark,
                revealStep = revealStep,
                form = form,
                totalWeeks = totalWeeks,
                currentOccupiedWeeks = currentOccupiedWeeks,
                selectableWeeks = selectableWeeks,
                selectableOddWeeks = selectableOddWeeks,
                selectableEvenWeeks = selectableEvenWeeks,
                hasOccupiedOddWeeks = hasOccupiedOddWeeks,
                hasOccupiedEvenWeeks = hasOccupiedEvenWeeks,
                onShowSectionDialog = {
                    tempStartSection = form.startSection
                    tempEndSection = form.endSection
                    showSectionDialog = true
                },
                onShowColorDialog = {
                    customColor = Color(form.selectedColor)
                    showColorDialog = true
                },
                onShowTimeDialog = {
                    tempStartHour = parseTimeHour(form.customStartTime)
                    tempStartMinute = parseTimeMinute(form.customStartTime)
                    tempEndHour = parseTimeHour(form.customEndTime)
                    tempEndMinute = parseTimeMinute(form.customEndTime)
                    timeError = false
                    showTimeDialog = true
                },
                onDeleteClick = { showDeleteDialog = true },
            )
        }
    }

    OverlayDialog(
        title = "删除课程",
        summary = "确定要删除课程「${course?.name}」吗？\n此操作不可撤销。",
        show = showDeleteDialog,
        onDismissRequest = { showDeleteDialog = false },
        liquidGlassBackdrop = sheetContentBackdropHolder.value ?: liquidGlassBackdrop
    ) {
        MiuixTheme(controller = appDialogController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides null) {
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
                            showDeleteDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "删除",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            course?.id?.let { onDelete(it) }
                            showDeleteDialog = false
                            onDismiss()
                        },
                        textColor = Color(0xFFF44336),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    OverlayDialog(
        title = "选择上课节次",
        show = showSectionDialog,
        onDismissRequest = { showSectionDialog = false },
        liquidGlassBackdrop = sheetContentBackdropHolder.value ?: liquidGlassBackdrop,
    ) {
        MiuixTheme(controller = appDialogController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                            // 结束 >= 开始 的夹取在同一快照里同步完成，省掉一帧延迟
                            NumberPicker(
                                value = tempStartSection,
                                onValueChange = {
                                    tempStartSection = it
                                    if (tempEndSection < it) tempEndSection = it
                                },
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
                            // 范围固定，拖动"开始"时不再反复重建本滚轮
                            NumberPicker(
                                value = tempEndSection,
                                onValueChange = { tempEndSection = it },
                                range = 1..totalSections,
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
                                    form.startSection = tempStartSection
                                    form.endSection = tempEndSection
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
    }

    OverlayDialog(
        title = "选择上课时间",
        show = showTimeDialog,
        onDismissRequest = { showTimeDialog = false },
        liquidGlassBackdrop = sheetContentBackdropHolder.value ?: liquidGlassBackdrop,
    ) {
        MiuixTheme(controller = appDialogController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides null) {
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
                            text = "结束时间需晚于开始时间",
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
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showTimeDialog = false
                                timeError = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val startMinutes = tempStartHour * 60 + tempStartMinute
                                val endMinutes = tempEndHour * 60 + tempEndMinute
                                if (endMinutes > startMinutes) {
                                    form.customStartTime =
                                        formatTime(tempStartHour, tempStartMinute)
                                    form.customEndTime = formatTime(tempEndHour, tempEndMinute)
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
    }

    OverlayDialog(
        title = "选择颜色",
        show = showColorDialog,
        onDismissRequest = { showColorDialog = false },
        liquidGlassBackdrop = liquidGlassBackdrop
    ) {
        MiuixTheme(controller = appDialogController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides null) {
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
                                form.selectedColor =
                                    (customColor.alpha * 255).toInt().toLong() shl 24 or
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
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun AddCourseDialogContent(
    isEdit: Boolean,
    isDark: Boolean,
    revealStep: Int,
    form: AddCourseFormState,
    totalWeeks: Int,
    currentOccupiedWeeks: Set<Int>,
    selectableWeeks: List<Int>,
    selectableOddWeeks: List<Int>,
    selectableEvenWeeks: List<Int>,
    hasOccupiedOddWeeks: Boolean,
    hasOccupiedEvenWeeks: Boolean,
    onShowSectionDialog: () -> Unit,
    onShowTimeDialog: () -> Unit,
    onShowColorDialog: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    // rememberUpdatedState 保证稳定引用 + 调用时拿到最新 lambda（remember{} 会冻结旧值）
    val stableOnShowSectionDialog by rememberUpdatedState(onShowSectionDialog)
    val stableOnShowTimeDialog by rememberUpdatedState(onShowTimeDialog)
    val stableOnShowColorDialog by rememberUpdatedState(onShowColorDialog)
    val stableOnDeleteClick by rememberUpdatedState(onDeleteClick)

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
        Spacer(modifier = Modifier.height(if (isTablet) 56.dp else 58.dp))

        CardReveal(visible = revealStep >= 0, index = 0) {
            BasicInfoCard(
                isDark = isDark,
                form = form,
            )
        }

        CardReveal(visible = revealStep >= 1, index = 1) {
            WeekdayCard(
                isDark = isDark,
                form = form,
            )
        }

        CardReveal(visible = revealStep >= 2, index = 2) {
            SectionTimeCard(
                isDark = isDark,
                form = form,
                onShowTimeDialog = stableOnShowTimeDialog,
                onShowSectionDialog = stableOnShowSectionDialog,
            )
        }

        CardReveal(visible = revealStep >= 3, index = 3) {
            WeekSettingCard(
                isDark = isDark,
                form = form,
                totalWeeks = totalWeeks,
                currentOccupiedWeeks = currentOccupiedWeeks,
                selectableWeeks = selectableWeeks,
                selectableOddWeeks = selectableOddWeeks,
                selectableEvenWeeks = selectableEvenWeeks,
                hasOccupiedOddWeeks = hasOccupiedOddWeeks,
                hasOccupiedEvenWeeks = hasOccupiedEvenWeeks,
            )
        }

        CardReveal(visible = revealStep >= 4, index = 4) {
            ColorCard(
                isDark = isDark,
                form = form,
                onShowColorDialog = stableOnShowColorDialog,
            )
        }

        CardReveal(visible = revealStep >= 5, index = 5) {
            if (isEdit) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        stableOnDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "删除",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF44336)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(if (isTablet) 4.dp else statusBarsPadding + 65.dp))
    }
}

/** 入场 reveal：始终占位参与布局，仅 graphicsLayer 做透明/位移/缩放；结束即撤层 */
@Composable
private fun CardReveal(
    visible: Boolean,
    index: Int,
    content: @Composable () -> Unit,
) {
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label = "cardReveal$index",
    )
    val revealDensity = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (appear < 1f) Modifier.graphicsLayer {
                    alpha = appear
                    translationY = (1f - appear) * revealDensity.run { 8.dp.toPx() }
                    scaleX = 0.97f + 0.03f * appear
                    scaleY = 0.97f + 0.03f * appear
                } else Modifier
            )
    ) {
        content()
    }
}

/** 基本信息卡片：课程名称 / 地点 / 教师 文本框 */
@Composable
private fun BasicInfoCard(
    isDark: Boolean,
    form: AddCourseFormState,
) {
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                    value = form.name,
                    onValueChange = { form.name = it },
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
                    value = form.classroom,
                    onValueChange = { form.classroom = it },
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
                    value = form.teacher,
                    onValueChange = { form.teacher = it },
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
}

/** 上课星期卡片：自定义时间勾选 + 星期按钮行 */
@Composable
private fun WeekdayCard(
    isDark: Boolean,
    form: AddCourseFormState,
) {
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 17.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 在 Row lambda 内读取，切换自定义时间只重组这一行
                val isCustomTime = form.isCustomTime
                Text(
                    text = "上课星期",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    state = if (isCustomTime) ToggleableState.On else ToggleableState.Off,
                    onClick = { form.isCustomTime = !isCustomTime }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "自定义时间",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val dayLabels = remember { listOf("一", "二", "三", "四", "五", "六", "日") }
                val currentDay = form.dayOfWeek
                for (day in 1..7) {
                    val isSelected = day == currentDay
                    val bgColor = if (isSelected) MiuixTheme.colorScheme.primary
                    else if (isDark) Color(0xFF363636) else Color(0xFFF2F2F2)
                    val textColor = if (isSelected) Color.White
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .squircleClip(10.dp)
                            .background(bgColor)
                            .clickable(
                                interactionSource = null,
                                indication = null,
                            ) {
                                form.selectDay(day)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayLabels[day - 1],
                            fontSize = 14.sp,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/** 节次范围 / 上课时间卡片（勾选自定义时间后切换为时间选择） */
@Composable
private fun SectionTimeCard(
    isDark: Boolean,
    form: AddCourseFormState,
    onShowTimeDialog: () -> Unit,
    onShowSectionDialog: () -> Unit,
) {
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        // isCustomTime 只在 content lambda 内读：切换时不必重组 SectionTimeCard 本体
        if (form.isCustomTime) {
            ArrowPreference(
                title = "上课时间",
                endActions = {
                    Text(
                        text = "${form.customStartTime} - ${form.customEndTime}",
                        fontSize = 14.5.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                },
                onClick = onShowTimeDialog,
            )
        } else {
            ArrowPreference(
                title = "上课节次",
                endActions = {
                    Text(
                        text = "第${form.startSection} - ${form.endSection}节",
                        fontSize = 14.5.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                },
                onClick = onShowSectionDialog,
            )
        }
    }
}

/** 周次设置卡片：全部/单周/双周勾选 + 周次网格 */
@Composable
private fun WeekSettingCard(
    isDark: Boolean,
    form: AddCourseFormState,
    totalWeeks: Int,
    currentOccupiedWeeks: Set<Int>,
    selectableWeeks: List<Int>,
    selectableOddWeeks: List<Int>,
    selectableEvenWeeks: List<Int>,
    hasOccupiedOddWeeks: Boolean,
    hasOccupiedEvenWeeks: Boolean,
) {
    val noDaySelected = form.dayOfWeek == 0
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (noDaySelected) 0.5f else 1f),
        colors = CardDefaults.defaultColors(
            color = if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // derivedStateOf：结果不变时不通知读取方，点周次格子多数情况不触发整行复选框重组
            val allSelectableSelected by remember(selectableWeeks) {
                derivedStateOf { selectableWeeks.isNotEmpty() && selectableWeeks.all { it in form.selectedWeeks } }
            }
            val allSelectableOddSelected by remember(selectableOddWeeks) {
                derivedStateOf { selectableOddWeeks.all { it in form.selectedWeeks } }
            }
            val allSelectableEvenSelected by remember(selectableEvenWeeks) {
                derivedStateOf { selectableEvenWeeks.all { it in form.selectedWeeks } }
            }
            val someSelectableOddSelected by remember(selectableOddWeeks) {
                derivedStateOf { selectableOddWeeks.any { it in form.selectedWeeks } }
            }
            val someSelectableEvenSelected by remember(selectableEvenWeeks) {
                derivedStateOf { selectableEvenWeeks.any { it in form.selectedWeeks } }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 必须在 Row lambda 内算：放外层会把派生态读取抬到整卡作用域
                val hasMixedSelection = someSelectableOddSelected && someSelectableEvenSelected
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
                    ) {
                        Checkbox(
                            state = if (allSelectableSelected) ToggleableState.On else ToggleableState.Off,
                            onClick = if (noDaySelected) null else {
                                {
                                    if (allSelectableSelected) {
                                        form.selectedWeeks.clear()
                                    } else {
                                        form.selectedWeeks.clear()
                                        form.selectedWeeks.addAll(selectableWeeks)
                                    }
                                }
                            },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "全部",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

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
                                    // 必须在 clear 之前判断：先 clear 会让 allSelectableOddSelected 变 false
                                    if (hasMixedSelection || !allSelectableOddSelected) {
                                        form.selectedWeeks.clear()
                                        form.selectedWeeks.addAll(selectableOddWeeks)
                                    } else {
                                        form.selectedWeeks.clear()
                                    }
                                }
                            },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "单周",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

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
                                    if (hasMixedSelection || !allSelectableEvenSelected) {
                                        form.selectedWeeks.clear()
                                        form.selectedWeeks.addAll(selectableEvenWeeks)
                                    } else {
                                        form.selectedWeeks.clear()
                                    }
                                }
                            },

                            )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "双周",
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
                selectedWeeks = form.selectedWeeks.toSet(),
                occupiedWeeks = currentOccupiedWeeks,
                enabled = !noDaySelected,
                isDark = isDark,
                onToggleWeek = { week ->
                    if (week in form.selectedWeeks) form.selectedWeeks.remove(week)
                    else form.selectedWeeks.add(week)
                },
                onReplaceWeeks = { weeks ->
                    form.selectedWeeks.clear()
                    form.selectedWeeks.addAll(weeks)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 课程颜色选择卡片 */
@Composable
private fun ColorCard(
    isDark: Boolean,
    form: AddCourseFormState,
    onShowColorDialog: () -> Unit,
) {
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val colorColumns = 6
                val allColors = remember { Course.courseColors }
                val totalItems = remember(allColors) { allColors.size + 1 }
                val colorRows = remember(
                    totalItems,
                    colorColumns
                ) { (totalItems + colorColumns - 1) / colorColumns }
                for (row in 0 until colorRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (col in 0 until colorColumns) {
                            val colorIndex = row * colorColumns + col
                            if (colorIndex < allColors.size) {
                                ColorSwatch(
                                    color = allColors[colorIndex],
                                    form = form,
                                    isDark = isDark,
                                )
                            } else if (colorIndex == allColors.size) {
                                CustomColorSwatch(
                                    form = form,
                                    isDark = isDark,
                                    onShowColorDialog = onShowColorDialog,
                                )
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

/** 预设颜色格子：内部读写 form.selectedColor，切换颜色只重组本格 */
@Composable
private fun RowScope.ColorSwatch(
    color: Long,
    form: AddCourseFormState,
    isDark: Boolean,
) {
    val isSelected = color == form.selectedColor
    val primaryColor = MiuixTheme.colorScheme.primary
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "borderAlpha"
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .pointerInput(color) {
                detectTapGestures { form.selectedColor = color }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .squircleBorder(
                    width = 2.dp,
                    color = primaryColor.copy(alpha = borderAlpha),
                    cornerRadius = 12.dp
                )
                .padding(4.dp)
                .squircleClip(8.dp)
                .background(Color(color).copy(alpha = if (isDark) 0.22f else 0.16f))
        )
    }
}

/** 自定义颜色格子（最后一个 "+"）：点击打开调色板 */
@Composable
private fun RowScope.CustomColorSwatch(
    form: AddCourseFormState,
    isDark: Boolean,
    onShowColorDialog: () -> Unit,
) {
    val allColors = remember { Course.courseColors }
    val isCustomColor = form.selectedColor !in allColors
    val hintColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val primaryColor = MiuixTheme.colorScheme.primary
    val customBorderAlpha by animateFloatAsState(
        targetValue = if (isCustomColor) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "customBorderAlpha"
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { onShowColorDialog() }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .squircleBorder(
                    width = 2.dp,
                    color = primaryColor.copy(alpha = customBorderAlpha),
                    cornerRadius = 12.dp
                )
                .padding(4.dp)
                .squircleClip(8.dp)
                .background(
                    if (isCustomColor) Color(form.selectedColor).copy(alpha = if (isDark) 0.22f else 0.16f)
                    else if (isDark) Color(0xFF424242) else Color(0xFFF0F0F0)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!isCustomColor) {
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

/** 时间段 时:分 双滚轮选择器 */
@SuppressLint("DefaultLocale")
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
            label = { String.format("%02d", it) },
            wrapAround = true,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ":",
            style = MiuixTheme.textStyles.paragraph,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding()
                .offset(y = (-2).dp)
        )
        val sMinIdx = minuteValues.indexOf(startMinute).coerceAtLeast(0)
        NumberPicker(
            value = sMinIdx,
            onValueChange = { onStartMinuteChange(minuteValues[it]) },
            range = minuteValues.indices,
            visibleItemCount = 3,
            itemHeight = 60.dp,
            label = { String.format("%02d", minuteValues[it]) },
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
            label = { String.format("%02d", it) },
            wrapAround = true,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ":",
            style = MiuixTheme.textStyles.paragraph,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding()
                .offset(y = (-2).dp)
        )
        val eMinIdx = minuteValues.indexOf(endMinute).coerceAtLeast(0)
        NumberPicker(
            value = eMinIdx,
            onValueChange = { onEndMinuteChange(minuteValues[it]) },
            range = minuteValues.indices,
            visibleItemCount = 3,
            itemHeight = 60.dp,
            label = { String.format("%02d", minuteValues[it]) },
            wrapAround = true,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
    }
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

@SuppressLint("DefaultLocale")
private fun formatTime(hour: Int, minute: Int): String {
    return String.format("%02d:%02d", hour, minute)
}

/** 自定义时间弹窗可用分钟值（每 5 分钟一档） */
private val minuteValues = (0..59 step 5).toList()
