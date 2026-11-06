/** 切换课程表页面 */
package com.haooz.chedule.ui.activities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick as onSemanticsClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.ScheduleFolder
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SwitchStr
import com.haooz.chedule.i18n.orDefaultName
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.cardRevealLayer
import com.haooz.chedule.ui.components.rememberCardRevealState
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.glass.BarFill
import com.haooz.chedule.ui.glass.BrightLargeTone
import com.haooz.chedule.ui.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.glass.applyLiveGlass
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentActionEffect
import com.haooz.chedule.ui.utils.NativeDocumentLeadingActionEffect
import com.haooz.chedule.ui.utils.NativeDocumentOverlayEffect
import com.haooz.chedule.ui.utils.NativeDocumentSecondaryActionEffect
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.bottomFloatingPadding
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.capsule.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.sign
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 切换课表页底部胶囊的柔光 shader 缓存键：尺寸与其他玻璃件不同，不能共用 uniform。 */
private const val LIVE_SWITCH_BAR_GLASS_KEY = "NexioSoftGlassLiveSwitchBar"

@Composable
fun SwitchScheduleScreen(
    onBack: (TransitionLayerSnapshot?, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    onBackStart: () -> Unit = {},
    onScheduleChanged: () -> Unit = {},
    onScreenReady: (cardBounds: androidx.compose.ui.geometry.Rect) -> Unit = {},
    onContentOffset: (x: Float, y: Float) -> Unit = { _, _ -> },
    pageScale: Float = 1f,
    initialScheduleNames: List<String>? = null,
    initialCurrentScheduleId: String? = null,
    initialScheduleSummaries: Map<String, String>? = null,
    /** 平板左栏内嵌：无卡片形变、无返回关闭，标题由外层分栏绘制 */
    embedded: Boolean = false,
    /** 分享执行：由 Android 宿主注入（ScheduleExport.performScheduleShare 是 Android 专有） */
    onShareSchedule: (scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit = { _, _ -> },
    /** 分享前置检查（课表是否为空）：宿主注入（buildShareScheduleMap 依赖 Gson=R2，留在宿主） */
    isScheduleShareable: (scheduleName: String) -> Boolean = { true },
    /** 内嵌时由外部指定内容顶距，替代按整屏状态栏+折叠标题推算 */
    contentTopPadding: androidx.compose.ui.unit.Dp? = null,
    /** 列表纵向滚动量回调，供内嵌顶栏遮罩使用 */
    onScrollYChanged: (Int) -> Unit = {},
    /** 内嵌标题栏「添加 / 新建文件夹」弹窗开关，由外层标题栏按钮驱动 */
    externalShowAddDialog: androidx.compose.runtime.MutableState<Boolean>? = null,
    externalShowAddFolderDialog: androidx.compose.runtime.MutableState<Boolean>? = null,
    /** 内嵌编辑模式状态：外层标题栏可据此显示 Close 并退出编辑 */
    externalIsEditMode: androidx.compose.runtime.MutableState<Boolean>? = null,
) {
    val nativeChrome = if (embedded) null else LocalNativeDocumentChrome.current
    val nativeNavigation = nativeChrome != null
    val nativeBarInset = (nativeChrome?.barInset ?: 0.0).dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val repository = remember { CourseRepository() }
    val scrollBehavior = rememberSharedScrollBehavior()
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val screenGraphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    // 录制层是否已过期（页面又画过但没重录）。列表**停稳**后补录一次（见下面的
    // drawWithContent）：退出形变要裁这一层，提前录好 → captureCardLayer() 一帧都不用等。
    // ⚠ 不能用「非滚动就录」：滚动停下后顶栏材质的 Animatable 还要跑 0.4~0.6s，
    //    那段时间每帧都画 → 会变成每帧一次整屏离屏渲染。必须等真正停稳。
    val layerStale = remember { booleanArrayOf(true) }
    // 上提到这里：预热要读 isScrollInProgress，draw 修饰符也要读，都在 Box 之前。
    val listState = rememberLazyListState()
    var listSettled by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            listSettled = false
        } else {
            // 与主界面预热同一个防抖口径；写 state 保证一定来一帧 draw 去补录
            delay(200.milliseconds)
            listSettled = true
        }
    }
    // 强制录制请求：必须是 state —— 页面静止不重画时，只有写 state 才能保证一定来一帧 draw。
    var recordRequest by remember { mutableIntStateOf(0) }
    val lastRecordedRequest = remember { intArrayOf(-1) }
    var contentRootX by remember { mutableFloatStateOf(0f) }
    var contentRootY by remember { mutableFloatStateOf(0f) }
    suspend fun captureCardLayer(bounds: androidx.compose.ui.geometry.Rect?): TransitionLayerSnapshot? {
        if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) return null
        // 图层新鲜就直接用（静止常录的正常路径，零等待）；
        // 过期（滚动中/还没录过）才发一次强制请求并等它落定。
        if (layerStale[0]) {
            recordRequest++
            while (lastRecordedRequest[0] != recordRequest) withFrameNanos { }
        }
        val localBounds = androidx.compose.ui.geometry.Rect(
            (bounds.left - contentRootX) / pageScale,
            (bounds.top - contentRootY) / pageScale,
            (bounds.right - contentRootX) / pageScale,
            (bounds.bottom - contentRootY) / pageScale,
        )
        val viewport = androidx.compose.ui.geometry.Rect(
            0f, 0f, screenGraphicsLayer.size.width.toFloat(), screenGraphicsLayer.size.height.toFloat(),
        )
        if (!localBounds.overlaps(viewport)) return null
        return TransitionLayerSnapshot(screenGraphicsLayer, localBounds)
    }

    var scheduleNames by remember {
        mutableStateOf(
            initialScheduleNames ?: repository.getScheduleNames()
        )
    }
    LaunchedEffect(Unit) {
        // initial 值来自 ScheduleViewModel 的实时 StateFlow，已经是最新
        // （initial 为 null）仍然需要读。
        if (initialScheduleNames == null) {
            scheduleNames = repository.getScheduleNames()
        }
    }
    var currentScheduleId by remember {
        mutableStateOf(
            initialCurrentScheduleId ?: repository.getCurrentScheduleId()
        )
    }
    LaunchedEffect(Unit) {
        com.haooz.chedule.ui.utils.FeatureLog.switchSchedule(
            "screen_compose",
            "names=${scheduleNames.size} current=$currentScheduleId"
        )
        if (initialCurrentScheduleId == null) {
            currentScheduleId = repository.getCurrentScheduleId()
        }
    }
    var scheduleSummaries by remember {
        mutableStateOf(
            initialScheduleSummaries?.toMutableMap() ?: mutableMapOf<String, String>()
        )
    }
    LaunchedEffect(initialScheduleSummaries) {
        scheduleSummaries = initialScheduleSummaries?.toMutableMap() ?: mutableMapOf<String, String>()
    }
    // 文件夹：只存「课表名引用」，课表全局顺序仍由 scheduleNames 决定
    var folders by remember { mutableStateOf(repository.getScheduleFolders()) }
    // 当前课表所在文件夹默认展开，保证主页的卡片形变动画能取到它的位置
    var expandedFolderIds by remember {
        mutableStateOf(
            repository.getFolderIdOfSchedule(currentScheduleId)?.let { setOf(it) } ?: emptySet()
        )
    }
    val showAddDialogState: androidx.compose.runtime.MutableState<Boolean> =
        externalShowAddDialog ?: remember { mutableStateOf(false) }
    var showAddDialog by showAddDialogState
    var newScheduleName by remember { mutableStateOf("") }
    val isEditModeState = externalIsEditMode ?: remember { mutableStateOf(false) }
    var isEditMode by isEditModeState
    var editMode by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingScheduleName by remember { mutableStateOf("") }
    var editScheduleName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingScheduleName by remember { mutableStateOf<String?>(null) }
    // 编辑模式选中项：课表名与文件夹 id 分开存，底部栏靠两者判断是否可操作
    var selectedSchedules by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val showAddFolderDialogState: androidx.compose.runtime.MutableState<Boolean> =
        externalShowAddFolderDialog ?: remember { mutableStateOf(false) }
    var showAddFolderDialog by showAddFolderDialogState
    var newFolderName by remember { mutableStateOf("") }
    var showEditFolderDialog by remember { mutableStateOf(false) }
    var editingFolderId by remember { mutableStateOf("") }
    var editFolderName by remember { mutableStateOf("") }
    var showMoveDialog by remember { mutableStateOf(false) }
    var currentCardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var isSharingSchedule by remember { mutableStateOf(false) }
    var showShareConfirmDialog by remember { mutableStateOf(false) }
    var shareConfirmScheduleName by remember { mutableStateOf<String?>(null) }

    fun performShareSchedule(scheduleName: String) {
        if (isSharingSchedule) return
        onShareSchedule(scheduleName) { isSharingSchedule = it }
    }

    // 返回/点当前课表：保持当前选中，不重排列表，只关闭页面
    val dismissKeepCurrent = {
        com.haooz.chedule.ui.utils.FeatureLog.switchSchedule("select_current", currentScheduleId)
        if (!embedded) {
            onBackStart()
            scope.launch {
                val bounds = currentCardBounds
                onBack(captureCardLayer(bounds), bounds)
            }
        }
    }

    /** 选中另一张课表：切换后带着卡片截图做形变退出（根目录与文件夹内共用） */
    fun selectSchedule(scheduleName: String, bounds: androidx.compose.ui.geometry.Rect?) {
        if (scheduleName == currentScheduleId) {
            dismissKeepCurrent()
            return
        }
        com.haooz.chedule.ui.utils.FeatureLog.switchSchedule("select_other", scheduleName)
        // 不重排列表，只切换当前课表
        repository.switchToSchedule(scheduleName)
        currentScheduleId = scheduleName
        onScheduleChanged()
        // 内嵌分栏：切换后留在当前页，不做卡片形变
        if (embedded) return
        onBackStart()
        scope.launch {
            onBack(captureCardLayer(bounds), bounds)
        }
    }

    /** 课表行：根目录与文件夹内共用一份实现 */
    @Composable
    fun ScheduleItem(
        scheduleName: String,
        indent: Boolean,
        itemModifier: Modifier
    ) {
        val summary = remember(scheduleName, scheduleSummaries) {
            scheduleSummaries[scheduleName] ?: repository.getScheduleSummary(scheduleName)
        }
        val isCurrent = scheduleName == currentScheduleId
        var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        ScheduleCardRow(
            scheduleName = scheduleName,
            summary = summary,
            isCurrent = isCurrent,
            isEditMode = isEditMode,
            checked = scheduleName in selectedSchedules,
            onCheckedChange = { isChecked ->
                selectedSchedules = if (isChecked) selectedSchedules + scheduleName
                else selectedSchedules - scheduleName
            },
            isDeleting = deletingScheduleName == scheduleName,
            indent = indent,
            itemModifier = itemModifier,
            onBoundsChanged = { rect ->
                cardBounds = rect
                if (isCurrent) {
                    currentCardBounds = rect
                }
            },
            onClick = { selectSchedule(scheduleName, cardBounds) },
            onLongClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                if (!isEditMode) {
                    isEditMode = true
                    selectedSchedules = setOf(scheduleName)
                    selectedFolders = emptySet()
                    repository.getFolderIdOfSchedule(scheduleName)
                        ?.let { expandedFolderIds = expandedFolderIds + it }
                }
            }
        )
    }

    /** 文件夹行：点击原地展开/折叠内部课表 */
    @Composable
    fun FolderItem(
        folder: ScheduleFolder,
        itemModifier: Modifier
    ) {
        FolderCardRow(
            folderName = folder.name.orDefaultName(SwitchStr.defaultFolderName),
            scheduleCount = folder.schedules.count { it in scheduleNames },
            expanded = folder.id in expandedFolderIds,
            isEditMode = isEditMode,
            checked = folder.id in selectedFolders,
            onCheckedChange = { isChecked ->
                selectedFolders = if (isChecked) selectedFolders + folder.id
                else selectedFolders - folder.id
            },
            itemModifier = itemModifier,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                expandedFolderIds = if (folder.id in expandedFolderIds) {
                    expandedFolderIds - folder.id
                } else {
                    expandedFolderIds + folder.id
                }
            },
            onLongClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                if (!isEditMode) {
                    isEditMode = true
                    selectedFolders = setOf(folder.id)
                    selectedSchedules = emptySet()
                }
            }
        )
    }
    fun exitEditMode() {
        isEditMode = false
        editMode = ""
        selectedSchedules = emptySet()
        selectedFolders = emptySet()
    }

    val focusRequester = remember { FocusRequester() }
    val editFocusRequester = remember { FocusRequester() }
    // 注意：这里不要再挂一个未被消费的 layerBackdrop —— 它会把整页内容每帧额外离屏录制一遍，
    // 而录制结果没有任何 drawBackdrop 使用，等于白烧一整条渲染管线。
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    LaunchedEffect(showAddDialog) {
        if (showAddDialog) {
            delay(180.milliseconds)
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(showEditDialog) {
        if (showEditDialog) {
            delay(180.milliseconds)
            editFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            // 当前课表若在文件夹里，展开它，保证可见（用于主页卡片形变锚点）
            repository.getFolderIdOfSchedule(currentScheduleId)
                ?.let { expandedFolderIds = expandedFolderIds + it }
            // 兜底：长按进入编辑模式时都会先选中某一项；极端情况下未选中任何项时默认选中当前课表
            if (selectedSchedules.isEmpty() && selectedFolders.isEmpty()) {
                selectedSchedules = setOf(currentScheduleId)
            }
        } else {
            // 外层直接改状态退出时，清掉编辑选中，避免下次进入残留
            editMode = ""
            selectedSchedules = emptySet()
            selectedFolders = emptySet()
        }
    }

    NavigationBackHandlerFor(
        enabled = isEditMode,
        onBackCompleted = { exitEditMode() },
        onBackCancelled = { }
    )

    NavigationBackHandlerFor(
        enabled = !isEditMode && !embedded,
        onBackCompleted = { dismissKeepCurrent() },
        onBackCancelled = { }
    )

    val displayTitle = if (isEditMode) SwitchStr.editSchedule() else SwitchStr.allSchedules()

    val modalActive = showAddDialog || showEditDialog || showDeleteDialog ||
        showAddFolderDialog || showEditFolderDialog || showMoveDialog ||
        showShareConfirmDialog || isSharingSchedule
    val tintArgb = MiuixTheme.colorScheme.primary.toArgb().toLong()
    SideEffect {
        nativeChrome?.title = displayTitle
        nativeChrome?.tintArgb = tintArgb
    }
    if (nativeNavigation) {
        NativeDocumentOverlayEffect(isEditMode || modalActive)
        NativeDocumentLeadingActionEffect(NativeDocumentAction(
            symbol = if (isEditMode) "xmark" else "chevron.backward",
            label = if (isEditMode) SwitchStr.closeEdit() else CommonStr.back(),
            enabled = !modalActive,
            onClick = { if (isEditMode) exitEditMode() else dismissKeepCurrent() },
        ))
        NativeDocumentActionEffect(NativeDocumentAction(
            symbol = if (isEditMode) "" else "plus",
            label = SwitchStr.addSchedule(),
            enabled = !isEditMode && !modalActive,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                showAddDialog = true
            },
        ))
        NativeDocumentSecondaryActionEffect(if (isEditMode) null else NativeDocumentAction(
            symbol = "folder.badge.plus",
            label = SwitchStr.newFolder(),
            enabled = !modalActive,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                showAddFolderDialog = true
            },
        ))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.consumeWindowInsets(PaddingValues(top = nativeBarInset)),
            topBar = {
                // 内嵌分栏：标题/操作由外层顶栏承载，这里不画系统式顶栏
                if (!embedded) {
                ProgressiveBlurTopBar(
                    backdrop = liquidGlassBackdrop,
                    topInsetAdjustment = nativeBarInset,
                    noTopStatusBar = hostVerticalBarEdge(),
                ) {
                    CollapsibleTopAppBar(
                        title = displayTitle,
                        largeTitle = displayTitle,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        scrollBehavior = scrollBehavior,
                        contentPadding = {},
                        startAction = if (nativeNavigation) null else { backdropAlpha, shadowAlpha ->
                            LiquidTopBarButton(
                                onClick = {
                                    if (isEditMode) {
                                        exitEditMode()
                                    } else {
                                        dismissKeepCurrent()
                                    }
                                },
                                backdrop = liquidGlassBackdrop,
                                icon = if (isEditMode) MiuixIcons.Normal.Close else MiuixIcons.ChevronBackward,
                                contentDescription = if (isEditMode) CommonStr.close() else CommonStr.back(),
                                performHapticFeedback = false,
                                iconSize = if (isEditMode) 24.dp else 25.dp,
                                iconOffset = if (isEditMode) DpOffset.Zero else DpOffset(
                                    x = (-2).dp,
                                    y = 0.dp
                                ),
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        },
                        endAction = if (!isEditMode && !nativeNavigation) { backdropAlpha, shadowAlpha ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LiquidTopBarButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showAddDialog = true
                                    },
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Add,
                                    contentDescription = CommonStr.add(),
                                    iconSize = 24.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                                Spacer(Modifier.width(8.dp))
                                LiquidTopBarButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showAddFolderDialog = true
                                    },
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.AddFolder,
                                    contentDescription = SwitchStr.newFolder(),
                                    iconSize = 26.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            }
                        } else null,
                    )
                }
                }
            },
            bottomBar = {
                var navBarVisible by remember { mutableStateOf(false) }
                // 滑动选择状态（LiquidGlassDropdownMenu 同款单一状态）
                val dragSelect = remember { BottomBarDragSelectState() }
                // 胶囊水平内边距：既是内容留白，也是滑选格子换算的左缘
                // （与下方 capsule 的 padding(horizontal = ...) 同一个值，改这里两处一起变）
                val bottomBarHPadding = 7.dp
                LaunchedEffect(isEditMode) {
                    if (isEditMode) {
                        navBarVisible = true
                    } else {
                        navBarVisible = false
                    }
                }
                
                AnimatedVisibility(
                    visible = navBarVisible,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    label = "BottomEditBar"
                ) {
                    val checkedCount = selectedSchedules.size + selectedFolders.size
                    val appear by transition.animateFloat(
                        transitionSpec = {
                            if (targetState == EnterExitState.Visible) {
                                tween(durationMillis = 320, easing = FastOutSlowInEasing)
                            } else {
                                tween(durationMillis = 200, easing = FastOutSlowInEasing)
                            }
                        },
                        label = "BottomEditBarAppear"
                    ) { if (it == EnterExitState.Visible) 1f else 0f }

                    val bottombarBlur = remember { Animatable(8f) }
                    LaunchedEffect(isEditMode) {
                        bottombarBlur.animateTo(
                            targetValue = if (isEditMode) 0f else 12f,
                            animationSpec = tween(
                                durationMillis = if (isEditMode) 300 else 200,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    // 胶囊底色：莫奈取色下跟随 surfaceContainer（与首页底栏、教务导入胶囊同一套）；
                    // 非莫奈保持原硬编码（亮 #FFFFFF/0.6、暗 #121212/0.54）；无材质档实色
                    val containerColor = if (MiuixTheme.isDynamicColor) {
                        MiuixTheme.colorScheme.surfaceContainer
                            .copy(alpha = com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(if (isAppDarkTheme()) 0.54f else 0.6f))
                    } else if (!isAppDarkTheme()) Color(0xFFFFFFFF)
                        .copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.6f))
                    else Color(0xFF121212).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.54f))

                    // 柔光玻璃：材质、档位、调色全由材质层给出；模糊走档位值，无材质档整体关。
                    // effects / shape / onDrawSurface 按引用比较，lambda 每帧新建会每帧重录采样层。
                    val bottomBarGlassBlur = AppMaterialSettings.glassBlurDp()
                    val bottomBarGlassOn = AppMaterialSettings.glassEffectsEnabled()
                    val bottomBarGlassStyle = AppMaterialSettings.chromeGlassStyle(
                        base = ScheduleSoftGlassStyle,
                        isDark = isAppDarkTheme(),
                        // 底部胶囊是「贴底 bar」类玻璃：走 BarFill + 大控件材质。
                        fill = BarFill,
                        tone = BrightLargeTone,
                    )
                    val bottomBarShapeBlock: () -> Shape = remember { { ContinuousCapsule() } }
                    val bottomBarEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
                        remember(bottomBarGlassBlur, bottomBarGlassOn, bottomBarGlassStyle) {
                            {
                                if (bottomBarGlassOn) {
                                    blur(bottomBarGlassBlur.dp.toPx())
                                    runtimeShaderEffect(
                                        key = LIVE_SWITCH_BAR_GLASS_KEY,
                                        shaderString = SOFT_GLASS_LIVE_SHADER,
                                        uniformShaderName = "content",
                                    ) {
                                        applyLiveGlass(
                                            style = bottomBarGlassStyle,
                                            width = size.width,
                                            height = size.height,
                                        )
                                    }
                                }
                            }
                        }
                    val bottomBarOnDrawSurface: DrawScope.() -> Unit = remember(containerColor) {
                        { drawRect(containerColor) }
                    }

                    // 按压高光：整条胶囊按哪亮哪。触发挂整条胶囊；下面的总手势会消费事件，
                    // 但 gestureModifier 是 observeConsumed=true 的纯视觉观察者，照常跟手。
                    val bottomBarScope = rememberCoroutineScope()
                    val interactiveHighlight = remember(bottomBarScope) {
                        InteractiveHighlight(animationScope = bottomBarScope)
                    }
                    // 左右跟手推移的累计位移(px) —— 与主页底栏 LiquidBottomTabs 的
                    // offsetAnimation 同角色，归一化/回弹参数也逐字一致
                    val barDragOffset = remember { Animatable(0f) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val r = bottombarBlur.value
                                val blurRadiusPx = if (r > 0.01f) r * density.density else 0f
                                renderEffect = if (blurRadiusPx > 0f && isRenderEffectSupported()) {
                                    BlurEffect(null, blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                                } else null
                            }
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                scaleX = 0.6f + 0.4f * appear
                                scaleY = 0.6f + 0.4f * appear
                                alpha = appear
                                clip = false
                            }
                            // 底部间距与首页底栏 / 教务导入胶囊同一套算法
                            .padding(top = 28.dp, bottom = bottomFloatingPadding()),
                        contentAlignment = Alignment.Center
                    ) {
                        // 外面多套的动画 Box：整条胶囊作为它的内容被整体包住
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(56.dp)
                                // 与主页底栏同一套形变：按住放大 16dp/宽，左右滑按栏宽归一化后
                                // EaseOut 压到 ±4dp。放在 drawBackdrop 之前 = 包住材质、高光与条目一起动。
                                .graphicsLayer {
                                    val fraction =
                                        (barDragOffset.value / size.width).coerceIn(-1f, 1f)
                                    translationX = 4f.dp.toPx() * fraction.sign *
                                        EaseOut.transform(abs(fraction))
                                    val press = interactiveHighlight.pressProgress
                                    val s = 1f + 16f.dp.toPx() / size.width * press
                                    scaleX = s
                                    scaleY = s
                                }
                                .drawBackdrop(
                                    backdrop = liquidGlassBackdrop,
                                    shape = bottomBarShapeBlock,
                                    effects = bottomBarEffects,
                                    highlight = null,
                                    // 柔光自己画玻璃色与方向光；无材质档不跑 shader，必须留容器色
                                    onDrawSurface = if (bottomBarGlassOn) null else bottomBarOnDrawSurface,
                                )
                                // 高光画在材质之上、条目之下（条目在 drawContent 里更靠内），
                                // 且被 drawBackdrop 的 clipPath 裁进胶囊外形
                                .then(interactiveHighlight.modifier)
                                .then(interactiveHighlight.gestureModifier)
                                // 底栏唯一手势：滑动选择 + 左右跟手推移 + 松手回弹。单一状态：按下即
                                // 命中，点按 = 滑动距离为 0 的滑选，故条目上不再有自己的手势。
                                .pointerInput(bottomBarScope) {
                                    val touchSlop = viewConfiguration.touchSlop
                                    awaitEachGesture {
                                        // 条目等宽（都 weight(1f)），按格下标换算命中；
                                        // 在手势内现算，栏宽变化后下次按下即生效
                                        val contentLeft = bottomBarHPadding.toPx()
                                        val cellWidth = (
                                            (size.width - contentLeft * 2f) /
                                                dragSelect.itemCount.coerceAtLeast(1)
                                            ).coerceAtLeast(1f)
                                        fun hitAt(x: Float) = dragSelect.hitTest(
                                            ((x - contentLeft) / cellWidth).toInt()
                                        )

                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        var lastHit = hitAt(down.position.x)
                                        var lastX = down.position.x
                                        var dragging = false
                                        while (true) {
                                            val event =
                                                awaitPointerEvent(PointerEventPass.Main)
                                            val change =
                                                event.changes.firstOrNull { it.id == down.id }
                                            if (change == null || !change.pressed) {
                                                // 松手：先取出命中项再清态，最后执行
                                                val hit = dragSelect.selected
                                                dragSelect.clear()
                                                // 回弹；纯点按没位移时 0→0 立即结束
                                                bottomBarScope.launch {
                                                    barDragOffset.animateTo(
                                                        0f,
                                                        spring(1f, 300f, 0.5f)
                                                    )
                                                }
                                                hit?.action?.invoke()
                                                break
                                            }
                                            if (!dragging && abs(
                                                    change.position.x - down.position.x
                                                ) > touchSlop
                                            ) {
                                                dragging = true
                                            }
                                            if (dragging) {
                                                // 左右推移：主页底栏同款。awaitEachGesture 是
                                                // 受限挂起作用域，suspend 调用只能进协程
                                                val target = barDragOffset.value +
                                                    change.position.x - lastX
                                                bottomBarScope.launch {
                                                    barDragOffset.snapTo(target)
                                                }
                                            }
                                            lastX = change.position.x
                                            // 逐帧改命中：滑动高光跟着手指走
                                            val hit = hitAt(change.position.x)
                                            if (hit != null && hit !== lastHit) {
                                                // 仅「换了一项」才震，按下即命中的那一下不震
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove
                                                )
                                            }
                                            lastHit = hit
                                            change.consume()
                                        }
                                    }
                                }
                                .padding(horizontal = bottomBarHPadding, vertical = 3.5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BottomBarItem(
                                    icon = MiuixIcons.Forward,
                                    state = dragSelect,
                                    index = 0,
                                    label = SwitchStr.share(),
                                    enabled = selectedFolders.isEmpty() && selectedSchedules.size == 1 && !isSharingSchedule,
                                    onClick = {
                                        com.haooz.chedule.ui.utils.FeatureLog.switchSchedule("share_dialog")
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        val selected = selectedSchedules.firstOrNull()
                                        if (selected != null && !isSharingSchedule) {
                                            if (!isScheduleShareable(selected)) {
                                                showToast(SwitchStr.cannotShareEmpty(selected))
                                            } else {
                                                shareConfirmScheduleName = selected
                                                showShareConfirmDialog = true
                                            }
                                        }
                                    }
                                )
                                BottomBarItem(
                                    icon = MiuixIcons.Edit,
                                    state = dragSelect,
                                    index = 1,
                                    label = CommonStr.edit(),
                                    enabled = checkedCount == 1,
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        if (checkedCount != 1) return@BottomBarItem
                                        val folderId = selectedFolders.firstOrNull()
                                        if (folderId != null) {
                                            val folder = folders.find { it.id == folderId }
                                            if (folder != null) {
                                                editingFolderId = folder.id
                                                editFolderName = folder.name.orDefaultName(SwitchStr.defaultFolderName)
                                                showEditFolderDialog = true
                                            }
                                            return@BottomBarItem
                                        }
                                        val selected = selectedSchedules.firstOrNull()
                                        if (selected != null) {
                                            editingScheduleName = selected
                                            editScheduleName = selected
                                            showEditDialog = true
                                        }
                                    }
                                )
                                BottomBarItem(
                                    icon = MiuixIcons.MoveFile,
                                    state = dragSelect,
                                    index = 2,
                                    label = SwitchStr.move(),
                                    enabled = selectedSchedules.isNotEmpty() && selectedFolders.isEmpty(),
                                    onClick = {
                                        com.haooz.chedule.ui.utils.FeatureLog.switchSchedule("move_dialog")
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        showMoveDialog = true
                                    }
                                )
                                BottomBarItem(
                                    icon = MiuixIcons.Delete,
                                    state = dragSelect,
                                    index = 3,
                                    label = CommonStr.delete(),
                                    enabled = checkedCount >= 1,
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        if (checkedCount >= 1) {
                                            showDeleteDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        val pos = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                        contentRootX = pos.x
                        contentRootY = pos.y
                        onContentOffset(pos.x, pos.y)
                    }
                    .drawWithContent {
                        // 静止时常录（退出形变要用这一层，提前录好 = 退出零等待）；
                        // 滚动中只处理显式强制请求，其余帧标脏不录。
                        val forced = lastRecordedRequest[0] != recordRequest
                        // ⚠ embedded（平板左栏）没有卡片形变、不会调 captureCardLayer，别白白常录。
                        // 只用「停稳」信号（防抖过），不用 isScrollInProgress —— 见上面的说明。
                        if (forced || (!embedded && listSettled)) {
                            screenGraphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            lastRecordedRequest[0] = recordRequest
                            layerStale[0] = false
                            drawLayer(screenGraphicsLayer)
                        } else {
                            layerStale[0] = true
                            drawContent()
                        }
                    }
                    .then(
                        // 无材质档：玻璃全关，采样录屏层不挂
                        if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                        else Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                    )
            ) {
                // 注意：这里不要再 collect firstVisibleItemScrollOffset 写 state ——
                // 那会让整页在滚动时每像素重组一次（listScrollY 之前根本没被读取，纯属白烧）。
                if (embedded) {
                    val currentOnScrollYChanged by rememberUpdatedState(onScrollYChanged)
                    LaunchedEffect(listState) {
                        // 与设置页左栏同式：index*8000+offset，越过首项后 offset 归零也不闪
                        snapshotFlow {
                            listState.firstVisibleItemIndex * 8_000 +
                                listState.firstVisibleItemScrollOffset
                        }.collect { offset -> currentOnScrollYChanged(offset) }
                    }
                }
                // Entry uses the current card's geometry without capturing the destination.
                // Keep one delivery per screen, including when later layouts move the card.
                val switchSnapshotDelivered = remember { intArrayOf(0) }
                LaunchedEffect(currentCardBounds) {
                    if (embedded) return@LaunchedEffect
                    if (switchSnapshotDelivered[0] != 0) return@LaunchedEffect
                    val bounds = currentCardBounds
                    if (bounds != null) {
                        // Entry only needs the destination geometry. Reveal the live card
                        // beneath the source morph instead of reading back a second full screen.
                        switchSnapshotDelivered[0] = 1
                        // Deliver the anchor even without a bitmap so entry can begin.
                        val adjustedBounds = androidx.compose.ui.geometry.Rect(
                            left = (bounds.left - contentRootX) / pageScale,
                            top = (bounds.top - contentRootY) / pageScale,
                            right = (bounds.right - contentRootX) / pageScale,
                            bottom = (bounds.bottom - contentRootY) / pageScale
                        )
                        onScreenReady(adjustedBounds)
                    }
                }
                // Once the first list layout is available, an offscreen card cannot
                // provide an entry anchor. Continue immediately rather than waiting 700 ms.
                LaunchedEffect(Unit) {
                    if (embedded) return@LaunchedEffect
                    snapshotFlow { listState.layoutInfo.viewportSize.height > 0 }.first { it }
                    withFrameNanos { }
                    if (currentCardBounds == null && switchSnapshotDelivered[0] == 0) {
                        switchSnapshotDelivered[0] = 1
                        onScreenReady(androidx.compose.ui.geometry.Rect.Zero)
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface),
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surface,
                        contentColor = MiuixTheme.colorScheme.onSurface
                    )
                ) {
                    // 未归入任何文件夹的课表；跟着课表列表与文件夹变化重算
                    val rootNames = remember(scheduleNames, folders) {
                        val grouped = folders.flatMap { it.schedules }.toSet()
                        scheduleNames.filter { it !in grouped }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .overScrollVertical()
                            .scrollEndHaptic(
                                hapticFeedbackType = HapticFeedbackType.TextHandleMove
                            )
                            // 内嵌没有手机式折叠顶栏，collapsibleTopInset/nestedScroll 会干扰列表滚动
                            .then(
                                if (embedded) Modifier
                                else Modifier
                                    .collapsibleTopInset(scrollBehavior)
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                            ),
                        contentPadding = PaddingValues(
                            start = tabletHorizontalPadding,
                            end = tabletHorizontalPadding,
                            top = contentTopPadding
                                ?: (paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight - 70.dp),
                            bottom = 60.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 根目录：文件夹（可原地展开）+ 未归档课表；文件夹内课表紧跟其文件夹卡片
                        folders.forEachIndexed { folderIndex, folder ->
                            // 文件夹卡片与它的课表放同一个 item：展开/收回时下方卡片跟着一起被推走。
                            // key 带序号：即便数据层出现重复 id，也不至于 LazyColumn 闪退。
                            item(key = "folder:${folder.id}#$folderIndex") {
                                Column(modifier = Modifier.animateItem(fadeInSpec = null)) {
                                    FolderItem(folder = folder, itemModifier = Modifier)
                                    CardExpand(
                                        visible = folder.id in expandedFolderIds,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            val children =
                                                folder.schedules.filter { it in scheduleNames }
                                            CardExpand(visible = children.isEmpty()) {
                                                Text(
                                                    text = SwitchStr.folderEmpty(),
                                                    fontSize = 14.sp,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.padding(
                                                        start = 16.dp,
                                                        bottom = 4.dp
                                                    )
                                                )
                                            }
                                            CardExpand(visible = children.isNotEmpty()) {
                                                // 间距要落在内层 Column 上：外层 spacedBy 只作用于
                                                // CardExpand 整体，管不到它内部的多张子卡
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    children.forEach { childName ->
                                                        ScheduleItem(
                                                            scheduleName = childName,
                                                            indent = true,
                                                            itemModifier = Modifier
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 未归档课表：按添加时间顺序展示，不区分「当前/其他」，选中不重排
                        items(
                            count = rootNames.size,
                            key = { "root:${rootNames[it]}" }
                        ) { index ->
                            ScheduleItem(
                                scheduleName = rootNames[index],
                                indent = false,
                                itemModifier = Modifier.animateItem(fadeInSpec = null)
                            )
                        }
                    }
                }
            }

            OverlayDialog(
                title = SwitchStr.shareScheduleTitle(),
                summary = SwitchStr.shareConfirmSummary(shareConfirmScheduleName.orEmpty()),
                show = showShareConfirmDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = {
                    showShareConfirmDialog = false
                    shareConfirmScheduleName = null
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showShareConfirmDialog = false
                            shareConfirmScheduleName = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = SwitchStr.confirmShare(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            val name = shareConfirmScheduleName
                            showShareConfirmDialog = false
                            shareConfirmScheduleName = null
                            if (name != null) {
                                performShareSchedule(name)
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OverlayDialog(
                title = SwitchStr.newSchedule(),
                show = showAddDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = {
                    showAddDialog = false
                    newScheduleName = ""
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NativeMiuixTextField(
                        value = newScheduleName,
                        onValueChange = { newScheduleName = it },
                        label = SwitchStr.scheduleNameLabel(),
                        modifier = Modifier.fillMaxWidth(),
                        requestFocus = showAddDialog
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showAddDialog = false
                                newScheduleName = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = CommonStr.ok(),
                            enabled = newScheduleName.isNotBlank(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (scheduleNames.contains(newScheduleName)) {
                                    showToast(SwitchStr.duplicateSchedule())
                                    return@TextButton
                                }
                                val name = newScheduleName
                                showAddDialog = false
                                newScheduleName = ""
                                com.haooz.chedule.ui.utils.FeatureLog.switchSchedule("add", name)
                                scheduleNames = repository.addSchedule(name)
                                // 手动新建课表：自动新建默认专属时间配置（跟随课表名）
                                repository.createDefaultTimeConfigForSchedule(name)
                                currentScheduleId = name
                                repository.switchToSchedule(name)
                                onScheduleChanged()
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            OverlayDialog(
                title = SwitchStr.editSchedule(),
                show = showEditDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = {
                    showEditDialog = false
                    editScheduleName = ""
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NativeMiuixTextField(
                        value = editScheduleName,
                        onValueChange = { editScheduleName = it },
                        label = SwitchStr.scheduleNameLabel(),
                        modifier = Modifier.fillMaxWidth(),
                        requestFocus = showEditDialog
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showEditDialog = false
                                editScheduleName = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = CommonStr.ok(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (editScheduleName.isBlank()) {
                                    showToast(SwitchStr.nameRequired())
                                    return@TextButton
                                }
                                if (editScheduleName == editingScheduleName) {
                                    showEditDialog = false
                                    editScheduleName = ""
                                    return@TextButton
                                }
                                if (scheduleNames.contains(editScheduleName)) {
                                    showToast(SwitchStr.duplicateSchedule())
                                    return@TextButton
                                }
                                val oldName = editingScheduleName
                                val newName = editScheduleName
                                com.haooz.chedule.ui.utils.FeatureLog.switchSchedule("rename", "$oldName->$newName")
                                val wasChecked = oldName in selectedSchedules
                                showEditDialog = false
                                editScheduleName = ""
                                scheduleNames = repository.renameSchedule(oldName, newName)
                                folders = repository.getScheduleFolders()
                                selectedSchedules = if (wasChecked) setOf(newName) else selectedSchedules - oldName
                                if (currentScheduleId == oldName) {
                                    currentScheduleId = newName
                                    repository.switchToSchedule(newName)
                                }
                                onScheduleChanged()
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val deleteFolderCount = selectedFolders.size
            val deleteScheduleCount = selectedSchedules.size
            val deleteDialogTitle = when {
                deleteFolderCount > 0 && deleteScheduleCount > 0 -> SwitchStr.deleteSchedulesAndFolders()
                deleteFolderCount > 0 -> SwitchStr.disbandFolder()
                else -> SwitchStr.deleteSchedule()
            }
            val deleteDialogText = when {
                deleteFolderCount > 0 && deleteScheduleCount > 0 ->
                    SwitchStr.deleteMixedConfirm(deleteScheduleCount, deleteFolderCount)
                deleteFolderCount > 0 ->
                    SwitchStr.disbandOnlyConfirm()
                else -> SwitchStr.deleteSchedulesConfirm()
            }
            OverlayDialog(
                title = deleteDialogTitle,
                show = showDeleteDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showDeleteDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = deleteDialogText,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        // 父 Column 居中摆位：选定项变化会换整段文案，按中心收放才不会先贴左边
                        textAlign = TextAlign.Center,
                    )
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
                                showDeleteDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = if (deleteScheduleCount > 0) CommonStr.delete() else SwitchStr.disband(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val selectedNames = selectedSchedules.toList()
                                val folderIds = selectedFolders.toList()
                                com.haooz.chedule.ui.utils.FeatureLog.switchSchedule(
                                    "delete", selectedNames.joinToString()
                                )
                                showDeleteDialog = false
                                exitEditMode()
                                // 先解散文件夹：课表回到根目录再删，避免删完留下空壳文件夹
                                if (folderIds.isNotEmpty()) {
                                    folderIds.forEach { id -> folders = repository.disbandScheduleFolder(id) }
                                }
                                scope.launch {
                                    selectedNames.forEach { name ->
                                        deletingScheduleName = name
                                        delay(300.milliseconds)
                                        scheduleNames = repository.deleteSchedule(name)
                                        if (currentScheduleId == name && scheduleNames.isNotEmpty()) {
                                            currentScheduleId = scheduleNames.first()
                                            repository.switchToSchedule(currentScheduleId)
                                        }
                                    }
                                    deletingScheduleName = null
                                    folders = repository.getScheduleFolders()
                                    if (selectedNames.isNotEmpty()) onScheduleChanged()
                                }
                            },
                            textColor = ComposeColor(0xFFF44336),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            OverlayDialog(
                title = SwitchStr.newFolder(),
                show = showAddFolderDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = {
                    showAddFolderDialog = false
                    newFolderName = ""
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NativeMiuixTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = SwitchStr.folderNameLabel(),
                        modifier = Modifier.fillMaxWidth(),
                        requestFocus = showAddFolderDialog
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showAddFolderDialog = false
                                newFolderName = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = CommonStr.ok(),
                            enabled = newFolderName.isNotBlank(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (folders.any { it.name == newFolderName }) {
                                    showToast(SwitchStr.duplicateFolder())
                                    return@TextButton
                                }
                                val name = newFolderName
                                com.haooz.chedule.ui.utils.FeatureLog.switchSchedule("add_folder", name)
                                val created = repository.addScheduleFolder(name)
                                folders = created
                                // 刚建的文件夹直接展开，方便接着往里搬课表
                                created.lastOrNull()?.id
                                    ?.let { expandedFolderIds = expandedFolderIds + it }
                                showAddFolderDialog = false
                                newFolderName = ""
                                exitEditMode()
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            OverlayDialog(
                title = SwitchStr.renameFolder(),
                show = showEditFolderDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = {
                    showEditFolderDialog = false
                    editFolderName = ""
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NativeMiuixTextField(
                        value = editFolderName,
                        onValueChange = { editFolderName = it },
                        label = SwitchStr.folderNameLabel(),
                        modifier = Modifier.fillMaxWidth(),
                        requestFocus = showEditFolderDialog
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showEditFolderDialog = false
                                editFolderName = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = CommonStr.ok(),
                            enabled = editFolderName.isNotBlank(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (editFolderName == (folders.find { it.id == editingFolderId }?.name ?: "")) {
                                    showEditFolderDialog = false
                                    editFolderName = ""
                                    return@TextButton
                                }
                                if (folders.any { it.name == editFolderName && it.id != editingFolderId }) {
                                    showToast(SwitchStr.duplicateFolder())
                                    return@TextButton
                                }
                                com.haooz.chedule.ui.utils.FeatureLog.switchSchedule(
                                    "rename_folder", editFolderName
                                )
                                folders = repository.renameScheduleFolder(editingFolderId, editFolderName)
                                showEditFolderDialog = false
                                editFolderName = ""
                                exitEditMode()
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            OverlayDialog(
                title = SwitchStr.moveTo(),
                summary = SwitchStr.moveSelectedCount(selectedSchedules.size),
                show = showMoveDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showMoveDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoveTargetRow(
                        title = SwitchStr.allSchedules(),
                        subtitle = SwitchStr.moveToRoot(),
                        icon = MiuixIcons.MoveFile,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showMoveDialog = false
                            folders = repository.moveSchedulesToFolder(selectedSchedules.toList(), null)
                            exitEditMode()
                        }
                    )
                    folders.forEach { folder ->
                        MoveTargetRow(
                            title = folder.name.orDefaultName(SwitchStr.defaultFolderName),
                            subtitle = SwitchStr.scheduleCountN(folder.schedules.size),
                            icon = MiuixIcons.Folder,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showMoveDialog = false
                                folders = repository.moveSchedulesToFolder(
                                    selectedSchedules.toList(),
                                    folder.id
                                )
                                expandedFolderIds = expandedFolderIds + folder.id
                                exitEditMode()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 切换页的课表卡片：编辑模式下变复选框行；indent 表示它挂在文件夹里 */
@Composable
private fun ScheduleCardRow(
    scheduleName: String,
    summary: String,
    isCurrent: Boolean,
    isEditMode: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDeleting: Boolean,
    indent: Boolean,
    itemModifier: Modifier,
    onBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 入场/退场统一走 CardRevealSpec；当前课表不参与入场动画，与进场形变锚点对齐
    val cardReveal = rememberCardRevealState(visible = !isDeleting, animateEnter = !isCurrent)
    Card(
        modifier = itemModifier
            .fillMaxWidth()
            .then(if (indent) Modifier.padding(start = 14.dp) else Modifier)
            .cardRevealLayer(cardReveal)
            .onGloballyPositioned { coordinates ->
                val position =
                    coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                val size = coordinates.size
                onBoundsChanged(
                    androidx.compose.ui.geometry.Rect(
                        left = position.x,
                        top = position.y,
                        right = position.x + size.width,
                        bottom = position.y + size.height
                    )
                )
            },
        insideMargin = PaddingValues(0.dp)
    ) {
        if (isEditMode) {
            CheckboxPreference(
                title = scheduleName,
                summary = summary,
                checked = checked,
                onCheckedChange = onCheckedChange,
                checkboxLocation = CheckboxLocation.End
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                showIndication = false,
                insideMargin = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 16.dp
                ),
                pressFeedbackType = PressFeedbackType.None,
                onClick = onClick,
                onLongPress = onLongClick
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = scheduleName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        if (summary.isNotEmpty()) {
                            Text(
                                text = summary,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                    if (isCurrent) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Check,
                            contentDescription = SwitchStr.currentSchedule(),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 文件夹卡片：点击原地展开/折叠里面的课表 */
@Composable
private fun FolderCardRow(
    folderName: String,
    scheduleCount: Int,
    expanded: Boolean,
    isEditMode: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    itemModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cardReveal = rememberCardRevealState()
    // 与展开/收回的高度动画同一条曲线同一时长，箭头和列表移动才是连贯的
    val expandRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else -90f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "folderExpandRotation"
    )
    Card(
        modifier = itemModifier
            .fillMaxWidth()
            .cardRevealLayer(cardReveal),
        insideMargin = PaddingValues(0.dp)
    ) {
        if (isEditMode) {
            // 编辑模式下仍保留左侧文件夹图标，右侧放复选框（点整行即可切换选中）
            Card(
                modifier = Modifier.fillMaxWidth(),
                showIndication = true,
                insideMargin = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 14.dp
                ),
                pressFeedbackType = PressFeedbackType.None,
                onClick = { onCheckedChange(!checked) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = MiuixIcons.FolderFill,
                        contentDescription = SwitchStr.folder(),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = SwitchStr.scheduleCountN(scheduleCount),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Checkbox(
                        state = if (checked) ToggleableState.On else ToggleableState.Off,
                        onClick = { onCheckedChange(!checked) }
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                showIndication = true,
                insideMargin = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 14.dp
                ),
                pressFeedbackType = PressFeedbackType.None,
                onClick = onClick,
                onLongPress = onLongClick
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expanded) MiuixIcons.FolderFill else MiuixIcons.Folder,
                        contentDescription = SwitchStr.folder(),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = SwitchStr.scheduleCountN(scheduleCount),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = expandRotation },
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
        }
    }
}

/** 「移动到」弹窗里的目标行 */
@Composable
private fun MoveTargetRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val cancelButtonColors = ButtonDefaults.textButtonColors()
    val rowColor = remember(cancelButtonColors) {
        cancelButtonColors.color.copy(alpha = cancelButtonColors.alpha)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        showIndication = true,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        colors = CardDefaults.defaultColors(color = rowColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

/**
 * 底栏滑动选择的单项登记：松手时要执行的动作 + 是否可用。
 * 不登记位置区间 —— 条目都是 `weight(1f)` 等宽格，命中由手势侧按格下标换算。
 */
private class BottomBarItemEntry {
    var enabled = true
    var action: (() -> Unit)? = null
}

/**
 * 底栏滑动选择状态，与 `LiquidGlassDropdownMenu` 的跟手滑选同款单一状态设计：
 * 按下即命中 → 逐帧改命中 → 松手执行；点按 = 滑动距离为 0 的滑选，共用同一条路径。
 * 因此条目上不能再挂 detectTapGestures/clickable：两个手势状态互相抢事件会跳状态
 * （无障碍改走 semantics onClick）。
 */
private class BottomBarDragSelectState {
    /** 按格下标登记；map 而非 list：下标由调用方显式给，不依赖组合顺序 */
    private val entries = mutableMapOf<Int, BottomBarItemEntry>()

    /** 当前命中项；null = 松手不执行（禁用项 / 落在条目之外的 padding 区） */
    var selected by mutableStateOf<BottomBarItemEntry?>(null)
        private set

    /** 参与换算的条目数（= 内容区格数） */
    val itemCount: Int get() = entries.size

    fun register(index: Int, entry: BottomBarItemEntry) {
        entries[index] = entry
    }

    fun unregister(index: Int, entry: BottomBarItemEntry) {
        if (entries[index] === entry) entries.remove(index)
        if (selected === entry) selected = null
    }

    fun hitTest(index: Int): BottomBarItemEntry? {
        val hit = entries[index]?.takeIf { it.enabled }
        selected = hit
        return hit
    }

    fun clear() {
        selected = null
    }
}

@Composable
private fun RowScope.BottomBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    state: BottomBarDragSelectState,
    index: Int,
    onClick: () -> Unit
) {
    val entry = remember { BottomBarItemEntry() }
    DisposableEffect(state, entry, index) {
        state.register(index, entry)
        onDispose { state.unregister(index, entry) }
    }
    // 每次重组刷新，拿不到最新 onClick / enabled 的话会执行旧闭包
    SideEffect {
        entry.action = onClick
        entry.enabled = enabled
    }
    // 命中即按压：点按与滑选是同一条路径，pressAlpha 的弹簧负责进出过渡
    val isPressed = state.selected === entry
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(150),
        label = "pressAlpha"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(150),
        label = "pressScale"
    )
    val pressColor = if (isAppDarkTheme()) ComposeColor.White.copy(alpha = 0.1f * pressAlpha)
    else ComposeColor.Black.copy(alpha = 0.06f * pressAlpha)
    Column(
        modifier = Modifier
            // 条目自己不挂手势（见 BottomBarDragSelectState）：点按与滑选统一由胶囊上的
            // 总手势处理，这里只补无障碍语义。semantics onClick 不产生手势、不抢事件。
            .semantics {
                role = Role.Button
                onSemanticsClick(label = label) {
                    if (enabled) onClick()
                    true
                }
            }
            .drawWithContent {
                if (pressAlpha > 0f) {
                    val extraWidth = 3.dp.toPx()
                    val overlayWidth = size.width + extraWidth * 2
                    val overlayHeight = size.height
                    val capsule = ContinuousCapsule()
                    val outline = capsule.createOutline(
                        Size(overlayWidth, overlayHeight),
                        layoutDirection,
                        this
                    )
                    val path = androidx.compose.ui.graphics.Path().apply {
                        when (outline) {
                            is androidx.compose.ui.graphics.Outline.Generic -> addPath(outline.path)
                            is androidx.compose.ui.graphics.Outline.Rounded -> addRoundRect(outline.roundRect)
                            is androidx.compose.ui.graphics.Outline.Rectangle -> addRect(outline.rect)
                        }
                    }
                    val centerX = size.width / 2f + extraWidth
                    val centerY = size.height / 2f
                    path.transform(androidx.compose.ui.graphics.Matrix().apply {
                        translate(-extraWidth, 0f)
                        translate(centerX, centerY)
                        scale(pressScale, pressScale, 0f)
                        translate(-centerX, -centerY)
                    })
                    drawPath(
                        path = path,
                        color = pressColor
                    )
                }
                drawContent()
            }
            .clip(ContinuousCapsule())
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurface.copy(
                alpha = 0.38f
            ),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurface.copy(
                alpha = 0.38f
            ),
            textAlign = TextAlign.Center,
        )
    }
}
