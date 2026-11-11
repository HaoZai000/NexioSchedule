package com.haooz.chedule.ui.basic

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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.haooz.chedule.ui.components.GlassDragStretchRatio
import com.haooz.chedule.ui.components.GlassDragTranslationDamping
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.softglass.glass.BrightLargeTone
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.GlassFill
import com.haooz.softglass.glass.GlassTone
import com.haooz.softglass.glass.ThemeFill
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.interactive.DropdownMaskMode
import top.yukonga.miuix.kmp.interactive.DropdownPanelDragSelectState
import top.yukonga.miuix.kmp.interactive.LocalDropdownPanelDragSelect
import top.yukonga.miuix.kmp.interactive.computeDragTransform
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragSelect
import top.yukonga.miuix.kmp.interactive.dropdownPanelEntry
import top.yukonga.miuix.kmp.interactive.dropdownPanelSlidingMask
import top.yukonga.miuix.kmp.internal.drawBlurredPathShadow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.rememberNavigationBack

/** 外层为阴影预留的内缩；外部按占位槽真实位置摆放本控件时需减掉它。跨模块须 public。 */
val LiquidGlassDropdownShadowPadding = 24.dp

/** 形变面板的实时柔光 shader 缓存键（与顶栏按钮分开：两者尺寸不同，共用会互相覆盖 uniform）。 */
private const val LIVE_DROPDOWN_GLASS_KEY = "NexioSoftGlassLiveDropdown"

/** 展开进度超过该值就切到大控件材质：收起态是 42dp 圆按钮，半程以后已长成面板。 */
private const val EXPANDED_TONE_THRESHOLD = 0.5f

/** 收起态圆形按钮直径，也是容器变换的起点尺寸 */
private val ButtonDiameter = 42.dp

/** 面板宽度与圆角 */
private val PanelWidth = 200.dp
private val PanelCornerRadius = 25.dp

/**
 * 面板外投射阴影档位。⚠️ ListPopup.kt 的 popupPanelShadow 有一份**同值副本**
 * （那边是 miuix 通用组件，不反向依赖 app 层），调整时两处必须同步，否则两种弹窗阴影深浅不一。
 */
private val ShadowColorLight = Color(0x1A000000)
private val ShadowColorDark = Color(0x2E000000)
private const val ShadowBlur = 12f
private const val ShadowExtend = 2f
/** 阴影 alpha 会被放大这么多倍 —— 环形路径重复描边会累积浓度 */
private const val ShadowAlphaGain = 3.2f

/**
 * 右上角「更多」按钮与下拉菜单是**同一个控件**（容器变换）：fraction=0 时是 42dp 玻璃盒
 * （圆角 25dp 被钳成正圆），fraction=1 时长成 [PanelWidth] × 面板高的圆角矩形。已展开时支持
 * 跟手滑动选菜单项（见 [dropdownPanelDragSelect]），无长按门槛。
 */
@Composable
fun LiquidGlassDropdownMenu(
    show: Boolean,
    backdrop: GlassBackdrop,
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
    /**
     * 材质填充策略。默认 [ThemeFill]（浅色白 0.55 / 深色黑 0.45）—— 与顶栏液态玻璃按钮
     * 同一个默认值：收起态那颗 42dp 圆就是按钮，形变过程中材质不能突变。
     * 传 `NoneFill` 则透出原本的玻璃色。
     */
    fill: GlassFill = ThemeFill,
    /**
     * 展开态面板的材质基调；默认 [BrightLargeTone]（悬浮大控件那套）。
     * 收起态那颗 42dp 圆按钮固定用 [BrightTone]，与顶栏液态玻璃按钮同一套。
     */
    tone: GlassTone = BrightLargeTone,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = !isAppDarkTheme()
    // 与顶栏液态玻璃按钮同色（同一个 alpha 来源；浅深各一档）：收起态即那颗按钮，形变过程中玻璃不突变
    val containerColor = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainer
        .copy(alpha = AppMaterialSettings.glassSurfaceAlpha(if (isLightTheme) 0.76f else 0.84f, isLightTheme))
    else if (isLightTheme) Color(0xFFFAFAFA).copy(AppMaterialSettings.glassSurfaceAlpha(0.76f, isLight = true))
        else Color(0xFF242424).copy(AppMaterialSettings.glassSurfaceAlpha(0.84f, isLight = false))
    // 展开态面板材质：几何 / 光学 / 调色都来自材质层，这里只声明用哪一套
    val panelGlassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = !isLightTheme,
        fill = fill,
        tone = tone,
    )
    // 收起态 42dp 圆按钮：与顶栏按钮同一套小控件材质。两套共用 fill，形变时底色不跳。
    // 实例保持稳定，择一发生在 draw 期 —— 组合期换 style 会让 effects 每帧换引用、重录采样层。
    val collapsedGlassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = !isLightTheme,
        fill = fill,
        tone = BrightTone,
    )
    val useSoftGlass = AppMaterialSettings.glassEffectsEnabled()
    val panelCornerRadiusPx = with(LocalDensity.current) { PanelCornerRadius.toPx() }
    val triggerIconTint = if (isLightTheme) Color.Black.copy(0.85f) else Color.White.copy(0.85f)
    val hapticFeedback = LocalHapticFeedback.current
    // 锚点迁移进度：比尺寸更快到 1，先"移向面板中心"再放大
    val originProgress = remember { Animatable(0f) }
    // 退出回弹脉冲（1 → 欠阻尼 → 0，取越过 0 的部分）
    val settleBounce = remember { Animatable(1f) }
    // 展开过才播退出动画，首帧不播（否则启动时按钮会被带偏）
    var hasOpened by remember { mutableStateOf(false) }
    // 跟手滑选：菜单项各自登记位置，手势逐帧命中测试
    // 滑选遮罩：整块从一项连贯滑到另一项（另一形态是逐项淡入淡出，见 DropdownMaskMode）
    val dragSelectState = remember {
        DropdownPanelDragSelectState(maskMode = DropdownMaskMode.Sliding)
    }

    // 预测性返回：始终注册（否则动画未播完时立即返回会抓不到手势）。⚠️ handler 与进度读取
    // 必须绑定同一份状态，拆开会导致跟手动画失效。
    val navBackState = rememberNavigationBack()
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
    // 与 LiquidGlassTextButton 同一套：拉伸交给 drawBackdrop 的 layerBlock，源库采样时自动逆缩放
    val glassLayerBlock: GraphicsLayerScope.() -> Unit = remember(interactiveHighlight, fraction) {
        {
            val drag = computeDragTransform(
                width = size.width,
                height = size.height,
                fraction = fraction.value,
                pressProgress = interactiveHighlight.pressProgress,
                offset = interactiveHighlight.offset,
                density = this,
                stretchRatio = GlassDragStretchRatio,
                translationDamping = GlassDragTranslationDamping,
                pressScaleDp = 2f,
            )
            scaleX = drag.scaleX
            scaleY = drag.scaleY
        }
    }

    navBackState.handler(
        enabled = show && onDismiss != null,
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

    // 逐帧收手势进度：菜单缩回锚点。backProgress() 手势进行中返回连续进度，其余时间为 null
    LaunchedEffect(Unit) {
        snapshotFlow { navBackState.backProgress() }
            .collect { progress ->
                if (progress != null && PredictiveBackSettings.enabled) {
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
    // 模糊与折射都随展开进度走，共用同一组 8 档量化：effects lambda 换引用才重算，
    // 每帧换引用会让节点每帧重建 RenderEffect。
    val blurStep = (f.coerceIn(0f, 1f) * 8f).roundToInt()
    val expandStep = blurStep / 8f
    // 展开态模糊全档位统一 12dp：收起态 6 → 展开 12；无材质档恒 0（玻璃效果整体关）。
    val blurDp = if (AppMaterialSettings.isNoneTier()) 0f
    else AppMaterialSettings.glassBlurDp() + 6f * expandStep
    // 折射随展开连贯变大：折叠 (7, 28) → 展开 (12, 48)。折叠态是 42dp 小圆，折射带相对不能太厚；
    // 长成面板后同步加厚，边缘的水灵感才跟得上尺寸。
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
                        stretchRatio = GlassDragStretchRatio,
                        translationDamping = GlassDragTranslationDamping,
                        pressScaleDp = 2f,
                    )
                    translationX = o.x - PanelWidth.toPx() * k + bouncePx + drag.translationX
                    translationY = o.y + targetHeight.toPx() * k - bouncePx + drag.translationY
                }
                .size(width = width, height = height)
                // 面板自身的 root 位置：菜单项靠它把 boundsInRoot 换算成面板局部坐标
                .onGloballyPositioned { dragSelectState.panelTopInRoot = it.boundsInRoot().top }
                .then(
                    // 纯面板模式（触发器画在外部，如顶栏那颗按钮）完全收起时，这块 42dp 透明壳
                    // 与外部触发器完全重合，若仍挂着手势就会把那边的点击吃掉 —— 表现为"按钮点不动"。
                    // 收起且无动画时整段手势都不挂；其余状态（展开、收回动画、自带触发器）行为不变。
                    if (triggerIcon == null && !show && f <= 0.001f) {
                        Modifier
                    } else {
                        Modifier
                            // 跟手滑选：挂在面板上而非 42dp 触发图标，手指按下后要能一路滑到任意一项
                            .dropdownPanelDragSelect(
                                state = dragSelectState,
                                fraction = { fraction.value },
                                hapticFeedback = hapticFeedback
                            )
                            // 拖动手势：放在 clip 之前，不受圆角裁剪影响
                            .then(interactiveHighlight.gestureModifier)
                    }
                )
                .drawBehind {
                    // 与顶栏液态玻璃按钮同一套阴影：环形（外圈减内圈）+ 模糊/外扩随材质衰减。用
                    // materialVisible 而非 materialAlpha：展开后即使顶栏材质透明也要有阴影。
                    val spread = materialVisible
                    if (spread > 0.01f) {
                        val shadowColor = if (isLightTheme) ShadowColorLight else ShadowColorDark
                        val blurRadius = ShadowBlur * density * spread
                        val shadowSpread = ShadowExtend * density * spread
                        val r = PanelCornerRadius.toPx()
                        // 环形：外圈 roundrect CW + 内圈 roundrect CCW（winding 差集）
                        val ringPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(
                                        -shadowSpread, -shadowSpread,
                                        size.width + shadowSpread, size.height + shadowSpread,
                                    ),
                                    cornerRadius = CornerRadius(r + shadowSpread, r + shadowSpread),
                                ),
                                Path.Direction.Clockwise,
                            )
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(0f, 0f, size.width, size.height),
                                    cornerRadius = CornerRadius(r, r),
                                ),
                                Path.Direction.CounterClockwise,
                            )
                        }
                        drawBlurredPathShadow(
                            path = ringPath,
                            // alpha × ShadowAlphaGain 在浮点域 clamp（不提前压到 255，避免精度损失）
                            color = shadowColor.copy(
                                alpha = (shadowColor.alpha * ShadowAlphaGain).coerceAtMost(1f),
                            ),
                            blurRadius = blurRadius.coerceAtLeast(0.1f),
                        )
                    }
                }
        ) {
            // 与顶栏液态玻璃按钮同一套采样效果；blur 随展开档位过渡（8dp → 24dp）；无材质档整体关
            val glassEffects: com.haooz.softglass.backdrop.GlassEffectScope.() -> Unit =
                remember(blurStep, blurDp, panelGlassStyle, collapsedGlassStyle, useSoftGlass) {
                    {
                        if (useSoftGlass) {
                            blur(blurDp.dp.toPx())
                            // 形变中的面板：尺寸每帧在变，uniform 在 draw 期按当前 size 重灌
                            // （DrawBackdrop 的 effectScope.update 会在尺寸变化时重放 effects）
                            runtimeShaderEffect(
                                key = LIVE_DROPDOWN_GLASS_KEY,
                                shaderString = SOFT_GLASS_LIVE_SHADER,
                                uniformShaderName = "content",
                            ) {
                                // 收起态圆按钮 / 展开态面板各一套材质，按展开进度在 draw 期择一。
                                // fraction.value 在这里读不会触发重组，也不换 effects 引用。
                                val activeStyle = if (fraction.value < EXPANDED_TONE_THRESHOLD) {
                                    collapsedGlassStyle
                                } else {
                                    panelGlassStyle
                                }
                                applySoftGlass(
                                    style = activeStyle,
                                    width = size.width,
                                    height = size.height,
                                    radiusPx = panelCornerRadiusPx,
                                )
                            }
                        }
                    }
                }
            // 玻璃壳即内容容器：layerBlock 的缩放要连菜单项、高光、图标一起带动
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = materialVisible }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shadow = null,
                        shape = { ContinuousRoundedRectangle(PanelCornerRadius) },
                        effects = glassEffects,
                        layerBlock = glassLayerBlock,
                        // 柔光自己画玻璃色与方向光；无材质档不跑 shader，必须留容器色
                        onDrawSurface = if (useSoftGlass) null else {
                            { drawRect(containerColor) }
                        }
                    )
            ) {

                    // 按压高光必须夹在玻璃壳与菜单内容之间：居上被材质盖住，居下会盖掉
                    // 菜单项的跟手高亮底色（Plus 加色会把底色冲白）。
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
                        // 遮罩与菜单项同在这个 Column 里，父层变换天然一起带。
                        .dropdownPanelSlidingMask(dragSelectState)
                ) {
                    // 菜单项从这里读跟手选中态（局部坐标换算要用面板在 root 里的位置）
                    CompositionLocalProvider(LocalDropdownPanelDragSelect provides dragSelectState) {
                        content()
                    }
                }

            }

            // 收起态的图标：不进玻璃层，材质 alpha 不会连它一起隐掉
            if (triggerIcon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(ButtonDiameter)
                        // 裁成圆：否则 clickable 的涟漪会按矩形画，展开时露出直角
                        .clip(ContinuousCapsule())
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