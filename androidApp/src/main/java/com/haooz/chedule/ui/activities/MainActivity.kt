package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouteContent
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.MainLayerTransition
import com.haooz.chedule.ui.navigation.rememberAppRouter
import com.haooz.chedule.ui.navigation.rememberNavTransitionState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.PrivacyConsent
import com.haooz.chedule.data.ScheduleAppearance
import com.haooz.chedule.data.StatsReporter
import com.haooz.chedule.data.ThemeMode
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenu
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenuItem
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import com.haooz.chedule.ui.components.BackToNowFloatingButton
import com.haooz.chedule.ui.components.CourseCard
import com.haooz.chedule.ui.components.LandRippleSpec
import com.haooz.chedule.ui.components.LocalLandRipple
import com.haooz.chedule.ui.components.ScheduleBottomBar
import com.haooz.chedule.ui.components.ScheduleTopBar
import com.haooz.chedule.ui.components.ShareImportDialog
import com.haooz.chedule.ui.components.UpdateDialog
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.screens.CourseDetailScreen
import com.haooz.chedule.ui.screens.CustomizeScheduleScreen
import com.haooz.chedule.ui.screens.MainScheduleScreen
import com.haooz.chedule.ui.screens.PrivacyConsentScreen
import com.haooz.chedule.ui.screens.ScheduleGridGeometry
import com.haooz.chedule.ui.screens.SettingsScreen
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TabletCourseManagePane
import com.haooz.chedule.ui.screens.TabletSwitchSchedulePane
import com.haooz.chedule.ui.screens.TodayScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.ApiCompat
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.applyNavigationBarIsDark
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.consumeAllTouches
import com.haooz.chedule.ui.utils.courseCardSolidBacking
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.PageBackdropSnapshot
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.haooz.chedule.ui.utils.rememberScheduleThemeMode
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.FastForward
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import kotlinx.datetime.LocalDate
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

/** 取消进行中的 pager 滚动/惯性 */
private suspend fun PagerState.cancelScroll() {
    scroll(MutatePriority.PreventUserInput) { }
}

/** 空白格快捷菜单锚点：高亮用列星期，添加课程用调课映射后的星期/周 */
private data class EmptyCellMenuTarget(
    val columnDay: Int,
    val section: Int,
    val addDay: Int,
    val addWeek: Int,
)

/**
 * 粘贴落点：动画/高亮走列星期（几何坐标），写库走调课映射后的数据坐标。
 * 调课日列显示的是 followWeekday/followWeek 的课，只传列星期会让粘出来的课落到错误星期/周次，
 * 在本页任何列都不渲染——与空白格「添加」必须保持同一套映射。
 */
private data class PasteTarget(
    val columnDay: Int,
    val dataDay: Int,
    val section: Int,
    /** 数据周次；调课日 = followWeek，否则 = 正在浏览的周 */
    val dataWeek: Int,
)

/** 主 tab 翻页动画（点底栏 tab 时平移切换）；相邻页略偏软 */
private val MainTabPagerAnimSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 320f,
)

/** 跨页（今日↔设置，距离 2 页）更缓，避免长距离仍按同刚度显得急 */
private val MainTabPagerCrossPageAnimSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 240f,
)

/** 动画切到目标主 tab；正常结束后若未精确落页则强制吸附 */
private suspend fun PagerState.animateMainTabTo(target: Int) {
    val distance = abs(target - currentPage)
    val spec = if (distance > 1) MainTabPagerCrossPageAnimSpec else MainTabPagerAnimSpec
    try {
        animateScrollToPage(target, animationSpec = spec)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    }
    if (currentPage != target || abs(currentPageOffsetFraction) > 0.001f) {
        scrollToPage(target)
    }
}

/** 拖拽速度/采样时间；只在松手 spring 时读，避开 composition */
private class DragMotionHolder {
    @Volatile
    var velocityX = 0f

    @Volatile
    var velocityY = 0f

    @Volatile
    var lastTimeMs = 0L

    @Volatile
    var lastOffsetX = 0f

    @Volatile
    var lastOffsetY = 0f

    fun reset() {
        velocityX = 0f
        velocityY = 0f
        lastTimeMs = 0L
        lastOffsetX = 0f
        lastOffsetY = 0f
    }
}

class MainActivity : ComponentActivity() {

    companion object {
        /** 二级页侧栏点选主 tab */
        const val EXTRA_MAIN_TAB = "extra_main_tab"

        // 跨 Activity 重建复用，避免每次启动重新解码壁纸
        @Volatile
        var cachedWallpaperBitmap: android.graphics.Bitmap? = null

        @Volatile
        var cachedWallpaperOffset: Offset = Offset.Zero

        @Volatile
        var cachedWallpaperScale: Float = 1f

        @Volatile
        var cachedAppearance: com.haooz.chedule.data.AppearanceConfig =
            com.haooz.chedule.data.AppearanceConfig()

        fun setTaskExcludedFromRecents(context: Context, hidden: Boolean) {
            runCatching {
                val manager = context.getSystemService(ActivityManager::class.java)
                val appTask = manager.appTasks.firstOrNull { task ->
                    task.taskInfo?.baseIntent?.component?.packageName == context.packageName
                } ?: manager.appTasks.firstOrNull()
                appTask?.setExcludeFromRecents(hidden)
            }
        }
    }

    var shareIntentUri: android.net.Uri? = null
        private set
    var shareIntentAction: String? = null
        private set

    // Compose State 跟踪 intent，变化时触发重组
    var shareIntentVersion by mutableIntStateOf(0)
        private set

    var titleBarHeight by mutableStateOf(56.dp)

    var isInFreeformWindow by mutableStateOf(false)
        private set

    var resumeCount by mutableIntStateOf(0)
        private set

    /**
     * 由 Intent 触发、需要路由跳转的待处理路由（目前是「打开提醒设置」深链）。
     *
     * 这类 intent 在 `onCreate`/`onNewIntent` 里到达，那时要么 Compose 还没起来、
     * 要么已经起来 —— 统一记成状态，由 setContent 里的 LaunchedEffect 消费，
     * 避免在 Activity 层直接 startActivity（那会绕过单宿主）。
     */
    var pendingRoute by mutableStateOf<com.haooz.chedule.ui.navigation.AppRoute?>(null)
        private set

    fun clearShareIntent() {
        shareIntentUri = null
        shareIntentAction = null
    }

    private fun updateFreeformWindowState() {
        isInFreeformWindow = isInMultiWindowMode
    }

    fun applyHideFromRecents(hidden: Boolean) {
        setTaskExcludedFromRecents(this, hidden)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getIntExtra(EXTRA_MAIN_TAB, -1)?.takeIf { it in 0..2 }?.let {
            com.haooz.chedule.ui.components.TabletNavSideState.pendingMainTab = it
        }

        // 默认不启用：只有"退出即隐藏后台"开启时才启用（见 syncBackCallback），
        // 其余情况交回系统默认返回，保证预测性返回动画可用
        backCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        }.also { onBackPressedDispatcher.addCallback(this, it) }

        applyHideFromRecents(
            AppStorage.store("app_preferences")
                .getBoolean("hide_background", false)
        )

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

        // 提醒调度与超级岛 Shizuku 初始化与首帧无关，放到 IO，不抢主线程
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            CourseReminderHelper.startReminderService(appContext)
            IslandNotificationHelper.init(appContext)
        }

        // 同步隐私同意状态到可观察状态：主界面已预加载，依赖同意的副作用据此判断是否可执行
        PrivacyConsent.refresh()

        // 合规：仅在用户同意隐私政策后，才进行设备信息上报
        if (PrivacyConsent.hasConsented()) {
            StatsReporter.reportActive()
            StatsReporter.reportInstallOnce()
        }

        // 异步预加载搭配壁纸，Compose 侧已处理 cachedWallpaperBitmap=null
        if (cachedWallpaperBitmap == null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 单搭配：无 id 维度，直接读外观文件
                    cachedWallpaperBitmap = ScheduleAppearance.loadWallpaper()
                    cachedWallpaperOffset = Offset(
                        ScheduleAppearance.getOffsetX(),
                        ScheduleAppearance.getOffsetY()
                    )
                    cachedWallpaperScale = ScheduleAppearance.getScale()
                    val refW = ScheduleAppearance.getOffsetRefW()
                    val refH = ScheduleAppearance.getOffsetRefH()
                    val metrics = this@MainActivity.resources.displayMetrics
                    val curW = metrics.widthPixels.toFloat()
                    val curH = metrics.heightPixels.toFloat()
                    if (refW > 0f && refH > 0f && (refW != curW || refH != curH)) {
                        val mapped = remapWallpaperForScreen(
                            cachedWallpaperOffset, cachedWallpaperScale, cachedWallpaperBitmap,
                            refW, refH, curW, curH
                        )
                        cachedWallpaperOffset = mapped.first
                        cachedWallpaperScale = mapped.second
                    }
                    cachedAppearance = com.haooz.chedule.data.AppearanceConfig(
                        cardBlurRadius = ScheduleAppearance.getCardBlur(),
                        cardAlpha = ScheduleAppearance.getCardAlpha(),
                        cardSurfaceAlpha = ScheduleAppearance.getCardSurfaceAlpha(),
                        cardHeight = ScheduleAppearance.getCardHeight(),
                        cardCornerRadius = ScheduleAppearance.getCardCornerRadius(),
                        wallpaperBrightness = ScheduleAppearance.getWallpaperBrightness(),
                        showBreakDividers = ScheduleAppearance.getShowBreakDividers(),
                        cardContentAlignment = ScheduleAppearance.getCardContentAlignment(),
                        cardTextColor = ScheduleAppearance.getCardTextColor(),
                        cardTextScale = ScheduleAppearance.getCardTextScale(),
                        showClassroom = ScheduleAppearance.getShowClassroom(),
                        showTeacher = ScheduleAppearance.getShowTeacher(),
                        cardRefraction = ScheduleAppearance.getCardRefraction(),
                        wallpaperBlur = ScheduleAppearance.getWallpaperBlur()
                    )
                } catch (_: Exception) {
                }
            }
        }

        setContent {
            CourseScheduleTheme {
                // 合规：未同意隐私政策前不申请权限、不收集信息（由 privacyConsented 门禁网络副作用）；
                // 主界面仍照常组合（预加载），这样点「同意」后即时进入，不会卡顿
                var privacyAgreed by remember {
                    mutableStateOf(PrivacyConsent.hasConsented())
                }
                val router = rememberAppRouter()
                // 主界面（常驻底座，不在 AppNavHost 的 AnimatedContent 里）的首层转场进度。
                // 栈被 rememberSaveable 恢复成非空时（旋转 / 进程重建），主界面应已是「被盖住」的 1，
                // 不能从 0 起步白播一次进入动画。
                val mainTransition = rememberNavTransitionState(
                    initialValue = if (router.hasOverlay) 1f else 0f,
                )
                // 消费 Intent 带来的待跳转路由（提醒设置深链等）
                LaunchedEffect(pendingRoute) {
                    pendingRoute?.let {
                        router.navigate(it)
                        pendingRoute = null
                    }
                }
                CompositionLocalProvider(LocalAppRouter provides router) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── 主界面常驻底座 ──
                        MainLayerTransition(mainTransition) {
                            CourseScheduleApp(privacyConsented = privacyAgreed)
                        }

                        // ── 子页叠加层 ──
                        AppNavHost(router, mainTransition = mainTransition) { route ->
                            AppRouteContent(router, route)
                        }

                        if (!privacyAgreed) {
                            PrivacyConsentScreen(
                                onAgree = {
                                    PrivacyConsent.setConsented()
                                    // 同意后方可进行设备信息上报
                                    StatsReporter.reportActive()
                                    StatsReporter.reportInstallOnce()
                                    privacyAgreed = true
                                },
                                onDecline = { finish() },
                                onOpenPolicy = {
                                    router.navigate(com.haooz.chedule.ui.navigation.AppRoute.PrivacyPolicy)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        isInFreeformWindow = isInMultiWindowMode
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        // 同步返回回调：仅"退出即隐藏后台"开启时需要自定义回调（moveTaskToBack），
        // 其余情况交回系统默认返回（finish），保留 Android 14+ 的预测性返回动画
        syncBackCallback()
    }

    // 仅"退出即隐藏后台"开启时才启用自定义返回回调，
    // 让系统把手势识别为默认返回并播放"返回桌面"动画
    private fun syncBackCallback() {
        val hideBackground = AppStorage.store("app_preferences")
            .getBoolean("hide_background", false)
        backCallback?.isEnabled = hideBackground
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        handleReminderSettingsIntent(intent)
        intent.getIntExtra(EXTRA_MAIN_TAB, -1).takeIf { it in 0..2 }?.let {
            com.haooz.chedule.ui.components.TabletNavSideState.pendingMainTab = it
        }
        shareIntentVersion++
    }

    private fun handleBackNavigation() {
        // 仅在"退出即隐藏后台"开启时回调才被启用，这里直接走隐藏后台逻辑
        applyHideFromRecents(true)
        moveTaskToBack(true)
    }

    private var backCallback: androidx.activity.OnBackPressedCallback? = null

    private fun handleReminderSettingsIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS,
                false
            ) == true
        ) {
            intent.removeExtra(CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS)
            // 课程提醒已改成路由，这里不能再 startActivity —— 记成「待跳转」，
            // 由 setContent 里的 LaunchedEffect 消费（onCreate 时 Compose 还没起来）。
            pendingRoute = com.haooz.chedule.ui.navigation.AppRoute.CourseReminder
        }
    }

    @SuppressLint("NewApi")
    private fun extractIntentData(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                shareIntentUri = intent.data
                shareIntentAction = Intent.ACTION_VIEW
            }

            Intent.ACTION_SEND -> {
                shareIntentUri = intent.getParcelableExtra(
                    Intent.EXTRA_STREAM,
                    android.net.Uri::class.java
                )
                shareIntentAction = Intent.ACTION_SEND
            }
        }
    }
}

// cover-fill 最小缩放，保证壁纸填满屏幕
private fun computeWallpaperMinScale(
    bitmap: android.graphics.Bitmap?,
    screenWPx: Float,
    screenHPx: Float
): Float {
    if (bitmap == null) return 1f
    return com.haooz.chedule.data.WallpaperTransform.minScale(bitmap.width, bitmap.height, screenWPx, screenHPx)
}

/**
 * 将壁纸 offset/scale 从 fromW×fromH 重映射到 toW×toH。
 * 无有效 bitmap/参考尺寸时原样返回，避免横竖屏切换把数据算坏。
 */
private fun remapWallpaperForScreen(
    offset: Offset,
    scale: Float,
    bitmap: android.graphics.Bitmap?,
    fromW: Float,
    fromH: Float,
    toW: Float,
    toH: Float,
): Pair<Offset, Float> {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return offset to scale
    return com.haooz.chedule.data.WallpaperTransform.remap(
        offset, scale, bitmap.width, bitmap.height, fromW, fromH, toW, toH
    )
}

// 16×16 网格感知加权测光，avg≥128 判亮
@SuppressLint("UseKtx")
private fun computeWallpaperIsLight(bitmap: android.graphics.Bitmap?): Boolean? {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
    val gridW = 16
    val gridH = 16
    val small = bitmap.scale(gridW, gridH)
    var sum = 0L
    for (x in 0 until gridW) {
        for (y in 0 until gridH) {
            val c = small[x, y]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            sum += (299 * r + 587 * g + 114 * b) / 1000
        }
    }
    val avg = sum / (gridW * gridH)
    small.recycle()
    return avg >= 128
}

/**
 * 按屏幕长短边降采样解码相册选图。
 * 相册原图可达 50MP+，全尺寸 ARGB_8888 解码是选壁纸路径 OOM 的主因。
 * 部分机型/云相册 URI 一次 open 不稳，先落到缓存文件再解码。
 */
private fun decodeWallpaperSampled(
    context: Context,
    uri: android.net.Uri,
    screenWPx: Float,
    screenHPx: Float,
): android.graphics.Bitmap? {
    val targetW = maxOf(screenWPx, screenHPx).toInt().coerceAtLeast(1)
    val targetH = minOf(screenWPx, screenHPx).toInt().coerceAtLeast(1)

    fun sampleSizeFromBounds(outW: Int, outH: Int): Int {
        var sample = 1
        while ((outW / 2 / sample) >= targetW && (outH / 2 / sample) >= targetH) {
            sample *= 2
        }
        return sample
    }

    fun downscaleIfNeeded(decoded: android.graphics.Bitmap?): android.graphics.Bitmap? {
        decoded ?: return null
        if (decoded.width <= 0 || decoded.height <= 0) return null
        if (decoded.width > targetW * 2 || decoded.height > targetH * 2) {
            val scale = minOf(
                targetW.toFloat() / decoded.width,
                targetH.toFloat() / decoded.height,
            )
            val scaled = decoded.scale(
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== decoded) decoded.recycle()
            return scaled
        }
        return decoded
    }

    // 1) 直接 URI 解码
    try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val sampleOpts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFromBounds(bounds.outWidth, bounds.outHeight)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, sampleOpts)
            }
            downscaleIfNeeded(decoded)?.let { return it }
        }
    } catch (_: Exception) { }

    // 2) ImageDecoder（HEIC/部分云图更稳，API 28+）
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        try {
            val decoded = android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, _, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
            downscaleIfNeeded(decoded)?.let { return it }
        } catch (_: Exception) { }
    }

    // 3) 复制到缓存再解码：规避 content URI 只能读一次 / 权限瞬时失效
    val cacheFile = java.io.File(context.cacheDir, "wallpaper_pick_${System.currentTimeMillis()}.img")
    try {
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
            true
        } == true
        if (copied && cacheFile.exists() && cacheFile.length() > 0) {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                val sampleOpts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFromBounds(bounds.outWidth, bounds.outHeight)
                }
                val decoded = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath, sampleOpts)
                downscaleIfNeeded(decoded)?.let { return it }
            }
        }
    } catch (_: Exception) { } finally {
        // 成功/失败都删，避免 cacheDir 残留 wallpaper_pick_*.img
        runCatching { cacheFile.delete() }
    }
    return null
}

/** 释放独立快照位图；与进程级壁纸缓存或 keep 引用共用时不 recycle */
private fun recycleIndependentBitmap(
    bitmap: android.graphics.Bitmap?,
    vararg keep: android.graphics.Bitmap?,
) {
    if (bitmap == null || bitmap.isRecycled) return
    if (bitmap === MainActivity.cachedWallpaperBitmap) return
    if (keep.any { it === bitmap }) return
    try {
        if (!bitmap.isRecycled) bitmap.recycle()
    } catch (_: Exception) {
    }
}

@Composable
private fun DeleteWeekCourseDialog(
    show: Boolean,
    course: Course?,
    week: Int,
    viewModel: CourseViewModel,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onDismiss: () -> Unit,
) {
    var deleteAllWeeks by remember(show) { mutableStateOf(false) }
    OverlayDialog(
        title = if (deleteAllWeeks) "删除课程" else "删除本周课程",
        summary = if (deleteAllWeeks) {
            "确定要删除「${course?.name}」的全部课程吗？\n此操作不可撤销。"
        } else {
            "确定要删除「${course?.name}」在第${week}周的课程吗？\n此操作不可撤销。"
        },
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (deleteAllWeeks) ToggleableState.On else ToggleableState.Off,
                    onClick = {
                        deleteAllWeeks = !deleteAllWeeks
                    }
                )
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "删除全部周",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (deleteAllWeeks) "删除该课程的所有周次" else "关闭则仅删除第${week}周",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
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
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        course?.let {
                            if (deleteAllWeeks) viewModel.deleteCourse(it.id)
                            else viewModel.deleteCourseForWeek(it.id, week)
                        }
                        onDismiss()
                    },
                    textColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PasteRangeDialog(
    show: Boolean,
    courseName: String,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onPaste: (allWeeks: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        title = "粘贴课程",
        summary = "选择「${courseName}」的粘贴范围",
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = "全部周",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPaste(true)
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = "当前周",
                textColor = MiuixTheme.colorScheme.primary,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPaste(false)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RescheduleConflictDialog(
    show: Boolean,
    source: Course?,
    target: Course?,
    /** 目标位数据坐标（星期, 起始节次）：调课日列要映射后的星期，几何/动画不走这里 */
    dropTarget: Pair<Int, Int>?,
    /** 目标位数据周次：调课日 = followWeek；交换/覆盖写库按它拆周 */
    dropTargetWeek: Int,
    draggedWeek: Int,
    viewModel: CourseViewModel,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onCancel: () -> Unit,
    onOverwriteResolved: () -> Unit,
    onSwapResolved: () -> Unit,
    onOverwrite: (Set<String>) -> Unit,
    onSwap: (Set<String>) -> Unit,
) {
    OverlayDialog(
        title = "该位置已有课程",
        summary = if (target != null && source != null && dropTarget != null) {
            "「${source.name}」与「${target.name}」的位置冲突\n" +
                    "覆盖：删除「${target.name}」本周的课程，并把「${source.name}」调到此位置\n" +
                    "交换：互换本周两节课的位置"
        } else {
            "该位置已有课程，要如何处理？"
        },
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onCancel
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = "取消",
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCancel()
                },
            )
            TextButton(
                text = "覆盖",
                textColor = Color(0xFFFF9800),
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (source != null && dropTarget != null && target != null) {
                        val sectionSpan = source.endSection - source.startSection
                        val targetEnd = dropTarget.second + sectionSpan
                        // 源课在 draggedWeek，目标位在 dropTargetWeek（调课日两者可能不同 → 跨周移动）
                        // 注意 targetWeek 是最后一个参数，别插到中间（全是 Int，错位不报错）
                        viewModel.overwriteCourseForWeek(
                            source.id, draggedWeek,
                            dropTarget.first, dropTarget.second, targetEnd,
                            dropTargetWeek
                        )
                        val sourceCourses = viewModel.getCoursesAtSlot(
                            draggedWeek, source.dayOfWeek, source.startSection, source.endSection
                        ).filter { it.id != source.id && !it.isActiveInWeek(draggedWeek) }
                        if (sourceCourses.isNotEmpty()) {
                            onOverwrite(sourceCourses.map { it.id }.toSet())
                        }
                    }
                    onOverwriteResolved()
                },
            )
            TextButton(
                text = "交换",
                textColor = MiuixTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (source != null && target != null) {
                        // 源课在 draggedWeek、冲突课在 dropTargetWeek，两侧各拆一周互换
                        viewModel.swapCoursesForWeek(source.id, target.id, draggedWeek, dropTargetWeek)
                        val sourceCourses = viewModel.getCoursesAtSlot(
                            draggedWeek, source.dayOfWeek, source.startSection, source.endSection
                        ).filter {
                            it.id != source.id && it.id != target.id && !it.isActiveInWeek(
                                draggedWeek
                            )
                        }
                        val conflictCourses = viewModel.getCoursesAtSlot(
                            dropTargetWeek, target.dayOfWeek, target.startSection, target.endSection
                        ).filter {
                            it.id != target.id && it.id != source.id && !it.isActiveInWeek(
                                dropTargetWeek
                            )
                        }
                        val allAnimated = (sourceCourses + conflictCourses).map { it.id }.toSet()
                        if (allAnimated.isNotEmpty()) {
                            onSwap(allAnimated)
                        }
                    }
                    onSwapResolved()
                },
            )
        }
    }
}

@Composable
private fun ShiftLoadingOverlay(
    show: Boolean,
    onShiftReady: () -> Unit,
    onHide: () -> Unit,
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(100)),
        exit = fadeOut(animationSpec = tween(100))
    ) {
        LaunchedEffect(Unit) {
            delay(100.milliseconds)
            onShiftReady()
            delay(500.milliseconds)
            onHide()
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

@Composable
private fun MorePopupMenus(
    showMorePopup: Boolean,
    onMorePopupDismiss: () -> Unit,
    onMorePopupExpand: () -> Unit = {},
    showTodayMorePopup: Boolean,
    onTodayMorePopupDismiss: () -> Unit,
    onTodayMorePopupExpand: () -> Unit = {},
    morePopupFraction: Animatable<Float, *>,
    todayMorePopupFraction: Animatable<Float, *>,
    scheduleMenuOffset: () -> Offset = { Offset.Zero },
    todayMenuOffset: () -> Offset = { Offset.Zero },
    /**
     * 「更多」占位槽实测顶部（root 坐标 px）。传 state 而非 Float：
     * 定位要在**绘制期**读（graphicsLayer 的 block 内），首帧才不会先画在兜底位置上再跳。
     */
    moreSlotTopPx: MutableFloatState = mutableFloatStateOf(0f),
    scheduleMaterialAlpha: Float = 0f,
    todayMaterialAlpha: Float = 0f,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop,
    isShiftMode: Boolean = false,
    onJumpWeek: () -> Unit,
    onCourseManage: () -> Unit,
    onEnterCustomize: () -> Unit,
    onJumpToDate: () -> Unit,
    onMoreBackProgress: (Float) -> Unit = {},
    onMoreBackCancelled: () -> Unit = {},
    onTodayMoreBackProgress: (Float) -> Unit = {},
    onTodayMoreBackCancelled: () -> Unit = {},
    // 平板右上角不显示「更多」：跳转/管理/外观都已在侧栏里
    enabled: Boolean = true,
) {
    if (!enabled) {
        // 兜底：若进入平板前菜单正开着，强制收起，避免残留展开态
        LaunchedEffect(Unit) {
            if (showMorePopup) onMorePopupDismiss()
            if (showTodayMorePopup) onTodayMorePopupDismiss()
        }
        return
    }
    if (showMorePopup) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onMorePopupDismiss() }
        )
    }
    if (showTodayMorePopup) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onTodayMorePopupDismiss() }
        )
    }
    // 「更多」锚点纵坐标：**绘制期**定位
    val fallbackAnchorTopPx = with(LocalDensity.current) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        (if (statusBarTop > 0.dp) statusBarTop - 20.dp else 17.dp)
            .coerceAtLeast(0.dp)
            .toPx()
    }
    val shadowPaddingPx = with(LocalDensity.current) {
        com.haooz.chedule.ui.basic.LiquidGlassDropdownShadowPadding.toPx()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = false
                // moreSlotTopPx 是占位槽顶边，减掉阴影外扩才是内容顶边
                val measured = moreSlotTopPx.floatValue - shadowPaddingPx
                translationY = if (measured > 0f) measured else fallbackAnchorTopPx
            }
            .offset(x = 9.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        // 课程表页「更多」
        LiquidGlassDropdownMenu(
            show = showMorePopup,
            backdrop = liquidGlassBackdrop,
            fraction = morePopupFraction,
            onDismiss = onMorePopupDismiss,
            onBackProgress = onMoreBackProgress,
            onBackCancelled = onMoreBackCancelled,
            triggerIcon = MiuixIcons.More,
            triggerContentDescription = "更多",
            onExpand = onMorePopupExpand,
            offsetPx = scheduleMenuOffset,
            materialAlpha = scheduleMaterialAlpha,
        ) {
            LiquidGlassDropdownMenuItem(
                text = "跳转周数",
                onClick = {
                    onMorePopupDismiss()
                    onJumpWeek()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Basic.FastForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
            if (!isShiftMode) {
                LiquidGlassDropdownMenuItem(
                    text = "课程管理",
                    onClick = {
                        onMorePopupDismiss()
                        onCourseManage()
                    },
                    icon = {
                        Icon(
                            imageVector = MiuixIcons.Backup,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                )
            }
            LiquidGlassDropdownMenuItem(
                text = "课表外观",
                onClick = {
                    onMorePopupDismiss()
                    onEnterCustomize()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Background,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
        }
        // 今日页「更多」
        LiquidGlassDropdownMenu(
            show = showTodayMorePopup,
            backdrop = liquidGlassBackdrop,
            fraction = todayMorePopupFraction,
            onDismiss = onTodayMorePopupDismiss,
            onBackProgress = onTodayMoreBackProgress,
            onBackCancelled = onTodayMoreBackCancelled,
            triggerIcon = MiuixIcons.More,
            triggerContentDescription = "更多",
            onExpand = onTodayMorePopupExpand,
            offsetPx = todayMenuOffset,
            materialAlpha = todayMaterialAlpha,
        ) {
            LiquidGlassDropdownMenuItem(
                text = "跳转日期",
                onClick = {
                    onTodayMorePopupDismiss()
                    onJumpToDate()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Basic.FastForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
            LiquidGlassDropdownMenuItem(
                text = "课程管理",
                onClick = {
                    onTodayMorePopupDismiss()
                    onCourseManage()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Backup,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
            LiquidGlassDropdownMenuItem(
                text = "课表外观",
                onClick = {
                    onTodayMorePopupDismiss()
                    onEnterCustomize()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Background,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight", "UseOfNonLambdaOffsetOverload", "UseKtx",
    "AutoboxingStateCreation"
)
@Composable
fun CourseScheduleApp(privacyConsented: Boolean = true) {
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
    val settingsScrollBehavior = rememberSharedScrollBehavior()
    val todayScrollBehavior = rememberSharedScrollBehavior()
    val scheduleScrollBehavior = rememberSharedScrollBehavior()

    LaunchedEffect(privacyConsented) {
        // 合规：未同意隐私政策前不进行云同步等网络行为
        if (!privacyConsented) return@LaunchedEffect
        val syncManager = com.haooz.chedule.data.SyncManager.getInstance()
        val repository = com.haooz.chedule.data.CourseRepository()
        val webDavManager = com.haooz.chedule.data.WebDavManager {
            com.haooz.chedule.reminder.CourseReminderHelper.onHolidayDataChanged(context)
        }
        syncManager.start(repository, webDavManager)
        // 备份/恢复后刷新 ViewModel 内存缓存
        syncManager.onSyncCompleted = {
            viewModel.refreshEssentialData()
            viewModel.reloadCourses()
            settingsViewModel.refreshSettings()
        }
    }

    val isDark = isAppDarkTheme()
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    // 供今日/课程表卡片玻璃采样：录的是共享壁纸层，不是页内透明占位
    val sharedWallpaperBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val configuredSectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val totalSections = morningSections + afternoonSections + eveningSections
    val activity = LocalActivity.current as? MainActivity
    val resumeCount = activity?.resumeCount ?: 0
    val holidayDataRevision by com.haooz.chedule.data.HolidayManager.dataRevision.collectAsState()
    // 只在「返回」时刷新；冷启动首次 onResume 时 ViewModel 刚加载完，再全量刷会拖慢首屏
    LaunchedEffect(resumeCount) {
        // 作息按日期自动切换：跨过生效日期后补一次提醒重排。
        // 只是比对 prefs 里记的 key，几乎零开销，所以冷启动这一次也跑
        withContext(Dispatchers.IO) {
            com.haooz.chedule.data.CourseRepository().syncRoutineAfterDateChange()
        }
        if (resumeCount > 1) {
            withContext(Dispatchers.IO) {
                settingsViewModel.refreshSettings()
                scheduleViewModel.refreshScheduleList()
            }
            viewModel.reloadCourses()
        }
    }
    // 节假日/调休保存不走课程 reload；resume 时对比 HolidayManager 版本，变了才 bump dataVersion
    var seenHolidayVersion by remember {
        mutableLongStateOf(
            com.haooz.chedule.data.HolidayManager.getVersion()
        )
    }
    LaunchedEffect(resumeCount, holidayDataRevision) {
        val holidayV = com.haooz.chedule.data.HolidayManager.getVersion()
        if (holidayV != seenHolidayVersion) {
            seenHolidayVersion = holidayV
            viewModel.bumpDataVersion()
        }
    }
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val navBarStyle = if (isTablet) "rail" else "standard"
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHPx = with(density) { config.screenHeightDp.dp.toPx() }
    val latestScreenWPx by rememberUpdatedState(screenWPx)
    val latestScreenHPx by rememberUpdatedState(screenHPx)

    // 首帧后预热 RenderEffect，避免首次开 BlurBottomSheet 掉帧
    val warmupBlurPx = with(density) { 24.dp.toPx() }
    var blurWarmupReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { blurWarmupReady = true }
    if (blurWarmupReady) {
        Box(
            modifier = Modifier
                .size(1.dp)
                .graphicsLayer { alpha = 0f }
                .then(
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        Modifier.drawBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = { androidx.compose.foundation.shape.RoundedCornerShape(36.dp) },
                            effects = {
                                vibrancy()
                                blur(warmupBlurPx)
                            },
                            highlight = null
                        )
                    } else Modifier
                )
        )
    }
    // 侧栏展开状态用全局对象；内容让位只在 layout 读进度，避免整树每帧重组
    // 侧栏伸缩后强制续录 + 顶栏糊层重采样：否则采样停在旧帧，切页才会恢复
    val railBlurResampleEpoch = remember { mutableIntStateOf(0) }
    val railForceRecordFrames = remember { intArrayOf(0) }
    LaunchedEffect(com.haooz.chedule.ui.components.TabletNavSideState.expanded, navBarStyle) {
        if (navBarStyle != "rail") return@LaunchedEffect
        railForceRecordFrames[0] = 12
        delay(360.milliseconds)
        railBlurResampleEpoch.intValue++
    }
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
    var detailTargetWeek by remember { mutableIntStateOf(0) }
    var hiddenCourseIds by remember { mutableStateOf(setOf<String>()) }

    var isDraggingCard by remember { mutableStateOf(false) }
    var draggingCourseIds by remember { mutableStateOf(setOf<String>()) }
    var draggedCardCourse by remember { mutableStateOf<Course?>(null) }
    var draggedCardPosition by remember { mutableStateOf(Offset.Zero) }
    var draggedCardSize by remember { mutableStateOf(Offset.Zero) }
    // 速度/采样时间只在 spring 起始读；offset 走 floatingOffset* 的 graphicsLayer，不进组合
    val dragMotion = remember { DragMotionHolder() }
    var draggedCardBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
    var draggedWeek by remember { mutableIntStateOf(1) }
    // 源卡片所在的**列**星期（几何坐标）。调课日列的数据星期 ≠ 列星期，
    // 落点原格高亮、几何吸附都要用列，写库才用数据星期
    var draggedColumnDay by remember { mutableIntStateOf(1) }
    // 拖拽落点检测用网格几何
    var gridGeometry by remember {
        mutableStateOf<ScheduleGridGeometry?>(
            null
        )
    }
    // (dayOfWeek, startSection)，null=无有效落点
    var pendingDropTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var animateInCourseIds by remember { mutableStateOf(setOf<String>()) }
    var showRescheduleConflictDialog by remember { mutableStateOf(false) }
    var pendingConflictCourse by remember { mutableStateOf<Course?>(null) }
    // 退出动画期间保持 true，结束后原卡片 alpha 恢复 1
    var floatingCardVisible by remember { mutableStateOf(false) }
    // 手指**正拖着**浮层：onCourseDragStart 置真、onCourseDragEnd 置假。
    // 不能拿 floatingCardVisible 当高光开关 —— 它从长按那一刻就为 true，且要等退场动画
    // （约 360ms）跑完才回 false，于是「长按没拖也亮」+「松手后等动画结束才开始灭」。
    var isFloatingCardDragging by remember { mutableStateOf(false) }
    // 入场 0.94→1.04，退场 1.04→1.0
    val floatingScale = remember { Animatable(0.94f) }
    // 吸附期间用 floatingOffsetAnim 替代 draggedCardOffset
    var isSnapping by remember { mutableStateOf(false) }
    // 浮层平移：拖拽回调非挂起，用 mutableFloatStateOf；只在 graphicsLayer 读，不触发组合
    val floatingOffsetX = remember { mutableFloatStateOf(0f) }
    val floatingOffsetY = remember { mutableFloatStateOf(0f) }

    // 高光开关 / 淡变值：声明在 Scaffold 之前 —— Scaffold 内容里的提饱和节点要用它。
    // 开关 = 正在拖 && 浮层还活着：长按没拖不亮；一松手 onCourseDragEnd 立刻置 false，
    // 立刻开始淡出，不用等 floatingCardVisible 那 360ms 退场动画跑完。
    val glowShowing = isFloatingCardDragging && floatingCardVisible
    val glowAlpha by animateFloatAsState(
        targetValue = if (glowShowing) 1f else 0f,
        animationSpec = tween(150),
        label = "dragCardGlowAlpha",
    )

    suspend fun animateFloatState(
        state: androidx.compose.runtime.MutableFloatState,
        target: Float,
        spec: androidx.compose.animation.core.AnimationSpec<Float>,
        initialVelocity: Float = 0f
    ) {
        animate(
            initialValue = state.floatValue,
            targetValue = target,
            animationSpec = spec,
            initialVelocity = initialVelocity
        ) { value, _ -> state.floatValue = value }
    }

    // 粘贴飞行：复用长按浮层，直线飞向目标格
    var isPasteFlight by remember { mutableStateOf(false) }
    // 落地冲击波：周围课程卡按距离延迟涟漪
    var landRippleCenter by remember { mutableStateOf(Offset.Zero) }
    var landRippleToken by remember { mutableIntStateOf(0) }
    // 冲突悬停：浮层停在目标卡上方等待用户选择
    var isConflictHover by remember { mutableStateOf(false) }
    var conflictHoverBobJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 交换时目标课作为第二浮层交叉飞行
    var swapFlightVisible by remember { mutableStateOf(false) }
    var swapFlightCourse by remember { mutableStateOf<Course?>(null) }
    var swapFlightOriginCenter by remember { mutableStateOf(Offset.Zero) }
    var swapFlightWidth by remember { mutableFloatStateOf(0f) }
    val swapFlightOffsetX = remember { Animatable(0f) }
    val swapFlightOffsetY = remember { Animatable(0f) }
    val swapFlightScale = remember { Animatable(1f) }
    var shortcutMenuCourse by remember { mutableStateOf<Course?>(null) }
    var shortcutMenuVisible by remember { mutableStateOf(false) }
    var shortcutMenuPosition by remember { mutableStateOf(Offset.Zero) }
    var shortcutMenuSize by remember { mutableStateOf(IntSize.Zero) }
    var shortcutMenuBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
    // 左移时菜单右边缘对齐卡片右边缘
    var shortcutMenuAnchorWidth by remember { mutableFloatStateOf(0f) }
    // (星期, 起始节次)；粘贴目标=长按格
    // columnDay 用于格子高亮；addDay/addWeek 供「添加」默认落到调课来源星期/周
    var emptyCellMenuTarget by remember { mutableStateOf<EmptyCellMenuTarget?>(null) }
    // 页面会话级剪贴板，再次复制覆盖，粘贴后保留
    var copiedCourseForPaste by remember { mutableStateOf<Course?>(null) }
    var showPasteRangeDialog by remember { mutableStateOf(false) }
    var pasteRangeTarget by remember { mutableStateOf<PasteTarget?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteConfirmCourse by remember { mutableStateOf<Course?>(null) }

    var showCustomizePage by remember { mutableStateOf(false) }
    var customizeSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var snapshotCoverBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isCustomizeExiting by remember { mutableStateOf(false) }
    var customizeExitTargetScale by remember { mutableFloatStateOf(1f) }
    val customizeExitScale = remember { Animatable(1f) }
    val customizeExitAlpha = remember { Animatable(1f) }
    var isWindowCutoutActive by remember { mutableStateOf(false) }
    // 动画只作用于快照覆盖层，避免双缩放叠加闪烁
    var customizeCoverActive by remember { mutableStateOf(false) }
    val customizeCoverScale = remember { Animatable(1f) }
    val customizeCoverAlpha = remember { Animatable(1f) }
    val wallpaperRepository = remember { com.haooz.chedule.data.CourseRepository() }
    var combinations by remember { mutableStateOf(listOf<com.haooz.chedule.data.Combination>()) }
    var currentCombinationIndex by remember { mutableIntStateOf(0) }
    var wallpaperBitmap by remember { mutableStateOf(MainActivity.cachedWallpaperBitmap) }
    var wallpaperOffset by remember { mutableStateOf(MainActivity.cachedWallpaperOffset) }
    var wallpaperScale by remember { mutableFloatStateOf(MainActivity.cachedWallpaperScale) }
    // offset/scale 当前对应的屏幕尺寸；旋转后按此重映射，避免像素值跨方向复用
    var wallpaperScreenRef by remember { mutableStateOf(screenWPx to screenHPx) }
    // 截快照时临时按该搭配的壁纸亮暗覆盖主题
    var captureThemeActive by remember { mutableStateOf(false) }
    var captureThemeIsDark by remember { mutableStateOf<Boolean?>(null) }

    // 壁纸主题锁定：课程表页有壁纸时按测光强制浅/深色；今日页仅开启显示壁纸时锁定；设置页跟随系统
    val currentComb = combinations.getOrNull(currentCombinationIndex)
    val currentCombIsLight = currentComb?.wallpaperIsLight
    // 壁纸异步加载，首帧同步读持久化结果，避免主题跳变
    val initialCombWallpaperIsLight = remember {
        ScheduleAppearance.getWallpaperIsLight()
    }
    // 搭配未加载（首帧）才退回 initial，避免无壁纸搭配误用初始测光
    val combIsLight = if (currentComb == null) initialCombWallpaperIsLight else currentCombIsLight
    
    // 独立偏好 key，与全局主题开关隔离
    val persistedScheduleThemeMode = rememberScheduleThemeMode()
    // 编辑中的临时档位：「应用」才落盘
    var pendingScheduleThemeMode by remember { mutableStateOf<ThemeMode?>(null) }
    val scheduleThemeMode = pendingScheduleThemeMode ?: persistedScheduleThemeMode
    // 壁纸强制主题：不依赖 selectedTab，三页各自预先算好，切页不再改主题
    // 无壁纸时「默认主题」不生效
    val wallpaperForcedDark: Boolean? = if (isShiftMode || combIsLight == null) {
        null
    } else {
        when (scheduleThemeMode) {
            ThemeMode.FOLLOW_WALLPAPER -> !combIsLight
            ThemeMode.FOLLOW_APP -> null
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }
    // 各页预先固定的主题（与 selectedTab 无关）
    // 今日页与课程表页壁纸恒同步，故两者共用同一壁纸锁定主题
    val todayPageForcedDark = wallpaperForcedDark
    val settingsPageForcedDark: Boolean? = null
    // 顶栏/底栏等 chrome 跟当前页有效主题
    val forcedDark = when {
        isShiftMode -> null
        showCustomizePage -> wallpaperForcedDark
        selectedTab == 2 -> null
        selectedTab == 1 -> wallpaperForcedDark
        selectedTab == 0 -> todayPageForcedDark
        else -> null
    }
    val effectiveIsDark = forcedDark ?: isDark
    val appSettingDark = rememberAppSettingDark()
    // 状态栏跟页面实际深浅；导航栏图标始终跟应用设置
    LaunchedEffect(effectiveIsDark, appSettingDark) {
        activity?.applyThemeAwareSystemBars(effectiveIsDark)
        activity?.applyNavigationBarIsDark(appSettingDark)
    }

    // 已应用快照，开洞编辑取消时回退
    var savedWallpaperBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var savedWallpaperOffset by remember { mutableStateOf(wallpaperOffset) }
    var savedWallpaperScale by remember { mutableFloatStateOf(wallpaperScale) }
    var savedAppearance by remember { mutableStateOf(com.haooz.chedule.data.AppearanceConfig()) }
    // 进入搭配页时的原始搭配；滑动切换不更新（退出非应用时还原）
    var originalCombinationIndex by remember { mutableIntStateOf(0) }
    var originalWallpaperBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var originalWallpaperOffset by remember { mutableStateOf(wallpaperOffset) }
    var originalWallpaperScale by remember { mutableFloatStateOf(wallpaperScale) }
    var originalSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 退出（非应用）时还原主题锁定
    var originalWallpaperIsLight by remember { mutableStateOf<Boolean?>(null) }
    // 整体还原，避免编辑回调字段残留
    var originalCombination by remember { mutableStateOf<com.haooz.chedule.data.Combination?>(null) }
    var isApplyingCustomize by remember { mutableStateOf(false) }
    var pendingEnterCutout by remember { mutableStateOf(false) }

    // 外观从当前搭配派生，编辑只写 combinations；未加载时回退启动缓存防冷启动闪默认值
    fun currentAppearance(): com.haooz.chedule.data.AppearanceConfig =
        combinations.getOrNull(currentCombinationIndex)
            ?.let { com.haooz.chedule.data.AppearanceConfig.fromCombination(it) }
            ?: MainActivity.cachedAppearance

    fun applyAppearance(value: com.haooz.chedule.data.AppearanceConfig) {
        val idx = currentCombinationIndex
        if (idx in combinations.indices) {
            combinations = combinations.toMutableList().also { list ->
                list[idx] = list[idx].copy(
                    cardBlurRadius = value.cardBlurRadius,
                    cardAlpha = value.cardAlpha.coerceIn(0f, 1f),
                    cardSurfaceAlpha = value.cardSurfaceAlpha.coerceIn(0f, 1f),
                    cardHeight = value.cardHeight,
                    cardCornerRadius = value.cardCornerRadius,
                    wallpaperBrightness = value.wallpaperBrightness,
                    showBreakDividers = value.showBreakDividers,
                    cardContentAlignment = value.cardContentAlignment,
                    cardTextColor = value.cardTextColor,
                    cardTextScale = value.cardTextScale,
                    showClassroom = value.showClassroom,
                    showTeacher = value.showTeacher,
                    cardRefraction = value.cardRefraction,
                    wallpaperBlur = value.wallpaperBlur
                )
            }
        }
    }

    /**
     * 搭配读取唯一入口：冷启动 IO 路径与伴生缓存路径都走这里。
     *
     * 两份逐字段复制的构造曾是 bug 温床——cardRefraction 只加进了 IO 路径，
     * 导致伴生缓存命中时折射强度被静默重置为 DEFAULT。新增外观字段只需改此处。
     */
    fun buildCombination(bitmap: android.graphics.Bitmap?) =
        com.haooz.chedule.data.Combination(
            bitmap = bitmap,
            offset = Offset(
                ScheduleAppearance.getOffsetX(),
                ScheduleAppearance.getOffsetY()
            ),
            scale = ScheduleAppearance.getScale(),
            snapshot = null,
            cardBlurRadius = ScheduleAppearance.getCardBlur(),
            cardAlpha = ScheduleAppearance.getCardAlpha(),
            cardSurfaceAlpha = ScheduleAppearance.getCardSurfaceAlpha(),
            cardHeight = ScheduleAppearance.getCardHeight(),
            cardCornerRadius = ScheduleAppearance.getCardCornerRadius(),
            wallpaperBrightness = ScheduleAppearance.getWallpaperBrightness(),
            showBreakDividers = ScheduleAppearance.getShowBreakDividers(),
            cardContentAlignment = ScheduleAppearance.getCardContentAlignment(),
            cardTextColor = ScheduleAppearance.getCardTextColor(),
            cardTextScale = ScheduleAppearance.getCardTextScale(),
            showClassroom = ScheduleAppearance.getShowClassroom(),
            showTeacher = ScheduleAppearance.getShowTeacher(),
            cardRefraction = ScheduleAppearance.getCardRefraction(),
            wallpaperIsLight = ScheduleAppearance.getWallpaperIsLight(),
            wallpaperBlur = ScheduleAppearance.getWallpaperBlur()
        )

    // 加载单搭配；有伴生缓存则跳过 IO
    LaunchedEffect(Unit) {
        val cached = MainActivity.cachedWallpaperBitmap

        val comb = if (cached != null) {
            buildCombination(cached)
        } else {
            withContext(Dispatchers.IO) { buildCombination(ScheduleAppearance.loadWallpaper()) }
        }
        // 单搭配恒为下标 0
        combinations = if (comb != null) listOf(comb) else emptyList()
        currentCombinationIndex = 0

        val curr = combinations.getOrNull(0)
        if (curr != null) {
            val curW = latestScreenWPx
            val curH = latestScreenHPx
            val refW = ScheduleAppearance.getOffsetRefW()
            val refH = ScheduleAppearance.getOffsetRefH()
            // 有参考尺寸且与当前屏不一致时重映射；旧数据 ref=0 时原样加载
            val (mappedOffset, mappedScale) = if (refW > 0f && refH > 0f && (refW != curW || refH != curH)) {
                remapWallpaperForScreen(curr.offset, curr.scale, curr.bitmap, refW, refH, curW, curH)
            } else {
                curr.offset to curr.scale
            }
            wallpaperBitmap = curr.bitmap
            wallpaperOffset = mappedOffset
            // 不把当前方向的 minScale 写回用户 scale：绘制层已 max(scale, minScale)，
            // 这里烘焙会导致旋转回原方向时缩放被抬高、画面错位
            wallpaperScale = mappedScale
            wallpaperScreenRef = curW to curH
            savedWallpaperBitmap = curr.bitmap
            savedWallpaperOffset = mappedOffset
            savedWallpaperScale = mappedScale
            savedAppearance = com.haooz.chedule.data.AppearanceConfig.fromCombination(curr)
            originalWallpaperBitmap = curr.bitmap
            originalWallpaperOffset = mappedOffset
            originalWallpaperScale = mappedScale
            if (mappedOffset != curr.offset || mappedScale != curr.scale) {
                combinations = combinations.toMutableList().also { list ->
                    list[0] = list[0].copy(offset = mappedOffset, scale = mappedScale)
                }
            }
            MainActivity.cachedWallpaperOffset = mappedOffset
            MainActivity.cachedWallpaperScale = mappedScale
        }
    }

    // 横竖屏（含自由窗口）尺寸变化：把当前显示中的变换从旧屏重映射到新屏
    LaunchedEffect(screenWPx, screenHPx) {
        val (prevW, prevH) = wallpaperScreenRef
        if (prevW == screenWPx && prevH == screenHPx) return@LaunchedEffect
        val bmp = wallpaperBitmap
        if (bmp != null && prevW > 0f && prevH > 0f) {
            val (newOffset, newScale) = remapWallpaperForScreen(
                wallpaperOffset, wallpaperScale, bmp, prevW, prevH, screenWPx, screenHPx
            )
            wallpaperOffset = newOffset
            wallpaperScale = newScale
            val idx = currentCombinationIndex
            if (idx in combinations.indices) {
                combinations = combinations.toMutableList().also { list ->
                    list[idx] = list[idx].copy(offset = newOffset, scale = newScale)
                }
            }
            MainActivity.cachedWallpaperOffset = newOffset
            MainActivity.cachedWallpaperScale = newScale
        }
        wallpaperScreenRef = screenWPx to screenHPx
    }
    val cutoutMainScale = remember { Animatable(1f) }
    var cutoutCenterYRatio by remember { mutableFloatStateOf(0.5f) }
    // 与 CustomizeScheduleScreen 共享实例，直接读 .value 同帧同步
    val sheetOffsetY = remember { Animatable(0f) }
    LaunchedEffect(isWindowCutoutActive) {
        if (isWindowCutoutActive) {
            val c = combinations.getOrNull(currentCombinationIndex)
            if (c != null) {
                // combinations 在加载/旋转时已同步为当前屏坐标系的 offset/scale
                wallpaperBitmap = c.bitmap
                wallpaperOffset = c.offset
                wallpaperScale = c.scale
                wallpaperScreenRef = latestScreenWPx to latestScreenHPx
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
    var mainContentSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 用布尔量而不用整屏位图当哨兵，避免白养 ~10MB Bitmap
    var switchOverlayActive by remember { mutableStateOf(false) }
    var switchCardSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 形变插值起止依据（窗口坐标系）
    var switchCardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // 退出时拿不到截图则退而用它
    var switchCurrentCardBounds by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(
            null
        )
    }
    // 页面坐标系 → 窗口坐标系的换算偏移
    var switchContentRootX by remember { mutableFloatStateOf(0f) }

    // 状态提升到 Activity 层，return@Scaffold 不会销毁
    val scheduleScrollState = rememberScrollState()
    val scheduleSelectedCourse = remember { mutableStateOf<Course?>(null) }
    val scheduleSelectedCourses = remember { mutableStateOf<List<Course>>(emptyList()) }
    val scheduleShowCourseDetail = remember { mutableStateOf(false) }

    val todayListState = rememberLazyListState()
    // 今日页各日期页独立 LazyListState，这里只收「是否在滚」供液态玻璃重录
    val todayListScrollInProgress = remember { mutableStateOf(false) }
    var switchContentRootY by remember { mutableFloatStateOf(0f) }
    var switchAnimJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var switchAnimForward by remember { mutableStateOf(false) }
    var switchAnimRunning by remember { mutableStateOf(false) }
    // 截图前需 join 等新课表数据就绪
    var switchReloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val switchAnimProgress = remember { Animatable(0f) }
    val backgroundScale = remember { Animatable(1f) }
    val managePageBlurRadius = remember { Animatable(0f) }
    // 仅课程长按菜单模糊；空白格菜单不模糊
    val shortcutMenuBlurRadius = remember { Animatable(0f) }
    LaunchedEffect(shortcutMenuVisible, shortcutMenuCourse) {
        if (shortcutMenuVisible && shortcutMenuCourse != null) {
            launch { shortcutMenuBlurRadius.animateTo(10f, tween(280)) }
        } else {
            launch { shortcutMenuBlurRadius.animateTo(0f, tween(250)) }
        }
    }
    val switchReturnBgScrim = remember { Animatable(0f) }
    val screenGraphicsLayer = rememberGraphicsLayer()
    // record() 会把整棵主内容树再画一遍，只在截图前录一帧，避免常驻翻倍绘制开销
    class MainSnapshotRequester {
        var lastRecordedToken: Int = 0
    }
    val mainSnapshotRequester = remember { MainSnapshotRequester() }
    var mainSnapshotToken by remember { mutableIntStateOf(0) }
    val captureMainContentBitmap: suspend () -> android.graphics.Bitmap? = {
        mainSnapshotToken++
        // 等两帧：draw 录制完成 + 帧已提交
        withFrameNanos { }
        withFrameNanos { }
        // 必须单独 catch OutOfMemoryError：Bitmap 像素在 native 堆，全屏 toImageBitmap
        // 读回是低端机上最容易 OOM 的一步，而 OOM 是 Error 不是 Exception，
        // 只写 catch(Exception) 等于没兜底 —— 直接把进程带走。
        // 返回 null：所有消费方都已能处理「没有快照」（切换页照样走完动画）。
        try {
            screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
        } catch (_: OutOfMemoryError) {
            com.haooz.chedule.ui.utils.CrashLogHelper.trace("主页", "main_snapshot_oom")
            null
        } catch (_: Exception) {
            null
        }
    }
    var blurSnapshotJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val hapticFeedback = LocalHapticFeedback.current

    val calendar = Calendar.getInstance()
    val currentDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val dataVersion by viewModel.dataVersion.collectAsState()
    val teachingWeekRepository = remember(context) { com.haooz.chedule.data.CourseRepository.getInstance() }
    val teachingWeekRules = remember(teachingWeekRepository, dataVersion, classStartTime) {
        teachingWeekRepository.getTeachingWeekReorganizations()
    }
    // 节假日/调休保存后需能重算跳周；resume 时刷新版本号
    val holidayVersion = remember(resumeCount, holidayDataRevision, context) {
        com.haooz.chedule.data.HolidayManager.getVersion()
    }

    val basePage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
    val autoAdvancePage = remember(currentWeek, totalWeeks, currentDayOfWeek, smartWeekend, holidayVersion, dataVersion, teachingWeekRules) {
        if (settingsViewModel.shouldAdvanceToNextWeek(currentDayOfWeek, currentWeek)) {
            (basePage + 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
        } else basePage
    }

    val pagerState = rememberPagerState(
        initialPage = autoAdvancePage,
        pageCount = { totalWeeks }
    )

    val todayMaxDateOffset = 1000
    val todayPagerState = rememberPagerState(
        initialPage = todayMaxDateOffset,
        pageCount = { todayMaxDateOffset * 2 }
    )

    // 主 tab 平移容器：今日/课程表/我的/课程管理/切换课表（排班模式为排班/设置），仅点底栏/侧栏 tab 驱动
    // 手机底栏只有 3 个 tab，后两页（课程管理/切换课表）只有平板侧栏才到得了。
    // 手机端按 5 页建会让这两页也常驻组合树，每次重组白陪跑。
    val mainPagerPageCount = if (isShiftMode) 2 else if (navBarStyle == "rail") 5 else 3
    val mainPagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, mainPagerPageCount - 1),
        pageCount = { mainPagerPageCount }
    )
    // 程序化切 tab 期间为 true，避免 currentPage 在动画中途把 selectedTab 拉回去
    var mainTabProgrammatic by remember { mutableStateOf(false) }

    // 当前可见页背后是不是壁纸 —— 决定高光亮度：近白底只能压到 0.05，壁纸背景有余量 → 0.1
    // （见 InteractiveHighlight 的 peak）。
    // 必须在组合期算出来再交给 SideEffect：只在 SideEffect 里读 currentPage 不会订阅，
    // 切页时就不更新了。0 今日 / 1 课程表（只有这两页有壁纸）；排班模式页数不同，排除。
    val pageBehindHasWallpaper = wallpaperBitmap != null &&
        !isShiftMode &&
        mainPagerState.currentPage in 0..1
    SideEffect { PageBackdropSnapshot.hasWallpaper.value = pageBehindHasWallpaper }

    // 二级页侧栏点选主 tab（无转场回来后处理）
    LaunchedEffect(com.haooz.chedule.ui.components.TabletNavSideState.pendingMainTab) {
        val pending = com.haooz.chedule.ui.components.TabletNavSideState.pendingMainTab
        if (pending in 0..4 && shiftModeInitialized) {
            // 手机端只有 3 页，侧栏带过来的页号要按实际页数收口
            val target = pending.coerceAtMost(mainPagerPageCount - 1)
            selectedTab = target
            mainPagerState.scrollToPage(target)
            com.haooz.chedule.ui.components.TabletNavSideState.pendingMainTab = -1
        }
    }

    LaunchedEffect(isShiftMode) {
        if (shiftModeInitialized) {
            selectedTab = if (isShiftMode) 0 else if (defaultHomepage == "今日") 0 else 1
            mainPagerState.scrollToPage(selectedTab)
        }
        shiftModeInitialized = true
    }

    var lastAutoPage by remember { mutableIntStateOf(autoAdvancePage) }
    var lastAutoWeek by remember { mutableIntStateOf(currentWeek) }
    LaunchedEffect(currentWeek, totalWeeks, autoAdvancePage) {
        // A rule edit must recalculate the default, but must not pull a manually browsed page back.
        if (currentWeek != lastAutoWeek || pagerState.currentPage == lastAutoPage) {
            if (pagerState.currentPage != autoAdvancePage) pagerState.scrollToPage(autoAdvancePage)
        }
        lastAutoWeek = currentWeek
        lastAutoPage = autoAdvancePage
    }

    var todaySelectedDayOfWeek by remember { mutableIntStateOf(currentDayOfWeek) }
    var todayIsToday by remember { mutableStateOf(true) }
    var scrollToTodayTrigger by remember { mutableIntStateOf(0) }

    val currentViewingWeek = pagerState.currentPage + 1
    val courses by viewModel.courses.collectAsState()
    // dataVersion + courses 引用都进 key：调课 size 可能不变，只靠 size 会让智能周末星期行停在旧值
    val dayRange = remember(
        currentViewingWeek,
        smartWeekend,
        courses,
        dataVersion,
        holidayVersion,
        configuredSectionTimes,
        morningSections,
        afternoonSections,
        eveningSections,
    ) {
        (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(currentViewingWeek)
            .filter { it in 6..7 }
    }
    val viewingIsHoliday = viewModel.isWeekHoliday(currentViewingWeek)
    val weekDates = remember(currentViewingWeek, classStartTime, teachingWeekRules) {
        try {
            val startDate = LocalDate.parse(classStartTime.replace("/", "-"))
            com.haooz.chedule.data.TeachingWeekReorganization.datesForTeachingWeek(
                startDate, currentViewingWeek, teachingWeekRules,
            )
        } catch (_: Exception) {
            emptyList()
        }
    }


    val isInFreeformWindow = activity?.isInFreeformWindow ?: false

    val screenCornerRadius = remember(isInFreeformWindow) {
        if (isInFreeformWindow) {
            20f * density.density  // 小窗默认圆角 20dp
        } else {
            ApiCompat.windowCornerRadius(activity?.window ?: return@remember 0f)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    // 内容区（切天/周）一有横向手势：立刻取消未完成的主 tab 平移，吸回当前页，避免露出隔壁页
    val cancelUnfinishedMainTabAnim by rememberUpdatedState {
        if (mainPagerState.isScrollInProgress ||
            abs(mainPagerState.currentPageOffsetFraction) > 0.001f
        ) {
            val target = mainPagerState.currentPage.coerceIn(
                0,
                (mainPagerState.pageCount - 1).coerceAtLeast(0)
            )
            coroutineScope.launch {
                mainPagerState.cancelScroll()
                mainPagerState.scrollToPage(target)
                // 底栏与当前页对齐，避免 tab 还停在未完成的目标页
                if (selectedTab != target) selectedTab = target
            }
        }
    }
    // 固定实例：lambda 每次重组都新建，不能拿它当 remember key
    val mainContentNestedScroll = remember(mainPagerState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 横向（手机主 tab）与纵向（平板主 tab）手势都取消未完成的切页动画
                if (source == NestedScrollSource.UserInput &&
                    (abs(available.x) > 1f || abs(available.y) > 1f)
                ) {
                    cancelUnfinishedMainTabAnim()
                }
                return Offset.Zero
            }
        }
    }
    val triggerLandRipple: (Offset) -> Unit = { center ->
        landRippleCenter = center
        landRippleToken++
    }
    val stopConflictHover: () -> Unit = {
        conflictHoverBobJob?.cancel()
        conflictHoverBobJob = null
        isConflictHover = false
    }
    val clearSwapFlight: () -> Unit = {
        swapFlightVisible = false
        swapFlightCourse = null
    }
    // 退场动画结束后再清状态，让原卡片 alpha 恢复 1
    val dismissFloatingCard: () -> Unit = {
        stopConflictHover()
        clearSwapFlight()
        coroutineScope.launch {
            floatingScale.animateTo(1f, tween(durationMillis = 180))
            isDraggingCard = false
            floatingCardVisible = false
            draggingCourseIds = emptySet()
            draggedCardCourse = null
            dragMotion.reset()
            pendingDropTarget = null
            isSnapping = false
            // 重置入场初值，避免下次首帧残留 1.0 造成抖动
            floatingScale.snapTo(0.94f)
            floatingOffsetX.floatValue = 0f
            floatingOffsetY.floatValue = 0f
        }
    }

    // 粘贴飞行：前半升到 peakScale，中段回 1.0，末段落地轻弹
    fun pasteFlightScaleAt(raw: Float, peakScale: Float): Float {
        val growSpan = 0.5f
        val landStart = 0.86f
        return when {
            raw <= growSpan -> 1f + (peakScale - 1f) * (raw / growSpan)
            raw <= landStart -> {
                val s = (raw - growSpan) / (landStart - growSpan)
                peakScale + (1f - peakScale) * s
            }
            else -> {
                val local = (raw - landStart) / (1f - landStart)
                1f + 0.05f * sin(PI * local).toFloat()
            }
        }
    }

    // 网格几何：分界缝只在开启时计入；节次 top / 课程视觉高度用于跨午休/晚修落点
    fun dividerPxFor(geom: ScheduleGridGeometry): Float =
        if (geom.showBreakDividers) with(density) { 24.dp.toPx() } else 0f

    fun sectionTopPx(geom: ScheduleGridGeometry, section: Int, dividerPx: Float): Float {
        // 优先用网格布局的**实际**节次顶（来自 computeSpecialGridLayout，含特殊块挤占）。
        // 下面的「节次高 × 序号 + 分界缝」现算公式漏掉特殊块高度，
        // 会让长按浮层与吸附落点整体偏上，偏移量正好等于该节次上方的特殊块总高
        //（特殊块高约半个卡片时就表现为「长按浮层偏上 50%」）。
        // 空节次长按路径一直用的是 grid.sectionTop，所以它没这个问题。
        geom.sectionTopDp[section]?.let { dp -> return with(density) { dp.dp.toPx() } }
        val sectionH = geom.sectionHeightPx
        if (sectionH <= 0f) return 0f
        val morning = geom.morningSections
        val afternoon = geom.afternoonSections
        return when {
            section <= morning -> (section - 1) * sectionH
            section <= morning + afternoon ->
                morning * sectionH + dividerPx + (section - morning - 1) * sectionH
            else ->
                morning * sectionH + dividerPx + afternoon * sectionH + dividerPx +
                    (section - morning - afternoon - 1) * sectionH
        }
    }

    fun courseVisualHeightPx(
        geom: ScheduleGridGeometry,
        startSection: Int,
        endSection: Int,
        dividerPx: Float
    ): Float {
        val sectionH = geom.sectionHeightPx
        if (sectionH <= 0f) return 0f
        val top = sectionTopPx(geom, startSection, dividerPx)
        val bottom = sectionTopPx(geom, endSection.coerceAtLeast(startSection), dividerPx) + sectionH
        return (bottom - top).coerceAtLeast(sectionH)
    }

    // 目标落点卡片中心绝对坐标（root px），供吸附动画
    fun computeTargetCenter(dayOfWeek: Int, startSection: Int, sectionSpan: Int): Offset? {
        val geom = gridGeometry ?: return null
        val bounds = geom.dayBounds[dayOfWeek] ?: return null
        if (bounds.size < 3) return null
        val centerX = (bounds[0] + bounds[1]) / 2f
        val topY = bounds[2]
        val dividerPx = dividerPxFor(geom)
        val startTop = sectionTopPx(geom, startSection, dividerPx)
        val endSection = startSection + sectionSpan
        val visualH = courseVisualHeightPx(geom, startSection, endSection, dividerPx)
        val cardCenterY = topY + startTop + visualH / 2f
        return Offset(centerX, cardCenterY)
    }

    /**
     * 列星期 → 数据（星期, 周次）。
     * 调课日列渲染的是 followWeekday/followWeek 的课，落点写库、冲突检测都要走这个映射；
     * 几何（吸附动画、落点高亮）仍然用列星期。映射缺失时退回 列星期 + 正在浏览的周。
     */
    fun dataPositionForColumn(columnDay: Int): Pair<Int, Int> =
        gridGeometry?.columnDataPosition?.get(columnDay) ?: (columnDay to currentViewingWeek)

    /** 数据（星期, 周次）→ 列星期：几何反查（粘贴飞行起点、交换飞行原点），找不到退回数据星期 */
    fun columnForDataPosition(day: Int, week: Int): Int =
        gridGeometry?.columnDataPosition?.entries
            ?.firstOrNull { it.value.first == day && it.value.second == week }?.key ?: day

    // spring 带回弹吸进目标格，中段开涟漪
    val snapFloatingCardToTarget: (dayOfWeek: Int, startSection: Int, sectionSpan: Int) -> Unit =
        { day, section, span ->
            val targetCenter = computeTargetCenter(day, section, span)
            if (targetCenter != null) {
                val targetOffsetX = targetCenter.x - draggedCardPosition.x
                val targetOffsetY = targetCenter.y - draggedCardPosition.y
                coroutineScope.launch {
                    isSnapping = true
                    // 拖拽期已在 floatingOffset* 上 snapTo，无需再从 draggedCardOffset 拷贝
                    launch {
                        delay(120.milliseconds)
                        triggerLandRipple(targetCenter)
                    }
                    val snapSpec = spring<Float>(
                        dampingRatio = 0.58f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                    val scaleSpec = spring<Float>(
                        dampingRatio = 0.7f,
                        stiffness = Spring.StiffnessMedium
                    )
                    val jobX = launch {
                        animateFloatState(floatingOffsetX, targetOffsetX, snapSpec, dragMotion.velocityX)
                    }
                    val jobY = launch {
                        animateFloatState(floatingOffsetY, targetOffsetY, snapSpec, dragMotion.velocityY)
                    }
                    val jobScale = launch {
                        floatingScale.animateTo(1f, scaleSpec)
                    }
                    jobX.join(); jobY.join(); jobScale.join()
                    isDraggingCard = false
                    floatingCardVisible = false
                    draggingCourseIds = emptySet()
                    draggedCardCourse = null
                    dragMotion.reset()
                    pendingDropTarget = null
                    isSnapping = false
                    floatingScale.snapTo(0.94f)
                    floatingOffsetX.floatValue = 0f
                    floatingOffsetY.floatValue = 0f
                }
            } else {
                dismissFloatingCard()
            }
        }

    // 粘贴飞行：复用长按浮层直线飞向目标格；无法定位时直接回调
    val playPasteFlightAnimation: (
        source: Course,
        targetDay: Int,
        targetSection: Int,
        onFinished: () -> Unit
    ) -> Unit = { source, targetDay, targetSection, onFinished ->
        val span = (source.endSection - source.startSection).coerceAtLeast(0)
        // 起点按**列**取几何：源课若是从调课日列复制来的，数据星期对不上列
        val sourceColumn = columnForDataPosition(source.dayOfWeek, currentViewingWeek)
        val sourceCenter = computeTargetCenter(sourceColumn, source.startSection, span)
        val targetCenter = computeTargetCenter(targetDay, targetSection, span)
        val sourceBounds = gridGeometry?.dayBounds?.get(sourceColumn)
        val sectionH = gridGeometry?.sectionHeightPx
        if (sourceCenter != null && targetCenter != null && sourceBounds != null && sectionH != null && sectionH > 0f) {
            coroutineScope.launch {
                delay(120.milliseconds) // 等弹窗退场
                // 原卡片保持可见（复制语义）
                draggedCardCourse = source
                draggedWeek = currentViewingWeek
                draggedCardPosition = sourceCenter
                dragMotion.reset()
                // 与 CourseCard 默认 2dp padding 对齐
                val cardPadPx = with(density) { 2.dp.toPx() }
                draggedCardSize = Offset(
                    (sourceBounds[1] - sourceBounds[0] - cardPadPx * 2f).coerceAtLeast(1f),
                    (span + 1) * sectionH
                )
                isPasteFlight = true
                isSnapping = true
                floatingCardVisible = true
                floatingOffsetX.floatValue = 0f
                floatingOffsetY.floatValue = 0f
                floatingScale.snapTo(1f)

                val dx = targetCenter.x - sourceCenter.x
                val dy = targetCenter.y - sourceCenter.y
                val peakScale = 1.4f
                val durationNanos = 480_000_000L
                val moveEase = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)
                var rippleFired = false
                val startNanos = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    val raw = ((now - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                    val t = moveEase.transform(raw)
                    floatingOffsetX.floatValue = dx * t
                    floatingOffsetY.floatValue = dy * t
                    floatingScale.snapTo(pasteFlightScaleAt(raw, peakScale))
                    // 快落地时先开涟漪
                    if (!rippleFired && raw >= 0.8f) {
                        rippleFired = true
                        triggerLandRipple(targetCenter)
                    }
                    if (raw >= 1f) break
                }

                // 落地后立刻换成真实课程，避免双影
                floatingCardVisible = false
                isSnapping = false
                isPasteFlight = false
                draggedCardCourse = null
                floatingOffsetX.floatValue = 0f
                floatingOffsetY.floatValue = 0f
                floatingScale.snapTo(0.94f)
                onFinished()
            }
        } else {
            onFinished()
        }
    }

    // 原地长按松手，spring 吸回原位
    val snapFloatingCardToOrigin: () -> Unit = {
        // 吸回原位也算「落地」：锚点即长按瞬间的原格中心，落地节奏与吸到新格一致
        val originCenter = draggedCardPosition
        coroutineScope.launch {
            isSnapping = true
            launch {
                delay(120.milliseconds)
                triggerLandRipple(originCenter)
            }
            val snapSpec = spring<Float>(
                dampingRatio = 0.58f,
                stiffness = Spring.StiffnessMediumLow
            )
            val scaleSpec = spring<Float>(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMedium
            )
            val jobX = launch {
                animateFloatState(floatingOffsetX, 0f, snapSpec, dragMotion.velocityX)
            }
            val jobY = launch {
                animateFloatState(floatingOffsetY, 0f, snapSpec, dragMotion.velocityY)
            }
            val jobScale = launch { floatingScale.animateTo(1f, scaleSpec) }
            jobX.join(); jobY.join(); jobScale.join()
            isDraggingCard = false
            floatingCardVisible = false
            draggingCourseIds = emptySet()
            draggedCardCourse = null
            dragMotion.reset()
            pendingDropTarget = null
            isSnapping = false
            floatingScale.snapTo(0.94f)
            floatingOffsetX.floatValue = 0f
            floatingOffsetY.floatValue = 0f
        }
    }

    // 粘贴节奏飞行：从悬停 offset 飞到 destOffset 后收起
    fun flyFloatingCardWithPasteMotion(destOffsetX: Float, destOffsetY: Float) {
        stopConflictHover()
        coroutineScope.launch {
            isSnapping = true
            isPasteFlight = true
            val startX = floatingOffsetX.floatValue
            val startY = floatingOffsetY.floatValue
            val peakScale = 1.4f
            val durationNanos = 480_000_000L
            val moveEase = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)
            floatingScale.snapTo(1f)
            var rippleFired = false
            val startNanos = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val raw = ((now - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                val t = moveEase.transform(raw)
                floatingOffsetX.floatValue = startX + (destOffsetX - startX) * t
                floatingOffsetY.floatValue = startY + (destOffsetY - startY) * t
                floatingScale.snapTo(pasteFlightScaleAt(raw, peakScale))
                if (!rippleFired && raw >= 0.8f) {
                    rippleFired = true
                    triggerLandRipple(
                        Offset(
                            draggedCardPosition.x + destOffsetX,
                            draggedCardPosition.y + destOffsetY
                        )
                    )
                }
                if (raw >= 1f) break
            }
            isDraggingCard = false
            floatingCardVisible = false
            draggingCourseIds = emptySet()
            draggedCardCourse = null
            pendingDropTarget = null
            isSnapping = false
            isPasteFlight = false
            floatingOffsetX.floatValue = 0f
            floatingOffsetY.floatValue = 0f
            floatingScale.snapTo(0.94f)
            clearSwapFlight()
        }
    }

    // 冲突取消：粘贴节奏飞回原位
    val flyFloatingCardHome: () -> Unit = {
        flyFloatingCardWithPasteMotion(0f, 0f)
    }

    // 覆盖确认：粘贴节奏飞到目标格
    val flyFloatingCardToDropTarget: () -> Unit = {
        val source = draggedCardCourse
        val target = pendingDropTarget
        if (source != null && target != null) {
            val span = source.endSection - source.startSection
            val targetCenter = computeTargetCenter(target.first, target.second, span)
            if (targetCenter != null) {
                flyFloatingCardWithPasteMotion(
                    targetCenter.x - draggedCardPosition.x,
                    targetCenter.y - draggedCardPosition.y
                )
            } else {
                dismissFloatingCard()
            }
        } else {
            dismissFloatingCard()
        }
    }

    // 交换双浮层交叉飞行（480ms / 1→1.4→1.0）
    val flySwapCards: (conflictCourse: Course) -> Unit = { conflictCourse ->
        stopConflictHover()
        val source = draggedCardCourse
        val dropTarget = pendingDropTarget
        if (source == null || dropTarget == null) {
            clearSwapFlight()
            dismissFloatingCard()
        } else {
            val sourceSpan = source.endSection - source.startSection
            val targetCenter = computeTargetCenter(dropTarget.first, dropTarget.second, sourceSpan)
            val occupiedSpan = conflictCourse.endSection - conflictCourse.startSection
            // 冲突课所在**列**：调课日列的数据星期 ≠ 列星期，几何必须按列取
            val conflictColumn = columnForDataPosition(
                conflictCourse.dayOfWeek,
                dataPositionForColumn(dropTarget.first).second
            )
            val occupiedCenter = computeTargetCenter(
                conflictColumn,
                conflictCourse.startSection,
                occupiedSpan
            )
            if (targetCenter == null || occupiedCenter == null) {
                clearSwapFlight()
                dismissFloatingCard()
            } else {
                coroutineScope.launch {
                    val bounds = gridGeometry?.dayBounds?.get(conflictColumn)
                    val cardPadPx = with(density) { 2.dp.toPx() }
                    swapFlightCourse = conflictCourse
                    swapFlightOriginCenter = occupiedCenter
                    swapFlightWidth = if (bounds != null && bounds.size >= 2) {
                        (bounds[1] - bounds[0] - cardPadPx * 2f).coerceAtLeast(1f)
                    } else {
                        draggedCardSize.x
                    }
                    swapFlightOffsetX.snapTo(0f)
                    swapFlightOffsetY.snapTo(0f)
                    swapFlightScale.snapTo(1f)
                    swapFlightVisible = true

                    isSnapping = true
                    isPasteFlight = true
                    floatingScale.snapTo(1f)
                    val srcStartX = floatingOffsetX.floatValue
                    val srcStartY = floatingOffsetY.floatValue
                    val srcEndX = targetCenter.x - draggedCardPosition.x
                    val srcEndY = targetCenter.y - draggedCardPosition.y
                    val tgtEndX = draggedCardPosition.x - occupiedCenter.x
                    val tgtEndY = draggedCardPosition.y - occupiedCenter.y

                    val peakScale = 1.4f
                    val durationNanos = 480_000_000L
                    val moveEase = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)
                    var rippleFired = false
                    val startNanos = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val raw = ((now - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                        val t = moveEase.transform(raw)
                        floatingOffsetX.floatValue = srcStartX + (srcEndX - srcStartX) * t
                        floatingOffsetY.floatValue = srcStartY + (srcEndY - srcStartY) * t
                        swapFlightOffsetX.snapTo(tgtEndX * t)
                        swapFlightOffsetY.snapTo(tgtEndY * t)
                        val scale = pasteFlightScaleAt(raw, peakScale)
                        floatingScale.snapTo(scale)
                        swapFlightScale.snapTo(scale)
                        if (!rippleFired && raw >= 0.8f) {
                            rippleFired = true
                            triggerLandRipple(targetCenter)
                        }
                        if (raw >= 1f) break
                    }

                    clearSwapFlight()
                    isDraggingCard = false
                    floatingCardVisible = false
                    draggingCourseIds = emptySet()
                    draggedCardCourse = null
                    pendingDropTarget = null
                    isSnapping = false
                    isPasteFlight = false
                    floatingOffsetX.floatValue = 0f
                    floatingOffsetY.floatValue = 0f
                    floatingScale.snapTo(0.94f)
                    swapFlightOffsetX.snapTo(0f)
                    swapFlightOffsetY.snapTo(0f)
                    swapFlightScale.snapTo(1f)
                }
            }
        }
    }

    // 由浮层中心点对齐网格，得出落点 (dayOfWeek, startSection)
    fun computeDropTarget(centerX: Float, firstSectionCenterY: Float): Pair<Int, Int>? {
        val geom = gridGeometry ?: return null
        if (geom.sectionHeightPx <= 0f) return null
        if (geom.dayBounds.isEmpty()) return null
        val day = geom.dayBounds.entries.firstOrNull { (columnDay, bounds) ->
            // 教学周重组的休课日列永远渲染为空，落进去的课在本页与今日页都不可见，直接拦掉
            columnDay !in geom.blockedColumns &&
                bounds.size >= 2 && centerX >= bounds[0] && centerX <= bounds[1]
        }?.key ?: return null
        val topY = geom.dayBounds[day]?.getOrNull(2) ?: return null
        val relY = firstSectionCenterY - topY
        if (relY < 0f) return null
        val sectionH = geom.sectionHeightPx

        // 优先用网格**权威**节次顶（sectionTopDp 含分界缝与特殊块挤占），
        // 与卡片实际排版、落点高亮（MainScheduleScreen 的 boxOf 用 grid.sectionTop）保持一致。
        // 下面那套手写遍历漏掉特殊块，会让「实际落到的格子」与「高亮显示的格子」错开。
        val totalSections = geom.morningSections + geom.afternoonSections + geom.eveningSections
        if (geom.sectionTopDp.isNotEmpty() && totalSections > 0) {
            for (s in 1..totalSections) {
                val top = geom.sectionTopDp[s]?.let { with(density) { it.dp.toPx() } } ?: continue
                if (relY < top + sectionH) return day to s
            }
            // 落在最后一节之下：吸到最后一节
            return day to totalSections
        }

        val dividerH = with(density) { 24.dp.toPx() }
        var cursor = 0f
        for (s in 1..geom.morningSections) {
            if (relY < cursor + sectionH) return day to s
            cursor += sectionH
        }
        if (geom.showBreakDividers) cursor += dividerH
        val afternoonStart = geom.morningSections + 1
        for (i in 1..geom.afternoonSections) {
            val s = afternoonStart + i - 1
            if (relY < cursor + sectionH) return day to s
            cursor += sectionH
        }
        if (geom.showBreakDividers) cursor += dividerH
        val eveningStart = afternoonStart + geom.afternoonSections
        for (i in 1..geom.eveningSections) {
            val s = eveningStart + i - 1
            if (relY < cursor + sectionH) return day to s
            cursor += sectionH
        }
        return null
    }

    fun openCourseDetail(
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        fromToday: Boolean,
        courseIdToHide: String = courses.firstOrNull()?.id ?: "",
        targetWeek: Int = 0
    ) {
        detailCourses = courses
        detailCardLeft = cardLeft
        detailCardTop = cardTop
        detailCardWidth = cardWidth
        detailCardHeight = cardHeight
        detailFromToday = fromToday
        detailTargetWeek = targetWeek
        coroutineScope.launch {
            // 隐藏课程前先截全屏，保证快照完整
            val fullSnapshot = captureMainContentBitmap()
            recycleIndependentBitmap(
                mainContentSnapshot,
                fullSnapshot,
                MainActivity.cachedWallpaperBitmap,
            )
            mainContentSnapshot = fullSnapshot
            hiddenCourseIds = setOf(courseIdToHide)
            val oldDetail = detailSnapshot
            detailSnapshot = fullSnapshot?.let { snap ->
                try {
                    val x = cardLeft.toInt().coerceIn(0, snap.width - 1)
                    val y = cardTop.toInt().coerceIn(0, snap.height - 1)
                    val w = cardWidth.toInt().coerceIn(1, snap.width - x)
                    val h = cardHeight.toInt().coerceIn(1, snap.height - y)
                    android.graphics.Bitmap.createBitmap(snap, x, y, w, h)
                } catch (_: Exception) {
                    null
                } catch (_: OutOfMemoryError) {
                    null
                }
            }
            recycleIndependentBitmap(oldDetail, detailSnapshot, fullSnapshot)

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

    // 顶栏「课表外观」与长按按钮共用
    val enterCustomizePage: () -> Unit = {
        com.haooz.chedule.ui.utils.CrashLogHelper.trace("课表外观", "enter")
        coroutineScope.launch {
            val screenW = windowInfo.containerSize.width.toFloat()
            customizeExitTargetScale = (screenW * 0.65f) / screenW
            val oldCombSnapshots = combinations.mapNotNull { it.snapshot }
            combinations = combinations.map { it.copy(snapshot = null) }
            delay(50.milliseconds)
            // 复制为独立 ARGB_8888 切断引用
            // 截不到（OOM）就不做开洞快照，直接照常进搭配页
            val captured = captureMainContentBitmap()
            val currentSnapshot = captured?.let { snap ->
                try {
                    withContext(Dispatchers.IO) {
                        snap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: snap
                    }
                } catch (_: OutOfMemoryError) {
                    null
                }
            }
            val oldCustomize = customizeSnapshot
            val oldCover = snapshotCoverBitmap
            customizeSnapshot = currentSnapshot
            // 先丢弃引用，下一帧再 recycle，避免仍在组合树中的 Image 读到已释放位图
            if (combinations.isNotEmpty()) {
                combinations = combinations.toMutableList().also {
                    it[0] = it[0].copy(snapshot = currentSnapshot)
                }
            }
            launch {
                delay(32.milliseconds)
                recycleIndependentBitmap(oldCustomize, currentSnapshot, captured)
                recycleIndependentBitmap(oldCover, currentSnapshot, captured)
                oldCombSnapshots.forEach { snap ->
                    recycleIndependentBitmap(
                        snap,
                        currentSnapshot,
                        captured,
                        MainActivity.cachedWallpaperBitmap,
                    )
                }
            }
            customizeExitScale.snapTo(1f)
            customizeExitAlpha.snapTo(1f)
            showCustomizePage = true
            isWindowCutoutActive = true
            pendingEnterCutout = true
            // 快照盖住开洞过渡，与主内容缩放同步后淡出
            customizeCoverActive = true
            customizeCoverScale.snapTo(1f)
            customizeCoverAlpha.snapTo(1f)
            // 丢弃未落盘的主题预览
            pendingScheduleThemeMode = null
            delay(280.milliseconds)
            launch {
                customizeCoverScale.animateTo(
                    0.75f,
                    tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
            }
            launch {
                delay(400.milliseconds)
                customizeCoverAlpha.animateTo(
                    0f,
                    tween(120, easing = FastOutSlowInEasing)
                )
                customizeCoverActive = false
            }
            originalCombinationIndex = 0
            originalWallpaperBitmap = wallpaperBitmap
            originalWallpaperOffset = wallpaperOffset
            originalWallpaperScale = wallpaperScale
            originalSnapshot = combinations.getOrNull(0)?.snapshot
            originalWallpaperIsLight = combinations.getOrNull(0)?.wallpaperIsLight
            originalCombination = combinations.getOrNull(0)
        }
    }

    var wallpaperDecodeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            android.widget.Toast.makeText(context, "未选择图片", android.widget.Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (wallpaperDecodeJob?.isActive == true) {
            android.widget.Toast.makeText(context, "正在处理壁纸，请稍候", android.widget.Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val decodeW = screenWPx
        val decodeH = screenHPx
        android.widget.Toast.makeText(context, "正在处理壁纸…", android.widget.Toast.LENGTH_SHORT).show()
        wallpaperDecodeJob = coroutineScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeWallpaperSampled(context, uri, decodeW, decodeH)
            }
            if (bitmap == null) {
                android.widget.Toast.makeText(context, "壁纸解码失败，请换一张图片", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val oldWallpaper = wallpaperBitmap
            wallpaperBitmap = bitmap
            MainActivity.cachedWallpaperBitmap = bitmap
            wallpaperOffset = Offset.Zero
            val autoScale = computeWallpaperMinScale(bitmap, decodeW, decodeH)
            wallpaperScale = autoScale
            val isLight = computeWallpaperIsLight(bitmap)
            val idx = currentCombinationIndex
            if (idx in combinations.indices) {
                combinations = combinations.toMutableList().also { list ->
                    list[idx] = list[idx].copy(
                        bitmap = bitmap,
                        // 清掉旧快照，搭配预览回退显示新壁纸，避免「选完没反应」
                        snapshot = null,
                        offset = Offset.Zero,
                        scale = autoScale,
                        wallpaperIsLight = isLight
                    )
                }
            }
            // 旧壁纸已离开当前 combination/显示引用后再回收（避开缓存与仍被展示的同一对象）
            launch {
                delay(32.milliseconds)
                recycleIndependentBitmap(
                    oldWallpaper,
                    bitmap,
                    MainActivity.cachedWallpaperBitmap,
                    wallpaperBitmap,
                    savedWallpaperBitmap,
                    originalWallpaperBitmap,
                    *combinations.mapNotNull { c -> c.bitmap }.toTypedArray(),
                )
            }
        }
    }

    var showSwitchSchedule by remember { mutableStateOf(false) }
    var switchPendingReverse by remember { mutableStateOf(false) }
    // 截图期间先隐藏切换页
    var switchCapturingSnapshot by remember { mutableStateOf(false) }
    // 改过课表则退出前重截主内容
    var scheduleChanged by remember { mutableStateOf(false) }
    var showMorePopup by remember { mutableStateOf(false) }
    var showTodayMorePopup by remember { mutableStateOf(false) }
    // 按钮原地随菜单展开淡出，形成"按钮连贯变为菜单"的单一动画
    val morePopupFraction = remember { Animatable(0f) }
    val todayMorePopupFraction = remember { Animatable(0f) }
    // 「更多」占位槽实测顶部位置（px，root 坐标）：布局期上报，菜单据此定位，避免首帧 inset 未到而偏位
    val moreSlotTopPx = remember { mutableFloatStateOf(0f) }
    val canSampleMoreSlot = !isWindowCutoutActive && !showCustomizePage && !showSwitchSchedule
    // 顶栏滚动材质透明度（收起态「更多」玻璃渐显渐隐）；两个顶栏各自一份，避免切页时互相覆盖
    // 初值 0 而非 1f：真实材质值由 SideEffect 在首帧组合后写入，
    // 用 1f 会让「更多」先亮一帧材质再变透明。收起态 f=0 时
    // materialVisible = max(materialAlpha, 0) = materialAlpha，故 0 安全。
    val scheduleMaterialAlpha = remember { mutableStateOf(0f) }
    val todayMaterialAlpha = remember { mutableFloatStateOf(0f) }
    var todayJumpToDateTrigger by remember { mutableIntStateOf(0) }

    val isViewingCurrentWeek = currentViewingWeek == currentWeek

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (showCustomizePage) Color(0xFF1A1A1A) else MiuixTheme.colorScheme.surface)
            .drawWithContent {
                drawContent()

            }) {
        val displayAppearance =
            // 开洞前显示已应用外观，避免快照过渡期闪编辑值
            if (showCustomizePage && !isWindowCutoutActive) {
                originalCombination?.let { com.haooz.chedule.data.AppearanceConfig.fromCombination(it) }
                    ?: currentAppearance()
            } else {
                currentAppearance()
            }
        // 顶栏/底栏必须采主内容 liquidGlass，否则玻璃退化成纯色
        val chromeBackdrop: com.kyant.backdrop.Backdrop = liquidGlassBackdrop
        val isEntryAnimating = showSwitchSchedule && switchAnimForward && switchAnimRunning
        val mainContentAlpha = when {
            showSwitchSchedule && switchOverlayActive -> 0f
            else -> 1f
        }
        val mainContentBlurDp =
            if (shortcutMenuBlurRadius.value > 0.01f) shortcutMenuBlurRadius.value.dp
            else managePageBlurRadius.value.dp
        val mainContentBlurModifier =
            if (mainContentBlurDp.value > 0f) Modifier.blur(mainContentBlurDp) else Modifier

        // liquidGlass 录制跳帧指纹：结构量变了才 update→markNeedsRecord。
        // 必须 remember 出稳定 List，否则 equals 失败会强制录制。
        // 用 pager 当前页而非 selectedTab：点击后 selectedTab 先变，真正像素落定在 currentPage。
        // pagerState.currentPage：无壁纸周滑跳过 mustRecord 后，靠落页变 key 补录。
        val liquidGlassRecordKey = remember(
            mainPagerState.currentPage, isShiftMode, showDetail, showCustomizePage, showSwitchSchedule,
            isWindowCutoutActive, shortcutMenuVisible, isDraggingCard, floatingCardVisible,
            dataVersion, currentWeek, totalWeeks, currentCombinationIndex, effectiveIsDark,
            wallpaperBitmap, com.haooz.chedule.ui.components.TabletNavSideState.expanded, scheduleShowCourseDetail.value,
            pagerState.currentPage
        ) {
            listOf(
                mainPagerState.currentPage, isShiftMode, showDetail, showCustomizePage, showSwitchSchedule,
                isWindowCutoutActive, shortcutMenuVisible, isDraggingCard, floatingCardVisible,
                dataVersion, currentWeek, totalWeeks, currentCombinationIndex, effectiveIsDark,
                wallpaperBitmap, com.haooz.chedule.ui.components.TabletNavSideState.expanded, scheduleShowCourseDetail.value,
                pagerState.currentPage
            )
        }
        // 局部 val 不能直接捕获进 remember lambda，一律经 rememberUpdatedState
        val latestCutoutScale by rememberUpdatedState(cutoutMainScale.value)
        val latestBackgroundScale by rememberUpdatedState(backgroundScale.value)
        val latestSheetOffset by rememberUpdatedState(sheetOffsetY.value)
        val latestShortcutBlur by rememberUpdatedState(shortcutMenuBlurRadius.value)
        val latestManageBlur by rememberUpdatedState(managePageBlurRadius.value)
        val latestSwitchProgress by rememberUpdatedState(switchAnimProgress.value)
        val latestCustomizeExitScale by rememberUpdatedState(customizeExitScale.value)
        val latestCustomizeCoverScale by rememberUpdatedState(customizeCoverScale.value)
        val latestCustomizeCoverAlpha by rememberUpdatedState(customizeCoverAlpha.value)
        val latestIsWindowCutout by rememberUpdatedState(isWindowCutoutActive)
        val latestShowCustomize by rememberUpdatedState(showCustomizePage)
        val latestIsCustomizeExiting by rememberUpdatedState(isCustomizeExiting)
        val latestDraggingCard by rememberUpdatedState(isDraggingCard)
        val latestRailBlurEpoch by rememberUpdatedState(railBlurResampleEpoch.intValue)
        // 无壁纸周左右滑：跳过 liquidGlass 整树逐帧重录（chrome 采样缓存随之跳过模糊）。
        // 落页靠 recordKey 里的 currentPage 补一帧；有壁纸时玻璃要跟像素，仍每帧重录。
        val latestHasWallpaperForGlass by rememberUpdatedState(wallpaperBitmap != null)
        // 课表/今日/设置滚动时主内容像素在变，必须重录，否则顶栏/底栏玻璃冻结
        val liquidGlassMustRecord = remember(scheduleScrollState, todayListScrollInProgress, pagerState, todayPagerState, mainPagerState) {
            var lastRailExpand = Float.NaN
            var lastBlurEpoch = -1
            {
                // 侧栏进度只在 draw 阶段读，不进组合
                val expand = com.haooz.chedule.ui.components.TabletNavSideState.expandProgress.floatValue
                val expandMoving = expand != lastRailExpand
                lastRailExpand = expand
                val epochBumped = latestRailBlurEpoch != lastBlurEpoch
                lastBlurEpoch = latestRailBlurEpoch
                val forceRail = railForceRecordFrames[0] > 0
                if (forceRail) railForceRecordFrames[0] = railForceRecordFrames[0] - 1
                scheduleScrollState.isScrollInProgress ||
                    todayListScrollInProgress.value ||
                    (pagerState.isScrollInProgress && latestHasWallpaperForGlass) ||
                    todayPagerState.isScrollInProgress ||
                    mainPagerState.isScrollInProgress ||
                    expandMoving ||
                    forceRail ||
                    epochBumped ||
                    // 开洞编辑时主内容持续缩放，绝不能停录
                    latestIsWindowCutout ||
                    (latestShowCustomize && latestIsCustomizeExiting) ||
                    // 切换课表进/退场动画期间刻意「不」录：这一段里主内容 alpha=0
                    latestShortcutBlur > 0.01f ||
                    latestManageBlur > 0.01f ||
                    // 非静止端点视为动画进行中
                    (latestCutoutScale != 1f && latestCutoutScale != 0.75f) ||
                    latestBackgroundScale != 1f ||
                    latestSheetOffset != 0f ||
                    (latestSwitchProgress != 0f && latestSwitchProgress != 1f) ||
                    latestCustomizeExitScale != 1f ||
                    latestCustomizeCoverScale != 1f ||
                    latestCustomizeCoverAlpha != 1f ||
                    // 拖拽课程卡片时像素每帧变
                    latestDraggingCard
            }
        }
        // 主内容（带缩放和裁切）
        // 空闲态不挂 graphicsLayer：全屏离屏合成是周滑 P50 主头之一。
        // 仅缩放/透明度/位移真正非单位时才开层；derivedStateOf 避免 Animatable 每帧重组。
        val mainLayerNeeded by remember {
            derivedStateOf {
                isCustomizeExiting ||
                    isWindowCutoutActive ||
                    showSwitchSchedule ||
                    showCustomizePage ||
                    mainContentAlpha != 1f ||
                    backgroundScale.value != 1f ||
                    cutoutMainScale.value != 1f ||
                    customizeExitScale.value != 1f ||
                    sheetOffsetY.value != 0f
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 半径为 0 时不必挂 blur：Modifier.blur 会为整棵主内容树额外建一个
                // RenderEffect 图层，0 半径也照常走一遍离屏合成。
                .then(mainContentBlurModifier)
                .then(
                    if (mainLayerNeeded) {
                        Modifier.graphicsLayer {
                            val baseScale =
                                if (!showSwitchSchedule) backgroundScale.value
                                else if (isEntryAnimating) 1f
                                else 1f
                            val exitScale = if (isCustomizeExiting) customizeExitScale.value else 1f
                            val cutoutScale = cutoutMainScale.value
                            // 开洞时由 cutoutScale 控制；进出场统一由快照覆盖层处理，避免叠加闪烁
                            val effectiveScale = if (isCustomizeExiting && isWindowCutoutActive) {
                                cutoutScale
                            } else {
                                exitScale * cutoutScale
                            }
                            scaleX = baseScale * effectiveScale
                            scaleY = baseScale * effectiveScale
                            alpha = mainContentAlpha
                            // 与 CustomizeScheduleScreen 共享同一 Animatable，像素级同帧
                            val sheetScale = cutoutMainScale.value
                            val sheetScaleProg = ((sheetScale - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
                            translationY = sheetOffsetY.value * (1f - sheetScaleProg)
                            if (isCustomizeExiting) {
                                transformOrigin = TransformOrigin(0.5f, 0.58f)
                            }
                            if (isWindowCutoutActive) {
                                // 使用 CustomizeScheduleScreen 传回的裁剪中心比例，保证两者完全对齐
                                transformOrigin = TransformOrigin(0.5f, cutoutCenterYRatio)
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    // 每帧按当前缩放重裁；搭配页退出时锁定 screenCornerRadius
                    Modifier.drawWithContent {
                        val scale = backgroundScale.value
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
                        // 仅在被请求时录制一帧，避免每帧重绘整棵主内容树
                        if (mainSnapshotRequester.lastRecordedToken != mainSnapshotToken) {
                            mainSnapshotRequester.lastRecordedToken = mainSnapshotToken
                            screenGraphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                        }
                        drawContent()
                    }
                )
        ) {
            val scaffoldContent = @Composable {
                val onMainTabSelected: (Int) -> Unit = { idx ->
                    if (idx != selectedTab) {
                        // 只翻高亮目标；selectedTab 等落页后由 settledPage 同步，
                        // 避免点击瞬间整树重组抢在动画第一帧
                        mainTabProgrammatic = true
                        selectedTab = idx
                        coroutineScope.launch {
                            try {
                                // pad：只取消主 pager 未完成滚动，尽快落页，保证点击跟手
                                if (navBarStyle == "rail") {
                                    if (mainPagerState.isScrollInProgress) mainPagerState.cancelScroll()
                                    mainPagerState.scrollToPage(idx)
                                } else {
                                    if (todayPagerState.isScrollInProgress) todayPagerState.cancelScroll()
                                    if (pagerState.isScrollInProgress) pagerState.cancelScroll()
                                    if (mainPagerState.isScrollInProgress) mainPagerState.cancelScroll()
                                    mainPagerState.animateMainTabTo(idx)
                                }
                                com.haooz.chedule.ui.utils.CrashLogHelper.trace(
                                    "主页", "tab", "idx=$idx shift=$isShiftMode rail=${navBarStyle == "rail"}"
                                )
                            } finally {
                                mainTabProgrammatic = false
                            }
                        }
                    }
                }
                Scaffold(
                    bottomBar = {
                        // 平板导航在叠层；这里不占 Scaffold bottomBar，避免全屏挡住内容滚动
                        if (navBarStyle != "rail") {
                            ScheduleBottomBar(
                                navBarStyle = navBarStyle,
                                isShiftMode = isShiftMode,
                                selectedTab = selectedTab,
                                onTabSelected = { idx ->
                                    if (idx != selectedTab) {
                                        // 先锁 programmatic，只改高亮目标，避免动画中途被拉回
                                        mainTabProgrammatic = true
                                        selectedTab = idx
                                        com.haooz.chedule.ui.utils.CrashLogHelper.trace(
                                            "主页", "tab", "idx=$idx shift=$isShiftMode"
                                        )
                                        coroutineScope.launch {
                                            try {
                                                if (todayPagerState.isScrollInProgress) {
                                                    todayPagerState.cancelScroll()
                                                }
                                                if (pagerState.isScrollInProgress) {
                                                    pagerState.cancelScroll()
                                                }
                                                if (mainPagerState.isScrollInProgress) {
                                                    mainPagerState.cancelScroll()
                                                }
                                                mainPagerState.animateMainTabTo(idx)
                                            } finally {
                                                mainTabProgrammatic = false
                                            }
                                        }
                                    }
                                },
                                liquidGlassBackdrop = chromeBackdrop,
                            )
                        }
                    },
                    topBar = {
                        // 标题可见性只在页码/方向变化时重组；平移读 pager 写在 graphicsLayer，
                        // 避免动画每帧重组顶栏及 ScheduleTopBar 整棵树。
                        val showTodayTitle by remember(isShiftMode, mainPagerState) {
                            derivedStateOf {
                                if (isShiftMode) {
                                    false
                                } else {
                                    val page = mainPagerState.currentPage
                                    val off = mainPagerState.currentPageOffsetFraction
                                    page == 0 || (page == 1 && off < 0f)
                                }
                            }
                        }
                        val showScheduleTitle by remember(isShiftMode, mainPagerState) {
                            derivedStateOf {
                                val page = mainPagerState.currentPage
                                val off = mainPagerState.currentPageOffsetFraction
                                if (isShiftMode) {
                                    page == 0 || (page == 1 && off < 0f)
                                } else {
                                    page == 1 ||
                                        (page == 0 && off > 0f) ||
                                        (page == 2 && off < 0f)
                                }
                            }
                        }
                        val showSettingsTitle by remember(isShiftMode, mainPagerState) {
                            derivedStateOf {
                                val page = mainPagerState.currentPage
                                val off = mainPagerState.currentPageOffsetFraction
                                if (isShiftMode) {
                                    page == 1 || (page == 0 && off > 0f)
                                } else {
                                    page == 2 || (page == 1 && off > 0f)
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (showScheduleTitle) {
                                val scheduleTitleIndex = if (isShiftMode) 0 else 1
                                // 空闲停在本页时 delta=0：不挂 graphicsLayer，免掉顶栏整棵离屏合成。
                                // 切 tab 过程中才开层，用 size.width 跟 pager 对齐。
                                val scheduleTitleIdle by remember(scheduleTitleIndex, navBarStyle) {
                                    derivedStateOf {
                                        val page = mainPagerState.currentPage
                                        val off = mainPagerState.currentPageOffsetFraction
                                        (scheduleTitleIndex - page - off) == 0f
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (scheduleTitleIdle) {
                                                Modifier
                                            } else {
                                                Modifier.graphicsLayer {
                                                    val page = mainPagerState.currentPage
                                                    val off = mainPagerState.currentPageOffsetFraction
                                                    val delta = scheduleTitleIndex - page - off
                                                    if (navBarStyle == "rail") {
                                                        translationX = 0f
                                                        translationY = delta * screenHPx
                                                    } else {
                                                        translationY = 0f
                                                        translationX = delta * size.width
                                                    }
                                                }
                                            }
                                        )
                                ) {
                                    // 锁本页主题：共享顶栏槽外层主题跟 selectedTab 变，
                                    // 切到设置页（浅色）时课程表顶栏遮罩会跟着跳浅
                                    PageLockedTopBarTheme(
                                        if (captureThemeActive) captureThemeIsDark
                                        else wallpaperForcedDark
                                    ) {
                                        val lockedSurface = MiuixTheme.colorScheme.surface
                                        ScheduleTopBar(
                                            visible = true,
                                            pagerCurrentPage = pagerState.currentPage,
                                            currentWeek = currentWeek,
                                            isHoliday = viewingIsHoliday,
                                            isViewingCurrentWeek = isViewingCurrentWeek,
                                            dayRange = dayRange,
                                            currentDayOfWeek = currentDayOfWeek,
                                            isCurrentWeek = pagerState.currentPage + 1 == currentWeek && currentWeek in 1..totalWeeks,
                                            weekDates = weekDates,
                                            isReorganized = teachingWeekRules.isNotEmpty(),
                                            onBackToCurrentWeek = {
                                                coroutineScope.launch {
                                                    val targetPage =
                                                        (currentWeek - 1).coerceIn(
                                                            0,
                                                            (totalWeeks - 1).coerceAtLeast(0)
                                                        )
                                                    pagerState.animateScrollToPage(targetPage)
                                                }
                                            },
                                            onOpenSwitchSchedule = {
                                                if (!isShiftMode && !showSwitchSchedule) {
                                                    com.haooz.chedule.ui.utils.CrashLogHelper.trace(
                                                        "切换课表", "open",
                                                        "shift=$isShiftMode tab=$selectedTab"
                                                    )
                                                    coroutineScope.launch {
                                                        // 切换页组合与快照截取并行，避免串行等待导致界面无响应
                                                        switchPendingReverse = true
                                                        switchCapturingSnapshot = true
                                                        showSwitchSchedule = true
                                                        mainContentSnapshot = try {
                                                            captureMainContentBitmap()
                                                        } catch (e: Exception) {
                                                            com.haooz.chedule.ui.utils.CrashLogHelper.trace(
                                                                "切换课表", "open_snapshot_fail",
                                                                e.javaClass.simpleName
                                                            )
                                                            null
                                                        }
                                                        com.haooz.chedule.ui.utils.CrashLogHelper.trace(
                                                            "切换课表", "open_snapshot_ok",
                                                            "bmp=${mainContentSnapshot != null}"
                                                        )
                                                    }
                                                }
                                            },
                                            onJumpWeek = { viewModel.showJumpWeekDialog() },
                                            onEnterCustomize = {
                                                coroutineScope.launch {
                                                    delay(200.milliseconds)
                                                    enterCustomizePage()
                                                }
                                            },
                                            isTablet = isTablet,
                                            isShiftMode = isShiftMode,
                                            liquidGlassBackdrop = chromeBackdrop,
                                            scrollBehavior = scheduleScrollBehavior,
                                            blurResampleKey = railBlurResampleEpoch.intValue,
                                            blurSampleTrack = com.haooz.chedule.ui.components.tabletNavExpandSampleTrack,
                                            onMoreSlotTop = { if (canSampleMoreSlot) moreSlotTopPx.value = it },
                                            onMoreMaterial = { scheduleMaterialAlpha.value = it },
                                            gradientColorOverride = lockedSurface,
                                        )
                                    }
                                }
                            }
                            if (showSettingsTitle) {
                                val settingsTitleIndex = if (isShiftMode) 1 else 2
                                val settingsTitleIdle by remember(settingsTitleIndex, navBarStyle) {
                                    derivedStateOf {
                                        val page = mainPagerState.currentPage
                                        val off = mainPagerState.currentPageOffsetFraction
                                        (settingsTitleIndex - page - off) == 0f
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (settingsTitleIdle) {
                                                Modifier
                                            } else {
                                                Modifier.graphicsLayer {
                                                    val page = mainPagerState.currentPage
                                                    val off = mainPagerState.currentPageOffsetFraction
                                                    val delta = settingsTitleIndex - page - off
                                                    if (navBarStyle == "rail") {
                                                        translationX = 0f
                                                        translationY = delta * screenHPx
                                                    } else {
                                                        translationY = 0f
                                                        translationX = delta * size.width
                                                    }
                                                }
                                            }
                                        )
                                ) {
                                    SettingsTopBar(
                                        liquidGlassBackdrop = chromeBackdrop,
                                        navBarStyle = navBarStyle,
                                        scrollBehavior = settingsScrollBehavior,
                                        blurResampleKey = railBlurResampleEpoch.intValue,
                                        blurSampleTrack = com.haooz.chedule.ui.components.tabletNavExpandSampleTrack,
                                    )
                                }
                            }
                            // 始终渲染，保证 currentHeightPx 启动即就位，切页不慢一帧
                            // 空闲在本页时不挂层；离屏时用 graphicsLayer 位移+隐藏
                            val todayTitleIdle by remember(navBarStyle) {
                                derivedStateOf {
                                    val page = mainPagerState.currentPage
                                    val off = mainPagerState.currentPageOffsetFraction
                                    val delta = 0 - page - off
                                    delta == 0f && showTodayTitle
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (todayTitleIdle) {
                                            Modifier
                                        } else {
                                            Modifier.graphicsLayer {
                                                val page = mainPagerState.currentPage
                                                val off = mainPagerState.currentPageOffsetFraction
                                                val delta = 0 - page - off
                                                if (navBarStyle == "rail") {
                                                    translationX = 0f
                                                    translationY = delta * screenHPx
                                                } else {
                                                    translationY = 0f
                                                    translationX = delta * size.width
                                                }
                                                alpha = if (showTodayTitle) 1f else 0f
                                            }
                                        }
                                    )
                            ) {
                                // 同 ScheduleTopBar：锁本页主题，避免切到设置页时今日页遮罩跳浅
                                PageLockedTopBarTheme(
                                    if (captureThemeActive) captureThemeIsDark
                                    else todayPageForcedDark
                                ) {
                                    val lockedSurface = MiuixTheme.colorScheme.surface
                                    TodayTopBar(
                                        liquidGlassBackdrop = chromeBackdrop,
                                        navBarStyle = navBarStyle,
                                        currentDayOfWeek = todaySelectedDayOfWeek,
                                        isToday = todayIsToday,
                                        onBackToToday = { scrollToTodayTrigger++ },
                                        onJumpToDate = { todayJumpToDateTrigger++ },
                                        onEnterCustomize = {
                                            coroutineScope.launch {
                                                delay(200.milliseconds)
                                                enterCustomizePage()
                                            }
                                        },
                                        scrollBehavior = todayScrollBehavior,
                                        visible = showTodayTitle,
                                        blurResampleKey = railBlurResampleEpoch.intValue,
                                        blurSampleTrack = com.haooz.chedule.ui.components.tabletNavExpandSampleTrack,
                                        onMoreSlotTop = { if (canSampleMoreSlot) moreSlotTopPx.value = it },
                                        onMoreMaterial = { todayMaterialAlpha.value = it },
                                        gradientColorOverride = lockedSurface,
                                    )
                                }
                            }
                        }
                    },
                    // popup 槽是 Scaffold 最上层，但内容按组合顺序绘制：
                    // 「更多」控件排在 MiuixPopupHost 之前，故只高于顶栏按钮、低于弹窗，
                    // 打开添加课程弹窗时会被遮罩一起压暗
                    popupHost = {
                        // 单个「更多」控件随所在顶栏一起滑动：与顶栏同一套 delta 公式；
                        // 非当前页时整块被平移出屏幕，天然只留一份可见
                        val scheduleMoreMenuOffset: () -> Offset = {
                            val page = mainPagerState.currentPage
                            val off = mainPagerState.currentPageOffsetFraction
                            val delta = (if (isShiftMode) 0 else 1) - page - off
                            if (navBarStyle == "rail") Offset(0f, delta * screenHPx)
                            else Offset(delta * screenWPx, 0f)
                        }
                        val todayMoreMenuOffset: () -> Offset = {
                            val page = mainPagerState.currentPage
                            val off = mainPagerState.currentPageOffsetFraction
                            // 排班模式下没有今日页，恒移出屏幕
                            val delta = if (isShiftMode) -1f else (-page - off)
                            if (navBarStyle == "rail") Offset(0f, delta * screenHPx)
                            else Offset(delta * screenWPx, 0f)
                        }
                        // 「更多」与顶栏同源问题：外层 chrome 主题跟 selectedTab 变，
                        // 切到设置页时未移出屏幕的「更多」会跳色。今日/课程表壁纸已恒同步
                        PageLockedTopBarTheme(wallpaperForcedDark) {
                        // LocalAppRouter.current 必须在组合层取（onClick lambda 里取不到）
                        val popupRouter = LocalAppRouter.current
                        MorePopupMenus(
                                    showMorePopup = showMorePopup,
                                    onMorePopupDismiss = { showMorePopup = false },
                                    onMorePopupExpand = { showMorePopup = true },
                                    showTodayMorePopup = showTodayMorePopup,
                                    onTodayMorePopupDismiss = { showTodayMorePopup = false },
                                    onTodayMorePopupExpand = { showTodayMorePopup = true },
                                    morePopupFraction = morePopupFraction,
                                    todayMorePopupFraction = todayMorePopupFraction,
                                    scheduleMenuOffset = scheduleMoreMenuOffset,
                                    todayMenuOffset = todayMoreMenuOffset,
                                    moreSlotTopPx = moreSlotTopPx,
                                    scheduleMaterialAlpha = scheduleMaterialAlpha.value,
                                    todayMaterialAlpha = todayMaterialAlpha.value,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    isShiftMode = isShiftMode,
                                    // 平板（rail）右上角不显示「更多」
                                    enabled = navBarStyle != "rail",
                                    // 预测性返回由 LiquidGlassDropdownMenu 内部驱动：
                                    // 它操作的就是这里传入的同一个 fraction Animatable，
                                    // 外面再 snapTo/animateTo 一遍会和它互相 cancel，动画直接卡死。
                                    onJumpWeek = { viewModel.showJumpWeekDialog() },
                                    onCourseManage = {
                                        popupRouter.navigate(
                                            com.haooz.chedule.ui.navigation.AppRoute.CourseManage
                                        )
                                    },
                                    onEnterCustomize = {
                                        coroutineScope.launch {
                                            delay(200.milliseconds)
                                            enterCustomizePage()
                                        }
                                    },
                                    onJumpToDate = { todayJumpToDateTrigger++ },
                                )
                        }
                        // 弹窗排在「更多」之后 → 始终盖在它之上，遮罩能压暗「更多」
                        MiuixPopupHost()
                    }
                ) { paddingValues ->
                    // 详情动画期间用快照占位；占位须吃满触摸，避免事件穿到卡片
                    if (showDetail && mainContentSnapshot != null) {
                        Box(modifier = Modifier.fillMaxSize().consumeAllTouches())
                        return@Scaffold
                    }
                    // 不门控 combinations.isEmpty()：网格只依赖 viewModel，与壁纸加载解耦
                    // backdrop 挂在未 padding 的全屏层：录制原点固定在屏幕
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 开洞时 mustRecord 恒 true：停录会让顶栏/底栏玻璃采样空内容
                            .liquidGlassLayerBackdrop(
                                backdrop = liquidGlassBackdrop,
                                recordKey = liquidGlassRecordKey,
                                mustRecord = liquidGlassMustRecord
                            )
                        ) {
                        // 共享壁纸层：铺满全屏（含侧栏下），与 rail 让位无关，侧栏伸缩时像素不动。
                            // 今日↔课程表：壁纸钉死不动；课程表→设置：壁纸随页平移，侧栏下用背景色盖住。
                            val sharedWallpaperBitmap =
                                if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBitmap
                                else wallpaperBitmap
                            val sharedWallpaperOffset =
                                if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset
                                else wallpaperOffset
                            val sharedWallpaperScale =
                                if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale
                                else wallpaperScale
                            // 可见性用 derivedStateOf：pager 每帧变 offset 时不重组父层/HorizontalPager
                            val showSharedWallpaperLayer by remember(
                                isShiftMode,
                                sharedWallpaperBitmap,
                                showCustomizePage,
                                mainPagerState,
                            ) {
                                derivedStateOf {
                                    // 搭配页/开洞编辑：必须始终显示壁纸层，否则选完壁纸「没反应」
                                    if (showCustomizePage && sharedWallpaperBitmap != null) {
                                        return@derivedStateOf true
                                    }
                                    val page = mainPagerState.currentPage
                                    val off = abs(mainPagerState.currentPageOffsetFraction)
                                    !isShiftMode && sharedWallpaperBitmap != null &&
                                        (page != 2 || off > 0.01f)
                                }
                            }
                            if (showSharedWallpaperLayer && sharedWallpaperBitmap != null) {
                                // 全屏垫背景色 + 裁切：壁纸切页平移时露出的空隙由背景色盖住（侧栏下始终有内容，不出漏）
                                val wallpaperPadColor =
                                    MiuixTheme.colorScheme.surface
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(wallpaperPadColor)
                                        .clipToBounds()
                                ) {
                                val sharedMinScale = remember(sharedWallpaperBitmap, screenWPx, screenHPx) {
                                    if (sharedWallpaperBitmap.width > 0 && sharedWallpaperBitmap.height > 0) {
                                        val fit = minOf(
                                            screenWPx / sharedWallpaperBitmap.width,
                                            screenHPx / sharedWallpaperBitmap.height
                                        )
                                        val cover = maxOf(
                                            screenWPx / sharedWallpaperBitmap.width,
                                            screenHPx / sharedWallpaperBitmap.height
                                        )
                                        if (fit > 0f) cover / fit else 1f
                                    } else 1f
                                }
                                val sharedBrightness = displayAppearance.wallpaperBrightness
                                val sharedBrightnessFilter = remember(sharedBrightness) {
                                    if (sharedBrightness != 0f) {
                                        val b = (1f + sharedBrightness / 50f).coerceIn(0f, 2f)
                                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                            androidx.compose.ui.graphics.ColorMatrix(
                                                floatArrayOf(
                                                    b, 0f, 0f, 0f, 0f,
                                                    0f, b, 0f, 0f, 0f,
                                                    0f, 0f, b, 0f, 0f,
                                                    0f, 0f, 0f, 1f, 0f
                                                )
                                            )
                                        )
                                    } else null
                                }
                                val sharedBlurEffect = remember(displayAppearance.wallpaperBlur) {
                                    if (displayAppearance.wallpaperBlur) {
                                        ApiCompat.blurRenderEffect(24f * density.density)
                                    } else null
                                }
                                val sharedWallpaperImage = remember(sharedWallpaperBitmap) {
                                    sharedWallpaperBitmap.asImageBitmap()
                                }
                                // 课程表页索引：正常模式 1；滚过它去设置时壁纸开始位移
                                val schedulePageIndex = if (isShiftMode) 0 else 1
                                // 今日↔课程表壁纸钉死不动，只有越过课程表去设置时像素才随位移变化。
                                val sharedWallpaperMustRecord =
                                    remember(mainPagerState, schedulePageIndex) {
                                        {
                                            val scrollPos = mainPagerState.currentPage +
                                                mainPagerState.currentPageOffsetFraction
                                            val moving = scrollPos > schedulePageIndex
                                            moving &&
                                                (mainPagerState.isScrollInProgress ||
                                                    abs(mainPagerState.currentPageOffsetFraction) > 0.001f)
                                        }
                                    }
                                Image(
                                    bitmap = sharedWallpaperImage,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .liquidGlassLayerBackdrop(
                                            backdrop = sharedWallpaperBackdrop,
                                            recordKey = listOf(
                                                sharedWallpaperBitmap,
                                                sharedWallpaperScale,
                                                sharedWallpaperOffset.x,
                                                sharedWallpaperOffset.y,
                                                sharedBrightness,
                                                displayAppearance.wallpaperBlur,
                                                sharedMinScale,
                                                schedulePageIndex,
                                            ),
                                            mustRecord = sharedWallpaperMustRecord
                                        )
                                        .graphicsLayer {
                                            val s = maxOf(sharedWallpaperScale, sharedMinScale)
                                            scaleX = s
                                            scaleY = s
                                            // 缩放/平移裁在自身 bounds 内，避免突出课程表页画进侧栏
                                            clip = true
                                            val scrollPos = mainPagerState.currentPage +
                                                mainPagerState.currentPageOffsetFraction
                                            // 今日/课程表区间内固定；越过课程表去设置时壁纸跟页
                                            // 手机横向（向左），平板竖向（向上）
                                            val settingsShift =
                                                if (scrollPos > schedulePageIndex) {
                                                    val span =
                                                        if (navBarStyle == "rail") screenHPx else screenWPx
                                                    -(scrollPos - schedulePageIndex) * span
                                                } else {
                                                    0f
                                                }
                                            if (navBarStyle == "rail") {
                                                translationX = sharedWallpaperOffset.x
                                                translationY = sharedWallpaperOffset.y + settingsShift
                                            } else {
                                                translationX = sharedWallpaperOffset.x + settingsShift
                                                translationY = sharedWallpaperOffset.y
                                            }
                                            renderEffect = sharedBlurEffect
                                        },
                                    contentScale = ContentScale.Fit,
                                    colorFilter = sharedBrightnessFilter
                                )
                                }
                            }

                            // 平移跟设置页同步，避免切回浅色课程表时侧栏下闪白。
                            run {
                                val settingsPageIndex = if (isShiftMode) 1 else 2
                                // 锁定应用主题背景色，不随课程表页/壁纸锁色变化
                                val settingsCoverTranslation by remember(mainPagerState, isShiftMode, navBarStyle) {
                                    derivedStateOf {
                                        val scrollPos = mainPagerState.currentPage +
                                            mainPagerState.currentPageOffsetFraction
                                        (settingsPageIndex - scrollPos)
                                    }
                                }
                                val settingsCoverController = remember {
                                    ThemeController(
                                        if (appSettingDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
                                    )
                                }
                                settingsCoverController.colorSchemeMode =
                                    if (appSettingDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
                                MiuixTheme(controller = settingsCoverController) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                val t = settingsCoverTranslation
                                                if (navBarStyle == "rail") {
                                                    translationY = t * screenHPx
                                                } else {
                                                    translationX = t * screenWPx
                                                }
                                            }
                                            
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (navBarStyle == "rail") {
                                            com.haooz.chedule.ui.components.tabletNavRailStartPadding()
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                            // 主 tab 仅由底栏/侧栏点击切换；关闭用户手势翻页。
                            // 平板（rail）竖向移动，手机横向移动。
                            val mainPagerModifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(mainContentNestedScroll)
                            val mainPagerPageKey: (Int) -> String = { page ->
                                if (isShiftMode) "shift-$page" else "main-$page"
                            }
                            val mainPagerPageContent: @Composable (Int) -> Unit = { page ->
                                if (!isShiftMode) {
                                    when (page) {
                                        0 -> {
                                            // 今日页主题预先固定，切 tab 不跟着 chrome 闪一帧
                                            val todayForced = if (captureThemeActive) captureThemeIsDark else todayPageForcedDark
                                            val todayDark = todayForced ?: appSettingDark
                                            val todayThemeController = remember {
                                                ThemeController(if (todayDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
                                        }
                                        todayThemeController.colorSchemeMode =
                                            if (todayDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
                                        MiuixTheme(controller = todayThemeController) {
                                            CompositionLocalProvider(LocalForcedDarkTheme provides todayForced) {
                                        TodayScreen(
                                            viewModel = viewModel,
                                            settingsViewModel = settingsViewModel,
                                            hiddenCourseIds = hiddenCourseIds,
                                            holidayDataTick = resumeCount,
                                            onCourseClick = { courses, left, top, width, height, _, courseIdToHide, targetWeek ->
                                                openCourseDetail(
                                                    courses,
                                                    left,
                                                    top,
                                                    width,
                                                    height,
                                                    fromToday = true,
                                                    courseIdToHide = courseIdToHide,
                                                    targetWeek = targetWeek
                                                )
                                            },
                                            pagerState = todayPagerState,
                                            navBarStyle = navBarStyle,
                                            onScrollYChanged = { _ -> },
                                            settingsScrollBehavior = todayScrollBehavior,
                                            onSelectedDayChanged = { todaySelectedDayOfWeek = it },
                                            onSelectedDateChanged = { todayIsToday = it },
                                            scrollToTodayTrigger = scrollToTodayTrigger,
                                            jumpToDateTrigger = todayJumpToDateTrigger,
                                            onJumpToDateProcessed = { todayJumpToDateTrigger = 0 },
                                            wallpaperBitmap = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBitmap else wallpaperBitmap,
                                            wallpaperOffset = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset,
                                            wallpaperScale = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale,
                                            useSharedWallpaper = !isShiftMode && wallpaperBitmap != null,
                                            sharedWallpaperBackdrop = sharedWallpaperBackdrop,
                                            wallpaperBrightness = displayAppearance.wallpaperBrightness,
                                            cardBlurRadius = displayAppearance.cardBlurRadius,
                                            cardRefraction = displayAppearance.cardRefraction,
                                            cardSurfaceAlpha = displayAppearance.cardSurfaceAlpha,
                                            wallpaperBlur = displayAppearance.wallpaperBlur,
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                            showClassroom = displayAppearance.showClassroom,
                                            showTeacher = displayAppearance.showTeacher,
                                            onListScrollInProgress = { todayListScrollInProgress.value = it },
                                        )
                                            }
                                        }
                                    }
                                    1 -> {
                                        val scheduleForced = if (captureThemeActive) captureThemeIsDark else wallpaperForcedDark
                                        val scheduleDark = scheduleForced ?: appSettingDark
                                        val scheduleThemeController = remember {
                                            ThemeController(if (scheduleDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
                                        }
                                        scheduleThemeController.colorSchemeMode =
                                            if (scheduleDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
                                        MiuixTheme(controller = scheduleThemeController) {
                                            CompositionLocalProvider(LocalForcedDarkTheme provides scheduleForced) {
                                        CompositionLocalProvider(
                                            LocalLandRipple provides LandRippleSpec(
                                                center = landRippleCenter,
                                                token = landRippleToken
                                            )
                                        ) {
                                            MainScheduleScreen(
                                            viewModel = viewModel,
                                            settingsViewModel = settingsViewModel,
                                            pagerState = pagerState,
                                            hiddenCourseIds = hiddenCourseIds,
                                            draggingCourseIds = draggingCourseIds,
                                            onCourseClick = { courses, left, top, width, height, _, courseIdToHide, targetWeek ->
                                                openCourseDetail(
                                                    courses,
                                                    left,
                                                    top,
                                                    width,
                                                    height,
                                                    fromToday = false,
                                                    courseIdToHide = courseIdToHide,
                                                    targetWeek = targetWeek
                                                )
                                            },
                                            onPopupStateChange = { showCourseDetailPopup = it },
                                            onEmptyLongPress = { day, section, centerX, cellTopY, width, _, addDay, addWeek ->
                                                shortcutMenuCourse = null
                                                emptyCellMenuTarget = EmptyCellMenuTarget(
                                                    columnDay = day,
                                                    section = section,
                                                    addDay = addDay,
                                                    addWeek = addWeek,
                                                )
                                                shortcutMenuVisible = true
                                                shortcutMenuPosition = Offset(centerX - width / 2f, cellTopY)
                                                shortcutMenuAnchorWidth = width
                                            },
                                            onCourseLongPress = { course, left, top, width, height, backdrop, currentWeek ->
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                isDraggingCard = true
                                                floatingCardVisible = true
                                                // 新一轮长按：还没开始拖，高光先关
                                                isFloatingCardDragging = false
                                                draggingCourseIds = setOf(course.id)
                                                draggedCardCourse = course
                                                draggedWeek = currentWeek
                                                // 跨午休/晚修课以整课视觉几何为锚点，避免长按在分段卡片上时中心偏移
                                                val geom = gridGeometry
                                                val anchorCenter: Offset
                                                val anchorSize: Offset
                                                if (geom != null) {
                                                    val div = dividerPxFor(geom)
                                                    val fullH = courseVisualHeightPx(
                                                        geom,
                                                        course.startSection,
                                                        course.endSection,
                                                        div
                                                    )
                                                    val bounds = geom.dayBounds[course.dayOfWeek]
                                                        ?: geom.dayBounds.values.firstOrNull()
                                                    // 宽度沿用卡片回调（含 Day 列内 2dp padding 后的实际宽），不要用整列 dayBounds
                                                    val topRel = sectionTopPx(geom, course.startSection, div)
                                                    // X 必须用卡片实测中心 left：
                                                    // ① 调课日列的数据星期 ≠ 列星期，dayBounds[course.dayOfWeek] 会取到隔壁列，
                                                    //    浮层/菜单整块错到别的列；② 自定义时间课按时间轴定位，不按节次几何。
                                                    anchorCenter = when {
                                                        course.hasValidCustomTime() ->
                                                            Offset(left, top)
                                                        bounds != null && bounds.size >= 3 ->
                                                            Offset(left, bounds[2] + topRel + fullH / 2f)
                                                        else ->
                                                            Offset(left, top)
                                                    }
                                                    anchorSize = if (course.hasValidCustomTime()) {
                                                        Offset(width, height)
                                                    } else {
                                                        Offset(width, fullH)
                                                    }
                                                } else {
                                                    anchorCenter = Offset(left, top)
                                                    anchorSize = Offset(width, height)
                                                }
                                                draggedCardPosition = anchorCenter
                                                // 由卡片实测 X 反推所在列：数据星期在调课日列对不上列
                                                draggedColumnDay = geom?.dayBounds?.entries
                                                    ?.firstOrNull { (_, b) ->
                                                        b.size >= 2 && left >= b[0] && left <= b[1]
                                                    }?.key ?: course.dayOfWeek
                                                dragMotion.reset()
                                                floatingOffsetX.floatValue = 0f
                                                floatingOffsetY.floatValue = 0f
                                                draggedCardSize = anchorSize
                                                draggedCardBackdrop = backdrop
                                                shortcutMenuCourse = course
                                                emptyCellMenuTarget = null
                                                shortcutMenuVisible = true
                                                shortcutMenuPosition =
                                                    Offset(
                                                        anchorCenter.x - anchorSize.x / 2f,
                                                        anchorCenter.y - anchorSize.y / 2f
                                                    )
                                                shortcutMenuAnchorWidth = anchorSize.x
                                                shortcutMenuBackdrop = backdrop
                                            },
                                            onCourseDragStart = { _ ->
                                                // 不关菜单；超过移动阈值后由 onCourseMenuDismiss 关闭
                                                pendingDropTarget = null
                                                isFloatingCardDragging = true
                                            },
                                            onCourseMenuDismiss = {
                                                shortcutMenuVisible = false
                                                coroutineScope.launch {
                                                    delay(220.milliseconds)
                                                    shortcutMenuCourse = null
                                                }
                                            },
                                            onCourseDrag = { _, offsetX, offsetY ->
                                                // 跟手 1:1；offset 只进 graphicsLayer，速度进 holder
                                                val now = android.os.SystemClock.uptimeMillis()
                                                if (dragMotion.lastTimeMs != 0L) {
                                                    val dt = (now - dragMotion.lastTimeMs).coerceAtLeast(1L)
                                                    dragMotion.velocityX =
                                                        (offsetX - dragMotion.lastOffsetX) / dt * 1000f
                                                    dragMotion.velocityY =
                                                        (offsetY - dragMotion.lastOffsetY) / dt * 1000f
                                                }
                                                dragMotion.lastTimeMs = now
                                                dragMotion.lastOffsetX = offsetX
                                                dragMotion.lastOffsetY = offsetY
                                                floatingOffsetX.floatValue = offsetX
                                                floatingOffsetY.floatValue = offsetY
                                                // 落点仅跨格时写 state，避免逐帧重组课表
                                                val course = draggedCardCourse
                                                if (course != null) {
                                                    val geomNow = gridGeometry
                                                    val sectionH = geomNow?.sectionHeightPx ?: 0f
                                                    val sectionCount =
                                                        course.endSection - course.startSection + 1
                                                    // 必须与浮层卡片用同一套高度（含分界缝与特殊块挤占）：
                                                    // 之前写死 sectionCount * sectionH，跨午休/晚修或插了特殊块时
                                                    // 「起始节次中心」会算偏，落点命中与高亮就错开一节。
                                                    val cardHeightPx = when {
                                                        course.hasValidCustomTime() -> draggedCardSize.y
                                                        geomNow != null -> courseVisualHeightPx(
                                                            geomNow,
                                                            course.startSection,
                                                            course.endSection,
                                                            dividerPxFor(geomNow),
                                                        )
                                                        else -> sectionCount * sectionH
                                                    }
                                                    val centerX = draggedCardPosition.x + offsetX
                                                    val cardTopY =
                                                        draggedCardPosition.y + offsetY - cardHeightPx / 2f
                                                    val firstSectionCenterY =
                                                        cardTopY + sectionH / 2f
                                                    val rawTarget = computeDropTarget(
                                                        centerX,
                                                        firstSectionCenterY
                                                    )
                                                    // 放不下的格子不给落点：起始节次+跨度超过总节次
                                                    // （与粘贴路径同款校验），否则松手写出
                                                    // endSection > totalSections 的课，渲染溢出/错位
                                                    val newTarget = rawTarget?.takeIf {
                                                        it.second + sectionCount - 1 <= totalSections
                                                    }
                                                    if (newTarget != pendingDropTarget) {
                                                        pendingDropTarget = newTarget
                                                    }
                                                }
                                            },
                                            onCourseDragEnd = { _ ->
                                                // 手指已离开：高光立刻开始淡出，不等下面的退场动画
                                                isFloatingCardDragging = false
                                                // 只结束拖拽浮层；菜单关闭交给 onCourseMenuDismiss
                                                val source = draggedCardCourse
                                                val target = pendingDropTarget
                                                val week = draggedWeek
                                                pendingDropTarget = null
                                                if (source != null && target != null) {
                                                    // 列 → 数据坐标：调课日列显示的是 followWeekday/followWeek 的课，
                                                    // 冲突检测与写库都必须落在数据坐标上，几何/动画仍用列
                                                    val (targetDay, targetWeek) =
                                                        dataPositionForColumn(target.first)
                                                    val sectionSpan =
                                                        source.endSection - source.startSection
                                                    val targetStart = target.second
                                                    val targetEnd = targetStart + sectionSpan
                                                    // 同格按数据坐标判：调课日列里原地拖动不再被误判成跨天
                                                    val sameSlot =
                                                        source.dayOfWeek == targetDay &&
                                                                week == targetWeek &&
                                                                source.startSection == targetStart &&
                                                                source.endSection == targetEnd
                                                    if (!sameSlot) {
                                                        val conflicts = viewModel.getCoursesAtSlot(
                                                            targetWeek,
                                                            targetDay,
                                                            targetStart,
                                                            targetEnd
                                                        ).filter {
                                                            it.id != source.id && it.isActiveInWeek(
                                                                targetWeek
                                                            )
                                                        }
                                                        if (conflicts.isEmpty()) {
                                                            viewModel.moveCourseForWeek(
                                                                source.id,
                                                                week,
                                                                targetDay,
                                                                targetStart,
                                                                targetEnd,
                                                                targetWeek
                                                            )
                                                            // 单周调课可能拆分/合并出新 id，按目标位重收并隐藏防叠影
                                                            draggingCourseIds =
                                                                viewModel.getCoursesAtSlot(
                                                                    targetWeek,
                                                                    targetDay,
                                                                    targetStart,
                                                                    targetEnd
                                                                )
                                                                    .filter { it.isActiveInWeek(targetWeek) }
                                                                    .map { it.id }
                                                                    .toSet()
                                                            snapFloatingCardToTarget(
                                                                target.first,
                                                                targetStart,
                                                                sectionSpan
                                                            )
                                                            // 原位置露出的非本周课程淡入放大
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
                                                                    sourceCourses.map { it.id }
                                                                        .toSet()
                                                                coroutineScope.launch {
                                                                    delay(350.milliseconds)
                                                                    animateInCourseIds = emptySet()
                                                                }
                                                            }
                                                        } else {
                                                            // 浮层连贯移到目标卡上方悬停等用户选择
                                                            pendingConflictCourse =
                                                                conflicts.first()
                                                            pendingDropTarget =
                                                                target.first to targetStart
                                                            showRescheduleConflictDialog = true
                                                            val hoverTargetCenter =
                                                                computeTargetCenter(
                                                                    target.first,
                                                                    targetStart,
                                                                    sectionSpan
                                                                )
                                                            if (hoverTargetCenter != null) {
                                                                coroutineScope.launch {
                                                                    stopConflictHover()
                                                                    isConflictHover = true
                                                                    isSnapping = true
                                                                    // 拖拽位移已在 floatingOffset*
                                                                    val sectionHPx =
                                                                        gridGeometry?.sectionHeightPx
                                                                            ?: 0f
                                                                    val floatH =
                                                                        (sectionSpan + 1) * sectionHPx
                                                                    val gap = with(density) {
                                                                        10.dp.toPx()
                                                                    }
                                                                    val hoverOffsetX =
                                                                        hoverTargetCenter.x -
                                                                                draggedCardPosition.x
                                                                    val hoverOffsetY =
                                                                        hoverTargetCenter.y -
                                                                                draggedCardPosition.y -
                                                                                floatH - gap
                                                                    val settleEase =
                                                                        CubicBezierEasing(
                                                                            0.32f, 0.72f, 0.28f, 1f
                                                                        )
                                                                    val jobX = launch {
                                                                        animateFloatState(
                                                                            floatingOffsetX,
                                                                            hoverOffsetX,
                                                                            tween(
                                                                                durationMillis = 420,
                                                                                easing = settleEase
                                                                            )
                                                                        )
                                                                    }
                                                                    val jobY = launch {
                                                                        animateFloatState(
                                                                            floatingOffsetY,
                                                                            hoverOffsetY,
                                                                            tween(
                                                                                durationMillis = 420,
                                                                                easing = settleEase
                                                                            )
                                                                        )
                                                                    }
                                                                    val jobScale = launch {
                                                                        floatingScale.animateTo(
                                                                            1.08f,
                                                                            tween(durationMillis = 420)
                                                                        )
                                                                    }
                                                                    jobX.join(); jobY.join(); jobScale.join()
                                                                    val bobAmp = with(density) {
                                                                        4.dp.toPx()
                                                                    }
                                                                    conflictHoverBobJob = launch {
                                                                        while (true) {
                                                                            animateFloatState(
                                                                                floatingOffsetY,
                                                                                hoverOffsetY + bobAmp,
                                                                                tween(
                                                                                    durationMillis = 1400,
                                                                                    easing = FastOutSlowInEasing
                                                                                )
                                                                            )
                                                                            animateFloatState(
                                                                                floatingOffsetY,
                                                                                hoverOffsetY - bobAmp,
                                                                                tween(
                                                                                    durationMillis = 1400,
                                                                                    easing = FastOutSlowInEasing
                                                                                )
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            } else {
                                                                coroutineScope.launch {
                                                                    floatingScale.animateTo(
                                                                        1f,
                                                                        tween(durationMillis = 180)
                                                                    )
                                                                    delay(180.milliseconds)
                                                                    isDraggingCard = false
                                                                    floatingCardVisible = false
                                                                    draggingCourseIds = emptySet()
                                                                    floatingOffsetX.floatValue = 0f
                                                                    floatingOffsetY.floatValue = 0f
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        snapFloatingCardToOrigin()
                                                    }
                                                } else if (source != null) {
                                                    // 无落点：松手在网格外 / 超出总节次 / 落到重组休课日
                                                    // → 与「同格」一样吸回原位，不凭空消失
                                                    stopConflictHover()
                                                    snapFloatingCardToOrigin()
                                                } else {
                                                    dismissFloatingCard()
                                                }
                                            },
                                            wallpaperBitmap = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBitmap else wallpaperBitmap,
                                            wallpaperOffset = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset,
                                            wallpaperScale = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale,
                                            useSharedWallpaper = !isShiftMode && wallpaperBitmap != null,
                                            sharedWallpaperBackdrop = sharedWallpaperBackdrop,
                                            isWallpaperEditing = isWindowCutoutActive,
                                            onWallpaperOffsetChange = { wallpaperOffset = it },
                                            onWallpaperScaleChange = { wallpaperScale = it },
                                            appearance = displayAppearance,
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                            onGridGeometryChange = { geom -> gridGeometry = geom },
                                            dropHighlight = run {
                                                val emptyTarget = emptyCellMenuTarget
                                                if (emptyTarget != null && shortcutMenuVisible) {
                                                    emptyTarget.columnDay to (emptyTarget.section..emptyTarget.section)
                                                } else {
                                                    val target = pendingDropTarget
                                                    val source = draggedCardCourse
                                                    if (floatingCardVisible && target != null && source != null) {
                                                        val sectionSpan =
                                                            source.endSection - source.startSection
                                                        target.first to (target.second..(target.second + sectionSpan))
                                                    } else null
                                                }
                                            },
                                            dropHighlightOrigin = run {
                                                val source = draggedCardCourse
                                                if (floatingCardVisible && source != null && !isPasteFlight) {
                                                    // 高亮画在网格列上：调课日列要用列星期，不能用数据星期
                                                    draggedColumnDay to (source.startSection..source.endSection)
                                                } else {
                                                    val emptyTarget = emptyCellMenuTarget
                                                    if (emptyTarget != null && shortcutMenuVisible) {
                                                        emptyTarget.columnDay to (emptyTarget.section..emptyTarget.section)
                                                    } else null
                                                }
                                            },
                                            scheduleScrollBehavior = scheduleScrollBehavior,
                                                externalScrollState = scheduleScrollState,
                                            externalShowCourseDetail = scheduleShowCourseDetail,
                                            externalSelectedCourse = scheduleSelectedCourse,
                                            externalSelectedCourses = scheduleSelectedCourses
                                        )
                                        }
                                            }
                                        }
                                    }
                                    2 -> {
                                        // 设置页始终跟应用主题，不被壁纸锁深色
                                        val settingsDark = appSettingDark
                                        val settingsThemeController = remember {
                                            ThemeController(if (settingsDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
                                        }
                                        settingsThemeController.colorSchemeMode =
                                            if (settingsDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
                                        MiuixTheme(controller = settingsThemeController) {
                                            CompositionLocalProvider(LocalForcedDarkTheme provides settingsPageForcedDark) {
                                        SettingsScreen(
                                            viewModel = viewModel,
                                            scheduleViewModel = scheduleViewModel,
                                            settingsViewModel = settingsViewModel,
                                            shiftViewModel = shiftViewModel,
                                            onEnterShiftMode = {
                                                com.haooz.chedule.ui.utils.FeatureLog.shift("enter_loading")
                                                showShiftLoading = true
                                                isExitingShift = false
                                            },
                                            navBarStyle = navBarStyle,
                                            onScrollYChanged = { _ -> },
                                            settingsScrollBehavior = settingsScrollBehavior,
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                        )
                                            }
                                        }
                                    }
                                    3 -> {
                                        // 课程管理不预加载：beyond 撑满整窗后这一格任何时候
                                        // 都在窗口里，不加 if 就等于常驻。只有它自己是当前页
                                        // 才真正组合，其余时刻渲染空 —— 见 VerticalPager 处注释。
                                        if (mainPagerState.currentPage == 3) {
                                            TabletCourseManagePane(
                                                viewModel = viewModel,
                                                settingsViewModel = settingsViewModel,
                                                liquidGlassBackdrop = liquidGlassBackdrop,
                                            )
                                        }
                                    }
                                    4 -> {
                                        // 切换课表同上，不预加载
                                        if (mainPagerState.currentPage == 4) {
                                            TabletSwitchSchedulePane(
                                                viewModel = viewModel,
                                                scheduleViewModel = scheduleViewModel,
                                                settingsViewModel = settingsViewModel,
                                                liquidGlassBackdrop = liquidGlassBackdrop,
                                            )
                                        }
                                    }
                                }
                            } else {
                                when (page) {
                                    0 -> ShiftScheduleScreen(
                                        shiftViewModel = shiftViewModel,
                                        settingsViewModel = settingsViewModel,
                                        pagerState = pagerState,
                                        scheduleDataVersion = dataVersion,
                                        cardHeightPerSection = currentAppearance().cardHeight,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        scheduleScrollBehavior = scheduleScrollBehavior,
                                    )

                                    1 -> SettingsScreen(
                                        viewModel = viewModel,
                                        scheduleViewModel = scheduleViewModel,
                                        settingsViewModel = settingsViewModel,
                                        shiftViewModel = shiftViewModel,
                                        isShiftMode = true,
                                        onExitShiftMode = {
                                            com.haooz.chedule.ui.utils.FeatureLog.shift("exit_loading")
                                            showShiftLoading = true
                                            isExitingShift = true
                                        },
                                        onEnterShiftMode = {
                                            showShiftLoading = true
                                            isExitingShift = false
                                        },
                                        navBarStyle = navBarStyle,
                                        onScrollYChanged = { _ -> },
                                        settingsScrollBehavior = settingsScrollBehavior,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                    )
                                }
                            }
                            }
                            if (navBarStyle == "rail") {
                                // 平板与手机对齐：今日/课程表/我的（0~2）三页常驻组合树，
                                // 切页零组合成本。
                                // beyond 撑到覆盖全部页 —— 它是**以当前页为中心的对称窗口**
                                // （PagerBeyondBoundsState：first = firstVisible - beyond、
                                // last = lastVisible + beyond），只有窗口大到能罩住全部页，
                                // 0~2 才能在停在 3/4 页时也不被挤出组合树。
                                // 单靠它表达不了「0~2 常驻 + 3/4 仅当前」，所以
                                // 「不预加载另外两页」由内容侧兜底：mainPagerPageContent
                                // 的 3/4 分支只在自己是当前页时才组合。
                                VerticalPager(
                                    state = mainPagerState,
                                    modifier = mainPagerModifier,
                                    key = mainPagerPageKey,
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = mainPagerPageCount,
                                ) { page -> mainPagerPageContent(page) }
                            } else {
                                // 手机切 tab 是 spring 平移动画。beyond=1 时目标页隔壁那页会在动画
                                // 途中才首次组合（我的/今日各约 23~31ms），直接砸在动画首帧上。
                                // 3 页用 beyond=2 让三页常驻，切页变成纯位移动画，零组合成本。
                                HorizontalPager(
                                    state = mainPagerState,
                                    modifier = mainPagerModifier,
                                    key = mainPagerPageKey,
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = 2,
                                ) { page -> mainPagerPageContent(page) }
                            }
                        }
                    }

                    // 手机端：只要不在当前周/天就常驻显示
                    // Pad（rail）顶栏仍保留原返回按钮，此处不叠加
                    val isPhoneChrome = navBarStyle != "rail"
                    val shouldShowBackToNowFab = isPhoneChrome &&
                        !isShiftMode &&
                        !showDetail &&
                        !showCustomizePage &&
                        !showSwitchSchedule &&
                        !isWindowCutoutActive &&
                        !shortcutMenuVisible &&
                        !floatingCardVisible &&
                        when (selectedTab) {
                            0 -> !todayIsToday
                            1 -> !isViewingCurrentWeek
                            else -> false
                        }
                    val backToNowLabel = "今"
                    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val bottomBarPadding = maxOf(28.dp, navBarBottomInset + 8.dp)
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 对齐切换课表底栏：None 转场 + 手动 appear + graphicsLayer clip=false，
                        // 避免 AnimatedVisibility 收拢尺寸时把阴影裁掉
                        AnimatedVisibility(
                            visible = shouldShowBackToNowFab,
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                // Keep 8.dp gap above ScheduleBottomBar: bottomBarPadding + 56.dp (height) + 8.dp (gap)
                                .padding(bottom = bottomBarPadding + 64.dp)
                                .zIndex(20f)
                        ) {
                            val appear by transition.animateFloat(
                                transitionSpec = {
                                    if (targetState == EnterExitState.Visible) {
                                        tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                    } else {
                                        tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    }
                                },
                                label = "BackToNowAppear"
                            ) { if (it == EnterExitState.Visible) 1f else 0f }

                            BackToNowFloatingButton(
                                backdrop = chromeBackdrop,
                                label = backToNowLabel,
                                modifier = Modifier.graphicsLayer {
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                    scaleX = 0.6f + 0.4f * appear
                                    scaleY = 0.6f + 0.4f * appear
                                    alpha = appear
                                    clip = false
                                },
                                onClick = {
                                    if (selectedTab == 0) {
                                        scrollToTodayTrigger++
                                    } else {
                                        coroutineScope.launch {
                                            val targetPage =
                                                (currentWeek - 1).coerceIn(
                                                    0,
                                                    (totalWeeks - 1).coerceAtLeast(0)
                                                )
                                            pagerState.animateScrollToPage(targetPage)
                                        }
                                    }
                                },
                            )
                        }
                    }

                    ShareImportDialog(
                        activity = activity,
                        shareIntentVersion = activity?.shareIntentVersion ?: 0,
                        courseViewModel = viewModel,
                        scheduleViewModel = scheduleViewModel,
                        settingsViewModel = settingsViewModel,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                    )

                    UpdateDialog(liquidGlassBackdrop = liquidGlassBackdrop)

                    val showJumpWeekDialog by viewModel.showJumpWeekDialog.collectAsState()
                    var jumpWeekTemp by remember { mutableIntStateOf(1) }
                    val hapticFeedback = LocalHapticFeedback.current
                    LaunchedEffect(showJumpWeekDialog) {
                        if (showJumpWeekDialog) {
                            jumpWeekTemp = pagerState.currentPage + 1
                        }
                    }
                    OverlayDialog(
                        title = "跳转周数",
                        show = showJumpWeekDialog,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        onDismissRequest = { viewModel.hideJumpWeekDialog() }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NumberPicker(
                                value = jumpWeekTemp,
                                onValueChange = { jumpWeekTemp = it },
                                range = 1..totalWeeks,
                                visibleItemCount = 3,
                                itemHeight = 60.dp,
                                textStyle = MiuixTheme.textStyles.title2,
                                label = { "第${it}周" },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    text = "取消",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.hideJumpWeekDialog()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    text = "确定",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.hideJumpWeekDialog()
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(jumpWeekTemp - 1)
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    val showAddDialog by viewModel.showAddDialog.collectAsState()
                    val editingCourse by viewModel.editingCourse.collectAsState()
                    val selectedStartSection by viewModel.selectedStartSection.collectAsState()
                    val selectedEndSection by viewModel.selectedEndSection.collectAsState()
                    val editingStartSection =
                        editingCourse?.startSection ?: selectedStartSection
                    val editingEndSection = editingCourse?.endSection ?: selectedEndSection
                    val addDialogSectionTimes by settingsViewModel.sectionTimes.collectAsState()

                    // 始终跟随应用主题，不受壁纸强制主题影响
                    val appDialogDark = rememberAppSettingDark()
                    val appDialogController = remember(appDialogDark) {
                        ThemeController(if (appDialogDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
                    }
                    MiuixTheme(controller = appDialogController) {
                        CompositionLocalProvider(LocalForcedDarkTheme provides null) {
                            val addDialogDefaultWeeks by viewModel.addDialogDefaultWeeks.collectAsState()
                            AddCourseDialog(
                                show = showAddDialog,
                                course = editingCourse,
                                selectedDay = viewModel.selectedDay.collectAsState().value,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                totalWeeks = totalWeeks,
                                totalSections = totalSections,
                                defaultStartSection = editingStartSection,
                                defaultEndSection = editingEndSection,
                                defaultWeeks = addDialogDefaultWeeks,
                                getOccupiedWeeks = { dayOfWeek, startSection, endSection, excludeIds, startTime, endTime ->
                                    viewModel.getOccupiedWeeks(
                                        dayOfWeek = dayOfWeek,
                                        startSection = startSection,
                                        endSection = endSection,
                                        excludeIds = excludeIds.toSet(),
                                        startTime = startTime,
                                        endTime = endTime
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
                                sectionTimes = addDialogSectionTimes
                            )
                        }
                    }
                    DeleteWeekCourseDialog(
                        show = showDeleteConfirmDialog,
                        course = deleteConfirmCourse,
                        week = draggedWeek,
                        viewModel = viewModel,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onDismiss = { showDeleteConfirmDialog = false },
                    )
                    PasteRangeDialog(
                        show = showPasteRangeDialog,
                        courseName = copiedCourseForPaste?.name ?: "",
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onPaste = { allWeeks ->
                            val meta = copiedCourseForPaste
                            val target = pasteRangeTarget
                            if (meta != null && target != null) {
                                // 写库用调课映射后的数据坐标；飞行/几何用列星期
                                val day = target.dataDay
                                val section = target.section
                                val span = (meta.endSection - meta.startSection).coerceAtLeast(0)
                                val endSection = section + span
                                // 「当前周」=该格所属教学周；调课日为 followWeek
                                val pasteWeek = target.dataWeek
                                if (endSection > totalSections) {
                                    android.widget.Toast.makeText(context, "空间不足，无法粘贴", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val conflicts = viewModel.getCoursesAtSlot(pasteWeek, day, section, endSection)
                                        .filter { it.isActiveInWeek(pasteWeek) }
                                    if (conflicts.isNotEmpty()) {
                                        android.widget.Toast.makeText(context, "目标位置有课，无法粘贴", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val pasted = if (allWeeks) {
                                            meta.copy(
                                                id = java.util.UUID.randomUUID().toString(),
                                                dayOfWeek = day,
                                                startSection = section,
                                                endSection = endSection,
                                                scheduleId = "",
                                                isCustomTime = false,
                                                customStartTime = null,
                                                customEndTime = null,
                                                lastModified = System.currentTimeMillis()
                                            )
                                        } else {
                                            meta.copy(
                                                id = java.util.UUID.randomUUID().toString(),
                                                dayOfWeek = day,
                                                startSection = section,
                                                endSection = endSection,
                                                startWeek = pasteWeek,
                                                endWeek = pasteWeek,
                                                weekType = Course.WEEK_TYPE_ALL,
                                                selectedWeeks = emptyList(),
                                                scheduleId = "",
                                                isCustomTime = false,
                                                customStartTime = null,
                                                customEndTime = null,
                                                lastModified = System.currentTimeMillis()
                                            )
                                        }
                                        // 落地后再写入课程
                                        playPasteFlightAnimation(
                                            meta,
                                            target.columnDay,
                                            section
                                        ) {
                                            viewModel.addCourse(pasted)
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        }
                                    }
                                }
                            }
                            showPasteRangeDialog = false
                            pasteRangeTarget = null
                        },
                        onDismiss = {
                            showPasteRangeDialog = false
                            pasteRangeTarget = null
                        },
                    )
                    RescheduleConflictDialog(
                        show = showRescheduleConflictDialog,
                        source = draggedCardCourse,
                        target = pendingConflictCourse,
                        // 写库走数据坐标；flyFloatingCardToDropTarget 仍读列坐标算几何
                        dropTarget = pendingDropTarget?.let {
                            dataPositionForColumn(it.first).first to it.second
                        },
                        dropTargetWeek = pendingDropTarget?.let {
                            dataPositionForColumn(it.first).second
                        } ?: draggedWeek,
                        draggedWeek = draggedWeek,
                        viewModel = viewModel,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onCancel = {
                            showRescheduleConflictDialog = false
                            pendingConflictCourse = null
                            flyFloatingCardHome()
                        },
                        onOverwriteResolved = {
                            showRescheduleConflictDialog = false
                            pendingConflictCourse = null
                            val source = draggedCardCourse
                            val target = pendingDropTarget
                            if (source != null && target != null) {
                                val (targetDay, targetWeek) = dataPositionForColumn(target.first)
                                val span = source.endSection - source.startSection
                                val atTarget = viewModel.getCoursesAtSlot(
                                    targetWeek, targetDay, target.second, target.second + span
                                )
                                draggingCourseIds = atTarget
                                    .filter { it.isActiveInWeek(targetWeek) }
                                    .map { it.id }
                                    .toSet()
                            }
                            flyFloatingCardToDropTarget()
                        },
                        onSwapResolved = {
                            showRescheduleConflictDialog = false
                            val conflict = pendingConflictCourse
                            pendingConflictCourse = null
                            val source = draggedCardCourse
                            val target = pendingDropTarget
                            val week = draggedWeek
                            if (source != null && target != null) {
                                val (targetDay, targetWeek) = dataPositionForColumn(target.first)
                                val span = source.endSection - source.startSection
                                // 目标位按目标周次收、源位按 draggedWeek 收（调课日两者可能不同）
                                val atTarget = viewModel.getCoursesAtSlot(
                                    targetWeek, targetDay, target.second, target.second + span
                                ).filter { it.isActiveInWeek(targetWeek) }
                                val atSource = viewModel.getCoursesAtSlot(
                                    week, source.dayOfWeek, source.startSection, source.endSection
                                ).filter { it.isActiveInWeek(week) }
                                draggingCourseIds = (atTarget + atSource)
                                    .map { it.id }
                                    .toSet()
                            }
                            if (conflict != null) {
                                flySwapCards(conflict)
                            } else {
                                dismissFloatingCard()
                            }
                        },
                        onOverwrite = { ids ->
                            animateInCourseIds = ids
                            coroutineScope.launch {
                                delay(350.milliseconds)
                                animateInCourseIds = emptySet()
                            }
                        },
                        onSwap = { ids ->
                            animateInCourseIds = ids
                            coroutineScope.launch {
                                delay(350.milliseconds)
                                animateInCourseIds = emptySet()
                            }
                        },
                    )
                    // 节点常驻组合树、仅 show 控显隐，退出动画才能播完
                    var notice by remember { mutableStateOf<com.haooz.chedule.data.Notice?>(null) }
                    LaunchedEffect(privacyConsented) {
                        // 合规：未同意隐私政策前不拉取公告（不触网）
                        if (!privacyConsented) return@LaunchedEffect
                        val n = com.haooz.chedule.data.NoticeFetcher.fetch()
                        if (n != null && com.haooz.chedule.data.NoticeFetcher.shouldShow(n)) {
                            notice = n
                        }
                    }
                    OverlayDialog(
                        title = notice?.title,
                        summary = notice?.content?.ifBlank { null },
                        show = notice != null,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        onDismissRequest = { notice = null },
                        onDismissFinished = { notice = null }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            notice?.let { n ->
                                TextButton(
                                    text = "完成",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        com.haooz.chedule.data.NoticeFetcher.markSeen(n)
                                        notice = null
                                    }
                                )
                            }
                        }
                    }
                    // 提饱和：与玻璃面板同源，采样主内容 export。
                    // 光晕半径取 min(短边, 250dp) × 0.5。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                if (glowAlpha > 0f) {
                                    drawContext.canvas.saveLayer(
                                        Rect(0f, 0f, size.width, size.height),
                                        Paint()
                                    )
                                    drawContent()
                                    // 半径与 InteractiveHighlight 同式：min(短边, 250dp) × 0.5
                                    val radius = minOf(size.minDimension, 250.dp.toPx()) * 0.5f
                                    drawRect(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color.White,
                                                Color.White,
                                                Color.Transparent,
                                            ),
                                            center = Offset(
                                                draggedCardPosition.x + floatingOffsetX.floatValue,
                                                draggedCardPosition.y + floatingOffsetY.floatValue,
                                            ),
                                            radius = radius,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                    drawContext.canvas.restore()
                                }
                            }
                            .drawBackdrop(
                                backdrop = liquidGlassBackdrop,
                                shape = { RectangleShape },
                                effects = { colorControls(saturation = 1.5f) },
                                highlight = null,
                                shadow = null,
                                downsampleScale = 1f,
                            )
                    )
                }
            }
            // 始终用 MiuixTheme 包裹保持结构恒定；ThemeController 原地切 mode，重建实例会卡一帧
            val effectiveForcedDark = if (captureThemeActive) captureThemeIsDark else forcedDark
            val effectiveDark = effectiveForcedDark ?: appSettingDark
            val pageController = remember {
                ThemeController(if (effectiveDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
            }
            // 组合期同步 mode：SideEffect 在本帧绘制后才执行，切 tab 会闪一帧旧主题
            pageController.colorSchemeMode =
                if (effectiveDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
            MiuixTheme(controller = pageController) {
                CompositionLocalProvider(LocalForcedDarkTheme provides effectiveForcedDark) {
                    scaffoldContent()
                    // 平板：胶囊/侧栏叠层（不占 Scaffold bottomBar，内容可正常滚动）
                    if (navBarStyle == "rail") {
                        val onTabletTabSelected: (Int) -> Unit = { idx ->
                            if (idx != selectedTab) {
                                mainTabProgrammatic = true
                                selectedTab = idx
                                coroutineScope.launch {
                                    try {
                                        if (todayPagerState.isScrollInProgress) todayPagerState.cancelScroll()
                                        if (pagerState.isScrollInProgress) pagerState.cancelScroll()
                                        if (mainPagerState.isScrollInProgress) mainPagerState.cancelScroll()
                                        mainPagerState.scrollToPage(idx)
                                    } finally {
                                        mainTabProgrammatic = false
                                    }
                                }
                            }
                        }
                        com.haooz.chedule.ui.components.LiquidNavigationRail(
                            selectedTab = selectedTab,
                            onTabSelected = onTabletTabSelected,
                            backdrop = chromeBackdrop,
                            isShiftMode = isShiftMode,
                            onSidebarExpandedChange = {
                                com.haooz.chedule.ui.components.TabletNavSideState.expanded = it
                            },
                            showBackToNow = !isShiftMode &&
                                !showDetail &&
                                !showCustomizePage &&
                                !showSwitchSchedule &&
                                !isWindowCutoutActive &&
                                !shortcutMenuVisible &&
                                !floatingCardVisible &&
                                when (selectedTab) {
                                    0 -> !todayIsToday
                                    1 -> !isViewingCurrentWeek
                                    else -> false
                                },
                            onBackToNow = {
                                if (selectedTab == 0) {
                                    scrollToTodayTrigger++
                                } else {
                                    coroutineScope.launch {
                                        val targetPage =
                                            (currentWeek - 1).coerceIn(
                                                0,
                                                (totalWeeks - 1).coerceAtLeast(0)
                                            )
                                        pagerState.animateScrollToPage(targetPage)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize().zIndex(22f),
                        )
                    }
                    // 平板设置：标题与分界线画在顶栏模糊之上，跟设置页切页平移。
                    // 主题锁定 appSettingDark：不随课程表/壁纸锁色瞬间切换。
                    if (navBarStyle == "rail" && !isShiftMode) {
                        val settingsChromePageIndex = 2
                        val settingsChromeTranslation by remember(mainPagerState) {
                            derivedStateOf {
                                val scrollPos = mainPagerState.currentPage +
                                    mainPagerState.currentPageOffsetFraction
                                (settingsChromePageIndex - scrollPos)
                            }
                        }
                        val settingsChromeController = remember {
                            ThemeController(
                                if (appSettingDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
                            )
                        }
                        settingsChromeController.colorSchemeMode =
                            if (appSettingDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
                        MiuixTheme(controller = settingsChromeController) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationY =
                                            settingsChromeTranslation * screenHPx
                                    }
                            ) {
                                com.haooz.chedule.ui.screens.TabletSettingsChromeOverlay()
                            }
                        }
                    }
                }
            }
            // 快照层也要拦截触摸，覆盖 showDetail 已 false 但快照未清除的窗口
            if (mainContentSnapshot != null) {
                // remember 固定包装对象身份：asImageBitmap() 每帧新建会让 Image 的位图纹理
                // 缓存每帧失效，全屏快照每帧重传纹理
                val mainSnapshotImage = remember(mainContentSnapshot) {
                    mainContentSnapshot!!.asImageBitmap()
                }
                Image(
                    bitmap = mainSnapshotImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().consumeAllTouches(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        // 浮层主题跟壁纸/应用设置
        val overlayEffectiveForcedDark = if (captureThemeActive) captureThemeIsDark else forcedDark
        val overlayEffectiveDark = overlayEffectiveForcedDark ?: appSettingDark
        val overlayPageController = remember {
            ThemeController(if (overlayEffectiveDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        }
        // 组合期同步 mode：SideEffect 会晚一帧
        overlayPageController.colorSchemeMode =
            if (overlayEffectiveDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
        // 无壁纸时浮层垫课程表页底色（与网格课程卡同一份，取值见 courseCardSolidBacking）
        val floatSolidBacking = courseCardSolidBacking(
            isDark = isAppDarkTheme(),
            hasWallpaper = wallpaperBitmap != null
        )
        // 拖浮层跟手高光的中心（px，非 state：只在 draw 里写，不触发额外失效）
        val glowCenterPx = remember { FloatArray(2) }
        val dragCardHighlight = remember {
            InteractiveHighlight(
                animationScope = coroutineScope,
                position = { _, _ ->
                    if (isFloatingCardDragging && floatingCardVisible) {
                        glowCenterPx[0] = draggedCardPosition.x + floatingOffsetX.floatValue
                        glowCenterPx[1] = draggedCardPosition.y + floatingOffsetY.floatValue
                    }
                    Offset(glowCenterPx[0], glowCenterPx[1])
                },
                radiusScale = { 1f / 3f },
                radiusBaseDp = 250.dp,
                // 按压进度由 glowAlpha 驱动（150ms 淡变），不走组件自带手势
                pressProgressOverride = { glowAlpha },
                // 只要圆形光晕，不要整幅提亮
                drawFlatOverlay = false,
            )
        }
        MiuixTheme(controller = overlayPageController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides overlayEffectiveForcedDark) {
                // 跟手高光：画在浮层之前（同一父级里先声明先画），压在整页之上、浮层之下
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(dragCardHighlight.modifier)
                )
                if (floatingCardVisible) {
                    val course = draggedCardCourse
                    if (course != null) {
                        // 锚点只用长按瞬间的中心；拖拽/吸附位移走 graphicsLayer，不进组合
                        val widthPx = draggedCardSize.x
                        val geom = gridGeometry
                        val sectionH = geom?.sectionHeightPx
                            ?: with(density) { displayAppearance.cardHeight.dp.toPx() }
                        // 高度含午休/晚修分界缝，与网格上分段卡片的视觉外接框一致
                        val heightPx = when {
                            // 自定义时间课按时间轴定位/定高，节次几何算出来的高度对不上，用长按实测高
                            course.hasValidCustomTime() && !isPasteFlight -> draggedCardSize.y
                            geom != null -> courseVisualHeightPx(
                                geom,
                                course.startSection,
                                course.endSection,
                                dividerPxFor(geom)
                            )
                            else -> (course.endSection - course.startSection + 1) * sectionH
                        }
                        val baseOffsetX = with(density) {
                            (draggedCardPosition.x - widthPx / 2f).toDp()
                        }
                        val baseOffsetY = with(density) {
                            (draggedCardPosition.y - heightPx / 2f).toDp()
                        }
                        val width = with(density) { widthPx.toDp() }
                        val height = with(density) { heightPx.toDp() }
                        LaunchedEffect(floatingCardVisible, isPasteFlight) {
                            // 粘贴飞行自行控制缩放，跳过长按入场
                            if (floatingCardVisible && !isPasteFlight) {
                                floatingScale.snapTo(0.94f)
                                floatingScale.animateTo(1.08f, tween(durationMillis = 120))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .offset(x = baseOffsetX, y = baseOffsetY)
                                .size(width = width, height = height)
                                .padding(vertical = 2.dp)
                                .graphicsLayer {
                                    translationX = floatingOffsetX.floatValue
                                    translationY = floatingOffsetY.floatValue
                                    scaleX = floatingScale.value
                                    scaleY = floatingScale.value
                                }
                        ) {
                            CourseCard(
                                course = course,
                                // 粘贴飞行强制本周样式，避免源课不在当前周飞出灰卡
                                isCurrentWeek = if (isPasteFlight) true else course.isActiveInWeek(draggedWeek),
                                wallpaperBackdrop = if (wallpaperBitmap != null) liquidGlassBackdrop else null,
                                cardBlurRadius = displayAppearance.cardBlurRadius,
                                cardRefraction = displayAppearance.cardRefraction,
                                cardAlpha = displayAppearance.cardAlpha,
                                cardSurfaceAlpha = displayAppearance.cardSurfaceAlpha,
                                // 无壁纸时垫页底色，取值见 floatSolidBacking
                                solidBackingColor = floatSolidBacking,
                                cardHeightPerSection = displayAppearance.cardHeight,
                                // ⚠ 必须把浮层 Box 的高度原样传给卡片，否则长按浮层会偏上。
                                // CourseCard 自身高度 = customCardHeightDp ?: (节次数 × 每节高)，
                                // 而上面 Box 的高度用的是 heightPx（实测/视觉高度）。
                                // 两者不一致时，固定高度的卡片在 Box 里按顶部对齐，
                                // 视觉中心就偏上 (heightPx − 卡片高)/2 ——
                                // 自定义时间课的实测高约为节次高的 2 倍时，
                                // 偏移正好是半个卡片高（表现为“偏上 50%”）。
                                // v1.6.4beta24 引入：那之前 Box 高度恒为节次几何，与卡片一致。
                                customCardHeightDp = with(density) { heightPx.toDp().value },
                                cardCornerRadius = displayAppearance.cardCornerRadius,
                                isTablet = isTablet,
                                cardContentAlignment = displayAppearance.cardContentAlignment,
                                cardTextColor = displayAppearance.cardTextColor,
                                cardTextScale = displayAppearance.cardTextScale,
                                showClassroom = displayAppearance.showClassroom,
                                showTeacher = displayAppearance.showTeacher,
                                disablePadding = true,
                                onClick = {}
                            )
                        }
                    }
                }
                if (swapFlightVisible) {
                    val swapCourse = swapFlightCourse
                    if (swapCourse != null) {
                        val centerX = swapFlightOriginCenter.x + swapFlightOffsetX.value
                        val centerY = swapFlightOriginCenter.y + swapFlightOffsetY.value
                        val widthPx = swapFlightWidth
                        val sectionCount = swapCourse.endSection - swapCourse.startSection + 1
                        val sectionH = gridGeometry?.sectionHeightPx
                            ?: with(density) { displayAppearance.cardHeight.dp.toPx() }
                        val heightPx = sectionCount * sectionH
                        val offsetX = with(density) { (centerX - widthPx / 2f).toDp() }
                        val offsetY = with(density) { (centerY - heightPx / 2f).toDp() }
                        val width = with(density) { widthPx.toDp() }
                        val height = with(density) { heightPx.toDp() }
                        Box(
                            modifier = Modifier
                                .offset(x = offsetX, y = offsetY)
                                .size(width = width, height = height)
                                .padding(vertical = 2.dp)
                                .graphicsLayer {
                                    scaleX = swapFlightScale.value
                                    scaleY = swapFlightScale.value
                                }
                        ) {
                            CourseCard(
                                course = swapCourse,
                                isCurrentWeek = true,
                                wallpaperBackdrop = if (wallpaperBitmap != null) liquidGlassBackdrop else null,
                                cardBlurRadius = displayAppearance.cardBlurRadius,
                                cardRefraction = displayAppearance.cardRefraction,
                                cardAlpha = displayAppearance.cardAlpha,
                                cardSurfaceAlpha = displayAppearance.cardSurfaceAlpha,
                                // 无壁纸时垫页底色，取值见 floatSolidBacking
                                solidBackingColor = floatSolidBacking,
                                cardHeightPerSection = displayAppearance.cardHeight,
                                cardCornerRadius = displayAppearance.cardCornerRadius,
                                isTablet = isTablet,
                                cardContentAlignment = displayAppearance.cardContentAlignment,
                                cardTextColor = displayAppearance.cardTextColor,
                                cardTextScale = displayAppearance.cardTextScale,
                                showClassroom = displayAppearance.showClassroom,
                                showTeacher = displayAppearance.showTeacher,
                                disablePadding = true,
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
        if (shortcutMenuCourse != null || emptyCellMenuTarget != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 退场动画并行，结束后再清空
                        shortcutMenuVisible = false
                        dismissFloatingCard()
                        coroutineScope.launch {
                            delay(220.milliseconds)
                            shortcutMenuCourse = null
                            emptyCellMenuTarget = null
                        }
                    }
            )
        }
        val activeShortcutCourse = shortcutMenuCourse
        val activeEmptyTarget = emptyCellMenuTarget
        if (activeShortcutCourse != null || activeEmptyTarget != null) {
            val menuItems = if (activeShortcutCourse != null) {
                listOf(
                    ShortcutMenuItem(
                        icon = MiuixIcons.Edit,
                        label = "编辑",
                        onClick = {
                            com.haooz.chedule.ui.utils.FeatureLog.course("edit_shortcut")
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
                        icon = MiuixIcons.Copy,
                        label = "复制",
                        onClick = {
                            com.haooz.chedule.ui.utils.FeatureLog.course("copy_shortcut")
                            copiedCourseForPaste = activeShortcutCourse
                            shortcutMenuVisible = false
                            dismissFloatingCard()
                            coroutineScope.launch {
                                delay(240.milliseconds)
                                shortcutMenuCourse = null
                            }
                            android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ),
                    ShortcutMenuItem(
                        icon = MiuixIcons.Delete,
                        label = "删除",
                        onClick = {
                            com.haooz.chedule.ui.utils.FeatureLog.course("delete_shortcut")
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
                )
            } else {
                val emptyDay = activeEmptyTarget?.columnDay ?: -1
                val emptySection = activeEmptyTarget?.section ?: -1
                val emptyAddDay = activeEmptyTarget?.addDay ?: emptyDay
                val emptyAddWeek = activeEmptyTarget?.addWeek ?: -1
                buildList {
                    if (copiedCourseForPaste != null && activeEmptyTarget != null) {
                        add(
                            ShortcutMenuItem(
                                icon = MiuixIcons.Paste,
                                label = "粘贴",
                                onClick = {
                                    pasteRangeTarget = PasteTarget(
                                        columnDay = emptyDay,
                                        dataDay = emptyAddDay,
                                        section = emptySection,
                                        dataWeek = if (emptyAddWeek > 0) emptyAddWeek else currentViewingWeek,
                                    )
                                    showPasteRangeDialog = true
                                    shortcutMenuVisible = false
                                    coroutineScope.launch {
                                        delay(240.milliseconds)
                                        emptyCellMenuTarget = null
                                    }
                                }
                            )
                        )
                    }
                    if (activeEmptyTarget != null) {
                        add(
                            ShortcutMenuItem(
                                icon = MiuixIcons.Add,
                                label = "添加",
                                iconSize = 24.dp,
                                onClick = {
                                    val defaultWeeks =
                                        if (emptyAddWeek > 0) setOf(emptyAddWeek) else emptySet()
                                    viewModel.showAddDialog(emptyAddDay, emptySection, null, defaultWeeks)
                                    shortcutMenuVisible = false
                                    coroutineScope.launch {
                                        delay(240.milliseconds)
                                        emptyCellMenuTarget = null
                                    }
                                }
                            )
                        )
                    }
                }
            }
            ShortcutMenu(
                show = shortcutMenuVisible,
                items = menuItems,
                modifier = Modifier.offset(
                    // ShadowPadding 12dp，左移使可见左缘与卡片对齐
                    x = with(density) { shortcutMenuPosition.x.toDp() - 12.dp },
                    y = with(density) { (shortcutMenuPosition.y - shortcutMenuSize.height).toDp() + 4.dp }
                ),
                backdrop = liquidGlassBackdrop,
                anchorRightPx = shortcutMenuPosition.x + shortcutMenuAnchorWidth,
                onMeasuredSize = { width, height ->
                    shortcutMenuSize = IntSize(width, height)
                },
                onDismiss = {
                    shortcutMenuVisible = false
                    dismissFloatingCard()
                    coroutineScope.launch {
                        delay(220.milliseconds)
                        shortcutMenuCourse = null
                        emptyCellMenuTarget = null
                    }
                }
            )
        }
        if (isEntryAnimating) {
            // alpha 必须走 graphicsLayer：原来这里在组合期读 switchReturnBgScrim.value，
            // 进场动画 350ms 里每帧让根内容作用域失效 → 整棵 MainActivity 内容树
            // （pager + 各页顶栏底栏 + 卡片）重组 20+ 次。视觉等价：黑底 + 层透明度。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black)
                    .graphicsLayer {
                        alpha = if (isDark) switchReturnBgScrim.value
                        else switchReturnBgScrim.value * 0.6f
                    }
            )
        }
        val window = (context as? ComponentActivity)?.window
        val windowInsetsController = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
        }
        if (showCustomizePage && customizeSnapshot != null) {
            LaunchedEffect(true) {
                if (showCustomizePage) {
                    windowInsetsController?.isAppearanceLightStatusBars = false
                    windowInsetsController?.isAppearanceLightNavigationBars = false
                } else {
                    windowInsetsController?.isAppearanceLightStatusBars = true
                    windowInsetsController?.isAppearanceLightNavigationBars = true
                }
            }
            val dismissCustomize: () -> Unit = {
                isApplyingCustomize = false
                // 取消时丢弃未落盘主题预览
                pendingScheduleThemeMode = null
                coroutineScope.launch {
                    blurSnapshotJob?.cancel()
                    // 动画期间保持编辑后实时状态；结束时统一恢复。仅锁定原搭配主题防跟随编辑壁纸
                    captureThemeActive = true
                    captureThemeIsDark = originalWallpaperIsLight?.let { !it }
                    // 复用进入时快照，从开洞放大回全屏盖住回退
                    customizeCoverActive = true
                    customizeCoverScale.stop()
                    customizeCoverScale.snapTo(cutoutMainScale.value)
                    customizeCoverAlpha.stop()
                    customizeCoverAlpha.snapTo(0f)
                    customizeExitScale.stop()
                    customizeExitScale.snapTo(cutoutMainScale.value)
                    customizeExitAlpha.stop()
                    customizeExitAlpha.snapTo(1f)
                    isCustomizeExiting = true
                    windowInsetsController?.isAppearanceLightStatusBars = true
                    windowInsetsController?.isAppearanceLightNavigationBars = true
                }
            }
            val applyCustomize: () -> Unit = {
                coroutineScope.launch {
                    val bitmap = wallpaperBitmap
                    val isLight = combinations.getOrNull(currentCombinationIndex)?.wallpaperIsLight
                    // 仅内存预览用
                    val capturedSnapshot = captureMainContentBitmap()
                    val saveJob = launch(Dispatchers.IO) {
                        // 合并为一次磁盘提交
                        ScheduleAppearance.batchSave {
                            if (bitmap != null) {
                                ScheduleAppearance.saveWallpaper(bitmap)
                            } else {
                                // 删文件，否则旧壁纸会在重启后恢复
                                ScheduleAppearance.clearWallpaper()
                            }
                            ScheduleAppearance.saveState(
                                wallpaperOffset.x,
                                wallpaperOffset.y,
                                wallpaperScale,
                                // 与当前显示尺寸绑定，旋转后可按参考屏重映射
                                latestScreenWPx,
                                latestScreenHPx
                            )
                            val appearanceToSave = currentAppearance()
                            ScheduleAppearance.saveCardBlur(
                                appearanceToSave.cardBlurRadius
                            )
                            ScheduleAppearance.saveCardAlpha(
                                appearanceToSave.cardAlpha
                            )
                            ScheduleAppearance.saveCardSurfaceAlpha(
                                appearanceToSave.cardSurfaceAlpha
                            )
                            ScheduleAppearance.saveCardHeight(
                                appearanceToSave.cardHeight
                            )
                            ScheduleAppearance.saveCardCornerRadius(
                                appearanceToSave.cardCornerRadius
                            )
                            ScheduleAppearance.saveWallpaperBrightness(
                                appearanceToSave.wallpaperBrightness
                            )
                            ScheduleAppearance.saveWallpaperIsLight( isLight)
                            ScheduleAppearance.saveShowBreakDividers(
                                appearanceToSave.showBreakDividers
                            )
                            ScheduleAppearance.saveCardContentAlignment(
                                appearanceToSave.cardContentAlignment
                            )
                            ScheduleAppearance.saveCardTextColor(
                                appearanceToSave.cardTextColor
                            )
                            ScheduleAppearance.saveCardTextScale(
                                appearanceToSave.cardTextScale
                            )
                            ScheduleAppearance.saveShowClassroom(
                                appearanceToSave.showClassroom
                            )
                            ScheduleAppearance.saveShowTeacher(
                                appearanceToSave.showTeacher
                            )
                            ScheduleAppearance.saveCardRefraction(
                                appearanceToSave.cardRefraction
                            )
                            ScheduleAppearance.saveWallpaperBlur(
                                appearanceToSave.wallpaperBlur
                            )
                        }
                        // 「应用」才写入偏好
                        pendingScheduleThemeMode?.let { mode ->
                            ScheduleAppearance.setThemeMode(mode)
                            pendingScheduleThemeMode = null
                        }
                    }
                    // 快照仅存内存；替换前记下旧 snapshot，延迟回收
                    val idx = currentCombinationIndex
                    val replacedSnapshot = combinations.getOrNull(idx)?.snapshot
                    if (idx in combinations.indices) {
                        combinations = combinations.toMutableList().also {
                            it[idx] = it[idx].copy(
                                bitmap = bitmap,
                                offset = wallpaperOffset,
                                scale = wallpaperScale,
                                snapshot = capturedSnapshot,
                                wallpaperIsLight = isLight
                            )
                        }
                    }
                    if (replacedSnapshot !== capturedSnapshot) {
                        launch {
                            delay(32.milliseconds)
                            recycleIndependentBitmap(
                                replacedSnapshot,
                                capturedSnapshot,
                                customizeSnapshot,
                                MainActivity.cachedWallpaperBitmap,
                                wallpaperBitmap,
                            )
                        }
                    }
                    savedWallpaperBitmap = bitmap
                    savedWallpaperOffset = wallpaperOffset
                    savedWallpaperScale = wallpaperScale
                    savedAppearance = currentAppearance()
                    saveJob.join()
                    isApplyingCustomize = true
                    // 从开洞大小（0.75）放大回全屏
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
                    onThemeModePreview = { mode -> pendingScheduleThemeMode = mode },
                    onPickWallpaper = {
                        wallpaperPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onClearWallpaper = {
                        wallpaperBitmap = null
                        wallpaperOffset = Offset.Zero
                        wallpaperScale = 1f
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also { list ->
                                list[idx] = list[idx].copy(
                                    bitmap = null,
                                    offset = Offset.Zero,
                                    scale = 1f,
                                    wallpaperIsLight = null
                                )
                            }
                        }
                    },
                    pendingEnterCutout = pendingEnterCutout,
                    onCutoutEntered = { pendingEnterCutout = false },
                    combinations = combinations,
                    currentCombinationIndex = currentCombinationIndex,
                    isExiting = isCustomizeExiting,
                    isApplying = isApplyingCustomize,
                    isApplyingCustomize = isApplyingCustomize,
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
                    sheetOffsetShared = sheetOffsetY,
                    appearance = currentAppearance(),
                    onAppearanceChange = { newAppearance ->
                        applyAppearance(newAppearance)
                    },
                    hasWallpaper = wallpaperBitmap != null,
                    previewPage = if (selectedTab == 1) 1 else 0,
                    onPreviewPageChange = { page ->
                        val target = if (page == 1) 1 else 0
                        if (selectedTab != target) {
                            mainTabProgrammatic = true
                            selectedTab = target
                            coroutineScope.launch {
                                try {
                                    if (navBarStyle == "rail") {
                                        mainPagerState.scrollToPage(target)
                                    } else {
                                        mainPagerState.animateMainTabTo(target)
                                    }
                                } finally {
                                    mainTabProgrammatic = false
                                }
                            }
                        }
                    },
                )
            }
        }

        if (snapshotCoverBitmap != null) {
            val coverImage = remember(snapshotCoverBitmap) { snapshotCoverBitmap!!.asImageBitmap() }
            Image(
                bitmap = coverImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // 只作用于快照这一层，避免叠加闪烁
        if (customizeCoverActive && customizeSnapshot != null) {
            val customizeImage = remember(customizeSnapshot) { customizeSnapshot!!.asImageBitmap() }
            Image(
                bitmap = customizeImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = customizeCoverAlpha.value
                        scaleX = customizeCoverScale.value
                        scaleY = customizeCoverScale.value
                        transformOrigin = TransformOrigin(0.5f, cutoutCenterYRatio)
                    }
                    .drawWithContent {
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
                    },
                contentScale = ContentScale.Crop
            )
        }

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
                    if (!isApplyingCustomize) {
                        launch {
                            customizeCoverScale.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    500,
                                    easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
                                )
                            )
                        }
                        launch {
                            customizeCoverAlpha.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    400,
                                    easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
                                )
                            )
                        }
                    }
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
                isCustomizeExiting = false
                showCustomizePage = false
                val exitingSnapshot = customizeSnapshot
                val exitingCover = snapshotCoverBitmap
                customizeSnapshot = null
                snapshotCoverBitmap = null
                // combinations 可能仍持有同一 snapshot 引用，共用时不 recycle
                val keptCombSnapshots = combinations.mapNotNull { it.snapshot }.toTypedArray()
                val keptCombBitmaps = combinations.mapNotNull { it.bitmap }.toTypedArray()
                recycleIndependentBitmap(
                    exitingSnapshot,
                    exitingCover,
                    MainActivity.cachedWallpaperBitmap,
                    wallpaperBitmap,
                    originalWallpaperBitmap,
                    savedWallpaperBitmap,
                    *keptCombSnapshots,
                    *keptCombBitmaps,
                )
                recycleIndependentBitmap(
                    exitingCover,
                    exitingSnapshot,
                    MainActivity.cachedWallpaperBitmap,
                    wallpaperBitmap,
                    originalWallpaperBitmap,
                    savedWallpaperBitmap,
                    *keptCombSnapshots,
                    *keptCombBitmaps,
                )
                customizeCoverActive = false
                isWindowCutoutActive = false
                // 原搭配已恢复，forcedDark 自然接管
                captureThemeActive = false
                captureThemeIsDark = null
                if (!isApplyingCustomize) {
                    // 非应用：整体还原，覆盖编辑回调改过的字段
                    wallpaperBitmap = originalWallpaperBitmap
                    wallpaperOffset = originalWallpaperOffset
                    wallpaperScale = originalWallpaperScale
                    currentCombinationIndex = originalCombinationIndex
                    val restoreIdx = originalCombinationIndex
                    val restored = originalCombination
                    if (restoreIdx in combinations.indices && restored != null) {
                        combinations = combinations.toMutableList().also {
                            it[restoreIdx] = restored
                        }
                    }
                }
                isApplyingCustomize = false
                windowInsetsController?.isAppearanceLightStatusBars = true
                windowInsetsController?.isAppearanceLightNavigationBars = true
            }
        }
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
                sectionTimes = sectionTimes,
                classStartTime = classStartTime,
                teachingWeekReorganizations = teachingWeekRules,
                targetWeek = detailTargetWeek,
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
                    // 延迟清快照，让内容先重组完；移出组合后再 recycle 独立副本
                    coroutineScope.launch {
                        delay(16.milliseconds)
                        val oldMain = mainContentSnapshot
                        val oldDetail = detailSnapshot
                        mainContentSnapshot = null
                        detailSnapshot = null
                        recycleIndependentBitmap(
                            oldDetail,
                            oldMain,
                            MainActivity.cachedWallpaperBitmap,
                            wallpaperBitmap,
                            savedWallpaperBitmap,
                            originalWallpaperBitmap,
                        )
                        recycleIndependentBitmap(
                            oldMain,
                            MainActivity.cachedWallpaperBitmap,
                            wallpaperBitmap,
                            savedWallpaperBitmap,
                            originalWallpaperBitmap,
                        )
                    }
                }
            )
        }
        // 切换课表：p 在卡片↔全屏之间形变；进入 p:1→0，退出 p:0→1
        if (showSwitchSchedule) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (switchCapturingSnapshot) 0f else 1f
                    }
            ) {
                SwitchScheduleScreen(
                    onBack = { switchPageBitmap ->
                        if (switchAnimRunning && !switchAnimForward) {
                            // 退出动画已在跑，这次回调没人消费。它带的整屏截图只用来裁卡片，
                            // 直接回收，否则每次快速连点返回都漏一张全屏位图
                            coroutineScope.launch {
                                delay(32.milliseconds)
                                recycleIndependentBitmap(switchPageBitmap, switchCardSnapshot)
                            }
                            return@SwitchScheduleScreen
                        }
                        val wasForward = switchAnimForward
                        switchAnimForward = false
                        switchAnimRunning = true
                        switchAnimJob?.cancel()
                        switchAnimJob = coroutineScope.launch {
                            if (scheduleChanged && !wasForward) {
                                scheduleChanged = false
                                switchReloadJob?.join()
                                switchReloadJob = null
                                mainContentSnapshot = null
                                // withFrameNanos 返回的是上一帧，需多等一帧；只等 1 帧仍有录到旧课表的隐患
                                withFrameNanos { }
                                mainContentSnapshot = try {
                                    captureMainContentBitmap()
                                } catch (_: Exception) {
                                    mainContentSnapshot
                                }
                            } else {
                                scheduleChanged = false
                            }
                            val currentBounds = switchCurrentCardBounds
                            val screenBitmap = switchPageBitmap ?: mainContentSnapshot
                            if (currentBounds != null && screenBitmap != null) {
                                switchOverlayActive = true
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
                                } catch (_: OutOfMemoryError) {
                                    null
                                }
                                val currentProgress = switchAnimProgress.value
                                val remainingDuration =
                                    ((1f - currentProgress) * 560).toInt().coerceAtLeast(1)
                                switchAnimProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = remainingDuration,
                                        easing = OobeQuartOutEasing
                                    )
                                )
                            }
                            showSwitchSchedule = false
                            com.haooz.chedule.ui.utils.CrashLogHelper.trace(
                                "切换课表", "close", "forward=$wasForward"
                            )
                            switchOverlayActive = false
                            val oldSwitchCard = switchCardSnapshot
                            val oldSwitchMain = mainContentSnapshot
                            switchCardSnapshot = null
                            switchCardBounds = null
                            switchCurrentCardBounds = null
                            mainContentSnapshot = null
                            switchAnimRunning = false
                            launch {
                                delay(32.milliseconds)
                                recycleIndependentBitmap(
                                    oldSwitchCard,
                                    oldSwitchMain,
                                    MainActivity.cachedWallpaperBitmap,
                                    wallpaperBitmap,
                                )
                                recycleIndependentBitmap(
                                    oldSwitchMain,
                                    switchPageBitmap,
                                    MainActivity.cachedWallpaperBitmap,
                                    wallpaperBitmap,
                                )
                                // onBack 带回来的整屏截图只用来裁卡片快照；裁完即弃。
                                // 它和 mainContentSnapshot 是两个独立对象时（选中的是另一张
                                // 课表 / 点了当前课表走 dismissKeepCurrent）才会走到这里。
                                recycleIndependentBitmap(
                                    switchPageBitmap,
                                    oldSwitchCard,
                                    oldSwitchMain,
                                    MainActivity.cachedWallpaperBitmap,
                                    wallpaperBitmap,
                                )
                            }
                        }
                    },
                    onScheduleChanged = {
                        com.haooz.chedule.ui.utils.CrashLogHelper.trace("切换课表", "schedule_changed")
                        switchReloadJob = viewModel.reloadCourses()
                        settingsViewModel.refreshSettings()
                        scheduleChanged = true
                        // 切换页直接改 repository，须把 ScheduleViewModel 拉回一致；摘要放 IO
                        coroutineScope.launch(Dispatchers.IO) {
                            scheduleViewModel.refreshScheduleList()
                        }
                    },
                    onCardClick = { bounds ->
                        switchCardBounds = bounds
                    },
                    onCardSnapshot = { fullBitmap, cardBitmap, bounds ->
                        if (switchAnimJob?.isActive == true) {
                            // 退出动画已在跑：卡片快照和它裁自的整屏截图都无人消费。
                            // 互为 keep，裁剪区等于整图时两者同一对象，天然都不回收
                            coroutineScope.launch {
                                delay(32.milliseconds)
                                recycleIndependentBitmap(cardBitmap, fullBitmap)
                                recycleIndependentBitmap(fullBitmap, cardBitmap)
                            }
                            return@SwitchScheduleScreen
                        }
                        switchAnimForward = false
                        switchAnimRunning = true
                        switchAnimJob?.cancel()
                        switchAnimJob = coroutineScope.launch {
                            if (scheduleChanged) {
                                scheduleChanged = false
                                switchReloadJob?.join()
                                switchReloadJob = null
                                mainContentSnapshot = null
                                // withFrameNanos 返回的是上一帧，需多等一帧
                                withFrameNanos { }
                                mainContentSnapshot = try {
                                    captureMainContentBitmap()
                                } catch (_: Exception) {
                                    mainContentSnapshot
                                }
                            }
                            switchOverlayActive = true
                            switchCardSnapshot = cardBitmap
                            switchCardBounds = bounds
                            val currentProgress = switchAnimProgress.value
                            val remainingDuration =
                                ((1f - currentProgress) * 560).toInt().coerceAtLeast(1)
                            switchAnimProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = remainingDuration,
                                    easing = OobeQuartOutEasing
                                )
                            )
                            switchAnimRunning = false
                            showSwitchSchedule = false
                            switchOverlayActive = false
                            switchCardSnapshot = null
                            switchCardBounds = null
                            mainContentSnapshot = null
                            // fullBitmap 是切换页的整屏截图，只用来裁 cardBitmap，裁完即弃；
                            // 每次「选中另一张课表」原先都直接丢弃一张全屏位图。
                            // keep 传 cardBitmap：裁剪区等于整图时两者同一对象，天然不回收
                            launch {
                                delay(32.milliseconds)
                                recycleIndependentBitmap(
                                    fullBitmap,
                                    cardBitmap,
                                    MainActivity.cachedWallpaperBitmap,
                                    wallpaperBitmap,
                                )
                            }
                        }
                    },
                    onCurrentCardBounds = { bounds ->
                        switchCurrentCardBounds = bounds
                    },
                    onScreenReady = { screenBitmap, cardBounds ->
                        if (!switchPendingReverse) {
                            // 锚点 bounds 后续再变会重复回调（见 SwitchScheduleScreen 里的上闩），
                            // 走到这里说明动画早已启动：这张全屏截图没人要，直接回收
                            com.haooz.chedule.ui.utils.CrashLogHelper.trace(
                                "切换课表", "screen_ready_stale", "pending=false"
                            )
                            coroutineScope.launch {
                                delay(32.milliseconds)
                                recycleIndependentBitmap(screenBitmap, switchCardSnapshot)
                            }
                        } else {
                            switchPendingReverse = false
                            switchAnimForward = true
                            switchAnimRunning = true
                            com.haooz.chedule.ui.utils.CrashLogHelper.trace(
                                "切换课表", "anim_enter_start",
                                "bmp=${screenBitmap != null} bounds=$cardBounds"
                            )
                            switchAnimJob?.cancel()
                            val cardBoundsInScreen = androidx.compose.ui.geometry.Rect(
                                left = switchContentRootX + cardBounds.left,
                                top = switchContentRootY + cardBounds.top,
                                right = switchContentRootX + cardBounds.right,
                                bottom = switchContentRootY + cardBounds.bottom
                            )
                            // 截图失败时 cardSnap=null，动画照常跑，不能卡在 alpha=0
                            val cardSnap = if (screenBitmap != null) {
                                try {
                                    val x =
                                        cardBounds.left.toInt().coerceIn(0, screenBitmap.width - 1)
                                    val y =
                                        cardBounds.top.toInt().coerceIn(0, screenBitmap.height - 1)
                                    val w =
                                        cardBounds.width.toInt().coerceIn(1, screenBitmap.width - x)
                                    val h = cardBounds.height.toInt()
                                        .coerceIn(1, screenBitmap.height - y)
                                    android.graphics.Bitmap.createBitmap(screenBitmap, x, y, w, h)
                                } catch (_: Exception) {
                                    null
                                } catch (_: OutOfMemoryError) {
                                    null
                                }
                            } else null
                            // screenBitmap 只用来裁 cardSnap，裁完即弃。原先只等 GC 回收，
                            // 反复进出切换页几次，光这一项就够在低端机上把动画收尾那一帧的
                            // 位图峰值顶爆（manifest 没开 largeHeap）。
                            // 延后两帧再回收：与本文件其余快照位图同一套时序。
                            // keep 传 cardSnap 是必须的：裁剪区恰好等于整图时
                            // createBitmap 会原样返回 screenBitmap，此时它就是 cardSnap 本体。
                            coroutineScope.launch {
                                delay(32.milliseconds)
                                recycleIndependentBitmap(screenBitmap, cardSnap, switchCardSnapshot)
                            }
                            switchAnimJob = coroutineScope.launch {
                                switchAnimProgress.snapTo(1f)
                                switchReturnBgScrim.snapTo(0.4f)
                                switchCapturingSnapshot = false
                                switchOverlayActive = true
                                switchCardBounds = cardBoundsInScreen
                                switchCardSnapshot = cardSnap
                                val remainingDuration = 350
                                val morphExitEase = CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)
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
                                com.haooz.chedule.ui.utils.CrashLogHelper.trace(
                                    "切换课表", "anim_enter_end",
                                    "p=${switchAnimProgress.value}"
                                )
                                switchOverlayActive = false
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
            // p 在卡片矩形↔全屏插值，卡片/主内容快照交叉淡入淡出
            SwitchMorphOverlay(
                active = switchOverlayActive,
                progress = switchAnimProgress,
                cardBounds = switchCardBounds,
                cardSnapshot = switchCardSnapshot,
                mainSnapshot = mainContentSnapshot,
                screenCornerRadius = screenCornerRadius,
                isDark = isDark,
            )
        }

        ShiftLoadingOverlay(
            show = showShiftLoading,
            onShiftReady = {
                if (isExitingShift) {
                    com.haooz.chedule.ui.utils.FeatureLog.shift("exit_confirmed")
                    shiftViewModel.exitShiftMode()
                    selectedTab = 0
                    selectedTab = 0
                    coroutineScope.launch { mainPagerState.scrollToPage(0) }
                } else {
                    com.haooz.chedule.ui.utils.FeatureLog.shift("enter_confirmed")
                    shiftViewModel.enterShiftMode()
                }
            },
            onHide = { showShiftLoading = false },
        )
    }
}

@Composable
private fun SwitchMorphOverlay(
    active: Boolean,
    progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    cardBounds: androidx.compose.ui.geometry.Rect?,
    cardSnapshot: android.graphics.Bitmap?,
    mainSnapshot: android.graphics.Bitmap?,
    screenCornerRadius: Float,
    isDark: Boolean,
) {
    // 门控必须留在本 composable 内：退出的兜底分支会出现
    // showSwitchSchedule && !switchOverlayActive 的窗口，无条件绘制会闪一帧全屏底色
    if (!active) return
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val screenWidth = windowInfo.containerSize.width.toFloat()
    val screenHeight = windowInfo.containerSize.height.toFloat()
    val density = LocalDensity.current
    val p = progress.value

    // asImageBitmap() 每次调用都 new 一个 AndroidImageBitmap 包装对象，而 Image 的位图
    // 纹理缓存是按这个对象身份命中的：写在组合里 = 每帧换一个身份 = 缓存全失效，
    // 全屏的 mainSnapshot 每帧都要重新上传纹理。这里按底层 Bitmap 身份记住包装对象。
    val cardImage = remember(cardSnapshot) { cardSnapshot?.asImageBitmap() }
    val mainImage = remember(mainSnapshot) { mainSnapshot?.asImageBitmap() }

    val sBounds = cardBounds
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) ComposeColor(0xFF2C2C2C).copy(alpha = (p * 0.5f).coerceIn(0f, 0.5f))
                else ComposeColor.Black.copy(alpha = (p * 0.5f).coerceIn(0f, 0.5f))
            )
    )
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
            .clip(ContinuousRoundedRectangle(cRadius))
            .background(MiuixTheme.colorScheme.background)
    ) {
        if (cardImage != null) {
            Image(
                bitmap = cardImage,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(ContinuousRoundedRectangle(20.dp))
                    .graphicsLayer { alpha = (1f - p * 2f).coerceIn(0f, 1f) },
                contentScale = ContentScale.None
            )
        }
        if (mainImage != null) {
            Image(
                bitmap = mainImage,
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

@SuppressLint("AutoboxingStateCreation")
/**
 * 共享顶栏槽里单页顶栏的主题锁。
 *
 * `Scaffold.topBar` 里今日/课程表/我的三根顶栏叠在一起靠 graphicsLayer 平移淡入淡出，
 * 外层统一套的是**跟随当前 tab** 的 chrome 主题（[forcedDark]，含 `selectedTab == 2 -> null`
 * 回落应用主题）。课程表/今日页跟随壁纸锁深浅，于是「课程表深色 → 切到设置页浅色」时
 * `forcedDark` 立刻变 null，尚未移出屏幕的课程表顶栏整体变浅，表现为遮罩瞬间变色。
 *
 * 这里按**本页自己**的主题再锁一层，与 pager 内各页的写法一致。稳态下两者取值相同，
 * 不改变静止时的观感。
 */
@Composable
private fun PageLockedTopBarTheme(
    pageForcedDark: Boolean?,
    content: @Composable () -> Unit,
) {
    val appDark = rememberAppSettingDark()
    val pageDark = pageForcedDark ?: appDark
    val controller = remember {
        ThemeController(if (pageDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
    }
    // 组合期同步 mode：SideEffect 等本帧绘制后才执行，切页会闪一帧旧主题
    controller.colorSchemeMode =
        if (pageDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalForcedDarkTheme provides pageForcedDark) {
            content()
        }
    }
}

@Composable
private fun SettingsTopBar(
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    navBarStyle: String,
    scrollBehavior: SharedScrollBehavior? = null,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
) {
    if (liquidGlassBackdrop == null) return
    val isTablet = navBarStyle == "rail"
    // 仅设置页顶栏锁应用主题，避免切页时渐变/糊层随课程表壁纸锁色跳变
    val settingsBarDark = rememberAppSettingDark()
    val settingsBarController = remember {
        ThemeController(if (settingsBarDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
    }
    settingsBarController.colorSchemeMode =
        if (settingsBarDark) ColorSchemeMode.Dark else ColorSchemeMode.Light

    // 平板设置：渐变画在 TabletSettingsScreen 内容层，这里不再叠全宽遮罩
    MiuixTheme(controller = settingsBarController) {
        ProgressiveBlurTopBar(
            backdrop = liquidGlassBackdrop,
            blurAlpha = if (isTablet) 0f else 1f,
            resampleKey = blurResampleKey,
            sampleTrack = blurSampleTrack,
        ) {
            CollapsibleTopAppBar(
                // 平板设置：顶栏不再显示「我的」，标题由 MainActivity 叠层绘制
                title = if (isTablet) "" else "我的",
                largeTitle = if (isTablet) "" else "我的",
                showLargeTitle = if (isTablet) false else null,
                showSmallTitle = if (isTablet) true else null,
                showGradientOverlay = !isTablet,
                gradientColorOverride = MiuixTheme.colorScheme.surface,
                modifier = Modifier.zIndex(1f),
                scrollBehavior = if (isTablet) null else scrollBehavior,
                startAction = null,
            )
        }
    }
}

@Composable
private fun TodayTopBar(
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    navBarStyle: String,
    currentDayOfWeek: Int,
    isToday: Boolean = true,
    onBackToToday: () -> Unit = {},
    onJumpToDate: () -> Unit = {},
    onEnterCustomize: () -> Unit = {},
    scrollBehavior: SharedScrollBehavior? = null,
    visible: Boolean = true,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
    onMoreSlotTop: (Float) -> Unit = {},
    onMoreMaterial: (Float) -> Unit = {},
    /** 渐变遮罩色；见 [ScheduleTopBar]。今日页同样跟壁纸锁色，需锁住避免切页跳色 */
    gradientColorOverride: Color? = null,
) {
    if (liquidGlassBackdrop == null) return
    val isTabletLiquidGlass = navBarStyle == "rail"
    // 平板：标题避让侧栏后左对齐用的附加修饰符
    val titleRailPadding = if (isTabletLiquidGlass) {
        com.haooz.chedule.ui.components.tabletNavRailStartPadding().padding(start = 12.dp)
    } else {
        Modifier
    }
    val dayOfWeekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val dayOfWeekName = if (currentDayOfWeek in 1..7) dayOfWeekNames[currentDayOfWeek - 1] else ""
    val titleText = if (isToday) "今天是$dayOfWeekName" else dayOfWeekName

    // 隐藏时 alpha=0 但仍测量，保证 currentHeightPx 启动即就位
    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
        modifier = Modifier.graphicsLayer { alpha = if (visible) 1f else 0f },
        resampleKey = blurResampleKey,
        sampleTrack = blurSampleTrack,
    ) {
        CollapsibleTopAppBar(
            title = titleText,
            largeTitle = titleText,
            modifier = Modifier.zIndex(1f),
            scrollBehavior = scrollBehavior,
            // 今日页渐变遮罩保持随滚动显隐
            gradientOverlayScrollTriggered = true,
            gradientColorOverride = gradientColorOverride,
            // 平板：标题避让左侧侧栏后左对齐（手机仍居中）
            titleStartAligned = isTabletLiquidGlass,
            titleModifier = titleRailPadding,
            // 平板左上角不放返回按钮
            startAction = null,
            onAlphaChanged = { backdrop, _ -> onMoreMaterial(backdrop) },
            endAction = { backdropAlpha, shadowAlpha ->
                if (visible) {
                    if (isTabletLiquidGlass) {
                        // pad：课程管理已在侧栏，右上角直接放跳转日期 + 课表外观
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidTopBarButton(
                                onClick = onJumpToDate,
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Basic.FastForward,
                                contentDescription = "跳转日期",
                                iconSize = 23.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                            LiquidTopBarButton(
                                onClick = onEnterCustomize,
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Background,
                                contentDescription = "课表外观",
                                iconSize = 23.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        }
                    } else {
                        // 「更多」按钮由下拉菜单组件自带（收起态即那颗按钮，唯一一份），这里只占位对齐
                        Spacer(
                            modifier = Modifier
                                .size(42.dp)
                                .onGloballyPositioned { onMoreSlotTop(it.positionInRoot().y) }
                        )
                    }
                }
            },
        )
    }
}
