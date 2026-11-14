@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.haooz.chedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.FeedbackStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.i18n.SwitchStr
import com.haooz.chedule.i18n.WeatherStr
import com.haooz.chedule.ui.activities.EduImportUpdater
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.NavTransitionState
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.HostNavigationState
import com.haooz.chedule.ui.utils.HostOverlayContent
import com.haooz.chedule.ui.utils.HostOverlayState
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.LocalHostOverlays
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentChrome
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import platform.UIKit.UIViewController
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixOverlayHostProvider
import top.yukonga.miuix.kmp.utils.MiuixOverlayHostState
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

/** One retained app session shared by native document and Compose controllers. */
class IosAppHost {
    internal val router = AppRouter()
    internal val mainTransition = NavTransitionState()
    internal var navigationOverlayActive by mutableStateOf(false)
    internal val navigation = HostNavigationState()
    private var systemDarkTheme by mutableStateOf<Boolean?>(null)
    private val overlays = HostOverlayState()
    private val scheduleLibraryOverlays = HostOverlayState()
    private val popupHost = MiuixOverlayHostState()
    private val nativePages = mutableStateListOf<NativeDocumentPage>()
    private var nextNativePageId = 0L
    private var requestedPopDepth by mutableStateOf<Int?>(null)
    private var nativeTransitionInFlight by mutableStateOf(false)
    private var deferredEditorPopRouteId: String? = null
    private var screenCornerRadiiPointsState by mutableStateOf(ScreenCornerRadiiPoints())

    /** Physical corners in points, mirrored from Compose for native page transitions. */
    val screenTopLeftCornerRadiusPoints: Double get() = screenCornerRadiiPointsState.topLeft
    val screenTopRightCornerRadiusPoints: Double get() = screenCornerRadiiPointsState.topRight
    val screenBottomRightCornerRadiusPoints: Double get() = screenCornerRadiiPointsState.bottomRight
    val screenBottomLeftCornerRadiusPoints: Double get() = screenCornerRadiiPointsState.bottomLeft
    val screenCornerRadiusPoints: Double get() = maxOf(
        screenTopLeftCornerRadiusPoints, screenTopRightCornerRadiusPoints,
        screenBottomRightCornerRadiusPoints, screenBottomLeftCornerRadiusPoints,
    )

    init {
        navigation.scheduleLibraryOverlays = scheduleLibraryOverlays
        router.nativeRouteHandler = { route ->
            if (route.isNativeDocument()) {
                navigateNativeDocument(route.id)
                true
            } else {
                false
            }
        }
        router.nativeBackHandler = ::requestNativePop
    }

    /** Instance IDs, not route kinds: pushing the same document twice keeps independent state. */
    val nativeRouteIds: List<String>
        get() = nativePages.take(requestedPopDepth ?: nativePages.size).map { it.id }
    val floatingOverlayActive: Boolean get() = overlays.isBelowPopupsActive || popupHost.isActive
    /** 弹窗层之上的 overlay（课程详情页）是否在显示；iOS 上它与弹窗容器分开承载 */
    val detailOverlayActive: Boolean get() = overlays.isAbovePopupsActive
    val nativePageActive: Boolean get() = nativePages.isNotEmpty()
    val nativePopPending: Boolean get() = requestedPopDepth != null
    val nativeContentOverlayActive: Boolean
        get() = nativePages.lastOrNull()?.chrome?.let { it.contentOverlayActive || it.editor != null } == true

    fun nativeRouteTitle(routeId: String): String =
        nativePage(routeId).let {
            it.chrome.editor?.title ?: it.chrome.title.ifEmpty { it.route.nativeDocumentTitle().orEmpty() }
        }

    fun navigateNativeDocument(routeId: String) {
        val route = AppRoute.fromId(routeId) ?: return
        // ⚠ 只拦 UIKit 自己的转场状态（编辑器开着 / 转场中 / 有待处理的 pop）；绝不拦
        // floatingOverlayActive（Compose 弹窗），否则「选适配器」弹窗里点学校会被静默吞掉（Android 无此 guard）。
        if (!route.isNativeDocument() || nativeContentOverlayActive ||
            nativeTransitionInFlight || requestedPopDepth != null
        ) return
        // 与 AppRouter.navigate 同一规则：挡「已在栈顶」，连点只压一层；A → B → A 仍合法
        if (nativePages.lastOrNull()?.route == route) return
        val usedKeys = nativePages.map { it.stateKey }.toSet()
        var stateKey = route.id
        var occurrence = 2
        while (stateKey in usedKeys) stateKey = "${route.id}#${occurrence++}"
        if (route == AppRoute.EducationalImport) {
            // Resolve the initial busy state before UIKit starts the push transition.
            EduImportUpdater.startUpdateIfNeeded()
        }
        nativePages.add(NativeDocumentPage(
            id = "native-document-${++nextNativePageId}",
            route = route,
            stateKey = stateKey,
        ))
    }

    /** Request a UIKit pop; keep the page, its registry, and business state alive until didShow. */
    private fun requestNativePop(): Boolean {
        if (nativePages.isEmpty()) return false
        // Route-local morph editors must finish their own back/save path first.
        if (nativeContentOverlayActive) return true
        if (!nativeTransitionInFlight && requestedPopDepth == null) {
            requestedPopDepth = nativePages.lastIndex
        }
        return true
    }

    fun setNativeTransitionInFlight(inFlight: Boolean) {
        nativeTransitionInFlight = inFlight
    }

    /** UIKit reports the depth it actually displays after every settled transition (cancelled gestures included). */
    fun completeNativePop(depth: Int) {
        // 出栈 = 销毁：**不缓存**它的 saveable 状态（同 AppNavHost 的 stateHolder.removeState），
        // 否则重进这一页永远是上次的滚动位置；只放掉组合控制器。
        // ⚠ 越界不 return：UIKit 报来的深度与这里对不上时也要收敛到「屏幕上还剩几层」，
        // 否则旧 page 会永久留在栈里，重进时它的 controller 被原样复用 = 状态还在。
        val target = depth.coerceIn(0, nativePages.size)
        while (nativePages.size > target) {
            val page = nativePages.removeAt(nativePages.lastIndex)
            page.controller = null
        }
        requestedPopDepth = null
    }

    fun updateNativePageBarInset(routeId: String, points: Double) {
        val page = nativePages.firstOrNull { it.id == routeId } ?: return
        if (!points.isFinite()) return
        val inset = points.coerceAtLeast(0.0)
        if (kotlin.math.abs(page.chrome.barInset - inset) > 0.5) page.chrome.barInset = inset
    }

    fun nativePageEditorActive(routeId: String): Boolean = nativePage(routeId).chrome.editor != null
    fun nativePageActionSymbol(routeId: String): String = nativePage(routeId).chrome.effectiveAction?.symbol.orEmpty()
    fun nativePageActionLabel(routeId: String): String = nativePage(routeId).chrome.effectiveAction?.label.orEmpty()
    fun nativePageActionEnabled(routeId: String): Boolean =
        pageControlsAllowed(routeId) && nativePage(routeId).chrome.effectiveAction?.enabled == true
    fun nativePageActionBusy(routeId: String): Boolean = nativePage(routeId).chrome.effectiveAction?.busy == true
    fun nativePageActionSuccessful(routeId: String): Boolean = nativePage(routeId).chrome.effectiveAction?.successful == true
    fun nativePageSecondaryActionSymbol(routeId: String): String = nativePage(routeId).chrome.effectiveSecondaryAction?.symbol.orEmpty()
    fun nativePageSecondaryActionLabel(routeId: String): String = nativePage(routeId).chrome.effectiveSecondaryAction?.label.orEmpty()
    fun nativePageSecondaryActionEnabled(routeId: String): Boolean =
        pageControlsAllowed(routeId) && nativePage(routeId).chrome.effectiveSecondaryAction?.enabled == true
    fun nativePageSecondaryActionBusy(routeId: String): Boolean = nativePage(routeId).chrome.effectiveSecondaryAction?.busy == true
    fun nativePageSecondaryActionSuccessful(routeId: String): Boolean = nativePage(routeId).chrome.effectiveSecondaryAction?.successful == true
    fun nativePageTintArgb(routeId: String): Long = nativePage(routeId).chrome.tintArgb

    // Match the leading slot across list/editor configurations using UIKit item identity.
    fun nativePageLeadingSymbol(routeId: String): String = nativePage(routeId).let {
        it.chrome.effectiveLeading?.symbol ?: if (it.route == AppRoute.CourseTimeSettings || it.route == AppRoute.CourseManage) "chevron.backward" else ""
    }
    fun nativePageLeadingLabel(routeId: String): String =
        nativePage(routeId).chrome.effectiveLeading?.label ?: CommonStr.back()
    fun nativePageLeadingEnabled(routeId: String): Boolean =
        pageVisibleAndInteractive(routeId) &&
            nativePage(routeId).chrome.effectiveLeading?.let { it.enabled && !it.busy } != false

    private fun pageVisibleAndInteractive(routeId: String): Boolean =
        !nativeTransitionInFlight && requestedPopDepth == null && !floatingOverlayActive &&
            nativePages.lastOrNull()?.id == routeId

    private fun pageControlsAllowed(routeId: String): Boolean =
        pageVisibleAndInteractive(routeId) &&
            (!nativeContentOverlayActive || nativePageEditorActive(routeId))

    fun performNativePageAction(routeId: String) {
        if (!pageControlsAllowed(routeId)) return
        val action = nativePage(routeId).chrome.effectiveAction ?: return
        if (action.enabled && !action.busy) action.onClick()
    }

    fun performNativePageSecondaryAction(routeId: String) {
        if (!pageControlsAllowed(routeId)) return
        val action = nativePage(routeId).chrome.effectiveSecondaryAction ?: return
        if (action.enabled && !action.busy) action.onClick()
    }

    /** 本页是否把顶栏整个交给系统（原生导航栏显示标题、页面不画 Compose 顶栏） */
    fun nativePageUsesSystemHeader(routeId: String): Boolean =
        nativePage(routeId).chrome.usesSystemHeader

    // ── 底部悬浮条（用系统控件渲染，见 Swift 侧 NativeDocumentNavigation）──

    // ⚠ 只判本页有没有底栏：底栏属于页面内容，转场时应跟页面一起移动，
    // 不能用 pageVisibleAndInteractive（会让它在转场中突然消失）。
    fun nativePageBottomBarVisible(routeId: String): Boolean =
        nativePage(routeId).chrome.bottomBar != null

    fun nativePageBottomBarTitle(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.title.orEmpty()

    fun nativePageBottomBarSubtitle(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.subtitle.orEmpty()

    fun nativePageBottomBarBadge(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.badge.orEmpty()

    fun nativePageBottomBarToggleSymbol(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.toggleSymbol.orEmpty()

    fun nativePageBottomBarToggleLabel(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.toggleLabel.orEmpty()

    fun nativePageBottomBarToggleEnabled(routeId: String): Boolean =
        pageControlsAllowed(routeId) && nativePage(routeId).chrome.bottomBar?.toggleEnabled == true

    fun nativePageBottomBarPrimarySymbol(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.primarySymbol.orEmpty()

    fun nativePageBottomBarPrimaryLabel(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.primaryLabel.orEmpty()

    fun nativePageBottomBarPrimaryEnabled(routeId: String): Boolean =
        pageControlsAllowed(routeId) && nativePage(routeId).chrome.bottomBar?.primaryEnabled == true

    fun performNativePageBottomToggle(routeId: String) {
        if (!pageControlsAllowed(routeId)) return
        val bar = nativePage(routeId).chrome.bottomBar ?: return
        if (bar.toggleEnabled) bar.onToggle()
    }

    fun performNativePageBottomPrimary(routeId: String) {
        if (!pageControlsAllowed(routeId)) return
        val bar = nativePage(routeId).chrome.bottomBar ?: return
        if (bar.primaryEnabled) bar.onPrimary()
    }

    fun performNativePageLeadingAction(routeId: String) {
        if (!nativePageLeadingEnabled(routeId)) return
        val leading = nativePage(routeId).chrome.effectiveLeading
        if (leading != null) leading.onClick()
        else if (nativeContentOverlayActive) {
            // The parent back button is already visible during the reverse morph.
            // Accept the tap now, but let save/disposal finish before popping its page.
            deferredEditorPopRouteId = routeId
        } else requestNativePop()
    }

    fun reconcileNativeEditorExit() {
        val id = deferredEditorPopRouteId ?: return
        if (nativePages.lastOrNull()?.id != id) {
            deferredEditorPopRouteId = null
        } else if (!nativeContentOverlayActive && pageVisibleAndInteractive(id)) {
            deferredEditorPopRouteId = null
            requestNativePop()
        }
    }

    fun makeNativePageController(routeId: String): UIViewController {
        val page = nativePage(routeId)
        // 命中复用 = 这次 push 拿回的是上一次的组合控制器。同一页还没出栈时是正常的；
        // 页面关掉后再进还走到这里，说明出栈那一步漏清了这个 page（栈里/UIKit 里对不齐）。
        page.controller?.let { NexioLog.d("IosNativePage", "reuse ${page.route.id} -> $routeId") }
        return page.controller ?: ComposeUIViewController {
            HostEnvironment {
                CompositionLocalProvider(
                    LocalNativeDocumentChrome provides page.chrome,
                    LocalSaveableStateRegistry provides page.registry,
                    // ⚠ 必须给：页里 navigate 读 LocalAppRouter，而它默认值是 error(...)
                    LocalAppRouter provides router,
                ) {
                    Surface(Modifier.fillMaxSize(), color = MiuixTheme.colorScheme.surface) {
                        NexioIosRouteContent(router, page.route)
                    }
                }
            }
        }.also { page.controller = it }
    }

    internal fun nativePage(id: String): NativeDocumentPage =
        nativePages.firstOrNull { it.id == id } ?: error("Unknown native document instance: $id")

    var onStateChanged: (() -> Unit)? = null

    val scheduleLibraryActive: Boolean get() = navigation.scheduleLibraryActive
    val scheduleLibraryTransitionActive: Boolean get() = navigation.scheduleLibraryTransitionActive
    val scheduleLibraryTitle: String get() = navigation.scheduleLibraryChrome.title.ifEmpty { SwitchStr.allSchedules() }
    val scheduleLibraryTintArgb: Long get() = navigation.scheduleLibraryChrome.tintArgb
    fun scheduleLibraryLeadingSymbol(): String = navigation.scheduleLibraryChrome.effectiveLeading?.symbol.orEmpty()
    fun scheduleLibraryLeadingLabel(): String = navigation.scheduleLibraryChrome.effectiveLeading?.label.orEmpty()
    fun scheduleLibraryLeadingEnabled(): Boolean = scheduleLibraryControlsAllowed() &&
        navigation.scheduleLibraryChrome.effectiveLeading?.let { it.enabled && !it.busy } == true
    fun scheduleLibraryActionSymbol(): String = navigation.scheduleLibraryChrome.effectiveAction?.symbol.orEmpty()
    fun scheduleLibraryActionLabel(): String = navigation.scheduleLibraryChrome.effectiveAction?.label.orEmpty()
    fun scheduleLibraryActionEnabled(): Boolean = scheduleLibraryTrailingAllowed() &&
        navigation.scheduleLibraryChrome.effectiveAction?.let { it.enabled && !it.busy } == true
    fun scheduleLibraryActionBusy(): Boolean = navigation.scheduleLibraryChrome.effectiveAction?.busy == true
    fun scheduleLibraryActionSuccessful(): Boolean = navigation.scheduleLibraryChrome.effectiveAction?.successful == true
    fun scheduleLibrarySecondaryActionSymbol(): String = navigation.scheduleLibraryChrome.effectiveSecondaryAction?.symbol.orEmpty()
    fun scheduleLibrarySecondaryActionLabel(): String = navigation.scheduleLibraryChrome.effectiveSecondaryAction?.label.orEmpty()
    fun scheduleLibrarySecondaryActionEnabled(): Boolean = scheduleLibraryTrailingAllowed() &&
        navigation.scheduleLibraryChrome.effectiveSecondaryAction?.let { it.enabled && !it.busy } == true
    fun scheduleLibrarySecondaryActionBusy(): Boolean = navigation.scheduleLibraryChrome.effectiveSecondaryAction?.busy == true
    fun scheduleLibrarySecondaryActionSuccessful(): Boolean = navigation.scheduleLibraryChrome.effectiveSecondaryAction?.successful == true

    private fun scheduleLibraryControlsAllowed(): Boolean = scheduleLibraryActive &&
        !floatingOverlayActive && !nativePageActive
    private fun scheduleLibraryTrailingAllowed(): Boolean = scheduleLibraryControlsAllowed() &&
        !navigation.scheduleLibraryChrome.contentOverlayActive

    fun performScheduleLibraryLeadingAction() {
        if (scheduleLibraryLeadingEnabled()) navigation.scheduleLibraryChrome.effectiveLeading?.onClick?.invoke()
    }

    fun performScheduleLibraryAction() {
        if (scheduleLibraryActionEnabled()) navigation.scheduleLibraryChrome.effectiveAction?.onClick?.invoke()
    }

    fun performScheduleLibrarySecondaryAction() {
        if (scheduleLibrarySecondaryActionEnabled()) navigation.scheduleLibraryChrome.effectiveSecondaryAction?.onClick?.invoke()
    }

    /** System appearance comes from SwiftUI above all app and wallpaper style overrides. */
    fun updateSystemDarkTheme(isDark: Boolean) {
        // Seed UIKit before the first Compose frame; later page/wallpaper overrides own navigation.isDark.
        if (systemDarkTheme == null) {
            navigation.isDark = when (AppStorage.store("app_theme_prefs").getString("theme_mode", "system")) {
                "dark" -> true
                "light" -> false
                else -> isDark
            }
        }
        systemDarkTheme = isDark
    }

    val selectedTab: Int get() = navigation.selectedTab
    val tabCount: Int get() = navigation.tabCount
    val shiftMode: Boolean get() = navigation.shiftMode
    val showsNativeTabBar: Boolean get() = navigation.showsNativeTabBar
    val showsNativeHeader: Boolean get() = navigation.showsNativeHeader
    /** 主界面是否跑在平板（rail）布局：宿主据此收起栈底那根恒空的原生导航栏 */
    val isTabletLayout: Boolean get() = navigation.isTabletLayout
    val keepsNativeHeaderDuringCutout: Boolean get() = navigation.keepsNativeHeaderDuringCutout
    val isDark: Boolean get() = navigation.isDark
    val overlayActive: Boolean
        get() = router.hasOverlay || navigationOverlayActive || overlays.isActive || popupHost.isActive || scheduleLibraryOverlays.isActive
    val mainProgress: Double get() = mainTransition.value.toDouble()
    val cutoutScale: Float get() = navigation.cutoutScale
    val cutoutCenterYRatio: Float get() = navigation.cutoutCenterYRatio
    val cutoutCenterXRatio: Float get() = navigation.cutoutCenterXRatio

    /**
     * Per-frame push of the main content's vertical offset while the customize page's sheet is
     * open (pt, negative = up, `-1` = none). Called from Compose's **draw phase** on the main
     * thread, so UIKit applies it in the same frame as the content it has to follow.
     * Assigned by Swift; must stay a plain callback — routing it through host state would put
     * it a frame or two behind.
     */
    var onCutoutChromeOffset: ((Double) -> Unit)? = null
    /** 下层背景模糊强度（0…1，换算在 commonMain 完成）；宿主据此在「详情页之下、其余一切之上」压一层 */
    var onDetailBackdropBlurProgress: ((Double) -> Unit)? = null

    init {
        navigation.onCutoutChromeOffsetY = { offsetY -> onCutoutChromeOffset?.invoke(offsetY) }
        navigation.onDetailBackdropBlurProgress = { onDetailBackdropBlurProgress?.invoke(it) }
        // 详情页下层由 Swift 侧 detailBackdropBlur（UIVisualEffectView）独占。
        // Compose 不再叠第二层 renderEffect：主内容被糊两遍既更糊更白，又是双份 GPU 开销。
        navigation.ownsDetailBackdropBlur = true
    }

    val mainController: UIViewController by lazy {
        ComposeUIViewController {
            HostEnvironment {
                Surface(Modifier.fillMaxSize()) { NexioIosMain(this@IosAppHost) }
            }
        }
    }

    val overlayController: UIViewController by lazy {
        ComposeUIViewController(configure = { opaque = false }) {
            HostEnvironment {
                Box(Modifier.fillMaxSize()) {
                    NexioIosNavigation(this@IosAppHost)
                    HostOverlayContent(scheduleLibraryOverlays)
                }
            }
        }
    }

    /** Popups remain above both legacy Compose routes and native navigation chrome. */
    val floatingController: UIViewController by lazy {
        ComposeUIViewController(configure = { opaque = false }) {
            HostEnvironment {
                Box(Modifier.fillMaxSize()) {
                    HostOverlayContent(overlays) {
                        nativePages.isEmpty() && !router.hasOverlay && !navigationOverlayActive
                    }
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        // 弹窗层同样不在 Compose 主内容的模糊层里，半径与主内容同源
                        val blurDp = navigation.detailBackgroundBlur
                        val blurPx = if (blurDp > 0.01f) blurDp.dp.toPx() else 0f
                        renderEffect = if (blurPx > 0f) BlurEffect(null, blurPx, blurPx, TileMode.Clamp) else null
                    }) {
                        MiuixPopupHost()
                    }
                }
                val appDark = isAppDarkTheme()
                val hasNativePages = nativePages.isNotEmpty()
                SideEffect {
                    if (hasNativePages) {
                        navigation.isDark = appDark
                    }
                }
                LaunchedEffect(this@IosAppHost) {
                    snapshotFlow {
                        NativeHostSnapshot(
                            selectedTab, tabCount, shiftMode, showsNativeTabBar,
                            isDark, overlayActive, mainProgress, cutoutScale, cutoutCenterYRatio,
                            cutoutCenterXRatio,
                            nativeRouteIds, floatingOverlayActive, detailOverlayActive,
                            // 逐字段快照（含底栏与系统标题开关）—— 见 NativeDocumentChrome.snapshot
                            nativePages.map { it.chrome.snapshot() },
                            nativeTransitionInFlight,
                            showsNativeHeader,
                            isTabletLayout,
                            keepsNativeHeaderDuringCutout,
                            scheduleLibraryActive,
                            scheduleLibraryTransitionActive,
                            navigation.scheduleLibraryChrome.snapshot(),
                        )
                    }.collect {
                        onStateChanged?.invoke()
                    }
                }
            }
        }
    }

    /** 弹窗层之上的 overlay（课程详情页）；单独一个容器，才能整体盖在宿主背景模糊层之上 */
    val abovePopupsController: UIViewController by lazy {
        ComposeUIViewController(configure = { opaque = false }) {
            HostEnvironment {
                HostOverlayContent(overlays, abovePopups = true)
            }
        }
    }

    fun selectTab(index: Int) {
        if (!overlayActive && !nativePageActive && showsNativeTabBar && index in 0 until tabCount) {
            navigation.onTabSelected?.invoke(index)
        }
    }

    fun performMainAction(actionId: String) {
        when (actionId) {
            "switchSchedule" -> navigation.onOpenSwitchSchedule?.invoke()
            "jumpWeek" -> navigation.onJumpWeek?.invoke()
            "courseManage" -> navigation.onCourseManage?.invoke()
            "customize" -> navigation.onEnterCustomize?.invoke()
            "jumpToDate" -> navigation.onJumpToDate?.invoke()
        }
    }

    fun updateTabTransitionDirection(vertical: Boolean) {
        navigation.usesVerticalTabTransitions = vertical
    }

    fun updateHorizontalSafeArea(left: Double, right: Double) {
        if (!left.isFinite() || !right.isFinite()) return
        navigation.leftSafeAreaInset = left.coerceAtLeast(0.0)
        navigation.rightSafeAreaInset = right.coerceAtLeast(0.0)
    }

    fun updateBottomBarInset(points: Double) {
        val inset = points.coerceAtLeast(0.0)
        if (kotlin.math.abs(navigation.bottomBarInset - inset) > 0.5) {
            navigation.bottomBarInset = inset
        }
    }

    /** 屏幕形态：tab bar 是否竖排到屏幕一侧（`traitCollection.verticalBarEdge`）。 */
    fun updateVerticalBarEdge(active: Boolean) {
        navigation.verticalBarEdgeActive = active
    }

    fun updateTopBarInset(points: Double) {
        val inset = points.coerceAtLeast(0.0)
        // The retained content controller inherits the navigation bar's safe-area overlap.
        if (kotlin.math.abs(navigation.scheduleLibraryChrome.barInset - inset) > 0.5) {
            navigation.scheduleLibraryChrome.barInset = inset
        }
        if (kotlin.math.abs(navigation.topBarInset - inset) > 0.5) {
            navigation.topBarInset = inset
        }
    }

    @Composable
    private fun HostEnvironment(content: @Composable () -> Unit) {
        CourseScheduleTheme(systemDarkTheme = systemDarkTheme) {
            // Keep observing window geometry: rotation/resizing can change each physical corner.
            ScreenCornerRadiusBridge()
            MiuixOverlayHostProvider(popupHost) {
                CompositionLocalProvider(
                    LocalAppRouter provides router,
                    LocalHostNavigation provides navigation,
                    LocalHostOverlays provides overlays,
                    content = content,
                )
            }
        }
    }

    /** Mirror all four [rememberScreenCornerRadiiPx] values in points for Swift. */
    @Composable
    private fun ScreenCornerRadiusBridge() {
        val density = LocalDensity.current
        val radiiPx = rememberScreenCornerRadiiPx()
        SideEffect {
            screenCornerRadiiPointsState = with(density) {
                ScreenCornerRadiiPoints(
                    topLeft = radiiPx.topLeft.toDp().value.toDouble(),
                    topRight = radiiPx.topRight.toDp().value.toDouble(),
                    bottomRight = radiiPx.bottomRight.toDp().value.toDouble(),
                    bottomLeft = radiiPx.bottomLeft.toDp().value.toDouble(),
                )
            }
        }
    }
}

private data class ScreenCornerRadiiPoints(
    val topLeft: Double = 0.0,
    val topRight: Double = 0.0,
    val bottomRight: Double = 0.0,
    val bottomLeft: Double = 0.0,
)

private data class NativeHostSnapshot(
    val selectedTab: Int,
    val tabCount: Int,
    val shiftMode: Boolean,
    val showsNativeTabBar: Boolean,
    val isDark: Boolean,
    val overlayActive: Boolean,
    val mainProgress: Double,
    val cutoutScale: Float,
    val cutoutCenterYRatio: Float,
    val cutoutCenterXRatio: Float,
    val nativeRouteIds: List<String>,
    val floatingOverlayActive: Boolean,
    val detailOverlayActive: Boolean,
    val nativeChrome: List<NativeDocumentChromeSnapshot>,
    val nativeTransitionInFlight: Boolean,
    val showsNativeHeader: Boolean,
    val isTabletLayout: Boolean,
    val keepsNativeHeaderDuringCutout: Boolean,
    val scheduleLibraryActive: Boolean,
    val scheduleLibraryTransitionActive: Boolean,
    val scheduleLibraryChrome: NativeDocumentChromeSnapshot,
)

private data class NativeDocumentChromeSnapshot(
    val title: String,
    val tintArgb: Long,
    val symbol: String?,
    val label: String?,
    val enabled: Boolean?,
    val busy: Boolean?,
    val successful: Boolean?,
    val contentOverlayActive: Boolean,
    val editorActive: Boolean,
    val leadingSymbol: String?,
    val leadingLabel: String?,
    val leadingEnabled: Boolean?,
    val leadingBusy: Boolean?,
    val secondarySymbol: String?,
    val secondaryLabel: String?,
    val secondaryEnabled: Boolean?,
    val secondaryBusy: Boolean?,
    val secondarySuccessful: Boolean?,
    // ⚠ 底栏与「顶栏交给系统」也要进快照，否则变化不触发 Swift 刷新（底栏不出现 / 标题停在旧值）
    val usesSystemHeader: Boolean,
    val bottomBarVisible: Boolean,
    val bottomTitle: String,
    val bottomSubtitle: String,
    val bottomBadge: String,
    val bottomToggleSymbol: String,
    val bottomPrimarySymbol: String,
    val bottomToggleEnabled: Boolean,
    val bottomPrimaryEnabled: Boolean,
)

private fun NativeDocumentChrome.snapshot(): NativeDocumentChromeSnapshot {
    val action = effectiveAction
    val leading = effectiveLeading
    val secondary = effectiveSecondaryAction
    val bar = bottomBar
    return NativeDocumentChromeSnapshot(
        editor?.title ?: title, tintArgb,
        action?.symbol, action?.label, action?.enabled, action?.busy, action?.successful,
        contentOverlayActive, editor != null,
        leading?.symbol, leading?.label, leading?.enabled, leading?.busy,
        secondary?.symbol, secondary?.label, secondary?.enabled, secondary?.busy, secondary?.successful,
        usesSystemHeader,
        bar != null, bar?.title.orEmpty(), bar?.subtitle.orEmpty(), bar?.badge.orEmpty(),
        bar?.toggleSymbol.orEmpty(), bar?.primarySymbol.orEmpty(),
        bar?.toggleEnabled == true, bar?.primaryEnabled == true,
    )
}

internal class NativeDocumentPage(
    val id: String,
    val route: AppRoute,
    val stateKey: String,
) {
    val chrome = NativeDocumentChrome().apply {
        if (route == AppRoute.EducationalImport) {
            // Install the trailing item before push; Compose keeps it up to date afterward.
            title = ImportStr.selectSchool()
            val updating = EduImportUpdater.isUpdating.value
            action = NativeDocumentAction(
                symbol = "arrow.clockwise",
                label = CommonStr.update(),
                enabled = !updating,
                busy = updating,
                onClick = { EduImportUpdater.forceUpdate() },
            )
        } else if (route == AppRoute.HolidaySettings) {
            // Include the item in UIKit's push transition before Compose publishes its handler.
            title = ChromeStr.holidaysTitle()
            action = NativeDocumentAction(
                symbol = "arrow.clockwise",
                label = CommonStr.update(),
                enabled = false,
                onClick = {},
            )
        }
    }
    // 随页存活：栈里这层不销毁，值就一直留着（出栈即整页丢弃，不做跨次恢复）
    val registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
    var controller: UIViewController? = null
}

internal fun AppRoute.isNativeDocument(): Boolean = nativeDocumentTitle() != null

internal fun AppRoute.nativeDocumentTitle(): String? = when (this) {
    // 设置二级页：走原生文档壳，顶栏与返回按钮由 iOS 原生 NavigationBar 给
    AppRoute.Settings -> CommonStr.settings()
    AppRoute.ScheduleSettings -> SettingsStr.scheduleSettings()
    AppRoute.Changelog -> ChromeStr.changelog()
    AppRoute.License -> ChromeStr.openSourceLicenses()
    AppRoute.PrivacyPolicy -> ChromeStr.privacyPolicy()
    AppRoute.Communication -> ChromeStr.communityFeedback()
    AppRoute.Feedback -> FeedbackStr.title()
    AppRoute.FeedbackHistory -> FeedbackStr.historyTitle()
    AppRoute.FeedbackRoadmap -> FeedbackStr.roadmapTitle()
    AppRoute.AppreciateAuthor -> ChromeStr.donate()
    AppRoute.PreferenceSettings -> ChromeStr.appPreferences()
    AppRoute.UpdateSettings -> ChromeStr.updateSettingsTitle()
    AppRoute.UpdatePreferences -> ChromeStr.updatePreferencesTitle()
    AppRoute.AiImport -> ChromeStr.aiImport()
    AppRoute.WidgetIntro -> ChromeStr.widgetsTitle()
    AppRoute.CourseReminder -> ChromeStr.courseReminders()
    AppRoute.HolidaySettings -> ChromeStr.holidaysTitle()
    AppRoute.WebDavSettings -> ChromeStr.webdavBackup()
    AppRoute.ScheduleBackup -> ChromeStr.scheduleBackup()
    AppRoute.ImportExport -> ChromeStr.importExport()
    AppRoute.About -> AboutStr.aboutApp()
    AppRoute.CourseTimeSettings -> ChromeStr.classTimeSettings()
    // 网页导入页与选择页同级：必须是原生文档（走 Compose 栈会被原生文档挡住）
    AppRoute.WebImport -> ChromeStr.webImport()
    // ⚠ 必须是原生文档：从「课表导出/导入/备份」这些原生文档页跳进来，
    // 走 Compose 栈会被上面压着的原生文档挡住（表现为「点了没反应」）
    AppRoute.EducationalImport -> ChromeStr.educationalImport()
    AppRoute.CourseManage -> SwitchStr.courseManage()
    AppRoute.Weather -> WeatherStr.title()
}
