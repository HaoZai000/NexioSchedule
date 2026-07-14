/** 切换课程表页面 */
package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
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
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
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

@Composable
fun SwitchScheduleScreen(
    onBack: (android.graphics.Bitmap?) -> Unit = { _ -> },
    onScheduleChanged: () -> Unit = {},
    onCardClick: (androidx.compose.ui.geometry.Rect) -> Unit = { _ -> onBack(null) },
    onCardSnapshot: (screenBitmap: android.graphics.Bitmap, cardBitmap: android.graphics.Bitmap, bounds: androidx.compose.ui.geometry.Rect) -> Unit = { _, _, _ -> },
    onCurrentCardBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onScreenReady: (screenBitmap: android.graphics.Bitmap, cardBounds: androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> },
    onContentOffset: (x: Float, y: Float) -> Unit = { _, _ -> },
    pageScale: Float = 1f,
    initialScheduleNames: List<String>? = null,
    initialCurrentScheduleId: String? = null,
    initialScheduleSummaries: Map<String, String>? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { CourseRepository(context) }
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val screenGraphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var contentRootX by remember { mutableFloatStateOf(0f) }
    var contentRootY by remember { mutableFloatStateOf(0f) }

    var scheduleNames by remember { mutableStateOf(initialScheduleNames ?: repository.getScheduleNames()) }
    LaunchedEffect(Unit) {
        scheduleNames = repository.getScheduleNames()
    }
    var currentScheduleId by remember { mutableStateOf(initialCurrentScheduleId ?: repository.getCurrentScheduleId()) }
    var scheduleSummaries by remember { mutableStateOf(initialScheduleSummaries?.toMutableMap() ?: mutableMapOf()) }
    LaunchedEffect(initialScheduleSummaries) {
        scheduleSummaries = initialScheduleSummaries?.toMutableMap() ?: mutableMapOf()
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var newScheduleName by remember { mutableStateOf("") }
    var isEditMode by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingScheduleName by remember { mutableStateOf("") }
    var editScheduleName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var firstCardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    val switchToCurrentSchedule = {
        val firstSchedule = scheduleNames.firstOrNull() ?: ""
        currentScheduleId = firstSchedule
        repository.switchToSchedule(firstSchedule)
        onScheduleChanged()
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            val bitmap = try { screenGraphicsLayer.toImageBitmap().asAndroidBitmap() } catch (_: Exception) { null }
            onBack(bitmap)
        }
    }
    val focusRequester = remember { FocusRequester() }
    val editFocusRequester = remember { FocusRequester() }
    val checkboxStates = remember { mutableStateMapOf<String, Boolean>() }
    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor) // 确保捕获到不透明背景
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    val liquidGlassBackdrop = if (appStyle == "liquidglass") {
        com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    } else null
    val isLiquidGlass = appStyle == "liquidglass"
    val colors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = 0.7f), BlurBlendMode.SrcOver)
            else BlendColorEntry(ComposeColor.White.copy(alpha = 0.7f), BlurBlendMode.SrcOver)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    val blurAlpha by remember { derivedStateOf { if (listScrollY < 50) 0f else ((listScrollY - 50) / 30f).coerceIn(0f, 0.7f) } }
    val topBarColorProgress by remember { derivedStateOf { ((listScrollY - 50) / 30f).coerceIn(0f, 1f) } }
    val surface = MiuixTheme.colorScheme.surface
    val darkTarget = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
    val topBarColor by remember {
        derivedStateOf {
            if (listScrollY < 50) surface
            else lerp(surface, darkTarget, topBarColorProgress)
        }
    }
    val topAppBarColors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(topBarColor, BlurBlendMode.SrcOver)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    LaunchedEffect(showAddDialog) {
        if (showAddDialog) {
            kotlinx.coroutines.delay(180.milliseconds)
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(showEditDialog) {
        if (showEditDialog) {
            kotlinx.coroutines.delay(180.milliseconds)
            editFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isEditMode) {
        if (isEditMode && scheduleNames.isNotEmpty()) {
            checkboxStates[currentScheduleId] = true
        }
    }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
        editMode = ""
        checkboxStates.clear()
    }

    BackHandler(enabled = !isEditMode) {
        switchToCurrentSchedule()
    }

    Scaffold(
        topBar = {
            if (isLiquidGlass && liquidGlassBackdrop != null) {
                val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val allTitleAlpha = remember { Animatable(1f) }
                val editTitleAlpha = remember { Animatable(0f) }
                var displayTitle by remember { mutableStateOf("全部课表") }
                LaunchedEffect(isEditMode) {
                    if (isEditMode) {
                        displayTitle = "编辑课表"
                        editTitleAlpha.snapTo(1f)
                    } else {
                        displayTitle = "全部课表"
                        allTitleAlpha.snapTo(1f)
                    }
                }
                ProgressiveBlurTopBar(
                    backdrop = liquidGlassBackdrop,
                ) {
                    SmallTopAppBar(
                        color = Color.Transparent,
                        title = displayTitle,
                        modifier = Modifier.zIndex(1f),
                        navigationIcon = {}
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(2f)
                            .offset(y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LiquidTopBarButton(
                            onClick = {
                                if (isEditMode) {
                                    isEditMode = false
                                    editMode = ""
                                    checkboxStates.clear()
                                } else { switchToCurrentSchedule() }
                            },
                            backdrop = liquidGlassBackdrop,
                            icon = if (isEditMode) MiuixIcons.Close else MiuixIcons.Medium.ChevronBackward,
                            contentDescription = if (isEditMode) "关闭" else "返回",
                            modifier = Modifier.offset(x = 20.dp),
                            iconSize = if (isEditMode) 21.dp else 22.dp,
                            iconOffset = if (isEditMode) DpOffset.Zero else DpOffset(x = (-2).dp, y = 0.dp),
                            useBackdropShadow = true
                        )
                        if (!isEditMode) {
                            LiquidTopBarButton(
                                onClick = { isEditMode = true },
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.offset(x = (-20).dp),
                                iconSize = 23.dp,
                                useBackdropShadow = true
                            )
                        }
                    }
                }
            } else {
                val allTitleAlpha = remember { Animatable(1f) }
                val editTitleAlpha = remember { Animatable(0f) }
                var displayTitle by remember { mutableStateOf("全部课表") }
                LaunchedEffect(isEditMode) {
                    if (isEditMode) {
                        allTitleAlpha.animateTo(0f, animationSpec = tween(80))
                        kotlinx.coroutines.delay(16.milliseconds)
                        displayTitle = "编辑课表"
                        editTitleAlpha.animateTo(1f, animationSpec = tween(170))
                    } else {
                        editTitleAlpha.animateTo(0f, animationSpec = tween(80))
                        kotlinx.coroutines.delay(16.milliseconds)
                        displayTitle = "全部课表"
                        allTitleAlpha.animateTo(1f, animationSpec = tween(170))
                    }
                }
                val surfaceColor = MiuixTheme.colorScheme.onSurface
                TopAppBar(
                    modifier = if (blurAlpha > 0f) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            colors = topAppBarColors
                        )
                    } else {
                        Modifier
                    },
                    color = topBarColor,
                    title = displayTitle,
                    largeTitle = displayTitle,
                    largeTitleColor = surfaceColor.copy(alpha = maxOf(allTitleAlpha.value, editTitleAlpha.value)),
                    titleColor = surfaceColor.copy(alpha = maxOf(allTitleAlpha.value, editTitleAlpha.value)),
                    scrollBehavior = scrollBehavior,
                    navigationIconPadding = 20.dp,
                    navigationIcon = {
                        val backAlpha = remember { Animatable(1f) }
                        val closeAlpha = remember { Animatable(0f) }
                        LaunchedEffect(isEditMode) {
                            if (isEditMode) {
                                backAlpha.animateTo(0f, animationSpec = tween(100))
                                closeAlpha.animateTo(1f, animationSpec = tween(300))
                            } else {
                                closeAlpha.animateTo(0f, animationSpec = tween(100))
                                backAlpha.animateTo(1f, animationSpec = tween(300))
                            }
                        }
                        IconButton(onClick = {
                            if (isEditMode) {
                                isEditMode = false
                                editMode = ""
                                checkboxStates.clear()
                            } else { switchToCurrentSchedule() }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                modifier = Modifier
                                    .size(28.dp)
                                    .graphicsLayer { alpha = backAlpha.value }
                            )
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = "关闭",
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer { alpha = closeAlpha.value }
                            )
                        }
                    },
                    actions = {
                        val editAlpha by animateFloatAsState(
                            targetValue = if (isEditMode) 0f else 1f,
                            animationSpec = tween(150),
                            label = "editAlpha"
                        )
                        IconButton(
                            onClick = { if (!isEditMode) isEditMode = true },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .graphicsLayer { alpha = editAlpha }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            var navBarVisible by remember { mutableStateOf(false) }
            LaunchedEffect(isEditMode) {
                if (isEditMode) {
                    kotlinx.coroutines.delay(100.milliseconds)
                    navBarVisible = true
                } else {
                    navBarVisible = false
                }
            }
            AnimatedVisibility(
                visible = navBarVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(150, easing = CubicBezierEasing(0.6f, 0f, 0.3f, 1f))
                ),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180))
            ) {
                val checkedCount = checkboxStates.values.count { it }
                NavigationBar(
                    modifier = Modifier
                        .height(74.dp)
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            colors = colors
                        ),
                    mode = NavigationBarDisplayMode.IconAndText,
                    color = ComposeColor.Transparent,
                    showDivider = false
                ) {
                    NavigationBarItem(
                        selected = checkedCount == 1,
                        enabled = checkedCount == 1,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        },
                        icon = MiuixIcons.Forward,
                        label = "分享"
                    )
                    NavigationBarItem(
                        selected = checkedCount == 1,
                        enabled = checkedCount == 1,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            if (checkedCount == 1) {
                                val selected = checkboxStates.entries.find { it.value }?.key
                                if (selected != null) {
                                    editingScheduleName = selected
                                    editScheduleName = selected
                                    showEditDialog = true
                                }
                            }
                        },
                        icon = MiuixIcons.Edit,
                        label = "编辑"
                    )
                    NavigationBarItem(
                        selected = checkedCount >= 1,
                        enabled = checkedCount >= 1,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            if (checkedCount >= 1) {
                                showDeleteDialog = true
                            }
                        },
                        icon = MiuixIcons.Delete,
                        label = "删除"
                    )
                }
            }
        },
        floatingActionButton = {
            var fabAlpha by remember { mutableFloatStateOf(1f) }
            var fabScaleTarget by remember { mutableFloatStateOf(1f) }
            LaunchedEffect(isEditMode) {
                if (isEditMode) {
                    fabAlpha = 0f
                    fabScaleTarget = 0.8f
                } else {
                    kotlinx.coroutines.delay(180.milliseconds)
                    fabAlpha = 1f
                    fabScaleTarget = 1f
                }
            }
            val animatedFabAlpha by animateFloatAsState(
                targetValue = fabAlpha,
                animationSpec = if (isEditMode) tween(100) else tween(120),
                label = "fabAlpha"
            )
            val fabScale by animateFloatAsState(
                targetValue = fabScaleTarget,
                animationSpec = if (isEditMode) tween(100) else tween(120),
                label = "fabScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp, bottom = 20.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                FloatingActionButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showAddDialog = true
                    },
                    shadowElevation = 0.dp,
                    modifier = Modifier.graphicsLayer {
                        alpha = animatedFabAlpha
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        tint = ComposeColor.White,
                        contentDescription = "添加"
                    )
                }
            }
        }
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
                    screenGraphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawContent()
                }
                .layerBackdrop(backdrop)
                .then(
                    if (liquidGlassBackdrop != null) Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                    else Modifier
                )
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .distinctUntilChanged()
                    .collect { offset ->
                        listScrollY = offset
                    }
            }
            Card(
                modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.onSurface)
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .then(
                        if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
                    ),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (isLiquidGlass) paddingValues.calculateTopPadding() + (-16).dp else paddingValues.calculateTopPadding(),
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SmallTitle(
                        text = "当前课表",
                        modifier = Modifier.offset(x = (-16).dp)
                    )
                    LaunchedEffect(firstCardBounds) {
                        val bounds = firstCardBounds
                        if (bounds != null) {
                            withFrameNanos { }
                            withFrameNanos { }
                            try {
                                val bitmap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                val adjustedBounds = androidx.compose.ui.geometry.Rect(
                                    left = (bounds.left - contentRootX) / pageScale,
                                    top = (bounds.top - contentRootY) / pageScale,
                                    right = (bounds.right - contentRootX) / pageScale,
                                    bottom = (bounds.bottom - contentRootY) / pageScale
                                )
                                onScreenReady(bitmap, adjustedBounds)
                            } catch (_: Exception) {}
                        }
                    }
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                                val size = coordinates.size
                                firstCardBounds = androidx.compose.ui.geometry.Rect(
                                    left = position.x,
                                    top = position.y,
                                    right = position.x + size.width,
                                    bottom = position.y + size.height
                                )
                                onCurrentCardBounds(firstCardBounds!!)
                            },
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        val firstSchedule = scheduleNames.firstOrNull() ?: ""
                        val firstSummary = remember(firstSchedule, scheduleSummaries) { scheduleSummaries[firstSchedule] ?: repository.getScheduleSummary(firstSchedule) }
                        if (isEditMode) {
                            CheckboxPreference(
                                title = firstSchedule,
                                summary = firstSummary,
                                checked = checkboxStates[firstSchedule] ?: false,
                                onCheckedChange = { isChecked ->
                                    checkboxStates[firstSchedule] = isChecked
                                },
                                checkboxLocation = CheckboxLocation.End
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 20.dp,
                                showIndication = true,
                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                pressFeedbackType = PressFeedbackType.None,
                                onClick = { switchToCurrentSchedule() }
                            ) {
                                Text(
                                    text = firstSchedule,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                if (firstSummary.isNotEmpty()) {
                                    Text(
                                        text = firstSummary,
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
                if (scheduleNames.size > 1) {
                    items(scheduleNames.size - 1) { index ->
                        if (index == 0) {
                            SmallTitle(
                                text = "其他课表",
                                modifier = Modifier.offset(x = (-16).dp)
                            )
                        }
                        val scheduleName = scheduleNames[index + 1]
                        val summary = remember(scheduleName, scheduleSummaries) { scheduleSummaries[scheduleName] ?: repository.getScheduleSummary(scheduleName) }
                        var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                                    val size = coordinates.size
                                    cardBounds = androidx.compose.ui.geometry.Rect(
                                        left = position.x,
                                        top = position.y,
                                        right = position.x + size.width,
                                        bottom = position.y + size.height
                                    )
                                },
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            if (isEditMode) {
                                CheckboxPreference(
                                    title = scheduleName,
                                    summary = summary,
                                    checked = checkboxStates[scheduleName] ?: false,
                                    onCheckedChange = { isChecked ->
                                        checkboxStates[scheduleName] = isChecked
                                    },
                                    checkboxLocation = CheckboxLocation.End
                                )
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 20.dp,
                                    showIndication = true,
                                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                    pressFeedbackType = PressFeedbackType.None,
                                    onClick = {
                                        val names = scheduleNames.toMutableList()
                                        names.remove(scheduleName)
                                        names.add(0, scheduleName)
                                        repository.saveScheduleNames(names)
                                        repository.switchToSchedule(scheduleName)
                                        onScheduleChanged()
                                        val bounds = cardBounds
                                        if (bounds != null) {
                                            scope.launch {
                                                withFrameNanos { }
                                                withFrameNanos { }
                                                try {
                                                    val fullBitmap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                                    val x = (bounds.left - contentRootX).toInt().coerceIn(0, fullBitmap.width - 1)
                                                    val y = (bounds.top - contentRootY).toInt().coerceIn(0, fullBitmap.height - 1)
                                                    val w = bounds.width.toInt().coerceIn(1, fullBitmap.width - x)
                                                    val h = bounds.height.toInt().coerceIn(1, fullBitmap.height - y)
                                                    val cardBitmap = android.graphics.Bitmap.createBitmap(fullBitmap, x, y, w, h)
                                                    onCardSnapshot(fullBitmap, cardBitmap, bounds)
                                                } catch (_: Exception) {}
                                                onCardClick(bounds)
                                            }
                                        } else {
                                            onBack(null)
                                        }
                                    }
                                ) {
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
                            }
                        }
                    }
                }
            }
            }
        }

        OverlayDialog(
            title = "新建课表",
            show = showAddDialog,

            onDismissRequest = {
                showAddDialog = false
                newScheduleName = ""
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = newScheduleName,
                    onValueChange = { newScheduleName = it },
                    label = "课表名称",
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
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
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (newScheduleName.isNotBlank()) {
                                scheduleNames = repository.addSchedule(newScheduleName)
                                currentScheduleId = newScheduleName
                                repository.switchToSchedule(newScheduleName)
                                onScheduleChanged()
                                showAddDialog = false
                                newScheduleName = ""
                            }
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

            onDismissRequest = {
                showEditDialog = false
                editScheduleName = ""
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = editScheduleName,
                    onValueChange = { editScheduleName = it },
                    label = "课表名称",
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(editFocusRequester)
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
                            if (editScheduleName.isNotBlank() && editScheduleName != editingScheduleName) {
                                scheduleNames = repository.renameSchedule(editingScheduleName, editScheduleName)
                                if (currentScheduleId == editingScheduleName) {
                                    currentScheduleId = editScheduleName
                                    repository.switchToSchedule(editScheduleName)
                                }
                                onScheduleChanged()
                                showEditDialog = false
                                editScheduleName = ""
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OverlayDialog(
            title = "删除课表",
            show = showDeleteDialog,

            onDismissRequest = { showDeleteDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "确定要删除选中的课表吗？",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showDeleteDialog = false
                        },
                    ) {
                        Text("取消",fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            val selectedNames = checkboxStates.filter { it.value }.keys.toList()
                            selectedNames.forEach { name ->
                                scheduleNames = repository.deleteSchedule(name)
                                if (currentScheduleId == name && scheduleNames.isNotEmpty()) {
                                    currentScheduleId = scheduleNames.first()
                                    repository.switchToSchedule(currentScheduleId)
                                }
                            }
                            checkboxStates.clear()
                            isEditMode = false
                            editMode = ""
                            onScheduleChanged()
                            showDeleteDialog = false
                        },
                    ) {
                        Text("删除",fontSize = 17.sp, fontWeight = FontWeight.Medium,color = ComposeColor(0xFFF44336))
                    }
                }
            }
        }

    }
}