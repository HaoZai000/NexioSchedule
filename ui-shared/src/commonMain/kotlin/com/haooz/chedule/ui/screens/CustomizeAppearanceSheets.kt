package com.haooz.chedule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.data.appearance.CardContentAlignment
import com.haooz.chedule.data.appearance.CardTextColor
import com.haooz.chedule.data.appearance.ThemeMode
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.sheetContentBottomInset
import com.haooz.chedule.ui.utils.TodayViewMode
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.nexioui.blurbottomsheet.BlurBottomSheet
import com.haooz.nexioui.blurbottomsheet.BlurBottomSheetTablet
import com.haooz.nexioui.blurbottomsheet.LocalBlurBottomSheetContentExpanded
import com.haooz.nexioui.blurbottomsheet.LocalSheetContentBackdrop
import com.haooz.nexioui.blurbottomsheet.LocalSheetTopBarMaterial
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.card.CardDefaults
import com.haooz.nexioui.dropdown.DropdownColors
import com.haooz.nexioui.dropdown.DropdownEntry
import com.haooz.nexioui.dropdown.DropdownItem
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Close
import com.haooz.nexioui.iconbutton.IconButton
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import kotlin.math.roundToInt

/** 卡片文字缩放区间：80%~150%，按整数百分比调节。 */
private val CardTextScaleRange = 0.8f..1.5f

/** 外观编辑草稿：页面内唯一状态源，所有条目直接读写同一份 [AppearanceConfig]。 */
@Stable
internal class AppearanceDraft(initial: AppearanceConfig) {
    var config by mutableStateOf(initial.normalizedTextScale())

    fun update(transform: (AppearanceConfig) -> AppearanceConfig) {
        config = transform(config)
    }

    /** 外部（搭配加载 / 复位）同步，值相同时不触发重组。 */
    fun sync(external: AppearanceConfig) {
        val normalized = external.normalizedTextScale()
        if (normalized != config) config = normalized
    }
}

/** 旧存档可能超出新滑杆区间，进入页面时收敛到 80%~150% 并吸附整百分比。 */
private fun AppearanceConfig.normalizedTextScale(): AppearanceConfig = copy(
    cardTextScale = (
        cardTextScale.coerceIn(CardTextScaleRange.start, CardTextScaleRange.endInclusive) *
            100f
        ).roundToInt() / 100f
)

/** 弹窗内分组卡片（左右 16dp 留白，内部由条目自行控 padding）。 */
@Composable
internal fun SheetCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(
            color = if (NexioTheme.isDynamicColor) NexioTheme.colorScheme.surfaceContainerHigh
            else if (isAppDarkTheme()) Color(0xFF303030) else Color(0xFFFFFFFF),
            contentColor = NexioTheme.colorScheme.onSurface
        ),
    ) { content() }
}

/** 玻璃下拉菜单条目。 */
@Composable
private fun SheetDropdown(
    title: String,
    entry: DropdownEntry,
    fallbackBackdrop: GlassBackdrop,
    dropdownColors: DropdownColors,
    enabled: Boolean = true,
) {
    OverlayDropdownMenu(
        title = title,
        entry = entry,
        enabled = enabled,
        liquidGlassBackdrop = LocalSheetContentBackdrop.current ?: fallbackBackdrop,
        dropdownColors = dropdownColors,
    )
}

/**
 * 外观弹窗宿主：手机版贴在底部（半屏吸附），平板版居中浮层，
 * 两种形态共享同一份内容与滚动配置。
 */
@Composable
internal fun CustomizeSheetHost(
    isTablet: Boolean,
    show: Boolean,
    title: String,
    defaultContentHeight: Dp,
    fallbackBackdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val startAction: @Composable (() -> Unit) = {
        SheetCloseButton(onClick = onDismiss, fallbackBackdrop = fallbackBackdrop)
    }
    val body: @Composable () -> Unit = {
        val expanded = LocalBlurBottomSheetContentExpanded.current
        val haptics = LocalHapticFeedback.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        isTablet -> Modifier.heightIn(max = 580.dp)
                        expanded -> Modifier.fillMaxHeight()
                        else -> Modifier.height(defaultContentHeight)
                    }
                )
                // 与列表滚动相关的修饰符必须在外层：否则越界回弹会被计入 contentOffset
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .verticalScroll(rememberScrollState())
                .sheetContentBottomInset(isTablet),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(if (isTablet) 56.dp else 58.dp))
            content()
        }
    }
    if (isTablet) {
        BlurBottomSheetTablet(
            show = show,
            title = title,
            sheetMaxHeight = 320.dp,
            isBottomAligned = true,
            onDismissRequest = onDismiss,
            startAction = startAction,
            content = body,
        )
    } else {
        BlurBottomSheet(
            show = show,
            title = title,
            onDismissRequest = onDismiss,
            startAction = startAction,
            content = body,
        )
    }
}

@Composable
internal fun SheetCloseButton(onClick: () -> Unit, fallbackBackdrop: GlassBackdrop? = null) {
    val backdrop = LocalSheetContentBackdrop.current ?: fallbackBackdrop ?: return
    val material = LocalSheetTopBarMaterial.current
    val isDark = isAppDarkTheme()
    IconButton(
        onClick = onClick,
        backdrop = backdrop,
        icon = NexioIcons.Normal.Close,
        contentDescription = CommonStr.close(),
        modifier = Modifier.padding(start = 16.dp),
        iconSize = 24.dp,
        containerColor = when {
            NexioTheme.isDynamicColor ->
                NexioTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.4f else 0.6f)
            isDark -> Color(0xFF363636).copy(alpha = 0.4f)
            else -> Color.White.copy(alpha = 0.6f)
        },
        backdropAlpha = material.backdropAlpha,
        shadowAlpha = material.shadowAlpha,
    )
}

/** 「效果」弹窗：主题、壁纸、卡片材质。 */
@Composable
internal fun EffectSheetContent(
    draft: AppearanceDraft,
    hasWallpaper: Boolean,
    materialNone: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    fallbackBackdrop: GlassBackdrop,
    dropdownColors: DropdownColors,
) {
    val config = draft.config
    val cardTuningEnabled = hasWallpaper && !materialNone
    val cardTuningDisabledText =
        if (materialNone) SchedStr.noMaterial() else SchedStr.wallpaperRequired()

    SheetCard {
        val themeEntry = if (hasWallpaper) {
            DropdownEntry(
                items = ThemeMode.entries.map { mode ->
                    DropdownItem(
                        text = mode.label,
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) }
                    )
                }
            )
        } else {
            DropdownEntry(
                items = listOf(
                    DropdownItem(text = SchedStr.followApp(), selected = true, onClick = {})
                )
            )
        }
        SheetDropdown(
            title = SchedStr.defaultTheme(),
            entry = themeEntry,
            fallbackBackdrop = fallbackBackdrop,
            dropdownColors = dropdownColors,
            enabled = hasWallpaper,
        )
    }

    SheetCard {
        Column {
            SliderItem(
                label = SchedStr.wallpaperBrightness(),
                value = config.wallpaperBrightness,
                valueRange = -50f..50f,
                keyPoints = listOf(0f),
                enabled = hasWallpaper,
                onValueChange = { value ->
                    if (hasWallpaper) draft.update { it.copy(wallpaperBrightness = value) }
                },
                quantize = { it.roundToInt().toFloat() },
                displayValue = { value ->
                    value.roundToInt().let { if (it > 0) "+$it" else it.toString() }
                },
                parseInput = { it.toFloatOrNull()?.coerceIn(-50f, 50f) }
            )
            SwitchPreference(
                title = SchedStr.wallpaperBlur(),
                checked = config.wallpaperBlur && hasWallpaper,
                enabled = hasWallpaper,
                onCheckedChange = { checked ->
                    if (hasWallpaper) draft.update { it.copy(wallpaperBlur = checked) }
                }
            )
        }
    }

    SheetCard {
        Column {
            SliderItem(
                label = SchedStr.cardTint(),
                value = config.cardAlpha,
                valueRange = 0f..1f,
                keyPoints = listOf(0.12f),
                enabled = true,
                onValueChange = { value -> draft.update { it.copy(cardAlpha = value) } },
                quantize = { (it * 100f).roundToInt() / 100f },
                suffix = "%",
                displayValue = { (it * 100).roundToInt().toString() },
                parseInput = { it.toFloatOrNull()?.let { v -> (v / 100f).coerceIn(0f, 1f) } }
            )
            SliderItem(
                label = SchedStr.cardOpacity(),
                value = config.cardSurfaceAlpha,
                valueRange = 0f..1f,
                keyPoints = listOf(0.34f),
                enabled = hasWallpaper,
                onValueChange = { value ->
                    if (hasWallpaper) draft.update { it.copy(cardSurfaceAlpha = value) }
                },
                quantize = { (it * 100f).roundToInt() / 100f },
                suffix = "%",
                displayValue = { (it * 100).roundToInt().toString() },
                parseInput = { it.toFloatOrNull()?.let { v -> (v / 100f).coerceIn(0f, 1f) } }
            )
        }
    }

    SheetCard {
        Column {
            SliderItem(
                label = SchedStr.cardBlur(),
                // 单位 px：值即 shader 的高斯 σ，不做 density 换算
                value = config.cardBlurRadius,
                valueRange = 0f..24f,
                keyPoints = listOf(12f),
                enabled = cardTuningEnabled,
                onValueChange = { value ->
                    if (hasWallpaper) draft.update { it.copy(cardBlurRadius = value) }
                },
                quantize = { it.roundToInt().coerceIn(0, 24).toFloat() },
                suffix = "px",
                displayValue = { it.roundToInt().toString() },
                parseInput = { it.toFloatOrNull()?.coerceIn(0f, 80f) },
                disabledText = cardTuningDisabledText
            )
            RefractionItem(
                value = config.cardRefraction,
                enabled = cardTuningEnabled,
                onValueChange = { level ->
                    if (hasWallpaper) draft.update { it.copy(cardRefraction = level) }
                },
                disabledText = cardTuningDisabledText
            )
            EdgeThicknessItem(
                value = config.cardEdgeThickness,
                enabled = cardTuningEnabled,
                onValueChange = { level ->
                    if (hasWallpaper) draft.update { it.copy(cardEdgeThickness = level) }
                },
                disabledText = cardTuningDisabledText
            )
            // 高光描边各材质档都有，只按有无壁纸判可用
            SwitchPreference(
                title = SchedStr.cardRim(),
                checked = config.cardRim,
                enabled = hasWallpaper,
                onCheckedChange = { checked ->
                    if (hasWallpaper) draft.update { it.copy(cardRim = checked) }
                }
            )
        }
    }
}

/** 「自定义」弹窗：课程卡片与课表视图。 */
@Composable
internal fun CustomizeSheetContent(
    draft: AppearanceDraft,
    scheduleViewMode: ScheduleViewMode,
    onScheduleViewModeChange: (ScheduleViewMode) -> Unit,
    todayViewMode: TodayViewMode,
    onTodayViewModeChange: (TodayViewMode) -> Unit,
    fallbackBackdrop: GlassBackdrop,
    dropdownColors: DropdownColors,
) {
    val config = draft.config
    val isWeekView = scheduleViewMode == ScheduleViewMode.WEEK

    SheetCard {
        Column {
            SliderItem(
                label = SchedStr.courseCardHeight(),
                value = config.cardHeight,
                valueRange = 34f..92f,
                keyPoints = listOf(54f),
                // 时间轴高度由真实时长决定，卡高档位无效
                enabled = scheduleViewMode != ScheduleViewMode.TIMELINE,
                disabledText = SchedStr.timelineFixedHeight(),
                onValueChange = { value -> draft.update { it.copy(cardHeight = value) } },
                quantize = { it.roundToInt().toFloat() },
                suffix = "dp",
                displayValue = { it.roundToInt().toString() },
                parseInput = { it.toFloatOrNull()?.coerceIn(34f, 92f) }
            )
            SliderItem(
                label = SchedStr.courseCardCornerRadius(),
                value = config.cardCornerRadius,
                valueRange = 0f..48f,
                keyPoints = listOf(10f),
                enabled = true,
                onValueChange = { value -> draft.update { it.copy(cardCornerRadius = value) } },
                quantize = { it.roundToInt().toFloat() },
                suffix = "dp",
                displayValue = { it.roundToInt().toString() },
                parseInput = { it.toFloatOrNull()?.coerceIn(0f, 48f) }
            )
        }
    }

    SheetCard {
        Column {
            val alignmentEntry = DropdownEntry(
                items = CardContentAlignment.entries.map { alignment ->
                    DropdownItem(
                        text = alignment.label,
                        selected = config.cardContentAlignment == alignment,
                        onClick = { draft.update { it.copy(cardContentAlignment = alignment) } }
                    )
                }
            )
            SheetDropdown(
                title = SchedStr.cardContentAlignment(),
                entry = alignmentEntry,
                fallbackBackdrop = fallbackBackdrop,
                dropdownColors = dropdownColors,
            )
            val textColorEntry = DropdownEntry(
                items = CardTextColor.entries.map { textColor ->
                    DropdownItem(
                        text = textColor.label,
                        selected = config.cardTextColor == textColor,
                        onClick = { draft.update { it.copy(cardTextColor = textColor) } }
                    )
                }
            )
            SheetDropdown(
                title = SchedStr.cardTextColor(),
                entry = textColorEntry,
                fallbackBackdrop = fallbackBackdrop,
                dropdownColors = dropdownColors,
            )
            SliderItem(
                label = SchedStr.cardTextScale(),
                value = config.cardTextScale,
                valueRange = CardTextScaleRange,
                keyPoints = listOf(1.0f),
                enabled = true,
                onValueChange = { value -> draft.update { it.copy(cardTextScale = value) } },
                quantize = { (it * 100f).roundToInt() / 100f },
                suffix = "%",
                displayValue = { (it * 100).roundToInt().toString() },
                parseInput = { input ->
                    input.replace(',', '.').toFloatOrNull()?.let { percent ->
                        (percent / 100f).coerceIn(CardTextScaleRange.start, CardTextScaleRange.endInclusive)
                    }
                }
            )
        }
    }

    SheetCard {
        Column {
            SwitchPreference(
                title = SchedStr.showClassroom(),
                checked = config.showClassroom,
                onCheckedChange = { checked -> draft.update { it.copy(showClassroom = checked) } }
            )
            SwitchPreference(
                title = SchedStr.showTeacher(),
                checked = config.showTeacher,
                onCheckedChange = { checked -> draft.update { it.copy(showTeacher = checked) } }
            )
        }
    }

    SheetCard {
        Column {
            val viewModeEntry = DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = SchedStr.viewAsWeek(),
                        selected = scheduleViewMode == ScheduleViewMode.WEEK,
                        onClick = { onScheduleViewModeChange(ScheduleViewMode.WEEK) }
                    ),
                    DropdownItem(
                        text = SchedStr.viewAsTimeline(),
                        selected = scheduleViewMode == ScheduleViewMode.TIMELINE,
                        onClick = { onScheduleViewModeChange(ScheduleViewMode.TIMELINE) }
                    ),
                )
            )
            SheetDropdown(
                title = SchedStr.viewAs(),
                entry = viewModeEntry,
                fallbackBackdrop = fallbackBackdrop,
                dropdownColors = dropdownColors,
            )
            // 今日页可独立选视图
            val todayViewModeEntry = DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = SchedStr.viewAsWeek(),
                        selected = todayViewMode == TodayViewMode.WEEK,
                        onClick = { onTodayViewModeChange(TodayViewMode.WEEK) }
                    ),
                    DropdownItem(
                        text = SchedStr.viewAsTimeline(),
                        selected = todayViewMode == TodayViewMode.TIMELINE,
                        onClick = { onTodayViewModeChange(TodayViewMode.TIMELINE) }
                    ),
                )
            )
            SheetDropdown(
                title = SchedStr.todayViewAs(),
                entry = todayViewModeEntry,
                fallbackBackdrop = fallbackBackdrop,
                dropdownColors = dropdownColors,
            )
            // 时间轴没有节次网格：两个开关在时间轴下置灰
            SwitchPreference(
                title = SchedStr.weekGridLines(),
                checked = config.showWeekGridLines,
                enabled = isWeekView,
                onCheckedChange = { checked -> draft.update { it.copy(showWeekGridLines = checked) } }
            )
            SwitchPreference(
                title = SchedStr.breakDividers(),
                checked = config.showBreakDividers,
                enabled = isWeekView,
                onCheckedChange = { checked -> draft.update { it.copy(showBreakDividers = checked) } }
            )
        }
    }
}
