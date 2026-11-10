package com.haooz.nexio.ohos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.ComposeArkUIViewController
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.ui.activities.main.CourseScheduleApp
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.MainLayerTransition
import com.haooz.chedule.ui.navigation.NAV_PARALLAX
import com.haooz.chedule.ui.navigation.NavTransitionState
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.LocalNativeBottomBarHost
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.LocalNativeTopBarHost
import com.haooz.chedule.ui.utils.NativeBottomBarHost
import com.haooz.chedule.ui.utils.NativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeTopBarHost
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.roundToInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_env
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 鸿蒙侧的两个 Compose 实例（与 iOS 的 mainController / overlayController 同构）。
 *
 * 一个 XComponent 只有一个层，画在它里面的东西永远盖不住 ArkUI 的兄弟节点 ——
 * 顶栏按钮、底栏胶囊都是 ArkUI 画的，所以二级页必须待在**另一个 Compose 实例**里，
 * 由 ArkUI 把那一层排在顶栏/底栏之后（z 序更高）。这样设置页是真的压在它们上面，
 * 而不是靠「把顶栏底栏藏起来」装作被盖住。
 *
 * 两个实例共用下面这一份状态：Compose 的 snapshot state 是进程级的，两边读同一个对象，
 * 一边写另一边同帧失效 —— 不需要任何手工同步。
 */
private val sharedRouter = AppRouter()
private val sharedMainTransition = NavTransitionState(0f)
/** 覆盖层栈顶的转场进度：AppNavHost 写，顶栏位移发布器逐帧读。 */
private val sharedOverlayTransition = NavTransitionState(1f)
private val sharedTopBar = NativeTopBarHost().apply { ownsTopRight = true }
private val sharedBottomBar = NativeBottomBarHost().apply { ownsBottomBar = true }

/**
 * 每个路由层一个原生顶栏 chrome（与 iOS 的 NativeDocumentPage.chrome 同构）。
 *
 * key 与 `AppNavHost` 的层 key 一致（裸 id / `#2`…）。层被组合时注册、销毁时移除；
 * pop 的退出动画期间层还活着，chrome 也还在 —— 宿主靠它把退出中的顶栏一起画完。
 */
private val docChromes = mutableStateMapOf<String, NativeDocumentChrome>()

/**
 * 主界面实例。函数名与签名必须与 `@cpf-kmp-cmp/compose` HAR 的约定一致。
 *
 * `ComposeArkUIViewController(env, content)` 来自 `androidx.compose.ui.window`（OH 分支新增）。
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("MainArkUIViewController")
fun MainArkUIViewController(env: napi_env): napi_value {
    installPlatformBackends()
    return ComposeArkUIViewController(env) { OhosMainRoot() }
}

/**
 * 二级页实例：只画路由栈，主界面完全不在这个实例里。
 *
 * ⚠ 由 ArkUI 常驻挂载（不压栈时 `Visibility.Hidden`），不能按需创建 ——
 * 转场进度、页面状态全在这个组合里，重建一次就等于把正在滑的页面扔掉。
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("OverlayArkUIViewController")
fun OverlayArkUIViewController(env: napi_env): napi_value =
    ComposeArkUIViewController(env) { OhosOverlayRoot() }

/**
 * 系统返回键 → 出栈。
 *
 * 鸿蒙 target 上 `rememberNavigationBack` 是空实现，Compose 内部接不到系统返回；
 * ArkTS 侧 `onBackPressed` 检测到有子页时调这里消费事件，否则系统默认行为是退出应用。
 * ArkTS 主线程与 Compose 主线程同线程，直接改 snapshot list 是安全的。
 */
@CName("NexioOnOverlayPopBack")
fun NexioOnOverlayPopBack() {
    sharedRouter.popBack()
}

/** 主界面：常驻底座 + 顶栏/底栏语义发布。子页不在这里渲染。 */
@Composable
private fun OhosMainRoot() {
    installPlatformStorage()
    CourseScheduleTheme {
        BindNativeChrome()
        CompositionLocalProvider(
            LocalNativeTopBarHost provides sharedTopBar,
            LocalNativeBottomBarHost provides sharedBottomBar,
            LocalAppRouter provides sharedRouter,
        ) {
            Box(Modifier.fillMaxSize()) {
                // 主界面被推走的那段视差：进度由覆盖层的 AppNavHost 写，这里逐帧读
                MainLayerTransition(sharedMainTransition) {
                    CourseScheduleApp(
                        // 选壁纸入口：系统选图器只能由 ArkTS 起，桥在 ohosSharedCore 里
                        onPickWallpaperImage = PlatformWallpaperPicker,
                        // 鸿蒙没有应用内更新通道（应用市场分发），隐藏更新入口（同 iOS）
                        showUpdateSettings = false,
                    )
                }
                // push 的判定源：栈一变就发；隐藏时机的另一半在覆盖层（等退出动画播完）
                val hasOverlay = sharedRouter.hasOverlay
                SideEffect { OverlayBridge.publishMain(hasOverlay) }
                PublishMainChromeShift()
            }
        }
    }
}

/**
 * 主界面视差：把 `-NAV_PARALLAX × p` 推给宿主，顶栏圆钮与底栏胶囊跟主界面一起滑。
 *
 * 组合期读进度让本函数每帧重组（只有一个 SideEffect）。不用 snapshotFlow：那要等 effect
 * 协程被调度，至少晚一帧。跨渲染栈的 1 帧延迟消不掉，两端同速即可，不要补偿。
 */
@Composable
private fun PublishMainChromeShift() {
    val shift = -NAV_PARALLAX * sharedMainTransition.value
    SideEffect { MainMotionBridge.publish(shift) }
}

/** 二级页：整栈常驻渲染，转场、手势、return 全走与其它端同一份 `AppNavHost`。 */
@Composable
private fun OhosOverlayRoot() {
    CourseScheduleTheme {
        DisposableEffect(Unit) {
            DocChromeBridge.onAction = ::onDocChromeAction
            onDispose { DocChromeBridge.onAction = null }
        }
        CompositionLocalProvider(
            LocalAppRouter provides sharedRouter,
            LocalNativeTopBarHost provides sharedTopBar,
            LocalNativeBottomBarHost provides sharedBottomBar,
        ) {
            // onOverlayActivityChanged：栈内还有几层要画（含 pop 后正在播退场的那层）。
            // 它变 false = 退出动画彻底播完，覆盖层这时候才能被 ArkUI 藏掉。
            AppNavHost(
                router = sharedRouter,
                mainTransition = sharedMainTransition,
                onOverlayActivityChanged = { OverlayBridge.publishOverlay(it) },
                transitionState = sharedOverlayTransition,
            ) { key, route ->
                val chrome = remember(key) { NativeDocumentChrome() }
                DisposableEffect(key) {
                    docChromes[key] = chrome
                    onDispose { docChromes.remove(key) }
                }
                // 页面看到 chrome 就不画自己的返回/动作按钮（DocumentPageScaffold 的约定，
                // 与 iOS 同一套协议），改由 ArkUI 的 Navigation.title 画
                CompositionLocalProvider(LocalNativeDocumentChrome provides chrome) {
                    OhosRouteContent(sharedRouter, route)
                }
            }
        }
        PublishDocContents()
        PublishDocMotion()
    }
}

/** 宿主点了某一层顶栏的按钮（ArkTS 主线程 = Compose 主线程，直接改 snapshot 安全）。 */
private fun onDocChromeAction(key: String, role: String) {
    NexioLog.w("DocChrome", "action key=$key role=$role")
    val chrome = docChromes[key] ?: return
    when (role) {
        "back" -> sharedRouter.popBack()
        "leading" -> chrome.effectiveLeading?.onClick?.invoke()
        "action" -> chrome.effectiveAction?.onClick?.invoke()
        "secondary" -> chrome.effectiveSecondaryAction?.onClick?.invoke()
    }
}

/**
 * 每层 chrome 的内容 → ArkUI，只在语义变化时重组。
 *
 * 与位移分家：合在一起的话转场每帧都要把所有层重编一遍 JSON，编完再被去重掉。
 */
@Composable
private fun PublishDocContents() {
    // 组合期读全部字段：任何一个变了都会重组 → SideEffect 重发
    val onSurfaceArgb = MiuixTheme.colorScheme.onSurface.toArgb().toLong() and 0xFFFFFFFFL
    val contents = docChromes.entries.toList()
        .mapNotNull { (key, chrome) -> encodeDocChrome(key, chrome, onSurfaceArgb) }
    val json = "[" + contents.joinToString(",") + "]"
    SideEffect { DocChromeBridge.publishChrome(json) }
}

/**
 * 位移 → ArkUI，转场期间逐帧（公式与 `PageLayer` 一致，原生按钮才能和自己的页面同帧滑）。
 *
 * ⚠ 层列表必须在 SideEffect 里读，不能读在组合期：出栈收尾那一帧 p 被拉回 1 且那一层
 * 被摘掉，组合期拿到的旧表会算出 `1-p = 0`，左上角闪一下返回钮。
 */
@Composable
private fun PublishDocMotion() {
    val p = sharedOverlayTransition.value
    SideEffect { DocChromeBridge.publishMotion(encodeDocMotion(docChromes.entries.toList(), p)) }
}

/** 没有标题的 chrome（页面没走 DocumentPageScaffold）不发布 —— 那种页自己画按钮。
 *  `label` = 黑白前景（onSurface，iOS 的 `.label` 对应物）：返回图标与动作文字用它；
 *  `tint`（主题 accent）照发，宿主将来要给个别部件着色时用。 */
private fun encodeDocChrome(key: String, chrome: NativeDocumentChrome, onSurfaceArgb: Long): String? {
    val title = chrome.title
    if (title.isEmpty()) return null
    val leading = chrome.effectiveLeading
    val action = chrome.effectiveAction
    val secondary = chrome.effectiveSecondaryAction
    return buildJsonObject {
        put("key", key)
        put("title", title)
        put("tint", chrome.tintArgb.toDouble())
        put("label", onSurfaceArgb.toDouble())
        put("back", leading == null)
        leading?.let {
            putJsonObject("leading") {
                put("symbol", it.symbol)
                put("label", it.label)
                put("enabled", it.enabled && !it.busy)
            }
        }
        action?.let {
            putJsonObject("action") {
                put("symbol", it.symbol)
                put("label", it.label)
                put("enabled", it.enabled && !it.busy)
            }
        }
        secondary?.let {
            putJsonObject("secondary") {
                put("symbol", it.symbol)
                put("label", it.label)
                put("enabled", it.enabled && !it.busy)
            }
        }
    }.toString()
}

/**
 * 末位（栈顶或退出中的那层）`1-p`，次顶 `-NAV_PARALLAX*p`；静止时不发次顶（否则留幽灵按钮）。
 *
 * 精度 1/10000 屏宽：按 1/100 每档 3.6vp，返回钮会一跳一跳。
 */
private fun encodeDocMotion(entries: List<Map.Entry<String, NativeDocumentChrome>>, p: Float): String =
    entries.asReversed().take(2)
        .mapIndexedNotNull { i, entry ->
            if (i == 1 && p >= 0.999f) {
                return@mapIndexedNotNull null
            }
            val frac = if (i == 0) 1f - p else -NAV_PARALLAX * p
            "[" + quote(entry.key) + "," + ((frac * 10000f).roundToInt()) + "]"
        }.joinToString(",", "[", "]")

private fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/**
 * 顶栏/底栏语义 → ArkUI。
 *
 * 这里把语义推过去、把宿主点的东西转回来。用 `onState` 同步出口而不是 `snapshotFlow`：
 * 后者要等 Compose 的 effect 协程被调度，至少晚一帧，翻页时按钮会肉眼可见地落后于页面。
 */
@Composable
private fun BindNativeChrome() {
    val scope = rememberCoroutineScope()
    DisposableEffect(sharedTopBar, scope) {
        TopBarBridge.bind(sharedTopBar, scope)
        sharedTopBar.onState = { TopBarBridge.publish(it) }
        // 绑定前可能已经发布过（首帧顺序不定），补一次
        TopBarBridge.publish(sharedTopBar.state)
        onDispose {
            sharedTopBar.onState = null
            TopBarBridge.publish(null)
        }
    }
    DisposableEffect(sharedBottomBar, scope) {
        BottomBarBridge.bind(sharedBottomBar, scope)
        sharedBottomBar.onState = { BottomBarBridge.publish(it) }
        BottomBarBridge.publish(sharedBottomBar.state)
        onDispose {
            sharedBottomBar.onState = null
            BottomBarBridge.publish(null)
        }
    }
}

private fun installPlatformBackends() {
    if (!OhosEnv.isReady) return
    if (WallpaperStore.backend == null) {
        val backend = OhosWallpaperBackend()
        WallpaperStore.backend = backend
        WallpaperStore.synchronousLoader = { backend.loadNow() }
    }
}
