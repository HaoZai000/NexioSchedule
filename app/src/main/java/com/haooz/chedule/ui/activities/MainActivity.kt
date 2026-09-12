/** 主页面 - 应用入口 Activity */
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.haooz.chedule.ui.components.CourseCard
import com.haooz.chedule.ui.components.LandRippleSpec
import com.haooz.chedule.ui.components.LiquidAddButton
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
import com.haooz.chedule.ui.screens.SettingsScreen
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TodayScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.applyNavigationBarIsDark
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.consumeAllTouches
import com.haooz.chedule.ui.utils.isAppDarkTheme
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
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
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
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

/**
 * 按 0.25px 量化缓存 RenderEffect。
 * graphicsLayer 每帧都会执行 lambda，直接 new 一个 RenderEffect 会同时产生 Java 对象与
 * native 对象，一段 560ms 的动画就是 ~34 个，GC 会明显抖。量化后复用同一个对象。
 */
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

    fun applyHideFromRecents(hidden: Boolean) {
        setTaskExcludedFromRecents(this, hidden)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // 提醒闹钟调度会读取并反序列化全部课程再逐个注册闹钟，超级岛初始化要走 Shizuku
        // 跨进程 binder。两者都与"首帧要显示什么"无关，放到后台执行，不与首帧渲染抢主线程。
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            CourseReminderHelper.startReminderService(appContext)
            // 初始化超级岛通知助手
            IslandNotificationHelper.init(appContext)
        }

        // 接入统计上报：active 每次启动上报；install 仅每个设备首次上报
        com.haooz.chedule.data.StatsReporter.init(this)
        com.haooz.chedule.data.StatsReporter.reportActive(this)
        com.haooz.chedule.data.StatsReporter.reportInstallOnce(this)

        // 异步预加载当前搭配壁纸，避免阻塞主线程（Compose 侧已处理 cachedWallpaperBitmap=null 的情况）
        if (cachedWallpaperBitmap == null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = com.haooz.chedule.data.CourseRepository(this@MainActivity)
                    repo.migrateToCombinationsIfNeeded()
                    val ids = repo.getCombinationIds()
                    val currentId = repo.getCurrentCombinationId()
                    val idx = ids.indexOf(currentId).coerceAtLeast(0)
                    // 单搭配模式：只缓存当前搭配，后续启动不必再读其余旧搭配
                    cachedCombinationIds = if (ids.isEmpty()) emptyList() else listOf(currentId)
                    cachedCurrentCombinationIndex = 0
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
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        isInFreeformWindow = isInMultiWindowMode
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        handleReminderSettingsIntent(intent)
        // 更新版本号触发 Compose 重组
        shareIntentVersion++
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        val hideBackground = getSharedPreferences("app_preferences", MODE_PRIVATE)
            .getBoolean("hide_background", false)
        if (hideBackground) {
            applyHideFromRecents(true)
            moveTaskToBack(true)
            return
        }
        super.onBackPressed()
    }

    private fun handleReminderSettingsIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS,
                false
            ) == true
        ) {
            intent.removeExtra(CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS)
            startActivity(Intent(this, CourseReminderActivity::class.java))
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

/** 计算壁纸 cover-fill 最小缩放比例，确保壁纸填满屏幕不露出底部背景 */
private fun computeWallpaperMinScale(
    bitmap: android.graphics.Bitmap?,
    screenWPx: Float,
    screenHPx: Float
): Float {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return 1f
    val fitScale = minOf(screenWPx / bitmap.width, screenHPx / bitmap.height)
    val coverScale = maxOf(screenWPx / bitmap.width, screenHPx / bitmap.height)
    return if (fitScale > 0f) coverScale / fitScale else 1f
}

/**
 * 壁纸均匀测光：将壁纸等比缩放到小网格后计算平均亮度（感知加权），
 * 平均亮度 >= 128 判定为亮色壁纸，否则为暗色壁纸。
 */
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

/** 删除本周课程确认弹窗 */
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
            // 删除范围选择
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

/** 粘贴范围弹窗：选择粘贴全部周还是仅当前周 */
@Composable
private fun PasteRangeDialog(
    show: Boolean,
    courseName: String,
    viewModel: CourseViewModel,
    currentWeek: Int,
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

/** 调课冲突弹窗：拖到有课位置时让用户选择"覆盖"或"交换" */
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

/** 排班模式切换加载遮罩 */
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

/** 课表页更多菜单 + 今日页更多菜单 */
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
    val scheduleScrollBehavior = rememberSharedScrollBehavior()

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
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

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
        // 只在"返回"时刷新：冷启动的第一次 onResume 时，各 ViewModel 刚把数据加载完，
        // 这里再全量刷一遍（设置 + 全部课程 + 所有课表摘要）纯属重复开销，会明显拖慢首屏。
        if (resumeCount > 1) {
            withContext(Dispatchers.IO) {
                settingsViewModel.refreshSettings()
                scheduleViewModel.refreshScheduleList()
            }
            viewModel.reloadCourses()
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

    // 预热 RenderEffect：创建一个不可见的 Box 触发 drawBackdrop 初始化，
    // 避免首次打开 BlurBottomSheet 时掉帧。
    // 预热要创建 GraphicsLayer + RenderEffect，本身有成本，因此等首帧出来之后再挂载，
    // 不占用冷启动首帧的合成时间。
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
    // 打开课程详情时所点击卡片所在的周次（用于详情页自动滚动定位）
    var detailTargetWeek by remember { mutableIntStateOf(0) }
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
    // 粘贴飞行：复用长按浮层卡片，直线飞向目标格，前段快放大到 1.4、后段快缩小回 1.0
    var isPasteFlight by remember { mutableStateOf(false) }
    // 浮层落地冲击波：周围课程卡按距离延迟涟漪
    var landRippleCenter by remember { mutableStateOf(Offset.Zero) }
    var landRippleToken by remember { mutableIntStateOf(0) }
    // 调课冲突悬停：浮层移到目标卡上方并上下浮动，直到用户在弹窗中做出选择
    var isConflictHover by remember { mutableStateOf(false) }
    var conflictHoverBobJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 交换飞行：被交换的目标课变成第二浮层，与源浮层同时交叉飞行
    var swapFlightVisible by remember { mutableStateOf(false) }
    var swapFlightCourse by remember { mutableStateOf<Course?>(null) }
    var swapFlightOriginCenter by remember { mutableStateOf(Offset.Zero) }
    var swapFlightWidth by remember { mutableFloatStateOf(0f) }
    val swapFlightOffsetX = remember { Animatable(0f) }
    val swapFlightOffsetY = remember { Animatable(0f) }
    val swapFlightScale = remember { Animatable(1f) }
    // 快捷菜单状态
    var shortcutMenuCourse by remember { mutableStateOf<Course?>(null) }
    var shortcutMenuVisible by remember { mutableStateOf(false) }
    var shortcutMenuPosition by remember { mutableStateOf(Offset.Zero) }
    var shortcutMenuSize by remember { mutableStateOf(IntSize.Zero) }
    var shortcutMenuBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
    // 卡片/格子宽度：左移时菜单右边缘对齐卡片右边缘
    var shortcutMenuAnchorWidth by remember { mutableFloatStateOf(0f) }
    // 空白格长按菜单：(星期, 起始节次)，null 表示未打开；粘贴目标 = 长按格
    var emptyCellMenuTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // 课程复制剪贴板：页面会话级（切页/进程结束时随 Activity 销毁），再次复制覆盖，粘贴后保留
    var copiedCourseForPaste by remember { mutableStateOf<Course?>(null) }
    // 粘贴范围弹窗：点粘贴后弹出，选择「全部周」或「当前周」
    var showPasteRangeDialog by remember { mutableStateOf(false) }
    var pasteRangeTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // 删除确认弹窗状态
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteConfirmCourse by remember { mutableStateOf<Course?>(null) }

    // 自定义课表页面状态
    var showCustomizePage by remember { mutableStateOf(false) }
    var customizeSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var snapshotCoverBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isCustomizeExiting by remember { mutableStateOf(false) }
    var customizeExitTargetScale by remember { mutableFloatStateOf(1f) }
    val customizeExitScale = remember { Animatable(1f) }
    val customizeExitAlpha = remember { Animatable(1f) }
    var isWindowCutoutActive by remember { mutableStateOf(false) }
    // 进出场全屏快照覆盖层：进入时用主界面快照盖住开洞过渡；退出-取消时盖住回退过程（不保存）。
    // 动画只作用于这一层，避免"主界面+页面双缩放+快照"叠加导致的闪烁。
    var customizeCoverActive by remember { mutableStateOf(false) }
    val customizeCoverScale = remember { Animatable(1f) }
    val customizeCoverAlpha = remember { Animatable(1f) }
    val wallpaperRepository = remember { com.haooz.chedule.data.CourseRepository(context) }
    // 多搭配支持
    var combinations by remember { mutableStateOf(listOf<com.haooz.chedule.data.Combination>()) }
    var currentCombinationIndex by remember { mutableIntStateOf(0) }
    var wallpaperBitmap by remember { mutableStateOf(MainActivity.cachedWallpaperBitmap) }
    var wallpaperOffset by remember { mutableStateOf(MainActivity.cachedWallpaperOffset) }
    var wallpaperScale by remember { mutableFloatStateOf(MainActivity.cachedWallpaperScale) }
    // 快照捕获时临时覆盖主题：captureThemeActive 为 true 时 effectiveForcedDark 取 captureThemeIsDark；
    // 用于捕获不同搭配快照时让主题跟随该搭配的壁纸亮暗（亮色壁纸→浅色，暗色→深色，无壁纸→跟随应用设置）
    var captureThemeActive by remember { mutableStateOf(false) }
    var captureThemeIsDark by remember { mutableStateOf<Boolean?>(null) }

    // 壁纸主题锁定：课程表页有壁纸时按壁纸均匀测光结果强制浅色/深色；今日页仅在开启"今日页显示壁纸"时锁定，否则跟随系统；设置页跟随系统
    val currentComb = combinations.getOrNull(currentCombinationIndex)
    val currentCombIsLight = currentComb?.wallpaperIsLight
    // 同步读取当前搭配持久化的壁纸亮暗结果：搭配/壁纸是异步加载的，若首帧只依赖它们，
    // 首次进入会先显示应用主题、加载完成后又跳变到壁纸主题。这里用轻量同步读取兜底，首帧即确定主题。
    val initialCombWallpaperIsLight = remember {
        wallpaperRepository.getCombinationWallpaperIsLight(wallpaperRepository.getCurrentCombinationId())
    }
    // 搭配已加载时用其自身测光结果；仅当搭配尚未加载（首帧）时退回 initial，
    // 避免无壁纸搭配(currentCombIsLight=null)错误回退到初始搭配的测光结果
    val combIsLight = if (currentComb == null) initialCombWallpaperIsLight else currentCombIsLight
    val todayShowWallpaper = settingsViewModel.todayShowWallpaper.collectAsState().value
    val todayPageShowsWallpaper = selectedTab == 0 && todayShowWallpaper
    // 默认主题(跟随壁纸/跟随应用/浅色模式/深色模式)：仅决定今日页与课程表页的主题来源，
    // 使用独立偏好 key，与全局主题开关完全隔离，不影响其它任何页面。
    val scheduleThemeMode = rememberScheduleThemeMode()
    val forcedDark = if (isShiftMode) null
    else if (selectedTab == 2) null
    // 无壁纸时整个"默认主题"选项不生效，两页一律跟随应用（清除壁纸后可恢复）
    else if (combIsLight == null) null
    else if (selectedTab == 1 || todayPageShowsWallpaper) {
        when (scheduleThemeMode) {
            ThemeMode.FOLLOW_WALLPAPER -> !combIsLight
            ThemeMode.FOLLOW_APP -> null
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    } else null
    val effectiveIsDark = forcedDark ?: isDark
    val appSettingDark = rememberAppSettingDark()
    // 系统状态栏跟随页面实际深浅（含壁纸强制主题），保证有壁纸页面正确反色；
    // 系统导航栏图标始终跟随应用设置（theme_mode）
    LaunchedEffect(effectiveIsDark, appSettingDark) {
        activity?.applyThemeAwareSystemBars(effectiveIsDark)
        activity?.applyNavigationBarIsDark(appSettingDark)
    }

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
    var originalSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 记录进入搭配页时原始搭配的壁纸亮暗结果，用于退出（非应用）时还原主题锁定
    var originalWallpaperIsLight by remember { mutableStateOf<Boolean?>(null) }
    // 记录进入搭配页时的原始搭配快照，取消退出时整体还原，避免编辑回调造成的字段残留
    var originalCombination by remember { mutableStateOf<com.haooz.chedule.data.Combination?>(null) }
    var isApplyingCustomize by remember { mutableStateOf(false) }
    // 新建搭配后自动进入编辑模式的触发器
    var pendingEnterCutout by remember { mutableStateOf(false) }

    // 单一数据源：外观从当前搭配（combinations）派生，编辑只写 combinations，去掉 appearance 双写。
    // 组合尚未加载前回退到启动缓存，保持冷启动不闪默认值。
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
                    cardAlpha = value.cardAlpha,
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

    // 启动时迁移旧数据并加载所有搭配
    LaunchedEffect(Unit) {
        // 如果伴生对象已有缓存，直接使用，跳过 Phase 1 的 IO
        val cached = MainActivity.cachedWallpaperBitmap
        val cachedIds = MainActivity.cachedCombinationIds
        val cachedIdx = MainActivity.cachedCurrentCombinationIndex

        val currentIndex: Int

        if (cached != null && cachedIds.isNotEmpty()) {
            // 有缓存：直接构建 combinations 列表，bitmap 用缓存
            currentIndex = cachedIdx
            val list = cachedIds.mapIndexed { index, id ->
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
                    cardContentAlignment = wallpaperRepository.getCombinationCardContentAlignment(id),
                    cardTextColor = wallpaperRepository.getCombinationCardTextColor(id),
                    cardTextScale = wallpaperRepository.getCombinationCardTextScale(id),
                    showClassroom = wallpaperRepository.getCombinationShowClassroom(id),
                    showTeacher = wallpaperRepository.getCombinationShowTeacher(id),
                    wallpaperIsLight = wallpaperRepository.getCombinationWallpaperIsLight(id),
                    wallpaperBlur = wallpaperRepository.getCombinationWallpaperBlur(id)
                )
            }
            // 单搭配模式：只保留当前搭配，其余旧搭配数据不再加载，
            // 避免主界面与进入课表外观时读取的组合不一致（随机出现旧搭配）。
            // 裁剪必须在赋值前完成：先赋全量列表再裁掉会额外触发一轮全量重组。
            val cachedCombOnly = list.getOrNull(currentIndex)
            combinations = if (cachedCombOnly != null) listOf(cachedCombOnly) else emptyList()
            currentCombinationIndex = 0
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
                        ),
                        cardTextColor = wallpaperRepository.getCombinationCardTextColor(id),
                        cardTextScale = wallpaperRepository.getCombinationCardTextScale(id),
                        showClassroom = wallpaperRepository.getCombinationShowClassroom(id),
                        showTeacher = wallpaperRepository.getCombinationShowTeacher(id),
                        cardRefraction = wallpaperRepository.getCombinationCardRefraction(id),
                        wallpaperIsLight = wallpaperRepository.getCombinationWallpaperIsLight(id),
                        wallpaperBlur = wallpaperRepository.getCombinationWallpaperBlur(id)
                    )
                }
                // 只需列表与当前下标：搭配 id 列表在裁剪为单搭配后不再需要外传
                Pair(list, loadedIndex)
            }
            currentIndex = phase1.second
            // 单搭配模式：同上，裁剪必须在赋值前完成，避免多触发一轮全量重组
            val currentCombOnly = phase1.first.getOrNull(currentIndex)
            combinations = if (currentCombOnly != null) listOf(currentCombOnly) else emptyList()
            currentCombinationIndex = 0
            // 更新缓存：只缓存当前搭配，下次启动不必再读其余旧搭配
            MainActivity.cachedCombinationIds =
                if (currentCombOnly != null) listOf(currentCombOnly.id) else emptyList()
            MainActivity.cachedCurrentCombinationIndex = 0
        }

        // 同步当前搭配状态到 wallpaperBitmap/Offset/Scale（主界面使用）
        val curr = combinations.getOrNull(0)
        if (curr != null) {
            wallpaperBitmap = curr.bitmap
            wallpaperOffset = curr.offset
            val minScale = computeWallpaperMinScale(curr.bitmap, screenWPx, screenHPx)
            wallpaperScale = maxOf(curr.scale, minScale)
            savedWallpaperBitmap = curr.bitmap
            savedWallpaperOffset = curr.offset
            savedWallpaperScale = wallpaperScale
            savedAppearance = com.haooz.chedule.data.AppearanceConfig.fromCombination(curr)
            originalWallpaperBitmap = curr.bitmap
            originalWallpaperOffset = curr.offset
            originalWallpaperScale = wallpaperScale
        }
    }
    val cutoutMainScale = remember { Animatable(1f) }
    var cutoutCenterYRatio by remember { mutableFloatStateOf(0.5f) }
    // 弹窗打开时的同步上移 Animatable（与 CustomizeScheduleScreen 共享同一实例，直接读 .value 同帧同步）
    // 位移在 graphicsLayer 中按当前缩放比例用同一表达式计算，与开洞中心保持一致
    val sheetOffsetY = remember { Animatable(0f) }
    LaunchedEffect(isWindowCutoutActive) {
        if (isWindowCutoutActive) {
            // 进入编辑模式时，同步当前搭配的值到 live 状态
            val c = combinations.getOrNull(currentCombinationIndex)
            if (c != null) {
                wallpaperBitmap = c.bitmap
                wallpaperOffset = c.offset
                val minScale = computeWallpaperMinScale(c.bitmap, screenWPx, screenHPx)
                wallpaperScale = maxOf(c.scale, minScale)
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
    // 覆盖层是否激活。原先用切换页的整屏位图当哨兵，但覆盖层从不绘制它，
    // 白养一张 ~10MB 全屏 Bitmap，故改用布尔量
    var switchOverlayActive by remember { mutableStateOf(false) }
    var switchCardSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // switchCardBounds：动画实际使用的卡片矩形（窗口坐标系），形变插值的起止依据
    var switchCardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // switchCurrentCardBounds：切换页上报的「当前课表卡片」位置，退出时若拿不到截图就退而用它
    var switchCurrentCardBounds by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(
            null
        )
    }
    // switchContentRootX/Y：切换页内容根在窗口中的偏移，卡片 bounds 是页面坐标系，
    //   覆盖层用窗口坐标系，两者靠它换算
    var switchContentRootX by remember { mutableFloatStateOf(0f) }

    // MainScheduleScreen 状态提升到 Activity 层，return@Scaffold 不会销毁
    val scheduleScrollState = rememberScrollState()
    val scheduleSelectedCourse = remember { mutableStateOf<Course?>(null) }
    val scheduleSelectedCourses = remember { mutableStateOf<List<Course>>(emptyList()) }
    val scheduleShowCourseDetail = remember { mutableStateOf(false) }

    // TodayScreen 状态提升到 Activity 层
    val todayListState = rememberLazyListState()
    var switchContentRootY by remember { mutableFloatStateOf(0f) }
    var switchAnimJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // switchAnimForward = true 表示正在「进入」切换页，false 表示「退出」回主内容
    var switchAnimForward by remember { mutableStateOf(false) }
    var switchAnimRunning by remember { mutableStateOf(false) }
    // 切换课表后的异步重载任务，快照截取前需 join 等待新课表数据就绪
    var switchReloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val switchAnimProgress = remember { Animatable(0f) }
    val backgroundScale = remember { Animatable(1f) }
    val managePageBlurRadius = remember { Animatable(0f) }
    // 长按快捷菜单显示时的背景模糊（仅课程长按菜单；空白格菜单不模糊）
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
    // 主内容快照按需录制：record() 会把整棵主内容树（3 个 tab + 壁纸 + 课程卡片）再画一遍，
    // 常驻每帧录制等于绘制开销翻倍，且与内部 backdrop 的录制嵌套放大。只在截图前录一帧。
    class MainSnapshotRequester {
        var lastRecordedToken: Int = 0
    }
    val mainSnapshotRequester = remember { MainSnapshotRequester() }
    var mainSnapshotToken by remember { mutableIntStateOf(0) }
    val captureMainContentBitmap: suspend () -> android.graphics.Bitmap = {
        mainSnapshotToken++
        // 等一帧让 draw 阶段完成录制，再等一帧确保该帧已提交
        withFrameNanos { }
        withFrameNanos { }
        screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
    }
    // 模糊变化后延迟重新捕获快照的 job
    var blurSnapshotJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val hapticFeedback = LocalHapticFeedback.current

    val calendar = Calendar.getInstance()
    val currentDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()

    val basePage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
    val autoAdvancePage = remember(currentWeek, totalWeeks, currentDayOfWeek, smartWeekend) {
        if (currentDayOfWeek in 6..7 && smartWeekend) {
            val weekendDays = settingsViewModel.getWeekendDaysForWeek(currentWeek)
            if (currentDayOfWeek !in weekendDays) {
                (basePage + 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
            } else basePage
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

    LaunchedEffect(isShiftMode) {
        if (shiftModeInitialized) {
            selectedTab = if (isShiftMode) 0 else if (defaultHomepage == "今日") 0 else 1
        }
        shiftModeInitialized = true
    }

    LaunchedEffect(currentWeek, totalWeeks, smartWeekend) {
        val base = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
        val target = if (currentDayOfWeek in 6..7 && smartWeekend) {
            val weekendDays = settingsViewModel.getWeekendDaysForWeek(currentWeek)
            if (currentDayOfWeek !in weekendDays) {
                (base + 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
            } else base
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
    // 触发落地涟漪
    val triggerLandRipple: (Offset) -> Unit = { center ->
        landRippleCenter = center
        landRippleToken++
    }
    // 停止冲突悬停浮动
    val stopConflictHover: () -> Unit = {
        conflictHoverBobJob?.cancel()
        conflictHoverBobJob = null
        isConflictHover = false
    }
    // 清理交换第二浮层
    val clearSwapFlight: () -> Unit = {
        swapFlightVisible = false
        swapFlightCourse = null
    }
    // 关闭浮层：先播退场动画（scale 1.04→1.0），动画结束再清空状态，让原卡片 alpha 恢复 1
    val dismissFloatingCard: () -> Unit = {
        stopConflictHover()
        clearSwapFlight()
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
     * 粘贴飞行缩放曲线：
     * 前半升到 peakScale，中段落回 1.0，末段落地轻弹（轻微放大再归位）。
     */
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
                // 落地：1 → 1.05 → 1
                val local = (raw - landStart) / (1f - landStart)
                1f + 0.05f * sin(PI * local).toFloat()
            }
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
                    // 浮层快落地时（约 80%）先开涟漪，邻卡在真卡显现前就开始动
                    val jobRipple = launch {
                        delay(176.milliseconds)
                        triggerLandRipple(targetCenter)
                    }
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
                        launch { floatingScale.animateTo(1f, tween(durationMillis = 180)) }
                    jobX.join(); jobY.join(); jobScale.join(); jobRipple.join()
                    // 落地轻弹：轻微放大再归位
                    floatingScale.animateTo(1.05f, tween(55, easing = FastOutSlowInEasing))
                    floatingScale.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
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

    /**
     * 粘贴飞行动画：复用长按浮层卡片。
     * 直线飞向目标格（位移线性），前 30% 放大到 1.4、后段快速缩回 1.0；结束后回调 onFinished 提交粘贴。
     * 无法定位时直接回调，不播动画。
     */
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
                // 等弹窗退场后再起飞，避免叠在一起
                delay(120.milliseconds)
                // 原卡片保持可见（复制语义），仅浮层克隆飞行
                draggedCardCourse = source
                draggedWeek = currentViewingWeek
                draggedCardPosition = sourceCenter
                draggedCardOffset = Offset.Zero
                // 卡片左右各 2dp padding，与 CourseCard 默认 padding 对齐
                val cardPadPx = with(density) { 2.dp.toPx() }
                draggedCardSize = Offset(
                    (sourceBounds[1] - sourceBounds[0] - cardPadPx * 2f).coerceAtLeast(1f),
                    (span + 1) * sectionH
                )
                isPasteFlight = true
                isSnapping = true
                floatingCardVisible = true
                floatingOffsetX.snapTo(0f)
                floatingOffsetY.snapTo(0f)
                floatingScale.snapTo(1f)

                // 位移 ease-in-out（两边慢中间快）；缩放：升到 1.4 再回落，落地轻弹
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
                    floatingOffsetX.snapTo(dx * t)
                    floatingOffsetY.snapTo(dy * t)
                    floatingScale.snapTo(pasteFlightScaleAt(raw, peakScale))
                    // 快落地时先开涟漪，邻卡在真卡显现前就开始动
                    if (!rippleFired && raw >= 0.8f) {
                        rippleFired = true
                        triggerLandRipple(targetCenter)
                    }
                    if (raw >= 1f) break
                }

                // 落地后立刻换上真实课程并撤掉浮层，避免双影
                floatingCardVisible = false
                isSnapping = false
                isPasteFlight = false
                draggedCardCourse = null
                draggedCardOffset = Offset.Zero
                floatingOffsetX.snapTo(0f)
                floatingOffsetY.snapTo(0f)
                floatingScale.snapTo(0.94f)
                onFinished()
            }
        } else {
            onFinished()
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
     * 粘贴节奏飞行：从当前悬停 offset 飞到 destOffset，结束后收起浮层。
     */
    fun flyFloatingCardWithPasteMotion(destOffsetX: Float, destOffsetY: Float) {
        stopConflictHover()
        coroutineScope.launch {
            isSnapping = true
            isPasteFlight = true
            val startX = floatingOffsetX.value
            val startY = floatingOffsetY.value
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
                floatingOffsetX.snapTo(startX + (destOffsetX - startX) * t)
                floatingOffsetY.snapTo(startY + (destOffsetY - startY) * t)
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
            draggedCardOffset = Offset.Zero
            pendingDropTarget = null
            isSnapping = false
            isPasteFlight = false
            floatingOffsetX.snapTo(0f)
            floatingOffsetY.snapTo(0f)
            floatingScale.snapTo(0.94f)
            clearSwapFlight()
        }
    }

    /**
     * 冲突取消：复用粘贴飞行节奏，从悬停位置连贯飞回原位再收起浮层。
     */
    val flyFloatingCardHome: () -> Unit = {
        flyFloatingCardWithPasteMotion(0f, 0f)
    }

    /**
     * 覆盖确认：复用粘贴飞行节奏，从悬停位置飞到目标格再落地收起。
     */
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

    /**
     * 交换双浮层：源浮层从悬停点飞向目标格，被交换课从目标格飞向原位，
     * 两套粘贴节奏（480ms / 1→1.4→1.0）同时进行。
     */
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
                    // 第二浮层：被交换的目标课
                    val sectionH = gridGeometry?.sectionHeightPx ?: 0f
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

                    // 源浮层
                    isSnapping = true
                    isPasteFlight = true
                    floatingScale.snapTo(1f)
                    val srcStartX = floatingOffsetX.value
                    val srcStartY = floatingOffsetY.value
                    val srcEndX = targetCenter.x - draggedCardPosition.x
                    val srcEndY = targetCenter.y - draggedCardPosition.y
                    // 目标课终点：源课原中心
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
                        floatingOffsetX.snapTo(srcStartX + (srcEndX - srcStartX) * t)
                        floatingOffsetY.snapTo(srcStartY + (srcEndY - srcStartY) * t)
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
                    draggedCardOffset = Offset.Zero
                    pendingDropTarget = null
                    isSnapping = false
                    isPasteFlight = false
                    floatingOffsetX.snapTo(0f)
                    floatingOffsetY.snapTo(0f)
                    floatingScale.snapTo(0.94f)
                    swapFlightOffsetX.snapTo(0f)
                    swapFlightOffsetY.snapTo(0f)
                    swapFlightScale.snapTo(1f)
                }
            }
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

    // 统一的课程详情页打开函数
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
            // 先截取全屏快照（在隐藏课程之前，确保快照内容完整）
            val fullSnapshot = captureMainContentBitmap()
            mainContentSnapshot = fullSnapshot
            hiddenCourseIds = setOf(courseIdToHide)
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

    // 进入"自定义课表"搭配页：捕获当前搭配快照后打开搭配页（单搭配）。
    // 由顶栏"课表外观"菜单和长按"自定义课表"按钮共用。
    val enterCustomizePage: () -> Unit = {
        coroutineScope.launch {
            val screenW = windowInfo.containerSize.width.toFloat()
            customizeExitTargetScale = (screenW * 0.65f) / screenW
            // 清除所有旧快照（每次进入搭配页时重新捕获）
            combinations = combinations.map { it.copy(snapshot = null) }
            // 外观由当前搭配派生（已含模糊设置），无需另行同步；稍作等待确保渲染携带最新效果
            delay(50.milliseconds)
            // 截取当前搭配快照。
            // 注意：toImageBitmap() 捕获的是绑定源 RenderNode 的硬件位图；若直接画回根图层，
            // 会与 Mi 背景模糊链形成渲染树自引用，导致 RenderNode::prepareTreeImpl 无限递归
            // 栈溢出（RenderThread SIGSEGV）。因此立即复制为独立 ARGB_8888 位图，切断对源层的引用。
            val captured = captureMainContentBitmap()
            // 整屏 ARGB_8888 拷贝约 10MB，放在主线程做必掉一帧，挪到 IO 线程
            val currentSnapshot = withContext(Dispatchers.IO) {
                captured.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: captured
            }
            customizeSnapshot = currentSnapshot
            if (combinations.isNotEmpty()) {
                combinations = combinations.toMutableList().also {
                    it[0] = it[0].copy(snapshot = currentSnapshot)
                }
            }
            // 立即打开搭配页并直接进入开洞(编辑)态：不再有落地卡片页
            customizeExitScale.snapTo(1f)
            customizeExitAlpha.snapTo(1f)
            showCustomizePage = true
            // 触发 MainActivity 主内容缩到开洞大小、CustomizeScheduleScreen 进入编辑态
            isWindowCutoutActive = true
            pendingEnterCutout = true
            // 用主界面整屏快照盖住开洞过渡：快照从满屏连贯缩小到开洞处，
            // 与背后主内容缩放同步（transformOrigin 对齐开洞中心），到位后淡出快照露出实时内容
            customizeCoverActive = true
            customizeCoverScale.snapTo(1f)
            customizeCoverAlpha.snapTo(1f)
            delay(280.milliseconds)
            launch {
                // 同步主界面的开洞缩放(0.75)，transformOrigin 对齐开洞中心
                customizeCoverScale.animateTo(
                    0.75f,
                    tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
            }
            launch {
                // 缩放到位后淡出快照，露出开洞后的实时内容
                delay(400.milliseconds)
                customizeCoverAlpha.animateTo(
                    0f,
                    tween(120, easing = FastOutSlowInEasing)
                )
                customizeCoverActive = false
            }
            // 记录进入搭配页时的原始搭配
            originalCombinationIndex = 0
            originalWallpaperBitmap = wallpaperBitmap
            originalWallpaperOffset = wallpaperOffset
            originalWallpaperScale = wallpaperScale
            originalSnapshot = combinations.getOrNull(0)?.snapshot
            originalWallpaperIsLight = combinations.getOrNull(0)?.wallpaperIsLight
            originalCombination = combinations.getOrNull(0)
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
            wallpaperOffset = Offset.Zero
            val autoScale = computeWallpaperMinScale(bitmap, screenWPx, screenHPx)
            wallpaperScale = autoScale
            // 均匀测光：判断亮色/暗色壁纸，供今日页/课程表页锁定主题
            val isLight = computeWallpaperIsLight(bitmap)
            // 同步到当前搭配
            val idx = currentCombinationIndex
            if (idx in combinations.indices) {
                combinations = combinations.toMutableList().also {
                    it[idx] = it[idx].copy(
                        bitmap = bitmap,
                        offset = Offset.Zero,
                        scale = autoScale,
                        wallpaperIsLight = isLight
                    )
                }
            }
        }
    }

    var showSwitchSchedule by remember { mutableStateOf(false) }
    // switchPendingReverse：切换页已就位、等待播放「进入」动画（p 1→0）
    var switchPendingReverse by remember { mutableStateOf(false) }
    // switchCapturingSnapshot：截图期间切换页先藏起来（alpha=0），截完才显示
    var switchCapturingSnapshot by remember { mutableStateOf(false) }
    // scheduleChanged：本次在切换页改过课表，退出前要重新截主内容快照
    var scheduleChanged by remember { mutableStateOf(false) }
    var showMorePopup by remember { mutableStateOf(false) }
    var showTodayMorePopup by remember { mutableStateOf(false) }
    val morePopupFraction = remember { Animatable(0f) }
    var todayJumpToDateTrigger by remember { mutableIntStateOf(0) }

    val isViewingCurrentWeek = currentViewingWeek == currentWeek

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
            // 进入搭配页但尚未开洞时显示"原始已被应用"的外观，避免快照过渡期闪烁实时编辑值；
            // 原始外观由 originalCombination 派生，开洞后直接展示当前（被编辑）外观
            if (showCustomizePage && !isWindowCutoutActive) {
                originalCombination?.let { com.haooz.chedule.data.AppearanceConfig.fromCombination(it) }
                    ?: currentAppearance()
            } else {
                currentAppearance()
            }
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
                    // 开洞编辑时主界面由 cutoutScale（0.75）控制；其余场景由 exitScale/cutoutMainScale 控制。
                    // 进出场动画统一由全屏快照覆盖层处理，这里不再叠加 enterScale，避免闪烁。
                    val effectiveScale = if (isCustomizeExiting && isWindowCutoutActive) {
                        cutoutScale
                    } else {
                        exitScale * cutoutScale
                    }
                    scaleX = baseScale * effectiveScale
                    scaleY = baseScale * effectiveScale
                    alpha = mainContentAlpha
                    // 弹窗打开时同步上移：读取与 CustomizeScheduleScreen 共享的同一 Animatable，像素级同帧。
                    // 位移按缩放比例换算（与开洞中心 1-scaleProg 同一表达式，基于 cutoutMainScale 计算）
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
                    // 用 drawWithContent + clipPath + addSquircleRect 实现 squircle 圆角裁剪
                    // drawWithContent 在 graphicsLayer 缩放后应用，每帧重新裁剪
                    // 视觉圆角 = screenRadius * effectiveScale（随缩放变小）
                    // 搭配页退出时锁定圆角为 screenCornerRadius，避免缩小
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
                        // 只在被请求时录制一帧（见 captureMainContentBitmap），避免每帧重复渲染整棵主内容树
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
            // 有壁纸时用强制主题包裹脚手架（同时修改 colorScheme 与 isAppDarkTheme 两条通道）
            val scaffoldContent = @Composable {
                Scaffold(
                    bottomBar = {
                        ScheduleBottomBar(
                            navBarStyle = navBarStyle,
                            isShiftMode = isShiftMode,
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            addButton = {
                                if (!isShiftMode) {
                                    LiquidAddButton(
                                        onClick = { viewModel.showAddDialog() },
                                        backdrop = liquidGlassBackdrop
                                    )
                                }
                            }
                        )
                    },
                    topBar = {
                        ScheduleTopBar(
                            visible = (!isShiftMode && selectedTab == 1) || (isShiftMode && selectedTab == 0),
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
                                        // 先让切换页进入组合（首帧组合 + 布局最贵），与快照截取并行。
                                        // 原串行流程要先等 2 帧 + 全屏回读，期间界面完全没反应。
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
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            scrollBehavior = scheduleScrollBehavior,
                            showMorePopup = showMorePopup,
                        )
                        // 设置页标题栏（Activity 层级渲染，避免 drawPlainBackdrop native crash）
                        if (selectedTab == 2 || (isShiftMode && selectedTab == 1)) {
                            SettingsTopBar(
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                navBarStyle = navBarStyle,
                                scrollBehavior = settingsScrollBehavior,
                            )
                        }
                        // 今日页标题栏（液态玻璃模式下在 Activity 层级渲染）。
                        // 始终渲染（非今日 tab 时 alpha=0、不渲染按钮）但保持测量，
                        // 确保 todayScrollBehavior.currentHeightPx 启动即就位，切到今日页时内容顶部不慢一帧。
                        TodayTopBar(
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            navBarStyle = navBarStyle,
                            currentDayOfWeek = todaySelectedDayOfWeek,
                            isToday = todayIsToday,
                            onBackToToday = { scrollToTodayTrigger++ },
                            onMoreClick = { showTodayMorePopup = true },
                            scrollBehavior = todayScrollBehavior,
                            showMorePopup = showTodayMorePopup,
                            visible = !isShiftMode && selectedTab == 0,
                        )
                    }
                ) { paddingValues ->
                    // 课程详情动画期间：跳过内容重组，用快照 Image 替代。
                    // 占位 Box 必须吃满触摸，否则详情页退出瞬间真实课表已重组、事件会穿到卡片上。
                    if (showDetail && mainContentSnapshot != null) {
                        Box(modifier = Modifier.fillMaxSize().consumeAllTouches())
                        return@Scaffold
                    }
                    // 不再用 combinations.isEmpty() 门控内容区：课程网格只依赖 viewModel，
                    // 与壁纸加载解耦；壁纸未就绪时 MainScheduleScreen 内部显示主题底色照常渲染。
                    // 搭配操作（新建/删除/编辑）在各自回调里已有 getOrNull 守卫，空列表不会越界。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = railPaddingStart)
                        ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // 注意：开洞（外观页编辑态）时**不能**停录这个 backdrop。
                                // 洞的尺寸 animW = 屏宽 × cardScale，而主内容也按 cardScale 缩放，
                                // 因此洞恰好框住整个缩放后的主界面——顶栏/底栏全在洞内可见，
                                // 停录会让它们的玻璃层采样到空内容，直接表现为"模糊消失"。
                                .liquidGlassLayerBackdrop(liquidGlassBackdrop)
                        ) {
                            if (!isShiftMode) {
                                // 始终渲染所有 tab，用 alpha 控制显隐，避免切换时重建导致延迟
                                // zIndex 确保当前 tab 在最上层接收事件
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .zIndex(if (selectedTab == 0) 2f else 0f)
                                            .graphicsLayer { alpha = if (selectedTab == 0) 1f else 0f }
                                            // 未选中的 tab 保留组合/测量（切页不重建、顶栏高度不跳变），
                                            // 但跳过绘制：3 个 tab 同时绘制会让每帧内容绘制量变成 3 倍，
                                            // 且每一次 backdrop 录制都会把它们全部再画一遍。
                                            .drawWithContent { if (selectedTab == 0) drawContent() }
                                    ) {
                                        TodayScreen(
                                            viewModel = viewModel,
                                            settingsViewModel = settingsViewModel,
                                            hiddenCourseIds = hiddenCourseIds,
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
                                            onScrollYChanged = { todayScrollY = it },
                                            settingsScrollBehavior = todayScrollBehavior,
                                            onSelectedDayChanged = { todaySelectedDayOfWeek = it },
                                            onSelectedDateChanged = { todayIsToday = it },
                                            scrollToTodayTrigger = scrollToTodayTrigger,
                                            jumpToDateTrigger = todayJumpToDateTrigger,
                                            onJumpToDateProcessed = { todayJumpToDateTrigger = 0 },
                                            wallpaperBitmap = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBitmap else wallpaperBitmap,
                                            wallpaperOffset = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset,
                                            wallpaperScale = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale,
                                            wallpaperBrightness = displayAppearance.wallpaperBrightness,
                                            cardBlurRadius = displayAppearance.cardBlurRadius,
                                            cardRefraction = displayAppearance.cardRefraction,
                                            cardAlpha = displayAppearance.cardAlpha,
                                            wallpaperBlur = displayAppearance.wallpaperBlur,
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                            showClassroom = displayAppearance.showClassroom,
                                            showTeacher = displayAppearance.showTeacher,
                                            externalListState = todayListState,
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .zIndex(if (selectedTab == 1) 2f else 0f)
                                            .graphicsLayer { alpha = if (selectedTab == 1) 1f else 0f }
                                            .drawWithContent { if (selectedTab == 1) drawContent() }
                                    ) {
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
                                            onEmptyLongPress = { day, section, centerX, cellTopY, width, height ->
                                                // 若课程快捷菜单开着，先关掉再开空白格菜单
                                                shortcutMenuCourse = null
                                                emptyCellMenuTarget = day to section
                                                shortcutMenuVisible = true
                                                // 菜单按格子左上角定位（与课程菜单同一套偏移公式）
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
                                                // left/top 现在是卡片正中心绝对坐标，浮层按中心对齐使用
                                                draggedCardPosition = Offset(left, top)
                                                draggedCardOffset = Offset.Zero
                                                draggedCardSize = Offset(width, height)
                                                draggedCardBackdrop = backdrop
                                                shortcutMenuCourse = course
                                                emptyCellMenuTarget = null
                                                shortcutMenuVisible = true
                                                // 快捷菜单仍按左上角定位，把中心点转回左上角
                                                shortcutMenuPosition =
                                                    Offset(left - width / 2f, top - height / 2f)
                                                shortcutMenuAnchorWidth = width
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
                                                    pendingDropTarget =
                                                        computeDropTarget(
                                                            centerX,
                                                            firstSectionCenterY
                                                        )
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
                                                    val sameSlot =
                                                        source.dayOfWeek == target.first &&
                                                                source.startSection == targetStart &&
                                                                source.endSection == targetEnd
                                                    if (!sameSlot) {
                                                        // 检查目标位置该周是否有冲突课程（仅算本周活跃的课）
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
                                                            // 空位：移动并播放吸附动画
                                                            viewModel.moveCourseForWeek(
                                                                source.id,
                                                                week,
                                                                target.first,
                                                                targetStart,
                                                                targetEnd
                                                            )
                                                            // 单周调课可能拆分/合并出新 id，
                                                            // 按目标位重新收集并隐藏，避免真实卡片与浮层叠影
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
                                                                    sourceCourses.map { it.id }
                                                                        .toSet()
                                                                coroutineScope.launch {
                                                                    delay(350.milliseconds)
                                                                    animateInCourseIds = emptySet()
                                                                }
                                                            }
                                                        } else {
                                                            // 有课：暂存冲突信息，弹出对话框；浮层不闪退，
                                                            // 连贯移到目标卡上方并上下浮动等用户选择
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
                                                                    floatingOffsetX.snapTo(
                                                                        draggedCardOffset.x
                                                                    )
                                                                    floatingOffsetY.snapTo(
                                                                        draggedCardOffset.y
                                                                    )
                                                                    val sectionHPx =
                                                                        gridGeometry?.sectionHeightPx
                                                                            ?: 0f
                                                                    val floatH =
                                                                        (sectionSpan + 1) * sectionHPx
                                                                    val gap = with(density) {
                                                                        10.dp.toPx()
                                                                    }
                                                                    // 悬在目标卡上方：中心再上移一整张浮层高度 + 间距
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
                                                                        floatingOffsetX.animateTo(
                                                                            hoverOffsetX,
                                                                            tween(
                                                                                durationMillis = 420,
                                                                                easing = settleEase
                                                                            )
                                                                        )
                                                                    }
                                                                    val jobY = launch {
                                                                        floatingOffsetY.animateTo(
                                                                            hoverOffsetY,
                                                                            tween(
                                                                                durationMillis = 420,
                                                                                easing = settleEase
                                                                            )
                                                                        )
                                                                    }
                                                                    val jobScale = launch {
                                                                        floatingScale.animateTo(
                                                                            1.04f,
                                                                            tween(durationMillis = 420)
                                                                        )
                                                                    }
                                                                    jobX.join(); jobY.join(); jobScale.join()
                                                                    // 上下浮动，表示仍在「拿着」这张卡
                                                                    val bobAmp = with(density) {
                                                                        4.dp.toPx()
                                                                    }
                                                                    conflictHoverBobJob = launch {
                                                                        while (true) {
                                                                            floatingOffsetY.animateTo(
                                                                                hoverOffsetY + bobAmp,
                                                                                tween(
                                                                                    durationMillis = 1400,
                                                                                    easing = FastOutSlowInEasing
                                                                                )
                                                                            )
                                                                            floatingOffsetY.animateTo(
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
                                                                // 无网格几何时退回旧逻辑：缩回后隐藏
                                                                coroutineScope.launch {
                                                                    floatingScale.animateTo(
                                                                        1f,
                                                                        tween(durationMillis = 180)
                                                                    )
                                                                    delay(180.milliseconds)
                                                                    isDraggingCard = false
                                                                    floatingCardVisible = false
                                                                    draggingCourseIds = emptySet()
                                                                    draggedCardOffset = Offset.Zero
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
                                            isWallpaperEditing = isWindowCutoutActive,
                                            onWallpaperOffsetChange = { wallpaperOffset = it },
                                            onWallpaperScaleChange = { wallpaperScale = it },
                                            appearance = displayAppearance,
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                            onGridGeometryChange = { geom -> gridGeometry = geom },
                                            dropHighlight = run {
                                                // 空白格长按菜单打开时高亮目标格
                                                val emptyTarget = emptyCellMenuTarget
                                                if (emptyTarget != null && shortcutMenuVisible) {
                                                    emptyTarget.first to (emptyTarget.second..emptyTarget.second)
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
                                            scheduleScrollBehavior = scheduleScrollBehavior,
                                            paddingValues = paddingValues,
                                            externalScrollState = scheduleScrollState,
                                            externalShowCourseDetail = scheduleShowCourseDetail,
                                            externalSelectedCourse = scheduleSelectedCourse,
                                            externalSelectedCourses = scheduleSelectedCourses
                                        )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .zIndex(if (selectedTab == 2) 2f else 0f)
                                            .graphicsLayer { alpha = if (selectedTab == 2) 1f else 0f }
                                            .drawWithContent { if (selectedTab == 2) drawContent() }
                                    ) {
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
                                            onScrollYChanged = { settingsScrollY = it },
                                            settingsScrollBehavior = settingsScrollBehavior,
                                            activeSecondaryActivity = activeSecondaryActivity,
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                        )
                                    }
                                }
                            } else {
                                when (selectedTab) {
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
                                        onScrollYChanged = { settingsScrollY = it },
                                        settingsScrollBehavior = settingsScrollBehavior,
                                        activeSecondaryActivity = activeSecondaryActivity,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                    )
                                }
                            }
                        }
                    }
                    // 分享导入确认弹窗（必须在 Scaffold 内部）
                    ShareImportDialog(
                        activity = activity,
                        shareIntentVersion = activity?.shareIntentVersion ?: 0,
                        courseViewModel = viewModel,
                        scheduleViewModel = scheduleViewModel,
                        settingsViewModel = settingsViewModel,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                    )

                    UpdateDialog(liquidGlassBackdrop = liquidGlassBackdrop)

                    // 跳转周数弹窗（提升到 MainActivity，排班/课程表均可用）
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

                    // 添加课程对话框（始终跟随应用主题，不受壁纸强制主题影响）
                    val appDialogDark = rememberAppSettingDark()
                    val appDialogController = remember(appDialogDark) {
                        ThemeController(if (appDialogDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
                    }
                    MiuixTheme(controller = appDialogController) {
                        CompositionLocalProvider(LocalForcedDarkTheme provides null) {
                            AddCourseDialog(
                                show = showAddDialog,
                                course = editingCourse,
                                selectedDay = viewModel.selectedDay.collectAsState().value,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                totalWeeks = totalWeeks,
                                totalSections = totalSections,
                                defaultStartSection = editingStartSection,
                                defaultEndSection = editingEndSection,
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
                        viewModel = viewModel,
                        currentWeek = currentViewingWeek,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onPaste = { allWeeks ->
                            val meta = copiedCourseForPaste
                            val target = pasteRangeTarget
                            if (meta != null && target != null) {
                                val (day, section) = target
                                val span = (meta.endSection - meta.startSection).coerceAtLeast(0)
                                val endSection = section + span
                                // 「当前周」= 正在浏览的周，不是日历上的 currentWeek
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
                                            // 全部周：保留原课程的周次设置
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
                                            // 当前周：仅粘贴到正在浏览的这一周
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
                                        // 直线飞到目标格，前段快放大/后段快缩小，落地后再写入课程
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
                            // 取消：复用粘贴动画节奏，飞回原位
                            flyFloatingCardHome()
                        },
                        onOverwriteResolved = {
                            showRescheduleConflictDialog = false
                            pendingConflictCourse = null
                            // 覆盖：先隐藏目标位课程，再复用粘贴动画飞到目标格落地
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
                            // 交换：先隐藏两侧课程，双浮层同时交叉飞行
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
                    // 后端公告弹窗：启动时拉取，未读则在 OverlayDialog 中展示，仅一个「完成」按钮。
                    // 弹窗节点需常驻组合树、仅通过 show 控制显隐，才能让退出动画在 LaunchedEffect(show)
                    // 观察到 false 后正常播放；若用条件渲染直接移除节点，动画会被一并销毁、弹窗瞬间消失。
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
            // 始终用 MiuixTheme 包裹脚手架，保持组合结构恒定；
            // 有壁纸时 controller 跟随壁纸强制主题，无壁纸时跟随应用设置。
            // 结构恒定可避免 tab 切换深浅变化时底栏被重建导致滑块动画丢失。
            val effectiveForcedDark = if (captureThemeActive) captureThemeIsDark else forcedDark
            val effectiveDark = effectiveForcedDark ?: appSettingDark
            val pageController = remember(effectiveDark) {
                ThemeController(if (effectiveDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
            }
            MiuixTheme(controller = pageController) {
                CompositionLocalProvider(LocalForcedDarkTheme provides effectiveForcedDark) {
                    scaffoldContent()
                }
            }
            // 课程详情动画期间：用静态快照替代实际内容渲染，降低性能负载。
            // 快照层也要拦截触摸：showDetail 已 false、真实课表刚重组、快照尚未清除的短暂窗口里，
            // 否则点击会直接落到课程卡片上。
            if (mainContentSnapshot != null) {
                Image(
                    bitmap = mainContentSnapshot!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().consumeAllTouches(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        // 拖拽课程卡片浮层（退出动画期间仍保持渲染，直到 scale 回到 1f 才移除并让原卡片显现）
        // 有壁纸时跟随壁纸主题，无壁纸时跟随应用设置
        val overlayEffectiveForcedDark = if (captureThemeActive) captureThemeIsDark else forcedDark
        val overlayEffectiveDark = overlayEffectiveForcedDark ?: appSettingDark
        val overlayPageController = remember(overlayEffectiveDark) {
            ThemeController(if (overlayEffectiveDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        }
        MiuixTheme(controller = overlayPageController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides overlayEffectiveForcedDark) {
                if (floatingCardVisible) {
                    val course = draggedCardCourse
                    if (course != null) {
                        android.util.Log.d(
                            "FloatRender",
                            "render course=${course.name}, sec=${course.startSection}-${course.endSection}, draggedCardSize=${draggedCardSize}"
                        )
                        // draggedCardPosition 为卡片正中心绝对坐标，浮层按中心对齐：offset = 中心 - 半宽
                        // 吸附期间使用 floatingOffsetAnim 替代 draggedCardOffset，实现从当前位置到目标位置的动画
                        val currentOffsetX =
                            if (isSnapping) floatingOffsetX.value else draggedCardOffset.x
                        val currentOffsetY =
                            if (isSnapping) floatingOffsetY.value else draggedCardOffset.y
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
                        LaunchedEffect(floatingCardVisible, isPasteFlight) {
                            // 粘贴飞行自行控制缩放，跳过长按入场 0.94→1.04
                            if (floatingCardVisible && !isPasteFlight) {
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
                                // 粘贴飞行强制本周样式，避免源课不在当前周时飞出灰卡
                                isCurrentWeek = if (isPasteFlight) true else course.isActiveInWeek(draggedWeek),
                                wallpaperBackdrop = if (wallpaperBitmap != null) liquidGlassBackdrop else null,
                                cardBlurRadius = displayAppearance.cardBlurRadius,
                                cardAlpha = displayAppearance.cardAlpha,
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
                // 交换第二浮层：被交换的目标课，与源浮层同时交叉飞行
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
        // 快捷菜单：点击外部关闭（先触发退出动画，动画结束再清空状态）
        if (shortcutMenuCourse != null || emptyCellMenuTarget != null) {
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
                            emptyCellMenuTarget = null
                        }
                    }
            )
        }
        // 快捷菜单浮层：课程长按（编辑/删除/复制）或空白格长按（粘贴/添加）共用
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
                val emptyDay = activeEmptyTarget?.first ?: -1
                val emptySection = activeEmptyTarget?.second ?: -1
                buildList {
                    if (copiedCourseForPaste != null && activeEmptyTarget != null) {
                        add(
                            ShortcutMenuItem(
                                icon = MiuixIcons.Paste,
                                label = "粘贴",
                                onClick = {
                                    // 先弹粘贴范围弹窗，用户选择「全部周」或「当前周」后再执行粘贴
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
                                    viewModel.showAddDialog(emptyDay, emptySection)
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
                    // 菜单 layout 含 ShadowPadding(12dp)，左移 12dp 使可见左边缘与卡片左边缘对齐
                    x = with(density) { shortcutMenuPosition.x.toDp() - 12.dp },
                    y = with(density) { (shortcutMenuPosition.y - shortcutMenuSize.height).toDp() + 6.dp }
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
        // LiquidGlass 更多菜单（有壁纸时主题跟随壁纸强制主题，无壁纸时跟随应用设置）
        val menuForcedDark = forcedDark
        val menuDark = menuForcedDark ?: appSettingDark
        val menuController = remember(menuDark) {
            ThemeController(if (menuDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        }
        MiuixTheme(controller = menuController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides menuForcedDark) {
                MorePopupMenus(
                    showMorePopup = showMorePopup,
                    onMorePopupDismiss = { showMorePopup = false },
                    showTodayMorePopup = showTodayMorePopup,
                    onTodayMorePopupDismiss = { showTodayMorePopup = false },
                    morePopupFraction = morePopupFraction,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    isShiftMode = isShiftMode,
                    onJumpWeek = { viewModel.showJumpWeekDialog() },
                    onCourseManage = {
                        val intent = Intent(context, CourseManageActivity::class.java)
                        context.startActivity(intent)
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
                    // 不在此恢复主界面内容：动画期间主界面保持被编辑后的实时状态，
                    // 待退出动画结束（快照刚要消失）时由 LaunchedEffect(isCustomizeExiting) 统一恢复修改前。
                    // 仅锁定原搭配主题，避免动画期间主题跟随被编辑过的壁纸
                    captureThemeActive = true
                    captureThemeIsDark = originalWallpaperIsLight?.let { !it }
                    // 复用进入时的那张快照：从开洞大小放大回全屏，盖住页面放大淡出与主内容回退。
                    // 快照始终放在 MainActivity 本层（覆盖层），外观页面自身仅做与应用时一致的「放大淡出」动画。
                    customizeCoverActive = true
                    customizeCoverScale.stop()
                    customizeCoverScale.snapTo(cutoutMainScale.value)
                    // 快照从透明淡入，配合从开洞处放大，盖住页面淡出
                    customizeCoverAlpha.stop()
                    customizeCoverAlpha.snapTo(0f)
                    // 外观页面自身执行与应用时一致的「放大淡出」动画，由 LaunchedEffect(isCustomizeExiting) 统一驱动
                    customizeExitScale.stop()
                    customizeExitScale.snapTo(cutoutMainScale.value)
                    customizeExitAlpha.stop()
                    customizeExitAlpha.snapTo(1f)
                    isCustomizeExiting = true
                    // 关闭/复位统一由 LaunchedEffect(isCustomizeExiting) 动画结束后处理
                    // 主界面内容与组合对象仅在动画结束（快照刚要消失）时才恢复修改前状态
                    windowInsetsController?.isAppearanceLightStatusBars = true
                    windowInsetsController?.isAppearanceLightNavigationBars = true
                }
            }
            val applyCustomize: () -> Unit = {
                coroutineScope.launch {
                    // 持久化当前搭配到磁盘（在 IO 线程异步执行，不阻塞 UI）
                    val bitmap = wallpaperBitmap
                    val combId = combinations.getOrNull(currentCombinationIndex)?.id ?: 0L
                    // 当前搭配的壁纸测光结果（选择壁纸时已计算），用于持久化 + 主题锁定
                    val isLight = combinations.getOrNull(currentCombinationIndex)?.wallpaperIsLight
                    // 截取当前 MainActivity 快照（包含课表+新壁纸）作为卡片预览（仅内存，不持久化）
                    val capturedSnapshot = captureMainContentBitmap()
                    val saveJob = launch(Dispatchers.IO) {
                        // 合并为一次磁盘提交：原本这里是 16 次独立的 prefs.edit
                        wallpaperRepository.batchEdit {
                            if (bitmap != null) {
                                wallpaperRepository.saveCombinationWallpaper(combId, bitmap)
                            } else {
                                // 清除壁纸：删除磁盘文件，否则旧壁纸会在再次应用/重启后恢复
                                wallpaperRepository.clearCombinationWallpaper(combId)
                            }
                            wallpaperRepository.saveCombinationState(
                                combId,
                                wallpaperOffset.x,
                                wallpaperOffset.y,
                                wallpaperScale
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
                                // 外观字段已在编辑期经 onAppearanceChange 整体写入 combinations，此处不再重复回写
                                wallpaperIsLight = isLight
                            )
                        }
                    }
                    // 更新已保存快照，避免退出时回退
                    savedWallpaperBitmap = bitmap
                    savedWallpaperOffset = wallpaperOffset
                    savedWallpaperScale = wallpaperScale
                    savedAppearance = currentAppearance()
                    saveJob.join()
                    isApplyingCustomize = true
                    // 从当前开洞大小（0.75）开始放大到全屏，而非从卡片预览大小（0.65）
                    customizeExitScale.snapTo(cutoutMainScale.value)
                    customizeExitAlpha.snapTo(1f)
                    isCustomizeExiting = true
                }
            }
            // 搭配页容器：进出场动画由全屏快照覆盖层处理，这里页面自身仅退出时淡出
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
                        // 单一数据源：整体写入 combinations，去掉 appearance 双写冗余
                        applyAppearance(newAppearance)
                    },
                    hasWallpaper = wallpaperBitmap != null,
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

        // 全屏快照覆盖层：进入开洞时盖住开洞过渡、退出-取消时盖住回退过程。
        // 复用进入时捕获的 customizeSnapshot，动画只作用于这一层，避免叠加闪烁。
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
                        // 以开洞中心为缩放锚点：进入时快照落进开洞，退出时从开洞放大回全屏
                        transformOrigin = TransformOrigin(0.5f, cutoutCenterYRatio)
                    }
                    // 给快照裁切屏幕圆角，与主界面开洞圆角一致
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
                    // 取消退出时：快照从开洞处淡入放大回全屏，与页面放大淡出同步
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
                    // 取消退出时淡出外观页面；应用时页面直接放大到全屏，动画结束后消失
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
                    // 主界面从开洞大小（0.75）放大到全屏（应用与取消共用）
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
                // 动画完成，真正关闭
                isCustomizeExiting = false
                showCustomizePage = false
                customizeSnapshot = null
                customizeCoverActive = false
                isWindowCutoutActive = false
                // 动画结束后 currentCombinationIndex 已恢复原搭配，forcedDark 自然接管，释放 captureTheme
                captureThemeActive = false
                captureThemeIsDark = null
                if (!isApplyingCustomize) {
                    // 退出（非应用）：恢复 live 变量，并还原 combinations 列表中被 callback 修改的条目
                    wallpaperBitmap = originalWallpaperBitmap
                    wallpaperOffset = originalWallpaperOffset
                    wallpaperScale = originalWallpaperScale
                    currentCombinationIndex = originalCombinationIndex
                    // 整体还原原始搭配对象，覆盖 onWallpaperOffsetChange 等编辑回调修改的所有字段
                    val restoreIdx = originalCombinationIndex
                    val restored = originalCombination
                    if (restoreIdx in combinations.indices && restored != null) {
                        combinations = combinations.toMutableList().also {
                            it[restoreIdx] = restored
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
                    // 延迟清除快照，让实际内容先重组完成，避免闪烁
                    coroutineScope.launch {
                        delay(16.milliseconds)
                        mainContentSnapshot = null
                    }
                }
            )
        }
        // 切换课表页：整段是一个「卡片 ↔ 全屏」的形变动画，进度 p = switchAnimProgress。
        //   p=1 覆盖层铺满全屏（主内容的样子），p=0 缩到卡片大小（切换页里那张卡）。
        //   进入 p:1→0（主内容缩成卡片，切换页从放大模糊态回正），退出 p:0→1。
        //   switchAnimForward = 方向，switchCapturingSnapshot = 截图期间用 alpha=0 藏起本页。
        if (showSwitchSchedule) {
            val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            val screenWidth = windowInfo.containerSize.width.toFloat()
            val screenHeight = windowInfo.containerSize.height.toFloat()
            val p = switchAnimProgress.value
            val switchPageScale = remember { Animatable(1f) }
            // 初值 0f：首帧（整页首次组合，最贵的一帧）不挂 RenderEffect
            val switchPageBlur = remember { Animatable(0f) }
            // RenderEffect 每帧新建会同时产生 Java 与 native 对象，按 0.25px 量化缓存复用
            val blurEffectCache = remember { BlurEffectCache() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // blur / scale / alpha 合成一层，少一次离屏合成
                    .graphicsLayer {
                        alpha = if (switchCapturingSnapshot) 0f else 1f
                        scaleX = switchPageScale.value
                        scaleY = switchPageScale.value
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
                                switchReloadJob?.join()   // 等异步重载，否则网格还是旧课表
                                switchReloadJob = null
                                // 切换页在独立图层，录不进主内容快照，无需为截图隐藏它
                                mainContentSnapshot = null
                                // withFrameNanos 在帧开始返回、装的是上一帧，需多等一帧才录得到新课表。
                                // 只等 1 帧：深层重组若没画完，仍会录到旧课表（已知隐患）
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
                            switchOverlayActive = false
                            switchCardSnapshot = null
                            switchCardBounds = null
                            switchCurrentCardBounds = null
                            mainContentSnapshot = null
                            switchAnimRunning = false
                        }
                    },
                    onScheduleChanged = {
                        switchReloadJob = viewModel.reloadCourses()
                        settingsViewModel.refreshSettings()
                        scheduleChanged = true
                        // 切换页是直接改 repository 的，必须把 ScheduleViewModel 拉回一致，
                        // 否则它持有的课表列表 / 当前课表会一直停留在旧值。
                        // 摘要要反序列化全部课表的课程 JSON，放 IO 线程，别压在动画上。
                        coroutineScope.launch(Dispatchers.IO) {
                            scheduleViewModel.refreshScheduleList()
                        }
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
                                switchReloadJob?.join()   // 等异步重载，否则网格还是旧课表
                                switchReloadJob = null
                                // 切换页在独立图层，录不进主内容快照，无需为截图隐藏它
                                mainContentSnapshot = null
                                // withFrameNanos 在帧开始返回、装的是上一帧，需多等一帧才录得到新课表。
                                // 只等 1 帧：深层重组若没画完，仍会录到旧课表（已知隐患）
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
                            // 截图失败时 cardSnap 为 null，动画照常跑（覆盖层只是没有卡片位图），
                            // 不能因为一次 GPU 回读失败就让页面永远停在 alpha=0
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
                                switchPageScale.snapTo(1.08f)
                                switchPageBlur.snapTo(5f)
                                switchReturnBgScrim.snapTo(0.4f)
                                switchCapturingSnapshot = false
                                switchOverlayActive = true
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
            // 覆盖层：用 p 在卡片矩形 ↔ 全屏之间插值出位置/尺寸/圆角，
            // 里面叠卡片快照（p 越小越实）与主内容快照（p 越大越实）交叉淡入淡出
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
                        .clip(ContinuousRoundedRectangle(cRadius))
                        .background(MiuixTheme.colorScheme.background)
                ) {
                    // 卡片快照（淡出，保持原始大小）
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

        // 排班模式加载遮罩
        ShiftLoadingOverlay(
            show = showShiftLoading,
            onShiftReady = {
                if (isExitingShift) {
                    shiftViewModel.exitShiftMode()
                    selectedTab = 0
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
) {
    if (liquidGlassBackdrop == null) return
    val isTabletLiquidGlass = navBarStyle == "rail"
    val dayOfWeekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val dayOfWeekName = if (currentDayOfWeek in 1..7) dayOfWeekNames[currentDayOfWeek - 1] else ""
    val titleText = if (isToday) "今天是$dayOfWeekName" else dayOfWeekName

    val buttonFraction = remember { Animatable(0f) }
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

    // 今日页顶栏始终渲染：隐藏时 alpha=0 且不渲染按钮，但仍被测量 ——
    // 这样 todayScrollBehavior.currentHeightPx 启动即就位，切到今日页时内容顶部偏移不会慢一帧。
    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
        modifier = Modifier.graphicsLayer { alpha = if (visible) 1f else 0f },
    ) {
        CollapsibleTopAppBar(
            title = titleText,
            largeTitle = titleText,
            modifier = Modifier.zIndex(1f),
            scrollBehavior = scrollBehavior,
            startAction = if (!visible) {
                null
            } else if (isTabletLiquidGlass) {
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
                            iconOffset = DpOffset(x = 0.dp, y = (-1).dp),
                            iconSize = 24.dp,
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                        )
                    }
                }
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
