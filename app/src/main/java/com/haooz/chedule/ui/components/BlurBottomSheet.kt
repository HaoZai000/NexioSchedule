/** 自定义模糊底部弹窗 - 支持全区域模糊背景 */
package com.haooz.chedule.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.oobe.OobeCubicOutEasing
import com.haooz.chedule.ui.oobe.OobeQuartOutEasing
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

/**
 * 自定义模糊底部弹窗组件，支持全区域（包括标题栏）的模糊背景效果。
 *
 * @param show 是否显示
 * @param title 标题文字
 * @param backdrop 模糊背景的 LayerBackdrop
 * @param blurRadius 模糊半径
 * @param dimBackground 是否压暗背景
 * @param onDismissRequest 关闭回调
 * @param endAction 标题栏右侧操作按钮
 * @param content 内容区域
 */
@Composable
fun BlurBottomSheet(
    show: Boolean,
    title: String,
    backdrop: LayerBackdrop?,
    blurRadius: Float = 24f,
    dimBackground: Boolean = false,
    onDismissRequest: () -> Unit,
    endAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { mutableStateOf(false) }

    // 显示时立即可见，隐藏时等动画播完再隐藏
    LaunchedEffect(show) {
        if (show) {
            visibleState.value = true
        }
    }

    DialogLayout(
        visible = visibleState,
        enableWindowDim = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        enableAutoLargeScreen = false,
        renderInRootScaffold = true,
    ) {
        BlurBottomSheetContent(
            show = show,
            visibleState = visibleState,
            title = title,
            backdrop = backdrop,
            blurRadius = blurRadius,
            dimBackground = dimBackground,
            onDismissRequest = onDismissRequest,
            endAction = endAction,
            content = content,
        )
    }
}

@Composable
private fun BlurBottomSheetContent(
    show: Boolean,
    visibleState: MutableState<Boolean>,
    title: String,
    backdrop: LayerBackdrop?,
    blurRadius: Float,
    dimBackground: Boolean = false,
    onDismissRequest: () -> Unit,
    endAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val sheetHeightPx = remember { mutableIntStateOf(0) }
    val imeInsets = WindowInsets.ime
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val sheetBgColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF7F7F7)
    val dismissThresholdPx = with(density) { 150.dp.toPx() }
    val velocityThresholdPx = with(density) { 800.dp.toPx() }

    // 显示/隐藏动画
    LaunchedEffect(show) {
        if (show) {
            dragOffsetY.snapTo(0f)
            animationProgress.animateTo(1f, animationSpec = tween(450, easing = OobeQuartOutEasing))
        } else {
            animationProgress.animateTo(0f, animationSpec = tween(340, easing = OobeCubicOutEasing))
            visibleState.value = false
        }
    }

    if (!show && animationProgress.value <= 0f) return

    // 返回手势处理
    BackHandler(enabled = show) {
        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
        onDismissRequest()
    }

    // 遮罩层
    Box(modifier = Modifier.fillMaxSize()) {
        if (show) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (dimBackground && animationProgress.value > 0f) Modifier.background(Color.Black.copy(alpha = animationProgress.value * 0.14f))
                        else Modifier
                    )
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
        }
    }

    // 底部弹窗主体 - 允许内容溢出屏幕底部
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val sheetModifier = Modifier
            .graphicsLayer {
                val progress = animationProgress.value
                val currentHeight = sheetHeightPx.intValue.toFloat()
                val windowHeightPx = with(density) { windowInfo.containerDpSize.height.toPx() }
                val baseOffset = if (currentHeight > 0) currentHeight else windowHeightPx
                translationY = baseOffset * (1f - progress) + dragOffsetY.value
            }

        Box(
            modifier = sheetModifier
                .offset(y = 200.dp)
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = windowInfo.containerDpSize.height - statusBarsPadding - 80.dp)
                .onGloballyPositioned { coordinates ->
                    if (imeInsets.getBottom(density) == 0) {
                        sheetHeightPx.intValue = coordinates.size.height
                    }
                }
                .imePadding()
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .then(
                    if (backdrop != null) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                            blurRadius = blurRadius,
                            highlight = Highlight.GlassStrokeSmallDark,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(
                                    BlendColorEntry(
                                        color = if (isDark) Color.Black.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.54f),
                                        mode = BlurBlendMode.Screen,
                                    )
                                ),
                                brightness = 0f,
                                contrast = 1f,
                                saturation = 1.2f,
                            ),
                        )
                    } else Modifier
                )
                .background(sheetBgColor.copy(alpha = if (backdrop != null)
                    if (isDark) 0.9f else 0.6f
                    else 1f))
                .pointerInput(Unit) {
                    detectTapGestures { /* consume clicks */ }
                }
                .semantics {
                    onClick(label = "Dismiss") {
                        onDismissRequest()
                        true
                    }
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dragAmount ->
                        coroutineScope.launch {
                            val newOffset = dragOffsetY.value + dragAmount
                            // 往上拖时加阻尼，越往上越难拖
                            val dampedOffset = if (newOffset < 0f) {
                                val resistance = 1f / (1f + kotlin.math.abs(newOffset) / 50f)
                                dragOffsetY.value + dragAmount * resistance
                            } else {
                                newOffset
                            }
                            dragOffsetY.snapTo(dampedOffset)
                        }
                    },
                    onDragStopped = { velocity ->
                        if (velocity > velocityThresholdPx || dragOffsetY.value > dismissThresholdPx) {
                            onDismissRequest()
                        } else {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(0f, animationSpec = tween(200))
                            }
                        }
                    },
                ),
            content = {
                // 拖拽手柄（仅按下放大动画）
                DragHandleArea()

                // 标题栏（zIndex 提升到顶层，消费触摸事件）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp)
                        .zIndex(2f)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if (endAction != null) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            endAction()
                        }
                    }
                }
                // 内容区域
                content()
            },
        )
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

/**
 * Miuix 风格的拖拽手柄：按下时放大。
 */
@Composable
private fun DragHandleArea() {
    val pressScale = remember { Animatable(1f) }
    val pressWidth = remember { Animatable(45f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .zIndex(2f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        coroutineScope.launch {
                            launch { pressScale.animateTo(1.15f, tween(100)) }
                            launch { pressWidth.animateTo(55f, tween(100)) }
                        }
                        // 等待松手
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        coroutineScope.launch {
                            launch { pressScale.animateTo(1f, tween(150)) }
                            launch { pressWidth.animateTo(45f, tween(150)) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(pressWidth.value.dp)
                .height(4.dp)
                .graphicsLayer {
                    scaleY = pressScale.value
                }
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f)),
        )
    }
}
