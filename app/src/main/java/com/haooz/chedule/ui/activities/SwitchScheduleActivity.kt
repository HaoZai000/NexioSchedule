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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
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
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
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

@SuppressLint("ConfigurationScreenWidthHeight", "MutableCollectionMutableState")
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
    val scrollBehavior = rememberSharedScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val screenGraphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var contentRootX by remember { mutableFloatStateOf(0f) }
    var contentRootY by remember { mutableFloatStateOf(0f) }

    var scheduleNames by remember {
        mutableStateOf(
            initialScheduleNames ?: repository.getScheduleNames()
        )
    }
    LaunchedEffect(Unit) {
        scheduleNames = repository.getScheduleNames()
    }
    var currentScheduleId by remember {
        mutableStateOf(
            initialCurrentScheduleId ?: repository.getCurrentScheduleId()
        )
    }
    LaunchedEffect(Unit) {
        currentScheduleId = repository.getCurrentScheduleId()
    }
    var scheduleSummaries by remember {
        mutableStateOf(
            initialScheduleSummaries?.toMutableMap() ?: mutableMapOf()
        )
    }
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
    var deletingScheduleName by remember { mutableStateOf<String?>(null) }
    var firstCardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    val switchToCurrentSchedule = {
        val firstSchedule = scheduleNames.firstOrNull() ?: ""
        currentScheduleId = firstSchedule
        repository.switchToSchedule(firstSchedule)
        onScheduleChanged()
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            val bitmap = try {
                screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
            } catch (_: Exception) {
                null
            }
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
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

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
                var topBarBlurAlpha by remember { mutableFloatStateOf(0f) }
                ProgressiveBlurTopBar(
                    backdrop = liquidGlassBackdrop,
                    blurAlpha = topBarBlurAlpha,
                ) {
                    CollapsibleTopAppBar(
                        title = displayTitle,
                        largeTitle = displayTitle,
                        modifier = Modifier,
                        scrollBehavior = scrollBehavior,
                        contentPadding = {},
                        onAlphaChanged = { bd, _ -> topBarBlurAlpha = bd },
                        startAction = { backdropAlpha, shadowAlpha ->
                            LiquidTopBarButton(
                                onClick = {
                                    if (isEditMode) {
                                        isEditMode = false
                                        editMode = ""
                                        checkboxStates.clear()
                                    } else {
                                        switchToCurrentSchedule()
                                    }
                                },
                                backdrop = liquidGlassBackdrop,
                                icon = if (isEditMode) MiuixIcons.Normal.Close else MiuixIcons.ChevronBackward,
                                contentDescription = if (isEditMode) "关闭" else "返回",
                                iconSize = if (isEditMode) 24.dp else 25.dp,
                                iconOffset = if (isEditMode) DpOffset.Zero else DpOffset(x = (-2).dp, y = 0.dp),
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
                                    onClick = { isEditMode = true },
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Normal.Edit,
                                    contentDescription = "编辑",
                                    iconSize = 26.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            }
                        } else null,
                    )
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
                AnimatedVisibility(
                    visible = navBarVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(150, easing = CubicBezierEasing(0.6f, 0f, 0.3f, 1f))
                    ),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180))
                ) {
                    val checkedCount = checkboxStates.values.count { it }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.63f)
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
                                    onDrawSurface = {
                                        val containerColor = if (isDark) ComposeColor(0xFF181818).copy(alpha = 0.84f) else ComposeColor.White.copy(alpha = 0.76f)
                                        drawRect(containerColor)
                                    }
                                )
                                .edgeLight(shape = ContinuousCapsule(), edgeLight = rememberDefaultEdgeLight())
                                .padding(horizontal = 7.dp, vertical = 3.5.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomBarItem(
                                icon = MiuixIcons.Forward,
                                label = "分享",
                                enabled = checkedCount == 1,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                }
                            )
                            BottomBarItem(
                                icon = MiuixIcons.Edit,
                                label = "编辑",
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
                        screenGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawContent()
                    }
                    .layerBackdrop(backdrop)
                    .liquidGlassLayerBackdrop(liquidGlassBackdrop)
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemScrollOffset }
                        .distinctUntilChanged()
                        .collect { offset ->
                            listScrollY = offset
                        }
                }
                val density = androidx.compose.ui.platform.LocalDensity.current
                val topBarHeightDp = with(density) {
                    scrollBehavior.currentHeightPx.toDp()
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .scrollEndHaptic(
                                hapticFeedbackType = HapticFeedbackType.TextHandleMove
                            )
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = tabletHorizontalPadding,
                            end = tabletHorizontalPadding,
                            top = paddingValues.calculateTopPadding() + topBarHeightDp - 82.dp,
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
                                        val bitmap =
                                            screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                        val adjustedBounds = androidx.compose.ui.geometry.Rect(
                                            left = (bounds.left - contentRootX) / pageScale,
                                            top = (bounds.top - contentRootY) / pageScale,
                                            right = (bounds.right - contentRootX) / pageScale,
                                            bottom = (bounds.bottom - contentRootY) / pageScale
                                        )
                                        onScreenReady(bitmap, adjustedBounds)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                            Card(
                                cornerRadius = 20.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        val position =
                                            coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
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
                                val firstSummary = remember(
                                    firstSchedule,
                                    scheduleSummaries
                                ) {
                                    scheduleSummaries[firstSchedule]
                                        ?: repository.getScheduleSummary(firstSchedule)
                                }
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
                                        insideMargin = PaddingValues(
                                            horizontal = 16.dp,
                                            vertical = 16.dp
                                        ),
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
                            items(
                                scheduleNames.size - 1,
                                key = { scheduleNames[it + 1] }) { index ->
                                if (index == 0) {
                                    SmallTitle(
                                        text = "其他课表",
                                        modifier = Modifier.offset(x = (-16).dp)
                                    )
                                }
                                val scheduleName = scheduleNames[index + 1]
                                val summary = remember(
                                    scheduleName,
                                    scheduleSummaries
                                ) {
                                    scheduleSummaries[scheduleName]
                                        ?: repository.getScheduleSummary(scheduleName)
                                }
                                var cardBounds by remember {
                                    mutableStateOf<androidx.compose.ui.geometry.Rect?>(
                                        null
                                    )
                                }
                                val isDeleting = deletingScheduleName == scheduleName
                                val cardScale = remember { Animatable(0.8f) }
                                val cardAlpha = remember { Animatable(0f) }
                                LaunchedEffect(Unit) {
                                    launch { cardScale.animateTo(1f, animationSpec = tween(400)) }
                                    launch { cardAlpha.animateTo(1f, animationSpec = tween(400)) }
                                }
                                LaunchedEffect(isDeleting) {
                                    if (isDeleting) {
                                        launch {
                                            cardScale.animateTo(
                                                0.8f,
                                                animationSpec = tween(300)
                                            )
                                        }
                                        launch {
                                            cardAlpha.animateTo(
                                                0f,
                                                animationSpec = tween(300)
                                            )
                                        }
                                    }
                                }
                                Card(
                                    cornerRadius = 20.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .graphicsLayer {
                                            scaleX = cardScale.value
                                            scaleY = cardScale.value
                                            alpha = cardAlpha.value
                                        }
                                        .onGloballyPositioned { coordinates ->
                                            val position =
                                                coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
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
                                            insideMargin = PaddingValues(
                                                horizontal = 16.dp,
                                                vertical = 16.dp
                                            ),
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
                                                            val fullBitmap =
                                                                screenGraphicsLayer.toImageBitmap()
                                                                    .asAndroidBitmap()
                                                            val x =
                                                                (bounds.left - contentRootX).toInt()
                                                                    .coerceIn(
                                                                        0,
                                                                        fullBitmap.width - 1
                                                                    )
                                                            val y =
                                                                (bounds.top - contentRootY).toInt()
                                                                    .coerceIn(
                                                                        0,
                                                                        fullBitmap.height - 1
                                                                    )
                                                            val w = bounds.width.toInt()
                                                                .coerceIn(1, fullBitmap.width - x)
                                                            val h = bounds.height.toInt()
                                                                .coerceIn(1, fullBitmap.height - y)
                                                            val cardBitmap =
                                                                android.graphics.Bitmap.createBitmap(
                                                                    fullBitmap,
                                                                    x,
                                                                    y,
                                                                    w,
                                                                    h
                                                                )
                                                            onCardSnapshot(
                                                                fullBitmap,
                                                                cardBitmap,
                                                                bounds
                                                            )
                                                        } catch (_: Exception) {
                                                        }
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
                                scheduleNames = repository.addSchedule(name)
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
                                val wasChecked = checkboxStates[oldName] == true
                                showEditDialog = false
                                editScheduleName = ""
                                scheduleNames = repository.renameSchedule(oldName, newName)
                                checkboxStates.remove(oldName)
                                if (wasChecked) {
                                    checkboxStates[newName] = true
                                }
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

            OverlayDialog(
                title = "删除课表",
                show = showDeleteDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
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
                                val selectedNames = checkboxStates.filter { it.value }.keys.toList()
                                showDeleteDialog = false
                                isEditMode = false
                                editMode = ""
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
                                    checkboxStates.clear()
                                    onScheduleChanged()
                                }
                            },
                            textColor = ComposeColor(0xFFF44336),
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                        color = ComposeColor.Black.copy(alpha = 0.08f * pressAlpha)
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
            tint = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}