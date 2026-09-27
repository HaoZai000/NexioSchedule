/** 切换课程表页面 */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.ScheduleFolder
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.collapsibleTopInset
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.buildShareScheduleMap
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.ui.utils.performScheduleShare
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.delay
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.state.ToggleableState
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class SwitchScheduleActivity : ComponentActivity() {
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
            SwitchScheduleScreen(
                onBack = {
                    setResult(RESULT_OK)
                    finish()
                },
                onScheduleChanged = {
                    setResult(RESULT_OK)
                }
            )
        }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight", "MutableCollectionMutableState")
@Composable
fun SwitchScheduleScreen(
    onBack: (android.graphics.Bitmap?) -> Unit = { _ -> },
    onScheduleChanged: () -> Unit = {},
    onCardClick: (androidx.compose.ui.geometry.Rect) -> Unit = { _ -> onBack(null) },
    onCardSnapshot: (screenBitmap: android.graphics.Bitmap, cardBitmap: android.graphics.Bitmap, bounds: androidx.compose.ui.geometry.Rect) -> Unit = { _, _, _ -> },
    onCurrentCardBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onScreenReady: (screenBitmap: android.graphics.Bitmap?, cardBounds: androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> },
    onContentOffset: (x: Float, y: Float) -> Unit = { _, _ -> },
    pageScale: Float = 1f,
    initialScheduleNames: List<String>? = null,
    initialCurrentScheduleId: String? = null,
    initialScheduleSummaries: Map<String, String>? = null,
    /** 平板左栏内嵌：无卡片形变、无返回关闭，标题由外层分栏绘制 */
    embedded: Boolean = false,
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val repository = remember { CourseRepository(context) }
    val scrollBehavior = rememberSharedScrollBehavior()
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val screenGraphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    // 页面快照「按需录制」：record() 会把整页（液态玻璃顶栏 + LazyColumn + backdrop 录制）再离屏
    // 完整画一遍，常驻每帧录制等于把每帧绘制成本翻倍，进/退动画期间必掉帧。
    // 只在真正要 toImageBitmap() 前录一帧，其余帧完全不录（与 MainActivity 主内容快照同一套做法）。
    val lastRecordedSnapshotToken = remember { intArrayOf(0) }
    var snapshotToken by remember { mutableIntStateOf(0) }
    val capturePageBitmap: suspend () -> android.graphics.Bitmap? = {
        snapshotToken++
        // 等一帧让 draw 阶段完成录制，再等一帧确保该帧已提交
        withFrameNanos { }
        withFrameNanos { }
        try {
            screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
        } catch (_: Exception) {
            null
        }
    }
    var contentRootX by remember { mutableFloatStateOf(0f) }
    var contentRootY by remember { mutableFloatStateOf(0f) }

    var scheduleNames by remember {
        mutableStateOf(
            initialScheduleNames ?: repository.getScheduleNames()
        )
    }
    LaunchedEffect(Unit) {
        // initial 值来自 ScheduleViewModel 的实时 StateFlow，已经是最新；再读一次磁盘只会
        // 让首帧之后立刻多一次重组，正好压在进场动画的头几帧上。独立 Activity 启动时
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
        com.haooz.chedule.ui.utils.CrashLogHelper.trace(
            "切换课表", "screen_compose",
            "names=${scheduleNames.size} current=$currentScheduleId"
        )
        if (initialCurrentScheduleId == null) {
            currentScheduleId = repository.getCurrentScheduleId()
        }
    }
    var scheduleSummaries by remember {
        mutableStateOf(
            initialScheduleSummaries?.toMutableMap() ?: mutableMapOf()
        )
    }
    LaunchedEffect(initialScheduleSummaries) {
        scheduleSummaries = initialScheduleSummaries?.toMutableMap() ?: mutableMapOf()
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
        performScheduleShare(
            context = context,
            scope = scope,
            scheduleName = scheduleName,
            onSharingChanged = { isSharingSchedule = it }
        )
    }

    // 返回/点当前课表：保持当前选中，不重排列表，只关闭页面
    val dismissKeepCurrent = {
        com.haooz.chedule.ui.utils.CrashLogHelper.trace("切换课表", "select_current", currentScheduleId)
        if (!embedded) {
            scope.launch {
                onBack(capturePageBitmap())
            }
        }
    }

    /** 选中另一张课表：切换后带着卡片截图做形变退出（根目录与文件夹内共用） */
    fun selectSchedule(scheduleName: String, bounds: androidx.compose.ui.geometry.Rect?) {
        if (scheduleName == currentScheduleId) {
            dismissKeepCurrent()
            return
        }
        com.haooz.chedule.ui.utils.CrashLogHelper.trace("切换课表", "select_other", scheduleName)
        // 不重排列表，只切换当前课表
        repository.switchToSchedule(scheduleName)
        currentScheduleId = scheduleName
        onScheduleChanged()
        // 内嵌分栏：切换后留在当前页，不做卡片形变
        if (embedded) return
        if (bounds != null) {
            scope.launch {
                val fullBitmap = capturePageBitmap()
                if (fullBitmap != null) {
                    val x = (bounds.left - contentRootX).toInt()
                        .coerceIn(0, fullBitmap.width - 1)
                    val y = (bounds.top - contentRootY).toInt()
                        .coerceIn(0, fullBitmap.height - 1)
                    val w = bounds.width.toInt().coerceIn(1, fullBitmap.width - x)
                    val h = bounds.height.toInt().coerceIn(1, fullBitmap.height - y)
                    val cardBitmap = android.graphics.Bitmap.createBitmap(fullBitmap, x, y, w, h)
                    onCardSnapshot(fullBitmap, cardBitmap, bounds)
                }
                onCardClick(bounds)
            }
        } else {
            onBack(null)
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
                    onCurrentCardBounds(rect)
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
            folderName = folder.name,
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
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
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

    BackHandler(enabled = isEditMode) {
        exitEditMode()
    }

    BackHandler(enabled = !isEditMode && !embedded) {
        dismissKeepCurrent()
    }

    var displayTitle by remember { mutableStateOf("全部课表") }
    LaunchedEffect(isEditMode) {
        displayTitle = if (isEditMode) {
            "编辑课表"
        } else {
            "全部课表"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // 内嵌分栏：标题/操作由外层顶栏承载，这里不画系统式顶栏
                if (!embedded) {
                ProgressiveBlurTopBar(
                    backdrop = liquidGlassBackdrop,
                ) {
                    CollapsibleTopAppBar(
                        title = displayTitle,
                        largeTitle = displayTitle,
                        modifier = Modifier,
                        scrollBehavior = scrollBehavior,
                        contentPadding = {},
                        startAction = { backdropAlpha, shadowAlpha ->
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
                                contentDescription = if (isEditMode) "关闭" else "返回",
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
                        endAction = if (!isEditMode) { backdropAlpha, shadowAlpha ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LiquidTopBarButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showAddDialog = true
                                    },
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Add,
                                    contentDescription = "添加",
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
                                    contentDescription = "新建文件夹",
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
                LaunchedEffect(isEditMode) {
                    if (isEditMode) {
                        navBarVisible = true
                    } else {
                        navBarVisible = false
                    }
                }
                
                // 胶囊本体
                AnimatedVisibility(
                    visible = navBarVisible,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
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
                    val containerColor =
                        if (!isAppDarkTheme()) Color(0xFFFFFFFF).copy(0.6f)
                        else Color(0xFF121212).copy(0.54f)


                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val r = bottombarBlur.value
                                renderEffect = if (r > 0.01f) {
                                    val px = r * density.density
                                    android.graphics.RenderEffect.createBlurEffect(
                                        px, px, android.graphics.Shader.TileMode.CLAMP
                                    ).asComposeRenderEffect()
                                } else null
                            }
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                scaleX = 0.6f + 0.4f * appear
                                scaleY = 0.6f + 0.4f * appear
                                alpha = appear
                                clip = false
                            }
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 外面多套的动画 Box：整条胶囊作为它的内容被整体包住
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(56.dp)
                                .drawBackdrop(
                                    backdrop = liquidGlassBackdrop,
                                    shape = { ContinuousCapsule() },
                                    effects = {
                                        vibrancy()
                                        blur(4f.dp.toPx())
                                        lens(10f.dp.toPx(), 32f.dp.toPx())
                                    },
                                    highlight = null,
                                    onDrawSurface = { drawRect(containerColor) }
                                )
                                .edgeLight(
                                    shape = ContinuousCapsule(),
                                    edgeLight = rememberDefaultEdgeLight()
                                )
                                .padding(horizontal = 7.dp, vertical = 3.5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BottomBarItem(
                                    icon = MiuixIcons.Forward,
                                    label = "分享",
                                    enabled = selectedFolders.isEmpty() && selectedSchedules.size == 1 && !isSharingSchedule,
                                    onClick = {
                                        com.haooz.chedule.ui.utils.FeatureLog.switchSchedule("share_dialog")
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        val selected = selectedSchedules.firstOrNull()
                                        if (selected != null && !isSharingSchedule) {
                                            if (buildShareScheduleMap(repository, selected) == null) {
                                                Toast.makeText(
                                                    context,
                                                    "「$selected」课表为空，无法分享",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                shareConfirmScheduleName = selected
                                                showShareConfirmDialog = true
                                            }
                                        }
                                    }
                                )
                                BottomBarItem(
                                    icon = MiuixIcons.Edit,
                                    label = "编辑",
                                    enabled = checkedCount == 1,
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        if (checkedCount != 1) return@BottomBarItem
                                        val folderId = selectedFolders.firstOrNull()
                                        if (folderId != null) {
                                            val folder = folders.find { it.id == folderId }
                                            if (folder != null) {
                                                editingFolderId = folder.id
                                                editFolderName = folder.name
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
                                    label = "移动",
                                    enabled = selectedSchedules.isNotEmpty() && selectedFolders.isEmpty(),
                                    onClick = {
                                        com.haooz.chedule.ui.utils.FeatureLog.switchSchedule("move_dialog")
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        showMoveDialog = true
                                    }
                                )
                                BottomBarItem(
                                    icon = MiuixIcons.Delete,
                                    label = "删除",
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
                        // 只在被请求时录制一帧（见 capturePageBitmap），避免每帧重复渲染整页
                        if (lastRecordedSnapshotToken[0] != snapshotToken) {
                            lastRecordedSnapshotToken[0] = snapshotToken
                            screenGraphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                        }
                        drawContent()
                    }
                    .liquidGlassLayerBackdrop(liquidGlassBackdrop)
            ) {
                // 注意：这里不要再 collect firstVisibleItemScrollOffset 写 state ——
                // 那会让整页在滚动时每像素重组一次（listScrollY 之前根本没被读取，纯属白烧）。
                val listState = rememberLazyListState()
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
                // 进场形变锚点：当前课表卡片（不是列表第一项）
                LaunchedEffect(currentCardBounds) {
                    if (embedded) return@LaunchedEffect
                    val bounds = currentCardBounds
                    if (bounds != null) {
                        val bitmap = capturePageBitmap()
                        // 截图失败也要回调：否则 switchCapturingSnapshot 永远为 true，
                        // 页面会一直停在 alpha=0 的黑屏上
                        val adjustedBounds = androidx.compose.ui.geometry.Rect(
                            left = (bounds.left - contentRootX) / pageScale,
                            top = (bounds.top - contentRootY) / pageScale,
                            right = (bounds.right - contentRootX) / pageScale,
                            bottom = (bounds.bottom - contentRootY) / pageScale
                        )
                        onScreenReady(bitmap, adjustedBounds)
                    }
                }
                // 兜底：当前课表被收在文件夹里、或首屏没布局到它时也要回调一次，
                // 否则主页 switchCapturingSnapshot 一直是 true，整个页面停在 alpha=0
                LaunchedEffect(Unit) {
                    if (embedded) return@LaunchedEffect
                    delay(700.milliseconds)
                    if (currentCardBounds == null) {
                        onScreenReady(null, androidx.compose.ui.geometry.Rect.Zero)
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
                            // 文件夹卡片与它的课表放同一个 item：高度逐帧变化，
                            // 展开/收回时下方卡片跟着一起被推走，而不是先消失再出现
                            // key 带序号：即便数据层出现重复 id，也不至于 LazyColumn 闪退
                            item(key = "folder:${folder.id}#$folderIndex") {
                                Column(modifier = Modifier.animateItem()) {
                                    FolderItem(folder = folder, itemModifier = Modifier)
                                    AnimatedVisibility(
                                        visible = folder.id in expandedFolderIds,
                                        enter = expandVertically(
                                            // 锚在底部：展开时内容跟着底边向下滑出，
                                            // 收起时一起向上滑回文件夹卡片下面，而不是从底部被裁掉
                                            expandFrom = Alignment.Bottom,
                                            animationSpec = tween(
                                                durationMillis = 300,
                                                easing = FastOutSlowInEasing
                                            )
                                        ) + fadeIn(
                                            animationSpec = tween(
                                                durationMillis = 300,
                                                easing = FastOutSlowInEasing
                                            )
                                        ),
                                        exit = shrinkVertically(
                                            shrinkTowards = Alignment.Bottom,
                                            animationSpec = tween(
                                                durationMillis = 300,
                                                easing = FastOutSlowInEasing
                                            )
                                        ) + fadeOut(
                                            animationSpec = tween(
                                                durationMillis = 300,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            val children =
                                                folder.schedules.filter { it in scheduleNames }
                                            if (children.isEmpty()) {
                                                Text(
                                                    text = "文件夹内暂无课表",
                                                    fontSize = 14.sp,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.padding(
                                                        start = 16.dp,
                                                        bottom = 4.dp
                                                    )
                                                )
                                            } else {
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
                        // 未归档课表：按添加时间顺序展示，不区分「当前/其他」，选中不重排
                        items(
                            count = rootNames.size,
                            key = { "root:${rootNames[it]}" }
                        ) { index ->
                            ScheduleItem(
                                scheduleName = rootNames[index],
                                indent = false,
                                itemModifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            OverlayDialog(
                title = "分享课表",
                summary = "将课表「${shareConfirmScheduleName.orEmpty()}」上传生成分享口令？\n口令 30 分钟内有效",
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
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showShareConfirmDialog = false
                            shareConfirmScheduleName = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确认分享",
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
                title = "新建课表",
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
                        label = "课表名称",
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
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showAddDialog = false
                                newScheduleName = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            enabled = newScheduleName.isNotBlank(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (scheduleNames.contains(newScheduleName)) {
                                    Toast.makeText(
                                        context,
                                        "已存在同名课表",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                title = "编辑课表",
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
                        label = "课表名称",
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
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showEditDialog = false
                                editScheduleName = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (editScheduleName.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "请输入课表名称",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@TextButton
                                }
                                if (editScheduleName == editingScheduleName) {
                                    showEditDialog = false
                                    editScheduleName = ""
                                    return@TextButton
                                }
                                if (scheduleNames.contains(editScheduleName)) {
                                    Toast.makeText(
                                        context,
                                        "已存在同名课表",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                deleteFolderCount > 0 && deleteScheduleCount > 0 -> "删除课表与文件夹"
                deleteFolderCount > 0 -> "解散文件夹"
                else -> "删除课表"
            }
            val deleteDialogText = when {
                deleteFolderCount > 0 && deleteScheduleCount > 0 ->
                    "选中的 $deleteScheduleCount 个课表将被删除；选中的 $deleteFolderCount 个文件夹将解散，其中课表移回「全部课表」。"
                deleteFolderCount > 0 ->
                    "选中的文件夹将被解散，其中课表移回「全部课表」，课表不会被删除。"
                else -> "确定要删除选中的课表吗？"
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
                        color = MiuixTheme.colorScheme.onSurface
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
                                showDeleteDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = if (deleteScheduleCount > 0) "删除" else "解散",
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
                title = "新建文件夹",
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
                        label = "文件夹名称",
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
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showAddFolderDialog = false
                                newFolderName = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            enabled = newFolderName.isNotBlank(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (folders.any { it.name == newFolderName }) {
                                    Toast.makeText(
                                        context,
                                        "已存在同名文件夹",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                title = "重命名文件夹",
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
                        label = "文件夹名称",
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
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showEditFolderDialog = false
                                editFolderName = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            enabled = editFolderName.isNotBlank(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (editFolderName == (folders.find { it.id == editingFolderId }?.name ?: "")) {
                                    showEditFolderDialog = false
                                    editFolderName = ""
                                    return@TextButton
                                }
                                if (folders.any { it.name == editFolderName && it.id != editingFolderId }) {
                                    Toast.makeText(
                                        context,
                                        "已存在同名文件夹",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                title = "移动到",
                summary = "已选中 ${selectedSchedules.size} 个课表",
                show = showMoveDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showMoveDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoveTargetRow(
                        title = "全部课表",
                        subtitle = "移出文件夹，回到根目录",
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
                            title = folder.name,
                            subtitle = "${folder.schedules.size} 个课表",
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
    // 当前课表不参与入场动画，与进场形变锚点对齐
    val cardScale = remember { Animatable(if (isCurrent) 1f else 0.8f) }
    val cardAlpha = remember { Animatable(if (isCurrent) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!isCurrent) {
            launch { cardScale.animateTo(1f, animationSpec = tween(400)) }
            launch { cardAlpha.animateTo(1f, animationSpec = tween(400)) }
        }
    }
    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            launch { cardScale.animateTo(0.8f, animationSpec = tween(300)) }
            launch { cardAlpha.animateTo(0f, animationSpec = tween(300)) }
        }
    }
    Card(
        cornerRadius = 20.dp,
        modifier = itemModifier
            .fillMaxWidth()
            .then(if (indent) Modifier.padding(start = 14.dp) else Modifier)
            .graphicsLayer {
                scaleX = cardScale.value
                scaleY = cardScale.value
                alpha = cardAlpha.value
            }
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
                cornerRadius = 20.dp,
                showIndication = true,
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
                            contentDescription = "当前课表",
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
    val cardScale = remember { Animatable(0.8f) }
    val cardAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { cardScale.animateTo(1f, animationSpec = tween(400)) }
        launch { cardAlpha.animateTo(1f, animationSpec = tween(400)) }
    }
    // 与展开/收回的高度动画同一条曲线同一时长，箭头和列表移动才是连贯的
    val expandRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else -90f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "folderExpandRotation"
    )
    Card(
        cornerRadius = 20.dp,
        modifier = itemModifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale.value
                scaleY = cardScale.value
                alpha = cardAlpha.value
            },
        insideMargin = PaddingValues(0.dp)
    ) {
        if (isEditMode) {
            // 编辑模式下仍保留左侧文件夹图标，右侧放复选框（点整行即可切换选中）
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
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
                        contentDescription = "文件夹",
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
                            text = "$scheduleCount 个课表",
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
                cornerRadius = 20.dp,
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
                        contentDescription = "文件夹",
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
                            text = "$scheduleCount 个课表",
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        showIndication = true,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
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

@Composable
private fun RowScope.BottomBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
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
    val pressColor = if (isAppDarkTheme()) ComposeColor.White.copy(alpha = 0.11f * pressAlpha)
    else ComposeColor.Black.copy(alpha = 0.07f * pressAlpha)
    Column(
        modifier = Modifier
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() }
                    )
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
            )
        )
    }
}