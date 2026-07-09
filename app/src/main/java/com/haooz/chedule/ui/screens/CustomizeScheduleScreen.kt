/** 自定义课表页面 - 课表外观选择 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import com.haooz.chedule.ui.utils.rememberAppStyle
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.haooz.chedule.ui.components.liquidglass.InteractiveHighlight
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun CustomizeScheduleScreen(
    snapshot: Bitmap?,
    screenCornerRadius: Float,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onCustomize: () -> Unit = {},
    onCancelCutout: () -> Unit = {},
    onPickWallpaper: () -> Unit = {},
    onCreateNewCombination: () -> Unit = {},
    combinations: List<com.haooz.chedule.data.Combination> = emptyList(),
    currentCombinationIndex: Int = 0,
    onCombinationPageChange: (Int) -> Unit = {},
    onDeleteCombination: (Long) -> Unit = {},
    exitScale: Float = 1f,
    isExiting: Boolean = false,
    isApplying: Boolean = false,
    isApplyingCustomize: Boolean = false,
    onRevertWallpaper: () -> Unit = {},
    wallpaperBitmap: Bitmap? = null,
    wallpaperOffset: Offset = Offset.Zero,
    wallpaperScale: Float = 1f,
    onWallpaperOffsetChange: (Offset) -> Unit = {},
    onWallpaperScaleChange: (Float) -> Unit = {},
    onCutoutCenterChange: (Float) -> Unit = {},
    onSheetOffsetChange: (Float) -> Unit = {},
    pendingEnterCutout: Boolean = false,
    onCutoutEntered: () -> Unit = {},
    onEffectValueChange: (Float, Float) -> Unit = { _, _ -> },
    initialCardBlurRadius: Float = 0f,
    initialCardAlpha: Float = 0.15f,
    onCustomizeValueChange: (Float, Float) -> Unit = { _, _ -> },
    initialCardHeight: Float = 54f,
    initialCardCornerRadius: Float = 8f
) {
    // ================================================================
    // 一、基础环境与尺寸计算
    // ================================================================
    val densityObj = LocalDensity.current
    val context = LocalContext.current
    val density = densityObj.density
    val screenRadiusDp = (screenCornerRadius / density).dp
    // 开洞时卡片上移的目标像素值
    val cutoutOffsetTargetPx = with(densityObj) { 10.dp.toPx() }
    // 弹窗打开时开洞区域上移的目标像素值
    val sheetOffsetTargetPx = with(densityObj) { 80.dp.toPx() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHPx = with(densityObj) { configuration.screenHeightDp.dp.toPx() }

    // 模糊支持检测（API 31+ 支持 graphicsLayer blurRadius）
    val blurSupported = isRuntimeShaderSupported()

    // 液态玻璃支持
    val appStyle = rememberAppStyle()
    val liquidGlassBackdrop = if (appStyle == "liquidglass") {
        com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    } else null
    val isLiquidGlass = appStyle == "liquidglass"
    val primaryColor = MiuixTheme.colorScheme.primary
    val isDarkForLiquid = isAppDarkTheme()
    val exitContainerColor = if (isDarkForLiquid) Color(0xFF121212).copy(0.4f) else Color(0xFFFAFAFA).copy(0.4f)
    val exitIconColor = if (isDarkForLiquid) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)

    // ================================================================
    // 二、UI 状态：加载指示 / 底部弹窗 / 删除流程
    // ================================================================

    // --- 应用加载指示器：点击"应用"后显示，进入退出动画开始时隐藏 ---
    var showApplyLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isApplyingCustomize) {
        if (isApplyingCustomize) {
            showApplyLoading = false
        }
    }

    // --- 编辑模式底部弹窗：效果 / 自定义 ---
    var showEffectSheet by remember { mutableStateOf(false) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    // 重置标志：取消编辑时自增，触发弹窗内部状态回到 initial 值
    var sheetResetKey by remember { mutableIntStateOf(0) }

    // 效果参数：卡片模糊 / 卡片透明度（随当前搭配切换、随重置键复位）
    var effectValue by remember(currentCombinationIndex, sheetResetKey) { mutableFloatStateOf(initialCardBlurRadius) }
    var cardAlphaValue by remember(currentCombinationIndex, sheetResetKey) { mutableFloatStateOf(initialCardAlpha) }
    LaunchedEffect(effectValue, cardAlphaValue) { onEffectValueChange(effectValue, cardAlphaValue) }

    // 自定义参数：卡片高度 / 卡片圆角（随当前搭配切换、随重置键复位）
    var cardHeightValue by remember(currentCombinationIndex, sheetResetKey) { mutableFloatStateOf(initialCardHeight) }
    var cardCornerRadiusValue by remember(currentCombinationIndex, sheetResetKey) { mutableFloatStateOf(initialCardCornerRadius) }
    LaunchedEffect(cardHeightValue, cardCornerRadiusValue) {
        kotlinx.coroutines.delay(16.milliseconds) // ~1帧防抖，避免拖动时每像素都触发重组
        onCustomizeValueChange(cardHeightValue, cardCornerRadiusValue)
    }

    // --- 删除流程状态 ---
    // 长按删除遮罩：记录当前处于删除态的搭配 id（null 表示无遮罩）
    var deleteTargetCombId by remember { mutableStateOf<Long?>(null) }
    // 模糊跟踪 id：进入删除态时同步设置；退出时等 alpha 动画归零后再清空，
    // 避免退出瞬间 isDeleteTarget=false 导致模糊跳变（模糊需从最大值渐变到 0）
    var blurringCombId by remember { mutableStateOf<Long?>(null) }
    // 删除遮罩淡入淡出动画
    val deleteMaskAlpha = remember { Animatable(0f) }
    LaunchedEffect(deleteTargetCombId) {
        if (deleteTargetCombId != null) {
            blurringCombId = deleteTargetCombId
            deleteMaskAlpha.snapTo(0f)
            deleteMaskAlpha.animateTo(1f, tween(300))
        } else {
            deleteMaskAlpha.animateTo(0f, tween(200))
            blurringCombId = null
        }
    }
    // 删除时的卡片消失动画：缩小到 0.6f 同时淡出
    var disappearingCombId by remember { mutableStateOf<Long?>(null) }
    val disappearScale = remember { Animatable(1f) }
    val disappearAlpha = remember { Animatable(1f) }
    // 删除进行中标志：阻止 LaunchedEffect 的二次滚动干扰删除补位动画
    var isDeleting by remember { mutableStateOf(false) }
    // "自定义"按钮淡入淡出动画（进入编辑模式时淡出，退出时淡入）
    val customizeButtonAlpha = remember { Animatable(1f) }

    // ================================================================
    // 三、Pager 状态与页面切换监听
    // ================================================================
    // pager: page 0 = "+"卡，page 1..n = combinations[0..n-1]
    val pageCount = 1 + combinations.size
    val initialPage = currentCombinationIndex + 1
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })

    // 监听页面切换，通知 MainActivity 更新当前搭配
    // isDeleting 守卫：删除补位滚动时 currentPage 会变化，但不应触发搭配切换
    LaunchedEffect(pagerState.currentPage) {
        if (isDeleting) return@LaunchedEffect
        if (pagerState.currentPage != initialPage || combinations.size > 1) {
            onCombinationPageChange(pagerState.currentPage)
        }
    }

    // 当 currentCombinationIndex 外部变化（如删除搭配后）且与当前页不一致时，滚动到目标页
    LaunchedEffect(currentCombinationIndex, pageCount) {
        if (isDeleting) return@LaunchedEffect
        val targetPage = currentCombinationIndex + 1
        if (targetPage in 0 until pageCount && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // ================================================================
    // 四、动画状态声明
    // ================================================================

    // --- 进入动画相关 ---
    val animProgress = remember { Animatable(0f) }
    var animDone by remember { mutableStateOf(false) }
    var isPageAnimating by remember { mutableStateOf(true) }
    // 标题淡入淡出动画：进入延迟淡入，退出快速淡出
    val titleFadeAnim = remember { Animatable(0f) }
    // 按钮缩放动画：进入时 1.5→1.0，退出/应用时 1.0→1.5
    val buttonScaleAnim = remember { Animatable(1.5f) }
    // 卡片间距动画：进入时 -140 → -10
    val pagerSpacing = remember { Animatable(-140f) }
    // 退出进度：卡片反向放大（无延迟）使用
    val exitProgress = remember { Animatable(0f) }

    // --- 开洞（编辑模式）动画相关 ---
    var isCutoutActive by remember { mutableStateOf(false) }
    var isCutoutAnimating by remember { mutableStateOf(false) }
    var cardHidden by remember { mutableStateOf(false) }
    val cutoutProgress = remember { Animatable(0f) }
    val cardScaleAnim = remember { Animatable(0.65f) }
    val buttonAlphaAnim = remember { Animatable(1f) }
    val titleAlphaAnim = remember { Animatable(1f) }
    val cutoutOffsetY = remember { Animatable(0f) }
    // 弹窗打开时，开洞区域与 MainActivity 同步上移的额外偏移
    val sheetOffsetY = remember { Animatable(0f) }
    // 编辑模式进入/退出进度：驱动相邻卡片放大缩小
    val cutoutEnterProgress = remember { Animatable(0f) }

    // ================================================================
    // 五、动画执行（LaunchedEffect 集中区）
    // ================================================================

    // 新建搭配后自动进入编辑模式
    LaunchedEffect(pendingEnterCutout) {
        if (pendingEnterCutout && !isCutoutActive) {
            isCutoutActive = true
            onCutoutEntered()
        }
    }

    // 进入动画：animProgress 0→1，按钮 1.5→1.0，间距 -140→-10，标题延迟淡入
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch {
                animProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
                animDone = true
                isPageAnimating = false
            }
            // 按钮缩放：1.5→1.0，与进入动画同步
            launch {
                buttonScaleAnim.animateTo(1f, tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)))
            }
            // 卡片间距：-140 → -10
            launch {
                pagerSpacing.animateTo(-10f, tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)))
            }
            // 延迟 100ms 后淡入标题，与进入动画并行
            launch {
                kotlinx.coroutines.delay(100.milliseconds)
                titleFadeAnim.animateTo(1f, tween(250, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)))
            }
        }
    }

    // 退出/应用时：按钮放大 1.0→1.5，标题快速淡出，exitProgress 0→1
    LaunchedEffect(isExiting) {
        if (isExiting) {
            exitProgress.snapTo(0f)
            kotlinx.coroutines.coroutineScope {
                launch { exitProgress.animateTo(1f, tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                launch { buttonScaleAnim.animateTo(1.5f, tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                launch { titleFadeAnim.animateTo(0f, tween(150)) }
            }
        }
    }

    // 开洞动画：进入编辑模式 / 退出编辑模式
    LaunchedEffect(isCutoutActive) {
        if (isCutoutActive) {
            isCutoutAnimating = true
            kotlinx.coroutines.coroutineScope {
                launch { cardScaleAnim.animateTo(0.75f, tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                launch { cutoutProgress.snapTo(1f) }
                // 编辑模式下顶部按钮保持可见（显示"取消"和"应用"），"自定义"按钮淡出
                launch { customizeButtonAlpha.animateTo(0f, tween(250)) }
                launch { titleAlphaAnim.animateTo(0f, tween(120)) }
                launch { cutoutOffsetY.animateTo(-cutoutOffsetTargetPx, tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                launch { cutoutEnterProgress.animateTo(1f, tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
            }
            cardHidden = true
            isCutoutAnimating = false
        } else if (animDone) {
            cardHidden = false
            // 退出开洞，恢复原始状态
            kotlinx.coroutines.coroutineScope {
                launch { cardScaleAnim.animateTo(0.65f, tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                launch { cutoutProgress.snapTo(0f) }
                launch { customizeButtonAlpha.animateTo(1f, tween(250)) }
                launch { buttonAlphaAnim.animateTo(1f, tween(250)) }
                launch { titleAlphaAnim.animateTo(1f, tween(250)) }
                launch { cutoutOffsetY.animateTo(0f, tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                launch { cutoutEnterProgress.animateTo(0f, tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                launch { pagerState.animateScrollToPage(currentCombinationIndex + 1) }
            }
        }
    }

    // 应用动画：裁剪区域完全跟随卡片放大进程（位置由 cardScaleAnim 推导）
    LaunchedEffect(isApplying) {
        if (isApplying) {
            kotlinx.coroutines.coroutineScope {
                launch { cardScaleAnim.animateTo(1f, tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                launch { buttonAlphaAnim.animateTo(0f, tween(250)) }
                launch { titleAlphaAnim.animateTo(0f, tween(250)) }
            }
        }
    }

    // 计算正确的 transformOrigin Y，传给 MainActivity 使缩放后的内容中心与裁剪区域中心对齐
    // 当 cutoutMainScale == cardScaleAnim 时（编辑模式和apply动画均满足），
    // transformOrigin Y = 0.58 + offset / (screenH * 0.35)，与 scaleProg 无关
    LaunchedEffect(cutoutOffsetY.value, screenHPx) {
        // sheetOffsetY 由 onSheetOffsetChange 单独同步，不通过 ratio 传递，避免帧延迟
        val tY = 0.58f + cutoutOffsetY.value / (screenHPx * 0.35f)
        onCutoutCenterChange(tY)
    }

    // 弹窗打开/关闭：取消/应用按钮消失，开洞区域与 MainActivity 同步上移/恢复
    val anySheetOpen = showEffectSheet || showCustomizeSheet
    LaunchedEffect(anySheetOpen) {
        if (anySheetOpen) {
            kotlinx.coroutines.coroutineScope {
                launch { buttonAlphaAnim.animateTo(0f, tween(250)) }
                launch {
                    sheetOffsetY.animateTo(-sheetOffsetTargetPx, tween(350, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)))
                }
            }
        } else {
            kotlinx.coroutines.coroutineScope {
                launch { buttonAlphaAnim.animateTo(1f, tween(250)) }
                launch {
                    sheetOffsetY.animateTo(0f, tween(350, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)))
                }
            }
        }
    }
    // 实时同步 sheetOffsetY 到 MainActivity，避免通过 ratio 回调的帧延迟
    LaunchedEffect(sheetOffsetY.value) {
        onSheetOffsetChange(sheetOffsetY.value)
    }

    // ================================================================
    // 六、返回键处理
    // ================================================================
    BackHandler {
        when {
            isDeleting -> { /* 删除补位动画进行中，不响应 */ }
            deleteTargetCombId != null -> deleteTargetCombId = null
            isPageAnimating -> { /* 页面进入动画中，不响应 */ }
            isCutoutAnimating -> { /* 编辑模式动画中，不响应 */ }
            isCutoutActive -> {
                isCutoutActive = false
                sheetResetKey++  // 重置弹窗内部状态，丢弃编辑中的拖动值
                onRevertWallpaper()
                scope.launch {
                    kotlinx.coroutines.delay(400.milliseconds)
                    onCancelCutout()
                }
            }
            else -> onDismiss()
        }
    }

    // ================================================================
    // 七、派生动画值（每帧读取）
    // ================================================================
    // 当前进入动画值（动画完成后恒为 1f，避免无谓重组）
    val enterValue = if (animDone) 1f else animProgress.value
    // 按钮缩放：由 buttonScaleAnim 控制（进入 1.5→1.0，退出/应用 1.0→1.5）
    val buttonScale = buttonScaleAnim.value
    // 标题透明度：由 titleFadeAnim 控制（进入延迟淡入，退出快速淡出）
    val titleAlpha = titleFadeAnim.value

    // ================================================================
    // 八、UI 渲染
    // ================================================================

    // 点击空白区域取消删除态的交互源
    val cancelDeleteInteractionSource = remember { MutableInteractionSource() }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = cancelDeleteInteractionSource,
                indication = null,
                enabled = deleteTargetCombId != null,
                onClick = { deleteTargetCombId = null }
            )
            // -------- 8.1 背景裁剪遮罩：在背景上挖出卡片形状的洞，露出底层 MainActivity 内容 --------
            .drawBehind {
                val snapshotW = snapshot?.width?.toFloat() ?: size.width
                val snapshotH = snapshot?.height?.toFloat() ?: size.height
                val aspect = snapshotH / snapshotW
                val cardCenterX = size.width / 2f
                // 裁剪区域中心 Y 完全跟随卡片放大进程：
                val scaleProg = ((cardScaleAnim.value - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
                val baseOffsetY = size.height * 0.028f + cutoutOffsetY.value + sheetOffsetY.value
                val cardCenterY = size.height / 2f + baseOffsetY * (1f - scaleProg)
                val animW = size.width * cardScaleAnim.value
                val animH = animW * aspect
                val p = cutoutProgress.value
                val cutoutRadiusPx = screenRadiusDp.toPx() * cardScaleAnim.value * p

                val left = cardCenterX + ((cardCenterX - animW / 2f) - cardCenterX) * p
                val top = cardCenterY + ((cardCenterY - animH / 2f) - cardCenterY) * p
                val right = cardCenterX + ((cardCenterX + animW / 2f) - cardCenterX) * p
                val bottom = cardCenterY + ((cardCenterY + animH / 2f) - cardCenterY) * p

                val path = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                    if (p > 0f) {
                        val squirclePath = Path().apply {
                            addSquircleRect(
                                width = animW,
                                height = animH,
                                cornerRadius = cutoutRadiusPx,
                            )
                        }
                        addPath(squirclePath, Offset(left, top))
                    }
                    fillType = PathFillType.EvenOdd
                }
                drawPath(path, color = Color(0xFF1A1A1A))
            }
    ) {
        // -------- 8.2 尺寸计算 --------
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        // 卡片尺寸：宽度基于屏幕，高度跟随快照比例
        val cardWidthPx = screenW * cardScaleAnim.value
        val snapshotWidth = snapshot?.width?.toFloat() ?: screenW
        val snapshotHeight = snapshot?.height?.toFloat() ?: screenH
        val snapshotAspect = snapshotHeight / snapshotWidth
        val cardHeightPx = cardWidthPx * snapshotAspect
        val cardWidthDp = with(densityObj) { cardWidthPx.toDp() }
        val cardHeightDp = with(densityObj) { cardHeightPx.toDp() }

        // 缩放比例：从全屏缩放到卡片大小
        val targetScaleX = cardWidthPx / screenW
        val targetScaleY = cardHeightPx / screenH
        val targetScale = minOf(targetScaleX, targetScaleY)

        // 进入动画
        val currentScale = 1f + (targetScale - 1f) * enterValue
        val currentTranslationY = 0f

        // -------- 8.3 内容区域：卡片 Pager + 自定义按钮 --------
        // 内容区域：卡片居中于屏幕 60% 高度处
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 水平翻页（卡片）
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (cardHidden) 0f else 1f },
                beyondViewportPageCount = 1,
                pageSpacing = pagerSpacing.value.dp,
                userScrollEnabled = !cardHidden && animDone && deleteTargetCombId == null && !isDeleting
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                val targetCardScale = 1f - pageOffset * 0.35f
                val baseCardAlpha = 1f
                val isExitingNow = isExiting && exitProgress.value < 1f
                // 进入动画进度（带延迟，越远的卡片延迟越久）
                val enterDelayed = if (pageOffset > 0.001f && !animDone) {
                    val delayThreshold = (pageOffset * 0.01f).coerceIn(0f, 0.01f)
                    ((enterValue - delayThreshold) / (1f - delayThreshold)).coerceIn(0f, 1f)
                } else 1f
                // 进入动画：相邻卡片从更大尺寸（2.0f）缩小到目标尺寸，带延迟
                // 退出动画：反向放大到 2.0f，无延迟
                // 编辑模式进入/退出：相邻卡片放大缩小（以屏幕中心为轴），幅度较小
                val cutoutScaleBoost = if (pageOffset > 0.001f) {
                    (1.2f - targetCardScale) * cutoutEnterProgress.value
                } else 0f
                val cardScale = when {
                    isExitingNow && pageOffset > 0.001f -> {
                        targetCardScale + (1.4f - targetCardScale) * exitProgress.value
                    }
                    pageOffset > 0.001f && !animDone -> {
                        2.0f + (targetCardScale - 2.0f) * enterDelayed
                    }
                    else -> targetCardScale + cutoutScaleBoost
                }
                val cardAlpha = baseCardAlpha
                // z-order：离当前页越近越在上（当前卡在最上方，相邻卡在其下，第三个更下）
                // 距离越远 zIndex 越小
                val zOrdinal = (pageCount - pageOffset).coerceAtLeast(0f)

                Box(
                    modifier = Modifier.fillMaxSize().zIndex(zOrdinal),
                    contentAlignment = Alignment.Center
                ) {
                    // 卡片 Y 偏移跟随 cardScaleAnim 进程（与裁剪区域保持同步）
                    val cardScaleProg = ((cardScaleAnim.value - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
                    val cardBaseOffsetY = screenH * 0.028f + cutoutOffsetY.value + sheetOffsetY.value
                    val cardOffsetY = cardBaseOffsetY * (1f - cardScaleProg)
                    // 删除消失动画：仅对该卡片生效
                    val comb = if (page > 0) combinations.getOrNull(page - 1) else null
                    val isDisappearing = comb?.id != null && comb.id == disappearingCombId
                    val extraScale = if (isDisappearing) disappearScale.value else 1f
                    val extraAlpha = if (isDisappearing) disappearAlpha.value else 1f
                    // 缩放中心始终为屏幕中心（0.5, 0.58），卡片从外侧缩向屏幕中心
                    // 稳态也用同一中心，动画结束与最终位置完全一致，无跳变
                    val signedRelativePosition =
                        (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                    val pageSpacingPx = with(densityObj) { pagerSpacing.value.dp.toPx() }
                    val cardCenterOffsetX = signedRelativePosition * (screenW + pageSpacingPx)
                    val pivotOriginX = 0.5f - cardCenterOffsetX / cardWidthPx
                    val pivotOriginY = 0.5f + (screenH * 0.08f - cardOffsetY) / cardHeightPx
                    Box(
                        modifier = Modifier
                            .width(cardWidthDp)
                            .height(cardHeightDp)
                            .offset(y = with(densityObj) { cardOffsetY.toDp() })
                            .graphicsLayer {
                                scaleX = cardScale * extraScale
                                scaleY = cardScale * extraScale
                                transformOrigin = TransformOrigin(pivotOriginX, pivotOriginY)
                                alpha = cardAlpha * extraAlpha
                            }
                            .clip(RoundedRectangle(screenRadiusDp * cardScaleAnim.value))
                    ) {
                        if (page == 0) {
                            // 左侧"+"占位卡：点击创建新搭配
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF363636))
                                    .clickable { onCreateNewCombination() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Add,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                    contentDescription = "添加"
                                )
                            }
                        } else {
                            // 搭配卡：page 1..n 对应 combinations[0..n-1]
                            val combIdx = page - 1
                            val isCurrentComb = combIdx == currentCombinationIndex
                            // 视觉上的删除态：用 blurringCombId 判断，退出时持续到 alpha 动画归零，
                            // 让模糊能从 50 平滑渐变到 0，而非瞬间消失
                            val isDeleteTarget = comb?.id != null && comb.id == blurringCombId
                            val cardClickInteractionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .combinedClickable(
                                        interactionSource = cardClickInteractionSource,
                                        indication = null,
                                        enabled = !cardHidden && !isCutoutActive,
                                        onClick = {
                                            // 若当前处于删除态，点击取消
                                            if (deleteTargetCombId != null) {
                                                deleteTargetCombId = null
                                            }
                                        },
                                        onLongClick = {
                                            // 长按当前搭配卡：进入删除态（仅剩一个搭配时不允许删除）
                                            if (isCurrentComb && comb != null) {
                                                if (combinations.size > 1) {
                                                    deleteTargetCombId = comb.id
                                                } else {
                                                    Toast.makeText(context, "当前应用搭配不可删除", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                            ) {
                                // 删除态下对内容应用模糊（逐渐加深，最大 20）
                                val contentBlurDp = if (isDeleteTarget && blurSupported) (20f * deleteMaskAlpha.value).dp else 0.dp
                                if (isCurrentComb && snapshot != null && animDone && !cardHidden) {
                                    // 当前搭配：实时快照（包含当前课表+壁纸的完整预览）
                                    Image(
                                        bitmap = snapshot.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(contentBlurDp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (comb?.snapshot != null) {
                                    // 非当前搭配：使用已保存的完整背景快照（课表+壁纸）
                                    val combSnapshot = comb.snapshot!!
                                    Image(
                                        bitmap = combSnapshot.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(contentBlurDp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (comb?.bitmap != null) {
                                    // 兼容旧数据：仅有壁纸时回退使用壁纸
                                    val combBitmap = comb.bitmap!!
                                    Image(
                                        bitmap = combBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(contentBlurDp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // 无背景且无快照的搭配：显示纯色占位
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF363636)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "搭配 ${combIdx + 1}",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                                // 删除遮罩：删除按钮（仅模糊内容作为视觉压暗），仅在该卡片处于删除态时显示
                                if (isDeleteTarget) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = deleteMaskAlpha.value },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFE53935).copy(alpha = deleteMaskAlpha.value))
                                                    .clickable {
                                                        // 触发删除：先做缩小消失动画，再补位
                                                        val targetId = comb.id
                                                        val combIdx = currentCombinationIndex
                                                        val totalSize = combinations.size
                                                        if (targetId != null) {
                                                            deleteTargetCombId = null
                                                            isDeleting = true
                                                            scope.launch {
                                                                try {
                                                                    // 1. 卡片缩小消失动画：缩小到 0.6f 同时淡出
                                                                    disappearingCombId = targetId
                                                                    kotlinx.coroutines.coroutineScope {
                                                                        launch { disappearScale.animateTo(0.6f, tween(280, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                                                                        launch { disappearAlpha.animateTo(0f, tween(280, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))) }
                                                                    }
                                                                    // 2. 补位滚动（延迟到消失动画结束后）
                                                                    if (combIdx == 0 && totalSize > 1) {
                                                                        // 删除第一个：先滚动到 page 2（B 从右侧滑入），删除后立即跳回 page 1（B）
                                                                        if (2 < pageCount) {
                                                                            pagerState.animateScrollToPage(2)
                                                                        }
                                                                        val oldSize = combinations.size
                                                                        onDeleteCombination(targetId)
                                                                        // 等待 combinations 列表更新（带超时保护，避免 onDeleteCombination
                                                                        // 未收缩列表时 isDeleting 永久卡死，导致返回键失效和编辑模式无法退出）
                                                                        withTimeoutOrNull(500) {
                                                                            snapshotFlow { combinations.size }.first { it < oldSize }
                                                                        }
                                                                        // 立即跳到 page 1（B），避免显示 page 2（C）
                                                                        pagerState.scrollToPage(1)
                                                                    } else if (combIdx > 0) {
                                                                        // 删除非第一个：滚动到左侧搭配所在页（page = combIdx），
                                                                        // 让左侧搭配向右滑入补位；删除后该页正好对应新的当前搭配
                                                                        // 该分支无需等待列表更新：MainActivity 会将 currentCombinationIndex
                                                                        // 设为 combIdx-1，目标页 = combIdx-1+1 = combIdx，与当前页一致，不会触发冲突滚动
                                                                        val targetPage = combIdx
                                                                        if (targetPage != pagerState.currentPage) {
                                                                            pagerState.animateScrollToPage(targetPage)
                                                                        }
                                                                        onDeleteCombination(targetId)
                                                                    }
                                                                } finally {
                                                                    // 3. 重置状态（finally 确保异常时也能恢复）
                                                                    isDeleting = false
                                                                    disappearingCombId = null
                                                                    disappearScale.snapTo(1f)
                                                                    disappearAlpha.snapTo(1f)
                                                                }
                                                            }
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Delete,
                                                    contentDescription = "删除搭配",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            Text(
                                                text = "删除",
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 自定义按钮：仅在搭配卡（非"+"卡）显示，进入编辑模式时淡出
            if (pagerState.currentPage > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = customizeButtonAlpha.value
                            scaleX = buttonScale
                            scaleY = buttonScale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Button(
                        modifier = Modifier.offset(y = -60.dp).width(200.dp).height(48.dp).clip(RoundedRectangle(24.dp)),
                        enabled = !isCutoutActive,
                        onClick = {
                            // 进入开洞前先清除删除态，让模糊随 alpha 一起淡出
                            deleteTargetCombId = null
                            isCutoutActive = true
                            onCustomize()
                        },
                        colors = ButtonDefaults.buttonColors(
                            color = Color(0xFF363636),
                            disabledColor = Color(0xFF363636)
                        )
                    ) {
                        Text("自定义", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // -------- 8.4 壁纸编辑手势层 --------
        // 开洞模式下拦截拖拽/缩放手势，直接更新壁纸状态
        // 放在标题/按钮之前，确保顶部和底部按钮的点击不被拦截
        if (isCutoutActive && cardHidden && wallpaperBitmap != null) {
            val latestScale by rememberUpdatedState(wallpaperScale)
            val latestOffset by rememberUpdatedState(wallpaperOffset)
            val latestOnScale by rememberUpdatedState(onWallpaperScaleChange)
            val latestOnOffset by rememberUpdatedState(onWallpaperOffsetChange)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            latestOnScale((latestScale * zoom).coerceIn(0.5f, 5f))
                            latestOnOffset(latestOffset + pan)
                        }
                    }
            )
        }

        // -------- 8.5 标题：进入/退出时以屏幕中心缩放 + 淡入淡出 --------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = titleAlphaAnim.value * titleAlpha
                    scaleX = buttonScale
                    scaleY = buttonScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "课表外观",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                modifier = Modifier
                    .padding(top = statusBarPadding + 70.dp)
            )
        }

        // -------- 8.6 顶部按钮栏（退出/应用） --------
        // 必须在内容区域之后，确保 Z 轴在最上层
        // 进入/退出时以屏幕中心缩放，缩放中心设在 (0.5, 0.5)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = buttonAlphaAnim.value
                    scaleX = buttonScale
                    scaleY = buttonScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarPadding + 16.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            if (isLiquidGlass && liquidGlassBackdrop != null) {
                val animationScope = rememberCoroutineScope()
                val exitHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
                val applyHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
                val hapticFeedback = LocalHapticFeedback.current
                Box(
                    modifier = Modifier
                        .width(84.dp).height(40.dp)
                        .drawBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur(2f.dp.toPx())
                                lens(12f.dp.toPx(), 12f.dp.toPx())
                            },
                            shadow = { com.kyant.backdrop.shadow.Shadow(alpha = 0.3f) },
                            layerBlock = {
                                val progress = exitHighlight.pressProgress
                                val scale = 1f + 2f.dp.toPx() / 40.dp.toPx() * progress
                                scaleX = scale
                                scaleY = scale
                                val offset = exitHighlight.offset
                                translationX = size.minDimension * 0.05f * offset.x / size.maxDimension
                                translationY = size.minDimension * 0.05f * offset.y / size.maxDimension
                            },
                            onDrawSurface = {
                                drawRect(exitContainerColor)
                                drawRect(Color.Black.copy(alpha = 0.03f * exitHighlight.pressProgress))
                            }
                        )
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                if (isPageAnimating || isCutoutAnimating) { /* 动画中不响应 */ }
                                else if (isCutoutActive) {
                                    isCutoutActive = false
                                    sheetResetKey++
                                    onRevertWallpaper()
                                    scope.launch {
                                        kotlinx.coroutines.delay(400.milliseconds)
                                        onCancelCutout()
                                    }
                                } else onDismiss()
                            }
                        )
                        .then(exitHighlight.modifier)
                        .then(exitHighlight.gestureModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isCutoutActive) "取消" else "退出",
                        color = exitIconColor, fontSize = 16.sp, fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .width(84.dp).height(40.dp)
                        .drawBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur(2f.dp.toPx())
                                lens(12f.dp.toPx(), 12f.dp.toPx())
                            },
                            shadow = { com.kyant.backdrop.shadow.Shadow(alpha = 0.3f) },
                            layerBlock = {
                                val progress = applyHighlight.pressProgress
                                val scale = 1f + 2f.dp.toPx() / 40.dp.toPx() * progress
                                scaleX = scale
                                scaleY = scale
                                val offset = applyHighlight.offset
                                translationX = size.minDimension * 0.05f * offset.x / size.maxDimension
                                translationY = size.minDimension * 0.05f * offset.y / size.maxDimension
                            },
                            onDrawSurface = {
                                drawRect(primaryColor.copy(0.8f))
                                drawRect(Color.Black.copy(alpha = 0.03f * applyHighlight.pressProgress))
                            }
                        )
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                if (!showApplyLoading) {
                                    showApplyLoading = true
                                    onApply()
                                }
                            }
                        )
                        .then(applyHighlight.modifier)
                        .then(applyHighlight.gestureModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text("应用", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            } else {
            Box(
                modifier = Modifier
                    .width(84.dp).height(40.dp)
                    .clip(RoundedRectangle(20.dp))
                    .background(Color(0xFF363636))
                    .clickable {
                        if (isPageAnimating || isCutoutAnimating) { /* 动画中不响应 */ }
                        else if (isCutoutActive) {
                            isCutoutActive = false
                            sheetResetKey++  // 重置弹窗内部状态，丢弃编辑中的拖动值
                            onRevertWallpaper()
                            scope.launch {
                                kotlinx.coroutines.delay(400.milliseconds)
                                onCancelCutout()
                            }
                        } else onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCutoutActive) "取消" else "退出",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .width(84.dp).height(40.dp)
                    .clip(RoundedRectangle(20.dp))
                    .background(MiuixTheme.colorScheme.primary)
                    .clickable(enabled = !showApplyLoading) {
                        showApplyLoading = true
                        onApply()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("应用", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            }
            }
        }

        // -------- 8.7 应用加载指示器：屏幕中央显示 --------
        if (showApplyLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfiniteProgressIndicator(
                        size = 24.dp,
                        strokeWidth = 2.2.dp,
                        orbitingDotSize = 2.7.dp,
                    )
                    Text(
                        text = "正在应用",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // -------- 8.8 编辑模式工具栏（底部圆形按钮）：进入编辑模式时从底部滑入 --------
        AnimatedVisibility(
            visible = isCutoutActive && !isApplyingCustomize,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 壁纸按钮
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF363636))
                        .clickable { onPickWallpaper() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Image,
                        contentDescription = "壁纸",
                        modifier = Modifier.size(26.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(18.dp))
                VerticalDivider(Modifier.height(36.dp).clip(CircleShape),thickness = 2.dp,color = Color(0xFF363636))
                Spacer(modifier = Modifier.width(18.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF363636))
                        .clickable { showEffectSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Background,
                        contentDescription = "效果",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF363636))
                        .clickable { showCustomizeSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.GridView,
                        contentDescription = "自定义",
                        tint = Color.White
                    )
                }
            }
        }

        // -------- 8.9 进入快照动画层：全屏 snapshot 缩放到卡片大小 --------
        // 容器填满整个屏幕，不在内部 padding，让 snapshot 从全屏位置开始
        if (snapshot != null && !animDone) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = snapshot.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = currentScale
                            scaleY = currentScale
                            transformOrigin = TransformOrigin(0.5f, 0.58f)
                            translationY = currentTranslationY
                            // clip 和 scale 在同一个 graphicsLayer 内，每帧重新裁剪
                            // 视觉圆角 = screenRadius * currentScale（随缩放变化）
                            clip = true
                            shape = RoundedRectangle(screenRadiusDp)
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }

        // -------- 8.10 退出快照放大层：从卡片大小放大回全屏 --------
        // 应用时不显示退出快照，让用户直接看到壁纸放大的真实界面
        if (isExiting && snapshot != null && !isApplying) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = snapshot.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = exitScale
                            scaleY = exitScale
                            transformOrigin = TransformOrigin(0.5f, 0.58f)
                        }
                        .drawWithContent {
                            val clipPath = Path().apply {
                                addSquircleRect(
                                    width = size.width,
                                    height = size.height,
                                    cornerRadius = screenRadiusDp.toPx()
                                )
                            }
                            clipPath(clipPath) { this@drawWithContent.drawContent() }
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }

        // -------- 8.11 效果弹窗 --------
        OverlayBottomSheet(
            show = showEffectSheet,
            title = "效果",
            startAction = {
                IconButton(onClick = { showEffectSheet = false },
                    modifier = Modifier.padding(horizontal = 20.dp)) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "关闭",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            enableWindowDim = false,
            insideMargin = DpSize(0.dp, 0.dp),
            onDismissRequest = { showEffectSheet = false },
            backgroundColor = if (isAppDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF7F7F7),
            cornerRadius = 36.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = 20.dp,
                    colors = CardDefaults.defaultColors(
                        color = if (isAppDarkTheme()) Color(0xFF363636) else Color(0xFFFFFFFF),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "课程卡片模糊: ${effectValue.roundToInt()}.dp",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        Slider(
                            value = effectValue,
                            onValueChange = { effectValue = it },
                            valueRange = 0f..50f,
                            showKeyPoints = true,
                            keyPoints = listOf(0f, 10f, 20f, 30f, 40f, 50f),
                            magnetThreshold = 0.05f,
                            modifier = Modifier.fillMaxWidth(),
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step
                        )
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = 20.dp,
                    colors = CardDefaults.defaultColors(
                        color = if (isAppDarkTheme()) Color(0xFF363636) else Color(0xFFFFFFFF),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "卡片透明度: ${(cardAlphaValue * 100).roundToInt()}%",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        Slider(
                            value = cardAlphaValue,
                            onValueChange = { cardAlphaValue = it },
                            valueRange = 0f..1f,
                            showKeyPoints = true,
                            keyPoints = listOf(0.15f),
                            magnetThreshold = 0.05f,
                            modifier = Modifier.fillMaxWidth(),
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }

        // -------- 8.12 自定义弹窗 --------
        OverlayBottomSheet(
            show = showCustomizeSheet,
            title = "自定义",
            startAction = {
                IconButton(onClick = { showCustomizeSheet = false },
                    modifier = Modifier.padding(horizontal = 20.dp)) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "关闭",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            enableWindowDim = false,
            insideMargin = DpSize(0.dp, 0.dp),
            onDismissRequest = { showCustomizeSheet = false },
            backgroundColor = if (isAppDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF7F7F7),
            cornerRadius = 36.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = 20.dp,
                    colors = CardDefaults.defaultColors(
                        color = if (isAppDarkTheme()) Color(0xFF363636) else Color(0xFFFFFFFF),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "课程卡片高度: ${cardHeightValue.roundToInt()}.dp",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        Slider(
                            value = cardHeightValue,
                            onValueChange = { cardHeightValue = (it.roundToInt() / 2 * 2).toFloat() },
                            valueRange = 34f..92f,
                            showKeyPoints = true,
                            keyPoints = listOf(54f),
                            magnetThreshold = 0.05f,
                            modifier = Modifier.fillMaxWidth(),
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step
                        )
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = 20.dp,
                    colors = CardDefaults.defaultColors(
                        color = if (isAppDarkTheme()) Color(0xFF363636) else Color(0xFFFFFFFF),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "课程卡片圆角: ${cardCornerRadiusValue.roundToInt()}.dp",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        Slider(
                            value = cardCornerRadiusValue,
                            onValueChange = { cardCornerRadiusValue = it },
                            valueRange = 0f..48f,
                            showKeyPoints = true,
                            keyPoints = listOf(8f),
                            magnetThreshold = 0.05f,
                            modifier = Modifier.fillMaxWidth(),
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
    } // Scaffold
}
