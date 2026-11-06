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
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.components.GlassDragStretchRatio
import com.haooz.chedule.ui.components.GlassDragTranslationDamping
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.haooz.chedule.ui.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.glass.applyLiveGlass
import com.haooz.chedule.ui.glass.SoftGlassStyle
import com.kyant.capsule.ContinuousCapsule
import top.yukonga.miuix.kmp.interactive.computeDragTransform
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
    /**
     * 材质填充策略。默认 [com.haooz.chedule.ui.glass.ThemeFill]（浅色白 / 深色黑 0.4）；
     * 传 [com.haooz.chedule.ui.glass.NoneFill] 则透出原本的玻璃色。
     */
    fill: com.haooz.chedule.ui.glass.GlassFill = com.haooz.chedule.ui.glass.ThemeFill,
    /**
     * 材质基调。默认 [com.haooz.chedule.ui.glass.BrightTone]（不压暗）：按钮里只有图标，
     * 没有需要在玻璃内保证可读性的文字，压暗只会让它在浅色页面上变成一块灰。
     */
    tone: com.haooz.chedule.ui.glass.GlassTone = com.haooz.chedule.ui.glass.BrightTone,
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    // 底色 alpha 随材质档位：柔光玻璃档压低一档，让折射透出来（与下拉菜单同源；浅深各一档）
    val resolvedContainerColor = if (containerColor != Color.Unspecified) containerColor
        else if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainer
            .copy(alpha = com.haooz.chedule.ui.utils.AppMaterialSettings.glassSurfaceAlpha(if (isLightTheme) 0.76f else 0.84f, isLightTheme))
        else if (isLightTheme) Color(0xFFFAFAFA).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.glassSurfaceAlpha(0.76f, isLight = true))
        else Color(0xFF242424).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.glassSurfaceAlpha(0.84f, isLight = false))

    val shadowColor = if (isLightTheme) Color(0x12000000) else Color(0x20000000)
    val interactionSource = remember { MutableInteractionSource() }

    // drawBackdrop 的 element 按引用比较 shape / effects / onDrawSurface；lambda 每帧新建
    // 会让节点每帧 invalidateDraw、重新录制采样层并重跑采样
    // 玻璃模糊随材质档位：柔光玻璃档更低（折射接管通透感），其余档 8；无材质档整体关
    val glassBlur = com.haooz.chedule.ui.utils.AppMaterialSettings.glassBlurDp()
    val glassEffectsOn = com.haooz.chedule.ui.utils.AppMaterialSettings.glassEffectsEnabled()
    val buttonShapeBlock: () -> androidx.compose.ui.graphics.Shape = remember { { ContinuousCapsule() } }
    // 材质、档位差异、基调、填充全部由材质层给出，这里只说「用哪套」
    val buttonGlassStyle: SoftGlassStyle =
        com.haooz.chedule.ui.utils.AppMaterialSettings.chromeGlassStyle(
            base = ScheduleSoftGlassStyle,
            isDark = !isLightTheme,
            fill = fill,
            tone = tone,
        )
    val buttonEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(glassBlur, glassEffectsOn, buttonGlassStyle) {
            {
                if (glassEffectsOn) {
                    blur(glassBlur.dp.toPx())
                    runtimeShaderEffect(
                        key = LIVE_GLASS_KEY,
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applyLiveGlass(
                            style = buttonGlassStyle,
                            width = size.width,
                            height = size.height,
                        )
                    }
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
                // 与 LiquidGlassDropdownMenu 缩小态同一套：按压 2dp、0.012 拉伸、0.032 位移阻尼
                val drag = computeDragTransform(
                    width = size.width,
                    height = size.height,
                    fraction = 0f,
                    pressProgress = interactiveHighlight.pressProgress,
                    offset = interactiveHighlight.offset,
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
                    // 柔光自己会画玻璃色与方向光，再叠容器色会把折射盖住。
                    // 但「无材质档」不跑 shader，必须留容器色，否则按钮变成透空圆孔。
                    onDrawSurface = if (glassEffectsOn) null else buttonOnDrawSurface
                )
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


/** 实时录制柔光的 shader 缓存键（参数全固定，一个程序够全应用按钮用）。 */
private const val LIVE_GLASS_KEY = "NexioSoftGlassLive"
