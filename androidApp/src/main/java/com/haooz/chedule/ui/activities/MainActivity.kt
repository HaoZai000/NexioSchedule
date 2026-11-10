package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.get
import androidx.core.graphics.scale
import com.haooz.chedule.data.platform.PrivacyConsent
import com.haooz.chedule.data.platform.StatsReporter
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.main.CourseScheduleApp
import com.haooz.chedule.ui.activities.main.bitmapRecycler
import com.haooz.chedule.ui.activities.today.TodayAssistantCard
import com.haooz.chedule.ui.components.SharedFileIntent
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouteContent
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.MainLayerTransition
import com.haooz.chedule.ui.navigation.rememberAppRouter
import com.haooz.chedule.ui.navigation.rememberNavTransitionState
import com.haooz.chedule.ui.screens.AndroidSettingsPane
import com.haooz.chedule.ui.screens.FireworksOverlay
import com.haooz.chedule.ui.screens.PrivacyConsentScreen
import com.haooz.chedule.ui.screens.WeatherBootstrap
import com.haooz.chedule.ui.screens.rememberWeather
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.AndroidUpdateController
import com.haooz.chedule.ui.utils.ApiCompat
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.performScheduleShare
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        /** 二级页侧栏点选主 tab */
        const val EXTRA_MAIN_TAB = "extra_main_tab"

        // 跨 Activity 重建复用，避免每次启动重新解码壁纸。
        // 壁纸与变换已下沉到 :ui-shared 的 WallpaperStore（桌面/iOS 的 CourseScheduleApp
        // 读同一份），这里只是**只读转发** —— 写一律走 WallpaperStore.setCached/saveTransform。
        // ⚠ 行为与迁移前一致：进程内跨 Activity 重建复用。
        val cachedWallpaperBitmap: android.graphics.Bitmap?
            get() = com.haooz.chedule.data.WallpaperStore.currentImageBitmap()?.asAndroidBitmap()

        var cachedWallpaperOffset: Offset
            get() = com.haooz.chedule.data.WallpaperStore.wallpaperOffset
            set(value) = com.haooz.chedule.data.WallpaperStore.saveTransform(
                value, com.haooz.chedule.data.WallpaperStore.transformScale
            )

        var cachedWallpaperScale: Float
            get() = com.haooz.chedule.data.WallpaperStore.transformScale
            set(value) = com.haooz.chedule.data.WallpaperStore.saveTransform(
                com.haooz.chedule.data.WallpaperStore.wallpaperOffset, value
            )

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

    // 主界面最近一次同步的系统栏深浅；子页退出后据此恢复
    private var lastStatusDark = false
    private var lastNavDark = false

    fun syncSystemBars(statusDark: Boolean, navDark: Boolean) {
        lastStatusDark = statusDark
        lastNavDark = navDark
        val statusFlag = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        val navFlag = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        var appearance = 0
        if (!statusDark) appearance = appearance or statusFlag
        if (!navDark) appearance = appearance or navFlag
        window.decorView.post {
            ApiCompat.setSystemBarsAppearance(window, appearance, statusFlag or navFlag)
        }
    }

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
        refreshWidgetsIfLaunchedFromWidget(intent)

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


        setContent {
            CourseScheduleTheme {
                // 合规：未同意隐私政策前不申请权限、不收集信息
                var privacyAgreed by remember {
                    mutableStateOf(PrivacyConsent.hasConsented())
                }
                val router = rememberAppRouter()
                // 《隐私政策》全文页在栈上时，把隐私弹窗让开：它挂在 AppNavHost 之上，
                // 遮罩不撤的话全文页读不了也滑不动（见 PrivacyConsentScreen）
                val policyPageVisible =
                    router.routes.any { it == com.haooz.chedule.ui.navigation.AppRoute.PrivacyPolicy }
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
                    // 子页盖住主界面时状态栏改跟应用主题，退出后恢复主界面深浅
                    var systemBarsFirstPass by remember { mutableStateOf(true) }
                    LaunchedEffect(router.hasOverlay) {
                        if (systemBarsFirstPass) {
                            systemBarsFirstPass = false
                            return@LaunchedEffect
                        }
                        if (router.hasOverlay) applyThemeAwareSystemBars()
                        else syncSystemBars(lastStatusDark, lastNavDark)
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 壳层引导：天气在课表页顶栏也要显示，不能等切到今日页才加载
                        WeatherBootstrap()

                        // ── 主界面常驻底座 ──
                        MainLayerTransition(mainTransition) {
                            AndroidCourseScheduleApp(privacyConsented = privacyAgreed)
                        }

                        // ── 子页叠加层 ──
                        AppNavHost(router, mainTransition = mainTransition) { _, route ->
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
                                },
                                policyPageVisible = policyPageVisible,
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
        refreshWidgetsIfLaunchedFromWidget(intent)
        extractIntentData(intent)
        handleReminderSettingsIntent(intent)
        intent.getIntExtra(EXTRA_MAIN_TAB, -1).takeIf { it in 0..2 }?.let {
            com.haooz.chedule.ui.components.TabletNavSideState.pendingMainTab = it
        }
        shareIntentVersion++
    }

    /** 小部件任意位置点击 = 打开应用 + 刷新全部小部件 */
    private fun refreshWidgetsIfLaunchedFromWidget(intent: Intent?) {
        if (!com.haooz.chedule.widget.WidgetClickHelper.consumeLaunchFlag(intent)) return
        com.haooz.chedule.widget.WidgetUpdateCache.updateInstalledWidgets(this)
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
 * 按屏幕宽高降采样解码相册选图（cover 口径：缩后仍覆盖屏幕，只缩不扩）。
 * 相册原图可达 50MP+，全尺寸 ARGB_8888 解码是选壁纸路径 OOM 的主因。
 * 部分机型/云相册 URI 一次 open 不稳，先落到缓存文件再解码。
 *
 * ⚠ target 是**方向感知**的 (宽, 高)：曾经在这里转成屏幕长短边，横图高度被砍到
 * 屏宽，竖屏 cover 显示时放大发虚。与保存侧 [com.haooz.chedule.data.WallpaperStore]
 * 的 cover 策略同口径。
 */
internal fun decodeWallpaperSampled(
    context: Context,
    uri: android.net.Uri,
    screenWPx: Float,
    screenHPx: Float,
): android.graphics.Bitmap? {
    val targetW = screenWPx.toInt().coerceAtLeast(1)
    val targetH = screenHPx.toInt().coerceAtLeast(1)

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
        // cover：按「缩后仍覆盖屏幕」的倍率缩（max）。用 fit（min）会把横图砍到
        // 屏宽以内 —— 方向感知 target 下 4000×3000 会被 min(1256/4000, 2760/3000)
        // 缩成 1256×942，比保存侧再缩还小，显示放大近 3 倍，比不修更糊。
        val cover = maxOf(
            targetW / decoded.width.toFloat(),
            targetH / decoded.height.toFloat(),
        )
        if (cover >= 1f) return decoded
        val scaled = decoded.scale(
            (decoded.width * cover).toInt().coerceAtLeast(1),
            (decoded.height * cover).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
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


// ── Android 装配层 ──────────────────────────────────────────────────────────
//
// Android 专有能力在这里逐项注入：系统栏 / 相册选壁纸（launcher + 降采样解码）/ 分享 /
// 外部分享文件读取 / 节假日提醒重排 / 设置页 8 个平台 dest / 今日助手卡 / 更新 / 位图回收。
// 共享页只认回调，不认平台类型。

/**
 * 位图回收注入：ImageBitmap 无 recycle()，共享层的快照回收点经此回调落到 Android Bitmap。
 * isRecycled 检查在这里做，共享层只管「该不该回收」（身份比对）。
 */
private val SharedBitmapRecycler: (ImageBitmap) -> Unit = { image ->
    runCatching {
        val bmp = image.asAndroidBitmap()
        if (!bmp.isRecycled) bmp.recycle()
    }
}

@Composable
fun AndroidCourseScheduleApp(privacyConsented: Boolean) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenW = with(density) { screenWidthDp().dp.toPx() }
    val screenH = with(density) { screenHeightDp().dp.toPx() }

    // 共享层快照回收 → Android Bitmap.recycle（iOS/桌面不注入，交给 GC）
    SideEffect { bitmapRecycler = SharedBitmapRecycler }

    // ── 相册选壁纸：系统选图器 + 三级降采样解码，结果回填共享层状态 ──
    var wallpaperDecodeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pendingWallpaperPicked by remember { mutableStateOf<((ImageBitmap?) -> Unit)?>(null) }
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            showToast(AndroidMiscStr.noImageSelected())
            return@rememberLauncherForActivityResult
        }
        if (wallpaperDecodeJob?.isActive == true) {
            showToast(AndroidMiscStr.processingWallpaperWait())
            return@rememberLauncherForActivityResult
        }
        val decodeW = screenW
        val decodeH = screenH
        showToast(AndroidMiscStr.processingWallpaper())
        wallpaperDecodeJob = coroutineScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeWallpaperSampled(context, uri, decodeW, decodeH)
            }
            if (bitmap == null) {
                showToast(AndroidMiscStr.wallpaperDecodeFailed(), longDuration = true)
                return@launch
            }
            pendingWallpaperPicked?.invoke(bitmap.asImageBitmap())
        }
    }

    CourseScheduleApp(
        privacyConsented = privacyConsented,
        resumeCount = activity?.resumeCount ?: 0,
        isInFreeformWindow = activity?.isInFreeformWindow ?: false,
        onSystemBarsChanged = { statusDark, navDark ->
            activity?.syncSystemBars(statusDark, navDark)
        },
        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
        onPickWallpaperImage = { onPicked ->
            pendingWallpaperPicked = onPicked
            wallpaperPickerLauncher.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        },
        onShareSchedule = { name, changed ->
            performScheduleShare(
                context = context,
                scope = coroutineScope,
                scheduleName = name,
                onSharingChanged = changed,
            )
        },
        onReadSharedFileText = { uriString ->
            try {
                activity?.contentResolver
                    ?.openInputStream(Uri.parse(uriString))
                    ?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                showToast(ImportStr.importFailedReason(e.message))
                null
            }
        },
        sharedFileIntent = activity?.let { act ->
            act.shareIntentUri?.let { uri ->
                SharedFileIntent(uri.toString(), act.shareIntentVersion)
            }
        },
        onShareIntentConsumed = { activity?.clearShareIntent() },
        settingsPane = { args ->
            AndroidSettingsPane(args)
        },
        todayAssistantCard = { d ->
            // 天气/定位是 Android 能力（LocationManager），在宿主算好传给共享卡片；
            // 抓取与多源融合在 :ui-shared 的 weather/，卡片与详情页共用同一份缓存
            val (weather, hasLocationPermission, requestLocation) = rememberWeather()
            val router = LocalAppRouter.current
            TodayAssistantCard(
                weather = weather,
                hasLocationPermission = hasLocationPermission,
                onRequestLocation = requestLocation,
                courses = d.courses,
                tomorrowCourses = d.tomorrowCourses,
                sectionTimes = d.sectionTimes,
                morningSections = d.morningSections,
                afternoonSections = d.afternoonSections,
                showClassroom = d.showClassroom,
                showTeacher = d.showTeacher,
                surfaceOpacity = d.surfaceOpacity,
                // 下课烟花仍留在 Android（依赖 LocalContext / System.nanoTime）
                fireworksOverlay = { trigger, modifier ->
                    FireworksOverlay(trigger = trigger, modifier = modifier)
                },
                onOpenWeather = { router.navigate(AppRoute.Weather) },
            )
        },
        updateController = remember { AndroidUpdateController(context) },
    )
}