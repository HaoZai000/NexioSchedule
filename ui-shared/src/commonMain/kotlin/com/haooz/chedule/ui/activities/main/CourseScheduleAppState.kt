package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.WallpaperTransform
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.data.appearance.AppearanceStore
import com.haooz.chedule.data.appearance.ThemeMode
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.UpdateController
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.ui.activities.classschedule.ScheduleGridGeometry
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import com.haooz.chedule.ui.activities.today.TodayAssistantCardData
import com.haooz.chedule.ui.components.PageSwitchState
import com.haooz.chedule.ui.components.SharedFileIntent
import com.haooz.chedule.ui.effects.motion.MORPH_BACKGROUND_SHRINK
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.HostNavigationState
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.TodayViewMode
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.captureScreenWithNativeChrome
import com.haooz.chedule.ui.utils.loadScheduleViewMode
import com.haooz.chedule.ui.utils.loadTodayViewMode
import com.haooz.chedule.ui.utils.saveScheduleViewMode
import com.haooz.chedule.ui.utils.saveTodayViewMode
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.utils.OverScrollState

/**
 * 课表主界面的状态与交互逻辑，由 [CourseScheduleApp] 用 `remember` 持有。
 *
 * 字段分三类，不要混用：
 * 1. `by mutableStateOf(...)`：界面状态，读写都参与重组；
 * 2. 普通 `var`：每次重组由外壳同步的当前值快照，只在外壳里写，子组合读它不订阅；
 * 3. `lateinit var`：组合期创建、注入一次即不变的实例。
 *
 * `Animatable.value` 一律只在 draw 阶段读，避免逐帧动画触发重组。
 */

/** 拖到边缘后每一页之间的驻留时间：卡片停在热区里，每满 500ms 翻一页。 */
private const val EDGE_AUTO_PAGE_INTERVAL_MS = 500L

/** 浮动卡片飞行时长（ns）：粘贴 / 覆盖 / 交换 / 回程共用一条时间轴。 */
private const val FLIGHT_DURATION_NANOS = 480_000_000f

/**
 * 平板主界面专属的切页转场参数（手机走横向平移，iOS 原生底栏不走这里）。
 *
 * 双页共存由 `tabletTransitionFrom` + [CourseScheduleAppState.tabletTabTransition] 驱动：
 * 切页前登记旧页，宿主在转场期间同时组合并绘制新旧两页。进度是 0..1 的 float state，
 * 空闲置 1（终态），不要用 -1 当哨兵 —— 收尾那一帧会被 `coerceIn(0,1)` 当成 0，
 * 新旧页 alpha 当场反转。
 */
internal object TabletTabTransitionSpec {
    /** 旧页 1.0 → SCALE_MIN，新页 SCALE_MIN → 1.0 */
    const val SCALE_MIN = 0.98f
    const val MAX_BLUR_DP = 8f
    const val DURATION_MS = 270
}

@Stable
class CourseScheduleAppState {

    // ── 1. 组合期注入的实例（注入一次，之后不变）────────────────────────────
    lateinit var viewModel: CourseViewModel
    lateinit var scheduleViewModel: ScheduleViewModel
    lateinit var settingsViewModel: SettingsViewModel
    lateinit var shiftViewModel: ShiftViewModel
    lateinit var router: AppRouter
    /** 壁纸单录一层（屏幕坐标、静态）：页内顶栏/菜单采样「壁纸 + 本页内容」时壁纸来自这里。 */
    lateinit var wallpaperBackdrop: LayerBackdrop
    /** 各页只录内容（不含顶栏/菜单），底栏、侧栏、浮层靠它们采样。 */
    lateinit var todayContentBackdrop: LayerBackdrop
    lateinit var scheduleContentBackdrop: LayerBackdrop
    lateinit var shiftContentBackdrop: LayerBackdrop
    lateinit var settingsContentBackdrop: LayerBackdrop
    lateinit var courseManageContentBackdrop: LayerBackdrop
    lateinit var switchScheduleContentBackdrop: LayerBackdrop
    /** 壁纸 + 所有页面内容；底栏、侧栏、详情浮层采样用，不含顶栏/菜单以免循环采样。 */
    lateinit var mainContentBackdrop: GlassBackdrop
    /** 底栏自身的采样层（底栏是玻璃，采 [mainContentBackdrop]）；宿主自绘底栏时不创建。 */
    lateinit var bottomBarSampleBackdrop: LayerBackdrop
    /** 「回到今日 / 本周」悬浮钮的采样层，与底栏一起进 [overlayGlassBackdrop]；自身不采本层。 */
    lateinit var backToNowSampleBackdrop: LayerBackdrop
    /** [mainContentBackdrop] + 底栏（+ 悬浮钮）= 比底栏高一层的采样层，供 OverlayDialog 使用。 */
    lateinit var overlayGlassBackdrop: GlassBackdrop
    lateinit var windowInfo: WindowInfo
    lateinit var densityProvider: Density
    lateinit var detailLayerLayoutDirection: LayoutDirection
    lateinit var switchLayerLayoutDirection: LayoutDirection
    lateinit var hapticFeedback: HapticFeedback
    lateinit var coroutineScope: CoroutineScope
    lateinit var screenGraphicsLayer: GraphicsLayer
    lateinit var retainedDetailLayer: GraphicsLayer
    lateinit var retainedSwitchCardScratchLayer: GraphicsLayer
    lateinit var scheduleScrollState: ScrollState
    lateinit var todayListState: LazyListState
    lateinit var screenCornerRadius: ScreenCornerRadiiPx
    /** 周页横向位置。 */
    lateinit var weekPageState: PageSwitchState

    /** 今日页翻日横向位置。 */
    lateinit var todayPageState: PageSwitchState
    /** 主 tab 位置来源：手机自绘平移，平板 / iOS 纵向 pager。 */
    lateinit var mainTabPosition: MainTabPosition
    /** 手机端平移状态。 */
    lateinit var mainTabSwitchState: PageSwitchState
    /** 竖排分支 pager，手机端为 null。 */
    var verticalMainPagerState: PagerState? = null

    /** 长距跳转的借位页：让相邻页临时显示目标周，滑一页就到位（见 [PagerJumpNeighbor]） */
    val weekJumpNeighbor = PagerJumpNeighbor()

    /** 同上，今日页按天 */
    val todayJumpNeighbor = PagerJumpNeighbor()
    var hostNavigation: HostNavigationState? = null
    var settingsScrollBehavior: SharedScrollBehavior? = null
    var todayScrollBehavior: SharedScrollBehavior? = null
    var scheduleScrollBehavior: SharedScrollBehavior? = null

    /**
     * 首页越界状态：顶栏画在 `Scaffold(topBar)` 槽、内容在 content 槽，两者必须读**同一个**
     * `LocalOverScrollState` 实例，顶栏材质判据的「停在顶部向下回弹」那一支才活。
     * 只建一份（同一时刻只有一页在滚），由 [CourseScheduleAppMainContent] 提供给整棵 Scaffold。
     */
    val mainOverScrollState: OverScrollState = OverScrollState()
    lateinit var mainContentNestedScroll: NestedScrollConnection

    // ── 2. 每次重组同步的当前值（别在这边写）────────────────────────────────
    var isDark: Boolean = false
    var appSettingDark: Boolean = false
    var isShiftMode: Boolean = false
    var totalWeeks: Int = 0
    var currentWeek: Int = 0
    var classStartTime: String = ""
    var morningSections: Int = 0
    var afternoonSections: Int = 0
    var eveningSections: Int = 0
    var configuredSectionTimes: Map<Int, String> = emptyMap()
    var holidayDataRevision: Long = 0L
    var smartWeekend: Boolean = false
    var dataVersion: Int = 0
    var teachingWeekRules: List<TeachingWeekReorganizationRule> = emptyList()
    var holidayVersion: Long = 0L
    var autoAdvancePage: Int = 0
    var courses: List<Course> = emptyList()
    var dayRange: List<Int> = emptyList()
    var weekDates: List<LocalDate> = emptyList()
    var latestScreenWPx: Float = 0f
    var latestScreenHPx: Float = 0f
    var latestMainTabSelected: (Int) -> Unit = {}
    var pushCutoutChromeOffset: (Double) -> Unit = {}
    var isTablet: Boolean = false
    var screenWPx: Float = 0f
    var screenHPx: Float = 0f
    /**
     * 跟手高光/提饱和的淡变进度（0~1）。
     *
     * snapshot state：两个消费者（提饱和遮罩、InteractiveHighlight）都在 draw 阶段读，
     * 失效订阅就落在 draw 上。组合期读它会把整个外壳拖进逐帧重组。
     */
    var glowAlpha by mutableFloatStateOf(0f)
    var persistedScheduleThemeMode: ThemeMode = ThemeMode.FOLLOW_APP

    /**
     * 搭配未加载完时的壁纸测光兜底。直接读盘上快照（与 [buildCombination] 同源），
     * 所以加载完不会跳主题；留 null 会让首帧按系统深浅画，壁纸到位再翻一次。
     */
    var initialCombWallpaperIsLight: Boolean? = AppearanceStore.getWallpaperIsLight()

    // ── 3. 宿主注入的平台能力 ─────────────────────────────────────────────
    var privacyConsented: Boolean = true
    var resumeCount: Int = 0
    var isInFreeformWindow: Boolean = false
    var onSystemBarsChanged: (statusDark: Boolean, navDark: Boolean) -> Unit = { _, _ -> }
    var onHolidayDataChanged: () -> Unit = {}
    var onPickWallpaperImage: (((ImageBitmap?) -> Unit) -> Unit)? = null
    var onShareSchedule: ((scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit)? =
        null
    var onReadSharedFileText: ((String) -> String?)? = null
    var sharedFileIntent: SharedFileIntent? = null
    var onShareIntentConsumed: () -> Unit = {}
    var settingsPane: (@Composable (SettingsPaneArgs) -> Unit)? = null
    var todayAssistantCard: (@Composable (TodayAssistantCardData) -> Unit)? = null
    var updateController: UpdateController? = null
    var showUpdateSettings: Boolean = true

    // ── 4. 界面状态与交互逻辑（原主函数体）──────────────────────────────────
    /**
     * 当前选中的主 tab。值存在 [selectedTabState] 里 —— 宿主注入的是**可保存**的
     * `rememberSaveable` 状态，进程被系统回收后重建时能跟着主 pager 一起恢复。
     */
    private var selectedTabState: MutableIntState = mutableIntStateOf(1)
    var selectedTab: Int
        get() = selectedTabState.intValue
        set(value) {
            selectedTabState.intValue = value
        }

    fun attachSelectedTabState(state: MutableIntState) {
        selectedTabState = state
    }

    var showShiftLoading by mutableStateOf(false)
    var shiftLoadingActive by mutableStateOf(false)
    var isExitingShift by mutableStateOf(false)
    var shiftModeInitialized by mutableStateOf(false)

    // 供今日/课程表卡片玻璃采样：录的是共享壁纸层，不是页内透明占位
    val totalSections get() = morningSections + afternoonSections + eveningSections

    val navBarStyle get() = if (isTablet) "rail" else "standard"
    val nativeBottomBar get() = hostNavigation != null && !isTablet
    val useVerticalMainPager
        get() = isTablet ||
                (nativeBottomBar && hostNavigation?.usesVerticalTabTransitions == true)

    /** 首帧预热 RenderEffect 用的模糊半径（px），由外壳按当前密度算好。 */
    var warmupBlurPx: Float = 0f

    // 侧栏展开状态用全局对象；内容让位只在 layout 读进度，避免整树每帧重组
    // 侧栏伸缩后强制续录 + 顶栏糊层重采样：否则采样停在旧帧，切页才会恢复
    val railBlurResampleEpoch = mutableIntStateOf(0)
    val railForceRecordFrames = intArrayOf(0)
    var detailCourses by mutableStateOf<List<Course>>(emptyList())
    var detailCardLeft by mutableFloatStateOf(0f)
    var detailCardTop by mutableFloatStateOf(0f)
    var detailCardWidth by mutableFloatStateOf(0f)
    var detailCardHeight by mutableFloatStateOf(0f)
    var detailSnapshot by mutableStateOf<TransitionLayerSnapshot?>(null)
    var showDetail by mutableStateOf(false)

    // 详情页退出中：主内容提前恢复绘制（快照垫底），画出来后再撤快照。见 onBackStart。
    var detailExiting by mutableStateOf(false)
    var detailFromToday by mutableStateOf(false)
    var detailTargetWeek by mutableIntStateOf(0)
    var hiddenCourseIds by mutableStateOf(setOf<String>())
    var isDraggingCard by mutableStateOf(false)
    // 被浮层顶替的 (课程 id, 周次)。必须带周次：多周课在每一周都是同一条记录，
    // 只按 id 隐藏会让所有周的原卡一起消失。只隐藏源周/落点周那一份。
    var draggingCourseSlots by mutableStateOf(setOf<Pair<String, Int>>())
    var draggedCardCourse by mutableStateOf<Course?>(null)
    var draggedCardPosition by mutableStateOf(Offset.Zero)
    var draggedCardSize by mutableStateOf(Offset.Zero)

    // 速度/采样时间只在 spring 起始读；offset 走 floatingOffset* 的 graphicsLayer，不进组合
    val dragMotion = DragMotionHolder()
    var draggedWeek by mutableIntStateOf(1)

    // 源卡片所在的**列**星期（几何坐标）。调课日列的数据星期 ≠ 列星期，
    // 落点原格高亮、几何吸附都要用列，写库才用数据星期
    var draggedColumnDay by mutableIntStateOf(1)

    // 拖拽落点检测用网格几何
    var gridGeometry by mutableStateOf<ScheduleGridGeometry?>(null)

    // (dayOfWeek, startSection)，null=无有效落点
    //
    // 普通字段而非 snapshot state：拖拽中每跨一格就变一次，做成 state 会把整个主内容
    // （4 页 pager + 整张课表）拖进「每跨一格全量重组一次」。读它的都是事件处理与弹窗参数，
    // 组合期读一次就够；唯一需要逐格重绘的落点遮罩订阅 [dropTargetRevision]。
    var pendingDropTarget: Pair<Int, Int>? = null
        private set

    /** 落点变更信号：只给落点遮罩订阅，避免逐格重组课表。 */
    var dropTargetRevision by mutableIntStateOf(0)
        private set

    fun setPendingDropTarget(value: Pair<Int, Int>?) {
        if (pendingDropTarget == value) return
        pendingDropTarget = value
        dropTargetRevision++
    }
    var animateInCourseIds by mutableStateOf(setOf<String>())
    var showRescheduleConflictDialog by mutableStateOf(false)
    var pendingConflictCourse by mutableStateOf<Course?>(null)
    /** 第一步选了覆盖/交换：等选择弹窗退出动画结束再开第二步确认弹窗 */
    var pendingConflictResolution by mutableStateOf<ConflictResolution?>(null)
    /** 第二步确认弹窗展示的操作；null = 不显示 */
    var conflictConfirmResolution by mutableStateOf<ConflictResolution?>(null)

    // 退出动画期间保持 true，结束后原卡片 alpha 恢复 1
    var floatingCardVisible by mutableStateOf(false)

    // 手指正拖着浮层：不能拿 floatingCardVisible 当高光开关——它从长按即为 true，
    // 且要等约 360ms 退场动画跑完才回 false，会出现「长按没拖也亮」「松手后不灭」。
    var isFloatingCardDragging by mutableStateOf(false)

    // 入场 0.94→1.04，退场 1.04→1.0
    val floatingScale = Animatable(0.94f)

    // 吸附期间用 floatingOffsetAnim 替代 draggedCardOffset
    var isSnapping by mutableStateOf(false)

    // 浮层平移：拖拽回调非挂起，用 mutableFloatStateOf；只在 graphicsLayer 读，不触发组合
    val floatingOffsetX = mutableFloatStateOf(0f)
    val floatingOffsetY = mutableFloatStateOf(0f)

    // 高光开关 = 正在拖 && 浮层还活着：声明在 Scaffold 之前，Scaffold 内容里的提饱和节点要用它。
    // 一松手 onCourseDragEnd 立刻置 false 开始淡出，不等 360ms 退场动画。
    val glowShowing get() = isFloatingCardDragging && floatingCardVisible
    suspend fun animateFloatState(
        state: MutableFloatState,
        target: Float,
        spec: AnimationSpec<Float>,
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
    var isPasteFlight by mutableStateOf(false)

    // 落地冲击波：周围课程卡按距离延迟涟漪
    var landRippleCenter by mutableStateOf(Offset.Zero)
    var landRippleToken by mutableIntStateOf(0)

    // 冲突悬停：浮层停在目标卡上方等待用户选择
    var isConflictHover by mutableStateOf(false)
    var conflictHoverBobJob by mutableStateOf<Job?>(null)

    // 交换时目标课作为第二浮层交叉飞行
    var swapFlightVisible by mutableStateOf(false)
    var swapFlightCourse by mutableStateOf<Course?>(null)
    var swapFlightOriginCenter by mutableStateOf(Offset.Zero)
    var swapFlightWidth by mutableFloatStateOf(0f)
    val swapFlightOffsetX = Animatable(0f)
    val swapFlightOffsetY = Animatable(0f)
    val swapFlightScale = Animatable(1f)
    var shortcutMenuCourse by mutableStateOf<Course?>(null)
    var shortcutMenuVisible by mutableStateOf(false)
    var shortcutMenuPosition by mutableStateOf(Offset.Zero)
    var shortcutMenuSize by mutableStateOf(IntSize.Zero)
    var shortcutMenuBackdrop by mutableStateOf<GlassBackdrop?>(null)

    // 左移时菜单右边缘对齐卡片右边缘
    var shortcutMenuAnchorWidth by mutableFloatStateOf(0f)

    /** 长按菜单是否给「复制」：时间轴是只读视图，复制/粘贴按节次跨度落位，不给 */
    var shortcutMenuAllowCopy by mutableStateOf(true)

    // (星期, 起始节次)；粘贴目标=长按格
    // columnDay 用于格子高亮；addDay/addWeek 供「添加」默认落到调课来源星期/周
    var emptyCellMenuTarget by mutableStateOf<EmptyCellMenuTarget?>(null)

    // 页面会话级剪贴板，再次复制覆盖，粘贴后保留
    var copiedCourseForPaste by mutableStateOf<Course?>(null)
    var showPasteRangeDialog by mutableStateOf(false)
    var pasteRangeTarget by mutableStateOf<PasteTarget?>(null)
    var showDeleteConfirmDialog by mutableStateOf(false)
    var deleteConfirmCourse by mutableStateOf<Course?>(null)
    var showCustomizePage by mutableStateOf(false)
    var customizeSnapshot by mutableStateOf<ImageBitmap?>(null)
    var snapshotCoverBitmap by mutableStateOf<ImageBitmap?>(null)
    var isCustomizeExiting by mutableStateOf(false)
    val customizeExitScale = Animatable(1f)
    val customizeExitAlpha = Animatable(1f)
    var isWindowCutoutActive by mutableStateOf(false)

    // 动画只作用于快照覆盖层，避免双缩放叠加闪烁
    var customizeCoverActive by mutableStateOf(false)
    val customizeCoverScale = Animatable(1f)
    val customizeCoverAlpha = Animatable(1f)
    var combinations by mutableStateOf(listOf<ScheduleCombination>())
    var currentCombinationIndex by mutableIntStateOf(0)
    var wallpaperBitmap by mutableStateOf(WallpaperStore.currentImageBitmap())
    var wallpaperOffset by mutableStateOf(WallpaperStore.wallpaperOffset)
    var wallpaperScale by mutableFloatStateOf(WallpaperStore.transformScale)

    // offset/scale 当前对应的屏幕尺寸；旋转后按此重映射，避免像素值跨方向复用
    var wallpaperScreenRef by mutableStateOf(0f to 0f)

    // 截快照时临时按该搭配的壁纸亮暗覆盖主题
    var captureThemeActive by mutableStateOf(false)
    var captureThemeIsDark by mutableStateOf<Boolean?>(null)

    // 壁纸主题锁定：课程表页有壁纸时按测光强制浅/深色；今日页仅开启显示壁纸时锁定；设置页跟随系统
    val currentComb get() = combinations.getOrNull(currentCombinationIndex)
    val currentCombIsLight get() = currentComb?.wallpaperIsLight

    // 壁纸异步加载，首帧同步读持久化结果，避免主题跳变
    // 搭配未加载（首帧）才退回 initial，避免无壁纸搭配误用初始测光
    val combIsLight get() = if (currentComb == null) initialCombWallpaperIsLight else currentCombIsLight

    // 独立偏好 key，与全局主题开关隔离
    // 编辑中的临时档位：「应用」才落盘
    var pendingScheduleThemeMode by mutableStateOf<ThemeMode?>(null)
    val scheduleThemeMode get() = pendingScheduleThemeMode ?: persistedScheduleThemeMode

    // 壁纸强制主题：不依赖 selectedTab，三页各自预先算好，切页不再改主题
    // 无壁纸时「默认主题」不生效
    val wallpaperForcedDark: Boolean?
        get() {
            if (isShiftMode) return null
            val light = combIsLight ?: return null
            return when (scheduleThemeMode) {
                ThemeMode.FOLLOW_WALLPAPER -> !light
                ThemeMode.FOLLOW_APP -> null
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
        }

    // 各页预先固定的主题（与 selectedTab 无关）
    // 今日页与课程表页壁纸恒同步，故两者共用同一壁纸锁定主题
    val todayPageForcedDark get() = wallpaperForcedDark

    // 顶栏/底栏等 chrome 跟当前页有效主题
    val forcedDark
        get() = when {
            isShiftMode -> null
            showCustomizePage -> wallpaperForcedDark
            selectedTab == 2 -> null
            selectedTab == 1 -> wallpaperForcedDark
            selectedTab == 0 -> todayPageForcedDark
            else -> null
        }
    val effectiveIsDark get() = forcedDark ?: isDark

    // 状态栏跟页面实际深浅；导航栏图标始终跟应用设置
    // 已应用快照，开洞编辑取消时回退
    var savedWallpaperBitmap by mutableStateOf<ImageBitmap?>(null)
    var savedWallpaperOffset by mutableStateOf(wallpaperOffset)
    var savedWallpaperScale by mutableFloatStateOf(wallpaperScale)
    var savedAppearance by mutableStateOf(AppearanceConfig())

    // 进入搭配页时的原始搭配；滑动切换不更新（退出非应用时还原）
    var originalCombinationIndex by mutableIntStateOf(0)
    var originalWallpaperBitmap by mutableStateOf<ImageBitmap?>(null)
    var originalWallpaperOffset by mutableStateOf(wallpaperOffset)
    var originalWallpaperScale by mutableFloatStateOf(wallpaperScale)

    // 退出（非应用）时还原主题锁定
    var originalWallpaperIsLight by mutableStateOf<Boolean?>(null)

    // 整体还原，避免编辑回调字段残留
    var originalCombination by mutableStateOf<ScheduleCombination?>(null)
    var isApplyingCustomize by mutableStateOf(false)
    var pendingEnterCutout by mutableStateOf(false)

    // 外观从当前搭配派生，编辑只写 combinations；未加载时回退启动缓存防冷启动闪默认值。
    // ⚠ 保持未钳制的纯派生：存档保存（AppearanceStore.saveCardBlur 等）与外观编辑页都走它；
    // 无材质档的「卡片模糊/折射渲染强制关」在 CourseScheduleApp 的 displayAppearance 处做。
    fun currentAppearance(): AppearanceConfig =
        combinations.getOrNull(currentCombinationIndex)
            ?.let { AppearanceConfig.fromCombination(it) }
            ?: AppearanceStore.cachedConfig

    /** 只改当前搭配；下标越界（尚未加载）时忽略。 */
    fun updateCurrentCombination(transform: (ScheduleCombination) -> ScheduleCombination) {
        val idx = currentCombinationIndex
        if (idx in combinations.indices) {
            combinations = combinations.toMutableList().also { it[idx] = transform(it[idx]) }
        }
    }

    fun applyAppearance(value: AppearanceConfig) {
        updateCurrentCombination {
            it.copy(
                cardBlurRadius = value.cardBlurRadius,
                cardAlpha = value.cardAlpha.coerceIn(0f, 1f),
                cardSurfaceAlpha = value.cardSurfaceAlpha.coerceIn(0f, 1f),
                cardHeight = value.cardHeight,
                cardCornerRadius = value.cardCornerRadius,
                wallpaperBrightness = value.wallpaperBrightness,
                showBreakDividers = value.showBreakDividers,
                showWeekGridLines = value.showWeekGridLines,
                cardContentAlignment = value.cardContentAlignment,
                cardTextColor = value.cardTextColor,
                cardTextScale = value.cardTextScale,
                showClassroom = value.showClassroom,
                showTeacher = value.showTeacher,
                cardRefraction = value.cardRefraction,
                cardEdgeThickness = value.cardEdgeThickness,
                cardRim = value.cardRim,
                wallpaperBlur = value.wallpaperBlur
            )
        }
    }

    /**
     * 搭配读取唯一入口：冷启动 IO 路径与伴生缓存路径都走这里。新增外观字段只改这一处
     * （两份逐字段复制的构造漏一处就静默丢字段）。
     */
    fun buildCombination(bitmap: ImageBitmap?) =
        ScheduleCombination(
            bitmap = bitmap,
            offset = Offset(
                AppearanceStore.getOffsetX(),
                AppearanceStore.getOffsetY()
            ),
            scale = AppearanceStore.getScale(),
            snapshot = null,
            cardBlurRadius = AppearanceStore.getCardBlur(),
            cardAlpha = AppearanceStore.getCardAlpha(),
            cardSurfaceAlpha = AppearanceStore.getCardSurfaceAlpha(),
            cardHeight = AppearanceStore.getCardHeight(),
            cardCornerRadius = AppearanceStore.getCardCornerRadius(),
            wallpaperBrightness = AppearanceStore.getWallpaperBrightness(),
            showBreakDividers = AppearanceStore.getShowBreakDividers(),
            showWeekGridLines = AppearanceStore.getShowWeekGridLines(),
            cardContentAlignment = AppearanceStore.getCardContentAlignment(),
            cardTextColor = AppearanceStore.getCardTextColor(),
            cardTextScale = AppearanceStore.getCardTextScale(),
            showClassroom = AppearanceStore.getShowClassroom(),
            showTeacher = AppearanceStore.getShowTeacher(),
            cardRefraction = AppearanceStore.getCardRefraction(),
            cardEdgeThickness = AppearanceStore.getCardEdgeThickness(),
            cardRim = AppearanceStore.getCardRim(),
            wallpaperIsLight = AppearanceStore.getWallpaperIsLight(),
            wallpaperBlur = AppearanceStore.getWallpaperBlur()
        )

    /**
     * 首帧搭配：壁纸位图 + 参数一起读，必须在画 UI 之前调用（见 `CourseScheduleApp`）。
     *
     * 壁纸由宿主在 Application 阶段就后台解码好（[WallpaperStore.synchronousLoader]），
     * 这里只是取结果；没赶上才等它剩下的时间。没有壁纸/平台没提供通道时就是无壁纸搭配。
     */
    fun ensureInitialCombination() {
        if (combinations.isNotEmpty()) return
        val bitmap = WallpaperStore.currentImageBitmap()
            ?: WallpaperStore.synchronousLoader?.invoke()?.also { WallpaperStore.setCached(it) }
        installInitialCombination(bitmap)
    }

    /** 装上首个搭配并把壁纸变换摆到当前屏。 */
    fun installInitialCombination(bitmap: ImageBitmap?) {
        if (combinations.isNotEmpty()) return
        val comb = buildCombination(bitmap)
        // 单搭配恒为下标 0
        combinations = listOf(comb)
        currentCombinationIndex = 0

        val curW = latestScreenWPx
        val curH = latestScreenHPx
        val refW = AppearanceStore.getOffsetRefW()
        val refH = AppearanceStore.getOffsetRefH()
        // 有参考尺寸且与当前屏不一致时重映射；旧数据 ref=0 时原样加载
        val (mappedOffset, mappedScale) =
            if (refW > 0f && refH > 0f && (refW != curW || refH != curH)) {
                remapWallpaperForScreen(comb.offset, comb.scale, comb.bitmap, refW, refH, curW, curH)
            } else {
                comb.offset to comb.scale
            }
        wallpaperBitmap = comb.bitmap
        wallpaperOffset = mappedOffset
        // 不把当前方向的 minScale 写回用户 scale：绘制层已 max(scale, minScale)，
        // 这里烘焙会导致旋转回原方向时缩放被抬高、画面错位
        wallpaperScale = mappedScale
        wallpaperScreenRef = curW to curH
        savedWallpaperBitmap = comb.bitmap
        savedWallpaperOffset = mappedOffset
        savedWallpaperScale = mappedScale
        savedAppearance = AppearanceConfig.fromCombination(comb)
        originalWallpaperBitmap = comb.bitmap
        originalWallpaperOffset = mappedOffset
        originalWallpaperScale = mappedScale
        if (mappedOffset != comb.offset || mappedScale != comb.scale) {
            updateCurrentCombination { it.copy(offset = mappedOffset, scale = mappedScale) }
        }
        WallpaperStore.saveTransform(mappedOffset, mappedScale)
    }

    // 横竖屏（含自由窗口）尺寸变化：把当前显示中的变换从旧屏重映射到新屏
    val cutoutMainScale = Animatable(1f)
    var cutoutCenterYRatio by mutableFloatStateOf(0.5f)
    var cutoutCenterXRatio by mutableFloatStateOf(0.5f)

    // 与 CustomizeScheduleScreen 共享实例，直接读 .value 同帧同步
    val sheetOffsetY = Animatable(0f)

    // 用布尔量而不用整屏位图当哨兵，避免白养 ~10MB Bitmap
    var switchOverlayActive by mutableStateOf(false)
    var switchCardSnapshot by mutableStateOf<TransitionLayerSnapshot?>(null)

    // 形变插值起止依据（窗口坐标系）
    var switchCardBounds by mutableStateOf<Rect?>(null)

    // 页面坐标系 → 窗口坐标系的换算偏移
    var switchContentRootX by mutableFloatStateOf(0f)

    // 提到 Activity 层是为了让液态玻璃重录能在外层读到「滚动中 / 弹窗开合」
    // （liquidGlassMustRecord、liquidGlassRecordKey），不是为了防销毁。
    val scheduleSelectedCourse = mutableStateOf<Course?>(null)
    val scheduleSelectedCourses = mutableStateOf<List<Course>>(emptyList())
    val scheduleShowCourseDetail = mutableStateOf(false)

    // 今日页各日期页独立 LazyListState，这里只收「是否在滚」供液态玻璃重录
    val todayListScrollInProgress = mutableStateOf(false)
    var switchContentRootY by mutableFloatStateOf(0f)
    var switchAnimJob by mutableStateOf<Job?>(null)
    var switchAnimForward by mutableStateOf(false)
    var switchAnimRunning by mutableStateOf(false)

    // 返回前先等课表重载完，再录返回图层
    var switchReloadJob by mutableStateOf<Job?>(null)
    val switchAnimProgress = Animatable(0f)
    val managePageBlurRadius = Animatable(0f)

    // 详情 morph 的整屏背景快照：录一帧主内容后独立缩放，避免实时内容缩放导致的整页重组
    var detailBackgroundSnapshot by mutableStateOf<TransitionLayerSnapshot?>(null)
    val detailBackgroundScale = Animatable(1f)

    // 仅课程长按菜单模糊；空白格菜单不模糊
    val shortcutMenuBlurRadius = Animatable(0f)
    val switchReturnBgScrim = Animatable(0f)

    // 切换课表：进入方向复用「上次退出录到的卡片」，但**图层跨不过组合销毁** ——
    // `drawLayer(x)` 存的是对 x 的引用，切换页一销毁就废。所以退出那一帧把卡片区域固化成位图
    //（位图不依赖组合），下次进入直接画它 ⇒ 进入方向零录制。
    var retainedSwitchCardBitmap by mutableStateOf<ImageBitmap?>(null)
    /**
     * **课程表页面（主界面）这一屏的整屏原生快照**，含 UITabBar / 导航栏（iOS 专有）。
     *
     * 切换课表的进入 morph 需要整屏图当主内容层，而 Compose 的 `screenGraphicsLayer` 录不到
     * 原生 chrome（底栏会缺一条）。所以在点「切换课表」、切换页上屏之前截这一张。
     * Android / 桌面恒为 null。用完立刻释放 —— 整屏图 ~12MB。
     */
    var mainScreenShot by mutableStateOf<ImageBitmap?>(null)

    // 转位图用的临时小图层：只承载卡片那一块，避免整屏 toImageBitmap（12MB + 整屏回读）。
    // 本次形变要画的卡片位图（只有进入方向用；退出方向仍用实时卡片图层）。
    var switchCardBitmap by mutableStateOf<ImageBitmap?>(null)

    // 按需保留绘制指令，供整屏快照与切换课表的形变使用（位图/文件那层见 SwitchCardSnapshotStore）
    class MainSnapshotRequester {
        var lastRecordedToken: Int = 0
    }

    val mainSnapshotRequester = MainSnapshotRequester()
    var mainSnapshotToken by mutableIntStateOf(0)
    suspend fun prepareMainContentLayer() {
        val token = ++mainSnapshotToken
        // record 只保留绘制指令，不分配位图、不做 CPU 光栅化
        while (mainSnapshotRequester.lastRecordedToken < token) {
            withFrameNanos { }
        }
    }

    suspend fun captureMainContentBitmap(includeNativeChrome: Boolean = true): ImageBitmap? {
        mainSnapshotToken++
        // 等两帧：draw 录制完成 + 帧已提交
        withFrameNanos { }
        withFrameNanos { }
        // 必须单独 catch OutOfMemoryError：Bitmap 像素在 native 堆，全屏 toImageBitmap
        return try {
            // 有原生 chrome 时（iOS）优先用原生整屏截图：GraphicsLayer.record()
            // 只录 Compose 绘制的内容，底部原生 TabBar 会整条缺失。
            (if (includeNativeChrome) captureScreenWithNativeChrome() else null)
                ?: screenGraphicsLayer.toImageBitmap()
        } catch (_: OutOfMemoryError) {
            FeatureLog.t("主页", "main_snapshot_oom")
            null
        } catch (_: Exception) {
            null
        }
    }

    val currentDayOfWeek get() = todayLocalDate().dayOfWeek.isoDayNumber
    val teachingWeekRepository = CourseRepository.getInstance()

    // 节假日/调休保存后需能重算跳周；resume 时刷新版本号
    val basePage get() = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
    val todayMaxDateOffset get() = 1000

    // 主 tab 平移容器：仅点底栏 / 侧栏 tab 驱动。
    // 手机 2 页（今日 / 课表，设置已是二级页）；排班模式 1 页；平板 rail 5 页（含设置 / 课程管理 / 切换课表）。
    val mainPagerPageCount get() = when {
        navBarStyle == "rail" -> if (isShiftMode) 2 else 5
        isShiftMode -> 1
        else -> 2
    }

    // 当前可见页背后是不是壁纸，决定高光亮度（见 InteractiveHighlight 的 peak）。
    val pageBehindHasWallpaper
        get() = wallpaperBitmap != null &&
                !isShiftMode &&
                mainTabPosition.currentPage in 0..1

    /**
     * 今日页当前选中的星期几（1..7）。0 = 还没初始化，外壳在首帧前补成今天。
     */
    var todaySelectedDayOfWeek by mutableIntStateOf(0)
    var todayIsToday by mutableStateOf(true)
    var scrollToTodayTrigger by mutableIntStateOf(0)

    /**
     * 程序化跳周的目标周（0 = 无覆盖）
     */
    var pendingViewingWeek by mutableIntStateOf(0)

    // 基线必须用 currentPage：手动翻页过半页标题/日期行就该跟着换
    val currentViewingWeek: Int
        get() {
            val live = weekPageState.currentPage + 1
            val target = pendingViewingWeek
            return if (target >= 1 && target != live) target else live
        }

    // dataVersion + courses 引用都进 key：调课 size 可能不变，只靠 size 会让智能周末星期行停在旧值
    val viewingIsHoliday get() = viewModel.isWeekHoliday(currentViewingWeek)

    // 主界面切页不做圆角裁切：各 tab 页都方角铺满。
    // 平板（rail）走自己的缩放 / 模糊淡入淡出转场（见 TabletTabTransitionSpec），
    // 位置仍是瞬时 snapTo，所以这里依旧不需要为横向平移动画撑满尺寸。
    val mainTabTransitionAnimated get() = navBarStyle != "rail"
    val settingsPageClipModifier
        get() = if (mainTabTransitionAnimated) Modifier.fillMaxSize() else Modifier
    val mainTabAnimation = arrayOfNulls<Job>(1)
    val mainTabGeneration = intArrayOf(0)

    // 平板专属切页转场进度；每帧写 float state，绘制期读，动画不触发任何重组。
    // 初值 1 = 终态（与稳态画面一致），空闲时也停在 1。
    val tabletTabTransition = mutableFloatStateOf(1f)

    // 转场旧页：必须在切页之前登记，否则宿主在切页那一帧就把旧页 dispose 了，
    // 双页共存会变成「旧页销毁后重新组合」，状态和首帧都保不住。
    var tabletTransitionFrom by mutableIntStateOf(-1)

    // 组合期开关：只有转场期间才让页面槽位挂 graphicsLayer。空闲时一个全屏 layer 都不留，
    // 否则侧栏展开 / 折叠重排时玻璃采样会贴着旧层走，展开过程乱闪（折叠方向正常）。
    var tabletTabTransitionActive by mutableStateOf(false)

    /** 平板切 tab：登记旧页 → 立即切页 → 旧/新两页交叉。 */
    private suspend fun playTabletTabTransition(from: Int, switch: suspend () -> Unit) {
        // 三个状态在同一次同步写里落定，任何一步跨帧都会让 active=true 那一帧读到上次的
        // 进度 1（新页 alpha=1）→ 表现成「点击瞬间就切页」。
        tabletTransitionFrom = from
        tabletTabTransition.floatValue = 0f
        tabletTabTransitionActive = true
        switch()
        // 逐帧写 float state：graphicsLayer 订阅 draw 阶段，不用赌 Animatable.value 的可观察性
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                TabletTabTransitionSpec.DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
        ) { value, _ ->
            tabletTabTransition.floatValue = value
        }
        // p=1 时新页 alpha=1 / scale=1 / blur=0，与稳态像素一致：
        // 先清旧页登记再摘 layer，中间没有任何跳变。
        tabletTransitionFrom = -1
        tabletTabTransitionActive = false
    }

    val onMainTabSelected: (Int) -> Unit = { index ->
        // 侧栏「滑选 + clickable」可能对同一次抬手各触发一次：目标页正在切过去时忽略重复调用，
        // 否则第二个任务会掐断正在播的转场。
        val duplicate = index == selectedTab && mainTabAnimation[0]?.isActive == true
        if (!duplicate && index in 0 until mainPagerPageCount &&
            (index != selectedTab || mainTabPosition.currentPage != index)
        ) {
            // 起点必须取「当前真正显示的页」：selectedTab 可能已被别的入口提前写成目标页
            // （预览切页、重复点击），拿它当 from 会让新页被当成旧页，播缩小+模糊的反向动画。
            val from = mainTabPosition.currentPage
            val generation = ++mainTabGeneration[0]
            mainTabAnimation[0]?.cancel()
            selectedTab = index
            mainTabAnimation[0] = coroutineScope.launch {
                try {
                    if (todayPageState.isAnimating) todayPageState.cancel()
                    if (weekPageState.isAnimating) weekPageState.cancel()
                    if (navBarStyle == "rail") {
                        if (mainTabPosition.isAnimating) mainTabPosition.cancel()
                        // from == index 说明目标页已在前台（重复触发）：只对齐位置，不再播转场，
                        // 否则旧/新页角色会落到同一页上。
                        if (from != index) {
                            playTabletTabTransition(from) { mainTabPosition.snapTo(index) }
                        } else {
                            mainTabPosition.snapTo(index)
                        }
                    } else mainTabPosition.animateTo(index)
                    FeatureLog.t(
                        "主页",
                        "tab",
                        "idx=$index shift=$isShiftMode rail=${navBarStyle == "rail"}"
                    )
                } finally {
                    if (mainTabGeneration[0] == generation) {
                        // 动画被手势 / 生命周期掐断时：进度、旧页登记、layer 开关全部归位，
                        // pager 一定对齐目标页，否则会停在「旧页还挂着、页面却没切」的状态。
                        withContext(NonCancellable) {
                            tabletTabTransition.floatValue = 1f
                            tabletTransitionFrom = -1
                            tabletTabTransitionActive = false
                            val target = selectedTab.coerceIn(
                                0,
                                (mainTabPosition.pageCount - 1).coerceAtLeast(0)
                            )
                            if (mainTabPosition.currentPage != target) {
                                mainTabPosition.snapTo(target)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 浮动卡片：落地 / 飞行 / 吸附 ─────────────────────────────────────────
    // 下面这些都是固定实例（`val ... = {}`），不要改成每次重组新建的 lambda：
    // 它们会被传进绘制层与手势回调，换引用等于换掉一整条动画的所有者。
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

    /**
     * 浮层收尾的统一定额：位置、缩放、拖拽残留全部归零。
     *
     * 松手吸附、取消、粘贴飞行、交换飞行走的是同一份收尾，任何一处漏一个字段都会留下
     * 「卡片没了但原卡还隐藏着」这类幽灵状态，所以只留这一个入口。
     */
    private suspend fun settleFloatingCard() {
        isDraggingCard = false
        floatingCardVisible = false
        draggingCourseSlots = emptySet()
        draggedCardCourse = null
        dragMotion.reset()
        setPendingDropTarget(null)
        isSnapping = false
        isPasteFlight = false
        // 重置入场初值，避免下次首帧残留 1.0 造成抖动
        floatingScale.snapTo(0.94f)
        floatingOffsetX.floatValue = 0f
        floatingOffsetY.floatValue = 0f
    }

    /** 直接取消浮层（返回键 / 点空白 / 无有效落点）：先弹回 1.0 再收尾，让原卡片的 alpha 平滑恢复。 */
    val dismissFloatingCard: () -> Unit = {
        stopConflictHover()
        clearSwapFlight()
        // 浮层可能不走 onCourseDragEnd 就被关掉，自动翻周必须一起停
        stopEdgeAutoPage()
        // 高光 / 提示都看它：不在收尾路径里复位会一直亮着
        isFloatingCardDragging = false
        // 浮层都没了，冲突选择 / 二次确认不能留在屏幕上
        showRescheduleConflictDialog = false
        pendingConflictResolution = null
        conflictConfirmResolution = null
        pendingConflictCourse = null
        coroutineScope.launch {
            floatingScale.animateTo(1f, tween(durationMillis = 180))
            settleFloatingCard()
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

    /**
     * 浮动卡片的飞行节奏：位置按缓入缓出插值，缩放 1 → [peakScale] → 1 再轻弹落地，
     * 落地前 80% 触发一次涟漪。粘贴、覆盖、交换、回程四条路径共用同一份时序。
     *
     * @param startX 起始相对偏移（相对浮层锚点）
     * @param endX 终点相对偏移（相对浮层锚点，不是相对起点）
     * @param rippleCenter 涟漪中心（绝对坐标，飞行途中才求值）
     * @param onFrame 每帧回调；交换飞行用它同步第二张卡片的位移与缩放
     */
    private suspend fun runFloatingFlight(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        peakScale: Float = 1.4f,
        rippleCenter: () -> Offset,
        onFrame: suspend (t: Float, scale: Float) -> Unit = { _, _ -> },
    ) {
        val moveEase = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)
        val durationNanos = FLIGHT_DURATION_NANOS
        var rippleFired = false
        val startNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val raw = ((now - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
            val t = moveEase.transform(raw)
            floatingOffsetX.floatValue = startX + (endX - startX) * t
            floatingOffsetY.floatValue = startY + (endY - startY) * t
            val scale = pasteFlightScaleAt(raw, peakScale)
            floatingScale.snapTo(scale)
            onFrame(t, scale)
            // 快落地时先开涟漪
            if (!rippleFired && raw >= 0.8f) {
                rippleFired = true
                triggerLandRipple(rippleCenter())
            }
            if (raw >= 1f) break
        }
    }

    // 网格几何：分界缝只在开启时计入；节次 top / 课程视觉高度用于跨午休/晚修落点
    fun dividerPxFor(geom: ScheduleGridGeometry): Float =
        if (geom.showBreakDividers) with(densityProvider) { 24.dp.toPx() } else 0f

    fun sectionTopPx(geom: ScheduleGridGeometry, section: Int, dividerPx: Float): Float {
        // 优先用网格布局的实际节次顶（含特殊块挤占）；下面的现算公式漏掉特殊块高度，
        // 会让长按浮层与吸附落点整体偏上，偏移量等于该节次上方的特殊块总高。
        geom.sectionTopDp[section]?.let { dp -> return with(densityProvider) { dp.dp.toPx() } }
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
        val bottom =
            sectionTopPx(geom, endSection.coerceAtLeast(startSection), dividerPx) + sectionH
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
     * 列星期 → 数据（星期, 周次）：调课日列渲染的是 followWeekday/followWeek 的课，
     * 落点写库、冲突检测都要走这个映射，几何仍用列星期。
     *
     * 认映射要过两关：几何得是当前页的（[ScheduleGridGeometry.pageWeek] == 浏览周），
     * 且映射周次 ≠ 浏览周（那才是调休日列）。跨周调课翻页后上层可能还拿着上一页的几何，
     * 少了第一条判据就会把课写进上一页那一周。
     */
    fun dataPositionForColumn(columnDay: Int): Pair<Int, Int> {
        val geom = gridGeometry
        val mapped = geom?.columnDataPosition?.get(columnDay)
        if (mapped != null &&
            geom.pageWeek == currentViewingWeek &&
            mapped.second != currentViewingWeek
        ) return mapped
        return columnDay to currentViewingWeek
    }

    /** 数据（星期, 周次）→ 列星期：几何反查（粘贴飞行起点、交换飞行原点），找不到退回数据星期 */
    fun columnForDataPosition(day: Int, week: Int): Int =
        gridGeometry?.columnDataPosition?.entries
            ?.firstOrNull { it.value.first == day && it.value.second == week }?.key ?: day

    // ── 跨周调课：拖着课翻周 ────────────────────────────────────────────────
    /** 手指停在屏幕左右边缘热区时持续翻页的 Job */
    private var edgeAutoPageJob: Job? = null
    /** 当前方向：-1 上一周 / 1 下一周 / 0 未触发。给边缘提示 UI 读 */
    var edgeAutoPageDirection by mutableIntStateOf(0)
        private set
    /** 循环实际在翻的方向（Job 内部用）；与 UI 方向分开，避免收尾时互相覆盖 */
    private var edgeAutoPageDir = 0
    /** 最近一次按手指位置判定的方向（手指不动时循环靠它判断还在不在热区） */
    private var edgeAutoPageLatestDir = 0
    private var edgeAutoPageEnabled = false
    /** 手指离开热区 / 到头：循环当前这次翻页结束后退出 */
    private var edgeAutoPageStopRequested = false
    /** 自动翻周的 pager 动画已开跑。取消它会停在半页，收尾时必须让它落页。 */
    private var edgeAutoPageAnimating = false
    /** Job 代号：旧 Job 的 finally 不能把新 Job 的状态清掉 */
    private var edgeAutoPageGeneration = 0

    /**
     * 拖拽中按卡片中心维护持续翻周：卡片停在左/右边缘热区，每 [EDGE_AUTO_PAGE_INTERVAL_MS]
     * 翻一页；**翻完还在热区就继续翻**，直到手指离开、到头或松手。
     *
     * [fingerX] 取窗口横坐标（当前用浮层卡片中心）；[enabled] 为 false（非拖拽、粘贴飞行）时立刻停。
     * 拖拽逐帧都会调进来，方向没变时不会重启循环。
     */
    fun updateEdgeAutoPage(fingerX: Float, enabled: Boolean) {
        val width = latestScreenWPx
        val lastPage = (totalWeeks - 1).coerceAtLeast(0)
        // 热区 32dp（约大半列）：拖到屏边即可触发，但也不必把卡片推出屏幕。
        // 防止「想放最后一列却被翻周」靠的是 500ms 驻留，而不是把热区挤得太窄。
        val zone = if (width <= 0f) {
            0f
        } else {
            with(densityProvider) { 32.dp.toPx() }.coerceAtMost(width * 0.1f)
        }
        val dir = when {
            !enabled || zone <= 0f -> 0
            fingerX <= zone -> -1
            fingerX >= width - zone -> 1
            else -> 0
        }
        val exhausted = (dir == -1 && weekPageState.currentPage <= 0) ||
            (dir == 1 && weekPageState.currentPage >= lastPage)
        val nextDir = if (exhausted) 0 else dir
        edgeAutoPageEnabled = enabled
        edgeAutoPageLatestDir = nextDir

        if (nextDir == 0) {
            // 离开热区 / 到头：delay 阶段立即停；动画阶段让它落页，循环自己退出。
            // 动画期间保留 edgeAutoPageDir，手指又回到同一侧时才能认出「还是这一轮」。
            edgeAutoPageStopRequested = true
            edgeAutoPageDirection = 0
            if (!edgeAutoPageAnimating) {
                edgeAutoPageDir = 0
                edgeAutoPageJob?.cancel()
                edgeAutoPageJob = null
            }
            return
        }
        // 已经在同方向循环里（delay 或动画中）：保持。手指短暂离开又回到同一侧时
        // 把 stopRequested 清掉，让它继续翻，而不是等下一次事件重新起一轮。
        if (edgeAutoPageJob?.isActive == true && edgeAutoPageDir == nextDir) {
            edgeAutoPageStopRequested = false
            edgeAutoPageDirection = nextDir
            return
        }
        // 动画中方向变了：不能取消 pager，标记停止，下一次事件再开新方向
        if (edgeAutoPageAnimating) {
            edgeAutoPageStopRequested = true
            edgeAutoPageDirection = 0
            return
        }
        edgeAutoPageJob?.cancel()
        edgeAutoPageJob = null
        edgeAutoPageDir = nextDir
        edgeAutoPageStopRequested = false
        edgeAutoPageDirection = nextDir
        val generation = ++edgeAutoPageGeneration
        edgeAutoPageJob = coroutineScope.launch {
            try {
                while (true) {
                    // 第一页也要先等满 500ms；翻完回到这里再等 500ms 翻下一页
                    delay(EDGE_AUTO_PAGE_INTERVAL_MS)
                    if (edgeAutoPageStopRequested || !edgeAutoPageEnabled ||
                        edgeAutoPageLatestDir != nextDir
                    ) break
                    val current = weekPageState.currentPage
                    val target = (current + nextDir).coerceIn(0, lastPage)
                    if (target == current) break
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    edgeAutoPageAnimating = true
                    try {
                        // 与「返回本周」同一条曲线。
                        weekPageState.jumpToPageSmoothly(target)
                    } catch (_: CancellationException) {
                        // 第二根手指开始滑 pager（UserInput 优先级更高）会取消这次翻页；
                        // 本 Job 自己没被取消就继续等下一轮，别把持续翻页整条打死。
                        if (!isActive) break
                    } finally {
                        edgeAutoPageAnimating = false
                    }
                    // 动画期间手指可能已经离开/松手：立刻退出，别再等下一轮
                    if (edgeAutoPageStopRequested || !edgeAutoPageEnabled ||
                        edgeAutoPageLatestDir != nextDir
                    ) break
                    // 翻完还在热区 → 下一轮 delay 后再翻；离开/松手则退出
                }
            } finally {
                // 只有「还属于自己这一代」时才清状态，避免旧 Job 收尾清掉新 Job 的方向
                if (edgeAutoPageGeneration == generation) {
                    edgeAutoPageDir = 0
                    edgeAutoPageDirection = 0
                    edgeAutoPageJob = null
                }
            }
        }
    }

    /**
     * 程序化跳周（回本周 / 跳周对话框）：**立刻**把「当前周」指向目标周，再滑过去
     *
     * 正常结束保持覆盖，等 `settledPage` 追上后由外壳的 LaunchedEffect 撤掉；
     * 被打断（用户反向滑 / 抢占）立即撤掉，标题回到真实页。
     */
    suspend fun jumpToWeek(targetWeek: Int) {
        val lastPage = (weekPageState.pageCount - 1).coerceAtLeast(0)
        val dest = (targetWeek - 1).coerceIn(0, lastPage)
        // 目标周按落点页算，避免「总周数」与 pageCount 不一致时标题落到界外
        pendingViewingWeek = dest + 1
        try {
            weekPageState.jumpToPageSmoothly(dest, weekJumpNeighbor)
        } catch (e: CancellationException) {
            pendingViewingWeek = 0
            throw e
        } finally {
            if (weekPageState.currentPage != dest) pendingViewingWeek = 0
        }
    }

    /** 拖拽结束/取消：停掉边缘自动翻周 */
    fun stopEdgeAutoPage() {
        edgeAutoPageEnabled = false
        edgeAutoPageLatestDir = 0
        edgeAutoPageStopRequested = true
        if (edgeAutoPageAnimating) {
            // 动画已经开跑：取消会把 pager 停在半页。让它落页，循环看到
            // stopRequested 会退出；落点由 onCourseDragEnd 的 isScrollInProgress 守卫拦下。
            edgeAutoPageDirection = 0
            return
        }
        edgeAutoPageJob?.cancel()
        edgeAutoPageJob = null
        edgeAutoPageDir = 0
        edgeAutoPageDirection = 0
    }

    /**
     * spring 吸附：把浮层从当前位置回弹到 [targetOffsetX]/[targetOffsetY]（相对锚点），
     * 带手指甩出的初速度，120ms 后在中途开一圈落地涟漪，动画结束即收尾。
     */
    private fun snapFloatingCardTo(targetOffsetX: Float, targetOffsetY: Float, rippleCenter: Offset) {
        coroutineScope.launch {
            isSnapping = true
            launch {
                delay(120.milliseconds)
                triggerLandRipple(rippleCenter)
            }
            val positionSpec = spring<Float>(
                dampingRatio = 0.58f,
                stiffness = Spring.StiffnessMediumLow
            )
            val scaleSpec = spring<Float>(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMedium
            )
            // 三条动画必须一起跑完再收尾，串行会让卡片先缩放后位移。
            // 用 kotlinx 的 coroutineScope 作用域函数（同名属性 coroutineScope 会遮蔽简单名）。
            kotlinx.coroutines.coroutineScope {
                launch {
                    animateFloatState(
                        floatingOffsetX, targetOffsetX, positionSpec, dragMotion.velocityX
                    )
                }
                launch {
                    animateFloatState(
                        floatingOffsetY, targetOffsetY, positionSpec, dragMotion.velocityY
                    )
                }
                launch { floatingScale.animateTo(1f, scaleSpec) }
            }
            settleFloatingCard()
        }
    }

    /** 拖到有效落点松手：吸进目标格。 */
    val snapFloatingCardToTarget: (dayOfWeek: Int, startSection: Int, sectionSpan: Int) -> Unit =
        { day, section, span ->
            val targetCenter = computeTargetCenter(day, section, span)
            if (targetCenter != null) {
                snapFloatingCardTo(
                    targetOffsetX = targetCenter.x - draggedCardPosition.x,
                    targetOffsetY = targetCenter.y - draggedCardPosition.y,
                    rippleCenter = targetCenter,
                )
            } else {
                dismissFloatingCard()
            }
        }

    /** 原地松手：吸回长按瞬间的原格中心，落地节奏与吸到新格完全一致。 */
    val snapFloatingCardToOrigin: () -> Unit = {
        snapFloatingCardTo(0f, 0f, draggedCardPosition)
    }

    /** 粘贴飞行：复用长按浮层从源格直线飞向目标格；几何取不到时直接回调。 */
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
                // 原卡片保持可见（复制语义，不清 draggingCourseSlots）
                draggedCardCourse = source
                draggedWeek = currentViewingWeek
                draggedCardPosition = sourceCenter
                dragMotion.reset()
                // 与 CourseCard 默认 2dp padding 对齐
                val cardPadPx = with(densityProvider) { 2.dp.toPx() }
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

                runFloatingFlight(
                    startX = 0f,
                    startY = 0f,
                    endX = targetCenter.x - sourceCenter.x,
                    endY = targetCenter.y - sourceCenter.y,
                    rippleCenter = { targetCenter },
                )
                // 落地后立刻换成真实课程，避免双影
                settleFloatingCard()
                onFinished()
            }
        } else {
            onFinished()
        }
    }

    /** 冲突取消 / 覆盖确认：沿用粘贴节奏从当前悬停位置飞到 [destOffsetX]/[destOffsetY]。 */
    fun flyFloatingCardWithPasteMotion(destOffsetX: Float, destOffsetY: Float) {
        stopConflictHover()
        coroutineScope.launch {
            isSnapping = true
            isPasteFlight = true
            floatingScale.snapTo(1f)
            runFloatingFlight(
                startX = floatingOffsetX.floatValue,
                startY = floatingOffsetY.floatValue,
                endX = destOffsetX,
                endY = destOffsetY,
                rippleCenter = {
                    Offset(
                        draggedCardPosition.x + destOffsetX,
                        draggedCardPosition.y + destOffsetY
                    )
                },
            )
            clearSwapFlight()
            settleFloatingCard()
        }
    }

    /**
     * 源课的「回程锚点」：同一张卡的位置，但跨周时只取**相邻页**（偏一屏）。
     *
     * 第 7 周拖到第 10 周后取消/交换，真实源周在屏幕外三页远；飞那么远用户也看不见，
     * 只要往源周方向飞出一屏（第 9 周同位置）就够了。同周时就是本页原格中心。
     */
    private fun sourceHomeCenter(source: Course): Offset {
        val span = source.endSection - source.startSection
        val base = computeTargetCenter(draggedColumnDay, source.startSection, span)
            ?: computeTargetCenter(source.dayOfWeek, source.startSection, span)
            ?: Offset(
                draggedCardPosition.x + floatingOffsetX.floatValue,
                draggedCardPosition.y + floatingOffsetY.floatValue
            )
        val pageDelta = when {
            draggedWeek < currentViewingWeek -> -1
            draggedWeek > currentViewingWeek -> 1
            else -> 0
        }
        return if (pageDelta == 0) base
        else Offset(base.x + pageDelta * latestScreenWPx, base.y)
    }

    // 冲突取消：飞到源周方向的相邻页（同周则回原格）
    val flyFloatingCardHome: () -> Unit = {
        val source = draggedCardCourse
        if (source != null) {
            val home = sourceHomeCenter(source)
            flyFloatingCardWithPasteMotion(
                home.x - draggedCardPosition.x,
                home.y - draggedCardPosition.y
            )
        } else {
            flyFloatingCardWithPasteMotion(0f, 0f)
        }
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
                    val cardPadPx = with(densityProvider) { 2.dp.toPx() }
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
                    // 冲突课飞向源周：跨周时只到相邻页同位置（屏幕外一屏），
                    // 不飞回真实源周（可能隔好几页，用户也看不到）
                    val sourceHome = sourceHomeCenter(source)
                    val tgtEndX = sourceHome.x - occupiedCenter.x
                    val tgtEndY = sourceHome.y - occupiedCenter.y

                    // 两张卡片交叉飞行：主卡片各自插值到目标格，冲突卡同步位移与缩放
                    runFloatingFlight(
                        startX = floatingOffsetX.floatValue,
                        startY = floatingOffsetY.floatValue,
                        endX = targetCenter.x - draggedCardPosition.x,
                        endY = targetCenter.y - draggedCardPosition.y,
                        rippleCenter = { targetCenter },
                        onFrame = { t, scale ->
                            swapFlightOffsetX.snapTo(tgtEndX * t)
                            swapFlightOffsetY.snapTo(tgtEndY * t)
                            swapFlightScale.snapTo(scale)
                        },
                    )

                    clearSwapFlight()
                    settleFloatingCard()
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

        // 优先用网格权威节次顶（sectionTopDp 含分界缝与特殊块挤占），与卡片排版、落点高亮保持一致；
        // 下面那套手写遍历漏掉特殊块，会让「实际落到的格子」与「高亮显示的格子」错开。
        val totalSections = geom.morningSections + geom.afternoonSections + geom.eveningSections
        if (geom.sectionTopDp.isNotEmpty() && totalSections > 0) {
            for (s in 1..totalSections) {
                val top =
                    geom.sectionTopDp[s]?.let { with(densityProvider) { it.dp.toPx() } } ?: continue
                if (relY < top + sectionH) return day to s
            }
            // 落在最后一节之下：吸到最后一节
            return day to totalSections
        }

        val dividerH = with(densityProvider) { 24.dp.toPx() }
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

    /**
     * 用浮层当前中心重算落点。
     *
     * 拖拽回调只在手指动时来；翻周（pager 平移一整页）、页内滚动、第二只手滚屏都会让
     * 网格在浮层底下换掉，手指不动时 [pendingDropTarget] 仍是上一页/上一屏算出来的格子。
     * 松手直接用，课就落进错误的周/节次 —— 几何一更新、翻页停下、松手前都要重算。
     */
    fun refreshPendingDropTarget() {
        val course = draggedCardCourse ?: return
        if (!floatingCardVisible) return
        val geom = gridGeometry ?: return
        val sectionH = geom.sectionHeightPx
        if (sectionH <= 0f) return
        val sectionCount = course.endSection - course.startSection + 1
        // 必须与浮层卡片用同一套高度（含分界缝与特殊块挤占）：写死
        // sectionCount * sectionH 会让「起始节次中心」算偏，落点命中与高亮错开一节。
        val cardHeightPx = if (course.hasValidCustomTime()) {
            draggedCardSize.y
        } else {
            courseVisualHeightPx(geom, course.startSection, course.endSection, dividerPxFor(geom))
        }
        val centerX = draggedCardPosition.x + floatingOffsetX.floatValue
        val cardTopY = draggedCardPosition.y + floatingOffsetY.floatValue - cardHeightPx / 2f
        val firstSectionCenterY = cardTopY + sectionH / 2f
        // 放不下的格子不给落点（与粘贴路径同款校验）：起始节次+跨度
        // 超过总节次时松手会写出 endSection > totalSections 的课，渲染溢出/错位。
        setPendingDropTarget(
            computeDropTarget(centerX, firstSectionCenterY)?.takeIf {
                it.second + sectionCount - 1 <= totalSections
            }
        )
    }

    fun openCourseDetail(
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        cardLayer: TransitionLayerSnapshot?,
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
        // 重新进入：上次退出若没来得及复位（异常路径），这里兜底
        detailExiting = false
        detailSnapshot = cardLayer?.takeIf {
            it.layer.size.width > 0 && it.layer.size.height > 0
        }?.let { source ->
            // 子图层依赖要保留在上面的 lazy / 响应式分支之外
            retainedDetailLayer.record(
                densityProvider,
                detailLayerLayoutDirection,
                source.layer.size
            ) {
                drawLayer(source.layer)
            }
            val snapshot = TransitionLayerSnapshot(retainedDetailLayer, source.bounds)
            // 再异步冻结一份位图：`drawLayer` 只是**活引用**，源行随后会被 hiddenCourseIds 藏掉，
            // 退出 morph 重放时就只剩玻璃背景、没有文字（进入时源还活着，只有退出缺内容）。
            // 光栅化后与源图层彻底脱钩：进入先用活引用，位图就绪后原地替换。
            coroutineScope.launch {
                val frozen = runCatching { source.layer.toImageBitmap() }.getOrNull()
                    ?: return@launch
                if (detailSnapshot === snapshot) {
                    detailSnapshot = snapshot.copy(frozen = frozen)
                }
            }
            snapshot
        }
        // 先录整屏背景快照，源卡片此时仍在画面里
        coroutineScope.launch {
            prepareMainContentLayer()
            detailBackgroundSnapshot = TransitionLayerSnapshot(screenGraphicsLayer)
            detailBackgroundScale.snapTo(1f)
            showDetail = true
            // 首帧静态态上屏后再起动画，与编辑页一致
            withFrameNanos { }
            val blurJob = launch {
                managePageBlurRadius.animateTo(
                    HostNavigationState.DETAIL_BACKDROP_BLUR_DP,
                    animationSpec = tween(580, easing = OobeQuartOutEasing)
                )
            }
            val scaleJob = launch {
                detailBackgroundScale.animateTo(
                    MORPH_BACKGROUND_SHRINK,
                    animationSpec = tween(580, easing = OobeQuartOutEasing)
                )
            }
            // 动画跑完再藏源卡片：动画期间不做列表重排，避免测量/布局尖峰
            blurJob.join()
            scaleJob.join()
            hiddenCourseIds = setOf(courseIdToHide)
        }
    }

    // 顶栏「课表外观」与长按按钮共用；所有入口都直接调这个，延迟统一在这里
    // （见 CUSTOMIZE_ENTRY_DELAY_MS），调用点不要再各自加 delay。
    val enterCustomizePage: () -> Unit = {
        FeatureLog.t("课表外观", "enter")
        coroutineScope.launch {
            delay(CUSTOMIZE_ENTRY_DELAY_MS.milliseconds)
            val oldCombSnapshots = combinations.mapNotNull { it.snapshot }
            combinations = combinations.map { it.copy(snapshot = null) }
            delay(50.milliseconds)
            // 截不到（OOM）就不做开洞快照，直接照常进搭配页。
            val captured = captureMainContentBitmap()
            val currentSnapshot = captured
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
                recycleSnapshot(oldCustomize, currentSnapshot, captured)
                recycleSnapshot(oldCover, currentSnapshot, captured)
                oldCombSnapshots.forEach { snap ->
                    recycleSnapshot(
                        snap,
                        currentSnapshot,
                        captured,
                        WallpaperStore.currentImageBitmap(),
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
            // 快照的缩小/淡出动画**不在这里启动**：与主内容 cutoutMainScale 一起由
            // `LaunchedEffect(isWindowCutoutActive)` 驱动，保证两者同帧起跑
            originalCombinationIndex = 0
            originalWallpaperBitmap = wallpaperBitmap
            originalWallpaperOffset = wallpaperOffset
            originalWallpaperScale = wallpaperScale
            originalWallpaperIsLight = combinations.getOrNull(0)?.wallpaperIsLight
            originalCombination = combinations.getOrNull(0)
        }
    }

    // 选壁纸：系统选图器 + 降采样解码是 Android 能力（宿主注入），解出的 ImageBitmap 回填这里状态。
    val applyPickedWallpaper: (ImageBitmap?) -> Unit = { picked ->
        if (picked != null) {
            val oldWallpaper = wallpaperBitmap
            wallpaperBitmap = picked
            // 壁纸缓存与变换在共享层 WallpaperStore（桌面/iOS 读同一份）
            WallpaperStore.setCached(picked)
            wallpaperOffset = Offset.Zero
            val autoScale =
                WallpaperTransform.minScale(picked.width, picked.height, screenWPx, screenHPx)
            wallpaperScale = autoScale
            WallpaperStore.saveTransform(Offset.Zero, autoScale)
            val isLight = WallpaperStore.measureLightness(picked)
            updateCurrentCombination {
                it.copy(
                    bitmap = picked,
                    // 清掉旧快照，搭配预览回退显示新壁纸，避免「选完没反应」
                    snapshot = null,
                    offset = Offset.Zero,
                    scale = autoScale,
                    wallpaperIsLight = isLight
                )
            }
            // 旧壁纸已离开当前 combination/显示引用后再回收（避开缓存与仍被展示的同一对象）
            coroutineScope.launch {
                delay(32.milliseconds)
                recycleSnapshot(
                    oldWallpaper,
                    picked,
                    WallpaperStore.currentImageBitmap(),
                    wallpaperBitmap,
                    savedWallpaperBitmap,
                    originalWallpaperBitmap,
                    *combinations.mapNotNull { c -> c.bitmap }.toTypedArray(),
                )
            }
        }
    }
    var showSwitchSchedule by mutableStateOf(false)
    var switchPendingReverse by mutableStateOf(false)
    var switchExiting by mutableStateOf(false)
    var switchMainLayerJob by mutableStateOf<Job?>(null)

    // 锚点与源图层都就绪之前，先让目标页保持隐藏
    var switchPreparingLayer by mutableStateOf(false)

    // 回到另一张课表前要重新录源图层
    var scheduleChanged by mutableStateOf(false)
    var showMorePopup by mutableStateOf(false)
    var showTodayMorePopup by mutableStateOf(false)

    /** 课表页呈现方式（网格 / 时间轴），落盘在 app_theme_prefs 的 schedule_view_mode */
    var scheduleViewMode by mutableStateOf(loadScheduleViewMode())

    /**
     * 切换视图模式：先更新内存再落盘，UI 不等磁盘回调。
     * 名字避开 `setScheduleViewMode` —— 会与 `var` 的自动 setter 撞 JVM 签名。
     */
    fun applyScheduleViewMode(mode: ScheduleViewMode) {
        if (scheduleViewMode == mode) return
        scheduleViewMode = mode
        saveScheduleViewMode(mode)
    }

    /** 今日页呈现方式（周网格 / 时间轴），落盘在 app_theme_prefs 的 today_view_mode */
    var todayViewMode by mutableStateOf(loadTodayViewMode())

    fun applyTodayViewMode(mode: TodayViewMode) {
        if (todayViewMode == mode) return
        todayViewMode = mode
        saveTodayViewMode(mode)
    }

    // 按钮原地随菜单展开淡出，形成"按钮连贯变为菜单"的单一动画
    val morePopupFraction = Animatable(0f)
    val todayMorePopupFraction = Animatable(0f)

    var todayJumpToDateTrigger by mutableIntStateOf(0)
    val isViewingCurrentWeek get() = currentViewingWeek == currentWeek
    fun openScheduleLibrary() {
        // switchPreparingLayer：截图+上屏在协程里，期间 showSwitchSchedule 还是 false，
        // 只靠它防连点（否则会连开两个协程、截两张图）
        if (isShiftMode || switchPreparingLayer || (showSwitchSchedule && !switchExiting)) return
        switchExiting = false
        switchAnimForward = true
        if (showSwitchSchedule) {
            // 从当前进度打断退场，保留活页与快照
            switchAnimJob?.cancel()
            switchPendingReverse = false
            switchPreparingLayer = false
            switchAnimRunning = true
            // 这也是「进入」方向（p→0），卡片来源同样是上次退出固化的位图
            switchCardSnapshot = null
            switchCardBitmap = retainedSwitchCardBitmap
            switchAnimJob = coroutineScope.launch {
                switchAnimProgress.animateTo(0f, tween(320, easing = OobeCubicOutEasing))
                switchOverlayActive = false
                switchCardSnapshot = null
                switchCardBitmap = null
                switchCardBounds = null
                switchAnimRunning = false
            }
        } else {
            switchPendingReverse = true
            switchPreparingLayer = true
            switchMainLayerJob = coroutineScope.launch {
                prepareMainContentLayer()
                // 截的是**课程表页面**这一屏（此刻切换页还没上屏，画面就是主界面）。
                // 与课表外观页 `captureMainContentBitmap(includeNativeChrome = true)` 同一个函数：
                // 从 window 渲染整屏，原生 UITabBar / 导航栏都在里面。
                // Android/桌面恒为 null → morph 仍用 Compose 图层（零回归）。
                val staleShot = mainScreenShot
                mainScreenShot = runCatching { captureScreenWithNativeChrome() }.getOrNull()
                recycleSnapshot(staleShot)
                // 截图完成后再上屏，保证 overlay 第一帧就有主内容图
                showSwitchSchedule = true
            }
        }
    }
}
