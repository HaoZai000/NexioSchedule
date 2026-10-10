/** 课程时间设置页面 - 一级：管理本课表的节次骨架与作息方案 */
package com.haooz.chedule.ui.activities

import com.haooz.chedule.data.todayLocalDate

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.data.TimeRoutine
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.datetime.LocalDate

data class TimeConfigCardBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/**
 * 一级页面。
 *
 * 交互分层（刻意为之）：
 * - **节次骨架**（上午/下午/晚上各几节）属于「这张课表」，不是某个作息 → 就在这一层改。
 * - **作息方案**才是二级页面的单位：点某个作息才进二级，改的是它自己的一套时间。
 * - 只有一个配置，所以这一层**没有配置名称**，只有「添加作息」。
 */
@SuppressLint("DefaultLocale", "UseOfNonLambdaOffsetOverload", "ConfigurationScreenWidthHeight")
@Composable
fun CourseTimeSettingsScreen(
    onEditRoutine: (TimeRoutine, TimeConfig, TimeConfigCardBounds) -> Unit,
    refreshTrigger: Int = 0,
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val repository = remember { CourseRepository() }
    // 液态玻璃效果的透明下拉颜色（与项目其他页面保持一致）
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    var config by remember {
        mutableStateOf(
            repository.getTimeConfig(
                repository.getScheduleTimeConfigId(repository.getCurrentScheduleId())
            ).ensureRoutine()
        )
    }

    fun refreshList() {
        config = repository.getTimeConfig(
            repository.getScheduleTimeConfigId(repository.getCurrentScheduleId())
        ).ensureRoutine()
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) refreshList()
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    val availableSchedules = remember(config) {
        repository.getScheduleNames().filter { it != repository.getCurrentScheduleId() }
    }

    // ---- 作息相关弹窗状态 ----
    var showSectionPicker by remember { mutableStateOf(false) }
    var secMorning by remember { mutableIntStateOf(4) }
    var secAfternoon by remember { mutableIntStateOf(4) }
    var secEvening by remember { mutableIntStateOf(4) }
    var showAddRoutine by remember { mutableStateOf(false) }
    var newRoutineName by remember { mutableStateOf("") }
    var newRoutineMonth by remember { mutableIntStateOf(todayLocalDate().monthNumber) }
    var newRoutineDay by remember { mutableIntStateOf(todayLocalDate().dayOfMonth) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var copyCandidate by remember { mutableStateOf<String?>(null) }

    fun commit() {
        repository.saveTimeConfig(config)
        // saveTimeConfig 只落盘不发通知：改节次数 / 加作息都会改变节次→时间的映射，
        // 必须广播一次让课程提醒、小部件、手表推送按新数据重排
        repository.notifyTimeConfigChanged()
        refreshList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(topBar = {}) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(
                            hapticFeedbackType = HapticFeedbackType.TextHandleMove
                        )
                        .collapsibleTopInset(scrollBehavior)
                        .then(
                            scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                        ),
                    contentPadding = PaddingValues(
                        start = tabletHorizontalPadding,
                        top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                            (if (isTablet) 24.dp else 12.dp),
                        end = tabletHorizontalPadding,
                        bottom = 60.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. 节次骨架：属于这张课表，收进弹窗里改，不平铺
                    item(key = "sections") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = "课表节数",
                                    summary = "上午 ${config.morningSections} 节 · " +
                                        "下午 ${config.afternoonSections} 节 · " +
                                        "晚上 ${config.eveningSections} 节",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.Confirm
                                        )
                                        secMorning = config.morningSections
                                        secAfternoon = config.afternoonSections
                                        secEvening = config.eveningSections
                                        showSectionPicker = true
                                    },
                                    holdDownState = showSectionPicker
                                )
                            }
                        }
                    }

                    // 2. 作息方案：点进去才进二级。按项目惯例收在一张 Card 里，行间自动分隔
                    item(key = "routines") {
                        SmallTitle(text = "作息方案")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                config.safeRoutines.forEach { routine ->
                                    var bounds by remember {
                                        mutableStateOf(TimeConfigCardBounds(0f, 0f, 0f, 0f))
                                    }
                                    val isActive = config.effectiveRoutineId() == routine.id
                                    ArrowPreference(
                                        title = routine.name,
                                        // 只有一套作息时没有切换对象，显示「始终生效」而不是日期
                                        summary = if (config.safeRoutines.size > 1) {
                                            "${routine.effectiveLabel}生效"
                                        } else "始终生效",
                                        endActions = {
                                            if (isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(
                                                            ContinuousRoundedRectangle(6.dp)
                                                        )
                                                        .background(
                                                            MiuixTheme.colorScheme.primary
                                                                .copy(alpha = 0.15f)
                                                        )
                                                        .padding(
                                                            horizontal = 6.dp,
                                                            vertical = 2.dp
                                                        )
                                                ) {
                                                    Text(
                                                        "生效中",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MiuixTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.Confirm
                                            )
                                            onEditRoutine(routine, config, bounds)
                                        },
                                        holdDownState = false,
                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                                val pos = coordinates.positionInWindow()
                                                bounds = TimeConfigCardBounds(
                                                    left = pos.x,
                                                    top = pos.y,
                                                    width = coordinates.size.width.toFloat(),
                                                    height = coordinates.size.height.toFloat()
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // 添加作息：独立成卡，与作息列表区分开
                    item(key = "add_routine") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = "添加作息",
                                    summary = "同一套节次，换一套时间",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.Confirm
                                        )
                                        // 名称留空由用户填，输入框 label「作息名称」做占位
                                        newRoutineName = ""
                                        newRoutineMonth = todayLocalDate().monthNumber
                                        newRoutineDay = todayLocalDate().dayOfMonth
                                        showAddRoutine = true
                                    },
                                    holdDownState = showAddRoutine
                                )
                            }
                        }
                    }

                    // 3. 从其他课表复制
                    item(key = "copy") {
                        val hasOther = availableSchedules.isNotEmpty()
                        SmallTitle(text = "更多")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = "从其他课表复制",
                                    summary = if (hasOther) "完整复制该课表的节次与全部作息"
                                    else "没有其他课表可复制",
                                    onClick = {
                                        if (!hasOther) return@ArrowPreference
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.Confirm
                                        )
                                        copyCandidate = availableSchedules.firstOrNull()
                                        showCopyDialog = true
                                    },
                                    holdDownState = showCopyDialog
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- 节次选择 ----
    OverlayDialog(
        title = "课表节数设置",
        show = showSectionPicker,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showSectionPicker = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    Triple("上午", { v: Int -> secMorning = v }, secMorning),
                    Triple("下午", { v: Int -> secAfternoon = v }, secAfternoon),
                    Triple("晚上", { v: Int -> secEvening = v }, secEvening)
                ).forEach { (label, onPick, current) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        NumberPicker(
                            value = current,
                            onValueChange = onPick,
                            range = 0..6,
                            visibleItemCount = 3,
                            itemHeight = 50.dp
                        )
                    }
                }
            }

            Text(
                "节次决定课表的行数，所有作息方案共用",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(top = 16.dp)
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
                        showSectionPicker = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        config = config.copy(
                            morningSections = secMorning,
                            afternoonSections = secAfternoon,
                            eveningSections = secEvening
                        )
                        commit()
                        showSectionPicker = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // ---- 添加作息 ----
    OverlayDialog(
        title = "添加作息",
        show = showAddRoutine,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showAddRoutine = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NativeMiuixTextField(
                value = newRoutineName,
                onValueChange = { newRoutineName = it },
                label = "作息名称",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberPicker(
                    value = newRoutineMonth,
                    // 切月时把日一起夹住：1/31 切到 2 月会变成 2/28，而不是留下一个不存在的日期
                    onValueChange = {
                        newRoutineMonth = it
                        newRoutineDay = TimeRoutine.clampDayOfMonth(it, newRoutineDay)
                    },
                    range = 1..12,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    label = { "${it}月" },
                    wrapAround = true,
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f)
                )
                NumberPicker(
                    value = newRoutineDay,
                    onValueChange = { newRoutineDay = it },
                    // 上限跟随月份，否则能选出「2 月 31 日」—— 这样的作息永远不会生效
                    range = 1..TimeRoutine.daysInMonth(newRoutineMonth),
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    label = { "${it}日" },
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "即「${newRoutineMonth}月${newRoutineDay}日」起使用这套时间",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showAddRoutine = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "添加",
                    onClick = {
                        // 同一天只能有一套作息，否则到点切换的结果取决于列表顺序
                        if (config.isRoutineDateTaken(newRoutineMonth, newRoutineDay)) {
                            Toast.makeText(
                                context,
                                "该日期已有作息，请换一个生效日期",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@TextButton
                        }
                        val name = newRoutineName.trim().ifEmpty { "作息" }
                        val (updated, _) = config.withRoutineAdded(
                            name, newRoutineMonth, newRoutineDay
                        )
                        config = updated
                        commit()
                        showAddRoutine = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // ---- 从其他课表复制 ----
    OverlayDialog(
        title = "从其他课表复制",
        show = showCopyDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            showCopyDialog = false
            copyCandidate = null
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (availableSchedules.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Text(
                        "没有其他课表可复制",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                val copyEntry = DropdownEntry(
                    items = availableSchedules.map { name ->
                        val other = repository.getTimeConfig(
                            repository.getScheduleTimeConfigId(name)
                        )
                        DropdownItem(
                            text = name,
                            summary = "上午${other.morningSections}·下午${other.afternoonSections}" +
                                "·晚上${other.eveningSections} · ${other.safeRoutines.size} 套作息",
                            selected = copyCandidate == name,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                copyCandidate = name
                            }
                        )
                    }
                )
                // 下拉按项目惯例收在 Card 内，行高等宽于页面里的列表行
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(color = if (isAppDarkTheme()) Color.White.copy(0.1f) else Color.Black.copy(0.06f)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OverlayDropdownMenu(
                            title = "选择课表",
                            entry = copyEntry,
                            collapseOnSelection = true,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            dropdownColors = liquidGlassDropdownColors,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showCopyDialog = false
                        copyCandidate = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定复制",
                    enabled = copyCandidate != null,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        val picked = copyCandidate
                        if (picked != null) {
                            repository.copyTimeConfigFromSchedule(picked)
                            Toast.makeText(
                                context,
                                "已复制「$picked」的时间配置",
                                Toast.LENGTH_SHORT
                            ).show()
                            // copyTimeConfigFromSchedule 内部已广播「设置变更」，这里只刷列表
                            refreshList()
                        }
                        copyCandidate = null
                        showCopyDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
