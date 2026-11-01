/** 时间配置编辑页面 - Screen */
package com.haooz.chedule.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.SpecialBlock
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.model.TimeRoutine
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.CourseEditStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.i18n.TimeCfgStr
import com.haooz.chedule.i18n.TimeEditStr
import com.haooz.chedule.i18n.orDefaultName
import com.haooz.chedule.ui.activities.classschedule.SpecialItemsEditorSection
import com.haooz.chedule.ui.activities.classschedule.describeSpecialDays
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.motion.rememberMorphTransition
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentEditorChrome
import com.haooz.chedule.ui.utils.NativeDocumentEditorEffect
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.TransitionLayerImage
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.datetime.Clock
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.kyant.capsule.continuousSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private fun parseTimeRange(timeStr: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    return try {
        val parts = timeStr.split("-")
        val startParts = parts[0].split(":")
        val endParts = parts[1].split(":")
        Pair(
            Pair(startParts[0].toInt(), startParts[1].toInt()),
            Pair(endParts[0].toInt(), endParts[1].toInt())
        )
    } catch (_: Exception) {
        Pair(Pair(8, 0), Pair(8, 45))
    }
}

/** 解析 "HH:mm" 为 (小时, 分钟)，解析失败返回 (8, 0) */
private fun parseTimeHm(time: String): Pair<Int, Int> {
    return try {
        val parts = time.split(":")
        Pair(parts[0].toInt(), parts[1].toInt())
    } catch (_: Exception) {
        Pair(8, 0)
    }
}

/** 格式化 (小时, 分钟) 为 "HH:mm"（跨平台替代 String.format("%02d:%02d")，见 checkKmpPurity） */
private fun formatHhMm(hour: Int, minute: Int): String =
    hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

/**
 * 保存前的时间冲突检查结果。[blocking] 节次互相重叠 → 不允许保存（沿用旧口径）；
 * [warnings] 有一边是特殊课程 → 只提示、允许「仍然保存」：块能正确排开，老数据也可能本就重叠。
 */
private data class OverlapCheck(
    val blocking: String? = null,
    val warnings: List<String> = emptyList()
) {
    val hasBlocking: Boolean get() = blocking != null
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}

private fun formatTimeRangeForDisplay(timeStr: String): String = timeStr.replace('-', '–')

/** 特殊时段块列表项的摘要文本：时段 + 已排的星期 */
private fun specialBlockSummary(block: SpecialBlock): String {
    // 纯展示文案，时间区间也用 en dash，与项目其它区间（如「第1–3节」）一致。
    // ⚠️ 别改 block.startTime/endTime 本身：那是 "HH:mm" 单值，拼接与解析都另有约定。
    val base = "${block.startTime}–${block.endTime}"
    val items = block.safeItems
    if (items.isEmpty()) return base
    val days = items.flatMap { it.startDay..it.endDay }.distinct().sorted()
    val dayLabel = if (days.size >= 7) TimeEditStr.everyDay() else describeSpecialDays(days.toSet())
    return "$base · $dayLabel"
}

/** 解析 "HH:mm" → 分钟数；解析失败返回 null（不猜默认值，猜错会静默改时间） */
private fun parseHhMmToMinutes(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

// 解析失败返回 Int.MAX_VALUE，使无法解析的条目排序时落在末尾
private fun parseTimeToMinutesForSort(time: String): Int {
    return try {
        val parts = time.split(":")
        if (parts.size == 2) parts[0].toInt() * 60 + parts[1].toInt() else Int.MAX_VALUE
    } catch (_: Exception) {
        Int.MAX_VALUE
    }
}

// 按开始时间（其次结束时间、再次名称）排序，避免插入顺序导致"午休排在午餐之前"等错乱
private fun sortSpecialBlocksByTime(blocks: List<SpecialBlock>): List<SpecialBlock> {
    return blocks.sortedWith(
        compareBy<SpecialBlock> { parseTimeToMinutesForSort(it.startTime) }
            .thenBy { parseTimeToMinutesForSort(it.endTime) }
            .thenBy { it.name }
    )
}

@Composable
fun TimeConfigEditScreen(
    timeConfig: TimeConfig,
    routineId: Long,
    onBackStart: () -> Unit = {},
    onBack: () -> Unit,
    onSave: (TimeConfig) -> Unit,
    cardLeft: Float = 0f,
    cardTop: Float = 0f,
    cardWidth: Float = 0f,
    cardHeight: Float = 0f,
    screenWidth: Float = 0f,
    screenHeight: Float = 0f,
    screenCornerRadius: ScreenCornerRadiiPx = ScreenCornerRadiiPx.Zero,
    cardStartCornerRadius: Float = 0f,
    /** Retained source row drawing, owned by the underlying time-settings page. */
    cardSnapshot: TransitionLayerSnapshot? = null,
    isFabCreation: Boolean = false,
    /** 删除当前作息并回到一级列表（宿主负责落库与刷新列表） */
    onDeleteRoutine: (Long) -> Unit = {},
    liquidGlassBackdrop: LayerBackdrop? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollBehavior = rememberSharedScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    val screenTitle = TimeEditStr.editRoutine()
    val usesNativeControls = LocalNativeDocumentChrome.current != null
    var routineName by remember(timeConfig, AppLanguage.resolved) {
        mutableStateOf(
            (timeConfig.safeRoutines.firstOrNull { it.id == routineId }?.name ?: "")
                .orDefaultName(TimeCfgStr.defaultRoutineName, TimeCfgStr.seededRoutineName, ImportStr.defaultRoutineName)
        )
    }

    var morningSections by remember(timeConfig) { mutableIntStateOf(timeConfig.morningSections) }
    var afternoonSections by remember(timeConfig) { mutableIntStateOf(timeConfig.afternoonSections) }
    var eveningSections by remember(timeConfig) { mutableIntStateOf(timeConfig.eveningSections) }

    // 开关只控制参数卡片展开，不落数据层。数据层 quickTimeEnabled 是"课表按参数实时计算"的总开关，
    // 与它解耦：否则开关落 false 而时间只在参数里时，课表会掉回内置默认时间。
    var quickExpanded by remember(timeConfig) { mutableStateOf(timeConfig.quickTimeEnabled) }
    var classDuration by remember(timeConfig) { mutableIntStateOf(timeConfig.classDuration) }
    var shortBreak by remember(timeConfig) { mutableIntStateOf(timeConfig.shortBreak) }
    var longBreakEnabled by remember(timeConfig) { mutableStateOf(timeConfig.longBreakEnabled) }
    var longBreakMorning by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakMorning) }
    var longBreakAfternoon by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakAfternoon) }
    var longBreakEvening by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakEvening) }
    var longBreakMorningSection by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakMorningSection) }
    var longBreakAfternoonSection by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakAfternoonSection) }
    var longBreakEveningSection by remember(timeConfig) { mutableIntStateOf(timeConfig.longBreakEveningSection) }
    var morningStartHour by remember(timeConfig) { mutableIntStateOf(timeConfig.morningStartHour) }
    var morningStartMinute by remember(timeConfig) { mutableIntStateOf(timeConfig.morningStartMinute) }
    var afternoonStartHour by remember(timeConfig) { mutableIntStateOf(timeConfig.afternoonStartHour) }
    var afternoonStartMinute by remember(timeConfig) { mutableIntStateOf(timeConfig.afternoonStartMinute) }
    var eveningStartHour by remember(timeConfig) { mutableIntStateOf(timeConfig.eveningStartHour) }
    var eveningStartMinute by remember(timeConfig) { mutableIntStateOf(timeConfig.eveningStartMinute) }

    // 节次时间

    var showQuickItemDialog by remember { mutableStateOf(false) }
    var quickEditType by remember { mutableStateOf("") }
    var quickTempValue by remember { mutableIntStateOf(0) }
    var quickTempSection by remember { mutableIntStateOf(2) }
    var quickTempHour by remember { mutableIntStateOf(0) }
    var quickTempMinute by remember { mutableIntStateOf(0) }

    var showTimeDialog by remember { mutableStateOf(false) }
    var editingSection by remember { mutableIntStateOf(1) }
    var editingPeriod by remember { mutableStateOf("morning") }
    var tempStartHour by remember { mutableIntStateOf(8) }
    var tempStartMinute by remember { mutableIntStateOf(0) }
    var tempEndHour by remember { mutableIntStateOf(8) }
    var tempEndMinute by remember { mutableIntStateOf(45) }

    var specialBlocks by remember(timeConfig) { mutableStateOf(sortSpecialBlocksByTime(timeConfig.specialBlocks)) }
    var showSpecialDialog by remember { mutableStateOf(false) }
    var editingSpecialIndex by remember { mutableIntStateOf(-1) }
    var tempSpecialName by remember { mutableStateOf("") }
    var tempSpecialStartHour by remember { mutableIntStateOf(8) }
    var tempSpecialStartMinute by remember { mutableIntStateOf(0) }
    var tempSpecialEndHour by remember { mutableIntStateOf(8) }
    var tempSpecialEndMinute by remember { mutableIntStateOf(40) }

    // 添加入口必须从空白初值开始，不能沿用上一次编辑的内容（resetSpecialTemp 见下方）

    // ---- 作息自身的元信息：生效日期（一级列表 summary 显示的就是它）与删除 ----
    val editingRoutine = timeConfig.safeRoutines.firstOrNull { it.id == routineId }
    val canDeleteRoutine = timeConfig.safeRoutines.size > 1
    var routineEffectiveMonth by remember(timeConfig) {
        mutableIntStateOf(editingRoutine?.effectiveMonth ?: 1)
    }
    var routineEffectiveDay by remember(timeConfig) {
        mutableIntStateOf(editingRoutine?.effectiveDay ?: 1)
    }
    var showRoutineDateDialog by remember { mutableStateOf(false) }
    var tempRoutineMonth by remember { mutableIntStateOf(1) }
    var tempRoutineDay by remember { mutableIntStateOf(1) }
    var showDeleteRoutineDialog by remember { mutableStateOf(false) }
    /** 危险操作红，与 AddCourseDialog 保持一致 */
    val destructiveColor = Color(0xFFF44336)

    var showSpecialDeleteConfirm by remember { mutableStateOf(false) }

    var showOverlapDialog by remember { mutableStateOf(false) }
    var showOverlapWarningDialog by remember { mutableStateOf(false) }
    var overlapMessage by remember { mutableStateOf("") }

    // ===================== Morph Animation =====================
    val density = LocalDensity.current
    val morph = rememberMorphTransition(
        cardLeft = cardLeft,
        cardTop = cardTop,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        screenCornerRadius = screenCornerRadius,
        startCornerRadiusDp = cardStartCornerRadius,
        onBackStart = onBackStart,
        onBack = onBack,
        // 对话框开着时返回只回弹页面，不关页
        interceptBack = { showTimeDialog || showQuickItemDialog },
    )

    fun triggerExitAndBack(onSavePending: (() -> Unit)? = null) {
        keyboardController?.hide()
        focusManager.clearFocus()
        morph.close(onSavePending)
    }

    val minuteValues = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55)

    // 节次时间（从已保存配置读取，只有点"应用"时才重新计算）
    var morningTimes by remember(timeConfig) { mutableStateOf(timeConfig.getPeriodTimes("morning")) }
    var afternoonTimes by remember(timeConfig) { mutableStateOf(timeConfig.getPeriodTimes("afternoon")) }
    var eveningTimes by remember(timeConfig) { mutableStateOf(timeConfig.getPeriodTimes("evening")) }

    // 默认时段跟着第一节课走（第1节开始前 40 分钟），别再写死 08:00-08:40 —— 那几乎必然
    // 和第1节撞车，用户一添加就踩重叠。（函数放在此处是因为要读下面的 morningTimes。）
    fun resetSpecialTemp() {
        tempSpecialName = ""
        val firstSectionStart = parseHhMmToMinutes(morningTimes[1]?.substringBefore("-")) ?: (8 * 60)
        val startMin = (firstSectionStart - 40).coerceAtLeast(0)
        tempSpecialStartHour = startMin / 60
        tempSpecialStartMinute = startMin % 60
        val endMin = (startMin + 40).coerceAtMost(23 * 60 + 59)
        tempSpecialEndHour = endMin / 60
        tempSpecialEndMinute = endMin % 60
    }

    // key 同 sectionTimes，如 "morning_1" -> "早自习"
    var sectionNames by remember(timeConfig) { mutableStateOf(timeConfig.sectionNames) }
    var tempSectionName by remember { mutableStateOf("") }

    fun getSectionTitle(period: String, relSection: Int): String {
        val key = "${period}_$relSection"
        val abs = when (period) {
            "morning" -> relSection
            "afternoon" -> morningSections + relSection
            "evening" -> morningSections + afternoonSections + relSection
            else -> relSection
        }
        val name = sectionNames[key]?.takeIf { it.isNotBlank() }
        return if (name != null) TimeEditStr.sectionNameN(abs, name) else TimeEditStr.sectionN(abs)
    }

    // 仅检查当前节数范围内的节次 + 当前特殊课程块
    fun checkTimeOverlap(): OverlapCheck {
        data class TimeRange(val start: Int, val end: Int, val label: String, val isSection: Boolean)

        // 解析 "HH:MM-HH:MM" → 分钟区间；空串或格式非法返回 null（显式跳过）
        fun parseRange(text: String): IntRange? {
            if (text.isBlank()) return null
            val parts = text.split("-")
            if (parts.size != 2) return null
            val startParts = parts[0].split(":")
            val endParts = parts[1].split(":")
            if (startParts.size != 2 || endParts.size != 2) return null
            val start = startParts[0].toIntOrNull()?.times(60)
                ?.plus(startParts[1].toIntOrNull() ?: 0) ?: return null
            val end = endParts[0].toIntOrNull()?.times(60)?.plus(endParts[1].toIntOrNull() ?: 0)
                ?: return null
            return start..end
        }

        val allTimes = mutableListOf<TimeRange>()
        for ((section, timeStr) in morningTimes) {
            if (section > morningSections) continue
            val range = parseRange(timeStr) ?: continue
            allTimes.add(TimeRange(range.first, range.last, TimeEditStr.morningSectionN(section), true))
        }
        for ((section, timeStr) in afternoonTimes) {
            if (section > afternoonSections) continue
            val range = parseRange(timeStr) ?: continue
            allTimes.add(TimeRange(range.first, range.last, TimeEditStr.afternoonSectionN(section), true))
        }
        for ((section, timeStr) in eveningTimes) {
            if (section > eveningSections) continue
            val range = parseRange(timeStr) ?: continue
            allTimes.add(TimeRange(range.first, range.last, TimeEditStr.eveningSectionN(section), true))
        }
        // 特殊课程块也上时间轴：早读/大课间与节次同处一条时间线，重叠会让横带骑到课程卡上。
        for (block in specialBlocks) {
            val s = parseRange("${block.startTime}-${block.endTime}") ?: continue
            if (s.last <= s.first) continue
            val label = block.name.ifBlank { TimeEditStr.specialCourse() }
            allTimes.add(TimeRange(s.first, s.last, label, false))
        }

        var blocking: String? = null
        val warnings = mutableListOf<String>()
        for (i in allTimes.indices) {
            for (j in i + 1 until allTimes.size) {
                val a = allTimes[i]
                val b = allTimes[j]
                if (a.start < b.end && b.start < a.end) {
                    // 节次互相重叠沿用旧口径：直接拦保存。
                    if (a.isSection && b.isSection) {
                        if (blocking == null) blocking = TimeEditStr.overlapPair(a.label, b.label)
                    } else {
                        warnings.add(TimeEditStr.overlapPair(a.label, b.label))
                    }
                }
            }
        }
        return OverlapCheck(blocking, warnings)
    }

    /** 真正落库并退出。顶栏保存与「仍然保存」共用，避免两处各写一份字段清单。 */
    fun commitSave() {
        val finalSectionTimes = mutableMapOf<String, String>()
        for ((k, v) in morningTimes) if (k <= morningSections) finalSectionTimes["morning_$k"] = v
        for ((k, v) in afternoonTimes) if (k <= afternoonSections) finalSectionTimes["afternoon_$k"] = v
        for ((k, v) in eveningTimes) if (k <= eveningSections) finalSectionTimes["evening_$k"] = v
        // 过滤掉超出当前节数范围的自定义名称
        val finalSectionNames = mutableMapOf<String, String>()
        for ((k, v) in sectionNames) {
            val parts = k.split("_")
            if (parts.size != 2) continue
            val period = parts[0]
            val idx = parts[1].toIntOrNull() ?: continue
            val count = when (period) {
                "morning" -> morningSections
                "afternoon" -> afternoonSections
                "evening" -> eveningSections
                else -> 0
            }
            if (idx in 1..count) finalSectionNames[k] = v
        }

        val newConfig = timeConfig.copy(
            name = routineName,
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            // 保存统一落 sectionTimes（所见即所存），quickTimeEnabled 恒为 false：
            // 显示值本就来自参数实时计算，落成 sectionTimes 后开关状态与节次手改不再互相打架。
            quickTimeEnabled = false,
            classDuration = classDuration,
            shortBreak = shortBreak,
            longBreakEnabled = longBreakEnabled,
            longBreakMorning = longBreakMorning,
            longBreakAfternoon = longBreakAfternoon,
            longBreakEvening = longBreakEvening,
            longBreakMorningSection = longBreakMorningSection,
            longBreakAfternoonSection = longBreakAfternoonSection,
            longBreakEveningSection = longBreakEveningSection,
            morningStartHour = morningStartHour,
            morningStartMinute = morningStartMinute,
            afternoonStartHour = afternoonStartHour,
            afternoonStartMinute = afternoonStartMinute,
            eveningStartHour = eveningStartHour,
            eveningStartMinute = eveningStartMinute,
            sectionTimes = finalSectionTimes,
            sectionNames = finalSectionNames,
            specialBlocks = specialBlocks
        ).withRoutineMeta(
            routineId,
            month = routineEffectiveMonth,
            day = routineEffectiveDay
        )
        triggerExitAndBack(onSavePending = { onSave(newConfig) })
    }

    fun requestSave() {
        if (morph.animating.value) return
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        val check = checkTimeOverlap()
        when {
            check.hasBlocking -> {
                overlapMessage = check.blocking!!
                showOverlapDialog = true
            }
            check.hasWarnings -> {
                overlapMessage = check.warnings.joinToString("\n")
                showOverlapWarningDialog = true
            }
            else -> commitSave()
        }
    }

    // Chrome follows destination intent, not animation completion. Opening remains
    // interruptible; closing restores the parent controls on its very first frame.
    NativeDocumentEditorEffect(
        if (morph.animating.value) null else NativeDocumentEditorChrome(
            title = screenTitle,
            cancel = NativeDocumentAction("xmark", CommonStr.cancel()) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                triggerExitAndBack()
            },
            save = NativeDocumentAction("checkmark", TimeEditStr.saveAndClose()) {
                requestSave()
            },
        )
    )

    val isDark = isAppDarkTheme()
    val isLiquidGlass = liquidGlassBackdrop != null
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    val s = morph.state.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) ComposeColor(0xFF2C2C2C).copy(alpha = s.bgAlpha)
                else ComposeColor.Black.copy(alpha = s.bgAlpha)
            )
            .pointerInput(Unit) { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    transformOrigin = TransformOrigin(s.anchorX, 0.5f)
                    scaleX = s.scale
                    scaleY = s.scale
                    translationX = s.translationX
                    translationY = s.translationY
                }
                .clip(morph.clipShape)
                .background(
                    if (isFabCreation) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.background
                )
        ) {
            if (cardSnapshot != null && s.snapshotAlpha > 0f) {
                TransitionLayerImage(
                    snapshot = cardSnapshot,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle((cardStartCornerRadius / s.scale).dp))
                        .graphicsLayer { alpha = s.snapshotAlpha },
                    contentScale = ContentScale.FillWidth
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = s.contentAlpha }
            ) {
                Scaffold(
                    topBar = {
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop!!,
                            // Native controls stay above the morph; preserve the existing inset contract.
                            topInsetAdjustment = (LocalNativeDocumentChrome.current?.barInset ?: 0.0).dp,
                            noTopStatusBar = hostVerticalBarEdge(),
                        ) {
                            CollapsibleTopAppBar(
                                title = screenTitle,
                                largeTitle = screenTitle,
                                modifier = Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                                ),
                                scrollBehavior = scrollBehavior,
                                contentPadding = {},
                                startAction = if (usesNativeControls) null else { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            triggerExitAndBack()
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Normal.Close,
                                        contentDescription = CommonStr.cancel(),
                                        iconSize = 24.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                                endAction = if (usesNativeControls) null else { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = { requestSave() },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Ok,
                                        contentDescription = TimeEditStr.saveAndClose(),
                                        iconSize = 25.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                            )
                        }
                    },
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                // 无材质档：玻璃全关，采样录屏层不挂
                                if (isLiquidGlass &&
                                    !com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()
                                ) {
                                    Modifier.liquidGlassLayerBackdrop(
                                        liquidGlassBackdrop
                                    )
                                } else Modifier
                            )
                    ) {
                        val listState = rememberLazyListState()
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.firstVisibleItemScrollOffset }
                                .collect { offset ->
                                    listScrollY = offset
                                }
                        }

                        Card(
                            modifier = Modifier.fillMaxSize()
                                .background(MiuixTheme.colorScheme.surface),
                            insideMargin = PaddingValues(0.dp),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surface,
                                contentColor = MiuixTheme.colorScheme.onSurface
                            )
                        ) {
                            val topBarHeightDp = with(density) {
                                scrollBehavior.currentHeightPx.toDp()
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                    .overScrollVertical()
                                    .scrollEndHaptic(
                                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                    )
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    start = tabletHorizontalPadding,
                                    top = paddingValues.calculateTopPadding() + topBarHeightDp - 82.dp,
                                    end = tabletHorizontalPadding,
                                    bottom = 120.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item(key = "config_name") {
                                    SmallTitle(text = TimeCfgStr.routineNameLabel())
                                    NativeMiuixTextField(
                                        value = routineName,
                                        onValueChange = { routineName = it },
                                        label = TimeEditStr.routineNamePlaceholder(),
                                        useLabelAsPlaceholder = true,
                                        requestFocus = isFabCreation
                                    )

                                }

                                // 生效安排：属于作息本身，跟名字一样在这一层改
                                item(key = "routine_meta") {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            ArrowPreference(
                                                title = TimeEditStr.effectiveDate(),
                                                // 只有一套作息时没有可切换的对象，日期无意义
                                                summary = if (canDeleteRoutine) {
                                                    TimeEditStr.effectiveDateSummaryN(routineEffectiveMonth, routineEffectiveDay)
                                                } else TimeCfgStr.alwaysActive(),
                                                enabled = canDeleteRoutine,
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.Confirm
                                                    )
                                                    tempRoutineMonth = routineEffectiveMonth
                                                    tempRoutineDay = routineEffectiveDay
                                                    showRoutineDateDialog = true
                                                },
                                                holdDownState = showRoutineDateDialog
                                            )
                                        }
                                    }
                                }

                                item(key = "quick_settings") {
                                    val bottomEndRadius by animateDpAsState(
                                        if (quickExpanded) 32.dp else 20.dp,
                                        label = "bottomEnd"
                                    )
                                    val bottomStartRadius by animateDpAsState(
                                        if (quickExpanded) 32.dp else 20.dp,
                                        label = "bottomStart"
                                    )
                                    val cardModifier = Modifier.fillMaxWidth().continuousSurface(
                                        color = MiuixTheme.colorScheme.secondaryVariant,
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomEnd = bottomEndRadius,
                                        bottomStart = bottomStartRadius
                                    )
                                    Card(
                                        cornerRadius = 0.dp,
                                        modifier = cardModifier,
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                                    .clickable {
                                                        quickExpanded = !quickExpanded
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    TimeEditStr.quickTimeSettings(),
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurface
                                                )
                                                Switch(
                                                    checked = quickExpanded,
                                                    onCheckedChange = { quickExpanded = it })
                                            }
                                            CardExpand(visible = quickExpanded) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    ArrowPreference(
                                                        title = TimeEditStr.classDurationTitle(),
                                                        endActions = {
                                                            Text(
                                                                TimeEditStr.minutesN(classDuration),
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                                textAlign = TextAlign.End,
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "duration"; quickTempValue =
                                                            classDuration; showQuickItemDialog =
                                                            true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "duration"
                                                    )
                                                    ArrowPreference(
                                                        title = TimeEditStr.shortBreakTitle(),
                                                        endActions = {
                                                            Text(
                                                                TimeEditStr.minutesN(shortBreak),
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                                textAlign = TextAlign.End,
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "short_break"; quickTempValue =
                                                            shortBreak; showQuickItemDialog = true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "short_break"
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth()
                                                            .clickable {
                                                                longBreakEnabled = !longBreakEnabled
                                                            }.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 14.dp
                                                            ),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            TimeEditStr.longBreakTitle(),
                                                            fontSize = 17.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MiuixTheme.colorScheme.onSurface
                                                        )
                                                        Switch(
                                                            checked = longBreakEnabled,
                                                            onCheckedChange = {
                                                                longBreakEnabled = it
                                                            })
                                                    }
                                                    CardExpand(visible = longBreakEnabled) {
                                                        Column {
                                                            ArrowPreference(
                                                                title = TimeEditStr.longBreakMorningTitle(),
                                                                endActions = {
                                                                    Text(
                                                                        TimeEditStr.afterSectionMinutesN(longBreakMorningSection, longBreakMorning),
                                                                        fontSize = 14.5.sp,
                                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                                        textAlign = TextAlign.End,
                                                                    )
                                                                },
                                                                onClick = {
                                                                    quickEditType =
                                                                        "long_morning"; quickTempSection =
                                                                    longBreakMorningSection; quickTempValue =
                                                                    longBreakMorning; showQuickItemDialog =
                                                                    true
                                                                },
                                                                holdDownState = showQuickItemDialog && quickEditType == "long_morning"
                                                            )
                                                            ArrowPreference(
                                                                title = TimeEditStr.longBreakAfternoonTitle(),
                                                                endActions = {
                                                                    Text(
                                                                        TimeEditStr.afterSectionMinutesN(longBreakAfternoonSection, longBreakAfternoon),
                                                                        fontSize = 14.5.sp,
                                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                                        textAlign = TextAlign.End,
                                                                    )
                                                                },
                                                                onClick = {
                                                                    quickEditType =
                                                                        "long_afternoon"; quickTempSection =
                                                                    longBreakAfternoonSection; quickTempValue =
                                                                    longBreakAfternoon; showQuickItemDialog =
                                                                    true
                                                                },
                                                                holdDownState = showQuickItemDialog && quickEditType == "long_afternoon"
                                                            )
                                                            ArrowPreference(
                                                                title = TimeEditStr.longBreakEveningTitle(),
                                                                endActions = {
                                                                    Text(
                                                                        TimeEditStr.afterSectionMinutesN(longBreakEveningSection, longBreakEvening),
                                                                        fontSize = 14.5.sp,
                                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                                        textAlign = TextAlign.End,
                                                                    )
                                                                },
                                                                onClick = {
                                                                    quickEditType =
                                                                        "long_evening"; quickTempSection =
                                                                    longBreakEveningSection; quickTempValue =
                                                                    longBreakEvening; showQuickItemDialog =
                                                                    true
                                                                },
                                                                holdDownState = showQuickItemDialog && quickEditType == "long_evening"
                                                            )
                                                        }
                                                    }
                                                    ArrowPreference(
                                                        title = TimeEditStr.morningStartTitle(),
                                                        endActions = {
                                                            Text(
                                                                formatHhMm(
                                                                    morningStartHour,
                                                                    morningStartMinute
                                                                ),
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                                textAlign = TextAlign.End,
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "start_morning"; quickTempHour =
                                                            morningStartHour; quickTempMinute =
                                                            morningStartMinute; showQuickItemDialog =
                                                            true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "start_morning"
                                                    )
                                                    ArrowPreference(
                                                        title = TimeEditStr.afternoonStartTitle(),
                                                        endActions = {
                                                            Text(
                                                                formatHhMm(
                                                                    afternoonStartHour,
                                                                    afternoonStartMinute
                                                                ),
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                                textAlign = TextAlign.End,
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "start_afternoon"; quickTempHour =
                                                            afternoonStartHour; quickTempMinute =
                                                            afternoonStartMinute; showQuickItemDialog =
                                                            true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "start_afternoon"
                                                    )
                                                    ArrowPreference(
                                                        title = TimeEditStr.eveningStartTitle(),
                                                        endActions = {
                                                            Text(
                                                                formatHhMm(
                                                                    eveningStartHour,
                                                                    eveningStartMinute
                                                                ),
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                                textAlign = TextAlign.End,
                                                            )
                                                        },
                                                        onClick = {
                                                            quickEditType =
                                                                "start_evening"; quickTempHour =
                                                            eveningStartHour; quickTempMinute =
                                                            eveningStartMinute; showQuickItemDialog =
                                                            true
                                                        },
                                                        holdDownState = showQuickItemDialog && quickEditType == "start_evening"
                                                    )
                                                    TextButton(
                                                        text = TimeEditStr.apply(),
                                                        onClick = {
                                                            hapticFeedback.performHapticFeedback(
                                                                HapticFeedbackType.Confirm
                                                            )
                                                            val lbM =
                                                                if (longBreakEnabled) longBreakMorning else shortBreak
                                                            val lbA =
                                                                if (longBreakEnabled) longBreakAfternoon else shortBreak
                                                            val lbE =
                                                                if (longBreakEnabled) longBreakEvening else shortBreak
                                                            val lbsM =
                                                                if (longBreakEnabled) longBreakMorningSection else 2
                                                            val lbsA =
                                                                if (longBreakEnabled) longBreakAfternoonSection else 2
                                                            val lbsE =
                                                                if (longBreakEnabled) longBreakEveningSection else 2
                                                            morningTimes =
                                                                Course.calculatePeriodTimes(
                                                                    morningSections,
                                                                    morningStartHour,
                                                                    morningStartMinute,
                                                                    classDuration,
                                                                    shortBreak,
                                                                    lbM,
                                                                    lbsM
                                                                )
                                                            afternoonTimes =
                                                                Course.calculatePeriodTimes(
                                                                    afternoonSections,
                                                                    afternoonStartHour,
                                                                    afternoonStartMinute,
                                                                    classDuration,
                                                                    shortBreak,
                                                                    lbA,
                                                                    lbsA
                                                                )
                                                            eveningTimes =
                                                                Course.calculatePeriodTimes(
                                                                    eveningSections,
                                                                    eveningStartHour,
                                                                    eveningStartMinute,
                                                                    classDuration,
                                                                    shortBreak,
                                                                    lbE,
                                                                    lbsE
                                                                )
                                                            showToast(TimeEditStr.timeApplied())
                                                        },
                                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                                        modifier = Modifier.fillMaxWidth().padding(
                                                            start = 20.dp,
                                                            end = 20.dp,
                                                            top = 8.dp,
                                                            bottom = 20.dp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 特殊课程（无编号时段块）
                                item(key = "morning") {
                                    SmallTitle(
                                        text = TimeCfgStr.morning(),
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            (1..morningSections).forEach { relSection ->
                                                val timeStr = morningTimes[relSection] ?: ""
                                                ArrowPreference(
                                                    title = getSectionTitle("morning", relSection),
                                                    endActions = {
                                                        Text(
                                                            formatTimeRangeForDisplay(timeStr),
                                                            fontSize = 14.5.sp,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                            textAlign = TextAlign.End,
                                                        )
                                                    },
                                                    onClick = {
                                                        editingSection = relSection; editingPeriod =
                                                        "morning"
                                                        tempSectionName = sectionNames["morning_$relSection"] ?: ""
                                                        val (start, end) = parseTimeRange(timeStr)
                                                        tempStartHour =
                                                            start.first; tempStartMinute =
                                                        start.second
                                                        tempEndHour = end.first; tempEndMinute =
                                                        end.second
                                                        showTimeDialog = true
                                                    },
                                                    holdDownState = showTimeDialog && editingPeriod == "morning" && editingSection == relSection
                                                )
                                            }
                                        }
                                    }
                                }

                                item(key = "afternoon") {
                                    SmallTitle(
                                        text = TimeCfgStr.afternoon(),
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            (1..afternoonSections).forEach { relSection ->
                                                val timeStr = afternoonTimes[relSection] ?: ""
                                                ArrowPreference(
                                                    title = getSectionTitle("afternoon", relSection),
                                                    endActions = {
                                                        Text(
                                                            formatTimeRangeForDisplay(timeStr),
                                                            fontSize = 14.5.sp,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                            textAlign = TextAlign.End,
                                                        )
                                                    },
                                                    onClick = {
                                                        editingSection = relSection; editingPeriod =
                                                        "afternoon"
                                                        tempSectionName = sectionNames["afternoon_$relSection"] ?: ""
                                                        val (start, end) = parseTimeRange(timeStr)
                                                        tempStartHour =
                                                            start.first; tempStartMinute =
                                                        start.second
                                                        tempEndHour = end.first; tempEndMinute =
                                                        end.second
                                                        showTimeDialog = true
                                                    },
                                                    holdDownState = showTimeDialog && editingPeriod == "afternoon" && editingSection == relSection
                                                )
                                            }
                                        }
                                    }
                                }

                                item(key = "evening") {
                                    SmallTitle(
                                        text = TimeCfgStr.evening(),
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            (1..eveningSections).forEach { relSection ->
                                                val timeStr = eveningTimes[relSection] ?: ""
                                                ArrowPreference(
                                                    title = getSectionTitle("evening", relSection),
                                                    endActions = {
                                                        Text(
                                                            formatTimeRangeForDisplay(timeStr),
                                                            fontSize = 14.5.sp,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                            textAlign = TextAlign.End,
                                                        )
                                                    },
                                                    onClick = {
                                                        editingSection = relSection; editingPeriod =
                                                        "evening"
                                                        tempSectionName = sectionNames["evening_$relSection"] ?: ""
                                                        val (start, end) = parseTimeRange(timeStr)
                                                        tempStartHour =
                                                            start.first; tempStartMinute =
                                                        start.second
                                                        tempEndHour = end.first; tempEndMinute =
                                                        end.second
                                                        showTimeDialog = true
                                                    },
                                                    holdDownState = showTimeDialog && editingPeriod == "evening" && editingSection == relSection
                                                )
                                            }
                                        }
                                    }
                                }

                                item(key = "special_courses") {
                                    SmallTitle(
                                        text = TimeEditStr.specialBlocksTitle()
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            CardExpand(visible = specialBlocks.isEmpty()) {
                                                ArrowPreference(
                                                    title = TimeEditStr.noSpecialBlocks(),
                                                    summary = TimeEditStr.noSpecialBlocksSummary(),
                                                    holdDownState = showSpecialDialog,
                                                    onClick = {
                                                        resetSpecialTemp()
                                                        editingSpecialIndex = -1
                                                        showSpecialDialog = true
                                                    }
                                                )
                                            }
                                            CardExpand(visible = specialBlocks.isNotEmpty()) {
                                                // 始终按开始时间排序展示
                                                sortSpecialBlocksByTime(specialBlocks).forEachIndexed { index, block ->
                                                    ArrowPreference(
                                                        title = if (block.name.isNotBlank()) block.name else TimeEditStr.specialCourse(),
                                                        summary = specialBlockSummary(block),
                                                        // 只压暗被点开弹窗的那一项
                                                        holdDownState = showSpecialDialog && index == editingSpecialIndex,
                                                        onClick = {
                                                            editingSpecialIndex = index
                                                            tempSpecialName = block.name
                                                            val (sh, sm) = parseTimeHm(block.startTime)
                                                            tempSpecialStartHour = sh; tempSpecialStartMinute = sm
                                                            val (eh, em) = parseTimeHm(block.endTime)
                                                            tempSpecialEndHour = eh; tempSpecialEndMinute = em
                                                            showSpecialDialog = true
                                                        }
                                                    )
                                                }
                                                ArrowPreference(
                                                    title = TimeEditStr.addSpecialBlock(),
                                                    holdDownState = showSpecialDialog && editingSpecialIndex == -1,
                                                    onClick = {
                                                        // 添加不能带出上一次编辑的名称/时间
                                                        resetSpecialTemp()
                                                        editingSpecialIndex = -1
                                                        showSpecialDialog = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                // 页面最后的危险操作：整宽红字按钮，样式同 AddCourseDialog 的删除按钮
                                if (canDeleteRoutine) {
                                    item(key = "routine_delete") {
                                        Button(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp),
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.Confirm
                                                )
                                                showDeleteRoutineDialog = true
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                color = if (isDark) Color.White.copy(alpha = 0.1f)
                                                else Color.Black.copy(alpha = 0.06f)
                                            ),
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = destructiveColor
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                TimeEditStr.deleteRoutine(),
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = destructiveColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OverlayDialog(
                            title = when (quickEditType) {
                                "duration" -> TimeEditStr.classDurationTitle()
                                "short_break" -> TimeEditStr.shortBreakDurationTitle()
                                "long_morning" -> TimeEditStr.longBreakMorningSetting()
                                "long_afternoon" -> TimeEditStr.longBreakAfternoonSetting()
                                "long_evening" -> TimeEditStr.longBreakEveningSetting()
                                "start_morning" -> TimeEditStr.morningStartTitle()
                                "start_afternoon" -> TimeEditStr.afternoonStartTitle()
                                "start_evening" -> TimeEditStr.eveningStartTitle()
                                else -> ""
                            },
                            show = showQuickItemDialog,
                            onDismissRequest = { showQuickItemDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (quickEditType.startsWith("start_")) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NumberPicker(
                                            value = quickTempHour,
                                            onValueChange = { quickTempHour = it },
                                            range = when (quickEditType) {
                                                "start_morning" -> 6..12; "start_afternoon" -> 12..18; else -> 17..22
                                            },
                                            visibleItemCount = 3,
                                            itemHeight = 60.dp,
                                            label = { it.toString().padStart(2, '0') },
                                            wrapAround = true,
                                            textStyle = MiuixTheme.textStyles.title1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            ":",
                                            style = MiuixTheme.textStyles.title2,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding().offset(y = (-2).dp)
                                        )
                                        val qMinIdx =
                                            minuteValues.indexOf(quickTempMinute).coerceAtLeast(0)
                                        NumberPicker(
                                            value = qMinIdx,
                                            onValueChange = { quickTempMinute = minuteValues[it] },
                                            range = minuteValues.indices,
                                            visibleItemCount = 3,
                                            itemHeight = 60.dp,
                                            label = { minuteValues[it].toString().padStart(2, '0') },
                                            wrapAround = true,
                                            textStyle = MiuixTheme.textStyles.title1,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else if (quickEditType.startsWith("long_")) {
                                    val sectionCount = when (quickEditType) {
                                        "long_morning" -> morningSections
                                        "long_afternoon" -> afternoonSections
                                        "long_evening" -> eveningSections
                                        else -> 4
                                    }
                                    val sectionRange = 1 until sectionCount
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NumberPicker(
                                            value = quickTempSection,
                                            onValueChange = { quickTempSection = it },
                                            range = sectionRange,
                                            visibleItemCount = 3,
                                            itemHeight = 60.dp,
                                            label = { TimeEditStr.afterSectionN(it) },
                                            wrapAround = false,
                                            textStyle = MiuixTheme.textStyles.title2,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val breakOptions = listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60)
                                        val breakIndex =
                                            breakOptions.indexOf(quickTempValue).coerceAtLeast(0)
                                        NumberPicker(
                                            value = breakIndex,
                                            onValueChange = { quickTempValue = breakOptions[it] },
                                            range = breakOptions.indices,
                                            visibleItemCount = 3,
                                            itemHeight = 60.dp,
                                            label = { TimeEditStr.minutesN(breakOptions[it]) },
                                            wrapAround = false,
                                            textStyle = MiuixTheme.textStyles.title2,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    val options = when (quickEditType) {
                                        "duration" -> listOf(40, 45, 50, 55, 60)
                                        "short_break" -> listOf(0, 5, 10, 15, 20, 25)
                                        else -> listOf(5, 10, 15, 20, 25, 30)
                                    }
                                    val currentIndex =
                                        options.indexOf(quickTempValue).coerceAtLeast(0)
                                    NumberPicker(
                                        value = currentIndex,
                                        onValueChange = { quickTempValue = options[it] },
                                        range = options.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { TimeEditStr.minutesN(options[it]) },
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = CommonStr.cancel(),
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showQuickItemDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = CommonStr.ok(),
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            when (quickEditType) {
                                                "duration" -> {
                                                    classDuration = quickTempValue
                                                }

                                                "short_break" -> {
                                                    shortBreak = quickTempValue
                                                }

                                                "long_morning" -> {
                                                    longBreakMorning =
                                                        quickTempValue; longBreakMorningSection =
                                                        quickTempSection
                                                }

                                                "long_afternoon" -> {
                                                    longBreakAfternoon =
                                                        quickTempValue; longBreakAfternoonSection =
                                                        quickTempSection
                                                }

                                                "long_evening" -> {
                                                    longBreakEvening =
                                                        quickTempValue; longBreakEveningSection =
                                                        quickTempSection
                                                }

                                                "start_morning" -> {
                                                    morningStartHour =
                                                        quickTempHour; morningStartMinute =
                                                        quickTempMinute
                                                }

                                                "start_afternoon" -> {
                                                    afternoonStartHour =
                                                        quickTempHour; afternoonStartMinute =
                                                        quickTempMinute
                                                }

                                                "start_evening" -> {
                                                    eveningStartHour =
                                                        quickTempHour; eveningStartMinute =
                                                        quickTempMinute
                                                }
                                            }
                                            showQuickItemDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }


                        OverlayDialog(
                            title = if (editingSpecialIndex == -1) TimeEditStr.addSpecialBlock() else TimeEditStr.editSpecialBlock(),
                            summary = null,
                            show = showSpecialDialog,
                            onDismissRequest = { showSpecialDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                NativeMiuixTextField(
                                    value = tempSpecialName,
                                    onValueChange = { tempSpecialName = it },
                                    label = TimeEditStr.specialCourse(),
                                    useLabelAsPlaceholder = true,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    NumberPicker(
                                        value = tempSpecialStartHour,
                                        onValueChange = { tempSpecialStartHour = it },
                                        range = 0..23,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { it.toString().padStart(2, '0') },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        ":",
                                        style = MiuixTheme.textStyles.paragraph,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding().offset(y = (-2).dp)
                                    )
                                    val sMinIdx = minuteValues.indexOf(tempSpecialStartMinute).coerceAtLeast(0)
                                    NumberPicker(
                                        value = sMinIdx,
                                        onValueChange = { tempSpecialStartMinute = minuteValues[it] },
                                        range = minuteValues.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { minuteValues[it].toString().padStart(2, '0') },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "-",
                                        style = MiuixTheme.textStyles.title2,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding()
                                    )
                                    NumberPicker(
                                        value = tempSpecialEndHour,
                                        onValueChange = { tempSpecialEndHour = it },
                                        range = 0..23,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { it.toString().padStart(2, '0') },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        ":",
                                        style = MiuixTheme.textStyles.paragraph,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding().offset(y = (-2).dp)
                                    )
                                    val eMinIdx = minuteValues.indexOf(tempSpecialEndMinute).coerceAtLeast(0)
                                    NumberPicker(
                                        value = eMinIdx,
                                        onValueChange = { tempSpecialEndMinute = minuteValues[it] },
                                        range = minuteValues.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { minuteValues[it].toString().padStart(2, '0') },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // 按星期的安排：原来只能回课表页点横带逐格填，这里一并能改
                                val editingSpecialBlock = specialBlocks.getOrNull(editingSpecialIndex)
                                if (editingSpecialIndex != -1) {
                                    Text(
                                        text = TimeEditStr.weekdaySchedule(),
                                        fontSize = 15.sp,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                    SpecialItemsEditorSection(
                                        items = editingSpecialBlock?.safeItems ?: emptyList(),
                                        onItemsChange = { newItems ->
                                            val target = editingSpecialIndex
                                            if (target in specialBlocks.indices) {
                                                specialBlocks = specialBlocks.mapIndexed { idx, b ->
                                                    if (idx == target) b.copy(items = newItems) else b
                                                }
                                            }
                                        },
                                        liquidGlassBackdrop = liquidGlassBackdrop
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = CommonStr.cancel(),
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showSpecialDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = CommonStr.save(),
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            val startMinutes = tempSpecialStartHour * 60 + tempSpecialStartMinute
                                            val endMinutes = tempSpecialEndHour * 60 + tempSpecialEndMinute
                                            // 结束不晚于开始按跨午夜处理（次日结束），只有起止完全相同才是无效输入
                                            if (startMinutes == endMinutes) {
                                                showToast(CourseEditStr.timeSameError())
                                                return@TextButton
                                            }
                                            val startStr = formatHhMm(tempSpecialStartHour, tempSpecialStartMinute)
                                            val endStr = formatHhMm(tempSpecialEndHour, tempSpecialEndMinute)
                                            val block = SpecialBlock(
                                                id = if (editingSpecialIndex == -1) Clock.System.now().toEpochMilliseconds() else specialBlocks[editingSpecialIndex].id,
                                                name = tempSpecialName,
                                                startTime = startStr,
                                                endTime = endStr,
                                                items = if (editingSpecialIndex == -1) null else specialBlocks[editingSpecialIndex].items
                                            )
                                            val updated = specialBlocks.toMutableList()
                                            if (editingSpecialIndex == -1) {
                                                updated.add(block)
                                            } else {
                                                updated[editingSpecialIndex] = block
                                            }
                                            // 保持列表按时间排序
                                            specialBlocks = sortSpecialBlocksByTime(updated)
                                            showSpecialDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            // 右上角删除按钮
                            if (editingSpecialIndex != -1) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(y = (-42).dp)
                                        .size(36.dp)
                                        .clip(ContinuousRoundedRectangle(20))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showSpecialDeleteConfirm = true
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = CommonStr.delete(),
                                        colorFilter = ColorFilter.tint(Color(0xFFF44336)),
                                        modifier = Modifier.size(23.dp),
                                    )
                                }
                            }
                            }
                        }

                        OverlayDialog(
                            title = TimeEditStr.deleteSpecialBlock(),
                            summary = TimeEditStr.deleteSpecialBlockConfirm(),
                            show = showSpecialDeleteConfirm,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            onDismissRequest = { showSpecialDeleteConfirm = false }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    CommonStr.cancel(),
                                    {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showSpecialDeleteConfirm = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    CommonStr.delete(),
                                    {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        if (editingSpecialIndex in specialBlocks.indices) {
                                            specialBlocks = sortSpecialBlocksByTime(
                                                specialBlocks.toMutableList().apply { removeAt(editingSpecialIndex) }
                                            )
                                        }
                                        showSpecialDeleteConfirm = false
                                        showSpecialDialog = false
                                    },
                                    textColor = Color(0xFFF44336),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        OverlayDialog(
                            title = when (editingPeriod) {
                                "morning" -> TimeEditStr.sectionTimeSettingsN(editingSection)
                                "afternoon" -> TimeEditStr.sectionTimeSettingsN(morningSections + editingSection)
                                "evening" -> TimeEditStr.sectionTimeSettingsN(morningSections + afternoonSections + editingSection)
                                else -> TimeEditStr.timeSettings()
                            },
                            show = showTimeDialog,
                            onDismissRequest = { showTimeDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                NativeMiuixTextField(
                                    value = tempSectionName,
                                    onValueChange = { tempSectionName = it },
                                    label = TimeEditStr.customSectionName(),
                                    useLabelAsPlaceholder = true,
                                    singleLine = true,
                                    trailingIcon = {
                                        val nameCount = tempSectionName.length
                                        Text(
                                            "$nameCount/2",
                                            fontSize = 14.2.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (nameCount > 2) ComposeColor(0xFFF44336)
                                            else MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            modifier = Modifier.padding(end = 16.dp),
                                            textAlign = TextAlign.Center,
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    NumberPicker(
                                        value = tempStartHour,
                                        onValueChange = { tempStartHour = it },
                                        range = 0..23,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { it.toString().padStart(2, '0') },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        ":",
                                        style = MiuixTheme.textStyles.paragraph,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding().offset(y = (-2).dp)
                                    )
                                    val sMinIdx =
                                        minuteValues.indexOf(tempStartMinute).coerceAtLeast(0)
                                    NumberPicker(
                                        value = sMinIdx,
                                        onValueChange = { tempStartMinute = minuteValues[it] },
                                        range = minuteValues.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { minuteValues[it].toString().padStart(2, '0') },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "-",
                                        style = MiuixTheme.textStyles.title2,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding()
                                    )
                                    NumberPicker(
                                        value = tempEndHour,
                                        onValueChange = { tempEndHour = it },
                                        range = 0..23,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { it.toString().padStart(2, '0') },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        ":",
                                        style = MiuixTheme.textStyles.paragraph,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding().offset(y = (-2).dp)
                                    )
                                    val eMinIdx =
                                        minuteValues.indexOf(tempEndMinute).coerceAtLeast(0)
                                    NumberPicker(
                                        value = eMinIdx,
                                        onValueChange = { tempEndMinute = minuteValues[it] },
                                        range = minuteValues.indices,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { minuteValues[it].toString().padStart(2, '0') },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = CommonStr.cancel(),
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showTimeDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = CommonStr.ok(),
                                        enabled = tempSectionName.length <= 2,
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            val startMinutes = tempStartHour * 60 + tempStartMinute
                                            val endMinutes = tempEndHour * 60 + tempEndMinute
                                            // 结束不晚于开始按跨午夜处理（次日结束），只有起止完全相同才是无效输入
                                            if (startMinutes == endMinutes) {
                                                showToast(CourseEditStr.timeSameError())
                                                return@TextButton
                                            }
                                            val newTimeStr = "${
                                                formatHhMm(
                                                    tempStartHour,
                                                    tempStartMinute
                                                )
                                            }-${
                                                formatHhMm(
                                                    tempEndHour,
                                                    tempEndMinute
                                                )
                                            }"
                                            when (editingPeriod) {
                                                "morning" -> {
                                                    morningTimes = morningTimes.toMutableMap()
                                                        .apply { put(editingSection, newTimeStr) }
                                                }

                                                "afternoon" -> {
                                                    afternoonTimes = afternoonTimes.toMutableMap()
                                                        .apply { put(editingSection, newTimeStr) }
                                                }

                                                "evening" -> {
                                                    eveningTimes = eveningTimes.toMutableMap()
                                                        .apply { put(editingSection, newTimeStr) }
                                                }
                                            }
                                            // 空名称则恢复默认"第N节"
                                            val nameKey = "${editingPeriod}_${editingSection}"
                                            val trimmedName = tempSectionName.trim()
                                            sectionNames = sectionNames.toMutableMap().apply {
                                                if (trimmedName.isEmpty()) remove(nameKey)
                                                else put(nameKey, trimmedName)
                                            }
                                            showTimeDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        OverlayDialog(
                            title = TimeEditStr.timeOverlap(),
                            show = showOverlapDialog,
                            onDismissRequest = { showOverlapDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = overlapMessage,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    // 父 Column 居中摆位：提示语换字时要按中心收放，否则先贴左边
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = TimeEditStr.timeOverlapBlocking(),
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    text = CommonStr.done(),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showOverlapDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 特殊课程与其它时间重叠：只警告。块会插到被压节次之前排开，
                        // 渲染不会坏；老数据也可能本来就重叠，给「仍然保存」出口。
                        OverlayDialog(
                            title = TimeEditStr.timeOverlapWarningTitle(),
                            show = showOverlapWarningDialog,
                            onDismissRequest = { showOverlapWarningDialog = false },
                            liquidGlassBackdrop = liquidGlassBackdrop
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = overlapMessage,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    // 父 Column 居中摆位：提示语换字时要按中心收放，否则先贴左边
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = TimeEditStr.overlapWarningBody(),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = TimeEditStr.backToEdit(),
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showOverlapWarningDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = TimeEditStr.saveAnyway(),
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showOverlapWarningDialog = false
                                            commitSave()
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // ---- 修改生效日期 ----
                        OverlayDialog(
                            title = TimeEditStr.effectiveDate(),
                            summary = TimeEditStr.effectiveDateDialogSummary(),
                            show = showRoutineDateDialog,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            onDismissRequest = { showRoutineDateDialog = false }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NumberPicker(
                                        value = tempRoutineMonth,
                                        // 切月时把日一起夹住：1/31 切到 2 月会变成 2/28
                                        onValueChange = {
                                            tempRoutineMonth = it
                                            tempRoutineDay = TimeRoutine
                                                .clampDayOfMonth(it, tempRoutineDay)
                                        },
                                        range = 1..12,
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { TimeCfgStr.monthN(it) },
                                        wrapAround = true,
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberPicker(
                                        value = tempRoutineDay,
                                        onValueChange = { tempRoutineDay = it },
                                        // 上限跟随月份，否则能选出「2 月 31 日」—— 这样的作息永远不会生效
                                        range = 1..TimeRoutine
                                            .daysInMonth(tempRoutineMonth),
                                        visibleItemCount = 3,
                                        itemHeight = 60.dp,
                                        label = { CommonStr.dateN(it) },
                                        textStyle = MiuixTheme.textStyles.title2,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = CommonStr.cancel(),
                                        onClick = { showRoutineDateDialog = false },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = CommonStr.ok(),
                                        onClick = {
                                            // 同一天只能有一套作息，否则到点切换的结果取决于列表顺序
                                            if (timeConfig.isRoutineDateTaken(
                                                    tempRoutineMonth, tempRoutineDay,
                                                    excludeId = routineId
                                                )
                                            ) {
                                                showToast(TimeCfgStr.routineDateTaken())
                                                return@TextButton
                                            }
                                            routineEffectiveMonth = tempRoutineMonth
                                            routineEffectiveDay = tempRoutineDay
                                            showRoutineDateDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // ---- 删除作息 ----
                        OverlayDialog(
                            title = TimeEditStr.deleteRoutine(),
                            summary = TimeEditStr.deleteRoutineConfirm(
                                (editingRoutine?.name ?: routineName).orDefaultName(TimeCfgStr.defaultRoutineName, TimeCfgStr.seededRoutineName, ImportStr.defaultRoutineName)
                            ),
                            show = showDeleteRoutineDialog,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            onDismissRequest = { showDeleteRoutineDialog = false }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    text = CommonStr.cancel(),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.Confirm
                                        )
                                        showDeleteRoutineDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    text = CommonStr.delete(),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.Confirm
                                        )
                                        showDeleteRoutineDialog = false
                                        onDeleteRoutine(routineId)
                                        triggerExitAndBack()
                                    },
                                    textColor = destructiveColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
