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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.embedding.SplitController
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.ThemeMode
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenu
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenuItem
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.components.BackToNowFloatingButton
import com.haooz.chedule.ui.components.CourseCard
import com.haooz.chedule.ui.components.LandRippleSpec
import com.haooz.chedule.ui.components.LocalLandRipple
import com.haooz.chedule.ui.components.ScheduleBottomBar
import com.haooz.chedule.ui.components.ScheduleTopBar
import com.haooz.chedule.ui.components.ShareImportDialog
import com.haooz.chedule.ui.components.UpdateDialog
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.screens.CourseDetailScreen
import com.haooz.chedule.ui.screens.CustomizeScheduleScreen
import com.haooz.chedule.ui.screens.MainScheduleScreen
import com.haooz.chedule.ui.screens.ScheduleGridGeometry
import com.haooz.chedule.ui.screens.SettingsScreen
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TodayScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.SecondaryPushParallax
import com.haooz.chedule.ui.utils.applyNavigationBarIsDark
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.consumeAllTouches
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.openSecondaryPage
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.haooz.chedule.ui.utils.rememberScheduleThemeMode
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
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
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.time.LocalDate
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

// 按 0.25px 量化缓存 RenderEffect，避免 graphicsLayer 每帧 new 造成 GC 抖动
private class BlurEffectCache {
    private var cachedPx = Float.NaN
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null

    fun get(px: Float): androidx.compose.ui.graphics.RenderEffect {
        val quantized = (px * 4f).toInt().toFloat() / 4f
        if (cachedPx != quantized || cached == null) {
            cachedPx = quantized
            cached = android.graphics.RenderEffect.createBlurEffect(
                quantized, quantized, android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
        return cached!!
    }
}

class MainActivity : ComponentActivity() {

    companion object {
        // 跨 Activity 重建复用，避免每次启动重新解码壁纸
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

        // 默认不启用：只有"退出即隐藏后台"开启时才启用（见 syncBackCallback），
        // 其余情况交回系统默认返回，保证预测性返回动画可用
        backCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        }.also { onBackPressedDispatcher.addCallback(this, it) }

        applyHideFromRecents(
            getSharedPreferences("app_preferences", MODE_PRIVATE)
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

        com.haooz.chedule.data.StatsReporter.init(this)
        com.haooz.chedule.data.StatsReporter.reportActive(this)
        com.haooz.chedule.data.StatsReporter.reportInstallOnce(this)

        // 异步预加载搭配壁纸，Compose 侧已处理 cachedWallpaperBitmap=null
        if (cachedWallpaperBitmap == null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = com.haooz.chedule.data.CourseRepository(this@MainActivity)
                    repo.migrateToCombinationsIfNeeded()
                    val ids = repo.getCombinationIds()
                    val currentId = repo.getCurrentCombinationId()
                    val idx = ids.indexOf(currentId).coerceAtLeast(0)
                    // 单搭配：只缓存当前搭配
                    cachedCombinationIds = if (ids.isEmpty()) emptyList() else listOf(currentId)
                    cachedCurrentCombinationIndex = 0
                    if (ids.isNotEmpty()) {
                        val currentIdValue = ids[idx]
                        cachedWallpaperBitmap = repo.loadCombinationWallpaper(currentIdValue)
                        val storedOffset = Offset(
                            repo.getCombinationOffsetX(currentIdValue),
                            repo.getCombinationOffsetY(currentIdValue)
                        )
                        val storedScale = repo.getCombinationScale(currentIdValue)
                        val refW = repo.getCombinationOffsetRefW(currentIdValue)
                        val refH = repo.getCombinationOffsetRefH(currentIdValue)
                        val metrics = this@MainActivity.resources.displayMetrics
                        val curW = metrics.widthPixels.toFloat()
                        val curH = metrics.heightPixels.toFloat()
                        if (refW > 0f && refH > 0f && (refW != curW || refH != curH)) {
                            val mapped = remapWallpaperForScreen(
                                storedOffset, storedScale, cachedWallpaperBitmap,
                                refW, refH, curW, curH
                            )
                            cachedWallpaperOffset = mapped.first
                            cachedWallpaperScale = mapped.second
                        } else {
                            cachedWallpaperOffset = storedOffset
                            cachedWallpaperScale = storedScale
                        }
                        cachedAppearance = com.haooz.chedule.data.AppearanceConfig(
                            cardBlurRadius = repo.getCombinationCardBlur(currentIdValue),
                            cardAlpha = repo.getCombinationCardAlpha(currentIdValue),
                            cardSurfaceAlpha = repo.getCombinationCardSurfaceAlpha(currentIdValue),
                            cardHeight = repo.getCombinationCardHeight(currentIdValue),
                            cardCornerRadius = repo.getCombinationCardCornerRadius(currentIdValue),
                            wallpaperBrightness = repo.getCombinationWallpaperBrightness(
                                currentIdValue
                            ),
                            showBreakDividers = repo.getCombinationShowBreakDividers(currentIdValue),
                            cardContentAlignment = repo.getCombinationCardContentAlignment(
                                currentIdValue
                            ),
                            cardTextColor = repo.getCombinationCardTextColor(currentIdValue),
                            cardTextScale = repo.getCombinationCardTextScale(currentIdValue),
                            showClassroom = repo.getCombinationShowClassroom(currentIdValue),
                            showTeacher = repo.getCombinationShowTeacher(currentIdValue),
                            cardRefraction = repo.getCombinationCardRefraction(currentIdValue),
                            wallpaperBlur = repo.getCombinationWallpaperBlur(currentIdValue)
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }

        setContent {
            CourseScheduleTheme {
                CourseScheduleApp()
            }
        }
        // 二级页 push 视差：直接平移本页 decorView（本页 onPause 时 Compose 不会刷新）
        SecondaryPushParallax.attachMainRoot(this)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        isInFreeformWindow = isInMultiWindowMode
        // 小窗仍要推主页；仅分栏/Embedding 锁定不左移
        SecondaryPushParallax.updateMainPushPolicy(this)
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        SecondaryPushParallax.attachMainRoot(this)
        SecondaryPushParallax.updateMainPushPolicy(this)
        // 同步返回回调：仅"退出即隐藏后台"开启时需要自定义回调（moveTaskToBack），
        // 其余情况交回系统默认返回（finish），保留 Android 14+ 的预测性返回动画
        syncBackCallback()
    }

    // 仅"退出即隐藏后台"开启时才启用自定义返回回调，
    // 让系统把手势识别为默认返回并播放"返回桌面"动画
    private fun syncBackCallback() {
        val hideBackground = getSharedPreferences("app_preferences", MODE_PRIVATE)
            .getBoolean("hide_background", false)
        backCallback?.isEnabled = hideBackground
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        handleReminderSettingsIntent(intent)
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
            openSecondaryPage(Intent(this, CourseReminderActivity::class.java))
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

    // 2) ImageDecoder（HEIC/部分云图更稳）
    try {
        val decoded = android.graphics.ImageDecoder.decodeBitmap(
            android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            val sample = sampleSizeFromBounds(info.size.width, info.size.height)
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
        downscaleIfNeeded(decoded)?.let { return it }
    } catch (_: Exception) { }

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
    dropTarget: Pair<Int, Int>?,
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
                        viewModel.overwriteCourseForWeek(
                            source.id, draggedWeek, dropTarget.first, dropTarget.second, targetEnd
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
                        viewModel.swapCoursesForWeek(source.id, target.id, draggedWeek)
                        val sourceCourses = viewModel.getCoursesAtSlot(
                            draggedWeek, source.dayOfWeek, source.startSection, source.endSection
                        ).filter {
                            it.id != source.id && it.id != target.id && !it.isActiveInWeek(
                                draggedWeek
                            )
                        }
                        val conflictCourses = viewModel.getCoursesAtSlot(
                            draggedWeek, target.dayOfWeek, target.startSection, target.endSection
                        ).filter {
                            it.id != target.id && it.id != source.id && !it.isActiveInWeek(
                                draggedWeek
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
    showTodayMorePopup: Boolean,
    onTodayMorePopupDismiss: () -> Unit,
    morePopupFraction: Animatable<Float, *>,
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
) {
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
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
            .padding(
                top = if (statusBarHeight > 0.dp) statusBarHeight - 20.dp else 17.dp,
            )
            .offset(x = 9.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        LiquidGlassDropdownMenu(
            show = showMorePopup,
            backdrop = liquidGlassBackdrop,
            fraction = morePopupFraction,
            onDismiss = onMorePopupDismiss,
            onBackProgress = onMoreBackProgress,
            onBackCancelled = onMoreBackCancelled,
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
        LiquidGlassDropdownMenu(
            show = showTodayMorePopup,
            backdrop = liquidGlassBackdrop,
            onDismiss = onTodayMorePopupDismiss,
            onBackProgress = onTodayMoreBackProgress,
            onBackCancelled = onTodayMoreBackCancelled,
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
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight", "UseOfNonLambdaOffsetOverload", "UseKtx")
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
    val settingsScrollBehavior = rememberSharedScrollBehavior()
    val todayScrollBehavior = rememberSharedScrollBehavior()
    val scheduleScrollBehavior = rememberSharedScrollBehavior()

    LaunchedEffect(Unit) {
        val syncManager = com.haooz.chedule.data.SyncManager.getInstance(context)
        val repository = com.haooz.chedule.data.CourseRepository(context)
        val webDavManager = com.haooz.chedule.data.WebDavManager(context)
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
    val totalSections = morningSections + afternoonSections + eveningSections
    val activity = LocalActivity.current as? MainActivity
    val resumeCount = activity?.resumeCount ?: 0
    // 只在「返回」时刷新；冷启动首次 onResume 时 ViewModel 刚加载完，再全量刷会拖慢首屏
    LaunchedEffect(resumeCount) {
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
            com.haooz.chedule.data.HolidayManager.getVersion(context)
        )
    }
    LaunchedEffect(resumeCount) {
        val holidayV = com.haooz.chedule.data.HolidayManager.getVersion(context)
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
    val railState = if (navBarStyle == "rail") rememberNavigationRailState() else null
    val railPaddingStart by animateDpAsState(
        targetValue = if (navBarStyle == "rail") {
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
    // 入场 0.94→1.04，退场 1.04→1.0
    val floatingScale = remember { Animatable(0.94f) }
    // 吸附期间用 floatingOffsetAnim 替代 draggedCardOffset
    var isSnapping by remember { mutableStateOf(false) }
    // 浮层平移：拖拽回调非挂起，用 mutableFloatStateOf；只在 graphicsLayer 读，不触发组合
    val floatingOffsetX = remember { mutableFloatStateOf(0f) }
    val floatingOffsetY = remember { mutableFloatStateOf(0f) }

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
    var pasteRangeTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
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
    val wallpaperRepository = remember { com.haooz.chedule.data.CourseRepository(context) }
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
        wallpaperRepository.getCombinationWallpaperIsLight(wallpaperRepository.getCurrentCombinationId())
    }
    // 搭配未加载（首帧）才退回 initial，避免无壁纸搭配误用初始测光
    val combIsLight = if (currentComb == null) initialCombWallpaperIsLight else currentCombIsLight
    val todayShowWallpaper = settingsViewModel.todayShowWallpaper.collectAsState().value
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
    val todayPageForcedDark = if (todayShowWallpaper) wallpaperForcedDark else null
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
    fun buildCombination(id: Long, bitmap: android.graphics.Bitmap?) =
        com.haooz.chedule.data.Combination(
            id = id,
            bitmap = bitmap,
            offset = Offset(
                wallpaperRepository.getCombinationOffsetX(id),
                wallpaperRepository.getCombinationOffsetY(id)
            ),
            scale = wallpaperRepository.getCombinationScale(id),
            snapshot = null,
            cardBlurRadius = wallpaperRepository.getCombinationCardBlur(id),
            cardAlpha = wallpaperRepository.getCombinationCardAlpha(id),
            cardSurfaceAlpha = wallpaperRepository.getCombinationCardSurfaceAlpha(id),
            cardHeight = wallpaperRepository.getCombinationCardHeight(id),
            cardCornerRadius = wallpaperRepository.getCombinationCardCornerRadius(id),
            wallpaperBrightness = wallpaperRepository.getCombinationWallpaperBrightness(id),
            showBreakDividers = wallpaperRepository.getCombinationShowBreakDividers(id),
            cardContentAlignment = wallpaperRepository.getCombinationCardContentAlignment(id),
            cardTextColor = wallpaperRepository.getCombinationCardTextColor(id),
            cardTextScale = wallpaperRepository.getCombinationCardTextScale(id),
            showClassroom = wallpaperRepository.getCombinationShowClassroom(id),
            showTeacher = wallpaperRepository.getCombinationShowTeacher(id),
            cardRefraction = wallpaperRepository.getCombinationCardRefraction(id),
            wallpaperIsLight = wallpaperRepository.getCombinationWallpaperIsLight(id),
            wallpaperBlur = wallpaperRepository.getCombinationWallpaperBlur(id)
        )

    // 迁移旧数据并加载搭配；有伴生缓存则跳过 IO
    LaunchedEffect(Unit) {
        val cached = MainActivity.cachedWallpaperBitmap
        val cachedIds = MainActivity.cachedCombinationIds
        val cachedIdx = MainActivity.cachedCurrentCombinationIndex

        val currentIndex: Int

        if (cached != null && cachedIds.isNotEmpty()) {
            currentIndex = cachedIdx
            val list = cachedIds.mapIndexed { index, id ->
                buildCombination(id, if (index == currentIndex) cached else null)
            }
            // 单搭配：裁剪须在赋值前完成，先赋全量再裁会多触发一轮全量重组
            val cachedCombOnly = list.getOrNull(currentIndex)
            combinations = if (cachedCombOnly != null) listOf(cachedCombOnly) else emptyList()
            currentCombinationIndex = 0
        } else {
            val phase1 = withContext(Dispatchers.IO) {
                wallpaperRepository.migrateToCombinationsIfNeeded()
                val loadedIds = wallpaperRepository.getCombinationIds()
                val currentId = wallpaperRepository.getCurrentCombinationId()
                val loadedIndex = loadedIds.indexOf(currentId).coerceAtLeast(0)
                val list = loadedIds.mapIndexed { index, id ->
                    buildCombination(
                        id,
                        if (index == loadedIndex) wallpaperRepository.loadCombinationWallpaper(id) else null
                    )
                }
                Pair(list, loadedIndex)
            }
            currentIndex = phase1.second
            val currentCombOnly = phase1.first.getOrNull(currentIndex)
            combinations = if (currentCombOnly != null) listOf(currentCombOnly) else emptyList()
            currentCombinationIndex = 0
            MainActivity.cachedCombinationIds =
                if (currentCombOnly != null) listOf(currentCombOnly.id) else emptyList()
            MainActivity.cachedCurrentCombinationIndex = 0
        }

        val curr = combinations.getOrNull(0)
        if (curr != null) {
            val curW = latestScreenWPx
            val curH = latestScreenHPx
            val refW = wallpaperRepository.getCombinationOffsetRefW(curr.id)
            val refH = wallpaperRepository.getCombinationOffsetRefH(curr.id)
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
    val captureMainContentBitmap: suspend () -> android.graphics.Bitmap = {
        mainSnapshotToken++
        // 等两帧：draw 录制完成 + 帧已提交
        withFrameNanos { }
        withFrameNanos { }
        screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
    }
    var blurSnapshotJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val hapticFeedback = LocalHapticFeedback.current

    val calendar = Calendar.getInstance()
    val currentDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    // 节假日/调休保存后需能重算跳周；resume 时刷新版本号
    val holidayVersion = remember(resumeCount, context) {
        com.haooz.chedule.data.HolidayManager.getVersion(context)
    }

    val basePage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
    val autoAdvancePage = remember(currentWeek, totalWeeks, currentDayOfWeek, smartWeekend, holidayVersion) {
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

    // 主 tab 平移容器：今日/课程表/设置（排班模式为排班/设置），仅点底栏 tab 驱动
    val mainPagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, 2),
        pageCount = { if (isShiftMode) 2 else 3 }
    )
    // 程序化切 tab 期间为 true，避免 currentPage 在动画中途把 selectedTab 拉回去
    var mainTabProgrammatic by remember { mutableStateOf(false) }

    LaunchedEffect(isShiftMode) {
        if (shiftModeInitialized) {
            selectedTab = if (isShiftMode) 0 else if (defaultHomepage == "今日") 0 else 1
            mainPagerState.scrollToPage(selectedTab)
        }
        shiftModeInitialized = true
    }

    LaunchedEffect(currentWeek, totalWeeks, smartWeekend, holidayVersion) {
        val base = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
        val target = if (settingsViewModel.shouldAdvanceToNextWeek(currentDayOfWeek, currentWeek)) {
            (base + 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
        } else base
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    var todaySelectedDayOfWeek by remember { mutableIntStateOf(currentDayOfWeek) }
    var todayIsToday by remember { mutableStateOf(true) }
    var scrollToTodayTrigger by remember { mutableIntStateOf(0) }

    val currentViewingWeek = pagerState.currentPage + 1
    val courses by viewModel.courses.collectAsState()
    val dataVersion by viewModel.dataVersion.collectAsState()
    // dataVersion + courses 引用都进 key：调课 size 可能不变，只靠 size 会让智能周末星期行停在旧值
    val dayRange = remember(currentViewingWeek, smartWeekend, courses, dataVersion) {
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
                if (source == NestedScrollSource.UserInput && abs(available.x) > 1f) {
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
        val sourceCenter = computeTargetCenter(source.dayOfWeek, source.startSection, span)
        val targetCenter = computeTargetCenter(targetDay, targetSection, span)
        val sourceBounds = gridGeometry?.dayBounds?.get(source.dayOfWeek)
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
        coroutineScope.launch {
            isSnapping = true
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
            val occupiedCenter = computeTargetCenter(
                conflictCourse.dayOfWeek,
                conflictCourse.startSection,
                occupiedSpan
            )
            if (targetCenter == null || occupiedCenter == null) {
                clearSwapFlight()
                dismissFloatingCard()
            } else {
                coroutineScope.launch {
                    val bounds = gridGeometry?.dayBounds?.get(conflictCourse.dayOfWeek)
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
        val day = geom.dayBounds.entries.firstOrNull { (_, bounds) ->
            bounds.size >= 2 && centerX >= bounds[0] && centerX <= bounds[1]
        }?.key ?: return null
        val topY = geom.dayBounds[day]?.getOrNull(2) ?: return null
        val relY = firstSectionCenterY - topY
        if (relY < 0f) return null
        val sectionH = geom.sectionHeightPx
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
            detailSnapshot = try {
                val x = cardLeft.toInt().coerceIn(0, fullSnapshot.width - 1)
                val y = cardTop.toInt().coerceIn(0, fullSnapshot.height - 1)
                val w = cardWidth.toInt().coerceIn(1, fullSnapshot.width - x)
                val h = cardHeight.toInt().coerceIn(1, fullSnapshot.height - y)
                android.graphics.Bitmap.createBitmap(fullSnapshot, x, y, w, h)
            } catch (_: Exception) {
                null
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
        coroutineScope.launch {
            val screenW = windowInfo.containerSize.width.toFloat()
            customizeExitTargetScale = (screenW * 0.65f) / screenW
            val oldCombSnapshots = combinations.mapNotNull { it.snapshot }
            combinations = combinations.map { it.copy(snapshot = null) }
            delay(50.milliseconds)
            // toImageBitmap 硬件位图直接画回会与背景模糊形成 RenderNode 自引用导致栈溢出，
            // 必须复制为独立 ARGB_8888 切断引用；~10MB 拷贝挪到 IO
            val captured = captureMainContentBitmap()
            val currentSnapshot = withContext(Dispatchers.IO) {
                captured.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: captured
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
    val morePopupFraction = remember { Animatable(0f) }
    // 顶栏"更多"按钮的移开/归位进度，由更多菜单的预测性返回手势驱动（shared 给顶栏与菜单）
    val scheduleMoreButtonFraction = remember { Animatable(0f) }
    val todayMoreButtonFraction = remember { Animatable(0f) }
    var todayJumpToDateTrigger by remember { mutableIntStateOf(0) }

    val isViewingCurrentWeek = currentViewingWeek == currentWeek

    // 分屏分割线画在最外层，避免被顶部模糊层遮挡（本页为分屏左侧）
    val splitDividerColor = if (isDark) Color(0xFF222222) else Color(0xFFEEEEEE)
    val isInSplit by produceState(initialValue = false) {
        val act = activity ?: return@produceState
        SplitController.getInstance(context).splitInfoList(act).collect { list ->
            value = list.isNotEmpty()
        }
    }
    // 已打开的二级/三级 Activity 类名集合：设置项压暗与整条导航栈联动
    // （打开三级时，绑定的二级选项仍保持压暗，而不是只亮最顶层）
    val activeSecondaryActivities by produceState(initialValue = emptySet<String>()) {
        val app = context.applicationContext as? android.app.Application ?: return@produceState
        val mainActivityClass = activity?.javaClass
        val openNames = LinkedHashSet<String>()
        val callback = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: android.app.Activity) {
                if (mainActivityClass != null && a.javaClass == mainActivityClass) return
                openNames.add(a.javaClass.simpleName)
                value = openNames.toSet()
            }

            override fun onActivityDestroyed(a: android.app.Activity) {
                if (mainActivityClass != null && a.javaClass == mainActivityClass) return
                if (openNames.remove(a.javaClass.simpleName)) {
                    value = openNames.toSet()
                }
            }

            override fun onActivityCreated(a: android.app.Activity, b: Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {
                if (mainActivityClass != null && a.javaClass == mainActivityClass) return
                if (openNames.add(a.javaClass.simpleName)) {
                    value = openNames.toSet()
                }
            }

            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: Bundle) {}
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
        val liquidGlassRecordKey = remember(
            mainPagerState.currentPage, isShiftMode, showDetail, showCustomizePage, showSwitchSchedule,
            isWindowCutoutActive, shortcutMenuVisible, isDraggingCard, floatingCardVisible,
            dataVersion, currentWeek, totalWeeks, currentCombinationIndex, effectiveIsDark,
            wallpaperBitmap, railState?.isExpanded, scheduleShowCourseDetail.value
        ) {
            listOf(
                mainPagerState.currentPage, isShiftMode, showDetail, showCustomizePage, showSwitchSchedule,
                isWindowCutoutActive, shortcutMenuVisible, isDraggingCard, floatingCardVisible,
                dataVersion, currentWeek, totalWeeks, currentCombinationIndex, effectiveIsDark,
                wallpaperBitmap, railState?.isExpanded == true, scheduleShowCourseDetail.value
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
        val latestSwitchAnimRunning by rememberUpdatedState(switchAnimRunning)
        val latestSwitchAnimForward by rememberUpdatedState(switchAnimForward)
        val latestShowSwitch by rememberUpdatedState(showSwitchSchedule)
        val latestDraggingCard by rememberUpdatedState(isDraggingCard)
        val latestRailPad by rememberUpdatedState(railPaddingStart)
        // 课表/今日/设置滚动时主内容像素在变，必须重录，否则顶栏/底栏玻璃冻结
        val liquidGlassMustRecord = remember(scheduleScrollState, todayListScrollInProgress, pagerState, todayPagerState, mainPagerState) {
            var lastRailPad = Float.NaN
            {
                val railPad = latestRailPad.value
                val railMoving = railPad != lastRailPad
                lastRailPad = railPad
                scheduleScrollState.isScrollInProgress ||
                    todayListScrollInProgress.value ||
                    pagerState.isScrollInProgress ||
                    todayPagerState.isScrollInProgress ||
                    mainPagerState.isScrollInProgress ||
                    railMoving ||
                    // 开洞编辑时主内容持续缩放，绝不能停录
                    latestIsWindowCutout ||
                    (latestShowCustomize && latestIsCustomizeExiting) ||
                    latestShowSwitch && latestSwitchAnimForward && latestSwitchAnimRunning ||
                    latestSwitchAnimRunning ||
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 半径为 0 时不必挂 blur：Modifier.blur 会为整棵主内容树额外建一个
                // RenderEffect 图层，0 半径也照常走一遍离屏合成。
                .then(mainContentBlurModifier)
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
                Scaffold(
                    bottomBar = {
                        ScheduleBottomBar(
                            navBarStyle = navBarStyle,
                            isShiftMode = isShiftMode,
                            selectedTab = selectedTab,
                            onTabSelected = { idx ->
                                if (idx != selectedTab) {
                                    // 先锁 programmatic，再改 selectedTab，避免动画中途被拉回
                                    mainTabProgrammatic = true
                                    selectedTab = idx
                                    coroutineScope.launch {
                                        try {
                                            // 内层切天/周若还在惯性，先停掉；未滚动则不空跑
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
                            liquidGlassBackdrop = chromeBackdrop
                        )
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            val page = mainPagerState.currentPage
                                            val off = mainPagerState.currentPageOffsetFraction
                                            translationX =
                                                (scheduleTitleIndex - page - off) * size.width
                                        }
                                ) {
                                    ScheduleTopBar(
                                        visible = true,
                                        navBarStyle = navBarStyle,
                                        pagerCurrentPage = pagerState.currentPage,
                                        currentWeek = currentWeek,
                                        isHoliday = viewingIsHoliday,
                                        isViewingCurrentWeek = isViewingCurrentWeek,
                                        dayRange = dayRange,
                                        currentDayOfWeek = currentDayOfWeek,
                                        isCurrentWeek = pagerState.currentPage + 1 == currentWeek && currentWeek in 1..totalWeeks,
                                        weekDates = weekDates,
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
                                                coroutineScope.launch {
                                                    // 切换页组合与快照截取并行，避免串行等待导致界面无响应
                                                    switchPendingReverse = true
                                                    switchCapturingSnapshot = true
                                                    showSwitchSchedule = true
                                                    mainContentSnapshot = captureMainContentBitmap()
                                                }
                                            }
                                        },
                                        onMoreClick = { showMorePopup = true },
                                        isTablet = isTablet,
                                        isShiftMode = isShiftMode,
                                        liquidGlassBackdrop = chromeBackdrop,
                                        scrollBehavior = scheduleScrollBehavior,
                                        showMorePopup = showMorePopup,
                                        buttonFractionParam = scheduleMoreButtonFraction,
                                    )
                                }
                            }
                            // 设置页顶栏在 Activity 层级渲染，避免 drawPlainBackdrop native crash
                            if (showSettingsTitle) {
                                val settingsTitleIndex = if (isShiftMode) 1 else 2
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            val page = mainPagerState.currentPage
                                            val off = mainPagerState.currentPageOffsetFraction
                                            translationX =
                                                (settingsTitleIndex - page - off) * size.width
                                        }
                                ) {
                                    SettingsTopBar(
                                        liquidGlassBackdrop = chromeBackdrop,
                                        navBarStyle = navBarStyle,
                                        scrollBehavior = settingsScrollBehavior,
                                    )
                                }
                            }
                            // 始终渲染但 alpha=0，保证 currentHeightPx 启动即就位，切页不慢一帧
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val page = mainPagerState.currentPage
                                        val off = mainPagerState.currentPageOffsetFraction
                                        translationX = (0 - page - off) * size.width
                                        alpha = if (showTodayTitle) 1f else 0f
                                    }
                            ) {
                                TodayTopBar(
                                    liquidGlassBackdrop = chromeBackdrop,
                                    navBarStyle = navBarStyle,
                                    currentDayOfWeek = todaySelectedDayOfWeek,
                                    isToday = todayIsToday,
                                    onBackToToday = { scrollToTodayTrigger++ },
                                    onMoreClick = { showTodayMorePopup = true },
                                    scrollBehavior = todayScrollBehavior,
                                    showMorePopup = showTodayMorePopup,
                                    visible = showTodayTitle,
                                    buttonFractionParam = todayMoreButtonFraction,
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    // 详情动画期间用快照占位；占位须吃满触摸，避免事件穿到卡片
                    if (showDetail && mainContentSnapshot != null) {
                        Box(modifier = Modifier.fillMaxSize().consumeAllTouches())
                        return@Scaffold
                    }
                    // 不门控 combinations.isEmpty()：网格只依赖 viewModel，与壁纸加载解耦
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = railPaddingStart)
                        ) {
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
                            // 共享壁纸层：画在主 pager 之下。
                            // 今日↔课程表：壁纸钉死不动；课程表→设置：壁纸随页向左平移。
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
                                todayShowWallpaper,
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
                                        !(page == 0 && off < 0.01f && !todayShowWallpaper) &&
                                        (page != 2 || off > 0.01f)
                                }
                            }
                            if (showSharedWallpaperLayer && sharedWallpaperBitmap != null) {
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
                                        val r = 24f * density.density
                                        android.graphics.RenderEffect.createBlurEffect(
                                            r, r, android.graphics.Shader.TileMode.CLAMP
                                        ).asComposeRenderEffect()
                                    } else null
                                }
                                val sharedWallpaperImage = remember(sharedWallpaperBitmap) {
                                    sharedWallpaperBitmap.asImageBitmap()
                                }
                                // 课程表页索引：正常模式 1；滚过它去设置时壁纸开始位移
                                val schedulePageIndex = if (isShiftMode) 0 else 1
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
                                            mustRecord = {
                                                // 滑向/离开设置时像素随位移变化，需重录
                                                mainPagerState.isScrollInProgress ||
                                                    abs(mainPagerState.currentPageOffsetFraction) > 0.001f
                                            }
                                        )
                                        .graphicsLayer {
                                            val s = maxOf(sharedWallpaperScale, sharedMinScale)
                                            scaleX = s
                                            scaleY = s
                                            val scrollPos = mainPagerState.currentPage +
                                                mainPagerState.currentPageOffsetFraction
                                            // 今日/课程表区间内固定；越过课程表去设置时，壁纸向左跟页平移
                                            val settingsShift =
                                                if (scrollPos > schedulePageIndex) {
                                                    -(scrollPos - schedulePageIndex) * size.width
                                                } else {
                                                    0f
                                                }
                                            translationX = sharedWallpaperOffset.x + settingsShift
                                            translationY = sharedWallpaperOffset.y
                                            renderEffect = sharedBlurEffect
                                        },
                                    contentScale = ContentScale.Fit,
                                    colorFilter = sharedBrightnessFilter
                                )
                            }
                            // 主 tab 仅由底栏点击平移切换；关闭用户横滑。
                            // 内容区横向手势会经 nestedScroll 取消未完成的主 tab 动画。
                            HorizontalPager(
                                state = mainPagerState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(mainContentNestedScroll),
                                key = { page -> if (isShiftMode) "shift-$page" else "main-$page" },
                                userScrollEnabled = false,
                                // 三页常驻合成，今日↔我的跨页切换不再中途首构目标页
                                beyondViewportPageCount = 2,
                            ) { page ->
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
                                            useSharedWallpaper = !isShiftMode && todayShowWallpaper && wallpaperBitmap != null,
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
                                                    // 宽度沿用卡片回调（含 Day 列内 2dp padding 后的实际宽），不要用整列 dayBounds
                                                    val topRel = sectionTopPx(geom, course.startSection, div)
                                                    anchorCenter = if (bounds != null && bounds.size >= 3) {
                                                        Offset(
                                                            (bounds[0] + bounds[1]) / 2f,
                                                            bounds[2] + topRel + fullH / 2f
                                                        )
                                                    } else {
                                                        Offset(left, top)
                                                    }
                                                    anchorSize = Offset(width, fullH)
                                                } else {
                                                    anchorCenter = Offset(left, top)
                                                    anchorSize = Offset(width, height)
                                                }
                                                draggedCardPosition = anchorCenter
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
                                                    val sectionH =
                                                        gridGeometry?.sectionHeightPx ?: 0f
                                                    val sectionCount =
                                                        course.endSection - course.startSection + 1
                                                    val cardHeightPx = sectionCount * sectionH
                                                    val centerX = draggedCardPosition.x + offsetX
                                                    val cardTopY =
                                                        draggedCardPosition.y + offsetY - cardHeightPx / 2f
                                                    val firstSectionCenterY =
                                                        cardTopY + sectionH / 2f
                                                    val newTarget = computeDropTarget(
                                                        centerX,
                                                        firstSectionCenterY
                                                    )
                                                    if (newTarget != pendingDropTarget) {
                                                        pendingDropTarget = newTarget
                                                    }
                                                }
                                            },
                                            onCourseDragEnd = { _ ->
                                                // 只结束拖拽浮层；菜单关闭交给 onCourseMenuDismiss
                                                val source = draggedCardCourse
                                                val target = pendingDropTarget
                                                val week = draggedWeek
                                                pendingDropTarget = null
                                                if (source != null && target != null) {
                                                    val sectionSpan =
                                                        source.endSection - source.startSection
                                                    val targetStart = target.second
                                                    val targetEnd = targetStart + sectionSpan
                                                    val sameSlot =
                                                        source.dayOfWeek == target.first &&
                                                                source.startSection == targetStart &&
                                                                source.endSection == targetEnd
                                                    if (!sameSlot) {
                                                        val conflicts = viewModel.getCoursesAtSlot(
                                                            week,
                                                            target.first,
                                                            targetStart,
                                                            targetEnd
                                                        ).filter {
                                                            it.id != source.id && it.isActiveInWeek(
                                                                week
                                                            )
                                                        }
                                                        if (conflicts.isEmpty()) {
                                                            viewModel.moveCourseForWeek(
                                                                source.id,
                                                                week,
                                                                target.first,
                                                                targetStart,
                                                                targetEnd
                                                            )
                                                            // 单周调课可能拆分/合并出新 id，按目标位重收并隐藏防叠影
                                                            draggingCourseIds =
                                                                viewModel.getCoursesAtSlot(
                                                                    week,
                                                                    target.first,
                                                                    targetStart,
                                                                    targetEnd
                                                                )
                                                                    .filter { it.isActiveInWeek(week) }
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
                                                    source.dayOfWeek to (source.startSection..source.endSection)
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
                                                showShiftLoading = true
                                                isExitingShift = false
                                            },
                                            navBarStyle = navBarStyle,
                                            onScrollYChanged = { _ -> },
                                            settingsScrollBehavior = settingsScrollBehavior,
                                            activeSecondaryActivities = activeSecondaryActivities,
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                        )
                                            }
                                        }
                                    }
                                }
                            } else {
                                when (page) {
                                    0 -> ShiftScheduleScreen(
                                        shiftViewModel = shiftViewModel,
                                        settingsViewModel = settingsViewModel,
                                        pagerState = pagerState,
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
                                        activeSecondaryActivities = activeSecondaryActivities,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                    )
                                }
                            }
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 对齐切换课表底栏：None 转场 + 手动 appear + graphicsLayer clip=false，
                        // 避免 AnimatedVisibility 收拢尺寸时把阴影裁掉
                        AnimatedVisibility(
                            visible = shouldShowBackToNowFab,
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 92.dp)
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
                                val (day, section) = target
                                val span = (meta.endSection - meta.startSection).coerceAtLeast(0)
                                val endSection = section + span
                                // 「当前周」=正在浏览的周，非日历 currentWeek
                                val pasteWeek = currentViewingWeek
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
                                            day,
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
                        dropTarget = pendingDropTarget,
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
                            val week = draggedWeek
                            if (source != null && target != null) {
                                val span = source.endSection - source.startSection
                                val atTarget = viewModel.getCoursesAtSlot(
                                    week, target.first, target.second, target.second + span
                                )
                                draggingCourseIds = atTarget
                                    .filter { it.isActiveInWeek(week) }
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
                                val span = source.endSection - source.startSection
                                val atTarget = viewModel.getCoursesAtSlot(
                                    week, target.first, target.second, target.second + span
                                )
                                val atSource = viewModel.getCoursesAtSlot(
                                    week, source.dayOfWeek, source.startSection, source.endSection
                                )
                                draggingCourseIds = (atTarget + atSource)
                                    .filter { it.isActiveInWeek(week) }
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
                    LaunchedEffect(Unit) {
                        val n = com.haooz.chedule.data.NoticeFetcher.fetch(context)
                        if (n != null && com.haooz.chedule.data.NoticeFetcher.shouldShow(context, n)) {
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
                                        com.haooz.chedule.data.NoticeFetcher.markSeen(context, n)
                                        notice = null
                                    }
                                )
                            }
                        }
                    }
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
                }
            }
            // 快照层也要拦截触摸，覆盖 showDetail 已 false 但快照未清除的窗口
            if (mainContentSnapshot != null) {
                Image(
                    bitmap = mainContentSnapshot!!.asImageBitmap(),
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
        MiuixTheme(controller = overlayPageController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides overlayEffectiveForcedDark) {
                if (floatingCardVisible) {
                    val course = draggedCardCourse
                    if (course != null) {
                        // 锚点只用长按瞬间的中心；拖拽/吸附位移走 graphicsLayer，不进组合
                        val widthPx = draggedCardSize.x
                        val geom = gridGeometry
                        val sectionH = geom?.sectionHeightPx
                            ?: with(density) { displayAppearance.cardHeight.dp.toPx() }
                        // 高度含午休/晚修分界缝，与网格上分段卡片的视觉外接框一致
                        val heightPx = if (geom != null) {
                            courseVisualHeightPx(
                                geom,
                                course.startSection,
                                course.endSection,
                                dividerPxFor(geom)
                            )
                        } else {
                            (course.endSection - course.startSection + 1) * sectionH
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
                                cardAlpha = displayAppearance.cardAlpha,
                                cardSurfaceAlpha = displayAppearance.cardSurfaceAlpha,
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
                                cardAlpha = displayAppearance.cardAlpha,
                                cardSurfaceAlpha = displayAppearance.cardSurfaceAlpha,
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
                                    pasteRangeTarget = emptyDay to emptySection
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
        val menuDark = forcedDark ?: appSettingDark
        val menuController = remember {
            ThemeController(if (menuDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        }
        // 组合期同步 mode：SideEffect 会晚一帧
        menuController.colorSchemeMode =
            if (menuDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
        MiuixTheme(controller = menuController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides forcedDark) {
                MorePopupMenus(
                    showMorePopup = showMorePopup,
                    onMorePopupDismiss = { showMorePopup = false },
                    showTodayMorePopup = showTodayMorePopup,
                    onTodayMorePopupDismiss = { showTodayMorePopup = false },
                    morePopupFraction = morePopupFraction,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    isShiftMode = isShiftMode,
                    // 预测性返回手势联动顶栏"更多"按钮：手势推进时按钮归位，取消时恢复移开
                    onMoreBackProgress = { progress ->
                        coroutineScope.launch { scheduleMoreButtonFraction.snapTo(1f - progress) }
                    },
                    onMoreBackCancelled = {
                        coroutineScope.launch { scheduleMoreButtonFraction.animateTo(1f, tween(150)) }
                    },
                    onTodayMoreBackProgress = { progress ->
                        coroutineScope.launch { todayMoreButtonFraction.snapTo(1f - progress) }
                    },
                    onTodayMoreBackCancelled = {
                        coroutineScope.launch { todayMoreButtonFraction.animateTo(1f, tween(150)) }
                    },
                    onJumpWeek = { viewModel.showJumpWeekDialog() },
                    onCourseManage = {
                        val intent = Intent(context, CourseManageActivity::class.java)
                        context.openSecondaryPage(intent)
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
        }
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
                    val combId = combinations.getOrNull(currentCombinationIndex)?.id ?: 0L
                    val isLight = combinations.getOrNull(currentCombinationIndex)?.wallpaperIsLight
                    // 仅内存预览用
                    val capturedSnapshot = captureMainContentBitmap()
                    val saveJob = launch(Dispatchers.IO) {
                        // 合并为一次磁盘提交
                        wallpaperRepository.batchEdit {
                            if (bitmap != null) {
                                wallpaperRepository.saveCombinationWallpaper(combId, bitmap)
                            } else {
                                // 删文件，否则旧壁纸会在重启后恢复
                                wallpaperRepository.clearCombinationWallpaper(combId)
                            }
                            wallpaperRepository.saveCombinationState(
                                combId,
                                wallpaperOffset.x,
                                wallpaperOffset.y,
                                wallpaperScale,
                                // 与当前显示尺寸绑定，旋转后可按参考屏重映射
                                latestScreenWPx,
                                latestScreenHPx
                            )
                            val appearanceToSave = currentAppearance()
                            wallpaperRepository.saveCombinationCardBlur(
                                combId,
                                appearanceToSave.cardBlurRadius
                            )
                            wallpaperRepository.saveCombinationCardAlpha(
                                combId,
                                appearanceToSave.cardAlpha
                            )
                            wallpaperRepository.saveCombinationCardSurfaceAlpha(
                                combId,
                                appearanceToSave.cardSurfaceAlpha
                            )
                            wallpaperRepository.saveCombinationCardHeight(
                                combId,
                                appearanceToSave.cardHeight
                            )
                            wallpaperRepository.saveCombinationCardCornerRadius(
                                combId,
                                appearanceToSave.cardCornerRadius
                            )
                            wallpaperRepository.saveCombinationWallpaperBrightness(
                                combId,
                                appearanceToSave.wallpaperBrightness
                            )
                            wallpaperRepository.saveCombinationWallpaperIsLight(combId, isLight)
                            wallpaperRepository.saveCombinationShowBreakDividers(
                                combId,
                                appearanceToSave.showBreakDividers
                            )
                            wallpaperRepository.saveCombinationCardContentAlignment(
                                combId,
                                appearanceToSave.cardContentAlignment
                            )
                            wallpaperRepository.saveCombinationCardTextColor(
                                combId,
                                appearanceToSave.cardTextColor
                            )
                            wallpaperRepository.saveCombinationCardTextScale(
                                combId,
                                appearanceToSave.cardTextScale
                            )
                            wallpaperRepository.saveCombinationShowClassroom(
                                combId,
                                appearanceToSave.showClassroom
                            )
                            wallpaperRepository.saveCombinationShowTeacher(
                                combId,
                                appearanceToSave.showTeacher
                            )
                            wallpaperRepository.saveCombinationCardRefraction(
                                combId,
                                appearanceToSave.cardRefraction
                            )
                            wallpaperRepository.saveCombinationWallpaperBlur(
                                combId,
                                appearanceToSave.wallpaperBlur
                            )
                            wallpaperRepository.setCurrentCombinationId(combId)
                        }
                        // 「应用」才写入偏好
                        pendingScheduleThemeMode?.let { mode ->
                            context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE)
                                .edit {
                                    putString(ThemeMode.SCHEDULE_THEME_MODE_KEY, mode.prefsValue)
                                }
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
                )
            }
        }

        if (snapshotCoverBitmap != null) {
            Image(
                bitmap = snapshotCoverBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // 只作用于快照这一层，避免叠加闪烁
        if (customizeCoverActive && customizeSnapshot != null) {
            Image(
                bitmap = customizeSnapshot!!.asImageBitmap(),
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
            val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            val screenWidth = windowInfo.containerSize.width.toFloat()
            val screenHeight = windowInfo.containerSize.height.toFloat()
            val p = switchAnimProgress.value
            // 初值 0：首帧不挂 RenderEffect
            val switchPageBlur = remember { Animatable(0f) }
            val blurEffectCache = remember { BlurEffectCache() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // blur/alpha 合一层，少一次离屏合成
                    .graphicsLayer {
                        alpha = if (switchCapturingSnapshot) 0f else 1f
                        val r = switchPageBlur.value
                        renderEffect = if (r > 0.01f) {
                            blurEffectCache.get(r * density.density)
                        } else null
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
                                }
                                val currentProgress = switchAnimProgress.value
                                val remainingDuration =
                                    ((1f - currentProgress) * 560).toInt().coerceAtLeast(1)
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
                                    MainActivity.cachedWallpaperBitmap,
                                    wallpaperBitmap,
                                )
                            }
                        }
                    },
                    onScheduleChanged = {
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
                    onCardSnapshot = { _, cardBitmap, bounds ->
                        if (switchAnimJob?.isActive == true) return@SwitchScheduleScreen
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
                            switchOverlayActive = false
                            switchCardSnapshot = null
                            switchCardBounds = null
                            mainContentSnapshot = null
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
                                }
                            } else null
                            switchAnimJob = coroutineScope.launch {
                                switchAnimProgress.snapTo(1f)
                                switchPageBlur.snapTo(5f)
                                switchReturnBgScrim.snapTo(0.4f)
                                switchCapturingSnapshot = false
                                switchOverlayActive = true
                                switchCardBounds = cardBoundsInScreen
                                switchCardSnapshot = cardSnap
                                val remainingDuration = 350
                                val morphExitEase = CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)
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
            if (switchOverlayActive) {
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
                    if (switchCardSnapshot != null) {
                        Image(
                            bitmap = switchCardSnapshot!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .clip(ContinuousRoundedRectangle(20.dp))
                                .graphicsLayer { alpha = (1f - p * 2f).coerceIn(0f, 1f) },
                            contentScale = ContentScale.None
                        )
                    }
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

        ShiftLoadingOverlay(
            show = showShiftLoading,
            onShiftReady = {
                if (isExitingShift) {
                    shiftViewModel.exitShiftMode()
                    selectedTab = 0
                    coroutineScope.launch { mainPagerState.scrollToPage(0) }
                } else {
                    shiftViewModel.enterShiftMode()
                }
            },
            onHide = { showShiftLoading = false },
        )
    }
}

@SuppressLint("AutoboxingStateCreation")
@Composable
private fun SettingsTopBar(
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    navBarStyle: String,
    scrollBehavior: SharedScrollBehavior? = null,
) {
    if (liquidGlassBackdrop == null) return
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
    scrollBehavior: SharedScrollBehavior? = null,
    showMorePopup: Boolean = false,
    visible: Boolean = true,
    buttonFractionParam: Animatable<Float, *>? = null,
) {
    if (liquidGlassBackdrop == null) return
    val isTabletLiquidGlass = navBarStyle == "rail"
    val dayOfWeekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val dayOfWeekName = if (currentDayOfWeek in 1..7) dayOfWeekNames[currentDayOfWeek - 1] else ""
    val titleText = if (isToday) "今天是$dayOfWeekName" else dayOfWeekName

    val buttonFraction = buttonFractionParam ?: remember { Animatable(0f) }
    LaunchedEffect(showMorePopup) {
        if (showMorePopup) {
            buttonFraction.animateTo(
                1f,
                tween(340, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f))
            )
        } else {
            buttonFraction.animateTo(
                0f,
                tween(420, easing = CubicBezierEasing(0.34f, 1.2f, 0.3f, 1f))
            )
        }
    }

    // 隐藏时 alpha=0 但仍测量，保证 currentHeightPx 启动即就位
    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
        modifier = Modifier.graphicsLayer { alpha = if (visible) 1f else 0f },
    ) {
        CollapsibleTopAppBar(
            title = titleText,
            largeTitle = titleText,
            modifier = Modifier.zIndex(1f),
            scrollBehavior = scrollBehavior,
            startAction = if (visible && isTabletLiquidGlass) {
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
                // 手机端左上角不再放「返回今天」，改为底栏上方悬浮液态玻璃按钮
                null
            },
            endAction = { backdropAlpha, shadowAlpha ->
                if (visible) {
                    if (isTabletLiquidGlass) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                    iconSize = 24.dp,
                                    iconOffset = DpOffset(x = 0.dp, y = (-1).dp),
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            }
                            LiquidTopBarButton(
                                onClick = onMoreClick,
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.More,
                                contentDescription = "更多",
                                iconSize = 23.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                                modifier = Modifier.offset {
                                    val f = buttonFraction.value
                                    IntOffset(
                                        x = (-100 * f).dp.roundToPx(),
                                        y = (45 * f).dp.roundToPx()
                                    )
                                }
                            )
                        }
                    } else {
                        LiquidTopBarButton(
                            onClick = onMoreClick,
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.More,
                            contentDescription = "更多",
                            iconSize = 23.dp,
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                            modifier = Modifier.offset {
                                val f = buttonFraction.value
                                IntOffset(
                                    x = (-100 * f).dp.roundToPx(),
                                    y = (45 * f).dp.roundToPx()
                                )
                            }
                        )
                    }
                }
            },
        )
    }
}
