/** 课程时间设置页面 */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
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

class CourseTimeSettingsActivity : ComponentActivity() {
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
                CourseTimeSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@SuppressLint("DefaultLocale", "AutoboxingStateValueProperty")
@Composable
fun CourseTimeSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val repository = remember { CourseRepository(context) }
    var morningTimes by remember { mutableStateOf(repository.getPeriodTimes("morning")) }
    var afternoonTimes by remember { mutableStateOf(repository.getPeriodTimes("afternoon")) }
    var eveningTimes by remember { mutableStateOf(repository.getPeriodTimes("evening")) }
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    val morningSections = remember { mutableIntStateOf(repository.getMorningSections()) }
    val afternoonSections = remember { mutableIntStateOf(repository.getAfternoonSections()) }
    val eveningSections = remember { mutableIntStateOf(repository.getEveningSections()) }

    // 快捷设置
    var quickTimeEnabled by remember { mutableStateOf(repository.getQuickTimeEnabled()) }
    var classDuration by remember { mutableIntStateOf(repository.getClassDuration()) }
    var shortBreak by remember { mutableIntStateOf(repository.getShortBreak()) }
    var longBreakEnabled by remember { mutableStateOf(repository.getLongBreakEnabled()) }
    var longBreakMorning by remember { mutableIntStateOf(repository.getLongBreakMorning()) }
    var longBreakAfternoon by remember { mutableIntStateOf(repository.getLongBreakAfternoon()) }
    var longBreakEvening by remember { mutableIntStateOf(repository.getLongBreakEvening()) }
    var longBreakMorningSection by remember { mutableIntStateOf(repository.getLongBreakMorningSection()) }
    var longBreakAfternoonSection by remember { mutableIntStateOf(repository.getLongBreakAfternoonSection()) }
    var longBreakEveningSection by remember { mutableIntStateOf(repository.getLongBreakEveningSection()) }
    var morningStartHour by remember { mutableIntStateOf(repository.getMorningStartHour()) }
    var morningStartMinute by remember { mutableIntStateOf(repository.getMorningStartMinute()) }
    var afternoonStartHour by remember { mutableIntStateOf(repository.getAfternoonStartHour()) }
    var afternoonStartMinute by remember { mutableIntStateOf(repository.getAfternoonStartMinute()) }
    var eveningStartHour by remember { mutableIntStateOf(repository.getEveningStartHour()) }
    var eveningStartMinute by remember { mutableIntStateOf(repository.getEveningStartMinute()) }
    var showQuickItemDialog by remember { mutableStateOf(false) }
    var quickEditType by remember { mutableStateOf("") }
    var quickTempValue by remember { mutableIntStateOf(0) }
    var quickTempSection by remember { mutableIntStateOf(2) }
    var quickTempHour by remember { mutableIntStateOf(0) }
    var quickTempMinute by remember { mutableIntStateOf(0) }

    // 节次时间编辑弹窗
    var showTimeDialog by remember { mutableStateOf(false) }
    var editingSection by remember { mutableIntStateOf(1) }
    var editingPeriod by remember { mutableStateOf("morning") }
    var tempStartHour by remember { mutableIntStateOf(8) }
    var tempStartMinute by remember { mutableIntStateOf(0) }
    var tempEndHour by remember { mutableIntStateOf(8) }
    var tempEndMinute by remember { mutableIntStateOf(45) }

    val minuteValues = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55)

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    val liquidGlassBackdrop = if (appStyle == "liquidglass") {
        com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    } else null
    val isLiquidGlass = appStyle == "liquidglass"
    val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 30f).coerceIn(0f, 0.7f)
    val topBarColorProgress = ((listScrollY - 50) / 30f).coerceIn(0f, 1f)
    val topBarColor = if (listScrollY < 50) {
        MiuixTheme.colorScheme.surface
    } else {
        val surface = MiuixTheme.colorScheme.surface
        val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
        lerp(surface, target, topBarColorProgress)
    }
    val topAppBarColors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    Scaffold(
        topBar = {
            if (isLiquidGlass && liquidGlassBackdrop != null) {
                val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                ProgressiveBlurTopBar(
                    backdrop = liquidGlassBackdrop,
                ) {
                    SmallTopAppBar(
                        color = Color.Transparent,
                        title = "课程时间",
                        modifier = Modifier.zIndex(1f),
                        navigationIcon = {}
                    )
                    LiquidTopBarButton(
                        onClick = { onBack() },
                        backdrop = liquidGlassBackdrop,
                        icon = MiuixIcons.Medium.ChevronBackward,
                        contentDescription = "返回",
                        modifier = Modifier
                            .zIndex(2f)
                            .offset(x = 20.dp, y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                        iconSize = 22.dp,
                        iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                        useBackdropShadow = true
                    )
                }
            } else {
                TopAppBar(
                    modifier = if (blurAlpha > 0f) {
                        Modifier.textureBlur(backdrop = backdrop, shape = RectangleShape, colors = topAppBarColors)
                    } else Modifier,
                    color = topBarColor,
                    title = "课程时间", largeTitle = "课程时间",
                    scrollBehavior = scrollBehavior,
                    navigationIconPadding = 20.dp,
                    navigationIcon = {
                        IconButton(onClick = { onBack() }) {
                            Icon(MiuixIcons.Back,
                                contentDescription = "返回",
                                modifier = Modifier.size(28.dp))
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
            .then(
                if (liquidGlassBackdrop != null) Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                    top = paddingValues.calculateTopPadding() + if (isLiquidGlass) { if (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() > 0.dp) 0.dp else (-12).dp } else 12.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 快捷设置
                item {
                    val topStartRadius by animateDpAsState(if (quickTimeEnabled) 20.dp else 20.dp, label = "topStart")
                    val topEndRadius by animateDpAsState(if (quickTimeEnabled) 20.dp else 20.dp, label = "topEnd")
                    val bottomEndRadius by animateDpAsState(if (quickTimeEnabled) 32.dp else 20.dp, label = "bottomEnd")
                    val bottomStartRadius by animateDpAsState(if (quickTimeEnabled) 32.dp else 20.dp, label = "bottomStart")
                    val cardModifier = Modifier.fillMaxWidth().squircleSurface(
                        color = MiuixTheme.colorScheme.secondaryVariant,
                        topStart = topStartRadius,
                        topEnd = topEndRadius,
                        bottomEnd = bottomEndRadius,
                        bottomStart = bottomStartRadius
                    )
                    Card(cornerRadius = 0.dp, modifier = cardModifier, insideMargin = PaddingValues(0.dp)) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { quickTimeEnabled = false; repository.setQuickTimeEnabled(false) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("快捷设置时间", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                                        Switch(checked = quickTimeEnabled, onCheckedChange = { quickTimeEnabled = it; repository.setQuickTimeEnabled(it) })
                                    }
                                    AnimatedVisibility(
                                        visible = quickTimeEnabled,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            ArrowPreference(title = "每节课时长",
                                                endActions = { Text("${classDuration}分钟", fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                                onClick = { quickEditType = "duration"; quickTempValue = classDuration; showQuickItemDialog = true },
                                                holdDownState = showQuickItemDialog && quickEditType == "duration")
                                            ArrowPreference(title = "课间休息",
                                                endActions = { Text("${shortBreak}分钟", fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                                onClick = { quickEditType = "short_break"; quickTempValue = shortBreak; showQuickItemDialog = true },
                                                holdDownState = showQuickItemDialog && quickEditType == "short_break")
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clickable { longBreakEnabled = !longBreakEnabled; repository.setLongBreakEnabled(longBreakEnabled) }.padding(horizontal = 16.dp, vertical = 14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("大课间休息", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                                                Switch(checked = longBreakEnabled, onCheckedChange = { longBreakEnabled = it; repository.setLongBreakEnabled(it) })
                                            }
                                            AnimatedVisibility(
                                                visible = longBreakEnabled,
                                                enter = expandVertically(),
                                                exit = shrinkVertically()
                                            ) {
                                                Column {
                                                    ArrowPreference(title = "上午大课间休息",
                                                        endActions = { Text("第${longBreakMorningSection}节后 ${longBreakMorning}分钟", fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                                        onClick = { quickEditType = "long_morning"; quickTempSection = longBreakMorningSection; quickTempValue = longBreakMorning; showQuickItemDialog = true },
                                                        holdDownState = showQuickItemDialog && quickEditType == "long_morning")
                                                    ArrowPreference(title = "下午大课间休息",
                                                        endActions = { Text("第${longBreakAfternoonSection}节后 ${longBreakAfternoon}分钟", fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                                        onClick = { quickEditType = "long_afternoon"; quickTempSection = longBreakAfternoonSection; quickTempValue = longBreakAfternoon; showQuickItemDialog = true },
                                                        holdDownState = showQuickItemDialog && quickEditType == "long_afternoon")
                                                    ArrowPreference(title = "晚上大课间休息",
                                                        endActions = { Text("第${longBreakEveningSection}节后 ${longBreakEvening}分钟", fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                                        onClick = { quickEditType = "long_evening"; quickTempSection = longBreakEveningSection; quickTempValue = longBreakEvening; showQuickItemDialog = true },
                                                        holdDownState = showQuickItemDialog && quickEditType == "long_evening")
                                                }
                                            }
                                            ArrowPreference(title = "上午开始时间",
                                                endActions = { Text(String.format("%02d:%02d", morningStartHour, morningStartMinute), fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                                onClick = { quickEditType = "start_morning"; quickTempHour = morningStartHour; quickTempMinute = morningStartMinute; showQuickItemDialog = true },
                                                holdDownState = showQuickItemDialog && quickEditType == "start_morning")
                                            ArrowPreference(title = "下午开始时间",
                                                endActions = { Text(String.format("%02d:%02d", afternoonStartHour, afternoonStartMinute), fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                                onClick = { quickEditType = "start_afternoon"; quickTempHour = afternoonStartHour; quickTempMinute = afternoonStartMinute; showQuickItemDialog = true },
                                                holdDownState = showQuickItemDialog && quickEditType == "start_afternoon")
                                            ArrowPreference(title = "晚上开始时间",
                                                endActions = { Text(String.format("%02d:%02d", eveningStartHour, eveningStartMinute), fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                                onClick = { quickEditType = "start_evening"; quickTempHour = eveningStartHour; quickTempMinute = eveningStartMinute; showQuickItemDialog = true },
                                                holdDownState = showQuickItemDialog && quickEditType == "start_evening")
                                            TextButton(
                                                text = "应用",
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                    val lbM = if (longBreakEnabled) longBreakMorning else shortBreak
                                                    val lbA = if (longBreakEnabled) longBreakAfternoon else shortBreak
                                                    val lbE = if (longBreakEnabled) longBreakEvening else shortBreak
                                                    val lbsM = if (longBreakEnabled) longBreakMorningSection else 2
                                                    val lbsA = if (longBreakEnabled) longBreakAfternoonSection else 2
                                                    val lbsE = if (longBreakEnabled) longBreakEveningSection else 2
                                                    val mTimes = Course.calculatePeriodTimes(morningSections.value, morningStartHour, morningStartMinute, classDuration, shortBreak, lbM, lbsM)
                                                    val aTimes = Course.calculatePeriodTimes(afternoonSections.value, afternoonStartHour, afternoonStartMinute, classDuration, shortBreak, lbA, lbsA)
                                                    val eTimes = Course.calculatePeriodTimes(eveningSections.value, eveningStartHour, eveningStartMinute, classDuration, shortBreak, lbE, lbsE)
                                                    morningTimes = mTimes; afternoonTimes = aTimes; eveningTimes = eTimes
                                                    repository.savePeriodTimes("morning", mTimes)
                                                    repository.savePeriodTimes("afternoon", aTimes)
                                                    repository.savePeriodTimes("evening", eTimes)
                                                    Toast.makeText(context, "课程时间已应用", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                // 上午
                item {
                    SmallTitle(text = "上午", modifier = Modifier.offset(x = (-16).dp))
                    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            (1..morningSections.value).forEach { relSection ->
                                val timeStr = morningTimes[relSection] ?: ""
                                ArrowPreference(
                                    title = "第${relSection}节",
                                    endActions = { Text(timeStr, fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                    onClick = {
                                        editingSection = relSection; editingPeriod = "morning"
                                        val (start, end) = parseTimeRange(timeStr)
                                        tempStartHour = start.first; tempStartMinute = start.second
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
                    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            (1..afternoonSections.value).forEach { relSection ->
                                val timeStr = afternoonTimes[relSection] ?: ""
                                ArrowPreference(
                                    title = "第${morningSections.value + relSection}节",
                                    endActions = { Text(timeStr, fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                    onClick = {
                                        editingSection = relSection; editingPeriod = "afternoon"
                                        val (start, end) = parseTimeRange(timeStr)
                                        tempStartHour = start.first; tempStartMinute = start.second
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
                    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            (1..eveningSections.value).forEach { relSection ->
                                val timeStr = eveningTimes[relSection] ?: ""
                                ArrowPreference(
                                    title = "第${morningSections.value + afternoonSections.value + relSection}节",
                                    endActions = { Text(timeStr, fontSize = 14.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                    onClick = {
                                        editingSection = relSection; editingPeriod = "evening"
                                        val (start, end) = parseTimeRange(timeStr)
                                        tempStartHour = start.first; tempStartMinute = start.second
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
                outsideMargin = DpSize(17.dp, 12.dp),
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
                            "long_morning" -> morningSections.value
                            "long_afternoon" -> afternoonSections.value
                            "long_evening" -> eveningSections.value
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
                            val breakOptions = listOf(10, 15, 20, 25, 30)
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
                            else -> listOf(10, 15, 20, 25, 30)
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
                                    "duration" -> { classDuration = quickTempValue; repository.setClassDuration(quickTempValue) }
                                    "short_break" -> { shortBreak = quickTempValue; repository.setShortBreak(quickTempValue) }
                                    "long_morning" -> { longBreakMorning = quickTempValue; longBreakMorningSection = quickTempSection; repository.setLongBreakMorning(quickTempValue); repository.setLongBreakMorningSection(quickTempSection) }
                                    "long_afternoon" -> { longBreakAfternoon = quickTempValue; longBreakAfternoonSection = quickTempSection; repository.setLongBreakAfternoon(quickTempValue); repository.setLongBreakAfternoonSection(quickTempSection) }
                                    "long_evening" -> { longBreakEvening = quickTempValue; longBreakEveningSection = quickTempSection; repository.setLongBreakEvening(quickTempValue); repository.setLongBreakEveningSection(quickTempSection) }
                                    "start_morning" -> { morningStartHour = quickTempHour; morningStartMinute = quickTempMinute; repository.setMorningStartHour(quickTempHour); repository.setMorningStartMinute(quickTempMinute) }
                                    "start_afternoon" -> { afternoonStartHour = quickTempHour; afternoonStartMinute = quickTempMinute; repository.setAfternoonStartHour(quickTempHour); repository.setAfternoonStartMinute(quickTempMinute) }
                                    "start_evening" -> { eveningStartHour = quickTempHour; eveningStartMinute = quickTempMinute; repository.setEveningStartHour(quickTempHour); repository.setEveningStartMinute(quickTempMinute) }
                                }
                                showQuickItemDialog = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 节次时间编辑弹窗
            OverlayDialog(
                title = when (editingPeriod) {
                    "morning" -> "第${editingSection}节 时间设置"
                    "afternoon" -> "第${morningSections.value + editingSection}节 时间设置"
                    "evening" -> "第${morningSections.value + afternoonSections.value + editingSection}节 时间设置"
                    else -> "时间设置"
                },
                show = showTimeDialog,
                outsideMargin = DpSize(17.dp, 12.dp),
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
                                when (editingPeriod) {
                                    "morning" -> { morningTimes = morningTimes.toMutableMap().apply { put(editingSection, newTimeStr) }; repository.savePeriodTimes("morning", morningTimes) }
                                    "afternoon" -> { afternoonTimes = afternoonTimes.toMutableMap().apply { put(editingSection, newTimeStr) }; repository.savePeriodTimes("afternoon", afternoonTimes) }
                                    "evening" -> { eveningTimes = eveningTimes.toMutableMap().apply { put(editingSection, newTimeStr) }; repository.savePeriodTimes("evening", eveningTimes) }
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