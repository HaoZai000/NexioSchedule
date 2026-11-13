package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
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
import com.haooz.chedule.ui.components.TabletNavSamplingTracker
import com.haooz.chedule.ui.components.TabletNavSideState
import com.haooz.chedule.ui.components.rememberPageSwitchState
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_FADE_MILLIS
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_SCALE_MILLIS
import com.haooz.chedule.ui.effects.motion.CustomizePageEasing
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.LocalNativeBottomBarHost
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
import com.haooz.softglass.GlassEnvironment
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.backdrops.rememberCombinedBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.shape.ContinuousRoundedRectangle
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
 * 宿主注入的位图回收实现（Android = `asAndroidBitmap().recycle()`；其余平台留 `null` 交给 GC）。
 *
 * 位图都在 native 堆，全屏快照单张十几 MB，光靠 GC 会拖动整个主界面的帧率。
 */
var bitmapRecycler: ((ImageBitmap) -> Unit)? = null

/**
 * 点「课表外观」到开始截屏的等待。
 *
 * 所有入口（顶栏按钮、长按菜单…）共用这一个常量，调用点不要再各自 `delay`：快照必须等
 * 按钮的按压动效收干净，否则截进来的画面带着半截按压态。
 */
internal const val CUSTOMIZE_ENTRY_DELAY_MS = 320L

/**
 * 课表主界面外壳：注入宿主能力与组合期上下文、搭背景与采样层，再交给
 * [CourseScheduleAppMainContent] 和 [CourseScheduleAppOverlayLayers] 绘制。
 *
 * - 平台差异全部走参数：不支持的能力传 `null`，对应入口整体不渲染。
 * - 宿主参数写入 [CourseScheduleAppState] 的普通字段（不触发重组）；`collectAsState` 留在
 *   本作用域，靠这里重组把新值带进子组合。
 * - 高频动画量只在 draw 阶段读：组合期读 `Animatable.value` 会让整个外壳逐帧重组。
 * - 采样层按「谁采谁」分层，见 [CourseScheduleAppState] 中各 backdrop 的说明。
 *
 * @param resumeCount 宿主 resume 次数，>1 时才刷新设置与课表（冷启动不重复全量刷新）。
 * @param showUpdateSettings iOS 走 App Store 时传 `false`，设置页不显示更新入口。
 */
@Suppress("RememberReturnType")
@Composable
fun CourseScheduleApp(
    privacyConsented: Boolean = true,
    resumeCount: Int = 0,
    isInFreeformWindow: Boolean = false,
    onSystemBarsChanged: (statusDark: Boolean, navDark: Boolean) -> Unit = { _, _ -> },
    onHolidayDataChanged: () -> Unit = {},
    onPickWallpaperImage: (((ImageBitmap?) -> Unit) -> Unit)? = null,
    onShareSchedule: ((scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit)? = null,
    onReadSharedFileText: ((String) -> String?)? = null,
    sharedFileIntent: SharedFileIntent? = null,
    onShareIntentConsumed: () -> Unit = {},
    settingsPane: (@Composable (SettingsPaneArgs) -> Unit)? = null,
    todayAssistantCard: (@Composable (TodayAssistantCardData) -> Unit)? = null,
    updateController: UpdateController? = null,
    showUpdateSettings: Boolean = true,
) {
    // 语言偏好只在根组合恢复一次：AppLanguage.set 自己落盘，重建后读回同值。
    remember { AppLanguage.init(LanguageStore.load()) }

    val viewModel: CourseViewModel = courseViewModel()
    val scheduleViewModel: ScheduleViewModel = scheduleViewModel()
    val settingsViewModel: SettingsViewModel = settingsViewModel()
    val shiftViewModel: ShiftViewModel = shiftViewModel()
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()

    // 底栏选中 tab 与主 pager 共用同一份可保存状态，进程被回收后能一起恢复。
    val selectedTabState = rememberSaveable(
        saver = Saver<MutableIntState, Int>(
            save = { it.intValue },
            restore = { mutableIntStateOf(it) },
        )
    ) {
        mutableIntStateOf(if (defaultHomepage == "今日") 0 else 1)
    }

    val state = remember(viewModel, scheduleViewModel, settingsViewModel, shiftViewModel) {
        CourseScheduleAppState().apply {
            this.viewModel = viewModel
            this.scheduleViewModel = scheduleViewModel
            this.settingsViewModel = settingsViewModel
            this.shiftViewModel = shiftViewModel
            attachSelectedTabState(selectedTabState)
        }
    }

    // ── 1. 宿主参数（普通字段，重组期直接覆盖）───────────────────────────────
    with(state) {
        this.privacyConsented = privacyConsented
        this.resumeCount = resumeCount
        this.isInFreeformWindow = isInFreeformWindow
        this.onSystemBarsChanged = onSystemBarsChanged
        this.onHolidayDataChanged = onHolidayDataChanged
        this.onPickWallpaperImage = onPickWallpaperImage
        this.onShareSchedule = onShareSchedule
        this.onReadSharedFileText = onReadSharedFileText
        this.sharedFileIntent = sharedFileIntent
        this.onShareIntentConsumed = onShareIntentConsumed
        this.settingsPane = settingsPane
        this.todayAssistantCard = todayAssistantCard
        this.updateController = updateController
        this.showUpdateSettings = showUpdateSettings
    }

    with(state) {
        // ── 2. 组合期上下文 ────────────────────────────────────────────────────
        router = LocalAppRouter.current
        hostNavigation = LocalHostNavigation.current
        settingsScrollBehavior = rememberSharedScrollBehavior()
        todayScrollBehavior = rememberSharedScrollBehavior()
        scheduleScrollBehavior = rememberSharedScrollBehavior()

        isDark = isAppDarkTheme()
        // 状态栏跟页面实际深浅，导航栏图标始终跟应用设置（独立偏好 key，与全局主题开关隔离）。
        persistedScheduleThemeMode = rememberScheduleThemeMode()
        appSettingDark = rememberAppSettingDark()

        windowInfo = LocalWindowInfo.current
        val density = LocalDensity.current
        densityProvider = density
        detailLayerLayoutDirection = LocalLayoutDirection.current
        switchLayerLayoutDirection = LocalLayoutDirection.current
        isTablet = isTabletWidth()
        screenWPx = with(density) { screenWidthDp().dp.toPx() }
        screenHPx = with(density) { screenHeightDp().dp.toPx() }
        warmupBlurPx = with(density) { 24.dp.toPx() }
        latestScreenWPx = screenWPx
        latestScreenHPx = screenHPx
        screenCornerRadius = rememberScreenCornerRadiiPx()
        hapticFeedback = LocalHapticFeedback.current
        coroutineScope = rememberCoroutineScope()

        // ── 3. 采样层：壁纸一层，各页内容各一层，再组合成「比底栏高一层」──
        // 壁纸单独录一层（屏幕坐标、静态）：页面内顶栏/菜单采「壁纸 + 本页内容」时，
        // 壁纸必须来自屏幕坐标的层，页层才能保持随页面平移。
        wallpaperBackdrop = rememberLayerBackdrop()
        // 各页面只录自己的内容，不含顶栏/菜单，否则底栏/浮层会采到自己的 chrome 形成循环。
        todayContentBackdrop = rememberLayerBackdrop()
        scheduleContentBackdrop = rememberLayerBackdrop()
        shiftContentBackdrop = rememberLayerBackdrop()
        settingsContentBackdrop = rememberLayerBackdrop()
        courseManageContentBackdrop = rememberLayerBackdrop()
        switchScheduleContentBackdrop = rememberLayerBackdrop()
        mainContentBackdrop = rememberCombinedBackdrop(
            wallpaperBackdrop,
            todayContentBackdrop,
            scheduleContentBackdrop,
            shiftContentBackdrop,
            settingsContentBackdrop,
            courseManageContentBackdrop,
            switchScheduleContentBackdrop,
        )
        // 「回到今日 / 本周」悬浮钮画在底栏之下、同属底部 chrome：「采底栏」的组合层要带上它。
        // 一层只能有一个录制点，所以它单独一层再和底栏层一起进 overlayGlassBackdrop。
        backToNowSampleBackdrop = rememberLayerBackdrop()
        // 宿主自己画底栏（鸿蒙 ArkUI）时 Compose 一个像素都不画，底栏层永远录不到东西：
        // 直接从组合层里摘掉，别让每个玻璃节点白跑一次采样。
        if (LocalNativeBottomBarHost.current?.ownsBottomBar == true) {
            overlayGlassBackdrop = rememberCombinedBackdrop(
                mainContentBackdrop, backToNowSampleBackdrop
            )
        } else {
            bottomBarSampleBackdrop = rememberLayerBackdrop()
            overlayGlassBackdrop = rememberCombinedBackdrop(
                mainContentBackdrop, bottomBarSampleBackdrop, backToNowSampleBackdrop
            )
        }

        // ── 4. 数据订阅 ────────────────────────────────────────────────────────
        // 这些 collect 必须留在本组合作用域：子组合读的是 state 的普通字段，靠这里的重组带下去。
        totalWeeks = viewModel.totalWeeks.collectAsState().value
        currentWeek = viewModel.currentWeek.collectAsState().value
        classStartTime = viewModel.classStartTime.collectAsState().value
        morningSections = settingsViewModel.morningSections.collectAsState().value
        afternoonSections = settingsViewModel.afternoonSections.collectAsState().value
        eveningSections = settingsViewModel.eveningSections.collectAsState().value
        configuredSectionTimes = settingsViewModel.sectionTimes.collectAsState().value
        holidayDataRevision = HolidayManager.dataRevision.collectAsState().value
        smartWeekend = settingsViewModel.smartWeekend.collectAsState().value
        dataVersion = viewModel.dataVersion.collectAsState().value
        isShiftMode = shiftViewModel.isShiftMode.collectAsState().value
        courses = viewModel.courses.collectAsState().value

        teachingWeekRules = remember(teachingWeekRepository, dataVersion, classStartTime) {
            teachingWeekRepository.getTeachingWeekReorganizations()
        }
        // 节假日 / 调休保存后需能重算跳周；resume 时刷新版本号。
        holidayVersion = remember(resumeCount, holidayDataRevision) {
            HolidayManager.getVersion()
        }

        // 首帧搭配：壁纸位图（宿主已同步解码）+ 卡片参数一起读，同步装好。
        // 必须在画 UI 之前 —— 首帧就是用户设定，不需要等一帧再跳，也不需要过渡动画。
        ensureInitialCombination()

        // 跟手高光的淡变进度。动画帧写进 `glowAlpha`（snapshot state），组合期不读它 ——
        // 两个消费者（提饱和遮罩、跟手高光）都只在 draw 阶段读，订阅就落在 draw 上。
        // 换成 `animateFloatAsState(...).value` 会让整个外壳在 150ms 里逐帧重组。
        val glowProgress = remember { Animatable(if (glowShowing) 1f else 0f) }
        LaunchedEffect(glowShowing) {
            glowProgress.animateTo(
                targetValue = if (glowShowing) 1f else 0f,
                animationSpec = tween(150),
            ) { glowAlpha = value }
        }

        // 长按课程菜单时整页轻微模糊；空白格菜单不糊。
        LaunchedEffect(shortcutMenuVisible, shortcutMenuCourse) {
            val blur = shortcutMenuVisible && shortcutMenuCourse != null
            shortcutMenuBlurRadius.animateTo(
                targetValue = if (blur) 16f else 0f,
                animationSpec = tween(if (blur) 280 else 250),
            )
        }

        // ── 5. 副作用：云同步 / 生命周期刷新 ───────────────────────────────────
        LaunchedEffect(privacyConsented) {
            if (!privacyConsented) return@LaunchedEffect
            val syncManager = SyncManager.getInstance()
            val repository = CourseRepository()
            val webDavManager = WebDavManager { onHolidayDataChanged() }
            syncManager.start(repository, webDavManager)
            // 备份 / 恢复后刷新 ViewModel 内存缓存
            syncManager.onSyncCompleted = {
                viewModel.refreshEssentialData()
                viewModel.reloadCourses()
                settingsViewModel.refreshSettings()
            }
        }

        LaunchedEffect(resumeCount) {
            // 作息按日期自动切换：跨过生效日期后补一次提醒重排。只比对 prefs 里记的 key，
            // 几乎零开销，冷启动这一次也跑。
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

        // 节假日 / 调休保存不走课程 reload：resume 时对比版本号，变了才 bump dataVersion。
        val seenHolidayVersion = remember { mutableLongStateOf(HolidayManager.getVersion()) }
        LaunchedEffect(resumeCount, holidayDataRevision) {
            val version = HolidayManager.getVersion()
            if (version != seenHolidayVersion.longValue) {
                seenHolidayVersion.longValue = version
                viewModel.bumpDataVersion()
            }
        }

        // ── 6. 副作用：主题 / 系统栏 / 尺寸 ────────────────────────────────────
        LaunchedEffect(effectiveIsDark, appSettingDark) {
            onSystemBarsChanged(effectiveIsDark, appSettingDark)
        }

        // 横竖屏（含自由窗口）尺寸变化：把当前显示中的变换从旧屏重映射到新屏。
        LaunchedEffect(screenWPx, screenHPx) {
            val (prevW, prevH) = wallpaperScreenRef
            if (prevW == screenWPx && prevH == screenHPx) return@LaunchedEffect
            val bitmap = wallpaperBitmap
            if (bitmap != null && prevW > 0f && prevH > 0f) {
                val (newOffset, newScale) = remapWallpaperForScreen(
                    wallpaperOffset, wallpaperScale, bitmap, prevW, prevH, screenWPx, screenHPx
                )
                wallpaperOffset = newOffset
                wallpaperScale = newScale
                updateCurrentCombination { it.copy(offset = newOffset, scale = newScale) }
                WallpaperStore.saveTransform(newOffset, newScale)
            }
            wallpaperScreenRef = screenWPx to screenHPx
        }

        // 开洞编辑：主内容与封面快照必须同帧启动，否则会看见两层错开缩放。
        LaunchedEffect(isWindowCutoutActive) {
            if (!isWindowCutoutActive) {
                cutoutMainScale.animateTo(1f, tween(400, easing = CustomizePageEasing))
                return@LaunchedEffect
            }
            val combination = combinations.getOrNull(currentCombinationIndex)
            if (combination != null) {
                // combinations 在加载 / 旋转时已同步为当前屏坐标系的 offset/scale
                wallpaperBitmap = combination.bitmap
                wallpaperOffset = combination.offset
                wallpaperScale = combination.scale
                wallpaperScreenRef = latestScreenWPx to latestScreenHPx
            }
            cutoutMainScale.snapTo(0.65f)
            launch {
                cutoutMainScale.animateTo(0.75f, tween(400, easing = CustomizePageEasing))
            }
            if (customizeSnapshot == null) return@LaunchedEffect
            launch {
                customizeCoverScale.animateTo(
                    0.75f, tween(CUSTOMIZE_COVER_SCALE_MILLIS, easing = CustomizePageEasing)
                )
            }
            launch {
                delay(CUSTOMIZE_COVER_SCALE_MILLIS.toLong().milliseconds)
                customizeCoverAlpha.animateTo(
                    0f, tween(CUSTOMIZE_COVER_FADE_MILLIS, easing = FastOutSlowInEasing)
                )
                customizeCoverActive = false
            }
        }

        // 首帧后预热 RenderEffect，避免第一次开 BlurBottomSheet 掉帧。
        BlurWarmup(
            enabled = AppMaterialSettings.glassEffectsEnabled(),
            backdrop = mainContentBackdrop,
            blurPx = warmupBlurPx,
        )

        // ── 7. 副作用：排班模式 ────────────────────────────────────────────────
        // 设置页是二级页，只投递请求；loading 覆盖层、pager 重排、VM 切换都留在常驻底座执行。
        // 用 snapshotFlow 而不是 key：pending 变化不该把整个 CourseScheduleApp 重组一遍。
        LaunchedEffect(Unit) {
            snapshotFlow { ShiftModeRequest.pending }.collect { pending ->
                val target = pending ?: return@collect
                // 先消费再执行，避免同一个请求被重放
                ShiftModeRequest.consume()
                FeatureLog.shift(if (target) "enter_loading" else "exit_loading")
                showShiftLoading = true
                isExitingShift = !target
            }
        }

        // ── 8. 副作用：主 tab / 周页 / 今日页的位置 ───────────────────────────
        // 侧栏伸缩后强制续录 + 顶栏模糊层重采样，否则采样停在旧帧，要等切页才恢复。
        LaunchedEffect(TabletNavSideState.expanded, navBarStyle) {
            if (navBarStyle != "rail") return@LaunchedEffect
            railForceRecordFrames[0] = 12
            delay(360.milliseconds)
            railBlurResampleEpoch.intValue++
        }

        // 平板 / iOS 纵向 pager 的初始页与页数；手机端自绘平移（见 MainTabPosition）。
        // pageCount 必须在首帧前写入，否则第一次点底栏会夹限到 0。
        val tabInitial = selectedTab.coerceIn(0, mainPagerPageCount - 1)
        val switchTabState = rememberPageSwitchState(tabInitial)
        val verticalTabPager = rememberPagerState(
            initialPage = tabInitial,
            pageCount = { mainPagerPageCount },
        )
        mainTabSwitchState = switchTabState
        verticalMainPagerState = verticalTabPager
        switchTabState.pageCount = mainPagerPageCount
        mainTabPosition = remember(useVerticalMainPager, switchTabState, verticalTabPager) {
            if (useVerticalMainPager) PagerMainTabPosition(verticalTabPager)
            else SwitchMainTabPosition(switchTabState)
        }
        // 容器形态切换后按当前 tab 对齐位置。
        LaunchedEffect(mainTabPosition) {
            mainTabPosition.snapTo(selectedTab.coerceIn(0, mainPagerPageCount - 1))
        }

        LaunchedEffect(mainPagerPageCount) {
            if (shiftModeInitialized && isShiftMode) return@LaunchedEffect
            val lastIndex = (mainPagerPageCount - 1).coerceAtLeast(0)
            // 页数收口按位置判断，不看 currentPage。
            if (selectedTab > lastIndex || mainTabPosition.currentPage > lastIndex) {
                selectedTab = lastIndex
                mainTabPosition.snapTo(lastIndex)
            }
        }

        // 二级页侧栏点选主 tab（无转场回来后处理）
        LaunchedEffect(TabletNavSideState.pendingMainTab) {
            val pending = TabletNavSideState.pendingMainTab
            if (pending !in 0..4 || !shiftModeInitialized) return@LaunchedEffect
            if (pending == 2 && navBarStyle != "rail") {
                // 手机 / 排班的设置已是二级页：深链直接压栈，不碰 pager
                router.navigate(AppRoute.Settings)
            } else {
                // 手机端只剩 2 页（排班 1 页），侧栏带过来的页号要按实际页数收口
                val target = pending.coerceAtMost(mainPagerPageCount - 1)
                selectedTab = target
                mainTabPosition.snapTo(target)
            }
            TabletNavSideState.pendingMainTab = -1
        }

        LaunchedEffect(isShiftMode) {
            if (shiftModeInitialized) {
                selectedTab = if (isShiftMode) 0 else if (defaultHomepage == "今日") 0 else 1
                mainTabPosition.snapTo(selectedTab)
            }
            shiftModeInitialized = true
        }

        // ── 9. 副作用：周页 / 今日页的自动落位 ────────────────────────────────
        // 「自动跳周」的基准页：规则改了要重算默认值，但不能把用户手动浏览的页拉回去。
        autoAdvancePage = remember(
            currentWeek,
            totalWeeks,
            currentDayOfWeek,
            smartWeekend,
            holidayVersion,
            dataVersion,
            teachingWeekRules,
        ) {
            if (settingsViewModel.shouldAdvanceToNextWeek(currentDayOfWeek, currentWeek)) {
                (basePage + 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
            } else basePage
        }

        // 周页 / 今日页共用一条位置来源；pageCount 在首帧前写入。
        weekPageState = rememberPageSwitchState(autoAdvancePage).also { it.pageCount = totalWeeks }
        todayPageState = rememberPageSwitchState(todayMaxDateOffset)
            .also { it.pageCount = todayMaxDateOffset * 2 }

        // 上一次的「周 / 自动页」，只给下面这个 effect 用：普通字段，写它不会触发重组。
        val lastAuto = remember { intArrayOf(currentWeek, autoAdvancePage) }
        LaunchedEffect(currentWeek, totalWeeks, autoAdvancePage) {
            val weekChanged = currentWeek != lastAuto[0]
            val onAutoPage = weekPageState.currentPage == lastAuto[1]
            if ((weekChanged || onAutoPage) && weekPageState.currentPage != autoAdvancePage) {
                weekPageState.snapTo(autoAdvancePage)
            }
            lastAuto[0] = currentWeek
            lastAuto[1] = autoAdvancePage
        }

        // 今日页默认选中今天。必须在子组合之前写：今日页第一帧就按正确的那一天排版，
        // 否则会先画错一天再跳。用 remember 包住是为了只写一次，且不在组合期订阅这个值
        //（订阅会让每次点日期都重组整个外壳）。
        remember { if (todaySelectedDayOfWeek == 0) todaySelectedDayOfWeek = currentDayOfWeek }

        // 切 tab 时停止周页 / 今日页的平移与回弹，并把今日页吸到最近的整页。
        LaunchedEffect(mainTabPosition.currentPage) {
            todayPageState.cancel()
            val visual = todayPageState.currentPage + todayPageState.offsetFraction
            val target = visual.roundToInt()
            if (abs(visual - target) > 0.001f) todayPageState.snapTo(target)
            weekPageState.cancel()
        }

        // 跳周落页后撤掉标题覆盖。
        LaunchedEffect(weekPageState.currentPage, pendingViewingWeek) {
            if (pendingViewingWeek >= 1 && weekPageState.currentPage + 1 == pendingViewingWeek) {
                pendingViewingWeek = 0
            }
        }

        // dataVersion + courses 引用都进 key：调课 size 可能不变，只靠 size 会让智能周末的星期行停在旧值。
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
                TeachingWeekReorganization.datesForTeachingWeek(
                    LocalDate.parse(classStartTime.replace("/", "-")),
                    currentViewingWeek,
                    teachingWeekRules,
                )
            } catch (_: Exception) {
                emptyList()
            }
        }

        // 详情页逐帧复用的图层与卡片快照图层（跨组合存活）。
        retainedDetailLayer = rememberGraphicsLayer()
        screenGraphicsLayer = rememberGraphicsLayer()
        // 转位图用的临时小图层：只承载卡片那一块，避免整屏 toImageBitmap（12MB + 整屏回读）。
        retainedSwitchCardScratchLayer = rememberGraphicsLayer()
        scheduleScrollState = rememberScrollState()
        todayListState = rememberLazyListState()

        // 读回上次会话退出时固化的卡片快照：位图活不过进程，只有文件能留到下次启动。
        LaunchedEffect(Unit) {
            if (retainedSwitchCardBitmap == null) {
                retainedSwitchCardBitmap = SwitchCardSnapshotStore.load()
            }
        }

        // ── 10. 副作用：内容区手势与宿主 chrome ────────────────────────────────
        latestMainTabSelected = onMainTabSelected
        // 内容区（切天 / 切周）一有手势：立刻取消未完成的主 tab 平移，吸回当前页，避免露出隔壁页。
        // 固定实例交给 [mainContentNestedScroll] 持有，每次重组重建会打断进行中的手势判定。
        val cancelMainTabAnim: () -> Unit = {
            if (mainTabPosition.isAnimating || abs(mainTabPosition.offsetFraction) > 0.001f) {
                val target = mainTabPosition.currentPage
                    .coerceIn(0, (mainTabPosition.pageCount - 1).coerceAtLeast(0))
                coroutineScope.launch {
                    mainTabPosition.cancel()
                    mainTabPosition.snapTo(target)
                    // 底栏与当前页对齐，避免 tab 还停在未完成的目标页
                    if (selectedTab != target) selectedTab = target
                }
            }
        }
        mainContentNestedScroll = remember(mainTabPosition) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // 横向（手机主 tab）与纵向（平板主 tab）手势都取消未完成的切页动画
                    if (source == NestedScrollSource.UserInput &&
                        (abs(available.x) > 1f || abs(available.y) > 1f)
                    ) {
                        cancelMainTabAnim()
                    }
                    return Offset.Zero
                }
            }
        }

        // 宿主顶栏 / 底栏的按钮回调。引用固定：宿主（iOS）按引用比对，每次重组新建会打断它的状态。
        val hostSwitchSchedule: () -> Unit = remember(state) { { openScheduleLibrary() } }
        val hostJumpWeek: () -> Unit = remember(state) { { viewModel.showJumpWeekDialog() } }
        val hostCourseManage: () -> Unit = remember(state, router) {
            { router.navigate(AppRoute.CourseManage) }
        }
        val hostEnterCustomize: () -> Unit = remember(state) { { enterCustomizePage() } }
        val hostJumpToDate: () -> Unit = remember(state) { { todayJumpToDateTrigger++ } }

        DisposableEffect(hostNavigation) {
            val host = hostNavigation
            val onTab: (Int) -> Unit = { index ->
                // 底栏最后一格是设置页**入口**（不是 pager 页）：页号越界即设置，
                // 手机普通模式 2 页 / 排班 1 页，两种都靠这个判据收口。
                if (index >= mainPagerPageCount) router.navigate(AppRoute.Settings)
                else latestMainTabSelected(index)
            }
            host?.onTabSelected = onTab
            onDispose {
                host?.let {
                    if (it.onTabSelected === onTab) it.onTabSelected = null
                    it.scheduleLibraryActive = false
                    it.scheduleLibraryTransitionActive = false
                    it.onOpenSwitchSchedule = null
                    it.onJumpWeek = null
                    it.onCourseManage = null
                    it.onEnterCustomize = null
                    it.onJumpToDate = null
                    it.keepsNativeHeaderDuringCutout = false
                    it.updateDetailBackgroundBlur(0f)
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
                // Swift 侧读这些值自行施加与 Compose 主内容 / 底栏同一套开洞变换
                host.cutoutScale = cutoutMainScale.value
                host.cutoutCenterYRatio = cutoutCenterYRatio
                host.cutoutCenterXRatio = cutoutCenterXRatio

                host.scheduleLibraryActive =
                    nativeBottomBar && !isTablet && showSwitchSchedule && !switchExiting
                host.scheduleLibraryTransitionActive = switchPreparingLayer || switchAnimRunning ||
                        switchPendingReverse || (showSwitchSchedule && switchExiting)
                // 不含 `!showDetail`：详情页盖满全屏，原生顶栏本就被挡住。
                host.showsNativeHeader = nativeBottomBar && !isTablet && !isWindowCutoutActive &&
                        !showCustomizePage && (!showSwitchSchedule || switchExiting)
                // 课表外观页（含开洞进出场）不清空原生顶栏：宿主拿 cutoutScale 自己施加同一套变换，
                // 右上角菜单跟着一起移动缩小，而不是消失。
                host.keepsNativeHeaderDuringCutout = nativeBottomBar && !isTablet &&
                        (showCustomizePage || isWindowCutoutActive)

                host.onOpenSwitchSchedule = hostSwitchSchedule
                host.onJumpWeek = hostJumpWeek
                host.onCourseManage = hostCourseManage
                host.onEnterCustomize = hostEnterCustomize
                host.onJumpToDate = hostJumpToDate
            }
        }

        // 宿主底栏画在 Compose 之外，主界面这几层全屏内容盖不住它，只能整条收起。
        val nativeBottomBarHost = LocalNativeBottomBarHost.current
        val bottomBarSuppressed = showCustomizePage || showSwitchSchedule || showDetail ||
                isWindowCutoutActive
        SideEffect { nativeBottomBarHost?.suppressed = bottomBarSuppressed }

        // 推给原生 chrome 的纵向位移：引用固定，避免每帧新建 lambda 让宿主侧比对失败。
        pushCutoutChromeOffset = remember(hostNavigation) {
            { offsetY: Double -> hostNavigation?.onCutoutChromeOffsetY?.invoke(offsetY) }
        }
    }

    // ── 11. 界面 ──────────────────────────────────────────────────────────────
    with(state) {
        // 开洞编辑器必须画在不透明的深底上：它自己要露出下面那一层真实主内容。
        val shellBackground = if (showCustomizePage) Color(0xFF1A1A1A) else MiuixTheme.colorScheme.surface
        Box(modifier = Modifier.fillMaxSize().background(shellBackground)) {
            // 材质档位必须进 remember key：切档时这里要当场重算，否则卡片模糊 / 折射要等下次重启。
            val materialLevel = AppMaterialSettings.level
            val materialNone = materialLevel == AppMaterialSettings.NONE
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
                // 无材质档：卡片模糊 / 折射渲染强制关（设置项已锁死，存档保留原值）。
                // 卡片仍采壁纸 —— 着色程度 / 不透明度照常生效，只是不挂玻璃采样层。
                if (materialNone) {
                    base.copy(cardBlurRadius = 0f, cardRefraction = CardRefractionLevel.OFF)
                } else {
                    base
                }
            }

            // 液态玻璃重录的跳帧指纹：结构量变了才 update → markNeedsRecord。
            // 必须 remember 出稳定 List，否则 equals 失败会强制重录（颜色 getter 是组合期的，
            // 先读到局部变量再进 remember）。
            val dynamicColor = MiuixTheme.isDynamicColor
            val scheme = MiuixTheme.colorScheme
            val themeBackdropKey = remember(
                dynamicColor,
                scheme.background,
                scheme.primary,
                scheme.secondaryContainer,
                scheme.tertiaryContainer,
            ) {
                listOf(
                    dynamicColor,
                    scheme.background,
                    scheme.primary,
                    scheme.secondaryContainer,
                    scheme.tertiaryContainer,
                )
            }
            val liquidGlassRecordKey = remember(
                mainTabPosition.currentPage,
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
                weekPageState.settledPage,
                themeBackdropKey,
            ) {
                listOf(
                    mainTabPosition.currentPage, isShiftMode, showDetail, showCustomizePage,
                    showSwitchSchedule, isWindowCutoutActive, shortcutMenuVisible, isDraggingCard,
                    floatingCardVisible, dataVersion, currentWeek, totalWeeks,
                    currentCombinationIndex, effectiveIsDark, wallpaperBitmap,
                    TabletNavSideState.expanded, scheduleShowCourseDetail.value,
                    weekPageState.settledPage, themeBackdropKey,
                )
            }

            // ⚠ 这些动画值不要写成 `rememberUpdatedState(x.value)`：那是在组合期读
            //   `Animatable.value`，长按菜单模糊 / 开洞缩放 / 面板位移期间外壳会逐帧重组
            //   （实测长按后每 11~14ms 一次，整张课表跟着重跑）。lambda 里直接读即可，
            //   它只在 draw 阶段执行，订阅落在 draw 上。
            val latestIsWindowCutout by rememberUpdatedState(isWindowCutoutActive)
            val latestShowCustomize by rememberUpdatedState(showCustomizePage)
            val latestIsCustomizeExiting by rememberUpdatedState(isCustomizeExiting)

            // 采样层重录谓词：闭包自带比较状态，只能建一份（两个节点各调一次会把帧计数双倍扣掉）。
            // 滚动时主内容在变，必须重录；翻页动画不在其列 —— 每帧重录等于整棵内容树在 300ms
            // 里重画 60 遍，落页后由闭包内的 settledPage 变化补录一帧。
            val liquidGlassMustRecord: () -> Boolean = remember(
                scheduleScrollState,
                todayListScrollInProgress,
                weekPageState,
                todayPageState,
                mainTabPosition,
            ) {
                // 侧栏进度只在 draw 阶段读，不进组合
                val navSampling = TabletNavSamplingTracker()
                var lastBlurEpoch = -1
                // 今日页上一次落定的页号：只在落页后补录一次。用 settledPage 而不是 currentPage ——
                // currentPage 在翻到一半就变了，那一帧补录下来的仍是半页内容。
                // 初值给 -1 而不是读 settledPage：在 remember 块里读会订阅组合，每翻一页白重组整个外壳。
                var lastTodayPage = -1
                {
                    val epoch = railBlurResampleEpoch.intValue
                    val epochBumped = epoch != lastBlurEpoch
                    lastBlurEpoch = epoch
                    val forceRail = railForceRecordFrames[0] > 0
                    if (forceRail) railForceRecordFrames[0] = railForceRecordFrames[0] - 1
                    val todayPage = todayPageState.settledPage
                    val todayPageChanged = todayPage != lastTodayPage
                    if (todayPageChanged) lastTodayPage = todayPage

                    scheduleScrollState.isScrollInProgress ||
                            todayListScrollInProgress.value ||
                            todayPageChanged ||
                            mainTabPosition.isAnimating ||
                            navSampling.moved() ||
                            forceRail ||
                            epochBumped ||
                            // 开洞编辑时主内容持续缩放，绝不能停录
                            latestIsWindowCutout ||
                            (latestShowCustomize && latestIsCustomizeExiting) ||
                            shortcutMenuBlurRadius.value > 0.01f ||
                            managePageBlurRadius.value > 0.01f ||
                            // 非静止端点视为动画进行中
                            (cutoutMainScale.value != 1f && cutoutMainScale.value != 0.75f) ||
                            sheetOffsetY.value != 0f ||
                            customizeExitScale.value != 1f ||
                            customizeCoverScale.value != 1f ||
                            customizeCoverAlpha.value != 1f
                    // ⚠ 拖课卡（isDraggingCard）也刻意不在此列：浮动卡片画在录制层**之上**，
                    //   根本不进 backdrop 像素；原卡隐藏 / 落点高亮由 recordKey 的
                    //   isDraggingCard 在起手、收手各补一帧。
                }
            }

            // 卡片柔光样式只有一份：网格（采静态壁纸）与浮层（采实时录制层）共用，
            // 采样源不同但参数不可能漂移。静态壁纸采样源也只建一份（烘焙一座金字塔，多建就是双倍显存）。
            val scheduleGlassStyle = rememberScheduleGlassStyle(displayAppearance)
            val scheduleGlassSurface = rememberScheduleGlassSurface(
                state = state,
                scheduleGlassStyle = scheduleGlassStyle,
                wallpaperImage = wallpaperBitmap,
                originalWallpaperImage = originalWallpaperBitmap,
                enabled = !materialNone,
            )

            CourseScheduleAppMainContent(
                state = state,
                displayAppearance = displayAppearance,
                chromeBackdrop = mainContentBackdrop,
                liquidGlassRecordKey = liquidGlassRecordKey,
                liquidGlassMustRecord = liquidGlassMustRecord,
                wallpaperImage = wallpaperBitmap,
                originalWallpaperImage = originalWallpaperBitmap,
                softGlassSurface = scheduleGlassSurface,
            )
            CourseScheduleAppOverlayLayers(
                state = state,
                displayAppearance = displayAppearance,
                isEntryAnimating = showSwitchSchedule && switchAnimForward && switchAnimRunning,
                scheduleGlassStyle = scheduleGlassStyle,
            )
        }

        // 「当前可见页背后有没有壁纸」要在组合期算再交给 SideEffect：只在 SideEffect 里读
        // currentPage 不会订阅，切页不更新。它决定跟手高光的亮度。
        SideEffect { GlassEnvironment.update(hasWallpaper = pageBehindHasWallpaper) }
    }
}

/**
 * 首帧后的 RenderEffect 预热：开一块 1dp、全透明的采样节点，逼渲染管线先编译一次模糊 shader。
 *
 * 不预热的话第一次开 [com.haooz.chedule.ui.components.BlurBottomSheet] 会卡在 shader 编译上。
 * 无材质档 / 平台不支持 RenderEffect 时整块不挂。
 */
@Composable
private fun BlurWarmup(enabled: Boolean, backdrop: GlassBackdrop, blurPx: Float) {
    var ready by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { ready = 1 }
    if (ready == 0 || !enabled || !isRenderEffectSupported()) return
    Box(
        modifier = Modifier
            .size(1.dp)
            .graphicsLayer { alpha = 0f }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousRoundedRectangle(36.dp) },
                effects = { blur(blurPx) },
            )
    )
}
