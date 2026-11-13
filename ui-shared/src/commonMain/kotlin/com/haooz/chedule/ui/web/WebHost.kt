package com.haooz.chedule.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.model.Course

// ════════════════════════════════════════════════════════════════════════
//  网页内容宿主 —— 教务导入用的 WebView 的平台无关契约
//
//  ⚠ 按框架铁律切开：`WebViewScreen` 的外壳（顶栏 / 进度条 / 底部胶囊 / 三个对话框 / 目标课表选择）是纯 Compose，进 commonMain；网页本体（`android.webkit.WebView` + PixelCopy 采色 + `addJavascriptInterface` 桥）留宿主 —— iOS 要整体换 WKWebView，一行都复用不了。
//
//  与 todayAssistantCard 的槽位注入不同：那个只注入一段 UI，这里还要注入行为（前进后退 / 执行 JS / 切桌面模式），所以是「接口 + 工厂」而非纯 @Composable 槽位。
// ════════════════════════════════════════════════════════════════════════

/**
 * 宿主构造 [WebHost] 时需要的配置 —— 全部来自页面状态。
 *
 * ⚠ 这些字段会变（桌面模式可切换、`contentTopPadding` 随顶栏高度变），宿主必须用 `LaunchedEffect` / `rememberUpdatedState` 跟上，不能只在首次读一次。
 */
class WebHostConfig(
    /** 教务系统入口地址；null = 无（页面会兜底 about:blank） */
    val importUrl: String?,
    /** 适配脚本在仓库里的相对路径；null = 该校无脚本（此时不注册导入桥） */
    val assetJsPath: String?,
    /** 学校资源目录名，脚本按它落盘 */
    val resourceFolder: String,
    /** 桌面版（平板默认 true）：决定 UA 与视口覆盖策略 */
    val isDesktopMode: Boolean,
    /** 内容顶部让位高度（顶栏 + 状态栏） */
    val contentTopPadding: Dp,
    /**
     * 内容底部额外让位高度（悬浮控件高度）。
     *
     * iOS 侧叠加到系统安全区之上写进 `scrollView.contentInset`，避免内容被底部悬浮条挡住；
     * Android 的 WebView 在 Compose 里走自己的布局，不使用这个值。
     */
    val contentBottomPadding: Dp = 0.dp,
    /** 学校名（原生底部条要展示）；Android / iOS 用不到，默认空。 */
    val schoolName: String = "",
    /** 全部课表名（原生底部条的「执行导入」选择目标课表用）。 */
    val scheduleNames: List<String> = emptyList(),
    /** 当前课表名（原生选择器的默认项）。 */
    val currentScheduleName: String = "",
)

/**
 * 宿主向页面回报的事件。
 *
 * ⚠ 宿主必须用 `rememberUpdatedState` 持有本对象：页面会随重组重建它，而 WebView 的回调是长生命周期对象，持旧闭包会写到过期的 state 上。
 */
class WebHostEvents(
    /** 页面标题 */
    val onTitle: (String) -> Unit = {},
    /** 加载进度 0f..1f */
    val onProgress: (Float) -> Unit = {},
    /**
     * 顶栏空白区当前上屏色（Android 用 `PixelCopy` 逐帧采样页面顶部一小段取众数色）。
     * 无此能力的平台不上报，页面就不画那条色带。
     */
    val onTopColor: (Color) -> Unit = {},
    /** JS `alert` —— 页面弹对话框，用户选择通过 `onResult` 回传 */
    val onAlert: (
        title: String, content: String, confirmText: String, onResult: (Boolean) -> Unit,
    ) -> Unit = { _, _, _, _ -> },
    /** JS `prompt` —— 同上，取消时 `onResult(null)` */
    val onPrompt: (
        title: String, tip: String, defaultText: String, validatorJs: String,
        onResult: (String?) -> Unit,
    ) -> Unit = { _, _, _, _, _ -> },
    /** JS 单选（原生单选列表对话框）—— 取消时 `onResult(null)` */
    val onSingleSelection: (
        title: String, items: List<String>, defaultIndex: Int, onResult: (Int?) -> Unit,
    ) -> Unit = { _, _, _, _ -> },
    /** 脚本导入完成，带回课程 */
    val onCoursesImported: (List<Course>) -> Unit = {},
    /** 脚本走完全流程 —— 页面据此收尾并返回 */
    val onTaskCompleted: () -> Unit = {},
)

/**
 * 网页内容宿主。
 *
 * 生命周期由宿主实现自己管（Android 侧在 `DisposableEffect` 里 stopLoading /
 * clearCache / clearHistory / destroy）—— 「怎么销毁一个 WebView」本身就是平台知识。
 */
interface WebHost {

    /** 渲染网页内容。页面负责给它正确的尺寸与层级（含 `contentTopPadding` 让位） */
    @Composable
    fun Content(modifier: Modifier)

    // ⚠ 刻意没有 loadUrl / setDesktopMode setter：初始地址与桌面模式都由 [WebHostConfig] 驱动，宿主用 `LaunchedEffect(config.xxx)` 跟 —— 接口上再开一对 setter 就是两个真相源。

    fun reload()

    fun canGoBack(): Boolean

    fun goBack()

    /**
     * 当前页面主机名（去掉 `www.` 前缀，小写）；无页面时 null。
     *
     * 用途：切桌面模式后判断是否被站点重定向到手机专属域名（如 `m.xxx.com`）——
     * 那种情况下 `reload()` 仍是手机版，必须带新 UA 重新访问入口。
     */
    fun currentHostOrNull(): String?

    /** 在页面里执行 JS（导入脚本靠它注入） */
    fun evaluateJavascript(js: String)

    /** 告知导入桥「往哪个课表导」（对应 `window.currentTableId`） */
    fun setImportTableId(tableId: String)
}

/**
 * 空实现 —— 桌面/iOS 尚未提供网页宿主时使用。
 *
 * 只保证「点了不崩」：内容区留白，控制方法全部空转，`canGoBack` 恒 false
 *（于是返回键直接走页面的 `onBack`）。
 */
object NoopWebHost : WebHost {
    @Composable
    override fun Content(modifier: Modifier) = Unit
    override fun reload() = Unit
    override fun canGoBack(): Boolean = false
    override fun goBack() = Unit
    override fun currentHostOrNull(): String? = null
    override fun evaluateJavascript(js: String) = Unit
    override fun setImportTableId(tableId: String) = Unit
}
