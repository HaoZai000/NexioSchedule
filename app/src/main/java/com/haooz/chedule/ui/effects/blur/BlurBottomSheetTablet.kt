/** 自定义模糊底部弹窗 - 平板版本：居中悬浮矩形，从底部滑入 */
package com.haooz.chedule.ui.effects.blur

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.effects.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

/**
 * 平板版模糊底部弹窗组件：居中悬浮矩形，从底部滑入动画。
 *
 * @param show 是否显示
 * @param title 标题文字
 * @param blurRadius 模糊半径
 * @param dimBackground 是否压暗背景
 * @param sheetMaxWidth 弹窗最大宽度
 * @param sheetBackgroundColor 弹窗背景颜色，null 则使用默认颜色
 * @param sheetBackgroundAlpha 弹窗背景透明度，null 则使用默认值
 * @param onDismissRequest 关闭回调
 * @param startAction 标题栏左侧操作按钮
 * @param endAction 标题栏右侧操作按钮
 * @param liquidGlassBackdrop 液态玻璃 backdrop
 * @param content 内容区域
 */
@Composable
fun BlurBottomSheetTablet(
    show: Boolean,
    title: String,
    blurRadius: Float = 24f,
    dimBackground: Boolean = false,
    sheetMaxWidth: Dp = 560.dp,
    sheetMaxHeight: Dp = Dp.Unspecified,
    sheetBackgroundColor: Color? = null,
    sheetBackgroundAlpha: Float? = null,
    isBottomAligned: Boolean = false,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    liquidGlassBackdrop: Backdrop? = null,
    onSheetContentBackdropCreated: ((Backdrop?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { mutableStateOf(false) }
    val sheetContentBackdropHolder = remember { mutableStateOf<Backdrop?>(null) }

    LaunchedEffect(show) {
        if (show) {
            visibleState.value = true
        }
    }

    LaunchedEffect(sheetContentBackdropHolder.value) {
        onSheetContentBackdropCreated?.invoke(sheetContentBackdropHolder.value)
    }

    DialogLayout(
        visible = visibleState,
        enableWindowDim = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        enableAutoLargeScreen = false,
        renderInRootScaffold = true,
    ) {
        BlurBottomSheetTabletContent(
            show = show,
            visibleState = visibleState,
            title = title,
            dimBackground = dimBackground,
            sheetMaxWidth = sheetMaxWidth,
            sheetMaxHeight = sheetMaxHeight,
            sheetBackgroundColor = sheetBackgroundColor,
            sheetBackgroundAlpha = sheetBackgroundAlpha,
            isBottomAligned = isBottomAligned,
            onDismissRequest = onDismissRequest,
            startAction = startAction,
            endAction = endAction,
            liquidGlassBackdrop = liquidGlassBackdrop,
            sheetContentBackdropHolder = sheetContentBackdropHolder,
            content = content,
        )
    }
}

@Composable
private fun BlurBottomSheetTabletContent(
    show: Boolean,
    visibleState: MutableState<Boolean>,
    title: String,
    dimBackground: Boolean = false,
    sheetMaxWidth: Dp = 560.dp,
    sheetMaxHeight: Dp = Dp.Unspecified,
    sheetBackgroundColor: Color? = null,
    sheetBackgroundAlpha: Float? = null,
    isBottomAligned: Boolean = false,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    liquidGlassBackdrop: Backdrop? = null,
    sheetContentBackdropHolder: MutableState<Backdrop?>? = null,
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f) }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val sheetHeightPx = remember { mutableIntStateOf(0) }

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val sheetBgColor = sheetBackgroundColor ?: if (isDark) Color(0xFF1E1E1E) else Color(0xFFF7F7F7)

    // 显示/隐藏动画
    LaunchedEffect(show) {
        if (show) {
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 520,
                    easing = CubicBezierEasing(0.34f, 1.15f, 0.3f, 1f)
                )
            )
        } else {
            animationProgress.animateTo(0f, animationSpec = tween(400, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f)))
            visibleState.value = false
        }
    }

    if (!show && animationProgress.value <= 0f) return

    BackHandler(enabled = show) {
        onDismissRequest()
    }

    // 遮罩层
    Box(modifier = Modifier.fillMaxSize()) {
        if (dimBackground && animationProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = animationProgress.value * 0.2f))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
        }
    }

    // 平板弹窗主体 - 居中悬浮矩形，从底部滑入
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = animationProgress.value > 0f,
                onClick = onDismissRequest,
            ),
        contentAlignment = if (isBottomAligned) Alignment.BottomCenter else Alignment.Center,
    ) {
        val sheetModifier = Modifier
            .graphicsLayer {
                val progress = animationProgress.value
                val windowHeightPx = with(density) { windowInfo.containerDpSize.height.toPx() }
                // 从屏幕底部滑入
                translationY = windowHeightPx * (1f - progress)
            }

        Box(
            modifier = sheetModifier
                .width(sheetMaxWidth)
                .fillMaxWidth()
                .heightIn(max = if (sheetMaxHeight != Dp.Unspecified) sheetMaxHeight else windowInfo.containerDpSize.height * 0.8f)
                .then(if (isBottomAligned) Modifier.padding(bottom = 20.dp) else Modifier)
                .onGloballyPositioned { coordinates ->
                    sheetHeightPx.intValue = coordinates.size.height
                }
                .clip(RoundedRectangle(38.dp))
                .then(
                    if (Build.VERSION.SDK_INT >= 33) {
                        Modifier.drawBackdrop(
                            backdrop = liquidGlassBackdrop!!,
                            shape = { RoundedRectangle(38.dp) },
                            effects = {
                                vibrancy()
                                blur(24.dp.toPx())
                            },
                            highlight = null
                        )
                    } else {
                        // API < 33 降级：渐变遮罩
                        Modifier.background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to sheetBgColor.copy(alpha = 0.9f),
                                    0.4f to sheetBgColor.copy(alpha = 0.82f),
                                    0.7f to sheetBgColor.copy(alpha = 0.6f),
                                    1.0f to sheetBgColor.copy(alpha = 0.0f)
                                )
                            )
                        )
                    }
                )
                .edgeLight(shape = RoundedRectangle(38.dp), edgeLight = rememberDefaultEdgeLight())
                .background(sheetBgColor.copy(alpha = sheetBackgroundAlpha ?: if (isDark) 0.92f else 0.9f))
                .pointerInput(Unit) {
                    detectTapGestures { /* consume clicks */ }
                }
                .semantics {
                    onClick(label = "Dismiss") {
                        onDismissRequest()
                        true
                    }
                },
            content = {

                // 捕获弹窗内容的 backdrop
                val sheetBackdropColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF4F4F4)
                val sheetContentBackdrop = rememberLayerBackdrop {
                    drawRect(sheetBackdropColor)
                    drawContent()
                }

                LaunchedEffect(sheetContentBackdrop) {
                    sheetContentBackdropHolder?.value = sheetContentBackdrop
                }

                // 内容区域
                Box(
                    modifier = Modifier.wrapContentHeight().layerBackdrop(sheetContentBackdrop)
                ) {
                    content()
                }

                // 渐变模糊遮罩
                ProgressiveBlurTopBar(
                    backdrop = sheetContentBackdrop,
                    height = 86.dp,
                    tintColor = sheetBgColor,
                    tintIntensity = 0f,
                    modifier = Modifier.zIndex(1f)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp))
                }

                // 标题栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 16.dp)
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
                    if (startAction != null) {
                        Box(modifier = Modifier.align(Alignment.CenterStart)) {
                            startAction()
                        }
                    }
                    if (endAction != null) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            endAction()
                        }
                    }
                }
            },
        )
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
