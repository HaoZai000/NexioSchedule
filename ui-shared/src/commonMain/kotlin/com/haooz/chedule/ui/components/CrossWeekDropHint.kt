package com.haooz.chedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.CustomizeFill
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.shape.ContinuousCapsule
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 跨周提示胶囊的柔光 shader 缓存键：尺寸与其他玻璃件不同，不能共用 uniform。 */
private const val LIVE_CROSS_WEEK_GLASS_KEY = "NexioSoftGlassLiveCrossWeekHint"

/**
 * 跨周调课提示胶囊：拖着课翻周时告诉用户松手会落到第几周。
 * [fromWeek] 为原周次（拖拽起点），[toWeek] 为当前浏览周（落点周）；两者不同即跨周调课，胶囊用主题色强调。
 * 玻璃效果与「回到今天/本周」悬浮按钮同一套柔光：blur + 柔光 shader，白字靠主题色填充保证对比度。
 */
@Composable
fun CrossWeekDropHint(
    fromWeek: Int,
    toWeek: Int,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val crossWeek = fromWeek > 0 && toWeek > 0 && fromWeek != toWeek
    val isLightTheme = !isAppDarkTheme()
    // 无材质档实色
    val baseAlpha = AppMaterialSettings.solidSurfaceAlpha(if (isLightTheme) 0.72f else 0.62f)
    val containerColor = MiuixTheme.colorScheme.primary.copy(alpha = baseAlpha)
    val textColor = Color.White
    val hintShape: Shape = ContinuousCapsule()
    val hintShapeBlock: () -> Shape = remember { { hintShape } }
    val glassBlur = AppMaterialSettings.glassBlurDp()
    val glassEffectsOn = AppMaterialSettings.glassEffectsEnabled()
    val hintGlassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = !isLightTheme,
        fill = CustomizeFill(MiuixTheme.colorScheme.primary),
        tone = BrightTone,
    )
    // drawBackdrop 按引用比较 shape / effects / onDrawSurface，lambda 每帧新建会重录采样层。
    val glassEffects: com.haooz.softglass.backdrop.GlassEffectScope.() -> Unit =
        remember(glassBlur, glassEffectsOn, hintGlassStyle) {
            {
                if (glassEffectsOn) {
                    blur(glassBlur.dp.toPx())
                    runtimeShaderEffect(
                        key = LIVE_CROSS_WEEK_GLASS_KEY,
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applySoftGlass(
                            style = hintGlassStyle,
                            width = size.width,
                            height = size.height,
                        )
                    }
                }
            }
        }
    // 底色随材质档（无材质档 alpha → 1），必须进 key，否则切档后仍画旧半透明面
    val onDrawSurface: DrawScope.() -> Unit = remember(containerColor) {
        { drawRect(containerColor) }
    }

    Box(modifier = modifier.wrapContentSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shadow = null,
                    shape = hintShapeBlock,
                    effects = glassEffects,
                    // 柔光自己画玻璃色与方向光；无材质档不跑 shader，必须留容器色。
                    onDrawSurface = if (glassEffectsOn) null else onDrawSurface,
                )
                // 尺寸补间与 CardExpand 同一条 spring
                .cardContentSize()
                .padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 跨周才出现的「第N周 →」淡入淡出，宽度由外层 animateContentSize 平滑展开
            AnimatedVisibility(
                visible = crossWeek,
                enter = fadeIn(tween(durationMillis = 220)),
                exit = fadeOut(tween(durationMillis = 160)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = CommonStr.weekN(fromWeek),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.72f),
                    )
                    Text(
                        text = "→",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.72f),
                    )
                }
            }
            Text(
                text = CommonStr.weekN(toWeek),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        }
    }
}

/**
 * 边缘自动翻周指示：拖着课停在屏幕左右边缘热区时，在那一侧铺一条渐隐色带 + 方向箭头，
 * 告诉用户停住就会继续翻周（见 [EDGE_HINT_WIDTH]）。
 *
 * 箭头用设置项同款 `MiuixIcons.Basic.ArrowRight`，左侧镜像。
 *
 * @param direction -1 上一周（左）/ 1 下一周（右）；0 时不画。
 */
@Composable
fun EdgeAutoPageIndicator(
    direction: Int,
    modifier: Modifier = Modifier,
) {
    if (direction == 0) return
    val accent = MiuixTheme.colorScheme.primary.copy(alpha = 0.34f)
    val brush = if (direction < 0) {
        Brush.horizontalGradient(listOf(accent, Color.Transparent))
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, accent))
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(EDGE_HINT_WIDTH)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    // 左侧用同一枚箭头镜像，保证两侧视觉重量一致
                    if (direction < 0) scaleX = -1f
                },
        )
    }
}

/** 边缘提示带宽度：略窄于 `CourseScheduleAppState.updateEdgeAutoPage` 的 32dp 触发热区，
 *  仅作视觉暗示；真正判定看卡片中心是否进热区。 */
val EDGE_HINT_WIDTH = 24.dp
