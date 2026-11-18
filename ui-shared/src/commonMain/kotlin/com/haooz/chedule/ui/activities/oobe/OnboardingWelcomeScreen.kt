/**
 * 引导首页：应用图标、字标与箭头按钮。
 *
 * 本屏只负责展示与回调，背景由 [OnboardingFlow] 统一提供，这样箭头 morph 到全屏时
 * 背景不会出现接缝。字标流光与入场缩放都只改绘制阶段，不产生逐帧重组。
 */
package com.haooz.chedule.ui.activities.oobe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.OnboardingStr
import com.haooz.nexioui.background.BgEffectBackgroundWithPreset
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.about_app_icon
import com.haooz.chedule.uishared.resources.bt_startup
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import com.haooz.nexioui.pressfeedback.SinkFeedback
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/** 字标文案：不随应用语言变化，中英混排是产品名本身。 */
private const val WORDMARK_TEXT = "Nexio课程表"

/**
 * 箭头按钮的几何规格，供本屏绘制与 [OnboardingFlow] 的 morph 起点共用。
 *
 * `bt_startup` 素材 849×752px，按 276×236dp FillBounds 排版后，高不透明度的圆实测
 * 288×288px ≈ 93.6×90.4dp（素材本身略扁）。形变起点必须用这个尺寸，用 96dp 会比按钮
 * 大一圈，变成「上右对齐、左下多出来」。
 */
internal object WelcomeLayout {

    /** 按钮实心圆的可见直径。 */
    val buttonSize = 93.6.dp

    /** 按钮底边距容器底部的比例。 */
    const val bottomRatio = 0.21f

    /** 实心圆圆心相对「图片居中、底部按 [bottomRatio] 摆放」的偏移（dp）。 */
    private const val centerOffsetX = 2.9f
    private const val centerOffsetY = 4.4f

    /** 按钮中心（相对容器左上角，px）。 */
    fun buttonCenter(width: Float, height: Float, density: Float): Offset {
        val half = buttonSize.value * density / 2f
        return Offset(
            width / 2f + centerOffsetX * density,
            height - height * bottomRatio - centerOffsetY * density - half,
        )
    }
}

/**
 * 欢迎页的实时流光背景（与「关于」页同款，淡蓝色调）。
 *
 * 由 [OnboardingFlow] 常驻一份，形变时只做缩放 + 模糊，流光本身不停，进出不会闪。
 *
 * @param playing 翻转期间置 false：下层此时被缩放、模糊、压暗盖着，流光继续逐帧
 *   重绘只会让上层的全屏模糊每帧失效，暂停 520ms 肉眼无感。
 */
@Composable
internal fun OnboardingWelcomeBackdrop(
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    BgEffectBackgroundWithPreset(
        dynamicBackground = playing,
        presetOverride = if (isAppDarkTheme()) OobeBluePresetDark else OobeBluePresetLight,
        timeScale = 0.6f,
        isFullSize = true,
        modifier = modifier.fillMaxSize(),
    ) {
    }
}

/**
 * 应用图标：88dp，G2 连续曲率圆角（squircle）加 1dp 描边，欢迎页与完成页共用。
 */
@Composable
internal fun OobeAppIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.about_app_icon),
        contentDescription = null,
        modifier = modifier
            .size(88.dp)
            // 与应用图标外形一致
            .clip(ContinuousRoundedRectangle(22.dp))
            .border(1.dp, Color(0x1F000000), ContinuousRoundedRectangle(22.dp)),
        contentScale = ContentScale.Crop,
    )
}

/**
 * 欢迎页内容（应用图标 + 字标 + 箭头），背景由宿主提供。
 *
 * 入场时间轴：图标与字标同步「0.5 → 0.95（440ms, sinOut）→ 1（700ms, cubicOut）」缩放，
 * 透明度 60ms 后 440ms sinOut 淡入；箭头按钮 1340ms 才出现，450ms cubicOut 从 0.9
 * 放大到 1 并淡入，播完才允许点击。
 *
 * @param interactive morph 进行中关掉点击，避免重复触发
 * @param showArrow 形变期间箭头由宿主单独绘制（会移动放大），这里关掉避免重影
 * @param shimmer 字标流光是否运行；离开欢迎页后关掉，省掉一份常驻无限动画
 */
@Composable
fun OnboardingWelcomeContent(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    showArrow: Boolean = true,
    shimmer: Boolean = true,
) {
    val emblemAlpha = remember { Animatable(0f) }
    val emblemScale = remember { Animatable(INTRO_SCALE_FROM) }
    // 按钮的 alpha 与 scale 共用同一个动画，进度直接当两者的小数
    val buttonEnter = remember { Animatable(0f) }
    var introFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            delay(INTRO_ALPHA_DELAY_MILLIS.toLong().milliseconds)
            emblemAlpha.animateTo(1f, tween(INTRO_ALPHA_MILLIS, easing = IntroSinOut))
        }
        launch {
            emblemScale.animateTo(
                INTRO_SCALE_MID,
                tween(INTRO_SCALE_IN_MILLIS, easing = IntroSinOut),
            )
            emblemScale.animateTo(
                1f,
                tween(INTRO_SCALE_SETTLE_MILLIS, easing = IntroCubicOut),
            )
        }
        launch {
            delay(INTRO_BUTTON_DELAY_MILLIS.toLong().milliseconds)
            buttonEnter.animateTo(1f, tween(INTRO_BUTTON_MILLIS, easing = IntroCubicOut))
            introFinished = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        OobeAppIcon(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.27f)
                .graphicsLayer {
                    alpha = emblemAlpha.value
                    scaleX = emblemScale.value
                    scaleY = emblemScale.value
                },
        )

        WelcomeWordmark(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.41f + 13.dp),
            alpha = { emblemAlpha.value },
            scale = { emblemScale.value },
            shimmer = shimmer,
        )

        // 箭头按钮独立成层，morph 时由 OnboardingFlow 复用同一份绘制做淡出
        if (showArrow) {
            WelcomeArrow(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = buttonEnter.value
                        alpha = p
                        val scale = INTRO_BUTTON_SCALE_FROM + (1f - INTRO_BUTTON_SCALE_FROM) * p
                        scaleX = scale
                        scaleY = scale
                        // 以按钮自身中心为缩放锚点
                        val center = WelcomeLayout.buttonCenter(size.width, size.height, density)
                        transformOrigin = TransformOrigin(
                            center.x / size.width,
                            center.y / size.height,
                        )
                    },
                onClick = if (interactive && introFinished) onContinue else null,
            )
        }
    }
}

/**
 * 字标：渐变随相位横向流动。
 *
 * 相位只在绘制阶段读取——若在组合期读，整块文字会逐帧重组并重建 TextStyle 与渐变。
 */
@Composable
internal fun WelcomeWordmark(
    alpha: () -> Float,
    scale: () -> Float,
    shimmer: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = remember {
        TextStyle(
            fontSize = 40.sp,
            lineHeight = 47.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
        )
    }
    val layout = remember(measurer, style) {
        measurer.measure(AnnotatedString(WORDMARK_TEXT), style)
    }
    val densityScope = LocalDensity.current
    val width = with(densityScope) { layout.size.width.toDp() }
    val height = with(densityScope) { layout.size.height.toDp() }

    // 流光只在可见时运行，避免离场后仍有无限动画在排帧
    val phase: State<Float> = if (shimmer) {
        val transition = rememberInfiniteTransition(label = "wordmarkShimmer")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MILLIS, easing = LinearEasing)),
            label = "wordmarkPhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Canvas(
        modifier = modifier
            .size(width, height)
            .graphicsLayer {
                this.alpha = alpha()
                val s = scale()
                scaleX = s
                scaleY = s
            },
    ) {
        val shift = phase.value * SHIMMER_SPAN - SHIMMER_SPAN / 2f
        drawText(
            textLayoutResult = layout,
            brush = Brush.linearGradient(
                colors = WordmarkColors,
                start = Offset(shift, 0f),
                end = Offset(shift + SHIMMER_SPAN, 0f),
            ),
        )
    }
}

/** 字标的四段渐变：首尾同色，横向移动时首尾能无缝衔接。 */
private val WordmarkColors = listOf(
    Color(0xFF3E5A96),
    Color(0xFF1F3560),
    Color(0xFF5A78B0),
    Color(0xFF3E5A96),
)

/** 流光周期与渐变横向跨度（px）。 */
private const val SHIMMER_PERIOD_MILLIS = 8000
private const val SHIMMER_SPAN = 1200f

/** 入场时间轴（毫秒）与缩放值。 */
private const val INTRO_ALPHA_DELAY_MILLIS = 60
private const val INTRO_ALPHA_MILLIS = 440
private const val INTRO_SCALE_IN_MILLIS = 440
private const val INTRO_SCALE_SETTLE_MILLIS = 700
private const val INTRO_SCALE_FROM = 0.5f
private const val INTRO_SCALE_MID = 0.95f
private const val INTRO_BUTTON_DELAY_MILLIS = 1340
private const val INTRO_BUTTON_MILLIS = 450
private const val INTRO_BUTTON_SCALE_FROM = 0.9f

/** 图标/字标透明度与第一段缩放用的 sinOut。 */
private val IntroSinOut = Easing { fraction -> sin(fraction * PI.toFloat() / 2f) }

/** 第二段缩放与按钮入场用的 cubicOut。 */
private val IntroCubicOut = Easing { fraction ->
    val inverse = 1f - fraction
    1f - inverse * inverse * inverse
}

/**
 * 玻璃箭头按钮（APK 原图）。
 *
 * 原图 320×283 里按钮只占中间约 1/3，其余是辉光，所以按布局里的 276×236dp 摆放，
 * 可见按钮直径才和 [WelcomeLayout.buttonSize] 一致。
 *
 * @param alpha 额外透明度，供形变时淡出源图快照
 * @param onClick 为 null 时不接点击（形变进行中 / 入场未播完）
 */
@Composable
internal fun WelcomeArrow(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // 与 miuix Card 的 PressFeedbackType.Sink 同一份实现：缩到 0.94、spring(0.8, 600) 复原
    val sinkFeedback = remember { SinkFeedback() }
    BoxWithConstraints(modifier = modifier) {
        Image(
            painter = painterResource(Res.drawable.bt_startup),
            contentDescription = OnboardingStr.next(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (maxHeight * WelcomeLayout.bottomRatio - 60.dp).coerceAtLeast(0.dp))
                .size(width = 276.dp, height = 236.dp)
                .graphicsLayer { this.alpha = alpha }
                .then(
                    // 按压反馈交给 Card 同款 SinkFeedback，不要水波纹
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = sinkFeedback,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentScale = ContentScale.FillBounds,
        )
    }
}
