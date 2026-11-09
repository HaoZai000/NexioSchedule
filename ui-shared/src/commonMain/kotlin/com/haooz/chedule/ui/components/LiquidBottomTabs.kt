package com.haooz.chedule.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.haooz.chedule.ui.effects.liquidglass.DampedDragAnimation
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.softglass.glass.BarFill
import com.haooz.softglass.glass.BrightLargeTone
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.shape.ContinuousCapsule
import top.yukonga.miuix.kmp.interactive.computeDragTransform
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** tab 胶囊 / 右侧圆按钮是两块独立玻璃，尺寸不同 ⇒ 各自一份 shader uniform，不能共用 key。 */
private const val LIVE_BOTTOM_BAR_PILL_GLASS_KEY = "NexioSoftGlassLiveBottomBarPill"
private const val LIVE_BOTTOM_BAR_BUTTON_GLASS_KEY = "NexioSoftGlassLiveBottomBarButton"

/** 底栏胶囊底色。tab 胶囊与圆按钮共用同一块材质，颜色必须同源，所以只在这里定义一次。 */
@Composable
internal fun bottomBarSurfaceColor(): Color {
    return when {
        MiuixTheme.isDynamicColor ->
            MiuixTheme.colorScheme.surfaceContainer
                .copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.5f))
        !isAppDarkTheme() ->
            Color(0xFFFAFAFA).copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.5f))

        else ->
            Color(0xFF242424).copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.6f))
    }
}

/**
 * 底栏右端的圆形按钮：一块**独立**玻璃，与 tab 胶囊之间只有间距、没有融合。
 */
data class LiquidBottomTrailing(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    /** 与胶囊的静止间距 */
    val gap: Dp = 10.dp,
)

/** 底部导航 tab：纯视觉，手势由 [LiquidBottomTabs] 统一处理 */
@Composable
fun RowScope.LiquidBottomTab(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(ContinuousCapsule())
            .semantics { role = Role.Tab }
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * 底部导航：一块 tab 胶囊 + 一颗独立圆按钮，两块玻璃互不融合。
 *
 * 手势统一由最上层的手势节点处理：按下 tab 选中、拖胶囊 1:1 跟手、松手吸附最近 tab 并提交。
 * 滑块只是胶囊内的纯色指示，不再叠一层采样玻璃 —— 融球形状、bleed 余量、离屏捕获层、
 * 自研折射与旧 `lens` 全部去掉。
 *
 * 按压手感没有跟着融球一起删：胶囊与圆按钮**各一份** [InteractiveHighlight]，
 * 形变走 `computeDragTransform`（按压缩放 + 沿拖动方向拉伸 + 阻尼跟手），高光单独一层并裁到形状；
 * 滑块只跟手平移、不额外缩放；跟手位移直接用 `computeDragTransform` 的标准 tanh 阻尼，
 * 不另加夹限。
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    backdrop: GlassBackdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 56.dp,
    selectorHeight: Dp = 48.dp,
    trailing: LiquidBottomTrailing? = null,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isAppDarkTheme()
    val containerColor = bottomBarSurfaceColor()
    val glassBlur = AppMaterialSettings.glassBlurDp()
    val glassEffectsOn = AppMaterialSettings.glassEffectsEnabled()
    val barGlassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = !isLightTheme,
        fill = BarFill,
        tone = BrightLargeTone,
    )
    // 两块玻璃都是胶囊；节点尺寸不同，uniform 必须各自一份
    val capsuleShape: () -> Shape = remember { { ContinuousCapsule() } }
    val pillEffects: GlassEffectScope.() -> Unit = remember(
        glassBlur, glassEffectsOn, barGlassStyle
    ) {
        {
            if (glassEffectsOn) {
                // Mirror：默认 Clamp 会把边缘采样钳到最外一行，边缘一圈模糊退化
                blur(glassBlur.dp.toPx(), TileMode.Mirror)
                padding = 0f
                runtimeShaderEffect(
                    key = LIVE_BOTTOM_BAR_PILL_GLASS_KEY,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(
                        style = barGlassStyle,
                        width = size.width,
                        height = size.height,
                    )
                }
            }
        }
    }
    val buttonEffects: GlassEffectScope.() -> Unit = remember(
        glassBlur, glassEffectsOn, barGlassStyle
    ) {
        {
            if (glassEffectsOn) {
                blur(glassBlur.dp.toPx(), TileMode.Mirror)
                padding = 0f
                runtimeShaderEffect(
                    key = LIVE_BOTTOM_BAR_BUTTON_GLASS_KEY,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(
                        style = barGlassStyle,
                        width = size.width,
                        height = size.height,
                    )
                }
            }
        }
    }
    // 柔光自己画玻璃色，叠容器色会盖住折射；只有无材质档留实色底
    val surface: DrawScope.() -> Unit = remember(containerColor) { { drawRect(containerColor) } }

    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val maxIndex = (tabsCount - 1).toFloat()
    val dampedDragAnimation = remember(animationScope, tabsCount) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedTabIndex().toFloat(),
            valueRange = 0f..maxIndex,
            visibilityThreshold = CapsuleSlider.DampedVisibilityThreshold,
            initialScale = 1f,
            pressedScale = CapsuleSlider.PressScale,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> }
        )
    }

    // 胶囊与圆按钮各有自己的按压进度：两块玻璃分开之后不能再共用一份高光状态，
    // 否则按其中一块、另一块也会跟着亮。手势分别挂在胶囊手势层与圆按钮自身。
    val pillHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            // 底栏是半透明玻璃，浅色默认 0.05 太弱
            lightPeakAlpha = 0.1f,
        )
    }
    val trailingHighlight = if (trailing != null) {
        remember(animationScope, containerHeight) {
            InteractiveHighlight(
                animationScope = animationScope,
                // 圆按钮是正圆：光晕半径固定按直径算，别被节点 minDimension 夹小
                radiusBaseDp = containerHeight,
                fixedRadius = true,
                // 只要手指处的圆形光晕，不整颗按钮铺一层白
                drawFlatOverlay = false,
            )
        }
    } else {
        null
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val viewConfiguration = LocalViewConfiguration.current
        val hapticFeedback = LocalHapticFeedback.current
        val gapDp = trailing?.gap ?: 0.dp
        val trailingSize = if (trailing != null) containerHeight else 0.dp
        val pillPx =
            constraints.maxWidth.toFloat() - with(density) { (gapDp + trailingSize).toPx() }
        val pillWidthDp = with(density) { pillPx.toDp() }
        val padPx = with(density) { 4f.dp.toPx() }
        val tabWidthPx = (pillPx - padPx * 2f) / tabsCount
        val selectorInsetPx = with(density) { CapsuleSlider.Inset.toPx() }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val ltrSign = if (isLtr) 1f else -1f
        // 不能用 selectedTabIndex 做 remember key：每次重组都是新实例，currentIndex 会被冲掉
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        val latestSelectedTabIndex by rememberUpdatedState(selectedTabIndex)

        val selectorWidthPx = tabWidthPx - selectorInsetPx * 2f
        // 槽位左边界：RTL 下 index 0 在最右，几何位置与逻辑 index 反向
        val slotLeft: (Float) -> Float = { index ->
            if (isLtr) {
                padPx + index * tabWidthPx + selectorInsetPx
            } else {
                pillPx - padPx - (index + 1f) * tabWidthPx + selectorInsetPx
            }
        }
        val capsule = remember(
            animationScope, tabsCount, tabWidthPx, pillPx, padPx, selectorInsetPx, isLtr
        ) {
            CapsuleSelector(
                scope = animationScope,
                widthPx = selectorWidthPx,
                startLeftPx = slotLeft(currentIndex.toFloat()),
                // trail 到边就不再延伸（原版同款夹限），也不许滑出胶囊的 4dp 内边
                leftLimit = { padPx..(pillPx - padPx - selectorWidthPx) },
            )
        }
        val selectorHeightPx = with(density) { selectorHeight.roundToPx() }
        val trailMaxPx = with(density) { CapsuleSlider.TrailMax.toPx() }
        // 松手 / 点别的 tab 时，目标槽位落在胶囊当前所在位置的哪一侧（几何方向，RTL 自动成立）
        val settleToSlot: (Int) -> Unit = { index ->
            val target = slotLeft(index.toFloat())
            capsule.settleTo(target, forward = target > capsule.leftPx)
        }
        // 胶囊视觉中心落在哪个槽位。比逻辑 index 准，且天然支持 RTL 与半格拖拽。
        val indexAtCapsule: () -> Int = {
            val raw = if (isLtr) {
                (capsule.centerPx - padPx) / tabWidthPx - 0.5f
            } else {
                (pillPx - padPx - capsule.centerPx) / tabWidthPx - 0.5f
            }
            raw.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { latestSelectedTabIndex() }
                .collectLatest { index ->
                    if (index != currentIndex) currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation, capsule) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    settleToSlot(index)
                    onTabSelected(index)
                }
        }

        // 两块玻璃都用绝对定位：不能用 Row —— 它在 RTL 下会自动反转子节点顺序，
        // 而下面的手势层是绝对定位的，反转后手势区与胶囊会错开。
        // ── tab 胶囊：一块独立柔光玻璃 ──
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(pillWidthDp)
                .height(containerHeight)
                // 整块胶囊（玻璃 + 滑块 + 图标）一起按压放大 / 沿拖动方向拉伸。
                // 用 draw 期读同一份 InteractiveHighlight 状态，不进组合期 animation state。
                .graphicsLayer {
                    clip = false
                    val drag = computeDragTransform(
                        width = size.width,
                        height = size.height,
                        fraction = 1f,
                        pressProgress = pillHighlight.pressProgress,
                        offset = pillHighlight.offset,
                        density = this,
                        shapeAspectRatio = 1f,
                        stretchRatio = GlassDragStretchRatio,
                        translationDamping = GlassDragTranslationDamping,
                        pressScaleDp = 2f,
                    )
                    scaleX = drag.scaleX
                    scaleY = drag.scaleY
                    translationX = drag.translationX
                    translationY = drag.translationY
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = capsuleShape,
                    effects = pillEffects,
                    onDrawSurface = if (glassEffectsOn) null else surface,
                )
        ) {
            // 按压高光单独一层、按胶囊裁切：光晕半径是 minDimension * 1.5，
            // 不裁会画成一整块亮方块（同 LiquidTopBarButton）。
            Box(
                Modifier
                    .matchParentSize()
                    .then(pillHighlight.modifier)
                    // clip 放链尾：它建立的 layer 才能把高光的整幅提亮一起裁成胶囊
                    .clip(ContinuousCapsule())
            )
            // 选中滑块：抄 miuix OverlayView —— 左右两条边各自一条弹簧，
            // 位移途中宽度先撑开再收回，所以平移带橡皮筋感。
            // 位置与宽度都在 layout 期读（[capsuleBounds]），组合期不读任何高频值。
            Box(
                Modifier.capsuleSliderPill(
                    selector = capsule,
                    heightPx = selectorHeightPx,
                    pressProgress = { pillHighlight.pressProgress },
                )
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // ── 右侧圆按钮：另一块独立柔光玻璃 ──
        if (trailing != null && trailingHighlight != null) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillWidthDp + gapDp)
                    .size(containerHeight)
                    // 与 LiquidTopBarButton 同一套：按压 2dp、沿拖动方向拉伸、tanh 阻尼跟手
                    .graphicsLayer {
                        clip = false
                        val drag = computeDragTransform(
                            width = size.width,
                            height = size.height,
                            fraction = 0f,
                            pressProgress = trailingHighlight.pressProgress,
                            offset = trailingHighlight.offset,
                            density = this,
                            stretchRatio = GlassDragStretchRatio,
                            translationDamping = GlassDragTranslationDamping,
                            pressScaleDp = 2f,
                        )
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        translationX = drag.translationX
                        translationY = drag.translationY
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = capsuleShape,
                        effects = buttonEffects,
                        onDrawSurface = if (glassEffectsOn) null else surface,
                    )
                    // 纯观察、不消费，clickable 照常收到点击
                    .then(trailingHighlight.gestureModifier)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            hapticFeedback.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm
                            )
                            trailing.onClick()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 高光单独一层并裁成圆：与胶囊那份完全独立
                Box(
                    Modifier
                        .matchParentSize()
                        .then(trailingHighlight.modifier)
                        .clip(ContinuousCapsule())
                )
                Icon(
                    imageVector = trailing.icon,
                    contentDescription = trailing.contentDescription,
                    tint = if (isLightTheme) Color.Black.copy(alpha = 0.85f)
                    else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // 统一手势层：按下 tab / 拖胶囊 / 松手选中。
        // 只覆盖胶囊宽度 —— 它是最上层的兄弟节点，盖到圆按钮会抢走命中测试。
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(pillWidthDp)
                .fillMaxHeight()
                // 胶囊的高光/拉伸进度由这一层手势观察：下面的 pointerInput 会 consume，
                // inspectDragGestures 带 observeConsumed=true，照样能跟手
                .then(pillHighlight.gestureModifier)
                .pointerInput(capsule, tabsCount, tabWidthPx, isLtr, padPx, pillPx, trailMaxPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
                        // 圆按钮那一侧交给它自己的点击
                        if (downX > pillPx) return@awaitEachGesture

                        // 按在滑块上才是拖；按在别的 tab 上直接选中（同原版：胶囊就是拖动把手）
                        val capsuleHalf = tabWidthPx * CapsuleSlider.GrabHalfWidthRatio
                        var dragging = abs(downX - capsule.centerPx) <= capsuleHalf
                        var pressedTab = -1
                        // 以「按下那一刻胶囊在哪」为锚，不用逻辑 index：
                        // 从飞行中途接手也不会先跳一格
                        var dragAnchorLeft = capsule.leftPx
                        var dragAnchorX = downX
                        var trailVelocity = 0f

                        if (dragging) {
                            dampedDragAnimation.press()
                        } else {
                            pressedTab = if (isLtr) {
                                floor((downX - padPx) / tabWidthPx).toInt()
                            } else {
                                floor((size.width - padPx - downX) / tabWidthPx).toInt()
                            }.fastCoerceIn(0, tabsCount - 1)
                            dampedDragAnimation.animateToValueKeepingPress(pressedTab.toFloat())
                            settleToSlot(pressedTab)
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                // 松手吸附按胶囊的实际位置判，不用逻辑 value
                                val target = if (dragging) {
                                    indexAtCapsule()
                                } else {
                                    pressedTab.fastCoerceIn(0, tabsCount - 1)
                                }
                                currentIndex = target
                                dampedDragAnimation.animateToValue(target.toFloat())
                                settleToSlot(target)
                                dampedDragAnimation.release()
                                // 立即通知宿主，不等 snapshotFlow 下一拍，减少与 pager 错帧
                                onTabSelected(target)
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX

                            // 点 tab 后继续滑，接管为拖动；锚点直接取当前手指位置，
                            // 把滑出 touchSlop 的那一小段吃掉，胶囊不会突然跳一下
                            if (!dragging && abs(totalDx) > touchSlop) {
                                dragging = true
                                dampedDragAnimation.press()
                                dragAnchorLeft = capsule.leftPx
                                dragAnchorX = change.position.x
                                trailVelocity = 0f
                                lastX = change.position.x
                            }

                            if (dragging) {
                                // 拖尾：原版是 min(4 × 单帧位移, 60px)，这里先做一阶低通，
                                // 否则每帧 dx 抖动会让尾巴一直哆嗦
                                trailVelocity = trailVelocity * CapsuleSlider.TrailSmoothing +
                                    dx * (1f - CapsuleSlider.TrailSmoothing)
                                val trail = (abs(trailVelocity) * CapsuleSlider.TrailGain).coerceAtMost(trailMaxPx)
                                val dir = when {
                                    trailVelocity > 0.5f -> 1
                                    trailVelocity < -0.5f -> -1
                                    else -> 0
                                }
                                capsule.onDrag(
                                    leftPx = dragAnchorLeft + (change.position.x - dragAnchorX),
                                    trailPx = trail,
                                    dir = dir,
                                )
                                // 手指没动就别反复重启 value 的弹簧
                                if (abs(dx) > 0.01f) {
                                    dampedDragAnimation.updateValue(
                                        (dampedDragAnimation.targetValue + dx / tabWidthPx * ltrSign)
                                            .fastCoerceIn(0f, maxIndex)
                                    )
                                }
                            }

                            lastX = change.position.x
                            change.consume()
                        }
                    }
                }
        )
    }
}
