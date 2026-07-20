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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.Course
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.components.LongPressCustomizeButton
import com.haooz.chedule.ui.components.ScheduleBottomBar
import com.haooz.chedule.ui.components.ScheduleTopBar
import com.haooz.chedule.ui.components.ShareImportDialog
import com.haooz.chedule.ui.components.UpdateDialog
import com.haooz.chedule.ui.components.liquidglass.LiquidAddButton
import com.haooz.chedule.ui.components.liquidglass.LiquidGlassDropdownMenu
import com.haooz.chedule.ui.components.liquidglass.LiquidGlassDropdownMenuItem
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.oobe.OobeCubicOutEasing
import com.haooz.chedule.ui.oobe.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.screens.CourseDetailScreen
import com.haooz.chedule.ui.screens.CustomizeScheduleScreen
import com.haooz.chedule.ui.screens.MainScheduleScreen
import com.haooz.chedule.ui.screens.SettingsScreen
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TodayScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton

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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import androidx.compose.ui.draw.blur
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Reset
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
        var cachedWallpaperOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero
        @Volatile
        var cachedWallpaperScale: Float = 1f
        @Volatile
        var cachedCourseCardBlur: Float = 0f
        @Volatile
        var cachedCourseCardAlpha: Float = 0.15f
        @Volatile
        var cachedCourseCardHeight: Float = 54f
        @Volatile
        var cachedCourseCardCornerRadius: Float = 8f
        @Volatile
        var cachedWallpaperBrightness: Float = 0f
        @Volatile
        var cachedShowBreakDividers: Boolean = true
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

    // 壁纸是否已首次出现（用于控制淡入动画只播放一次）
    var wallpaperHasAppeared by mutableStateOf(false)

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
                    cachedWallpaperOffset = androidx.compose.ui.geometry.Offset(
                        repo.getCombinationOffsetX(currentIdValue),
                        repo.getCombinationOffsetY(currentIdValue)
                    )
                    cachedWallpaperScale = repo.getCombinationScale(currentIdValue)
                    cachedCourseCardBlur = repo.getCombinationCardBlur(currentIdValue)
                    cachedCourseCardAlpha = repo.getCombinationCardAlpha(currentIdValue)
                    cachedCourseCardHeight = repo.getCombinationCardHeight(currentIdValue)
                    cachedCourseCardCornerRadius = repo.getCombinationCardCornerRadius(currentIdValue)
                    cachedWallpaperBrightness = repo.getCombinationWallpaperBrightness(currentIdValue)
                    cachedShowBreakDividers = repo.getCombinationShowBreakDividers(currentIdValue)
                }
            } catch (_: Exception) {}
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
        if (intent?.getBooleanExtra(CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS, false) == true) {
            intent.removeExtra(CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS)
            startActivity(android.content.Intent(this, CourseReminderActivity::class.java))
        }
    }

    private fun extractIntentData(intent: android.content.Intent?) {
        when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> {
                shareIntentUri = intent.data
                shareIntentAction = android.content.Intent.ACTION_VIEW
            }
            android.content.Intent.ACTION_SEND -> {
                shareIntentUri = intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                shareIntentAction = android.content.Intent.ACTION_SEND
            }
        }
    }
}

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
    val settingsScrollBehavior = MiuixScrollBehavior()
    var todayScrollY by remember { mutableIntStateOf(0) }
    val todayScrollBehavior = MiuixScrollBehavior()

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
    val liquidGlassBackdrop = if (appStyle == "liquidglass") {
        com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    } else null
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
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val currentDensity = LocalDensity.current.density
    val navBarStyle = run {
        val shortestSidePx = minOf(windowInfo.containerSize.width, windowInfo.containerSize.height)
        val shortestSideDp = shortestSidePx / currentDensity
        if (shortestSideDp > 500f) "rail" else "standard"
    }
    val railState = if (navBarStyle == "rail") rememberNavigationRailState() else null
    val railPaddingStart by animateDpAsState(
        targetValue = if (appStyle == "liquidglass" && liquidGlassBackdrop != null && navBarStyle == "rail") {
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
    var showCardOverlay by remember { mutableStateOf(false) }
    var overlayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var overlayLeft by remember { mutableFloatStateOf(0f) }
    var overlayTop by remember { mutableFloatStateOf(0f) }
    var overlayWidth by remember { mutableFloatStateOf(0f) }
    var overlayHeight by remember { mutableFloatStateOf(0f) }

    // 长按空白区域"自定义课表"按钮状态
    var showLongPressButton by remember { mutableStateOf(false) }
    var showLongPressOverlay by remember { mutableStateOf(false) }

    // 自定义课表页面状态
    var showCustomizePage by remember { mutableStateOf(false) }
    var customizeSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var snapshotCoverBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var courseCardBlur by remember { mutableFloatStateOf(MainActivity.cachedCourseCardBlur) }
    var courseCardAlpha by remember { mutableFloatStateOf(MainActivity.cachedCourseCardAlpha) }
    var courseCardHeight by remember { mutableFloatStateOf(MainActivity.cachedCourseCardHeight) }
    var courseCardCornerRadius by remember { mutableFloatStateOf(MainActivity.cachedCourseCardCornerRadius) }
    var wallpaperBrightness by remember { mutableFloatStateOf(MainActivity.cachedWallpaperBrightness) }
    var showBreakDividers by remember { mutableStateOf(MainActivity.cachedShowBreakDividers) }
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
    var savedCourseCardBlur by remember { mutableFloatStateOf(0f) }
    var savedCourseCardAlpha by remember { mutableFloatStateOf(0.15f) }
    var savedCourseCardHeight by remember { mutableFloatStateOf(54f) }
    var savedCourseCardCornerRadius by remember { mutableFloatStateOf(8f) }
    var savedWallpaperBrightness by remember { mutableFloatStateOf(0f) }
    var savedShowBreakDividers by remember { mutableStateOf(true) }
    // 记录进入搭配页时已应用的原始搭配，用于退出（非应用）时还原（滑动切换不更新此项）
    var originalCombinationIndex by remember { mutableIntStateOf(0) }
    var originalWallpaperBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var originalWallpaperOffset by remember { mutableStateOf(wallpaperOffset) }
    var originalWallpaperScale by remember { mutableFloatStateOf(wallpaperScale) }
    var originalCourseCardBlur by remember { mutableFloatStateOf(0f) }
    var originalCourseCardAlpha by remember { mutableFloatStateOf(0.15f) }
    var originalCourseCardHeight by remember { mutableFloatStateOf(54f) }
    var originalCourseCardCornerRadius by remember { mutableFloatStateOf(8f) }
    var originalWallpaperBrightness by remember { mutableFloatStateOf(0f) }
    var originalShowBreakDividers by remember { mutableStateOf(true) }
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
                    offset = androidx.compose.ui.geometry.Offset(
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
                    showBreakDividers = wallpaperRepository.getCombinationShowBreakDividers(id)
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
                        bitmap = if (index == loadedIndex) wallpaperRepository.loadCombinationWallpaper(id) else null,
                        offset = androidx.compose.ui.geometry.Offset(
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
                        showBreakDividers = wallpaperRepository.getCombinationShowBreakDividers(id)
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
            wallpaperScale = curr.scale
            savedWallpaperBitmap = curr.bitmap
            savedWallpaperOffset = curr.offset
            savedWallpaperScale = curr.scale
            savedCourseCardBlur = curr.cardBlurRadius
            courseCardBlur = curr.cardBlurRadius
            savedCourseCardAlpha = curr.cardAlpha
            courseCardAlpha = curr.cardAlpha
            savedCourseCardHeight = curr.cardHeight
            courseCardHeight = curr.cardHeight
            savedCourseCardCornerRadius = curr.cardCornerRadius
            savedWallpaperBrightness = curr.wallpaperBrightness
            savedShowBreakDividers = curr.showBreakDividers
            courseCardCornerRadius = curr.cardCornerRadius
            wallpaperBrightness = curr.wallpaperBrightness
            showBreakDividers = curr.showBreakDividers
            originalWallpaperBitmap = curr.bitmap
            originalWallpaperOffset = curr.offset
            originalWallpaperScale = curr.scale
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
                wallpaperScale = c.scale
                courseCardBlur = c.cardBlurRadius
                courseCardAlpha = c.cardAlpha
                courseCardHeight = c.cardHeight
                courseCardCornerRadius = c.cardCornerRadius
                wallpaperBrightness = c.wallpaperBrightness
                showBreakDividers = c.showBreakDividers
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
    var screenSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
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
    val dayRange = remember(currentViewingWeek, smartWeekend) {
        (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(currentViewingWeek).filter { it in 6..7 }
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

    val density = LocalDensity.current
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
            // 1. 截取卡片快照
            val fullForCard = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
            val cardBmp = try {
                val x = cardLeft.toInt().coerceIn(0, fullForCard.width - 1)
                val y = cardTop.toInt().coerceIn(0, fullForCard.height - 1)
                val w = cardWidth.toInt().coerceIn(1, fullForCard.width - x)
                val h = cardHeight.toInt().coerceIn(1, fullForCard.height - y)
                android.graphics.Bitmap.createBitmap(fullForCard, x, y, w, h)
            } catch (_: Exception) { null }
            detailSnapshot = cardBmp

            // 2. 显示覆盖层（遮住原卡片）
            overlayBitmap = cardBmp
            overlayLeft = cardLeft
            overlayTop = cardTop
            overlayWidth = cardWidth
            overlayHeight = cardHeight
            showCardOverlay = true

            // 3. 隐藏原卡片
            hiddenCourseIds = setOf(courseIdToHide)

            // 4. 等待一帧，让卡片隐藏生效
            delay(50.milliseconds)

            // 5. 截取屏幕快照（覆盖层不参与 record，快照里没有覆盖层）
            val fullSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
            screenSnapshot = fullSnapshot

            // 6. 隐藏覆盖层
            showCardOverlay = false
            overlayBitmap = null

            // 7. 开始动画
            showDetail = true
            delay(16.milliseconds)
            backgroundScale.animateTo(0.92f, animationSpec = tween(580, easing = OobeQuartOutEasing))
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
            courseCardBlur = combinations.getOrNull(currentCombinationIndex)?.cardBlurRadius ?: 0f
            courseCardAlpha = combinations.getOrNull(currentCombinationIndex)?.cardAlpha ?: 0.15f
            wallpaperBrightness = combinations.getOrNull(currentCombinationIndex)?.wallpaperBrightness ?: 0f
            showBreakDividers = combinations.getOrNull(currentCombinationIndex)?.showBreakDividers ?: true
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
                val savedBlur2 = courseCardBlur
                val savedAlpha2 = courseCardAlpha
                val savedHeight2 = courseCardHeight
                val savedCorner2 = courseCardCornerRadius
                val savedBri2 = wallpaperBrightness
                val savedDiv2 = showBreakDividers
                val savedOrigWp2 = originalWallpaperBitmap
                val savedOrigOf2 = originalWallpaperOffset
                val savedOrigSc2 = originalWallpaperScale
                val savedOrigBlur2 = originalCourseCardBlur
                val savedOrigAlpha2 = originalCourseCardAlpha
                val savedOrigHeight2 = originalCourseCardHeight
                val savedOrigCorner2 = originalCourseCardCornerRadius
                val savedOrigBri2 = originalWallpaperBrightness
                wallpaperBitmap = nextComb.bitmap
                wallpaperOffset = nextComb.offset
                wallpaperScale = nextComb.scale
                courseCardBlur = nextComb.cardBlurRadius
                courseCardAlpha = nextComb.cardAlpha
                courseCardHeight = nextComb.cardHeight
                courseCardCornerRadius = nextComb.cardCornerRadius
                wallpaperBrightness = nextComb.wallpaperBrightness
                showBreakDividers = nextComb.showBreakDividers
                originalWallpaperBitmap = nextComb.bitmap
                originalWallpaperOffset = nextComb.offset
                originalWallpaperScale = nextComb.scale
                originalCourseCardBlur = nextComb.cardBlurRadius
                originalCourseCardAlpha = nextComb.cardAlpha
                originalCourseCardHeight = nextComb.cardHeight
                originalCourseCardCornerRadius = nextComb.cardCornerRadius
                originalWallpaperBrightness = nextComb.wallpaperBrightness
                originalShowBreakDividers = nextComb.showBreakDividers
                delay(120.milliseconds)
                val nextSnap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                combinations = combinations.toMutableList().also {
                    it[1] = it[1].copy(snapshot = nextSnap)
                }
                wallpaperBitmap = savedWp2
                wallpaperOffset = savedOf2
                wallpaperScale = savedSc2
                courseCardBlur = savedBlur2
                courseCardAlpha = savedAlpha2
                courseCardHeight = savedHeight2
                courseCardCornerRadius = savedCorner2
                wallpaperBrightness = savedBri2
                showBreakDividers = savedDiv2
                originalWallpaperBitmap = savedOrigWp2
                originalWallpaperOffset = savedOrigOf2
                originalWallpaperScale = savedOrigSc2
                originalCourseCardBlur = savedOrigBlur2
                originalCourseCardAlpha = savedOrigAlpha2
                originalCourseCardHeight = savedOrigHeight2
                originalCourseCardCornerRadius = savedOrigCorner2
                originalWallpaperBrightness = savedOrigBri2
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
            originalCourseCardBlur = courseCardBlur
            originalCourseCardAlpha = courseCardAlpha
            originalCourseCardHeight = courseCardHeight
            originalCourseCardCornerRadius = courseCardCornerRadius
            originalWallpaperBrightness = wallpaperBrightness
            originalShowBreakDividers = showBreakDividers
            originalSnapshot = combinations.getOrNull(currentCombinationIndex)?.snapshot
            // 后台逐个捕获其他搭配快照（等打开动画结束后再开始，避免动画期间主界面壁纸跳变）
            delay(500.milliseconds)
            val savedWp = wallpaperBitmap
            val savedOf = wallpaperOffset
            val savedSc = wallpaperScale
            val savedBlur = courseCardBlur
            val savedAlpha = courseCardAlpha
            val savedHeight = courseCardHeight
            val savedCorner = courseCardCornerRadius
            val savedBri = wallpaperBrightness
            val savedDiv = showBreakDividers
            for (i in 1 until combinations.size) {
                val comb = combinations[i]
                wallpaperBitmap = comb.bitmap
                wallpaperOffset = comb.offset
                wallpaperScale = comb.scale
                courseCardBlur = comb.cardBlurRadius
                courseCardAlpha = comb.cardAlpha
                courseCardHeight = comb.cardHeight
                courseCardCornerRadius = comb.cardCornerRadius
                wallpaperBrightness = comb.wallpaperBrightness
                showBreakDividers = comb.showBreakDividers
                originalWallpaperBitmap = comb.bitmap
                originalWallpaperOffset = comb.offset
                originalWallpaperScale = comb.scale
                originalCourseCardBlur = comb.cardBlurRadius
                originalCourseCardAlpha = comb.cardAlpha
                originalCourseCardHeight = comb.cardHeight
                originalCourseCardCornerRadius = comb.cardCornerRadius
                originalWallpaperBrightness = comb.wallpaperBrightness
                originalShowBreakDividers = comb.showBreakDividers
                delay(120.milliseconds)
                val snap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                combinations = combinations.toMutableList().also {
                    it[i] = it[i].copy(snapshot = snap)
                }
            }
            wallpaperBitmap = savedWp
            wallpaperOffset = savedOf
            wallpaperScale = savedSc
            courseCardBlur = savedBlur
            courseCardAlpha = savedAlpha
            courseCardHeight = savedHeight
            courseCardCornerRadius = savedCorner
            wallpaperBrightness = savedBri
            showBreakDividers = savedDiv
            originalWallpaperBitmap = savedWp
            originalWallpaperOffset = savedOf
            originalWallpaperScale = savedSc
            originalCourseCardBlur = savedBlur
            originalCourseCardAlpha = savedAlpha
            originalCourseCardHeight = savedHeight
            originalCourseCardCornerRadius = savedCorner
            originalWallpaperBrightness = savedBri
            originalShowBreakDividers = savedDiv
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
            // 选择新壁纸时重置位移与缩放，便于从头调整
            wallpaperOffset = androidx.compose.ui.geometry.Offset.Zero
            wallpaperScale = 1f
            // 同步到当前搭配
            val idx = currentCombinationIndex
            if (idx in combinations.indices) {
                combinations = combinations.toMutableList().also {
                    it[idx] = it[idx].copy(
                        bitmap = bitmap,
                        offset = androidx.compose.ui.geometry.Offset.Zero,
                        scale = 1f
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

    val animationState = remember(isInFreeformWindow) {
        derivedStateOf {
            val progress = backgroundScale.value
            val blurProg = ((1f - progress) / (1f - 0.92f)).coerceIn(0f, 1f)
            val blurR = (blurProg * 6f).coerceIn(0f, 6f)
            val clipR = if (showDetail) {
                if (isInFreeformWindow) {
                    20.dp
                } else {
                    with(density) { (screenCornerRadius * (2f - progress)).toDp() }
                }
            } else {
                0.dp
            }
            Triple(blurR, clipR, progress)
        }
    }
    val isDetailActive = showDetail && screenSnapshot != null
    // 课程详情页背景快照模糊半径：跟随 backgroundScale 从 0 渐变到 5f
    val snapshotBlurRadius = if (!isDetailActive) 0.dp
    else {
        val progress = backgroundScale.value
        val blurProg = ((1f - progress) / (1f - 0.92f)).coerceIn(0f, 1f)
        (blurProg * 5f).dp
    }
    Box(modifier = Modifier.fillMaxSize().background(if (showCustomizePage) Color.Black else MiuixTheme.colorScheme.surface)) {
        val shouldRecordGL = !isDetailActive
        val isEntryAnimating = showSwitchSchedule && switchAnimForward && switchAnimRunning
        val mainContentAlpha = when {
            isDetailActive -> 0f
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
                .then(
                    if (navBarStyle != "rail") {
                        // 圆角裁剪在 graphicsLayer 内部完成（见下），这里不再单独 clip
                        Modifier
                    } else Modifier
                )
                .graphicsLayer {
                    val baseScale =
                        if (showDetail || (!isDetailActive && !showSwitchSchedule)) animationState.value.third
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
                    scaleX = baseScale * effectiveScale
                    scaleY = baseScale * effectiveScale
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
                    if (navBarStyle != "rail") {
                        // 用 drawWithContent + clipPath + addSquircleRect 实现 squircle 圆角裁剪
                        // drawWithContent 在 graphicsLayer 缩放后应用，每帧重新裁剪
                        // 视觉圆角 = screenRadius * effectiveScale（随缩放变小）
                        // 搭配页退出时锁定圆角为 screenCornerRadius，避免缩小
                        Modifier.drawWithContent {
                            val exitScale = if (isCustomizeExiting) customizeExitScale.value else 1f
                            val cutoutScale = cutoutMainScale.value
                            val effectiveScale =
                                if (isWindowCutoutActive) cutoutScale else exitScale * cutoutScale
                            val p = (1f - effectiveScale).coerceIn(0f, 1f)
                            // 搭配页退出时锁定圆角为 screenCornerRadius，避免随缩放缩小
                            val animClipPx =
                                if (isCustomizeExiting) screenCornerRadius else screenCornerRadius * p
                            val baseClipPx = animationState.value.second.toPx()
                            val finalClipPx =
                                if (animClipPx > baseClipPx) animClipPx else baseClipPx
                            if (finalClipPx > 0f) {
                                val path = Path().apply {
                                    addSquircleRect(
                                        width = size.width,
                                        height = size.height,
                                        cornerRadius = finalClipPx
                                    )
                                }
                                clipPath(path) {
                                    this@drawWithContent.drawContent()
                                }
                            } else {
                                drawContent()
                            }
                        }
                    } else Modifier
                )
                .then(
                    if (shouldRecordGL) {
                        Modifier.drawWithContent {
                            screenGraphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawContent()
                        }
                    } else {
                        Modifier
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
                                val targetPage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
                                pagerState.animateScrollToPage(targetPage)
                            }
                        },
                        onOpenSwitchSchedule = {
                            if (!isShiftMode && !showSwitchSchedule) {
                                coroutineScope.launch {
                                    mainContentSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
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
                            val intent = android.content.Intent(context, CourseManageActivity::class.java)
                            context.startActivity(intent)
                        },
                        onTitleBarMeasured = { activity?.titleBarHeight = it },
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        showMorePopup = showMorePopup,
                        onShowMorePopupChange = { showMorePopup = it }
                    )
                    // 设置页标题栏（Activity 层级渲染，避免 drawPlainBackdrop native crash）
                    if (selectedTab == 2 || (isShiftMode && selectedTab == 1)) {
                        SettingsTopBar(
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            navBarStyle = navBarStyle,
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
                            onMoreClick = { showTodayMorePopup = true }
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
                        modifier = Modifier.fillMaxSize().then(
                            if (liquidGlassBackdrop != null) Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
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
                                    openCourseDetail(courses, left, top, width, height, fromToday = true, courseIdToHide = courseIdToHide)
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
                                onCourseManage = {
                                    val intent = android.content.Intent(context, CourseManageActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )

                            1 -> MainScheduleScreen(
                                viewModel = viewModel,
                                settingsViewModel = settingsViewModel,
                                pagerState = pagerState,
                                currentDayOfWeek = currentDayOfWeek,
                                hiddenCourseIds = hiddenCourseIds,
                                onCourseClick = { courses, left, top, width, height, _, courseIdToHide ->
                                    openCourseDetail(courses, left, top, width, height, fromToday = false, courseIdToHide = courseIdToHide)
                                },
                                onPopupStateChange = { showCourseDetailPopup = it },
                                onEmptyLongPress = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showLongPressButton = true
                                },
                                wallpaperBitmap = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBitmap else wallpaperBitmap,
                                wallpaperOffset = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset,
                                wallpaperScale = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale,
                                isWallpaperEditing = isWindowCutoutActive,
                                onWallpaperOffsetChange = { wallpaperOffset = it },
                                onWallpaperScaleChange = { wallpaperScale = it },
                                cardBlurRadius = if (showCustomizePage && !isWindowCutoutActive) originalCourseCardBlur else courseCardBlur,
                                cardAlpha = if (showCustomizePage && !isWindowCutoutActive) originalCourseCardAlpha else courseCardAlpha,
                                cardHeightPerSection = if (showCustomizePage && !isWindowCutoutActive) originalCourseCardHeight else courseCardHeight,
                                cardCornerRadius = if (showCustomizePage && !isWindowCutoutActive) originalCourseCardCornerRadius else courseCardCornerRadius,
                                wallpaperBrightness = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBrightness else wallpaperBrightness,
                                showBreakDividers = if (showCustomizePage && !isWindowCutoutActive) originalShowBreakDividers else showBreakDividers,
                                wallpaperHasAppeared = activity?.wallpaperHasAppeared ?: false,
                                onWallpaperAppeared = { activity?.wallpaperHasAppeared = true },
                                liquidGlassBackdrop = liquidGlassBackdrop
                            )

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
                                settingsScrollBehavior = settingsScrollBehavior
                            )
                        }
                    } else {
                        when (selectedTab) {
                            0 -> ShiftScheduleScreen(
                                viewModel = viewModel,
                                shiftViewModel = shiftViewModel,
                                settingsViewModel = settingsViewModel,
                                currentDayOfWeek = currentDayOfWeek,
                                pagerState = pagerState,
                                cardHeightPerSection = courseCardHeight,
                                wallpaperBitmap = wallpaperBitmap,
                                wallpaperOffset = wallpaperOffset,
                                wallpaperScale = wallpaperScale
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
                                settingsScrollBehavior = settingsScrollBehavior
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
                if (appStyle == "liquidglass" && liquidGlassBackdrop != null && !isShiftMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 20.dp, bottom = 28.dp),
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
                if (showAddDialog) {
                    val editingStartSection =
                        editingCourse?.startSection ?: selectedStartSection
                    val editingEndSection = editingCourse?.endSection ?: selectedEndSection

                    AddCourseDialog(
                        course = editingCourse,
                        selectedDay = viewModel.selectedDay.collectAsState().value,
                        totalWeeks = totalWeeks,
                        totalSections = totalSections,
                        defaultStartSection = editingStartSection,
                        defaultEndSection = editingEndSection,
                        getOccupiedWeeks = { dayOfWeek, startSection, endSection ->
                            viewModel.getOccupiedWeeks(
                                dayOfWeek = dayOfWeek,
                                startSection = startSection,
                                endSection = endSection,
                                excludeIds = setOfNotNull(editingCourse?.id)
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
                        },
                        liquidGlassBackdrop = liquidGlassBackdrop
                    )
                }
            }
        }
        // LiquidGlass 更多菜单（Scaffold 外层，显示在最上方）
        if (appStyle == "liquidglass" && liquidGlassBackdrop != null) {
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
                    .padding(top = (statusBarHeight + 32.dp).coerceAtLeast(76.dp), end = 2.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                    LiquidGlassDropdownMenu(
                        show = showMorePopup,
                        onDismissRequest = { showMorePopup = false },
                        backdrop = liquidGlassBackdrop,
                        menuHeight = 144.dp
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
                                val intent = android.content.Intent(context, CourseManageActivity::class.java)
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
                        onDismissRequest = { showTodayMorePopup = false },
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
                                val intent = android.content.Intent(context, CourseManageActivity::class.java)
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
                            currentCombinationIndex = (originalCombinationIndex - 1).coerceAtLeast(0)
                            originalCombinationIndex = currentCombinationIndex
                            // 恢复原始搭配状态
                            wallpaperBitmap = savedWallpaperBitmap
                            wallpaperOffset = savedWallpaperOffset
                            wallpaperScale = savedWallpaperScale
                            courseCardBlur = savedCourseCardBlur
                            courseCardAlpha = savedCourseCardAlpha
                            courseCardHeight = savedCourseCardHeight
                            courseCardCornerRadius = savedCourseCardCornerRadius
                            wallpaperBrightness = savedWallpaperBrightness
                            showBreakDividers = savedShowBreakDividers
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
                        wallpaperRepository.saveCombinationCardBlur(combId, courseCardBlur)
                        wallpaperRepository.saveCombinationCardAlpha(combId, courseCardAlpha)
                        wallpaperRepository.saveCombinationCardHeight(combId, courseCardHeight)
                        wallpaperRepository.saveCombinationCardCornerRadius(combId, courseCardCornerRadius)
                        wallpaperRepository.saveCombinationWallpaperBrightness(combId, wallpaperBrightness)
                        wallpaperRepository.saveCombinationShowBreakDividers(combId, showBreakDividers)
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
                                cardBlurRadius = courseCardBlur,
                                cardAlpha = courseCardAlpha,
                                cardHeight = courseCardHeight,
                                cardCornerRadius = courseCardCornerRadius,
                                wallpaperBrightness = wallpaperBrightness,
                                showBreakDividers = showBreakDividers
                            )
                        }
                    }
                    // 更新已保存快照，避免退出时回退
                    savedWallpaperBitmap = bitmap
                    savedWallpaperOffset = wallpaperOffset
                    savedWallpaperScale = wallpaperScale
                    savedCourseCardBlur = courseCardBlur
                    savedCourseCardAlpha = courseCardAlpha
                    savedCourseCardHeight = courseCardHeight
                    savedCourseCardCornerRadius = courseCardCornerRadius
                    savedWallpaperBrightness = wallpaperBrightness
                    savedShowBreakDividers = showBreakDividers
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
                            android.widget.Toast.makeText(context, "最多创建5个搭配", android.widget.Toast.LENGTH_SHORT).show()
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
                            offset = androidx.compose.ui.geometry.Offset.Zero,
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
                        val savedOrigBlur = originalCourseCardBlur
                        val savedOrigAlpha = originalCourseCardAlpha
                        val savedOrigHeight = originalCourseCardHeight
                        val savedOrigCorner = originalCourseCardCornerRadius
                        val savedOrigBri = originalWallpaperBrightness
                        val savedOrigDiv = originalShowBreakDividers
                        // 新搭配无背景，临时清除壁纸以截取无壁纸快照
                        wallpaperBitmap = null
                        wallpaperOffset = androidx.compose.ui.geometry.Offset.Zero
                        wallpaperScale = 1f
                        courseCardBlur = 0f
                        courseCardAlpha = 0.15f
                        courseCardHeight = 54f
                        courseCardCornerRadius = 8f
                        wallpaperBrightness = 0f
                        showBreakDividers = true
                        // 同步更新 original* 让 MainScheduleScreen 渲染空状态
                        originalWallpaperBitmap = null
                        originalWallpaperOffset = androidx.compose.ui.geometry.Offset.Zero
                        originalWallpaperScale = 1f
                        originalCourseCardBlur = 0f
                        originalCourseCardAlpha = 0.15f
                        originalCourseCardHeight = 54f
                        originalCourseCardCornerRadius = 8f
                        originalWallpaperBrightness = 0f
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
                            courseCardBlur = savedOrigBlur
                            courseCardAlpha = savedOrigAlpha
                            courseCardHeight = savedOrigHeight
                            courseCardCornerRadius = savedOrigCorner
                            wallpaperBrightness = savedOrigBri
                            showBreakDividers = savedOrigDiv
                            originalWallpaperBitmap = savedOrigWp
                            originalWallpaperOffset = savedOrigOf
                            originalWallpaperScale = savedOrigSc
                            originalCourseCardBlur = savedOrigBlur
                            originalCourseCardAlpha = savedOrigAlpha
                            originalCourseCardHeight = savedOrigHeight
                            originalCourseCardCornerRadius = savedOrigCorner
                            originalWallpaperBrightness = savedOrigBri
                            originalShowBreakDividers = savedOrigDiv
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
                            wallpaperScale = c.scale
                            courseCardBlur = c.cardBlurRadius
                            courseCardAlpha = c.cardAlpha
                            courseCardHeight = c.cardHeight
                            courseCardCornerRadius = c.cardCornerRadius
                            wallpaperBrightness = c.wallpaperBrightness
                            showBreakDividers = c.showBreakDividers
                            // 同步更新 savedWallpaper*：编辑取消时需回退到"当前查看搭配"的未编辑状态，
                            // 切换搭配时必须同步，否则取消编辑会闪回原搭配
                            savedWallpaperBitmap = c.bitmap
                            savedWallpaperOffset = c.offset
                            savedWallpaperScale = c.scale
                            savedCourseCardBlur = c.cardBlurRadius
                            savedCourseCardAlpha = c.cardAlpha
                            savedCourseCardHeight = c.cardHeight
                            savedCourseCardCornerRadius = c.cardCornerRadius
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
                                    wallpaperScale = c.scale
                                    savedWallpaperBitmap = c.bitmap
                                    savedWallpaperOffset = c.offset
                                    savedWallpaperScale = c.scale
                                    savedCourseCardBlur = c.cardBlurRadius
                                    savedCourseCardAlpha = c.cardAlpha
                                    savedCourseCardHeight = c.cardHeight
                            savedCourseCardCornerRadius = c.cardCornerRadius
                            savedWallpaperBrightness = c.wallpaperBrightness
                            savedShowBreakDividers = c.showBreakDividers
                                    savedWallpaperBrightness = c.wallpaperBrightness
                                    savedShowBreakDividers = c.showBreakDividers
                                    courseCardBlur = c.cardBlurRadius
                                    courseCardAlpha = c.cardAlpha
                                    courseCardHeight = c.cardHeight
                                    courseCardCornerRadius = c.cardCornerRadius
                                    wallpaperBrightness = c.wallpaperBrightness
                                    showBreakDividers = c.showBreakDividers
                                    // 无条件更新原始搭配值，确保 MainScheduleScreen 显示正确
                                    originalWallpaperBitmap = c.bitmap
                                    originalWallpaperOffset = c.offset
                                    originalWallpaperScale = c.scale
                                    originalCourseCardBlur = c.cardBlurRadius
                                    originalCourseCardAlpha = c.cardAlpha
                                    originalCourseCardHeight = c.cardHeight
                                    originalCourseCardCornerRadius = c.cardCornerRadius
                                    originalWallpaperBrightness = c.wallpaperBrightness
                                    originalShowBreakDividers = c.showBreakDividers
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
                                currentCombinationIndex = (originalCombinationIndex - 1).coerceAtLeast(0)
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
                        courseCardBlur = savedCourseCardBlur
                        courseCardAlpha = savedCourseCardAlpha
                        courseCardHeight = savedCourseCardHeight
                        courseCardCornerRadius = savedCourseCardCornerRadius
                        wallpaperBrightness = savedWallpaperBrightness
                        showBreakDividers = savedShowBreakDividers
                        // 同步恢复 combinations[idx] 的编辑字段，避免 onCustomizeValueChange 污染列表后
                        // 被 onCombinationPageChange 重新读取覆盖已恢复的变量
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(
                                    bitmap = savedWallpaperBitmap,
                                    offset = savedWallpaperOffset,
                                    scale = savedWallpaperScale,
                                    cardBlurRadius = savedCourseCardBlur,
                                    cardAlpha = savedCourseCardAlpha,
                                    cardHeight = savedCourseCardHeight,
                                    cardCornerRadius = savedCourseCardCornerRadius,
                                    wallpaperBrightness = savedWallpaperBrightness,
                                    showBreakDividers = savedShowBreakDividers
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
                        courseCardBlur = blur
                        courseCardAlpha = alpha
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(cardBlurRadius = blur, cardAlpha = alpha)
                            }
                        }
                    },
                    initialCardBlurRadius = combinations.getOrNull(currentCombinationIndex)?.cardBlurRadius
                        ?: 0f,
                    initialCardAlpha = courseCardAlpha,
                    onWallpaperBrightnessChange = { brightness ->
                        wallpaperBrightness = brightness
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(wallpaperBrightness = brightness)
                            }
                        }
                    },
                    initialWallpaperBrightness = wallpaperBrightness,
                    onShowBreakDividersChange = { show ->
                        showBreakDividers = show
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(showBreakDividers = show)
                            }
                        }
                    },
                    initialShowBreakDividers = showBreakDividers,
                    onCustomizeValueChange = { height, cornerRadius ->
                        courseCardHeight = height
                        courseCardCornerRadius = cornerRadius
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also {
                                it[idx] = it[idx].copy(cardHeight = height, cardCornerRadius = cornerRadius)
                            }
                        }
                    },
                    initialCardHeight = courseCardHeight,
                    initialCardCornerRadius = courseCardCornerRadius,
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
                    // 退出（非应用）：只恢复 live 变量，不动 combinations 列表
                    wallpaperBitmap = originalWallpaperBitmap
                    wallpaperOffset = originalWallpaperOffset
                    wallpaperScale = originalWallpaperScale
                    courseCardBlur = originalCourseCardBlur
                    courseCardAlpha = originalCourseCardAlpha
                    courseCardHeight = originalCourseCardHeight
                    courseCardCornerRadius = originalCourseCardCornerRadius
                    wallpaperBrightness = originalWallpaperBrightness
                    showBreakDividers = originalShowBreakDividers
                    currentCombinationIndex = originalCombinationIndex
                }
                // 应用时保留当前壁纸状态（已持久化到磁盘）
                isApplyingCustomize = false
                windowInsetsController?.isAppearanceLightStatusBars = true
                windowInsetsController?.isAppearanceLightNavigationBars = true
            }
        }
        // 卡片覆盖层（点击时短暂显示，遮住原卡片消失的过程）
        if (showCardOverlay && overlayBitmap != null) {
            Image(
                bitmap = overlayBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = with(LocalDensity.current) { overlayLeft.toDp() },
                        y = with(LocalDensity.current) { overlayTop.toDp() }
                    )
                    .size(
                        width = with(LocalDensity.current) { overlayWidth.toDp() },
                        height = with(LocalDensity.current) { overlayHeight.toDp() }
                    )
                    .clip(RoundedRectangle(16.dp)),
                contentScale = ContentScale.FillBounds
            )
        }
        // 课程详情页背景快照（仅快照模糊，不模糊上层详情页）
        if (isDetailActive) {
            val s = animationState.value
            Box(modifier = Modifier.fillMaxSize().blur(snapshotBlurRadius)) {
                Image(
                    bitmap = screenSnapshot!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = s.third
                            scaleY = s.third
                        }
                        .clip(RoundedRectangle(s.second)),
                    contentScale = ContentScale.Crop
                )
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
                        backgroundScale.animateTo(1f, animationSpec = tween(360, easing = OobeCubicOutEasing))
                    }
                },
                onBack = {
                    showDetail = false
                    screenSnapshot = null
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
                                    ((1f - currentProgress) * 580).toInt().coerceAtLeast(1)
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
                                ((1f - currentProgress) * 580).toInt().coerceAtLeast(1)
                            launch {
                                switchPageScale.animateTo(
                                    1.08f,
                                    animationSpec = tween(remainingDuration, easing = OobeQuartOutEasing)
                                )
                            }
                            launch {
                                switchPageBlur.animateTo(
                                    5f,
                                    animationSpec = tween(remainingDuration, easing = OobeQuartOutEasing)
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
                                val remainingDuration = 360
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
                val cRadius = with(density) { (startRadius + (screenCornerRadius - startRadius) * p).toDp() }
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
) {
    if (liquidGlassBackdrop == null) return
    val appStyle = rememberAppStyle()
    if (appStyle != "liquidglass") return
    val isTabletLiquidGlass = navBarStyle == "rail"

    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
    ) {
        SmallTopAppBar(
            color = Color.Transparent,
            title = if (isTabletLiquidGlass) "" else "我的",
            modifier = Modifier.zIndex(1f),
            navigationIcon = if (isTabletLiquidGlass) {
                {
                    Text(
                        text = "我的",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            } else {
                {}
            },
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
        SmallTopAppBar(
            color = Color.Transparent,
            title = if (isTabletLiquidGlass) "" else titleText,
            modifier = Modifier.zIndex(1f),
            navigationIcon = {
                if (isTabletLiquidGlass) {
                    Text(
                        text = titleText,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                } else {
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
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            },
            actions = {
                LiquidTopBarButton(
                    onClick = onMoreClick,
                    backdrop = liquidGlassBackdrop,
                    icon = MiuixIcons.More,
                    contentDescription = "更多",
                    iconSize = 20.dp,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        )
    }
}
