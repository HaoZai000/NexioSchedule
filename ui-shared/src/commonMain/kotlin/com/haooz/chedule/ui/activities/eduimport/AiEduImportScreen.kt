/**
 * AI 教务导入：沿用教务网页导入页的外壳（顶栏、WebView、底部悬浮条），在其上叠一层 AI 对话。
 *
 * 对话必须留在本页内、以底部浮层形式出现：工具都在这台 WebView 上执行，
 * 页面一旦离开组合，WebView 就被销毁。
 */
package com.haooz.chedule.ui.activities.eduimport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.SchoolRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.ai.AiEduPreviewHolder
import com.haooz.chedule.ui.ai.EduAgentSession
import com.haooz.chedule.ui.ai.EduAgentUiState
import com.haooz.chedule.ui.ai.EduChatItem
import com.haooz.chedule.ui.ai.StreamingRevealText
import com.haooz.chedule.ui.ai.bottomAnchoredClip
import com.haooz.chedule.ui.ai.streamingWindowStart
import com.haooz.chedule.ui.ai.stripMarkdownEmphasis
import com.haooz.nexioui.iconbutton.IconButton
import com.haooz.nexioui.cardexpand.CardExpand
import com.haooz.chedule.ui.components.PageBlurBaseHeight
import com.haooz.chedule.ui.components.softGlassBackdrop
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.nexioui.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentActionEffect
import com.haooz.chedule.ui.utils.NativeDocumentLeadingActionEffect
import com.haooz.chedule.ui.utils.ExtractedShareTools
import com.haooz.chedule.ui.utils.NativeDocumentSystemHeaderEffect
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import com.haooz.nexioui.window.ScreenCornerRadiiPx
import com.haooz.nexioui.window.toShape
import com.haooz.chedule.ui.web.EduCourseJson
import com.haooz.chedule.ui.web.EduImportSelection
import com.haooz.chedule.ui.web.EduNetLogStore
import com.haooz.chedule.ui.web.WebHost
import com.haooz.chedule.ui.web.WebHostConfig
import com.haooz.chedule.ui.web.WebHostEvents
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.glass.BarFill
import com.haooz.softglass.glass.BrightLargeTone
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.CustomizeFill
import com.haooz.softglass.glass.SoftGlassStyle
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.haooz.nexioui.button.ButtonDefaults
import androidx.compose.material3.Icon
import com.haooz.nexioui.textfield.TextField
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.basic.ArrowRight
import com.haooz.nexioui.icon.extended.Close
import com.haooz.nexioui.icon.extended.Refresh
import com.haooz.nexioui.topappbar.CollapsibleTopAppBar
import com.haooz.nexioui.progressiveblurtopbar.ProgressiveBlurTopBar
import com.haooz.nexioui.topappbar.rememberSharedScrollBehavior
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.overscroll.overScrollVertical
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.material3.Text as PlainText

private const val PREFS_AI_EDU = "ai_edu_import_prefs"
private const val KEY_LAST_URL = "last_url"

/** 右上角「历史」图标：1024×1024 的时钟 SVG，路径直接交给 [PathParser] 解析成 [ImageVector]。 */
private val AiEduHistoryIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AiEduHistory",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f,
    ).addPath(
        pathData = PathParser().parsePathString(AI_EDU_HISTORY_PATH).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

private const val AI_EDU_HISTORY_PATH =
    "M512 938.666667c235.648 0 426.666667-191.018667 426.666667-426.666667" +
        "S747.648 85.333333 512 85.333333 85.333333 276.352 85.333333 512" +
        "s191.018667 426.666667 426.666667 426.666667z" +
        "m0 85.333333C229.248 1024 0 794.752 0 512S229.248 0 512 0" +
        "s512 229.248 512 512-229.248 512-512 512z" +
        "m42.666667-486.869333V298.538667C554.666667 275.328 535.552 256 512 256" +
        "c-23.722667 0-42.666667 19.029333-42.666667 42.538667v256.256" +
        "a41.984 41.984 0 0 0 12.202667 29.866666l121.258667 121.258667" +
        "a42.368 42.368 0 0 0 60.032-0.298667 42.666667 42.666667 0 0 0 0.298666-60.032" +
        "L554.666667 537.130667z"

/** 对话浮层占屏高比例：上面露出的一截网页就是「悬浮小窗」。 */
private const val ChatSheetFraction = 0.64f

/** 悬浮输入条的高度估算：列表要靠它留出底部空白，否则最后一条会被压在输入框下面。 */
private val ComposerHeight = 52.dp
private val ComposerButtonSize = 48.dp

/** 对话区统一左右留白：气泡、过程段、输入条、状态条共用一条基准线。 */
private val ChatHPadding = 12.dp

/** 顶部悬浮头 + 渐变遮罩的高度。 */
private val ChatTopInset = 64.dp

/** 对话浮层离屏幕左/右/下的距离；底部圆角要跟着收窄同样的量才能与屏幕同心。 */
private val ChatSheetInset = 4.dp

/** 流式思考的展示高度：约 4 行，超出部分靠自动贴底滚上来。 */
private val StreamingThinkingMaxHeight = 78.dp

private enum class Stage { Web, Chat }

@Composable
fun AiEduImportScreen(
    webHostFactory: @Composable (WebHostConfig, WebHostEvents) -> WebHost,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPreview: () -> Unit,
    /** 地址命中本地适配后，用户选择走现成适配器导入 */
    onOpenAdapterImport: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val nativeChrome = LocalNativeDocumentChrome.current
    val nativeBarInset = (nativeChrome?.barInset ?: 0.0).dp
    val showNavigationActions = nativeChrome == null
    // ⚠ 必须在组合期取好：SideEffect 的 lambda 不是 @Composable，读不了 MiuixTheme
    val chromeTint = NexioTheme.colorScheme.primary.toArgb().toLong()

    var stage by remember { mutableStateOf(Stage.Web) }
    var urlInput by remember {
        mutableStateOf(AppStorage.store(PREFS_AI_EDU).getStringOrNull(KEY_LAST_URL).orEmpty())
    }
    var openedUrl by remember { mutableStateOf<String?>(null) }
    // 本地已有匹配适配时，先让用户选「用现成适配」还是「继续 AI」；两个状态一起出现
    val localSchools = remember { SchoolRepository().getSchools() }
    var matchedSchool by remember { mutableStateOf<SchoolData?>(null) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    // 顶栏跟着网页标题走（同 WebImportScreen）：进去以后这条顶栏就是「你在看哪一页」
    var webPageTitle by remember(AppLanguage.resolved) { mutableStateOf(CommonStr.loading()) }
    // 网页顶部取色：顶栏文字/图标按页面背景反色，不跟应用深浅主题（同 WebImportScreen）
    var webTopColor by remember { mutableStateOf<Color?>(null) }
    val themeBarColor = NexioTheme.colorScheme.onSurface
    val webBarContentColor = when {
        // 还没进网页或还没采到色：顶栏压在应用底色上，跟主题走
        openedUrl == null || webTopColor == null -> themeBarColor
        else -> {
            val color = webTopColor!!
            val lum = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
            if (lum >= 0.55f) Color.Black else Color.White
        }
    }

    val session = remember { EduAgentSession() }
    val state by session.state.collectAsState()
    // 拉着同一段对话：用户收起浮层去网页里登录 / 翻课表后，底栏给他回来的入口
    val hasConversation = state.items.isNotEmpty()
    // WebViewScreen 内部 remember 出来的宿主，通过 onHostReady 交过来 —— agent 的工具都在它上面跑
    var host by remember { mutableStateOf<WebHost?>(null) }

    val liquidGlassBackdrop: LayerBackdrop = rememberLayerBackdrop()
    val webViewScrollBehavior = rememberSharedScrollBehavior()

    SideEffect {
        nativeChrome?.title = ImportStr.aiEduImportTitle()
        nativeChrome?.tintArgb = chromeTint
    }
    NativeDocumentLeadingActionEffect(
        if (nativeChrome != null) {
            NativeDocumentAction(symbol = "xmark", label = CommonStr.close(), onClick = onBack)
        } else null
    )
    NativeDocumentActionEffect(
        if (openedUrl == null) {
            // 还没进网页时没有可刷新的东西，右上角换成「历史」
            NativeDocumentAction(
                symbol = "clock.arrow.circlepath",
                label = ImportStr.aiEduHistory(),
                onClick = onOpenHistory,
            )
        } else {
            NativeDocumentAction(
                symbol = "arrow.clockwise",
                label = ImportStr.refresh(),
                onClick = { host?.reload() },
            )
        }
    )

    LaunchedEffect(openedUrl) {
        val url = openedUrl ?: return@LaunchedEffect
        // 换了一个教务系统：旧学校的请求记录与对话都不能留给下一次分析
        EduNetLogStore.clear()
        session.reset()
        AppStorage.store(PREFS_AI_EDU).edit { putString(KEY_LAST_URL, url) }
    }

    val startAnalyze: () -> Unit = {
        val target = host
        val url = openedUrl
        if (url.isNullOrBlank() || target == null) {
            showToast(ImportStr.aiEduNeedUrl())
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            stage = Stage.Chat
            session.reset()
            scope.launch {
                session.analyze(
                    host = target,
                    entryUrl = url,
                    runtimeHint = agentRuntimeHint(
                        netCount = EduNetLogStore.snapshot().count { it.kind != "document" },
                    ),
                )
            }
        }
    }

    NativeDocumentSystemHeaderEffect(nativeChrome != null)

    Scaffold(
        modifier = Modifier.consumeWindowInsets(PaddingValues(top = nativeBarInset))
    ) { paddingValues ->
        // 顶栏交给系统时没有 Compose 顶栏，折叠高度按 0 算
        val topBarHeightDp = if (nativeChrome != null) {
            0.dp
        } else {
            with(LocalDensity.current) { webViewScrollBehavior.currentHeightPx.toDp() }
        }
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val blurHeight = PageBlurBaseHeight + (statusBarHeight - nativeBarInset).coerceAtLeast(0.dp)
        val contentTopInset = paddingValues.calculateTopPadding() + nativeBarInset
        // 顶栏与底部悬浮条共用：录的是 WebView 的离屏层
        val webContentBackdrop = rememberLayerBackdrop()

        Box(modifier = Modifier.fillMaxSize()) {
            WebViewScreen(
                schoolName = ImportStr.aiEduImportTitle(),
                // 没有适配脚本，两个值都是空的
                resourceFolder = "",
                adapterId = "",
                importUrl = openedUrl,
                assetJsPath = null,
                // AI 分析要先看页面自己打过哪些接口：记录器从首个文档的 document-start 就挂上
                netRecorderEnabled = true,
                webContentBackdrop = webContentBackdrop,
                contentTopPadding = (contentTopInset + topBarHeightDp + 12.dp).coerceAtLeast(0.dp),
                onBack = onBack,
                // AI 这条路不接原生桥：课程必须过本地预览，直连落库就绕过确认了
                onImportComplete = {},
                onTaskCompleted = {},
                onPageTitleChanged = { webPageTitle = it },
                onTopColorChanged = { webTopColor = it },
                onHostReady = { host = it },
                webHostFactory = webHostFactory,
                dockTitle = ImportStr.aiEduImportTitle(),
                dockSubtitle = if (hasConversation) ImportStr.aiEduResumeHint() else ImportStr.aiEduDockHint(),
                // 已经聊过就变成「继续对话」：用户登完录 / 翻到课表页后能回到同一段上下文
                primaryActionText = if (hasConversation) ImportStr.aiEduResumeChat() else ImportStr.aiEduStart(),
                primaryActionEnabled = openedUrl != null,
                onPrimaryAction = {
                    if (hasConversation) {
                        stage = Stage.Chat
                    } else {
                        startAnalyze()
                    }
                },
            )

            // ── 对话：底部浮层，上方露出网页 ──
            AnimatedVisibility(
                visible = stage == Stage.Chat,
                enter = slideInVertically(
                    animationSpec = tween(
                        durationMillis = 480,
                        easing = CubicBezierEasing(0.34f, 1.12f, 0.3f, 1f),
                    )
                ) { it },
                exit = slideOutVertically(
                    animationSpec = tween(320, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f))
                ) { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = ChatSheetInset, end = ChatSheetInset, bottom = ChatSheetInset),
            ) {
                ChatSheet(
                    state = state,
                    onStop = { session.requestStop() },
                    onSend = { text ->
                        val target = host
                        if (target != null) {
                            if (!session.enqueue(text)) {
                                scope.launch { session.continueWith(target, text) }
                            }
                        }
                    },
                    onPreview = {
                        val draft = state.draft
                        if (draft != null) {
                            // 严格解析：缺星期/节次/周次的课跳过、重复课合并，都由预览页如实标注
                            val parsed = runCatching { EduCourseJson().parseDraft(draft.coursesJson, null) }
                                .getOrElse {
                                    showToast(it.message ?: ImportStr.aiEduParseFailed())
                                    null
                                }
                            if (parsed != null && parsed.courses.isNotEmpty()) {
                                AiEduPreviewHolder.set(
                                    draft = draft,
                                    courses = parsed.courses,
                                    mergedDuplicates = parsed.merged,
                                    skippedNames = parsed.skipped,
                                )
                                onOpenPreview()
                            } else if (parsed != null) {
                                // 一门都留不下：必须说清楚为什么，不能静默无反应
                                showToast(
                                    ImportStr.aiEduDraftNothingUsable(parsed.skipped.size),
                                    longDuration = true,
                                )
                            }
                        }
                    },
                    onCollapse = { stage = Stage.Web },
                )
            }

            // ── 地址没填时：先让人填地址，别让他对着一片白页面猜 ──
            if (openedUrl == null) {
                UrlEntryOverlay(
                    urlInput = urlInput,
                    onUrlChange = { urlInput = it },
                    onOpen = {
                        val normalized = normalizeEduUrl(urlInput)
                        if (normalized == null) {
                            showToast(ImportStr.aiEduNeedUrl())
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            // 本地已有这个站点的适配时先弹出来：能走现成脚本就不必让模型重写
                            val matched = findLocalSchoolForUrl(normalized, localSchools)
                            if (matched == null) {
                                openedUrl = normalized
                            } else {
                                pendingUrl = normalized
                                matchedSchool = matched
                            }
                        }
                    },
                )
            }

            AdapterSelectionBottomSheet(
                show = matchedSchool != null,
                school = matchedSchool,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = {
                    matchedSchool = null
                    pendingUrl = null
                },
                onAdapterSelected = { school, adapter ->
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    matchedSchool = null
                    pendingUrl = null
                    EduImportSelection.set(school, adapter)
                    onOpenAdapterImport()
                },
                footerContent = {
                    FlatTextButton(
                        text = ImportStr.aiEduContinue(),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            val url = pendingUrl
                            matchedSchool = null
                            pendingUrl = null
                            if (url != null) openedUrl = url
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )

            // iOS 顶栏归系统，Compose 渐变模糊顶栏整条不画（同 WebImportScreen）
            if (nativeChrome == null) ProgressiveBlurTopBar(
                backdrop = liquidGlassBackdrop,
                height = blurHeight,
                blurAlpha = 0f,
            ) {
                CollapsibleTopAppBar(
                    title = if (openedUrl == null) ImportStr.aiEduImportTitle() else webPageTitle,
                    // 折叠标题：本页内容是网页 + 浮层，展开大标题只会把网页挤下去
                    showLargeTitle = false,
                    showShadow = false,
                    titleColor = webBarContentColor,
                    modifier = Modifier,
                    scrollBehavior = webViewScrollBehavior,
                    contentPadding = {},
                    startAction = if (showNavigationActions) {
                        { backdropAlpha, shadowAlpha ->
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    onBack()
                                },
                                backdrop = liquidGlassBackdrop,
                                icon = NexioIcons.Close,
                                iconTint = webBarContentColor.copy(alpha = 0.85f),
                                contentDescription = CommonStr.close(),
                                performHapticFeedback = false,
                                iconSize = 22.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        }
                    } else null,
                    endAction = if (showNavigationActions) {
                        { backdropAlpha, shadowAlpha ->
                            if (openedUrl == null) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        onOpenHistory()
                                    },
                                    backdrop = liquidGlassBackdrop,
                                    icon = AiEduHistoryIcon,
                                    iconTint = webBarContentColor.copy(alpha = 0.85f),
                                    contentDescription = ImportStr.aiEduHistory(),
                                    performHapticFeedback = false,
                                    iconSize = 22.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        host?.reload()
                                    },
                                    backdrop = liquidGlassBackdrop,
                                    icon = NexioIcons.Refresh,
                                    iconTint = webBarContentColor.copy(alpha = 0.85f),
                                    contentDescription = ImportStr.refresh(),
                                    performHapticFeedback = false,
                                    iconSize = 24.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            }
                        }
                    } else null,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  地址入口
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun UrlEntryOverlay(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexioTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
            .padding(top = 120.dp)
    ) {
        Text(
            text = ImportStr.aiEduUrlHint(),
            style = NexioTheme.textStyles.body2,
            color = NexioTheme.colorScheme.onSurfaceVariantActions,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            value = urlInput,
            onValueChange = onUrlChange,
            minLines = 4,
            maxLines = 4,
            label = ImportStr.aiEduUrlLabel(),
            useLabelAsPlaceholder = true,
        )
        Spacer(Modifier.height(12.dp))
        FlatTextButton(
            text = ImportStr.aiEduOpen(),
            onClick = onOpen,
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  对话浮层
// ════════════════════════════════════════════════════════════════════════

/**
 * 对话浮层。外观与 `BlurBottomSheet` 一致，但不复用其交互：浮层必须常驻，
 * 关闭等同中断 agent。
 */
@Composable
private fun ChatSheet(
    state: EduAgentUiState,
    onStop: () -> Unit,
    onSend: (String) -> Unit,
    onPreview: () -> Unit,
    onCollapse: () -> Unit,
) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val bubbleMax = (screenWidthDp() * 0.8f).dp.coerceAtMost(520.dp)
    // 顶部沿用 BlurBottomSheet 的 36dp；底部两角贴合屏幕物理圆角
    val density = LocalDensity.current
    val screenCorners = rememberScreenCornerRadiiPx()
    val sheetShape = remember(screenCorners, density) {
        val topPx = with(density) { 36.dp.toPx() }
        // 浮层离屏幕边缘 4dp，底部圆角同步减掉才和屏幕圆角同心
        val bottomInsetPx = with(density) { ChatSheetInset.toPx() }
        ScreenCornerRadiiPx(
            topLeft = topPx,
            topRight = topPx,
            bottomRight = (screenCorners.bottomRight - bottomInsetPx).coerceAtLeast(0f),
            bottomLeft = (screenCorners.bottomLeft - bottomInsetPx).coerceAtLeast(0f),
        ).toShape(density)
    }

    // 输入条玻璃的采样源：浮层**内部**的内容（列表 + 底色）。
    // ⚠ 生产者只包列表，Composer 在它外面 —— 玻璃节点不能采样自己所在的层（见 topics/glass.md）。
    val sheetBackdrop = rememberLayerBackdrop()
    val glassOn = AppMaterialSettings.glassEffectsEnabled()
    val glassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = isAppDarkTheme(),
        // 输入条是「贴底 bar」类：BarFill + 大控件基调
        fill = BarFill,
        tone = BrightLargeTone,
    )

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.running) {
        if (state.running) {
            while (true) {
                tick = Clock.System.now().toEpochMilliseconds()
                delay(250.milliseconds)
            }
        }
    }
    val elapsedMs = if (state.running && state.startedAtMs > 0) {
        (tick.takeIf { it > 0 } ?: state.startedAtMs) - state.startedAtMs
    } else {
        state.elapsedMs
    }

    // 连续的思考 / 工具步骤收成一段「过程」，气泡才不会被这些细碎行割裂
    val rows = remember(state.items) { state.items.toChatRows() }
    // 排队提示 / 错误行 / 预览入口都挂在列表尾部，滚动到底时要一起算进去
    val trailingRows =
        (if (state.queued.isNotEmpty()) 1 else 0) +
            (if (state.error != null) 1 else 0) +
            (if (state.draft != null) 1 else 0)
    LaunchedEffect(rows.size, state.items.size, state.running, state.queued.size, state.draft) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex + trailingRows)
    }
    // 流式期间每帧贴底：文字按揭示进度逐字长高，只在 delta 到达时滚一次会落后一截。
    // 用户没主动上滑就保持跟随；滑到最底部再恢复。
    var followStream by remember { mutableStateOf(true) }
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(state.running) { if (state.running) followStream = true }
    LaunchedEffect(dragging) { if (dragging) followStream = false }
    LaunchedEffect(state.running) {
        if (!state.running) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (!followStream) {
                // 已经滑回底部（没有可继续滚的内容）就恢复跟随
                if (!listState.canScrollForward) followStream = true
                continue
            }
            if (listState.isScrollInProgress) continue
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: continue
            val overflow =
                last.offset + last.size + info.afterContentPadding - info.viewportEndOffset
            if (overflow > 0) listState.scrollBy(overflow.toFloat())
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(ChatSheetFraction)
            .clip(sheetShape)
            .background(NexioTheme.colorScheme.surface)
            .border(0.5.dp, NexioTheme.colorScheme.outline, sheetShape)
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(sheetBackdrop)
                .background(NexioTheme.colorScheme.surface)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().overScrollVertical(),
                // 顶部胶囊 / 收起按钮 / 扶手都是悬浮的，不参与布局：
                // 靠留白让开它们和渐隐带，内容滑上去时由渐变淡出，不做硬裁断
                contentPadding = PaddingValues(
                    start = ChatHPadding,
                    end = ChatHPadding,
                    top = ChatTopInset,
                    bottom = ComposerHeight + navBottom + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    count = rows.size,
                    // ⚠ 不能用 atMs 当 key：排队消息会在同一毫秒批量插入，key 撞车 LazyColumn 抛异常
                    key = { index -> index },
                ) { index ->
                    when (val row = rows[index]) {
                        is ChatRow.Message -> when (val item = row.item) {
                            is EduChatItem.User -> UserBubble(item.text, bubbleMax)
                            is EduChatItem.Assistant -> AssistantBubble(item.text, bubbleMax, item.streaming)
                            else -> Unit
                        }
                        is ChatRow.Process -> ProcessBlock(row.items)
                    }
                }
                if (state.queued.isNotEmpty()) {
                    item(key = "queued") {
                        PlainText(
                            text = ImportStr.aiEduQueuedNote(state.queued.size),
                            style = NexioTheme.textStyles.body2,
                            color = NexioTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
                state.error?.let { error ->
                    item(key = "error") {
                        PlainText(
                            text = error,
                            style = NexioTheme.textStyles.body2,
                            color = NexioTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
                // 预览入口放在滚动区尾部：贴底会让它和输入条抢同一块区域，滚到底就能点到
                if (state.draft != null) {
                    item(key = "preview") {
                        FlatTextButton(
                            text = ImportStr.aiEduPreviewActionN(state.draft.courseCount),
                            onClick = onPreview,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }
            }

            // 顶部渐变遮罩（照 BlurBottomSheet）：常驻、固定 ChatTopInset、多段色标，向下淡出到透明
            val scrimColor = NexioTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatTopInset)
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                // 0 ~ 0.75 不透明，最后 1/4 从 1f 淡到全透明
                                0.0f to scrimColor,
                                0.75f to scrimColor,
                                1.0f to scrimColor.copy(alpha = 0f),
                            ),
                        )
                    )
            )
            // 顶部悬浮头：扶手 + 状态胶囊 + 收起按钮，固定 ChatTopInset、zIndex 置顶，不占滚动空间
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatTopInset)
                    .align(Alignment.TopCenter)
                    .zIndex(2f),
            ) {
                SheetDragHandle()
                StatusRow(
                    state = state,
                    elapsedMs = elapsedMs,
                    onStop = onStop,
                    onCollapse = onCollapse,
                )
            }
        }

        Composer(
            value = input,
            onValueChange = { input = it },
            placeholder = if (state.running) ImportStr.aiEduSteerHint() else ImportStr.aiEduReplyHint(),
            running = state.running,
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    onSend(text)
                    input = ""
                }
            },
            backdrop = sheetBackdrop,
            glassOn = glassOn,
            glassStyle = glassStyle,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = ChatHPadding, end = ChatHPadding, bottom = navBottom + 14.dp),
        )
    }
}

/** 列表行：普通消息单独成行，连续的思考 / 工具步骤并成一段过程。 */
private sealed interface ChatRow {
    data class Message(val item: EduChatItem) : ChatRow
    data class Process(val items: List<EduChatItem>) : ChatRow
}

private fun List<EduChatItem>.toChatRows(): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var index = 0
    while (index < size) {
        when (val item = this[index]) {
            is EduChatItem.User, is EduChatItem.Assistant -> {
                rows += ChatRow.Message(item)
                index++
            }
            else -> {
                val start = index
                while (index < size && this[index] !is EduChatItem.User && this[index] !is EduChatItem.Assistant) {
                    index++
                }
                // 一个字都没有的思考块本来就不显示，别让它的留白把卡片撑出一段空档
                val process = subList(start, index).filterNot {
                    it is EduChatItem.Thinking && it.empty && !it.streaming
                }
                if (process.isNotEmpty()) rows += ChatRow.Process(process)
            }
        }
    }
    return rows
}

/**
 * 过程段：一轮里的思考与工具调用按发生顺序排在一起。
 *
 * 不加卡片底板 —— 这些是过程信息，不该和答案气泡抢视觉层级；
 * 只靠统一的间距把连续的步骤收成一组。
 */
@Composable
private fun ProcessBlock(items: List<EduChatItem>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            when (item) {
                is EduChatItem.Thinking -> ThinkingBlock(item)
                is EduChatItem.Step -> StepBlock(item)
                else -> Unit
            }
        }
    }
}

/** 抓手：22dp 容器里那条 45×4dp 的 20% 灰条。悬浮在顶部，不占滚动空间。 */
@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().height(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(45.dp)
                .height(4.dp)
                .clip(ContinuousRoundedRectangle(2.dp))
                .background(NexioTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f))
        )
    }
}

/**
 * 状态条。
 *
 * ⚠ 这里用 `PlainText`（material3 原生）而不是项目的 `Text`：项目的 Text 包了 TextMotionBox，
 * 秒数每 250ms 变一次会不停触发模糊聚焦过渡，整行字看着一直在抖。
 */
@Composable
private fun StatusRow(
    state: EduAgentUiState,
    elapsedMs: Long,
    onStop: () -> Unit,
    onCollapse: () -> Unit,
) {
    val pillShape = remember { ContinuousCapsule() }
    val accentColor = when {
        state.running -> NexioTheme.colorScheme.primary
        state.error != null -> NexioTheme.colorScheme.error
        state.draft != null -> NexioTheme.colorScheme.primary
        else -> NexioTheme.colorScheme.onSurfaceVariantActions
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ChatHPadding + 4.dp, end = ChatHPadding, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(accentColor.copy(alpha = 0.12f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accentColor, CircleShape)
            )
            Spacer(Modifier.size(8.dp))
            PlainText(
                text = when {
                    state.running -> ImportStr.aiEduStatusRunning(formatDurationPlain(elapsedMs))
                    state.draft != null -> ImportStr.aiEduStatusDone(formatDurationPlain(elapsedMs), state.turn)
                    state.error != null -> ImportStr.aiEduFailed()
                    else -> ImportStr.aiEduStatusIdle(formatDurationPlain(elapsedMs))
                },
                style = NexioTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = accentColor,
            )
        }
        Spacer(Modifier.weight(1f))
        PlainText(
            text = if (state.running) ImportStr.aiEduStop() else ImportStr.aiEduCollapseSheet(),
            style = NexioTheme.textStyles.body2,
            color = if (state.running) NexioTheme.colorScheme.error else NexioTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { if (state.running) onStop() else onCollapse() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * 输入条：胶囊输入框 + 圆形箭头按钮，悬浮在浮层底部（不占布局，列表用 contentPadding 让位）。
 */
@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    running: Boolean,
    onSend: () -> Unit,
    /** 玻璃采样源（浮层内部内容） */
    backdrop: GlassBackdrop,
    glassOn: Boolean,
    glassStyle: SoftGlassStyle,
    modifier: Modifier = Modifier,
) {
    val capsule = remember { ContinuousCapsule() }
    val capsuleRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = NexioTheme.textStyles.body1.copy(color = NexioTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(NexioTheme.colorScheme.primary),
            maxLines = 1,
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(capsule)
                        .then(
                            if (glassOn) {
                                Modifier.softGlassBackdrop(
                                    backdrop = backdrop,
                                    shape = capsule,
                                    style = glassStyle,
                                    radiusPx = capsuleRadiusPx,
                                )
                            } else {
                                Modifier.background(NexioTheme.colorScheme.surfaceVariant)
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    if (value.isEmpty()) {
                        PlainText(
                            text = placeholder,
                            style = NexioTheme.textStyles.body1,
                            color = NexioTheme.colorScheme.onSurfaceVariantActions,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.size(10.dp))
        val enabled = value.isNotBlank()
        // 玻璃材质常驻：可用时填主色（白箭头），不可用时填灰，而不是把整块材质降透明
        val buttonGlassStyle = AppMaterialSettings.chromeGlassStyle(
            base = ScheduleSoftGlassStyle,
            isDark = isAppDarkTheme(),
            fill = if (enabled) {
                CustomizeFill(NexioTheme.colorScheme.primary)
            } else {
                CustomizeFill(NexioTheme.colorScheme.surfaceVariant)
            },
            tone = BrightTone,
        )
        Box(
            modifier = Modifier
                .size(ComposerButtonSize)
                .clip(CircleShape)
                .then(
                    if (glassOn) {
                        Modifier.softGlassBackdrop(
                            backdrop = backdrop,
                            shape = CircleShape,
                            style = buttonGlassStyle,
                            radiusPx = with(LocalDensity.current) { (ComposerButtonSize / 2).toPx() },
                        )
                    } else {
                        // 无材质档退成实色，禁用态同样只置灰
                        Modifier.background(
                            if (enabled) NexioTheme.colorScheme.primary
                            else NexioTheme.colorScheme.surfaceVariant
                        )
                    }
                )
                .clickable(enabled = enabled) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = NexioIcons.Basic.ArrowRight,
                contentDescription = if (running) ImportStr.aiEduSteer() else ImportStr.aiEduSend(),
                modifier = Modifier
                    .size(20.dp)
                    // ArrowRight 转 -90° 就是向上的发送箭头，省一个图标资源
                    .graphicsLayer { rotationZ = -90f },
                tint = if (enabled) NexioTheme.colorScheme.onPrimary else NexioTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
private fun UserBubble(text: String, maxWidth: Dp) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        PlainText(
            text = text,
            style = NexioTheme.textStyles.body1,
            color = NexioTheme.colorScheme.onPrimary,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(ContinuousRoundedRectangle(18.dp))
                .background(NexioTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun AssistantBubble(text: String, maxWidth: Dp, streaming: Boolean) {
    // 模型很爱回 markdown 加粗，这里只做最轻的清洗：留着星号比渲染错更难看
    val clean = text.stripMarkdownEmphasis()
    val style = LocalTextStyle.current.merge(NexioTheme.textStyles.body1)
        .copy(color = NexioTheme.colorScheme.onSurface)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(ContinuousRoundedRectangle(18.dp))
                .background(NexioTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (streaming) {
                StreamingRevealText(text = clean, style = style)
            } else {
                PlainText(text = clean, style = style)
            }
        }
    }
}

/**
 * 思考块：过程段里的一段。收起时一行摘要，展开后最高 200dp、超出滚动。
 *
 * 展开内容交给 [CardExpand]（全站同一条高度 spring + 淡入 + 模糊），箭头用同一条时间线旋转，
 * 头部点击不挂水波纹 —— 这里只是「展开详情」，不是按钮。
 */
@Composable
private fun ThinkingBlock(item: EduChatItem.Thinking) {
    // streaming 中即使还没有字也要显示（否则用户看到的是空白等待）
    if (item.empty && !item.streaming) return
    var expanded by remember(item.atMs) { mutableStateOf(false) }
    // 是否真的超过一行：只有超了才给箭头与点击，一行就说完的思考不该假装能展开。
    // 用实测布局判断（hasVisualOverflow），比按字符数估准。
    var overflows by remember(item.atMs) { mutableStateOf(false) }
    val expandable = overflows || expanded
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(240, easing = OobeQuartOutEasing),
        label = "thinkingChevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(8.dp))
                .then(
                    if (expandable && !item.streaming) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { expanded = !expanded }
                    } else Modifier
                )
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlainText(
                text = if (item.streaming) {
                    ImportStr.aiEduThinkingStreaming()
                } else {
                    ImportStr.aiEduThinkingBlock(formatDuration((item.seconds * 1000).toLong()))
                },
                style = NexioTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = NexioTheme.colorScheme.onSurfaceVariantActions,
            )
            if (item.streaming) {
                Spacer(Modifier.size(6.dp))
                PulsingDot()
            } else if (expandable) {
                Spacer(Modifier.size(4.dp))
                Icon(
                    imageVector = NexioIcons.Basic.ArrowRight,
                    contentDescription = if (expanded) ImportStr.aiEduCollapse() else ImportStr.aiEduExpand(),
                    modifier = Modifier
                        .size(13.dp)
                        // 未展开朝右，展开转 90° 朝下
                        .graphicsLayer { rotationZ = chevronRotation },
                    tint = NexioTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
        if (item.streaming) {
            // 思考正文一直在往后追加：只排版最后一段，底对齐裁掉上面旧的内容，
            // 新写的行不断把旧行顶出去，就是「一直在滚」；文字本身仍然逐字淡入。
            val windowStart = remember(item.text) { item.text.streamingWindowStart() }
            val window = remember(item.text, windowStart) { item.text.substring(windowStart) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = StreamingThinkingMaxHeight)
                    .clipToBounds(),
            ) {
                StreamingRevealText(
                    text = window,
                    style = NexioTheme.textStyles.body2.copy(
                        fontSize = 13.sp,
                        color = NexioTheme.colorScheme.onSurfaceVariantActions,
                    ),
                    revealBase = windowStart,
                    modifier = Modifier.fillMaxWidth().bottomAnchoredClip(),
                )
            }
        } else {
            AnimatedVisibility(
                visible = !expanded,
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(100)),
            ) {
                ThinkingText(
                    text = item.text,
                    maxLines = 1,
                    onTextLayout = { layout ->
                        if (!expanded) overflows = layout.hasVisualOverflow
                    },
                )
            }
            CardExpand(visible = expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ThinkingText(text = item.text)
                }
            }
        }
    }
}

@Composable
private fun ThinkingText(
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    PlainText(
        text = text,
        style = NexioTheme.textStyles.body2,
        fontSize = 13.sp,
        color = NexioTheme.colorScheme.onSurfaceVariantActions,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = onTextLayout ?: {},
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

/** 工具步骤：只留一行状态点 + 摘要标题，回执与耗时都不展示。 */
@Composable
private fun StepBlock(item: EduChatItem.Step) {
    val statusColor = if (item.failed) {
        NexioTheme.colorScheme.error
    } else {
        NexioTheme.colorScheme.onSurfaceVariantActions
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.running) {
            PulsingDot()
        } else {
            Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
        }
        Spacer(Modifier.size(8.dp))
        PlainText(
            text = item.title,
            style = NexioTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = NexioTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 运行中的呼吸点：同一时刻只有一处，无限动画开销可忽略。 */
@Composable
private fun PulsingDot(size: Dp = 5.dp) {
    val transition = rememberInfiniteTransition(label = "runningDot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "runningDotAlpha",
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .background(NexioTheme.colorScheme.primary, CircleShape)
    )
}

// ════════════════════════════════════════════════════════════════════════
//  工具函数
// ════════════════════════════════════════════════════════════════════════

/** 交给模型的运行环境：只给真正用得上的节次换算规则与页面请求数。 */
private fun agentRuntimeHint(netCount: Int): String = buildString {
    append("节次说明：startSection/endSection 用学校课表里的实际连续节次序号（从 1 开始）；")
    append("学校按「上午/下午第 N 节」标注时，按该校一天内的顺序换算。")
    append("\n页面网络记录：")
    if (netCount > 0) {
        append("已有 $netCount 条请求。先 NET_LOG list 看接口，不要问用户要接口地址。")
    } else {
        append("当前为空。先 READ_PAGE；仍为空再 RELOAD_PAGE 或请用户点一次「课表查询」。")
    }
}

/** 地址归一：省略协议时补 https（教务系统几乎都是 https）。 */
private fun normalizeEduUrl(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val withScheme = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
    return withScheme.takeIf { it.contains('.') }
}

/**
 * 本地适配匹配。
 *
 * 先按「host + path」精确匹配，再退到同 host：同一所学校常有本科 / 研究生两套入口，
 * 精确命中能区分它们；用户只填到域名时也能用同 host 的适配兜住。
 */
private fun findLocalSchoolForUrl(rawUrl: String, schools: List<SchoolData>): SchoolData? {
    val site = normalizedSite(rawUrl) ?: return null
    val host = site.substringBefore('/')

    fun SchoolData.hasAdapter(predicate: (String?) -> Boolean): Boolean =
        adapters.any { it.adapterId !in ExtractedShareTools.ADAPTER_IDS && predicate(it.importUrl) }

    return schools.firstOrNull { school -> school.hasAdapter { normalizedSite(it) == site } }
        ?: schools.firstOrNull { school ->
            school.hasAdapter { normalizedSite(it)?.substringBefore('/') == host }
        }
}

/** host/path 归一：忽略协议、www.、端口、查询参数与末尾斜杠。 */
private fun normalizedSite(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val withScheme = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
    val rest = withScheme.substringAfter("://", "")
    if (rest.isEmpty()) return null
    val hostPath = rest.substringBefore('#').substringBefore('?')
    val host = hostPath.substringBefore('/').substringBefore(':').lowercase().removePrefix("www.")
    if (!host.contains('.')) return null
    val path = hostPath.substringAfter('/', "").trim('/')
    return if (path.isEmpty()) host else "$host/$path"
}

/**
 * 状态条的计时：**不带小数**（12s / 1:05）。
 *
 * 它每 250ms 刷一次，带小数时最后一位一直在跳，看着像在抽搐；
 * 思考块里那点耗时是一次性的，所以仍用带小数的 [formatDuration]。
 */
internal fun formatDurationPlain(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + 500) / 1000
    if (seconds < 60) return "${seconds}s"
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** 12.4s / 1:05 / <0.1s —— 超过一分钟才用分秒；亚百毫秒的读取不要显示成「0.0s」像没跑。 */
internal fun formatDuration(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    if (safe < 60_000) {
        if (safe < 100) return "<0.1s"
        val tenths = (safe + 50) / 100
        return "${tenths / 10}.${tenths % 10}s"
    }
    val totalSeconds = (safe + 500) / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
