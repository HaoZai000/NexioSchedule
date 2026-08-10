/** 主页面 - 应用入口 Activity */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.embedding.SplitController
import com.haooz.chedule.data.Course
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.components.CollapsibleTopAppBar
import com.haooz.chedule.ui.components.CourseCard
import com.haooz.chedule.ui.components.LongPressCustomizeButton
import com.haooz.chedule.ui.components.ScheduleBottomBar
import com.haooz.chedule.ui.components.ScheduleTopBar
import com.haooz.chedule.ui.components.ShareImportDialog
import com.haooz.chedule.ui.components.ShortcutMenu
import com.haooz.chedule.ui.components.ShortcutMenuItem
import com.haooz.chedule.ui.components.UpdateDialog
import com.haooz.chedule.ui.components.rememberSharedScrollBehavior
import com.haooz.chedule.ui.effects.liquidglass.LiquidAddButton
import com.haooz.chedule.ui.effects.liquidglass.LiquidGlassDropdownMenu
import com.haooz.chedule.ui.effects.liquidglass.LiquidGlassDropdownMenuItem
import com.haooz.chedule.ui.effects.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.effects.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.screens.CourseDetailScreen
import com.haooz.chedule.ui.screens.CustomizeScheduleScreen
import com.haooz.chedule.ui.screens.MainScheduleScreen
import com.haooz.chedule.ui.screens.SettingsScreen
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TodayScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class MainActivity : ComponentActivity() {

    companion object {
        // 跨 Activity 重建的壁纸缓存，避免每次启动都重新解码
        @Volatile
        var cachedWallpaperBitmap: android.graphics.Bitmap? = null

        @Volatile
        var cachedCombinationIds: List<Long> = emptyList()

        @Volatile
        var cachedCurrentCombinationIndex: Int = 0

        @Volatile
        var cachedWallpaperOffset: Offset = Offset.Zero

        @Volatile
        var cachedWallpaperScale: Float = 1f

        @Volatile
        var cachedAppearance: com.haooz.chedule.data.AppearanceConfig =
            com.haooz.chedule.data.AppearanceConfig()
    }

    var shareIntentUri: android.net.Uri? = null
        private set
    var shareIntentAction: String? = null
        private set

    // 用 Compose State 跟踪 intent 变化
    var shareIntentVersion by mutableIntStateOf(0)
        private set

    var titleBarHeight by mutableStateOf(56.dp)

    // 小窗状态
    var isInFreeformWindow by mutableStateOf(false)
        private set

    var resumeCount by mutableIntStateOf(0)
        private set

    fun clearShareIntent() {
        shareIntentUri = null
        shareIntentAction = null
    }

    private fun updateFreeformWindowState() {
        isInFreeformWindow = isInMultiWindowMode
    }

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
        extractIntentData(intent)
        updateFreeformWindowState()
        handleReminderSettingsIntent(intent)
        CourseReminderHelper.startReminderService(this)

        // 初始化超级岛通知助手
        IslandNotificationHelper.init(this)

        // 同步预加载当前搭配壁纸，让首帧就有壁纸数据
        if (cachedWallpaperBitmap == null) {
            try {
                val repo = com.haooz.chedule.data.CourseRepository(this)
                repo.migrateToCombinationsIfNeeded()
                val ids = repo.getCombinationIds()
                val currentId = repo.getCurrentCombinationId()
                val idx = ids.indexOf(currentId).coerceAtLeast(0)
                cachedCombinationIds = ids
                cachedCurrentCombinationIndex = idx
                if (ids.isNotEmpty()) {
                    val currentIdValue = ids[idx]
                    cachedWallpaperBitmap = repo.loadCombinationWallpaper(currentIdValue)
                    cachedWallpaperOffset = Offset(
                        repo.getCombinationOffsetX(currentIdValue),
                        repo.getCombinationOffsetY(currentIdValue)
                    )
                    cachedWallpaperScale = repo.getCombinationScale(currentIdValue)
                    cachedAppearance = com.haooz.chedule.data.AppearanceConfig(
                        cardBlurRadius = repo.getCombinationCardBlur(currentIdValue),
                        cardAlpha = repo.getCombinationCardAlpha(currentIdValue),
                        cardHeight = repo.getCombinationCardHeight(currentIdValue),
                        cardCornerRadius = repo.getCombinationCardCornerRadius(currentIdValue),
                        wallpaperBrightness = repo.getCombinationWallpaperBrightness(currentIdValue),
                        showBreakDividers = repo.getCombinationShowBreakDividers(currentIdValue),
                        cardContentAlignment = repo.getCombinationCardContentAlignment(
                            currentIdValue
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }

        setContent {
            CourseScheduleTheme {
                CourseScheduleApp()
            }
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        isInFreeformWindow = isInMultiWindowMode
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        handleReminderSettingsIntent(intent)
        // 更新版本号触发 Compose 重组
        shareIntentVersion++
    }

    private fun handleReminderSettingsIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(
                CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS,
                false
            ) == true
        ) {
            intent.removeExtra(CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS)
            startActivity(android.content.Intent(this, CourseReminderActivity::class.java))
        }
    }

    @SuppressLint("NewApi")
    private fun extractIntentData(intent: android.content.Intent?) {
        when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> {
                shareIntentUri = intent.data
                shareIntentAction = android.content.Intent.ACTION_VIEW
            }

            android.content.Intent.ACTION_SEND -> {
                shareIntentUri = intent.getParcelableExtra(
                    android.content.Intent.EXTRA_STREAM,
                    android.net.Uri::class.java
                )
                shareIntentAction = android.content.Intent.ACTION_SEND
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight", "UseOfNonLambdaOffsetOverload")
@Composable
fun CourseScheduleApp() {
    val context = LocalContext.current


    val viewModel: CourseViewModel = viewModel()
    val scheduleViewModel: ScheduleViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val shiftViewModel: ShiftViewModel = viewModel()
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()
    var selectedTab by remember { mutableIntStateOf(if (defaultHomepage == "今日") 0 else 1) }
    var showShiftLoading by remember { mutableStateOf(false) }
    var isExitingShift by remember { mutableStateOf(false) }
    var shiftModeInitialized by remember { mutableStateOf(false) }
    var settingsScrollY by remember { mutableIntStateOf(0) }
    val settingsScrollBehavior = rememberSharedScrollBehavior()
    var todayScrollY by remember { mutableIntStateOf(0) }
    val todayScrollBehavior = rememberSharedScrollBehavior()

    // 初始化 SyncManager
    LaunchedEffect(Unit) {
        val syncManager = com.haooz.chedule.data.SyncManager.getInstance(context)
        val repository = com.haooz.chedule.data.CourseRepository(context)
        val webDavManager = com.haooz.chedule.data.WebDavManager(context)
        syncManager.start(repository, webDavManager)
        // 备份/恢复完成后刷新 ViewModel 内存缓存
        syncManager.onSyncCompleted = {
            viewModel.refreshEssentialData()
            viewModel.reloadCourses()
            settingsViewModel.refreshSettings()
        }
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    // 始终创建液态玻璃 backdrop，供长按卡片菜单等组件采样（不受 appStyle 限制）
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val blurColors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(
                ComposeColor.Black.copy(alpha = 0.7f),
                BlurBlendMode.Multiply
            )
            else BlendColorEntry(ComposeColor.White.copy(alpha = 0.8f), BlurBlendMode.Screen)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val totalSections = morningSections + afternoonSections + eveningSections
    val activity = LocalActivity.current as? MainActivity
    val resumeCount = activity?.resumeCount ?: 0
    // 从其他 Activity 返回时刷新设置（如教务导入应用了预设时间段）
    LaunchedEffect(resumeCount) {
        if (resumeCount > 0) {
            settingsViewModel.refreshSettings()
            viewModel.reloadCourses()
            scheduleViewModel.refreshScheduleList()
        }
    }
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val navBarStyle = if (isTablet) "rail" else "standard"
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = LocalDensity.current
    // 提前计算屏幕像素尺寸，供 picker 回调和 LaunchedEffect 使用
    val screenWPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHPx = with(density) { config.screenHeightDp.dp.toPx() }
    val railState = if (navBarStyle == "rail") rememberNavigationRailState() else null
    val railPaddingStart by animateDpAsState(
        targetValue = if (appStyle == "liquidglass" && navBarStyle == "rail") {
            0.dp
        } else if (railState != null && railState.isExpanded) {
            NavigationRailDefaults.ExpandedWidth
        } else if (navBarStyle == "rail") {
            NavigationRailDefaults.MinWidth
        } else {
            0.dp
        },
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "railPadding",
    )
    val isShiftMode by shiftViewModel.isShiftMode.collectAsState()

    var showCourseDetailPopup by remember { mutableStateOf(false) }

    var detailCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var detailCardLeft by remember { mutableFloatStateOf(0f) }
    var detailCardTop by remember { mutableFloatStateOf(0f) }
    var detailCardWidth by remember { mutableFloatStateOf(0f) }
    var detailCardHeight by remember { mutableFloatStateOf(0f) }
    var detailSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showDetail by remember { mutableStateOf(false) }

    var detailFromToday by remember { mutableStateOf(false) }
    var hiddenCourseIds by remember { mutableStateOf(setOf<String>()) }

    // 拖拽课程卡片状态
    var isDraggingCard by remember { mutableStateOf(false) }
    var draggingCourseIds by remember { mutableStateOf(setOf<String>()) }
    var draggedCardCourse by remember { mutableStateOf<Course?>(null) }
    var draggedCardPosition by remember { mutableStateOf(Offset.Zero) }
    var draggedCardSize by remember { mutableStateOf(Offset.Zero) }
    var draggedCardOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedCardBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
    var draggedWeek by remember { mutableIntStateOf(1) }
    // 网格几何信息：拖拽落点检测使用
    var gridGeometry by remember {
        mutableStateOf<com.haooz.chedule.ui.screens.ScheduleGridGeometry?>(
            null
        )
    }
    // 当前拖拽落点：(dayOfWeek, startSection)，null 表示无有效落点
    var pendingDropTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // 调课后需要淡入放大的课程ID集合
    var animateInCourseIds by remember { mutableStateOf(setOf<String>()) }
    // 调课冲突对话框状态：拖到有课位置时弹出
    var showRescheduleConflictDialog by remember { mutableStateOf(false) }
    var pendingConflictCourse by remember { mutableStateOf<Course?>(null) }
    // 浮层卡片是否仍在渲染（退出动画期间保持 true，动画结束才 false，此时原卡片 alpha 恢复 1）
    var floatingCardVisible by remember { mutableStateOf(false) }
    // 浮层缩放：入场 0.94→1.04，退场 1.04→1.0；退场结束才让原卡片显现
    val floatingScale = remember { Animatable(0.94f) }
    // 吸附动画：调课成功后浮层从当前位置动画移动到目标位置，同时缩小到 1f
    // 吸附期间 isSnapping=true，浮层使用 floatingOffsetAnim 替代 draggedCardOffset
    var isSnapping by remember { mutableStateOf(false) }
    val floatingOffsetX = remember { Animatable(0f) }
    val floatingOffsetY = remember { Animatable(0f) }
    // 快捷菜单状态
    var shortcutMenuCourse by remember { mutableStateOf<Course?>(null) }
    var shortcutMenuVisible by remember { mutableStateOf(false) }
    var shortcutMenuPosition by remember { mutableStateOf(Offset.Zero) }
    var shortcutMenuSize by remember { mutableStateOf(IntSize.Zero) }
    var shortcutMenuBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
    // 删除确认弹窗状态
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteConfirmCourse by remember { mutableStateOf<Course?>(null) }

    // 长按空白区域"自定义课表"按钮状态
    var showLongPressButton by remember { mutableStateOf(false) }
    var showLongPressOverlay by remember { mutableStateOf(false) }

    // 自定义课表页面状态
    var showCustomizePage by remember { mutableStateOf(false) }
    var customizeSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var snapshotCoverBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var appearance by remember { mutableStateOf(MainActivity.cachedAppearance) }
    var isCustomizeExiting by remember { mutableStateOf(false) }
    var customizeExitTargetScale by remember { mutableFloatStateOf(1f) }
    val customizeExitScale = remember { Animatable(1f) }
    val customizeExitAlpha = remember { Animatable(1f) }
    var isWindowCutoutActive by remember { mutableStateOf(false) }
    val wallpaperRepository = remember { com.haooz.chedule.data.CourseRepository(context) }
    // 多搭配支持
    var combinations by remember { mutableStateOf(listOf<com.haooz.chedule.data.Combination>()) }
    var currentCombinationIndex by remember { mutableIntStateOf(0) }
    var wallpaperBitmap by remember { mutableStateOf(MainActivity.cachedWallpaperBitmap) }
    var wallpaperOffset by remember { mutableStateOf(MainActivity.cachedWallpaperOffset) }
    var wallpaperScale by remember { mutableFloatStateOf(MainActivity.cachedWallpaperScale) }

    // 保存"已应用"的壁纸快照，用于开洞编辑取消时回退到当前查看的搭配
    var savedWallpaperBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var savedWallpaperOffset by remember { mutableStateOf(wallpaperOffset) }
    var savedWallpaperScale by remember { mutableFloatStateOf(wallpaperScale) }
    var savedAppearance by remember { mutableStateOf(com.haooz.chedule.data.AppearanceConfig()) }
    // 记录进入搭配页时已应用的原始搭配，用于退出（非应用）时还原（滑动切换不更新此项）
    var originalCombinationIndex by remember { mutableIntStateOf(0) }
    var originalWallpaperBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var originalWallpaperOffset by remember { mutableStateOf(wallpaperOffset) }
    var originalWallpaperScale by remember { mutableFloatStateOf(wallpaperScale) }
    var originalAppearance by remember { mutableStateOf(com.haooz.chedule.data.AppearanceConfig()) }
    var originalSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isApplyingCustomize by remember { mutableStateOf(false) }
    var isNewCombinationCreated by remember { mutableStateOf(false) }
    var newCombinationIndex by remember { mutableIntStateOf(0) }
    // 新建搭配后自动进入编辑模式的触发器
    var pendingEnterCutout by remember { mutableStateOf(false) }
    // 启动时迁移旧数据并加载所有搭配
    LaunchedEffect(Unit) {
        // 如果伴生对象已有缓存，直接使用，跳过 Phase 1 的 IO
        val cached = MainActivity.cachedWallpaperBitmap
        val cachedIds = MainActivity.cachedCombinationIds
        val cachedIdx = MainActivity.cachedCurrentCombinationIndex

        val ids: List<Long>
        val currentIndex: Int

        if (cached != null && cachedIds.isNotEmpty()) {
            // 有缓存：直接构建 combinations 列表，bitmap 用缓存
            ids = cachedIds
            currentIndex = cachedIdx
            val list = ids.mapIndexed { index, id ->
                com.haooz.chedule.data.Combination(
                    id = id,
                    bitmap = if (index == currentIndex) cached else null,
                    offset = Offset(
                        wallpaperRepository.getCombinationOffsetX(id),
                        wallpaperRepository.getCombinationOffsetY(id)
                    ),
                    scale = wallpaperRepository.getCombinationScale(id),
                    snapshot = null,
                    cardBlurRadius = wallpaperRepository.getCombinationCardBlur(id),
                    cardAlpha = wallpaperRepository.getCombinationCardAlpha(id),
                    cardHeight = wallpaperRepository.getCombinationCardHeight(id),
                    cardCornerRadius = wallpaperRepository.getCombinationCardCornerRadius(id),
                    wallpaperBrightness = wallpaperRepository.getCombinationWallpaperBrightness(id),
                    showBreakDividers = wallpaperRepository.getCombinationShowBreakDividers(id),
                    cardContentAlignment = wallpaperRepository.getCombinationCardContentAlignment(id)
                )
            }
            combinations = list
            currentCombinationIndex = currentIndex
        } else {
            // 无缓存：走原有逻辑
            val phase1 = withContext(Dispatchers.IO) {
                wallpaperRepository.migrateToCombinationsIfNeeded()
                val loadedIds = wallpaperRepository.getCombinationIds()
                val currentId = wallpaperRepository.getCurrentCombinationId()
                val loadedIndex = loadedIds.indexOf(currentId).coerceAtLeast(0)
                val list = loadedIds.mapIndexed { index, id ->
                    com.haooz.chedule.data.Combination(
                        id = id,
                        bitmap = if (index == loadedIndex) wallpaperRepository.loadCombinationWallpaper(
                            id
                        ) else null,
                        offset = Offset(
                            wallpaperRepository.getCombinationOffsetX(id),
                            wallpaperRepository.getCombinationOffsetY(id)
                        ),
                        scale = wallpaperRepository.getCombinationScale(id),
                        snapshot = null,
                        cardBlurRadius = wallpaperRepository.getCombinationCardBlur(id),
                        cardAlpha = wallpaperRepository.getCombinationCardAlpha(id),
                        cardHeight = wallpaperRepository.getCombinationCardHeight(id),
                        cardCornerRadius = wallpaperRepository.getCombinationCardCornerRadius(id),
                        wallpaperBrightness = wallpaperRepository.getCombinationWallpaperBrightness(
                            id
                        ),
                        showBreakDividers = wallpaperRepository.getCombinationShowBreakDividers(id),
                        cardContentAlignment = wallpaperRepository.getCombinationCardContentAlignment(
                            id
                        )
                    )
                }
                Triple(list, loadedIds, loadedIndex)
            }
            ids = phase1.second
            currentIndex = phase1.third
            combinations = phase1.first
            currentCombinationIndex = currentIndex
            // 更新缓存
            MainActivity.cachedCombinationIds = ids
            MainActivity.cachedCurrentCombinationIndex = currentIndex
        }

        // 同步当前搭配状态到 wallpaperBitmap/Offset/Scale（主界面使用）
        val curr = combinations.getOrNull(currentIndex)
        if (curr != null) {
            wallpaperBitmap = curr.bitmap
            wallpaperOffset = curr.offset
            // 计算最小缩放比例，确保壁纸填满屏幕不露出底部背景
            val bmp = curr.bitmap
            val minScale = if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                val fitScale = minOf(screenWPx / bmp.width, screenHPx / bmp.height)
                val coverScale = maxOf(screenWPx / bmp.width, screenHPx / bmp.height)
                if (fitScale > 0f) coverScale / fitScale else 1f
            } else 1f
            wallpaperScale = maxOf(curr.scale, minScale)
            savedWallpaperBitmap = curr.bitmap
            savedWallpaperOffset = curr.offset
            savedWallpaperScale = wallpaperScale
            savedAppearance = com.haooz.chedule.data.AppearanceConfig.fromCombination(curr)
            appearance = com.haooz.chedule.data.AppearanceConfig.fromCombination(curr)
            originalWallpaperBitmap = curr.bitmap
            originalWallpaperOffset = curr.offset
            originalWallpaperScale = wallpaperScale
        }

        // Phase 2：后台逐张解码其余搭配的壁纸
        withContext(Dispatchers.IO) {
            ids.forEachIndexed { index, id ->
                if (index == currentIndex) return@forEachIndexed
                val bmp = wallpaperRepository.loadCombinationWallpaper(id) ?: return@forEachIndexed
                withContext(Dispatchers.Main) {
                    val cur = combinations.getOrNull(index)
                    if (cur != null && cur.id == id && cur.bitmap == null) {
                        combinations = combinations.toMutableList().also {
                            it[index] = cur.copy(bitmap = bmp)
                        }
                    }
                }
            }
        }
    }
    val cutoutMainScale = remember { Animatable(1f) }
    var cutoutCenterYRatio by remember { mutableFloatStateOf(0.5f) }
    // 弹窗打开时的同步上移偏移（直接 translationY，与 CustomizeScheduleScreen 同帧）
    var sheetOffsetY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isWindowCutoutActive) {
        if (isWindowCutoutActive) {
            // 进入编辑模式时，同步当前搭配的值到 live 状态
            val c = combinations.getOrNull(currentCombinationIndex)
            if (c != null) {
                wallpaperBitmap = c.bitmap
                wallpaperOffset = c.offset
                // 计算最小缩放比例，确保壁纸填满屏幕不露出底部背景
                val bmp = c.bitmap
                val minScale = if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                    val fitScale = minOf(screenWPx / bmp.width, screenHPx / bmp.height)
                    val coverScale = maxOf(screenWPx / bmp.width, screenHPx / bmp.height)
                    if (fitScale > 0f) coverScale / fitScale else 1f
                } else 1f
                wallpaperScale = maxOf(c.scale, minScale)
                appearance = com.haooz.chedule.data.AppearanceConfig.fromCombination(c)
            }
            cutoutMainScale.snapTo(0.65f)
            cutoutMainScale.animateTo(
                0.75f,
                tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
            )
        } else {
            cutoutMainScale.animateTo(
                1f,
                tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
            )
        }
    }
    LaunchedEffect(showLongPressButton) {
        // 显隐由 LongPressCustomizeButton 内部驱动动画，这里只同步 visible 状态
        showLongPressOverlay = showLongPressButton
    }
    // 切换页面时关闭长按按钮
    LaunchedEffect(selectedTab) {
        if (showLongPressButton) {
            showLongPressButton = false
        }
    }
    var mainContentSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var switchScreenSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var switchCardSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var switchCardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var switchCurrentCardBounds by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(
            null
        )
    }
    var switchContentRootX by remember { mutableFloatStateOf(0f) }
    var switchContentRootY by remember { mutableFloatStateOf(0f) }
    var switchAnimJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var switchAnimForward by remember { mutableStateOf(false) }
    var switchAnimRunning by remember { mutableStateOf(false) }
    val switchAnimProgress = remember { Animatable(0f) }
    val backgroundScale = remember { Animatable(1f) }
    val managePageBlurRadius = remember { Animatable(0f) }
    // 长按快捷菜单显示时的背景模糊与缩放（与 CourseManageActivity 一致：blur 10dp / scale 0.98）
    val shortcutMenuBlurRadius = remember { Animatable(0f) }
    val shortcutMenuPageScale = remember { Animatable(1f) }
    LaunchedEffect(shortcutMenuVisible) {
        if (shortcutMenuVisible) {
            launch { shortcutMenuBlurRadius.animateTo(10f, tween(280)) }
            launch { shortcutMenuPageScale.animateTo(0.98f, tween(280)) }
        } else {
            launch { shortcutMenuBlurRadius.animateTo(0f, tween(250)) }
            launch { shortcutMenuPageScale.animateTo(1f, tween(250)) }
        }
    }
    val switchReturnBgScrim = remember { Animatable(0f) }
    val screenGraphicsLayer = rememberGraphicsLayer()
    // 模糊变化后延迟重新捕获快照的 job
    var blurSnapshotJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val hapticFeedback = LocalHapticFeedback.current

    val pagerState = rememberPagerState(
        initialPage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0)),
        pageCount = { totalWeeks }
    )

    val todayMaxDateOffset = 1000
    val todayPagerState = rememberPagerState(
        initialPage = todayMaxDateOffset,
        pageCount = { todayMaxDateOffset * 2 }
    )

    LaunchedEffect(isShiftMode) {
        if (shiftModeInitialized) {
            selectedTab = if (isShiftMode) 0 else if (defaultHomepage == "今日") 0 else 1
        }
        shiftModeInitialized = true
    }

    val topAppBarColors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(
                MiuixTheme.colorScheme.surface.copy(alpha = 0.7f),
                BlurBlendMode.SrcOver
            )
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )



    LaunchedEffect(currentWeek, totalWeeks) {
        val targetPage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    val calendar = Calendar.getInstance()
    val currentDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    var todaySelectedDayOfWeek by remember { mutableIntStateOf(currentDayOfWeek) }
    var todayIsToday by remember { mutableStateOf(true) }
    var scrollToTodayTrigger by remember { mutableIntStateOf(0) }

    val currentViewingWeek = pagerState.currentPage + 1
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val dayRange = remember(currentViewingWeek, smartWeekend, courses.size) {
        (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(currentViewingWeek)
            .filter { it in 6..7 }
    }
    val viewingIsHoliday = viewModel.isWeekHoliday(currentViewingWeek)
    val weekDates = remember(currentViewingWeek, classStartTime) {
        try {
            val startDate = LocalDate.parse(classStartTime.replace("/", "-"))
            val startMonday = startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())
            val weekMonday = startMonday.plusDays((currentViewingWeek - 1).toLong() * 7)
            (0..6).map { dayOffset -> weekMonday.plusDays(dayOffset.toLong()) }
        } catch (_: Exception) {
            emptyList()
        }
    }


    val isInFreeformWindow = activity?.isInFreeformWindow ?: false

    val screenCornerRadius = remember(isInFreeformWindow) {
        if (isInFreeformWindow) {
            20f * density.density  // 小窗默认圆角 20dp
        } else {
            try {
                val windowManager =
                    context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                val windowMetrics = windowManager.currentWindowMetrics
                val insets = windowMetrics.windowInsets
                @SuppressLint("WrongConstant")
                insets.getRoundedCorner(0)?.radius?.toFloat() ?: 0f
            } catch (_: Exception) {
                0f
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    // 关闭浮层：先播退场动画（scale 1.04→1.0），动画结束再清空状态，让原卡片 alpha 恢复 1
    val dismissFloatingCard: () -> Unit = {
        coroutineScope.launch {
            floatingScale.animateTo(1f, tween(durationMillis = 180))
            isDraggingCard = false
            floatingCardVisible = false
            draggingCourseIds = emptySet()
            draggedCardCourse = null
            draggedCardOffset = Offset.Zero
            pendingDropTarget = null
            isSnapping = false
            // 重置为入场起始值，避免下次显示时首帧渲染残留的 1.0 造成抖动
            floatingScale.snapTo(0.94f)
        }
    }

    /**
     * 计算目标落点位置卡片正中心的绝对坐标（root px）
     * 用于吸附动画：浮层从当前位置移动到目标位置中心
     */
    fun computeTargetCenter(dayOfWeek: Int, startSection: Int, sectionSpan: Int): Offset? {
        val geom = gridGeometry ?: return null
        val bounds = geom.dayBounds[dayOfWeek] ?: return null
        if (bounds.size < 3) return null
        val centerX = (bounds[0] + bounds[1]) / 2f
        val topY = bounds[2]
        val sectionH = geom.sectionHeightPx
        val dividerH = with(density) { 24.dp.toPx() }
        // 计算起始节次相对于列顶部的 y 偏移
        val morningEnd = geom.morningSections
        val afternoonStart = morningEnd + 1
        val afternoonEnd = morningEnd + geom.afternoonSections
        val eveningStart = afternoonEnd + 1
        val targetSectionTop: Float = when {
            startSection <= morningEnd -> {
                (startSection - 1) * sectionH
            }

            startSection in afternoonStart..afternoonEnd -> {
                morningEnd * sectionH + dividerH + (startSection - afternoonStart) * sectionH
            }

            else -> {
                morningEnd * sectionH + dividerH + geom.afternoonSections * sectionH + dividerH + (startSection - eveningStart) * sectionH
            }
        }
        // 卡片中心 = 卡片顶部 + 半高；卡片顶部 = 列顶 + 起始节次顶部
        val cardTopY = topY + targetSectionTop
        val cardCenterY = cardTopY + (sectionSpan + 1) * sectionH / 2f
        return Offset(centerX, cardCenterY)
    }

    /**
     * 启动吸附动画：浮层从当前 offset 移动到目标位置中心，同时缩小到 1f
     * 动画结束后清空状态，原卡片在目标位置显现
     */
    val snapFloatingCardToTarget: (dayOfWeek: Int, startSection: Int, sectionSpan: Int) -> Unit =
        { day, section, span ->
            val targetCenter = computeTargetCenter(day, section, span)
            if (targetCenter != null) {
                // 目标 offset = 目标中心 - 原卡片中心（原卡片中心 = draggedCardPosition）
                val targetOffsetX = targetCenter.x - draggedCardPosition.x
                val targetOffsetY = targetCenter.y - draggedCardPosition.y
                coroutineScope.launch {
                    isSnapping = true
                    // 初始化吸附起点为当前拖拽 offset
                    floatingOffsetX.snapTo(draggedCardOffset.x)
                    floatingOffsetY.snapTo(draggedCardOffset.y)
                    // 并行执行位移和缩小动画
                    val jobX = launch {
                        floatingOffsetX.animateTo(
                            targetOffsetX,
                            tween(
                                durationMillis = 220,
                                easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f)
                            )
                        )
                    }
                    val jobY = launch {
                        floatingOffsetY.animateTo(
                            targetOffsetY,
                            tween(
                                durationMillis = 220,
                                easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f)
                            )
                        )
                    }
                    val jobScale =
                        launch { floatingScale.animateTo(1f, tween(durationMillis = 220)) }
                    jobX.join(); jobY.join(); jobScale.join()
                    // 清空状态，原卡片在目标位置显现
                    isDraggingCard = false
                    floatingCardVisible = false
                    draggingCourseIds = emptySet()
                    draggedCardCourse = null
                    draggedCardOffset = Offset.Zero
                    pendingDropTarget = null
                    isSnapping = false
                    floatingScale.snapTo(0.94f)
                }
            } else {
                dismissFloatingCard()
            }
        }

    /** 回弹动画：浮层从当前位置动画回到原位再消失 */
    val snapFloatingCardToOrigin: () -> Unit = {
        coroutineScope.launch {
            isSnapping = true
            floatingOffsetX.snapTo(draggedCardOffset.x)
            floatingOffsetY.snapTo(draggedCardOffset.y)
            val jobX = launch {
                floatingOffsetX.animateTo(
                    0f,
                    tween(durationMillis = 220, easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f))
                )
            }
            val jobY = launch {
                floatingOffsetY.animateTo(
                    0f,
                    tween(durationMillis = 220, easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f))
                )
            }
            val jobScale = launch { floatingScale.animateTo(1f, tween(durationMillis = 220)) }
            jobX.join(); jobY.join(); jobScale.join()
            isDraggingCard = false
            floatingCardVisible = false
            draggingCourseIds = emptySet()
            draggedCardCourse = null
            draggedCardOffset = Offset.Zero
            pendingDropTarget = null
            isSnapping = false
            floatingScale.snapTo(0.94f)
        }
    }

    /**
     * 根据浮层位置计算落点 (dayOfWeek, startSection)
     * - dayOfWeek：用浮层中心点 x 找出 dayBounds 中包含的列
     * - startSection：用卡片第一格中心 y（= 卡片顶部 + 半节高）对齐网格节次
     */
    fun computeDropTarget(centerX: Float, firstSectionCenterY: Float): Pair<Int, Int>? {
        val geom = gridGeometry ?: return null
        if (geom.sectionHeightPx <= 0f) return null
        if (geom.dayBounds.isEmpty()) return null
        // 找出 centerX 落在哪列
        val day = geom.dayBounds.entries.firstOrNull { (_, bounds) ->
            bounds.size >= 2 && centerX >= bounds[0] && centerX <= bounds[1]
        }?.key ?: return null
        val topY = geom.dayBounds[day]?.getOrNull(2) ?: return null
        val relY = firstSectionCenterY - topY
        if (relY < 0f) return null
        val sectionH = geom.sectionHeightPx
        val dividerH = with(density) { 24.dp.toPx() }
        var cursor = 0f
        // 上午
        for (s in 1..geom.morningSections) {
            if (relY < cursor + sectionH) return day to s
            cursor += sectionH
        }
        if (geom.showBreakDividers) cursor += dividerH
        // 下午
        val afternoonStart = geom.morningSections + 1
        for (i in 1..geom.afternoonSections) {
            val s = afternoonStart + i - 1
            if (relY < cursor + sectionH) return day to s
            cursor += sectionH
        }
        if (geom.showBreakDividers) cursor += dividerH
        // 晚上
        val eveningStart = afternoonStart + geom.afternoonSections
        for (i in 1..geom.eveningSections) {
            val s = eveningStart + i - 1
            if (relY < cursor + sectionH) return day to s
            cursor += sectionH
        }
        return null
    }

    // 统一的课程详情页打开函数
    fun openCourseDetail(
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        fromToday: Boolean,
        courseIdToHide: String = courses.firstOrNull()?.id ?: ""
    ) {
        detailCourses = courses
        detailCardLeft = cardLeft
        detailCardTop = cardTop
        detailCardWidth = cardWidth
        detailCardHeight = cardHeight
        detailFromToday = fromToday
        coroutineScope.launch {
            hiddenCourseIds = setOf(courseIdToHide)
            // 截取全屏快照并裁剪卡片
            val fullSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
            detailSnapshot = try {
                val x = cardLeft.toInt().coerceIn(0, fullSnapshot.width - 1)
                val y = cardTop.toInt().coerceIn(0, fullSnapshot.height - 1)
                val w = cardWidth.toInt().coerceIn(1, fullSnapshot.width - x)
                val h = cardHeight.toInt().coerceIn(1, fullSnapshot.height - y)
                android.graphics.Bitmap.createBitmap(fullSnapshot, x, y, w, h)
            } catch (_: Exception) {
                null
            }

            showDetail = true
            delay(12.milliseconds)
            launch {
                backgroundScale.animateTo(
                    0.92f,
                    animationSpec = tween(560, easing = OobeQuartOutEasing)
                )
            }
            launch {
                managePageBlurRadius.animateTo(
                    5f,
                    animationSpec = tween(560, easing = OobeQuartOutEasing)
                )
            }
        }
    }

    // 进入"自定义课表"搭配页：捕获当前及相邻搭配快照后打开搭配页。
    // 由顶栏"课表外观"菜单和长按"自定义课表"按钮共用。
    val enterCustomizePage: () -> Unit = {
        coroutineScope.launch {
            val screenW = windowInfo.containerSize.width.toFloat()
            customizeExitTargetScale = (screenW * 0.65f) / screenW
            // 重排序：将当前搭配移到 index 0（加号卡右侧），其余保持相对顺序
            if (currentCombinationIndex > 0 && combinations.isNotEmpty()) {
                val list = combinations.toMutableList()
                val curr = list.removeAt(currentCombinationIndex)
                list.add(0, curr)
                combinations = list.toList()
                currentCombinationIndex = 0
            }
            // 清除所有旧快照（每次进入搭配页时重新捕获）
            combinations = combinations.map { it.copy(snapshot = null) }
            // 先加载模糊设置，确保快照捕获时包含模糊效果
            appearance = combinations.getOrNull(currentCombinationIndex)?.let {
                com.haooz.chedule.data.AppearanceConfig.fromCombination(it)
            } ?: appearance
            delay(50.milliseconds)
            // 截取当前搭配快照
            val currentSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
            customizeSnapshot = currentSnapshot
            if (combinations.isNotEmpty()) {
                combinations = combinations.toMutableList().also {
                    it[0] = it[0].copy(snapshot = currentSnapshot)
                }
            }
            // 立即捕获相邻搭配快照（相邻卡片已可见）
            if (combinations.size > 1) {
                // 用当前搭配快照遮挡，避免用户看到壁纸切换
                snapshotCoverBitmap = customizeSnapshot
                val nextComb = combinations[1]
                val savedWp2 = wallpaperBitmap
                val savedOf2 = wallpaperOffset
                val savedSc2 = wallpaperScale
                val savedAppear2 = appearance
                val savedOrigWp2 = originalWallpaperBitmap
                val savedOrigOf2 = originalWallpaperOffset
                val savedOrigSc2 = originalWallpaperScale
                val savedOrigAppear2 = originalAppearance
                wallpaperBitmap = nextComb.bitmap
                wallpaperOffset = nextComb.offset
                // 计算最小缩放比例，确保壁纸填满屏幕不露出底部背景
                val bmp2 = nextComb.bitmap
                val minScale2 = if (bmp2 != null && bmp2.width > 0 && bmp2.height > 0) {
                    val fitScale = minOf(screenWPx / bmp2.width, screenHPx / bmp2.height)
                    val coverScale = maxOf(screenWPx / bmp2.width, screenHPx / bmp2.height)
                    if (fitScale > 0f) coverScale / fitScale else 1f
                } else 1f
                wallpaperScale = maxOf(nextComb.scale, minScale2)
                appearance = com.haooz.chedule.data.AppearanceConfig.fromCombination(nextComb)
                originalWallpaperBitmap = nextComb.bitmap
                originalWallpaperOffset = nextComb.offset
                originalWallpaperScale = nextComb.scale
                originalAppearance =
                    com.haooz.chedule.data.AppearanceConfig.fromCombination(nextComb)
                delay(120.milliseconds)
                val nextSnap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                combinations = combinations.toMutableList().also {
                    it[1] = it[1].copy(snapshot = nextSnap)
                }
                wallpaperBitmap = savedWp2
                wallpaperOffset = savedOf2
                wallpaperScale = savedSc2
                appearance = savedAppear2
                originalWallpaperBitmap = savedOrigWp2
                originalWallpaperOffset = savedOrigOf2
                originalWallpaperScale = savedOrigSc2
                originalAppearance = savedOrigAppear2
                snapshotCoverBitmap = null
            }
            // 立即打开搭配页（用户看到当前搭配的正确快照）
            customizeExitScale.snapTo(1f)
            customizeExitAlpha.snapTo(1f)
            showCustomizePage = true
            isNewCombinationCreated = false
            // 记录进入搭配页时的原始搭配（重排序后当前搭配在 index 0）
            originalCombinationIndex = 0
            originalWallpaperBitmap = wallpaperBitmap
            originalWallpaperOffset = wallpaperOffset
            originalWallpaperScale = wallpaperScale
            originalAppearance = appearance
            originalSnapshot = combinations.getOrNull(currentCombinationIndex)?.snapshot
            // 后台逐个捕获其他搭配快照（等打开动画结束后再开始，避免动画期间主界面壁纸跳变）
            delay(500.milliseconds)
            val savedWp = wallpaperBitmap
            val savedOf = wallpaperOffset
            val savedSc = wallpaperScale
            val savedAppear = appearance
            for (i in 1 until combinations.size) {
                val comb = combinations[i]
                wallpaperBitmap = comb.bitmap
                wallpaperOffset = comb.offset
                // 计算最小缩放比例，确保壁纸填满屏幕不露出底部背景
                val bmp3 = comb.bitmap
                val minScale3 = if (bmp3 != null && bmp3.width > 0 && bmp3.height > 0) {
                    val fitScale = minOf(screenWPx / bmp3.width, screenHPx / bmp3.height)
                    val coverScale = maxOf(screenWPx / bmp3.width, screenHPx / bmp3.height)
                    if (fitScale > 0f) coverScale / fitScale else 1f
                } else 1f
                wallpaperScale = maxOf(comb.scale, minScale3)
                appearance = com.haooz.chedule.data.AppearanceConfig.fromCombination(comb)
                originalWallpaperBitmap = comb.bitmap
                originalWallpaperOffset = comb.offset
                originalWallpaperScale = comb.scale
                originalAppearance = com.haooz.chedule.data.AppearanceConfig.fromCombination(comb)
                delay(120.milliseconds)
                val snap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                combinations = combinations.toMutableList().also {
                    it[i] = it[i].copy(snapshot = snap)
                }
            }
            wallpaperBitmap = savedWp
            wallpaperOffset = savedOf
            wallpaperScale = savedSc
            appearance = savedAppear
            originalWallpaperBitmap = savedWp
            originalWallpaperOffset = savedOf
            originalWallpaperScale = savedSc
            originalAppearance = savedAppear
        }
    }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val bitmap = context.contentResolver.openInputStream(it)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
            wallpaperBitmap = bitmap
            // 选择新壁纸时重置位移，缩放自动计算为填满短边的最小值
            wallpaperOffset = Offset.Zero
            // 自动计算最小缩放比例，确保壁纸填满屏幕不露出底部背景
            val autoScale = if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                val fitScale = minOf(screenWPx / bitmap.width, screenHPx / bitmap.height)
                val coverScale = maxOf(screenWPx / bitmap.width, screenHPx / bitmap.height)
                if (fitScale > 0f) coverScale / fitScale else 1f
            } else 1f
            wallpaperScale = autoScale
            // 同步到当前搭配
            val idx = currentCombinationIndex
            if (idx in combinations.indices) {
                combinations = combinations.toMutableList().also {
                    it[idx] = it[idx].copy(
                        bitmap = bitmap,
                        offset = Offset.Zero,
                        scale = autoScale
                    )
                }
            }
        }
    }

    var showSwitchSchedule by remember { mutableStateOf(false) }
    var switchPendingReverse by remember { mutableStateOf(false) }
    var switchCapturingSnapshot by remember { mutableStateOf(false) }
    var scheduleChanged by remember { mutableStateOf(false) }
    var showMorePopup by remember { mutableStateOf(false) }
    var showTodayMorePopup by remember { mutableStateOf(false) }
    var todayJumpToDateTrigger by remember { mutableIntStateOf(0) }

    val isViewingCurrentWeek = currentViewingWeek == currentWeek

    // 退出缩放中心：与搭配界面卡片中心对齐

    // 分屏分割线：在最外层 Box 绘制，层级高于所有内部模糊层，避免被顶部模糊层遮挡
    // MainActivity 是分屏左侧（primary），其最右侧即为左右分界处
    val splitDividerColor = if (isDark) Color(0xFF222222) else Color(0xFFEEEEEE)
    val isInSplit by produceState(initialValue = false) {
        val act = activity ?: return@produceState
        SplitController.getInstance(context).splitInfoList(act).collect { list ->
            value = list.isNotEmpty()
        }
    }
    // 追踪分屏右侧当前打开的 Activity 类名，用于压暗左侧对应选项
    // ActivityStack 不暴露公开的 Activity 列表，改用全局生命周期回调追踪
    val activeSecondaryActivity by produceState<String?>(initialValue = null) {
        val app = context.applicationContext as? android.app.Application ?: return@produceState
        val mainActivityClass = activity?.javaClass
        val callback = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: android.app.Activity) {
                if (a.javaClass == mainActivityClass) return
                value = a.javaClass.simpleName
            }

            override fun onActivityPaused(a: android.app.Activity) {
                if (a.javaClass.simpleName == value) value = null
            }

            override fun onActivityCreated(a: android.app.Activity, b: Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callback)
        awaitDispose { app.unregisterActivityLifecycleCallbacks(callback) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (showCustomizePage) Color(0xFF1A1A1A) else MiuixTheme.colorScheme.surface)
            .drawWithContent {
                drawContent()
                if (isInSplit) {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = splitDividerColor,
                        start = Offset(size.width - strokeWidth / 2f, 0f),
                        end = Offset(size.width - strokeWidth / 2f, size.height),
                        strokeWidth = strokeWidth
                    )
                }
            }) {
        val displayAppearance =
            if (showCustomizePage && !isWindowCutoutActive) originalAppearance else appearance
        val isEntryAnimating = showSwitchSchedule && switchAnimForward && switchAnimRunning
        val mainContentAlpha = when {
            showSwitchSchedule && switchScreenSnapshot != null -> 0f
            else -> 1f
        }
        // 创建全屏模糊的 backdrop（始终存在，不依赖 showDetail）
        val color = MiuixTheme.colorScheme.surface
        val fullBlurBackdrop = rememberLayerBackdrop {
            drawRect(color)
            drawContent()
        }
        // 主内容（带缩放和裁切）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(if (shortcutMenuBlurRadius.value > 0.01f) shortcutMenuBlurRadius.value.dp else managePageBlurRadius.value.dp)
                .then(
                    if (navBarStyle != "rail") {
                        // 圆角裁剪在 graphicsLayer 内部完成（见下），这里不再单独 clip
                        Modifier
                    } else Modifier
                )
                .graphicsLayer {
                    val baseScale =
                        if (!showSwitchSchedule) backgroundScale.value
                        else if (isEntryAnimating) 1f
                        else 1f
                    val exitScale = if (isCustomizeExiting) customizeExitScale.value else 1f
                    val cutoutScale = cutoutMainScale.value
                    // 应用时从开洞状态退出，主界面仅由 cutoutScale 控制（0.75→1.0）
                    // 避免与 exitScale 相乘导致双重缩放
                    val effectiveScale = if (isCustomizeExiting && isWindowCutoutActive) {
                        cutoutScale
                    } else {
                        exitScale * cutoutScale
                    }
                    scaleX = baseScale * effectiveScale * shortcutMenuPageScale.value
                    scaleY = baseScale * effectiveScale * shortcutMenuPageScale.value
                    alpha = mainContentAlpha
                    // 弹窗打开时同步上移（直接 translationY，与 CustomizeScheduleScreen 同帧）
                    // cutout 区域的偏移贡献为 sheetOffsetY * (1-scaleProg)，scale=0.75 时 = 0.7143
                    translationY = sheetOffsetY * 0.7143f
                    if (isCustomizeExiting) {
                        transformOrigin = TransformOrigin(0.5f, 0.58f)
                    }
                    if (isWindowCutoutActive) {
                        // 使用 CustomizeScheduleScreen 传回的裁剪中心比例，保证两者完全对齐
                        transformOrigin = TransformOrigin(0.5f, cutoutCenterYRatio)
                    }
                }
                .then(
                    // 用 drawWithContent + clipPath + addSquircleRect 实现 squircle 圆角裁剪
                    // drawWithContent 在 graphicsLayer 缩放后应用，每帧重新裁剪
                    // 视觉圆角 = screenRadius * effectiveScale（随缩放变小）
                    // 搭配页退出时锁定圆角为 screenCornerRadius，避免缩小
                    Modifier.drawWithContent {
                        val scale = backgroundScale.value * shortcutMenuPageScale.value
                        val shouldClip = !isCustomizeExiting && scale < 0.999f
                        val animClipPx =
                            if (isCustomizeExiting) screenCornerRadius else screenCornerRadius
                        if (animClipPx > 0f && (shouldClip || isCustomizeExiting)) {
                            val path = Path().apply {
                                addSquircleRect(
                                    width = size.width,
                                    height = size.height,
                                    cornerRadius = animClipPx
                                )
                            }
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                    }
                )
                .then(
                    Modifier.drawWithContent {
                        screenGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawContent()
                    }
                )
                .layerBackdrop(fullBlurBackdrop)
        ) {
            Scaffold(
                bottomBar = {
                    ScheduleBottomBar(
                        navBarStyle = navBarStyle,
                        isShiftMode = isShiftMode,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        railState = railState,
                        backdrop = backdrop,
                        blurColors = blurColors,
                        isDark = isDark,
                        liquidGlassBackdrop = liquidGlassBackdrop
                    )
                },
                topBar = {
                    ScheduleTopBar(
                        visible = (!isShiftMode && selectedTab == 1) || (isShiftMode && selectedTab == 0),
                        navBarStyle = navBarStyle,
                        railState = railState,
                        pagerCurrentPage = pagerState.currentPage,
                        currentWeek = currentWeek,
                        totalWeeks = totalWeeks,
                        isHoliday = viewingIsHoliday,
                        isViewingCurrentWeek = isViewingCurrentWeek,
                        titleBarHeight = activity?.titleBarHeight ?: 56.dp,
                        topAppBarColors = topAppBarColors,
                        backdrop = backdrop,
                        dayRange = dayRange,
                        currentDayOfWeek = currentDayOfWeek,
                        isCurrentWeek = pagerState.currentPage + 1 == currentWeek && currentWeek in 1..totalWeeks,
                        weekDates = weekDates,
                        onBackToCurrentWeek = {
                            coroutineScope.launch {
                                val targetPage =
                                    (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
                                pagerState.animateScrollToPage(targetPage)
                            }
                        },
                        onOpenSwitchSchedule = {
                            if (!isShiftMode && !showSwitchSchedule) {
                                coroutineScope.launch {
                                    mainContentSnapshot =
                                        screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                    switchPendingReverse = true
                                    switchCapturingSnapshot = true
                                    showSwitchSchedule = true
                                }
                            }
                        },
                        onJumpWeek = { viewModel.showJumpWeekDialog() },
                        onOpenCustomize = {
                            coroutineScope.launch {
                                // 等待弹窗菜单收回后再截屏
                                delay(200.milliseconds)
                                enterCustomizePage()
                            }
                        },
                        onCourseManage = {
                            val intent =
                                android.content.Intent(context, CourseManageActivity::class.java)
                            context.startActivity(intent)
                        },
                        onTitleBarMeasured = { activity?.titleBarHeight = it },
                        isTablet = isTablet,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        showMorePopup = showMorePopup,
                        onShowMorePopupChange = { showMorePopup = it }
                    )
                    // 设置页标题栏（Activity 层级渲染，避免 drawPlainBackdrop native crash）
                    if (selectedTab == 2 || (isShiftMode && selectedTab == 1)) {
                        SettingsTopBar(
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            navBarStyle = navBarStyle,
                            scrollBehavior = settingsScrollBehavior,
                        )
                    }
                    // 今日页标题栏（液态玻璃模式下在 Activity 层级渲染）
                    if (!isShiftMode && selectedTab == 0) {
                        TodayTopBar(
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            navBarStyle = navBarStyle,
                            currentDayOfWeek = todaySelectedDayOfWeek,
                            isToday = todayIsToday,
                            onBackToToday = { scrollToTodayTrigger++ },
                            onMoreClick = { showTodayMorePopup = true },
                            scrollBehavior = todayScrollBehavior,
                        )
                    }
                }
            ) { _ ->
                // 不再用 combinations.isEmpty() 门控整个内容区：
                // 课程网格（TodayScreen/MainScheduleScreen）只依赖 viewModel，与壁纸加载解耦。
                // 壁纸未就绪时 wallpaperBitmap=null，MainScheduleScreen 内部显示主题底色，课程方块照常渲染。
                // 搭配相关的操作（新建/删除/编辑）在各自回调里已有 getOrNull 守卫，空列表时不会越界。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = railPaddingStart)
                        .layerBackdrop(backdrop)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (appStyle == "liquidglass") Modifier.liquidGlassLayerBackdrop(
                                    liquidGlassBackdrop
                                )
                                else Modifier
                            )
                    ) {
                        if (!isShiftMode) {
                            when (selectedTab) {
                                0 -> TodayScreen(
                                    viewModel = viewModel,
                                    settingsViewModel = settingsViewModel,
                                    hiddenCourseIds = hiddenCourseIds,
                                    onCourseClick = { courses, left, top, width, height, _, courseIdToHide ->
                                        openCourseDetail(
                                            courses,
                                            left,
                                            top,
                                            width,
                                            height,
                                            fromToday = true,
                                            courseIdToHide = courseIdToHide
                                        )
                                    },
                                    pagerState = todayPagerState,
                                    navBarStyle = navBarStyle,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    onScrollYChanged = { todayScrollY = it },
                                    settingsScrollBehavior = todayScrollBehavior,
                                    onSelectedDayChanged = { todaySelectedDayOfWeek = it },
                                    onSelectedDateChanged = { todayIsToday = it },
                                    scrollToTodayTrigger = scrollToTodayTrigger,
                                    showMorePopup = showTodayMorePopup,
                                    onShowMorePopupChange = { showTodayMorePopup = it },
                                    jumpToDateTrigger = todayJumpToDateTrigger,
                                    onJumpToDateProcessed = { todayJumpToDateTrigger = 0 },
                                    onCourseManage = {
                                        val intent = android.content.Intent(
                                            context,
                                            CourseManageActivity::class.java
                                        )
                                        context.startActivity(intent)
                                    },
                                    wallpaperBitmap = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBitmap else wallpaperBitmap,
                                    wallpaperOffset = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset,
                                    wallpaperScale = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale,
                                    wallpaperBrightness = displayAppearance.wallpaperBrightness,
                                    cardBlurRadius = displayAppearance.cardBlurRadius
                                )

                                1 -> {
                                    MainScheduleScreen(
                                        viewModel = viewModel,
                                        settingsViewModel = settingsViewModel,
                                        pagerState = pagerState,
                                        hiddenCourseIds = hiddenCourseIds,
                                        draggingCourseIds = draggingCourseIds,
                                        onCourseClick = { courses, left, top, width, height, _, courseIdToHide ->
                                            openCourseDetail(
                                                courses,
                                                left,
                                                top,
                                                width,
                                                height,
                                                fromToday = false,
                                                courseIdToHide = courseIdToHide
                                            )
                                        },
                                        onPopupStateChange = { showCourseDetailPopup = it },
                                        onEmptyLongPress = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showLongPressButton = true
                                        },
                                        onCourseLongPress = { course, left, top, width, height, backdrop, currentWeek ->
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isDraggingCard = true
                                            floatingCardVisible = true
                                            draggingCourseIds = setOf(course.id)
                                            draggedCardCourse = course
                                            draggedWeek = currentWeek
                                            // left/top 现在是卡片正中心绝对坐标，浮层按中心对齐使用
                                            draggedCardPosition = Offset(left, top)
                                            draggedCardOffset = Offset.Zero
                                            draggedCardSize = Offset(width, height)
                                            draggedCardBackdrop = backdrop
                                            shortcutMenuCourse = course
                                            shortcutMenuVisible = true
                                            // 快捷菜单仍按左上角定位，把中心点转回左上角
                                            shortcutMenuPosition =
                                                Offset(left - width / 2f, top - height / 2f)
                                            shortcutMenuBackdrop = backdrop
                                        },
                                        onCourseDragStart = { _ ->
                                            // 拖拽开始不关闭菜单，菜单保留到移动超过阈值后由 onCourseMenuDismiss 关闭
                                            pendingDropTarget = null
                                        },
                                        onCourseMenuDismiss = {
                                            // 移动超过阈值，触发菜单退出动画
                                            shortcutMenuVisible = false
                                            coroutineScope.launch {
                                                delay(220.milliseconds)
                                                shortcutMenuCourse = null
                                            }
                                        },
                                        onCourseDrag = { _, offsetX, offsetY ->
                                            draggedCardOffset = Offset(offsetX, offsetY)
                                            // 实时计算落点：x 用浮层中心，y 用卡片第一格中心（卡片顶部 + 半节高）
                                            // 卡片高度基于 course 实时计算，避免 draggedCardSize 缓存旧值导致偏移
                                            val course = draggedCardCourse
                                            if (course != null) {
                                                val sectionH = gridGeometry?.sectionHeightPx ?: 0f
                                                val sectionCount =
                                                    course.endSection - course.startSection + 1
                                                val cardHeightPx = sectionCount * sectionH
                                                val centerX = draggedCardPosition.x + offsetX
                                                val cardTopY =
                                                    draggedCardPosition.y + offsetY - cardHeightPx / 2f
                                                val firstSectionCenterY = cardTopY + sectionH / 2f
                                                pendingDropTarget =
                                                    computeDropTarget(centerX, firstSectionCenterY)
                                            }
                                        },
                                        onCourseDragEnd = { _ ->
                                            // 仅结束拖拽浮层，不关闭菜单；菜单关闭交给 onCourseMenuDismiss（超过阈值）或点击空白处
                                            // 调课落点提交：根据 pendingDropTarget 决定是否调课
                                            val source = draggedCardCourse
                                            val target = pendingDropTarget
                                            val week = draggedWeek
                                            // 松手立即清除高亮
                                            pendingDropTarget = null
                                            if (source != null && target != null) {
                                                val sectionSpan =
                                                    source.endSection - source.startSection
                                                val targetStart = target.second
                                                val targetEnd = targetStart + sectionSpan
                                                // 落点位置若与原位置一致，不做任何操作
                                                val sameSlot = source.dayOfWeek == target.first &&
                                                        source.startSection == targetStart &&
                                                        source.endSection == targetEnd
                                                if (!sameSlot) {
                                                    // 检查目标位置该周是否有冲突课程（仅算本周活跃的课）
                                                    val conflicts = viewModel.getCoursesAtSlot(
                                                        week, target.first, targetStart, targetEnd
                                                    ).filter {
                                                        it.id != source.id && it.isActiveInWeek(week)
                                                    }
                                                    if (conflicts.isEmpty()) {
                                                        // 空位：移动并播放吸附动画
                                                        viewModel.moveCourseForWeek(
                                                            source.id,
                                                            week,
                                                            target.first,
                                                            targetStart,
                                                            targetEnd
                                                        )
                                                        snapFloatingCardToTarget(
                                                            target.first,
                                                            targetStart,
                                                            sectionSpan
                                                        )
                                                        // 计算原位置露出的非本周课程，添加淡入放大动画
                                                        val sourceCourses =
                                                            viewModel.getCoursesAtSlot(
                                                                week,
                                                                source.dayOfWeek,
                                                                source.startSection,
                                                                source.endSection
                                                            ).filter {
                                                                it.id != source.id && !it.isActiveInWeek(
                                                                    week
                                                                )
                                                            }
                                                        if (sourceCourses.isNotEmpty()) {
                                                            animateInCourseIds =
                                                                sourceCourses.map { it.id }.toSet()
                                                            coroutineScope.launch {
                                                                delay(350.milliseconds)
                                                                animateInCourseIds = emptySet()
                                                            }
                                                        }
                                                    } else {
                                                        // 有课：暂存冲突信息，弹出对话框让用户选择"覆盖"或"交换"
                                                        pendingConflictCourse = conflicts.first()
                                                        // 暂存目标位置到 draggedCardCourse 的临时字段不容易，借助独立状态
                                                        pendingDropTarget =
                                                            target.first to targetStart
                                                        showRescheduleConflictDialog = true
                                                        // 不立刻关闭浮层，等用户选择后再处理
                                                        // 但浮层要先隐藏，避免遮挡对话框
                                                        coroutineScope.launch {
                                                            floatingScale.animateTo(
                                                                1f,
                                                                tween(durationMillis = 180)
                                                            )
                                                            delay(180.milliseconds)
                                                            isDraggingCard = false
                                                            floatingCardVisible = false
                                                            draggingCourseIds = emptySet()
                                                            // 不清空 draggedCardCourse/pendingDropTarget，待对话框处理后再清
                                                            draggedCardOffset = Offset.Zero
                                                        }
                                                    }
                                                } else {
                                                    snapFloatingCardToOrigin()
                                                }
                                            } else {
                                                dismissFloatingCard()
                                            }
                                        },
                                        wallpaperBitmap = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBitmap else wallpaperBitmap,
                                        wallpaperOffset = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset,
                                        wallpaperScale = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale,
                                        isWallpaperEditing = isWindowCutoutActive,
                                        onWallpaperOffsetChange = { wallpaperOffset = it },
                                        onWallpaperScaleChange = { wallpaperScale = it },
                                        cardBlurRadius = displayAppearance.cardBlurRadius,
                                        cardAlpha = displayAppearance.cardAlpha,
                                        cardHeightPerSection = displayAppearance.cardHeight,
                                        cardCornerRadius = displayAppearance.cardCornerRadius,
                                        wallpaperBrightness = displayAppearance.wallpaperBrightness,
                                        showBreakDividers = displayAppearance.showBreakDividers,
                                        cardContentAlignment = displayAppearance.cardContentAlignment,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        onGridGeometryChange = { geom -> gridGeometry = geom },
                                        dropHighlight = run {
                                            val target = pendingDropTarget
                                            val source = draggedCardCourse
                                            if (floatingCardVisible && target != null && source != null) {
                                                val sectionSpan =
                                                    source.endSection - source.startSection
                                                target.first to (target.second..(target.second + sectionSpan))
                                            } else null
                                        },
                                        animateInCourseIds = animateInCourseIds
                                    )
                                }

                                2 -> SettingsScreen(
                                    viewModel = viewModel,
                                    scheduleViewModel = scheduleViewModel,
                                    settingsViewModel = settingsViewModel,
                                    shiftViewModel = shiftViewModel,
                                    onEnterShiftMode = {
                                        showShiftLoading = true
                                        isExitingShift = false
                                    },
                                    navBarStyle = navBarStyle,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    onScrollYChanged = { settingsScrollY = it },
                                    settingsScrollBehavior = settingsScrollBehavior,
                                    activeSecondaryActivity = activeSecondaryActivity
                                )
                            }
                        } else {
                            when (selectedTab) {
                                0 -> ShiftScheduleScreen(
                                    shiftViewModel = shiftViewModel,
                                    settingsViewModel = settingsViewModel,
                                    pagerState = pagerState,
                                    cardHeightPerSection = appearance.cardHeight,
                                )

                                1 -> SettingsScreen(
                                    viewModel = viewModel,
                                    scheduleViewModel = scheduleViewModel,
                                    settingsViewModel = settingsViewModel,
                                    shiftViewModel = shiftViewModel,
                                    isShiftMode = true,
                                    onExitShiftMode = {
                                        showShiftLoading = true
                                        isExitingShift = true
                                    },
                                    onEnterShiftMode = {
                                        showShiftLoading = true
                                        isExitingShift = false
                                    },
                                    navBarStyle = navBarStyle,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    onScrollYChanged = { settingsScrollY = it },
                                    settingsScrollBehavior = settingsScrollBehavior,
                                    activeSecondaryActivity = activeSecondaryActivity
                                )
                            }
                        }
                    }
                }
                // 长按空白区域后显示的"自定义课表"按钮
                LongPressCustomizeButton(
                    visible = showLongPressOverlay,
                    backdrop = backdrop,
                    isDark = isDark,
                    onClick = {
                        showLongPressButton = false
                        coroutineScope.launch {
                            delay(120.milliseconds)
                            showLongPressOverlay = false
                            enterCustomizePage()
                        }
                    },
                    onDismiss = { showLongPressButton = false }
                )

                // 分享导入确认弹窗（必须在 Scaffold 内部）
                ShareImportDialog(
                    activity = activity,
                    shareIntentVersion = activity?.shareIntentVersion ?: 0,
                    courseViewModel = viewModel,
                    scheduleViewModel = scheduleViewModel,
                    settingsViewModel = settingsViewModel
                )

                // 更新弹窗
                UpdateDialog()

                // LiquidGlass 添加课程浮动按钮
                if (appStyle == "liquidglass" && !isShiftMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 20.dp, bottom = 28.dp)
                            .zIndex(1f),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        LiquidAddButton(
                            onClick = { viewModel.showAddDialog() },
                            backdrop = liquidGlassBackdrop
                        )
                    }
                }

                // 添加课程对话框
                val showAddDialog by viewModel.showAddDialog.collectAsState()
                val editingCourse by viewModel.editingCourse.collectAsState()
                val selectedStartSection by viewModel.selectedStartSection.collectAsState()
                val selectedEndSection by viewModel.selectedEndSection.collectAsState()
                val editingStartSection =
                    editingCourse?.startSection ?: selectedStartSection
                val editingEndSection = editingCourse?.endSection ?: selectedEndSection

                AddCourseDialog(
                    show = showAddDialog,
                    course = editingCourse,
                    selectedDay = viewModel.selectedDay.collectAsState().value,
                    backdrop = backdrop,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    totalWeeks = totalWeeks,
                    totalSections = totalSections,
                    defaultStartSection = editingStartSection,
                    defaultEndSection = editingEndSection,
                    getOccupiedWeeks = { dayOfWeek, startSection, endSection, excludeIds ->
                        viewModel.getOccupiedWeeks(
                            dayOfWeek = dayOfWeek,
                            startSection = startSection,
                            endSection = endSection,
                            excludeIds = excludeIds.toSet()
                        )
                    },
                    onDismiss = { viewModel.hideDialog() },
                    onConfirm = { course ->
                        if (editingCourse != null) {
                            viewModel.updateCourse(course)
                        } else {
                            viewModel.addCourse(course)
                        }
                    },
                    onDelete = { courseId ->
                        viewModel.deleteCourse(courseId)
                    }
                )
                // 删除本周课程确认弹窗
                OverlayDialog(
                    title = "删除本周课程",
                    summary = "确定要删除「${deleteConfirmCourse?.name}」在第${draggedWeek}周的课程吗？\n此操作不可撤销。",
                    show = showDeleteConfirmDialog,
                    onDismissRequest = { showDeleteConfirmDialog = false }
                ) {
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
                                showDeleteConfirmDialog = false
                            },
                        ) {
                            Text(
                                "取消",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                deleteConfirmCourse?.let { course ->
                                    viewModel.deleteCourseForWeek(course.id, draggedWeek)
                                }
                                showDeleteConfirmDialog = false
                            },
                        ) {
                            Text(
                                "删除",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFF44336)
                            )
                        }
                    }
                }
                // 调课冲突弹窗：拖到有课位置时让用户选择"覆盖"或"交换"
                val conflictSource = draggedCardCourse
                val conflictTarget = pendingConflictCourse
                val conflictDrop = pendingDropTarget
                OverlayDialog(
                    title = "该位置已有课程",
                    summary = if (conflictTarget != null && conflictSource != null && conflictDrop != null) {
                        "「${conflictSource.name}」要如何处理与「${conflictTarget.name}」的位置冲突？\n" +
                                "覆盖：删除「${conflictTarget.name}」本周的课程，并把「${conflictSource.name}」调到此位置\n" +
                                "交换：互换本周两节课的位置"
                    } else {
                        "该位置已有课程，要如何处理？"
                    },
                    show = showRescheduleConflictDialog,
                    onDismissRequest = {
                        // 取消：仅清空状态，不做调课
                        showRescheduleConflictDialog = false
                        pendingConflictCourse = null
                        pendingDropTarget = null
                        draggedCardCourse = null
                    }
                ) {
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
                                showRescheduleConflictDialog = false
                                pendingConflictCourse = null
                                pendingDropTarget = null
                                draggedCardCourse = null
                            },
                        ) {
                            Text(
                                "取消",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val source = draggedCardCourse
                                val target = pendingDropTarget
                                val conflict = pendingConflictCourse
                                if (source != null && target != null && conflict != null) {
                                    val sectionSpan = source.endSection - source.startSection
                                    val targetEnd = target.second + sectionSpan
                                    viewModel.overwriteCourseForWeek(
                                        source.id,
                                        draggedWeek,
                                        target.first,
                                        target.second,
                                        targetEnd
                                    )
                                    // 计算原位置露出的非本周课程
                                    val sourceCourses = viewModel.getCoursesAtSlot(
                                        draggedWeek,
                                        source.dayOfWeek,
                                        source.startSection,
                                        source.endSection
                                    ).filter {
                                        it.id != source.id && !it.isActiveInWeek(
                                            draggedWeek
                                        )
                                    }
                                    if (sourceCourses.isNotEmpty()) {
                                        animateInCourseIds = sourceCourses.map { it.id }.toSet()
                                        coroutineScope.launch {
                                            delay(350.milliseconds)
                                            animateInCourseIds = emptySet()
                                        }
                                    }
                                }
                                showRescheduleConflictDialog = false
                                pendingConflictCourse = null
                                pendingDropTarget = null
                                draggedCardCourse = null
                            },
                        ) {
                            Text(
                                "覆盖",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF9800)
                            )
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val source = draggedCardCourse
                                val conflict = pendingConflictCourse
                                if (source != null && conflict != null) {
                                    viewModel.swapCoursesForWeek(
                                        source.id,
                                        conflict.id,
                                        draggedWeek
                                    )
                                    // 计算两个原位置露出的非本周课程
                                    val sourceCourses = viewModel.getCoursesAtSlot(
                                        draggedWeek,
                                        source.dayOfWeek,
                                        source.startSection,
                                        source.endSection
                                    ).filter {
                                        it.id != source.id && it.id != conflict.id && !it.isActiveInWeek(
                                            draggedWeek
                                        )
                                    }
                                    val conflictCourses = viewModel.getCoursesAtSlot(
                                        draggedWeek,
                                        conflict.dayOfWeek,
                                        conflict.startSection,
                                        conflict.endSection
                                    ).filter {
                                        it.id != conflict.id && it.id != source.id && !it.isActiveInWeek(
                                            draggedWeek
                                        )
                                    }
                                    val allAnimated =
                                        (sourceCourses + conflictCourses).map { it.id }.toSet()
                                    if (allAnimated.isNotEmpty()) {
                                        animateInCourseIds = allAnimated
                                        coroutineScope.launch {
                                            delay(350.milliseconds)
                                            animateInCourseIds = emptySet()
                                        }
                                    }
                                }
                                showRescheduleConflictDialog = false
                                pendingConflictCourse = null
                                pendingDropTarget = null
                                draggedCardCourse = null
                            },
                        ) {
                            Text(
                                "交换",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        // 拖拽课程卡片浮层（退出动画期间仍保持渲染，直到 scale 回到 1f 才移除并让原卡片显现）
        if (floatingCardVisible) {
            val course = draggedCardCourse
            if (course != null) {
                android.util.Log.d(
                    "FloatRender",
                    "render course=${course.name}, sec=${course.startSection}-${course.endSection}, draggedCardSize=${draggedCardSize}"
                )
                // draggedCardPosition 为卡片正中心绝对坐标，浮层按中心对齐：offset = 中心 - 半宽
                // 吸附期间使用 floatingOffsetAnim 替代 draggedCardOffset，实现从当前位置到目标位置的动画
                val currentOffsetX = if (isSnapping) floatingOffsetX.value else draggedCardOffset.x
                val currentOffsetY = if (isSnapping) floatingOffsetY.value else draggedCardOffset.y
                val centerX = draggedCardPosition.x + currentOffsetX
                val centerY = draggedCardPosition.y + currentOffsetY
                // 宽度用 draggedCardSize（宽度不随节数变化）
                // 高度按原卡片每节高度 × 当前 course 节数实时计算，避免 draggedCardSize 缓存旧节数
                val widthPx = draggedCardSize.x
                val sectionCount = course.endSection - course.startSection + 1
                val sectionH = gridGeometry?.sectionHeightPx
                    ?: with(density) { displayAppearance.cardHeight.dp.toPx() }
                val heightPx = sectionCount * sectionH
                val offsetX = with(density) { (centerX - widthPx / 2f).toDp() }
                val offsetY = with(density) { (centerY - heightPx / 2f).toDp() }
                val width = with(density) { widthPx.toDp() }
                val height = with(density) { heightPx.toDp() }
                LaunchedEffect(floatingCardVisible) {
                    if (floatingCardVisible) {
                        floatingScale.snapTo(0.94f)
                        floatingScale.animateTo(1.04f, tween(durationMillis = 120))
                    }
                }

                Box(
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .size(width = width, height = height)
                        .padding(vertical = 2.dp)
                        .graphicsLayer {
                            scaleX = floatingScale.value
                            scaleY = floatingScale.value
                        }
                ) {
                    CourseCard(
                        course = course,
                        isCurrentWeek = course.isActiveInWeek(draggedWeek),
                        wallpaperBackdrop = if (wallpaperBitmap != null) liquidGlassBackdrop else null,
                        cardBlurRadius = displayAppearance.cardBlurRadius,
                        cardAlpha = displayAppearance.cardAlpha,
                        cardHeightPerSection = displayAppearance.cardHeight,
                        cardCornerRadius = displayAppearance.cardCornerRadius,
                        isTablet = isTablet,
                        cardContentAlignment = displayAppearance.cardContentAlignment,
                        disablePadding = true,
                        onClick = {}
                    )
                }
            }
        }
        // 快捷菜单：点击外部关闭（先触发退出动画，动画结束再清空状态）
        if (shortcutMenuCourse != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 菜单退场动画 + 浮层缩回 1f 动画并行，动画结束再清空
                        shortcutMenuVisible = false
                        dismissFloatingCard()
                        coroutineScope.launch {
                            delay(220.milliseconds)
                            shortcutMenuCourse = null
                        }
                    }
            )
        }
        // 快捷菜单浮层
        val activeShortcutCourse = shortcutMenuCourse
        if (activeShortcutCourse != null) {
            ShortcutMenu(
                show = shortcutMenuVisible,
                items = listOf(
                    ShortcutMenuItem(
                        icon = MiuixIcons.Edit,
                        label = "编辑",
                        onClick = {
                            shortcutMenuVisible = false
                            dismissFloatingCard()
                            coroutineScope.launch {
                                delay(240.milliseconds)
                                shortcutMenuCourse = null
                            }
                            viewModel.showEditDialog(activeShortcutCourse)
                        }
                    ),
                    ShortcutMenuItem(
                        icon = MiuixIcons.Delete,
                        label = "删除",
                        onClick = {
                            deleteConfirmCourse = activeShortcutCourse
                            shortcutMenuVisible = false
                            dismissFloatingCard()
                            coroutineScope.launch {
                                delay(240.milliseconds)
                                shortcutMenuCourse = null
                            }
                            showDeleteConfirmDialog = true
                        }
                    )
                ),
                modifier = Modifier.offset(
                    x = with(density) { shortcutMenuPosition.x.toDp() - 14.dp },
                    y = with(density) { (shortcutMenuPosition.y - shortcutMenuSize.height).toDp() + 6.dp }
                ),
                backdrop = liquidGlassBackdrop,
                onMeasuredSize = { width, height ->
                    shortcutMenuSize = IntSize(width, height)
                },
                onDismiss = {
                    shortcutMenuVisible = false
                    dismissFloatingCard()
                    coroutineScope.launch {
                        delay(220.milliseconds)
                        shortcutMenuCourse = null
                    }
                }
            )
        }
        // LiquidGlass 更多菜单（Scaffold 外层，显示在最上方）
        if (appStyle == "liquidglass") {
            if (showMorePopup) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showMorePopup = false }
                )
            }
            if (showTodayMorePopup) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showTodayMorePopup = false }
                )
            }
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = false }
                    .padding(
                        top = if (statusBarHeight > 0.dp) statusBarHeight + 32.dp else 70.dp,
                        end = 2.dp
                    ),
                contentAlignment = Alignment.TopEnd
            ) {
                LiquidGlassDropdownMenu(
                    show = showMorePopup,
                    backdrop = liquidGlassBackdrop,
                ) {
                    LiquidGlassDropdownMenuItem(
                        text = "跳转周数",
                        onClick = {
                            showMorePopup = false
                            viewModel.showJumpWeekDialog()
                        }
                    )
                    LiquidGlassDropdownMenuItem(
                        text = "课程管理",
                        onClick = {
                            showMorePopup = false
                            val intent =
                                android.content.Intent(context, CourseManageActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                    LiquidGlassDropdownMenuItem(
                        text = "课表外观",
                        onClick = {
                            showMorePopup = false
                            coroutineScope.launch {
                                delay(200.milliseconds)
                                enterCustomizePage()
                            }
                        }
                    )
                }
                LiquidGlassDropdownMenu(
                    show = showTodayMorePopup,
                    backdrop = liquidGlassBackdrop
                ) {
                    LiquidGlassDropdownMenuItem(
                        text = "跳转日期",
                        onClick = {
                            showTodayMorePopup = false
                            todayJumpToDateTrigger++
                        }
                    )
                    LiquidGlassDropdownMenuItem(
                        text = "课程管理",
                        onClick = {
                            showTodayMorePopup = false
                            val intent =
                                android.content.Intent(context, CourseManageActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
        // 进入动画遮罩（仅颜色渐变，模糊由 SwitchScheduleScreen 自身承担）
        if (isEntryAnimating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) ComposeColor.Black.copy(alpha = switchReturnBgScrim.value)
                        else ComposeColor.Black.copy(alpha = switchReturnBgScrim.value * 0.6f)
                    )
            )
        }
        // 自定义课表页面（层级在 MainActivity 之上）
        val window = (context as? ComponentActivity)?.window
        val windowInsetsController = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
        }
        if (showCustomizePage && customizeSnapshot != null) {
            LaunchedEffect(true) {
                if (showCustomizePage) {
                    // 黑色背景，状态栏/导航栏图标反色为白色
                    windowInsetsController?.isAppearanceLightStatusBars = false
                    windowInsetsController?.isAppearanceLightNavigationBars = false
                } else {
                    windowInsetsController?.isAppearanceLightStatusBars = true
                    windowInsetsController?.isAppearanceLightNavigationBars = true
                }
            }
            val dismissCustomize: () -> Unit = {
                isApplyingCustomize = false
                coroutineScope.launch {
                    blurSnapshotJob?.cancel()
                    // 如果本次会话创建了新搭配，退出时需删除它
                    if (isNewCombinationCreated) {
                        val newComb = combinations.getOrNull(currentCombinationIndex)
                        if (newComb != null) {
                            withContext(Dispatchers.IO) {
                                wallpaperRepository.deleteCombination(newComb.id)
                            }
                            combinations = combinations.toMutableList().also {
                                it.removeAt(currentCombinationIndex)
                            }
                            currentCombinationIndex =
                                (originalCombinationIndex - 1).coerceAtLeast(0)
                            originalCombinationIndex = currentCombinationIndex
                            // 恢复原始搭配状态
                            wallpaperBitmap = savedWallpaperBitmap
                            wallpaperOffset = savedWallpaperOffset
                            wallpaperScale = savedWallpaperScale
                            appearance = savedAppearance
                            // 恢复原始搭配的快照
                            if (originalSnapshot != null) {
                                combinations = combinations.toMutableList().also {
                                    val idx = currentCombinationIndex
                                    if (idx in it.indices) {
                                        it[idx] = it[idx].copy(snapshot = originalSnapshot)
                                    }
                                }
                                customizeSnapshot = originalSnapshot
                            }
                        }
                        isNewCombinationCreated = false
                    }
                    customizeExitScale.snapTo(customizeExitTargetScale)
                    customizeExitAlpha.snapTo(1f)
                    isCustomizeExiting = true
                }
            }
            val applyCustomize: () -> Unit = {
                coroutineScope.launch {
                    isNewCombinationCreated = false
                    // 持久化当前搭配到磁盘（在 IO 线程异步执行，不阻塞 UI）
                    val bitmap = wallpaperBitmap
                    val combId = combinations.getOrNull(currentCombinationIndex)?.id ?: 0L
                    // 截取当前 MainActivity 快照（包含课表+新壁纸）作为卡片预览（仅内存，不持久化）
                    val capturedSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                    val saveJob = launch(Dispatchers.IO) {
                        if (bitmap != null) {
                            wallpaperRepository.saveCombinationWallpaper(combId, bitmap)
                        }
                        wallpaperRepository.saveCombinationState(
                            combId,
                            wallpaperOffset.x,
                            wallpaperOffset.y,
                            wallpaperScale
                        )
                        wallpaperRepository.saveCombinationCardBlur(
                            combId,
                            appearance.cardBlurRadius
                        )
                        wallpaperRepository.saveCombinationCardAlpha(combId, appearance.cardAlpha)
                        wallpaperRepository.saveCombinationCardHeight(combId, appearance.cardHeight)
                        wallpaperRepository.saveCombinationCardCornerRadius(
                            combId,
                            appearance.cardCornerRadius
                        )
                        wallpaperRepository.saveCombinationWallpaperBrightness(
                            combId,
                            appearance.wallpaperBrightness
                        )
                        wallpaperRepository.saveCombinationShowBreakDividers(
                            combId,
                            appearance.showBreakDividers
                        )
                        wallpaperRepository.saveCombinationCardContentAlignment(
                            combId,
                            appearance.cardContentAlignment
                        )
                        wallpaperRepository.setCurrentCombinationId(combId)
                    }
                    // 同步到当前搭配对象（快照仅存内存）
                    val idx = currentCombinationIndex
                    if (idx in combinations.indices) {
                        combinations = combinations.toMutableList().also {
                            it[idx] = it[idx].copy(
                                bitmap = bitmap,
                                offset = wallpaperOffset,
                                scale = wallpaperScale,
                                snapshot = capturedSnapshot,
                                cardBlurRadius = appearance.cardBlurRadius,
                                cardAlpha = appearance.cardAlpha,
                                cardHeight = appearance.cardHeight,
                                cardCornerRadius = appearance.cardCornerRadius,
                                wallpaperBrightness = appearance.wallpaperBrightness,
                                showBreakDividers = appearance.showBreakDividers,
                                cardContentAlignment = appearance.cardContentAlignment
                            )
                        }
                    }
                    // 更新已保存快照，避免退出时回退
                    savedWallpaperBitmap = bitmap
                    savedWallpaperOffset = wallpaperOffset
                    savedWallpaperScale = wallpaperScale
                    savedAppearance = appearance
                    // 等待磁盘保存完成
                    saveJob.join()
                    // 开始退出动画
                    isApplyingCustomize = true
                    // 从当前开洞大小（0.75）开始放大到全屏，而非从卡片预览大小（0.65）
                    customizeExitScale.snapTo(cutoutMainScale.value)
                    customizeExitAlpha.snapTo(1f)
                    isCustomizeExiting = true
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = customizeExitAlpha.value
                    }
            ) {
                CustomizeScheduleScreen(
                    snapshot = customizeSnapshot,
                    screenCornerRadius = screenCornerRadius,
                    onDismiss = dismissCustomize,
                    onApply = applyCustomize,
                    onCustomize = { isWindowCutoutActive = true },
                    onCancelCutout = { isWindowCutoutActive = false },
                    onPickWallpaper = {
                        wallpaperPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onCreateNewCombination = {
                        if (combinations.size >= 5) {
                            android.widget.Toast.makeText(
                                context,
                                "最多创建5个搭配",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@CustomizeScheduleScreen
                        }
                        isNewCombinationCreated = true
                        // 在插入新搭配之前，保存原始快照（customizeSnapshot 保存的是当前搭配的快照）
                        val savedOrigSnapshot = customizeSnapshot
                        // 创建新搭配：持久化并插入到 index 0（加号卡右侧），新搭配无背景
                        val newId = wallpaperRepository.addCombination()
                        val newComb = com.haooz.chedule.data.Combination(
                            id = newId,
                            bitmap = null,
                            offset = Offset.Zero,
                            scale = 1f
                        )
                        // 新搭配插入到列表头部，永远在加号卡右侧
                        combinations = listOf(newComb) + combinations
                        currentCombinationIndex = 0
                        newCombinationIndex = 0
                        // 原始搭配被推到 index 1，更新原始索引
                        originalCombinationIndex += 1
                        // 保存原搭配状态，快照捕获后恢复
                        val savedOrigWp = originalWallpaperBitmap
                        val savedOrigOf = originalWallpaperOffset
                        val savedOrigSc = originalWallpaperScale
                        val savedOrigAppear = originalAppearance
                        // 新搭配无背景，临时清除壁纸以截取无壁纸快照
                        wallpaperBitmap = null
                        wallpaperOffset = Offset.Zero
                        wallpaperScale = 1f
                        appearance = com.haooz.chedule.data.AppearanceConfig()
                        // 同步更新 original* 让 MainScheduleScreen 渲染空状态
                        originalWallpaperBitmap = null
                        originalWallpaperOffset = Offset.Zero
                        originalWallpaperScale = 1f
                        originalAppearance = com.haooz.chedule.data.AppearanceConfig()
                        coroutineScope.launch {
                            delay(150.milliseconds)
                            val newSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                            customizeSnapshot = newSnapshot
                            combinations = combinations.toMutableList().also {
                                if (it.isNotEmpty()) it[0] = it[0].copy(snapshot = newSnapshot)
                            }
                            // 恢复原搭配状态
                            wallpaperBitmap = savedOrigWp
                            wallpaperOffset = savedOrigOf
                            wallpaperScale = savedOrigSc
                            appearance = savedOrigAppear
                            originalWallpaperBitmap = savedOrigWp
                            originalWallpaperOffset = savedOrigOf
                            originalWallpaperScale = savedOrigSc
                            originalAppearance = savedOrigAppear
                            // 恢复原始搭配的快照
                            if (savedOrigSnapshot != null) {
                                combinations = combinations.toMutableList().also {
                                    val origIdx = originalCombinationIndex
                                    if (origIdx in it.indices) {
                                        it[origIdx] = it[origIdx].copy(snapshot = savedOrigSnapshot)
                                    }
                                }
                            }
                            // 触发自动进入编辑模式
                            isWindowCutoutActive = true
                            pendingEnterCutout = true
                        }
                    },
                    pendingEnterCutout = pendingEnterCutout,
                    onCutoutEntered = { pendingEnterCutout = false },
                    combinations = combinations,
                    currentCombinationIndex = currentCombinationIndex,
                    onCombinationPageChange = { newPage ->
                        // pager 页面切换：page 0 是"+"卡，page 1..n 对应 combinations[0..n-1]
                        val combIdx = newPage - 1
                        if (combIdx in combinations.indices && combIdx != currentCombinationIndex) {
                            // 取消模糊快照防抖任务，避免切换后错误捕获
                            blurSnapshotJob?.cancel()
                            currentCombinationIndex = combIdx
                            val c = combinations[combIdx]
                            wallpaperBitmap = c.bitmap
                            wallpaperOffset = c.offset
                            // 计算最小缩放比例，确保壁纸填满屏幕不露出底部背景
                            val bmp = c.bitmap
                            val minScale = if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                                val fitScale = minOf(screenWPx / bmp.width, screenHPx / bmp.height)
                                val coverScale =
                                    maxOf(screenWPx / bmp.width, screenHPx / bmp.height)
                                if (fitScale > 0f) coverScale / fitScale else 1f
                            } else 1f
                            wallpaperScale = maxOf(c.scale, minScale)
                            appearance = com.haooz.chedule.data.AppearanceConfig.fromCombination(c)
                            // 同步更新 savedWallpaper*：编辑取消时需回退到"当前查看搭配"的未编辑状态，
                            // 切换搭配时必须同步，否则取消编辑会闪回原搭配
                            savedWallpaperBitmap = c.bitmap
                            savedWallpaperOffset = c.offset
                            savedWallpaperScale = wallpaperScale
                            savedAppearance =
                                com.haooz.chedule.data.AppearanceConfig.fromCombination(c)
                            // 如果已有快照，立即更新 customizeSnapshot（无延迟）
                            if (c.snapshot != null) {
                                customizeSnapshot = c.snapshot
                            }
                            // 后台捕获新当前搭配的快照及下一个相邻搭配的快照
                            coroutineScope.launch {
                                delay(150.milliseconds)
                                // 捕获当前搭配快照（如尚未有）
                                if (combinations.getOrNull(combIdx)?.snapshot == null) {
                                    val snap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                    combinations = combinations.toMutableList().also {
                                        if (combIdx < it.size) it[combIdx] =
                                            it[combIdx].copy(snapshot = snap)
                                    }
                                    // 仅当用户仍停留在该搭配时才更新 customizeSnapshot，
                                    // 避免快速切换时旧协程覆盖为非当前搭配的快照
                                    if (combIdx == currentCombinationIndex) {
                                        customizeSnapshot = snap
                                    }
                                }
                            }
                        }
                    },
                    onDeleteCombination = { combId ->
                        // 删除指定搭配：从磁盘移除并从列表移除
                        coroutineScope.launch {
                            launch(Dispatchers.IO) {
                                wallpaperRepository.deleteCombination(combId)
                            }
                            val deleteIdx = combinations.indexOfFirst { it.id == combId }
                            if (deleteIdx >= 0) {
                                val list = combinations.toMutableList()
                                list.removeAt(deleteIdx)
                                combinations = list
                                // 调整当前搭配索引
                                if (combinations.isEmpty()) {
                                    // 删光后立即关闭搭配页
                                    currentCombinationIndex = 0
                                    isApplyingCustomize = false
                                    customizeExitScale.snapTo(customizeExitTargetScale)
                                    customizeExitAlpha.snapTo(1f)
                                    isCustomizeExiting = true
                                } else {
                                    // 若删除的是当前搭配，切换到第一个
                                    currentCombinationIndex =
                                        if (deleteIdx == 0) 0 else (deleteIdx - 1).coerceAtLeast(0)
                                    val c = combinations[currentCombinationIndex]
                                    wallpaperBitmap = c.bitmap
                                    wallpaperOffset = c.offset
                                    // 计算最小缩放比例，确保壁纸填满屏幕不露出底部背景
                                    val bmpDel = c.bitmap
                                    val minScaleDel =
                                        if (bmpDel != null && bmpDel.width > 0 && bmpDel.height > 0) {
                                            val fitScale = minOf(
                                                screenWPx / bmpDel.width,
                                                screenHPx / bmpDel.height
                                            )
                                            val coverScale = maxOf(
                                                screenWPx / bmpDel.width,
                                                screenHPx / bmpDel.height
                                            )
                                            if (fitScale > 0f) coverScale / fitScale else 1f
                                        } else 1f
                                    wallpaperScale = maxOf(c.scale, minScaleDel)
                                    savedWallpaperBitmap = c.bitmap
                                    savedWallpaperOffset = c.offset
                                    savedWallpaperScale = wallpaperScale
                                    savedAppearance =
                                        com.haooz.chedule.data.AppearanceConfig.fromCombination(c)
                                    appearance =
                                        com.haooz.chedule.data.AppearanceConfig.fromCombination(c)
                                    // 无条件更新原始搭配值，确保 MainScheduleScreen 显示正确
                                    originalWallpaperBitmap = c.bitmap
                                    originalWallpaperOffset = c.offset
                                    originalWallpaperScale = wallpaperScale
                                    originalAppearance =
                                        com.haooz.chedule.data.AppearanceConfig.fromCombination(c)
                                    // 若删除的是原始搭配，更新原始追踪器到新的当前搭配
                                    if (deleteIdx == originalCombinationIndex) {
                                        originalCombinationIndex = currentCombinationIndex
                                    } else if (deleteIdx < originalCombinationIndex) {
                                        // 删除的在原始之前，原始索引前移
                                        originalCombinationIndex =
                                            (originalCombinationIndex - 1).coerceAtLeast(0)
                                    }
                                    // 同步当前搭配到磁盘
                                    wallpaperRepository.setCurrentCombinationId(c.id)
                                    // 更新实时快照
                                    delay(150.milliseconds)
                                    customizeSnapshot =
                                        screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                }
                            }
                        }
                    },
                    exitScale = customizeExitScale.value,
                    isExiting = isCustomizeExiting,
                    isApplying = isApplyingCustomize,
                    isApplyingCustomize = isApplyingCustomize,
                    onRevertWallpaper = {
                        // 如果本次会话创建了新搭配，取消时需删除它并回退到原始搭配
                        if (isNewCombinationCreated) {
                            val newComb = combinations.getOrNull(currentCombinationIndex)
                            if (newComb != null) {
                                // 删除新搭配的持久化数据
                                coroutineScope.launch(Dispatchers.IO) {
                                    wallpaperRepository.deleteCombination(newComb.id)
                                }
                                // 从列表移除新搭配
                                combinations = combinations.toMutableList().also {
                                    it.removeAt(currentCombinationIndex)
                                }
                                // 回退到原始搭配（新搭配插入在头部，原始搭配 index +1）
                                currentCombinationIndex =
                                    (originalCombinationIndex - 1).coerceAtLeast(0)
                                originalCombinationIndex = currentCombinationIndex
                                // 恢复原始搭配的快照
                                if (originalSnapshot != null) {
                                    combinations = combinations.toMutableList().also {
                                        val idx = currentCombinationIndex
                                        if (idx in it.indices) {
                                            it[idx] = it[idx].copy(snapshot = originalSnapshot)
                                        }
                                    }
                                    customizeSnapshot = originalSnapshot
                                }
                            }
                            isNewCombinationCreated = false
                        }
                        wallpaperBitmap = savedWallpaperBitmap
                        wallpaperOffset = savedWallpaperOffset
                        wallpaperScale = savedWallpaperScale
                        appearance = savedAppearance
                        // 同步恢复 combinations[originalCombinationIndex] 的编辑字段，
                        // 避免退出时将旧值写入已滑动到的其他搭配
                        val idx = originalCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = savedAppearance.applyToCombination(it[idx]).copy(
                                    bitmap = savedWallpaperBitmap,
                                    offset = savedWallpaperOffset,
                                    scale = savedWallpaperScale
                                )
                            }
                        }
                        blurSnapshotJob?.cancel()
                    },
                    wallpaperBitmap = wallpaperBitmap,
                    wallpaperOffset = wallpaperOffset,
                    wallpaperScale = wallpaperScale,
                    onWallpaperOffsetChange = {
                        wallpaperOffset = it
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also { list ->
                                list[idx] = list[idx].copy(offset = it)
                            }
                        }
                    },
                    onWallpaperScaleChange = {
                        wallpaperScale = it
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also { list ->
                                list[idx] = list[idx].copy(scale = it)
                            }
                        }
                    },
                    onCutoutCenterChange = { cutoutCenterYRatio = it },
                    onSheetOffsetChange = { sheetOffsetY = it },
                    onEffectValueChange = { blur, alpha ->
                        appearance = appearance.copy(cardBlurRadius = blur, cardAlpha = alpha)
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(cardBlurRadius = blur, cardAlpha = alpha)
                            }
                        }
                    },
                    initialCardBlurRadius = appearance.cardBlurRadius,
                    initialCardAlpha = appearance.cardAlpha,
                    onWallpaperBrightnessChange = { brightness ->
                        appearance = appearance.copy(wallpaperBrightness = brightness)
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(wallpaperBrightness = brightness)
                            }
                        }
                    },
                    initialWallpaperBrightness = appearance.wallpaperBrightness,
                    onShowBreakDividersChange = { show ->
                        appearance = appearance.copy(showBreakDividers = show)
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(showBreakDividers = show)
                            }
                        }
                    },
                    initialShowBreakDividers = appearance.showBreakDividers,
                    onCardContentAlignmentChange = { alignment ->
                        appearance = appearance.copy(cardContentAlignment = alignment)
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(cardContentAlignment = alignment)
                            }
                        }
                    },
                    initialCardContentAlignment = appearance.cardContentAlignment,
                    hasWallpaper = wallpaperBitmap != null,
                    onCustomizeValueChange = { height, cornerRadius ->
                        appearance =
                            appearance.copy(cardHeight = height, cardCornerRadius = cornerRadius)
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(
                                    cardHeight = height,
                                    cardCornerRadius = cornerRadius
                                )
                            }
                        }
                    },
                    initialCardHeight = appearance.cardHeight,
                    initialCardCornerRadius = appearance.cardCornerRadius,
                )
            }
        }

        // 搭配快照遮罩：捕获相邻快照时挡住屏幕闪烁
        if (snapshotCoverBitmap != null) {
            Image(
                bitmap = snapshotCoverBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // 退出动画：真实界面从卡片大小缩放回全屏，搭配界面淡出
        LaunchedEffect(isCustomizeExiting) {
            if (isCustomizeExiting && customizeSnapshot != null) {
                kotlinx.coroutines.coroutineScope {
                    launch {
                        customizeExitScale.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                500,
                                easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
                            )
                        )
                    }
                    // 应用时不淡出搭配页面，动画结束后直接消失
                    if (!isApplyingCustomize) {
                        launch {
                            customizeExitAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    500,
                                    easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
                                )
                            )
                        }
                    }
                    // 应用时，主界面从开洞大小（0.75）放大到全屏
                    if (isApplyingCustomize) {
                        launch {
                            cutoutMainScale.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    500,
                                    easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
                                )
                            )
                        }
                    }
                }
                // 动画完成，真正关闭
                isCustomizeExiting = false
                showCustomizePage = false
                customizeSnapshot = null
                isWindowCutoutActive = false
                if (!isApplyingCustomize) {
                    // 退出（非应用）：恢复 live 变量，并还原 combinations 列表中被 callback 修改的条目
                    wallpaperBitmap = originalWallpaperBitmap
                    wallpaperOffset = originalWallpaperOffset
                    wallpaperScale = originalWallpaperScale
                    appearance = originalAppearance
                    currentCombinationIndex = originalCombinationIndex
                    // 还原 combinations 列表中被 onCardSelfPermissionChange / onShowBreakDividersChange 等 callback 修改的值
                    val restoreIdx = originalCombinationIndex
                    if (restoreIdx in combinations.indices) {
                        combinations = combinations.toMutableList().also {
                            it[restoreIdx] = it[restoreIdx].copy(
                                showBreakDividers = originalAppearance.showBreakDividers,
                                cardContentAlignment = originalAppearance.cardContentAlignment
                            )
                        }
                    }
                }
                // 应用时保留当前壁纸状态（已持久化到磁盘）
                isApplyingCustomize = false
                windowInsetsController?.isAppearanceLightStatusBars = true
                windowInsetsController?.isAppearanceLightNavigationBars = true
            }
        }
        // 课程详情页（不受缩放影响）
        if (showDetail) {
            val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
            val classStartTime by viewModel.classStartTime.collectAsState()
            CourseDetailScreen(
                courses = detailCourses,
                cardLeft = detailCardLeft,
                cardTop = detailCardTop,
                cardWidth = detailCardWidth,
                cardHeight = detailCardHeight,
                screenWidth = windowInfo.containerSize.width.toFloat(),
                screenHeight = windowInfo.containerSize.height.toFloat(),
                screenCornerRadius = screenCornerRadius,
                cardSnapshot = detailSnapshot,
                fromToday = detailFromToday,
                sectionTimes = sectionTimes,
                classStartTime = classStartTime,
                onBackStart = {
                    coroutineScope.launch {
                        launch {
                            backgroundScale.animateTo(
                                1f,
                                animationSpec = tween(350, easing = OobeCubicOutEasing)
                            )
                        }
                        launch {
                            managePageBlurRadius.animateTo(
                                0f,
                                animationSpec = tween(350, easing = OobeCubicOutEasing)
                            )
                        }
                    }
                },
                onBack = {
                    showDetail = false
                    hiddenCourseIds = emptySet()
                }
            )
        }
        // 切换课表页
        if (showSwitchSchedule) {
            val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            val screenWidth = windowInfo.containerSize.width.toFloat()
            val screenHeight = windowInfo.containerSize.height.toFloat()
            val p = switchAnimProgress.value
            val switchPageScale = remember { Animatable(1f) }
            val switchPageBlur = remember { Animatable(5f) }
            // 切换课表页始终渲染（底层，截取快照期间隐藏）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (switchPageBlur.value > 0.01f) {
                            val px = switchPageBlur.value * density.density
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                px, px, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        } else {
                            renderEffect = null
                        }
                    }
                    .graphicsLayer {
                        alpha = if (switchCapturingSnapshot) 0f else 1f
                        scaleX = switchPageScale.value
                        scaleY = switchPageScale.value
                    }
            ) {
                SwitchScheduleScreen(
                    onBack = { switchPageBitmap ->
                        if (switchAnimRunning && !switchAnimForward) return@SwitchScheduleScreen
                        val wasForward = switchAnimForward
                        switchAnimForward = false
                        switchAnimRunning = true
                        switchAnimJob?.cancel()
                        switchAnimJob = coroutineScope.launch {
                            if (scheduleChanged && !wasForward) {
                                scheduleChanged = false
                                withFrameNanos { }
                                withFrameNanos { }
                                mainContentSnapshot = try {
                                    screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                } catch (_: Exception) {
                                    mainContentSnapshot
                                }
                            } else {
                                scheduleChanged = false
                            }
                            val currentBounds = switchCurrentCardBounds
                            val screenBitmap = switchPageBitmap ?: mainContentSnapshot
                            if (currentBounds != null && screenBitmap != null) {
                                switchScreenSnapshot = screenBitmap
                                switchCardBounds = currentBounds
                                switchCardSnapshot = try {
                                    val x = currentBounds.left.toInt()
                                        .coerceIn(0, screenBitmap.width - 1)
                                    val y = currentBounds.top.toInt()
                                        .coerceIn(0, screenBitmap.height - 1)
                                    val w = currentBounds.width.toInt()
                                        .coerceIn(1, screenBitmap.width - x)
                                    val h = currentBounds.height.toInt()
                                        .coerceIn(1, screenBitmap.height - y)
                                    android.graphics.Bitmap.createBitmap(screenBitmap, x, y, w, h)
                                } catch (_: Exception) {
                                    null
                                }
                                val currentProgress = switchAnimProgress.value
                                val remainingDuration =
                                    ((1f - currentProgress) * 560).toInt().coerceAtLeast(1)
                                launch {
                                    switchPageScale.animateTo(
                                        1.08f,
                                        animationSpec = tween(
                                            remainingDuration,
                                            easing = OobeQuartOutEasing
                                        )
                                    )
                                }
                                launch {
                                    switchPageBlur.animateTo(
                                        5f,
                                        animationSpec = tween(
                                            remainingDuration,
                                            easing = OobeQuartOutEasing
                                        )
                                    )
                                }
                                switchAnimProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = remainingDuration,
                                        easing = OobeQuartOutEasing
                                    )
                                )
                            }
                            showSwitchSchedule = false
                            switchScreenSnapshot = null
                            switchCardSnapshot = null
                            switchCardBounds = null
                            switchCurrentCardBounds = null
                            switchAnimRunning = false
                        }
                    },
                    onScheduleChanged = {
                        viewModel.reloadCourses()
                        settingsViewModel.refreshSettings()
                        scheduleChanged = true
                    },
                    onCardClick = { bounds ->
                        switchCardBounds = bounds
                    },
                    onCardSnapshot = { screenBitmap, cardBitmap, bounds ->
                        if (switchAnimJob?.isActive == true) return@SwitchScheduleScreen
                        switchAnimForward = false
                        switchAnimRunning = true
                        switchAnimJob?.cancel()
                        switchAnimJob = coroutineScope.launch {
                            if (scheduleChanged) {
                                scheduleChanged = false
                                withFrameNanos { }
                                withFrameNanos { }
                                mainContentSnapshot = try {
                                    screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                } catch (_: Exception) {
                                    mainContentSnapshot
                                }
                            }
                            switchScreenSnapshot = screenBitmap
                            switchCardSnapshot = cardBitmap
                            switchCardBounds = bounds
                            val currentProgress = switchAnimProgress.value
                            val remainingDuration =
                                ((1f - currentProgress) * 560).toInt().coerceAtLeast(1)
                            launch {
                                switchPageScale.animateTo(
                                    1.08f,
                                    animationSpec = tween(
                                        remainingDuration,
                                        easing = OobeQuartOutEasing
                                    )
                                )
                            }
                            launch {
                                switchPageBlur.animateTo(
                                    5f,
                                    animationSpec = tween(
                                        remainingDuration,
                                        easing = OobeQuartOutEasing
                                    )
                                )
                            }
                            switchAnimProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = remainingDuration,
                                    easing = OobeQuartOutEasing
                                )
                            )
                            switchAnimRunning = false
                            showSwitchSchedule = false
                            switchScreenSnapshot = null
                            switchCardSnapshot = null
                            switchCardBounds = null
                        }
                    },
                    onCurrentCardBounds = { bounds ->
                        switchCurrentCardBounds = bounds
                    },
                    onScreenReady = { screenBitmap, cardBounds ->
                        if (switchPendingReverse) {
                            switchPendingReverse = false
                            switchAnimForward = true
                            switchAnimRunning = true
                            switchAnimJob?.cancel()
                            val cardBoundsInScreen = androidx.compose.ui.geometry.Rect(
                                left = switchContentRootX + cardBounds.left,
                                top = switchContentRootY + cardBounds.top,
                                right = switchContentRootX + cardBounds.right,
                                bottom = switchContentRootY + cardBounds.bottom
                            )
                            val cardSnap = try {
                                val x = cardBounds.left.toInt().coerceIn(0, screenBitmap.width - 1)
                                val y = cardBounds.top.toInt().coerceIn(0, screenBitmap.height - 1)
                                val w = cardBounds.width.toInt().coerceIn(1, screenBitmap.width - x)
                                val h =
                                    cardBounds.height.toInt().coerceIn(1, screenBitmap.height - y)
                                android.graphics.Bitmap.createBitmap(screenBitmap, x, y, w, h)
                            } catch (_: Exception) {
                                null
                            }
                            switchAnimJob = coroutineScope.launch {
                                switchAnimProgress.snapTo(1f)
                                switchPageScale.snapTo(1.08f)
                                switchPageBlur.snapTo(5f)
                                switchReturnBgScrim.snapTo(0.4f)
                                switchCapturingSnapshot = false
                                switchScreenSnapshot = screenBitmap
                                switchCardBounds = cardBoundsInScreen
                                switchCardSnapshot = cardSnap
                                val remainingDuration = 350
                                val morphExitEase = CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)
                                launch {
                                    switchPageScale.animateTo(
                                        1f,
                                        animationSpec = tween(
                                            remainingDuration,
                                            easing = OobeCubicOutEasing
                                        )
                                    )
                                }
                                launch {
                                    switchPageBlur.animateTo(
                                        0f,
                                        animationSpec = tween(
                                            remainingDuration,
                                            easing = OobeCubicOutEasing
                                        )
                                    )
                                }
                                launch {
                                    switchReturnBgScrim.animateTo(
                                        0f,
                                        animationSpec = tween(
                                            remainingDuration,
                                            easing = morphExitEase
                                        )
                                    )
                                }
                                switchAnimProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = remainingDuration,
                                        easing = OobeCubicOutEasing
                                    )
                                )
                                switchScreenSnapshot = null
                                switchCardSnapshot = null
                                switchCardBounds = null
                                switchAnimRunning = false
                            }
                        }
                    },
                    onContentOffset = { x, y ->
                        switchContentRootX = x
                        switchContentRootY = y
                    },
                    pageScale = 1f,
                    initialScheduleNames = scheduleViewModel.scheduleNames.collectAsState().value,
                    initialCurrentScheduleId = scheduleViewModel.currentScheduleName.collectAsState().value,
                    initialScheduleSummaries = scheduleViewModel.scheduleSummaries.collectAsState().value
                )
            }
            // 动画覆盖层（顶层）
            if (switchScreenSnapshot != null) {
                val sBounds = switchCardBounds
                val cLeft: Float
                val cTop: Float
                val cWidth: Float
                val cHeight: Float
                if (sBounds != null) {
                    cLeft = sBounds.left + (0f - sBounds.left) * p
                    cTop = sBounds.top + (0f - sBounds.top) * p
                    cWidth = sBounds.width + (screenWidth - sBounds.width) * p
                    cHeight = sBounds.height + (screenHeight - sBounds.height) * p
                } else {
                    cLeft = 0f; cTop = 0f; cWidth = screenWidth; cHeight = screenHeight
                }
                val startRadius = with(density) { 20.dp.toPx() }
                val cRadius =
                    with(density) { (startRadius + (screenCornerRadius - startRadius) * p).toDp() }
                // 压暗遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDark) ComposeColor(0xFF2C2C2C).copy(
                                alpha = (p * 0.5f).coerceIn(
                                    0f,
                                    0.5f
                                )
                            )
                            else ComposeColor.Black.copy(alpha = (p * 0.5f).coerceIn(0f, 0.5f))
                        )
                )
                // 展开的卡片区域
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { cLeft.toDp() },
                            y = with(density) { cTop.toDp() }
                        )
                        .size(
                            width = with(density) { cWidth.toDp() },
                            height = with(density) { cHeight.toDp() }
                        )
                        .clip(RoundedRectangle(cRadius))
                        .background(MiuixTheme.colorScheme.background)
                ) {
                    // 卡片快照（淡出，保持原始大小）
                    if (switchCardSnapshot != null) {
                        Image(
                            bitmap = switchCardSnapshot!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .clip(RoundedRectangle(20.dp))
                                .graphicsLayer { alpha = (1f - p * 2f).coerceIn(0f, 1f) },
                            contentScale = ContentScale.None
                        )
                    }
                    // 主内容快照（淡入）
                    if (mainContentSnapshot != null) {
                        Image(
                            bitmap = mainContentSnapshot!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = ((p - 0.2f) / 0.7f).coerceIn(0f, 1f) },
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter
                        )
                    }
                }
            }
        }

        // 排班模式加载遮罩（全屏覆盖，包括导航栏和状态栏）
        AnimatedVisibility(
            visible = showShiftLoading,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(100))
        ) {
            LaunchedEffect(Unit) {
                delay(100.milliseconds)
                if (isExitingShift) {
                    shiftViewModel.exitShiftMode()
                    selectedTab = 0
                } else {
                    shiftViewModel.enterShiftMode()
                }
                delay(500.milliseconds)
                showShiftLoading = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
                        size = 30.dp,
                        strokeWidth = 2.8.dp,
                        orbitingDotSize = 3.2.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "切换中",
                        style = MiuixTheme.textStyles.body1.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    navBarStyle: String,
    scrollBehavior: com.haooz.chedule.ui.components.SharedScrollBehavior? = null,
) {
    if (liquidGlassBackdrop == null) return
    val appStyle = rememberAppStyle()
    if (appStyle != "liquidglass") return
    val isTabletLiquidGlass = navBarStyle == "rail"

    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
    ) {
        CollapsibleTopAppBar(
            title = "我的",
            largeTitle = "我的",
            modifier = Modifier.zIndex(1f),
            scrollBehavior = scrollBehavior,
            startAction = if (isTabletLiquidGlass) {
                { _, _ ->
                    Text(
                        text = "我的",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            } else null,
        )
    }
}

@Composable
private fun TodayTopBar(
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    navBarStyle: String,
    currentDayOfWeek: Int,
    isToday: Boolean = true,
    onBackToToday: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    scrollBehavior: com.haooz.chedule.ui.components.SharedScrollBehavior? = null,
) {
    if (liquidGlassBackdrop == null) return
    val appStyle = rememberAppStyle()
    if (appStyle != "liquidglass") return
    val isTabletLiquidGlass = navBarStyle == "rail"
    val dayOfWeekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val dayOfWeekName = if (currentDayOfWeek in 1..7) dayOfWeekNames[currentDayOfWeek - 1] else ""
    val titleText = if (isToday) "今天是$dayOfWeekName" else dayOfWeekName

    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
    ) {
        CollapsibleTopAppBar(
            title = titleText,
            largeTitle = titleText,
            modifier = Modifier.zIndex(1f),
            scrollBehavior = scrollBehavior,
            startAction = if (isTabletLiquidGlass) {
                { _, _ ->
                    Text(
                        text = titleText,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            } else {
                { backdropAlpha, shadowAlpha ->
                    AnimatedVisibility(
                        visible = !isToday,
                        enter = fadeIn(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(120))
                    ) {
                        LiquidTopBarButton(
                            onClick = onBackToToday,
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.Medium.Reset,
                            contentDescription = "返回今天",
                            iconSize = 22.dp,
                            modifier = Modifier.padding(start = 4.dp),
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                        )
                    }
                }
            },
            endAction = { backdropAlpha, shadowAlpha ->
                LiquidTopBarButton(
                    onClick = onMoreClick,
                    backdrop = liquidGlassBackdrop,
                    icon = MiuixIcons.More,
                    contentDescription = "更多",
                    iconSize = 20.dp,
                    modifier = Modifier.padding(end = 4.dp),
                    backdropAlpha = backdropAlpha,
                    shadowAlpha = shadowAlpha,
                )
            },
        )
    }
}
