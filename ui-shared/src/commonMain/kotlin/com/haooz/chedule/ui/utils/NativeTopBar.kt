package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 宿主自绘的主界面顶栏（当前只有鸿蒙 ArkUI）。
 *
 * 为什么整条顶栏都要搬走：顶栏的**模糊必须压在滚动内容之上**，而标题/星期行又必须压在
 * 模糊之上。skia 后端整块界面只有一个 XComponent，Compose 画不出「在原生层之上、在模糊
 * 之下」的东西，所以只搬材质层做不到 —— 要么整条搬，要么留在 Compose 里自己糊。
 *
 * 与 iOS 的 `NativeDocumentChrome` 同构：Compose 发布语义，原生渲染呈现。这里刻意**不复用**
 * [HostNavigationState]：那个类承载的是 iOS 导航栈/底栏/开洞缩放一整套语义，鸿蒙只需要顶栏
 * 这一件事，共用一个 provider 会把 iOS 的分支全部激活。
 */
class NativeTopBarHost {

    /** true = 由宿主画顶栏；Compose 侧据此一条都不画。 */
    var ownsTopBar by mutableStateOf(false)

    /** 最近一次发布的顶栏语义；null = 当前没有顶栏。 */
    var state by mutableStateOf<NativeTopBarState?>(null)

    /**
     * true = 暂时整条收起。
     *
     * push 了子页（设置、导入导出、文档页）时主内容在 `MainLayerTransition` 里缩放位移，
     * 而宿主顶栏在 Compose 之外、不会跟着动 —— 不收起就是「顶栏浮在新页面之上」。
     */
    var suppressed by mutableStateOf(false)

    /** 顶栏按钮点击（`switchSchedule` / `more` / `jumpWeek` / `customize` / `jumpToDate:<ISO>`）。 */
    var onAction: ((String) -> Unit)? = null
}

val LocalNativeTopBarHost = staticCompositionLocalOf<NativeTopBarHost?> { null }

/** 宿主自绘顶栏要的全部语义。 */
data class NativeTopBarState(
    /** 标题（「第 N 周」/「假期中」/「未开学」）；折叠动画由宿主自行处理。 */
    val title: String,
    /** 星期行（列数随课表设置变，可能是 5/6/7）。 */
    val days: List<NativeTopBarDay>,
    /** 滚动是否已过阈值（该不该上材质）；渐显渐隐的动画由宿主自己跑，不逐帧过桥。 */
    val materialVisible: Boolean,
    /** 今天高亮色（与 Compose 侧 `scheduleAccentColor()` 同源）。 */
    val accentArgb: Long,
    val onSurfaceArgb: Long,
    val onSurfaceVariantArgb: Long,
    /** 是否显示「切换课表」按钮（排班模式 / 平板时为 false）。 */
    val showsSwitch: Boolean,
    /** 是否显示「更多」按钮（平板为 false，右上角换成跳周 + 课表外观）。 */
    val showsMore: Boolean,
    /**
     * 「更多」菜单项。菜单本身由宿主用原生菜单渲染 —— 它必须压在顶栏之上，
     * 而 Compose 的弹层在同一块 surface 上，会被宿主的模糊层盖住。
     */
    val menu: List<NativeTopBarMenuItem>,
    /** 星期行左侧要让出节次列（手机 36dp / 平板 56dp），否则日期和网格列对不上。 */
    val leadingPadDp: Float,
    /** 星期行右侧内边距（含竖排 tab bar 的避让）。 */
    val trailingPadDp: Float,
    /** 顶栏总高（含状态栏，dp）：宿主照此绘制，Compose 的内容顶边按同一口径预留。 */
    val heightDp: Float,
    /**
     * 标题栏高度（**不含**状态栏，dp）：78dp 标题行 + 40dp 星期行。
     *
     * 宿主把 `statusBarDp + titleHeightDp` 交给 `Navigation.title({ height })`，
     * 并把内容整体下移 `statusBarDp`（实测标题栏从窗口顶端 y=0 开始排，不吃安全区）。
     */
    val titleHeightDp: Float,
    /**
     * 状态栏高度（dp）。顶部没有状态栏的形态（折叠屏竖排 tab bar）为 0。
     *
     * 必须由 Compose 报：只有它拿得到 `WindowInsets.statusBars`，宿主侧再算一份就会
     * 和 Compose 预留的内容顶边对不上（差几 dp 就是星期行压着第一节课）。
     */
    val statusBarDp: Float,
)

/**
 * 「更多」菜单里的一项。
 *
 * 只带 id 与文案：图标是 Compose 的 ImageVector，宿主拿不到，原生菜单不放图标。
 */
data class NativeTopBarMenuItem(
    val id: String,
    val label: String,
)

/** 星期行一格：星期名 + 月/日（课表没设日期时为 null）+ 是否今天。 */
data class NativeTopBarDay(
    val label: String,
    val date: String?,
    val isToday: Boolean,
)
