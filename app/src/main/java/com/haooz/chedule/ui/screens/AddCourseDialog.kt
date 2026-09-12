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

/**
 * 内容区分组数（基本信息 / 星期 / 节次 / 周次网格 / 颜色 / 删除），用于逐组错落揭示。
 */
private const val REVEAL_GROUP_COUNT = 6

/** 每组之间的揭示间隔（毫秒），总时长 ≈ 该值 × (REVEAL_GROUP_COUNT-1)。 */
private const val REVEAL_STEP_MS = 60L

/**
 * 添加/编辑课程表单的状态持有者。
 *
 * 关键约定：**每个字段只允许在真正消费它的那张卡片内部被读取**，父级 `AddCourseDialog`
 * 在组合期一律不解引用（只在 `onConfirmClick` 这类事件回调里读——事件里读取不会建立重组依赖）。
 *
 * 已在 app/compose-stability.conf 中声明为 stable，否则内部 `var` 字段会让整个类
 * 被判定为 unstable，子卡片就无法靠参数比对跳过重组。
 */
private class AddCourseFormState(
    course: Course?,
    selectedDay: Int,
    defaultStartSection: Int,
    defaultEndSection: Int,
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
        }
    }

    /** 切换星期：周次占用情况随星期变化，已选周次一并清空。 */
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
    // 嵌套弹窗（节次/自定义时间等）渲染在 root popup host，继承宿主的壁纸主题；
    // 此处强制跟随应用主题。
    val appDialogDark = rememberAppSettingDark()
    val appDialogController = remember(appDialogDark) {
        ThemeController(if (appDialogDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
    }
    // 二级弹窗（删除确认/节次/时间选择）在弹窗作用域之外，读不到 LocalSheetContentBackdrop，
    // 用非快照 holder 接收 —— 写入零重组，不会让宿主页面在弹窗进入动画期间重跑组合。
    val sheetContentBackdropHolder = remember { BackdropHolder() }

    // 逐组揭示：revealStep 从 -1 递增，各卡片 target 逐组变为可见。
    // 卡片始终占位参与布局（不 AnimatedVisibility 移除节点），仅通过 graphicsLayer 做透明/位移/缩放，
    // 因此弹窗高度首帧定型、全程稳定不闪。整体先延迟 120ms 再开始。
    var revealStep by remember(show) { mutableIntStateOf(-1) }
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        // 整体延迟一档（120ms）再开始
        delay(120.milliseconds)
        for (step in 0 until REVEAL_GROUP_COUNT) {
            revealStep = step
            delay(REVEAL_STEP_MS.milliseconds)
        }
    }

    // 表单状态集中在持有者里：父级组合期不解引用其字段，读取全部下沉到各卡片内部，
    // 敲字 / 点周次 / 选颜色只重组对应的那一张卡，不再波及整个弹窗。
    val form = remember(show) {
        AddCourseFormState(course, selectedDay, defaultStartSection, defaultEndSection)
    }

    // 自定义上课时间的弹窗暂存值（只在时间弹窗内被读取，放在父级没有重组代价）
    var showTimeDialog by remember(show) { mutableStateOf(false) }
    var timeError by remember(show) { mutableStateOf(false) }
    var tempStartHour by remember(show) { mutableIntStateOf(parseTimeHour(course?.customStartTime)) }
    var tempStartMinute by remember(show) { mutableIntStateOf(parseTimeMinute(course?.customStartTime)) }
    var tempEndHour by remember(show) { mutableIntStateOf(parseTimeHour(course?.customEndTime)) }
    var tempEndMinute by remember(show) { mutableIntStateOf(parseTimeMinute(course?.customEndTime)) }

    // 勾选自定义时间时，自动从节次时间预填
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
    // 占用周次的计算与"剔除已占周次"合并进同一个协程：原先拆成两个 LaunchedEffect，
    // 星期/节次变化要多等一帧才画出来。内容相等时不写状态，避免空 Set 反复重启协程。
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
    // 注意：下面这 5 个布尔不能再在这里算 —— 它们要读 form.selectedWeeks，
    // 一旦在父级作用域读取，点一个周次格子就会让整个弹窗重组。已下沉到 WeekSettingCard。
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

    // 事件回调里读取 form 字段不会建立重组依赖，所以这里可以放心读
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
                // 这些回调体内解引用 form 的当前字段（调用时才读），
                // 因此不存在"捕获旧值"的问题，改完节次/颜色再打开也是最新的
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
                // 这些回调体内解引用 form 的当前字段（调用时才读），
                // 因此不存在"捕获旧值"的问题，改完节次/颜色再打开也是最新的
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
    } // end of if (isTablet) else

    // 删除确认弹窗（强制跟随应用主题）
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

    // 节次选择弹窗（强制跟随应用主题）
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
                            // 把"结束 >= 开始"的夹取放在同一个快照里同步完成，
                            // 既省掉 LaunchedEffect 带来的一帧延迟，也避免结束滚轮的 range 一直变
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
                            // 范围固定为 1..totalSections：拖动"开始"时不再反复重建本滚轮
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

    // 自定义上课时间选择弹窗（时:分 双滚轮，强制跟随应用主题）
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

    // 自定义颜色选择弹窗（强制跟随应用主题，与节次/时间/删除弹窗一致）
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

    // 用 rememberUpdatedState 而不是 remember{}：后者会冻结首次组合时创建的 lambda，
    // 而"打开子弹窗"这类回调里读的是当时的字段值，冻结后二次打开会回填旧值。
    // rememberUpdatedState 既给到稳定引用（revealStep 递进时各卡片仍可跳过重组），
    // 又保证调用时拿到的是最新的那个 lambda。
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

        // 基本信息卡片
        CardReveal(visible = revealStep >= 0, index = 0) {
            BasicInfoCard(
                isDark = isDark,
                form = form,
            )
        }

        // 上课星期卡片
        CardReveal(visible = revealStep >= 1, index = 1) {
            WeekdayCard(
                isDark = isDark,
                form = form,
            )
        }

        // 节次范围 / 上课时间（勾选自定义时间后切换为时间选择）
        CardReveal(visible = revealStep >= 2, index = 2) {
            SectionTimeCard(
                isDark = isDark,
                form = form,
                onShowTimeDialog = stableOnShowTimeDialog,
                onShowSectionDialog = stableOnShowSectionDialog,
            )
        }

        // 周次设置
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

        // 课程颜色选择
        CardReveal(visible = revealStep >= 4, index = 4) {
            ColorCard(
                isDark = isDark,
                form = form,
                onShowColorDialog = stableOnShowColorDialog,
            )
        }

        // 删除按钮（仅编辑模式）
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

/**
 * 弹窗内容卡的入场 reveal：卡片始终占位参与布局，仅通过 graphicsLayer 做透明/位移/缩放，
 * 保证弹窗外高首帧定型、全程稳定不闪（替代原 AnimatedVisibility 的移除式展开）。
 * 该卡动画结束（appear==1）即撤层，避免长期保留离屏层。
 */
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

/** 基本信息卡片：课程名称 / 地点 / 教师 文本框。独立组件使敲键仅重组本卡。 */
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

/** 上课星期卡片：自定义时间勾选 + 星期按钮行。 */
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
                // 在这里（Row 的 lambda 作用域内）读取，切换自定义时间只重组这一行
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
                // 同样把 dayOfWeek 的读取点留在这个 Row 作用域内
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

/** 节次范围 / 上课时间卡片（勾选自定义时间后切换为时间选择）。 */
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
        // isCustomTime 只在 Card 的 content lambda 里读：切换时不必重组 SectionTimeCard 本体
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

/** 周次设置卡片：全部/单周/双周勾选 + 周次网格。 */
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
            // 勾选态派生值：derivedStateOf 只在"结果真的变了"时才通知读取方。
            // 点单个周次格子时这 5 个布尔绝大多数情况不变，于是下面整行复选框
            // 连带着本 Column 都不会重组，只有真正翻变的那一两个格子会重画。
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
                // hasMixedSelection 必须在这个 Row 的 lambda 里算：放在外层 Column 里
                // 会把上面几个派生态的读取点抬到整卡作用域，点格子就整卡重组了
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
                    // 全部
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            state = if (allSelectableSelected) ToggleableState.On else ToggleableState.Off,
                            onClick = if (noDaySelected) null else {
                                {
                                    form.selectedWeeks.clear()
                                    if (!allSelectableSelected) {
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
                                    form.selectedWeeks.clear()
                                    if (!allSelectableOddSelected) {
                                        form.selectedWeeks.addAll(selectableOddWeeks)
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

            // 周次网格
            val columns = 6
            val rows = remember(totalWeeks, columns) { (totalWeeks + columns - 1) / columns }
            val outlineColor = MiuixTheme.colorScheme.outline
            val onSurfaceSummaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
            val occupiedColor = if (isDark) Color(0xFF4A4A4A) else Color(0xFFF0F0F0)

            // 选中态/非选中态在 WeekCell 内部读取 form.selectedWeeks，
            // 点单个格子只重组那一个 Box，不再波及整个网格
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
                            if (idx < totalWeeks) {
                                WeekCell(
                                    weekNum = idx + 1,
                                    isOccupied = (idx + 1) in currentOccupiedWeeks,
                                    noDaySelected = noDaySelected,
                                    isDark = isDark,
                                    outlineColor = outlineColor,
                                    onSurfaceSummaryColor = onSurfaceSummaryColor,
                                    occupiedColor = occupiedColor,
                                    form = form,
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

/**
 * 单个周次格子。
 *
 * isSelected 在 cell 内部读取 form.selectedWeeks；点击直接写 form.selectedWeeks。
 * 这样某个格子被点选只触发这一个 WeekCell 的重组，外层网格整列不再重画。
 *
 * noDaySelected / isOccupied 通过参数下传——它们一周次内至多变化一次，
 * 在父级读一次后下传最划算（form.selectedWeeks 之外的读取仍然走父级）。
 */
@Composable
private fun RowScope.WeekCell(
    weekNum: Int,
    isOccupied: Boolean,
    noDaySelected: Boolean,
    isDark: Boolean,
    outlineColor: Color,
    onSurfaceSummaryColor: Color,
    occupiedColor: Color,
    form: AddCourseFormState,
) {
    val isSelected = weekNum in form.selectedWeeks
    val primaryColor = MiuixTheme.colorScheme.primary
    val bgColor = when {
        isSelected -> primaryColor
        isOccupied -> occupiedColor
        else -> if (isDark) Color(0xFF363636) else Color(0xFFF2F2F2)
    }
    val contentTextColor = when {
        noDaySelected -> outlineColor
        isSelected -> Color.White
        isOccupied -> outlineColor
        else -> onSurfaceSummaryColor
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(32.dp)
            .squircleClip(10.dp)
            .background(bgColor)
            .then(
                if (noDaySelected || isOccupied) Modifier
                else Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                ) {
                    if (isSelected) form.selectedWeeks.remove(weekNum)
                    else form.selectedWeeks.add(weekNum)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$weekNum",
            fontSize = 13.sp,
            color = contentTextColor
        )
    }
}

/** 课程颜色选择卡片。 */
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

/**
 * 预设颜色格子：内部读 form.selectedColor 判断选中，点击直接写 form.selectedColor。
 * 这样切换颜色只重组这一个 ColorSwatch，外层网格整行不再重画。
 */
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

/**
 * 自定义颜色格子（最后一个 "+"）：点击打开调色板，选中态展示当前 custom 颜色。
 */
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

/**
 * 时间段 时:分 双滚轮选择器（与时间配置编辑页一致的左右布局）
 */
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

/**
 * 解析 "HH:mm" 格式字符串中的小时，无效时返回 8
 */
private fun parseTimeHour(time: String?): Int {
    if (time.isNullOrBlank()) return 8
    val parts = time.split(":")
    return parts.firstOrNull()?.toIntOrNull()?.coerceIn(0, 23) ?: 8
}

/**
 * 解析 "HH:mm" 格式字符串中的分钟，无效时返回 0
 */
private fun parseTimeMinute(time: String?): Int {
    if (time.isNullOrBlank()) return 0
    val parts = time.split(":")
    return parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
}

/**
 * 将时/分格式化为 "HH:mm"
 */
@SuppressLint("DefaultLocale")
private fun formatTime(hour: Int, minute: Int): String {
    return String.format("%02d:%02d", hour, minute)
}

/**
 * 自定义时间选择弹窗中可用的分钟值（每 5 分钟一档）
 */
private val minuteValues = (0..59 step 5).toList()
