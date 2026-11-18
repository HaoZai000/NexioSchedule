package com.haooz.chedule.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

import com.haooz.nexioui.theme.NexioTheme

/** Navigation chrome owned by a native host, scoped to one app instance. */
class HostNavigationState {
    var selectedTab by mutableIntStateOf(0)
    var tabCount by mutableIntStateOf(3)
    var shiftMode by mutableStateOf(false)
    var showsNativeTabBar by mutableStateOf(true)
    var usesVerticalTabTransitions by mutableStateOf(false)
    var isDark by mutableStateOf(false)
    var bottomBarInset by mutableStateOf(0.0)
    var leftSafeAreaInset by mutableStateOf(0.0)
    var rightSafeAreaInset by mutableStateOf(0.0)
    var topBarInset by mutableStateOf(0.0)
    /**
     * 竖排 tab bar 形态（iOS 的 `traitCollection.verticalBarEdge` 为 leading/trailing）：
     * 折叠屏 iPhone 折叠态，tab bar 竖排到侧边、状态栏跟着移到右侧，**屏幕顶部没有状态栏**，
     * 顶栏糊带与星期行走另一套几何。
     *
     * 形态只认这个 trait：状态栏高度算出来是 0 有两种可能（顶部没有 / 被 `topBarInset` 吃光）。
     */
    var verticalBarEdgeActive by mutableStateOf(false)
    var onTabSelected: ((Int) -> Unit)? = null
    /**
     * 开洞缩放进度，1f=正常。宿主（当前只有 iOS）据此对原生 chrome 施加同样缩放——
     * Android 上底栏是 Compose 的，在主内容缩放层内，本就跟随，无需上报。
     */
    var cutoutScale by mutableFloatStateOf(1f)
    /**
     * 开洞缩放的变换原点纵比，与主内容 graphicsLayer 同源。开洞中心由
     * CustomizeScheduleScreen 回传，不是常量，故一并上报供原生 chrome 对齐。
     */
    var cutoutCenterYRatio by mutableFloatStateOf(0.5f)
    /**
     * 开洞缩放的变换原点横比。折叠屏上开洞要求整体左移（右侧被竖排底栏占掉一条），
     * 洞心不在屏幕中心，故与纵比一样由 CustomizeScheduleScreen 回传。
     */
    var cutoutCenterXRatio by mutableFloatStateOf(0.5f)
    /**
     * 开洞期间主内容整体上移的纵向位移（pt）。与主内容 graphicsLayer 的 translationY 同源同值
     */
    var onCutoutChromeOffsetY: ((Double) -> Unit)? = null
    /**
     * 连贯动画期间下层的实时模糊半径（dp），0 = 无模糊。
     *
     * iOS 上原生 chrome（底栏、顶栏按钮）与整个弹窗层都在 Compose 主内容之外，宿主据此在
     * 「详情页之下、其余一切之上」压一层同强度背景模糊；Android 不读这个值。
     * 只报页面级模糊，不含课程长按菜单 —— 它自己就在弹窗层内，糊了会连菜单一起糊。
     */
    var detailBackgroundBlur by mutableFloatStateOf(0f)
        private set
    /** 下层背景模糊强度（0…1，不是 dp）：iOS 没有可设的半径，宿主只能按强度插值。 */
    var onDetailBackdropBlurProgress: ((Double) -> Unit)? = null
    /**
     * 宿主是否自己接管「详情页下层背景模糊」。
     *
     * iOS 宿主在「详情页之下、其余一切之上」压了一层系统 material，Compose 侧就不该再给主内容
     * 挂 renderEffect —— 否则同一段内容被糊两次：视觉上更糊更白，成本也翻倍。
     * 只对页面级模糊生效；课程长按菜单仍在 Compose 层内自糊（宿主不读那段半径）。
     */
    var ownsDetailBackdropBlur by mutableStateOf(false)

    companion object {
        /** 详情 morph 的目标模糊半径（dp）；也是换算成 0…1 强度的基准，两处必须同源。 */
        const val DETAIL_BACKDROP_BLUR_DP = 16f
    }

    fun updateDetailBackgroundBlur(blurDp: Float) {
        if (detailBackgroundBlur == blurDp) return
        detailBackgroundBlur = blurDp
        onDetailBackdropBlurProgress?.invoke(
            (blurDp / DETAIL_BACKDROP_BLUR_DP).coerceIn(0f, 1f).toDouble()
        )
    }
    /** Whether the main screen top bar header controls are owned by native host chrome. */
    var showsNativeHeader by mutableStateOf(false)
    /**
     * 主界面是否跑在**平板（rail）布局**下。宽度 ≥ 600dp 即为 true
     */
    var isTabletLayout by mutableStateOf(false)
    /**
     * 课表外观页（开洞）期间，原生顶栏右侧是否继续保留。
     *
     * 开洞时主内容整体缩放，原生 chrome 在缩放层之外、得由宿主自己做同一套变换，
     * 否则右上角菜单只能被清空（「一进外观页就消失」）。Android 顶栏/底栏都在
     * Compose 缩放层内，不需要这个开关。
     */
    var keepsNativeHeaderDuringCutout by mutableStateOf(false)
    /** Root-hosted library keeps the existing card morph instead of pushing a document. */
    val scheduleLibraryChrome = NativeDocumentChrome()
    /** iOS installs a root overlay destination beneath its native navigation bar. */
    var scheduleLibraryOverlays: HostOverlayState? = null
    var scheduleLibraryActive by mutableStateOf(false)
    var scheduleLibraryTransitionActive by mutableStateOf(false)
    var onOpenSwitchSchedule: (() -> Unit)? = null
    var onJumpWeek: (() -> Unit)? = null
    var onCourseManage: (() -> Unit)? = null
    var onEnterCustomize: (() -> Unit)? = null
    var onJumpToDate: (() -> Unit)? = null
}

val LocalHostNavigation = staticCompositionLocalOf<HostNavigationState?> { null }

/** A fixed render destination for full-screen overlays above native chrome. */
class HostOverlayState {
    internal val entries = mutableStateListOf<HostOverlayEntry>()
    val isActive: Boolean get() = entries.any { it.active }

    /** 弹窗层之下的 overlay 是否在显示（iOS 上与弹窗层分开渲染） */
    val isBelowPopupsActive: Boolean get() = entries.any { it.active && !it.abovePopups }

    /** 弹窗层之上的 overlay（课程详情页）是否在显示；iOS 上它盖在宿主背景模糊层之上 */
    val isAbovePopupsActive: Boolean get() = entries.any { it.active && it.abovePopups }
}

internal class HostOverlayEntry {
    var active by mutableStateOf(false)
    /**
     * 渲染层：false = 弹窗层（`MiuixPopupHost`）之下（默认），true = 之上。
     * Android 的 overlay 就地渲染、天然在弹窗之上；iOS 两者是兄弟节点，得显式分层
     * （例：课程详情页必须盖住弹出它的「课程详情」底部弹窗）。
     */
    var abovePopups by mutableStateOf(false)
    var content by mutableStateOf<@Composable () -> Unit>({})
}

val LocalHostOverlays = staticCompositionLocalOf<HostOverlayState?> { null }

/** Registers once; content keeps its composition in the upper controller while idle. */
@Composable
fun BoxScope.HostedOverlay(
    active: Boolean,
    /** 见 [HostOverlayEntry.abovePopups]；宿主无分层概念时忽略（Android 就地渲染）。 */
    abovePopups: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalHostOverlays.current
    if (host == null) {
        content(this)
        return
    }
    val colors = NexioTheme.colorScheme
    val textStyles = NexioTheme.textStyles
    val isDark = NexioTheme.isDark
    val isDynamicColor = NexioTheme.isDynamicColor
    val forcedDark = LocalForcedDarkTheme.current
    val entry = remember(host) { HostOverlayEntry() }
    SideEffect {
        entry.active = active
        entry.abovePopups = abovePopups
        entry.content = {
            NexioTheme(
                isDark = isDark,
                colorScheme = colors,
                textStyles = textStyles,
                isDynamicColor = isDynamicColor,
            ) {
                CompositionLocalProvider(LocalForcedDarkTheme provides forcedDark) {
                    Box(Modifier.fillMaxSize(), content = content)
                }
            }
        }
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

@Composable
fun HostOverlayContent(
    state: HostOverlayState,
    /** 只渲染该层：false = 弹窗之下（默认），true = 弹窗之上。见 [HostOverlayEntry.abovePopups]。 */
    abovePopups: Boolean = false,
    drawInactiveEntries: () -> Boolean = { true },
) {
    Box(Modifier.fillMaxSize()) {
        for (entry in state.entries) {
            if (entry.abovePopups != abovePopups) continue
            androidx.compose.runtime.key(entry) {
                // Keep the composition alive without drawing or hit-testing hidden root chrome.
                Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (entry.active || drawInactiveEntries()) placeable.place(0, 0)
                    }
                }) {
                    entry.content()
                }
            }
        }
    }
}

/**
 * 是否竖排 tab bar 形态（折叠屏 iPhone 折叠态，屏幕顶部没有状态栏）：糊带收窄
 * [top.yukonga.miuix.kmp.layout.NoStatusBarBlurReduction]、星期行不再补那 40dp。
 * 无宿主（Android/桌面）恒为 false，走常规几何。
 */
@Composable
fun hostVerticalBarEdge(): Boolean = LocalHostNavigation.current?.verticalBarEdgeActive == true
