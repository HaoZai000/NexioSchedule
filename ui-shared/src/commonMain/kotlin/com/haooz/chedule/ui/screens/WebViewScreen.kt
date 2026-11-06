/** WebView 页面 - 用于加载教务系统网页 */
package com.haooz.chedule.ui.screens

// ⚠ 本文件不得出现 android.* / androidx.activity / androidx.core；
// 需要新能力就往 WebHost 契约上加方法，而不是把平台类型带进来。

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.ScriptRepository
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.glass.BarFill
import com.haooz.chedule.ui.glass.BrightLargeTone
import com.haooz.chedule.ui.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.glass.applyLiveGlass
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentBottomBar
import com.haooz.chedule.ui.utils.NativeDocumentBottomBarEffect
import com.haooz.chedule.ui.utils.bottomFloatingPadding
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.ui.web.NoopWebHost
import com.haooz.chedule.ui.web.WebHost
import com.haooz.chedule.ui.web.WebHostConfig
import com.haooz.chedule.ui.web.WebHostEvents
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_phone_pc
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor


/** 网页页底部 dock 的柔光 shader 缓存键：尺寸与其他玻璃件不同，不能共用 uniform。 */
private const val LIVE_WEB_DOCK_GLASS_KEY = "NexioSoftGlassLiveWebDock"

private data class AlertData(
    val title: String,
    val content: String,
    val confirmText: String,
    val onResult: (Boolean) -> Unit
)

private data class PromptData(
    val title: String,
    val tip: String,
    val defaultText: String,
    val onResult: (String?) -> Unit
)

private data class SelectionData(
    val title: String,
    val items: List<String>,
    val defaultIndex: Int,
    val onResult: (Int?) -> Unit
)

@Composable
fun WebViewScreen(
    school: SchoolData,
    adapterId: String,
    importUrl: String?,
    assetJsPath: String?,
    webContentBackdrop: LayerBackdrop,
    contentTopPadding: Dp = 0.dp,
    scheduleNames: List<String> = emptyList(),
    currentScheduleName: String = "",
    onBack: () -> Unit,
    onImportComplete: (List<Course>) -> Unit,
    onTaskCompleted: () -> Unit = {},
    onPageTitleChanged: (String) -> Unit = {},
    /** 顶栏空白区当前上屏色（已含插值），供顶栏标题/图标按明暗切黑白 */
    onTopColorChanged: (Color) -> Unit = {},
    onReloadRef: ((() -> Unit) -> Unit)? = null,
    /** 网页宿主工厂：Android 注入 `AndroidWebHost`，桌面/iOS 用 [NoopWebHost]。 */
    webHostFactory: @Composable (WebHostConfig, WebHostEvents) -> WebHost = { _, _ -> NoopWebHost },
) {
    val isTablet = isTabletWidth()
    // iOS 原生文档页把底部悬浮条交给系统控件，本页不再画 Compose 胶囊；Android / 桌面没有 chrome。
    val nativeChrome = LocalNativeDocumentChrome.current
    // 76dp = Swift 侧底栏高度（44 按钮 + 上下 10 内边距 + 12 让位），给网页留出底部让位
    val bottomBarHeight = 76.dp
    val tabletHorizontalPadding = if (isTablet) {
        val widthDp = screenWidthDp()
        ((widthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 0.dp
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    // 顶栏采样色：宿主逐帧上报（Android = PixelCopy）；null = 该平台没有这个能力，不画色带
    var topColor by remember { mutableStateOf<Color?>(null) }
    var pageTitle by remember(AppLanguage.resolved) { mutableStateOf(CommonStr.loading()) }
    // 平板默认桌面版，手机默认手机版（UA 与视口策略都跟着走，见 WebCompatDelegate）
    var isDesktopMode by remember { mutableStateOf(isTablet) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(pageTitle) { onPageTitleChanged(pageTitle) }
    val currentOnTopColorChanged by rememberUpdatedState(onTopColorChanged)

    // 进入页面时按需预下载适配脚本，失败不阻塞页面
    LaunchedEffect(school.resourceFolder, assetJsPath) {
        val path = assetJsPath ?: return@LaunchedEffect
        ScriptRepository(ScriptRepository.getRepoUrl())
            .ensureScript(school.resourceFolder, path)
    }

    var alertData by remember { mutableStateOf<AlertData?>(null) }
    var promptData by remember { mutableStateOf<PromptData?>(null) }
    var selectionData by remember { mutableStateOf<SelectionData?>(null) }
    var showCourseTablePicker by remember { mutableStateOf(false) }
    val webDockShapeBlock: () -> Shape = remember { { ContinuousCapsule() } }
    // 柔光玻璃：材质、档位、调色全由材质层给出；模糊走档位值，无材质档整体关。
    // effects 必须记住引用，否则 drawBackdrop 每帧重新录制采样层。
    val webDockGlassBlur = AppMaterialSettings.glassBlurDp()
    val webDockGlassOn = AppMaterialSettings.glassEffectsEnabled()
    val webDockGlassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = isAppDarkTheme(),
        // 底部 dock 是「贴底 bar」类玻璃：走 BarFill + 大控件材质。
        fill = BarFill,
        tone = BrightLargeTone,
    )
    val webDockEffects: BackdropEffectScope.() -> Unit =
        remember(webDockGlassBlur, webDockGlassOn, webDockGlassStyle) {
            {
                if (webDockGlassOn) {
                    blur(webDockGlassBlur.dp.toPx())
                    runtimeShaderEffect(
                        key = LIVE_WEB_DOCK_GLASS_KEY,
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applyLiveGlass(
                            style = webDockGlassStyle,
                            width = size.width,
                            height = size.height,
                        )
                    }
                }
            }
        }
    val isLightTheme = !isAppDarkTheme()
    // 底部胶囊底色：莫奈取色下跟随 surfaceContainer（与首页底栏、切换课表底栏同一套）；
    // 非莫奈保持原硬编码（亮 #FFFFFF/0.6、暗 #121212/0.54）；无材质档实色
    val webDockSurfaceColor = if (MiuixTheme.isDynamicColor) {
        MiuixTheme.colorScheme.surfaceContainer
            .copy(alpha = com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(if (isLightTheme) 0.6f else 0.54f))
    } else if (isLightTheme) {
        Color(0xFFFFFFFF).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.6f))
    } else {
        Color(0xFF121212).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.54f))
    }
    val webDockOnDrawSurface: DrawScope.() -> Unit = remember(webDockSurfaceColor) {
        {
            drawRect(webDockSurfaceColor)
        }
    }
    var importCompleted by remember { mutableStateOf(false) }

    val currentOnImportComplete by rememberUpdatedState(onImportComplete)
    val currentOnTaskCompleted by rememberUpdatedState(onTaskCompleted)
    val currentOnBack by rememberUpdatedState(onBack)

    val showAlertCallback by rememberUpdatedState { title: String, content: String, confirmText: String, onResult: (Boolean) -> Unit ->
        alertData = AlertData(title, content, confirmText, onResult)
    }
    val showPromptCallback by rememberUpdatedState { title: String, tip: String, defaultText: String, validatorJs: String, onResult: (String?) -> Unit ->
        promptData = PromptData(title, tip, defaultText, onResult)
    }
    val showSelectionCallback by rememberUpdatedState { title: String, items: List<String>, defaultIndex: Int, onResult: (Int?) -> Unit ->
        selectionData = SelectionData(title, items, defaultIndex, onResult)
    }


    // ── 网页宿主 ──
    // 网页本体（WebView / JS 桥 / PixelCopy 采色）在平台侧，页面只通过 WebHost 契约驱动它。
    // events 用 remember 建一次：宿主内部以 rememberUpdatedState 持有，闭包读的都是最新的 MutableState。
    val webHostEvents = remember {
        WebHostEvents(
            onTitle = { pageTitle = it },
            onProgress = { loadingProgress = it },
            onTopColor = { color ->
                topColor = color
                currentOnTopColorChanged(color)
            },
            onAlert = { title, content, confirmText, onResult ->
                alertData = AlertData(title, content, confirmText, onResult)
            },
            onPrompt = { title, tip, defaultText, validatorJs, onResult ->
                promptData = PromptData(title, tip, defaultText, onResult)
            },
            onSingleSelection = { title, items, defaultIndex, onResult ->
                selectionData = SelectionData(title, items, defaultIndex, onResult)
            },
            onCoursesImported = { courses ->
                importCompleted = true
                currentOnImportComplete(courses)
            },
            onTaskCompleted = {
                currentOnTaskCompleted()
                if (!importCompleted) {
                    showToast(SchedStr.importDone(), longDuration = true)
                }
                currentOnBack()
            },
        )
    }
    val webHost = webHostFactory(
        WebHostConfig(
            importUrl = importUrl,
            assetJsPath = assetJsPath,
            resourceFolder = school.resourceFolder,
            isDesktopMode = isDesktopMode,
            // iOS：顶部让位交给系统安全区，Compose 不推 WebView
            contentTopPadding = if (nativeChrome != null) 0.dp else contentTopPadding,
            // 原生底栏时给网页留出高度；Android 走自己的布局
            contentBottomPadding = if (nativeChrome != null) bottomBarHeight else 0.dp,
        ),
        webHostEvents,
    )
    // Alert 对话框
    alertData?.let { data ->
        WebAlertDialog(
            title = data.title,
            content = data.content,
            confirmText = data.confirmText,
            tabletHorizontalPadding = tabletHorizontalPadding,
            onConfirm = {
                data.onResult(true)
                alertData = null
            },
            onDismiss = {
                data.onResult(false)
                alertData = null
            }
        )
    }

    // Prompt 对话框
    promptData?.let { data ->
        WebPromptDialog(
            title = data.title,
            tip = data.tip,
            defaultText = data.defaultText,
            tabletHorizontalPadding = tabletHorizontalPadding,
            onConfirm = { input ->
                data.onResult(input)
                promptData = null
            },
            onDismiss = {
                data.onResult(null)
                promptData = null
            }
        )
    }

    val onExecuteImport: () -> Unit = {
        if (assetJsPath != null) {
            showCourseTablePicker = true
        } else {
            showToast(SchedStr.noImportScript(), longDuration = true)
        }
    }

    // 原生底部悬浮条：页面只给语义，玻璃材质 / 布局 / 安全区由系统控件渲染
    NativeDocumentBottomBarEffect(
        if (nativeChrome != null) {
            NativeDocumentBottomBar(
                title = school.name,
                subtitle = SchedStr.webImportSubtitle(),
                badge = if (isDesktopMode) SchedStr.desktopMode() else SchedStr.mobileMode(),
                toggleSymbol = "rectangle.on.rectangle",
                toggleLabel = SchedStr.toggleDesktopMode(),
                primarySymbol = "square.and.arrow.down",
                primaryLabel = SchedStr.runImport(),
                primaryEnabled = assetJsPath != null,
                onToggle = { isDesktopMode = !isDesktopMode },
                onPrimary = onExecuteImport,
            )
        } else null
    )

    val scope = rememberCoroutineScope()

    val executeImportWithTable: (String) -> Unit = { tableId ->
        assetJsPath?.let { path ->
            scope.launch {
                val scriptFile = ScriptRepository(ScriptRepository.getRepoUrl())
                    .ensureScript(school.resourceFolder, path)
                if (scriptFile != null) {
                    val jsCode = scriptFile.readText()
                    webHost.setImportTableId(tableId)
                    // 课表名可含引号/换行，必须转义成合法 JS 字面量（否则整段脚本语法错误）
                    val tableIdLiteral = Json.encodeToString(tableId)
                    val fullJsCode = "window.currentTableId = $tableIdLiteral;\n$jsCode"
                    webHost.evaluateJavascript(fullJsCode)
                    showToast(SchedStr.runningImportScript())
                } else {
                    showToast(SchedStr.scriptDownloadFailed(path), longDuration = true)
                }
            }
        } ?: showToast(SchedStr.noImportScript(), longDuration = true)
    }

    // 单选列表对话框
    selectionData?.let { data ->
        WebSelectionDialog(
            title = data.title,
            items = data.items,
            defaultIndex = data.defaultIndex,
            tabletHorizontalPadding = tabletHorizontalPadding,
            onSelect = { index ->
                data.onResult(index)
                selectionData = null
            },
            onDismiss = {
                data.onResult(null)
                selectionData = null
            }
        )
    }

    // 执行导入前的目标课表选择对话框
    if (showCourseTablePicker) {
        CourseTablePickerDialog(
            scheduleNames = scheduleNames,
            currentScheduleName = currentScheduleName,
            tabletHorizontalPadding = tabletHorizontalPadding,
            onDismissRequest = { showCourseTablePicker = false },
            onTableSelected = { tableId ->
                showCourseTablePicker = false
                executeImportWithTable(tableId)
            }
        )
    }


    NavigationBackHandlerFor(
        enabled = true,
        onBackCompleted = {
            if (webHost.canGoBack()) webHost.goBack() else onBack()
        },
        onBackCancelled = {},
    )

    LaunchedEffect(onReloadRef) { onReloadRef?.invoke { webHost.reload() } }

    Box(modifier = Modifier.fillMaxSize()) {
        // 生产者包住 WebView：底部玻璃从这里实时采样页面像素
        // 顶栏在宿主层，这里只负责页面 + 底部胶囊
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
                // 无材质档：底部胶囊玻璃全关，页面采样录屏层不挂
                .then(
                    if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                    else Modifier.layerBackdrop(webContentBackdrop)
                )
        ) {
            // 顶栏区域：宿主采到的上屏色铺满 contentTopPadding。
            // ⚠ 必须 animateColorAsState 连续过渡：宿主采样已节流到 8Hz 且只报目标色
            // （加变化阈值），直接 background(target) 会一跳一跳、且每次上报都触发整条顶栏
            // 重组（CollapsibleTopAppBar 内含多个 Animatable，不便宜）；动画在 render 线程
            // 驱动 60fps，采样降频不影响帧率，重组只在目标色离散跳变时发生（稀疏）。
            topColor?.let { color ->
                val animatedColor by animateColorAsState(
                    targetValue = color,
                    animationSpec = tween(durationMillis = 300),
                    label = "webTopMirrorColor"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contentTopPadding)
                        .background(animatedColor)
                )
            }
            Box(
                modifier = Modifier
                    // 原生文档页不推：让网页铺到导航栏下面，顶部让位由系统安全区处理
                    .padding(top = if (nativeChrome != null) 0.dp else contentTopPadding)
                    .fillMaxSize()
            ) {
                webHost.Content(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                )

                AnimatedVisibility(
                    visible = loadingProgress in 0.01f..0.99f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MiuixTheme.colorScheme.primary)
                    )
                }
            }
        }

        // 原生文档页（iOS）用系统控件画这条玻璃胶囊，Compose 再画一条就是双层
        if (nativeChrome == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp + tabletHorizontalPadding)
                    // 与首页底栏同一套底部间距算法
                    .padding(bottom = bottomFloatingPadding())
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = webContentBackdrop,
                        shape = webDockShapeBlock,
                        effects = webDockEffects,
                        highlight = null,
                        // 柔光自己画玻璃色与方向光；无材质档不跑 shader，必须留容器色
                        onDrawSurface = if (webDockGlassOn) null else webDockOnDrawSurface
                    )
            ) {
                WebImportDockContent(
                    schoolName = school.name,
                    isDesktopMode = isDesktopMode,
                    onToggleDesktopMode = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        isDesktopMode = !isDesktopMode
                    },
                    onExecuteImport = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onExecuteImport()
                    },
                    importEnabled = assetJsPath != null
                )
            }
        }
    }
}

@Composable
private fun WebImportDockContent(
    schoolName: String,
    isDesktopMode: Boolean,
    onToggleDesktopMode: () -> Unit,
    onExecuteImport: () -> Unit,
    importEnabled: Boolean
) {
    val isLightTheme = !isAppDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 11.dp, end = 12.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = schoolName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                // 仅展示当前模式，不可点击
                Surface(
                    shape = ContinuousRoundedRectangle(4.dp),
                    color = if (isDesktopMode)
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else
                        Color(0xFF66BB6A).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isDesktopMode) SchedStr.desktopMode() else SchedStr.mobileMode(),
                        fontSize = 12.sp,
                        color = if (isDesktopMode)
                            MiuixTheme.colorScheme.primary
                        else
                            Color(0xFF66BB6A),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                // 标签不要顶到右侧按钮
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = SchedStr.webImportSubtitle(),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 手机/电脑切换：固定图标、不随状态变色
            Surface(
                modifier = Modifier.size(44.dp),
                shape = ContinuousRoundedRectangle(22.dp),
                color = if (isLightTheme) Color.Black.copy(alpha = 0.08f)
                else Color.White.copy(alpha = 0.1f)
            ) {
                IconButton(onClick = onToggleDesktopMode) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_phone_pc),
                        contentDescription = SchedStr.toggleDesktopMode(),
                        modifier = Modifier.size(22.dp),
                        tint = if (isLightTheme) Color.Black else Color.White
                    )
                }
            }

            Surface(
                modifier = Modifier.size(44.dp),
                shape = ContinuousRoundedRectangle(22.dp),
                color = if (importEnabled)
                    MiuixTheme.colorScheme.primary
                else
                    MiuixTheme.colorScheme.surfaceVariant
            ) {
                IconButton(
                    onClick = onExecuteImport,
                    enabled = importEnabled
                ) {
                    Icon(
                        MiuixIcons.Normal.Download,
                        contentDescription = SchedStr.runImport(),
                        modifier = Modifier.size(26.dp),
                        tint = if (importEnabled)
                            MiuixTheme.colorScheme.onPrimary
                        else
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
        }
    }
}

@Composable
private fun WebAlertDialog(
    title: String,
    content: String,
    confirmText: String,
    tabletHorizontalPadding: Dp = 0.dp,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = ContinuousRoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp + tabletHorizontalPadding)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = content,
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        text = confirmText,
                        onClick = onConfirm,
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

@Composable
private fun WebPromptDialog(
    title: String,
    tip: String,
    defaultText: String,
    tabletHorizontalPadding: Dp = 0.dp,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(defaultText)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = ContinuousRoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp + tabletHorizontalPadding)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tip,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = ContinuousRoundedRectangle(12.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant
                ) {
                    NativeMiuixTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        requestFocus = true
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = CommonStr.ok(),
                        onClick = { onConfirm(textFieldValue.text) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WebSelectionDialog(
    title: String,
    items: List<String>,
    defaultIndex: Int,
    tabletHorizontalPadding: Dp = 0.dp,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(defaultIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = ContinuousRoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp + tabletHorizontalPadding)
                .heightIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(items) { index, item ->
                        val isSelected = index == selectedIndex
                        Surface(
                            shape = ContinuousRoundedRectangle(12.dp),
                            color = if (isSelected)
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ContinuousRoundedRectangle(12.dp))
                                .clickable {
                                    selectedIndex = index
                                }
                        ) {
                            Text(
                                text = item,
                                fontSize = 16.sp,
                                color = if (isSelected)
                                    MiuixTheme.colorScheme.primary
                                else
                                    MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = CommonStr.ok(),
                        onClick = { onSelect(selectedIndex) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseTablePickerDialog(
    scheduleNames: List<String>,
    currentScheduleName: String,
    tabletHorizontalPadding: Dp = 0.dp,
    onDismissRequest: () -> Unit,
    onTableSelected: (String) -> Unit
) {
    var selectedSchedule by remember { mutableStateOf(currentScheduleName.ifBlank { scheduleNames.firstOrNull() ?: "" }) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = ContinuousRoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp + tabletHorizontalPadding)
                .heightIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = SchedStr.selectScheduleToImport(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = SchedStr.importTargetHint(),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(scheduleNames) { index, name ->
                        val isCurrent = name == currentScheduleName
                        val isSelected = name == selectedSchedule
                        Surface(
                            shape = ContinuousRoundedRectangle(12.dp),
                            color = if (isSelected)
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ContinuousRoundedRectangle(12.dp))
                                .clickable {
                                    selectedSchedule = name
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 16.sp,
                                    color = if (isSelected)
                                        MiuixTheme.colorScheme.primary
                                    else
                                        MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Text(
                                        text = SchedStr.currentTag(),
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = CommonStr.ok(),
                        onClick = { onTableSelected(selectedSchedule) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
