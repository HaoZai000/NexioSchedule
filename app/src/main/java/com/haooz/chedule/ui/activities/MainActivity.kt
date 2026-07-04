/** 主页面 - 应用入口 Activity */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.Course
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.screens.CourseDetailScreen
import com.haooz.chedule.ui.screens.MainScheduleScreen
import com.haooz.chedule.ui.screens.SettingsScreen
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TodayScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {
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

    fun clearShareIntent() {
        shareIntentUri = null
        shareIntentAction = null
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val maxSize = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxSize) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
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

        // 自动检查更新：每天进入应用时检查一次
        val prefs = getSharedPreferences("update_settings", MODE_PRIVATE)
        val autoCheck = prefs.getBoolean("auto_check_update", true)
        if (autoCheck) {
            val lastCheckDate = prefs.getString("last_check_date", "")
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            if (lastCheckDate != today) {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val response = java.net.URL("https://gitee.com/api/v5/repos/com_haooz_account/hyper_schedule/releases/latest").readText()
                        val json = org.json.JSONObject(response)
                        val tagName = json.getString("tag_name")
                        val currentVersion = try {
                            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                        } catch (_: Exception) { "" }
                        val tagVer = tagName.removePrefix("v").substringBefore("-")
                        val appVer = currentVersion.removePrefix("v").substringBefore("-")
                        val hasUpdate = isNewerVersion(tagVer, appVer)
                        prefs.edit()
                            .putString("last_check_date", today)
                            .putBoolean("has_update", hasUpdate)
                            .apply()
                        if (hasUpdate) {
                            var apkUrl = ""
                            val assets = json.optJSONArray("assets")
                            if (assets != null) {
                                for (i in 0 until assets.length()) {
                                    val a = assets.getJSONObject(i)
                                    if (a.getString("name").endsWith(".apk")) {
                                        apkUrl = a.getString("browser_download_url"); break
                                    }
                                }
                            }
                            prefs.edit()
                                .putString("latest_url", json.getString("html_url"))
                                .putString("latest_tag", tagName)
                                .putString("latest_name", json.getString("name"))
                                .putString("latest_body", json.optString("body", ""))
                                .putString("latest_apk_url", apkUrl)
                                .putString("latest_date", json.getString("created_at"))
                                .apply()
                        }
                    } catch (_: Exception) {}
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
    val viewModel: CourseViewModel = viewModel()
    val scheduleViewModel: ScheduleViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val shiftViewModel: ShiftViewModel = viewModel()
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()
    var selectedTab by remember { mutableIntStateOf(if (defaultHomepage == "今日") 0 else 1) }
    var showShiftLoading by remember { mutableStateOf(false) }
    var isExitingShift by remember { mutableStateOf(false) }
    var shiftModeInitialized by remember { mutableStateOf(false) }

    // 初始化 SyncManager 并检查云端更新
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val syncManager = com.haooz.chedule.data.SyncManager.getInstance(context)
        val repository = com.haooz.chedule.data.CourseRepository(context)
        val webDavManager = com.haooz.chedule.data.WebDavManager(context)
        syncManager.start(repository, webDavManager)
        // 启动时检查云端是否有更新
        syncManager.checkAndSyncOnStart()
    }

    // 更新弹窗状态
    val updatePrefs = remember { context.getSharedPreferences("update_settings", Context.MODE_PRIVATE) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateTagName by remember { mutableStateOf("") }
    var updateBody by remember { mutableStateOf("") }
    var hasDownloadedApk by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1400)
        val hasUpdate = updatePrefs.getBoolean("has_update", false)
        val updateReminder = updatePrefs.getBoolean("update_reminder", true)
        val tag = updatePrefs.getString("latest_tag", "") ?: ""
        val body = updatePrefs.getString("latest_body", "") ?: ""
        if (hasUpdate && updateReminder && tag.isNotBlank()) {
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) { "" }
            val latestVer = tag.removePrefix("v").substringBefore("-")
            val localVer = currentVersion.removePrefix("v").substringBefore("-")
            val remoteParts = latestVer.split(".").mapNotNull { it.toIntOrNull() }
            val localParts = localVer.split(".").mapNotNull { it.toIntOrNull() }
            val maxSize = maxOf(remoteParts.size, localParts.size)
            var actuallyNewer = false
            for (i in 0 until maxSize) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) { actuallyNewer = true; break }
                if (r < l) { actuallyNewer = false; break }
            }
            if (actuallyNewer) {
                updateTagName = tag
                updateBody = body
                val apkFile = java.io.File(context.filesDir, "update-$tag.apk")
                hasDownloadedApk = apkFile.exists() && apkFile.length() > 0
                kotlinx.coroutines.delay(800.milliseconds)
                showUpdateDialog = true
            } else {
                updatePrefs.edit().putBoolean("has_update", false).apply()
            }
        }
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val blurColors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(
                ComposeColor.Black.copy(alpha = 0.7f),
                BlurBlendMode.SrcOver
            )
            else BlendColorEntry(ComposeColor.White.copy(alpha = 0.7f), BlurBlendMode.SrcOver)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val showWeekendDays by settingsViewModel.showWeekendDays.collectAsState()
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
    var screenSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var mainContentSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var switchScreenSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var switchCardSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var switchCardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var switchCurrentCardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var switchContentRootX by remember { mutableFloatStateOf(0f) }
    var switchContentRootY by remember { mutableFloatStateOf(0f) }
    var switchAnimJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var switchAnimForward by remember { mutableStateOf(false) }
    var switchAnimRunning by remember { mutableStateOf(false) }
    val switchAnimProgress = remember { Animatable(0f) }
    val backgroundScale = remember { Animatable(1f) }
    val switchReturnBgScrim = remember { Animatable(0f) }
    val screenGraphicsLayer = rememberGraphicsLayer()

    val hapticFeedback = LocalHapticFeedback.current

    val pagerState = rememberPagerState(
        initialPage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0)),
        pageCount = { totalWeeks }
    )

    LaunchedEffect(isShiftMode) {
        if (shiftModeInitialized) {
            selectedTab = 0
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
    val dayRange = if (showWeekendDays.isNotEmpty()) {
        (1..5) + showWeekendDays.filter { it in 6..7 }
    } else {
        (1..5).toList()
    }

    val currentViewingWeek = pagerState.currentPage + 1
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
    val activity = LocalActivity.current as? MainActivity
    val isInFreeformWindow = activity?.isInFreeformWindow ?: false

    val screenCornerRadius = remember(isInFreeformWindow) {
        if (isInFreeformWindow) {
            20f * density.density  // 小窗默认圆角 20dp
        } else {
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                val windowMetrics = windowManager.currentWindowMetrics
                val insets = windowMetrics.windowInsets
                @SuppressLint("WrongConstant")
                insets.getRoundedCorner(0)?.radius?.toFloat() ?: 0f
            } catch (_: Exception) { 0f }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    var showSwitchSchedule by remember { mutableStateOf(false) }
    var switchPendingReverse by remember { mutableStateOf(false) }
    var switchCapturingSnapshot by remember { mutableStateOf(false) }
    var scheduleChanged by remember { mutableStateOf(false) }

    // 处理从系统分享导入的JSON文件
    var showShareImportDialog by remember { mutableStateOf(false) }
    var shareImportData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var shareImportScheduleName by remember { mutableStateOf("") }


    // 监听 intentVersion 变化，每次有新 intent 都会触发
    LaunchedEffect(activity?.shareIntentVersion) {
        val uri = activity?.shareIntentUri
        val action = activity?.shareIntentAction
        if (uri != null && action != null) {
            activity.clearShareIntent()

            val intentData = when (action) {
                android.content.Intent.ACTION_VIEW -> uri
                android.content.Intent.ACTION_SEND -> uri
                else -> null
            }

            intentData?.let { shareUri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(shareUri)
                    val text = inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                    inputStream?.close()

                    if (text.isNotBlank()) {
                        val fileName = shareUri.lastPathSegment?.lowercase() ?: ""
                        val isIcs = fileName.endsWith(".ics")

                        val (success, message, data) = if (isIcs) {
                            com.haooz.chedule.ui.screens.parseIcsFile(text)
                        } else {
                            com.haooz.chedule.ui.screens.parseFullScheduleJson(text)
                        }

                        if (success && data != null) {
                            val scheduleName = if (isIcs) "ICS导入课表" else (data["schedule_name"] as? String) ?: "导入的课表"
                            shareImportData = data
                            shareImportScheduleName = scheduleName
                            showShareImportDialog = true
                        } else {
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "导入失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isViewingCurrentWeek = currentViewingWeek == currentWeek

    val animationState = remember(isInFreeformWindow) {
        derivedStateOf {
            val progress = backgroundScale.value
            val blurProg = ((1f - progress) / (1f - 0.92f)).coerceIn(0f, 1f)
            val blurR = (blurProg * 5f).coerceIn(0f, 5f)
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
    Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        val isDetailActive = showDetail && screenSnapshot != null && !showCourseDetailPopup
        val shouldRecordGL = !isDetailActive
        val isEntryAnimating = showSwitchSchedule && switchAnimForward && switchAnimRunning
        val mainContentAlpha = when {
            isDetailActive -> 0f
            showSwitchSchedule && switchScreenSnapshot != null -> 0f
            else -> 1f
        }
        // 主内容（带缩放和裁切）
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                .then(
                    if (!isDetailActive && !showSwitchSchedule) {
                        val blurR = animationState.value.first
                        if (blurR > 0.01f) {
                            Modifier.graphicsLayer {
                                val px = blurR * density.density
                                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                    px, px, android.graphics.Shader.TileMode.CLAMP
                                ).asComposeRenderEffect()
                            }
                        } else Modifier
                    } else Modifier
                )
                .graphicsLayer {
                    scaleX = if (!isDetailActive && !showSwitchSchedule) animationState.value.third
                    else if (isEntryAnimating) 1f
                    else 1f
                    scaleY = if (!isDetailActive && !showSwitchSchedule) animationState.value.third
                    else if (isEntryAnimating) 1f
                    else 1f
                    alpha = mainContentAlpha
                }
                .clip(RoundedRectangle(animationState.value.second))
        ) {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier
                            .height(74.dp)
                            .textureBlur(
                                backdrop = backdrop,
                                shape = RectangleShape,
                                colors = blurColors
                            ),
                        mode = NavigationBarDisplayMode.IconAndText,
                        color = ComposeColor.Transparent
                    ) {
                        if (!isShiftMode) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    selectedTab = 0
                                },
                                icon = MiuixIcons.Album,
                                label = "今日"
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    selectedTab = 1
                                },
                                icon = MiuixIcons.Months,
                                label = "课程表"
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    selectedTab = 2
                                },
                                icon = MiuixIcons.Demibold.Settings,
                                label = "设置"
                            )
                        } else {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    selectedTab = 0
                                },
                                icon = MiuixIcons.Months,
                                label = "排班课表"
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    selectedTab = 1
                                },
                                icon = MiuixIcons.Demibold.Settings,
                                label = "设置"
                            )
                        }
                    }
                },
                topBar = {
                    if ((!isShiftMode && selectedTab == 1) || (isShiftMode && selectedTab == 0)) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((activity?.titleBarHeight ?: 56.dp) + 40.dp)
                                    .then(
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = RectangleShape,
                                            colors = topAppBarColors
                                        )
                                    )
                            )
                            Column {
                                SmallTopAppBar(
                                    title = "第${pagerState.currentPage + 1}周",
                                    color = ComposeColor.Transparent,
                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                        activity?.titleBarHeight =
                                            with(density) { coordinates.size.height.toDp() }
                                    },
                                    navigationIcon = {
                                        AnimatedVisibility(
                                            visible = !isViewingCurrentWeek,
                                            enter = fadeIn(animationSpec = tween(180)),
                                            exit = fadeOut(animationSpec = tween(120))
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val targetPage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
                                                        pagerState.animateScrollToPage(targetPage)
                                                    }
                                                },
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Medium.Reset,
                                                    contentDescription = "返回本周",
                                                    modifier = Modifier.size(25.dp)
                                                )
                                            }
                                        }
                                    },
                                    actions = {
                                        if (!isShiftMode) {
                                            IconButton(
                                                onClick = {
                                                    if (!showSwitchSchedule) {
                                                        coroutineScope.launch {
                                                            mainContentSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                                            switchPendingReverse = true
                                                            switchCapturingSnapshot = true
                                                            showSwitchSchedule = true
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.padding(end = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Normal.ConvertFile,
                                                    contentDescription = "课表切换",
                                                    modifier = Modifier.size(27.dp)
                                                )
                                            }
                                            val entry = remember {
                                                DropdownEntry(
                                                    items = listOf(
                                                        DropdownItem(
                                                            text = "跳转周数",
                                                            onClick = { viewModel.showJumpWeekDialog() }
                                                        )
                                                    )
                                                )
                                            }
                                            OverlayIconDropdownMenu(
                                                entry = entry,
                                                modifier = Modifier.padding(end = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.More,
                                                    contentDescription = "更多",
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Spacer(modifier = Modifier.width(36.dp))
                                    val dayNames =
                                        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                                    dayRange.forEach { dayOfWeek ->
                                        val index = dayOfWeek - 1
                                        val name = dayNames[index]
                                        val isToday =
                                            dayOfWeek == currentDayOfWeek && pagerState.currentPage + 1 == currentWeek
                                        Box(
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = name,
                                                    style = MiuixTheme.textStyles.footnote1.copy(
                                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    color = if (isToday) MiuixTheme.colorScheme.primary
                                                    else MiuixTheme.colorScheme.onSurface
                                                )
                                                if (weekDates.isNotEmpty() && index < weekDates.size) {
                                                    val dateText = weekDates[index].format(
                                                        DateTimeFormatter.ofPattern("MM/dd")
                                                    )
                                                    Text(
                                                        text = dateText,
                                                        style = MiuixTheme.textStyles.footnote2,
                                                        color = if (isToday) MiuixTheme.colorScheme.primary
                                                        else MiuixTheme.colorScheme.onSurfaceVariantActions
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
            ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                ) {
                    if (!isShiftMode) {
                        when (selectedTab) {
                            0 -> TodayScreen(
                                viewModel = viewModel,
                                settingsViewModel = settingsViewModel,
                                onCourseClick = { courses, left, top, width, height, _ ->
                                    detailCourses = courses
                                    detailCardLeft = left
                                    detailCardTop = top
                                    detailCardWidth = width
                                    detailCardHeight = height
                                    detailFromToday = true
                                    coroutineScope.launch {
                                        val fullSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                        screenSnapshot = fullSnapshot
                                        detailSnapshot = try {
                                            val x = left.toInt().coerceIn(0, fullSnapshot.width - 1)
                                            val y = top.toInt().coerceIn(0, fullSnapshot.height - 1)
                                            val w = width.toInt().coerceIn(1, fullSnapshot.width - x)
                                            val h = height.toInt().coerceIn(1, fullSnapshot.height - y)
                                            android.graphics.Bitmap.createBitmap(fullSnapshot, x, y, w, h)
                                        } catch (_: Exception) { null }
                                        showDetail = true
                                        backgroundScale.animateTo(0.92f, animationSpec = tween(520, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)))
                                    }
                                }
                            )
                            1 -> MainScheduleScreen(
                                viewModel = viewModel,
                                settingsViewModel = settingsViewModel,
                                pagerState = pagerState,
                                currentDayOfWeek = currentDayOfWeek,
                                dayRange = dayRange,
                                onCourseClick = { courses, left, top, width, height, _ ->
                                    detailCourses = courses
                                    detailCardLeft = left
                                    detailCardTop = top
                                    detailCardWidth = width
                                    detailCardHeight = height
                                    detailFromToday = false
                                    coroutineScope.launch {
                                        val fullSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                        screenSnapshot = fullSnapshot
                                        detailSnapshot = try {
                                            val x = left.toInt().coerceIn(0, fullSnapshot.width - 1)
                                            val y = top.toInt().coerceIn(0, fullSnapshot.height - 1)
                                            val w = width.toInt().coerceIn(1, fullSnapshot.width - x)
                                            val h = height.toInt().coerceIn(1, fullSnapshot.height - y)
                                            android.graphics.Bitmap.createBitmap(fullSnapshot, x, y, w, h)
                                        } catch (_: Exception) { null }
                                        showDetail = true
                                        backgroundScale.animateTo(0.92f, animationSpec = tween(520, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)))
                                    }
                                },
                                onPopupStateChange = { showCourseDetailPopup = it }
                            )
                            2 -> SettingsScreen(
                                viewModel = viewModel,
                                scheduleViewModel = scheduleViewModel,
                                settingsViewModel = settingsViewModel,
                                shiftViewModel = shiftViewModel,
                                onEnterShiftMode = {
                                    showShiftLoading = true
                                    isExitingShift = false
                                }
                            )
                        }
                    } else {
                        when (selectedTab) {
                            0 -> ShiftScheduleScreen(
                                viewModel = viewModel,
                                shiftViewModel = shiftViewModel,
                                currentDayOfWeek = currentDayOfWeek,
                                dayRange = dayRange,
                                pagerState = pagerState
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
                                }
                            )
                        }
                    }
                }
                // 分享导入确认弹窗（必须在 Scaffold 内部）
                OverlayDialog(
                    title = "导入课表",
                    summary = "是否导入课表「$shareImportScheduleName」？\n确定导入将创建一个新的课表",
                    show = showShareImportDialog,
                    outsideMargin = DpSize(17.dp, 12.dp),
                    onDismissRequest = {
                        showShareImportDialog = false
                        shareImportData = null
                        shareImportScheduleName = ""
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        top.yukonga.miuix.kmp.basic.TextField(
                            value = shareImportScheduleName,
                            onValueChange = { shareImportScheduleName = it },
                            label = "课表名称",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "取消",
                                onClick = {
                                    showShareImportDialog = false
                                    shareImportData = null
                                    shareImportScheduleName = ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "确定导入",
                                onClick = {
                                    if (shareImportScheduleName.isNotBlank() && shareImportData != null) {
                                        val (_, importMessage) = com.haooz.chedule.ui.screens.applyScheduleData(
                                            context,
                                            viewModel,
                                            scheduleViewModel,
                                            settingsViewModel,
                                            shareImportScheduleName,
                                            shareImportData!!
                                        )
                                        android.widget.Toast.makeText(context, importMessage, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    showShareImportDialog = false
                                    shareImportData = null
                                    shareImportScheduleName = ""
                                },
                                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 更新弹窗
                if (showUpdateDialog) {
                    OverlayDialog(
                        title = "发现新版本",
                        summary = "最新版本: $updateTagName",
                        show = showUpdateDialog,
                        outsideMargin = DpSize(17.dp, 12.dp),
                        onDismissRequest = { showUpdateDialog = false }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (updateBody.isNotBlank()) {
                                Text(
                                    text = updateBody.take(300),
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                top.yukonga.miuix.kmp.basic.TextButton(
                                    text = "稍后",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showUpdateDialog = false },
                                    modifier = Modifier.weight(1f)
                                )
                                top.yukonga.miuix.kmp.basic.Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        showUpdateDialog = false
                                        val intent = android.content.Intent(context, UpdateSettingsActivity::class.java)
                                        context.startActivity(intent)
                                    },
                                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary()
                                ) {
                                    Text(
                                        text = if (hasDownloadedApk) "安装" else "更新",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ComposeColor.White
                                    )
                                }
                            }
                        }
                    }
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
        // 课程详情页背景快照
        if (showDetail && screenSnapshot != null && !showCourseDetailPopup) {
            val s = animationState.value
            Image(
                bitmap = screenSnapshot!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (s.first > 0.01f) {
                            Modifier.graphicsLayer {
                                val px = s.first * density.density
                                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                    px, px, android.graphics.Shader.TileMode.CLAMP
                                ).asComposeRenderEffect()
                            }
                        } else Modifier
                    )
                    .graphicsLayer {
                        scaleX = s.third
                        scaleY = s.third
                    }
                    .clip(RoundedRectangle(s.second)),
                contentScale = ContentScale.Crop
            )
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
                        backgroundScale.animateTo(1f, animationSpec = tween(370, easing = CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)))
                    }
                },
                onBack = {
                    showDetail = false
                    screenSnapshot = null
                }
            )
        }
        // 切换课表页
        if (showSwitchSchedule) {
            val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            val screenWidth = windowInfo.containerSize.width.toFloat()
            val screenHeight = windowInfo.containerSize.height.toFloat()
            val p = switchAnimProgress.value
            val morphOpenEase = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
            val switchPageScale = remember { Animatable(1f) }
            val switchPageBlur = remember { Animatable(0f) }
            // 切换课表页始终渲染（底层，截取快照期间隐藏）
            Box(modifier = Modifier
                .fillMaxSize()
                .then(
                    if (switchPageBlur.value > 0.01f) {
                        Modifier.graphicsLayer {
                            val px = switchPageBlur.value * density.density
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                px, px, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    } else Modifier
                )
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
                                } catch (_: Exception) { mainContentSnapshot }
                            } else {
                                scheduleChanged = false
                            }
                            val currentBounds = switchCurrentCardBounds
                            val screenBitmap = switchPageBitmap ?: mainContentSnapshot
                            if (currentBounds != null && screenBitmap != null) {
                                switchScreenSnapshot = screenBitmap
                                switchCardBounds = currentBounds
                                switchCardSnapshot = try {
                                    val x = currentBounds.left.toInt().coerceIn(0, screenBitmap.width - 1)
                                    val y = currentBounds.top.toInt().coerceIn(0, screenBitmap.height - 1)
                                    val w = currentBounds.width.toInt().coerceIn(1, screenBitmap.width - x)
                                    val h = currentBounds.height.toInt().coerceIn(1, screenBitmap.height - y)
                                    android.graphics.Bitmap.createBitmap(screenBitmap, x, y, w, h)
                                } catch (_: Exception) { null }
                                val currentProgress = switchAnimProgress.value
                                val remainingDuration = ((1f - currentProgress) * 520).toInt().coerceAtLeast(1)
                                launch {
                                    switchPageScale.animateTo(1.08f, animationSpec = tween(remainingDuration, easing = morphOpenEase))
                                }
                                launch {
                                    switchPageBlur.animateTo(8f, animationSpec = tween(remainingDuration, easing = morphOpenEase))
                                }
                                switchAnimProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = remainingDuration,
                                        easing = morphOpenEase
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
                                } catch (_: Exception) { mainContentSnapshot }
                            }
                            switchScreenSnapshot = screenBitmap
                            switchCardSnapshot = cardBitmap
                            switchCardBounds = bounds
                            val currentProgress = switchAnimProgress.value
                            val remainingDuration = ((1f - currentProgress) * 520).toInt().coerceAtLeast(1)
                            launch {
                                switchPageScale.animateTo(1.08f, animationSpec = tween(remainingDuration, easing = morphOpenEase))
                            }
                            launch {
                                switchPageBlur.animateTo(8f, animationSpec = tween(remainingDuration, easing = morphOpenEase))
                            }
                            switchAnimProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = remainingDuration,
                                    easing = morphOpenEase
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
                                val h = cardBounds.height.toInt().coerceIn(1, screenBitmap.height - y)
                                android.graphics.Bitmap.createBitmap(screenBitmap, x, y, w, h)
                            } catch (_: Exception) { null }
                            switchAnimJob = coroutineScope.launch {
                                switchAnimProgress.snapTo(1f)
                                switchPageScale.snapTo(1.08f)
                                switchPageBlur.snapTo(8f)
                                switchReturnBgScrim.snapTo(0.4f)
                                switchCapturingSnapshot = false
                                switchScreenSnapshot = screenBitmap
                                switchCardBounds = cardBoundsInScreen
                                switchCardSnapshot = cardSnap
                                val remainingDuration = 370
                                val morphExitEase = CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)
                                launch {
                                    switchPageScale.animateTo(1f, animationSpec = tween(remainingDuration, easing = morphOpenEase))
                                }
                                launch {
                                    switchPageBlur.animateTo(0f, animationSpec = tween(remainingDuration, easing = morphOpenEase))
                                }
                                launch {
                                    switchReturnBgScrim.animateTo(0f, animationSpec = tween(remainingDuration, easing = morphExitEase))
                                }
                                switchAnimProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = remainingDuration,
                                        easing = CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)
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
                val endRadius = screenCornerRadius
                val cRadius = with(density) { (startRadius + (endRadius - startRadius) * p).toDp() }
                // 压暗遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDark) ComposeColor(0xFF2C2C2C).copy(alpha = (p * 0.5f).coerceIn(0f, 0.5f))
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
                kotlinx.coroutines.delay(100.milliseconds)
                if (isExitingShift) {
                    shiftViewModel.exitShiftMode()
                    selectedTab = 0
                } else {
                    shiftViewModel.enterShiftMode()
                }
                kotlinx.coroutines.delay(500.milliseconds)
                showShiftLoading = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(size = 30.dp,
                        strokeWidth = 2.8.dp,
                        orbitingDotSize = 3.2.dp)
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
