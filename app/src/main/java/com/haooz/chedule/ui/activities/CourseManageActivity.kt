/** 课程管理页面 */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.CourseEditScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.viewmodel.CourseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class CourseManageActivity : ComponentActivity() {
    // 小窗状态
    private var _isInFreeformWindow = mutableStateOf(false)
    val isInFreeformWindow: Boolean get() = _isInFreeformWindow.value

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        _isInFreeformWindow.value = isInMultiWindowMode
    }

    @SuppressLint("UseOfNonLambdaOffsetOverload")
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
        _isInFreeformWindow.value = isInMultiWindowMode
        setContent {
            CourseScheduleTheme {
                val backgroundColor = MiuixTheme.colorScheme.surface
                val backdrop = rememberLayerBackdrop {
                    drawRect(backgroundColor)
                    drawContent()
                }
                val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                val editLiquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                val shortcutMenuBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                val scrollBehavior = rememberSharedScrollBehavior()
                val courseViewModel: CourseViewModel = viewModel()

                // Edit screen state
                var showEditScreen by remember { mutableStateOf(false) }
                var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
                var cardLeft by remember { mutableFloatStateOf(0f) }
                var cardTop by remember { mutableFloatStateOf(0f) }
                var cardWidth by remember { mutableFloatStateOf(0f) }
                var cardHeight by remember { mutableFloatStateOf(0f) }
                var cardSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                var cardColor by remember { mutableStateOf(Color(0xFF4CAF50)) }
                var cardAlpha by remember { mutableFloatStateOf(0.15f) }
                var hiddenCourseIds by remember { mutableStateOf(setOf<String>()) }
                var shrinkingCourseIds by remember { mutableStateOf(setOf<String>()) }
                var pendingAutoExitDeleteIds by remember { mutableStateOf(setOf<String>()) }

                // Shortcut菜单状态
                var showShortcutMenu by remember { mutableStateOf(false) }
                var shortcutMenuPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                var shortcutMenuCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
                var shortcutMenuHeight by remember { mutableFloatStateOf(0f) }
                var shortcutMenuCardWidth by remember { mutableFloatStateOf(0f) }
                var shortcutMenuCardHeight by remember { mutableFloatStateOf(0f) }
                val shortcutMenuBlurRadius = remember { Animatable(0f) }
                val shortcutMenuCardScale = remember { Animatable(1f) }
                val shortcutMenuPageScale = remember { Animatable(1f) }
                var shortcutMenuSnapshot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                var editingCourse by remember { mutableStateOf<Course?>(null) }

                // Track course IDs created in this session for cleanup on exit
                var createdCourseIds by remember { mutableStateOf(setOf<String>()) }
                // Track course IDs that were opened for editing (to avoid deleting modified ones)
                var editedCourseIds by remember { mutableStateOf(setOf<String>()) }

                // Cleanup empty courses when activity finishes
                DisposableEffect(Unit) {
                    onDispose {
                        val coursesToDelete = createdCourseIds - editedCourseIds
                        coursesToDelete.forEach { courseId: String ->
                            courseViewModel.deleteCourse(courseId)
                        }
                    }
                }

                // Graphics layer for capturing screen content
                val screenGraphicsLayer = rememberGraphicsLayer()

                LaunchedEffect(showShortcutMenu) {
                    if (showShortcutMenu) {
                        launch { shortcutMenuBlurRadius.animateTo(10f, tween(280)) }
                        launch { shortcutMenuCardScale.animateTo(1.04f, tween(280)) }
                        launch { shortcutMenuPageScale.animateTo(0.98f, tween(280)) }
                    } else {
                        launch { shortcutMenuBlurRadius.animateTo(0f, tween(250)) }
                        launch { shortcutMenuCardScale.animateTo(1f, tween(250)) }
                        launch { shortcutMenuPageScale.animateTo(1f, tween(250)) }
                    }
                }

                // 返回键关闭菜单
                androidx.activity.compose.BackHandler(enabled = showShortcutMenu) {
                    showShortcutMenu = false
                }

                // Background scale animation (same as MainActivity)
                val backgroundScale = remember { Animatable(1f) }
                val managePageBlurRadius = remember { Animatable(0f) }
                val windowInfo = LocalWindowInfo.current
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current
                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = context as? CourseManageActivity

                // 小窗状态
                var isInFreeformWindow by remember { mutableStateOf(activity?.isInFreeformWindow ?: false) }
                LaunchedEffect(Unit) {
                    snapshotFlow { activity?.isInFreeformWindow }
                        .collect { value ->
                            isInFreeformWindow = value ?: false
                        }
                }

                // Dynamically get screen corner radius from window insets
                val screenCornerRadius = remember(isInFreeformWindow) {
                    if (isInFreeformWindow) {
                        20f * density.density  // 小窗默认圆角 20dp
                    } else {
                        try {
                            val windowManager = context.getSystemService(WINDOW_SERVICE) as android.view.WindowManager
                            val windowMetrics = windowManager.currentWindowMetrics
                            val insets = windowMetrics.windowInsets
                            @SuppressLint("WrongConstant")
                            insets.getRoundedCorner(0)?.radius?.toFloat() ?: 0f
                        } catch (_: Exception) {
                            0f
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Main content with blur and scale animation
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .blur(if (shortcutMenuBlurRadius.value > 0.01f) shortcutMenuBlurRadius.value.dp else managePageBlurRadius.value.dp)
                        .graphicsLayer {
                            scaleX = shortcutMenuPageScale.value
                            scaleY = shortcutMenuPageScale.value
                        }
                        .background(MiuixTheme.colorScheme.surface)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val scale = backgroundScale.value
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .drawWithContent {
                                    screenGraphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    // 缩放时保持屏幕圆角不变
                                    val scale = backgroundScale.value
                                    if (scale < 0.999f) {
                                        val path = Path().apply {
                                            addSquircleRect(
                                                width = size.width,
                                                height = size.height,
                                                cornerRadius = screenCornerRadius
                                            )
                                        }
                                        clipPath(path) {
                                            this@drawWithContent.drawContent()
                                        }
                                    } else {
                                        drawContent()
                                    }
                                }
                        ) {
                            Scaffold(
                                topBar = {
                                    var topBarBlurAlpha by remember { mutableFloatStateOf(0f) }
                                    ProgressiveBlurTopBar(
                                        backdrop = liquidGlassBackdrop,
                                        blurAlpha = topBarBlurAlpha,
                                    ) {
                                        CollapsibleTopAppBar(
                                            title = "课程管理",
                                            largeTitle = "课程管理",
                                            modifier = Modifier,
                                            scrollBehavior = scrollBehavior,
                                            contentPadding = {},
                                            onAlphaChanged = { bd, _ -> topBarBlurAlpha = bd },
                                            startAction = { backdropAlpha, shadowAlpha ->
                                                LiquidTopBarButton(
                                                    onClick = { finish() },
                                                    backdrop = liquidGlassBackdrop,
                                                    icon = MiuixIcons.ChevronBackward,
                                                    contentDescription = "返回",
                                                    iconSize = 25.dp,
                                                    iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                                    backdropAlpha = backdropAlpha,
                                                    shadowAlpha = shadowAlpha,
                                                )
                                            },
                                        )
                                    }
                                }
                            ) { _ ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .layerBackdrop(backdrop)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().then(
                                            Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                                        )
                                    ) {
                                        CourseManageScreen(
                                            scrollBehavior = scrollBehavior,
                                            hiddenCourseIds = hiddenCourseIds,
                                            shrinkingCourseIds = shrinkingCourseIds,
                                            onNewCourseCreated = { course ->
                                                courseViewModel.addCourse(course)
                                                createdCourseIds = createdCourseIds + course.id
                                            },
                                            onCourseUpdated = { oldName, course ->
                                                courseViewModel.updateCoursesByName(oldName, course)
                                            },
                                            onEditDismiss = {
                                                editingCourse = null
                                            },
                                            pendingEditCourse = editingCourse,
                                            onCourseLongPress = { courses, left, top, width, height ->
                                                shortcutMenuCourses = courses
                                                shortcutMenuPosition = androidx.compose.ui.geometry.Offset(left, top)
                                                shortcutMenuCardWidth = width
                                                shortcutMenuCardHeight = height
                                                coroutineScope.launch {
                                                    shortcutMenuSnapshot = screenGraphicsLayer.toImageBitmap()
                                                    showShortcutMenu = true
                                                }
                                            },
                                            onCourseClick = { courses, left, top, width, height, _, color, alpha ->
                                                coroutineScope.launch {
                                                    selectedCourses = courses
                                                    cardLeft = left
                                                    cardTop = top
                                                    cardWidth = width
                                                    cardHeight = height
                                                    cardColor = color
                                                    cardAlpha = alpha
                                                    hiddenCourseIds = courses.map { it.id }.toSet()
                                                    // Mark courses as edited when opened
                                                    editedCourseIds = editedCourseIds + courses.map { it.id }

                                                    // Capture full screen snapshot
                                                    val fullSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()

                                                    // Crop card area from full snapshot
                                                    cardSnapshot = try {
                                                        val x = left.toInt().coerceIn(0, fullSnapshot.width - 1)
                                                        val y = top.toInt().coerceIn(0, fullSnapshot.height - 1)
                                                        val w = width.toInt().coerceIn(1, fullSnapshot.width - x)
                                                        val h = height.toInt().coerceIn(1, fullSnapshot.height - y)
                                                        android.graphics.Bitmap.createBitmap(fullSnapshot, x, y, w, h)
                                                    } catch (_: Exception) {
                                                        null
                                                    }

                                                    showEditScreen = true
                                                    launch {
                                                        delay(12.milliseconds)
                                                        launch {
                                                            backgroundScale.animateTo(
                                                                targetValue = 0.92f,
                                                                animationSpec = tween(560,easing = OobeQuartOutEasing)
                                                            )
                                                        }
                                                        launch {
                                                            managePageBlurRadius.animateTo(
                                                                targetValue = 5f,
                                                                animationSpec = tween(560, easing = OobeQuartOutEasing)
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Edit screen (above blur layer)
                    if (showEditScreen) {
                        CourseEditScreen(
                            courses = selectedCourses,
                            cardLeft = cardLeft,
                            cardTop = cardTop,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            screenWidth = windowInfo.containerSize.width.toFloat(),
                            screenHeight = windowInfo.containerSize.height.toFloat(),
                            screenCornerRadius = screenCornerRadius,
                            cardSnapshot = cardSnapshot,
                            cardColor = cardColor,
                            cardAlpha = cardAlpha,
                            onBackStart = {
                                coroutineScope.launch {
                                    launch {
                                        backgroundScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(350, easing = OobeCubicOutEasing)
                                        )
                                    }
                                    launch {
                                        managePageBlurRadius.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(350, easing = OobeCubicOutEasing)
                                        )
                                    }
                                }
                            },
                            onBack = {
                                if (pendingAutoExitDeleteIds.isNotEmpty()) {
                                    showEditScreen = false
                                    cardSnapshot = null
                                    hiddenCourseIds = emptySet()
                                    shrinkingCourseIds = pendingAutoExitDeleteIds
                                    coroutineScope.launch {
                                        delay(300.milliseconds)
                                        pendingAutoExitDeleteIds.forEach { id ->
                                            courseViewModel.deleteCourse(id)
                                        }
                                        shrinkingCourseIds = emptySet()
                                        pendingAutoExitDeleteIds = emptySet()
                                    }
                                } else {
                                    showEditScreen = false
                                    cardSnapshot = null
                                    hiddenCourseIds = emptySet()
                                }
                            },
                            onCourseUpdated = { course ->
                                courseViewModel.updateCourse(course)
                                selectedCourses = selectedCourses.map { if (it.id == course.id) course else it }
                            },
                            onCourseAdded = { course ->
                                courseViewModel.addCourse(course)
                                selectedCourses = selectedCourses + course
                            },
                            onDeleteCourse = { courseId ->
                                if (selectedCourses.size > 1) {
                                    courseViewModel.deleteCourse(courseId)
                                } else {
                                    pendingAutoExitDeleteIds = setOf(courseId)
                                }
                                selectedCourses = selectedCourses.filter { it.id != courseId }
                            },
                            onColorChanged = { colorRes ->
                                cardColor = Color(colorRes)
                            },
                            getOccupiedWeeks = { dayOfWeek, startSection, endSection, excludeIds, startTime, endTime ->
                                courseViewModel.getOccupiedWeeks(
                                    dayOfWeek = dayOfWeek,
                                    startSection = startSection,
                                    endSection = endSection,
                                    excludeIds = excludeIds.toSet(),
                                    startTime = startTime,
                                    endTime = endTime
                                )
                            },
                            liquidGlassBackdrop = editLiquidGlassBackdrop
                        )
                    }

                    // Shortcut菜单遮罩层
                    if (showShortcutMenu || shortcutMenuBlurRadius.value > 0.01f) {
                        val snapshot = shortcutMenuSnapshot
                        val cardLeft = shortcutMenuPosition.x
                        val cardTop = shortcutMenuPosition.y
                        val cardW = shortcutMenuCardWidth
                        val cardH = shortcutMenuCardHeight

                        // 点击空白区域关闭菜单
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { showShortcutMenu = false }
                        )

                        // 卡片快照（清晰，在最顶层）
                        if (snapshot != null && cardW > 0f && cardH > 0f) {
                            Canvas(
                                modifier = Modifier
                                    .offset(
                                        x = with(density) { cardLeft.toDp() },
                                        y = with(density) { cardTop.toDp() }
                                    )
                                    .size(
                                        width = with(density) { cardW.toDp() },
                                        height = with(density) { cardH.toDp() }
                                    )
                                    .graphicsLayer {
                                        scaleX = shortcutMenuCardScale.value
                                        scaleY = shortcutMenuCardScale.value
                                    }
                            ) {
                                val cornerRadius = 16.dp.toPx()
                                val squirclePath = Path().apply {
                                    addSquircleRect(
                                        width = size.width,
                                        height = size.height,
                                        cornerRadius = cornerRadius
                                    )
                                }
                                clipPath(squirclePath) {
                                    drawImage(
                                        image = snapshot,
                                        srcOffset = IntOffset(cardLeft.toInt(), cardTop.toInt()),
                                        srcSize = IntSize(cardW.toInt(), cardH.toInt()),
                                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                    )
                                }
                            }
                        }
                    }

                    // Shortcut菜单
                    val shortcutMenuDensity = density
                    ShortcutMenu(
                        show = showShortcutMenu,
                        items = listOf(
                            ShortcutMenuItem(
                                icon = MiuixIcons.Edit,
                                label = "编辑",
                                onClick = {
                                    if (shortcutMenuCourses.isNotEmpty()) {
                                        val courseToEdit = shortcutMenuCourses.first()
                                        showShortcutMenu = false
                                        coroutineScope.launch {
                                            delay(260.milliseconds)
                                            editingCourse = courseToEdit
                                        }
                                    }
                                }
                            ),
                            ShortcutMenuItem(
                                icon = MiuixIcons.Delete,
                                label = "删除",
                                onClick = {
                                    val coursesToDelete = shortcutMenuCourses.toList()
                                    showShortcutMenu = false
                                    coroutineScope.launch {
                                        delay(260.milliseconds)
                                        coursesToDelete.forEach { course ->
                                            courseViewModel.deleteCourse(course.id)
                                        }
                                    }
                                }
                            )
                        ),
                        modifier = Modifier.offset(
                            x = with(shortcutMenuDensity) { shortcutMenuPosition.x.toDp() - 14.dp },
                            y = with(shortcutMenuDensity) { shortcutMenuPosition.y.toDp() - shortcutMenuHeight.toDp() + 4.dp }
                        ),
                        backdrop = shortcutMenuBackdrop,
                        onDismiss = { showShortcutMenu = false },
                        onMeasuredSize = { _, height ->
                            shortcutMenuHeight = height.toFloat()
                        }
                    )
                }
            }
        }
    }
}
