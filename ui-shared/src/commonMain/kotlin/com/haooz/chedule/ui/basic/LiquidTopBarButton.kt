package com.haooz.chedule.ui.basic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import top.yukonga.miuix.kmp.internal.drawBlurredPathShadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏液态玻璃圆形按钮。拖拽沿拖动方向拉伸、抬手回弹，机制与 BackToNowFloatingButton 一致
 * （[InteractiveHighlight] 高光 + 高光跟随、图标随动、阴影同步变形）。
 */
@Composable
fun LiquidTopBarButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    iconOffset: DpOffset = DpOffset.Zero,
    buttonHeight: Dp = 42.dp,
    backdropAlpha: Float = 1f,
    shadowAlpha: Float = 1f,
    iconTint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    performHapticFeedback: Boolean = true,
    enabled: Boolean = true,
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    // 底色 alpha 随材质档位：通透玻璃档压低一档，让折射透出来（与下拉菜单同源；浅深各一档）
    val resolvedContainerColor = if (containerColor != Color.Unspecified) containerColor
        else if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainer
            .copy(alpha = com.haooz.chedule.ui.utils.AppMaterialSettings.glassSurfaceAlpha(if (isLightTheme) 0.76f else 0.84f, isLightTheme))
        else if (isLightTheme) Color(0xFFFAFAFA).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.glassSurfaceAlpha(0.76f, isLight = true))
        else Color(0xFF242424).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.glassSurfaceAlpha(0.84f, isLight = false))

    val shadowColor = if (isLightTheme) Color(0x12000000) else Color(0x20000000)
    val interactionSource = remember { MutableInteractionSource() }

    // drawBackdrop 的 element 按引用比较 shape / effects / onDrawSurface；lambda 每帧新建
    // 会让节点每帧 invalidateDraw、重新录制采样层并重跑采样
    val chromeLens = com.haooz.chedule.ui.utils.AppMaterialSettings.chromeLensEnabled()
    // 玻璃模糊随材质档位：通透玻璃档更低（折射接管通透感），其余档 8；无材质档整体关
    val glassBlur = com.haooz.chedule.ui.utils.AppMaterialSettings.glassBlurDp()
    val glassEffectsOn = com.haooz.chedule.ui.utils.AppMaterialSettings.glassEffectsEnabled()
    val buttonShapeBlock: () -> androidx.compose.ui.graphics.Shape = remember { { ContinuousCapsule() } }
    val buttonEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(chromeLens, glassBlur, glassEffectsOn) {
        {
            if (glassEffectsOn) {
                vibrancy()
                blur(glassBlur.dp.toPx())
                // 均衡及以下关闭折射
                if (chromeLens) lens(6f.dp.toPx(), 36f.dp.toPx())
            }
        }
    }
    val buttonOnDrawSurface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
        remember(resolvedContainerColor) {
            { drawRect(resolvedContainerColor) }
        }

    Box(
        modifier = modifier
            .wrapContentSize()
            .size(buttonHeight)
            .graphicsLayer {
                clip = false
                val width = size.width
                val height = size.height
                val progress = interactiveHighlight.pressProgress.coerceAtLeast(0f)
                val scale = lerp(1f, 1f + 4f.dp.toPx() / height, progress)
                val offset = interactiveHighlight.offset
                val maxDragScale = 1f.dp.toPx() / height
                val offsetAngle = atan2(offset.y, offset.x)
                scaleX =
                    scale +
                        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                        (width / height).fastCoerceAtMost(1f)
                scaleY =
                    scale +
                        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                        (height / width).fastCoerceAtMost(1f)
                val contentMin = size.minDimension.coerceAtLeast(1f)
                // 起始跟手斜率：0.08 时位移约为手指的 8%，越拖阻尼越强（与 BackToNowFloatingButton 一致）。
                val initialDerivative = 0.08f
                translationX = contentMin * tanh(initialDerivative * offset.x / contentMin)
                translationY = contentMin * tanh(initialDerivative * offset.y / contentMin)
            }
            .drawBehind {
                val spread = shadowAlpha
                if (spread > 0.01f) {
                    val maxBlurRadius = 10f * density
                    val maxShadowSpread = 2f * density
                    val blurRadius = maxBlurRadius * spread
                    val shadowSpread = maxShadowSpread * spread
                    val outerRadius = size.minDimension / 2f + shadowSpread
                    val innerRadius = size.minDimension / 2f
                    // 环形阴影：外圈 CW + 内圈 CCW（winding 差集），与原 android.graphics.Path 同语义。
                    // compose Path 无 addCircle —— 圆用等宽高 addOval 表达。
                    val path = Path().apply {
                        addOval(
                            Rect(
                                center.x - outerRadius, center.y - outerRadius,
                                center.x + outerRadius, center.y + outerRadius,
                            ),
                            Path.Direction.Clockwise,
                        )
                        addOval(
                            Rect(
                                center.x - innerRadius, center.y - innerRadius,
                                center.x + innerRadius, center.y + innerRadius,
                            ),
                            Path.Direction.CounterClockwise,
                        )
                    }
                    // 原实现：alpha × 3.2 后取整（Color.alpha 是 0..255 整数）——这里 0..1 浮点同算
                    drawBlurredPathShadow(
                        path = path,
                        color = shadowColor.copy(
                            alpha = (shadowColor.alpha * 3.2f).coerceAtMost(1f),
                        ),
                        blurRadius = blurRadius.coerceAtLeast(0.1f),
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                // 不用 clip：拉伸/位移会超出原 layout bounds（同 BackToNowFloatingButton）
                .size(buttonHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = buttonShapeBlock,
                    effects = buttonEffects,
                    highlight = null,
                    shadow = null,
                    layerBlock = { alpha = backdropAlpha },
                    onDrawSurface = buttonOnDrawSurface
                )
                .edgeLight(shape = ContinuousCapsule(), edgeLight = rememberDefaultEdgeLight(degradedBaseColor = resolvedContainerColor))
                .zIndex(0f)
        )
        // 按压高光单独一层、与材质层平级：drawBackdrop 的 layerBlock 会把整个节点 alpha
        // 乘上 backdropAlpha，挂在材质 Box 上会让顶栏滚动时高光跟着材质一起消失。
        // 高光跟随手势（gestureModifier）与 clickable 同层，两者都是 pointerInput，可叠加。
        Box(
            modifier = Modifier
                .size(buttonHeight)
                // 必须 clip：光晕半径是 minDimension * 1.5，本就溢出圆形，
                // 不裁会画成一个亮方块。放在 interactiveHighlight 之前（外层）。
                .clip(ContinuousCapsule())
                .then(interactiveHighlight.modifier)
                .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    // 按压视觉由上面的高光层提供，不要默认涟漪
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        if (performHapticFeedback) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                        onClick()
                    }
                )
                .zIndex(1f)
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(iconSize)
                .offset(iconOffset.x, iconOffset.y)
                // 高光层 zIndex=1，图标再高一级，别依赖"同zIndex 靠后绘制"
                .zIndex(2f),
            tint = if (iconTint != Color.Unspecified) iconTint else if (isLightTheme) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
        )
    }
}
