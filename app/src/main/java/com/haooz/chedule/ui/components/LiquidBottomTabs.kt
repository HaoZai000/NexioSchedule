package com.haooz.chedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.DampedDragAnimation
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

/** 底部导航 tab：纯视觉；手势由 [LiquidBottomTabs] 统一层处理 */
@Composable
fun RowScope.LiquidBottomTab(
    index: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(ContinuousCapsule())
            .semantics { role = Role.Tab }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * 液态玻璃底部导航。
 *
 * 统一手势层：
 * - 按下 tab：胶囊飞过去并保持按压
 * - 按下胶囊 / 随后拖动：1:1 跟手
 * - 松手：吸附最近 tab，提交选中
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 56.dp,
    highlightHeight: Dp = 48.dp,
    selectorHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isAppDarkTheme()
    val accentColor =
        if (isLightTheme) Color.Black
        else Color.White
    val containerColor =
        if (isLightTheme) Color(0xFFFFFFFF).copy(0.6f)
        else Color(0xFF121212).copy(0.54f)
    val defaultEdgeLight = rememberDefaultEdgeLight()

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val viewConfiguration = LocalViewConfiguration.current
        val padPx = with(density) { 4f.dp.toPx() }
        val tabWidth = (constraints.maxWidth.toFloat() - padPx * 2f) / tabsCount
        val maxIndex = (tabsCount - 1).toFloat()

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val ltrSign = if (isLtr) 1f else -1f
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..maxIndex,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // 可见层：不缩放（源库只缩放下面的捕获层，滑块外的放大不可见）
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    highlight = null,
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .edgeLight(shape = ContinuousCapsule(), edgeLight = defaultEdgeLight)
                .then(interactiveHighlight.modifier)
                .height(containerHeight)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // 捕获层（透明）：按压时缩放，只通过玻璃滑块的 backdrop 透出
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(highlightHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // 胶囊（纯视觉）
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { ContinuousCapsule() },
                    downsampleScale = 1f,
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.08f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .then(interactiveHighlight.gestureModifier)
                .height(selectorHeight)
                .fillMaxWidth(1f / tabsCount)
        )

        // 统一手势层：按下 tab / 拖胶囊 / 松手选中 都在这里
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(tabsCount, tabWidth, isLtr, padPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x

                        val capsuleCenterX = if (isLtr) {
                            padPx + (dampedDragAnimation.value + 0.5f) * tabWidth
                        } else {
                            size.width - padPx - (dampedDragAnimation.value + 0.5f) * tabWidth
                        }
                        val capsuleHalf = tabWidth * 0.55f
                        var dragging = abs(downX - capsuleCenterX) <= capsuleHalf
                        var pressedTab = -1

                        if (dragging) {
                            dampedDragAnimation.press()
                        } else {
                            pressedTab = if (isLtr) {
                                floor((downX - padPx) / tabWidth).toInt()
                            } else {
                                floor((size.width - padPx - downX) / tabWidth).toInt()
                            }.fastCoerceIn(0, tabsCount - 1)
                            // 按下 tab：胶囊飞过去，保持按压
                            dampedDragAnimation.animateToValueKeepingPress(pressedTab.toFloat())
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val target = if (dragging) {
                                    dampedDragAnimation.targetValue
                                        .fastRoundToInt()
                                        .fastCoerceIn(0, tabsCount - 1)
                                } else {
                                    pressedTab.fastCoerceIn(0, tabsCount - 1)
                                }
                                currentIndex = target
                                dampedDragAnimation.animateToValue(target.toFloat())
                                dampedDragAnimation.release()
                                animationScope.launch {
                                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                                }
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX

                            // 点 tab 后继续滑 → 接管为拖动
                            if (!dragging && abs(totalDx) > touchSlop) {
                                dragging = true
                                lastX = change.position.x
                            }

                            if (dragging && abs(dx) > 0.01f) {
                                lastX = change.position.x
                                dampedDragAnimation.updateValue(
                                    (dampedDragAnimation.targetValue + dx / tabWidth * ltrSign)
                                        .fastCoerceIn(0f, maxIndex)
                                )
                                animationScope.launch {
                                    offsetAnimation.snapTo(offsetAnimation.value + dx)
                                }
                            } else {
                                lastX = change.position.x
                            }

                            change.consume()
                        }
                    }
                }
        )
    }
}

@Composable
fun LiquidNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    isShiftMode: Boolean,
    modifier: Modifier = Modifier
) {
    var liquidSelectedTab by remember { mutableIntStateOf(selectedTab) }
    LaunchedEffect(selectedTab) { liquidSelectedTab = selectedTab }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarPadding > 0.dp) statusBarPadding else 36.dp
    val isDark = !isAppDarkTheme()
    val textColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LiquidBottomTabs(
            selectedTabIndex = { liquidSelectedTab },
            onTabSelected = { onTabSelected(it) },
            backdrop = backdrop,
            tabsCount = if (!isShiftMode) 3 else 2,
            modifier = Modifier
                .padding(top = topPadding + 4.dp)
                .width(if (isShiftMode) 160.dp else 240.dp)
                .height(42.dp),
            containerHeight = 400.dp,
            highlightHeight = 34.dp,
            selectorHeight = 34.dp
        ) {
            if (!isShiftMode) {
                LiquidBottomTab(index = 0) {
                    Text("今日", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
                LiquidBottomTab(index = 1) {
                    Text("课程表", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
                LiquidBottomTab(index = 2) {
                    Text("我的", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
            } else {
                LiquidBottomTab(index = 0) {
                    Text("排班课表", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
                LiquidBottomTab(index = 1) {
                    Text("设置", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
            }
        }
    }
}
