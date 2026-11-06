package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.SwitchCardSnapshotStore
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.data.backup.SyncManager
import com.haooz.chedule.data.backup.WebDavManager
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.UpdateController
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.LanguageStore
import com.haooz.chedule.ui.activities.today.TodayAssistantCardData
import com.haooz.chedule.ui.components.SharedFileIntent
import com.haooz.chedule.ui.components.TabletNavSideState
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_FADE_MILLIS
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_SCALE_MILLIS
import com.haooz.chedule.ui.effects.motion.CustomizePageEasing
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.LocalNativeBottomBarHost
import com.haooz.chedule.ui.utils.PageBackdropSnapshot
import com.haooz.chedule.ui.utils.ShiftModeRequest
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.haooz.chedule.ui.utils.rememberScheduleThemeMode
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.chedule.viewmodel.shiftViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 真课表主界面。平台能力全部走注入参数，缺能力的入口不渲染；
 * 位图统一 [ImageBitmap]，快照裁剪走 [com.haooz.chedule.ui.utils.TransitionLayerSnapshot]（GraphicsLayer + clipRect，零位图分配），
 * 壁纸存储走 `WallpaperStore`。
 */

/** 宿主注入的位图回收实现（Android = `asAndroidBitmap().recycle()`；iOS/桌面 = null，交给 GC）。 */
var bitmapRecycler: ((ImageBitmap) -> Unit)? = null

/**
 * 点「课表外观」到开始截屏的等待，所有入口共用。
 */
internal const val CUSTOMIZE_ENTRY_DELAY_MS = 320L

// 课表外观页「封面快照」的时序常量（CUSTOMIZE_COVER_*）在
// ui/effects/motion/OobeMotion.kt —— 外观页也要按同一个窗口对齐自己的入场时间轴。

@Suppress("RememberReturnType")
@Composable
fun CourseScheduleApp(
    privacyConsented: Boolean = true,
    // ── 平台注入（宿主提供；桌面/iOS 用默认空实现）─────────────────────────
    /** 宿主 resume 次数（Android = MainActivity.resumeCount）：>1 时刷新设置/课表 */
    resumeCount: Int = 0,
    /** 平台小窗模式（Android = MainActivity.isInFreeformWindow）；排班页/圆角用 */
    isInFreeformWindow: Boolean = false,
    /** 状态栏/导航栏深浅同步（Android = applyThemeAwareSystemBars/applyNavigationBarIsDark） */
    onSystemBarsChanged: (statusDark: Boolean, navDark: Boolean) -> Unit = { _, _ -> },
    /** 节假日数据变更副作用（Android = CourseReminderHelper.onHolidayDataChanged） */
    onHolidayDataChanged: () -> Unit = {},
    /** 选壁纸：宿主负责系统选图器+降采样解码，成功后回调 ImageBitmap（null=取消/失败） */
    onPickWallpaperImage: (((ImageBitmap?) -> Unit) -> Unit)? = null,
    /** 分享课表（Android = performScheduleShare）；null = 该端无此能力 */
    onShareSchedule: ((scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit)? = null,
    /** 读取外部分享进的文件全文（Android = contentResolver）；null = 不支持 */
    onReadSharedFileText: ((String) -> String?)? = null,
    /** 外部分享进的文件（Android = MainActivity.shareIntentUri/version 摊平） */
    sharedFileIntent: SharedFileIntent? = null,
    onShareIntentConsumed: () -> Unit = {},
    /** 设置页宿主装配（Android = AndroidSettingsScreen）；null = 共享 SettingsScreen 默认装配 */
    settingsPane: (@Composable (SettingsPaneArgs) -> Unit)? = null,
    /** 今日助手卡（天气/定位是 Android 能力）；null = 不渲染 */
    todayAssistantCard: (@Composable (TodayAssistantCardData) -> Unit)? = null,
    /** 更新能力（检查/下载/安装）；null = NoopUpdateController（不弹更新） */
    updateController: UpdateController? = null,
    /** 是否显示设置页的「更新设置」入口；iOS 走 App Store → 传 false，入口整个不渲染。 */
    showUpdateSettings: Boolean = true,
    /**
     * 是否显示「排班模式」入口（设置页进入开关 / 退出按钮 / 确认弹窗）；iOS 不支持 → 传 false，入口整个不渲染。
     */
) {


    // 语言偏好：进根组合时从存储恢复一次（AppLanguage.set 同时落盘，重建后读回同值）
    remember { AppLanguage.init(LanguageStore.load()) }

    val viewModel: CourseViewModel = courseViewModel()
    val scheduleViewModel: ScheduleViewModel = scheduleViewModel()
    val settingsViewModel: SettingsViewModel = settingsViewModel()
    val shiftViewModel: ShiftViewModel = shiftViewModel()
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()

    // 状态与交互逻辑全在 CourseScheduleAppState；这里只负责注入上下文、跑组合副作用、画 UI
    // 底栏选中 tab 必须和主 pager 一样能跨进程回收恢复
    val selectedTabState = rememberSaveable(
        saver = Saver<MutableIntState, Int>(
            save = { it.intValue },
            restore = { mutableIntStateOf(it) },
        )
    ) {
        mutableIntStateOf(if (defaultHomepage == "今日") 0 else 1)
    }
    val state = remember {
        CourseScheduleAppState().apply {
            this.viewModel = viewModel
            this.scheduleViewModel = scheduleViewModel
            this.settingsViewModel = settingsViewModel
            this.shiftViewModel = shiftViewModel
            // 首屏默认 tab：与原来的 remember { mutableIntStateOf(...) } 初值一致
            attachSelectedTabState(selectedTabState)
        }
    }
    // 宿主参数每次重组同步（值变化极罕见，但保持与原实现一致）
    state.privacyConsented = privacyConsented
    state.resumeCount = resumeCount
    state.isInFreeformWindow = isInFreeformWindow
    state.onSystemBarsChanged = onSystemBarsChanged
    state.onHolidayDataChanged = onHolidayDataChanged
    state.onPickWallpaperImage = onPickWallpaperImage
    state.onShareSchedule = onShareSchedule
    state.onReadSharedFileText = onReadSharedFileText
    state.sharedFileIntent = sharedFileIntent
    state.onShareIntentConsumed = onShareIntentConsumed
    state.settingsPane = settingsPane
    state.todayAssistantCard = todayAssistantCard
    state.updateController = updateController
    state.showUpdateSettings = showUpdateSettings

    with(state) {
        // ── 组合期上下文：每次重组把「当前值」写进 state，等价于原来的局部 val ──
        router = LocalAppRouter.current
        hostNavigation = LocalHostNavigation.current
        settingsScrollBehavior = rememberSharedScrollBehavior()
        todayScrollBehavior = rememberSharedScrollBehavior()
        scheduleScrollBehavior = rememberSharedScrollBehavior()

        LaunchedEffect(privacyConsented) {
            // 合规：未同意隐私政策前不进行云同步等网络行为
            if (!privacyConsented) return@LaunchedEffect
            val syncManager = SyncManager.getInstance()
            val repository = CourseRepository()
            val webDavManager = WebDavManager {
                onHolidayDataChanged()
            }
            syncManager.start(repository, webDavManager)
            // 备份/恢复后刷新 ViewModel 内存缓存
            syncManager.onSyncCompleted = {
                viewModel.refreshEssentialData()
                viewModel.reloadCourses()
                settingsViewModel.refreshSettings()
            }
        }

        isDark = isAppDarkTheme()
        // ⚠ 主内容采样层**不能**降采样录制（recordScale）：它 contentOnlyCapture=true，
        // 录完 blit 上屏，用户直接看的就是这层；且改成双遍历（上屏走 drawContent）
        // 后实测切 tab 更差（20 帧 / 50% janky / 90th 97ms，基线 192 帧 9.38% 24ms）——
        // 每帧多遍历整棵三页树的代价超过采样 blit 面积降 4 倍的收益。
        // recordScale 基础设施（默认 1f）保留给**本来就是双遍历**的纯采样层
        // （contentOnlyCapture=false 的页级 backdrop，降采样不增加遍历成本）。
        liquidGlassBackdrop = rememberLayerBackdrop()
        // 顶栏/底栏各自再录一层：两者都在内容层之上，只有把它们也录进来，
        // 「比底栏更高一层」的组合采样层才成立
        topBarSampleBackdrop = rememberLayerBackdrop()
        // 宿主自己画底栏（鸿蒙 ArkUI）时 Compose 一个像素都不画，底栏那层永远录不到东西：
        // 直接从组合里摘掉，别让每个玻璃节点白跑一次采样
        if (LocalNativeBottomBarHost.current?.ownsBottomBar == true) {
            overlayGlassBackdrop = rememberCombinedBackdrop(
                liquidGlassBackdrop, topBarSampleBackdrop
            )
        } else {
            bottomBarSampleBackdrop = rememberLayerBackdrop()
            overlayGlassBackdrop = rememberCombinedBackdrop(
                liquidGlassBackdrop, topBarSampleBackdrop, bottomBarSampleBackdrop
            )
        }
        totalWeeks = viewModel.totalWeeks.collectAsState().value
        currentWeek = viewModel.currentWeek.collectAsState().value
        classStartTime = viewModel.classStartTime.collectAsState().value
        morningSections = settingsViewModel.morningSections.collectAsState().value
        afternoonSections = settingsViewModel.afternoonSections.collectAsState().value
        eveningSections = settingsViewModel.eveningSections.collectAsState().value
        configuredSectionTimes = settingsViewModel.sectionTimes.collectAsState().value
        holidayDataRevision = HolidayManager.dataRevision.collectAsState().value
        // 只在「返回」时刷新；冷启动首次 onResume 时 ViewModel 刚加载完，再全量刷会拖慢首屏
        LaunchedEffect(resumeCount) {
            // 作息按日期自动切换：跨过生效日期后补一次提醒重排。
            // 只是比对 prefs 里记的 key，几乎零开销，所以冷启动这一次也跑
            withContext(ioDispatcher) {
                CourseRepository().syncRoutineAfterDateChange()
            }
            if (resumeCount > 1) {
                withContext(ioDispatcher) {
                    settingsViewModel.refreshSettings()
                    scheduleViewModel.refreshScheduleList()
                }
                viewModel.reloadCourses()
            }
        }
        // 节假日/调休保存不走课程 reload；resume 时对比 HolidayManager 版本，变了才 bump dataVersion
        LaunchedEffect(resumeCount, holidayDataRevision) {
            val holidayV = HolidayManager.getVersion()
            if (holidayV != seenHolidayVersion) {
                seenHolidayVersion = holidayV
                viewModel.bumpDataVersion()
            }
        }
        windowInfo = LocalWindowInfo.current
        densityProvider = LocalDensity.current
        // 局部别名：UI 里大量 `density.xxx` 保持原写法（也避免被 graphicsLayer 的 density 遮蔽）
        val density = densityProvider
        detailLayerLayoutDirection = LocalLayoutDirection.current
        // 这三个原来是 Composable 局部 val（依赖 LocalConfiguration / density），每次重组重算
        isTablet = isTabletWidth()
        screenWPx = with(density) { screenWidthDp().dp.toPx() }
        screenHPx = with(density) { screenHeightDp().dp.toPx() }
        warmupBlurPx = with(density) { 24.dp.toPx() }
        latestScreenWPx = screenWPx
        latestScreenHPx = screenHPx

        // 首帧搭配：壁纸位图（宿主已同步解码）+ 卡片参数一起读，同步装好。
        // 放在画 UI 之前 —— 首帧就是用户设定，不需要等一帧再跳、也不需要过渡动画。
        ensureInitialCombination()

        // 排班模式切换请求：设置页现在是二级页（在路由栈里），它只投递请求；
        // 真正的 loading 覆盖层、pager 重排、VM 切换都必须留在常驻底座执行。
        // snapshotFlow 而不是 key：pending 变化不该把整个 CourseScheduleApp 重组一遍。
        LaunchedEffect(Unit) {
            snapshotFlow { ShiftModeRequest.pending }.collect { pending ->
                val target = pending ?: return@collect
                // 先消费再执行，避免同一个请求被重放
                ShiftModeRequest.consume()
                com.haooz.chedule.ui.utils.FeatureLog.shift(
                    if (target) "enter_loading" else "exit_loading"
                )
                showShiftLoading = true
                isExitingShift = !target
            }
        }

        // 首帧后预热 RenderEffect，避免首次开 BlurBottomSheet 掉帧
        LaunchedEffect(Unit) { blurWarmupReady = true }
        if (blurWarmupReady) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .graphicsLayer { alpha = 0f }
                    .then(
                        // 无材质档玻璃全关，预热没有意义，直接跳过
                        if (isRenderEffectSupported() && com.haooz.chedule.ui.utils.AppMaterialSettings.glassEffectsEnabled()) {
                            Modifier.drawBackdrop(
                                backdrop = liquidGlassBackdrop,
                                shape = { ContinuousRoundedRectangle(36.dp) },
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
        LaunchedEffect(TabletNavSideState.expanded, navBarStyle) {
            if (navBarStyle != "rail") return@LaunchedEffect
            railForceRecordFrames[0] = 12
            delay(360.milliseconds)
            railBlurResampleEpoch.intValue++
        }
        isShiftMode = shiftViewModel.isShiftMode.collectAsState().value

        retainedDetailLayer = rememberGraphicsLayer()

        // 高光开关 = 正在拖 && 浮层还活着：声明在 Scaffold 之前，Scaffold 内容里的提饱和节点要用它。
        // 一松手 onCourseDragEnd 立刻置 false 开始淡出，不等 360ms 退场动画。
        //
        // 进度由动画帧写进 `glowAlpha`（snapshot state），组合期不读它 —— 两个消费者
        // （提饱和遮罩、跟手高光）都只在 draw 阶段读，订阅落在 draw 上就够。
        // 写成 `animateFloatAsState(...).value` 会让整个外壳（4 页 pager + 课表页）
        // 在 150ms 淡变里逐帧重组。
        val glowProgress = remember { Animatable(if (glowShowing) 1f else 0f) }
        LaunchedEffect(glowShowing) {
            glowProgress.animateTo(
                targetValue = if (glowShowing) 1f else 0f,
                animationSpec = tween(150),
            ) { glowAlpha = value }
        }

        // 独立偏好 key，与全局主题开关隔离
        persistedScheduleThemeMode = rememberScheduleThemeMode()
        // 状态栏跟页面实际深浅；导航栏图标始终跟应用设置
        appSettingDark = rememberAppSettingDark()
        LaunchedEffect(effectiveIsDark, appSettingDark) {
            onSystemBarsChanged(effectiveIsDark, appSettingDark)
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
                WallpaperStore.saveTransform(newOffset, newScale)
            }
            wallpaperScreenRef = screenWPx to screenHPx
        }
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
                // 开洞过渡：主内容（含顶/底栏按钮）与封面快照必须**同帧**启动
                launch {
                    cutoutMainScale.animateTo(
                        0.75f,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
                if (customizeSnapshot != null) {
                    launch {
                        customizeCoverScale.animateTo(
                            0.75f,
                            tween(CUSTOMIZE_COVER_SCALE_MILLIS, easing = CustomizePageEasing)
                        )
                    }
                    launch {
                        delay(CUSTOMIZE_COVER_SCALE_MILLIS.toLong().milliseconds)
                        customizeCoverAlpha.animateTo(
                            0f,
                            tween(CUSTOMIZE_COVER_FADE_MILLIS, easing = FastOutSlowInEasing)
                        )
                        customizeCoverActive = false
                    }
                }
            } else {
                cutoutMainScale.animateTo(
                    1f,
                    tween(400, easing = CustomizePageEasing)
                )
            }
        }

        // 保留在 Activity 层不是为了防止销毁（详情页期间主内容已不再移出组合），
        // 而是液态玻璃重录要读它们的「滚动中 / 弹窗开合」（liquidGlassMustRecord、liquidGlassRecordKey）
        scheduleScrollState = rememberScrollState()
        todayListState = rememberLazyListState()
        // 仅课程长按菜单模糊；空白格菜单不模糊
        LaunchedEffect(shortcutMenuVisible, shortcutMenuCourse) {
            if (shortcutMenuVisible && shortcutMenuCourse != null) {
                launch { shortcutMenuBlurRadius.animateTo(16f, tween(280)) }
            } else {
                launch { shortcutMenuBlurRadius.animateTo(0f, tween(250)) }
            }
        }
        screenGraphicsLayer = rememberGraphicsLayer()
        // 转位图用的临时小图层：只承载卡片那一块，避免整屏 toImageBitmap（12MB + 整屏回读）。
        retainedSwitchCardScratchLayer = rememberGraphicsLayer()
        switchLayerLayoutDirection = LocalLayoutDirection.current
        // 读回上次会话退出时固化的卡片快照：位图活不过进程，只有文件能留到下次启动。
        // 桌面上 Store 未初始化/无实现，返回 null，退化成会话内复用。
        LaunchedEffect(Unit) {
            if (retainedSwitchCardBitmap == null) {
                retainedSwitchCardBitmap = SwitchCardSnapshotStore.load()
            }
        }

        hapticFeedback = LocalHapticFeedback.current

        currentDayOfWeek.let { } // 保持与原声明顺序一致（getter，无副作用）
        smartWeekend = settingsViewModel.smartWeekend.collectAsState().value
        dataVersion = viewModel.dataVersion.collectAsState().value
        teachingWeekRules = remember(teachingWeekRepository, dataVersion, classStartTime) {
            teachingWeekRepository.getTeachingWeekReorganizations()
        }
        // 节假日/调休保存后需能重算跳周；resume 时刷新版本号
        holidayVersion = remember(resumeCount, holidayDataRevision) {
            HolidayManager.getVersion()
        }

        autoAdvancePage = remember(
            currentWeek,
            totalWeeks,
            currentDayOfWeek,
            smartWeekend,
            holidayVersion,
            dataVersion,
            teachingWeekRules
        ) {
            if (settingsViewModel.shouldAdvanceToNextWeek(currentDayOfWeek, currentWeek)) {
                (basePage + 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
            } else basePage
        }

        pagerState = rememberPagerState(
            initialPage = autoAdvancePage,
            pageCount = { totalWeeks }
        )

        todayPagerState = rememberPagerState(
            initialPage = todayMaxDateOffset,
            pageCount = { todayMaxDateOffset * 2 }
        )

        // 主 tab 平移容器：仅点底栏/侧栏 tab 驱动。手机端只有 3 页，
        // 后两页（课程管理/切换课表）只有平板侧栏才到得了，按 5 页建会让它们常驻组合树白陪跑。
        mainPagerState = rememberPagerState(
            initialPage = selectedTab.coerceIn(0, mainPagerPageCount - 1),
            pageCount = { mainPagerPageCount }
        )

        LaunchedEffect(mainPagerPageCount) {
            if (shiftModeInitialized && isShiftMode) return@LaunchedEffect
            val lastIndex = (mainPagerPageCount - 1).coerceAtLeast(0)
            if (mainPagerState.currentPage > lastIndex) {
                mainPagerState.scrollToPage(lastIndex)
                selectedTab = lastIndex
            }
        }

        // 当前可见页背后是不是壁纸，决定高光亮度（见 InteractiveHighlight 的 peak）。
        // 必须在组合期算再交给 SideEffect：只在 SideEffect 里读 currentPage 不会订阅，切页不更新。
        SideEffect { PageBackdropSnapshot.hasWallpaper.value = pageBehindHasWallpaper }

        // 二级页侧栏点选主 tab（无转场回来后处理）
        LaunchedEffect(TabletNavSideState.pendingMainTab) {
            val pending = TabletNavSideState.pendingMainTab
            if (pending in 0..4 && shiftModeInitialized) {
                if (pending == 2 && navBarStyle != "rail") {
                    // 手机/排班的设置已是二级页：深链直接压栈，不碰 pager
                    router.navigate(AppRoute.Settings)
                } else {
                    // 手机端只剩 2 页（排班 1 页），侧栏带过来的页号要按实际页数收口
                    val target = pending.coerceAtMost(mainPagerPageCount - 1)
                    selectedTab = target
                    mainPagerState.scrollToPage(target)
                }
                TabletNavSideState.pendingMainTab = -1
            }
        }

        LaunchedEffect(isShiftMode) {
            if (shiftModeInitialized) {
                selectedTab = if (isShiftMode) 0 else if (defaultHomepage == "今日") 0 else 1
                mainPagerState.scrollToPage(selectedTab)
            }
            shiftModeInitialized = true
        }

        // 基线初值：等价于原来的 remember { mutableIntStateOf(currentWeek / autoAdvancePage) }
        remember { lastAutoWeek = currentWeek; lastAutoPage = autoAdvancePage }
        LaunchedEffect(currentWeek, totalWeeks, autoAdvancePage) {
            // A rule edit must recalculate the default, but must not pull a manually browsed page back.
            if (currentWeek != lastAutoWeek || pagerState.currentPage == lastAutoPage) {
                if (pagerState.currentPage != autoAdvancePage) pagerState.scrollToPage(
                    autoAdvancePage
                )
            }
            lastAutoWeek = currentWeek
            lastAutoPage = autoAdvancePage
        }

        // 切 tab 时强制放掉 pager 上可能残留的 scroll 锁。
        // 周 pager 与今日页 pager 走的是同一套 pagerAxisTakeoverGesture，锁泄漏家族相同：
        // 今日页锁一旦泄漏，切回今日后左右滑就彻底失效（只能重进应用）。
        LaunchedEffect(mainPagerState.currentPage) {
            runCatching { pagerState.scroll(MutatePriority.PreventUserInput) { } }
            runCatching {
                // 抢占会把未完成的 settle 动画一起掐掉，落点可能停在两页之间，补一次吸附
                todayPagerState.scroll(MutatePriority.PreventUserInput) { }
                val visual =
                    todayPagerState.currentPage + todayPagerState.currentPageOffsetFraction
                val target = visual.roundToInt()
                if (abs(visual - target) > 0.001f) todayPagerState.scrollToPage(target)
            }
        }

        // 今日页默认选中今天：等价于原来的 remember { mutableIntStateOf(currentDayOfWeek) }
        remember { if (todaySelectedDayOfWeek == 0) todaySelectedDayOfWeek = currentDayOfWeek }

        // 跳周落到目标页后撤掉标题覆盖：currentPage 已被 scrollToPage 定在目标，
        // 此时覆盖值与真实值相同，撤掉画面不跳；被打断的情况由 jumpToWeek 自己清。
        LaunchedEffect(pagerState.currentPage, pendingViewingWeek) {
            if (pendingViewingWeek >= 1 && pagerState.currentPage + 1 == pendingViewingWeek) {
                pendingViewingWeek = 0
            }
        }

        courses = viewModel.courses.collectAsState().value
        // dataVersion + courses 引用都进 key：调课 size 可能不变，只靠 size 会让智能周末星期行停在旧值
        dayRange = remember(
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
        weekDates = remember(currentViewingWeek, classStartTime, teachingWeekRules) {
            try {
                val startDate = LocalDate.parse(classStartTime.replace("/", "-"))
                TeachingWeekReorganization.datesForTeachingWeek(
                    startDate, currentViewingWeek, teachingWeekRules,
                )
            } catch (_: Exception) {
                emptyList()
            }
        }

        screenCornerRadius = rememberScreenCornerRadiiPx()

        coroutineScope = rememberCoroutineScope()
        latestMainTabSelected = onMainTabSelected
        DisposableEffect(hostNavigation) {
            val callback: (Int) -> Unit = { index ->
                // 底栏最后一格是设置页**入口**（不是 pager 页）：页号越界即设置，
                // 手机普通模式 2 页 / 排班 1 页，两种都靠这个判据收口
                if (index >= mainPagerPageCount) router.navigate(AppRoute.Settings)
                else latestMainTabSelected(index)
            }
            hostNavigation?.onTabSelected = callback
            onDispose {
                hostNavigation?.let { host ->
                    if (host.onTabSelected === callback) host.onTabSelected = null
                }
            }
        }
        SideEffect {
            hostNavigation?.let { host ->
                host.selectedTab = selectedTab
                host.tabCount = if (isShiftMode) 2 else 3
                host.shiftMode = isShiftMode
                host.showsNativeTabBar = nativeBottomBar
                host.isTabletLayout = isTablet
                host.isDark = effectiveIsDark
                // 需由Swift 读这个值自行施加同样变换（对齐 Android 上 Compose 底栏的行为）
                host.cutoutScale = cutoutMainScale.value
                host.cutoutCenterYRatio = cutoutCenterYRatio
                host.cutoutCenterXRatio = cutoutCenterXRatio
            }
        }
        // 外观页弹窗让位时主内容的纵向位移，改由主内容自己的 graphicsLayer 块直推
        // 引用固定，避免每帧新建 lambda 让 iOS 侧 update() 比对失败。
        pushCutoutChromeOffset = remember(hostNavigation) {
            { offsetY: Double -> hostNavigation?.onCutoutChromeOffsetY?.invoke(offsetY) }
        }
        // 内容区（切天/周）一有横向手势：立刻取消未完成的主 tab 平移，吸回当前页，避免露出隔壁页
        cancelUnfinishedMainTabAnim = {
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
        mainContentNestedScroll = remember(mainPagerState) {
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

        DisposableEffect(hostNavigation) {
            onDispose {
                hostNavigation?.let { host ->
                    host.scheduleLibraryActive = false
                    host.scheduleLibraryTransitionActive = false
                    host.onOpenSwitchSchedule = null
                    host.onJumpWeek = null
                    host.onCourseManage = null
                    host.onEnterCustomize = null
                    host.onJumpToDate = null
                    host.keepsNativeHeaderDuringCutout = false
                    host.updateDetailBackgroundBlur(0f)
                }
            }
        }
        SideEffect {
            hostNavigation?.let { host ->
                host.scheduleLibraryActive =
                    nativeBottomBar && !isTablet && showSwitchSchedule && !switchExiting
                host.scheduleLibraryTransitionActive =
                    switchPreparingLayer || switchAnimRunning || switchPendingReverse ||
                            (showSwitchSchedule && switchExiting)
                // ⚠ 不含 `!showDetail`：详情页盖满全屏，原生顶栏本就被挡住。
                host.showsNativeHeader = nativeBottomBar && !isTablet && !isWindowCutoutActive &&
                        !showCustomizePage && (!showSwitchSchedule || switchExiting)
                // 课表外观页（含开洞进出场）：原生顶栏不清空，宿主拿 cutoutScale 自己施加与
                // 主内容/底栏同一套变换 —— 右上角菜单跟着一起移动缩小，而不是消失。
                host.keepsNativeHeaderDuringCutout = nativeBottomBar && !isTablet &&
                        (showCustomizePage || isWindowCutoutActive)
                host.onOpenSwitchSchedule = { openScheduleLibrary() }
                host.onJumpWeek = { viewModel.showJumpWeekDialog() }
                host.onCourseManage = {
                    router.navigate(AppRoute.CourseManage)
                }
                host.onEnterCustomize = { enterCustomizePage() }
                host.onJumpToDate = { todayJumpToDateTrigger++ }
            }
        }
        // 宿主底栏画在 Compose 之外，主界面上这几层全屏内容盖不住它，只能整条收起
        val nativeBottomBarHost = LocalNativeBottomBarHost.current
        val bottomBarSuppressed = showCustomizePage || showSwitchSchedule || showDetail ||
                isWindowCutoutActive
        SideEffect {
            nativeBottomBarHost?.suppressed = bottomBarSuppressed
        }
    }

    with(state) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (showCustomizePage) Color(0xFF1A1A1A) else MiuixTheme.colorScheme.surface)
                .drawWithContent {
                    drawContent()

                }) {
            // 材质档位必须进 remember key：切档时这里要当场重算，
            // 否则卡片模糊/折射要等下次重启（key 没变 → 返回旧 base）。
            val materialLevel = com.haooz.chedule.ui.utils.AppMaterialSettings.level
            val displayAppearance = remember(
                showCustomizePage,
                isWindowCutoutActive,
                originalCombination,
                combinations,
                currentCombinationIndex,
                materialLevel,
            ) {
                // 开洞前显示已应用外观，避免快照过渡期闪编辑值
                val base = if (showCustomizePage && !isWindowCutoutActive) {
                    originalCombination?.let { AppearanceConfig.fromCombination(it) }
                        ?: currentAppearance()
                } else {
                    currentAppearance()
                }
                when (materialLevel) {
                    // 无材质档：卡片模糊/折射渲染强制关（设置项已锁死，存档保留原值）。
                    // 卡片仍采壁纸——着色程度/不透明度照常生效；只有玻璃采样录屏层不挂
                    com.haooz.chedule.ui.utils.AppMaterialSettings.NONE -> base.copy(
                        cardBlurRadius = 0f,
                        cardRefraction = CardRefractionLevel.OFF,
                    )
                    else -> base
                }
            }
            // 顶栏/底栏必须采主内容 liquidGlass，否则玻璃退化成纯色
            val chromeBackdrop: Backdrop = liquidGlassBackdrop
            val isEntryAnimating = showSwitchSchedule && switchAnimForward && switchAnimRunning
            // The source remains rendered underneath the morph, including across controller frames.
            val mainContentAlpha = 1f

            // liquidGlass 录制跳帧指纹：结构量变了才 update→markNeedsRecord。
            // 必须 remember 出稳定 List，否则 equals 失败会强制录制。
            val themeBackdropKey = listOf(
                MiuixTheme.isDynamicColor, MiuixTheme.colorScheme.background,
                MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondaryContainer,
                MiuixTheme.colorScheme.tertiaryContainer,
            )
            val liquidGlassRecordKey = remember(
                mainPagerState.currentPage,
                isShiftMode,
                showDetail,
                showCustomizePage,
                showSwitchSchedule,
                isWindowCutoutActive,
                shortcutMenuVisible,
                isDraggingCard,
                floatingCardVisible,
                dataVersion,
                currentWeek,
                totalWeeks,
                currentCombinationIndex,
                effectiveIsDark,
                wallpaperBitmap,
                TabletNavSideState.expanded,
                scheduleShowCourseDetail.value,
                pagerState.settledPage,
                themeBackdropKey
            ) {
                listOf(
                    mainPagerState.currentPage,
                    isShiftMode,
                    showDetail,
                    showCustomizePage,
                    showSwitchSchedule,
                    isWindowCutoutActive,
                    shortcutMenuVisible,
                    isDraggingCard,
                    floatingCardVisible,
                    dataVersion,
                    currentWeek,
                    totalWeeks,
                    currentCombinationIndex,
                    effectiveIsDark,
                    wallpaperBitmap,
                    TabletNavSideState.expanded,
                    scheduleShowCourseDetail.value,
                    pagerState.settledPage,
                    themeBackdropKey
                )
            }
            // ⚠ 下面这些动画值**不要**写成 `rememberUpdatedState(x.value)`：那是在组合期读
            //   `Animatable.value`，长按菜单模糊 / 开洞缩放 / 面板位移这些动画期间外壳会逐帧
            //   重组（实测长按后每 11~14ms 一次，整张课表跟着重跑）。lambda 里直接读
            //   `x.value` 就行 —— 它只在 draw 阶段执行，订阅落在 draw 上。
            val latestIsWindowCutout by rememberUpdatedState(isWindowCutoutActive)
            val latestShowCustomize by rememberUpdatedState(showCustomizePage)
            val latestIsCustomizeExiting by rememberUpdatedState(isCustomizeExiting)
            // ⚠ 必须 remember 缓存：asImageBitmap() 每次调用都新建包装对象，每帧新建会让
            // Image 的位图纹理反复重建。Bitmap 生命周期管理里的 `===` 身份比较一律不动。
            val wallpaperImage = remember(wallpaperBitmap) { wallpaperBitmap }
            val originalWallpaperImage = originalWallpaperBitmap
            // 课表/今日/设置滚动时主内容像素在变，必须重录，否则顶栏/底栏玻璃冻结。
            // ⚠ 周左右滑（pagerState）与**今日页左右翻日（todayPagerState）**刻意不在此列：
            //   一次翻页动画期间每帧重录 = 整棵内容树在 300ms 里重画 60 遍，帧预算直接吃满，
            //   这才是程序化滚动（返回今日 / 调课翻周）发卡的来源。内容整页平移时玻璃滞后的
            //   观感与周左右滑同一取舍，用户已接受。落页靠下面 draw 期比较 settledPage 补一帧。
            val liquidGlassMustRecord = remember(
                scheduleScrollState,
                todayListScrollInProgress,
                pagerState,
                todayPagerState,
                mainPagerState
            ) {
                var lastRailExpand = Float.NaN
                var lastBlurEpoch = -1
                // 今日页上一次落定的页号：只在落页后补录一次。
                // 用 settledPage 而不是 currentPage —— currentPage 在翻到一半就变了，
                // 那一帧补录下来的仍是「半页」内容，剩下半段动画玻璃照样滞后。
                // ⚠ 初值给 -1 而不是读 settledPage —— 在 remember 块里读会订阅组合，
                //   每翻一页白白重组整个外壳。
                var lastTodayPage = -1
                {
                    // 侧栏进度只在 draw 阶段读，不进组合
                    val expand = TabletNavSideState.expandProgress.floatValue
                    val expandMoving = expand != lastRailExpand
                    lastRailExpand = expand
                    val epochBumped = railBlurResampleEpoch.intValue != lastBlurEpoch
                    lastBlurEpoch = railBlurResampleEpoch.intValue
                    val forceRail = railForceRecordFrames[0] > 0
                    if (forceRail) railForceRecordFrames[0] = railForceRecordFrames[0] - 1
                    // 今日页落页：draw 期读，不触发组合，也不逐帧重录
                    val todayPage = todayPagerState.settledPage
                    val todayPageChanged = todayPage != lastTodayPage
                    if (todayPageChanged) lastTodayPage = todayPage
                    scheduleScrollState.isScrollInProgress ||
                            todayListScrollInProgress.value ||
                            // ⚠ 周左右滑（pagerState）与今日页左右翻日（todayPagerState）不在此列
                            todayPageChanged ||
                            mainPagerState.isScrollInProgress ||
                            expandMoving ||
                            forceRail ||
                            epochBumped ||
                            // 开洞编辑时主内容持续缩放，绝不能停录
                            latestIsWindowCutout ||
                            (latestShowCustomize && latestIsCustomizeExiting) ||
                            // Morph progress does not change the source backdrop.
                            shortcutMenuBlurRadius.value > 0.01f ||
                            managePageBlurRadius.value > 0.01f ||
                            // 非静止端点视为动画进行中
                            (cutoutMainScale.value != 1f && cutoutMainScale.value != 0.75f) ||
                            sheetOffsetY.value != 0f ||
                            customizeExitScale.value != 1f ||
                            customizeCoverScale.value != 1f ||
                            customizeCoverAlpha.value != 1f
                    // ⚠ 拖课卡（isDraggingCard）也刻意不在此列：浮动卡片画在录制层**之上**，
                    //   根本不进 backdrop 像素；原卡隐藏/落点高亮由 recordKey 里的
                    //   isDraggingCard 在起手、收手各补一帧。
                }
            }
            // 卡片柔光样式只有一份：网格（采静态壁纸）与浮层（采实时录制层）共用，
            // 采样源不同但参数不可能漂移
            val scheduleGlassStyle = rememberScheduleGlassStyle(displayAppearance)
            // 静态壁纸采样源只建一份（烘焙一座金字塔，多建一份就是双倍显存）
            val scheduleGlassSurface = rememberScheduleGlassSurface(
                state = state,
                scheduleGlassStyle = scheduleGlassStyle,
                wallpaperImage = wallpaperImage,
                originalWallpaperImage = originalWallpaperImage,
            )
            // 主内容（带模糊/裁切）与浮层组各拆一个文件，外壳只做局部值派生
            CourseScheduleAppMainContent(
                state = state,
                displayAppearance = displayAppearance,
                chromeBackdrop = chromeBackdrop,
                liquidGlassRecordKey = liquidGlassRecordKey,
                liquidGlassMustRecord = liquidGlassMustRecord,
                mainContentAlpha = mainContentAlpha,
                wallpaperImage = wallpaperImage,
                originalWallpaperImage = originalWallpaperImage,
                softGlassSurface = scheduleGlassSurface,
            )
            CourseScheduleAppOverlayLayers(
                state = state,
                displayAppearance = displayAppearance,
                isEntryAnimating = isEntryAnimating,
                scheduleGlassStyle = scheduleGlassStyle,
            )
        }
    }
}

