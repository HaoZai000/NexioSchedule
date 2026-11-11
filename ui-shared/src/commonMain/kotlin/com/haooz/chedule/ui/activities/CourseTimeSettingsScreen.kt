/** 课程时间设置页面 - 一级：管理本课表的节次骨架与作息方案 */
package com.haooz.chedule.ui.activities

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.model.TimeRoutine
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.i18n.TimeCfgStr
import com.haooz.chedule.i18n.orDefaultName
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.shape.ContinuousRoundedRectangle
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
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

data class TimeConfigCardBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val snapshot: TransitionLayerSnapshot? = null,
)

/**
 * 一级页面。交互分层（刻意为之）：
 * - 节次骨架（上午/下午/晚上各几节）属于「这张课表」而非某个作息，就在这一层改。
 * - 作息方案才是二级页面的单位：点某个作息进二级，改它自己的一套时间。
 * - 只有一个配置，故本层无配置名称，只有「添加作息」。
 */
@Composable
fun CourseTimeSettingsScreen(
    onEditRoutine: (TimeRoutine, TimeConfig, TimeConfigCardBounds) -> Unit,
    refreshTrigger: Int = 0,
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /**
     * 二级编辑页是否打开。点作息行时页面**自己**立刻隐藏源行，宿主在退出动画播完后
     * 把它置 false 才放开；传 null = 本入口没有二级页（桌面预览），不启用隐藏
     */
    morphActive: Boolean? = null,
) {
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val repository = remember { CourseRepository() }
    // 谁被点开就藏谁，不依赖宿主回传 id；可见性由 morphActive 同帧门控，关页即恢复（无异步清状态的一帧空窗）。
    var hiddenRoutineId by remember { mutableStateOf<Long?>(null) }
    // Rows may disappear while their editor is closing; the page owns their drawing layers.
    val graphicsContext = LocalGraphicsContext.current
    val routineLayers = remember(graphicsContext) { mutableMapOf<Long, GraphicsLayer>() }
    DisposableEffect(graphicsContext) {
        onDispose { routineLayers.values.forEach(graphicsContext::releaseGraphicsLayer) }
    }
    val routineBackground = MiuixTheme.colorScheme.surfaceContainer
    // 液态玻璃效果的透明下拉颜色（与项目其他页面保持一致）
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    var config by remember {
        // 走「可写」入口：未绑定的课表会先补绑，否则下面 commit() 的 saveTimeConfig
        // 会把节次/作息改动写进它回退到的那套（别人的）配置
        mutableStateOf(repository.getWritableCurrentTimeConfig().ensureRoutine())
    }

    fun refreshList() {
        config = repository.getWritableCurrentTimeConfig().ensureRoutine()
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) refreshList()
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    val availableSchedules = remember(config) {
        repository.getScheduleNames().filter { it != repository.getCurrentScheduleId() }
    }

    // ---- 作息相关弹窗状态 ----
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
                            pageTopExtra(isTablet, hasSmallTitle = true),
                        end = tabletHorizontalPadding,
                        bottom = 60.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 作息方案：点进去才进二级。按项目惯例收在一张 Card 里，行间自动分隔
                    item(key = "routines") {
                        SmallTitle(text = TimeCfgStr.routinesTitle())
                        // morph 期间整张作息卡都不画，routineLayer 留住上一帧给二级页重放。
                        val hideRoutinesCard = morphActive == true
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent { if (!hideRoutinesCard) drawContent() },
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                config.safeRoutines.forEach { routine ->
                                    val routineLayer = routineLayers.getOrPut(routine.id) {
                                        graphicsContext.createGraphicsLayer()
                                    }
                                    // 只缓存布局坐标，点击时再换算：每次 placement 写一个新 Bounds
                                    // 都是一次分配 + 一次快照写，滚动期间每帧都会发生
                                    val cardCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                                    val isActive = config.effectiveRoutineId() == routine.id
                                    val isMorphing = morphActive == true && hiddenRoutineId == routine.id
                                    ArrowPreference(
                                        title = routine.name.orDefaultName(TimeCfgStr.defaultRoutineName, TimeCfgStr.seededRoutineName, ImportStr.defaultRoutineName),
                                        // 只有一套作息时没有切换对象，显示「始终生效」而不是日期
                                        summary = if (config.safeRoutines.size > 1) {
                                            TimeCfgStr.effectiveFrom(routine.effectiveLabel)
                                        } else TimeCfgStr.alwaysActive(),
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
                                                        TimeCfgStr.activeNow(),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MiuixTheme.colorScheme.primary,
                                                        textAlign = TextAlign.End,
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.Confirm
                                            )
                                            // 先隐藏源行再交给宿主开二级页：同一帧生效，morph 首帧就干净。
                                            if (morphActive != null) hiddenRoutineId = routine.id
                                            val coords = cardCoords[0]
                                            val bounds = if (coords != null && coords.isAttached) {
                                                val pos = coords.positionInWindow()
                                                TimeConfigCardBounds(
                                                    left = pos.x,
                                                    top = pos.y,
                                                    width = coords.size.width.toFloat(),
                                                    height = coords.size.height.toFloat(),
                                                )
                                            } else {
                                                TimeConfigCardBounds(0f, 0f, 0f, 0f)
                                            }
                                            onEditRoutine(routine, config, bounds.copy(
                                                snapshot = TransitionLayerSnapshot(routineLayer)
                                            ))
                                        },
                                        holdDownState = false,
                                        modifier = Modifier
                                            .graphicsLayer { alpha = if (isMorphing) 0f else 1f }
                                            .drawWithContent {
                                                // 源行整笔不画（不只是 alpha=0）：连带 drawRect 底色都不留
                                                if (isMorphing) return@drawWithContent
                                                routineLayer.record {
                                                    drawRect(routineBackground)
                                                    this@drawWithContent.drawContent()
                                                }
                                                drawLayer(routineLayer)
                                            }
                                            .onGloballyPositioned { cardCoords[0] = it }
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
                                    title = TimeCfgStr.addRoutine(),
                                    summary = TimeCfgStr.addRoutineSummary(),
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
                        SmallTitle(text = CommonStr.more())
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = TimeCfgStr.copyFromSchedule(),
                                    summary = if (hasOther) TimeCfgStr.copyFromScheduleSummary()
                                    else TimeCfgStr.noScheduleToCopy(),
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

    // ---- 添加作息 ----
    OverlayDialog(
        title = TimeCfgStr.addRoutine(),
        show = showAddRoutine,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showAddRoutine = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NativeMiuixTextField(
                value = newRoutineName,
                onValueChange = { newRoutineName = it },
                label = TimeCfgStr.routineNameLabel(),
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
                    label = { TimeCfgStr.monthN(it) },
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
                    label = { CommonStr.dateN(it) },
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                TimeCfgStr.effectiveFromN(newRoutineMonth, newRoutineDay),
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
                    text = CommonStr.cancel(),
                    onClick = { showAddRoutine = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.add(),
                    onClick = {
                        // 同一天只能有一套作息，否则到点切换的结果取决于列表顺序
                        if (config.isRoutineDateTaken(newRoutineMonth, newRoutineDay)) {
                            showToast(TimeCfgStr.routineDateTaken())
                            return@TextButton
                        }
                        val name = newRoutineName.trim().ifEmpty { TimeCfgStr.defaultRoutineName() }
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
        title = TimeCfgStr.copyFromSchedule(),
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
                        TimeCfgStr.noScheduleToCopy(),
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
                            summary = TimeCfgStr.sectionsBriefN(
                                other.morningSections,
                                other.afternoonSections,
                                other.eveningSections,
                                other.safeRoutines.size,
                            ),
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
                            title = TimeCfgStr.selectSchedule(),
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
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showCopyDialog = false
                        copyCandidate = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = TimeCfgStr.confirmCopy(),
                    enabled = copyCandidate != null,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        val picked = copyCandidate
                        if (picked != null) {
                            repository.copyTimeConfigFromSchedule(picked)
                            showToast(TimeCfgStr.copiedTimeConfig(picked))
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
