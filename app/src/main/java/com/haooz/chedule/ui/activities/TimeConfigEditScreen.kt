/** 时间配置编辑页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.kyant.backdrop.backdrops.LayerBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

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

@SuppressLint("DefaultLocale", "AutoboxingStateValueProperty")
@Composable
fun TimeConfigEditScreen(
    timeConfig: TimeConfig,
    onBack: () -> Unit,
    onSave: (TimeConfig) -> Unit,
    liquidGlassBackdrop: LayerBackdrop? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    // 配置名称
    var configName by remember { mutableStateOf(timeConfig.name) }

    // 节数配置
    var morningSections by remember { mutableIntStateOf(timeConfig.morningSections) }
    var afternoonSections by remember { mutableIntStateOf(timeConfig.afternoonSections) }
    var eveningSections by remember { mutableIntStateOf(timeConfig.eveningSections) }

    // 快捷设置
    var quickTimeEnabled by remember { mutableStateOf(timeConfig.quickTimeEnabled) }
    var classDuration by remember { mutableIntStateOf(timeConfig.classDuration) }
    var shortBreak by remember { mutableIntStateOf(timeConfig.shortBreak) }
    var longBreakEnabled by remember { mutableStateOf(timeConfig.longBreakEnabled) }
    var longBreakMorning by remember { mutableIntStateOf(timeConfig.longBreakMorning) }
    var longBreakAfternoon by remember { mutableIntStateOf(timeConfig.longBreakAfternoon) }
    var longBreakEvening by remember { mutableIntStateOf(timeConfig.longBreakEvening) }
    var longBreakMorningSection by remember { mutableIntStateOf(timeConfig.longBreakMorningSection) }
    var longBreakAfternoonSection by remember { mutableIntStateOf(timeConfig.longBreakAfternoonSection) }
    var longBreakEveningSection by remember { mutableIntStateOf(timeConfig.longBreakEveningSection) }
    var morningStartHour by remember { mutableIntStateOf(timeConfig.morningStartHour) }
    var morningStartMinute by remember { mutableIntStateOf(timeConfig.morningStartMinute) }
    var afternoonStartHour by remember { mutableIntStateOf(timeConfig.afternoonStartHour) }
    var afternoonStartMinute by remember { mutableIntStateOf(timeConfig.afternoonStartMinute) }
    var eveningStartHour by remember { mutableIntStateOf(timeConfig.eveningStartHour) }
    var eveningStartMinute by remember { mutableIntStateOf(timeConfig.eveningStartMinute) }

    // 节次时间

    // 快捷设置弹窗状态
    var showQuickItemDialog by remember { mutableStateOf(false) }
    var quickEditType by remember { mutableStateOf("") }
    var quickTempValue by remember { mutableIntStateOf(0) }
    var quickTempSection by remember { mutableIntStateOf(2) }
    var quickTempHour by remember { mutableIntStateOf(0) }
    var quickTempMinute by remember { mutableIntStateOf(0) }

    // 节次时间编辑弹窗状态
    var showTimeDialog by remember { mutableStateOf(false) }
    var editingSection by remember { mutableIntStateOf(1) }
    var editingPeriod by remember { mutableStateOf("morning") }
    var tempStartHour by remember { mutableIntStateOf(8) }
    var tempStartMinute by remember { mutableIntStateOf(0) }
    var tempEndHour by remember { mutableIntStateOf(8) }
    var tempEndMinute by remember { mutableIntStateOf(45) }

    // 节数设置弹窗状态
    var showSectionCountDialog by remember { mutableStateOf(false) }

    val minuteValues = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55)

    // 节次时间（从已保存的配置读取，只有点击"应用"按钮时才重新计算）
    var morningTimes by remember { mutableStateOf(timeConfig.getPeriodTimes("morning")) }
    var afternoonTimes by remember { mutableStateOf(timeConfig.getPeriodTimes("afternoon")) }
    var eveningTimes by remember { mutableStateOf(timeConfig.getPeriodTimes("evening")) }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass" && liquidGlassBackdrop != null
    val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 50f).coerceIn(0f, 0.7f)
    val surface = MiuixTheme.colorScheme.surface
    val topBarColor = if (listScrollY < 50) {
        surface
    } else {
        val topBarColorProgress = ((listScrollY - 50) / 50f).coerceIn(0f, 1f)
        val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
        lerp(surface, target, topBarColorProgress)
    }
    val topAppBarColors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
        ),
        brightness = 0f, contrast = 1f, saturation = 1.2f
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (isLiquidGlass) {
                    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    ProgressiveBlurTopBar(
                        backdrop = liquidGlassBackdrop,
                    ) {
                        SmallTopAppBar(
                            color = Color.Transparent,
                            title = "编辑时间配置",
                            modifier = Modifier.zIndex(1f),
                            navigationIcon = {}
                        )
                        LiquidTopBarButton(
                            onClick = { onBack() },
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.Normal.Close,
                            contentDescription = "返回",
                            modifier = Modifier
                                .zIndex(2f)
                                .offset(x = 20.dp, y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                            iconSize = 22.dp,
                            useBackdropShadow = true
                        )
                        LiquidTopBarButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val finalSectionTimes = mutableMapOf<String, String>()
                                for ((k, v) in morningTimes) finalSectionTimes["morning_$k"] = v
                                for ((k, v) in afternoonTimes) finalSectionTimes["afternoon_$k"] = v
                                for ((k, v) in eveningTimes) finalSectionTimes["evening_$k"] = v

                                val newConfig = timeConfig.copy(
                                    name = configName,
                                    morningSections = morningSections,
                                    afternoonSections = afternoonSections,
                                    eveningSections = eveningSections,
                                    quickTimeEnabled = quickTimeEnabled,
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
                                    sectionTimes = finalSectionTimes
                                )
                                onSave(newConfig)
                                onBack()
                            },
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.Ok,
                            contentDescription = "保存并关闭",
                            modifier = Modifier
                                .zIndex(2f)
                                .align(Alignment.TopEnd)
                                .offset(x = (-20).dp, y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                            iconSize = 23.dp,
                            useBackdropShadow = true,
                            iconTint = Color.White,
                            containerColor = if (isAppDarkTheme()) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else MiuixTheme.colorScheme.primary.copy(alpha = 0.9f)
                        )
                    }
                } else {
                    TopAppBar(
                        modifier = if (blurAlpha > 0f) {
                            Modifier.textureBlur(backdrop = backdrop, shape = RectangleShape, colors = topAppBarColors!!)
                        } else Modifier,
                        color = topBarColor,
                        title = "编辑时间配置", largeTitle = "编辑时间配置",
                        scrollBehavior = scrollBehavior,
                        navigationIconPadding = 20.dp,
                        navigationIcon = {
                            IconButton(onClick = { onBack() }) {
                                Icon(MiuixIcons.Normal.Close,
                                    contentDescription = "返回",
                                    modifier = Modifier.size(24.dp))
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val finalSectionTimes = mutableMapOf<String, String>()
                                for ((k, v) in morningTimes) finalSectionTimes["morning_$k"] = v
                                for ((k, v) in afternoonTimes) finalSectionTimes["afternoon_$k"] = v
                                for ((k, v) in eveningTimes) finalSectionTimes["evening_$k"] = v

                                val newConfig = timeConfig.copy(
                                    name = configName,
                                    morningSections = morningSections,
                                    afternoonSections = afternoonSections,
                                    eveningSections = eveningSections,
                                    quickTimeEnabled = quickTimeEnabled,
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
                                    sectionTimes = finalSectionTimes
                                )
                                onSave(newConfig)
                                onBack()
                            },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Ok,
                                    contentDescription = "保存并关闭",
                                    modifier = Modifier.size(26.dp)
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
                    .then(
                        if (isLiquidGlass) Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                        else Modifier
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
                    modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surface,
                        contentColor = MiuixTheme.colorScheme.onSurface
                    )
                ) {
                    LazyColumn(
                        state = listState,
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
                            top = if (isLiquidGlass) paddingValues.calculateTopPadding() + (-16).dp else paddingValues.calculateTopPadding(),
                            end = 16.dp,
                            bottom = 120.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 配置名称
                        item {
                            SmallTitle(
                                text = "配置名称",
                                modifier = Modifier.offset(x = (-16).dp)
                            )
                            TextField(
                                value = configName,
                                onValueChange = { configName = it },
                                cornerRadius = 20.dp,
                                label = "请输入配置名称",
                                useLabelAsPlaceholder = true
                            )
                        }

                        // 课表节数设置
                        item {
                            SmallTitle(
                                text = "节次与时间",
                                modifier = Modifier.offset(x = (-16).dp)
                            )
                            Card(
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(0.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ArrowPreference(
                                        title = "课表节数",
                                        endActions = {
                                            Text(
                                                text = "$morningSections·$afternoonSections·$eveningSections",
                                                fontSize = 14.5.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        },
                                        onClick = { showSectionCountDialog = true },
                                        holdDownState = showSectionCountDialog
                                    )
                                }
                            }
                        }

                        // 快捷设置
                        item {
                            val topStartRadius by animateDpAsState(20.dp, label = "topStart")
                            val topEndRadius by animateDpAsState(20.dp, label = "topEnd")
                            val bottomEndRadius by animateDpAsState(
                                if (quickTimeEnabled) 32.dp else 20.dp,
                                label = "bottomEnd"
                            )
                            val bottomStartRadius by animateDpAsState(
                                if (quickTimeEnabled) 32.dp else 20.dp,
                                label = "bottomStart"
                            )
                            val cardModifier = Modifier.fillMaxWidth().squircleSurface(
                                color = MiuixTheme.colorScheme.secondaryVariant,
                                topStart = topStartRadius,
                                topEnd = topEndRadius,
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
                                            .clickable { quickTimeEnabled = !quickTimeEnabled }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "快捷设置时间",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        Switch(
                                            checked = quickTimeEnabled,
                                            onCheckedChange = { quickTimeEnabled = it })
                                    }
                                    AnimatedVisibility(
                                        visible = quickTimeEnabled,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            // 每节课时长
                                            ArrowPreference(
                                                title = "每节课时长",
                                                endActions = {
                                                    Text(
                                                        "${classDuration}分钟",
                                                        fontSize = 14.5.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                },
                                                onClick = {
                                                    quickEditType = "duration"; quickTempValue =
                                                    classDuration; showQuickItemDialog = true
                                                },
                                                holdDownState = showQuickItemDialog && quickEditType == "duration"
                                            )
                                            // 课间休息
                                            ArrowPreference(
                                                title = "课间休息",
                                                endActions = {
                                                    Text(
                                                        "${shortBreak}分钟",
                                                        fontSize = 14.5.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                },
                                                onClick = {
                                                    quickEditType = "short_break"; quickTempValue =
                                                    shortBreak; showQuickItemDialog = true
                                                },
                                                holdDownState = showQuickItemDialog && quickEditType == "short_break"
                                            )
                                            // 大课间休息开关
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    longBreakEnabled = !longBreakEnabled
                                                }.padding(horizontal = 16.dp, vertical = 14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "大课间休息",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurface
                                                )
                                                Switch(
                                                    checked = longBreakEnabled,
                                                    onCheckedChange = { longBreakEnabled = it })
                                            }
                                            AnimatedVisibility(
                                                visible = longBreakEnabled,
                                                enter = expandVertically(),
                                                exit = shrinkVertically()
                                            ) {
                                                Column {
                                                    ArrowPreference(
                                                        title = "上午大课间休息",
                                                        endActions = {
                                                            Text(
                                                                "第${longBreakMorningSection}节后 ${longBreakMorning}分钟",
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
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
                                                        title = "下午大课间休息",
                                                        endActions = {
                                                            Text(
                                                                "第${longBreakAfternoonSection}节后 ${longBreakAfternoon}分钟",
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
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
                                                        title = "晚上大课间休息",
                                                        endActions = {
                                                            Text(
                                                                "第${longBreakEveningSection}节后 ${longBreakEvening}分钟",
                                                                fontSize = 14.5.sp,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
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
                                            // 开始时间
                                            ArrowPreference(
                                                title = "上午开始时间",
                                                endActions = {
                                                    Text(
                                                        String.format(
                                                            "%02d:%02d",
                                                            morningStartHour,
                                                            morningStartMinute
                                                        ),
                                                        fontSize = 14.5.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                },
                                                onClick = {
                                                    quickEditType = "start_morning"; quickTempHour =
                                                    morningStartHour; quickTempMinute =
                                                    morningStartMinute; showQuickItemDialog = true
                                                },
                                                holdDownState = showQuickItemDialog && quickEditType == "start_morning"
                                            )
                                            ArrowPreference(
                                                title = "下午开始时间",
                                                endActions = {
                                                    Text(
                                                        String.format(
                                                            "%02d:%02d",
                                                            afternoonStartHour,
                                                            afternoonStartMinute
                                                        ),
                                                        fontSize = 14.5.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                },
                                                onClick = {
                                                    quickEditType =
                                                        "start_afternoon"; quickTempHour =
                                                    afternoonStartHour; quickTempMinute =
                                                    afternoonStartMinute; showQuickItemDialog = true
                                                },
                                                holdDownState = showQuickItemDialog && quickEditType == "start_afternoon"
                                            )
                                            ArrowPreference(
                                                title = "晚上开始时间",
                                                endActions = {
                                                    Text(
                                                        String.format(
                                                            "%02d:%02d",
                                                            eveningStartHour,
                                                            eveningStartMinute
                                                        ),
                                                        fontSize = 14.5.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                },
                                                onClick = {
                                                    quickEditType = "start_evening"; quickTempHour =
                                                    eveningStartHour; quickTempMinute =
                                                    eveningStartMinute; showQuickItemDialog = true
                                                },
                                                holdDownState = showQuickItemDialog && quickEditType == "start_evening"
                                            )
                                            // 应用按钮
                                            TextButton(
                                                text = "应用",
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
                                                    morningTimes = Course.calculatePeriodTimes(
                                                        morningSections,
                                                        morningStartHour,
                                                        morningStartMinute,
                                                        classDuration,
                                                        shortBreak,
                                                        lbM,
                                                        lbsM
                                                    )
                                                    afternoonTimes = Course.calculatePeriodTimes(
                                                        afternoonSections,
                                                        afternoonStartHour,
                                                        afternoonStartMinute,
                                                        classDuration,
                                                        shortBreak,
                                                        lbA,
                                                        lbsA
                                                    )
                                                    eveningTimes = Course.calculatePeriodTimes(
                                                        eveningSections,
                                                        eveningStartHour,
                                                        eveningStartMinute,
                                                        classDuration,
                                                        shortBreak,
                                                        lbE,
                                                        lbsE
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "课程时间已应用",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
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

                        // 上午
                        item {
                            SmallTitle(text = "上午", modifier = Modifier.offset(x = (-16).dp))
                            Card(
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(0.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    (1..morningSections).forEach { relSection ->
                                        val timeStr = morningTimes[relSection] ?: ""
                                        ArrowPreference(
                                            title = "第${relSection}节",
                                            endActions = {
                                                Text(
                                                    timeStr,
                                                    fontSize = 14.5.sp,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            },
                                            onClick = {
                                                editingSection = relSection; editingPeriod =
                                                "morning"
                                                val (start, end) = parseTimeRange(timeStr)
                                                tempStartHour = start.first; tempStartMinute =
                                                start.second
                                                tempEndHour = end.first; tempEndMinute = end.second
                                                showTimeDialog = true
                                            },
                                            holdDownState = showTimeDialog && editingPeriod == "morning" && editingSection == relSection
                                        )
                                    }
                                }
                            }
                        }

                        // 下午
                        item {
                            SmallTitle(text = "下午", modifier = Modifier.offset(x = (-16).dp))
                            Card(
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(0.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    (1..afternoonSections).forEach { relSection ->
                                        val timeStr = afternoonTimes[relSection] ?: ""
                                        ArrowPreference(
                                            title = "第${morningSections + relSection}节",
                                            endActions = {
                                                Text(
                                                    timeStr,
                                                    fontSize = 14.5.sp,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            },
                                            onClick = {
                                                editingSection = relSection; editingPeriod =
                                                "afternoon"
                                                val (start, end) = parseTimeRange(timeStr)
                                                tempStartHour = start.first; tempStartMinute =
                                                start.second
                                                tempEndHour = end.first; tempEndMinute = end.second
                                                showTimeDialog = true
                                            },
                                            holdDownState = showTimeDialog && editingPeriod == "afternoon" && editingSection == relSection
                                        )
                                    }
                                }
                            }
                        }

                        // 晚上
                        item {
                            SmallTitle(text = "晚上", modifier = Modifier.offset(x = (-16).dp))
                            Card(
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(0.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    (1..eveningSections).forEach { relSection ->
                                        val timeStr = eveningTimes[relSection] ?: ""
                                        ArrowPreference(
                                            title = "第${morningSections + afternoonSections + relSection}节",
                                            endActions = {
                                                Text(
                                                    timeStr,
                                                    fontSize = 14.5.sp,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            },
                                            onClick = {
                                                editingSection = relSection; editingPeriod =
                                                "evening"
                                                val (start, end) = parseTimeRange(timeStr)
                                                tempStartHour = start.first; tempStartMinute =
                                                start.second
                                                tempEndHour = end.first; tempEndMinute = end.second
                                                showTimeDialog = true
                                            },
                                            holdDownState = showTimeDialog && editingPeriod == "evening" && editingSection == relSection
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 快捷设置单项弹窗
                OverlayDialog(
                    title = when (quickEditType) {
                        "duration" -> "每节课时长"
                        "short_break" -> "小课间休息时长"
                        "long_morning" -> "上午大课间设置"
                        "long_afternoon" -> "下午大课间设置"
                        "long_evening" -> "晚上大课间设置"
                        "start_morning" -> "上午开始时间"
                        "start_afternoon" -> "下午开始时间"
                        "start_evening" -> "晚上开始时间"
                        else -> ""
                    },
                    show = showQuickItemDialog,
                    onDismissRequest = { showQuickItemDialog = false }
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (quickEditType.startsWith("start_")) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                NumberPicker(
                                    value = quickTempHour, onValueChange = { quickTempHour = it },
                                    range = when (quickEditType) { "start_morning" -> 6..12; "start_afternoon" -> 12..18; else -> 17..22 },
                                    visibleItemCount = 3, itemHeight = 60.dp,
                                    label = { String.format("%02d", it) }, wrapAround = true,
                                    textStyle = MiuixTheme.textStyles.title1, modifier = Modifier.weight(1f)
                                )
                                Text(":", style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding().offset(y = (-2).dp))
                                val qMinIdx = minuteValues.indexOf(quickTempMinute).coerceAtLeast(0)
                                NumberPicker(
                                    value = qMinIdx, onValueChange = { quickTempMinute = minuteValues[it] },
                                    range = minuteValues.indices, visibleItemCount = 3, itemHeight = 60.dp,
                                    label = { String.format("%02d", minuteValues[it]) }, wrapAround = true,
                                    textStyle = MiuixTheme.textStyles.title1, modifier = Modifier.weight(1f)
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
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                NumberPicker(
                                    value = quickTempSection, onValueChange = { quickTempSection = it },
                                    range = sectionRange,
                                    visibleItemCount = 3, itemHeight = 60.dp,
                                    label = { "第${it}节后" }, wrapAround = false,
                                    textStyle = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f)
                                )
                                val breakOptions = listOf(5, 10, 15, 20, 25, 30)
                                val breakIndex = breakOptions.indexOf(quickTempValue).coerceAtLeast(0)
                                NumberPicker(
                                    value = breakIndex,
                                    onValueChange = { quickTempValue = breakOptions[it] },
                                    range = breakOptions.indices,
                                    visibleItemCount = 3, itemHeight = 60.dp,
                                    label = { "${breakOptions[it]}分钟" }, wrapAround = false,
                                    textStyle = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            val options = when (quickEditType) {
                                "duration" -> listOf(40, 45, 50, 55, 60)
                                "short_break" -> listOf(5, 10, 15, 20, 25)
                                else -> listOf(5, 10, 15, 20, 25, 30)
                            }
                            val currentIndex = options.indexOf(quickTempValue).coerceAtLeast(0)
                            NumberPicker(
                                value = currentIndex,
                                onValueChange = { quickTempValue = options[it] },
                                range = options.indices,
                                visibleItemCount = 3, itemHeight = 60.dp,
                                label = { "${options[it]}分钟" }, textStyle = MiuixTheme.textStyles.title2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(text = "取消",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showQuickItemDialog = false
                                },
                                modifier = Modifier.weight(1f))
                            TextButton(
                                text = "确定",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    when (quickEditType) {
                                        "duration" -> { classDuration = quickTempValue }
                                        "short_break" -> { shortBreak = quickTempValue }
                                        "long_morning" -> { longBreakMorning = quickTempValue; longBreakMorningSection = quickTempSection }
                                        "long_afternoon" -> { longBreakAfternoon = quickTempValue; longBreakAfternoonSection = quickTempSection }
                                        "long_evening" -> { longBreakEvening = quickTempValue; longBreakEveningSection = quickTempSection }
                                        "start_morning" -> { morningStartHour = quickTempHour; morningStartMinute = quickTempMinute }
                                        "start_afternoon" -> { afternoonStartHour = quickTempHour; afternoonStartMinute = quickTempMinute }
                                        "start_evening" -> { eveningStartHour = quickTempHour; eveningStartMinute = quickTempMinute }
                                    }
                                    showQuickItemDialog = false
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 节数设置弹窗 - 三个选择器并排显示
                OverlayDialog(
                    title = "课表节数设置",
                    show = showSectionCountDialog,
                    onDismissRequest = { showSectionCountDialog = false }
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 上午节数
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "上午",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                                NumberPicker(
                                    value = morningSections,
                                    onValueChange = { morningSections = it },
                                    range = 0..6,
                                    visibleItemCount = 3,
                                    itemHeight = 50.dp
                                )
                            }

                            // 下午节数
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "下午",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                                NumberPicker(
                                    value = afternoonSections,
                                    onValueChange = { afternoonSections = it },
                                    range = 0..6,
                                    visibleItemCount = 3,
                                    itemHeight = 50.dp
                                )
                            }

                            // 晚上节数
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "晚上",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                                NumberPicker(
                                    value = eveningSections,
                                    onValueChange = { eveningSections = it },
                                    range = 0..6,
                                    visibleItemCount = 3,
                                    itemHeight = 50.dp
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(text = "取消",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showSectionCountDialog = false
                                },
                                modifier = Modifier.weight(1f))
                            TextButton(
                                text = "确定",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showSectionCountDialog = false
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 节次时间编辑弹窗
                OverlayDialog(
                    title = when (editingPeriod) {
                        "morning" -> "第${editingSection}节时间设置"
                        "afternoon" -> "第${morningSections + editingSection}节时间设置"
                        "evening" -> "第${morningSections + afternoonSections + editingSection}节时间设置"
                        else -> "时间设置"
                    },
                    show = showTimeDialog,
                    onDismissRequest = { showTimeDialog = false }
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            NumberPicker(value = tempStartHour, onValueChange = { tempStartHour = it }, range = 0..23, visibleItemCount = 3, itemHeight = 60.dp, label = { String.format("%02d", it) }, wrapAround = true, textStyle = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f))
                            Text(":", style = MiuixTheme.textStyles.paragraph, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding().offset(y = (-2).dp))
                            val sMinIdx = minuteValues.indexOf(tempStartMinute).coerceAtLeast(0)
                            NumberPicker(value = sMinIdx, onValueChange = { tempStartMinute = minuteValues[it] }, range = minuteValues.indices, visibleItemCount = 3, itemHeight = 60.dp, label = { String.format("%02d", minuteValues[it]) }, wrapAround = true, textStyle = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f))
                            Text("-", style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding())
                            NumberPicker(value = tempEndHour, onValueChange = { tempEndHour = it }, range = 0..23, visibleItemCount = 3, itemHeight = 60.dp, label = { String.format("%02d", it) }, wrapAround = true, textStyle = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f))
                            Text(":", style = MiuixTheme.textStyles.paragraph, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding().offset(y = (-2).dp))
                            val eMinIdx = minuteValues.indexOf(tempEndMinute).coerceAtLeast(0)
                            NumberPicker(value = eMinIdx, onValueChange = { tempEndMinute = minuteValues[it] }, range = minuteValues.indices, visibleItemCount = 3, itemHeight = 60.dp, label = { String.format("%02d", minuteValues[it]) }, wrapAround = true, textStyle = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(text = "取消",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showTimeDialog = false
                                },
                                modifier = Modifier.weight(1f))
                            TextButton(
                                text = "确定",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    val newTimeStr = "${String.format("%02d:%02d", tempStartHour, tempStartMinute)}-${String.format("%02d:%02d", tempEndHour, tempEndMinute)}"
                                    // 直接更新对应的时段时间
                                    when (editingPeriod) {
                                        "morning" -> { morningTimes = morningTimes.toMutableMap().apply { put(editingSection, newTimeStr) } }
                                        "afternoon" -> { afternoonTimes = afternoonTimes.toMutableMap().apply { put(editingSection, newTimeStr) } }
                                        "evening" -> { eveningTimes = eveningTimes.toMutableMap().apply { put(editingSection, newTimeStr) } }
                                    }
                                    showTimeDialog = false
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
