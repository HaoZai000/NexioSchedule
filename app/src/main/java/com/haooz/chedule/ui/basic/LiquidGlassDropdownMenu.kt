package com.haooz.chedule.ui.basic

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick as onSemanticsClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kyant.backdrop.edgelight.edgeLight
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.interactive.computeDragTransform

/** 外层为阴影预留的内缩；外部按"占位槽真实位置"摆放本控件时需要减掉它 */
internal val LiquidGlassDropdownShadowPadding = 24.dp

/** 收起态圆形按钮直径，也是容器变换的起点尺寸 */
private val ButtonDiameter = 42.dp

/** 面板宽度与圆角 */
private val PanelWidth = 200.dp
private val PanelCornerRadius = 25.dp

/**
 * 面板外投射阴影档位。
 *
 * ⚠️ ListPopup.kt 的 popupPanelShadow 有一份**同值副本**（那边是 miuix 通用组件，
 * 不反向依赖 app 层），调整时两处必须同步，否则两种弹窗阴影深浅不一。
 */
private const val ShadowArgbLight = 0x1A000000
private const val ShadowArgbDark = 0x2E000000
private const val ShadowBlur = 12f
private const val ShadowExtend = 2f
/** ARGB 里的 alpha 会被放大这么多倍 —— 环形路径重复描边会累积浓度 */
private const val ShadowAlphaGain = 3.2f

/**
 * 右上角「更多」按钮与下拉菜单是**同一个控件**（容器变换）：
 * fraction=0 时是 42dp 玻璃盒（圆角 25dp 被钳成 21dp = 正圆），
 * fraction=1 时同一块玻璃长成 [PanelWidth] × 面板高的圆角矩形；图标与菜单项都在盒内随之裁剪。
 *
 * 已展开时还支持跟手滑动选菜单项（见 [dropdownPanelDragSelect]），无长按门槛。
 */
@Composable
fun LiquidGlassDropdownMenu(
    show: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    fraction: Animatable<Float, *> = remember { Animatable(0f) },
    onDismiss: (() -> Unit)? = null,
    onBackProgress: ((Float) -> Unit)? = null,
    onBackCancelled: (() -> Unit)? = null,
    triggerIcon: ImageVector? = null,
    triggerIconSize: Dp = 23.dp,
    triggerContentDescription: String? = null,
    onExpand: (() -> Unit)? = null,
    // 整块控件随顶栏一起平移（非当前页移出屏幕外）
    offsetPx: () -> Offset = { Offset.Zero },
    // 顶栏滚动材质透明度：收起态跟随它渐显渐隐，展开时按 fraction 渐显
    materialAlpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = !isAppDarkTheme()
    // 与顶栏液态玻璃按钮同色：收起态即那颗按钮，形变过程中玻璃不突变
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.76f)
        else Color(0xFF242424).copy(0.84f)
    val chromeLens = AppMaterialSettings.chromeLensEnabled()
    val triggerIconTint = if (isLightTheme) Color.Black.copy(0.85f) else Color.White.copy(0.85f)
    val hapticFeedback = LocalHapticFeedback.current
    // 锚点迁移进度：比尺寸更快到 1，先"移向面板中心"再放大
    val originProgress = remember { Animatable(0f) }
    // 退出回弹脉冲（1 → 欠阻尼 → 0，取越过 0 的部分）
    val settleBounce = remember { Animatable(1f) }
    // 展开过才播退出动画，首帧不播（否则启动时按钮会被带偏）
    var hasOpened by remember { mutableStateOf(false) }
    // 跟手滑选：菜单项各自登记位置，手势逐帧命中测试
    val dragSelectState = remember { DropdownPanelDragSelectState() }

    // 预测性返回：始终注册（否则动画未播完时立即返回会抓不到手势）
    val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    val backProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    // 按压高光 + 跟手拉伸 + 回弹
    val interactiveHighlight = remember(coroutineScope) {
        InteractiveHighlight(
            animationScope = coroutineScope,
            radiusScale = { 1f - 0.6f * fraction.value.coerceIn(0f, 1f) },
            // 半径上限 150dp：不用 minDimension，否则菜单项越多面板越高、光晕越大。
            // 收起态是 42dp 小圆，min 会自动退回自身尺寸，光晕不会漫出圆外。
            radiusBaseDp = 150.dp,
        )
    }

    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = show && onDismiss != null,
        onBackCancelled = {
            onBackCancelled?.invoke()
            coroutineScope.launch {
                if (backProgress.value > 0f) {
                    fraction.animateTo(1f, animationSpec = tween(150))
                    originProgress.animateTo(1f, animationSpec = tween(150))
                    backProgress.snapTo(0f)
                }
            }
        },
        onBackCompleted = {
            onDismiss?.invoke()
        },
    )

    // 逐帧收手势进度：菜单缩回锚点
    LaunchedEffect(Unit) {
        snapshotFlow { navigationEventState.transitionState }
            .collect { transitionState ->
                if (
                    transitionState is NavigationEventTransitionState.InProgress &&
                    transitionState.direction == NavigationEventTransitionState.TRANSITIONING_BACK &&
                    PredictiveBackSettings.enabled
                ) {
                    val progress = transitionState.latestEvent.progress
                    backProgress.snapTo(progress)
                    onBackProgress?.invoke(progress)
                    fraction.snapTo(1f - progress)
                    originProgress.snapTo(1f - progress)
                }
            }
    }

    LaunchedEffect(show) {
        if (show) {
            hasOpened = true
            launch {
                fraction.animateTo(
                    1f,
                    spring(dampingRatio = 0.77f, stiffness = 220f, visibilityThreshold = 0.0001f)
                )
            }
            launch {
                originProgress.animateTo(
                    1f,
                    spring(dampingRatio = 0.77f, stiffness = 420f, visibilityThreshold = 0.0001f)
                )
            }
            settleBounce.snapTo(1f)
        } else if (!hasOpened) {
            // 首帧（从未展开）：直接归位，不播退出动画/回弹
            fraction.snapTo(0f)
            originProgress.snapTo(0f)
            settleBounce.snapTo(0f)
        } else {
            val exitEasing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
            launch {
                fraction.animateTo(
                    0f,
                    spring(dampingRatio = 0.85f, stiffness = 650f, visibilityThreshold = 0.0001f)
                )
            }
            // 退出位移回弹：独立一条欠阻尼 spring，与尺寸 spring 解耦
            launch {
                settleBounce.snapTo(1f)
                settleBounce.animateTo(
                    0f,
                    spring(dampingRatio = 0.52f, stiffness = 220f, visibilityThreshold = 0.0001f)
                )
            }
            // 这里必须"等"动画跑完（不能立刻 snapTo，否则会把上面的动画取消掉）；
            // 用锚点 tween 兜住，结束后再收尾
            originProgress.animateTo(0f, tween(340, easing = exitEasing))
            fraction.snapTo(0f)
            originProgress.snapTo(0f)
        }
    }

    // 读 fraction 驱动尺寸；不钳到 1，保留 spring 过冲（末尾回弹）
    val f = fraction.value
    // 背景模糊度随展开进度过渡：收起态 8dp（与顶栏液态玻璃按钮一致），展开态 24dp（面板更大，需更强模糊）。
    // 折射 lens(8, 24) 保持常量：它作用在边缘 SDF 上，面板变大时跟着涨反而会让边缘变形。
    // drawBackdrop 靠「effects lambda 换引用」才会重算 blur/lens uniform
    // （updateEffects 里 cachedEffectFn === effects 命中就复用旧 RenderEffect），
    // 但每帧换引用又会让节点每帧重建 RenderEffect 并重录采样层；故量化成 8 档再喂进去。
    val blurStep = (f.coerceIn(0f, 1f) * 8f).roundToInt()
    val blurDp = 8f + 16f * (blurStep / 8f)
    // 面板自然高度：由容器内的内容以全尺寸测量得到，作为高度插值终点
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val targetHeight = if (panelHeightPx > 0) {
        with(LocalDensity.current) { panelHeightPx.toDp() }
    } else {
        ButtonDiameter
    }
    // 材质可见度：滚动值与展开进度取大（f*5 → 0.2 饱和，出现早、消失晚）
    val materialVisible = max(materialAlpha, (f * 5f).coerceIn(0f, 1f))
    // 内容模糊：清晰(0)→模糊(中途)→清晰(1)
    val contentBlur = 6.dp * (1f - abs(2f * f - 1f)).coerceIn(0f, 1f)
    val width = lerp(ButtonDiameter, PanelWidth, f)
    val height = lerp(ButtonDiameter, targetHeight, f)
    // 内容随容器等比缩放（还原原来"内容有缩放"的手感），同样保留过冲
    val contentScale = (width.value / PanelWidth.value).coerceAtLeast(0f)

    Box(
        modifier = modifier.padding(LiquidGlassDropdownShadowPadding)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    val o = offsetPx()
                    // 绕"右上角→正中心"迁移锚点缩放的位移（p=originProgress，s=宽/面板宽，两端为 0）
                    val p = originProgress.value
                    val s = size.width / PanelWidth.toPx()
                    val k = 0.5f * p * (1f - s)
                    // 退出位移回弹：沿收回方向（右上）越过终点再弹回，进入为 0
                    val pulse = (-settleBounce.value).coerceAtLeast(0f)
                    val bouncePx = pulse * 12f * density

                    // 按压/拖拽形变：按压缩放 4dp、沿拖动方向拉伸、位移 tanh 阻尼跟手
                    val drag = computeDragTransform(
                        width = size.width,
                        height = size.height,
                        fraction = f,
                        pressProgress = interactiveHighlight.pressProgress,
                        offset = interactiveHighlight.offset,
                        density = this,
                    )
                    scaleX = drag.scaleX
                    scaleY = drag.scaleY
                    translationX = o.x - PanelWidth.toPx() * k + bouncePx + drag.translationX
                    translationY = o.y + targetHeight.toPx() * k - bouncePx + drag.translationY
                }
                .size(width = width, height = height)
                // 面板自身的 root 位置：菜单项靠它把 boundsInRoot 换算成面板局部坐标
                .onGloballyPositioned { dragSelectState.panelTopInRoot = it.boundsInRoot().top }
                // 跟手滑选：挂在面板上而非 42dp 触发图标，手指按下后要能一路滑到任意一项
                .dropdownPanelDragSelect(
                    state = dragSelectState,
                    fraction = { fraction.value },
                    hapticFeedback = hapticFeedback
                )
                // 拖动手势：放在 clip 之前，不受圆角裁剪影响
                .then(interactiveHighlight.gestureModifier)
                .drawBehind {
                    // 与顶栏液态玻璃按钮同一套阴影：环形（外圈减内圈）+ 模糊/外扩随材质衰减
                    // 用 materialVisible 而非 materialAlpha：收起态仍跟顶栏材质，展开后
                    // 即使顶栏材质透明也要有阴影（材质可见度已按展开进度饱和到 1）
                    val spread = materialVisible
                    if (spread > 0.01f) {
                        val shadowArgb = if (isLightTheme) ShadowArgbLight else ShadowArgbDark
                        val blurRadius = ShadowBlur * density * spread
                        val shadowSpread = ShadowExtend * density * spread
                        val r = PanelCornerRadius.toPx()
                        val nativePath = android.graphics.Path().apply {
                            addRoundRect(
                                -shadowSpread, -shadowSpread,
                                size.width + shadowSpread, size.height + shadowSpread,
                                r + shadowSpread, r + shadowSpread,
                                android.graphics.Path.Direction.CW
                            )
                            addRoundRect(
                                0f, 0f, size.width, size.height, r, r,
                                android.graphics.Path.Direction.CCW
                            )
                        }
                        val paint = Paint().apply {
                            color = android.graphics.Color.argb(
                                (android.graphics.Color.alpha(shadowArgb) * ShadowAlphaGain).coerceAtMost(255f).toInt(),
                                android.graphics.Color.red(shadowArgb),
                                android.graphics.Color.green(shadowArgb),
                                android.graphics.Color.blue(shadowArgb)
                            )
                            maskFilter = BlurMaskFilter(
                                blurRadius.coerceAtLeast(0.1f),
                                BlurMaskFilter.Blur.NORMAL
                            )
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawPath(nativePath, paint)
                        }
                    }
                }
                .clip(ContinuousRoundedRectangle(PanelCornerRadius))
        ) {
            // 与顶栏液态玻璃按钮同一套采样效果；blur 随展开档位过渡（8dp → 24dp）
            val glassEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
                remember(chromeLens, blurStep) {
                    {
                        vibrancy()
                        blur(blurDp.dp.toPx())
                        if (chromeLens) lens(8.dp.toPx(), 24.dp.toPx())
                    }
                }
            // 玻璃单独一层：只有它跟材质 alpha（layerBlock 作用于整个节点，挂容器上会连图标一起透明）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = materialVisible }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousRoundedRectangle(PanelCornerRadius) },
                        effects = glassEffects,
                        highlight = null,
                        shadow = null,
                        onDrawSurface = {
                            drawRect(containerColor)
                        }
                    )
                    .edgeLight(
                        shape = ContinuousRoundedRectangle(PanelCornerRadius),
                        edgeLight = rememberDefaultEdgeLight(baseColor = containerColor)
                    )
            )

            // 按压高光：必须在玻璃壳之上、菜单内容之下 —— 前者否则被材质盖住，
            // 后者否则会盖掉菜单项的跟手高亮底色（Plus 加色会把底色冲白）。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(interactiveHighlight.modifier)
            )

            // 菜单内容：全尺寸居中、按中心缩放，随容器长大被裁出
            Column(
                modifier = Modifier
                    // 居中：缩放中心始终 = 当前卡片整体的正中心
                    .align(Alignment.Center)
                    .requiredWidth(PanelWidth)
                    .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                    .onSizeChanged { panelHeightPx = it.height }
                    .graphicsLayer {
                        // 内容可见窗口：f<0.3 全隐、f>0.7 全显
                        alpha = ((f - 0.3f) / 0.4f).coerceIn(0f, 1f)
                        scaleX = contentScale
                        scaleY = contentScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .blur(contentBlur)
                    .padding(vertical = 8.dp)
            ) {
                // 菜单项从这里读跟手选中态（局部坐标换算要用面板在 root 里的位置）
                CompositionLocalProvider(LocalDropdownPanelDragSelect provides dragSelectState) {
                    content()
                }
            }

            // 收起态的图标：就在同一个玻璃盒里，随盒子长大淡出
            if (triggerIcon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(ButtonDiameter)
                        // 裁成圆：否则 clickable 的涟漪会按矩形画，展开时露出直角
                        .clip(CircleShape)
                        .graphicsLayer {
                            // 图标从右上角移向容器中心，到达时刚好淡完；退出系数更小 → 更早出现
                            val iconK = if (show) 2.5f else 1.7f
                            val p = (fraction.value * iconK).coerceIn(0f, 1f)
                            alpha = 1f - p
                            translationX = p * (ButtonDiameter.toPx() / 2f - width.toPx() / 2f)
                            translationY = p * (height.toPx() / 2f - ButtonDiameter.toPx() / 2f)
                        }
                       // interactiveHighlight 高光提供，不要默认涟漪
                        .clickable(
                            enabled = !show && onExpand != null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onExpand?.invoke()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = triggerIcon,
                        contentDescription = triggerContentDescription,
                        modifier = Modifier.size(triggerIconSize),
                        tint = triggerIconTint,
                    )
                }
            }
        }
    }
}

@Composable
fun LiquidGlassDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    val isLightTheme = !isAppDarkTheme()
    val textColor = if (isLightTheme) Color(0xFF1A1A1A) else Color(0xFFE8E4DE)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(ContinuousRoundedRectangle(17.dp))
            // 登记纵向区间供面板命中测试 + 绘制跟手高亮
            .dropdownPanelEntry(enabled = true, action = onClick)
            // 无障碍点击动作：菜单项不再挂 clickable（会与面板手势抢事件），
            // 但读屏/键盘仍要能触发
            .semantics {
                role = Role.Button
                onSemanticsClick {
                    onClick()
                    true
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.size(10.dp))
            }
            Text(
                text = text,
                fontSize = 15.6.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}